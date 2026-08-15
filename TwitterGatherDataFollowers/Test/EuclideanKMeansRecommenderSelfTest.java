package TwitterGatherDataFollowers.userRyersonU;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

public final class EuclideanKMeansRecommenderSelfTest
{
	private EuclideanKMeansRecommenderSelfTest()
	{
	}

	public static void main(String[] args)
	{
		shouldKeepArithmeticCentroidMean();
		shouldRankNearestUserInsideCluster();
		shouldUseDeterministicAssignments();
		shouldFallbackWhenTargetClusterHasNoCandidate();
		shouldHandleTinyAndMissingTargets();
		shouldHandleDuplicateVectorsAndBadWeights();
		System.out.println("EuclideanKMeansRecommenderSelfTest passed");
	}

	private static void shouldKeepArithmeticCentroidMean()
	{
		LinkedHashMap<String,Map<String,Double>> docs =
				new LinkedHashMap<String,Map<String,Double>>();
		docs.put("A", vector("x", 1.0));
		docs.put("B", vector("x", 1.0, "y", 1.0));

		LinkedHashMap<String,Integer> assignments =
				new LinkedHashMap<String,Integer>();
		assignments.put("A", 0);
		assignments.put("B", 0);

		Map<String,Double> centroid =
				EuclideanKMeansRecommender.meanVectorForCluster(
						assignments, docs, 0);
		require(closeTo(centroid.get("x"), 1.0),
				"centroid x should be the arithmetic mean");
		require(closeTo(centroid.get("y"), 0.5),
				"centroid y should not be normalized to unit length");
	}

	private static void shouldRankNearestUserInsideCluster()
	{
		LinkedHashMap<String,Map<String,Double>> docs =
				new LinkedHashMap<String,Map<String,Double>>();
		docs.put("Target", vector("milk", 1.0));
		docs.put("Close", vector("milk", 0.9, "bread", 0.1));
		docs.put("Far", vector("engine", 1.0));
		docs.put("FarTwo", vector("engine", 0.9, "steel", 0.1));

		EuclideanKMeansRecommender.Result result =
				EuclideanKMeansRecommender.score(
						docs, Arrays.asList("Target"), 2, 20);
		TreeMap<String,Double> targetScores = result.scores.get("Target");
		require(targetScores != null, "target score row missing");
		require(targetScores.get("Close") > targetScores.get("Far"),
				"nearest same-cluster user should outrank distant users");
		require(result.effectiveClusters == 2,
				"effective cluster count should respect requested clusters");
	}

	private static void shouldUseDeterministicAssignments()
	{
		LinkedHashMap<String,Map<String,Double>> docs =
				new LinkedHashMap<String,Map<String,Double>>();
		docs.put("Z", vector("z", 1.0));
		docs.put("A", vector("a", 1.0));
		docs.put("B", vector("a", 0.9, "b", 0.1));
		docs.put("Y", vector("z", 0.9, "y", 0.1));

		EuclideanKMeansRecommender.Result first =
				EuclideanKMeansRecommender.score(docs, Arrays.asList("A"), 2, 20);
		EuclideanKMeansRecommender.Result second =
				EuclideanKMeansRecommender.score(docs, Arrays.asList("A"), 2, 20);
		require(first.scores.equals(second.scores),
				"Euclidean K-means should be deterministic for the same input");
	}

	private static void shouldFallbackWhenTargetClusterHasNoCandidate()
	{
		LinkedHashMap<String,Map<String,Double>> docs =
				new LinkedHashMap<String,Map<String,Double>>();
		docs.put("Target", vector("only", 1.0));
		docs.put("Other", vector("different", 1.0));

		EuclideanKMeansRecommender.Result result =
				EuclideanKMeansRecommender.score(
						docs, Arrays.asList("Target"), 2, 20);
		double score = result.scores.get("Target").get("Other");
		require(score > 0.0,
				"singleton target clusters should still return nearest fallback scores");
	}

	private static void shouldHandleTinyAndMissingTargets()
	{
		LinkedHashMap<String,Map<String,Double>> docs =
				new LinkedHashMap<String,Map<String,Double>>();
		docs.put("Only", vector("solo", 1.0));

		EuclideanKMeansRecommender.Result oneUser =
				EuclideanKMeansRecommender.score(
						docs, Arrays.asList("Only"), 3, 20);
		require(oneUser.effectiveClusters == 1,
				"one-user input should use one effective cluster");
		require(oneUser.scores.get("Only").isEmpty(),
				"one-user target should have no candidates");

		EuclideanKMeansRecommender.Result missing =
				EuclideanKMeansRecommender.score(
						docs, Arrays.asList("Missing"), 3, 20);
		require(missing.scores.isEmpty(),
				"missing target should not create fake score rows");
	}

	private static void shouldHandleDuplicateVectorsAndBadWeights()
	{
		LinkedHashMap<String,Map<String,Double>> docs =
				new LinkedHashMap<String,Map<String,Double>>();
		docs.put("Target", vector("same", 1.0));
		docs.put("DuplicateA", vector("same", 1.0));
		docs.put("DuplicateB", vector("same", 1.0));
		docs.put("BadWeight", vector("same", Double.NaN, "usable", 1.0));

		EuclideanKMeansRecommender.Result result =
				EuclideanKMeansRecommender.score(
						docs, Arrays.asList("Target"), 3, 20);
		require(result.effectiveClusters == 3,
				"duplicate vectors should still honor the safe effective cluster count");
		require(result.clusters.size() == 3,
				"duplicate-vector repair should keep every requested effective cluster");
		require(result.scores.get("Target").containsKey("DuplicateA"),
				"duplicate candidates should stay scoreable");
		require(!result.scores.get("Target").containsKey("Target"),
				"target should never recommend itself");
	}

	private static Map<String,Double> vector(Object... termsAndWeights)
	{
		Map<String,Double> map = new LinkedHashMap<String,Double>();
		for (int i = 0; i + 1 < termsAndWeights.length; i += 2)
		{
			map.put((String) termsAndWeights[i],
					(Double) termsAndWeights[i + 1]);
		}
		return map;
	}

	private static boolean closeTo(Double actual, double expected)
	{
		return actual != null && Math.abs(actual.doubleValue() - expected) < 1e-9;
	}

	private static void require(boolean condition, String message)
	{
		if (!condition)
		{
			throw new AssertionError(message);
		}
	}
}
