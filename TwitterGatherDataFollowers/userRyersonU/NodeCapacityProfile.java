package TwitterGatherDataFollowers.userRyersonU;

import java.text.DecimalFormat;
import java.util.Locale;

/**
 * One node's capacity contract, as entered by the user.
 *
 * Every field has one physical meaning in the cluster model (see
 * {@link NodeSpec} and DSMP_V4_NODE_LAB.md):
 *
 * <ul>
 * <li>CPU multiplier: the node's processing speed in reference cores. A node of
 *     speed 2 does the same work in half the time of the reference node (speed
 *     1). Speeds are absolute: they never depend on the other nodes.</li>
 * <li>Max concurrent jobs: lanes, the units the node executes at once.</li>
 * <li>Memory limit: admission budget for units that declare memory.</li>
 * <li>Queue capacity: the most units the node holds at once (running plus
 *     delivered); the rest wait at the coordinator.</li>
 * <li>Network delay: one-way latency of the node's link to the coordinator.</li>
 * <li>Bandwidth: the link's serialization rate in megabits per second (0 is
 *     unlimited); a message of b bytes occupies the link for b / bandwidth.</li>
 * <li>Slowdown: a degradation factor that divides the speed further.</li>
 * <li>Failure probability: the fraction of unit executions lost to transient
 *     faults (detected when the execution ends).</li>
 * <li>MTBF / MTTR: the node's crash-recovery process; the node crashes after
 *     a random up-time with mean MTBF and is repaired after a down-time with
 *     mean MTTR. Crashes lose the work in progress.</li>
 * </ul>
 *
 * Failures and crashes are injected only in fault-injection runs.
 */
final class NodeCapacityProfile
{
	enum Role
	{
		NORMAL("Normal", "Reference core, one lane, 1 Gbit/s LAN link"),
		WEAK("Weak", "Half speed, half memory, 25 ms / 100 Mbit/s link"),
		STRONG("Strong", "Double speed, two lanes, double memory, 10 Gbit/s link"),
		MEMORY_HEAVY("Memory-heavy", "Reference speed, double memory"),
		UNRELIABLE("Unreliable", "0.75 speed, 1.5x slowdown, 100 ms link, 5% faults, crashes");

		private final String label;
		private final String description;

		Role(String label, String description)
		{
			this.label = label;
			this.description = description;
		}

		String getLabel()
		{
			return label;
		}

		String getDescription()
		{
			return description;
		}

		public String toString()
		{
			return label;
		}
	}

	static final double MIN_CPU_MULTIPLIER = 0.05;
	static final double MAX_CPU_MULTIPLIER = 16.00;
	static final int MIN_MEMORY_MB = 128;
	static final int MAX_MEMORY_MB = 262144;
	static final int MIN_MAX_CONCURRENT_JOBS = 1;
	static final int MAX_MAX_CONCURRENT_JOBS = 64;
	static final int MIN_NETWORK_DELAY_MS = 0;
	static final int MAX_NETWORK_DELAY_MS = 600000;
	static final int MIN_QUEUE_CAPACITY = 1;
	static final int MAX_QUEUE_CAPACITY = 4096;
	static final double MIN_SLOWDOWN_MULTIPLIER = 1.00;
	static final double MAX_SLOWDOWN_MULTIPLIER = 50.00;
	static final int MIN_FAILURE_PERCENT = 0;
	static final int MAX_FAILURE_PERCENT = 100;
	static final double MAX_BANDWIDTH_MBPS = 1000000.0;
	static final double MAX_MTBF_SECONDS = 1.0e7;
	static final double MAX_MTTR_SECONDS = 1.0e6;

	private static final DecimalFormat DECIMAL = new DecimalFormat("0.##");

	private final int nodeIndex;
	private final Role role;
	private final double cpuMultiplier;
	private final int memoryLimitMb;
	private final int maxConcurrentJobs;
	private final int networkDelayMs;
	private final int queueCapacity;
	private final boolean slowdownSimulationEnabled;
	private final double slowdownMultiplier;
	private final boolean failureSimulationEnabled;
	private final int failureProbabilityPercent;
	private final double bandwidthMbps;
	private final double mtbfSeconds;
	private final double mttrSeconds;

