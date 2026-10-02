package TwitterGatherDataFollowers.userRyersonU;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.TreeMap;

/**
 * The Temporal Graph Foundation (TGF) recommender.
 *
 * For a target user TGF estimates the probability that the target belongs to
 * each community of the run (the users who follow one account), then
 *
 * <ul>
 * <li>recommends followee accounts by that probability, and</li>
 * <li>recommends users by the probability of sharing the target's community:
 *     p(account of v) for a user v whose follow is known, and
 *     sum_c p(c) q_v(c) for another target, whose follow is hidden.</li>
 * </ul>
 *
 * The probabilities come from {@link TgfModel}: a cross-fitted Super Learner
 * over text, graph and temporal learners whose stack is a non-negative
 * logarithmic opinion pool fitted by maximum likelihood, with split-conformal
 * prediction sets. A target's own follow is never read: it only grades the
 * result.
 *
 * Every recommendation carries an exact explanation: the log-odds of the
 * predicted community against the runner-up split by learner, the terms of the
 * text model behind them, the labelled users whose labels reached the target
 * through each graph (label propagation is linear in the labels, so each
 * user's influence is exact), the months that set the activity evidence, each
 * learner's ablation and the fewest terms whose removal changes the
 * prediction.
 *
 * The model is fitted once per run from the whole corpus (the prepare step,
 * computed by one node and shared), so the results are the same for every
 * node count; each target is then scored and explained independently.
 */
final class TemporalGraphFoundationModelSupport
{
	static final String FORMAT = "dsmp-tgf/1";
	private static final int TERM_EVIDENCE = 10;
	private static final int NEIGHBOUR_EVIDENCE = 8;
	private static final int COUNTERFACTUAL_TERMS = 25;
	private static final int RECOMMENDATION_EVIDENCE = 25;
	private static final int COMMUNITY_TERMS = 12;
	/** Weight of the text similarity in a user's delivered score (it only orders users of equal probability). */
	static final double TIE_BREAK = 1e-6;

	private TemporalGraphFoundationModelSupport()
	{
	}

	// =================================================================== state

	/**
	 * Everything that does not depend on the target: the features, the fitted
	 * model and the model card. Built once per run and then only read.
	 */
	static final class Prepared implements PayloadSize.Sized
	{
		private final Input input;
		private final TgfFeatures features;
		private final TgfModel model;
		private final LinkedHashMap<String,Object> card;
		private volatile long payloadBytes = -1L;

		private Prepared(Input input, TgfFeatures features, TgfModel model, LinkedHashMap<String,Object> card)
		{
			this.input = input;
			this.features = features;
			this.model = model;
			this.card = card;
		}

		/** Wire size of the shared state: features, graphs, learners and posteriors. */
		public long payloadBytes()
		{
			long cached = payloadBytes;
			if (cached < 0L)
			{
				cached = model.payloadBytes() + PayloadSize.of(card);
				payloadBytes = cached;
			}
			return cached;
		}

		boolean isEmpty()
		{
			return features.size() == 0;
		}

		boolean hasProfile(String user)
		{
			return features.index.containsKey(user);
		}

		int profileCount()
		{
			return features.size();
		}

		TgfModel model()
		{
			return model;
		}

		Map<String,Object> card()
		{
			return card;
		}
	}

	/** One target's ranked users, ranked accounts and explanation. */
	static final class TargetOutcome implements PayloadSize.Sized
	{
		private final String target;
		private final TreeMap<String,Double> scores;
		private final TreeMap<String,String> details;
		private final TreeMap<String,Double> accounts;
		private final TreeMap<String,String> accountDetails;
		private final LinkedHashMap<String,Object> report;
		private final int scoredPairs;

		private TargetOutcome(String target, TreeMap<String,Double> scores, TreeMap<String,String> details,
				TreeMap<String,Double> accounts, TreeMap<String,String> accountDetails,
				LinkedHashMap<String,Object> report, int scoredPairs)
		{
			this.target = target;
			this.scores = scores;
			this.details = details;
			this.accounts = accounts;
			this.accountDetails = accountDetails;
			this.report = report;
			this.scoredPairs = scoredPairs;
		}

		String getTarget()
		{
			return target;
		}

		/** Kept candidate users and their probability of sharing the target's community. */
		TreeMap<String,Double> getScores()
		{
			return scores;
		}

		TreeMap<String,String> getDetails()
		{
			return details;
		}

		/** Every account and the probability that the target follows it. */
		TreeMap<String,Double> getAccountScores()
		{
			return accounts;
		}

		TreeMap<String,String> getAccountDetails()
		{
			return accountDetails;
		}

		Map<String,Object> getReport()
		{
			return report;
		}

		int getScoredPairs()
		{
			return scoredPairs;
		}

		public long payloadBytes()
		{
			return PayloadSize.string(target) + PayloadSize.of(scores) + PayloadSize.of(details)
					+ PayloadSize.of(accounts) + PayloadSize.of(accountDetails) + PayloadSize.of(report) + 4L;
		}
	}

	// ================================================================ pipeline

