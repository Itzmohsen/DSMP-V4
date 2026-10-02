package TwitterGatherDataFollowers.userRyersonU;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.SplittableRandom;

/**
 * Graph algorithms on compressed sparse rows, each the textbook version and
 * each deterministic.
 *
 * <ul>
 * <li>PageRank by power iteration, weighted, dangling mass spread uniformly
 * (Page, Brin, Motwani and Winograd 1999);</li>
 * <li>betweenness by Brandes' algorithm (2001), exact, or estimated from
 * seeded pivots on large graphs (Brandes and Pich 2007), normalised as
 * NetworkX does;</li>
 * <li>connected components, k-core numbers (Batagelj and Zaversnik 2003),
 * local clustering and transitivity (Watts and Strogatz 1998);</li>
 * <li>shortest-path length and diameter of a component by breadth-first
 * search;</li>
 * <li>modularity with a resolution (Newman and Girvan 2004; Reichardt and
 * Bornholdt 2006) and the participation coefficient and within-module degree
 * of Guimer&agrave; and Amaral (2005).</li>
 * </ul>
 */
final class NetworkAlgorithms
{
	private NetworkAlgorithms()
	{
	}

	// ------------------------------------------------------------ graphs

	/** An undirected weighted graph: parallel edges merged (weights summed), self-loops dropped. */
	static final class Undirected
	{
		final int n;
		final int[] offsets;
		final int[] neighbors;
		final double[] weights;
		/** Each node's weighted degree. */
		final double[] strength;
		/** The total edge weight, each edge counted once (m). */
		final double total;

		private Undirected(int n, int[] offsets, int[] neighbors, double[] weights)
		{
			this.n = n;
			this.offsets = offsets;
			this.neighbors = neighbors;
			this.weights = weights;
			this.strength = new double[n];
			double sum = 0.0;
			for (int node = 0; node < n; node++)
			{
				for (int edge = offsets[node]; edge < offsets[node + 1]; edge++)
				{
					strength[node] += weights[edge];
				}
				sum += strength[node];
			}
			this.total = sum / 2.0;
		}

		static Undirected of(int n, int[] sources, int[] targets, double[] edgeWeights)
		{
			int m = sources.length;
			int[] count = new int[n + 1];
			for (int edge = 0; edge < m; edge++)
			{
				if (sources[edge] != targets[edge])
				{
					count[sources[edge] + 1]++;
					count[targets[edge] + 1]++;
				}
			}
			for (int node = 0; node < n; node++)
			{
				count[node + 1] += count[node];
			}
			int[] cursor = Arrays.copyOf(count, n + 1);
			int[] raw = new int[count[n]];
			double[] rawWeights = new double[count[n]];
			for (int edge = 0; edge < m; edge++)
			{
				int a = sources[edge];
				int b = targets[edge];
				if (a == b)
				{
					continue;
				}
				double w = edgeWeights == null ? 1.0 : edgeWeights[edge];
				raw[cursor[a]] = b;
				rawWeights[cursor[a]++] = w;
				raw[cursor[b]] = a;
				rawWeights[cursor[b]++] = w;
			}
			return merge(n, count, raw, rawWeights);
		}

		int degree(int node)
		{
			return offsets[node + 1] - offsets[node];
		}

		int edgeCount()
		{
			return neighbors.length / 2;
		}

		/** The same graph with every edge weighing 1. */
		Undirected unweighted()
		{
			double[] ones = new double[weights.length];
			Arrays.fill(ones, 1.0);
			return new Undirected(n, offsets, neighbors, ones);
		}

		/**
		 * The subgraph on the nodes with {@code keep}, renumbered in order;
		 * {@code mapping} receives old to new numbers (-1 for dropped nodes).
		 */
		Undirected induced(boolean[] keep, int[] mapping)
		{
			int next = 0;
			for (int node = 0; node < n; node++)
			{
				mapping[node] = keep[node] ? next++ : -1;
			}
			int[] offsetsOut = new int[next + 1];
			int edges = 0;
			for (int node = 0; node < n; node++)
			{
				if (mapping[node] < 0)
				{
					continue;
				}
				for (int edge = offsets[node]; edge < offsets[node + 1]; edge++)
				{
					edges += mapping[neighbors[edge]] >= 0 ? 1 : 0;
				}
				offsetsOut[mapping[node] + 1] = edges;
			}
			int[] neighborsOut = new int[edges];
			double[] weightsOut = new double[edges];
			int cursor = 0;
			for (int node = 0; node < n; node++)
			{
				if (mapping[node] < 0)
				{
					continue;
				}
				for (int edge = offsets[node]; edge < offsets[node + 1]; edge++)
				{
					int other = mapping[neighbors[edge]];
					if (other >= 0)
					{
						neighborsOut[cursor] = other;
						weightsOut[cursor++] = weights[edge];
					}
				}
			}
			return new Undirected(next, offsetsOut, neighborsOut, weightsOut);
		}