	/** A profile with an unlimited link and no crash process. */
	NodeCapacityProfile(
			int nodeIndex,
			Role role,
			double cpuMultiplier,
			int memoryLimitMb,
			int maxConcurrentJobs,
			int networkDelayMs,
			int queueCapacity,
			boolean slowdownSimulationEnabled,
			double slowdownMultiplier,
			boolean failureSimulationEnabled,
			int failureProbabilityPercent)
	{
		this(nodeIndex, role, cpuMultiplier, memoryLimitMb, maxConcurrentJobs,
				networkDelayMs, queueCapacity, slowdownSimulationEnabled,
				slowdownMultiplier, failureSimulationEnabled, failureProbabilityPercent,
				0.0, 0.0, 0.0);
	}

	/**
	 * @param bandwidthMbps link bandwidth in megabits per second; 0 is unlimited
	 * @param mtbfSeconds   mean time between crashes; 0 disables crashes
	 * @param mttrSeconds   mean time to repair a crash
	 */
	NodeCapacityProfile(
			int nodeIndex,
			Role role,
			double cpuMultiplier,
			int memoryLimitMb,
			int maxConcurrentJobs,
			int networkDelayMs,
			int queueCapacity,
			boolean slowdownSimulationEnabled,
			double slowdownMultiplier,
			boolean failureSimulationEnabled,
			int failureProbabilityPercent,
			double bandwidthMbps,
			double mtbfSeconds,
			double mttrSeconds)
	{
		this.nodeIndex = Math.max(1, nodeIndex);
		this.role = role == null ? Role.NORMAL : role;
		this.cpuMultiplier = clampDouble(
				cpuMultiplier,
				MIN_CPU_MULTIPLIER,
				MAX_CPU_MULTIPLIER);
		this.memoryLimitMb = clampInt(
				memoryLimitMb,
				MIN_MEMORY_MB,
				MAX_MEMORY_MB);
		this.maxConcurrentJobs = clampInt(
				maxConcurrentJobs,
				MIN_MAX_CONCURRENT_JOBS,
				MAX_MAX_CONCURRENT_JOBS);
		this.networkDelayMs = clampInt(
				networkDelayMs,
				MIN_NETWORK_DELAY_MS,
				MAX_NETWORK_DELAY_MS);
		this.queueCapacity = clampInt(
				Math.max(queueCapacity, this.maxConcurrentJobs),
				MIN_QUEUE_CAPACITY,
				MAX_QUEUE_CAPACITY);
		this.slowdownSimulationEnabled = slowdownSimulationEnabled;
		this.slowdownMultiplier = clampDouble(
				slowdownMultiplier,
				MIN_SLOWDOWN_MULTIPLIER,
				MAX_SLOWDOWN_MULTIPLIER);
		this.failureSimulationEnabled = failureSimulationEnabled;
		this.failureProbabilityPercent = clampInt(
				failureProbabilityPercent,
				MIN_FAILURE_PERCENT,
				MAX_FAILURE_PERCENT);
		this.bandwidthMbps = nonNegative(bandwidthMbps, MAX_BANDWIDTH_MBPS);
		this.mtbfSeconds = nonNegative(mtbfSeconds, MAX_MTBF_SECONDS);
		this.mttrSeconds = nonNegative(mttrSeconds, MAX_MTTR_SECONDS);
	}

	static NodeCapacityProfile defaultProfile(
			int nodeIndex,
			int requestedNodes,
			int heapMb)
	{
		return rolePreset(
				nodeIndex,
				Role.NORMAL,
				requestedNodes,
				heapMb);
	}

	static NodeCapacityProfile rolePreset(
			int nodeIndex,
			Role role,
			int requestedNodes,
			int heapMb)
	{
		int baseMemory = defaultMemoryMb(requestedNodes, heapMb);
		if (role == Role.WEAK)
		{
			return new NodeCapacityProfile(
					nodeIndex, role, 0.50,
					Math.max(MIN_MEMORY_MB, baseMemory / 2),
					1, 25, 1,
					false, 1.0,
					false, 0,
					100.0, 0.0, 0.0);
		}
		if (role == Role.STRONG)
		{
			return new NodeCapacityProfile(
					nodeIndex, role, 2.00,
					Math.min(MAX_MEMORY_MB, baseMemory * 2),
					2, 0, 4,
					false, 1.0,
					false, 0,
					10000.0, 0.0, 0.0);
		}
		if (role == Role.MEMORY_HEAVY)
		{
			return new NodeCapacityProfile(
					nodeIndex, role, 1.00,
					Math.min(MAX_MEMORY_MB, baseMemory * 2),
					1, 0, 2,
					false, 1.0,
					false, 0,
					1000.0, 0.0, 0.0);
		}
		if (role == Role.UNRELIABLE)
		{
			return new NodeCapacityProfile(
					nodeIndex, role, 0.75,
					baseMemory,
					1, 100, 1,
					true, 1.50,
					true, 5,
					100.0, 30.0, 2.0);
		}
		return new NodeCapacityProfile(
				nodeIndex, Role.NORMAL, 1.00,
				baseMemory,
				1, 0, 2,
				false, 1.0,
				false, 0,
				1000.0, 0.0, 0.0);
	}

