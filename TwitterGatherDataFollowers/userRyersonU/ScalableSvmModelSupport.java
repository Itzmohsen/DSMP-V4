package TwitterGatherDataFollowers.userRyersonU;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import weka.core.Instance;
import weka.core.Instances;
import weka.core.converters.ArffLoader.ArffReader;

/**
 * Sparse, deterministic, high-cardinality linear SVM.
 *
 * The legacy exact SVM remains in {@link ExactSvmModelSupport}. This separate
 * model uses a multiclass passive-aggressive max-margin update and streams ARFF
 * rows without Weka's multiclass filters or dense normalization.
 */
final class ScalableSvmModelSupport
{
	private static final int DEFAULT_EPOCHS = 10;
	private static final double DEFAULT_AGGRESSIVENESS = 0.1;
	private static final long MAX_FLAT_WEIGHT_VALUES = Integer.MAX_VALUE - 8L;
	/** Fixed seed for the presentation shuffle, so training stays reproducible. */
	private static final long SHUFFLE_SEED = 1L;
	private static final int DEFAULT_SHUFFLE_BUFFER = 8192;

	/**
	 * Rows held in memory while shuffling. Large enough to break up class-sorted
	 * runs, small enough to keep a million-row corpus streaming.
	 */
	private static int shuffleBufferCapacity()
	{
		String value = System.getProperty("recommender.scalableSvm.shuffleBuffer");
		if (value == null)
		{
			return DEFAULT_SHUFFLE_BUFFER;
		}
		try
		{
			return Math.max(2, Integer.parseInt(value.trim()));
		}
		catch (NumberFormatException ignored)
		{
			return DEFAULT_SHUFFLE_BUFFER;
		}
	}

	private ScalableSvmModelSupport()
	{
	}

	static ExactSvmWorkerResult trainAndScore(
			File trainingFile,
			File testFile,
			File recommendationFile) throws Exception
	{
		Structure trainingStructure = readStructure(trainingFile);
		int[] trainingClassCounts = countTrainingClasses(
				trainingFile, trainingStructure);
		int trainingInstances = sum(trainingClassCounts);
		if (trainingInstances < 2)
		{
			throw new IllegalArgumentException(
					"Scalable SVM needs at least two training instances.");
		}

		SparseMulticlassModel model = new SparseMulticlassModel(
				trainingStructure, trainingClassCounts);
		model.train(trainingFile, configuredEpochs());

		EvaluationSummary evaluation = evaluate(
				model, testFile, trainingStructure);
		ScoringSummary recommendations = scoreRecommendations(
				model, recommendationFile, trainingStructure);
		if (recommendations.predictedClasses.isEmpty())
		{
			throw new IllegalArgumentException(
					"Scalable SVM received no recommendation target instances.");
		}

		return new ExactSvmWorkerResult(
				trainingStructure.classNames,
				recommendations.predictedClasses,
				recommendations.distributions,
				evaluation.accuracyPercent,
				evaluation.diagnostic);
	}

	private static int configuredEpochs()
	{
		String value = System.getProperty("recommender.scalableSvm.epochs");
		if (value == null)
		{
			return DEFAULT_EPOCHS;
		}
		try
		{
			return Math.max(1, Integer.parseInt(value.trim()));
		}
		catch (NumberFormatException ignored)
		{
			return DEFAULT_EPOCHS;
		}
	}

	private static double configuredAggressiveness()
	{
		String value = System.getProperty(
				"recommender.scalableSvm.aggressiveness");
		if (value == null)
		{
			return DEFAULT_AGGRESSIVENESS;
		}
		try
		{
			double parsed = Double.parseDouble(value.trim());
			return Double.isFinite(parsed) && parsed > 0.0
					? parsed : DEFAULT_AGGRESSIVENESS;
		}
		catch (NumberFormatException ignored)
		{
			return DEFAULT_AGGRESSIVENESS;
		}
	}

