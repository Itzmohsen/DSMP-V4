package TwitterGatherDataFollowers.userRyersonU;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Why a recommendation was made, for any algorithm of any run.
 *
 * A recommendation is explained against a reference model computed from the
 * run's own canonical corpus with the recommender agents' TF-IDF:
 *
 * <ul>
 * <li><b>Exact</b> where the reference is the algorithm's own scoring rule:
 *     Similarity (TF-IDF cosine), Common Neighbors (the run's link-prediction
 *     variant), and user rankers asked for accounts (an account scores its
 *     best-matching follower). The reported scores are reproduced and the
 *     explanation is the model's.</li>
 * <li><b>Measured</b> everywhere else (K-Means, SVM, MLP, Doc2Vec): the
 *     reference is shared vocabulary (TF-IDF cosine, or cosine to an account's
 *     community centroid) and its fidelity to the algorithm's actual list is
 *     measured by rank-biased overlap, overlap@k and Kendall's tau, so the
 *     studio says how much of the ranking shared words explain.</li>
 * </ul>
 *
 * For every recommendation: the exact additive term contributions of the
 * reference score; the effect of removing each word from the target's profile
 * and the fewest words whose removal costs the recommendation its place or its
 * place in the top k (exact for the reference, the corpus statistics held
 * fixed); the contrast with the rival recommendation; quoted tweets; when both
 * users wrote the decisive words; the ground truth; the run graph; and, for a
 * TGF run, TGF's own explanation.
 */
final class RecommendationEvidenceEngine
{
	private static final int TERM_LIMIT = 12;
	private static final int REMOVAL_LIMIT = 8;
	private static final int GREEDY_LIMIT = 300;
	private static final int QUOTE_LIMIT = 4;
	private static final double RBO_PERSISTENCE = 0.9;

	private RecommendationEvidenceEngine()
	{
	}

	// ================================================================ input

	static final class MatchEvidence
	{
		final int rank;
		final String name;
		final String score;
		final String detail;

		MatchEvidence(int rank, String name, String score, String detail)
		{
			this.rank = Math.max(1, rank);
			this.name = clean(name, "Unknown recommendation");
			this.score = score == null ? "" : score.trim();
			this.detail = detail == null ? "" : detail.trim();
		}

		double value()
		{
			return parse(score);
		}
	}

	/** One target's delivered recommendations. */
	static final class RecommendationEvent
	{
		final String runId;
		final String targetUser;
		final RecommendationCandidateType candidateType;
		final RecommendationResultStatus status;
		final String resultLabel;
		final String message;
		final List<MatchEvidence> matches;
		final long createdAtMillis;

		RecommendationEvent(String runId, String targetUser, RecommendationCandidateType candidateType,
				RecommendationResultStatus status, String resultLabel, String message, List<MatchEvidence> matches)
		{
			this.runId = runId == null ? "" : runId;
			this.targetUser = clean(targetUser, "Unknown target");
			this.candidateType = candidateType == null ? RecommendationCandidateType.USER : candidateType;
			this.status = status == null ? RecommendationResultStatus.OK : status;
			this.resultLabel = resultLabel == null ? "" : resultLabel;
			this.message = message == null ? "" : message;
			this.matches = matches == null ? Collections.<MatchEvidence>emptyList()
					: Collections.unmodifiableList(new ArrayList<MatchEvidence>(matches));
			this.createdAtMillis = System.currentTimeMillis();
		}
	}

	/**
	 * A run whose recommendations are explained: the delivered lists and what
	 * is needed to explain them (corpus, algorithm, run graph, TGF report).
	 */
	static final class EvidenceRun
	{
		final String runId;
		final int algorithm;
		final String algorithmName;
		final RecommendationCandidateType candidateType;
		final String datasetName;
		final File corpusFile;
		/** The run's record, null while the run is live. */
		final File record;
		final int cutoff;
		final String linkVariant;
		/** Each target's delivered list; results of a live run arrive while explanations are computed. */
		private final LinkedHashMap<String,RecommendationEvent> events = new LinkedHashMap<String,RecommendationEvent>();

		EvidenceRun(String runId, int algorithm, RecommendationCandidateType candidateType, String datasetName,
				File corpusFile, File record, int cutoff, String linkVariant)
		{
			this.runId = runId == null ? "" : runId;
			this.algorithm = algorithm;
			this.algorithmName = RecommendationModeSupport.algorithmName(algorithm);
			this.candidateType = candidateType == null ? RecommendationCandidateType.USER : candidateType;
			this.datasetName = datasetName == null ? "" : datasetName;
			this.corpusFile = corpusFile;
			this.record = record;
			this.cutoff = Math.max(1, cutoff);
			this.linkVariant = linkVariant == null ? AlgorithmParameterSettings.DEFAULT_CN_VARIANT : linkVariant;
		}

		boolean live()
		{
			return record == null;
		}

		synchronized void add(RecommendationEvent event)
		{
			events.put(event.targetUser, event);
		}

		synchronized List<String> targets()
		{
			return new ArrayList<String>(events.keySet());
		}

		synchronized RecommendationEvent event(String target)
		{
			return events.get(target);
		}

		synchronized List<RecommendationEvent> eventList()
		{
			return new ArrayList<RecommendationEvent>(events.values());
		}

		synchronized int size()
		{
			return events.size();
		}

		/** The run as its record stores it: recommendations.csv, the manifest and quality.json. */
		static EvidenceRun fromRecord(File directory) throws IOException
		{
			Object manifest = JsonText.read(new File(directory, "manifest.json"));
			String runId = JsonText.string(manifest, "runId");
			String algorithmName = JsonText.string(manifest, "properties.algorithm");
			String datasetName = JsonText.string(manifest, "properties.datasetName");
			String corpusName = JsonText.string(manifest, "properties.corpus");
			File qualityFile = new File(directory, "quality.json");
			Object quality = qualityFile.isFile() ? JsonText.read(qualityFile) : null;
			RecommendationCandidateType type = quality != null
					&& "followee".equalsIgnoreCase(JsonText.string(quality, "candidateType"))
					? RecommendationCandidateType.FOLLOWEE : RecommendationCandidateType.USER;
			int cutoff = quality == null ? 3 : (int)Math.max(1, JsonText.number(quality, "k"));
			File corpus = corpusName == null || corpusName.isEmpty() ? null
					: new File(RunCorpus.defaultDirectory(), corpusName);
			String linkVariant = JsonText.string(manifest, "properties.linkVariant");
			EvidenceRun run = new EvidenceRun(runId, algorithmCode(algorithmName), type, datasetName,
					corpus != null && corpus.isFile() ? corpus : null, directory, cutoff,
					linkVariant == null || linkVariant.isEmpty() ? AlgorithmParameterSettings.DEFAULT_CN_VARIANT
							: linkVariant);
			TreeMap<String,TreeMap<Integer,String[]>> lists = new TreeMap<String,TreeMap<Integer,String[]>>();
			ArrayList<String> order = new ArrayList<String>();
			File recommendations = new File(directory, "recommendations.csv");
			if (recommendations.isFile())
			{
				for (Map<String,String> row : JsonText.readCsv(recommendations))
				{
					String target = row.get("target");
					if (!lists.containsKey(target))
					{
						lists.put(target, new TreeMap<Integer,String[]>());
						order.add(target);
					}
					lists.get(target).put(Integer.valueOf(JsonText.parseInt(row.get("rank"), 0)),
							new String[] {row.get("candidate"), row.get("score")});
				}
			}
			for (String target : order)
			{
				ArrayList<MatchEvidence> matches = new ArrayList<MatchEvidence>();
				for (Map.Entry<Integer,String[]> entry : lists.get(target).entrySet())
				{
					matches.add(new MatchEvidence(entry.getKey().intValue(), entry.getValue()[0], entry.getValue()[1],
							""));
				}
				run.add(new RecommendationEvent(runId, target, type, RecommendationResultStatus.OK,
						algorithmName, "", matches));
			}
			return run;
		}
	}

	static int algorithmCode(String name)
	{
		for (int code = 0; code <= 8; code++)
		{
			if (RecommendationModeSupport.algorithmName(code).equalsIgnoreCase(name == null ? "" : name.trim()))
			{
				return code;
			}
		}
		return -1;
	}

