package TwitterGatherDataFollowers.userRyersonU;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Guards the multi-node double-counting defect.
 *
 * Recommendation targets are replicated to every recommender node, and the
 * legacy merge sums a candidate seen more than once. Without a per-direction
 * ownership check, a fellow target user accumulated roughly N times the score of
 * an equally similar ordinary candidate on an N-node run.
 */
public final class CandidateOwnershipSelfTest
{
	private static final double EPSILON = 1.0e-9;

	private CandidateOwnershipSelfTest()
	{
	}

	public static void main(String[] args)
	{
		unownedDirectionIsNotWritten();
		ownedDirectionIsStillWritten();
		replicatedTargetIsNotInflatedByNodeCount();
		System.out.println("CandidateOwnershipSelfTest passed");
	}

	/** The node that does not own a candidate must not score it. */
	private static void unownedDirectionIsNotWritten()
	{
		Map<String,TreeMap<String,Double>> rows = rows("t1", "t2");
		//This node owns t1 but not t2.
		KMeansSupport.putScoreForTrackedTargets(
				rows, "t1", "t2", 5.0, true, false);

		require(rows.get("t1").get("t2") == null,
				"scored a candidate owned by another node");
		requireClose(rows.get("t2").get("t1"), 5.0,
				"dropped the direction this node does own");
	}

	private static void ownedDirectionIsStillWritten()
	{
		Map<String,TreeMap<String,Double>> rows = rows("t1", "t2");
		KMeansSupport.putScoreForTrackedTargets(
				rows, "t1", "t2", 4.0, true, true);

		requireClose(rows.get("t1").get("t2"), 4.0, "missing forward direction");
		requireClose(rows.get("t2").get("t1"), 4.0, "missing reverse direction");
	}

	/**
	 * Simulates the merge. Every node holds the replicated target t2 and scores
	 * it identically; only its owner may contribute, so the merged sum is the
	 * single-node value rather than N times it.
	 */
	private static void replicatedTargetIsNotInflatedByNodeCount()
	{
		int nodeCount = 8;
		int ownerNode = 3;
		double perNodeScore = 7.0;

		double merged = 0.0;
		for (int node = 1; node <= nodeCount; node++)
		{
			Map<String,TreeMap<String,Double>> rows = rows("t1", "t2");
			boolean ownsT2 = node == ownerNode;
			KMeansSupport.putScoreForTrackedTargets(
					rows, "t1", "t2", perNodeScore, true, ownsT2);
			Double contribution = rows.get("t1").get("t2");
			if (contribution != null)
			{
				merged += contribution.doubleValue();
			}
		}

		requireClose(merged, perNodeScore,
				"replicated target was summed once per node instead of once");
	}

	private static Map<String,TreeMap<String,Double>> rows(String... targets)
	{
		LinkedHashMap<String,TreeMap<String,Double>> rows =
				new LinkedHashMap<String,TreeMap<String,Double>>();
		List<String> names = new ArrayList<String>();
		for (String target : targets)
		{
			names.add(target);
		}
		for (String target : names)
		{
			rows.put(target, new TreeMap<String,Double>());
		}
		return rows;
	}

	private static void requireClose(Double actual, double expected, String label)
	{
		require(actual != null, label + " (value was absent)");
		require(Math.abs(actual.doubleValue() - expected) < EPSILON,
				label + ": expected " + expected + " but was " + actual);
	}

	private static void require(boolean condition, String message)
	{
		if (!condition)
		{
			throw new AssertionError(message);
		}
	}
}
