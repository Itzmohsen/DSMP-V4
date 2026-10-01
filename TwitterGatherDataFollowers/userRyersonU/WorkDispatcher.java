package TwitterGatherDataFollowers.userRyersonU;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Deterministic coordinator for one phase of schedulable work.
 *
 * The same class drives live runs (real node threads, emulated time), the
 * Dynamic Queue demo (real threads) and the discrete-event simulator (virtual
 * time), so every policy behaves the same way everywhere. All times are in
 * the caller's time unit ({@link Options#msPerTimeUnit} milliseconds each).
 *
 * <h3>Where units are</h3>
 * A unit that has not started is at the coordinator (in the shared
 * <i>backlog</i> or in a node's <i>plan</i>) or has been <i>delivered</i> to a
 * node's local queue. A node holds at most its queue capacity of units,
 * running plus delivered; whenever it has room the coordinator delivers the
 * next planned unit. Delivery sends a descriptor over the node's link, and a
 * unit that runs away from its home node also moves its input data there, so
 * every assignment carries the model time at which the unit can start
 * ({@link Assignment#getReadyAt()}).
 *
 * <h3>Execution modes</h3>
 * <ul>
 * <li>STATIC policies plan every unit onto a node when the phase opens
 *     ({@link PlacementPlanner}); each node works through its own plan.</li>
 * <li>PULL policies keep one backlog in the policy's order; a lane that is idle
 *     asks the coordinator (a request/reply round trip) and gets the next unit
 *     the policy gives it; the coordinator prefetches up to the queue
 *     capacity, which hides that round trip.</li>
 * <li>STEAL starts from the capacity-weighted plan; a lane whose node has
 *     nothing left takes the last unit of the node with the most remaining
 *     work, from the coordinator's plan when possible.</li>
 * </ul>
 *
 * <h3>Exactly once</h3>
 * Every execution carries a token; an execution that is revoked (drain, crash,
 * or its lane withdrawing) can never commit, and its {@link Assignment} is
 * marked revoked at once so the running code can stop early.
 *
 * <h3>Crashes</h3>
 * A crashed node loses its running executions and its delivered units at the
 * crash; the coordinator notices after the detection timeout and only then
 * reschedules that node's work. While a node is down its data cannot be read,
 * so units homed there wait for its repair. A repaired node rejoins empty.
 *
 * All methods are synchronized; callers may use it from many threads.
 */
final class WorkDispatcher
{
	enum UnitState
	{
		/** At the coordinator, not assigned to a node. */
		BACKLOG,
		/** At the coordinator, in a node's plan. */
		PLANNED,
		/** Delivered to (or on its way to) a node's local queue. */
		RESERVED,
		RUNNING,
		COMMITTED,
		FAILED
	}

	enum NodeState
	{
		ACTIVE,
		PAUSED,
		UNAVAILABLE,
		/** Crashed; the coordinator may not know yet. */
		DOWN
	}

	enum Source
	{
		LOCAL,
		HUB,
		STOLEN
	}

	enum Outcome
	{
		/** The result is accepted; the caller must now publish it. */
		COMMITTED,
		/** The execution was lost to an injected transient fault; discard it. */
		INJECTED_FAILURE,
		/** The assignment was revoked before it finished; discard it. */
		STALE,
		/** A reported failure was requeued. */
		RETRY,
		/** The unit failed too often; the phase has failed. */
		FINAL_FAILURE
	}

	enum ExecutionMode
	{
		STATIC,
		PULL,
		STEAL
	}

	static ExecutionMode modeOf(SchedulerLabEngine.SchedulingPolicy policy)
	{
		if (policy == null)
		{
			return ExecutionMode.STATIC;
		}
		switch (policy)
		{
			case WORK_STEALING:
				return ExecutionMode.STEAL;
			case QUEUE_LENGTH:
			case DEADLINE_PRIORITY:
			case MEMORY_AWARE_SAFE_WAVE:
			case FAILURE_AWARE_RESCHEDULING:
				return ExecutionMode.PULL;
			default:
				return ExecutionMode.STATIC;
		}
	}

	/** Receives every dispatcher event (not capped, unlike the retained list). */
	interface EventListener
	{
		void onEvent(Event event);
	}

	/**
	 * Crash losses one unit may suffer before the phase fails. A unit of
	 * duration D on nodes crashing every MTBF on average needs about
	 * e^(D/MTBF) executions; this many losses means D is several MTBFs and the
	 * unit cannot finish between crashes. Crash losses are not the unit's
	 * fault, so they never count against its failure budgets.
	 */
	static final int MAX_CRASH_LOSSES_PER_UNIT = 200;

	static final class Options
	{
		private int maxFailuresPerUnit = 10;
		private int maxRealFailuresPerUnit = 2;
		private boolean faultInjection;
		private long failureSeed = 0x5EEDL;
		private long globalMemoryBudgetMb = Long.MAX_VALUE;
		private double msPerTimeUnit = 1.0;
		private int maxRetainedEvents = 5000;
		private NetworkModel network;
		private double openedAt;
		private EventListener listener;

		Options maxFailuresPerUnit(int value)
		{
			maxFailuresPerUnit = Math.max(1, value);
			return this;
		}

		/** Reported (non-injected) failures a unit may have before the phase fails. */
		Options maxRealFailuresPerUnit(int value)
		{
			maxRealFailuresPerUnit = Math.max(1, value);
			return this;
		}

		Options faultInjection(boolean value, long seed)
		{
			faultInjection = value;
			failureSeed = seed;
			return this;
		}

		Options globalMemoryBudgetMb(long value)
		{
			globalMemoryBudgetMb = value <= 0L ? Long.MAX_VALUE : value;
			return this;
		}

		/** Milliseconds represented by one time unit (network times are in ms). */
		Options msPerTimeUnit(double value)
		{
			msPerTimeUnit = Double.isNaN(value) || value <= 0.0 ? 1.0 : value;
			return this;
		}

		Options maxRetainedEvents(int value)
		{
			maxRetainedEvents = Math.max(10, value);
			return this;
		}

		/** The run's network; null builds one from the node specs. */
		Options network(NetworkModel value)
		{
			network = value;
			return this;
		}

		/** Time the phase opens (initial deliveries leave then). */
		Options openedAt(double value)
		{
			openedAt = Double.isNaN(value) ? 0.0 : value;
			return this;
		}

		Options listener(EventListener value)
		{
			listener = value;
			return this;
		}

		boolean isFaultInjection()
		{
			return faultInjection;
		}

		double getMsPerTimeUnit()
		{
			return msPerTimeUnit;
		}
	}

	/** One execution of one unit on one lane. */
	static final class Assignment
	{
		private final WorkUnitSpec unit;
		private final int node;
		private final int lane;
		private final long token;
		private final int execution;
		private final Source source;
		private final double assignedAt;
		private final double readyAt;
		private final long transferredBytes;
		private volatile String revokedReason;

		private Assignment(
				WorkUnitSpec unit,
				int node,
				int lane,
				long token,
				int execution,
				Source source,
				double assignedAt,
				double readyAt,
				long transferredBytes)
		{
			this.unit = unit;
			this.node = node;
			this.lane = lane;
			this.token = token;
			this.execution = execution;
			this.source = source;
			this.assignedAt = assignedAt;
			this.readyAt = readyAt;
			this.transferredBytes = transferredBytes;
		}

		WorkUnitSpec getUnit()
		{
			return unit;
		}

		int getUnitId()
		{
			return unit.getId();
		}

		int getNode()
		{
			return node;
		}

		int getLane()
		{
			return lane;
		}

		long getToken()
		{
			return token;
		}

		/** 1 for the first execution of the unit, 2 for the second, ... */
		int getExecution()
		{
			return execution;
		}

		Source getSource()
		{
			return source;
		}

		/** Time the coordinator handed the unit to the lane. */
		double getAssignedAt()
		{
			return assignedAt;
		}

		/**
		 * Earliest time the unit can start on its node: its descriptor and, when
		 * it runs away from home, its input data have arrived.
		 */
		double getReadyAt()
		{
			return readyAt;
		}

		/** Input bytes moved over the network for this execution. */
		long getTransferredBytes()
		{
			return transferredBytes;
		}

		/** True once the execution can no longer commit (drain, crash, withdrawal). */
		boolean isRevoked()
		{
			return revokedReason != null;
		}

		String getRevokedReason()
		{
			return revokedReason;
		}

		private void revoke(String reason)
		{
			if (revokedReason == null)
			{
				revokedReason = reason == null ? "revoked" : reason;
			}
		}
	}

	static final class Event
	{
		private final double time;
		private final int node;
		private final int unitId;
		private final String unitKey;
		private final String type;
		private final String detail;

		private Event(double time, int node, int unitId, String unitKey, String type, String detail)
		{
			this.time = time;
			this.node = node;
			this.unitId = unitId;
			this.unitKey = unitKey == null ? "" : unitKey;
			this.type = type;
			this.detail = detail == null ? "" : detail;
		}

		double getTime()
		{
			return time;
		}

		int getNode()
		{
			return node;
		}

		int getUnitId()
		{
			return unitId;
		}

		String getUnitKey()
		{
			return unitKey;
		}

		/** assigned, stolen, delivered, completed, failed, injected-failure,
		 *  retried, stale, revoked, redirected, released, paused, resumed,
		 *  unavailable, crashed, detected, recovered, lost, policy, failed-final. */
		String getType()
		{
			return type;
		}

		String getDetail()
		{
			return detail;
		}
	}

	/** Per-node counters for reports and panels. */
	static final class NodeStats
	{
		private final int node;
		private final NodeState state;
		private final int running;
		private final int reserved;
		private final int planned;
		private final int assigned;
		private final int completed;
		private final int failed;
		private final int stolen;
		private final int stale;
		private final int lostToCrash;
		private final int deliveries;
		private final long memoryInUseMb;
		private final int queuePeak;
		private final List<Integer> runningUnits;

		private NodeStats(NodeRecord record, NodeState state)
		{
			queuePeak = record.queuePeak;
			node = record.spec.getIndex();
			this.state = state;
			running = record.inflight;
			reserved = record.localQueue.size();
			planned = record.plan.size();
			assigned = record.assigned;
			completed = record.completed;
			failed = record.failed;
			stolen = record.stolen;
			stale = record.stale;
			lostToCrash = record.lostToCrash;
			deliveries = record.deliveries;
			memoryInUseMb = record.memoryInUseMb;
			runningUnits = Collections.unmodifiableList(
					new ArrayList<Integer>(record.runningUnits));
		}

		int getNode()
		{
			return node;
		}

		NodeState getState()
		{
			return state;
		}

		int getRunning()
		{
			return running;
		}

		/** Units delivered to the node and not started. */
		int getReserved()
		{
			return reserved;
		}

		/** Units the coordinator still keeps in this node's plan. */
		int getPlanned()
		{
			return planned;
		}

		int getAssigned()
		{
			return assigned;
		}

		int getCompleted()
		{
			return completed;
		}

		int getFailed()
		{
			return failed;
		}

		int getStolen()
		{
			return stolen;
		}

		int getStale()
		{
			return stale;
		}

		/** Executions and delivered units lost to crashes of this node. */
		int getLostToCrash()
		{
			return lostToCrash;
		}

		int getDeliveries()
		{
			return deliveries;
		}

		long getMemoryInUseMb()
		{
			return memoryInUseMb;
		}

		/** Most units the node held at once (running plus delivered). */
		int getQueuePeak()
		{
			return queuePeak;
		}

		List<Integer> getRunningUnits()
		{
			return runningUnits;
		}
	}

	private static final class UnitRecord
	{
		private final WorkUnitSpec spec;
		private UnitState state = UnitState.BACKLOG;
		private int node;
		private long token;
		private int executions;
		private int failures;
		private int realFailures;
		/** Executions of this unit lost to node crashes. */
		private int crashLosses;
		private final BitSet excluded = new BitSet();
		private boolean retryFirst;
		/** For a delivered unit: when its descriptor and data are at the node. */
		private double readyAt;
		private long deliveredBytes;
		private boolean deliveredFromVictim;

		private UnitRecord(WorkUnitSpec spec)
		{
			this.spec = spec;
		}
	}

	private static final class NodeRecord
	{
		private NodeSpec spec;
		private NodeState operatorState = NodeState.ACTIVE;
		private boolean down;
		private boolean crashDetected;
		private double downSince;
		private final ArrayDeque<Integer> plan = new ArrayDeque<Integer>();
		private final ArrayDeque<Integer> localQueue = new ArrayDeque<Integer>();
		private int inflight;
		private long memoryInUseMb;
		private int assigned;
		private int completed;
		private int failed;
		private int stolen;
		private int stale;
		private int lostToCrash;
		private int deliveries;
		private int queuePeak;
		private final TreeSet<Integer> runningUnits = new TreeSet<Integer>();

		private NodeRecord(NodeSpec spec)
		{
			this.spec = spec;
		}

		NodeState effectiveState()
		{
			return down ? NodeState.DOWN : operatorState;
		}

		boolean isActive()
		{
			return !down && operatorState == NodeState.ACTIVE;
		}
	}

	private static final class Inflight
	{
		private final int unitId;
		private final int node;
		private final int memoryMb;
		private final Assignment assignment;

		private Inflight(int unitId, int node, int memoryMb, Assignment assignment)
		{
			this.unitId = unitId;
			this.node = node;
			this.memoryMb = memoryMb;
			this.assignment = assignment;
		}
	}

	private final String phaseKey;
	private final Options options;
	private final NetworkModel network;
	private SchedulerLabEngine.SchedulingPolicy policy;
	private ExecutionMode mode;
	private final LinkedHashMap<Integer,UnitRecord> units =
			new LinkedHashMap<Integer,UnitRecord>();
	private final NodeRecord[] nodes;
	private TreeSet<UnitRecord> backlog;
	/** Waiting lanes, encoded node * 4096 + lane, in (node, lane) order. */
	private final TreeSet<Integer> waitingLanes = new TreeSet<Integer>();
	/** When each waiting lane asked: {local time, arrival at the coordinator or NaN}. */
	private final HashMap<Integer,double[]> requests = new HashMap<Integer,double[]>();
	private final Map<Integer,Assignment> pending = new TreeMap<Integer,Assignment>();
	private final Map<Long,Inflight> inflight = new LinkedHashMap<Long,Inflight>();
	private final ArrayList<Event> events = new ArrayList<Event>();
	private final List<WorkUnitSpec> unitSpecs;
	private long nextToken = 1L;
	private long globalMemoryInUseMb;
	private int committedCount;
	private boolean failed;
	private String failureReason = "";
	private double lastEventTime;
	private int droppedEvents;
	private double firstStart = Double.NaN;
	private double lastCommit = Double.NaN;

	WorkDispatcher(
			String phaseKey,
			SchedulerLabEngine.SchedulingPolicy policy,
			List<NodeSpec> nodeSpecs,
			List<WorkUnitSpec> unitSpecs,
			Options options)
	{
		if (nodeSpecs == null || nodeSpecs.isEmpty())
		{
			throw new IllegalArgumentException("A dispatcher needs at least one node.");
		}
		this.phaseKey = phaseKey == null ? "phase" : phaseKey;
		this.options = options == null ? new Options() : options;
		this.policy = policy == null
				? SchedulerLabEngine.SchedulingPolicy.CAPACITY_WEIGHTED : policy;
		this.mode = modeOf(this.policy);
		this.nodes = new NodeRecord[nodeSpecs.size()];
		for (int index = 0; index < nodeSpecs.size(); index++)
		{
			NodeSpec spec = nodeSpecs.get(index);
			if (spec.getIndex() != index + 1)
			{
				throw new IllegalArgumentException(
						"Node specs must be indexed 1..N in order.");
			}
			nodes[index] = new NodeRecord(spec);
		}
		this.network = this.options.network != null
				? this.options.network : new NetworkModel(nodeSpecs, false);
		ArrayList<WorkUnitSpec> accepted = new ArrayList<WorkUnitSpec>();
		if (unitSpecs != null)
		{
			for (WorkUnitSpec spec : unitSpecs)
			{
				if (spec == null)
				{
					continue;
				}
				if (units.containsKey(Integer.valueOf(spec.getId())))
				{
					throw new IllegalArgumentException(
							"Duplicate work unit id " + spec.getId() + ".");
				}
				requireFeasible(spec);
				units.put(Integer.valueOf(spec.getId()), new UnitRecord(spec));
				accepted.add(spec);
			}
		}
		this.unitSpecs = Collections.unmodifiableList(accepted);
		lastEventTime = this.options.openedAt;
		backlog = new TreeSet<UnitRecord>(backlogOrder(this.policy));
		distribute(new ArrayList<UnitRecord>(units.values()), this.options.openedAt);
		deliverAll(this.options.openedAt);
	}

	// ------------------------------------------------------------ public API

	/**
	 * A lane asks for work at {@code now}. Returns the lane's assignment, or
	 * null when it has to wait (it stays registered and gets the next unit
	 * the policy gives it, see {@link #pendingFor}).
	 */
	synchronized Assignment request(int node, int lane, double now)
	{
		requireNode(node);
		noteTime(now);
		int laneKey = laneKey(node, lane);
		Assignment ready = pending.remove(Integer.valueOf(laneKey));
		if (ready != null)
		{
			return ready;
		}
		if (isComplete() || failed)
		{
			return null;
		}
		if (!waitingLanes.contains(Integer.valueOf(laneKey)))
		{
			waitingLanes.add(Integer.valueOf(laneKey));
			requests.put(Integer.valueOf(laneKey), new double[] {now, Double.NaN});
		}
		dispatch(now);
		return pending.remove(Integer.valueOf(laneKey));
	}

	/** The assignment made for a waiting lane since it asked, or null. */
	synchronized Assignment pendingFor(int node, int lane)
	{
		return pending.remove(Integer.valueOf(laneKey(node, lane)));
	}

	/** Removes a lane from the waiting set (it stopped asking for work). */
	synchronized void withdraw(int node, int lane)
	{
		int laneKey = laneKey(node, lane);
		waitingLanes.remove(Integer.valueOf(laneKey));
		requests.remove(Integer.valueOf(laneKey));
		Assignment orphan = pending.remove(Integer.valueOf(laneKey));
		if (orphan != null)
		{
			//The lane will never run it: treat as a revoked execution.
			orphan.revoke("the lane withdrew");
			releaseInflight(orphan.getToken());
			UnitRecord record = units.get(Integer.valueOf(orphan.getUnitId()));
			if (record != null && record.token == orphan.getToken()
					&& record.state == UnitState.RUNNING)
			{
				record.token = 0L;
				record.state = UnitState.BACKLOG;
				requeueFront(record, orphan.getNode(), lastEventTime);
			}
			dispatch(lastEventTime);
		}
	}

	synchronized Outcome complete(Assignment assignment, double now)
	{
		noteTime(now);
		Inflight run = releaseInflight(assignment.getToken());
		UnitRecord record = units.get(Integer.valueOf(assignment.getUnitId()));
		NodeRecord node = nodeRecord(assignment.getNode());
		double hub = hubLearns(assignment.getNode(), NetworkModel.MESSAGE_HEADER_BYTES, now);
		if (record == null || run == null || record.token != assignment.getToken()
				|| record.state != UnitState.RUNNING || assignment.isRevoked())
		{
			node.stale++;
			addEvent(now, assignment.getNode(), assignment.getUnit(), "stale",
					"Result of revoked execution " + assignment.getExecution()
					+ " discarded"
					+ (assignment.getRevokedReason() == null ? "."
							: " (" + assignment.getRevokedReason() + ")."));
			deliver(node, hub);
			dispatch(now);
			return Outcome.STALE;
		}
		if (options.faultInjection && injectedFailure(record, assignment))
		{
			addEvent(now, assignment.getNode(), record.spec, "injected-failure",
					String.format(Locale.US,
							"Execution %d lost to an injected fault (p=%.3f).",
							assignment.getExecution(),
							node.spec.getFailureProbability()));
			Outcome outcome = retry(record, assignment.getNode(), now,
					"injected transient fault");
			deliver(node, hub);
			prefetch(node, hub);
			dispatch(now);
			return outcome == Outcome.FINAL_FAILURE
					? Outcome.FINAL_FAILURE : Outcome.INJECTED_FAILURE;
		}
		if (record.spec.getOutputBytes() > 0L)
		{
			//The result travels to the coordinator on the node's link.
			network.send(assignment.getNode(), NetworkModel.HUB,
					record.spec.getOutputBytes(), toMs(now));
		}
		record.state = UnitState.COMMITTED;
		record.token = 0L;
		record.node = assignment.getNode();
		node.completed++;
		committedCount++;
		lastCommit = now;
		addEvent(now, assignment.getNode(), record.spec, "completed",
				"Committed on node " + assignment.getNode() + ".");
		deliver(node, hub);
		prefetch(node, hub);
		dispatch(now);
		return Outcome.COMMITTED;
	}

	synchronized Outcome fail(Assignment assignment, double now, String reason)
	{
		return fail(assignment, now, reason, true);
	}

	/**
	 * Reports a real execution error. A {@code retryable} error is retried up
	 * to the unit's real-failure budget; any other fails the phase at once.
	 */
	synchronized Outcome fail(Assignment assignment, double now, String reason, boolean retryable)
	{
		noteTime(now);
		Inflight run = releaseInflight(assignment.getToken());
		UnitRecord record = units.get(Integer.valueOf(assignment.getUnitId()));
		NodeRecord node = nodeRecord(assignment.getNode());
		double hub = hubLearns(assignment.getNode(), NetworkModel.MESSAGE_HEADER_BYTES, now);
		if (record == null || run == null || record.token != assignment.getToken()
				|| record.state != UnitState.RUNNING || assignment.isRevoked())
		{
			node.stale++;
			deliver(node, hub);
			dispatch(now);
			return Outcome.STALE;
		}
		addEvent(now, assignment.getNode(), record.spec, "failed",
				reason == null ? "Execution failed." : reason);
		record.realFailures++;
		if (!retryable || record.realFailures >= options.maxRealFailuresPerUnit)
		{
			node.failed++;
			record.failures++;
			record.token = 0L;
			record.state = UnitState.FAILED;
			failed = true;
			failureReason = retryable
					? "Work unit " + record.spec.getKey() + " failed "
							+ record.realFailures + " time(s) with a real error; last on node "
							+ assignment.getNode() + ": " + (reason == null ? "unknown" : reason)
					: "Work unit " + record.spec.getKey() + " failed on node " + assignment.getNode()
							+ " (not retried: the work itself failed, so a retry would fail the same way): "
							+ (reason == null ? "unknown" : reason);
			addEvent(now, assignment.getNode(), record.spec, "failed-final", failureReason);
			revokeAll("the phase failed");
			return Outcome.FINAL_FAILURE;
		}
		Outcome outcome = retry(record, assignment.getNode(), now, reason);
		deliver(node, hub);
		dispatch(now);
		return outcome;
	}

	/**
	 * Operator state of a node: ACTIVE, PAUSED or UNAVAILABLE. A paused node
	 * starts nothing new; an unavailable node also hands back what the
	 * coordinator planned or delivered for it. (Crashes use {@link #crash}.)
	 */
	synchronized void setNodeState(int node, NodeState state, double now)
	{
		requireNode(node);
		noteTime(now);
		NodeRecord record = nodeRecord(node);
		NodeState next = state == null || state == NodeState.DOWN ? NodeState.ACTIVE : state;
		if (record.operatorState == next)
		{
			return;
		}
		record.operatorState = next;
		addEvent(now, node, null,
				next == NodeState.ACTIVE ? "resumed"
						: (next == NodeState.PAUSED ? "paused" : "unavailable"),
				"Node " + node + " is now " + next.name().toLowerCase(Locale.US) + ".");
		if (next == NodeState.UNAVAILABLE)
		{
			releaseNodeWork(record, now, true);
		}
		else if (next == NodeState.PAUSED && mode == ExecutionMode.PULL)
		{
			//A paused node gives its prefetched units back to the shared backlog.
			releaseNodeWork(record, now, false);
		}
		if (next == NodeState.ACTIVE)
		{
			deliver(record, now);
		}
		dispatch(now);
	}

	/**
	 * Makes the node unavailable and revokes its running executions, so their
	 * units start over elsewhere. The revoked executions are marked revoked
	 * at once, so code that checks {@link Assignment#isRevoked()} stops early;
	 * a revoked result that still arrives is discarded.
	 *
	 * @return ids of the revoked units
	 */
	synchronized List<Integer> drain(int node, double now)
	{
		requireNode(node);
		noteTime(now);
		NodeRecord record = nodeRecord(node);
		ArrayList<Integer> revoked = new ArrayList<Integer>();
		if (record.operatorState != NodeState.UNAVAILABLE)
		{
			record.operatorState = NodeState.UNAVAILABLE;
			addEvent(now, node, null, "unavailable",
					"Node " + node + " is draining.");
		}
		ArrayList<UnitRecord> toRequeue = revokeRunning(node, "the node was drained", now);
		for (UnitRecord unit : toRequeue)
		{
			revoked.add(Integer.valueOf(unit.spec.getId()));
			addEvent(now, node, unit.spec, "revoked",
					"Execution revoked by drain; the unit starts over elsewhere.");
		}
		releaseNodeWork(record, now, true);
		for (UnitRecord unit : toRequeue)
		{
			unit.retryFirst = true;
		}
		distribute(toRequeue, now);
		deliverAll(now);
		dispatch(now);
		return revoked;
	}

	/**
	 * The node crashes at {@code now}: its running executions and delivered
	 * units are lost. The coordinator does not know yet; call
	 * {@link #detectCrash} when the detection timeout expires.
	 *
	 * @return ids of the units whose executions were lost
	 */
	synchronized List<Integer> crash(int node, double now)
	{
		requireNode(node);
		noteTime(now);
		NodeRecord record = nodeRecord(node);
		ArrayList<Integer> lost = new ArrayList<Integer>();
		if (record.down)
		{
			return lost;
		}
		boolean failedBefore = failed;
		record.down = true;
		record.crashDetected = false;
		record.downSince = now;
		addEvent(now, node, null, "crashed", "Node " + node + " crashed; its running work is lost.");
		for (UnitRecord unit : revokeRunning(node, "the node crashed", now))
		{
			lost.add(Integer.valueOf(unit.spec.getId()));
			record.lostToCrash++;
			unit.crashLosses++;
			if (unit.crashLosses >= MAX_CRASH_LOSSES_PER_UNIT && !failed)
			{
				failed = true;
				failureReason = "Work unit " + unit.spec.getKey() + " was lost to node crashes "
						+ unit.crashLosses + " times: it takes longer than the time between the"
						+ " nodes' crashes, so it can never finish. Raise the nodes' MTBF (or lower"
						+ " their crash rate) and run again.";
				addEvent(now, node, unit.spec, "failed-final", failureReason);
			}
			unit.state = UnitState.RESERVED;
			unit.node = node;
			unit.retryFirst = true;
			//Until the coordinator notices, it still believes the unit is there.
			record.localQueue.addFirst(Integer.valueOf(unit.spec.getId()));
			addEvent(now, node, unit.spec, "lost", "Execution lost in the crash of node " + node + ".");
		}
		for (Integer id : record.localQueue)
		{
			UnitRecord unit = units.get(id);
			unit.readyAt = Double.POSITIVE_INFINITY;
		}
		if (failed && !failedBefore)
		{
			revokeAll("the phase failed");
		}
		dispatch(now);
		return lost;
	}

	/**
	 * The coordinator notices that the node crashed: everything it had planned,
	 * delivered or running there is rescheduled onto the remaining nodes (units
	 * whose data lives on the crashed node wait for its repair).
	 */
	synchronized void detectCrash(int node, double now)
	{
		requireNode(node);
		noteTime(now);
		NodeRecord record = nodeRecord(node);
		if (!record.down || record.crashDetected)
		{
			return;
		}
		record.crashDetected = true;
		addEvent(now, node, null, "detected", String.format(Locale.US,
				"Coordinator detected the crash of node %d after %.1f; its work is rescheduled.",
				node, now - record.downSince));
		releaseNodeWork(record, now, true);
		deliverAll(now);
		dispatch(now);
	}

	/** The node is repaired and rejoins, empty. */
	synchronized void recover(int node, double now)
	{
		requireNode(node);
		noteTime(now);
		NodeRecord record = nodeRecord(node);
		if (!record.down)
		{
			return;
		}
		addEvent(now, node, null, "recovered", String.format(Locale.US,
				"Node %d repaired after %.1f.", node, now - record.downSince));
		//Delivered units died with the node; the rejoin tells the coordinator.
		if (!record.crashDetected)
		{
			ArrayList<UnitRecord> lost = new ArrayList<UnitRecord>();
			for (Integer id : record.localQueue)
			{
				lost.add(units.get(id));
			}
			record.localQueue.clear();
			for (UnitRecord unit : lost)
			{
				unit.state = UnitState.PLANNED;
				unit.node = node;
				unit.readyAt = 0.0;
			}
			for (int index = lost.size() - 1; index >= 0; index--)
			{
				record.plan.addFirst(Integer.valueOf(lost.get(index).spec.getId()));
			}
		}
		record.down = false;
		record.crashDetected = false;
		if (mode != ExecutionMode.PULL && !backlog.isEmpty())
		{
			//Units that waited for this node's data are planned again (home first
			//under data-local policies).
			ArrayList<UnitRecord> waiting = new ArrayList<UnitRecord>(backlog);
			backlog.clear();
			distribute(waiting, now);
		}
		deliverAll(now);
		dispatch(now);
	}

	/**
	 * Moves up to {@code count} units that have not started to the node. Other
	 * nodes keep working on everything else; nobody waits.
	 */
	synchronized int redirect(int node, int count, double now)
	{
		requireNode(node);
		noteTime(now);
		NodeRecord target = nodeRecord(node);
		if (!target.isActive() || count <= 0)
		{
			return 0;
		}
		int moved = 0;
		Iterator<UnitRecord> fromBacklog = backlog.iterator();
		while (moved < count && fromBacklog.hasNext())
		{
			UnitRecord unit = fromBacklog.next();
			if (!unit.spec.isAllowedOn(node) || !dataAvailable(unit))
			{
				continue;
			}
			fromBacklog.remove();
			plan(unit, target, false);
			addEvent(now, node, unit.spec, "redirected",
					"Operator redirected the unit to node " + node + ".");
			moved++;
		}
		while (moved < count)
		{
			NodeRecord donor = null;
			double donorWork = -1.0;
			for (NodeRecord candidate : nodes)
			{
				if (candidate == target || (candidate.plan.isEmpty() && candidate.localQueue.isEmpty()))
				{
					continue;
				}
				double work = queuedWork(candidate);
				if (work > donorWork)
				{
					donor = candidate;
					donorWork = work;
				}
			}
			if (donor == null)
			{
				break;
			}
			UnitRecord unit = takeTailAllowed(donor, node);
			if (unit == null)
			{
				break;
			}
			plan(unit, target, false);
			addEvent(now, node, unit.spec, "redirected",
					"Operator moved the unit from node " + donor.spec.getIndex()
					+ " to node " + node + ".");
			moved++;
		}
		deliverAll(now);
		dispatch(now);
		return moved;
	}

	/** Replaces node specs (live capacity changes); plans made earlier stay. */
	synchronized void updateSpecs(List<NodeSpec> specs, double now)
	{
		noteTime(now);
		if (specs == null)
		{
			return;
		}
		for (NodeSpec spec : specs)
		{
			if (spec != null && spec.getIndex() >= 1 && spec.getIndex() <= nodes.length)
			{
				nodes[spec.getIndex() - 1].spec = spec;
			}
		}
		deliverAll(now);
		dispatch(now);
	}

	/** Switches policy for every unit that has not started yet. */
	synchronized void switchPolicy(SchedulerLabEngine.SchedulingPolicy next, double now)
	{
		noteTime(now);
		if (next == null || next == policy)
		{
			return;
		}
		ArrayList<UnitRecord> notStarted = new ArrayList<UnitRecord>();
		for (UnitRecord unit : units.values())
		{
			if (unit.state == UnitState.BACKLOG || unit.state == UnitState.PLANNED
					|| (unit.state == UnitState.RESERVED && !nodeRecord(unit.node).down))
			{
				notStarted.add(unit);
			}
		}
		for (NodeRecord node : nodes)
		{
			node.plan.clear();
			if (!node.down)
			{
				node.localQueue.clear();
			}
		}
		backlog.clear();
		for (UnitRecord unit : notStarted)
		{
			unit.state = UnitState.BACKLOG;
			unit.node = 0;
		}
		policy = next;
		mode = modeOf(next);
		backlog = new TreeSet<UnitRecord>(backlogOrder(next));
		addEvent(now, 0, null, "policy",
				"Remaining " + notStarted.size() + " unit(s) now follow "
				+ next.getLabel() + ".");
		distribute(notStarted, now);
		deliverAll(now);
		dispatch(now);
	}

	synchronized boolean isComplete()
	{
		return committedCount == units.size();
	}

	synchronized boolean hasFailed()
	{
		return failed;
	}

	synchronized String getFailureReason()
	{
		return failureReason;
	}

	synchronized SchedulerLabEngine.SchedulingPolicy getPolicy()
	{
		return policy;
	}

	synchronized ExecutionMode getMode()
	{
		return mode;
	}

	String getPhaseKey()
	{
		return phaseKey;
	}

	NetworkModel getNetwork()
	{
		return network;
	}

	double getOpenedAt()
	{
		return options.openedAt;
	}

	/** The units of this phase, in submission order. */
	List<WorkUnitSpec> getUnitSpecs()
	{
		return unitSpecs;
	}

	synchronized int getUnitCount()
	{
		return units.size();
	}

	synchronized int getCommittedCount()
	{
		return committedCount;
	}

	/** Committed fraction of the phase's units, in [0, 1]. */
	synchronized double progress()
	{
		return units.isEmpty() ? 1.0 : committedCount / (double)units.size();
	}

	/** Time of the last commit, NaN before the first. */
	synchronized double getLastCommitTime()
	{
		return lastCommit;
	}

	/** Time the first execution was assigned, NaN before it. */
	synchronized double getFirstStartTime()
	{
		return firstStart;
	}

	synchronized int count(UnitState state)
	{
		int count = 0;
		for (UnitRecord unit : units.values())
		{
			if (unit.state == state)
			{
				count++;
			}
		}
		return count;
	}

	synchronized UnitState stateOf(int unitId)
	{
		UnitRecord record = units.get(Integer.valueOf(unitId));
		return record == null ? null : record.state;
	}

	/** Node that committed the unit, 0 when not committed. */
	synchronized int committedNodeOf(int unitId)
	{
		UnitRecord record = units.get(Integer.valueOf(unitId));
		return record == null || record.state != UnitState.COMMITTED ? 0 : record.node;
	}

	synchronized boolean isLive(Assignment assignment)
	{
		UnitRecord record = units.get(Integer.valueOf(assignment.getUnitId()));
		return record != null && record.state == UnitState.RUNNING
				&& record.token == assignment.getToken() && !assignment.isRevoked();
	}

	synchronized List<NodeStats> nodeStats()
	{
		ArrayList<NodeStats> stats = new ArrayList<NodeStats>();
		for (NodeRecord node : nodes)
		{
			stats.add(new NodeStats(node, node.effectiveState()));
		}
		return stats;
	}

	synchronized NodeState nodeState(int node)
	{
		requireNode(node);
		return nodeRecord(node).effectiveState();
	}

	synchronized List<NodeSpec> nodeSpecs()
	{
		ArrayList<NodeSpec> specs = new ArrayList<NodeSpec>();
		for (NodeRecord node : nodes)
		{
			specs.add(node.spec);
		}
		return specs;
	}

	/**
	 * Lower bound of this phase on the current specs, its descriptors and data
	 * transfers included (see {@link PlacementPlanner#bound}); join times are
	 * not known yet.
	 */
	synchronized double lowerBound()
	{
		return PlacementPlanner.bound(unitSpecs, nodeSpecs(), null, network != null && !network.isFree()).value;
	}

	/** Ids of units not started yet, in the order they would start (preview). */
	synchronized List<Integer> waitingUnits(int limit)
	{
		ArrayList<Integer> ids = new ArrayList<Integer>();
		for (NodeRecord node : nodes)
		{
			for (Integer id : node.localQueue)
			{
				if (ids.size() >= limit)
				{
					return ids;
				}
				ids.add(id);
			}
		}
		for (NodeRecord node : nodes)
		{
			for (Integer id : node.plan)
			{
				if (ids.size() >= limit)
				{
					return ids;
				}
				ids.add(id);
			}
		}
		for (UnitRecord unit : backlog)
		{
			if (ids.size() >= limit)
			{
				return ids;
			}
			ids.add(Integer.valueOf(unit.spec.getId()));
		}
		return ids;
	}

	synchronized List<Integer> committedUnits(int limit)
	{
		ArrayList<Integer> ids = new ArrayList<Integer>();
		for (UnitRecord unit : units.values())
		{
			if (unit.state == UnitState.COMMITTED)
			{
				ids.add(Integer.valueOf(unit.spec.getId()));
				if (ids.size() >= limit)
				{
					break;
				}
			}
		}
		return ids;
	}

	/** Copy of retained events, oldest first. */
	synchronized List<Event> events()
	{
		return new ArrayList<Event>(events);
	}

	synchronized int droppedEventCount()
	{
		return droppedEvents;
	}

	/**
	 * True when no unit can make progress until an operator acts: nothing is
	 * running and no node that is up is active.
	 */
	synchronized boolean isBlockedByOperator()
	{
		if (isComplete() || failed)
		{
			return false;
		}
		if (!inflight.isEmpty())
		{
			return false;
		}
		for (NodeRecord node : nodes)
		{
			if (node.isActive())
			{
				return false;
			}
		}
		return true;
	}

	/** True while some node is down (work may be waiting for its repair). */
	synchronized boolean isWaitingForRepair()
	{
		for (NodeRecord node : nodes)
		{
			if (node.down)
			{
				return true;
			}
		}
		return false;
	}

	// ------------------------------------------------------------ internals

	private double toMs(double timeUnits)
	{
		return timeUnits * options.msPerTimeUnit;
	}

	private double toUnits(double milliseconds)
	{
		return milliseconds / options.msPerTimeUnit;
	}

	/** When the coordinator learns of an event a node reported at {@code now}. */
	private double hubLearns(int node, long bytes, double now)
	{
		return toUnits(network.send(node, NetworkModel.HUB, bytes, toMs(now)));
	}

	private void requireFeasible(WorkUnitSpec spec)
	{
		boolean anyNode = false;
		int largestFit = 0;
		for (NodeRecord node : nodes)
		{
			if (spec.isAllowedOn(node.spec.getIndex()))
			{
				anyNode = true;
				largestFit = Math.max(largestFit, node.spec.getMemoryMb());
			}
		}
		if (!anyNode)
		{
			throw new IllegalArgumentException(
					"Work unit " + spec + " is not allowed on any node.");
		}
		if (spec.getMemoryMb() > largestFit
				|| spec.getMemoryMb() > options.globalMemoryBudgetMb)
		{
			throw new IllegalArgumentException(
					"Work unit " + spec + " needs " + spec.getMemoryMb()
					+ " MB but the largest eligible node budget is " + largestFit
					+ " MB; it could never start.");
		}
	}

	/** Its input data can be read: no data, or its home node is up. */
	private boolean dataAvailable(UnitRecord unit)
	{
		int home = unit.spec.getHomeNode();
		return home <= 0 || home > nodes.length || unit.spec.getInputBytes() <= 0L
				|| !nodeRecord(home).down;
	}

	/** True when the coordinator may give the node new work. */
	private boolean acceptsWork(NodeRecord node)
	{
		return !node.down && node.operatorState == NodeState.ACTIVE;
	}

	/**
	 * True when the coordinator believes the node can take planned work: it
	 * is up (or its crash is not detected yet) and not unavailable.
	 */
	private boolean plannable(NodeRecord node)
	{
		return (!node.down || !node.crashDetected) && node.operatorState != NodeState.UNAVAILABLE;
	}

	/** Places units that have not started according to the current mode. */
	private void distribute(List<UnitRecord> toPlace, double now)
	{
		if (toPlace.isEmpty())
		{
			return;
		}
		if (mode == ExecutionMode.PULL)
		{
			for (UnitRecord unit : toPlace)
			{
				toBacklog(unit);
			}
			return;
		}
		//Plan onto active nodes; fall back to paused ones (they will resume),
		//never onto unavailable nodes or nodes known to be down.
		List<NodeSpec> eligible = new ArrayList<NodeSpec>();
		for (NodeRecord node : nodes)
		{
			//The coordinator still plans onto a crashed node it has not detected.
			if (plannable(node) && node.operatorState == NodeState.ACTIVE)
			{
				eligible.add(node.spec);
			}
		}
		if (eligible.isEmpty())
		{
			for (NodeRecord node : nodes)
			{
				if (plannable(node))
				{
					eligible.add(node.spec);
				}
			}
		}
		if (eligible.isEmpty())
		{
			for (UnitRecord unit : toPlace)
			{
				toBacklog(unit);
			}
			return;
		}
		SchedulerLabEngine.SchedulingPolicy planPolicy =
				mode == ExecutionMode.STEAL
						? SchedulerLabEngine.SchedulingPolicy.CAPACITY_WEIGHTED : policy;
		//Plan over a compact index space of eligible nodes, then map back.
		ArrayList<NodeSpec> compact = new ArrayList<NodeSpec>();
		int[] original = new int[eligible.size()];
		double[] baseLoad = new double[eligible.size()];
		for (int index = 0; index < eligible.size(); index++)
		{
			NodeSpec spec = eligible.get(index);
			original[index] = spec.getIndex();
			compact.add(spec.reindexed(index + 1));
			baseLoad[index] = queuedWork(nodeRecord(spec.getIndex()))
					* nodeRecord(spec.getIndex()).spec.getThroughput();
		}
		ArrayList<WorkUnitSpec> specs = new ArrayList<WorkUnitSpec>();
		LinkedHashMap<Integer,UnitRecord> byId = new LinkedHashMap<Integer,UnitRecord>();
		for (UnitRecord unit : toPlace)
		{
			if (!allowedOnAny(unit.spec, original) || !dataAvailable(unit))
			{
				//Data-bound work whose nodes are all gone waits for them.
				toBacklog(unit);
				continue;
			}
			specs.add(compactSpec(unit.spec, original));
			byId.put(Integer.valueOf(unit.spec.getId()), unit);
		}
		if (specs.isEmpty())
		{
			return;
		}
		PlacementPlanner.Plan planned = PlacementPlanner.plan(
				planPolicy, specs, compact, baseLoad, true, false,
				options.msPerTimeUnit);
		for (int index = 0; index < compact.size(); index++)
		{
			NodeRecord node = nodeRecord(original[index]);
			for (WorkUnitSpec spec : planned.unitsFor(index + 1))
			{
				UnitRecord unit = byId.get(Integer.valueOf(spec.getId()));
				boolean first = unit.retryFirst;
				unit.retryFirst = false;
				plan(unit, node, first);
			}
		}
	}

	private void toBacklog(UnitRecord unit)
	{
		unit.state = UnitState.BACKLOG;
		unit.node = 0;
		unit.readyAt = 0.0;
		backlog.add(unit);
	}

	private void plan(UnitRecord unit, NodeRecord node, boolean front)
	{
		unit.state = UnitState.PLANNED;
		unit.node = node.spec.getIndex();
		unit.readyAt = 0.0;
		if (front)
		{
			node.plan.addFirst(Integer.valueOf(unit.spec.getId()));
		}
		else
		{
			node.plan.addLast(Integer.valueOf(unit.spec.getId()));
		}
	}

	private static boolean allowedOnAny(WorkUnitSpec spec, int[] nodeIndexes)
	{
		for (int node : nodeIndexes)
		{
			if (spec.isAllowedOn(node))
			{
				return true;
			}
		}
		return false;
	}

	private WorkUnitSpec compactSpec(WorkUnitSpec spec, int[] original)
	{
		int home = 0;
		ArrayList<Integer> allowed = new ArrayList<Integer>();
		for (int index = 0; index < original.length; index++)
		{
			if (original[index] == spec.getHomeNode())
			{
				home = index + 1;
			}
			if (spec.isAllowedOn(original[index]))
			{
				allowed.add(Integer.valueOf(index + 1));
			}
		}
		int[] allowedArray = null;
		if (spec.isRestricted())
		{
			if (allowed.isEmpty())
			{
				throw new IllegalStateException(
						"Unit " + spec + " has no eligible node left.");
			}
			allowedArray = new int[allowed.size()];
			for (int index = 0; index < allowed.size(); index++)
			{
				allowedArray[index] = allowed.get(index).intValue();
			}
		}
		if (home == 0 && spec.getHomeNode() > 0 && spec.getInputBytes() > 0L)
		{
			//The home node is not in the plan (unavailable or paused-out): the
			//data still has to come from it, so price it as remote everywhere.
			home = 0;
		}
		return spec.withNodes(home, allowedArray);
	}

	private void deliverAll(double now)
	{
		for (NodeRecord node : nodes)
		{
			deliver(node, now);
		}
	}

	/**
	 * Delivers planned units to the node while it has room: a descriptor over
	 * its link and, away from home, the unit's input data. {@code hubTime} is
	 * when the coordinator acts.
	 */
	private void deliver(NodeRecord node, double hubTime)
	{
		if (!acceptsWork(node))
		{
			return;
		}
		while (outstanding(node) < node.spec.getQueueCapacity() && !node.plan.isEmpty())
		{
			UnitRecord unit = null;
			Iterator<Integer> iterator = node.plan.iterator();
			while (iterator.hasNext())
			{
				UnitRecord candidate = units.get(iterator.next());
				if (dataAvailable(candidate))
				{
					unit = candidate;
					iterator.remove();
					break;
				}
			}
			if (unit == null)
			{
				return;
			}
			sendToNode(unit, node, hubTime, false, 0);
		}
	}

	/** Moves a unit into the node's local queue and prices its transfers. */
	private void sendToNode(UnitRecord unit, NodeRecord node, double hubTime,
			boolean front, int viaVictim)
	{
		int index = node.spec.getIndex();
		double hubMs = toMs(hubTime);
		double descriptor;
		if (viaVictim > 0)
		{
			//The victim hands the unit over: the coordinator asks it, it sends.
			double asked = network.send(NetworkModel.HUB, viaVictim,
					NetworkModel.MESSAGE_HEADER_BYTES, hubMs);
			descriptor = network.send(viaVictim, index, NetworkModel.UNIT_DESCRIPTOR_BYTES, asked);
		}
		else
		{
			descriptor = network.send(NetworkModel.HUB, index,
					NetworkModel.UNIT_DESCRIPTOR_BYTES, hubMs);
		}
		double data = hubMs;
		long moved = 0L;
		int home = unit.spec.getHomeNode();
		if (home > 0 && home <= nodes.length && home != index && unit.spec.getInputBytes() > 0L)
		{
			moved = unit.spec.getInputBytes();
			data = network.send(home, index, moved, hubMs);
		}
		unit.state = UnitState.RESERVED;
		unit.node = index;
		unit.readyAt = toUnits(Math.max(descriptor, data));
		unit.deliveredBytes = moved;
		node.deliveries++;
		if (front)
		{
			node.localQueue.addFirst(Integer.valueOf(unit.spec.getId()));
		}
		else
		{
			node.localQueue.addLast(Integer.valueOf(unit.spec.getId()));
		}
		notePeak(node);
		addEvent(hubTime, index, unit.spec, "delivered",
				String.format(Locale.US, "Delivered to node %d, ready at %.3f%s.",
						index, unit.readyAt, moved > 0L ? " with " + moved + " input bytes" : ""));
	}

	private void notePeak(NodeRecord node)
	{
		node.queuePeak = Math.max(node.queuePeak, node.inflight + node.localQueue.size());
	}

	/**
	 * Returns the node's planned and delivered units to the coordinator and
	 * redistributes them (or keeps a paused static node's plan).
	 *
	 * @param handOver true when the units leave the node (unavailable, drained,
	 *                 crash detected); false for a paused pull node, whose
	 *                 prefetched units return to the shared backlog
	 */
	private void releaseNodeWork(NodeRecord node, double now, boolean handOver)
	{
		ArrayList<UnitRecord> released = new ArrayList<UnitRecord>();
		for (Integer id : node.localQueue)
		{
			released.add(units.get(id));
		}
		node.localQueue.clear();
		if (handOver)
		{
			for (Integer id : node.plan)
			{
				released.add(units.get(id));
			}
			node.plan.clear();
		}
		if (released.isEmpty())
		{
			return;
		}
		for (UnitRecord unit : released)
		{
			unit.state = UnitState.BACKLOG;
			unit.node = 0;
			unit.readyAt = 0.0;
		}
		addEvent(now, node.spec.getIndex(), null, "released",
				released.size() + " unit(s) released from node "
				+ node.spec.getIndex() + ".");
		distribute(released, now);
	}

	/** Revokes every running execution on the node; returns their units, requeue-ready. */
	private ArrayList<UnitRecord> revokeRunning(int node, String reason, double now)
	{
		ArrayList<UnitRecord> revoked = new ArrayList<UnitRecord>();
		ArrayList<Long> tokens = new ArrayList<Long>();
		for (Map.Entry<Long,Inflight> entry : inflight.entrySet())
		{
			if (entry.getValue().node == node)
			{
				tokens.add(entry.getKey());
			}
		}
		for (Long token : tokens)
		{
			Inflight run = inflight.get(token);
			run.assignment.revoke(reason);
			releaseInflight(token.longValue());
			UnitRecord unit = units.get(Integer.valueOf(run.unitId));
			if (unit != null && unit.state == UnitState.RUNNING && unit.token == token.longValue())
			{
				unit.state = UnitState.BACKLOG;
				unit.token = 0L;
				revoked.add(unit);
			}
		}
		//Assignments made for waiting lanes of this node but not picked up yet.
		Iterator<Map.Entry<Integer,Assignment>> pendingIterator = pending.entrySet().iterator();
		while (pendingIterator.hasNext())
		{
			Map.Entry<Integer,Assignment> entry = pendingIterator.next();
			if (entry.getValue().getNode() == node)
			{
				Assignment orphan = entry.getValue();
				orphan.revoke(reason);
				pendingIterator.remove();
				UnitRecord unit = units.get(Integer.valueOf(orphan.getUnitId()));
				if (unit != null && unit.state == UnitState.RUNNING && unit.token == orphan.getToken())
				{
					unit.state = UnitState.BACKLOG;
					unit.token = 0L;
					revoked.add(unit);
				}
			}
		}
		return revoked;
	}

	private void revokeAll(String reason)
	{
		for (Inflight run : inflight.values())
		{
			run.assignment.revoke(reason);
		}
		for (Assignment orphan : pending.values())
		{
			orphan.revoke(reason);
		}
	}

	private Outcome retry(UnitRecord record, int failedNode, double now, String reason)
	{
		NodeRecord node = nodeRecord(failedNode);
		node.failed++;
		record.failures++;
		record.token = 0L;
		record.excluded.set(failedNode);
		int budget = record.spec.getMaxFailures() > 0
				? record.spec.getMaxFailures() : options.maxFailuresPerUnit;
		if (record.failures >= budget)
		{
			record.state = UnitState.FAILED;
			failed = true;
			failureReason = "Work unit " + record.spec.getKey() + " failed "
					+ record.failures + " time(s); last failure on node "
					+ failedNode + ": " + (reason == null ? "unknown" : reason);
			addEvent(now, failedNode, record.spec, "failed-final", failureReason);
			revokeAll("the phase failed");
			return Outcome.FINAL_FAILURE;
		}
		record.state = UnitState.BACKLOG;
		requeueFront(record, failedNode, now);
		addEvent(now, failedNode, record.spec, "retried",
				"Retry " + record.failures + " queued"
				+ (policy == SchedulerLabEngine.SchedulingPolicy.FAILURE_AWARE_RESCHEDULING
						? " away from node " + failedNode + "." : "."));
		return Outcome.RETRY;
	}

	/** Puts a unit that must start over at the front of the right queue. */
	private void requeueFront(UnitRecord record, int previousNode, double now)
	{
		if (mode == ExecutionMode.PULL)
		{
			record.retryFirst = true;
			toBacklog(record);
			return;
		}
		NodeRecord node = previousNode >= 1 && previousNode <= nodes.length
				? nodeRecord(previousNode) : null;
		if (node != null && plannable(node) && !node.down
				&& record.spec.isAllowedOn(previousNode))
		{
			//Retried where it failed, first in line (static semantics).
			plan(record, node, true);
			deliver(node, now);
			return;
		}
		record.retryFirst = true;
		ArrayList<UnitRecord> one = new ArrayList<UnitRecord>();
		one.add(record);
		distribute(one, now);
		deliverAll(now);
	}

	/** Matches waiting lanes with units according to the mode. */
	private void dispatch(double now)
	{
		if (failed)
		{
			return;
		}
		boolean progress = true;
		while (progress && !waitingLanes.isEmpty())
		{
			progress = false;
			ArrayList<Integer> lanes = new ArrayList<Integer>(waitingLanes);
			for (Integer laneKey : lanes)
			{
				if (!waitingLanes.contains(laneKey))
				{
					continue;
				}
				int node = laneKey.intValue() / 4096;
				int lane = laneKey.intValue() % 4096;
				NodeRecord record = nodeRecord(node);
				if (!acceptsWork(record))
				{
					continue;
				}
				if (startFromLocalQueue(record, lane, now))
				{
					progress = true;
					continue;
				}
				if (!record.plan.isEmpty())
				{
					//The node's next planned unit is on its way (or blocked on data).
					deliver(record, hubTimeFor(laneKey, now));
					if (startFromLocalQueue(record, lane, now))
					{
						progress = true;
						continue;
					}
				}
				if (mode == ExecutionMode.STEAL && startStolen(record, lane, now))
				{
					progress = true;
				}
			}
			if (!backlog.isEmpty() && assignFromBacklog(now))
			{
				progress = true;
			}
		}
	}

	/**
	 * When the coordinator can act for a waiting lane: once its request has
	 * arrived (the request message is sent only when the lane cannot be
	 * served from its own queue) and not before {@code now}.
	 */
	private double hubTimeFor(Integer laneKey, double now)
	{
		double[] request = requests.get(laneKey);
		if (request == null)
		{
			return now;
		}
		if (Double.isNaN(request[1]))
		{
			int node = laneKey.intValue() / 4096;
			request[1] = toUnits(network.send(node, NetworkModel.HUB,
					NetworkModel.MESSAGE_HEADER_BYTES, toMs(request[0])));
		}
		return Math.max(now, request[1]);
	}

	private boolean startFromLocalQueue(NodeRecord node, int lane, double now)
	{
		Integer head = node.localQueue.peekFirst();
		if (head == null)
		{
			return false;
		}
		UnitRecord unit = units.get(head);
		if (!memoryFits(node, unit.spec))
		{
			return false;
		}
		node.localQueue.pollFirst();
		start(unit, node, lane, Source.LOCAL, now, Math.max(now, unit.readyAt), unit.deliveredBytes);
		return true;
	}

	private boolean startStolen(NodeRecord thief, int lane, double now)
	{
		if (!thief.localQueue.isEmpty() || !thief.plan.isEmpty())
		{
			return false;
		}
		NodeRecord victim = null;
		double victimWork = 0.0;
		for (NodeRecord candidate : nodes)
		{
			if (candidate == thief || (candidate.plan.isEmpty() && candidate.localQueue.isEmpty()))
			{
				continue;
			}
			double work = queuedWork(candidate);
			if (victim == null || work > victimWork)
			{
				victim = candidate;
				victimWork = work;
			}
		}
		if (victim == null)
		{
			return false;
		}
		int laneKey = laneKey(thief.spec.getIndex(), lane);
		double hubTime = hubTimeFor(Integer.valueOf(laneKey), now);
		//Prefer the coordinator's copy of the victim's plan: no victim round trip.
		Iterator<Integer> planned = victim.plan.descendingIterator();
		while (planned.hasNext())
		{
			UnitRecord unit = units.get(planned.next());
			if (stealable(unit, thief))
			{
				planned.remove();
				takeStolen(unit, thief, victim, lane, now, hubTime, 0);
				return true;
			}
		}
		if (victim.down)
		{
			return false;
		}
		Iterator<Integer> delivered = victim.localQueue.descendingIterator();
		while (delivered.hasNext())
		{
			UnitRecord unit = units.get(delivered.next());
			if (stealable(unit, thief))
			{
				delivered.remove();
				takeStolen(unit, thief, victim, lane, now, hubTime, victim.spec.getIndex());
				return true;
			}
		}
		return false;
	}

	private boolean stealable(UnitRecord unit, NodeRecord thief)
	{
		return unit.spec.isAllowedOn(thief.spec.getIndex())
				&& memoryFits(thief, unit.spec)
				&& dataAvailable(unit);
	}

	private void takeStolen(UnitRecord unit, NodeRecord thief, NodeRecord victim, int lane,
			double now, double hubTime, int viaVictim)
	{
		sendToNode(unit, thief, hubTime, true, viaVictim);
		thief.localQueue.pollFirst();
		thief.stolen++;
		start(unit, thief, lane, Source.STOLEN, now, Math.max(now, unit.readyAt), unit.deliveredBytes);
		addEvent(now, thief.spec.getIndex(), unit.spec, "stolen",
				"Stolen from node " + victim.spec.getIndex()
				+ (viaVictim > 0 ? "'s local queue." : "'s plan at the coordinator."));
	}

	/** Pull matching: walk the backlog in policy order, give each unit a lane. */
	private boolean assignFromBacklog(double now)
	{
		boolean assigned = false;
		Iterator<UnitRecord> iterator = new ArrayList<UnitRecord>(backlog).iterator();
		while (iterator.hasNext() && !waitingLanes.isEmpty())
		{
			UnitRecord unit = iterator.next();
			if (unit.state != UnitState.BACKLOG || !backlog.contains(unit) || !dataAvailable(unit))
			{
				continue;
			}
			int bestLane = -1;
			NodeRecord bestNode = null;
			for (Integer laneKey : waitingLanes)
			{
				int node = laneKey.intValue() / 4096;
				NodeRecord record = nodeRecord(node);
				if (!acceptsWork(record)
						|| !unit.spec.isAllowedOn(node)
						|| !memoryFits(record, unit.spec)
						|| isExcluded(unit, node))
				{
					continue;
				}
				if (!record.localQueue.isEmpty()
						|| (mode != ExecutionMode.PULL && !record.plan.isEmpty()))
				{
					//A lane serves units already delivered to its node first (they
					//count against its queue capacity); static and stealing nodes
					//also serve their own plan first.
					continue;
				}
				if (bestNode == null || prefer(record, bestNode, unit))
				{
					bestNode = record;
					bestLane = laneKey.intValue();
				}
			}
			if (bestNode == null)
			{
				continue;
			}
			backlog.remove(unit);
			unit.retryFirst = false;
			double hubTime = hubTimeFor(Integer.valueOf(bestLane), now);
			sendToNode(unit, bestNode, hubTime, true, 0);
			bestNode.localQueue.pollFirst();
			start(unit, bestNode, bestLane % 4096, Source.HUB, now,
					Math.max(now, unit.readyAt), unit.deliveredBytes);
			assigned = true;
			prefetch(bestNode, hubTime);
		}
		return assigned;
	}

	/** True when {@code candidate} should get {@code unit} before {@code best}. */
	private boolean prefer(NodeRecord candidate, NodeRecord best, UnitRecord unit)
	{
		NodeSpec first = candidate.spec;
		NodeSpec second = best.spec;
		switch (policy)
		{
			case QUEUE_LENGTH:
			{
				double firstLength = outstanding(candidate) / (double)first.getLanes();
				double secondLength = outstanding(best) / (double)second.getLanes();
				if (firstLength != secondLength)
				{
					return firstLength < secondLength;
				}
				break;
			}
			case MEMORY_AWARE_SAFE_WAVE:
			{
				long firstFree = first.getMemoryMb() - candidate.memoryInUseMb
						- unit.spec.getMemoryMb();
				long secondFree = second.getMemoryMb() - best.memoryInUseMb
						- unit.spec.getMemoryMb();
				if (firstFree != secondFree)
				{
					return firstFree < secondFree;
				}
				break;
			}
			case FAILURE_AWARE_RESCHEDULING:
			{
				double firstRate = first.getSpeed() * (1.0 - first.getFailureProbability())
						* first.getAvailability();
				double secondRate = second.getSpeed() * (1.0 - second.getFailureProbability())
						* second.getAvailability();
				if (firstRate != secondRate)
				{
					return firstRate > secondRate;
				}
				break;
			}
			default:
				break;
		}
		double firstFinish = expectedFinish(unit.spec, first);
		double secondFinish = expectedFinish(unit.spec, second);
		if (firstFinish != secondFinish)
		{
			return firstFinish < secondFinish;
		}
		return first.getIndex() < second.getIndex();
	}

	/** Execution time plus data movement, in time units, for tie-breaking. */
	private double expectedFinish(WorkUnitSpec unit, NodeSpec spec)
	{
		double transfer = 0.0;
		int home = unit.getHomeNode();
		if (home > 0 && home != spec.getIndex() && home <= nodes.length
				&& unit.getInputBytes() > 0L)
		{
			NodeSpec homeSpec = nodes[home - 1].spec;
			transfer = toUnits(homeSpec.getDelayMs() + homeSpec.serializationMs(unit.getInputBytes())
					+ spec.getDelayMs() + spec.serializationMs(unit.getInputBytes()));
		}
		return unit.durationOn(spec.getSpeed()) + transfer;
	}

	private boolean isExcluded(UnitRecord unit, int node)
	{
		if (policy != SchedulerLabEngine.SchedulingPolicy.FAILURE_AWARE_RESCHEDULING
				|| !unit.excluded.get(node))
		{
			return false;
		}
		//Lift the exclusion when every eligible node has already failed it.
		for (NodeRecord record : nodes)
		{
			int index = record.spec.getIndex();
			if (acceptsWork(record) && unit.spec.isAllowedOn(index)
					&& !unit.excluded.get(index))
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * Pull-mode prefetch up to the node's queue capacity, in policy order. The
	 * coordinator tops a node up whenever it hands it work and whenever it
	 * learns that a unit finished there, so a node with spare queue capacity
	 * never waits for a request/reply round trip.
	 */
	private void prefetch(NodeRecord node, double hubTime)
	{
		if (mode != ExecutionMode.PULL || !acceptsWork(node))
		{
			return;
		}
		Iterator<UnitRecord> iterator = backlog.iterator();
		ArrayList<UnitRecord> taken = new ArrayList<UnitRecord>();
		while (outstanding(node) + taken.size() < node.spec.getQueueCapacity()
				&& iterator.hasNext())
		{
			UnitRecord unit = iterator.next();
			int index = node.spec.getIndex();
			if (!unit.spec.isAllowedOn(index) || isExcluded(unit, index) || !dataAvailable(unit))
			{
				continue;
			}
			iterator.remove();
			unit.retryFirst = false;
			taken.add(unit);
		}
		for (UnitRecord unit : taken)
		{
			sendToNode(unit, node, hubTime, false, 0);
		}
	}

	private void start(UnitRecord unit, NodeRecord node, int lane, Source source,
			double now, double readyAt, long transferredBytes)
	{
		int nodeIndex = node.spec.getIndex();
		unit.state = UnitState.RUNNING;
		unit.node = nodeIndex;
		unit.executions++;
		unit.token = nextToken++;
		node.assigned++;
		node.inflight++;
		node.memoryInUseMb += unit.spec.getMemoryMb();
		node.runningUnits.add(Integer.valueOf(unit.spec.getId()));
		notePeak(node);
		globalMemoryInUseMb += unit.spec.getMemoryMb();
		Assignment assignment = new Assignment(
				unit.spec,
				nodeIndex,
				lane,
				unit.token,
				unit.executions,
				source,
				now,
				readyAt,
				transferredBytes);
		inflight.put(Long.valueOf(unit.token),
				new Inflight(unit.spec.getId(), nodeIndex, unit.spec.getMemoryMb(), assignment));
		int laneKey = laneKey(nodeIndex, lane);
		waitingLanes.remove(Integer.valueOf(laneKey));
		requests.remove(Integer.valueOf(laneKey));
		pending.put(Integer.valueOf(laneKey), assignment);
		if (Double.isNaN(firstStart))
		{
			firstStart = now;
		}
		addEvent(now, nodeIndex, unit.spec, "assigned",
				String.format(Locale.US, "%s, execution %d, ready at %.3f.",
						source == Source.LOCAL ? "Started from node " + nodeIndex + "'s queue"
								: source == Source.STOLEN ? "Started after a steal"
								: "Pulled from the coordinator",
						unit.executions, readyAt));
	}

	private Inflight releaseInflight(long token)
	{
		Inflight run = inflight.remove(Long.valueOf(token));
		if (run == null)
		{
			return null;
		}
		NodeRecord node = nodeRecord(run.node);
		node.inflight = Math.max(0, node.inflight - 1);
		node.memoryInUseMb = Math.max(0L, node.memoryInUseMb - run.memoryMb);
		node.runningUnits.remove(Integer.valueOf(run.unitId));
		globalMemoryInUseMb = Math.max(0L, globalMemoryInUseMb - run.memoryMb);
		return run;
	}

	private boolean memoryFits(NodeRecord node, WorkUnitSpec unit)
	{
		return node.memoryInUseMb + unit.getMemoryMb() <= node.spec.getMemoryMb()
				&& globalMemoryInUseMb + unit.getMemoryMb() <= options.globalMemoryBudgetMb;
	}

	private int outstanding(NodeRecord node)
	{
		return node.inflight + node.localQueue.size();
	}

	/** Remaining planned and delivered work of the node, in time units at its throughput. */
	private double queuedWork(NodeRecord node)
	{
		double work = 0.0;
		for (Integer id : node.localQueue)
		{
			work += units.get(id).spec.durationOn(1.0);
		}
		for (Integer id : node.plan)
		{
			work += units.get(id).spec.durationOn(1.0);
		}
		return work / Math.max(1.0e-9, node.spec.getThroughput());
	}

	private UnitRecord takeTailAllowed(NodeRecord donor, int targetNode)
	{
		Iterator<Integer> tail = donor.plan.descendingIterator();
		while (tail.hasNext())
		{
			UnitRecord unit = units.get(tail.next());
			if (unit.spec.isAllowedOn(targetNode))
			{
				tail.remove();
				return unit;
			}
		}
		if (donor.down)
		{
			return null;
		}
		tail = donor.localQueue.descendingIterator();
		while (tail.hasNext())
		{
			UnitRecord unit = units.get(tail.next());
			if (unit.spec.isAllowedOn(targetNode))
			{
				tail.remove();
				return unit;
			}
		}
		return null;
	}

	/**
	 * Deterministic injected fault: a SplitMix64 hash of (seed, phase, unit,
	 * execution, node) mapped to [0, 1) and compared with the node's fault
	 * probability. The same execution always fails or succeeds.
	 */
	private boolean injectedFailure(UnitRecord unit, Assignment assignment)
	{
		double probability = nodeRecord(assignment.getNode()).spec.getFailureProbability();
		if (probability <= 0.0)
		{
			return false;
		}
		long hash = options.failureSeed;
		hash = mix(hash ^ phaseKey.hashCode());
		hash = mix(hash ^ unit.spec.getKey().hashCode());
		hash = mix(hash ^ ((long)unit.spec.getId() << 20));
		hash = mix(hash ^ ((long)assignment.getExecution() << 40));
		hash = mix(hash ^ assignment.getNode());
		double uniform = (hash >>> 11) * 0x1.0p-53;
		return uniform < probability;
	}

	static long mix(long value)
	{
		long z = value + 0x9E3779B97F4A7C15L;
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		return z ^ (z >>> 31);
	}

	private Comparator<UnitRecord> backlogOrder(SchedulerLabEngine.SchedulingPolicy forPolicy)
	{
		final Comparator<WorkUnitSpec> order = PlacementPlanner.orderFor(forPolicy);
		return new Comparator<UnitRecord>()
		{
			public int compare(UnitRecord first, UnitRecord second)
			{
				if (first.retryFirst != second.retryFirst)
				{
					return first.retryFirst ? -1 : 1;
				}
				int byOrder = order.compare(first.spec, second.spec);
				return byOrder != 0 ? byOrder
						: Integer.compare(first.spec.getId(), second.spec.getId());
			}
		};
	}

	private void addEvent(double now, int node, WorkUnitSpec unit, String type, String detail)
	{
		Event event = new Event(
				now,
				node,
				unit == null ? 0 : unit.getId(),
				unit == null ? "" : unit.getKey(),
				type,
				detail);
		events.add(event);
		if (events.size() > options.maxRetainedEvents)
		{
			events.remove(0);
			droppedEvents++;
		}
		if (options.listener != null)
		{
			try
			{
				options.listener.onEvent(event);
			}
			catch (RuntimeException ignored)
			{
				//A listener must never break scheduling.
			}
		}
	}

	private void noteTime(double now)
	{
		if (!Double.isNaN(now) && now > lastEventTime)
		{
			lastEventTime = now;
		}
	}

	private NodeRecord nodeRecord(int node)
	{
		return nodes[node - 1];
	}

	private void requireNode(int node)
	{
		if (node < 1 || node > nodes.length)
		{
			throw new IllegalArgumentException(
					"Node " + node + " is outside 1.." + nodes.length + ".");
		}
	}

	private static int laneKey(int node, int lane)
	{
		if (lane < 0 || lane >= 4096)
		{
			throw new IllegalArgumentException("Lane index out of range: " + lane);
		}
		return node * 4096 + lane;
	}
}
