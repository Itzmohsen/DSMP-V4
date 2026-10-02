package TwitterGatherDataFollowers.userRyersonU;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Writes one run's record to {@code Results/Runs/<run>/} and adds a line to
 * {@code Results/Runs/runs-index.csv}.
 *
 * <pre>
 * manifest.json     configuration: dataset, algorithm, mode, policy, cluster,
 *                   scenario, seeds, time dilation, environment, results hash
 * metrics.json      {@link RunMetrics}: stages, phases, nodes, network, faults
 * units.csv         every execution of every work unit (model milliseconds)
 * phases.csv        every work phase with its measures
 * phase-units.csv   every phase's unit specs (for simulator replay)
 * phase-nodes.csv   every phase's node specs at its opening (for replay)
 * nodes.csv         per-node measures and emulation ledger
 * stages.csv        each node's pipeline stages
 * collectives.csv   every collective step: bytes, reduction, delivery
 * events.csv        operator actions, scenario steps, faults, placement
 * milestones.csv    run milestones
 * controls.csv      every live control applied, as the replay re-applies it
 * phase-joins.csv   when each node joined each phase (for replay)
 * replay.csv        each phase replayed with its live policy (model check)
 * counterfactuals.csv  the main phase replayed under every policy
 * </pre>
 *
 * The results hash is the SHA-256 of the merged recommendations (every
 * target's ranked candidates and the exact bits of their scores), so two runs
 * that must agree can be compared from their manifests alone.
 */
final class RunRecordWriter
{
	private static final Object INDEX_LOCK = new Object();
	private static final Map<String,String> DATASET_HASHES = new HashMap<String,String>();
	private static String gitDescription;

	private RunRecordWriter()
	{
	}

	/** The directory runs are written into (the working directory's Results/Runs). */
	static File runsDirectory()
	{
		return new File(System.getProperty("dsmp.results.dir", "Results"), "Runs");
	}

	/**
	 * Writes the run's record. Returns the run's directory, or null when it
	 * could not be written (the reason is printed).
	 */
	static File write(SchedulingRunContext context, RunMetrics metrics)
	{
		RunRecorder recorder = context.recorder();
		Map<String,String> properties = recorder.properties();
		String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
				.format(new Date(context.getCreatedAtMillis()));
		String algorithm = sanitize(properties.containsKey("algorithm")
				? properties.get("algorithm") : "run");
		String name = stamp + "_" + algorithm + "_" + context.getNodeCount() + "n_"
				+ sanitize(context.getBasePolicy().name().toLowerCase(Locale.US)) + "_"
				+ sanitize(shortId(context.getRunId()));
		File directory = new File(runsDirectory(), name);
		try
		{
			if (!directory.isDirectory() && !directory.mkdirs())
			{
				throw new IOException("cannot create " + directory.getPath());
			}
			String resultsHash = resultsHash(context.getRunId());
			writeText(new File(directory, "manifest.json"),
					JsonText.of(manifest(context, metrics, resultsHash)));
			writeText(new File(directory, "metrics.json"), JsonText.of(metricsJson(metrics)));
			writeUnits(new File(directory, "units.csv"), recorder.executions());
			writePhases(new File(directory, "phases.csv"), metrics.getPhases());
			writePhaseUnits(new File(directory, "phase-units.csv"), recorder.phases());
			writePhaseNodes(new File(directory, "phase-nodes.csv"), recorder.phases());
			writeNodes(new File(directory, "nodes.csv"), metrics.getNodes());
			writeStages(new File(directory, "stages.csv"), recorder.stages());
			writeCollectives(new File(directory, "collectives.csv"), recorder.collectives());
			writeEvents(new File(directory, "events.csv"), recorder.events());
			writeMilestones(new File(directory, "milestones.csv"), recorder.milestones());
			writeControls(new File(directory, "controls.csv"), recorder.controls());
			writePhaseJoins(new File(directory, "phase-joins.csv"), recorder.allPhaseJoins());
			RecommendationQualityEvaluator.Report quality = writeQuality(directory, context.getRunId());
			RunGraphAnalysis graph = writeGraph(directory, context);
			writeTemporalGraphFoundation(directory, context);
			appendIndex(name, context, metrics, resultsHash, quality, graph);
			return directory;
		}
		catch (IOException failure)
		{
			System.err.println("Could not write the run record for " + context.getRunId() + ": "
					+ failure.getMessage());
			return null;
		}
	}

	private static LinkedHashMap<String,Object> manifest(SchedulingRunContext context, RunMetrics metrics,
			String resultsHash)
	{
		RunRecorder recorder = context.recorder();
		LinkedHashMap<String,Object> manifest = new LinkedHashMap<String,Object>();
		manifest.put("format", "dsmp-node-lab-run/1");
		manifest.put("runId", context.getRunId());
		manifest.put("status", recorder.getStatus());
		manifest.put("created", iso(context.getCreatedAtMillis()));
		manifest.put("written", iso(System.currentTimeMillis()));
		manifest.put("properties", new LinkedHashMap<String,Object>(recorder.properties()));
		String dataset = recorder.properties().get("dataset");
		if (dataset != null && dataset.length() > 0)
		{
			manifest.put("datasetSha256", datasetHash(new File(dataset)));
		}
		manifest.put("resultsSha256", resultsHash);
		LinkedHashMap<String,Object> scheduling = new LinkedHashMap<String,Object>();
		scheduling.put("mode", context.getMode().getLabel());
		scheduling.put("policy", context.getBasePolicy().getLabel());
		scheduling.put("finalPolicy", context.effectivePolicy().getLabel());
		scheduling.put("nodes", Integer.valueOf(context.getNodeCount()));
		scheduling.put("timeDilation", Double.valueOf(context.clock().getDilation()));
		scheduling.put("clusterSettings", context.getSettings().describe());
		scheduling.put("faultSeed", Long.valueOf(context.getFaultSeed()));
		scheduling.put("emulating", Boolean.valueOf(context.isEmulating()));
		scheduling.put("faultInjection", Boolean.valueOf(context.isFaultInjection()));
		scheduling.put("detectionTimeoutMs", Integer.valueOf(context.getSettings().getDetectionTimeoutMs()));
		scheduling.put("coordinatorSpeed", Double.valueOf(context.getSettings().getCoordinatorSpeed()));
		manifest.put("scheduling", scheduling);
		ArrayList<Object> nodes = new ArrayList<Object>();
		for (NodeCapacityProfile profile : context.getProfiles())
		{
			LinkedHashMap<String,Object> node = new LinkedHashMap<String,Object>();
			node.put("node", Integer.valueOf(profile.getNodeIndex()));
			node.put("role", profile.getRoleLabel());
			node.put("cpuMultiplier", Double.valueOf(profile.getCpuMultiplier()));
			node.put("speed", Double.valueOf(profile.effectiveSpeed()));
			node.put("lanes", Integer.valueOf(profile.getMaxConcurrentJobs()));
			node.put("memoryMb", Integer.valueOf(profile.getMemoryLimitMb()));
			node.put("queueCapacity", Integer.valueOf(profile.getQueueCapacity()));
			node.put("latencyMs", Integer.valueOf(profile.getNetworkDelayMs()));
			node.put("bandwidthMbps", Double.valueOf(profile.getBandwidthMbps()));
			node.put("transientFaultPercent", Integer.valueOf(profile.isFailureSimulationEnabled()
					? profile.getFailureProbabilityPercent() : 0));
			node.put("mtbfSeconds", Double.valueOf(profile.getMtbfSeconds()));
			node.put("mttrSeconds", Double.valueOf(profile.getMttrSeconds()));
			nodes.add(node);
		}
		manifest.put("cluster", nodes);
		LinkedHashMap<String,Object> scenario = new LinkedHashMap<String,Object>();
		scenario.put("name", context.getScenario().getName());
		scenario.put("fingerprint", context.getScenario().fingerprint());
		scenario.put("text", context.getScenario().toText());
		manifest.put("scenario", scenario);
		manifest.put("environment", environment());
		manifest.put("summary", new LinkedHashMap<String,Object>(metrics.summary()));
		manifest.put("warnings", new ArrayList<Object>(recorder.warnings()));
		return manifest;
	}

	private static LinkedHashMap<String,Object> metricsJson(RunMetrics metrics)
	{
		LinkedHashMap<String,Object> json = new LinkedHashMap<String,Object>();
		json.put("runId", metrics.getRunId());
		json.put("status", metrics.getStatus());
		json.put("summary", new LinkedHashMap<String,Object>(metrics.summary()));
		json.put("stagesMs", new LinkedHashMap<String,Object>(metrics.getStages()));
		ArrayList<Object> phases = new ArrayList<Object>();
		for (RunMetrics.PhaseMetrics phase : metrics.getPhases())
		{
			LinkedHashMap<String,Object> value = new LinkedHashMap<String,Object>();
			value.put("key", phase.key);
			value.put("policy", phase.policy);
			value.put("units", Integer.valueOf(phase.units));
			value.put("executions", Integer.valueOf(phase.executions));
			value.put("makespanMs", Double.valueOf(phase.makespan));
			value.put("lowerBoundMs", Double.valueOf(phase.lowerBound));
			value.put("lowerBoundKind", phase.lowerBoundKind);
			value.put("efficiency", Double.valueOf(phase.efficiency));
			value.put("imbalance", Double.valueOf(phase.imbalance));
			value.put("utilization", Double.valueOf(phase.utilization));
			value.put("wastedFraction", Double.valueOf(phase.wastedFraction));
			value.put("movedBytes", Long.valueOf(phase.movedBytes));
			value.put("stolen", Integer.valueOf(phase.stolen));
			value.put("offHome", Integer.valueOf(phase.offHome));
			value.put("faults", Integer.valueOf(phase.faults));
			value.put("revoked", Integer.valueOf(phase.revoked));
			phases.add(value);
		}
		json.put("phases", phases);
		json.put("warnings", new ArrayList<Object>(metrics.getWarnings()));
		return json;
	}

	private static void writeUnits(File file, List<RunRecorder.UnitExecution> executions) throws IOException
	{
		Writer out = open(file);
		try
		{
			out.write("phase,unit_id,key,execution,node,lane,home_node,source,estimated_cost,assigned_at_ms,"
					+ "ready_at_ms,start_ms,end_ms,duration_ms,host_cpu_ms,worker_cpu_ms,host_wall_ms,"
					+ "moved_bytes,outcome\n");
			for (RunRecorder.UnitExecution e : executions)
			{
				out.write(JsonText.csvLine(e.phase, Integer.valueOf(e.unitId), e.key, Integer.valueOf(e.execution),
						Integer.valueOf(e.node), Integer.valueOf(e.lane), Integer.valueOf(e.homeNode), e.source,
						Double.valueOf(e.estimatedCost), Double.valueOf(e.assignedAt), Double.valueOf(e.readyAt),
						Double.valueOf(e.startAt), Double.valueOf(e.endAt), Double.valueOf(e.getDuration()),
						Double.valueOf(e.hostCpuNanos / 1.0e6), Double.valueOf(e.externalCpuNanos / 1.0e6),
						Double.valueOf(e.hostWallNanos / 1.0e6), Long.valueOf(e.transferredBytes), e.outcome));
			}
		}
		finally
		{
			out.close();
		}
	}

	private static void writePhases(File file, List<RunMetrics.PhaseMetrics> phases) throws IOException
	{
		Writer out = open(file);
		try
		{
			out.write("phase,label,policy,execution_mode,units,executions,committed,opened_at_ms,completed_at_ms,"
					+ "makespan_ms,lower_bound_ms,lower_bound_kind,estimated_lower_bound,efficiency,imbalance,utilization,"
					+ "wasted_fraction,mean_ready_delay_ms,mean_unit_ms,unit_cost_cv,stolen,off_home,moved_bytes,"
					+ "faults,revoked,errors,status\n");
			for (RunMetrics.PhaseMetrics p : phases)
			{
				out.write(JsonText.csvLine(p.key, p.label, p.policy, p.executionMode, Integer.valueOf(p.units),
						Integer.valueOf(p.executions), Integer.valueOf(p.committed), Double.valueOf(p.openedAt),
						Double.valueOf(p.completedAt), Double.valueOf(p.makespan), Double.valueOf(p.lowerBound),
						p.lowerBoundKind, Double.valueOf(p.estimatedLowerBound), Double.valueOf(p.efficiency),
						Double.valueOf(p.imbalance), Double.valueOf(p.utilization), Double.valueOf(p.wastedFraction),
						Double.valueOf(p.meanReadyDelay), Double.valueOf(p.meanUnitDuration),
						Double.valueOf(p.unitCostCv), Integer.valueOf(p.stolen), Integer.valueOf(p.offHome),
						Long.valueOf(p.movedBytes), Integer.valueOf(p.faults), Integer.valueOf(p.revoked),
						Integer.valueOf(p.errors), p.status));
			}
		}
		finally
		{
			out.close();
		}
	}

	private static void writePhaseUnits(File file, List<RunRecorder.Phase> phases) throws IOException
	{
		Writer out = open(file);
		try
		{
			out.write("phase,unit_id,key,estimated_cost,fixed,memory_mb,priority,deadline,home_node,"
					+ "allowed_nodes,input_bytes,output_bytes,max_failures\n");
			for (RunRecorder.Phase phase : phases)
			{
				for (WorkUnitSpec unit : phase.unitSpecs)
				{
					int[] allowed = unit.allowedNodesCopy();
					StringBuilder nodes = new StringBuilder();
					if (allowed != null)
					{
						for (int index = 0; index < allowed.length; index++)
						{
							nodes.append(index == 0 ? "" : " ").append(allowed[index]);
						}
					}
					out.write(JsonText.csvLine(phase.key, Integer.valueOf(unit.getId()), unit.getKey(),
							Double.valueOf(unit.getCost()), Double.valueOf(unit.getFixedMs()),
							Integer.valueOf(unit.getMemoryMb()), Integer.valueOf(unit.getPriority()),
							Double.isInfinite(unit.getDeadline()) ? "" : Double.valueOf(unit.getDeadline()),
							Integer.valueOf(unit.getHomeNode()), nodes.toString(),
							Long.valueOf(unit.getInputBytes()), Long.valueOf(unit.getOutputBytes()),
							Integer.valueOf(unit.getMaxFailures())));
				}
			}
		}
		finally
		{
			out.close();
		}
	}

	private static void writePhaseNodes(File file, List<RunRecorder.Phase> phases) throws IOException
	{
		Writer out = open(file);
		try
		{
			out.write("phase,node,role,speed,lanes,memory_mb,queue_capacity,latency_ms,bandwidth_bytes_per_ms,"
					+ "fault_probability,mtbf_ms,mttr_ms\n");
			for (RunRecorder.Phase phase : phases)
			{
				for (NodeSpec spec : phase.nodesAtOpen)
				{
					out.write(JsonText.csvLine(phase.key, Integer.valueOf(spec.getIndex()), spec.getRoleLabel(),
							Double.valueOf(spec.getSpeed()), Integer.valueOf(spec.getLanes()),
							Integer.valueOf(spec.getMemoryMb()), Integer.valueOf(spec.getQueueCapacity()),
							Integer.valueOf(spec.getDelayMs()), Double.valueOf(spec.getBandwidthBytesPerMs()),
							Double.valueOf(spec.getFailureProbability()), Double.valueOf(spec.getMtbfMs()),
							Double.valueOf(spec.getMttrMs())));
				}
			}
		}
		finally
		{
			out.close();
		}
	}

	private static void writeNodes(File file, List<RunMetrics.NodeMetrics> nodes) throws IOException
	{
		Writer out = open(file);
		try
		{
			out.write("node,role,speed,lanes,busy_ms,wasted_ms,committed,crashes,bytes_up,bytes_down,"
					+ "host_compute_ms,speed_emulation_ms,owed_emulation_ms,network_wait_ms,downtime_ms,redo_ms,"
					+ "mean_sleep_error_ms\n");
			for (RunMetrics.NodeMetrics n : nodes)
			{
				out.write(JsonText.csvLine(Integer.valueOf(n.node), n.role, Double.valueOf(n.speed),
						Integer.valueOf(n.lanes), Double.valueOf(n.busy), Double.valueOf(n.wasted),
						Integer.valueOf(n.committed), Integer.valueOf(n.crashes), Long.valueOf(n.bytesUp),
						Long.valueOf(n.bytesDown), Double.valueOf(n.computeMs), Double.valueOf(n.dilationMs),
						Double.valueOf(n.owedMs), Double.valueOf(n.networkWaitMs), Double.valueOf(n.downtimeMs),
						Double.valueOf(n.redoMs), Double.valueOf(n.meanSleepErrorMs)));
			}
		}
		finally
		{
			out.close();
		}
	}

	private static void writeStages(File file, List<RunRecorder.Stage> stages) throws IOException
	{
		Writer out = open(file);
		try
		{
			out.write("node,stage,start_ms,end_ms,duration_ms\n");
			for (RunRecorder.Stage stage : stages)
			{
				out.write(JsonText.csvLine(Integer.valueOf(stage.node), stage.stage, Double.valueOf(stage.startAt),
						Double.valueOf(stage.endAt), Double.valueOf(stage.endAt - stage.startAt)));
			}
		}
		finally
		{
			out.close();
		}
	}

	private static void writeCollectives(File file, List<RunRecorder.Collective> collectives) throws IOException
	{
		Writer out = open(file);
		try
		{
			out.write("step,nodes,upload_bytes,download_bytes,first_arrival_ms,reduce_start_ms,reduce_end_ms,"
					+ "reduce_cpu_ms,last_delivery_ms\n");
			for (RunRecorder.Collective c : collectives)
			{
				out.write(JsonText.csvLine(c.step, Integer.valueOf(c.nodes), Long.valueOf(c.uploadBytes),
						Long.valueOf(c.downloadBytes), Double.valueOf(c.firstArrivalAt),
						Double.valueOf(c.reduceStartAt), Double.valueOf(c.reduceEndAt),
						Double.valueOf(c.reduceCpuNanos / 1.0e6), Double.valueOf(c.lastDeliveryAt)));
			}
		}
		finally
		{
			out.close();
		}
	}

	private static void writeEvents(File file, List<RunRecorder.Event> events) throws IOException
	{
		Writer out = open(file);
		try
		{
			out.write("at_ms,kind,node,detail\n");
			for (RunRecorder.Event event : events)
			{
				out.write(JsonText.csvLine(Double.valueOf(event.at), event.kind, Integer.valueOf(event.node),
						event.detail));
			}
		}
		finally
		{
			out.close();
		}
	}

	private static void writeMilestones(File file, Map<String,Double> milestones) throws IOException
	{
		Writer out = open(file);
		try
		{
			out.write("milestone,at_ms\n");
			for (Map.Entry<String,Double> milestone : milestones.entrySet())
			{
				out.write(JsonText.csvLine(milestone.getKey(), milestone.getValue()));
			}
		}
		finally
		{
			out.close();
		}
	}

	private static void writeControls(File file, List<RunRecorder.Control> controls) throws IOException
	{
		Writer out = open(file);
		try
		{
			out.write("at_ms,action,node,target,count,value,policy\n");
			for (RunRecorder.Control control : controls)
			{
				out.write(JsonText.csvLine(Double.valueOf(control.at), control.kind.name(),
						Integer.valueOf(control.node), Integer.valueOf(control.target), Integer.valueOf(control.count),
						Double.valueOf(control.value), control.policy == null ? "" : control.policy.name()));
			}
		}
		finally
		{
			out.close();
		}
	}

	private static void writePhaseJoins(File file, Map<String,Map<Integer,Double>> joins) throws IOException
	{
		Writer out = open(file);
		try
		{
			out.write("phase,node,joined_at_ms\n");
			for (Map.Entry<String,Map<Integer,Double>> phase : joins.entrySet())
			{
				for (Map.Entry<Integer,Double> join : phase.getValue().entrySet())
				{
					out.write(JsonText.csvLine(phase.getKey(), join.getKey(), join.getValue()));
				}
			}
		}
		finally
		{
			out.close();
		}
	}

	/** Writes replay.csv (validation per phase) and counterfactuals.csv (main phase under every policy). */
	static void writeReplay(File directory, List<PhaseReplay.Result> validation,
			List<PhaseReplay.Result> counterfactuals)
	{
		try
		{
			Writer out = open(new File(directory, "replay.csv"));
			try
			{
				out.write("phase,policy,live_makespan_ms,predicted_makespan_ms,relative_error,lower_bound_ms,"
						+ "same_node_fraction,complete\n");
				for (PhaseReplay.Result result : validation)
				{
					out.write(JsonText.csvLine(result.getPhase(), result.getPolicy().getLabel(),
							Double.valueOf(result.getLiveMakespan()), Double.valueOf(result.getPredictedMakespan()),
							Double.valueOf(result.getRelativeError()), Double.valueOf(result.getLowerBound()),
							Double.valueOf(result.getSameNodeFraction()), Boolean.valueOf(result.isComplete())));
				}
			}
			finally
			{
				out.close();
			}
			out = open(new File(directory, "counterfactuals.csv"));
			try
			{
				out.write("phase,policy,predicted_makespan_ms,lower_bound_ms,live_policy_makespan_ms,complete\n");
				for (PhaseReplay.Result result : counterfactuals)
				{
					out.write(JsonText.csvLine(result.getPhase(), result.getPolicy().getLabel(),
							Double.valueOf(result.getPredictedMakespan()), Double.valueOf(result.getLowerBound()),
							Double.valueOf(result.getLiveMakespan()), Boolean.valueOf(result.isComplete())));
				}
			}
			finally
			{
				out.close();
			}
		}
		catch (IOException failure)
		{
			System.err.println("Could not write the replay of " + directory + ": " + failure.getMessage());
		}
	}

	/**
	 * Writes the run's merged lists (recommendations.csv) and, when the run was
	 * prepared for evaluation, its quality (quality.json, quality-targets.csv).
	 * Returns the quality, or null.
	 */
	private static RecommendationQualityEvaluator.Report writeQuality(File directory, String runId)
			throws IOException
	{
		Map<String,List<RecommendationRunResults.Entry>> results = RecommendationRunResults.get(runId);
		if (results == null)
		{
			return null;
		}
		RecommendationQualityEvaluator.Setup setup = RecommendationQualityEvaluator.setupOf(runId);
		Writer out = open(new File(directory, "recommendations.csv"));
		try
		{
			out.write(JsonText.csvLine("target", "target_followee", "rank", "candidate", "candidate_followee",
					"score", "relevant"));
			for (Map.Entry<String,List<RecommendationRunResults.Entry>> row
					: new java.util.TreeMap<String,List<RecommendationRunResults.Entry>>(results).entrySet())
			{
				String followee = setup == null ? null : setup.labels.get(row.getKey());
				int rank = 0;
				for (RecommendationRunResults.Entry entry : row.getValue())
				{
					rank++;
					boolean followeeMode = setup != null
							&& setup.candidateType == RecommendationCandidateType.FOLLOWEE;
					String candidateFollowee = setup == null ? null
							: followeeMode ? entry.getCandidate() : setup.labels.get(entry.getCandidate());
					Object relevant = followee == null || candidateFollowee == null ? ""
							: Integer.valueOf(followee.equals(candidateFollowee)
									&& !entry.getCandidate().equals(row.getKey()) ? 1 : 0);
					out.write(JsonText.csvLine(row.getKey(), followee == null ? "" : followee,
							Integer.valueOf(rank), entry.getCandidate(),
							candidateFollowee == null ? "" : candidateFollowee,
							Double.valueOf(entry.getScore()), relevant));
				}
			}
		}
		finally
		{
			out.close();
		}
		RecommendationQualityEvaluator.Report quality = RecommendationQualityEvaluator.reportFor(runId);
		if (quality == null)
		{
			return null;
		}
		writeText(new File(directory, "quality.json"), JsonText.of(quality.toJson()));
		out = open(new File(directory, "quality-targets.csv"));
		try
		{
			out.write(JsonText.csvLine((Object[])RecommendationQualityEvaluator.Report.targetColumns()));
			for (Object[] line : quality.targetLines())
			{
				out.write(JsonText.csvLine(line));
			}
		}
		finally
		{
			out.close();
		}
		return quality;
	}

	/**
	 * Writes the run's graph (follows, recommendations, adoptions) and its
	 * analysis into the record's graph folder; the layout and figures too when
	 * the run asked for the final graph output. Returns the analysis, or null.
	 */
	private static RunGraphAnalysis writeGraph(File directory, SchedulingRunContext context)
	{
		Map<String,String> properties = context.recorder().properties();
		RunGraph graph = RunGraph.of(RecommendationQualityEvaluator.setupOf(context.getRunId()),
				RecommendationRunResults.get(context.getRunId()), properties.get("datasetName"));
		if (graph == null)
		{
			return null;
		}
		try
		{
			return RunGraphRecord.write(directory, graph, !"false".equals(properties.get("graphGeneration")));
		}
		catch (IOException | RuntimeException failure)
		{
			System.err.println("Could not write the run graph of " + context.getRunId() + ": " + failure);
			return null;
		}
	}

	/**
	 * Writes the TGF report (model card and every target's explanation) into
	 * the record's tgf folder when this run used TGF.
	 */
	private static void writeTemporalGraphFoundation(File directory, SchedulingRunContext context)
	{
		TemporalGraphFoundationModelSupport.Result result = TemporalGraphFoundationRegistry.latest();
		if (result == null || !context.getRunId().equals(result.getRunId()))
		{
			return;
		}
		try
		{
			TgfReport.write(directory, result);
		}
		catch (IOException | RuntimeException failure)
		{
			System.err.println("Could not write the TGF report of " + context.getRunId() + ": " + failure);
		}
	}

	/** Columns of runs-index.csv, one line per run. */
	static final String[] INDEX_COLUMNS = {
		"run_dir", "run_id", "status", "created", "algorithm", "dataset", "nodes", "mode", "policy",
		"scenario", "tdf", "seed", "results_sha256", "makespan_ms", "stage_input_ms", "stage_model_ms",
		"stage_merge_ms", "stage_delivery_ms", "phase_makespan_ms", "phase_lower_bound_ms",
		"phase_efficiency", "phase_imbalance", "phase_utilization", "phase_wasted_fraction", "imbalance",
		"wasted_fraction", "bytes_total", "bytes_collectives", "crashes", "faults", "revocations",
		"host_utilization", "mean_sleep_error_ms", "replay_abs_error", "replay_main_phase_error",
		"replay_best_makespan_ms", "study", "cell", "repetition",
		"quality_k", "quality_targets", "hit_rate", "precision_at_k", "recall_at_k", "ndcg_at_k", "mrr",
		"map_at_k", "ndcg_random", "ndcg_lift", "ndcg_p_value", "top1_accuracy",
		"graph_homophily", "graph_homophily_chance", "graph_homophily_p", "graph_assortativity",
		"graph_community_ami", "graph_exposure_gini", "graph_coverage", "graph_adoption_ei",
		"graph_adoption_ei_chance", "graph_adoption_p", "graph_modularity_before", "graph_modularity_after"
	};

	private static void appendIndex(String directory, SchedulingRunContext context, RunMetrics metrics,
			String resultsHash, RecommendationQualityEvaluator.Report quality, RunGraphAnalysis graph)
			throws IOException
	{
		Map<String,String> properties = context.recorder().properties();
		Map<String,Double> summary = metrics.summary();
		Object[] fields = {
			directory, context.getRunId(), metrics.getStatus(), iso(context.getCreatedAtMillis()),
			properties.get("algorithm"), properties.get("datasetName"), Integer.valueOf(context.getNodeCount()),
			context.getMode().getLabel(), context.getBasePolicy().getLabel(), context.getScenario().getName(),
			Double.valueOf(context.clock().getDilation()), Long.valueOf(context.getFaultSeed()), resultsHash,
			summary.get("makespan_ms"), summary.get("stage_input_ms"), summary.get("stage_model_ms"),
			summary.get("stage_merge_ms"), summary.get("stage_delivery_ms"), summary.get("phase_makespan_ms"),
			summary.get("phase_lower_bound_ms"), summary.get("phase_efficiency"), summary.get("phase_imbalance"),
			summary.get("phase_utilization"), summary.get("phase_wasted_fraction"), summary.get("imbalance"),
			summary.get("wasted_fraction"), summary.get("bytes_total"), summary.get("bytes_collectives"),
			summary.get("crashes"), summary.get("faults"), summary.get("revocations"),
			summary.get("host_utilization"), summary.get("mean_sleep_error_ms"),
			summary.get("replay_abs_error"), summary.get("replay_main_phase_error"),
			summary.get("replay_best_makespan_ms"), properties.get("study"),
			properties.get("cell"), properties.get("repetition"),
			quality == null ? null : Integer.valueOf(quality.setup.cutoff),
			quality == null ? null : Integer.valueOf(quality.evaluated()),
			qualityMean(quality, "hit_rate"), qualityMean(quality, "precision"),
			qualityMean(quality, "recall"), qualityMean(quality, "ndcg"), qualityMean(quality, "mrr"),
			qualityMean(quality, "map"),
			quality == null || quality.metric("ndcg") == null ? null : finite(quality.metric("ndcg").random),
			quality == null || quality.metric("ndcg") == null ? null : finite(quality.metric("ndcg").lift()),
			quality == null || quality.metric("ndcg") == null ? null : finite(quality.metric("ndcg").pValue),
			quality == null ? null : finite(quality.accuracy),
			graph == null ? null : finite(graph.saturated ? graph.weightedHomophily : graph.homophily),
			graph == null ? null : finite(graph.saturated ? graph.weightedHomophilyChance : graph.homophilyChance),
			graph == null ? null : finite(graph.saturated ? graph.weightedHomophilyP : graph.homophilyP),
			graph == null ? null : finite(graph.assortativity.r),
			graph == null ? null : finite(graph.recommended.recovery.ami),
			graph == null ? null : finite(graph.exposureGini), graph == null ? null : finite(graph.coverage),
			graph == null ? null : finite(graph.eiIndex), graph == null ? null : finite(graph.eiChance),
			graph == null ? null : finite(graph.adoptionP),
			graph == null ? null : finite(graph.observed.realModularity),
			graph == null ? null : finite(graph.adopted.realModularity)
		};
		synchronized (INDEX_LOCK)
		{
			File index = new File(runsDirectory(), "runs-index.csv");
			migrateIndexHeader(index);
			boolean header = !index.isFile() || index.length() == 0L;
			Writer out = new BufferedWriter(new OutputStreamWriter(
					new FileOutputStream(index, true), StandardCharsets.UTF_8));
			try
			{
				if (header)
				{
					out.write(JsonText.csvLine((Object[])INDEX_COLUMNS));
				}
				out.write(JsonText.csvLine(fields));
			}
			finally
			{
				out.close();
			}
		}
	}

	private static Double qualityMean(RecommendationQualityEvaluator.Report quality, String metric)
	{
		if (quality == null || quality.metric(metric) == null)
		{
			return null;
		}
		return finite(quality.metric(metric).mean);
	}

	private static Double finite(double value)
	{
		return Double.isNaN(value) || Double.isInfinite(value) ? null : Double.valueOf(value);
	}

	/**
	 * Rewrites an index written with older columns under the current header,
	 * matching columns by name, so new columns are never appended under a
	 * header that does not name them.
	 */
	private static void migrateIndexHeader(File index) throws IOException
	{
		if (!index.isFile() || index.length() == 0L)
		{
			return;
		}
		List<List<String>> rows = JsonText.parseCsv(new String(
				java.nio.file.Files.readAllBytes(index.toPath()), StandardCharsets.UTF_8));
		if (rows.isEmpty() || rows.get(0).equals(java.util.Arrays.asList(INDEX_COLUMNS)))
		{
			return;
		}
		List<Map<String,String>> table = JsonText.readCsv(index);
		File migrated = new File(index.getParentFile(), index.getName() + ".migrating");
		Writer out = open(migrated);
		try
		{
			out.write(JsonText.csvLine((Object[])INDEX_COLUMNS));
			for (Map<String,String> row : table)
			{
				Object[] fields = new Object[INDEX_COLUMNS.length];
				for (int column = 0; column < INDEX_COLUMNS.length; column++)
				{
					String value = row.get(INDEX_COLUMNS[column]);
					fields[column] = value == null ? "" : value;
				}
				out.write(JsonText.csvLine(fields));
			}
		}
		finally
		{
			out.close();
		}
		java.nio.file.Files.move(migrated.toPath(), index.toPath(),
				java.nio.file.StandardCopyOption.REPLACE_EXISTING);
	}

	/** SHA-256 of the run's merged recommendations, or "" when it produced none. */
	static String resultsHash(String runId)
	{
		Map<String,List<RecommendationRunResults.Entry>> results = RecommendationRunResults.get(runId);
		if (results == null)
		{
			return "";
		}
		try
		{
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			for (Map.Entry<String,List<RecommendationRunResults.Entry>> row
					: new java.util.TreeMap<String,List<RecommendationRunResults.Entry>>(results).entrySet())
			{
				digest.update(row.getKey().getBytes(StandardCharsets.UTF_8));
				digest.update((byte)0);
				for (RecommendationRunResults.Entry entry : row.getValue())
				{
					digest.update(entry.getCandidate().getBytes(StandardCharsets.UTF_8));
					digest.update((byte)1);
					long bits = Double.doubleToLongBits(entry.getScore());
					for (int shift = 56; shift >= 0; shift -= 8)
					{
						digest.update((byte)(bits >>> shift));
					}
				}
				digest.update((byte)2);
			}
			return hex(digest.digest());
		}
		catch (Exception unavailable)
		{
			return "";
		}
	}

	/** SHA-256 of a file, cached by path, size and modification time. */
	static String datasetHash(File file)
	{
		if (file == null || !file.isFile())
		{
			return "";
		}
		String key = file.getAbsolutePath() + "|" + file.length() + "|" + file.lastModified();
		synchronized (DATASET_HASHES)
		{
			String cached = DATASET_HASHES.get(key);
			if (cached != null)
			{
				return cached;
			}
		}
		try
		{
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			InputStream in = new FileInputStream(file);
			try
			{
				byte[] buffer = new byte[1 << 16];
				int read;
				while ((read = in.read(buffer)) > 0)
				{
					digest.update(buffer, 0, read);
				}
			}
			finally
			{
				in.close();
			}
			String hash = hex(digest.digest());
			synchronized (DATASET_HASHES)
			{
				DATASET_HASHES.put(key, hash);
			}
			return hash;
		}
		catch (Exception unreadable)
		{
			return "";
		}
	}

	/** Code version, JVM and host, for reproducibility. */
	static LinkedHashMap<String,Object> environment()
	{
		LinkedHashMap<String,Object> environment = new LinkedHashMap<String,Object>();
		environment.put("code", gitDescription());
		environment.put("java", System.getProperty("java.version") + " (" + System.getProperty("java.vendor") + ")");
		environment.put("os", System.getProperty("os.name") + " " + System.getProperty("os.version")
				+ " " + System.getProperty("os.arch"));
		environment.put("cores", Integer.valueOf(Runtime.getRuntime().availableProcessors()));
		environment.put("maxHeapMb", Long.valueOf(Runtime.getRuntime().maxMemory() / (1L << 20)));
		environment.put("cpuTimeMeasured", Boolean.valueOf(NodeEmulator.isCpuTimeMeasured()));
		return environment;
	}

	/** "commit" or "commit (modified)", from git; cached for the JVM. */
	static synchronized String gitDescription()
	{
		if (gitDescription != null)
		{
			return gitDescription;
		}
		String commit = run("git", "rev-parse", "--short=12", "HEAD");
		String changes = run("git", "status", "--porcelain", "--untracked-files=no");
		gitDescription = commit.length() == 0 ? "unknown"
				: commit + (changes.trim().length() > 0 ? " (modified)" : "");
		return gitDescription;
	}

	private static String run(String... command)
	{
		try
		{
			Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
			StringBuilder output = new StringBuilder();
			InputStreamReader reader = new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8);
			char[] buffer = new char[4096];
			int read;
			while ((read = reader.read(buffer)) > 0 && output.length() < 1 << 20)
			{
				output.append(buffer, 0, read);
			}
			process.waitFor();
			return process.exitValue() == 0 ? output.toString().trim() : "";
		}
		catch (Exception unavailable)
		{
			return "";
		}
	}

	private static Writer open(File file) throws IOException
	{
		return new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8));
	}

	private static void writeText(File file, String text) throws IOException
	{
		Writer out = open(file);
		try
		{
			out.write(text);
		}
		finally
		{
			out.close();
		}
	}

	private static String iso(long millis)
	{
		SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US);
		return format.format(new Date(millis));
	}

	private static String hex(byte[] bytes)
	{
		StringBuilder builder = new StringBuilder();
		for (byte value : bytes)
		{
			builder.append(String.format(Locale.US, "%02x", value & 0xFF));
		}
		return builder.toString();
	}

	private static String shortId(String runId)
	{
		String id = runId == null ? "" : runId;
		return id.length() <= 8 ? id : id.substring(id.length() - 8);
	}

	private static String sanitize(String text)
	{
		return (text == null ? "" : text).replaceAll("[^A-Za-z0-9._-]+", "-");
	}
}
