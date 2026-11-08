package TwitterGatherDataFollowers.userRyersonU;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * A node-lab study: a full factorial design over algorithms, clusters,
 * scheduling policies and scenarios, each cell run for a number of
 * repetitions.
 *
 * <b>Design principles.</b> Repetition r of every cell uses the same seeds
 * (seed_r derived from the study seed), so cells compare under common random
 * numbers: the same fault and crash realizations, which makes paired
 * comparisons much sharper. Within a repetition the cells run in a random
 * order (seeded), so slow drift of the host (thermal state, other load) cannot
 * masquerade as a policy effect. Warm-up runs (not analyzed) come first, so
 * the JVM's just-in-time compilation does not favour later cells. Every run of
 * one algorithm must produce the same recommendations (scheduling never
 * changes results); the report checks it.
 *
 * Text format (one key per line; lists are comma separated, clusters and
 * scenarios are separated by "|"). Every run recommends for the first
 * {@code targets} users of the simulator's user list and cuts every list and
 * metric at {@code k} (3 to 100, default 10), whatever the Simulator's
 * Targets page is set to:
 *
 * <pre>
 * name: Policies on a heterogeneous trio
 * dataset: Dataset/Retail.txt
 * algorithms: SIMILARITY, KMEANS
 * targets: 4
 * k: 10
 * mode: emulated
 * policies: original dsmp, capacity-weighted, work stealing, queue-length
 * baseline: original dsmp
 * clusters: preset:3 nodes: strong, normal, weak | file:Clusters/slow-links.cluster | current
 * scenarios: none | builtin:Drain mid-phase | file:Scenarios/crash.scenario
 * repetitions: 5
 * warmup: 1
 * seed: 42
 * </pre>
 */
final class StudyDesign
{
	/** List length when a design names none (what every study used before k was a setting). */
	static final int DEFAULT_K = 10;
	/** The range of the Simulator's Targets page. */
	static final int MIN_K = 3;
	static final int MAX_K = 100;

	/** One combination of factor levels. */
	static final class Cell
	{
		final int index;
		final int algorithm;
		final ClusterConfig cluster;
		final SchedulerLabEngine.SchedulingPolicy policy;
		final Scenario scenario;

		Cell(int index, int algorithm, ClusterConfig cluster, SchedulerLabEngine.SchedulingPolicy policy,
				Scenario scenario)
		{
			this.index = index;
			this.algorithm = algorithm;
			this.cluster = cluster;
			this.policy = policy;
			this.scenario = scenario;
		}

		String algorithmName()
		{
			return RecommendationModeSupport.algorithmName(algorithm);
		}

		/** The cell without its policy: the group paired comparisons are made in. */
		String groupKey()
		{
			return algorithmName() + " | " + cluster.getName() + " | " + scenario.getName();
		}

		String label()
		{
			return algorithmName() + " | " + cluster.getName() + " | " + policy.getLabel() + " | "
					+ scenario.getName();
		}
	}

	/** One scheduled run: a cell and a repetition (0 for warm-up runs). */
	static final class RunSlot
	{
		final Cell cell;
		final int repetition;
		final boolean warmup;
		final long seed;

		RunSlot(Cell cell, int repetition, boolean warmup, long seed)
		{
			this.cell = cell;
			this.repetition = repetition;
			this.warmup = warmup;
			this.seed = seed;
		}
	}

	private final String name;
	private final File dataset;
	private final List<Integer> algorithms;
	private final int targets;
	private final int k;
	private final RecommendationSchedulerPlan.Mode mode;
	private final List<SchedulerLabEngine.SchedulingPolicy> policies;
	private final SchedulerLabEngine.SchedulingPolicy baseline;
	private final List<ClusterConfig> clusters;
	private final List<Scenario> scenarios;
	private final int repetitions;
	private final int warmup;
	private final long seed;

