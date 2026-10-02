package TwitterGatherDataFollowers.userRyersonU;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * A Temporal Graph Foundation report: the model card of one run and the
 * explanation of every target, read from the JSON a run produces (live, from
 * {@link TemporalGraphFoundationModelSupport.Result#toJson()}) or stores in
 * its record ({@code tgf/report.json}). The studio and Evidence Studio read
 * only this view, so what they show for a live run is exactly what its record
 * keeps.
 */
final class TgfReport
{
	static final String FOLDER = "tgf";
	static final String FILE = "report.json";

	final String runId;
	final String dataset;
	final boolean supervised;
	final String note;
	final List<String> warnings;
	final Data data;
	final List<Learner> learners;
	final List<Community> communities;
	final Validation validation;
	final double graphAlpha;
	final int graphNeighbours;
	final long textEdges;
	final long termTimeEdges;
	final long activityEdges;
	final long fitMillis;
	final int folds;
	final int textIterations;
	final boolean textConverged;
	final double conformalAlpha;
	final double conformalThreshold;
	final int graded;
	final int correct;
	final int covered;
	final int scoredPairs;
	final List<Target> targets;
	/** The run record this report was read from; null for a live run. */
	final File directory;

	static final class Data
	{
		final int users;
		final int labelledUsers;
		final int targets;
		final int terms;
		final int termMonthPairs;
		final int months;
		final String firstMonth;
		final String lastMonth;
		final long rowsScanned;
		final long datedRows;
		final boolean fromCorpus;

		Data(Map<?,?> json)
		{
			users = integer(json, "users");
			labelledUsers = integer(json, "labelledUsers");
			targets = integer(json, "targets");
			terms = integer(json, "terms");
			termMonthPairs = integer(json, "termMonthPairs");
			months = integer(json, "months");
			firstMonth = string(json, "firstMonth");
			lastMonth = string(json, "lastMonth");
			rowsScanned = (long)number(json, "rowsScanned");
			datedRows = (long)number(json, "datedRows");
			fromCorpus = bool(json, "fromCorpus");
		}

		/** The month label of a month index of the span (yyyy-MM), or "" when undated. */
		String month(int index)
		{
			if (firstMonth == null || firstMonth.length() < 7)
			{
				return "";
			}
			int year = Integer.parseInt(firstMonth.substring(0, 4));
			int month = Integer.parseInt(firstMonth.substring(5, 7)) - 1 + index;
			return String.format(java.util.Locale.US, "%04d-%02d", Integer.valueOf(year + month / 12),
					Integer.valueOf(month % 12 + 1));
		}
	}

	static final class Learner
	{
		final String key;
		final String name;
		final String description;
		final boolean active;
		final double weight;
		final double scale;
		final double accuracy;
		final double logLoss;

		Learner(Map<?,?> json)
		{
			key = string(json, "key");
			name = string(json, "name");
			description = string(json, "description");
			active = bool(json, "active");
			weight = number(json, "weight");
			scale = number(json, "scale");
			accuracy = number(json, "accuracy");
			logLoss = number(json, "logLoss");
		}
	}

	static final class Term
	{
		final String term;
		final double weight;

		Term(Map<?,?> json)
		{
			term = string(json, "term");
			weight = number(json, "weight");
		}
	}

	static final class Community
	{
		final String name;
		final int labelled;
		final double share;
		final double prior;
		final double precision;
		final double recall;
		final double f1;
		final int support;
		final List<Term> terms;
		final double[] monthly;
		final double[] hourly;

		Community(Map<?,?> json)
		{
			name = string(json, "name");
			labelled = integer(json, "labelled");
			share = number(json, "share");
			prior = number(json, "prior");
			precision = number(json, "precision");
			recall = number(json, "recall");
			f1 = number(json, "f1");
			support = integer(json, "support");
			ArrayList<Term> list = new ArrayList<Term>();
			for (Map<?,?> item : maps(json, "terms"))
			{
				list.add(new Term(item));
			}
			terms = Collections.unmodifiableList(list);
			monthly = doubles(json.get("monthly"));
			hourly = doubles(json.get("hourly"));
		}
	}

	static final class Validation
	{
		final int users;
		final double accuracy;
		final double macroF1;
		final double logLoss;
		final double brier;
		final double calibrationError;
		final double majorityAccuracy;
		final double chanceAccuracy;
		/** [bin][count, mean confidence, accuracy]. */
		final double[][] reliability;
		final int[][] confusion;
		final double conformalCoverage;
		final double conformalSetSize;

		Validation(Map<?,?> json)
		{
			users = integer(json, "users");
			accuracy = number(json, "accuracy");
			macroF1 = number(json, "macroF1");
			logLoss = number(json, "logLoss");
			brier = number(json, "brier");
			calibrationError = number(json, "calibrationError");
			majorityAccuracy = number(json, "majorityAccuracy");
			chanceAccuracy = number(json, "chanceAccuracy");
			List<?> bins = list(json, "reliability");
			reliability = new double[bins.size()][];
			for (int i = 0; i < bins.size(); i++)
			{
				reliability[i] = doubles(bins.get(i));
			}
			List<?> rows = list(json, "confusion");
			confusion = new int[rows.size()][];
			for (int i = 0; i < rows.size(); i++)
			{
				double[] values = doubles(rows.get(i));
				confusion[i] = new int[values.length];
				for (int j = 0; j < values.length; j++)
				{
					confusion[i][j] = (int)Math.round(values[j]);
				}
			}
			conformalCoverage = number(json, "conformalCoverage");
			conformalSetSize = number(json, "conformalSetSize");
		}
	}

	/** One learner's part in a target's prediction. */
	static final class TargetLearner
	{
		final String key;
		final String name;
		final boolean active;
		final double weight;
		final String says;
		final double saysProbability;
		/** Its share of the log-odds of the predicted account against the runner-up. */
		final double contribution;

		TargetLearner(Map<?,?> json)
		{
			key = string(json, "key");
			name = string(json, "name");
			active = bool(json, "active");
			weight = number(json, "weight");
			says = string(json, "says");
			saysProbability = number(json, "saysProbability");
			contribution = number(json, "contribution");
		}
	}

	static final class Ablation
	{
		final String key;
		final String name;
		final String predicted;
		final double probability;
		final boolean changes;

		Ablation(Map<?,?> json)
		{
			key = string(json, "key");
			name = string(json, "name");
			predicted = string(json, "predicted");
			probability = number(json, "probability");
			changes = bool(json, "changes");
		}
	}

	static final class TermEvidence
	{
		final String term;
		final double contribution;
		final int count;
		final double weight;
		final int users;

		TermEvidence(Map<?,?> json)
		{
			term = string(json, "term");
			contribution = number(json, "contribution");
			count = integer(json, "count");
			weight = number(json, "weight");
			users = integer(json, "users");
		}
	}

	static final class Neighbour
	{
		final String user;
		final String community;
		final boolean supports;
		final double influence;
		final double share;
		final boolean direct;
		final List<String> shared;

		Neighbour(Map<?,?> json)
		{
			user = string(json, "user");
			community = string(json, "community");
			supports = bool(json, "supports");
			influence = number(json, "influence");
			share = number(json, "share");
			direct = bool(json, "direct");
			shared = strings(json.get("shared"));
		}
	}

	static final class Driver
	{
		final String label;
		final double share;
		final double contribution;

		Driver(Map<?,?> json)
		{
			label = string(json, "label");
			share = number(json, "share");
			contribution = number(json, "contribution");
		}
	}

	static final class AccountScore
	{
		final String account;
		final double probability;
		final boolean inSet;

		AccountScore(Map<?,?> json)
		{
			account = string(json, "account");
			probability = number(json, "probability");
			inSet = bool(json, "inSet");
		}
	}

	static final class Recommendation
	{
		final String user;
		final String community;
		final double probability;
		final double similarity;
		final List<String> shared;
		/** Whether the user shares the target's real community; null when unknown. */
		final Boolean relevant;

		Recommendation(Map<?,?> json)
		{
			user = string(json, "user");
			community = string(json, "community");
			probability = number(json, "probability");
			similarity = number(json, "similarity");
			shared = strings(json.get("shared"));
			relevant = json.get("relevant") instanceof Boolean ? (Boolean)json.get("relevant") : null;
		}
	}

	/** One target: its prediction and the complete explanation. */
	static final class Target
	{
		final String user;
		final String truth;
		final int tweets;
		final int datedTweets;
		final String first;
		final String last;
		final int terms;
		final boolean supervised;
		final double[] posterior;
		final String predicted;
		final String runnerUp;
		final double probability;
		final double margin;
		final double entropy;
		final List<String> set;
		final Boolean correct;
		final Boolean inSet;
		final int truthRank;
		final double logOdds;
		final double prior;
		final List<TargetLearner> learners;
		final List<Ablation> ablations;
		final List<TermEvidence> supporting;
		final List<TermEvidence> opposing;
		final double termTotal;
		final double textIntercept;
		final List<Neighbour> textNeighbours;
		final List<Neighbour> termTimeNeighbours;
		final boolean activityAvailable;
		final double[] monthly;
		final double[] hourly;
		final double activityWeight;
		final List<Driver> drivers;
		final boolean counterfactualFound;
		final List<String> counterfactualTerms;
		final String counterfactualFlipsTo;
		final double counterfactualProbability;
		final String counterfactualNote;
		final List<AccountScore> accounts;
		final List<Recommendation> recommendations;
		final String headline;

		Target(Map<?,?> json)
		{
			user = string(json, "user");
			truth = string(json, "truth");
			tweets = integer(json, "tweets");
			datedTweets = integer(json, "datedTweets");
			first = string(json, "first");
			last = string(json, "last");
			terms = integer(json, "terms");
			supervised = bool(json, "supervised");
			posterior = doubles(json.get("posterior"));
			predicted = string(json, "predicted");
			runnerUp = string(json, "runnerUp");
			probability = number(json, "probability");
			margin = number(json, "margin");
			entropy = number(json, "entropy");
			set = strings(json.get("set"));
			correct = json.get("correct") instanceof Boolean ? (Boolean)json.get("correct") : null;
			inSet = json.get("inSet") instanceof Boolean ? (Boolean)json.get("inSet") : null;
			truthRank = integer(json, "truthRank");
			logOdds = number(json, "logOdds");
			prior = number(json, "prior");
			ArrayList<TargetLearner> learnerList = new ArrayList<TargetLearner>();
			for (Map<?,?> item : maps(json, "learners"))
			{
				learnerList.add(new TargetLearner(item));
			}
			learners = Collections.unmodifiableList(learnerList);
			ArrayList<Ablation> ablationList = new ArrayList<Ablation>();
			for (Map<?,?> item : maps(json, "ablations"))
			{
				ablationList.add(new Ablation(item));
			}
			ablations = Collections.unmodifiableList(ablationList);
			Map<?,?> text = map(json, "textEvidence");
			supporting = termList(text, "supporting");
			opposing = termList(text, "opposing");
			termTotal = number(text, "termTotal");
			textIntercept = number(text, "intercept");
			textNeighbours = neighbourList(json, "textNeighbours");
			termTimeNeighbours = neighbourList(json, "termTimeNeighbours");
			Map<?,?> activity = map(json, "activity");
			activityAvailable = bool(activity, "available");
			monthly = doubles(activity.get("monthly"));
			hourly = doubles(activity.get("hourly"));
			activityWeight = number(activity, "weight");
			ArrayList<Driver> driverList = new ArrayList<Driver>();
			for (Map<?,?> item : maps(activity, "drivers"))
			{
				driverList.add(new Driver(item));
			}
			drivers = Collections.unmodifiableList(driverList);
			Map<?,?> counterfactual = map(json, "counterfactual");
			counterfactualFound = bool(counterfactual, "found");
			counterfactualTerms = strings(counterfactual.get("terms"));
			counterfactualFlipsTo = string(counterfactual, "flipsTo");
			counterfactualProbability = number(counterfactual, "probability");
			counterfactualNote = string(counterfactual, "note");
			ArrayList<AccountScore> accountList = new ArrayList<AccountScore>();
			for (Map<?,?> item : maps(json, "accounts"))
			{
				accountList.add(new AccountScore(item));
			}
			accounts = Collections.unmodifiableList(accountList);
			ArrayList<Recommendation> recommendationList = new ArrayList<Recommendation>();
			for (Map<?,?> item : maps(json, "recommendations"))
			{
				recommendationList.add(new Recommendation(item));
			}
			recommendations = Collections.unmodifiableList(recommendationList);
			headline = string(json, "headline");
		}

		/** Whether the target's real follow is known (to grade the prediction). */
		boolean graded()
		{
			return correct != null;
		}

		private static List<TermEvidence> termList(Map<?,?> json, String key)
		{
			ArrayList<TermEvidence> result = new ArrayList<TermEvidence>();
			for (Map<?,?> item : maps(json, key))
			{
				result.add(new TermEvidence(item));
			}
			return Collections.unmodifiableList(result);
		}

		private static List<Neighbour> neighbourList(Map<?,?> json, String key)
		{
			ArrayList<Neighbour> result = new ArrayList<Neighbour>();
			for (Map<?,?> item : maps(json, key))
			{
				result.add(new Neighbour(item));
			}
			return Collections.unmodifiableList(result);
		}
	}

	private TgfReport(Map<?,?> json, File directory)
	{
		this.directory = directory;
		runId = string(json, "runId");
		dataset = string(json, "dataset");
		supervised = bool(json, "supervised");
		note = string(json, "note");
		warnings = strings(json.get("warnings"));
		data = new Data(map(json, "data"));
		ArrayList<Learner> learnerList = new ArrayList<Learner>();
		for (Map<?,?> item : maps(json, "learners"))
		{
			learnerList.add(new Learner(item));
		}
		learners = Collections.unmodifiableList(learnerList);
		ArrayList<Community> communityList = new ArrayList<Community>();
		for (Map<?,?> item : maps(json, "communities"))
		{
			communityList.add(new Community(item));
		}
		communities = Collections.unmodifiableList(communityList);
		validation = new Validation(map(json, "validation"));
		Map<?,?> graphs = map(json, "graphs");
		graphAlpha = number(graphs, "alpha");
		graphNeighbours = integer(graphs, "neighbours");
		textEdges = (long)number(graphs, "textEdges");
		termTimeEdges = (long)number(graphs, "termTimeEdges");
		activityEdges = (long)number(graphs, "activityEdges");
		Map<?,?> fit = map(json, "fit");
		fitMillis = (long)number(fit, "millis");
		folds = integer(fit, "folds");
		textIterations = integer(fit, "textIterations");
		textConverged = bool(fit, "textConverged");
		conformalAlpha = number(fit, "conformalAlpha");
		conformalThreshold = number(fit, "conformalThreshold");
		Map<?,?> run = map(json, "run");
		graded = integer(run, "graded");
		correct = integer(run, "correct");
		covered = integer(run, "covered");
		scoredPairs = integer(run, "scoredPairs");
		ArrayList<Target> targetList = new ArrayList<Target>();
		for (Map<?,?> item : maps(json, "targets"))
		{
			targetList.add(new Target(item));
		}
		targets = Collections.unmodifiableList(targetList);
	}

	/** The report of a live result. */
	static TgfReport of(TemporalGraphFoundationModelSupport.Result result)
	{
		return result == null ? null : new TgfReport(result.toJson(), null);
	}

	static TgfReport parse(Map<?,?> json, File directory)
	{
		return new TgfReport(json, directory);
	}

	boolean live()
	{
		return directory == null;
	}

	/** The community names, in the order of every posterior. */
	String[] communityNames()
	{
		String[] names = new String[communities.size()];
		for (int i = 0; i < names.length; i++)
		{
			names[i] = communities.get(i).name;
		}
		return names;
	}

	int communityIndex(String name)
	{
		for (int i = 0; i < communities.size(); i++)
		{
			if (communities.get(i).name.equals(name))
			{
				return i;
			}
		}
		return -1;
	}

	Target target(String user)
	{
		for (Target target : targets)
		{
			if (target.user.equals(user))
			{
				return target;
			}
		}
		return null;
	}

	Learner learner(String key)
	{
		for (Learner learner : learners)
		{
			if (learner.key.equals(key))
			{
				return learner;
			}
		}
		return null;
	}

	// ============================================================== records

	/** Writes a result into a run record: tgf/report.json and tgf/predictions.csv. */
	static void write(File runDirectory, TemporalGraphFoundationModelSupport.Result result) throws IOException
	{
		File folder = new File(runDirectory, FOLDER);
		if (!folder.isDirectory() && !folder.mkdirs())
		{
			throw new IOException("cannot create " + folder.getPath());
		}
		write(new File(folder, FILE), JsonText.of(result.toJson()));
		TgfReport report = of(result);
		StringBuilder csv = new StringBuilder();
		csv.append(JsonText.csvLine("target", "truth", "predicted", "probability", "runner_up", "margin",
				"conformal_set", "set_size", "correct", "in_set", "truth_rank"));
		for (Target target : report.targets)
		{
			csv.append(JsonText.csvLine(target.user, target.truth, target.predicted, Double.valueOf(target.probability),
					target.runnerUp, Double.valueOf(target.margin), join(target.set), Integer.valueOf(target.set.size()),
					target.correct, target.inSet, target.truthRank > 0 ? Integer.valueOf(target.truthRank) : null));
		}
		write(new File(folder, "predictions.csv"), csv.toString());
	}

	/** The report stored in a run record, or null when the run has none. */
	static TgfReport load(File runDirectory) throws IOException
	{
		File file = new File(new File(runDirectory, FOLDER), FILE);
		if (!file.isFile())
		{
			return null;
		}
		Object json = JsonText.read(file);
		if (!(json instanceof Map))
		{
			throw new IOException(file.getPath() + " is not a TGF report");
		}
		return new TgfReport((Map<?,?>)json, runDirectory);
	}

	/** Run records that hold a TGF report, newest first. */
	static List<File> recordedRuns()
	{
		File[] runs = RunRecordWriter.runsDirectory().listFiles();
		ArrayList<File> result = new ArrayList<File>();
		if (runs == null)
		{
			return result;
		}
		Arrays.sort(runs);
		for (int i = runs.length - 1; i >= 0; i--)
		{
			if (new File(new File(runs[i], FOLDER), FILE).isFile())
			{
				result.add(runs[i]);
			}
		}
		return result;
	}

	private static void write(File file, String text) throws IOException
	{
		try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file),
				StandardCharsets.UTF_8)))
		{
			writer.write(text);
		}
	}

	private static String join(List<String> values)
	{
		StringBuilder builder = new StringBuilder();
		for (String value : values)
		{
			if (builder.length() > 0)
			{
				builder.append("; ");
			}
			builder.append(value);
		}
		return builder.toString();
	}

	// ============================================================ accessors

	static String string(Map<?,?> json, String key)
	{
		Object value = json == null ? null : json.get(key);
		return value == null ? null : value.toString();
	}

	static double number(Map<?,?> json, String key)
	{
		Object value = json == null ? null : json.get(key);
		return value instanceof Number ? ((Number)value).doubleValue() : Double.NaN;
	}

	static int integer(Map<?,?> json, String key)
	{
		double value = number(json, key);
		return Double.isNaN(value) ? 0 : (int)Math.round(value);
	}

	static boolean bool(Map<?,?> json, String key)
	{
		Object value = json == null ? null : json.get(key);
		return value instanceof Boolean && ((Boolean)value).booleanValue();
	}

	static Map<?,?> map(Map<?,?> json, String key)
	{
		Object value = json == null ? null : json.get(key);
		return value instanceof Map ? (Map<?,?>)value : Collections.emptyMap();
	}

	static List<?> list(Map<?,?> json, String key)
	{
		Object value = json == null ? null : json.get(key);
		return value instanceof List ? (List<?>)value : Collections.emptyList();
	}

	static List<Map<?,?>> maps(Map<?,?> json, String key)
	{
		ArrayList<Map<?,?>> result = new ArrayList<Map<?,?>>();
		for (Object item : list(json, key))
		{
			if (item instanceof Map)
			{
				result.add((Map<?,?>)item);
			}
		}
		return result;
	}

	static double[] doubles(Object value)
	{
		if (!(value instanceof List))
		{
			return new double[0];
		}
		List<?> list = (List<?>)value;
		double[] result = new double[list.size()];
		for (int i = 0; i < result.length; i++)
		{
			Object item = list.get(i);
			result[i] = item instanceof Number ? ((Number)item).doubleValue() : Double.NaN;
		}
		return result;
	}

	static List<String> strings(Object value)
	{
		ArrayList<String> result = new ArrayList<String>();
		if (value instanceof List)
		{
			for (Object item : (List<?>)value)
			{
				if (item != null)
				{
					result.add(item.toString());
				}
			}
		}
		return Collections.unmodifiableList(result);
	}
}
