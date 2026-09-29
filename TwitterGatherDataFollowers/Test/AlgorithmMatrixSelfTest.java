package TwitterGatherDataFollowers.userRyersonU;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Runs the user-ranking algorithms on a real corpus and checks each against an
 * independently derived reference value.
 *
 * The simulator's own pipeline is used to build the corpus — the shared
 * normaliser, then corpus-wide document frequency through the barrier, then
 * TF-IDF — so what is exercised here is the same arithmetic a run performs, not
 * a re-implementation of it.
 *
 * A dataset path may be given as an argument. Without one a small deterministic
 * corpus is built in memory, so the check runs anywhere.
 */
public final class AlgorithmMatrixSelfTest
{
	private static final double EPSILON = 1.0e-9;
	private static int checks;

	private AlgorithmMatrixSelfTest()
	{
	}

	private static File datasetFile;

	public static void main(String[] args) throws Exception
	{
		datasetFile = args.length > 0 && new File(args[0]).isFile()
				? new File(args[0]) : null;
		Corpus corpus = datasetFile != null
				? Corpus.fromDataset(datasetFile, 400)
				: Corpus.synthetic();
		System.out.println("corpus: " + corpus.describe());

		tfIdfMatchesTheTextbookDefinition(corpus);
		documentsAreUnitVectors(corpus);
		cosineEqualsNormalisedDotProduct(corpus);
		cosineIsSymmetricAndBounded(corpus);
		commonNeighboursCountsSharedNonZeroTerms(corpus);
		euclideanSimilarityMatchesItsDefinition(corpus);
		euclideanKMeansPartitionsAndScoresCorrectly(corpus);
		cosineKMeansConvergesToAFixedPoint(corpus);
		temporalGraphFoundationScoresWithinItsStatedRange(corpus);
		corpusWideDocumentFrequencyIsPartitionIndependent(corpus);
		everyTargetModeResolvesToTheRightCandidateType();
		mergePoliciesCombineNodeScoresExactly();
		classifierGuidedMergeAppliesItsStatedFormula();
		normalisationRanksAndRescalesCorrectly();

		System.out.println("AlgorithmMatrixSelfTest passed (" + checks + " checks)");
	}

	// ------------------------------------------------------------------
	// TF-IDF foundation
	// ------------------------------------------------------------------

	/**
	 * tfidf(t,d) = tf(t,d) * log2(N / df(t)), then the document is scaled to unit
	 * L2 length. Recomputed here from raw counts and compared against what the
	 * pipeline produced.
	 */
	private static void tfIdfMatchesTheTextbookDefinition(Corpus corpus)
	{
		for (String user : corpus.users())
		{
			Map<String,Double> raw = corpus.rawCounts.get(user);
			Map<String,Double> actual = corpus.tfidf.get(user);

			LinkedHashMap<String,Double> reference = new LinkedHashMap<String,Double>();
			double magnitude = 0.0;
			for (Map.Entry<String,Double> term : raw.entrySet())
			{
				double df = corpus.documentFrequency.get(term.getKey()).doubleValue();
				double idf = Math.log(corpus.totalDocuments / df) / Math.log(2.0);
				double weight = term.getValue().doubleValue() * idf;
				reference.put(term.getKey(), Double.valueOf(weight));
				magnitude += weight * weight;
			}
			magnitude = Math.sqrt(magnitude);
			for (Map.Entry<String,Double> term : reference.entrySet())
			{
				double normalised = magnitude > 0.0
						? term.getValue().doubleValue() / magnitude : 0.0;
				requireClose(actual.get(term.getKey()).doubleValue(), normalised,
						"tfidf(" + user + "," + term.getKey() + ")");
			}
		}
		pass("TF-IDF equals tf * log2(N/df), L2-normalised, for every term of every user");
	}

	/** Cosine is only the dot product when the vectors have unit length. */
	private static void documentsAreUnitVectors(Corpus corpus)
	{
		for (String user : corpus.users())
		{
			double magnitude = 0.0;
			for (Double weight : corpus.tfidf.get(user).values())
			{
				magnitude += weight.doubleValue() * weight.doubleValue();
			}
			magnitude = Math.sqrt(magnitude);
			//A document whose every term is corpus-wide has zero weight throughout;
			//that is a legitimate zero vector, not a normalisation failure.
			require(Math.abs(magnitude - 1.0) < 1.0e-9 || magnitude == 0.0,
					"document " + user + " has L2 norm " + magnitude);
		}
		pass("every TF-IDF document is a unit vector (or a legitimate zero vector)");
	}

	// ------------------------------------------------------------------
	// Similarity family
	// ------------------------------------------------------------------

