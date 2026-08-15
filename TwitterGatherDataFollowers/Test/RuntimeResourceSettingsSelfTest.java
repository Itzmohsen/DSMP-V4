package TwitterGatherDataFollowers.userRyersonU;

import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

public final class RuntimeResourceSettingsSelfTest
{
	private RuntimeResourceSettingsSelfTest()
	{
	}

	public static void main(String[] args) throws Exception
	{
		require(RuntimeResourceSettings.parseHeapMb("4096") == 4096,
				"valid heap size was not parsed");
		requireFails("256", "small heap size was accepted");
		requireFails("four gigabytes", "non-numeric heap size was accepted");

		List<String> command = RuntimeRelauncher.buildTargetCommand(
				4096,
				Arrays.asList(
						"-Xms128m",
						"-Xmx1424m",
						"--add-opens=java.base/java.lang=ALL-UNNAMED"),
				"lib/*:classes",
				"jade.Boot -local-port 35240 controller:example.Controller");
		require(command.contains("-Xms64m"), "replacement initial heap is missing");
		require(command.contains("-Xmx4096m"), "replacement maximum heap is missing");
		require(!command.contains("-Xmx1424m"), "old maximum heap leaked into relaunch");
		require(command.contains("jade.Boot"), "application main class was lost");
		require(command.contains("controller:example.Controller"),
				"application arguments were lost");

		List<String> quoted = RuntimeRelauncher.tokenizeCommand(
				"jade.Boot \"controller:example Controller\" plain");
		require(quoted.size() == 3, "quoted launch argument was split");
		require("controller:example Controller".equals(quoted.get(1)),
				"quoted launch argument changed");
		recognizesManagedLauncherRestart();
		persistsHeapSetting();
		recommendsHeapForLargeDatasets();
		System.out.println("RuntimeResourceSettingsSelfTest passed");
	}

	private static void recognizesManagedLauncherRestart()
	{
		String originalManaged =
				System.getProperty("dsmp.launcher.managed");
		String originalExitCode =
				System.getProperty("dsmp.restart.exitCode");
		try
		{
			System.setProperty("dsmp.launcher.managed", "true");
			System.setProperty("dsmp.restart.exitCode", "43");
			require(RuntimeRelauncher.isManagedByLauncher(),
					"managed launcher flag was not detected");
			require(RuntimeRelauncher.restartExitCode() == 43,
					"managed launcher restart exit code changed");
		}
		finally
		{
			restoreProperty("dsmp.launcher.managed", originalManaged);
			restoreProperty("dsmp.restart.exitCode", originalExitCode);
		}
	}

	private static void persistsHeapSetting() throws Exception
	{
		String originalUserDirectory = System.getProperty("user.dir");
		Path temporaryDirectory =
				Files.createTempDirectory("runtime-resource-settings-");
		try
		{
			System.setProperty("user.dir", temporaryDirectory.toString());
			RuntimeResourceSettings.saveHeapMb(6144);
			Properties properties = new Properties();
			try (InputStream input = Files.newInputStream(
					temporaryDirectory.resolve(
							".simulator-runtime.properties")))
			{
				properties.load(input);
			}
			require("6144".equals(properties.getProperty("maxHeapMb")),
					"saved heap setting changed");
		}
		finally
		{
			System.setProperty("user.dir", originalUserDirectory);
			Files.deleteIfExists(temporaryDirectory.resolve(
					".simulator-runtime.properties"));
			Files.deleteIfExists(temporaryDirectory);
		}
	}

	private static void recommendsHeapForLargeDatasets() throws Exception
	{
		Path dataset = Files.createTempFile("dsmp-large-dataset-", ".txt");
		try
		{
			try (RandomAccessFile output =
					new RandomAccessFile(dataset.toFile(), "rw"))
			{
				output.setLength(803L * 1024L * 1024L);
			}
			require(RuntimeResourceSettings.recommendedHeapForTextDatasetMb(
					dataset.toFile()) == 8192,
					"large dataset load heap recommendation changed");
			require(RuntimeResourceSettings.recommendedHeapForTextDatasetAndAlgorithmMb(
					dataset.toFile(),
					"K-Means") == 16384,
					"large K-Means heap recommendation changed");
			require(RuntimeResourceSettings.recommendedHeapForTextDatasetAndAlgorithmMb(
					dataset.toFile(),
					"SVM") == 16384,
					"large SVM heap recommendation changed");
			require(RuntimeResourceSettings.recommendedHeapForTextDatasetAndAlgorithmMb(
					dataset.toFile(),
					"MLP") == 24576,
					"large MLP heap recommendation changed");
			require(RuntimeResourceSettings.recommendedHeapForTextDatasetAndAlgorithmMb(
					dataset.toFile(),
					"Doc2Vec") == 8192,
					"large Doc2Vec heap recommendation changed");
		}
		finally
		{
			Files.deleteIfExists(dataset);
		}
	}

	private static void requireFails(String value, String message)
	{
		try
		{
			RuntimeResourceSettings.parseHeapMb(value);
			throw new AssertionError(message);
		}
		catch (IllegalArgumentException expected)
		{
			// Expected.
		}
	}

	private static void require(boolean condition, String message)
	{
		if (!condition)
		{
			throw new AssertionError(message);
		}
	}

	private static void restoreProperty(String key, String value)
	{
		if (value == null)
		{
			System.clearProperty(key);
		}
		else
		{
			System.setProperty(key, value);
		}
	}
}
