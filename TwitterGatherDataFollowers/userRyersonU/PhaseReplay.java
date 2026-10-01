package TwitterGatherDataFollowers.userRyersonU;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Replays a live run's work phase in the discrete-event simulator.
 *
 * The replay uses what the live run measured: the phase's units (with their
 * data sizes and homes), each unit's measured reference work (host CPU; a host
 * core is the reference core) and speed-independent time, the node specs at
 * the phase's opening, each node's recorded join time, the same seed for
 * transient faults, and every control and crash applied during the phase at
 * its recorded time. Only the scheduler's decisions and time are simulated.
 *
 * Two uses:
 * <ul>
 * <li>Validation: with the live policy the replay predicts the live phase;
 *     the relative error of the predicted makespan measures how faithful the
 *     model is (the simulator is only as good as this error is small).</li>
 * <li>Counterfactuals: with another policy (or other node specs) the replay
 *     answers "what would this phase have taken" for exactly the same work.</li>
 * </ul>
 */
final class PhaseReplay
{
	/** One replay of one phase. */
	static final class Result
	{
		private final String phase;
		private final SchedulerLabEngine.SchedulingPolicy policy;
		private final double liveMakespan;
		private final double predictedMakespan;
		private final double lowerBound;
		private final double sameNodeFraction;
		private final SchedulingSimulator.Trace trace;
		private final boolean complete;

		private Result(String phase, SchedulerLabEngine.SchedulingPolicy policy, double liveMakespan,
				double predictedMakespan, double lowerBound, double sameNodeFraction,
				SchedulingSimulator.Trace trace, boolean complete)
		{
			this.phase = phase;
			this.policy = policy;
			this.liveMakespan = liveMakespan;
			this.predictedMakespan = predictedMakespan;
			this.lowerBound = lowerBound;
			this.sameNodeFraction = sameNodeFraction;
			this.trace = trace;
			this.complete = complete;
		}

		String getPhase()
		{
			return phase;
		}

		SchedulerLabEngine.SchedulingPolicy getPolicy()
		{
			return policy;
		}

		double getLiveMakespan()
		{
			return liveMakespan;
		}

		double getPredictedMakespan()
		{
			return predictedMakespan;
		}

		/** (predicted - live) / live; NaN when the live phase did not complete. */
		double getRelativeError()
		{
			return liveMakespan > 0.0 && !Double.isNaN(liveMakespan)
					? (predictedMakespan - liveMakespan) / liveMakespan : Double.NaN;
		}

		double getLowerBound()
		{
			return lowerBound;
		}

		/** Fraction of units the replay committed on the node the live run committed them on. */
		double getSameNodeFraction()
		{
			return sameNodeFraction;
		}

		SchedulingSimulator.Trace getTrace()
		{
			return trace;
		}

		boolean isComplete()
		{
			return complete;
		}

		String describe()
		{
			return String.format(Locale.US,
					"%s with %s: live %.2f ms, predicted %.2f ms (error %+.1f%%), lower bound %.2f ms, same node %.0f%%",
					phase, policy.getLabel(), liveMakespan, predictedMakespan, getRelativeError() * 100.0,
					lowerBound, sameNodeFraction * 100.0);
		}
	}

	private PhaseReplay()
	{
	}

	/**
	 * The facts of a run a replay needs besides its recorder: whether it was
	 * emulated and at which dilation, its fault seed and crash detection
	 * timeout. Built from a live context or from a run record on disk.
	 */
	static final class Run
	{
		final String runId;
		final boolean emulating;
		final double dilation;
		final boolean faultInjection;
		final long faultSeed;
		final double detectionTimeoutMs;
		final RunRecorder recorder;

		Run(String runId, boolean emulating, double dilation, boolean faultInjection, long faultSeed,
				double detectionTimeoutMs, RunRecorder recorder)
		{
			this.runId = runId == null ? "" : runId;
			this.emulating = emulating;
			this.dilation = dilation > 0.0 && !Double.isNaN(dilation) ? dilation : 1.0;
			this.faultInjection = faultInjection;
			this.faultSeed = faultSeed;
			this.detectionTimeoutMs = Math.max(0.0, detectionTimeoutMs);
			this.recorder = recorder;
		}