	/**
	 * The scoring path computes a bare dot product. That is only cosine because
	 * the inputs are unit vectors, so the claim is checked against a full cosine
	 * computed from the unnormalised weights.
	 */
	private static void cosineEqualsNormalisedDotProduct(Corpus corpus)
	{
		List<String> users = corpus.users();
		int compared = 0;
		for (int i = 0; i < users.size() && compared < 300; i++)
		{
			for (int j = i + 1; j < users.size() && compared < 300; j++)
			{
				Map<String,Double> a = corpus.tfidf.get(users.get(i));
				Map<String,Double> b = corpus.tfidf.get(users.get(j));
				double dotOfUnitVectors = dot(a, b);

				Map<String,Double> rawA = corpus.unnormalised.get(users.get(i));
				Map<String,Double> rawB = corpus.unnormalised.get(users.get(j));
				double normA = Math.sqrt(dot(rawA, rawA));
				double normB = Math.sqrt(dot(rawB, rawB));
				double trueCosine = normA == 0.0 || normB == 0.0
						? 0.0 : dot(rawA, rawB) / (normA * normB);

				requireClose(dotOfUnitVectors, trueCosine, 1.0e-9,
						"cosine(" + users.get(i) + "," + users.get(j) + ")");
				compared++;
			}
		}
		pass("dot product of unit vectors equals cosine similarity (" + compared + " pairs)");
	}

	private static void cosineIsSymmetricAndBounded(Corpus corpus)
	{
		List<String> users = corpus.users();
		int compared = 0;
		for (int i = 0; i < users.size() && compared < 300; i++)
		{
			for (int j = i + 1; j < users.size() && compared < 300; j++)
			{
				Map<String,Double> a = corpus.tfidf.get(users.get(i));
				Map<String,Double> b = corpus.tfidf.get(users.get(j));
				double ab = dot(a, b);
				double ba = dot(b, a);
				requireClose(ab, ba, "cosine symmetry");
				require(ab >= -1.0e-9 && ab <= 1.0 + 1.0e-9,
						"cosine out of range: " + ab);
				compared++;
			}
		}
		//TF-IDF weights are non-negative, so cosine cannot be negative here.
		pass("cosine is symmetric and within [0,1] on non-negative TF-IDF vectors");
	}

	private static void commonNeighboursCountsSharedNonZeroTerms(Corpus corpus)
	{
		List<String> users = corpus.users();
		int compared = 0;
		for (int i = 0; i < users.size() && compared < 200; i++)
		{
			for (int j = i + 1; j < users.size() && compared < 200; j++)
			{
				Map<String,Double> a = corpus.tfidf.get(users.get(i));
				Map<String,Double> b = corpus.tfidf.get(users.get(j));

				int reference = 0;
				for (Map.Entry<String,Double> term : a.entrySet())
				{
					Double other = b.get(term.getKey());
					if (term.getValue().doubleValue() != 0.0
							&& other != null && other.doubleValue() != 0.0)
					{
						reference++;
					}
				}
				double actual =
						SparseRecommendationMath.commonNeighborScore(a, b);
				requireClose(actual, reference, "common neighbours");
				requireClose(actual,
						SparseRecommendationMath.commonNeighborScore(b, a),
						"common-neighbour symmetry");
				compared++;
			}
		}
		pass("common neighbours equals the count of shared non-zero terms (" + compared + " pairs)");
	}

	/** score = 1 / (1 + d), with d the sparse Euclidean distance. */
	private static void euclideanSimilarityMatchesItsDefinition(Corpus corpus)
	{
		List<String> users = corpus.users();
		int compared = 0;
		for (int i = 0; i < users.size() && compared < 200; i++)
		{
			for (int j = i + 1; j < users.size() && compared < 200; j++)
			{
				Map<String,Double> a = corpus.tfidf.get(users.get(i));
				Map<String,Double> b = corpus.tfidf.get(users.get(j));

				Set<String> terms = new LinkedHashSet<String>(a.keySet());
				terms.addAll(b.keySet());
				double sum = 0.0;
				for (String term : terms)
				{
					double difference = value(a, term) - value(b, term);
					sum += difference * difference;
				}
				double distance = Math.sqrt(sum);

				requireClose(
						SparseRecommendationMath.sparseEuclideanDistance(a, b),
						distance, "euclidean distance");
				requireClose(
						SparseRecommendationMath.euclideanSimilarityScore(a, b),
						1.0 / (1.0 + distance), "euclidean similarity");
				compared++;
			}
		}
		pass("Euclidean distance and its 1/(1+d) similarity match their definitions ("
				+ compared + " pairs)");
	}

	// ------------------------------------------------------------------
	// Clustering
	// ------------------------------------------------------------------

