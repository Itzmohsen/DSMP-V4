package TwitterGatherDataFollowers.userRyersonU;

import java.awt.BorderLayout;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.swing.JComponent;
import javax.swing.JPanel;

/**
 * Communities: whether the communities the recommendations form are the real
 * ones (Leiden on the recommendation network against the followed accounts),
 * and where recommendations flow between the real communities.
 */
final class GraphCommunitiesPage extends GraphInsightsPanel.Page
{
	private static final long serialVersionUID = 1L;
	private static final int ROWS = 14;

	private final NodeLabUi.Page page = new NodeLabUi.Page(1360);
	private RunGraph shownGraph;

	GraphCommunitiesPage(GraphInsightsPanel panel)
	{
	}

	void show(RunGraphRecord.Loaded loaded, String message)
	{
		if (loaded == null)
		{
			shownGraph = null;
			removeAll();
			add(GraphInsightsPanel.waiting(message), BorderLayout.CENTER);
			revalidate();
			repaint();
			return;
		}
		if (loaded.graph == shownGraph)
		{
			return;
		}
		shownGraph = loaded.graph;
		removeAll();
		page.clear();
		build(loaded);
		add(NodeLabUi.scroll(page), BorderLayout.CENTER);
		revalidate();
		repaint();
	}

	private void build(RunGraphRecord.Loaded loaded)
	{
		RunGraph graph = loaded.graph;
		RunGraphAnalysis a = loaded.analysis;
		boolean night = NodeLabUi.night();
		NetworkStatistics.Agreement recovery = a.recommended.recovery;
		NodeLabUi.Hero hero = new NodeLabUi.Hero("Communities",
				"Do the recommendations recover the real communities?",
				"Leiden finds the communities of the recommendation network (each target linked to its top-"
						+ graph.cutoff + ", weighted by rank). If the recommender captures who belongs together,"
						+ " those communities match the real ones, the followers of each account.",
				NebulaDesign.INFO);
		hero.chip(GraphInsightsPanel.testChip(a.recoveryPermutationP, recovery.ami > 0, "Better than chance",
				"Worse than chance"));
		hero.chip(new NodeLabUi.Chip(a.recoveryNodes + " nodes compared", NodeLabUi.Tone.NEUTRAL, false));
		page.section(hero);

		JPanel tiles = NodeLabUi.grid(200, 5, 12);
		tiles.add(new NodeLabUi.Metric("AMI", GraphInsightsPanel.fixed(recovery.ami, 3),
				"0 chance · 1 identical", NebulaDesign.INFO));
		tiles.add(new NodeLabUi.Metric("ARI", GraphInsightsPanel.fixed(recovery.ari, 3),
				"pairs placed alike, chance-corrected", NebulaDesign.ACCENT));
		tiles.add(new NodeLabUi.Metric("NMI", GraphInsightsPanel.fixed(recovery.nmi, 3),
				"not chance-corrected", NebulaDesign.PURPLE));
		tiles.add(new NodeLabUi.Metric("Leiden communities", Integer.toString(a.recommended.activeCommunities),
				"modularity " + GraphInsightsPanel.fixed(a.recommended.communities.quality, 3), NebulaDesign.SUCCESS));
		tiles.add(new NodeLabUi.Metric("Real communities", Integer.toString(graph.communityCount()),
				"followed accounts", NebulaDesign.PINK));
		page.section(tiles);

		page.section(contingency(loaded));
		page.section(flows(loaded, night));
		page.section(table(loaded));
	}