		static Run of(SchedulingRunContext context)
		{
			return new Run(context.getRunId(), context.isEmulating(), context.clock().getDilation(),
					context.isFaultInjection(), context.getFaultSeed(),
					context.getSettings().getDetectionTimeoutMs(), context.recorder());
		}

		/** The phases that completed, in opening order. */
		List<RunRecorder.Phase> completedPhases()
		{
			ArrayList<RunRecorder.Phase> phases = new ArrayList<RunRecorder.Phase>();
			for (RunRecorder.Phase phase : recorder.phases())
			{
				if (!Double.isNaN(phase.completedAt))
				{
					phases.add(phase);
				}
			}
			return phases;
		}
	}

	/** Replays the phase with the policy it ran with (validation). */
	static Result validate(SchedulingRunContext context, String phaseKey)
	{
		return validate(Run.of(context), phaseKey);
	}

	static Result validate(Run run, String phaseKey)
	{
		RunRecorder.Phase phase = run.recorder.phase(phaseKey);
		if (phase == null)
		{
			throw new IllegalArgumentException("The run has no phase " + phaseKey + ".");
		}
		return replay(run, phaseKey, policyNamed(phase.policy), null);
	}

	/** Replays every phase of the run with its own policy. */
	static List<Result> validateAll(SchedulingRunContext context)
	{
		return validateAll(Run.of(context));
	}

	static List<Result> validateAll(Run run)
	{
		ArrayList<Result> results = new ArrayList<Result>();
		for (RunRecorder.Phase phase : run.completedPhases())
		{
			results.add(validate(run, phase.key));
		}
		return results;
	}

	/**
	 * Replays the phase with {@code policy} (counterfactual) and, when
	 * {@code specs} is not null, on other node specs (same count).
	 */
	static Result replay(SchedulingRunContext context, String phaseKey,
			SchedulerLabEngine.SchedulingPolicy policy, List<NodeSpec> specs)
	{
		return replay(Run.of(context), phaseKey, policy, specs);
	}

	static Result replay(Run run, String phaseKey, SchedulerLabEngine.SchedulingPolicy policy,
			List<NodeSpec> specs)
	{
		return replay(run, phaseKey, policy, specs, null);
	}

