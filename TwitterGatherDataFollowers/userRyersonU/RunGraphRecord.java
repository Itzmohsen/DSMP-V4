package TwitterGatherDataFollowers.userRyersonU;

import java.awt.Color;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import javax.imageio.ImageIO;

/**
 * A run's graph in its run record (the {@code graph} folder).
 *
 * <pre>
 * graph.json      the analysis: views, tests, findings, parameters
 * nodes.csv       every node with its attributes, centralities and position
 * edges.csv       every edge with its layer, rank, score and community relation
 * graph.gexf      for Gephi (GEXF 1.2, with positions, colours and sizes)
 * graph.graphml   for NetworkX, igraph, Cytoscape and yEd
 * graph.svg/.png  the figure (with the final graph output)
 * </pre>
 *
 * The data and analysis are always written; the layout and its figures when
 * the run asked for the final graph output. Records written before run
 * graphs existed are rebuilt from their recommendations.csv and dataset.
 */
final class RunGraphRecord
{
	static final String FOLDER = "graph";
	private static final Object CACHE_LOCK = new Object();
	private static final LinkedHashMap<String,Loaded> CACHE = new LinkedHashMap<String,Loaded>();
	private static final int CACHE_SIZE = 6;
	private static volatile Loaded latest;

	private RunGraphRecord()
	{
	}

	/** A run graph with its analysis and (possibly null) positions. */
	static final class Loaded
	{
		final RunGraph graph;
		final RunGraphAnalysis analysis;
		final double[] x;
		final double[] y;
		final File directory;
		/** True when rebuilt from an older record's files rather than saved with the run. */
		final boolean reconstructed;

		Loaded(RunGraph graph, RunGraphAnalysis analysis, double[] x, double[] y, File directory,
				boolean reconstructed)
		{
			this.graph = graph;
			this.analysis = analysis;
			this.x = x;
			this.y = y;
			this.directory = directory;
			this.reconstructed = reconstructed;
		}

		boolean hasLayout()
		{
			return x != null && y != null;
		}

		Loaded withLayout(double[] newX, double[] newY)
		{
			return new Loaded(graph, analysis, newX, newY, directory, reconstructed);
		}
	}

	// ------------------------------------------------------------ writing

	/**
	 * Writes the run's graph into its record. Returns the analysis, or null
	 * when the run was not prepared for evaluation.
	 */
	static RunGraphAnalysis write(File runDirectory, RunGraph graph, boolean figures) throws IOException
	{
		if (graph == null)
		{
			return null;
		}
		File folder = new File(runDirectory, FOLDER);
		if (!folder.isDirectory() && !folder.mkdirs())
		{
			throw new IOException("cannot create " + folder.getPath());
		}
		RunGraphAnalysis analysis = RunGraphAnalysis.of(graph);
		RunGraphAnalysis.GraphLayoutResult layout = figures ? layout(graph, analysis) : null;
		writeText(new File(folder, "graph.json"), JsonText.of(analysis.toJson(layout)));
		writeNodes(new File(folder, "nodes.csv"), analysis, layout);
		writeEdges(new File(folder, "edges.csv"), graph);
		if (layout != null)
		{
			writeText(new File(folder, "graph.gexf"), gexf(analysis, layout));
			writeText(new File(folder, "graph.graphml"), graphml(analysis, layout));
			GraphRenderer.Style style = new GraphRenderer.Style();
			style.hideIsolated = true;
			writeText(new File(folder, "graph.svg"), GraphRenderer.svg(graph, analysis, layout.x, layout.y, 1600, 1000,
					style));
			ImageIO.write(GraphRenderer.image(graph, analysis, layout.x, layout.y, 1600, 1000, 2.0, style), "png",
					new File(folder, "graph.png"));
		}
		Loaded loaded = new Loaded(graph, analysis, layout == null ? null : layout.x, layout == null ? null : layout.y,
				runDirectory, false);
		remember(folder, loaded);
		latest = loaded;
		return analysis;
	}

	/** The graph of the run recorded last in this session, or null. */
	static Loaded latest()
	{
		return latest;
	}

