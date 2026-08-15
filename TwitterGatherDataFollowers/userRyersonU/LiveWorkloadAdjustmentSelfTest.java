package TwitterGatherDataFollowers.userRyersonU;

import java.util.ArrayList;

public final class LiveWorkloadAdjustmentSelfTest
{
	private LiveWorkloadAdjustmentSelfTest()
	{
	}

	public static void main(String[] args) throws Exception
	{
		int nodes = 3;
		int heapMb = 4096;
		int shards = 12;
		String runId = "live-adjustment-self-test-" + System.nanoTime();

		ArrayList<NodeCapacityProfile> profiles =
				new ArrayList<NodeCapacityProfile>();
		profiles.add(NodeCapacityProfile.rolePreset(
				1,
				NodeCapacityProfile.Role.STRONG,
				nodes,
				heapMb));
		profiles.add(NodeCapacityProfile.rolePreset(
				2,
				NodeCapacityProfile.Role.NORMAL,
				nodes,
				heapMb));
		profiles.add(NodeCapacityProfile.rolePreset(
				3,
				NodeCapacityProfile.Role.WEAK,
				nodes,
				heapMb));
		NodeCapacityRegistry.setProfiles(profiles, heapMb);
		RecommendationSchedulerRegistry.apply(
				RecommendationSchedulerPlan.Mode.SCHEDULING_EXPERIMENT,
				SchedulerLabEngine.SchedulingPolicy.CAPACITY_WEIGHTED,
				nodes,
				heapMb,
				"live adjustment self-test");
		RecommendationSchedulerRegistry.beginRun(
				runId,
				nodes,
				heapMb,
				ControllerAgent.Doc2Vec,
				1);

		LiveWorkloadAdjustmentRegistry.setPaused(runId, 1, true);
		RecommendationWorkQueueRegistry.ShardClaim pausedClaim =
				RecommendationWorkQueueRegistry.claimDoc2VecCandidateShard(
						runId,
						1,
						nodes,
						shards);
		assertTrue(
				pausedClaim.isDeferred() && !pausedClaim.hasShard(),
				"paused node must not claim a pending shard");

		LiveWorkloadAdjustmentRegistry.setPaused(runId, 1, false);
		LiveWorkloadAdjustmentRegistry.redirectPendingToNode(runId, 2, 2);
		RecommendationWorkQueueRegistry.ShardClaim wrongNode =
				RecommendationWorkQueueRegistry.claimDoc2VecCandidateShard(
						runId,
						3,
						nodes,
						shards);
		assertTrue(
				wrongNode.isDeferred() && !wrongNode.hasShard(),
				"redirect should reserve pending work for the selected node");

		RecommendationWorkQueueRegistry.ShardClaim redirectedOne =
				RecommendationWorkQueueRegistry.claimDoc2VecCandidateShard(
						runId,
						2,
						nodes,
						shards);
		assertTrue(redirectedOne.hasShard(),
				"redirect target should claim the first redirected shard");
		RecommendationWorkQueueRegistry.completeDoc2VecCandidateShard(
				runId,
				2,
				nodes,
				shards,
				redirectedOne.getShardNumber());

		RecommendationWorkQueueRegistry.ShardClaim redirectedTwo =
				RecommendationWorkQueueRegistry.claimDoc2VecCandidateShard(
						runId,
						2,
						nodes,
						shards);
		assertTrue(redirectedTwo.hasShard(),
				"redirect target should claim the second redirected shard");
		RecommendationWorkQueueRegistry.completeDoc2VecCandidateShard(
				runId,
				2,
				nodes,
				shards,
				redirectedTwo.getShardNumber());
		assertTrue(
				LiveWorkloadAdjustmentRegistry.snapshot(runId)
						.getRedirectRemaining() == 0,
				"redirect counter should be consumed after selected claims");

		LiveWorkloadAdjustmentRegistry.setPacingDelayMs(runId, 15);
		LiveWorkloadAdjustmentRegistry.Snapshot paced =
				LiveWorkloadAdjustmentRegistry.snapshot(runId);
		assertTrue(paced.getPacingDelayMs() == 15,
				"active pacing delay should be recorded");
		assertTrue(paced.hasActiveControls(),
				"pacing delay should count as a live control");
		LiveWorkloadAdjustmentRegistry.applyClaimPacingDelay(runId);

		LiveWorkloadAdjustmentRegistry.setUnavailable(runId, 2, true);
		RecommendationWorkQueueRegistry.ShardClaim unavailableClaim =
				RecommendationWorkQueueRegistry.claimDoc2VecCandidateShard(
						runId,
						2,
						nodes,
						shards);
		assertTrue(
				unavailableClaim.isDeferred() && !unavailableClaim.hasShard(),
				"unavailable node must not claim future shards");

		LiveWorkloadAdjustmentRegistry.setCapacityScale(runId, 3, 0.25);
		LiveWorkloadAdjustmentRegistry.setPolicyOverride(
				runId,
				SchedulerLabEngine.SchedulingPolicy.ROUND_ROBIN);
		LiveWorkloadAdjustmentRegistry.Snapshot active =
				LiveWorkloadAdjustmentRegistry.snapshot(runId);
		assertTrue(active.hasActiveControls(),
				"capacity and policy controls should be visible");

		LiveWorkloadAdjustmentRegistry.resetRunControls(runId);
		LiveWorkloadAdjustmentRegistry.Snapshot reset =
				LiveWorkloadAdjustmentRegistry.snapshot(runId);
		assertTrue(!reset.hasActiveControls(),
				"reset should clear all live workload overrides");
		assertTrue(reset.getPacingDelayMs() == 0,
				"reset should clear the active action window");

		String nextRunId = runId + "-next";
		LiveWorkloadAdjustmentRegistry.setNextRunPacingDelayMs(20);
		RecommendationSchedulerRegistry.beginRun(
				nextRunId,
				nodes,
				heapMb,
				ControllerAgent.Doc2Vec,
				1);
		assertTrue(
				LiveWorkloadAdjustmentRegistry.snapshot(nextRunId)
						.getPacingDelayMs() == 20,
				"next-run action window should apply when the next run begins");
		LiveWorkloadAdjustmentRegistry.setNextRunPacingDelayMs(0);
		LiveWorkloadAdjustmentRegistry.resetRunControls(nextRunId);

		RecommendationSchedulerRegistry.restoreDefault(nodes, heapMb);
		System.out.println("LiveWorkloadAdjustmentSelfTest passed");
	}

	private static void assertTrue(boolean condition, String message)
	{
		if (!condition)
		{
			throw new AssertionError(message);
		}
	}
}
