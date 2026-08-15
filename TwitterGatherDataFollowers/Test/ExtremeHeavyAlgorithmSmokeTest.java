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
 * Exercises the extreme corpus through representative active algorithm
 * structures without running the intentionally hours-long complete workload.
 */
public final class ExtremeHeavyAlgorithmSmokeTest
{
	private static final int EXPECTED_USERS = 480;
	private static final int EXPECTED_CLASSES = 16;
	private static final int EXPECTED_FEATURES = 262144;
	private static final int EXPECTED_TERMS_PER_USER = 107520;

	private ExtremeHeavyAlgorithmSmokeTest()
	{
	}

	public static void main(String[] args) throws Exception
	{
		File file = args.length > 0
				? new File(args[0])
				: new File(
						"Dataset/Benchmarks/"
						+ "Extreme-15-Minute-Plus-Journal-245k.txt");
		boolean runMlpProbe = args.length > 1
				&& "--mlp-probe".equals(args[1]);
		Corpus sample = loadSample(file);
		validateSparseDistances(sample);
		validateExactSvmAdmission();
		if (runMlpProbe)
		{
			validateMlpShape(sample);
		}
		else
		{
			System.out.println(
					"Extreme MLP construction probe skipped by default. "
					+ "The measured probe exceeded 15 minutes for one row; "
					+ "pass --mlp-probe only when intentionally repeating it.");
		}
		printConservativeRuntimeProjections();
		System.out.println(
				"ExtremeHeavyAlgorithmSmokeTest passed: "
				+ sample.userCount + " users, "
				+ sample.vocabulary.size() + " features, "
				+ sample.classes.size() + " classes.");
	}

