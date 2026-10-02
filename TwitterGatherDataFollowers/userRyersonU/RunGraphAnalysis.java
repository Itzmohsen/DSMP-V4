package TwitterGatherDataFollowers.userRyersonU;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SplittableRandom;

/**
 * The network analysis of one run graph: what the recommender did to the
 * social network, measured against chance.
 *
 * <pre>
 * Recommendation network   who is recommended to whom (top-k, rank-weighted)
 *   homophily             recommendations inside the target's community, exact test vs random lists
 *   assortativity         Newman's r by community, jackknife error
 *   community recovery    Leiden communities of the recommendations vs the real ones (AMI, ARI, NMI)
 *   exposure              who gets recommended: coverage, Gini, entropy, hubness, size bias
 * Adoption                 the follow simulation's edges added to the observed network
 *   E-I index             bridging vs reinforcing adoptions, exact test vs random adoption
 *   evolution             modularity of the real communities, components, detected communities before and after
 * Nodes                    PageRank and the attention it gained, betweenness, k-core, roles
 * </pre>
 *
 * Everything is deterministic: node order is by name, Leiden and sampled
 * statistics use fixed seeds.
 */
final class RunGraphAnalysis
{
	static final double RESOLUTION = 1.0;
	static final long SEED = 0x6A09E667F3BCC908L;
	static final int LEIDEN_STARTS = 5;
	static final double DAMPING = 0.85;
	static final int PERMUTATIONS = 999;
	static final int LIST_PERMUTATIONS = 9999;
	/** Random rankings when the lists hold more than {@link #LARGE_LISTS} edges in all (p resolution 0.0005). */
	static final int LARGE_LIST_PERMUTATIONS = 1999;
	static final int LARGE_LISTS = 20000;
	static final int EXACT_BETWEENNESS_LIMIT = 4000;
	static final int BETWEENNESS_PIVOTS = 400;
	static final int EXACT_DISTANCE_LIMIT = 3000;
	static final int DISTANCE_PIVOTS = 300;

	/** The structure of one view of the graph (a set of layers). */
	static final class View
	{
		final String name;
		final String description;
		final int activeNodes;
		final int edges;
		final double density;
		final double meanDegree;
		final int components;
		final int giantComponent;
		final double giantFraction;
		final double transitivity;
		final double averageClustering;
		final NetworkAlgorithms.Distances distances;
		final LeidenClustering.Result communities;
		/** Detected communities with at least one node that has an edge in this view. */
		final int activeCommunities;
		/** Modularity of the real communities in this view (NaN without edges). */
		final double realModularity;
		/** Agreement of the detected and the real communities over the view's labelled active nodes. */
		final NetworkStatistics.Agreement recovery;
		final int[] componentOf;

		View(String name, String description, int activeNodes, int edges, double density, double meanDegree,
				int components, int giantComponent, double giantFraction, double transitivity,
				double averageClustering, NetworkAlgorithms.Distances distances, LeidenClustering.Result communities,
				int activeCommunities, double realModularity, NetworkStatistics.Agreement recovery, int[] componentOf)
		{
			this.name = name;
			this.description = description;
			this.activeNodes = activeNodes;
			this.edges = edges;
			this.density = density;
			this.meanDegree = meanDegree;
			this.components = components;
			this.giantComponent = giantComponent;
			this.giantFraction = giantFraction;
			this.transitivity = transitivity;
			this.averageClustering = averageClustering;
			this.distances = distances;
			this.communities = communities;
			this.activeCommunities = activeCommunities;
			this.realModularity = realModularity;
			this.recovery = recovery;
			this.componentOf = componentOf;
		}
	}

	/** One of the analysis' plain-language findings. */
	static final class Finding
	{
		final String title;
		final String detail;
		/** "good", "bad", "neutral" or "warning": how the UI colours it. */
		final String tone;

		Finding(String title, String detail, String tone)
		{
			this.title = title;
			this.detail = detail;
			this.tone = tone;
		}
	}

	final RunGraph graph;
	final View observed;
	final View adopted;
	final View recommended;
	final View full;

	// recommendation homophily
	final int insideRecommendations;
	final int classifiedRecommendations;
	final double homophily;
	final double homophilyChance;
	final NetworkStatistics.CountDistribution homophilyNull;
	final double homophilyP;
	/** Homophily with each edge weighted by its rank (1 / log2(1 + rank)), and its permutation test. */
	final double weightedHomophily;
	final double weightedHomophilyChance;
	final double weightedHomophilyP;
	/** The random rankings the rank-weighted test drew. */
	final int listPermutations;
	/** Every list holds every candidate it could (k at least the candidate set): only the ranking varies. */
	final boolean saturated;
	/** Rank-weighted assortativity of the recommendation edges by community. */
	final NetworkStatistics.Assortativity assortativity;
	final int[][] recommendationMixing;
	final double recoveryPermutationP;
	final int recoveryNodes;

	// exposure (position-weighted: each appearance counts 1 / log2(1 + rank))
	final double[] exposure;
	/** Each node's position-weighted exposure (0 for nodes never recommended). */
	final double[] exposureByNode;
	final int exposedCandidates;
	final double coverage;
	final double exposureGini;
	final double exposureEntropy;
	final double exposureSkewness;
	final NetworkStatistics.Correlation sizeBias;
	/** Each community's share of recommendation edges and of candidates. */
	final double[] communityExposureShare;
	final double[] communityCandidateShare;

	// adoption
	final int adoptionsInside;
	final int adoptionsClassified;
	final double eiIndex;
	final double eiChance;
	final NetworkStatistics.CountDistribution adoptionNull;
	final double adoptionP;
	final int[][] adoptionMixing;
	final int bridgedPairs;
	final int mergedComponents;

	// nodes
	final double[] pageRank;
	final double[] pageRankObserved;
	final NetworkAlgorithms.Betweenness betweenness;
	final int[] core;
	final double[] participation;
	final double[] withinModuleZ;
	final String[] roles;
	final int[] followers;
	final int[] recommendedIn;
	final int[] adoptedIn;
	final int[] degreeFull;
	final int maxCore;

