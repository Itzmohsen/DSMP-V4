package TwitterGatherDataFollowers.userRyersonU;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

/**
 * The Temporal Graph Foundation model: the target's own follow is never read,
 * nothing outside the run changes the result, the result is deterministic and
 * right on separable communities, every explanation adds up exactly, and the
 * learning machinery (label propagation, logistic regression, the stack, the
 * conformal threshold) is correct on its own.
 */
public final class TemporalGraphFoundationModelSupportSelfTest
{
	private static final String[] COMMUNITIES = {"@research", "@kitchen", "@travel"};
	private static final String[][] VOCABULARY = {
			{"graph", "foundation", "ranking", "model", "embedding", "citation"},
			{"recipe", "garden", "oven", "pasta", "harvest", "spice"},
			{"flight", "hotel", "beach", "passport", "museum", "train"}};

	private TemporalGraphFoundationModelSupportSelfTest()
	{
	}

	public static void main(String[] args) throws Exception
	{
		learningMachinery();
		File directory = java.nio.file.Files.createTempDirectory("dsmp-tgf").toFile();
		File raw = new File(directory, "tgf-self-test.txt");
		Map<String,String> followees = new LinkedHashMap<String,String>();
		Map<String,Map<String,Double>> tfidf = new LinkedHashMap<String,Map<String,Double>>();
		writeCorpus(raw, followees, tfidf);
		File corpus = asRunCorpus(raw, directory);
		List<String> targets = new ArrayList<String>();
		targets.add("user-0-0");
		targets.add("user-1-0");
		targets.add("user-2-0");

		TemporalGraphFoundationModelSupport.Result result = score(corpus, tfidf, followees, targets);
		TgfReport report = TgfReport.of(result);
		require(report.supervised, "the model was not learned: " + report.note);
		require(report.learners.size() == TgfModel.CHANNELS, "expected six learners");
		require(report.learner("term-time-graph").active && report.learner("activity").active,
				"the dated corpus should switch the temporal learners on");

		//1. Scores are probabilities: users in (0, 1], accounts summing to one.
		for (String target : targets)
		{
			TreeMap<String,Double> users = result.getScores().get(target);
			require(users != null && users.size() > 3, "missing user ranking for " + target);
			for (double p : users.values())
			{
				//A probability plus the 1e-6 text-similarity tie-break.
				require(p > 0.0 && p <= 1.0 + TemporalGraphFoundationModelSupport.TIE_BREAK,
						"a user score is not a probability: " + p);
			}
			//Users of one community are delivered by text similarity, not by name.
			List<Map.Entry<String,Double>> delivered = new ArrayList<Map.Entry<String,Double>>(users.entrySet());
			java.util.Collections.sort(delivered, new java.util.Comparator<Map.Entry<String,Double>>()
			{
				public int compare(Map.Entry<String,Double> a, Map.Entry<String,Double> b)
				{
					return Double.compare(b.getValue(), a.getValue());
				}
			});
			TgfReport.Target explained = TgfReport.of(result).target(target);
			for (int i = 0; i + 1 < Math.min(5, delivered.size()); i++)
			{
				require(explained.recommendations.get(i).user.equals(delivered.get(i).getKey()),
						"the delivered order differs from the explanation's at rank " + (i + 1));
			}
			double total = 0.0;
			for (double p : result.getAccountScores().get(target).values())
			{
				total += p;
			}
			require(Math.abs(total - 1.0) < 1e-9, "account probabilities of " + target + " sum to " + total);
		}

		//2. The targets' own follows are never read.
		Map<String,String> relabelled = new LinkedHashMap<String,String>(followees);
		relabelled.put("user-0-0", "@travel");
		relabelled.put("user-1-0", "");
		TemporalGraphFoundationModelSupport.Result blind = score(corpus, tfidf, relabelled, targets);
		require(result.getScores().equals(blind.getScores()), "changing the targets' own follows changed the users");
		require(result.getAccountScores().equals(blind.getAccountScores()),
				"changing the targets' own follows changed the accounts");

		//3. Nothing outside the run's corpus changes the result.
		File importantStuff = new File("important-stuff");
		boolean created = importantStuff.mkdirs();
		File edges = new File(importantStuff, "edges-numbers-tgf-self-test.txt");
		try
		{
			writeLines(edges, "1 2", "1 2", "1 2");
			require(result.getScores().equals(score(corpus, tfidf, followees, targets).getScores()),
					"a file outside the run changed the rankings");
		}
		finally
		{
			edges.delete();
			if (created)
			{
				importantStuff.delete();
			}
		}

		//4. Deterministic, report included (all but the fit's wall-clock time).
		TemporalGraphFoundationModelSupport.Result again = score(corpus, tfidf, followees, targets);
		require(JsonText.of(withoutTiming(result)).equals(JsonText.of(withoutTiming(again))),
				"two identical runs differ");

		//5. Right on separable communities.
		for (TgfReport.Target target : report.targets)
		{
			require(target.truth != null && target.truth.equals(target.predicted),
					target.user + " follows " + target.truth + " but was predicted " + target.predicted);
			require(target.set.contains(target.predicted), "the conformal set must hold the most likely account");
			TgfReport.Recommendation first = target.recommendations.get(0);
			require(Boolean.TRUE.equals(first.relevant), target.user + "'s first recommended user is not in its"
					+ " community");
		}
		require(report.validation.accuracy > 0.9, "cross-validated accuracy should be near 1, got "
				+ report.validation.accuracy);

		//6. Every explanation adds up exactly.
		for (TgfReport.Target target : report.targets)
		{
			double sum = target.prior;
			for (TgfReport.TargetLearner learner : target.learners)
			{
				sum += learner.active ? learner.contribution : 0.0;
			}
			require(Math.abs(sum - target.logOdds) < 1e-8, "learner contributions " + sum
					+ " do not add up to the log-odds " + target.logOdds);
			TgfReport.TargetLearner text = target.learners.get(TgfModel.TEXT);
			require(Math.abs(target.termTotal + target.textIntercept - text.contribution) < 1e-8,
					"the text model's words and intercept do not add up to its contribution");
			double shares = 0.0;
			for (TgfReport.Neighbour neighbour : target.textNeighbours)
			{
				require(neighbour.influence > 0.0, "a listed neighbour has no influence");
				shares += neighbour.share;
			}
			require(shares <= 1.0 + 1e-9, "influence shares exceed one");
			if (target.counterfactualFound)
			{
				require(!target.counterfactualTerms.isEmpty() && target.counterfactualFlipsTo != null
						&& !target.counterfactualFlipsTo.equals(target.predicted), "malformed counterfactual");
			}
		}

		//7. The record round trip keeps everything the studio shows.
		File record = new File(directory, "record");
		record.mkdirs();
		TgfReport.write(record, result);
		TgfReport loaded = TgfReport.load(record);
		require(loaded != null && loaded.targets.size() == report.targets.size(), "the record lost targets");
		for (int i = 0; i < report.targets.size(); i++)
		{
			TgfReport.Target live = report.targets.get(i);
			TgfReport.Target stored = loaded.targets.get(i);
			require(live.predicted.equals(stored.predicted) && Math.abs(live.probability - stored.probability) < 1e-8
					&& live.recommendations.size() == stored.recommendations.size(),
					"the record changed " + live.user + "'s report");
		}
		require(new File(new File(record, TgfReport.FOLDER), "predictions.csv").isFile(), "missing predictions.csv");

		//8. Undated: the temporal learners are off and the rest still works.
		File undated = new File(directory, "tgf-undated.txt");
		writeUndated(raw, undated);
		TgfReport plain = TgfReport.of(score(undated, tfidf, followees, targets));
		require(!plain.learner("term-time-graph").active && !plain.learner("activity").active
				&& !plain.learner("activity-neighbours").active, "undated rows must not produce temporal learners");
		require(plain.supervised && plain.targets.get(0).predicted.equals("@research"), "undated TGF failed");

		//9. A raw dataset file (no run corpus) gives the same model as its run corpus.
		TemporalGraphFoundationModelSupport.Result fromRaw = score(raw, tfidf, followees, targets);
		require(fromRaw.getAccountScores().equals(result.getAccountScores()),
				"the raw dataset and its run corpus gave different models");

		//10. One labelled account: no model, users ranked by text similarity.
		Map<String,String> single = new LinkedHashMap<String,String>();
		for (String user : followees.keySet())
		{
			single.put(user, "@only");
		}
		TemporalGraphFoundationModelSupport.Result fallback = score(corpus, tfidf, single, targets);
		require(!TgfReport.of(fallback).supervised, "a single account cannot be learned");
		require(fallback.getScores().get("user-0-0").size() > 3, "the fallback must still rank users");

		System.out.println("TemporalGraphFoundationModelSupportSelfTest passed");
	}

