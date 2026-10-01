package TwitterGatherDataFollowers.userRyersonU;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.math3.stat.inference.TTest;

/**
 * The evaluation protocol: metrics worked out by hand, the closed-form random
 * baseline against exhaustive enumeration, the MostPop baseline, the
 * statistics against commons-math, and the protocol's rules (abstaining
 * scores zero, unanswerable targets are skipped, followee mode scores the
 * target's own followee).
 */
public final class RecommendationQualityEvaluatorSelfTest
{
	private static final double EPSILON = 1.0e-9;

	private RecommendationQualityEvaluatorSelfTest()
	{
	}

	public static void main(String[] args)
	{
		metricsMatchHandCalculation();
		shortListsAreNotRewarded();
		randomBaselineMatchesEnumeration();
		similarUsersProtocol();
		followeeProtocolAndTrivialCutoff();
		mostPopularBaseline();
		labelViewAndConfusion();
		statisticsMatchCommonsMath();
		jsonAndRowsAreComplete();
		System.out.println("RecommendationQualityEvaluatorSelfTest passed");
	}

	/**
	 * Relevant {u1, u3} (m = 2), list [u1, u2, u3], k = 3:
	 *   P@3 = 2/3, R@3 = 1, RR = 1, AP@3 = (1/1 + 2/3) / 2,
	 *   NDCG@3 = (1 + 1/log2 4) / (1 + 1/log2 3).
	 */
	private static void metricsMatchHandCalculation()
	{
		double[] values = RecommendationQualityEvaluator.metrics(new boolean[] {true, false, true}, 2, 3);
		requireClose(values[0], 1.0, "hit");
		requireClose(values[1], 2.0 / 3.0, "precision");
		requireClose(values[2], 1.0, "recall");
		requireClose(values[3], (1.0 + 1.0 / log2(4)) / (1.0 + 1.0 / log2(3)), "ndcg");
		requireClose(values[4], 1.0, "reciprocal rank");
		requireClose(values[5], (1.0 + 2.0 / 3.0) / 2.0, "average precision");

		//m = 5 > k = 2: the ideal list has two hits; AP is normalised by min(m, k).
		values = RecommendationQualityEvaluator.metrics(new boolean[] {false, true}, 5, 2);
		requireClose(values[1], 0.5, "precision with m > k");
		requireClose(values[2], 0.2, "recall with m > k");
		requireClose(values[3], (1.0 / log2(3)) / (1.0 + 1.0 / log2(3)), "ndcg with m > k");
		requireClose(values[4], 0.5, "reciprocal rank at 2");
		requireClose(values[5], 0.5 / 2.0, "AP@2 with m > k");
	}

	/** A list shorter than k keeps the denominator k: answering less never helps. */
	private static void shortListsAreNotRewarded()
	{
		double[] one = RecommendationQualityEvaluator.metrics(new boolean[] {true}, 3, 5);
		requireClose(one[1], 1.0 / 5.0, "P@5 of a one-item list");
		requireClose(one[3], 1.0 / (1.0 + 1.0 / log2(3) + 1.0 / log2(4)), "NDCG@5 of a one-item list");
		double[] none = RecommendationQualityEvaluator.metrics(new boolean[0], 3, 5);
		for (double value : none)
		{
			requireClose(value, 0.0, "an empty list scores zero");
		}
	}

	/** Every closed form equals the mean over all permutations of the candidates. */
	private static void randomBaselineMatchesEnumeration()
	{
		for (int n = 1; n <= 6; n++)
		{
			for (int m = 1; m <= n; m++)
			{
				for (int k = 1; k <= 7; k++)
				{
					double[] exact = enumerate(n, m, k);
					double[] closed = RecommendationQualityEvaluator.randomExpectation(n, m, k);
					for (int metric = 0; metric < exact.length; metric++)
					{
						requireClose(closed[metric], exact[metric],
								"random " + RecommendationQualityEvaluator.METRICS[metric]
								+ " n=" + n + " m=" + m + " k=" + k);
					}
				}
			}
		}
	}