	final List<Finding> findings;
	final List<String> warnings;

	private RunGraphAnalysis(Builder b)
	{
		graph = b.graph;
		observed = b.observed;
		adopted = b.adopted;
		recommended = b.recommended;
		full = b.full;
		insideRecommendations = b.insideRecommendations;
		classifiedRecommendations = b.classifiedRecommendations;
		homophily = b.homophily;
		homophilyChance = b.homophilyChance;
		homophilyNull = b.homophilyNull;
		homophilyP = b.homophilyP;
		weightedHomophily = b.weightedHomophily;
		weightedHomophilyChance = b.weightedHomophilyChance;
		weightedHomophilyP = b.weightedHomophilyP;
		listPermutations = b.listPermutations;
		saturated = b.saturated;
		assortativity = b.assortativity;
		recommendationMixing = b.recommendationMixing;
		recoveryPermutationP = b.recoveryPermutationP;
		recoveryNodes = b.recoveryNodes;
		exposure = b.exposure;
		exposureByNode = b.exposureByNode;
		exposedCandidates = b.exposedCandidates;
		coverage = b.coverage;
		exposureGini = b.exposureGini;
		exposureEntropy = b.exposureEntropy;
		exposureSkewness = b.exposureSkewness;
		sizeBias = b.sizeBias;
		communityExposureShare = b.communityExposureShare;
		communityCandidateShare = b.communityCandidateShare;
		adoptionsInside = b.adoptionsInside;
		adoptionsClassified = b.adoptionsClassified;
		eiIndex = b.eiIndex;
		eiChance = b.eiChance;
		adoptionNull = b.adoptionNull;
		adoptionP = b.adoptionP;
		adoptionMixing = b.adoptionMixing;
		bridgedPairs = b.bridgedPairs;
		mergedComponents = b.mergedComponents;
		pageRank = b.pageRank;
		pageRankObserved = b.pageRankObserved;
		betweenness = b.betweenness;
		core = b.core;
		participation = b.participation;
		withinModuleZ = b.withinModuleZ;
		roles = b.roles;
		followers = b.followers;
		recommendedIn = b.recommendedIn;
		adoptedIn = b.adoptedIn;
		degreeFull = b.degreeFull;
		int highest = 0;
		for (int value : core)
		{
			highest = Math.max(highest, value);
		}
		maxCore = highest;
		findings = Collections.unmodifiableList(b.findings);
		warnings = Collections.unmodifiableList(b.warnings);
	}

	/** Analyses a run graph. */
	static RunGraphAnalysis of(RunGraph graph)
	{
		Builder builder = new Builder(graph);
		builder.compute();
		return new RunGraphAnalysis(builder);
	}

	// ------------------------------------------------------------ derived values

	/** Each node's ground-truth community, -1 when unknown. */
	int[] realCommunities()
	{
		int[] labels = new int[graph.size()];
		for (int node = 0; node < labels.length; node++)
		{
			labels[node] = graph.community(node);
		}
		return labels;
	}

	/** PageRank gained from the recommender: the full network's minus the observed network's. */
	double attentionGained(int node)
	{
		return pageRank[node] - pageRankObserved[node];
	}

	double homophilyLift()
	{
		return homophilyChance > 0.0 ? homophily / homophilyChance : Double.NaN;
	}

	/** Nodes ranked by a score, highest first (ties by name), at most {@code limit}. */
	List<Integer> top(final double[] score, int limit, boolean candidatesOnly)
	{
		List<Integer> nodes = new ArrayList<Integer>();
		for (int node = 0; node < score.length; node++)
		{
			if (!candidatesOnly || graph.isCandidate(node))
			{
				nodes.add(Integer.valueOf(node));
			}
		}
		Collections.sort(nodes, new Comparator<Integer>()
		{
			public int compare(Integer left, Integer right)
			{
				int byScore = Double.compare(score[right.intValue()], score[left.intValue()]);
				return byScore != 0 ? byScore : graph.name(left.intValue()).compareTo(graph.name(right.intValue()));
			}
		});
		return nodes.subList(0, Math.min(limit, nodes.size()));
	}

	static double[] asDoubles(int[] values)
	{
		double[] result = new double[values.length];
		for (int index = 0; index < values.length; index++)
		{
			result[index] = values[index];
		}
		return result;
	}

