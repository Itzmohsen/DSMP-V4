package TwitterGatherDataFollowers.userRyersonU;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.ArrayList;
import java.util.List;

/**
 * Verifies that corpus-wide TF-IDF document frequency is genuinely global:
 * every node sees the same totals, and those totals do not depend on how the
 * corpus was split across nodes.
 */
public final class GlobalDocumentFrequencySelfTest
{
	private GlobalDocumentFrequencySelfTest()
	{
	}

	public static void main(String[] args) throws Exception
	{
		singleNodeReturnsItsOwnCounts();
		everyNodeSeesTheSameGlobalTotals();
		totalsDoNotDependOnThePartition();
		replicatedUsersAreCountedOnce();
		System.out.println("GlobalDocumentFrequencySelfTest passed");
	}

	private static void singleNodeReturnsItsOwnCounts() throws Exception
	{
		GlobalDocumentFrequencyCoordinator.Totals totals =
				GlobalDocumentFrequencyCoordinator.combine(
						"solo", "run-solo", 1, 1, counts("cat", 2, "dog", 1), 3);
		require(totals.getTotalDocuments() == 3, "single-node corpus size changed");
		require(totals.getDocumentFrequency().get("cat").intValue() == 2,
				"single-node document frequency changed");
	}

	private static void everyNodeSeesTheSameGlobalTotals() throws Exception
	{
		final String runId = "run-three-nodes";
		List<Map<String,Integer>> shards = new ArrayList<Map<String,Integer>>();
		shards.add(counts("cat", 2, "dog", 1));
		shards.add(counts("cat", 1, "bird", 4));
		shards.add(counts("dog", 3, "bird", 1));
		int[] documentCounts = {3, 5, 4};

		List<GlobalDocumentFrequencyCoordinator.Totals> results =
				runAllNodes(runId, shards, documentCounts);

		for (GlobalDocumentFrequencyCoordinator.Totals totals : results)
		{
			require(totals.getTotalDocuments() == 12,
					"corpus size was not the sum of all nodes: "
					+ totals.getTotalDocuments());
			require(totals.getDocumentFrequency().get("cat").intValue() == 3,
					"global df for 'cat' was wrong");
			require(totals.getDocumentFrequency().get("dog").intValue() == 4,
					"global df for 'dog' was wrong");
			require(totals.getDocumentFrequency().get("bird").intValue() == 5,
					"global df for 'bird' was wrong");
		}
		for (int i = 1; i < results.size(); i++)
		{
			require(results.get(0).getDocumentFrequency()
							.equals(results.get(i).getDocumentFrequency()),
					"nodes disagreed on the global document frequency");
		}
	}

	/**
	 * The same corpus split two different ways must produce identical totals.
	 * This is the property that makes TF-IDF independent of node count and of
	 * the node-capacity settings.
	 */
	private static void totalsDoNotDependOnThePartition() throws Exception
	{
		List<Map<String,Integer>> twoWay = new ArrayList<Map<String,Integer>>();
		twoWay.add(counts("cat", 3, "dog", 2));
		twoWay.add(counts("cat", 1, "bird", 5));
		GlobalDocumentFrequencyCoordinator.Totals splitInTwo =
				runAllNodes("run-2way", twoWay, new int[] {5, 6}).get(0);

		List<Map<String,Integer>> fourWay = new ArrayList<Map<String,Integer>>();
		fourWay.add(counts("cat", 2));
		fourWay.add(counts("cat", 1, "dog", 2));
		fourWay.add(counts("cat", 1, "bird", 2));
		fourWay.add(counts("bird", 3));
		GlobalDocumentFrequencyCoordinator.Totals splitInFour =
				runAllNodes("run-4way", fourWay, new int[] {2, 3, 3, 3}).get(0);

		require(splitInTwo.getTotalDocuments() == splitInFour.getTotalDocuments(),
				"corpus size depended on the partition");
		require(splitInTwo.getDocumentFrequency()
						.equals(splitInFour.getDocumentFrequency()),
				"document frequency depended on the partition: "
				+ splitInTwo.getDocumentFrequency() + " vs "
				+ splitInFour.getDocumentFrequency());
	}

