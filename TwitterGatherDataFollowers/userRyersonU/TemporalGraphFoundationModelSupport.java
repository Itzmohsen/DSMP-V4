package TwitterGatherDataFollowers.userRyersonU;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeMap;

/**
 * The Temporal Graph Foundation (TGF) recommender: a learned fusion of three
 * label-free similarity signals between a target user and a candidate user.
 *
 * <ol>
 * <li><b>Text</b>: cosine similarity of the users' TF-IDF vectors.</li>
 * <li><b>Graph</b>: mass diffusion on the user-term bipartite graph (Zhou et
 *     al., 2007): a unit of resource leaves the target, spreads over its terms in
 *     proportion to their TF-IDF weight and from each term to the users that
 *     use it in proportion to their weight on it; shared rare terms carry more
 *     resource than shared common ones. Normalised by the target's best
 *     candidate.</li>
 * <li><b>Time</b>: temporal co-activity, the cosine similarity of the two
 *     users' activity histograms over the corpus's dated span (UTC). Without
 *     at least two distinct timestamps the signal is unavailable; row order in
 *     a file is never used as a clock.</li>
 * </ol>
 *
 * <b>Fusion.</b> P(candidate shares the target's community | signals) is
 * modelled by an L2-regularised logistic regression fitted by iteratively
 * reweighted least squares on pairs of labelled users that are <i>not</i>
 * recommendation targets: for each sampled anchor, its same-followee users
 * are positives and an equal number of other users negatives. The intercept is
 * then corrected from the balanced sample to the true base rate of same-community
 * pairs, so the fused score is a calibrated probability. A target's own
 * followee is never read when it is scored: it only grades the result
 * afterwards (relevant rank, NDCG, ablation lenses).
 *
 * <b>Uncertainty.</b> The model is refitted on bootstrap resamples of the
 * anchors (a cluster bootstrap, since one anchor's pairs are correlated); each
 * candidate gets the 5-95% interval of its probability and the share of
 * refits that keep it in the target's top ten. A held-out 20% of the anchors
 * gives the model's pair AUC.
 *
 * Everything is deterministic: anchors, pairs, bootstrap resamples and folds
 * come from seeded generators, ties break by user name, and the prepared
 * state is computed once per run from the whole corpus, so the result is the
 * same for every node count.
 */
final class TemporalGraphFoundationModelSupport
{
	private static final int DETAIL_TERM_LIMIT = 4;
	private static final int TIMELINE_FRAME_COUNT = 12;
	private static final int ACTIVITY_BINS = 24;
	private static final int MAX_ANCHORS = 200;
	private static final int PAIRS_PER_CLASS = 10;
	private static final int BOOTSTRAP_FITS = 30;
	private static final int STABILITY_TOP = 10;
	private static final double L2_PENALTY = 1.0;
	private static final long SEED = 0x7E6F0A11L;
	private static final String[] SIGNALS = {"text", "graph", "time"};
	private static final int TEXT = 0;
	private static final int GRAPH = 1;
	private static final int TIME = 2;

	private TemporalGraphFoundationModelSupport()
	{
	}

	// =================================================================== state

	/**
	 * Everything the model needs that does not depend on the target: user
	 * profiles, the diffusion index, activity histograms and the fitted
	 * fusion. Built once per run from the whole corpus and then only read, so
	 * targets can be scored independently, in any order and on any node.
	 */
	static final class Prepared implements PayloadSize.Sized
	{
		private final Input input;
		private final List<UserProfile> users;
		private final LinkedHashMap<String,UserProfile> profiles;
		private final DiffusionIndex diffusion;
		private final TemporalCorpus corpus;
		private final FusionModel model;
		private volatile long payloadBytes = -1L;

		/**
		 * Wire size of the prepared state every node needs: each profile (name,
		 * followee, TF-IDF vector, activity histogram) and the fitted model.
		 */
		public long payloadBytes()
		{
			long cached = payloadBytes;
			if (cached >= 0L)
			{
				return cached;
			}
			long total = 64L;
			for (UserProfile profile : users)
			{
				total += PayloadSize.string(profile.name) + PayloadSize.string(profile.followee)
						+ PayloadSize.sparseVector(profile.tfidf) + 8L * ACTIVITY_BINS + 32L;
			}
			total += 8L * 4L * (BOOTSTRAP_FITS + 1);
			payloadBytes = total;
			return total;
		}

		private Prepared(Input input, List<UserProfile> users, DiffusionIndex diffusion,
				TemporalCorpus corpus, FusionModel model)
		{
			this.input = input;
			this.users = users;
			this.profiles = new LinkedHashMap<String,UserProfile>();
			for (UserProfile profile : users)
			{
				profiles.put(profile.name, profile);
			}
			this.diffusion = diffusion;
			this.corpus = corpus;
			this.model = model;
		}

		boolean isEmpty()
		{
			return users.isEmpty();
		}

		boolean hasProfile(String user)
		{
			return profiles.containsKey(user);
		}

		int profileCount()
		{
			return users.size();
		}
	}

	/** One target's scored candidates, kept rows and report. */
	static final class TargetOutcome
	{
		private final String target;
		private final TreeMap<String,Double> scores;
		private final TreeMap<String,String> details;
		private final TargetReport report;
		private final int scoredPairs;

		private TargetOutcome(String target, TreeMap<String,Double> scores, TreeMap<String,String> details,
				TargetReport report, int scoredPairs)
		{
			this.target = target;
			this.scores = scores;
			this.details = details;
			this.report = report;
			this.scoredPairs = scoredPairs;
		}

		String getTarget()
		{
			return target;
		}

		TreeMap<String,Double> getScores()
		{
			return scores;
		}

		int getScoredPairs()
		{
			return scoredPairs;
		}
	}

	// ================================================================ pipeline

	static Result score(Input input)
	{
		Prepared prepared = prepare(input);
		if (prepared.isEmpty() || prepared.input.targetUsers.isEmpty())
		{
			return Result.empty(prepared.input.runId, prepared.input.datasetFile,
					"No usable user profiles were available.");
		}
		ArrayList<TargetOutcome> outcomes = new ArrayList<TargetOutcome>();
		for (String targetUser : prepared.input.targetUsers)
		{
			TargetOutcome outcome = scoreTarget(prepared, targetUser);
			if (outcome != null)
			{
				outcomes.add(outcome);
			}
		}
		return assemble(prepared, outcomes);
	}

	static Prepared prepare(Input input)
	{
		Input safe = input == null ? Input.empty() : input;
		List<UserProfile> users = buildProfiles(safe);
		DiffusionIndex diffusion = new DiffusionIndex(users);
		TemporalCorpus corpus = readActivity(safe.datasetFile, users);
		HashSet<String> targets = new HashSet<String>(safe.targetUsers);
		FusionModel model = FusionModel.fit(users, targets, diffusion, corpus);
		return new Prepared(safe, users, diffusion, corpus, model);
	}

	/**
	 * Scores {@code targetUser} against every other user and keeps the top
	 * {@code localResultLimit}. Returns null when the target has no profile.
	 * Reads the prepared state only; the target's followee is not used.
	 */
	static TargetOutcome scoreTarget(Prepared prepared, String targetUser)
	{
		UserProfile target = prepared.profiles.get(targetUser);
		if (target == null)
		{
			return null;
		}
		double[][] features = prepared.model.features(target, prepared.users, prepared.diffusion, prepared.corpus);
		List<CandidateScore> all = new ArrayList<CandidateScore>();
		for (int index = 0; index < prepared.users.size(); index++)
		{
			if ((index & 63) == 0)
			{
				//A revoked unit (drain, crash) stops here.
				UnitCancellation.check();
			}
			UserProfile candidate = prepared.users.get(index);
			if (candidate == target)
			{
				continue;
			}
			CandidateScore score = new CandidateScore(target, candidate);
			score.text = features[index][TEXT];
			score.graph = features[index][GRAPH];
			score.time = features[index][TIME];
			score.finalScore = prepared.model.probability(features[index]);
			all.add(score);
		}
		Collections.sort(all, CandidateScore.FINAL_ORDER);
		for (int i = 0; i < all.size(); i++)
		{
			all.get(i).rank = i + 1;
		}
		List<BenchmarkRow> lenses = benchmarkRows(target, all, prepared.corpus.available);
		int keep = prepared.input.localResultLimit <= 0
				? all.size() : Math.min(prepared.input.localResultLimit, all.size());
		List<CandidateScore> kept = new ArrayList<CandidateScore>(all.subList(0, keep));
		explain(target, kept, all, prepared);
		TreeMap<String,Double> rowScores = new TreeMap<String,Double>();
		TreeMap<String,String> rowDetails = new TreeMap<String,String>();
		for (CandidateScore score : kept)
		{
			if (score.finalScore > 0.0 && Double.isFinite(score.finalScore))
			{
				rowScores.put(score.candidate.name, score.finalScore);
				rowDetails.put(score.candidate.name, detailFor(score));
			}
		}
		return new TargetOutcome(target.name, rowScores, rowDetails,
				TargetReport.from(target, kept, all, lenses, prepared.corpus), all.size());
	}

	/** The run's result from the outcomes of its targets, in target order. */
	static Result assemble(Prepared prepared, List<TargetOutcome> outcomes)
	{
		TreeMap<String,TreeMap<String,Double>> scoreRows = new TreeMap<String,TreeMap<String,Double>>();
		TreeMap<String,TreeMap<String,String>> detailRows = new TreeMap<String,TreeMap<String,String>>();
		LinkedHashMap<String,TargetReport> reports = new LinkedHashMap<String,TargetReport>();
		int scoredPairs = 0;
		for (TargetOutcome outcome : outcomes)
		{
			if (outcome == null)
			{
				continue;
			}
			scoreRows.put(outcome.target, outcome.scores);
			detailRows.put(outcome.target, outcome.details);
			reports.put(outcome.target, outcome.report);
			scoredPairs += outcome.scoredPairs;
		}
		String summary = "Temporal Graph Foundation scored " + scoredPairs
				+ " target-candidate pairs. " + prepared.model.describe();
		ResearchSummary research = ResearchSummary.from(reports.values(), scoredPairs, prepared.corpus,
				prepared.model);
		return new Result(prepared.input.runId, prepared.input.datasetFile, summary,
				prepared.corpus.rowsScanned, prepared.corpus.datedRows, 0,
				scoreRows, detailRows, reports, research);
	}

	// ================================================================ profiles