	/** Forgets the graphs loaded in this session (they are read again from their records). */
	static void clearCache()
	{
		synchronized (CACHE_LOCK)
		{
			CACHE.clear();
		}
	}

	/** Makes a graph the latest (the run graph Evidence Studio reads). */
	static void publish(Loaded loaded)
	{
		latest = loaded;
	}

	/** The deterministic ForceAtlas2 layout of a graph, with overlaps removed. */
	static RunGraphAnalysis.GraphLayoutResult layout(RunGraph graph, RunGraphAnalysis analysis)
	{
		GraphLayout layout = start(graph);
		layout.run(GraphLayout.budget(graph.size(), graph.allEdges().size()));
		finish(layout, graph, analysis);
		double[][] positions = layout.positions();
		return new RunGraphAnalysis.GraphLayoutResult(positions[0], positions[1],
				GraphLayout.Settings.forSize(graph.size()).describe(), layout.iterations(), layout.isConverged());
	}

	/** A layout of the whole network, ready to step. */
	static GraphLayout start(RunGraph graph)
	{
		List<RunGraph.Edge> edges = graph.allEdges();
		int[] sources = new int[edges.size()];
		int[] targets = new int[edges.size()];
		double[] weights = new double[edges.size()];
		for (int index = 0; index < edges.size(); index++)
		{
			sources[index] = edges.get(index).source;
			targets[index] = edges.get(index).target;
			weights[index] = edges.get(index).weight();
		}
		long[] seeds = new long[graph.size()];
		for (int node = 0; node < seeds.length; node++)
		{
			seeds[node] = SimulationSeeds.forName(graph.name(node));
		}
		return new GraphLayout(graph.size(), sources, targets, weights, seeds,
				GraphLayout.Settings.forSize(graph.size()));
	}

	/** Removes overlaps at the figure's scale (nodes sized by degree on a 1300-unit-wide canvas). */
	static void finish(GraphLayout layout, RunGraph graph, RunGraphAnalysis analysis)
	{
		double perPixel = layout.extent() / 1300.0;
		double[] metric = GraphRenderer.sizeMetric(analysis, GraphRenderer.SizeBy.DEGREE);
		double[] radii = new double[graph.size()];
		for (int node = 0; node < radii.length; node++)
		{
			radii[node] = (GraphRenderer.radius(graph, metric[node], node) + 1.5) * perPixel;
		}
		layout.preventOverlap(radii, 300);
	}

	static final String[] NODE_COLUMNS = {
		"id", "name", "kind", "community", "target", "candidate", "followers", "recommended", "adopted",
		"recommendations_out", "degree", "pagerank", "pagerank_observed", "attention_gained", "betweenness",
		"core", "participation", "within_module_z", "role", "detected_community", "recommendation_community",
		"x", "y"
	};

	private static void writeNodes(File file, RunGraphAnalysis analysis, RunGraphAnalysis.GraphLayoutResult layout)
			throws IOException
	{
		RunGraph graph = analysis.graph;
		int[] out = new int[graph.size()];
		for (RunGraph.Edge edge : graph.edges(RunGraph.Layer.RECOMMEND))
		{
			out[edge.source]++;
		}
		Writer writer = open(file);
		try
		{
			writer.write(JsonText.csvLine((Object[])NODE_COLUMNS));
			for (int node = 0; node < graph.size(); node++)
			{
				int community = graph.community(node);
				writer.write(JsonText.csvLine(Integer.valueOf(node), graph.name(node),
						graph.kind(node).name().toLowerCase(Locale.US),
						community < 0 ? "" : graph.communityName(community),
						Integer.valueOf(graph.isTarget(node) ? 1 : 0), Integer.valueOf(graph.isCandidate(node) ? 1 : 0),
						Integer.valueOf(analysis.followers[node]), Integer.valueOf(analysis.recommendedIn[node]),
						Integer.valueOf(analysis.adoptedIn[node]), Integer.valueOf(out[node]),
						Integer.valueOf(analysis.degreeFull[node]), exact(analysis.pageRank[node]),
						exact(analysis.pageRankObserved[node]), exact(analysis.attentionGained(node)),
						exact(analysis.betweenness.values[node]), Integer.valueOf(analysis.core[node]),
						exact(analysis.participation[node]), exact(analysis.withinModuleZ[node]),
						analysis.roles[node], Integer.valueOf(analysis.full.communities.membership[node]),
						Integer.valueOf(analysis.recommended.communities.membership[node]),
						layout == null ? "" : exact(layout.x[node]), layout == null ? "" : exact(layout.y[node])));
			}
		}
		finally
		{
			writer.close();
		}
	}

