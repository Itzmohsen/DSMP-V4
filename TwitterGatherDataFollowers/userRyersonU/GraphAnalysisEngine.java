package TwitterGatherDataFollowers.userRyersonU;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

final class GraphAnalysisEngine
{
	private static final int TOP_LIMIT = 8;
	private static final int DETAIL_LIMIT = 10;
	private static final int EDGE_SAMPLE_LIMIT = 6;
	private static final int REPORT_NODE_DETAIL_LIMIT = 120;
	private static final int LABEL_PROPAGATION_ITERATIONS = 18;
	private static final String FALLBACK_COMMUNITY_METHOD =
			"Fast label-propagation estimate";
	static final int UNASSIGNED_COMMUNITY_ID = Integer.MIN_VALUE;

	private GraphAnalysisEngine()
	{
	}

	static Analysis analyze(File mappingFile, File edgeFile) throws IOException
	{
		if (mappingFile == null || !mappingFile.isFile())
		{
			return Analysis.missing(
					mappingFile,
					edgeFile,
					"Graph node map is missing. Run Get Users and at least one recommendation first.");
		}

		Mapping mapping = loadMapping(mappingFile);
		MutableAnalysis state = new MutableAnalysis(mappingFile, edgeFile, mapping);
		if (edgeFile == null || !edgeFile.isFile())
		{
			state.warnings.add("No edge list exists yet for this dataset and recommendation mode.");
			state.warnings.add("Recommendations will populate this file even when final graph export is unchecked.");
			state.computeGraphShape();
			state.computeWarnings();
			return state.finish();
		}

		readEdges(edgeFile, mapping, state);
		state.computeGraphShape();
		state.computeCommunities();
		state.computeRankings();
		state.computeWarnings();
		return state.finish();
	}

	static String graphEdgesBaseName(String selectedFileName, String convertedModeSuffix)
	{
		String datasetName = selectedFileName == null ? "" : selectedFileName;
		int extensionIndex = datasetName.lastIndexOf('.');
		if (extensionIndex > 0)
		{
			datasetName = datasetName.substring(0, extensionIndex);
		}
		datasetName = datasetName.replaceAll("[^A-Za-z0-9._-]", "_");
		if (datasetName.length() == 0)
		{
			datasetName = "uploaded-dataset";
		}
		return "edges-numbers-" + datasetName
				+ (convertedModeSuffix == null ? "" : convertedModeSuffix);
	}

	static String convertedModeSuffix(
			RecommendationTargetMode mode,
			int algorithm)
	{
		if (!RecommendationModeSupport.requiresConvertedPipeline(mode, algorithm))
		{
			return "";
		}
		RecommendationCandidateType type =
				RecommendationModeSupport.resolveCandidateType(mode, algorithm);
		return type == RecommendationCandidateType.USER
				? "_similar-users" : "_followee-accounts";
	}

	static File mappingFile(File importantStuffDirectory, String selectedFileName)
	{
		return new File(importantStuffDirectory, "name-number-" + selectedFileName);
	}

	static File edgeFile(
			File importantStuffDirectory,
			String selectedFileName,
			RecommendationTargetMode mode,
			int algorithm)
	{
		String suffix = convertedModeSuffix(mode, algorithm);
		return new File(
				importantStuffDirectory,
				graphEdgesBaseName(selectedFileName, suffix) + ".txt");
	}

