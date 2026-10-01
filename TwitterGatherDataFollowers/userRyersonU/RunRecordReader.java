package TwitterGatherDataFollowers.userRyersonU;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Loads run records back from {@code Results/Runs}: the index of every run,
 * and one run's full record as a {@link RunRecorder} with the facts a replay
 * needs, so a past run can be inspected, validated and replayed under other
 * policies exactly as when it had just finished.
 */
final class RunRecordReader
{
	/** One line of runs-index.csv. */
	static final class Entry
	{
		final Map<String,String> fields;
		final File directory;

		Entry(Map<String,String> fields, File directory)
		{
			this.fields = fields;
			this.directory = directory;
		}

		String get(String column)
		{
			String value = fields.get(column);
			return value == null ? "" : value;
		}

		double number(String column)
		{
			return JsonText.parseDouble(fields.get(column));
		}

		String getRunId()
		{
			return get("run_id");
		}

		boolean exists()
		{
			return directory != null && directory.isDirectory();
		}
	}

	/** A run record loaded from its directory. */
	static final class Record
	{
		final File directory;
		final Object manifest;
		final RunRecorder recorder;
		final PhaseReplay.Run run;
		final List<Map<String,String>> phases;
		final List<Map<String,String>> nodes;
		final List<Map<String,String>> replay;
		final List<Map<String,String>> counterfactuals;
		final Map<String,Double> summary;
		final Map<String,Double> stages;

		Record(File directory, Object manifest, RunRecorder recorder, PhaseReplay.Run run,
				List<Map<String,String>> phases, List<Map<String,String>> nodes,
				List<Map<String,String>> replay, List<Map<String,String>> counterfactuals,
				Map<String,Double> summary, Map<String,Double> stages)
		{
			this.directory = directory;
			this.manifest = manifest;
			this.recorder = recorder;
			this.run = run;
			this.phases = phases;
			this.nodes = nodes;
			this.replay = replay;
			this.counterfactuals = counterfactuals;
			this.summary = summary;
			this.stages = stages;
		}

		String manifest(String path)
		{
			return JsonText.string(manifest, path);
		}

		double summary(String key)
		{
			Double value = summary.get(key);
			return value == null ? Double.NaN : value.doubleValue();
		}

		/** The node count of the run. */
		int nodeCount()
		{
			return (int)Math.max(1, Math.round(JsonText.number(manifest, "scheduling.nodes")));
		}

		/** The cluster of the run as capacity profiles (from its manifest). */
		List<NodeCapacityProfile> profiles()
		{
			ArrayList<NodeCapacityProfile> profiles = new ArrayList<NodeCapacityProfile>();
			Object cluster = JsonText.path(manifest, "cluster");
			if (!(cluster instanceof List))
			{
				return profiles;
			}
			for (Object node : (List<?>)cluster)
			{
				String role = JsonText.string(node, "role");
				NodeCapacityProfile.Role parsed = NodeCapacityProfile.Role.NORMAL;
				for (NodeCapacityProfile.Role candidate : NodeCapacityProfile.Role.values())
				{
					if (candidate.getLabel().equalsIgnoreCase(role))
					{
						parsed = candidate;
					}
				}
				double cpu = JsonText.number(node, "cpuMultiplier");
				double speed = JsonText.number(node, "speed");
				double slowdown = cpu > 0.0 && speed > 0.0 ? Math.max(1.0, cpu / speed) : 1.0;
				int faults = (int)Math.round(orZero(JsonText.number(node, "transientFaultPercent")));
				profiles.add(new NodeCapacityProfile(
						(int)Math.round(JsonText.number(node, "node")),
						parsed,
						cpu,
						(int)Math.round(JsonText.number(node, "memoryMb")),
						(int)Math.round(JsonText.number(node, "lanes")),
						(int)Math.round(orZero(JsonText.number(node, "latencyMs"))),
						(int)Math.round(JsonText.number(node, "queueCapacity")),
						slowdown > 1.0001,
						slowdown,
						faults > 0,
						faults,
						orZero(JsonText.number(node, "bandwidthMbps")),
						orZero(JsonText.number(node, "mtbfSeconds")),
						orZero(JsonText.number(node, "mttrSeconds"))));
			}
			return profiles;
		}
	}

