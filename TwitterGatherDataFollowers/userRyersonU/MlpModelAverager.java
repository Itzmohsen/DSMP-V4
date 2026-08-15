package TwitterGatherDataFollowers.userRyersonU;

import java.util.List;

import org.neuroph.core.Connection;
import org.neuroph.core.Layer;
import org.neuroph.core.NeuralNetwork;
import org.neuroph.core.Neuron;
import org.neuroph.core.Weight;
import org.neuroph.nnet.MultiLayerPerceptron;

final class MlpModelAverager
{
	private MlpModelAverager()
	{
	}

	static void averageLegacyCompatible(List<String> modelPaths, String outputPath)
	{
		if (modelPaths == null || modelPaths.isEmpty())
		{
			throw new IllegalArgumentException("At least one MLP model is required");
		}

		MultiLayerPerceptron averaged =
				(MultiLayerPerceptron) NeuralNetwork.createFromFile(modelPaths.get(0));
		int connectionCount = countInputConnections(averaged);
		double[] originalFirstWeights = new double[connectionCount];
		double[] weightSums = new double[connectionCount];
		accumulateInputWeights(
				averaged, weightSums, originalFirstWeights, true);

		for (int i = 1; i < modelPaths.size(); i++)
		{
			MultiLayerPerceptron current =
					(MultiLayerPerceptron) NeuralNetwork.createFromFile(modelPaths.get(i));
			accumulateInputWeights(current, weightSums, null, false);
			current = null;
		}

		applyLegacyCompatibleWeights(
				averaged, weightSums, originalFirstWeights, modelPaths.size());
		averaged.save(outputPath);
	}

	private static int countInputConnections(MultiLayerPerceptron network)
	{
		int count = 0;
		List<Layer> layers = network.getLayers();
		for (int layerIndex = 1; layerIndex < layers.size(); layerIndex++)
		{
			for (Neuron neuron : layers.get(layerIndex).getNeurons())
			{
				count += neuron.getInputConnections().size();
			}
		}
		return count;
	}

	private static void accumulateInputWeights(
			MultiLayerPerceptron network,
			double[] sums,
			double[] firstWeights,
			boolean recordFirst)
	{
		int index = 0;
		List<Layer> layers = network.getLayers();
		for (int layerIndex = 1; layerIndex < layers.size(); layerIndex++)
		{
			for (Neuron neuron : layers.get(layerIndex).getNeurons())
			{
				for (Connection connection : neuron.getInputConnections())
				{
					double value = connection.getWeight().getValue();
					sums[index] += value;
					if (recordFirst)
					{
						firstWeights[index] = value;
					}
					index++;
				}
			}
		}
		if (index != sums.length)
		{
			throw new IllegalStateException("MLP models do not share the same topology");
		}
	}

	private static void applyLegacyCompatibleWeights(
			MultiLayerPerceptron network,
			double[] sums,
			double[] firstWeights,
			int modelCount)
	{
		int index = 0;
		List<Layer> layers = network.getLayers();
		for (int layerIndex = 1; layerIndex < layers.size(); layerIndex++)
		{
			for (Neuron neuron : layers.get(layerIndex).getNeurons())
			{
				for (Connection connection : neuron.getInputConnections())
				{
					double trueAverage = sums[index] / modelCount;
					double otherModelsSum = sums[index] - firstWeights[index];
					double legacyFinalWeight =
							(trueAverage + otherModelsSum) / modelCount;
					connection.setWeight(new Weight(legacyFinalWeight));
					index++;
				}
			}
		}
	}
}
