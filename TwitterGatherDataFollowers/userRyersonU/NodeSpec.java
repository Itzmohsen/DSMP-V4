package TwitterGatherDataFollowers.userRyersonU;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Immutable scheduling and emulation view of one recommender node.
 *
 * Every field has one physical meaning, shared by the placement planner, the
 * work dispatcher, the Scheduler Lab, the Dynamic Queue Lab and the live
 * runtime:
 *
 * <ul>
 * <li>{@code speed}: relative processing speed in (0, 1]. The node whose
 *     nominal capacity (CPU multiplier x live capacity scale) is highest runs
 *     at hardware speed, 1.0; every other node is proportionally slower, and a
 *     slowdown multiplier divides the result further. A unit of reference work
 *     W therefore takes W / speed on this node. In a live run the slower nodes
 *     are really slowed: their measured compute is dilated by 1/speed.</li>
 * <li>{@code lanes}: units the node can execute concurrently (worker threads
 *     in a live run, parallel machines in the lab).</li>
 * <li>{@code memoryMb}: the node's memory budget. Units whose estimated
 *     memory does not fit the free budget are not started on the node.</li>
 * <li>{@code queueCapacity}: the most units the node may hold at once
 *     (running plus reserved). Anything above {@code lanes} is prefetch.</li>
 * <li>{@code delayMs}: one-way latency of the node's link to the coordinator.
 *     A message between two nodes costs the sum of their delays.</li>
 * <li>{@code failureProbability}: probability that one execution of a unit on
 *     this node is lost and must be retried (injected only in fault-injection
 *     runs).</li>
 * </ul>
 */
final class NodeSpec
{
	private final int index;
	private final String roleLabel;
	private final double cpuMultiplier;
	private final double capacityScale;
	private final double slowdown;
	private final double speed;
	private final int lanes;
	private final int memoryMb;
	private final int queueCapacity;
	private final int delayMs;
	private final double failureProbability;

	NodeSpec(
			int index,
			String roleLabel,
			double cpuMultiplier,
			double capacityScale,
			double slowdown,
			double speed,
			int lanes,
			int memoryMb,
			int queueCapacity,
			int delayMs,
			double failureProbability)
	{
		this.index = Math.max(1, index);
		this.roleLabel = roleLabel == null ? "Node" : roleLabel;
		this.cpuMultiplier = positive(cpuMultiplier, 1.0);
		this.capacityScale = positive(capacityScale, 1.0);
		this.slowdown = Math.max(1.0, positive(slowdown, 1.0));
		this.speed = Math.max(1.0e-6, Math.min(1.0, positive(speed, 1.0)));
		this.lanes = Math.max(1, lanes);
		this.memoryMb = Math.max(1, memoryMb);
		this.queueCapacity = Math.max(this.lanes, queueCapacity);
		this.delayMs = Math.max(0, delayMs);
		double probability = Double.isNaN(failureProbability) ? 0.0 : failureProbability;
		this.failureProbability = Math.max(0.0, Math.min(1.0, probability));
	}