	/** Label propagation, its influence rows, logistic regression, the stack and the conformal threshold. */
	private static void learningMachinery()
	{
		Random random = new Random(11L);
		int n = 40;
		int classes = 3;
		int[] label = new int[n];
		//Clustered points on a circle: users of a class are close.
		int[] pointer = new int[n + 1];
		int[] column = new int[n * 3];
		double[] value = new double[n * 3];
		for (int u = 0; u < n; u++)
		{
			label[u] = u % classes;
			double[] x = {0.2, 0.2, 0.2};
			x[label[u]] = 1.0 + random.nextDouble();
			x[(label[u] + 1) % classes] += 0.3 * random.nextDouble();
			double norm = Math.sqrt(x[0] * x[0] + x[1] * x[1] + x[2] * x[2]);
			pointer[u] = 3 * u;
			for (int d = 0; d < 3; d++)
			{
				column[3 * u + d] = d;
				value[3 * u + d] = x[d] / norm;
			}
		}
		pointer[n] = 3 * n;
		TgfFeatures.Sparse rows = new TgfFeatures.Sparse(pointer, column, value, 3);
		TgfLearning.Graph graph = TgfLearning.knn(rows, 5, 10);
		int[] visible = label.clone();
		for (int u = 0; u < n; u += 4)
		{
			visible[u] = -1;
		}
		double alpha = 0.9;
		double[][] mass = TgfLearning.propagate(graph, visible, classes, alpha);
		//(I - alpha S) F = (1 - alpha) Y holds.
		for (int u = 0; u < n; u++)
		{
			for (int c = 0; c < classes; c++)
			{
				double residual = mass[u][c] - (visible[u] == c ? 1.0 - alpha : 0.0);
				for (int i = 0; i < graph.adjacent[u].length; i++)
				{
					residual -= alpha * graph.normalised[u][i] * mass[graph.adjacent[u][i]][c];
				}
				require(Math.abs(residual) < 1e-8, "label propagation residual " + residual);
			}
		}
		//The class mass is exactly the sum of the labelled users' influence.
		for (int u = 0; u < n; u += 7)
		{
			double[] influence = TgfLearning.influence(graph, u, alpha);
			for (int c = 0; c < classes; c++)
			{
				double sum = 0.0;
				for (int v = 0; v < n; v++)
				{
					sum += visible[v] == c ? influence[v] : 0.0;
				}
				require(Math.abs(sum - mass[u][c]) < 1e-8, "influence " + sum + " does not give the mass " + mass[u][c]);
			}
		}
		//Logistic regression: separable clusters are learned, and the penalised gradient vanishes.
		int[] train = TgfModel.labelledUsers(visible);
		TgfLearning.Logistic logistic = TgfLearning.logistic(rows, train, visible, classes, 1e-3, 300);
		require(logistic.converged, "logistic regression did not converge");
		int correct = 0;
		for (int u = 0; u < n; u++)
		{
			correct += TgfModel.argmax(logistic.logits(rows, u)) == label[u] ? 1 : 0;
		}
		require(correct >= n - 2, "logistic regression misclassified " + (n - correct) + " clustered users");
		double[][] gradient = new double[classes][4];
		for (int u : train)
		{
			double[] p = TgfLearning.softmax(logistic.logits(rows, u));
			for (int c = 0; c < classes; c++)
			{
				double r = (p[c] - (c == visible[u] ? 1.0 : 0.0)) / train.length;
				for (int q = rows.pointer[u]; q < rows.pointer[u + 1]; q++)
				{
					gradient[c][rows.column[q]] += r * rows.value[q];
				}
				gradient[c][3] += r;
			}
		}
		for (int c = 0; c < classes; c++)
		{
			for (int d = 0; d < 3; d++)
			{
				gradient[c][d] += 1e-3 * logistic.weights[c][d];
				require(Math.abs(gradient[c][d]) < 1e-4, "logistic gradient not zero at the optimum: " + gradient[c][d]);
			}
			require(Math.abs(gradient[c][3]) < 1e-4, "intercept gradient not zero: " + gradient[c][3]);
		}
		//The stack: an informative channel gets weight, pure noise none.
		double[][][] scores = new double[2][n][classes];
		for (int u = 0; u < n; u++)
		{
			for (int c = 0; c < classes; c++)
			{
				scores[0][u][c] = (c == label[u] ? 1.0 : -0.5) + 0.3 * random.nextGaussian();
				scores[1][u][c] = random.nextGaussian();
			}
		}
		double[] theta = TgfLearning.pool(scores, label, classes, 1e-2, 1e-1);
		require(theta[0] > 0.5, "the informative channel got weight " + theta[0]);
		require(theta[1] >= 0.0 && theta[1] < 0.25, "the noise channel got weight " + theta[1]);
		//Conformal threshold: the ceil((n + 1)(1 - alpha))-th smallest score.
		double[] nonconformity = {0.9, 0.1, 0.5, 0.3, 0.7, 0.2, 0.8, 0.4, 0.6};
		require(TgfLearning.conformalThreshold(nonconformity, 0.2) == 0.8, "conformal threshold");
		require(TgfLearning.conformalThreshold(nonconformity, 0.05) == 1.0, "too few users: the full set");
	}

