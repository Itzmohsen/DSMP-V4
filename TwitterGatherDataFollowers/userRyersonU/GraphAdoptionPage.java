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
 * Adoption: what the simulated follows do to the network. Whether they stay
 * inside communities or bridge them (the E-I index against random adoption),
 * and the observed network before and after: modularity of the real
 * communities, components, detected communities and their agreement with the
 * real ones.
 */
final class GraphAdoptionPage extends GraphInsightsPanel.Page
{
	private static final long serialVersionUID = 1L;
	private static final int ROWS = 300;

	private final GraphInsightsPanel panel;
	private final NodeLabUi.Page page = new NodeLabUi.Page(1360);
	private RunGraph shownGraph;

	GraphAdoptionPage(GraphInsightsPanel panel)
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

	private void build(RunGraphRecord.Loaded loaded)
	{
		RunGraph graph = loaded.graph;
		RunGraphAnalysis a = loaded.analysis;
		String title = "No adoption could be classified";
		for (RunGraphAnalysis.Finding finding : a.findings)
		{
			if (finding.title.startsWith("Adoptions"))
			{
				title = finding.title;
			}
		}
		NodeLabUi.Hero hero = new NodeLabUi.Hero("Adoption", title,
				"Each target follows one of its top " + Math.min(3, graph.cutoff) + " recommendations, chosen with"
						+ " probability proportional to its score, exactly as the user agents chose it. The E–I"
						+ " index (Krackhardt and Stern 1988) is −1 when every adoption stays inside the target's"
						+ " community and +1 when every one crosses to another.", NebulaDesign.PURPLE);
		hero.chip(GraphInsightsPanel.neutralTestChip(a.adoptionP, a.eiIndex > a.eiChance,
				"Bridges more than chance", "Stays inside more than chance"));
		hero.chip(new NodeLabUi.Chip(a.adoptionsInside + " of " + a.adoptionsClassified + " inside",
				NodeLabUi.Tone.NEUTRAL, false));
		page.section(hero);

		JPanel tiles = NodeLabUi.grid(170, 6, 12);
		tiles.add(new NodeLabUi.Metric("E–I index", GraphInsightsPanel.fixed(a.eiIndex, 3),
				"chance " + GraphInsightsPanel.fixed(a.eiChance, 3) + " · "
						+ NetworkStatistics.pText(a.adoptionP), NebulaDesign.PURPLE));
		double expectedInside = a.adoptionNull == null ? Double.NaN : a.adoptionNull.mean;
		tiles.add(new NodeLabUi.Metric("Inside adoptions", a.adoptionsInside + " / " + a.adoptionsClassified,
				"random adoption expects " + GraphInsightsPanel.fixed(expectedInside, 1), NebulaDesign.SUCCESS));
		tiles.add(new NodeLabUi.Metric("Modularity change", signed(a.adopted.realModularity - a.observed.realModularity),
				"real communities " + GraphInsightsPanel.fixed(a.observed.realModularity, 3) + " → "
						+ GraphInsightsPanel.fixed(a.adopted.realModularity, 3), NebulaDesign.PINK));
		tiles.add(new NodeLabUi.Metric("Components", a.observed.components + " → " + a.adopted.components,
				a.bridgedPairs + " community pair" + (a.bridgedPairs == 1 ? "" : "s") + " newly linked",
				NebulaDesign.INFO));
		tiles.add(new NodeLabUi.Metric("Leiden communities", a.observed.activeCommunities + " → "
				+ a.adopted.activeCommunities, "detected before → after", NebulaDesign.ACCENT));
		tiles.add(new NodeLabUi.Metric("AMI change", signed(a.adopted.recovery.ami - a.observed.recovery.ami),
				"detected vs real " + GraphInsightsPanel.fixed(a.observed.recovery.ami, 3) + " → "
						+ GraphInsightsPanel.fixed(a.adopted.recovery.ami, 3), NebulaDesign.WARNING));
		page.section(tiles);

		page.section(comparison(a));
		page.section(flows(loaded));
		page.section(adoptions(loaded));
	}

