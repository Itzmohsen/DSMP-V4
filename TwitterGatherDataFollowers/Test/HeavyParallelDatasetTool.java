package TwitterGatherDataFollowers.userRyersonU;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Generates and validates a deterministic journal-style benchmark designed to
 * make Scalable Linear SVM Similar Users batching benefit from multiple nodes.
 */
public final class HeavyParallelDatasetTool
{
	private static final int CLASS_COUNT = 192;
	private static final int USERS_PER_CLASS = 64;
	private static final int ROWS_PER_USER = 12;
	private static final int GROUP_SIZE = 8;
	private static final int COHORTS_PER_CLASS = 4;
	private static final int CLASS_TERMS = 32;
	private static final int GROUP_TERMS = 12;
	private static final int COHORT_TERMS = 16;
	private static final int NOISE_TERMS_PER_ROW = 8;
	private static final int NOISE_POOL_SIZE = 24576;
	private static final File DEFAULT_FILE = new File(
			"Dataset/Benchmarks/Parallel-ScalableSVM-Journal-147k.txt");

	private HeavyParallelDatasetTool()
	{
	}

	public static void main(String[] args) throws Exception
	{
		String command = args.length == 0 ? "generate" : args[0];
		File file = args.length > 1 ? new File(args[1]) : DEFAULT_FILE;
		if ("generate".equalsIgnoreCase(command))
		{
			generate(file);
			validate(file);
			return;
		}
		if ("validate".equalsIgnoreCase(command))
		{
			validate(file);
			return;
		}
		if ("benchmark".equalsIgnoreCase(command))
		{
			int targetCount = args.length > 2
					? Integer.parseInt(args[2]) : 8;
			int parallelNodes = args.length > 3
					? Integer.parseInt(args[3]) : 8;
			benchmark(file, targetCount, parallelNodes);
			return;
		}
		throw new IllegalArgumentException(
				"Usage: HeavyParallelDatasetTool "
				+ "[generate|validate|benchmark] [dataset] [targets] [nodes]");
	}

	private static void generate(File file) throws Exception
	{
		File parent = file.getParentFile();
		if (parent != null && !parent.exists() && !parent.mkdirs())
		{
			throw new IllegalStateException(
					"Could not create dataset directory: " + parent);
		}

		long rowIndex = 0L;
		try (BufferedWriter writer = new BufferedWriter(
				new OutputStreamWriter(
						new FileOutputStream(file),
						StandardCharsets.UTF_8),
				256 * 1024))
		{
			int userCount = CLASS_COUNT * USERS_PER_CLASS;
			for (int userIndex = 0; userIndex < userCount; userIndex++)
			{
				int classIndex = userIndex % CLASS_COUNT;
				int memberIndex = userIndex / CLASS_COUNT;
				int groupIndex = classIndex / GROUP_SIZE;
				int cohortIndex = memberIndex % COHORTS_PER_CLASS;
				String followee = String.format("JournalClass%03d", classIndex);
				String user = String.format("BenchmarkAuthor%05d", userIndex);
				long userId = 1000000L + userIndex;

				for (int row = 0; row < ROWS_PER_USER; row++)
				{
					StringBuilder text = new StringBuilder(320);
					appendTerms(text, "classword", classIndex * CLASS_TERMS,
							CLASS_TERMS, row * 4, 4);
					appendTerms(text, "groupword", groupIndex * GROUP_TERMS,
							GROUP_TERMS, row * 2, 2);
					int cohortBase =
							(classIndex * COHORTS_PER_CLASS + cohortIndex)
									* COHORT_TERMS;
					appendTerms(text, "cohortword", cohortBase,
							COHORT_TERMS, row * 2, 2);
					for (int slot = 0; slot < NOISE_TERMS_PER_ROW; slot++)
					{
						int occurrence = row * NOISE_TERMS_PER_ROW + slot;
						int noiseIndex = Math.floorMod(
								userIndex * 193 + occurrence * 257,
								NOISE_POOL_SIZE);
						appendToken(text, "noiseword" + letters(noiseIndex));
					}

					long tweetId = 8000000000000000L + rowIndex;
					String date = String.format(
							"2024-01-%02d 12:%02d:00",
							1 + (userIndex + row) % 28,
							(userIndex * 7 + row) % 60);
					writer.write(followee);
					writer.write('\t');
					writer.write(Long.toString(tweetId));
					writer.write('\t');
					writer.write(date);
					writer.write('\t');
					writer.write(Long.toString(userId));
					writer.write('\t');
					writer.write(user);
					writer.write('\t');
					writer.write(text.toString());
					writer.newLine();
					rowIndex++;
				}
			}
		}
		System.out.println(
				"Generated " + file.getPath() + " with " + rowIndex
				+ " rows.");
	}