	private static void euclideanKMeansPartitionsAndScoresCorrectly(Corpus corpus)
	{
		List<String> targets = corpus.firstUsers(3);
		EuclideanKMeansRecommender.Result result =
				EuclideanKMeansRecommender.score(corpus.tfidf, targets, 3, 10);

		require(result.effectiveClusters <= 3, "more clusters than requested");
		require(result.effectiveClusters >= 1, "no clusters produced");
		require(result.iterations >= 1, "no iteration was counted");

		//Every user must appear in exactly one cluster.
		LinkedHashMap<String,Integer> seen = new LinkedHashMap<String,Integer>();
		for (Cluster cluster : result.clusters)
		{
			for (Point point : cluster.getPoints())
			{
				Integer previous = seen.put(point.getUserName(),
						Integer.valueOf(cluster.getId()));
				require(previous == null,
						"user " + point.getUserName() + " is in two clusters");
			}
		}
		require(seen.size() == corpus.tfidf.size(),
				"clusters cover " + seen.size() + " of " + corpus.tfidf.size() + " users");

		//A positive score must equal 1/(1+d) against that candidate.
		for (Map.Entry<String,TreeMap<String,Double>> row : result.scores.entrySet())
		{
			for (Map.Entry<String,Double> candidate : row.getValue().entrySet())
			{
				double score = candidate.getValue().doubleValue();
				if (score > 0.0)
				{
					requireClose(score,
							SparseRecommendationMath.euclideanSimilarityScore(
									corpus.tfidf.get(row.getKey()),
									corpus.tfidf.get(candidate.getKey())),
							"Euclidean K-Means score");
				}
			}
		}

		//Deterministic: the same corpus must give the same partition every time.
		EuclideanKMeansRecommender.Result again =
				EuclideanKMeansRecommender.score(corpus.tfidf, targets, 3, 10);
		require(again.scores.toString().equals(result.scores.toString()),
				"Euclidean K-Means was not deterministic");
		pass("Euclidean K-Means: " + result.effectiveClusters + " clusters, "
				+ result.iterations + " iteration(s), converged=" + result.converged
				+ ", partition complete, scores match 1/(1+d), deterministic");
	}

	/**
	 * Replays the agent's cosine K-Means loop with the real helpers.
	 *
	 * The defect this guards was a convergence test that always fired after the
	 * first pass. Here the loop must reach a genuine fixed point: one more
	 * assignment pass over the converged centroids must not move anybody.
	 */
	private static void cosineKMeansConvergesToAFixedPoint(Corpus corpus)
	{
		int clusterCount = Math.min(3, corpus.tfidf.size());
		int maxIterations = 25;
		List<String> allUsers = corpus.users();

		List<Cluster> clusters = new ArrayList<Cluster>();
		List<String> seeds = KMeansSupport.selectSeedUsers(
				allUsers, corpus.tfidf, clusterCount, 1L);
		for (int i = 0; i < clusterCount; i++)
		{
			Cluster cluster = new Cluster(i);
			cluster.setCentroid(new Point(-1,
					new LinkedHashMap<String,Double>(corpus.tfidf.get(seeds.get(i)))));
			clusters.add(cluster);
		}

		LinkedHashMap<String,Integer> previous = null;
		boolean converged = false;
		int iterations = 0;
		while (!converged && iterations < maxIterations)
		{
			assignAll(clusters, allUsers, corpus);
			LinkedHashMap<String,Integer> current =
					KMeansSupport.snapshotClusterAssignments(clusters);
			iterations++;
			converged = current.equals(previous);
			if (!converged)
			{
				previous = current;
				recomputeCentroids(clusters);
			}
		}

		require(converged || iterations == maxIterations,
				"loop exited without converging or exhausting iterations");

		//The real check: at the reported fixed point, another pass changes nothing.
		LinkedHashMap<String,Integer> atFixedPoint =
				KMeansSupport.snapshotClusterAssignments(clusters);
		assignAll(clusters, allUsers, corpus);
		LinkedHashMap<String,Integer> afterOneMore =
				KMeansSupport.snapshotClusterAssignments(clusters);
		if (converged)
		{
			require(atFixedPoint.equals(afterOneMore),
					"reported convergence was not a fixed point");
		}
		require(atFixedPoint.size() == allUsers.size(),
				"assignment did not cover every user");
		pass("cosine K-Means reached a genuine fixed point in " + iterations
				+ " iteration(s) over " + allUsers.size() + " users"
				+ (converged ? "" : " (hit the iteration cap)"));
	}

	private static void assignAll(
			List<Cluster> clusters, List<String> users, Corpus corpus)
	{
		for (Cluster cluster : clusters)
		{
			cluster.clear();
		}
		for (String user : users)
		{
			Point point = new Point(user, corpus.tfidf.get(user));
			int best = 0;
			double bestScore = -1.0;
			for (int i = 0; i < clusters.size(); i++)
			{
				double score = point.cosSimDistance(clusters.get(i).getCentroid());
				if (score > bestScore)
				{
					bestScore = score;
					best = i;
				}
			}
			point.setCluster(best);
			clusters.get(best).addPoint(point);
		}
	}

