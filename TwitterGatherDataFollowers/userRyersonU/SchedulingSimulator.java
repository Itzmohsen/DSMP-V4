package TwitterGatherDataFollowers.userRyersonU;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Discrete-event execution of one work phase in virtual time.
 *
 * It runs the very {@link WorkDispatcher} the live simulator uses; only time
 * is simulated. A unit starts when its lane has it and its descriptor and data
 * have arrived ({@link WorkDispatcher.Assignment#getReadyAt()}), and runs for
 * cost / speed + its fixed part on its node. Operator controls, crashes and
 * repairs, transient faults and the network all act exactly as in a live run,
 * so the simulator predicts what the live system does and, run on the costs a
 * live run measured, can be checked against it (see {@link PhaseReplay}).
 *
 * Two knobs model a scheduler that does not know the truth: per-node actual
 * speeds that differ from the specs the policy plans with, and log-normal
 * noise on unit costs (mean-preserving, seeded per unit).
 */
final class SchedulingSimulator
{
	private SchedulingSimulator()
	{
	}

	/** An operator action or fault at a virtual time or at a progress point. */
	static final class Control
	{
		enum Kind
		{
			PAUSE,
			RESUME,
			UNAVAILABLE,
			AVAILABLE,
			DRAIN,
			REDIRECT,
			SWITCH_POLICY,
			CAPACITY,
			CRASH,
			RECOVER
		}

		private final double time;
		private final double progress;
		private final Kind kind;
		private final int node;
		private final int count;
		private final SchedulerLabEngine.SchedulingPolicy policy;
		private final double value;

		Control(double time, Kind kind, int node, int count,
				SchedulerLabEngine.SchedulingPolicy policy)
		{
			this(time, Double.NaN, kind, node, count, policy, Double.NaN);
		}

		/**
		 * @param time     virtual time of the action (NaN when progress-triggered)
		 * @param progress committed fraction of the phase that triggers the action
		 *                 (NaN when time-triggered)
		 * @param value    capacity scale for CAPACITY; outage length for CRASH
		 *                 (NaN: repaired only by an explicit RECOVER)
		 */
		Control(double time, double progress, Kind kind, int node, int count,
				SchedulerLabEngine.SchedulingPolicy policy, double value)
		{
			this.time = time;
			this.progress = progress;
			this.kind = kind;
			this.node = node;
			this.count = count;
			this.policy = policy;
			this.value = value;
		}

		static Control atProgress(double fraction, Kind kind, int node, int count,
				SchedulerLabEngine.SchedulingPolicy policy, double value)
		{
			return new Control(Double.NaN, fraction, kind, node, count, policy, value);
		}

		double getTime()
		{
			return time;
		}

		double getProgress()
		{
			return progress;
		}

		boolean isProgressTriggered()
		{
			return !Double.isNaN(progress);
		}

		Kind getKind()
		{
			return kind;
		}

		int getNode()
		{
			return node;
		}

		int getCount()
		{
			return count;
		}

		SchedulerLabEngine.SchedulingPolicy getPolicy()
		{
			return policy;
		}

		double getValue()
		{
			return value;
		}

		Control at(double newTime)
		{
			return new Control(newTime, Double.NaN, kind, node, count, policy, value);
		}
	}

	static final class Config
	{
		private final SchedulerLabEngine.SchedulingPolicy policy;
		private final List<NodeSpec> nodes;
		private final List<WorkUnitSpec> units;
		private final WorkDispatcher.Options options;
		private final double msPerTimeUnit;
		private double[] actualSpeed;
		private double costNoise;
		private long seed = 0x51D0L;
		private Map<Integer,double[]> actualCosts;
		private final ArrayList<Control> controls = new ArrayList<Control>();
		private List<FailureSchedule> crashSchedules = Collections.emptyList();
		private double detectionTimeout;
		private double startTime;
		private NetworkModel network;
		private boolean checkInvariants;
		private String phaseKey = "simulation";
		private double horizon = Double.POSITIVE_INFINITY;
		private double[] nodeStartTimes;

		Config(SchedulerLabEngine.SchedulingPolicy policy,
				List<NodeSpec> nodes,
				List<WorkUnitSpec> units,
				WorkDispatcher.Options options,
				double msPerTimeUnit)
		{
			this.policy = policy;
			this.nodes = nodes;
			this.units = units;
			this.options = options == null ? new WorkDispatcher.Options() : options;
			this.msPerTimeUnit = msPerTimeUnit <= 0.0 ? 1.0 : msPerTimeUnit;
			this.options.msPerTimeUnit(this.msPerTimeUnit);
		}

		/** Speeds the nodes really run at; the policy plans with the specs. */
		Config actualSpeeds(double[] speeds)
		{
			actualSpeed = speeds == null ? null : speeds.clone();
			return this;
		}

		/**
		 * Log-normal noise on unit costs: the actual cost is the estimate times
		 * exp(sigma Z - sigma^2 / 2), drawn once per unit, so estimates are
		 * right on average but wrong for each unit.
		 */
		Config costNoise(double sigma, long noiseSeed)
		{
			costNoise = Math.max(0.0, sigma);
			seed = noiseSeed;
			return this;
		}

		/**
		 * The measured truth for trace replay: unit id to {reference cost,
		 * fixed part}. Units not listed run at their spec.
		 */
		Config actualCosts(Map<Integer,double[]> costs)
		{
			actualCosts = costs;
			return this;
		}

		Config control(Control control)
		{
			controls.add(control);
			return this;
		}

		/** Crash-recovery processes, in the same time unit as the simulation. */
		Config crashes(List<FailureSchedule> schedules, double detectionTimeoutUnits)
		{
			crashSchedules = schedules == null ? Collections.<FailureSchedule>emptyList() : schedules;
			detectionTimeout = Math.max(0.0, detectionTimeoutUnits);
			return this;
		}

		Config detectionTimeout(double units)
		{
			detectionTimeout = Math.max(0.0, units);
			return this;
		}

		/** Virtual time at which the phase opens (crash schedules count from 0). */
		Config startTime(double time)
		{
			startTime = Double.isNaN(time) ? 0.0 : time;
			options.openedAt(startTime);
			return this;
		}

		Config network(NetworkModel value)
		{
			network = value;
			return this;
		}

		Config checkInvariants(boolean value)
		{
			checkInvariants = value;
			return this;
		}

		Config phaseKey(String key)
		{
			phaseKey = key == null ? "simulation" : key;
			return this;
		}

		/**
		 * When each node's lanes first ask for work (index node - 1); earlier
		 * than the phase start or missing means the phase start. Live nodes
		 * join a phase at different times (each finishes its own preceding
		 * stage first); replays use their recorded join times.
		 */
		Config nodeStartTimes(double[] times)
		{
			nodeStartTimes = times == null ? null : times.clone();
			return this;
		}

		/** Stops the simulation at this virtual time even if work remains. */
		Config horizon(double time)
		{
			horizon = time;
			return this;
		}

		List<NodeSpec> getNodes()
		{
			return nodes;
		}

		List<WorkUnitSpec> getUnits()
		{
			return units;
		}

		SchedulerLabEngine.SchedulingPolicy getPolicy()
		{
			return policy;
		}
	}

	/** One execution of a unit, as it happened in the simulation. */
	static final class Execution
	{
		private final int unitId;
		private final int node;
		private final int lane;
		private final double assignedAt;
		private final double start;
		private final double end;
		private final WorkDispatcher.Outcome outcome;
		private final WorkDispatcher.Source source;
		private final boolean lostToCrash;
		private final long transferredBytes;

		private Execution(int unitId, int node, int lane, double assignedAt, double start,
				double end, WorkDispatcher.Outcome outcome, WorkDispatcher.Source source,
				boolean lostToCrash, long transferredBytes)
		{
			this.unitId = unitId;
			this.node = node;
			this.lane = lane;
			this.assignedAt = assignedAt;
			this.start = start;
			this.end = end;
			this.outcome = outcome;
			this.source = source;
			this.lostToCrash = lostToCrash;
			this.transferredBytes = transferredBytes;
		}

		int getUnitId()
		{
			return unitId;
		}

		int getNode()
		{
			return node;
		}

		int getLane()
		{
			return lane;
		}

		double getAssignedAt()
		{
			return assignedAt;
		}

		double getStart()
		{
			return start;
		}

		double getEnd()
		{
			return end;
		}

		WorkDispatcher.Outcome getOutcome()
		{
			return outcome;
		}

		WorkDispatcher.Source getSource()
		{
			return source;
		}

		boolean isLostToCrash()
		{
			return lostToCrash;
		}

		boolean isCommitted()
		{
			return outcome == WorkDispatcher.Outcome.COMMITTED;
		}

		long getTransferredBytes()
		{
			return transferredBytes;
		}
	}

	static final class Trace
	{
		private final WorkDispatcher dispatcher;
		private final double startTime;
		private final double endTime;
		private final double[] busyTime;
		private final double[] lastFinish;
		private final double[] downTime;
		private final List<Execution> executions;
		private final Map<Integer,Integer> committedNode;
		private final List<String> violations;
		private final boolean deadlocked;
		private final double lowerBound;
		private final NetworkModel network;
		private final int crashes;

		private Trace(WorkDispatcher dispatcher, double startTime, double endTime,
				double[] busyTime, double[] lastFinish, double[] downTime,
				List<Execution> executions, Map<Integer,Integer> committedNode,
				List<String> violations, boolean deadlocked, double lowerBound,
				NetworkModel network, int crashes)
		{
			this.dispatcher = dispatcher;
			this.startTime = startTime;
			this.endTime = endTime;
			this.busyTime = busyTime;
			this.lastFinish = lastFinish;
			this.downTime = downTime;
			this.executions = Collections.unmodifiableList(executions);
			this.committedNode = Collections.unmodifiableMap(committedNode);
			this.violations = Collections.unmodifiableList(violations);
			this.deadlocked = deadlocked;
			this.lowerBound = lowerBound;
			this.network = network;
			this.crashes = crashes;
		}

		WorkDispatcher getDispatcher()
		{
			return dispatcher;
		}

		/** Time from the phase opening to its last commit. */
		double getMakespan()
		{
			return Math.max(0.0, endTime - startTime);
		}

		double getStartTime()
		{
			return startTime;
		}

		double getEndTime()
		{
			return endTime;
		}

		/** Lane-time spent executing (committed or not) on node (1-based). */
		double getBusyTime(int node)
		{
			return busyTime[node - 1];
		}

		double getLastFinish(int node)
		{
			return lastFinish[node - 1];
		}

		/** Time the node spent crashed during the phase. */
		double getDownTime(int node)
		{
			return downTime[node - 1];
		}

		List<Execution> getExecutions()
		{
			return executions;
		}

		Map<Integer,Integer> getCommittedNode()
		{
			return committedNode;
		}

		List<String> getViolations()
		{
			return violations;
		}

		boolean isDeadlocked()
		{
			return deadlocked;
		}

		boolean isComplete()
		{
			return dispatcher.isComplete();
		}

		double getLowerBound()
		{
			return lowerBound;
		}

		NetworkModel getNetwork()
		{
			return network;
		}

		int getCrashes()
		{
			return crashes;
		}

		/** Lane-time of executions that did not commit (faults, drains, crashes). */
		double getWastedTime()
		{
			double wasted = 0.0;
			for (Execution execution : executions)
			{
				if (!execution.isCommitted())
				{
					wasted += execution.end - execution.start;
				}
			}
			return wasted;
		}

		/** Busy lane-time divided by available lane-time up to the makespan. */
		double getUtilization()
		{
			double busy = 0.0;
			int lanes = 0;
			for (int node = 1; node <= busyTime.length; node++)
			{
				busy += busyTime[node - 1];
				lanes += dispatcher.nodeSpecs().get(node - 1).getLanes();
			}
			double capacity = lanes * getMakespan();
			return capacity <= 0.0 ? 0.0 : busy / capacity;
		}
	}

	private static final int CONTROL = 0;
	private static final int CRASH = 1;
	private static final int DETECT = 2;
	private static final int RECOVER = 3;
	private static final int UNIT_DONE = 4;
	private static final int LANE_READY = 5;

	private static final class Event implements Comparable<Event>
	{
		private final double time;
		private final long sequence;
		private final int type;
		private final int node;
		private final int lane;
		private final WorkDispatcher.Assignment assignment;
		private final double start;
		private final Control control;
		private boolean cancelled;

		private Event(double time, long sequence, int type, int node, int lane,
				WorkDispatcher.Assignment assignment, double start, Control control)
		{
			this.time = time;
			this.sequence = sequence;
			this.type = type;
			this.node = node;
			this.lane = lane;
			this.assignment = assignment;
			this.start = start;
			this.control = control;
		}

		public int compareTo(Event other)
		{
			int byTime = Double.compare(time, other.time);
			if (byTime != 0)
			{
				return byTime;
			}
			//Controls and faults first, then completions, then idle lanes; then FIFO.
			int byType = Integer.compare(type, other.type);
			return byType != 0 ? byType : Long.compare(sequence, other.sequence);
		}
	}

	/** Mutable state of one simulation. */
	private static final class State
	{
		private final Config config;
		private final WorkDispatcher dispatcher;
		private final PriorityQueue<Event> queue = new PriorityQueue<Event>();
		private final HashMap<Long,Event> running = new HashMap<Long,Event>();
		private final boolean[][] waiting;
		private final boolean[] down;
		private final double[] downSince;
		private final double[] busy;
		private final double[] lastFinish;
		private final double[] downTime;
		private final double[] speedScale;
		private final int[] crashEpoch;
		private final ArrayList<Execution> executions = new ArrayList<Execution>();
		private final LinkedHashMap<Integer,Integer> committed = new LinkedHashMap<Integer,Integer>();
		private final ArrayList<String> violations = new ArrayList<String>();
		private final ArrayList<Control> progressControls = new ArrayList<Control>();
		private final HashMap<Integer,FailureSchedule> schedules = new HashMap<Integer,FailureSchedule>();
		private List<NodeSpec> specs;
		private long sequence;
		private int crashes;
		private double lastCommit;

		private State(Config config)
		{
			this.config = config;
			NetworkModel network = config.network != null ? config.network
					: new NetworkModel(config.nodes, false);
			config.options.network(network).openedAt(config.startTime);
			dispatcher = new WorkDispatcher(config.phaseKey, config.policy, config.nodes,
					config.units, config.options);
			int count = config.nodes.size();
			waiting = new boolean[count][];
			down = new boolean[count];
			downSince = new double[count];
			busy = new double[count];
			lastFinish = new double[count];
			downTime = new double[count];
			speedScale = new double[count];
			java.util.Arrays.fill(speedScale, 1.0);
			crashEpoch = new int[count];
			specs = config.nodes;
			lastCommit = config.startTime;
			for (int node = 1; node <= count; node++)
			{
				waiting[node - 1] = new boolean[config.nodes.get(node - 1).getLanes()];
			}
		}

		private void schedule(double time, int type, int node, int lane,
				WorkDispatcher.Assignment assignment, double start, Control control)
		{
			Event event = new Event(time, sequence++, type, node, lane, assignment, start, control);
			queue.add(event);
			if (type == UNIT_DONE)
			{
				running.put(Long.valueOf(assignment.getToken()), event);
			}
		}

		private double actualSpeed(int node)
		{
			double base = config.actualSpeed != null && node - 1 < config.actualSpeed.length
					&& config.actualSpeed[node - 1] > 0.0
					? config.actualSpeed[node - 1] * speedScale[node - 1]
					: specs.get(node - 1).getSpeed();
			return Math.max(1.0e-9, base);
		}

		/**
		 * The lower bound of what actually ran: actual costs, and each node at
		 * the fastest speed it reached (see {@link PlacementPlanner#bound}).
		 */
		private double lowerBound()
		{
			ArrayList<WorkUnitSpec> actual = new ArrayList<WorkUnitSpec>();
			for (WorkUnitSpec unit : config.units)
			{
				double[] cost = actualCost(unit);
				actual.add(unit.withCost(cost[0], cost[1]));
			}
			ArrayList<NodeSpec> fastest = new ArrayList<NodeSpec>();
			for (int node = 1; node <= config.nodes.size(); node++)
			{
				double base = config.actualSpeed != null && node - 1 < config.actualSpeed.length
						&& config.actualSpeed[node - 1] > 0.0
						? config.actualSpeed[node - 1] : config.nodes.get(node - 1).getSpeed();
				double scale = 1.0;
				for (Control control : config.controls)
				{
					if (control.kind == Control.Kind.CAPACITY && control.node == node && !Double.isNaN(control.value))
					{
						scale = Math.max(scale, control.value);
					}
				}
				fastest.add(config.nodes.get(node - 1).withSpeed(base * scale));
			}
			return PlacementPlanner.bound(actual, fastest, relativeStarts(config),
					config.network == null || !config.network.isFree()).value;
		}

		/** Actual reference cost and fixed part of a unit. */
		private double[] actualCost(WorkUnitSpec unit)
		{
			if (config.actualCosts != null)
			{
				double[] measured = config.actualCosts.get(Integer.valueOf(unit.getId()));
				if (measured != null)
				{
					return measured;
				}
			}
			double cost = unit.getCost();
			if (config.costNoise > 0.0)
			{
				FailureSchedule.SplitMix random = new FailureSchedule.SplitMix(
						WorkDispatcher.mix(config.seed ^ (0x632BE59BD9B4E019L * unit.getId())));
				double sigma = config.costNoise;
				cost *= Math.exp(sigma * random.nextGaussian() - sigma * sigma / 2.0);
			}
			return new double[] {cost, unit.getFixedMs()};
		}

		private void begin(WorkDispatcher.Assignment assignment, double now)
		{
			int node = assignment.getNode();
			double start = Math.max(now, assignment.getReadyAt());
			double[] cost = actualCost(assignment.getUnit());
			double end = start + cost[0] / actualSpeed(node) + cost[1];
			schedule(end, UNIT_DONE, node, assignment.getLane(), assignment, start, null);
		}

		private void pickUpPending(double now)
		{
			for (int node = 1; node <= waiting.length; node++)
			{
				if (down[node - 1])
				{
					continue;
				}
				for (int lane = 0; lane < waiting[node - 1].length; lane++)
				{
					if (!waiting[node - 1][lane])
					{
						continue;
					}
					WorkDispatcher.Assignment assignment = dispatcher.pendingFor(node, lane);
					if (assignment != null)
					{
						waiting[node - 1][lane] = false;
						begin(assignment, now);
					}
				}
			}
		}

		private void laneReady(int node, int lane, double now)
		{
			if (down[node - 1])
			{
				return;
			}
			WorkDispatcher.Assignment assignment = dispatcher.request(node, lane, now);
			if (assignment != null)
			{
				waiting[node - 1][lane] = false;
				begin(assignment, now);
			}
			else
			{
				waiting[node - 1][lane] = true;
			}
		}

		private void record(Event event, double end, WorkDispatcher.Outcome outcome, boolean lost)
		{
			WorkDispatcher.Assignment assignment = event.assignment;
			int node = assignment.getNode();
			double span = Math.max(0.0, end - event.start);
			busy[node - 1] += span;
			lastFinish[node - 1] = Math.max(lastFinish[node - 1], end);
			executions.add(new Execution(assignment.getUnitId(), node, assignment.getLane(),
					assignment.getAssignedAt(), event.start, end, outcome,
					assignment.getSource(), lost, assignment.getTransferredBytes()));
		}

		private void crash(int node, double now, double outage)
		{
			if (down[node - 1])
			{
				return;
			}
			crashes++;
			down[node - 1] = true;
			downSince[node - 1] = now;
			//Executions on the node stop now; their work is lost.
			ArrayList<Event> lost = new ArrayList<Event>();
			for (Event event : running.values())
			{
				if (event.node == node && !event.cancelled)
				{
					lost.add(event);
				}
			}
			for (Event event : lost)
			{
				event.cancelled = true;
				running.remove(Long.valueOf(event.assignment.getToken()));
				record(event, Math.max(event.start, now), WorkDispatcher.Outcome.STALE, true);
			}
			dispatcher.crash(node, now);
			for (int lane = 0; lane < waiting[node - 1].length; lane++)
			{
				waiting[node - 1][lane] = false;
				dispatcher.withdraw(node, lane);
			}
			crashEpoch[node - 1]++;
			//The detection event carries the crash's epoch in its lane field, so a
			//stale detection of an earlier outage never detects a later one early.
			schedule(now + config.detectionTimeout, DETECT, node, crashEpoch[node - 1], null, 0.0, null);
			if (!Double.isNaN(outage))
			{
				schedule(now + Math.max(0.0, outage), RECOVER, node, 0, null, 0.0, null);
			}
		}

		private void recover(int node, double now)
		{
			if (!down[node - 1])
			{
				return;
			}
			down[node - 1] = false;
			downTime[node - 1] += now - downSince[node - 1];
			dispatcher.recover(node, now);
			for (int lane = 0; lane < waiting[node - 1].length; lane++)
			{
				schedule(now, LANE_READY, node, lane, null, 0.0, null);
			}
			scheduleNextOutage(node, now);
		}

		private void scheduleNextOutage(int node, double after)
		{
			FailureSchedule schedule = schedules.get(Integer.valueOf(node));
			if (schedule == null)
			{
				return;
			}
			FailureSchedule.Outage outage = schedule.nextAfter(after);
			if (outage != null)
			{
				Control crash = new Control(outage.getCrashAtMs(), Double.NaN,
						Control.Kind.CRASH, node, 0, null, outage.getDowntimeMs());
				schedule(Math.max(after, outage.getCrashAtMs()), CRASH, node, 0, null, 0.0, crash);
			}
		}

		private void apply(Control control, double now)
		{
			int node = control.node;
			switch (control.kind)
			{
				case PAUSE:
					dispatcher.setNodeState(node, WorkDispatcher.NodeState.PAUSED, now);
					break;
				case RESUME:
				case AVAILABLE:
					dispatcher.setNodeState(node, WorkDispatcher.NodeState.ACTIVE, now);
					wake(node, now);
					break;
				case UNAVAILABLE:
					dispatcher.setNodeState(node, WorkDispatcher.NodeState.UNAVAILABLE, now);
					break;
				case DRAIN:
					for (Integer unitId : dispatcher.drain(node, now))
					{
						cancelRunningUnit(unitId.intValue(), now);
					}
					break;
				case REDIRECT:
					dispatcher.redirect(node, control.count, now);
					break;
				case SWITCH_POLICY:
					dispatcher.switchPolicy(control.policy, now);
					break;
				case CAPACITY:
				{
					double scale = Double.isNaN(control.value) ? 1.0 : Math.max(0.01, control.value);
					speedScale[node - 1] = scale;
					ArrayList<NodeSpec> updated = new ArrayList<NodeSpec>(specs);
					NodeSpec base = config.nodes.get(node - 1);
					updated.set(node - 1, base.withSpeed(base.getSpeed() * scale));
					specs = updated;
					dispatcher.updateSpecs(updated, now);
					break;
				}
				case CRASH:
					crash(node, now, control.value);
					break;
				case RECOVER:
					recover(node, now);
					break;
				default:
					break;
			}
		}

		/** A drained execution stops at once in the simulation (its lane is freed). */
		private void cancelRunningUnit(int unitId, double now)
		{
			ArrayList<Event> hit = new ArrayList<Event>();
			for (Event event : running.values())
			{
				if (event.assignment.getUnitId() == unitId && !event.cancelled
						&& event.assignment.isRevoked())
				{
					hit.add(event);
				}
			}
			for (Event event : hit)
			{
				event.cancelled = true;
				running.remove(Long.valueOf(event.assignment.getToken()));
				record(event, Math.max(event.start, now), WorkDispatcher.Outcome.STALE, false);
				dispatcher.complete(event.assignment, now);
				schedule(now, LANE_READY, event.node, event.lane, null, 0.0, null);
			}
		}

		private void wake(int node, double now)
		{
			for (int lane = 0; lane < waiting[node - 1].length; lane++)
			{
				if (waiting[node - 1][lane])
				{
					waiting[node - 1][lane] = false;
					schedule(now, LANE_READY, node, lane, null, 0.0, null);
				}
			}
		}

		private void fireProgressControls(double now)
		{
			if (progressControls.isEmpty())
			{
				return;
			}
			double progress = dispatcher.progress();
			ArrayList<Control> due = new ArrayList<Control>();
			for (Control control : progressControls)
			{
				if (progress + 1e-12 >= control.progress)
				{
					due.add(control);
				}
			}
			progressControls.removeAll(due);
			for (Control control : due)
			{
				schedule(now, CONTROL, control.node, 0, null, 0.0, control.at(now));
			}
		}
	}

	/** Node start times relative to the phase's start (for the lower bound). */
	private static double[] relativeStarts(Config config)
	{
		if (config.nodeStartTimes == null)
		{
			return null;
		}
		double[] relative = new double[config.nodeStartTimes.length];
		for (int index = 0; index < relative.length; index++)
		{
			double start = config.nodeStartTimes[index];
			relative[index] = Double.isNaN(start) ? 0.0 : Math.max(0.0, start - config.startTime);
		}
		return relative;
	}

	static Trace run(Config config)
	{
		State state = new State(config);
		WorkDispatcher dispatcher = state.dispatcher;
		double start = config.startTime;
		for (int node = 1; node <= config.nodes.size(); node++)
		{
			double joins = config.nodeStartTimes != null && node - 1 < config.nodeStartTimes.length
					&& !Double.isNaN(config.nodeStartTimes[node - 1])
					? Math.max(start, config.nodeStartTimes[node - 1]) : start;
			for (int lane = 0; lane < config.nodes.get(node - 1).getLanes(); lane++)
			{
				state.schedule(joins, LANE_READY, node, lane, null, 0.0, null);
			}
		}
		for (Control control : config.controls)
		{
			if (control.isProgressTriggered())
			{
				state.progressControls.add(control);
			}
			else
			{
				state.schedule(Math.max(start, control.time), control.kind == Control.Kind.CRASH
						? CRASH : CONTROL, control.node, 0, null, 0.0, control);
			}
		}
		for (FailureSchedule schedule : config.crashSchedules)
		{
			state.schedules.put(Integer.valueOf(schedule.getNode()), schedule);
			state.scheduleNextOutage(schedule.getNode(), start);
		}
		state.fireProgressControls(start);
		boolean deadlocked = false;
		while (!dispatcher.isComplete() && !dispatcher.hasFailed())
		{
			Event event = state.queue.poll();
			if (event == null)
			{
				deadlocked = true;
				break;
			}
			if (event.cancelled)
			{
				continue;
			}
			double now = event.time;
			if (now > config.horizon)
			{
				break;
			}
			switch (event.type)
			{
				case CONTROL:
					state.apply(event.control, now);
					break;
				case CRASH:
					state.crash(event.node, now, event.control == null ? Double.NaN : event.control.value);
					break;
				case DETECT:
					if (state.down[event.node - 1]
							&& state.crashEpoch[event.node - 1] == event.lane)
					{
						dispatcher.detectCrash(event.node, now);
					}
					break;
				case RECOVER:
					state.recover(event.node, now);
					break;
				case UNIT_DONE:
				{
					state.running.remove(Long.valueOf(event.assignment.getToken()));
					WorkDispatcher.Outcome outcome = dispatcher.complete(event.assignment, now);
					state.record(event, now, outcome, false);
					if (outcome == WorkDispatcher.Outcome.COMMITTED)
					{
						state.committed.put(Integer.valueOf(event.assignment.getUnitId()),
								Integer.valueOf(event.node));
						state.lastCommit = Math.max(state.lastCommit, now);
					}
					state.schedule(now, LANE_READY, event.node, event.lane, null, 0.0, null);
					state.fireProgressControls(now);
					break;
				}
				case LANE_READY:
					state.laneReady(event.node, event.lane, now);
					break;
				default:
					break;
			}
			state.pickUpPending(now);
			if (config.checkInvariants)
			{
				checkInvariants(dispatcher, state.specs, state.violations, now);
			}
		}
		double end = dispatcher.isComplete() ? state.lastCommit : Double.NaN;
		for (int node = 1; node <= state.down.length; node++)
		{
			if (state.down[node - 1] && !Double.isNaN(end))
			{
				state.downTime[node - 1] += Math.max(0.0, end - state.downSince[node - 1]);
			}
		}
		return new Trace(dispatcher, start, Double.isNaN(end) ? start : end,
				state.busy, state.lastFinish, state.downTime, state.executions,
				state.committed, state.violations, deadlocked,
				state.lowerBound(),
				dispatcher.getNetwork(), state.crashes);
	}

	/**
	 * Memory, lane and exactly-once invariants after every event: no node over
	 * its memory budget or lane count, no unit committed twice.
	 */
	private static void checkInvariants(WorkDispatcher dispatcher, List<NodeSpec> specs,
			List<String> violations, double now)
	{
		for (WorkDispatcher.NodeStats stats : dispatcher.nodeStats())
		{
			NodeSpec spec = specs.get(stats.getNode() - 1);
			if (stats.getMemoryInUseMb() > spec.getMemoryMb())
			{
				violations.add("Node " + stats.getNode() + " exceeded its memory budget at t="
						+ now);
			}
			if (stats.getRunning() > spec.getLanes())
			{
				violations.add("Node " + stats.getNode() + " ran more units than lanes at t="
						+ now);
			}
			if (stats.getRunning() + stats.getReserved() > spec.getQueueCapacity()
					&& stats.getState() != WorkDispatcher.NodeState.DOWN)
			{
				StringBuilder recent = new StringBuilder();
				List<WorkDispatcher.Event> history = dispatcher.events();
				for (int index = Math.max(0, history.size() - 12); index < history.size(); index++)
				{
					WorkDispatcher.Event event = history.get(index);
					recent.append("\n    t=").append(event.getTime()).append(" n").append(event.getNode())
							.append(' ').append(event.getType()).append(' ').append(event.getUnitKey())
							.append(": ").append(event.getDetail());
				}
				violations.add("Node " + stats.getNode() + " held more units than its queue capacity at t="
						+ now + " (running " + stats.getRunning() + ", delivered " + stats.getReserved()
						+ ", capacity " + spec.getQueueCapacity() + ", policy "
						+ dispatcher.getPolicy() + ")" + recent);
			}
		}
	}
}
