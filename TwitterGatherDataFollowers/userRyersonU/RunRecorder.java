package TwitterGatherDataFollowers.userRyersonU;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Structured measurements of one run, in model milliseconds.
 *
 * Every live run (and every simulated phase) records the same things: each
 * execution of each work unit, each phase, each node stage, each collective
 * step, each operator action and fault, and the run's milestones. The records
 * are written as CSV and JSON by {@link RunRecordWriter} and summarized by
 * {@link RunMetrics}; nothing is sampled or capped. Thread-safe.
 */
final class RunRecorder
{
	/** One execution of one work unit. */
	static final class UnitExecution
	{
		final String phase;
		final int unitId;
		final String key;
		final int execution;
		final int node;
		final int lane;
		final int homeNode;
		final String source;
		final double estimatedCost;
		final double assignedAt;
		final double readyAt;
		final double startAt;
		final double endAt;
		final long hostCpuNanos;
		final long externalCpuNanos;
		final long hostWallNanos;
		final long transferredBytes;
		final String outcome;

		UnitExecution(String phase, int unitId, String key, int execution, int node, int lane,
				int homeNode, String source, double estimatedCost, double assignedAt, double readyAt,
				double startAt, double endAt, long hostCpuNanos, long externalCpuNanos,
				long hostWallNanos, long transferredBytes, String outcome)
		{
			this.phase = phase;
			this.unitId = unitId;
			this.key = key;
			this.execution = execution;
			this.node = node;
			this.lane = lane;
			this.homeNode = homeNode;
			this.source = source;
			this.estimatedCost = estimatedCost;
			this.assignedAt = assignedAt;
			this.readyAt = readyAt;
			this.startAt = startAt;
			this.endAt = endAt;
			this.hostCpuNanos = hostCpuNanos;
			this.externalCpuNanos = externalCpuNanos;
			this.hostWallNanos = hostWallNanos;
			this.transferredBytes = transferredBytes;
			this.outcome = outcome;
		}

		boolean isCommitted()
		{
			return "committed".equals(outcome);
		}

		double getDuration()
		{
			return Math.max(0.0, endAt - startAt);
		}
	}

	/** One work phase. */
	static final class Phase
	{
		final String key;
		final String label;
		final String policy;
		final String executionMode;
		final int units;
		final double openedAt;
		final double lowerBound;
		final List<WorkUnitSpec> unitSpecs;
		final List<NodeSpec> nodesAtOpen;
		double completedAt = Double.NaN;
		String status = "open";

		Phase(String key, String label, String policy, String executionMode, int units,
				double openedAt, double lowerBound, List<WorkUnitSpec> unitSpecs,
				List<NodeSpec> nodesAtOpen)
		{
			this.key = key;
			this.label = label;
			this.policy = policy;
			this.executionMode = executionMode;
			this.units = units;
			this.openedAt = openedAt;
			this.lowerBound = lowerBound;
			this.unitSpecs = unitSpecs;
			this.nodesAtOpen = nodesAtOpen;
		}

		double getMakespan()
		{
			return Double.isNaN(completedAt) ? Double.NaN : completedAt - openedAt;
		}
	}

	/** A node's pass through one pipeline stage. */
	static final class Stage
	{
		final int node;
		final String stage;
		final double startAt;
		final double endAt;

		Stage(int node, String stage, double startAt, double endAt)
		{
			this.node = node;
			this.stage = stage;
			this.startAt = startAt;
			this.endAt = endAt;
		}
	}

	/** One collective step (all-gather, all-reduce, barrier). */
	static final class Collective
	{
		final String step;
		final int nodes;
		final long uploadBytes;
		final long downloadBytes;
		final double firstArrivalAt;
		final double reduceStartAt;
		final double reduceEndAt;
		final long reduceCpuNanos;
		final double lastDeliveryAt;