	private static Structure readStructure(File file) throws Exception
	{
		try (BufferedReader reader = new BufferedReader(new FileReader(file)))
		{
			ArffReader arff = new ArffReader(reader, 1, false);
			Instances structure = arff.getStructure();
			structure.setClassIndex(structure.numAttributes() - 1);
			if (!structure.classAttribute().isNominal())
			{
				throw new IllegalArgumentException(
						"Scalable SVM requires a nominal class attribute.");
			}
			ArrayList<String> classNames = new ArrayList<String>();
			for (int index = 0;
					index < structure.classAttribute().numValues(); index++)
			{
				classNames.add(structure.classAttribute().value(index));
			}
			return new Structure(
					structure,
					structure.classIndex(),
					structure.numAttributes() - 1,
					classNames);
		}
	}

	private static int[] countTrainingClasses(
			File file,
			Structure expected) throws Exception
	{
		final int[] counts = new int[expected.classNames.size()];
		forEachInstance(file, expected, new InstanceConsumer()
		{
			public void accept(Instance instance)
			{
				if (!instance.classIsMissing())
				{
					counts[(int)instance.classValue()]++;
				}
			}
		});
		return counts;
	}

	private static EvaluationSummary evaluate(
			final SparseMulticlassModel model,
			File file,
			Structure expected) throws Exception
	{
		final long[] counts = new long[2];
		final double[] scores = new double[model.activeClassCount];
		forEachInstance(file, expected, new InstanceConsumer()
		{
			public void accept(Instance instance)
			{
				if (instance.classIsMissing())
				{
					return;
				}
				counts[1]++;
				if (model.predictGlobalClass(instance, scores)
						== (int)instance.classValue())
				{
					counts[0]++;
				}
			}
		});
		double accuracy = counts[1] == 0L
				? Double.NaN : counts[0] * 100.0 / counts[1];
		return new EvaluationSummary(
				accuracy,
				"Sparse scalable-SVM diagnostics: "
				+ model.activeClassCount + " trained classes, "
				+ model.featureCount + " features, "
				+ configuredEpochs() + " deterministic epochs.");
	}

	private static ScoringSummary scoreRecommendations(
			final SparseMulticlassModel model,
			File file,
			Structure expected) throws Exception
	{
		final ArrayList<String> predictions = new ArrayList<String>();
		final ArrayList<double[]> distributions = new ArrayList<double[]>();
		forEachInstance(file, expected, new InstanceConsumer()
		{
			public void accept(Instance instance)
			{
				double[] distribution = model.distribution(instance);
				int best = bestIndex(distribution);
				predictions.add(model.structure.classNames.get(best));
				distributions.add(distribution);
			}
		});
		return new ScoringSummary(predictions, distributions);
	}

	private static void forEachInstance(
			File file,
			Structure expected,
			InstanceConsumer consumer) throws Exception
	{
		try (BufferedReader reader = new BufferedReader(new FileReader(file)))
		{
			ArffReader arff = new ArffReader(reader, 1, false);
			Instances structure = arff.getStructure();
			structure.setClassIndex(structure.numAttributes() - 1);
			validateCompatible(expected, structure, file);
			Instance instance;
			while ((instance = arff.readInstance(structure)) != null)
			{
				consumer.accept(instance);
			}
		}
	}

	private static void validateCompatible(
			Structure expected,
			Instances actual,
			File file)
	{
		if (actual.numAttributes() != expected.header.numAttributes()
				|| actual.classAttribute().numValues()
						!= expected.classNames.size())
		{
			throw new IllegalArgumentException(
					"Incompatible scalable-SVM ARFF header: "
					+ file.getAbsolutePath());
		}
		for (int index = 0; index < expected.classNames.size(); index++)
		{
			if (!expected.classNames.get(index).equals(
					actual.classAttribute().value(index)))
			{
				throw new IllegalArgumentException(
						"Incompatible scalable-SVM class order: "
						+ file.getAbsolutePath());
			}
		}
	}

	private static int bestIndex(double[] values)
	{
		int best = 0;
		for (int index = 1; index < values.length; index++)
		{
			if (values[index] > values[best])
			{
				best = index;
			}
		}
		return best;
	}

	private static int sum(int[] values)
	{
		int total = 0;
		for (int value : values)
		{
			total += value;
		}
		return total;
	}

