package TwitterGatherDataFollowers.userRyersonU;

import java.io.File;

/**
 * Runs the scalable worker against already-generated simulator ARFF files.
 *
 * Usage:
 * ScalableSvmJournalArffSmokeTest <train> <test> <recommend> <features>
 * <training rows> <classes>
 */
public final class ScalableSvmJournalArffSmokeTest
{
	private ScalableSvmJournalArffSmokeTest()
	{
	}

	public static void main(String[] args) throws Exception
	{
		if (args.length != 6)
		{
			throw new IllegalArgumentException(
					"Expected train, test, recommend, features, rows, classes.");
		}
		ExactSvmWorkerResult result = ScalableSvmWorkerRunner.run(
				"JournalSmokeTest",
				"real journal node",
				null,
				new File(args[0]),
				new File(args[1]),
				new File(args[2]),
				Integer.parseInt(args[3]),
				Integer.parseInt(args[4]),
				Integer.parseInt(args[5]));
		if (result.getPredictedClasses().isEmpty())
		{
			throw new AssertionError("No journal recommendation was produced.");
		}
		double[] distribution = result.getDistributions().get(0);
		double total = 0.0;
		int positive = 0;
		for (double score : distribution)
		{
			if (!Double.isFinite(score) || score < 0.0)
			{
				throw new AssertionError("Invalid journal score: " + score);
			}
			total += score;
			if (score > 0.0)
			{
				positive++;
			}
		}
		if (Math.abs(total - 1.0) > 1.0e-9)
		{
			throw new AssertionError(
					"Journal distribution is not normalized: " + total);
		}
		System.out.println(
				"ScalableSvmJournalArffSmokeTest passed: predicted="
				+ result.getPredictedClasses().get(0)
				+ " positiveClasses=" + positive
				+ " accuracy=" + result.getTestAccuracyPercent());
	}
}
