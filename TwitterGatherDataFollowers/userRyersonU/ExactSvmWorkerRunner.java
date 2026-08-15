package TwitterGatherDataFollowers.userRyersonU;

import java.io.File;
import java.io.FileInputStream;
import java.io.ObjectInputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Performs exact-SVM preflight and launches the unchanged model in a bounded
 * child JVM. A worker crash therefore cannot consume or corrupt the JADE JVM.
 */
final class ExactSvmWorkerRunner
{
	private static final long MEBIBYTE = RecommendationMemoryCoordinator.MEBIBYTE;
	private static final long DEFAULT_MAX_WORKER_MB = calculateDefaultMaxWorkerMb();

	private ExactSvmWorkerRunner()
	{
	}

	static void validateBestCaseStructure(
			int totalInstanceCount,
			int totalClassCount,
			int requestedNodes)
	{
		if (totalClassCount < 2)
		{
			throw new IllegalArgumentException(
					"Exact SVM needs at least two followee classes.");
		}
		int nodes = Math.max(1, requestedNodes);
		int bestCaseClassesPerNode =
				(totalClassCount + nodes - 1) / nodes;
		int bestCaseInstancesPerNode =
				(totalInstanceCount + nodes - 1) / nodes;
		Profile bestCase = Profile.create(
				null,
				null,
				null,
				0,
				bestCaseInstancesPerNode,
				bestCaseClassesPerNode);
		if (bestCase.estimatedHeapMb > DEFAULT_MAX_WORKER_MB
				|| bestCase.estimatedHeapMb
						> ExactSvmWorkerCoordinator.totalBudgetMb())
		{
			throw new IllegalArgumentException(
					"Exact SVM cannot safely fit this dataset even under an ideal "
					+ nodes + "-node class distribution. The dataset has "
					+ String.format(java.util.Locale.US, "%,d", totalClassCount)
					+ " followee classes; the best-case node still needs about "
					+ RuntimeResourceSettings.formatMegabytes(
							bestCase.estimatedHeapMb)
					+ " for calibrated pairwise SMO models. Increase recommender "
					+ "nodes or choose Scalable Linear SVM.");
		}
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
		Profile profile = Profile.create(
				trainingFile,
				testFile,
				recommendationFile,
				featureCount,
				trainingInstanceCount,
				trainingClassCount);
		profile.validate();

		File resultFile = Files.createTempFile(
				"exact-svm-result-", ".bin").toFile();
		try
		{
			long workerHeapMb = profile.estimatedHeapMb;
			long maximumWorkerHeapMb = maximumSafeWorkerHeapMb();
			int heapRetries = 0;
			while (true)
			{
				Files.deleteIfExists(resultFile.toPath());
				ModelWorkerProcessSupport.Result processResult;
				try (ExactSvmWorkerCoordinator.Lease ignored =
						ExactSvmWorkerCoordinator.acquire(
								owner, stage, workerHeapMb))
				{
					RecommendationRunAbortRegistry.requireActive(runId);
					NodeExecutionMonitor.modelRunning(
							owner,
							stage + ": exact SMO in isolated worker; "
							+ profile.describe(workerHeapMb));
					processResult = ModelWorkerProcessSupport.run(
							buildCommand(
									trainingFile,
										testFile,
										recommendationFile,
										resultFile,
										workerHeapMb,
										settings),
								ModelWorkerProcessSupport.configuredTimeoutSeconds());
				}

				if (processResult.isTimedOut())
				{
					throw new IllegalStateException(
							"Exact SVM worker exceeded the configured containment "
							+ "timeout and was stopped.");
				}
				if (processResult.getExitCode() == 0
						&& resultFile.isFile()
						&& resultFile.length() > 0L)
				{
					return readResult(resultFile);
				}

				if (isHeapExhaustionDiagnostic(processResult.getOutput())
						&& heapRetries < configuredMaxHeapRetries()
						&& workerHeapMb < maximumWorkerHeapMb)
				{
					long nextHeapMb = nextRetryHeapMb(
							workerHeapMb, maximumWorkerHeapMb);
					if (nextHeapMb > workerHeapMb)
					{
						heapRetries++;
						String retryMessage = owner
								+ " exact SVM worker exhausted "
								+ RuntimeResourceSettings.formatMegabytes(workerHeapMb)
								+ "; retrying with "
								+ RuntimeResourceSettings.formatMegabytes(nextHeapMb)
								+ " (" + heapRetries + "/"
								+ configuredMaxHeapRetries() + ").";
						System.err.println(retryMessage);
						NodeExecutionMonitor.waitingForMemory(
								owner,
								stage + " retry after worker heap exhaustion",
								nextHeapMb);
						workerHeapMb = nextHeapMb;
						continue;
					}
				}

				throw new IllegalStateException(
						"Exact SVM worker exited with code "
						+ processResult.getExitCode() + ". "
						+ summarize(processResult.getOutput()));
			}
		}
		finally
		{
			Files.deleteIfExists(resultFile.toPath());
		}
	}

