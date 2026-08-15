package TwitterGatherDataFollowers.userRyersonU;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Validated advanced algorithm parameters.
 *
 * The default profile deliberately mirrors the values that were previously
 * hard-coded in the recommender. When custom mode is disabled, all effective
 * getters return those defaults regardless of stored draft values.
 */
final class AlgorithmParameterSettings implements Serializable
{
	private static final long serialVersionUID = 1L;

	static final int DEFAULT_SIMILARITY_LOCAL_TOP_MULTIPLIER = 10;
	static final int DEFAULT_SIMILARITY_LOCAL_TOP_MINIMUM = 20;
	static final int DEFAULT_KMEANS_CLUSTERS = 3;
	static final int DEFAULT_KMEANS_MAX_ITERATIONS = 10;
	static final double DEFAULT_SVM_C = 0.1;
	static final boolean DEFAULT_SVM_CALIBRATION = true;
	static final int DEFAULT_SCALABLE_SVM_EPOCHS = 10;
	static final double DEFAULT_SCALABLE_SVM_AGGRESSIVENESS = 0.1;
	static final String MLP_ENGINE_LEGACY_NEUROPH = "legacy-neuroph";
	static final String MLP_ENGINE_SPARSE_FEDERATED = "sparse-federated";
	static final String DEFAULT_MLP_ENGINE = MLP_ENGINE_LEGACY_NEUROPH;
	static final int DEFAULT_MLP_HIDDEN_NEURONS = 10;
	static final int DEFAULT_MLP_HIDDEN_LAYERS = 1;
	static final double DEFAULT_MLP_LEARNING_RATE = 0.1;
	static final double DEFAULT_MLP_MAX_ERROR = 0.01;
	static final int DEFAULT_MLP_SPARSE_EPOCHS = 8;
	static final double DEFAULT_MLP_SPARSE_L2 = 0.0001;
	static final double DEFAULT_MLP_FEDPROX_MU = 0.0;
	static final int DEFAULT_DOC2VEC_DM = 0;
	static final int DEFAULT_DOC2VEC_DBOW_WORDS = 1;
	static final int DEFAULT_DOC2VEC_MIN_COUNT = 4;
	static final int DEFAULT_DOC2VEC_NEGATIVE = 3;
	static final int DEFAULT_DOC2VEC_HS = 0;
	static final double DEFAULT_DOC2VEC_SAMPLE = 1e-4;
	static final int DEFAULT_DOC2VEC_WINDOW = 5;
	static final int DEFAULT_DOC2VEC_VECTOR_SIZE = 300;
	static final int DEFAULT_DOC2VEC_EPOCHS = 20;

	private boolean customEnabled;
	private int similarityLocalTopMultiplier;
	private int similarityLocalTopMinimum;
	private int kMeansClusters;
	private int kMeansMaxIterations;
	private double svmC;
	private boolean svmCalibration;
	private int scalableSvmEpochs;
	private double scalableSvmAggressiveness;
	private String mlpEngine;
	private int mlpHiddenNeurons;
	private int mlpHiddenLayers;
	private double mlpLearningRate;
	private double mlpMaxError;
	private int mlpSparseEpochs;
	private double mlpSparseL2;
	private double mlpFedProxMu;
	private int doc2VecDm;
	private int doc2VecDbowWords;
	private int doc2VecMinCount;
	private int doc2VecNegative;
	private int doc2VecHs;
	private double doc2VecSample;
	private int doc2VecWindow;
	private int doc2VecVectorSize;
	private int doc2VecEpochs;

	private AlgorithmParameterSettings()
	{
		resetToDefaults();
	}

	static AlgorithmParameterSettings defaults()
	{
		return new AlgorithmParameterSettings();
	}

	static AlgorithmParameterSettings effective(
			AlgorithmParameterSettings settings)
	{
		if (settings == null || !settings.isCustomEnabled())
		{
			return defaults();
		}
		return settings.copy();
	}