	private static List<UserProfile> buildProfiles(Input input)
	{
		ArrayList<UserProfile> users = new ArrayList<UserProfile>();
		for (Map.Entry<String,? extends Map<String,Double>> entry : new TreeMap<String,Map<String,Double>>(
				castVectors(input.tfidfDocuments)).entrySet())
		{
			if (entry.getKey() == null || entry.getValue() == null || entry.getValue().isEmpty())
			{
				continue;
			}
			String user = entry.getKey();
			String followee = input.datasetUserFollowees.get(user);
			if (followee == null)
			{
				followee = input.userFollowees.get(user);
			}
			users.add(new UserProfile(users.size(), user, followee, entry.getValue()));
		}
		return users;
	}

	@SuppressWarnings("unchecked")
	private static Map<String,Map<String,Double>> castVectors(Map<String,? extends Map<String,Double>> vectors)
	{
		return (Map<String,Map<String,Double>>)vectors;
	}

	/**
	 * Reads each user's activity: from the run corpus (RunCorpus: user,
	 * followee, epoch millis, text) or a raw dataset file (followee, id, date,
	 * user id, user, text), timestamps in UTC. Users outside the profiles are
	 * ignored.
	 */
	private static TemporalCorpus readActivity(File file, List<UserProfile> users)
	{
		final TemporalCorpus corpus = new TemporalCorpus();
		final HashMap<String,UserProfile> byName = new HashMap<String,UserProfile>();
		for (UserProfile profile : users)
		{
			byName.put(profile.name, profile);
		}
		final HashMap<UserProfile,List<Long>> times = new HashMap<UserProfile,List<Long>>();
		if (file == null || !file.isFile())
		{
			corpus.warning = "No corpus file was available, so the temporal signal is off.";
			return corpus;
		}
		try
		{
			if (file.getName().startsWith("corpus-") && file.getName().endsWith(".tsv"))
			{
				RunCorpus.read(file, new RunCorpus.RowConsumer()
				{
					public void accept(RunCorpus.Row row)
					{
						observe(corpus, byName.get(row.user), row.time, times);
					}
				});
			}
			else
			{
				try (BufferedReader reader = new BufferedReader(new InputStreamReader(
						new FileInputStream(file), StandardCharsets.UTF_8), 128 * 1024))
				{
					String line;
					boolean first = true;
					while ((line = reader.readLine()) != null)
					{
						if (first && line.startsWith("﻿"))
						{
							line = line.substring(1);
						}
						first = false;
						String[] fields = line.split("\t", 6);
						if (fields.length != 6)
						{
							continue;
						}
						observe(corpus, byName.get(fields[4].trim()), RunCorpus.parseTimestamp(fields[2]), times);
					}
				}
			}
		}
		catch (IOException exception)
		{
			corpus.warning = "The temporal scan failed: " + exception.getMessage();
			return corpus;
		}
		corpus.available = corpus.datedRows > 0 && corpus.maxTime > corpus.minTime;
		if (!corpus.available)
		{
			corpus.warning = "Fewer than two distinct timestamps: the temporal signal is off.";
			return corpus;
		}
		double span = corpus.maxTime - corpus.minTime;
		for (Map.Entry<UserProfile,List<Long>> entry : times.entrySet())
		{
			double[] histogram = new double[ACTIVITY_BINS];
			for (Long time : entry.getValue())
			{
				int bin = (int)Math.min(ACTIVITY_BINS - 1, Math.floor((time - corpus.minTime) / span * ACTIVITY_BINS));
				histogram[bin] += 1.0;
			}
			entry.getKey().activity = histogram;
		}
		return corpus;
	}

	private static void observe(TemporalCorpus corpus, UserProfile profile, long time,
			Map<UserProfile,List<Long>> times)
	{
		corpus.rowsScanned++;
		if (profile == null)
		{
			return;
		}
		profile.observedRows++;
		if (time == Long.MIN_VALUE)
		{
			return;
		}
		corpus.datedRows++;
		corpus.minTime = Math.min(corpus.minTime, time);
		corpus.maxTime = Math.max(corpus.maxTime, time);
		profile.firstTime = Math.min(profile.firstTime, time);
		profile.latestTime = Math.max(profile.latestTime, time);
		List<Long> list = times.get(profile);
		if (list == null)
		{
			list = new ArrayList<Long>();
			times.put(profile, list);
		}
		list.add(Long.valueOf(time));
	}

	// =============================================================== signals

	/** Postings of the user-term bipartite graph for mass diffusion. */
	private static final class DiffusionIndex
	{
		private final HashMap<String,int[]> postingsUsers = new HashMap<String,int[]>();
		private final HashMap<String,double[]> postingsWeights = new HashMap<String,double[]>();
		private final HashMap<String,Double> termTotals = new HashMap<String,Double>();
		private final double[] userTotals;

		DiffusionIndex(List<UserProfile> users)
		{
			userTotals = new double[users.size()];
			TreeMap<String,List<double[]>> postings = new TreeMap<String,List<double[]>>();
			for (UserProfile user : users)
			{
				for (Map.Entry<String,Double> entry : user.tfidf.entrySet())
				{
					double weight = finite(entry.getValue());
					if (weight <= 0.0)
					{
						continue;
					}
					userTotals[user.index] += weight;
					List<double[]> list = postings.get(entry.getKey());
					if (list == null)
					{
						list = new ArrayList<double[]>();
						postings.put(entry.getKey(), list);
					}
					list.add(new double[] {user.index, weight});
				}
			}
			for (Map.Entry<String,List<double[]>> entry : postings.entrySet())
			{
				int[] userIds = new int[entry.getValue().size()];
				double[] weights = new double[userIds.length];
				double total = 0.0;
				for (int i = 0; i < userIds.length; i++)
				{
					userIds[i] = (int)entry.getValue().get(i)[0];
					weights[i] = entry.getValue().get(i)[1];
					total += weights[i];
				}
				postingsUsers.put(entry.getKey(), userIds);
				postingsWeights.put(entry.getKey(), weights);
				termTotals.put(entry.getKey(), Double.valueOf(total));
			}
		}

		/** Resource each user receives from {@code source}, normalised by the best other user. */
		double[] spread(UserProfile source, int userCount, String excludedTerm)
		{
			double[] received = new double[userCount];
			double total = userTotals[source.index];
			if (total <= 0.0)
			{
				return received;
			}
			for (Map.Entry<String,Double> entry : source.tfidf.entrySet())
			{
				double weight = finite(entry.getValue());
				if (weight <= 0.0 || entry.getKey().equals(excludedTerm))
				{
					continue;
				}
				int[] userIds = postingsUsers.get(entry.getKey());
				double[] weights = postingsWeights.get(entry.getKey());
				double termTotal = termTotals.get(entry.getKey()).doubleValue();
				double share = weight / total;
				for (int i = 0; i < userIds.length; i++)
				{
					received[userIds[i]] += share * weights[i] / termTotal;
				}
			}
			received[source.index] = 0.0;
			double max = 0.0;
			for (double value : received)
			{
				max = Math.max(max, value);
			}
			if (max > 0.0)
			{
				for (int i = 0; i < received.length; i++)
				{
					received[i] /= max;
				}
			}
			return received;
		}
	}

	private static double coActivity(double[] first, double[] second)
	{
		if (first == null || second == null)
		{
			return 0.0;
		}
		double dot = 0.0;
		double a = 0.0;
		double b = 0.0;
		for (int i = 0; i < first.length; i++)
		{
			dot += first[i] * second[i];
			a += first[i] * first[i];
			b += second[i] * second[i];
		}
		return a <= 0.0 || b <= 0.0 ? 0.0 : clamp01(dot / Math.sqrt(a * b));
	}

	/** The share of a histogram up to (and including) {@code bins} bins. */
	private static double[] prefix(double[] histogram, int bins)
	{
		if (histogram == null)
		{
			return null;
		}
		double[] cut = new double[histogram.length];
		for (int i = 0; i < Math.min(bins, histogram.length); i++)
		{
			cut[i] = histogram[i];
		}
		return cut;
	}

	// ================================================================ fusion

	/** The fitted logistic fusion and its bootstrap refits. */
	private static final class FusionModel
	{
		/** Coefficients on standardised features: intercept, text, graph, time. */
		private final double[] coefficients;
		private final double[][] bootstrap;
		private final double[] means;
		private final double[] scales;
		private final boolean[] active;
		private final boolean supervised;
		private final int trainingPairs;
		private final int anchors;
		private final double heldOutAuc;
		private final double baseRate;
		private final String note;

		private FusionModel(double[] coefficients, double[][] bootstrap, double[] means, double[] scales,
				boolean[] active, boolean supervised, int trainingPairs, int anchors, double heldOutAuc,
				double baseRate, String note)
		{
			this.coefficients = coefficients;
			this.bootstrap = bootstrap;
			this.means = means;
			this.scales = scales;
			this.active = active;
			this.supervised = supervised;
			this.trainingPairs = trainingPairs;
			this.anchors = anchors;
			this.heldOutAuc = heldOutAuc;
			this.baseRate = baseRate;
			this.note = note;
		}

		/** Features of the target against every user: [index][text, graph, time]. */
		double[][] features(UserProfile target, List<UserProfile> users, DiffusionIndex diffusion,
				TemporalCorpus corpus)
		{
			double[] graph = diffusion.spread(target, users.size(), null);
			double[][] features = new double[users.size()][3];
			for (UserProfile candidate : users)
			{
				if (candidate == target)
				{
					continue;
				}
				features[candidate.index][TEXT] = cosine(target.tfidf, candidate.tfidf);
				features[candidate.index][GRAPH] = graph[candidate.index];
				features[candidate.index][TIME] = corpus.available
						? coActivity(target.activity, candidate.activity) : 0.0;
			}
			return features;
		}

		double probability(double[] x)
		{
			return probability(coefficients, x);
		}

		double probability(double[] beta, double[] x)
		{
			double z = beta[0];
			for (int signal = 0; signal < 3; signal++)
			{
				if (active[signal])
				{
					z += beta[signal + 1] * (x[signal] - means[signal]) / scales[signal];
				}
			}
			return sigmoid(z);
		}

		/** The additive contribution of one signal to the log-odds (relative to the mean pair). */
		double contribution(int signal, double value)
		{
			return active[signal] ? coefficients[signal + 1] * (value - means[signal]) / scales[signal] : 0.0;
		}

		/** The probability with some signals replaced by their mean (removed). */
		double probabilityWithout(double[] x, boolean[] removed)
		{
			double[] copy = x.clone();
			for (int signal = 0; signal < 3; signal++)
			{
				if (removed[signal])
				{
					copy[signal] = means[signal];
				}
			}
			return probability(copy);
		}

