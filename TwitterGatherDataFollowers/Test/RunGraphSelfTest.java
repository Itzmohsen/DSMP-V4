package TwitterGatherDataFollowers.userRyersonU;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;

/**
 * The run graph: built from what a run was evaluated against and its merged
 * lists, its adoptions are the user agents' own choices, its homophily is the
 * evaluator's precision with the same chance baseline, and its record reads
 * back identically, in well-formed GEXF, GraphML and SVG; records written
 * before run graphs existed are rebuilt from their files.
 */
public final class RunGraphSelfTest
{
	private RunGraphSelfTest()
	{
	}

	public static void main(String[] args) throws Exception
	{
		//Figures are drawn off screen; the test must not wait on a display.
		System.setProperty("java.awt.headless", "true");
		RecommendationQualityEvaluator.Setup setup = userSetup();
		Map<String,List<RecommendationRunResults.Entry>> results = userResults();
		RunGraph graph = RunGraph.of(setup, results, "sample.txt");
		nodesAndLayers(graph);
		adoptionsAreTheUserAgentsChoices(graph, results);
		RunGraphAnalysis analysis = RunGraphAnalysis.of(graph);
		homophilyIsTheEvaluatorsPrecision(setup, results, analysis);
		recordRoundTrip(graph, analysis);
		accountListsHoldingEveryAccountAreSaturated();
		olderRecordsAreRebuilt();
		System.out.println("RunGraphSelfTest passed");
	}

	/** Three communities (StoreA, StoreB, StoreC); StoreC is also a user who follows StoreA; u0 has no followee. */
	static RecommendationQualityEvaluator.Setup userSetup()
	{
		LinkedHashMap<String,String> labels = new LinkedHashMap<String,String>();
		String[][] members = {{"StoreA", "a1", "a2", "a3", "a4", "a5", "a6"}, {"StoreB", "b1", "b2", "b3", "b4", "b5"},
			{"StoreC", "c1", "c2", "c3", "c4"}};
		List<String> users = new ArrayList<String>();
		for (String[] community : members)
		{
			for (int index = 1; index < community.length; index++)
			{
				labels.put(community[index], community[0]);
				users.add(community[index]);
			}
		}
		labels.put("StoreC", "StoreA");
		users.add("StoreC");
		users.add("u0");
		return new RecommendationQualityEvaluator.Setup("run-graph-test", "Similarity", "Algorithm Default",
				RecommendationCandidateType.USER, 3, Arrays.asList("a1", "a2", "b1", "c1"), users, labels);
	}

	static Map<String,List<RecommendationRunResults.Entry>> userResults()
	{
		Map<String,List<RecommendationRunResults.Entry>> results = new LinkedHashMap<String,List<RecommendationRunResults.Entry>>();
		results.put("a1", list("a2", 0.9, "a3", 0.8, "b2", 0.5, "c2", 0.3));
		results.put("a2", list("b3", 0.95, "a4", 0.7, "u0", 0.6, "a5", 0.2));
		results.put("b1", list("b2", 0.6, "b3", 0.59, "StoreC", 0.4));
		results.put("c1", list("a6", 0.8, "c3", 0.75, "c4", 0.1, "b5", 0.05));
		return results;
	}

	static List<RecommendationRunResults.Entry> list(Object... pairs)
	{
		List<RecommendationRunResults.Entry> entries = new ArrayList<RecommendationRunResults.Entry>();
		for (int index = 0; index < pairs.length; index += 2)
		{
			entries.add(new RecommendationRunResults.Entry((String)pairs[index], ((Double)pairs[index + 1]).doubleValue()));
		}
		return entries;
	}

