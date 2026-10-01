package TwitterGatherDataFollowers.userRyersonU;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Corpus-wide document frequency for TF-IDF across all recommender nodes.
 *
 * Each node counts document frequency over the users it happens to hold. Used
 * directly, that makes IDF a function of the node partition: the same term is
 * weighted differently on every node, the same target user gets a different
 * vector on every node, and the merge then compares scores that were never in
 * the same vector space. Node count and node-capacity settings both change the
 * ranking as a result.
 *
 * This coordinator restores the textbook definition. Every node publishes its
 * local term counts and user count, the last arrival sums them, and all nodes
 * read back one global document frequency and one global corpus size. TF-IDF
 * then depends only on the corpus, not on how it was split.
 *
 * The barrier follows the same in-JVM pattern as
 * {@link ScalableSvmSharedCoordinator}: all agents share one JADE container, so
 * no new message protocol is needed. Every wait honours the run abort registry
 * and a wall-clock deadline, so a lost node fails the run instead of hanging it.
 */
final class GlobalDocumentFrequencyCoordinator
{
	private static final Object LOCK = new Object();
	private static final Map<String,RunState> RUNS =
			new LinkedHashMap<String,RunState>();
	private static final long EXTRA_WAIT_SECONDS = 3600L;

	private GlobalDocumentFrequencyCoordinator()
	{
	}

	/** Corpus-wide totals, identical on every node for a given run. */
	static final class Totals
	{
		private final Map<String,Integer> documentFrequency;
		private final int totalDocuments;

		private Totals(Map<String,Integer> documentFrequency, int totalDocuments)
		{
			this.documentFrequency = documentFrequency;
			this.totalDocuments = totalDocuments;
		}

		/** Number of users, corpus-wide, whose document contains each term. */
		Map<String,Integer> getDocumentFrequency()
		{
			return Collections.unmodifiableMap(documentFrequency);
		}

		/** Number of user documents in the whole corpus. */
		int getTotalDocuments()
		{
			return totalDocuments;
		}
	}

	/**
	 * Publishes this node's local counts and returns the corpus-wide totals.
	 *
	 * Blocks until every expected node has published. With {@code expectedNodes}
	 * of 1 this returns immediately with the local counts, so single-node runs
	 * take no synchronization at all.
	 *
	 * @param owner              agent name, for diagnostics
	 * @param runId              current recommendation run
	 * @param expectedNodes      number of recommender nodes in this run
	 * @param nodeNumber         1-based index of the calling node
	 * @param localFrequency     term -> local document count
	 * @param localDocumentCount number of user documents held by this node
	 */
	static Totals combine(
			String owner,
			String runId,
			int expectedNodes,
			int nodeNumber,
			Map<String,Integer> localFrequency,
			int localDocumentCount) throws Exception
	{
		int safeExpectedNodes = Math.max(1, expectedNodes);
		if (safeExpectedNodes == 1)
		{
			return new Totals(
					copyCounts(localFrequency),
					Math.max(0, localDocumentCount));
		}

		String key = runId == null ? "__legacy_global_df__" : runId;
		RunState state;
		synchronized (LOCK)
		{
			state = RUNS.get(key);
			if (state == null)
			{
				state = new RunState(safeExpectedNodes);
				RUNS.put(key, state);
			}
			state.requireSameNodeCount(safeExpectedNodes);
			if (!state.submitted.contains(Integer.valueOf(nodeNumber)))
			{
				state.submitted.add(Integer.valueOf(nodeNumber));
				state.addCounts(localFrequency, localDocumentCount);
			}
			if (state.submitted.size() == state.expectedNodes
					&& state.totals == null)
			{
				state.totals = new Totals(
						state.mergedFrequency, state.mergedDocumentCount);
				LOCK.notifyAll();
			}
		}

		if (state.totals == null)
		{
			NodeExecutionMonitor.waitingForSharedModel(
					owner,
					"Waiting for all recommender shards before computing "
					+ "corpus-wide TF-IDF document frequency.");
		}

		long deadline = System.currentTimeMillis()
				+ (ModelWorkerProcessSupport.configuredTimeoutSeconds()
						+ EXTRA_WAIT_SECONDS) * 1000L;
		synchronized (LOCK)
		{
			while (state.totals == null)
			{
				if (RecommendationRunAbortRegistry.isAborted(runId))
				{
					removeRunIfCurrent(key, state);
					LOCK.notifyAll();
					//requireActive throws with the recorded reason. The explicit
					//throw after it guarantees this loop can never spin even if the
					//registry ever reports an abort without a reason to report.
					RecommendationRunAbortRegistry.requireActive(runId);
					throw new IllegalStateException(
							"Recommendation run " + runId + " was aborted while "
							+ "waiting for corpus-wide TF-IDF document frequency.");
				}
				long remaining = deadline - System.currentTimeMillis();
				if (remaining <= 0L)
				{
					removeRunIfCurrent(key, state);
					LOCK.notifyAll();
					throw new IllegalStateException(
							"Timed out waiting for corpus-wide TF-IDF document "
							+ "frequency: " + state.submitted.size() + " of "
							+ state.expectedNodes + " recommender node(s) reported.");
				}
				LOCK.wait(Math.min(1000L, remaining));
			}
			Totals result = state.totals;
			state.consumers++;
			if (state.consumers >= state.expectedNodes)
			{
				removeRunIfCurrent(key, state);
			}
			return result;
		}
	}