	private interface InstanceConsumer
	{
		void accept(Instance instance) throws Exception;
	}

	private static final class SparseMulticlassModel
	{
		private final Structure structure;
		private final int featureCount;
		private final int activeClassCount;
		private final int[] globalToActive;
		private final int[] activeToGlobal;
		private final float[] weights;
		private final float[] biases;
		private final double aggressiveness;

		private SparseMulticlassModel(
				Structure structure,
				int[] classCounts)
		{
			this.structure = structure;
			featureCount = structure.featureCount;
			globalToActive = new int[classCounts.length];
			Arrays.fill(globalToActive, -1);
			int active = 0;
			for (int count : classCounts)
			{
				if (count > 0)
				{
					active++;
				}
			}
			if (active < 2)
			{
				throw new IllegalArgumentException(
						"Scalable SVM needs at least two populated classes.");
			}
			activeClassCount = active;
			activeToGlobal = new int[activeClassCount];
			int next = 0;
			for (int global = 0; global < classCounts.length; global++)
			{
				if (classCounts[global] > 0)
				{
					globalToActive[global] = next;
					activeToGlobal[next] = global;
					next++;
				}
			}

			long weightValues = (long)featureCount * activeClassCount;
			if (weightValues > MAX_FLAT_WEIGHT_VALUES)
			{
				throw new IllegalArgumentException(
						"Scalable SVM requires too many feature/class weights: "
						+ String.format(java.util.Locale.US, "%,d", weightValues)
						+ ". Use more recommender nodes.");
			}
			weights = new float[(int)weightValues];
			biases = new float[activeClassCount];
			aggressiveness = configuredAggressiveness();
		}

		/**
		 * One passive-aggressive pass per epoch, over a shuffled presentation.
		 *
		 * Online max-margin learners assume examples arrive independently. The
		 * training ARFF is written class by class, so presenting it in file order
		 * lets the last class overwrite the earlier ones: on a three-class retail
		 * split that drove accuracy to 11% while a shuffled presentation of the
		 * same rows reached 71%.
		 *
		 * The rows are not all loaded at once, because this model exists to stream
		 * corpora too large to materialise. Instead a bounded buffer is filled,
		 * shuffled and consumed, which breaks up class-sorted runs while keeping
		 * memory flat. The shuffle is seeded per epoch, so runs stay reproducible.
		 */
		private void train(File trainingFile, int epochs) throws Exception
		{
			final int capacity = shuffleBufferCapacity();
			for (int epoch = 0; epoch < epochs; epoch++)
			{
				final double[] scores = new double[activeClassCount];
				final java.util.ArrayList<Instance> buffer =
						new java.util.ArrayList<Instance>(capacity);
				final java.util.Random random =
						new java.util.Random(SHUFFLE_SEED + epoch * 0x9E3779B9L);
				forEachInstance(trainingFile, structure, new InstanceConsumer()
				{
					public void accept(Instance instance)
					{
						if (instance.classIsMissing())
						{
							return;
						}
						buffer.add(instance);
						if (buffer.size() >= capacity)
						{
							drainShuffled(buffer, random, scores);
						}
					}
				});
				drainShuffled(buffer, random, scores);
			}
		}

		private void drainShuffled(
				java.util.List<Instance> buffer,
				java.util.Random random,
				double[] scores)
		{
			java.util.Collections.shuffle(buffer, random);
			for (Instance instance : buffer)
			{
				update(instance, scores);
			}
			buffer.clear();
		}

