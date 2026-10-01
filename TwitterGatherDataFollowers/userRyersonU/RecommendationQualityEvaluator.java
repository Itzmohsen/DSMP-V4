package TwitterGatherDataFollowers.userRyersonU;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.apache.commons.math3.distribution.TDistribution;

/**
 * Offline evaluation of a run's recommendation lists against the dataset's
 * ground truth: every user's followee.
 *
 * <h3>Protocol</h3>
 * The run's targets are the queries. For a target t the candidate set U(t) is
 * what the algorithm could have recommended, and the relevant set R(t) is the
 * part of U(t) that is correct:
 * <ul>
 *   <li>similar users: U(t) is every other user of the run with a usable
 *       document; a user is relevant when it follows t's followee (same
 *       community);</li>
 *   <li>followee accounts: U(t) is every followee account of the run; only
 *       t's own followee is relevant.</li>
 * </ul>
 * A target whose followee is unknown, or whose relevant set is empty (it is
 * alone in its community), cannot be scored by any system and is skipped and
 * counted. A target that received no list scores zero: abstaining is a miss,
 * never a skip, so a system cannot raise its score by answering less.
 *
 * <h3>Metrics</h3>
 * Binary relevance, cut off at the requested k: hit rate, P@k = hits / k
 * (a short list does not shrink the denominator), R@k = hits / |R(t)|,
 * AP@k normalised by min(|R(t)|, k), reciprocal rank of the first hit within
 * k, and NDCG@k with the ideal DCG of min(|R(t)|, k) hits. Means are reported
 * with 95% Student-t intervals over targets.
 *
 * <h3>Baselines</h3>
 * <ul>
 *   <li>Random: the exact expectation of every metric under a uniformly random
 *       ranking of U(t) (closed forms, no sampling), per target;</li>
 *   <li>MostPop: the non-personalised ranking by how many other users follow
 *       the candidate's followee (the most popular community, or followee
 *       account, first), ties by name.</li>
 * </ul>
 * Each metric is compared with the random expectation by a paired t-test over
 * targets. A label view scores the top-1 recommendation as a prediction of the
 * target's followee (1-nearest-neighbour for users): accuracy, macro-F1 and a
 * confusion matrix.
 *
 * This class only observes: it never influences scoring, ordering or output.
 */
final class RecommendationQualityEvaluator
{
	static final String[] METRICS = {"hit_rate", "precision", "recall", "ndcg", "mrr", "map"};
	static final String[] METRIC_LABELS = {"Hit rate", "Precision", "Recall", "NDCG", "MRR", "MAP"};
	private static final int HIT = 0, PRECISION = 1, RECALL = 2, NDCG = 3, MRR = 4, MAP = 5;
	private static final String NO_PREDICTION = "(none)";

	/** What one run is evaluated against. */
	static final class Setup
	{
		final String runId;
		final String algorithm;
		final String targetMode;
		final RecommendationCandidateType candidateType;
		final int cutoff;
		/** The run's targets, in order. */
		final List<String> targets;
		/** Users of the run with a usable document: the user candidate set. */
		final Set<String> users;
		/** Followee of every labelled user of the run. */
		final Map<String,String> labels;

		Setup(String runId, String algorithm, String targetMode, RecommendationCandidateType candidateType,
				int cutoff, Collection<String> targets, Collection<String> users, Map<String,String> labels)
		{
			this.runId = runId;
			this.algorithm = algorithm;
			this.targetMode = targetMode;
			this.candidateType = candidateType == null ? RecommendationCandidateType.USER : candidateType;
			this.cutoff = Math.max(1, cutoff);
			this.targets = Collections.unmodifiableList(new ArrayList<String>(
					targets == null ? Collections.<String>emptyList() : targets));
			this.users = Collections.unmodifiableSet(new TreeSet<String>(
					users == null ? Collections.<String>emptySet() : users));
			TreeMap<String,String> known = new TreeMap<String,String>();
			if (labels != null)
			{
				for (Map.Entry<String,String> entry : labels.entrySet())
				{
					if (entry.getKey() != null && entry.getValue() != null && entry.getValue().length() > 0)
					{
						known.put(entry.getKey(), entry.getValue());
					}
				}
			}
			this.labels = Collections.unmodifiableMap(known);
		}

		/** The followee accounts of the run: the followee candidate set. */
		Set<String> followees()
		{
			TreeSet<String> followees = new TreeSet<String>();
			for (String user : users)
			{
				String label = labels.get(user);
				if (label != null)
				{
					followees.add(label);
				}
			}
			return followees;
		}
	}

