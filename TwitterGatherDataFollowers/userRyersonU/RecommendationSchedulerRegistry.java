package TwitterGatherDataFollowers.userRyersonU;

/**
 * Holds the scheduler plan the next run will use, and freezes it into a
 * {@link SchedulingRunContext} when a run is initialized.
 *
 * Changing the plan here never affects a run that has already started: each
 * run reads only its own frozen context. Live changes to a running run go
 * through {@link LiveWorkloadAdjustmentRegistry}.
 */
final class RecommendationSchedulerRegistry
{
	/** Units failing this often abort the run with a clear reason. */
	static final int MAX_FAILURES_PER_UNIT = 25;
	/** A phase with no progress for this long, and no operator hold, fails. */
	static final long STALL_TIMEOUT_MS = 6L * 60L * 60L * 1000L;
	/** Seed of the injected-failure hash; fixed so fault runs are reproducible. */
	static final long FAULT_SEED = 0x44534D50L;

	private static final Object LOCK = new Object();
	private static RecommendationSchedulerPlan currentPlan =
			RecommendationSchedulerPlan.defaultPlan();
	private static long revision;
	private static String activeRunId = "";

	private RecommendationSchedulerRegistry()
	{
	}

	/** The plan the next initialized run will freeze. */
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
					"Original DSMP (V3) placement; no capacity emulation.");
			return currentPlan;
		}
	}

	/**
	 * Freezes the current plan and node profiles into this run's context and
	 * registers it. Called once per initialization, before any agent starts.
	 */
	static SchedulingRunContext beginRun(
			String runId,
			int nodeCount,
			int heapMb,
			int algorithm,
			int targetCount)
	{
		RecommendationSchedulerPlan plan;
		synchronized (LOCK)
		{
			activeRunId = runId == null ? "" : runId;
			plan = currentPlan;
		}
		int nodes = Math.max(1, nodeCount);
		NodeCapacityRegistry.configureForNodeCount(nodes, heapMb);
		SchedulingRunContext context = new SchedulingRunContext(
				runId,
				SchedulingRunContext.Mode.fromPlan(plan),
				plan.getPolicy(),
				NodeCapacityRegistry.snapshot(),
				nodes,
				algorithm,
				FAULT_SEED,
				MAX_FAILURES_PER_UNIT,
				STALL_TIMEOUT_MS,
				LiveWorkloadAdjustmentRegistry.consumeNextRunPacingDelayMs());
		SchedulingRuntime.register(context);
		System.out.println(
				"Recommendation scheduler plan for run " + runId + ": "
				+ context.describe()
				+ " algorithm=" + RecommendationModeSupport.algorithmName(algorithm)
				+ " targets=" + Math.max(0, targetCount)
				+ " heapMb=" + Math.max(RuntimeResourceSettings.MIN_HEAP_MB, heapMb));
		for (NodeSpec spec : context.currentSpecs())
		{
			System.out.println("  " + spec.describe());
		}
		return context;
	}

	static String activeRunId()
	{
		synchronized (LOCK)
		{
			return activeRunId;
		}
	}
}