	static AlgorithmParameterSettings fromSystemProperties()
	{
		AlgorithmParameterSettings settings = defaults();
		settings.customEnabled = Boolean.parseBoolean(
				System.getProperty("dsmp.algorithm.custom", "false"));
		settings.similarityLocalTopMultiplier = intProperty(
				"dsmp.similarity.localTopMultiplier",
				DEFAULT_SIMILARITY_LOCAL_TOP_MULTIPLIER);
		settings.similarityLocalTopMinimum = intProperty(
				"dsmp.similarity.localTopMinimum",
				DEFAULT_SIMILARITY_LOCAL_TOP_MINIMUM);
		settings.kMeansClusters = intProperty(
				"dsmp.kmeans.clusters", DEFAULT_KMEANS_CLUSTERS);
		settings.kMeansMaxIterations = intProperty(
				"dsmp.kmeans.maxIterations", DEFAULT_KMEANS_MAX_ITERATIONS);
		settings.svmC = doubleProperty("dsmp.svm.c", DEFAULT_SVM_C);
		settings.svmCalibration = Boolean.parseBoolean(
				System.getProperty("dsmp.svm.calibration",
						Boolean.toString(DEFAULT_SVM_CALIBRATION)));
		settings.scalableSvmEpochs = intProperty(
				"dsmp.scalableSvm.epochs", DEFAULT_SCALABLE_SVM_EPOCHS);
		settings.scalableSvmAggressiveness = doubleProperty(
				"dsmp.scalableSvm.aggressiveness",
				DEFAULT_SCALABLE_SVM_AGGRESSIVENESS);
		settings.mlpEngine = mlpEngineProperty(
				"dsmp.mlp.engine", DEFAULT_MLP_ENGINE);
		settings.mlpHiddenNeurons = intProperty(
				"dsmp.mlp.hiddenNeurons", DEFAULT_MLP_HIDDEN_NEURONS);
		settings.mlpHiddenLayers = intProperty(
				"dsmp.mlp.hiddenLayers", DEFAULT_MLP_HIDDEN_LAYERS);
		settings.mlpLearningRate = doubleProperty(
				"dsmp.mlp.learningRate", DEFAULT_MLP_LEARNING_RATE);
		settings.mlpMaxError = doubleProperty(
				"dsmp.mlp.maxError", DEFAULT_MLP_MAX_ERROR);
		settings.mlpSparseEpochs = intProperty(
				"dsmp.mlp.sparseEpochs", DEFAULT_MLP_SPARSE_EPOCHS);
		settings.mlpSparseL2 = doubleProperty(
				"dsmp.mlp.sparseL2", DEFAULT_MLP_SPARSE_L2);
		settings.mlpFedProxMu = doubleProperty(
				"dsmp.mlp.fedProxMu", DEFAULT_MLP_FEDPROX_MU);
		settings.doc2VecDm = intProperty(
				"dsmp.doc2vec.dm", DEFAULT_DOC2VEC_DM);
		settings.doc2VecDbowWords = intProperty(
				"dsmp.doc2vec.dbowWords", DEFAULT_DOC2VEC_DBOW_WORDS);
		settings.doc2VecMinCount = intProperty(
				"dsmp.doc2vec.minCount", DEFAULT_DOC2VEC_MIN_COUNT);
		settings.doc2VecNegative = intProperty(
				"dsmp.doc2vec.negative", DEFAULT_DOC2VEC_NEGATIVE);
		settings.doc2VecHs = intProperty(
				"dsmp.doc2vec.hs", DEFAULT_DOC2VEC_HS);
		settings.doc2VecSample = doubleProperty(
				"dsmp.doc2vec.sample", DEFAULT_DOC2VEC_SAMPLE);
		settings.doc2VecWindow = intProperty(
				"dsmp.doc2vec.window", DEFAULT_DOC2VEC_WINDOW);
		settings.doc2VecVectorSize = intProperty(
				"dsmp.doc2vec.vectorSize", DEFAULT_DOC2VEC_VECTOR_SIZE);
		settings.doc2VecEpochs = intProperty(
				"dsmp.doc2vec.epochs", DEFAULT_DOC2VEC_EPOCHS);
		return settings;
	}

	private static int intProperty(String name, int fallback)
	{
		String value = System.getProperty(name);
		if (value == null)
		{
			return fallback;
		}
		try
		{
			return Integer.parseInt(value.trim());
		}
		catch (NumberFormatException ignored)
		{
			return fallback;
		}
	}

	private static double doubleProperty(String name, double fallback)
	{
		String value = System.getProperty(name);
		if (value == null)
		{
			return fallback;
		}
		try
		{
			double parsed = Double.parseDouble(value.trim());
			return Double.isFinite(parsed) ? parsed : fallback;
		}
		catch (NumberFormatException ignored)
		{
			return fallback;
		}
	}

	private static String mlpEngineProperty(String name, String fallback)
	{
		String value = System.getProperty(name);
		if (isValidMlpEngine(value))
		{
			return value.trim();
		}
		return fallback;
	}