	private static final Object LOCK = new Object();
	private static final LinkedHashMap<String,Setup> SETUPS = new LinkedHashMap<String,Setup>();
	private static final LinkedHashMap<String,Report> REPORTS = new LinkedHashMap<String,Report>();
	private static final int RETAINED_RUNS = 16;

	private RecommendationQualityEvaluator()
	{
	}

	/** Installs what a run is evaluated against; called when the run is initialised. */
	static void prepare(Setup setup)
	{
		synchronized (LOCK)
		{
			SETUPS.remove(setup.runId);
			SETUPS.put(setup.runId, setup);
			REPORTS.remove(setup.runId);
			while (SETUPS.size() > RETAINED_RUNS)
			{
				String oldest = SETUPS.keySet().iterator().next();
				SETUPS.remove(oldest);
				REPORTS.remove(oldest);
			}
		}
	}

	static Setup setupOf(String runId)
	{
		synchronized (LOCK)
		{
			return SETUPS.get(runId);
		}
	}

	/**
	 * The quality of a run's merged lists, or null when the run was not
	 * prepared for evaluation or has produced no lists yet. Computed once.
	 */
	static Report reportFor(String runId)
	{
		Setup setup;
		synchronized (LOCK)
		{
			Report cached = REPORTS.get(runId);
			if (cached != null)
			{
				return cached;
			}
			setup = SETUPS.get(runId);
		}
		Map<String,List<RecommendationRunResults.Entry>> results = RecommendationRunResults.get(runId);
		if (setup == null || results == null)
		{
			return null;
		}
		Report report = evaluate(setup, results);
		synchronized (LOCK)
		{
			REPORTS.put(runId, report);
		}
		return report;
	}

	/** Evaluates ranked lists (target to candidates in rank order, with scores). */
	static Report evaluate(Setup setup, Map<String,? extends List<RecommendationRunResults.Entry>> ranked)
	{
		boolean followeeMode = setup.candidateType == RecommendationCandidateType.FOLLOWEE;
		int k = setup.cutoff;
		Set<String> followees = setup.followees();
		//How many users of the run follow each account (community sizes).
		HashMap<String,Integer> community = new HashMap<String,Integer>();
		for (String user : setup.users)
		{
			String label = setup.labels.get(user);
			if (label != null)
			{
				Integer count = community.get(label);
				community.put(label, Integer.valueOf(count == null ? 1 : count.intValue() + 1));
			}
		}

		ArrayList<TargetRow> rows = new ArrayList<TargetRow>();
		int skippedUnlabelled = 0;
		int skippedNoRelevant = 0;
		int trivialTargets = 0;
		int tiesAtCutoff = 0;
		int foreignCandidates = 0;
		int emptyLists = 0;
		int shortLists = 0;
		TreeSet<String> recommended = new TreeSet<String>();
		LinkedHashSet<String> seen = new LinkedHashSet<String>();
		for (String target : setup.targets)
		{
			if (target == null || !seen.add(target))
			{
				continue;
			}
			String label = setup.labels.get(target);
			if (label == null)
			{
				skippedUnlabelled++;
				continue;
			}
			//The target's candidate set and relevant set.
			int universe;
			int relevantCount;
			if (followeeMode)
			{
				universe = followees.size();
				relevantCount = followees.contains(label) ? 1 : 0;
			}
			else
			{
				universe = setup.users.size() - (setup.users.contains(target) ? 1 : 0);
				Integer size = community.get(label);
				relevantCount = (size == null ? 0 : size.intValue()) - (setup.users.contains(target) ? 1 : 0);
			}
			if (relevantCount <= 0 || universe <= 0)
			{
				skippedNoRelevant++;
				continue;
			}
			List<RecommendationRunResults.Entry> list = ranked == null ? null : ranked.get(target);
			if (list == null)
			{
				list = Collections.emptyList();
			}
			ArrayList<String> top = new ArrayList<String>();
			for (int index = 0; index < list.size() && top.size() < k; index++)
			{
				top.add(list.get(index).getCandidate());
			}
			if (list.size() > k && list.get(k - 1).getScore() == list.get(k).getScore())
			{
				tiesAtCutoff++;
			}
			if (top.isEmpty())
			{
				emptyLists++;
			}
			else if (top.size() < k)
			{
				shortLists++;
			}
			if (universe <= k)
			{
				trivialTargets++;
			}
			boolean[] relevant = new boolean[top.size()];
			for (int index = 0; index < top.size(); index++)
			{
				String candidate = top.get(index);
				boolean inUniverse = followeeMode ? followees.contains(candidate)
						: setup.users.contains(candidate) && !candidate.equals(target);
				if (!inUniverse)
				{
					foreignCandidates++;
				}
				recommended.add(candidate);
				relevant[index] = inUniverse && (followeeMode ? label.equals(candidate)
						: label.equals(setup.labels.get(candidate)));
			}
			double[] metrics = metrics(relevant, relevantCount, k);
			double[] random = randomExpectation(universe, relevantCount, k);
			double[] popular = metrics(mostPopular(setup, target, label, followeeMode, followees, community, k),
					relevantCount, k);
			String predicted = top.isEmpty() ? NO_PREDICTION
					: followeeMode ? top.get(0)
					: setup.labels.containsKey(top.get(0)) ? setup.labels.get(top.get(0)) : NO_PREDICTION;
			int firstHit = 0;
			for (int index = 0; index < relevant.length; index++)
			{
				if (relevant[index])
				{
					firstHit = index + 1;
					break;
				}
			}
			rows.add(new TargetRow(target, label, top.size(), universe, relevantCount, firstHit,
					metrics, random, popular, predicted));
		}
		int catalog = followeeMode ? followees.size() : setup.users.size();
		return new Report(setup, rows, skippedUnlabelled, skippedNoRelevant, trivialTargets, tiesAtCutoff,
				foreignCandidates, emptyLists, shortLists,
				catalog == 0 ? Double.NaN : countIn(recommended, followeeMode ? followees : setup.users)
						/ (double)catalog);
	}

