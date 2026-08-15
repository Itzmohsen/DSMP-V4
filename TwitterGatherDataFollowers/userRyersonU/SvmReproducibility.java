package TwitterGatherDataFollowers.userRyersonU;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;

import weka.classifiers.functions.SMO;

final class SvmReproducibility {
	static final long SPLIT_SEED = 1L;
	static final int MODEL_SEED = 1;

	private SvmReproducibility() {
	}

	static Split stratifiedSplit(Map<String,? extends List<String>> usersByClass, double testSetPercent) {
		if (testSetPercent < 0.0 || testSetPercent >= 1.0) {
			throw new IllegalArgumentException("testSetPercent must be at least 0.0 and less than 1.0");
		}

		ArrayList<String> trainUsers = new ArrayList<String>();
		ArrayList<String> testUsers = new ArrayList<String>();
		if (usersByClass == null || usersByClass.isEmpty()) {
			return new Split(trainUsers, testUsers);
		}

		for (String className : sortedNonNullValues(usersByClass.keySet())) {
			List<String> sourceUsers = usersByClass.get(className);
			if (sourceUsers == null || sourceUsers.isEmpty()) {
				continue;
			}

			ArrayList<String> classUsers = sortedNonNullValues(sourceUsers);
			Collections.shuffle(classUsers, new Random(seedForClass(className)));
			int classTestUsers = getStratifiedClassTestCount(classUsers.size(), testSetPercent);
			int classTrainUsers = classUsers.size() - classTestUsers;
			trainUsers.addAll(classUsers.subList(0, classTrainUsers));
			testUsers.addAll(classUsers.subList(classTrainUsers, classUsers.size()));
		}

		if (trainUsers.isEmpty() && testUsers.size() > 1) {
			trainUsers.add(testUsers.remove(testUsers.size() - 1));
		}
		return new Split(trainUsers, testUsers);
	}

	static ArrayList<String> sortedNonNullValues(Collection<String> values) {
		ArrayList<String> sortedValues = new ArrayList<String>();
		if (values != null) {
			for (String value : values) {
				if (value != null) {
					sortedValues.add(value);
				}
			}
		}
		Collections.sort(sortedValues);
		return sortedValues;
	}

	static void applyModelSeed(SMO svmModel) {
		if (svmModel == null) {
			throw new IllegalArgumentException("svmModel must not be null");
		}
		svmModel.setRandomSeed(MODEL_SEED);
	}

	private static int getStratifiedClassTestCount(int classSize, double testSetPercent) {
		if (classSize <= 1) {
			return 0;
		}
		int classTestUsers = (int)Math.floor(classSize * testSetPercent);
		if (classTestUsers < 1) {
			classTestUsers = 1;
		}
		if (classTestUsers >= classSize) {
			classTestUsers = classSize - 1;
		}
		return classTestUsers;
	}

	private static long seedForClass(String className) {
		return SPLIT_SEED ^ className.hashCode();
	}

	static final class Split {
		private final ArrayList<String> trainUsers;
		private final ArrayList<String> testUsers;

		private Split(ArrayList<String> trainUsers, ArrayList<String> testUsers) {
			this.trainUsers = trainUsers;
			this.testUsers = testUsers;
		}

		ArrayList<String> getTrainUsers() {
			return new ArrayList<String>(trainUsers);
		}

		ArrayList<String> getTestUsers() {
			return new ArrayList<String>(testUsers);
		}
	}
}
