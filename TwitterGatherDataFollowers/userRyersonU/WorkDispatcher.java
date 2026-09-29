package TwitterGatherDataFollowers.userRyersonU;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Deterministic dispatcher for one phase of schedulable work.
 *
 * The same class drives the live simulator (real node threads), the Dynamic
 * Queue Lab (real threads, emulated work) and the Scheduler Lab (a
 * discrete-event simulation with virtual time), so every policy behaves the
 * same way in all three.
 *
 * <h3>Protocol</h3>
 * An idle lane (one worker slot of a node) calls {@link #request}. The
 * dispatcher matches every waiting lane with a unit according to the policy
 * and returns the calling lane's {@link Assignment}, or null when the lane has
 * to wait. The lane runs the unit and calls {@link #complete} or {@link #fail}.
 * Every assignment carries a token; an assignment whose token was revoked (by
 * a drain) can never commit, so each unit commits exactly once.
 *
 * <h3>Execution modes</h3>
 * <ul>
 * <li>STATIC policies follow a plan made up front by {@link PlacementPlanner}:
 *     each node works through its own local queue and never takes another
 *     node's units. Failures are retried on the same node.</li>
 * <li>PULL policies keep one central backlog in the policy's order; whenever
 *     lanes are waiting, the next runnable unit goes to the waiting lane the
 *     policy prefers. No unit is ever withheld from an idle eligible lane that
 *     can run it, except when its memory does not fit.</li>
 * <li>STEAL starts from the capacity-weighted plan; a lane whose node queue is
 *     empty steals the last unit of the node with the most remaining work.</li>
 * </ul>
 *
 * <h3>Costs of communication</h3>
 * Each node's link has a one-way delay d. Units handed out by the coordinator
 * (backlog pulls and steals) cost a round trip, 2d; units already queued on the
 * node cost nothing, except the first one, which carries the plan (d). A unit
 * whose input lives on another node additionally pays d(home) + d(node) to move
 * that input. {@link Assignment#getTransferDelayMs()} reports the total so the
 * caller can incur it (sleep in real time, advance virtual time).
 *
 * All methods are synchronized; callers may use it from many threads.
 */
final class WorkDispatcher
{
	enum UnitState
	{
		BACKLOG,
		RESERVED,
		RUNNING,
		COMMITTED,
		FAILED
	}

	enum NodeState
	{
		ACTIVE,
		PAUSED,
		UNAVAILABLE
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
		/** The execution was lost to an injected node failure; discard it. */
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

	static final class Options
	{
		private int maxFailuresPerUnit = 10;
		private int maxRealFailuresPerUnit = 2;
		private boolean faultInjection;
		private long failureSeed = 0x5EEDL;
		private long globalMemoryBudgetMb = Long.MAX_VALUE;
		private double msPerTimeUnit = 1.0;
		private int maxRetainedEvents = 5000;

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

		/** Milliseconds represented by one unit of cost (for latency in plans). */
		Options msPerTimeUnit(double value)
		{
			msPerTimeUnit = Double.isNaN(value) || value < 0.0 ? 0.0 : value;
			return this;
		}

		Options maxRetainedEvents(int value)
		{
			maxRetainedEvents = Math.max(10, value);
			return this;
		}

		boolean isFaultInjection()
		{
			return faultInjection;
		}
	}

	static final class Assignment
	{
		private final WorkUnitSpec unit;
		private final int node;
		private final int lane;
		private final long token;
		private final int execution;
		private final Source source;
		private final double transferDelayMs;

		private Assignment(
				WorkUnitSpec unit,
				int node,
				int lane,
				long token,
				int execution,
				Source source,
				double transferDelayMs)
		{
			this.unit = unit;
			this.node = node;
			this.lane = lane;
			this.token = token;
			this.execution = execution;
			this.source = source;
			this.transferDelayMs = transferDelayMs;
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

		/** Latency the executing lane must incur before starting the unit. */
		double getTransferDelayMs()
		{
			return transferDelayMs;
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

		/** assigned, stolen, completed, failed, injected-failure, retried,
		 *  stale, revoked, redirected, released, paused, resumed, unavailable. */
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
		private final int assigned;
		private final int completed;
		private final int failed;
		private final int stolen;
		private final int stale;
		private final long memoryInUseMb;
		private final int queuePeak;
		private final List<Integer> runningUnits;

		private NodeStats(NodeRecord record)
		{
			queuePeak = record.queuePeak;
			node = record.spec.getIndex();
			state = record.state;
			running = record.inflight;
			reserved = record.localQueue.size();
			assigned = record.assigned;
			completed = record.completed;
			failed = record.failed;
			stolen = record.stolen;
			stale = record.stale;
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

		int getReserved()
		{
			return reserved;
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

		long getMemoryInUseMb()
		{
			return memoryInUseMb;
		}

		/** Most units the node held at once (running plus queued). */
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
		private final BitSet excluded = new BitSet();
		private boolean retryFirst;

		private UnitRecord(WorkUnitSpec spec)
		{
			this.spec = spec;
		}
	}

	private static final class NodeRecord
	{
		private NodeSpec spec;
		private NodeState state = NodeState.ACTIVE;
		private final ArrayDeque<Integer> localQueue = new ArrayDeque<Integer>();
		private int inflight;
		private long memoryInUseMb;
		private int assigned;
		private int completed;
		private int failed;
		private int stolen;
		private int stale;
		private boolean planDelivered;
		private int queuePeak;
		private final TreeSet<Integer> runningUnits = new TreeSet<Integer>();

		private NodeRecord(NodeSpec spec)
		{
			this.spec = spec;
		}
	}

	private static final class Inflight
	{
		private final int unitId;
		private final int node;
		private final int memoryMb;

		private Inflight(int unitId, int node, int memoryMb)
		{
			this.unitId = unitId;
			this.node = node;
			this.memoryMb = memoryMb;
		}
	}

	private final String phaseKey;
	private final Options options;
	private SchedulerLabEngine.SchedulingPolicy policy;
	private ExecutionMode mode;
	private final LinkedHashMap<Integer,UnitRecord> units =
			new LinkedHashMap<Integer,UnitRecord>();
	private final NodeRecord[] nodes;
	private TreeSet<UnitRecord> backlog;
	/** Waiting lanes, encoded node * 4096 + lane, kept in (node, lane) order. */
	private final TreeSet<Integer> waitingLanes = new TreeSet<Integer>();
	private final Map<Integer,Assignment> pending = new TreeMap<Integer,Assignment>();
	private final Map<Long,Inflight> inflight = new LinkedHashMap<Long,Inflight>();
	private final ArrayList<Event> events = new ArrayList<Event>();
	private long nextToken = 1L;
	private long globalMemoryInUseMb;
	private int committedCount;
	private boolean failed;
	private String failureReason = "";
	private double lastEventTime;
	private int droppedEvents;

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
			}
		}
		backlog = new TreeSet<UnitRecord>(backlogOrder(this.policy));
		distribute(new ArrayList<UnitRecord>(units.values()), 0.0);
	}

	// ------------------------------------------------------------ public API

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
		waitingLanes.add(Integer.valueOf(laneKey));
		dispatch(now);
		return pending.remove(Integer.valueOf(laneKey));
	}

	/** Removes a lane from the waiting set (it stopped asking for work). */
	synchronized void withdraw(int node, int lane)
	{
		int laneKey = laneKey(node, lane);
		waitingLanes.remove(Integer.valueOf(laneKey));
		Assignment orphan = pending.remove(Integer.valueOf(laneKey));
		if (orphan != null)
		{
			//The lane will never run it: treat as a revoked execution.
			releaseInflight(orphan.getToken());
			UnitRecord record = units.get(Integer.valueOf(orphan.getUnitId()));
			if (record != null && record.token == orphan.getToken()
					&& record.state == UnitState.RUNNING)
			{
				record.token = 0L;
				record.state = UnitState.BACKLOG;
				requeueFront(record, orphan.getNode());
			}
		}
	}

	synchronized Outcome complete(Assignment assignment, double now)
	{
		noteTime(now);
		Inflight run = releaseInflight(assignment.getToken());
		UnitRecord record = units.get(Integer.valueOf(assignment.getUnitId()));
		NodeRecord node = nodeRecord(assignment.getNode());
		if (record == null || run == null || record.token != assignment.getToken()
				|| record.state != UnitState.RUNNING)
		{
			node.stale++;
			addEvent(now, assignment.getNode(), assignment.getUnit(), "stale",
					"Result of revoked execution " + assignment.getExecution()
					+ " discarded.");
			dispatch(now);
			return Outcome.STALE;
		}
		if (options.faultInjection && injectedFailure(record, assignment))
		{
			addEvent(now, assignment.getNode(), record.spec, "injected-failure",
					String.format(Locale.US,
							"Execution %d lost to an injected failure (p=%.3f).",
							assignment.getExecution(),
							node.spec.getFailureProbability()));
			Outcome outcome = retry(record, assignment.getNode(), now,
					"injected node failure");
			dispatch(now);
			return outcome == Outcome.FINAL_FAILURE
					? Outcome.FINAL_FAILURE : Outcome.INJECTED_FAILURE;
		}
		record.state = UnitState.COMMITTED;
		record.token = 0L;
		record.node = assignment.getNode();
		node.completed++;
		committedCount++;
		addEvent(now, assignment.getNode(), record.spec, "completed",
				"Committed on node " + assignment.getNode() + ".");
		dispatch(now);
		return Outcome.COMMITTED;
	}

	synchronized Outcome fail(Assignment assignment, double now, String reason)
	{
		noteTime(now);
		Inflight run = releaseInflight(assignment.getToken());
		UnitRecord record = units.get(Integer.valueOf(assignment.getUnitId()));
		NodeRecord node = nodeRecord(assignment.getNode());
		if (record == null || run == null || record.token != assignment.getToken()
				|| record.state != UnitState.RUNNING)
		{
			node.stale++;
			dispatch(now);
			return Outcome.STALE;
		}
		addEvent(now, assignment.getNode(), record.spec, "failed",
				reason == null ? "Execution failed." : reason);
		record.realFailures++;
		if (record.realFailures >= options.maxRealFailuresPerUnit)
		{
			NodeRecord failedNode = nodeRecord(assignment.getNode());
			failedNode.failed++;
			record.failures++;
			record.token = 0L;
			record.state = UnitState.FAILED;
			failed = true;
			failureReason = "Work unit " + record.spec.getKey() + " failed "
					+ record.realFailures + " time(s) with a real error; last on node "
					+ assignment.getNode() + ": " + (reason == null ? "unknown" : reason);
			addEvent(now, assignment.getNode(), record.spec, "failed-final", failureReason);
			dispatch(now);
			return Outcome.FINAL_FAILURE;
		}
		Outcome outcome = retry(record, assignment.getNode(), now, reason);
		dispatch(now);
		return outcome;
	}

	synchronized void setNodeState(int node, NodeState state, double now)
	{
		requireNode(node);
		noteTime(now);
		NodeRecord record = nodeRecord(node);
		NodeState next = state == null ? NodeState.ACTIVE : state;
		if (record.state == next)
		{
			return;
		}
		record.state = next;
		addEvent(now, node, null,
				next == NodeState.ACTIVE ? "resumed"
						: (next == NodeState.PAUSED ? "paused" : "unavailable"),
				"Node " + node + " is now " + next.name().toLowerCase(Locale.US) + ".");
		if (next == NodeState.UNAVAILABLE
				|| (next == NodeState.PAUSED && mode == ExecutionMode.PULL))
		{
			releaseLocalQueue(record, now);
		}
		dispatch(now);
	}

	/**
	 * Makes the node unavailable and revokes its running executions, so their
	 * units start over elsewhere. The revoked executions keep their lanes busy
	 * until they report, and their results are then discarded.
	 *
	 * @return ids of the revoked units
	 */
	synchronized List<Integer> drain(int node, double now)
	{
		requireNode(node);
		noteTime(now);
		NodeRecord record = nodeRecord(node);
		ArrayList<Integer> revoked = new ArrayList<Integer>();
		if (record.state != NodeState.UNAVAILABLE)
		{
			record.state = NodeState.UNAVAILABLE;
			addEvent(now, node, null, "unavailable",
					"Node " + node + " is draining.");
		}
		releaseLocalQueue(record, now);
		ArrayList<UnitRecord> toRequeue = new ArrayList<UnitRecord>();
		for (UnitRecord unit : units.values())
		{
			if (unit.state == UnitState.RUNNING && unit.node == node)
			{
				unit.state = UnitState.BACKLOG;
				unit.token = 0L;
				revoked.add(Integer.valueOf(unit.spec.getId()));
				addEvent(now, node, unit.spec, "revoked",
						"Execution revoked by drain; the unit starts over elsewhere.");
				toRequeue.add(unit);
			}
		}
		for (UnitRecord unit : toRequeue)
		{
			unit.retryFirst = true;
		}
		distribute(toRequeue, now);
		dispatch(now);
		return revoked;
	}

	/**
	 * Moves up to {@code count} units that have not started into the node's
	 * own queue. Other nodes keep working on everything else; nobody waits.
	 */
	synchronized int redirect(int node, int count, double now)
	{
		requireNode(node);
		noteTime(now);
		NodeRecord target = nodeRecord(node);
		if (target.state != NodeState.ACTIVE || count <= 0)
		{
			return 0;
		}
		int moved = 0;
		Iterator<UnitRecord> fromBacklog = backlog.iterator();
		while (moved < count && fromBacklog.hasNext())
		{
			UnitRecord unit = fromBacklog.next();
			if (!unit.spec.isAllowedOn(node))
			{
				continue;
			}
			fromBacklog.remove();
			reserve(unit, target);
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
				if (candidate == target || candidate.localQueue.isEmpty())
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
			reserve(unit, target);
			addEvent(now, node, unit.spec, "redirected",
					"Operator moved the unit from node " + donor.spec.getIndex()
					+ " to node " + node + ".");
			moved++;
		}
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
			if (unit.state == UnitState.BACKLOG || unit.state == UnitState.RESERVED)
			{
				notStarted.add(unit);
			}
		}
		for (NodeRecord node : nodes)
		{
			node.localQueue.clear();
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

	synchronized int getUnitCount()
	{
		return units.size();
	}

	synchronized int getCommittedCount()
	{
		return committedCount;
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
				&& record.token == assignment.getToken();
	}

	synchronized List<NodeStats> nodeStats()
	{
		ArrayList<NodeStats> stats = new ArrayList<NodeStats>();
		for (NodeRecord node : nodes)
		{
			stats.add(new NodeStats(node));
		}
		return stats;
	}

	synchronized NodeState nodeState(int node)
	{
		requireNode(node);
		return nodeRecord(node).state;
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

	/** Ids of units not started yet, in the order they would start (preview). */
	synchronized List<Integer> waitingUnits(int limit)
	{
		ArrayList<Integer> ids = new ArrayList<Integer>();
		for (UnitRecord unit : backlog)
		{
			if (ids.size() >= limit)
			{
				return ids;
			}
			ids.add(Integer.valueOf(unit.spec.getId()));
		}
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

	/** True when lanes wait but nothing can ever be assigned without operator action. */
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
			if (node.state == NodeState.ACTIVE)
			{
				return false;
			}
		}
		return true;
	}

	// ------------------------------------------------------------ internals

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
				unit.state = UnitState.BACKLOG;
				unit.node = 0;
				backlog.add(unit);
			}
			return;
		}
		//Plan onto active nodes; fall back to paused ones (they will resume) and
		//never onto unavailable ones.
		List<NodeSpec> eligible = new ArrayList<NodeSpec>();
		for (NodeRecord node : nodes)
		{
			if (node.state == NodeState.ACTIVE)
			{
				eligible.add(node.spec);
			}
		}
		if (eligible.isEmpty())
		{
			for (NodeRecord node : nodes)
			{
				if (node.state == NodeState.PAUSED)
				{
					eligible.add(node.spec);
				}
			}
		}
		if (eligible.isEmpty())
		{
			//Nobody can take them now; they wait in the backlog for a node.
			for (UnitRecord unit : toPlace)
			{
				unit.state = UnitState.BACKLOG;
				unit.node = 0;
				backlog.add(unit);
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
			compact.add(reindexed(spec, index + 1));
			baseLoad[index] = queuedWork(nodeRecord(spec.getIndex()))
					* nodeRecord(spec.getIndex()).spec.getThroughput();
		}
		ArrayList<WorkUnitSpec> specs = new ArrayList<WorkUnitSpec>();
		LinkedHashMap<Integer,UnitRecord> byId = new LinkedHashMap<Integer,UnitRecord>();
		for (UnitRecord unit : toPlace)
		{
			if (!allowedOnAny(unit.spec, original))
			{
				//Data-local work whose nodes are all unavailable waits for them.
				unit.state = UnitState.BACKLOG;
				unit.node = 0;
				backlog.add(unit);
				continue;
			}
			WorkUnitSpec compactSpec = compactSpec(unit.spec, original);
			specs.add(compactSpec);
			byId.put(Integer.valueOf(unit.spec.getId()), unit);
		}
		if (specs.isEmpty())
		{
			return;
		}
		PlacementPlanner.Plan plan = PlacementPlanner.plan(
				planPolicy, specs, compact, baseLoad, true, false,
				options.msPerTimeUnit);
		for (int index = 0; index < compact.size(); index++)
		{
			NodeRecord node = nodeRecord(original[index]);
			for (WorkUnitSpec spec : plan.unitsFor(index + 1))
			{
				UnitRecord unit = byId.get(Integer.valueOf(spec.getId()));
				if (unit.retryFirst)
				{
					unit.retryFirst = false;
					unit.state = UnitState.RESERVED;
					unit.node = node.spec.getIndex();
					node.localQueue.addFirst(Integer.valueOf(unit.spec.getId()));
				}
				else
				{
					reserve(unit, node);
				}
			}
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
		return new WorkUnitSpec(
				spec.getId(),
				spec.getKey(),
				spec.getCost(),
				spec.getMemoryMb(),
				spec.getPriority(),
				spec.getDeadline(),
				home,
				allowedArray);
	}

	private static NodeSpec reindexed(NodeSpec spec, int index)
	{
		return new NodeSpec(
				index,
				spec.getRoleLabel(),
				spec.getCpuMultiplier(),
				spec.getCapacityScale(),
				spec.getSlowdown(),
				spec.getSpeed(),
				spec.getLanes(),
				spec.getMemoryMb(),
				spec.getQueueCapacity(),
				spec.getDelayMs(),
				spec.getFailureProbability());
	}

	private void reserve(UnitRecord unit, NodeRecord node)
	{
		unit.state = UnitState.RESERVED;
		unit.node = node.spec.getIndex();
		node.localQueue.addLast(Integer.valueOf(unit.spec.getId()));
		notePeak(node);
	}

	private static void notePeak(NodeRecord node)
	{
		node.queuePeak = Math.max(node.queuePeak, node.inflight + node.localQueue.size());
	}

	private void releaseLocalQueue(NodeRecord node, double now)
	{
		if (node.localQueue.isEmpty())
		{
			return;
		}
		ArrayList<UnitRecord> released = new ArrayList<UnitRecord>();
		for (Integer id : node.localQueue)
		{
			UnitRecord unit = units.get(id);
			unit.state = UnitState.BACKLOG;
			unit.node = 0;
			released.add(unit);
		}
		node.localQueue.clear();
		addEvent(now, node.spec.getIndex(), null, "released",
				released.size() + " queued unit(s) released from node "
				+ node.spec.getIndex() + ".");
		if (node.state == NodeState.PAUSED && mode != ExecutionMode.PULL)
		{
			//Static and stealing modes keep a paused node's plan; only an
			//unavailable node gives its units away.
			for (UnitRecord unit : released)
			{
				reserve(unit, node);
			}
			return;
		}
		distribute(released, now);
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
			return Outcome.FINAL_FAILURE;
		}
		record.state = UnitState.BACKLOG;
		requeueFront(record, failedNode);
		addEvent(now, failedNode, record.spec, "retried",
				"Retry " + record.failures + " queued"
				+ (policy == SchedulerLabEngine.SchedulingPolicy.FAILURE_AWARE_RESCHEDULING
						? " away from node " + failedNode + "." : "."));
		return Outcome.RETRY;
	}

	/** Puts a unit that must start over at the front of the right queue. */
	private void requeueFront(UnitRecord record, int previousNode)
	{
		if (mode == ExecutionMode.PULL)
		{
			record.retryFirst = true;
			record.node = 0;
			backlog.add(record);
			return;
		}
		NodeRecord node = previousNode >= 1 && previousNode <= nodes.length
				? nodeRecord(previousNode) : null;
		if (node != null && node.state != NodeState.UNAVAILABLE
				&& record.spec.isAllowedOn(previousNode))
		{
			record.state = UnitState.RESERVED;
			record.node = previousNode;
			node.localQueue.addFirst(Integer.valueOf(record.spec.getId()));
			return;
		}
		record.retryFirst = true;
		ArrayList<UnitRecord> one = new ArrayList<UnitRecord>();
		one.add(record);
		distribute(one, lastEventTime);
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
				if (record.state != NodeState.ACTIVE)
				{
					continue;
				}
				if (startFromLocalQueue(record, lane, now))
				{
					progress = true;
					continue;
				}
				if (mode == ExecutionMode.STEAL && startStolen(record, lane, now))
				{
					progress = true;
					continue;
				}
			}
			if (mode == ExecutionMode.PULL || !backlog.isEmpty())
			{
				if (assignFromBacklog(now))
				{
					progress = true;
				}
			}
		}
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
		start(unit, node, lane, Source.LOCAL, now);
		return true;
	}

	private boolean startStolen(NodeRecord thief, int lane, double now)
	{
		if (!thief.localQueue.isEmpty())
		{
			return false;
		}
		NodeRecord victim = null;
		double victimWork = 0.0;
		for (NodeRecord candidate : nodes)
		{
			if (candidate == thief || candidate.localQueue.isEmpty())
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
		Iterator<Integer> tail = victim.localQueue.descendingIterator();
		while (tail.hasNext())
		{
			UnitRecord unit = units.get(tail.next());
			if (unit.spec.isAllowedOn(thief.spec.getIndex())
					&& memoryFits(thief, unit.spec))
			{
				tail.remove();
				thief.stolen++;
				start(unit, thief, lane, Source.STOLEN, now);
				addEvent(now, thief.spec.getIndex(), unit.spec, "stolen",
						"Stolen from node " + victim.spec.getIndex() + ".");
				return true;
			}
		}
		return false;
	}

	/** Pull matching: walk the backlog in policy order, give each unit a lane. */
	private boolean assignFromBacklog(double now)
	{
		boolean assigned = false;
		Iterator<UnitRecord> iterator = new ArrayList<UnitRecord>(backlog).iterator();
		while (iterator.hasNext() && !waitingLanes.isEmpty())
		{
			UnitRecord unit = iterator.next();
			if (unit.state != UnitState.BACKLOG || !backlog.contains(unit))
			{
				//Prefetched into a node queue earlier in this pass.
				continue;
			}
			int bestLane = -1;
			NodeRecord bestNode = null;
			for (Integer laneKey : waitingLanes)
			{
				int node = laneKey.intValue() / 4096;
				NodeRecord record = nodeRecord(node);
				if (record.state != NodeState.ACTIVE
						|| !unit.spec.isAllowedOn(node)
						|| !memoryFits(record, unit.spec)
						|| isExcluded(unit, node))
				{
					continue;
				}
				if (mode != ExecutionMode.PULL && !record.localQueue.isEmpty())
				{
					//Static and stealing nodes serve their own queue first.
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
			start(unit, bestNode, bestLane % 4096, Source.HUB, now);
			assigned = true;
			prefetch(bestNode);
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
				double firstRate = first.getSpeed() * (1.0 - first.getFailureProbability());
				double secondRate = second.getSpeed() * (1.0 - second.getFailureProbability());
				if (firstRate != secondRate)
				{
					return firstRate > secondRate;
				}
				break;
			}
			default:
				break;
		}
		double firstFinish = expectedFinishCost(unit.spec, first);
		double secondFinish = expectedFinishCost(unit.spec, second);
		if (firstFinish != secondFinish)
		{
			return firstFinish < secondFinish;
		}
		return first.getIndex() < second.getIndex();
	}

	/** Execution time plus data movement, in cost units, for tie-breaking. */
	private double expectedFinishCost(WorkUnitSpec unit, NodeSpec spec)
	{
		double transfer = 0.0;
		int home = unit.getHomeNode();
		if (options.msPerTimeUnit > 0.0 && home > 0 && home != spec.getIndex()
				&& home <= nodes.length)
		{
			transfer = (nodes[home - 1].spec.getDelayMs() + spec.getDelayMs())
					/ options.msPerTimeUnit;
		}
		return unit.getCost() / spec.getSpeed() + transfer;
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
			if (record.state == NodeState.ACTIVE && unit.spec.isAllowedOn(index)
					&& !unit.excluded.get(index))
			{
				return true;
			}
		}
		return false;
	}

	/** Pull-mode prefetch up to the node's queue capacity. */
	private void prefetch(NodeRecord node)
	{
		if (mode != ExecutionMode.PULL || node.state != NodeState.ACTIVE)
		{
			return;
		}
		Iterator<UnitRecord> iterator = backlog.iterator();
		while (outstanding(node) < node.spec.getQueueCapacity() && iterator.hasNext())
		{
			UnitRecord unit = iterator.next();
			int index = node.spec.getIndex();
			if (!unit.spec.isAllowedOn(index) || isExcluded(unit, index))
			{
				continue;
			}
			iterator.remove();
			unit.retryFirst = false;
			reserve(unit, node);
		}
	}

	private void start(UnitRecord unit, NodeRecord node, int lane, Source source, double now)
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
		inflight.put(Long.valueOf(unit.token),
				new Inflight(unit.spec.getId(), nodeIndex, unit.spec.getMemoryMb()));
		double transfer;
		if (source == Source.LOCAL)
		{
			transfer = node.planDelivered ? 0.0 : node.spec.getDelayMs();
			node.planDelivered = true;
		}
		else
		{
			transfer = 2.0 * node.spec.getDelayMs();
		}
		int home = unit.spec.getHomeNode();
		if (home > 0 && home != nodeIndex && home <= nodes.length)
		{
			transfer += nodes[home - 1].spec.getDelayMs() + node.spec.getDelayMs();
		}
		Assignment assignment = new Assignment(
				unit.spec,
				nodeIndex,
				lane,
				unit.token,
				unit.executions,
				source,
				transfer);
		int laneKey = laneKey(nodeIndex, lane);
		waitingLanes.remove(Integer.valueOf(laneKey));
		pending.put(Integer.valueOf(laneKey), assignment);
		addEvent(now, nodeIndex, unit.spec, "assigned",
				(source == Source.LOCAL ? "Started from node " + nodeIndex + "'s queue"
						: source == Source.STOLEN ? "Started after a steal"
						: "Pulled from the coordinator")
				+ ", execution " + unit.executions + ".");
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

	private double queuedWork(NodeRecord node)
	{
		double work = 0.0;
		for (Integer id : node.localQueue)
		{
			work += units.get(id).spec.getCost();
		}
		return work / Math.max(1.0e-9, node.spec.getThroughput());
	}

	private UnitRecord takeTailAllowed(NodeRecord donor, int targetNode)
	{
		Iterator<Integer> tail = donor.localQueue.descendingIterator();
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
	 * Deterministic injected failure: a SplitMix64 hash of (seed, phase, unit,
	 * execution, node) mapped to [0, 1) and compared with the node's
	 * failure probability. The same execution always fails or succeeds.
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
		events.add(new Event(
				now,
				node,
				unit == null ? 0 : unit.getId(),
				unit == null ? "" : unit.getKey(),
				type,
				detail));
		if (events.size() > options.maxRetainedEvents)
		{
			events.remove(0);
			droppedEvents++;
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
