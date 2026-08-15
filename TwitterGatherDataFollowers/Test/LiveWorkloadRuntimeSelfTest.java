package TwitterGatherDataFollowers.userRyersonU;

import java.util.ArrayList;
import java.util.List;

public final class LiveWorkloadRuntimeSelfTest
{
	private LiveWorkloadRuntimeSelfTest()
	{
	}

	public static void main(String[] args)
	{
		wrongRunIdCannotMutateActiveControls();
		missingQueueSnapshotDoesNotInventWaitingWork();
		inputBatchQueueTracksDispatchRetargetAndCompletion();
		runtimeClosedInputCanAdvanceWithRetainedRecords();
		phaseBoundaryPauseWaitsForResume();
		pauseBlocksFutureClaims();
		drainRequeuesActiveShardAndIgnoresStaleCompletion();
		targetJobQueueSupportsDrainAndStaleCompletionGuard();
		RecommendationSchedulerRegistry.restoreDefault(2, 4096);
		System.out.println("LiveWorkloadRuntimeSelfTest passed");
	}

	private static void wrongRunIdCannotMutateActiveControls()
	{
		String runId = RecommendationRunLifecycle.createRunId();
		beginControlledRun(runId, ControllerAgent.Doc2Vec, 8);
		LiveWorkloadAdjustmentRegistry.setPaused(
				runId + "-wrong",
				1,
				true);
		require(!LiveWorkloadAdjustmentRegistry.snapshot(runId).isPaused(1),
				"wrong run ID was allowed to pause the active run");
		LiveWorkloadAdjustmentRegistry.setPaused(runId, 1, true);
		require(LiveWorkloadAdjustmentRegistry.snapshot(runId).isPaused(1),
				"active run ID did not pause the node");
	}

	private static void missingQueueSnapshotDoesNotInventWaitingWork()
	{
		String runId = RecommendationRunLifecycle.createRunId();
		beginControlledRun(runId, ControllerAgent.COS_SIM, 40);
		RecommendationWorkQueueRegistry.QueueSnapshot snapshot =
				RecommendationWorkQueueRegistry.snapshotExperimentalCandidateQueue(
						runId,
						"Similarity",
						4,
						40);
		require(snapshot.getTotalShards() == 0
						&& snapshot.getWaitingCount() == 0
						&& snapshot.getEvents().isEmpty(),
				"missing live queue was displayed as unclaimed waiting work");
	}

	private static void inputBatchQueueTracksDispatchRetargetAndCompletion()
	{
		String runId = RecommendationRunLifecycle.createRunId();
		beginControlledRun(runId, ControllerAgent.COS_SIM, 4);
		RecommendationWorkQueueRegistry.beginInputBatchQueue(runId, 2, 3);
		RecommendationWorkQueueRegistry.QueueSnapshot snapshot =
				RecommendationWorkQueueRegistry.snapshotInputBatchQueue(
						runId,
						2);
		require(snapshot.getTotalShards() == 3
						&& snapshot.getWaitingCount() == 3,
				"input batch queue did not expose waiting batches");
		RecommendationWorkQueueRegistry.dispatchInputBatch(
				runId,
				1,
				2,
				3,
				1);
		snapshot = RecommendationWorkQueueRegistry.snapshotInputBatchQueue(
				runId,
				2);
		require(snapshot.getActiveCount() == 1
						&& snapshot.getWaitingCount() == 2,
				"input batch dispatch was not tracked as active work");
		RecommendationWorkQueueRegistry.dispatchInputBatch(
				runId,
				2,
				2,
				3,
				1);
		RecommendationWorkQueueRegistry.completeInputBatch(
				runId,
				1,
				2,
				3,
				1);
		snapshot = RecommendationWorkQueueRegistry.snapshotInputBatchQueue(
				runId,
				2);
		require(snapshot.getCompletedCount() == 0,
				"stale input batch completion was counted after retarget");
		RecommendationWorkQueueRegistry.completeInputBatch(
				runId,
				2,
				2,
				3,
				1);
		snapshot = RecommendationWorkQueueRegistry.snapshotInputBatchQueue(
				runId,
				2);
		require(snapshot.getCompletedCount() == 1
						&& snapshot.getActiveCount() == 0,
				"retargeted input batch completion was not counted");
	}

