package TwitterGatherDataFollowers.userRyersonU;

import java.util.concurrent.atomic.AtomicInteger;

final class RecommendationSchedulerRegistry
{
	private static final Object LOCK = new Object();
	private static RecommendationSchedulerPlan currentPlan =
			RecommendationSchedulerPlan.defaultPlan();
	private static long revision;
	private static final AtomicInteger ROUND_ROBIN_CURSOR =
			new AtomicInteger();
	private static String activeRunId = "";

	private RecommendationSchedulerRegistry()
	{
	}

	static RecommendationSchedulerPlan snapshot()
	{
		synchronized (LOCK)
		{
			return currentPlan;
		}
	}

	static RecommendationSchedulerPlan apply(
			RecommendationSchedulerPlan.Mode mode,
			SchedulerLabEngine.SchedulingPolicy policy,
			int plannedNodes,
			int plannedHeapMb,
			String note)
	{
		synchronized (LOCK)
		{
			revision++;
			currentPlan = RecommendationSchedulerPlan.create(
					mode,
					policy,
					revision,
					plannedNodes,
					plannedHeapMb,
					note);
			ROUND_ROBIN_CURSOR.set(0);
			return currentPlan;
		}
	}

	static RecommendationSchedulerPlan restoreDefault(
			int plannedNodes,
			int plannedHeapMb)
	{
		synchronized (LOCK)
		{
			revision++;
			currentPlan = RecommendationSchedulerPlan.create(
					RecommendationSchedulerPlan.Mode.DSMP_DEFAULT,
					SchedulerLabEngine.SchedulingPolicy.CAPACITY_WEIGHTED,
					revision,
					plannedNodes,
					plannedHeapMb,
					"Original DSMP assignment behavior is active.");
			ROUND_ROBIN_CURSOR.set(0);
			return currentPlan;
		}
	}

	static void beginRun(
			String runId,
			int nodeCount,
			int heapMb,
			int algorithm,
			int targetCount)
	{
		synchronized (LOCK)
		{
			activeRunId = runId == null ? "" : runId;
			ROUND_ROBIN_CURSOR.set(0);
			LiveWorkloadAdjustmentRegistry.beginRun(
					activeRunId,
					Math.max(1, nodeCount));
			RecommendationSchedulerPlan plan = currentPlan;
			System.out.println(
					"Recommendation scheduler plan for run "
					+ activeRunId + ": "
					+ plan.summary()
					+ " algorithm="
					+ RecommendationModeSupport.algorithmName(algorithm)
					+ " targets=" + Math.max(0, targetCount)
					+ " nodes=" + Math.max(1, nodeCount)
					+ " heapMb=" + Math.max(
							RuntimeResourceSettings.MIN_HEAP_MB,
							heapMb));
		}
	}

	static int nextRoundRobinNode(int nodeCount)
	{
		int safeCount = Math.max(1, nodeCount);
		return Math.floorMod(
				ROUND_ROBIN_CURSOR.getAndIncrement(),
				safeCount);
	}

	static String activeRunId()
	{
		synchronized (LOCK)
		{
			return activeRunId;
		}
	}
}
