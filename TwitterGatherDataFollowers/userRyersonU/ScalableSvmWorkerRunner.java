package TwitterGatherDataFollowers.userRyersonU;

import java.io.File;
import java.io.FileInputStream;
import java.io.ObjectInputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

final class ScalableSvmWorkerRunner
{
	private static final long MEBIBYTE = RecommendationMemoryCoordinator.MEBIBYTE;

	private ScalableSvmWorkerRunner()
	{
	}

	static ExactSvmWorkerResult run(
			String owner,
			String stage,
			String runId,
			File trainingFile,
			File testFile,
			File recommendationFile,
			int featureCount,
			int trainingInstanceCount,
			int trainingClassCount) throws Exception
	{
		return run(
				owner,
				stage,
				runId,
				trainingFile,
				testFile,
				recommendationFile,
				featureCount,
				trainingInstanceCount,
				trainingClassCount,
				AlgorithmParameterSettings.defaults());
	}

	static ExactSvmWorkerResult run(
			String owner,
			String stage,
			String runId,
			File trainingFile,
			File testFile,
			File recommendationFile,
			int featureCount,
			int trainingInstanceCount,
			int trainingClassCount,
			AlgorithmParameterSettings settings) throws Exception
	{
		RecommendationRunAbortRegistry.requireActive(runId);
		if (trainingInstanceCount < 2 || trainingClassCount < 2)
		{
			throw new IllegalArgumentException(
					"Scalable SVM needs at least two training users and two followee classes.");
		}
		long arffBytes = fileLength(trainingFile)
				+ fileLength(testFile) + fileLength(recommendationFile);
		long estimateBytes = 384L * MEBIBYTE
				+ safeMultiply(arffBytes, 2L)
				+ safeMultiply(
						safeMultiply(
								Math.max(1, featureCount),
								Math.max(1, trainingClassCount)),
						5L)
				+ safeMultiply(Math.max(1, trainingClassCount), 64L);
		long heapMb = Math.max(
				1024L,
				(estimateBytes + MEBIBYTE - 1L) / MEBIBYTE);
		heapMb = ((heapMb + 255L) / 256L) * 256L;
		if (heapMb > ExactSvmWorkerCoordinator.totalBudgetMb())
		{
			throw new IllegalArgumentException(
					"Scalable SVM needs an estimated "
					+ RuntimeResourceSettings.formatMegabytes(heapMb)
					+ " for one isolated worker, but only "
					+ RuntimeResourceSettings.formatMegabytes(
							ExactSvmWorkerCoordinator.totalBudgetMb())
					+ " is safely available.");
		}

		File resultFile = Files.createTempFile(
				"scalable-svm-result-", ".bin").toFile();
		try (ExactSvmWorkerCoordinator.Lease ignored =
				ExactSvmWorkerCoordinator.acquire(owner, stage, heapMb))
		{
			RecommendationRunAbortRegistry.requireActive(runId);
			NodeExecutionMonitor.modelRunning(
					owner,
					stage + ": sparse multiclass max-margin SVM in isolated worker; "
					+ trainingInstanceCount + " training users, "
					+ trainingClassCount + " declared classes, "
					+ "streamed sparse training, worker heap "
					+ RuntimeResourceSettings.formatMegabytes(heapMb));
			List<String> command = new ArrayList<String>();
			command.add(System.getProperty("java.home") + File.separator
					+ "bin" + File.separator + "java");
			command.add("-Xms256m");
			command.add("-Xmx" + heapMb + "m");
			addModernJavaCompatibilityOptions(command);
			command.add("-Djava.awt.headless=true");
			(settings == null
					? AlgorithmParameterSettings.defaults()
					: settings).addJavaSystemProperties(command);
			command.add("-cp");
			command.add(System.getProperty("java.class.path"));
			command.add(ScalableSvmWorkerMain.class.getName());
			command.add(trainingFile.getAbsolutePath());
			command.add(testFile.getAbsolutePath());
			command.add(recommendationFile.getAbsolutePath());
			command.add(resultFile.getAbsolutePath());

			ModelWorkerProcessSupport.Result processResult =
					ModelWorkerProcessSupport.run(
							command,
							ModelWorkerProcessSupport.configuredTimeoutSeconds());
			int exitCode = processResult.getExitCode();
			if (processResult.isTimedOut())
			{
				throw new IllegalStateException(
						"Scalable SVM worker exceeded the configured containment "
						+ "timeout and was stopped.");
			}
			if (exitCode != 0 || resultFile.length() == 0L)
			{
				throw new IllegalStateException(
						"Scalable SVM worker exited with code " + exitCode
						+ ". " + summarize(processResult.getOutput()));
			}
			try (ObjectInputStream input = new ObjectInputStream(
					new FileInputStream(resultFile)))
			{
				return (ExactSvmWorkerResult)input.readObject();
			}
		}
		finally
		{
			Files.deleteIfExists(resultFile.toPath());
		}
	}

	private static long fileLength(File file)
	{
		return file != null && file.isFile() ? file.length() : 0L;
	}

	private static void addModernJavaCompatibilityOptions(List<String> command)
	{
		String specificationVersion =
				System.getProperty("java.specification.version", "8");
		if (!specificationVersion.startsWith("1."))
		{
			command.add("--add-opens");
			command.add("java.base/java.lang=ALL-UNNAMED");
		}
	}

	private static long safeMultiply(long first, long second)
	{
		if (first <= 0L || second <= 0L)
		{
			return 0L;
		}
		return first > Long.MAX_VALUE / second
				? Long.MAX_VALUE : first * second;
	}

	private static String summarize(String output)
	{
		String trimmed = output == null ? "" : output.trim();
		if (trimmed.length() == 0)
		{
			return "No worker diagnostic was produced.";
		}
		return trimmed.length() <= 1200
				? trimmed : trimmed.substring(trimmed.length() - 1200);
	}
}
