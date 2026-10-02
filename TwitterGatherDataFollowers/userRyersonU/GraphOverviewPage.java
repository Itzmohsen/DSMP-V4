package TwitterGatherDataFollowers.userRyersonU;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Line2D;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

/**
 * The run graph at a glance: the headline finding, the six measures with
 * their chance baselines, every finding in words, what each layer holds and
 * how this run compares with the others.
 */
final class GraphOverviewPage extends GraphInsightsPanel.Page
{
	private static final long serialVersionUID = 1L;
	private static final int RUN_ROWS = 12;

	private final GraphInsightsPanel panel;
	private final NodeLabUi.Page page = new NodeLabUi.Page(1360);
	private final GraphCharts.Thumbnail thumbnail = new GraphCharts.Thumbnail(400, 250);
	private RunGraph shownGraph;

	GraphOverviewPage(GraphInsightsPanel panel)
	{
		this.panel = panel;
		thumbnail.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		thumbnail.setToolTipText("Open the Explorer");
		thumbnail.addMouseListener(new MouseAdapter()
		{
			public void mouseClicked(MouseEvent event)
			{
				GraphOverviewPage.this.panel.show(GraphInsightsPanel.EXPLORER);
			}
		});
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
		thumbnail.set(loaded);
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
		boolean accounts = graph.candidateType == RecommendationCandidateType.FOLLOWEE;
		RunGraphAnalysis.Finding lead = a.findings.isEmpty() ? null : a.findings.get(0);
		NodeLabUi.Hero hero = new NodeLabUi.Hero("Run graph · " + graph.algorithm + " on " + graph.dataset,
				lead == null ? "The observed network" : lead.title,
				lead == null ? graph.describe() + "." : lead.detail, NebulaDesign.PURPLE);
		hero.chip(new NodeLabUi.Chip(accounts ? "Recommends accounts" : "Recommends users", NodeLabUi.Tone.ACCENT,
				false));
		hero.chip(new NodeLabUi.Chip("k = " + graph.cutoff, NodeLabUi.Tone.NEUTRAL, false));
		hero.chip(new NodeLabUi.Chip(graph.targetCount() + (graph.targetCount() == 1 ? " target" : " targets"),
				NodeLabUi.Tone.NEUTRAL, false));
		hero.chip(new NodeLabUi.Chip(String.format(Locale.US, "%,d nodes · %,d edges", graph.size(),
				graph.allEdges().size()), NodeLabUi.Tone.NEUTRAL, false));
		if (loaded.reconstructed)
		{
			hero.chip(new NodeLabUi.Chip("Rebuilt from an older record", NodeLabUi.Tone.WARNING, true));
		}
		JPanel right = new JPanel(new BorderLayout(0, 6));
		right.setOpaque(false);
		right.add(thumbnail, BorderLayout.CENTER);
		NodeLabUi.Text caption = NodeLabUi.caption("Click to explore the graph");
		caption.setBorder(new EmptyBorder(0, 4, 0, 0));
		right.add(caption, BorderLayout.SOUTH);
		hero.right(right);
		page.section(hero);

		JPanel tiles = NodeLabUi.grid(170, 6, 12);
		double homophily = a.saturated ? a.weightedHomophily : a.homophily;
		double chance = a.saturated ? a.weightedHomophilyChance : a.homophilyChance;
		double homophilyP = a.saturated ? a.weightedHomophilyP : a.homophilyP;
		tiles.add(new NodeLabUi.Metric("Homophily", GraphInsightsPanel.percent(homophily),
				"chance " + GraphInsightsPanel.percent(chance) + " · " + NetworkStatistics.pText(homophilyP)
						+ (a.saturated ? " · rank-weighted" : ""), tone(homophilyP, homophily > chance)));
		tiles.add(new NodeLabUi.Metric("Assortativity r", GraphInsightsPanel.fixed(a.assortativity.r, 3),
				"± " + GraphInsightsPanel.fixed(a.assortativity.standardError, 3) + " · "
						+ NetworkStatistics.pText(a.assortativity.pValue()),
				tone(a.assortativity.pValue(), a.assortativity.r > 0)));
		tiles.add(new NodeLabUi.Metric("Community recovery", GraphInsightsPanel.fixed(a.recommended.recovery.ami, 3),
				"AMI · " + a.recommended.activeCommunities + " communities found",
				tone(a.recoveryPermutationP, a.recommended.recovery.ami > 0)));
		tiles.add(new NodeLabUi.Metric("Exposure Gini", GraphInsightsPanel.fixed(a.exposureGini, 3),
				"coverage " + GraphInsightsPanel.percent(a.coverage),
				a.exposureGini > 0.8 ? NebulaDesign.WARNING : NebulaDesign.INFO));
		tiles.add(new NodeLabUi.Metric("Adoption E–I", GraphInsightsPanel.fixed(a.eiIndex, 3),
				"chance " + GraphInsightsPanel.fixed(a.eiChance, 3) + " · "
						+ NetworkStatistics.pText(a.adoptionP), NebulaDesign.PURPLE));
		double change = a.adopted.realModularity - a.observed.realModularity;
		tiles.add(new NodeLabUi.Metric("Modularity change", Double.isNaN(change) ? "—"
				: String.format(Locale.US, "%+.3f", change), GraphInsightsPanel.fixed(a.observed.realModularity, 3)
						+ " → " + GraphInsightsPanel.fixed(a.adopted.realModularity, 3),
				NebulaDesign.PINK));
		page.section(tiles);

		NodeLabUi.Card findings = new NodeLabUi.Card("Findings",
				"Each statement is tested against chance: random recommendation lists, random rankings or random"
						+ " adoption, with exact or permutation tests.");
		findings.action(NodeLabUi.help("How the tests work",
				"Homophily: the number of recommendation edges inside the target's community, against the exact"
						+ " distribution of random lists (a sum of hypergeometric draws), and the rank-weighted share"
						+ " against 9,999 random rankings (1,999 for very large runs). Assortativity: Newman's r"
						+ " with a jackknife standard error."
						+ " Community recovery: adjusted mutual information, with 999 label permutations. Adoption:"
						+ " the number of adoptions inside the community, against the exact distribution of random"
						+ " adoption (a Poisson-binomial)."));
		JPanel list = GraphInsightsPanel.column(20);
		for (RunGraphAnalysis.Finding finding : a.findings)
		{
			list.add(finding(finding));
		}
		if (a.findings.isEmpty())
		{
			list.add(NodeLabUi.note("No recommendation edges were recorded, so only the observed network is shown."));
		}
		findings.content(list);
		page.section(findings);

		if (!a.warnings.isEmpty())
		{
			NodeLabUi.Card warnings = new NodeLabUi.Card("Read with care", null);
			warnings.accent(NebulaDesign.WARNING);
			JPanel lines = GraphInsightsPanel.column(8);
			for (String warning : a.warnings)
			{
				lines.add(NodeLabUi.prose(warning));
			}
			if (loaded.reconstructed)
			{
				lines.add(NodeLabUi.prose("This run was recorded before run graphs existed: its graph was rebuilt from"
						+ " recommendations.csv and the dataset, and every user with a followee is taken as a"
						+ " candidate, which can slightly overstate the run's own candidate set."));
			}
			warnings.content(lines);
			page.section(warnings);
		}

		NodeLabUi.Card layers = new NodeLabUi.Card("What the graph holds",
				"One node per user and per followed account (" + graph.count(RunGraph.Kind.USER) + " users, "
						+ graph.count(RunGraph.Kind.ACCOUNT) + " accounts" + (graph.count(RunGraph.Kind.BOTH) > 0
								? ", " + graph.count(RunGraph.Kind.BOTH) + " both" : "") + "), and three kinds of edge.");
		JPanel columns = NodeLabUi.columns(260, 3, 24);
		columns.add(layer(new LayerSwatch(0), "Follows · " + count(graph, RunGraph.Layer.FOLLOW),
				"What the dataset says: each user follows one account. The users of an account form its real"
						+ " community, the ground truth everything else is measured against."));
		columns.add(layer(new LayerSwatch(1), "Recommendations · " + count(graph, RunGraph.Layer.RECOMMEND),
				"What the recommender proposed: each target linked to its top-" + graph.cutoff + " "
						+ (accounts ? "accounts" : "users") + ". Edges weigh 1/log2(1 + rank), so the first counts"
						+ " most; dashed edges leave the target's community."));
		columns.add(layer(new LayerSwatch(2), "Adoptions · " + count(graph, RunGraph.Layer.ADOPT),
				"What the simulated users did: each target follows one of its top " + Math.min(3, graph.cutoff)
						+ " recommendations, chosen with probability proportional to its score, as the user agents"
						+ " chose it."));
		layers.content(columns);
		page.section(layers);

		page.section(acrossRuns(loaded));
	}

