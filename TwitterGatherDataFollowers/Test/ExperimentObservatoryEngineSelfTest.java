package TwitterGatherDataFollowers.userRyersonU;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public final class ExperimentObservatoryEngineSelfTest
{
	private ExperimentObservatoryEngineSelfTest()
	{
	}

	public static void main(String[] args) throws Exception
	{
		buildsMatrixScansArtifactsAndWritesCapsule();
		System.out.println("ExperimentObservatoryEngineSelfTest passed");
	}

	private static void buildsMatrixScansArtifactsAndWritesCapsule() throws Exception
	{
		Path project = Files.createTempDirectory("dsmp-observatory");
		File dataset = write(project, "sample-dsmp.txt",
				"StoreA\t1\t2026-01-01\t101\tAlice\tblue red graph text\n"
						+ "StoreB\t2\t2026-01-02\t102\tBob\tgreen graph score text\n");
		write(project, "resultsTiming.txt",
				"[StageTimer] FEATURE build total: 512 ms\n"
						+ "[StageTimer] SIMULATION total before graph visualization: 1448 ms\n"
						+ "[StageTimer] GRAPH visualization total: 220 ms\n");
		write(project, "MessagePassingTimes.txt", "messages=12\n");
		write(project, "recommendations_lists.txt",
				"================ Recommendations for Alice ================\n"
						+ "Recommendation 1: Bob\n"
						+ "Recommendation 2: Carol\n");
		Path runGraph = Files.createDirectories(project.resolve("Results").resolve("Runs")
				.resolve("20260101-000000_SVM_1n_dsmp_default_abc").resolve("graph"));
		write(runGraph, "graph.json", "{}\n");
		write(runGraph, "graph.gexf", "<gexf/>\n");
		Path recommendationDir =
				Files.createDirectories(project.resolve("Results").resolve("Recommendations").resolve("Alice"));
		write(recommendationDir, "Recommendations_Alice.txt",
				"Recommendation 1: Bob\nRecommendation 2: Carol\n");
		Path reportDir = Files.createDirectories(project.resolve("reports"));
		write(reportDir, "sample-report.txt", "report\n");

		ExperimentObservatoryEngine.ExperimentDesign design =
				new ExperimentObservatoryEngine.ExperimentDesign(
						"sample-dsmp.txt",
						dataset.getAbsolutePath(),
						Arrays.asList(
								Integer.valueOf(RecommendationModeSupport.SVM),
								Integer.valueOf(RecommendationModeSupport.DOC2VEC)),
						Arrays.asList(Integer.valueOf(1), Integer.valueOf(4)),
						Arrays.asList(Integer.valueOf(1), Integer.valueOf(5)),
						Arrays.asList("DSMP default", "Capacity-weighted"),
						Arrays.asList(Boolean.FALSE, Boolean.TRUE),
						Arrays.asList(Boolean.FALSE, Boolean.TRUE),
						2,
						3,
						4096,
						"Algorithm Default",
						"Default",
						"default algorithm parameters",
						"Balanced capacity contract");

		require(ExperimentObservatoryEngine.buildMatrix(design).size() == 128,
				"matrix size should include all selected factors and repetitions");

		ExperimentObservatoryEngine.ObservatoryReport report =
				ExperimentObservatoryEngine.scanProject(project.toFile(), design);

		require(report.datasetHash.length() == 64, "dataset SHA-256 should be captured");
		require(report.datasetBytes > 0L, "dataset size should be captured");
		require(report.timingSummary.stageLineCount == 3, "stage timing lines not counted");
		require(report.timingSummary.latestTotalBeforeGraphMs == 1448L,
				"latest total before graph was not parsed");
		require(report.inventory.runGraphs == 1, "run graphs not detected");
		require(report.inventory.runGraphFigures == 1, "run graph figures not detected");
		require(report.inventory.reportFiles == 1, "report artifacts not detected");
		require(report.inventory.recommendationArtifacts == 1,
				"recommendation artifacts not detected");
		require(report.inventory.hasMessagePassing, "message-passing log not detected");
		require(report.recommendationSummary.detectedTargets == 1,
				"recommendation target count not detected");
		require(report.recommendationSummary.recommendationLines == 4,
				"recommendation lines not counted");
		require(report.comparison.reproducibilityScore >= 80,
				"reproducibility score should reflect complete capsule inputs");

		String json = ExperimentObservatoryEngine.toJson(report);
		require(json.contains("\"plannedRuns\": 128"), "JSON should include planned run count");
		require(json.contains("\"datasetHash\""), "JSON should include dataset hash");

		File capsule = ExperimentObservatoryEngine.writeCapsule(
				project.resolve("capsules").toFile(), report);
		require(capsule.isFile(), "capsule JSON was not written");
		File markdown = new File(capsule.getParentFile(),
				capsule.getName().replace(".json", ".md"));
		require(markdown.isFile(), "capsule Markdown was not written");
		require(new String(Files.readAllBytes(markdown.toPath()), StandardCharsets.UTF_8)
						.contains("DSMP Experiment Observatory Report"),
				"capsule Markdown should be a readable observatory report");
	}

	private static File write(Path directory, String fileName, String text) throws Exception
	{
		Files.createDirectories(directory);
		Path file = directory.resolve(fileName);
		Files.write(file, text.getBytes(StandardCharsets.UTF_8));
		return file.toFile();
	}

	private static void require(boolean condition, String message)
	{
		if (!condition)
		{
			throw new AssertionError(message);
		}
	}
}
