package TwitterGatherDataFollowers.userRyersonU;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public final class RecommendationEvidenceEngineSelfTest
{
	private RecommendationEvidenceEngineSelfTest()
	{
	}

	public static void main(String[] args) throws Exception
	{
		explainsUserRecommendationFromDatasetTermsAndGraphEdges();
		explainsFolloweeRecommendationWithoutCandidateRows();
		System.out.println("RecommendationEvidenceEngineSelfTest passed");
	}

	private static void explainsUserRecommendationFromDatasetTermsAndGraphEdges()
			throws Exception
	{
		Path temp = Files.createTempDirectory("evidence-engine");
		File dataset = write(temp, "sample.txt",
				"StoreA\t1\t2026-01-01\t10\tAlice\tgraph neural recommender model\n"
						+ "StoreA\t2\t2026-01-02\t10\tAlice\tneural ranking evidence\n"
						+ "StoreB\t3\t2026-01-03\t11\tBob\tgraph neural model ranking\n"
						+ "StoreC\t4\t2026-01-04\t12\tCarol\tfresh fruit market\n");
		File important = new File("important-stuff");
		important.mkdirs();
		File mapping = new File(important, "name-number-sample.txt");
		File edges = new File(important, "edges-numbers-sample.txt");
		try
		{
			write(mapping,
					"StoreA\t1\nAlice\t2\nStoreB\t3\nBob\t4\nStoreC\t5\nCarol\t6\n");
			write(edges,
					"2 1\n2 4\n");

			SimulatorHelpPanel.Snapshot snapshot = snapshot(
					dataset,
					"sample.txt",
					RecommendationModeSupport.COS_SIM,
					"Similarity",
					RecommendationCandidateType.USER);
			RecommendationEvidenceEngine.RecommendationEvent event =
					new RecommendationEvidenceEngine.RecommendationEvent(
							"Alice",
							RecommendationCandidateType.USER,
							RecommendationResultStatus.OK,
							"Similarity result",
							"",
							Arrays.asList(
									new RecommendationEvidenceEngine.MatchEvidence(
											1,
											"Bob",
											"97.5",
											"ranked recommendation"),
									new RecommendationEvidenceEngine.MatchEvidence(
											2,
											"Carol",
											"41.0",
											"ranked recommendation")));

			RecommendationEvidenceEngine.EvidenceReport report =
					RecommendationEvidenceEngine.analyze(snapshot, event, 0);

			require(report.available, "report should be available");
			require("Alice".equals(report.targetUser), "wrong target");
			require("Bob".equals(report.candidateName), "wrong candidate");
			require(report.targetRowCount == 2, "target rows not counted");
			require(report.candidateRowCount == 1, "candidate rows not counted");
			require(report.sharedTerms.size() >= 3, "shared terms were not extracted");
			require(containsTerm(report.sharedTerms, "graph"), "missing shared graph term");
			require(report.graphAvailable, "graph should be available");
			require(report.recommendationMatchesGraphEdge,
					"recommendation should match latest graph edge");
			require(!report.sameAsOriginalLabel,
					"latest graph edge should change from original label to Bob");
			require(report.plainVerdict.contains("Similarity ranked Bob for Alice"),
					"plain verdict should name recommendation");
			require(report.comparison.available,
					"rank one should be compared against rank two");
			require(report.comparison.leftSupport > report.comparison.rightSupport,
					"winner should have stronger inspected text support");
			require(!report.counterfactualInsights.isEmpty(),
					"counterfactual evidence should be generated");
			require(report.counterfactualSummary.length() > 0,
					"counterfactual summary should be generated");
			require(containsTermLocation(report.termLocations, "graph", "shared"),
					"shared graph term should be clickable/highlightable");
			require(containsTermLocation(report.termLocations, "evidence", "target-only"),
					"target-only term should be represented for highlighting");
		}
		finally
		{
			Files.deleteIfExists(mapping.toPath());
			Files.deleteIfExists(edges.toPath());
		}
	}

	private static void explainsFolloweeRecommendationWithoutCandidateRows()
			throws Exception
	{
		Path temp = Files.createTempDirectory("evidence-engine-followee");
		File dataset = write(temp, "followee.txt",
				"JournalA\t1\t2026-01-01\t20\tAuthorA\tdeep learning recommender\n"
						+ "JournalB\t2\t2026-01-02\t21\tAuthorB\tgraph mining recommender\n");
		SimulatorHelpPanel.Snapshot snapshot = snapshot(
				dataset,
				"followee.txt",
				RecommendationModeSupport.SVM,
				"SVM",
				RecommendationCandidateType.FOLLOWEE);
		RecommendationEvidenceEngine.RecommendationEvent event =
				new RecommendationEvidenceEngine.RecommendationEvent(
						"AuthorA",
						RecommendationCandidateType.FOLLOWEE,
						RecommendationResultStatus.OK,
						"SVM result",
						"",
						Arrays.asList(
								new RecommendationEvidenceEngine.MatchEvidence(
										1,
										"JournalB",
										"100.0",
										"class score")));

		RecommendationEvidenceEngine.EvidenceReport report =
				RecommendationEvidenceEngine.analyze(snapshot, event, 0);

		require(report.available, "followee report should be available");
		require(report.targetRowCount == 1, "target row missing");
		require(report.candidateRowCount == 1, "followee candidate row missing");
		require(report.originalLabels.contains("JournalA"),
				"original target label should be recorded");
		require(report.algorithmLensTitle.contains("Class-score"),
				"SVM lens should explain class-score evidence");
	}

	private static SimulatorHelpPanel.Snapshot snapshot(
			File dataset,
			String datasetName,
			int algorithm,
			String algorithmName,
			RecommendationCandidateType outputType)
	{
		return new SimulatorHelpPanel.Snapshot(
				algorithm,
				1,
				3,
				1,
				0,
				4096,
				0,
				"Text dataset",
				datasetName,
				dataset.getAbsolutePath(),
				algorithmName,
				RecommendationTargetMode.ALGORITHM_DEFAULT,
				"Algorithm Default",
				outputType,
				outputType,
				false,
				true,
				false,
				true,
				true,
				true,
				false,
				false,
				"Default",
				"Default profile",
				"Balanced",
				"TARGET: Alice");
	}

	private static File write(Path directory, String fileName, String content)
			throws Exception
	{
		File file = directory.resolve(fileName).toFile();
		write(file, content);
		return file;
	}

	private static void write(File file, String content) throws Exception
	{
		Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
	}

	private static boolean containsTerm(
			java.util.List<RecommendationEvidenceEngine.TermScore> terms,
			String term)
	{
		for (RecommendationEvidenceEngine.TermScore score : terms)
		{
			if (term.equals(score.term))
			{
				return true;
			}
		}
		return false;
	}

	private static boolean containsTermLocation(
			java.util.List<RecommendationEvidenceEngine.TermLocation> locations,
			String term,
			String role)
	{
		for (RecommendationEvidenceEngine.TermLocation location : locations)
		{
			if (term.equals(location.term) && role.equals(location.role))
			{
				return true;
			}
		}
		return false;
	}

	private static void require(boolean condition, String message)
	{
		if (!condition)
		{
			throw new AssertionError(message);
		}
	}
}
