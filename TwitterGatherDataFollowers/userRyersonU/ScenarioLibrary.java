package TwitterGatherDataFollowers.userRyersonU;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Saved scenarios: {@code Scenarios/*.scenario} files next to the built-in
 * library ({@link Scenario#library()}).
 */
final class ScenarioLibrary
{
	private ScenarioLibrary()
	{
	}

	static File directory()
	{
		return new File(System.getProperty("dsmp.scenarios.dir", "Scenarios"));
	}

	/** Every readable saved scenario, by file name. */
	static List<Scenario> saved()
	{
		ArrayList<Scenario> scenarios = new ArrayList<Scenario>();
		File[] files = directory().listFiles();
		if (files == null)
		{
			return scenarios;
		}
		java.util.Arrays.sort(files);
		for (File file : files)
		{
			if (!file.getName().endsWith(".scenario"))
			{
				continue;
			}
			try
			{
				scenarios.add(load(file));
			}
			catch (Exception unreadable)
			{
				System.err.println("Skipping scenario file " + file + ": " + unreadable.getMessage());
			}
		}
		return scenarios;
	}

	/** The built-in scenarios followed by the saved ones. */
	static List<Scenario> all()
	{
		ArrayList<Scenario> scenarios = new ArrayList<Scenario>(Scenario.library());
		scenarios.addAll(saved());
		return scenarios;
	}

	static Scenario load(File file) throws IOException, Scenario.ScenarioException
	{
		return Scenario.parse(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
	}

	/** Saves the scenario's text as {@code Scenarios/<name>.scenario}; returns the file. */
	static File save(Scenario scenario) throws IOException
	{
		File directory = directory();
		if (!directory.isDirectory() && !directory.mkdirs())
		{
			throw new IOException("Cannot create " + directory);
		}
		String fileName = scenario.getName().replaceAll("[^A-Za-z0-9._-]+", "-").replaceAll("^-|-$", "");
		File file = new File(directory, (fileName.length() == 0 ? "scenario" : fileName) + ".scenario");
		String text = scenario.toText();
		if (!text.toLowerCase(java.util.Locale.US).startsWith("name:"))
		{
			text = "name: " + scenario.getName() + "\n" + text;
		}
		Files.write(file.toPath(), text.getBytes(StandardCharsets.UTF_8));
		return file;
	}
}
