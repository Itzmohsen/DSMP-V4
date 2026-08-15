package TwitterGatherDataFollowers.userRyersonU;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;

public final class ExactPopulationSweepEngineSelfTest
{
	private ExactPopulationSweepEngineSelfTest()
	{
	}

	public static void main(String[] args) throws Exception
	{
		tokenizationMatchesTheActivePipeline();
		similarityIsIdenticalAcrossWorkerCounts();
		svmIsIdenticalAcrossWorkerCounts();
		svmRestoresSchedulerAfterPreparationFailure();
		System.out.println("ExactPopulationSweepEngineSelfTest passed");
	}

	private static void tokenizationMatchesTheActivePipeline()
	{
		List<String> tokens = ExactPopulationSweepEngine.tokenizeForTest(
				"RT @store: The QUICK brown fox visits https://example.com #Retail",
				true,
				false,
				true);
		require(tokens.equals(Arrays.asList(
				"store", "quick", "brown", "fox", "visits", "retail")),
				"population mode text processing diverged: " + tokens);
		require(ExactPopulationSweepEngine.tokenizeForTest(
				"RT @store: useful words remain",
				true,
				true,
				true).isEmpty(),
				"retweet removal no longer matches the simulator");
	}

	private static void similarityIsIdenticalAcrossWorkerCounts()
			throws Exception
	{
		File dataset = createDataset();
		try
		{
			ExactPopulationSweepEngine.Report report =
					ExactPopulationSweepEngine.run(
							config(
									dataset,
									ExactPopulationSweepEngine.Algorithm.SIMILARITY,
									3),
							null,
							new ExactPopulationSweepEngine.CancellationToken());
			require(report.isVerified(),
					"similarity fingerprints differed across worker counts");
			require(report.getTotalTargets() == 8,
					"similarity did not process every target");
			require(report.getResults().size() == 8,
					"similarity result map is incomplete");
			for (ExactPopulationSweepEngine.TargetResult result
					: report.getResults().values())
			{
				require(!result.getTopScores().isEmpty(),
						"similarity returned an empty ranked result for "
								+ result.getTarget());
			}
		}
		finally
		{
			Files.deleteIfExists(dataset.toPath());
		}
	}

	private static void svmIsIdenticalAcrossWorkerCounts()
			throws Exception
	{
		File dataset = createDataset();
		try
		{
			RecommendationMemoryCoordinator.configureRequestedParallelism(2);
			ExactPopulationSweepEngine.Report report =
					ExactPopulationSweepEngine.run(
							config(
									dataset,
									ExactPopulationSweepEngine.Algorithm.SVM,
									2),
							null,
							new ExactPopulationSweepEngine.CancellationToken());
			require(report.isVerified(),
					"SVM fingerprints differed across worker counts");
			require(report.getResults().size() == 8,
					"SVM result map is incomplete");
			for (ExactPopulationSweepEngine.TargetResult result
					: report.getResults().values())
			{
				LinkedHashMap<String,Double> scores =
						result.getTopScores();
				require(!scores.isEmpty(),
						"SVM returned no followee scores for "
								+ result.getTarget());
				for (String className : scores.keySet())
				{
					require("StoreA".equals(className)
									|| "StoreB".equals(className),
							"SVM produced an unexpected class: " + className);
				}
			}
		}
		finally
		{
			Files.deleteIfExists(dataset.toPath());
		}
	}

	private static void svmRestoresSchedulerAfterPreparationFailure()
			throws Exception
	{
		File emptyDataset = File.createTempFile(
				"exact-population-empty-", ".txt");
		int original = RecommendationMemoryCoordinator.snapshot()
				.getRequestedParallelism();
		try
		{
			RecommendationMemoryCoordinator.configureRequestedParallelism(3);
			boolean failed = false;
			try
			{
				ExactPopulationSweepEngine.run(
						config(
								emptyDataset,
								ExactPopulationSweepEngine.Algorithm.SVM,
								2),
						null,
						new ExactPopulationSweepEngine.CancellationToken());
			}
			catch (IllegalArgumentException expected)
			{
				failed = true;
			}
			require(failed, "empty SVM dataset unexpectedly completed");
			require(
					RecommendationMemoryCoordinator.snapshot()
							.getRequestedParallelism() == 3,
					"SVM preparation failure did not restore the scheduler");
		}
		finally
		{
			RecommendationMemoryCoordinator.configureRequestedParallelism(
					original);
			Files.deleteIfExists(emptyDataset.toPath());
		}
	}

	private static ExactPopulationSweepEngine.Config config(
			File dataset,
			ExactPopulationSweepEngine.Algorithm algorithm,
			int nodes)
	{
		return new ExactPopulationSweepEngine.Config(
				dataset,
				algorithm,
				nodes,
				3,
				0,
				"2007-01-01",
				"2099-12-31",
				true,
				true,
				true,
				true,
				"python3");
	}

	private static File createDataset() throws Exception
	{
		File dataset = File.createTempFile(
				"exact-population-sweep-", ".txt");
		try (BufferedWriter writer = new BufferedWriter(
				new OutputStreamWriter(
						new FileOutputStream(dataset),
						StandardCharsets.UTF_8)))
		{
			long tweetId = 1L;
			for (int userIndex = 1; userIndex <= 8; userIndex++)
			{
				String store = userIndex <= 4 ? "StoreA" : "StoreB";
				String user = "user" + userIndex;
				String classTerms = userIndex <= 4
						? "apple fresh market value"
						: "hardware tools building value";
				for (int tweet = 0; tweet < 3; tweet++)
				{
					writer.write(store);
					writer.write('\t');
					writer.write(Long.toString(tweetId));
					writer.write('\t');
					writer.write("2020-01-02");
					writer.write('\t');
					writer.write(Long.toString(1000L + userIndex));
					writer.write('\t');
					writer.write(user);
					writer.write('\t');
					writer.write(classTerms + " unique" + userIndex);
					writer.newLine();
					tweetId++;
				}
			}
		}
		return dataset;
	}

	private static void require(boolean condition, String message)
	{
		if (!condition)
		{
			throw new AssertionError(message);
		}
	}
}
