package TwitterGatherDataFollowers.userRyersonU;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * The outcomes of a study and their analysis: per-cell summaries with 95%
 * confidence intervals, paired comparisons of every policy with the baseline
 * (Holm-adjusted), the exactness check, and the simulator-validation summary.
 * Written as CSV, JSON and a Markdown report.
 */
final class StudyResult
{
	/** Measures analyzed for every cell, with their display names. */
	static final String[][] METRICS = {
		{"makespan_ms", "Makespan (ms)"},
		{"phase_makespan_ms", "Main phase makespan (ms)"},
		{"phase_efficiency", "Phase efficiency (LB / makespan)"},
		{"phase_imbalance", "Phase imbalance (max / mean busy)"},
		{"phase_utilization", "Phase utilization"},
		{"wasted_fraction", "Wasted work fraction"},
		{"stage_input_ms", "Input stage (ms)"},
		{"stage_model_ms", "Model stage (ms)"},
		{"bytes_total", "Network bytes"},
		{"crashes", "Crashes"},
		{"replay_abs_error", "Simulator replay |error|"}
	};

	/** One executed run. */
	static final class RunOutcome
	{
		final StudyDesign.RunSlot slot;
		final String runId;
		final String status;
		final Map<String,Double> summary;
		final String resultsHash;
		final String recordDirectory;
		final String error;
		final double wallSeconds;

		RunOutcome(StudyDesign.RunSlot slot, String runId, String status, Map<String,Double> summary,
				String resultsHash, String recordDirectory, String error, double wallSeconds)
		{
			this.slot = slot;
			this.runId = runId == null ? "" : runId;
			this.status = status == null ? "" : status;
			this.summary = summary == null ? Collections.<String,Double>emptyMap() : summary;
			this.resultsHash = resultsHash == null ? "" : resultsHash;
			this.recordDirectory = recordDirectory == null ? "" : recordDirectory;
			this.error = error == null ? "" : error;
			this.wallSeconds = wallSeconds;
		}

		boolean isCompleted()
		{
			return "completed".equals(status);
		}

		double metric(String key)
		{
			Double value = summary.get(key);
			return value == null ? Double.NaN : value.doubleValue();
		}
	}

	/** A cell's summary of one metric. */
	static final class CellSummary
	{
		final StudyDesign.Cell cell;
		final String metric;
		final StudyStatistics.Summary summary;

		CellSummary(StudyDesign.Cell cell, String metric, StudyStatistics.Summary summary)
		{
			this.cell = cell;
			this.metric = metric;
			this.summary = summary;
		}
	}

	/** A policy compared with the baseline in one group, for one metric. */
	static final class PolicyComparison
	{
		final StudyDesign.Cell cell;
		final StudyDesign.Cell baselineCell;
		final String metric;
		final StudyStatistics.Comparison comparison;

		PolicyComparison(StudyDesign.Cell cell, StudyDesign.Cell baselineCell, String metric,
				StudyStatistics.Comparison comparison)
		{
			this.cell = cell;
			this.baselineCell = baselineCell;
			this.metric = metric;
			this.comparison = comparison;
		}
	}

	private final StudyDesign design;
	private final ArrayList<RunOutcome> outcomes = new ArrayList<RunOutcome>();
	private final long startedAt = System.currentTimeMillis();
	private File loadedFrom;

	StudyResult(StudyDesign design)
	{
		this.design = design;
	}

