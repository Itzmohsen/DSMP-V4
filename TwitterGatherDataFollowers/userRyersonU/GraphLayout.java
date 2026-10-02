package TwitterGatherDataFollowers.userRyersonU;

import java.util.Arrays;
import java.util.SplittableRandom;

/**
 * ForceAtlas2 (Jacomy, Venturini, Heymann and Bastian 2014, "ForceAtlas2, a
 * continuous graph layout algorithm for handy network visualization designed
 * for the Gephi software", PLoS ONE 9(6): e98679), deterministic.
 *
 * Nodes repel in proportion to the product of their masses (degree + 1) over
 * their distance, edges attract, and a gravity keeps components together. In
 * LinLog mode attraction grows with the logarithm of the distance, which
 * Noack (2009) showed makes the layout's clusters the graph's modularity
 * communities; "dissuade hubs" divides an edge's pull by its source's mass, so
 * followed accounts sit among their followers. Repulsion uses the Barnes-Hut
 * approximation (theta 1.2) on larger graphs, the speed adapts per node to
 * its swinging as in Gephi, and a final phase removes node overlaps. Initial
 * positions are seeded by node name, so a graph always gets the same picture.
 */
final class GraphLayout
{
	/** ForceAtlas2's settings. */
	static final class Settings
	{
		double scalingRatio = 2.0;
		double gravity = 1.0;
		boolean strongGravity;
		boolean linLog = true;
		boolean dissuadeHubs = true;
		double edgeWeightInfluence = 1.0;
		double jitterTolerance = 1.0;
		boolean barnesHut = true;
		double theta = 1.2;

		/** Gephi's defaults for a graph of n nodes, with LinLog and dissuade hubs. */
		static Settings forSize(int n)
		{
			Settings settings = new Settings();
			settings.scalingRatio = n >= 100 ? 2.0 : 10.0;
			settings.barnesHut = n > 400;
			return settings;
		}

		String describe()
		{
			return String.format(java.util.Locale.US,
					"ForceAtlas2, %s attraction, %s, scaling %.1f, gravity %.1f%s, %s",
					linLog ? "LinLog" : "linear", dissuadeHubs ? "dissuade hubs" : "no hub dissuasion",
					scalingRatio, gravity, strongGravity ? " (strong)" : "",
					barnesHut ? "Barnes-Hut theta " + theta : "exact repulsion");
		}
	}

	private final int n;
	private final double[] x;
	private final double[] y;
	private final double[] dx;
	private final double[] dy;
	private final double[] oldDx;
	private final double[] oldDy;
	private final double[] mass;
	private final double[] size;
	private final int[] edgeSource;
	private final int[] edgeTarget;
	private final double[] edgeWeight;
	private final Settings settings;
	private double speed = 1.0;
	private double speedEfficiency = 1.0;
	private boolean adjustSizes;
	private int iterations;
	private int calmIterations;
	private boolean converged;

	/**
	 * A layout of {@code n} nodes and the directed weighted edges
	 * {@code sources[i]} to {@code targets[i]} (parallel edges summed,
	 * self-loops ignored), starting from positions seeded by {@code seeds}.
	 */
	GraphLayout(int n, int[] sources, int[] targets, double[] weights, long[] seeds, Settings settings)
	{
		this.n = n;
		this.settings = settings == null ? Settings.forSize(n) : settings;
		x = new double[n];
		y = new double[n];
		dx = new double[n];
		dy = new double[n];
		oldDx = new double[n];
		oldDy = new double[n];
		mass = new double[n];
		size = new double[n];
		NetworkAlgorithms.Directed merged = NetworkAlgorithms.Directed.of(n, sources, targets, weights);
		int m = merged.neighbors.length;
		edgeSource = new int[m];
		edgeTarget = new int[m];
		edgeWeight = new double[m];
		int cursor = 0;
		for (int node = 0; node < n; node++)
		{
			for (int edge = merged.offsets[node]; edge < merged.offsets[node + 1]; edge++)
			{
				edgeSource[cursor] = node;
				edgeTarget[cursor] = merged.neighbors[edge];
				edgeWeight[cursor++] = merged.weights[edge];
			}
		}
		double[] degree = new double[n];
		for (int edge = 0; edge < m; edge++)
		{
			degree[edgeSource[edge]]++;
			degree[edgeTarget[edge]]++;
		}
		double spread = 10.0 * Math.sqrt(Math.max(1, n));
		for (int node = 0; node < n; node++)
		{
			mass[node] = 1.0 + degree[node];
			SplittableRandom random = new SplittableRandom(seeds == null ? node : seeds[node]);
			x[node] = (random.nextDouble() - 0.5) * spread;
			y[node] = (random.nextDouble() - 0.5) * spread;
		}
	}

