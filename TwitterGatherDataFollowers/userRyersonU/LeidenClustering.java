package TwitterGatherDataFollowers.userRyersonU;

import java.util.Arrays;
import java.util.Random;

/**
 * Community detection with the Leiden algorithm (Traag, Waltman and van Eck
 * 2019, "From Louvain to Leiden: guaranteeing well-connected communities",
 * Scientific Reports 9, 5233), maximising modularity at a resolution.
 *
 * Louvain can return badly connected and even disconnected communities;
 * Leiden refines each community before aggregating, so every community it
 * returns is connected. The implementation follows the authors' reference
 * (the CWTS networkanalysis package): modularity is optimised as the constant
 * Potts model with node weights equal to the strengths and resolution
 * gamma / 2m; fast local moving with a queue of unstable nodes; refinement by
 * randomised merging of well-connected singletons (randomness 0.01); and the
 * aggregate network is partitioned starting from the unrefined partition.
 * Iterations repeat until the partition is stable. Node orders come from a
 * seeded generator, so a graph always gets the same communities.
 */
final class LeidenClustering
{
	static final double RANDOMNESS = 0.01;
	private static final int MAX_ITERATIONS = 64;

	/** A partition and its modularity. */
	static final class Result
	{
		/** Community of each node, numbered by size (0 the largest). */
		final int[] membership;
		final int communities;
		/** Modularity at the resolution (NaN for a graph without edges). */
		final double quality;
		final double resolution;

		Result(int[] membership, double quality, double resolution)
		{
			this.membership = membership;
			this.communities = NetworkAlgorithms.groupCount(membership);
			this.quality = quality;
			this.resolution = resolution;
		}
	}

	private LeidenClustering()
	{
	}

	/**
	 * The best of {@code starts} Leiden runs (seeds {@code seed},
	 * {@code seed + 1}, ...), each iterated until stable.
	 */
	static Result cluster(NetworkAlgorithms.Undirected graph, double resolution, long seed, int starts)
	{
		int n = graph.n;
		if (graph.total <= 0.0)
		{
			int[] singletons = new int[n];
			for (int node = 0; node < n; node++)
			{
				singletons[node] = node;
			}
			return new Result(singletons, Double.NaN, resolution);
		}
		int[] best = null;
		double bestQuality = Double.NEGATIVE_INFINITY;
		for (int start = 0; start < Math.max(1, starts); start++)
		{
			int[] membership = run(graph, resolution, new Random(seed + start));
			double quality = NetworkAlgorithms.modularity(graph, membership, resolution);
			if (quality > bestQuality + 1e-12)
			{
				best = membership;
				bestQuality = quality;
			}
		}
		int[] numbered = NetworkAlgorithms.renumberBySize(best, NetworkAlgorithms.groupCount(best));
		return new Result(numbered, bestQuality, resolution);
	}

	private static int[] run(NetworkAlgorithms.Undirected graph, double resolution, Random random)
	{
		Network network = Network.of(graph);
		double cpmResolution = resolution / (2.0 * graph.total);
		int[] clusters = new int[network.n];
		for (int node = 0; node < network.n; node++)
		{
			clusters[node] = node;
		}
		for (int iteration = 0; iteration < MAX_ITERATIONS; iteration++)
		{
			if (!iterate(network, clusters, cpmResolution, random))
			{
				break;
			}
		}
		return compact(clusters);
	}

	/** One Leiden iteration; updates {@code clusters} in place and says whether it changed. */
	private static boolean iterate(Network network, int[] clusters, double resolution, Random random)
	{
		boolean update = moveNodesFast(network, clusters, resolution, random);
		int count = NetworkAlgorithms.groupCount(clusters);
		if (count == network.n)
		{
			return update;
		}
		int[][] members = membersOf(clusters, count);
		int[] refined = new int[network.n];
		int refinedCount = 0;
		int[] scratch = new int[network.n];
		Arrays.fill(scratch, -1);
		for (int cluster = 0; cluster < count; cluster++)
		{
			Network sub = network.subnetwork(members[cluster], scratch);
			int[] local = mergeNodesSubset(sub, resolution, random);
			int localCount = NetworkAlgorithms.groupCount(local);
			for (int index = 0; index < members[cluster].length; index++)
			{
				refined[members[cluster][index]] = refinedCount + local[index];
			}
			refinedCount += localCount;
		}
		if (refinedCount == network.n)
		{
			return update;
		}
		Network aggregate = network.aggregate(refined, refinedCount);
		int[] aggregateClusters = new int[refinedCount];
		for (int node = 0; node < network.n; node++)
		{
			aggregateClusters[refined[node]] = clusters[node];
		}
		aggregateClusters = compact(aggregateClusters);
		update |= iterate(aggregate, aggregateClusters, resolution, random);
		for (int node = 0; node < network.n; node++)
		{
			clusters[node] = aggregateClusters[refined[node]];
		}
		return update;
	}