	/**
	 * The replay, optionally with a fault seed other than the run's (for
	 * replications of a counterfactual with faults; null keeps the run's).
	 */
	static Result replay(Run run, String phaseKey, SchedulerLabEngine.SchedulingPolicy policy,
			List<NodeSpec> specs, Long faultSeed)
	{
		RunRecorder recorder = run.recorder;
		RunRecorder.Phase phase = recorder.phase(phaseKey);
		if (phase == null)
		{
			throw new IllegalArgumentException("The run has no phase " + phaseKey + ".");
		}
		//A node faster than the run's time dilation ran at host speed.
		List<NodeSpec> nodes = effectiveNodes(run, specs == null ? phase.nodesAtOpen : specs);
		HashMap<Integer,double[]> actual = new HashMap<Integer,double[]>();
		HashMap<Integer,Integer> liveNode = new HashMap<Integer,Integer>();
		for (RunRecorder.UnitExecution execution : recorder.executions())
		{
			if (!phaseKey.equals(execution.phase) || !execution.isCommitted())
			{
				continue;
			}
			double speed = execution.node >= 1 && execution.node <= phase.nodesAtOpen.size()
					? phase.nodesAtOpen.get(execution.node - 1).getSpeed() : 1.0;
			double effective = run.emulating ? Math.min(speed, run.dilation) : 1.0;
			double reference = (execution.hostCpuNanos + execution.externalCpuNanos) / 1.0e6;
			double fixed = Math.max(0.0, execution.getDuration() - reference / Math.max(1.0e-9, effective));
			actual.put(Integer.valueOf(execution.unitId), new double[] {reference, fixed});
			liveNode.put(Integer.valueOf(execution.unitId), Integer.valueOf(execution.node));
		}
		SchedulingSimulator.Config config = new SchedulingSimulator.Config(policy, nodes,
				phase.unitSpecs,
				new WorkDispatcher.Options()
						.faultInjection(run.faultInjection, faultSeed == null ? run.faultSeed : faultSeed.longValue())
						.maxFailuresPerUnit(RecommendationSchedulerRegistry.MAX_FAILURES_PER_UNIT),
				1.0)
				.phaseKey(run.runId + "/" + phaseKey)
				.startTime(phase.openedAt)
				.actualCosts(actual)
				.detectionTimeout(run.detectionTimeoutMs);
		if (!run.emulating)
		{
			config.network(NetworkModel.free(nodes.size()));
		}
		double[] joins = new double[nodes.size()];
		java.util.Arrays.fill(joins, Double.NaN);
		for (Map.Entry<Integer,Double> join : recorder.phaseJoins(phaseKey).entrySet())
		{
			int node = join.getKey().intValue();
			if (node >= 1 && node <= joins.length)
			{
				joins[node - 1] = join.getValue().doubleValue();
			}
		}
		config.nodeStartTimes(joins);
		double end = Double.isNaN(phase.completedAt) ? Double.POSITIVE_INFINITY : phase.completedAt;
		for (RunRecorder.Control control : recorder.controls())
		{
			SchedulingSimulator.Control.Kind kind = kindOf(control.kind);
			if (kind == null || control.node > nodes.size())
			{
				continue;
			}
			//Controls before the phase set its starting state; later ones act in it.
			double at = Math.max(control.at, phase.openedAt);
			if (control.at > end && kind != SchedulingSimulator.Control.Kind.RECOVER)
			{
				continue;
			}
			if (kind == SchedulingSimulator.Control.Kind.CRASH)
			{
				//Repairs are replayed from their own recorded time.
				config.control(new SchedulingSimulator.Control(at, Double.NaN, kind, control.node, 0, null,
						Double.NaN));
				continue;
			}
			if (kind == SchedulingSimulator.Control.Kind.SWITCH_POLICY && specs == null
					&& control.policy != null && policyChanged(control.policy, policy, phase))
			{
				config.control(new SchedulingSimulator.Control(at, kind, 0, 0, control.policy));
				continue;
			}
			if (kind == SchedulingSimulator.Control.Kind.SWITCH_POLICY)
			{
				continue;
			}
			config.control(new SchedulingSimulator.Control(at, Double.NaN, kind, control.node,
					control.count, control.policy, control.value));
		}
		SchedulingSimulator.Trace trace = SchedulingSimulator.run(config);
		int same = 0;
		for (Map.Entry<Integer,Integer> commit : trace.getCommittedNode().entrySet())
		{
			Integer live = liveNode.get(commit.getKey());
			if (live != null && live.intValue() == commit.getValue().intValue())
			{
				same++;
			}
		}
		ArrayList<WorkUnitSpec> measured = new ArrayList<WorkUnitSpec>();
		for (WorkUnitSpec unit : phase.unitSpecs)
		{
			double[] cost = actual.get(Integer.valueOf(unit.getId()));
			measured.add(cost == null ? unit : unit.withCost(cost[0], cost[1]));
		}
		return new Result(phaseKey, policy, phase.getMakespan(), trace.getMakespan(),
				PlacementPlanner.bound(measured, effectiveNodes(run, fastestNodes(run, phase, specs == null
						? phase.nodesAtOpen : specs)), availability(run, phase, nodes.size()), run.emulating).value,
				liveNode.isEmpty() ? Double.NaN : same / (double)liveNode.size(), trace,
				trace.isComplete());
	}

	/** The nodes as they effectively ran: in an emulated run no node outran the time dilation factor. */
	static List<NodeSpec> effectiveNodes(Run run, List<NodeSpec> nodes)
	{
		if (!run.emulating)
		{
			return nodes;
		}
		ArrayList<NodeSpec> effective = new ArrayList<NodeSpec>();
		for (NodeSpec node : nodes)
		{
			effective.add(node.getSpeed() > run.dilation ? node.withSpeed(run.dilation) : node);
		}
		return effective;
	}