	/** The analysis as JSON-ready maps (graph.json). */
	LinkedHashMap<String,Object> toJson(GraphLayoutResult layout)
	{
		LinkedHashMap<String,Object> json = new LinkedHashMap<String,Object>();
		json.put("format", "dsmp-run-graph/1");
		json.put("runId", graph.runId);
		json.put("algorithm", graph.algorithm);
		json.put("dataset", graph.dataset);
		json.put("candidateType", graph.candidateType == RecommendationCandidateType.FOLLOWEE ? "account" : "user");
		json.put("k", Integer.valueOf(graph.cutoff));
		LinkedHashMap<String,Object> nodes = new LinkedHashMap<String,Object>();
		nodes.put("total", Integer.valueOf(graph.size()));
		nodes.put("users", Integer.valueOf(graph.count(RunGraph.Kind.USER)));
		nodes.put("accounts", Integer.valueOf(graph.count(RunGraph.Kind.ACCOUNT)));
		nodes.put("usersAndAccounts", Integer.valueOf(graph.count(RunGraph.Kind.BOTH)));
		nodes.put("targets", Integer.valueOf(graph.targetCount()));
		nodes.put("candidates", Integer.valueOf(graph.candidateCount()));
		nodes.put("communities", Integer.valueOf(graph.communityCount()));
		json.put("nodes", nodes);
		LinkedHashMap<String,Object> edges = new LinkedHashMap<String,Object>();
		for (RunGraph.Layer layer : RunGraph.Layer.values())
		{
			edges.put(layer.name().toLowerCase(Locale.US), Integer.valueOf(graph.edges(layer).size()));
		}
		json.put("edges", edges);
		LinkedHashMap<String,Object> views = new LinkedHashMap<String,Object>();
		views.put("observed", viewJson(observed));
		views.put("adopted", viewJson(adopted));
		views.put("recommendations", viewJson(recommended));
		views.put("full", viewJson(full));
		json.put("views", views);
		LinkedHashMap<String,Object> homophilyJson = new LinkedHashMap<String,Object>();
		homophilyJson.put("inside", Integer.valueOf(insideRecommendations));
		homophilyJson.put("edges", Integer.valueOf(classifiedRecommendations));
		homophilyJson.put("fraction", finite(homophily));
		homophilyJson.put("chance", finite(homophilyChance));
		homophilyJson.put("lift", finite(homophilyLift()));
		homophilyJson.put("pValue", finite(homophilyP));
		homophilyJson.put("exactNull", Boolean.valueOf(homophilyNull != null && homophilyNull.exact()));
		homophilyJson.put("rankWeighted", finite(weightedHomophily));
		homophilyJson.put("rankWeightedChance", finite(weightedHomophilyChance));
		homophilyJson.put("rankWeightedPValue", finite(weightedHomophilyP));
		homophilyJson.put("rankWeightedPermutations", Integer.valueOf(listPermutations));
		homophilyJson.put("saturatedLists", Boolean.valueOf(saturated));
		json.put("homophily", homophilyJson);
		LinkedHashMap<String,Object> assortativityJson = new LinkedHashMap<String,Object>();
		assortativityJson.put("r", finite(assortativity.r));
		assortativityJson.put("standardError", finite(assortativity.standardError));
		assortativityJson.put("z", finite(assortativity.standardError > 0.0
				? assortativity.r / assortativity.standardError : Double.NaN));
		assortativityJson.put("pValue", finite(assortativity.pValue()));
		assortativityJson.put("edges", Integer.valueOf(assortativity.edges));
		assortativityJson.put("weights", "1 / log2(1 + rank)");
		json.put("assortativity", assortativityJson);
		LinkedHashMap<String,Object> recovery = new LinkedHashMap<String,Object>();
		recovery.put("method", "Leiden, modularity, resolution " + RESOLUTION + ", rank-weighted edges");
		recovery.put("communities", Integer.valueOf(recommended.activeCommunities));
		recovery.put("modularity", finite(recommended.communities.quality));
		recovery.put("nodes", Integer.valueOf(recoveryNodes));
		recovery.put("ami", finite(recommended.recovery.ami));
		recovery.put("ari", finite(recommended.recovery.ari));
		recovery.put("nmi", finite(recommended.recovery.nmi));
		recovery.put("permutationP", finite(recoveryPermutationP));
		recovery.put("permutations", Integer.valueOf(PERMUTATIONS));
		json.put("communityRecovery", recovery);
		LinkedHashMap<String,Object> exposureJson = new LinkedHashMap<String,Object>();
		exposureJson.put("weights", "1 / log2(1 + rank) per appearance (Singh and Joachims 2018)");
		exposureJson.put("candidates", Integer.valueOf(exposure.length));
		exposureJson.put("exposed", Integer.valueOf(exposedCandidates));
		exposureJson.put("coverage", finite(coverage));
		exposureJson.put("gini", finite(exposureGini));
		exposureJson.put("normalisedEntropy", finite(exposureEntropy));
		exposureJson.put("skewness", finite(exposureSkewness));
		LinkedHashMap<String,Object> bias = new LinkedHashMap<String,Object>();
		bias.put("rho", finite(sizeBias.rho));
		bias.put("pValue", finite(sizeBias.pValue));
		bias.put("n", Integer.valueOf(sizeBias.n));
		exposureJson.put("communitySizeSpearman", bias);
		json.put("exposure", exposureJson);
		LinkedHashMap<String,Object> adoption = new LinkedHashMap<String,Object>();
		adoption.put("adoptions", Integer.valueOf(graph.edges(RunGraph.Layer.ADOPT).size()));
		adoption.put("inside", Integer.valueOf(adoptionsInside));
		adoption.put("classified", Integer.valueOf(adoptionsClassified));
		adoption.put("eiIndex", finite(eiIndex));
		adoption.put("eiChance", finite(eiChance));
		adoption.put("pValue", finite(adoptionP));
		adoption.put("exactNull", Boolean.valueOf(adoptionNull != null && adoptionNull.exact()));
		adoption.put("realModularityBefore", finite(observed.realModularity));
		adoption.put("realModularityAfter", finite(adopted.realModularity));
		adoption.put("componentsBefore", Integer.valueOf(observed.components));
		adoption.put("componentsAfter", Integer.valueOf(adopted.components));
		adoption.put("bridgedCommunityPairs", Integer.valueOf(bridgedPairs));
		adoption.put("detectedCommunitiesBefore", Integer.valueOf(observed.activeCommunities));
		adoption.put("detectedCommunitiesAfter", Integer.valueOf(adopted.activeCommunities));
		adoption.put("amiBefore", finite(observed.recovery.ami));
		adoption.put("amiAfter", finite(adopted.recovery.ami));
		json.put("adoption", adoption);
		LinkedHashMap<String,Object> mixing = new LinkedHashMap<String,Object>();
		List<Object> communityNames = new ArrayList<Object>();
		for (int community = 0; community < graph.communityCount(); community++)
		{
			communityNames.add(graph.communityName(community));
		}
		mixing.put("communities", communityNames);
		mixing.put("recommendations", matrixJson(recommendationMixing));
		mixing.put("adoptions", matrixJson(adoptionMixing));
		json.put("mixing", mixing);
		LinkedHashMap<String,Object> topNodes = new LinkedHashMap<String,Object>();
		topNodes.put("pageRank", topJson(pageRank, false));
		topNodes.put("betweenness", topJson(betweenness.values, false));
		topNodes.put("exposure", topJson(exposureByNode, true));
		json.put("topNodes", topNodes);
		LinkedHashMap<String,Object> parameters = new LinkedHashMap<String,Object>();
		parameters.put("leidenResolution", Double.valueOf(RESOLUTION));
		parameters.put("leidenStarts", Integer.valueOf(LEIDEN_STARTS));
		parameters.put("leidenRandomness", Double.valueOf(LeidenClustering.RANDOMNESS));
		parameters.put("seed", Long.valueOf(SEED));
		parameters.put("pageRankDamping", Double.valueOf(DAMPING));
		parameters.put("betweenness", betweenness.exact ? "exact" : "estimated from " + betweenness.sources
				+ " pivots");
		parameters.put("recommendationEdgeWeight", "1 / log2(1 + rank)");
		parameters.put("adoption", "one of the top " + Math.min(3, graph.cutoff)
				+ " recommendations, chosen with probability proportional to its score (seeded by the user)");
		json.put("parameters", parameters);
		if (layout != null)
		{
			LinkedHashMap<String,Object> layoutJson = new LinkedHashMap<String,Object>();
			layoutJson.put("algorithm", layout.description);
			layoutJson.put("iterations", Integer.valueOf(layout.iterations));
			layoutJson.put("converged", Boolean.valueOf(layout.converged));
			json.put("layout", layoutJson);
		}
		List<Object> findingList = new ArrayList<Object>();
		for (Finding finding : findings)
		{
			LinkedHashMap<String,Object> value = new LinkedHashMap<String,Object>();
			value.put("title", finding.title);
			value.put("detail", finding.detail);
			value.put("tone", finding.tone);
			findingList.add(value);
		}
		json.put("findings", findingList);
		json.put("warnings", new ArrayList<Object>(warnings));
		return json;
	}