		String weights()
		{
			StringBuilder text = new StringBuilder();
			for (int signal = 0; signal < 3; signal++)
			{
				if (text.length() > 0)
				{
					text.append(", ");
				}
				text.append(SIGNALS[signal]).append('=');
				text.append(active[signal] ? String.format(Locale.US, "%+.3f", coefficients[signal + 1]) : "off");
			}
			return text.toString();
		}

		String describe()
		{
			return note;
		}

		static FusionModel fit(List<UserProfile> users, Set<String> targets, DiffusionIndex diffusion,
				TemporalCorpus corpus)
		{
			boolean[] active = {true, true, corpus.available};
			//Anchors: labelled users that are not targets, in a seeded order.
			ArrayList<UserProfile> labelled = new ArrayList<UserProfile>();
			TreeMap<String,Integer> classSizes = new TreeMap<String,Integer>();
			for (UserProfile user : users)
			{
				if (user.followee.length() > 0 && !targets.contains(user.name))
				{
					labelled.add(user);
					Integer size = classSizes.get(user.followee);
					classSizes.put(user.followee, Integer.valueOf(size == null ? 1 : size.intValue() + 1));
				}
			}
			double baseRate = baseRate(classSizes, labelled.size());
			if (classSizes.size() < 2 || labelled.size() < 4)
			{
				return unsupervised(active, "Fewer than two labelled communities outside the targets: the"
						+ " signals are averaged with equal weights (no learned fusion).");
			}
			ArrayList<UserProfile> anchors = new ArrayList<UserProfile>(labelled);
			Collections.shuffle(anchors, new Random(SEED));
			if (anchors.size() > MAX_ANCHORS)
			{
				anchors = new ArrayList<UserProfile>(anchors.subList(0, MAX_ANCHORS));
			}
			//Pairs per anchor: up to PAIRS_PER_CLASS same-community users and as
			//many others, both drawn without replacement from labelled non-targets.
			ArrayList<double[]> rows = new ArrayList<double[]>();
			ArrayList<Integer> rowAnchor = new ArrayList<Integer>();
			Random random = new Random(SEED + 1);
			for (int a = 0; a < anchors.size(); a++)
			{
				UserProfile anchor = anchors.get(a);
				ArrayList<UserProfile> positives = new ArrayList<UserProfile>();
				ArrayList<UserProfile> negatives = new ArrayList<UserProfile>();
				for (UserProfile other : labelled)
				{
					if (other == anchor)
					{
						continue;
					}
					(other.followee.equals(anchor.followee) ? positives : negatives).add(other);
				}
				if (positives.isEmpty() || negatives.isEmpty())
				{
					continue;
				}
				Collections.shuffle(positives, random);
				Collections.shuffle(negatives, random);
				int count = Math.min(PAIRS_PER_CLASS, Math.min(positives.size(), negatives.size()));
				double[] graph = diffusion.spread(anchor, users.size(), null);
				for (int i = 0; i < count; i++)
				{
					rows.add(pair(anchor, positives.get(i), graph, corpus, 1.0));
					rowAnchor.add(Integer.valueOf(a));
					rows.add(pair(anchor, negatives.get(i), graph, corpus, 0.0));
					rowAnchor.add(Integer.valueOf(a));
				}
			}
			if (rows.size() < 8)
			{
				return unsupervised(active, "Too few labelled pairs outside the targets to learn the fusion:"
						+ " the signals are averaged with equal weights.");
			}
			double[] means = new double[3];
			double[] scales = new double[3];
			for (int signal = 0; signal < 3; signal++)
			{
				double sum = 0.0;
				for (double[] row : rows)
				{
					sum += row[signal];
				}
				means[signal] = sum / rows.size();
				double squares = 0.0;
				for (double[] row : rows)
				{
					squares += (row[signal] - means[signal]) * (row[signal] - means[signal]);
				}
				scales[signal] = Math.sqrt(squares / rows.size());
				if (!(scales[signal] > 1e-12))
				{
					active[signal] = false;
					scales[signal] = 1.0;
				}
			}
			int anchorCount = anchors.size();
			//Held-out quality: fit on 80% of the anchors, AUC on the other 20%.
			boolean[] heldOut = new boolean[anchorCount];
			Integer[] order = new Integer[anchorCount];
			for (int i = 0; i < anchorCount; i++)
			{
				order[i] = Integer.valueOf(i);
			}
			Collections.shuffle(Arrays.asList(order), new Random(SEED + 2));
			for (int i = 0; i < anchorCount / 5; i++)
			{
				heldOut[order[i].intValue()] = true;
			}
			ArrayList<double[]> fitRows = new ArrayList<double[]>();
			ArrayList<double[]> testRows = new ArrayList<double[]>();
			for (int i = 0; i < rows.size(); i++)
			{
				(heldOut[rowAnchor.get(i).intValue()] ? testRows : fitRows).add(rows.get(i));
			}
			double heldOutAuc = Double.NaN;
			if (!fitRows.isEmpty() && !testRows.isEmpty())
			{
				double[] partial = irls(fitRows, means, scales, active);
				heldOutAuc = auc(testRows, partial, means, scales, active);
			}
			double[] coefficients = irls(rows, means, scales, active);
			//Bootstrap over anchors (clusters of pairs).
			double[][] bootstrap = new double[BOOTSTRAP_FITS][];
			Random resample = new Random(SEED + 3);
			ArrayList<List<double[]>> byAnchor = new ArrayList<List<double[]>>();
			for (int a = 0; a < anchorCount; a++)
			{
				byAnchor.add(new ArrayList<double[]>());
			}
			for (int i = 0; i < rows.size(); i++)
			{
				byAnchor.get(rowAnchor.get(i).intValue()).add(rows.get(i));
			}
			for (int b = 0; b < BOOTSTRAP_FITS; b++)
			{
				ArrayList<double[]> sample = new ArrayList<double[]>();
				for (int draw = 0; draw < anchorCount; draw++)
				{
					sample.addAll(byAnchor.get(resample.nextInt(anchorCount)));
				}
				bootstrap[b] = irls(sample.isEmpty() ? rows : sample, means, scales, active);
			}
			//From the balanced sample to the population base rate of same-community pairs.
			double correction = Math.log(baseRate / (1.0 - baseRate));
			coefficients[0] += correction;
			for (double[] beta : bootstrap)
			{
				beta[0] += correction;
			}
			FusionModel model = new FusionModel(coefficients, bootstrap, means, scales, active, true,
					rows.size(), anchorCount, heldOutAuc, baseRate, "");
			String note = "Learned fusion (logistic regression, " + rows.size() + " labelled pairs from "
					+ anchorCount + " non-target anchors; standardised weights " + model.weights()
					+ "; held-out pair AUC " + (Double.isNaN(heldOutAuc) ? "n/a"
							: String.format(Locale.US, "%.3f", heldOutAuc))
					+ "; base rate " + String.format(Locale.US, "%.3f", baseRate)
					+ (corpus.available ? "" : "; temporal signal off: " + corpus.warning) + ").";
			return new FusionModel(coefficients, bootstrap, means, scales, active, true, rows.size(),
					anchorCount, heldOutAuc, baseRate, note);
		}

		private static FusionModel unsupervised(boolean[] active, String note)
		{
			int signals = 0;
			for (boolean on : active)
			{
				signals += on ? 1 : 0;
			}
			double[] coefficients = new double[4];
			double[] means = new double[3];
			double[] scales = {1.0, 1.0, 1.0};
			//With equal unit weights on raw signals the score is a monotone
			//transform of their mean; the intercept centres it on 0.5.
			for (int signal = 0; signal < 3; signal++)
			{
				coefficients[signal + 1] = active[signal] ? 4.0 / Math.max(1, signals) : 0.0;
			}
			double[][] bootstrap = new double[BOOTSTRAP_FITS][];
			for (int b = 0; b < BOOTSTRAP_FITS; b++)
			{
				bootstrap[b] = coefficients.clone();
			}
			return new FusionModel(coefficients, bootstrap, means, scales, active, false, 0, 0, Double.NaN,
					Double.NaN, "Unsupervised fusion: " + note);
		}

		private static double[] pair(UserProfile anchor, UserProfile other, double[] graph,
				TemporalCorpus corpus, double label)
		{
			return new double[] {
					cosine(anchor.tfidf, other.tfidf),
					graph[other.index],
					corpus.available ? coActivity(anchor.activity, other.activity) : 0.0,
					label};
		}

		/** Probability that two random distinct labelled users share a community. */
		private static double baseRate(Map<String,Integer> classSizes, int users)
		{
			if (users < 2)
			{
				return 0.5;
			}
			double same = 0.0;
			for (Integer size : classSizes.values())
			{
				same += (double)size.intValue() * (size.intValue() - 1);
			}
			double rate = same / ((double)users * (users - 1));
			return Math.max(1e-6, Math.min(1.0 - 1e-6, rate));
		}

		/**
		 * L2-regularised logistic regression by Newton's method (IRLS) on
		 * standardised features; the intercept is not penalised.
		 */
		private static double[] irls(List<double[]> rows, double[] means, double[] scales, boolean[] active)
		{
			int dimension = 4;
			double[] beta = new double[dimension];
			for (int iteration = 0; iteration < 50; iteration++)
			{
				double[] gradient = new double[dimension];
				double[][] hessian = new double[dimension][dimension];
				for (double[] row : rows)
				{
					double[] x = design(row, means, scales, active);
					double z = 0.0;
					for (int j = 0; j < dimension; j++)
					{
						z += beta[j] * x[j];
					}
					double p = sigmoid(z);
					double w = Math.max(p * (1.0 - p), 1e-10);
					for (int j = 0; j < dimension; j++)
					{
						gradient[j] += (row[3] - p) * x[j];
						for (int k = 0; k < dimension; k++)
						{
							hessian[j][k] += w * x[j] * x[k];
						}
					}
				}
				for (int j = 1; j < dimension; j++)
				{
					gradient[j] -= L2_PENALTY * beta[j];
					hessian[j][j] += L2_PENALTY;
					if (!active[j - 1])
					{
						//Inactive signals stay at zero.
						Arrays.fill(hessian[j], 0.0);
						for (int k = 0; k < dimension; k++)
						{
							hessian[k][j] = 0.0;
						}
						hessian[j][j] = 1.0;
						gradient[j] = -beta[j];
					}
				}
				double[] step = solve(hessian, gradient);
				double change = 0.0;
				for (int j = 0; j < dimension; j++)
				{
					beta[j] += step[j];
					change = Math.max(change, Math.abs(step[j]));
				}
				if (change < 1e-10)
				{
					break;
				}
			}
			return beta;
		}

		private static double[] design(double[] row, double[] means, double[] scales, boolean[] active)
		{
			return new double[] {
					1.0,
					active[0] ? (row[0] - means[0]) / scales[0] : 0.0,
					active[1] ? (row[1] - means[1]) / scales[1] : 0.0,
					active[2] ? (row[2] - means[2]) / scales[2] : 0.0};
		}