	/**
	 * Builds the specs for a set of capacity profiles.
	 *
	 * @param profiles       one profile per node, any order
	 * @param capacityScales live capacity scale per node index (1-based slot
	 *                       {@code index - 1}); null or missing means 1.0
	 * @param emulate        false gives every node hardware speed, no delay and
	 *                       no failures (the original, capacity-oblivious model)
	 */
	static List<NodeSpec> fromProfiles(
			List<NodeCapacityProfile> profiles,
			double[] capacityScales,
			boolean emulate)
	{
		ArrayList<NodeCapacityProfile> sorted = new ArrayList<NodeCapacityProfile>();
		if (profiles != null)
		{
			for (NodeCapacityProfile profile : profiles)
			{
				if (profile != null)
				{
					sorted.add(profile);
				}
			}
		}
		Collections.sort(sorted, new java.util.Comparator<NodeCapacityProfile>()
		{
			public int compare(NodeCapacityProfile first, NodeCapacityProfile second)
			{
				return Integer.compare(first.getNodeIndex(), second.getNodeIndex());
			}
		});
		ArrayList<NodeSpec> specs = new ArrayList<NodeSpec>();
		if (sorted.isEmpty())
		{
			return specs;
		}
		double fastestNominal = 0.0;
		for (NodeCapacityProfile profile : sorted)
		{
			fastestNominal = Math.max(
					fastestNominal,
					nominalCapacity(profile, scaleFor(capacityScales, profile)));
		}
		for (NodeCapacityProfile profile : sorted)
		{
			double scale = scaleFor(capacityScales, profile);
			if (!emulate)
			{
				specs.add(new NodeSpec(
						profile.getNodeIndex(),
						profile.getRoleLabel(),
						profile.getCpuMultiplier(),
						1.0,
						1.0,
						1.0,
						profile.getMaxConcurrentJobs(),
						profile.getMemoryLimitMb(),
						profile.getQueueCapacity(),
						0,
						0.0));
				continue;
			}
			double slowdown = profile.isSlowdownSimulationEnabled()
					? Math.max(1.0, profile.getSlowdownMultiplier()) : 1.0;
			double speed = nominalCapacity(profile, scale)
					/ Math.max(1.0e-9, fastestNominal) / slowdown;
			specs.add(new NodeSpec(
					profile.getNodeIndex(),
					profile.getRoleLabel(),
					profile.getCpuMultiplier(),
					scale,
					slowdown,
					speed,
					profile.getMaxConcurrentJobs(),
					profile.getMemoryLimitMb(),
					profile.getQueueCapacity(),
					profile.getNetworkDelayMs(),
					profile.isFailureSimulationEnabled()
							? profile.getFailureProbabilityPercent() / 100.0 : 0.0));
		}
		return Collections.unmodifiableList(specs);
	}

	/** N identical hardware-speed nodes with one lane each. */
	static List<NodeSpec> uniform(int nodeCount)
	{
		ArrayList<NodeSpec> specs = new ArrayList<NodeSpec>();
		for (int node = 1; node <= Math.max(1, nodeCount); node++)
		{
			specs.add(new NodeSpec(node, "Normal", 1.0, 1.0, 1.0, 1.0,
					1, 1 << 20, 1, 0, 0.0));
		}
		return Collections.unmodifiableList(specs);
	}

	private static double nominalCapacity(NodeCapacityProfile profile, double scale)
	{
		return Math.max(1.0e-6, profile.getCpuMultiplier()) * Math.max(1.0e-6, scale);
	}

	private static double scaleFor(double[] scales, NodeCapacityProfile profile)
	{
		int slot = profile.getNodeIndex() - 1;
		if (scales == null || slot < 0 || slot >= scales.length)
		{
			return 1.0;
		}
		double scale = scales[slot];
		return Double.isNaN(scale) || scale <= 0.0 ? 1.0 : scale;
	}

	private static double positive(double value, double fallback)
	{
		return Double.isNaN(value) || Double.isInfinite(value) || value <= 0.0
				? fallback : value;
	}

	int getIndex()
	{
		return index;
	}

	String getRoleLabel()
	{
		return roleLabel;
	}

	double getCpuMultiplier()
	{
		return cpuMultiplier;
	}

	double getCapacityScale()
	{
		return capacityScale;
	}

	double getSlowdown()
	{
		return slowdown;
	}

	/** Relative speed in (0, 1]; reference work W takes W / speed here. */
	double getSpeed()
	{
		return speed;
	}

	int getLanes()
	{
		return lanes;
	}

	int getMemoryMb()
	{
		return memoryMb;
	}

	int getQueueCapacity()
	{
		return queueCapacity;
	}

	int getDelayMs()
	{
		return delayMs;
	}

	double getFailureProbability()
	{
		return failureProbability;
	}

	/** Reference work per unit time with every lane busy. */
	double getThroughput()
	{
		return speed * lanes;
	}

	/** Throughput that survives failures: throughput x (1 - failure). */
	double getReliableThroughput()
	{
		return getThroughput() * (1.0 - failureProbability);
	}

	String describe()
	{
		return String.format(
				Locale.US,
				"Node %d %s: speed %.3f, lanes %d, throughput %.3f, memory %d MB, queue %d, delay %d ms, failure %.1f%%",
				index,
				roleLabel,
				speed,
				lanes,
				getThroughput(),
				memoryMb,
				queueCapacity,
				delayMs,
				failureProbability * 100.0);
	}
}
