package TwitterGatherDataFollowers.userRyersonU;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Exactness of the distributed K-Means: for every node count and every random
 * partition of users onto nodes, the scores merged the way the organizer merges
 * them (sum per target and candidate) are bitwise identical to one node's.
 */
public final class DistributedKMeansSelfTest
{
	private DistributedKMeansSelfTest()
	{
	}

	public static void main(String[] args) throws Exception
	{
		Random random = new Random(7L);
		int corpora = 0;
		for (int trial = 0; trial < 14; trial++)
		{
			Map<String,LinkedHashMap<String,Double>> corpus = randomCorpus(random, 30 + random.nextInt(60));
			List<String> users = new ArrayList<String>(corpus.keySet());
			Collections.sort(users);
			List<String> targets = new ArrayList<String>();
			for (int index = 0; index < 4; index++)
			{
				targets.add(users.get(random.nextInt(users.size())));
			}
			targets = new ArrayList<String>(new java.util.LinkedHashSet<String>(targets));
			int clusters = 2 + random.nextInt(6);
			for (boolean euclidean : new boolean[] {false, true})
			{
				TreeMap<String,TreeMap<String,Double>> reference =
						run(corpus, targets, 1, random, clusters, euclidean, trial);
				for (int nodes : new int[] {2, 3, 5})
				{
					for (int repeat = 0; repeat < 2; repeat++)
					{
						TreeMap<String,TreeMap<String,Double>> distributed =
								run(corpus, targets, nodes, random, clusters, euclidean, trial);
						requireIdentical(reference, distributed,
								(euclidean ? "Euclidean" : "cosine") + " K-Means, " + nodes
								+ " nodes, trial " + trial);
					}
				}
			}
			checkEuclideanMatchesOriginal(overlappingCorpus(random, 30 + random.nextInt(40)),
					clusters);
			corpora++;
		}
		System.out.println("DistributedKMeansSelfTest passed (" + corpora + " corpora)");
	}

	/** Runs the algorithm on {@code nodes} threads and merges like the organizer. */
	private static TreeMap<String,TreeMap<String,Double>> run(
			final Map<String,LinkedHashMap<String,Double>> corpus,
			final List<String> targets,
			final int nodes,
			Random random,
			final int clusters,
			final boolean euclidean,
			final int trial) throws Exception
	{
		//Random exclusive ownership; targets are replicated to every node.
		final List<Map<String,LinkedHashMap<String,Double>>> owned =
				new ArrayList<Map<String,LinkedHashMap<String,Double>>>();
		final List<List<String>> local = new ArrayList<List<String>>();
		for (int node = 0; node < nodes; node++)
		{
			owned.add(new LinkedHashMap<String,LinkedHashMap<String,Double>>());
			local.add(new ArrayList<String>());
		}
		for (Map.Entry<String,LinkedHashMap<String,Double>> entry : corpus.entrySet())
		{
			int node = random.nextInt(nodes);
			owned.get(node).put(entry.getKey(), entry.getValue());
			local.get(node).add(entry.getKey());
		}
		for (int node = 0; node < nodes; node++)
		{
			for (String target : targets)
			{
				if (!local.get(node).contains(target))
				{
					local.get(node).add(target);
				}
			}
		}
		final RunCollectives collectives = new RunCollectives(null, nodes);
		final List<TreeMap<String,TreeMap<String,Double>>> results =
				Collections.synchronizedList(new ArrayList<TreeMap<String,TreeMap<String,Double>>>());
		final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
		ArrayList<Thread> threads = new ArrayList<Thread>();
		for (int node = 1; node <= nodes; node++)
		{
			final int index = node;
			Thread thread = new Thread(new Runnable()
			{
				public void run()
				{
					try
					{
						DistributedKMeans.Result result = euclidean
								? DistributedKMeans.euclidean(collectives, "km" + trial, index,
										owned.get(index - 1), local.get(index - 1), targets,
										clusters, 25)
								: DistributedKMeans.cosine(collectives, "km" + trial, index,
										owned.get(index - 1), local.get(index - 1), targets,
										owned.get(index - 1), clusters, 25, 11L);
						results.add(result.getScores());
					}
					catch (Throwable error)
					{
						failure.compareAndSet(null, error);
					}
				}
			});
			threads.add(thread);
			thread.start();
		}
		for (Thread thread : threads)
		{
			thread.join(60000L);
			if (thread.isAlive())
			{
				throw new AssertionError("distributed K-Means deadlocked with " + nodes + " nodes");
			}
		}
		if (failure.get() != null)
		{
			throw new AssertionError("distributed K-Means failed", failure.get());
		}
		TreeMap<String,TreeMap<String,Double>> merged = new TreeMap<String,TreeMap<String,Double>>();
		for (TreeMap<String,TreeMap<String,Double>> result : results)
		{
			for (Map.Entry<String,TreeMap<String,Double>> row : result.entrySet())
			{
				TreeMap<String,Double> mergedRow = merged.get(row.getKey());
				if (mergedRow == null)
				{
					mergedRow = new TreeMap<String,Double>();
					merged.put(row.getKey(), mergedRow);
				}
				for (Map.Entry<String,Double> score : row.getValue().entrySet())
				{
					Double old = mergedRow.get(score.getKey());
					mergedRow.put(score.getKey(), old == null
							? score.getValue() : Double.valueOf(old + score.getValue()));
				}
			}
		}
		return merged;
	}

