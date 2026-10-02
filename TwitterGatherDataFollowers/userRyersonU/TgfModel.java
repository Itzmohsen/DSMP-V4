package TwitterGatherDataFollowers.userRyersonU;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

/**
 * The Temporal Graph Foundation model of one run: a Super Learner (van der
 * Laan, Polley and Hubbard 2007) over six learners of the target's community
 * (the account it follows), each seeing a different view of the run:
 *
 * <ol>
 * <li><b>Text model</b>: multinomial logistic regression on sublinear TF-IDF.</li>
 * <li><b>Text graph</b>: label propagation (Zhou et al. 2004) over the cosine
 *     10-nearest-neighbour graph of the users' text.</li>
 * <li><b>Term-time graph</b>: label propagation over the temporal co-activity
 *     graph, users linked when they wrote the same words in the same month.</li>
 * <li><b>Activity model</b>: logistic regression on when users tweet (month,
 *     hour of day, weekday).</li>
 * <li><b>Text neighbours</b> and <b>activity neighbours</b>: similarity-weighted
 *     votes of the 10 most similar labelled users.</li>
 * </ol>
 *
 * Labels are the followed accounts of the users that are not targets; a
 * target's own follow is never read. Each learner is cross-fitted over
 * stratified folds of the labelled users, so the stack learns from scores of
 * users the learner did not see; the stack is a non-negative logarithmic
 * opinion pool (Breiman's stacked regression with non-negative weights),
 * fitted by maximum likelihood, so its probabilities are calibrated and its
 * evidence decomposes additively by learner. Cross-fitted posteriors of the
 * labelled users give the validation metrics and the split-conformal
 * threshold of the prediction sets.
 */
final class TgfModel
{
	static final String[] CHANNEL_KEYS = {"text", "text-graph", "term-time-graph", "activity", "text-neighbours",
			"activity-neighbours"};
	static final String[] CHANNEL_NAMES = {"Text model", "Text graph", "Term-time graph", "Activity model",
			"Text neighbours", "Activity neighbours"};
	static final String[] CHANNEL_DESCRIPTIONS = {
			"Multinomial logistic regression on sublinear TF-IDF (L2, lambda 1e-4)",
			"Label propagation over the cosine 10-nearest-neighbour text graph (alpha 0.9)",
			"Label propagation over the graph of users who wrote the same words in the same month (alpha 0.9)",
			"Multinomial logistic regression on tweet shares per month, hour of day and weekday (L2, lambda 1e-3)",
			"Similarity-weighted vote of the 10 most similar labelled users by text",
			"Similarity-weighted vote of the 10 most similar labelled users by monthly activity"};
	static final int TEXT = 0;
	static final int TEXT_GRAPH = 1;
	static final int TERM_TIME_GRAPH = 2;
	static final int ACTIVITY = 3;
	static final int TEXT_NEIGHBOURS = 4;
	static final int ACTIVITY_NEIGHBOURS = 5;
	static final int CHANNELS = 6;

	static final double ALPHA = 0.9;
	static final int NEIGHBOURS = 10;
	static final int KEEP = 30;
	static final double TEXT_LAMBDA = 1e-4;
	static final double ACTIVITY_LAMBDA = 1e-3;
	static final double CONFORMAL_ALPHA = 0.1;
	static final long SEED = 0x7E6F0A12L;

	final TgfFeatures features;
	final String[] classes;
	/** Class of each labelled non-target user, -1 for targets and users without a known follow. */
	final int[] label;
	/** The true class of every user whose follow is known (targets included), for grading only. */
	final int[] truth;
	final boolean[] target;
	final boolean supervised;
	final boolean[] active;
	final TgfLearning.Graph textGraph;
	final TgfLearning.Graph termTimeGraph;
	final TgfLearning.Graph activityGraph;
	final TgfLearning.Logistic textModel;
	final TgfLearning.Logistic activityModel;
	/** Standardised, per-user centred learner scores: [channel][user][class] (all labels used). */
	final double[][][] scores;
	final double[] scale;
	final double[] weights;
	final double[] bias;
	final double[][] posterior;
	/** Cross-fitted pool posteriors of the labelled users (null for the others). */
	final double[][] heldOut;
	/** Cross-fitted standardised learner scores of the labelled users. */
	final double[][][] heldOutScores;
	final double conformalThreshold;
	final Validation validation;
	final int folds;
	final long trainMillis;
	final String note;