	static Result score(Input input)
	{
		Prepared prepared = prepare(input);
		ArrayList<TargetOutcome> outcomes = new ArrayList<TargetOutcome>();
		for (String target : prepared.input.targetUsers)
		{
			TargetOutcome outcome = scoreTarget(prepared, target);
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
		TreeMap<String,String> followees = new TreeMap<String,String>();
		followees.putAll(safe.userFollowees);
		followees.putAll(safe.datasetUserFollowees);
		ArrayList<String> users = new ArrayList<String>();
		for (Map.Entry<String,? extends Map<String,Double>> entry : safe.tfidfDocuments.entrySet())
		{
			if (entry.getKey() != null && entry.getValue() != null && !entry.getValue().isEmpty())
			{
				users.add(entry.getKey());
			}
		}
		TgfFeatures features = TgfFeatures.build(safe.datasetFile, users, safe.tfidfDocuments);
		TgfModel model = TgfModel.fit(features, followees, new HashSet<String>(safe.targetUsers));
		return new Prepared(safe, features, model, modelCard(safe, features, model));
	}

	/**
	 * Scores {@code targetUser}: ranks every other user and every account and
	 * explains the result. Reads the prepared state only; null when the target
	 * has no profile.
	 */
	static TargetOutcome scoreTarget(Prepared prepared, String targetUser)
	{
		Integer position = prepared.features.index.get(targetUser);
		if (position == null)
		{
			return null;
		}
		TgfFeatures features = prepared.features;
		TgfModel model = prepared.model;
		final int u = position.intValue();
		int n = features.size();
		final double[] similarity = similarities(features, u);
		final double[] together = new double[n];
		double[] p = model.posterior[u];
		for (int v = 0; v < n; v++)
		{
			if ((v & 1023) == 0)
			{
				//A revoked unit (drain, crash) stops here.
				UnitCancellation.check();
			}
			if (v == u)
			{
				continue;
			}
			together[v] = model.supervised ? sameCommunity(model, u, v) : similarity[v];
		}
		Integer[] order = new Integer[n - 1];
		int m = 0;
		for (int v = 0; v < n; v++)
		{
			if (v != u)
			{
				order[m++] = Integer.valueOf(v);
			}
		}
		final String[] names = features.users;
		Arrays.sort(order, new Comparator<Integer>()
		{
			public int compare(Integer a, Integer b)
			{
				int byScore = Double.compare(together[b.intValue()], together[a.intValue()]);
				if (byScore != 0)
				{
					return byScore;
				}
				int bySimilarity = Double.compare(similarity[b.intValue()], similarity[a.intValue()]);
				return bySimilarity != 0 ? bySimilarity : names[a.intValue()].compareTo(names[b.intValue()]);
			}
		});
		int keep = prepared.input.localResultLimit <= 0 ? order.length
				: Math.min(prepared.input.localResultLimit, order.length);
		TreeMap<String,Double> scores = new TreeMap<String,Double>();
		TreeMap<String,String> details = new TreeMap<String,String>();
		for (int i = 0; i < keep; i++)
		{
			int v = order[i].intValue();
			double score = together[v];
			if (!(score > 0.0) || !Double.isFinite(score))
			{
				continue;
			}
			//Users of one community share a probability; the merge breaks score ties
			//by name, so the delivered score carries the text similarity as the
			//tie-break, 10^-6 below the probability's resolution.
			scores.put(names[v], Double.valueOf(model.supervised ? score + TIE_BREAK * similarity[v] : score));
			details.put(names[v], detail(model, features, u, v, score, similarity[v]));
		}
		TreeMap<String,Double> accounts = new TreeMap<String,Double>();
		if (model.supervised)
		{
			for (int c = 0; c < model.classCount(); c++)
			{
				accounts.put(model.classes[c], Double.valueOf(p[c]));
			}
		}
		else
		{
			//Without a model, an account scores its most similar follower (as user rankers project).
			for (int v = 0; v < n; v++)
			{
				if (v != u && model.label[v] >= 0 && similarity[v] > 0.0)
				{
					String account = model.classes[model.label[v]];
					Double old = accounts.get(account);
					if (old == null || similarity[v] > old.doubleValue())
					{
						accounts.put(account, Double.valueOf(similarity[v]));
					}
				}
			}
		}
		TreeMap<String,String> accountDetails = new TreeMap<String,String>();
		if (model.supervised)
		{
			boolean[] set = model.predictionSet(p);
			int[] ranked = rankClasses(p);
			for (int i = 0; i < ranked.length; i++)
			{
				int c = ranked[i];
				accountDetails.put(model.classes[c], "P(follows) " + fixed(p[c]) + " | rank " + (i + 1) + " of "
						+ ranked.length + " | " + (set[c] ? "inside" : "outside") + " the "
						+ (int)Math.round(100 * (1 - TgfModel.CONFORMAL_ALPHA)) + "% conformal set");
			}
		}
		LinkedHashMap<String,Object> report = explain(prepared, u, order, together, similarity);
		return new TargetOutcome(targetUser, scores, details, accounts, accountDetails, report, n - 1);
	}

	/** The run's result from its targets' outcomes, in target order. */
	static Result assemble(Prepared prepared, List<TargetOutcome> outcomes)
	{
		TreeMap<String,TreeMap<String,Double>> scores = new TreeMap<String,TreeMap<String,Double>>();
		TreeMap<String,TreeMap<String,String>> details = new TreeMap<String,TreeMap<String,String>>();
		TreeMap<String,TreeMap<String,Double>> accounts = new TreeMap<String,TreeMap<String,Double>>();
		TreeMap<String,TreeMap<String,String>> accountDetails = new TreeMap<String,TreeMap<String,String>>();
		ArrayList<Object> targets = new ArrayList<Object>();
		int scoredPairs = 0;
		int graded = 0;
		int correct = 0;
		int covered = 0;
		for (TargetOutcome outcome : outcomes)
		{
			if (outcome == null)
			{
				continue;
			}
			scores.put(outcome.target, outcome.scores);
			details.put(outcome.target, outcome.details);
			accounts.put(outcome.target, outcome.accounts);
			accountDetails.put(outcome.target, outcome.accountDetails);
			targets.add(outcome.report);
			scoredPairs += outcome.scoredPairs;
			Object isCorrect = outcome.report.get("correct");
			if (isCorrect instanceof Boolean)
			{
				graded++;
				correct += ((Boolean)isCorrect).booleanValue() ? 1 : 0;
				covered += Boolean.TRUE.equals(outcome.report.get("inSet")) ? 1 : 0;
			}
		}
		LinkedHashMap<String,Object> json = new LinkedHashMap<String,Object>(prepared.card);
		LinkedHashMap<String,Object> run = new LinkedHashMap<String,Object>();
		run.put("targets", Integer.valueOf(targets.size()));
		run.put("scoredPairs", Integer.valueOf(scoredPairs));
		run.put("graded", Integer.valueOf(graded));
		run.put("correct", Integer.valueOf(correct));
		run.put("covered", Integer.valueOf(covered));
		json.put("run", run);
		json.put("targets", targets);
		TgfModel model = prepared.model;
		String summary = "Temporal Graph Foundation scored " + scoredPairs + " target-candidate pairs. "
				+ (model.supervised ? String.format(Locale.US,
						"Super Learner over %d learners: cross-validated accuracy %.3f (majority %.3f), log-loss %.3f,"
								+ " %d%% conformal sets cover %.3f with %.2f accounts on average.",
						activeCount(model), model.validation.accuracy, model.validation.majorityAccuracy,
						model.validation.logLoss, (int)Math.round(100 * (1 - TgfModel.CONFORMAL_ALPHA)),
						model.validation.conformalCoverage, model.validation.conformalSetSize)
						: model.note);
		return new Result(prepared.input.runId, prepared.input.datasetFile, summary,
				prepared.features.rowsScanned, prepared.features.datedRows, scores, details, accounts, accountDetails,
				json);
	}

	// ============================================================== ranking

	/** Cosine of the target's text with every user's. */
	private static double[] similarities(TgfFeatures features, int u)
	{
		TgfFeatures.Sparse text = features.text;
		double[] dense = new double[text.columns];
		for (int p = text.pointer[u]; p < text.pointer[u + 1]; p++)
		{
			dense[text.column[p]] = text.value[p];
		}
		double[] similarity = new double[features.size()];
		for (int v = 0; v < features.size(); v++)
		{
			similarity[v] = v == u ? 0.0 : Math.max(0.0, text.dot(v, dense));
		}
		return similarity;
	}

	/** P(v shares u's community): p_u(account of v), or sum_c p_u(c) p_v(c) when v's follow is hidden. */
	private static double sameCommunity(TgfModel model, int u, int v)
	{
		double[] p = model.posterior[u];
		if (model.label[v] >= 0)
		{
			return p[model.label[v]];
		}
		double total = 0.0;
		double[] q = model.posterior[v];
		for (int c = 0; c < p.length; c++)
		{
			total += p[c] * q[c];
		}
		return total;
	}

	private static String detail(TgfModel model, TgfFeatures features, int u, int v, double score, double similarity)
	{
		String community = model.label[v] >= 0 ? "follows " + model.classes[model.label[v]] : "follow hidden (a target)";
		List<String> shared = sharedTerms(features, u, v, 3);
		return (model.supervised ? "P(same community) " + fixed(score) : "text cosine " + fixed(score)) + " | "
				+ community + " | text cosine " + fixed(similarity)
				+ (shared.isEmpty() ? "" : " | shares " + join(shared));
	}

	// ========================================================== explanation

	private static LinkedHashMap<String,Object> explain(Prepared prepared, int u, Integer[] order, double[] together,
			double[] similarity)
	{
		TgfFeatures features = prepared.features;
		TgfModel model = prepared.model;
		LinkedHashMap<String,Object> report = new LinkedHashMap<String,Object>();
		report.put("user", features.users[u]);
		report.put("truth", model.truth[u] >= 0 ? model.classes[model.truth[u]] : null);
		report.put("tweets", Integer.valueOf(features.tweets[u]));
		report.put("datedTweets", Integer.valueOf(features.datedTweets[u]));
		report.put("first", features.datedTweets[u] > 0 ? day(features.firstTime[u]) : null);
		report.put("last", features.datedTweets[u] > 0 ? day(features.lastTime[u]) : null);
		report.put("terms", Integer.valueOf(features.text.pointer[u + 1] - features.text.pointer[u]));
		int classes = model.classCount();
		if (!model.supervised || classes < 2)
		{
			report.put("supervised", Boolean.FALSE);
			report.put("headline", "Ranked by text similarity: " + model.note);
			report.put("recommendations", recommendations(model, features, u, order, together, similarity));
			return report;
		}
		report.put("supervised", Boolean.TRUE);
		double[] p = model.posterior[u];
		int[] ranked = rankClasses(p);
		int best = ranked[0];
		int second = ranked[1];
		boolean[] set = model.predictionSet(p);
		report.put("posterior", toList(p));
		report.put("predicted", model.classes[best]);
		report.put("runnerUp", model.classes[second]);
		report.put("probability", Double.valueOf(p[best]));
		report.put("margin", Double.valueOf(p[best] - p[second]));
		report.put("entropy", Double.valueOf(entropy(p)));
		ArrayList<Object> setNames = new ArrayList<Object>();
		for (int c : ranked)
		{
			if (set[c])
			{
				setNames.add(model.classes[c]);
			}
		}
		report.put("set", setNames);
		if (model.truth[u] >= 0)
		{
			report.put("correct", Boolean.valueOf(model.truth[u] == best));
			report.put("inSet", Boolean.valueOf(set[model.truth[u]]));
			report.put("truthRank", Integer.valueOf(rankOf(ranked, model.truth[u])));
		}
		//Log-odds of the predicted community against the runner-up, split exactly by learner.
		double logOdds = Math.log(p[best]) - Math.log(p[second]);
		report.put("logOdds", Double.valueOf(logOdds));
		report.put("prior", Double.valueOf(model.bias[best] - model.bias[second]));
		ArrayList<Object> learners = new ArrayList<Object>();
		for (int channel = 0; channel < TgfModel.CHANNELS; channel++)
		{
			LinkedHashMap<String,Object> learner = new LinkedHashMap<String,Object>();
			learner.put("key", TgfModel.CHANNEL_KEYS[channel]);
			learner.put("name", TgfModel.CHANNEL_NAMES[channel]);
			learner.put("active", Boolean.valueOf(model.active[channel]));
			learner.put("weight", Double.valueOf(model.weights[channel]));
			double[] own = TgfLearning.softmax(model.scores[channel][u]);
			int ownBest = TgfModel.argmax(own);
			learner.put("says", model.active[channel] ? model.classes[ownBest] : null);
			learner.put("saysProbability", Double.valueOf(model.active[channel] ? own[ownBest] : Double.NaN));
			learner.put("contribution", Double.valueOf(model.contribution(channel, u, best)
					- model.contribution(channel, u, second)));
			learners.add(learner);
		}
		report.put("learners", learners);
		report.put("ablations", ablations(model, u, best));
		report.put("textEvidence", textEvidence(model, features, u, best, second));
		report.put("textNeighbours", model.active[TgfModel.TEXT_GRAPH]
				? neighbours(model, features, model.textGraph, u, best, false) : new ArrayList<Object>());
		report.put("termTimeNeighbours", model.active[TgfModel.TERM_TIME_GRAPH]
				? neighbours(model, features, model.termTimeGraph, u, best, true) : new ArrayList<Object>());
		report.put("activity", activityEvidence(model, features, u, best, second));
		report.put("counterfactual", counterfactual(model, features, u, best, second));
		ArrayList<Object> accounts = new ArrayList<Object>();
		for (int c : ranked)
		{
			LinkedHashMap<String,Object> account = new LinkedHashMap<String,Object>();
			account.put("account", model.classes[c]);
			account.put("probability", Double.valueOf(p[c]));
			account.put("inSet", Boolean.valueOf(set[c]));
			accounts.add(account);
		}
		report.put("accounts", accounts);
		report.put("recommendations", recommendations(model, features, u, order, together, similarity));
		report.put("headline", headline(model, features, u, best, second, set, learners));
		return report;
	}

	/** Each learner removed (weight zero, the stack otherwise unchanged): the prediction and its probability. */
	private static List<Object> ablations(TgfModel model, int u, int best)
	{
		ArrayList<Object> result = new ArrayList<Object>();
		int classes = model.classCount();
		for (int removed = 0; removed < TgfModel.CHANNELS; removed++)
		{
			if (!model.active[removed] || model.weights[removed] <= 0.0)
			{
				continue;
			}
			double[] logits = new double[classes];
			for (int c = 0; c < classes; c++)
			{
				logits[c] = model.bias[c];
				for (int channel = 0; channel < TgfModel.CHANNELS; channel++)
				{
					if (channel != removed)
					{
						logits[c] += model.contribution(channel, u, c);
					}
				}
			}
			double[] q = TgfLearning.softmax(logits);
			int now = TgfModel.argmax(q);
			LinkedHashMap<String,Object> ablation = new LinkedHashMap<String,Object>();
			ablation.put("key", TgfModel.CHANNEL_KEYS[removed]);
			ablation.put("name", TgfModel.CHANNEL_NAMES[removed]);
			ablation.put("predicted", model.classes[now]);
			ablation.put("probability", Double.valueOf(q[best]));
			ablation.put("changes", Boolean.valueOf(now != best));
			result.add(ablation);
		}
		return result;
	}

	/**
	 * The text model's evidence in the final log-odds: term t adds
	 * x_t (W[best][t] - W[second][t]) times the stack weight over the
	 * learner's scale. The terms for the predicted community first, then those
	 * for the runner-up.
	 */
	private static Map<String,Object> textEvidence(TgfModel model, TgfFeatures features, int u, int best, int second)
	{
		LinkedHashMap<String,Object> evidence = new LinkedHashMap<String,Object>();
		if (model.textModel == null || !model.active[TgfModel.TEXT])
		{
			evidence.put("supporting", new ArrayList<Object>());
			evidence.put("opposing", new ArrayList<Object>());
			return evidence;
		}
		double factor = model.weights[TgfModel.TEXT] / model.scale[TgfModel.TEXT];
		TgfFeatures.Sparse text = features.text;
		final ArrayList<double[]> terms = new ArrayList<double[]>();
		for (int p = text.pointer[u]; p < text.pointer[u + 1]; p++)
		{
			int t = text.column[p];
			double contribution = text.value[p] * (model.textModel.weights[best][t] - model.textModel.weights[second][t])
					* factor;
			terms.add(new double[] {t, contribution, features.termCounts[p], text.value[p]});
		}
		Collections.sort(terms, new Comparator<double[]>()
		{
			public int compare(double[] a, double[] b)
			{
				int byContribution = Double.compare(b[1], a[1]);
				return byContribution != 0 ? byContribution : Double.compare(a[0], b[0]);
			}
		});
		ArrayList<Object> supporting = new ArrayList<Object>();
		ArrayList<Object> opposing = new ArrayList<Object>();
		for (int i = 0; i < terms.size() && supporting.size() < TERM_EVIDENCE; i++)
		{
			if (terms.get(i)[1] > 0.0)
			{
				supporting.add(termEntry(features, terms.get(i)));
			}
		}
		for (int i = terms.size() - 1; i >= 0 && opposing.size() < TERM_EVIDENCE; i--)
		{
			if (terms.get(i)[1] < 0.0)
			{
				opposing.add(termEntry(features, terms.get(i)));
			}
		}
		double total = 0.0;
		for (double[] term : terms)
		{
			total += term[1];
		}
		evidence.put("supporting", supporting);
		evidence.put("opposing", opposing);
		evidence.put("terms", Integer.valueOf(terms.size()));
		evidence.put("termTotal", Double.valueOf(total));
		evidence.put("intercept", Double.valueOf((model.textModel.bias[best] - model.textModel.bias[second]) * factor));
		return evidence;
	}

	private static Map<String,Object> termEntry(TgfFeatures features, double[] term)
	{
		LinkedHashMap<String,Object> entry = new LinkedHashMap<String,Object>();
		int t = (int)term[0];
		entry.put("term", features.terms[t]);
		entry.put("contribution", Double.valueOf(term[1]));
		entry.put("count", Integer.valueOf((int)term[2]));
		entry.put("weight", Double.valueOf(term[3]));
		entry.put("users", Integer.valueOf(features.termUsers[t]));
		return entry;
	}

	/**
	 * The labelled users whose labels reached the target through a graph: the
	 * propagated mass of class c at u is exactly sum over users v labelled c of
	 * M[u][v], so M[u][v] is v's influence. The most influential users of the
	 * predicted community and of the others, with what links them to the target.
	 */
	private static List<Object> neighbours(TgfModel model, TgfFeatures features, TgfLearning.Graph graph, int u,
			int best, boolean termTime)
	{
		final double[] influence = TgfLearning.influence(graph, u, TgfModel.ALPHA);
		double total = 0.0;
		ArrayList<Integer> labelled = new ArrayList<Integer>();
		for (int v = 0; v < influence.length; v++)
		{
			if (v != u && model.label[v] >= 0 && influence[v] > 0.0)
			{
				labelled.add(Integer.valueOf(v));
				total += influence[v];
			}
		}
		Collections.sort(labelled, new Comparator<Integer>()
		{
			public int compare(Integer a, Integer b)
			{
				int byInfluence = Double.compare(influence[b.intValue()], influence[a.intValue()]);
				return byInfluence != 0 ? byInfluence : Integer.compare(a.intValue(), b.intValue());
			}
		});
		ArrayList<Object> result = new ArrayList<Object>();
		for (int i = 0; i < labelled.size() && result.size() < NEIGHBOUR_EVIDENCE; i++)
		{
			int v = labelled.get(i).intValue();
			LinkedHashMap<String,Object> entry = new LinkedHashMap<String,Object>();
			entry.put("user", features.users[v]);
			entry.put("community", model.classes[model.label[v]]);
			entry.put("supports", Boolean.valueOf(model.label[v] == best));
			entry.put("influence", Double.valueOf(influence[v]));
			entry.put("share", Double.valueOf(total > 0.0 ? influence[v] / total : 0.0));
			entry.put("direct", Boolean.valueOf(isAdjacent(graph, u, v)));
			entry.put("shared", termTime ? sharedPairs(features, u, v, 4) : sharedTerms(features, u, v, 4));
			result.add(entry);
		}
		return result;
	}

	private static boolean isAdjacent(TgfLearning.Graph graph, int u, int v)
	{
		return Arrays.binarySearch(graph.adjacent[u], v) >= 0;
	}

	/** The target's activity per month and the months and hours that move the activity model most. */
	private static Map<String,Object> activityEvidence(TgfModel model, TgfFeatures features, int u, int best,
			int second)
	{
		LinkedHashMap<String,Object> evidence = new LinkedHashMap<String,Object>();
		if (!features.temporal())
		{
			evidence.put("available", Boolean.FALSE);
			return evidence;
		}
		evidence.put("available", Boolean.TRUE);
		evidence.put("monthly", toList(features.monthly[u]));
		evidence.put("hourly", toList(features.hourly[u]));
		evidence.put("weight", Double.valueOf(model.weights[TgfModel.ACTIVITY]));
		if (model.activityModel == null || !model.active[TgfModel.ACTIVITY] || model.weights[TgfModel.ACTIVITY] <= 0.0)
		{
			//The stack gives the activity model no weight: when the target tweets moves nothing.
			evidence.put("drivers", new ArrayList<Object>());
			return evidence;
		}
		double factor = model.weights[TgfModel.ACTIVITY] / model.scale[TgfModel.ACTIVITY];
		TgfFeatures.Sparse rows = features.activity;
		final ArrayList<double[]> items = new ArrayList<double[]>();
		for (int p = rows.pointer[u]; p < rows.pointer[u + 1]; p++)
		{
			int f = rows.column[p];
			double contribution = rows.value[p]
					* (model.activityModel.weights[best][f] - model.activityModel.weights[second][f]) * factor;
			items.add(new double[] {f, contribution, rows.value[p]});
		}
		Collections.sort(items, new Comparator<double[]>()
		{
			public int compare(double[] a, double[] b)
			{
				int byMagnitude = Double.compare(Math.abs(b[1]), Math.abs(a[1]));
				return byMagnitude != 0 ? byMagnitude : Double.compare(a[0], b[0]);
			}
		});
		ArrayList<Object> drivers = new ArrayList<Object>();
		for (int i = 0; i < items.size() && drivers.size() < 6; i++)
		{
			int f = (int)items.get(i)[0];
			LinkedHashMap<String,Object> entry = new LinkedHashMap<String,Object>();
			entry.put("label", activityLabel(features, f));
			entry.put("share", Double.valueOf(items.get(i)[2]));
			entry.put("contribution", Double.valueOf(items.get(i)[1]));
			drivers.add(entry);
		}
		evidence.put("drivers", drivers);
		return evidence;
	}

	private static String activityLabel(TgfFeatures features, int feature)
	{
		if (feature < features.months)
		{
			return features.monthLabel(feature);
		}
		if (feature < features.months + 24)
		{
			return String.format(Locale.US, "%02d:00-%02d:59 UTC", Integer.valueOf(feature - features.months),
					Integer.valueOf(feature - features.months));
		}
		String[] days = {"Mondays", "Tuesdays", "Wednesdays", "Thursdays", "Fridays", "Saturdays", "Sundays"};
		return days[Math.max(0, Math.min(6, feature - features.months - 24))];
	}

	/**
	 * The fewest of the target's terms whose removal from the text model's
	 * input (the vector renormalised, every other learner's evidence held
	 * fixed) changes the predicted community: terms are removed in order of
	 * their support for the prediction, up to {@value #COUNTERFACTUAL_TERMS}.
	 */
	private static Map<String,Object> counterfactual(TgfModel model, TgfFeatures features, int u, int best,
			int second)
	{
		LinkedHashMap<String,Object> result = new LinkedHashMap<String,Object>();
		if (model.textModel == null || !model.active[TgfModel.TEXT] || model.weights[TgfModel.TEXT] <= 0.0)
		{
			result.put("found", Boolean.FALSE);
			result.put("note", "The text model carries no weight in the stack, so removing words cannot change the"
					+ " prediction.");
			return result;
		}
		int classes = model.classCount();
		TgfFeatures.Sparse text = features.text;
		int start = text.pointer[u];
		int end = text.pointer[u + 1];
		final double[] support = new double[end - start];
		Integer[] order = new Integer[end - start];
		for (int p = start; p < end; p++)
		{
			int t = text.column[p];
			support[p - start] = text.value[p] * (model.textModel.weights[best][t] - model.textModel.weights[second][t]);
			order[p - start] = Integer.valueOf(p - start);
		}
		Arrays.sort(order, new Comparator<Integer>()
		{
			public int compare(Integer a, Integer b)
			{
				int bySupport = Double.compare(support[b.intValue()], support[a.intValue()]);
				return bySupport != 0 ? bySupport : Integer.compare(a.intValue(), b.intValue());
			}
		});
		double[] others = new double[classes];
		for (int c = 0; c < classes; c++)
		{
			others[c] = model.bias[c];
			for (int channel = 0; channel < TgfModel.CHANNELS; channel++)
			{
				if (channel != TgfModel.TEXT)
				{
					others[c] += model.contribution(channel, u, c);
				}
			}
		}
		boolean[] removed = new boolean[end - start];
		ArrayList<Object> terms = new ArrayList<Object>();
		double factor = model.weights[TgfModel.TEXT] / model.scale[TgfModel.TEXT];
		for (int step = 0; step < Math.min(COUNTERFACTUAL_TERMS, order.length); step++)
		{
			int index = order[step].intValue();
			if (support[index] <= 0.0)
			{
				break;
			}
			removed[index] = true;
			terms.add(features.terms[text.column[start + index]]);
			//The text model's logits on the remaining terms, renormalised to unit length.
			double norm = 0.0;
			for (int i = 0; i < removed.length; i++)
			{
				if (!removed[i])
				{
					norm += text.value[start + i] * text.value[start + i];
				}
			}
			norm = Math.sqrt(norm);
			double[] logits = model.textModel.bias.clone();
			if (norm > 0.0)
			{
				for (int i = 0; i < removed.length; i++)
				{
					if (removed[i])
					{
						continue;
					}
					int t = text.column[start + i];
					double x = text.value[start + i] / norm;
					for (int c = 0; c < classes; c++)
					{
						logits[c] += model.textModel.weights[c][t] * x;
					}
				}
			}
			double[] centred = TgfModel.centred(logits);
			double[] final_ = new double[classes];
			for (int c = 0; c < classes; c++)
			{
				final_[c] = others[c] + factor * centred[c];
			}
			double[] q = TgfLearning.softmax(final_);
			int now = TgfModel.argmax(q);
			if (now != best)
			{
				result.put("found", Boolean.TRUE);
				result.put("terms", terms);
				result.put("flipsTo", model.classes[now]);
				result.put("probability", Double.valueOf(q[now]));
				result.put("originalProbability", Double.valueOf(q[best]));
				result.put("note", "Removing " + (terms.size() == 1 ? "this word" : "these " + terms.size() + " words")
						+ " from the target's text (every other learner's evidence held fixed) makes "
						+ model.classes[now] + " the most likely community.");
				return result;
			}
		}
		result.put("found", Boolean.FALSE);
		result.put("terms", terms);
		result.put("note", "No set of up to " + COUNTERFACTUAL_TERMS + " of the target's most supportive words changes"
				+ " the prediction on its own: the other learners' evidence is decisive.");
		return result;
	}

	private static List<Object> recommendations(TgfModel model, TgfFeatures features, int u, Integer[] order,
			double[] together, double[] similarity)
	{
		ArrayList<Object> result = new ArrayList<Object>();
		for (int i = 0; i < order.length && result.size() < RECOMMENDATION_EVIDENCE; i++)
		{
			int v = order[i].intValue();
			LinkedHashMap<String,Object> entry = new LinkedHashMap<String,Object>();
			entry.put("user", features.users[v]);
			entry.put("community", model.label[v] >= 0 ? model.classes[model.label[v]] : null);
			entry.put("probability", Double.valueOf(together[v]));
			entry.put("similarity", Double.valueOf(similarity[v]));
			entry.put("shared", sharedTerms(features, u, v, 3));
			if (model.truth[u] >= 0 && model.truth[v] >= 0)
			{
				entry.put("relevant", Boolean.valueOf(model.truth[u] == model.truth[v]));
			}
			result.add(entry);
		}
		return result;
	}

	private static String headline(TgfModel model, TgfFeatures features, int u, int best, int second, boolean[] set,
			List<Object> learners)
	{
		double[] p = model.posterior[u];
		String lead = null;
		double strongest = 0.0;
		for (Object item : learners)
		{
			@SuppressWarnings("unchecked")
			Map<String,Object> learner = (Map<String,Object>)item;
			double contribution = ((Double)learner.get("contribution")).doubleValue();
			if (contribution > strongest)
			{
				strongest = contribution;
				lead = (String)learner.get("name");
			}
		}
		int setSize = 0;
		for (boolean in : set)
		{
			setSize += in ? 1 : 0;
		}
		return features.users[u] + " most likely follows " + model.classes[best] + " (p = " + fixed(p[best])
				+ ", ahead of " + model.classes[second] + " at " + fixed(p[second]) + ")"
				+ (lead == null ? "" : "; the " + lead.toLowerCase(Locale.US) + " contributes most")
				+ ". The " + (int)Math.round(100 * (1 - TgfModel.CONFORMAL_ALPHA)) + "% conformal set holds "
				+ setSize + (setSize == 1 ? " account." : " accounts.");
	}

	// ============================================================ model card

	private static LinkedHashMap<String,Object> modelCard(Input input, TgfFeatures features, TgfModel model)
	{
		LinkedHashMap<String,Object> card = new LinkedHashMap<String,Object>();
		card.put("format", FORMAT);
		card.put("runId", input.runId);
		card.put("dataset", input.datasetName);
		card.put("corpus", input.datasetFile == null ? null : input.datasetFile.getName());
		card.put("supervised", Boolean.valueOf(model.supervised));
		card.put("note", model.note);
		ArrayList<Object> warnings = new ArrayList<Object>();
		if (!features.warning.isEmpty())
		{
			warnings.add(features.warning);
		}
		card.put("warnings", warnings);
		LinkedHashMap<String,Object> data = new LinkedHashMap<String,Object>();
		int labelled = 0;
		int targets = 0;
		for (int u = 0; u < features.size(); u++)
		{
			labelled += model.label[u] >= 0 ? 1 : 0;
			targets += model.target[u] ? 1 : 0;
		}
		data.put("users", Integer.valueOf(features.size()));
		data.put("labelledUsers", Integer.valueOf(labelled));
		data.put("targets", Integer.valueOf(targets));
		data.put("terms", Integer.valueOf(features.terms.length));
		data.put("termMonthPairs", Integer.valueOf(features.termTime.columns));
		data.put("months", Integer.valueOf(features.months));
		data.put("firstMonth", features.months > 0 ? features.monthLabel(0) : null);
		data.put("lastMonth", features.months > 0 ? features.monthLabel(features.months - 1) : null);
		data.put("rowsScanned", Long.valueOf(features.rowsScanned));
		data.put("datedRows", Long.valueOf(features.datedRows));
		data.put("fromCorpus", Boolean.valueOf(features.fromCorpus));
		card.put("data", data);
		card.put("learners", learnerCards(model));
		card.put("communities", communityCards(model, features));
		card.put("validation", validationCard(model));
		LinkedHashMap<String,Object> graphs = new LinkedHashMap<String,Object>();
		graphs.put("neighbours", Integer.valueOf(TgfModel.NEIGHBOURS));
		graphs.put("alpha", Double.valueOf(TgfModel.ALPHA));
		graphs.put("textEdges", Long.valueOf(model.textGraph.edges()));
		graphs.put("termTimeEdges", Long.valueOf(model.termTimeGraph == null ? 0L : model.termTimeGraph.edges()));
		graphs.put("activityEdges", Long.valueOf(model.activityGraph == null ? 0L : model.activityGraph.edges()));
		card.put("graphs", graphs);
		LinkedHashMap<String,Object> fit = new LinkedHashMap<String,Object>();
		fit.put("millis", Long.valueOf(model.trainMillis));
		fit.put("folds", Integer.valueOf(model.folds));
		fit.put("textIterations", Integer.valueOf(model.textModel == null ? 0 : model.textModel.iterations));
		fit.put("textConverged", Boolean.valueOf(model.textModel != null && model.textModel.converged));
		fit.put("activityIterations", Integer.valueOf(model.activityModel == null ? 0 : model.activityModel.iterations));
		fit.put("conformalAlpha", Double.valueOf(TgfModel.CONFORMAL_ALPHA));
		fit.put("conformalThreshold", Double.valueOf(model.conformalThreshold));
		card.put("fit", fit);
		return card;
	}

	private static List<Object> learnerCards(TgfModel model)
	{
		ArrayList<Object> learners = new ArrayList<Object>();
		for (int channel = 0; channel < TgfModel.CHANNELS; channel++)
		{
			LinkedHashMap<String,Object> learner = new LinkedHashMap<String,Object>();
			learner.put("key", TgfModel.CHANNEL_KEYS[channel]);
			learner.put("name", TgfModel.CHANNEL_NAMES[channel]);
			learner.put("description", TgfModel.CHANNEL_DESCRIPTIONS[channel]);
			learner.put("active", Boolean.valueOf(model.active[channel]));
			learner.put("weight", Double.valueOf(model.weights[channel]));
			learner.put("scale", Double.valueOf(model.scale[channel]));
			learner.put("accuracy", Double.valueOf(model.validation.channelAccuracy[channel]));
			learner.put("logLoss", Double.valueOf(model.validation.channelLogLoss[channel]));
			learners.add(learner);
		}
		return learners;
	}

	private static List<Object> communityCards(TgfModel model, TgfFeatures features)
	{
		ArrayList<Object> communities = new ArrayList<Object>();
		int classes = model.classCount();
		int[] sizes = new int[classes];
		double[][] monthly = new double[classes][features.months];
		double[][] hourly = new double[classes][features.temporal() ? 24 : 0];
		for (int u = 0; u < features.size(); u++)
		{
			int c = model.label[u];
			if (c < 0)
			{
				continue;
			}
			sizes[c]++;
			for (int m = 0; m < features.months; m++)
			{
				monthly[c][m] += features.monthly[u][m];
			}
			for (int h = 0; h < hourly[c].length; h++)
			{
				hourly[c][h] += features.hourly[u][h];
			}
		}
		int labelled = 0;
		for (int size : sizes)
		{
			labelled += size;
		}
		for (int c = 0; c < classes; c++)
		{
			LinkedHashMap<String,Object> community = new LinkedHashMap<String,Object>();
			community.put("name", model.classes[c]);
			community.put("labelled", Integer.valueOf(sizes[c]));
			community.put("share", Double.valueOf(labelled == 0 ? 0.0 : sizes[c] / (double)labelled));
			community.put("prior", Double.valueOf(model.supervised ? model.bias[c] : 0.0));
			if (model.supervised)
			{
				TgfModel.Validation v = model.validation;
				community.put("precision", Double.valueOf(v.classPrecision[c]));
				community.put("recall", Double.valueOf(v.classRecall[c]));
				community.put("f1", Double.valueOf(v.classF1[c]));
				community.put("support", Integer.valueOf(v.classSupport[c]));
			}
			community.put("terms", communityTerms(model, features, c));
			community.put("monthly", toList(shares(monthly[c])));
			community.put("hourly", toList(shares(hourly[c])));
			communities.add(community);
		}
		return communities;
	}

	/** The terms whose text-model weight for a community most exceeds their mean weight over communities. */
	private static List<Object> communityTerms(TgfModel model, TgfFeatures features, int c)
	{
		ArrayList<Object> result = new ArrayList<Object>();
		if (model.textModel == null)
		{
			return result;
		}
		final double[][] w = model.textModel.weights;
		int classes = w.length;
		final double[] distinct = new double[features.terms.length];
		Integer[] order = new Integer[distinct.length];
		for (int t = 0; t < distinct.length; t++)
		{
			double mean = 0.0;
			for (int k = 0; k < classes; k++)
			{
				mean += w[k][t];
			}
			distinct[t] = w[c][t] - mean / classes;
			order[t] = Integer.valueOf(t);
		}
		Arrays.sort(order, new Comparator<Integer>()
		{
			public int compare(Integer a, Integer b)
			{
				int byWeight = Double.compare(distinct[b.intValue()], distinct[a.intValue()]);
				return byWeight != 0 ? byWeight : Integer.compare(a.intValue(), b.intValue());
			}
		});
		for (int i = 0; i < Math.min(COMMUNITY_TERMS, order.length); i++)
		{
			int t = order[i].intValue();
			if (distinct[t] <= 0.0)
			{
				break;
			}
			LinkedHashMap<String,Object> entry = new LinkedHashMap<String,Object>();
			entry.put("term", features.terms[t]);
			entry.put("weight", Double.valueOf(distinct[t]));
			result.add(entry);
		}
		return result;
	}

	private static Map<String,Object> validationCard(TgfModel model)
	{
		TgfModel.Validation v = model.validation;
		LinkedHashMap<String,Object> card = new LinkedHashMap<String,Object>();
		card.put("users", Integer.valueOf(v.users));
		card.put("accuracy", Double.valueOf(v.accuracy));
		card.put("macroF1", Double.valueOf(v.macroF1));
		card.put("logLoss", Double.valueOf(v.logLoss));
		card.put("brier", Double.valueOf(v.brier));
		card.put("calibrationError", Double.valueOf(v.calibrationError));
		card.put("majorityAccuracy", Double.valueOf(v.majorityAccuracy));
		card.put("chanceAccuracy", Double.valueOf(v.chanceAccuracy));
		ArrayList<Object> reliability = new ArrayList<Object>();
		for (double[] bin : v.reliability)
		{
			reliability.add(toList(bin));
		}
		card.put("reliability", reliability);
		ArrayList<Object> confusion = new ArrayList<Object>();
		for (int[] row : v.confusion)
		{
			ArrayList<Object> line = new ArrayList<Object>();
			for (int value : row)
			{
				line.add(Integer.valueOf(value));
			}
			confusion.add(line);
		}
		card.put("confusion", confusion);
		card.put("conformalCoverage", Double.valueOf(v.conformalCoverage));
		card.put("conformalSetSize", Double.valueOf(v.conformalSetSize));
		return card;
	}

	// =============================================================== helpers

	private static List<String> sharedTerms(TgfFeatures features, int u, int v, int limit)
	{
		TgfFeatures.Sparse text = features.text;
		final ArrayList<double[]> shared = new ArrayList<double[]>();
		int i = text.pointer[u];
		int j = text.pointer[v];
		while (i < text.pointer[u + 1] && j < text.pointer[v + 1])
		{
			int a = text.column[i];
			int b = text.column[j];
			if (a == b)
			{
				shared.add(new double[] {a, text.value[i] * text.value[j]});
				i++;
				j++;
			}
			else if (a < b)
			{
				i++;
			}
			else
			{
				j++;
			}
		}
		Collections.sort(shared, new Comparator<double[]>()
		{
			public int compare(double[] a, double[] b)
			{
				int byProduct = Double.compare(b[1], a[1]);
				return byProduct != 0 ? byProduct : Double.compare(a[0], b[0]);
			}
		});
		ArrayList<String> terms = new ArrayList<String>();
		for (int k = 0; k < Math.min(limit, shared.size()); k++)
		{
			terms.add(features.terms[(int)shared.get(k)[0]]);
		}
		return terms;
	}

	/** (term, month) features two users share, strongest first, as "term (yyyy-MM)". */
	private static List<String> sharedPairs(TgfFeatures features, int u, int v, int limit)
	{
		TgfFeatures.Sparse pairs = features.termTime;
		final ArrayList<double[]> shared = new ArrayList<double[]>();
		int i = pairs.pointer[u];
		int j = pairs.pointer[v];
		while (i < pairs.pointer[u + 1] && j < pairs.pointer[v + 1])
		{
			int a = pairs.column[i];
			int b = pairs.column[j];
			if (a == b)
			{
				shared.add(new double[] {a, pairs.value[i] * pairs.value[j]});
				i++;
				j++;
			}
			else if (a < b)
			{
				i++;
			}
			else
			{
				j++;
			}
		}
		Collections.sort(shared, new Comparator<double[]>()
		{
			public int compare(double[] a, double[] b)
			{
				int byProduct = Double.compare(b[1], a[1]);
				return byProduct != 0 ? byProduct : Double.compare(a[0], b[0]);
			}
		});
		ArrayList<String> result = new ArrayList<String>();
		for (int k = 0; k < Math.min(limit, shared.size()); k++)
		{
			int feature = (int)shared.get(k)[0];
			result.add(features.terms[features.pairTerm[feature]] + " (" + features.monthLabel(features.pairMonth[feature])
					+ ")");
		}
		return result;
	}

	private static int[] rankClasses(final double[] p)
	{
		Integer[] order = new Integer[p.length];
		for (int c = 0; c < p.length; c++)
		{
			order[c] = Integer.valueOf(c);
		}
		Arrays.sort(order, new Comparator<Integer>()
		{
			public int compare(Integer a, Integer b)
			{
				int byP = Double.compare(p[b.intValue()], p[a.intValue()]);
				return byP != 0 ? byP : Integer.compare(a.intValue(), b.intValue());
			}
		});
		int[] ranked = new int[p.length];
		for (int i = 0; i < p.length; i++)
		{
			ranked[i] = order[i].intValue();
		}
		return ranked;
	}

	private static int rankOf(int[] ranked, int c)
	{
		for (int i = 0; i < ranked.length; i++)
		{
			if (ranked[i] == c)
			{
				return i + 1;
			}
		}
		return 0;
	}

	/** Shannon entropy over log(K): 0 when certain, 1 when uniform. */
	private static double entropy(double[] p)
	{
		double h = 0.0;
		for (double value : p)
		{
			if (value > 0.0)
			{
				h -= value * Math.log(value);
			}
		}
		return p.length < 2 ? 0.0 : h / Math.log(p.length);
	}

	private static double[] shares(double[] counts)
	{
		double total = 0.0;
		for (double value : counts)
		{
			total += value;
		}
		double[] result = new double[counts.length];
		for (int i = 0; i < counts.length; i++)
		{
			result[i] = total > 0.0 ? counts[i] / total : 0.0;
		}
		return result;
	}

	private static int activeCount(TgfModel model)
	{
		int count = 0;
		for (int channel = 0; channel < TgfModel.CHANNELS; channel++)
		{
			count += model.active[channel] ? 1 : 0;
		}
		return count;
	}

	static List<Object> toList(double[] values)
	{
		ArrayList<Object> list = new ArrayList<Object>();
		for (double value : values)
		{
			list.add(Double.valueOf(value));
		}
		return list;
	}

	private static String day(long millis)
	{
		SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
		format.setTimeZone(TimeZone.getTimeZone("UTC"));
		return format.format(new Date(millis));
	}

	private static String fixed(double value)
	{
		return String.format(Locale.US, "%.3f", Double.isFinite(value) ? value : 0.0);
	}

	private static String join(List<String> values)
	{
		StringBuilder builder = new StringBuilder();
		for (String value : values)
		{
			if (builder.length() > 0)
			{
				builder.append(", ");
			}
			builder.append(value);
		}
		return builder.toString();
	}

	static String methodologySummary()
	{
		return "TGF estimates the probability that the target follows each account with a cross-fitted Super Learner"
				+ " over six learners (text logistic regression, label propagation over the text and term-time graphs,"
				+ " an activity model and two neighbour votes) stacked by a non-negative logarithmic opinion pool, with"
				+ " 90% split-conformal prediction sets. Users are recommended by the probability of sharing the"
				+ " target's community; the target's own follow is used only to grade the result.";
	}

	// ================================================================ records

	static final class Input
	{
		final String runId;
		/** The run corpus (RunCorpus); without it the TF-IDF vectors are used and time is off. */
		final File datasetFile;
		final Map<String,? extends Map<String,Double>> tfidfDocuments;
		final Map<String,String> userFollowees;
		final Map<String,String> datasetUserFollowees;
		final List<String> targetUsers;
		final Map<String,Integer> userPrimaryRecServer;
		final int currentNode;
		final int numRecAgents;
		final int localResultLimit;
		/** The dataset's own name, shown instead of the run corpus's content-addressed file name. */
		final String datasetName;

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
			this.datasetName = datasetFile == null ? null : datasetFile.getName();
		}

		private Input(Input source, String datasetName)
		{
			this.runId = source.runId;
			this.datasetFile = source.datasetFile;
			this.tfidfDocuments = source.tfidfDocuments;
			this.userFollowees = source.userFollowees;
			this.datasetUserFollowees = source.datasetUserFollowees;
			this.targetUsers = source.targetUsers;
			this.userPrimaryRecServer = source.userPrimaryRecServer;
			this.currentNode = source.currentNode;
			this.numRecAgents = source.numRecAgents;
			this.localResultLimit = source.localResultLimit;
			this.datasetName = datasetName;
		}

		/** The same input naming the dataset the run read (its corpus file is named by a hash). */
		Input named(String name)
		{
			return new Input(this, name == null || name.trim().isEmpty() ? datasetName : name.trim());
		}

		static Input empty()
		{
			return new Input("", null, Collections.<String,Map<String,Double>>emptyMap(),
					Collections.<String,String>emptyMap(), Collections.<String,String>emptyMap(),
					Collections.<String>emptyList(), Collections.<String,Integer>emptyMap(), 1, 1, 0);
		}
	}