	private static void runtimeClosedInputCanAdvanceWithRetainedRecords()
	{
		String runId = RecommendationRunLifecycle.createRunId();
		RecommendationRunLifecycle lifecycle =
				new RecommendationRunLifecycle();
		lifecycle.admit(runId);
		lifecycle.markInputDeliveryComplete(runId);
		require(!lifecycle.markTextProcessingCompleteIfReady(5, 10),
				"legacy input close accepted a short node assignment");

		String runtimeRunId = RecommendationRunLifecycle.createRunId();
		RecommendationRunLifecycle runtimeLifecycle =
				new RecommendationRunLifecycle();
		runtimeLifecycle.admit(runtimeRunId);
		runtimeLifecycle.markInputDeliveryComplete(runtimeRunId);
		require(runtimeLifecycle.markTextProcessingCompleteIfReady(
						5,
						10,
						true),
				"runtime-controlled input close did not accept retained records");
		require(runtimeLifecycle.isTextProcessingComplete(),
				"runtime-controlled input close did not advance text processing");
	}

	private static void phaseBoundaryPauseWaitsForResume()
	{
		final String runId = RecommendationRunLifecycle.createRunId();
		beginControlledRun(runId, ControllerAgent.K_MEANS, 4);
		LiveWorkloadAdjustmentRegistry.setPaused(runId, 1, true);
		final boolean[] finished = new boolean[] { false };
		final RuntimeException[] failure = new RuntimeException[] { null };
		Thread waiter = new Thread(new Runnable()
		{
			public void run()
			{
				try
				{
					LiveWorkloadAdjustmentRegistry.waitUntilNodeEligible(
							runId,
							1,
							"self-test boundary");
					finished[0] = true;
				}
				catch (InterruptedException exception)
				{
					Thread.currentThread().interrupt();
					failure[0] = new RuntimeException(exception);
				}
			}
		});
		waiter.start();
		sleep(100L);
		require(!finished[0],
				"phase boundary did not wait while node was paused");
		LiveWorkloadAdjustmentRegistry.setPaused(runId, 1, false);
		try
		{
			waiter.join(2000L);
		}
		catch (InterruptedException exception)
		{
			Thread.currentThread().interrupt();
			throw new AssertionError("self-test interrupted");
		}
		if (failure[0] != null)
		{
			throw failure[0];
		}
		require(finished[0],
				"phase boundary did not resume after pause was cleared");
	}

	private static void pauseBlocksFutureClaims()
	{
		String runId = RecommendationRunLifecycle.createRunId();
		beginControlledRun(runId, ControllerAgent.Doc2Vec, 8);
		LiveWorkloadAdjustmentRegistry.setPaused(runId, 1, true);
		RecommendationWorkQueueRegistry.ShardClaim claim =
				RecommendationWorkQueueRegistry.claimDoc2VecCandidateShard(
						runId,
						1,
						2,
						8);
		require(!claim.hasShard() && claim.isDeferred(),
				"paused node claimed live queue work");
		LiveWorkloadAdjustmentRegistry.setPaused(runId, 1, false);
		claim = RecommendationWorkQueueRegistry.claimDoc2VecCandidateShard(
				runId,
				1,
				2,
				8);
		require(claim.hasShard(),
				"resumed node did not claim live queue work");
	}

	private static void drainRequeuesActiveShardAndIgnoresStaleCompletion()
	{
		String runId = RecommendationRunLifecycle.createRunId();
		beginControlledRun(runId, ControllerAgent.Doc2Vec, 8);
		RecommendationWorkQueueRegistry.ShardClaim nodeOneClaim =
				RecommendationWorkQueueRegistry.claimDoc2VecCandidateShard(
						runId,
						1,
						2,
						8);
		require(nodeOneClaim.hasShard(),
				"node 1 did not claim an initial shard");
		int shard = nodeOneClaim.getShardNumber();
		int moved = LiveWorkloadAdjustmentRegistry.drainNodeTo(
				runId,
				1,
				2,
				8);
		require(moved == 1,
				"drain did not requeue the active shard");
		require(!RecommendationWorkQueueRegistry.isActiveDoc2VecCandidateShard(
						runId,
						1,
						2,
						8,
						shard),
				"drained shard remained active on source node");
		RecommendationWorkQueueRegistry.ShardClaim nodeTwoClaim =
				RecommendationWorkQueueRegistry.claimDoc2VecCandidateShard(
						runId,
						2,
						2,
						8);
		require(nodeTwoClaim.hasShard()
						&& nodeTwoClaim.getShardNumber() == shard,
				"drained shard was not reclaimed by the target node");
		RecommendationWorkQueueRegistry.completeDoc2VecCandidateShard(
				runId,
				1,
				2,
				8,
				shard);
		RecommendationWorkQueueRegistry.QueueSnapshot snapshot =
				RecommendationWorkQueueRegistry.snapshotDoc2VecQueue(
						runId,
						2,
						8);
		require(snapshot.getCompletedCount() == 0,
				"stale source completion was counted after drain");
		RecommendationWorkQueueRegistry.completeDoc2VecCandidateShard(
				runId,
				2,
				2,
				8,
				shard);
		snapshot = RecommendationWorkQueueRegistry.snapshotDoc2VecQueue(
				runId,
				2,
				8);
		require(snapshot.getCompletedCount() == 1,
				"target completion was not counted after drain");
	}

