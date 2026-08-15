package TwitterGatherDataFollowers.userRyersonU;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

import org.neuroph.core.NeuralNetwork;
import org.neuroph.core.data.DataSet;
import org.neuroph.core.data.DataSetRow;
import org.neuroph.nnet.MultiLayerPerceptron;
import org.neuroph.nnet.learning.BackPropagation;
import org.neuroph.util.TransferFunctionType;

/**
 * Exercises the universal corpus through the active sparse distance functions
 * and the real MLP dimensional structure without launching JADE.
 */
public final class UniversalHeavyAlgorithmSmokeTest
{
	private UniversalHeavyAlgorithmSmokeTest()
	{
	}

	public static void main(String[] args) throws Exception
	{
		File file = args.length > 0
				? new File(args[0])
				: new File(
						"Dataset/Benchmarks/"
						+ "Universal-Heavy-All-Working-Algorithms-76k.txt");
		Corpus corpus = load(file);
		validateSimilarityAndKMeansDistances(corpus);
		validateMlpShape(corpus);
		System.out.println(
				"UniversalHeavyAlgorithmSmokeTest passed: "
				+ corpus.documents.size() + " users, "
				+ corpus.vocabulary.size() + " features, "
				+ corpus.classes.size() + " classes.");
	}

	private static Corpus load(File file) throws Exception
	{
		TreeMap<String,LinkedHashMap<String,Double>> documents =
				new TreeMap<String,LinkedHashMap<String,Double>>();
		TreeMap<String,String> userClasses = new TreeMap<String,String>();
		TreeSet<String> vocabulary = new TreeSet<String>();
		TreeSet<String> classes = new TreeSet<String>();
		long lineNumber = 0L;
		try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(
						new FileInputStream(file),
						StandardCharsets.UTF_8),
				512 * 1024))
		{
			String line;
			while ((line = reader.readLine()) != null)
			{
				TwitterGatherDataFollowers.userRyersonU.DatasetRow row =
						TwitterGatherDataFollowers.userRyersonU.DatasetRow.parse(
								line, ++lineNumber);
				userClasses.put(row.userName, row.referenceUser);
				classes.add(row.referenceUser);
				LinkedHashMap<String,Double> document =
						documents.get(row.userName);
				if (document == null)
				{
					document = new LinkedHashMap<String,Double>();
					documents.put(row.userName, document);
				}
				for (String token : row.text.split(" "))
				{
					vocabulary.add(token);
					Double old = document.get(token);
					document.put(token, old == null ? 1.0 : old + 1.0);
				}
			}
		}
		require(documents.size() == 480, "unexpected document count");
		require(vocabulary.size() == 24576, "unexpected feature count");
		require(classes.size() == 16, "unexpected class count");
		return new Corpus(documents, userClasses, vocabulary, classes);
	}

	private static void validateSimilarityAndKMeansDistances(Corpus corpus)
	{
		String targetName = "UniversalAuthorC00M00";
		Point target = point(targetName, corpus);
		Point sameJournal = point("UniversalAuthorC00M05", corpus);
		Point unrelated = point("UniversalAuthorC08M00", corpus);

		double sameCosine = target.cosSimDistance(sameJournal);
		double unrelatedCosine = target.cosSimDistance(unrelated);
		require(Double.isFinite(sameCosine) && Double.isFinite(unrelatedCosine),
				"cosine score is not finite");
		require(sameCosine > unrelatedCosine,
				"same-journal cosine score is not stronger");

		double sameEuclidean = target.eucliDistance(sameJournal);
		double unrelatedEuclidean = target.eucliDistance(unrelated);
		require(Double.isFinite(sameEuclidean)
						&& Double.isFinite(unrelatedEuclidean),
				"Euclidean score is not finite");
		require(sameEuclidean < unrelatedEuclidean,
				"same-journal Euclidean distance is not closer");

		List<Point> centroids = new ArrayList<Point>();
		centroids.add(point("UniversalAuthorC00M00", corpus));
		centroids.add(point("UniversalAuthorC05M00", corpus));
		centroids.add(point("UniversalAuthorC10M00", corpus));
		int cosineAssignments = 0;
		int euclideanAssignments = 0;
		for (String user : corpus.documents.keySet())
		{
			Point candidate = point(user, corpus);
			double bestCosine = -Double.MAX_VALUE;
			double bestEuclidean = Double.MAX_VALUE;
			for (Point centroid : centroids)
			{
				double cosine = candidate.cosSimDistance(centroid);
				double euclidean = candidate.eucliDistance(centroid);
				require(Double.isFinite(cosine) && Double.isFinite(euclidean),
						"K-Means distance is not finite for " + user);
				bestCosine = Math.max(bestCosine, cosine);
				bestEuclidean = Math.min(bestEuclidean, euclidean);
			}
			if (bestCosine > -Double.MAX_VALUE)
			{
				cosineAssignments++;
			}
			if (bestEuclidean < Double.MAX_VALUE)
			{
				euclideanAssignments++;
			}
		}
		require(cosineAssignments == corpus.documents.size(),
				"cosine K-Means could not assign every user");
		require(euclideanAssignments == corpus.documents.size(),
				"Euclidean K-Means could not assign every user");
	}

	private static void validateMlpShape(Corpus corpus)
	{
		List<String> features = new ArrayList<String>(corpus.vocabulary);
		List<String> classes = new ArrayList<String>(corpus.classes);
		DataSet data = new DataSet(features.size(), classes.size());
		TreeSet<String> represented = new TreeSet<String>();
		for (Map.Entry<String,LinkedHashMap<String,Double>> entry
				: corpus.documents.entrySet())
		{
			String className = corpus.userClasses.get(entry.getKey());
			if (!represented.add(className))
			{
				continue;
			}
			double[] input = dense(entry.getValue(), features);
			double[] output = new double[classes.size()];
			output[classes.indexOf(className)] = 1.0;
			data.addRow(new DataSetRow(input, output));
		}
		require(data.size() == classes.size(),
				"MLP smoke data does not represent every class");

		MultiLayerPerceptron model = new MultiLayerPerceptron(
				TransferFunctionType.TANH,
				features.size(),
				10,
				classes.size());
		BackPropagation learning = (BackPropagation)model.getLearningRule();
		learning.setLearningRate(0.1);
		learning.setMaxError(0.0);
		learning.setMaxIterations(1);
		model.learn(data);
		NeuralNetwork network = model;
		network.setInput(data.getRowAt(0).getInput());
		network.calculate();
		double[] output = network.getOutput();
		require(output.length == classes.size(),
				"MLP output dimension changed");
		for (double value : output)
		{
			require(Double.isFinite(value), "MLP produced a non-finite output");
		}
	}

	private static Point point(String user, Corpus corpus)
	{
		LinkedHashMap<String,Double> values = corpus.documents.get(user);
		require(values != null, "missing document for " + user);
		return new Point(user, values);
	}

	private static double[] dense(
			Map<String,Double> values,
			List<String> features)
	{
		double[] vector = new double[features.size()];
		for (int index = 0; index < features.size(); index++)
		{
			Double value = values.get(features.get(index));
			if (value != null)
			{
				vector[index] = value.doubleValue();
			}
		}
		return vector;
	}

	private static void require(boolean condition, String message)
	{
		if (!condition)
		{
			throw new AssertionError(message);
		}
	}

	private static final class Corpus
	{
		private final TreeMap<String,LinkedHashMap<String,Double>> documents;
		private final TreeMap<String,String> userClasses;
		private final TreeSet<String> vocabulary;
		private final TreeSet<String> classes;

		private Corpus(
				TreeMap<String,LinkedHashMap<String,Double>> documents,
				TreeMap<String,String> userClasses,
				TreeSet<String> vocabulary,
				TreeSet<String> classes)
		{
			this.documents = documents;
			this.userClasses = userClasses;
			this.vocabulary = vocabulary;
			this.classes = classes;
		}
	}
}
