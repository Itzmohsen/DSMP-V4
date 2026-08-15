package TwitterGatherDataFollowers.userRyersonU;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

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

		private Controller(AtomicBoolean cancelled, Thread supervisor)
		{
			this.cancelled = cancelled;
			this.supervisor = supervisor;
		}

		void cancel()
		{
			cancelled.set(true);
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

		NodeSnapshot(NodeRuntime runtime, long elapsedMillis)
		{
			synchronized (runtime.lock)
			{
				this.profile = runtime.profile;
				this.assignedJobs = runtime.assignedJobs;
				this.completedJobs = runtime.completedJobs;
				this.failedJobs = runtime.failedJobs;
				this.retriedJobs = runtime.retriedJobs;
				this.runningJobs = runtime.runningJobs;
				this.queuedJobs = runtime.queuedJobs;
				this.busyMillis = runtime.busyMillis;
				long laneMillis = elapsedMillis
						* Math.max(1, runtime.activeWorkerSlots);
				this.idleMillis = Math.max(0L, laneMillis - runtime.busyMillis);
				this.currentState = runtime.currentState;
				this.currentJob = runtime.currentJob;
			}
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

		Snapshot(SharedRunState state, boolean finished, boolean cancelled)
		{
			long elapsed = state.elapsedMillis();
			ArrayList<NodeSnapshot> nodeCopies =
					new ArrayList<NodeSnapshot>();
			int nodeWaiting = 0;
			int nodeRunning = 0;
			for (NodeRuntime runtime : state.nodes)
			{
				NodeSnapshot snapshot = new NodeSnapshot(runtime, elapsed);
				nodeCopies.add(snapshot);
				nodeWaiting += snapshot.getQueuedJobs();
				nodeRunning += snapshot.getRunningJobs();
			}
			synchronized (state.lock)
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
				this.waitingJobs = state.sharedQueue.size() + nodeWaiting;
				this.runningJobs = nodeRunning;
				this.elapsedMillis = elapsed;
				this.finished = finished;
				this.cancelled = cancelled;
			}
			this.nodes = Collections.unmodifiableList(nodeCopies);
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

	private static final class WorkState
	{
		private final SchedulerLabEngine.WorkloadUnit unit;
		private final HashSet<Integer> failedNodeIndexes =
				new HashSet<Integer>();
		private int attempts;

		WorkState(SchedulerLabEngine.WorkloadUnit unit)
		{
			this.unit = unit;
		}
	}

	private static final class NodeRuntime
	{
		private final Object lock = new Object();
		private final NodeCapacityProfile profile;
		private final ArrayDeque<WorkState> localQueue =
				new ArrayDeque<WorkState>();
		private int assignedJobs;
		private int completedJobs;
		private int failedJobs;
		private int retriedJobs;
		private int runningJobs;
		private int queuedJobs;
		private int activeWorkerSlots;
		private long busyMillis;
		private String currentState = "idle";
		private String currentJob = "";

		NodeRuntime(NodeCapacityProfile profile)
		{
			this.profile = profile;
			this.activeWorkerSlots = Math.max(
					1,
					Math.min(4, profile.getMaxConcurrentJobs()));
		}
	}

	private static final class SharedRunState
	{
		private final Object lock = new Object();
		private final List<NodeRuntime> nodes;
		private final ArrayDeque<WorkState> sharedQueue =
				new ArrayDeque<WorkState>();
		private final ArrayList<Segment> segments = new ArrayList<Segment>();
		private final ArrayList<String> logLines = new ArrayList<String>();
		private final SchedulerLabEngine.SchedulingPolicy policy;
		private final SchedulerLabEngine.WorkloadTemplate template;
		private final int totalJobs;
		private final int heapMb;
		private final int millisecondsPerCostUnit;
		private final int maxRetries;
		private final AtomicBoolean cancelled;
		private long activeMemoryMb;
		private long startNano;
		private int completedJobs;
		private int failedJobs;
		private int retriedJobs;

		SharedRunState(
				List<NodeRuntime> nodes,
				SchedulerLabEngine.SchedulingPolicy policy,
				SchedulerLabEngine.WorkloadTemplate template,
				int totalJobs,
				int heapMb,
				int millisecondsPerCostUnit,
				int maxRetries,
				AtomicBoolean cancelled)
		{
			this.nodes = nodes;
			this.policy = policy;
			this.template = template;
			this.totalJobs = totalJobs;
			this.heapMb = heapMb;
			this.millisecondsPerCostUnit = millisecondsPerCostUnit;
			this.maxRetries = maxRetries;
			this.cancelled = cancelled;
			this.startNano = System.nanoTime();
		}

		long elapsedMillis()
		{
			return Math.max(0L, (System.nanoTime() - startNano) / 1000000L);
		}

	}

	private DynamicQueueSchedulerEngine()
	{
	}

	static Controller runAsync(Config config, Listener listener)
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
		Thread supervisor = new Thread(new Runnable()
		{
			public void run()
			{
				runSupervisor(safeConfig, cancelled, listener);
			}
		}, "DynamicQueueScheduler-supervisor");
		supervisor.setDaemon(true);
		supervisor.start();
		return new Controller(cancelled, supervisor);
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
			throw new IllegalStateException(
					"Dynamic queue run failed.",
					failure[0]);
		}
		return result[0];
	}

	private static void runSupervisor(
			Config config,
			AtomicBoolean cancelled,
			Listener listener)
	{
		try
		{
			ArrayList<NodeRuntime> nodes = new ArrayList<NodeRuntime>();
			for (NodeCapacityProfile profile : config.profiles)
			{
				nodes.add(new NodeRuntime(profile));
			}
			List<SchedulerLabEngine.WorkloadUnit> units =
					SchedulerLabEngine.createWorkload(
							config.template,
							config.workUnitCount,
							config.workUnitCount);
			SharedRunState state = new SharedRunState(
					nodes,
					config.policy,
					config.template,
					units.size(),
					config.heapMb,
					config.millisecondsPerCostUnit,
					config.maxRetries,
					cancelled);
			prepareQueues(state, units);
			log(state, "prepared " + units.size() + " independent work units.");
			emit(listener, state, false);

			int workerCount = 0;
			for (NodeRuntime node : nodes)
			{
				workerCount += Math.max(1, node.activeWorkerSlots);
			}
			CountDownLatch latch = new CountDownLatch(workerCount);
			for (NodeRuntime node : nodes)
			{
				int slots = Math.max(1, node.activeWorkerSlots);
				for (int lane = 0; lane < slots; lane++)
				{
					final NodeRuntime workerNode = node;
					final int laneIndex = lane;
					Thread worker = new Thread(new Runnable()
					{
						public void run()
						{
							runWorker(workerNode, laneIndex, state, listener);
							latch.countDown();
						}
					}, "DynamicQueue-node" + node.profile.getNodeIndex()
							+ "-lane" + (lane + 1));
					worker.setDaemon(true);
					worker.start();
				}
			}
			while (!cancelled.get())
			{
				if (latch.getCount() == 0L)
				{
					break;
				}
				emit(listener, state, false);
				Thread.sleep(180L);
			}
			if (cancelled.get())
			{
				log(state, "cancelled by user; completed work is kept in this run snapshot.");
			}
			latch.await();
			Snapshot finalSnapshot =
					new Snapshot(state, true, cancelled.get());
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

	private static void runWorker(
			NodeRuntime node,
			int laneIndex,
			SharedRunState state,
			Listener listener)
	{
		while (!state.cancelled.get())
		{
			WorkState work = nextWork(node, state);
			if (work == null)
			{
				if (isRunDrained(state))
				{
					break;
				}
				setNodeState(node, "waiting", "");
				sleepQuietly(20L);
				continue;
			}
			runWorkUnit(node, laneIndex, work, state, listener);
		}
		setNodeState(node, "idle", "");
	}

	private static void runWorkUnit(
			NodeRuntime node,
			int laneIndex,
			WorkState work,
			SharedRunState state,
			Listener listener)
	{
		if (!acquireMemory(node, work, state))
		{
			requeueWork(node, work, state, false);
			return;
		}
		long start = state.elapsedMillis();
		long runtime = estimateRuntimeMillis(
				work.unit,
				node.profile,
				state.millisecondsPerCostUnit);
		synchronized (node.lock)
		{
			node.runningJobs++;
			node.currentState = "running";
			node.currentJob = work.unit.getId();
		}
		log(state, "node " + node.profile.getNodeIndex()
				+ " started " + work.unit.getId()
				+ " (" + work.unit.getType().getLabel() + ")");
		emit(listener, state, false);
		sleepInterruptibly(runtime, state.cancelled);
		boolean failed = shouldFail(node.profile, work);
		long end = state.elapsedMillis();
		synchronized (node.lock)
		{
			node.runningJobs = Math.max(0, node.runningJobs - 1);
			node.busyMillis += Math.max(1L, end - start);
			node.currentState = failed ? "retrying" : "complete";
			node.currentJob = work.unit.getId();
		}
		synchronized (state.lock)
		{
			state.activeMemoryMb = Math.max(
					0L,
					state.activeMemoryMb - work.unit.getMemoryMb());
			state.segments.add(new Segment(
					node.profile.getNodeIndex(),
					laneIndex,
					work.unit.getId(),
					work.unit.getType(),
					start,
					end,
					failed,
					work.attempts));
			state.lock.notifyAll();
		}
		if (failed && work.attempts < retryLimit(work, state))
		{
			work.attempts++;
			work.failedNodeIndexes.add(Integer.valueOf(
					node.profile.getNodeIndex()));
			synchronized (node.lock)
			{
				node.failedJobs++;
				node.retriedJobs++;
			}
			synchronized (state.lock)
			{
				state.retriedJobs++;
			}
			log(state, "node " + node.profile.getNodeIndex()
					+ " failed " + work.unit.getId()
					+ "; retry " + work.attempts + " queued.");
			requeueWork(node, work, state, true);
		}
		else if (failed)
		{
			synchronized (node.lock)
			{
				node.failedJobs++;
			}
			synchronized (state.lock)
			{
				state.failedJobs++;
			}
			log(state, "node " + node.profile.getNodeIndex()
					+ " failed " + work.unit.getId()
					+ "; retry limit reached.");
		}
		else
		{
			synchronized (node.lock)
			{
				node.completedJobs++;
				node.currentState = "complete";
			}
			synchronized (state.lock)
			{
				state.completedJobs++;
			}
			log(state, "node " + node.profile.getNodeIndex()
					+ " completed " + work.unit.getId() + ".");
		}
		emit(listener, state, false);
	}

	private static void prepareQueues(
			SharedRunState state,
			List<SchedulerLabEngine.WorkloadUnit> units)
	{
		ArrayList<WorkState> jobs = new ArrayList<WorkState>();
		for (SchedulerLabEngine.WorkloadUnit unit : units)
		{
			jobs.add(new WorkState(unit));
		}
		sortWork(jobs, state.policy);
		if (usesLocalQueues(state.policy))
		{
			assignInitialQueues(state, jobs);
		}
		else
		{
			synchronized (state.lock)
			{
				for (WorkState job : jobs)
				{
					state.sharedQueue.add(job);
				}
				state.lock.notifyAll();
			}
		}
	}

	private static void assignInitialQueues(
			SharedRunState state,
			List<WorkState> jobs)
	{
		long[] loads = new long[state.nodes.size()];
		int rr = 0;
		ArrayList<Integer> weightedRing = weightedRing(state.nodes);
		int weightedCursor = 0;
		for (WorkState job : jobs)
		{
			int nodeIndex;
			if (state.policy == SchedulerLabEngine.SchedulingPolicy.ROUND_ROBIN)
			{
				nodeIndex = rr++ % state.nodes.size();
			}
			else if (state.policy
					== SchedulerLabEngine.SchedulingPolicy.WEIGHTED_ROUND_ROBIN
					&& !weightedRing.isEmpty())
			{
				nodeIndex = weightedRing.get(
						weightedCursor++ % weightedRing.size()).intValue();
			}
			else
			{
				nodeIndex = smallestLoad(loads);
			}
			assignToLocalNode(state.nodes.get(nodeIndex), job);
			loads[nodeIndex] += estimateRuntimeMillis(
					job.unit,
					state.nodes.get(nodeIndex).profile,
					state.millisecondsPerCostUnit);
		}
	}

	private static WorkState nextWork(
			NodeRuntime node,
			SharedRunState state)
	{
		synchronized (node.lock)
		{
			if (!node.localQueue.isEmpty())
			{
				WorkState work = node.localQueue.removeFirst();
				node.queuedJobs = Math.max(0, node.queuedJobs - 1);
				return work;
			}
		}
		if (state.policy == SchedulerLabEngine.SchedulingPolicy.WORK_STEALING)
		{
			WorkState stolen = stealWork(node, state);
			if (stolen != null)
			{
				return stolen;
			}
		}
		if (usesLocalQueues(state.policy))
		{
			return null;
		}
		synchronized (state.lock)
		{
			WorkState selected = null;
			for (WorkState candidate : state.sharedQueue)
			{
				if (canRunOnNode(candidate, node, state))
				{
					if (selected == null
							|| prefer(candidate, selected, state.policy))
					{
						selected = candidate;
					}
				}
			}
			if (selected != null)
			{
				state.sharedQueue.remove(selected);
				assignToRuntime(node, selected);
				return selected;
			}
		}
		return null;
	}

	private static WorkState stealWork(
			NodeRuntime idle,
			SharedRunState state)
	{
		NodeRuntime donor = null;
		for (NodeRuntime node : state.nodes)
		{
			if (node == idle)
			{
				continue;
			}
			synchronized (node.lock)
			{
				if (node.localQueue.size() < 2)
				{
					continue;
				}
				if (donor == null)
				{
					donor = node;
				}
				else
				{
					synchronized (donor.lock)
					{
						if (node.localQueue.size()
								> donor.localQueue.size())
						{
							donor = node;
						}
					}
				}
			}
		}
		if (donor == null)
		{
			return null;
		}
		synchronized (donor.lock)
		{
			WorkState work = donor.localQueue.pollLast();
			if (work == null || !canRunOnNode(work, idle, state))
			{
				if (work != null)
				{
					donor.localQueue.addLast(work);
				}
				return null;
			}
			donor.queuedJobs = Math.max(0, donor.queuedJobs - 1);
			assignToRuntime(idle, work);
			log(state, "node " + idle.profile.getNodeIndex()
					+ " stole " + work.unit.getId()
					+ " from node " + donor.profile.getNodeIndex() + ".");
			return work;
		}
	}

	private static boolean acquireMemory(
			NodeRuntime node,
			WorkState work,
			SharedRunState state)
	{
		long deadline = System.currentTimeMillis() + 60000L;
		synchronized (state.lock)
		{
			while (!state.cancelled.get())
			{
				if (work.unit.getMemoryMb() > node.profile.getMemoryLimitMb())
				{
					log(state, "node " + node.profile.getNodeIndex()
							+ " cannot run " + work.unit.getId()
							+ " because the job exceeds its memory profile.");
					return false;
				}
				if (state.activeMemoryMb + work.unit.getMemoryMb()
						<= state.heapMb)
				{
					state.activeMemoryMb += work.unit.getMemoryMb();
					return true;
				}
				setNodeState(node, "waiting for memory", work.unit.getId());
				try
				{
					state.lock.wait(80L);
				}
				catch (InterruptedException interrupted)
				{
					Thread.currentThread().interrupt();
					return false;
				}
				if (System.currentTimeMillis() > deadline)
				{
					return false;
				}
			}
		}
		return false;
	}

	private static void requeueWork(
			NodeRuntime node,
			WorkState work,
			SharedRunState state,
			boolean retry)
	{
		if (state.cancelled.get())
		{
			return;
		}
		if (usesLocalQueues(state.policy)
				&& state.policy != SchedulerLabEngine.SchedulingPolicy.WORK_STEALING)
		{
			assignToLocalNode(node, work);
			return;
		}
		synchronized (state.lock)
		{
			if (retry
					&& state.policy
							== SchedulerLabEngine.SchedulingPolicy.DEADLINE_PRIORITY)
			{
				state.sharedQueue.addFirst(work);
			}
			else
			{
				state.sharedQueue.addLast(work);
			}
			state.lock.notifyAll();
		}
	}

	private static boolean isRunDrained(SharedRunState state)
	{
		synchronized (state.lock)
		{
			if (!state.sharedQueue.isEmpty())
			{
				return false;
			}
			for (NodeRuntime node : state.nodes)
			{
				synchronized (node.lock)
				{
					if (!node.localQueue.isEmpty()
							|| node.runningJobs > 0)
					{
						return false;
					}
				}
			}
			return state.completedJobs + state.failedJobs >= state.totalJobs;
		}
	}

	private static void assignToLocalNode(NodeRuntime node, WorkState work)
	{
		synchronized (node.lock)
		{
			node.localQueue.addLast(work);
			assignToRuntime(node, work);
			node.queuedJobs++;
		}
	}

	private static void assignToRuntime(NodeRuntime node, WorkState work)
	{
		synchronized (node.lock)
		{
			node.assignedJobs++;
			node.currentState = "queued";
			node.currentJob = work.unit.getId();
		}
	}

	private static boolean canRunOnNode(
			WorkState work,
			NodeRuntime node,
			SharedRunState state)
	{
		if (work.unit.getMemoryMb() > node.profile.getMemoryLimitMb())
		{
			return false;
		}
		if (state.policy
				== SchedulerLabEngine.SchedulingPolicy.FAILURE_AWARE_RESCHEDULING
				&& work.failedNodeIndexes.contains(
						Integer.valueOf(node.profile.getNodeIndex()))
				&& work.failedNodeIndexes.size() < state.nodes.size())
		{
			return false;
		}
		return true;
	}

	private static boolean prefer(
			WorkState candidate,
			WorkState current,
			SchedulerLabEngine.SchedulingPolicy policy)
	{
		if (policy == SchedulerLabEngine.SchedulingPolicy.DEADLINE_PRIORITY)
		{
			int priority = Integer.compare(
					candidate.unit.getPriority(),
					current.unit.getPriority());
			if (priority != 0)
			{
				return priority > 0;
			}
			return candidate.unit.getDeadlineTick()
					< current.unit.getDeadlineTick();
		}
		if (policy == SchedulerLabEngine.SchedulingPolicy.MEMORY_AWARE_SAFE_WAVE)
		{
			return candidate.unit.getMemoryMb()
					< current.unit.getMemoryMb();
		}
		return false;
	}

	private static boolean usesLocalQueues(
			SchedulerLabEngine.SchedulingPolicy policy)
	{
		return policy == SchedulerLabEngine.SchedulingPolicy.STATIC_EQUAL_SPLIT
				|| policy == SchedulerLabEngine.SchedulingPolicy.ROUND_ROBIN
				|| policy == SchedulerLabEngine.SchedulingPolicy.WEIGHTED_ROUND_ROBIN
				|| policy == SchedulerLabEngine.SchedulingPolicy.WORK_STEALING;
	}

	private static void sortWork(
			List<WorkState> jobs,
			SchedulerLabEngine.SchedulingPolicy policy)
	{
		if (policy == SchedulerLabEngine.SchedulingPolicy.DEADLINE_PRIORITY)
		{
			Collections.sort(jobs, new Comparator<WorkState>()
			{
				public int compare(WorkState first, WorkState second)
				{
					int priority = Integer.compare(
							second.unit.getPriority(),
							first.unit.getPriority());
					if (priority != 0)
					{
						return priority;
					}
					return Integer.compare(
							first.unit.getDeadlineTick(),
							second.unit.getDeadlineTick());
				}
			});
		}
	}

	private static ArrayList<Integer> weightedRing(List<NodeRuntime> nodes)
	{
		ArrayList<Integer> ring = new ArrayList<Integer>();
		for (int index = 0; index < nodes.size(); index++)
		{
			double weight = NodeCapacityScheduler.schedulingWeight(
					nodes.get(index).profile);
			int turns = Math.max(1, Math.min(16,
					(int)Math.round(weight * 2.0)));
			for (int turn = 0; turn < turns; turn++)
			{
				ring.add(Integer.valueOf(index));
			}
		}
		return ring;
	}

	private static int smallestLoad(long[] loads)
	{
		int best = 0;
		for (int index = 1; index < loads.length; index++)
		{
			if (loads[index] < loads[best])
			{
				best = index;
			}
		}
		return best;
	}

	private static int retryLimit(
			WorkState work,
			SharedRunState state)
	{
		return Math.min(
				Math.max(0, work.unit.getMaxRetries()),
				Math.max(0, state.maxRetries));
	}

	private static long estimateRuntimeMillis(
			SchedulerLabEngine.WorkloadUnit unit,
			NodeCapacityProfile profile,
			int millisecondsPerCostUnit)
	{
		double runtime = unit.getCostUnits()
				* Math.max(1, millisecondsPerCostUnit);
		runtime /= Math.max(0.10, profile.getCpuMultiplier());
		if (profile.isSlowdownSimulationEnabled())
		{
			runtime *= Math.max(1.0, profile.getSlowdownMultiplier());
		}
		runtime += NodeCapacityScheduler.perJobDelayMillis(profile);
		return Math.max(8L, Math.min(120000L, Math.round(runtime)));
	}

	private static boolean shouldFail(
			NodeCapacityProfile profile,
			WorkState work)
	{
		if (!profile.isFailureSimulationEnabled()
				|| profile.getFailureProbabilityPercent() <= 0)
		{
			return false;
		}
		String key = profile.getNodeIndex()
				+ "|" + work.unit.getId()
				+ "|" + work.attempts;
		int bucket = Math.floorMod(key.hashCode(), 100);
		return bucket < profile.getFailureProbabilityPercent();
	}

	private static void setNodeState(
			NodeRuntime node,
			String state,
			String job)
	{
		synchronized (node.lock)
		{
			node.currentState = state == null ? "idle" : state;
			node.currentJob = job == null ? "" : job;
		}
	}

	private static void log(SharedRunState state, String line)
	{
		synchronized (state.lock)
		{
			String message = String.format(
					Locale.US,
					"t=%06d ms  %s",
					Long.valueOf(state.elapsedMillis()),
					line == null ? "" : line);
			state.logLines.add(message);
			while (state.logLines.size() > 500)
			{
				state.logLines.remove(0);
			}
		}
	}

	private static void emit(
			Listener listener,
			SharedRunState state,
			boolean finished)
	{
		if (listener != null)
		{
			listener.onSnapshot(new Snapshot(
					state,
					finished,
					state.cancelled.get()));
		}
	}

	private static void sleepQuietly(long millis)
	{
		try
		{
			Thread.sleep(Math.max(1L, millis));
		}
		catch (InterruptedException interrupted)
		{
			Thread.currentThread().interrupt();
		}
	}

	private static void sleepInterruptibly(
			long millis,
			AtomicBoolean cancelled)
	{
		long remaining = Math.max(1L, millis);
		while (remaining > 0L && !cancelled.get())
		{
			long chunk = Math.min(remaining, 80L);
			long before = System.currentTimeMillis();
			sleepQuietly(chunk);
			remaining -= Math.max(1L, System.currentTimeMillis() - before);
		}
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

	private static int clamp(int value, int minimum, int maximum)
	{
		return Math.max(minimum, Math.min(maximum, value));
	}
}
