package TwitterGatherDataFollowers.userRyersonU;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class ExperimentResetFilesSelfTest {
	private ExperimentResetFilesSelfTest() {
	}

	public static void main(String[] args) throws Exception {
		resetsOnlyTransientExperimentOutputs();
		cleansDatasetSelectedAfterReset();
		System.out.println("ExperimentResetFilesSelfTest passed");
	}

	private static void resetsOnlyTransientExperimentOutputs() throws Exception {
		Path project = Files.createTempDirectory("experiment-reset");
		Path importantStuff = Files.createDirectories(project.resolve("important-stuff"));
		File selectedDataset = write(project, "Retail 50K.txt");

		File selectedEdges = write(importantStuff, "edges-numbers-Retail_50K.txt");
		File selectedGraph = write(importantStuff, "edges-numbers-Retail_50K.gml");
		File selectedUserEdges = write(importantStuff, "edges-numbers-Retail_50K_similar-users.txt");
		File selectedFolloweeGraph = write(importantStuff, "edges-numbers-Retail_50K_followee-accounts.gml");
		File legacyDatasetCopy = write(importantStuff, "Dataset-Test-Retail 50K.txt");
		File sharedGraph = write(importantStuff, "graph.gexf");
		File recommendationChoices = write(importantStuff, "outPutSepFolloweeResults.txt");
		File recommendations = write(project, "recommendations_lists.txt");
		File userRecommendations = write(project, "recommendations_lists_similar-users.txt");
		File followeeRecommendations = write(project, "recommendations_lists_followee-accounts.txt");
		File tweetCounts = write(project, "tweetCounts.txt");

		File generatedMapping = write(importantStuff, "name-number-Retail 50K.txt");
		File unrelatedEdges = write(importantStuff, "edges-numbers-Other.txt");
		File archivedResult = write(Files.createDirectories(project.resolve("Results")), "history.txt");
		File doc2VecCache = write(Files.createDirectories(project.resolve("Stored_Doc2VecModel")), "model.bin");

		List<File> failedDeletes = ExperimentResetFiles.resetExperimentOutputs(project.toFile(), selectedDataset);

		require(failedDeletes.isEmpty(), "reset reported failed deletes");
		require(!selectedEdges.exists(), "selected dataset edge list was preserved");
		require(!selectedGraph.exists(), "selected dataset graph was preserved");
		require(!selectedUserEdges.exists(), "converted user edge list was preserved");
		require(!selectedFolloweeGraph.exists(), "converted followee graph was preserved");
		require(!legacyDatasetCopy.exists(), "obsolete rewritten dataset copy was preserved");
		require(!sharedGraph.exists(), "shared graph output was preserved");
		require(!recommendationChoices.exists(), "recommendation-side transient output was preserved");
		require(!recommendations.exists(), "recommendations list was preserved");
		require(!userRecommendations.exists(), "converted user recommendations were preserved");
		require(!followeeRecommendations.exists(), "converted followee recommendations were preserved");
		require(!tweetCounts.exists(), "tweet counts were preserved");

		require(selectedDataset.exists(), "selected dataset was deleted");
		require(generatedMapping.exists(), "generated name-number mapping was deleted");
		require(unrelatedEdges.exists(), "unrelated dataset edge list was deleted");
		require(archivedResult.exists(), "archived result was deleted");
		require(doc2VecCache.exists(), "Doc2Vec cache was deleted");
	}

	private static void cleansDatasetSelectedAfterReset() throws Exception {
		Path project = Files.createTempDirectory("experiment-reset-selection");
		Path importantStuff = Files.createDirectories(project.resolve("important-stuff"));
		File selectedDataset = write(project, "Next Dataset.txt");
		File selectedEdges = write(importantStuff, "edges-numbers-Next_Dataset.txt");
		File sharedOutput = write(importantStuff, "outPutSep.txt");

		List<File> failedDeletes = ExperimentResetFiles.resetDatasetOutputs(project.toFile(), selectedDataset);

		require(failedDeletes.isEmpty(), "dataset selection cleanup reported failed deletes");
		require(!selectedEdges.exists(), "newly selected dataset edge list was preserved");
		require(sharedOutput.exists(), "dataset selection cleanup deleted shared output");
	}

	private static File write(Path directory, String fileName) throws Exception {
		Path path = directory.resolve(fileName);
		Files.write(path, "test".getBytes(StandardCharsets.UTF_8));
		return path.toFile();
	}

	private static void require(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
