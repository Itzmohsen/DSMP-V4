package TwitterGatherDataFollowers.userRyersonU;

import java.util.ArrayList;
import java.util.List;

public final class AlgorithmParameterSettingsSelfTest
{
	private AlgorithmParameterSettingsSelfTest()
	{
	}

	public static void main(String[] args)
	{
		defaultsPreserveCurrentValues();
		validationRejectsUnsafeValues();
		doc2VecArgumentsFollowValidatedOrder();
		systemPropertiesRoundTrip();
		System.out.println("AlgorithmParameterSettingsSelfTest passed");
	}

	private static void defaultsPreserveCurrentValues()
	{
		AlgorithmParameterSettings defaults =
				AlgorithmParameterSettings.defaults();
		require(!defaults.isCustomEnabled(), "defaults should not be custom");
		require(defaults.getKMeansClusters() == 3,
				"K-Means default cluster count changed");
		require(defaults.getKMeansMaxIterations() == 10,
				"K-Means default iteration count changed");
		requireClose(defaults.getSvmC(), 0.1,
				"exact SVM default C changed");
		require(defaults.isSvmCalibrationEnabled(),
				"exact SVM default calibration changed");
		require(defaults.getScalableSvmEpochs() == 10,
				"scalable SVM default epochs changed");
		requireClose(defaults.getScalableSvmAggressiveness(), 0.1,
				"scalable SVM default aggressiveness changed");
			require(defaults.getMlpHiddenNeurons() == 10,
					"MLP default hidden-neuron count changed");
			require(AlgorithmParameterSettings.MLP_ENGINE_SPARSE_FEDERATED.equals(
					defaults.getMlpEngine()),
					"the default MLP engine is the softmax engine with early stopping");
			require(defaults.getMlpHiddenLayers() == 1,
					"MLP default hidden-layer count changed");
			requireClose(defaults.getMlpLearningRate(), 0.1,
					"MLP default learning rate changed");
			requireClose(defaults.getMlpMaxError(), 0.01,
					"MLP default max error changed");
			require(defaults.getMlpSparseEpochs() == AlgorithmParameterSettings.DEFAULT_MLP_SPARSE_EPOCHS,
					"the softmax MLP's epoch limit changed");
			requireClose(defaults.getMlpSparseL2(), 0.0001,
					"MLP sparse default L2 changed");
			requireClose(defaults.getMlpFedProxMu(), 0.0,
					"MLP FedProx default changed");
			require(defaults.getDoc2VecVectorSize() == 300,
					"Doc2Vec default vector size changed");
		require(defaults.getDoc2VecEpochs() == 20,
				"Doc2Vec default epochs changed");
		require(defaults.getSimilarityLocalTopMultiplier() == 10,
				"Similarity default local multiplier changed");
		require(defaults.getSimilarityLocalTopMinimum() == 20,
				"Similarity default local minimum changed");
	}

	private static void validationRejectsUnsafeValues()
	{
		AlgorithmParameterSettings settings =
				AlgorithmParameterSettings.defaults();
		settings.setCustomEnabled(true);
		settings.setDoc2VecVectorSize(10);
		settings.setDoc2VecEpochs(0);
		List<String> errors = settings.validateForAlgorithm(
				RecommendationModeSupport.DOC2VEC);
		require(errors.size() >= 2,
				"invalid Doc2Vec parameters were accepted");
		settings.resetToDefaults();
		settings.setCustomEnabled(true);
			settings.setSvmC(0.0);
			require(!settings.validateForAlgorithm(
					RecommendationModeSupport.SVM).isEmpty(),
					"invalid SVM C was accepted");
			settings.resetToDefaults();
			settings.setCustomEnabled(true);
			settings.setMlpHiddenLayers(0);
			require(!settings.validateForAlgorithm(
					RecommendationModeSupport.MLP).isEmpty(),
					"invalid MLP hidden-layer count was accepted");
		}