	// ========================================================== explanation

	/** How a recommendation is explained. */
	enum Reference
	{
		COSINE("TF-IDF cosine"),
		LINK("Link prediction"),
		CENTROID("Community centroid"),
		PROJECTION("Best follower");

		final String label;

		Reference(String label)
		{
			this.label = label;
		}
	}

	/** One term's exact part in the reference score. */
	static final class TermContribution
	{
		final String term;
		final double contribution;
		/** Share of the reference score. */
		final double share;
		final int targetCount;
		final int candidateCount;
		final int users;

		TermContribution(String term, double contribution, double share, int targetCount, int candidateCount,
				int users)
		{
			this.term = term;
			this.contribution = contribution;
			this.share = share;
			this.targetCount = targetCount;
			this.candidateCount = candidateCount;
			this.users = users;
		}
	}

	/** The reference after removing one word from the target's profile. */
	static final class Removal
	{
		final String term;
		final double score;
		final int rank;
		/** The candidate that takes the recommendation's place, or null when it keeps it. */
		final String overtakenBy;

		Removal(String term, double score, int rank, String overtakenBy)
		{
			this.term = term;
			this.score = score;
			this.rank = rank;
			this.overtakenBy = overtakenBy;
		}
	}

	/** A tweet quoted as evidence, with the decisive words it contains. */
	static final class Quote
	{
		final String author;
		final String date;
		final String text;
		final List<String> terms;

		Quote(String author, String date, String text, List<String> terms)
		{
			this.author = author;
			this.date = date;
			this.text = text;
			this.terms = terms;
		}
	}

	/** TGF's own explanation of a target (on TGF runs). */
	static final class TgfView
	{
		final String predicted;
		final double probability;
		final List<String> set;
		final String truth;
		/** P(the candidate shares the target's community), or P(target follows the account). */
		final double candidateProbability;
		final List<String[]> learners;
		final String headline;

		TgfView(String predicted, double probability, List<String> set, String truth, double candidateProbability,
				List<String[]> learners, String headline)
		{
			this.predicted = predicted;
			this.probability = probability;
			this.set = set;
			this.truth = truth;
			this.candidateProbability = candidateProbability;
			this.learners = learners;
			this.headline = headline;
		}
	}

	/** Everything shown about one recommendation. */
	static final class Explanation
	{
		String runId = "";
		int algorithm = -1;
		String algorithmName = "";
		RecommendationCandidateType type = RecommendationCandidateType.USER;
		String target = "";
		String candidate = "";
		int rank;
		int listLength;
		String reportedScore = "";
		String targetCommunity;
		String candidateCommunity;
		Boolean relevant;
		boolean textAvailable;
		Reference reference = Reference.COSINE;
		/** What the reference score is, in words (for example "the link score of the best follower"). */
		String referenceMeaning = "";
		/** The reference reproduces the algorithm's own scoring rule. */
		boolean exact;
		String linkVariant;
		/** Largest relative difference between the reported and the reference scores (exact references). */
		double reproductionError = Double.NaN;
		double referenceScore = Double.NaN;
		int referenceRank;
		int candidates;
		/** The follower whose text scores an account (projection), or the closest follower (centroid). */
		String explainingUser;
		List<TermContribution> terms = new ArrayList<TermContribution>();
		int sharedTerms;
		double topShare = Double.NaN;
		double overlap = Double.NaN;
		double rbo = Double.NaN;
		double kendall = Double.NaN;
		String fidelityLabel = "";
		List<Removal> removals = new ArrayList<Removal>();
		List<String> loseRank = new ArrayList<String>();
		String loseRankTo;
		List<String> leaveTop = new ArrayList<String>();
		boolean leaveTopFound;
		String rival;
		int rivalRank;
		List<TermContribution> favouring = new ArrayList<TermContribution>();
		List<TermContribution> rivalFavouring = new ArrayList<TermContribution>();
		List<Quote> targetQuotes = new ArrayList<Quote>();
		List<Quote> candidateQuotes = new ArrayList<Quote>();
		List<String> sharedMoments = new ArrayList<String>();
		double activityOverlap = Double.NaN;
		int commonMonths;
		boolean graphAvailable;
		String graphRelation = "";
		String graphExposure = "";
		String graphPageRank = "";
		String graphAdoption = "";
		TgfView tgf;
		String verdict = "";
		List<String> warnings = new ArrayList<String>();
	}

	/**
	 * Explains the {@code index}-th recommendation (0-based) of a target.
	 * {@code graph} is the run's graph when known, {@code tgf} its TGF report.
	 */
	static Explanation explain(EvidenceRun run, String targetUser, int index, RunGraphRecord.Loaded graph,
			TgfReport tgf)
	{
		Explanation x = new Explanation();
		x.runId = run.runId;
		x.algorithm = run.algorithm;
		x.algorithmName = run.algorithmName;
		x.type = run.candidateType;
		x.linkVariant = run.linkVariant;
		RecommendationEvent event = run.event(targetUser);
		if (event == null || event.matches.isEmpty())
		{
			x.target = targetUser == null ? "" : targetUser;
			x.verdict = "No delivered recommendation for this target.";
			return x;
		}
		int selected = Math.max(0, Math.min(index, event.matches.size() - 1));
		MatchEvidence match = event.matches.get(selected);
		x.target = event.targetUser;
		x.candidate = match.name;
		x.rank = match.rank;
		x.listLength = event.matches.size();
		x.reportedScore = match.score;
		EvidenceCorpus corpus = null;
		if (run.corpusFile != null)
		{
			try
			{
				corpus = EvidenceCorpus.of(run.corpusFile);
			}
			catch (IOException | RuntimeException failure)
			{
				x.warnings.add("The run's corpus could not be read: " + failure.getMessage());
			}
		}
		else
		{
			x.warnings.add("This run's corpus is not available (records written before every run kept its corpus),"
					+ " so there is no text evidence: only the ground truth and the run graph.");
		}
		if (corpus != null && corpus.index.containsKey(x.target))
		{
			text(x, run, event, selected, corpus);
		}
		else if (corpus != null)
		{
			x.warnings.add(x.target + " has no document in the run's corpus.");
		}
		if (graph != null)
		{
			graph(x, graph);
		}
		if (tgf != null && run.runId.equals(tgf.runId))
		{
			x.tgf = tgfView(tgf, x);
		}
		x.verdict = verdict(x);
		return x;
	}

	// ----------------------------------------------------------------- text

	private static void text(Explanation x, EvidenceRun run, RecommendationEvent event, int selected,
			EvidenceCorpus corpus)
	{
		x.textAvailable = true;
		int u = corpus.index.get(x.target).intValue();
		x.targetCommunity = empty(corpus.followee[u]);
		boolean accounts = x.type == RecommendationCandidateType.FOLLOWEE;
		Scorer scorer = Scorer.of(run, corpus, u, accounts);
		x.reference = scorer.reference;
		x.referenceMeaning = scorer.meaning();
		x.exact = scorer.exact;
		x.candidates = scorer.size();
		int c = scorer.indexOf(x.candidate);
		if (accounts)
		{
			x.candidateCommunity = x.candidate;
			x.relevant = x.targetCommunity == null ? null : Boolean.valueOf(x.candidate.equals(x.targetCommunity));
		}
		else
		{
			Integer v = corpus.index.get(x.candidate);
			x.candidateCommunity = v == null ? null : empty(corpus.followee[v.intValue()]);
			x.relevant = x.targetCommunity == null || x.candidateCommunity == null ? null
					: Boolean.valueOf(x.targetCommunity.equals(x.candidateCommunity));
		}
		if (c < 0)
		{
			x.warnings.add(x.candidate + " is not a candidate of the reference (no document in the run's corpus).");
			return;
		}
		double[] scores = scorer.scores();
		x.referenceScore = scores[c];
		x.referenceRank = rankOf(scores, c, scorer);
		x.explainingUser = scorer.explainingUser(c);
		//Exact term decomposition of the reference score.
		List<TermContribution> all = scorer.contributions(c);
		x.sharedTerms = all.size();
		double top = 0.0;
		for (int i = 0; i < all.size(); i++)
		{
			if (i < 5)
			{
				top += all.get(i).contribution;
			}
		}
		x.topShare = x.referenceScore > 0.0 ? top / x.referenceScore : Double.NaN;
		x.terms = new ArrayList<TermContribution>(all.subList(0, Math.min(TERM_LIMIT, all.size())));
		//Fidelity: the delivered list against the reference ranking.
		fidelity(x, event, scorer, scores);
		//Counterfactuals.
		counterfactuals(x, event, scorer, c, all);
		//Contrast with the rival recommendation.
		MatchEvidence rival = event.matches.size() < 2 ? null : event.matches.get(selected == 0 ? 1 : 0);
		if (rival != null)
		{
			int r = scorer.indexOf(rival.name);
			if (r >= 0)
			{
				x.rival = rival.name;
				x.rivalRank = rival.rank;
				contrast(x, scorer, c, r);
			}
		}
		//Quotes, shared moments and activity.
		evidence(x, corpus, u, scorer, c);
	}