	private static double[] enumerate(int n, int m, int k)
	{
		int[] order = new int[n];
		for (int index = 0; index < n; index++)
		{
			order[index] = index;
		}
		double[] total = new double[RecommendationQualityEvaluator.METRICS.length];
		int count = 0;
		do
		{
			boolean[] relevant = new boolean[Math.min(k, n)];
			for (int index = 0; index < relevant.length; index++)
			{
				relevant[index] = order[index] < m;
			}
			double[] values = RecommendationQualityEvaluator.metrics(relevant, m, k);
			for (int metric = 0; metric < total.length; metric++)
			{
				total[metric] += values[metric];
			}
			count++;
		}
		while (nextPermutation(order));
		for (int metric = 0; metric < total.length; metric++)
		{
			total[metric] /= count;
		}
		return total;
	}

	private static boolean nextPermutation(int[] values)
	{
		int i = values.length - 2;
		while (i >= 0 && values[i] >= values[i + 1])
		{
			i--;
		}
		if (i < 0)
		{
			return false;
		}
		int j = values.length - 1;
		while (values[j] <= values[i])
		{
			j--;
		}
		int swap = values[i];
		values[i] = values[j];
		values[j] = swap;
		for (int left = i + 1, right = values.length - 1; left < right; left++, right--)
		{
			swap = values[left];
			values[left] = values[right];
			values[right] = swap;
		}
		return true;
	}

	/**
	 * Communities A = {a1, a2, a3}, B = {b1, b2}, C = {c1}; x has no followee.
	 * Targets a1 (answered), b1 (no list: scores zero), c1 (alone in its
	 * community: skipped), x (unlabelled: skipped).
	 */
	private static void similarUsersProtocol()
	{
		RecommendationQualityEvaluator.Setup setup = setup(RecommendationCandidateType.USER, 2,
				Arrays.asList("a1", "b1", "c1", "x"));
		Map<String,List<RecommendationRunResults.Entry>> ranked =
				new LinkedHashMap<String,List<RecommendationRunResults.Entry>>();
		ranked.put("a1", list("b2", 90.0, "a3", 80.0, "a2", 70.0));
		RecommendationQualityEvaluator.Report report = RecommendationQualityEvaluator.evaluate(setup, ranked);
		require(report.evaluated() == 2, "a1 and b1 are evaluated, got " + report.evaluated());
		require(report.skippedUnlabelled == 1, "x has no followee");
		require(report.skippedNoRelevant == 1, "c1 has no community member to find");
		require(report.emptyLists == 1, "b1 received nothing");
		RecommendationQualityEvaluator.TargetRow a1 = row(report, "a1");
		require(a1.candidates == 6 && a1.relevant == 2, "a1 has 6 candidates, 2 relevant");
		requireClose(a1.metrics[1], 0.5, "a1 P@2: [b2, a3]");
		requireClose(a1.metrics[4], 0.5, "a1 RR: first hit at 2");
		require(a1.firstHit == 2, "a1 first hit");
		require("B".equals(a1.predicted), "a1's top user follows B");
		RecommendationQualityEvaluator.TargetRow b1 = row(report, "b1");
		for (double value : b1.metrics)
		{
			requireClose(value, 0.0, "b1 abstained, so it scores zero");
		}
		require(report.tiesAtCutoff == 0, "no ties at the cutoff");
		//Recommending the target itself is outside its candidate set.
		ranked.put("a1", list("a1", 100.0, "a2", 90.0));
		report = RecommendationQualityEvaluator.evaluate(setup, ranked);
		require(report.foreignCandidates == 1, "self-recommendation is flagged");
		requireClose(row(report, "a1").metrics[1], 0.5, "the self-recommendation is a miss");
	}

	/** Followee mode: only the target's own followee is relevant; k >= classes is flagged. */
	private static void followeeProtocolAndTrivialCutoff()
	{
		RecommendationQualityEvaluator.Setup setup = setup(RecommendationCandidateType.FOLLOWEE, 2,
				Arrays.asList("a1", "b1"));
		Map<String,List<RecommendationRunResults.Entry>> ranked =
				new LinkedHashMap<String,List<RecommendationRunResults.Entry>>();
		ranked.put("a1", list("B", 0.7, "A", 0.3));
		ranked.put("b1", list("B", 0.9, "A", 0.1));
		RecommendationQualityEvaluator.Report report = RecommendationQualityEvaluator.evaluate(setup, ranked);
		RecommendationQualityEvaluator.TargetRow a1 = row(report, "a1");
		require(a1.candidates == 3 && a1.relevant == 1, "three followee accounts, one relevant");
		requireClose(a1.metrics[4], 0.5, "a1's own followee is second");
		requireClose(row(report, "b1").metrics[4], 1.0, "b1's own followee is first");
		require(report.trivialTargets == 0, "3 accounts > k = 2");
		report = RecommendationQualityEvaluator.evaluate(setup(RecommendationCandidateType.FOLLOWEE, 3,
				Arrays.asList("a1", "b1")), ranked);
		require(report.trivialTargets == 2, "k = 3 covers all three accounts");
		require(!report.notes().isEmpty(), "the trivial cutoff is explained");
	}

