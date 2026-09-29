package TwitterGatherDataFollowers.userRyersonU;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Exact distributed K-Means: the clustering one node would compute over the
 * whole corpus, computed by N nodes that each hold only their own users.
 *
 * Each node assigns only the users it owns. Everything that needs the whole
 * corpus goes through {@link RunCollectives}: the name-sorted user list for
 * seeding, the farthest-first seed search (a global arg-max per seed), the
 * cluster centroids (sums of member vectors kept as {@link ExactSum}s, so the
 * rounded result is the same for every partition), cluster sizes, and the
 * convergence test (no user changed cluster anywhere). All ties are broken by
 * user name or cluster index. The clusters, and therefore the recommendations,
 * are identical for every node count, every placement of users onto nodes and
 * every schedule; with one node this is the single-node algorithm.
 *
 * Two variants reproduce the simulator's two K-Means recommenders:
 * <ul>
 * <li>{@link #cosine}: cosine assignment to unit-normalised centroids, seeds
 *     by farthest-first from a seeded start, scores = cosine between target and
 *     candidate for candidates in the target's cluster;</li>
 * <li>{@link #euclidean}: Euclidean assignment to mean centroids, empty
 *     clusters repaired with the farthest movable user, scores = 1 / (1 +
 *     distance) within the cluster, or over all candidates for a target whose
 *     cluster yields no positive score.</li>
 * </ul>
 * Each candidate's score is published only by the node that owns it.
 */
final class DistributedKMeans
{
	private DistributedKMeans()
	{
	}

	/** Scores for this node's local target rows and a description of the run. */
	static final class Result
	{
		private final TreeMap<String,TreeMap<String,Double>> scores;
		private final int clusters;
		private final int iterations;
		private final boolean converged;
		private final int[] clusterSizes;
		private final Map<String,Integer> ownedAssignments;

		private Result(TreeMap<String,TreeMap<String,Double>> scores, int clusters,
				int iterations, boolean converged, int[] clusterSizes,
				Map<String,Integer> ownedAssignments)
		{
			this.scores = scores;
			this.clusters = clusters;
			this.iterations = iterations;
			this.converged = converged;
			this.clusterSizes = clusterSizes;
			this.ownedAssignments = ownedAssignments;
		}

		TreeMap<String,TreeMap<String,Double>> getScores()
		{
			return scores;
		}

		int getClusters()
		{
			return clusters;
		}

		int getIterations()
		{
			return iterations;
		}

		boolean isConverged()
		{
			return converged;
		}

		int[] getClusterSizes()
		{
			return clusterSizes.clone();
		}

		/** Final cluster of each user this node owns. */
		Map<String,Integer> getOwnedAssignments()
		{
			return ownedAssignments;
		}
	}

	/** A node's farthest candidate in one seeding or repair round. */
	private static final class Proposal
	{
		private final String user;
		private final double distance;
		private final Map<String,Double> vector;

		private Proposal(String user, double distance, Map<String,Double> vector)
		{
			this.user = user;
			this.distance = distance;
			this.vector = vector;
		}
	}

	/** Partial centroid sums of one node for one iteration. */
	private static final class Partial
	{
		private final List<Map<String,ExactSum>> sums;
		private final int[] counts;
		private final int changed;

		private Partial(List<Map<String,ExactSum>> sums, int[] counts, int changed)
		{
			this.sums = sums;
			this.counts = counts;
			this.changed = changed;
		}
	}

	/** Centroids and global counts after one iteration, shared by every node. */
	private static final class Round
	{
		private final List<Map<String,Double>> centroids;
		private final int[] sizes;
		private final int changed;

		private Round(List<Map<String,Double>> centroids, int[] sizes, int changed)
		{
			this.centroids = centroids;
			this.sizes = sizes;
			this.changed = changed;
		}
	}

	// ================================================================ cosine

	/**
	 * Cosine K-Means with deterministic farthest-first seeding.
	 *
	 * @param owned       TF-IDF vectors of the users this node owns
	 * @param localUsers  every user this node holds (for zero-initialised rows)
	 * @param targets     recommendation targets
	 * @param targetVectors TF-IDF vectors of the targets available on this node
	 */
	static Result cosine(
			RunCollectives collectives,
			String stepPrefix,
			int node,
			Map<String,? extends Map<String,Double>> owned,
			List<String> localUsers,
			List<String> targets,
			Map<String,? extends Map<String,Double>> targetVectors,
			int requestedClusters,
			int maxIterations,
			long clusterSeed)
	{
		List<String> allUsers = globalUsers(collectives, stepPrefix + "/users", node, owned);
		int n = allUsers.size();
		int k = requestedClusters;
		if (k > n)
		{
			k = n;
		}
		if (k < 2)
		{
			k = 2;
		}
		if (n == 0)
		{
			return new Result(zeroRows(targets, localUsers), k, 0, true, new int[k],
					new LinkedHashMap<String,Integer>());
		}

		//Seeds: the first by the seed index, then farthest-first by Euclidean
		//distance to the chosen seeds, ties to the smaller name.
		ArrayList<String> seeds = new ArrayList<String>();
		ArrayList<Map<String,Double>> seedVectors = new ArrayList<Map<String,Double>>();
		String first = allUsers.get((int)Math.floorMod(clusterSeed, (long)n));
		seeds.add(first);
		seedVectors.add(shareVector(collectives, stepPrefix + "/seed0", node, owned, first));
		java.util.HashSet<String> seedLookup = new java.util.HashSet<String>(seeds);
		while (seeds.size() < k)
		{
			Proposal local = null;
			for (Map.Entry<String,? extends Map<String,Double>> entry : sortedEntries(owned))
			{
				String user = entry.getKey();
				if (seedLookup.contains(user))
				{
					continue;
				}
				double nearest = Double.POSITIVE_INFINITY;
				for (Map<String,Double> seedVector : seedVectors)
				{
					double distance = SparseRecommendationMath.sparseEuclideanDistance(
							entry.getValue(), seedVector);
					if (distance < nearest)
					{
						nearest = distance;
					}
				}
				local = better(local, new Proposal(user, nearest, entry.getValue()));
			}
			Proposal chosen = globalBest(collectives, stepPrefix + "/seed" + seeds.size(), node, local);
			if (chosen == null)
			{
				break;
			}
			seeds.add(chosen.user);
			seedVectors.add(chosen.vector);
			seedLookup.add(chosen.user);
		}
		//Fewer distinct users than clusters: repeat users by name, as one node does.
		while (seeds.size() < k)
		{
			String user = allUsers.get(seeds.size() % n);
			seeds.add(user);
			seedVectors.add(shareVector(collectives, stepPrefix + "/seedfill" + seeds.size(),
					node, owned, user));
		}

		//Pass 0: every seed starts its own cluster; every other user joins the
		//nearest seed by cosine, ties to the lower cluster index.
		List<Map<String,Double>> centroids = new ArrayList<Map<String,Double>>();
		for (Map<String,Double> seedVector : seedVectors)
		{
			centroids.add(new HashMap<String,Double>(seedVector));
		}
		ArrayList<String> remaining = new ArrayList<String>(sortedKeys(owned));
		Map<String,Integer> assignment = new LinkedHashMap<String,Integer>();
		List<List<String>> memberLists = new ArrayList<List<String>>();
		for (int cluster = 0; cluster < k; cluster++)
		{
			memberLists.add(new ArrayList<String>());
		}
		for (int cluster = 0; cluster < k; cluster++)
		{
			String seed = seeds.get(cluster);
			if (owned.containsKey(seed))
			{
				remaining.remove(seed);
				memberLists.get(cluster).add(seed);
				assignment.put(seed, Integer.valueOf(cluster));
			}
		}
		for (String user : remaining)
		{
			int cluster = nearestByCosine(owned.get(user), centroids);
			memberLists.get(cluster).add(user);
			assignment.put(user, Integer.valueOf(cluster));
		}
		Round round = combineCosine(collectives, stepPrefix + "/pass0", node, owned,
				memberLists, centroids, 0);
		centroids = round.centroids;
		Map<String,Integer> previous = assignment;

		int iteration = 0;
		boolean converged = false;
		while (!converged && iteration < maxIterations)
		{
			Map<String,Integer> current = new LinkedHashMap<String,Integer>();
			List<List<String>> members = new ArrayList<List<String>>();
			for (int cluster = 0; cluster < k; cluster++)
			{
				members.add(new ArrayList<String>());
			}
			int changed = 0;
			for (String user : sortedKeys(owned))
			{
				int cluster = nearestByCosine(owned.get(user), centroids);
				members.get(cluster).add(user);
				current.put(user, Integer.valueOf(cluster));
				Integer before = previous.get(user);
				if (before == null || before.intValue() != cluster)
				{
					changed++;
				}
			}
			round = combineCosine(collectives, stepPrefix + "/iteration" + iteration, node,
					owned, members, centroids, changed);
			centroids = round.centroids;
			iteration++;
			converged = round.changed == 0;
			previous = current;
		}

		//Scores: each owned candidate in a target's cluster, target first.
		Map<String,Integer> targetClusters = targetAssignments(
				collectives, stepPrefix + "/targets", node, targets, previous);
		Map<String,Map<String,Double>> canonicalTargets = targetVectorsFromOwners(
				collectives, stepPrefix + "/targetvectors", node, targets, owned);
		TreeMap<String,TreeMap<String,Double>> scores = zeroRows(targets, localUsers);
		for (Map.Entry<String,TreeMap<String,Double>> row : scores.entrySet())
		{
			String target = row.getKey();
			Integer cluster = targetClusters.get(target);
			Map<String,Double> targetVector = canonicalTargets.get(target);
			if (cluster == null || targetVector == null)
			{
				continue;
			}
			for (String candidate : sortedKeys(owned))
			{
				if (candidate.equals(target)
						|| previous.get(candidate).intValue() != cluster.intValue())
				{
					continue;
				}
				row.getValue().put(candidate,
						unitVectorCosine(targetVector, owned.get(candidate)));
			}
		}
		return new Result(scores, k, iteration, converged, round.sizes, previous);
	}

	private static int nearestByCosine(Map<String,Double> vector, List<Map<String,Double>> centroids)
	{
		int best = 0;
		double highest = -1.0;
		for (int cluster = 0; cluster < centroids.size(); cluster++)
		{
			double similarity = cosineToCentroid(vector, centroids.get(cluster));
			if (similarity > highest)
			{
				highest = similarity;
				best = cluster;
			}
		}
		return best;
	}

	/** Same arithmetic as {@link Point#cosSimDistance}: iterate the point's terms. */
	private static double cosineToCentroid(Map<String,Double> point, Map<String,Double> centroid)
	{
		double sum = 0.0;
		if (point == null)
		{
			return sum;
		}
		for (Map.Entry<String,Double> entry : point.entrySet())
		{
			Double value = centroid.get(entry.getKey());
			if (value != null)
			{
				sum += entry.getValue().doubleValue() * value.doubleValue();
			}
		}
		return sum;
	}

	/**
	 * Mean of the members, then unit-normalised over its positive terms, as the
	 * single-node recommender does; an empty cluster keeps its centroid.
	 */
	private static Round combineCosine(
			RunCollectives collectives,
			String step,
			int node,
			final Map<String,? extends Map<String,Double>> owned,
			List<List<String>> members,
			final List<Map<String,Double>> previousCentroids,
			int changed)
	{
		final int k = members.size();
		List<Map<String,ExactSum>> sums = new ArrayList<Map<String,ExactSum>>();
		int[] counts = new int[k];
		for (int cluster = 0; cluster < k; cluster++)
		{
			HashMap<String,ExactSum> clusterSums = new HashMap<String,ExactSum>();
			for (String user : members.get(cluster))
			{
				counts[cluster]++;
				accumulate(clusterSums, owned.get(user));
			}
			sums.add(clusterSums);
		}
		return collectives.allReduce(step, node, new Partial(sums, counts, changed),
				new RunCollectives.Reducer<Round>()
				{
					public Round reduce(List<Object> contributions)
					{
						Partial total = merge(contributions, k);
						List<Map<String,Double>> centroids = new ArrayList<Map<String,Double>>();
						for (int cluster = 0; cluster < k; cluster++)
						{
							int count = total.counts[cluster];
							if (count <= 0)
							{
								centroids.add(previousCentroids.get(cluster));
								continue;
							}
							TreeMap<String,Double> mean = new TreeMap<String,Double>();
							ExactSum magnitude = new ExactSum();
							for (Map.Entry<String,ExactSum> entry : new TreeMap<String,ExactSum>(
									total.sums.get(cluster)).entrySet())
							{
								double value = entry.getValue().toDouble();
								if (value > 0.0)
								{
									value = value / count;
									magnitude.add(value * value);
								}
								mean.put(entry.getKey(), Double.valueOf(value));
							}
							double length = Math.sqrt(magnitude.toDouble());
							HashMap<String,Double> centroid = new HashMap<String,Double>();
							for (Map.Entry<String,Double> entry : mean.entrySet())
							{
								double value = entry.getValue().doubleValue();
								centroid.put(entry.getKey(),
										Double.valueOf(value > 0.0 ? value / length : value));
							}
							centroids.add(centroid);
						}
						return new Round(centroids, total.counts, total.changed);
					}
				});
	}

	// ============================================================= euclidean

	/**
	 * Euclidean K-Means with farthest-first seeding from the smallest name and
	 * empty-cluster repair.
	 */
	static Result euclidean(
			RunCollectives collectives,
			String stepPrefix,
			int node,
			Map<String,? extends Map<String,Double>> owned,
			List<String> localUsers,
			List<String> targets,
			int requestedClusters,
			int maxIterations)
	{
		List<String> allUsers = globalUsers(collectives, stepPrefix + "/users", node, owned);
		int n = allUsers.size();
		TreeMap<String,TreeMap<String,Double>> scores = zeroRows(targets, localUsers);
		//Only global facts may end the algorithm early: a node without target
		//rows must still take part in every collective step.
		if (n == 0)
		{
			return new Result(scores, 0, 0, true, new int[0], new LinkedHashMap<String,Integer>());
		}
		int k = Math.min(Math.max(1, requestedClusters), n);
		int iterationsLimit = Math.max(1, maxIterations);

		//Seeds: the smallest name, then farthest-first, skipping exact duplicates
		//of an existing centroid, ties to the smaller name.
		List<Map<String,Double>> centroids = new ArrayList<Map<String,Double>>();
		centroids.add(sortedNonZeroCopy(
				shareVector(collectives, stepPrefix + "/seed0", node, owned, allUsers.get(0))));
		while (centroids.size() < k)
		{
			Proposal local = null;
			for (Map.Entry<String,? extends Map<String,Double>> entry : sortedEntries(owned))
			{
				Map<String,Double> vector = entry.getValue();
				boolean duplicate = false;
				double nearest = Double.POSITIVE_INFINITY;
				for (Map<String,Double> centroid : centroids)
				{
					double distance = SparseRecommendationMath.sparseEuclideanDistance(vector, centroid);
					if (distance == 0.0)
					{
						duplicate = true;
						break;
					}
					nearest = Math.min(nearest, distance);
				}
				if (!duplicate)
				{
					local = better(local, new Proposal(entry.getKey(), nearest, vector));
				}
			}
			Proposal chosen = globalBest(collectives, stepPrefix + "/seed" + centroids.size(),
					node, local);
			if (chosen == null)
			{
				break;
			}
			centroids.add(sortedNonZeroCopy(chosen.vector));
		}

		Map<String,Integer> assignments = null;
		boolean converged = false;
		int iterations = 0;
		int[] sizes = new int[k];
		for (int iteration = 0; iteration < iterationsLimit; iteration++)
		{
			Map<String,Integer> fresh = new LinkedHashMap<String,Integer>();
			int[] localCounts = new int[k];
			for (Map.Entry<String,? extends Map<String,Double>> entry : sortedEntries(owned))
			{
				int cluster = nearestByDistance(entry.getValue(), centroids);
				fresh.put(entry.getKey(), Integer.valueOf(cluster));
				localCounts[cluster]++;
			}
			sizes = sumCounts(collectives, stepPrefix + "/sizes" + iteration, node, localCounts);
			repairEmptyClusters(collectives, stepPrefix + "/repair" + iteration, node, owned,
					fresh, centroids, sizes);
			iterations++;
			int changed = 0;
			for (Map.Entry<String,Integer> entry : fresh.entrySet())
			{
				Integer before = assignments == null ? null : assignments.get(entry.getKey());
				if (before == null || before.intValue() != entry.getValue().intValue())
				{
					changed++;
				}
			}
			Round round = combineMeans(collectives, stepPrefix + "/means" + iteration, node,
					owned, fresh, k, assignments == null ? 1 : changed);
			assignments = fresh;
			centroids = round.centroids;
			sizes = round.sizes;
			if (round.changed == 0)
			{
				converged = true;
				break;
			}
		}

		//Scores: owned candidates in the target's cluster; if no node found a
		//positive same-cluster score for a target, every owned candidate.
		Map<String,Integer> targetClusters = targetAssignments(
				collectives, stepPrefix + "/targets", node, targets, assignments);
		Map<String,Map<String,Double>> canonicalTargets = targetVectorsFromOwners(
				collectives, stepPrefix + "/targetvectors", node, targets, owned);
		LinkedHashMap<String,Boolean> localPositive = new LinkedHashMap<String,Boolean>();
		for (Map.Entry<String,TreeMap<String,Double>> row : scores.entrySet())
		{
			String target = row.getKey();
			Integer cluster = targetClusters.get(target);
			Map<String,Double> targetVector = canonicalTargets.get(target);
			boolean positive = false;
			if (cluster != null && targetVector != null)
			{
				for (String candidate : sortedKeys(owned))
				{
					if (candidate.equals(target)
							|| assignments.get(candidate).intValue() != cluster.intValue())
					{
						continue;
					}
					double score = SparseRecommendationMath.euclideanSimilarityScore(
							targetVector, owned.get(candidate));
					row.getValue().put(candidate, Double.valueOf(score));
					if (score > 0.0)
					{
						positive = true;
					}
				}
			}
			localPositive.put(target, Boolean.valueOf(positive));
		}
		Map<String,Boolean> anyPositive = anyTrue(collectives, stepPrefix + "/positive", node,
				localPositive, targets);
		for (Map.Entry<String,TreeMap<String,Double>> row : scores.entrySet())
		{
			String target = row.getKey();
			Map<String,Double> targetVector = canonicalTargets.get(target);
			if (Boolean.TRUE.equals(anyPositive.get(target)) || targetVector == null)
			{
				continue;
			}
			for (String candidate : sortedKeys(owned))
			{
				if (!candidate.equals(target))
				{
					row.getValue().put(candidate, Double.valueOf(
							SparseRecommendationMath.euclideanSimilarityScore(
									targetVector, owned.get(candidate))));
				}
			}
		}
		return new Result(scores, k, iterations, converged, sizes, assignments);
	}

	private static int nearestByDistance(Map<String,Double> vector, List<Map<String,Double>> centroids)
	{
		int best = 0;
		double bestDistance = Double.POSITIVE_INFINITY;
		for (int cluster = 0; cluster < centroids.size(); cluster++)
		{
			double distance = SparseRecommendationMath.sparseEuclideanDistance(
					vector, centroids.get(cluster));
			if (distance < bestDistance)
			{
				bestDistance = distance;
				best = cluster;
			}
		}
		return best;
	}

	/**
	 * Each empty cluster, in index order, takes the user farthest from its own
	 * centroid among users whose cluster has more than one member, ties to the
	 * smaller name. Sizes are global and updated after every move.
	 */
	private static void repairEmptyClusters(
			RunCollectives collectives,
			String step,
			int node,
			Map<String,? extends Map<String,Double>> owned,
			Map<String,Integer> assignment,
			List<Map<String,Double>> centroids,
			int[] sizes)
	{
		for (int cluster = 0; cluster < sizes.length; cluster++)
		{
			if (sizes[cluster] > 0)
			{
				continue;
			}
			Proposal local = null;
			for (Map.Entry<String,? extends Map<String,Double>> entry : sortedEntries(owned))
			{
				int current = assignment.get(entry.getKey()).intValue();
				if (sizes[current] <= 1 || current >= centroids.size())
				{
					continue;
				}
				double distance = SparseRecommendationMath.sparseEuclideanDistance(
						entry.getValue(), centroids.get(current));
				local = better(local, new Proposal(entry.getKey(), distance, null));
			}
			Proposal chosen = globalBest(collectives, step + "/cluster" + cluster, node, local);
			if (chosen == null)
			{
				continue;
			}
			//Every node applies the same move to its copy of the sizes.
			Integer from = assignment.get(chosen.user);
			if (from != null)
			{
				assignment.put(chosen.user, Integer.valueOf(cluster));
			}
			int source = movedFrom(collectives, step + "/from" + cluster, node,
					from == null ? -1 : from.intValue());
			sizes[source]--;
			sizes[cluster]++;
		}
	}

	private static Round combineMeans(
			RunCollectives collectives,
			String step,
			int node,
			Map<String,? extends Map<String,Double>> owned,
			Map<String,Integer> assignment,
			final int k,
			int changed)
	{
		List<Map<String,ExactSum>> sums = new ArrayList<Map<String,ExactSum>>();
		int[] counts = new int[k];
		for (int cluster = 0; cluster < k; cluster++)
		{
			sums.add(new HashMap<String,ExactSum>());
		}
		for (Map.Entry<String,Integer> entry : assignment.entrySet())
		{
			int cluster = entry.getValue().intValue();
			counts[cluster]++;
			Map<String,Double> vector = owned.get(entry.getKey());
			if (vector == null)
			{
				continue;
			}
			Map<String,ExactSum> clusterSums = sums.get(cluster);
			for (Map.Entry<String,Double> term : vector.entrySet())
			{
				double value = safe(term.getValue());
				if (value == 0.0)
				{
					continue;
				}
				ExactSum sum = clusterSums.get(term.getKey());
				if (sum == null)
				{
					sum = new ExactSum();
					clusterSums.put(term.getKey(), sum);
				}
				sum.add(value);
			}
		}
		return collectives.allReduce(step, node, new Partial(sums, counts, changed),
				new RunCollectives.Reducer<Round>()
				{
					public Round reduce(List<Object> contributions)
					{
						Partial total = merge(contributions, k);
						List<Map<String,Double>> centroids = new ArrayList<Map<String,Double>>();
						for (int cluster = 0; cluster < k; cluster++)
						{
							LinkedHashMap<String,Double> mean = new LinkedHashMap<String,Double>();
							int count = total.counts[cluster];
							if (count > 0)
							{
								for (Map.Entry<String,ExactSum> entry : new TreeMap<String,ExactSum>(
										total.sums.get(cluster)).entrySet())
								{
									mean.put(entry.getKey(),
											Double.valueOf(entry.getValue().toDouble() / count));
								}
							}
							centroids.add(mean);
						}
						return new Round(centroids, total.counts, total.changed);
					}
				});
	}

	// ============================================================= shared steps

	private static List<String> globalUsers(
			RunCollectives collectives, String step, int node,
			Map<String,? extends Map<String,Double>> owned)
	{
		List<List<String>> byNode = collectives.allGather(step, node, sortedKeys(owned));
		ArrayList<String> all = new ArrayList<String>();
		for (List<String> users : byNode)
		{
			if (users != null)
			{
				all.addAll(users);
			}
		}
		Collections.sort(all);
		for (int index = 1; index < all.size(); index++)
		{
			if (all.get(index).equals(all.get(index - 1)))
			{
				throw new IllegalStateException(
						"User " + all.get(index) + " is owned by more than one node.");
			}
		}
		return all;
	}

	/** The owner of {@code user} contributes its vector; everyone receives it. */
	private static Map<String,Double> shareVector(
			RunCollectives collectives, String step, int node,
			Map<String,? extends Map<String,Double>> owned, String user)
	{
		Map<String,Double> mine = owned.containsKey(user)
				? Collections.<String,Double>unmodifiableMap(owned.get(user)) : null;
		List<Map<String,Double>> byNode = collectives.allGather(step, node, mine);
		for (Map<String,Double> vector : byNode)
		{
			if (vector != null)
			{
				return vector;
			}
		}
		return Collections.<String,Double>emptyMap();
	}

	private static Proposal globalBest(RunCollectives collectives, String step, int node, Proposal local)
	{
		List<Proposal> byNode = collectives.allGather(step, node, local);
		Proposal best = null;
		for (Proposal proposal : byNode)
		{
			best = better(best, proposal);
		}
		return best;
	}

	/** Larger distance wins; equal distances go to the smaller name. */
	private static Proposal better(Proposal current, Proposal candidate)
	{
		if (candidate == null)
		{
			return current;
		}
		if (current == null)
		{
			return candidate;
		}
		int byDistance = Double.compare(candidate.distance, current.distance);
		if (byDistance > 0)
		{
			return candidate;
		}
		if (byDistance == 0 && candidate.user.compareTo(current.user) < 0)
		{
			return candidate;
		}
		return current;
	}

	private static int movedFrom(RunCollectives collectives, String step, int node, int from)
	{
		List<Integer> byNode = collectives.allGather(step, node,
				from < 0 ? null : Integer.valueOf(from));
		for (Integer value : byNode)
		{
			if (value != null)
			{
				return value.intValue();
			}
		}
		throw new IllegalStateException("The repaired user has no owner in step " + step + ".");
	}

	private static int[] sumCounts(RunCollectives collectives, String step, int node, int[] local)
	{
		final int k = local.length;
		return collectives.allReduce(step, node, local.clone(), new RunCollectives.Reducer<int[]>()
		{
			public int[] reduce(List<Object> contributions)
			{
				int[] total = new int[k];
				for (Object contribution : contributions)
				{
					int[] counts = (int[])contribution;
					for (int index = 0; index < k; index++)
					{
						total[index] += counts[index];
					}
				}
				return total;
			}
		});
	}

	private static Map<String,Integer> targetAssignments(
			RunCollectives collectives, String step, int node,
			List<String> targets, Map<String,Integer> ownedAssignment)
	{
		LinkedHashMap<String,Integer> mine = new LinkedHashMap<String,Integer>();
		if (targets != null && ownedAssignment != null)
		{
			for (String target : targets)
			{
				Integer cluster = ownedAssignment.get(target);
				if (cluster != null)
				{
					mine.put(target, cluster);
				}
			}
		}
		List<Map<String,Integer>> byNode = collectives.allGather(step, node, mine);
		LinkedHashMap<String,Integer> all = new LinkedHashMap<String,Integer>();
		for (Map<String,Integer> map : byNode)
		{
			if (map != null)
			{
				all.putAll(map);
			}
		}
		return all;
	}

	/** The owner's vector of each target, so every node scores with one object. */
	private static Map<String,Map<String,Double>> targetVectorsFromOwners(
			RunCollectives collectives, String step, int node, List<String> targets,
			Map<String,? extends Map<String,Double>> owned)
	{
		LinkedHashMap<String,Map<String,Double>> mine = new LinkedHashMap<String,Map<String,Double>>();
		if (targets != null)
		{
			for (String target : targets)
			{
				Map<String,Double> vector = owned.get(target);
				if (vector != null)
				{
					mine.put(target, Collections.<String,Double>unmodifiableMap(vector));
				}
			}
		}
		List<Map<String,Map<String,Double>>> byNode = collectives.allGather(step, node, mine);
		LinkedHashMap<String,Map<String,Double>> all = new LinkedHashMap<String,Map<String,Double>>();
		for (Map<String,Map<String,Double>> map : byNode)
		{
			if (map != null)
			{
				all.putAll(map);
			}
		}
		return all;
	}

	private static Map<String,Boolean> anyTrue(
			RunCollectives collectives, String step, int node,
			Map<String,Boolean> local, List<String> targets)
	{
		List<Map<String,Boolean>> byNode = collectives.allGather(step, node, local);
		LinkedHashMap<String,Boolean> any = new LinkedHashMap<String,Boolean>();
		if (targets != null)
		{
			for (String target : targets)
			{
				any.put(target, Boolean.FALSE);
			}
		}
		for (Map<String,Boolean> map : byNode)
		{
			if (map == null)
			{
				continue;
			}
			for (Map.Entry<String,Boolean> entry : map.entrySet())
			{
				if (Boolean.TRUE.equals(entry.getValue()))
				{
					any.put(entry.getKey(), Boolean.TRUE);
				}
			}
		}
		return any;
	}

	private static Partial merge(List<Object> contributions, int k)
	{
		List<Map<String,ExactSum>> sums = new ArrayList<Map<String,ExactSum>>();
		int[] counts = new int[k];
		int changed = 0;
		for (int cluster = 0; cluster < k; cluster++)
		{
			sums.add(new HashMap<String,ExactSum>());
		}
		for (Object contribution : contributions)
		{
			Partial partial = (Partial)contribution;
			changed += partial.changed;
			for (int cluster = 0; cluster < k; cluster++)
			{
				counts[cluster] += partial.counts[cluster];
				Map<String,ExactSum> target = sums.get(cluster);
				for (Map.Entry<String,ExactSum> entry : partial.sums.get(cluster).entrySet())
				{
					ExactSum sum = target.get(entry.getKey());
					if (sum == null)
					{
						target.put(entry.getKey(), entry.getValue().copy());
					}
					else
					{
						sum.add(entry.getValue());
					}
				}
			}
		}
		return new Partial(sums, counts, changed);
	}

	private static void accumulate(Map<String,ExactSum> sums, Map<String,Double> vector)
	{
		if (vector == null)
		{
			return;
		}
		for (Map.Entry<String,Double> entry : vector.entrySet())
		{
			ExactSum sum = sums.get(entry.getKey());
			if (sum == null)
			{
				sum = new ExactSum();
				sums.put(entry.getKey(), sum);
			}
			sum.add(entry.getValue().doubleValue());
		}
	}

	/** {@link RecommenderAgent}'s unit-vector cosine, target first. */
	static double unitVectorCosine(Map<String,Double> target, Map<String,Double> candidate)
	{
		if (target == null || candidate == null || target.isEmpty() || candidate.isEmpty())
		{
			return 0.0;
		}
		Map<String,Double> smaller = target;
		Map<String,Double> larger = candidate;
		if (candidate.size() < target.size())
		{
			smaller = candidate;
			larger = target;
		}
		double dot = 0.0;
		for (Map.Entry<String,Double> entry : smaller.entrySet())
		{
			Double other = larger.get(entry.getKey());
			if (other != null)
			{
				dot += entry.getValue().doubleValue() * other.doubleValue();
			}
		}
		return dot;
	}

	private static TreeMap<String,TreeMap<String,Double>> zeroRows(
			List<String> targets, List<String> localUsers)
	{
		TreeMap<String,TreeMap<String,Double>> rows = new TreeMap<String,TreeMap<String,Double>>();
		if (targets == null || localUsers == null)
		{
			return rows;
		}
		java.util.LinkedHashSet<String> available = new java.util.LinkedHashSet<String>(localUsers);
		for (String target : targets)
		{
			if (target == null || !available.contains(target) || rows.containsKey(target))
			{
				continue;
			}
			TreeMap<String,Double> row = new TreeMap<String,Double>();
			for (String candidate : localUsers)
			{
				if (candidate != null && !candidate.equals(target))
				{
					row.put(candidate, Double.valueOf(0.0));
				}
			}
			rows.put(target, row);
		}
		return rows;
	}

	private static List<String> sortedKeys(Map<String,?> map)
	{
		ArrayList<String> keys = new ArrayList<String>(map.keySet());
		Collections.sort(keys);
		return keys;
	}

	@SuppressWarnings("unchecked")
	private static List<Map.Entry<String,? extends Map<String,Double>>> sortedEntries(
			Map<String,? extends Map<String,Double>> map)
	{
		ArrayList<Map.Entry<String,? extends Map<String,Double>>> entries =
				new ArrayList<Map.Entry<String,? extends Map<String,Double>>>();
		for (Map.Entry<String,? extends Map<String,Double>> entry : map.entrySet())
		{
			entries.add(entry);
		}
		Collections.sort(entries, new java.util.Comparator<Map.Entry<String,? extends Map<String,Double>>>()
		{
			public int compare(Map.Entry<String,? extends Map<String,Double>> first,
					Map.Entry<String,? extends Map<String,Double>> second)
			{
				return first.getKey().compareTo(second.getKey());
			}
		});
		return entries;
	}

	/**
	 * The Euclidean recommender's centroid layout: terms in sorted order, zero
	 * weights dropped. The layout fixes the summation order of every distance,
	 * so it is part of the algorithm's definition.
	 */
	private static Map<String,Double> sortedNonZeroCopy(Map<String,Double> vector)
	{
		LinkedHashMap<String,Double> copy = new LinkedHashMap<String,Double>();
		if (vector == null)
		{
			return copy;
		}
		for (Map.Entry<String,Double> entry : new TreeMap<String,Double>(vector).entrySet())
		{
			double value = safe(entry.getValue());
			if (value != 0.0)
			{
				copy.put(entry.getKey(), Double.valueOf(value));
			}
		}
		return copy;
	}

	private static double safe(Double value)
	{
		return value == null || !Double.isFinite(value.doubleValue()) ? 0.0 : value.doubleValue();
	}
}