	/** 1 + candidates scoring higher (ties: earlier name first), as the algorithms rank. */
	private static int rankOf(double[] scores, int c, Scorer scorer)
	{
		int rank = 1;
		for (int i = 0; i < scores.length; i++)
		{
			if (i == c || !scorer.candidate(i))
			{
				continue;
			}
			if (scores[i] > scores[c] || (scores[i] == scores[c] && scorer.name(i).compareTo(scorer.name(c)) < 0))
			{
				rank++;
			}
		}
		return rank;
	}

	private static void fidelity(Explanation x, RecommendationEvent event, Scorer scorer, double[] scores)
	{
		int depth = event.matches.size();
		List<String> delivered = new ArrayList<String>();
		for (MatchEvidence match : event.matches)
		{
			delivered.add(match.name);
		}
		List<String> reference = scorer.top(scores, depth);
		x.overlap = overlap(delivered, reference);
		x.rbo = rbo(delivered, reference, RBO_PERSISTENCE);
		double[] reported = new double[depth];
		double[] referenceScores = new double[depth];
		boolean numeric = true;
		for (int i = 0; i < depth; i++)
		{
			reported[i] = event.matches.get(i).value();
			int c = scorer.indexOf(event.matches.get(i).name);
			referenceScores[i] = c < 0 ? 0.0 : scores[c];
			numeric &= !Double.isNaN(reported[i]);
		}
		x.kendall = numeric && depth >= 2 ? kendallTauB(reported, referenceScores) : Double.NaN;
		if (x.exact && numeric && depth >= 1 && reported[0] > 0.0 && referenceScores[0] > 0.0)
		{
			//Delivered scores may be rescaled (the top to 100): compare the ratios.
			double error = 0.0;
			for (int i = 0; i < depth; i++)
			{
				double a = reported[i] / reported[0];
				double b = referenceScores[i] / referenceScores[0];
				error = Math.max(error, Math.abs(a - b) / Math.max(1e-12, Math.abs(b)));
			}
			x.reproductionError = error;
		}
		x.fidelityLabel = x.exact ? (x.rbo > 0.999 ? "Exact: the reference is the algorithm's scoring rule"
				: "Exact rule, lists differ (ties or a changed corpus)")
				: x.rbo >= 0.8 ? "Shared words explain most of this ranking"
				: x.rbo >= 0.5 ? "Shared words explain part of this ranking"
				: x.rbo >= 0.2 ? "Shared words explain little of this ranking"
				: "Shared words do not explain this ranking";
	}

	private static void counterfactuals(Explanation x, RecommendationEvent event, Scorer scorer, int c,
			List<TermContribution> all)
	{
		int baseline = x.referenceRank;
		for (int i = 0; i < Math.min(REMOVAL_LIMIT, all.size()); i++)
		{
			Scorer.State state = scorer.start();
			state.remove(all.get(i).term);
			double[] scores = state.scores();
			int rank = rankOf(scores, c, scorer);
			String overtaken = rank > baseline ? overtaker(scores, c, scorer) : null;
			x.removals.add(new Removal(all.get(i).term, state.normalised(scores[c]), rank, overtaken));
		}
		Scorer.State state = scorer.start();
		ArrayList<String> removed = new ArrayList<String>();
		int k = Math.max(event.matches.size(), 1);
		for (int i = 0; i < Math.min(GREEDY_LIMIT, all.size()); i++)
		{
			state.remove(all.get(i).term);
			removed.add(all.get(i).term);
			double[] scores = state.scores();
			int rank = rankOf(scores, c, scorer);
			if (x.loseRank.isEmpty() && rank > baseline)
			{
				x.loseRank = new ArrayList<String>(removed);
				x.loseRankTo = overtaker(scores, c, scorer);
			}
			if (rank > k)
			{
				x.leaveTop = new ArrayList<String>(removed);
				x.leaveTopFound = true;
				break;
			}
		}
	}

	/** The candidate now directly above {@code c}. */
	private static String overtaker(double[] scores, int c, Scorer scorer)
	{
		int best = -1;
		for (int i = 0; i < scores.length; i++)
		{
			if (i == c || !scorer.candidate(i))
			{
				continue;
			}
			boolean above = scores[i] > scores[c]
					|| (scores[i] == scores[c] && scorer.name(i).compareTo(scorer.name(c)) < 0);
			if (above && (best < 0 || scores[i] < scores[best]
					|| (scores[i] == scores[best] && scorer.name(i).compareTo(scorer.name(best)) > 0)))
			{
				best = i;
			}
		}
		return best < 0 ? null : scorer.name(best);
	}

	private static void contrast(Explanation x, Scorer scorer, int c, int r)
	{
		HashMap<String,TermContribution> mine = new HashMap<String,TermContribution>();
		for (TermContribution term : scorer.contributions(c))
		{
			mine.put(term.term, term);
		}
		HashMap<String,TermContribution> theirs = new HashMap<String,TermContribution>();
		for (TermContribution term : scorer.contributions(r))
		{
			theirs.put(term.term, term);
		}
		TreeSet<String> union = new TreeSet<String>(mine.keySet());
		union.addAll(theirs.keySet());
		final HashMap<String,Double> difference = new HashMap<String,Double>();
		for (String term : union)
		{
			double a = mine.containsKey(term) ? mine.get(term).contribution : 0.0;
			double b = theirs.containsKey(term) ? theirs.get(term).contribution : 0.0;
			difference.put(term, Double.valueOf(a - b));
		}
		ArrayList<String> terms = new ArrayList<String>(union);
		Collections.sort(terms, new Comparator<String>()
		{
			public int compare(String a, String b)
			{
				int byDifference = Double.compare(difference.get(b).doubleValue(), difference.get(a).doubleValue());
				return byDifference != 0 ? byDifference : a.compareTo(b);
			}
		});
		for (int i = 0; i < terms.size() && x.favouring.size() < 6; i++)
		{
			double d = difference.get(terms.get(i)).doubleValue();
			if (d > 0.0)
			{
				TermContribution own = mine.get(terms.get(i));
				x.favouring.add(new TermContribution(terms.get(i), d, Double.NaN, own == null ? 0 : own.targetCount,
						own == null ? 0 : own.candidateCount, own == null ? 0 : own.users));
			}
		}
		for (int i = terms.size() - 1; i >= 0 && x.rivalFavouring.size() < 6; i--)
		{
			double d = difference.get(terms.get(i)).doubleValue();
			if (d < 0.0)
			{
				TermContribution other = theirs.get(terms.get(i));
				x.rivalFavouring.add(new TermContribution(terms.get(i), -d, Double.NaN,
						other == null ? 0 : other.targetCount, other == null ? 0 : other.candidateCount,
						other == null ? 0 : other.users));
			}
		}
	}

