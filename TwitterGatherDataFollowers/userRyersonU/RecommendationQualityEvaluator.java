package TwitterGatherDataFollowers.userRyersonU;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Ranking-quality metrics for the recommendation lists the simulator produces.
 *
 * The simulator previously reported only followee-classification accuracy, which
 * says nothing about whether the ranked top-N list a user actually receives is
 * any good. This evaluator scores the delivered lists against the ground truth
 * already present in the dataset: every user's followee.
 *
 * Relevance depends on what was recommended.
 * <ul>
 *   <li>Followee accounts: a candidate is relevant when it is the target's own
 *       followee.</li>
 *   <li>Similar users: a candidate is relevant when it follows the same followee
 *       as the target, which is the standard "same community" criterion for this
 *       dataset.</li>
 * </ul>
 *
 * Metrics reported, all averaged over the evaluated targets: hit rate at k,
 * precision at k, recall at k, mean reciprocal rank, mean average precision and
 * NDCG at k. Targets with no known ground truth are skipped and counted, so a
 * partial ground truth can never silently inflate a score.
 *
 * This class only observes. It never influences scoring, ordering or output.
 */
final class RecommendationQualityEvaluator
{
	private static final Object LOCK = new Object();
	private static final Map<String,String> GROUND_TRUTH =
			new LinkedHashMap<String,String>();
	private static final List<TargetScore> SCORES = new ArrayList<TargetScore>();
	private static int skippedNoGroundTruth;
	private static int skippedNoCandidates;

	private RecommendationQualityEvaluator()
	{
	}

	/**
	 * Installs the user -> followee ground truth for the coming run and clears
	 * any results carried over from the previous one.
	 */
	static void startRun(Map<String,String> userFollowee)
	{
		synchronized (LOCK)
		{
			GROUND_TRUTH.clear();
			SCORES.clear();
			skippedNoGroundTruth = 0;
			skippedNoCandidates = 0;
			if (userFollowee != null)
			{
				for (Map.Entry<String,String> entry : userFollowee.entrySet())
				{
					String user = entry.getKey();
					String followee = entry.getValue();
					if (user != null && followee != null && followee.length() > 0)
					{
						GROUND_TRUTH.put(user, followee);
					}
				}
			}
		}
	}

	/**
	 * Clears the scores collected for the previous run but keeps the ground
	 * truth, so a second run over the same loaded dataset starts from zero
	 * instead of averaging the two runs together.
	 */
	static void beginRun()
	{
		synchronized (LOCK)
		{
			SCORES.clear();
			skippedNoGroundTruth = 0;
			skippedNoCandidates = 0;
		}
	}

	/** Discards all state, including the ground truth. */
	static void reset()
	{
		synchronized (LOCK)
		{
			GROUND_TRUTH.clear();
			SCORES.clear();
			skippedNoGroundTruth = 0;
			skippedNoCandidates = 0;
		}
	}

	/** True once a ground truth has been installed for this run. */
	static boolean hasGroundTruth()
	{
		synchronized (LOCK)
		{
			return !GROUND_TRUTH.isEmpty();
		}
	}

	/**
	 * Scores one delivered recommendation list.
	 *
	 * @param payload the merged, ranked result handed to the target user
	 * @param cutoff  the k the user asked for (top-N)
	 */
	static void record(RecommendationResultPayload payload, int cutoff)
	{
		if (payload == null
				|| payload.getStatus() != RecommendationResultStatus.OK)
		{
			return;
		}
		int k = Math.max(1, cutoff);
		synchronized (LOCK)
		{
			if (GROUND_TRUTH.isEmpty())
			{
				return;
			}
			String target = payload.getTargetUser();
			String targetFollowee = GROUND_TRUTH.get(target);
			if (targetFollowee == null)
			{
				skippedNoGroundTruth++;
				return;
			}

			List<String> ranked = new ArrayList<String>(payload.getScores().keySet());
			if (ranked.isEmpty())
			{
				skippedNoCandidates++;
				return;
			}

			Set<String> relevant = relevantCandidates(
					payload.getCandidateType(), target, targetFollowee, ranked);
			SCORES.add(evaluate(target, ranked, relevant, k));
		}
	}

