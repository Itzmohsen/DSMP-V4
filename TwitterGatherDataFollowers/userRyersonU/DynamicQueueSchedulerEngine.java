package TwitterGatherDataFollowers.userRyersonU;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The Dynamic Queue Lab: a real-time run of the live work dispatcher.
 *
 * Each node lane is a real thread that claims units from a {@link WorkPhase}
 * over the same {@link WorkDispatcher} the live simulator uses. A unit of cost
 * c represents c * millisecondsPerCostUnit of reference work; a node of speed s
 * spends that divided by s, exactly as a live node's measured work is dilated.
 * Link delays, memory admission, queue capacity and injected failures behave
 * as in a live run.
 */
final class DynamicQueueSchedulerEngine
{
	interface Listener
	{
		void onSnapshot(Snapshot snapshot);
		void onFinished(Result result);
		void onFailed(String message, Throwable throwable);
	}

	static final class Config
	{
		private final List<NodeCapacityProfile> profiles;
		private final SchedulerLabEngine.SchedulingPolicy policy;
		private final SchedulerLabEngine.WorkloadTemplate template;
		private final int workUnitCount;
		private final int heapMb;
		private final int millisecondsPerCostUnit;
		private final int maxRetries;

		Config(
				List<NodeCapacityProfile> profiles,
				SchedulerLabEngine.SchedulingPolicy policy,
				SchedulerLabEngine.WorkloadTemplate template,
				int workUnitCount,
				int heapMb,
				int millisecondsPerCostUnit,
				int maxRetries)
		{
			this.profiles = sanitizeProfiles(profiles, heapMb);
			this.policy = policy == null
					? SchedulerLabEngine.SchedulingPolicy.CAPACITY_WEIGHTED
					: policy;
			this.template = template == null
					? SchedulerLabEngine.WorkloadTemplate.POPULATION_TARGET_SWEEP
					: template;
			this.workUnitCount = clamp(workUnitCount, 1, 5000);
			this.heapMb = Math.max(RuntimeResourceSettings.MIN_HEAP_MB, heapMb);
			this.millisecondsPerCostUnit =
					clamp(millisecondsPerCostUnit, 1, 200);
			this.maxRetries = clamp(maxRetries, 0, 10);
		}
	}

	static final class Controller
	{
		private final AtomicBoolean cancelled;
		private final Thread supervisor;
		private volatile WorkPhase phase;

		private Controller(AtomicBoolean cancelled, Thread supervisor)
		{
			this.cancelled = cancelled;
			this.supervisor = supervisor;
		}

		void cancel()
		{
			cancelled.set(true);
			WorkPhase current = phase;
			if (current != null)
			{
				current.close("cancelled by user");
			}
			if (supervisor != null)
			{
				supervisor.interrupt();
			}
		}
	}

	static final class NodeSnapshot
	{
		private final NodeCapacityProfile profile;
		private final int assignedJobs;
		private final int completedJobs;
		private final int failedJobs;
		private final int retriedJobs;
		private final int runningJobs;
		private final int queuedJobs;
		private final long busyMillis;
		private final long idleMillis;
		private final String currentState;
		private final String currentJob;

		private NodeSnapshot(NodeRuntime runtime, WorkDispatcher.NodeStats stats,
				long elapsedMillis)
		{
			synchronized (runtime)
			{
				this.profile = runtime.profile;
				this.assignedJobs = runtime.assignedJobs;
				this.completedJobs = runtime.completedJobs;
				this.failedJobs = runtime.failedJobs;
				this.retriedJobs = runtime.retriedJobs;
				this.busyMillis = runtime.busyMillis;
				this.currentState = runtime.runningJobs > 0 ? "running"
						: (runtime.finished ? "idle" : "waiting");
				this.currentJob = runtime.currentJob;
				this.runningJobs = runtime.runningJobs;
			}
			this.queuedJobs = stats == null ? 0 : stats.getReserved();
			long laneMillis = elapsedMillis * Math.max(1, profile.getMaxConcurrentJobs());
			this.idleMillis = Math.max(0L, laneMillis - busyMillis);
		}

