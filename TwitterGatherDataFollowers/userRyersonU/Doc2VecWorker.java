package TwitterGatherDataFollowers.userRyersonU;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * One persistent Python Doc2Vec scoring process (one per node lane).
 *
 * The process prepares the shared model and loads the vector index once, then
 * scores any number of candidate shards, so a shard costs its scoring work and
 * no interpreter start-up. The worker reports its own CPU time for preparation
 * and for each request; the node dilates that work by its emulated speed.
 */
final class Doc2VecWorker implements Closeable
{
	private static final String READY = "DOC2VEC_READY";
	private static final String UNIT_DONE = "DOC2VEC_UNIT_DONE";
	private static final String FATAL = "DOC2VEC_FATAL";

	private final Process process;
	private final BufferedReader output;
	private final BufferedWriter input;
	private final Doc2VecBatchRunner.ProgressListener listener;
	private final int candidateCount;
	private final long preparationCpuNanos;
	private int requestSequence;

	Doc2VecWorker(
			String pythonCommand,
			File scriptFile,
			File datasetFile,
			int trainingWorkers,
			AlgorithmParameterSettings settings,
			Doc2VecBatchRunner.ProgressListener listener) throws IOException
	{
		this.listener = listener;
		List<String> command = new ArrayList<String>();
		command.add(pythonCommand);
		command.add("-u");
		command.add(scriptFile.getPath());
		command.add(datasetFile.getAbsolutePath());
		command.add(String.valueOf(Math.max(1, trainingWorkers)));
		command.addAll((settings == null
				? AlgorithmParameterSettings.defaults() : settings).toDoc2VecCommandArguments());
		ProcessBuilder builder = new ProcessBuilder(command);
		builder.redirectErrorStream(true);
		//One BLAS thread keeps each score's arithmetic identical on every run.
		builder.environment().put("OMP_NUM_THREADS", "1");
		builder.environment().put("OPENBLAS_NUM_THREADS", "1");
		builder.environment().put("MKL_NUM_THREADS", "1");
		process = builder.start();
		output = new BufferedReader(new InputStreamReader(
				process.getInputStream(), StandardCharsets.UTF_8));
		input = new BufferedWriter(new OutputStreamWriter(
				process.getOutputStream(), StandardCharsets.UTF_8));
		int count = -1;
		long cpu = 0L;
		StringBuilder diagnostics = new StringBuilder();
		String line;
		while ((line = output.readLine()) != null)
		{
			if (line.startsWith(READY + "\t"))
			{
				String[] fields = line.split("\t");
				count = Integer.parseInt(fields[1].trim());
				cpu = secondsToNanos(fields[2]);
				break;
			}
			if (line.startsWith(FATAL + "\t"))
			{
				destroy();
				throw new IOException("Doc2Vec worker could not start: "
						+ decode(line.substring(FATAL.length() + 1)) + diagnostics);
			}
			if (!forwardProgress(line))
			{
				appendDiagnostic(diagnostics, line);
			}
		}
		if (count < 0)
		{
			int exit = waitForExit();
			throw new IOException("Doc2Vec worker exited with code " + exit
					+ " before it was ready." + diagnostics);
		}
		candidateCount = count;
		preparationCpuNanos = cpu;
	}

	/** Number of documents in the shared model. */
	int getCandidateCount()
	{
		return candidateCount;
	}

	/** CPU time the process spent preparing the model and index. */
	long getPreparationCpuNanos()
	{
		return preparationCpuNanos;
	}

