package TwitterGatherDataFollowers.userRyersonU;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Measures the active Point cosine/Euclidean pairwise scoring primitives with
 * one and eight independent user shards and requires identical results.
 */
public final class UniversalHeavyParallelCoreBenchmark
{
	private UniversalHeavyParallelCoreBenchmark()
	{
	}

	public static void main(String[] args) throws Exception
	{
		File file = args.length > 0
				? new File(args[0])
				: new File(
						"Dataset/Benchmarks/"
						+ "Universal-Heavy-All-Working-Algorithms-76k.txt");
		List<Point> points = load(file);
		long oneStart = System.nanoTime();
		TreeMap<String,String> one = score(points, 1);
		long oneMs = elapsedMs(oneStart);
		long eightStart = System.nanoTime();
		TreeMap<String,String> eight = score(points, 8);
		long eightMs = elapsedMs(eightStart);
		require(one.equals(eight),
				"one-node and eight-node pairwise results differ");
		require(one.size() == points.size(),
				"pairwise benchmark did not score every user");
		System.out.println(
				"UniversalHeavyParallelCoreBenchmark passed: oneNode="
				+ oneMs + " ms, eightNodes=" + eightMs + " ms, speedup="
				+ String.format("%.2fx", oneMs / (double)Math.max(1L, eightMs))
				+ ", identicalUsers=" + one.size() + ".");
	}

	private static List<Point> load(File file) throws Exception
	{
		TreeMap<String,LinkedHashMap<String,Double>> documents =
				new TreeMap<String,LinkedHashMap<String,Double>>();
		long lineNumber = 0L;
		try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(
						new FileInputStream(file),
						StandardCharsets.UTF_8),
				512 * 1024))
		{
			String line;
			while ((line = reader.readLine()) != null)
			{
				DatasetRow row = DatasetRow.parse(line, ++lineNumber);
				LinkedHashMap<String,Double> document =
						documents.get(row.userName);
				if (document == null)
				{
					document = new LinkedHashMap<String,Double>();
					documents.put(row.userName, document);
				}
				for (String token : row.text.split(" "))
				{
					Double old = document.get(token);
					document.put(token, old == null ? 1.0 : old + 1.0);
				}
			}
		}
		List<Point> points = new ArrayList<Point>();
		for (Map.Entry<String,LinkedHashMap<String,Double>> entry
				: documents.entrySet())
		{
			points.add(new Point(entry.getKey(), entry.getValue()));
		}
		require(points.size() == 480, "unexpected point count");
		return points;
	}

	private static TreeMap<String,String> score(
			final List<Point> points,
			int nodes) throws Exception
	{
		ExecutorService executor = Executors.newFixedThreadPool(nodes);
		List<Future<TreeMap<String,String>>> futures =
				new ArrayList<Future<TreeMap<String,String>>>();
		try
		{
			for (int node = 0; node < nodes; node++)
			{
				final int shard = node;
				futures.add(executor.submit(
						new Callable<TreeMap<String,String>>()
				{
					public TreeMap<String,String> call()
					{
						TreeMap<String,String> result =
								new TreeMap<String,String>();
						for (int targetIndex = shard;
								targetIndex < points.size();
								targetIndex += nodes)
						{
							Point target = points.get(targetIndex);
							String bestCosineUser = null;
							double bestCosine = -Double.MAX_VALUE;
							String bestEuclideanUser = null;
							double bestEuclidean = Double.MAX_VALUE;
							for (int candidateIndex = 0;
									candidateIndex < points.size();
									candidateIndex++)
							{
								if (candidateIndex == targetIndex)
								{
									continue;
								}
								Point candidate = points.get(candidateIndex);
								double cosine =
										target.cosSimDistance(candidate);
								double euclidean =
										target.eucliDistance(candidate);
								if (cosine > bestCosine)
								{
									bestCosine = cosine;
									bestCosineUser = candidate.getUserName();
								}
								if (euclidean < bestEuclidean)
								{
									bestEuclidean = euclidean;
									bestEuclideanUser = candidate.getUserName();
								}
							}
							require(bestCosineUser != null
											&& bestEuclideanUser != null,
									"no pairwise candidate for "
									+ target.getUserName());
							result.put(
									target.getUserName(),
									bestCosineUser + "\t" + bestEuclideanUser
									+ "\t" + Double.toString(bestCosine)
									+ "\t" + Double.toString(bestEuclidean));
						}
						return result;
					}
				}));
			}
			TreeMap<String,String> result = new TreeMap<String,String>();
			for (Future<TreeMap<String,String>> future : futures)
			{
				result.putAll(future.get(8, TimeUnit.HOURS));
			}
			return result;
		}
		finally
		{
			executor.shutdownNow();
			executor.awaitTermination(30, TimeUnit.SECONDS);
		}
	}

	private static long elapsedMs(long started)
	{
		return (System.nanoTime() - started) / 1000000L;
	}

	private static void require(boolean condition, String message)
	{
		if (!condition)
		{
			throw new AssertionError(message);
		}
	}
}