	/** One run's TGF result: the ranked lists and the report (model card and per-target explanations). */
	static final class Result
	{
		private final String runId;
		private final File datasetFile;
		private final String summary;
		private final long rowsScanned;
		private final long datedRows;
		private final TreeMap<String,TreeMap<String,Double>> scores;
		private final TreeMap<String,TreeMap<String,String>> details;
		private final TreeMap<String,TreeMap<String,Double>> accounts;
		private final TreeMap<String,TreeMap<String,String>> accountDetails;
		private final LinkedHashMap<String,Object> json;

		private Result(String runId, File datasetFile, String summary, long rowsScanned, long datedRows,
				TreeMap<String,TreeMap<String,Double>> scores, TreeMap<String,TreeMap<String,String>> details,
				TreeMap<String,TreeMap<String,Double>> accounts, TreeMap<String,TreeMap<String,String>> accountDetails,
				LinkedHashMap<String,Object> json)
		{
			this.runId = runId == null ? "" : runId;
			this.datasetFile = datasetFile;
			this.summary = summary == null ? "" : summary;
			this.rowsScanned = rowsScanned;
			this.datedRows = datedRows;
			this.scores = scores;
			this.details = details;
			this.accounts = accounts;
			this.accountDetails = accountDetails;
			this.json = json;
		}

