package TwitterGatherDataFollowers.userRyersonU;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The global set of candidate users and their division into work units, for
 * the per-candidate scoring algorithms (Similarity, Common-Neighbors).
 *
 * Every node contributes the TF-IDF vectors of the candidates it owns; the
 * contributions are combined in node order, so every node builds the same
 * plan: candidates grouped by owner, sorted by name, and cut into contiguous
 * shards of similar estimated cost. A shard's home is its owner, the node that
 * already holds those vectors; running it elsewhere moves them over the
 * network (the unit's input bytes). Each candidate belongs to exactly one
 * shard, so scoring every shard once scores every candidate once. Only the
 * layout (names, owners, costs) is exchanged when the plan is built.
 */
final class CandidateShardPlan
{
	/** Estimated milliseconds per sparse term lookup, for unit costs. */
	static final double MILLISECONDS_PER_TERM_LOOKUP = 5.0e-5;

	private final List<List<String>> shardUsers = new ArrayList<List<String>>();
	private final List<WorkUnitSpec> units = new ArrayList<WorkUnitSpec>();
	private final Map<String,Map<String,Double>> vectors = new LinkedHashMap<String,Map<String,Double>>();
	private final Map<String,Integer> owners = new LinkedHashMap<String,Integer>();
	private final int candidateCount;

	/**
	 * @param ownedVectorsByNode node i's owned candidates (index i - 1)
	 * @param targetCount        number of targets each candidate is scored against
	 * @param totalLanes         lanes across the run, to size shards
	 */
	CandidateShardPlan(
			List<? extends Map<String,? extends Map<String,Double>>> ownedVectorsByNode,
			int targetCount,
			int totalLanes)
	{
		int nodes = ownedVectorsByNode.size();
		long totalCost = 0L;
		List<List<String>> sortedByOwner = new ArrayList<List<String>>();
		for (int node = 1; node <= nodes; node++)
		{
			Map<String,? extends Map<String,Double>> owned = ownedVectorsByNode.get(node - 1);
			ArrayList<String> users = new ArrayList<String>();
			if (owned != null)
			{
				for (Map.Entry<String,? extends Map<String,Double>> entry : owned.entrySet())
				{
					String user = entry.getKey();
					if (owners.containsKey(user))
					{
						throw new IllegalStateException(
								"Candidate " + user + " is owned by nodes " + owners.get(user)
								+ " and " + node + "; ownership must be exclusive.");
					}
					Map<String,Double> vector = entry.getValue();
					vectors.put(user, vector);
					owners.put(user, Integer.valueOf(node));
					users.add(user);
					totalCost += cost(vector, targetCount);
				}
			}
			Collections.sort(users);
			sortedByOwner.add(users);
		}
		candidateCount = owners.size();
		long unitTarget = Math.max(1L, totalCost / Math.max(1L, 4L * Math.max(1, totalLanes)));
		for (int node = 1; node <= nodes; node++)
		{
			List<String> users = sortedByOwner.get(node - 1);
			ArrayList<String> current = new ArrayList<String>();
			long currentCost = 0L;
			int chunk = 0;
			for (String user : users)
			{
				current.add(user);
				currentCost += cost(vectors.get(user), targetCount);
				if (currentCost >= unitTarget)
				{
					addUnit(node, chunk++, current, currentCost);
					current = new ArrayList<String>();
					currentCost = 0L;
				}
			}
			if (!current.isEmpty())
			{
				addUnit(node, chunk, current, currentCost);
			}
		}
	}

	private static long cost(Map<String,Double> vector, int targetCount)
	{
		return (long)(1 + (vector == null ? 0 : vector.size())) * Math.max(1, targetCount);
	}

	private void addUnit(int owner, int chunk, List<String> users, long cost)
	{
		int id = units.size() + 1;
		shardUsers.add(Collections.unmodifiableList(new ArrayList<String>(users)));
		//The shard's input is its candidates' vectors, held by the owner: a shard
		//that runs elsewhere moves them over the network.
		long sizingStart = NodeEmulator.threadCpuNanos();
		long bytes = 4L;
		for (String user : users)
		{
			bytes += PayloadSize.string(user) + PayloadSize.sparseVector(vectors.get(user));
		}
		PayloadSize.excludeCpu(NodeEmulator.threadCpuNanos() - sizingStart);
		units.add(new WorkUnitSpec(
				id,
				String.format("o%04d-c%06d", owner, chunk),
				cost * MILLISECONDS_PER_TERM_LOOKUP,
				0,
				0,
				Double.POSITIVE_INFINITY,
				owner,
				null).withData(bytes, 0L));
	}

	/**
	 * Wire size of one node's contribution to the plan: the names and estimated
	 * costs of the candidates it owns (their vectors stay home until a shard
	 * needs them elsewhere).
	 */
	static long layoutBytes(Map<String,? extends Map<String,Double>> owned)
	{
		long bytes = 4L;
		if (owned != null)
		{
			for (String user : owned.keySet())
			{
				bytes += PayloadSize.string(user) + 8L;
			}
		}
		return bytes;
	}

	/** Every node downloads the layout of every shard: names, owners and costs. */
	static final RunCollectives.DownloadSizer LAYOUT = new RunCollectives.DownloadSizer()
	{
		public long bytesFor(int node, Object result, List<Long> uploadBytesByNode,
				List<Object> contributionsByNode)
		{
			long total = 4L;
			for (Long upload : uploadBytesByNode)
			{
				total += upload.longValue();
			}
			return total + (result instanceof CandidateShardPlan
					? 16L * ((CandidateShardPlan)result).units.size() : 0L);
		}
	};

	List<WorkUnitSpec> units()
	{
		return Collections.unmodifiableList(units);
	}

	/** Candidates of unit {@code unitId}, sorted by name. */
	List<String> candidatesOf(int unitId)
	{
		return shardUsers.get(unitId - 1);
	}

	Map<String,Double> vectorOf(String user)
	{
		return vectors.get(user);
	}

	int ownerOf(String user)
	{
		Integer owner = owners.get(user);
		return owner == null ? 0 : owner.intValue();
	}

	int candidateCount()
	{
		return candidateCount;
	}

	/** Sorted names of every candidate in the run. */
	List<String> allCandidates()
	{
		ArrayList<String> all = new ArrayList<String>(owners.keySet());
		Collections.sort(all);
		return all;
	}

	/** Copies a vector in sorted term order (a stable, order-independent layout). */
	static Map<String,Double> sortedCopy(Map<String,Double> vector)
	{
		return vector == null ? new TreeMap<String,Double>() : new TreeMap<String,Double>(vector);
	}
}
