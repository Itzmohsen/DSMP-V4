package TwitterGatherDataFollowers.userRyersonU;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Guards the two K-Means defects that silently produced wrong clusterings:
 * a convergence snapshot that aliased the live cluster lists (so the loop always
 * "converged" after one pass), and seed selection that ignored its own shuffle in
 * favour of hardcoded account names.
 */
public final class KMeansConvergenceSelfTest
{
	private KMeansConvergenceSelfTest()
	{
	}

	public static void main(String[] args)
	{
		snapshotIsDetachedFromTheLiveClusterLists();
		snapshotDetectsBothGainsAndLosses();
		seedSelectionIsDeterministic();
		seedSelectionRespondsToTheSeed();
		seedSelectionSpreadsSeedsApart();
		seedSelectionAlwaysFillsEveryCluster();
		System.out.println("KMeansConvergenceSelfTest passed");
	}

	/**
	 * The regression itself: clearing and refilling the clusters must not change
	 * a snapshot that was already taken.
	 */
	private static void snapshotIsDetachedFromTheLiveClusterLists()
	{
		List<Cluster> clusters = clusters(2);
		clusters.get(0).addPoint(point("alice"));
		clusters.get(1).addPoint(point("bob"));

		Map<String,Integer> before =
				KMeansSupport.snapshotClusterAssignments(clusters);

		for (Cluster cluster : clusters)
		{
			cluster.clear();
		}
		clusters.get(1).addPoint(point("alice"));
		clusters.get(0).addPoint(point("bob"));

		require(before.size() == 2, "snapshot lost entries");
		require(before.get("alice").intValue() == 0,
				"snapshot followed the live list instead of detaching");
		require(before.get("bob").intValue() == 1,
				"snapshot followed the live list instead of detaching");

		Map<String,Integer> after =
				KMeansSupport.snapshotClusterAssignments(clusters);
		require(!before.equals(after),
				"a changed assignment was reported as converged");
	}

	/** A cluster that only gains members must not read as converged. */
	private static void snapshotDetectsBothGainsAndLosses()
	{
		List<Cluster> clusters = clusters(2);
		clusters.get(0).addPoint(point("alice"));
		Map<String,Integer> before =
				KMeansSupport.snapshotClusterAssignments(clusters);

		clusters.get(0).addPoint(point("bob"));
		Map<String,Integer> after =
				KMeansSupport.snapshotClusterAssignments(clusters);

		require(!before.equals(after),
				"a cluster gaining a member was reported as converged");
	}

	private static void seedSelectionIsDeterministic()
	{
		Map<String,Map<String,Double>> documents = documents();
		List<String> first = KMeansSupport.selectSeedUsers(documents.keySet(), documents, 3, 1L);
		List<String> second = KMeansSupport.selectSeedUsers(shuffledNames(documents), documents, 3, 1L);

		require(first.equals(second),
				"seed selection depended on input order: " + first + " vs " + second);
		require(first.size() == 3, "wrong number of seeds: " + first);
	}

	private static void seedSelectionRespondsToTheSeed()
	{
		Map<String,Map<String,Double>> documents = documents();
		List<String> seedOne = KMeansSupport.selectSeedUsers(documents.keySet(), documents, 2, 0L);
		List<String> seedTwo = KMeansSupport.selectSeedUsers(documents.keySet(), documents, 2, 1L);

		require(!seedOne.get(0).equals(seedTwo.get(0)),
				"changing the seed did not change the initialisation");
	}

	/** Farthest-first must not pick two seeds from the same tight group. */
	private static void seedSelectionSpreadsSeedsApart()
	{
		LinkedHashMap<String,Map<String,Double>> documents =
				new LinkedHashMap<String,Map<String,Double>>();
		documents.put("a1", vector("cat", 1.0));
		documents.put("a2", vector("cat", 1.0));
		documents.put("b1", vector("dog", 1.0));

		List<String> seeds = KMeansSupport.selectSeedUsers(documents.keySet(), documents, 2, 0L);
		require(seeds.contains("b1"),
				"farthest-first ignored the distant document: " + seeds);
	}

	/** Fewer distinct users than clusters must still yield one seed per cluster. */
	private static void seedSelectionAlwaysFillsEveryCluster()
	{
		LinkedHashMap<String,Map<String,Double>> documents =
				new LinkedHashMap<String,Map<String,Double>>();
		documents.put("only", vector("cat", 1.0));

		List<String> seeds = KMeansSupport.selectSeedUsers(documents.keySet(), documents, 3, 0L);
		require(seeds.size() == 3,
				"seed list was short, which would throw during cluster creation: "
				+ seeds);
	}

	private static Map<String,Map<String,Double>> documents()
	{
		LinkedHashMap<String,Map<String,Double>> documents =
				new LinkedHashMap<String,Map<String,Double>>();
		documents.put("alice", vector("cat", 1.0));
		documents.put("bob", vector("dog", 1.0));
		documents.put("carol", vector("bird", 1.0));
		documents.put("dave", vector("cat", 0.9));
		return documents;
	}

	private static List<String> shuffledNames(Map<String,Map<String,Double>> documents)
	{
		List<String> names = new ArrayList<String>(documents.keySet());
		java.util.Collections.reverse(names);
		return names;
	}

	private static Map<String,Double> vector(String term, double weight)
	{
		LinkedHashMap<String,Double> vector = new LinkedHashMap<String,Double>();
		vector.put(term, Double.valueOf(weight));
		return vector;
	}

	private static List<Cluster> clusters(int count)
	{
		List<Cluster> clusters = new ArrayList<Cluster>();
		for (int i = 0; i < count; i++)
		{
			clusters.add(new Cluster(i));
		}
		return clusters;
	}

	private static Point point(String userName)
	{
		return new Point(userName, new LinkedHashMap<String,Double>());
	}

	private static void require(boolean condition, String message)
	{
		if (!condition)
		{
			throw new AssertionError(message);
		}
	}
}
