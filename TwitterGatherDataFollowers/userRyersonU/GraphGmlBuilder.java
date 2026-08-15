package TwitterGatherDataFollowers.userRyersonU;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

final class GraphGmlBuilder {
	private GraphGmlBuilder() {
	}

	static Result build(File edgeListFile, File nameNumberFile, File outputFile) throws IOException {
		requireReadableFile(edgeListFile, "Graph edge list");
		requireReadableFile(nameNumberFile, "Graph name-number map");
		if (outputFile == null) {
			throw new IllegalArgumentException("outputFile must not be null");
		}

		int maxNodeId = readMaxNodeId(nameNumberFile);
		if (maxNodeId < 1) {
			throw new IOException("Graph name-number map contains no valid node IDs: " + nameNumberFile.getPath());
		}

		File outputParent = outputFile.getAbsoluteFile().getParentFile();
		if (outputParent != null && !outputParent.exists() && !outputParent.mkdirs()) {
			throw new IOException("Could not create graph output directory: " + outputParent.getPath());
		}

		Path tempPath = Files.createTempFile(outputParent.toPath(), outputFile.getName() + ".", ".part");
		long edgeCount = 0;
		try {
			try (BufferedWriter writer = Files.newBufferedWriter(tempPath, StandardCharsets.UTF_8)) {
				writer.write("Creator \"DSMP - Source file: " + escapeGmlString(edgeListFile.getPath()) + "\"");
				writer.newLine();
				writer.write("graph");
				writer.newLine();
				writer.write("[");
				writer.newLine();

				for (int nodeId = 1; nodeId <= maxNodeId; nodeId++) {
					writer.write("  node");
					writer.newLine();
					writer.write("  [");
					writer.newLine();
					writer.write("    id " + nodeId);
					writer.newLine();
					writer.write("  ]");
					writer.newLine();
				}

				try (BufferedReader reader = Files.newBufferedReader(edgeListFile.toPath(), StandardCharsets.UTF_8)) {
					String line;
					int lineNumber = 0;
					while ((line = reader.readLine()) != null) {
						lineNumber++;
						String trimmedLine = line.trim();
						if (trimmedLine.isEmpty()) {
							continue;
						}
						String[] fields = trimmedLine.split("\\s+");
						if (fields.length != 2) {
							throw invalidEdgeLine(edgeListFile, lineNumber, "expected exactly two node IDs");
						}
						int source = parsePositiveId(fields[0], edgeListFile, lineNumber, "source");
						int target = parsePositiveId(fields[1], edgeListFile, lineNumber, "target");
						if (source > maxNodeId || target > maxNodeId) {
							throw invalidEdgeLine(edgeListFile, lineNumber,
									"node ID exceeds maximum mapped node ID " + maxNodeId);
						}

						writer.write("  edge");
						writer.newLine();
						writer.write("  [");
						writer.newLine();
						writer.write("    source " + source);
						writer.newLine();
						writer.write("    target " + target);
						writer.newLine();
						writer.write("  ]");
						writer.newLine();
						edgeCount++;
					}
				}

				writer.write("]");
				writer.newLine();
			}

			moveIntoPlace(tempPath, outputFile.toPath());
			return new Result(outputFile, maxNodeId, edgeCount);
		}
		finally {
			Files.deleteIfExists(tempPath);
		}
	}

	private static int readMaxNodeId(File nameNumberFile) throws IOException {
		int maxNodeId = 0;
		try (BufferedReader reader = Files.newBufferedReader(nameNumberFile.toPath(), StandardCharsets.UTF_8)) {
			String line;
			int lineNumber = 0;
			while ((line = reader.readLine()) != null) {
				lineNumber++;
				String trimmedLine = line.trim();
				if (trimmedLine.isEmpty()) {
					continue;
				}
				int separatorIndex = lastWhitespaceIndex(trimmedLine);
				if (separatorIndex < 1 || separatorIndex == trimmedLine.length() - 1) {
					throw new IOException("Invalid name-number mapping at " + nameNumberFile.getPath()
							+ ":" + lineNumber + ": expected a name followed by a numeric node ID");
				}
				String idField = trimmedLine.substring(separatorIndex + 1).trim();
				int nodeId = parsePositiveId(idField, nameNumberFile, lineNumber, "mapped node");
				maxNodeId = Math.max(maxNodeId, nodeId);
			}
		}
		return maxNodeId;
	}

	private static int lastWhitespaceIndex(String value) {
		for (int i = value.length() - 1; i >= 0; i--) {
			if (Character.isWhitespace(value.charAt(i))) {
				return i;
			}
		}
		return -1;
	}

	private static int parsePositiveId(String value, File file, int lineNumber, String fieldName) throws IOException {
		try {
			int nodeId = Integer.parseInt(value);
			if (nodeId < 1) {
				throw new NumberFormatException("node ID must be positive");
			}
			return nodeId;
		}
		catch (NumberFormatException ex) {
			throw new IOException("Invalid " + fieldName + " ID at " + file.getPath() + ":" + lineNumber
					+ ": " + value, ex);
		}
	}

	private static IOException invalidEdgeLine(File edgeListFile, int lineNumber, String reason) {
		return new IOException("Invalid graph edge at " + edgeListFile.getPath() + ":" + lineNumber + ": " + reason);
	}

	private static void requireReadableFile(File file, String description) throws IOException {
		if (file == null || !file.isFile() || !file.canRead()) {
			throw new IOException(description + " is missing or unreadable: "
					+ (file == null ? "null" : file.getPath()));
		}
	}

	private static void moveIntoPlace(Path tempPath, Path outputPath) throws IOException {
		try {
			Files.move(tempPath, outputPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		}
		catch (AtomicMoveNotSupportedException ex) {
			Files.move(tempPath, outputPath, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	private static String escapeGmlString(String value) {
		return value.replace("\\", "\\\\").replace("\"", "\\\"");
	}

	static final class Result {
		private final File outputFile;
		private final int nodeCount;
		private final long edgeCount;

		private Result(File outputFile, int nodeCount, long edgeCount) {
			this.outputFile = outputFile;
			this.nodeCount = nodeCount;
			this.edgeCount = edgeCount;
		}

		File getOutputFile() {
			return outputFile;
		}

		int getNodeCount() {
			return nodeCount;
		}

		long getEdgeCount() {
			return edgeCount;
		}
	}
}