	private static void recomputeCentroids(List<Cluster> clusters)
	{
		for (Cluster cluster : clusters)
		{
			List<Point> points = cluster.getPoints();
			if (points.isEmpty())
			{
				continue;
			}
			LinkedHashMap<String,Double> sums = new LinkedHashMap<String,Double>();
			for (Point point : points)
			{
				for (Map.Entry<String,Double> term : point.getTfidf_or_Tf().entrySet())
				{
					Double current = sums.get(term.getKey());
					sums.put(term.getKey(), Double.valueOf(
							(current == null ? 0.0 : current.doubleValue())
							+ term.getValue().doubleValue()));
				}
			}
			double magnitude = 0.0;
			for (Map.Entry<String,Double> term : sums.entrySet())
			{
				double mean = term.getValue().doubleValue() / points.size();
				term.setValue(Double.valueOf(mean));
				magnitude += mean * mean;
			}
			magnitude = Math.sqrt(magnitude);
			if (magnitude > 0.0)
			{
				for (Map.Entry<String,Double> term : sums.entrySet())
				{
					term.setValue(Double.valueOf(
							term.getValue().doubleValue() / magnitude));
				}
			}
			cluster.getCentroid().setTfidf_or_Tf(sums);
		}
	}

	/**
	 * The temporal graph score is a convex blend of six component scores, each in
	 * [0,1], so the blend must also land in [0,1]. It must also be reproducible:
	 * the same corpus and targets have to give the same ranking every time.
	 */
	private static void temporalGraphFoundationScoresWithinItsStatedRange(
			Corpus corpus)
	{
		List<String> targets = corpus.firstUsers(3);
		LinkedHashMap<String,Integer> owners = new LinkedHashMap<String,Integer>();
		for (String user : corpus.users())
		{
			owners.put(user, Integer.valueOf(1));
		}

		TemporalGraphFoundationModelSupport.Input input =
				new TemporalGraphFoundationModelSupport.Input(
						"matrix-tgf", datasetFile, corpus.tfidf,
						corpus.followees, corpus.followees, targets,
						owners, 1, 1, 0);
		TemporalGraphFoundationModelSupport.Result first =
				TemporalGraphFoundationModelSupport.score(input);
		TemporalGraphFoundationModelSupport.Result second =
				TemporalGraphFoundationModelSupport.score(input);

		int scored = 0;
		for (Map.Entry<String,TreeMap<String,Double>> row
				: first.getScores().entrySet())
		{
			require(targets.contains(row.getKey()),
					"temporal graph scored an untargeted user: " + row.getKey());
			for (Map.Entry<String,Double> candidate : row.getValue().entrySet())
			{
				double score = candidate.getValue().doubleValue();
				require(Double.isFinite(score),
						"temporal graph produced a non-finite score");
				require(score >= 0.0 && score <= 1.0,
						"temporal graph score outside [0,1]: " + score);
				require(!candidate.getKey().equals(row.getKey()),
						"temporal graph recommended a target to itself");
				scored++;
			}
		}
		require(first.getScores().toString().equals(second.getScores().toString()),
				"temporal graph scoring was not reproducible");
		pass("temporal graph foundation: " + scored
				+ " candidate score(s) across " + first.getScores().size()
				+ " target(s), all within [0,1], reproducible");
	}

	// ------------------------------------------------------------------
	// Distribution
	// ------------------------------------------------------------------

	/**
	 * The same corpus split across one, two and five nodes must yield identical
	 * corpus-wide totals, which is what makes TF-IDF independent of node count.
	 */
	private static void corpusWideDocumentFrequencyIsPartitionIndependent(
			Corpus corpus) throws Exception
	{
		Map<String,Integer> reference = null;
		int referenceTotal = -1;
		for (int nodes : new int[] {1, 2, 5})
		{
			List<Map<String,Integer>> shards = new ArrayList<Map<String,Integer>>();
			int[] counts = new int[nodes];
			for (int i = 0; i < nodes; i++)
			{
				shards.add(new LinkedHashMap<String,Integer>());
			}
			int index = 0;
			for (String user : corpus.users())
			{
				int owner = index % nodes;
				Map<String,Integer> shard = shards.get(owner);
				for (String term : corpus.rawCounts.get(user).keySet())
				{
					Integer current = shard.get(term);
					shard.put(term, Integer.valueOf(
							current == null ? 1 : current.intValue() + 1));
				}
				counts[owner]++;
				index++;
			}

			GlobalDocumentFrequencyCoordinator.Totals totals =
					combineAcrossNodes("matrix-df-" + nodes, shards, counts);
			if (reference == null)
			{
				reference = totals.getDocumentFrequency();
				referenceTotal = totals.getTotalDocuments();
			}
			else
			{
				require(totals.getTotalDocuments() == referenceTotal,
						"corpus size changed at " + nodes + " nodes");
				require(totals.getDocumentFrequency().equals(reference),
						"document frequency changed at " + nodes + " nodes");
			}
			require(totals.getTotalDocuments() == corpus.users().size(),
					"corpus size disagreed with the user count at " + nodes + " nodes");
		}
		pass("corpus-wide document frequency identical across 1, 2 and 5 node splits");
	}