	/** Runs until the layout is calm or {@code maxIterations} steps have run. */
	void run(int maxIterations)
	{
		while (!converged && iterations < maxIterations)
		{
			step();
		}
	}

	/** A sensible iteration budget for a graph of n nodes and m edges. */
	static int budget(int n, int m)
	{
		double work = Math.max(1.0, (n + m) * Math.log(Math.max(2, n)));
		return (int)Math.max(150, Math.min(1500, 4.0e7 / work));
	}

	/**
	 * Removes overlaps between nodes of the given radii (layout units): a few
	 * hundred steps with sizes taken into account, as Gephi's "prevent
	 * overlap" does.
	 */
	void preventOverlap(double[] radii, int steps)
	{
		System.arraycopy(radii, 0, size, 0, n);
		adjustSizes = true;
		for (int step = 0; step < steps; step++)
		{
			if (step >= 10 && step % 10 == 0 && !hasOverlap())
			{
				break;
			}
			step();
		}
		adjustSizes = false;
	}

	boolean isConverged()
	{
		return converged;
	}

	int iterations()
	{
		return iterations;
	}

	/** The positions, centred on the origin: {x[], y[]}. */
	double[][] positions()
	{
		double cx = 0.0;
		double cy = 0.0;
		for (int node = 0; node < n; node++)
		{
			cx += x[node];
			cy += y[node];
		}
		cx /= Math.max(1, n);
		cy /= Math.max(1, n);
		double[] px = new double[n];
		double[] py = new double[n];
		for (int node = 0; node < n; node++)
		{
			px[node] = x[node] - cx;
			py[node] = y[node] - cy;
		}
		return new double[][] {px, py};
	}

	/** The width of the positions' bounding box (at least 1). */
	double extent()
	{
		double minX = Double.MAX_VALUE;
		double maxX = -Double.MAX_VALUE;
		double minY = Double.MAX_VALUE;
		double maxY = -Double.MAX_VALUE;
		for (int node = 0; node < n; node++)
		{
			minX = Math.min(minX, x[node]);
			maxX = Math.max(maxX, x[node]);
			minY = Math.min(minY, y[node]);
			maxY = Math.max(maxY, y[node]);
		}
		return Math.max(1.0, Math.max(maxX - minX, maxY - minY));
	}

	/** One ForceAtlas2 step. */
	void step()
	{
		if (n == 0)
		{
			converged = true;
			return;
		}
		for (int node = 0; node < n; node++)
		{
			oldDx[node] = dx[node];
			oldDy[node] = dy[node];
			dx[node] = 0.0;
			dy[node] = 0.0;
		}
		double coefficient = settings.scalingRatio;
		if (settings.barnesHut && n > 1)
		{
			Region root = Region.build(this, allNodes());
			for (int node = 0; node < n; node++)
			{
				root.repel(this, node, coefficient, settings.theta);
			}
		}
		else
		{
			for (int a = 0; a < n; a++)
			{
				for (int b = 0; b < a; b++)
				{
					repelPair(a, b, coefficient);
				}
			}
		}
		double gravity = settings.gravity / settings.scalingRatio;
		for (int node = 0; node < n; node++)
		{
			double distance = Math.sqrt(x[node] * x[node] + y[node] * y[node]);
			if (distance > 0.0)
			{
				double factor = settings.strongGravity ? coefficient * mass[node] * gravity
						: coefficient * mass[node] * gravity / distance;
				dx[node] -= x[node] * factor;
				dy[node] -= y[node] * factor;
			}
		}
		double attraction = 1.0;
		if (settings.dissuadeHubs)
		{
			double total = 0.0;
			for (double value : mass)
			{
				total += value;
			}
			attraction = total / n;
		}
		for (int edge = 0; edge < edgeSource.length; edge++)
		{
			int a = edgeSource[edge];
			int b = edgeTarget[edge];
			double weight = settings.edgeWeightInfluence == 0.0 ? 1.0
					: settings.edgeWeightInfluence == 1.0 ? edgeWeight[edge]
							: Math.pow(edgeWeight[edge], settings.edgeWeightInfluence);
			double xDistance = x[a] - x[b];
			double yDistance = y[a] - y[b];
			double distance = Math.sqrt(xDistance * xDistance + yDistance * yDistance);
			if (adjustSizes)
			{
				distance -= size[a] + size[b];
				if (distance <= 0.0)
				{
					continue;
				}
			}
			double factor;
			if (settings.linLog)
			{
				if (distance <= 0.0)
				{
					continue;
				}
				factor = -attraction * weight * Math.log(1.0 + distance) / distance;
			}
			else
			{
				factor = -attraction * weight;
			}
			if (settings.dissuadeHubs)
			{
				factor /= mass[a];
			}
			dx[a] += xDistance * factor;
			dy[a] += yDistance * factor;
			dx[b] -= xDistance * factor;
			dy[b] -= yDistance * factor;
		}
		adjustSpeedAndMove();
		iterations++;
	}