	AlgorithmParameterSettings copy()
	{
		AlgorithmParameterSettings copy = defaults();
		copy.customEnabled = customEnabled;
		copy.similarityLocalTopMultiplier = similarityLocalTopMultiplier;
		copy.similarityLocalTopMinimum = similarityLocalTopMinimum;
		copy.kMeansClusters = kMeansClusters;
		copy.kMeansMaxIterations = kMeansMaxIterations;
		copy.svmC = svmC;
		copy.svmCalibration = svmCalibration;
		copy.scalableSvmEpochs = scalableSvmEpochs;
		copy.scalableSvmAggressiveness = scalableSvmAggressiveness;
		copy.mlpEngine = mlpEngine;
		copy.mlpHiddenNeurons = mlpHiddenNeurons;
		copy.mlpHiddenLayers = mlpHiddenLayers;
		copy.mlpLearningRate = mlpLearningRate;
		copy.mlpMaxError = mlpMaxError;
		copy.mlpSparseEpochs = mlpSparseEpochs;
		copy.mlpSparseL2 = mlpSparseL2;
		copy.mlpFedProxMu = mlpFedProxMu;
		copy.doc2VecDm = doc2VecDm;
		copy.doc2VecDbowWords = doc2VecDbowWords;
		copy.doc2VecMinCount = doc2VecMinCount;
		copy.doc2VecNegative = doc2VecNegative;
		copy.doc2VecHs = doc2VecHs;
		copy.doc2VecSample = doc2VecSample;
		copy.doc2VecWindow = doc2VecWindow;
		copy.doc2VecVectorSize = doc2VecVectorSize;
		copy.doc2VecEpochs = doc2VecEpochs;
		return copy;
	}

	void resetToDefaults()
	{
		customEnabled = false;
		similarityLocalTopMultiplier = DEFAULT_SIMILARITY_LOCAL_TOP_MULTIPLIER;
		similarityLocalTopMinimum = DEFAULT_SIMILARITY_LOCAL_TOP_MINIMUM;
		kMeansClusters = DEFAULT_KMEANS_CLUSTERS;
		kMeansMaxIterations = DEFAULT_KMEANS_MAX_ITERATIONS;
		svmC = DEFAULT_SVM_C;
		svmCalibration = DEFAULT_SVM_CALIBRATION;
		scalableSvmEpochs = DEFAULT_SCALABLE_SVM_EPOCHS;
		scalableSvmAggressiveness = DEFAULT_SCALABLE_SVM_AGGRESSIVENESS;
		mlpEngine = DEFAULT_MLP_ENGINE;
		mlpHiddenNeurons = DEFAULT_MLP_HIDDEN_NEURONS;
		mlpHiddenLayers = DEFAULT_MLP_HIDDEN_LAYERS;
		mlpLearningRate = DEFAULT_MLP_LEARNING_RATE;
		mlpMaxError = DEFAULT_MLP_MAX_ERROR;
		mlpSparseEpochs = DEFAULT_MLP_SPARSE_EPOCHS;
		mlpSparseL2 = DEFAULT_MLP_SPARSE_L2;
		mlpFedProxMu = DEFAULT_MLP_FEDPROX_MU;
		doc2VecDm = DEFAULT_DOC2VEC_DM;
		doc2VecDbowWords = DEFAULT_DOC2VEC_DBOW_WORDS;
		doc2VecMinCount = DEFAULT_DOC2VEC_MIN_COUNT;
		doc2VecNegative = DEFAULT_DOC2VEC_NEGATIVE;
		doc2VecHs = DEFAULT_DOC2VEC_HS;
		doc2VecSample = DEFAULT_DOC2VEC_SAMPLE;
		doc2VecWindow = DEFAULT_DOC2VEC_WINDOW;
		doc2VecVectorSize = DEFAULT_DOC2VEC_VECTOR_SIZE;
		doc2VecEpochs = DEFAULT_DOC2VEC_EPOCHS;
	}

	boolean isCustomEnabled()
	{
		return customEnabled;
	}

	void setCustomEnabled(boolean customEnabled)
	{
		this.customEnabled = customEnabled;
	}

	boolean isDefaultProfile()
	{
		return !customEnabled;
	}

