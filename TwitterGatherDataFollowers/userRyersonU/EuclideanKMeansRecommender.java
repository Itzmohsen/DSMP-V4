package TwitterGatherDataFollowers.userRyersonU;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

final class EuclideanKMeansRecommender
{
	static final class Result
	{
		final TreeMap<String,TreeMap<String,Double>> scores;
		final List<Cluster> clusters;
		final int requestedClusters;
		final int effectiveClusters;
		final int iterations;
		final boolean converged;

		Result(TreeMap<String,TreeMap<String,Double>> scores,
				List<Cluster> clusters,
				int requestedClusters,
				int effectiveClusters,
				int iterations,
				boolean converged)
		{
			this.scores = scores;
			this.clusters = clusters;
			this.requestedClusters = requestedClusters;
			this.effectiveClusters = effectiveClusters;
			this.iterations = iterations;
			this.converged = converged;
		}
	}

	private EuclideanKMeansRecommender()
	{
	}

	static Result score(
			Map<String,? extends Map<String,Double>> documents,
			Collection<String> targetUsers,
			int requestedClusters,
			int requestedMaxIterations)
	{
		LinkedHashMap<String,Map<String,Double>> preparedDocuments =
				prepareDocuments(documents);
		TreeMap<String,TreeMap<String,Double>> scores =
				initializeTargetRows(targetUsers, preparedDocuments.keySet());
		if (preparedDocuments.isEmpty() || scores.isEmpty())
		{
			return new Result(scores, Collections.<Cluster>emptyList(),
					requestedClusters, 0, 0, true);
		}

		int effectiveClusters = clampClusterCount(
				requestedClusters, preparedDocuments.size());
		int maxIterations = Math.max(1, requestedMaxIterations);

		List<String> userNames =
				new ArrayList<String>(preparedDocuments.keySet());
		List<Map<String,Double>> centroids =
				initializeCentroids(preparedDocuments, effectiveClusters);

		LinkedHashMap<String,Integer> assignments = null;
		boolean converged = false;
		int iterations = 0;
		for (int iteration = 0; iteration < maxIterations; iteration++)
		{
			LinkedHashMap<String,Integer> newAssignments =
					assignUsersToCentroids(userNames, preparedDocuments, centroids);
			repairEmptyClusters(newAssignments, userNames,
					preparedDocuments, centroids, effectiveClusters);

			if (newAssignments.equals(assignments))
			{
				converged = true;
				assignments = newAssignments;
				break;
			}

			assignments = newAssignments;
			centroids = recomputeCentroids(
					assignments, preparedDocuments, effectiveClusters);
			iterations++;
		}
		if (assignments == null)
		{
			assignments = assignUsersToCentroids(
					userNames, preparedDocuments, centroids);
			repairEmptyClusters(assignments, userNames,
					preparedDocuments, centroids, effectiveClusters);
		}

		List<Cluster> clusters = buildClusters(
				assignments, preparedDocuments, centroids, effectiveClusters);
		fillScores(scores, preparedDocuments, assignments);

		return new Result(scores, clusters, requestedClusters,
				effectiveClusters, iterations, converged);
	}

	private static LinkedHashMap<String,Map<String,Double>> prepareDocuments(
			Map<String,? extends Map<String,Double>> documents)
	{
		LinkedHashMap<String,Map<String,Double>> prepared =
				new LinkedHashMap<String,Map<String,Double>>();
		if (documents == null)
		{
			return prepared;
		}

		ArrayList<String> users = new ArrayList<String>(documents.keySet());
		Collections.sort(users);
		for (String user : users)
		{
			if (user == null)
			{
				continue;
			}
			Map<String,Double> vector = documents.get(user);
			if (vector == null)
			{
				vector = Collections.emptyMap();
			}
			prepared.put(user, vector);
		}
		return prepared;
	}

	private static TreeMap<String,TreeMap<String,Double>> initializeTargetRows(
			Collection<String> targetUsers,
			Collection<String> availableUsers)
	{
		TreeMap<String,TreeMap<String,Double>> rows =
				new TreeMap<String,TreeMap<String,Double>>();
		if (targetUsers == null || availableUsers == null)
		{
			return rows;
		}

		LinkedHashSet<String> available =
				new LinkedHashSet<String>(availableUsers);
		for (String target : targetUsers)
		{
			if (target == null || !available.contains(target)
					|| rows.containsKey(target))
			{
				continue;
			}
			TreeMap<String,Double> candidateScores =
					new TreeMap<String,Double>();
			for (String candidate : available)
			{
				if (!target.equals(candidate))
				{
					candidateScores.put(candidate, 0.0);
				}
			}
			rows.put(target, candidateScores);
		}
		return rows;
	}

