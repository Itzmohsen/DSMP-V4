package TwitterGatherDataFollowers.userRyersonU;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class GraphAnalysisEngineSelfTest
{
	private GraphAnalysisEngineSelfTest()
	{
	}

	public static void main(String[] args) throws Exception
	{
		analyzesCurrentTwoEdgeRecommendationFormat();
		flagsMalformedAndUnknownEdgesWithoutFailing();
		handlesMissingEdgeFileAsWaitingState();
		derivesModeSpecificEdgeFileNames();
		System.out.println("GraphAnalysisEngineSelfTest passed");
	}

	private static void analyzesCurrentTwoEdgeRecommendationFormat() throws Exception
	{
		Path directory = Files.createTempDirectory("graph-analysis");
		File mapping = write(directory, "name-number-sample.txt",
				"StoreA\t1\nAlice\t2\nStoreB\t3\nBob\t4\nStoreC\t5\nCarol\t6\n");
		File edges = write(directory, "edges-numbers-sample.txt",
				"2 1\n2 5\n4 3\n4 5\n6 3\n6 5\n");

		GraphAnalysisEngine.Analysis analysis =
				GraphAnalysisEngine.analyze(mapping, edges);

		require(analysis.available, "analysis should be available");
		require(analysis.mappedNodeCount == 6, "unexpected mapped count");
		require(analysis.activeNodeCount == 6, "unexpected active count");
		require(analysis.validEdgeCount == 6L, "unexpected edge count");
		require(analysis.recommendationEdgeCount == 3L, "unexpected recommendation edge count");
		require(analysis.uniqueRecommendedTargets == 1, "unexpected recommendation diversity");
		require(!analysis.topRecommendedTargets.isEmpty(), "missing top recommended target");
		require("StoreC".equals(analysis.topRecommendedTargets.get(0).name),
				"top recommended target was not StoreC");
		require(analysis.topRecommendedTargets.get(0).value == 3,
				"StoreC should have three recommendation edges");
		require(analysis.recommendationChangedTargetCount == 3L,
				"all recommendations should differ from original labels");
		require(analysis.activeComponentCount == 1,
				"this small graph should be one active component");
		require(!analysis.communityDetails.isEmpty(),
				"community explorer details were not produced");
		require(analysis.nodeDetails.size() == 6,
				"node inspector should include every mapped node");
		GraphAnalysisEngine.NodeDetail alice = findNode(analysis, "Alice");
		require(alice != null, "Alice node detail missing");
		require(alice.outDegree == 2, "Alice outgoing degree was not captured");
		require(alice.communityId != GraphAnalysisEngine.UNASSIGNED_COMMUNITY_ID,
				"active Alice node should have a community assignment");
		GraphAnalysisEngine.CommunityDetail firstCommunity =
				analysis.communityDetails.get(0);
		require(firstCommunity.size > 0,
				"community detail should include a size");
		require(firstCommunity.internalEdgeCount + firstCommunity.externalEdgeCount > 0L,
				"community edge counts were not computed");
		require(analysis.warnings.toString().contains("fewer than 10 valid edges"),
				"small graph warning was not emitted");
	}

	private static void flagsMalformedAndUnknownEdgesWithoutFailing() throws Exception
	{
		Path directory = Files.createTempDirectory("graph-analysis-invalid");
		File mapping = write(directory, "name-number-sample.txt",
				"Known\t1\nTarget\t2\n");
		File edges = write(directory, "edges-numbers-sample.txt",
				"1 2\nbad line\n1 99\n2 1\n");

		GraphAnalysisEngine.Analysis analysis =
				GraphAnalysisEngine.analyze(mapping, edges);

		require(analysis.validEdgeCount == 2L, "valid edges were not retained");
		require(analysis.malformedEdgeLines == 1L, "malformed edge was not counted");
		require(analysis.unknownNodeEdges == 1L, "unknown node edge was not counted");
		require("NEEDS ATTENTION".equals(analysis.statusLabel()),
				"invalid graph should need attention");
	}

	private static void handlesMissingEdgeFileAsWaitingState() throws Exception
	{
		Path directory = Files.createTempDirectory("graph-analysis-missing");
		File mapping = write(directory, "name-number-sample.txt",
				"Known\t1\nTarget\t2\n");
		File edges = directory.resolve("edges-numbers-sample.txt").toFile();

		GraphAnalysisEngine.Analysis analysis =
				GraphAnalysisEngine.analyze(mapping, edges);

		require(analysis.available, "mapping alone should still produce an analysis");
		require(analysis.validEdgeCount == 0L, "missing edge file should have zero edges");
		require("NO EDGES YET".equals(analysis.statusLabel()),
				"missing edge file should be a no-edges state");
		require(analysis.warnings.toString().contains("No edge list exists yet"),
				"missing edge warning was not emitted");
	}

	private static void derivesModeSpecificEdgeFileNames()
	{
		String regular = GraphAnalysisEngine.graphEdgesBaseName("Retail 50K.txt", "");
		require("edges-numbers-Retail_50K".equals(regular),
				"regular edge base name was wrong: " + regular);

		String converted = GraphAnalysisEngine.graphEdgesBaseName(
				"Retail 50K.txt",
				GraphAnalysisEngine.convertedModeSuffix(
						RecommendationTargetMode.SIMILAR_USERS,
						RecommendationModeSupport.SVM));
		require("edges-numbers-Retail_50K_similar-users".equals(converted),
				"converted edge base name was wrong: " + converted);
	}

	private static File write(Path directory, String fileName, String content) throws Exception
	{
		Path file = directory.resolve(fileName);
		Files.write(file, content.getBytes(StandardCharsets.UTF_8));
		return file.toFile();
	}

	private static void require(boolean condition, String message)
	{
		if (!condition)
		{
			throw new AssertionError(message);
		}
	}

	private static GraphAnalysisEngine.NodeDetail findNode(
			GraphAnalysisEngine.Analysis analysis,
			String name)
	{
		for (GraphAnalysisEngine.NodeDetail detail : analysis.nodeDetails)
		{
			if (name.equals(detail.name))
			{
				return detail;
			}
		}
		return null;
	}
}