	/** Tweets quoting the decisive words, the months both users wrote them, and how their activity overlaps. */
	private static void evidence(Explanation x, EvidenceCorpus corpus, int u, Scorer scorer, int c)
	{
		final HashMap<String,Double> weight = new HashMap<String,Double>();
		for (TermContribution term : x.terms)
		{
			weight.put(term.term, Double.valueOf(term.contribution));
		}
		Integer author = x.explainingUser != null ? corpus.index.get(x.explainingUser)
				: x.type == RecommendationCandidateType.USER ? corpus.index.get(x.candidate) : null;
		try
		{
			List<EvidenceCorpus.Tweet> targetTweets = corpus.tweetsOf(u);
			x.targetQuotes = quotes(x.target, targetTweets, weight);
			if (author != null)
			{
				List<EvidenceCorpus.Tweet> candidateTweets = corpus.tweetsOf(author.intValue());
				x.candidateQuotes = quotes(corpus.users[author.intValue()], candidateTweets, weight);
				x.sharedMoments = moments(targetTweets, candidateTweets, weight);
				double[] overlap = activity(corpus.monthly.get(u), corpus.monthly.get(author.intValue()));
				x.activityOverlap = overlap[0];
				x.commonMonths = (int)overlap[1];
			}
		}
		catch (IOException failure)
		{
			x.warnings.add("Tweets could not be read back from the corpus: " + failure.getMessage());
		}
	}

	private static List<Quote> quotes(String author, List<EvidenceCorpus.Tweet> tweets, final Map<String,Double> weight)
	{
		final ArrayList<Object[]> scored = new ArrayList<Object[]>();
		for (int i = 0; i < tweets.size(); i++)
		{
			EvidenceCorpus.Tweet tweet = tweets.get(i);
			LinkedHashSetHolder terms = new LinkedHashSetHolder();
			double value = 0.0;
			for (String token : TgfFeatures.tokens(tweet.text))
			{
				Double w = weight.get(token);
				if (w != null && terms.add(token))
				{
					value += w.doubleValue();
				}
			}
			if (value > 0.0)
			{
				scored.add(new Object[] {Double.valueOf(value), Integer.valueOf(i), terms.list()});
			}
		}
		Collections.sort(scored, new Comparator<Object[]>()
		{
			public int compare(Object[] a, Object[] b)
			{
				int byValue = Double.compare(((Double)b[0]).doubleValue(), ((Double)a[0]).doubleValue());
				return byValue != 0 ? byValue : ((Integer)a[1]).compareTo((Integer)b[1]);
			}
		});
		ArrayList<Quote> result = new ArrayList<Quote>();
		for (int i = 0; i < Math.min(QUOTE_LIMIT, scored.size()); i++)
		{
			EvidenceCorpus.Tweet tweet = tweets.get(((Integer)scored.get(i)[1]).intValue());
			@SuppressWarnings("unchecked")
			List<String> terms = (List<String>)scored.get(i)[2];
			result.add(new Quote(author, tweet.time == Long.MIN_VALUE ? "undated" : day(tweet.time), tweet.text, terms));
		}
		return result;
	}

	/** Months in which both users wrote one of the decisive words, as "word (yyyy-MM)", strongest first. */
	private static List<String> moments(List<EvidenceCorpus.Tweet> a, List<EvidenceCorpus.Tweet> b,
			final Map<String,Double> weight)
	{
		Set<String> first = pairs(a, weight);
		final ArrayList<String> shared = new ArrayList<String>();
		for (String pair : pairs(b, weight))
		{
			if (first.contains(pair))
			{
				shared.add(pair);
			}
		}
		Collections.sort(shared, new Comparator<String>()
		{
			public int compare(String x, String y)
			{
				double wx = weight.get(x.substring(0, x.indexOf(' '))).doubleValue();
				double wy = weight.get(y.substring(0, y.indexOf(' '))).doubleValue();
				int byWeight = Double.compare(wy, wx);
				return byWeight != 0 ? byWeight : y.compareTo(x);
			}
		});
		return new ArrayList<String>(shared.subList(0, Math.min(10, shared.size())));
	}

	private static Set<String> pairs(List<EvidenceCorpus.Tweet> tweets, Map<String,Double> weight)
	{
		TreeSet<String> pairs = new TreeSet<String>();
		for (EvidenceCorpus.Tweet tweet : tweets)
		{
			if (tweet.time == Long.MIN_VALUE)
			{
				continue;
			}
			String month = day(tweet.time).substring(0, 7);
			for (String token : TgfFeatures.tokens(tweet.text))
			{
				if (weight.containsKey(token))
				{
					pairs.add(token + " (" + month + ")");
				}
			}
		}
		return pairs;
	}

	/** Cosine of two users' monthly activity, and the months both were active. */
	private static double[] activity(TreeMap<Integer,Integer> a, TreeMap<Integer,Integer> b)
	{
		double dot = 0.0;
		double na = 0.0;
		double nb = 0.0;
		int common = 0;
		for (Map.Entry<Integer,Integer> entry : a.entrySet())
		{
			double x = entry.getValue().doubleValue();
			na += x * x;
			Integer y = b.get(entry.getKey());
			if (y != null)
			{
				dot += x * y.doubleValue();
				common++;
			}
		}
		for (Integer y : b.values())
		{
			nb += y.doubleValue() * y.doubleValue();
		}
		return new double[] {na > 0.0 && nb > 0.0 ? dot / Math.sqrt(na * nb) : Double.NaN, common};
	}

	// ---------------------------------------------------------------- graph

	private static void graph(Explanation x, RunGraphRecord.Loaded loaded)
	{
		RunGraph graph = loaded.graph;
		int target = graph.indexOf(x.target);
		if (target < 0 || !graph.isTarget(target))
		{
			return;
		}
		RunGraphAnalysis analysis = loaded.analysis;
		x.graphAvailable = true;
		RunGraph.Edge adoption = graph.adoptionOf(target);
		int candidate = graph.indexOf(x.candidate);
		if (adoption != null)
		{
			Boolean inside = graph.inside(adoption);
			x.graphAdoption = x.target + " adopted " + graph.name(adoption.target) + " (rank " + adoption.rank + ", "
					+ (inside != null && inside.booleanValue() ? "inside its community" : "a bridge") + ")"
					+ (adoption.target == candidate ? ": this recommendation." : ".");
		}
		else
		{
			x.graphAdoption = x.target + " adopted nothing.";
		}
		Boolean inside = null;
		for (RunGraph.Edge edge : graph.recommendationsOf(target))
		{
			if (edge.target == candidate)
			{
				inside = graph.inside(edge);
			}
		}
		if (candidate < 0)
		{
			return;
		}
		int community = graph.candidateType == RecommendationCandidateType.FOLLOWEE
				? graph.anchoredCommunity(candidate) : graph.community(candidate);
		x.graphRelation = inside == null ? (community < 0 ? "Unknown community" : "Outside the top-" + graph.cutoff)
				: inside.booleanValue() ? "Inside the community" : "Bridges to " + graph.communityName(community);
		int exposureRank = 1;
		int candidates = 0;
		for (int node = 0; node < graph.size(); node++)
		{
			if (graph.isCandidate(node))
			{
				candidates++;
				exposureRank += analysis.exposureByNode[node] > analysis.exposureByNode[candidate] ? 1 : 0;
			}
		}
		x.graphExposure = "Recommended to " + count(analysis.recommendedIn[candidate])
				+ (analysis.recommendedIn[candidate] == 1 ? " target" : " targets") + " (#" + exposureRank + " of "
				+ count(candidates) + " by exposure)";
		int pageRankPosition = 1;
		for (int node = 0; node < graph.size(); node++)
		{
			pageRankPosition += analysis.pageRank[node] > analysis.pageRank[candidate] ? 1 : 0;
		}
		x.graphPageRank = "#" + pageRankPosition + " of " + count(graph.size()) + " by PageRank";
	}

	// ------------------------------------------------------------------ tgf

	private static TgfView tgfView(TgfReport report, Explanation x)
	{
		TgfReport.Target target = report.target(x.target);
		if (target == null || !target.supervised)
		{
			return null;
		}
		double candidateProbability = Double.NaN;
		if (x.type == RecommendationCandidateType.FOLLOWEE)
		{
			for (TgfReport.AccountScore account : target.accounts)
			{
				if (account.account.equals(x.candidate))
				{
					candidateProbability = account.probability;
				}
			}
		}
		else
		{
			for (TgfReport.Recommendation recommendation : target.recommendations)
			{
				if (recommendation.user.equals(x.candidate))
				{
					candidateProbability = recommendation.probability;
				}
			}
		}
		ArrayList<String[]> learners = new ArrayList<String[]>();
		for (TgfReport.TargetLearner learner : target.learners)
		{
			if (learner.active && learner.weight > 0.0)
			{
				learners.add(new String[] {learner.name, String.format(Locale.US, "%+.2f", learner.contribution),
						learner.says});
			}
		}
		return new TgfView(target.predicted, target.probability, target.set, target.truth, candidateProbability,
				learners, target.headline);
	}

