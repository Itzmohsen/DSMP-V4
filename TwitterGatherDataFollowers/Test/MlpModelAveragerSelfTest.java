package TwitterGatherDataFollowers.userRyersonU;

import java.io.File;
import java.util.Arrays;

import org.neuroph.core.Connection;
import org.neuroph.core.Layer;
import org.neuroph.core.NeuralNetwork;
import org.neuroph.core.Neuron;
import org.neuroph.core.Weight;
import org.neuroph.nnet.MultiLayerPerceptron;
import org.neuroph.util.TransferFunctionType;

public final class MlpModelAveragerSelfTest
{
	private MlpModelAveragerSelfTest()
	{
	}

	public static void main(String[] args)
	{
		File first = temporaryModelFile("first");
		File second = temporaryModelFile("second");
		File third = temporaryModelFile("third");
		File averaged = temporaryModelFile("averaged");
		try
		{
			saveConstantModel(first, 1.0);
			saveConstantModel(second, 3.0);
			saveConstantModel(third, 5.0);
			MlpModelAverager.averageLegacyCompatible(
					Arrays.asList(
							first.getAbsolutePath(),
							second.getAbsolutePath(),
							third.getAbsolutePath()),
					averaged.getAbsolutePath());

			MultiLayerPerceptron result = (MultiLayerPerceptron)
					NeuralNetwork.createFromFile(averaged.getAbsolutePath());
			double expected = (3.0 + 8.0) / 3.0;
			for (int layerIndex = 1;
					layerIndex < result.getLayers().size(); layerIndex++)
			{
				Layer layer = result.getLayers().get(layerIndex);
				for (Neuron neuron : layer.getNeurons())
				{
					for (Connection connection : neuron.getInputConnections())
					{
						require(Math.abs(connection.getWeight().getValue() - expected)
								< 1.0e-12,
								"streamed MLP average changed legacy weight behavior");
					}
				}
			}
			System.out.println("MlpModelAveragerSelfTest passed");
		}
		finally
		{
			first.delete();
			second.delete();
			third.delete();
			averaged.delete();
		}
	}

	private static void saveConstantModel(File file, double value)
	{
		MultiLayerPerceptron model =
				new MultiLayerPerceptron(TransferFunctionType.TANH, 3, 2, 2);
		for (int layerIndex = 1;
				layerIndex < model.getLayers().size(); layerIndex++)
		{
			for (Neuron neuron : model.getLayers().get(layerIndex).getNeurons())
			{
				for (Connection connection : neuron.getInputConnections())
				{
					connection.setWeight(new Weight(value));
				}
			}
		}
		model.save(file.getAbsolutePath());
	}

	private static File temporaryModelFile(String label)
	{
		try
		{
			File file = File.createTempFile("mlp-"+label, ".nnet");
			file.delete();
			return file;
		}
		catch (java.io.IOException e)
		{
			throw new RuntimeException(e);
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