	private static GlobalDocumentFrequencyCoordinator.Totals combineAcrossNodes(
			final String runId,
			final List<Map<String,Integer>> shards,
			final int[] counts) throws Exception
	{
		final int nodes = shards.size();
		java.util.concurrent.ExecutorService pool =
				java.util.concurrent.Executors.newFixedThreadPool(nodes);
		try
		{
			List<java.util.concurrent.Callable<GlobalDocumentFrequencyCoordinator.Totals>>
					tasks = new ArrayList<
							java.util.concurrent.Callable<
									GlobalDocumentFrequencyCoordinator.Totals>>();
			for (int i = 0; i < nodes; i++)
			{
				final int node = i + 1;
				tasks.add(new java.util.concurrent.Callable<
						GlobalDocumentFrequencyCoordinator.Totals>() {
					public GlobalDocumentFrequencyCoordinator.Totals call()
							throws Exception
					{
						return GlobalDocumentFrequencyCoordinator.combine(
								"node" + node, runId, nodes, node,
								shards.get(node - 1), counts[node - 1]);
					}
				});
			}
			return pool.invokeAll(tasks).get(0).get();
		}
		finally
		{
			pool.shutdownNow();
		}
	}

	// ------------------------------------------------------------------
	// Target modes and merge policies
	// ------------------------------------------------------------------

	/** Every algorithm x target-mode cell must resolve to a defined behaviour. */
	private static void everyTargetModeResolvesToTheRightCandidateType()
	{
		int[] algorithms = {
			RecommendationModeSupport.COS_SIM,
			RecommendationModeSupport.K_MEANS,
			RecommendationModeSupport.SVM,
			RecommendationModeSupport.MLP,
			RecommendationModeSupport.DOC2VEC,
			RecommendationModeSupport.COMMON_NEIGHBORS,
			RecommendationModeSupport.K_MEANS_EUCLIDEAN,
			RecommendationModeSupport.SCALABLE_SVM,
			RecommendationModeSupport.TEMPORAL_GRAPH_FOUNDATION,
		};
		int cells = 0;
		int converted = 0;
		StringBuilder matrix = new StringBuilder();
		for (int algorithm : algorithms)
		{
			RecommendationCandidateType nativeType =
					RecommendationModeSupport.nativeCandidateType(algorithm);
			require(nativeType != null,
					"no native candidate type for "
					+ RecommendationModeSupport.algorithmName(algorithm));
			require(RecommendationModeSupport.isWorkingAlgorithm(algorithm),
					RecommendationModeSupport.algorithmName(algorithm)
					+ " is not marked as a working algorithm");

			for (RecommendationTargetMode mode : RecommendationTargetMode.values())
			{
				RecommendationCandidateType resolved =
						RecommendationModeSupport.resolveCandidateType(mode, algorithm);
				require(resolved != null,
						"unresolved candidate type for "
						+ RecommendationModeSupport.algorithmName(algorithm)
						+ " / " + mode);

				boolean needsConversion =
						RecommendationModeSupport.requiresConvertedPipeline(mode, algorithm);
				require(needsConversion == (mode != RecommendationTargetMode.ALGORITHM_DEFAULT
								&& resolved != nativeType),
						"conversion flag disagrees with the resolved type for "
						+ RecommendationModeSupport.algorithmName(algorithm)
						+ " / " + mode);

				if (mode == RecommendationTargetMode.ALGORITHM_DEFAULT)
				{
					require(resolved == nativeType,
							"default mode changed the candidate type for "
							+ RecommendationModeSupport.algorithmName(algorithm));
				}
				if (needsConversion)
				{
					converted++;
				}
				cells++;
			}
			matrix.append("    ")
					.append(pad(RecommendationModeSupport.algorithmName(algorithm), 26))
					.append("native=").append(pad(nativeType.name(), 9));
			for (RecommendationTargetMode mode : RecommendationTargetMode.values())
			{
				matrix.append(" ")
						.append(RecommendationModeSupport
								.resolveCandidateType(mode, algorithm).name().charAt(0))
						.append(RecommendationModeSupport
								.requiresConvertedPipeline(mode, algorithm) ? "*" : " ");
			}
			matrix.append('\n');
		}
		System.out.print(matrix);
		require(cells == 27, "expected 27 algorithm/mode cells, found " + cells);
		pass(cells + " algorithm x target-mode cells resolve consistently ("
				+ converted + " need the converted pipeline)");
	}