	private static String count(RunGraph graph, RunGraph.Layer layer)
	{
		return String.format(Locale.US, "%,d", graph.edges(layer).size());
	}

	private static Color tone(double p, boolean above)
	{
		if (Double.isNaN(p) || p >= 0.05)
		{
			return NebulaDesign.WARNING;
		}
		return above ? NebulaDesign.SUCCESS : NebulaDesign.DANGER;
	}

	private static JComponent finding(RunGraphAnalysis.Finding finding)
	{
		JPanel row = new JPanel(new BorderLayout(12, 0));
		row.setOpaque(false);
		NodeLabUi.Tone tone = GraphInsightsPanel.tone(finding.tone);
		JPanel badgeHolder = new JPanel(new BorderLayout());
		badgeHolder.setOpaque(false);
		badgeHolder.add(NodeLabOverviewPage.badge(tone == NodeLabUi.Tone.SUCCESS ? NodeLabUi.Glyph.CHECK
				: tone == NodeLabUi.Tone.DANGER ? NodeLabUi.Glyph.CROSS : tone == NodeLabUi.Tone.WARNING
						? NodeLabUi.Glyph.WARNING : NodeLabUi.Glyph.INFO, tone, 30), BorderLayout.NORTH);
		row.add(badgeHolder, BorderLayout.WEST);
		row.add(NodeLabUi.stack(3, NodeLabUi.strong(finding.title), NodeLabUi.prose(finding.detail)),
				BorderLayout.CENTER);
		return row;
	}