	private static Corpus loadSample(File file) throws Exception
	{
		TreeSet<String> wanted = new TreeSet<String>();
		wanted.add("ExtremeAuthorC00M00");
		wanted.add("ExtremeAuthorC00M05");
		wanted.add("ExtremeAuthorC01M00");
		wanted.add("ExtremeAuthorC08M00");
		TreeMap<String,LinkedHashMap<String,Double>> documents =
				new TreeMap<String,LinkedHashMap<String,Double>>();
		TreeSet<String> users = new TreeSet<String>();
		TreeSet<String> vocabulary = new TreeSet<String>();
		TreeSet<String> classes = new TreeSet<String>();
		long lineNumber = 0L;
		try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(
						new FileInputStream(file),
						StandardCharsets.UTF_8),
				1024 * 1024))
		{
			String line;
			while ((line = reader.readLine()) != null)
			{
				TwitterGatherDataFollowers.userRyersonU.DatasetRow row =
						TwitterGatherDataFollowers.userRyersonU.DatasetRow.parse(
								line, ++lineNumber);
				users.add(row.userName);
				classes.add(row.referenceUser);
				LinkedHashMap<String,Double> document = documents.get(row.userName);
				if (wanted.contains(row.userName) && document == null)
				{
					document = new LinkedHashMap<String,Double>();
					documents.put(row.userName, document);
				}
				for (String token : row.text.split(" "))
				{
					vocabulary.add(token);
					if (document != null)
					{
						Double old = document.get(token);
						document.put(token, old == null ? 1.0 : old + 1.0);
					}
				}
			}
		}
		require(users.size() == EXPECTED_USERS, "unexpected user count");
		require(classes.size() == EXPECTED_CLASSES, "unexpected class count");
		require(vocabulary.size() == EXPECTED_FEATURES, "unexpected feature count");
		require(documents.size() == wanted.size(), "missing sample document");
		for (Map.Entry<String,LinkedHashMap<String,Double>> entry
				: documents.entrySet())
		{
			require(entry.getValue().size() == EXPECTED_TERMS_PER_USER,
					"unexpected sample size for " + entry.getKey());
		}
		return new Corpus(documents, vocabulary, classes, users.size());
	}

	private static void validateSparseDistances(Corpus corpus)
	{
		Point target = point("ExtremeAuthorC00M00", corpus);
		Point sameJournal = point("ExtremeAuthorC00M05", corpus);
		Point sameGroup = point("ExtremeAuthorC01M00", corpus);
		Point unrelated = point("ExtremeAuthorC08M00", corpus);
		double sameJournalCosine = target.cosSimDistance(sameJournal);
		double sameGroupCosine = target.cosSimDistance(sameGroup);
		double unrelatedCosine = target.cosSimDistance(unrelated);
		require(Double.isFinite(sameJournalCosine)
						&& Double.isFinite(sameGroupCosine)
						&& Double.isFinite(unrelatedCosine),
				"cosine distance produced a non-finite score");
		require(sameJournalCosine > sameGroupCosine
						&& sameGroupCosine > unrelatedCosine,
				"cosine semantic hierarchy changed");

		double sameJournalEuclidean = target.eucliDistance(sameJournal);
		double sameGroupEuclidean = target.eucliDistance(sameGroup);
		double unrelatedEuclidean = target.eucliDistance(unrelated);
		require(Double.isFinite(sameJournalEuclidean)
						&& Double.isFinite(sameGroupEuclidean)
						&& Double.isFinite(unrelatedEuclidean),
				"Euclidean distance produced a non-finite score");
		require(sameJournalEuclidean < sameGroupEuclidean
						&& sameGroupEuclidean < unrelatedEuclidean,
				"Euclidean semantic hierarchy changed");
	}

	private static void validateExactSvmAdmission()
	{
		ExactSvmWorkerRunner.validateBestCaseStructure(
				EXPECTED_USERS, EXPECTED_CLASSES, 1);
		ExactSvmWorkerRunner.validateBestCaseStructure(
				EXPECTED_USERS, EXPECTED_CLASSES, 8);

		long retainedEntries =
				(long)(EXPECTED_USERS + 7) * EXPECTED_TERMS_PER_USER;
		long largestNodeEntries = 61L * EXPECTED_TERMS_PER_USER;
		ExactSvmCorpusAdmission.WavePlan fourGigabytePlan =
				ExactSvmCorpusAdmission.wavePlan(
						retainedEntries,
						largestNodeEntries,
						EXPECTED_USERS,
						8,
						4L * 1024L * RecommendationMemoryCoordinator.MEBIBYTE);
		fourGigabytePlan.validate();
		require(fourGigabytePlan.requiresDiskBackedWaves(),
				"extreme corpus must use disk-backed Exact-SVM waves at 4 GB");
	}

	private static void validateMlpShape(Corpus corpus)
	{
		List<String> features = new ArrayList<String>(corpus.vocabulary);
		List<String> classes = new ArrayList<String>(corpus.classes);
		Map<String,Double> sampleValues =
				corpus.documents.get("ExtremeAuthorC00M00");
		double[] input = new double[features.size()];
		for (int index = 0; index < features.size(); index++)
		{
			Double value = sampleValues.get(features.get(index));
			if (value != null)
			{
				input[index] = value.doubleValue();
			}
		}
		double[] desired = new double[classes.size()];
		desired[0] = 1.0;
		DataSet data = new DataSet(features.size(), classes.size());
		data.addRow(new DataSetRow(input, desired));

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
		network.setInput(input);
		network.calculate();
		double[] output = network.getOutput();
		require(output.length == classes.size(), "MLP output dimension changed");
		for (double value : output)
		{
			require(Double.isFinite(value), "MLP produced a non-finite output");
		}
	}

	private static void printConservativeRuntimeProjections()
	{
		long universalTokens = 76800L * 32L;
		long extremeTokens = 245760L * 256L;
		double doc2VecMinutes = 282656.0
				* extremeTokens / universalTokens / 60000.0;
		double exactSvmBatchMinutes = 4317.0
				* EXPECTED_USERS / 60000.0;
		System.out.println(
				"Conservative projection from measured universal benchmark: "
				+ "cold Doc2Vec about "
				+ String.format("%.1f", doc2VecMinutes)
				+ " minutes; 480-target exact SVM batch at least "
				+ String.format("%.1f", exactSvmBatchMinutes)
				+ " minutes at the measured extreme-corpus one-target rate.");
	}

	private static Point point(String user, Corpus corpus)
	{
		LinkedHashMap<String,Double> values = corpus.documents.get(user);
		require(values != null, "missing sample document for " + user);
		return new Point(user, values);
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
		private final TreeSet<String> vocabulary;
		private final TreeSet<String> classes;
		private final int userCount;

		private Corpus(
				TreeMap<String,LinkedHashMap<String,Double>> documents,
				TreeSet<String> vocabulary,
				TreeSet<String> classes,
				int userCount)
		{
			this.documents = documents;
			this.vocabulary = vocabulary;
			this.classes = classes;
			this.userCount = userCount;
		}
	}
}