		NodeCapacityProfile getProfile()
		{
			return profile;
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

		int getRunningJobs()
		{
			return runningJobs;
		}

		int getQueuedJobs()
		{
			return queuedJobs;
		}

		long getBusyMillis()
		{
			return busyMillis;
		}

		long getIdleMillis()
		{
			return idleMillis;
		}

		String getCurrentState()
		{
			return currentState;
		}

		String getCurrentJob()
		{
			return currentJob;
		}
	}

	static final class Segment
	{
		private final int nodeIndex;
		private final int laneIndex;
		private final String jobId;
		private final SchedulerLabEngine.WorkloadUnitType type;
		private final long startMillis;
		private final long endMillis;
		private final boolean failed;
		private final int attempt;

		Segment(
				int nodeIndex,
				int laneIndex,
				String jobId,
				SchedulerLabEngine.WorkloadUnitType type,
				long startMillis,
				long endMillis,
				boolean failed,
				int attempt)
		{
			this.nodeIndex = nodeIndex;
			this.laneIndex = laneIndex;
			this.jobId = jobId == null ? "" : jobId;
			this.type = type == null
					? SchedulerLabEngine.WorkloadUnitType.SCORING_JOB
					: type;
			this.startMillis = Math.max(0L, startMillis);
			this.endMillis = Math.max(this.startMillis, endMillis);
			this.failed = failed;
			this.attempt = Math.max(0, attempt);
		}

		int getNodeIndex()
		{
			return nodeIndex;
		}

		int getLaneIndex()
		{
			return laneIndex;
		}

		String getJobId()
		{
			return jobId;
		}

		SchedulerLabEngine.WorkloadUnitType getType()
		{
			return type;
		}

		long getStartMillis()
		{
			return startMillis;
		}

		long getEndMillis()
		{
			return endMillis;
		}

		boolean isFailed()
		{
			return failed;
		}

		int getAttempt()
		{
			return attempt;
		}
	}

	static final class Snapshot
	{
		private final SchedulerLabEngine.SchedulingPolicy policy;
		private final SchedulerLabEngine.WorkloadTemplate template;
		private final List<NodeSnapshot> nodes;
		private final List<Segment> segments;
		private final List<String> logLines;
		private final int totalJobs;
		private final int completedJobs;
		private final int failedJobs;
		private final int retriedJobs;
		private final int waitingJobs;
		private final int runningJobs;
		private final long elapsedMillis;
		private final boolean finished;
		private final boolean cancelled;

		private Snapshot(RunState state, boolean finished, boolean cancelled)
		{
			long elapsed = state.elapsedMillis();
			List<WorkDispatcher.NodeStats> stats = state.dispatcher.nodeStats();
			ArrayList<NodeSnapshot> copies = new ArrayList<NodeSnapshot>();
			int running = 0;
			for (int index = 0; index < state.nodes.size(); index++)
			{
				NodeSnapshot snapshot = new NodeSnapshot(
						state.nodes.get(index),
						index < stats.size() ? stats.get(index) : null,
						elapsed);
				copies.add(snapshot);
				running += snapshot.getRunningJobs();
			}
			synchronized (state)
			{
				this.policy = state.policy;
				this.template = state.template;
				this.segments = Collections.unmodifiableList(
						new ArrayList<Segment>(state.segments));
				this.logLines = Collections.unmodifiableList(
						new ArrayList<String>(state.logLines));
				this.totalJobs = state.totalJobs;
				this.completedJobs = state.completedJobs;
				this.failedJobs = state.failedJobs;
				this.retriedJobs = state.retriedJobs;
			}
			this.waitingJobs = state.dispatcher.count(WorkDispatcher.UnitState.BACKLOG)
					+ state.dispatcher.count(WorkDispatcher.UnitState.RESERVED);
			this.runningJobs = running;
			this.elapsedMillis = elapsed;
			this.finished = finished;
			this.cancelled = cancelled;
			this.nodes = Collections.unmodifiableList(copies);
		}