	/** Detected communities (rows) against real communities (columns). */
	private JComponent contingency(RunGraphRecord.Loaded loaded)
	{
		RunGraph graph = loaded.graph;
		RunGraphAnalysis a = loaded.analysis;
		boolean night = NodeLabUi.night();
		int[] membership = a.recommended.communities.membership;
		int detected = a.recommended.communities.communities;
		int real = graph.communityCount();
		int[][] counts = new int[detected][real];
		int[] sizes = new int[detected];
		boolean[] active = activeInRecommendations(graph);
		for (int node = 0; node < graph.size(); node++)
		{
			if (active[node] && graph.community(node) >= 0)
			{
				counts[membership[node]][graph.community(node)]++;
				sizes[membership[node]]++;
			}
		}
		List<Integer> order = new ArrayList<Integer>();
		for (int community = 0; community < detected; community++)
		{
			if (sizes[community] > 0)
			{
				order.add(Integer.valueOf(community));
			}
		}
		int shownRows = Math.min(ROWS, order.size());
		boolean other = order.size() > ROWS;
		String[] rows = new String[shownRows + (other ? 1 : 0)];
		double[][] values = new double[rows.length][real];
		String[][] text = new String[rows.length][real];
		for (int row = 0; row < rows.length; row++)
		{
			int[] line = new int[real];
			int total = 0;
			if (row < shownRows)
			{
				int community = order.get(row).intValue();
				line = counts[community];
				total = sizes[community];
				rows[row] = "Community " + (community + 1) + " · " + total;
			}
			else
			{
				for (int index = ROWS; index < order.size(); index++)
				{
					int community = order.get(index).intValue();
					for (int column = 0; column < real; column++)
					{
						line[column] += counts[community][column];
					}
					total += sizes[community];
				}
				rows[row] = (order.size() - ROWS) + " smaller · " + total;
			}
			for (int column = 0; column < real; column++)
			{
				values[row][column] = total == 0 ? 0.0 : line[column] / (double)total;
				text[row][column] = line[column] == 0 ? "" : Integer.toString(line[column]);
			}
		}
		String[] columns = new String[real];
		Color[] columnColors = new Color[real];
		for (int column = 0; column < real; column++)
		{
			columns[column] = graph.communityName(column);
			columnColors[column] = GraphRenderer.category(column, night);
		}
		GraphCharts.Heatmap heatmap = new GraphCharts.Heatmap();
		heatmap.set(rows, columns, values, text, null, columnColors, false, "found ↓ real →");
		NodeLabUi.Card card = new NodeLabUi.Card("Detected against real communities",
				"Each row is a community Leiden found in the recommendation network; each column a real community."
						+ " A recommender that recovers the real communities puts each row's nodes in one column.");
		card.action(NodeLabUi.help("Reading AMI and ARI",
				"Mutual information measures how much knowing a node's detected community tells about its real"
						+ " one. AMI subtracts what two random partitions with the same community sizes would share"
						+ " (its expectation under the hypergeometric model) and divides by the room left, so 0 is"
						+ " chance and 1 is identical; it can be slightly negative. ARI does the same for pairs of"
						+ " nodes placed together or apart. The permutation test shuffles the real labels 999 times."));
		card.content(heatmap);
		return card;
	}

	private static boolean[] activeInRecommendations(RunGraph graph)
	{
		boolean[] active = new boolean[graph.size()];
		for (RunGraph.Edge edge : graph.edges(RunGraph.Layer.RECOMMEND))
		{
			active[edge.source] = true;
			active[edge.target] = true;
		}
		return active;
	}

	/** Where recommendations go: real community of the target (rows) to that of the candidate (columns). */
	private JComponent flows(RunGraphRecord.Loaded loaded, boolean night)
	{
		RunGraph graph = loaded.graph;
		RunGraphAnalysis a = loaded.analysis;
		int real = graph.communityCount();
		List<Integer> rowsShown = new ArrayList<Integer>();
		for (int community = 0; community < real; community++)
		{
			int total = 0;
			for (int column = 0; column < real; column++)
			{
				total += a.recommendationMixing[community][column];
			}
			if (total > 0)
			{
				rowsShown.add(Integer.valueOf(community));
			}
		}
		String[] rows = new String[rowsShown.size()];
		Color[] rowColors = new Color[rowsShown.size()];
		double[][] values = new double[rowsShown.size()][real];
		String[][] text = new String[rowsShown.size()][real];
		for (int row = 0; row < rowsShown.size(); row++)
		{
			int community = rowsShown.get(row).intValue();
			int total = 0;
			for (int column = 0; column < real; column++)
			{
				total += a.recommendationMixing[community][column];
			}
			rows[row] = graph.communityName(community) + " · " + total;
			rowColors[row] = GraphRenderer.category(community, night);
			for (int column = 0; column < real; column++)
			{
				double share = total == 0 ? 0.0 : a.recommendationMixing[community][column] / (double)total;
				values[row][column] = share;
				text[row][column] = a.recommendationMixing[community][column] == 0 ? ""
						: String.format(Locale.US, "%.0f%%", 100.0 * share);
			}
		}
		String[] columns = new String[real];
		Color[] columnColors = new Color[real];
		String[] shareLabels = new String[real];
		for (int column = 0; column < real; column++)
		{
			columns[column] = graph.communityName(column);
			columnColors[column] = GraphRenderer.category(column, night);
			shareLabels[column] = graph.communityName(column);
		}
		GraphCharts.Heatmap heatmap = new GraphCharts.Heatmap();
		heatmap.set(rows, columns, values, text, rowColors, columnColors, rowsShown.size() == real,
				"from ↓ to →");
		GraphCharts.ShareBars bars = new GraphCharts.ShareBars();
		bars.set(shareLabels, a.communityCandidateShare, a.communityExposureShare, columnColors);
		NodeLabUi.Card card = new NodeLabUi.Card("Where the recommendations go",
				"Each row is the targets of one real community; each cell the share of their recommendations that"
						+ " go to a candidate of the column's community. The outlined diagonal is homophily ("
						+ GraphInsightsPanel.percent(a.homophily) + " overall, chance "
						+ GraphInsightsPanel.percent(a.homophilyChance) + ").");
		JPanel columnsPanel = NodeLabUi.columns(420, 2, 24);
		columnsPanel.add(NodeLabUi.stack(8, NodeLabUi.strong("Recommendation flows"), heatmap));
		columnsPanel.add(NodeLabUi.stack(8, NodeLabUi.strong("Exposure against size"), bars,
				NodeLabUi.note("Exposure weighs each appearance by its rank. A community recommended more than its"
						+ " share of candidates (ratio above 1) is favoured; below 1, overlooked.")));
		card.content(columnsPanel);
		return card;
	}