	private static int countIn(Set<String> items, Set<String> universe)
	{
		int count = 0;
		for (String item : items)
		{
			if (universe.contains(item))
			{
				count++;
			}
		}
		return count;
	}

	/**
	 * Hit, P@k, R@k, NDCG@k, RR@k and AP@k of one list.
	 *
	 * @param relevant  relevance of the list's first (at most k) entries
	 */
	static double[] metrics(boolean[] relevant, int relevantCount, int k)
	{
		double[] values = new double[METRICS.length];
		int hits = 0;
		double dcg = 0.0;
		double precisionSum = 0.0;
		double reciprocal = 0.0;
		for (int index = 0; index < relevant.length && index < k; index++)
		{
			if (!relevant[index])
			{
				continue;
			}
			hits++;
			dcg += discount(index + 1);
			precisionSum += hits / (double)(index + 1);
			if (reciprocal == 0.0)
			{
				reciprocal = 1.0 / (index + 1);
			}
		}
		int ideal = Math.min(relevantCount, k);
		values[HIT] = hits > 0 ? 1.0 : 0.0;
		values[PRECISION] = hits / (double)k;
		values[RECALL] = relevantCount <= 0 ? 0.0 : hits / (double)relevantCount;
		values[NDCG] = ideal <= 0 ? 0.0 : dcg / idealDcg(ideal);
		values[MRR] = reciprocal;
		values[MAP] = ideal <= 0 ? 0.0 : precisionSum / ideal;
		return values;
	}

	/**
	 * The exact expectation of every metric when the list is the first
	 * min(k, n) entries of a uniformly random permutation of n candidates, m of
	 * them relevant. Each follows from linearity of expectation over list
	 * positions: a position is relevant with probability m/n, two positions
	 * both are with probability m(m-1)/(n(n-1)), and the first hit is at i with
	 * probability prod_{j<i-1} (n-m-j)/(n-j) * m/(n-i+1).
	 */
	static double[] randomExpectation(int n, int m, int k)
	{
		double[] values = new double[METRICS.length];
		if (n <= 0 || m <= 0)
		{
			return values;
		}
		int length = Math.min(k, n);
		double p = m / (double)n;
		double pair = n <= 1 ? 0.0 : m * (double)(m - 1) / (n * (double)(n - 1));
		double noHitSoFar = 1.0;
		double expectedDcg = 0.0;
		double expectedPrecisionSum = 0.0;
		double expectedReciprocal = 0.0;
		for (int position = 1; position <= length; position++)
		{
			//P(first hit at this position) = P(no hit before) * m / (n - position + 1).
			double firstHere = noHitSoFar * (m / (double)(n - position + 1));
			expectedReciprocal += firstHere / position;
			noHitSoFar *= Math.max(0.0, (n - m - (position - 1)) / (double)(n - (position - 1)));
			expectedDcg += p * discount(position);
			expectedPrecisionSum += (p + (position - 1) * pair) / position;
		}
		int ideal = Math.min(m, k);
		values[HIT] = 1.0 - noHitSoFar;
		values[PRECISION] = length * p / k;
		values[RECALL] = length / (double)n;
		values[NDCG] = expectedDcg / idealDcg(ideal);
		values[MRR] = expectedReciprocal;
		values[MAP] = expectedPrecisionSum / ideal;
		return values;
	}

