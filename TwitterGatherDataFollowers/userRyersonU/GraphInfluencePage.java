package TwitterGatherDataFollowers.userRyersonU;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.swing.JComponent;
import javax.swing.JPanel;

/**
 * Influence: who receives the recommender's attention. Exposure inequality
 * (Lorenz curve, Gini, coverage, hubness), the most recommended, most central
 * and best-connected nodes, and every node's role in the network's
 * communities.
 */
final class GraphInfluencePage extends GraphInsightsPanel.Page
{
	private static final long serialVersionUID = 1L;
	private static final int TOP = 10;

	private final GraphInsightsPanel panel;
	private final NodeLabUi.Page page = new NodeLabUi.Page(1360);
	private RunGraph shownGraph;

	GraphInfluencePage(GraphInsightsPanel panel)
	{
		this.panel = panel;
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

	private void build(final RunGraphRecord.Loaded loaded)
	{
		RunGraph graph = loaded.graph;
		RunGraphAnalysis a = loaded.analysis;
		boolean accounts = graph.candidateType == RecommendationCandidateType.FOLLOWEE;
		NodeLabUi.Hero hero = new NodeLabUi.Hero("Influence", "Who gets the attention",
				"Each appearance in a list gives a candidate exposure 1/log2(1 + rank), the position-based attention"
						+ " model of Singh and Joachims (2018). An even spread has a Gini near 0; a few candidates"
						+ " taking most of it, near 1.", NebulaDesign.WARNING);
		hero.chip(new NodeLabUi.Chip(String.format(Locale.US, "%d of %d candidates recommended", a.exposedCandidates,
				a.exposure.length), NodeLabUi.Tone.NEUTRAL, false));
		hero.chip(GraphInsightsPanel.testChip(a.sizeBias.pValue, a.sizeBias.rho > 0,
				"Large communities favoured", "Small communities favoured"));
		page.section(hero);

		JPanel tiles = NodeLabUi.grid(200, 5, 12);
		tiles.add(new NodeLabUi.Metric("Exposure Gini", GraphInsightsPanel.fixed(a.exposureGini, 3),
				"0 equal, 1 one candidate takes all", a.exposureGini > 0.8 ? NebulaDesign.WARNING : NebulaDesign.INFO));
		tiles.add(new NodeLabUi.Metric("Coverage", GraphInsightsPanel.percent(a.coverage),
				"candidates recommended at least once", NebulaDesign.SUCCESS));
		tiles.add(new NodeLabUi.Metric("Entropy", GraphInsightsPanel.fixed(a.exposureEntropy, 3),
				"normalised (1 uniform)", NebulaDesign.ACCENT));
		tiles.add(new NodeLabUi.Metric("Hubness", GraphInsightsPanel.fixed(a.exposureSkewness, 2),
				"skewness of exposure", NebulaDesign.PURPLE));
		tiles.add(new NodeLabUi.Metric("Size bias ρ", GraphInsightsPanel.fixed(a.sizeBias.rho, 3),
				"exposure vs community size · " + NetworkStatistics.pText(a.sizeBias.pValue),
				NebulaDesign.PINK));
		page.section(tiles);

		NodeLabUi.Card exposure = new NodeLabUi.Card("Exposure inequality",
				"Left: the share of all exposure held by the least exposed share of candidates (the further below"
						+ " the diagonal, the more unequal). Right: how many candidates reach each exposure, on log"
						+ " scales; a straight, long tail means a few hubs dominate.");
		GraphCharts.Lorenz lorenz = new GraphCharts.Lorenz();
		lorenz.set(a.exposure, a.exposureGini);
		GraphCharts.Ccdf ccdf = new GraphCharts.Ccdf();
		ccdf.set(a.exposure, "Exposure x");
		JPanel charts = NodeLabUi.grid(320, 2, 24);
		charts.add(lorenz);
		charts.add(ccdf);
		exposure.content(charts);
		page.section(exposure);

		JPanel tables = NodeLabUi.columns(360, 3, 14);
		tables.add(ranking(loaded, "Most recommended", "Candidates by exposure (times in a list).",
				a.exposureByNode, true, new Value()
				{
					public String text(int node)
					{
						return GraphInsightsPanel.fixed(loaded.analysis.exposureByNode[node], 2) + " · "
								+ loaded.analysis.recommendedIn[node] + "×";
					}
				}));
		tables.add(ranking(loaded, "PageRank", "On follows, recommendations and adoptions; the gain is"
				+ " over the observed network alone.", a.pageRank, false, new Value()
				{
					public String text(int node)
					{
						return String.format(Locale.US, "%.4f · %+.4f", loaded.analysis.pageRank[node],
								loaded.analysis.attentionGained(node));
					}
				}));
		tables.add(ranking(loaded, "Brokers", "Betweenness: how often a node lies on the shortest paths between"
				+ " others" + (a.betweenness.exact ? "." : " (estimated from " + a.betweenness.sources + " pivots)."),
				a.betweenness.values, false, new Value()
				{
					public String text(int node)
					{
						return String.format(Locale.US, "%.4f", loaded.analysis.betweenness.values[node]);
					}
				}));
		page.section(tables);

		NodeLabUi.Card roles = new NodeLabUi.Card("Network roles",
				"Guimerà and Amaral's map of the whole network: z is how well a node is connected inside its own"
						+ " detected community, P how evenly its links spread over communities. Hubs (z ≥ 2.5)"
						+ " anchor communities; connectors (high P) bridge them. Click a point to inspect it.");
		roles.action(NodeLabUi.help("The seven roles",
				"Non-hubs: R1 ultra-peripheral (P ≤ 0.05, all links inside), R2 peripheral (most inside), R3"
						+ " connectors (many links out), R4 kinless (links everywhere). Hubs: R5 provincial (mostly"
						+ " inside), R6 connector hubs, R7 kinless hubs. Communities are Leiden's on the whole"
						+ " network (follows, recommendations and adoptions)."));
		GraphCharts.RoleMap map = new GraphCharts.RoleMap();
		map.set(loaded, new GraphCharts.NodeListener()
		{
			public void nodeChosen(int node)
			{
				panel.inspect(node);
			}
		});
		int[] roleCounts = new int[GraphRenderer.roles().length];
		for (int node = 0; node < graph.size(); node++)
		{
			roleCounts[GraphRenderer.roleIndex(a.roles[node])]++;
		}
		NodeLabUi.Table roleTable = new NodeLabUi.Table(NodeLabUi.Table.text("Role", 1.4f),
				NodeLabUi.Table.number("Nodes", 0.6f));
		List<String[]> rows = new ArrayList<String[]>();
		for (int role = 0; role < roleCounts.length; role++)
		{
			if (roleCounts[role] > 0)
			{
				rows.add(new String[] {GraphRenderer.roles()[role], Integer.toString(roleCounts[role])});
			}
		}
		roleTable.setRows(rows, null);
		JPanel roleColumns = NodeLabUi.columns(300, 2, 24);
		roleColumns.add(map);
		roleColumns.add(NodeLabUi.stack(10, roleTable, NodeLabUi.note(accounts
				? "In account mode the accounts are the recommended items, so they are the hubs; a user whose"
						+ " recommendations spread over several accounts shows a high P."
				: "Targets whose recommendations reach other communities show a high P: they are the bridges the"
						+ " recommender builds.")));
		roles.content(roleColumns);
		page.section(roles);
	}

	/** Formats a node's value in a ranking. */
	private interface Value
	{
		String text(int node);
	}

	private JComponent ranking(final RunGraphRecord.Loaded loaded, String title, String subtitle, double[] score,
			boolean candidatesOnly, Value value)
	{
		NodeLabUi.Card card = new NodeLabUi.Card(title, subtitle);
		final NodeLabUi.Table table = new NodeLabUi.Table(NodeLabUi.Table.number("#", 0.3f),
				NodeLabUi.Table.text("Node", 1.4f), NodeLabUi.Table.number("Value", 1.3f));
		final List<Integer> nodes = loaded.analysis.top(score, TOP, candidatesOnly);
		List<String[]> rows = new ArrayList<String[]>();
		List<Color[]> colors = new ArrayList<Color[]>();
		boolean night = NodeLabUi.night();
		for (int index = 0; index < nodes.size(); index++)
		{
			int node = nodes.get(index).intValue();
			rows.add(new String[] {Integer.toString(index + 1), loaded.graph.name(node), value.text(node)});
			int community = loaded.graph.community(node);
			colors.add(new Color[] {null, community < 0 ? null : GraphRenderer.category(community, night), null});
		}
		table.setRows(rows, colors);
		table.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		table.setToolTipText("Click a node to inspect it in the Explorer");
		table.addMouseListener(new MouseAdapter()
		{
			public void mouseClicked(MouseEvent event)
			{
				int row = table.rowAt(event.getY());
				if (row >= 0 && row < nodes.size())
				{
					panel.inspect(nodes.get(row).intValue());
				}
			}
		});
		card.content(table);
		return card;
	}
}