	private void adjustSpeedAndMove()
	{
		double swingingTotal = 0.0;
		double traction = 0.0;
		for (int node = 0; node < n; node++)
		{
			double swing = Math.sqrt((oldDx[node] - dx[node]) * (oldDx[node] - dx[node])
					+ (oldDy[node] - dy[node]) * (oldDy[node] - dy[node]));
			swingingTotal += mass[node] * swing;
			traction += mass[node] * 0.5 * Math.sqrt((oldDx[node] + dx[node]) * (oldDx[node] + dx[node])
					+ (oldDy[node] + dy[node]) * (oldDy[node] + dy[node]));
		}
		double estimated = 0.05 * Math.sqrt(n);
		double minJitter = Math.sqrt(estimated);
		double jitter = settings.jitterTolerance * Math.max(minJitter,
				Math.min(10.0, estimated * traction / ((double)n * n)));
		if (swingingTotal > 0.0 && traction > 0.0 && swingingTotal / traction > 2.0)
		{
			if (speedEfficiency > 0.05)
			{
				speedEfficiency *= 0.5;
			}
			jitter = Math.max(jitter, settings.jitterTolerance);
		}
		double targetSpeed = swingingTotal > 0.0 ? jitter * speedEfficiency * traction / swingingTotal : speed * 1.5;
		if (swingingTotal > jitter * traction)
		{
			if (speedEfficiency > 0.05)
			{
				speedEfficiency *= 0.7;
			}
		}
		else if (speed < 1000.0)
		{
			speedEfficiency *= 1.3;
		}
		speed = speed + Math.min(targetSpeed - speed, 0.5 * speed);
		if (!(speed > 0.0) || Double.isInfinite(speed))
		{
			speed = 1.0;
		}
		double moved = 0.0;
		for (int node = 0; node < n; node++)
		{
			double swing = mass[node] * Math.sqrt((oldDx[node] - dx[node]) * (oldDx[node] - dx[node])
					+ (oldDy[node] - dy[node]) * (oldDy[node] - dy[node]));
			double factor;
			if (adjustSizes)
			{
				factor = 0.1 * speed / (1.0 + Math.sqrt(speed * swing));
				double force = Math.sqrt(dx[node] * dx[node] + dy[node] * dy[node]);
				factor = force > 0.0 ? Math.min(factor * force, 10.0) / force : 0.0;
			}
			else
			{
				factor = speed / (1.0 + Math.sqrt(speed * swing));
			}
			double stepX = dx[node] * factor;
			double stepY = dy[node] * factor;
			if (Double.isNaN(stepX) || Double.isNaN(stepY))
			{
				continue;
			}
			x[node] += stepX;
			y[node] += stepY;
			moved += Math.sqrt(stepX * stepX + stepY * stepY);
		}
		double relative = moved / n / extent();
		if (relative < 2e-4)
		{
			calmIterations++;
			if (calmIterations >= 30 && !adjustSizes)
			{
				converged = true;
			}
		}
		else
		{
			calmIterations = 0;
		}
	}

	private void repelPair(int a, int b, double coefficient)
	{
		double xDistance = x[a] - x[b];
		double yDistance = y[a] - y[b];
		double distance = Math.sqrt(xDistance * xDistance + yDistance * yDistance);
		double factor;
		if (adjustSizes)
		{
			double gap = distance - size[a] - size[b];
			if (gap > 0.0)
			{
				factor = coefficient * mass[a] * mass[b] / gap / gap;
			}
			else if (gap < 0.0)
			{
				factor = 100.0 * coefficient * mass[a] * mass[b];
			}
			else
			{
				return;
			}
		}
		else
		{
			if (distance <= 0.0)
			{
				return;
			}
			factor = coefficient * mass[a] * mass[b] / distance / distance;
		}
		dx[a] += xDistance * factor;
		dy[a] += yDistance * factor;
		dx[b] -= xDistance * factor;
		dy[b] -= yDistance * factor;
	}