	/**
	 * Fast local moving: visits nodes from a queue (initially all, in random
	 * order) and moves each to the neighbouring cluster, or an empty one, with
	 * the largest quality gain; the neighbours a move unsettles rejoin the
	 * queue. A node stays when staying is as good as the best move.
	 */
	private static boolean moveNodesFast(Network network, int[] clusters, double resolution, Random random)
	{
		int n = network.n;
		if (n <= 1)
		{
			return false;
		}
		double[] clusterWeights = new double[n];
		int[] clusterSizes = new int[n];
		for (int node = 0; node < n; node++)
		{
			clusterWeights[clusters[node]] += network.nodeWeights[node];
			clusterSizes[clusters[node]]++;
		}
		int[] unused = new int[n];
		int unusedCount = 0;
		for (int cluster = n - 1; cluster >= 0; cluster--)
		{
			if (clusterSizes[cluster] == 0)
			{
				unused[unusedCount++] = cluster;
			}
		}
		int[] queue = permutation(n, random);
		boolean[] stable = new boolean[n];
		double[] edgeWeightPerCluster = new double[n];
		int[] neighboring = new int[n];
		int unstable = n;
		int position = 0;
		boolean update = false;
		do
		{
			int node = queue[position];
			int current = clusters[node];
			clusterWeights[current] -= network.nodeWeights[node];
			clusterSizes[current]--;
			if (clusterSizes[current] == 0)
			{
				unused[unusedCount++] = current;
			}
			neighboring[0] = unused[unusedCount - 1];
			int neighborCount = 1;
			for (int edge = network.offsets[node]; edge < network.offsets[node + 1]; edge++)
			{
				int cluster = clusters[network.neighbors[edge]];
				if (edgeWeightPerCluster[cluster] == 0.0)
				{
					neighboring[neighborCount++] = cluster;
				}
				edgeWeightPerCluster[cluster] += network.weights[edge];
			}
			int best = current;
			double bestGain = edgeWeightPerCluster[current]
					- network.nodeWeights[node] * clusterWeights[current] * resolution;
			for (int index = 0; index < neighborCount; index++)
			{
				int cluster = neighboring[index];
				double gain = edgeWeightPerCluster[cluster]
						- network.nodeWeights[node] * clusterWeights[cluster] * resolution;
				if (gain > bestGain)
				{
					best = cluster;
					bestGain = gain;
				}
				edgeWeightPerCluster[cluster] = 0.0;
			}
			clusterWeights[best] += network.nodeWeights[node];
			clusterSizes[best]++;
			if (best == unused[unusedCount - 1])
			{
				unusedCount--;
			}
			stable[node] = true;
			unstable--;
			if (best != current)
			{
				clusters[node] = best;
				for (int edge = network.offsets[node]; edge < network.offsets[node + 1]; edge++)
				{
					int neighbor = network.neighbors[edge];
					if (stable[neighbor] && clusters[neighbor] != best)
					{
						stable[neighbor] = false;
						unstable++;
						int slot = position + unstable;
						queue[slot < n ? slot : slot - n] = neighbor;
					}
				}
				update = true;
			}
			position = position < n - 1 ? position + 1 : 0;
		}
		while (unstable > 0);
		if (update)
		{
			int[] compacted = compact(clusters);
			System.arraycopy(compacted, 0, clusters, 0, n);
		}
		return update;
	}