		SchedulerLabEngine.SchedulingPolicy getPolicy()
		{
			return policy;
		}

		SchedulerLabEngine.WorkloadTemplate getTemplate()
		{
			return template;
		}

		List<NodeSnapshot> getNodes()
		{
			return nodes;
		}

		List<Segment> getSegments()
		{
			return segments;
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

		int getRunningJobs()
		{
			return runningJobs;
		}

		long getElapsedMillis()
		{
			return elapsedMillis;
		}

		boolean isFinished()
		{
			return finished;
		}

		boolean isCancelled()
		{
			return cancelled;
		}

		double utilization()
		{
			long busy = 0L;
			long idle = 0L;
			for (NodeSnapshot node : nodes)
			{
				busy += node.getBusyMillis();
				idle += node.getIdleMillis();
			}
			long total = busy + idle;
			return total <= 0L ? 0.0 : busy / (double)total;
		}
	}

	static final class Result
	{
		private final Snapshot finalSnapshot;

		Result(Snapshot finalSnapshot)
		{
			this.finalSnapshot = finalSnapshot;
		}

		Snapshot getFinalSnapshot()
		{
			return finalSnapshot;
		}
	}

	private static final class NodeRuntime
	{
		private final NodeCapacityProfile profile;
		private final NodeSpec spec;
		private int assignedJobs;
		private int completedJobs;
		private int failedJobs;
		private int retriedJobs;
		private int runningJobs;
		private long busyMillis;
		private boolean finished;
		private String currentJob = "";

		private NodeRuntime(NodeCapacityProfile profile, NodeSpec spec)
		{
			this.profile = profile;
			this.spec = spec;
		}
	}

	private static final class RunState
	{
		private final SchedulerLabEngine.SchedulingPolicy policy;
		private final SchedulerLabEngine.WorkloadTemplate template;
		private final List<NodeRuntime> nodes;
		private final WorkDispatcher dispatcher;
		private final List<SchedulerLabEngine.WorkloadUnit> jobs;
		private final int totalJobs;
		private final int msPerCost;
		private final long startNanos = System.nanoTime();
		private final ArrayList<Segment> segments = new ArrayList<Segment>();
		private final ArrayList<String> logLines = new ArrayList<String>();
		private int completedJobs;
		private int failedJobs;
		private int retriedJobs;

		private RunState(Config config, List<NodeRuntime> nodes,
				WorkDispatcher dispatcher, List<SchedulerLabEngine.WorkloadUnit> jobs,
				int totalJobs)
		{
			this.policy = config.policy;
			this.template = config.template;
			this.nodes = nodes;
			this.dispatcher = dispatcher;
			this.jobs = jobs;
			this.totalJobs = totalJobs;
			this.msPerCost = config.millisecondsPerCostUnit;
		}

		long elapsedMillis()
		{
			return Math.max(0L, (System.nanoTime() - startNanos) / 1000000L);
		}

		synchronized void log(String line)
		{
			logLines.add(String.format(Locale.US, "t=%06d ms  %s",
					Long.valueOf(elapsedMillis()), line == null ? "" : line));
			while (logLines.size() > 500)
			{
				logLines.remove(0);
			}
		}
	}

	private DynamicQueueSchedulerEngine()
	{
	}

