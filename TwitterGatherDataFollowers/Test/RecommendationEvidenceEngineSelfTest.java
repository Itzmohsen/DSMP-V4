package TwitterGatherDataFollowers.userRyersonU;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Evidence Studio's engine: its TF-IDF is the agents', its references
 * reproduce Similarity, Common Neighbors and the account projection exactly,
 * its decompositions add up, its counterfactuals are exact, its fidelity
 * measures have their known values, and it keeps the run graph's context and
 * reads recorded runs.
 */
public final class RecommendationEvidenceEngineSelfTest
{
	private static final String[][] USERS = {
		{"Alice", "StoreA", "graph neural recommender model|neural ranking evidence|graph ranking model study"},
		{"Bob", "StoreB", "graph neural model ranking|neural graph study notes|ranking model evidence"},
		{"Carol", "StoreC", "fresh fruit market|fruit market prices today|fresh bread market"},
		{"Dave", "StoreA", "neural model training|graph model study|recommender evidence notes"},
		{"Erin", "StoreC", "market prices fruit|fresh fruit bread|bread market morning"},
		{"Frank", "StoreB", "graph recommender ranking|ranking study notes|neural notes model"}};

	private RecommendationEvidenceEngineSelfTest()
	{
	}

	public static void main(String[] args) throws Exception
	{
		File directory = Files.createTempDirectory("evidence-engine").toFile();
		System.setProperty("dsmp.runCorpus.dir", new File(directory, "run-corpus").getPath());
		File corpusFile = corpus(new File(directory, "run-corpus"));
		EvidenceCorpus corpus = EvidenceCorpus.of(corpusFile);
		tfidfIsTheAgents(corpus);
		measures();
		similarityIsExact(corpus, corpusFile);
		linkPredictionIsExact(corpus);
		accountsAreProjectedAndCentred(corpus, corpusFile);
		runGraphContext(corpusFile);
		recordedRunsLoad(directory, corpusFile);
		System.out.println("RecommendationEvidenceEngineSelfTest passed");
	}

	/** The corpus's TF-IDF is raw tf times log2(N / df), L2-normalised, recomputed here independently. */
	private static void tfidfIsTheAgents(EvidenceCorpus corpus)
	{
		int n = USERS.length;
		HashMap<String,Integer> df = new HashMap<String,Integer>();
		List<Map<String,Integer>> counts = new ArrayList<Map<String,Integer>>();
		for (String[] user : USERS)
		{
			TreeMap<String,Integer> map = new TreeMap<String,Integer>();
			for (String tweet : user[2].split("\\|"))
			{
				for (String token : tweet.split(" "))
				{
					map.put(token, Integer.valueOf(map.containsKey(token) ? map.get(token).intValue() + 1 : 1));
				}
			}
			for (String term : map.keySet())
			{
				df.put(term, Integer.valueOf(df.containsKey(term) ? df.get(term).intValue() + 1 : 1));
			}
			counts.add(map);
		}
		for (int i = 0; i < n; i++)
		{
			int u = corpus.index.get(USERS[i][0]).intValue();
			double norm = 0.0;
			HashMap<String,Double> raw = new HashMap<String,Double>();
			for (Map.Entry<String,Integer> entry : counts.get(i).entrySet())
			{
				double w = entry.getValue().intValue() * (Math.log(n / (double)df.get(entry.getKey()).intValue())
						/ Math.log(2));
				raw.put(entry.getKey(), Double.valueOf(w));
				norm += w * w;
			}
			for (Map.Entry<String,Double> entry : raw.entrySet())
			{
				double expected = entry.getValue().doubleValue() / Math.sqrt(norm);
				double actual = corpus.weightOf(u, corpus.termIndex.get(entry.getKey()).intValue());
				require(Math.abs(expected - actual) < 1e-12, "TF-IDF of " + entry.getKey() + " for " + USERS[i][0]
						+ ": " + actual + " instead of " + expected);
			}
		}
	}