	private static void targetJobQueueSupportsDrainAndStaleCompletionGuard()
	{
		String runId = RecommendationRunLifecycle.createRunId();
		beginControlledRun(runId, ControllerAgent.SVM, 4);
		List<String> targets = targetUsers(4);
		RecommendationWorkQueueRegistry.TargetClaim first =
				RecommendationWorkQueueRegistry.claimTargetUserJob(
						runId,
						"SVM",
						1,
						2,
						targets);
		require(first.hasTarget(),
				"SVM target job queue did not issue an initial claim");
		int job = first.getJobNumber();
		int moved = LiveWorkloadAdjustmentRegistry.drainNodeTo(
				runId,
				1,
				2,
				4);
		require(moved == 1,
				"drain did not requeue the active SVM target job");
		RecommendationWorkQueueRegistry.TargetClaim second =
				RecommendationWorkQueueRegistry.claimTargetUserJob(
						runId,
						"SVM",
						2,
						2,
						targets);
		require(second.hasTarget() && second.getJobNumber() == job,
				"SVM target job was not reclaimed by the drain target");
		RecommendationWorkQueueRegistry.completeTargetUserJob(
				runId,
				"SVM",
				1,
				2,
				targets.size(),
				job);
		RecommendationWorkQueueRegistry.QueueSnapshot snapshot =
				RecommendationWorkQueueRegistry.snapshotTargetUserQueue(
						runId,
						"SVM",
						2,
						targets.size());
		require(snapshot.getCompletedCount() == 0,
				"stale SVM target-job completion was counted");
		RecommendationWorkQueueRegistry.completeTargetUserJob(
				runId,
				"SVM",
				2,
				2,
				targets.size(),
				job);
		snapshot = RecommendationWorkQueueRegistry.snapshotTargetUserQueue(
				runId,
				"SVM",
				2,
				targets.size());
		require(snapshot.getCompletedCount() == 1,
				"SVM target-job completion was not counted for the target node");
		LiveWorkloadAdjustmentRegistry.setPaused(runId, 1, true);
		RecommendationWorkQueueRegistry.TargetClaim paused =
				RecommendationWorkQueueRegistry.claimTargetUserJob(
						runId,
						"SVM",
						1,
						2,
						targets);
		require(!paused.hasTarget() && paused.isDeferred(),
				"paused node claimed an SVM target job");
	}

	private static void beginControlledRun(
			String runId,
			int algorithm,
			int targetCount)
	{
		NodeCapacityRegistry.resetToDefaults(2, 4096);
		RecommendationSchedulerRegistry.apply(
				RecommendationSchedulerPlan.Mode.SCHEDULING_EXPERIMENT,
				SchedulerLabEngine.SchedulingPolicy.ROUND_ROBIN,
				2,
				4096,
				"live workload self-test");
		RecommendationSchedulerRegistry.beginRun(
				runId,
				2,
				4096,
				algorithm,
				targetCount);
	}

	private static List<String> targetUsers(int count)
	{
		ArrayList<String> targets = new ArrayList<String>();
		for (int index = 1; index <= count; index++)
		{
			targets.add("Target" + index);
		}
		return targets;
	}

	private static void sleep(long millis)
	{
		try
		{
			Thread.sleep(millis);
		}
		catch (InterruptedException exception)
		{
			Thread.currentThread().interrupt();
			throw new AssertionError("self-test sleep interrupted");
		}
	}

	private static void require(boolean condition, String message)
	{
		if (!condition)
		{
			throw new AssertionError(message);
		}
	}
}
