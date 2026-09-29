package TwitterGatherDataFollowers.userRyersonU;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Live controls against a running phase and live input routing: controls
 * need the right run id, a paused node holds at phase boundaries and claims
 * nothing, a drain revokes running units and the stale completion is
 * discarded while the rerun commits, and input users are routed whole and
 * only once.
 */
public final class LiveWorkloadRuntimeSelfTest
{
	private LiveWorkloadRuntimeSelfTest()
	{
	}

	public static void main(String[] args)
	{
		wrongRunIdCannotMutateActiveControls();
		missingQueueSnapshotDoesNotInventWaitingWork();
		inputRoutingKeepsUsersWholeAndDecidesOnce();
		runtimeClosedInputCanAdvanceWithRetainedRecords();
		phaseBoundaryPauseWaitsForResume();
		pauseBlocksFutureClaims();
		drainRevokesRunningUnitAndIgnoresStaleCompletion();
		RecommendationSchedulerRegistry.restoreDefault(2, 4096);
		System.out.println("LiveWorkloadRuntimeSelfTest passed");
	}

	private static void wrongRunIdCannotMutateActiveControls()
	{
		String runId = RecommendationRunLifecycle.createRunId();
		beginControlledRun(runId, ControllerAgent.Doc2Vec, 8);
		require(!LiveWorkloadAdjustmentRegistry.setPaused(runId + "-wrong", 1, true),
				"a wrong run id was accepted");
		require(!LiveWorkloadAdjustmentRegistry.snapshot(runId).isPaused(1),
				"wrong run ID was allowed to pause the active run");
		LiveWorkloadAdjustmentRegistry.setPaused(runId, 1, true);
		require(LiveWorkloadAdjustmentRegistry.snapshot(runId).isPaused(1),
				"active run ID did not pause the node");
		SchedulingRuntime.close(runId, "test finished");
	}

	private static void missingQueueSnapshotDoesNotInventWaitingWork()
	{
		String runId = RecommendationRunLifecycle.createRunId();
		beginControlledRun(runId, ControllerAgent.COS_SIM, 40);
		RecommendationWorkQueueRegistry.QueueSnapshot snapshot =
				RecommendationWorkQueueRegistry.snapshotPhase(
						runId, RecommendationWorkQueueRegistry.CANDIDATES);
		require(snapshot.getTotalShards() == 0
						&& snapshot.getWaitingCount() == 0
						&& snapshot.getEvents().isEmpty(),
				"a phase that never opened was displayed as waiting work");
		SchedulingRuntime.close(runId, "test finished");
	}

	private static void inputRoutingKeepsUsersWholeAndDecidesOnce()
	{
		String runId = RecommendationRunLifecycle.createRunId();
		SchedulingRunContext context = beginControlledRun(runId, ControllerAgent.COS_SIM, 4);
		context.inputPlanned(3);
		require(context.isStreamedInput(), "announced input was not marked as streamed");
		require(context.routeUsers(Arrays.asList("alice", "bob"), 1, 10.0) == 1,
				"users should go to their planned node while it is available");
		LiveWorkloadAdjustmentRegistry.setPaused(runId, 1, true);
		require(context.routeUsers(Arrays.asList("carol"), 1, 5.0) == 0,
				"a user of a paused node should be held, not split or moved");
		LiveWorkloadAdjustmentRegistry.setPaused(runId, 1, false);
		require(LiveWorkloadAdjustmentRegistry.drainNodeTo(runId, 1, 2, 0) >= 0,
				"drain was refused");
		require(context.routeUsers(Arrays.asList("carol"), 1, 5.0) == 2,
				"a user of a drained node should go to the drain target");
		require(context.routeUsers(Arrays.asList("alice"), 1, 10.0) == 1,
				"a routed user must keep its receiver (its rows never split)");
		context.inputDispatched("carol", 2);
		context.inputDelivered("carol", 2);
		require(context.inputDeliveredByUser().get("carol").intValue() == 2,
				"delivery was not tracked");
		SchedulingRuntime.close(runId, "test finished");
	}

