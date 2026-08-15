package TwitterGatherDataFollowers.userRyersonU;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SparseFederatedMlpModelSupportSelfTest
{
	private SparseFederatedMlpModelSupportSelfTest()
	{
	}

	public static void main(String[] args) throws Exception
	{
		List<SparseFederatedMlpModelSupport.TrainingExample> examples =
				new ArrayList<SparseFederatedMlpModelSupport.TrainingExample>();
		examples.add(example("left-a", 0, 1.0, 0));
		examples.add(example("left-b", 0, 0.9, 0));
		examples.add(example("right-a", 1, 1.0, 1));
		examples.add(example("right-b", 1, 0.9, 1));

		SparseFederatedMlpModelSupport.SparseMlpModel model =
				SparseFederatedMlpModelSupport.SparseMlpModel.create(
						2, 1, 4, 2, 42L);
		model.train(examples, 120, 0.2, 0.0, 0.0);

		double[] left = model.predict(features(0, 1.0));
		double[] right = model.predict(features(1, 1.0));
		require(left[0] > left[1], "left feature should predict class 0");
		require(right[1] > right[0], "right feature should predict class 1");
		requireClose(left[0] + left[1], 1.0, "left probabilities should sum to 1");
		requireClose(right[0] + right[1], 1.0, "right probabilities should sum to 1");
		require(model.getSampleCount() == examples.size(),
				"sample count should match training examples");

		File tempDir = new File("classes/test-artifacts");
		if (!tempDir.exists())
		{
			tempDir.mkdirs();
		}
		File firstModel = new File(tempDir, "sparse-mlp-a.ser");
		File secondModel = new File(tempDir, "sparse-mlp-b.ser");
		File averagedModel = new File(tempDir, "sparse-mlp-avg.ser");
		model.save(firstModel);
		SparseFederatedMlpModelSupport.SparseMlpModel loaded =
				SparseFederatedMlpModelSupport.SparseMlpModel.load(firstModel);
		requireClose(
				loaded.predict(features(0, 1.0))[0],
				left[0],
				"loaded model should preserve predictions");

		SparseFederatedMlpModelSupport.SparseMlpModel second =
				SparseFederatedMlpModelSupport.SparseMlpModel.create(
						2, 1, 4, 2, 42L);
		second.train(examples, 80, 0.15, 0.0, 0.01);
		second.save(secondModel);
		List<String> paths = new ArrayList<String>();
		paths.add(firstModel.getPath());
		paths.add(secondModel.getPath());
		SparseFederatedMlpModelSupport.saveAveragedModel(
				paths, averagedModel.getPath());
		SparseFederatedMlpModelSupport.SparseMlpModel averaged =
				SparseFederatedMlpModelSupport.SparseMlpModel.load(averagedModel);
		double[] averagedLeft = averaged.predict(features(0, 1.0));
		require(averagedLeft.length == 2,
				"averaged model output shape changed");
		require(!Double.isNaN(averagedLeft[0]) && !Double.isNaN(averagedLeft[1]),
				"averaged model produced NaN probabilities");

		System.out.println("SparseFederatedMlpModelSupportSelfTest passed");
	}

	private static SparseFederatedMlpModelSupport.TrainingExample example(
			String user,
			int featureIndex,
			double value,
			int labelIndex)
	{
		return new SparseFederatedMlpModelSupport.TrainingExample(
				user, features(featureIndex, value), labelIndex);
	}

	private static Map<Integer,Double> features(int featureIndex, double value)
	{
		Map<Integer,Double> features = new LinkedHashMap<Integer,Double>();
		features.put(featureIndex, value);
		return features;
	}

	private static void require(boolean condition, String message)
	{
		if (!condition)
		{
			throw new AssertionError(message);
		}
	}

	private static void requireClose(double actual, double expected, String message)
	{
		if (Math.abs(actual - expected) > 0.0000001)
		{
			throw new AssertionError(message + ": " + actual);
		}
	}
}