		/** Sorts each row by neighbour and sums parallel edges. */
		private static Undirected merge(int n, int[] offsets, int[] raw, double[] rawWeights)
		{
			int[] outOffsets = new int[n + 1];
			int[] outNeighbors = new int[raw.length];
			double[] outWeights = new double[raw.length];
			int cursor = 0;
			long[] keys = new long[0];
			for (int node = 0; node < n; node++)
			{
				int from = offsets[node];
				int to = offsets[node + 1];
				int length = to - from;
				if (keys.length < length)
				{
					keys = new long[Math.max(length, keys.length * 2)];
				}
				for (int edge = 0; edge < length; edge++)
				{
					keys[edge] = ((long)raw[from + edge] << 32) | edge;
				}
				Arrays.sort(keys, 0, length);
				int previous = -1;
				for (int edge = 0; edge < length; edge++)
				{
					int neighbor = (int)(keys[edge] >>> 32);
					double weight = rawWeights[from + (int)(keys[edge] & 0xffffffffL)];
					if (neighbor == previous)
					{
						outWeights[cursor - 1] += weight;
					}
					else
					{
						outNeighbors[cursor] = neighbor;
						outWeights[cursor++] = weight;
						previous = neighbor;
					}
				}
				outOffsets[node + 1] = cursor;
			}
			return new Undirected(n, outOffsets, Arrays.copyOf(outNeighbors, cursor), Arrays.copyOf(outWeights, cursor));
		}
	}

	/** A directed weighted graph by out-edges: parallel edges merged, self-loops dropped. */
	static final class Directed
	{
		final int n;
		final int[] offsets;
		final int[] neighbors;
		final double[] weights;
		final double[] outStrength;

		private Directed(int n, int[] offsets, int[] neighbors, double[] weights)
		{
			this.n = n;
			this.offsets = offsets;
			this.neighbors = neighbors;
			this.weights = weights;
			this.outStrength = new double[n];
			for (int node = 0; node < n; node++)
			{
				for (int edge = offsets[node]; edge < offsets[node + 1]; edge++)
				{
					outStrength[node] += weights[edge];
				}
			}
		}

		static Directed of(int n, int[] sources, int[] targets, double[] edgeWeights)
		{
			int[] count = new int[n + 1];
			for (int edge = 0; edge < sources.length; edge++)
			{
				if (sources[edge] != targets[edge])
				{
					count[sources[edge] + 1]++;
				}
			}
			for (int node = 0; node < n; node++)
			{
				count[node + 1] += count[node];
			}
			int[] cursor = Arrays.copyOf(count, n + 1);
			int[] raw = new int[count[n]];
			double[] rawWeights = new double[count[n]];
			for (int edge = 0; edge < sources.length; edge++)
			{
				if (sources[edge] == targets[edge])
				{
					continue;
				}
				raw[cursor[sources[edge]]] = targets[edge];
				rawWeights[cursor[sources[edge]]++] = edgeWeights == null ? 1.0 : edgeWeights[edge];
			}
			Undirected merged = Undirected.merge(n, count, raw, rawWeights);
			return new Directed(n, merged.offsets, merged.neighbors, merged.weights);
		}

		int[] inDegrees()
		{
			int[] in = new int[n];
			for (int target : neighbors)
			{
				in[target]++;
			}
			return in;
		}

		int outDegree(int node)
		{
			return offsets[node + 1] - offsets[node];
		}
	}

	// ------------------------------------------------------------ PageRank

