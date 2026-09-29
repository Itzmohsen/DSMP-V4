package TwitterGatherDataFollowers.userRyersonU;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Verifies the ranking metrics against values worked out by hand.
 */
public final class RecommendationQualityEvaluatorSelfTest
{
	private static final double EPSILON = 1.0e-9;

	private RecommendationQualityEvaluatorSelfTest()
	{
	}

	public static void main(String[] args)
	{
		similarUserMetricsMatchHandCalculation();
		followeeModeScoresTheTargetsOwnFollowee();
		perfectRankingScoresOne();
		missWithNoRelevantCandidateScoresZero();
		targetsWithoutGroundTruthAreSkippedNotCounted();
		System.out.println("RecommendationQualityEvaluatorSelfTest passed");
	}

	/**
	 * target -> followee A; ranked [u1(A), u2(B), u3(A)]; k = 3.
	 * relevant = {u1, u3}, hits at ranks 1 and 3.
	 *   precision@3 = 2/3            recall@3 = 2/2 = 1
	 *   MRR         = 1/1 = 1        AP = (1/1 + 2/3) / 2
	 *   DCG         = 1/log2(2) + 1/log2(4)
	 *   IDCG        = 1/log2(2) + 1/log2(3)
	 */
	private static void similarUserMetricsMatchHandCalculation()
	{
		RecommendationQualityEvaluator.startRun(groundTruth());
		RecommendationQualityEvaluator.record(
				userPayload("target", "u1", "u2", "u3"), 3);
		RecommendationQualityEvaluator.Report report =
				RecommendationQualityEvaluator.report(3);

		require(report != null, "no report produced");
		require(report.evaluatedTargets == 1, "expected one evaluated target");
		requireClose(report.hitRate, 1.0, "hit rate");
		requireClose(report.precision, 2.0 / 3.0, "precision@3");
		requireClose(report.recall, 1.0, "recall@3");
		requireClose(report.meanReciprocalRank, 1.0, "MRR");
		requireClose(report.meanAveragePrecision, (1.0 + 2.0 / 3.0) / 2.0, "MAP");
		//u1 and u3 share the target's followee; the target itself never counts.
		requireClose(report.meanRelevantSetSize, 2.0, "mean relevant set size");

		double log2 = Math.log(2.0);
		double dcg = 1.0 / (Math.log(2.0) / log2) + 1.0 / (Math.log(4.0) / log2);
		double idcg = 1.0 / (Math.log(2.0) / log2) + 1.0 / (Math.log(3.0) / log2);
		requireClose(report.ndcg, dcg / idcg, "NDCG@3");
		RecommendationQualityEvaluator.reset();
	}

	/** In followee mode only the target's own followee counts as relevant. */
	private static void followeeModeScoresTheTargetsOwnFollowee()
	{
		RecommendationQualityEvaluator.startRun(groundTruth());
		RecommendationQualityEvaluator.record(
				followeePayload("target", "B", "A"), 2);
		RecommendationQualityEvaluator.Report report =
				RecommendationQualityEvaluator.report(2);

		require(report != null, "no followee-mode report");
		requireClose(report.hitRate, 1.0, "followee hit rate");
		requireClose(report.precision, 0.5, "followee precision@2");
		requireClose(report.recall, 1.0, "followee recall@2");
		requireClose(report.meanReciprocalRank, 0.5, "followee MRR");
		RecommendationQualityEvaluator.reset();
	}

	/** Both relevant users ranked first: every metric is 1. */
	private static void perfectRankingScoresOne()
	{
		RecommendationQualityEvaluator.startRun(groundTruth());
		RecommendationQualityEvaluator.record(
				userPayload("target", "u1", "u3", "u2"), 2);
		RecommendationQualityEvaluator.Report report =
				RecommendationQualityEvaluator.report(2);

		require(report != null, "no perfect-ranking report");
		requireClose(report.precision, 1.0, "perfect precision@2");
		requireClose(report.recall, 1.0, "perfect recall@2");
		requireClose(report.ndcg, 1.0, "perfect NDCG@2");
		requireClose(report.meanAveragePrecision, 1.0, "perfect MAP");
		RecommendationQualityEvaluator.reset();
	}

	/** A list containing nothing relevant scores zero, not NaN. */
	private static void missWithNoRelevantCandidateScoresZero()
	{
		RecommendationQualityEvaluator.startRun(groundTruth());
		RecommendationQualityEvaluator.record(userPayload("target", "u2"), 1);
		RecommendationQualityEvaluator.Report report =
				RecommendationQualityEvaluator.report(1);

		require(report != null, "no miss report");
		requireClose(report.hitRate, 0.0, "miss hit rate");
		requireClose(report.precision, 0.0, "miss precision");
		requireClose(report.ndcg, 0.0, "miss NDCG");
		require(!Double.isNaN(report.meanAveragePrecision), "MAP became NaN");
		RecommendationQualityEvaluator.reset();
	}

	/** An unknown target must not be averaged in as a zero. */
	private static void targetsWithoutGroundTruthAreSkippedNotCounted()
	{
		RecommendationQualityEvaluator.startRun(groundTruth());
		RecommendationQualityEvaluator.record(userPayload("stranger", "u1"), 1);
		require(RecommendationQualityEvaluator.report(1) == null,
				"a target with no known followee was scored anyway");

		RecommendationQualityEvaluator.record(userPayload("target", "u1"), 1);
		RecommendationQualityEvaluator.Report report =
				RecommendationQualityEvaluator.report(1);
		require(report != null, "no report after a valid target");
		require(report.evaluatedTargets == 1,
				"skipped target leaked into the evaluated count");
		require(report.skippedWithoutGroundTruth == 1,
				"skipped target was not counted");
		requireClose(report.hitRate, 1.0, "hit rate diluted by a skipped target");
		RecommendationQualityEvaluator.reset();
	}

	private static Map<String,String> groundTruth()
	{
		LinkedHashMap<String,String> truth = new LinkedHashMap<String,String>();
		truth.put("target", "A");
		truth.put("u1", "A");
		truth.put("u2", "B");
		truth.put("u3", "A");
		return truth;
	}

	private static RecommendationResultPayload userPayload(
			String target, String... rankedCandidates)
	{
		return payload(target, RecommendationCandidateType.USER, rankedCandidates);
	}

	private static RecommendationResultPayload followeePayload(
			String target, String... rankedCandidates)
	{
		return payload(target, RecommendationCandidateType.FOLLOWEE, rankedCandidates);
	}

	private static RecommendationResultPayload payload(
			String target,
			RecommendationCandidateType candidateType,
			String[] rankedCandidates)
	{
		LinkedHashMap<String,Double> scores = new LinkedHashMap<String,Double>();
		double score = 100.0;
		for (String candidate : rankedCandidates)
		{
			scores.put(candidate, Double.valueOf(score));
			score -= 1.0;
		}
		return new RecommendationResultPayload(
				target,
				RecommendationTargetMode.ALGORITHM_DEFAULT,
				candidateType,
				RecommendationResultStatus.OK,
				"self-test",
				scores);
	}

	private static void requireClose(double actual, double expected, String label)
	{
		require(Math.abs(actual - expected) < EPSILON,
				label + ": expected " + expected + " but was " + actual);
	}

	private static void require(boolean condition, String message)
	{
		if (!condition)
		{
			throw new AssertionError(message);
		}
	}
}
