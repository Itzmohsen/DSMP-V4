package TwitterGatherDataFollowers.userRyersonU;

import java.util.Locale;

final class RecommendationSchedulerPlan
{
	enum Mode
	{
		DSMP_DEFAULT(
				"DSMP default",
				"Use the simulator's existing assignment and safety behavior."),
		SCORING_SAFE(
				"Scoring-safe scheduling",
				"Apply the selected policy only at safe assignment boundaries."),
		SCHEDULING_EXPERIMENT(
				"Scheduling experiment",
				"Use experimental placement policies; failure and slowdown stay in isolated schedulable experiments unless a complete unit can be retried safely.");

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

	private RecommendationSchedulerPlan(
			Mode mode,
			SchedulerLabEngine.SchedulingPolicy policy,
			long revision,
			int plannedNodes,
			int plannedHeapMb,
			String note)
	{
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
				"Original DSMP assignment behavior is active.");
	}

	static RecommendationSchedulerPlan create(
			Mode mode,
			SchedulerLabEngine.SchedulingPolicy policy,
			long revision,
			int plannedNodes,
			int plannedHeapMb,
			String note)
	{
		return new RecommendationSchedulerPlan(
				mode,
				policy,
				revision,
				plannedNodes,
				plannedHeapMb,
				note);
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

	boolean allowsExperimentalSideEffects()
	{
		return mode == Mode.SCHEDULING_EXPERIMENT;
	}

	String summary()
	{
		if (!isActive())
		{
			return "DSMP default scheduling";
		}
		return mode.getLabel() + " | " + policy.getLabel()
				+ " | " + plannedNodes + " node"
				+ (plannedNodes == 1 ? "" : "s")
				+ " | " + RuntimeResourceSettings.formatMegabytes(plannedHeapMb);
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
