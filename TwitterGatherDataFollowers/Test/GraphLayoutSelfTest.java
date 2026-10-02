package TwitterGatherDataFollowers.userRyersonU;

import java.util.Arrays;

/**
 * ForceAtlas2: the same graph always gets the same picture, communities land
 * apart (LinLog), positions stay finite with and without Barnes-Hut, and the
 * final pass removes overlaps.
 */
public final class GraphLayoutSelfTest
{
	private GraphLayoutSelfTest()
	{
	}

	public static void main(String[] args)
	{
		int[][] edges = twoCommunities(18);
		double[][] first = layout(36, edges, null);
		double[][] second = layout(36, edges, null);
		require(Arrays.equals(first[0], second[0]) && Arrays.equals(first[1], second[1]), "the layout is deterministic");
		requireFinite(first);
		separatesCommunities(first, 18);

		//Barnes-Hut on a larger graph: finite, and the communities still separate.
		int[][] large = twoCommunities(260);
		GraphLayout.Settings settings = GraphLayout.Settings.forSize(520);
		require(settings.barnesHut, "Barnes-Hut above 400 nodes");
		double[][] approximate = layout(520, large, settings);
		requireFinite(approximate);
		separatesCommunities(approximate, 260);

		//Overlap removal: after it, no two nodes of radius r are closer than 2r.
		int n = 36;
		GraphLayout layout = start(n, edges, null);
		layout.run(GraphLayout.budget(n, edges.length));
		double radius = layout.extent() / 30.0;
		double[] radii = new double[n];
		Arrays.fill(radii, radius);
		layout.preventOverlap(radii, 600);
		double[][] spread = layout.positions();
		int overlaps = 0;
		for (int a = 0; a < n; a++)
		{
			for (int b = 0; b < a; b++)
			{
				double dx = spread[0][a] - spread[0][b];
				double dy = spread[1][a] - spread[1][b];
				overlaps += Math.sqrt(dx * dx + dy * dy) < 2 * radius * 0.98 ? 1 : 0;
			}
		}
		require(overlaps == 0, overlaps + " overlapping pairs remain");
		require(GraphLayout.budget(100, 200) >= 150 && GraphLayout.budget(1000000, 3000000) == 150,
				"the iteration budget shrinks with the graph");
		System.out.println("GraphLayoutSelfTest passed");
	}

	/** Two dense communities of {@code size} nodes joined by a single edge. */
	static int[][] twoCommunities(int size)
	{
		java.util.List<int[]> edges = new java.util.ArrayList<int[]>();
		java.util.Random random = new java.util.Random(11L);
		for (int block = 0; block < 2; block++)
		{
			for (int a = 0; a < size; a++)
			{
				for (int b = 0; b < a; b++)
				{
					if (random.nextDouble() < Math.min(1.0, 8.0 / size))
					{
						edges.add(new int[] {block * size + a, block * size + b});
					}
				}
				if (a > 0)
				{
					edges.add(new int[] {block * size + a, block * size + a - 1});
				}
			}
		}
		edges.add(new int[] {0, size});
		return edges.toArray(new int[edges.size()][]);
	}

	private static GraphLayout start(int n, int[][] edges, GraphLayout.Settings settings)
	{
		int[] sources = new int[edges.length];
		int[] targets = new int[edges.length];
		for (int edge = 0; edge < edges.length; edge++)
		{
			sources[edge] = edges[edge][0];
			targets[edge] = edges[edge][1];
		}
		long[] seeds = new long[n];
		for (int node = 0; node < n; node++)
		{
			seeds[node] = SimulationSeeds.forName("node-" + node);
		}
		return new GraphLayout(n, sources, targets, null, seeds, settings);
	}

	private static double[][] layout(int n, int[][] edges, GraphLayout.Settings settings)
	{
		GraphLayout layout = start(n, edges, settings);
		layout.run(GraphLayout.budget(n, edges.length));
		return layout.positions();
	}

	private static void requireFinite(double[][] positions)
	{
		for (double[] axis : positions)
		{
			for (double value : axis)
			{
				require(!Double.isNaN(value) && !Double.isInfinite(value), "a position is not finite");
			}
		}
	}

	/** The two communities' centroids are far apart compared with their members' spread. */
	private static void separatesCommunities(double[][] p, int size)
	{
		double[][] centroid = new double[2][2];
		for (int node = 0; node < 2 * size; node++)
		{
			centroid[node / size][0] += p[0][node] / size;
			centroid[node / size][1] += p[1][node] / size;
		}
		double spread = 0.0;
		for (int node = 0; node < 2 * size; node++)
		{
			double dx = p[0][node] - centroid[node / size][0];
			double dy = p[1][node] - centroid[node / size][1];
			spread += Math.sqrt(dx * dx + dy * dy) / (2 * size);
		}
		double gap = Math.hypot(centroid[0][0] - centroid[1][0], centroid[0][1] - centroid[1][1]);
		require(gap > 2.0 * spread, "communities are not separated: gap " + gap + ", spread " + spread);
	}

	static void require(boolean condition, String message)
	{
		if (!condition)
		{
			throw new AssertionError(message);
		}
	}
}
