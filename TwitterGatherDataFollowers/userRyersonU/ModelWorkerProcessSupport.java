package TwitterGatherDataFollowers.userRyersonU;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Runs model subprocesses with bounded diagnostics and a hard containment
 * timeout. The output drain prevents a full pipe from deadlocking a worker.
 */
final class ModelWorkerProcessSupport
{
	private static final int MAX_OUTPUT_CHARS = 65536;

	private ModelWorkerProcessSupport()
	{
	}

	static Result run(List<String> command, long timeoutSeconds) throws Exception
	{
		ProcessBuilder builder = new ProcessBuilder(command);
		builder.redirectErrorStream(true);
		final Process process = builder.start();
		final StringBuilder output = new StringBuilder();
		Thread outputDrainer = new Thread(new Runnable()
		{
			public void run()
			{
				try (BufferedReader reader = new BufferedReader(
						new InputStreamReader(process.getInputStream())))
				{
					String line;
					while ((line = reader.readLine()) != null)
					{
						synchronized (output)
						{
							if (output.length() < MAX_OUTPUT_CHARS)
							{
								output.append(line).append('\n');
							}
						}
					}
				}
				catch (Exception ignored)
				{
					// The process exit/forced exit is the authoritative result.
				}
			}
		}, "model-worker-output");
		outputDrainer.setDaemon(true);
		outputDrainer.start();

		//Poll so a revoked work unit (a drain or a crash of its node) stops its
		//worker at once instead of waiting for it to finish.
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(Math.max(1L, timeoutSeconds));
		boolean finished = false;
		boolean revoked = false;
		while (!finished)
		{
			long remaining = deadline - System.nanoTime();
			if (remaining <= 0L)
			{
				break;
			}
			finished = process.waitFor(Math.min(remaining, TimeUnit.MILLISECONDS.toNanos(100L)),
					TimeUnit.NANOSECONDS);
			if (!finished && UnitCancellation.isRevoked())
			{
				revoked = true;
				break;
			}
		}
		if (revoked)
		{
			process.destroyForcibly();
			process.waitFor(5L, TimeUnit.SECONDS);
			outputDrainer.join(1000L);
			UnitCancellation.check();
		}
		if (!finished)
		{
			process.destroy();
			if (!process.waitFor(5L, TimeUnit.SECONDS))
			{
				process.destroyForcibly();
				process.waitFor(5L, TimeUnit.SECONDS);
			}
		}
		outputDrainer.join(5000L);
		String captured;
		synchronized (output)
		{
			captured = output.toString();
		}
		return new Result(
				finished ? process.exitValue() : -1,
				!finished,
				captured);
	}

	static long configuredTimeoutSeconds()
	{
		String configured = System.getProperty(
				"recommender.svm.worker.timeout.seconds");
		if (configured != null)
		{
			try
			{
				return Math.max(60L, Long.parseLong(configured.trim()));
			}
			catch (NumberFormatException ignored)
			{
				// Use the safe default below.
			}
		}
		return 6L * 60L * 60L;
	}

	static final class Result
	{
		private final int exitCode;
		private final boolean timedOut;
		private final String output;

		private Result(int exitCode, boolean timedOut, String output)
		{
			this.exitCode = exitCode;
			this.timedOut = timedOut;
			this.output = output;
		}

		int getExitCode()
		{
			return exitCode;
		}

		boolean isTimedOut()
		{
			return timedOut;
		}

		String getOutput()
		{
			return output;
		}
	}
}