	/** Every detected community with its size, dominant real community, purity and conductance. */
	private JComponent table(RunGraphRecord.Loaded loaded)
	{
		RunGraph graph = loaded.graph;
		RunGraphAnalysis a = loaded.analysis;
		int[] membership = a.recommended.communities.membership;
		int detected = a.recommended.communities.communities;
		boolean[] active = activeInRecommendations(graph);
		List<RunGraph.Edge> edges = graph.edges(RunGraph.Layer.RECOMMEND);
		int[] sources = new int[edges.size()];
		int[] targets = new int[edges.size()];
		for (int index = 0; index < edges.size(); index++)
		{
			sources[index] = edges.get(index).source;
			targets[index] = edges.get(index).target;
		}
		NetworkAlgorithms.Undirected simple = NetworkAlgorithms.Undirected.of(graph.size(), sources, targets, null)
				.unweighted();
		double[] volume = new double[detected];
		double[] cut = new double[detected];
		int[] nodes = new int[detected];
		int[] targetCount = new int[detected];
		int[][] realCounts = new int[detected][graph.communityCount()];
		double totalVolume = 0.0;
		for (int node = 0; node < graph.size(); node++)
		{
			if (!active[node])
			{
				continue;
			}
			int community = membership[node];
			nodes[community]++;
			targetCount[community] += graph.isTarget(node) ? 1 : 0;
			if (graph.community(node) >= 0)
			{
				realCounts[community][graph.community(node)]++;
			}
			volume[community] += simple.degree(node);
			totalVolume += simple.degree(node);
			for (int edge = simple.offsets[node]; edge < simple.offsets[node + 1]; edge++)
			{
				if (membership[simple.neighbors[edge]] != community)
				{
					cut[community] += 1.0;
				}
			}
		}
		NodeLabUi.Table table = new NodeLabUi.Table(NodeLabUi.Table.text("Community", 0.9f),
				NodeLabUi.Table.number("Nodes", 0.6f), NodeLabUi.Table.number("Targets", 0.6f),
				NodeLabUi.Table.text("Mostly", 1.3f), NodeLabUi.Table.number("Purity", 0.7f),
				NodeLabUi.Table.number("Conductance", 0.9f));
		List<String[]> rows = new ArrayList<String[]>();
		List<Color[]> colors = new ArrayList<Color[]>();
		boolean night = NodeLabUi.night();
		for (int community = 0; community < detected && rows.size() < 60; community++)
		{
			if (nodes[community] == 0)
			{
				continue;
			}
			int best = -1;
			int labelled = 0;
			for (int real = 0; real < graph.communityCount(); real++)
			{
				labelled += realCounts[community][real];
				if (best < 0 || realCounts[community][real] > realCounts[community][best])
				{
					best = real;
				}
			}
			double purity = labelled == 0 || best < 0 ? Double.NaN : realCounts[community][best] / (double)labelled;
			double denominator = Math.min(volume[community], totalVolume - volume[community]);
			double conductance = denominator <= 0.0 ? Double.NaN : cut[community] / denominator;
			rows.add(new String[] {"Community " + (community + 1), Integer.toString(nodes[community]),
					Integer.toString(targetCount[community]), best < 0 ? "—" : graph.communityName(best),
					GraphInsightsPanel.percent(purity), GraphInsightsPanel.fixed(conductance, 3)});
			colors.add(new Color[] {null, null, null, best < 0 ? null : GraphRenderer.category(best, night),
					purity >= 0.8 ? NebulaDesign.SUCCESS : null, null});
		}
		table.setRows(rows, colors);
		NodeLabUi.Card card = new NodeLabUi.Card("Detected communities",
				"Purity: the share of a community's nodes from its most common real community. Conductance: the"
						+ " share of its links that leave it (0 a closed community, 1 none of its links inside).");
		card.content(rows.size() >= 60 ? NodeLabUi.stack(8, table, NodeLabUi.note("The 60 largest communities are"
				+ " listed; nodes.csv in the run's graph folder has every node's community.")) : table);
		return card;
	}
}