	/**
	 * When each node could first work in the phase, relative to its opening:
	 * its recorded join; a node that never joined took no part (+inf). Null
	 * when the record has no join times (older records).
	 */
	static double[] availability(Run run, RunRecorder.Phase phase, int nodeCount)
	{
		Map<Integer,Double> joins = run.recorder.phaseJoins(phase.key);
		if (joins.isEmpty())
		{
			return null;
		}
		double[] available = new double[nodeCount];
		java.util.Arrays.fill(available, Double.POSITIVE_INFINITY);
		for (Map.Entry<Integer,Double> join : joins.entrySet())
		{
			int node = join.getKey().intValue();
			if (node >= 1 && node <= nodeCount)
			{
				available[node - 1] = Math.max(0.0, join.getValue().doubleValue() - phase.openedAt);
			}
		}
		return available;
	}

	/**
	 * The nodes at the fastest each ran during the phase: a live capacity
	 * boost before the phase ended counts (a bound must hold however the
	 * speeds changed).
	 */
	static List<NodeSpec> fastestNodes(Run run, RunRecorder.Phase phase, List<NodeSpec> nodes)
	{
		double end = Double.isNaN(phase.completedAt) ? Double.POSITIVE_INFINITY : phase.completedAt;
		ArrayList<NodeSpec> fastest = new ArrayList<NodeSpec>();
		for (NodeSpec node : nodes)
		{
			double scale = node.getCapacityScale();
			for (RunRecorder.Control control : run.recorder.controls())
			{
				if (control.kind == Scenario.ActionKind.CAPACITY && control.node == node.getIndex()
						&& control.at <= end && !Double.isNaN(control.value))
				{
					scale = Math.max(scale, control.value);
				}
			}
			fastest.add(scale > node.getCapacityScale() + 1e-12
					? node.withSpeed(node.getSpeed() * scale / Math.max(1e-12, node.getCapacityScale())) : node);
		}
		return fastest;
	}

	/** The phase's measured lower bound (see {@link PlacementPlanner#bound}). */
	static PlacementPlanner.Bound measuredBound(Run run, String phaseKey)
	{
		RunRecorder.Phase phase = run.recorder.phase(phaseKey);
		List<NodeSpec> nodes = effectiveNodes(run, fastestNodes(run, phase, phase.nodesAtOpen));
		return PlacementPlanner.bound(measuredUnits(run, phaseKey), nodes,
				availability(run, phase, nodes.size()), run.emulating);
	}

	/**
	 * The phase's units with their measured work: reference CPU milliseconds
	 * and speed-independent time, as the replay uses them (units that never
	 * committed keep their estimates).
	 */
	static List<WorkUnitSpec> measuredUnits(Run run, String phaseKey)
	{
		RunRecorder.Phase phase = run.recorder.phase(phaseKey);
		if (phase == null)
		{
			throw new IllegalArgumentException("The run has no phase " + phaseKey + ".");
		}
		HashMap<Integer,double[]> actual = new HashMap<Integer,double[]>();
		for (RunRecorder.UnitExecution execution : run.recorder.executions())
		{
			if (!phaseKey.equals(execution.phase) || !execution.isCommitted())
			{
				continue;
			}
			double speed = execution.node >= 1 && execution.node <= phase.nodesAtOpen.size()
					? phase.nodesAtOpen.get(execution.node - 1).getSpeed() : 1.0;
			double effective = run.emulating ? Math.min(speed, run.dilation) : 1.0;
			double reference = (execution.hostCpuNanos + execution.externalCpuNanos) / 1.0e6;
			double fixed = Math.max(0.0, execution.getDuration() - reference / Math.max(1.0e-9, effective));
			actual.put(Integer.valueOf(execution.unitId), new double[] {reference, fixed});
		}
		ArrayList<WorkUnitSpec> units = new ArrayList<WorkUnitSpec>();
		for (WorkUnitSpec unit : phase.unitSpecs)
		{
			double[] cost = actual.get(Integer.valueOf(unit.getId()));
			units.add(cost == null ? unit : unit.withCost(cost[0], cost[1]));
		}
		return units;
	}

