package TwitterGatherDataFollowers.userRyersonU;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * The Scheduler Lab: what-if scheduling of synthetic recommender workloads.
 *
 * The lab no longer has scheduling rules of its own. It runs the workload
 * through {@link WorkDispatcher}, the dispatcher the live simulator uses, in
 * virtual time ({@link SchedulingSimulator}), so a policy's behaviour here is
 * exactly its behaviour in a live run with the same node profiles.
 */
final class SchedulerLabEngine
{
	enum WorkloadUnitType
	{
		PREPROCESSING_BATCH("Preprocessing batch", "clean and tokenize a batch"),
		TARGET_RECOMMENDATION_JOB("Target-user job", "produce one target recommendation"),
		CANDIDATE_SHARD("Candidate shard", "score a slice of candidates"),
		MODEL_TRAINING_JOB("Model training", "build or refresh model state"),
		SCORING_JOB("Scoring job", "rank candidate scores"),
		GRAPH_REPORTING_JOB("Graph/reporting", "persist graph or analysis output");

		private final String label;
		private final String description;

		WorkloadUnitType(String label, String description)
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

	enum WorkloadTemplate
	{
		BALANCED_RECOMMENDER_RUN(
				"Balanced recommender run",
				"preprocessing, model, scoring, and graph jobs"),
		POPULATION_TARGET_SWEEP(
				"Population target sweep",
				"many independent target-user recommendation jobs"),
		MODEL_HEAVY_RUN(
				"Model-heavy run",
				"larger feature/model jobs with memory pressure"),
		GRAPH_AND_REPORTING_RUN(
				"Graph and reporting run",
				"small scoring jobs followed by graph/report work");

		private final String label;
		private final String description;

		WorkloadTemplate(String label, String description)
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

	/**
	 * The scheduling policies. Each has one precise definition, implemented once
	 * by {@link PlacementPlanner} (static placement) and {@link WorkDispatcher}
	 * (execution), and shared by the Scheduler Lab, the Dynamic Queue Lab and the
	 * live simulator.
	 */
	enum SchedulingPolicy
	{
		STATIC_EQUAL_SPLIT(
				"Static equal split",
				"static baseline: contiguous blocks of equal size in canonical order; ignores cost and speed"),
		ROUND_ROBIN(
				"Round robin",
				"static: unit k goes to node k mod N; ignores cost and speed"),
		WEIGHTED_ROUND_ROBIN(
				"Weighted round robin",
				"static: smooth weighted round robin; unit counts proportional to node throughput"),
		LEAST_LOADED_NODE(
				"Least loaded node",
				"static: each unit to the node with the least assigned cost (Graham list scheduling); ignores speed"),
		FASTEST_NODE_FIRST(
				"Fastest node first",
				"static: each unit to the node that frees up first, ties to the fastest; ignores unit size"),
		MEMORY_AWARE_SAFE_WAVE(
				"Memory-aware safe-wave",
				"dynamic: largest memory first; a unit starts only where it fits the free memory budget, the rest wait for the next wave"),
		CAPACITY_WEIGHTED(
				"Capacity-weighted",
				"static: largest units first, each to the node where it finishes earliest (LPT-EFT)"),
		QUEUE_LENGTH(
				"Queue-length",
				"dynamic: idle lanes pull from one queue; simultaneous requests favour the shortest node queue"),
		WORK_STEALING(
				"Work stealing",
				"dynamic: starts from the capacity-weighted plan; idle nodes steal from the node with the most remaining work"),
		DEADLINE_PRIORITY(
				"Deadline / priority",
				"dynamic: highest priority, then earliest deadline first; urgent units go to the fastest idle node"),
		FAILURE_AWARE_RESCHEDULING(
				"Failure-aware rescheduling",
				"dynamic: failed units retry away from the node that failed them, preferring reliable throughput");

		private final String label;
		private final String description;

		SchedulingPolicy(String label, String description)
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