	/**
	 * The relevant set for one target.
	 *
	 * In followee mode that is the single account the target actually follows. In
	 * similar-user mode it is every other user in the corpus with the same
	 * followee, which is the full ground-truth relevant set, not just the part
	 * that happened to be returned. Recall at k is therefore small whenever the
	 * relevant set is much larger than k; the report prints the mean relevant-set
	 * size so the figure can be read correctly.
	 */
	private static Set<String> relevantCandidates(
			RecommendationCandidateType candidateType,
			String target,
			String targetFollowee,
			List<String> ranked)
	{
		LinkedHashSet<String> relevant = new LinkedHashSet<String>();
		if (candidateType == RecommendationCandidateType.FOLLOWEE)
		{
			relevant.add(targetFollowee);
			return relevant;
		}
		for (Map.Entry<String,String> entry : GROUND_TRUTH.entrySet())
		{
			String candidate = entry.getKey();
			if (!candidate.equals(target)
					&& targetFollowee.equals(entry.getValue()))
			{
				relevant.add(candidate);
			}
		}
		return relevant;
	}

	private static TargetScore evaluate(
			String target,
			List<String> ranked,
			Set<String> relevant,
			int k)
	{
		int limit = Math.min(k, ranked.size());
		int hits = 0;
		double reciprocalRank = 0.0;
		double sumPrecisionAtHits = 0.0;
		double discountedGain = 0.0;

		for (int index = 0; index < limit; index++)
		{
			if (!relevant.contains(ranked.get(index)))
			{
				continue;
			}
			hits++;
			//log2(rank + 1) with rank counted from 1.
			discountedGain += 1.0 / (Math.log(index + 2.0) / Math.log(2.0));
			sumPrecisionAtHits += hits / (double)(index + 1);
			if (reciprocalRank == 0.0)
			{
				reciprocalRank = 1.0 / (index + 1);
			}
		}

		//Ideal DCG: the best achievable ordering puts min(|relevant|, k) hits first.
		double idealGain = 0.0;
		int idealHits = Math.min(relevant.size(), limit);
		for (int index = 0; index < idealHits; index++)
		{
			idealGain += 1.0 / (Math.log(index + 2.0) / Math.log(2.0));
		}

		double precision = limit == 0 ? 0.0 : hits / (double)limit;
		double recall = relevant.isEmpty() ? 0.0 : hits / (double)relevant.size();
		double averagePrecision = relevant.isEmpty()
				? 0.0 : sumPrecisionAtHits / Math.min(relevant.size(), limit);
		double ndcg = idealGain <= 0.0 ? 0.0 : discountedGain / idealGain;

		return new TargetScore(
				target, hits > 0 ? 1.0 : 0.0, precision, recall,
				reciprocalRank, averagePrecision, ndcg,
				relevant.size(), limit);
	}

	/** Aggregated report, or null when nothing could be evaluated. */
	static Report report(int cutoff)
	{
		synchronized (LOCK)
		{
			if (SCORES.isEmpty())
			{
				return null;
			}
			double hitRate = 0.0, precision = 0.0, recall = 0.0;
			double mrr = 0.0, map = 0.0, ndcg = 0.0;
			double relevantTotal = 0.0;
			for (TargetScore score : SCORES)
			{
				relevantTotal += score.relevantCount;
				hitRate += score.hit;
				precision += score.precision;
				recall += score.recall;
				mrr += score.reciprocalRank;
				map += score.averagePrecision;
				ndcg += score.ndcg;
			}
			int n = SCORES.size();
			return new Report(
					Math.max(1, cutoff), n,
					hitRate / n, precision / n, recall / n,
					mrr / n, map / n, ndcg / n,
					relevantTotal / n,
					skippedNoGroundTruth, skippedNoCandidates);
		}
	}