	private LinkedHashMap<String,Object> viewJson(View view)
	{
		LinkedHashMap<String,Object> json = new LinkedHashMap<String,Object>();
		json.put("description", view.description);
		json.put("activeNodes", Integer.valueOf(view.activeNodes));
		json.put("edges", Integer.valueOf(view.edges));
		json.put("density", finite(view.density));
		json.put("meanDegree", finite(view.meanDegree));
		json.put("components", Integer.valueOf(view.components));
		json.put("giantComponent", Integer.valueOf(view.giantComponent));
		json.put("giantFraction", finite(view.giantFraction));
		json.put("transitivity", finite(view.transitivity));
		json.put("averageClustering", finite(view.averageClustering));
		json.put("meanPathLength", finite(view.distances.meanLength));
		json.put("diameter", Integer.valueOf(view.distances.diameter));
		json.put("distancesExact", Boolean.valueOf(view.distances.exact));
		json.put("leidenCommunities", Integer.valueOf(view.activeCommunities));
		json.put("leidenModularity", finite(view.communities.quality));
		json.put("realModularity", finite(view.realModularity));
		json.put("amiWithReal", finite(view.recovery.ami));
		json.put("ariWithReal", finite(view.recovery.ari));
		return json;
	}

	private List<Object> matrixJson(int[][] matrix)
	{
		List<Object> rows = new ArrayList<Object>();
		for (int[] row : matrix)
		{
			List<Object> values = new ArrayList<Object>();
			for (int value : row)
			{
				values.add(Integer.valueOf(value));
			}
			rows.add(values);
		}
		return rows;
	}

	private List<Object> topJson(double[] score, boolean candidatesOnly)
	{
		List<Object> list = new ArrayList<Object>();
		for (Integer node : top(score, 10, candidatesOnly))
		{
			LinkedHashMap<String,Object> value = new LinkedHashMap<String,Object>();
			value.put("name", graph.name(node.intValue()));
			value.put("value", finite(score[node.intValue()]));
			list.add(value);
		}
		return list;
	}

	static Double finite(double value)
	{
		return Double.isNaN(value) || Double.isInfinite(value) ? null : Double.valueOf(value);
	}

	/** A computed layout: positions and how they were obtained. */
	static final class GraphLayoutResult
	{
		final double[] x;
		final double[] y;
		final String description;
		final int iterations;
		final boolean converged;

		GraphLayoutResult(double[] x, double[] y, String description, int iterations, boolean converged)
		{
			this.x = x;
			this.y = y;
			this.description = description;
			this.iterations = iterations;
			this.converged = converged;
		}
	}

	// ------------------------------------------------------------ computation

	private static final class Builder
	{
		final RunGraph graph;
		final int n;
		View observed;
		View adopted;
		View recommended;
		View full;
		int insideRecommendations;
		int classifiedRecommendations;
		double homophily = Double.NaN;
		double homophilyChance = Double.NaN;
		NetworkStatistics.CountDistribution homophilyNull;
		double homophilyP = Double.NaN;
		double weightedHomophily = Double.NaN;
		double weightedHomophilyChance = Double.NaN;
		double weightedHomophilyP = Double.NaN;
		int listPermutations;
		boolean saturated;
		NetworkStatistics.Assortativity assortativity;
		int[][] recommendationMixing;
		double recoveryPermutationP = Double.NaN;
		int recoveryNodes;
		double[] exposure;
		double[] exposureByNode;
		int exposedCandidates;
		double coverage = Double.NaN;
		double exposureGini = Double.NaN;
		double exposureEntropy = Double.NaN;
		double exposureSkewness = Double.NaN;
		NetworkStatistics.Correlation sizeBias;
		double[] communityExposureShare;
		double[] communityCandidateShare;
		int adoptionsInside;
		int adoptionsClassified;
		double eiIndex = Double.NaN;
		double eiChance = Double.NaN;
		NetworkStatistics.CountDistribution adoptionNull;
		double adoptionP = Double.NaN;
		int[][] adoptionMixing;
		int bridgedPairs;
		int mergedComponents;
		double[] pageRank;
		double[] pageRankObserved;
		NetworkAlgorithms.Betweenness betweenness;
		int[] core;
		double[] participation;
		double[] withinModuleZ;
		String[] roles;
		int[] followers;
		int[] recommendedIn;
		int[] adoptedIn;
		int[] degreeFull;
		final List<Finding> findings = new ArrayList<Finding>();
		final List<String> warnings = new ArrayList<String>();

