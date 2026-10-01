package TwitterGatherDataFollowers.userRyersonU;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

/**
 * The Temporal Graph Foundation model: the target's own followee never
 * influences its ranking, no file outside the run changes it, the fusion is
 * learned from labelled non-target users, and the evidence it reports
 * (intervals, counterfactuals, timeline, ablation lenses) is well formed.
 */
public final class TemporalGraphFoundationModelSupportSelfTest
{
	private static final String[] COMMUNITIES = {"@research", "@kitchen", "@travel"};
	private static final String[][] VOCABULARY = {
			{"graph", "foundation", "ranking", "model", "embedding", "citation"},
			{"recipe", "garden", "oven", "pasta", "harvest", "spice"},
			{"flight", "hotel", "beach", "passport", "museum", "train"}};

	private TemporalGraphFoundationModelSupportSelfTest()
	{
	}

	public static void main(String[] args) throws Exception
	{
		File directory = java.nio.file.Files.createTempDirectory("dsmp-tgf").toFile();
		File dataset = new File(directory, "tgf-self-test.txt");
		Map<String,String> followees = new LinkedHashMap<String,String>();
		Map<String,Map<String,Double>> tfidf = new LinkedHashMap<String,Map<String,Double>>();
		writeCorpus(dataset, followees, tfidf);
		List<String> targets = new ArrayList<String>();
		targets.add("user-0-0");
		targets.add("user-1-0");
		targets.add("user-2-0");

		TemporalGraphFoundationModelSupport.Result result = score(dataset, tfidf, followees, targets);
		TreeMap<String,Double> row = result.getScores().get("user-0-0");
		require(row != null && row.size() > 3, "missing score row");
		for (double probability : row.values())
		{
			require(probability > 0.0 && probability < 1.0, "a score is not a probability: " + probability);
		}
		require(result.getSummary().contains("Learned fusion"), "the fusion was not learned: " + result.getSummary());
		TemporalGraphFoundationModelSupport.FoundationReadiness fit =
				result.getResearchSummary().foundationReadiness;
		require(fit.overall > 0.8, "held-out pair AUC should be high on separable communities, got " + fit.overall);

		//1. The target's own followee is never used to score it.
		Map<String,String> relabelled = new LinkedHashMap<String,String>(followees);
		relabelled.put("user-0-0", "@travel");
		relabelled.put("user-1-0", "");
		requireSameScores(result, score(dataset, tfidf, relabelled, targets),
				"changing the targets' own followees changed their rankings");

		//2. Nothing outside the run's corpus changes the result (the old model read
		//   accumulated graph files from important-stuff/).
		File importantStuff = new File("important-stuff");
		boolean created = importantStuff.mkdirs();
		File mapping = new File(importantStuff, "name-number-" + dataset.getName());
		File edges = new File(importantStuff, "edges-numbers-tgf-self-test.txt");
		try
		{
			writeLines(mapping, "user-0-0\t1", "user-2-5\t2");
			writeLines(edges, "1 2", "1 2", "1 2");
			requireSameScores(result, score(dataset, tfidf, followees, targets),
					"a graph file outside the run changed the rankings");
		}
		finally
		{
			mapping.delete();
			edges.delete();
			if (created)
			{
				importantStuff.delete();
			}
		}

		//3. Deterministic.
		requireSameScores(result, score(dataset, tfidf, followees, targets), "two identical runs differ");

		//4. The ranking: same-vocabulary users first.
		TemporalGraphFoundationModelSupport.TargetReport report = result.getReports().get("user-0-0");
		require(report.relevantRank == 1, "a same-community user should rank first, got " + report.relevantRank);
		require(report.ndcgAtFive > 0.9, "NDCG@5 should be near 1 on separable communities, got " + report.ndcgAtFive);
		TemporalGraphFoundationModelSupport.CandidateScore top = report.topCandidates.get(0);
		require(top.candidateFollowee().equals("@research"), "top candidate is not from the research community");

		//5. Evidence: interval, stability, counterfactuals, contributions, timeline.
		require(top.confidenceLower <= top.confidenceUpper, "invalid bootstrap interval");
		require(top.stability >= 0.0 && top.stability <= 1.0, "stability is not a share");
		require(top.counterfactuals.size() == 4, "expected four counterfactual probes");
		require(top.counterfactualScore(true, false, false, false, false) <= top.finalScore + 1e-12,
				"removing positive graph evidence raised the probability");
		require(top.evidenceAtoms.size() == 3, "expected text, graph and time evidence");
		require(top.timeline.size() == 12, "expected twelve dated timeline frames");
		require(top.timeline.get(11).rank > 0, "timeline ranks were not assigned");
		require(top.whyThis.startsWith("Rank #1"), "missing explanation");
		require(top.bridge == 0.0 && top.debias == 0.0 && top.robust == 0.0, "retired signals must be zero");

		//6. Ablation lenses rank all candidates and grade with the proper NDCG.
		require(report.benchmarkRows.size() == 4, "expected fusion, text, graph and time lenses");
		require("TGF fusion".equals(report.benchmarkRows.get(0).label), "the fusion lens comes first");
		require(result.getResearchSummary().benchmarkRows.size() == 4, "summary lenses missing");

		//7. Undated corpora: no temporal signal and no line-order clock.
		File undated = new File(directory, "tgf-undated.txt");
		writeUndated(dataset, undated);
		TemporalGraphFoundationModelSupport.Result plain = score(undated, tfidf, followees, targets);
		TemporalGraphFoundationModelSupport.CandidateScore plainTop =
				plain.getReports().get("user-0-0").topCandidates.get(0);
		require(plainTop.time == 0.0 && plainTop.timeline.isEmpty(), "undated rows must not produce a temporal signal");
		require(plain.getReports().get("user-0-0").benchmarkRows.size() == 3, "no time lens without dates");

		System.out.println("TemporalGraphFoundationModelSupportSelfTest passed");
	}