	private static void measures()
	{
		List<String> a = Arrays.asList("a", "b", "c", "d");
		require(Math.abs(RecommendationEvidenceEngine.rbo(a, a, 0.9) - 1.0) < 1e-12, "RBO of a list with itself");
		require(RecommendationEvidenceEngine.rbo(a, Arrays.asList("w", "x", "y", "z"), 0.9) == 0.0, "RBO of disjoint lists");
		//Swapping the first two: X = 0, 2, 3, 4 at depths 1..4.
		double expected = 1.0 * Math.pow(0.9, 4) + (0.1 / 0.9) * (0.0 * 0.9 + 1.0 * 0.81 + 1.0 * 0.729 + 1.0 * 0.6561);
		double swapped = RecommendationEvidenceEngine.rbo(a, Arrays.asList("b", "a", "c", "d"), 0.9);
		require(Math.abs(swapped - expected) < 1e-12, "RBO of a swap: " + swapped + " instead of " + expected);
		require(Math.abs(RecommendationEvidenceEngine.kendallTauB(new double[] {1, 2, 3}, new double[] {1, 2, 3}) - 1.0)
				< 1e-12, "tau of the same order");
		require(Math.abs(RecommendationEvidenceEngine.kendallTauB(new double[] {1, 2, 3}, new double[] {3, 2, 1}) + 1.0)
				< 1e-12, "tau of reversed orders");
		//Ties: x = (1, 1, 2), y = (1, 2, 3): one concordant-in-y-only tie, tau-b = 2 / sqrt(2 * 3).
		require(Math.abs(RecommendationEvidenceEngine.kendallTauB(new double[] {1, 1, 2}, new double[] {1, 2, 3})
				- 2.0 / Math.sqrt(6.0)) < 1e-12, "tau-b with a tie");
	}

	/** A Similarity run: the reference reproduces the delivered scores, and every number adds up. */
	private static void similarityIsExact(EvidenceCorpus corpus, File corpusFile)
	{
		RecommendationEvidenceEngine.EvidenceRun run = new RecommendationEvidenceEngine.EvidenceRun("sim-run",
				RecommendationModeSupport.COS_SIM, RecommendationCandidateType.USER, "sample.txt", corpusFile, null, 3,
				null);
		int u = corpus.index.get("Alice").intValue();
		double[] cosine = corpus.cosines(u);
		List<String> order = ranked(corpus, cosine, u);
		ArrayList<RecommendationEvidenceEngine.MatchEvidence> matches =
				new ArrayList<RecommendationEvidenceEngine.MatchEvidence>();
		double top = cosine[corpus.index.get(order.get(0)).intValue()];
		for (int i = 0; i < 3; i++)
		{
			double score = 100.0 * cosine[corpus.index.get(order.get(i)).intValue()] / top;
			matches.add(new RecommendationEvidenceEngine.MatchEvidence(i + 1, order.get(i), Double.toString(score), ""));
		}
		run.add(new RecommendationEvidenceEngine.RecommendationEvent("sim-run", "Alice", RecommendationCandidateType.USER,
				RecommendationResultStatus.OK, "Similarity", "", matches));
		RecommendationEvidenceEngine.Explanation x = RecommendationEvidenceEngine.explain(run, "Alice", 0, null, null);
		require(x.textAvailable && x.exact, "Similarity's reference must be exact");
		require(x.candidate.equals(order.get(0)) && x.referenceRank == 1, "the first recommendation ranks first");
		require(x.reproductionError < 1e-9, "the delivered scores are not reproduced: " + x.reproductionError);
		require(Math.abs(x.rbo - 1.0) < 1e-12 && x.overlap == 1.0, "the reference list must be the delivered list");
		double sum = 0.0;
		double shares = 0.0;
		for (RecommendationEvidenceEngine.TermContribution term : x.terms)
		{
			sum += term.contribution;
			shares += term.share;
		}
		require(x.sharedTerms == x.terms.size(), "this small example keeps every shared term");
		require(Math.abs(sum - x.referenceScore) < 1e-12, "term contributions " + sum + " do not sum to the cosine "
				+ x.referenceScore);
		require(Math.abs(shares - 1.0) < 1e-12, "term shares do not sum to one");
		//A removal is exact: the cosine recomputed with the word removed from Alice's profile.
		RecommendationEvidenceEngine.Removal removal = x.removals.get(0);
		int b = corpus.index.get(x.candidate).intValue();
		int t = corpus.termIndex.get(removal.term).intValue();
		double dot = 0.0;
		double norm = 0.0;
		for (int p = corpus.pointer[u]; p < corpus.pointer[u + 1]; p++)
		{
			if (corpus.column[p] == t)
			{
				continue;
			}
			norm += corpus.weight[p] * corpus.weight[p];
			dot += corpus.weight[p] * corpus.weightOf(b, corpus.column[p]);
		}
		double expected = dot / Math.sqrt(norm);
		require(Math.abs(removal.score - expected) < 1e-12, "removing '" + removal.term + "' gives " + removal.score
				+ " instead of " + expected);
		require(!x.targetQuotes.isEmpty() && !x.candidateQuotes.isEmpty(), "both users' tweets should be quoted");
		require(x.relevant != null, "the corpus records both follows");
		RecommendationEvidenceEngine.RunAnalysis analysis = RecommendationEvidenceEngine.analyzeRun(run);
		require(analysis.exact && Math.abs(analysis.meanRbo - 1.0) < 1e-12, "the run's mean RBO must be 1");
		require(analysis.calibration[0][1] == 1.0, "one first-rank recommendation");
	}