		Collective(String step, int nodes, long uploadBytes, long downloadBytes,
				double firstArrivalAt, double reduceStartAt, double reduceEndAt,
				long reduceCpuNanos, double lastDeliveryAt)
		{
			this.step = step;
			this.nodes = nodes;
			this.uploadBytes = uploadBytes;
			this.downloadBytes = downloadBytes;
			this.firstArrivalAt = firstArrivalAt;
			this.reduceStartAt = reduceStartAt;
			this.reduceEndAt = reduceEndAt;
			this.reduceCpuNanos = reduceCpuNanos;
			this.lastDeliveryAt = lastDeliveryAt;
		}
	}

	/**
	 * A live control as it was applied, in structured form, so the phase can
	 * be replayed in the simulator with the same controls at the same times.
	 */
	static final class Control
	{
		final double at;
		final Scenario.ActionKind kind;
		final int node;
		final int target;
		final int count;
		final double value;
		final SchedulerLabEngine.SchedulingPolicy policy;

		Control(double at, Scenario.ActionKind kind, int node, int target, int count, double value,
				SchedulerLabEngine.SchedulingPolicy policy)
		{
			this.at = at;
			this.kind = kind;
			this.node = node;
			this.target = target;
			this.count = count;
			this.value = value;
			this.policy = policy;
		}
	}

	/** An operator action, fault, placement decision or warning. */
	static final class Event
	{
		final double at;
		final String kind;
		final int node;
		final String detail;

		Event(double at, String kind, int node, String detail)
		{
			this.at = at;
			this.kind = kind;
			this.node = node;
			this.detail = detail;
		}
	}

	private final String runId;
	private final ArrayList<UnitExecution> executions = new ArrayList<UnitExecution>();
	private final LinkedHashMap<String,Phase> phases = new LinkedHashMap<String,Phase>();
	private final ArrayList<Stage> stages = new ArrayList<Stage>();
	private final LinkedHashMap<String,double[]> openStages = new LinkedHashMap<String,double[]>();
	private final ArrayList<Collective> collectives = new ArrayList<Collective>();
	private final ArrayList<Event> events = new ArrayList<Event>();
	private final LinkedHashMap<String,Double> milestones = new LinkedHashMap<String,Double>();
	private final ArrayList<String> warnings = new ArrayList<String>();
	private final LinkedHashMap<String,String> properties = new LinkedHashMap<String,String>();
	private final ArrayList<Control> controls = new ArrayList<Control>();
	/** Phase key to per-node first-claim model time. */
	private final LinkedHashMap<String,LinkedHashMap<Integer,Double>> joins =
			new LinkedHashMap<String,LinkedHashMap<Integer,Double>>();
	private String status = "running";

	RunRecorder(String runId)
	{
		this.runId = runId == null ? "" : runId;
	}

	String getRunId()
	{
		return runId;
	}

	synchronized void execution(UnitExecution execution)
	{
		if (execution != null)
		{
			executions.add(execution);
		}
	}

	synchronized void phaseOpened(Phase phase)
	{
		if (phase != null && !phases.containsKey(phase.key))
		{
			phases.put(phase.key, phase);
		}
	}

	synchronized void phaseFinished(String key, double at, String status)
	{
		Phase phase = phases.get(key);
		if (phase != null && "open".equals(phase.status))
		{
			phase.completedAt = at;
			phase.status = status == null ? "completed" : status;
		}
	}

	/** Marks the start of a node's stage; ended by {@link #stageEnd}. */
	synchronized void stageStart(int node, String stage, double at)
	{
		openStages.put(node + "\u0000" + stage, new double[] {at});
	}

	synchronized void stageEnd(int node, String stage, double at)
	{
		double[] start = openStages.remove(node + "\u0000" + stage);
		if (start != null)
		{
			stages.add(new Stage(node, stage, start[0], at));
		}
	}

	/** Adds a finished stage directly (when loading a record back). */
	synchronized void stage(Stage stage)
	{
		if (stage != null)
		{
			stages.add(stage);
		}
	}