		/** Area under the ROC curve (Mann-Whitney, ties counted half). */
		private static double auc(List<double[]> rows, double[] beta, double[] means, double[] scales,
				boolean[] active)
		{
			ArrayList<Double> positives = new ArrayList<Double>();
			ArrayList<Double> negatives = new ArrayList<Double>();
			for (double[] row : rows)
			{
				double[] x = design(row, means, scales, active);
				double z = 0.0;
				for (int j = 0; j < 4; j++)
				{
					z += beta[j] * x[j];
				}
				(row[3] > 0.5 ? positives : negatives).add(Double.valueOf(z));
			}
			if (positives.isEmpty() || negatives.isEmpty())
			{
				return Double.NaN;
			}
			double wins = 0.0;
			for (Double positive : positives)
			{
				for (Double negative : negatives)
				{
					int compare = Double.compare(positive, negative);
					wins += compare > 0 ? 1.0 : compare == 0 ? 0.5 : 0.0;
				}
			}
			return wins / ((double)positives.size() * negatives.size());
		}

		/** Gaussian elimination with partial pivoting on a small system. */
		private static double[] solve(double[][] matrix, double[] vector)
		{
			int n = vector.length;
			double[][] a = new double[n][n + 1];
			for (int i = 0; i < n; i++)
			{
				System.arraycopy(matrix[i], 0, a[i], 0, n);
				a[i][n] = vector[i];
			}
			for (int column = 0; column < n; column++)
			{
				int pivot = column;
				for (int row = column + 1; row < n; row++)
				{
					if (Math.abs(a[row][column]) > Math.abs(a[pivot][column]))
					{
						pivot = row;
					}
				}
				double[] swap = a[column];
				a[column] = a[pivot];
				a[pivot] = swap;
				double divisor = a[column][column];
				if (Math.abs(divisor) < 1e-15)
				{
					continue;
				}
				for (int row = 0; row < n; row++)
				{
					if (row == column)
					{
						continue;
					}
					double factor = a[row][column] / divisor;
					for (int k = column; k <= n; k++)
					{
						a[row][k] -= factor * a[column][k];
					}
				}
			}
			double[] result = new double[n];
			for (int i = 0; i < n; i++)
			{
				result[i] = Math.abs(a[i][i]) < 1e-15 ? 0.0 : a[i][n] / a[i][i];
			}
			return result;
		}
	}

	private static double sigmoid(double z)
	{
		if (z >= 0)
		{
			double e = Math.exp(-z);
			return 1.0 / (1.0 + e);
		}
		double e = Math.exp(z);
		return e / (1.0 + e);
	}

	// ============================================================ explanation

	/**
	 * Fills the kept candidates' evidence: additive contributions, bootstrap
	 * interval and rank stability, counterfactual ranks, the timeline and the
	 * plain-language summaries. Ranks under counterfactuals are among all
	 * candidates.
	 */
	private static void explain(UserProfile target, List<CandidateScore> kept, List<CandidateScore> all,
			Prepared prepared)
	{
		FusionModel model = prepared.model;
		//Ranks of every candidate under each bootstrap refit.
		int n = all.size();
		double[][] features = new double[n][];
		for (int i = 0; i < n; i++)
		{
			CandidateScore score = all.get(i);
			features[i] = new double[] {score.text, score.graph, score.time};
		}
		HashMap<CandidateScore,Integer> inTop = new HashMap<CandidateScore,Integer>();
		HashMap<CandidateScore,double[]> samples = new HashMap<CandidateScore,double[]>();
		java.util.IdentityHashMap<CandidateScore,Integer> positions = new java.util.IdentityHashMap<CandidateScore,Integer>();
		for (int i = 0; i < n; i++)
		{
			positions.put(all.get(i), Integer.valueOf(i));
		}
		for (CandidateScore score : kept)
		{
			inTop.put(score, Integer.valueOf(0));
			samples.put(score, new double[model.bootstrap.length]);
		}
		for (int b = 0; b < model.bootstrap.length; b++)
		{
			final double[] probabilities = new double[n];
			Integer[] order = new Integer[n];
			for (int i = 0; i < n; i++)
			{
				probabilities[i] = model.probability(model.bootstrap[b], features[i]);
				order[i] = Integer.valueOf(i);
			}
			final List<CandidateScore> candidates = all;
			Arrays.sort(order, new Comparator<Integer>()
			{
				public int compare(Integer first, Integer second)
				{
					int byScore = Double.compare(probabilities[second.intValue()], probabilities[first.intValue()]);
					return byScore != 0 ? byScore : candidates.get(first.intValue()).candidate.name
							.compareTo(candidates.get(second.intValue()).candidate.name);
				}
			});
			for (int position = 0; position < Math.min(STABILITY_TOP, n); position++)
			{
				CandidateScore score = all.get(order[position].intValue());
				Integer count = inTop.get(score);
				if (count != null)
				{
					inTop.put(score, Integer.valueOf(count.intValue() + 1));
				}
			}
			for (CandidateScore score : kept)
			{
				samples.get(score)[b] = probabilities[positions.get(score).intValue()];
			}
		}
		//Counterfactual ranks: each signal removed (set to its mean), and the top shared term removed.
		int[][] counterfactualRanks = new int[4][];
		double[][] counterfactualScores = new double[4][n];
		for (int signal = 0; signal < 3; signal++)
		{
			boolean[] removed = new boolean[3];
			removed[signal] = true;
			for (int i = 0; i < n; i++)
			{
				counterfactualScores[signal][i] = model.probabilityWithout(features[i], removed);
			}
			counterfactualRanks[signal] = ranks(all, counterfactualScores[signal]);
		}
		for (CandidateScore score : kept)
		{
			score.sharedTerms = sharedTerms(target.tfidf, score.candidate.tfidf, DETAIL_TERM_LIMIT);
		}
		String topTerm = kept.isEmpty() || kept.get(0).sharedTerms.isEmpty() ? null : kept.get(0).sharedTerms.get(0);
		double[] graphWithoutTerm = prepared.diffusion.spread(target, prepared.users.size(), topTerm);
		for (int i = 0; i < n; i++)
		{
			CandidateScore score = all.get(i);
			double[] x = features[i].clone();
			if (topTerm != null)
			{
				x[TEXT] = cosineExcluding(target.tfidf, score.candidate.tfidf, topTerm);
				x[GRAPH] = graphWithoutTerm[score.candidate.index];
			}
			counterfactualScores[3][i] = model.probability(x);
			score.withoutTopTermText = x[TEXT];
		}
		counterfactualRanks[3] = ranks(all, counterfactualScores[3]);
		for (int i = 0; i < n; i++)
		{
			CandidateScore score = all.get(i);
			score.withoutTextScore = counterfactualScores[TEXT][i];
			score.withoutGraphScore = counterfactualScores[GRAPH][i];
			score.withoutTimeScore = counterfactualScores[TIME][i];
			score.withoutTopTermScore = counterfactualScores[3][i];
			score.withoutBridgeScore = score.finalScore;
			score.withoutDebiasScore = score.finalScore;
			score.withoutTextRank = counterfactualRanks[TEXT][i];
			score.withoutGraphRank = counterfactualRanks[GRAPH][i];
			score.withoutTimeRank = counterfactualRanks[TIME][i];
			score.withoutTopTermRank = counterfactualRanks[3][i];
			score.withoutBridgeRank = score.rank;
			score.withoutDebiasRank = score.rank;
		}
		for (int k = 0; k < kept.size(); k++)
		{
			CandidateScore score = kept.get(k);
			score.textContribution = model.contribution(TEXT, score.text);
			score.graphContribution = model.contribution(GRAPH, score.graph);
			score.timeContribution = model.contribution(TIME, score.time);
			score.model = model;
			double[] draws = samples.get(score);
			double[] sorted = draws.clone();
			Arrays.sort(sorted);
			score.confidenceLower = percentile(sorted, 0.05);
			score.confidenceUpper = percentile(sorted, 0.95);
			score.confidenceRadius = (score.confidenceUpper - score.confidenceLower) / 2.0;
			score.stability = inTop.get(score).intValue() / (double)Math.max(1, model.bootstrap.length);
			score.calibrationPValue = score.stability;
			score.calibratedConfidence = score.finalScore;
			score.marginToNext = k + 1 < all.size() ? Math.max(0.0, score.finalScore - all.get(k + 1).finalScore) : 0.0;
			score.evidenceCoverage = coverage(score, prepared.corpus.available);
			score.path = graphPath(score);
			score.counterfactuals = counterfactuals(score, topTerm);
			score.counterfactualFragility = fragility(score);
			score.evidenceAtoms = atoms(score, prepared.corpus.available);
			score.timeline = timeline(target, score, model, prepared.corpus);
			CandidateScore challenger = k == 0 ? (all.size() > 1 ? all.get(1) : null) : all.get(0);
			attachChallenger(score, challenger, model);
			score.riskFlags = riskFlags(score, prepared.corpus, model);
			score.reliabilityBand = score.stability >= 0.8 ? "stable" : score.stability >= 0.5 ? "reviewable" : "unstable";
			score.calibrationVerdict = "in the top " + STABILITY_TOP + " under "
					+ pct(score.stability) + " of " + model.bootstrap.length + " bootstrap refits";
			score.calibrationNote = "90% bootstrap interval of the probability: "
					+ fmt(score.confidenceLower) + " to " + fmt(score.confidenceUpper)
					+ (model.supervised ? "" : " (unsupervised fusion: no sampling uncertainty)");
			score.whyThis = whyThis(score);
			score.whyNotChallenger = whyNotChallenger(score);
			score.researchVerdict = score.reliabilityBand + " recommendation, led by " + leadingSignal(score);
		}
		assignTimelineRanks(kept);
	}

	private static int[] ranks(List<CandidateScore> all, final double[] scores)
	{
		int n = all.size();
		Integer[] order = new Integer[n];
		for (int i = 0; i < n; i++)
		{
			order[i] = Integer.valueOf(i);
		}
		final List<CandidateScore> candidates = all;
		Arrays.sort(order, new Comparator<Integer>()
		{
			public int compare(Integer first, Integer second)
			{
				int byScore = Double.compare(scores[second.intValue()], scores[first.intValue()]);
				return byScore != 0 ? byScore : candidates.get(first.intValue()).candidate.name
						.compareTo(candidates.get(second.intValue()).candidate.name);
			}
		});
		int[] ranks = new int[n];
		for (int position = 0; position < n; position++)
		{
			ranks[order[position].intValue()] = position + 1;
		}
		return ranks;
	}