	// -------------------------------------------------------------- verdict

	private static String verdict(Explanation x)
	{
		StringBuilder text = new StringBuilder();
		text.append(x.algorithmName).append(" ranked ").append(x.candidate).append(" #").append(x.rank)
				.append(" for ").append(x.target).append('.');
		if (x.textAvailable && !Double.isNaN(x.referenceScore))
		{
			if (x.exact)
			{
				text.append(" Its score is ").append(x.referenceMeaning).append(", ")
						.append(String.format(Locale.US, "%.4f", x.referenceScore));
			}
			else
			{
				text.append(" By shared vocabulary it would rank #").append(x.referenceRank).append(" of ")
						.append(count(x.candidates));
			}
			if (!x.terms.isEmpty())
			{
				text.append(", carried most by ‘").append(x.terms.get(0).term).append('’');
				if (x.terms.size() > 1)
				{
					text.append(" and ‘").append(x.terms.get(1).term).append('’');
				}
			}
			text.append('.');
			if (!x.exact && !Double.isNaN(x.rbo))
			{
				text.append(" ").append(x.fidelityLabel).append(" (rank-biased overlap ")
						.append(String.format(Locale.US, "%.2f", x.rbo)).append(").");
			}
		}
		if (x.tgf != null)
		{
			text.append(" TGF puts ").append(x.target).append(" in ").append(x.tgf.predicted).append(" (p = ")
					.append(String.format(Locale.US, "%.2f", x.tgf.probability)).append(").");
		}
		if (x.relevant != null)
		{
			text.append(x.relevant.booleanValue() ? " It is relevant: " : " It is not relevant: ")
					.append(x.target).append(" follows ").append(x.targetCommunity)
					.append(x.type == RecommendationCandidateType.USER && x.candidateCommunity != null
							? " and " + x.candidate + " follows " + x.candidateCommunity : "").append('.');
		}
		return text.toString();
	}

	// ========================================================== run analysis

	/** How the reference explains a whole run, and how relevance falls with rank. */
	static final class RunAnalysis
	{
		final int targets;
		final double meanRbo;
		final double meanOverlap;
		final double meanKendall;
		/** Per target: RBO. */
		final List<double[]> perTarget;
		final List<String> targetNames;
		/** Per rank: [relevant, delivered, chance]. */
		final double[][] calibration;
		final List<TermContribution> runTerms;
		final boolean exact;
		final Reference reference;
		final boolean textAvailable;

		RunAnalysis(int targets, double meanRbo, double meanOverlap, double meanKendall, List<double[]> perTarget,
				List<String> targetNames, double[][] calibration, List<TermContribution> runTerms, boolean exact,
				Reference reference, boolean textAvailable)
		{
			this.targets = targets;
			this.meanRbo = meanRbo;
			this.meanOverlap = meanOverlap;
			this.meanKendall = meanKendall;
			this.perTarget = perTarget;
			this.targetNames = targetNames;
			this.calibration = calibration;
			this.runTerms = runTerms;
			this.exact = exact;
			this.reference = reference;
			this.textAvailable = textAvailable;
		}
	}

	static RunAnalysis analyzeRun(EvidenceRun run)
	{
		List<RecommendationEvent> events = run.eventList();
		int k = 1;
		for (RecommendationEvent event : events)
		{
			k = Math.max(k, event.matches.size());
		}
		double[][] calibration = new double[k][3];
		ArrayList<double[]> perTarget = new ArrayList<double[]>();
		ArrayList<String> names = new ArrayList<String>();
		final HashMap<String,Double> termTotals = new HashMap<String,Double>();
		double rboSum = 0.0;
		double overlapSum = 0.0;
		double kendallSum = 0.0;
		int kendallCount = 0;
		int scored = 0;
		EvidenceCorpus corpus = null;
		try
		{
			corpus = run.corpusFile == null ? null : EvidenceCorpus.of(run.corpusFile);
		}
		catch (IOException | RuntimeException unreadable)
		{
			corpus = null;
		}
		Reference reference = Reference.COSINE;
		boolean exact = false;
		boolean accounts = run.candidateType == RecommendationCandidateType.FOLLOWEE;
		for (RecommendationEvent event : events)
		{
			if (corpus == null || !corpus.index.containsKey(event.targetUser) || event.matches.isEmpty())
			{
				continue;
			}
			int u = corpus.index.get(event.targetUser).intValue();
			Scorer scorer = Scorer.of(run, corpus, u, accounts);
			reference = scorer.reference;
			exact = scorer.exact;
			double[] scores = scorer.scores();
			List<String> delivered = new ArrayList<String>();
			double[] reported = new double[event.matches.size()];
			double[] referenceScores = new double[event.matches.size()];
			boolean numeric = true;
			for (int i = 0; i < event.matches.size(); i++)
			{
				MatchEvidence match = event.matches.get(i);
				delivered.add(match.name);
				reported[i] = match.value();
				numeric &= !Double.isNaN(reported[i]);
				int c = scorer.indexOf(match.name);
				referenceScores[i] = c < 0 ? 0.0 : scores[c];
			}
			List<String> top = scorer.top(scores, delivered.size());
			double rbo = rbo(delivered, top, RBO_PERSISTENCE);
			double overlap = overlap(delivered, top);
			rboSum += rbo;
			overlapSum += overlap;
			if (numeric && delivered.size() >= 2)
			{
				double tau = kendallTauB(reported, referenceScores);
				if (!Double.isNaN(tau))
				{
					kendallSum += tau;
					kendallCount++;
				}
			}
			perTarget.add(new double[] {rbo, overlap});
			names.add(event.targetUser);
			scored++;
			//Relevance by rank, and its chance level.
			String community = empty(corpus.followee[u]);
			if (community != null)
			{
				double chance = scorer.relevantShare(community);
				for (int i = 0; i < event.matches.size(); i++)
				{
					String name = event.matches.get(i).name;
					boolean relevant;
					if (accounts)
					{
						relevant = community.equals(name);
					}
					else
					{
						Integer v = corpus.index.get(name);
						relevant = v != null && community.equals(corpus.followee[v.intValue()]);
					}
					calibration[i][0] += relevant ? 1.0 : 0.0;
					calibration[i][1] += 1.0;
					calibration[i][2] += chance;
				}
			}
			//The words behind the first recommendation of every target.
			int c = scorer.indexOf(event.matches.get(0).name);
			if (c >= 0)
			{
				for (TermContribution term : scorer.contributions(c))
				{
					Double old = termTotals.get(term.term);
					termTotals.put(term.term, Double.valueOf((old == null ? 0.0 : old.doubleValue()) + term.contribution));
				}
			}
		}
		for (double[] row : calibration)
		{
			if (row[1] > 0.0)
			{
				row[2] /= row[1];
			}
		}
		ArrayList<String> terms = new ArrayList<String>(termTotals.keySet());
		Collections.sort(terms, new Comparator<String>()
		{
			public int compare(String a, String b)
			{
				int byTotal = Double.compare(termTotals.get(b).doubleValue(), termTotals.get(a).doubleValue());
				return byTotal != 0 ? byTotal : a.compareTo(b);
			}
		});
		ArrayList<TermContribution> runTerms = new ArrayList<TermContribution>();
		for (int i = 0; i < Math.min(15, terms.size()); i++)
		{
			runTerms.add(new TermContribution(terms.get(i), termTotals.get(terms.get(i)).doubleValue(), Double.NaN, 0, 0,
					corpus == null ? 0 : corpus.documentFrequency[corpus.termIndex.get(terms.get(i)).intValue()]));
		}
		return new RunAnalysis(scored, scored == 0 ? Double.NaN : rboSum / scored,
				scored == 0 ? Double.NaN : overlapSum / scored, kendallCount == 0 ? Double.NaN : kendallSum / kendallCount,
				perTarget, names, calibration, runTerms, exact, reference, corpus != null);
	}

	// ============================================================ reference