	/**
	 * Recommendation targets live on every node, so the caller contributes only
	 * the users it owns. This mirrors that call pattern and checks the totals
	 * match a single-node run over the same corpus.
	 *
	 * If a replicated user were contributed by each node instead, both the corpus
	 * size and that user's terms would grow with the node count, and TF-IDF would
	 * be partition-dependent again.
	 */
	private static void replicatedUsersAreCountedOnce() throws Exception
	{
		//Corpus: owned-by-1 {cat}, owned-by-2 {dog}, and a target {cat,bird}
		//replicated onto both nodes but owned by node 1.
		GlobalDocumentFrequencyCoordinator.Totals single =
				GlobalDocumentFrequencyCoordinator.combine(
						"solo", "run-replication-single", 1, 1,
						counts("cat", 2, "dog", 1, "bird", 1), 3);

		List<Map<String,Integer>> owned = new ArrayList<Map<String,Integer>>();
		owned.add(counts("cat", 2, "bird", 1));   // node 1: its own user + the target
		owned.add(counts("dog", 1));              // node 2: holds the target but does not own it
		GlobalDocumentFrequencyCoordinator.Totals distributed =
				runAllNodes("run-replication-two", owned, new int[] {2, 1}).get(0);

		require(distributed.getTotalDocuments() == single.getTotalDocuments(),
				"corpus size changed once a user was replicated: "
				+ distributed.getTotalDocuments() + " vs "
				+ single.getTotalDocuments());
		require(distributed.getDocumentFrequency()
						.equals(single.getDocumentFrequency()),
				"document frequency changed once a user was replicated: "
				+ distributed.getDocumentFrequency() + " vs "
				+ single.getDocumentFrequency());
	}

	/** Runs every node concurrently, as the simulator does. */
	private static List<GlobalDocumentFrequencyCoordinator.Totals> runAllNodes(
			final String runId,
			final List<Map<String,Integer>> shards,
			final int[] documentCounts) throws Exception
	{
		final int nodeCount = shards.size();
		ExecutorService executor = Executors.newFixedThreadPool(nodeCount);
		try
		{
			List<Callable<GlobalDocumentFrequencyCoordinator.Totals>> tasks =
					new ArrayList<Callable<GlobalDocumentFrequencyCoordinator.Totals>>();
			for (int i = 0; i < nodeCount; i++)
			{
				final int nodeNumber = i + 1;
				tasks.add(new Callable<GlobalDocumentFrequencyCoordinator.Totals>() {
					public GlobalDocumentFrequencyCoordinator.Totals call()
							throws Exception
					{
						return GlobalDocumentFrequencyCoordinator.combine(
								"node" + nodeNumber, runId, nodeCount, nodeNumber,
								shards.get(nodeNumber - 1),
								documentCounts[nodeNumber - 1]);
					}
				});
			}
			List<Future<GlobalDocumentFrequencyCoordinator.Totals>> futures =
					executor.invokeAll(tasks);
			List<GlobalDocumentFrequencyCoordinator.Totals> results =
					new ArrayList<GlobalDocumentFrequencyCoordinator.Totals>();
			for (Future<GlobalDocumentFrequencyCoordinator.Totals> future : futures)
			{
				results.add(future.get());
			}
			return results;
		}
		finally
		{
			executor.shutdownNow();
		}
	}

	private static Map<String,Integer> counts(Object... termsAndCounts)
	{
		LinkedHashMap<String,Integer> counts = new LinkedHashMap<String,Integer>();
		for (int i = 0; i + 1 < termsAndCounts.length; i += 2)
		{
			counts.put((String)termsAndCounts[i], (Integer)termsAndCounts[i + 1]);
		}
		return counts;
	}

	private static void require(boolean condition, String message)
	{
		if (!condition)
		{
			throw new AssertionError(message);
		}
	}
}
