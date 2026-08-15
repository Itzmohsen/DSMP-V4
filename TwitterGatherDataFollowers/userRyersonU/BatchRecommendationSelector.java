package TwitterGatherDataFollowers.userRyersonU;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.Collection;
import java.util.List;
import java.util.Random;

final class BatchRecommendationSelector {
	private static final int MAX_WEIGHTED_CHOICES = 3;
	private static final double DOC2VEC_FIRST_WEIGHT_ADJUSTMENT = 50.0;

	private BatchRecommendationSelector() {
	}

	static int selectIndex(List<Double> weights, boolean adjustDoc2VecFirstWeight, Random random) {
		if (weights == null || weights.isEmpty()) {
			return -1;
		}
		if (random == null) {
			throw new IllegalArgumentException("random must not be null");
		}

		int choiceCount = Math.min(MAX_WEIGHTED_CHOICES, weights.size());
		double[] usableWeights = new double[choiceCount];
		double totalWeight = 0.0;

		for (int i = 0; i < choiceCount; i++) {
			Double weightObject = weights.get(i);
			double weight = weightObject == null ? 0.0 : weightObject.doubleValue();
			if (i == 0 && adjustDoc2VecFirstWeight) {
				weight -= DOC2VEC_FIRST_WEIGHT_ADJUSTMENT;
			}
			if (!Double.isFinite(weight) || weight < 0.0) {
				weight = 0.0;
			}
			usableWeights[i] = weight;
			totalWeight += weight;
		}

		if (totalWeight <= 0.0) {
			return 0;
		}

		double selectedWeight = random.nextDouble() * totalWeight;
		double cumulativeWeight = 0.0;
		for (int i = 0; i < choiceCount; i++) {
			cumulativeWeight += usableWeights[i];
			if (selectedWeight < cumulativeWeight) {
				return i;
			}
		}
		return choiceCount - 1;
	}

	static void writeSelectedRecommendation(File outputFile, String selectedRecommendation) throws IOException {
		if (outputFile == null) {
			throw new IllegalArgumentException("outputFile must not be null");
		}
		File parent = outputFile.getParentFile();
		if (parent != null && !parent.exists()) {
			parent.mkdirs();
		}
		try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile, false))) {
			if (selectedRecommendation != null) {
				writer.write(selectedRecommendation);
			}
		}
	}

	static void writeFolloweeChoices(
			Writer writer,
			Collection<String> users,
			String originalFollowee,
			List<String> recommendations,
			int selectedRecommendationIndex) throws IOException {
		if (writer == null) {
			throw new IllegalArgumentException("writer must not be null");
		}
		if (users == null || users.isEmpty()) {
			return;
		}

		int finalRecommendationIndex = -1;
		if (recommendations != null && !recommendations.isEmpty() && selectedRecommendationIndex >= 0) {
			finalRecommendationIndex = Math.min(selectedRecommendationIndex, recommendations.size() - 1);
		}

		for (String user : users) {
			if (user == null || user.isEmpty()) {
				continue;
			}
			writeFolloweeLine(writer, user, originalFollowee);
			for (int i = 0; i <= finalRecommendationIndex; i++) {
				writeFolloweeLine(writer, user, recommendations.get(i));
			}
		}
	}

	private static void writeFolloweeLine(Writer writer, String user, String followee) throws IOException {
		if (followee == null || followee.isEmpty()) {
			return;
		}
		writer.write(user);
		writer.write(" Followes ");
		writer.write(followee);
		writer.write(System.lineSeparator());
	}
}