	private static void nodesAndLayers(RunGraph graph)
	{
		//15 members, StoreC (both), u0, StoreA, StoreB.
		require(graph.size() == 19, "nodes: " + graph.describe());
		require(graph.kind(graph.indexOf("StoreC")) == RunGraph.Kind.BOTH, "StoreC is a user and an account");
		require(graph.kind(graph.indexOf("StoreA")) == RunGraph.Kind.ACCOUNT, "StoreA is an account");
		require(graph.communityCount() == 3, "three real communities");
		require(graph.communityName(graph.community(graph.indexOf("a1"))).equals("StoreA"), "a1 follows StoreA");
		require(graph.communityName(graph.community(graph.indexOf("StoreC"))).equals("StoreA"),
				"StoreC, as a user, belongs to the community it follows");
		require(graph.communityName(graph.anchoredCommunity(graph.indexOf("StoreC"))).equals("StoreC"),
				"StoreC anchors its own community");
		require(graph.community(graph.indexOf("u0")) < 0, "u0's community is unknown");
		require(graph.edges(RunGraph.Layer.FOLLOW).size() == 16, "16 labelled users follow an account");
		require(graph.edges(RunGraph.Layer.RECOMMEND).size() == 12, "top-3 of four targets");
		require(graph.edges(RunGraph.Layer.ADOPT).size() == 4, "one adoption per target");
		require(graph.targetCount() == 4 && graph.candidateCount() == 17, "targets and candidates");
		RunGraph.Edge first = graph.recommendationsOf(graph.indexOf("b1")).get(2);
		require(graph.name(first.target).equals("StoreC") && first.rank == 3, "ranks follow the list");
		require(Boolean.FALSE.equals(graph.inside(first)), "b1 -> StoreC crosses to StoreA's community");
		int[] counts = graph.insideCounts(graph.indexOf("a1"));
		//a1's candidates: the other 16 users; inside: a2..a6 and StoreC (6).
		require(counts[0] == 6 && counts[1] == 16, "a1's chance: " + Arrays.toString(counts));
	}

	private static void adoptionsAreTheUserAgentsChoices(RunGraph graph,
			Map<String,List<RecommendationRunResults.Entry>> results) throws Exception
	{
		File important = Files.createTempDirectory("run-graph-post").toFile();
		for (Map.Entry<String,List<RecommendationRunResults.Entry>> row : results.entrySet())
		{
			LinkedHashMap<String,Double> scores = new LinkedHashMap<String,Double>();
			for (RecommendationRunResults.Entry entry : row.getValue())
			{
				scores.put(entry.getCandidate(), Double.valueOf(entry.getScore()));
			}
			RecommendationPostProcessor.Result chosen = RecommendationPostProcessor.process(
					new RecommendationPostProcessor.Request(null, important, row.getKey(), "StoreA", "", scores, 3,
							BatchRecommendationSelector.adoptionRandom(row.getKey()), null));
			RunGraph.Edge adoption = graph.adoptionOf(graph.indexOf(row.getKey()));
			require(graph.name(adoption.target).equals(chosen.getSelectedRecommendation())
					&& adoption.rank == chosen.getSelectedRecommendationIndex() + 1,
					row.getKey() + " adopted " + graph.name(adoption.target) + " but the user agent chose "
							+ chosen.getSelectedRecommendation());
		}
	}

	private static void homophilyIsTheEvaluatorsPrecision(RecommendationQualityEvaluator.Setup setup,
			Map<String,List<RecommendationRunResults.Entry>> results, RunGraphAnalysis analysis)
	{
		RecommendationQualityEvaluator.Report report = RecommendationQualityEvaluator.evaluate(setup, results);
		RecommendationQualityEvaluator.MetricSummary precision = report.metric("precision");
		//Every list holds k = 3 edges, so the micro share equals the mean precision@3.
		close(analysis.homophily, precision.mean, 1e-12, "homophily is precision@k");
		close(analysis.homophilyChance, precision.random, 1e-12, "the same chance baseline");
		require(analysis.insideRecommendations == 7 && analysis.classifiedRecommendations == 12,
				"7 of 12 edges inside: " + analysis.insideRecommendations);
		require(!analysis.saturated, "lists do not hold every candidate");
		require(analysis.homophilyNull.exact() && analysis.homophilyP > 0.0 && analysis.homophilyP <= 1.0,
				"an exact p-value");
		require(analysis.findings.size() >= 3, "findings are written");
		require(analysis.warnings.size() >= 1, "few targets are flagged");
		//The adoption chance of each target is its share of same-community candidates.
		close(analysis.eiChance, (4 - 2.0 * (6.0 / 16 + 6.0 / 16 + 4.0 / 16 + 3.0 / 16)) / 4.0, 1e-12, "E-I chance");
	}