		/** True for policies that rebalance while the work runs. */
		boolean isDynamic()
		{
			return WorkDispatcher.modeOf(this) != WorkDispatcher.ExecutionMode.STATIC;
		}

		public String toString()
		{
			return label;
		}
	}

	enum EventType
	{
		ASSIGNED,
		STARTED,
		COMPLETED,
		FAILED,
		RETRIED,
		STOLEN,
		WAITING,
		SKIPPED
	}

	static final class WorkloadUnit
	{
		private final String id;
		private final WorkloadUnitType type;
		private final String label;
		private final int costUnits;
		private final int memoryMb;
		private final int priority;
		private final int deadlineTick;
		private final int maxRetries;

		WorkloadUnit(
				String id,
				WorkloadUnitType type,
				String label,
				int costUnits,
				int memoryMb,
				int priority,
				int deadlineTick,
				int maxRetries)
		{
			this.id = id == null ? "job" : id;
			this.type = type == null
					? WorkloadUnitType.TARGET_RECOMMENDATION_JOB : type;
			this.label = label == null ? this.type.getLabel() : label;
			this.costUnits = clampInt(costUnits, 1, 1000000);
			this.memoryMb = clampInt(memoryMb, 1, 262144);
			this.priority = clampInt(priority, 1, 10);
			this.deadlineTick = clampInt(deadlineTick, 1, 100000000);
			this.maxRetries = clampInt(maxRetries, 0, 10);
		}

		String getId()
		{
			return id;
		}

		WorkloadUnitType getType()
		{
			return type;
		}

		String getLabel()
		{
			return label;
		}

		int getCostUnits()
		{
			return costUnits;
		}

		int getMemoryMb()
		{
			return memoryMb;
		}

		int getPriority()
		{
			return priority;
		}

		int getDeadlineTick()
		{
			return deadlineTick;
		}

		int getMaxRetries()
		{
			return maxRetries;
		}
	}

	static final class TimelineEvent
	{
		private final int tick;
		private final int nodeIndex;
		private final String jobId;
		private final WorkloadUnitType jobType;
		private final EventType type;
		private final String detail;

		TimelineEvent(
				int tick,
				int nodeIndex,
				String jobId,
				WorkloadUnitType jobType,
				EventType type,
				String detail)
		{
			this.tick = Math.max(0, tick);
			this.nodeIndex = Math.max(0, nodeIndex);
			this.jobId = jobId == null ? "" : jobId;
			this.jobType = jobType == null
					? WorkloadUnitType.TARGET_RECOMMENDATION_JOB : jobType;
			this.type = type == null ? EventType.WAITING : type;
			this.detail = detail == null ? "" : detail;
		}

		int getTick()
		{
			return tick;
		}

		int getNodeIndex()
		{
			return nodeIndex;
		}

		String getJobId()
		{
			return jobId;
		}

		WorkloadUnitType getJobType()
		{
			return jobType;
		}

		EventType getType()
		{
			return type;
		}

		String getDetail()
		{
			return detail;
		}
	}

	static final class NodeSummary
	{
		private final NodeCapacityProfile profile;
		private int assignedJobs;
		private int completedJobs;
		private int failedJobs;
		private int retriedJobs;
		private int stolenJobs;
		private int queuePeak;
		private long plannedCost;
		private long busyTicks;
		private long idleTicks;
		private int lastFinishTick;

		NodeSummary(NodeCapacityProfile profile)
		{
			this.profile = profile;
		}

		NodeCapacityProfile getProfile()
		{
			return profile;
		}

		int getNodeIndex()
		{
			return profile.getNodeIndex();
		}

		int getAssignedJobs()
		{
			return assignedJobs;
		}

		int getCompletedJobs()
		{
			return completedJobs;
		}

		int getFailedJobs()
		{
			return failedJobs;
		}

		int getRetriedJobs()
		{
			return retriedJobs;
		}

		int getStolenJobs()
		{
			return stolenJobs;
		}

		int getQueuePeak()
		{
			return queuePeak;
		}

