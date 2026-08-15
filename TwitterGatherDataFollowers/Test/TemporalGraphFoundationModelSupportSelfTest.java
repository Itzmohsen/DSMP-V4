package TwitterGatherDataFollowers.userRyersonU;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

public final class TemporalGraphFoundationModelSupportSelfTest
{
	private TemporalGraphFoundationModelSupportSelfTest()
	{
	}

	public static void main(String[] args) throws Exception
	{
		File dataset = File.createTempFile("dsmp-tgf-self-test", ".txt");
		dataset.deleteOnExit();
		writeDataset(dataset);

		Map<String,Map<String,Double>> tfidf =
				new LinkedHashMap<String,Map<String,Double>>();
		tfidf.put("alice", vector(
				"graph", 1.0,
				"foundation", 0.8,
				"recommender", 0.6));
		tfidf.put("bob", vector(
				"graph", 0.95,
				"foundation", 0.7,
				"ranking", 0.4));
		tfidf.put("cara", vector(
				"recipe", 0.9,
				"garden", 0.6,
				"travel", 0.4));

		Map<String,String> followees = new LinkedHashMap<String,String>();
		followees.put("alice", "@research");
		followees.put("bob", "@research");
		followees.put("cara", "@lifestyle");

		Map<String,Integer> primaryServers =
				new LinkedHashMap<String,Integer>();
		primaryServers.put("bob", Integer.valueOf(1));
		primaryServers.put("cara", Integer.valueOf(1));

		TemporalGraphFoundationModelSupport.Result result =
				TemporalGraphFoundationModelSupport.score(
						new TemporalGraphFoundationModelSupport.Input(
								"self-test",
								dataset,
								tfidf,
								followees,
								followees,
								Collections.singletonList("alice"),
								primaryServers,
								1,
								1,
								5));

		TreeMap<String,Double> row = result.getScores().get("alice");
		require(row != null, "missing alice score row");
		require(row.containsKey("bob"), "missing bob recommendation");
		require(row.containsKey("cara"), "missing cara recommendation");
		require(row.get("bob").doubleValue() > row.get("cara").doubleValue(),
				"bob should outrank unrelated old candidate");
		String detail = result.getDetails().get("alice").get("bob");
		require(detail != null && detail.contains("TGF text"),
				"candidate detail did not include TGF signal summary");
		require(result.getRowsScanned() == 4L,
				"dataset scan count changed");
		TemporalGraphFoundationModelSupport.TargetReport report =
				result.getReports().get("alice");
		require(report != null && !report.topCandidates.isEmpty(),
				"missing target report");
		require("bob".equals(report.topCandidates.get(0).candidateName()),
				"top report candidate was not bob");
			require(report.topCandidates.get(0).withoutGraphRank > 0,
					"ablation ranks were not assigned");
			TemporalGraphFoundationModelSupport.CandidateScore top =
					report.topCandidates.get(0);
			require(!top.timeline.isEmpty(),
					"temporal replay frames were not generated");
			require(top.timeline.get(0).rank > 0,
					"temporal replay frame ranks were not assigned");
			require(top.timeline.get(top.timeline.size() - 1).dominantSignal != null
							&& top.timeline.get(top.timeline.size() - 1)
									.dominantSignal.length() > 0,
					"temporal replay dominant signal was not assigned");
			require(!top.counterfactuals.isEmpty(),
					"counterfactual probes were not generated");
				require(top.confidenceUpper >= top.confidenceLower,
						"confidence interval was invalid");
				require(top.calibrationVerdict != null
								&& top.calibrationVerdict.length() > 0,
						"calibration verdict was not generated");
				require(top.calibrationPValue >= 0.0,
						"calibration p-value was not computed");
				require(!top.evidenceAtoms.isEmpty(),
						"evidence ledger atoms were not generated");
				require(top.evidenceCoverage > 0.0,
						"evidence coverage was not computed");
				require(top.counterfactualFragility >= 0.0,
						"counterfactual fragility was not computed");
				require(top.reliabilityBand != null
								&& top.reliabilityBand.length() > 0,
						"reliability band was not assigned");
				require(top.whyThis != null && top.whyThis.indexOf("Rank") >= 0,
						"why-this narrative was not generated");
				require(top.whyNotChallenger != null
								&& top.whyNotChallenger.length() > 0,
						"why-not challenger narrative was not generated");
				require(!top.riskFlags.isEmpty(),
						"risk flags were not generated");
				require(top.counterfactualScore(true, false, false, false, false)
						<= top.finalScore,
						"removing graph should not raise this self-test score");
			require(result.getResearchSummary().timelineFrames > 0,
					"research summary did not count timeline frames");
				require(result.getResearchSummary().hitAtOne > 0.0,
						"research summary did not detect same-followee top hit");
				require(result.getResearchSummary().averageEvidenceCoverage > 0.0,
						"research summary did not compute evidence coverage");
				require(result.getResearchSummary().averageCounterfactualFragility >= 0.0,
						"research summary did not compute counterfactual fragility");
				require(!report.benchmarkRows.isEmpty(),
						"target benchmark board rows were not generated");
				require(result.getResearchSummary().benchmarkRows.size() >= 5,
						"research summary benchmark board was incomplete");
				require(result.getResearchSummary().foundationReadiness.overall > 0.0,
						"foundation readiness was not computed");
				require(TemporalGraphFoundationModelSupport.weightsDescription()
								.indexOf("text=") >= 0,
						"model weight description was not generated");
				System.out.println("TemporalGraphFoundationModelSupportSelfTest passed");
		}

	private static void writeDataset(File dataset) throws Exception
	{
		BufferedWriter writer = new BufferedWriter(new FileWriter(dataset));
		try
		{
			writer.write("@research\t1\t2026-01-01\t10\talice\tgraph foundation recommender\n");
			writer.write("@research\t2\t2026-01-02\t11\tbob\tgraph foundation ranking\n");
			writer.write("@lifestyle\t3\t2020-01-01\t12\tcara\trecipe garden travel\n");
			writer.write("@research\t4\t2026-01-03\t11\tbob\tgraph recommender bridge\n");
		}
		finally
		{
			writer.close();
		}
	}

	private static Map<String,Double> vector(Object... termsAndWeights)
	{
		Map<String,Double> map = new LinkedHashMap<String,Double>();
		for (int i = 0; i + 1 < termsAndWeights.length; i += 2)
		{
			map.put((String) termsAndWeights[i], (Double) termsAndWeights[i + 1]);
		}
		return map;
	}

	private static void require(boolean condition, String message)
	{
		if (!condition)
		{
			throw new AssertionError(message);
		}
	}
}