	/** The same profile for a different node index. */
	NodeCapacityProfile withNodeIndex(int index)
	{
		return new NodeCapacityProfile(
				index, role, cpuMultiplier, memoryLimitMb, maxConcurrentJobs,
				networkDelayMs, queueCapacity, slowdownSimulationEnabled,
				slowdownMultiplier, failureSimulationEnabled, failureProbabilityPercent,
				bandwidthMbps, mtbfSeconds, mttrSeconds);
	}

	/** The same profile with another link. */
	NodeCapacityProfile withLink(int delayMs, double bandwidth)
	{
		return new NodeCapacityProfile(
				nodeIndex, role, cpuMultiplier, memoryLimitMb, maxConcurrentJobs,
				delayMs, queueCapacity, slowdownSimulationEnabled,
				slowdownMultiplier, failureSimulationEnabled, failureProbabilityPercent,
				bandwidth, mtbfSeconds, mttrSeconds);
	}

	/** The same profile with another crash-recovery process. */
	NodeCapacityProfile withCrashProcess(double mtbf, double mttr)
	{
		return new NodeCapacityProfile(
				nodeIndex, role, cpuMultiplier, memoryLimitMb, maxConcurrentJobs,
				networkDelayMs, queueCapacity, slowdownSimulationEnabled,
				slowdownMultiplier, failureSimulationEnabled, failureProbabilityPercent,
				bandwidthMbps, mtbf, mttr);
	}

	/** The same profile with another speed, lanes and queue capacity. */
	NodeCapacityProfile withCompute(double cpu, int lanes, int queue)
	{
		return new NodeCapacityProfile(
				nodeIndex, role, cpu, memoryLimitMb, lanes,
				networkDelayMs, queue, slowdownSimulationEnabled,
				slowdownMultiplier, failureSimulationEnabled, failureProbabilityPercent,
				bandwidthMbps, mtbfSeconds, mttrSeconds);
	}

	private static int defaultMemoryMb(int requestedNodes, int heapMb)
	{
		int safeNodes = Math.max(1, requestedNodes);
		int safeHeap = Math.max(RuntimeResourceSettings.MIN_HEAP_MB, heapMb);
		return Math.max(MIN_MEMORY_MB, safeHeap / safeNodes);
	}

	private static int clampInt(int value, int minimum, int maximum)
	{
		return Math.max(minimum, Math.min(maximum, value));
	}

	private static double clampDouble(
			double value,
			double minimum,
			double maximum)
	{
		if (Double.isNaN(value) || Double.isInfinite(value))
		{
			return minimum;
		}
		return Math.max(minimum, Math.min(maximum, value));
	}

	private static double nonNegative(double value, double maximum)
	{
		if (Double.isNaN(value) || value <= 0.0)
		{
			return 0.0;
		}
		return Math.min(maximum, value);
	}

	/** True when every setting except the node index is equal. */
	boolean sameSettingsAs(NodeCapacityProfile other)
	{
		return other != null
				&& role == other.role
				&& Math.abs(cpuMultiplier - other.cpuMultiplier) < 1e-9
				&& memoryLimitMb == other.memoryLimitMb
				&& maxConcurrentJobs == other.maxConcurrentJobs
				&& networkDelayMs == other.networkDelayMs
				&& queueCapacity == other.queueCapacity
				&& slowdownSimulationEnabled == other.slowdownSimulationEnabled
				&& Math.abs(slowdownMultiplier - other.slowdownMultiplier) < 1e-9
				&& failureSimulationEnabled == other.failureSimulationEnabled
				&& failureProbabilityPercent == other.failureProbabilityPercent
				&& Math.abs(bandwidthMbps - other.bandwidthMbps) < 1e-9
				&& Math.abs(mtbfSeconds - other.mtbfSeconds) < 1e-9
				&& Math.abs(mttrSeconds - other.mttrSeconds) < 1e-9;
	}