	static int clampClusterCount(int requestedClusters, int documentCount)
	{
		if (documentCount <= 0)
		{
			return 0;
		}
		int clusters = Math.max(1, requestedClusters);
		return Math.min(clusters, documentCount);
	}

	static List<Map<String,Double>> initializeCentroids(
			LinkedHashMap<String,Map<String,Double>> documents,
			int clusterCount)
	{
		ArrayList<Map<String,Double>> centroids =
				new ArrayList<Map<String,Double>>();
		if (documents == null || documents.isEmpty() || clusterCount <= 0)
		{
			return centroids;
		}

		ArrayList<String> users = new ArrayList<String>(documents.keySet());
		Collections.sort(users);
		String firstSeed = users.get(0);
		centroids.add(copyVector(documents.get(firstSeed)));

		while (centroids.size() < clusterCount)
		{
			String farthestUser = null;
			double farthestDistance = -1.0;
			for (String user : users)
			{
				if (isAlreadySeed(documents.get(user), centroids))
				{
					continue;
				}
				double distance = distanceToNearestCentroid(
						documents.get(user), centroids);
				if (distance > farthestDistance
						|| (Double.compare(distance, farthestDistance) == 0
								&& (farthestUser == null
										|| user.compareTo(farthestUser) < 0)))
				{
					farthestUser = user;
					farthestDistance = distance;
				}
			}
			if (farthestUser == null)
			{
				break;
			}
			centroids.add(copyVector(documents.get(farthestUser)));
		}

		return centroids;
	}

	private static boolean isAlreadySeed(
			Map<String,Double> vector,
			List<Map<String,Double>> centroids)
	{
		for (Map<String,Double> centroid : centroids)
		{
			if (SparseRecommendationMath.sparseEuclideanDistance(
					vector, centroid) == 0.0)
			{
				return true;
			}
		}
		return false;
	}

	private static double distanceToNearestCentroid(
			Map<String,Double> vector,
			List<Map<String,Double>> centroids)
	{
		double nearest = Double.POSITIVE_INFINITY;
		for (Map<String,Double> centroid : centroids)
		{
			double distance = SparseRecommendationMath.sparseEuclideanDistance(
					vector, centroid);
			if (distance < nearest)
			{
				nearest = distance;
			}
		}
		return nearest;
	}

	private static LinkedHashMap<String,Integer> assignUsersToCentroids(
			List<String> users,
			Map<String,Map<String,Double>> documents,
			List<Map<String,Double>> centroids)
	{
		LinkedHashMap<String,Integer> assignments =
				new LinkedHashMap<String,Integer>();
		for (String user : users)
		{
			int cluster = nearestCentroid(documents.get(user), centroids);
			assignments.put(user, cluster);
		}
		return assignments;
	}

	private static int nearestCentroid(
			Map<String,Double> vector,
			List<Map<String,Double>> centroids)
	{
		int bestCluster = 0;
		double bestDistance = Double.POSITIVE_INFINITY;
		for (int i = 0; i < centroids.size(); i++)
		{
			double distance = SparseRecommendationMath.sparseEuclideanDistance(
					vector, centroids.get(i));
			if (distance < bestDistance)
			{
				bestDistance = distance;
				bestCluster = i;
			}
		}
		return bestCluster;
	}

	private static void repairEmptyClusters(
			LinkedHashMap<String,Integer> assignments,
			List<String> users,
			Map<String,Map<String,Double>> documents,
			List<Map<String,Double>> centroids,
			int clusterCount)
	{
		for (int cluster = 0; cluster < clusterCount; cluster++)
		{
			if (assignments.containsValue(cluster))
			{
				continue;
			}
			String replacementUser = findFarthestMovableUser(
					assignments, users, documents, centroids);
			if (replacementUser == null)
			{
				continue;
			}
			assignments.put(replacementUser, cluster);
		}
	}

	private static String findFarthestMovableUser(
			LinkedHashMap<String,Integer> assignments,
			List<String> users,
			Map<String,Map<String,Double>> documents,
			List<Map<String,Double>> centroids)
	{
		String farthestUser = null;
		double farthestDistance = -1.0;
		for (String user : users)
		{
			Integer cluster = assignments.get(user);
			if (cluster == null || clusterSize(assignments, cluster) <= 1)
			{
				continue;
			}
			double distance = SparseRecommendationMath.sparseEuclideanDistance(
					documents.get(user), centroids.get(cluster));
			if (distance > farthestDistance
					|| (Double.compare(distance, farthestDistance) == 0
							&& (farthestUser == null
									|| user.compareTo(farthestUser) < 0)))
			{
				farthestUser = user;
				farthestDistance = distance;
			}
		}
		return farthestUser;
	}

	private static int clusterSize(
			Map<String,Integer> assignments,
			int cluster)
	{
		int size = 0;
		for (Integer assignedCluster : assignments.values())
		{
			if (assignedCluster != null && assignedCluster.intValue() == cluster)
			{
				size++;
			}
		}
		return size;
	}

