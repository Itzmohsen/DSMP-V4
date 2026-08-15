package TwitterGatherDataFollowers.userRyersonU;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class RecommendationScoreMergerSelfTest
{
	private RecommendationScoreMergerSelfTest()
	{
	}

	public static void main(String[] args) throws Exception
	{
		preservesNativeModeResolution();
		mergesProjectedFolloweesWithMax();
		mergesClassifierGuidedUsers();
		sortsTiesDeterministically();
		reportsEmptyResultsWithoutFakeCandidates();
		roundTripsPayloadSerialization();
		System.out.println("RecommendationScoreMergerSelfTest passed");
	}

	private static void preservesNativeModeResolution()
	{
		require(RecommendationModeSupport.resolveCandidateType(
				RecommendationTargetMode.ALGORITHM_DEFAULT,
				RecommendationModeSupport.DOC2VEC) == RecommendationCandidateType.USER,
				"Doc2Vec default no longer resolves to users");
		require(RecommendationModeSupport.resolveCandidateType(
				RecommendationTargetMode.ALGORITHM_DEFAULT,
				RecommendationModeSupport.SVM) == RecommendationCandidateType.FOLLOWEE,
				"SVM default no longer resolves to followees");
		require(!RecommendationModeSupport.requiresConvertedPipeline(
				RecommendationTargetMode.FOLLOWEE_ACCOUNTS,
				RecommendationModeSupport.SVM),
				"native SVM followee mode was treated as converted");
	}

	private static void mergesProjectedFolloweesWithMax()
	{
		Map<String,Map<String,Double>> firstScores = scores(
				"target", values("StoreA", 0.8, "StoreB", 0.5));
		Map<String,Map<String,Double>> secondScores = scores(
				"target", values("StoreA", 0.6, "StoreC", 0.4));
		Map<String,Map<String,String>> firstDetails =
				new TreeMap<String,Map<String,String>>();
		firstDetails.put("target", textValues(
				"StoreA", "Best-matching user: alice",
				"StoreB", "Best-matching user: bob"));
		List<RecommendationScorePayload> payloads = Arrays.asList(
				RecommendationScorePayload.directWithDetails(
						RecommendationTargetMode.FOLLOWEE_ACCOUNTS,
						RecommendationCandidateType.FOLLOWEE,
						RecommendationMergePolicy.MAX,
						Arrays.asList("target"), firstScores, firstDetails, null),
				RecommendationScorePayload.direct(
						RecommendationTargetMode.FOLLOWEE_ACCOUNTS,
						RecommendationCandidateType.FOLLOWEE,
						RecommendationMergePolicy.MAX,
						Arrays.asList("target"), secondScores, null));

		RecommendationResultPayload result =
				RecommendationScoreMerger.merge(payloads).get("target");
		require(close(result.getScores().get("StoreA"), 100.0), "MAX merge lost top score");
		require(close(result.getScores().get("StoreB"), 62.5), "MAX merge changed StoreB");
		require(close(result.getScores().get("StoreC"), 50.0), "MAX merge changed StoreC");
		require("Best-matching user: alice".equals(
				result.getCandidateDetails().get("StoreA")),
				"projection representative was lost");
	}

	private static void mergesClassifierGuidedUsers()
	{
		Map<String,Map<String,Double>> classScores = scores(
				"target", values("Retail", 0.8, "Technology", 0.2));
		Map<String,Map<String,RecommendationCandidateEvidence>> evidence =
				new TreeMap<String,Map<String,RecommendationCandidateEvidence>>();
		Map<String,RecommendationCandidateEvidence> candidates =
				new TreeMap<String,RecommendationCandidateEvidence>();
		candidates.put("alice", new RecommendationCandidateEvidence("Retail", 0.5));
		candidates.put("bob", new RecommendationCandidateEvidence("Technology", 1.0));
		evidence.put("target", candidates);

		RecommendationScorePayload payload =
				RecommendationScorePayload.classifierGuidedUsers(
						RecommendationTargetMode.SIMILAR_USERS,
						Arrays.asList("target"), classScores, evidence,
						"SVM-guided users", null);
		RecommendationResultPayload result =
				RecommendationScoreMerger.merge(Arrays.asList(payload)).get("target");

		require(close(result.getScores().get("alice"), 100.0),
				"classifier-guided top user changed");
		require(close(result.getScores().get("bob"), 50.0),
				"classifier-guided secondary user changed");
		require("SVM-guided users".equals(result.getResultLabel()),
				"classifier guidance label was lost");
	}

	private static void sortsTiesDeterministically()
	{
		LinkedHashMap<String,Double> normalized =
				RecommendationScoreMerger.normalizeAndSort(values("zeta", 1.0, "alpha", 1.0));
		require("alpha".equals(normalized.keySet().iterator().next()),
				"equal scores were not sorted by candidate name");
	}

	private static void reportsEmptyResultsWithoutFakeCandidates()
	{
		RecommendationScorePayload payload = RecommendationScorePayload.direct(
				RecommendationTargetMode.FOLLOWEE_ACCOUNTS,
				RecommendationCandidateType.FOLLOWEE,
				RecommendationMergePolicy.MAX,
				Arrays.asList("target"),
				scores("target", new TreeMap<String,Double>()),
				null);
		RecommendationResultPayload result =
				RecommendationScoreMerger.merge(Arrays.asList(payload)).get("target");
		require(result.getStatus() == RecommendationResultStatus.NO_CANDIDATES,
				"empty result did not report NO_CANDIDATES");
		require(result.getScores().isEmpty(), "empty result invented a candidate");
	}

	private static void roundTripsPayloadSerialization() throws Exception
	{
		RecommendationScorePayload original = RecommendationScorePayload.direct(
				RecommendationTargetMode.FOLLOWEE_ACCOUNTS,
				RecommendationCandidateType.FOLLOWEE,
				RecommendationMergePolicy.MAX,
				Arrays.asList("target"),
				scores("target", values("StoreA", 1.0)),
				null);
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		ObjectOutputStream output = new ObjectOutputStream(bytes);
		output.writeObject(original);
		output.close();
		ObjectInputStream input = new ObjectInputStream(
				new ByteArrayInputStream(bytes.toByteArray()));
		Object restored = input.readObject();
		input.close();
		require(restored instanceof RecommendationScorePayload,
				"score payload did not survive serialization");
	}

	private static Map<String,Map<String,Double>> scores(
			String target, Map<String,Double> values)
	{
		Map<String,Map<String,Double>> scores =
				new TreeMap<String,Map<String,Double>>();
		scores.put(target, values);
		return scores;
	}

	private static LinkedHashMap<String,Double> values(
			String firstName, double firstScore,
			String secondName, double secondScore)
	{
		LinkedHashMap<String,Double> values = new LinkedHashMap<String,Double>();
		values.put(firstName, firstScore);
		values.put(secondName, secondScore);
		return values;
	}

	private static LinkedHashMap<String,Double> values(String name, double score)
	{
		LinkedHashMap<String,Double> values = new LinkedHashMap<String,Double>();
		values.put(name, score);
		return values;
	}

	private static Map<String,String> textValues(
			String firstName, String firstValue,
			String secondName, String secondValue)
	{
		Map<String,String> values = new TreeMap<String,String>();
		values.put(firstName, firstValue);
		values.put(secondName, secondValue);
		return values;
	}

	private static boolean close(Double actual, double expected)
	{
		return actual != null && Math.abs(actual - expected) < 0.000001;
	}

	private static void require(boolean condition, String message)
	{
		if (!condition)
		{
			throw new AssertionError(message);
		}
	}
}