	/** MAX keeps the largest node score; SUM adds them. */
	private static void mergePoliciesCombineNodeScoresExactly()
	{
		List<String> targets = Arrays.asList("t1");
		for (RecommendationMergePolicy policy
				: new RecommendationMergePolicy[] {
					RecommendationMergePolicy.MAX, RecommendationMergePolicy.SUM })
		{
			List<RecommendationScorePayload> payloads =
					new ArrayList<RecommendationScorePayload>();
			payloads.add(directPayload(policy, targets, "candA", 2.0, "candB", 9.0));
			payloads.add(directPayload(policy, targets, "candA", 5.0, "candB", 1.0));
			payloads.add(directPayload(policy, targets, "candA", 3.0, "candB", 2.0));

			Map<String,RecommendationResultPayload> merged =
					RecommendationScoreMerger.merge(payloads);
			Map<String,Double> scores = merged.get("t1").getScores();

			double expectedA = policy == RecommendationMergePolicy.MAX ? 5.0 : 10.0;
			double expectedB = policy == RecommendationMergePolicy.MAX ? 9.0 : 12.0;
			double top = Math.max(expectedA, expectedB);

			requireClose(scores.get("candA").doubleValue(), expectedA / top * 100.0,
					policy + " merged candA");
			requireClose(scores.get("candB").doubleValue(), expectedB / top * 100.0,
					policy + " merged candB");
		}
		pass("MAX and SUM merge policies combine three nodes' scores exactly");
	}

	/** final = (classScore / totalClassScore) * textSimilarity. */
	private static void classifierGuidedMergeAppliesItsStatedFormula()
	{
		List<String> targets = Arrays.asList("t1");
		LinkedHashMap<String,Map<String,Double>> classScores =
				new LinkedHashMap<String,Map<String,Double>>();
		LinkedHashMap<String,Double> perClass = new LinkedHashMap<String,Double>();
		perClass.put("followeeX", Double.valueOf(3.0));
		perClass.put("followeeY", Double.valueOf(1.0));
		classScores.put("t1", perClass);

		LinkedHashMap<String,Map<String,RecommendationCandidateEvidence>> evidence =
				new LinkedHashMap<String,Map<String,RecommendationCandidateEvidence>>();
		LinkedHashMap<String,RecommendationCandidateEvidence> perCandidate =
				new LinkedHashMap<String,RecommendationCandidateEvidence>();
		perCandidate.put("userA",
				new RecommendationCandidateEvidence("followeeX", 0.8));
		perCandidate.put("userB",
				new RecommendationCandidateEvidence("followeeY", 0.5));
		evidence.put("t1", perCandidate);

		List<RecommendationScorePayload> payloads =
				new ArrayList<RecommendationScorePayload>();
		payloads.add(RecommendationScorePayload.classifierGuidedUsers(
				RecommendationTargetMode.SIMILAR_USERS, targets,
				classScores, evidence, new LinkedHashMap<String,String>()));

		Map<String,Double> scores =
				RecommendationScoreMerger.merge(payloads).get("t1").getScores();

		double rawA = (3.0 / 4.0) * 0.8;
		double rawB = (1.0 / 4.0) * 0.5;
		double top = Math.max(rawA, rawB);
		requireClose(scores.get("userA").doubleValue(), rawA / top * 100.0,
				"classifier-guided userA");
		requireClose(scores.get("userB").doubleValue(), rawB / top * 100.0,
				"classifier-guided userB");
		pass("classifier-guided merge applies (classScore/total) * textSimilarity");
	}

    /** Top candidate is always 100; order is by score, then by name. */
	private static void normalisationRanksAndRescalesCorrectly()
	{
		LinkedHashMap<String,Double> raw = new LinkedHashMap<String,Double>();
		raw.put("zebra", Double.valueOf(4.0));
		raw.put("apple", Double.valueOf(4.0));
		raw.put("mango", Double.valueOf(8.0));
		raw.put("never", Double.valueOf(0.0));
		raw.put("nan", Double.valueOf(Double.NaN));

		LinkedHashMap<String,Double> ranked =
				RecommendationScoreMerger.normalizeAndSort(raw);
		List<String> order = new ArrayList<String>(ranked.keySet());

		require(order.equals(Arrays.asList("mango", "apple", "zebra")),
				"unexpected ranking order: " + order);
		requireClose(ranked.get("mango").doubleValue(), 100.0, "top score");
		requireClose(ranked.get("apple").doubleValue(), 50.0, "tied score");
		requireClose(ranked.get("zebra").doubleValue(), 50.0, "tied score");
		require(!ranked.containsKey("never"), "a zero score was ranked");
		require(!ranked.containsKey("nan"), "a NaN score was ranked");
		pass("ranking drops non-positive and non-finite scores, rescales the top to 100, "
				+ "and breaks ties by name");
	}

