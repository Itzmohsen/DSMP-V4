package TwitterGatherDataFollowers.userRyersonU;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Random;

public final class RecommendationPostProcessorSelfTest
{
	private RecommendationPostProcessorSelfTest()
	{
	}

	public static void main(String[] args) throws Exception
	{
		formatsEmptyRecommendationListsSafely();
		choosesAndWritesLegacyOutputsWithoutGraphFiles();
		fallsBackToOneDatasetPassWhenContextIsUnavailable();
		System.out.println("RecommendationPostProcessorSelfTest passed");
	}

	private static void formatsEmptyRecommendationListsSafely()
	{
		require("".equals(MobileAgent.formatRecommendationListScores(
						new LinkedHashMap<String,Double>())),
				"empty recommendation lists must not be substring-trimmed");
		LinkedHashMap<String,Double> scores = new LinkedHashMap<String,Double>();
		scores.put("First", Double.valueOf(1.0));
		scores.put("Second", Double.valueOf(0.5));
		require("First: 1.0, Second: 0.5".equals(
						MobileAgent.formatRecommendationListScores(scores)),
				"recommendation list formatting changed");
	}

	private static void choosesAndWritesLegacyOutputsWithoutGraphFiles()
			throws Exception
	{
		Path project = Files.createTempDirectory("recommendation-post-processing");
		File importantStuff = Files.createDirectories(
				project.resolve("important-stuff")).toFile();
		File unavailableDataset = project.resolve("Retail Sample.txt").toFile();

		Map<String, String> userFollowees = new LinkedHashMap<String, String>();
		userFollowees.put("Alice", "Store A");
		userFollowees.put("Bob", "Store B");
		userFollowees.put("Carol", "Store C");
		RecommendationDatasetContext context = new RecommendationDatasetContext(
				userFollowees,
				new LinkedHashSet<String>(
						Arrays.asList("Store A", "Store B", "Store C")));

		LinkedHashMap<String, Double> scores =
				new LinkedHashMap<String, Double>();
		scores.put("Bob", Double.valueOf(0.0));
		scores.put("Store C", Double.valueOf(10.0));

		RecommendationPostProcessor.Result result =
				RecommendationPostProcessor.process(
						new RecommendationPostProcessor.Request(
								unavailableDataset,
								importantStuff,
								"Alice",
								"Store A",
								"recommendations",
								scores,
								2,
								new Random(1L),
								context));

		require(result.getSelectedRecommendationIndex() == 1,
				"weighted recommendation choice changed");
		require("Store C".equals(result.getSelectedRecommendation()),
				"wrong recommendation was selected");
		require(read(new File(
				importantStuff,
				"outPutSepResult.txt")).equals(
						"Bob : Store B\nStore C : Store C\n\n"),
				"legacy recommendation summary changed");
		require(read(new File(importantStuff, "outputChoice.txt")).equals("Store C"),
				"the follow choice was not written");
		String[] written = importantStuff.list();
		for (String name : written)
		{
			require(!name.startsWith("edges-numbers") && !name.startsWith("name-number"),
					"the run graph lives in the run record, not in " + name);
		}
		require(!new File(
				importantStuff,
				"Dataset-Test-" + unavailableDataset.getName()).exists(),
				"obsolete full dataset copy was generated");
	}

	private static void fallsBackToOneDatasetPassWhenContextIsUnavailable()
			throws Exception
	{
		Path project = Files.createTempDirectory("recommendation-post-fallback");
		File importantStuff = Files.createDirectories(
				project.resolve("important-stuff")).toFile();
		File dataset = project.resolve("sample.txt").toFile();
		Files.write(
				dataset.toPath(),
				("Store A\t1\t2020-01-01\t10\tAlice\talpha\n"
						+ "Store B\t2\t2020-01-01\t11\tBob\tbeta\n")
						.getBytes(StandardCharsets.UTF_8));

		LinkedHashMap<String, Double> scores =
				new LinkedHashMap<String, Double>();
		scores.put("Bob", Double.valueOf(1.0));

		RecommendationPostProcessor.Result result =
				RecommendationPostProcessor.process(
						new RecommendationPostProcessor.Request(
								dataset,
								importantStuff,
								"Alice",
								"Store A",
								"recommendations",
								scores,
								1,
								new Random(1L),
								RecommendationDatasetContext.empty()));

		require("Bob".equals(result.getSelectedRecommendation()),
				"the only recommendation was not chosen");
		require(read(new File(
				importantStuff,
				"outPutSepResult.txt")).equals("Bob : Store B\n\n"),
				"the dataset pass did not resolve Bob's account");
	}

	/** Reads a file with the platform line separator (CRLF on Windows) as "\n". */
	private static String read(File file) throws Exception
	{
		return new String(
				Files.readAllBytes(file.toPath()),
				StandardCharsets.UTF_8).replace(System.lineSeparator(), "\n");
	}

	private static void require(boolean condition, String message)
	{
		if (!condition)
		{
			throw new AssertionError(message);
		}
	}
}