	/** Every Common Neighbors variant matches SparseRecommendationMath exactly. */
	private static void linkPredictionIsExact(EvidenceCorpus corpus)
	{
		LinkedHashMap<String,Integer> df = new LinkedHashMap<String,Integer>();
		for (int t = 0; t < corpus.terms.length; t++)
		{
			df.put(corpus.terms[t], Integer.valueOf(corpus.documentFrequency[t]));
		}
		int u = corpus.index.get("Alice").intValue();
		for (String variant : new String[] {AlgorithmParameterSettings.CN_COMMON_NEIGHBOURS,
			AlgorithmParameterSettings.CN_JACCARD, AlgorithmParameterSettings.CN_ADAMIC_ADAR,
			AlgorithmParameterSettings.CN_RESOURCE_ALLOCATION})
		{
			RecommendationEvidenceEngine.LinkScorer scorer = new RecommendationEvidenceEngine.LinkScorer(corpus, u, variant);
			double[] scores = scorer.scores();
			for (int v = 0; v < corpus.size(); v++)
			{
				if (v == u)
				{
					continue;
				}
				double expected = SparseRecommendationMath.linkPredictionScore(vector(corpus, u), vector(corpus, v), df,
						variant);
				require(Math.abs(scores[v] - expected) < 1e-12, variant + " score of " + corpus.users[v] + ": "
						+ scores[v] + " instead of " + expected);
				double sum = 0.0;
				for (RecommendationEvidenceEngine.TermContribution term : scorer.contributions(v))
				{
					sum += term.contribution;
				}
				require(Math.abs(sum - expected) < 1e-12, variant + " contributions do not add up for " + corpus.users[v]);
			}
		}
	}

