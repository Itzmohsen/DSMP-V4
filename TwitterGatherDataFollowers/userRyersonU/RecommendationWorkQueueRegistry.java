package TwitterGatherDataFollowers.userRyersonU;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class RecommendationWorkQueueRegistry
{
	static final class ShardClaim
	{
		private final int shardNumber;
		private final int remaining;
		private final boolean deferred;
		private final String reason;

		private ShardClaim(
				int shardNumber,
				int remaining,
				boolean deferred,
				String reason)
		{
			this.shardNumber = shardNumber;
			this.remaining = Math.max(0, remaining);
			this.deferred = deferred;
			this.reason = reason == null ? "" : reason;
		}

		static ShardClaim none(int remaining)
		{
			return new ShardClaim(0, remaining, false, "");
		}

		static ShardClaim deferred(int remaining, String reason)
		{
			return new ShardClaim(0, remaining, true, reason);
		}

		static ShardClaim claimed(int shardNumber, int remaining)
		{
			return new ShardClaim(shardNumber, remaining, false, "");
		}

		boolean hasShard()
		{
			return shardNumber > 0;
		}

		boolean isDeferred()
		{
			return deferred;
		}

		int getShardNumber()
		{
			return shardNumber;
		}

		int getRemaining()
		{
			return remaining;
		}

		String getReason()
		{
			return reason;
		}
	}

	static final class TargetClaim
	{
		private final int jobNumber;
		private final String targetUser;
		private final int remaining;
		private final boolean deferred;
		private final String reason;

		private TargetClaim(
				int jobNumber,
				String targetUser,
				int remaining,
				boolean deferred,
				String reason)
		{
			this.jobNumber = Math.max(0, jobNumber);
			this.targetUser = targetUser == null ? "" : targetUser;
			this.remaining = Math.max(0, remaining);
			this.deferred = deferred;
			this.reason = reason == null ? "" : reason;
		}

		static TargetClaim none(int remaining)
		{
			return new TargetClaim(0, "", remaining, false, "");
		}

		static TargetClaim deferred(int remaining, String reason)
		{
			return new TargetClaim(0, "", remaining, true, reason);
		}

		static TargetClaim claimed(int jobNumber, String targetUser, int remaining)
		{
			return new TargetClaim(jobNumber, targetUser, remaining, false, "");
		}

		boolean hasTarget()
		{
			return jobNumber > 0 && targetUser.length() > 0;
		}

		boolean isDeferred()
		{
			return deferred;
		}

		int getJobNumber()
		{
			return jobNumber;
		}

		String getTargetUser()
		{
			return targetUser;
		}

		int getRemaining()
		{
			return remaining;
		}

		String getReason()
		{
			return reason;
		}
	}

	static final class QueueEvent
	{
		private final long time;
		private final int nodeIndex;
		private final int shardNumber;
		private final String action;
		private final String detail;

		private QueueEvent(
				long time,
				int nodeIndex,
				int shardNumber,
				String action,
				String detail)
		{
			this.time = Math.max(0L, time);
			this.nodeIndex = Math.max(0, nodeIndex);
			this.shardNumber = Math.max(0, shardNumber);
			this.action = action == null ? "" : action;
			this.detail = detail == null ? "" : detail;
		}

		long getTime()
		{
			return time;
		}

		int getNodeIndex()
		{
			return nodeIndex;
		}

		int getShardNumber()
		{
			return shardNumber;
		}

		String getAction()
		{
			return action;
		}

		String getDetail()
		{
			return detail;
		}
	}

	static final class QueueSnapshot
	{
		private final String key;
		private final int totalShards;
		private final int waitingCount;
		private final int activeCount;
		private final int completedCount;
		private final int failedCount;
		private final Map<Integer,List<Integer>> activeByNode;
		private final Map<Integer,Integer> claimedByNode;
		private final Map<Integer,Integer> completedByNode;
		private final List<Integer> waitingPreview;
		private final List<Integer> completedPreview;
		private final List<QueueEvent> events;

		private QueueSnapshot(
				String key,
				int totalShards,
				int waitingCount,
				int activeCount,
				int completedCount,
				int failedCount,
				Map<Integer,List<Integer>> activeByNode,
				Map<Integer,Integer> claimedByNode,
				Map<Integer,Integer> completedByNode,
				List<Integer> waitingPreview,
				List<Integer> completedPreview,
				List<QueueEvent> events)
		{
			this.key = key == null ? "" : key;
			this.totalShards = Math.max(0, totalShards);
			this.waitingCount = Math.max(0, waitingCount);
			this.activeCount = Math.max(0, activeCount);
			this.completedCount = Math.max(0, completedCount);
			this.failedCount = Math.max(0, failedCount);
			this.activeByNode = immutableListMap(activeByNode);
			this.claimedByNode = Collections.unmodifiableMap(
					new LinkedHashMap<Integer,Integer>(claimedByNode));
			this.completedByNode = Collections.unmodifiableMap(
					new LinkedHashMap<Integer,Integer>(completedByNode));
			this.waitingPreview = Collections.unmodifiableList(
					new ArrayList<Integer>(waitingPreview));
			this.completedPreview = Collections.unmodifiableList(
					new ArrayList<Integer>(completedPreview));
			this.events = Collections.unmodifiableList(
					new ArrayList<QueueEvent>(events));
		}

		String getKey()
		{
			return key;
		}

		int getTotalShards()
		{
			return totalShards;
		}

		int getWaitingCount()
		{
			return waitingCount;
		}

		int getActiveCount()
		{
			return activeCount;
		}

		int getCompletedCount()
		{
			return completedCount;
		}

		int getFailedCount()
		{
			return failedCount;
		}

		Map<Integer,List<Integer>> getActiveByNode()
		{
			return activeByNode;
		}

		Map<Integer,Integer> getClaimedByNode()
		{
			return claimedByNode;
		}

		Map<Integer,Integer> getCompletedByNode()
		{
			return completedByNode;
		}

		List<Integer> getWaitingPreview()
		{
			return waitingPreview;
		}

		List<Integer> getCompletedPreview()
		{
			return completedPreview;
		}

		List<QueueEvent> getEvents()
		{
			return events;
		}
	}

	private static final Object LOCK = new Object();
	private static final int MAX_RETAINED_QUEUES = 12;
	private static final LinkedHashMap<String, Doc2VecShardQueue> DOC2VEC_QUEUES =
			new LinkedHashMap<String, Doc2VecShardQueue>();
	private static final LinkedHashMap<String, Doc2VecShardQueue> EXPERIMENTAL_CANDIDATE_QUEUES =
			new LinkedHashMap<String, Doc2VecShardQueue>();
	private static final LinkedHashMap<String, Doc2VecShardQueue> TARGET_JOB_QUEUES =
			new LinkedHashMap<String, Doc2VecShardQueue>();
	private static final LinkedHashMap<String, Doc2VecShardQueue> INPUT_BATCH_QUEUES =
			new LinkedHashMap<String, Doc2VecShardQueue>();

	private RecommendationWorkQueueRegistry()
	{
	}

	static ShardClaim claimDoc2VecCandidateShard(
			String runId,
			int nodeIndex,
			int physicalNodeCount,
			int virtualShardCount)
	{
		return claimCandidateShard(
				DOC2VEC_QUEUES,
				queueKey("doc2vec", runId, physicalNodeCount, virtualShardCount),
				"Doc2Vec",
				runId,
				nodeIndex,
				physicalNodeCount,
				virtualShardCount);
	}

	static ShardClaim claimExperimentalCandidateShard(
			String runId,
			String algorithmKey,
			int nodeIndex,
			int physicalNodeCount,
			int virtualShardCount)
	{
		return claimCandidateShard(
				EXPERIMENTAL_CANDIDATE_QUEUES,
				queueKey(
						"experiment-" + sanitizeKey(algorithmKey),
						runId,
						physicalNodeCount,
						virtualShardCount),
				"experimental " + sanitizeLabel(algorithmKey),
				runId,
				nodeIndex,
				physicalNodeCount,
				virtualShardCount);
	}

	static TargetClaim claimTargetUserJob(
			String runId,
			String algorithmKey,
			int nodeIndex,
			int physicalNodeCount,
			List<String> targets)
	{
		int targetCount = targets == null ? 0 : targets.size();
		if (targetCount <= 0)
		{
			return TargetClaim.none(0);
		}
		ShardClaim claim = claimCandidateShard(
				TARGET_JOB_QUEUES,
				queueKey(
						"target-" + sanitizeKey(algorithmKey),
						runId,
						physicalNodeCount,
						targetCount),
				sanitizeLabel(algorithmKey) + " target",
				runId,
				nodeIndex,
				physicalNodeCount,
				targetCount);
		if (claim.isDeferred())
		{
			return TargetClaim.deferred(claim.getRemaining(), claim.getReason());
		}
		if (!claim.hasShard())
		{
			return TargetClaim.none(claim.getRemaining());
		}
		String target = targetForJobNumber(targets, claim.getShardNumber());
		if (target.length() == 0)
		{
			return TargetClaim.none(claim.getRemaining());
		}
		return TargetClaim.claimed(
				claim.getShardNumber(),
				target,
				claim.getRemaining());
	}

	static void beginInputBatchQueue(
			String runId,
			int physicalNodeCount,
			int batchCount)
	{
		if (batchCount <= 0)
		{
			return;
		}
		String key = queueKey(
				"input-batch",
				runId,
				physicalNodeCount,
				batchCount);
		synchronized (LOCK)
		{
			if (!INPUT_BATCH_QUEUES.containsKey(key))
			{
				INPUT_BATCH_QUEUES.put(
						key,
						new Doc2VecShardQueue(batchCount));
				pruneLocked();
			}
		}
	}

	static void dispatchInputBatch(
			String runId,
			int nodeIndex,
			int physicalNodeCount,
			int batchCount,
			int batchNumber)
	{
		if (batchCount <= 0 || batchNumber <= 0)
		{
			return;
		}
		String key = queueKey(
				"input-batch",
				runId,
				physicalNodeCount,
				batchCount);
		synchronized (LOCK)
		{
			Doc2VecShardQueue queue = INPUT_BATCH_QUEUES.get(key);
			if (queue == null)
			{
				queue = new Doc2VecShardQueue(batchCount);
				INPUT_BATCH_QUEUES.put(key, queue);
				pruneLocked();
			}
			markSpecificWorkItemActive(
					queue,
					runId,
					Math.max(1, Math.min(nodeIndex, Math.max(1, physicalNodeCount))),
					batchNumber,
					"input batch");
		}
	}

	static void completeInputBatch(
			String runId,
			int nodeIndex,
			int physicalNodeCount,
			int batchCount,
			int batchNumber)
	{
		markCandidateShard(
				INPUT_BATCH_QUEUES,
				queueKey(
						"input-batch",
						runId,
						physicalNodeCount,
						batchCount),
				nodeIndex,
				batchNumber,
				true);
	}

	static void failInputBatch(
			String runId,
			int nodeIndex,
			int physicalNodeCount,
			int batchCount,
			int batchNumber)
	{
		markCandidateShard(
				INPUT_BATCH_QUEUES,
				queueKey(
						"input-batch",
						runId,
						physicalNodeCount,
						batchCount),
				nodeIndex,
				batchNumber,
				false);
	}

	static QueueSnapshot snapshotInputBatchQueue(
			String runId,
			int physicalNodeCount,
			int batchCount)
	{
		String key = queueKey(
				"input-batch",
				runId,
				physicalNodeCount,
				Math.max(1, batchCount));
		synchronized (LOCK)
		{
			Doc2VecShardQueue queue = INPUT_BATCH_QUEUES.get(key);
			if (queue == null)
			{
				return emptySnapshot(key, 0);
			}
			return queue.snapshot(key);
		}
	}

	static QueueSnapshot snapshotInputBatchQueue(
			String runId,
			int physicalNodeCount)
	{
		String safeRun = runId == null || runId.trim().length() == 0
				? "no-run" : runId.trim();
		String prefix = safeRun + "|input-batch|nodes="
				+ Math.max(1, physicalNodeCount) + "|";
		synchronized (LOCK)
		{
			String matchingKey = null;
			Doc2VecShardQueue matchingQueue = null;
			for (Map.Entry<String,Doc2VecShardQueue> entry
					: INPUT_BATCH_QUEUES.entrySet())
			{
				if (entry.getKey() != null
						&& entry.getKey().startsWith(prefix))
				{
					matchingKey = entry.getKey();
					matchingQueue = entry.getValue();
				}
			}
			if (matchingQueue == null)
			{
				return emptySnapshot(prefix + "shards=0", 0);
			}
			return matchingQueue.snapshot(matchingKey);
		}
	}

	private static ShardClaim claimCandidateShard(
			LinkedHashMap<String,Doc2VecShardQueue> queues,
			String key,
			String queueLabel,
			String runId,
			int nodeIndex,
			int physicalNodeCount,
			int virtualShardCount)
	{
		synchronized (LOCK)
		{
			int safeNodeIndex = Math.max(
					1,
					Math.min(nodeIndex, Math.max(1, physicalNodeCount)));
			Doc2VecShardQueue queue = queues.get(key);
			if (queue == null)
			{
				queue = new Doc2VecShardQueue(virtualShardCount);
				queues.put(key, queue);
				pruneLocked();
			}
			if (queue.unclaimed.isEmpty())
			{
				return ShardClaim.none(queue.unclaimed.size());
			}
			long now = System.currentTimeMillis();
			LiveWorkloadAdjustmentRegistry.Snapshot liveControls =
					LiveWorkloadAdjustmentRegistry.snapshot(runId);
			String deferReason = dynamicClaimDeferReason(
					queue,
					safeNodeIndex,
					physicalNodeCount,
					now,
					queueLabel,
					liveControls);
			if (deferReason != null)
			{
				queue.addDeferredEventIfNeeded(
						now,
						safeNodeIndex,
						deferReason);
				return ShardClaim.deferred(queue.unclaimed.size(), deferReason);
			}

			Integer shard = queue.unclaimed.pollFirst();
			Integer claims = queue.claimsByNode.get(safeNodeIndex);
			queue.claimsByNode.put(
					safeNodeIndex,
					Integer.valueOf(claims == null ? 1 : claims.intValue() + 1));
			queue.activeShardToNode.put(shard, Integer.valueOf(safeNodeIndex));
			queue.activeStartedAt.put(shard, Long.valueOf(now));
			queue.lastClaimAt = now;
			boolean redirected = LiveWorkloadAdjustmentRegistry.noteClaimedByNode(
					runId,
					safeNodeIndex);
			String liveSuffix = "";
			LiveWorkloadAdjustmentRegistry.Snapshot afterClaim =
					LiveWorkloadAdjustmentRegistry.snapshot(runId);
			if (redirected || afterClaim.hasActiveControls())
			{
				liveSuffix = " Live controls: "
						+ afterClaim.compactSummary();
			}
			queue.addEvent(new QueueEvent(
					now,
					safeNodeIndex,
					shard.intValue(),
					"claimed",
					"Node " + safeNodeIndex + " pulled shard "
							+ shard.intValue() + " from the queue; "
							+ queue.unclaimed.size() + " shard(s) remain."
							+ liveSuffix));
			return ShardClaim.claimed(shard.intValue(), queue.unclaimed.size());
		}
	}

	private static String dynamicClaimDeferReason(
			Doc2VecShardQueue queue,
			int requestedNodeIndex,
			int physicalNodeCount,
			long now,
			String queueLabel,
			LiveWorkloadAdjustmentRegistry.Snapshot liveControls)
	{
		RecommendationSchedulerPlan plan =
				RecommendationSchedulerRegistry.snapshot();
		if (!plan.isActive())
		{
			return null;
		}
		int safeNodes = Math.max(1, physicalNodeCount);
		int nodeIndex = Math.max(1, Math.min(requestedNodeIndex, safeNodes));
		if (liveControls != null)
		{
			LiveWorkloadAdjustmentRegistry.NodeControl control =
					liveControls.controlForNode(nodeIndex);
			if (control.isUnavailable() || control.isPaused())
			{
				return "Live workload control marks Node " + nodeIndex
						+ " as " + control.statusLabel()
						+ "; pending " + safeQueueLabel(queueLabel)
						+ " shards remain available for other nodes.";
			}
		}
		SchedulerLabEngine.SchedulingPolicy activePolicy =
				liveControls != null
						&& liveControls.getPolicyOverride() != null
						? liveControls.getPolicyOverride()
						: plan.getPolicy();
		boolean redirected = liveControls != null
				&& liveControls.getRedirectRemaining() > 0
				&& liveControls.isEligible(liveControls.getRedirectTargetNode());
		if (queue.unclaimed.size() <= 1 && !redirected)
		{
			return null;
		}
		int preferredNode = redirected
				? liveControls.getRedirectTargetNode()
				: preferredNodeForNextShard(
						queue,
						safeNodes,
						activePolicy,
						liveControls);
		if (preferredNode <= 0 || preferredNode == nodeIndex)
		{
			return null;
		}

		long lastProgressAt = queue.lastClaimAt > 0L
				? queue.lastClaimAt : queue.createdAt;
		if (redirected)
		{
			return "Live redirect is reserving the next "
					+ safeQueueLabel(queueLabel)
					+ " shard for Node " + preferredNode
					+ " before Node " + nodeIndex
					+ " claims more work.";
		}
		if (lastProgressAt <= 0L || now - lastProgressAt > 1500L)
		{
			return null;
		}
		return "Policy " + activePolicy.getLabel()
				+ " is reserving the next " + safeQueueLabel(queueLabel)
				+ " shard for Node "
				+ preferredNode + " before Node " + nodeIndex
				+ " claims more work.";
	}

	private static int preferredNodeForNextShard(
			Doc2VecShardQueue queue,
			int safeNodes,
			SchedulerLabEngine.SchedulingPolicy policy,
			LiveWorkloadAdjustmentRegistry.Snapshot liveControls)
	{
		SchedulerLabEngine.SchedulingPolicy safePolicy = policy == null
				? SchedulerLabEngine.SchedulingPolicy.CAPACITY_WEIGHTED : policy;
		if (safePolicy == SchedulerLabEngine.SchedulingPolicy.ROUND_ROBIN)
		{
			return firstEligibleNodeFrom(
					safeNodes,
					Math.floorMod(totalClaims(queue), safeNodes) + 1,
					liveControls);
		}
		if (safePolicy == SchedulerLabEngine.SchedulingPolicy.FASTEST_NODE_FIRST)
		{
			return fastestNode(safeNodes, WeightMode.CAPACITY, liveControls);
		}
		if (safePolicy == SchedulerLabEngine.SchedulingPolicy.STATIC_EQUAL_SPLIT
				|| safePolicy == SchedulerLabEngine.SchedulingPolicy.LEAST_LOADED_NODE
				|| safePolicy == SchedulerLabEngine.SchedulingPolicy.QUEUE_LENGTH)
		{
			return smallestClaimNode(queue, safeNodes, liveControls);
		}
		if (safePolicy == SchedulerLabEngine.SchedulingPolicy.MEMORY_AWARE_SAFE_WAVE)
		{
			return weightedClaimNode(queue, safeNodes, WeightMode.MEMORY, liveControls);
		}
		if (safePolicy == SchedulerLabEngine.SchedulingPolicy.FAILURE_AWARE_RESCHEDULING)
		{
			return weightedClaimNode(
					queue,
					safeNodes,
					WeightMode.RELIABILITY,
					liveControls);
		}
		return weightedClaimNode(queue, safeNodes, WeightMode.CAPACITY, liveControls);
	}

	private static int totalClaims(Doc2VecShardQueue queue)
	{
		int total = 0;
		for (Integer count : queue.claimsByNode.values())
		{
			if (count != null && count.intValue() > 0)
			{
				total += count.intValue();
			}
		}
		return total;
	}

	private static int claimCount(Doc2VecShardQueue queue, int nodeIndex)
	{
		Integer count = queue.claimsByNode.get(Integer.valueOf(nodeIndex));
		return count == null ? 0 : Math.max(0, count.intValue());
	}

	private static int smallestClaimNode(
			Doc2VecShardQueue queue,
			int safeNodes,
			LiveWorkloadAdjustmentRegistry.Snapshot liveControls)
	{
		int bestNode = 0;
		int bestCount = Integer.MAX_VALUE;
		for (int node = 1; node <= safeNodes; node++)
		{
			if (!isEligible(liveControls, node))
			{
				continue;
			}
			int count = claimCount(queue, node);
			if (count < bestCount)
			{
				bestCount = count;
				bestNode = node;
			}
		}
		return bestNode <= 0 ? firstEligibleNodeFrom(safeNodes, 1, liveControls)
				: bestNode;
	}

	private static int fastestNode(
			int safeNodes,
			WeightMode mode,
			LiveWorkloadAdjustmentRegistry.Snapshot liveControls)
	{
		int bestNode = 0;
		double bestWeight = -1.0;
		for (int node = 1; node <= safeNodes; node++)
		{
			if (!isEligible(liveControls, node))
			{
				continue;
			}
			double weight = policyWeight(
					NodeCapacityRegistry.profileForNode(node),
					mode,
					liveControls,
					node);
			if (weight > bestWeight)
			{
				bestWeight = weight;
				bestNode = node;
			}
		}
		return bestNode <= 0 ? firstEligibleNodeFrom(safeNodes, 1, liveControls)
				: bestNode;
	}

	private static int weightedClaimNode(
			Doc2VecShardQueue queue,
			int safeNodes,
			WeightMode mode,
			LiveWorkloadAdjustmentRegistry.Snapshot liveControls)
	{
		int bestNode = 0;
		double bestScore = Double.MAX_VALUE;
		int bestClaimCount = Integer.MAX_VALUE;
		for (int node = 1; node <= safeNodes; node++)
		{
			if (!isEligible(liveControls, node))
			{
				continue;
			}
			int count = claimCount(queue, node);
			NodeCapacityProfile profile =
					NodeCapacityRegistry.profileForNode(node);
			double weight = policyWeight(profile, mode, liveControls, node);
			double score = count / Math.max(0.05, weight);
			if (score < bestScore
					|| (Math.abs(score - bestScore) < 0.0000001
							&& count < bestClaimCount))
			{
				bestScore = score;
				bestNode = node;
				bestClaimCount = count;
			}
		}
		return bestNode <= 0 ? firstEligibleNodeFrom(safeNodes, 1, liveControls)
				: bestNode;
	}

	private static double policyWeight(
			NodeCapacityProfile profile,
			WeightMode mode,
			LiveWorkloadAdjustmentRegistry.Snapshot liveControls,
			int nodeIndex)
	{
		double baseWeight;
		if (profile == null)
		{
			baseWeight = 1.0;
		}
		else if (mode == WeightMode.MEMORY)
		{
			NodeCapacityProfile defaultProfile =
					NodeCapacityProfile.defaultProfile(
							profile.getNodeIndex(),
							Math.max(1, NodeCapacityRegistry.configuredNodeCount()),
							NodeCapacityRegistry.configuredHeapMb());
			double memoryRatio = profile.getMemoryLimitMb()
					/ (double)Math.max(1, defaultProfile.getMemoryLimitMb());
			baseWeight = clamp(memoryRatio, 0.05, 32.0);
		}
		else if (mode == WeightMode.RELIABILITY)
		{
			double reliability = 1.0;
			if (profile.isFailureSimulationEnabled())
			{
				reliability = Math.max(
						0.05,
						1.0 - profile.getFailureProbabilityPercent() / 100.0);
			}
			baseWeight = clamp(
					NodeCapacityScheduler.schedulingWeight(profile) * reliability,
					0.05,
					32.0);
		}
		else
		{
			baseWeight = NodeCapacityScheduler.schedulingWeight(profile);
		}
		return LiveWorkloadAdjustmentRegistry.adjustedWeight(
				liveControls,
				nodeIndex,
				baseWeight);
	}

	private static int firstEligibleNodeFrom(
			int safeNodes,
			int preferredStart,
			LiveWorkloadAdjustmentRegistry.Snapshot liveControls)
	{
		int safeCount = Math.max(1, safeNodes);
		int start = Math.max(1, Math.min(preferredStart, safeCount));
		for (int offset = 0; offset < safeCount; offset++)
		{
			int node = Math.floorMod(start - 1 + offset, safeCount) + 1;
			if (isEligible(liveControls, node))
			{
				return node;
			}
		}
		return 0;
	}

	private static boolean isEligible(
			LiveWorkloadAdjustmentRegistry.Snapshot liveControls,
			int nodeIndex)
	{
		return liveControls == null || liveControls.isEligible(nodeIndex);
	}

	private enum WeightMode
	{
		CAPACITY,
		MEMORY,
		RELIABILITY
	}

	static void completeDoc2VecCandidateShard(
			String runId,
			int nodeIndex,
			int physicalNodeCount,
			int virtualShardCount,
			int shardNumber)
	{
		markDoc2VecCandidateShard(
				runId,
				nodeIndex,
				physicalNodeCount,
				virtualShardCount,
				shardNumber,
				true);
	}

	static void completeExperimentalCandidateShard(
			String runId,
			String algorithmKey,
			int nodeIndex,
			int physicalNodeCount,
			int virtualShardCount,
			int shardNumber)
	{
		markCandidateShard(
				EXPERIMENTAL_CANDIDATE_QUEUES,
				queueKey(
						"experiment-" + sanitizeKey(algorithmKey),
						runId,
						physicalNodeCount,
						virtualShardCount),
				nodeIndex,
				shardNumber,
				true);
	}

	static void failDoc2VecCandidateShard(
			String runId,
			int nodeIndex,
			int physicalNodeCount,
			int virtualShardCount,
			int shardNumber)
	{
		markDoc2VecCandidateShard(
				runId,
				nodeIndex,
				physicalNodeCount,
				virtualShardCount,
				shardNumber,
				false);
	}

	static void failExperimentalCandidateShard(
			String runId,
			String algorithmKey,
			int nodeIndex,
			int physicalNodeCount,
			int virtualShardCount,
			int shardNumber)
	{
		markCandidateShard(
				EXPERIMENTAL_CANDIDATE_QUEUES,
				queueKey(
						"experiment-" + sanitizeKey(algorithmKey),
						runId,
						physicalNodeCount,
						virtualShardCount),
				nodeIndex,
				shardNumber,
				false);
	}

	static void completeTargetUserJob(
			String runId,
			String algorithmKey,
			int nodeIndex,
			int physicalNodeCount,
			int targetCount,
			int jobNumber)
	{
		markCandidateShard(
				TARGET_JOB_QUEUES,
				queueKey(
						"target-" + sanitizeKey(algorithmKey),
						runId,
						physicalNodeCount,
						targetCount),
				nodeIndex,
				jobNumber,
				true);
	}

	static void failTargetUserJob(
			String runId,
			String algorithmKey,
			int nodeIndex,
			int physicalNodeCount,
			int targetCount,
			int jobNumber)
	{
		markCandidateShard(
				TARGET_JOB_QUEUES,
				queueKey(
						"target-" + sanitizeKey(algorithmKey),
						runId,
						physicalNodeCount,
						targetCount),
				nodeIndex,
				jobNumber,
				false);
	}

	static int claimedDoc2VecShardCount(
			String runId,
			int nodeIndex,
			int physicalNodeCount,
			int virtualShardCount)
	{
		String key = queueKey(runId, physicalNodeCount, virtualShardCount);
		synchronized (LOCK)
		{
			Doc2VecShardQueue queue = DOC2VEC_QUEUES.get(key);
			if (queue == null)
			{
				return 0;
			}
			Integer count = queue.claimsByNode.get(nodeIndex);
			return count == null ? 0 : Math.max(0, count.intValue());
		}
	}

	static int claimedExperimentalShardCount(
			String runId,
			String algorithmKey,
			int nodeIndex,
			int physicalNodeCount,
			int virtualShardCount)
	{
		String key = queueKey(
				"experiment-" + sanitizeKey(algorithmKey),
				runId,
				physicalNodeCount,
				virtualShardCount);
		synchronized (LOCK)
		{
			Doc2VecShardQueue queue = EXPERIMENTAL_CANDIDATE_QUEUES.get(key);
			if (queue == null)
			{
				return 0;
			}
			Integer count = queue.claimsByNode.get(nodeIndex);
			return count == null ? 0 : Math.max(0, count.intValue());
		}
	}

	static QueueSnapshot snapshotDoc2VecQueue(
			String runId,
			int physicalNodeCount,
			int virtualShardCount)
	{
		String key = queueKey(runId, physicalNodeCount, virtualShardCount);
		synchronized (LOCK)
		{
			Doc2VecShardQueue queue = DOC2VEC_QUEUES.get(key);
			if (queue == null)
			{
				return emptySnapshot(key, 0);
			}
			return queue.snapshot(key);
		}
	}

	static QueueSnapshot snapshotExperimentalCandidateQueue(
			String runId,
			String algorithmKey,
			int physicalNodeCount,
			int virtualShardCount)
	{
		String key = queueKey(
				"experiment-" + sanitizeKey(algorithmKey),
				runId,
				physicalNodeCount,
				virtualShardCount);
		synchronized (LOCK)
		{
			Doc2VecShardQueue queue = EXPERIMENTAL_CANDIDATE_QUEUES.get(key);
			if (queue == null)
			{
				return emptySnapshot(key, 0);
			}
			return queue.snapshot(key);
		}
	}

	static QueueSnapshot snapshotTargetUserQueue(
			String runId,
			String algorithmKey,
			int physicalNodeCount,
			int targetCount)
	{
		String key = queueKey(
				"target-" + sanitizeKey(algorithmKey),
				runId,
				physicalNodeCount,
				targetCount);
		synchronized (LOCK)
		{
			Doc2VecShardQueue queue = TARGET_JOB_QUEUES.get(key);
			if (queue == null)
			{
				return emptySnapshot(key, 0);
			}
			return queue.snapshot(key);
		}
	}

	static boolean isActiveDoc2VecCandidateShard(
			String runId,
			int nodeIndex,
			int physicalNodeCount,
			int virtualShardCount,
			int shardNumber)
	{
		return isActiveCandidateShard(
				DOC2VEC_QUEUES,
				queueKey(runId, physicalNodeCount, virtualShardCount),
				nodeIndex,
				shardNumber);
	}

	static boolean isActiveExperimentalCandidateShard(
			String runId,
			String algorithmKey,
			int nodeIndex,
			int physicalNodeCount,
			int virtualShardCount,
			int shardNumber)
	{
		return isActiveCandidateShard(
				EXPERIMENTAL_CANDIDATE_QUEUES,
				queueKey(
						"experiment-" + sanitizeKey(algorithmKey),
						runId,
						physicalNodeCount,
						virtualShardCount),
				nodeIndex,
				shardNumber);
	}

	static boolean isActiveTargetUserJob(
			String runId,
			String algorithmKey,
			int nodeIndex,
			int physicalNodeCount,
			int targetCount,
			int jobNumber)
	{
		return isActiveCandidateShard(
				TARGET_JOB_QUEUES,
				queueKey(
						"target-" + sanitizeKey(algorithmKey),
						runId,
						physicalNodeCount,
						targetCount),
				nodeIndex,
				jobNumber);
	}

	static int requeueActiveWorkForNode(
			String runId,
			int nodeIndex,
			int preferredNode)
	{
		synchronized (LOCK)
		{
			int moved = 0;
			moved += requeueActiveWorkForNodeLocked(
					DOC2VEC_QUEUES,
					runId,
					nodeIndex,
					preferredNode);
			moved += requeueActiveWorkForNodeLocked(
					EXPERIMENTAL_CANDIDATE_QUEUES,
					runId,
					nodeIndex,
					preferredNode);
			moved += requeueActiveWorkForNodeLocked(
					TARGET_JOB_QUEUES,
					runId,
					nodeIndex,
					preferredNode);
			return moved;
		}
	}

	static QueueSnapshot emptyQueueSnapshot()
	{
		return emptySnapshot("empty-live-queue", 0);
	}

	private static void markDoc2VecCandidateShard(
			String runId,
			int nodeIndex,
			int physicalNodeCount,
			int virtualShardCount,
			int shardNumber,
			boolean completed)
	{
		if (shardNumber <= 0)
		{
			return;
		}
		String key = queueKey(runId, physicalNodeCount, virtualShardCount);
		markCandidateShard(
				DOC2VEC_QUEUES,
				key,
				nodeIndex,
				shardNumber,
				completed);
	}

	private static void markCandidateShard(
			LinkedHashMap<String,Doc2VecShardQueue> queues,
			String key,
			int nodeIndex,
			int shardNumber,
			boolean completed)
	{
		if (shardNumber <= 0)
		{
			return;
		}
		synchronized (LOCK)
		{
			Doc2VecShardQueue queue = queues.get(key);
			if (queue == null)
			{
				return;
			}
			Integer shard = Integer.valueOf(shardNumber);
			long now = System.currentTimeMillis();
			Integer activeNode = queue.activeShardToNode.get(shard);
			if (activeNode == null || activeNode.intValue() != nodeIndex)
			{
				queue.addEvent(new QueueEvent(
						now,
						nodeIndex,
						shardNumber,
						completed ? "stale complete" : "stale fail",
						"Node " + nodeIndex + " reported work "
								+ shardNumber
								+ " after it was no longer assigned to that node; "
								+ "the stale result was ignored."));
				return;
			}
			queue.activeShardToNode.remove(shard);
			queue.activeStartedAt.remove(shard);
			if (completed)
			{
				queue.completedShardToNode.put(shard, Integer.valueOf(nodeIndex));
				Integer count = queue.completedByNode.get(nodeIndex);
				queue.completedByNode.put(
						nodeIndex,
						Integer.valueOf(count == null ? 1 : count.intValue() + 1));
				queue.addEvent(new QueueEvent(
						now,
						nodeIndex,
						shardNumber,
						"completed",
						"Node " + nodeIndex + " finished shard "
								+ shardNumber + "."));
			}
			else
			{
				queue.failedShardToNode.put(shard, Integer.valueOf(nodeIndex));
				queue.addEvent(new QueueEvent(
						now,
						nodeIndex,
						shardNumber,
						"failed",
						"Node " + nodeIndex + " stopped while scoring shard "
								+ shardNumber + "."));
			}
		}
	}

	private static String queueKey(
			String runId,
			int physicalNodeCount,
			int virtualShardCount)
	{
		return queueKey("doc2vec", runId, physicalNodeCount, virtualShardCount);
	}

	private static String queueKey(
			String queueType,
			String runId,
			int physicalNodeCount,
			int virtualShardCount)
	{
		String safeRun = runId == null || runId.trim().length() == 0
				? "no-run" : runId.trim();
		String safeType = queueType == null || queueType.trim().length() == 0
				? "queue" : queueType.trim();
		return safeRun + "|" + safeType + "|nodes="
				+ Math.max(1, physicalNodeCount)
				+ "|shards=" + Math.max(1, virtualShardCount);
	}

	private static void pruneLocked()
	{
		pruneMapLocked(DOC2VEC_QUEUES);
		pruneMapLocked(EXPERIMENTAL_CANDIDATE_QUEUES);
		pruneMapLocked(TARGET_JOB_QUEUES);
		pruneMapLocked(INPUT_BATCH_QUEUES);
	}

	private static void pruneMapLocked(
			LinkedHashMap<String,Doc2VecShardQueue> queues)
	{
		while (queues.size() > MAX_RETAINED_QUEUES)
		{
			String eldest = null;
			for (String key : queues.keySet())
			{
				eldest = key;
				break;
			}
			if (eldest == null)
			{
				return;
			}
			queues.remove(eldest);
		}
	}

	private static String sanitizeKey(String value)
	{
		if (value == null || value.trim().length() == 0)
		{
			return "algorithm";
		}
		return value.trim().replaceAll("[^A-Za-z0-9._-]", "_");
	}

	private static String sanitizeLabel(String value)
	{
		if (value == null || value.trim().length() == 0)
		{
			return "algorithm";
		}
		return value.trim();
	}

	private static String safeQueueLabel(String value)
	{
		if (value == null || value.trim().length() == 0)
		{
			return "candidate";
		}
		return value.trim();
	}

	private static String targetForJobNumber(List<String> targets, int jobNumber)
	{
		if (targets == null || jobNumber <= 0 || jobNumber > targets.size())
		{
			return "";
		}
		String target = targets.get(jobNumber - 1);
		return target == null ? "" : target;
	}

	private static void markSpecificWorkItemActive(
			Doc2VecShardQueue queue,
			String runId,
			int nodeIndex,
			int workItemNumber,
			String workLabel)
	{
		if (queue == null || workItemNumber <= 0)
		{
			return;
		}
		Integer workItem = Integer.valueOf(workItemNumber);
		long now = System.currentTimeMillis();
		Integer activeNode = queue.activeShardToNode.get(workItem);
		if (activeNode != null)
		{
			if (activeNode.intValue() == nodeIndex)
			{
				return;
			}
			queue.activeShardToNode.put(workItem, Integer.valueOf(nodeIndex));
			queue.activeStartedAt.put(workItem, Long.valueOf(now));
			queue.addEvent(new QueueEvent(
					now,
					nodeIndex,
					workItemNumber,
					"reassigned",
					"Pending " + safeQueueLabel(workLabel)
							+ " " + workItemNumber
							+ " was reassigned from Node "
							+ activeNode.intValue()
							+ " to Node " + nodeIndex + "."));
			return;
		}
		if (queue.completedShardToNode.containsKey(workItem)
				|| queue.failedShardToNode.containsKey(workItem))
		{
			queue.addEvent(new QueueEvent(
					now,
					nodeIndex,
					workItemNumber,
					"stale dispatch",
					"Dispatch for finished " + safeQueueLabel(workLabel)
							+ " " + workItemNumber + " was ignored."));
			return;
		}
		if (!queue.unclaimed.remove(workItem))
		{
			return;
		}
		Integer nodeKey = Integer.valueOf(nodeIndex);
		Integer claims = queue.claimsByNode.get(nodeKey);
		queue.claimsByNode.put(
				nodeKey,
				Integer.valueOf(claims == null ? 1 : claims.intValue() + 1));
		queue.activeShardToNode.put(workItem, nodeKey);
		queue.activeStartedAt.put(workItem, Long.valueOf(now));
		queue.lastClaimAt = now;
		boolean redirected = LiveWorkloadAdjustmentRegistry.noteClaimedByNode(
				runId,
				nodeIndex);
		String liveSuffix = "";
		LiveWorkloadAdjustmentRegistry.Snapshot afterClaim =
				LiveWorkloadAdjustmentRegistry.snapshot(runId);
		if (redirected || afterClaim.hasActiveControls())
		{
			liveSuffix = " Live controls: "
					+ afterClaim.compactSummary();
		}
		queue.addEvent(new QueueEvent(
				now,
				nodeIndex,
				workItemNumber,
				"claimed",
				"Node " + nodeIndex + " received "
						+ safeQueueLabel(workLabel) + " "
						+ workItemNumber + "; "
						+ queue.unclaimed.size()
						+ " work item(s) remain."
						+ liveSuffix));
	}

	private static boolean isActiveCandidateShard(
			LinkedHashMap<String,Doc2VecShardQueue> queues,
			String key,
			int nodeIndex,
			int shardNumber)
	{
		if (shardNumber <= 0)
		{
			return false;
		}
		synchronized (LOCK)
		{
			Doc2VecShardQueue queue = queues.get(key);
			if (queue == null)
			{
				return false;
			}
			Integer owner = queue.activeShardToNode.get(
					Integer.valueOf(shardNumber));
			return owner != null && owner.intValue() == nodeIndex;
		}
	}

	private static int requeueActiveWorkForNodeLocked(
			LinkedHashMap<String,Doc2VecShardQueue> queues,
			String runId,
			int nodeIndex,
			int preferredNode)
	{
		String safeRun = runId == null || runId.trim().length() == 0
				? "" : runId.trim();
		if (safeRun.length() == 0)
		{
			return 0;
		}
		int moved = 0;
		for (Map.Entry<String,Doc2VecShardQueue> entry : queues.entrySet())
		{
			String key = entry.getKey();
			if (key == null || !key.startsWith(safeRun + "|"))
			{
				continue;
			}
			moved += entry.getValue().requeueActiveForNode(
					nodeIndex,
					preferredNode);
		}
		return moved;
	}

	private static QueueSnapshot emptySnapshot(String key, int virtualShardCount)
	{
		int safeTotal = Math.max(0, virtualShardCount);
		ArrayList<Integer> waiting = new ArrayList<Integer>();
		for (int shard = 1; shard <= safeTotal
				&& waiting.size() < 24; shard++)
		{
			waiting.add(Integer.valueOf(shard));
		}
		return new QueueSnapshot(
				key,
				safeTotal,
				safeTotal,
				0,
				0,
				0,
				new LinkedHashMap<Integer,List<Integer>>(),
				new LinkedHashMap<Integer,Integer>(),
				new LinkedHashMap<Integer,Integer>(),
				waiting,
				new ArrayList<Integer>(),
				new ArrayList<QueueEvent>());
	}

	private static Map<Integer,List<Integer>> immutableListMap(
			Map<Integer,List<Integer>> source)
	{
		LinkedHashMap<Integer,List<Integer>> copy =
				new LinkedHashMap<Integer,List<Integer>>();
		for (Map.Entry<Integer,List<Integer>> entry : source.entrySet())
		{
			copy.put(
					entry.getKey(),
					Collections.unmodifiableList(
							new ArrayList<Integer>(entry.getValue())));
		}
		return Collections.unmodifiableMap(copy);
	}

	private static void addToListMap(
			Map<Integer,List<Integer>> map,
			int nodeIndex,
			int shardNumber)
	{
		Integer key = Integer.valueOf(nodeIndex);
		List<Integer> values = map.get(key);
		if (values == null)
		{
			values = new ArrayList<Integer>();
			map.put(key, values);
		}
		values.add(Integer.valueOf(shardNumber));
	}

	private static List<Integer> preview(Iterable<Integer> values, int limit)
	{
		ArrayList<Integer> preview = new ArrayList<Integer>();
		for (Integer value : values)
		{
			if (value != null)
			{
				preview.add(value);
			}
			if (preview.size() >= limit)
			{
				break;
			}
		}
		return preview;
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

	private static final class Doc2VecShardQueue
	{
		private static final int MAX_EVENTS = 80;
		private static final long DEFER_EVENT_INTERVAL_MS = 750L;
		private final int totalShards;
		private final long createdAt;
		private long lastClaimAt;
		private final ArrayDeque<Integer> unclaimed =
				new ArrayDeque<Integer>();
		private final Map<Integer,Integer> claimsByNode =
				new LinkedHashMap<Integer,Integer>();
		private final Map<Integer,Integer> completedByNode =
				new LinkedHashMap<Integer,Integer>();
		private final Map<Integer,Integer> activeShardToNode =
				new LinkedHashMap<Integer,Integer>();
		private final Map<Integer,Long> activeStartedAt =
				new LinkedHashMap<Integer,Long>();
		private final Map<Integer,Integer> completedShardToNode =
				new LinkedHashMap<Integer,Integer>();
		private final Map<Integer,Integer> failedShardToNode =
				new LinkedHashMap<Integer,Integer>();
		private final Map<Integer,Long> deferredEventByNode =
				new LinkedHashMap<Integer,Long>();
		private final ArrayList<QueueEvent> events =
				new ArrayList<QueueEvent>();

		private Doc2VecShardQueue(int virtualShardCount)
		{
			int safeShards = Math.max(1, virtualShardCount);
			totalShards = safeShards;
			createdAt = System.currentTimeMillis();
			lastClaimAt = 0L;
			for (int shard = 1; shard <= safeShards; shard++)
			{
				unclaimed.add(Integer.valueOf(shard));
			}
		}

		private void addEvent(QueueEvent event)
		{
			events.add(event);
			while (events.size() > MAX_EVENTS)
			{
				events.remove(0);
			}
		}

		private void addDeferredEventIfNeeded(
				long now,
				int nodeIndex,
				String detail)
		{
			Integer key = Integer.valueOf(nodeIndex);
			Long last = deferredEventByNode.get(key);
			if (last != null
					&& now - last.longValue() < DEFER_EVENT_INTERVAL_MS)
			{
				return;
			}
			deferredEventByNode.put(key, Long.valueOf(now));
			addEvent(new QueueEvent(
					now,
					nodeIndex,
					0,
					"waiting",
					detail));
		}

		private QueueSnapshot snapshot(String key)
		{
			LinkedHashMap<Integer,List<Integer>> activeByNode =
					new LinkedHashMap<Integer,List<Integer>>();
			for (Map.Entry<Integer,Integer> entry : activeShardToNode.entrySet())
			{
				addToListMap(
						activeByNode,
						entry.getValue().intValue(),
						entry.getKey().intValue());
			}
			ArrayList<QueueEvent> eventCopy =
					new ArrayList<QueueEvent>(events);
			Collections.reverse(eventCopy);
			return new QueueSnapshot(
					key,
					totalShards,
					unclaimed.size(),
					activeShardToNode.size(),
					completedShardToNode.size(),
					failedShardToNode.size(),
					activeByNode,
					claimsByNode,
					completedByNode,
					preview(unclaimed, 24),
					preview(completedShardToNode.keySet(), 24),
					eventCopy);
		}

		private int requeueActiveForNode(int nodeIndex, int preferredNode)
		{
			ArrayList<Integer> toMove = new ArrayList<Integer>();
			for (Map.Entry<Integer,Integer> entry : activeShardToNode.entrySet())
			{
				Integer owner = entry.getValue();
				if (owner != null && owner.intValue() == nodeIndex)
				{
					toMove.add(entry.getKey());
				}
			}
			long now = System.currentTimeMillis();
			for (int index = toMove.size() - 1; index >= 0; index--)
			{
				Integer shard = toMove.get(index);
				activeShardToNode.remove(shard);
				activeStartedAt.remove(shard);
				if (!completedShardToNode.containsKey(shard)
						&& !failedShardToNode.containsKey(shard)
						&& !unclaimed.contains(shard))
				{
					unclaimed.addFirst(shard);
				}
				addEvent(new QueueEvent(
						now,
						nodeIndex,
						shard.intValue(),
						"requeued",
						"Node " + nodeIndex + " was drained; work "
								+ shard.intValue()
								+ " returned to the waiting queue"
								+ (preferredNode > 0
										? " with Node " + preferredNode
												+ " preferred next."
										: ".")));
			}
			return toMove.size();
		}
	}
}
