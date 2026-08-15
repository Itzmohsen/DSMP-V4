package TwitterGatherDataFollowers.userRyersonU;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import weka.classifiers.functions.SMO;
import weka.core.Instances;

public final class SvmReproducibilitySelfTest {
	private SvmReproducibilitySelfTest() {
	}

	public static void main(String[] args) throws Exception {
		producesTheSameSplitRepeatedly();
		ignoresInputMapAndUserOrdering();
		preservesStratifiedClassCounts();
		keepsSingletonClassesInTraining();
		sortsClassLabelsDeterministically();
		appliesTheStableModelSeed();
		seededModelsProduceIdenticalScores();
		System.out.println("SvmReproducibilitySelfTest passed");
	}

	private static void producesTheSameSplitRepeatedly() {
		Map<String,List<String>> usersByClass = standardUsersByClass();
		SvmReproducibility.Split first = SvmReproducibility.stratifiedSplit(usersByClass, 0.30);
		SvmReproducibility.Split second = SvmReproducibility.stratifiedSplit(usersByClass, 0.30);
		require(first.getTrainUsers().equals(second.getTrainUsers()), "training split changed between calls");
		require(first.getTestUsers().equals(second.getTestUsers()), "test split changed between calls");
	}

	private static void ignoresInputMapAndUserOrdering() {
		Map<String,List<String>> firstInput = standardUsersByClass();
		Map<String,List<String>> reorderedInput = new LinkedHashMap<String,List<String>>();
		reorderedInput.put("T&T Supermarket", Arrays.asList("t4", "t3", "t2", "t1"));
		reorderedInput.put("Safeway", Arrays.asList("s5", "s4", "s3", "s2", "s1"));

		SvmReproducibility.Split first = SvmReproducibility.stratifiedSplit(firstInput, 0.30);
		SvmReproducibility.Split reordered = SvmReproducibility.stratifiedSplit(reorderedInput, 0.30);
		require(first.getTrainUsers().equals(reordered.getTrainUsers()), "training split depends on input ordering");
		require(first.getTestUsers().equals(reordered.getTestUsers()), "test split depends on input ordering");
	}

	private static void preservesStratifiedClassCounts() {
		SvmReproducibility.Split split = SvmReproducibility.stratifiedSplit(standardUsersByClass(), 0.30);
		require(split.getTrainUsers().size() == 7, "unexpected training set size");
		require(split.getTestUsers().size() == 2, "unexpected test set size");
	}

	private static void keepsSingletonClassesInTraining() {
		Map<String,List<String>> usersByClass = new LinkedHashMap<String,List<String>>();
		usersByClass.put("Only Class", Arrays.asList("only-user"));
		SvmReproducibility.Split split = SvmReproducibility.stratifiedSplit(usersByClass, 0.30);
		require(split.getTrainUsers().equals(Arrays.asList("only-user")),
				"singleton class was not kept in training");
		require(split.getTestUsers().isEmpty(), "singleton class was incorrectly placed in testing");
	}

	private static void sortsClassLabelsDeterministically() {
		ArrayList<String> sorted = SvmReproducibility.sortedNonNullValues(
				Arrays.asList("T&T Supermarket", "Safeway", null, "IGA"));
		require(sorted.equals(Arrays.asList("IGA", "Safeway", "T&T Supermarket")),
				"class labels were not sorted");
	}

	private static void appliesTheStableModelSeed() {
		SMO svmModel = new SMO();
		SvmReproducibility.applyModelSeed(svmModel);
		require(svmModel.getRandomSeed() == SvmReproducibility.MODEL_SEED,
				"stable SMO model seed was not applied");
	}

	private static void seededModelsProduceIdenticalScores() throws Exception {
		String arff = "@relation deterministic\n"
				+ "@attribute x numeric\n"
				+ "@attribute result {'A','B'}\n"
				+ "@data\n"
				+ "0.0,'A'\n"
				+ "0.1,'A'\n"
				+ "0.9,'B'\n"
				+ "1.0,'B'\n";
		Instances data = new Instances(new StringReader(arff));
		data.setClassIndex(data.numAttributes() - 1);

		SMO first = configuredModel();
		SMO second = configuredModel();
		first.buildClassifier(data);
		second.buildClassifier(data);

		double[] firstScores = first.distributionForInstance(data.instance(0));
		double[] secondScores = second.distributionForInstance(data.instance(0));
		require(Arrays.equals(firstScores, secondScores), "seeded SMO scores changed between models");
	}

	private static SMO configuredModel() {
		SMO svmModel = new SMO();
		svmModel.setC(0.1);
		svmModel.setBuildCalibrationModels(true);
		SvmReproducibility.applyModelSeed(svmModel);
		return svmModel;
	}

	private static Map<String,List<String>> standardUsersByClass() {
		Map<String,List<String>> usersByClass = new LinkedHashMap<String,List<String>>();
		usersByClass.put("Safeway", Arrays.asList("s1", "s2", "s3", "s4", "s5"));
		usersByClass.put("T&T Supermarket", Arrays.asList("t1", "t2", "t3", "t4"));
		return usersByClass;
	}

	private static void require(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