	/** MostPop ranks the largest community (or account) first, excluding the target's own count. */
	private static void mostPopularBaseline()
	{
		RecommendationQualityEvaluator.Setup setup = setup(RecommendationCandidateType.FOLLOWEE, 1,
				Arrays.asList("a1", "b1"));
		Map<String,List<RecommendationRunResults.Entry>> ranked =
				new LinkedHashMap<String,List<RecommendationRunResults.Entry>>();
		RecommendationQualityEvaluator.Report report = RecommendationQualityEvaluator.evaluate(setup, ranked);
		//A has three followers: MostPop's top-1 is A for both targets.
		requireClose(row(report, "a1").mostPopular[0], 1.0, "MostPop finds A for a1");
		requireClose(row(report, "b1").mostPopular[0], 0.0, "MostPop misses B for b1");
		setup = setup(RecommendationCandidateType.USER, 2, Arrays.asList("b1"));
		report = RecommendationQualityEvaluator.evaluate(setup, ranked);
		//For b1 the other users rank A members first (A has 3, B has 1 left without b1).
		requireClose(row(report, "b1").mostPopular[1], 0.0, "MostPop lists A members for b1");
	}

	private static void labelViewAndConfusion()
	{
		RecommendationQualityEvaluator.Setup setup = setup(RecommendationCandidateType.USER, 1,
				Arrays.asList("a1", "a2", "b1"));
		Map<String,List<RecommendationRunResults.Entry>> ranked =
				new LinkedHashMap<String,List<RecommendationRunResults.Entry>>();
		ranked.put("a1", list("a2", 1.0));
		ranked.put("a2", list("b2", 1.0));
		ranked.put("b1", list("b2", 1.0));
		RecommendationQualityEvaluator.Report report = RecommendationQualityEvaluator.evaluate(setup, ranked);
		requireClose(report.accuracy, 2.0 / 3.0, "two of three top users share the followee");
		require(report.confusion.get("A").get("A").intValue() == 1
				&& report.confusion.get("A").get("B").intValue() == 1
				&& report.confusion.get("B").get("B").intValue() == 1, "confusion matrix");
		//A: precision 1/1, recall 1/2 -> F1 2/3; B: precision 1/2, recall 1 -> F1 2/3.
		requireClose(report.macroF1, 2.0 / 3.0, "macro-F1");
	}

	private static void statisticsMatchCommonsMath()
	{
		double[] values = {0.2, 0.5, 0.9, 0.4, 0.4, 0.7, 0.1};
		double[] interval = RecommendationQualityEvaluator.meanInterval(values);
		double mean = 0.0;
		for (double value : values)
		{
			mean += value;
		}
		mean /= values.length;
		double variance = 0.0;
		for (double value : values)
		{
			variance += (value - mean) * (value - mean);
		}
		variance /= values.length - 1;
		//t(0.975, 6) = 2.446911851.
		double half = 2.446911851 * Math.sqrt(variance / values.length);
		requireClose(interval[0], mean, "mean");
		require(Math.abs(interval[1] - (mean - half)) < 1e-6 && Math.abs(interval[2] - (mean + half)) < 1e-6,
				"95% t interval");
		requireClose(RecommendationQualityEvaluator.pairedPValue(values), new TTest().tTest(0.0, values),
				"one-sample t-test p-value");
		require(Double.isNaN(RecommendationQualityEvaluator.pairedPValue(new double[] {1.0})),
				"no test on one value");
		require(RecommendationQualityEvaluator.pairedPValue(new double[] {0.0, 0.0}) == 1.0,
				"no difference: p = 1");
		//Wilson score interval of 37 hits in 40 targets, against commons-math.
		double[] hits = new double[40];
		for (int index = 0; index < 37; index++)
		{
			hits[index] = 1.0;
		}
		double[] wilson = RecommendationQualityEvaluator.wilsonInterval(hits);
		org.apache.commons.math3.stat.interval.ConfidenceInterval reference =
				new org.apache.commons.math3.stat.interval.WilsonScoreInterval().createInterval(40, 37, 0.95);
		requireClose(wilson[0], 37.0 / 40.0, "Wilson centre is the proportion");
		require(Math.abs(wilson[1] - reference.getLowerBound()) < 1e-9
				&& Math.abs(wilson[2] - reference.getUpperBound()) < 1e-9,
				"Wilson interval [" + wilson[1] + ", " + wilson[2] + "] is not commons-math's ["
				+ reference.getLowerBound() + ", " + reference.getUpperBound() + "]");
		double[] bounded = RecommendationQualityEvaluator.boundedInterval(new double[] {1.0, 1.0, 0.9, 1.0});
		require(bounded[2] <= 1.0 && bounded[1] >= 0.0, "a bounded metric's interval leaves [0, 1]");
	}