	private static void appendTerms(
			StringBuilder text,
			String prefix,
			int base,
			int range,
			int offset,
			int count)
	{
		for (int index = 0; index < count; index++)
		{
			appendToken(
					text,
					prefix + letters(base + Math.floorMod(offset + index, range)));
		}
	}

	private static void appendToken(StringBuilder text, String token)
	{
		if (text.length() > 0)
		{
			text.append(' ');
		}
		text.append(token);
	}

	private static String letters(int value)
	{
		StringBuilder result = new StringBuilder();
		int remaining = value;
		do
		{
			result.append((char)('a' + remaining % 26));
			remaining = remaining / 26;
		}
		while (remaining > 0);
		return result.reverse().toString();
	}

	private static void validate(File file) throws Exception
	{
		LinkedHashMap<String,String> mappings = new LinkedHashMap<String,String>();
		LinkedHashMap<String,Integer> userRows = new LinkedHashMap<String,Integer>();
		TreeMap<String,Integer> classUsers = new TreeMap<String,Integer>();
		TreeSet<String> terms = new TreeSet<String>();
		LinkedHashSet<String> firstTargets = new LinkedHashSet<String>();
		long rows = 0L;

		try (BufferedReader reader = reader(file))
		{
			String line;
			while ((line = reader.readLine()) != null)
			{
				rows++;
				DatasetRow row = DatasetRow.parse(line, rows);
				String old = mappings.put(row.userName, row.referenceUser);
				if (old != null && !old.equals(row.referenceUser))
				{
					throw new AssertionError(
							"Conflicting followee mapping for " + row.userName);
				}
				if (old == null)
				{
					increment(classUsers, row.referenceUser);
					if (firstTargets.size() < 40)
					{
						firstTargets.add(row.userName);
					}
				}
				increment(userRows, row.userName);
				String[] tokens = row.text.split(" ");
				if (tokens.length < 3)
				{
					throw new AssertionError(
							"Row " + rows + " has fewer than three tokens.");
				}
				for (String token : tokens)
				{
					if (!token.matches("[a-z]+"))
					{
						throw new AssertionError(
								"Row " + rows
								+ " contains a token that will not survive cleaning: "
								+ token);
					}
					terms.add(token);
				}
			}
		}

		require(rows == (long)CLASS_COUNT * USERS_PER_CLASS * ROWS_PER_USER,
				"unexpected row count: " + rows);
		require(mappings.size() == CLASS_COUNT * USERS_PER_CLASS,
				"unexpected user count: " + mappings.size());
		require(classUsers.size() == CLASS_COUNT,
				"unexpected class count: " + classUsers.size());
		for (Map.Entry<String,Integer> entry : userRows.entrySet())
		{
			require(entry.getValue() == ROWS_PER_USER,
					"unexpected rows for " + entry.getKey());
		}
		for (Map.Entry<String,Integer> entry : classUsers.entrySet())
		{
			require(entry.getValue() == USERS_PER_CLASS,
					"unbalanced class " + entry.getKey());
		}
		TreeSet<String> firstTargetClasses = new TreeSet<String>();
		for (String target : firstTargets)
		{
			firstTargetClasses.add(mappings.get(target));
		}
		require(firstTargetClasses.size() == firstTargets.size(),
				"the first 40 users are not from distinct classes");

		System.out.println(
				"Validated " + file.getPath() + ": "
				+ rows + " rows, " + mappings.size() + " users, "
				+ classUsers.size() + " balanced classes, "
				+ terms.size() + " cleaned vocabulary terms, "
				+ firstTargets.size()
				+ " initial targets from distinct classes.");
	}