	/** Relevance of the MostPop list for one target (the target's own label excluded from the counts). */
	private static boolean[] mostPopular(Setup setup, final String target, String label, boolean followeeMode,
			Set<String> followees, Map<String,Integer> community, int k)
	{
		final HashMap<String,Integer> popularity = new HashMap<String,Integer>();
		for (Map.Entry<String,Integer> entry : community.entrySet())
		{
			int count = entry.getValue().intValue();
			if (entry.getKey().equals(label) && setup.users.contains(target))
			{
				count--;
			}
			popularity.put(entry.getKey(), Integer.valueOf(count));
		}
		ArrayList<String> candidates = new ArrayList<String>();
		final Map<String,String> labels = setup.labels;
		if (followeeMode)
		{
			candidates.addAll(followees);
		}
		else
		{
			for (String user : setup.users)
			{
				if (!user.equals(target))
				{
					candidates.add(user);
				}
			}
		}
		final boolean byAccount = followeeMode;
		Collections.sort(candidates, new java.util.Comparator<String>()
		{
			public int compare(String first, String second)
			{
				int a = weight(first), b = weight(second);
				return a != b ? Integer.compare(b, a) : first.compareTo(second);
			}

			private int weight(String candidate)
			{
				String account = byAccount ? candidate : labels.get(candidate);
				Integer count = account == null ? null : popularity.get(account);
				return count == null ? 0 : count.intValue();
			}
		});
		int length = Math.min(k, candidates.size());
		boolean[] relevant = new boolean[length];
		for (int index = 0; index < length; index++)
		{
			String candidate = candidates.get(index);
			relevant[index] = followeeMode ? label.equals(candidate) : label.equals(labels.get(candidate));
		}
		return relevant;
	}

	private static double discount(int rank)
	{
		return 1.0 / (Math.log(rank + 1.0) / Math.log(2.0));
	}

	private static double idealDcg(int hits)
	{
		double ideal = 0.0;
		for (int rank = 1; rank <= hits; rank++)
		{
			ideal += discount(rank);
		}
		return ideal;
	}

	/** One evaluated target. */
	static final class TargetRow
	{
		final String target;
		final String followee;
		final int listLength;
		final int candidates;
		final int relevant;
		/** Rank of the first relevant recommendation within k; 0 when none. */
		final int firstHit;
		final double[] metrics;
		final double[] random;
		final double[] mostPopular;
		final String predicted;

		TargetRow(String target, String followee, int listLength, int candidates, int relevant, int firstHit,
				double[] metrics, double[] random, double[] mostPopular, String predicted)
		{
			this.target = target;
			this.followee = followee;
			this.listLength = listLength;
			this.candidates = candidates;
			this.relevant = relevant;
			this.firstHit = firstHit;
			this.metrics = metrics;
			this.random = random;
			this.mostPopular = mostPopular;
			this.predicted = predicted;
		}
	}

	/** Mean, 95% interval and comparison with the random baseline of one metric. */
	static final class MetricSummary
	{
		final String key;
		final String label;
		final double mean;
		final double low;
		final double high;
		final double random;
		final double mostPopular;
		/** Mean of (metric - random expectation) over targets. */
		final double gain;
		final double gainLow;
		final double gainHigh;
		/** Two-sided paired t-test p-value against the random expectation. */
		final double pValue;
		/** Mean of (metric - MostPop's metric) over targets, and its paired t-test p-value. */
		final double popularGain;
		final double popularPValue;

		MetricSummary(String key, String label, double mean, double low, double high, double random,
				double mostPopular, double gain, double gainLow, double gainHigh, double pValue,
				double popularGain, double popularPValue)
		{
			this.popularGain = popularGain;
			this.popularPValue = popularPValue;
			this.key = key;
			this.label = label;
			this.mean = mean;
			this.low = low;
			this.high = high;
			this.random = random;
			this.mostPopular = mostPopular;
			this.gain = gain;
			this.gainLow = gainLow;
			this.gainHigh = gainHigh;
			this.pValue = pValue;
		}

		/** Mean over the random expectation; NaN when the baseline is zero. */
		double lift()
		{
			return random > 0.0 ? mean / random : Double.NaN;
		}
	}

	/** The evaluation of one run. */
	static final class Report
	{
		final Setup setup;
		final List<TargetRow> rows;
		final int skippedUnlabelled;
		final int skippedNoRelevant;
		/** Targets whose whole candidate set fits in the top k. */
		final int trivialTargets;
		/** Targets whose k-th and (k+1)-th scores tie, so the cutoff was decided by name. */
		final int tiesAtCutoff;
		/** Recommended entries outside the target's candidate set (should be none). */
		final int foreignCandidates;
		final int emptyLists;
		final int shortLists;
		/** Share of the catalogue recommended to at least one target. */
		final double catalogCoverage;
		final List<MetricSummary> summaries;
		final double accuracy;
		final double macroF1;
		/** Actual followee to predicted followee (top-1) to count. */
		final TreeMap<String,TreeMap<String,Integer>> confusion;
		/** Mean of min(1, k / |R(t)|): the largest mean recall any list could reach. */
		final double recallBound;

