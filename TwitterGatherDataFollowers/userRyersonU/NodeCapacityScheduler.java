package TwitterGatherDataFollowers.userRyersonU;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class NodeCapacityScheduler
{
	interface CancellationCheck
	{
		void throwIfCancelled() throws InterruptedException;
	}

	private NodeCapacityScheduler()
	{
	}

	static int chooseNodeByCapacity(int[] currentLoads, int nodeCount)
	{
		if (currentLoads == null || currentLoads.length == 0)
		{
			return 0;
		}
		int limit = Math.min(currentLoads.length, Math.max(1, nodeCount));
		long[] loads = new long[limit];
		for (int index = 0; index < limit; index++)
		{
			loads[index] = currentLoads[index];
		}
		return chooseNodeByActivePolicy(loads, limit);
	}

	static int chooseNodeByCapacity(long[] currentLoads, int nodeCount)
	{
		if (currentLoads == null || currentLoads.length == 0)
		{
			return 0;
		}
		int limit = Math.min(currentLoads.length, Math.max(1, nodeCount));
		return chooseNodeByActivePolicy(currentLoads, limit);
	}

	static List<List<String>> assignTargetJobs(
			List<String> targets,
			int workerCount)
	{
		ArrayList<List<String>> assignments = new ArrayList<List<String>>();
		int safeWorkers = Math.max(1, workerCount);
		for (int worker = 0; worker < safeWorkers; worker++)
		{
			assignments.add(new ArrayList<String>());
		}
		if (targets == null || targets.isEmpty())
		{
			return assignments;
		}

		long[] loads = new long[safeWorkers];
		for (String target : targets)
		{
			int nodeIndex = chooseNodeByCapacity(loads, safeWorkers);
			assignments.get(nodeIndex).add(target);
			loads[nodeIndex] = saturatingAdd(loads[nodeIndex], 1L);
		}
		return assignments;
	}

	static List<List<String>> assignTargetJobsDeterministic(
			List<String> targets,
			int workerCount)
	{
		ArrayList<List<String>> assignments = new ArrayList<List<String>>();
		int safeWorkers = Math.max(1, workerCount);
		for (int worker = 0; worker < safeWorkers; worker++)
		{
			assignments.add(new ArrayList<String>());
		}
		if (targets == null || targets.isEmpty())
		{
			return assignments;
		}

		RecommendationSchedulerPlan plan =
				RecommendationSchedulerRegistry.snapshot();
		SchedulerLabEngine.SchedulingPolicy policy =
				plan != null && plan.isActive()
				? plan.getPolicy()
				: SchedulerLabEngine.SchedulingPolicy.CAPACITY_WEIGHTED;
		long[] loads = new long[safeWorkers];
		int sequence = 0;
		for (String target : targets)
		{
			int nodeIndex = chooseNodeByPolicyForPlan(
					loads,
					safeWorkers,
					policy,
					sequence);
			assignments.get(nodeIndex).add(target);
			loads[nodeIndex] = saturatingAdd(loads[nodeIndex], 1L);
			sequence++;
		}
		return assignments;
	}

	static String assignmentDetail(
			int workerIndex,
			int assignedTargets)
	{
		NodeCapacityProfile profile =
				NodeCapacityRegistry.profileForNode(workerIndex);
		return profile.shortLabel()
				+ " | assigned " + assignedTargets + " target user"
				+ (assignedTargets == 1 ? "" : "s")
				+ " | weight "
				+ String.format(Locale.US, "%.2f", schedulingWeight(profile));
	}

	static int virtualCandidateShardCount(
			int physicalNodeCount,
			int targetCount)
	{
		int safeNodes = Math.max(1, physicalNodeCount);
		if (safeNodes == 1)
		{
			return 1;
		}
		RecommendationSchedulerPlan plan =
				RecommendationSchedulerRegistry.snapshot();
		if (!plan.isActive())
		{
			return safeNodes;
		}

		int granularity = safeNodes * 4;
		if (targetCount > safeNodes)
		{
			granularity = Math.max(granularity, targetCount / 12);
		}
		return Math.max(safeNodes, Math.min(64, granularity));
	}

	static List<Integer> assignedVirtualCandidateShards(
			int physicalNodeIndex,
			int physicalNodeCount,
			int virtualShardCount)
	{
		int safeNodes = Math.max(1, physicalNodeCount);
		int safeShards = Math.max(safeNodes, virtualShardCount);
		int nodeIndex = Math.max(1, Math.min(physicalNodeIndex, safeNodes));
		RecommendationSchedulerPlan plan =
				RecommendationSchedulerRegistry.snapshot();
		ArrayList<Integer> assigned = new ArrayList<Integer>();
		if (!plan.isActive())
		{
			if (nodeIndex <= safeShards)
			{
				assigned.add(nodeIndex);
			}
			return assigned;
		}

		ArrayList<List<Integer>> assignments = new ArrayList<List<Integer>>();
		for (int worker = 0; worker < safeNodes; worker++)
		{
			assignments.add(new ArrayList<Integer>());
		}
		long[] loads = new long[safeNodes];
		for (int shard = 1; shard <= safeShards; shard++)
		{
			int chosen = chooseNodeByCapacity(loads, safeNodes);
			assignments.get(chosen).add(shard);
			loads[chosen] = saturatingAdd(loads[chosen], 1L);
		}
		return new ArrayList<Integer>(assignments.get(nodeIndex - 1));
	}

	static String virtualCandidateShardDetail(
			int physicalNodeIndex,
			int physicalNodeCount,
			List<Integer> assignedShards,
			int virtualShardCount)
	{
		NodeCapacityProfile profile =
				NodeCapacityRegistry.profileForNode(physicalNodeIndex);
		int assignedCount = assignedShards == null ? 0 : assignedShards.size();
		String shardText;
		if (assignedCount == 0)
		{
			shardText = "no virtual candidate shards";
		}
		else if (assignedCount <= 6)
		{
			shardText = "shards " + assignedShards.toString();
		}
		else
		{
			shardText = assignedCount + " of " + virtualShardCount
					+ " virtual candidate shards";
		}
		return "Node " + physicalNodeIndex + " "
				+ profile.shortLabel()
				+ " | " + shardText
				+ " | policy "
				+ RecommendationSchedulerRegistry.snapshot().getPolicy().getLabel();
	}

	static void applyPerJobSimulation(
			NodeCapacityProfile profile,
			String jobKey,
			int workerIndex,
			CancellationCheck cancellationCheck) throws InterruptedException
	{
		if (profile == null)
		{
			return;
		}
		if (profile.isFailureSimulationEnabled()
				&& profile.getFailureProbabilityPercent() > 0
				&& shouldFail(profile, jobKey, workerIndex))
		{
			throw new IllegalStateException(
					"Node " + workerIndex
					+ " simulated failure for target " + jobKey
					+ " (" + profile.getFailureProbabilityPercent() + "% profile).");
		}
		long delayMs = perJobDelayMillis(profile);
		if (delayMs > 0L)
		{
			sleepWithCancellation(delayMs, cancellationCheck);
		}
	}

	static void applyPerJobDelayOnly(
			NodeCapacityProfile profile,
			CancellationCheck cancellationCheck) throws InterruptedException
	{
		long delayMs = perJobDelayMillis(profile);
		if (delayMs > 0L)
		{
			sleepWithCancellation(delayMs, cancellationCheck);
		}
	}

	static long perJobDelayMillis(NodeCapacityProfile profile)
	{
		if (profile == null)
		{
			return 0L;
		}
		long delay = Math.max(0, profile.getNetworkDelayMs());
		if (profile.isSlowdownSimulationEnabled())
		{
			double multiplier = Math.max(1.0, profile.getSlowdownMultiplier());
			delay += Math.min(5000L, Math.round((multiplier - 1.0) * 100.0));
		}
		return delay;
	}

	static double schedulingWeight(NodeCapacityProfile profile)
	{
		if (profile == null)
		{
			return 1.0;
		}
		NodeCapacityProfile defaultProfile =
				NodeCapacityProfile.defaultProfile(
						profile.getNodeIndex(),
						Math.max(1, NodeCapacityRegistry.configuredNodeCount()),
						NodeCapacityRegistry.configuredHeapMb());
		double weight = clamp(profile.getCpuMultiplier(), 0.10, 8.00);

		double memoryRatio = profile.getMemoryLimitMb()
				/ (double)Math.max(1, defaultProfile.getMemoryLimitMb());
		weight *= (0.80 + 0.20 * clamp(memoryRatio, 0.25, 3.00));

		double queueRatio = profile.getQueueCapacity() / 1000.0;
		weight *= (0.85 + 0.15 * clamp(queueRatio, 0.10, 3.00));

		weight *= Math.sqrt(Math.max(1, profile.getMaxConcurrentJobs()));

		if (profile.isSlowdownSimulationEnabled())
		{
			weight /= Math.max(1.0, profile.getSlowdownMultiplier());
		}
		if (profile.getNetworkDelayMs() > 0)
		{
			weight /= (1.0 + Math.min(10.0, profile.getNetworkDelayMs() / 1000.0));
		}
		if (profile.isFailureSimulationEnabled())
		{
			weight *= Math.max(
					0.05,
					1.0 - profile.getFailureProbabilityPercent() / 100.0);
		}
		return clamp(weight, 0.05, 32.0);
	}

	private static double normalizedLoadScore(
			long currentLoad,
			NodeCapacityProfile profile)
	{
		double weight = schedulingWeight(profile);
		return currentLoad / Math.max(0.05, weight);
	}

	private static int chooseNodeByActivePolicy(long[] currentLoads, int limit)
	{
		RecommendationSchedulerPlan plan =
				RecommendationSchedulerRegistry.snapshot();
		SchedulerLabEngine.SchedulingPolicy policy = plan.isActive()
				? plan.getPolicy()
				: SchedulerLabEngine.SchedulingPolicy.CAPACITY_WEIGHTED;
		switch (policy)
		{
			case ROUND_ROBIN:
				return RecommendationSchedulerRegistry.nextRoundRobinNode(limit);
			case STATIC_EQUAL_SPLIT:
			case LEAST_LOADED_NODE:
			case QUEUE_LENGTH:
				return chooseSmallestRawLoad(currentLoads, limit);
			case FASTEST_NODE_FIRST:
				return chooseFastestEligibleNode(currentLoads, limit);
			case MEMORY_AWARE_SAFE_WAVE:
				return chooseBestWeightedNode(
						currentLoads,
						limit,
						WeightMode.MEMORY);
			case FAILURE_AWARE_RESCHEDULING:
				return chooseBestWeightedNode(
						currentLoads,
						limit,
						WeightMode.RELIABILITY);
			case WEIGHTED_ROUND_ROBIN:
			case CAPACITY_WEIGHTED:
			case WORK_STEALING:
			case DEADLINE_PRIORITY:
			default:
				return chooseBestWeightedNode(
						currentLoads,
						limit,
					WeightMode.CAPACITY);
		}
	}

	private static int chooseNodeByPolicyForPlan(
			long[] currentLoads,
			int limit,
			SchedulerLabEngine.SchedulingPolicy policy,
			int sequence)
	{
		SchedulerLabEngine.SchedulingPolicy safePolicy = policy == null
				? SchedulerLabEngine.SchedulingPolicy.CAPACITY_WEIGHTED
				: policy;
		switch (safePolicy)
		{
			case ROUND_ROBIN:
				return Math.floorMod(sequence, Math.max(1, limit));
			case STATIC_EQUAL_SPLIT:
			case LEAST_LOADED_NODE:
			case QUEUE_LENGTH:
				return chooseSmallestRawLoad(currentLoads, limit);
			case FASTEST_NODE_FIRST:
				return chooseFastestEligibleNode(currentLoads, limit);
			case MEMORY_AWARE_SAFE_WAVE:
				return chooseBestWeightedNode(
						currentLoads,
						limit,
						WeightMode.MEMORY);
			case FAILURE_AWARE_RESCHEDULING:
				return chooseBestWeightedNode(
						currentLoads,
						limit,
						WeightMode.RELIABILITY);
			case WEIGHTED_ROUND_ROBIN:
			case CAPACITY_WEIGHTED:
			case WORK_STEALING:
			case DEADLINE_PRIORITY:
			default:
				return chooseBestWeightedNode(
						currentLoads,
						limit,
						WeightMode.CAPACITY);
		}
	}

	private enum WeightMode
	{
		CAPACITY,
		MEMORY,
		RELIABILITY
	}

	private static int chooseSmallestRawLoad(long[] currentLoads, int limit)
	{
		int bestIndex = 0;
		long bestLoad = currentLoads[0];
		for (int index = 1; index < limit; index++)
		{
			if (currentLoads[index] < bestLoad)
			{
				bestLoad = currentLoads[index];
				bestIndex = index;
			}
		}
		return bestIndex;
	}

	private static int chooseFastestEligibleNode(long[] currentLoads, int limit)
	{
		int bestIndex = 0;
		double bestWeight = -1.0;
		for (int index = 0; index < limit; index++)
		{
			NodeCapacityProfile profile =
					NodeCapacityRegistry.profileForNode(index + 1);
			double weight = schedulingWeight(profile);
			if (weight > bestWeight
					|| (Math.abs(weight - bestWeight) < 0.0000001
							&& currentLoads[index] < currentLoads[bestIndex]))
			{
				bestWeight = weight;
				bestIndex = index;
			}
		}
		return bestIndex;
	}

	private static int chooseBestWeightedNode(
			long[] currentLoads,
			int limit,
			WeightMode mode)
	{
		double bestScore = Double.MAX_VALUE;
		int bestIndex = 0;
		for (int index = 0; index < limit; index++)
		{
			NodeCapacityProfile profile =
					NodeCapacityRegistry.profileForNode(index + 1);
			double score = currentLoads[index]
					/ Math.max(0.05, policyWeight(profile, mode));
			if (score < bestScore
					|| (Math.abs(score - bestScore) < 0.0000001
							&& currentLoads[index] < currentLoads[bestIndex]))
			{
				bestScore = score;
				bestIndex = index;
			}
		}
		return bestIndex;
	}

	private static double policyWeight(
			NodeCapacityProfile profile,
			WeightMode mode)
	{
		if (profile == null)
		{
			return 1.0;
		}
		if (mode == WeightMode.MEMORY)
		{
			NodeCapacityProfile defaultProfile =
					NodeCapacityProfile.defaultProfile(
							profile.getNodeIndex(),
							Math.max(1, NodeCapacityRegistry.configuredNodeCount()),
							NodeCapacityRegistry.configuredHeapMb());
			double memoryRatio = profile.getMemoryLimitMb()
					/ (double)Math.max(1, defaultProfile.getMemoryLimitMb());
			return clamp(memoryRatio, 0.05, 32.0);
		}
		if (mode == WeightMode.RELIABILITY)
		{
			double reliability = 1.0;
			if (profile.isFailureSimulationEnabled())
			{
				reliability = Math.max(
						0.05,
						1.0 - profile.getFailureProbabilityPercent() / 100.0);
			}
			return clamp(schedulingWeight(profile) * reliability, 0.05, 32.0);
		}
		return schedulingWeight(profile);
	}

	private static boolean shouldFail(
			NodeCapacityProfile profile,
			String jobKey,
			int workerIndex)
	{
		String key = String.valueOf(workerIndex)
				+ "|" + profile.getNodeIndex()
				+ "|" + String.valueOf(jobKey);
		int bucket = Math.floorMod(key.hashCode(), 100);
		return bucket < profile.getFailureProbabilityPercent();
	}

	private static void sleepWithCancellation(
			long delayMs,
			CancellationCheck cancellationCheck) throws InterruptedException
	{
		long remaining = delayMs;
		while (remaining > 0L)
		{
			if (cancellationCheck != null)
			{
				cancellationCheck.throwIfCancelled();
			}
			long slice = Math.min(remaining, 100L);
			Thread.sleep(slice);
			remaining -= slice;
		}
		if (cancellationCheck != null)
		{
			cancellationCheck.throwIfCancelled();
		}
	}

	private static long saturatingAdd(long first, long second)
	{
		if (Long.MAX_VALUE - first < second)
		{
			return Long.MAX_VALUE;
		}
		return first + second;
	}

	private static double clamp(
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
}