	private static List<String> buildCommand(
			File trainingFile,
			File testFile,
			File recommendationFile,
			File resultFile,
			long workerHeapMb,
			AlgorithmParameterSettings settings)
	{
		List<String> command = new ArrayList<String>();
		command.add(javaExecutable());
		command.add("-Xms256m");
		command.add("-Xmx" + workerHeapMb + "m");
		addModernJavaCompatibilityOptions(command);
		command.add("-Djava.awt.headless=true");
		(settings == null
				? AlgorithmParameterSettings.defaults()
				: settings).addJavaSystemProperties(command);
		command.add("-cp");
		command.add(System.getProperty("java.class.path"));
		command.add(ExactSvmWorkerMain.class.getName());
		command.add(trainingFile.getAbsolutePath());
		command.add(testFile.getAbsolutePath());
		command.add(recommendationFile.getAbsolutePath());
		command.add(resultFile.getAbsolutePath());
		return command;
	}

	private static ExactSvmWorkerResult readResult(File resultFile)
			throws Exception
	{
		try (ObjectInputStream input = new ObjectInputStream(
				new FileInputStream(resultFile)))
		{
			Object result = input.readObject();
			if (!(result instanceof ExactSvmWorkerResult))
			{
				throw new IllegalStateException(
						"Exact SVM worker returned an unreadable result.");
			}
			return (ExactSvmWorkerResult)result;
		}
	}

