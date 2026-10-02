package TwitterGatherDataFollowers.userRyersonU;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * The run graph itself, interactive: choose what colours and sizes the
 * nodes, which layers show, search for a node, and inspect any node's
 * community, centralities, recommendations and adoption.
 */
final class GraphExplorerPage extends GraphInsightsPanel.Page
{
	private static final long serialVersionUID = 1L;
	private static final int LIST_LIMIT = 40;

	private final GraphInsightsPanel panel;
	private final GraphCanvas canvas = new GraphCanvas();
	private final JPanel inspector = new JPanel(new BorderLayout());
	private final NodeLabUi.Chip layoutChip = new NodeLabUi.Chip("Layout ready", NodeLabUi.Tone.SUCCESS, true);
	private final NodeLabUi.Segmented colorBy;
	private final NodeLabUi.Segmented sizeBy;
	private final NodeLabUi.ToggleChip follows;
	private final NodeLabUi.ToggleChip recommendations;
	private final NodeLabUi.ToggleChip adoptions;
	private final NodeLabUi.Switch labels = new NodeLabUi.Switch(true);
	private final NodeLabUi.Switch isolated = new NodeLabUi.Switch(false);
	private final NodeLabUi.Switch legend = new NodeLabUi.Switch(true);
	private final NodeLabUi.Field search = new NodeLabUi.Field("", 14, "Find a user or account");
	private final JPanel content = new JPanel(new BorderLayout(14, 0));
	private boolean showingGraph;

