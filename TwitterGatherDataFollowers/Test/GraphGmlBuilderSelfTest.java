package TwitterGatherDataFollowers.userRyersonU;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public final class GraphGmlBuilderSelfTest {
	private GraphGmlBuilderSelfTest() {
	}

	public static void main(String[] args) throws Exception {
		buildsACompleteValidatedGraph();
		buildsFromEdgesAccumulatedAcrossRuns();
		buildsGraphWhenThereAreNoEdges();
		rejectsUnknownNodeIdsWithoutReplacingPreviousOutput();
		rejectsMalformedEdges();
		System.out.println("GraphGmlBuilderSelfTest passed");
	}

	private static void buildsACompleteValidatedGraph() throws Exception {
		Path directory = Files.createTempDirectory("graph-gml-builder");
		File mappings = write(directory, "name-number.txt",
				"Wal-Mart\t1\nT&T Supermarket\t2\nDennis Harrison\t3\n");
		File edges = write(directory, "edges.txt", "3 1\n3 2\n");
		File output = directory.resolve("graph.gml").toFile();

		GraphGmlBuilder.Result result = GraphGmlBuilder.build(edges, mappings, output);
		String gml = read(output);

		require(result.getNodeCount() == 3, "unexpected node count");
		require(result.getEdgeCount() == 2, "unexpected edge count");
		require(result.getOutputFile().equals(output), "unexpected output file");
		require(count(gml, "  node\n") == 3, "GML did not contain all mapped nodes");
		require(count(gml, "  edge\n") == 2, "GML did not contain all edges");
		require(gml.contains("    source 3\n    target 2\n"), "GML edge endpoints were incorrect");
	}

	private static void buildsFromEdgesAccumulatedAcrossRuns() throws Exception {
		Path directory = Files.createTempDirectory("graph-gml-builder-accumulated");
		File mappings = write(directory, "name-number.txt",
				"First User\t1\nSecond User\t2\nThird User\t3\nFourth User\t4\n");
		File edges = write(directory, "edges.txt", "1 2\n");
		Files.write(
				edges.toPath(),
				"2 3\n3 4\n".getBytes(StandardCharsets.UTF_8),
				StandardOpenOption.APPEND);
		File output = directory.resolve("graph.gml").toFile();

		GraphGmlBuilder.Result result = GraphGmlBuilder.build(edges, mappings, output);
		String gml = read(output);

		require(result.getEdgeCount() == 3, "later graph build lost accumulated edges");
		require(count(gml, "  edge\n") == 3, "GML did not contain every accumulated edge");
		require(gml.contains("    source 1\n    target 2\n"), "first-run edge was lost");
		require(gml.contains("    source 3\n    target 4\n"), "latest-run edge was lost");
	}

	private static void buildsGraphWhenThereAreNoEdges() throws Exception {
		Path directory = Files.createTempDirectory("graph-gml-builder-empty");
		File mappings = write(directory, "name-number.txt", "First User\t1\nSecond User\t2\n");
		File edges = write(directory, "edges.txt", "");
		File output = directory.resolve("graph.gml").toFile();

		GraphGmlBuilder.Result result = GraphGmlBuilder.build(edges, mappings, output);
		require(result.getNodeCount() == 2, "empty-edge graph lost mapped nodes");
		require(result.getEdgeCount() == 0, "empty-edge graph unexpectedly contained edges");
		require(output.isFile(), "empty-edge graph was not written");
	}

	private static void rejectsUnknownNodeIdsWithoutReplacingPreviousOutput() throws Exception {
		Path directory = Files.createTempDirectory("graph-gml-builder-atomic");
		File mappings = write(directory, "name-number.txt", "Known User\t1\n");
		File edges = write(directory, "edges.txt", "1 2\n");
		File output = write(directory, "graph.gml", "previous valid graph");

		expectIOException(new CheckedOperation() {
			@Override
			public void run() throws Exception {
				GraphGmlBuilder.build(edges, mappings, output);
			}
		}, "unknown node ID was accepted");

		require(read(output).equals("previous valid graph"), "invalid conversion replaced the previous graph");
	}

	private static void rejectsMalformedEdges() throws Exception {
		Path directory = Files.createTempDirectory("graph-gml-builder-malformed");
		File mappings = write(directory, "name-number.txt", "Known User\t1\n");
		File edges = write(directory, "edges.txt", "1 not-a-node\n");
		File output = directory.resolve("graph.gml").toFile();

		expectIOException(new CheckedOperation() {
			@Override
			public void run() throws Exception {
				GraphGmlBuilder.build(edges, mappings, output);
			}
		}, "malformed edge was accepted");

		require(!output.exists(), "malformed conversion produced an output graph");
	}

	private static File write(Path directory, String fileName, String content) throws IOException {
		Path path = directory.resolve(fileName);
		Files.write(path, content.getBytes(StandardCharsets.UTF_8));
		return path.toFile();
	}

	private static String read(File file) throws IOException {
		return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
	}

	private static int count(String value, String target) {
		int occurrences = 0;
		int index = 0;
		while ((index = value.indexOf(target, index)) >= 0) {
			occurrences++;
			index += target.length();
		}
		return occurrences;
	}

	private static void expectIOException(CheckedOperation operation, String failureMessage) throws Exception {
		try {
			operation.run();
		}
		catch (IOException expected) {
			return;
		}
		throw new AssertionError(failureMessage);
	}

	private static void require(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}

	private interface CheckedOperation {
		void run() throws Exception;
	}
}