	static Controller runAsync(Config config, final Listener listener)
	{
		final AtomicBoolean cancelled = new AtomicBoolean(false);
		final Config safeConfig = config == null
				? new Config(
						NodeCapacityRegistry.snapshot(),
						SchedulerLabEngine.SchedulingPolicy.CAPACITY_WEIGHTED,
						SchedulerLabEngine.WorkloadTemplate.POPULATION_TARGET_SWEEP,
						120,
						RuntimeResourceSettings.currentMaxHeapMb(),
						8,
						2)
				: config;
		final Controller[] holder = new Controller[1];
		Thread supervisor = new Thread(new Runnable()
		{
			public void run()
			{
				runSupervisor(safeConfig, cancelled, listener, holder);
			}
		}, "DynamicQueueScheduler-supervisor");
		supervisor.setDaemon(true);
		Controller controller = new Controller(cancelled, supervisor);
		holder[0] = controller;
		supervisor.start();
		return controller;
	}

	static Result runBlocking(Config config) throws InterruptedException
	{
		final Object lock = new Object();
		final Result[] result = new Result[1];
		final Throwable[] failure = new Throwable[1];
		Controller controller = runAsync(config, new Listener()
		{
			public void onSnapshot(Snapshot snapshot)
			{
			}

			public void onFinished(Result finished)
			{
				synchronized (lock)
				{
					result[0] = finished;
					lock.notifyAll();
				}
			}

			public void onFailed(String message, Throwable throwable)
			{
				synchronized (lock)
				{
					failure[0] = throwable == null
							? new IllegalStateException(message)
							: throwable;
					lock.notifyAll();
				}
			}
		});
		synchronized (lock)
		{
			while (result[0] == null && failure[0] == null)
			{
				lock.wait(250L);
			}
		}
		controller.cancel();
		if (failure[0] != null)
		{
			throw new IllegalStateException("Dynamic queue run failed.", failure[0]);
		}
		return result[0];
	}

