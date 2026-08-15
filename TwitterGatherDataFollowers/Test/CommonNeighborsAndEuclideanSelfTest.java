package TwitterGatherDataFollowers.userRyersonU;

import java.util.LinkedHashMap;
import java.util.Map;

public final class CommonNeighborsAndEuclideanSelfTest
{
	private CommonNeighborsAndEuclideanSelfTest()
	{
	}

	public static void main(String[] args)
	{
		Map<String,Double> target = vector(
				"milk", 1.0,
				"bread", 0.8,
				"coffee", 0.5);
		Map<String,Double> close = vector(
				"milk", 0.9,
				"bread", 0.7,
				"tea", 0.4);
		Map<String,Double> far = vector(
				"garden", 0.9,
				"tools", 0.6);

		require(SparseRecommendationMath.commonNeighborScore(target, close) == 2.0,
				"Common Neighbors did not count shared nonzero terms");
		require(SparseRecommendationMath.commonNeighborScore(target, far) == 0.0,
				"Common Neighbors counted unrelated terms");

		double closeDistance =
				SparseRecommendationMath.sparseEuclideanDistance(target, close);
		double farDistance =
				SparseRecommendationMath.sparseEuclideanDistance(target, far);
		require(closeDistance < farDistance,
				"Euclidean distance no longer ranks closer sparse vectors first");

		double closeScore =
				SparseRecommendationMath.euclideanSimilarityScore(target, close);
		double farScore =
				SparseRecommendationMath.euclideanSimilarityScore(target, far);
		require(closeScore > farScore,
				"Euclidean similarity score no longer favors lower distance");

		System.out.println("CommonNeighborsAndEuclideanSelfTest passed");
	}

	private static Map<String,Double> vector(Object... termsAndWeights)
	{
		Map<String,Double> map = new LinkedHashMap<String,Double>();
		for (int i = 0; i + 1 < termsAndWeights.length; i += 2)
		{
			map.put((String) termsAndWeights[i], (Double) termsAndWeights[i + 1]);
		}
		return map;
	}

	private static void require(boolean condition, String message)
	{
		if (!condition)
		{
			throw new AssertionError(message);
		}
	}
}