		Builder(RunGraph graph)
		{
			this.graph = graph;
			this.n = graph.size();
		}

		void compute()
		{
			int[] real = new int[n];
			for (int node = 0; node < n; node++)
			{
				real[node] = graph.community(node);
			}
			observed = view("Observed network", "Follows only: every user and the account it follows.",
					EnumLayers.FOLLOW, real);
			adopted = view("After adoption", "Follows plus each target's adopted recommendation.",
					EnumLayers.FOLLOW_ADOPT, real);
			recommended = view("Recommendation network",
					"Each target linked to its top-" + graph.cutoff + " recommendations, weighted by rank.",
					EnumLayers.RECOMMEND, real);
			full = view("Whole network", "Follows, recommendations and adoptions together.", EnumLayers.ALL, real);
			homophily(real);
			recovery(real);
			exposure();
			adoption(real);
			nodes();
			findings();
		}

		/** The layers a view is made of. */
		private enum EnumLayers
		{
			FOLLOW,
			FOLLOW_ADOPT,
			RECOMMEND,
			ALL;

			boolean includes(RunGraph.Layer layer)
			{
				switch (this)
				{
					case FOLLOW:
						return layer == RunGraph.Layer.FOLLOW;
					case FOLLOW_ADOPT:
						return layer != RunGraph.Layer.RECOMMEND;
					case RECOMMEND:
						return layer == RunGraph.Layer.RECOMMEND;
					default:
						return true;
				}
			}
		}

		private NetworkAlgorithms.Undirected undirected(EnumLayers layers, boolean weighted)
		{
			List<RunGraph.Edge> edges = new ArrayList<RunGraph.Edge>();
			for (RunGraph.Layer layer : RunGraph.Layer.values())
			{
				if (layers.includes(layer))
				{
					edges.addAll(graph.edges(layer));
				}
			}
			int[] sources = new int[edges.size()];
			int[] targets = new int[edges.size()];
			double[] weights = new double[edges.size()];
			for (int index = 0; index < edges.size(); index++)
			{
				RunGraph.Edge edge = edges.get(index);
				sources[index] = edge.source;
				targets[index] = edge.target;
				weights[index] = weighted ? edge.weight() : 1.0;
			}
			return NetworkAlgorithms.Undirected.of(n, sources, targets, weights);
		}

		private NetworkAlgorithms.Directed directed(EnumLayers layers)
		{
			List<RunGraph.Edge> edges = new ArrayList<RunGraph.Edge>();
			for (RunGraph.Layer layer : RunGraph.Layer.values())
			{
				if (layers.includes(layer))
				{
					edges.addAll(graph.edges(layer));
				}
			}
			int[] sources = new int[edges.size()];
			int[] targets = new int[edges.size()];
			double[] weights = new double[edges.size()];
			for (int index = 0; index < edges.size(); index++)
			{
				sources[index] = edges.get(index).source;
				targets[index] = edges.get(index).target;
				weights[index] = edges.get(index).weight();
			}
			return NetworkAlgorithms.Directed.of(n, sources, targets, weights);
		}

		private View view(String name, String description, EnumLayers layers, int[] real)
		{
			NetworkAlgorithms.Undirected weighted = undirected(layers, true);
			NetworkAlgorithms.Undirected simple = weighted.unweighted();
			int active = 0;
			for (int node = 0; node < n; node++)
			{
				active += simple.degree(node) > 0 ? 1 : 0;
			}
			int edges = simple.edgeCount();
			double density = active < 2 ? 0.0 : 2.0 * edges / ((double)active * (active - 1.0));
			int[] component = NetworkAlgorithms.components(simple);
			int[] sizes = NetworkAlgorithms.groupSizes(component);
			int components = 0;
			for (int size : sizes)
			{
				components += size > 1 ? 1 : 0;
			}
			int giant = sizes.length == 0 || sizes[0] < 2 ? 0 : sizes[0];
			double[] clustering = NetworkAlgorithms.clustering(simple);
			double clusteringSum = 0.0;
			for (int node = 0; node < n; node++)
			{
				clusteringSum += simple.degree(node) > 0 ? clustering[node] : 0.0;
			}
			NetworkAlgorithms.Distances distances = giant < 2 ? new NetworkAlgorithms.Distances(0.0, 0, true)
					: NetworkAlgorithms.distances(simple, component, 0, EXACT_DISTANCE_LIMIT, DISTANCE_PIVOTS, SEED);
			LeidenClustering.Result communities = LeidenClustering.cluster(weighted, RESOLUTION, SEED, LEIDEN_STARTS);
			double realQ = weighted.total > 0.0 ? NetworkAlgorithms.modularity(weighted, real, RESOLUTION) : Double.NaN;
			int[] detected = new int[n];
			int[] truth = new int[n];
			for (int node = 0; node < n; node++)
			{
				boolean keep = simple.degree(node) > 0 && real[node] >= 0;
				detected[node] = keep ? communities.membership[node] : -1;
				truth[node] = keep ? real[node] : -1;
			}
			NetworkStatistics.Agreement recovery = NetworkStatistics.agreement(truth, detected);
			boolean[] seen = new boolean[communities.communities];
			int activeCommunities = 0;
			for (int node = 0; node < n; node++)
			{
				if (simple.degree(node) > 0 && !seen[communities.membership[node]])
				{
					seen[communities.membership[node]] = true;
					activeCommunities++;
				}
			}
			return new View(name, description, active, edges, density, active == 0 ? 0.0 : 2.0 * edges / active,
					components, giant, active == 0 ? 0.0 : giant / (double)active,
					NetworkAlgorithms.transitivity(simple), active == 0 ? 0.0 : clusteringSum / active, distances,
					communities, activeCommunities, realQ, recovery, component);
		}