		private void update(Instance instance, double[] scores)
		{
			int trueClass = globalToActive[(int)instance.classValue()];
			if (trueClass < 0)
			{
				return;
			}
			activeScores(instance, scores);
			int rival = -1;
			for (int active = 0; active < activeClassCount; active++)
			{
				if (active != trueClass
						&& (rival < 0 || scores[active] > scores[rival]))
				{
					rival = active;
				}
			}
			if (rival < 0)
			{
				return;
			}

			double loss = 1.0 - scores[trueClass] + scores[rival];
			if (loss <= 0.0)
			{
				return;
			}
			double normSquared = sparseNormSquared(instance) + 1.0;
			double step = Math.min(
					aggressiveness,
					loss / Math.max(2.0 * normSquared, 1.0e-12));
			for (int sparse = 0; sparse < instance.numValues(); sparse++)
			{
				int feature = instance.index(sparse);
				if (feature == structure.classIndex
						|| feature < 0 || feature >= featureCount)
				{
					continue;
				}
				double value = instance.valueSparse(sparse);
				if (!Double.isFinite(value) || value == 0.0)
				{
					continue;
				}
				int offset = feature * activeClassCount;
				float adjustment = (float)(step * value);
				weights[offset + trueClass] += adjustment;
				weights[offset + rival] -= adjustment;
			}
			biases[trueClass] += (float)step;
			biases[rival] -= (float)step;
		}

		private int predictGlobalClass(Instance instance, double[] scores)
		{
			activeScores(instance, scores);
			return activeToGlobal[bestIndex(scores)];
		}

		private double[] distribution(Instance instance)
		{
			double[] activeScores = new double[activeClassCount];
			activeScores(instance, activeScores);
			double max = activeScores[bestIndex(activeScores)];
			double total = 0.0;
			for (int active = 0; active < activeScores.length; active++)
			{
				double value = Math.exp(Math.max(-60.0, activeScores[active] - max));
				activeScores[active] = value;
				total += value;
			}
			double[] distribution = new double[structure.classNames.size()];
			if (total <= 0.0 || !Double.isFinite(total))
			{
				distribution[activeToGlobal[0]] = 1.0;
				return distribution;
			}
			for (int active = 0; active < activeClassCount; active++)
			{
				distribution[activeToGlobal[active]] = activeScores[active] / total;
			}
			return distribution;
		}

		private void activeScores(Instance instance, double[] scores)
		{
			for (int active = 0; active < activeClassCount; active++)
			{
				scores[active] = biases[active];
			}
			for (int sparse = 0; sparse < instance.numValues(); sparse++)
			{
				int feature = instance.index(sparse);
				if (feature == structure.classIndex
						|| feature < 0 || feature >= featureCount)
				{
					continue;
				}
				double value = instance.valueSparse(sparse);
				if (!Double.isFinite(value) || value == 0.0)
				{
					continue;
				}
				int offset = feature * activeClassCount;
				for (int active = 0; active < activeClassCount; active++)
				{
					scores[active] += weights[offset + active] * value;
				}
			}
		}

		private double sparseNormSquared(Instance instance)
		{
			double total = 0.0;
			for (int sparse = 0; sparse < instance.numValues(); sparse++)
			{
				int feature = instance.index(sparse);
				if (feature == structure.classIndex
						|| feature < 0 || feature >= featureCount)
				{
					continue;
				}
				double value = instance.valueSparse(sparse);
				if (Double.isFinite(value))
				{
					total += value * value;
				}
			}
			return total;
		}
	}

	private static final class Structure
	{
		private final Instances header;
		private final int classIndex;
		private final int featureCount;
		private final ArrayList<String> classNames;

		private Structure(
				Instances header,
				int classIndex,
				int featureCount,
				List<String> classNames)
		{
			this.header = new Instances(header, 0);
			this.header.setClassIndex(classIndex);
			this.classIndex = classIndex;
			this.featureCount = featureCount;
			this.classNames = new ArrayList<String>(classNames);
		}
	}

	private static final class EvaluationSummary
	{
		private final double accuracyPercent;
		private final String diagnostic;

		private EvaluationSummary(double accuracyPercent, String diagnostic)
		{
			this.accuracyPercent = accuracyPercent;
			this.diagnostic = diagnostic;
		}
	}

	private static final class ScoringSummary
	{
		private final ArrayList<String> predictedClasses;
		private final ArrayList<double[]> distributions;

		private ScoringSummary(
				ArrayList<String> predictedClasses,
				ArrayList<double[]> distributions)
		{
			this.predictedClasses = predictedClasses;
			this.distributions = distributions;
		}
	}
}