	private static void benchmark(
			File file,
			int targetCount,
			int parallelNodes) throws Exception
	{
		Corpus corpus = loadCorpus(file, targetCount);
		System.out.println(
				"Benchmark corpus loaded: " + corpus.documents.size()
				+ " users, " + corpus.targets.size() + " targets.");

		long oneStarted = System.nanoTime();
		ScalableSvmSharedCoordinator.Outcome one = run(
				corpus, 1, "heavy-benchmark-one-" + System.nanoTime());
		long oneMs = elapsedMs(oneStarted);

		long parallelStarted = System.nanoTime();
		ScalableSvmSharedCoordinator.Outcome parallel = run(
				corpus,
				Math.max(1, parallelNodes),
				"heavy-benchmark-parallel-" + System.nanoTime());
		long parallelMs = elapsedMs(parallelStarted);
		requireUsefulOutcome(one, corpus.targets);
		requireUsefulOutcome(parallel, corpus.targets);
		require(one.getScores().equals(parallel.getScores()),
				"one-node and multi-node class scores differ");
		requireEvidenceEqual(one.getEvidence(), parallel.getEvidence());
		System.out.println(
				"Heavy benchmark passed with identical results: oneNode="
				+ oneMs + " ms, " + parallelNodes + "Nodes=" + parallelMs
				+ " ms, speedup="
				+ String.format("%.2fx", oneMs / (double)Math.max(1L, parallelMs)));
	}

	private static void requireUsefulOutcome(
			ScalableSvmSharedCoordinator.Outcome outcome,
			List<String> targets)
	{
		Map<String,TreeMap<String,Double>> scores = outcome.getScores();
		Map<String,TreeMap<String,RecommendationCandidateEvidence>> evidence =
				outcome.getEvidence();
		require(scores.keySet().containsAll(targets),
				"the benchmark did not score every requested target");
		require(evidence.keySet().containsAll(targets),
				"the benchmark did not build evidence for every requested target");
		for (String target : targets)
		{
			boolean positive = false;
			for (Double score : scores.get(target).values())
			{
				positive = positive || score != null
						&& Double.isFinite(score.doubleValue())
						&& score.doubleValue() > 0.0;
			}
			require(positive, "target has no positive class score: " + target);
			require(evidence.get(target) != null
					&& !evidence.get(target).isEmpty(),
					"target has no Similar Users candidate evidence: " + target);
		}
	}

	private static Corpus loadCorpus(File file, int targetCount) throws Exception
	{
		TreeMap<String,LinkedHashMap<String,Double>> documents =
				new TreeMap<String,LinkedHashMap<String,Double>>();
		TreeMap<String,String> followees = new TreeMap<String,String>();
		LinkedHashSet<String> orderedUsers = new LinkedHashSet<String>();
		long lineNumber = 0L;
		try (BufferedReader reader = reader(file))
		{
			String line;
			while ((line = reader.readLine()) != null)
			{
				DatasetRow row = DatasetRow.parse(line, ++lineNumber);
				orderedUsers.add(row.userName);
				followees.put(row.userName, row.referenceUser);
				LinkedHashMap<String,Double> document =
						documents.get(row.userName);
				if (document == null)
				{
					document = new LinkedHashMap<String,Double>();
					documents.put(row.userName, document);
				}
				for (String token : row.text.split(" "))
				{
					Double count = document.get(token);
					document.put(token, count == null ? 1.0 : count + 1.0);
				}
			}
		}
		ArrayList<String> targets = new ArrayList<String>();
		for (String user : orderedUsers)
		{
			if (targets.size() >= Math.max(1, targetCount))
			{
				break;
			}
			targets.add(user);
		}
		return new Corpus(documents, followees, targets);
	}

