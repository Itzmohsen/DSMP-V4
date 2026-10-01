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
		return apply(mode, policy, plannedNodes, plannedHeapMb, note, null);
	}

	/**
	 * Sets the plan the next initialized run freezes; a null scenario keeps the
	 * current plan's scenario.
	 */
	static RecommendationSchedulerPlan apply(
			RecommendationSchedulerPlan.Mode mode,
			SchedulerLabEngine.SchedulingPolicy policy,
			int plannedNodes,
			int plannedHeapMb,
			String note,
			Scenario scenario)
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
					note,
					scenario == null ? currentPlan.getScenario() : scenario);
			return currentPlan;
		}
	}

	/** Replaces only the scenario of the current plan. */
	static RecommendationSchedulerPlan applyScenario(Scenario scenario)
	{
		synchronized (LOCK)
		{
			RecommendationSchedulerPlan plan = currentPlan;
			revision++;
			currentPlan = RecommendationSchedulerPlan.create(plan.getMode(), plan.getPolicy(), revision,
					plan.getPlannedNodes(), plan.getPlannedHeapMb(), plan.getNote(),
					scenario == null ? Scenario.NONE : scenario);
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
					"Original DSMP (V3) placement; no capacity emulation.",
					currentPlan.getScenario());
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
		SchedulingRunContext.Mode mode = SchedulingRunContext.Mode.fromPlan(plan);
		SchedulingRunContext context = new SchedulingRunContext(
				runId,
				mode,
				plan.getPolicy(),
				NodeCapacityRegistry.snapshot(),
				nodes,
				algorithm,
				NodeCapacityRegistry.clusterSettings(),
				mode == SchedulingRunContext.Mode.BASELINE ? Scenario.NONE : plan.getScenario(),
				MAX_FAILURES_PER_UNIT,
				STALL_TIMEOUT_MS,
				LiveWorkloadAdjustmentRegistry.consumeNextRunPacingDelayMs());
		RunRecorder recorder = context.recorder();
		recorder.property("algorithm", RecommendationModeSupport.algorithmName(algorithm));
		recorder.property("targets", Integer.toString(Math.max(0, targetCount)));
		recorder.property("heapMb", Integer.toString(Math.max(RuntimeResourceSettings.MIN_HEAP_MB, heapMb)));
		recorder.property("planNote", plan.getNote());
		for (java.util.Map.Entry<String,String> entry : pendingRunProperties().entrySet())
		{
			recorder.property(entry.getKey(), entry.getValue());
		}
		SchedulingRuntime.register(context);
		context.start();
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

	private static final java.util.LinkedHashMap<String,String> NEXT_RUN_PROPERTIES =
			new java.util.LinkedHashMap<String,String>();

	/**
	 * Properties the next initialized run records in its manifest (for example
	 * a study's name, cell and repetition). They apply to every later run until
	 * cleared.
	 */
	static void setRunProperties(java.util.Map<String,String> properties)
	{
		synchronized (LOCK)
		{
			NEXT_RUN_PROPERTIES.clear();
			if (properties != null)
			{
				NEXT_RUN_PROPERTIES.putAll(properties);
			}
		}
	}

	private static java.util.Map<String,String> pendingRunProperties()
	{
		synchronized (LOCK)
		{
			return new java.util.LinkedHashMap<String,String>(NEXT_RUN_PROPERTIES);
		}
	}

	static String activeRunId()
	{
		synchronized (LOCK)
		{
			return activeRunId;
		}
	}
}