	/**
	 * Refinement of one cluster (its subnetwork): starting from singletons,
	 * each well-connected node still alone joins a well-connected cluster
	 * with probability proportional to exp(gain / randomness) over the
	 * non-negative gains, so clusters only grow from connected pieces.
	 */
	private static int[] mergeNodesSubset(Network network, double resolution, Random random)
	{
		int n = network.n;
		int[] clusters = new int[n];
		for (int node = 0; node < n; node++)
		{
			clusters[node] = node;
		}
		if (n == 1)
		{
			return clusters;
		}
		double totalNodeWeight = 0.0;
		double[] clusterWeights = new double[n];
		double[] externalEdgeWeight = new double[n];
		for (int node = 0; node < n; node++)
		{
			totalNodeWeight += network.nodeWeights[node];
			clusterWeights[node] = network.nodeWeights[node];
			for (int edge = network.offsets[node]; edge < network.offsets[node + 1]; edge++)
			{
				externalEdgeWeight[node] += network.weights[edge];
			}
		}
		boolean[] nonSingleton = new boolean[n];
		int[] order = permutation(n, random);
		double[] edgeWeightPerCluster = new double[n];
		int[] neighboring = new int[n];
		double[] cumulative = new double[n];
		boolean update = false;
		for (int index = 0; index < n; index++)
		{
			int node = order[index];
			if (nonSingleton[node] || externalEdgeWeight[node]
					< clusterWeights[node] * (totalNodeWeight - clusterWeights[node]) * resolution)
			{
				continue;
			}
			clusterWeights[node] = 0.0;
			externalEdgeWeight[node] = 0.0;
			neighboring[0] = node;
			int neighborCount = 1;
			for (int edge = network.offsets[node]; edge < network.offsets[node + 1]; edge++)
			{
				int cluster = clusters[network.neighbors[edge]];
				if (edgeWeightPerCluster[cluster] == 0.0)
				{
					neighboring[neighborCount++] = cluster;
				}
				edgeWeightPerCluster[cluster] += network.weights[edge];
			}
			int best = node;
			double bestGain = 0.0;
			double total = 0.0;
			for (int slot = 0; slot < neighborCount; slot++)
			{
				int cluster = neighboring[slot];
				if (externalEdgeWeight[cluster]
						>= clusterWeights[cluster] * (totalNodeWeight - clusterWeights[cluster]) * resolution)
				{
					double gain = edgeWeightPerCluster[cluster]
							- network.nodeWeights[node] * clusterWeights[cluster] * resolution;
					if (gain > bestGain)
					{
						best = cluster;
						bestGain = gain;
					}
					if (gain >= 0.0)
					{
						total += Math.exp(gain / RANDOMNESS);
					}
				}
				cumulative[slot] = total;
				edgeWeightPerCluster[cluster] = 0.0;
			}
			int chosen;
			if (total < Double.POSITIVE_INFINITY)
			{
				double r = total * random.nextDouble();
				int low = -1;
				int high = neighborCount - 1;
				while (low < high - 1)
				{
					int middle = (low + high) >>> 1;
					if (cumulative[middle] >= r)
					{
						high = middle;
					}
					else
					{
						low = middle;
					}
				}
				chosen = neighboring[high];
			}
			else
			{
				chosen = best;
			}
			clusterWeights[chosen] += network.nodeWeights[node];
			for (int edge = network.offsets[node]; edge < network.offsets[node + 1]; edge++)
			{
				if (clusters[network.neighbors[edge]] == chosen)
				{
					externalEdgeWeight[chosen] -= network.weights[edge];
				}
				else
				{
					externalEdgeWeight[chosen] += network.weights[edge];
				}
			}
			if (chosen != node)
			{
				clusters[node] = chosen;
				nonSingleton[chosen] = true;
				update = true;
			}
		}
		return update ? compact(clusters) : clusters;
	}

	// ------------------------------------------------------------ helpers

	private static int[] permutation(int n, Random random)
	{
		int[] order = new int[n];
		for (int index = 0; index < n; index++)
		{
			order[index] = index;
		}
		for (int index = n - 1; index > 0; index--)
		{
			int swap = random.nextInt(index + 1);
			int value = order[index];
			order[index] = order[swap];
			order[swap] = value;
		}
		return order;
	}

	/** Renumbers labels 0, 1, ... in order of their smallest id (empty labels removed). */
	private static int[] compact(int[] clusters)
	{
		int max = -1;
		for (int cluster : clusters)
		{
			max = Math.max(max, cluster);
		}
		int[] renamed = new int[max + 1];
		Arrays.fill(renamed, -1);
		boolean[] used = new boolean[max + 1];
		for (int cluster : clusters)
		{
			used[cluster] = true;
		}
		int next = 0;
		for (int cluster = 0; cluster <= max; cluster++)
		{
			if (used[cluster])
			{
				renamed[cluster] = next++;
			}
		}
		int[] result = new int[clusters.length];
		for (int node = 0; node < clusters.length; node++)
		{
			result[node] = renamed[clusters[node]];
		}
		return result;
	}