		/** Ticks of work executed on the node (cost / speed, all executions). */
		long getPlannedCost()
		{
			return plannedCost;
		}

		/** Lane-ticks spent executing. */
		long getBusyTicks()
		{
			return busyTicks;
		}

		/** Lane-ticks idle before the run finished. */
		long getIdleTicks()
		{
			return idleTicks;
		}

		int getLastFinishTick()
		{
			return lastFinishTick;
		}
	}

	static final class Result
	{
		private final SchedulingPolicy policy;
		private final WorkloadTemplate template;
		private final List<WorkloadUnit> jobs;
		private final List<NodeSummary> nodes;
		private final List<TimelineEvent> events;
		private final List<String> logLines;
		private final int totalJobs;
		private final int completedJobs;
		private final int failedJobs;
		private final int retriedJobs;
		private final int waitingJobs;
		private final int makespanTicks;
		private final long totalBusyTicks;
		private final long totalIdleTicks;
		private final int heapMb;
		private final double lowerBoundTicks;

		Result(
				SchedulingPolicy policy,
				WorkloadTemplate template,
				List<WorkloadUnit> jobs,
				List<NodeSummary> nodes,
				List<TimelineEvent> events,
				List<String> logLines,
				int totalJobs,
				int completedJobs,
				int failedJobs,
				int retriedJobs,
				int waitingJobs,
				int makespanTicks,
				long totalBusyTicks,
				long totalIdleTicks,
				int heapMb,
				double lowerBoundTicks)
		{
			this.policy = policy;
			this.template = template;
			this.jobs = Collections.unmodifiableList(
					new ArrayList<WorkloadUnit>(jobs));
			this.nodes = Collections.unmodifiableList(
					new ArrayList<NodeSummary>(nodes));
			this.events = Collections.unmodifiableList(
					new ArrayList<TimelineEvent>(events));
			this.logLines = Collections.unmodifiableList(
					new ArrayList<String>(logLines));
			this.totalJobs = totalJobs;
			this.completedJobs = completedJobs;
			this.failedJobs = failedJobs;
			this.retriedJobs = retriedJobs;
			this.waitingJobs = waitingJobs;
			this.makespanTicks = makespanTicks;
			this.totalBusyTicks = totalBusyTicks;
			this.totalIdleTicks = totalIdleTicks;
			this.heapMb = heapMb;
			this.lowerBoundTicks = lowerBoundTicks;
		}

		SchedulingPolicy getPolicy()
		{
			return policy;
		}

		WorkloadTemplate getTemplate()
		{
			return template;
		}

		List<WorkloadUnit> getJobs()
		{
			return jobs;
		}

		List<NodeSummary> getNodes()
		{
			return nodes;
		}

		List<TimelineEvent> getEvents()
		{
			return events;
		}

		List<String> getLogLines()
		{
			return logLines;
		}

		int getTotalJobs()
		{
			return totalJobs;
		}

		int getCompletedJobs()
		{
			return completedJobs;
		}

		int getFailedJobs()
		{
			return failedJobs;
		}

		int getRetriedJobs()
		{
			return retriedJobs;
		}

		int getWaitingJobs()
		{
			return waitingJobs;
		}

		int getMakespanTicks()
		{
			return makespanTicks;
		}

		long getTotalBusyTicks()
		{
			return totalBusyTicks;
		}

		long getTotalIdleTicks()
		{
			return totalIdleTicks;
		}

		int getHeapMb()
		{
			return heapMb;
		}

		/**
		 * A lower bound on any schedule's makespan for this workload and these
		 * nodes: max(total work / total throughput, largest unit / fastest node).
		 */
		double getLowerBoundTicks()
		{
			return lowerBoundTicks;
		}

		/** Makespan divided by the lower bound (1.0 is provably optimal). */
		double efficiencyRatio()
		{
			return lowerBoundTicks <= 0.0 ? 1.0 : makespanTicks / lowerBoundTicks;
		}