	private static void runSupervisor(
			Config config,
			AtomicBoolean cancelled,
			Listener listener,
			Controller[] holder)
	{
		try
		{
			List<NodeSpec> specs = NodeSpec.fromProfiles(config.profiles, null, true);
			ArrayList<NodeRuntime> nodes = new ArrayList<NodeRuntime>();
			int largestMemory = 0;
			boolean faults = false;
			for (int index = 0; index < specs.size(); index++)
			{
				nodes.add(new NodeRuntime(config.profiles.get(index), specs.get(index)));
				largestMemory = Math.max(largestMemory, specs.get(index).getMemoryMb());
				faults |= specs.get(index).getFailureProbability() > 0.0;
			}
			List<SchedulerLabEngine.WorkloadUnit> all = SchedulerLabEngine.createWorkload(
					config.template, config.workUnitCount, config.workUnitCount);
			ArrayList<SchedulerLabEngine.WorkloadUnit> runnable =
					new ArrayList<SchedulerLabEngine.WorkloadUnit>();
			ArrayList<WorkUnitSpec> units = new ArrayList<WorkUnitSpec>();
			ArrayList<String> skipped = new ArrayList<String>();
			for (int index = 0; index < all.size(); index++)
			{
				SchedulerLabEngine.WorkloadUnit job = all.get(index);
				if (job.getMemoryMb() > largestMemory || job.getMemoryMb() > config.heapMb)
				{
					skipped.add(job.getId() + " needs " + job.getMemoryMb()
							+ " MB; no node budget or heap can hold it.");
					continue;
				}
				runnable.add(job);
				int retries = Math.min(job.getMaxRetries(), config.maxRetries);
				units.add(new WorkUnitSpec(
						runnable.size(),
						String.format(Locale.US, "%06d:%s", index + 1, job.getId()),
						job.getCostUnits(),
						job.getMemoryMb(),
						job.getPriority(),
						job.getDeadlineTick(),
						0,
						null,
						retries + 1));
			}
			WorkDispatcher dispatcher = new WorkDispatcher(
					"dynamic-queue-lab",
					config.policy,
					specs,
					units,
					new WorkDispatcher.Options()
							.faultInjection(faults, SchedulerLabEngine.FAULT_SEED)
							.globalMemoryBudgetMb(config.heapMb)
							.msPerTimeUnit(config.millisecondsPerCostUnit)
							.maxRetainedEvents(2000));
			final RunState state = new RunState(config, nodes, dispatcher, runnable, all.size());
			final WorkPhase phase = new WorkPhase(dispatcher, null, 0L);
			holder[0].phase = phase;
			synchronized (state)
			{
				state.failedJobs = skipped.size();
			}
			state.log("prepared " + units.size() + " unit(s) with policy "
					+ config.policy.getLabel() + (faults ? "; failure injection on" : "") + ".");
			for (String line : skipped)
			{
				state.log("skipped " + line);
			}
			emit(listener, state, false, cancelled.get());

			int workers = 0;
			for (NodeSpec spec : specs)
			{
				workers += spec.getLanes();
			}
			final CountDownLatch latch = new CountDownLatch(workers);
			final AtomicBoolean cancelledFlag = cancelled;
			final Listener safeListener = listener;
			for (int index = 0; index < nodes.size(); index++)
			{
				final NodeRuntime node = nodes.get(index);
				for (int lane = 0; lane < node.spec.getLanes(); lane++)
				{
					final int laneIndex = lane;
					Thread worker = new Thread(new Runnable()
					{
						public void run()
						{
							try
							{
								runLane(node, laneIndex, state, phase, cancelledFlag, safeListener);
							}
							finally
							{
								latch.countDown();
							}
						}
					}, "DynamicQueue-node" + node.spec.getIndex() + "-lane" + (lane + 1));
					worker.setDaemon(true);
					worker.start();
				}
			}
			while (!cancelled.get() && latch.getCount() > 0L)
			{
				emit(listener, state, false, false);
				try
				{
					Thread.sleep(180L);
				}
				catch (InterruptedException interrupted)
				{
					if (cancelled.get())
					{
						break;
					}
				}
			}
			if (cancelled.get())
			{
				phase.close("cancelled by user");
				state.log("cancelled by user; committed work is kept in this snapshot.");
			}
			latch.await();
			if (dispatcher.hasFailed())
			{
				state.log(dispatcher.getFailureReason());
			}
			Snapshot finalSnapshot = new Snapshot(state, true, cancelled.get());
			if (listener != null)
			{
				listener.onSnapshot(finalSnapshot);
				listener.onFinished(new Result(finalSnapshot));
			}
		}
		catch (Throwable throwable)
		{
			if (listener != null)
			{
				listener.onFailed(throwable.getMessage(), throwable);
			}
		}
	}