	/** The repulsion of node {@code b} on node {@code a} only (Barnes-Hut leaves). */
	private void repelOneSided(int a, int b, double coefficient)
	{
		if (a == b)
		{
			return;
		}
		double xDistance = x[a] - x[b];
		double yDistance = y[a] - y[b];
		double distance = Math.sqrt(xDistance * xDistance + yDistance * yDistance);
		double factor;
		if (adjustSizes)
		{
			double gap = distance - size[a] - size[b];
			if (gap > 0.0)
			{
				factor = coefficient * mass[a] * mass[b] / gap / gap;
			}
			else if (gap < 0.0)
			{
				factor = 100.0 * coefficient * mass[a] * mass[b];
			}
			else
			{
				return;
			}
		}
		else
		{
			if (distance <= 0.0)
			{
				return;
			}
			factor = coefficient * mass[a] * mass[b] / distance / distance;
		}
		dx[a] += xDistance * factor;
		dy[a] += yDistance * factor;
	}

	private boolean hasOverlap()
	{
		if (n > 1500)
		{
			return true;
		}
		for (int a = 0; a < n; a++)
		{
			for (int b = 0; b < a; b++)
			{
				double xDistance = x[a] - x[b];
				double yDistance = y[a] - y[b];
				double reach = size[a] + size[b];
				if (xDistance * xDistance + yDistance * yDistance < reach * reach)
				{
					return true;
				}
			}
		}
		return false;
	}

	private int[] allNodes()
	{
		int[] nodes = new int[n];
		for (int node = 0; node < n; node++)
		{
			nodes[node] = node;
		}
		return nodes;
	}

	/** A Barnes-Hut region: its nodes' total mass, centre of mass and size, split into quadrants. */
	private static final class Region
	{
		private final int[] nodes;
		private double mass;
		private double centerX;
		private double centerY;
		private double size;
		private Region[] children = new Region[0];

		private Region(int[] nodes)
		{
			this.nodes = nodes;
		}

		static Region build(GraphLayout layout, int[] nodes)
		{
			Region region = new Region(nodes);
			region.measure(layout);
			region.split(layout, 0);
			return region;
		}

		private void measure(GraphLayout layout)
		{
			double sumX = 0.0;
			double sumY = 0.0;
			mass = 0.0;
			for (int node : nodes)
			{
				mass += layout.mass[node];
				sumX += layout.x[node] * layout.mass[node];
				sumY += layout.y[node] * layout.mass[node];
			}
			centerX = sumX / mass;
			centerY = sumY / mass;
			size = Double.MIN_VALUE;
			for (int node : nodes)
			{
				double distance = Math.sqrt((layout.x[node] - centerX) * (layout.x[node] - centerX)
						+ (layout.y[node] - centerY) * (layout.y[node] - centerY));
				size = Math.max(size, 2.0 * distance);
			}
		}

		private void split(GraphLayout layout, int depth)
		{
			if (nodes.length < 2)
			{
				return;
			}
			int[][] quadrants = new int[4][nodes.length];
			int[] counts = new int[4];
			for (int node : nodes)
			{
				int quadrant = (layout.x[node] < centerX ? 0 : 2) + (layout.y[node] < centerY ? 0 : 1);
				quadrants[quadrant][counts[quadrant]++] = node;
			}
			java.util.List<Region> regions = new java.util.ArrayList<Region>();
			for (int quadrant = 0; quadrant < 4; quadrant++)
			{
				if (counts[quadrant] == 0)
				{
					continue;
				}
				if (counts[quadrant] < nodes.length && depth < 64)
				{
					regions.add(new Region(Arrays.copyOf(quadrants[quadrant], counts[quadrant])));
				}
				else
				{
					for (int index = 0; index < counts[quadrant]; index++)
					{
						regions.add(new Region(new int[] {quadrants[quadrant][index]}));
					}
				}
			}
			children = regions.toArray(new Region[regions.size()]);
			for (Region child : children)
			{
				child.measure(layout);
				child.split(layout, depth + 1);
			}
		}

		void repel(GraphLayout layout, int node, double coefficient, double theta)
		{
			if (nodes.length < 2)
			{
				layout.repelOneSided(node, nodes[0], coefficient);
				return;
			}
			double xDistance = layout.x[node] - centerX;
			double yDistance = layout.y[node] - centerY;
			double distance = Math.sqrt(xDistance * xDistance + yDistance * yDistance);
			if (distance * theta > size)
			{
				if (distance > 0.0)
				{
					double factor = coefficient * layout.mass[node] * mass / distance / distance;
					layout.dx[node] += xDistance * factor;
					layout.dy[node] += yDistance * factor;
				}
				return;
			}
			for (Region child : children)
			{
				child.repel(layout, node, coefficient, theta);
			}
		}
	}
}