	StudyDesign(String name, File dataset, List<Integer> algorithms, int targets, int k,
			RecommendationSchedulerPlan.Mode mode, List<SchedulerLabEngine.SchedulingPolicy> policies,
			SchedulerLabEngine.SchedulingPolicy baseline, List<ClusterConfig> clusters,
			List<Scenario> scenarios, int repetitions, int warmup, long seed)
	{
		this.name = name == null || name.trim().length() == 0 ? "Study" : name.trim();
		this.dataset = dataset;
		this.algorithms = Collections.unmodifiableList(new ArrayList<Integer>(
				algorithms == null ? Collections.<Integer>emptyList() : algorithms));
		this.targets = Math.max(1, targets);
		this.k = Math.max(MIN_K, Math.min(MAX_K, k));
		this.mode = mode == null || mode == RecommendationSchedulerPlan.Mode.DSMP_DEFAULT
				? RecommendationSchedulerPlan.Mode.SCORING_SAFE : mode;
		this.policies = Collections.unmodifiableList(new ArrayList<SchedulerLabEngine.SchedulingPolicy>(
				policies == null ? Collections.<SchedulerLabEngine.SchedulingPolicy>emptyList() : policies));
		this.baseline = baseline != null ? baseline
				: this.policies.isEmpty() ? SchedulerLabEngine.SchedulingPolicy.ORIGINAL_DSMP : this.policies.get(0);
		this.clusters = Collections.unmodifiableList(new ArrayList<ClusterConfig>(
				clusters == null ? Collections.<ClusterConfig>emptyList() : clusters));
		ArrayList<Scenario> scenarioList = new ArrayList<Scenario>(
				scenarios == null ? Collections.<Scenario>emptyList() : scenarios);
		if (scenarioList.isEmpty())
		{
			scenarioList.add(Scenario.NONE);
		}
		this.scenarios = Collections.unmodifiableList(scenarioList);
		this.repetitions = Math.max(1, repetitions);
		this.warmup = Math.max(0, warmup);
		this.seed = seed;
	}

	String getName()
	{
		return name;
	}

	File getDataset()
	{
		return dataset;
	}

	List<Integer> getAlgorithms()
	{
		return algorithms;
	}

	int getTargets()
	{
		return targets;
	}

	/** The list length k of every run. */
	int getK()
	{
		return k;
	}

	RecommendationSchedulerPlan.Mode getMode()
	{
		return mode;
	}

	List<SchedulerLabEngine.SchedulingPolicy> getPolicies()
	{
		return policies;
	}

	SchedulerLabEngine.SchedulingPolicy getBaseline()
	{
		return baseline;
	}

	List<ClusterConfig> getClusters()
	{
		return clusters;
	}

	List<Scenario> getScenarios()
	{
		return scenarios;
	}

	int getRepetitions()
	{
		return repetitions;
	}

	int getWarmup()
	{
		return warmup;
	}

	long getSeed()
	{
		return seed;
	}

	/** Every cell of the full factorial design, in factor order. */
	List<Cell> cells()
	{
		ArrayList<Cell> cells = new ArrayList<Cell>();
		int index = 0;
		for (Integer algorithm : algorithms)
		{
			for (ClusterConfig cluster : clusters)
			{
				for (Scenario scenario : scenarios)
				{
					for (SchedulerLabEngine.SchedulingPolicy policy : policies)
					{
						cells.add(new Cell(++index, algorithm.intValue(), cluster, policy, scenario));
					}
				}
			}
		}
		return cells;
	}

	/** The seed every cell uses in repetition {@code repetition} (common random numbers). */
	long seedOf(int repetition)
	{
		return WorkDispatcher.mix(seed ^ (0x5DEECE66DL * (repetition + 1L)));
	}