		/** Where recommendations go relative to the target's community, against random lists. */
		private void homophily(int[] real)
		{
			int communities = graph.communityCount();
			recommendationMixing = new int[communities][communities];
			List<RunGraph.Edge> edges = graph.edges(RunGraph.Layer.RECOMMEND);
			int[] sourceCategory = new int[edges.size()];
			int[] targetCategory = new int[edges.size()];
			double[] weights = new double[edges.size()];
			int[] perTarget = new int[n];
			double weightTotal = 0.0;
			double weightInside = 0.0;
			for (int index = 0; index < edges.size(); index++)
			{
				RunGraph.Edge edge = edges.get(index);
				int own = real[edge.source];
				int other = candidateCommunity(edge.target);
				sourceCategory[index] = own;
				targetCategory[index] = other;
				weights[index] = edge.weight();
				if (own < 0)
				{
					continue;
				}
				perTarget[edge.source]++;
				classifiedRecommendations++;
				weightTotal += edge.weight();
				if (other >= 0)
				{
					recommendationMixing[own][other]++;
				}
				if (other == own)
				{
					insideRecommendations++;
					weightInside += edge.weight();
				}
			}
			assortativity = NetworkStatistics.assortativity(sourceCategory, targetCategory, weights, communities);
			if (classifiedRecommendations == 0)
			{
				return;
			}
			homophily = insideRecommendations / (double)classifiedRecommendations;
			weightedHomophily = weightInside / weightTotal;
			List<Integer> listed = new ArrayList<Integer>();
			double expected = 0.0;
			double expectedWeighted = 0.0;
			saturated = true;
			for (int node = 0; node < n; node++)
			{
				if (perTarget[node] == 0)
				{
					continue;
				}
				listed.add(Integer.valueOf(node));
				int[] counts = graph.insideCounts(node);
				double chance = counts[1] == 0 ? 0.0 : counts[0] / (double)counts[1];
				expected += perTarget[node] * chance;
				for (int rank = 1; rank <= perTarget[node]; rank++)
				{
					expectedWeighted += RunGraph.rankWeight(rank) * chance;
				}
				saturated &= perTarget[node] >= counts[1];
			}
			homophilyChance = expected / classifiedRecommendations;
			weightedHomophilyChance = expectedWeighted / weightTotal;
			int[] population = new int[listed.size()];
			int[] successes = new int[listed.size()];
			int[] draws = new int[listed.size()];
			for (int index = 0; index < listed.size(); index++)
			{
				int[] counts = graph.insideCounts(listed.get(index).intValue());
				population[index] = counts[1];
				successes[index] = counts[0];
				draws[index] = perTarget[listed.get(index).intValue()];
			}
			homophilyNull = NetworkStatistics.hypergeometricSum(population, successes, draws);
			homophilyP = homophilyNull.twoSided(insideRecommendations);
			weightedHomophilyP = randomListTest(population, successes, draws, weightInside);
		}

		/**
		 * Two-sided Monte Carlo p-value of a rank-weighted inside weight under
		 * random lists: each target's list is a uniformly random ordered draw,
		 * without replacement, from its candidates.
		 */
		private double randomListTest(int[] population, int[] successes, int[] draws, double observed)
		{
			SplittableRandom random = new SplittableRandom(SEED ^ 0x3C6EF372FE94F82BL);
			int maxDraws = 0;
			for (int value : draws)
			{
				maxDraws = Math.max(maxDraws, value);
			}
			double[] rankWeights = new double[maxDraws + 1];
			for (int rank = 1; rank <= maxDraws; rank++)
			{
				rankWeights[rank] = RunGraph.rankWeight(rank);
			}
			double tolerance = 1e-9 * Math.max(1.0, observed);
			long edges = 0;
			for (int value : draws)
			{
				edges += value;
			}
			listPermutations = edges > LARGE_LISTS ? LARGE_LIST_PERMUTATIONS : LIST_PERMUTATIONS;
			int atLeast = 0;
			int atMost = 0;
			for (int permutation = 0; permutation < listPermutations; permutation++)
			{
				double total = 0.0;
				for (int part = 0; part < population.length; part++)
				{
					int remaining = population[part];
					int inside = successes[part];
					int length = Math.min(draws[part], remaining);
					for (int rank = 1; rank <= length; rank++)
					{
						if (inside > 0 && random.nextInt(remaining) < inside)
						{
							total += rankWeights[rank];
							inside--;
						}
						remaining--;
					}
				}
				atLeast += total >= observed - tolerance ? 1 : 0;
				atMost += total <= observed + tolerance ? 1 : 0;
			}
			double upper = (1.0 + atLeast) / (1.0 + listPermutations);
			double lower = (1.0 + atMost) / (1.0 + listPermutations);
			return Math.min(1.0, 2.0 * Math.min(upper, lower));
		}

		/** The community a candidate counts for: an account's own, a user's followee's. */
		private int candidateCommunity(int node)
		{
			return graph.candidateType == RecommendationCandidateType.FOLLOWEE ? graph.anchoredCommunity(node)
					: graph.community(node);
		}

		/** Whether the recommendation network's communities are the real ones, with a permutation test. */
		private void recovery(int[] real)
		{
			NetworkAlgorithms.Undirected simple = undirected(EnumLayers.RECOMMEND, false);
			List<Integer> nodes = new ArrayList<Integer>();
			for (int node = 0; node < n; node++)
			{
				if (simple.degree(node) > 0 && real[node] >= 0)
				{
					nodes.add(Integer.valueOf(node));
				}
			}
			recoveryNodes = nodes.size();
			if (nodes.size() < 3 || Double.isNaN(recommended.recovery.ami))
			{
				return;
			}
			int[] truth = new int[nodes.size()];
			int[] detected = new int[nodes.size()];
			for (int index = 0; index < nodes.size(); index++)
			{
				truth[index] = real[nodes.get(index).intValue()];
				detected[index] = recommended.communities.membership[nodes.get(index).intValue()];
			}
			double observedAmi = recommended.recovery.ami;
			SplittableRandom random = new SplittableRandom(SEED ^ 0x9E3779B97F4A7C15L);
			int atLeast = 0;
			int[] shuffled = Arrays.copyOf(truth, truth.length);
			for (int permutation = 0; permutation < PERMUTATIONS; permutation++)
			{
				for (int index = shuffled.length - 1; index > 0; index--)
				{
					int swap = random.nextInt(index + 1);
					int value = shuffled[index];
					shuffled[index] = shuffled[swap];
					shuffled[swap] = value;
				}
				double ami = NetworkStatistics.agreement(shuffled, detected).ami;
				if (ami >= observedAmi - 1e-12)
				{
					atLeast++;
				}
			}
			recoveryPermutationP = (1.0 + atLeast) / (1.0 + PERMUTATIONS);
		}