		double utilization()
		{
			long denominator = totalBusyTicks + totalIdleTicks;
			if (denominator <= 0L)
			{
				return 0.0;
			}
			return totalBusyTicks / (double)denominator;
		}
	}

	/** Milliseconds represented by one lab tick of reference work. */
	static final double MILLISECONDS_PER_TICK = 100.0;
	/** Fixed seed so injected failures are reproducible run to run. */
	static final long FAULT_SEED = 20260928L;

	private SchedulerLabEngine()
	{
	}

	static List<WorkloadUnit> createWorkload(
			WorkloadTemplate template,
			int jobCount,
			int targetCountHint)
	{
		WorkloadTemplate safeTemplate = template == null
				? WorkloadTemplate.BALANCED_RECOMMENDER_RUN : template;
		int safeCount = clampInt(jobCount, 1, 20000);
		int targetHint = Math.max(1, targetCountHint);
		ArrayList<WorkloadUnit> jobs = new ArrayList<WorkloadUnit>();
		for (int index = 0; index < safeCount; index++)
		{
			jobs.add(createJob(safeTemplate, index, safeCount, targetHint));
		}
		return jobs;
	}

	/**
	 * Runs the workload through the shared work dispatcher in virtual time.
	 *
	 * One tick is 100 ms of reference work. Node speed, lanes, memory budget,
	 * link delay and failure probability come from the capacity profiles exactly
	 * as the live simulator interprets them; failure injection is on when any
	 * profile enables it. Jobs no node could ever hold are skipped and reported.
	 */
	static Result simulate(
			List<NodeCapacityProfile> profiles,
			SchedulingPolicy policy,
			WorkloadTemplate template,
			List<WorkloadUnit> jobs,
			int heapMb)
	{
		SchedulingPolicy safePolicy = policy == null
				? SchedulingPolicy.CAPACITY_WEIGHTED : policy;
		WorkloadTemplate safeTemplate = template == null
				? WorkloadTemplate.BALANCED_RECOMMENDER_RUN : template;
		ArrayList<NodeCapacityProfile> safeProfiles = sanitizeProfiles(profiles, heapMb);
		int safeHeap = Math.max(RuntimeResourceSettings.MIN_HEAP_MB, heapMb);
		List<NodeSpec> specs = NodeSpec.fromProfiles(safeProfiles, null, true);
		List<WorkloadUnit> safeJobs = jobs == null
				? Collections.<WorkloadUnit>emptyList() : jobs;

		ArrayList<TimelineEvent> events = new ArrayList<TimelineEvent>();
		ArrayList<String> log = new ArrayList<String>();
		ArrayList<WorkUnitSpec> units = new ArrayList<WorkUnitSpec>();
		ArrayList<WorkloadUnit> byUnitId = new ArrayList<WorkloadUnit>();
		int largestNodeMemory = 0;
		boolean faults = false;
		for (NodeSpec spec : specs)
		{
			largestNodeMemory = Math.max(largestNodeMemory, spec.getMemoryMb());
			faults |= spec.getFailureProbability() > 0.0;
		}
		int skipped = 0;
		for (int index = 0; index < safeJobs.size(); index++)
		{
			WorkloadUnit job = safeJobs.get(index);
			if (job == null)
			{
				continue;
			}
			if (job.getMemoryMb() > largestNodeMemory || job.getMemoryMb() > safeHeap)
			{
				skipped++;
				events.add(new TimelineEvent(0, 0, job.getId(), job.getType(),
						EventType.SKIPPED,
						"Needs " + job.getMemoryMb() + " MB; no node budget ("
						+ largestNodeMemory + " MB) or the heap (" + safeHeap
						+ " MB) can ever hold it."));
				continue;
			}
			byUnitId.add(job);
			units.add(new WorkUnitSpec(
					byUnitId.size(),
					String.format(Locale.US, "%06d:%s", index + 1, job.getId()),
					job.getCostUnits(),
					job.getMemoryMb(),
					job.getPriority(),
					job.getDeadlineTick(),
					0,
					null,
					job.getMaxRetries() + 1));
		}
		log.add(String.format(Locale.US,
				"t=0000  Scheduler Lab: %d unit(s), policy %s (%s), %d node(s), heap %d MB%s.",
				units.size(), safePolicy.getLabel(),
				safePolicy.isDynamic() ? "dynamic" : "static", specs.size(), safeHeap,
				faults ? ", failure injection on" : ""));
		for (NodeSpec spec : specs)
		{
			log.add("t=0000  " + spec.describe());
		}

		ArrayList<NodeSummary> summaries = new ArrayList<NodeSummary>();
		for (NodeCapacityProfile profile : safeProfiles)
		{
			summaries.add(new NodeSummary(profile));
		}
		double lowerBound = lowerBound(units, specs);
		if (units.isEmpty())
		{
			return new Result(safePolicy, safeTemplate, safeJobs, summaries, events, log,
					safeJobs.size(), 0, skipped, 0, 0, 0, 0L, 0L, safeHeap, lowerBound);
		}

		SchedulingSimulator.Trace trace = SchedulingSimulator.run(
				new SchedulingSimulator.Config(
						safePolicy,
						specs,
						units,
						new WorkDispatcher.Options()
								.faultInjection(faults, FAULT_SEED)
								.globalMemoryBudgetMb(safeHeap),
						MILLISECONDS_PER_TICK).phaseKey("scheduler-lab"));

		int retried = 0;
		for (SchedulingSimulator.Execution execution : trace.getExecutions())
		{
			WorkloadUnit job = byUnitId.get(execution.getUnitId() - 1);
			NodeSummary summary = summaries.get(execution.getNode() - 1);
			summary.assignedJobs++;
			summary.plannedCost += Math.round(execution.getEnd() - execution.getStart());
			if (execution.getSource() == WorkDispatcher.Source.STOLEN)
			{
				summary.stolenJobs++;
				events.add(new TimelineEvent(tick(execution.getStart()), execution.getNode(),
						job.getId(), job.getType(), EventType.STOLEN,
						"Stolen by node " + execution.getNode() + "."));
			}
			events.add(new TimelineEvent(tick(execution.getStart()), execution.getNode(),
					job.getId(), job.getType(), EventType.STARTED,
					job.getType().getDescription()));
			if (execution.getOutcome() == WorkDispatcher.Outcome.COMMITTED)
			{
				summary.completedJobs++;
				events.add(new TimelineEvent(tick(execution.getEnd()), execution.getNode(),
						job.getId(), job.getType(), EventType.COMPLETED,
						"Finished on node " + execution.getNode() + "."));
			}
			else if (execution.getOutcome() == WorkDispatcher.Outcome.STALE)
			{
				events.add(new TimelineEvent(tick(execution.getEnd()), execution.getNode(),
						job.getId(), job.getType(), EventType.SKIPPED,
						"Revoked execution discarded."));
			}
			else
			{
				summary.failedJobs++;
				events.add(new TimelineEvent(tick(execution.getEnd()), execution.getNode(),
						job.getId(), job.getType(), EventType.FAILED,
						"Simulated node failure; the work is lost."));
				if (execution.getOutcome() != WorkDispatcher.Outcome.FINAL_FAILURE)
				{
					summary.retriedJobs++;
					retried++;
					events.add(new TimelineEvent(tick(execution.getEnd()), execution.getNode(),
							job.getId(), job.getType(), EventType.RETRIED,
							safePolicy == SchedulingPolicy.FAILURE_AWARE_RESCHEDULING
									? "Retry queued away from this node."
									: "Retry queued."));
				}
			}
		}
		Collections.sort(events, new Comparator<TimelineEvent>()
		{
			public int compare(TimelineEvent first, TimelineEvent second)
			{
				return Integer.compare(first.getTick(), second.getTick());
			}
		});

		double makespan = trace.getMakespan();
		long totalBusy = 0L;
		long totalIdle = 0L;
		for (int index = 0; index < summaries.size(); index++)
		{
			NodeSummary summary = summaries.get(index);
			NodeSpec spec = specs.get(index);
			double busy = trace.getBusyTime(index + 1);
			summary.busyTicks = Math.round(busy);
			summary.idleTicks = Math.max(0L, Math.round(spec.getLanes() * makespan - busy));
			summary.lastFinishTick = (int)Math.ceil(trace.getLastFinish(index + 1));
			for (WorkDispatcher.NodeStats stats : trace.getDispatcher().nodeStats())
			{
				if (stats.getNode() == index + 1)
				{
					summary.queuePeak = stats.getQueuePeak();
				}
			}
			totalBusy += summary.busyTicks;
			totalIdle += summary.idleTicks;
		}
		int committed = trace.getCommittedNode().size();
		int permanentlyFailed = trace.getDispatcher().count(WorkDispatcher.UnitState.FAILED);
		int waiting = units.size() - committed - permanentlyFailed;
		log.add(String.format(Locale.US,
				"t=%04d  Run finished: %d committed, %d failed permanently, %d retried, %d skipped; "
				+ "makespan %.1f ticks, lower bound %.1f ticks (ratio %.3f).",
				(int)Math.ceil(makespan), committed, permanentlyFailed, retried, skipped,
				makespan, lowerBound, lowerBound <= 0.0 ? 1.0 : makespan / lowerBound));
		if (trace.getDispatcher().hasFailed())
		{
			log.add("t=" + String.format(Locale.US, "%04d", (int)Math.ceil(makespan))
					+ "  " + trace.getDispatcher().getFailureReason());
		}
		for (String violation : trace.getViolations())
		{
			log.add("INVARIANT  " + violation);
		}
		return new Result(
				safePolicy,
				safeTemplate,
				safeJobs,
				summaries,
				events,
				log,
				safeJobs.size(),
				committed,
				permanentlyFailed + skipped,
				retried,
				Math.max(0, waiting),
				(int)Math.ceil(makespan),
				totalBusy,
				totalIdle,
				safeHeap,
				lowerBound);
	}

