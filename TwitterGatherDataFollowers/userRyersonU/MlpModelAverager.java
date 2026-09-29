package TwitterGatherDataFollowers.userRyersonU;

import java.util.List;

import org.neuroph.core.Connection;
import org.neuroph.core.Layer;
import org.neuroph.core.NeuralNetwork;
import org.neuroph.core.Neuron;
import org.neuroph.core.Weight;
import org.neuroph.nnet.MultiLayerPerceptron;

/**
 * Uniform federated averaging of the legacy Neuroph MLP node models.
 *
 * Every node model contributes exactly 1/N of each connection weight, which is
 * the plain FedAvg update for equally weighted clients. Models are streamed one
 * at a time so only two networks are resident at once.
 *
 * The averaged weight is order independent: reordering the input model paths
 * cannot change the result.
 */
final class MlpModelAverager
{
	private MlpModelAverager()
	{
	}

	/**
	 * Averages the input-connection weights of every supplied MLP and writes the
	 * result to {@code outputPath}.
	 *
	 * @param modelPaths one saved Neuroph {@code .nnet} file per node; all must
	 *                   share the same topology
	 * @param outputPath destination for the averaged network
	 */
	static void average(List<String> modelPaths, String outputPath)
	{
		if (modelPaths == null || modelPaths.isEmpty())
		{
			throw new IllegalArgumentException("At least one MLP model is required");
		}

		MultiLayerPerceptron averaged =
				(MultiLayerPerceptron) NeuralNetwork.createFromFile(modelPaths.get(0));
		int connectionCount = countInputConnections(averaged);
		double[] weightSums = new double[connectionCount];
		accumulateInputWeights(averaged, weightSums);

		for (int i = 1; i < modelPaths.size(); i++)
		{
			MultiLayerPerceptron current =
					(MultiLayerPerceptron) NeuralNetwork.createFromFile(modelPaths.get(i));
			accumulateInputWeights(current, weightSums);
			current = null;
		}

		applyAveragedWeights(averaged, weightSums, modelPaths.size());
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
			double[] sums)
	{
		int index = 0;
		List<Layer> layers = network.getLayers();
		for (int layerIndex = 1; layerIndex < layers.size(); layerIndex++)
		{
			for (Neuron neuron : layers.get(layerIndex).getNeurons())
			{
				for (Connection connection : neuron.getInputConnections())
				{
					if (index >= sums.length)
					{
						throw new IllegalStateException(
								"MLP models do not share the same topology");
					}
					sums[index] += connection.getWeight().getValue();
					index++;
				}
			}
		}
		if (index != sums.length)
		{
			throw new IllegalStateException("MLP models do not share the same topology");
		}
	}

	private static void applyAveragedWeights(
			MultiLayerPerceptron network,
			double[] sums,
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
					connection.setWeight(new Weight(sums[index] / modelCount));
					index++;
				}
			}
		}
	}
}
