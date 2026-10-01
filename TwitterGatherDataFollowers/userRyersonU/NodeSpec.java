package TwitterGatherDataFollowers.userRyersonU;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Immutable model of one recommender node, shared by the placement planner,
 * the work dispatcher, the discrete-event simulator and the live emulator.
 *
 * <ul>
 * <li>{@code speed}: absolute processing speed in reference cores. Reference
 *     work W (the time the work takes on a speed-1 node) takes W / speed on
 *     this node. Speeds never depend on the other nodes, so two cluster
 *     configurations can be compared on one time scale.</li>
 * <li>{@code lanes}: units the node executes concurrently.</li>
 * <li>{@code memoryMb}: admission budget for units that declare memory.</li>
 * <li>{@code queueCapacity}: the most units the node holds at once (running
 *     plus delivered); the coordinator keeps the rest.</li>
 * <li>{@code delayMs}: one-way latency of the node's link to the coordinator.</li>
 * <li>{@code bandwidthBytesPerMs}: serialization rate of that link in each
 *     direction; 0 means unlimited.</li>
 * <li>{@code failureProbability}: probability that one execution of a unit is
 *     lost to a transient fault (fault-injection runs only).</li>
 * <li>{@code mtbfMs}, {@code mttrMs}: the node's crash-recovery process
 *     (fault-injection runs only); 0 MTBF means no crashes.</li>
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
	private final double bandwidthBytesPerMs;
	private final double failureProbability;
	private final double mtbfMs;
	private final double mttrMs;

	/** A node with an unlimited link and no crash process. */
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
		this(index, roleLabel, cpuMultiplier, capacityScale, slowdown, speed, lanes,
				memoryMb, queueCapacity, delayMs, 0.0, failureProbability, 0.0, 0.0);
	}

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
			double bandwidthBytesPerMs,
			double failureProbability,
			double mtbfMs,
			double mttrMs)
	{
		this.index = Math.max(1, index);
		this.roleLabel = roleLabel == null ? "Node" : roleLabel;
		this.cpuMultiplier = positive(cpuMultiplier, 1.0);
		this.capacityScale = positive(capacityScale, 1.0);
		this.slowdown = Math.max(1.0, positive(slowdown, 1.0));
		this.speed = Math.max(1.0e-6, positive(speed, 1.0));
		this.lanes = Math.max(1, lanes);
		this.memoryMb = Math.max(1, memoryMb);
		this.queueCapacity = Math.max(this.lanes, queueCapacity);
		this.delayMs = Math.max(0, delayMs);
		this.bandwidthBytesPerMs = Double.isNaN(bandwidthBytesPerMs) || bandwidthBytesPerMs <= 0.0
				? 0.0 : bandwidthBytesPerMs;
		double probability = Double.isNaN(failureProbability) ? 0.0 : failureProbability;
		this.failureProbability = Math.max(0.0, Math.min(1.0, probability));
		this.mtbfMs = Double.isNaN(mtbfMs) || mtbfMs <= 0.0 ? 0.0 : mtbfMs;
		this.mttrMs = Double.isNaN(mttrMs) || mttrMs <= 0.0 ? 0.0 : mttrMs;
	}

	/**
	 * The specs of a set of capacity profiles, in node order.
	 *
	 * @param profiles       one profile per node, any order
	 * @param capacityScales live capacity scale per node (slot {@code index - 1});
	 *                       null or missing means 1.0
	 * @param emulate        false gives the unemulated hardware model: every node
	 *                       at speed 1 with no link cost and no faults
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
		for (NodeCapacityProfile profile : sorted)
		{
			specs.add(emulate
					? fromProfile(profile, scaleFor(capacityScales, profile))
					: hardware(profile));
		}
		return Collections.unmodifiableList(specs);
	}

	/** The emulated model of one profile at a live capacity scale. */
	static NodeSpec fromProfile(NodeCapacityProfile profile, double scale)
	{
		double safeScale = Double.isNaN(scale) || scale <= 0.0 ? 1.0 : scale;
		double slowdown = profile.isSlowdownSimulationEnabled()
				? Math.max(1.0, profile.getSlowdownMultiplier()) : 1.0;
		double speed = profile.getCpuMultiplier() * safeScale / slowdown;
		return new NodeSpec(
				profile.getNodeIndex(),
				profile.getRoleLabel(),
				profile.getCpuMultiplier(),
				safeScale,
				slowdown,
				speed,
				profile.getMaxConcurrentJobs(),
				profile.getMemoryLimitMb(),
				profile.getQueueCapacity(),
				profile.getNetworkDelayMs(),
				mbpsToBytesPerMs(profile.getBandwidthMbps()),
				profile.isFailureSimulationEnabled()
						? profile.getFailureProbabilityPercent() / 100.0 : 0.0,
				profile.getMtbfSeconds() * 1000.0,
				profile.getMttrSeconds() * 1000.0);
	}

	/** The unemulated model: hardware speed, free links, no faults. */
	static NodeSpec hardware(NodeCapacityProfile profile)
	{
		return new NodeSpec(
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
				0.0,
				0.0,
				0.0,
				0.0);
	}

	/** N identical reference nodes with one lane each and free links. */
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

	/** Megabits per second to bytes per millisecond; 0 stays unlimited. */
	static double mbpsToBytesPerMs(double mbps)
	{
		return mbps <= 0.0 || Double.isNaN(mbps) ? 0.0 : mbps * 125.0;
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

	/** The same node renumbered (used when planning over a subset of nodes). */
	NodeSpec reindexed(int newIndex)
	{
		return new NodeSpec(newIndex, roleLabel, cpuMultiplier, capacityScale, slowdown,
				speed, lanes, memoryMb, queueCapacity, delayMs, bandwidthBytesPerMs,
				failureProbability, mtbfMs, mttrMs);
	}

	/** The same node at another speed (used to model misestimated capacity). */
	NodeSpec withSpeed(double newSpeed)
	{
		return new NodeSpec(index, roleLabel, cpuMultiplier, capacityScale, slowdown,
				newSpeed, lanes, memoryMb, queueCapacity, delayMs, bandwidthBytesPerMs,
				failureProbability, mtbfMs, mttrMs);
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

	/** Absolute speed in reference cores; reference work W takes W / speed. */
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

	/** One-way link latency in milliseconds. */
	int getDelayMs()
	{
		return delayMs;
	}

	/** Link serialization rate in bytes per millisecond; 0 is unlimited. */
	double getBandwidthBytesPerMs()
	{
		return bandwidthBytesPerMs;
	}

	/** Milliseconds the link is busy sending {@code bytes}. */
	double serializationMs(long bytes)
	{
		return bandwidthBytesPerMs <= 0.0 || bytes <= 0L ? 0.0 : bytes / bandwidthBytesPerMs;
	}

	double getFailureProbability()
	{
		return failureProbability;
	}

	/** Mean time between crashes in model milliseconds; 0 means never. */
	double getMtbfMs()
	{
		return mtbfMs;
	}

	/** Mean time to repair in model milliseconds. */
	double getMttrMs()
	{
		return mttrMs;
	}

	boolean hasCrashProcess()
	{
		return mtbfMs > 0.0;
	}

	/** Steady-state availability MTBF / (MTBF + MTTR); 1 without crashes. */
	double getAvailability()
	{
		return mtbfMs <= 0.0 ? 1.0 : mtbfMs / (mtbfMs + mttrMs);
	}

	/** Reference work per unit time with every lane busy. */
	double getThroughput()
	{
		return speed * lanes;
	}

	/** Throughput that survives transient faults and crashes. */
	double getReliableThroughput()
	{
		return getThroughput() * (1.0 - failureProbability) * getAvailability();
	}

	String describe()
	{
		return String.format(
				Locale.US,
				"Node %d %s: speed %.3f, lanes %d, throughput %.3f, memory %d MB, queue %d, link %d ms / %s, faults %.1f%%%s",
				index,
				roleLabel,
				speed,
				lanes,
				getThroughput(),
				memoryMb,
				queueCapacity,
				delayMs,
				bandwidthBytesPerMs <= 0.0 ? "unlimited"
						: NodeCapacityProfile.formatBandwidth(bandwidthBytesPerMs / 125.0),
				failureProbability * 100.0,
				hasCrashProcess()
						? String.format(Locale.US, ", MTBF %.1f s / MTTR %.1f s",
								mtbfMs / 1000.0, mttrMs / 1000.0)
						: "");
	}
}