	/**
	 * PageRank with damping {@code alpha}: a walker follows an out-edge with
	 * probability proportional to its weight, and with probability
	 * 1 - alpha (always, from a node without out-edges) jumps to a uniformly
	 * random node. Iterates until the L1 change is below {@code tolerance}.
	 */
	static double[] pageRank(Directed graph, double alpha, double tolerance, int maxIterations)
	{
		int n = graph.n;
		double[] rank = new double[n];
		if (n == 0)
		{
			return rank;
		}
		Arrays.fill(rank, 1.0 / n);
		double[] next = new double[n];
		for (int iteration = 0; iteration < maxIterations; iteration++)
		{
			double dangling = 0.0;
			for (int node = 0; node < n; node++)
			{
				if (graph.outStrength[node] <= 0.0)
				{
					dangling += rank[node];
				}
			}
			double base = (alpha * dangling + (1.0 - alpha)) / n;
			Arrays.fill(next, base);
			for (int node = 0; node < n; node++)
			{
				double out = graph.outStrength[node];
				if (out <= 0.0)
				{
					continue;
				}
				double share = alpha * rank[node] / out;
				for (int edge = graph.offsets[node]; edge < graph.offsets[node + 1]; edge++)
				{
					next[graph.neighbors[edge]] += share * graph.weights[edge];
				}
			}
			double change = 0.0;
			for (int node = 0; node < n; node++)
			{
				change += Math.abs(next[node] - rank[node]);
			}
			double[] swap = rank;
			rank = next;
			next = swap;
			if (change < tolerance)
			{
				break;
			}
		}
		return rank;
	}

	// ------------------------------------------------------------ betweenness

	/** Betweenness and whether it is exact (all sources) or estimated from pivots. */
	static final class Betweenness
	{
		final double[] values;
		final boolean exact;
		final int sources;

		Betweenness(double[] values, boolean exact, int sources)
		{
			this.values = values;
			this.exact = exact;
			this.sources = sources;
		}
	}

	/**
	 * Shortest-path betweenness of an unweighted undirected graph, normalised
	 * by (n - 1)(n - 2) as NetworkX's {@code betweenness_centrality} is. Exact
	 * when the graph has at most {@code exactLimit} nodes; otherwise estimated
	 * from {@code pivots} sources drawn with the seed and scaled by n / pivots.
	 */
	static Betweenness betweenness(Undirected graph, int exactLimit, int pivots, long seed)
	{
		int n = graph.n;
		double[] centrality = new double[n];
		if (n < 3)
		{
			return new Betweenness(centrality, true, n);
		}
		int[] sources;
		boolean exact = n <= exactLimit || pivots >= n;
		if (exact)
		{
			sources = new int[n];
			for (int node = 0; node < n; node++)
			{
				sources[node] = node;
			}
		}
		else
		{
			int[] order = new int[n];
			for (int node = 0; node < n; node++)
			{
				order[node] = node;
			}
			SplittableRandom random = new SplittableRandom(seed);
			for (int index = 0; index < pivots; index++)
			{
				int swap = index + random.nextInt(n - index);
				int value = order[index];
				order[index] = order[swap];
				order[swap] = value;
			}
			sources = Arrays.copyOf(order, pivots);
			Arrays.sort(sources);
		}
		int[] stack = new int[n];
		int[] distance = new int[n];
		double[] paths = new double[n];
		double[] dependency = new double[n];
		int[] queue = new int[n];
		for (int source : sources)
		{
			Arrays.fill(distance, -1);
			Arrays.fill(paths, 0.0);
			Arrays.fill(dependency, 0.0);
			int top = 0;
			int head = 0;
			int tail = 0;
			distance[source] = 0;
			paths[source] = 1.0;
			queue[tail++] = source;
			while (head < tail)
			{
				int node = queue[head++];
				stack[top++] = node;
				for (int edge = graph.offsets[node]; edge < graph.offsets[node + 1]; edge++)
				{
					int neighbor = graph.neighbors[edge];
					if (distance[neighbor] < 0)
					{
						distance[neighbor] = distance[node] + 1;
						queue[tail++] = neighbor;
					}
					if (distance[neighbor] == distance[node] + 1)
					{
						paths[neighbor] += paths[node];
					}
				}
			}
			while (top > 0)
			{
				int node = stack[--top];
				for (int edge = graph.offsets[node]; edge < graph.offsets[node + 1]; edge++)
				{
					int predecessor = graph.neighbors[edge];
					if (distance[predecessor] == distance[node] - 1)
					{
						dependency[predecessor] += paths[predecessor] / paths[node] * (1.0 + dependency[node]);
					}
				}
				if (node != source)
				{
					centrality[node] += dependency[node];
				}
			}
		}
		double scale = 1.0 / ((n - 1.0) * (n - 2.0));
		if (!exact)
		{
			scale *= n / (double)sources.length;
		}
		for (int node = 0; node < n; node++)
		{
			centrality[node] *= scale;
		}
		return new Betweenness(centrality, exact, sources.length);
	}

