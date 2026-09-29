package TwitterGatherDataFollowers.userRyersonU;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Pure helpers for the cosine K-Means path in {@link RecommenderAgent}.
 *
 * Both functions used to live inside the agent's behaviour class, where they
 * could not be tested and where the convergence snapshot was easy to get wrong.
 * They depend on nothing but their arguments.
 */
final class KMeansSupport
{
	private KMeansSupport()
	{
	}

	/**
	 * Detached snapshot of user -> cluster id across all clusters.
	 *
	 * Convergence must be decided on the assignment, not on the cluster point
	 * lists. {@link Cluster#getPoints()} returns the live list that the next
	 * iteration clears and refills, so a snapshot of those lists compares a list
	 * with itself and always looks converged; and {@link Point} declares no
	 * {@code equals}, so element containment is identity-based and would never
	 * match the freshly allocated points of the next iteration either.
	 */
	static LinkedHashMap<String,Integer> snapshotClusterAssignments(
			List<Cluster> clusters)
	{
		LinkedHashMap<String,Integer> assignments =
				new LinkedHashMap<String,Integer>();
		if (clusters == null)
		{
			return assignments;
		}
		for (Cluster cluster : clusters)
		{
			List<Point> points = cluster.getPoints();
			if (points == null)
			{
				continue;
			}
			for (Point point : points)
			{
				if (point != null && point.getUserName() != null)
				{
					assignments.put(
							point.getUserName(),
							Integer.valueOf(cluster.getId()));
				}
			}
		}
		return assignments;
	}

	/**
	 * Deterministic farthest-first (max-min) seed selection.
	 *
	 * The first seed is chosen by {@code clusterSeed}, so different seeds give
	 * different but reproducible initialisations. Every later seed is the user
	 * farthest from the seeds chosen so far, with ties broken by user name. The
	 * result depends only on the documents, the cluster count and the seed, and
	 * never on map iteration order.
	 *
	 * This replaces an unseeded shuffle that was then overridden by a list of
	 * hardcoded account names. Those names were the followee class labels, so on
	 * the bundled Twitter dataset the unsupervised algorithm was being started at
	 * the supervised class centres; on every other dataset the unseeded shuffle
	 * made runs unreproducible.
	 *
	 * @return exactly {@code clusterCount} seeds whenever at least one candidate
	 *         exists, so cluster construction cannot fail on a short list
	 */
	static List<String> selectSeedUsers(
			Collection<String> candidateUsers,
			Map<String,? extends Map<String,Double>> documents,
			int clusterCount,
			long clusterSeed)
	{
		//Hash-based de-duplication. A List.contains scan here is quadratic in the
		//user count, which on a corpus of tens of thousands of users makes seed
		//selection look like a hang.
		ArrayList<String> ordered = new ArrayList<String>();
		HashSet<String> seen = new HashSet<String>();
		if (candidateUsers != null)
		{
			for (String user : candidateUsers)
			{
				if (user != null && seen.add(user))
				{
					ordered.add(user);
				}
			}
		}
		Collections.sort(ordered);

		ArrayList<String> seeds = new ArrayList<String>();
		HashSet<String> seedLookup = new HashSet<String>();
		if (ordered.isEmpty() || clusterCount <= 0)
		{
			return seeds;
		}

		int firstIndex = (int)Math.floorMod(clusterSeed, (long)ordered.size());
		seeds.add(ordered.get(firstIndex));
		seedLookup.add(ordered.get(firstIndex));

		while (seeds.size() < clusterCount)
		{
			String farthestUser = null;
			double farthestDistance = -1.0;
			for (String user : ordered)
			{
				if (seedLookup.contains(user))
				{
					continue;
				}
				double nearest = Double.POSITIVE_INFINITY;
				for (String seed : seeds)
				{
					double distance =
							SparseRecommendationMath.sparseEuclideanDistance(
									documents == null ? null : documents.get(user),
									documents == null ? null : documents.get(seed));
					if (distance < nearest)
					{
						nearest = distance;
					}
				}
				if (nearest > farthestDistance
						|| (Double.compare(nearest, farthestDistance) == 0
								&& (farthestUser == null
										|| user.compareTo(farthestUser) < 0)))
				{
					farthestUser = user;
					farthestDistance = nearest;
				}
			}
			if (farthestUser == null)
			{
				break;
			}
			seeds.add(farthestUser);
			seedLookup.add(farthestUser);
		}

		//Degenerate corpora (fewer distinct users than clusters) still need one
		//seed per cluster; reuse name-sorted users rather than failing the run.
		int fallbackIndex = 0;
		while (seeds.size() < clusterCount && fallbackIndex < ordered.size())
		{
			String user = ordered.get(fallbackIndex);
			fallbackIndex++;
			if (seedLookup.add(user))
			{
				seeds.add(user);
			}
		}
		while (seeds.size() < clusterCount)
		{
			seeds.add(ordered.get(seeds.size() % ordered.size()));
		}
		return seeds;
	}

	/** Writes a symmetric pair score into both target rows. */
	static void putScoreForTrackedTargets(
			Map<String,TreeMap<String,Double>> scoreRows,
			String firstUser,
			String secondUser,
			double score)
	{
		putScoreForTrackedTargets(
				scoreRows, firstUser, secondUser, score, true, true);
	}

	/**
	 * Writes a symmetric pair score into the target rows that want it.
	 *
	 * @param ownsFirstUser  write (secondUser -> firstUser): firstUser is the
	 *                       candidate in that direction
	 * @param ownsSecondUser write (firstUser -> secondUser): secondUser is the
	 *                       candidate in that direction
	 */
	static void putScoreForTrackedTargets(
			Map<String,TreeMap<String,Double>> scoreRows,
			String firstUser,
			String secondUser,
			double score,
			boolean ownsFirstUser,
			boolean ownsSecondUser)
	{
		if (scoreRows == null)
		{
			return;
		}
		if (ownsSecondUser)
		{
			TreeMap<String,Double> firstRow = scoreRows.get(firstUser);
			if (firstRow != null)
			{
				firstRow.put(secondUser, score);
			}
		}
		if (ownsFirstUser)
		{
			TreeMap<String,Double> secondRow = scoreRows.get(secondUser);
			if (secondRow != null)
			{
				secondRow.put(firstUser, score);
			}
		}
	}
}