	/** Aggregated ranking quality for one run. */
	static final class Report
	{
		final int cutoff;
		final int evaluatedTargets;
		final double hitRate;
		final double precision;
		final double recall;
		final double meanReciprocalRank;
		final double meanAveragePrecision;
		final double ndcg;
		final double meanRelevantSetSize;
		final int skippedWithoutGroundTruth;
		final int skippedWithoutCandidates;

		private Report(
				int cutoff, int evaluatedTargets,
				double hitRate, double precision, double recall,
				double meanReciprocalRank, double meanAveragePrecision, double ndcg,
				double meanRelevantSetSize,
				int skippedWithoutGroundTruth, int skippedWithoutCandidates)
		{
			this.cutoff = cutoff;
			this.evaluatedTargets = evaluatedTargets;
			this.hitRate = hitRate;
			this.precision = precision;
			this.recall = recall;
			this.meanReciprocalRank = meanReciprocalRank;
			this.meanAveragePrecision = meanAveragePrecision;
			this.ndcg = ndcg;
			this.meanRelevantSetSize = meanRelevantSetSize;
			this.skippedWithoutGroundTruth = skippedWithoutGroundTruth;
			this.skippedWithoutCandidates = skippedWithoutCandidates;
		}

		/** One-line-per-metric summary for the results pane and the console. */
		String describe()
		{
			StringBuilder text = new StringBuilder();
			text.append("=== Recommendation quality (k=").append(cutoff)
					.append(", ").append(evaluatedTargets)
					.append(evaluatedTargets == 1 ? " target" : " targets")
					.append(") ===\n");
			text.append("Hit rate@").append(cutoff).append(":  ")
					.append(percent(hitRate)).append('\n');
			text.append("Precision@").append(cutoff).append(": ")
					.append(percent(precision)).append('\n');
			text.append("Recall@").append(cutoff).append(":    ")
					.append(percent(recall)).append('\n');
			text.append("NDCG@").append(cutoff).append(":      ")
					.append(percent(ndcg)).append('\n');
			text.append("MRR:          ").append(number(meanReciprocalRank)).append('\n');
			text.append("MAP:          ").append(number(meanAveragePrecision));
			text.append("\nRelevant items per target (mean): ")
					.append(number(meanRelevantSetSize));
			if (meanRelevantSetSize > cutoff)
			{
				text.append("  <- recall@").append(cutoff)
						.append(" cannot exceed ")
						.append(number(cutoff / meanRelevantSetSize));
			}
			if (skippedWithoutGroundTruth > 0 || skippedWithoutCandidates > 0)
			{
				text.append("\nSkipped: ").append(skippedWithoutGroundTruth)
						.append(" target(s) with no known followee, ")
						.append(skippedWithoutCandidates)
						.append(" with no candidates.");
			}
			return text.toString();
		}

		private static String percent(double value)
		{
			return String.format(Locale.US, "%.2f%%", value * 100.0);
		}

		private static String number(double value)
		{
			return String.format(Locale.US, "%.4f", value);
		}
	}

	private static final class TargetScore
	{
		private final String target;
		private final double hit;
		private final double precision;
		private final double recall;
		private final double reciprocalRank;
		private final double averagePrecision;
		private final double ndcg;
		private final int relevantCount;
		private final int consideredCount;

		private TargetScore(
				String target, double hit, double precision, double recall,
				double reciprocalRank, double averagePrecision, double ndcg,
				int relevantCount, int consideredCount)
		{
			this.target = target;
			this.hit = hit;
			this.precision = precision;
			this.recall = recall;
			this.reciprocalRank = reciprocalRank;
			this.averagePrecision = averagePrecision;
			this.ndcg = ndcg;
			this.relevantCount = relevantCount;
			this.consideredCount = consideredCount;
		}
	}

	/** Unmodifiable view of the installed ground truth, for diagnostics. */
	static Map<String,String> groundTruthView()
	{
		synchronized (LOCK)
		{
			return Collections.unmodifiableMap(
					new LinkedHashMap<String,String>(GROUND_TRUTH));
		}
	}
}