	private static double percentile(double[] sorted, double quantile)
	{
		if (sorted.length == 0)
		{
			return 0.0;
		}
		double position = quantile * (sorted.length - 1);
		int low = (int)Math.floor(position);
		int high = (int)Math.ceil(position);
		return sorted[low] + (sorted[high] - sorted[low]) * (position - low);
	}

	private static double coverage(CandidateScore score, boolean timeAvailable)
	{
		int available = timeAvailable ? 3 : 2;
		int present = (score.text > 0.0 ? 1 : 0) + (score.graph > 0.0 ? 1 : 0)
				+ (timeAvailable && score.time > 0.0 ? 1 : 0);
		return present / (double)available;
	}

	private static String graphPath(CandidateScore score)
	{
		if (score.graph <= 0.0)
		{
			return "no shared terms in the user-term graph";
		}
		return score.target.name + " -> " + (score.sharedTerms.isEmpty() ? "shared terms"
				: joinTerms(score.sharedTerms)) + " -> " + score.candidate.name
				+ " (diffusion " + pct(score.graph) + " of the target's best)";
	}

	private static List<CounterfactualProbe> counterfactuals(CandidateScore score, String topTerm)
	{
		ArrayList<CounterfactualProbe> probes = new ArrayList<CounterfactualProbe>();
		probes.add(CounterfactualProbe.from("Remove text", score.withoutTextScore, score.withoutTextRank,
				score.finalScore, score.rank));
		probes.add(CounterfactualProbe.from("Remove graph", score.withoutGraphScore, score.withoutGraphRank,
				score.finalScore, score.rank));
		probes.add(CounterfactualProbe.from("Remove time", score.withoutTimeScore, score.withoutTimeRank,
				score.finalScore, score.rank));
		probes.add(CounterfactualProbe.from(topTerm == null ? "Remove top term"
				: "Remove '" + topTerm + "' from the target",
				score.withoutTopTermScore, score.withoutTopTermRank, score.finalScore, score.rank));
		return Collections.unmodifiableList(probes);
	}

	/** The largest relative probability drop under any single counterfactual. */
	private static double fragility(CandidateScore score)
	{
		if (score.finalScore <= 0.0)
		{
			return 0.0;
		}
		double worst = 0.0;
		for (CounterfactualProbe probe : score.counterfactuals)
		{
			worst = Math.max(worst, probe.scoreDrop / score.finalScore);
		}
		return clamp01(worst);
	}

	private static List<EvidenceAtom> atoms(CandidateScore score, boolean timeAvailable)
	{
		ArrayList<EvidenceAtom> atoms = new ArrayList<EvidenceAtom>();
		atoms.add(new EvidenceAtom("Text similarity", score.text, score.textContribution,
				"TF-IDF cosine; shared terms: " + joinTerms(score.sharedTerms)));
		atoms.add(new EvidenceAtom("Graph diffusion", score.graph, score.graphContribution, score.path));
		atoms.add(new EvidenceAtom("Temporal co-activity", score.time, score.timeContribution,
				timeAvailable ? "cosine of the two users' activity histograms over the dated span"
						: "unavailable: the corpus has fewer than two distinct timestamps"));
		return Collections.unmodifiableList(atoms);
	}

	/**
	 * The probability recomputed with the temporal signal measured on activity
	 * up to each frame's date (text and graph are this run's values).
	 */
	private static List<TimelineFrame> timeline(UserProfile target, CandidateScore score, FusionModel model,
			TemporalCorpus corpus)
	{
		ArrayList<TimelineFrame> frames = new ArrayList<TimelineFrame>();
		if (!corpus.available)
		{
			return Collections.unmodifiableList(frames);
		}
		double previous = 0.0;
		for (int frame = 0; frame < TIMELINE_FRAME_COUNT; frame++)
		{
			double progress = (frame + 1) / (double)TIMELINE_FRAME_COUNT;
			int bins = (int)Math.round(progress * ACTIVITY_BINS);
			double time = coActivity(prefix(target.activity, bins), prefix(score.candidate.activity, bins));
			double probability = model.probability(new double[] {score.text, score.graph, time});
			long cutoff = corpus.minTime + Math.round(progress * (corpus.maxTime - corpus.minTime));
			String label = date(cutoff);
			String evidence = "co-activity up to " + label + ": " + pct(time);
			frames.add(new TimelineFrame(frame, progress, label, probability, score.text, score.graph, time,
					0.0, 0.0, 0.0, evidence, frame == 0 ? probability : probability - previous,
					strongest(model, score.text, score.graph, time),
					"probability " + fmt(probability) + " with activity observed up to " + label + "."));
			previous = probability;
		}
		return Collections.unmodifiableList(frames);
	}

	private static String strongest(FusionModel model, double text, double graph, double time)
	{
		double[] contributions = {
				model.contribution(TEXT, text), model.contribution(GRAPH, graph), model.contribution(TIME, time)};
		int best = 0;
		for (int i = 1; i < 3; i++)
		{
			if (contributions[i] > contributions[best])
			{
				best = i;
			}
		}
		return SIGNALS[best];
	}

	private static void assignTimelineRanks(List<CandidateScore> scores)
	{
		if (scores.isEmpty() || scores.get(0).timeline.isEmpty())
		{
			return;
		}
		int frames = scores.get(0).timeline.size();
		for (int frame = 0; frame < frames; frame++)
		{
			final int index = frame;
			ArrayList<CandidateScore> ordered = new ArrayList<CandidateScore>(scores);
			Collections.sort(ordered, new Comparator<CandidateScore>()
			{
				public int compare(CandidateScore first, CandidateScore second)
				{
					return CandidateScore.compareScore(first, second,
							first.timeline.get(index).score, second.timeline.get(index).score);
				}
			});
			for (int i = 0; i < ordered.size(); i++)
			{
				TimelineFrame current = ordered.get(i).timeline.get(frame);
				current.rank = i + 1;
				current.previousRank = frame == 0 ? current.rank : ordered.get(i).timeline.get(frame - 1).rank;
			}
		}
	}

	private static void attachChallenger(CandidateScore score, CandidateScore challenger, FusionModel model)
	{
		if (challenger == null)
		{
			return;
		}
		score.challengerName = challenger.candidate.name;
		score.challengerFollowee = challenger.candidate.followee;
		score.challengerScore = challenger.finalScore;
		score.challengerText = challenger.text;
		score.challengerGraph = challenger.graph;
		score.challengerTime = challenger.time;
		score.challengerGap = score.finalScore - challenger.finalScore;
		double[] deltas = {
				model.contribution(TEXT, score.text) - model.contribution(TEXT, challenger.text),
				model.contribution(GRAPH, score.graph) - model.contribution(GRAPH, challenger.graph),
				model.contribution(TIME, score.time) - model.contribution(TIME, challenger.time)};
		int best = 0;
		for (int i = 1; i < 3; i++)
		{
			if (Math.abs(deltas[i]) > Math.abs(deltas[best]))
			{
				best = i;
			}
		}
		score.bestContrastSignal = SIGNALS[best] + " " + String.format(Locale.US, "%+.3f", deltas[best])
				+ " log-odds";
	}

	private static List<String> riskFlags(CandidateScore score, TemporalCorpus corpus, FusionModel model)
	{
		ArrayList<String> flags = new ArrayList<String>();
		if (score.rank == 1 && score.marginToNext < score.confidenceRadius)
		{
			flags.add("the lead over the next candidate is inside the bootstrap interval");
		}
		if (score.stability < 0.5)
		{
			flags.add("in the top " + STABILITY_TOP + " under fewer than half of the bootstrap refits");
		}
		if (!corpus.available)
		{
			flags.add("temporal signal unavailable");
		}
		if (!model.supervised)
		{
			flags.add("unsupervised fusion (no labelled pairs to learn from)");
		}
		double total = Math.abs(score.textContribution) + Math.abs(score.graphContribution)
				+ Math.abs(score.timeContribution);
		double largest = Math.max(Math.abs(score.textContribution),
				Math.max(Math.abs(score.graphContribution), Math.abs(score.timeContribution)));
		if (total > 0.0 && largest / total > 0.85)
		{
			flags.add("one signal carries over 85% of the evidence");
		}
		if (flags.isEmpty())
		{
			flags.add("no evidence-limit flags");
		}
		return Collections.unmodifiableList(flags);
	}

	private static String leadingSignal(CandidateScore score)
	{
		double[] contributions = {score.textContribution, score.graphContribution, score.timeContribution};
		int best = 0;
		for (int i = 1; i < 3; i++)
		{
			if (contributions[i] > contributions[best])
			{
				best = i;
			}
		}
		return SIGNALS[best];
	}

	private static String whyThis(CandidateScore score)
	{
		return "Rank " + rankText(score.rank) + ": probability " + fmt(score.finalScore)
				+ " (90% bootstrap " + fmt(score.confidenceLower) + "-" + fmt(score.confidenceUpper)
				+ ") of sharing the target's community, led by " + leadingSignal(score)
				+ "; log-odds contributions text " + signedFmt(score.textContribution)
				+ ", graph " + signedFmt(score.graphContribution)
				+ ", time " + signedFmt(score.timeContribution) + ".";
	}

	private static String whyNotChallenger(CandidateScore score)
	{
		if (score.challengerName == null || score.challengerName.length() == 0)
		{
			return "No other candidate to contrast.";
		}
		return score.candidate.name + (score.challengerGap >= 0.0 ? " leads " : " trails ")
				+ score.challengerName + " by " + String.format(Locale.US, "%.4f", Math.abs(score.challengerGap))
				+ "; largest difference: " + score.bestContrastSignal + ".";
	}

	private static String detailFor(CandidateScore score)
	{
		return "TGF probability " + fmt(score.finalScore)
				+ " (text " + pct(score.text) + ", graph " + pct(score.graph) + ", time " + pct(score.time)
				+ "); " + score.calibrationVerdict + "; shared " + joinTerms(score.sharedTerms);
	}

	// ============================================================= evaluation

	/**
	 * Ablation lenses over all candidates: the fusion and each signal alone,
	 * graded against the target's followee (Hit@1, Hit@3, MRR, NDCG@5).
	 */
	private static List<BenchmarkRow> benchmarkRows(UserProfile target, List<CandidateScore> all,
			boolean timeAvailable)
	{
		ArrayList<BenchmarkRow> rows = new ArrayList<BenchmarkRow>();
		if (all.isEmpty() || target.followee.length() == 0)
		{
			return rows;
		}
		rows.add(lens("TGF fusion", "learned fusion of text, graph and time", target, all, -1));
		rows.add(lens("Text only", "TF-IDF cosine (the Similarity algorithm)", target, all, TEXT));
		rows.add(lens("Graph only", "mass diffusion on the user-term graph", target, all, GRAPH));
		if (timeAvailable)
		{
			rows.add(lens("Time only", "temporal co-activity", target, all, TIME));
		}
		return Collections.unmodifiableList(rows);
	}