	private RunRecordReader()
	{
	}

	private static double orZero(double value)
	{
		return Double.isNaN(value) ? 0.0 : value;
	}

	/** Every run of runs-index.csv, newest first (unreadable index: none). */
	static List<Entry> index()
	{
		File directory = RunRecordWriter.runsDirectory();
		ArrayList<Entry> entries = new ArrayList<Entry>();
		try
		{
			for (Map<String,String> row : JsonText.readCsv(new File(directory, "runs-index.csv")))
			{
				String name = row.get("run_dir");
				entries.add(new Entry(row, name == null || name.length() == 0 ? null : new File(directory, name)));
			}
		}
		catch (IOException unreadable)
		{
			System.err.println("Could not read the runs index: " + unreadable.getMessage());
		}
		Collections.reverse(entries);
		return entries;
	}

	/** Loads one run's record from its directory. */
	static Record load(File directory) throws IOException
	{
		Object manifest = JsonText.read(new File(directory, "manifest.json"));
		String runId = JsonText.string(manifest, "runId");
		RunRecorder recorder = new RunRecorder(runId);
		Object properties = JsonText.path(manifest, "properties");
		if (properties instanceof Map)
		{
			for (Map.Entry<?,?> property : ((Map<?,?>)properties).entrySet())
			{
				recorder.property(String.valueOf(property.getKey()),
						property.getValue() == null ? "" : String.valueOf(property.getValue()));
			}
		}
		for (Map<String,String> row : JsonText.readCsv(new File(directory, "units.csv")))
		{
			recorder.execution(new RunRecorder.UnitExecution(
					row.get("phase"),
					JsonText.parseInt(row.get("unit_id"), 0),
					row.get("key"),
					JsonText.parseInt(row.get("execution"), 1),
					JsonText.parseInt(row.get("node"), 0),
					JsonText.parseInt(row.get("lane"), 0),
					JsonText.parseInt(row.get("home_node"), 0),
					row.get("source"),
					JsonText.parseDouble(row.get("estimated_cost")),
					JsonText.parseDouble(row.get("assigned_at_ms")),
					JsonText.parseDouble(row.get("ready_at_ms")),
					JsonText.parseDouble(row.get("start_ms")),
					JsonText.parseDouble(row.get("end_ms")),
					nanos(row.get("host_cpu_ms")),
					nanos(row.get("worker_cpu_ms")),
					nanos(row.get("host_wall_ms")),
					JsonText.parseLong(row.get("moved_bytes"), 0L),
					row.get("outcome")));
		}
		LinkedHashMap<String,List<WorkUnitSpec>> units = new LinkedHashMap<String,List<WorkUnitSpec>>();
		for (Map<String,String> row : JsonText.readCsv(new File(directory, "phase-units.csv")))
		{
			String phase = row.get("phase");
			List<WorkUnitSpec> list = units.get(phase);
			if (list == null)
			{
				list = new ArrayList<WorkUnitSpec>();
				units.put(phase, list);
			}
			double deadline = JsonText.parseDouble(row.get("deadline"));
			list.add(new WorkUnitSpec(
					JsonText.parseInt(row.get("unit_id"), list.size() + 1),
					row.get("key"),
					JsonText.parseDouble(row.get("estimated_cost")),
					JsonText.parseInt(row.get("memory_mb"), 0),
					JsonText.parseInt(row.get("priority"), 0),
					Double.isNaN(deadline) ? Double.POSITIVE_INFINITY : deadline,
					JsonText.parseInt(row.get("home_node"), 0),
					allowed(row.get("allowed_nodes")),
					JsonText.parseInt(row.get("max_failures"), 0),
					JsonText.parseLong(row.get("input_bytes"), 0L),
					JsonText.parseLong(row.get("output_bytes"), 0L),
					orZero(JsonText.parseDouble(row.get("fixed")))));
		}
		LinkedHashMap<String,List<NodeSpec>> specs = new LinkedHashMap<String,List<NodeSpec>>();
		for (Map<String,String> row : JsonText.readCsv(new File(directory, "phase-nodes.csv")))
		{
			String phase = row.get("phase");
			List<NodeSpec> list = specs.get(phase);
			if (list == null)
			{
				list = new ArrayList<NodeSpec>();
				specs.put(phase, list);
			}
			double speed = JsonText.parseDouble(row.get("speed"));
			list.add(new NodeSpec(
					JsonText.parseInt(row.get("node"), list.size() + 1),
					row.get("role"),
					speed,
					1.0,
					1.0,
					speed,
					JsonText.parseInt(row.get("lanes"), 1),
					JsonText.parseInt(row.get("memory_mb"), 1 << 20),
					JsonText.parseInt(row.get("queue_capacity"), 1),
					JsonText.parseInt(row.get("latency_ms"), 0),
					orZero(JsonText.parseDouble(row.get("bandwidth_bytes_per_ms"))),
					orZero(JsonText.parseDouble(row.get("fault_probability"))),
					orZero(JsonText.parseDouble(row.get("mtbf_ms"))),
					orZero(JsonText.parseDouble(row.get("mttr_ms")))));
		}
		List<Map<String,String>> phases = JsonText.readCsv(new File(directory, "phases.csv"));
		for (Map<String,String> row : phases)
		{
			String key = row.get("phase");
			List<WorkUnitSpec> phaseUnits = units.containsKey(key) ? units.get(key)
					: Collections.<WorkUnitSpec>emptyList();
			List<NodeSpec> phaseNodes = specs.containsKey(key) ? specs.get(key) : Collections.<NodeSpec>emptyList();
			RunRecorder.Phase phase = new RunRecorder.Phase(key, row.get("label"), row.get("policy"),
					row.get("execution_mode"), JsonText.parseInt(row.get("units"), phaseUnits.size()),
					JsonText.parseDouble(row.get("opened_at_ms")),
					JsonText.parseDouble(row.get("estimated_lower_bound")),
					phaseUnits, phaseNodes);
			recorder.phaseOpened(phase);
			double completed = JsonText.parseDouble(row.get("completed_at_ms"));
			String status = row.get("status");
			if (!Double.isNaN(completed) || (status != null && !"open".equals(status)))
			{
				recorder.phaseFinished(key, completed, status);
			}
		}
		for (Map<String,String> row : JsonText.readCsv(new File(directory, "stages.csv")))
		{
			recorder.stage(new RunRecorder.Stage(JsonText.parseInt(row.get("node"), 0), row.get("stage"),
					JsonText.parseDouble(row.get("start_ms")), JsonText.parseDouble(row.get("end_ms"))));
		}
		for (Map<String,String> row : JsonText.readCsv(new File(directory, "collectives.csv")))
		{
			recorder.collective(new RunRecorder.Collective(row.get("step"), JsonText.parseInt(row.get("nodes"), 0),
					JsonText.parseLong(row.get("upload_bytes"), 0L), JsonText.parseLong(row.get("download_bytes"), 0L),
					JsonText.parseDouble(row.get("first_arrival_ms")), JsonText.parseDouble(row.get("reduce_start_ms")),
					JsonText.parseDouble(row.get("reduce_end_ms")), nanos(row.get("reduce_cpu_ms")),
					JsonText.parseDouble(row.get("last_delivery_ms"))));
		}
		for (Map<String,String> row : JsonText.readCsv(new File(directory, "events.csv")))
		{
			recorder.event(JsonText.parseDouble(row.get("at_ms")), row.get("kind"),
					JsonText.parseInt(row.get("node"), 0), row.get("detail"));
		}
		for (Map<String,String> row : JsonText.readCsv(new File(directory, "milestones.csv")))
		{
			recorder.milestone(row.get("milestone"), JsonText.parseDouble(row.get("at_ms")));
		}
		for (Map<String,String> row : JsonText.readCsv(new File(directory, "controls.csv")))
		{
			Scenario.ActionKind kind;
			try
			{
				kind = Scenario.ActionKind.valueOf(row.get("action"));
			}
			catch (RuntimeException unknown)
			{
				continue;
			}
			String policy = row.get("policy");
			recorder.control(new RunRecorder.Control(JsonText.parseDouble(row.get("at_ms")), kind,
					JsonText.parseInt(row.get("node"), 0), JsonText.parseInt(row.get("target"), 0),
					JsonText.parseInt(row.get("count"), 0), JsonText.parseDouble(row.get("value")),
					policy == null || policy.length() == 0 ? null : PhaseReplay.policyNamed(policy)));
		}
		for (Map<String,String> row : JsonText.readCsv(new File(directory, "phase-joins.csv")))
		{
			recorder.phaseJoined(row.get("phase"), JsonText.parseInt(row.get("node"), 0),
					JsonText.parseDouble(row.get("joined_at_ms")));
		}
		String status = JsonText.string(manifest, "status");
		recorder.status(status.length() == 0 ? "completed" : status);

		String mode = JsonText.string(manifest, "scheduling.mode");
		Object emulatingValue = JsonText.path(manifest, "scheduling.emulating");
		boolean emulating = emulatingValue instanceof Boolean ? ((Boolean)emulatingValue).booleanValue()
				: !mode.toLowerCase(Locale.US).startsWith("hardware");
		Object faultValue = JsonText.path(manifest, "scheduling.faultInjection");
		boolean faults = faultValue instanceof Boolean ? ((Boolean)faultValue).booleanValue()
				: mode.toLowerCase(Locale.US).contains("fault");
		double detection = JsonText.number(manifest, "scheduling.detectionTimeoutMs");
		PhaseReplay.Run run = new PhaseReplay.Run(runId, emulating,
				JsonText.number(manifest, "scheduling.timeDilation"), faults,
				(long)orZero(JsonText.number(manifest, "scheduling.faultSeed")),
				Double.isNaN(detection) ? ClusterSettings.defaults().getDetectionTimeoutMs() : detection, recorder);

		Object metrics = null;
		File metricsFile = new File(directory, "metrics.json");
		if (metricsFile.isFile())
		{
			metrics = JsonText.read(metricsFile);
		}
		return new Record(directory, manifest, recorder, run, phases,
				JsonText.readCsv(new File(directory, "nodes.csv")),
				JsonText.readCsv(new File(directory, "replay.csv")),
				JsonText.readCsv(new File(directory, "counterfactuals.csv")),
				numbers(JsonText.path(metrics != null ? metrics : manifest, "summary")),
				numbers(JsonText.path(metrics, "stagesMs")));
	}

	private static long nanos(String milliseconds)
	{
		double value = JsonText.parseDouble(milliseconds);
		return Double.isNaN(value) ? 0L : Math.round(value * 1.0e6);
	}

	private static int[] allowed(String text)
	{
		if (text == null || text.trim().length() == 0)
		{
			return null;
		}
		String[] parts = text.trim().split("\\s+");
		int[] nodes = new int[parts.length];
		for (int index = 0; index < parts.length; index++)
		{
			nodes[index] = JsonText.parseInt(parts[index], 0);
		}
		return nodes;
	}

	private static Map<String,Double> numbers(Object map)
	{
		LinkedHashMap<String,Double> values = new LinkedHashMap<String,Double>();
		if (map instanceof Map)
		{
			for (Map.Entry<?,?> entry : ((Map<?,?>)map).entrySet())
			{
				Object value = entry.getValue();
				values.put(String.valueOf(entry.getKey()),
						Double.valueOf(value instanceof Double ? ((Double)value).doubleValue() : Double.NaN));
			}
		}
		return values;
	}
}