	private static void jsonAndRowsAreComplete()
	{
		RecommendationQualityEvaluator.Setup setup = setup(RecommendationCandidateType.USER, 2,
				Arrays.asList("a1", "a2", "b1"));
		Map<String,List<RecommendationRunResults.Entry>> ranked =
				new LinkedHashMap<String,List<RecommendationRunResults.Entry>>();
		ranked.put("a1", list("a2", 1.0, "b1", 0.5));
		ranked.put("a2", list("a3", 1.0, "a1", 0.5));
		ranked.put("b1", list("b2", 1.0, "a1", 1.0, "a2", 1.0));
		RecommendationQualityEvaluator.Report report = RecommendationQualityEvaluator.evaluate(setup, ranked);
		require(report.tiesAtCutoff == 1, "b1's second and third scores tie at the cutoff");
		Map<String,Object> json = report.toJson();
		for (String key : new String[] {"metrics", "top1", "diagnostics", "k", "evaluated"})
		{
			require(json.containsKey(key), "quality.json misses " + key);
		}
		@SuppressWarnings("unchecked")
		Map<String,Object> metrics = (Map<String,Object>)json.get("metrics");
		require(metrics.keySet().equals(new java.util.LinkedHashSet<String>(
				Arrays.asList(RecommendationQualityEvaluator.METRICS))), "every metric is exported");
		String[] columns = RecommendationQualityEvaluator.Report.targetColumns();
		for (Object[] line : report.targetLines())
		{
			require(line.length == columns.length, "a target row has every column");
		}
		require(report.describe().contains("NDCG"), "the text report names its metrics");
		JsonText.of(json);
	}

	/** Users a1..a3 follow A, b1..b2 follow B, c1 follows C, x has no followee. */
	private static RecommendationQualityEvaluator.Setup setup(RecommendationCandidateType type, int k,
			List<String> targets)
	{
		LinkedHashMap<String,String> labels = new LinkedHashMap<String,String>();
		labels.put("a1", "A");
		labels.put("a2", "A");
		labels.put("a3", "A");
		labels.put("b1", "B");
		labels.put("b2", "B");
		labels.put("c1", "C");
		List<String> users = new ArrayList<String>(labels.keySet());
		users.add("x");
		return new RecommendationQualityEvaluator.Setup("self-test", "Test", "Algorithm Default", type, k,
				targets, users, labels);
	}

	private static List<RecommendationRunResults.Entry> list(Object... candidateScorePairs)
	{
		ArrayList<RecommendationRunResults.Entry> entries = new ArrayList<RecommendationRunResults.Entry>();
		for (int index = 0; index < candidateScorePairs.length; index += 2)
		{
			entries.add(new RecommendationRunResults.Entry((String)candidateScorePairs[index],
					((Double)candidateScorePairs[index + 1]).doubleValue()));
		}
		return Collections.unmodifiableList(entries);
	}

	private static RecommendationQualityEvaluator.TargetRow row(RecommendationQualityEvaluator.Report report,
			String target)
	{
		for (RecommendationQualityEvaluator.TargetRow row : report.rows)
		{
			if (row.target.equals(target))
			{
				return row;
			}
		}
		throw new AssertionError("no row for " + target);
	}

	private static double log2(double value)
	{
		return Math.log(value) / Math.log(2.0);
	}

	private static void requireClose(double actual, double expected, String label)
	{
		if (Math.abs(actual - expected) > EPSILON)
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