	/**
	 * A study written earlier, loaded back from its directory (its
	 * self-contained design and runs.csv) for analysis.
	 */
	static StudyResult load(File directory, int heapMb) throws IOException
	{
		StudyDesign design = StudyDesign.load(new File(directory, "design.study"), heapMb);
		StudyResult result = new StudyResult(design);
		result.loadedFrom = directory;
		List<StudyDesign.Cell> cells = design.cells();
		java.util.HashSet<String> fixed = new java.util.HashSet<String>(java.util.Arrays.asList("cell", "algorithm",
				"cluster", "policy", "scenario", "repetition", "warmup", "seed", "run_id", "status", "results_sha256",
				"wall_seconds", "record_dir", "error"));
		for (Map<String,String> row : JsonText.readCsv(new File(directory, "runs.csv")))
		{
			int index = JsonText.parseInt(row.get("cell"), 0);
			if (index < 1 || index > cells.size())
			{
				continue;
			}
			StudyDesign.RunSlot slot = new StudyDesign.RunSlot(cells.get(index - 1),
					JsonText.parseInt(row.get("repetition"), 0), Boolean.parseBoolean(row.get("warmup")),
					JsonText.parseLong(row.get("seed"), 0L));
			LinkedHashMap<String,Double> summary = new LinkedHashMap<String,Double>();
			for (Map.Entry<String,String> column : row.entrySet())
			{
				if (!fixed.contains(column.getKey()))
				{
					summary.put(column.getKey(), Double.valueOf(JsonText.parseDouble(column.getValue())));
				}
			}
			result.add(new RunOutcome(slot, row.get("run_id"), row.get("status"), summary, row.get("results_sha256"),
					row.get("record_dir"), row.get("error"), JsonText.parseDouble(row.get("wall_seconds"))));
		}
		return result;
	}

	/** Every study under Results/Studies, newest first. */
	static List<File> studyDirectories()
	{
		ArrayList<File> directories = new ArrayList<File>();
		File[] files = new File(System.getProperty("dsmp.results.dir", "Results"), "Studies").listFiles();
		if (files != null)
		{
			for (File file : files)
			{
				if (new File(file, "design.study").isFile() && new File(file, "runs.csv").isFile())
				{
					directories.add(file);
				}
			}
		}
		Collections.sort(directories);
		Collections.reverse(directories);
		return directories;
	}

	StudyDesign getDesign()
	{
		return design;
	}

	synchronized void add(RunOutcome outcome)
	{
		outcomes.add(outcome);
	}

	synchronized List<RunOutcome> outcomes()
	{
		return Collections.unmodifiableList(new ArrayList<RunOutcome>(outcomes));
	}

	/** Analyzed runs (not warm-up) of a cell, in repetition order. */
	List<RunOutcome> runsOf(StudyDesign.Cell cell)
	{
		TreeMap<Integer,RunOutcome> byRepetition = new TreeMap<Integer,RunOutcome>();
		for (RunOutcome outcome : outcomes())
		{
			if (!outcome.slot.warmup && outcome.slot.cell.index == cell.index)
			{
				byRepetition.put(Integer.valueOf(outcome.slot.repetition), outcome);
			}
		}
		return new ArrayList<RunOutcome>(byRepetition.values());
	}

	/** Metric values of a cell by repetition 1..R (NaN where the run failed or is missing). */
	double[] values(StudyDesign.Cell cell, String metric)
	{
		double[] values = new double[design.getRepetitions()];
		java.util.Arrays.fill(values, Double.NaN);
		for (RunOutcome outcome : runsOf(cell))
		{
			if (outcome.isCompleted() && outcome.slot.repetition >= 1
					&& outcome.slot.repetition <= values.length)
			{
				values[outcome.slot.repetition - 1] = outcome.metric(metric);
			}
		}
		return values;
	}

	List<CellSummary> summaries(String metric)
	{
		ArrayList<CellSummary> summaries = new ArrayList<CellSummary>();
		for (StudyDesign.Cell cell : design.cells())
		{
			summaries.add(new CellSummary(cell, metric,
					StudyStatistics.summarize(values(cell, metric), 0.95)));
		}
		return summaries;
	}

	/**
	 * Every non-baseline policy compared with the baseline in its group
	 * (algorithm, cluster, scenario), paired by repetition; the p-values of one
	 * metric form one Holm family.
	 */
	List<PolicyComparison> comparisons(String metric)
	{
		ArrayList<PolicyComparison> comparisons = new ArrayList<PolicyComparison>();
		List<StudyDesign.Cell> cells = design.cells();
		for (StudyDesign.Cell cell : cells)
		{
			if (cell.policy == design.getBaseline())
			{
				continue;
			}
			StudyDesign.Cell baseline = null;
			for (StudyDesign.Cell candidate : cells)
			{
				if (candidate.policy == design.getBaseline() && candidate.groupKey().equals(cell.groupKey()))
				{
					baseline = candidate;
				}
			}
			if (baseline == null)
			{
				continue;
			}
			comparisons.add(new PolicyComparison(cell, baseline, metric, StudyStatistics.paired(
					values(baseline, metric), values(cell, metric), 0.95,
					WorkDispatcher.mix(design.getSeed() ^ cell.index))));
		}
		ArrayList<StudyStatistics.Comparison> family = new ArrayList<StudyStatistics.Comparison>();
		for (PolicyComparison comparison : comparisons)
		{
			family.add(comparison.comparison);
		}
		StudyStatistics.holm(family);
		return comparisons;
	}