	private static BenchmarkRow lens(String label, String note, UserProfile target, List<CandidateScore> all,
			final int signal)
	{
		ArrayList<CandidateScore> ordered = new ArrayList<CandidateScore>(all);
		Collections.sort(ordered, new Comparator<CandidateScore>()
		{
			public int compare(CandidateScore first, CandidateScore second)
			{
				return CandidateScore.compareScore(first, second, value(first, signal), value(second, signal));
			}
		});
		int relevant = 0;
		for (CandidateScore score : all)
		{
			relevant += sameCommunity(target, score.candidate) ? 1 : 0;
		}
		int firstRelevant = 0;
		double dcg = 0.0;
		for (int i = 0; i < ordered.size(); i++)
		{
			if (sameCommunity(target, ordered.get(i).candidate))
			{
				if (firstRelevant == 0)
				{
					firstRelevant = i + 1;
				}
				if (i < 5)
				{
					dcg += 1.0 / (Math.log(i + 2.0) / Math.log(2.0));
				}
			}
		}
		double idcg = 0.0;
		for (int i = 0; i < Math.min(5, relevant); i++)
		{
			idcg += 1.0 / (Math.log(i + 2.0) / Math.log(2.0));
		}
		CandidateScore top = ordered.get(0);
		return new BenchmarkRow(label, top.candidate.name, value(top, signal), firstRelevant,
				firstRelevant == 1 ? 1.0 : 0.0, firstRelevant > 0 && firstRelevant <= 3 ? 1.0 : 0.0,
				firstRelevant > 0 ? 1.0 / firstRelevant : 0.0, idcg > 0.0 ? dcg / idcg : 0.0, note);
	}

	private static double value(CandidateScore score, int signal)
	{
		return signal == TEXT ? score.text : signal == GRAPH ? score.graph : signal == TIME ? score.time
				: score.finalScore;
	}

	/** Grading only: whether a candidate follows the target's followee. */
	private static boolean sameCommunity(UserProfile target, UserProfile candidate)
	{
		return target.followee.length() > 0 && target.followee.equals(candidate.followee);
	}

	static String weightsDescription()
	{
		return "learned per run by logistic regression on labelled non-target pairs (see the run summary)";
	}

	static String methodologySummary()
	{
		return "TGF ranks candidates by the probability that they share the target's community, from a "
				+ "logistic fusion of three label-free signals: TF-IDF cosine, mass diffusion on the user-term "
				+ "graph and temporal co-activity. The fusion is learned on labelled users that are not targets, "
				+ "with bootstrap intervals and rank stability; the target's own followee is used only to grade "
				+ "the result.";
	}

	// ================================================================= helpers

	private static double cosine(Map<String,Double> first, Map<String,Double> second)
	{
		if (first == null || second == null || first.isEmpty() || second.isEmpty())
		{
			return 0.0;
		}
		Map<String,Double> smaller = first.size() <= second.size() ? first : second;
		Map<String,Double> larger = smaller == first ? second : first;
		double dot = 0.0;
		for (Map.Entry<String,Double> entry : smaller.entrySet())
		{
			Double other = larger.get(entry.getKey());
			if (other != null)
			{
				dot += finite(entry.getValue()) * finite(other);
			}
		}
		double norm = vectorNorm(first, null) * vectorNorm(second, null);
		return norm <= 0.0 ? 0.0 : clamp01(dot / norm);
	}

	private static double cosineExcluding(Map<String,Double> first, Map<String,Double> second, String excluded)
	{
		if (first == null || second == null || first.isEmpty() || second.isEmpty())
		{
			return 0.0;
		}
		double dot = 0.0;
		for (Map.Entry<String,Double> entry : first.entrySet())
		{
			if (entry.getKey().equals(excluded))
			{
				continue;
			}
			Double other = second.get(entry.getKey());
			if (other != null)
			{
				dot += finite(entry.getValue()) * finite(other);
			}
		}
		double norm = vectorNorm(first, excluded) * vectorNorm(second, excluded);
		return norm <= 0.0 ? 0.0 : clamp01(dot / norm);
	}

	private static double vectorNorm(Map<String,Double> vector, String excluded)
	{
		double total = 0.0;
		for (Map.Entry<String,Double> entry : vector.entrySet())
		{
			if (entry.getKey().equals(excluded))
			{
				continue;
			}
			double value = finite(entry.getValue());
			total += value * value;
		}
		return Math.sqrt(total);
	}

	/** Shared terms by their contribution to the cosine (product of weights), ties by name. */
	private static List<String> sharedTerms(Map<String,Double> first, Map<String,Double> second, int limit)
	{
		ArrayList<Map.Entry<String,Double>> shared = new ArrayList<Map.Entry<String,Double>>();
		for (Map.Entry<String,Double> entry : first.entrySet())
		{
			Double other = second.get(entry.getKey());
			double product = finite(entry.getValue()) * finite(other);
			if (other != null && product > 0.0)
			{
				shared.add(new java.util.AbstractMap.SimpleImmutableEntry<String,Double>(
						entry.getKey(), Double.valueOf(product)));
			}
		}
		Collections.sort(shared, new Comparator<Map.Entry<String,Double>>()
		{
			public int compare(Map.Entry<String,Double> a, Map.Entry<String,Double> b)
			{
				int byWeight = Double.compare(b.getValue(), a.getValue());
				return byWeight != 0 ? byWeight : a.getKey().compareTo(b.getKey());
			}
		});
		ArrayList<String> terms = new ArrayList<String>();
		for (int i = 0; i < shared.size() && i < limit; i++)
		{
			terms.add(shared.get(i).getKey());
		}
		return terms;
	}

	private static String date(long millis)
	{
		SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
		format.setTimeZone(TimeZone.getTimeZone("UTC"));
		return format.format(new Date(millis));
	}

	private static String rankText(int rank)
	{
		return rank <= 0 ? "n/a" : "#" + rank;
	}

	private static String pct(double value)
	{
		return Integer.toString((int)Math.round(clamp01(value) * 100.0)) + "%";
	}

	private static String fmt(double value)
	{
		return String.format(Locale.US, "%.3f", Double.isFinite(value) ? value : 0.0);
	}

	private static String signedFmt(double value)
	{
		double safe = Double.isFinite(value) ? value : 0.0;
		return String.format(Locale.US, "%+.3f", safe);
	}

	private static String joinTerms(List<String> terms)
	{
		if (terms == null || terms.isEmpty())
		{
			return "no shared terms";
		}
		StringBuilder builder = new StringBuilder();
		for (int i = 0; i < terms.size(); i++)
		{
			if (i > 0)
			{
				builder.append(", ");
			}
			builder.append(terms.get(i));
		}
		return builder.toString();
	}

	private static double finite(Double value)
	{
		return value == null || !Double.isFinite(value) ? 0.0 : value.doubleValue();
	}

	private static double clamp01(double value)
	{
		if (!Double.isFinite(value) || value < 0.0)
		{
			return 0.0;
		}
		return value > 1.0 ? 1.0 : value;
	}

	// ================================================================ records

	static final class Input
	{
		final String runId;
		/** The run corpus (RunCorpus) or a raw dataset file; read for timestamps only. */
		final File datasetFile;
		final Map<String,? extends Map<String,Double>> tfidfDocuments;
		final Map<String,String> userFollowees;
		final Map<String,String> datasetUserFollowees;
		final List<String> targetUsers;
		final Map<String,Integer> userPrimaryRecServer;
		final int currentNode;
		final int numRecAgents;
		final int localResultLimit;

		Input(
				String runId,
				File datasetFile,
				Map<String,? extends Map<String,Double>> tfidfDocuments,
				Map<String,String> userFollowees,
				Map<String,String> datasetUserFollowees,
				Collection<String> targetUsers,
				Map<String,Integer> userPrimaryRecServer,
				int currentNode,
				int numRecAgents,
				int localResultLimit)
		{
			this.runId = runId == null ? "" : runId;
			this.datasetFile = datasetFile;
			this.tfidfDocuments = tfidfDocuments == null
					? Collections.<String,Map<String,Double>>emptyMap() : tfidfDocuments;
			this.userFollowees = userFollowees == null ? Collections.<String,String>emptyMap() : userFollowees;
			this.datasetUserFollowees = datasetUserFollowees == null
					? Collections.<String,String>emptyMap() : datasetUserFollowees;
			this.targetUsers = targetUsers == null ? Collections.<String>emptyList()
					: new ArrayList<String>(targetUsers);
			this.userPrimaryRecServer = userPrimaryRecServer == null
					? Collections.<String,Integer>emptyMap() : userPrimaryRecServer;
			this.currentNode = currentNode;
			this.numRecAgents = Math.max(1, numRecAgents);
			this.localResultLimit = Math.max(0, localResultLimit);
		}

		static Input empty()
		{
			return new Input("", null, Collections.<String,Map<String,Double>>emptyMap(),
					Collections.<String,String>emptyMap(), Collections.<String,String>emptyMap(),
					Collections.<String>emptyList(), Collections.<String,Integer>emptyMap(), 1, 1, 0);
		}
	}

	static final class Result
	{
		private final String runId;
		private final File datasetFile;
		private final String summary;
		private final long rowsScanned;
		private final long datedRows;
		private final int graphArtifactEdges;
		private final TreeMap<String,TreeMap<String,Double>> scores;
		private final TreeMap<String,TreeMap<String,String>> details;
		private final LinkedHashMap<String,TargetReport> reports;
		private final ResearchSummary researchSummary;

		private Result(String runId, File datasetFile, String summary, long rowsScanned, long datedRows,
				int graphArtifactEdges, Map<String,TreeMap<String,Double>> scores,
				Map<String,TreeMap<String,String>> details, Map<String,TargetReport> reports,
				ResearchSummary researchSummary)
		{
			this.runId = runId == null ? "" : runId;
			this.datasetFile = datasetFile;
			this.summary = summary == null ? "" : summary;
			this.rowsScanned = Math.max(0L, rowsScanned);
			this.datedRows = Math.max(0L, datedRows);
			this.graphArtifactEdges = Math.max(0, graphArtifactEdges);
			this.scores = copyNestedDouble(scores);
			this.details = copyNestedString(details);
			this.reports = new LinkedHashMap<String,TargetReport>();
			if (reports != null)
			{
				this.reports.putAll(reports);
			}
			this.researchSummary = researchSummary == null ? ResearchSummary.empty() : researchSummary;
		}