		/**
		 * Who is recommended: each candidate's position-weighted exposure, the
		 * sum over the lists it appears in of 1 / log2(1 + rank), the
		 * attention model of Singh and Joachims (2018).
		 */
		private void exposure()
		{
			recommendedIn = new int[n];
			exposureByNode = new double[n];
			for (RunGraph.Edge edge : graph.edges(RunGraph.Layer.RECOMMEND))
			{
				recommendedIn[edge.target]++;
				exposureByNode[edge.target] += edge.weight();
			}
			adoptedIn = new int[n];
			for (RunGraph.Edge edge : graph.edges(RunGraph.Layer.ADOPT))
			{
				adoptedIn[edge.target]++;
			}
			followers = new int[n];
			for (RunGraph.Edge edge : graph.edges(RunGraph.Layer.FOLLOW))
			{
				followers[edge.target]++;
			}
			List<Integer> pool = new ArrayList<Integer>();
			for (int node = 0; node < n; node++)
			{
				if (graph.isCandidate(node))
				{
					pool.add(Integer.valueOf(node));
				}
			}
			exposure = new double[pool.size()];
			double[] communitySize = new double[pool.size()];
			int[] candidateSizes = graph.candidateType == RecommendationCandidateType.FOLLOWEE
					? graph.followerCounts() : graph.candidateCommunitySizes();
			int communities = graph.communityCount();
			communityExposureShare = new double[communities];
			communityCandidateShare = new double[communities];
			int labelledCandidates = 0;
			for (int index = 0; index < pool.size(); index++)
			{
				int node = pool.get(index).intValue();
				exposure[index] = exposureByNode[node];
				exposedCandidates += recommendedIn[node] > 0 ? 1 : 0;
				int community = candidateCommunity(node);
				communitySize[index] = community >= 0 ? candidateSizes[community] : 0.0;
				if (community >= 0)
				{
					communityCandidateShare[community] += 1.0;
					labelledCandidates++;
				}
			}
			double weightIntoCommunities = 0.0;
			for (RunGraph.Edge edge : graph.edges(RunGraph.Layer.RECOMMEND))
			{
				int community = candidateCommunity(edge.target);
				if (community >= 0)
				{
					communityExposureShare[community] += edge.weight();
					weightIntoCommunities += edge.weight();
				}
			}
			for (int community = 0; community < communities; community++)
			{
				communityCandidateShare[community] = labelledCandidates == 0 ? 0.0
						: communityCandidateShare[community] / labelledCandidates;
				communityExposureShare[community] = weightIntoCommunities <= 0.0 ? 0.0
						: communityExposureShare[community] / weightIntoCommunities;
			}
			if (pool.isEmpty() || graph.edges(RunGraph.Layer.RECOMMEND).isEmpty())
			{
				sizeBias = new NetworkStatistics.Correlation(Double.NaN, Double.NaN, pool.size());
				return;
			}
			coverage = exposedCandidates / (double)pool.size();
			exposureGini = NetworkStatistics.gini(exposure);
			exposureEntropy = NetworkStatistics.normalisedEntropy(exposure);
			exposureSkewness = NetworkStatistics.skewness(exposure);
			sizeBias = NetworkStatistics.spearman(exposure, communitySize);
		}

		/** The adopted edges: inside or bridging, against random adoption; the network before and after. */
		private void adoption(int[] real)
		{
			int communities = graph.communityCount();
			adoptionMixing = new int[communities][communities];
			List<RunGraph.Edge> edges = graph.edges(RunGraph.Layer.ADOPT);
			List<Double> chances = new ArrayList<Double>();
			HashSet<Long> pairs = new HashSet<Long>();
			for (RunGraph.Edge edge : edges)
			{
				int own = real[edge.source];
				if (own < 0)
				{
					continue;
				}
				int other = candidateCommunity(edge.target);
				adoptionsClassified++;
				if (other == own)
				{
					adoptionsInside++;
				}
				else if (other >= 0)
				{
					pairs.add(Long.valueOf((long)Math.min(own, other) * communities + Math.max(own, other)));
				}
				if (other >= 0)
				{
					adoptionMixing[own][other]++;
				}
				double chance = graph.insideChance(edge.source);
				chances.add(Double.valueOf(Double.isNaN(chance) ? 0.0 : chance));
			}
			bridgedPairs = pairs.size();
			mergedComponents = Math.max(0, observed.components - adopted.components);
			if (adoptionsClassified == 0)
			{
				return;
			}
			int external = adoptionsClassified - adoptionsInside;
			eiIndex = (external - adoptionsInside) / (double)adoptionsClassified;
			double[] p = new double[chances.size()];
			double expectedInside = 0.0;
			for (int index = 0; index < p.length; index++)
			{
				p[index] = chances.get(index).doubleValue();
				expectedInside += p[index];
			}
			eiChance = (adoptionsClassified - 2.0 * expectedInside) / adoptionsClassified;
			adoptionNull = NetworkStatistics.poissonBinomial(p);
			adoptionP = adoptionNull.twoSided(adoptionsInside);
		}

