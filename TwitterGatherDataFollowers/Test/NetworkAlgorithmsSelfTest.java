package TwitterGatherDataFollowers.userRyersonU;

/**
 * The graph algorithms against published or independently computed values:
 * Zachary's karate club with the values NetworkX 3.5 gives (PageRank,
 * betweenness, clustering, k-cores, path lengths, modularity, roles), a
 * weighted directed PageRank, and Leiden reaching the karate club's best
 * known modularity, 0.4198, with connected communities.
 */
public final class NetworkAlgorithmsSelfTest
{
	/** Zachary's karate club (1977), 34 members, 78 ties. */
	static final int[][] KARATE = {{0, 1}, {0, 2}, {0, 3}, {0, 4}, {0, 5}, {0, 6}, {0, 7}, {0, 8}, {0, 10}, {0, 11},
		{0, 12}, {0, 13}, {0, 17}, {0, 19}, {0, 21}, {0, 31}, {1, 2}, {1, 3}, {1, 7}, {1, 13}, {1, 17}, {1, 19},
		{1, 21}, {1, 30}, {2, 3}, {2, 7}, {2, 8}, {2, 9}, {2, 13}, {2, 27}, {2, 28}, {2, 32}, {3, 7}, {3, 12},
		{3, 13}, {4, 6}, {4, 10}, {5, 6}, {5, 10}, {5, 16}, {6, 16}, {8, 30}, {8, 32}, {8, 33}, {9, 33}, {13, 33},
		{14, 32}, {14, 33}, {15, 32}, {15, 33}, {18, 32}, {18, 33}, {19, 33}, {20, 32}, {20, 33}, {22, 32}, {22, 33},
		{23, 25}, {23, 27}, {23, 29}, {23, 32}, {23, 33}, {24, 25}, {24, 27}, {24, 31}, {25, 31}, {26, 29}, {26, 33},
		{27, 33}, {28, 31}, {28, 33}, {29, 32}, {29, 33}, {30, 32}, {30, 33}, {31, 32}, {31, 33}, {32, 33}};

	/** The club each member joined after the split (0 Mr. Hi, 1 the officer). */
	static final int[] CLUB = {0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 1, 0, 0, 1, 0, 1, 0, 1, 1, 1, 1, 1, 1,
		1, 1, 1, 1, 1, 1};

	private NetworkAlgorithmsSelfTest()
	{
	}

	public static void main(String[] args)
	{
		NetworkAlgorithms.Undirected karate = karate();
		pageRankMatchesNetworkX(karate);
		betweennessMatchesNetworkX(karate);
		clusteringCoresAndPathsMatchNetworkX(karate);
		modularityAndRolesMatchNetworkX(karate);
		leidenReachesTheBestKnownPartition(karate);
		System.out.println("NetworkAlgorithmsSelfTest passed");
	}

	static NetworkAlgorithms.Undirected karate()
	{
		int[] sources = new int[KARATE.length];
		int[] targets = new int[KARATE.length];
		for (int edge = 0; edge < KARATE.length; edge++)
		{
			sources[edge] = KARATE[edge][0];
			targets[edge] = KARATE[edge][1];
		}
		return NetworkAlgorithms.Undirected.of(34, sources, targets, null);
	}

	private static void pageRankMatchesNetworkX(NetworkAlgorithms.Undirected karate)
	{
		int[] sources = new int[KARATE.length * 2];
		int[] targets = new int[KARATE.length * 2];
		for (int edge = 0; edge < KARATE.length; edge++)
		{
			sources[2 * edge] = KARATE[edge][0];
			targets[2 * edge] = KARATE[edge][1];
			sources[2 * edge + 1] = KARATE[edge][1];
			targets[2 * edge + 1] = KARATE[edge][0];
		}
		double[] rank = NetworkAlgorithms.pageRank(NetworkAlgorithms.Directed.of(34, sources, targets, null), 0.85,
				1e-14, 100000);
		close(rank[0], 0.096997285388304, 1e-10, "PageRank of member 0");
		close(rank[33], 0.100919182332617, 1e-10, "PageRank of member 33");
		close(rank[2], 0.057078509488462, 1e-10, "PageRank of member 2");
		close(rank[11], 0.009564745492136, 1e-10, "PageRank of member 11");
		//Weighted and directed, with a dangling node (4) and one without in-links (3).
		double[] weighted = NetworkAlgorithms.pageRank(NetworkAlgorithms.Directed.of(5, new int[] {0, 0, 1, 2, 3, 3},
				new int[] {1, 2, 2, 0, 2, 0}, new double[] {1.0, 2.0, 1.0, 1.0, 0.5, 1.5}), 0.85, 1e-14, 100000);
		double[] expected = {0.390761931744515, 0.146860458974199, 0.390088452654780, 0.036144578313253,
			0.036144578313253};
		double total = 0.0;
		for (int node = 0; node < 5; node++)
		{
			close(weighted[node], expected[node], 1e-10, "weighted PageRank of node " + node);
			total += weighted[node];
		}
		close(total, 1.0, 1e-12, "PageRank sums to 1");
	}

