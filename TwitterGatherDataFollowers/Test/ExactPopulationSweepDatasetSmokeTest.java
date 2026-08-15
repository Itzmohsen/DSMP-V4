package TwitterGatherDataFollowers.userRyersonU;

import java.io.File;
import java.util.Locale;

public final class ExactPopulationSweepDatasetSmokeTest
{
	private ExactPopulationSweepDatasetSmokeTest()
	{
	}

	public static void main(String[] args) throws Exception
	{
		if (args.length < 1)
		{
			throw new IllegalArgumentException(
					"usage: <dataset> [SIMILARITY|SVM|DOC2VEC] [nodes] [targetLimit]");
		}
		ExactPopulationSweepEngine.Algorithm algorithm = args.length > 1
				? ExactPopulationSweepEngine.Algorithm.valueOf(
						args[1].toUpperCase(Locale.US))
				: ExactPopulationSweepEngine.Algorithm.SIMILARITY;
		int nodes = args.length > 2 ? Integer.parseInt(args[2]) : 4;
		int targetLimit = args.length > 3 ? Integer.parseInt(args[3]) : 0;
		RecommendationMemoryCoordinator.configureRequestedParallelism(nodes);
		ExactPopulationSweepEngine.Report report =
				ExactPopulationSweepEngine.run(
						new ExactPopulationSweepEngine.Config(
								new File(args[0]),
								algorithm,
								nodes,
								3,
								targetLimit,
								"2007-01-01",
								"2099-12-31",
								true,
								true,
								true,
								true,
								System.getProperty(
										"recommender.python.command", "python3")),
						null,
						new ExactPopulationSweepEngine.CancellationToken());
		if (!report.isVerified())
		{
			throw new AssertionError("real-dataset sweep was not verified");
		}
		System.out.println(
				"ExactPopulationSweepDatasetSmokeTest passed algorithm="
						+ algorithm
						+ " targets=" + report.getTotalTargets()
						+ " baselineMs=" + report.getBaselineMillis()
						+ " parallelMs=" + report.getParallelMillis()
						+ " speedup=" + report.getSpeedup()
						+ " digest=" + report.getResultDigest());
	}
}