	private static TemporalGraphFoundationModelSupport.Result score(File dataset,
			Map<String,Map<String,Double>> tfidf, Map<String,String> followees, List<String> targets)
	{
		return TemporalGraphFoundationModelSupport.score(new TemporalGraphFoundationModelSupport.Input(
				"self-test", dataset, tfidf, followees, followees, targets,
				Collections.<String,Integer>emptyMap(), 1, 1, 20));
	}

	private static void requireSameScores(TemporalGraphFoundationModelSupport.Result first,
			TemporalGraphFoundationModelSupport.Result second, String message)
	{
		require(first.getScores().equals(second.getScores()), message);
	}

	/**
	 * Three communities of twelve users. Each user mostly uses its community's
	 * words plus a little shared noise, and is active in its community's
	 * season, so text, graph and time all carry signal.
	 */
	private static void writeCorpus(File dataset, Map<String,String> followees,
			Map<String,Map<String,Double>> tfidf) throws Exception
	{
		Random random = new Random(7L);
		String[] noise = {"today", "great", "news", "photo", "friends"};
		BufferedWriter writer = new BufferedWriter(new FileWriter(dataset));
		try
		{
			long id = 1;
			for (int community = 0; community < COMMUNITIES.length; community++)
			{
				for (int member = 0; member < 12; member++)
				{
					String user = "user-" + community + "-" + member;
					followees.put(user, COMMUNITIES[community]);
					LinkedHashMap<String,Double> vector = new LinkedHashMap<String,Double>();
					for (int tweet = 0; tweet < 6; tweet++)
					{
						String a = VOCABULARY[community][random.nextInt(6)];
						String b = VOCABULARY[community][random.nextInt(6)];
						String c = random.nextInt(4) == 0 ? VOCABULARY[random.nextInt(3)][random.nextInt(6)]
								: noise[random.nextInt(noise.length)];
						for (String term : new String[] {a, b, c})
						{
							Double old = vector.get(term);
							vector.put(term, Double.valueOf(old == null ? 1.0 : old.doubleValue() + 1.0));
						}
						int month = 1 + community * 4 + random.nextInt(4);
						writer.write(COMMUNITIES[community] + "\t" + (id++) + "\t2025-"
								+ (month < 10 ? "0" + month : "" + month) + "-1" + random.nextInt(9) + " 12:00:00\t"
								+ (100 + community) + "\t" + user + "\t" + a + " " + b + " " + c + "\n");
					}
					double norm = 0.0;
					for (double value : vector.values())
					{
						norm += value * value;
					}
					for (Map.Entry<String,Double> entry : vector.entrySet())
					{
						entry.setValue(Double.valueOf(entry.getValue().doubleValue() / Math.sqrt(norm)));
					}
					tfidf.put(user, vector);
				}
			}
		}
		finally
		{
			writer.close();
		}
	}

	private static void writeUndated(File dated, File undated) throws Exception
	{
		List<String> lines = java.nio.file.Files.readAllLines(dated.toPath());
		BufferedWriter writer = new BufferedWriter(new FileWriter(undated));
		try
		{
			for (String line : lines)
			{
				String[] fields = line.split("\t", 6);
				writer.write(fields[0] + "\t" + fields[1] + "\t\t" + fields[3] + "\t" + fields[4] + "\t" + fields[5] + "\n");
			}
		}
		finally
		{
			writer.close();
		}
	}

	private static void writeLines(File file, String... lines) throws Exception
	{
		BufferedWriter writer = new BufferedWriter(new FileWriter(file));
		try
		{
			for (String line : lines)
			{
				writer.write(line);
				writer.newLine();
			}
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
