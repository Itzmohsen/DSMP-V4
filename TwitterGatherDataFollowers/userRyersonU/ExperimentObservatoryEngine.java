package TwitterGatherDataFollowers.userRyersonU;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class ExperimentObservatoryEngine
{
	private static final DecimalFormat DECIMAL = new DecimalFormat("0.##");
	private static final int MAX_TIMING_LINES = 220;
	private static final int MAX_RECOMMENDATION_LINES = 200;

	private ExperimentObservatoryEngine()
	{
	}

	static ExperimentDesign designFromSnapshot(SimulatorHelpPanel.Snapshot snapshot)
	{
		SimulatorHelpPanel.Snapshot s =
				snapshot == null ? SimulatorHelpPanel.Snapshot.empty() : snapshot;
		ArrayList<Integer> algorithms = new ArrayList<Integer>();
		algorithms.add(Integer.valueOf(s.algorithm));
		ArrayList<Integer> nodeCounts = new ArrayList<Integer>();
		nodeCounts.add(Integer.valueOf(Math.max(1, s.nodes)));
		if (s.nodes != 1)
		{
			nodeCounts.add(Integer.valueOf(1));
		}
		if (s.nodes < 8)
		{
			nodeCounts.add(Integer.valueOf(8));
		}
		ArrayList<Integer> batchSizes = new ArrayList<Integer>();
		batchSizes.add(Integer.valueOf(Math.max(1, s.batchUsers)));
		ArrayList<String> policies = new ArrayList<String>();
		policies.add("DSMP default");
		if (s.algorithm == RecommendationModeSupport.DOC2VEC
				|| s.algorithm == RecommendationModeSupport.COS_SIM
				|| s.algorithm == RecommendationModeSupport.COMMON_NEIGHBORS
				|| s.algorithm == RecommendationModeSupport.TEMPORAL_GRAPH_FOUNDATION)
		{
			policies.add("Capacity-weighted");
		}
		return new ExperimentDesign(
				s.datasetName,
				s.datasetPath,
				algorithms,
				nodeCounts,
				batchSizes,
				policies,
				Collections.singletonList(Boolean.valueOf(s.graphEnabled)),
				Collections.singletonList(Boolean.valueOf(s.simulateDelay)),
				1,
				s.topRecommendations,
				s.heapMb,
				s.modeLabel,
				s.parameterProfile,
				s.parameterSummary,
				s.capacitySummary);
	}

	static List<RunSpec> buildMatrix(ExperimentDesign design)
	{
		if (design == null)
		{
			return Collections.emptyList();
		}
		ArrayList<RunSpec> runs = new ArrayList<RunSpec>();
		int index = 1;
		for (Integer algorithm : design.algorithms)
		{
			for (Integer nodes : design.nodeCounts)
			{
				for (Integer batch : design.batchSizes)
				{
					for (String policy : design.policies)
					{
						for (Boolean graph : design.graphOptions)
						{
							for (Boolean tweetDelay : design.tweetDelayOptions)
							{
								for (int repeat = 1; repeat <= Math.max(1, design.repetitions); repeat++)
								{
									runs.add(new RunSpec(
											index++,
											algorithm.intValue(),
											RecommendationModeSupport.algorithmName(algorithm.intValue()),
											Math.max(1, nodes.intValue()),
											Math.max(1, batch.intValue()),
											policy == null ? "DSMP default" : policy,
											graph.booleanValue(),
											tweetDelay.booleanValue(),
											repeat,
											Math.max(1, design.topResults),
											Math.max(0, design.heapMb),
											design.targetModeLabel,
											design.parameterProfile));
								}
							}
						}
					}
				}
			}
		}
		return runs;
	}

	static ObservatoryReport scanProject(File projectDirectory, ExperimentDesign design)
	{
		File root = projectDirectory == null ? new File(".") : projectDirectory;
		ExperimentDesign d = design == null
				? designFromSnapshot(SimulatorHelpPanel.Snapshot.empty()) : design;
		ArrayList<String> warnings = new ArrayList<String>();
		File dataset = d.datasetPath == null || d.datasetPath.trim().length() == 0
				? null : new File(d.datasetPath);
		if (dataset != null && !dataset.isFile())
		{
			warnings.add("Selected dataset path was not found on disk: " + dataset.getPath());
		}

		TimingSummary timings = readTimingSummary(new File(root, "resultsTiming.txt"));
		File importantStuff = new File(root, "important-stuff");
		File reports = new File(root, "reports");
		File recommendations = new File(root, "Results/Recommendations");
		ArtifactInventory inventory = new ArtifactInventory(
				countFiles(importantStuff, "edges-numbers-", ".txt"),
				countFiles(importantStuff, "name-number-", ".txt"),
				countFiles(reports, "", ""),
				countFiles(recommendations, "", ""),
				new File(root, "MessagePassingTimes.txt").isFile(),
				new File(root, "recommendations_lists.txt").isFile(),
				new File(root, "recommendations_lists_similar-users.txt").isFile(),
				new File(root, "recommendations_lists_followee-accounts.txt").isFile());
		RecommendationSummary recommendationSummary =
				readRecommendationSummary(root, recommendations);
		EnvironmentSummary environment = collectEnvironment(root);
		String datasetHash = dataset != null && dataset.isFile()
				? sha256(dataset) : "unavailable";
		long datasetBytes = dataset != null && dataset.isFile() ? dataset.length() : 0L;
		String capsuleId = capsuleId(datasetHash, d, environment);
		List<RunSpec> matrix = buildMatrix(d);
		ComparisonSummary comparison =
				compare(matrix, timings, recommendationSummary, inventory);
		return new ObservatoryReport(
				d,
				matrix,
				timings,
				inventory,
				recommendationSummary,
				comparison,
				environment,
				datasetHash,
				datasetBytes,
				capsuleId,
				warnings);
	}

	static File writeCapsule(File outputDirectory, ObservatoryReport report)
			throws IOException
	{
		if (report == null)
		{
			throw new IllegalArgumentException("report must not be null");
		}
		File directory = outputDirectory == null
				? new File("reports/observatory") : outputDirectory;
		if (!directory.exists() && !directory.mkdirs())
		{
			throw new IOException("Could not create output directory: " + directory.getPath());
		}
		String safeId = report.capsuleId.replaceAll("[^A-Za-z0-9._-]", "_");
		File json = new File(directory, "dsmp-observatory-" + safeId + ".json");
		File markdown = new File(directory, "dsmp-observatory-" + safeId + ".md");
		writeText(json, toJson(report));
		writeText(markdown, toMarkdown(report));
		return json;
	}

	static String toMarkdown(ObservatoryReport report)
	{
		StringBuilder b = new StringBuilder();
		b.append("# DSMP Experiment Observatory Report\n\n");
		b.append("Generated: ").append(timestamp()).append("\n\n");
		b.append("## Experiment Design\n\n");
		b.append("- Dataset: ").append(report.design.datasetName).append("\n");
		b.append("- Dataset SHA-256: ").append(report.datasetHash).append("\n");
		b.append("- Algorithms: ").append(joinAlgorithmNames(report.design.algorithms)).append("\n");
		b.append("- Node counts: ").append(report.design.nodeCounts).append("\n");
		b.append("- Batch sizes: ").append(report.design.batchSizes).append("\n");
		b.append("- Scheduling policies: ").append(report.design.policies).append("\n");
		b.append("- Graph options: ").append(report.design.graphOptions).append("\n");
		b.append("- Tweet-delay options: ").append(report.design.tweetDelayOptions).append("\n");
		b.append("- Repetitions: ").append(report.design.repetitions).append("\n");
		b.append("- Heap MB: ").append(report.design.heapMb).append("\n");
		b.append("- Target mode: ").append(report.design.targetModeLabel).append("\n");
		b.append("- Parameters: ").append(report.design.parameterProfile).append("\n\n");
		b.append("## Artifact Summary\n\n");
		b.append("- Timing entries: ").append(report.timingSummary.stageLineCount).append("\n");
		b.append("- Latest simulation total: ").append(report.timingSummary.latestTotalBeforeGraphMs).append(" ms\n");
		b.append("- Edge files: ").append(report.inventory.edgeFiles).append("\n");
		b.append("- Node-map files: ").append(report.inventory.nodeMapFiles).append("\n");
		b.append("- Recommendation folders/files: ").append(report.inventory.recommendationArtifacts).append("\n");
		b.append("- Reports: ").append(report.inventory.reportFiles).append("\n\n");
		b.append("## Comparison Read\n\n");
		b.append("- Fastest observed run: ").append(report.comparison.fastestRead).append("\n");
		b.append("- Stability read: ").append(report.comparison.stabilityRead).append("\n");
		b.append("- Graph read: ").append(report.comparison.graphRead).append("\n");
		b.append("- Artifact completeness: ")
				.append(report.comparison.reproducibilityScore)
				.append("/100 (share of expected run artifacts present on disk; ")
				.append("not a measurement of run-to-run reproducibility)\n\n");
		b.append("## Reproducibility Capsule\n\n");
		b.append("- Capsule ID: ").append(report.capsuleId).append("\n");
		b.append("- Code commit: ").append(report.environment.gitCommit).append("\n");
		b.append("- Java: ").append(report.environment.javaVersion).append("\n");
		b.append("- Python: ").append(report.environment.pythonVersion).append("\n");
		b.append("- OS: ").append(report.environment.osName).append("\n");
		return b.toString();
	}

	static String toJson(ObservatoryReport report)
	{
		StringBuilder b = new StringBuilder();
		b.append("{\n");
		jsonField(b, 1, "generated", timestamp(), true);
		jsonField(b, 1, "capsuleId", report.capsuleId, true);
		jsonField(b, 1, "datasetName", report.design.datasetName, true);
		jsonField(b, 1, "datasetPath", report.design.datasetPath, true);
		jsonField(b, 1, "datasetHash", report.datasetHash, true);
		jsonField(b, 1, "datasetSha256", report.datasetHash, true);
		jsonNumberField(b, 1, "datasetBytes", report.datasetBytes, true);
		jsonField(b, 1, "codeCommit", report.environment.gitCommit, true);
		jsonField(b, 1, "javaVersion", report.environment.javaVersion, true);
		jsonField(b, 1, "pythonVersion", report.environment.pythonVersion, true);
		jsonField(b, 1, "targetMode", report.design.targetModeLabel, true);
		jsonField(b, 1, "parameterProfile", report.design.parameterProfile, true);
		jsonNumberField(b, 1, "heapMb", report.design.heapMb, true);
		jsonNumberField(b, 1, "plannedRuns", report.matrix.size(), true);
		jsonNumberField(b, 1, "latestTotalBeforeGraphMs",
				report.timingSummary.latestTotalBeforeGraphMs, true);
		jsonNumberField(b, 1, "edgeFiles", report.inventory.edgeFiles, true);
		jsonNumberField(b, 1, "nodeMapFiles", report.inventory.nodeMapFiles, true);
		jsonNumberField(b, 1, "recommendationArtifacts",
				report.inventory.recommendationArtifacts, true);
		jsonNumberField(b, 1, "artifactCompletenessScore",
				report.comparison.reproducibilityScore, true);
		b.append("  \"runs\": [\n");
		for (int i = 0; i < report.matrix.size(); i++)
		{
			RunSpec run = report.matrix.get(i);
			b.append("    {");
			b.append("\"id\":\"").append(escape(run.id())).append("\",");
			b.append("\"algorithm\":\"").append(escape(run.algorithmName)).append("\",");
			b.append("\"nodes\":").append(run.nodes).append(",");
			b.append("\"batchUsers\":").append(run.batchUsers).append(",");
			b.append("\"policy\":\"").append(escape(run.policy)).append("\",");
			b.append("\"graph\":").append(run.graphEnabled).append(",");
			b.append("\"tweetDelay\":").append(run.simulateTweetDelay).append(",");
			b.append("\"repeat\":").append(run.repetition);
			b.append("}");
			if (i < report.matrix.size() - 1)
			{
				b.append(",");
			}
			b.append("\n");
		}
		b.append("  ]\n");
		b.append("}\n");
		return b.toString();
	}

	/**
	 * Artifact completeness, not reproducibility.
	 *
	 * This counts which expected run artifacts exist on disk. It re-runs nothing,
	 * compares no two runs and measures no variance, so it cannot say whether a
	 * run reproduces. It was previously exported as a "reproducibility score".
	 */
	private static ComparisonSummary compare(
			List<RunSpec> matrix,
			TimingSummary timing,
			RecommendationSummary recommendations,
			ArtifactInventory inventory)
	{
		int score = 35;
		if (timing.stageLineCount > 0)
		{
			score += 18;
		}
		if (inventory.edgeFiles > 0 && inventory.nodeMapFiles > 0)
		{
			score += 18;
		}
		if (recommendations.detectedTargets > 0)
		{
			score += 14;
		}
		if (matrix.size() > 1)
		{
			score += 10;
		}
		if (inventory.hasMessagePassing)
		{
			score += 5;
		}
		score = Math.max(0, Math.min(100, score));
		String fastest = timing.latestTotalBeforeGraphMs > 0
				? "Latest completed simulator run: "
						+ timing.latestTotalBeforeGraphMs + " ms before graph visualization."
				: "No completed simulator timing artifact was found yet.";
		String stability = recommendations.detectedTargets > 0
				? recommendations.detectedTargets
						+ " target recommendation blocks found in saved output."
				: "No recommendation output artifact found yet; run the simulator to measure drift.";
		String graph = inventory.edgeFiles > 0
				? inventory.edgeFiles + " graph edge artifact(s) and "
						+ inventory.nodeMapFiles + " node-map artifact(s) are available."
				: "Graph edge artifacts are not available yet.";
		String bottleneck = timing.longestStageLabel.length() == 0
				? "Unknown until timing artifacts exist."
				: timing.longestStageLabel + " (" + timing.longestStageMs + " ms)";
		return new ComparisonSummary(score, fastest, stability, graph, bottleneck);
	}

	private static TimingSummary readTimingSummary(File file)
	{
		if (file == null || !file.isFile())
		{
			return TimingSummary.empty();
		}
		ArrayList<String> lines = new ArrayList<String>();
		int stageLines = 0;
		long latestTotal = -1L;
		long longestMs = -1L;
		String longestLabel = "";
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(
				new FileInputStream(file), StandardCharsets.UTF_8)))
		{
			String line;
			while ((line = reader.readLine()) != null)
			{
				if (line.indexOf("[StageTimer]") >= 0
						|| line.indexOf("Mapper") >= 0
						|| line.indexOf("Message Passing") >= 0)
				{
					stageLines++;
					lines.add(line);
					if (lines.size() > MAX_TIMING_LINES)
					{
						lines.remove(0);
					}
				}
				if (line.indexOf("SIMULATION total before graph visualization") >= 0)
				{
					long parsed = trailingMilliseconds(line);
					if (parsed >= 0)
					{
						latestTotal = parsed;
					}
				}
				long parsedStage = trailingMilliseconds(line);
				if (parsedStage > longestMs && line.indexOf("[StageTimer]") >= 0)
				{
					longestMs = parsedStage;
					longestLabel = line.replace("[StageTimer]", "").trim();
				}
			}
		}
		catch (IOException ignored)
		{
			return TimingSummary.empty();
		}
		return new TimingSummary(stageLines, latestTotal, longestMs,
				longestLabel, lines);
	}

	private static RecommendationSummary readRecommendationSummary(
			File root,
			File recommendationsDirectory)
	{
		ArrayList<File> candidates = new ArrayList<File>();
		addIfFile(candidates, new File(root, "recommendations_lists.txt"));
		addIfFile(candidates, new File(root, "recommendations_lists_similar-users.txt"));
		addIfFile(candidates, new File(root, "recommendations_lists_followee-accounts.txt"));
		if (recommendationsDirectory != null && recommendationsDirectory.isDirectory())
		{
			collectFiles(candidates, recommendationsDirectory, ".txt", 40);
		}
		int targetBlocks = 0;
		int recommendationLines = 0;
		LinkedHashSet<String> outputs = new LinkedHashSet<String>();
		ArrayList<String> sample = new ArrayList<String>();
		for (File file : candidates)
		{
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(
					new FileInputStream(file), StandardCharsets.UTF_8)))
			{
				String line;
				while ((line = reader.readLine()) != null)
				{
					if (line.indexOf("Recommendations for") >= 0
							|| line.indexOf("Recommendation ") >= 0)
					{
						if (line.indexOf("Recommendations for") >= 0)
						{
							targetBlocks++;
						}
						if (line.indexOf("Recommendation ") >= 0)
						{
							recommendationLines++;
							String output = afterColon(line);
							if (output.length() > 0)
							{
								outputs.add(output);
							}
						}
						if (sample.size() < MAX_RECOMMENDATION_LINES)
						{
							sample.add(line);
						}
					}
				}
			}
			catch (IOException ignored)
			{
			}
		}
		return new RecommendationSummary(
				targetBlocks,
				recommendationLines,
				outputs.size(),
				sample);
	}

	private static EnvironmentSummary collectEnvironment(File root)
	{
		return new EnvironmentSummary(
				System.getProperty("java.version", "unknown"),
				System.getProperty("os.name", "unknown") + " "
						+ System.getProperty("os.version", ""),
				readPythonVersion(),
				readGitCommit(root),
				new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
	}

	private static String readPythonVersion()
	{
		return runShortCommand(new String[] {"python3", "--version"});
	}

	private static String readGitCommit(File root)
	{
		String value = runShortCommand(root, new String[] {"git", "rev-parse", "--short", "HEAD"});
		return value.length() == 0 ? "unavailable" : value;
	}

	private static String runShortCommand(String[] command)
	{
		return runShortCommand(null, command);
	}

	private static String runShortCommand(File directory, String[] command)
	{
		try
		{
			ProcessBuilder builder = new ProcessBuilder(command);
			if (directory != null)
			{
				builder.directory(directory);
			}
			builder.redirectErrorStream(true);
			Process process = builder.start();
			StringBuilder out = new StringBuilder();
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(
					process.getInputStream(), StandardCharsets.UTF_8)))
			{
				String line = reader.readLine();
				if (line != null)
				{
					out.append(line.trim());
				}
			}
			process.waitFor();
			return out.toString();
		}
		catch (Exception ignored)
		{
			return "unavailable";
		}
	}

	private static int countFiles(File root, String prefix, String suffix)
	{
		if (root == null || !root.isDirectory())
		{
			return 0;
		}
		ArrayList<File> files = new ArrayList<File>();
		collectFiles(files, root, suffix, 5000);
		int count = 0;
		for (File file : files)
		{
			String name = file.getName();
			if ((prefix == null || prefix.length() == 0 || name.startsWith(prefix))
					&& (suffix == null || suffix.length() == 0 || name.endsWith(suffix)))
			{
				count++;
			}
		}
		return count;
	}

	private static void collectFiles(
			List<File> out,
			File root,
			String suffix,
			int limit)
	{
		if (out.size() >= limit || root == null || !root.exists())
		{
			return;
		}
		if (root.isFile())
		{
			if (suffix == null || suffix.length() == 0 || root.getName().endsWith(suffix))
			{
				out.add(root);
			}
			return;
		}
		File[] children = root.listFiles();
		if (children == null)
		{
			return;
		}
		for (File child : children)
		{
			if (out.size() >= limit)
			{
				break;
			}
			collectFiles(out, child, suffix, limit);
		}
	}

	private static void addIfFile(List<File> files, File file)
	{
		if (file != null && file.isFile())
		{
			files.add(file);
		}
	}

	private static long trailingMilliseconds(String line)
	{
		if (line == null)
		{
			return -1L;
		}
		String lower = line.toLowerCase(Locale.ROOT);
		int msIndex = lower.lastIndexOf(" ms");
		if (msIndex < 0)
		{
			return -1L;
		}
		int start = msIndex - 1;
		while (start >= 0 && Character.isDigit(line.charAt(start)))
		{
			start--;
		}
		if (start == msIndex - 1)
		{
			return -1L;
		}
		try
		{
			return Long.parseLong(line.substring(start + 1, msIndex));
		}
		catch (NumberFormatException e)
		{
			return -1L;
		}
	}

	private static String afterColon(String line)
	{
		int index = line == null ? -1 : line.indexOf(':');
		return index < 0 ? "" : line.substring(index + 1).trim();
	}

	private static String sha256(File file)
	{
		try
		{
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] buffer = new byte[1024 * 1024];
			try (FileInputStream in = new FileInputStream(file))
			{
				int read;
				while ((read = in.read(buffer)) >= 0)
				{
					if (read > 0)
					{
						digest.update(buffer, 0, read);
					}
				}
			}
			return hex(digest.digest());
		}
		catch (IOException e)
		{
			return "unavailable";
		}
		catch (NoSuchAlgorithmException e)
		{
			return "unavailable";
		}
	}

	private static String capsuleId(
			String datasetHash,
			ExperimentDesign design,
			EnvironmentSummary environment)
	{
		String seed = String.valueOf(datasetHash)
				+ "|" + design.datasetName
				+ "|" + design.algorithms
				+ "|" + design.nodeCounts
				+ "|" + design.policies
				+ "|" + design.graphOptions
				+ "|" + design.tweetDelayOptions
				+ "|" + environment.gitCommit;
		try
		{
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return hex(digest.digest(seed.getBytes(StandardCharsets.UTF_8))).substring(0, 14);
		}
		catch (NoSuchAlgorithmException e)
		{
			return Long.toHexString(seed.hashCode());
		}
	}

	private static String hex(byte[] bytes)
	{
		StringBuilder b = new StringBuilder();
		for (byte value : bytes)
		{
			b.append(String.format("%02x", value & 0xff));
		}
		return b.toString();
	}

	private static void writeText(File file, String text) throws IOException
	{
		File parent = file.getParentFile();
		if (parent != null && !parent.exists() && !parent.mkdirs())
		{
			throw new IOException("Could not create output directory: " + parent.getPath());
		}
		try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
				new FileOutputStream(file), StandardCharsets.UTF_8)))
		{
			writer.write(text == null ? "" : text);
		}
	}

	private static void jsonField(
			StringBuilder b,
			int indent,
			String key,
			String value,
			boolean comma)
	{
		indent(b, indent);
		b.append("\"").append(escape(key)).append("\": \"")
				.append(escape(value)).append("\"");
		if (comma)
		{
			b.append(",");
		}
		b.append("\n");
	}

	private static void jsonNumberField(
			StringBuilder b,
			int indent,
			String key,
			long value,
			boolean comma)
	{
		indent(b, indent);
		b.append("\"").append(escape(key)).append("\": ").append(value);
		if (comma)
		{
			b.append(",");
		}
		b.append("\n");
	}

	private static void indent(StringBuilder b, int indent)
	{
		for (int i = 0; i < indent; i++)
		{
			b.append("  ");
		}
	}

	private static String escape(String value)
	{
		if (value == null)
		{
			return "";
		}
		return value.replace("\\", "\\\\")
				.replace("\"", "\\\"")
				.replace("\n", "\\n")
				.replace("\r", "\\r");
	}

	private static String timestamp()
	{
		return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
	}

	private static String joinAlgorithmNames(List<Integer> algorithms)
	{
		ArrayList<String> names = new ArrayList<String>();
		for (Integer algorithm : algorithms)
		{
			names.add(RecommendationModeSupport.algorithmName(algorithm.intValue()));
		}
		return names.toString();
	}

	static final class ExperimentDesign
	{
		final String datasetName;
		final String datasetPath;
		final List<Integer> algorithms;
		final List<Integer> nodeCounts;
		final List<Integer> batchSizes;
		final List<String> policies;
		final List<Boolean> graphOptions;
		final List<Boolean> tweetDelayOptions;
		final int repetitions;
		final int topResults;
		final int heapMb;
		final String targetModeLabel;
		final String parameterProfile;
		final String parameterSummary;
		final String capacitySummary;

		ExperimentDesign(
				String datasetName,
				String datasetPath,
				List<Integer> algorithms,
				List<Integer> nodeCounts,
				List<Integer> batchSizes,
				List<String> policies,
				List<Boolean> graphOptions,
				List<Boolean> tweetDelayOptions,
				int repetitions,
				int topResults,
				int heapMb,
				String targetModeLabel,
				String parameterProfile,
				String parameterSummary,
				String capacitySummary)
		{
			this.datasetName = datasetName == null ? "No dataset selected" : datasetName;
			this.datasetPath = datasetPath == null ? "" : datasetPath;
			this.algorithms = immutableIntegers(algorithms, RecommendationModeSupport.K_MEANS);
			this.nodeCounts = immutableIntegers(nodeCounts, 1);
			this.batchSizes = immutableIntegers(batchSizes, 1);
			this.policies = immutableStrings(policies, "DSMP default");
			this.graphOptions = immutableBooleans(graphOptions, Boolean.FALSE);
			this.tweetDelayOptions = immutableBooleans(tweetDelayOptions, Boolean.TRUE);
			this.repetitions = Math.max(1, repetitions);
			this.topResults = Math.max(1, topResults);
			this.heapMb = Math.max(0, heapMb);
			this.targetModeLabel = targetModeLabel == null ? "Algorithm Default" : targetModeLabel;
			this.parameterProfile = parameterProfile == null ? "Default" : parameterProfile;
			this.parameterSummary = parameterSummary == null ? "" : parameterSummary;
			this.capacitySummary = capacitySummary == null ? "Balanced DSMP default" : capacitySummary;
		}

		private static List<Integer> immutableIntegers(List<Integer> values, int fallback)
		{
			LinkedHashSet<Integer> clean = new LinkedHashSet<Integer>();
			if (values != null)
			{
				for (Integer value : values)
				{
					if (value != null && value.intValue() >= 0)
					{
						clean.add(value);
					}
				}
			}
			if (clean.isEmpty())
			{
				clean.add(Integer.valueOf(fallback));
			}
			return Collections.unmodifiableList(new ArrayList<Integer>(clean));
		}

		private static List<String> immutableStrings(List<String> values, String fallback)
		{
			LinkedHashSet<String> clean = new LinkedHashSet<String>();
			if (values != null)
			{
				for (String value : values)
				{
					if (value != null && value.trim().length() > 0)
					{
						clean.add(value.trim());
					}
				}
			}
			if (clean.isEmpty())
			{
				clean.add(fallback);
			}
			return Collections.unmodifiableList(new ArrayList<String>(clean));
		}

		private static List<Boolean> immutableBooleans(List<Boolean> values, Boolean fallback)
		{
			LinkedHashSet<Boolean> clean = new LinkedHashSet<Boolean>();
			if (values != null)
			{
				for (Boolean value : values)
				{
					if (value != null)
					{
						clean.add(value);
					}
				}
			}
			if (clean.isEmpty())
			{
				clean.add(fallback);
			}
			return Collections.unmodifiableList(new ArrayList<Boolean>(clean));
		}
	}

	static final class RunSpec
	{
		final int index;
		final int algorithm;
		final String algorithmName;
		final int nodes;
		final int batchUsers;
		final String policy;
		final boolean graphEnabled;
		final boolean simulateTweetDelay;
		final int repetition;
		final int topResults;
		final int heapMb;
		final String targetModeLabel;
		final String parameterProfile;

		RunSpec(
				int index,
				int algorithm,
				String algorithmName,
				int nodes,
				int batchUsers,
				String policy,
				boolean graphEnabled,
				boolean simulateTweetDelay,
				int repetition,
				int topResults,
				int heapMb,
				String targetModeLabel,
				String parameterProfile)
		{
			this.index = index;
			this.algorithm = algorithm;
			this.algorithmName = algorithmName;
			this.nodes = nodes;
			this.batchUsers = batchUsers;
			this.policy = policy;
			this.graphEnabled = graphEnabled;
			this.simulateTweetDelay = simulateTweetDelay;
			this.repetition = repetition;
			this.topResults = topResults;
			this.heapMb = heapMb;
			this.targetModeLabel = targetModeLabel;
			this.parameterProfile = parameterProfile;
		}

		String id()
		{
			return "R" + index + "-" + algorithmName.replaceAll("[^A-Za-z0-9]+", "")
					+ "-N" + nodes + "-B" + batchUsers
					+ (graphEnabled ? "-Gon" : "-Goff")
					+ (simulateTweetDelay ? "-Don" : "-Doff")
					+ "-P" + repetition;
		}
	}

	static final class ObservatoryReport
	{
		final ExperimentDesign design;
		final List<RunSpec> matrix;
		final TimingSummary timingSummary;
		final ArtifactInventory inventory;
		final RecommendationSummary recommendationSummary;
		final ComparisonSummary comparison;
		final EnvironmentSummary environment;
		final String datasetHash;
		final long datasetBytes;
		final String capsuleId;
		final List<String> warnings;

		ObservatoryReport(
				ExperimentDesign design,
				List<RunSpec> matrix,
				TimingSummary timingSummary,
				ArtifactInventory inventory,
				RecommendationSummary recommendationSummary,
				ComparisonSummary comparison,
				EnvironmentSummary environment,
				String datasetHash,
				long datasetBytes,
				String capsuleId,
				List<String> warnings)
		{
			this.design = design;
			this.matrix = Collections.unmodifiableList(new ArrayList<RunSpec>(matrix));
			this.timingSummary = timingSummary;
			this.inventory = inventory;
			this.recommendationSummary = recommendationSummary;
			this.comparison = comparison;
			this.environment = environment;
			this.datasetHash = datasetHash;
			this.datasetBytes = datasetBytes;
			this.capsuleId = capsuleId;
			this.warnings = Collections.unmodifiableList(new ArrayList<String>(warnings));
		}
	}

	static final class TimingSummary
	{
		final int stageLineCount;
		final long latestTotalBeforeGraphMs;
		final long longestStageMs;
		final String longestStageLabel;
		final List<String> sampleLines;

		TimingSummary(
				int stageLineCount,
				long latestTotalBeforeGraphMs,
				long longestStageMs,
				String longestStageLabel,
				List<String> sampleLines)
		{
			this.stageLineCount = stageLineCount;
			this.latestTotalBeforeGraphMs = latestTotalBeforeGraphMs;
			this.longestStageMs = longestStageMs;
			this.longestStageLabel = longestStageLabel == null ? "" : longestStageLabel;
			this.sampleLines = Collections.unmodifiableList(new ArrayList<String>(sampleLines));
		}

		static TimingSummary empty()
		{
			return new TimingSummary(0, -1L, -1L, "", Collections.<String>emptyList());
		}
	}

	static final class ArtifactInventory
	{
		final int edgeFiles;
		final int nodeMapFiles;
		final int reportFiles;
		final int recommendationArtifacts;
		final boolean hasMessagePassing;
		final boolean hasDefaultRecommendationList;
		final boolean hasSimilarUsersList;
		final boolean hasFolloweeAccountsList;

		ArtifactInventory(
				int edgeFiles,
				int nodeMapFiles,
				int reportFiles,
				int recommendationArtifacts,
				boolean hasMessagePassing,
				boolean hasDefaultRecommendationList,
				boolean hasSimilarUsersList,
				boolean hasFolloweeAccountsList)
		{
			this.edgeFiles = edgeFiles;
			this.nodeMapFiles = nodeMapFiles;
			this.reportFiles = reportFiles;
			this.recommendationArtifacts = recommendationArtifacts;
			this.hasMessagePassing = hasMessagePassing;
			this.hasDefaultRecommendationList = hasDefaultRecommendationList;
			this.hasSimilarUsersList = hasSimilarUsersList;
			this.hasFolloweeAccountsList = hasFolloweeAccountsList;
		}
	}

	static final class RecommendationSummary
	{
		final int detectedTargets;
		final int recommendationLines;
		final int uniqueOutputs;
		final List<String> sampleLines;

		RecommendationSummary(
				int detectedTargets,
				int recommendationLines,
				int uniqueOutputs,
				List<String> sampleLines)
		{
			this.detectedTargets = detectedTargets;
			this.recommendationLines = recommendationLines;
			this.uniqueOutputs = uniqueOutputs;
			this.sampleLines = Collections.unmodifiableList(new ArrayList<String>(sampleLines));
		}
	}

	static final class ComparisonSummary
	{
		final int reproducibilityScore;
		final String fastestRead;
		final String stabilityRead;
		final String graphRead;
		final String bottleneckRead;

		ComparisonSummary(
				int reproducibilityScore,
				String fastestRead,
				String stabilityRead,
				String graphRead,
				String bottleneckRead)
		{
			this.reproducibilityScore = reproducibilityScore;
			this.fastestRead = fastestRead;
			this.stabilityRead = stabilityRead;
			this.graphRead = graphRead;
			this.bottleneckRead = bottleneckRead;
		}
	}

	static final class EnvironmentSummary
	{
		final String javaVersion;
		final String osName;
		final String pythonVersion;
		final String gitCommit;
		final String generatedAt;

		EnvironmentSummary(
				String javaVersion,
				String osName,
				String pythonVersion,
				String gitCommit,
				String generatedAt)
		{
			this.javaVersion = javaVersion;
			this.osName = osName;
			this.pythonVersion = pythonVersion;
			this.gitCommit = gitCommit;
			this.generatedAt = generatedAt;
		}
	}
}