	/**
	 * The runs in execution order: warm-up runs first (the first cells, not
	 * analyzed), then one randomly ordered block of every cell per repetition.
	 */
	List<RunSlot> schedule()
	{
		List<Cell> cells = cells();
		ArrayList<RunSlot> slots = new ArrayList<RunSlot>();
		for (int index = 0; index < warmup && !cells.isEmpty(); index++)
		{
			slots.add(new RunSlot(cells.get(index % cells.size()), 0, true, seedOf(-1 - index)));
		}
		FailureSchedule.SplitMix random = new FailureSchedule.SplitMix(WorkDispatcher.mix(seed));
		for (int repetition = 1; repetition <= repetitions; repetition++)
		{
			ArrayList<Cell> block = new ArrayList<Cell>(cells);
			for (int index = block.size() - 1; index > 0; index--)
			{
				int swap = (int)Math.floor(random.nextUnit() * (index + 1));
				Cell held = block.get(index);
				block.set(index, block.get(swap));
				block.set(swap, held);
			}
			for (Cell cell : block)
			{
				slots.add(new RunSlot(cell, repetition, false, seedOf(repetition)));
			}
		}
		return slots;
	}

	int runCount()
	{
		return cells().size() * repetitions + warmup;
	}

	// ------------------------------------------------------------ text

	String toText()
	{
		return toText(false);
	}

	/** The file a study directory keeps this cluster in. */
	static String clusterFileName(ClusterConfig cluster)
	{
		return "cluster-" + cluster.getName().replaceAll("[^A-Za-z0-9._-]+", "-") + ".cluster";
	}

	/** The file a study directory keeps this scenario in. */
	static String scenarioFileName(Scenario scenario)
	{
		return "scenario-" + scenario.getName().replaceAll("[^A-Za-z0-9._-]+", "-") + ".scenario";
	}

	/**
	 * The design as text. Self-contained text names its clusters and
	 * scenarios by the files {@link StudyResult#write} keeps next to it, so a
	 * study directory reproduces its design exactly, whatever the presets,
	 * saved clusters or library hold later.
	 */
	String toText(boolean selfContained)
	{
		StringBuilder text = new StringBuilder();
		text.append("name: ").append(name).append('\n');
		text.append("dataset: ").append(dataset == null ? "" : dataset.getPath()).append('\n');
		text.append("algorithms: ");
		for (int index = 0; index < algorithms.size(); index++)
		{
			text.append(index == 0 ? "" : ", ").append(algorithmKey(algorithms.get(index).intValue()));
		}
		text.append('\n');
		text.append("targets: ").append(targets).append('\n');
		text.append("k: ").append(k).append('\n');
		text.append("mode: ").append(mode == RecommendationSchedulerPlan.Mode.SCHEDULING_EXPERIMENT
				? "faults" : "emulated").append('\n');
		text.append("policies: ");
		for (int index = 0; index < policies.size(); index++)
		{
			text.append(index == 0 ? "" : ", ").append(policies.get(index).getLabel().toLowerCase(Locale.US));
		}
		text.append('\n');
		text.append("baseline: ").append(baseline.getLabel().toLowerCase(Locale.US)).append('\n');
		text.append("clusters: ");
		for (int index = 0; index < clusters.size(); index++)
		{
			text.append(index == 0 ? "" : " | ").append(selfContained ? "file:" + clusterFileName(clusters.get(index))
					: "preset:" + clusters.get(index).getName());
		}
		text.append('\n');
		text.append("scenarios: ");
		for (int index = 0; index < scenarios.size(); index++)
		{
			Scenario scenario = scenarios.get(index);
			text.append(index == 0 ? "" : " | ")
					.append(scenario.isEmpty() ? "none" : selfContained ? "file:" + scenarioFileName(scenario)
							: "builtin:" + scenario.getName());
		}
		text.append('\n');
		text.append("repetitions: ").append(repetitions).append('\n');
		text.append("warmup: ").append(warmup).append('\n');
		text.append("seed: ").append(seed).append('\n');
		return text.toString();
	}

