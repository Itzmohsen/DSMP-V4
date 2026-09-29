package TwitterGatherDataFollowers.userRyersonU;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Discrete-event execution of a {@link WorkDispatcher} in virtual time.
 *
 * This is the Scheduler Lab's engine and the kernel's test harness. It runs the
 * very dispatcher the live simulator uses; only time is simulated. A unit of
 * cost c starts after its assignment's transfer latency and runs for
 * c / actualSpeed on its node. {@code actualSpeed} defaults to the node spec's
 * speed and can be set differently to model a planner whose capacity estimates
 * are wrong (the case dynamic policies exist for).
 */
final class SchedulingSimulator
{
	private SchedulingSimulator()
	{
	}

	/** An operator action applied at a given virtual time. */
	static final class Control
	{
		enum Kind
		{
			PAUSE,
			RESUME,
			UNAVAILABLE,
			DRAIN,
			REDIRECT,
			SWITCH_POLICY
		}

		private final double time;
		private final Kind kind;
		private final int node;
		private final int count;
		private final SchedulerLabEngine.SchedulingPolicy policy;

		Control(double time, Kind kind, int node, int count,
				SchedulerLabEngine.SchedulingPolicy policy)
		{
			this.time = time;
			this.kind = kind;
			this.node = node;
			this.count = count;
			this.policy = policy;
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
		private final ArrayList<Control> controls = new ArrayList<Control>();
		private boolean checkInvariants;
		private String phaseKey = "simulation";

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

		Config actualSpeeds(double[] speeds)
		{
			actualSpeed = speeds == null ? null : speeds.clone();
			return this;
		}

		Config control(Control control)
		{
			controls.add(control);
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
	}

	static final class Execution
	{
		private final int unitId;
		private final int node;
		private final int lane;
		private final double start;
		private final double end;
		private final WorkDispatcher.Outcome outcome;
		private final WorkDispatcher.Source source;

		private Execution(int unitId, int node, int lane, double start, double end,
				WorkDispatcher.Outcome outcome, WorkDispatcher.Source source)
		{
			this.unitId = unitId;
			this.node = node;
			this.lane = lane;
			this.start = start;
			this.end = end;
			this.outcome = outcome;
			this.source = source;
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
	}

	static final class Trace
	{
		private final WorkDispatcher dispatcher;
		private final double makespan;
		private final double[] busyTime;
		private final double[] lastFinish;
		private final List<Execution> executions;
		private final Map<Integer,Integer> committedNode;
		private final List<String> violations;
		private final boolean deadlocked;

		private Trace(WorkDispatcher dispatcher, double makespan, double[] busyTime,
				double[] lastFinish, List<Execution> executions,
				Map<Integer,Integer> committedNode, List<String> violations,
				boolean deadlocked)
		{
			this.dispatcher = dispatcher;
			this.makespan = makespan;
			this.busyTime = busyTime;
			this.lastFinish = lastFinish;
			this.executions = Collections.unmodifiableList(executions);
			this.committedNode = Collections.unmodifiableMap(committedNode);
			this.violations = Collections.unmodifiableList(violations);
			this.deadlocked = deadlocked;
		}

		WorkDispatcher getDispatcher()
		{
			return dispatcher;
		}

		double getMakespan()
		{
			return makespan;
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
	}

	private static final int LANE_READY = 0;
	private static final int UNIT_DONE = 1;
	private static final int CONTROL = 2;

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
			//Controls first, then completions, then idle lanes; then FIFO.
			int byType = Integer.compare(order(type), order(other.type));
			return byType != 0 ? byType : Long.compare(sequence, other.sequence);
		}

		private static int order(int type)
		{
			return type == CONTROL ? 0 : (type == UNIT_DONE ? 1 : 2);
		}
	}

	static Trace run(Config config)
	{
		WorkDispatcher dispatcher = new WorkDispatcher(
				config.phaseKey, config.policy, config.nodes, config.units, config.options);
		int nodeCount = config.nodes.size();
		double[] busy = new double[nodeCount];
		double[] lastFinish = new double[nodeCount];
		ArrayList<Execution> executions = new ArrayList<Execution>();
		LinkedHashMap<Integer,Integer> committed = new LinkedHashMap<Integer,Integer>();
		ArrayList<String> violations = new ArrayList<String>();
		PriorityQueue<Event> queue = new PriorityQueue<Event>();
		long[] sequence = new long[1];
		boolean[][] waiting = new boolean[nodeCount][];
		for (int node = 1; node <= nodeCount; node++)
		{
			int lanes = config.nodes.get(node - 1).getLanes();
			waiting[node - 1] = new boolean[lanes];
			for (int lane = 0; lane < lanes; lane++)
			{
				queue.add(new Event(0.0, sequence[0]++, LANE_READY, node, lane, null, 0.0, null));
			}
		}
		for (Control control : config.controls)
		{
			queue.add(new Event(control.time, sequence[0]++, CONTROL, control.node, 0,
					null, 0.0, control));
		}
		double makespan = 0.0;
		int guard = 0;
		int maxEvents = 50000 + 400 * Math.max(1, config.units.size())
				* Math.max(1, config.options == null ? 10 : 10);
		while (!queue.isEmpty() && !dispatcher.isComplete() && !dispatcher.hasFailed())
		{
			if (++guard > maxEvents)
			{
				violations.add("Simulation exceeded its event budget.");
				break;
			}
			Event event = queue.poll();
			if (event.type == CONTROL)
			{
				apply(dispatcher, event.control);
				pickUpPending(dispatcher, config, queue, sequence, waiting, event.time);
				continue;
			}
			if (event.type == UNIT_DONE)
			{
				WorkDispatcher.Assignment assignment = event.assignment;
				WorkDispatcher.Outcome outcome = dispatcher.complete(assignment, event.time);
				int node = assignment.getNode();
				busy[node - 1] += event.time - event.start;
				lastFinish[node - 1] = Math.max(lastFinish[node - 1], event.time);
				executions.add(new Execution(assignment.getUnitId(), node,
						assignment.getLane(), event.start, event.time, outcome,
						assignment.getSource()));
				if (outcome == WorkDispatcher.Outcome.COMMITTED)
				{
					Integer previous = committed.put(
							Integer.valueOf(assignment.getUnitId()), Integer.valueOf(node));
					if (previous != null)
					{
						violations.add("Unit " + assignment.getUnitId() + " committed twice.");
					}
					makespan = Math.max(makespan, event.time);
				}
				queue.add(new Event(event.time, sequence[0]++, LANE_READY, node,
						assignment.getLane(), null, 0.0, null));
				pickUpPending(dispatcher, config, queue, sequence, waiting, event.time);
				if (config.checkInvariants)
				{
					checkInvariants(dispatcher, config, waiting, violations, event.time);
				}
				continue;
			}
			//LANE_READY
			WorkDispatcher.Assignment assignment =
					dispatcher.request(event.node, event.lane, event.time);
			if (assignment == null)
			{
				waiting[event.node - 1][event.lane] = true;
			}
			else
			{
				waiting[event.node - 1][event.lane] = false;
				schedule(config, queue, sequence, assignment, event.time);
			}
			pickUpPending(dispatcher, config, queue, sequence, waiting, event.time);
		}
		boolean deadlocked = !dispatcher.isComplete() && !dispatcher.hasFailed();
		if (deadlocked)
		{
			violations.add("Simulation stopped with "
					+ (dispatcher.getUnitCount() - dispatcher.getCommittedCount())
					+ " unit(s) never committed.");
		}
		return new Trace(dispatcher, makespan, busy, lastFinish, executions,
				committed, violations, deadlocked);
	}

	private static void apply(WorkDispatcher dispatcher, Control control)
	{
		switch (control.kind)
		{
			case PAUSE:
				dispatcher.setNodeState(control.node, WorkDispatcher.NodeState.PAUSED, control.time);
				break;
			case RESUME:
				dispatcher.setNodeState(control.node, WorkDispatcher.NodeState.ACTIVE, control.time);
				break;
			case UNAVAILABLE:
				dispatcher.setNodeState(control.node, WorkDispatcher.NodeState.UNAVAILABLE, control.time);
				break;
			case DRAIN:
				dispatcher.drain(control.node, control.time);
				break;
			case REDIRECT:
				dispatcher.redirect(control.node, control.count, control.time);
				break;
			case SWITCH_POLICY:
				dispatcher.switchPolicy(control.policy, control.time);
				break;
			default:
				break;
		}
	}

	/** Waiting lanes collect assignments the dispatcher made for them. */
	private static void pickUpPending(WorkDispatcher dispatcher, Config config,
			PriorityQueue<Event> queue, long[] sequence, boolean[][] waiting, double now)
	{
		for (int node = 1; node <= waiting.length; node++)
		{
			for (int lane = 0; lane < waiting[node - 1].length; lane++)
			{
				if (!waiting[node - 1][lane])
				{
					continue;
				}
				WorkDispatcher.Assignment assignment = dispatcher.request(node, lane, now);
				if (assignment != null)
				{
					waiting[node - 1][lane] = false;
					schedule(config, queue, sequence, assignment, now);
				}
			}
		}
	}

	private static void schedule(Config config, PriorityQueue<Event> queue, long[] sequence,
			WorkDispatcher.Assignment assignment, double now)
	{
		int node = assignment.getNode();
		double speed = config.actualSpeed != null && node - 1 < config.actualSpeed.length
				&& config.actualSpeed[node - 1] > 0.0
				? config.actualSpeed[node - 1]
				: config.nodes.get(node - 1).getSpeed();
		double start = now + assignment.getTransferDelayMs() / config.msPerTimeUnit;
		double end = start + assignment.getUnit().getCost() / speed;
		queue.add(new Event(end, sequence[0]++, UNIT_DONE, node, assignment.getLane(),
				assignment, start, null));
	}

	/**
	 * Work conservation for pull policies: after any event, no eligible lane may
	 * wait while the backlog holds a unit that fits it.
	 */
	private static void checkInvariants(WorkDispatcher dispatcher, Config config,
			boolean[][] waiting, List<String> violations, double now)
	{
		for (WorkDispatcher.NodeStats stats : dispatcher.nodeStats())
		{
			NodeSpec spec = config.nodes.get(stats.getNode() - 1);
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
		}
	}
}