		/** Each target's kept users and their probability of sharing its community (copies). */
		TreeMap<String,TreeMap<String,Double>> getScores()
		{
			return copy(scores);
		}

		TreeMap<String,TreeMap<String,String>> getDetails()
		{
			TreeMap<String,TreeMap<String,String>> copy = new TreeMap<String,TreeMap<String,String>>();
			for (Map.Entry<String,TreeMap<String,String>> entry : details.entrySet())
			{
				copy.put(entry.getKey(), new TreeMap<String,String>(entry.getValue()));
			}
			return copy;
		}

		/** Each target's accounts and the probability that it follows them (copies). */
		TreeMap<String,TreeMap<String,Double>> getAccountScores()
		{
			return copy(accounts);
		}

		TreeMap<String,TreeMap<String,String>> getAccountDetails()
		{
			TreeMap<String,TreeMap<String,String>> copy = new TreeMap<String,TreeMap<String,String>>();
			for (Map.Entry<String,TreeMap<String,String>> entry : accountDetails.entrySet())
			{
				copy.put(entry.getKey(), new TreeMap<String,String>(entry.getValue()));
			}
			return copy;
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

		/** The dataset's name (not the run corpus's hashed file name). */
		String getDatasetName()
		{
			Object name = json.get("dataset");
			return name == null ? (datasetFile == null ? "" : datasetFile.getName()) : name.toString();
		}

		long getRowsScanned()
		{
			return rowsScanned;
		}

		long getDatedRows()
		{
			return datedRows;
		}

		int targetCount()
		{
			return scores.size();
		}

		/** The report as JSON values (ordered maps, lists, numbers, strings): what the record stores. */
		Map<String,Object> toJson()
		{
			return Collections.unmodifiableMap(json);
		}

		private static TreeMap<String,TreeMap<String,Double>> copy(TreeMap<String,TreeMap<String,Double>> source)
		{
			TreeMap<String,TreeMap<String,Double>> copy = new TreeMap<String,TreeMap<String,Double>>();
			for (Map.Entry<String,TreeMap<String,Double>> entry : source.entrySet())
			{
				copy.put(entry.getKey(), new TreeMap<String,Double>(entry.getValue()));
			}
			return copy;
		}
	}
}