	List<String> validateForAlgorithm(int algorithm)
	{
		ArrayList<String> errors = new ArrayList<String>();
		if (!customEnabled)
		{
			return errors;
		}
		switch (algorithm)
		{
			case RecommendationModeSupport.COS_SIM:
			case RecommendationModeSupport.COMMON_NEIGHBORS:
			case RecommendationModeSupport.TEMPORAL_GRAPH_FOUNDATION:
				validateInt(errors, "Similarity candidate multiplier",
						similarityLocalTopMultiplier, 1, 100);
				validateInt(errors, "Similarity minimum local candidates",
						similarityLocalTopMinimum, 1, 10000);
				break;
			case RecommendationModeSupport.K_MEANS:
			case RecommendationModeSupport.K_MEANS_EUCLIDEAN:
				validateInt(errors, "K-Means clusters", kMeansClusters, 2, 200);
				validateInt(errors, "K-Means max iterations",
						kMeansMaxIterations, 1, 100);
				break;
			case RecommendationModeSupport.SVM:
				validateDouble(errors, "SVM complexity C", svmC, 0.001, 100.0);
				break;
			case RecommendationModeSupport.SCALABLE_SVM:
				validateInt(errors, "Scalable SVM epochs",
						scalableSvmEpochs, 1, 200);
				validateDouble(errors, "Scalable SVM aggressiveness",
						scalableSvmAggressiveness, 0.0001, 10.0);
				break;
			case RecommendationModeSupport.MLP:
				if (!isValidMlpEngine(mlpEngine))
				{
					errors.add("MLP engine must be Legacy Neuroph or Sparse Federated.");
				}
				validateInt(errors, "MLP hidden neurons",
						mlpHiddenNeurons, 1, 512);
				validateInt(errors, "MLP hidden layers",
						mlpHiddenLayers, 1, 8);
				validateDouble(errors, "MLP learning rate",
						mlpLearningRate, 0.00001, 1.0);
				validateDouble(errors, "MLP max error",
						mlpMaxError, 0.000001, 0.5);
				validateInt(errors, "Sparse MLP epochs",
						mlpSparseEpochs, 1, 200);
				validateDouble(errors, "Sparse MLP L2",
						mlpSparseL2, 0.0, 1.0);
				validateDouble(errors, "FedProx mu",
						mlpFedProxMu, 0.0, 10.0);
				break;
			case RecommendationModeSupport.DOC2VEC:
				validateBinary(errors, "Doc2Vec dm", doc2VecDm);
				validateBinary(errors, "Doc2Vec dbow_words", doc2VecDbowWords);
				validateInt(errors, "Doc2Vec min_count",
						doc2VecMinCount, 1, 100);
				validateInt(errors, "Doc2Vec negative",
						doc2VecNegative, 1, 50);
				validateBinary(errors, "Doc2Vec hs", doc2VecHs);
				validateDouble(errors, "Doc2Vec sample",
						doc2VecSample, 0.0, 0.1);
				validateInt(errors, "Doc2Vec window", doc2VecWindow, 1, 50);
				validateInt(errors, "Doc2Vec vector size",
						doc2VecVectorSize, 20, 1000);
				validateInt(errors, "Doc2Vec epochs", doc2VecEpochs, 1, 200);
				break;
			default:
				break;
		}
		return errors;
	}

	private static void validateBinary(List<String> errors, String label, int value)
	{
		if (value != 0 && value != 1)
		{
			errors.add(label + " must be 0 or 1.");
		}
	}

	private static void validateInt(
			List<String> errors,
			String label,
			int value,
			int minimum,
			int maximum)
	{
		if (value < minimum || value > maximum)
		{
			errors.add(label + " must be between " + minimum
					+ " and " + maximum + ".");
		}
	}

	private static void validateDouble(
			List<String> errors,
			String label,
			double value,
			double minimum,
			double maximum)
	{
		if (!Double.isFinite(value) || value < minimum || value > maximum)
		{
			errors.add(label + " must be between " + minimum
					+ " and " + maximum + ".");
		}
	}

	static boolean isValidMlpEngine(String value)
	{
		if (value == null)
		{
			return false;
		}
		String trimmed = value.trim();
		return MLP_ENGINE_LEGACY_NEUROPH.equals(trimmed)
				|| MLP_ENGINE_SPARSE_FEDERATED.equals(trimmed);
	}

	String profileLabel()
	{
		return customEnabled ? "Custom" : "Default";
	}