	private static ScalableSvmSharedCoordinator.Outcome run(
			Corpus corpus,
			int nodes,
			String runId) throws Exception
	{
		RecommendationRunAbortRegistry.prepare(runId);
		if (nodes == 1)
		{
			return ScalableSvmSharedCoordinator.score(
					"HeavyBenchmarkNode1",
					runId,
					1,
					1,
					corpus.documents,
					corpus.followees,
					corpus.followees,
					corpus.targets,
					true);
		}

		List<Map<String,LinkedHashMap<String,Double>>> shards =
				buildShards(corpus.documents, corpus.targets, nodes);
		ExecutorService executor = Executors.newFixedThreadPool(nodes);
		List<Future<ScalableSvmSharedCoordinator.Outcome>> futures =
				new ArrayList<Future<ScalableSvmSharedCoordinator.Outcome>>();
		try
		{
			for (int index = 0; index < nodes; index++)
			{
				final int node = index + 1;
				final Map<String,LinkedHashMap<String,Double>> shard =
						shards.get(index);
				futures.add(executor.submit(
						new Callable<ScalableSvmSharedCoordinator.Outcome>()
				{
					public ScalableSvmSharedCoordinator.Outcome call()
							throws Exception
					{
						return ScalableSvmSharedCoordinator.score(
								"HeavyBenchmarkNode" + node,
								runId,
								nodes,
								node,
								shard,
								corpus.followees,
								corpus.followees,
								corpus.targets,
								true);
					}
				}));
			}
			ScalableSvmSharedCoordinator.Outcome result = null;
			for (Future<ScalableSvmSharedCoordinator.Outcome> future : futures)
			{
				ScalableSvmSharedCoordinator.Outcome current =
						future.get(8, TimeUnit.HOURS);
				if (result == null)
				{
					result = current;
				}
				else
				{
					require(result.getScores().equals(current.getScores()),
							"parallel nodes received different shared scores");
				}
			}
			return result;
		}
		finally
		{
			executor.shutdownNow();
			executor.awaitTermination(30, TimeUnit.SECONDS);
		}
	}

	private static List<Map<String,LinkedHashMap<String,Double>>> buildShards(
			Map<String,LinkedHashMap<String,Double>> documents,
			List<String> targets,
			int nodes)
	{
		LinkedHashSet<String> targetSet = new LinkedHashSet<String>(targets);
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
			if (targetSet.contains(entry.getKey()))
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

	private static void requireEvidenceEqual(
			Map<String,TreeMap<String,RecommendationCandidateEvidence>> expected,
			Map<String,TreeMap<String,RecommendationCandidateEvidence>> actual)
	{
		require(expected.keySet().equals(actual.keySet()),
				"one-node and multi-node evidence targets differ");
		for (String target : expected.keySet())
		{
			Map<String,RecommendationCandidateEvidence> left = expected.get(target);
			Map<String,RecommendationCandidateEvidence> right = actual.get(target);
			require(left.keySet().equals(right.keySet()),
					"candidate evidence differs for " + target);
			for (String candidate : left.keySet())
			{
				RecommendationCandidateEvidence first = left.get(candidate);
				RecommendationCandidateEvidence second = right.get(candidate);
				require(first.getFollowee().equals(second.getFollowee())
						&& Double.doubleToLongBits(first.getTextSimilarity())
								== Double.doubleToLongBits(second.getTextSimilarity()),
						"candidate evidence value differs for " + target
						+ " -> " + candidate);
			}
		}
	}

	private static BufferedReader reader(File file) throws Exception
	{
		return new BufferedReader(
				new InputStreamReader(
						new FileInputStream(file),
						StandardCharsets.UTF_8),
				256 * 1024);
	}

	private static <K> void increment(Map<K,Integer> counts, K key)
	{
		Integer old = counts.get(key);
		counts.put(key, old == null ? 1 : old + 1);
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

	private static final class Corpus
	{
		private final Map<String,LinkedHashMap<String,Double>> documents;
		private final Map<String,String> followees;
		private final List<String> targets;

		private Corpus(
				Map<String,LinkedHashMap<String,Double>> documents,
				Map<String,String> followees,
				List<String> targets)
		{
			this.documents = documents;
			this.followees = followees;
			this.targets = targets;
		}
	}
}