	private static String javaExecutable()
	{
		String executable = System.getProperty("java.home")
				+ File.separator + "bin" + File.separator + "java";
		return new File(executable).getAbsolutePath();
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

	private static long calculateDefaultMaxWorkerMb()
	{
		long configured = readPositiveLongProperty(
				"recommender.exactSvm.maxWorkerMb", -1L);
		if (configured > 0L)
		{
			return configured;
		}
		long physicalMb = RuntimeResourceSettings.totalPhysicalMemoryMb();
		if (physicalMb <= 0L)
		{
			return Math.max(2048L, RuntimeResourceSettings.currentMaxHeapMb());
		}
		return Math.max(2048L, Math.min(16384L, physicalMb / 4L));
	}

	private static long maximumSafeWorkerHeapMb()
	{
		return Math.min(
				DEFAULT_MAX_WORKER_MB,
				ExactSvmWorkerCoordinator.totalBudgetMb());
	}

	private static int configuredMaxHeapRetries()
	{
		String configured = System.getProperty(
				"recommender.svm.worker.maxHeapRetries");
		if (configured == null)
		{
			return 4;
		}
		try
		{
			return Math.max(0, Integer.parseInt(configured.trim()));
		}
		catch (NumberFormatException ignored)
		{
			return 4;
		}
	}

	static boolean isHeapExhaustionDiagnostic(String output)
	{
		if (output == null)
		{
			return false;
		}
		String lower = output.toLowerCase(java.util.Locale.ROOT);
		return lower.contains("java heap space")
				|| lower.contains("gc overhead limit exceeded")
				|| lower.contains("requested array size exceeds vm limit");
	}

	static long nextRetryHeapMb(long currentHeapMb, long maximumHeapMb)
	{
		if (currentHeapMb >= maximumHeapMb)
		{
			return currentHeapMb;
		}
		long doubled = currentHeapMb > Long.MAX_VALUE / 2L
				? Long.MAX_VALUE : currentHeapMb * 2L;
		long increased = currentHeapMb > Long.MAX_VALUE - 1024L
				? Long.MAX_VALUE : currentHeapMb + 1024L;
		long next = Math.max(doubled, increased);
		next = ((next + 255L) / 256L) * 256L;
		return Math.min(maximumHeapMb, next);
	}

	private static long readPositiveLongProperty(String name, long fallback)
	{
		String value = System.getProperty(name);
		if (value == null)
		{
			return fallback;
		}
		try
		{
			long parsed = Long.parseLong(value.trim());
			return parsed > 0L ? parsed : fallback;
		}
		catch (NumberFormatException ignored)
		{
			return fallback;
		}
	}

	private static String summarize(String output)
	{
		if (output == null || output.trim().length() == 0)
		{
			return "No worker diagnostic was produced.";
		}
		String trimmed = output.trim();
		return trimmed.length() <= 1200
				? trimmed : trimmed.substring(trimmed.length() - 1200);
	}

	static final class Profile
	{
		private final int featureCount;
		private final int trainingInstanceCount;
		private final int trainingClassCount;
		private final long pairwiseModelCount;
		private final long arffBytes;
		private final long estimatedHeapMb;

		private Profile(
				int featureCount,
				int trainingInstanceCount,
				int trainingClassCount,
				long pairwiseModelCount,
				long arffBytes,
				long estimatedHeapMb)
		{
			this.featureCount = featureCount;
			this.trainingInstanceCount = trainingInstanceCount;
			this.trainingClassCount = trainingClassCount;
			this.pairwiseModelCount = pairwiseModelCount;
			this.arffBytes = arffBytes;
			this.estimatedHeapMb = estimatedHeapMb;
		}

		static Profile create(
				File trainingFile,
				File testFile,
				File recommendationFile,
				int featureCount,
				int trainingInstanceCount,
				int trainingClassCount)
		{
			long classes = Math.max(0, trainingClassCount);
			long pairs = classes < 2L ? 0L : classes * (classes - 1L) / 2L;
			long arffBytes = fileLength(trainingFile)
					+ fileLength(testFile) + fileLength(recommendationFile);

			long estimateBytes = 384L * MEBIBYTE;
			estimateBytes = safeAdd(estimateBytes, safeMultiply(arffBytes, 8L));
			estimateBytes = safeAdd(
					estimateBytes,
					safeMultiply(
							safeMultiply(
									Math.max(1, featureCount),
									Math.max(1, trainingClassCount)),
							12L));
			// Exact Weka SMO builds one calibrated binary classifier per class
			// pair. This dominates journal-style datasets with thousands of labels.
			estimateBytes = safeAdd(
					estimateBytes,
					safeMultiply(pairs, 24L * 1024L));
			estimateBytes = safeAdd(
					estimateBytes,
					safeMultiply(Math.max(1, trainingInstanceCount), 4096L));
			long estimateMb = Math.max(
					1024L,
					(estimateBytes + MEBIBYTE - 1L) / MEBIBYTE);
			estimateMb = ((estimateMb + 255L) / 256L) * 256L;
			return new Profile(
					featureCount,
					trainingInstanceCount,
					trainingClassCount,
					pairs,
					arffBytes,
					estimateMb);
		}

		void validate()
		{
			if (trainingInstanceCount < 2)
			{
				throw new IllegalArgumentException(
						"Exact SVM needs at least two training users.");
			}
			if (trainingClassCount < 2)
			{
				throw new IllegalArgumentException(
						"Exact SVM needs at least two followee classes in this node's training set.");
			}
			if (estimatedHeapMb > DEFAULT_MAX_WORKER_MB)
			{
				throw new IllegalArgumentException(
						"Exact SVM was rejected before training: "
						+ trainingClassCount + " followee classes require "
						+ String.format(java.util.Locale.US, "%,d", pairwiseModelCount)
						+ " calibrated pairwise models. The estimated isolated-worker heap is "
						+ RuntimeResourceSettings.formatMegabytes(estimatedHeapMb)
						+ ", above the safe per-worker limit of "
						+ RuntimeResourceSettings.formatMegabytes(DEFAULT_MAX_WORKER_MB)
						+ ". Use more recommender nodes so each node receives fewer classes, "
						+ "or choose a model intended for very high-class datasets.");
			}
			if (estimatedHeapMb > ExactSvmWorkerCoordinator.totalBudgetMb())
			{
				throw new IllegalArgumentException(
						"Exact SVM was rejected before training: one isolated worker needs an "
						+ "estimated " + RuntimeResourceSettings.formatMegabytes(estimatedHeapMb)
						+ ", but only "
						+ RuntimeResourceSettings.formatMegabytes(
								ExactSvmWorkerCoordinator.totalBudgetMb())
						+ " is safely available after reserving memory for the simulator and operating system.");
			}
		}

		String describe()
		{
			return describe(estimatedHeapMb);
		}

		String describe(long workerHeapMb)
		{
			return trainingInstanceCount + " training users, "
					+ trainingClassCount + " classes, "
					+ String.format(java.util.Locale.US, "%,d", pairwiseModelCount)
					+ " pairwise models, worker heap "
					+ RuntimeResourceSettings.formatMegabytes(workerHeapMb);
		}

		private static long fileLength(File file)
		{
			return file != null && file.isFile() ? file.length() : 0L;
		}

		private static long safeAdd(long first, long second)
		{
			return first > Long.MAX_VALUE - second
					? Long.MAX_VALUE : first + second;
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
	}
}