	String summaryForAlgorithm(int algorithm)
	{
		AlgorithmParameterSettings effective = effective(this);
		if (!customEnabled)
		{
			return "Default profile: current DSMP behavior is preserved.";
		}
		switch (algorithm)
		{
			case RecommendationModeSupport.COS_SIM:
				return "Similarity buffer "
						+ effective.getSimilarityLocalTopMultiplier() + "x, min "
						+ effective.getSimilarityLocalTopMinimum();
			case RecommendationModeSupport.COMMON_NEIGHBORS:
				return "Common-neighbor buffer "
						+ effective.getSimilarityLocalTopMultiplier() + "x, min "
						+ effective.getSimilarityLocalTopMinimum();
			case RecommendationModeSupport.TEMPORAL_GRAPH_FOUNDATION:
				return "Fixed TGF profile, local buffer "
						+ effective.getSimilarityLocalTopMultiplier() + "x, min "
						+ effective.getSimilarityLocalTopMinimum();
			case RecommendationModeSupport.K_MEANS:
			case RecommendationModeSupport.K_MEANS_EUCLIDEAN:
				return effective.getKMeansClusters() + " clusters, "
						+ effective.getKMeansMaxIterations() + " max iterations";
			case RecommendationModeSupport.SVM:
				return "C=" + formatDouble(effective.getSvmC())
						+ ", calibration "
						+ (effective.isSvmCalibrationEnabled() ? "on" : "off");
			case RecommendationModeSupport.SCALABLE_SVM:
				return effective.getScalableSvmEpochs() + " epochs, aggressiveness "
						+ formatDouble(effective.getScalableSvmAggressiveness());
			case RecommendationModeSupport.MLP:
				return effective.getMlpEngineLabel() + ", "
						+ effective.getMlpHiddenLayers() + " hidden layer"
						+ (effective.getMlpHiddenLayers() == 1 ? "" : "s")
						+ ", " + effective.getMlpHiddenNeurons()
						+ " neurons/layer, rate "
						+ formatDouble(effective.getMlpLearningRate())
						+ ", max error " + formatDouble(effective.getMlpMaxError());
			case RecommendationModeSupport.DOC2VEC:
				return effective.getDoc2VecVectorSize() + " dimensions, "
						+ effective.getDoc2VecEpochs() + " epochs, window "
						+ effective.getDoc2VecWindow();
			default:
				return "No active tunable parameters for this algorithm.";
		}
	}

	static String formatDouble(double value)
	{
		if (Math.abs(value) >= 1000.0 || Math.abs(value) < 0.001 && value != 0.0)
		{
			return String.format(java.util.Locale.US, "%.4g", value);
		}
		return String.format(java.util.Locale.US, "%.6f", value)
				.replaceAll("0+$", "")
				.replaceAll("\\.$", "");
	}

	void addJavaSystemProperties(List<String> command)
	{
		AlgorithmParameterSettings effective = effective(this);
		command.add("-Ddsmp.algorithm.custom="
				+ Boolean.toString(customEnabled));
		command.add("-Ddsmp.similarity.localTopMultiplier="
				+ effective.getSimilarityLocalTopMultiplier());
		command.add("-Ddsmp.similarity.localTopMinimum="
				+ effective.getSimilarityLocalTopMinimum());
		command.add("-Ddsmp.kmeans.clusters=" + effective.getKMeansClusters());
		command.add("-Ddsmp.kmeans.maxIterations="
				+ effective.getKMeansMaxIterations());
		command.add("-Ddsmp.svm.c=" + effective.getSvmC());
		command.add("-Ddsmp.svm.calibration="
				+ effective.isSvmCalibrationEnabled());
		command.add("-Ddsmp.scalableSvm.epochs="
				+ effective.getScalableSvmEpochs());
		command.add("-Ddsmp.scalableSvm.aggressiveness="
				+ effective.getScalableSvmAggressiveness());
		command.add("-Drecommender.scalableSvm.epochs="
				+ effective.getScalableSvmEpochs());
		command.add("-Drecommender.scalableSvm.aggressiveness="
				+ effective.getScalableSvmAggressiveness());
		command.add("-Ddsmp.mlp.engine=" + effective.getMlpEngine());
		command.add("-Ddsmp.mlp.hiddenNeurons="
				+ effective.getMlpHiddenNeurons());
		command.add("-Ddsmp.mlp.hiddenLayers="
				+ effective.getMlpHiddenLayers());
		command.add("-Ddsmp.mlp.learningRate="
				+ effective.getMlpLearningRate());
		command.add("-Ddsmp.mlp.maxError=" + effective.getMlpMaxError());
		command.add("-Ddsmp.mlp.sparseEpochs="
				+ effective.getMlpSparseEpochs());
		command.add("-Ddsmp.mlp.sparseL2=" + effective.getMlpSparseL2());
		command.add("-Ddsmp.mlp.fedProxMu=" + effective.getMlpFedProxMu());
		addDoc2VecJavaSystemProperties(command, effective);
	}