	private static Map<String,Object> withoutTiming(TemporalGraphFoundationModelSupport.Result result)
	{
		LinkedHashMap<String,Object> json = new LinkedHashMap<String,Object>(result.toJson());
		@SuppressWarnings("unchecked")
		LinkedHashMap<String,Object> fit = new LinkedHashMap<String,Object>((Map<String,Object>)json.get("fit"));
		fit.remove("millis");
		json.put("fit", fit);
		return json;
	}

	private static TemporalGraphFoundationModelSupport.Result score(File dataset,
			Map<String,Map<String,Double>> tfidf, Map<String,String> followees, List<String> targets)
	{
		return TemporalGraphFoundationModelSupport.score(new TemporalGraphFoundationModelSupport.Input(
				"self-test", dataset, tfidf, followees, followees, targets,
				Collections.<String,Integer>emptyMap(), 1, 1, 20));
	}

	/**
	 * Three communities of twelve users. Each user mostly uses its community's
	 * words plus a little shared noise, and is active in its community's
	 * season, so text, graph and time all carry signal.
	 */
	private static void writeCorpus(File dataset, Map<String,String> followees,
			Map<String,Map<String,Double>> tfidf) throws Exception
	{
		Random random = new Random(7L);
		String[] noise = {"sunny", "weekend", "coffee", "photo", "friends"};
		BufferedWriter writer = new BufferedWriter(new FileWriter(dataset));
		try
		{
			long id = 1;
			for (int community = 0; community < COMMUNITIES.length; community++)
			{
				for (int member = 0; member < 12; member++)
				{
					String user = "user-" + community + "-" + member;
					followees.put(user, COMMUNITIES[community]);
					LinkedHashMap<String,Double> vector = new LinkedHashMap<String,Double>();
					for (int tweet = 0; tweet < 6; tweet++)
					{
						String a = VOCABULARY[community][random.nextInt(6)];
						String b = VOCABULARY[community][random.nextInt(6)];
						String c = VOCABULARY[community][random.nextInt(6)];
						String d = random.nextInt(4) == 0 ? VOCABULARY[random.nextInt(3)][random.nextInt(6)]
								: noise[random.nextInt(noise.length)];
						for (String term : new String[] {a, b, c, d})
						{
							Double old = vector.get(term);
							vector.put(term, Double.valueOf(old == null ? 1.0 : old.doubleValue() + 1.0));
						}
						int month = 1 + community * 4 + random.nextInt(4);
						writer.write(COMMUNITIES[community] + "\t" + (id++) + "\t2025-"
								+ (month < 10 ? "0" + month : "" + month) + "-1" + random.nextInt(9) + " 12:00:00\t"
								+ (100 + community) + "\t" + user + "\t" + a + " " + b + " " + c + " " + d + "\n");
					}
					tfidf.put(user, vector);
				}
			}
		}
		finally
		{
			writer.close();
		}
	}