	static StudyDesign parse(String text, File baseDirectory, int heapMb) throws IOException
	{
		String name = "Study";
		File dataset = null;
		ArrayList<Integer> algorithms = new ArrayList<Integer>();
		int targets = 4;
		int k = DEFAULT_K;
		RecommendationSchedulerPlan.Mode mode = RecommendationSchedulerPlan.Mode.SCORING_SAFE;
		ArrayList<SchedulerLabEngine.SchedulingPolicy> policies = new ArrayList<SchedulerLabEngine.SchedulingPolicy>();
		SchedulerLabEngine.SchedulingPolicy baseline = null;
		ArrayList<ClusterConfig> clusters = new ArrayList<ClusterConfig>();
		ArrayList<Scenario> scenarios = new ArrayList<Scenario>();
		int repetitions = 3;
		int warmup = 1;
		long seed = 42L;
		String[] lines = (text == null ? "" : text).split("\\r?\\n");
		for (int index = 0; index < lines.length; index++)
		{
			String line = lines[index].trim();
			if (line.length() == 0 || line.startsWith("#"))
			{
				continue;
			}
			int colon = line.indexOf(':');
			if (colon < 0)
			{
				throw new IOException("Line " + (index + 1) + ": expected \"key: value\".");
			}
			String key = line.substring(0, colon).trim().toLowerCase(Locale.US);
			String value = line.substring(colon + 1).trim();
			try
			{
				if (key.equals("name"))
				{
					name = value;
				}
				else if (key.equals("dataset"))
				{
					dataset = resolve(baseDirectory, value);
				}
				else if (key.equals("algorithms"))
				{
					for (String algorithm : value.split(","))
					{
						algorithms.add(Integer.valueOf(algorithmNamed(algorithm.trim())));
					}
				}
				else if (key.equals("targets"))
				{
					targets = Integer.parseInt(value);
				}
				else if (key.equals("k"))
				{
					k = Integer.parseInt(value);
					if (k < MIN_K || k > MAX_K)
					{
						throw new IllegalArgumentException("k must be between " + MIN_K + " and " + MAX_K
								+ " (the Targets page's range); it is " + k + ".");
					}
				}
				else if (key.equals("mode"))
				{
					mode = value.toLowerCase(Locale.US).startsWith("fault")
							? RecommendationSchedulerPlan.Mode.SCHEDULING_EXPERIMENT
							: RecommendationSchedulerPlan.Mode.SCORING_SAFE;
				}
				else if (key.equals("policies"))
				{
					for (String policy : value.split(","))
					{
						policies.add(policy(policy.trim()));
					}
				}
				else if (key.equals("baseline"))
				{
					baseline = policy(value);
				}
				else if (key.equals("clusters"))
				{
					for (String cluster : value.split("\\|"))
					{
						clusters.add(cluster(cluster.trim(), baseDirectory, heapMb));
					}
				}
				else if (key.equals("scenarios"))
				{
					for (String scenario : value.split("\\|"))
					{
						scenarios.add(scenario(scenario.trim(), baseDirectory));
					}
				}
				else if (key.equals("repetitions"))
				{
					repetitions = Integer.parseInt(value);
				}
				else if (key.equals("warmup"))
				{
					warmup = Integer.parseInt(value);
				}
				else if (key.equals("seed"))
				{
					seed = Long.parseLong(value);
				}
				else
				{
					throw new IOException("unknown key " + key);
				}
			}
			catch (RuntimeException invalid)
			{
				throw new IOException("Line " + (index + 1) + ": " + invalid.getMessage(), invalid);
			}
			catch (Scenario.ScenarioException invalid)
			{
				throw new IOException("Line " + (index + 1) + ": scenario: " + invalid.getMessage(), invalid);
			}
		}
		if (algorithms.isEmpty() || policies.isEmpty() || clusters.isEmpty())
		{
			throw new IOException("A study needs at least one algorithm, one policy and one cluster.");
		}
		return new StudyDesign(name, dataset, algorithms, targets, k, mode, policies, baseline, clusters,
				scenarios, repetitions, warmup, seed);
	}