		Report(Setup setup, List<TargetRow> rows, int skippedUnlabelled, int skippedNoRelevant,
				int trivialTargets, int tiesAtCutoff, int foreignCandidates, int emptyLists, int shortLists,
				double catalogCoverage)
		{
			this.setup = setup;
			this.rows = Collections.unmodifiableList(rows);
			this.skippedUnlabelled = skippedUnlabelled;
			this.skippedNoRelevant = skippedNoRelevant;
			this.trivialTargets = trivialTargets;
			this.tiesAtCutoff = tiesAtCutoff;
			this.foreignCandidates = foreignCandidates;
			this.emptyLists = emptyLists;
			this.shortLists = shortLists;
			this.catalogCoverage = catalogCoverage;
			ArrayList<MetricSummary> metrics = new ArrayList<MetricSummary>();
			for (int metric = 0; metric < METRICS.length; metric++)
			{
				metrics.add(summarise(metric));
			}
			this.summaries = Collections.unmodifiableList(metrics);
			this.confusion = new TreeMap<String,TreeMap<String,Integer>>();
			int correct = 0;
			double bound = 0.0;
			for (TargetRow row : rows)
			{
				TreeMap<String,Integer> line = confusion.get(row.followee);
				if (line == null)
				{
					line = new TreeMap<String,Integer>();
					confusion.put(row.followee, line);
				}
				Integer count = line.get(row.predicted);
				line.put(row.predicted, Integer.valueOf(count == null ? 1 : count.intValue() + 1));
				if (row.followee.equals(row.predicted))
				{
					correct++;
				}
				bound += Math.min(1.0, setup.cutoff / (double)row.relevant);
			}
			this.accuracy = rows.isEmpty() ? Double.NaN : correct / (double)rows.size();
			this.macroF1 = macroF1(confusion);
			this.recallBound = rows.isEmpty() ? Double.NaN : bound / rows.size();
		}

		int evaluated()
		{
			return rows.size();
		}

		MetricSummary metric(String key)
		{
			for (MetricSummary summary : summaries)
			{
				if (summary.key.equals(key))
				{
					return summary;
				}
			}
			return null;
		}

		private MetricSummary summarise(int metric)
		{
			int n = rows.size();
			double[] values = new double[n];
			double[] gains = new double[n];
			double[] popularGains = new double[n];
			double random = 0.0;
			double popular = 0.0;
			for (int index = 0; index < n; index++)
			{
				TargetRow row = rows.get(index);
				values[index] = row.metrics[metric];
				gains[index] = row.metrics[metric] - row.random[metric];
				popularGains[index] = row.metrics[metric] - row.mostPopular[metric];
				random += row.random[metric];
				popular += row.mostPopular[metric];
			}
			//Every metric lies in [0, 1]. The hit rate is a proportion of targets,
			//so it gets the Wilson score interval; the others a Student-t interval
			//kept inside the metric's range.
			double[] interval = metric == HIT ? wilsonInterval(values) : boundedInterval(values);
			double[] gain = meanInterval(gains);
			return new MetricSummary(METRICS[metric], METRIC_LABELS[metric], interval[0], interval[1],
					interval[2], n == 0 ? Double.NaN : random / n, n == 0 ? Double.NaN : popular / n,
					gain[0], gain[1], gain[2], pairedPValue(gains), meanInterval(popularGains)[0],
					pairedPValue(popularGains));
		}