	/** Accounts: a user ranker's account scores its best follower; a classifier's reference is the centroid. */
	private static void accountsAreProjectedAndCentred(EvidenceCorpus corpus, File corpusFile)
	{
		int u = corpus.index.get("Alice").intValue();
		RecommendationEvidenceEngine.EvidenceRun projected = new RecommendationEvidenceEngine.EvidenceRun("proj-run",
				RecommendationModeSupport.COS_SIM, RecommendationCandidateType.FOLLOWEE, "sample.txt", corpusFile, null,
				2, null);
		RecommendationEvidenceEngine.Scorer projection = RecommendationEvidenceEngine.Scorer.of(projected, corpus, u, true);
		require(projection.exact && projection.reference == RecommendationEvidenceEngine.Reference.PROJECTION,
				"a Similarity run's accounts are projected exactly");
		double[] cosine = corpus.cosines(u);
		double[] scores = projection.scores();
		for (int a = 0; a < projection.size(); a++)
		{
			double best = 0.0;
			String bestUser = null;
			for (int v = 0; v < corpus.size(); v++)
			{
				if (v != u && corpus.followee[v].equals(projection.name(a)) && cosine[v] > best)
				{
					best = cosine[v];
					bestUser = corpus.users[v];
				}
			}
			require(Math.abs(scores[a] - best) < 1e-12, "projection of " + projection.name(a));
			require(bestUser == null || bestUser.equals(projection.explainingUser(a)), "the explaining follower");
		}
		RecommendationEvidenceEngine.EvidenceRun classified = new RecommendationEvidenceEngine.EvidenceRun("svm-run",
				RecommendationModeSupport.SVM, RecommendationCandidateType.FOLLOWEE, "sample.txt", corpusFile, null, 2,
				null);
		RecommendationEvidenceEngine.Scorer centroid = RecommendationEvidenceEngine.Scorer.of(classified, corpus, u, true);
		require(!centroid.exact && centroid.reference == RecommendationEvidenceEngine.Reference.CENTROID,
				"a classifier's accounts are explained by their centroids, with measured fidelity");
		double[] centroidScores = centroid.scores();
		for (int a = 0; a < centroid.size(); a++)
		{
			HashMap<Integer,Double> sum = new HashMap<Integer,Double>();
			for (int v = 0; v < corpus.size(); v++)
			{
				if (v == u || !corpus.followee[v].equals(centroid.name(a)))
				{
					continue;
				}
				for (int p = corpus.pointer[v]; p < corpus.pointer[v + 1]; p++)
				{
					Integer t = Integer.valueOf(corpus.column[p]);
					sum.put(t, Double.valueOf((sum.containsKey(t) ? sum.get(t).doubleValue() : 0.0) + corpus.weight[p]));
				}
			}
			double norm = 0.0;
			for (double value : sum.values())
			{
				norm += value * value;
			}
			double dot = 0.0;
			for (int p = corpus.pointer[u]; p < corpus.pointer[u + 1]; p++)
			{
				Double c = sum.get(Integer.valueOf(corpus.column[p]));
				dot += c == null ? 0.0 : corpus.weight[p] * c.doubleValue();
			}
			require(Math.abs(centroidScores[a] - dot / Math.sqrt(norm)) < 1e-12, "centroid cosine of " + centroid.name(a));
		}
	}

	/** The run graph's context is kept: adoption, bridge or inside, exposure. */
	private static void runGraphContext(File corpusFile)
	{
		LinkedHashMap<String,String> labels = new LinkedHashMap<String,String>();
		for (String[] user : USERS)
		{
			labels.put(user[0], user[1]);
		}
		RecommendationQualityEvaluator.Setup setup = new RecommendationQualityEvaluator.Setup("graph-run", "Similarity",
				"Algorithm Default", RecommendationCandidateType.USER, 2, Arrays.asList("Alice"), labels.keySet(), labels);
		Map<String,List<RecommendationRunResults.Entry>> results =
				new LinkedHashMap<String,List<RecommendationRunResults.Entry>>();
		results.put("Alice", Arrays.asList(new RecommendationRunResults.Entry("Bob", 97.5),
				new RecommendationRunResults.Entry("Carol", 41.0)));
		RunGraph graph = RunGraph.of(setup, results, "sample.txt");
		RunGraphRecord.Loaded loaded = new RunGraphRecord.Loaded(graph, RunGraphAnalysis.of(graph), null, null, null,
				false);
		RecommendationEvidenceEngine.EvidenceRun run = new RecommendationEvidenceEngine.EvidenceRun("graph-run",
				RecommendationModeSupport.COS_SIM, RecommendationCandidateType.USER, "sample.txt", corpusFile, null, 2,
				null);
		run.add(new RecommendationEvidenceEngine.RecommendationEvent("graph-run", "Alice",
				RecommendationCandidateType.USER, RecommendationResultStatus.OK, "Similarity", "", Arrays.asList(
						new RecommendationEvidenceEngine.MatchEvidence(1, "Bob", "97.5", ""),
						new RecommendationEvidenceEngine.MatchEvidence(2, "Carol", "41.0", ""))));
		RecommendationEvidenceEngine.Explanation x = RecommendationEvidenceEngine.explain(run, "Alice", 0, loaded, null);
		require(x.graphAvailable, "the run graph should be available");
		require("Bridges to StoreB".equals(x.graphRelation), "Bob bridges: " + x.graphRelation);
		require(x.graphExposure.startsWith("Recommended to 1 target"), x.graphExposure);
		String adopted = graph.name(graph.adoptionOf(graph.indexOf("Alice")).target);
		require(x.graphAdoption.contains("adopted " + adopted), x.graphAdoption);
		require(x.rival != null && x.rival.equals("Carol"), "the rival of the first recommendation is the second");
	}