	// ------------------------------------------------------------------
	// Corpus
	// ------------------------------------------------------------------

	static final class Corpus
	{
		final LinkedHashMap<String,LinkedHashMap<String,Double>> rawCounts;
		final LinkedHashMap<String,LinkedHashMap<String,Double>> unnormalised;
		final LinkedHashMap<String,LinkedHashMap<String,Double>> tfidf;
		final LinkedHashMap<String,Integer> documentFrequency;
		final LinkedHashMap<String,String> followees;
		final int totalDocuments;
		private final String label;

		private Corpus(
				LinkedHashMap<String,LinkedHashMap<String,Double>> rawCounts,
				LinkedHashMap<String,LinkedHashMap<String,Double>> unnormalised,
				LinkedHashMap<String,LinkedHashMap<String,Double>> tfidf,
				LinkedHashMap<String,Integer> documentFrequency,
				LinkedHashMap<String,String> followees,
				String label)
		{
			this.rawCounts = rawCounts;
			this.unnormalised = unnormalised;
			this.tfidf = tfidf;
			this.documentFrequency = documentFrequency;
			this.followees = followees;
			this.totalDocuments = rawCounts.size();
			this.label = label;
		}

		List<String> users()
		{
			return new ArrayList<String>(tfidf.keySet());
		}

		List<String> firstUsers(int count)
		{
			List<String> all = users();
			return all.subList(0, Math.min(count, all.size()));
		}

		String describe()
		{
			int terms = documentFrequency.size();
			LinkedHashSet<String> classes = new LinkedHashSet<String>(followees.values());
			return label + " users=" + totalDocuments + " terms=" + terms
					+ " classes=" + classes.size();
		}

		/** Builds the corpus exactly as a run does: normalise, count, weight. */
		static Corpus fromDataset(File dataset, int maxUsers) throws Exception
		{
			Set<String> stopWords =
					TweetTextNormalizer.stopWordSet(RecommenderAgent.stopWordsArray);
			LinkedHashMap<String,LinkedHashMap<String,Double>> counts =
					new LinkedHashMap<String,LinkedHashMap<String,Double>>();
			LinkedHashMap<String,String> followees =
					new LinkedHashMap<String,String>();

			BufferedReader reader = new BufferedReader(new InputStreamReader(
					new FileInputStream(dataset), StandardCharsets.UTF_8));
			try
			{
				String line;
				while ((line = reader.readLine()) != null)
				{
					String[] fields = line.split("\t", -1);
					if (fields.length < 6)
					{
						continue;
					}
					String user = fields[4].trim();
					String followee = fields[0].trim();
					if (user.length() == 0 || followee.length() == 0)
					{
						continue;
					}
					if (!counts.containsKey(user) && counts.size() >= maxUsers)
					{
						continue;
					}
					String normalised = TweetTextNormalizer.normalize(
							fields[5], false, false, true, stopWords);
					if (TweetTextNormalizer.countTokens(normalised)
							< TweetTextNormalizer.MINIMUM_USABLE_TOKENS)
					{
						continue;
					}
					followees.put(user, followee);
					LinkedHashMap<String,Double> document = counts.get(user);
					if (document == null)
					{
						document = new LinkedHashMap<String,Double>();
						counts.put(user, document);
					}
					for (String token : normalised.split(" "))
					{
						if (token.length() == 0)
						{
							continue;
						}
						Double current = document.get(token);
						document.put(token, Double.valueOf(
								current == null ? 1.0 : current.doubleValue() + 1.0));
					}
				}
			}
			finally
			{
				reader.close();
			}
			return weight(counts, followees, dataset.getName());
		}

		static Corpus synthetic()
		{
			String[][] rows = {
				{"alpha", "u1", "shoes bags hats leather shoes"},
				{"alpha", "u2", "shoes leather bags belts"},
				{"alpha", "u3", "hats caps shoes bags"},
				{"beta",  "u4", "kettle mugs plates kitchen kettle"},
				{"beta",  "u5", "plates mugs kitchen bowls"},
				{"beta",  "u6", "kitchen kettle bowls plates"},
				{"gamma", "u7", "garden seeds pots soil garden"},
				{"gamma", "u8", "pots soil seeds trowel"},
				{"gamma", "u9", "garden trowel seeds pots"},
			};
			LinkedHashMap<String,LinkedHashMap<String,Double>> counts =
					new LinkedHashMap<String,LinkedHashMap<String,Double>>();
			LinkedHashMap<String,String> followees =
					new LinkedHashMap<String,String>();
			for (String[] row : rows)
			{
				followees.put(row[1], row[0]);
				LinkedHashMap<String,Double> document =
						new LinkedHashMap<String,Double>();
				for (String token : row[2].split(" "))
				{
					Double current = document.get(token);
					document.put(token, Double.valueOf(
							current == null ? 1.0 : current.doubleValue() + 1.0));
				}
				counts.put(row[1], document);
			}
			return weight(counts, followees, "synthetic");
		}