		/** Plain-text report for the results pane and the console. */
		String describe()
		{
			int k = setup.cutoff;
			StringBuilder text = new StringBuilder();
			text.append("=== Recommendation quality: ").append(setup.algorithm).append(", ")
					.append(setup.candidateType == RecommendationCandidateType.FOLLOWEE
							? "followee accounts" : "similar users")
					.append(", k=").append(k).append(", ").append(rows.size())
					.append(rows.size() == 1 ? " target" : " targets").append(" ===\n");
			if (rows.isEmpty())
			{
				text.append("No target could be evaluated.");
				appendSkips(text);
				return text.toString();
			}
			text.append(String.format(Locale.US, "%-10s %8s  %-17s %8s %8s %6s  %-16s %s%n",
					"Metric@" + k, "Mean", "95% CI", "Random", "MostPop", "Lift", "vs random", "vs MostPop"));
			for (MetricSummary metric : summaries)
			{
				text.append(String.format(Locale.US, "%-10s %8s  %-17s %8s %8s %6s  %-16s %s%n",
						metric.label, number(metric.mean),
						Double.isNaN(metric.low) ? "n/a" : "[" + number(metric.low) + ", " + number(metric.high) + "]",
						number(metric.random), number(metric.mostPopular),
						Double.isNaN(metric.lift()) ? "n/a" : String.format(Locale.US, "%.2fx", metric.lift()),
						significance(metric.pValue), significance(metric.popularPValue)));
			}
			text.append(String.format(Locale.US,
					"Top-1 followee: accuracy %s, macro-F1 %s (%s).%n",
					percent(accuracy), number(macroF1),
					setup.candidateType == RecommendationCandidateType.FOLLOWEE
							? "the top account as the predicted followee"
							: "the top user's followee, a 1-nearest-neighbour prediction"));
			text.append(String.format(Locale.US,
					"Relevant per target: %s mean; recall@%d cannot exceed %s on average. Catalogue coverage %s.",
					number(meanRelevant()), k, number(recallBound), percent(catalogCoverage)));
			for (String note : notes())
			{
				text.append("\nNote: ").append(note);
			}
			appendSkips(text);
			return text.toString();
		}

		/** Conditions under which a figure must be read with care. */
		List<String> notes()
		{
			ArrayList<String> notes = new ArrayList<String>();
			int k = setup.cutoff;
			if (trivialTargets > 0)
			{
				notes.add(trivialTargets + " target(s) have at most k=" + k
						+ " candidates, so any complete list contains every relevant item:"
						+ " hit rate and recall are 100% there by construction. Compare NDCG and MRR,"
						+ " or use a smaller k.");
			}
			if (emptyLists > 0)
			{
				notes.add(emptyLists + " target(s) received no recommendation; they score zero.");
			}
			if (shortLists > 0)
			{
				notes.add(shortLists + " list(s) are shorter than k; missing positions count as misses.");
			}
			if (tiesAtCutoff > 0)
			{
				notes.add(tiesAtCutoff + " list(s) tie at the k-th score; equal scores are ordered by name.");
			}
			if (foreignCandidates > 0)
			{
				notes.add(foreignCandidates + " recommended item(s) are outside the target's candidate set"
						+ " (for example the target itself); they count as misses.");
			}
			if (rows.size() < 10)
			{
				notes.add("Fewer than ten targets: the intervals are wide and the tests weak.");
			}
			for (MetricSummary metric : summaries)
			{
				if (!Double.isNaN(metric.random) && metric.random >= 0.9)
				{
					notes.add(metric.label + "@" + k + " is " + String.format(Locale.US, "%.0f%%", metric.random
							* 100.0) + " by chance alone here, so it cannot separate models; read NDCG, MRR and"
							+ " MAP instead.");
				}
			}
			return notes;
		}

		double meanRelevant()
		{
			double total = 0.0;
			for (TargetRow row : rows)
			{
				total += row.relevant;
			}
			return rows.isEmpty() ? Double.NaN : total / rows.size();
		}

		private void appendSkips(StringBuilder text)
		{
			if (skippedUnlabelled > 0 || skippedNoRelevant > 0)
			{
				text.append("\nNot evaluated: ").append(skippedUnlabelled)
						.append(" target(s) with no known followee, ").append(skippedNoRelevant)
						.append(" with no relevant candidate in the run.");
			}
		}