	// ------------------------------------------------------------ structure

	/** Connected component of each node, numbered by size (0 the largest; ties by smallest node). */
	static int[] components(Undirected graph)
	{
		int n = graph.n;
		int[] raw = new int[n];
		Arrays.fill(raw, -1);
		int count = 0;
		ArrayDeque<Integer> queue = new ArrayDeque<Integer>();
		int[] sizes = new int[n];
		for (int start = 0; start < n; start++)
		{
			if (raw[start] >= 0)
			{
				continue;
			}
			raw[start] = count;
			queue.add(Integer.valueOf(start));
			while (!queue.isEmpty())
			{
				int node = queue.poll().intValue();
				sizes[count]++;
				for (int edge = graph.offsets[node]; edge < graph.offsets[node + 1]; edge++)
				{
					int neighbor = graph.neighbors[edge];
					if (raw[neighbor] < 0)
					{
						raw[neighbor] = count;
						queue.add(Integer.valueOf(neighbor));
					}
				}
			}
			count++;
		}
		return renumberBySize(raw, count);
	}

	/** Renumbers labels 0..count-1 so the largest group is 0 (ties: the group of the smallest node first). */
	static int[] renumberBySize(int[] labels, int count)
	{
		final int[] sizes = new int[count];
		final int[] first = new int[count];
		Arrays.fill(first, Integer.MAX_VALUE);
		for (int node = 0; node < labels.length; node++)
		{
			sizes[labels[node]]++;
			first[labels[node]] = Math.min(first[labels[node]], node);
		}
		Integer[] order = new Integer[count];
		for (int label = 0; label < count; label++)
		{
			order[label] = Integer.valueOf(label);
		}
		Arrays.sort(order, new java.util.Comparator<Integer>()
		{
			public int compare(Integer left, Integer right)
			{
				int bySize = Integer.compare(sizes[right.intValue()], sizes[left.intValue()]);
				return bySize != 0 ? bySize : Integer.compare(first[left.intValue()], first[right.intValue()]);
			}
		});
		int[] renamed = new int[count];
		for (int rank = 0; rank < count; rank++)
		{
			renamed[order[rank].intValue()] = rank;
		}
		int[] result = new int[labels.length];
		for (int node = 0; node < labels.length; node++)
		{
			result[node] = renamed[labels[node]];
		}
		return result;
	}

	static int groupCount(int[] labels)
	{
		int max = -1;
		for (int label : labels)
		{
			max = Math.max(max, label);
		}
		return max + 1;
	}

	static int[] groupSizes(int[] labels)
	{
		int[] sizes = new int[groupCount(labels)];
		for (int label : labels)
		{
			if (label >= 0)
			{
				sizes[label]++;
			}
		}
		return sizes;
	}

	/** Each node's core number: the largest k such that it belongs to the k-core (unweighted degrees). */
	static int[] coreNumbers(Undirected graph)
	{
		int n = graph.n;
		int[] degree = new int[n];
		int maxDegree = 0;
		for (int node = 0; node < n; node++)
		{
			degree[node] = graph.degree(node);
			maxDegree = Math.max(maxDegree, degree[node]);
		}
		int[] bin = new int[maxDegree + 1];
		for (int node = 0; node < n; node++)
		{
			bin[degree[node]]++;
		}
		int start = 0;
		for (int d = 0; d <= maxDegree; d++)
		{
			int count = bin[d];
			bin[d] = start;
			start += count;
		}
		int[] position = new int[n];
		int[] vertices = new int[n];
		for (int node = 0; node < n; node++)
		{
			position[node] = bin[degree[node]];
			vertices[position[node]] = node;
			bin[degree[node]]++;
		}
		for (int d = maxDegree; d >= 1; d--)
		{
			bin[d] = bin[d - 1];
		}
		if (maxDegree >= 0)
		{
			bin[0] = 0;
		}
		for (int index = 0; index < n; index++)
		{
			int node = vertices[index];
			for (int edge = graph.offsets[node]; edge < graph.offsets[node + 1]; edge++)
			{
				int neighbor = graph.neighbors[edge];
				if (degree[neighbor] > degree[node])
				{
					int neighborDegree = degree[neighbor];
					int neighborPosition = position[neighbor];
					int firstPosition = bin[neighborDegree];
					int firstNode = vertices[firstPosition];
					if (neighbor != firstNode)
					{
						position[neighbor] = firstPosition;
						vertices[neighborPosition] = firstNode;
						position[firstNode] = neighborPosition;
						vertices[firstPosition] = neighbor;
					}
					bin[neighborDegree]++;
					degree[neighbor]--;
				}
			}
		}
		return degree;
	}