		/** Node centralities on the whole network and roles in its communities. */
		private void nodes()
		{
			pageRank = NetworkAlgorithms.pageRank(directed(EnumLayers.ALL), DAMPING, 1e-12, 10000);
			pageRankObserved = NetworkAlgorithms.pageRank(directed(EnumLayers.FOLLOW), DAMPING, 1e-12, 10000);
			NetworkAlgorithms.Undirected simple = undirected(EnumLayers.ALL, false);
			betweenness = NetworkAlgorithms.betweenness(simple, EXACT_BETWEENNESS_LIMIT, BETWEENNESS_PIVOTS, SEED);
			core = NetworkAlgorithms.coreNumbers(simple);
			participation = NetworkAlgorithms.participation(simple, full.communities.membership);
			withinModuleZ = NetworkAlgorithms.withinModuleDegree(simple, full.communities.membership);
			roles = new String[n];
			degreeFull = new int[n];
			for (int node = 0; node < n; node++)
			{
				degreeFull[node] = simple.degree(node);
				roles[node] = degreeFull[node] == 0 ? "Isolated"
						: NetworkAlgorithms.role(participation[node], withinModuleZ[node]);
			}
		}

		/** The plain-language statements the Graph Insights overview leads with. */
		private void findings()
		{
			boolean accounts = graph.candidateType == RecommendationCandidateType.FOLLOWEE;
			if (graph.edges(RunGraph.Layer.RECOMMEND).isEmpty())
			{
				warnings.add("The run produced no recommendation edges, so only the observed network is analysed.");
				return;
			}
			if (graph.targetCount() < 10)
			{
				warnings.add("Only " + graph.targetCount() + " target(s): the statistics below have little power;"
						+ " choose more targets (Simulator, step 3) for firm conclusions.");
			}
			if (graph.communityCount() < 2)
			{
				warnings.add("The users follow a single account, so communities cannot be compared.");
			}
			if (!Double.isNaN(homophily))
			{
				String what = accounts ? "go to the target's own account" : "stay inside the target's community";
				boolean significant;
				boolean above;
				String detail;
				if (saturated)
				{
					significant = weightedHomophilyP < 0.05;
					above = weightedHomophily > weightedHomophilyChance;
					detail = String.format(Locale.US, "Every list holds all of its candidates (k = %d), so only the"
							+ " ranking varies. Weighted by rank, %.1f%% of the recommendation weight goes %s;"
							+ " random rankings would give %.1f%%, %s (permutation test, %,d random lists).",
							graph.cutoff, 100.0 * weightedHomophily, accounts ? "to the target's own account"
									: "inside the target's community", 100.0 * weightedHomophilyChance,
							NetworkStatistics.pText(weightedHomophilyP), listPermutations);
					warnings.add("Each target's list contains every candidate (k = " + graph.cutoff + " is at least the"
							+ " " + graph.candidateCount() + " candidates), so unweighted homophily and exposure equal"
							+ " chance by construction; the rank-weighted measures carry the signal.");
				}
				else
				{
					significant = homophilyP < 0.05;
					above = homophily > homophilyChance;
					detail = String.format(Locale.US, "%d of %d recommendation edges %s (%.1f%%); random lists"
							+ " would give %.1f%%, %s (exact test). Weighted by rank: %.1f%% against %.1f%%, %s.",
							insideRecommendations, classifiedRecommendations, what, 100.0 * homophily,
							100.0 * homophilyChance, NetworkStatistics.pText(homophilyP), 100.0 * weightedHomophily,
							100.0 * weightedHomophilyChance, NetworkStatistics.pText(weightedHomophilyP));
				}
				findings.add(new Finding(!significant ? "Recommendations ignore community structure"
						: above ? "Recommendations favour the target's community"
								: "Recommendations avoid the target's community", detail,
						!significant ? "warning" : above ? "good" : "bad"));
			}
			if (!Double.isNaN(assortativity.r))
			{
				findings.add(new Finding(String.format(Locale.US, "Assortativity r = %.3f \u00b1 %.3f",
						assortativity.r, assortativity.standardError),
						"Newman's assortativity of the rank-weighted recommendation edges by community: 1 when every"
								+ " recommendation stays in its community, 0 when communities mix as their sizes"
								+ " predict, negative when they avoid each other ("
								+ NetworkStatistics.pText(assortativity.pValue()) + ", jackknife error).",
						assortativity.pValue() < 0.05 && assortativity.r > 0 ? "good"
								: assortativity.pValue() < 0.05 ? "bad" : "neutral"));
			}
			if (!Double.isNaN(recommended.recovery.ami) && recoveryNodes >= 3)
			{
				findings.add(new Finding(String.format(Locale.US, "Community recovery AMI = %.3f",
						recommended.recovery.ami), String.format(Locale.US, "Leiden finds %d communities in the"
						+ " recommendation network (modularity %.3f); their adjusted mutual information with the real"
						+ " communities is %.3f (1 identical, 0 chance; permutation %s; ARI %.3f).",
						recommended.activeCommunities, recommended.communities.quality,
						recommended.recovery.ami, NetworkStatistics.pText(recoveryPermutationP),
						recommended.recovery.ari),
						recoveryPermutationP < 0.05 && recommended.recovery.ami > 0.0 ? "good" : "neutral"));
			}
			if (!Double.isNaN(exposureGini))
			{
				findings.add(new Finding(String.format(Locale.US, "Exposure Gini = %.3f, coverage %.1f%%",
						exposureGini, 100.0 * coverage), String.format(Locale.US, "%d of %d candidates are"
						+ " recommended at least once. Exposure weighs each appearance by its rank; a Gini near 1"
						+ " means a few candidates receive most of it (skewness %.2f).", exposedCandidates,
						exposure.length, exposureSkewness),
						exposureGini > 0.8 ? "warning" : "neutral"));
			}
			if (!Double.isNaN(eiIndex))
			{
				boolean significant = adoptionP < 0.05;
				findings.add(new Finding(eiIndex < eiChance && significant ? "Adoptions reinforce communities"
						: eiIndex > eiChance && significant ? "Adoptions bridge communities"
								: "Adoptions mix communities as chance would",
						String.format(Locale.US, "E-I index %.3f (random adoption: %.3f, %s); %d of %d adopted"
								+ " follows stay inside; the real communities' modularity goes from %.3f to %.3f.",
								eiIndex, eiChance, NetworkStatistics.pText(adoptionP), adoptionsInside,
								adoptionsClassified, observed.realModularity, adopted.realModularity),
						"neutral"));
			}
		}
	}
}