	private static JComponent layer(JComponent swatch, String title, String text)
	{
		JPanel column = new JPanel(new BorderLayout(0, 10));
		column.setOpaque(false);
		JPanel holder = new JPanel(new BorderLayout());
		holder.setOpaque(false);
		holder.add(swatch, BorderLayout.WEST);
		column.add(holder, BorderLayout.NORTH);
		column.add(NodeLabUi.stack(4, NodeLabUi.strong(title), NodeLabUi.prose(text)), BorderLayout.CENTER);
		return column;
	}

	/** The recent runs side by side (from runs-index.csv); a row opens that run. */
	private JComponent acrossRuns(RunGraphRecord.Loaded loaded)
	{
		NodeLabUi.Card card = new NodeLabUi.Card("Across runs",
				"The same measures for the latest runs, so algorithms can be compared on one dataset. Click a run"
						+ " to open its graph.");
		final NodeLabUi.Table table = new NodeLabUi.Table(NodeLabUi.Table.text("Run", 1.0f),
				NodeLabUi.Table.text("Algorithm", 1.3f), NodeLabUi.Table.text("Dataset", 1.5f),
				NodeLabUi.Table.number("Targets", 0.6f), NodeLabUi.Table.number("Homophily", 0.8f),
				NodeLabUi.Table.number("Chance", 0.7f), NodeLabUi.Table.number("r", 0.6f),
				NodeLabUi.Table.number("AMI", 0.6f), NodeLabUi.Table.number("Gini", 0.6f),
				NodeLabUi.Table.number("E–I", 0.6f));
		List<String[]> rows = new ArrayList<String[]>();
		List<Color[]> colors = new ArrayList<Color[]>();
		final List<File> directories = new ArrayList<File>();
		for (RunRecordReader.Entry entry : RunRecordReader.index())
		{
			if (rows.size() >= RUN_ROWS)
			{
				break;
			}
			if (!entry.exists())
			{
				continue;
			}
			boolean current = loaded.directory != null && loaded.directory.equals(entry.directory);
			String created = entry.get("created");
			String when = created.length() >= 16 ? created.substring(5, 16).replace('T', ' ') : created;
			double homophily = entry.number("graph_homophily");
			double chance = entry.number("graph_homophily_chance");
			rows.add(new String[] {when, entry.get("algorithm"), entry.get("dataset"), entry.get("quality_targets"),
					GraphInsightsPanel.percent(homophily), GraphInsightsPanel.percent(chance),
					GraphInsightsPanel.fixed(entry.number("graph_assortativity"), 3),
					GraphInsightsPanel.fixed(entry.number("graph_community_ami"), 3),
					GraphInsightsPanel.fixed(entry.number("graph_exposure_gini"), 3),
					GraphInsightsPanel.fixed(entry.number("graph_adoption_ei"), 3)});
			double p = entry.number("graph_homophily_p");
			colors.add(new Color[] {current ? NebulaDesign.ACCENT : null, null, null, null,
					Double.isNaN(p) ? null : p < 0.05 && homophily > chance ? NebulaDesign.SUCCESS
							: p < 0.05 ? NebulaDesign.DANGER : null, null, null, null, null, null});
			directories.add(entry.directory);
		}
		table.setRows(rows, colors);
		table.setEmptyText("No recorded runs yet.");
		table.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		table.addMouseListener(new MouseAdapter()
		{
			public void mouseClicked(MouseEvent event)
			{
				int row = table.rowAt(event.getY());
				if (row >= 0 && row < directories.size())
				{
					panel.select(directories.get(row));
				}
			}
		});
		card.content(NodeLabUi.stack(8, table, NodeLabUi.note("Runs recorded before run graphs existed show no graph"
				+ " measures here; open one to rebuild its graph from its record.")));
		return card;
	}