	private static void addDoc2VecJavaSystemProperties(
			List<String> command,
			AlgorithmParameterSettings effective)
	{
		command.add("-Ddsmp.doc2vec.dm=" + effective.getDoc2VecDm());
		command.add("-Ddsmp.doc2vec.dbowWords="
				+ effective.getDoc2VecDbowWords());
		command.add("-Ddsmp.doc2vec.minCount="
				+ effective.getDoc2VecMinCount());
		command.add("-Ddsmp.doc2vec.negative="
				+ effective.getDoc2VecNegative());
		command.add("-Ddsmp.doc2vec.hs=" + effective.getDoc2VecHs());
		command.add("-Ddsmp.doc2vec.sample=" + effective.getDoc2VecSample());
		command.add("-Ddsmp.doc2vec.window=" + effective.getDoc2VecWindow());
		command.add("-Ddsmp.doc2vec.vectorSize="
				+ effective.getDoc2VecVectorSize());
		command.add("-Ddsmp.doc2vec.epochs=" + effective.getDoc2VecEpochs());
	}

	List<String> toDoc2VecCommandArguments()
	{
		AlgorithmParameterSettings effective = effective(this);
		ArrayList<String> args = new ArrayList<String>();
		args.add(Integer.toString(effective.getDoc2VecDm()));
		args.add(Integer.toString(effective.getDoc2VecDbowWords()));
		args.add(Integer.toString(effective.getDoc2VecMinCount()));
		args.add(Integer.toString(effective.getDoc2VecNegative()));
		args.add(Integer.toString(effective.getDoc2VecHs()));
		args.add(Double.toString(effective.getDoc2VecSample()));
		args.add(Integer.toString(effective.getDoc2VecWindow()));
		args.add(Integer.toString(effective.getDoc2VecVectorSize()));
		args.add(Integer.toString(effective.getDoc2VecEpochs()));
		return args;
	}

	Map<String,String> doc2VecParameterMap()
	{
		AlgorithmParameterSettings effective = effective(this);
		LinkedHashMap<String,String> values = new LinkedHashMap<String,String>();
		values.put("dm", Integer.toString(effective.getDoc2VecDm()));
		values.put("dbow_words", Integer.toString(effective.getDoc2VecDbowWords()));
		values.put("min_count", Integer.toString(effective.getDoc2VecMinCount()));
		values.put("negative", Integer.toString(effective.getDoc2VecNegative()));
		values.put("hs", Integer.toString(effective.getDoc2VecHs()));
		values.put("sample", Double.toString(effective.getDoc2VecSample()));
		values.put("window", Integer.toString(effective.getDoc2VecWindow()));
		values.put("vector_size", Integer.toString(effective.getDoc2VecVectorSize()));
		values.put("epochs", Integer.toString(effective.getDoc2VecEpochs()));
		return values;
	}

	int getSimilarityLocalTopMultiplier()
	{
		return customEnabled ? similarityLocalTopMultiplier
				: DEFAULT_SIMILARITY_LOCAL_TOP_MULTIPLIER;
	}

	int getSimilarityLocalTopMinimum()
	{
		return customEnabled ? similarityLocalTopMinimum
				: DEFAULT_SIMILARITY_LOCAL_TOP_MINIMUM;
	}

	int getKMeansClusters()
	{
		return customEnabled ? kMeansClusters : DEFAULT_KMEANS_CLUSTERS;
	}

	int getKMeansMaxIterations()
	{
		return customEnabled ? kMeansMaxIterations
				: DEFAULT_KMEANS_MAX_ITERATIONS;
	}

	double getSvmC()
	{
		return customEnabled ? svmC : DEFAULT_SVM_C;
	}

	boolean isSvmCalibrationEnabled()
	{
		return customEnabled ? svmCalibration : DEFAULT_SVM_CALIBRATION;
	}

	int getScalableSvmEpochs()
	{
		return customEnabled ? scalableSvmEpochs
				: DEFAULT_SCALABLE_SVM_EPOCHS;
	}

	double getScalableSvmAggressiveness()
	{
		return customEnabled ? scalableSvmAggressiveness
				: DEFAULT_SCALABLE_SVM_AGGRESSIVENESS;
	}

	String getMlpEngine()
	{
		return customEnabled ? mlpEngine : DEFAULT_MLP_ENGINE;
	}

	String getMlpEngineLabel()
	{
		return MLP_ENGINE_SPARSE_FEDERATED.equals(getMlpEngine())
				? "Sparse federated MLP" : "Legacy Neuroph MLP";
	}