	static List<Map<String,Double>> recomputeCentroids(
			Map<String,Integer> assignments,
			Map<String,Map<String,Double>> documents,
			int clusterCount)
	{
		ArrayList<Map<String,Double>> centroids =
				new ArrayList<Map<String,Double>>();
		for (int cluster = 0; cluster < clusterCount; cluster++)
		{
			centroids.add(meanVectorForCluster(assignments, documents, cluster));
		}
		return centroids;
	}

	static Map<String,Double> meanVectorForCluster(
			Map<String,Integer> assignments,
			Map<String,Map<String,Double>> documents,
			int cluster)
	{
		LinkedHashMap<String,Double> sums = new LinkedHashMap<String,Double>();
		int count = 0;
		for (Map.Entry<String,Integer> assignment : assignments.entrySet())
		{
			if (assignment.getValue() == null
					|| assignment.getValue().intValue() != cluster)
			{
				continue;
			}
			count++;
			Map<String,Double> vector = documents.get(assignment.getKey());
			if (vector == null)
			{
				continue;
			}
			for (Map.Entry<String,Double> entry : vector.entrySet())
			{
				double value = safeValue(entry.getValue());
				if (value == 0.0)
				{
					continue;
				}
				Double current = sums.get(entry.getKey());
				if (current == null)
				{
					current = 0.0;
				}
				sums.put(entry.getKey(), current.doubleValue() + value);
			}
		}

		if (count <= 0)
		{
			return sums;
		}

		LinkedHashMap<String,Double> mean = new LinkedHashMap<String,Double>();
		ArrayList<String> terms = new ArrayList<String>(sums.keySet());
		Collections.sort(terms);
		for (String term : terms)
		{
			mean.put(term, sums.get(term) / count);
		}
		return mean;
	}

	private static void fillScores(
			TreeMap<String,TreeMap<String,Double>> scores,
			Map<String,Map<String,Double>> documents,
			Map<String,Integer> assignments)
	{
		for (Map.Entry<String,TreeMap<String,Double>> targetRow :
				scores.entrySet())
		{
			String target = targetRow.getKey();
			Map<String,Double> targetVector = documents.get(target);
			Integer targetCluster = assignments.get(target);
			TreeMap<String,Double> candidateScores = targetRow.getValue();
			boolean hasPositiveSameClusterScore = false;

			for (String candidate : candidateScores.keySet())
			{
				Integer candidateCluster = assignments.get(candidate);
				if (targetCluster != null
						&& targetCluster.equals(candidateCluster))
				{
					double score = SparseRecommendationMath.euclideanSimilarityScore(
							targetVector, documents.get(candidate));
					candidateScores.put(candidate, score);
					if (score > 0.0)
					{
						hasPositiveSameClusterScore = true;
					}
				}
			}

			if (!hasPositiveSameClusterScore)
			{
				for (String candidate : candidateScores.keySet())
				{
					double score = SparseRecommendationMath.euclideanSimilarityScore(
							targetVector, documents.get(candidate));
					candidateScores.put(candidate, score);
				}
			}
		}
	}

	private static List<Cluster> buildClusters(
			Map<String,Integer> assignments,
			Map<String,Map<String,Double>> documents,
			List<Map<String,Double>> centroids,
			int clusterCount)
	{
		ArrayList<Cluster> clusters = new ArrayList<Cluster>();
		for (int i = 0; i < clusterCount; i++)
		{
			Cluster cluster = new Cluster(i);
			cluster.setCentroid(new Point(-1, copyVector(centroids.get(i))));
			clusters.add(cluster);
		}

		ArrayList<String> users = new ArrayList<String>(assignments.keySet());
		Collections.sort(users);
		for (String user : users)
		{
			Integer clusterId = assignments.get(user);
			if (clusterId == null || clusterId < 0
					|| clusterId >= clusters.size())
			{
				continue;
			}
			Point point = new Point(user, documents.get(user));
			point.setCluster(clusterId);
			clusters.get(clusterId).addPoint(point);
		}
		return clusters;
	}

	private static Map<String,Double> copyVector(Map<String,Double> vector)
	{
		LinkedHashMap<String,Double> copy = new LinkedHashMap<String,Double>();
		if (vector == null)
		{
			return copy;
		}
		ArrayList<String> terms = new ArrayList<String>(vector.keySet());
		Collections.sort(terms);
		for (String term : terms)
		{
			double value = safeValue(vector.get(term));
			if (value != 0.0)
			{
				copy.put(term, value);
			}
		}
		return copy;
	}

	private static double safeValue(Double value)
	{
		if (value == null || !Double.isFinite(value.doubleValue()))
		{
			return 0.0;
		}
		return value.doubleValue();
	}
}