	private static String signed(double value)
	{
		return Double.isNaN(value) ? "—" : String.format(Locale.US, "%+.3f", value);
	}

	/** The observed network beside the network after adoption. */
	private static JComponent comparison(RunGraphAnalysis a)
	{
		NodeLabUi.Card card = new NodeLabUi.Card("Before and after adoption",
				"The observed network (follows) and the same network once every target has followed its adopted"
						+ " recommendation. Falling modularity of the real communities and fewer components mean the"
						+ " adoptions mix communities; rising, that they deepen them.");
		card.action(NodeLabUi.help("Why the real communities start so modular",
				"In the observed network every user follows exactly one account, so the network is one star per"
						+ " account and the real communities are its components: their modularity is"
						+ " 1 − Σ (share of edges)², the maximum possible, and Leiden recovers them exactly"
						+ " (AMI 1). Each adoption that crosses communities lowers both."));
		NodeLabUi.Table table = new NodeLabUi.Table(NodeLabUi.Table.text("Measure", 1.6f),
				NodeLabUi.Table.number("Observed", 0.9f), NodeLabUi.Table.number("After adoption", 0.9f),
				NodeLabUi.Table.number("Change", 0.8f));
		List<String[]> rows = new ArrayList<String[]>();
		RunGraphAnalysis.View before = a.observed;
		RunGraphAnalysis.View after = a.adopted;
		rows.add(row("Nodes with an edge", before.activeNodes, after.activeNodes));
		rows.add(row("Edges (distinct pairs)", before.edges, after.edges));
		rows.add(row("Components (two or more nodes)", before.components, after.components));
		rows.add(new String[] {"Largest component", GraphInsightsPanel.percent(before.giantFraction),
				GraphInsightsPanel.percent(after.giantFraction),
				signedPercent(after.giantFraction - before.giantFraction)});
		rows.add(new String[] {"Modularity of the real communities", GraphInsightsPanel.fixed(before.realModularity, 3),
				GraphInsightsPanel.fixed(after.realModularity, 3), signed(after.realModularity - before.realModularity)});
		rows.add(row("Leiden communities", before.activeCommunities, after.activeCommunities));
		rows.add(new String[] {"Leiden modularity", GraphInsightsPanel.fixed(before.communities.quality, 3),
				GraphInsightsPanel.fixed(after.communities.quality, 3),
				signed(after.communities.quality - before.communities.quality)});
		rows.add(new String[] {"AMI, detected vs real", GraphInsightsPanel.fixed(before.recovery.ami, 3),
				GraphInsightsPanel.fixed(after.recovery.ami, 3), signed(after.recovery.ami - before.recovery.ami)});
		rows.add(new String[] {"Mean shortest path (largest component)",
				GraphInsightsPanel.fixed(before.distances.meanLength, 2),
				GraphInsightsPanel.fixed(after.distances.meanLength, 2),
				signed(after.distances.meanLength - before.distances.meanLength)});
		rows.add(row("Diameter (largest component)", before.distances.diameter, after.distances.diameter));
		table.setRows(rows, null);
		card.content(table);
		return card;
	}

	private static String[] row(String label, int before, int after)
	{
		return new String[] {label, String.format(Locale.US, "%,d", before), String.format(Locale.US, "%,d", after),
				String.format(Locale.US, "%+,d", after - before)};
	}

	private static String signedPercent(double value)
	{
		return Double.isNaN(value) ? "—" : String.format(Locale.US, "%+.1f pt", 100.0 * value);
	}