	/** A short line in the style of a layer: hairline (follows), dashed curve (recommendations), thick arrow (adoptions). */
	private static final class LayerSwatch extends JComponent
	{
		private static final long serialVersionUID = 1L;
		private final int kind;

		LayerSwatch(int kind)
		{
			this.kind = kind;
		}

		public Dimension getPreferredSize()
		{
			return new Dimension(64, 26);
		}

		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D)graphics.create();
			NodeLabUi.prepare(g);
			boolean night = NodeLabUi.night();
			Color community = GraphRenderer.category(0, night);
			g.setColor(NodeLabUi.alpha(GraphRenderer.category(1, night), 255));
			g.fillOval(2, 8, 10, 10);
			g.setColor(community);
			g.fillOval(50, 6, 14, 14);
			if (kind == 0)
			{
				g.setColor(NodeLabUi.alpha(community, 150));
				g.setStroke(new BasicStroke(1f));
				g.draw(new Line2D.Double(12, 13, 50, 13));
			}
			else if (kind == 1)
			{
				g.setColor(GraphRenderer.bridge(night));
				g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10f,
						new float[] {4f, 3f}, 0f));
				g.draw(new java.awt.geom.QuadCurve2D.Double(12, 13, 31, 2, 48, 12));
			}
			else
			{
				g.setColor(GraphRenderer.ink(night));
				g.setStroke(new BasicStroke(2.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
				g.draw(new Line2D.Double(12, 13, 42, 13));
				g.fillPolygon(new int[] {50, 41, 41}, new int[] {13, 8, 18}, 3);
			}
			g.dispose();
		}
	}
}
