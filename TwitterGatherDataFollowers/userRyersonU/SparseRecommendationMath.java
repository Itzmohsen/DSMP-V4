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

	/**
	 * Link prediction between two users of the user-term bipartite graph: a
	 * user's neighbours are the terms it uses (non-zero weight) and a term's
	 * degree {@code df} is the number of users that use it.
	 * <ul>
	 * <li>common neighbours: |N(u) and N(v)|, ties broken by Adamic-Adar (the
	 *     score is CN + AA / (1 + AA), which keeps the whole-number CN order
	 *     and orders equal counts by how rare the shared terms are, instead of
	 *     by name);</li>
	 * <li>Jaccard: |N(u) and N(v)| / |N(u) or N(v)|;</li>
	 * <li>Adamic-Adar: the sum over shared terms of 1 / ln(df);</li>
	 * <li>resource allocation: the sum over shared terms of 1 / df.</li>
	 * </ul>
	 * A shared term has degree at least two; a missing degree counts as two.
	 */
	static double linkPredictionScore(
			Map<String,Double> doc1,
			Map<String,Double> doc2,
			Map<String,? extends Number> documentFrequency,
			String variant)
	{
		if (doc1 == null || doc2 == null || doc1.isEmpty() || doc2.isEmpty())
		{
			return 0.0;
		}
		Map<String,Double> smallerDoc = doc1.size() <= doc2.size() ? doc1 : doc2;
		Map<String,Double> largerDoc = smallerDoc == doc1 ? doc2 : doc1;
		int shared = 0;
		double adamicAdar = 0.0;
		double resource = 0.0;
		for (Map.Entry<String,Double> entry : smallerDoc.entrySet())
		{
			if (!hasUsableSparseWeight(entry.getValue())
					|| !hasUsableSparseWeight(largerDoc.get(entry.getKey())))
			{
				continue;
			}
			shared++;
			Number frequency = documentFrequency == null ? null : documentFrequency.get(entry.getKey());
			double degree = Math.max(2.0, frequency == null ? 2.0 : frequency.doubleValue());
			adamicAdar += 1.0 / Math.log(degree);
			resource += 1.0 / degree;
		}
		if (shared == 0)
		{
			return 0.0;
		}
		if (AlgorithmParameterSettings.CN_JACCARD.equals(variant))
		{
			int union = usableCount(doc1) + usableCount(doc2) - shared;
			return union <= 0 ? 0.0 : shared / (double)union;
		}
		if (AlgorithmParameterSettings.CN_ADAMIC_ADAR.equals(variant))
		{
			return adamicAdar;
		}
		if (AlgorithmParameterSettings.CN_RESOURCE_ALLOCATION.equals(variant))
		{
			return resource;
		}
		return shared + adamicAdar / (1.0 + adamicAdar);
	}

	private static int usableCount(Map<String,Double> document)
	{
		int count = 0;
		for (Double value : document.values())
		{
			if (hasUsableSparseWeight(value))
			{
				count++;
			}
		}
		return count;
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
