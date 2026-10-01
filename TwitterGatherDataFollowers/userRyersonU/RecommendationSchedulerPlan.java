package TwitterGatherDataFollowers.userRyersonU;

import java.util.Locale;

final class RecommendationSchedulerPlan
{
	enum Mode
	{
		DSMP_DEFAULT(
				"Hardware (unemulated)",
				"Runs on this machine's hardware with the original DSMP (V3) placement; node profiles are ignored and nothing is emulated. The exactness reference."),
		SCORING_SAFE(
				"Emulated cluster",
				"Every node's speed, lanes, memory, queue, link latency and bandwidth are physically emulated on one time scale, and the policy places and dispatches the work. Recommendations are identical to the hardware mode."),
		SCHEDULING_EXPERIMENT(
				"Emulated cluster with faults",
				"As emulated, plus transient unit faults and node crashes with repairs, all seeded and reproducible, retried with exactly-once commits. Recommendations are still identical.");

		private final String label;
		private final String description;

		Mode(String label, String description)
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

	private final Mode mode;
	private final SchedulerLabEngine.SchedulingPolicy policy;
	private final long revision;
	private final int plannedNodes;
	private final int plannedHeapMb;
	private final String note;
	private final Scenario scenario;

	private RecommendationSchedulerPlan(
			Mode mode,
			SchedulerLabEngine.SchedulingPolicy policy,
			long revision,
			int plannedNodes,
			int plannedHeapMb,
			String note,
			Scenario scenario)
	{
		this.scenario = scenario == null ? Scenario.NONE : scenario;
		this.mode = mode == null ? Mode.DSMP_DEFAULT : mode;
		this.policy = policy == null
				? SchedulerLabEngine.SchedulingPolicy.CAPACITY_WEIGHTED : policy;
		this.revision = Math.max(0L, revision);
		this.plannedNodes = Math.max(1, plannedNodes);
		this.plannedHeapMb = Math.max(
				RuntimeResourceSettings.MIN_HEAP_MB,
				plannedHeapMb);
		this.note = note == null ? "" : note;
	}

	static RecommendationSchedulerPlan defaultPlan()
	{
		return new RecommendationSchedulerPlan(
				Mode.DSMP_DEFAULT,
				SchedulerLabEngine.SchedulingPolicy.CAPACITY_WEIGHTED,
				0L,
				Math.max(1, NodeCapacityRegistry.configuredNodeCount()),
				NodeCapacityRegistry.configuredHeapMb(),
				"Original DSMP (V3) placement; no capacity emulation.",
				Scenario.NONE);
	}

	static RecommendationSchedulerPlan create(
			Mode mode,
			SchedulerLabEngine.SchedulingPolicy policy,
			long revision,
			int plannedNodes,
			int plannedHeapMb,
			String note)
	{
		return create(mode, policy, revision, plannedNodes, plannedHeapMb, note, Scenario.NONE);
	}

	static RecommendationSchedulerPlan create(
			Mode mode,
			SchedulerLabEngine.SchedulingPolicy policy,
			long revision,
			int plannedNodes,
			int plannedHeapMb,
			String note,
			Scenario scenario)
	{
		return new RecommendationSchedulerPlan(
				mode,
				policy,
				revision,
				plannedNodes,
				plannedHeapMb,
				note,
				scenario);
	}

	/** The scripted adjustments the next run executes (empty for none). */
	Scenario getScenario()
	{
		return scenario;
	}

	Mode getMode()
	{
		return mode;
	}

	SchedulerLabEngine.SchedulingPolicy getPolicy()
	{
		return policy;
	}

	long getRevision()
	{
		return revision;
	}

	int getPlannedNodes()
	{
		return plannedNodes;
	}

	int getPlannedHeapMb()
	{
		return plannedHeapMb;
	}

	String getNote()
	{
		return note;
	}

	boolean isActive()
	{
		return mode != Mode.DSMP_DEFAULT;
	}

	/** True when unit failures are injected (the fault-injection mode). */
	boolean injectsFailures()
	{
		return mode == Mode.SCHEDULING_EXPERIMENT;
	}

	String summary()
	{
		if (!isActive())
		{
			return "Hardware (unemulated), original DSMP placement";
		}
		return mode.getLabel() + " | " + policy.getLabel()
				+ " | " + plannedNodes + " node"
				+ (plannedNodes == 1 ? "" : "s")
				+ " | " + RuntimeResourceSettings.formatMegabytes(plannedHeapMb)
				+ (scenario.isEmpty() ? "" : " | scenario: " + scenario.getName());
	}

	String compactPolicyLabel()
	{
		if (!isActive())
		{
			return "DEFAULT";
		}
		return policy.getLabel().toUpperCase(Locale.US);
	}
}