		/** The report as JSON-ready maps and lists (quality.json). */
		LinkedHashMap<String,Object> toJson()
		{
			LinkedHashMap<String,Object> json = new LinkedHashMap<String,Object>();
			json.put("format", "dsmp-quality/1");
			json.put("runId", setup.runId);
			json.put("algorithm", setup.algorithm);
			json.put("targetMode", setup.targetMode);
			json.put("candidateType", setup.candidateType == RecommendationCandidateType.FOLLOWEE
					? "followee" : "user");
			json.put("k", Integer.valueOf(setup.cutoff));
			json.put("targets", Integer.valueOf(setup.targets.size()));
			json.put("evaluated", Integer.valueOf(rows.size()));
			json.put("skippedUnlabelled", Integer.valueOf(skippedUnlabelled));
			json.put("skippedNoRelevant", Integer.valueOf(skippedNoRelevant));
			json.put("candidateUsers", Integer.valueOf(setup.users.size()));
			json.put("followeeAccounts", Integer.valueOf(setup.followees().size()));
			LinkedHashMap<String,Object> metrics = new LinkedHashMap<String,Object>();
			for (MetricSummary metric : summaries)
			{
				LinkedHashMap<String,Object> entry = new LinkedHashMap<String,Object>();
				entry.put("mean", json(metric.mean));
				entry.put("ci95", Collections.<Object>unmodifiableList(java.util.Arrays.<Object>asList(
						json(metric.low), json(metric.high))));
				entry.put("random", json(metric.random));
				entry.put("mostPopular", json(metric.mostPopular));
				entry.put("lift", json(metric.lift()));
				entry.put("gainOverRandom", json(metric.gain));
				entry.put("gainCi95", Collections.<Object>unmodifiableList(java.util.Arrays.<Object>asList(
						json(metric.gainLow), json(metric.gainHigh))));
				entry.put("pValue", json(metric.pValue));
				entry.put("gainOverMostPopular", json(metric.popularGain));
				entry.put("pValueVsMostPopular", json(metric.popularPValue));
				metrics.put(metric.key, entry);
			}
			json.put("metrics", metrics);
			LinkedHashMap<String,Object> labels = new LinkedHashMap<String,Object>();
			labels.put("accuracy", json(accuracy));
			labels.put("macroF1", json(macroF1));
			LinkedHashMap<String,Object> matrix = new LinkedHashMap<String,Object>();
			for (Map.Entry<String,TreeMap<String,Integer>> line : confusion.entrySet())
			{
				matrix.put(line.getKey(), new LinkedHashMap<String,Object>(line.getValue()));
			}
			labels.put("confusion", matrix);
			json.put("top1", labels);
			LinkedHashMap<String,Object> diagnostics = new LinkedHashMap<String,Object>();
			diagnostics.put("meanRelevant", json(meanRelevant()));
			diagnostics.put("recallBound", json(recallBound));
			diagnostics.put("catalogCoverage", json(catalogCoverage));
			diagnostics.put("trivialTargets", Integer.valueOf(trivialTargets));
			diagnostics.put("tiesAtCutoff", Integer.valueOf(tiesAtCutoff));
			diagnostics.put("foreignCandidates", Integer.valueOf(foreignCandidates));
			diagnostics.put("emptyLists", Integer.valueOf(emptyLists));
			diagnostics.put("shortLists", Integer.valueOf(shortLists));
			diagnostics.put("notes", new ArrayList<Object>(notes()));
			json.put("diagnostics", diagnostics);
			return json;
		}

		/** Header of {@link #targetLines()}. */
		static String[] targetColumns()
		{
			ArrayList<String> columns = new ArrayList<String>();
			Collections.addAll(columns, "target", "followee", "list_length", "candidates", "relevant",
					"first_hit_rank", "predicted_followee");
			for (String metric : METRICS)
			{
				columns.add(metric);
			}
			for (String metric : METRICS)
			{
				columns.add(metric + "_random");
			}
			for (String metric : METRICS)
			{
				columns.add(metric + "_mostpop");
			}
			return columns.toArray(new String[columns.size()]);
		}

		/** One row per evaluated target (quality-targets.csv). */
		List<Object[]> targetLines()
		{
			ArrayList<Object[]> lines = new ArrayList<Object[]>();
			for (TargetRow row : rows)
			{
				ArrayList<Object> line = new ArrayList<Object>();
				Collections.addAll(line, row.target, row.followee, Integer.valueOf(row.listLength),
						Integer.valueOf(row.candidates), Integer.valueOf(row.relevant),
						Integer.valueOf(row.firstHit), row.predicted);
				for (double value : row.metrics)
				{
					line.add(Double.valueOf(value));
				}
				for (double value : row.random)
				{
					line.add(Double.valueOf(value));
				}
				for (double value : row.mostPopular)
				{
					line.add(Double.valueOf(value));
				}
				lines.add(line.toArray());
			}
			return lines;
		}

		private static Object json(double value)
		{
			return Double.isNaN(value) || Double.isInfinite(value) ? null : Double.valueOf(value);
		}
	}

	/** Mean and 95% Student-t interval; the interval is NaN below two values. */
	static double[] meanInterval(double[] values)
	{
		int n = values.length;
		if (n == 0)
		{
			return new double[] {Double.NaN, Double.NaN, Double.NaN};
		}
		double mean = 0.0;
		for (double value : values)
		{
			mean += value;
		}
		mean /= n;
		if (n < 2)
		{
			return new double[] {mean, Double.NaN, Double.NaN};
		}
		double squares = 0.0;
		for (double value : values)
		{
			squares += (value - mean) * (value - mean);
		}
		double standardError = Math.sqrt(squares / (n - 1)) / Math.sqrt(n);
		double half = new TDistribution(null, n - 1).inverseCumulativeProbability(0.975) * standardError;
		return new double[] {mean, mean - half, mean + half};
	}