	private static void betweennessMatchesNetworkX(NetworkAlgorithms.Undirected karate)
	{
		NetworkAlgorithms.Betweenness exact = NetworkAlgorithms.betweenness(karate, 1000, 0, 1L);
		require(exact.exact && exact.sources == 34, "small graphs are exact");
		close(exact.values[0], 0.437635281385281, 1e-12, "betweenness of member 0");
		close(exact.values[33], 0.304074975949976, 1e-12, "betweenness of member 33");
		close(exact.values[2], 0.143656806156806, 1e-12, "betweenness of member 2");
		close(exact.values[11], 0.0, 1e-15, "betweenness of a leaf");
		//Pivot sampling with every node as a pivot is the exact value; with fewer it is an estimate.
		NetworkAlgorithms.Betweenness sampled = NetworkAlgorithms.betweenness(karate, 0, 17, 5L);
		require(!sampled.exact && sampled.sources == 17, "17 pivots");
		require(sampled.values[0] > 0.2, "member 0 stays a broker in the estimate: " + sampled.values[0]);
		NetworkAlgorithms.Betweenness again = NetworkAlgorithms.betweenness(karate, 0, 17, 5L);
		require(java.util.Arrays.equals(sampled.values, again.values), "the estimate is seeded");
		//A path a-b-c-d: b and c each sit on two of the three pairs through them, normalised by (n-1)(n-2).
		NetworkAlgorithms.Betweenness path = NetworkAlgorithms.betweenness(NetworkAlgorithms.Undirected.of(4,
				new int[] {0, 1, 2}, new int[] {1, 2, 3}, null), 1000, 0, 1L);
		close(path.values[1], 4.0 / 6.0, 1e-15, "betweenness on a path");
	}

	private static void clusteringCoresAndPathsMatchNetworkX(NetworkAlgorithms.Undirected karate)
	{
		double[] clustering = NetworkAlgorithms.clustering(karate);
		close(clustering[0], 0.15, 1e-15, "clustering of member 0");
		close(clustering[33], 0.110294117647059, 1e-12, "clustering of member 33");
		close(clustering[2], 0.244444444444444, 1e-12, "clustering of member 2");
		double average = 0.0;
		for (double value : clustering)
		{
			average += value;
		}
		close(average / 34.0, 0.570638478207682, 1e-12, "average clustering");
		close(NetworkAlgorithms.transitivity(karate), 0.255681818181818, 1e-12, "transitivity");
		int[] expectedCores = {4, 4, 4, 4, 3, 3, 3, 4, 4, 2, 3, 1, 2, 4, 2, 2, 2, 2, 2, 3, 2, 2, 2, 3, 3, 3, 2, 3, 3,
			3, 4, 3, 4, 4};
		require(java.util.Arrays.equals(NetworkAlgorithms.coreNumbers(karate), expectedCores), "k-core numbers");
		int[] components = NetworkAlgorithms.components(karate);
		require(NetworkAlgorithms.groupCount(components) == 1, "the club is connected");
		NetworkAlgorithms.Distances distances = NetworkAlgorithms.distances(karate, components, 0, 1000, 0, 1L);
		close(distances.meanLength, 2.408199643493761, 1e-12, "mean shortest path");
		require(distances.diameter == 5 && distances.exact, "diameter 5");
		//Components are numbered by size: two triangles and an isolated node.
		int[] parts = NetworkAlgorithms.components(NetworkAlgorithms.Undirected.of(7,
				new int[] {0, 1, 2, 4, 5, 6, 6}, new int[] {1, 2, 0, 5, 6, 4, 6}, null));
		require(parts[0] == 0 && parts[4] == 1 && parts[3] == 2, "components by size, ties by first node");
	}

