package TwitterGatherDataFollowers.userRyersonU;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public final class ScalableSvmNodeInvarianceSelfTest
{
	private static final List<String> TARGETS =
			Arrays.asList(
					"alpha-target",
					"beta-target",
					"gamma-target",
					"alpha-user-1",
					"beta-user-1",
					"gamma-user-1",
					"alpha-user-2",
					"beta-user-2");

	private ScalableSvmNodeInvarianceSelfTest()
	{
	}

	public static void main(String[] args) throws Exception
	{
		producesIdenticalResultsForOneAndEightNodes(false);
		producesIdenticalResultsForOneAndEightNodes(true);
		releasesSharedStateAfterFailure();
		System.out.println("ScalableSvmNodeInvarianceSelfTest passed");
	}

	private static void producesIdenticalResultsForOneAndEightNodes(
			boolean similarUsers)
			throws Exception
	{
		Map<String,LinkedHashMap<String,Double>> documents = buildDocuments();
		Map<String,String> followees = buildFollowees(documents.keySet());

		String mode = similarUsers ? "similar" : "native";
		String oneRun = "scalable-node-invariance-one-" + mode + "-"
				+ System.nanoTime();
		RecommendationRunAbortRegistry.prepare(oneRun);
		long oneStarted = System.nanoTime();
		ScalableSvmSharedCoordinator.Outcome one =
				ScalableSvmSharedCoordinator.score(
						"ScalableInvariantNode1",
						oneRun,
						1,
						1,
						documents,
						followees,
						followees,
						TARGETS,
						similarUsers);
		long oneElapsedMs = (System.nanoTime() - oneStarted) / 1000000L;

		String eightRun = "scalable-node-invariance-eight-" + mode + "-"
				+ System.nanoTime();
		RecommendationRunAbortRegistry.prepare(eightRun);
		List<Map<String,LinkedHashMap<String,Double>>> shards =
				buildShards(documents, 8);
		ExecutorService executor = Executors.newFixedThreadPool(8);
		List<Future<ScalableSvmSharedCoordinator.Outcome>> futures =
				new ArrayList<Future<ScalableSvmSharedCoordinator.Outcome>>();
		List<ScalableSvmSharedCoordinator.Outcome> eightOutcomes =
				new ArrayList<ScalableSvmSharedCoordinator.Outcome>();
		long eightStarted = System.nanoTime();
		try
		{
			for (int index = 0; index < shards.size(); index++)
			{
				final int node = index + 1;
				final Map<String,LinkedHashMap<String,Double>> shard =
						shards.get(index);
				futures.add(executor.submit(
						new Callable<ScalableSvmSharedCoordinator.Outcome>()
						{
							@Override
							public ScalableSvmSharedCoordinator.Outcome call()
									throws Exception
							{
								return ScalableSvmSharedCoordinator.score(
										"ScalableInvariantNode" + node,
										eightRun,
										8,
										node,
										shard,
										followees,
										followees,
										TARGETS,
										similarUsers);
							}
						}));
			}
			for (Future<ScalableSvmSharedCoordinator.Outcome> future : futures)
			{
				ScalableSvmSharedCoordinator.Outcome eight =
						future.get(3, TimeUnit.MINUTES);
				eightOutcomes.add(eight);
				requireScoresEqual(one.getScores(), eight.getScores());
				requireEvidenceEqual(one.getEvidence(), eight.getEvidence());
			}
			requireDownstreamMergesEqual(one, eightOutcomes);
			long eightElapsedMs =
					(System.nanoTime() - eightStarted) / 1000000L;
			System.out.println(
					"Scalable SVM " + mode + " timing: oneNode="
					+ oneElapsedMs + " ms eightNodes=" + eightElapsedMs + " ms");
		}
		finally
		{
			executor.shutdownNow();
			executor.awaitTermination(10, TimeUnit.SECONDS);
		}
	}

	private static void releasesSharedStateAfterFailure() throws Exception
	{
		String runId = "scalable-failure-cleanup-" + System.nanoTime();
		Map<String,LinkedHashMap<String,Double>> invalidDocuments =
				new TreeMap<String,LinkedHashMap<String,Double>>();
		invalidDocuments.put("only-user", document("only-term", 1.0));
		Map<String,String> invalidFollowees = new TreeMap<String,String>();
		invalidFollowees.put("only-user", "Only-Class");
		boolean failed = false;
		try
		{
			ScalableSvmSharedCoordinator.score(
					"ScalableFailureCleanup",
					runId,
					1,
					1,
					invalidDocuments,
					invalidFollowees,
					invalidFollowees,
					Arrays.asList("only-user"),
					false);
		}
		catch (IllegalArgumentException expected)
		{
			failed = true;
		}
		require(failed, "invalid shared scalable-SVM run did not fail");

		Map<String,LinkedHashMap<String,Double>> validDocuments = buildDocuments();
		Map<String,String> validFollowees = buildFollowees(validDocuments.keySet());
		ScalableSvmSharedCoordinator.Outcome recovered =
				ScalableSvmSharedCoordinator.score(
						"ScalableFailureCleanup",
						runId,
						1,
						1,
						validDocuments,
						validFollowees,
						validFollowees,
						Arrays.asList("alpha-target"),
						false);
		require(!recovered.getScores().isEmpty(),
				"failed shared run retained stale coordinator state");
	}

	private static LinkedHashMap<String,Double> document(
			String term,
			double frequency)
	{
		LinkedHashMap<String,Double> document =
				new LinkedHashMap<String,Double>();
		document.put(term, frequency);
		return document;
	}

	private static void requireDownstreamMergesEqual(
			ScalableSvmSharedCoordinator.Outcome one,
			List<ScalableSvmSharedCoordinator.Outcome> eight)
	{
		List<RecommendationScorePayload> oneDirect =
				new ArrayList<RecommendationScorePayload>();
		List<RecommendationScorePayload> eightDirect =
				new ArrayList<RecommendationScorePayload>();
		List<RecommendationScorePayload> oneGuided =
				new ArrayList<RecommendationScorePayload>();
		List<RecommendationScorePayload> eightGuided =
				new ArrayList<RecommendationScorePayload>();
		oneDirect.add(directPayload(one));
		oneGuided.add(guidedPayload(one));
		for (int index = 0; index < eight.size(); index++)
		{
			if (index == 0)
			{
				eightDirect.add(directPayload(eight.get(index)));
				eightGuided.add(guidedPayload(eight.get(index)));
			}
			else
			{
				eightDirect.add(emptyDirectPayload());
				eightGuided.add(emptyGuidedPayload());
			}
		}
		requireMergedResultsEqual(
				RecommendationScoreMerger.merge(oneDirect),
				RecommendationScoreMerger.merge(eightDirect),
				"direct followee");
		requireMergedResultsEqual(
				RecommendationScoreMerger.merge(oneGuided),
				RecommendationScoreMerger.merge(eightGuided),
				"classifier-guided user");
	}

	private static RecommendationScorePayload emptyDirectPayload()
	{
		return RecommendationScorePayload.direct(
				RecommendationTargetMode.FOLLOWEE_ACCOUNTS,
				RecommendationCandidateType.FOLLOWEE,
				RecommendationMergePolicy.SUM,
				TARGETS,
				new TreeMap<String,TreeMap<String,Double>>(),
				null);
	}

	private static RecommendationScorePayload emptyGuidedPayload()
	{
		return RecommendationScorePayload.classifierGuidedUsers(
				RecommendationTargetMode.SIMILAR_USERS,
				TARGETS,
				new TreeMap<String,TreeMap<String,Double>>(),
				new TreeMap<String,TreeMap<String,RecommendationCandidateEvidence>>(),
				null);
	}

	private static RecommendationScorePayload directPayload(
			ScalableSvmSharedCoordinator.Outcome outcome)
	{
		return RecommendationScorePayload.direct(
				RecommendationTargetMode.FOLLOWEE_ACCOUNTS,
				RecommendationCandidateType.FOLLOWEE,
				RecommendationMergePolicy.SUM,
				TARGETS,
				outcome.getScores(),
				null);
	}

	private static RecommendationScorePayload guidedPayload(
			ScalableSvmSharedCoordinator.Outcome outcome)
	{
		return RecommendationScorePayload.classifierGuidedUsers(
				RecommendationTargetMode.SIMILAR_USERS,
				TARGETS,
				outcome.getScores(),
				outcome.getEvidence(),
				null);
	}

	private static void requireMergedResultsEqual(
			Map<String,RecommendationResultPayload> expected,
			Map<String,RecommendationResultPayload> actual,
			String mode)
	{
		require(expected.keySet().equals(actual.keySet()),
				"one-node and eight-node " + mode + " result targets differ");
		for (String target : expected.keySet())
		{
			require(expected.get(target).getScores().equals(
							actual.get(target).getScores()),
					"one-node and eight-node " + mode
					+ " final recommendations differ for " + target
					+ "\none node: " + expected.get(target).getScores()
					+ "\neight nodes: " + actual.get(target).getScores());
		}
	}

	private static Map<String,LinkedHashMap<String,Double>> buildDocuments()
	{
		TreeMap<String,LinkedHashMap<String,Double>> documents =
				new TreeMap<String,LinkedHashMap<String,Double>>();
		for (int classIndex = 0; classIndex < 3; classIndex++)
		{
			String classTerm = classIndex == 0 ? "alpha"
					: classIndex == 1 ? "beta" : "gamma";
			for (int userIndex = 0; userIndex < 9; userIndex++)
			{
				String user = classTerm + "-"
						+ (userIndex == 0 ? "target" : "user-" + userIndex);
				LinkedHashMap<String,Double> document =
						new LinkedHashMap<String,Double>();
				document.put(classTerm, 4.0 + userIndex % 3);
				document.put("shared", 1.0);
				document.put("topic-" + (userIndex % 4), 1.0);
				document.put(classTerm + "-detail-" + (userIndex % 2), 2.0);
				documents.put(user, document);
			}
		}
		return documents;
	}

	private static Map<String,String> buildFollowees(Iterable<String> users)
	{
		TreeMap<String,String> followees = new TreeMap<String,String>();
		for (String user : users)
		{
			String followee = user.startsWith("alpha-") ? "Journal-A"
					: user.startsWith("beta-") ? "Journal-B" : "Journal-C";
			followees.put(user, followee);
		}
		return followees;
	}

	private static List<Map<String,LinkedHashMap<String,Double>>> buildShards(
			Map<String,LinkedHashMap<String,Double>> documents,
			int nodes)
	{
		List<Map<String,LinkedHashMap<String,Double>>> shards =
				new ArrayList<Map<String,LinkedHashMap<String,Double>>>();
		for (int index = 0; index < nodes; index++)
		{
			shards.add(new LinkedHashMap<String,LinkedHashMap<String,Double>>());
		}
		int assigned = 0;
		for (Map.Entry<String,LinkedHashMap<String,Double>> entry
				: documents.entrySet())
		{
			if (TARGETS.contains(entry.getKey()))
			{
				for (Map<String,LinkedHashMap<String,Double>> shard : shards)
				{
					shard.put(entry.getKey(), entry.getValue());
				}
			}
			else
			{
				shards.get(assigned % nodes).put(entry.getKey(), entry.getValue());
				assigned++;
			}
		}
		return shards;
	}

	private static void requireScoresEqual(
			Map<String,TreeMap<String,Double>> expected,
			Map<String,TreeMap<String,Double>> actual)
	{
		require(expected.equals(actual),
				"one-node and eight-node scalable-SVM class scores differ\n"
				+ "one node: " + expected + "\neight nodes: " + actual);
	}

	private static void requireEvidenceEqual(
			Map<String,TreeMap<String,RecommendationCandidateEvidence>> expected,
			Map<String,TreeMap<String,RecommendationCandidateEvidence>> actual)
	{
		require(expected.keySet().equals(actual.keySet()),
				"one-node and eight-node evidence targets differ");
		for (String target : expected.keySet())
		{
			Map<String,RecommendationCandidateEvidence> expectedCandidates =
					expected.get(target);
			Map<String,RecommendationCandidateEvidence> actualCandidates =
					actual.get(target);
			require(expectedCandidates.keySet().equals(actualCandidates.keySet()),
					"one-node and eight-node candidate evidence differ for " + target);
			for (String candidate : expectedCandidates.keySet())
			{
				RecommendationCandidateEvidence expectedEvidence =
						expectedCandidates.get(candidate);
				RecommendationCandidateEvidence actualEvidence =
						actualCandidates.get(candidate);
				require(expectedEvidence.getFollowee().equals(
								actualEvidence.getFollowee())
						&& Double.doubleToLongBits(
								expectedEvidence.getTextSimilarity())
							== Double.doubleToLongBits(
								actualEvidence.getTextSimilarity()),
						"one-node and eight-node similarity evidence differ for "
						+ target + " -> " + candidate);
			}
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
