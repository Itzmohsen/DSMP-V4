package TwitterGatherDataFollowers.userRyersonU;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

final class Doc2VecBatchRunner
{
	private static final String SCORE_PREFIX = "DOC2VEC_SCORE";
	private static final String ERROR_PREFIX = "DOC2VEC_ERROR";
	private static final String DONE_PREFIX = "DOC2VEC_DONE";
	private static final String PROGRESS_PREFIX = "DOC2VEC_PROGRESS";

	private Doc2VecBatchRunner()
	{
	}

	static Result run(String pythonCommand, File scriptFile, int topn, File datasetFile,
			int nodeNumber, int numNodes, int workers, List<String> users) throws IOException, InterruptedException
	{
		return run(
				pythonCommand,
				scriptFile,
				topn,
				datasetFile,
				nodeNumber,
				numNodes,
				workers,
				users,
				null);
	}

	static Result run(String pythonCommand, File scriptFile, int topn, File datasetFile,
			int nodeNumber, int numNodes, int workers, List<String> users,
			ProgressListener progressListener) throws IOException, InterruptedException
	{
		return run(
				pythonCommand,
				scriptFile,
				topn,
				datasetFile,
				nodeNumber,
				numNodes,
				workers,
				users,
				AlgorithmParameterSettings.defaults(),
				progressListener);
	}

	static Result run(String pythonCommand, File scriptFile, int topn, File datasetFile,
			int nodeNumber, int numNodes, int workers, List<String> users,
			AlgorithmParameterSettings settings,
			ProgressListener progressListener) throws IOException, InterruptedException
	{
		if (users == null || users.isEmpty())
		{
			throw new IllegalArgumentException("At least one Doc2Vec batch user is required.");
		}

		Path usersFile = Files.createTempFile("doc2vec-batch-users-node-" + nodeNumber + "-", ".txt");
		try
		{
			Files.write(usersFile, users, StandardCharsets.UTF_8);
			List<String> command = new ArrayList<String>();
			command.add(pythonCommand);
			command.add("-u");
			command.add(scriptFile.getPath());
			command.add(String.valueOf(topn));
			command.add(datasetFile.getAbsolutePath());
			command.add(String.valueOf(nodeNumber));
				command.add(String.valueOf(numNodes));
				command.add(String.valueOf(workers));
				command.add(usersFile.toAbsolutePath().toString());
				command.addAll((settings == null
						? AlgorithmParameterSettings.defaults()
						: settings).toDoc2VecCommandArguments());

				ProcessBuilder processBuilder = new ProcessBuilder(command);
			processBuilder.redirectErrorStream(true);
			Process process = processBuilder.start();
			Result result = new Result(users);
			BufferedReader outputReader = new BufferedReader(new InputStreamReader(
					process.getInputStream(), StandardCharsets.UTF_8));
			try
			{
				String line;
				while ((line = outputReader.readLine()) != null)
				{
					ProgressUpdate progressUpdate = parseProgressLine(line);
					if (progressUpdate != null)
					{
						if (progressListener != null)
						{
							progressListener.onProgress(
									progressUpdate.event,
									progressUpdate.detail);
						}
					}
					else if (!parseOutputLine(line, result))
					{
						result.addDiagnostic(line);
						System.out.println(line);
					}
				}
			}
			finally
			{
				outputReader.close();
			}
			result.exitCode = process.waitFor();
			return result;
		}
		finally
		{
			Files.deleteIfExists(usersFile);
		}
	}

	static ProgressUpdate parseProgressLine(String line)
	{
		if (line == null || !line.startsWith(PROGRESS_PREFIX + "\t"))
		{
			return null;
		}
		String[] fields = line.split("\t", 3);
		if (fields.length < 2)
		{
			return null;
		}
		return new ProgressUpdate(
				fields[1],
				fields.length == 3 ? fields[2] : "");
	}