	/** Each node's local clustering coefficient (unweighted; 0 below degree 2). */
	static double[] clustering(Undirected graph)
	{
		int n = graph.n;
		double[] coefficient = new double[n];
		boolean[] mark = new boolean[n];
		for (int node = 0; node < n; node++)
		{
			int degree = graph.degree(node);
			if (degree < 2)
			{
				continue;
			}
			for (int edge = graph.offsets[node]; edge < graph.offsets[node + 1]; edge++)
			{
				mark[graph.neighbors[edge]] = true;
			}
			long links = 0;
			for (int edge = graph.offsets[node]; edge < graph.offsets[node + 1]; edge++)
			{
				int neighbor = graph.neighbors[edge];
				for (int second = graph.offsets[neighbor]; second < graph.offsets[neighbor + 1]; second++)
				{
					if (mark[graph.neighbors[second]])
					{
						links++;
					}
				}
			}
			for (int edge = graph.offsets[node]; edge < graph.offsets[node + 1]; edge++)
			{
				mark[graph.neighbors[edge]] = false;
			}
			coefficient[node] = links / ((double)degree * (degree - 1));
		}
		return coefficient;
	}

	/** Transitivity: three times the triangles over the connected triples. */
	static double transitivity(Undirected graph)
	{
		double[] local = clustering(graph);
		double closed = 0.0;
		double triples = 0.0;
		for (int node = 0; node < graph.n; node++)
		{
			double degree = graph.degree(node);
			double pairs = degree * (degree - 1.0);
			triples += pairs;
			closed += local[node] * pairs;
		}
		return triples == 0.0 ? 0.0 : closed / triples;
	}

	/** Mean shortest-path length and diameter of one component (hop counts). */
	static final class Distances
	{
		final double meanLength;
		final int diameter;
		final boolean exact;

		Distances(double meanLength, int diameter, boolean exact)
		{
			this.meanLength = meanLength;
			this.diameter = diameter;
			this.exact = exact;
		}
	}

	/**
	 * Breadth-first distances within the component {@code component}: exact
	 * from every member when it has at most {@code exactLimit} nodes,
	 * otherwise from {@code pivots} seeded members (the mean is then an
	 * estimate and the diameter a lower bound).
	 */
	static Distances distances(Undirected graph, int[] components, int component, int exactLimit, int pivots,
			long seed)
	{
		int n = graph.n;
		int members = 0;
		for (int node = 0; node < n; node++)
		{
			members += components[node] == component ? 1 : 0;
		}
		if (members < 2)
		{
			return new Distances(0.0, 0, true);
		}
		int[] list = new int[members];
		int cursor = 0;
		for (int node = 0; node < n; node++)
		{
			if (components[node] == component)
			{
				list[cursor++] = node;
			}
		}
		boolean exact = members <= exactLimit || pivots >= members;
		int[] sources = list;
		if (!exact)
		{
			int[] order = Arrays.copyOf(list, members);
			SplittableRandom random = new SplittableRandom(seed);
			for (int index = 0; index < pivots; index++)
			{
				int swap = index + random.nextInt(members - index);
				int value = order[index];
				order[index] = order[swap];
				order[swap] = value;
			}
			sources = Arrays.copyOf(order, pivots);
		}
		int[] distance = new int[n];
		int[] queue = new int[n];
		double sum = 0.0;
		double pairs = 0.0;
		int diameter = 0;
		for (int source : sources)
		{
			Arrays.fill(distance, -1);
			int head = 0;
			int tail = 0;
			distance[source] = 0;
			queue[tail++] = source;
			while (head < tail)
			{
				int node = queue[head++];
				for (int edge = graph.offsets[node]; edge < graph.offsets[node + 1]; edge++)
				{
					int neighbor = graph.neighbors[edge];
					if (distance[neighbor] < 0)
					{
						distance[neighbor] = distance[node] + 1;
						queue[tail++] = neighbor;
						sum += distance[neighbor];
						pairs++;
						diameter = Math.max(diameter, distance[neighbor]);
					}
				}
			}
		}
		return new Distances(pairs == 0.0 ? 0.0 : sum / pairs, diameter, exact);
	}

	// ------------------------------------------------------------ communities