	private static void recordRoundTrip(RunGraph graph, RunGraphAnalysis analysis) throws Exception
	{
		File run = Files.createTempDirectory("run-graph-record").toFile();
		RunGraphRecord.write(run, graph, true);
		File folder = new File(run, RunGraphRecord.FOLDER);
		for (String name : new String[] {"graph.json", "nodes.csv", "edges.csv", "graph.gexf", "graph.graphml",
			"graph.svg", "graph.png"})
		{
			require(new File(folder, name).isFile() && new File(folder, name).length() > 0, name + " written");
		}
		require(RunGraphRecord.latest() != null && RunGraphRecord.latest().graph == graph, "the latest run graph");
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setNamespaceAware(true);
		Document gexf = factory.newDocumentBuilder().parse(new File(folder, "graph.gexf"));
		require(gexf.getElementsByTagNameNS("http://www.gexf.net/1.2draft", "node").getLength() == graph.size(),
				"GEXF nodes");
		require(gexf.getElementsByTagNameNS("http://www.gexf.net/1.2draft", "edge").getLength()
				== graph.allEdges().size(), "GEXF edges");
		require(gexf.getElementsByTagNameNS("http://www.gexf.net/1.2draft/viz", "position").getLength()
				== graph.size(), "GEXF positions");
		Document graphml = factory.newDocumentBuilder().parse(new File(folder, "graph.graphml"));
		require(graphml.getElementsByTagNameNS("http://graphml.graphdrawing.org/xmlns", "edge").getLength()
				== graph.allEdges().size(), "GraphML edges");
		Document svg = factory.newDocumentBuilder().parse(new File(folder, "graph.svg"));
		require(svg.getDocumentElement().getLocalName().equals("svg"), "SVG document");
		Object json = JsonText.read(new File(folder, "graph.json"));
		close(JsonText.number(json, "homophily.fraction"), analysis.homophily, 1e-8, "graph.json homophily");
		require("user".equals(JsonText.string(json, "candidateType")) && JsonText.number(json, "k") == 3.0,
				"graph.json header");
		RunGraphRecord.clearCache();
		RunGraphRecord.Loaded loaded = RunGraphRecord.load(run);
		require(!loaded.reconstructed && loaded.hasLayout(), "read back with its layout");
		RunGraph read = loaded.graph;
		require(read.size() == graph.size() && read.allEdges().size() == graph.allEdges().size()
				&& read.targetCount() == graph.targetCount() && read.candidateCount() == graph.candidateCount(),
				"the same graph: " + read.describe());
		for (int node = 0; node < graph.size(); node++)
		{
			require(read.name(node).equals(graph.name(node)) && read.kind(node) == graph.kind(node)
					&& read.community(node) == graph.community(node), "node " + graph.name(node));
		}
		for (int index = 0; index < graph.edges(RunGraph.Layer.ADOPT).size(); index++)
		{
			RunGraph.Edge a = graph.edges(RunGraph.Layer.ADOPT).get(index);
			RunGraph.Edge b = read.edges(RunGraph.Layer.ADOPT).get(index);
			require(a.source == b.source && a.target == b.target && a.rank == b.rank, "adoption " + index);
		}
		close(loaded.analysis.homophily, analysis.homophily, 0.0, "homophily read back");
		close(loaded.analysis.assortativity.r, analysis.assortativity.r, 1e-15, "assortativity read back");
		close(loaded.analysis.recommended.recovery.ami, analysis.recommended.recovery.ami, 1e-15, "AMI read back");
		close(loaded.analysis.exposureGini, analysis.exposureGini, 1e-15, "Gini read back");
		RunGraphAnalysis.GraphLayoutResult layout = RunGraphRecord.layout(graph, analysis);
		for (int node = 0; node < graph.size(); node++)
		{
			close(loaded.x[node], layout.x[node], 0.0, "the layout is reproducible");
		}
	}