	private static int[][] membersOf(int[] clusters, int count)
	{
		int[] sizes = new int[count];
		for (int cluster : clusters)
		{
			sizes[cluster]++;
		}
		int[][] members = new int[count][];
		for (int cluster = 0; cluster < count; cluster++)
		{
			members[cluster] = new int[sizes[cluster]];
		}
		int[] fill = new int[count];
		for (int node = 0; node < clusters.length; node++)
		{
			members[clusters[node]][fill[clusters[node]]++] = node;
		}
		return members;
	}

	/** A weighted network with node weights (the strengths in the original graph), without self-links. */
	private static final class Network
	{
		final int n;
		final double[] nodeWeights;
		final int[] offsets;
		final int[] neighbors;
		final double[] weights;

		Network(int n, double[] nodeWeights, int[] offsets, int[] neighbors, double[] weights)
		{
			this.n = n;
			this.nodeWeights = nodeWeights;
			this.offsets = offsets;
			this.neighbors = neighbors;
			this.weights = weights;
		}

		static Network of(NetworkAlgorithms.Undirected graph)
		{
			return new Network(graph.n, Arrays.copyOf(graph.strength, graph.n), graph.offsets, graph.neighbors,
					graph.weights);
		}

		/**
		 * The subnetwork induced by {@code members} (in that order), keeping
		 * node weights. {@code local} is scratch space of -1s, left as found.
		 */
		Network subnetwork(int[] members, int[] local)
		{
			int size = members.length;
			for (int index = 0; index < size; index++)
			{
				local[members[index]] = index;
			}
			int[] subOffsets = new int[size + 1];
			int edges = 0;
			for (int index = 0; index < size; index++)
			{
				int node = members[index];
				for (int edge = offsets[node]; edge < offsets[node + 1]; edge++)
				{
					edges += local[neighbors[edge]] >= 0 ? 1 : 0;
				}
				subOffsets[index + 1] = edges;
			}
			int[] subNeighbors = new int[edges];
			double[] subWeights = new double[edges];
			double[] subNodeWeights = new double[size];
			int cursor = 0;
			for (int index = 0; index < size; index++)
			{
				int node = members[index];
				subNodeWeights[index] = nodeWeights[node];
				for (int edge = offsets[node]; edge < offsets[node + 1]; edge++)
				{
					int other = local[neighbors[edge]];
					if (other >= 0)
					{
						subNeighbors[cursor] = other;
						subWeights[cursor++] = weights[edge];
					}
				}
			}
			for (int member : members)
			{
				local[member] = -1;
			}
			return new Network(size, subNodeWeights, subOffsets, subNeighbors, subWeights);
		}

		/** The aggregate network of a partition: one node per cluster, weights summed, internal edges dropped. */
		Network aggregate(int[] clusters, int count)
		{
			double[] aggregateWeights = new double[count];
			int[][] members = membersOf(clusters, count);
			for (int node = 0; node < n; node++)
			{
				aggregateWeights[clusters[node]] += nodeWeights[node];
			}
			int[] aggregateOffsets = new int[count + 1];
			int[] neighborList = new int[neighbors.length];
			double[] weightList = new double[neighbors.length];
			double[] accumulated = new double[count];
			int[] touched = new int[count];
			int cursor = 0;
			for (int cluster = 0; cluster < count; cluster++)
			{
				int touchedCount = 0;
				for (int node : members[cluster])
				{
					for (int edge = offsets[node]; edge < offsets[node + 1]; edge++)
					{
						int other = clusters[neighbors[edge]];
						if (other == cluster)
						{
							continue;
						}
						if (accumulated[other] == 0.0)
						{
							touched[touchedCount++] = other;
						}
						accumulated[other] += weights[edge];
					}
				}
				Arrays.sort(touched, 0, touchedCount);
				for (int index = 0; index < touchedCount; index++)
				{
					neighborList[cursor] = touched[index];
					weightList[cursor++] = accumulated[touched[index]];
					accumulated[touched[index]] = 0.0;
				}
				aggregateOffsets[cluster + 1] = cursor;
			}
			return new Network(count, aggregateWeights, aggregateOffsets, Arrays.copyOf(neighborList, cursor),
					Arrays.copyOf(weightList, cursor));
		}
	}
}
