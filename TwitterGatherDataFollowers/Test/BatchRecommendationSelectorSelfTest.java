package TwitterGatherDataFollowers.userRyersonU;

import java.io.File;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Random;

public final class BatchRecommendationSelectorSelfTest {
	private BatchRecommendationSelectorSelfTest() {
	}

	public static void main(String[] args) throws Exception {
		preservesRecommendationNamesContainingSpaces();
		writesFullSelectedRecommendation();
		handlesInvalidWeightsWithoutHanging();
		everyAlgorithmCanChooseItsTopRecommendation();
		writesBoundedFolloweeHistory();
		System.out.println("BatchRecommendationSelectorSelfTest passed");
	}

	private static void preservesRecommendationNamesContainingSpaces() {
		int selectedIndex = BatchRecommendationSelector.selectIndex(
				Arrays.asList(0.0, 10.0, 0.0), new Random(1L));
		String selectedName = Arrays.asList("Sobeys", "T&T Supermarket", "Longos").get(selectedIndex);
		require("T&T Supermarket".equals(selectedName), "recommendation name was truncated");
	}

	private static void writesFullSelectedRecommendation() throws Exception {
		File outputFile = File.createTempFile("batch-recommendation-choice", ".txt");
		try {
			BatchRecommendationSelector.writeSelectedRecommendation(outputFile, "T&T Supermarket");
			String selectedName = new String(Files.readAllBytes(outputFile.toPath()), StandardCharsets.UTF_8);
			require("T&T Supermarket".equals(selectedName), "selected recommendation file was truncated");
		} finally {
			outputFile.delete();
		}
	}

	private static void handlesInvalidWeightsWithoutHanging() {
		int selectedIndex = BatchRecommendationSelector.selectIndex(
				Arrays.asList(Double.NaN, -1.0, null), new Random(1L));
		require(selectedIndex == 0, "invalid weights did not use the deterministic fallback");
	}

	private static void everyAlgorithmCanChooseItsTopRecommendation() {
		//Cosine-like scores (Doc2Vec's are in [-1, 1]): the choice frequencies
		//follow the score shares, and the top recommendation is reachable.
		int[] chosen = new int[3];
		Random random = new Random(1L);
		for (int i = 0; i < 30000; i++) {
			chosen[BatchRecommendationSelector.selectIndex(Arrays.asList(0.6, 0.3, 0.1), random)]++;
		}
		require(Math.abs(chosen[0] / 30000.0 - 0.6) < 0.02, "top choice share " + chosen[0] / 30000.0);
		require(Math.abs(chosen[1] / 30000.0 - 0.3) < 0.02, "second choice share " + chosen[1] / 30000.0);
		require(Math.abs(chosen[2] / 30000.0 - 0.1) < 0.02, "third choice share " + chosen[2] / 30000.0);
	}

	private static void writesBoundedFolloweeHistory() throws Exception {
		StringWriter writer = new StringWriter();
		BatchRecommendationSelector.writeFolloweeChoices(
				writer,
				Arrays.asList("RobertBlake"),
				"Original Store",
				Arrays.asList("Sobeys", "T&T Supermarket", "Longos"),
				1);
		String newline = System.lineSeparator();
		String expected = "RobertBlake Followes Original Store" + newline
				+ "RobertBlake Followes Sobeys" + newline
				+ "RobertBlake Followes T&T Supermarket" + newline;
		require(expected.equals(writer.toString()), "followee history was not written correctly");
	}

	private static void require(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
