package TwitterGatherDataFollowers.userRyersonU;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;
import java.text.DecimalFormat;
import java.util.Properties;

final class RuntimeResourceSettings
{
	static final int MIN_HEAP_MB = 512;
	static final int HEAP_STEP_MB = 512;
	private static final String SETTINGS_FILE_NAME =
			".simulator-runtime.properties";

	private RuntimeResourceSettings()
	{
	}

	static int currentMaxHeapMb()
	{
		return (int)Math.max(
				MIN_HEAP_MB,
				Runtime.getRuntime().maxMemory() / RecommendationMemoryCoordinator.MEBIBYTE);
	}

	static long totalPhysicalMemoryMb()
	{
		Object operatingSystem = ManagementFactory.getOperatingSystemMXBean();
		String[] methodNames = {"getTotalMemorySize", "getTotalPhysicalMemorySize"};
		for (String methodName : methodNames)
		{
			try
			{
				Method method = com.sun.management.OperatingSystemMXBean.class
						.getMethod(methodName);
				Object value = method.invoke(operatingSystem);
				if (value instanceof Number)
				{
					return ((Number)value).longValue()
							/ RecommendationMemoryCoordinator.MEBIBYTE;
				}
			}
			catch (Exception ignored)
			{
				// Some JVMs do not expose physical-memory information.
			}
		}
		return -1L;
	}

	static int maximumRecommendedHeapMb()
	{
		long physicalMemoryMb = totalPhysicalMemoryMb();
		if (physicalMemoryMb <= 0L)
		{
			return 65536;
		}
		long recommended = physicalMemoryMb * 3L / 4L;
		return (int)Math.max(
				MIN_HEAP_MB,
				Math.min(Integer.MAX_VALUE, recommended));
	}

	static int recommendedHeapForTextDatasetMb(File datasetFile)
	{
		if (datasetFile == null || !datasetFile.isFile())
		{
			return MIN_HEAP_MB;
		}
		long sizeMb = Math.max(
				1L,
				(datasetFile.length() + RecommendationMemoryCoordinator.MEBIBYTE - 1L)
						/ RecommendationMemoryCoordinator.MEBIBYTE);
		long recommended;
		if (sizeMb >= 500L)
		{
			recommended = 8192L;
		}
		else if (sizeMb >= 200L)
		{
			recommended = 4096L;
		}
		else if (sizeMb >= 80L)
		{
			recommended = 2048L;
		}
		else if (sizeMb >= 20L)
		{
			recommended = 1024L;
		}
		else
		{
			recommended = MIN_HEAP_MB;
		}
		return roundHeapMb(Math.max(MIN_HEAP_MB, recommended));
	}

	static int recommendedHeapForTextDatasetAndAlgorithmMb(
			File datasetFile,
			String algorithmName)
	{
		int recommended = recommendedHeapForTextDatasetMb(datasetFile);
		if (datasetFile == null || !datasetFile.isFile())
		{
			return recommended;
		}
		long sizeMb = Math.max(
				1L,
				(datasetFile.length() + RecommendationMemoryCoordinator.MEBIBYTE - 1L)
						/ RecommendationMemoryCoordinator.MEBIBYTE);
		String algorithm = algorithmName == null
				? "" : algorithmName.toLowerCase();
		long algorithmMinimum = recommended;
		if (algorithm.contains("mlp"))
		{
			algorithmMinimum = sizeMb >= 500L ? 24576L
					: sizeMb >= 200L ? 16384L
					: sizeMb >= 80L ? 8192L
					: recommended;
		}
		else if (algorithm.contains("k-means") || algorithm.contains("k means"))
		{
			algorithmMinimum = sizeMb >= 500L ? 16384L
					: sizeMb >= 200L ? 8192L
					: sizeMb >= 80L ? 4096L
					: recommended;
		}
		else if (algorithm.contains("svm")
				&& !algorithm.contains("scalable"))
		{
			algorithmMinimum = sizeMb >= 500L ? 16384L
					: sizeMb >= 200L ? 8192L
					: sizeMb >= 80L ? 4096L
					: recommended;
		}
		else if (algorithm.contains("doc2vec"))
		{
			algorithmMinimum = sizeMb >= 500L ? 4096L
					: sizeMb >= 200L ? 2048L
					: recommended;
		}
		return roundHeapMb(Math.max(recommended, algorithmMinimum));
	}

	static int roundHeapMb(long heapMb)
	{
		long rounded = ((heapMb + HEAP_STEP_MB - 1L) / HEAP_STEP_MB)
				* HEAP_STEP_MB;
		return (int)Math.min(Integer.MAX_VALUE, Math.max(MIN_HEAP_MB, rounded));
	}

	static int parseHeapMb(String value)
	{
		if (value == null)
		{
			throw new IllegalArgumentException("Enter a Java heap size in megabytes.");
		}
		final int heapMb;
		try
		{
			heapMb = Integer.parseInt(value.trim());
		}
		catch (NumberFormatException exception)
		{
			throw new IllegalArgumentException(
					"Java heap must be a whole number of megabytes.");
		}
		if (heapMb < MIN_HEAP_MB)
		{
			throw new IllegalArgumentException(
					"Java heap must be at least " + MIN_HEAP_MB + " MB.");
		}
		return heapMb;
	}

	static boolean isCurrentHeap(int requestedHeapMb)
	{
		int currentHeapMb = currentMaxHeapMb();
		return Math.abs(currentHeapMb - requestedHeapMb) <= 16;
	}

	static void saveHeapMb(int heapMb) throws IOException
	{
		Properties properties = new Properties();
		properties.setProperty("maxHeapMb", Integer.toString(heapMb));
		File settingsFile = new File(
				System.getProperty("user.dir"),
				SETTINGS_FILE_NAME);
		File temporaryFile = new File(
				settingsFile.getParentFile(),
				settingsFile.getName() + ".tmp");
		try (FileOutputStream output = new FileOutputStream(temporaryFile))
		{
			properties.store(
					output,
					"Distributed Recommender Simulator runtime settings");
		}
		if (settingsFile.exists() && !settingsFile.delete())
		{
			throw new IOException(
					"Could not replace " + settingsFile.getAbsolutePath());
		}
		if (!temporaryFile.renameTo(settingsFile))
		{
			throw new IOException(
					"Could not save " + settingsFile.getAbsolutePath());
		}
	}

	static String formatMegabytes(long megabytes)
	{
		if (megabytes >= 1024L)
		{
			return new DecimalFormat("0.#").format(megabytes / 1024.0) + " GB";
		}
		return megabytes + " MB";
	}
}
