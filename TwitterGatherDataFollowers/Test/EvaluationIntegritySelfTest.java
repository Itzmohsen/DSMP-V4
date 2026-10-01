package TwitterGatherDataFollowers.userRyersonU;

import java.io.File;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The pieces that keep the evaluation honest and the runs reproducible:
 * classifier targets are held out in proper folds, the link-prediction scores
 * are the textbook formulas, every algorithm reads one canonical corpus,
 * database queries bind user values instead of splicing them into SQL, the
 * simulation's random draws are seeded by name, and only failures of the
 * environment are retried.
 */
public final class EvaluationIntegritySelfTest
{
	private EvaluationIntegritySelfTest()
	{
	}

	public static void main(String[] args) throws Exception
	{
		targetFoldsHoldEveryTargetOutOnce();
		linkPredictionVariantsMatchTheirDefinitions();
		runCorpusIsCanonical();
		databaseQueriesBindUserValues();
		simulationDrawsAreSeededByName();
		onlyEnvironmentFailuresAreRetried();
		System.out.println("EvaluationIntegritySelfTest passed");
	}

	private static void targetFoldsHoldEveryTargetOutOnce()
	{
		Map<String,String> labels = new LinkedHashMap<String,String>();
		List<String> targets = new ArrayList<String>();
		for (int index = 0; index < 60; index++)
		{
			String user = "u" + index;
			labels.put(user, index % 3 == 0 ? "A" : index % 3 == 1 ? "B" : "C");
			targets.add(user);
		}
		//Few targets (at most 20% of the labelled users): one model, all held out.
		List<List<String>> single = TargetFolds.of(targets.subList(0, 12), labels, 60);
		require(single.size() == 1 && single.get(0).size() == 12, "12 of 60 targets need one model");
		//Many targets: five stratified folds; each target in exactly one fold.
		List<List<String>> folds = TargetFolds.of(targets, labels, 60);
		require(folds.size() == TargetFolds.FOLDS, "60 targets need five folds");
		HashSet<String> seen = new HashSet<String>();
		for (List<String> fold : folds)
		{
			require(fold.size() == 12, "folds are balanced: " + fold.size());
			int[] perClass = new int[3];
			for (String target : fold)
			{
				require(seen.add(target), target + " is in two folds");
				perClass[labels.get(target).charAt(0) - 'A']++;
			}
			require(perClass[0] == 4 && perClass[1] == 4 && perClass[2] == 4, "folds are stratified");
		}
		require(seen.size() == 60, "every target is held out");
		List<String> shuffled = new ArrayList<String>(targets);
		Collections.shuffle(shuffled, new java.util.Random(5L));
		require(TargetFolds.of(shuffled, labels, 60).equals(folds), "folds depend on the target order");
	}

	/**
	 * u = {a, b, c}, v = {b, c, d}; term degrees a 2, b 4, c 8, d 2.
	 * shared {b, c}: CN 2, Jaccard 2/4, Adamic-Adar 1/ln4 + 1/ln8, RA 1/4 + 1/8.
	 */
	private static void linkPredictionVariantsMatchTheirDefinitions()
	{
		Map<String,Double> u = new LinkedHashMap<String,Double>();
		u.put("a", 0.5);
		u.put("b", 0.2);
		u.put("c", 0.1);
		Map<String,Double> v = new LinkedHashMap<String,Double>();
		v.put("b", 0.3);
		v.put("c", 0.4);
		v.put("d", 0.9);
		Map<String,Integer> degree = new HashMap<String,Integer>();
		degree.put("a", 2);
		degree.put("b", 4);
		degree.put("c", 8);
		degree.put("d", 2);
		double aa = 1.0 / Math.log(4.0) + 1.0 / Math.log(8.0);
		requireClose(SparseRecommendationMath.linkPredictionScore(u, v, degree, AlgorithmParameterSettings.CN_JACCARD),
				0.5, "Jaccard");
		requireClose(SparseRecommendationMath.linkPredictionScore(u, v, degree,
				AlgorithmParameterSettings.CN_ADAMIC_ADAR), aa, "Adamic-Adar");
		requireClose(SparseRecommendationMath.linkPredictionScore(u, v, degree,
				AlgorithmParameterSettings.CN_RESOURCE_ALLOCATION), 0.375, "resource allocation");
		requireClose(SparseRecommendationMath.linkPredictionScore(u, v, degree,
				AlgorithmParameterSettings.CN_COMMON_NEIGHBOURS), 2.0 + aa / (1.0 + aa),
				"common neighbours with the Adamic-Adar tie-break");
		//The tie-break never reorders different counts: it stays below 1.
		Map<String,Double> w = new LinkedHashMap<String,Double>(v);
		w.put("a", 1.0);
		require(SparseRecommendationMath.linkPredictionScore(u, w, degree, AlgorithmParameterSettings.CN_COMMON_NEIGHBOURS)
				> SparseRecommendationMath.linkPredictionScore(u, v, degree, AlgorithmParameterSettings.CN_COMMON_NEIGHBOURS) + 0.5,
				"three shared terms outrank two");
	}