	private static void modularityAndRolesMatchNetworkX(NetworkAlgorithms.Undirected karate)
	{
		close(NetworkAlgorithms.modularity(karate, CLUB, 1.0), 0.358234714003945, 1e-12, "modularity of the split");
		close(NetworkAlgorithms.modularity(karate, CLUB, 0.5), 0.608604536489152, 1e-12, "modularity at gamma 0.5");
		double[] participation = NetworkAlgorithms.participation(karate, CLUB);
		close(participation[0], 0.1171875, 1e-15, "participation of member 0");
		close(participation[33], 0.290657439446367, 1e-12, "participation of member 33");
		close(participation[2], 0.48, 1e-15, "participation of member 2");
		double[] z = NetworkAlgorithms.withinModuleDegree(karate, CLUB);
		close(z[0], 3.346532549477450, 1e-12, "within-club degree of member 0");
		close(z[33], 3.136280808528076, 1e-12, "within-club degree of member 33");
		close(z[8], -0.651217144763179, 1e-12, "within-club degree of member 8");
		require(NetworkAlgorithms.role(participation[0], z[0]).equals("R5 provincial hub"), "member 0 is a hub");
		require(NetworkAlgorithms.role(0.7, 0.1).equals("R3 connector"), "connector");
	}

	private static void leidenReachesTheBestKnownPartition(NetworkAlgorithms.Undirected karate)
	{
		LeidenClustering.Result result = LeidenClustering.cluster(karate, 1.0, 42L, 5);
		//0.4198 is the maximum modularity of the karate club (Brandes et al. 2008).
		require(result.quality > 0.4197, "Leiden reaches the best known modularity: " + result.quality);
		close(result.quality, NetworkAlgorithms.modularity(karate, result.membership, 1.0), 1e-15,
				"the reported quality is the partition's");
		require(result.communities == 4, "four communities: " + result.communities);
		requireConnected(karate, result.membership);
		LeidenClustering.Result again = LeidenClustering.cluster(karate, 1.0, 42L, 5);
		require(java.util.Arrays.equals(result.membership, again.membership), "Leiden is deterministic");
		int[] sizes = NetworkAlgorithms.groupSizes(result.membership);
		for (int community = 1; community < sizes.length; community++)
		{
			require(sizes[community - 1] >= sizes[community], "communities are numbered by size");
		}
		//A higher resolution splits the club further; a much lower one merges it.
		require(LeidenClustering.cluster(karate, 2.0, 42L, 3).communities > 4, "resolution 2 splits further");
		require(LeidenClustering.cluster(karate, 0.2, 42L, 3).communities < 4, "resolution 0.2 merges");
		//Two cliques joined by one edge: exactly the cliques.
		int[][] cliques = {{0, 1}, {0, 2}, {0, 3}, {1, 2}, {1, 3}, {2, 3}, {4, 5}, {4, 6}, {4, 7}, {5, 6}, {5, 7},
			{6, 7}, {3, 4}};
		int[] sources = new int[cliques.length];
		int[] targets = new int[cliques.length];
		for (int edge = 0; edge < cliques.length; edge++)
		{
			sources[edge] = cliques[edge][0];
			targets[edge] = cliques[edge][1];
		}
		LeidenClustering.Result two = LeidenClustering.cluster(NetworkAlgorithms.Undirected.of(8, sources, targets, null),
				1.0, 1L, 1);
		require(two.communities == 2 && two.membership[0] == two.membership[3] && two.membership[4] == two.membership[7]
				&& two.membership[0] != two.membership[4], "two cliques");
		//No edges: singletons, quality undefined.
		LeidenClustering.Result none = LeidenClustering.cluster(NetworkAlgorithms.Undirected.of(3, new int[0],
				new int[0], null), 1.0, 1L, 1);
		require(none.communities == 3 && Double.isNaN(none.quality), "a graph without edges");
	}

	/** Every community induces a connected subgraph (Leiden's guarantee). */
	static void requireConnected(NetworkAlgorithms.Undirected graph, int[] membership)
	{
		for (int community = 0; community < NetworkAlgorithms.groupCount(membership); community++)
		{
			boolean[] keep = new boolean[graph.n];
			for (int node = 0; node < graph.n; node++)
			{
				keep[node] = membership[node] == community;
			}
			int[] mapping = new int[graph.n];
			NetworkAlgorithms.Undirected sub = graph.induced(keep, mapping);
			require(NetworkAlgorithms.groupCount(NetworkAlgorithms.components(sub)) <= 1,
					"community " + community + " is disconnected");
		}
	}

	static void close(double actual, double expected, double tolerance, String label)
	{
		if (!(Math.abs(actual - expected) <= tolerance))
		{
			throw new AssertionError(label + ": expected " + expected + " but was " + actual);
		}
	}

	static void require(boolean condition, String message)
	{
		if (!condition)
		{
			throw new AssertionError(message);
		}
	}
}