	/** max(total work / total throughput, largest unit / fastest node). */
	static double lowerBound(List<WorkUnitSpec> units, List<NodeSpec> nodes)
	{
		double work = 0.0;
		double largest = 0.0;
		double throughput = 0.0;
		double fastest = 0.0;
		for (NodeSpec node : nodes)
		{
			throughput += node.getThroughput();
			fastest = Math.max(fastest, node.getSpeed());
		}
		for (WorkUnitSpec unit : units)
		{
			work += unit.getCost();
			largest = Math.max(largest, unit.getCost());
		}
		if (throughput <= 0.0 || fastest <= 0.0)
		{
			return 0.0;
		}
		return Math.max(work / throughput, largest / fastest);
	}

	private static int tick(double time)
	{
		return (int)Math.round(time);
	}

	private static WorkloadUnit createJob(
			WorkloadTemplate template,
			int index,
			int total,
			int targetHint)
	{
		if (template == WorkloadTemplate.POPULATION_TARGET_SWEEP)
		{
			return new WorkloadUnit(
					"target-" + (index + 1),
					WorkloadUnitType.TARGET_RECOMMENDATION_JOB,
					"Target user " + ((index % targetHint) + 1),
					18 + (index % 9),
					48 + (index % 4) * 16,
					5 + (index % 3),
					120 + index,
					2);
		}
		if (template == WorkloadTemplate.MODEL_HEAVY_RUN)
		{
			WorkloadUnitType type = index % 5 == 0
					? WorkloadUnitType.MODEL_TRAINING_JOB
					: (index % 3 == 0
							? WorkloadUnitType.CANDIDATE_SHARD
							: WorkloadUnitType.SCORING_JOB);
			int cost = type == WorkloadUnitType.MODEL_TRAINING_JOB
					? 90 + (index % 7) * 12
					: 24 + (index % 11) * 4;
			int memory = type == WorkloadUnitType.MODEL_TRAINING_JOB
					? 768 + (index % 5) * 256
					: 96 + (index % 6) * 32;
			return new WorkloadUnit(
					"model-" + (index + 1),
					type,
					type.getLabel() + " " + (index + 1),
					cost,
					memory,
					4 + (index % 5),
					180 + index * 2,
					2);
		}
		if (template == WorkloadTemplate.GRAPH_AND_REPORTING_RUN)
		{
			WorkloadUnitType type;
			if (index > Math.max(0, total - 6))
			{
				type = WorkloadUnitType.GRAPH_REPORTING_JOB;
			}
			else
			{
				type = index % 2 == 0
						? WorkloadUnitType.SCORING_JOB
						: WorkloadUnitType.CANDIDATE_SHARD;
			}
			return new WorkloadUnit(
					"graph-" + (index + 1),
					type,
					type.getLabel() + " " + (index + 1),
					type == WorkloadUnitType.GRAPH_REPORTING_JOB
							? 60 + (index % 5) * 10
							: 14 + (index % 8) * 3,
					type == WorkloadUnitType.GRAPH_REPORTING_JOB
							? 256 + (index % 4) * 64
							: 32 + (index % 5) * 16,
					type == WorkloadUnitType.GRAPH_REPORTING_JOB ? 3 : 6,
					150 + index,
					1);
		}

		WorkloadUnitType type;
		int position = index % 10;
		if (position < 2)
		{
			type = WorkloadUnitType.PREPROCESSING_BATCH;
		}
		else if (position < 4)
		{
			type = WorkloadUnitType.MODEL_TRAINING_JOB;
		}
		else if (position < 7)
		{
			type = WorkloadUnitType.CANDIDATE_SHARD;
		}
		else if (position < 9)
		{
			type = WorkloadUnitType.SCORING_JOB;
		}
		else
		{
			type = WorkloadUnitType.GRAPH_REPORTING_JOB;
		}
		int cost = 18 + (index % 6) * 5;
		int memory = 48 + (index % 8) * 24;
		if (type == WorkloadUnitType.MODEL_TRAINING_JOB)
		{
			cost += 50;
			memory += 384;
		}
		else if (type == WorkloadUnitType.GRAPH_REPORTING_JOB)
		{
			cost += 28;
			memory += 96;
		}
		return new WorkloadUnit(
				"unit-" + (index + 1),
				type,
				type.getLabel() + " " + (index + 1),
				cost,
				memory,
				3 + (index % 7),
				120 + index * 2,
				2);
	}