		static Result empty(String runId, File datasetFile, String summary)
		{
			return new Result(runId, datasetFile, summary, 0L, 0L, 0,
					Collections.<String,TreeMap<String,Double>>emptyMap(),
					Collections.<String,TreeMap<String,String>>emptyMap(),
					Collections.<String,TargetReport>emptyMap(), ResearchSummary.empty());
		}

		TreeMap<String,TreeMap<String,Double>> getScores()
		{
			return copyNestedDouble(scores);
		}

		TreeMap<String,TreeMap<String,String>> getDetails()
		{
			return copyNestedString(details);
		}

		String getSummary()
		{
			return summary;
		}

		String getRunId()
		{
			return runId;
		}

		File getDatasetFile()
		{
			return datasetFile;
		}

		long getRowsScanned()
		{
			return rowsScanned;
		}

		long getDatedRows()
		{
			return datedRows;
		}

		/** Always 0: TGF reads no graph files; its graph is built from the run's own text. */
		int getGraphArtifactEdges()
		{
			return graphArtifactEdges;
		}

		Map<String,TargetReport> getReports()
		{
			return Collections.unmodifiableMap(reports);
		}

		ResearchSummary getResearchSummary()
		{
			return researchSummary;
		}
	}

	static final class TargetReport
	{
		final String targetUser;
		/** The target's actual followee, used only to grade the ranking. */
		final String targetFollowee;
		final List<CandidateScore> topCandidates;
		final String headline;
		/** Rank of the first same-community candidate among all candidates (0: none). */
		final int relevantRank;
		/** NDCG@5 against same-community relevance, ideal over min(|relevant|, 5). */
		final double ndcgAtFive;
		final int observedRows;
		final String temporalCoverage;
		final List<BenchmarkRow> benchmarkRows;

		private TargetReport(String targetUser, String targetFollowee, List<CandidateScore> topCandidates,
				String headline, int relevantRank, double ndcgAtFive, int observedRows, String temporalCoverage,
				List<BenchmarkRow> benchmarkRows)
		{
			this.targetUser = targetUser;
			this.targetFollowee = targetFollowee;
			this.topCandidates = Collections.unmodifiableList(new ArrayList<CandidateScore>(topCandidates));
			this.headline = headline;
			this.relevantRank = relevantRank;
			this.ndcgAtFive = ndcgAtFive;
			this.observedRows = observedRows;
			this.temporalCoverage = temporalCoverage;
			this.benchmarkRows = Collections.unmodifiableList(new ArrayList<BenchmarkRow>(
					benchmarkRows == null ? Collections.<BenchmarkRow>emptyList() : benchmarkRows));
		}

		static TargetReport from(UserProfile target, List<CandidateScore> kept, List<CandidateScore> all,
				List<BenchmarkRow> lenses, TemporalCorpus corpus)
		{
			String headline;
			if (kept.isEmpty())
			{
				headline = "No candidate received a positive probability.";
			}
			else
			{
				CandidateScore top = kept.get(0);
				headline = top.candidate.name + " leads with probability " + fmt(top.finalScore)
						+ " (text " + pct(top.text) + ", graph " + pct(top.graph) + ", time " + pct(top.time) + ").";
			}
			BenchmarkRow fused = lenses.isEmpty() ? null : lenses.get(0);
			String coverage = target.observedRows <= 0 ? "no rows in the corpus"
					: corpus.available && target.latestTime != Long.MIN_VALUE
							? target.observedRows + " rows from " + date(target.firstTime) + " to " + date(target.latestTime)
							: target.observedRows + " rows, undated";
			return new TargetReport(target.name, target.followee, kept, headline,
					fused == null ? 0 : fused.relevantRank, fused == null ? 0.0 : fused.ndcgAtFive,
					target.observedRows, coverage, lenses);
		}
	}

	static final class BenchmarkRow
	{
		final String label;
		final String topCandidate;
		final double topScore;
		final int relevantRank;
		final double hitAtOne;
		final double hitAtThree;
		final double mrr;
		final double ndcgAtFive;
		final String note;

		private BenchmarkRow(String label, String topCandidate, double topScore, int relevantRank,
				double hitAtOne, double hitAtThree, double mrr, double ndcgAtFive, String note)
		{
			this.label = label == null ? "" : label;
			this.topCandidate = topCandidate == null ? "" : topCandidate;
			this.topScore = clamp01(topScore);
			this.relevantRank = Math.max(0, relevantRank);
			this.hitAtOne = clamp01(hitAtOne);
			this.hitAtThree = clamp01(hitAtThree);
			this.mrr = clamp01(mrr);
			this.ndcgAtFive = clamp01(ndcgAtFive);
			this.note = note == null ? "" : note;
		}
	}

	static final class CandidateScore
	{
		static final Comparator<CandidateScore> FINAL_ORDER = new Comparator<CandidateScore>()
		{
			public int compare(CandidateScore first, CandidateScore second)
			{
				return compareScore(first, second, first.finalScore, second.finalScore);
			}
		};

		final UserProfile target;
		final UserProfile candidate;
		private FusionModel model;
		double text;
		double graph;
		double time;
		/** Retired signals of the earlier heuristic model; always 0. */
		double bridge;
		double debias;
		double robust;
		double finalScore;
		double withoutTextScore;
		double withoutGraphScore;
		double withoutTimeScore;
		double withoutBridgeScore;
		double withoutDebiasScore;
		double withoutTopTermScore;
		double withoutTopTermText;
		/** Additive log-odds contributions relative to the mean training pair. */
		double textContribution;
		double graphContribution;
		double timeContribution;
		double bridgeContribution;
		double debiasContribution;
		double robustContribution;
		double marginToNext;
		double confidenceRadius;
		double confidenceLower;
		double confidenceUpper;
		/** Share of bootstrap refits that keep the candidate in the top ten. */
		double stability;
		double evidenceCoverage;
		double counterfactualFragility;
		double calibrationResidual;
		double calibrationThreshold;
		double calibrationPValue;
		double calibratedConfidence;
		double challengerScore;
		double challengerGap;
		double challengerText;
		double challengerGraph;
		double challengerTime;
		double challengerBridge;
		double challengerDebias;
		double challengerRobust;
		int rank;
		int withoutTextRank;
		int withoutGraphRank;
		int withoutTimeRank;
		int withoutBridgeRank;
		int withoutDebiasRank;
		int withoutTopTermRank;
		String path = "";
		String challengerName = "";
		String challengerFollowee = "";
		String bestContrastSignal = "n/a";
		String reliabilityBand = "pending";
		String calibrationVerdict = "";
		String calibrationNote = "";
		String whyThis = "";
		String whyNotChallenger = "";
		List<String> sharedTerms = Collections.emptyList();
		List<TimelineFrame> timeline = Collections.emptyList();
		List<CounterfactualProbe> counterfactuals = Collections.emptyList();
		List<EvidenceAtom> evidenceAtoms = Collections.emptyList();
		List<String> riskFlags = Collections.emptyList();
		String researchVerdict = "";

		private CandidateScore(UserProfile target, UserProfile candidate)
		{
			this.target = target;
			this.candidate = candidate;
		}

		String candidateName()
		{
			return candidate.name;
		}

		String candidateFollowee()
		{
			return candidate.followee;
		}

		String impactSummary()
		{
			return whyThis + " " + whyNotChallenger + " Without text: rank " + rankText(withoutTextRank)
					+ "; without graph: rank " + rankText(withoutGraphRank) + "; without time: rank "
					+ rankText(withoutTimeRank) + "; stability " + pct(stability) + ".";
		}

		/**
		 * The probability with the chosen signals removed (set to the mean
		 * training pair); the bridge and debias switches are ignored (those
		 * signals were retired).
		 */
		double counterfactualScore(boolean removeGraph, boolean removeTime, boolean removeBridge,
				boolean removeDebias, boolean removeTopTerm)
		{
			if (model == null)
			{
				return finalScore;
			}
			double[] x = {removeTopTerm ? withoutTopTermText : text, graph, time};
			return model.probabilityWithout(x, new boolean[] {false, removeGraph, removeTime});
		}

		private static int compareScore(CandidateScore first, CandidateScore second, double firstScore,
				double secondScore)
		{
			int score = Double.compare(secondScore, firstScore);
			return score != 0 ? score : first.candidate.name.compareTo(second.candidate.name);
		}
	}

	static final class EvidenceAtom
	{
		final String label;
		final double value;
		/** Additive log-odds contribution (may be negative). */
		final double contribution;
		final String explanation;

		private EvidenceAtom(String label, double value, double contribution, String explanation)
		{
			this.label = label == null ? "" : label;
			this.value = clamp01(value);
			this.contribution = contribution;
			this.explanation = explanation == null ? "" : explanation;
		}
	}

	static final class TimelineFrame
	{
		final int index;
		final double progress;
		final String label;
		final double score;
		final double text;
		final double graph;
		final double time;
		final double bridge;
		final double debias;
		final double robust;
		final String evidence;
		final double scoreDelta;
		final String dominantSignal;
		final String auditNote;
		int rank;
		int previousRank;

		private TimelineFrame(int index, double progress, String label, double score, double text, double graph,
				double time, double bridge, double debias, double robust, String evidence, double scoreDelta,
				String dominantSignal, String auditNote)
		{
			this.index = index;
			this.progress = clamp01(progress);
			this.label = label == null ? "" : label;
			this.score = clamp01(score);
			this.text = clamp01(text);
			this.graph = clamp01(graph);
			this.time = clamp01(time);
			this.bridge = clamp01(bridge);
			this.debias = clamp01(debias);
			this.robust = clamp01(robust);
			this.evidence = evidence == null ? "" : evidence;
			this.scoreDelta = scoreDelta;
			this.dominantSignal = dominantSignal == null ? "" : dominantSignal;
			this.auditNote = auditNote == null ? "" : auditNote;
		}
	}

	static final class CounterfactualProbe
	{
		final String label;
		final double score;
		final int rank;
		final double scoreDrop;
		final boolean rankChanged;
		final int rankShift;

		private CounterfactualProbe(String label, double score, int rank, double scoreDrop, boolean rankChanged,
				int rankShift)
		{
			this.label = label == null ? "" : label;
			this.score = clamp01(score);
			this.rank = rank;
			this.scoreDrop = Math.max(0.0, scoreDrop);
			this.rankChanged = rankChanged;
			this.rankShift = Math.max(0, rankShift);
		}

