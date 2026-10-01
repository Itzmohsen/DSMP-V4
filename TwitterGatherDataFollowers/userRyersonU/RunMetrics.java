package TwitterGatherDataFollowers.userRyersonU;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The analysis measures of one run, computed from its {@link RunRecorder}.
 *
 * Every time is model milliseconds. Definitions (see DSMP_V4_NODE_LAB.md):
 *
 * <ul>
 * <li>makespan: run start (Start SIM) to run end (every target answered);
 *     stage times split it at the run's milestones.</li>
 * <li>phase makespan: the phase opening to its last commit.</li>
 * <li>measured lower bound of a phase: {@link PlacementPlanner#bound} on the
 *     units' measured reference work (host CPU; a host core is the reference
 *     core) and speed-independent waits, with each node's join time, its
 *     fastest speed during the phase and the network
 *     ({@link PhaseReplay#measuredBound}); efficiency = lower bound /
 *     makespan, 1 being a perfect schedule.</li>
 * <li>imbalance: the busiest node's busy time over the mean busy time.</li>
 * <li>utilization: busy lane-time over available lane-time during the phase.</li>
 * <li>wasted work: lane-time of executions that did not commit (faults,
 *     drains, crashes) over all lane-time.</li>
 * </ul>
 */
final class RunMetrics
{
	/** Measures of one work phase. */
	static final class PhaseMetrics
	{
		String key;
		String label;
		String policy;
		String executionMode;
		int units;
		int executions;
		int committed;
		int faults;
		int revoked;
		int errors;
		int stolen;
		int offHome;
		long movedBytes;
		double openedAt;
		double completedAt;
		double makespan;
		double lowerBound;
		double estimatedLowerBound;
		/** The constraint that sets the measured lower bound (work, lanes, unit, pinned). */
		String lowerBoundKind = "";
		double efficiency;
		double imbalance;
		double utilization;
		double wastedFraction;
		double meanReadyDelay;
		double meanUnitDuration;
		double unitCostCv;
		String status;
	}

	/** Measures of one node. */
	static final class NodeMetrics
	{
		int node;
		String role;
		double speed;
		int lanes;
		double busy;
		double wasted;
		int committed;
		int crashes;
		long bytesUp;
		long bytesDown;
		double computeMs;
		double dilationMs;
		double owedMs;
		double networkWaitMs;
		double downtimeMs;
		double redoMs;
		double meanSleepErrorMs;
	}

	private final String runId;
	private final String status;
	private final double makespan;
	private final LinkedHashMap<String,Double> stages = new LinkedHashMap<String,Double>();
	private final List<PhaseMetrics> phases;
	private final List<NodeMetrics> nodes;
	private final long totalBytes;
	private final long collectiveBytes;
	private final int collectiveSteps;
	private final int crashes;
	private final int faults;
	private final int revocations;
	private final double hostUtilization;
	private final double dilation;
	private final double meanSleepErrorMs;
	private final List<String> warnings;
	private double replayError = Double.NaN;
	private double mainPhaseReplayError = Double.NaN;
	private String bestReplayPolicy = "";
	private double bestReplayMakespan = Double.NaN;

	private RunMetrics(String runId, String status, double makespan, List<PhaseMetrics> phases,
			List<NodeMetrics> nodes, long totalBytes, long collectiveBytes, int collectiveSteps,
			int crashes, int faults, int revocations, double hostUtilization, double dilation,
			double meanSleepErrorMs, List<String> warnings)
	{
		this.runId = runId;
		this.status = status;
		this.makespan = makespan;
		this.phases = Collections.unmodifiableList(phases);
		this.nodes = Collections.unmodifiableList(nodes);
		this.totalBytes = totalBytes;
		this.collectiveBytes = collectiveBytes;
		this.collectiveSteps = collectiveSteps;
		this.crashes = crashes;
		this.faults = faults;
		this.revocations = revocations;
		this.hostUtilization = hostUtilization;
		this.dilation = dilation;
		this.meanSleepErrorMs = meanSleepErrorMs;
		this.warnings = Collections.unmodifiableList(warnings);
	}

	static RunMetrics compute(SchedulingRunContext context)
	{
		RunRecorder recorder = context.recorder();
		Map<String,Double> milestones = recorder.milestones();
		double start = milestone(milestones, "run-start", 0.0);
		double end = milestone(milestones, "run-end", Double.NaN);
		List<RunRecorder.UnitExecution> executions = recorder.executions();

		ArrayList<PhaseMetrics> phaseMetrics = new ArrayList<PhaseMetrics>();
		PhaseReplay.Run run = PhaseReplay.Run.of(context);
		for (RunRecorder.Phase phase : recorder.phases())
		{
			phaseMetrics.add(phase(phase, executions, run));
		}

		List<NodeSpec> specs = context.currentSpecs();
		ArrayList<NodeMetrics> nodeMetrics = new ArrayList<NodeMetrics>();
		long totalBytes = context.network().totalBytes();
		double sleepErrorSum = 0.0;
		long sleeps = 0L;
		for (int node = 1; node <= context.getNodeCount(); node++)
		{
			NodeMetrics metrics = new NodeMetrics();
			NodeSpec spec = specs.get(node - 1);
			metrics.node = node;
			metrics.role = spec.getRoleLabel();
			metrics.speed = spec.getSpeed();
			metrics.lanes = spec.getLanes();
			for (RunRecorder.UnitExecution execution : executions)
			{
				if (execution.node != node)
				{
					continue;
				}
				metrics.busy += execution.getDuration();
				if (execution.isCommitted())
				{
					metrics.committed++;
				}
				else
				{
					metrics.wasted += execution.getDuration();
				}
			}
			metrics.crashes = context.crashCount(node);
			metrics.bytesUp = context.network().bytesUp(node);
			metrics.bytesDown = context.network().bytesDown(node);
			NodeEmulator.Ledger ledger = context.emulator(node).getLedger();
			double dilation = context.clock().getDilation();
			metrics.computeMs = ledger.getComputeNanos() / 1.0e6;
			metrics.dilationMs = ledger.getDilationNanos() / 1.0e6 / dilation;
			metrics.owedMs = ledger.getOwedNanos() / 1.0e6 / dilation;
			metrics.networkWaitMs = ledger.getNetworkNanos() / 1.0e6 / dilation;
			metrics.downtimeMs = ledger.getDowntimeNanos() / 1.0e6 / dilation;
			metrics.redoMs = ledger.getRedoNanos() / 1.0e6 / dilation;
			metrics.meanSleepErrorMs = ledger.getSleeps() == 0L ? 0.0
					: ledger.getSleepErrorNanos() / 1.0e6 / ledger.getSleeps();
			sleepErrorSum += ledger.getSleepErrorNanos() / 1.0e6;
			sleeps += ledger.getSleeps();
			nodeMetrics.add(metrics);
		}
		long collectiveBytes = 0L;
		List<RunRecorder.Collective> collectives = recorder.collectives();
		for (RunRecorder.Collective collective : collectives)
		{
			collectiveBytes += collective.uploadBytes + collective.downloadBytes;
		}
		int crashes = 0;
		int faults = 0;
		int revocations = 0;
		for (RunRecorder.UnitExecution execution : executions)
		{
			if ("fault".equals(execution.outcome))
			{
				faults++;
			}
			else if ("revoked".equals(execution.outcome))
			{
				revocations++;
			}
		}
		for (int node = 1; node <= context.getNodeCount(); node++)
		{
			crashes += context.crashCount(node);
		}
		RunMetrics metrics = new RunMetrics(context.getRunId(), recorder.getStatus(),
				Double.isNaN(end) ? Double.NaN : end - start, phaseMetrics, nodeMetrics,
				totalBytes, collectiveBytes, collectives.size(), crashes, faults, revocations,
				context.hostUtilization(), context.clock().getDilation(),
				sleeps == 0L ? 0.0 : sleepErrorSum / sleeps, recorder.warnings());
		metrics.stages.put("input", span(milestones, "run-start", "text-processing-complete"));
		metrics.stages.put("model", span(milestones, "model-start", "all-reported"));
		metrics.stages.put("merge", span(milestones, "all-reported", "merge-complete"));
		metrics.stages.put("delivery", span(milestones, "merge-complete", "run-end"));
		return metrics;
	}

	private static PhaseMetrics phase(RunRecorder.Phase phase, List<RunRecorder.UnitExecution> executions,
			PhaseReplay.Run run)
	{
		PhaseMetrics metrics = new PhaseMetrics();
		metrics.key = phase.key;
		metrics.label = phase.label;
		metrics.policy = phase.policy;
		metrics.executionMode = phase.executionMode;
		metrics.units = phase.units;
		metrics.openedAt = phase.openedAt;
		metrics.completedAt = phase.completedAt;
		metrics.makespan = phase.getMakespan();
		metrics.status = phase.status;
		metrics.estimatedLowerBound = phase.lowerBound;
		int nodeCount = phase.nodesAtOpen.size();
		double[] busy = new double[nodeCount];
		int lanes = 0;
		for (NodeSpec spec : phase.nodesAtOpen)
		{
			lanes += spec.getLanes();
		}
		double allTime = 0.0;
		double wastedTime = 0.0;
		double readyDelay = 0.0;
		LinkedHashMap<Integer,double[]> measured = new LinkedHashMap<Integer,double[]>();
		for (RunRecorder.UnitExecution execution : executions)
		{
			if (!phase.key.equals(execution.phase))
			{
				continue;
			}
			metrics.executions++;
			double duration = execution.getDuration();
			allTime += duration;
			readyDelay += Math.max(0.0, execution.startAt - execution.assignedAt);
			if (execution.node >= 1 && execution.node <= nodeCount)
			{
				busy[execution.node - 1] += duration;
			}
			if ("stolen".equals(execution.source))
			{
				metrics.stolen++;
			}
			if (execution.homeNode > 0 && execution.homeNode != execution.node)
			{
				metrics.offHome++;
			}
			metrics.movedBytes += execution.transferredBytes;
			if (execution.isCommitted())
			{
				metrics.committed++;
				double speed = execution.node >= 1 && execution.node <= nodeCount
						? phase.nodesAtOpen.get(execution.node - 1).getSpeed() : 1.0;
				double reference = (execution.hostCpuNanos + execution.externalCpuNanos) / 1.0e6;
				double fixed = Math.max(0.0, duration - reference / Math.max(1.0e-9, speed));
				measured.put(Integer.valueOf(execution.unitId), new double[] {reference, fixed, duration});
			}
			else
			{
				wastedTime += duration;
				if ("fault".equals(execution.outcome))
				{
					metrics.faults++;
				}
				else if ("revoked".equals(execution.outcome))
				{
					metrics.revoked++;
				}
				else
				{
					metrics.errors++;
				}
			}
		}
		ArrayList<WorkUnitSpec> measuredUnits = new ArrayList<WorkUnitSpec>();
		double costSum = 0.0;
		double costSquares = 0.0;
		double durationSum = 0.0;
		for (Map.Entry<Integer,double[]> entry : measured.entrySet())
		{
			double[] value = entry.getValue();
			measuredUnits.add(new WorkUnitSpec(entry.getKey().intValue(), "m" + entry.getKey(), value[0])
					.withCost(value[0], value[1]));
			costSum += value[0];
			costSquares += value[0] * value[0];
			durationSum += value[2];
		}
		int count = measured.size();
		metrics.meanUnitDuration = count == 0 ? Double.NaN : durationSum / count;
		double meanCost = count == 0 ? 0.0 : costSum / count;
		double variance = count < 2 ? 0.0 : Math.max(0.0, costSquares / count - meanCost * meanCost);
		metrics.unitCostCv = meanCost <= 0.0 ? Double.NaN : Math.sqrt(variance) / meanCost;
		PlacementPlanner.Bound bound = PhaseReplay.measuredBound(run, phase.key);
		metrics.lowerBound = bound.value;
		metrics.lowerBoundKind = bound.kind;
		metrics.efficiency = metrics.makespan > 0.0 && !Double.isNaN(metrics.makespan)
				? Math.min(1.0, metrics.lowerBound / metrics.makespan) : Double.NaN;
		double busySum = 0.0;
		double busyMax = 0.0;
		for (double value : busy)
		{
			busySum += value;
			busyMax = Math.max(busyMax, value);
		}
		metrics.imbalance = busySum <= 0.0 ? Double.NaN : busyMax / (busySum / nodeCount);
		metrics.utilization = metrics.makespan > 0.0 && lanes > 0
				? busySum / (lanes * metrics.makespan) : Double.NaN;
		metrics.wastedFraction = allTime <= 0.0 ? 0.0 : wastedTime / allTime;
		metrics.meanReadyDelay = metrics.executions == 0 ? Double.NaN : readyDelay / metrics.executions;
		return metrics;
	}

	private static double milestone(Map<String,Double> milestones, String name, double fallback)
	{
		Double value = milestones.get(name);
		return value == null ? fallback : value.doubleValue();
	}

	private static double span(Map<String,Double> milestones, String from, String to)
	{
		Double first = milestones.get(from);
		Double second = milestones.get(to);
		return first == null || second == null ? Double.NaN : second.doubleValue() - first.doubleValue();
	}

	String getRunId()
	{
		return runId;
	}

	String getStatus()
	{
		return status;
	}

	/** End-to-end model time from Start SIM to the last answered target. */
	double getMakespan()
	{
		return makespan;
	}

	Map<String,Double> getStages()
	{
		return Collections.unmodifiableMap(stages);
	}

	List<PhaseMetrics> getPhases()
	{
		return phases;
	}

	List<NodeMetrics> getNodes()
	{
		return nodes;
	}

	long getTotalBytes()
	{
		return totalBytes;
	}

	long getCollectiveBytes()
	{
		return collectiveBytes;
	}

	int getCollectiveSteps()
	{
		return collectiveSteps;
	}

	int getCrashes()
	{
		return crashes;
	}

	int getFaults()
	{
		return faults;
	}

	int getRevocations()
	{
		return revocations;
	}

	double getHostUtilization()
	{
		return hostUtilization;
	}

	double getDilation()
	{
		return dilation;
	}

	double getMeanSleepErrorMs()
	{
		return meanSleepErrorMs;
	}

	List<String> getWarnings()
	{
		return warnings;
	}

	/** The run's busiest-to-mean node busy ratio across all unit phases. */
	double getImbalance()
	{
		double sum = 0.0;
		double max = 0.0;
		for (NodeMetrics node : nodes)
		{
			sum += node.busy;
			max = Math.max(max, node.busy);
		}
		return sum <= 0.0 ? Double.NaN : max / (sum / nodes.size());
	}

	/** Wasted lane-time over all lane-time across all unit phases. */
	double getWastedFraction()
	{
		double busy = 0.0;
		double wasted = 0.0;
		for (NodeMetrics node : nodes)
		{
			busy += node.busy;
			wasted += node.wasted;
		}
		return busy <= 0.0 ? 0.0 : wasted / busy;
	}

	/** Makespan of the main unit phase (the one with the most units), NaN without phases. */
	double getMainPhaseMakespan()
	{
		PhaseMetrics main = mainPhase();
		return main == null ? Double.NaN : main.makespan;
	}

	PhaseMetrics mainPhase()
	{
		PhaseMetrics main = null;
		for (PhaseMetrics phase : phases)
		{
			if (main == null || phase.units > main.units)
			{
				main = phase;
			}
		}
		return main;
	}

	/**
	 * Records the simulator replay of this run's phases: the mean absolute
	 * relative error of the predicted phase makespans, the main phase's signed
	 * error, and the policy the simulator predicts would have been fastest
	 * for the main phase.
	 */
	synchronized void setReplay(double meanAbsoluteError, double mainPhaseError, String bestPolicy,
			double bestMakespan)
	{
		replayError = meanAbsoluteError;
		mainPhaseReplayError = mainPhaseError;
		bestReplayPolicy = bestPolicy == null ? "" : bestPolicy;
		bestReplayMakespan = bestMakespan;
	}

	synchronized double getReplayError()
	{
		return replayError;
	}

	synchronized double getMainPhaseReplayError()
	{
		return mainPhaseReplayError;
	}

	synchronized String getBestReplayPolicy()
	{
		return bestReplayPolicy;
	}

	/** Flat named measures for one-line-per-run tables (studies, the run index). */
	synchronized LinkedHashMap<String,Double> summary()
	{
		LinkedHashMap<String,Double> values = new LinkedHashMap<String,Double>();
		values.put("makespan_ms", Double.valueOf(makespan));
		for (Map.Entry<String,Double> stage : stages.entrySet())
		{
			values.put("stage_" + stage.getKey() + "_ms", stage.getValue());
		}
		PhaseMetrics main = mainPhase();
		values.put("phase_makespan_ms", Double.valueOf(main == null ? Double.NaN : main.makespan));
		values.put("phase_lower_bound_ms", Double.valueOf(main == null ? Double.NaN : main.lowerBound));
		values.put("phase_efficiency", Double.valueOf(main == null ? Double.NaN : main.efficiency));
		values.put("phase_imbalance", Double.valueOf(main == null ? Double.NaN : main.imbalance));
		values.put("phase_utilization", Double.valueOf(main == null ? Double.NaN : main.utilization));
		values.put("phase_wasted_fraction", Double.valueOf(main == null ? Double.NaN : main.wastedFraction));
		values.put("imbalance", Double.valueOf(getImbalance()));
		values.put("wasted_fraction", Double.valueOf(getWastedFraction()));
		values.put("bytes_total", Double.valueOf(totalBytes));
		values.put("bytes_collectives", Double.valueOf(collectiveBytes));
		values.put("crashes", Double.valueOf(crashes));
		values.put("faults", Double.valueOf(faults));
		values.put("revocations", Double.valueOf(revocations));
		values.put("host_utilization", Double.valueOf(hostUtilization));
		values.put("tdf", Double.valueOf(dilation));
		values.put("mean_sleep_error_ms", Double.valueOf(meanSleepErrorMs));
		values.put("replay_abs_error", Double.valueOf(replayError));
		values.put("replay_main_phase_error", Double.valueOf(mainPhaseReplayError));
		values.put("replay_best_makespan_ms", Double.valueOf(bestReplayMakespan));
		return values;
	}

	String describe()
	{
		StringBuilder builder = new StringBuilder();
		builder.append(String.format(Locale.US, "Run %s (%s): makespan %.1f ms", runId, status, makespan));
		for (Map.Entry<String,Double> stage : stages.entrySet())
		{
			builder.append(String.format(Locale.US, ", %s %.1f", stage.getKey(), stage.getValue()));
		}
		for (PhaseMetrics phase : phases)
		{
			builder.append(String.format(Locale.US,
					"%n  phase %s (%s): %d units, makespan %.1f ms, lower bound %.1f ms, efficiency %.3f, imbalance %.3f, utilization %.3f, wasted %.3f, moved %d B",
					phase.key, phase.policy, phase.units, phase.makespan, phase.lowerBound, phase.efficiency,
					phase.imbalance, phase.utilization, phase.wastedFraction, phase.movedBytes));
		}
		builder.append(String.format(Locale.US, "%n  network %d B (collectives %d B in %d steps), crashes %d, faults %d, revoked %d, TDF %.2f, host CPU %.0f%%, mean sleep error %.3f ms",
				totalBytes, collectiveBytes, collectiveSteps, crashes, faults, revocations, dilation,
				hostUtilization * 100.0, meanSleepErrorMs));
		return builder.toString();
	}
}