	private static ArrayList<NodeCapacityProfile> sanitizeProfiles(
			List<NodeCapacityProfile> profiles,
			int heapMb)
	{
		ArrayList<NodeCapacityProfile> safe =
				new ArrayList<NodeCapacityProfile>();
		if (profiles != null)
		{
			for (NodeCapacityProfile profile : profiles)
			{
				if (profile != null)
				{
					safe.add(profile);
				}
			}
		}
		if (safe.isEmpty())
		{
			safe.add(NodeCapacityProfile.defaultProfile(1, 1, heapMb));
		}
		Collections.sort(safe, new Comparator<NodeCapacityProfile>()
		{
			public int compare(
					NodeCapacityProfile first,
					NodeCapacityProfile second)
			{
				return Integer.compare(
						first.getNodeIndex(),
						second.getNodeIndex());
			}
		});
		//Re-number densely so node indexes are always 1..N.
		ArrayList<NodeCapacityProfile> dense = new ArrayList<NodeCapacityProfile>();
		for (int index = 0; index < safe.size(); index++)
		{
			NodeCapacityProfile profile = safe.get(index);
			dense.add(profile.getNodeIndex() == index + 1
					? profile : profile.withNodeIndex(index + 1));
		}
		return dense;
	}

	private static int clampInt(int value, int minimum, int maximum)
	{
		return Math.max(minimum, Math.min(maximum, value));
	}
}