		static CounterfactualProbe from(String label, double score, int rank, double originalScore, int originalRank)
		{
			return new CounterfactualProbe(label, score, rank, Math.max(0.0, originalScore - score),
					originalRank > 0 && rank != originalRank,
					originalRank > 0 && rank > 0 ? Math.abs(rank - originalRank) : 0);
		}
	}

	static final class ResearchSummary
	{
		final int targetCount;
		final int scoredPairs;
		final int timelineFrames;
		/** Fusion lens, averaged over the targets. */
		final double hitAtOne;
		final double hitAtThree;
		final double mrr;
		final double ndcgAtFive;
		final double averageStability;
		final double averageConfidenceWidth;
		/** Share of kept candidates whose rank changes when one signal is removed. */
		final double counterfactualFlipRate;
		final double averageEvidenceCoverage;
		final double averageCounterfactualFragility;
		final double averageCalibrationPValue;
		/** Share of kept candidates in the top ten under fewer than half of the refits. */
		final double abstainRate;
		final int evidenceLimitFlags;
		final List<BenchmarkRow> benchmarkRows;
		final FoundationReadiness foundationReadiness;
		final String headline;

		private ResearchSummary(int targetCount, int scoredPairs, int timelineFrames, double hitAtOne,
				double hitAtThree, double mrr, double ndcgAtFive, double averageStability,
				double averageConfidenceWidth, double counterfactualFlipRate, double averageEvidenceCoverage,
				double averageCounterfactualFragility, double averageCalibrationPValue, double abstainRate,
				int evidenceLimitFlags, List<BenchmarkRow> benchmarkRows, FoundationReadiness foundationReadiness,
				String headline)
		{
			this.targetCount = Math.max(0, targetCount);
			this.scoredPairs = Math.max(0, scoredPairs);
			this.timelineFrames = Math.max(0, timelineFrames);
			this.hitAtOne = clamp01(hitAtOne);
			this.hitAtThree = clamp01(hitAtThree);
			this.mrr = clamp01(mrr);
			this.ndcgAtFive = clamp01(ndcgAtFive);
			this.averageStability = clamp01(averageStability);
			this.averageConfidenceWidth = clamp01(averageConfidenceWidth);
			this.counterfactualFlipRate = clamp01(counterfactualFlipRate);
			this.averageEvidenceCoverage = clamp01(averageEvidenceCoverage);
			this.averageCounterfactualFragility = clamp01(averageCounterfactualFragility);
			this.averageCalibrationPValue = clamp01(averageCalibrationPValue);
			this.abstainRate = clamp01(abstainRate);
			this.evidenceLimitFlags = Math.max(0, evidenceLimitFlags);
			this.benchmarkRows = Collections.unmodifiableList(new ArrayList<BenchmarkRow>(
					benchmarkRows == null ? Collections.<BenchmarkRow>emptyList() : benchmarkRows));
			this.foundationReadiness = foundationReadiness == null ? FoundationReadiness.empty() : foundationReadiness;
			this.headline = headline == null ? "" : headline;
		}

		static ResearchSummary empty()
		{
			return new ResearchSummary(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
					Collections.<BenchmarkRow>emptyList(), FoundationReadiness.empty(),
					"No TGF run has been recorded yet.");
		}

		static ResearchSummary from(Collection<TargetReport> reports, int scoredPairs, TemporalCorpus corpus,
				FusionModel model)
		{
			if (reports == null || reports.isEmpty())
			{
				return empty();
			}
			int targets = 0;
			int frames = 0;
			int candidates = 0;
			int flips = 0;
			int unstable = 0;
			int flags = 0;
			double stability = 0.0;
			double width = 0.0;
			double coverage = 0.0;
			double fragility = 0.0;
			for (TargetReport report : reports)
			{
				targets++;
				for (CandidateScore score : report.topCandidates)
				{
					candidates++;
					frames += score.timeline.size();
					stability += score.stability;
					width += Math.max(0.0, score.confidenceUpper - score.confidenceLower);
					coverage += score.evidenceCoverage;
					fragility += score.counterfactualFragility;
					if (score.stability < 0.5)
					{
						unstable++;
					}
					if (score.withoutTextRank != score.rank || score.withoutGraphRank != score.rank
							|| score.withoutTimeRank != score.rank)
					{
						flips++;
					}
					for (String flag : score.riskFlags)
					{
						if (!"no evidence-limit flags".equals(flag))
						{
							flags++;
						}
					}
				}
			}
			List<BenchmarkRow> lenses = aggregate(reports);
			BenchmarkRow fused = lenses.isEmpty() ? null : lenses.get(0);
			double denominator = Math.max(1, candidates);
			String headline = fused == null ? "No graded targets."
					: "Fusion lens over " + targets + " target(s): Hit@1 " + pct(fused.hitAtOne) + ", Hit@3 "
							+ pct(fused.hitAtThree) + ", MRR " + fmt(fused.mrr) + ", NDCG@5 " + fmt(fused.ndcgAtFive) + ".";
			return new ResearchSummary(targets, scoredPairs, frames,
					fused == null ? 0.0 : fused.hitAtOne, fused == null ? 0.0 : fused.hitAtThree,
					fused == null ? 0.0 : fused.mrr, fused == null ? 0.0 : fused.ndcgAtFive,
					stability / denominator, width / denominator, flips / denominator, coverage / denominator,
					fragility / denominator, stability / denominator, unstable / denominator, flags, lenses,
					FoundationReadiness.from(corpus, model, coverage / denominator), headline);
		}

		private static List<BenchmarkRow> aggregate(Collection<TargetReport> reports)
		{
			LinkedHashMap<String,double[]> totals = new LinkedHashMap<String,double[]>();
			LinkedHashMap<String,String> notes = new LinkedHashMap<String,String>();
			LinkedHashMap<String,String> tops = new LinkedHashMap<String,String>();
			for (TargetReport report : reports)
			{
				for (BenchmarkRow row : report.benchmarkRows)
				{
					double[] total = totals.get(row.label);
					if (total == null)
					{
						total = new double[6];
						totals.put(row.label, total);
						notes.put(row.label, row.note);
						tops.put(row.label, row.topCandidate);
					}
					total[0] += 1.0;
					total[1] += row.hitAtOne;
					total[2] += row.hitAtThree;
					total[3] += row.mrr;
					total[4] += row.ndcgAtFive;
					total[5] += row.topScore;
				}
			}
			ArrayList<BenchmarkRow> rows = new ArrayList<BenchmarkRow>();
			for (Map.Entry<String,double[]> entry : totals.entrySet())
			{
				double[] t = entry.getValue();
				double n = Math.max(1.0, t[0]);
				rows.add(new BenchmarkRow(entry.getKey(), tops.get(entry.getKey()), t[5] / n, 0, t[1] / n, t[2] / n,
						t[3] / n, t[4] / n, notes.get(entry.getKey())));
			}
			return Collections.unmodifiableList(rows);
		}
	}

	/**
	 * What the run's evidence rests on: the share of dated rows, the share of
	 * training pairs with graph evidence, the mean evidence coverage of the kept
	 * candidates, and the fusion's held-out pair AUC ({@code overall}).
	 */
	static final class FoundationReadiness
	{
		final double temporalDepth;
		final double graphCoverage;
		final double evidenceCoverage;
		final double calibrationTrust;
		final double benchmarkHonesty;
		final double overall;
		final String label;
		final String note;

		private FoundationReadiness(double temporalDepth, double graphCoverage, double evidenceCoverage,
				double calibrationTrust, double benchmarkHonesty, double overall, String label, String note)
		{
			this.temporalDepth = clamp01(temporalDepth);
			this.graphCoverage = clamp01(graphCoverage);
			this.evidenceCoverage = clamp01(evidenceCoverage);
			this.calibrationTrust = clamp01(calibrationTrust);
			this.benchmarkHonesty = clamp01(benchmarkHonesty);
			this.overall = clamp01(overall);
			this.label = label == null ? "" : label;
			this.note = note == null ? "" : note;
		}

		static FoundationReadiness empty()
		{
			return new FoundationReadiness(0, 0, 0, 0, 0, 0, "no run yet", "Run TGF to fit and evaluate the fusion.");
		}

		static FoundationReadiness from(TemporalCorpus corpus, FusionModel model, double coverage)
		{
			double dated = corpus.rowsScanned <= 0 ? 0.0 : corpus.datedRows / (double)corpus.rowsScanned;
			double auc = Double.isNaN(model.heldOutAuc) ? 0.0 : model.heldOutAuc;
			String label = !model.supervised ? "unsupervised fusion"
					: Double.isNaN(model.heldOutAuc) ? "learned fusion (no held-out anchors)"
					: "learned fusion, held-out pair AUC " + fmt(model.heldOutAuc);
			return new FoundationReadiness(dated, model.active[GRAPH] ? 1.0 : 0.0, coverage, auc,
					model.supervised ? 1.0 : 0.0, auc, label, model.describe());
		}
	}

	private static final class UserProfile
	{
		final int index;
		final String name;
		final String followee;
		final LinkedHashMap<String,Double> tfidf;
		double[] activity;
		long firstTime = Long.MAX_VALUE;
		long latestTime = Long.MIN_VALUE;
		int observedRows;

		UserProfile(int index, String name, String followee, Map<String,Double> tfidf)
		{
			this.index = index;
			this.name = name;
			this.followee = followee == null ? "" : followee.trim();
			this.tfidf = new LinkedHashMap<String,Double>(tfidf);
		}
	}

	private static final class TemporalCorpus
	{
		long rowsScanned;
		long datedRows;
		long minTime = Long.MAX_VALUE;
		long maxTime = Long.MIN_VALUE;
		boolean available;
		String warning = "";
	}

	private static TreeMap<String,TreeMap<String,Double>> copyNestedDouble(Map<String,TreeMap<String,Double>> source)
	{
		TreeMap<String,TreeMap<String,Double>> copy = new TreeMap<String,TreeMap<String,Double>>();
		if (source != null)
		{
			for (Map.Entry<String,TreeMap<String,Double>> entry : source.entrySet())
			{
				copy.put(entry.getKey(), entry.getValue() == null
						? new TreeMap<String,Double>() : new TreeMap<String,Double>(entry.getValue()));
			}
		}
		return copy;
	}

	private static TreeMap<String,TreeMap<String,String>> copyNestedString(Map<String,TreeMap<String,String>> source)
	{
		TreeMap<String,TreeMap<String,String>> copy = new TreeMap<String,TreeMap<String,String>>();
		if (source != null)
		{
			for (Map.Entry<String,TreeMap<String,String>> entry : source.entrySet())
			{
				copy.put(entry.getKey(), entry.getValue() == null
						? new TreeMap<String,String>() : new TreeMap<String,String>(entry.getValue()));
			}
		}
		return copy;
	}
}