	private static void runtimeClosedInputCanAdvanceWithRetainedRecords()
	{
		String runId = RecommendationRunLifecycle.createRunId();
		RecommendationRunLifecycle lifecycle = new RecommendationRunLifecycle();
		lifecycle.admit(runId);
		lifecycle.markInputDeliveryComplete(runId);
		require(!lifecycle.markTextProcessingCompleteIfReady(5, 10),
				"legacy input close accepted a short node assignment");

		String runtimeRunId = RecommendationRunLifecycle.createRunId();
		RecommendationRunLifecycle runtimeLifecycle = new RecommendationRunLifecycle();
		runtimeLifecycle.admit(runtimeRunId);
		runtimeLifecycle.markInputDeliveryComplete(runtimeRunId);
		require(runtimeLifecycle.markTextProcessingCompleteIfReady(5, 10, true),
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
					LiveWorkloadAdjustmentRegistry.waitUntilNodeEligible(runId, 1, "self-test boundary");
					finished[0] = true;
				}
				catch (RuntimeException exception)
				{
					failure[0] = exception;
				}
			}
		});
		waiter.start();
		sleep(100L);
		require(!finished[0], "phase boundary did not wait while node was paused");
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
		require(finished[0], "phase boundary did not resume after pause was cleared");
		SchedulingRuntime.close(runId, "test finished");
	}

	private static void pauseBlocksFutureClaims()
	{
		String runId = RecommendationRunLifecycle.createRunId();
		SchedulingRunContext context = beginControlledRun(runId, ControllerAgent.Doc2Vec, 8);
		WorkPhase phase = openPhase(context, 8);
		LiveWorkloadAdjustmentRegistry.setPaused(runId, 1, true);
		require(phase.getDispatcher().request(1, 0, phase.now()) == null,
				"paused node claimed live queue work");
		LiveWorkloadAdjustmentRegistry.setPaused(runId, 1, false);
		require(phase.getDispatcher().request(1, 0, phase.now()) != null,
				"resumed node did not claim live queue work");
		SchedulingRuntime.close(runId, "test finished");
	}

	private static void drainRevokesRunningUnitAndIgnoresStaleCompletion()
	{
		String runId = RecommendationRunLifecycle.createRunId();
		SchedulingRunContext context = beginControlledRun(runId, ControllerAgent.SVM, 4);
		WorkPhase phase = openPhase(context, 6);
		WorkDispatcher dispatcher = phase.getDispatcher();
		WorkDispatcher.Assignment onNodeOne = dispatcher.request(1, 0, phase.now());
		require(onNodeOne != null, "node 1 did not claim an initial unit");
		int unit = onNodeOne.getUnitId();
		int revoked = LiveWorkloadAdjustmentRegistry.drainNodeTo(runId, 1, 2, 0);
		require(revoked == 1, "drain did not revoke the running unit (revoked " + revoked + ")");
		require(!phase.isLive(onNodeOne), "the drained execution stayed live");
		require(phase.complete(onNodeOne) == WorkDispatcher.Outcome.STALE,
				"a stale completion after a drain was accepted");
		require(dispatcher.getCommittedCount() == 0, "a stale completion was counted");
		int guard = 0;
		while (!dispatcher.isComplete() && guard++ < 100)
		{
			WorkDispatcher.Assignment next = dispatcher.request(2, 0, phase.now());
			require(next != null, "node 2 ran out of work before the phase completed");
			require(phase.complete(next) == WorkDispatcher.Outcome.COMMITTED, "a unit did not commit");
		}
		require(dispatcher.isComplete(), "the phase did not complete on the remaining node");
		require(dispatcher.committedNodeOf(unit) == 2,
				"the revoked unit was not committed by the drain target");
		SchedulingRuntime.close(runId, "test finished");
	}

	private static SchedulingRunContext beginControlledRun(String runId, int algorithm, int targetCount)
	{
		NodeCapacityRegistry.resetToDefaults(2, 4096);
		RecommendationSchedulerRegistry.apply(
				RecommendationSchedulerPlan.Mode.SCORING_SAFE,
				SchedulerLabEngine.SchedulingPolicy.ROUND_ROBIN,
				2,
				4096,
				"live workload self-test");
		return RecommendationSchedulerRegistry.beginRun(runId, 2, 4096, algorithm, targetCount);
	}

	private static WorkPhase openPhase(SchedulingRunContext context, final int units)
	{
		return context.openPhase("svm-targets", "self-test units", new SchedulingRunContext.UnitSupplier()
		{
			public List<WorkUnitSpec> units()
			{
				ArrayList<WorkUnitSpec> list = new ArrayList<WorkUnitSpec>();
				for (int id = 1; id <= units; id++)
				{
					list.add(new WorkUnitSpec(id, "unit-" + id, 1.0));
				}
				return list;
			}
		});
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
