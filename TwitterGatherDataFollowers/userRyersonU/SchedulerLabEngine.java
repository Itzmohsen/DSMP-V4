package TwitterGatherDataFollowers.userRyersonU;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

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

	enum SchedulingPolicy
	{
		STATIC_EQUAL_SPLIT(
				"Static equal split",
				"baseline: jobs are divided evenly by index"),
		ROUND_ROBIN(
				"Round robin",
				"each next job moves to the next node"),
		WEIGHTED_ROUND_ROBIN(
				"Weighted round robin",
				"strong nodes receive more turns"),
		LEAST_LOADED_NODE(
				"Least loaded node",
				"new work goes to the node with the smallest planned load"),
		FASTEST_NODE_FIRST(
				"Fastest node first",
				"highest capacity node fills first, then others"),
		MEMORY_AWARE_SAFE_WAVE(
				"Memory-aware safe-wave",
				"starts only the jobs that fit the active memory budget"),
		CAPACITY_WEIGHTED(
				"Capacity-weighted",
				"uses CPU, memory, queue, delay, slowdown, and failure hints"),
		QUEUE_LENGTH(
				"Queue-length",
				"chooses the shortest eligible queue"),
		WORK_STEALING(
				"Work stealing",
				"idle nodes pull queued work from busier nodes"),
		DEADLINE_PRIORITY(
				"Deadline / priority",
				"higher priority and earlier deadline jobs are placed first"),
		FAILURE_AWARE_RESCHEDULING(
				"Failure-aware rescheduling",
				"failed jobs retry on more reliable eligible nodes");

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

		long getPlannedCost()
		{
			return plannedCost;
		}

		long getBusyTicks()
		{
			return busyTicks;
		}

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
				int heapMb)
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

	private static final class JobState
	{
		private final WorkloadUnit job;
		private int attempts;

		JobState(WorkloadUnit job)
		{
			this.job = job;
		}
	}

	private static final class RunningJob
	{
		private final JobState state;
		private int remainingTicks;

		RunningJob(JobState state, int remainingTicks)
		{
			this.state = state;
			this.remainingTicks = remainingTicks;
		}
	}

	private static final class NodeRuntime
	{
		private final NodeSummary summary;
		private final ArrayList<JobState> queue = new ArrayList<JobState>();
		private final ArrayList<RunningJob> running =
				new ArrayList<RunningJob>();

		NodeRuntime(NodeSummary summary)
		{
			this.summary = summary;
		}
	}

	private static final class SimulationState
	{
		private final SchedulingPolicy policy;
		private final int heapMb;
		private final ArrayList<NodeRuntime> nodes;
		private final ArrayList<JobState> backlog;
		private final ArrayList<TimelineEvent> events =
				new ArrayList<TimelineEvent>();
		private final ArrayList<String> logLines = new ArrayList<String>();
		private long activeMemoryMb;
		private int roundRobinCursor;
		private int completedJobs;
		private int failedJobs;
		private int retriedJobs;

		SimulationState(
				SchedulingPolicy policy,
				int heapMb,
				ArrayList<NodeRuntime> nodes,
				ArrayList<JobState> backlog)
		{
			this.policy = policy;
			this.heapMb = heapMb;
			this.nodes = nodes;
			this.backlog = backlog;
		}
	}

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
		ArrayList<NodeCapacityProfile> safeProfiles =
				sanitizeProfiles(profiles, heapMb);
		ArrayList<NodeRuntime> runtimes = new ArrayList<NodeRuntime>();
		ArrayList<NodeSummary> summaries = new ArrayList<NodeSummary>();
		for (NodeCapacityProfile profile : safeProfiles)
		{
			NodeSummary summary = new NodeSummary(profile);
			summaries.add(summary);
			runtimes.add(new NodeRuntime(summary));
		}

		ArrayList<JobState> backlog = new ArrayList<JobState>();
		if (jobs != null)
		{
			for (WorkloadUnit job : jobs)
			{
				if (job != null)
				{
					backlog.add(new JobState(job));
				}
			}
		}
		sortBacklog(backlog, safePolicy);

		int safeHeap = Math.max(RuntimeResourceSettings.MIN_HEAP_MB, heapMb);
		SimulationState state =
				new SimulationState(safePolicy, safeHeap, runtimes, backlog);
		log(state, 0, "Scheduler Lab prepared " + backlog.size()
				+ " work units using " + safePolicy.getLabel() + ".");
		fillQueues(state, 0);

		int tick = 0;
		int guard = Math.max(1000, backlog.size() * 200 + 20000);
		while (!isFinished(state) && tick < guard)
		{
			startReadyJobs(state, tick);
			processRunningJobs(state, tick);
			if (safePolicy == SchedulingPolicy.WORK_STEALING)
			{
				stealWork(state, tick);
			}
			fillQueues(state, tick);
			recordBusyIdleTicks(state);
			tick++;
		}

		if (!isFinished(state))
		{
			for (JobState waiting : new ArrayList<JobState>(state.backlog))
			{
				state.failedJobs++;
				event(state, tick, 0, waiting.job, EventType.SKIPPED,
						"Guard limit reached before scheduling.");
			}
			state.backlog.clear();
		}

		long busy = 0L;
		long idle = 0L;
		int waitingJobs = state.backlog.size();
		for (NodeSummary summary : summaries)
		{
			busy += summary.busyTicks;
			idle += summary.idleTicks;
		}
		log(state, tick, "Run completed: " + state.completedJobs
				+ " finished, " + state.failedJobs + " failed, "
				+ state.retriedJobs + " retried.");
		return new Result(
				safePolicy,
				safeTemplate,
				jobs == null ? Collections.<WorkloadUnit>emptyList() : jobs,
				summaries,
				state.events,
				state.logLines,
				jobs == null ? 0 : jobs.size(),
				state.completedJobs,
				state.failedJobs,
				state.retriedJobs,
				waitingJobs,
				tick,
				busy,
				idle,
				safeHeap);
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
		return safe;
	}

	private static void fillQueues(SimulationState state, int tick)
	{
		boolean moved = true;
		while (moved && !state.backlog.isEmpty())
		{
			moved = false;
			JobState job = state.backlog.get(0);
			int nodeIndex = chooseNode(state, job, null);
			if (nodeIndex < 0)
			{
				if (!canAnyNodeEverRun(state, job))
				{
					state.backlog.remove(0);
					state.failedJobs++;
					event(state, tick, 0, job.job, EventType.SKIPPED,
							"No node has enough per-node memory for this job.");
					moved = true;
				}
				continue;
			}
			state.backlog.remove(0);
			assignToNode(state, nodeIndex, job, tick, "queued");
			moved = true;
		}
	}

	private static int chooseNode(
			SimulationState state,
			JobState job,
			Set<Integer> excludedNodes)
	{
		if (state.nodes.isEmpty())
		{
			return -1;
		}
		ArrayList<Integer> candidates = eligibleNodes(
				state,
				job,
				excludedNodes);
		if (candidates.isEmpty())
		{
			return -1;
		}

		if (state.policy == SchedulingPolicy.STATIC_EQUAL_SPLIT)
		{
			int target = Math.floorMod(job.job.getId().hashCode(),
					state.nodes.size());
			if (candidates.contains(Integer.valueOf(target)))
			{
				return target;
			}
		}
		if (state.policy == SchedulingPolicy.ROUND_ROBIN)
		{
			for (int offset = 0; offset < state.nodes.size(); offset++)
			{
				int candidate = (state.roundRobinCursor + offset)
						% state.nodes.size();
				if (candidates.contains(Integer.valueOf(candidate)))
				{
					state.roundRobinCursor = (candidate + 1)
							% state.nodes.size();
					return candidate;
				}
			}
		}
		if (state.policy == SchedulingPolicy.WEIGHTED_ROUND_ROBIN
				|| state.policy == SchedulingPolicy.CAPACITY_WEIGHTED)
		{
			return chooseLowestNormalizedLoad(state, candidates);
		}
		if (state.policy == SchedulingPolicy.LEAST_LOADED_NODE)
		{
			return chooseLowestPlannedLoad(state, candidates);
		}
		if (state.policy == SchedulingPolicy.FASTEST_NODE_FIRST)
		{
			return chooseFastest(state, candidates);
		}
		if (state.policy == SchedulingPolicy.QUEUE_LENGTH)
		{
			return chooseShortestQueue(state, candidates);
		}
		if (state.policy == SchedulingPolicy.DEADLINE_PRIORITY)
		{
			return chooseFastestThenDeadline(state, candidates);
		}
		if (state.policy == SchedulingPolicy.FAILURE_AWARE_RESCHEDULING)
		{
			return chooseMostReliable(state, candidates);
		}
		if (state.policy == SchedulingPolicy.MEMORY_AWARE_SAFE_WAVE)
		{
			return chooseMemoryFit(state, candidates, job);
		}
		return chooseLowestNormalizedLoad(state, candidates);
	}

	private static ArrayList<Integer> eligibleNodes(
			SimulationState state,
			JobState job,
			Set<Integer> excludedNodes)
	{
		ArrayList<Integer> candidates = new ArrayList<Integer>();
		for (int index = 0; index < state.nodes.size(); index++)
		{
			if (excludedNodes != null
					&& excludedNodes.contains(Integer.valueOf(index)))
			{
				continue;
			}
			NodeRuntime node = state.nodes.get(index);
			NodeCapacityProfile profile = node.summary.profile;
			if (node.queue.size() >= profile.getQueueCapacity())
			{
				continue;
			}
			if (job.job.getMemoryMb() > profile.getMemoryLimitMb())
			{
				continue;
			}
			candidates.add(Integer.valueOf(index));
		}
		return candidates;
	}

	private static void assignToNode(
			SimulationState state,
			int nodeIndex,
			JobState job,
			int tick,
			String reason)
	{
		NodeRuntime node = state.nodes.get(nodeIndex);
		node.queue.add(job);
		node.summary.assignedJobs++;
		node.summary.plannedCost += estimatedRuntimeTicks(
				job.job,
				node.summary.profile);
		node.summary.queuePeak = Math.max(
				node.summary.queuePeak,
				node.queue.size());
		event(state, tick, node.summary.getNodeIndex(), job.job,
				EventType.ASSIGNED, reason);
	}

	private static void startReadyJobs(SimulationState state, int tick)
	{
		for (NodeRuntime node : state.nodes)
		{
			NodeCapacityProfile profile = node.summary.profile;
			while (!node.queue.isEmpty()
					&& node.running.size() < profile.getMaxConcurrentJobs())
			{
				JobState job = node.queue.get(0);
				if (state.activeMemoryMb + job.job.getMemoryMb()
						> state.heapMb)
				{
					if (tick == 0 || tick % 25 == 0)
					{
						event(state, tick, profile.getNodeIndex(), job.job,
								EventType.WAITING,
								"waiting for memory-safe wave");
					}
					break;
				}
				node.queue.remove(0);
				state.activeMemoryMb += job.job.getMemoryMb();
				node.running.add(new RunningJob(
						job,
						estimatedRuntimeTicks(job.job, profile)));
				event(state, tick, profile.getNodeIndex(), job.job,
						EventType.STARTED,
						job.job.getType().getDescription());
			}
		}
	}

	private static void processRunningJobs(SimulationState state, int tick)
	{
		for (NodeRuntime node : state.nodes)
		{
			ArrayList<RunningJob> finished = new ArrayList<RunningJob>();
			for (RunningJob running : node.running)
			{
				running.remainingTicks--;
				if (running.remainingTicks <= 0)
				{
					finished.add(running);
				}
			}
			for (RunningJob running : finished)
			{
				node.running.remove(running);
				state.activeMemoryMb = Math.max(
						0L,
						state.activeMemoryMb - running.state.job.getMemoryMb());
				if (shouldFail(node.summary.profile, running.state))
				{
					handleFailure(state, node, running.state, tick);
				}
				else
				{
					node.summary.completedJobs++;
					node.summary.lastFinishTick = tick;
					state.completedJobs++;
					event(state, tick, node.summary.getNodeIndex(),
							running.state.job, EventType.COMPLETED,
							"finished on node "
									+ node.summary.getNodeIndex());
				}
			}
		}
	}

	private static void handleFailure(
			SimulationState state,
			NodeRuntime node,
			JobState job,
			int tick)
	{
		node.summary.failedJobs++;
		event(state, tick, node.summary.getNodeIndex(), job.job,
				EventType.FAILED,
				"simulated node failure");
		if (job.attempts < job.job.getMaxRetries())
		{
			job.attempts++;
			node.summary.retriedJobs++;
			state.retriedJobs++;
			HashSet<Integer> excluded = new HashSet<Integer>();
			if (state.policy == SchedulingPolicy.FAILURE_AWARE_RESCHEDULING)
			{
				excluded.add(Integer.valueOf(
						indexOfNode(state, node.summary.getNodeIndex())));
			}
			int next = chooseNode(state, job, excluded);
			if (next < 0)
			{
				state.backlog.add(job);
				event(state, tick, node.summary.getNodeIndex(), job.job,
						EventType.RETRIED,
						"retry waiting for any eligible node");
			}
			else
			{
				assignToNode(state, next, job, tick, "retry");
				event(state, tick,
						state.nodes.get(next).summary.getNodeIndex(),
						job.job,
						EventType.RETRIED,
						"rescheduled after failure");
			}
		}
		else
		{
			state.failedJobs++;
		}
	}

	private static void stealWork(SimulationState state, int tick)
	{
		for (NodeRuntime idle : state.nodes)
		{
			if (!idle.running.isEmpty() || !idle.queue.isEmpty())
			{
				continue;
			}
			NodeRuntime donor = null;
			for (NodeRuntime node : state.nodes)
			{
				if (node == idle || node.queue.size() < 2)
				{
					continue;
				}
				if (donor == null || node.queue.size() > donor.queue.size())
				{
					donor = node;
				}
			}
			if (donor == null)
			{
				continue;
			}
			JobState job = donor.queue.remove(donor.queue.size() - 1);
			if (job.job.getMemoryMb() > idle.summary.profile.getMemoryLimitMb())
			{
				donor.queue.add(job);
				continue;
			}
			idle.queue.add(job);
			idle.summary.stolenJobs++;
			event(state, tick, idle.summary.getNodeIndex(), job.job,
					EventType.STOLEN,
					"stole from node " + donor.summary.getNodeIndex());
		}
	}

	private static void recordBusyIdleTicks(SimulationState state)
	{
		for (NodeRuntime node : state.nodes)
		{
			if (node.running.isEmpty())
			{
				node.summary.idleTicks++;
			}
			else
			{
				node.summary.busyTicks++;
			}
		}
	}

	private static boolean isFinished(SimulationState state)
	{
		if (!state.backlog.isEmpty())
		{
			return false;
		}
		for (NodeRuntime node : state.nodes)
		{
			if (!node.queue.isEmpty() || !node.running.isEmpty())
			{
				return false;
			}
		}
		return true;
	}

	private static boolean canAnyNodeEverRun(
			SimulationState state,
			JobState job)
	{
		for (NodeRuntime node : state.nodes)
		{
			if (job.job.getMemoryMb()
					<= node.summary.profile.getMemoryLimitMb())
			{
				return true;
			}
		}
		return false;
	}

	private static int chooseLowestNormalizedLoad(
			SimulationState state,
			ArrayList<Integer> candidates)
	{
		int best = candidates.get(0).intValue();
		double bestScore = Double.MAX_VALUE;
		for (Integer candidateValue : candidates)
		{
			int candidate = candidateValue.intValue();
			NodeRuntime node = state.nodes.get(candidate);
			double score = currentLoad(node)
					/ Math.max(0.05, NodeCapacityScheduler.schedulingWeight(
							node.summary.profile));
			if (score < bestScore)
			{
				bestScore = score;
				best = candidate;
			}
		}
		return best;
	}

	private static int chooseLowestPlannedLoad(
			SimulationState state,
			ArrayList<Integer> candidates)
	{
		int best = candidates.get(0).intValue();
		long bestScore = Long.MAX_VALUE;
		for (Integer candidateValue : candidates)
		{
			int candidate = candidateValue.intValue();
			long score = currentLoad(state.nodes.get(candidate));
			if (score < bestScore)
			{
				bestScore = score;
				best = candidate;
			}
		}
		return best;
	}

	private static int chooseFastest(
			SimulationState state,
			ArrayList<Integer> candidates)
	{
		int best = candidates.get(0).intValue();
		double bestWeight = -1.0;
		for (Integer candidateValue : candidates)
		{
			int candidate = candidateValue.intValue();
			double weight = NodeCapacityScheduler.schedulingWeight(
					state.nodes.get(candidate).summary.profile);
			if (weight > bestWeight)
			{
				bestWeight = weight;
				best = candidate;
			}
		}
		return best;
	}

	private static int chooseShortestQueue(
			SimulationState state,
			ArrayList<Integer> candidates)
	{
		int best = candidates.get(0).intValue();
		int bestQueue = Integer.MAX_VALUE;
		for (Integer candidateValue : candidates)
		{
			int candidate = candidateValue.intValue();
			NodeRuntime node = state.nodes.get(candidate);
			int queue = node.queue.size() + node.running.size();
			if (queue < bestQueue)
			{
				bestQueue = queue;
				best = candidate;
			}
		}
		return best;
	}

	private static int chooseFastestThenDeadline(
			SimulationState state,
			ArrayList<Integer> candidates)
	{
		int fastest = chooseFastest(state, candidates);
		NodeRuntime node = state.nodes.get(fastest);
		if (node.queue.size() < node.summary.profile.getQueueCapacity())
		{
			return fastest;
		}
		return chooseLowestNormalizedLoad(state, candidates);
	}

	private static int chooseMostReliable(
			SimulationState state,
			ArrayList<Integer> candidates)
	{
		int best = candidates.get(0).intValue();
		double bestScore = Double.MAX_VALUE;
		for (Integer candidateValue : candidates)
		{
			int candidate = candidateValue.intValue();
			NodeCapacityProfile profile =
					state.nodes.get(candidate).summary.profile;
			double score = profile.getFailureProbabilityPercent()
					+ (profile.isSlowdownSimulationEnabled()
							? profile.getSlowdownMultiplier() * 4.0 : 0.0)
					+ profile.getNetworkDelayMs() / 1000.0
					+ currentLoad(state.nodes.get(candidate))
							/ Math.max(0.05,
									NodeCapacityScheduler.schedulingWeight(
											profile));
			if (score < bestScore)
			{
				bestScore = score;
				best = candidate;
			}
		}
		return best;
	}

	private static int chooseMemoryFit(
			SimulationState state,
			ArrayList<Integer> candidates,
			JobState job)
	{
		int best = candidates.get(0).intValue();
		long bestRemainder = Long.MAX_VALUE;
		for (Integer candidateValue : candidates)
		{
			int candidate = candidateValue.intValue();
			NodeCapacityProfile profile =
					state.nodes.get(candidate).summary.profile;
			long remainder = profile.getMemoryLimitMb()
					- job.job.getMemoryMb();
			if (remainder >= 0 && remainder < bestRemainder)
			{
				bestRemainder = remainder;
				best = candidate;
			}
		}
		return best;
	}

	private static long currentLoad(NodeRuntime node)
	{
		long load = node.summary.plannedCost;
		for (RunningJob running : node.running)
		{
			load += running.remainingTicks;
		}
		return load;
	}

	private static int estimatedRuntimeTicks(
			WorkloadUnit job,
			NodeCapacityProfile profile)
	{
		double cpu = Math.max(0.10, profile.getCpuMultiplier());
		double cost = job.getCostUnits() / cpu;
		if (profile.isSlowdownSimulationEnabled())
		{
			cost *= Math.max(1.0, profile.getSlowdownMultiplier());
		}
		cost += Math.max(0, profile.getNetworkDelayMs()) / 100.0;
		return Math.max(1, (int)Math.ceil(cost));
	}

	private static boolean shouldFail(
			NodeCapacityProfile profile,
			JobState state)
	{
		if (!profile.isFailureSimulationEnabled()
				|| profile.getFailureProbabilityPercent() <= 0)
		{
			return false;
		}
		String key = profile.getNodeIndex()
				+ "|" + state.job.getId()
				+ "|" + state.attempts;
		int bucket = Math.floorMod(key.hashCode(), 100);
		return bucket < profile.getFailureProbabilityPercent();
	}

	private static int indexOfNode(SimulationState state, int nodeIndex)
	{
		for (int index = 0; index < state.nodes.size(); index++)
		{
			if (state.nodes.get(index).summary.getNodeIndex() == nodeIndex)
			{
				return index;
			}
		}
		return -1;
	}

	private static void sortBacklog(
			ArrayList<JobState> backlog,
			SchedulingPolicy policy)
	{
		if (policy == SchedulingPolicy.DEADLINE_PRIORITY)
		{
			Collections.sort(backlog, new Comparator<JobState>()
			{
				public int compare(JobState first, JobState second)
				{
					return deadlineCompare(first.job, second.job);
				}
			});
		}
	}

	private static int deadlineCompare(
			WorkloadUnit first,
			WorkloadUnit second)
	{
		int priority = Integer.compare(second.getPriority(), first.getPriority());
		if (priority != 0)
		{
			return priority;
		}
		return Integer.compare(first.getDeadlineTick(), second.getDeadlineTick());
	}

	private static void event(
			SimulationState state,
			int tick,
			int nodeIndex,
			WorkloadUnit job,
			EventType type,
			String detail)
	{
		state.events.add(new TimelineEvent(
				tick,
				nodeIndex,
				job == null ? "" : job.getId(),
				job == null ? WorkloadUnitType.TARGET_RECOMMENDATION_JOB
						: job.getType(),
				type,
				detail));
		if (type == EventType.FAILED
				|| type == EventType.RETRIED
				|| type == EventType.SKIPPED)
		{
			log(state, tick, "Node " + nodeIndex + " " + type
					+ " " + (job == null ? "job" : job.getId())
					+ ": " + detail);
		}
	}

	private static void log(
			SimulationState state,
			int tick,
			String message)
	{
		state.logLines.add(String.format(
				Locale.US,
				"t=%04d  %s",
				Integer.valueOf(tick),
				message));
	}

	private static int clampInt(int value, int minimum, int maximum)
	{
		return Math.max(minimum, Math.min(maximum, value));
	}
}