	private static void runCorpusIsCanonical() throws Exception
	{
		File directory = Files.createTempDirectory("dsmp-corpus").toFile();
		Map<String,List<Tweet>> tweets = new LinkedHashMap<String,List<Tweet>>();
		tweets.put("zoe", Arrays.asList(
				new Tweet("Café crème, brûlée and more", 20L, "2025-03-02 10:00:00", "zoe"),
				new Tweet("too short", 10L, "2025-03-01", "zoe")));
		tweets.put("adam", Arrays.asList(
				new Tweet("Visiting the museum and the old harbour", 30L, "not a date", "adam")));
		Map<String,String> followees = new HashMap<String,String>();
		followees.put("zoe", "@kitchen");
		followees.put("adam", "@travel");
		List<String> users = Arrays.asList("zoe", "adam");
		File first = RunCorpus.write(directory, users, tweets, followees, true, true, false, Collections.<String>emptySet());
		File second = RunCorpus.write(directory, Arrays.asList("adam", "zoe"), tweets, followees, true, true, false,
				Collections.<String>emptySet());
		require(first.equals(second), "the corpus file is named by its content");
		List<String> lines = Files.readAllLines(first.toPath(), StandardCharsets.UTF_8);
		require(lines.size() == 2, "the two-word tweet is dropped: " + lines);
		require(lines.get(0).startsWith("adam\t@travel\t\t"), "users are sorted and an undated tweet has no time");
		require(lines.get(1).equals("zoe\t@kitchen\t" + RunCorpus.parseTimestamp("2025-03-02 10:00:00")
				+ "\tcafé crème brûlée and more"), "letters of every script are kept: " + lines.get(1));
		require(RunCorpus.parseTimestamp("2025-03-02") == 1740873600000L, "dates are read in UTC");
		final List<RunCorpus.Row> rows = new ArrayList<RunCorpus.Row>();
		RunCorpus.read(first, new RunCorpus.RowConsumer()
		{
			public void accept(RunCorpus.Row row)
			{
				rows.add(row);
			}
		});
		require(rows.size() == 2 && rows.get(0).time == Long.MIN_VALUE && rows.get(1).followee.equals("@kitchen"),
				"rows read back as written");
		for (File file : directory.listFiles())
		{
			file.delete();
		}
		directory.delete();
	}

	/** The SQL text never contains a user value; values are bound in order. */
	private static void databaseQueriesBindUserValues() throws Exception
	{
		final List<String> sql = new ArrayList<String>();
		final List<Object> bound = new ArrayList<Object>();
		final PreparedStatement statement = (PreparedStatement)Proxy.newProxyInstance(
				EvaluationIntegritySelfTest.class.getClassLoader(), new Class<?>[] {PreparedStatement.class},
				new InvocationHandler()
				{
					public Object invoke(Object proxy, Method method, Object[] arguments)
					{
						if (method.getName().startsWith("set") && arguments != null && arguments.length == 2)
						{
							bound.add(arguments[1]);
						}
						return null;
					}
				});
		Connection connection = (Connection)Proxy.newProxyInstance(
				EvaluationIntegritySelfTest.class.getClassLoader(), new Class<?>[] {Connection.class},
				new InvocationHandler()
				{
					public Object invoke(Object proxy, Method method, Object[] arguments)
					{
						if ("prepareStatement".equals(method.getName()))
						{
							sql.add((String)arguments[0]);
							return statement;
						}
						return null;
					}
				});
		String hostile = "o'brien'; DROP TABLE usertweet; --";
		TweetDatabaseQueries.userTweets(connection, hostile, "2007-01-01", "2017-01-01", 500, "alice");
		require(!sql.get(0).contains("brien") && !sql.get(0).contains("2007") && !sql.get(0).contains("alice"),
				"user values reached the SQL text: " + sql.get(0));
		require(bound.equals(Arrays.<Object>asList(hostile, "2007-01-01", "2017-01-01", 500, "alice")),
				"values are bound in order: " + bound);
		require(sql.get(0).contains("LIMIT ?"), "the tweet limit is a parameter");
		bound.clear();
		TweetDatabaseQueries.usersByTweetCount(connection, "@a", "2007-01-01", "2017-01-01", 0);
		require(!sql.get(1).contains("LIMIT") && bound.size() == 3, "no limit, three parameters");
	}

	private static void simulationDrawsAreSeededByName()
	{
		require(SimulationSeeds.forName("alice") == SimulationSeeds.forName("alice"), "seeds are stable");
		require(SimulationSeeds.forName("alice") != SimulationSeeds.forName("bob"), "names give different seeds");
		require(SimulationSeeds.forPair("a", "b") != SimulationSeeds.forPair("b", "a"), "pairs are ordered");
		require(SimulationSeeds.weibull(42L, 0.5, 2.0) == SimulationSeeds.weibull(42L, 0.5, 2.0),
				"a seeded draw repeats");
		//The Weibull mean is scale * Gamma(1 + 1/shape): Gamma(2) = 1 for shape 1.
		double total = 0.0;
		for (long seed = 0; seed < 20000; seed++)
		{
			total += SimulationSeeds.weibull(SimulationSeeds.forName("s" + seed), 1.0, 3.0);
		}
		require(Math.abs(total / 20000.0 - 3.0) < 0.1, "Weibull(1, 3) has mean 3: " + total / 20000.0);
	}

	private static void onlyEnvironmentFailuresAreRetried()
	{
		require(WorkPhase.isTransient(new java.io.IOException("worker died")), "I/O errors are retried");
		require(WorkPhase.isTransient(new IllegalStateException("wrapped", new java.io.IOException("x"))),
				"a wrapped I/O error is retried");
		require(WorkPhase.isTransient(new OutOfMemoryError()), "running out of memory is retried");
		require(!WorkPhase.isTransient(new IllegalStateException("MLP needs two classes")),
				"a deterministic error is not retried");
		require(!WorkPhase.isTransient(new IllegalArgumentException("bad setting")), "a bad setting is not retried");
	}

	private static void requireClose(double actual, double expected, String label)
	{
		if (Math.abs(actual - expected) > 1e-12)
		{
			throw new AssertionError(label + ": expected " + expected + " but was " + actual);
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