	/**
	 * Exactness check: every completed run of one algorithm must have produced
	 * the same recommendations. Returns algorithm name to the distinct results
	 * hashes it produced (one means exact).
	 */
	Map<String,LinkedHashSet<String>> resultHashesByAlgorithm()
	{
		LinkedHashMap<String,LinkedHashSet<String>> hashes = new LinkedHashMap<String,LinkedHashSet<String>>();
		for (RunOutcome outcome : outcomes())
		{
			if (!outcome.isCompleted() || outcome.resultsHash.length() == 0)
			{
				continue;
			}
			String algorithm = outcome.slot.cell.algorithmName();
			LinkedHashSet<String> set = hashes.get(algorithm);
			if (set == null)
			{
				set = new LinkedHashSet<String>();
				hashes.put(algorithm, set);
			}
			set.add(outcome.resultsHash);
		}
		return hashes;
	}

	boolean isExact()
	{
		for (LinkedHashSet<String> set : resultHashesByAlgorithm().values())
		{
			if (set.size() > 1)
			{
				return false;
			}
		}
		return true;
	}

	int failedRuns()
	{
		int failed = 0;
		for (RunOutcome outcome : outcomes())
		{
			if (!outcome.isCompleted())
			{
				failed++;
			}
		}
		return failed;
	}

	// ------------------------------------------------------------ writing

