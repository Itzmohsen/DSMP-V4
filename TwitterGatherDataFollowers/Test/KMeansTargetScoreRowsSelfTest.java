package TwitterGatherDataFollowers.userRyersonU;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

public final class KMeansTargetScoreRowsSelfTest
{
	private KMeansTargetScoreRowsSelfTest()
	{
	}

	@SuppressWarnings("unchecked")
	public static void main(String[] args) throws Exception
	{
		Class<?> behaviourClass = Class.forName(
				"TwitterGatherDataFollowers.userRyersonU.RecommenderAgent$RecommenderServiceBehaviour");
		Method initializeRows = behaviourClass.getDeclaredMethod(
				"initializeTargetScoreRows",
				java.util.Collection.class,
				String[].class);
		Method putScore = behaviourClass.getDeclaredMethod(
				"putScoreForTrackedTargets",
				Map.class,
				String.class,
				String.class,
				double.class);
		initializeRows.setAccessible(true);
		putScore.setAccessible(true);

		String[] users = new String[] { "TargetA", "CandidateB", "CandidateC" };
		TreeMap<String,TreeMap<String,Double>> scores =
				(TreeMap<String,TreeMap<String,Double>>) initializeRows.invoke(
						null,
						Collections.singletonList("TargetA"),
						users);

		require(scores.size() == 1,
				"K-Means should keep only selected target score rows");
		require(scores.containsKey("TargetA"),
				"selected target score row was not created");
		require(!scores.containsKey("CandidateB"),
				"non-target score row should not be created");
		require(scores.get("TargetA").size() == 2,
				"target row should still include all candidates");

		putScore.invoke(null, scores, "TargetA", "CandidateB", 42.0);
		putScore.invoke(null, scores, "CandidateB", "CandidateC", 99.0);

		require(Double.compare(scores.get("TargetA").get("CandidateB"), 42.0) == 0,
				"target pair score was not preserved");
		require(Double.compare(scores.get("TargetA").get("CandidateC"), 0.0) == 0,
				"non-target pair should not alter unrelated target candidate score");
		require(!scores.containsKey("CandidateB"),
				"putScore should not create non-target rows");

		TreeMap<String,TreeMap<String,Double>> duplicateTargets =
				(TreeMap<String,TreeMap<String,Double>>) initializeRows.invoke(
						null,
						Arrays.asList("TargetA", "TargetA", "MissingTarget"),
						users);
		require(duplicateTargets.size() == 1,
				"duplicate or missing targets should not inflate target rows");

		System.out.println("KMeansTargetScoreRowsSelfTest passed");
	}

	private static void require(boolean condition, String message)
	{
		if (!condition)
		{
			throw new AssertionError(message);
		}
	}
}