	/**
	 * Modularity of a partition at resolution {@code gamma}:
	 * Q = sum over communities c of L_c / m - gamma (K_c / 2m)^2, with L_c the
	 * weight inside c and K_c its total strength. Nodes labelled -1 are their
	 * own singletons.
	 */
	static double modularity(Undirected graph, int[] partition, double gamma)
	{
		if (graph.total <= 0.0)
		{
			return 0.0;
		}
		int[] labels = singletonsFor(partition);
		int groups = groupCount(labels);
		double[] inside = new double[groups];
		double[] strength = new double[groups];
		for (int node = 0; node < graph.n; node++)
		{
			strength[labels[node]] += graph.strength[node];
			for (int edge = graph.offsets[node]; edge < graph.offsets[node + 1]; edge++)
			{
				if (labels[graph.neighbors[edge]] == labels[node])
				{
					inside[labels[node]] += graph.weights[edge];
				}
			}
		}
		double m = graph.total;
		double q = 0.0;
		for (int group = 0; group < groups; group++)
		{
			q += inside[group] / 2.0 / m - gamma * (strength[group] / (2.0 * m)) * (strength[group] / (2.0 * m));
		}
		return q;
	}

	/** A copy where every -1 label becomes a fresh singleton label. */
	static int[] singletonsFor(int[] partition)
	{
		int next = groupCount(partition);
		int[] labels = Arrays.copyOf(partition, partition.length);
		for (int node = 0; node < labels.length; node++)
		{
			if (labels[node] < 0)
			{
				labels[node] = next++;
			}
		}
		return labels;
	}

	/**
	 * The participation coefficient of each node: 1 - sum over communities s
	 * of (k_is / k_i)^2, with k_is its links into s (0 for an isolated node).
	 */
	static double[] participation(Undirected graph, int[] partition)
	{
		int[] labels = singletonsFor(partition);
		double[] share = new double[groupCount(labels)];
		double[] result = new double[graph.n];
		for (int node = 0; node < graph.n; node++)
		{
			int degree = graph.degree(node);
			if (degree == 0)
			{
				continue;
			}
			for (int edge = graph.offsets[node]; edge < graph.offsets[node + 1]; edge++)
			{
				share[labels[graph.neighbors[edge]]] += 1.0;
			}
			double sum = 0.0;
			for (int edge = graph.offsets[node]; edge < graph.offsets[node + 1]; edge++)
			{
				int label = labels[graph.neighbors[edge]];
				if (share[label] > 0.0)
				{
					double fraction = share[label] / degree;
					sum += fraction * fraction;
					share[label] = 0.0;
				}
			}
			result[node] = 1.0 - sum;
		}
		return result;
	}

	/**
	 * The within-module degree z-score of each node: its links inside its
	 * community, standardised over the community's members (0 when they all
	 * have the same).
	 */
	static double[] withinModuleDegree(Undirected graph, int[] partition)
	{
		int[] labels = singletonsFor(partition);
		int groups = groupCount(labels);
		double[] inside = new double[graph.n];
		double[] sum = new double[groups];
		double[] squares = new double[groups];
		int[] members = new int[groups];
		for (int node = 0; node < graph.n; node++)
		{
			for (int edge = graph.offsets[node]; edge < graph.offsets[node + 1]; edge++)
			{
				if (labels[graph.neighbors[edge]] == labels[node])
				{
					inside[node] += 1.0;
				}
			}
			sum[labels[node]] += inside[node];
			squares[labels[node]] += inside[node] * inside[node];
			members[labels[node]]++;
		}
		double[] z = new double[graph.n];
		for (int node = 0; node < graph.n; node++)
		{
			int label = labels[node];
			double mean = sum[label] / members[label];
			double variance = squares[label] / members[label] - mean * mean;
			double deviation = variance > 1e-12 ? Math.sqrt(variance) : 0.0;
			z[node] = deviation == 0.0 ? 0.0 : (inside[node] - mean) / deviation;
		}
		return z;
	}

	/** The role of Guimer&agrave; and Amaral (2005) for a participation coefficient and within-module degree. */
	static String role(double participation, double z)
	{
		if (z >= 2.5)
		{
			return participation <= 0.30 ? "R5 provincial hub"
					: participation <= 0.75 ? "R6 connector hub" : "R7 kinless hub";
		}
		return participation <= 0.05 ? "R1 ultra-peripheral"
				: participation <= 0.62 ? "R2 peripheral"
						: participation <= 0.80 ? "R3 connector" : "R4 kinless";
	}
}
