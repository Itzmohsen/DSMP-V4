package TwitterGatherDataFollowers.userRyersonU;

import java.util.LinkedHashMap;
import java.util.Map;

public final class SparsePointDistanceSelfTest
{
	private SparsePointDistanceSelfTest()
	{
	}

	public static void main(String[] args)
	{
		Map<String,Double> pointValues = new LinkedHashMap<String,Double>();
		pointValues.put("alpha", 0.6);
		pointValues.put("beta", 0.8);

		Map<String,Double> denseCentroidValues = new LinkedHashMap<String,Double>();
		denseCentroidValues.put("alpha", 0.5);
		denseCentroidValues.put("beta", 0.0);
		denseCentroidValues.put("gamma", 0.5);

		Map<String,Double> sparseCentroidValues = new LinkedHashMap<String,Double>();
		sparseCentroidValues.put("alpha", 0.5);
		sparseCentroidValues.put("gamma", 0.5);

		Point point = new Point("point", pointValues);
		Point denseCentroid = new Point("dense", denseCentroidValues);
		Point sparseCentroid = new Point("sparse", sparseCentroidValues);

		require(point.cosSimDistance(denseCentroid)
				== point.cosSimDistance(sparseCentroid),
				"sparse centroid changed cosine similarity");
		require(point.eucliDistance(denseCentroid)
				== point.eucliDistance(sparseCentroid),
				"sparse centroid changed Euclidean distance");

		Map<String,Double> centroidOnlyValues = new LinkedHashMap<String,Double>();
		centroidOnlyValues.put("gamma", 0.5);
		Point originLikePoint = new Point("origin", new LinkedHashMap<String,Double>());
		Point centroidOnlyPoint = new Point("centroidOnly", centroidOnlyValues);
		require(Math.abs(originLikePoint.eucliDistance(centroidOnlyPoint) - 0.5)
						< 0.0000001,
				"Euclidean distance ignored centroid-only sparse terms");
		System.out.println("SparsePointDistanceSelfTest passed");
	}

	private static void require(boolean condition, String message)
	{
		if (!condition)
		{
			throw new AssertionError(message);
		}
	}
}
