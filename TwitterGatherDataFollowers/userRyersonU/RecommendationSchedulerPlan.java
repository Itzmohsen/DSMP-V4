package TwitterGatherDataFollowers.userRyersonU;

import java.util.Locale;

final class RecommendationSchedulerPlan
{
	enum Mode
	{
		DSMP_DEFAULT(
				"Original DSMP",
				"V3 list-scheduling placement; each node works on its own data; no capacity emulation."),
		SCORING_SAFE(
				"Scheduled, capacity emulated",
				"Placement and work dispatch follow the policy, and node speed, lanes, memory, queue and link delay are physically emulated. Recommendations are identical to the original mode."),
		SCHEDULING_EXPERIMENT(
				"Scheduled, fault injection",
				"As capacity emulated, plus injected unit failures at each node's failure probability, retried with exactly-once commits. Recommendations are still identical.");

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
				"Original DSMP (V3) placement; no capacity emulation.");
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

	/** True when unit failures are injected (the fault-injection mode). */
	boolean injectsFailures()
	{
		return mode == Mode.SCHEDULING_EXPERIMENT;
	}

	String summary()
	{
		if (!isActive())
		{
			return "Original DSMP scheduling";
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