	/**
	 * One node's share of the document-frequency exchange when it runs as a
	 * collective of the run ({@link RunCollectives}): its local counts and the
	 * number of documents it owns. On the wire a node sends its non-zero counts
	 * and receives the corpus-wide counts of the terms it holds.
	 */
	static final class Share implements PayloadSize.Sized
	{
		private final Map<String,Integer> counts;
		private final int documents;

		Share(Map<String,Integer> localFrequency, int documents)
		{
			this.counts = localFrequency == null
					? Collections.<String,Integer>emptyMap() : localFrequency;
			this.documents = Math.max(0, documents);
		}

		/** Non-zero counts: term and a 4-byte count each, plus the document count. */
		public long payloadBytes()
		{
			long total = 8L;
			for (Map.Entry<String,Integer> entry : counts.entrySet())
			{
				if (entry.getValue() != null && entry.getValue().intValue() > 0)
				{
					total += PayloadSize.string(entry.getKey()) + 4L;
				}
			}
			return total;
		}

		/** Bytes of the corpus-wide counts of every term this node holds. */
		long neededBytes()
		{
			long total = 8L;
			for (String term : counts.keySet())
			{
				total += PayloadSize.string(term) + 4L;
			}
			return total;
		}
	}

	/** Each node downloads the corpus-wide counts of the terms it holds. */
	static final RunCollectives.DownloadSizer NEEDED_TERMS = new RunCollectives.DownloadSizer()
	{
		public long bytesFor(int node, Object result, java.util.List<Long> uploadBytesByNode,
				java.util.List<Object> contributionsByNode)
		{
			Object share = contributionsByNode.get(node - 1);
			return share instanceof Share ? ((Share)share).neededBytes() : PayloadSize.of(result);
		}
	};

	/**
	 * Sums the shares of every node (integer counts, so the order of the sum is
	 * irrelevant; terms are kept in name order).
	 */
	static Totals combineShares(java.util.List<Object> shares)
	{
		RunState state = new RunState(Math.max(1, shares.size()));
		for (Object share : shares)
		{
			if (share instanceof Share)
			{
				state.addCounts(((Share)share).counts, ((Share)share).documents);
			}
		}
		return new Totals(state.mergedFrequency, state.mergedDocumentCount);
	}

	/** Drops any partial state for a run that ended or was abandoned. */
	static void discard(String runId)
	{
		synchronized (LOCK)
		{
			RUNS.remove(runId == null ? "__legacy_global_df__" : runId);
			LOCK.notifyAll();
		}
	}

	private static void removeRunIfCurrent(String key, RunState state)
	{
		if (RUNS.get(key) == state)
		{
			RUNS.remove(key);
		}
	}

	private static Map<String,Integer> copyCounts(Map<String,Integer> source)
	{
		TreeMap<String,Integer> copy = new TreeMap<String,Integer>();
		if (source != null)
		{
			for (Map.Entry<String,Integer> entry : source.entrySet())
			{
				if (entry.getKey() != null && entry.getValue() != null)
				{
					copy.put(entry.getKey(), entry.getValue());
				}
			}
		}
		return copy;
	}

	private static final class RunState
	{
		private final int expectedNodes;
		private final java.util.Set<Integer> submitted =
				new java.util.LinkedHashSet<Integer>();
		private final TreeMap<String,Integer> mergedFrequency =
				new TreeMap<String,Integer>();
		private int mergedDocumentCount;
		private int consumers;
		private Totals totals;

		private RunState(int expectedNodes)
		{
			this.expectedNodes = expectedNodes;
		}

		private void requireSameNodeCount(int expected)
		{
			if (expected != expectedNodes)
			{
				throw new IllegalStateException(
						"Recommender nodes disagree on the node count for this run: "
						+ expected + " vs " + expectedNodes + ".");
			}
		}

		private void addCounts(Map<String,Integer> localFrequency, int localDocumentCount)
		{
			mergedDocumentCount += Math.max(0, localDocumentCount);
			if (localFrequency == null)
			{
				return;
			}
			for (Map.Entry<String,Integer> entry : localFrequency.entrySet())
			{
				String term = entry.getKey();
				Integer count = entry.getValue();
				if (term == null || count == null || count.intValue() <= 0)
				{
					continue;
				}
				Integer current = mergedFrequency.get(term);
				mergedFrequency.put(
						term,
						Integer.valueOf(
								(current == null ? 0 : current.intValue())
								+ count.intValue()));
			}
		}
	}
}
