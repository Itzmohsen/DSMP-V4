package TwitterGatherDataFollowers.userRyersonU;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Arrays;
import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * The softmax MLP: tanh hidden layers, a softmax output layer and the
 * cross-entropy loss, trained by seeded stochastic gradient descent with L2
 * regularization and early stopping on a validation split. Inputs stay sparse,
 * so large TF-IDF corpora train without dense vectors. This is the default MLP
 * engine; the Neuroph engine is kept for comparison with V3.
 *
 * The same network also trains by federated averaging ({@link FederatedMlp}):
 * {@link SparseMlpModel#localUpdate} is one client's local training and
 * {@link SparseMlpModel#plusAverage} the server's aggregation. That kernel
 * reads documents as {@link SparseRow}s and never modifies the model it is
 * called on.
 */
final class SparseFederatedMlpModelSupport
{
	private SparseFederatedMlpModelSupport()
	{
	}

	static final class TrainingExample implements Serializable
	{
		private static final long serialVersionUID = 1L;

		private final String user;
		private final Map<Integer,Double> features;
		private final int labelIndex;

		TrainingExample(String user, Map<Integer,Double> features, int labelIndex)
		{
			this.user = user;
			this.features = new LinkedHashMap<Integer,Double>(features);
			this.labelIndex = labelIndex;
		}

		String getUser()
		{
			return user;
		}

		Map<Integer,Double> getFeatures()
		{
			return features;
		}

		int getLabelIndex()
		{
			return labelIndex;
		}
	}

	/**
	 * One user's document for the federated kernel: feature indexes in
	 * ascending order with their values. Sums run in this order, so it is part
	 * of the result and is fixed here instead of being inherited from a map.
	 */
	static final class SparseRow implements Serializable
	{
		private static final long serialVersionUID = 1L;

		private final String user;
		private final int[] indexes;
		private final double[] values;
		private final int labelIndex;

		SparseRow(String user, int[] indexes, double[] values, int labelIndex)
		{
			if (indexes == null || values == null || indexes.length != values.length)
			{
				throw new IllegalArgumentException("A sparse row needs one value per index.");
			}
			for (int position = 0; position < indexes.length; position++)
			{
				if (indexes[position] < 0
						|| (position > 0 && indexes[position] <= indexes[position - 1]))
				{
					throw new IllegalArgumentException(
							"Sparse row indexes must be non-negative, distinct and ascending.");
				}
			}
			this.user = user;
			this.indexes = indexes.clone();
			this.values = values.clone();
			this.labelIndex = labelIndex;
		}

		/**
		 * The row of a feature map. Zero and non-finite values are left out, as
		 * the central engine's examples leave them out, and the rest is sorted
		 * by index.
		 */
		static SparseRow of(String user, Map<Integer,Double> features, int labelIndex)
		{
			java.util.TreeMap<Integer,Double> sorted = new java.util.TreeMap<Integer,Double>();
			if (features != null)
			{
				for (Map.Entry<Integer,Double> entry : features.entrySet())
				{
					Integer index = entry.getKey();
					Double value = entry.getValue();
					if (index != null && index.intValue() >= 0 && value != null
							&& Double.isFinite(value.doubleValue()) && value.doubleValue() != 0.0)
					{
						sorted.put(index, value);
					}
				}
			}
			int[] indexes = new int[sorted.size()];
			double[] values = new double[sorted.size()];
			int position = 0;
			for (Map.Entry<Integer,Double> entry : sorted.entrySet())
			{
				indexes[position] = entry.getKey().intValue();
				values[position] = entry.getValue().doubleValue();
				position++;
			}
			return new SparseRow(user, indexes, values, labelIndex);
		}

		String getUser()
		{
			return user;
		}

		int getLabelIndex()
		{
			return labelIndex;
		}

		/** Number of features with a value. */
		int size()
		{
			return indexes.length;
		}

		int indexAt(int position)
		{
			return indexes[position];
		}

		double valueAt(int position)
		{
			return values[position];
		}
	}

	static final class SparseMlpModel implements Serializable
	{
		private static final long serialVersionUID = 1L;

		private final int inputSize;
		private final int hiddenLayerCount;
		private final int hiddenSize;
		private final int outputSize;
		private final double[][][] weights;
		private final double[][] biases;
		private int sampleCount;
		/** Seed for the per-epoch example shuffle; 0 on models built by averaging. */
		private long trainingSeed;

		private SparseMlpModel(
				int inputSize,
				int hiddenLayerCount,
				int hiddenSize,
				int outputSize,
				double[][][] weights,
				double[][] biases)
		{
			this.inputSize = inputSize;
			this.hiddenLayerCount = hiddenLayerCount;
			this.hiddenSize = hiddenSize;
			this.outputSize = outputSize;
			this.weights = weights;
			this.biases = biases;
		}

		static SparseMlpModel create(
				int inputSize,
				int hiddenLayerCount,
				int hiddenSize,
				int outputSize,
				long seed)
		{
			int safeInputSize = Math.max(1, inputSize);
			int safeHiddenLayerCount = Math.max(1, hiddenLayerCount);
			int safeHiddenSize = Math.max(1, hiddenSize);
			int safeOutputSize = Math.max(1, outputSize);
			int layerCount = safeHiddenLayerCount + 1;
			double[][][] weights = new double[layerCount][][];
			double[][] biases = new double[layerCount][];
			Random random = new Random(seed);
			int previousSize = safeInputSize;
			for (int layer = 0; layer < layerCount; layer++)
			{
				int currentSize = layer == layerCount - 1
						? safeOutputSize : safeHiddenSize;
				weights[layer] = new double[previousSize][currentSize];
				biases[layer] = new double[currentSize];
				double limit = Math.sqrt(6.0 / (previousSize + currentSize));
				for (int source = 0; source < previousSize; source++)
				{
					for (int target = 0; target < currentSize; target++)
					{
						weights[layer][source][target] =
								(random.nextDouble() * 2.0 - 1.0) * limit;
					}
				}
				previousSize = currentSize;
			}
			SparseMlpModel model = new SparseMlpModel(
					safeInputSize,
					safeHiddenLayerCount,
					safeHiddenSize,
					safeOutputSize,
					weights,
					biases);
			model.trainingSeed = seed;
			return model;
		}

		void train(
				List<TrainingExample> examples,
				int epochs,
				double learningRate,
				double l2,
				double fedProxMu)
		{
			if (examples == null || examples.isEmpty())
			{
				throw new IllegalArgumentException(
						"Sparse MLP training requires at least one example.");
			}
			int safeEpochs = Math.max(1, epochs);
			double safeLearningRate = Math.max(0.0000001, learningRate);
			double safeL2 = Math.max(0.0, l2);
			double safeFedProxMu = Math.max(0.0, fedProxMu);
			double[][][] referenceWeights = deepCopy(weights);
			double[][] referenceBiases = copyBiases(biases);

			//Stochastic gradient descent assumes examples arrive in a random order.
			//The training set is built class by class, so without this the last
			//class seen dominates the final weights and the first class is never
			//predicted. The shuffle is seeded, so runs stay reproducible.
			int[] order = new int[examples.size()];
			for (int index = 0; index < order.length; index++)
			{
				order[index] = index;
			}
			for (int epoch = 0; epoch < safeEpochs; epoch++)
			{
				//A revoked training unit (drain, crash) stops between epochs.
				UnitCancellation.check();
				shuffleOrder(order, new Random(trainingSeed + epoch * 0x9E3779B9L));
				for (int index : order)
				{
					trainOne(examples.get(index), safeLearningRate, safeL2,
							safeFedProxMu, referenceWeights, referenceBiases);
				}
			}
			sampleCount = examples.size();
		}

		/** What one early-stopped training did, for the run's report. */
		static final class TrainingReport
		{
			final int epochsRun;
			final int bestEpoch;
			final double bestValidationLoss;
			final boolean stoppedEarly;

			TrainingReport(int epochsRun, int bestEpoch, double bestValidationLoss, boolean stoppedEarly)
			{
				this.epochsRun = epochsRun;
				this.bestEpoch = bestEpoch;
				this.bestValidationLoss = bestValidationLoss;
				this.stoppedEarly = stoppedEarly;
			}

			String describe()
			{
				if (bestEpoch <= 0)
				{
					return epochsRun + " epoch(s) without a validation split";
				}
				return epochsRun + " epoch(s); best validation cross-entropy "
						+ String.format(java.util.Locale.US, "%.4f", bestValidationLoss)
						+ " at epoch " + bestEpoch
						+ (stoppedEarly ? " (stopped early)" : " (epoch limit reached)");
			}
		}

		/**
		 * Stochastic gradient descent on the softmax cross-entropy with early
		 * stopping: after every epoch (one seeded, shuffled pass over the
		 * training examples) the mean cross-entropy of the validation examples
		 * is measured, the weights of the best epoch are kept, and training
		 * stops after {@code patience} epochs without improvement or at
		 * {@code maxEpochs}. Without validation examples it trains
		 * {@code maxEpochs} epochs. Deterministic for a given seed.
		 */
		TrainingReport trainWithEarlyStopping(
				List<TrainingExample> training,
				List<TrainingExample> validation,
				int maxEpochs,
				int patience,
				double learningRate,
				double l2)
		{
			if (training == null || training.isEmpty())
			{
				throw new IllegalArgumentException(
						"MLP training requires at least one example.");
			}
			int safeEpochs = Math.max(1, maxEpochs);
			int safePatience = Math.max(1, patience);
			double safeLearningRate = Math.max(0.0000001, learningRate);
			double safeL2 = Math.max(0.0, l2);
			double[][][] referenceWeights = deepCopy(weights);
			double[][] referenceBiases = copyBiases(biases);
			boolean validating = validation != null && !validation.isEmpty();
			int[] order = new int[training.size()];
			for (int index = 0; index < order.length; index++)
			{
				order[index] = index;
			}
			double bestLoss = Double.POSITIVE_INFINITY;
			int bestEpoch = 0;
			double[][][] bestWeights = null;
			double[][] bestBiases = null;
			int sinceBest = 0;
			int epochsRun = 0;
			boolean stoppedEarly = false;
			for (int epoch = 0; epoch < safeEpochs; epoch++)
			{
				//A revoked training unit (drain, crash) stops between epochs.
				UnitCancellation.check();
				shuffleOrder(order, new Random(trainingSeed + epoch * 0x9E3779B9L));
				for (int index : order)
				{
					trainOne(training.get(index), safeLearningRate, safeL2, 0.0,
							referenceWeights, referenceBiases);
				}
				epochsRun = epoch + 1;
				if (!validating)
				{
					continue;
				}
				double loss = meanCrossEntropy(validation);
				if (loss < bestLoss - 1e-12)
				{
					bestLoss = loss;
					bestEpoch = epochsRun;
					bestWeights = deepCopy(weights);
					bestBiases = copyBiases(biases);
					sinceBest = 0;
				}
				else if (++sinceBest >= safePatience)
				{
					stoppedEarly = true;
					break;
				}
			}
			if (bestWeights != null)
			{
				copyInto(bestWeights, weights);
				copyInto(bestBiases, biases);
			}
			sampleCount = training.size();
			return new TrainingReport(epochsRun, bestEpoch,
					validating ? bestLoss : Double.NaN, stoppedEarly);
		}

		/** Mean softmax cross-entropy of the examples' true classes. */
		double meanCrossEntropy(List<TrainingExample> examples)
		{
			double total = 0.0;
			int counted = 0;
			for (TrainingExample example : examples)
			{
				int label = example.getLabelIndex();
				if (label < 0 || label >= outputSize)
				{
					continue;
				}
				double probability = predict(example.getFeatures())[label];
				total -= Math.log(Math.max(probability, 1e-15));
				counted++;
			}
			return counted == 0 ? Double.NaN : total / counted;
		}

		private static void copyInto(double[][][] source, double[][][] destination)
		{
			for (int layer = 0; layer < source.length; layer++)
			{
				copyInto(source[layer], destination[layer]);
			}
		}

		private static void copyInto(double[][] source, double[][] destination)
		{
			for (int row = 0; row < source.length; row++)
			{
				System.arraycopy(source[row], 0, destination[row], 0, source[row].length);
			}
		}

		/** Fisher-Yates over an index array, so the caller's list is untouched. */
		private static void shuffleOrder(int[] order, Random random)
		{
			for (int i = order.length - 1; i > 0; i--)
			{
				int j = random.nextInt(i + 1);
				int swap = order[i];
				order[i] = order[j];
				order[j] = swap;
			}
		}

		double[] predict(Map<Integer,Double> sparseFeatures)
		{
			double[][] activations = forward(sparseFeatures);
			return activations[activations.length - 1];
		}

		int getSampleCount()
		{
			return sampleCount;
		}

		void save(File file) throws IOException
		{
			File parent = file.getParentFile();
			if (parent != null && !parent.exists())
			{
				parent.mkdirs();
			}
			ObjectOutputStream output = new ObjectOutputStream(
					new BufferedOutputStream(new FileOutputStream(file)));
			try
			{
				output.writeObject(this);
			}
			finally
			{
				output.close();
			}
		}

		static SparseMlpModel load(File file)
				throws IOException, ClassNotFoundException
		{
			ObjectInputStream input = new ObjectInputStream(
					new BufferedInputStream(new FileInputStream(file)));
			try
			{
				Object object = input.readObject();
				if (!(object instanceof SparseMlpModel))
				{
					throw new IOException("Not a sparse MLP model: " + file);
				}
				return (SparseMlpModel)object;
			}
			finally
			{
				input.close();
			}
		}

		static SparseMlpModel average(List<SparseMlpModel> models)
		{
			if (models == null || models.isEmpty())
			{
				throw new IllegalArgumentException("No sparse MLP models to average.");
			}
			SparseMlpModel first = models.get(0);
			double[][][] averagedWeights = zerosLike(first.weights);
			double[][] averagedBiases = zerosLike(first.biases);
			int totalWeight = 0;
			for (SparseMlpModel model : models)
			{
				first.requireCompatible(model);
				int modelWeight = Math.max(1, model.sampleCount);
				totalWeight += modelWeight;
				addWeighted(averagedWeights, model.weights, modelWeight);
				addWeighted(averagedBiases, model.biases, modelWeight);
			}
			scale(averagedWeights, 1.0 / Math.max(1, totalWeight));
			scale(averagedBiases, 1.0 / Math.max(1, totalWeight));
			SparseMlpModel averaged = new SparseMlpModel(
					first.inputSize,
					first.hiddenLayerCount,
					first.hiddenSize,
					first.outputSize,
					averagedWeights,
					averagedBiases);
			averaged.sampleCount = totalWeight;
			return averaged;
		}

		private void trainOne(
				TrainingExample example,
				double learningRate,
				double l2,
				double fedProxMu,
				double[][][] referenceWeights,
				double[][] referenceBiases)
		{
			if (example.getLabelIndex() < 0
					|| example.getLabelIndex() >= outputSize)
			{
				return;
			}
			double[][] activations = forward(example.getFeatures());
			double[][] deltas = computeDeltas(activations, example.getLabelIndex());
			updateFirstLayer(
					example.getFeatures(),
					deltas[0],
					learningRate,
					l2,
					fedProxMu,
					referenceWeights[0]);
			for (int layer = 1; layer < weights.length; layer++)
			{
				updateDenseLayer(
						layer,
						activations[layer - 1],
						deltas[layer],
						learningRate,
						l2,
						fedProxMu,
						referenceWeights[layer]);
			}
			for (int layer = 0; layer < biases.length; layer++)
			{
				for (int index = 0; index < biases[layer].length; index++)
				{
					double proximal =
							fedProxMu * (biases[layer][index]
									- referenceBiases[layer][index]);
					biases[layer][index] -= learningRate
							* (deltas[layer][index] + proximal);
				}
			}
		}

		private double[][] forward(Map<Integer,Double> sparseFeatures)
		{
			double[][] activations = new double[weights.length][];
			activations[0] = new double[hiddenSize];
			for (int target = 0; target < hiddenSize; target++)
			{
				double sum = biases[0][target];
				for (Map.Entry<Integer,Double> entry : sparseFeatures.entrySet())
				{
					int source = entry.getKey();
					if (source >= 0 && source < inputSize)
					{
						sum += entry.getValue() * weights[0][source][target];
					}
				}
				activations[0][target] = Math.tanh(sum);
			}
			for (int layer = 1; layer < weights.length - 1; layer++)
			{
				activations[layer] = denseTanh(
						activations[layer - 1],
						weights[layer],
						biases[layer]);
			}
			int outputLayer = weights.length - 1;
			activations[outputLayer] = softmax(
					denseLinear(
							activations[outputLayer - 1],
							weights[outputLayer],
							biases[outputLayer]));
			return activations;
		}

		private double[][] computeDeltas(double[][] activations, int labelIndex)
		{
			double[][] deltas = new double[weights.length][];
			int outputLayer = weights.length - 1;
			deltas[outputLayer] = Arrays.copyOf(
					activations[outputLayer],
					activations[outputLayer].length);
			deltas[outputLayer][labelIndex] -= 1.0;
			for (int layer = outputLayer - 1; layer >= 0; layer--)
			{
				deltas[layer] = new double[activations[layer].length];
				for (int source = 0; source < activations[layer].length; source++)
				{
					double downstream = 0.0;
					for (int target = 0; target < deltas[layer + 1].length; target++)
					{
						downstream += weights[layer + 1][source][target]
								* deltas[layer + 1][target];
					}
					double activation = activations[layer][source];
					deltas[layer][source] = downstream
							* (1.0 - activation * activation);
				}
			}
			return deltas;
		}

		private void updateFirstLayer(
				Map<Integer,Double> sparseFeatures,
				double[] delta,
				double learningRate,
				double l2,
				double fedProxMu,
				double[][] referenceWeights)
		{
			for (Map.Entry<Integer,Double> entry : sparseFeatures.entrySet())
			{
				int source = entry.getKey();
				if (source < 0 || source >= inputSize)
				{
					continue;
				}
				double value = entry.getValue();
				for (int target = 0; target < delta.length; target++)
				{
					double current = weights[0][source][target];
					double gradient = value * delta[target]
							+ l2 * current
							+ fedProxMu * (current - referenceWeights[source][target]);
					weights[0][source][target] -= learningRate * gradient;
				}
			}
		}

		private void updateDenseLayer(
				int layer,
				double[] previousActivation,
				double[] delta,
				double learningRate,
				double l2,
				double fedProxMu,
				double[][] referenceWeights)
		{
			for (int source = 0; source < previousActivation.length; source++)
			{
				double activation = previousActivation[source];
				for (int target = 0; target < delta.length; target++)
				{
					double current = weights[layer][source][target];
					double gradient = activation * delta[target]
							+ l2 * current
							+ fedProxMu * (current - referenceWeights[source][target]);
					weights[layer][source][target] -= learningRate * gradient;
				}
			}
		}

		private void requireCompatible(SparseMlpModel other)
		{
			if (other.inputSize != inputSize
					|| other.hiddenLayerCount != hiddenLayerCount
					|| other.hiddenSize != hiddenSize
					|| other.outputSize != outputSize)
			{
				throw new IllegalArgumentException(
						"Sparse MLP model shapes are not compatible.");
			}
		}

		// ------------------------------------------------- federated kernel
		//
		// Everything below serves federated averaging and leaves this model
		// unchanged: a client trains a private copy and returns the difference,
		// and the next global model is a new object.

		int getInputSize()
		{
			return inputSize;
		}

		int getOutputSize()
		{
			return outputSize;
		}

		/** Weight layers: the hidden layers and the output layer. */
		int layerCount()
		{
			return weights.length;
		}

		/** Units feeding layer {@code layer}. */
		int layerInputs(int layer)
		{
			return weights[layer].length;
		}

		/** Units of layer {@code layer}. */
		int layerOutputs(int layer)
		{
			return biases[layer].length;
		}

		double weightAt(int layer, int source, int target)
		{
			return weights[layer][source][target];
		}

		double biasAt(int layer, int index)
		{
			return biases[layer][index];
		}

		/** Weights and biases of the whole model. */
		long parameterCount()
		{
			long count = 0L;
			for (int layer = 0; layer < weights.length; layer++)
			{
				count += (long)weights[layer].length * biases[layer].length + biases[layer].length;
			}
			return count;
		}

		/** Wire size of the whole model: eight bytes per parameter and its shape. */
		long payloadBytes()
		{
			return 16L + 8L * parameterCount();
		}

		/**
		 * SHA-256 over the shape and the bits of every parameter, in a fixed
		 * order: two models have the same fingerprint exactly when they are
		 * bit-identical.
		 */
		String fingerprint()
		{
			java.security.MessageDigest digest;
			try
			{
				digest = java.security.MessageDigest.getInstance("SHA-256");
			}
			catch (java.security.NoSuchAlgorithmException impossible)
			{
				throw new IllegalStateException(impossible);
			}
			java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocate(8192);
			buffer.putInt(inputSize).putInt(hiddenLayerCount).putInt(hiddenSize).putInt(outputSize);
			for (int layer = 0; layer < weights.length; layer++)
			{
				for (double[] row : weights[layer])
				{
					digestInto(digest, buffer, row);
				}
				digestInto(digest, buffer, biases[layer]);
			}
			buffer.flip();
			digest.update(buffer);
			StringBuilder hex = new StringBuilder();
			for (byte value : digest.digest())
			{
				hex.append(String.format("%02x", Integer.valueOf(value & 0xff)));
			}
			return hex.toString();
		}

		private static void digestInto(java.security.MessageDigest digest, java.nio.ByteBuffer buffer,
				double[] values)
		{
			for (double value : values)
			{
				if (buffer.remaining() < 8)
				{
					buffer.flip();
					digest.update(buffer);
					buffer.clear();
				}
				buffer.putLong(Double.doubleToLongBits(value));
			}
		}

		double[] predict(SparseRow row)
		{
			double[][] activations = forward(row);
			return activations[activations.length - 1];
		}

		/** Mean softmax cross-entropy of the rows' true classes; rows without a class are skipped. */
		double meanCrossEntropyOfRows(List<SparseRow> rows)
		{
			double total = 0.0;
			int counted = 0;
			for (SparseRow row : rows)
			{
				int label = row.labelIndex;
				if (label < 0 || label >= outputSize)
				{
					continue;
				}
				double probability = predict(row)[label];
				total -= Math.log(Math.max(probability, 1e-15));
				counted++;
			}
			return counted == 0 ? Double.NaN : total / counted;
		}

		/**
		 * Local training of one federated client. A private copy of this (global)
		 * model runs {@code epochs} passes of stochastic gradient descent over
		 * the client's rows, each pass in an order drawn from {@code seed}, on
		 * the cross-entropy with L2 and, when {@code proximalMu} is positive,
		 * with the FedProx term mu/2 |w - global|^2 that keeps the client near
		 * the model it started from. The result is the client's update: its
		 * weights minus this model's. This model is not modified.
		 *
		 * A first-layer row is copied only when one of the client's rows first
		 * changes it, so both the copy and the update grow with the client's own
		 * features, not with the vocabulary.
		 */
		ModelUpdate localUpdate(List<SparseRow> rows, int epochs, double learningRate, double l2,
				double proximalMu, long seed)
		{
			LocalRun run = runLocal(rows, epochs, learningRate, l2, proximalMu, seed);
			int[] touched = new int[run.owned.cardinality()];
			double[][] first = new double[touched.length][];
			int position = 0;
			for (int row = run.owned.nextSetBit(0); row >= 0; row = run.owned.nextSetBit(row + 1))
			{
				touched[position] = row;
				first[position] = difference(run.model.weights[0][row], weights[0][row]);
				position++;
			}
			double[][][] upper = new double[weights.length - 1][][];
			for (int layer = 1; layer < weights.length; layer++)
			{
				upper[layer - 1] = new double[weights[layer].length][];
				for (int source = 0; source < weights[layer].length; source++)
				{
					upper[layer - 1][source] = difference(
							run.model.weights[layer][source], weights[layer][source]);
				}
			}
			double[][] biasDelta = new double[biases.length][];
			for (int layer = 0; layer < biases.length; layer++)
			{
				biasDelta[layer] = difference(run.model.biases[layer], biases[layer]);
			}
			return new ModelUpdate(run.samples, inputSize, touched, first, upper, biasDelta);
		}

		/**
		 * The weights {@link #localUpdate} trains, as a model of their own; the
		 * update is this result minus the model it was called on.
		 */
		SparseMlpModel locallyTrained(List<SparseRow> rows, int epochs, double learningRate, double l2,
				double proximalMu, long seed)
		{
			LocalRun run = runLocal(rows, epochs, learningRate, l2, proximalMu, seed);
			SparseMlpModel trained = new SparseMlpModel(inputSize, hiddenLayerCount, hiddenSize, outputSize,
					deepCopy(run.model.weights), copyBiases(run.model.biases));
			trained.sampleCount = run.samples;
			trained.trainingSeed = trainingSeed;
			return trained;
		}

		/** A client's private model, the first-layer rows it owns, and its labelled rows. */
		private static final class LocalRun
		{
			private final SparseMlpModel model;
			private final BitSet owned;
			private final int samples;

			private LocalRun(SparseMlpModel model, BitSet owned, int samples)
			{
				this.model = model;
				this.owned = owned;
				this.samples = samples;
			}
		}

		private LocalRun runLocal(List<SparseRow> rows, int epochs, double learningRate, double l2,
				double proximalMu, long seed)
		{
			if (rows == null || rows.isEmpty())
			{
				throw new IllegalArgumentException(
						"Federated local training requires at least one row.");
			}
			int samples = 0;
			for (SparseRow row : rows)
			{
				if (row.labelIndex >= 0 && row.labelIndex < outputSize)
				{
					samples++;
				}
			}
			if (samples == 0)
			{
				throw new IllegalArgumentException(
						"Federated local training requires at least one labelled row.");
			}
			int safeEpochs = Math.max(1, epochs);
			double safeLearningRate = Math.max(0.0000001, learningRate);
			double safeL2 = Math.max(0.0, l2);
			double safeMu = Math.max(0.0, proximalMu);
			double[][][] localWeights = new double[weights.length][][];
			//First-layer rows stay shared with this model until they are written.
			localWeights[0] = weights[0].clone();
			for (int layer = 1; layer < weights.length; layer++)
			{
				localWeights[layer] = new double[weights[layer].length][];
				for (int source = 0; source < weights[layer].length; source++)
				{
					localWeights[layer][source] = Arrays.copyOf(
							weights[layer][source], weights[layer][source].length);
				}
			}
			SparseMlpModel local = new SparseMlpModel(inputSize, hiddenLayerCount, hiddenSize, outputSize,
					localWeights, copyBiases(biases));
			BitSet owned = new BitSet(inputSize);
			int[] order = new int[rows.size()];
			for (int index = 0; index < order.length; index++)
			{
				order[index] = index;
			}
			for (int epoch = 0; epoch < safeEpochs; epoch++)
			{
				//A revoked client unit (drain, crash) stops between epochs.
				UnitCancellation.check();
				shuffleOrder(order, new Random(seed + epoch * 0x9E3779B9L));
				for (int index : order)
				{
					local.trainOwned(rows.get(index), safeLearningRate, safeL2, safeMu, this, owned);
				}
			}
			return new LocalRun(local, owned, samples);
		}

		/**
		 * One step of stochastic gradient descent on a client's private model:
		 * {@link #trainOne} on a {@link SparseRow}, taking the proximal reference
		 * from {@code global} and making a first-layer row private the first
		 * time it is written.
		 */
		private void trainOwned(SparseRow row, double learningRate, double l2, double proximalMu,
				SparseMlpModel global, BitSet owned)
		{
			int label = row.labelIndex;
			if (label < 0 || label >= outputSize)
			{
				return;
			}
			double[][] activations = forward(row);
			double[][] deltas = computeDeltas(activations, label);
			double[] delta = deltas[0];
			for (int position = 0; position < row.indexes.length; position++)
			{
				int source = row.indexes[position];
				if (source >= inputSize)
				{
					continue;
				}
				double[] reference = global.weights[0][source];
				if (!owned.get(source))
				{
					weights[0][source] = Arrays.copyOf(reference, reference.length);
					owned.set(source);
				}
				double[] outgoing = weights[0][source];
				double value = row.values[position];
				for (int target = 0; target < delta.length; target++)
				{
					double current = outgoing[target];
					double gradient = value * delta[target]
							+ l2 * current
							+ proximalMu * (current - reference[target]);
					outgoing[target] -= learningRate * gradient;
				}
			}
			for (int layer = 1; layer < weights.length; layer++)
			{
				updateDenseLayer(layer, activations[layer - 1], deltas[layer], learningRate, l2,
						proximalMu, global.weights[layer]);
			}
			for (int layer = 0; layer < biases.length; layer++)
			{
				for (int index = 0; index < biases[layer].length; index++)
				{
					double proximal = proximalMu * (biases[layer][index] - global.biases[layer][index]);
					biases[layer][index] -= learningRate * (deltas[layer][index] + proximal);
				}
			}
		}

		/** {@link #forward(Map)} on a {@link SparseRow}: the same sums in the row's index order. */
		private double[][] forward(SparseRow row)
		{
			double[][] activations = new double[weights.length][];
			double[] first = Arrays.copyOf(biases[0], hiddenSize);
			for (int position = 0; position < row.indexes.length; position++)
			{
				int source = row.indexes[position];
				if (source >= inputSize)
				{
					continue;
				}
				double value = row.values[position];
				double[] outgoing = weights[0][source];
				for (int target = 0; target < hiddenSize; target++)
				{
					first[target] += value * outgoing[target];
				}
			}
			for (int target = 0; target < hiddenSize; target++)
			{
				first[target] = Math.tanh(first[target]);
			}
			activations[0] = first;
			for (int layer = 1; layer < weights.length - 1; layer++)
			{
				activations[layer] = denseTanh(
						activations[layer - 1],
						weights[layer],
						biases[layer]);
			}
			int outputLayer = weights.length - 1;
			activations[outputLayer] = softmax(
					denseLinear(
							activations[outputLayer - 1],
							weights[outputLayer],
							biases[outputLayer]));
			return activations;
		}

		/**
		 * Federated averaging: a new model equal to this one plus the clients'
		 * updates averaged with their sample counts as weights,
		 * w + sum(n_k * delta_k) / sum(n_k). The sum runs in the order of
		 * {@code updates}; callers pass the clients in a fixed order, so the
		 * result does not depend on where or when each client trained. A
		 * first-layer row no client touched is exactly unchanged.
		 */
		SparseMlpModel plusAverage(List<ModelUpdate> updates)
		{
			if (updates == null || updates.isEmpty())
			{
				throw new IllegalArgumentException("No federated updates to average.");
			}
			long total = 0L;
			for (ModelUpdate update : updates)
			{
				requireCompatible(update);
				total += update.samples;
			}
			double[][][] next = deepCopy(weights);
			double[][] nextBiases = copyBiases(biases);
			double[][] firstSums = new double[inputSize][];
			for (ModelUpdate update : updates)
			{
				for (int position = 0; position < update.rows.length; position++)
				{
					int row = update.rows[position];
					double[] sums = firstSums[row];
					if (sums == null)
					{
						sums = new double[hiddenSize];
						firstSums[row] = sums;
					}
					double[] delta = update.firstLayer[position];
					for (int target = 0; target < hiddenSize; target++)
					{
						sums[target] += update.samples * delta[target];
					}
				}
			}
			for (int row = 0; row < inputSize; row++)
			{
				double[] sums = firstSums[row];
				if (sums == null)
				{
					continue;
				}
				for (int target = 0; target < hiddenSize; target++)
				{
					next[0][row][target] = weights[0][row][target] + sums[target] / total;
				}
			}
			for (int layer = 1; layer < weights.length; layer++)
			{
				for (int source = 0; source < weights[layer].length; source++)
				{
					for (int target = 0; target < weights[layer][source].length; target++)
					{
						double sum = 0.0;
						for (ModelUpdate update : updates)
						{
							sum += update.samples * update.upperLayers[layer - 1][source][target];
						}
						next[layer][source][target] = weights[layer][source][target] + sum / total;
					}
				}
			}
			for (int layer = 0; layer < biases.length; layer++)
			{
				for (int index = 0; index < biases[layer].length; index++)
				{
					double sum = 0.0;
					for (ModelUpdate update : updates)
					{
						sum += update.samples * update.biases[layer][index];
					}
					nextBiases[layer][index] = biases[layer][index] + sum / total;
				}
			}
			SparseMlpModel averaged = new SparseMlpModel(inputSize, hiddenLayerCount, hiddenSize, outputSize,
					next, nextBiases);
			averaged.sampleCount = (int)Math.min(Integer.MAX_VALUE, total);
			averaged.trainingSeed = trainingSeed;
			return averaged;
		}

		private void requireCompatible(ModelUpdate update)
		{
			boolean compatible = update != null
					&& update.samples > 0
					&& update.inputSize == inputSize
					&& update.upperLayers.length == weights.length - 1
					&& update.biases.length == biases.length;
			for (int position = 0; compatible && position < update.rows.length; position++)
			{
				compatible = update.rows[position] < inputSize
						&& update.firstLayer[position].length == hiddenSize;
			}
			for (int layer = 1; compatible && layer < weights.length; layer++)
			{
				double[][] delta = update.upperLayers[layer - 1];
				compatible = delta.length == weights[layer].length;
				for (int source = 0; compatible && source < delta.length; source++)
				{
					compatible = delta[source].length == weights[layer][source].length;
				}
			}
			for (int layer = 0; compatible && layer < biases.length; layer++)
			{
				compatible = update.biases[layer].length == biases[layer].length;
			}
			if (!compatible)
			{
				throw new IllegalArgumentException(
						"A federated update does not fit the model it is averaged into.");
			}
		}
	}

	/**
	 * What one federated client returns: its trained weights minus the global
	 * weights it started from, and the number of rows it trained on. First-layer
	 * rows the client never touched are exactly zero and are left out, which
	 * keeps the update as small as the client's own features.
	 */
	static final class ModelUpdate implements PayloadSize.Sized, Serializable
	{
		private static final long serialVersionUID = 1L;

		private final int samples;
		private final int inputSize;
		private final int[] rows;
		private final double[][] firstLayer;
		private final double[][][] upperLayers;
		private final double[][] biases;

		private ModelUpdate(int samples, int inputSize, int[] rows, double[][] firstLayer,
				double[][][] upperLayers, double[][] biases)
		{
			this.samples = samples;
			this.inputSize = inputSize;
			this.rows = rows;
			this.firstLayer = firstLayer;
			this.upperLayers = upperLayers;
			this.biases = biases;
		}

		/** Rows the client trained on: its weight in the average. */
		int getSamples()
		{
			return samples;
		}

		/** First-layer rows (features) the client changed. */
		int getTouchedRows()
		{
			return rows.length;
		}

		/** The {@code position}-th touched first-layer row, ascending. */
		int touchedRowAt(int position)
		{
			return rows[position];
		}

		/** Change of the weight from the {@code position}-th touched row to hidden unit {@code target}. */
		double firstLayerDelta(int position, int target)
		{
			return firstLayer[position][target];
		}

		/** Change of a weight of layer {@code layer} (1 or above). */
		double upperDelta(int layer, int source, int target)
		{
			return upperLayers[layer - 1][source][target];
		}

		double biasDelta(int layer, int index)
		{
			return biases[layer][index];
		}

		/** Wire size: the sample count, the touched row numbers and eight bytes per value sent. */
		public long payloadBytes()
		{
			long values = 0L;
			for (double[] row : firstLayer)
			{
				values += row.length;
			}
			for (double[][] layer : upperLayers)
			{
				for (double[] row : layer)
				{
					values += row.length;
				}
			}
			for (double[] layer : biases)
			{
				values += layer.length;
			}
			return 4L + 4L + 4L * rows.length + 8L * values;
		}

		/** Largest absolute change of any weight or bias. */
		double maxAbsolute()
		{
			double max = 0.0;
			for (double[] row : firstLayer)
			{
				for (double value : row)
				{
					max = Math.max(max, Math.abs(value));
				}
			}
			for (double[][] layer : upperLayers)
			{
				for (double[] row : layer)
				{
					for (double value : row)
					{
						max = Math.max(max, Math.abs(value));
					}
				}
			}
			for (double[] layer : biases)
			{
				for (double value : layer)
				{
					max = Math.max(max, Math.abs(value));
				}
			}
			return max;
		}
	}

	static void saveAveragedModel(List<String> modelPaths, String outputPath)
			throws IOException, ClassNotFoundException
	{
		java.util.ArrayList<SparseMlpModel> models =
				new java.util.ArrayList<SparseMlpModel>();
		for (String modelPath : modelPaths)
		{
			models.add(SparseMlpModel.load(new File(modelPath)));
		}
		SparseMlpModel.average(models).save(new File(outputPath));
	}

	private static double[] denseLinear(
			double[] input,
			double[][] layerWeights,
			double[] layerBiases)
	{
		double[] output = Arrays.copyOf(layerBiases, layerBiases.length);
		for (int source = 0; source < input.length; source++)
		{
			double value = input[source];
			for (int target = 0; target < output.length; target++)
			{
				output[target] += value * layerWeights[source][target];
			}
		}
		return output;
	}

	private static double[] denseTanh(
			double[] input,
			double[][] layerWeights,
			double[] layerBiases)
	{
		double[] linear = denseLinear(input, layerWeights, layerBiases);
		for (int index = 0; index < linear.length; index++)
		{
			linear[index] = Math.tanh(linear[index]);
		}
		return linear;
	}

	private static double[] difference(double[] left, double[] right)
	{
		double[] result = new double[left.length];
		for (int index = 0; index < result.length; index++)
		{
			result[index] = left[index] - right[index];
		}
		return result;
	}

	private static double[] softmax(double[] logits)
	{
		double max = logits[0];
		for (int index = 1; index < logits.length; index++)
		{
			max = Math.max(max, logits[index]);
		}
		double sum = 0.0;
		double[] probabilities = new double[logits.length];
		for (int index = 0; index < logits.length; index++)
		{
			probabilities[index] = Math.exp(logits[index] - max);
			sum += probabilities[index];
		}
		if (sum <= 0.0 || !Double.isFinite(sum))
		{
			Arrays.fill(probabilities, 1.0 / probabilities.length);
			return probabilities;
		}
		for (int index = 0; index < probabilities.length; index++)
		{
			probabilities[index] /= sum;
		}
		return probabilities;
	}

	private static double[][][] deepCopy(double[][][] source)
	{
		double[][][] copy = new double[source.length][][];
		for (int layer = 0; layer < source.length; layer++)
		{
			copy[layer] = new double[source[layer].length][];
			for (int row = 0; row < source[layer].length; row++)
			{
				copy[layer][row] = Arrays.copyOf(
						source[layer][row], source[layer][row].length);
			}
		}
		return copy;
	}

	private static double[][] copyBiases(double[][] source)
	{
		double[][] copy = new double[source.length][];
		for (int layer = 0; layer < source.length; layer++)
		{
			copy[layer] = Arrays.copyOf(source[layer], source[layer].length);
		}
		return copy;
	}

	private static double[][][] zerosLike(double[][][] source)
	{
		double[][][] zero = new double[source.length][][];
		for (int layer = 0; layer < source.length; layer++)
		{
			zero[layer] = new double[source[layer].length][];
			for (int row = 0; row < source[layer].length; row++)
			{
				zero[layer][row] = new double[source[layer][row].length];
			}
		}
		return zero;
	}

	private static double[][] zerosLike(double[][] source)
	{
		double[][] zero = new double[source.length][];
		for (int row = 0; row < source.length; row++)
		{
			zero[row] = new double[source[row].length];
		}
		return zero;
	}

	private static void addWeighted(
			double[][][] destination,
			double[][][] source,
			double weight)
	{
		for (int layer = 0; layer < destination.length; layer++)
		{
			for (int row = 0; row < destination[layer].length; row++)
			{
				for (int column = 0; column < destination[layer][row].length; column++)
				{
					destination[layer][row][column] +=
							source[layer][row][column] * weight;
				}
			}
		}
	}

	private static void addWeighted(
			double[][] destination,
			double[][] source,
			double weight)
	{
		for (int row = 0; row < destination.length; row++)
		{
			for (int column = 0; column < destination[row].length; column++)
			{
				destination[row][column] += source[row][column] * weight;
			}
		}
	}

	private static void scale(double[][][] values, double multiplier)
	{
		for (int layer = 0; layer < values.length; layer++)
		{
			for (int row = 0; row < values[layer].length; row++)
			{
				for (int column = 0; column < values[layer][row].length; column++)
				{
					values[layer][row][column] *= multiplier;
				}
			}
		}
	}

	private static void scale(double[][] values, double multiplier)
	{
		for (int row = 0; row < values.length; row++)
		{
			for (int column = 0; column < values[row].length; column++)
			{
				values[row][column] *= multiplier;
			}
		}
	}
}