	/** Cross-validated quality of the model on the labelled users. */
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
		/** Reliability bins: [bin][count, mean confidence, accuracy]. */
		final double[][] reliability;
		/** Actual class x predicted class. */
		final int[][] confusion;
		final double[] classPrecision;
		final double[] classRecall;
		final double[] classF1;
		final int[] classSupport;
		final double[] channelAccuracy;
		final double[] channelLogLoss;
		final double conformalCoverage;
		final double conformalSetSize;

		Validation(int users, double accuracy, double macroF1, double logLoss, double brier, double calibrationError,
				double majorityAccuracy, double chanceAccuracy, double[][] reliability, int[][] confusion,
				double[] classPrecision, double[] classRecall, double[] classF1, int[] classSupport,
				double[] channelAccuracy, double[] channelLogLoss, double conformalCoverage, double conformalSetSize)
		{
			this.users = users;
			this.accuracy = accuracy;
			this.macroF1 = macroF1;
			this.logLoss = logLoss;
			this.brier = brier;
			this.calibrationError = calibrationError;
			this.majorityAccuracy = majorityAccuracy;
			this.chanceAccuracy = chanceAccuracy;
			this.reliability = reliability;
			this.confusion = confusion;
			this.classPrecision = classPrecision;
			this.classRecall = classRecall;
			this.classF1 = classF1;
			this.classSupport = classSupport;
			this.channelAccuracy = channelAccuracy;
			this.channelLogLoss = channelLogLoss;
			this.conformalCoverage = conformalCoverage;
			this.conformalSetSize = conformalSetSize;
		}
	}

	private TgfModel(TgfFeatures features, String[] classes, int[] label, int[] truth, boolean[] target,
			boolean supervised, boolean[] active, TgfLearning.Graph textGraph, TgfLearning.Graph termTimeGraph,
			TgfLearning.Graph activityGraph, TgfLearning.Logistic textModel, TgfLearning.Logistic activityModel,
			double[][][] scores, double[] scale, double[] weights, double[] bias, double[][] posterior,
			double[][] heldOut, double[][][] heldOutScores, double conformalThreshold, Validation validation,
			int folds, long trainMillis, String note)
	{
		this.features = features;
		this.classes = classes;
		this.label = label;
		this.truth = truth;
		this.target = target;
		this.supervised = supervised;
		this.active = active;
		this.textGraph = textGraph;
		this.termTimeGraph = termTimeGraph;
		this.activityGraph = activityGraph;
		this.textModel = textModel;
		this.activityModel = activityModel;
		this.scores = scores;
		this.scale = scale;
		this.weights = weights;
		this.bias = bias;
		this.posterior = posterior;
		this.heldOut = heldOut;
		this.heldOutScores = heldOutScores;
		this.conformalThreshold = conformalThreshold;
		this.validation = validation;
		this.folds = folds;
		this.trainMillis = trainMillis;
		this.note = note;
	}

	int size()
	{
		return features.size();
	}

	int classCount()
	{
		return classes.length;
	}

	// ================================================================= fit

	/**
	 * Fits the model. {@code followees} gives every known follow (targets'
	 * follows are only stored in {@link #truth} for grading); {@code targets}
	 * are never labelled.
	 */
	static TgfModel fit(TgfFeatures features, Map<String,String> followees, Set<String> targets)
	{
		long started = System.nanoTime();
		int n = features.size();
		boolean[] target = new boolean[n];
		TreeSet<String> classSet = new TreeSet<String>();
		for (int u = 0; u < n; u++)
		{
			target[u] = targets.contains(features.users[u]);
			String followee = followees.get(features.users[u]);
			if (!target[u] && followee != null && followee.trim().length() > 0)
			{
				classSet.add(followee.trim());
			}
		}
		String[] classes = classSet.toArray(new String[0]);
		int[] label = new int[n];
		int[] truth = new int[n];
		int labelled = 0;
		for (int u = 0; u < n; u++)
		{
			String followee = followees.get(features.users[u]);
			int c = followee == null ? -1 : Arrays.binarySearch(classes, followee.trim());
			truth[u] = c >= 0 ? c : -1;
			label[u] = target[u] || c < 0 ? -1 : c;
			labelled += label[u] >= 0 ? 1 : 0;
		}
		TgfLearning.Graph textGraph = TgfLearning.knn(features.text, NEIGHBOURS, KEEP);
		boolean temporal = features.temporal();
		TgfLearning.Graph termTimeGraph = temporal && features.termTime.columns > 0
				? TgfLearning.knn(features.termTime, NEIGHBOURS, KEEP) : null;
		TgfLearning.Graph activityGraph = temporal ? TgfLearning.knn(monthlyRows(features), NEIGHBOURS, KEEP) : null;
		boolean[] active = {true, true, termTimeGraph != null, temporal, true, activityGraph != null};
		int classCount = classes.length;
		if (classCount < 2 || labelled < 4)
		{
			return unsupervised(features, classes, label, truth, target, textGraph, termTimeGraph, activityGraph,
					started, classCount < 2
							? "Fewer than two followed accounts among the labelled users: TGF ranks users by text"
									+ " similarity and cannot learn communities."
							: "Fewer than four labelled users: TGF ranks users by text similarity.");
		}
		int[] calibration = labelledUsers(label);
		int folds = calibration.length > 2000 ? 3 : 5;
		int[] foldOf = stratifiedFolds(calibration, label, classCount, folds);
		//Cross-fitted learner scores of the labelled users.
		double[][][] oof = new double[CHANNELS][n][];
		for (int f = 0; f < folds; f++)
		{
			int[] hidden = label.clone();
			ArrayList<Integer> heldUsers = new ArrayList<Integer>();
			for (int i = 0; i < calibration.length; i++)
			{
				if (foldOf[i] == f)
				{
					hidden[calibration[i]] = -1;
					heldUsers.add(Integer.valueOf(calibration[i]));
				}
			}
			int[] held = toArray(heldUsers);
			double[][][] fold = channelScores(features, hidden, classCount, active, textGraph, termTimeGraph,
					activityGraph, held).scores;
			for (int m = 0; m < CHANNELS; m++)
			{
				for (int i = 0; i < held.length; i++)
				{
					oof[m][held[i]] = fold[m][i];
				}
			}
		}
		//Standardise each learner by the root mean square of its centred cross-fitted scores.
		double[] scale = new double[CHANNELS];
		for (int m = 0; m < CHANNELS; m++)
		{
			double squares = 0.0;
			long count = 0;
			for (int u : calibration)
			{
				for (double value : oof[m][u])
				{
					squares += value * value;
					count++;
				}
			}
			double rms = Math.sqrt(squares / Math.max(1L, count));
			scale[m] = active[m] && rms > 1e-12 ? rms : 1.0;
			if (!(rms > 1e-12))
			{
				active[m] = false;
			}
			for (int u : calibration)
			{
				for (int c = 0; c < classCount; c++)
				{
					oof[m][u][c] = active[m] ? oof[m][u][c] / scale[m] : 0.0;
				}
			}
		}
		int[] y = new int[calibration.length];
		for (int i = 0; i < calibration.length; i++)
		{
			y[i] = label[calibration[i]];
		}
		double[] theta = TgfLearning.pool(stackRows(oof, calibration, active), y, classCount, 1e-2, 1e-1);
		double[] weights = Arrays.copyOf(theta, CHANNELS);
		double[] bias = Arrays.copyOfRange(theta, CHANNELS, CHANNELS + classCount);
		//Cross-fitted pool posteriors: the pool refitted without each fold.
		double[][] heldOut = new double[n][];
		for (int f = 0; f < folds; f++)
		{
			ArrayList<Integer> trainUsers = new ArrayList<Integer>();
			ArrayList<Integer> testUsers = new ArrayList<Integer>();
			for (int i = 0; i < calibration.length; i++)
			{
				(foldOf[i] == f ? testUsers : trainUsers).add(Integer.valueOf(calibration[i]));
			}
			int[] train = toArray(trainUsers);
			int[] trainY = new int[train.length];
			for (int i = 0; i < train.length; i++)
			{
				trainY[i] = label[train[i]];
			}
			double[] foldTheta = TgfLearning.pool(stackRows(oof, train, active), trainY, classCount, 1e-2, 1e-1);
			for (Integer u : testUsers)
			{
				heldOut[u.intValue()] = posteriorOf(oof, u.intValue(), foldTheta, classCount);
			}
		}
		//Final learner scores with every label, for every user.
		Learners fitted = channelScores(features, label, classCount, active, textGraph, termTimeGraph,
				activityGraph, allUsers(n));
		double[][][] all = fitted.scores;
		double[][][] scores = new double[CHANNELS][n][classCount];
		for (int m = 0; m < CHANNELS; m++)
		{
			for (int u = 0; u < n; u++)
			{
				for (int c = 0; c < classCount; c++)
				{
					scores[m][u][c] = active[m] ? all[m][u][c] / scale[m] : 0.0;
				}
			}
		}
		double[][] posterior = new double[n][];
		for (int u = 0; u < n; u++)
		{
			posterior[u] = posteriorOf(scores, u, theta, classCount);
		}
		double[] nonconformity = new double[calibration.length];
		for (int i = 0; i < calibration.length; i++)
		{
			nonconformity[i] = 1.0 - heldOut[calibration[i]][label[calibration[i]]];
		}
		double threshold = TgfLearning.conformalThreshold(nonconformity, CONFORMAL_ALPHA);
		Validation validation = validate(heldOut, oof, calibration, label, classCount, foldOf, folds, active);
		long millis = (System.nanoTime() - started) / 1000000L;
		return new TgfModel(features, classes, label, truth, target, true, active, textGraph, termTimeGraph,
				activityGraph, fitted.text, fitted.activity, scores, scale, weights, bias, posterior, heldOut, oof, threshold,
				validation, folds, millis, "");
	}

	/** Learner scores of some users and the logistic models behind them. */
	private static final class Learners
	{
		final double[][][] scores;
		final TgfLearning.Logistic text;
		final TgfLearning.Logistic activity;

		Learners(double[][][] scores, TgfLearning.Logistic text, TgfLearning.Logistic activity)
		{
			this.scores = scores;
			this.text = text;
			this.activity = activity;
		}
	}

	/**
	 * Each learner's centred class scores for {@code users} given the visible
	 * labels. Logistic models return logits, graph and vote learners the log of
	 * their class distribution.
	 */
	private static Learners channelScores(TgfFeatures features, int[] label, int classes, boolean[] active,
			TgfLearning.Graph textGraph, TgfLearning.Graph termTimeGraph, TgfLearning.Graph activityGraph, int[] users)
	{
		double[][][] scores = new double[CHANNELS][users.length][classes];
		int[] train = labelledUsers(label);
		int textIterations = iterationBudget(features.text, classes);
		TgfLearning.Logistic text = TgfLearning.logistic(features.text, train, label, classes, TEXT_LAMBDA,
				textIterations);
		TgfLearning.Logistic activity = active[ACTIVITY] ? TgfLearning.logistic(features.activity, train, label,
				classes, ACTIVITY_LAMBDA, iterationBudget(features.activity, classes)) : null;
		double[][] textMass = TgfLearning.propagate(textGraph, label, classes, ALPHA);
		double[][] termMass = active[TERM_TIME_GRAPH] ? TgfLearning.propagate(termTimeGraph, label, classes, ALPHA)
				: null;
		for (int i = 0; i < users.length; i++)
		{
			int u = users[i];
			scores[TEXT][i] = centred(text.logits(features.text, u));
			scores[TEXT_GRAPH][i] = centred(logDistribution(textMass[u]));
			if (termMass != null)
			{
				scores[TERM_TIME_GRAPH][i] = centred(logDistribution(termMass[u]));
			}
			if (activity != null)
			{
				scores[ACTIVITY][i] = centred(activity.logits(features.activity, u));
			}
			scores[TEXT_NEIGHBOURS][i] = centred(log(TgfLearning.vote(textGraph, u, label, classes, NEIGHBOURS)));
			if (active[ACTIVITY_NEIGHBOURS])
			{
				scores[ACTIVITY_NEIGHBOURS][i] = centred(log(TgfLearning.vote(activityGraph, u, label, classes,
						NEIGHBOURS)));
			}
		}
		return new Learners(scores, text, activity);
	}

	/**
	 * L-BFGS iterations for a logistic learner: up to 300, fewer when one pass
	 * over the data is costly (about 3e9 multiply-adds in all), never below 30.
	 */
	static int iterationBudget(TgfFeatures.Sparse rows, int classes)
	{
		double pass = Math.max(1.0, (double)rows.nonZeros() * classes);
		return (int)Math.max(30, Math.min(300, 3.0e9 / pass));
	}

	private static TgfModel unsupervised(TgfFeatures features, String[] classes, int[] label, int[] truth,
			boolean[] target, TgfLearning.Graph textGraph, TgfLearning.Graph termTimeGraph,
			TgfLearning.Graph activityGraph, long started, String note)
	{
		int n = features.size();
		int classCount = Math.max(classes.length, 0);
		double[][] posterior = new double[n][classCount];
		for (double[] row : posterior)
		{
			Arrays.fill(row, classCount == 0 ? 0.0 : 1.0 / classCount);
		}
		boolean[] active = new boolean[CHANNELS];
		Validation validation = new Validation(0, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN,
				Double.NaN, Double.NaN, new double[0][], new int[classCount][classCount], new double[classCount],
				new double[classCount], new double[classCount], new int[classCount], new double[CHANNELS],
				new double[CHANNELS], Double.NaN, Double.NaN);
		return new TgfModel(features, classes, label, truth, target, false, active, textGraph, termTimeGraph,
				activityGraph, null, null, new double[CHANNELS][n][classCount], new double[CHANNELS],
				new double[CHANNELS], new double[classCount], posterior, new double[n][], new double[CHANNELS][n][],
				1.0, validation, 0, (System.nanoTime() - started) / 1000000L, note);
	}

	// ========================================================== validation

	private static Validation validate(double[][] heldOut, double[][][] oof, int[] calibration, int[] label,
			int classes, int[] foldOf, int folds, boolean[] active)
	{
		int n = calibration.length;
		int[][] confusion = new int[classes][classes];
		int[] support = new int[classes];
		int correct = 0;
		double logLoss = 0.0;
		double brier = 0.0;
		int bins = 10;
		double[][] reliability = new double[bins][3];
		for (int i = 0; i < n; i++)
		{
			int u = calibration[i];
			double[] p = heldOut[u];
			int y = label[u];
			int predicted = argmax(p);
			confusion[y][predicted]++;
			support[y]++;
			correct += predicted == y ? 1 : 0;
			logLoss -= Math.log(Math.max(1e-15, p[y]));
			for (int c = 0; c < classes; c++)
			{
				double e = p[c] - (c == y ? 1.0 : 0.0);
				brier += e * e;
			}
			double confidence = p[predicted];
			int bin = Math.min(bins - 1, (int)Math.floor(confidence * bins));
			reliability[bin][0] += 1.0;
			reliability[bin][1] += confidence;
			reliability[bin][2] += predicted == y ? 1.0 : 0.0;
		}
		double calibrationError = 0.0;
		for (double[] bin : reliability)
		{
			if (bin[0] > 0)
			{
				bin[1] /= bin[0];
				bin[2] /= bin[0];
				calibrationError += bin[0] / n * Math.abs(bin[1] - bin[2]);
			}
		}
		double[] precision = new double[classes];
		double[] recall = new double[classes];
		double[] f1 = new double[classes];
		double macro = 0.0;
		int present = 0;
		int majority = 0;
		double chance = 0.0;
		for (int c = 0; c < classes; c++)
		{
			int predictedC = 0;
			for (int a = 0; a < classes; a++)
			{
				predictedC += confusion[a][c];
			}
			precision[c] = predictedC == 0 ? 0.0 : confusion[c][c] / (double)predictedC;
			recall[c] = support[c] == 0 ? 0.0 : confusion[c][c] / (double)support[c];
			f1[c] = precision[c] + recall[c] > 0 ? 2 * precision[c] * recall[c] / (precision[c] + recall[c]) : 0.0;
			if (support[c] > 0)
			{
				macro += f1[c];
				present++;
			}
			majority = Math.max(majority, support[c]);
			double share = support[c] / (double)Math.max(1, n);
			chance += share * share;
		}
		double[] channelAccuracy = new double[CHANNELS];
		double[] channelLoss = new double[CHANNELS];
		for (int m = 0; m < CHANNELS; m++)
		{
			if (!active[m])
			{
				channelAccuracy[m] = Double.NaN;
				channelLoss[m] = Double.NaN;
				continue;
			}
			int hits = 0;
			double loss = 0.0;
			for (int u : calibration)
			{
				double[] s = oof[m][u];
				hits += argmax(s) == label[u] ? 1 : 0;
				double[] p = TgfLearning.softmax(s);
				loss -= Math.log(Math.max(1e-15, p[label[u]]));
			}
			channelAccuracy[m] = hits / (double)n;
			channelLoss[m] = loss / n;
		}
		//Cross-conformal check: the threshold from the other folds, coverage on this one.
		int covered = 0;
		double setSize = 0.0;
		for (int f = 0; f < folds; f++)
		{
			ArrayList<Double> scores = new ArrayList<Double>();
			for (int i = 0; i < n; i++)
			{
				if (foldOf[i] != f)
				{
					scores.add(Double.valueOf(1.0 - heldOut[calibration[i]][label[calibration[i]]]));
				}
			}
			double[] values = new double[scores.size()];
			for (int i = 0; i < values.length; i++)
			{
				values[i] = scores.get(i).doubleValue();
			}
			double q = TgfLearning.conformalThreshold(values, CONFORMAL_ALPHA);
			for (int i = 0; i < n; i++)
			{
				if (foldOf[i] != f)
				{
					continue;
				}
				double[] p = heldOut[calibration[i]];
				int size = 0;
				boolean holds = false;
				for (int c = 0; c < classes; c++)
				{
					if (1.0 - p[c] <= q || c == argmax(p))
					{
						size++;
						holds |= c == label[calibration[i]];
					}
				}
				covered += holds ? 1 : 0;
				setSize += size;
			}
		}
		return new Validation(n, correct / (double)n, present == 0 ? 0.0 : macro / present, logLoss / n,
				brier / n, calibrationError, majority / (double)n, chance, reliability, confusion, precision, recall,
				f1, support, channelAccuracy, channelLoss, covered / (double)n, setSize / n);
	}

	// ============================================================ helpers

	/** The prediction set of a posterior: classes whose nonconformity is within the threshold (the top one always). */
	boolean[] predictionSet(double[] p)
	{
		boolean[] set = new boolean[p.length];
		int best = argmax(p);
		for (int c = 0; c < p.length; c++)
		{
			set[c] = c == best || 1.0 - p[c] <= conformalThreshold;
		}
		return set;
	}

	/** The pool's logit for class c of user u, per learner: weights[m] * scores[m][u][c]. */
	double contribution(int channel, int user, int c)
	{
		return active[channel] ? weights[channel] * scores[channel][user][c] : 0.0;
	}

	static double[] posteriorOf(double[][][] scores, int user, double[] theta, int classes)
	{
		double[] logits = new double[classes];
		for (int c = 0; c < classes; c++)
		{
			logits[c] = theta[CHANNELS + c];
			for (int m = 0; m < CHANNELS; m++)
			{
				if (scores[m][user] != null)
				{
					logits[c] += theta[m] * scores[m][user][c];
				}
			}
		}
		return TgfLearning.softmax(logits);
	}

	private static double[][][] stackRows(double[][][] oof, int[] users, boolean[] active)
	{
		double[][][] rows = new double[CHANNELS][users.length][];
		for (int m = 0; m < CHANNELS; m++)
		{
			for (int i = 0; i < users.length; i++)
			{
				rows[m][i] = oof[m][users[i]];
			}
		}
		return rows;
	}

	/** Users' monthly activity as L2-normalised sparse rows (for the activity graph). */
	private static TgfFeatures.Sparse monthlyRows(TgfFeatures features)
	{
		int n = features.size();
		int[] pointer = new int[n + 1];
		ArrayList<Integer> columns = new ArrayList<Integer>();
		ArrayList<Double> values = new ArrayList<Double>();
		for (int u = 0; u < n; u++)
		{
			pointer[u] = columns.size();
			double norm = 0.0;
			for (double x : features.monthly[u])
			{
				norm += x * x;
			}
			norm = Math.sqrt(norm);
			for (int m = 0; m < features.monthly[u].length; m++)
			{
				if (features.monthly[u][m] > 0.0 && norm > 0.0)
				{
					columns.add(Integer.valueOf(m));
					values.add(Double.valueOf(features.monthly[u][m] / norm));
				}
			}
		}
		pointer[n] = columns.size();
		int[] column = new int[columns.size()];
		double[] value = new double[columns.size()];
		for (int i = 0; i < column.length; i++)
		{
			column[i] = columns.get(i).intValue();
			value[i] = values.get(i).doubleValue();
		}
		return new TgfFeatures.Sparse(pointer, column, value, features.months);
	}

	static int[] labelledUsers(int[] label)
	{
		int count = 0;
		for (int value : label)
		{
			count += value >= 0 ? 1 : 0;
		}
		int[] users = new int[count];
		int i = 0;
		for (int u = 0; u < label.length; u++)
		{
			if (label[u] >= 0)
			{
				users[i++] = u;
			}
		}
		return users;
	}

	/** Each class's users (in index order) shuffled by a fixed seed and dealt round-robin. */
	private static int[] stratifiedFolds(int[] users, int[] label, int classes, int folds)
	{
		int[] foldOf = new int[users.length];
		Random random = new Random(SEED);
		int next = 0;
		for (int c = 0; c < classes; c++)
		{
			List<Integer> members = new ArrayList<Integer>();
			for (int i = 0; i < users.length; i++)
			{
				if (label[users[i]] == c)
				{
					members.add(Integer.valueOf(i));
				}
			}
			Collections.shuffle(members, random);
			for (Integer i : members)
			{
				foldOf[i.intValue()] = next % folds;
				next++;
			}
		}
		return foldOf;
	}

	private static int[] allUsers(int n)
	{
		int[] users = new int[n];
		for (int u = 0; u < n; u++)
		{
			users[u] = u;
		}
		return users;
	}

	private static int[] toArray(List<Integer> values)
	{
		int[] array = new int[values.size()];
		for (int i = 0; i < array.length; i++)
		{
			array[i] = values.get(i).intValue();
		}
		return array;
	}

	static int argmax(double[] values)
	{
		int best = 0;
		for (int i = 1; i < values.length; i++)
		{
			if (values[i] > values[best])
			{
				best = i;
			}
		}
		return best;
	}

	static double[] centred(double[] values)
	{
		double mean = 0.0;
		for (double value : values)
		{
			mean += value;
		}
		mean /= Math.max(1, values.length);
		double[] result = new double[values.length];
		for (int i = 0; i < values.length; i++)
		{
			result[i] = values[i] - mean;
		}
		return result;
	}

	private static double[] logDistribution(double[] mass)
	{
		double total = 0.0;
		for (double value : mass)
		{
			total += Math.max(0.0, value);
		}
		double[] result = new double[mass.length];
		for (int c = 0; c < mass.length; c++)
		{
			result[c] = Math.log((Math.max(0.0, mass[c]) + 1e-6) / (total + 1e-6 * mass.length));
		}
		return result;
	}

	private static double[] log(double[] p)
	{
		double[] result = new double[p.length];
		for (int c = 0; c < p.length; c++)
		{
			result[c] = Math.log(Math.max(1e-12, p[c]));
		}
		return result;
	}

	/** Wire size of the fitted state every node needs. */
	long payloadBytes()
	{
		long n = size();
		long classes = classCount();
		long total = features.payloadBytes();
		total += 12L * (textGraph.edges() * 2 + n * KEEP);
		total += termTimeGraph == null ? 0L : 12L * (termTimeGraph.edges() * 2 + n * KEEP);
		total += activityGraph == null ? 0L : 12L * (activityGraph.edges() * 2 + n * KEEP);
		total += textModel == null ? 0L : 8L * classes * (features.text.columns + 1);
		total += activityModel == null ? 0L : 8L * classes * (features.activity.columns + 1);
		total += 8L * n * classes * (CHANNELS + 2);
		return total;
	}
}