	/** Adoptions from the target's real community (rows) to the adopted node's (columns). */
	private static JComponent flows(RunGraphRecord.Loaded loaded)
	{
		RunGraph graph = loaded.graph;
		RunGraphAnalysis a = loaded.analysis;
		boolean night = NodeLabUi.night();
		int real = graph.communityCount();
		List<Integer> shown = new ArrayList<Integer>();
		for (int community = 0; community < real; community++)
		{
			int total = 0;
			for (int column = 0; column < real; column++)
			{
				total += a.adoptionMixing[community][column];
			}
			if (total > 0)
			{
				shown.add(Integer.valueOf(community));
			}
		}
		String[] rows = new String[shown.size()];
		Color[] rowColors = new Color[shown.size()];
		double[][] values = new double[shown.size()][real];
		String[][] text = new String[shown.size()][real];
		for (int row = 0; row < shown.size(); row++)
		{
			int community = shown.get(row).intValue();
			int total = 0;
			for (int column = 0; column < real; column++)
			{
				total += a.adoptionMixing[community][column];
			}
			rows[row] = graph.communityName(community) + " · " + total;
			rowColors[row] = GraphRenderer.category(community, night);
			for (int column = 0; column < real; column++)
			{
				int count = a.adoptionMixing[community][column];
				values[row][column] = total == 0 ? 0.0 : count / (double)total;
				text[row][column] = count == 0 ? "" : Integer.toString(count);
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
		heatmap.set(rows, columns, values, text, rowColors, columnColors, shown.size() == real,
				"from ↓ to →");
		NodeLabUi.Card card = new NodeLabUi.Card("Adoption flows",
				"How many targets of each real community adopted a recommendation from each community. The diagonal"
						+ " holds the adoptions that stay inside.");
		card.content(shown.isEmpty() ? NodeLabUi.note("No adoption could be classified (the targets' communities are"
				+ " unknown).") : heatmap);
		return card;
	}

	/** Every adopted follow. */
	private JComponent adoptions(final RunGraphRecord.Loaded loaded)
	{
		final RunGraph graph = loaded.graph;
		final NodeLabUi.Table table = new NodeLabUi.Table(NodeLabUi.Table.text("Target", 1.2f),
				NodeLabUi.Table.text("Follows", 1.1f), NodeLabUi.Table.text("Adopted", 1.2f),
				NodeLabUi.Table.number("Rank", 0.5f), NodeLabUi.Table.number("Score", 0.7f),
				NodeLabUi.Table.text("Relation", 1.4f));
		List<String[]> rows = new ArrayList<String[]>();
		List<Color[]> colors = new ArrayList<Color[]>();
		final List<Integer> targets = new ArrayList<Integer>();
		for (RunGraph.Edge edge : graph.edges(RunGraph.Layer.ADOPT))
		{
			if (rows.size() >= ROWS)
			{
				break;
			}
			int followee = graph.followeeOf(edge.source);
			Boolean inside = graph.inside(edge);
			int community = graph.candidateType == RecommendationCandidateType.FOLLOWEE
					? graph.anchoredCommunity(edge.target) : graph.community(edge.target);
			rows.add(new String[] {graph.name(edge.source), followee < 0 ? "—" : graph.name(followee),
					graph.name(edge.target), Integer.toString(edge.rank), String.format(Locale.US, "%.4g", edge.score),
					inside == null ? "unknown" : inside.booleanValue() ? "inside"
							: "bridge to " + graph.communityName(community)});
			colors.add(new Color[] {null, null, null, null, null, inside == null ? null : inside.booleanValue()
					? NebulaDesign.SUCCESS : NebulaDesign.WARNING});
			targets.add(Integer.valueOf(edge.source));
		}
		table.setRows(rows, colors);
		table.setEmptyText("No adoptions were recorded.");
		table.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		table.addMouseListener(new MouseAdapter()
		{
			public void mouseClicked(MouseEvent event)
			{
				int row = table.rowAt(event.getY());
				if (row >= 0 && row < targets.size())
				{
					panel.inspect(targets.get(row).intValue());
				}
			}
		});
		NodeLabUi.Card card = new NodeLabUi.Card("Adopted follows",
				"Each target's simulated follow, with the rank and score of the adopted recommendation. Click a row to"
						+ " see the target in the Explorer.");
		int total = graph.edges(RunGraph.Layer.ADOPT).size();
		card.content(total > ROWS ? NodeLabUi.stack(8, table, NodeLabUi.note("The first " + ROWS + " of " + total
				+ " adoptions; edges.csv in the run's graph folder has them all.")) : table);
		return card;
	}
}