	private static void accountListsHoldingEveryAccountAreSaturated()
	{
		LinkedHashMap<String,String> labels = new LinkedHashMap<String,String>();
		List<String> users = new ArrayList<String>();
		String[] accounts = {"Alpha", "Beta", "Gamma"};
		for (int user = 0; user < 12; user++)
		{
			labels.put("user" + user, accounts[user % 3]);
			users.add("user" + user);
		}
		RecommendationQualityEvaluator.Setup setup = new RecommendationQualityEvaluator.Setup("accounts", "SVM",
				"Algorithm Default", RecommendationCandidateType.FOLLOWEE, 5, users.subList(0, 6), users, labels);
		Map<String,List<RecommendationRunResults.Entry>> results = new LinkedHashMap<String,List<RecommendationRunResults.Entry>>();
		for (int user = 0; user < 6; user++)
		{
			String own = accounts[user % 3];
			List<String> order = new ArrayList<String>(Arrays.asList(accounts));
			order.remove(own);
			order.add(0, own);
			results.put("user" + user, list(order.get(0), 0.9, order.get(1), 0.5, order.get(2), 0.1));
		}
		RunGraphAnalysis analysis = RunGraphAnalysis.of(RunGraph.of(setup, results, "accounts.txt"));
		require(analysis.saturated, "every list holds all three accounts");
		close(analysis.homophily, 1.0 / 3.0, 1e-15, "unweighted homophily is chance by construction");
		close(analysis.homophilyChance, 1.0 / 3.0, 1e-15, "chance is one account in three");
		require(analysis.weightedHomophily > analysis.weightedHomophilyChance && analysis.weightedHomophilyP < 0.05,
				"ranking the own account first shows in the rank-weighted test: p = " + analysis.weightedHomophilyP);
		require(analysis.findings.get(0).detail.contains("only the ranking varies"), "the saturated finding");
	}

	private static void olderRecordsAreRebuilt() throws Exception
	{
		File run = Files.createTempDirectory("run-graph-old").toFile();
		File dataset = new File(run, "old.txt");
		write(dataset, "\uFEFFStoreA\t1\t2020-01-01\t10\ta1\talpha text\nStoreA\t2\t2020-01-01\t10\ta2\tbeta text\n"
				+ "StoreB\t3\t2020-01-01\t11\tb1\tgamma text\nStoreB\t4\t2020-01-01\t11\tb2\tdelta text\n");
		write(new File(run, "manifest.json"), "{\"runId\": \"old-run\", \"properties\": {\"algorithm\": \"K-Means\","
				+ " \"datasetName\": \"old.txt\", \"dataset\": " + JsonText.of(dataset.getAbsolutePath()).trim() + "}}\n");
		write(new File(run, "quality.json"), "{\"candidateType\": \"user\", \"k\": 2}\n");
		write(new File(run, "recommendations.csv"), "target,target_followee,rank,candidate,candidate_followee,score,"
				+ "relevant\na1,StoreA,1,a2,StoreA,0.9,1\na1,StoreA,2,b1,StoreB,0.4,0\na1,StoreA,3,b2,StoreB,0.1,0\n");
		RunGraphRecord.Loaded loaded = RunGraphRecord.load(run);
		require(loaded != null && loaded.reconstructed && !loaded.hasLayout(), "rebuilt without a layout");
		require(loaded.graph.size() == 6 && loaded.graph.edges(RunGraph.Layer.RECOMMEND).size() == 2,
				"every dataset user, top-2 edges: " + loaded.graph.describe());
		require("old-run".equals(loaded.graph.runId) && loaded.graph.cutoff == 2, "the record's identity");
		require(loaded.graph.indexOf("StoreA") >= 0, "the byte-order mark is not part of the first account");
	}

	private static void write(File file, String text) throws Exception
	{
		Files.write(file.toPath(), text.getBytes(StandardCharsets.UTF_8));
	}

	static void close(double actual, double expected, double tolerance, String label)
	{
		if (!(Math.abs(actual - expected) <= tolerance))
		{
			throw new AssertionError(label + ": expected " + expected + " but was " + actual);
		}
	}

	static void require(boolean condition, String message)
	{
		if (!condition)
		{
			throw new AssertionError(message);
		}
	}
}