	boolean isSparseFederatedMlpEnabled()
	{
		return MLP_ENGINE_SPARSE_FEDERATED.equals(getMlpEngine());
	}

	int getMlpHiddenNeurons()
	{
		return customEnabled ? mlpHiddenNeurons : DEFAULT_MLP_HIDDEN_NEURONS;
	}

	int getMlpHiddenLayers()
	{
		return customEnabled ? mlpHiddenLayers : DEFAULT_MLP_HIDDEN_LAYERS;
	}

	double getMlpLearningRate()
	{
		return customEnabled ? mlpLearningRate : DEFAULT_MLP_LEARNING_RATE;
	}

	double getMlpMaxError()
	{
		return customEnabled ? mlpMaxError : DEFAULT_MLP_MAX_ERROR;
	}

	int getMlpSparseEpochs()
	{
		return customEnabled ? mlpSparseEpochs : DEFAULT_MLP_SPARSE_EPOCHS;
	}

	double getMlpSparseL2()
	{
		return customEnabled ? mlpSparseL2 : DEFAULT_MLP_SPARSE_L2;
	}

	double getMlpFedProxMu()
	{
		return customEnabled ? mlpFedProxMu : DEFAULT_MLP_FEDPROX_MU;
	}

	int getDoc2VecDm()
	{
		return customEnabled ? doc2VecDm : DEFAULT_DOC2VEC_DM;
	}

	int getDoc2VecDbowWords()
	{
		return customEnabled ? doc2VecDbowWords : DEFAULT_DOC2VEC_DBOW_WORDS;
	}

	int getDoc2VecMinCount()
	{
		return customEnabled ? doc2VecMinCount : DEFAULT_DOC2VEC_MIN_COUNT;
	}

	int getDoc2VecNegative()
	{
		return customEnabled ? doc2VecNegative : DEFAULT_DOC2VEC_NEGATIVE;
	}

	int getDoc2VecHs()
	{
		return customEnabled ? doc2VecHs : DEFAULT_DOC2VEC_HS;
	}

	double getDoc2VecSample()
	{
		return customEnabled ? doc2VecSample : DEFAULT_DOC2VEC_SAMPLE;
	}

	int getDoc2VecWindow()
	{
		return customEnabled ? doc2VecWindow : DEFAULT_DOC2VEC_WINDOW;
	}

	int getDoc2VecVectorSize()
	{
		return customEnabled ? doc2VecVectorSize : DEFAULT_DOC2VEC_VECTOR_SIZE;
	}

	int getDoc2VecEpochs()
	{
		return customEnabled ? doc2VecEpochs : DEFAULT_DOC2VEC_EPOCHS;
	}

	void setSimilarityLocalTopMultiplier(int value)
	{
		similarityLocalTopMultiplier = value;
	}

	void setSimilarityLocalTopMinimum(int value)
	{
		similarityLocalTopMinimum = value;
	}

	void setKMeansClusters(int value)
	{
		kMeansClusters = value;
	}

	void setKMeansMaxIterations(int value)
	{
		kMeansMaxIterations = value;
	}

	void setSvmC(double value)
	{
		svmC = value;
	}

	void setSvmCalibration(boolean value)
	{
		svmCalibration = value;
	}

	void setScalableSvmEpochs(int value)
	{
		scalableSvmEpochs = value;
	}

	void setScalableSvmAggressiveness(double value)
	{
		scalableSvmAggressiveness = value;
	}

	void setMlpEngine(String value)
	{
		mlpEngine = isValidMlpEngine(value) ? value.trim() : DEFAULT_MLP_ENGINE;
	}

	void setMlpHiddenNeurons(int value)
	{
		mlpHiddenNeurons = value;
	}

	void setMlpHiddenLayers(int value)
	{
		mlpHiddenLayers = value;
	}

	void setMlpLearningRate(double value)
	{
		mlpLearningRate = value;
	}

	void setMlpMaxError(double value)
	{
		mlpMaxError = value;
	}

	void setMlpSparseEpochs(int value)
	{
		mlpSparseEpochs = value;
	}

	void setMlpSparseL2(double value)
	{
		mlpSparseL2 = value;
	}

	void setMlpFedProxMu(double value)
	{
		mlpFedProxMu = value;
	}

	void setDoc2VecDm(int value)
	{
		doc2VecDm = value;
	}

	void setDoc2VecDbowWords(int value)
	{
		doc2VecDbowWords = value;
	}

	void setDoc2VecMinCount(int value)
	{
		doc2VecMinCount = value;
	}

	void setDoc2VecNegative(int value)
	{
		doc2VecNegative = value;
	}

