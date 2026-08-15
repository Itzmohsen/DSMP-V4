package TwitterGatherDataFollowers.userRyersonU;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

import weka.classifiers.Classifier;
import weka.classifiers.Evaluation;
import weka.classifiers.functions.SMO;
import weka.core.Instances;

/**
 * The single definition of the legacy exact SMO model.
 *
 * Keeping this configuration shared by the simulator and worker process makes
 * process isolation a resource-management change, not a recommendation change.
 */
final class ExactSvmModelSupport
{
	private ExactSvmModelSupport()
	{
	}

	static void configure(SMO model)
	{
		configure(model, AlgorithmParameterSettings.fromSystemProperties());
	}

	static void configure(SMO model, AlgorithmParameterSettings settings)
	{
		AlgorithmParameterSettings effective =
				AlgorithmParameterSettings.effective(settings);
		model.setC(effective.getSvmC());
		model.setBuildCalibrationModels(effective.isSvmCalibrationEnabled());
		SvmReproducibility.applyModelSeed(model);
	}

	static ExactSvmWorkerResult trainAndScore(
			File trainingFile,
			File testFile,
			File recommendationFile) throws Exception
	{
		return trainAndScore(
				trainingFile,
				testFile,
				recommendationFile,
				AlgorithmParameterSettings.fromSystemProperties());
	}

	static ExactSvmWorkerResult trainAndScore(
			File trainingFile,
			File testFile,
			File recommendationFile,
			AlgorithmParameterSettings settings) throws Exception
	{
		Instances training = load(trainingFile);
		training.setClassIndex(training.numAttributes() - 1);
		if (training.numInstances() < 2)
		{
			throw new IllegalArgumentException(
					"Exact SVM needs at least two training instances.");
		}
		training = TrainingSetBalancer.balanceExactSvmTraining(training);
		training.setClassIndex(training.numAttributes() - 1);
		if (training.numInstances() < 2)
		{
			throw new IllegalArgumentException(
					"Exact SVM balance filtering left fewer than two training instances.");
		}

		SMO model = new SMO();
		configure(model, settings);
		model.buildClassifier(training);

		Instances test = load(testFile);
		test.setClassIndex(test.numAttributes() - 1);
		EvaluationSummary evaluation = evaluate(model, training, test);

		Instances recommendations = load(recommendationFile);
		recommendations.setClassIndex(recommendations.numAttributes() - 1);
		if (recommendations.numInstances() == 0)
		{
			throw new IllegalArgumentException(
					"Exact SVM received no recommendation target instances.");
		}

		ArrayList<String> classNames = new ArrayList<String>();
		for (int index = 0; index < recommendations.classAttribute().numValues(); index++)
		{
			classNames.add(recommendations.classAttribute().value(index));
		}

		ArrayList<String> predictedClasses = new ArrayList<String>();
		ArrayList<double[]> distributions = new ArrayList<double[]>();
		for (int index = 0; index < recommendations.numInstances(); index++)
		{
			double predictedIndex = model.classifyInstance(
					recommendations.instance(index));
			predictedClasses.add(recommendations.classAttribute().value(
					(int)predictedIndex));
			distributions.add(model.distributionForInstance(
					recommendations.instance(index)));
		}

		return new ExactSvmWorkerResult(
				classNames,
				predictedClasses,
				distributions,
				evaluation.accuracyPercent,
				evaluation.confusionMatrix);
	}

	static EvaluationSummary evaluate(
			Classifier model,
			Instances training,
			Instances test) throws Exception
	{
		if (test.numInstances() == 0)
		{
			return new EvaluationSummary(Double.NaN, "");
		}
		if (training.numClasses() <= 200)
		{
			Evaluation evaluation = new Evaluation(training);
			evaluation.evaluateModel(model, test);
			return new EvaluationSummary(
					evaluation.pctCorrect(),
					evaluation.toMatrixString());
		}

		int correct = 0;
		int counted = 0;
		for (int index = 0; index < test.numInstances(); index++)
		{
			if (test.instance(index).classIsMissing())
			{
				continue;
			}
			double predicted = model.classifyInstance(test.instance(index));
			if ((int)predicted == (int)test.instance(index).classValue())
			{
				correct++;
			}
			counted++;
		}
		return new EvaluationSummary(
				counted == 0 ? Double.NaN : correct * 100.0 / counted,
				"Confusion matrix omitted for " + training.numClasses()
						+ " classes to avoid a quadratic diagnostic allocation.");
	}

	static final class EvaluationSummary
	{
		final double accuracyPercent;
		final String confusionMatrix;

		private EvaluationSummary(double accuracyPercent, String confusionMatrix)
		{
			this.accuracyPercent = accuracyPercent;
			this.confusionMatrix = confusionMatrix;
		}
	}

	private static Instances load(File file) throws Exception
	{
		try (BufferedReader reader = new BufferedReader(new FileReader(file)))
		{
			return new Instances(reader);
		}
	}
}