	private static void runLane(
			NodeRuntime node,
			int lane,
			RunState state,
			WorkPhase phase,
			AtomicBoolean cancelled,
			Listener listener)
	{
		try
		{
			while (!cancelled.get())
			{
				WorkDispatcher.Assignment assignment;
				try
				{
					assignment = phase.claim(node.spec.getIndex(), lane);
				}
				catch (IllegalStateException closedOrFailed)
				{
					return;
				}
				catch (InterruptedException interrupted)
				{
					Thread.currentThread().interrupt();
					return;
				}
				if (assignment == null)
				{
					return;
				}
				SchedulerLabEngine.WorkloadUnit job =
						state.jobs.get(assignment.getUnitId() - 1);
				synchronized (node)
				{
					node.assignedJobs++;
					node.runningJobs++;
					node.currentJob = job.getId();
				}
				//Wait until the unit's descriptor and data have arrived (phase clock, ms).
				sleep((long)Math.max(0L, Math.round(assignment.getReadyAt() - phase.now())), cancelled);
				long start = state.elapsedMillis();
				//Reference work c * ms, spent at this node's speed.
				double millis = assignment.getUnit().getCost() * state.msPerCost
						/ node.spec.getSpeed();
				sleep((long)Math.round(millis), cancelled);
				if (cancelled.get())
				{
					phase.withdraw(node.spec.getIndex(), lane);
					synchronized (node)
					{
						node.runningJobs = Math.max(0, node.runningJobs - 1);
					}
					return;
				}
				WorkDispatcher.Outcome outcome = phase.complete(assignment);
				long end = state.elapsedMillis();
				boolean lost = outcome == WorkDispatcher.Outcome.INJECTED_FAILURE
						|| outcome == WorkDispatcher.Outcome.FINAL_FAILURE;
				synchronized (node)
				{
					node.runningJobs = Math.max(0, node.runningJobs - 1);
					node.busyMillis += Math.max(1L, end - start);
					if (outcome == WorkDispatcher.Outcome.COMMITTED)
					{
						node.completedJobs++;
					}
					else if (lost)
					{
						node.failedJobs++;
						if (outcome == WorkDispatcher.Outcome.INJECTED_FAILURE)
						{
							node.retriedJobs++;
						}
					}
				}
				synchronized (state)
				{
					state.segments.add(new Segment(node.spec.getIndex(), lane, job.getId(),
							job.getType(), start, end, lost, assignment.getExecution()));
					if (outcome == WorkDispatcher.Outcome.COMMITTED)
					{
						state.completedJobs++;
					}
					else if (outcome == WorkDispatcher.Outcome.INJECTED_FAILURE)
					{
						state.retriedJobs++;
					}
					else if (outcome == WorkDispatcher.Outcome.FINAL_FAILURE)
					{
						state.failedJobs++;
					}
				}
				state.log("node " + node.spec.getIndex() + " "
						+ outcomeLabel(outcome) + " " + job.getId()
						+ " (execution " + assignment.getExecution() + ").");
				emit(listener, state, false, false);
			}
		}
		finally
		{
			synchronized (node)
			{
				node.finished = true;
			}
		}
	}

	private static String outcomeLabel(WorkDispatcher.Outcome outcome)
	{
		switch (outcome)
		{
			case COMMITTED:
				return "completed";
			case INJECTED_FAILURE:
				return "lost to an injected failure, retry queued:";
			case FINAL_FAILURE:
				return "failed permanently:";
			case STALE:
				return "discarded a revoked execution of";
			default:
				return "finished";
		}
	}

	private static void emit(Listener listener, RunState state, boolean finished,
			boolean cancelled)
	{
		if (listener != null)
		{
			listener.onSnapshot(new Snapshot(state, finished, cancelled));
		}
	}

	private static void sleep(long millis, AtomicBoolean cancelled)
	{
		long remaining = Math.max(0L, millis);
		while (remaining > 0L && !cancelled.get())
		{
			long chunk = Math.min(remaining, 50L);
			long before = System.nanoTime();
			try
			{
				Thread.sleep(chunk);
			}
			catch (InterruptedException interrupted)
			{
				Thread.currentThread().interrupt();
				return;
			}
			remaining -= Math.max(1L, (System.nanoTime() - before) / 1000000L);
		}
	}

	private static ArrayList<NodeCapacityProfile> sanitizeProfiles(
			List<NodeCapacityProfile> profiles,
			int heapMb)
	{
		ArrayList<NodeCapacityProfile> safe = new ArrayList<NodeCapacityProfile>();
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
		Collections.sort(safe, new java.util.Comparator<NodeCapacityProfile>()
		{
			public int compare(NodeCapacityProfile first, NodeCapacityProfile second)
			{
				return Integer.compare(first.getNodeIndex(), second.getNodeIndex());
			}
		});
		ArrayList<NodeCapacityProfile> dense = new ArrayList<NodeCapacityProfile>();
		for (int index = 0; index < safe.size(); index++)
		{
			NodeCapacityProfile profile = safe.get(index);
			dense.add(profile.getNodeIndex() == index + 1
					? profile : profile.withNodeIndex(index + 1));
		}
		return dense;
	}

	private static int clamp(int value, int minimum, int maximum)
	{
		return Math.max(minimum, Math.min(maximum, value));
	}
}