	/**
	 * The reference model for one target: scores of every candidate, exact
	 * term contributions, and incremental removal of the target's words.
	 */
	abstract static class Scorer
	{
		final EvidenceCorpus corpus;
		final int target;
		Reference reference;
		boolean exact;

		Scorer(EvidenceCorpus corpus, int target)
		{
			this.corpus = corpus;
			this.target = target;
		}

		static Scorer of(EvidenceRun run, EvidenceCorpus corpus, int target, boolean accounts)
		{
			int algorithm = run.algorithm;
			boolean userRanker = algorithm == RecommendationModeSupport.COS_SIM
					|| algorithm == RecommendationModeSupport.COMMON_NEIGHBORS
					|| algorithm == RecommendationModeSupport.K_MEANS
					|| algorithm == RecommendationModeSupport.K_MEANS_EUCLIDEAN
					|| algorithm == RecommendationModeSupport.DOC2VEC;
			Scorer users = algorithm == RecommendationModeSupport.COMMON_NEIGHBORS
					? new LinkScorer(corpus, target, run.linkVariant) : new CosineScorer(corpus, target);
			users.exact = algorithm == RecommendationModeSupport.COS_SIM
					|| algorithm == RecommendationModeSupport.COMMON_NEIGHBORS;
			if (!accounts)
			{
				return users;
			}
			if (userRanker)
			{
				//The run projected the user ranking onto accounts: the best follower's score.
				ProjectionScorer projection = new ProjectionScorer(corpus, target, users);
				projection.exact = users.exact;
				return projection;
			}
			CentroidScorer centroid = new CentroidScorer(corpus, target);
			centroid.exact = false;
			return centroid;
		}

		/** Number of candidates (users or accounts). */
		abstract int size();

		abstract String name(int candidate);

		abstract int indexOf(String name);

		/** Whether an index is a candidate (the target itself is not). */
		boolean candidate(int index)
		{
			return true;
		}

		abstract double[] scores();

		/** Exact additive contributions of the target's terms to a candidate's score, largest first. */
		abstract List<TermContribution> contributions(int candidate);

		/** The user whose text stands for an account candidate (null for user candidates). */
		String explainingUser(int candidate)
		{
			return null;
		}

		/** Share of the candidates relevant to a community (the chance rate of a random list). */
		abstract double relevantShare(String community);

		/** What the score is, in words. */
		String meaning()
		{
			switch (reference)
			{
				case LINK:
					return "the link score of the terms both users write";
				case CENTROID:
					return "the TF-IDF cosine with the centroid of the account's followers";
				default:
					return "the TF-IDF cosine of the two users";
			}
		}

		abstract State start();

		/** The reference with some of the target's words removed (the corpus statistics held fixed). */
		abstract static class State
		{
			abstract void remove(String term);

			abstract double[] scores();

			double normalised(double score)
			{
				return score;
			}
		}

		List<String> top(final double[] scores, int depth)
		{
			Integer[] order = new Integer[size()];
			int m = 0;
			for (int i = 0; i < size(); i++)
			{
				if (candidate(i))
				{
					order[m++] = Integer.valueOf(i);
				}
			}
			order = Arrays.copyOf(order, m);
			Arrays.sort(order, new Comparator<Integer>()
			{
				public int compare(Integer a, Integer b)
				{
					int byScore = Double.compare(scores[b.intValue()], scores[a.intValue()]);
					return byScore != 0 ? byScore : name(a.intValue()).compareTo(name(b.intValue()));
				}
			});
			ArrayList<String> result = new ArrayList<String>();
			for (int i = 0; i < Math.min(depth, order.length); i++)
			{
				result.add(name(order[i].intValue()));
			}
			return result;
		}

		TermContribution contribution(String term, double value, double total, int candidateUser)
		{
			int t = corpus.termIndex.get(term).intValue();
			return new TermContribution(term, value, total > 0.0 ? value / total : Double.NaN,
					corpus.countOf(target, t), candidateUser < 0 ? 0 : corpus.countOf(candidateUser, t),
					corpus.documentFrequency[t]);
		}

		static List<TermContribution> sorted(List<TermContribution> terms)
		{
			Collections.sort(terms, new Comparator<TermContribution>()
			{
				public int compare(TermContribution a, TermContribution b)
				{
					int byValue = Double.compare(b.contribution, a.contribution);
					return byValue != 0 ? byValue : a.term.compareTo(b.term);
				}
			});
			return terms;
		}
	}

	/** Candidates are users; the score is the TF-IDF cosine (exactly the Similarity algorithm's). */
	static final class CosineScorer extends Scorer
	{
		CosineScorer(EvidenceCorpus corpus, int target)
		{
			super(corpus, target);
			reference = Reference.COSINE;
		}

		int size()
		{
			return corpus.size();
		}

		String name(int candidate)
		{
			return corpus.users[candidate];
		}

		int indexOf(String name)
		{
			Integer index = corpus.index.get(name);
			return index == null || index.intValue() == target ? -1 : index.intValue();
		}

		boolean candidate(int index)
		{
			return index != target;
		}

		double[] scores()
		{
			return corpus.cosines(target);
		}