	/** A recorded run is read back from recommendations.csv, the manifest and quality.json. */
	private static void recordedRunsLoad(File directory, File corpusFile) throws Exception
	{
		File record = new File(directory, "record");
		record.mkdirs();
		write(new File(record, "manifest.json"), "{\"runId\": \"rec-run\", \"properties\": {\"algorithm\": \"Similarity\","
				+ " \"datasetName\": \"sample.txt\", \"corpus\": \"" + corpusFile.getName() + "\"}}\n");
		write(new File(record, "quality.json"), "{\"candidateType\": \"user\", \"k\": 2}\n");
		write(new File(record, "recommendations.csv"), "target,target_followee,rank,candidate,candidate_followee,score,"
				+ "relevant\nAlice,StoreA,1,Bob,StoreB,100.0,0\nAlice,StoreA,2,Dave,StoreA,80.0,1\n");
		RecommendationEvidenceEngine.EvidenceRun run = RecommendationEvidenceEngine.EvidenceRun.fromRecord(record);
		require("rec-run".equals(run.runId) && run.algorithm == RecommendationModeSupport.COS_SIM, "manifest not read");
		require(run.corpusFile != null && run.corpusFile.getName().equals(corpusFile.getName()), "corpus not found");
		require(run.event("Alice").matches.size() == 2 && run.event("Alice").matches.get(1).name.equals("Dave"),
				"recommendations.csv not read");
		RecommendationEvidenceEngine.Explanation x = RecommendationEvidenceEngine.explain(run, "Alice", 1, null, null);
		require(Boolean.TRUE.equals(x.relevant), "Dave shares Alice's account");
	}

	// ============================================================ fixtures

	private static File corpus(File folder) throws Exception
	{
		LinkedHashMap<String,List<Tweet>> tweets = new LinkedHashMap<String,List<Tweet>>();
		LinkedHashMap<String,String> followees = new LinkedHashMap<String,String>();
		long id = 1;
		for (String[] user : USERS)
		{
			ArrayList<Tweet> list = new ArrayList<Tweet>();
			int month = 1;
			for (String text : user[2].split("\\|"))
			{
				list.add(new Tweet(text, id++, "2026-0" + month++ + "-1" + (id % 9) + " 10:00:00", user[0]));
			}
			tweets.put(user[0], list);
			followees.put(user[0], user[1]);
		}
		return RunCorpus.write(folder, tweets.keySet(), tweets, followees, true, true, false,
				java.util.Collections.<String>emptySet());
	}

	private static List<String> ranked(EvidenceCorpus corpus, final double[] scores, int u)
	{
		ArrayList<String> names = new ArrayList<String>();
		for (int v = 0; v < corpus.size(); v++)
		{
			if (v != u)
			{
				names.add(corpus.users[v]);
			}
		}
		final EvidenceCorpus c = corpus;
		java.util.Collections.sort(names, new java.util.Comparator<String>()
		{
			public int compare(String a, String b)
			{
				int byScore = Double.compare(scores[c.index.get(b).intValue()], scores[c.index.get(a).intValue()]);
				return byScore != 0 ? byScore : a.compareTo(b);
			}
		});
		return names;
	}

	private static Map<String,Double> vector(EvidenceCorpus corpus, int u)
	{
		LinkedHashMap<String,Double> map = new LinkedHashMap<String,Double>();
		for (int p = corpus.pointer[u]; p < corpus.pointer[u + 1]; p++)
		{
			map.put(corpus.terms[corpus.column[p]], Double.valueOf(corpus.weight[p]));
		}
		return map;
	}

	private static void write(File file, String text) throws Exception
	{
		FileWriter writer = new FileWriter(file);
		try
		{
			writer.write(text);
		}
		finally
		{
			writer.close();
		}
	}

	private static void require(boolean condition, String message)
	{
		if (!condition)
		{
			throw new AssertionError(message);
		}
	}
}
