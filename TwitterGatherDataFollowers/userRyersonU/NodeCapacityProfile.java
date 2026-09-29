package TwitterGatherDataFollowers.userRyersonU;

import java.text.DecimalFormat;
import java.util.Locale;

/**
 * One node's capacity contract, as entered by the user.
 *
 * Every field is physically emulated in scheduled runs (see {@link NodeSpec}):
 * the CPU multiplier sets relative speed (slower nodes' measured work is
 * dilated), max concurrent jobs sets worker lanes, the memory limit is an
 * admission budget, queue capacity is the most units the node may hold at once
 * (running plus prefetched), network delay is the one-way latency of the
 * node's link, slowdown divides speed further, and failure probability loses
 * that fraction of unit executions in fault-injection runs.
 */
final class NodeCapacityProfile
{
	enum Role
	{
		NORMAL("Normal", "Reference speed, one lane"),
		WEAK("Weak", "Half speed, half memory, 25 ms link"),
		STRONG("Strong", "Double speed, two lanes, double memory"),
		MEMORY_HEAVY("Memory-heavy", "Reference speed, double memory"),
		UNRELIABLE("Unreliable", "0.75 speed, 1.5x slowdown, 100 ms link, 5% failures");

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

	static final double MIN_CPU_MULTIPLIER = 0.10;
	static final double MAX_CPU_MULTIPLIER = 8.00;
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
					nodeIndex,
					role,
					0.50,
					Math.max(MIN_MEMORY_MB, baseMemory / 2),
					1,
					25,
					1,
					false,
					1.0,
					false,
					0);
		}
		if (role == Role.STRONG)
		{
			return new NodeCapacityProfile(
					nodeIndex,
					role,
					2.00,
					Math.min(MAX_MEMORY_MB, baseMemory * 2),
					2,
					0,
					2,
					false,
					1.0,
					false,
					0);
		}
		if (role == Role.MEMORY_HEAVY)
		{
			return new NodeCapacityProfile(
					nodeIndex,
					role,
					1.00,
					Math.min(MAX_MEMORY_MB, baseMemory * 2),
					1,
					0,
					1,
					false,
					1.0,
					false,
					0);
		}
		if (role == Role.UNRELIABLE)
		{
			return new NodeCapacityProfile(
					nodeIndex,
					role,
					0.75,
					baseMemory,
					1,
					100,
					1,
					true,
					1.50,
					true,
					5);
		}
		return new NodeCapacityProfile(
				nodeIndex,
				Role.NORMAL,
				1.00,
				baseMemory,
				1,
				0,
				1,
				false,
				1.0,
				false,
				0);
	}

	/** The same profile for a different node index. */
	NodeCapacityProfile withNodeIndex(int index)
	{
		return new NodeCapacityProfile(
				index,
				role,
				cpuMultiplier,
				memoryLimitMb,
				maxConcurrentJobs,
				networkDelayMs,
				queueCapacity,
				slowdownSimulationEnabled,
				slowdownMultiplier,
				failureSimulationEnabled,
				failureProbabilityPercent);
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

	String simulationSummary()
	{
		StringBuilder builder = new StringBuilder();
		builder.append(networkDelayMs > 0
				? networkDelayMs + " ms message delay"
				: "no message delay");
		builder.append(" | ");
		builder.append(slowdownSimulationEnabled
				? "slowdown " + DECIMAL.format(slowdownMultiplier) + "x"
				: "slowdown off");
		builder.append(" | ");
		builder.append(failureSimulationEnabled
				? "failure " + failureProbabilityPercent + "%"
				: "failure off");
		return builder.toString();
	}

	String toReportLine()
	{
		return String.format(
				Locale.US,
				"Node %d: %s, CPU %.2fx, memory %d MB, jobs %d, queue %d, delay %d ms, slowdown %s, failure %s",
				nodeIndex,
				role.getLabel(),
				cpuMultiplier,
				memoryLimitMb,
				maxConcurrentJobs,
				queueCapacity,
				networkDelayMs,
				slowdownSimulationEnabled
						? DECIMAL.format(slowdownMultiplier) + "x" : "off",
				failureSimulationEnabled
						? failureProbabilityPercent + "%" : "off");
	}
}