	static StudyDesign load(File file, int heapMb) throws IOException
	{
		return parse(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8),
				file.getAbsoluteFile().getParentFile(), heapMb);
	}

	private static File resolve(File base, String path)
	{
		File file = new File(path);
		if (file.isAbsolute() || file.exists() || base == null)
		{
			return file;
		}
		File relative = new File(base, path);
		return relative.exists() ? relative : file;
	}

	private static SchedulerLabEngine.SchedulingPolicy policy(String name)
	{
		SchedulerLabEngine.SchedulingPolicy policy = Scenario.policyNamed(name);
		if (policy == null)
		{
			throw new IllegalArgumentException("unknown policy \"" + name + "\"");
		}
		return policy;
	}

	private static ClusterConfig cluster(String spec, File base, int heapMb) throws IOException
	{
		String lower = spec.toLowerCase(Locale.US);
		if (lower.equals("current"))
		{
			return ClusterConfig.current("Current cluster");
		}
		if (lower.startsWith("file:"))
		{
			return ClusterConfig.load(resolve(base, spec.substring(5).trim()));
		}
		String presetName = lower.startsWith("preset:") ? spec.substring(7).trim() : spec;
		for (ClusterConfig preset : ClusterConfig.presets(heapMb))
		{
			if (preset.getName().equalsIgnoreCase(presetName))
			{
				return preset;
			}
		}
		for (ClusterConfig saved : ClusterConfig.saved())
		{
			if (saved.getName().equalsIgnoreCase(presetName))
			{
				return saved;
			}
		}
		throw new IllegalArgumentException("unknown cluster \"" + spec + "\"");
	}

	private static Scenario scenario(String spec, File base) throws IOException, Scenario.ScenarioException
	{
		String lower = spec.toLowerCase(Locale.US);
		if (lower.equals("none") || lower.length() == 0)
		{
			return Scenario.NONE;
		}
		if (lower.startsWith("file:"))
		{
			File file = resolve(base, spec.substring(5).trim());
			return Scenario.parse(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
		}
		String builtIn = lower.startsWith("builtin:") ? spec.substring(8).trim() : spec;
		Scenario scenario = Scenario.builtInNamed(builtIn);
		if (scenario == null)
		{
			for (Scenario saved : ScenarioLibrary.saved())
			{
				if (saved.getName().equalsIgnoreCase(builtIn))
				{
					return saved;
				}
			}
			throw new IllegalArgumentException("unknown scenario \"" + spec + "\"");
		}
		return scenario;
	}

	/** Algorithm constant of a name such as SIMILARITY or "K-Means". */
	static int algorithmNamed(String name)
	{
		String wanted = name.toUpperCase(Locale.US).replaceAll("[^A-Z0-9]", "");
		String[][] names = {
			{"SIMILARITY", "COSSIM", "COSINE"},
			{"KMEANS"},
			{"SVM", "EXACTSVM"},
			{"MLP"},
			{"DOC2VEC"},
			{"COMMONNEIGHBORS", "COMMONNEIGHBOURS"},
			{"KMEANSEUCLIDEAN"},
			{"SCALABLESVM", "SCALABLELINEARSVM"},
			{"TGF", "TEMPORALGRAPHFOUNDATION"}
		};
		int[] codes = {
			ControllerAgent.COS_SIM, ControllerAgent.K_MEANS, ControllerAgent.SVM, ControllerAgent.MLP,
			ControllerAgent.Doc2Vec, ControllerAgent.CommonNeighbors, ControllerAgent.K_MEANSEUCLIDEAN,
			ControllerAgent.SCALABLE_SVM, ControllerAgent.TEMPORAL_GRAPH_FOUNDATION
		};
		for (int index = 0; index < names.length; index++)
		{
			for (String candidate : names[index])
			{
				if (candidate.equals(wanted))
				{
					return codes[index];
				}
			}
		}
		for (int algorithm : codes)
		{
			if (RecommendationModeSupport.algorithmName(algorithm).toUpperCase(Locale.US)
					.replaceAll("[^A-Z0-9]", "").equals(wanted))
			{
				return algorithm;
			}
		}
		throw new IllegalArgumentException("unknown algorithm \"" + name + "\"");
	}

	static String algorithmKey(int algorithm)
	{
		return RecommendationModeSupport.algorithmName(algorithm).toUpperCase(Locale.US)
				.replaceAll("[^A-Z0-9]+", "_").replaceAll("^_|_$", "");
	}
}