	/**
	 * One-node distributed Euclidean clustering equals the original recommender's
	 * on data without rounding-level ties (the two differ only in how centroid
	 * sums are rounded, which can decide exact ties differently).
	 */
	private static void checkEuclideanMatchesOriginal(
			Map<String,LinkedHashMap<String,Double>> corpus, int clusters)
			throws Exception
	{
		List<String> targets = new ArrayList<String>(corpus.keySet()).subList(0, 3);
		EuclideanKMeansRecommender.Result original = EuclideanKMeansRecommender.score(
				corpus, targets, clusters, 25);
		Map<String,Integer> originalAssignment = new LinkedHashMap<String,Integer>();
		for (Cluster cluster : original.clusters)
		{
			for (Point point : cluster.getPoints())
			{
				originalAssignment.put(point.getUserName(), Integer.valueOf(cluster.getId()));
			}
		}
		final RunCollectives collectives = new RunCollectives(null, 1);
		DistributedKMeans.Result distributed = DistributedKMeans.euclidean(collectives, "orig", 1,
				corpus, new ArrayList<String>(corpus.keySet()), targets, clusters, 25);
		int agree = 0;
		for (Map.Entry<String,Integer> entry : distributed.getOwnedAssignments().entrySet())
		{
			if (entry.getValue().equals(originalAssignment.get(entry.getKey())))
			{
				agree++;
			}
		}
		if (agree != corpus.size())
		{
			throw new AssertionError("one-node distributed Euclidean clustering disagrees with the "
					+ "original on " + (corpus.size() - agree) + " of " + corpus.size() + " users");
		}
	}

	/** Every user shares most of a small vocabulary, so distances rarely tie. */
	private static Map<String,LinkedHashMap<String,Double>> overlappingCorpus(Random random, int users)
	{
		LinkedHashMap<String,LinkedHashMap<String,Double>> corpus =
				new LinkedHashMap<String,LinkedHashMap<String,Double>>();
		for (int user = 0; user < users; user++)
		{
			LinkedHashMap<String,Double> vector = new LinkedHashMap<String,Double>();
			int center = random.nextInt(3);
			for (int term = 0; term < 12; term++)
			{
				double value = random.nextDouble() + (term % 3 == center ? 2.0 : 0.0);
				vector.put("w" + term, Double.valueOf(value));
			}
			corpus.put(String.format("user%03d", user), vector);
		}
		return corpus;
	}

	private static Map<String,LinkedHashMap<String,Double>> randomCorpus(Random random, int users)
	{
		LinkedHashMap<String,LinkedHashMap<String,Double>> corpus =
				new LinkedHashMap<String,LinkedHashMap<String,Double>>();
		String[] vocabulary = new String[60];
		for (int index = 0; index < vocabulary.length; index++)
		{
			vocabulary[index] = "t" + index;
		}
		for (int user = 0; user < users; user++)
		{
			LinkedHashMap<String,Double> vector = new LinkedHashMap<String,Double>();
			int terms = 2 + random.nextInt(12);
			int topic = random.nextInt(4) * 15;
			for (int term = 0; term < terms; term++)
			{
				String word = vocabulary[(topic + random.nextInt(20)) % vocabulary.length];
				vector.put(word, Double.valueOf(0.05 + random.nextDouble()));
			}
			double norm = 0.0;
			for (Double value : vector.values())
			{
				norm += value * value;
			}
			norm = Math.sqrt(norm);
			for (Map.Entry<String,Double> entry : vector.entrySet())
			{
				entry.setValue(Double.valueOf(entry.getValue() / norm));
			}
			corpus.put(String.format("user%03d", user), vector);
		}
		return corpus;
	}

	private static void requireIdentical(
			TreeMap<String,TreeMap<String,Double>> expected,
			TreeMap<String,TreeMap<String,Double>> actual,
			String label)
	{
		if (!expected.keySet().equals(actual.keySet()))
		{
			throw new AssertionError(label + ": target rows differ " + expected.keySet()
					+ " vs " + actual.keySet());
		}
		for (String target : expected.keySet())
		{
			TreeMap<String,Double> a = expected.get(target);
			TreeMap<String,Double> b = actual.get(target);
			if (!a.keySet().equals(b.keySet()))
			{
				throw new AssertionError(label + ": candidates differ for " + target);
			}
			for (String candidate : a.keySet())
			{
				if (Double.doubleToLongBits(a.get(candidate))
						!= Double.doubleToLongBits(b.get(candidate)))
				{
					throw new AssertionError(label + ": score " + target + "->" + candidate
							+ " differs: " + a.get(candidate) + " vs " + b.get(candidate));
				}
			}
		}
	}
}