	/** The run corpus of a raw dataset, written by the application's own RunCorpus. */
	private static File asRunCorpus(File raw, File directory) throws Exception
	{
		final LinkedHashMap<String,List<Tweet>> tweets = new LinkedHashMap<String,List<Tweet>>();
		final LinkedHashMap<String,String> followees = new LinkedHashMap<String,String>();
		for (String line : java.nio.file.Files.readAllLines(raw.toPath()))
		{
			String[] fields = line.split("\t", 6);
			List<Tweet> list = tweets.get(fields[4]);
			if (list == null)
			{
				list = new ArrayList<Tweet>();
				tweets.put(fields[4], list);
			}
			list.add(new Tweet(fields[5], Long.parseLong(fields[1]), fields[2], fields[4]));
			followees.put(fields[4], fields[0]);
		}
		return RunCorpus.write(new File(directory, "corpus"), tweets.keySet(), tweets, followees, true, true, true,
				ControllerAgent.STOP_WORD_SET);
	}

	private static void writeUndated(File dated, File undated) throws Exception
	{
		List<String> lines = java.nio.file.Files.readAllLines(dated.toPath());
		BufferedWriter writer = new BufferedWriter(new FileWriter(undated));
		try
		{
			for (String line : lines)
			{
				String[] fields = line.split("\t", 6);
				writer.write(fields[0] + "\t" + fields[1] + "\t\t" + fields[3] + "\t" + fields[4] + "\t" + fields[5] + "\n");
			}
		}
		finally
		{
			writer.close();
		}
	}

	private static void writeLines(File file, String... lines) throws Exception
	{
		BufferedWriter writer = new BufferedWriter(new FileWriter(file));
		try
		{
			for (String line : lines)
			{
				writer.write(line);
				writer.newLine();
			}
		}
		finally
		{
			writer.close();
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