	/** Scores shard {@code shard} of {@code shards} for {@code users}. */
	Scored score(int shard, int shards, int topn, List<String> users) throws IOException
	{
		String id = "r" + (++requestSequence);
		StringBuilder request = new StringBuilder();
		request.append("{\"id\":\"").append(id).append("\",\"shard\":").append(shard)
				.append(",\"shards\":").append(shards).append(",\"topn\":").append(topn)
				.append(",\"users\":[");
		for (int index = 0; index < users.size(); index++)
		{
			if (index > 0)
			{
				request.append(',');
			}
			request.append(jsonString(users.get(index)));
		}
		request.append("]}");
		input.write(request.toString());
		input.newLine();
		input.flush();

		Doc2VecBatchRunner.Result result = new Doc2VecBatchRunner.Result(users);
		String line;
		while ((line = output.readLine()) != null)
		{
			if (line.startsWith(UNIT_DONE + "\t"))
			{
				String[] fields = line.split("\t");
				if (!id.equals(fields[1]))
				{
					throw new IOException("Doc2Vec worker answered request " + fields[1]
							+ " while " + id + " was pending.");
				}
				result.setExitCode(0);
				return new Scored(result, secondsToNanos(fields[2]));
			}
			if (line.startsWith(FATAL + "\t"))
			{
				throw new IOException("Doc2Vec worker failed: "
						+ decode(line.substring(FATAL.length() + 1)));
			}
			if (!forwardProgress(line) && !Doc2VecBatchRunner.parseOutputLine(line, result))
			{
				result.addDiagnosticLine(line);
			}
		}
		throw new IOException("Doc2Vec worker exited with code " + waitForExit()
				+ " while scoring shard " + shard + "/" + shards
				+ result.getDiagnosticSummary());
	}

	public void close()
	{
		try
		{
			input.write("{\"op\":\"stop\"}");
			input.newLine();
			input.flush();
		}
		catch (IOException ignored)
		{
			//The process is gone already.
		}
		try
		{
			if (!process.waitFor(5, TimeUnit.SECONDS))
			{
				destroy();
			}
		}
		catch (InterruptedException interrupted)
		{
			Thread.currentThread().interrupt();
			destroy();
		}
	}

	/** A request's parsed result and the worker's CPU time for it. */
	static final class Scored
	{
		private final Doc2VecBatchRunner.Result result;
		private final long cpuNanos;

		private Scored(Doc2VecBatchRunner.Result result, long cpuNanos)
		{
			this.result = result;
			this.cpuNanos = cpuNanos;
		}

		Doc2VecBatchRunner.Result getResult()
		{
			return result;
		}

		long getCpuNanos()
		{
			return cpuNanos;
		}
	}

	private boolean forwardProgress(String line)
	{
		Doc2VecBatchRunner.ProgressUpdate update = Doc2VecBatchRunner.parseProgressLine(line);
		if (update == null)
		{
			return false;
		}
		if (listener != null)
		{
			listener.onProgress(update.getEvent(), update.getDetail());
		}
		return true;
	}

	private int waitForExit()
	{
		try
		{
			if (process.waitFor(10, TimeUnit.SECONDS))
			{
				return process.exitValue();
			}
		}
		catch (InterruptedException interrupted)
		{
			Thread.currentThread().interrupt();
		}
		destroy();
		return -1;
	}

	private void destroy()
	{
		process.destroy();
		try
		{
			if (!process.waitFor(2, TimeUnit.SECONDS))
			{
				process.destroyForcibly();
			}
		}
		catch (InterruptedException interrupted)
		{
			Thread.currentThread().interrupt();
			process.destroyForcibly();
		}
	}

	private static void appendDiagnostic(StringBuilder diagnostics, String line)
	{
		if (line != null && line.trim().length() > 0 && diagnostics.length() < 1500)
		{
			diagnostics.append(diagnostics.length() == 0 ? " Process output: " : " | ")
					.append(line.trim());
		}
	}

	private static long secondsToNanos(String seconds)
	{
		try
		{
			return Math.max(0L, (long)Math.round(Double.parseDouble(seconds.trim()) * 1.0e9));
		}
		catch (RuntimeException unparseable)
		{
			return 0L;
		}
	}

	private static String decode(String encoded)
	{
		try
		{
			return new String(Base64.getDecoder().decode(encoded.trim()), StandardCharsets.UTF_8);
		}
		catch (RuntimeException notBase64)
		{
			return encoded;
		}
	}

	private static String jsonString(String value)
	{
		StringBuilder builder = new StringBuilder("\"");
		for (int index = 0; index < value.length(); index++)
		{
			char character = value.charAt(index);
			switch (character)
			{
				case '"':
					builder.append("\\\"");
					break;
				case '\\':
					builder.append("\\\\");
					break;
				case '\n':
					builder.append("\\n");
					break;
				case '\r':
					builder.append("\\r");
					break;
				case '\t':
					builder.append("\\t");
					break;
				default:
					if (character < 0x20)
					{
						builder.append(String.format("\\u%04x", (int)character));
					}
					else
					{
						builder.append(character);
					}
			}
		}
		return builder.append('"').toString();
	}
}
