package TwitterGatherDataFollowers.userRyersonU;

import java.util.ArrayList;

/**
 * Operator controls on a live run act on its open work phases: a paused node
 * claims nothing, a redirect moves not-started units to a node without
 * blocking anyone else, an unavailable node takes no new work, and pacing,
 * capacity and policy controls are visible until reset.
 */
public final class LiveWorkloadAdjustmentSelfTest
{
	private LiveWorkloadAdjustmentSelfTest()
	{
	}

	public static void main(String[] args) throws Exception
	{
		int nodes = 3;
		int heapMb = 4096;
		String runId = "live-adjustment-self-test-" + System.nanoTime();

		ArrayList<NodeCapacityProfile> profiles = new ArrayList<NodeCapacityProfile>();
		profiles.add(NodeCapacityProfile.rolePreset(1, NodeCapacityProfile.Role.STRONG, nodes, heapMb));
		profiles.add(NodeCapacityProfile.rolePreset(2, NodeCapacityProfile.Role.NORMAL, nodes, heapMb));
		profiles.add(NodeCapacityProfile.rolePreset(3, NodeCapacityProfile.Role.WEAK, nodes, heapMb));
		NodeCapacityRegistry.setProfiles(profiles, heapMb);
		RecommendationSchedulerRegistry.apply(
				RecommendationSchedulerPlan.Mode.SCORING_SAFE,
				SchedulerLabEngine.SchedulingPolicy.QUEUE_LENGTH,
				nodes,
				heapMb,
				"live adjustment self-test");
		SchedulingRunContext context = RecommendationSchedulerRegistry.beginRun(
				runId, nodes, heapMb, ControllerAgent.Doc2Vec, 1);
		WorkPhase phase = context.openPhase("doc2vec-shards", "self-test shards",
				new SchedulingRunContext.UnitSupplier()
				{
					public java.util.List<WorkUnitSpec> units()
					{
						ArrayList<WorkUnitSpec> units = new ArrayList<WorkUnitSpec>();
						for (int id = 1; id <= 12; id++)
						{
							units.add(new WorkUnitSpec(id, "shard-" + id, 1.0));
						}
						return units;
					}
				});
		WorkDispatcher dispatcher = phase.getDispatcher();

		require(LiveWorkloadAdjustmentRegistry.setPaused(runId, 1, true), "pause was refused");
		require(dispatcher.request(1, 0, phase.now()) == null,
				"a paused node must not claim a pending unit");
		require(LiveWorkloadAdjustmentRegistry.setPaused(runId, 1, false), "resume was refused");
		WorkDispatcher.Assignment first = dispatcher.request(1, 0, phase.now());
		require(first != null, "a resumed node must claim work again");
		require(phase.complete(first) == WorkDispatcher.Outcome.COMMITTED, "first unit did not commit");

		int moved = LiveWorkloadAdjustmentRegistry.redirectPendingToNode(runId, 2, 2);
		require(moved == 2, "redirect should move two not-started units to node 2, moved " + moved);
		WorkDispatcher.Assignment other = dispatcher.request(3, 0, phase.now());
		require(other != null, "a redirect must not block the other nodes");
		phase.complete(other);
		for (int lane = 0; lane < 2; lane++)
		{
			WorkDispatcher.Assignment redirected = dispatcher.request(2, lane, phase.now());
			require(redirected != null && redirected.getNode() == 2,
					"node 2 should run the redirected units");
			phase.complete(redirected);
		}

		LiveWorkloadAdjustmentRegistry.setPacingDelayMs(runId, 15);
		LiveWorkloadAdjustmentRegistry.Snapshot paced = LiveWorkloadAdjustmentRegistry.snapshot(runId);
		require(paced.getPacingDelayMs() == 15, "active pacing delay should be recorded");
		require(paced.hasActiveControls(), "pacing delay should count as a live control");
		long start = System.nanoTime();
		LiveWorkloadAdjustmentRegistry.applyClaimPacingDelay(runId, 2);
		require(System.nanoTime() - start >= 14000000L, "pacing should hold the claim for its window");

		require(LiveWorkloadAdjustmentRegistry.setUnavailable(runId, 2, true), "unavailable was refused");
		require(dispatcher.request(2, 0, phase.now()) == null,
				"an unavailable node must not claim future units");
		require(LiveWorkloadAdjustmentRegistry.setUnavailable(runId, 2, false), "available was refused");

		require(LiveWorkloadAdjustmentRegistry.setCapacityScale(runId, 3, 0.25), "capacity scale was refused");
		require(LiveWorkloadAdjustmentRegistry.setPolicyOverride(
				runId, SchedulerLabEngine.SchedulingPolicy.ROUND_ROBIN), "policy override was refused");
		require(LiveWorkloadAdjustmentRegistry.snapshot(runId).hasActiveControls(),
				"capacity and policy controls should be visible");

		LiveWorkloadAdjustmentRegistry.resetRunControls(runId);
		LiveWorkloadAdjustmentRegistry.Snapshot reset = LiveWorkloadAdjustmentRegistry.snapshot(runId);
		require(!reset.hasActiveControls(), "reset should clear all live workload overrides");
		require(reset.getPacingDelayMs() == 0, "reset should clear the active action window");

		String nextRunId = runId + "-next";
		LiveWorkloadAdjustmentRegistry.setNextRunPacingDelayMs(20);
		RecommendationSchedulerRegistry.beginRun(nextRunId, nodes, heapMb, ControllerAgent.Doc2Vec, 1);
		require(LiveWorkloadAdjustmentRegistry.snapshot(nextRunId).getPacingDelayMs() == 20,
				"next-run action window should apply when the next run begins");
		LiveWorkloadAdjustmentRegistry.setNextRunPacingDelayMs(0);
		LiveWorkloadAdjustmentRegistry.resetRunControls(nextRunId);

		context.close("self-test finished");
		SchedulingRuntime.close(nextRunId, "self-test finished");
		RecommendationSchedulerRegistry.restoreDefault(nodes, heapMb);
		NodeCapacityRegistry.resetToDefaults(nodes, heapMb);
		System.out.println("LiveWorkloadAdjustmentSelfTest passed");
	}

	private static void require(boolean condition, String message)
	{
		if (!condition)
		{
			throw new AssertionError(message);
		}
	}
}