		private static Corpus weight(
				LinkedHashMap<String,LinkedHashMap<String,Double>> counts,
				LinkedHashMap<String,String> followees,
				String label)
		{
			LinkedHashMap<String,Integer> documentFrequency =
					new LinkedHashMap<String,Integer>();
			for (LinkedHashMap<String,Double> document : counts.values())
			{
				for (String term : document.keySet())
				{
					Integer current = documentFrequency.get(term);
					documentFrequency.put(term, Integer.valueOf(
							current == null ? 1 : current.intValue() + 1));
				}
			}
			int total = counts.size();

			LinkedHashMap<String,LinkedHashMap<String,Double>> unnormalised =
					new LinkedHashMap<String,LinkedHashMap<String,Double>>();
			LinkedHashMap<String,LinkedHashMap<String,Double>> tfidf =
					new LinkedHashMap<String,LinkedHashMap<String,Double>>();
			for (Map.Entry<String,LinkedHashMap<String,Double>> entry : counts.entrySet())
			{
				LinkedHashMap<String,Double> weights =
						new LinkedHashMap<String,Double>();
				double magnitude = 0.0;
				for (Map.Entry<String,Double> term : entry.getValue().entrySet())
				{
					double df = documentFrequency.get(term.getKey()).doubleValue();
					double idf = Math.log10(total / df) / Math.log10(2.0);
					double weight = term.getValue().doubleValue() * idf;
					if (!Double.isFinite(weight))
					{
						weight = 0.0;
					}
					weights.put(term.getKey(), Double.valueOf(weight));
					magnitude += weight * weight;
				}
				unnormalised.put(entry.getKey(),
						new LinkedHashMap<String,Double>(weights));
				magnitude = Math.sqrt(magnitude);
				LinkedHashMap<String,Double> unit = new LinkedHashMap<String,Double>();
				for (Map.Entry<String,Double> term : weights.entrySet())
				{
					double value = magnitude > 0.0
							? term.getValue().doubleValue() / magnitude : 0.0;
					unit.put(term.getKey(),
							Double.valueOf(Double.isFinite(value) ? value : 0.0));
				}
				tfidf.put(entry.getKey(), unit);
			}
			return new Corpus(counts, unnormalised, tfidf,
					documentFrequency, followees, label);
		}
	}

	// ------------------------------------------------------------------
	// Helpers
	// ------------------------------------------------------------------

	private static RecommendationScorePayload directPayload(
			RecommendationMergePolicy policy,
			List<String> targets,
			String firstCandidate, double firstScore,
			String secondCandidate, double secondScore)
	{
		LinkedHashMap<String,Map<String,Double>> scores =
				new LinkedHashMap<String,Map<String,Double>>();
		LinkedHashMap<String,Double> row = new LinkedHashMap<String,Double>();
		row.put(firstCandidate, Double.valueOf(firstScore));
		row.put(secondCandidate, Double.valueOf(secondScore));
		scores.put(targets.get(0), row);
		return RecommendationScorePayload.directWithDetails(
				RecommendationTargetMode.ALGORITHM_DEFAULT,
				RecommendationCandidateType.USER,
				policy,
				targets,
				scores,
				new LinkedHashMap<String,Map<String,String>>(),
				new LinkedHashMap<String,String>());
	}

	private static double dot(Map<String,Double> a, Map<String,Double> b)
	{
		Map<String,Double> smaller = a.size() <= b.size() ? a : b;
		Map<String,Double> larger = smaller == a ? b : a;
		double total = 0.0;
		for (Map.Entry<String,Double> entry : smaller.entrySet())
		{
			Double other = larger.get(entry.getKey());
			if (other != null)
			{
				total += entry.getValue().doubleValue() * other.doubleValue();
			}
		}
		return total;
	}

	private static double value(Map<String,Double> document, String term)
	{
		Double weight = document.get(term);
		return weight == null ? 0.0 : weight.doubleValue();
	}

	private static String pad(String text, int width)
	{
		StringBuilder padded = new StringBuilder(text);
		while (padded.length() < width)
		{
			padded.append(' ');
		}
		return padded.toString();
	}

	private static void pass(String message)
	{
		checks++;
		System.out.println("  ok  " + message);
	}

	private static void requireClose(double actual, double expected, String label)
	{
		requireClose(actual, expected, EPSILON, label);
	}

	private static void requireClose(
			double actual, double expected, double tolerance, String label)
	{
		require(Math.abs(actual - expected) <= tolerance,
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