	/** The study's output directory under Results/Studies. */
	File directory()
	{
		if (loadedFrom != null)
		{
			return loadedFrom;
		}
		String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date(startedAt));
		String name = design.getName().replaceAll("[^A-Za-z0-9._-]+", "-").replaceAll("^-|-$", "");
		return new File(new File(System.getProperty("dsmp.results.dir", "Results"), "Studies"),
				stamp + "_" + (name.length() == 0 ? "study" : name));
	}

	/** Writes every output file; returns the directory. */
	File write() throws IOException
	{
		File directory = directory();
		if (!directory.isDirectory() && !directory.mkdirs())
		{
			throw new IOException("Cannot create " + directory);
		}
		writeText(new File(directory, "design.study"), design.toText(true));
		writeRuns(new File(directory, "runs.csv"));
		writeSummary(new File(directory, "summary.csv"));
		writeComparisons(new File(directory, "comparisons.csv"));
		writeText(new File(directory, "report.md"), report());
		writeText(new File(directory, "study.json"), JsonText.of(json()));
		for (ClusterConfig cluster : design.getClusters())
		{
			writeText(new File(directory, StudyDesign.clusterFileName(cluster)), cluster.toText());
		}
		for (Scenario scenario : design.getScenarios())
		{
			if (!scenario.isEmpty())
			{
				String text = scenario.toText();
				if (!text.toLowerCase(Locale.US).startsWith("name:"))
				{
					text = "name: " + scenario.getName() + "\n" + text;
				}
				writeText(new File(directory, StudyDesign.scenarioFileName(scenario)), text);
			}
		}
		return directory;
	}

	private void writeRuns(File file) throws IOException
	{
		Writer out = open(file);
		try
		{
			ArrayList<String> metricKeys = new ArrayList<String>();
			for (RunOutcome outcome : outcomes())
			{
				for (String key : outcome.summary.keySet())
				{
					if (!metricKeys.contains(key))
					{
						metricKeys.add(key);
					}
				}
			}
			ArrayList<Object> header = new ArrayList<Object>();
			Collections.addAll(header, "cell", "algorithm", "cluster", "policy", "scenario", "repetition",
					"warmup", "seed", "run_id", "status", "results_sha256", "wall_seconds", "record_dir");
			header.addAll(metricKeys);
			header.add("error");
			out.write(JsonText.csvLine(header.toArray()));
			for (RunOutcome outcome : outcomes())
			{
				ArrayList<Object> row = new ArrayList<Object>();
				StudyDesign.Cell cell = outcome.slot.cell;
				Collections.addAll(row, Integer.valueOf(cell.index), cell.algorithmName(), cell.cluster.getName(),
						cell.policy.getLabel(), cell.scenario.getName(), Integer.valueOf(outcome.slot.repetition),
						Boolean.valueOf(outcome.slot.warmup), Long.valueOf(outcome.slot.seed), outcome.runId,
						outcome.status, outcome.resultsHash, Double.valueOf(outcome.wallSeconds),
						outcome.recordDirectory);
				for (String key : metricKeys)
				{
					row.add(Double.valueOf(outcome.metric(key)));
				}
				row.add(outcome.error);
				out.write(JsonText.csvLine(row.toArray()));
			}
		}
		finally
		{
			out.close();
		}
	}

	private void writeSummary(File file) throws IOException
	{
		Writer out = open(file);
		try
		{
			out.write(JsonText.csvLine("cell", "algorithm", "cluster", "policy", "scenario", "metric", "n",
					"mean", "sd", "ci95_low", "ci95_high", "median", "q1", "q3", "min", "max", "cv"));
			for (String[] metric : METRICS)
			{
				for (CellSummary summary : summaries(metric[0]))
				{
					StudyStatistics.Summary s = summary.summary;
					StudyDesign.Cell cell = summary.cell;
					out.write(JsonText.csvLine(Integer.valueOf(cell.index), cell.algorithmName(),
							cell.cluster.getName(), cell.policy.getLabel(), cell.scenario.getName(), metric[0],
							Integer.valueOf(s.n), Double.valueOf(s.mean), Double.valueOf(s.sd),
							Double.valueOf(s.ciLow), Double.valueOf(s.ciHigh), Double.valueOf(s.median),
							Double.valueOf(s.q1), Double.valueOf(s.q3), Double.valueOf(s.min),
							Double.valueOf(s.max), Double.valueOf(s.cv())));
				}
			}
		}
		finally
		{
			out.close();
		}
	}

	private void writeComparisons(File file) throws IOException
	{
		Writer out = open(file);
		try
		{
			out.write(JsonText.csvLine("algorithm", "cluster", "scenario", "policy", "baseline", "metric", "pairs",
					"baseline_mean", "policy_mean", "mean_difference", "ci95_low", "ci95_high",
					"relative_change", "effect_size_dz", "p_value", "p_holm"));
			for (String[] metric : METRICS)
			{
				for (PolicyComparison comparison : comparisons(metric[0]))
				{
					StudyStatistics.Comparison c = comparison.comparison;
					StudyDesign.Cell cell = comparison.cell;
					out.write(JsonText.csvLine(cell.algorithmName(), cell.cluster.getName(), cell.scenario.getName(),
							cell.policy.getLabel(), comparison.baselineCell.policy.getLabel(), metric[0],
							Integer.valueOf(c.n), Double.valueOf(c.baselineMean), Double.valueOf(c.treatmentMean),
							Double.valueOf(c.meanDifference), Double.valueOf(c.ciLow), Double.valueOf(c.ciHigh),
							Double.valueOf(c.relativeChange), Double.valueOf(c.effectSize), Double.valueOf(c.pValue),
							Double.valueOf(c.adjustedPValue)));
				}
			}
		}
		finally
		{
			out.close();
		}
	}

	private LinkedHashMap<String,Object> json()
	{
		LinkedHashMap<String,Object> json = new LinkedHashMap<String,Object>();
		json.put("format", "dsmp-node-lab-study/1");
		json.put("name", design.getName());
		json.put("dataset", design.getDataset() == null ? "" : design.getDataset().getAbsolutePath());
		json.put("datasetSha256", design.getDataset() == null ? "" : RunRecordWriter.datasetHash(design.getDataset()));
		json.put("design", design.toText());
		json.put("runs", Integer.valueOf(outcomes().size()));
		json.put("failedRuns", Integer.valueOf(failedRuns()));
		json.put("exact", Boolean.valueOf(isExact()));
		LinkedHashMap<String,Object> hashes = new LinkedHashMap<String,Object>();
		for (Map.Entry<String,LinkedHashSet<String>> entry : resultHashesByAlgorithm().entrySet())
		{
			hashes.put(entry.getKey(), new ArrayList<Object>(entry.getValue()));
		}
		json.put("resultsSha256ByAlgorithm", hashes);
		json.put("environment", RunRecordWriter.environment());
		return json;
	}

	/** The Markdown report: design, validity checks, results, comparisons. */
	String report()
	{
		StringBuilder md = new StringBuilder();
		md.append("# ").append(design.getName()).append("\n\n");
		md.append("Generated ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new Date()))
				.append(" by the DSMP Node Lab.\n\n");
		md.append("## Design\n\n");
		md.append("- Dataset: `").append(design.getDataset() == null ? "(simulator's)" : design.getDataset().getName())
				.append("`\n");
		md.append("- Mode: ").append(design.getMode().getLabel()).append("\n");
		md.append("- Algorithms: ");
		for (int index = 0; index < design.getAlgorithms().size(); index++)
		{
			md.append(index == 0 ? "" : ", ")
					.append(RecommendationModeSupport.algorithmName(design.getAlgorithms().get(index).intValue()));
		}
		md.append(" (").append(design.getTargets()).append(" target(s), k = ").append(design.getK()).append(")\n");
		md.append("- Clusters: ");
		for (int index = 0; index < design.getClusters().size(); index++)
		{
			ClusterConfig cluster = design.getClusters().get(index);
			md.append(index == 0 ? "" : "; ").append(cluster.getName()).append(" (").append(cluster.describe())
					.append(")");
		}
		md.append("\n- Policies: ");
		for (int index = 0; index < design.getPolicies().size(); index++)
		{
			md.append(index == 0 ? "" : ", ").append(design.getPolicies().get(index).getLabel());
		}
		md.append(" (baseline: ").append(design.getBaseline().getLabel()).append(")\n");
		md.append("- Scenarios: ");
		for (int index = 0; index < design.getScenarios().size(); index++)
		{
			md.append(index == 0 ? "" : ", ").append(design.getScenarios().get(index).getName());
		}
		md.append("\n- ").append(design.cells().size()).append(" cells x ").append(design.getRepetitions())
				.append(" repetitions (+").append(design.getWarmup()).append(" warm-up) = ")
				.append(design.runCount()).append(" runs; common random numbers per repetition, randomized")
				.append(" order within each repetition, study seed ").append(design.getSeed()).append(".\n\n");

		md.append("## Validity\n\n");
		md.append("- Runs completed: ").append(outcomes().size() - failedRuns()).append(" of ")
				.append(outcomes().size()).append(".\n");
		md.append("- Exactness: ");
		if (isExact())
		{
			md.append("every run of each algorithm produced bit-identical recommendations");
			md.append(" (scheduling changed only time).\n");
		}
		else
		{
			md.append("**VIOLATED** — some runs of one algorithm produced different recommendations:");
			for (Map.Entry<String,LinkedHashSet<String>> entry : resultHashesByAlgorithm().entrySet())
			{
				md.append(" ").append(entry.getKey()).append(": ").append(entry.getValue().size())
						.append(" distinct result(s);");
			}
			md.append("\n");
		}
		StudyStatistics.Summary replay = StudyStatistics.summarize(allValues("replay_abs_error"), 0.95);
		StudyStatistics.Summary sleep = StudyStatistics.summarize(allValues("mean_sleep_error_ms"), 0.95);
		StudyStatistics.Summary host = StudyStatistics.summarize(allValues("host_utilization"), 0.95);
		md.append(String.format(Locale.US,
				"- Simulator fidelity: replaying each run's phases in the discrete-event simulator predicts"
				+ " the live phase makespans with a mean absolute error of %.1f%% (n=%d runs).%n",
				replay.mean * 100.0, replay.n));
		md.append(String.format(Locale.US,
				"- Emulation precision: mean sleep error %.3f ms per emulated pause; mean host CPU"
				+ " utilization %.0f%%%s.%n%n", sleep.mean, host.mean * 100.0,
				host.max > 0.85 ? " (some runs saturated the host: raise the time dilation factor)" : ""));

		for (String[] metric : METRICS)
		{
			if (metric[0].equals("replay_abs_error") || metric[0].equals("crashes"))
			{
				continue;
			}
			md.append("## ").append(metric[1]).append("\n\n");
			md.append("| Algorithm | Cluster | Scenario | Policy | n | Mean | 95% CI | Median |\n");
			md.append("|---|---|---|---|---:|---:|---|---:|\n");
			for (CellSummary summary : summaries(metric[0]))
			{
				StudyStatistics.Summary s = summary.summary;
				StudyDesign.Cell cell = summary.cell;
				md.append("| ").append(cell.algorithmName()).append(" | ").append(cell.cluster.getName())
						.append(" | ").append(cell.scenario.getName()).append(" | ").append(cell.policy.getLabel())
						.append(" | ").append(s.n).append(" | ").append(format(s.mean)).append(" | [")
						.append(format(s.ciLow)).append(", ").append(format(s.ciHigh)).append("] | ")
						.append(format(s.median)).append(" |\n");
			}
			md.append("\n");
			List<PolicyComparison> comparisons = comparisons(metric[0]);
			if (!comparisons.isEmpty())
			{
				md.append("Paired with ").append(design.getBaseline().getLabel())
						.append(" (same seeds per repetition; exact sign-flip test, Holm-adjusted):\n\n");
				md.append("| Algorithm | Cluster | Scenario | Policy | Pairs | Change | Diff 95% CI | d_z | p (Holm) |\n");
				md.append("|---|---|---|---|---:|---:|---|---:|---:|\n");
				for (PolicyComparison comparison : comparisons)
				{
					StudyStatistics.Comparison c = comparison.comparison;
					StudyDesign.Cell cell = comparison.cell;
					md.append("| ").append(cell.algorithmName()).append(" | ").append(cell.cluster.getName())
							.append(" | ").append(cell.scenario.getName()).append(" | ")
							.append(cell.policy.getLabel()).append(" | ").append(c.n).append(" | ")
							.append(Double.isNaN(c.relativeChange) ? "—"
									: String.format(Locale.US, "%+.1f%%", c.relativeChange * 100.0))
							.append(" | [").append(format(c.ciLow)).append(", ").append(format(c.ciHigh))
							.append("] | ").append(format(c.effectSize)).append(" | ")
							.append(format(c.adjustedPValue)).append(c.isSignificant(0.05) ? " *" : "")
							.append(" |\n");
				}
				md.append("\n");
			}
		}
		md.append("## Files\n\n");
		md.append("`runs.csv` (one line per run, every measure), `summary.csv` (per cell and measure),"
				+ " `comparisons.csv` (paired comparisons), `study.json`, `design.study` (re-runnable),"
				+ " the cluster files, and each run's full record under `Results/Runs` (see the run_dir column).\n");
		return md.toString();
	}

	private double[] allValues(String metric)
	{
		ArrayList<Double> values = new ArrayList<Double>();
		for (RunOutcome outcome : outcomes())
		{
			if (outcome.isCompleted() && !outcome.slot.warmup)
			{
				values.add(Double.valueOf(outcome.metric(metric)));
			}
		}
		double[] array = new double[values.size()];
		for (int index = 0; index < array.length; index++)
		{
			array[index] = values.get(index).doubleValue();
		}
		return array;
	}

	static String format(double value)
	{
		if (Double.isNaN(value) || Double.isInfinite(value))
		{
			return "—";
		}
		double magnitude = Math.abs(value);
		if (magnitude >= 1000.0)
		{
			return String.format(Locale.US, "%.0f", value);
		}
		if (magnitude >= 10.0)
		{
			return String.format(Locale.US, "%.1f", value);
		}
		if (magnitude >= 0.01 || value == 0.0)
		{
			return String.format(Locale.US, "%.3f", value);
		}
		return String.format(Locale.US, "%.2e", value);
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
}
