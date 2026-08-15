package TwitterGatherDataFollowers.userRyersonU;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

final class ExperimentResetFiles {
	private ExperimentResetFiles() {
	}

	static List<File> resetExperimentOutputs(File projectDirectory, File selectedDataset) {
		List<File> failedDeletes = resetDatasetOutputs(projectDirectory, selectedDataset);
		File importantStuffDirectory = new File(projectDirectory, "important-stuff");

		deleteIfExists(new File(importantStuffDirectory, "graph.gexf"), failedDeletes);
		deleteIfExists(new File(importantStuffDirectory, "layout.jpg"), failedDeletes);
		deleteIfExists(new File(importantStuffDirectory, "autolayout.pdf"), failedDeletes);
		deleteIfExists(new File(importantStuffDirectory, "partition.pdf"), failedDeletes);
		deleteIfExists(new File(importantStuffDirectory, "test-file-name.txt"), failedDeletes);
		deleteIfExists(new File(importantStuffDirectory, "outPutSepFolloweeResults.txt"), failedDeletes);
		deleteIfExists(new File(importantStuffDirectory, "outPutSepFollowee.txt"), failedDeletes);
		deleteIfExists(new File(importantStuffDirectory, "outPutSep.txt"), failedDeletes);
		deleteIfExists(new File(importantStuffDirectory, "outPutSepResult.txt"), failedDeletes);
		deleteIfExists(new File(importantStuffDirectory, "outputChoice.txt"), failedDeletes);
		deleteIfExists(new File(projectDirectory, "recommendations_lists.txt"), failedDeletes);
		deleteIfExists(new File(projectDirectory, "recommendations_lists_similar-users.txt"), failedDeletes);
		deleteIfExists(new File(projectDirectory, "recommendations_lists_followee-accounts.txt"), failedDeletes);
		deleteIfExists(new File(projectDirectory, "tweetCounts.txt"), failedDeletes);

		return failedDeletes;
	}

	static List<File> resetDatasetOutputs(File projectDirectory, File selectedDataset) {
		List<File> failedDeletes = new ArrayList<File>();
		if (selectedDataset == null) {
			return failedDeletes;
		}

		File importantStuffDirectory = new File(projectDirectory, "important-stuff");
		String graphEdgesBaseName = getDatasetGraphBaseName(selectedDataset.getName());
		deleteIfExists(
				new File(
						importantStuffDirectory,
						"Dataset-Test-" + selectedDataset.getName()),
				failedDeletes);
		deleteIfExists(new File(importantStuffDirectory, graphEdgesBaseName + ".txt"), failedDeletes);
		deleteIfExists(new File(importantStuffDirectory, graphEdgesBaseName + ".gml"), failedDeletes);
		deleteIfExists(new File(importantStuffDirectory, graphEdgesBaseName + "_similar-users.txt"), failedDeletes);
		deleteIfExists(new File(importantStuffDirectory, graphEdgesBaseName + "_similar-users.gml"), failedDeletes);
		deleteIfExists(new File(importantStuffDirectory, graphEdgesBaseName + "_followee-accounts.txt"), failedDeletes);
		deleteIfExists(new File(importantStuffDirectory, graphEdgesBaseName + "_followee-accounts.gml"), failedDeletes);
		return failedDeletes;
	}

	private static void deleteIfExists(File file, List<File> failedDeletes) {
		if (file.exists() && !file.delete()) {
			failedDeletes.add(file);
		}
	}

	private static String getDatasetGraphBaseName(String selectedFileName) {
		String datasetName = selectedFileName;
		int extensionIndex = datasetName.lastIndexOf('.');
		if (extensionIndex > 0) {
			datasetName = datasetName.substring(0, extensionIndex);
		}
		datasetName = datasetName.replaceAll("[^A-Za-z0-9._-]", "_");
		if (datasetName.length() == 0) {
			datasetName = "uploaded-dataset";
		}
		return "edges-numbers-" + datasetName;
	}
}