	static void writeTextReport(Analysis analysis, File outputFile) throws IOException
	{
		if (analysis == null)
		{
			throw new IllegalArgumentException("analysis must not be null");
		}
		if (outputFile == null)
		{
			throw new IllegalArgumentException("outputFile must not be null");
		}
		File parent = outputFile.getParentFile();
		if (parent != null && !parent.exists() && !parent.mkdirs())
		{
			throw new IOException("Could not create report directory: " + parent.getPath());
		}

		try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
				new FileOutputStream(outputFile), StandardCharsets.UTF_8)))
		{
			writer.write("DSMP Graph Insights Report");
			writer.newLine();
			writer.write("Generated: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
			writer.newLine();
			writer.write("Mapping: " + safePath(analysis.mappingFile));
			writer.newLine();
			writer.write("Edges: " + safePath(analysis.edgeFile));
			writer.newLine();
			writer.newLine();
			writer.write("Summary");
			writer.newLine();
			writer.write("Mapped nodes: " + analysis.mappedNodeCount);
			writer.newLine();
			writer.write("Active nodes: " + analysis.activeNodeCount);
			writer.newLine();
			writer.write("Valid edges: " + analysis.validEdgeCount);
			writer.newLine();
			writer.write("Unique edges: " + analysis.uniqueDirectedEdgeCount);
			writer.newLine();
			writer.write("Duplicate edges: " + analysis.duplicateEdgeCount);
			writer.newLine();
			writer.write("Malformed edge lines: " + analysis.malformedEdgeLines);
			writer.newLine();
			writer.write("Unknown node edges: " + analysis.unknownNodeEdges);
			writer.newLine();
			writer.write("Active components: " + analysis.activeComponentCount);
			writer.newLine();
			writer.write("Largest component: " + analysis.largestComponentSize);
			writer.newLine();
			writer.write("Communities: " + analysis.estimatedCommunityCount);
			writer.newLine();
			writer.write("Community method: " + analysis.communityMethod);
			writer.newLine();
			writer.write("Community source: "
					+ (analysis.gephiCommunityCount
							? "Gephi modularity, resolution 1.0, weighted edges"
							: "fallback estimate"));
			if (Double.isFinite(analysis.modularityScore))
			{
				writer.newLine();
				writer.write("Gephi modularity score: "
						+ String.format(Locale.US, "%.4f", analysis.modularityScore));
			}
			if (analysis.gephiCommunityCount)
			{
				writer.newLine();
				writer.write("Gephi imported nodes: " + analysis.gephiImportedNodeCount);
				writer.newLine();
				writer.write("Gephi imported edges: " + analysis.gephiImportedEdgeCount);
			}
			writer.newLine();
			writer.write("Recommendation edges: " + analysis.recommendationEdgeCount);
			writer.newLine();
			writer.write("Unique recommended targets: " + analysis.uniqueRecommendedTargets);
			writer.newLine();
			writer.write("Recommendation diversity: " + percent(analysis.recommendationDiversity));
			writer.newLine();
			writer.write("Hub concentration: " + percent(analysis.hubConcentration));
			writer.newLine();
			writer.newLine();
			writeSection(writer, "Warnings", analysis.warnings);
			writeNodeSection(writer, "Top recommended targets", analysis.topRecommendedTargets);
			writeNodeSection(writer, "Top incoming nodes", analysis.topIncomingNodes);
			writeNodeSection(writer, "Top graph hubs", analysis.topHubs);
			writeNodeSection(writer, "Largest communities", analysis.topCommunities);
			writeCommunityDetailSection(writer, analysis.communityDetails);
			writeNodeDetailSection(writer, analysis.nodeDetails);
		}
	}

	private static Mapping loadMapping(File mappingFile) throws IOException
	{
		Map<Integer, String> namesById = new LinkedHashMap<Integer, String>();
		Map<String, Integer> idsByName = new LinkedHashMap<String, Integer>();
		int malformed = 0;
		int duplicateIds = 0;
		int duplicateNames = 0;

		try (BufferedReader reader = new BufferedReader(new InputStreamReader(
				new FileInputStream(mappingFile), StandardCharsets.UTF_8), 128 * 1024))
		{
			String line;
			int lineNumber = 0;
			while ((line = reader.readLine()) != null)
			{
				lineNumber++;
				String trimmed = line.trim();
				if (trimmed.length() == 0)
				{
					continue;
				}
				String[] fields = trimmed.split("\\s+(?=\\d+$)", 2);
				if (fields.length != 2)
				{
					malformed++;
					continue;
				}
				try
				{
					int id = Integer.parseInt(fields[1].trim());
					if (id < 1 || fields[0].trim().length() == 0)
					{
						malformed++;
						continue;
					}
					if (namesById.containsKey(id))
					{
						duplicateIds++;
					}
					if (idsByName.containsKey(fields[0]))
					{
						duplicateNames++;
					}
					namesById.put(id, fields[0].trim());
					idsByName.put(fields[0].trim(), id);
				}
				catch (NumberFormatException ex)
				{
					malformed++;
				}
			}
		}

		return new Mapping(namesById, idsByName, malformed, duplicateIds, duplicateNames);
	}

	private static void readEdges(
			File edgeFile,
			Mapping mapping,
			MutableAnalysis state) throws IOException
	{
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(
				new FileInputStream(edgeFile), StandardCharsets.UTF_8), 128 * 1024))
		{
			String line;
			int lineNumber = 0;
			int pairSource = -1;
			int pairOriginalTarget = -1;
			long validIndex = 0L;
			while ((line = reader.readLine()) != null)
			{
				lineNumber++;
				String trimmed = line.trim();
				if (trimmed.length() == 0)
				{
					continue;
				}
				state.nonEmptyEdgeLines++;
				String[] fields = trimmed.split("\\s+");
				if (fields.length != 2)
				{
					state.malformedEdgeLines++;
					state.rememberIssue("Line " + lineNumber + ": expected two node ids.");
					continue;
				}
				int source;
				int target;
				try
				{
					source = Integer.parseInt(fields[0]);
					target = Integer.parseInt(fields[1]);
				}
				catch (NumberFormatException ex)
				{
					state.malformedEdgeLines++;
					state.rememberIssue("Line " + lineNumber + ": non-numeric node id.");
					continue;
				}
				if (!mapping.namesById.containsKey(source)
						|| !mapping.namesById.containsKey(target))
				{
					state.unknownNodeEdges++;
					state.rememberIssue("Line " + lineNumber + ": edge references an unmapped node.");
					continue;
				}

				state.validEdgeCount++;
				state.activeNodes.add(source);
				state.activeNodes.add(target);
				increment(state.outDegree, source, 1);
				increment(state.inDegree, target, 1);
				increment(state.totalDegree, source, 1);
				if (source != target)
				{
					increment(state.totalDegree, target, 1);
				}
				else
				{
					state.selfLoopCount++;
				}
				long directedKey = edgeKey(source, target);
				if (!state.uniqueDirectedEdges.add(directedKey))
				{
					state.duplicateEdgeCount++;
				}
				addNeighbor(state.adjacency, source, target);
				addNeighbor(state.adjacency, target, source);

				if (validIndex % 2L == 0L)
				{
					pairSource = source;
					pairOriginalTarget = target;
				}
				else if (source == pairSource)
				{
					state.originalEdgeCount++;
					state.recommendationEdgeCount++;
					if (pairOriginalTarget == target)
					{
						state.recommendationSameAsOriginalCount++;
					}
					else
					{
						state.recommendationChangedTargetCount++;
					}
					increment(state.recommendedTargetCounts, target, 1);
					increment(state.originalTargetCounts, pairOriginalTarget, 1);
				}
				else
				{
					state.edgePairMismatchCount++;
				}
				validIndex++;
			}
			if (validIndex % 2L != 0L)
			{
				state.unpairedTrailingEdge = true;
			}
		}
	}

	private static ParsedEdge parseValidEdgeLine(String line, Mapping mapping)
	{
		if (line == null)
		{
			return null;
		}
		String trimmed = line.trim();
		if (trimmed.length() == 0)
		{
			return null;
		}
		String[] fields = trimmed.split("\\s+");
		if (fields.length != 2)
		{
			return null;
		}
		try
		{
			int source = Integer.parseInt(fields[0]);
			int target = Integer.parseInt(fields[1]);
			if (!mapping.namesById.containsKey(source)
					|| !mapping.namesById.containsKey(target))
			{
				return null;
			}
			return new ParsedEdge(source, target);
		}
		catch (NumberFormatException ex)
		{
			return null;
		}
	}

	private static void addNeighbor(
			Map<Integer, Set<Integer>> adjacency,
			int source,
			int target)
	{
		Set<Integer> neighbors = adjacency.get(source);
		if (neighbors == null)
		{
			neighbors = new LinkedHashSet<Integer>();
			adjacency.put(source, neighbors);
		}
		neighbors.add(target);
	}

	private static void increment(Map<Integer, Integer> map, int key, int amount)
	{
		Integer current = map.get(key);
		map.put(key, current == null ? amount : current.intValue() + amount);
	}

	private static int value(Map<Integer, Integer> map, Integer key)
	{
		if (map == null || key == null)
		{
			return 0;
		}
		Integer value = map.get(key);
		return value == null ? 0 : value.intValue();
	}

	private static Set<Integer> idSet(List<NodeScore> scores)
	{
		Set<Integer> ids = new HashSet<Integer>();
		if (scores == null)
		{
			return ids;
		}
		for (NodeScore score : scores)
		{
			ids.add(score.id);
		}
		return ids;
	}

	private static long edgeKey(int source, int target)
	{
		return (((long) source) << 32) ^ (target & 0xffffffffL);
	}

	private static long undirectedKey(int a, int b)
	{
		int low = Math.min(a, b);
		int high = Math.max(a, b);
		return edgeKey(low, high);
	}

	private static String safePath(File file)
	{
		return file == null ? "not selected" : file.getPath();
	}

	private static String percent(double value)
	{
		return new DecimalFormat("0.0%").format(value);
	}

	private static void writeSection(
			BufferedWriter writer,
			String title,
			List<String> lines) throws IOException
	{
		writer.newLine();
		writer.write(title);
		writer.newLine();
		if (lines == null || lines.isEmpty())
		{
			writer.write("- None");
			writer.newLine();
			return;
		}
		for (String line : lines)
		{
			writer.write("- " + line);
			writer.newLine();
		}
	}

	private static void writeNodeSection(
			BufferedWriter writer,
			String title,
			List<NodeScore> scores) throws IOException
	{
		writer.newLine();
		writer.write(title);
		writer.newLine();
		if (scores == null || scores.isEmpty())
		{
			writer.write("- None");
			writer.newLine();
			return;
		}
		for (NodeScore score : scores)
		{
			writer.write("- " + score.name + " (" + score.value + ")");
			if (score.detail != null && score.detail.length() > 0)
			{
				writer.write(" - " + score.detail);
			}
			writer.newLine();
		}
	}

	private static void writeCommunityDetailSection(
			BufferedWriter writer,
			List<CommunityDetail> communities) throws IOException
	{
		writer.newLine();
		writer.write("Community Explorer");
		writer.newLine();
		if (communities == null || communities.isEmpty())
		{
			writer.write("- None");
			writer.newLine();
			return;
		}
		for (CommunityDetail community : communities)
		{
			writer.write("- #" + community.id + " " + community.name
					+ " | size " + community.size
					+ " | active " + community.activeNodeCount
					+ " | internal edges " + community.internalEdgeCount
					+ " | external edges " + community.externalEdgeCount
					+ " | dominant target " + community.dominantRecommendedTarget
					+ " (" + community.dominantRecommendedTargetCount + ")"
					+ " | " + community.methodDetail);
			writer.newLine();
			writer.write("  Why: " + community.why);
			writer.newLine();
			if (!community.nodePreview.isEmpty())
			{
				writer.write("  Nodes: " + joinStrings(community.nodePreview));
				writer.newLine();
			}
			if (!community.internalEdgeSamples.isEmpty())
			{
				writer.write("  Internal edge samples: "
						+ joinEdges(community.internalEdgeSamples));
				writer.newLine();
			}
			if (!community.externalEdgeSamples.isEmpty())
			{
				writer.write("  Leaving edge samples: "
						+ joinEdges(community.externalEdgeSamples));
				writer.newLine();
			}
		}
	}

	private static void writeNodeDetailSection(
			BufferedWriter writer,
			List<NodeDetail> nodes) throws IOException
	{
		writer.newLine();
		writer.write("Node Inspector");
		writer.newLine();
		if (nodes == null || nodes.isEmpty())
		{
			writer.write("- None");
			writer.newLine();
			return;
		}
		int limit = Math.min(nodes.size(), REPORT_NODE_DETAIL_LIMIT);
		for (int i = 0; i < limit; i++)
		{
			NodeDetail node = nodes.get(i);
			writer.write("- #" + node.id + " " + node.name
					+ " | community " + node.communityName
					+ " | component size " + node.componentSize
					+ " | in/out/degree " + node.inDegree + "/"
					+ node.outDegree + "/" + node.totalDegree
					+ " | original target count " + node.originalTargetCount
					+ " | recommended target count " + node.recommendedTargetCount
					+ " | role " + node.role);
			writer.newLine();
			writer.write("  " + node.why);
			writer.newLine();
		}
		if (nodes.size() > limit)
		{
			writer.write("- Node detail truncated at "
					+ REPORT_NODE_DETAIL_LIMIT
					+ " rows for report readability.");
			writer.newLine();
		}
	}

	private static String joinStrings(List<String> values)
	{
		StringBuilder builder = new StringBuilder();
		for (String value : values)
		{
			if (builder.length() > 0)
			{
				builder.append(", ");
			}
			builder.append(value);
		}
		return builder.toString();
	}

	private static String joinEdges(List<EdgeSample> values)
	{
		StringBuilder builder = new StringBuilder();
		for (EdgeSample value : values)
		{
			if (builder.length() > 0)
			{
				builder.append(", ");
			}
			builder.append(value.summary());
		}
		return builder.toString();
	}

	private static final class ParsedEdge
	{
		private final int source;
		private final int target;

		private ParsedEdge(int source, int target)
		{
			this.source = source;
			this.target = target;
		}
	}

	private static final class Mapping
	{
		private final Map<Integer, String> namesById;
		private final Map<String, Integer> idsByName;
		private final int malformedLines;
		private final int duplicateIds;
		private final int duplicateNames;

		private Mapping(
				Map<Integer, String> namesById,
				Map<String, Integer> idsByName,
				int malformedLines,
				int duplicateIds,
				int duplicateNames)
		{
			this.namesById = namesById;
			this.idsByName = idsByName;
			this.malformedLines = malformedLines;
			this.duplicateIds = duplicateIds;
			this.duplicateNames = duplicateNames;
		}
	}

	private static final class MutableAnalysis
	{
		private final File mappingFile;
		private final File edgeFile;
		private final Mapping mapping;
		private final Set<Integer> activeNodes = new LinkedHashSet<Integer>();
		private final Map<Integer, Set<Integer>> adjacency =
				new LinkedHashMap<Integer, Set<Integer>>();
		private final Set<Long> uniqueDirectedEdges = new HashSet<Long>();
		private final Map<Integer, Integer> inDegree = new HashMap<Integer, Integer>();
		private final Map<Integer, Integer> outDegree = new HashMap<Integer, Integer>();
		private final Map<Integer, Integer> totalDegree = new HashMap<Integer, Integer>();
		private final Map<Integer, Integer> recommendedTargetCounts =
				new HashMap<Integer, Integer>();
		private final Map<Integer, Integer> originalTargetCounts =
				new HashMap<Integer, Integer>();
		private final List<String> warnings = new ArrayList<String>();
		private final List<String> issueSamples = new ArrayList<String>();
		private final Map<Integer, Integer> componentSizeByLeader =
				new LinkedHashMap<Integer, Integer>();
		private final Map<Integer, Integer> componentLeaderByNode =
				new LinkedHashMap<Integer, Integer>();
		private final Map<Integer, Integer> componentSizeByNode =
				new LinkedHashMap<Integer, Integer>();
		private final Map<Integer, Integer> communitySizeByLabel =
				new LinkedHashMap<Integer, Integer>();
		private final Map<Integer, String> communityNameByLabel =
				new LinkedHashMap<Integer, String>();
		private final Map<Integer, Integer> communityByNode =
				new LinkedHashMap<Integer, Integer>();

		private long nonEmptyEdgeLines;
		private long validEdgeCount;
		private long duplicateEdgeCount;
		private long malformedEdgeLines;
		private long unknownNodeEdges;
		private long selfLoopCount;
		private long originalEdgeCount;
		private long recommendationEdgeCount;
		private long recommendationChangedTargetCount;
		private long recommendationSameAsOriginalCount;
		private long edgePairMismatchCount;
		private boolean unpairedTrailingEdge;
		private int activeComponentCount;
		private int largestComponentSize;
		private int isolatedMappedNodeCount;
		private int estimatedCommunityCount;
		private String communityMethod = "Not computed";
		private boolean gephiCommunityCount;
		private double modularityScore = Double.NaN;
		private int gephiImportedNodeCount;
		private long gephiImportedEdgeCount;
		private long uniqueUndirectedEdgeCount;
		private double activeDensity;
		private double recommendationDiversity;
		private double hubConcentration;
		private int healthScore = 100;

		private List<NodeScore> topRecommendedTargets =
				Collections.emptyList();
		private List<NodeScore> topIncomingNodes =
				Collections.emptyList();
		private List<NodeScore> topHubs =
				Collections.emptyList();
		private List<NodeScore> topCommunities =
				Collections.emptyList();
		private List<CommunityDetail> communityDetails =
				Collections.emptyList();
		private List<NodeDetail> nodeDetails =
				Collections.emptyList();

		private MutableAnalysis(File mappingFile, File edgeFile, Mapping mapping)
		{
			this.mappingFile = mappingFile;
			this.edgeFile = edgeFile;
			this.mapping = mapping;
		}

		private void rememberIssue(String issue)
		{
			if (issueSamples.size() < 5)
			{
				issueSamples.add(issue);
			}
		}

		private void computeGraphShape()
		{
			isolatedMappedNodeCount = Math.max(
					0,
					mapping.namesById.size() - activeNodes.size());
			Set<Integer> visited = new HashSet<Integer>();
			List<Integer> nodes = new ArrayList<Integer>(activeNodes);
			Collections.sort(nodes);
			for (Integer node : nodes)
			{
				if (visited.contains(node))
				{
					continue;
				}
				int size = 0;
				List<Integer> componentNodes = new ArrayList<Integer>();
				ArrayDeque<Integer> queue = new ArrayDeque<Integer>();
				queue.add(node);
				visited.add(node);
				while (!queue.isEmpty())
				{
					Integer current = queue.removeFirst();
					size++;
					componentLeaderByNode.put(current, node);
					componentNodes.add(current);
					Set<Integer> neighbors = adjacency.get(current);
					if (neighbors == null)
					{
						continue;
					}
					for (Integer neighbor : neighbors)
					{
						if (!visited.contains(neighbor))
						{
							visited.add(neighbor);
							queue.add(neighbor);
						}
					}
				}
				activeComponentCount++;
				largestComponentSize = Math.max(largestComponentSize, size);
				componentSizeByLeader.put(node, size);
				for (Integer componentNode : componentNodes)
				{
					componentSizeByNode.put(componentNode, size);
				}
			}

			Set<Long> undirected = new HashSet<Long>();
			for (Map.Entry<Integer, Set<Integer>> entry : adjacency.entrySet())
			{
				for (Integer neighbor : entry.getValue())
				{
					undirected.add(undirectedKey(entry.getKey(), neighbor));
				}
			}
			uniqueUndirectedEdgeCount = undirected.size();
			int n = activeNodes.size();
			activeDensity = n < 2 ? 0.0
					: Math.min(1.0, (2.0 * uniqueUndirectedEdgeCount) / (n * (n - 1.0)));
		}

		private void computeCommunities()
		{
			if (activeNodes.isEmpty())
			{
				estimatedCommunityCount = 0;
				communityMethod = "No active graph edges";
				return;
			}
			if (edgeFile != null && edgeFile.isFile() && validEdgeCount > 0L)
			{
				try
				{
					GraphModularityAnalyzer.Result result =
							GraphModularityAnalyzer.analyze(
									mappingFile,
									edgeFile,
									mapping.namesById);
					if (!result.communitySizes.isEmpty())
					{
						communitySizeByLabel.clear();
						communitySizeByLabel.putAll(result.communitySizes);
						communityNameByLabel.clear();
						communityNameByLabel.putAll(result.communityNames);
						communityByNode.clear();
						communityByNode.putAll(result.communityByNodeId);
						estimatedCommunityCount = communitySizeByLabel.size();
						communityMethod =
								"Gephi modularity (resolution 1.0, weighted edges)";
						gephiCommunityCount = true;
						modularityScore = result.modularity;
						gephiImportedNodeCount = result.importedNodeCount;
						gephiImportedEdgeCount = result.importedEdgeCount;
						return;
					}
				}
				catch (Exception ex)
				{
					warnings.add("Gephi modularity could not be computed for this graph, "
							+ "so Graph Insights is showing a fallback community estimate: "
							+ ex.getMessage());
					healthScore -= 4;
				}
			}
			computeFallbackCommunities();
		}

		private void computeFallbackCommunities()
		{
			List<Integer> nodes = new ArrayList<Integer>(activeNodes);
			Collections.sort(nodes);
			Map<Integer, Integer> labels = new HashMap<Integer, Integer>();
			for (Integer node : nodes)
			{
				labels.put(node, node);
			}
			for (int i = 0; i < LABEL_PROPAGATION_ITERATIONS; i++)
			{
				boolean changed = false;
				for (Integer node : nodes)
				{
					Set<Integer> neighbors = adjacency.get(node);
					if (neighbors == null || neighbors.isEmpty())
					{
						continue;
					}
					Map<Integer, Integer> counts = new TreeMap<Integer, Integer>();
					for (Integer neighbor : neighbors)
					{
						Integer label = labels.get(neighbor);
						if (label == null)
						{
							continue;
						}
						Integer count = counts.get(label);
						counts.put(label, count == null ? 1 : count + 1);
					}
					int bestLabel = labels.get(node);
					int bestCount = -1;
					for (Map.Entry<Integer, Integer> entry : counts.entrySet())
					{
						if (entry.getValue() > bestCount)
						{
							bestLabel = entry.getKey();
							bestCount = entry.getValue();
						}
					}
					if (bestLabel != labels.get(node).intValue())
					{
						labels.put(node, bestLabel);
						changed = true;
					}
				}
				if (!changed)
				{
					break;
				}
			}
			for (Integer node : nodes)
			{
				Integer label = labels.get(node);
				communityByNode.put(node, label);
				Integer count = communitySizeByLabel.get(label);
				communitySizeByLabel.put(label, count == null ? 1 : count + 1);
				if (!communityNameByLabel.containsKey(label))
				{
					String leader = mapping.namesById.get(label);
					communityNameByLabel.put(
							label,
							"Estimated community near "
									+ (leader == null ? "#" + label : leader));
				}
			}
			estimatedCommunityCount = communitySizeByLabel.size();
			communityMethod = FALLBACK_COMMUNITY_METHOD;
		}

		private void computeRankings()
		{
			topRecommendedTargets = topNodes(
					recommendedTargetCounts,
					mapping.namesById,
					"recommended edge",
					recommendationEdgeCount,
					TOP_LIMIT);
			topIncomingNodes = topNodes(
					inDegree,
					mapping.namesById,
					"incoming edge",
					validEdgeCount,
					TOP_LIMIT);
			topHubs = topNodes(
					totalDegree,
					mapping.namesById,
					"total degree",
					Math.max(1L, validEdgeCount * 2L),
					TOP_LIMIT);
			topCommunities = topCommunities(
					communitySizeByLabel,
					communityNameByLabel,
					mapping.namesById,
					gephiCommunityCount ? mapping.namesById.size() : activeNodes.size(),
					communityMethod,
					TOP_LIMIT);
			int uniqueRecommended = recommendedTargetCounts.size();
			recommendationDiversity = recommendationEdgeCount == 0L
					? 0.0 : uniqueRecommended / (double) recommendationEdgeCount;
			int maxInDegree = 0;
			for (Integer value : inDegree.values())
			{
				maxInDegree = Math.max(maxInDegree, value.intValue());
			}
			hubConcentration = validEdgeCount == 0L
					? 0.0 : maxInDegree / (double) validEdgeCount;
		}

		private void computeExplorationDetails()
		{
			Map<Integer, CommunityAccumulator> communities =
					createCommunityAccumulators();
			populateCommunityEdgeDetails(communities);
			communityDetails = finishCommunityDetails(communities);
			nodeDetails = buildNodeDetails();
		}

		private Map<Integer, CommunityAccumulator> createCommunityAccumulators()
		{
			Map<Integer, CommunityAccumulator> communities =
					new LinkedHashMap<Integer, CommunityAccumulator>();
			for (Integer nodeId : mapping.namesById.keySet())
			{
				Integer communityId = communityByNode.get(nodeId);
				if (communityId == null)
				{
					continue;
				}
				CommunityAccumulator community = communities.get(communityId);
				if (community == null)
				{
					community = new CommunityAccumulator(
							communityId,
							communityDisplayName(communityId));
					communities.put(communityId, community);
				}
				community.nodeIds.add(nodeId);
				if (activeNodes.contains(nodeId))
				{
					community.activeNodeCount++;
				}
			}
			return communities;
		}

		private void populateCommunityEdgeDetails(
				Map<Integer, CommunityAccumulator> communities)
		{
			if (communities.isEmpty() || edgeFile == null || !edgeFile.isFile())
			{
				return;
			}
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(
					new FileInputStream(edgeFile), StandardCharsets.UTF_8), 128 * 1024))
			{
				String line;
				int pairSource = -1;
				int pairOriginalTarget = -1;
				long validIndex = 0L;
				while ((line = reader.readLine()) != null)
				{
					ParsedEdge edge = parseValidEdgeLine(line, mapping);
					if (edge == null)
					{
						continue;
					}
					Integer sourceCommunityId = communityByNode.get(edge.source);
					Integer targetCommunityId = communityByNode.get(edge.target);
					boolean recommendationEdge =
							validIndex % 2L != 0L && edge.source == pairSource;
					if (sourceCommunityId != null && targetCommunityId != null)
					{
						if (sourceCommunityId.equals(targetCommunityId))
						{
							CommunityAccumulator community =
									communities.get(sourceCommunityId);
							if (community != null)
							{
								community.internalEdgeCount++;
								rememberEdge(
										community.internalEdgeSamples,
										edge,
										sourceCommunityId,
										targetCommunityId,
										true,
										recommendationEdge);
							}
						}
						else
						{
							CommunityAccumulator sourceCommunity =
									communities.get(sourceCommunityId);
							if (sourceCommunity != null)
							{
								sourceCommunity.externalEdgeCount++;
								rememberEdge(
										sourceCommunity.externalEdgeSamples,
										edge,
										sourceCommunityId,
										targetCommunityId,
										false,
										recommendationEdge);
							}
							CommunityAccumulator targetCommunity =
									communities.get(targetCommunityId);
							if (targetCommunity != null)
							{
								targetCommunity.incomingExternalEdgeCount++;
							}
						}
					}

					if (validIndex % 2L == 0L)
					{
						pairSource = edge.source;
						pairOriginalTarget = edge.target;
					}
					else if (edge.source == pairSource)
					{
						CommunityAccumulator community =
								sourceCommunityId == null
										? null : communities.get(sourceCommunityId);
						if (community != null)
						{
							community.recommendationEdgeCount++;
							increment(community.recommendedTargetCounts, edge.target, 1);
							increment(community.originalTargetCounts, pairOriginalTarget, 1);
						}
					}
					validIndex++;
				}
			}
			catch (IOException ex)
			{
				warnings.add("Community Explorer could not re-read edge samples: "
						+ ex.getMessage());
				healthScore -= 4;
			}
		}

		private List<CommunityDetail> finishCommunityDetails(
				Map<Integer, CommunityAccumulator> communities)
		{
			List<CommunityAccumulator> ordered =
					new ArrayList<CommunityAccumulator>(communities.values());
			Collections.sort(ordered, CommunityAccumulator.DESCENDING);
			List<CommunityDetail> details = new ArrayList<CommunityDetail>();
			for (CommunityAccumulator community : ordered)
			{
				List<NodeScore> topNodes = topNodesForIds(
						community.nodeIds,
						totalDegree,
						"total degree",
						Math.max(1L, validEdgeCount * 2L),
						DETAIL_LIMIT);
				List<NodeScore> topHubs = topNodesForIds(
						community.nodeIds,
						inDegree,
						"incoming edge",
						Math.max(1L, validEdgeCount),
						DETAIL_LIMIT);
				List<NodeScore> topRecommendations = topNodes(
						community.recommendedTargetCounts,
						mapping.namesById,
						"recommendation edge",
						Math.max(1L, community.recommendationEdgeCount),
						DETAIL_LIMIT);
				String dominantName = topRecommendations.isEmpty()
						? "none" : topRecommendations.get(0).name;
				int dominantCount = topRecommendations.isEmpty()
						? 0 : topRecommendations.get(0).value;
				details.add(new CommunityDetail(
						community.id,
						community.name,
						community.nodeIds.size(),
						community.activeNodeCount,
						community.internalEdgeCount,
						community.externalEdgeCount,
						community.incomingExternalEdgeCount,
						community.recommendationEdgeCount,
						dominantName,
						dominantCount,
						communityMethodDetail(),
						topNodes,
						topHubs,
						topRecommendations,
						nodeNames(community.nodeIds, DETAIL_LIMIT),
						immutableEdges(community.internalEdgeSamples),
						immutableEdges(community.externalEdgeSamples),
						communityWhy(community, topNodes, topRecommendations)));
			}
			return Collections.unmodifiableList(details);
		}

		private List<NodeDetail> buildNodeDetails()
		{
			Set<Integer> topHubIds = idSet(topHubs);
			Set<Integer> topRecommendedIds = idSet(topRecommendedTargets);
			List<NodeDetail> details = new ArrayList<NodeDetail>();
			for (Integer nodeId : mapping.namesById.keySet())
			{
				String name = mapping.namesById.get(nodeId);
				int in = value(inDegree, nodeId);
				int out = value(outDegree, nodeId);
				int total = value(totalDegree, nodeId);
				int originalTargets = value(originalTargetCounts, nodeId);
				int recommendedTargets = value(recommendedTargetCounts, nodeId);
				Integer communityId = communityByNode.get(nodeId);
				String communityName = communityId == null
						? "Unassigned isolate" : communityDisplayName(communityId);
				int componentLeader = value(componentLeaderByNode, nodeId);
				int componentSize = value(componentSizeByNode, nodeId);
				Set<Integer> neighbors = adjacency.get(nodeId);
				int neighborCount = neighbors == null ? 0 : neighbors.size();
				int externalNeighborCount = externalNeighborCount(nodeId, neighbors);
				List<NodeScore> neighborScores = neighborScores(neighbors, communityId);
				boolean active = activeNodes.contains(nodeId);
				boolean isolate = !active;
				boolean hub = active && (topHubIds.contains(nodeId)
						|| total >= Math.max(3, Math.sqrt(Math.max(1L, validEdgeCount))));
				boolean bridge = active && externalNeighborCount > 0
						&& (externalNeighborCount >= 2 || total >= 3);
				boolean recommendationSink = recommendedTargets > 0
						&& (topRecommendedIds.contains(nodeId)
								|| recommendedTargets >= Math.max(2, recommendationEdgeCount / 10L));
				String role = nodeRole(
						isolate,
						hub,
						bridge,
						recommendationSink,
						in,
						out);
				details.add(new NodeDetail(
						nodeId,
						name,
						communityId == null ? UNASSIGNED_COMMUNITY_ID : communityId,
						communityName,
						componentLeader,
						componentSize,
						in,
						out,
						total,
						originalTargets,
						recommendedTargets,
						neighborCount,
						externalNeighborCount,
						active,
						isolate,
						hub,
						bridge,
						recommendationSink,
						role,
						neighborScores,
						nodeWhy(
								name,
								communityName,
								isolate,
								hub,
								bridge,
								recommendationSink,
								neighborCount,
								externalNeighborCount,
								in,
								out,
								recommendedTargets)));
			}
			Collections.sort(details, NodeDetail.BY_NAME);
			return Collections.unmodifiableList(details);
		}

		private String communityDisplayName(Integer communityId)
		{
			if (communityId == null)
			{
				return "Unassigned";
			}
			String name = communityNameByLabel.get(communityId);
			if (name != null && name.length() > 0)
			{
				return name;
			}
			String leader = mapping.namesById.get(communityId);
			return leader == null
					? "Community " + communityId
					: "Community " + communityId + " near " + leader;
		}

		private String communityMethodDetail()
		{
			if (gephiCommunityCount)
			{
				if (Double.isFinite(modularityScore))
				{
					return "Gephi modularity Q="
							+ String.format(Locale.US, "%.4f", modularityScore);
				}
				return "Gephi modularity";
			}
			return communityMethod;
		}

		private String communityWhy(
				CommunityAccumulator community,
				List<NodeScore> topNodes,
				List<NodeScore> topRecommendations)
		{
			if (community.nodeIds.isEmpty())
			{
				return "This community has no mapped nodes in the current analysis.";
			}
			String leader = topNodes.isEmpty()
					? community.name : topNodes.get(0).name;
			String recommendation = topRecommendations.isEmpty()
					? "no dominant recommendation target yet"
					: "dominant recommendation target " + topRecommendations.get(0).name
							+ " (" + topRecommendations.get(0).value + " edge"
							+ (topRecommendations.get(0).value == 1 ? "" : "s") + ")";
			if (community.externalEdgeCount == 0L && community.internalEdgeCount > 0L)
			{
				return "Mostly self-contained around " + leader + "; "
						+ recommendation + ".";
			}
			if (community.externalEdgeCount > community.internalEdgeCount)
			{
				return "Boundary-heavy group around " + leader
						+ " with more outgoing cross-community edges than internal edges; "
						+ recommendation + ".";
			}
			if (community.activeNodeCount == 0)
			{
				return "Mapped community with no active saved edges yet; it is visible because the node map already contains these labels.";
			}
			return "Clustered around " + leader + " with "
					+ community.internalEdgeCount + " internal edge"
					+ (community.internalEdgeCount == 1L ? "" : "s")
					+ " and " + community.externalEdgeCount
					+ " outgoing external edge"
					+ (community.externalEdgeCount == 1L ? "" : "s")
					+ "; " + recommendation + ".";
		}

		private String nodeWhy(
				String name,
				String communityName,
				boolean isolate,
				boolean hub,
				boolean bridge,
				boolean recommendationSink,
				int neighborCount,
				int externalNeighborCount,
				int in,
				int out,
				int recommendedTargets)
		{
			if (isolate)
			{
				return name + " is mapped but has no active saved graph edges yet.";
			}
			if (bridge)
			{
				return name + " connects " + externalNeighborCount
						+ " neighbor community boundary point"
						+ (externalNeighborCount == 1 ? "" : "s")
						+ " while sitting in " + communityName + ".";
			}
			if (recommendationSink)
			{
				return name + " is a recommendation sink with "
						+ recommendedTargets + " recommendation edge"
						+ (recommendedTargets == 1 ? "" : "s")
						+ " and " + in + " incoming edge"
						+ (in == 1 ? "" : "s") + ".";
			}
			if (hub)
			{
				return name + " is a local hub with " + neighborCount
						+ " neighbor" + (neighborCount == 1 ? "" : "s")
						+ " and total degree " + (in + out) + ".";
			}
			return name + " is an active member of " + communityName
					+ " with " + in + " incoming and " + out
					+ " outgoing edge" + (out == 1 ? "" : "s") + ".";
		}

		private String nodeRole(
				boolean isolate,
				boolean hub,
				boolean bridge,
				boolean recommendationSink,
				int in,
				int out)
		{
			if (isolate)
			{
				return "isolate";
			}
			if (bridge && hub)
			{
				return "bridge hub";
			}
			if (recommendationSink && hub)
			{
				return "recommendation hub";
			}
			if (recommendationSink)
			{
				return "recommendation sink";
			}
			if (hub)
			{
				return "graph hub";
			}
			if (bridge)
			{
				return "bridge";
			}
			if (out > 0 && in == 0)
			{
				return "source actor";
			}
			return "active node";
		}

		private List<NodeScore> topNodesForIds(
				List<Integer> ids,
				Map<Integer, Integer> values,
				String singular,
				long denominator,
				int limit)
		{
			List<NodeScore> scores = new ArrayList<NodeScore>();
			for (Integer id : ids)
			{
				int count = value(values, id);
				String name = mapping.namesById.get(id);
				double share = denominator <= 0L ? 0.0 : count / (double) denominator;
				String detail = count + " " + singular
						+ (count == 1 ? "" : "s")
						+ " | in " + value(inDegree, id)
						+ " / out " + value(outDegree, id)
						+ " | " + percent(share);
				scores.add(new NodeScore(id, name, count, detail));
			}
			Collections.sort(scores, NodeScore.DESCENDING);
			if (scores.size() > limit)
			{
				return new ArrayList<NodeScore>(scores.subList(0, limit));
			}
			return scores;
		}

		private List<NodeScore> neighborScores(
				Set<Integer> neighbors,
				Integer communityId)
		{
			if (neighbors == null || neighbors.isEmpty())
			{
				return Collections.emptyList();
			}
			List<NodeScore> scores = new ArrayList<NodeScore>();
			for (Integer neighbor : neighbors)
			{
				Integer neighborCommunity = communityByNode.get(neighbor);
				String detail = "degree " + value(totalDegree, neighbor)
						+ " | " + (neighborCommunity == null
								? "unassigned"
								: neighborCommunity.equals(communityId)
										? "same community"
										: "community " + neighborCommunity);
				scores.add(new NodeScore(
						neighbor,
						mapping.namesById.get(neighbor),
						value(totalDegree, neighbor),
						detail));
			}
			Collections.sort(scores, NodeScore.DESCENDING);
			if (scores.size() > DETAIL_LIMIT)
			{
				return new ArrayList<NodeScore>(scores.subList(0, DETAIL_LIMIT));
			}
			return scores;
		}

		private int externalNeighborCount(
				Integer nodeId,
				Set<Integer> neighbors)
		{
			if (neighbors == null || neighbors.isEmpty())
			{
				return 0;
			}
			Integer communityId = communityByNode.get(nodeId);
			if (communityId == null)
			{
				return 0;
			}
			int count = 0;
			for (Integer neighbor : neighbors)
			{
				Integer neighborCommunity = communityByNode.get(neighbor);
				if (neighborCommunity != null
						&& !communityId.equals(neighborCommunity))
				{
					count++;
				}
			}
			return count;
		}

		private List<String> nodeNames(List<Integer> ids, int limit)
		{
			List<String> names = new ArrayList<String>();
			for (int i = 0; i < ids.size() && i < limit; i++)
			{
				Integer id = ids.get(i);
				String name = mapping.namesById.get(id);
				names.add((name == null ? "#" + id : name) + " (#" + id + ")");
			}
			return Collections.unmodifiableList(names);
		}

		private void rememberEdge(
				List<EdgeSample> samples,
				ParsedEdge edge,
				int sourceCommunityId,
				int targetCommunityId,
				boolean internal,
				boolean recommendationEdge)
		{
			if (samples.size() >= EDGE_SAMPLE_LIMIT)
			{
				return;
			}
			samples.add(new EdgeSample(
					edge.source,
					name(edge.source),
					edge.target,
					name(edge.target),
					sourceCommunityId,
					targetCommunityId,
					internal,
					recommendationEdge));
		}

		private String name(int nodeId)
		{
			String name = mapping.namesById.get(nodeId);
			return name == null ? "#" + nodeId : name;
		}

		private static final class CommunityAccumulator
		{
			static final Comparator<CommunityAccumulator> DESCENDING =
					new Comparator<CommunityAccumulator>()
					{
						public int compare(
								CommunityAccumulator left,
								CommunityAccumulator right)
						{
							int bySize = Integer.compare(
									right.nodeIds.size(), left.nodeIds.size());
							if (bySize != 0)
							{
								return bySize;
							}
							return left.name.compareToIgnoreCase(right.name);
						}
					};

			final int id;
			final String name;
			final List<Integer> nodeIds = new ArrayList<Integer>();
			final Map<Integer, Integer> recommendedTargetCounts =
					new HashMap<Integer, Integer>();
			final Map<Integer, Integer> originalTargetCounts =
					new HashMap<Integer, Integer>();
			final List<EdgeSample> internalEdgeSamples =
					new ArrayList<EdgeSample>();
			final List<EdgeSample> externalEdgeSamples =
					new ArrayList<EdgeSample>();
			int activeNodeCount;
			long internalEdgeCount;
			long externalEdgeCount;
			long incomingExternalEdgeCount;
			long recommendationEdgeCount;

			CommunityAccumulator(int id, String name)
			{
				this.id = id;
				this.name = name == null ? "Community " + id : name;
			}
		}

		private void computeWarnings()
		{
			if (mapping.namesById.isEmpty())
			{
				warnings.add("The node map is empty, so no graph can be interpreted.");
				healthScore -= 50;
			}
			if (mapping.malformedLines > 0)
			{
				warnings.add(mapping.malformedLines
						+ " malformed node-map line(s) were ignored.");
				healthScore -= 10;
			}
			if (mapping.duplicateIds > 0 || mapping.duplicateNames > 0)
			{
				warnings.add("The node map contains duplicate ids or names. The last value wins.");
				healthScore -= 12;
			}
			if (validEdgeCount == 0L)
			{
				warnings.add("No valid graph edges are present yet.");
				healthScore -= 35;
			}
			else if (validEdgeCount < 10L)
			{
				warnings.add("The graph has fewer than 10 valid edges. Community statistics are only a preview.");
				healthScore -= 14;
			}
			if (malformedEdgeLines > 0L || unknownNodeEdges > 0L)
			{
				warnings.add("Some edge lines were ignored because they were malformed or referenced unknown nodes.");
				healthScore -= 16;
			}
			if (!issueSamples.isEmpty())
			{
				warnings.add("Examples: " + issueSamples.toString());
			}
			if (unpairedTrailingEdge || edgePairMismatchCount > 0L)
			{
				warnings.add("The edge file does not fully match the current two-edge-per-target format.");
				healthScore -= 12;
			}
			if (isolatedMappedNodeCount > activeNodes.size() * 3 && mapping.namesById.size() > 20)
			{
				warnings.add("Most mapped nodes are isolates. This is normal early in a run but weak for community analysis.");
				healthScore -= 8;
			}
			if (estimatedCommunityCount <= 2 && activeNodes.size() >= 20 && validEdgeCount >= 20)
			{
				warnings.add("Community-collapse signal: "
						+ communityMethod + " groups the graph into only "
						+ estimatedCommunityCount + " communities.");
				healthScore -= 15;
			}
			if (!topRecommendedTargets.isEmpty() && recommendationEdgeCount > 0L)
			{
				NodeScore top = topRecommendedTargets.get(0);
				double share = top.value / (double) recommendationEdgeCount;
				if (share >= 0.45)
				{
					warnings.add("Recommendation concentration: "
							+ top.name + " receives " + percent(share)
							+ " of recommendation edges.");
					healthScore -= 16;
				}
			}
			healthScore = Math.max(0, Math.min(100, healthScore));
			if (warnings.isEmpty())
			{
				warnings.add("No structural issues detected in the current graph artifacts.");
			}
		}

		private Analysis finish()
		{
			computeRankings();
			computeExplorationDetails();
			if (warnings.isEmpty())
			{
				computeWarnings();
			}
			return new Analysis(
					mappingFile,
					edgeFile,
					true,
					mapping.namesById.size(),
					activeNodes.size(),
					isolatedMappedNodeCount,
					nonEmptyEdgeLines,
					validEdgeCount,
					uniqueDirectedEdges.size(),
					uniqueUndirectedEdgeCount,
					duplicateEdgeCount,
					malformedEdgeLines,
					unknownNodeEdges,
					selfLoopCount,
					originalEdgeCount,
					recommendationEdgeCount,
					recommendationChangedTargetCount,
					recommendationSameAsOriginalCount,
					edgePairMismatchCount,
					unpairedTrailingEdge,
					activeComponentCount,
					largestComponentSize,
					estimatedCommunityCount,
						activeDensity,
						recommendationDiversity,
						hubConcentration,
						healthScore,
						recommendedTargetCounts.size(),
						communityMethod,
						gephiCommunityCount,
						modularityScore,
						gephiImportedNodeCount,
						gephiImportedEdgeCount,
						topRecommendedTargets,
					topIncomingNodes,
					topHubs,
					topCommunities,
					communityDetails,
					nodeDetails,
					new ArrayList<String>(warnings));
		}
	}

	private static List<NodeScore> topNodes(
			Map<Integer, Integer> values,
			Map<Integer, String> namesById,
			String singular,
			long denominator,
			int limit)
	{
		List<NodeScore> scores = new ArrayList<NodeScore>();
		for (Map.Entry<Integer, Integer> entry : values.entrySet())
		{
			String name = namesById.get(entry.getKey());
			if (name == null)
			{
				name = "#" + entry.getKey();
			}
			double share = denominator <= 0L ? 0.0 : entry.getValue() / (double) denominator;
			String detail = entry.getValue() + " " + singular
					+ (entry.getValue() == 1 ? "" : "s")
					+ " | " + percent(share);
			scores.add(new NodeScore(entry.getKey(), name, entry.getValue(), detail));
		}
		Collections.sort(scores, NodeScore.DESCENDING);
		if (scores.size() > limit)
		{
			return new ArrayList<NodeScore>(scores.subList(0, limit));
		}
		return scores;
	}

	private static List<NodeScore> topCommunities(
			Map<Integer, Integer> communitySizes,
			Map<Integer, String> communityNames,
			Map<Integer, String> namesById,
			int activeNodeCount,
			String communityMethod,
			int limit)
	{
		List<NodeScore> scores = new ArrayList<NodeScore>();
		for (Map.Entry<Integer, Integer> entry : communitySizes.entrySet())
		{
			String leader = communityNames == null
					? null : communityNames.get(entry.getKey());
			if (leader == null || leader.length() == 0)
			{
				leader = namesById.get(entry.getKey());
			}
			if (leader == null || leader.length() == 0)
			{
				leader = "#" + entry.getKey();
			}
			double share = activeNodeCount <= 0 ? 0.0 : entry.getValue() / (double) activeNodeCount;
			scores.add(new NodeScore(
					entry.getKey(),
					leader,
					entry.getValue(),
					entry.getValue() + " graph node"
							+ (entry.getValue() == 1 ? "" : "s")
							+ " | " + percent(share)
							+ " | " + communityMethod));
		}
		Collections.sort(scores, NodeScore.DESCENDING);
		if (scores.size() > limit)
		{
			return new ArrayList<NodeScore>(scores.subList(0, limit));
		}
		return scores;
	}

	static final class Analysis
	{
		final File mappingFile;
		final File edgeFile;
		final boolean available;
		final int mappedNodeCount;
		final int activeNodeCount;
		final int isolatedMappedNodeCount;
		final long nonEmptyEdgeLines;
		final long validEdgeCount;
		final long uniqueDirectedEdgeCount;
		final long uniqueUndirectedEdgeCount;
		final long duplicateEdgeCount;
		final long malformedEdgeLines;
		final long unknownNodeEdges;
		final long selfLoopCount;
		final long originalEdgeCount;
		final long recommendationEdgeCount;
		final long recommendationChangedTargetCount;
		final long recommendationSameAsOriginalCount;
		final long edgePairMismatchCount;
		final boolean unpairedTrailingEdge;
		final int activeComponentCount;
		final int largestComponentSize;
			final int estimatedCommunityCount;
			final double activeDensity;
			final double recommendationDiversity;
			final double hubConcentration;
			final int healthScore;
			final int uniqueRecommendedTargets;
			final String communityMethod;
			final boolean gephiCommunityCount;
			final double modularityScore;
			final int gephiImportedNodeCount;
			final long gephiImportedEdgeCount;
			final List<NodeScore> topRecommendedTargets;
		final List<NodeScore> topIncomingNodes;
		final List<NodeScore> topHubs;
		final List<NodeScore> topCommunities;
		final List<CommunityDetail> communityDetails;
		final List<NodeDetail> nodeDetails;
		final List<String> warnings;

		private Analysis(
				File mappingFile,
				File edgeFile,
				boolean available,
				int mappedNodeCount,
				int activeNodeCount,
				int isolatedMappedNodeCount,
				long nonEmptyEdgeLines,
				long validEdgeCount,
				long uniqueDirectedEdgeCount,
				long uniqueUndirectedEdgeCount,
				long duplicateEdgeCount,
				long malformedEdgeLines,
				long unknownNodeEdges,
				long selfLoopCount,
				long originalEdgeCount,
				long recommendationEdgeCount,
				long recommendationChangedTargetCount,
				long recommendationSameAsOriginalCount,
				long edgePairMismatchCount,
				boolean unpairedTrailingEdge,
				int activeComponentCount,
					int largestComponentSize,
					int estimatedCommunityCount,
					double activeDensity,
					double recommendationDiversity,
					double hubConcentration,
					int healthScore,
					int uniqueRecommendedTargets,
					String communityMethod,
					boolean gephiCommunityCount,
					double modularityScore,
					int gephiImportedNodeCount,
					long gephiImportedEdgeCount,
					List<NodeScore> topRecommendedTargets,
				List<NodeScore> topIncomingNodes,
				List<NodeScore> topHubs,
				List<NodeScore> topCommunities,
				List<CommunityDetail> communityDetails,
				List<NodeDetail> nodeDetails,
				List<String> warnings)
		{
			this.mappingFile = mappingFile;
			this.edgeFile = edgeFile;
			this.available = available;
			this.mappedNodeCount = mappedNodeCount;
			this.activeNodeCount = activeNodeCount;
			this.isolatedMappedNodeCount = isolatedMappedNodeCount;
			this.nonEmptyEdgeLines = nonEmptyEdgeLines;
			this.validEdgeCount = validEdgeCount;
			this.uniqueDirectedEdgeCount = uniqueDirectedEdgeCount;
			this.uniqueUndirectedEdgeCount = uniqueUndirectedEdgeCount;
			this.duplicateEdgeCount = duplicateEdgeCount;
			this.malformedEdgeLines = malformedEdgeLines;
			this.unknownNodeEdges = unknownNodeEdges;
			this.selfLoopCount = selfLoopCount;
			this.originalEdgeCount = originalEdgeCount;
			this.recommendationEdgeCount = recommendationEdgeCount;
			this.recommendationChangedTargetCount = recommendationChangedTargetCount;
			this.recommendationSameAsOriginalCount = recommendationSameAsOriginalCount;
			this.edgePairMismatchCount = edgePairMismatchCount;
			this.unpairedTrailingEdge = unpairedTrailingEdge;
			this.activeComponentCount = activeComponentCount;
			this.largestComponentSize = largestComponentSize;
				this.estimatedCommunityCount = estimatedCommunityCount;
				this.activeDensity = activeDensity;
				this.recommendationDiversity = recommendationDiversity;
				this.hubConcentration = hubConcentration;
				this.healthScore = healthScore;
				this.uniqueRecommendedTargets = uniqueRecommendedTargets;
				this.communityMethod = communityMethod == null
						? "Not computed" : communityMethod;
				this.gephiCommunityCount = gephiCommunityCount;
				this.modularityScore = modularityScore;
				this.gephiImportedNodeCount = gephiImportedNodeCount;
				this.gephiImportedEdgeCount = gephiImportedEdgeCount;
				this.topRecommendedTargets = immutable(topRecommendedTargets);
			this.topIncomingNodes = immutable(topIncomingNodes);
			this.topHubs = immutable(topHubs);
			this.topCommunities = immutable(topCommunities);
			this.communityDetails = immutableCommunityDetails(communityDetails);
			this.nodeDetails = immutableNodeDetails(nodeDetails);
			this.warnings = warnings == null
					? Collections.<String>emptyList()
					: Collections.unmodifiableList(warnings);
		}

		static Analysis missing(File mappingFile, File edgeFile, String message)
		{
			List<String> warnings = new ArrayList<String>();
			warnings.add(message);
			return new Analysis(
					mappingFile,
					edgeFile,
					false,
					0,
					0,
					0,
					0L,
					0L,
					0L,
					0L,
					0L,
					0L,
					0L,
					0L,
					0L,
					0L,
					0L,
					0L,
					0L,
					false,
					0,
					0,
					0,
						0.0,
						0.0,
						0.0,
						0,
						0,
						"Not computed",
						false,
						Double.NaN,
						0,
						0L,
						Collections.<NodeScore>emptyList(),
					Collections.<NodeScore>emptyList(),
					Collections.<NodeScore>emptyList(),
					Collections.<NodeScore>emptyList(),
					Collections.<CommunityDetail>emptyList(),
					Collections.<NodeDetail>emptyList(),
					warnings);
		}

		String statusLabel()
		{
			if (!available)
			{
				return "WAITING FOR GRAPH DATA";
			}
			if (validEdgeCount == 0L)
			{
				return "NO EDGES YET";
			}
			if (malformedEdgeLines > 0L || unknownNodeEdges > 0L)
			{
				return "NEEDS ATTENTION";
			}
			if (healthScore < 70)
			{
				return "STRUCTURE WARNING";
			}
			return "GRAPH HEALTHY";
		}
	}

	static final class CommunityDetail
	{
		final int id;
		final String name;
		final int size;
		final int activeNodeCount;
		final long internalEdgeCount;
		final long externalEdgeCount;
		final long incomingExternalEdgeCount;
		final long recommendationEdgeCount;
		final String dominantRecommendedTarget;
		final int dominantRecommendedTargetCount;
		final String methodDetail;
		final List<NodeScore> topNodes;
		final List<NodeScore> topHubs;
		final List<NodeScore> topRecommendations;
		final List<String> nodePreview;
		final List<EdgeSample> internalEdgeSamples;
		final List<EdgeSample> externalEdgeSamples;
		final String why;

		private CommunityDetail(
				int id,
				String name,
				int size,
				int activeNodeCount,
				long internalEdgeCount,
				long externalEdgeCount,
				long incomingExternalEdgeCount,
				long recommendationEdgeCount,
				String dominantRecommendedTarget,
				int dominantRecommendedTargetCount,
				String methodDetail,
				List<NodeScore> topNodes,
				List<NodeScore> topHubs,
				List<NodeScore> topRecommendations,
				List<String> nodePreview,
				List<EdgeSample> internalEdgeSamples,
				List<EdgeSample> externalEdgeSamples,
				String why)
		{
			this.id = id;
			this.name = name == null ? "Community " + id : name;
			this.size = size;
			this.activeNodeCount = activeNodeCount;
			this.internalEdgeCount = internalEdgeCount;
			this.externalEdgeCount = externalEdgeCount;
			this.incomingExternalEdgeCount = incomingExternalEdgeCount;
			this.recommendationEdgeCount = recommendationEdgeCount;
			this.dominantRecommendedTarget =
					dominantRecommendedTarget == null ? "none" : dominantRecommendedTarget;
			this.dominantRecommendedTargetCount = dominantRecommendedTargetCount;
			this.methodDetail = methodDetail == null ? "" : methodDetail;
			this.topNodes = immutable(topNodes);
			this.topHubs = immutable(topHubs);
			this.topRecommendations = immutable(topRecommendations);
			this.nodePreview = immutableStrings(nodePreview);
			this.internalEdgeSamples = immutableEdges(internalEdgeSamples);
			this.externalEdgeSamples = immutableEdges(externalEdgeSamples);
			this.why = why == null ? "" : why;
		}
	}

	static final class NodeDetail
	{
		static final Comparator<NodeDetail> BY_NAME =
				new Comparator<NodeDetail>()
				{
					public int compare(NodeDetail left, NodeDetail right)
					{
						return left.name.compareToIgnoreCase(right.name);
					}
				};

		final int id;
		final String name;
		final int communityId;
		final String communityName;
		final int componentLeader;
		final int componentSize;
		final int inDegree;
		final int outDegree;
		final int totalDegree;
		final int originalTargetCount;
		final int recommendedTargetCount;
		final int neighborCount;
		final int externalNeighborCount;
		final boolean active;
		final boolean isolate;
		final boolean hub;
		final boolean bridge;
		final boolean recommendationSink;
		final String role;
		final List<NodeScore> neighbors;
		final String why;

		private NodeDetail(
				int id,
				String name,
				int communityId,
				String communityName,
				int componentLeader,
				int componentSize,
				int inDegree,
				int outDegree,
				int totalDegree,
				int originalTargetCount,
				int recommendedTargetCount,
				int neighborCount,
				int externalNeighborCount,
				boolean active,
				boolean isolate,
				boolean hub,
				boolean bridge,
				boolean recommendationSink,
				String role,
				List<NodeScore> neighbors,
				String why)
		{
			this.id = id;
			this.name = name == null ? "#" + id : name;
			this.communityId = communityId;
			this.communityName = communityName == null ? "Unassigned" : communityName;
			this.componentLeader = componentLeader;
			this.componentSize = componentSize;
			this.inDegree = inDegree;
			this.outDegree = outDegree;
			this.totalDegree = totalDegree;
			this.originalTargetCount = originalTargetCount;
			this.recommendedTargetCount = recommendedTargetCount;
			this.neighborCount = neighborCount;
			this.externalNeighborCount = externalNeighborCount;
			this.active = active;
			this.isolate = isolate;
			this.hub = hub;
			this.bridge = bridge;
			this.recommendationSink = recommendationSink;
			this.role = role == null ? "node" : role;
			this.neighbors = immutable(neighbors);
			this.why = why == null ? "" : why;
		}
	}

	static final class EdgeSample
	{
		final int sourceId;
		final String sourceName;
		final int targetId;
		final String targetName;
		final int sourceCommunityId;
		final int targetCommunityId;
		final boolean internal;
		final boolean recommendationEdge;

		private EdgeSample(
				int sourceId,
				String sourceName,
				int targetId,
				String targetName,
				int sourceCommunityId,
				int targetCommunityId,
				boolean internal,
				boolean recommendationEdge)
		{
			this.sourceId = sourceId;
			this.sourceName = sourceName == null ? "#" + sourceId : sourceName;
			this.targetId = targetId;
			this.targetName = targetName == null ? "#" + targetId : targetName;
			this.sourceCommunityId = sourceCommunityId;
			this.targetCommunityId = targetCommunityId;
			this.internal = internal;
			this.recommendationEdge = recommendationEdge;
		}

		String summary()
		{
			return sourceName + " -> " + targetName
					+ (recommendationEdge ? " (recommended)" : "");
		}
	}

	static final class NodeScore
	{
		static final Comparator<NodeScore> DESCENDING =
				new Comparator<NodeScore>()
				{
					public int compare(NodeScore left, NodeScore right)
					{
						int byValue = Integer.compare(right.value, left.value);
						if (byValue != 0)
						{
							return byValue;
						}
						return left.name.compareToIgnoreCase(right.name);
					}
				};

		final int id;
		final String name;
		final int value;
		final String detail;

		private NodeScore(int id, String name, int value, String detail)
		{
			this.id = id;
			this.name = name == null ? "#" + id : name;
			this.value = value;
			this.detail = detail == null ? "" : detail;
		}
	}

	private static List<NodeScore> immutable(List<NodeScore> input)
	{
		if (input == null || input.isEmpty())
		{
			return Collections.emptyList();
		}
		return Collections.unmodifiableList(new ArrayList<NodeScore>(input));
	}

	private static List<CommunityDetail> immutableCommunityDetails(
			List<CommunityDetail> input)
	{
		if (input == null || input.isEmpty())
		{
			return Collections.emptyList();
		}
		return Collections.unmodifiableList(new ArrayList<CommunityDetail>(input));
	}

	private static List<NodeDetail> immutableNodeDetails(List<NodeDetail> input)
	{
		if (input == null || input.isEmpty())
		{
			return Collections.emptyList();
		}
		return Collections.unmodifiableList(new ArrayList<NodeDetail>(input));
	}

	private static List<String> immutableStrings(List<String> input)
	{
		if (input == null || input.isEmpty())
		{
			return Collections.emptyList();
		}
		return Collections.unmodifiableList(new ArrayList<String>(input));
	}

	private static List<EdgeSample> immutableEdges(List<EdgeSample> input)
	{
		if (input == null || input.isEmpty())
		{
			return Collections.emptyList();
		}
		return Collections.unmodifiableList(new ArrayList<EdgeSample>(input));
	}
}