	/** The Student-t interval of the mean, kept inside [0, 1]. */
	static double[] boundedInterval(double[] values)
	{
		double[] interval = meanInterval(values);
		if (!Double.isNaN(interval[1]))
		{
			interval[1] = Math.max(0.0, interval[1]);
			interval[2] = Math.min(1.0, interval[2]);
		}
		return interval;
	}

	/**
	 * The 95% Wilson score interval of a proportion of 0/1 values: unlike the
	 * normal approximation it stays inside [0, 1] and keeps its coverage when
	 * the proportion is near 0 or 1.
	 */
	static double[] wilsonInterval(double[] values)
	{
		int n = values.length;
		if (n == 0)
		{
			return new double[] {Double.NaN, Double.NaN, Double.NaN};
		}
		double successes = 0.0;
		for (double value : values)
		{
			successes += value;
		}
		double p = successes / n;
		double z = 1.959963984540054;
		double z2 = z * z;
		double centre = (p + z2 / (2.0 * n)) / (1.0 + z2 / n);
		double half = z * Math.sqrt(p * (1.0 - p) / n + z2 / (4.0 * n * (double)n)) / (1.0 + z2 / n);
		return new double[] {p, Math.max(0.0, centre - half), Math.min(1.0, centre + half)};
	}

	/**
	 * Two-sided p-value of a one-sample t-test that the differences have mean
	 * zero. NaN below two values; with no spread, 0 when the mean is non-zero
	 * and 1 when it is zero.
	 */
	static double pairedPValue(double[] differences)
	{
		int n = differences.length;
		if (n < 2)
		{
			return Double.NaN;
		}
		double mean = 0.0;
		for (double value : differences)
		{
			mean += value;
		}
		mean /= n;
		double squares = 0.0;
		for (double value : differences)
		{
			squares += (value - mean) * (value - mean);
		}
		double standardError = Math.sqrt(squares / (n - 1)) / Math.sqrt(n);
		if (standardError <= 1e-15)
		{
			return Math.abs(mean) <= 1e-15 ? 1.0 : 0.0;
		}
		double t = Math.abs(mean / standardError);
		return Math.min(1.0, 2.0 * new TDistribution(null, n - 1).cumulativeProbability(-t));
	}

	/** Macro-F1 over every followee that is an actual or a predicted label. */
	static double macroF1(Map<String,? extends Map<String,Integer>> confusion)
	{
		HashMap<String,int[]> counts = new HashMap<String,int[]>(); //tp, actual, predicted
		for (Map.Entry<String,? extends Map<String,Integer>> line : confusion.entrySet())
		{
			for (Map.Entry<String,Integer> cell : line.getValue().entrySet())
			{
				int count = cell.getValue().intValue();
				int[] actual = counter(counts, line.getKey());
				actual[1] += count;
				if (!NO_PREDICTION.equals(cell.getKey()))
				{
					counter(counts, cell.getKey())[2] += count;
				}
				if (line.getKey().equals(cell.getKey()))
				{
					actual[0] += count;
				}
			}
		}
		if (counts.isEmpty())
		{
			return Double.NaN;
		}
		double total = 0.0;
		for (int[] count : counts.values())
		{
			double precision = count[2] == 0 ? 0.0 : count[0] / (double)count[2];
			double recall = count[1] == 0 ? 0.0 : count[0] / (double)count[1];
			total += precision + recall == 0.0 ? 0.0 : 2.0 * precision * recall / (precision + recall);
		}
		return total / counts.size();
	}

	private static int[] counter(Map<String,int[]> counts, String label)
	{
		int[] count = counts.get(label);
		if (count == null)
		{
			count = new int[3];
			counts.put(label, count);
		}
		return count;
	}

	static String significance(double pValue)
	{
		if (Double.isNaN(pValue))
		{
			return "n/a";
		}
		String stars = pValue < 0.001 ? " ***" : pValue < 0.01 ? " **" : pValue < 0.05 ? " *" : "";
		return (pValue < 0.0001 ? "p<0.0001" : String.format(Locale.US, "p=%.4f", pValue)) + stars;
	}

	private static String number(double value)
	{
		return Double.isNaN(value) ? "n/a" : String.format(Locale.US, "%.4f", value);
	}

	private static String percent(double value)
	{
		return Double.isNaN(value) ? "n/a" : String.format(Locale.US, "%.1f%%", value * 100.0);
	}

	/** Kept for callers that only need to know whether a run can be evaluated. */
	static boolean isPrepared(String runId)
	{
		return setupOf(runId) != null;
	}
}