	private static void doc2VecArgumentsFollowValidatedOrder()
	{
		AlgorithmParameterSettings settings =
				AlgorithmParameterSettings.defaults();
		settings.setCustomEnabled(true);
		settings.setDoc2VecDm(1);
		settings.setDoc2VecDbowWords(0);
		settings.setDoc2VecMinCount(2);
		settings.setDoc2VecNegative(4);
		settings.setDoc2VecHs(1);
		settings.setDoc2VecSample(0.002);
		settings.setDoc2VecWindow(7);
		settings.setDoc2VecVectorSize(128);
		settings.setDoc2VecEpochs(9);
		List<String> args = settings.toDoc2VecCommandArguments();
		require(args.size() == 9, "Doc2Vec parameter argument count changed");
		require("1".equals(args.get(0)), "Doc2Vec dm order changed");
		require("0".equals(args.get(1)), "Doc2Vec dbow_words order changed");
		require("2".equals(args.get(2)), "Doc2Vec min_count order changed");
		require("4".equals(args.get(3)), "Doc2Vec negative order changed");
		require("1".equals(args.get(4)), "Doc2Vec hs order changed");
		require("0.002".equals(args.get(5)), "Doc2Vec sample order changed");
		require("7".equals(args.get(6)), "Doc2Vec window order changed");
		require("128".equals(args.get(7)), "Doc2Vec vector_size order changed");
		require("9".equals(args.get(8)), "Doc2Vec epochs order changed");
	}

	private static void systemPropertiesRoundTrip()
	{
		AlgorithmParameterSettings settings =
				AlgorithmParameterSettings.defaults();
		settings.setCustomEnabled(true);
		settings.setSvmC(2.5);
			settings.setSvmCalibration(false);
			settings.setScalableSvmEpochs(17);
			settings.setScalableSvmAggressiveness(0.42);
			settings.setMlpEngine(
					AlgorithmParameterSettings.MLP_ENGINE_SPARSE_FEDERATED);
			settings.setMlpHiddenNeurons(31);
			settings.setMlpHiddenLayers(5);
			settings.setMlpLearningRate(0.03);
			settings.setMlpMaxError(0.02);
			settings.setMlpSparseEpochs(13);
			settings.setMlpSparseL2(0.004);
			settings.setMlpFedProxMu(0.12);
			ArrayList<String> command = new ArrayList<String>();
			settings.addJavaSystemProperties(command);
		applyProperties(command);
		try
		{
			AlgorithmParameterSettings loaded =
					AlgorithmParameterSettings.fromSystemProperties();
			require(loaded.isCustomEnabled(),
					"custom property flag did not round-trip");
			requireClose(loaded.getSvmC(), 2.5,
					"SVM C did not round-trip");
			require(!loaded.isSvmCalibrationEnabled(),
					"SVM calibration did not round-trip");
			require(loaded.getScalableSvmEpochs() == 17,
					"scalable SVM epochs did not round-trip");
				requireClose(loaded.getScalableSvmAggressiveness(), 0.42,
						"scalable SVM aggressiveness did not round-trip");
				require(AlgorithmParameterSettings.MLP_ENGINE_SPARSE_FEDERATED.equals(
						loaded.getMlpEngine()),
						"MLP engine did not round-trip");
				require(loaded.getMlpHiddenNeurons() == 31,
						"MLP hidden neurons did not round-trip");
				require(loaded.getMlpHiddenLayers() == 5,
						"MLP hidden layers did not round-trip");
				requireClose(loaded.getMlpLearningRate(), 0.03,
						"MLP learning rate did not round-trip");
				requireClose(loaded.getMlpMaxError(), 0.02,
						"MLP max error did not round-trip");
				require(loaded.getMlpSparseEpochs() == 13,
						"MLP sparse epochs did not round-trip");
				requireClose(loaded.getMlpSparseL2(), 0.004,
						"MLP sparse L2 did not round-trip");
				requireClose(loaded.getMlpFedProxMu(), 0.12,
						"MLP FedProx mu did not round-trip");
			}
		finally
		{
			clearDsmpProperties(command);
		}
	}

	private static void applyProperties(List<String> command)
	{
		for (String arg : command)
		{
			if (arg.startsWith("-D") && arg.indexOf('=') > 2)
			{
				String payload = arg.substring(2);
				int split = payload.indexOf('=');
				System.setProperty(
						payload.substring(0, split),
						payload.substring(split + 1));
			}
		}
	}

	private static void clearDsmpProperties(List<String> command)
	{
		for (String arg : command)
		{
			if (arg.startsWith("-D") && arg.indexOf('=') > 2)
			{
				String name = arg.substring(2, arg.indexOf('='));
				System.clearProperty(name);
			}
		}
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