	synchronized void collective(Collective collective)
	{
		if (collective != null)
		{
			collectives.add(collective);
		}
	}

	synchronized void event(double at, String kind, int node, String detail)
	{
		events.add(new Event(at, kind == null ? "" : kind, node, detail == null ? "" : detail));
	}

	/** Records a named milestone once (the first time wins). */
	synchronized void milestone(String name, double at)
	{
		if (name != null && !milestones.containsKey(name))
		{
			milestones.put(name, Double.valueOf(at));
		}
	}

	synchronized void warning(String text)
	{
		if (text != null && !warnings.contains(text))
		{
			warnings.add(text);
		}
	}

	synchronized void control(Control control)
	{
		if (control != null)
		{
			controls.add(control);
		}
	}

	synchronized List<Control> controls()
	{
		return Collections.unmodifiableList(new ArrayList<Control>(controls));
	}

	/** The first time a node's lanes asked for work in a phase (it joined the phase). */
	synchronized void phaseJoined(String phase, int node, double at)
	{
		LinkedHashMap<Integer,Double> byNode = joins.get(phase);
		if (byNode == null)
		{
			byNode = new LinkedHashMap<Integer,Double>();
			joins.put(phase, byNode);
		}
		if (!byNode.containsKey(Integer.valueOf(node)))
		{
			byNode.put(Integer.valueOf(node), Double.valueOf(at));
		}
	}

	synchronized Map<Integer,Double> phaseJoins(String phase)
	{
		LinkedHashMap<Integer,Double> byNode = joins.get(phase);
		return byNode == null ? Collections.<Integer,Double>emptyMap()
				: Collections.unmodifiableMap(new LinkedHashMap<Integer,Double>(byNode));
	}

	/** Every phase's join times, by phase key. */
	synchronized Map<String,Map<Integer,Double>> allPhaseJoins()
	{
		LinkedHashMap<String,Map<Integer,Double>> copy = new LinkedHashMap<String,Map<Integer,Double>>();
		for (Map.Entry<String,LinkedHashMap<Integer,Double>> phase : joins.entrySet())
		{
			copy.put(phase.getKey(), Collections.unmodifiableMap(
					new LinkedHashMap<Integer,Double>(phase.getValue())));
		}
		return Collections.unmodifiableMap(copy);
	}

	/** A descriptive property of the run for its manifest (dataset, algorithm, ...). */
	synchronized void property(String key, String value)
	{
		if (key != null)
		{
			properties.put(key, value == null ? "" : value);
		}
	}

	synchronized Map<String,String> properties()
	{
		return Collections.unmodifiableMap(new LinkedHashMap<String,String>(properties));
	}

	/** Final status: running, completed, failed: reason, aborted. */
	synchronized void status(String value)
	{
		if ("running".equals(status) && value != null)
		{
			status = value;
		}
	}

	synchronized String getStatus()
	{
		return status;
	}

	synchronized List<UnitExecution> executions()
	{
		return Collections.unmodifiableList(new ArrayList<UnitExecution>(executions));
	}

	synchronized List<Phase> phases()
	{
		return Collections.unmodifiableList(new ArrayList<Phase>(phases.values()));
	}

	synchronized Phase phase(String key)
	{
		return phases.get(key);
	}

	synchronized List<Stage> stages()
	{
		return Collections.unmodifiableList(new ArrayList<Stage>(stages));
	}

	synchronized List<Collective> collectives()
	{
		return Collections.unmodifiableList(new ArrayList<Collective>(collectives));
	}

	synchronized List<Event> events()
	{
		return Collections.unmodifiableList(new ArrayList<Event>(events));
	}

	synchronized Map<String,Double> milestones()
	{
		return Collections.unmodifiableMap(new LinkedHashMap<String,Double>(milestones));
	}

	synchronized Double milestone(String name)
	{
		return milestones.get(name);
	}

	synchronized List<String> warnings()
	{
		return Collections.unmodifiableList(new ArrayList<String>(warnings));
	}
}