	GraphExplorerPage(GraphInsightsPanel panel)
	{
		this.panel = panel;
		setBorder(new EmptyBorder(6, 24, 20, 24));
		String[] colorOptions = new String[GraphRenderer.ColorBy.values().length];
		for (int index = 0; index < colorOptions.length; index++)
		{
			colorOptions[index] = GraphRenderer.ColorBy.values()[index].shortLabel;
		}
		colorBy = new NodeLabUi.Segmented(colorOptions, null, 0);
		String[] sizeOptions = new String[GraphRenderer.SizeBy.values().length];
		for (int index = 0; index < sizeOptions.length; index++)
		{
			sizeOptions[index] = GraphRenderer.SizeBy.values()[index].label;
		}
		sizeBy = new NodeLabUi.Segmented(sizeOptions, null, 0);
		ChangeListener restyle = new ChangeListener()
		{
			public void stateChanged(ChangeEvent event)
			{
				applyStyle();
			}
		};
		colorBy.addChangeListener(restyle);
		sizeBy.addChangeListener(restyle);
		follows = new NodeLabUi.ToggleChip("Follows", RunGraph.Layer.FOLLOW, true);
		recommendations = new NodeLabUi.ToggleChip("Recommendations", RunGraph.Layer.RECOMMEND, true);
		adoptions = new NodeLabUi.ToggleChip("Adoptions", RunGraph.Layer.ADOPT, true);
		follows.addChangeListener(restyle);
		recommendations.addChangeListener(restyle);
		adoptions.addChangeListener(restyle);
		labels.addChangeListener(restyle);
		isolated.addChangeListener(restyle);
		labels.setToolTipText("Name the accounts, the targets and the recommended users");
		isolated.setToolTipText("Also show nodes without an edge in the shown layers");
		legend.setToolTipText("Show the colour and edge legend");
		legend.addChangeListener(new ChangeListener()
		{
			public void stateChanged(ChangeEvent event)
			{
				canvas.setLegend(legend.isOn());
			}
		});
		search.getDocument().addDocumentListener(new DocumentListener()
		{
			public void insertUpdate(DocumentEvent event)
			{
				applySearch(false);
			}

			public void removeUpdate(DocumentEvent event)
			{
				applySearch(false);
			}

			public void changedUpdate(DocumentEvent event)
			{
				applySearch(false);
			}
		});
		search.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent event)
			{
				applySearch(true);
			}
		});
		canvas.setListener(new GraphCanvas.Listener()
		{
			public void selectionChanged(int node)
			{
				inspect(node);
			}

			public void layoutProgress(boolean running, int iterations, boolean converged)
			{
				layoutChip.set(running ? "Laying out · " + iterations + " steps"
						: converged ? "Layout settled · " + iterations + " steps"
								: "Layout done · " + iterations + " steps",
						running ? NodeLabUi.Tone.INFO : NodeLabUi.Tone.SUCCESS);
			}

			public void layoutFinished(RunGraphRecord.Loaded withLayout)
			{
				GraphExplorerPage.this.panel.layoutReady(withLayout);
			}
		});
		content.setOpaque(false);
		inspector.setOpaque(false);
		inspector.setPreferredSize(new Dimension(340, 400));
	}

	void show(RunGraphRecord.Loaded loaded, String message)
	{
		if (loaded == null)
		{
			canvas.setLoaded(null);
			showingGraph = false;
			removeAll();
			add(GraphInsightsPanel.waiting(message), BorderLayout.CENTER);
			revalidate();
			repaint();
			return;
		}
		if (!showingGraph)
		{
			showingGraph = true;
			removeAll();
			add(toolbar(), BorderLayout.NORTH);
			NodeLabUi.Card graphCard = new NodeLabUi.Card(null, null);
			graphCard.setBorder(new EmptyBorder(8, 8, 11, 8));
			graphCard.content(canvas);
			content.removeAll();
			content.add(graphCard, BorderLayout.CENTER);
			NodeLabUi.Card inspectorCard = new NodeLabUi.Card(null, null);
			inspectorCard.content(inspector);
			inspectorCard.setPreferredSize(new Dimension(360, 400));
			content.add(inspectorCard, BorderLayout.EAST);
			add(content, BorderLayout.CENTER);
			revalidate();
		}
		boolean newGraph = canvas.loaded() == null || canvas.loaded().graph != loaded.graph;
		canvas.setLoaded(loaded);
		if (newGraph)
		{
			search.setText("");
			layoutChip.set(loaded.hasLayout() ? "Saved layout" : "Laying out…",
					loaded.hasLayout() ? NodeLabUi.Tone.SUCCESS : NodeLabUi.Tone.INFO);
			applyStyle();
			inspect(-1);
		}
		repaint();
	}

	/** Focuses a node (from another page's chart or table). */
	void focus(int node)
	{
		canvas.select(node);
		canvas.center(node, true);
	}

	private JComponent toolbar()
	{
		NodeLabUi.Card card = new NodeLabUi.Card(null, null);
		card.setBorder(new EmptyBorder(12, 16, 14, 16));
		NodeLabUi.Button fit = NodeLabUi.iconButton(NodeLabUi.Glyph.TARGET, "Fit the graph to the view (F)");
		fit.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent event)
			{
				canvas.fit();
			}
		});
		NodeLabUi.Button relayout = NodeLabUi.iconButton(NodeLabUi.Glyph.REFRESH,
				"Lay the graph out again from its seeded start (the result is always the same)");
		relayout.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent event)
			{
				canvas.relayout();
			}
		});
		final NodeLabUi.Button export = new NodeLabUi.Button("Export", NodeLabUi.ButtonKind.SECONDARY,
				NodeLabUi.Glyph.DOWNLOAD, true);
		export.setToolTipText("Save the current view as an image or as vector graphics");
		export.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent event)
			{
				javax.swing.JPopupMenu menu = new javax.swing.JPopupMenu();
				javax.swing.JMenuItem png = new javax.swing.JMenuItem("PNG image (twice the screen resolution)");
				png.addActionListener(new ActionListener()
				{
					public void actionPerformed(ActionEvent chosen)
					{
						export(true);
					}
				});
				javax.swing.JMenuItem svg = new javax.swing.JMenuItem("SVG vector graphics (papers and slides)");
				svg.addActionListener(new ActionListener()
				{
					public void actionPerformed(ActionEvent chosen)
					{
						export(false);
					}
				});
				menu.add(png);
				menu.add(svg);
				menu.show(export, 0, export.getHeight() + 4);
			}
		});
		JPanel first = NodeLabUi.spread(flow(labelled("Colour", colorBy), labelled("Size", sizeBy)), flow(layoutChip));
		search.setPreferredSize(new Dimension(170, search.getPreferredSize().height));
		JPanel second = NodeLabUi.spread(flow(follows, recommendations, adoptions, switchRow("Labels", labels),
				switchRow("Isolated", isolated), switchRow("Legend", legend)), flow(search, fit, relayout, export));
		card.content(NodeLabUi.stack(10, first, second));
		JPanel holder = new JPanel(new BorderLayout());
		holder.setOpaque(false);
		holder.setBorder(new EmptyBorder(0, 0, 12, 0));
		holder.add(card, BorderLayout.CENTER);
		return holder;
	}

	/** One row of controls that does not wrap (the page is wide enough for both toolbar rows). */
	private static JPanel flow(JComponent... controls)
	{
		JPanel row = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 10, 0));
		row.setOpaque(false);
		for (JComponent control : controls)
		{
			row.add(control);
		}
		return row;
	}

	private static JComponent labelled(String text, JComponent control)
	{
		JPanel row = new JPanel(new BorderLayout(8, 0));
		row.setOpaque(false);
		NodeLabUi.Text caption = NodeLabUi.caption(text.toUpperCase(Locale.US));
		row.add(caption, BorderLayout.WEST);
		row.add(control, BorderLayout.CENTER);
		return row;
	}

	private static JComponent switchRow(String text, NodeLabUi.Switch control)
	{
		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setOpaque(false);
		row.add(control, BorderLayout.WEST);
		row.add(NodeLabUi.body(text), BorderLayout.CENTER);
		row.setBorder(new EmptyBorder(4, 4, 0, 4));
		return row;
	}

	private void applyStyle()
	{
		GraphRenderer.Style style = canvas.style();
		style.colorBy = GraphRenderer.ColorBy.values()[colorBy.getSelected()];
		style.sizeBy = GraphRenderer.SizeBy.values()[sizeBy.getSelected()];
		style.follows = follows.isSelected();
		style.recommendations = recommendations.isSelected();
		style.adoptions = adoptions.isSelected();
		style.labels = labels.isOn();
		style.hideIsolated = !isolated.isOn();
		canvas.repaint();
	}

	private void applySearch(boolean jump)
	{
		RunGraphRecord.Loaded loaded = canvas.loaded();
		if (loaded == null)
		{
			return;
		}
		String query = search.getText().trim().toLowerCase(Locale.US);
		if (query.length() < 2)
		{
			canvas.style().matches = null;
			canvas.repaint();
			return;
		}
		boolean[] matches = new boolean[loaded.graph.size()];
		int first = -1;
		int count = 0;
		for (int node = 0; node < matches.length; node++)
		{
			String name = loaded.graph.name(node).toLowerCase(Locale.US);
			if (name.contains(query))
			{
				matches[node] = true;
				count++;
				//An exact name wins; otherwise the first match in name order.
				if (first < 0 || name.equals(query) && !loaded.graph.name(first).equalsIgnoreCase(query))
				{
					first = node;
				}
			}
		}
		canvas.style().matches = count == 0 ? null : matches;
		canvas.repaint();
		if (jump && first >= 0)
		{
			canvas.select(first);
			canvas.center(first, true);
		}
	}

	private void export(boolean image)
	{
		RunGraphRecord.Loaded loaded = canvas.loaded();
		if (loaded == null || !canvas.hasPositions())
		{
			return;
		}
		JFileChooser chooser = new JFileChooser(loaded.directory == null ? null
				: new File(loaded.directory, RunGraphRecord.FOLDER));
		String base = "graph-" + loaded.graph.algorithm.replaceAll("[^A-Za-z0-9._-]", "_") + "-view";
		chooser.setSelectedFile(new File(base + (image ? ".png" : ".svg")));
		if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION)
		{
			return;
		}
		File file = chooser.getSelectedFile();
		try
		{
			if (image)
			{
				ImageIO.write(canvas.image(2.0), "png", file);
			}
			else
			{
				Files.write(file.toPath(), canvas.svg().getBytes(StandardCharsets.UTF_8));
			}
		}
		catch (IOException failure)
		{
			System.err.println("Could not save " + file + ": " + failure.getMessage());
		}
	}

	// ------------------------------------------------------------ inspector

	private void inspect(int node)
	{
		inspector.removeAll();
		RunGraphRecord.Loaded loaded = canvas.loaded();
		if (loaded == null)
		{
			inspector.revalidate();
			inspector.repaint();
			return;
		}
		JPanel body = node < 0 ? overview(loaded) : details(loaded, node);
		NodeLabUi.Page scrollPage = new NodeLabUi.Page(330, 4, 200);
		scrollPage.section(body);
		javax.swing.JScrollPane scroll = NodeLabUi.scroll(scrollPage);
		scroll.setBorder(null);
		inspector.add(scroll, BorderLayout.CENTER);
		inspector.revalidate();
		inspector.repaint();
	}

	private JPanel overview(RunGraphRecord.Loaded loaded)
	{
		RunGraph graph = loaded.graph;
		JPanel list = GraphInsightsPanel.column(10);
		list.add(NodeLabUi.eyebrow("The run graph"));
		list.add(NodeLabUi.heading(graph.algorithm));
		list.add(NodeLabUi.prose(graph.dataset + ": " + graph.describe() + "."));
		list.add(fact("Communities", graph.communityCount() + " real · " + loaded.analysis.full.activeCommunities
				+ " detected"));
		list.add(fact("Targets", Integer.toString(graph.targetCount())));
		list.add(fact("Candidates", graph.candidateCount() + (graph.candidateType == RecommendationCandidateType.FOLLOWEE
				? " accounts" : " users")));
		list.add(NodeLabUi.note("Click a node to inspect it; double-click to centre it. Drag to pan, scroll to"
				+ " zoom, double-click the background (or press F) to fit. Ringed nodes are targets; large rings"
				+ " are followed accounts."));
		return list;
	}

	private JPanel details(final RunGraphRecord.Loaded loaded, int node)
	{
		final RunGraph graph = loaded.graph;
		RunGraphAnalysis a = loaded.analysis;
		JPanel list = GraphInsightsPanel.column(10);
		list.add(NodeLabUi.eyebrow(graph.kind(node).label + (graph.isTarget(node) ? " · target" : "")));
		list.add(NodeLabUi.heading(graph.name(node)));
		int community = graph.community(node);
		JPanel chips = NodeLabUi.row(6);
		if (community >= 0)
		{
			chips.add(new SwatchChip(graph.communityName(community), GraphRenderer.category(community,
					NodeLabUi.night())));
		}
		chips.add(new NodeLabUi.Chip(a.roles[node], NodeLabUi.Tone.NEUTRAL, false));
		list.add(chips);
		int followee = graph.followeeOf(node);
		if (followee >= 0)
		{
			list.add(fact("Follows", graph.name(followee)));
		}
		if (graph.isAccount(node))
		{
			list.add(fact("Followers", Integer.toString(a.followers[node])));
		}
		list.add(fact("Recommended to", a.recommendedIn[node] + (a.recommendedIn[node] == 1 ? " target" : " targets")
				+ " · exposure " + GraphInsightsPanel.fixed(a.exposureByNode[node], 2)));
		list.add(fact("Adopted by", a.adoptedIn[node] + (a.adoptedIn[node] == 1 ? " target" : " targets")));
		list.add(fact("PageRank", String.format(Locale.US, "%.5f (#%d)", a.pageRank[node], rank(a.pageRank, node))));
		double gained = a.attentionGained(node);
		list.add(fact("Attention gained", String.format(Locale.US, "%+.5f", gained) + " from recommendations"));
		list.add(fact("Betweenness", String.format(Locale.US, "%.5f (#%d)", a.betweenness.values[node],
				rank(a.betweenness.values, node))));
		list.add(fact("k-core", a.core[node] + " (of " + a.maxCore + ")"));
		list.add(fact("Participation", String.format(Locale.US, "P %.2f · z %.2f", a.participation[node],
				a.withinModuleZ[node])));
		if (graph.isTarget(node))
		{
			list.add(NodeLabUi.eyebrow("Its top-" + graph.cutoff + " recommendations"));
			RunGraph.Edge adoption = graph.adoptionOf(node);
			JPanel recommendations = GraphInsightsPanel.column(4);
			for (RunGraph.Edge edge : graph.recommendationsOf(node))
			{
				Boolean inside = graph.inside(edge);
				boolean adopted = adoption != null && adoption.target == edge.target;
				recommendations.add(link(edge.target, edge.rank + ". " + graph.name(edge.target)
						+ (adopted ? "  — adopted" : ""), inside == null ? "unknown community"
								: inside.booleanValue() ? "inside" : "bridge", inside == null ? NebulaDesign.TEXT_TERTIARY
										: inside.booleanValue() ? NebulaDesign.SUCCESS : NebulaDesign.WARNING));
			}
			list.add(recommendations);
		}
		List<Integer> recommendedBy = new ArrayList<Integer>();
		for (RunGraph.Edge edge : graph.edges(RunGraph.Layer.RECOMMEND))
		{
			if (edge.target == node)
			{
				recommendedBy.add(Integer.valueOf(edge.source));
			}
		}
		if (!recommendedBy.isEmpty())
		{
			list.add(NodeLabUi.eyebrow("Recommended to"));
			JPanel sources = GraphInsightsPanel.column(4);
			for (int index = 0; index < Math.min(LIST_LIMIT, recommendedBy.size()); index++)
			{
				int source = recommendedBy.get(index).intValue();
				sources.add(link(source, graph.name(source), graph.communityName(graph.community(source)), null));
			}
			if (recommendedBy.size() > LIST_LIMIT)
			{
				sources.add(NodeLabUi.caption("and " + (recommendedBy.size() - LIST_LIMIT) + " more"));
			}
			list.add(sources);
		}
		NodeLabUi.Button clear = new NodeLabUi.Button("Clear selection", NodeLabUi.ButtonKind.GHOST,
				NodeLabUi.Glyph.CROSS, true);
		clear.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent event)
			{
				canvas.select(-1);
			}
		});
		list.add(clear);
		return list;
	}

	private static int rank(double[] values, int node)
	{
		int rank = 1;
		for (double value : values)
		{
			rank += value > values[node] ? 1 : 0;
		}
		return rank;
	}

	private static JComponent fact(String label, String value)
	{
		JPanel row = new JPanel(new BorderLayout(10, 0));
		row.setOpaque(false);
		NodeLabUi.Text name = NodeLabUi.caption(label);
		name.setPreferredSize(new Dimension(104, name.getPreferredSize().height));
		name.setVerticalAlignment(javax.swing.SwingConstants.TOP);
		JPanel nameHolder = new JPanel(new BorderLayout());
		nameHolder.setOpaque(false);
		nameHolder.add(name, BorderLayout.NORTH);
		row.add(nameHolder, BorderLayout.WEST);
		row.add(NodeLabUi.prose(value), BorderLayout.CENTER);
		return row;
	}

	/** A clickable line naming a node, with a note on the right. */
	private JComponent link(final int node, String text, String note, Color noteColor)
	{
		NodeLabUi.Button button = new NodeLabUi.Button(text, NodeLabUi.ButtonKind.GHOST, null, true);
		button.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
		button.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent event)
			{
				canvas.select(node);
				canvas.center(node, false);
			}
		});
		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setOpaque(false);
		row.add(button, BorderLayout.CENTER);
		row.add(new NodeLabUi.Text(note, NebulaDesign.fontMedium(11f),
				noteColor == null ? NebulaDesign.TEXT_TERTIARY : noteColor), BorderLayout.EAST);
		return row;
	}

	/** A chip with a colour dot (a community). */
	private static final class SwatchChip extends JComponent
	{
		private static final long serialVersionUID = 1L;
		private final String text;
		private final Color color;

		SwatchChip(String text, Color color)
		{
			this.text = text;
			this.color = color;
			setFont(NebulaDesign.fontSemiBold(11f));
		}

		public Dimension getPreferredSize()
		{
			return new Dimension(getFontMetrics(getFont()).stringWidth(text) + 30, 24);
		}

		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D)graphics.create();
			NodeLabUi.prepare(g);
			g.setColor(GraphRenderer.alpha(color, 36));
			g.fill(new java.awt.geom.RoundRectangle2D.Float(0, 1, getWidth() - 1, 22, 22, 22));
			g.setColor(color);
			g.fill(new java.awt.geom.Ellipse2D.Float(9, 8, 8, 8));
			g.setColor(NebulaDesign.TEXT_PRIMARY);
			g.setFont(getFont());
			g.drawString(text, 22, 16);
			g.dispose();
		}
	}
}