	void setDoc2VecHs(int value)
	{
		doc2VecHs = value;
	}

	void setDoc2VecSample(double value)
	{
		doc2VecSample = value;
	}

	void setDoc2VecWindow(int value)
	{
		doc2VecWindow = value;
	}

	void setDoc2VecVectorSize(int value)
	{
		doc2VecVectorSize = value;
	}

	void setDoc2VecEpochs(int value)
	{
		doc2VecEpochs = value;
	}

	public boolean equals(Object object)
	{
		if (this == object)
		{
			return true;
		}
		if (!(object instanceof AlgorithmParameterSettings))
		{
			return false;
		}
		AlgorithmParameterSettings other = (AlgorithmParameterSettings)object;
		return customEnabled == other.customEnabled
				&& similarityLocalTopMultiplier == other.similarityLocalTopMultiplier
				&& similarityLocalTopMinimum == other.similarityLocalTopMinimum
				&& kMeansClusters == other.kMeansClusters
				&& kMeansMaxIterations == other.kMeansMaxIterations
				&& Double.compare(svmC, other.svmC) == 0
				&& svmCalibration == other.svmCalibration
				&& scalableSvmEpochs == other.scalableSvmEpochs
				&& Double.compare(
						scalableSvmAggressiveness,
						other.scalableSvmAggressiveness) == 0
				&& mlpEngine.equals(other.mlpEngine)
				&& mlpHiddenNeurons == other.mlpHiddenNeurons
				&& mlpHiddenLayers == other.mlpHiddenLayers
				&& Double.compare(mlpLearningRate, other.mlpLearningRate) == 0
				&& Double.compare(mlpMaxError, other.mlpMaxError) == 0
				&& mlpSparseEpochs == other.mlpSparseEpochs
				&& Double.compare(mlpSparseL2, other.mlpSparseL2) == 0
				&& Double.compare(mlpFedProxMu, other.mlpFedProxMu) == 0
				&& doc2VecDm == other.doc2VecDm
				&& doc2VecDbowWords == other.doc2VecDbowWords
				&& doc2VecMinCount == other.doc2VecMinCount
				&& doc2VecNegative == other.doc2VecNegative
				&& doc2VecHs == other.doc2VecHs
				&& Double.compare(doc2VecSample, other.doc2VecSample) == 0
				&& doc2VecWindow == other.doc2VecWindow
				&& doc2VecVectorSize == other.doc2VecVectorSize
				&& doc2VecEpochs == other.doc2VecEpochs;
	}

	public int hashCode()
	{
		int result = customEnabled ? 1 : 0;
		result = 31 * result + similarityLocalTopMultiplier;
		result = 31 * result + similarityLocalTopMinimum;
		result = 31 * result + kMeansClusters;
		result = 31 * result + kMeansMaxIterations;
		long bits = Double.doubleToLongBits(svmC);
		result = 31 * result + (int)(bits ^ (bits >>> 32));
		result = 31 * result + (svmCalibration ? 1 : 0);
		result = 31 * result + scalableSvmEpochs;
		bits = Double.doubleToLongBits(scalableSvmAggressiveness);
		result = 31 * result + (int)(bits ^ (bits >>> 32));
		result = 31 * result + mlpEngine.hashCode();
		result = 31 * result + mlpHiddenNeurons;
		result = 31 * result + mlpHiddenLayers;
		bits = Double.doubleToLongBits(mlpLearningRate);
		result = 31 * result + (int)(bits ^ (bits >>> 32));
		bits = Double.doubleToLongBits(mlpMaxError);
		result = 31 * result + (int)(bits ^ (bits >>> 32));
		result = 31 * result + mlpSparseEpochs;
		bits = Double.doubleToLongBits(mlpSparseL2);
		result = 31 * result + (int)(bits ^ (bits >>> 32));
		bits = Double.doubleToLongBits(mlpFedProxMu);
		result = 31 * result + (int)(bits ^ (bits >>> 32));
		result = 31 * result + doc2VecDm;
		result = 31 * result + doc2VecDbowWords;
		result = 31 * result + doc2VecMinCount;
		result = 31 * result + doc2VecNegative;
		result = 31 * result + doc2VecHs;
		bits = Double.doubleToLongBits(doc2VecSample);
		result = 31 * result + (int)(bits ^ (bits >>> 32));
		result = 31 * result + doc2VecWindow;
		result = 31 * result + doc2VecVectorSize;
		result = 31 * result + doc2VecEpochs;
		return result;
	}
}