		List<TermContribution> contributions(int candidate)
		{
			ArrayList<TermContribution> terms = new ArrayList<TermContribution>();
			double total = 0.0;
			ArrayList<double[]> raw = new ArrayList<double[]>();
			int i = corpus.pointer[target];
			int j = corpus.pointer[candidate];
			while (i < corpus.pointer[target + 1] && j < corpus.pointer[candidate + 1])
			{
				int a = corpus.column[i];
				int b = corpus.column[j];
				if (a == b)
				{
					double value = corpus.weight[i] * corpus.weight[j];
					if (value > 0.0)
					{
						raw.add(new double[] {a, value});
						total += value;
					}
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
			for (double[] term : raw)
			{
				terms.add(contribution(corpus.terms[(int)term[0]], term[1], total, candidate));
			}
			return sorted(terms);
		}

		double relevantShare(String community)
		{
			int relevant = 0;
			int candidates = 0;
			for (int v = 0; v < corpus.size(); v++)
			{
				if (v == target)
				{
					continue;
				}
				candidates++;
				relevant += community.equals(corpus.followee[v]) ? 1 : 0;
			}
			return candidates == 0 ? 0.0 : relevant / (double)candidates;
		}

		State start()
		{
			final double[] dots = scores();
			return new State()
			{
				double removedSquares;

				void remove(String term)
				{
					Integer t = corpus.termIndex.get(term);
					if (t == null)
					{
						return;
					}
					double a = corpus.weightOf(target, t.intValue());
					if (a <= 0.0)
					{
						return;
					}
					removedSquares += a * a;
					for (int q = corpus.postingPointer[t.intValue()]; q < corpus.postingPointer[t.intValue() + 1]; q++)
					{
						int v = corpus.postingUser[q];
						if (v != target)
						{
							dots[v] -= a * corpus.postingWeight[q];
						}
					}
				}

				double[] scores()
				{
					return dots;
				}

				double normalised(double score)
				{
					double norm = Math.sqrt(Math.max(1e-300, 1.0 - removedSquares));
					return score / norm;
				}
			};
		}
	}

	/**
	 * Candidates are users; the score is the run's Common Neighbors variant
	 * over the users' term sets (degrees are the terms' user counts), exactly as
	 * SparseRecommendationMath computes it. Contributions: each shared term
	 * adds 1 (and its share of the Adamic-Adar tie-break) for common
	 * neighbours, 1/ln(degree) for Adamic-Adar, 1/degree for resource
	 * allocation, 1/|union| for Jaccard.
	 */
	static final class LinkScorer extends Scorer
	{
		private final String variant;

		LinkScorer(EvidenceCorpus corpus, int target, String variant)
		{
			super(corpus, target);
			this.variant = variant;
			reference = Reference.LINK;
		}

		int size()
		{
			return corpus.size();
		}

		String name(int candidate)
		{
			return corpus.users[candidate];
		}

		int indexOf(String name)
		{
			Integer index = corpus.index.get(name);
			return index == null || index.intValue() == target ? -1 : index.intValue();
		}

		boolean candidate(int index)
		{
			return index != target;
		}

		private double degree(int term)
		{
			return Math.max(2.0, corpus.documentFrequency[term]);
		}

		double[] scores()
		{
			return start().scores();
		}

		private double combine(int shared, double adamicAdar, double resource, int union)
		{
			if (shared == 0)
			{
				return 0.0;
			}
			if (AlgorithmParameterSettings.CN_JACCARD.equals(variant))
			{
				return union <= 0 ? 0.0 : shared / (double)union;
			}
			if (AlgorithmParameterSettings.CN_ADAMIC_ADAR.equals(variant))
			{
				return adamicAdar;
			}
			if (AlgorithmParameterSettings.CN_RESOURCE_ALLOCATION.equals(variant))
			{
				return resource;
			}
			return shared + adamicAdar / (1.0 + adamicAdar);
		}

		List<TermContribution> contributions(int candidate)
		{
			ArrayList<int[]> shared = new ArrayList<int[]>();
			double adamicAdar = 0.0;
			int i = corpus.pointer[target];
			int j = corpus.pointer[candidate];
			while (i < corpus.pointer[target + 1] && j < corpus.pointer[candidate + 1])
			{
				int a = corpus.column[i];
				int b = corpus.column[j];
				if (a == b)
				{
					if (corpus.weight[i] > 0.0 && corpus.weight[j] > 0.0)
					{
						shared.add(new int[] {a});
						adamicAdar += 1.0 / Math.log(degree(a));
					}
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
			int union = corpus.usableTerms(target) + corpus.usableTerms(candidate) - shared.size();
			double total = 0.0;
			ArrayList<double[]> values = new ArrayList<double[]>();
			for (int[] term : shared)
			{
				double inverseLog = 1.0 / Math.log(degree(term[0]));
				double value;
				if (AlgorithmParameterSettings.CN_JACCARD.equals(variant))
				{
					value = union <= 0 ? 0.0 : 1.0 / union;
				}
				else if (AlgorithmParameterSettings.CN_ADAMIC_ADAR.equals(variant))
				{
					value = inverseLog;
				}
				else if (AlgorithmParameterSettings.CN_RESOURCE_ALLOCATION.equals(variant))
				{
					value = 1.0 / degree(term[0]);
				}
				else
				{
					value = 1.0 + inverseLog / (1.0 + adamicAdar);
				}
				values.add(new double[] {term[0], value});
				total += value;
			}
			ArrayList<TermContribution> terms = new ArrayList<TermContribution>();
			for (double[] value : values)
			{
				terms.add(contribution(corpus.terms[(int)value[0]], value[1], total, candidate));
			}
			return sorted(terms);
		}

		double relevantShare(String community)
		{
			return new CosineScorer(corpus, target).relevantShare(community);
		}

		State start()
		{
			final int n = corpus.size();
			final int[] shared = new int[n];
			final double[] adamicAdar = new double[n];
			final double[] resource = new double[n];
			final int[] usable = new int[n];
			for (int v = 0; v < n; v++)
			{
				usable[v] = corpus.usableTerms(v);
			}
			final HashSet<Integer> removed = new HashSet<Integer>();
			for (int p = corpus.pointer[target]; p < corpus.pointer[target + 1]; p++)
			{
				if (corpus.weight[p] <= 0.0)
				{
					continue;
				}
				int t = corpus.column[p];
				for (int q = corpus.postingPointer[t]; q < corpus.postingPointer[t + 1]; q++)
				{
					int v = corpus.postingUser[q];
					shared[v]++;
					adamicAdar[v] += 1.0 / Math.log(degree(t));
					resource[v] += 1.0 / degree(t);
				}
			}
			final int[] targetUsable = {usable[target]};
			return new State()
			{
				void remove(String term)
				{
					Integer t = corpus.termIndex.get(term);
					if (t == null || removed.contains(t) || corpus.weightOf(target, t.intValue()) <= 0.0)
					{
						return;
					}
					removed.add(t);
					targetUsable[0]--;
					for (int q = corpus.postingPointer[t.intValue()]; q < corpus.postingPointer[t.intValue() + 1]; q++)
					{
						int v = corpus.postingUser[q];
						shared[v]--;
						adamicAdar[v] -= 1.0 / Math.log(degree(t.intValue()));
						resource[v] -= 1.0 / degree(t.intValue());
					}
				}

				double[] scores()
				{
					double[] scores = new double[n];
					for (int v = 0; v < n; v++)
					{
						if (v == target)
						{
							continue;
						}
						scores[v] = combine(shared[v], adamicAdar[v], resource[v],
								targetUsable[0] + usable[v] - shared[v]);
					}
					return scores;
				}
			};
		}
	}

	/**
	 * Candidates are accounts scored by their best follower under a user-level
	 * score, as the run projects user rankings onto accounts.
	 */
	static final class ProjectionScorer extends Scorer
	{
		private final Scorer users;
		private final String[] accounts;
		private final HashMap<String,Integer> accountIndex = new HashMap<String,Integer>();
		private final int[] best;

		ProjectionScorer(EvidenceCorpus corpus, int target, Scorer users)
		{
			super(corpus, target);
			this.users = users;
			reference = Reference.PROJECTION;
			TreeSet<String> names = new TreeSet<String>();
			for (int v = 0; v < corpus.size(); v++)
			{
				if (v != target && corpus.followee[v].length() > 0)
				{
					names.add(corpus.followee[v]);
				}
			}
			accounts = names.toArray(new String[0]);
			for (int a = 0; a < accounts.length; a++)
			{
				accountIndex.put(accounts[a], Integer.valueOf(a));
			}
			best = new int[accounts.length];
			project(users.scores());
		}

		private double[] project(double[] userScores)
		{
			double[] scores = new double[accounts.length];
			Arrays.fill(best, -1);
			for (int v = 0; v < corpus.size(); v++)
			{
				if (v == target || corpus.followee[v].length() == 0)
				{
					continue;
				}
				int a = accountIndex.get(corpus.followee[v]).intValue();
				if (userScores[v] > 0.0 && (best[a] < 0 || userScores[v] > scores[a]))
				{
					scores[a] = userScores[v];
					best[a] = v;
				}
			}
			return scores;
		}

		int size()
		{
			return accounts.length;
		}

		String name(int candidate)
		{
			return accounts[candidate];
		}

		int indexOf(String name)
		{
			Integer index = accountIndex.get(name);
			return index == null ? -1 : index.intValue();
		}

		double[] scores()
		{
			return project(users.scores());
		}

		List<TermContribution> contributions(int candidate)
		{
			project(users.scores());
			return best[candidate] < 0 ? new ArrayList<TermContribution>() : users.contributions(best[candidate]);
		}

		String explainingUser(int candidate)
		{
			project(users.scores());
			return best[candidate] < 0 ? null : corpus.users[best[candidate]];
		}

		String meaning()
		{
			return (users.reference == Reference.LINK ? "the link score" : "the TF-IDF cosine")
					+ " of the account's best-matching follower";
		}

		double relevantShare(String community)
		{
			return accountIndex.containsKey(community) ? 1.0 / Math.max(1, accounts.length) : 0.0;
		}

		State start()
		{
			final State inner = users.start();
			return new State()
			{
				void remove(String term)
				{
					inner.remove(term);
				}

				double[] scores()
				{
					return project(inner.scores());
				}

				double normalised(double score)
				{
					return inner.normalised(score);
				}
			};
		}
	}

	/**
	 * Candidates are accounts scored by the cosine of the target with the
	 * L2-normalised centroid of the account's followers (the target excluded):
	 * the reference for classifiers, which learn accounts from their followers'
	 * text.
	 */
	static final class CentroidScorer extends Scorer
	{
		private final String[] accounts;
		private final HashMap<String,Integer> accountIndex = new HashMap<String,Integer>();
		private final ArrayList<HashMap<Integer,Double>> centroids = new ArrayList<HashMap<Integer,Double>>();
		private final int[] closest;

		CentroidScorer(EvidenceCorpus corpus, int target)
		{
			super(corpus, target);
			reference = Reference.CENTROID;
			TreeMap<String,ArrayList<Integer>> followers = new TreeMap<String,ArrayList<Integer>>();
			for (int v = 0; v < corpus.size(); v++)
			{
				if (v == target || corpus.followee[v].length() == 0)
				{
					continue;
				}
				ArrayList<Integer> list = followers.get(corpus.followee[v]);
				if (list == null)
				{
					list = new ArrayList<Integer>();
					followers.put(corpus.followee[v], list);
				}
				list.add(Integer.valueOf(v));
			}
			accounts = followers.keySet().toArray(new String[0]);
			closest = new int[accounts.length];
			double[] cosines = corpus.cosines(target);
			for (int a = 0; a < accounts.length; a++)
			{
				accountIndex.put(accounts[a], Integer.valueOf(a));
				HashMap<Integer,Double> centroid = new HashMap<Integer,Double>();
				closest[a] = -1;
				for (Integer v : followers.get(accounts[a]))
				{
					for (int p = corpus.pointer[v.intValue()]; p < corpus.pointer[v.intValue() + 1]; p++)
					{
						if (corpus.weight[p] > 0.0)
						{
							Integer t = Integer.valueOf(corpus.column[p]);
							Double old = centroid.get(t);
							centroid.put(t, Double.valueOf((old == null ? 0.0 : old.doubleValue()) + corpus.weight[p]));
						}
					}
					if (closest[a] < 0 || cosines[v.intValue()] > cosines[closest[a]])
					{
						closest[a] = v.intValue();
					}
				}
				double norm = 0.0;
				for (Double value : centroid.values())
				{
					norm += value.doubleValue() * value.doubleValue();
				}
				norm = Math.sqrt(norm);
				for (Map.Entry<Integer,Double> entry : centroid.entrySet())
				{
					entry.setValue(Double.valueOf(norm > 0.0 ? entry.getValue().doubleValue() / norm : 0.0));
				}
				centroids.add(centroid);
			}
		}

		int size()
		{
			return accounts.length;
		}

		String name(int candidate)
		{
			return accounts[candidate];
		}

		int indexOf(String name)
		{
			Integer index = accountIndex.get(name);
			return index == null ? -1 : index.intValue();
		}

		double[] scores()
		{
			return start().scores();
		}

		List<TermContribution> contributions(int candidate)
		{
			HashMap<Integer,Double> centroid = centroids.get(candidate);
			ArrayList<double[]> raw = new ArrayList<double[]>();
			double total = 0.0;
			for (int p = corpus.pointer[target]; p < corpus.pointer[target + 1]; p++)
			{
				Double c = centroid.get(Integer.valueOf(corpus.column[p]));
				if (c != null && corpus.weight[p] > 0.0)
				{
					double value = corpus.weight[p] * c.doubleValue();
					raw.add(new double[] {corpus.column[p], value});
					total += value;
				}
			}
			ArrayList<TermContribution> terms = new ArrayList<TermContribution>();
			for (double[] term : raw)
			{
				terms.add(contribution(corpus.terms[(int)term[0]], term[1], total, closest[candidate]));
			}
			return sorted(terms);
		}

		String explainingUser(int candidate)
		{
			return closest[candidate] < 0 ? null : corpus.users[closest[candidate]];
		}

		double relevantShare(String community)
		{
			return accountIndex.containsKey(community) ? 1.0 / Math.max(1, accounts.length) : 0.0;
		}

		State start()
		{
			final double[] dots = new double[accounts.length];
			for (int a = 0; a < accounts.length; a++)
			{
				HashMap<Integer,Double> centroid = centroids.get(a);
				for (int p = corpus.pointer[target]; p < corpus.pointer[target + 1]; p++)
				{
					Double c = centroid.get(Integer.valueOf(corpus.column[p]));
					if (c != null)
					{
						dots[a] += corpus.weight[p] * c.doubleValue();
					}
				}
			}
			return new State()
			{
				double removedSquares;

				void remove(String term)
				{
					Integer t = corpus.termIndex.get(term);
					if (t == null)
					{
						return;
					}
					double a = corpus.weightOf(target, t.intValue());
					if (a <= 0.0)
					{
						return;
					}
					removedSquares += a * a;
					for (int k = 0; k < accounts.length; k++)
					{
						Double c = centroids.get(k).get(t);
						if (c != null)
						{
							dots[k] -= a * c.doubleValue();
						}
					}
				}

				double[] scores()
				{
					return dots;
				}

				double normalised(double score)
				{
					return score / Math.sqrt(Math.max(1e-300, 1.0 - removedSquares));
				}
			};
		}
	}

	// ============================================================ measures

	/** Share of the delivered list found in the reference's top of the same length. */
	static double overlap(List<String> delivered, List<String> reference)
	{
		if (delivered.isEmpty())
		{
			return Double.NaN;
		}
		HashSet<String> top = new HashSet<String>(reference);
		int shared = 0;
		for (String item : delivered)
		{
			shared += top.contains(item) ? 1 : 0;
		}
		return shared / (double)delivered.size();
	}

	/**
	 * Extrapolated rank-biased overlap of two rankings truncated at the same
	 * depth k (Webber, Moffat and Zobel 2010, eq. 32):
	 * RBO = (X_k / k) p^k + ((1 - p) / p) sum_{d=1..k} (X_d / d) p^d,
	 * X_d the overlap of the two top-d prefixes. 1 for identical rankings, 0
	 * for disjoint ones; the top ranks weigh most.
	 */
	static double rbo(List<String> first, List<String> second, double p)
	{
		int k = Math.min(first.size(), second.size());
		if (k == 0)
		{
			return Double.NaN;
		}
		HashSet<String> a = new HashSet<String>();
		HashSet<String> b = new HashSet<String>();
		int overlap = 0;
		double sum = 0.0;
		for (int d = 1; d <= k; d++)
		{
			String x = first.get(d - 1);
			String y = second.get(d - 1);
			if (x.equals(y))
			{
				overlap++;
			}
			else
			{
				overlap += b.contains(x) ? 1 : 0;
				overlap += a.contains(y) ? 1 : 0;
			}
			a.add(x);
			b.add(y);
			sum += overlap / (double)d * Math.pow(p, d);
		}
		return overlap / (double)k * Math.pow(p, k) + (1 - p) / p * sum;
	}

	/** Kendall's tau-b of two score vectors (ties in either corrected for); NaN when undefined. */
	static double kendallTauB(double[] x, double[] y)
	{
		int n = x.length;
		long concordant = 0;
		long discordant = 0;
		long tiesX = 0;
		long tiesY = 0;
		for (int i = 0; i < n; i++)
		{
			for (int j = i + 1; j < n; j++)
			{
				double dx = Math.signum(x[i] - x[j]);
				double dy = Math.signum(y[i] - y[j]);
				if (dx == 0 && dy == 0)
				{
					continue;
				}
				if (dx == 0)
				{
					tiesX++;
				}
				else if (dy == 0)
				{
					tiesY++;
				}
				else if (dx == dy)
				{
					concordant++;
				}
				else
				{
					discordant++;
				}
			}
		}
		double denominator = Math.sqrt((double)(concordant + discordant + tiesX) * (concordant + discordant + tiesY));
		return denominator == 0.0 ? Double.NaN : (concordant - discordant) / denominator;
	}

	// ============================================================== helpers

	/** An insertion-ordered set of words (kept small: the words of one tweet). */
	private static final class LinkedHashSetHolder
	{
		private final java.util.LinkedHashSet<String> set = new java.util.LinkedHashSet<String>();

		boolean add(String value)
		{
			return set.add(value);
		}

		List<String> list()
		{
			return new ArrayList<String>(set);
		}
	}

	static double parse(String score)
	{
		if (score == null || score.trim().isEmpty())
		{
			return Double.NaN;
		}
		try
		{
			return Double.parseDouble(score.trim().replace("%", ""));
		}
		catch (NumberFormatException unreadable)
		{
			return Double.NaN;
		}
	}

	private static String empty(String value)
	{
		return value == null || value.trim().isEmpty() ? null : value.trim();
	}

	private static String clean(String value, String fallback)
	{
		return value == null || value.trim().length() == 0 ? fallback : value.trim();
	}

	private static String day(long millis)
	{
		java.text.SimpleDateFormat format = new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US);
		format.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
		return format.format(new java.util.Date(millis));
	}

	static String count(long value)
	{
		return String.format(Locale.US, "%,d", Long.valueOf(value));
	}

	static String formatCount(long value)
	{
		return count(value);
	}
}