	static boolean parseOutputLine(String line, Result result)
	{
		if (line == null)
		{
			return false;
		}

		try
		{
			if (line.startsWith(SCORE_PREFIX + "\t"))
			{
				String[] fields = line.split("\t", 4);
				if (fields.length != 4)
				{
					return false;
				}
				String user = decodeField(fields[1]);
				String candidate = decodeField(fields[2]);
				double score = Double.parseDouble(fields[3]);
				if (!Double.isNaN(score) && !Double.isInfinite(score))
				{
					result.addScore(user, candidate, score);
				}
				return true;
			}
			if (line.startsWith(ERROR_PREFIX + "\t"))
			{
				String[] fields = line.split("\t", 3);
				if (fields.length != 3)
				{
					return false;
				}
				result.addError(decodeField(fields[1]), decodeField(fields[2]));
				return true;
			}
			if (line.startsWith(DONE_PREFIX + "\t"))
			{
				String[] fields = line.split("\t", 2);
				if (fields.length != 2)
				{
					return false;
				}
				result.markCompleted(decodeField(fields[1]));
				return true;
			}
		}
		catch (RuntimeException e)
		{
			System.out.println("Could not parse Doc2Vec batch output line: " + line);
			e.printStackTrace();
			return true;
		}
		return false;
	}

	private static String decodeField(String encoded)
	{
		return new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
	}

	interface ProgressListener
	{
		void onProgress(String event, String detail);
	}

	static final class ProgressUpdate
	{
		private final String event;
		private final String detail;

		private ProgressUpdate(String event, String detail)
		{
			this.event = event;
			this.detail = detail;
		}

		String getEvent()
		{
			return event;
		}

		String getDetail()
		{
			return detail;
		}
	}

	static final class Result
	{
		private final TreeMap<String,TreeMap<String,Double>> scores = new TreeMap<String,TreeMap<String,Double>>();
		private final TreeMap<String,String> errors = new TreeMap<String,String>();
		private final Set<String> requestedUsers = new HashSet<String>();
		private final Set<String> completedUsers = new HashSet<String>();
		private final ArrayList<String> diagnostics = new ArrayList<String>();
		private boolean unsafeNumericalDiagnostic;
		private int exitCode = -1;

		Result(Collection<String> users)
		{
			requestedUsers.addAll(users);
			for (String user : users)
			{
				scores.put(user, new TreeMap<String,Double>());
			}
		}

		private void addScore(String user, String candidate, double score)
		{
			if (requestedUsers.contains(user))
			{
				scores.get(user).put(candidate, score);
			}
		}

		private void addError(String user, String error)
		{
			if (requestedUsers.contains(user))
			{
				errors.put(user, error);
			}
		}

		private void markCompleted(String user)
		{
			if (requestedUsers.contains(user))
			{
				completedUsers.add(user);
			}
		}

		private void addDiagnostic(String diagnostic)
		{
			if (diagnostic != null && diagnostic.trim().length() > 0)
			{
				String trimmedDiagnostic = diagnostic.trim();
				if (trimmedDiagnostic.contains("Exception ignored in:")
						&& trimmedDiagnostic.contains(
								"gensim.models.word2vec_inner.our_dot"))
				{
					unsafeNumericalDiagnostic = true;
				}
				if (diagnostics.size() < 100)
				{
					diagnostics.add(trimmedDiagnostic);
				}
			}
		}

		TreeMap<String,Double> getScores(String user)
		{
			return scores.get(user);
		}

		Map<String,String> getErrors()
		{
			return errors;
		}

		boolean isCompleted(String user)
		{
			return completedUsers.contains(user);
		}

		boolean hasError(String user)
		{
			return errors.containsKey(user);
		}

		int getExitCode()
		{
			return exitCode;
		}

		boolean hasUnsafeNumericalDiagnostic()
		{
			return unsafeNumericalDiagnostic;
		}

		String getDiagnosticSummary()
		{
			if (diagnostics.isEmpty())
			{
				return "";
			}
			String prefix = " Process output: ";
			StringBuilder summary = new StringBuilder(prefix);
			for (String diagnostic : diagnostics)
			{
				if (summary.length() + diagnostic.length() > 1200)
				{
					summary.append("...");
					break;
				}
				if (summary.length() > prefix.length())
				{
					summary.append(" | ");
				}
				summary.append(diagnostic);
			}
			return summary.toString();
		}
	}
}
