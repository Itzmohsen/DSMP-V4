package TwitterGatherDataFollowers.userRyersonU;

import java.util.Collections;
import java.util.Map;

final class SparseRecommendationMath
{
	private SparseRecommendationMath()
	{
	}

	static double commonNeighborScore(
			Map<String,Double> doc1,
			Map<String,Double> doc2)
	{
		if (doc1 == null || doc2 == null || doc1.isEmpty() || doc2.isEmpty())
		{
			return 0.0;
		}

		Map<String,Double> smallerDoc = doc1;
		Map<String,Double> largerDoc = doc2;
		if (doc2.size() < doc1.size())
		{
			smallerDoc = doc2;
			largerDoc = doc1;
		}

		int sharedNeighbors = 0;
		for (Map.Entry<String,Double> entry : smallerDoc.entrySet())
		{
			if (!hasUsableSparseWeight(entry.getValue()))
			{
				continue;
			}
			if (hasUsableSparseWeight(largerDoc.get(entry.getKey())))
			{
				sharedNeighbors++;
			}
		}
		return sharedNeighbors;
	}

	static double sparseEuclideanDistance(
			Map<String,Double> doc1,
			Map<String,Double> doc2)
	{
		if ((doc1 == null || doc1.isEmpty())
				&& (doc2 == null || doc2.isEmpty()))
		{
			return 0.0;
		}
		if (doc1 == null)
		{
			doc1 = Collections.emptyMap();
		}
		if (doc2 == null)
		{
			doc2 = Collections.emptyMap();
		}

		double sum = 0.0;
		for (Map.Entry<String,Double> entry : doc1.entrySet())
		{
			double firstValue = safeSparseValue(entry.getValue());
			double secondValue = safeSparseValue(doc2.get(entry.getKey()));
			double difference = firstValue - secondValue;
			sum += difference * difference;
		}
		for (Map.Entry<String,Double> entry : doc2.entrySet())
		{
			if (doc1.containsKey(entry.getKey()))
			{
				continue;
			}
			double secondValue = safeSparseValue(entry.getValue());
			sum += secondValue * secondValue;
		}
		return Math.sqrt(sum);
	}

	static double euclideanSimilarityScore(
			Map<String,Double> doc1,
			Map<String,Double> doc2)
	{
		double distance = sparseEuclideanDistance(doc1, doc2);
		if (!Double.isFinite(distance))
		{
			return 0.0;
		}
		return 1.0 / (1.0 + distance);
	}

	private static boolean hasUsableSparseWeight(Double value)
	{
		return value != null
				&& Double.isFinite(value.doubleValue())
				&& value.doubleValue() != 0.0;
	}

	private static double safeSparseValue(Double value)
	{
		if (value == null || !Double.isFinite(value.doubleValue()))
		{
			return 0.0;
		}
		return value.doubleValue();
	}
}
