package TwitterGatherDataFollowers.userRyersonU;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.BufferedReader;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;

import weka.classifiers.Evaluation;
import weka.classifiers.functions.SMO;
import weka.core.Instances;

public final class ExactSvmWorkerSelfTest
{
	private ExactSvmWorkerSelfTest()
	{
	}

	public static void main(String[] args) throws Exception
	{
		isolatedWorkerMatchesTheExactInProcessModel();
		heapRetryPolicyExpandsOnlyConfirmedHeapFailures();
		rejectsImpossibleHighClassWorkloadBeforeTraining();
		rejectsImpossibleBestCaseStructureDuringInitialization();
		System.out.println("ExactSvmWorkerSelfTest passed");
	}

	private static void heapRetryPolicyExpandsOnlyConfirmedHeapFailures()
	{
		require(ExactSvmWorkerRunner.isHeapExhaustionDiagnostic(
						"java.lang.OutOfMemoryError: Java heap space"),
				"worker retry policy missed a Java heap failure");
		require(ExactSvmWorkerRunner.isHeapExhaustionDiagnostic(
						"GC overhead limit exceeded"),
				"worker retry policy missed a GC-overhead heap failure");
		require(!ExactSvmWorkerRunner.isHeapExhaustionDiagnostic(
						"IllegalArgumentException: malformed ARFF"),
				"worker retry policy would mask a non-memory model failure");
		require(!ExactSvmWorkerRunner.isHeapExhaustionDiagnostic(
						"java.lang.OutOfMemoryError: unable to create native thread"),
				"worker retry policy would worsen a native-resource failure");
		require(ExactSvmWorkerRunner.nextRetryHeapMb(1024L, 8192L) == 2048L,
				"first worker retry did not double its heap");
		require(ExactSvmWorkerRunner.nextRetryHeapMb(6144L, 8192L) == 8192L,
				"worker retry exceeded or failed to reach its safe maximum");
		require(ExactSvmWorkerRunner.nextRetryHeapMb(8192L, 8192L) == 8192L,
				"worker retry expanded beyond its safe maximum");
	}

	private static void isolatedWorkerMatchesTheExactInProcessModel()
			throws Exception
	{
		File directory = Files.createTempDirectory("exact-svm-worker-test-").toFile();
		File train = new File(directory, "train.arff");
		File test = new File(directory, "test.arff");
		File recommend = new File(directory, "recommend.arff");
		try
		{
			write(train,
					"@relation train\n"
					+ "@attribute x numeric\n"
					+ "@attribute result {'A','B'}\n"
					+ "@data\n"
					+ "0.0,'A'\n0.1,'A'\n0.9,'B'\n1.0,'B'\n");
			write(test,
					"@relation test\n"
					+ "@attribute x numeric\n"
					+ "@attribute result {'A','B'}\n"
					+ "@data\n"
					+ "0.05,'A'\n0.95,'B'\n");
			write(recommend,
					"@relation recommend\n"
					+ "@attribute x numeric\n"
					+ "@attribute result {'A','B'}\n"
					+ "@data\n"
					+ "0.03,'A'\n0.97,'B'\n");

			ExactSvmWorkerResult inProcess =
					runLegacyInProcessSequence(train, test, recommend);
			ExactSvmWorkerResult isolated = ExactSvmWorkerRunner.run(
					"SelfTest",
					"exact equality",
					null,
					train,
					test,
					recommend,
					1,
					4,
					2);

			require(inProcess.getClassNames().equals(isolated.getClassNames()),
					"class ordering changed in the isolated worker");
			require(inProcess.getPredictedClasses().equals(
					isolated.getPredictedClasses()),
					"predicted classes changed in the isolated worker");
			for (int index = 0; index < inProcess.getDistributions().size(); index++)
			{
				require(Arrays.equals(
						inProcess.getDistributions().get(index),
						isolated.getDistributions().get(index)),
						"class distribution changed in the isolated worker");
			}
		}
		finally
		{
			delete(directory);
		}
	}

	private static ExactSvmWorkerResult runLegacyInProcessSequence(
			File train,
			File test,
			File recommend) throws Exception
	{
		Instances training = load(train);
		Instances testing = load(test);
		Instances recommendations = load(recommend);
		training.setClassIndex(training.numAttributes() - 1);
		testing.setClassIndex(testing.numAttributes() - 1);
		recommendations.setClassIndex(recommendations.numAttributes() - 1);

		SMO model = new SMO();
		model.setC(0.1);
		model.setBuildCalibrationModels(true);
		SvmReproducibility.applyModelSeed(model);
		model.buildClassifier(training);

		Evaluation testEvaluation = new Evaluation(training);
		testEvaluation.evaluateModel(model, testing);
		Evaluation recommendationEvaluation = new Evaluation(training);
		recommendationEvaluation.evaluateModel(model, recommendations);

		ArrayList<String> classes = new ArrayList<String>();
		for (int index = 0; index < recommendations.classAttribute().numValues(); index++)
		{
			classes.add(recommendations.classAttribute().value(index));
		}
		ArrayList<String> predictions = new ArrayList<String>();
		ArrayList<double[]> distributions = new ArrayList<double[]>();
		for (int index = 0; index < recommendations.numInstances(); index++)
		{
			double predictedIndex = model.classifyInstance(
					recommendations.instance(index));
			predictions.add(recommendations.classAttribute().value(
					(int)predictedIndex));
			distributions.add(model.distributionForInstance(
					recommendations.instance(index)));
		}
		return new ExactSvmWorkerResult(
				classes,
				predictions,
				distributions,
				testEvaluation.pctCorrect(),
				testEvaluation.toMatrixString());
	}

	private static Instances load(File file) throws Exception
	{
		try (BufferedReader reader = new BufferedReader(new FileReader(file)))
		{
			return new Instances(reader);
		}
	}

	private static void rejectsImpossibleHighClassWorkloadBeforeTraining()
	{
		ExactSvmWorkerRunner.Profile profile =
				ExactSvmWorkerRunner.Profile.create(
						null, null, null, 50000, 30000, 5000);
		try
		{
			profile.validate();
			throw new AssertionError(
					"high-class exact SVM workload was not rejected");
		}
		catch (IllegalArgumentException expected)
		{
			require(expected.getMessage().contains("pairwise models"),
					"preflight rejection did not explain the pairwise-model cause");
		}
	}

	private static void rejectsImpossibleBestCaseStructureDuringInitialization()
	{
		try
		{
			ExactSvmWorkerRunner.validateBestCaseStructure(38875, 5015, 1);
			throw new AssertionError(
					"impossible single-node journal workload was not rejected");
		}
		catch (IllegalArgumentException expected)
		{
			require(expected.getMessage().contains("Scalable Linear SVM"),
					"structural preflight did not offer the scalable model");
		}
		ExactSvmWorkerRunner.validateBestCaseStructure(5000, 400, 8);
	}

	private static void write(File file, String content) throws Exception
	{
		try (FileWriter writer = new FileWriter(file))
		{
			writer.write(content);
		}
	}

	private static void delete(File file) throws Exception
	{
		if (file == null || !file.exists())
		{
			return;
		}
		if (file.isDirectory())
		{
			File[] children = file.listFiles();
			if (children != null)
			{
				for (File child : children)
				{
					delete(child);
				}
			}
		}
		Files.deleteIfExists(file.toPath());
	}

	private static void require(boolean condition, String message)
	{
		if (!condition)
		{
			throw new AssertionError(message);
		}
	}
}