	int getNodeIndex()
	{
		return nodeIndex;
	}

	Role getRole()
	{
		return role;
	}

	String getRoleLabel()
	{
		return role.getLabel();
	}

	String getRoleDescription()
	{
		return role.getDescription();
	}

	double getCpuMultiplier()
	{
		return cpuMultiplier;
	}

	int getMemoryLimitMb()
	{
		return memoryLimitMb;
	}

	int getMaxConcurrentJobs()
	{
		return maxConcurrentJobs;
	}

	int getNetworkDelayMs()
	{
		return networkDelayMs;
	}

	int getQueueCapacity()
	{
		return queueCapacity;
	}

	boolean isSlowdownSimulationEnabled()
	{
		return slowdownSimulationEnabled;
	}

	double getSlowdownMultiplier()
	{
		return slowdownMultiplier;
	}

	boolean isFailureSimulationEnabled()
	{
		return failureSimulationEnabled;
	}

	int getFailureProbabilityPercent()
	{
		return failureProbabilityPercent;
	}

	/** Link bandwidth in megabits per second; 0 means unlimited. */
	double getBandwidthMbps()
	{
		return bandwidthMbps;
	}

	/** Mean time between crashes in seconds; 0 means the node never crashes. */
	double getMtbfSeconds()
	{
		return mtbfSeconds;
	}

	/** Mean time to repair after a crash, in seconds. */
	double getMttrSeconds()
	{
		return mttrSeconds;
	}

	boolean hasCrashProcess()
	{
		return mtbfSeconds > 0.0;
	}

	/** Speed in reference cores: CPU multiplier divided by the slowdown. */
	double effectiveSpeed()
	{
		return cpuMultiplier / (slowdownSimulationEnabled ? slowdownMultiplier : 1.0);
	}

	String shortLabel()
	{
		return role.getLabel() + " "
				+ DECIMAL.format(cpuMultiplier) + "x";
	}

	String compactSummary()
	{
		return role.getLabel()
				+ " | CPU " + DECIMAL.format(cpuMultiplier) + "x"
				+ " | " + RuntimeResourceSettings.formatMegabytes(memoryLimitMb)
				+ " | jobs " + maxConcurrentJobs;
	}

	static String formatBandwidth(double mbps)
	{
		if (mbps <= 0.0)
		{
			return "unlimited";
		}
		if (mbps >= 1000.0)
		{
			return DECIMAL.format(mbps / 1000.0) + " Gbit/s";
		}
		return DECIMAL.format(mbps) + " Mbit/s";
	}

	String simulationSummary()
	{
		StringBuilder builder = new StringBuilder();
		builder.append(networkDelayMs > 0
				? networkDelayMs + " ms"
				: "0 ms");
		builder.append(" / ").append(formatBandwidth(bandwidthMbps));
		builder.append(" | ");
		builder.append(slowdownSimulationEnabled
				? "slowdown " + DECIMAL.format(slowdownMultiplier) + "x"
				: "slowdown off");
		builder.append(" | ");
		builder.append(failureSimulationEnabled
				? "faults " + failureProbabilityPercent + "%"
				: "faults off");
		builder.append(" | ");
		builder.append(hasCrashProcess()
				? "MTBF " + DECIMAL.format(mtbfSeconds) + " s, MTTR " + DECIMAL.format(mttrSeconds) + " s"
				: "no crashes");
		return builder.toString();
	}

	String toReportLine()
	{
		return String.format(
				Locale.US,
				"Node %d: %s, CPU %.2fx, memory %d MB, jobs %d, queue %d, delay %d ms, bandwidth %s, slowdown %s, faults %s, crashes %s",
				nodeIndex,
				role.getLabel(),
				cpuMultiplier,
				memoryLimitMb,
				maxConcurrentJobs,
				queueCapacity,
				networkDelayMs,
				formatBandwidth(bandwidthMbps),
				slowdownSimulationEnabled
						? DECIMAL.format(slowdownMultiplier) + "x" : "off",
				failureSimulationEnabled
						? failureProbabilityPercent + "%" : "off",
				hasCrashProcess()
						? "MTBF " + DECIMAL.format(mtbfSeconds) + " s / MTTR "
								+ DECIMAL.format(mttrSeconds) + " s"
						: "off");
	}
}