	/**
	 * Replays the phase under every policy (the same measured work), in policy
	 * order: which policy would have been best for exactly this phase.
	 */
	static List<Result> comparePolicies(SchedulingRunContext context, String phaseKey)
	{
		return comparePolicies(Run.of(context), phaseKey);
	}

	static List<Result> comparePolicies(Run run, String phaseKey)
	{
		ArrayList<Result> results = new ArrayList<Result>();
		for (SchedulerLabEngine.SchedulingPolicy policy : SchedulerLabEngine.SchedulingPolicy.values())
		{
			results.add(replay(run, phaseKey, policy, null));
		}
		return results;
	}

	/** Validation of every phase of the run as text lines. */
	static List<String> report(SchedulingRunContext context)
	{
		ArrayList<String> lines = new ArrayList<String>();
		for (Result result : validateAll(context))
		{
			lines.add(result.describe());
		}
		return lines.isEmpty() ? Collections.singletonList("The run has no completed work phase.") : lines;
	}

	private static boolean policyChanged(SchedulerLabEngine.SchedulingPolicy to,
			SchedulerLabEngine.SchedulingPolicy replayed, RunRecorder.Phase phase)
	{
		//A live policy switch is replayed only in validation (the replayed
		//policy is the phase's own); a counterfactual keeps its policy.
		return replayed == policyNamed(phase.policy);
	}

	private static SchedulingSimulator.Control.Kind kindOf(Scenario.ActionKind kind)
	{
		switch (kind)
		{
			case PAUSE:
				return SchedulingSimulator.Control.Kind.PAUSE;
			case RESUME:
				return SchedulingSimulator.Control.Kind.RESUME;
			case UNAVAILABLE:
				return SchedulingSimulator.Control.Kind.UNAVAILABLE;
			case AVAILABLE:
				return SchedulingSimulator.Control.Kind.AVAILABLE;
			case DRAIN:
				return SchedulingSimulator.Control.Kind.DRAIN;
			case REDIRECT:
				return SchedulingSimulator.Control.Kind.REDIRECT;
			case CAPACITY:
				return SchedulingSimulator.Control.Kind.CAPACITY;
			case POLICY:
				return SchedulingSimulator.Control.Kind.SWITCH_POLICY;
			case CRASH:
				return SchedulingSimulator.Control.Kind.CRASH;
			case RECOVER:
				return SchedulingSimulator.Control.Kind.RECOVER;
			default:
				return null;
		}
	}

	/** The policy of a label as recorded ("Capacity-weighted", ...). */
	static SchedulerLabEngine.SchedulingPolicy policyNamed(String label)
	{
		for (SchedulerLabEngine.SchedulingPolicy policy : SchedulerLabEngine.SchedulingPolicy.values())
		{
			if (policy.getLabel().equals(label) || policy.name().equals(label))
			{
				return policy;
			}
		}
		SchedulerLabEngine.SchedulingPolicy parsed = Scenario.policyNamed(label);
		return parsed == null ? SchedulerLabEngine.SchedulingPolicy.CAPACITY_WEIGHTED : parsed;
	}

	/** Mean absolute relative error of the phases' predicted makespans (NaN without phases). */
	static double meanAbsoluteError(List<Result> results)
	{
		double sum = 0.0;
		int count = 0;
		for (Result result : results)
		{
			double error = result.getRelativeError();
			if (!Double.isNaN(error))
			{
				sum += Math.abs(error);
				count++;
			}
		}
		return count == 0 ? Double.NaN : sum / count;
	}

	/** The results by policy label, for tables. */
	static LinkedHashMap<String,Double> makespanByPolicy(List<Result> results)
	{
		LinkedHashMap<String,Double> byPolicy = new LinkedHashMap<String,Double>();
		for (Result result : results)
		{
			byPolicy.put(result.getPolicy().getLabel(), Double.valueOf(result.getPredictedMakespan()));
		}
		return byPolicy;
	}
}