	static final String[] EDGE_COLUMNS = {"source", "target", "layer", "rank", "score", "weight", "inside"};

	private static void writeEdges(File file, RunGraph graph) throws IOException
	{
		Writer writer = open(file);
		try
		{
			writer.write(JsonText.csvLine((Object[])EDGE_COLUMNS));
			for (RunGraph.Edge edge : graph.allEdges())
			{
				Boolean inside = edge.layer == RunGraph.Layer.FOLLOW ? null : graph.inside(edge);
				writer.write(JsonText.csvLine(graph.name(edge.source), graph.name(edge.target),
						edge.layer.name().toLowerCase(Locale.US),
						edge.layer == RunGraph.Layer.FOLLOW ? "" : Integer.valueOf(edge.rank), exact(edge.score),
						exact(edge.weight()),
						inside == null ? "" : Integer.valueOf(inside.booleanValue() ? 1 : 0)));
			}
		}
		finally
		{
			writer.close();
		}
	}

	// ------------------------------------------------------------ GEXF and GraphML

	/** The graph as GEXF 1.2 (Gephi's format), with positions, community colours and sizes. */
	static String gexf(RunGraphAnalysis analysis, RunGraphAnalysis.GraphLayoutResult layout)
	{
		RunGraph graph = analysis.graph;
		StringBuilder out = new StringBuilder();
		out.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
		out.append("<gexf xmlns=\"http://www.gexf.net/1.2draft\" xmlns:viz=\"http://www.gexf.net/1.2draft/viz\"")
				.append(" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"")
				.append(" xsi:schemaLocation=\"http://www.gexf.net/1.2draft http://www.gexf.net/1.2draft/gexf.xsd\"")
				.append(" version=\"1.2\">\n");
		out.append("  <meta>\n    <creator>DSMP V4</creator>\n    <description>")
				.append(xml("Run " + graph.runId + ": " + graph.algorithm + " on " + graph.dataset
						+ " (follows, top-" + graph.cutoff + " recommendations, adoptions)"))
				.append("</description>\n  </meta>\n");
		out.append("  <graph defaultedgetype=\"directed\" mode=\"static\">\n");
		out.append("    <attributes class=\"node\">\n");
		String[][] nodeAttributes = {
			{"kind", "string"}, {"community", "string"}, {"target", "boolean"}, {"candidate", "boolean"},
			{"followers", "integer"}, {"recommended", "integer"}, {"pagerank", "double"},
			{"attention_gained", "double"}, {"betweenness", "double"}, {"core", "integer"}, {"role", "string"},
			{"detected_community", "integer"}
		};
		for (int index = 0; index < nodeAttributes.length; index++)
		{
			out.append("      <attribute id=\"").append(index).append("\" title=\"").append(nodeAttributes[index][0])
					.append("\" type=\"").append(nodeAttributes[index][1]).append("\"/>\n");
		}
		out.append("    </attributes>\n    <attributes class=\"edge\">\n");
		String[][] edgeAttributes = {{"layer", "string"}, {"rank", "integer"}, {"score", "double"},
			{"inside", "string"}};
		for (int index = 0; index < edgeAttributes.length; index++)
		{
			out.append("      <attribute id=\"").append(index).append("\" title=\"").append(edgeAttributes[index][0])
					.append("\" type=\"").append(edgeAttributes[index][1]).append("\"/>\n");
		}
		out.append("    </attributes>\n    <nodes>\n");
		double[] metric = GraphRenderer.sizeMetric(analysis, GraphRenderer.SizeBy.DEGREE);
		for (int node = 0; node < graph.size(); node++)
		{
			int community = graph.community(node);
			Object[] values = {graph.kind(node).name().toLowerCase(Locale.US),
				community < 0 ? "" : graph.communityName(community), Boolean.valueOf(graph.isTarget(node)),
				Boolean.valueOf(graph.isCandidate(node)), Integer.valueOf(analysis.followers[node]),
				Integer.valueOf(analysis.recommendedIn[node]), Double.valueOf(analysis.pageRank[node]),
				Double.valueOf(analysis.attentionGained(node)), Double.valueOf(analysis.betweenness.values[node]),
				Integer.valueOf(analysis.core[node]), analysis.roles[node],
				Integer.valueOf(analysis.full.communities.membership[node])};
			out.append("      <node id=\"n").append(node).append("\" label=\"").append(xml(graph.name(node)))
					.append("\">\n        <attvalues>\n");
			for (int index = 0; index < values.length; index++)
			{
				out.append("          <attvalue for=\"").append(index).append("\" value=\"")
						.append(xml(String.valueOf(values[index]))).append("\"/>\n");
			}
			out.append("        </attvalues>\n");
			Color color = graph.isAccount(node) ? GraphRenderer.accountColor(graph, node, false)
					: GraphRenderer.category(community, false);
			out.append("        <viz:color r=\"").append(color.getRed()).append("\" g=\"").append(color.getGreen())
					.append("\" b=\"").append(color.getBlue()).append("\"/>\n");
			out.append("        <viz:position x=\"").append(number(layout.x[node])).append("\" y=\"")
					.append(number(-layout.y[node])).append("\" z=\"0.0\"/>\n");
			out.append("        <viz:size value=\"").append(number(GraphRenderer.radius(graph, metric[node], node)))
					.append("\"/>\n      </node>\n");
		}
		out.append("    </nodes>\n    <edges>\n");
		int id = 0;
		for (RunGraph.Edge edge : graph.allEdges())
		{
			Boolean inside = edge.layer == RunGraph.Layer.FOLLOW ? null : graph.inside(edge);
			out.append("      <edge id=\"e").append(id++).append("\" source=\"n").append(edge.source)
					.append("\" target=\"n").append(edge.target).append("\" weight=\"").append(number(edge.weight()))
					.append("\">\n        <attvalues>\n");
			out.append("          <attvalue for=\"0\" value=\"").append(edge.layer.name().toLowerCase(Locale.US))
					.append("\"/>\n");
			if (edge.layer != RunGraph.Layer.FOLLOW)
			{
				out.append("          <attvalue for=\"1\" value=\"").append(edge.rank).append("\"/>\n");
				out.append("          <attvalue for=\"2\" value=\"").append(number(edge.score)).append("\"/>\n");
			}
			out.append("          <attvalue for=\"3\" value=\"")
					.append(inside == null ? "" : inside.booleanValue() ? "inside" : "across").append("\"/>\n");
			out.append("        </attvalues>\n      </edge>\n");
		}
		out.append("    </edges>\n  </graph>\n</gexf>\n");
		return out.toString();
	}

	/** The graph as GraphML with typed attributes (positions included). */
	static String graphml(RunGraphAnalysis analysis, RunGraphAnalysis.GraphLayoutResult layout)
	{
		RunGraph graph = analysis.graph;
		StringBuilder out = new StringBuilder();
		out.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
		out.append("<graphml xmlns=\"http://graphml.graphdrawing.org/xmlns\"")
				.append(" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"")
				.append(" xsi:schemaLocation=\"http://graphml.graphdrawing.org/xmlns")
				.append(" http://graphml.graphdrawing.org/xmlns/1.0/graphml.xsd\">\n");
		String[][] nodeKeys = {
			{"label", "string"}, {"kind", "string"}, {"community", "string"}, {"target", "boolean"},
			{"candidate", "boolean"}, {"pagerank", "double"}, {"betweenness", "double"}, {"core", "int"},
			{"role", "string"}, {"detected_community", "int"}, {"x", "double"}, {"y", "double"}
		};
		for (int index = 0; index < nodeKeys.length; index++)
		{
			out.append("  <key id=\"v").append(index).append("\" for=\"node\" attr.name=\"").append(nodeKeys[index][0])
					.append("\" attr.type=\"").append(nodeKeys[index][1]).append("\"/>\n");
		}
		String[][] edgeKeys = {{"layer", "string"}, {"rank", "int"}, {"score", "double"}, {"weight", "double"},
			{"inside", "string"}};
		for (int index = 0; index < edgeKeys.length; index++)
		{
			out.append("  <key id=\"e").append(index).append("\" for=\"edge\" attr.name=\"").append(edgeKeys[index][0])
					.append("\" attr.type=\"").append(edgeKeys[index][1]).append("\"/>\n");
		}
		out.append("  <graph id=\"run\" edgedefault=\"directed\">\n");
		for (int node = 0; node < graph.size(); node++)
		{
			int community = graph.community(node);
			Object[] values = {graph.name(node), graph.kind(node).name().toLowerCase(Locale.US),
				community < 0 ? "" : graph.communityName(community), Boolean.valueOf(graph.isTarget(node)),
				Boolean.valueOf(graph.isCandidate(node)), number(analysis.pageRank[node]),
				number(analysis.betweenness.values[node]), Integer.valueOf(analysis.core[node]), analysis.roles[node],
				Integer.valueOf(analysis.full.communities.membership[node]),
				layout == null ? null : number(layout.x[node]), layout == null ? null : number(layout.y[node])};
			out.append("    <node id=\"n").append(node).append("\">\n");
			for (int index = 0; index < values.length; index++)
			{
				if (values[index] == null)
				{
					continue;
				}
				out.append("      <data key=\"v").append(index).append("\">")
						.append(xml(String.valueOf(values[index]))).append("</data>\n");
			}
			out.append("    </node>\n");
		}
		int id = 0;
		for (RunGraph.Edge edge : graph.allEdges())
		{
			Boolean inside = edge.layer == RunGraph.Layer.FOLLOW ? null : graph.inside(edge);
			out.append("    <edge id=\"e").append(id++).append("\" source=\"n").append(edge.source)
					.append("\" target=\"n").append(edge.target).append("\">\n");
			out.append("      <data key=\"e0\">").append(edge.layer.name().toLowerCase(Locale.US)).append("</data>\n");
			if (edge.layer != RunGraph.Layer.FOLLOW)
			{
				out.append("      <data key=\"e1\">").append(edge.rank).append("</data>\n");
				out.append("      <data key=\"e2\">").append(number(edge.score)).append("</data>\n");
			}
			out.append("      <data key=\"e3\">").append(number(edge.weight())).append("</data>\n");
			if (inside != null)
			{
				out.append("      <data key=\"e4\">").append(inside.booleanValue() ? "inside" : "across")
						.append("</data>\n");
			}
			out.append("    </edge>\n");
		}
		out.append("  </graph>\n</graphml>\n");
		return out.toString();
	}

	// ------------------------------------------------------------ reading

	/** Whether a run record has a saved graph. */
	static boolean hasGraph(File runDirectory)
	{
		return runDirectory != null && new File(new File(runDirectory, FOLDER), "nodes.csv").isFile();
	}

	/**
	 * Loads a run's graph (saved, or rebuilt from an older record), analysed
	 * with the current code. Null when the record has no lists to build from.
	 */
	static Loaded load(File runDirectory) throws IOException
	{
		File folder = new File(runDirectory, FOLDER);
		String key = folder.getAbsolutePath();
		synchronized (CACHE_LOCK)
		{
			Loaded cached = CACHE.get(key);
			if (cached != null)
			{
				return cached;
			}
		}
		Loaded loaded = hasGraph(runDirectory) ? read(runDirectory) : reconstruct(runDirectory);
		if (loaded != null)
		{
			remember(folder, loaded);
		}
		return loaded;
	}

	/** Remembers a layout computed later (for example in the Explorer) for this session. */
	static void rememberLayout(Loaded loaded, double[] x, double[] y)
	{
		if (loaded == null || loaded.directory == null)
		{
			return;
		}
		remember(new File(loaded.directory, FOLDER), loaded.withLayout(x, y));
	}

	private static void remember(File folder, Loaded loaded)
	{
		synchronized (CACHE_LOCK)
		{
			CACHE.remove(folder.getAbsolutePath());
			CACHE.put(folder.getAbsolutePath(), loaded);
			while (CACHE.size() > CACHE_SIZE)
			{
				CACHE.remove(CACHE.keySet().iterator().next());
			}
		}
	}

	private static Loaded read(File runDirectory) throws IOException
	{
		File folder = new File(runDirectory, FOLDER);
		Object json = JsonText.read(new File(folder, "graph.json"));
		String candidateType = JsonText.string(json, "candidateType");
		RunGraph.Builder builder = new RunGraph.Builder(JsonText.string(json, "runId"),
				JsonText.string(json, "algorithm"), JsonText.string(json, "dataset"),
				"account".equals(candidateType) ? RecommendationCandidateType.FOLLOWEE : RecommendationCandidateType.USER,
				(int)JsonText.number(json, "k"));
		List<Map<String,String>> nodes = JsonText.readCsv(new File(folder, "nodes.csv"));
		double[] x = new double[nodes.size()];
		double[] y = new double[nodes.size()];
		boolean positioned = !nodes.isEmpty();
		for (Map<String,String> row : nodes)
		{
			String name = row.get("name");
			String kind = row.get("kind");
			String community = row.get("community");
			if ("account".equals(kind))
			{
				builder.account(name);
			}
			else
			{
				builder.user(name, community == null || community.length() == 0 ? null : community);
				if ("both".equals(kind))
				{
					builder.account(name);
				}
			}
			builder.candidate(name, "1".equals(row.get("candidate")));
			if ("1".equals(row.get("target")))
			{
				builder.target(name, community == null || community.length() == 0 ? null : community);
			}
		}
		//Adoptions are not read back: the builder recomputes them from the lists, as the user agents chose them.
		TreeMap<String,List<String[]>> lists = new TreeMap<String,List<String[]>>();
		for (Map<String,String> row : JsonText.readCsv(new File(folder, "edges.csv")))
		{
			if ("recommend".equals(row.get("layer")))
			{
				List<String[]> list = lists.get(row.get("source"));
				if (list == null)
				{
					list = new ArrayList<String[]>();
					lists.put(row.get("source"), list);
				}
				list.add(new String[] {row.get("target"), row.get("rank"), row.get("score")});
			}
		}
		for (Map.Entry<String,List<String[]>> list : lists.entrySet())
		{
			List<String> candidates = new ArrayList<String>();
			List<Double> scores = new ArrayList<Double>();
			for (String[] entry : list.getValue())
			{
				candidates.add(entry[0]);
				scores.add(Double.valueOf(JsonText.parseDouble(entry[2])));
			}
			builder.list(list.getKey(), candidates, scores, null);
		}
		RunGraph graph = builder.build();
		for (Map<String,String> row : nodes)
		{
			int node = graph.indexOf(row.get("name"));
			double px = JsonText.parseDouble(row.get("x"));
			double py = JsonText.parseDouble(row.get("y"));
			if (node < 0 || Double.isNaN(px) || Double.isNaN(py))
			{
				positioned = false;
				continue;
			}
			x[node] = px;
			y[node] = py;
		}
		if (graph.size() != nodes.size())
		{
			positioned = false;
		}
		RunGraphAnalysis analysis = RunGraphAnalysis.of(graph);
		return new Loaded(graph, analysis, positioned ? x : null, positioned ? y : null, runDirectory, false);
	}

	/**
	 * Rebuilds the graph of a record written before run graphs existed: the
	 * lists from recommendations.csv, every user's followee from the run's
	 * dataset (or, when it is gone, from the lists). The candidate set is then
	 * every user (or account), which slightly overstates the run's own.
	 */
	private static Loaded reconstruct(File runDirectory) throws IOException
	{
		File recommendations = new File(runDirectory, "recommendations.csv");
		File manifestFile = new File(runDirectory, "manifest.json");
		if (!recommendations.isFile() || !manifestFile.isFile())
		{
			return null;
		}
		Object manifest = JsonText.read(manifestFile);
		Object quality = new File(runDirectory, "quality.json").isFile()
				? JsonText.read(new File(runDirectory, "quality.json")) : null;
		boolean accounts = quality != null && "followee".equalsIgnoreCase(JsonText.string(quality, "candidateType"));
		int k = quality == null ? 3 : (int)Math.max(1, JsonText.number(quality, "k"));
		RunGraph.Builder builder = new RunGraph.Builder(JsonText.string(manifest, "runId"),
				JsonText.string(manifest, "properties.algorithm"), JsonText.string(manifest, "properties.datasetName"),
				accounts ? RecommendationCandidateType.FOLLOWEE : RecommendationCandidateType.USER, k);
		TreeMap<String,String> labels = new TreeMap<String,String>();
		File dataset = new File(JsonText.string(manifest, "properties.dataset"));
		if (dataset.isFile())
		{
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(dataset),
					StandardCharsets.UTF_8), 1 << 16))
			{
				String line;
				boolean first = true;
				while ((line = reader.readLine()) != null)
				{
					if (first && line.length() > 0 && line.charAt(0) == '\uFEFF')
					{
						line = line.substring(1);
					}
					first = false;
					String[] fields = line.split("\t", 6);
					if (fields.length >= 5 && fields[4].length() > 0 && fields[0].length() > 0
							&& !labels.containsKey(fields[4]))
					{
						labels.put(fields[4], fields[0]);
					}
				}
			}
		}
		TreeMap<String,List<String>> candidates = new TreeMap<String,List<String>>();
		TreeMap<String,List<Double>> scores = new TreeMap<String,List<Double>>();
		for (Map<String,String> row : JsonText.readCsv(recommendations))
		{
			String target = row.get("target");
			String candidate = row.get("candidate");
			if (target == null || candidate == null)
			{
				continue;
			}
			if (!labels.containsKey(target) && row.get("target_followee") != null
					&& row.get("target_followee").length() > 0)
			{
				labels.put(target, row.get("target_followee"));
			}
			if (!accounts && !labels.containsKey(candidate) && row.get("candidate_followee") != null
					&& row.get("candidate_followee").length() > 0)
			{
				labels.put(candidate, row.get("candidate_followee"));
			}
			List<String> list = candidates.get(target);
			if (list == null)
			{
				list = new ArrayList<String>();
				candidates.put(target, list);
				scores.put(target, new ArrayList<Double>());
			}
			list.add(candidate);
			scores.get(target).add(Double.valueOf(JsonText.parseDouble(row.get("score"))));
		}
		if (candidates.isEmpty())
		{
			return null;
		}
		for (Map.Entry<String,String> label : labels.entrySet())
		{
			builder.user(label.getKey(), label.getValue());
			builder.candidate(label.getKey(), !accounts);
			if (accounts)
			{
				builder.candidate(label.getValue(), true);
			}
		}
		for (Map.Entry<String,List<String>> list : candidates.entrySet())
		{
			builder.target(list.getKey(), labels.get(list.getKey()));
			builder.list(list.getKey(), list.getValue(), scores.get(list.getKey()), labels);
		}
		RunGraph graph = builder.build();
		return new Loaded(graph, RunGraphAnalysis.of(graph), null, null, runDirectory, true);
	}

	// ------------------------------------------------------------ helpers

	private static Writer open(File file) throws IOException
	{
		return new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8));
	}

	private static void writeText(File file, String text) throws IOException
	{
		Writer writer = open(file);
		try
		{
			writer.write(text);
		}
		finally
		{
			writer.close();
		}
	}

	/** A double in full (round-trip) precision, empty when undefined. */
	static String exact(double value)
	{
		return Double.isNaN(value) || Double.isInfinite(value) ? "" : Double.toString(value);
	}

	private static String number(double value)
	{
		if (Double.isNaN(value) || Double.isInfinite(value))
		{
			return "0";
		}
		return String.format(Locale.US, "%.6g", value);
	}

	static String xml(String text)
	{
		return GraphRenderer.SvgCanvas.escape(text == null ? "" : text);
	}
}
