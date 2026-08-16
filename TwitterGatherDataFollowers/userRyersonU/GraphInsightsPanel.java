package TwitterGatherDataFollowers.userRyersonU;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.QuadCurve2D;
import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JViewport;
import javax.swing.ListSelectionModel;
import javax.swing.RowFilter;
import javax.swing.ScrollPaneConstants;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableRowSorter;

final class GraphInsightsPanel extends JPanel
{
	private static final Color BACKGROUND = NebulaDesign.CANVAS_TOP;
	private static final Color SURFACE = NebulaDesign.SURFACE;
	private static final Color SURFACE_ALT = NebulaDesign.SURFACE_ALT;
	private static final Color BORDER = NebulaDesign.BORDER_SUBTLE;
	private static final Color TEXT = NebulaDesign.TEXT_PRIMARY;
	private static final Color MUTED = NebulaDesign.TEXT_SECONDARY;
	private static final Color BLUE = NebulaDesign.ACCENT;
	private static final Color GREEN = NebulaDesign.SUCCESS;
	private static final Color PURPLE = NebulaDesign.PURPLE;
	private static final Color AMBER = NebulaDesign.WARNING;
	private static final Color RED = NebulaDesign.DANGER;
	private static final Color TEAL = NebulaDesign.INFO;
	private static final int RADIUS = 10;
	private static final int HEADER_DASHBOARD_GAP = 12;
	private static final int DASHBOARD_EXPLORER_GAP = 14;
	private static final int EXPLORER_FIRST_VIEW_HINT = 12;
	private static final int DASHBOARD_MIN_HEIGHT = 620;
	private static final int DASHBOARD_BOTTOM_ROW_HEIGHT = 250;

	private final ControllerAgentGui owner;
	private final JLabel titleLabel;
	private final JLabel subtitleLabel;
	private final JLabel statusPill;
	private final JLabel artifactLabel;
	private final JButton refreshButton;
	private final JButton exportButton;
	private final HeaderSignalPanel headerSignalPanel;
	private final HealthPanel healthPanel;
	private final NetworkShapePanel networkShapePanel;
	private final RecommendationImpactStoryPanel impactPanel;
	private final RankingsPanel rankingsPanel;
	private final EvidencePanel evidencePanel;
	private final ExplorationPanel explorationPanel;

	private SimulatorHelpPanel.Snapshot snapshot = SimulatorHelpPanel.Snapshot.empty();
	private GraphAnalysisEngine.Analysis analysis =
			GraphAnalysisEngine.Analysis.missing(null, null, "Choose a dataset to analyze graph artifacts.");
	private String lastAnalysisKey = "";
	private int analysisRequest;

	GraphInsightsPanel(ControllerAgentGui owner)
	{
		this.owner = owner;
		setLayout(new BorderLayout(12, 12));
		setBorder(new EmptyBorder(14, 16, 14, 16));
		setOpaque(false);
		setBackground(BACKGROUND);

		titleLabel = new JLabel("Graph Insights")
		{
			public Color getForeground()
			{
				return TEXT;
			}
		};
		titleLabel.setFont(NebulaDesign.fontDisplay());
		subtitleLabel = new JLabel("Read-only analysis of the node and edge files produced by the simulator.")
		{
			public Color getForeground()
			{
				return MUTED;
			}
		};
		subtitleLabel.setFont(NebulaDesign.fontBody(12.5f));
		statusPill = pill("Waiting for graph data", BLUE);
		artifactLabel = new JLabel("No graph artifact selected yet.")
		{
			public Color getForeground()
			{
				return NebulaDesign.TEXT_TERTIARY;
			}
		};
		artifactLabel.setFont(NebulaDesign.fontMono(11f));

		refreshButton = actionButton("Analyze now", true);
		refreshButton.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent event)
			{
				refreshFromSimulator(true);
			}
		});
		exportButton = actionButton("Export report", false);
		exportButton.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent event)
			{
				exportReport();
			}
		});

		headerSignalPanel = new HeaderSignalPanel();
		healthPanel = new HealthPanel();
		networkShapePanel = new NetworkShapePanel();
		impactPanel = new RecommendationImpactStoryPanel();
		rankingsPanel = new RankingsPanel();
		evidencePanel = new EvidencePanel();
		explorationPanel = new ExplorationPanel();

		add(createScrollablePage(), BorderLayout.CENTER);

		final Timer timer = new Timer(24, new ActionListener()
		{
			public void actionPerformed(ActionEvent event)
			{
				headerSignalPanel.advance();
				networkShapePanel.advance();
				impactPanel.advance();
			}
		});
		timer.start();
		addHierarchyListener(new java.awt.event.HierarchyListener()
		{
			public void hierarchyChanged(java.awt.event.HierarchyEvent event)
			{
				if ((event.getChangeFlags()
						& java.awt.event.HierarchyEvent.SHOWING_CHANGED) != 0)
				{
					if (isShowing())
					{
						timer.start();
					}
					else
					{
						timer.stop();
					}
				}
			}
		});
		refreshFromSimulator(false);
	}

	private JComponent createScrollablePage()
	{
		ScrollablePagePanel page = new ScrollablePagePanel();
		page.setLayout(null);
		page.setOpaque(false);

		JPanel header = createHeader();
		header.setMaximumSize(new Dimension(Integer.MAX_VALUE, 138));
		page.add(header);

		final JPanel dashboard = createDashboard();
		dashboard.setPreferredSize(new Dimension(1180, DASHBOARD_MIN_HEIGHT));
		dashboard.setMinimumSize(new Dimension(920, DASHBOARD_MIN_HEIGHT));
		dashboard.setMaximumSize(new Dimension(
				Integer.MAX_VALUE,
				DASHBOARD_MIN_HEIGHT));
		page.add(dashboard);

		explorationPanel.setPreferredSize(new Dimension(1180, 650));
		explorationPanel.setMinimumSize(new Dimension(920, 600));
		explorationPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 700));
		page.add(explorationPanel);
		page.setSections(header, dashboard, explorationPanel);

		JScrollPane scrollPane = new JScrollPane(page);
		scrollPane.setBorder(BorderFactory.createEmptyBorder());
		scrollPane.setOpaque(false);
		scrollPane.getViewport().setOpaque(false);
		scrollPane.getViewport().setBackground(BACKGROUND);
		scrollPane.setBackground(BACKGROUND);
		scrollPane.setVerticalScrollBarPolicy(
				ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
		scrollPane.setHorizontalScrollBarPolicy(
				ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		scrollPane.getVerticalScrollBar().setUnitIncrement(24);
		return scrollPane;
	}

	void refreshFromSimulator()
	{
		refreshFromSimulator(false);
	}

	private void refreshFromSimulator(boolean force)
	{
		snapshot = owner == null
				? SimulatorHelpPanel.Snapshot.empty()
				: owner.buildSimulatorHelpSnapshot();
		File importantStuff = new File("important-stuff");
		File mapping = null;
		File edge = null;
		String datasetName = snapshot.datasetName == null ? "" : snapshot.datasetName;
		if (datasetName.length() > 0 && !"No dataset selected".equals(datasetName))
		{
			mapping = GraphAnalysisEngine.mappingFile(importantStuff, datasetName);
			edge = GraphAnalysisEngine.edgeFile(
					importantStuff,
					datasetName,
					snapshot.mode,
					snapshot.algorithm);
		}
		String key = analysisKey(mapping, edge, snapshot);
		if (!force && key.equals(lastAnalysisKey))
		{
			updateLabels();
			updatePanels();
			return;
		}
		lastAnalysisKey = key;
		final File mappingFile = mapping;
		final File edgeFile = edge;
		final int request = ++analysisRequest;
		analysis = GraphAnalysisEngine.Analysis.missing(
				mappingFile,
				edgeFile,
				mappingFile == null
						? "Choose a simulator dataset first."
						: "Analyzing graph artifacts...");
		updateLabels();
		updatePanels();

		Thread worker = new Thread(new Runnable()
		{
			public void run()
			{
				GraphAnalysisEngine.Analysis loaded;
				try
				{
					loaded = GraphAnalysisEngine.analyze(mappingFile, edgeFile);
				}
				catch (IOException ex)
				{
					loaded = GraphAnalysisEngine.Analysis.missing(
							mappingFile,
							edgeFile,
							"Could not analyze graph artifacts: " + ex.getMessage());
				}
				final GraphAnalysisEngine.Analysis result = loaded;
				SwingUtilities.invokeLater(new Runnable()
				{
					public void run()
					{
						if (request == analysisRequest)
						{
							analysis = result;
							updateLabels();
							updatePanels();
						}
					}
				});
			}
		}, "DSMP-Graph-Insights-Analyzer");
		worker.setDaemon(true);
		worker.start();
	}

	private JPanel createHeader()
	{
		JPanel header = new HeroCardPanel(new BorderLayout(18, 0));
		header.setPreferredSize(new Dimension(900, 138));
		header.setBorder(new EmptyBorder(13, 20, 14, 18));

		JLabel eyebrow = new JLabel("GRAPH OBSERVATORY");
		eyebrow.setFont(NebulaDesign.fontSection());
		eyebrow.setForeground(BLUE);

		JPanel copy = new JPanel();
		copy.setOpaque(false);
		copy.setLayout(new BoxLayout(copy, BoxLayout.Y_AXIS));
		copy.setPreferredSize(new Dimension(440, 108));
		copy.add(eyebrow);
		copy.add(Box.createVerticalStrut(2));
		copy.add(titleLabel);
		copy.add(Box.createVerticalStrut(3));
		copy.add(subtitleLabel);
		copy.add(Box.createVerticalStrut(7));

		JPanel statusRow = new JPanel(new BorderLayout(10, 0));
		statusRow.setOpaque(false);
		statusRow.setMaximumSize(new Dimension(440, 26));
		statusRow.add(statusPill, BorderLayout.WEST);
		statusRow.add(artifactLabel, BorderLayout.CENTER);
		copy.add(statusRow);

		headerSignalPanel.setPreferredSize(new Dimension(690, 104));

		JPanel actions = new JPanel(new GridLayout(2, 1, 0, 8));
		actions.setOpaque(false);
		actions.setPreferredSize(new Dimension(142, 78));
		actions.add(refreshButton);
		actions.add(exportButton);

		header.add(copy, BorderLayout.WEST);
		header.add(headerSignalPanel, BorderLayout.CENTER);
		header.add(actions, BorderLayout.EAST);
		return header;
	}

	private JPanel createDashboard()
	{
		JPanel dashboard = new JPanel(new GridBagLayout());
		dashboard.setOpaque(false);

		GridBagConstraints c = new GridBagConstraints();
		c.fill = GridBagConstraints.BOTH;
		c.insets = new java.awt.Insets(0, 0, 12, 12);
		c.weighty = 1.0;

		c.gridx = 0;
		c.gridy = 0;
		c.weightx = 0.26;
		dashboard.add(card("Graph Health", healthPanel), c);

		c.gridx = 1;
		c.weightx = 0.44;
		dashboard.add(card("Network Shape", networkShapePanel), c);

		c.gridx = 2;
		c.weightx = 0.30;
		c.weighty = 1.0;
		c.gridheight = 2;
		c.insets = new java.awt.Insets(0, 0, 0, 0);
		JPanel impactCard = card("Recommendation Impact", impactPanel);
		impactCard.setPreferredSize(new Dimension(380, DASHBOARD_MIN_HEIGHT));
		impactCard.setMinimumSize(new Dimension(340, 400));
		dashboard.add(impactCard, c);

		c.gridy = 1;
		c.gridheight = 1;
		c.insets = new java.awt.Insets(0, 0, 0, 12);
		c.weighty = 0.0;
		c.gridx = 0;
		c.weightx = 0.36;
		JPanel topNodesCard = card("Top Nodes", rankingsPanel);
		topNodesCard.setPreferredSize(new Dimension(360, DASHBOARD_BOTTOM_ROW_HEIGHT));
		topNodesCard.setMinimumSize(new Dimension(260, 210));
		dashboard.add(topNodesCard, c);

		c.gridx = 1;
		c.weightx = 0.34;
		JPanel evidenceCard = card("Interpretation + Artifacts", evidencePanel);
		evidenceCard.setPreferredSize(new Dimension(420, DASHBOARD_BOTTOM_ROW_HEIGHT));
		evidenceCard.setMinimumSize(new Dimension(320, 210));
		dashboard.add(evidenceCard, c);
		return dashboard;
	}

	private JPanel card(String title, JPanel body)
	{
		JPanel panel = new RoundedPanel(new BorderLayout(0, 8));
		panel.setBorder(new EmptyBorder(14, 14, 14, 14));
		JLabel label = new JLabel(title);
		label.setFont(NebulaDesign.fontTitle());
		label.setForeground(TEXT);
		panel.add(label, BorderLayout.NORTH);
		panel.add(body, BorderLayout.CENTER);
		return panel;
	}

	private void updateLabels()
	{
		String family = datasetFamily(snapshot);
		titleLabel.setText("Graph Insights");
		subtitleLabel.setText(family + "  ·  "
				+ snapshot.algorithmName + "  ·  "
				+ snapshot.modeLabel);
		statusPill.setText(sentenceCase(analysis.statusLabel()));
		statusPill.putClientProperty("nebula.tone", statusColor(analysis));
		statusPill.repaint();
		artifactLabel.setText(compact(edgeName(analysis.edgeFile), 58));
		artifactLabel.setToolTipText(edgeName(analysis.edgeFile));
	}

	private static String sentenceCase(String value)
	{
		if (value == null || value.length() == 0)
		{
			return "";
		}
		String lower = value.toLowerCase(Locale.US);
		return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
	}

	private void updatePanels()
	{
		headerSignalPanel.setState(snapshot, analysis);
		healthPanel.setState(snapshot, analysis);
		networkShapePanel.setState(snapshot, analysis);
		impactPanel.setState(snapshot, analysis);
		rankingsPanel.setState(snapshot, analysis);
		evidencePanel.setState(snapshot, analysis);
		explorationPanel.setState(snapshot, analysis);
		revalidate();
		repaint();
	}

	private void exportReport()
	{
		if (analysis == null || !analysis.available)
		{
			NebulaDialogs.showInfo(
					this,
					"There is no graph analysis report to export yet.",
					"Graph Insights");
			return;
		}
		String base = snapshot.datasetName == null ? "graph" : snapshot.datasetName;
		base = base.replaceAll("[^A-Za-z0-9._-]", "_");
		File report = new File(
				"reports",
				"graph-insights-" + base + "-"
						+ new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date())
						+ ".txt");
		try
		{
			GraphAnalysisEngine.writeTextReport(analysis, report);
			if (Desktop.isDesktopSupported())
			{
				try
				{
					Desktop.getDesktop().open(report);
				}
				catch (Exception ignored)
				{
					// The report was still written; opening is a convenience only.
				}
			}
			NebulaDialogs.showSuccess(
					this,
					"Graph insights report written to:\n" + report.getAbsolutePath(),
					"Graph Insights");
		}
		catch (IOException ex)
		{
			NebulaDialogs.showWarning(
					this,
					"Could not write graph insights report:\n" + ex.getMessage(),
					"Graph Insights");
		}
	}

	private static String analysisKey(
			File mapping,
			File edge,
			SimulatorHelpPanel.Snapshot snapshot)
	{
		return pathState(mapping)
				+ "|"
				+ pathState(edge)
				+ "|"
				+ snapshot.algorithm
				+ "|"
				+ snapshot.mode;
	}

	private static String pathState(File file)
	{
		if (file == null)
		{
			return "null";
		}
		return file.getAbsolutePath()
				+ ":"
				+ (file.isFile() ? file.lastModified() : -1L)
				+ ":"
				+ (file.isFile() ? file.length() : -1L);
	}

	private static Color statusColor(GraphAnalysisEngine.Analysis analysis)
	{
		if (analysis == null || !analysis.available || analysis.validEdgeCount == 0L)
		{
			return BLUE;
		}
		if (analysis.malformedEdgeLines > 0L
				|| analysis.unknownNodeEdges > 0L
				|| analysis.healthScore < 70)
		{
			return RED;
		}
		if (analysis.healthScore < 86)
		{
			return AMBER;
		}
		return GREEN;
	}

	private static String datasetFamily(SimulatorHelpPanel.Snapshot s)
	{
		String value = ((s.datasetName == null ? "" : s.datasetName) + " "
				+ (s.datasetPath == null ? "" : s.datasetPath) + " "
				+ (s.datasetSource == null ? "" : s.datasetSource))
				.toLowerCase(Locale.US);
		if (value.indexOf("retail") >= 0
				|| value.indexOf("online") >= 0
				|| value.indexOf("customer") >= 0)
		{
			return "Retail graph";
		}
		if (value.indexOf("journal") >= 0
				|| value.indexOf("author") >= 0
				|| value.indexOf("paper") >= 0
				|| value.indexOf("title") >= 0)
		{
			return "Journal graph";
		}
		if (value.indexOf("tweet") >= 0
				|| value.indexOf("twitter") >= 0
				|| value.indexOf("ryerson") >= 0
				|| value.indexOf("social") >= 0)
		{
			return "Twitter graph";
		}
		return "DSMP graph";
	}

	private static String outputMeaning(SimulatorHelpPanel.Snapshot s)
	{
		if (s.outputType == RecommendationCandidateType.FOLLOWEE)
		{
			if ("Retail graph".equals(datasetFamily(s)))
			{
				return "product/store labels";
			}
			if ("Journal graph".equals(datasetFamily(s)))
			{
				return "journals or venues";
			}
			if ("Twitter graph".equals(datasetFamily(s)))
			{
				return "followed accounts";
			}
			return "column-1 labels";
		}
		if (s.outputType == RecommendationCandidateType.USER)
		{
			if ("Retail graph".equals(datasetFamily(s)))
			{
				return "similar customers";
			}
			if ("Journal graph".equals(datasetFamily(s)))
			{
				return "similar authors";
			}
			if ("Twitter graph".equals(datasetFamily(s)))
			{
				return "similar Twitter users";
			}
			return "similar users";
		}
		return "recommendations";
	}

	private static String originalMeaning(SimulatorHelpPanel.Snapshot s)
	{
		if ("Retail graph".equals(datasetFamily(s)))
		{
			return "customer -> original product/store";
		}
		if ("Journal graph".equals(datasetFamily(s)))
		{
			return "author -> original journal/venue";
		}
		if ("Twitter graph".equals(datasetFamily(s)))
		{
			return "user -> original followed account";
		}
		return "target -> original reference";
	}

	/** Short source-edge phrase that fits the small signal boxes without "...". */
	private static String sourceShort(SimulatorHelpPanel.Snapshot s)
	{
		String meaning = originalMeaning(s);
		int arrow = meaning.indexOf(" -> ");
		if (arrow > 0)
		{
			return meaning.substring(0, arrow) + " edges";
		}
		return meaning;
	}

	/** Just the querying entity ("customer", "user"), for the tightest boxes. */
	private static String sourceWord(SimulatorHelpPanel.Snapshot s)
	{
		String phrase = sourceShort(s);
		if (phrase.endsWith(" edges"))
		{
			return phrase.substring(0, phrase.length() - " edges".length());
		}
		return phrase;
	}

	/** Short community caption: "near <leader>" beats a clipped full name. */
	private static String communityShort(String name)
	{
		if (name == null || name.length() == 0)
		{
			return "community";
		}
		int near = name.indexOf(" near ");
		if (near >= 0)
		{
			return name.substring(near + 1);
		}
		return name;
	}

	/** Dataset tile value that never clips the placeholder sentence. */
	private static String datasetShort(SimulatorHelpPanel.Snapshot snapshot)
	{
		String name = snapshot == null ? null : snapshot.datasetName;
		if (name == null
				|| name.length() == 0
				|| "No dataset selected".equals(name))
		{
			return "None yet";
		}
		return compact(name, 18);
	}

	private static String communityMetricDetail(GraphAnalysisEngine.Analysis analysis)
	{
		if (analysis == null || !analysis.available)
		{
			return "waiting for graph";
		}
		if (analysis.gephiCommunityCount)
		{
			if (Double.isFinite(analysis.modularityScore))
			{
				return "Gephi Q " + new DecimalFormat("0.000").format(analysis.modularityScore);
			}
			return "Gephi modularity";
		}
		return "fallback estimate";
	}

	private static String communityLayerLabel(GraphAnalysisEngine.Analysis analysis)
	{
		return analysis != null && analysis.gephiCommunityCount
				? "GEPHI MODULARITY" : "COMMUNITY ESTIMATE";
	}

	private static String communityListTitle(GraphAnalysisEngine.Analysis analysis)
	{
		return analysis != null && analysis.gephiCommunityCount
				? "Largest Gephi communities" : "Largest estimated groups";
	}

	/**
	 * Self-painting status pill: soft tint capsule + strong hue label. The
	 * tone color lives in the "nebula.tone" client property so the pill stays
	 * correct across theme toggles and analysis refreshes.
	 */
	private static JLabel pill(String text, Color color)
	{
		JLabel label = new JLabel(text, SwingConstants.CENTER)
		{
			protected void paintComponent(Graphics graphics)
			{
				Graphics2D g = (Graphics2D)graphics.create();
				NebulaDesign.prepare(g);
				Object tone = getClientProperty("nebula.tone");
				Color hue = tone instanceof Color ? (Color)tone : BLUE;
				int w = getWidth();
				int h = getHeight();
				g.setColor(tint(hue, 0.84f));
				g.fillRoundRect(0, 0, w, h, h, h);
				g.setColor(alpha(hue, 84));
				g.drawRoundRect(0, 0, w - 1, h - 1, h - 1, h - 1);
				g.setColor(deep(hue));
				g.setFont(getFont());
				FontMetrics metrics = g.getFontMetrics();
				String value = getText();
				g.drawString(value,
						(w - metrics.stringWidth(value)) / 2,
						(h - metrics.getHeight()) / 2 + metrics.getAscent());
				g.dispose();
			}
		};
		label.setFont(NebulaDesign.fontMedium(11f));
		label.setOpaque(false);
		label.setBorder(new EmptyBorder(6, 13, 6, 13));
		label.putClientProperty("nebula.tone", color);
		return label;
	}

	/**
	 * Nebula action button: primary = accent fill, otherwise a quiet neutral
	 * card. Fully self-painting so night-mode tree walks cannot wash it out.
	 */
	private static JButton actionButton(String text, final boolean primary)
	{
		JButton button = new JButton(text)
		{
			protected void paintComponent(Graphics graphics)
			{
				Graphics2D g = (Graphics2D)graphics.create();
				NebulaDesign.prepare(g);
				int w = getWidth();
				int h = getHeight();
				boolean hover = getModel().isRollover();
				boolean pressed = getModel().isPressed();
				if (primary)
				{
					Color fill = pressed
							? NebulaDesign.ACCENT_PRESS
							: hover ? NebulaDesign.ACCENT_HOVER : NebulaDesign.ACCENT;
					if (hover)
					{
						g.setColor(alpha(NebulaDesign.ACCENT, 58));
						g.fillRoundRect(0, 0, w, h, RADIUS + 3, RADIUS + 3);
					}
					g.setColor(fill);
					g.fillRoundRect(1, 1, w - 2, h - 2, RADIUS, RADIUS);
					g.setColor(NebulaDesign.TEXT_ON_ACCENT);
				}
				else
				{
					g.setColor(hover ? NebulaDesign.SURFACE_RAISED : SURFACE);
					g.fillRoundRect(1, 1, w - 2, h - 2, RADIUS, RADIUS);
					g.setColor(hover
							? alpha(NebulaDesign.ACCENT, 120)
							: NebulaDesign.BORDER_STRONG);
					g.drawRoundRect(1, 1, w - 3, h - 3, RADIUS, RADIUS);
					g.setColor(TEXT);
				}
				g.setFont(getFont());
				FontMetrics metrics = g.getFontMetrics();
				String value = getText();
				g.drawString(value,
						(w - metrics.stringWidth(value)) / 2,
						(h - metrics.getHeight()) / 2 + metrics.getAscent());
				g.dispose();
			}
		};
		button.setFont(NebulaDesign.fontMedium(12.5f));
		button.setContentAreaFilled(false);
		button.setOpaque(false);
		button.setRolloverEnabled(true);
		button.setBorder(new EmptyBorder(9, 16, 9, 16));
		NebulaWidgets.polishButton(button);
		return button;
	}

	private static Font font(int style, int size)
	{
		float scaled = Math.max(9, size);
		if (style == Font.BOLD)
		{
			return NebulaDesign.fontSemiBold(scaled);
		}
		return NebulaDesign.fontBody(scaled);
	}

	/**
	 * Emphasis tone for small labels: deepened in day mode for contrast on
	 * soft tints, kept bright in night mode where darkening would vanish.
	 */
	private static Color deep(Color color)
	{
		if (StudioNightTheme.isNightMode())
		{
			return new Color(color.getRed(), color.getGreen(), color.getBlue());
		}
		return color.darker();
	}

	private static Color tint(Color color, float amount)
	{
		if (StudioNightTheme.isNightMode())
		{
			float accentAmount = Math.max(0.10f, Math.min(0.42f, 1.0f - amount));
			Color base = amount > 0.70f
					? StudioNightTheme.NIGHT_SURFACE
					: StudioNightTheme.NIGHT_PANEL_INK;
			return StudioNightTheme.blend(base, color, accentAmount);
		}
		int r = (int)(color.getRed() + (255 - color.getRed()) * amount);
		int g = (int)(color.getGreen() + (255 - color.getGreen()) * amount);
		int b = (int)(color.getBlue() + (255 - color.getBlue()) * amount);
		return new Color(Math.min(255, r), Math.min(255, g), Math.min(255, b));
	}

	private static Color themed(Color day, Color night)
	{
		return StudioNightTheme.isNightMode() ? night : day;
	}

	private static Color glass(int alpha)
	{
		return themed(
				StudioNightTheme.withAlpha(NebulaDesign.DAY_SURFACE, alpha),
				StudioNightTheme.withAlpha(
						NebulaDesign.NIGHT_SURFACE_ALT,
						Math.min(255, alpha + 16)));
	}

	private static Color track()
	{
		return themed(NebulaDesign.DAY_INSET, NebulaDesign.NIGHT_INSET);
	}

	private static Color alpha(Color color, int amount)
	{
		return new Color(color.getRed(), color.getGreen(), color.getBlue(),
				Math.max(0, Math.min(255, amount)));
	}

	private static String compact(String value, int max)
	{
		if (value == null)
		{
			return "";
		}
		String clean = value.replace('\n', ' ').replace('\r', ' ').trim();
		return clean.length() <= max ? clean : clean.substring(0, Math.max(0, max - 3)) + "...";
	}

	private static String formatCount(long value)
	{
		return String.format(Locale.US, "%,d", Long.valueOf(Math.max(0L, value)));
	}

	private static String percent(double value)
	{
		return new DecimalFormat("0.0%").format(value);
	}

	private static String percentWhole(double value)
	{
		return new DecimalFormat("0%").format(value);
	}

	private static String edgeName(File file)
	{
		return file == null ? "No edge file selected" : file.getPath();
	}

	private static void drawClippedString(
			Graphics2D g,
			String value,
			int x,
			int y,
			int maxWidth)
	{
		if (value == null || maxWidth <= 0)
		{
			return;
		}
		FontMetrics metrics = g.getFontMetrics();
		String text = value;
		if (metrics.stringWidth(text) <= maxWidth)
		{
			g.drawString(text, x, y);
			return;
		}
		while (text.length() > 4 && metrics.stringWidth(text + "...") > maxWidth)
		{
			text = text.substring(0, text.length() - 1);
		}
		g.drawString(text + "...", x, y);
	}

	private static void drawWrapped(
			Graphics2D g,
			String value,
			int x,
			int y,
			int maxWidth,
			int maxLines,
			int lineHeight)
	{
		if (value == null || value.length() == 0 || maxWidth <= 0)
		{
			return;
		}
		FontMetrics metrics = g.getFontMetrics();
		String[] words = value.split("\\s+");
		StringBuilder line = new StringBuilder();
		int lineNumber = 0;
		for (int i = 0; i < words.length; i++)
		{
			String candidate = line.length() == 0
					? words[i] : line.toString() + " " + words[i];
			if (metrics.stringWidth(candidate) > maxWidth && line.length() > 0)
			{
				g.drawString(line.toString(), x, y + lineNumber * lineHeight);
				lineNumber++;
				line = new StringBuilder(words[i]);
				if (lineNumber >= maxLines)
				{
					return;
				}
			}
			else
			{
				line = new StringBuilder(candidate);
			}
		}
		if (line.length() > 0 && lineNumber < maxLines)
		{
			drawClippedString(g, line.toString(), x, y + lineNumber * lineHeight, maxWidth);
		}
	}

	/** Nebula KPI tile: quiet surface, leading hue dot, eyebrow + value. */
	private static void drawMetric(
			Graphics2D g,
			int x,
			int y,
			int w,
			int h,
			String label,
			String value,
			Color color)
	{
		g.setColor(SURFACE_ALT);
		g.fillRoundRect(x, y, w, h, RADIUS, RADIUS);
		g.setColor(BORDER);
		g.drawRoundRect(x, y, w, h, RADIUS, RADIUS);
		g.setColor(color);
		g.fillOval(x + 10, y + 10, 7, 7);
		g.setFont(font(Font.BOLD, 9));
		g.setColor(NebulaDesign.TEXT_TERTIARY);
		drawClippedString(g, label.toUpperCase(Locale.US), x + 22, y + 17, w - 32);
		g.setFont(font(Font.BOLD, 17));
		g.setColor(TEXT);
		drawClippedString(g, value, x + 11, y + h - 11, w - 21);
	}

	private static abstract class VisualPanel extends JPanel
	{
		SimulatorHelpPanel.Snapshot snapshot = SimulatorHelpPanel.Snapshot.empty();
		GraphAnalysisEngine.Analysis analysis =
				GraphAnalysisEngine.Analysis.missing(null, null, "");
		float phase;

		VisualPanel()
		{
			setOpaque(false);
		}

		void setState(
				SimulatorHelpPanel.Snapshot snapshot,
				GraphAnalysisEngine.Analysis analysis)
		{
			this.snapshot = snapshot == null ? SimulatorHelpPanel.Snapshot.empty() : snapshot;
			this.analysis = analysis == null
					? GraphAnalysisEngine.Analysis.missing(null, null, "")
					: analysis;
			repaint();
		}

		void advance()
		{
			phase += 0.028f;
			repaint();
		}

		void prepare(Graphics2D g)
		{
			NebulaDesign.prepare(g);
			g.setFont(font(Font.PLAIN, 11));
		}
	}

	private static final class RoundedPanel extends JPanel
	{
		RoundedPanel(BorderLayout layout)
		{
			super(layout);
			setOpaque(false);
		}

		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D)graphics.create();
			NebulaWidgets.paintCard(g, 0, 0, getWidth(), getHeight(),
					NebulaDesign.RADIUS_CARD);
			g.dispose();
			super.paintComponent(graphics);
		}
	}

	/** Hero card: Nebula card plus a very subtle two-tone aurora wash. */
	private static final class HeroCardPanel extends JPanel
	{
		HeroCardPanel(BorderLayout layout)
		{
			super(layout);
			setOpaque(false);
		}

		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D)graphics.create();
			int w = getWidth();
			int h = getHeight();
			NebulaWidgets.paintCard(g, 0, 0, w, h, NebulaDesign.RADIUS_CARD);
			boolean night = StudioNightTheme.isNightMode();
			java.awt.Shape oldClip = g.getClip();
			g.clip(new java.awt.geom.RoundRectangle2D.Float(
					0, 0, w - 1, h - 3, NebulaDesign.RADIUS_CARD, NebulaDesign.RADIUS_CARD));
			g.setPaint(new java.awt.RadialGradientPaint(
					new java.awt.geom.Point2D.Float(w * 0.24f, 0f),
					Math.max(1f, w * 0.42f),
					new float[] {0f, 1f},
					new Color[] {
							alpha(BLUE, night ? 26 : 15),
							alpha(BLUE, 0)
					}));
			g.fillRect(0, 0, w, h);
			g.setPaint(new java.awt.RadialGradientPaint(
					new java.awt.geom.Point2D.Float(w * 0.86f, h),
					Math.max(1f, w * 0.38f),
					new float[] {0f, 1f},
					new Color[] {
							alpha(PURPLE, night ? 22 : 13),
							alpha(PURPLE, 0)
					}));
			g.fillRect(0, 0, w, h);
			g.setClip(oldClip);
			g.dispose();
			super.paintComponent(graphics);
		}
	}

	private static final class ScrollablePagePanel extends JPanel implements Scrollable
	{
		private Component header;
		private Component dashboard;
		private Component explorer;

		void setSections(
				Component header,
				Component dashboard,
				Component explorer)
		{
			this.header = header;
			this.dashboard = dashboard;
			this.explorer = explorer;
		}

		public void doLayout()
		{
			int width = getWidth();
			int y = 0;
			int headerHeight = headerHeight();
			int dashboardHeight = dashboardHeight();
			int explorerHeight = explorerHeight();
			if (header != null)
			{
				header.setBounds(0, y, width, headerHeight);
			}
			y += headerHeight + HEADER_DASHBOARD_GAP;
			if (dashboard != null)
			{
				dashboard.setBounds(0, y, width, dashboardHeight);
			}
			y += dashboardHeight + DASHBOARD_EXPLORER_GAP;
			if (explorer != null)
			{
				explorer.setBounds(0, y, width, explorerHeight);
			}
		}

		public Dimension getPreferredSize()
		{
			int width = preferredWidth();
			int height = headerHeight()
					+ HEADER_DASHBOARD_GAP
					+ dashboardHeight()
					+ DASHBOARD_EXPLORER_GAP
					+ explorerHeight();
			return new Dimension(width, height);
		}

		public Dimension getPreferredScrollableViewportSize()
		{
			return getPreferredSize();
		}

		public int getScrollableUnitIncrement(
				Rectangle visibleRect,
				int orientation,
				int direction)
		{
			return 24;
		}

		public int getScrollableBlockIncrement(
				Rectangle visibleRect,
				int orientation,
				int direction)
		{
			return Math.max(120, visibleRect.height - 80);
		}

		public boolean getScrollableTracksViewportWidth()
		{
			return true;
		}

		public boolean getScrollableTracksViewportHeight()
		{
			return false;
		}

		private int preferredWidth()
		{
			int width = 1180;
			if (header != null)
			{
				width = Math.max(width, header.getPreferredSize().width);
			}
			if (dashboard != null)
			{
				width = Math.max(width, dashboard.getPreferredSize().width);
			}
			if (explorer != null)
			{
				width = Math.max(width, explorer.getPreferredSize().width);
			}
			if (getParent() instanceof JViewport)
			{
				width = Math.max(width, ((JViewport)getParent()).getWidth());
			}
			return width;
		}

		private int headerHeight()
		{
			if (header == null)
			{
				return 126;
			}
			return Math.max(126, header.getPreferredSize().height);
		}

		private int dashboardHeight()
		{
			int targetHeight = DASHBOARD_MIN_HEIGHT;
			if (getParent() instanceof JViewport)
			{
				int viewportHeight = ((JViewport)getParent()).getHeight();
				if (viewportHeight > 0)
				{
					targetHeight = viewportHeight
							- headerHeight()
							- HEADER_DASHBOARD_GAP
							- DASHBOARD_EXPLORER_GAP
							- EXPLORER_FIRST_VIEW_HINT;
				}
			}
			return Math.max(DASHBOARD_MIN_HEIGHT, targetHeight);
		}

		private int explorerHeight()
		{
			if (explorer == null)
			{
				return 650;
			}
			return Math.max(600, explorer.getPreferredSize().height);
		}
	}

	private static final class ExplorationPanel extends JPanel
	{
		private final ExplorerHeroPanel heroPanel = new ExplorerHeroPanel();
		private final CommunityTableModel communityModel =
				new CommunityTableModel();
		private final JTable communityTable = new JTable(communityModel);
		private final CommunityDetailPanel communityDetailPanel =
				new CommunityDetailPanel();
		private final NodeTableModel nodeModel = new NodeTableModel();
		private final JTable nodeTable = new JTable(nodeModel);
		private final TableRowSorter<NodeTableModel> nodeSorter =
				new TableRowSorter<NodeTableModel>(nodeModel);
		private final NodeDetailPanel nodeDetailPanel = new NodeDetailPanel();
		private final JTextField searchField = new JTextField();
		private final JButton clearCommunityButton =
				actionButton("Clear community", false);
		private GraphAnalysisEngine.Analysis analysis =
				GraphAnalysisEngine.Analysis.missing(null, null, "");
		private Integer activeCommunityFilter;
		private boolean adjustingSelection;

		ExplorationPanel()
		{
			setOpaque(false);
			setLayout(new BorderLayout(0, 10));
			heroPanel.setPreferredSize(new Dimension(1000, 92));
			add(heroPanel, BorderLayout.NORTH);

			styleTable(communityTable);
			styleTable(nodeTable);
			communityTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
			nodeTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
			nodeTable.setRowSorter(nodeSorter);
			searchField.setFont(NebulaDesign.fontBody(12.5f));
			searchField.setForeground(TEXT);
			searchField.setBackground(SURFACE_ALT);
			searchField.setCaretColor(NebulaDesign.currentAccent());
			searchField.setBorder(BorderFactory.createCompoundBorder(
					BorderFactory.createLineBorder(BORDER),
					new EmptyBorder(8, 10, 8, 10)));
			searchField.getDocument().addDocumentListener(new DocumentListener()
			{
				public void insertUpdate(DocumentEvent event)
				{
					applyNodeFilter();
				}

				public void removeUpdate(DocumentEvent event)
				{
					applyNodeFilter();
				}

				public void changedUpdate(DocumentEvent event)
				{
					applyNodeFilter();
				}
			});

			clearCommunityButton.addActionListener(new ActionListener()
			{
				public void actionPerformed(ActionEvent event)
				{
					activeCommunityFilter = null;
					communityTable.clearSelection();
					communityDetailPanel.setCommunity(analysis, null);
					applyNodeFilter();
					selectFirstVisibleNode();
				}
			});

			communityTable.getSelectionModel().addListSelectionListener(
					new ListSelectionListener()
					{
						public void valueChanged(ListSelectionEvent event)
						{
							if (!event.getValueIsAdjusting()
									&& !adjustingSelection)
							{
								communitySelectionChanged();
							}
						}
					});
			nodeTable.getSelectionModel().addListSelectionListener(
					new ListSelectionListener()
					{
						public void valueChanged(ListSelectionEvent event)
						{
							if (!event.getValueIsAdjusting())
							{
								nodeSelectionChanged();
							}
						}
					});

			JPanel workspace = new JPanel(new GridBagLayout());
			workspace.setOpaque(false);
			GridBagConstraints c = new GridBagConstraints();
			c.fill = GridBagConstraints.BOTH;
			c.insets = new Insets(0, 0, 0, 12);
			c.gridx = 0;
			c.gridy = 0;
			c.weightx = 0.58;
			c.weighty = 1.0;
			workspace.add(explorerCard(
					"Community Explorer",
					"Select a community to inspect membership, edge boundaries, and recommendation pull.",
					createCommunityBody()), c);

			c.gridx = 1;
			c.weightx = 0.42;
			c.insets = new Insets(0, 0, 0, 0);
			workspace.add(explorerCard(
					"Node Inspector",
					"Search or click a node to inspect its role, community, degrees, and neighbors.",
					createNodeBody()), c);
			add(workspace, BorderLayout.CENTER);
		}

		void setState(
				SimulatorHelpPanel.Snapshot snapshot,
				GraphAnalysisEngine.Analysis analysis)
		{
			this.analysis = analysis == null
					? GraphAnalysisEngine.Analysis.missing(null, null, "")
					: analysis;
			heroPanel.setState(snapshot, this.analysis);
			communityModel.setRows(this.analysis.communityDetails);
			nodeModel.setRows(this.analysis.nodeDetails);
			if (activeCommunityFilter != null
					&& findCommunity(activeCommunityFilter.intValue()) == null)
			{
				activeCommunityFilter = null;
			}
			restoreCommunitySelection();
			applyNodeFilter();
			selectFirstVisibleNode();
			repaint();
		}

		protected void paintComponent(Graphics graphics)
		{
			// Full opaque canvas base beneath the explorer cards: blends with
			// the page background in-app and keeps the standalone render
			// (self-test contract) fully painted.
			Graphics2D g = (Graphics2D)graphics.create();
			NebulaDesign.prepare(g);
			g.setPaint(new GradientPaint(
					0, 0, themed(NebulaDesign.DAY_CANVAS_TOP, NebulaDesign.NIGHT_CANVAS_TOP),
					0, Math.max(1, getHeight()),
					themed(NebulaDesign.DAY_CANVAS_BOTTOM, NebulaDesign.NIGHT_CANVAS_BOTTOM)));
			g.fillRect(0, 0, getWidth(), getHeight());
			g.dispose();
			super.paintComponent(graphics);
		}

		private JPanel createCommunityBody()
		{
			JPanel body = new JPanel(new BorderLayout(0, 8));
			body.setOpaque(false);
			JScrollPane tableScroll = explorerScroll(communityTable);
			body.add(tableScroll, BorderLayout.CENTER);
			communityDetailPanel.setPreferredSize(new Dimension(500, 218));
			body.add(communityDetailPanel, BorderLayout.SOUTH);
			return body;
		}

		private JPanel createNodeBody()
		{
			JPanel body = new JPanel(new BorderLayout(0, 8));
			body.setOpaque(false);
			JPanel toolbar = new JPanel(new BorderLayout(8, 0));
			toolbar.setOpaque(false);
			searchField.setToolTipText("Search by node id, name, community, or role.");
			toolbar.add(searchField, BorderLayout.CENTER);
			toolbar.add(clearCommunityButton, BorderLayout.EAST);
			body.add(toolbar, BorderLayout.NORTH);
			body.add(explorerScroll(nodeTable), BorderLayout.CENTER);
			nodeDetailPanel.setPreferredSize(new Dimension(420, 286));
			body.add(nodeDetailPanel, BorderLayout.SOUTH);
			return body;
		}

		private JPanel explorerCard(String title, String detail, JComponent body)
		{
			JPanel card = new RoundedPanel(new BorderLayout(0, 8));
			card.setBorder(new EmptyBorder(14, 14, 14, 14));
			JPanel header = new JPanel(new BorderLayout(10, 0));
			header.setOpaque(false);
			JLabel titleLabel = new JLabel(title);
			titleLabel.setFont(NebulaDesign.fontTitle());
			titleLabel.setForeground(TEXT);
			JLabel detailLabel = new JLabel(detail);
			detailLabel.setFont(NebulaDesign.fontBody(11f));
			detailLabel.setForeground(NebulaDesign.TEXT_TERTIARY);
			header.add(titleLabel, BorderLayout.WEST);
			header.add(detailLabel, BorderLayout.CENTER);
			card.add(header, BorderLayout.NORTH);
			card.add(body, BorderLayout.CENTER);
			return card;
		}

		private JScrollPane explorerScroll(Component component)
		{
			JScrollPane scroll = new JScrollPane(component);
			if (component instanceof JTable)
			{
				JTable table = (JTable)component;
				table.getTableHeader().setPreferredSize(new Dimension(1, 28));
				scroll.setColumnHeaderView(table.getTableHeader());
			}
			scroll.setBorder(BorderFactory.createLineBorder(BORDER));
			scroll.getViewport().setBackground(SURFACE);
			scroll.setBackground(SURFACE);
			scroll.setVerticalScrollBarPolicy(
					ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
			scroll.setHorizontalScrollBarPolicy(
					ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
			scroll.getVerticalScrollBar().setUnitIncrement(18);
			scroll.getVerticalScrollBar().setUI(new NebulaWidgets.PillScrollBarUI());
			scroll.getVerticalScrollBar().setOpaque(false);
			return scroll;
		}

		private void communitySelectionChanged()
		{
			int row = communityTable.getSelectedRow();
			if (row < 0)
			{
				activeCommunityFilter = null;
				communityDetailPanel.setCommunity(analysis, null);
				applyNodeFilter();
				selectFirstVisibleNode();
				return;
			}
			int modelRow = communityTable.convertRowIndexToModel(row);
			GraphAnalysisEngine.CommunityDetail detail =
					communityModel.getRow(modelRow);
			activeCommunityFilter = Integer.valueOf(detail.id);
			communityDetailPanel.setCommunity(analysis, detail);
			applyNodeFilter();
			selectFirstVisibleNode();
		}

		private void nodeSelectionChanged()
		{
			int row = nodeTable.getSelectedRow();
			if (row < 0)
			{
				nodeDetailPanel.setNode(analysis, null);
				return;
			}
			int modelRow = nodeTable.convertRowIndexToModel(row);
			nodeDetailPanel.setNode(analysis, nodeModel.getRow(modelRow));
		}

		private void applyNodeFilter()
		{
			List<RowFilter<NodeTableModel, Integer>> filters =
					new ArrayList<RowFilter<NodeTableModel, Integer>>();
			if (activeCommunityFilter != null)
			{
				filters.add(new RowFilter<NodeTableModel, Integer>()
				{
					public boolean include(
							Entry<? extends NodeTableModel, ? extends Integer> entry)
					{
						GraphAnalysisEngine.NodeDetail node =
								entry.getModel().getRow(entry.getIdentifier().intValue());
						return node.communityId == activeCommunityFilter.intValue();
					}
				});
			}
			String query = searchField.getText() == null
					? "" : searchField.getText().trim();
			if (query.length() > 0)
			{
				filters.add(RowFilter.regexFilter(
						"(?i)" + Pattern.quote(query)));
			}
			if (filters.isEmpty())
			{
				nodeSorter.setRowFilter(null);
			}
			else if (filters.size() == 1)
			{
				nodeSorter.setRowFilter(filters.get(0));
			}
			else
			{
				nodeSorter.setRowFilter(RowFilter.andFilter(filters));
			}
		}

		private void restoreCommunitySelection()
		{
			if (activeCommunityFilter == null
					&& communityModel.getRowCount() > 0)
			{
				activeCommunityFilter =
						Integer.valueOf(communityModel.getRow(0).id);
			}
			if (activeCommunityFilter == null)
			{
				communityDetailPanel.setCommunity(analysis, null);
				return;
			}
			for (int i = 0; i < communityModel.getRowCount(); i++)
			{
				GraphAnalysisEngine.CommunityDetail detail =
						communityModel.getRow(i);
				if (detail.id == activeCommunityFilter.intValue())
				{
					int view = communityTable.convertRowIndexToView(i);
					if (view >= 0)
					{
						adjustingSelection = true;
						communityTable.getSelectionModel()
								.setSelectionInterval(view, view);
						adjustingSelection = false;
						communityDetailPanel.setCommunity(analysis, detail);
					}
					return;
				}
			}
			activeCommunityFilter = null;
			communityDetailPanel.setCommunity(analysis, null);
		}

		private void selectFirstVisibleNode()
		{
			if (nodeTable.getRowCount() == 0)
			{
				nodeDetailPanel.setNode(analysis, null);
				return;
			}
			if (nodeTable.getSelectedRow() < 0)
			{
				nodeTable.getSelectionModel().setSelectionInterval(0, 0);
			}
			else
			{
				nodeSelectionChanged();
			}
		}

		private GraphAnalysisEngine.CommunityDetail findCommunity(int id)
		{
			for (GraphAnalysisEngine.CommunityDetail detail : analysis.communityDetails)
			{
				if (detail.id == id)
				{
					return detail;
				}
			}
			return null;
		}

		private void styleTable(JTable table)
		{
			table.setFillsViewportHeight(true);
			table.setRowHeight(32);
			table.setFont(NebulaDesign.fontBody(12f));
			table.setForeground(TEXT);
			table.setBackground(SURFACE);
			table.setGridColor(BORDER);
			table.setShowVerticalLines(false);
			table.setIntercellSpacing(new Dimension(0, 1));
			table.setSelectionBackground(NebulaDesign.SELECTION);
			table.setSelectionForeground(TEXT);
			table.getTableHeader().setFont(NebulaDesign.fontMedium(11f));
			table.getTableHeader().setForeground(MUTED);
			table.getTableHeader().setBackground(SURFACE_ALT);
			table.getTableHeader().setBorder(
					BorderFactory.createMatteBorder(0, 0, 1, 0,
							NebulaDesign.currentBorderStrong()));
			table.setDefaultRenderer(Object.class, new ExplorerCellRenderer());
			table.setDefaultRenderer(Integer.class, new ExplorerCellRenderer());
			table.setDefaultRenderer(Long.class, new ExplorerCellRenderer());
		}
	}

	private static final class ExplorerHeroPanel extends VisualPanel
	{
		protected void paintComponent(Graphics graphics)
		{
			super.paintComponent(graphics);
			Graphics2D g = (Graphics2D)graphics.create();
			prepare(g);
			int w = getWidth();
			int h = getHeight();
			int pad = 16;
			NebulaWidgets.paintCard(g, 0, 0, w, h, NebulaDesign.RADIUS_CARD);

			NebulaDesign.paintEyebrow(g, "EXPLORER", pad, 22,
					NebulaDesign.currentAccent());
			g.setFont(NebulaDesign.fontTitle());
			g.setColor(TEXT);
			drawClippedString(g, "Explorer layer", pad, 44, w / 3);
			g.setFont(font(Font.PLAIN, 11));
			g.setColor(MUTED);
			drawClippedString(g,
					"Community membership, node roles, edge boundaries, and recommendation pull.",
					pad,
					64,
					w / 2);

			int tileW = Math.max(110, Math.min(170, (w - pad - w / 2 - 24) / 4));
			int x = w - pad - tileW * 4 - 24;
			drawMetric(g, x, 17, tileW, 54,
					"communities",
					formatCount(analysis.communityDetails.size()),
					GREEN);
			drawMetric(g, x + tileW + 8, 17, tileW, 54,
					"inspectable nodes",
					formatCount(analysis.nodeDetails.size()),
					BLUE);
			drawMetric(g, x + (tileW + 8) * 2, 17, tileW, 54,
					"active nodes",
					formatCount(analysis.activeNodeCount),
					PURPLE);
			drawMetric(g, x + (tileW + 8) * 3, 17, tileW, 54,
					"method",
					analysis.gephiCommunityCount ? "Gephi" : "Fallback",
					AMBER);
			g.dispose();
		}
	}

	private static final class CommunityDetailPanel extends VisualPanel
	{
		private GraphAnalysisEngine.CommunityDetail detail;

		void setCommunity(
				GraphAnalysisEngine.Analysis analysis,
				GraphAnalysisEngine.CommunityDetail detail)
		{
			this.analysis = analysis == null
					? GraphAnalysisEngine.Analysis.missing(null, null, "")
					: analysis;
			this.detail = detail;
			repaint();
		}

		protected void paintComponent(Graphics graphics)
		{
			super.paintComponent(graphics);
			Graphics2D g = (Graphics2D)graphics.create();
			prepare(g);
			int w = getWidth();
			int h = getHeight();
			g.setColor(SURFACE_ALT);
			g.fillRoundRect(0, 0, w - 1, h - 1, RADIUS, RADIUS);
			g.setColor(BORDER);
			g.drawRoundRect(0, 0, w - 1, h - 1, RADIUS, RADIUS);
			if (detail == null)
			{
				drawEmptyExplorerState(
						g,
						"Select a community",
						"Click a row above to inspect nodes, internal edges, outgoing boundaries, top recommendations, and the structural explanation.",
						w,
						h);
				g.dispose();
				return;
			}

			g.setFont(font(Font.BOLD, 14));
			g.setColor(TEXT);
			drawClippedString(g, compact(detail.name, 48), 14, 24, w - 190);
			g.setFont(font(Font.BOLD, 10));
			g.setColor(deep(GREEN));
			drawClippedString(g, detail.methodDetail, w - 172, 24, 158);

			int metricY = 38;
			int metricW = Math.max(54, (w - 62) / 5);
			drawTinyMetric(g, 14, metricY, metricW, 42, "size", formatCount(detail.size), BLUE);
			drawTinyMetric(g, 22 + metricW, metricY, metricW, 42, "active", formatCount(detail.activeNodeCount), PURPLE);
			drawTinyMetric(g, 30 + metricW * 2, metricY, metricW, 42, "internal", formatCount(detail.internalEdgeCount), GREEN);
			drawTinyMetric(g, 38 + metricW * 3, metricY, metricW, 42, "leaving", formatCount(detail.externalEdgeCount), AMBER);
			drawTinyMetric(g, 46 + metricW * 4, metricY, metricW, 42, "recs", formatCount(detail.recommendationEdgeCount), RED);

			int columnY = 94;
			int columnH = h - columnY - 48;
			int columnW = Math.max(100, (w - 58) / 4);
			drawListBlock(g, 14, columnY, columnW, columnH,
					"NODES", detail.nodePreview);
			drawEdgeBlock(g, 22 + columnW, columnY, columnW, columnH,
					"INTERNAL EDGES", detail.internalEdgeSamples);
			drawEdgeBlock(g, 30 + columnW * 2, columnY, columnW, columnH,
					"LEAVING EDGES", detail.externalEdgeSamples);
			drawScoreBlock(g, 38 + columnW * 3, columnY, columnW, columnH,
					"TOP RECS", detail.topRecommendations);

			g.setFont(font(Font.BOLD, 10));
			g.setColor(deep(AMBER));
			drawClippedString(g,
					"WHY THIS GROUP EXISTS",
					14,
					h - 32,
					w - 28);
			g.setFont(font(Font.PLAIN, 10));
			g.setColor(MUTED);
			drawWrapped(g, detail.why, 14, h - 16, w - 28, 2, 13);
			g.dispose();
		}
	}

	private static final class NodeDetailPanel extends VisualPanel
	{
		private GraphAnalysisEngine.NodeDetail node;

		void setNode(
				GraphAnalysisEngine.Analysis analysis,
				GraphAnalysisEngine.NodeDetail node)
		{
			this.analysis = analysis == null
					? GraphAnalysisEngine.Analysis.missing(null, null, "")
					: analysis;
			this.node = node;
			repaint();
		}

		protected void paintComponent(Graphics graphics)
		{
			super.paintComponent(graphics);
			Graphics2D g = (Graphics2D)graphics.create();
			prepare(g);
			int w = getWidth();
			int h = getHeight();
			g.setColor(SURFACE_ALT);
			g.fillRoundRect(0, 0, w - 1, h - 1, RADIUS, RADIUS);
			g.setColor(BORDER);
			g.drawRoundRect(0, 0, w - 1, h - 1, RADIUS, RADIUS);
			if (node == null)
			{
				drawEmptyExplorerState(
						g,
						"Select a node",
						"Search or click a row above to inspect its community, component, degrees, original/recommended target counts, neighbors, and role.",
						w,
						h);
				g.dispose();
				return;
			}

			Color roleColor = nodeColor(node);
			g.setColor(tint(roleColor, 0.84f));
			g.fillRoundRect(12, 12, w - 24, 42, RADIUS, RADIUS);
			g.setColor(alpha(roleColor, 92));
			g.drawRoundRect(12, 12, w - 24, 42, RADIUS, RADIUS);
			g.setFont(font(Font.BOLD, 14));
			g.setColor(TEXT);
			drawClippedString(g, node.name + "  #" + node.id, 24, 32, w - 170);
			g.setFont(font(Font.BOLD, 10));
			g.setColor(deep(roleColor));
			drawClippedString(g, node.role.toUpperCase(Locale.US), w - 138, 32, 112);

			int metricY = 66;
			int metricW = Math.max(56, (w - 60) / 4);
			drawTinyMetric(g, 12, metricY, metricW, 42, "in", formatCount(node.inDegree), BLUE);
			drawTinyMetric(g, 20 + metricW, metricY, metricW, 42, "out", formatCount(node.outDegree), GREEN);
			drawTinyMetric(g, 28 + metricW * 2, metricY, metricW, 42, "degree", formatCount(node.totalDegree), PURPLE);
			drawTinyMetric(g, 36 + metricW * 3, metricY, metricW, 42, "rec target", formatCount(node.recommendedTargetCount), AMBER);

			int infoY = 122;
			g.setFont(font(Font.BOLD, 10));
			g.setColor(deep(BLUE));
			drawClippedString(g, "COMMUNITY", 14, infoY, w - 28);
			g.setFont(font(Font.PLAIN, 10));
			g.setColor(TEXT);
			drawClippedString(g, compact(node.communityName, 46), 14, infoY + 17, w - 28);

			g.setFont(font(Font.BOLD, 10));
			g.setColor(deep(PURPLE));
			drawClippedString(g, "COMPONENT + BOUNDARY", 14, infoY + 42, w - 28);
			g.setFont(font(Font.PLAIN, 10));
			g.setColor(MUTED);
			drawClippedString(g,
					"component size " + formatCount(node.componentSize)
							+ " | neighbors " + formatCount(node.neighborCount)
							+ " | external neighbors " + formatCount(node.externalNeighborCount),
					14,
					infoY + 59,
					w - 28);

			int listY = infoY + 82;
			int listH = h - listY - 58;
			if (listH >= 46)
			{
				drawScoreBlock(g, 14, listY, w - 28, listH,
						"NEIGHBORS", node.neighbors);
			}

			g.setFont(font(Font.BOLD, 10));
			g.setColor(deep(roleColor));
			drawClippedString(g, "INSPECTOR READ", 14, h - 32, w - 28);
			g.setFont(font(Font.PLAIN, 10));
			g.setColor(MUTED);
			drawWrapped(g, node.why, 14, h - 16, w - 28, 2, 13);
			g.dispose();
		}
	}

	private static final class CommunityTableModel extends AbstractTableModel
	{
		private final String[] columns = {
				"ID", "Size", "Top nodes", "Top hubs",
				"Internal", "External", "Dominant target", "Source"
		};
		private List<GraphAnalysisEngine.CommunityDetail> rows =
				new ArrayList<GraphAnalysisEngine.CommunityDetail>();

		void setRows(List<GraphAnalysisEngine.CommunityDetail> rows)
		{
			this.rows = rows == null
					? new ArrayList<GraphAnalysisEngine.CommunityDetail>()
					: new ArrayList<GraphAnalysisEngine.CommunityDetail>(rows);
			fireTableDataChanged();
		}

		GraphAnalysisEngine.CommunityDetail getRow(int row)
		{
			return rows.get(row);
		}

		public int getRowCount()
		{
			return rows.size();
		}

		public int getColumnCount()
		{
			return columns.length;
		}

		public String getColumnName(int column)
		{
			return columns[column];
		}

		public Class<?> getColumnClass(int column)
		{
			if (column == 4 || column == 5)
			{
				return Long.class;
			}
			return column == 0 || column == 1 ? Integer.class : String.class;
		}

		public Object getValueAt(int rowIndex, int columnIndex)
		{
			GraphAnalysisEngine.CommunityDetail row = rows.get(rowIndex);
			switch (columnIndex)
			{
				case 0:
					return Integer.valueOf(row.id);
				case 1:
					return Integer.valueOf(row.size);
				case 2:
					return scoreNames(row.topNodes, 3);
				case 3:
					return scoreNames(row.topHubs, 3);
				case 4:
					return Long.valueOf(row.internalEdgeCount);
				case 5:
					return Long.valueOf(row.externalEdgeCount);
				case 6:
					return row.dominantRecommendedTarget
							+ (row.dominantRecommendedTargetCount > 0
									? " (" + row.dominantRecommendedTargetCount + ")"
									: "");
				default:
					return row.methodDetail;
			}
		}
	}

	private static final class NodeTableModel extends AbstractTableModel
	{
		private final String[] columns = {
				"ID", "Name", "Community", "In", "Out",
				"Degree", "Original", "Recommended", "Role"
		};
		private List<GraphAnalysisEngine.NodeDetail> rows =
				new ArrayList<GraphAnalysisEngine.NodeDetail>();

		void setRows(List<GraphAnalysisEngine.NodeDetail> rows)
		{
			this.rows = rows == null
					? new ArrayList<GraphAnalysisEngine.NodeDetail>()
					: new ArrayList<GraphAnalysisEngine.NodeDetail>(rows);
			fireTableDataChanged();
		}

		GraphAnalysisEngine.NodeDetail getRow(int row)
		{
			return rows.get(row);
		}

		public int getRowCount()
		{
			return rows.size();
		}

		public int getColumnCount()
		{
			return columns.length;
		}

		public String getColumnName(int column)
		{
			return columns[column];
		}

		public Class<?> getColumnClass(int column)
		{
			return column == 0 || column == 3 || column == 4
					|| column == 5 || column == 6 || column == 7
							? Integer.class : String.class;
		}

		public Object getValueAt(int rowIndex, int columnIndex)
		{
			GraphAnalysisEngine.NodeDetail row = rows.get(rowIndex);
			switch (columnIndex)
			{
				case 0:
					return Integer.valueOf(row.id);
				case 1:
					return row.name;
				case 2:
					return row.communityId == GraphAnalysisEngine.UNASSIGNED_COMMUNITY_ID
							? "unassigned" : Integer.toString(row.communityId);
				case 3:
					return Integer.valueOf(row.inDegree);
				case 4:
					return Integer.valueOf(row.outDegree);
				case 5:
					return Integer.valueOf(row.totalDegree);
				case 6:
					return Integer.valueOf(row.originalTargetCount);
				case 7:
					return Integer.valueOf(row.recommendedTargetCount);
				default:
					return row.role;
			}
		}
	}

	private static final class ExplorerCellRenderer extends DefaultTableCellRenderer
	{
		public Component getTableCellRendererComponent(
				JTable table,
				Object value,
				boolean selected,
				boolean focused,
				int row,
				int column)
		{
			JLabel label = (JLabel)super.getTableCellRendererComponent(
					table,
					value,
					selected,
					focused,
					row,
					column);
			if (value instanceof Number)
			{
				label.setFont(NebulaDesign.fontMono(11.5f));
			}
			else
			{
				label.setFont(NebulaDesign.fontBody(12f));
			}
			if (selected)
			{
				label.setBackground(NebulaDesign.SELECTION);
				label.setForeground(TEXT);
				label.setBorder(BorderFactory.createCompoundBorder(
						BorderFactory.createMatteBorder(0, column == 0 ? 2 : 0, 0, 0,
								NebulaDesign.currentAccent()),
						new EmptyBorder(0, column == 0 ? 8 : 10, 0, 8)));
			}
			else
			{
				label.setBackground(SURFACE);
				label.setForeground(column == 0 ? deep(BLUE) : TEXT);
				label.setBorder(new EmptyBorder(0, 10, 0, 8));
			}
			label.setToolTipText(value == null ? "" : String.valueOf(value));
			return label;
		}
	}

	private static void drawEmptyExplorerState(
			Graphics2D g,
			String title,
			String body,
			int w,
			int h)
	{
		g.setFont(font(Font.BOLD, 14));
		g.setColor(TEXT);
		drawClippedString(g, title, 16, h / 2 - 10, w - 32);
		g.setFont(font(Font.PLAIN, 11));
		g.setColor(MUTED);
		drawWrapped(g, body, 16, h / 2 + 12, w - 32, 3, 14);
	}

	private static void drawTinyMetric(
			Graphics2D g,
			int x,
			int y,
			int w,
			int h,
			String label,
			String value,
			Color color)
	{
		g.setColor(glass(210));
		g.fillRoundRect(x, y, w, h, RADIUS, RADIUS);
		g.setColor(BORDER);
		g.drawRoundRect(x, y, w, h, RADIUS, RADIUS);
		g.setColor(color);
		g.fillOval(x + 8, y + 7, 5, 5);
		g.setFont(font(Font.BOLD, 9));
		g.setColor(NebulaDesign.TEXT_TERTIARY);
		drawClippedString(g, label.toUpperCase(Locale.US), x + 17, y + 14, w - 25);
		g.setFont(font(Font.BOLD, 14));
		g.setColor(TEXT);
		drawClippedString(g, value, x + 8, y + 32, w - 16);
	}

	private static void drawListBlock(
			Graphics2D g,
			int x,
			int y,
			int w,
			int h,
			String title,
			List<String> values)
	{
		drawBlockFrame(g, x, y, w, h, title, BLUE);
		g.setFont(font(Font.PLAIN, 10));
		g.setColor(TEXT);
		if (values == null || values.isEmpty())
		{
			drawClippedString(g, "No rows yet.", x + 10, y + 34, w - 20);
			return;
		}
		int lineY = y + 34;
		for (int i = 0; i < values.size() && lineY < y + h - 8; i++)
		{
			drawClippedString(g, values.get(i), x + 10, lineY, w - 20);
			lineY += 15;
		}
	}

	private static void drawScoreBlock(
			Graphics2D g,
			int x,
			int y,
			int w,
			int h,
			String title,
			List<GraphAnalysisEngine.NodeScore> values)
	{
		drawBlockFrame(g, x, y, w, h, title, GREEN);
		g.setFont(font(Font.PLAIN, 10));
		g.setColor(TEXT);
		if (values == null || values.isEmpty())
		{
			drawClippedString(g, "No rows yet.", x + 10, y + 34, w - 20);
			return;
		}
		int lineY = y + 34;
		for (int i = 0; i < values.size() && lineY < y + h - 8; i++)
		{
			GraphAnalysisEngine.NodeScore score = values.get(i);
			drawClippedString(g,
					compact(score.name, 24) + "  " + score.value,
					x + 10,
					lineY,
					w - 20);
			lineY += 15;
		}
	}

	private static void drawEdgeBlock(
			Graphics2D g,
			int x,
			int y,
			int w,
			int h,
			String title,
			List<GraphAnalysisEngine.EdgeSample> values)
	{
		drawBlockFrame(g, x, y, w, h, title, AMBER);
		g.setFont(font(Font.PLAIN, 10));
		g.setColor(TEXT);
		if (values == null || values.isEmpty())
		{
			drawClippedString(g, "No sampled edges.", x + 10, y + 34, w - 20);
			return;
		}
		int lineY = y + 34;
		for (int i = 0; i < values.size() && lineY < y + h - 8; i++)
		{
			drawClippedString(g, values.get(i).summary(), x + 10, lineY, w - 20);
			lineY += 15;
		}
	}

	private static void drawBlockFrame(
			Graphics2D g,
			int x,
			int y,
			int w,
			int h,
			String title,
			Color color)
	{
		g.setColor(glass(180));
		g.fillRoundRect(x, y, w, h, RADIUS, RADIUS);
		g.setColor(BORDER);
		g.drawRoundRect(x, y, w, h, RADIUS, RADIUS);
		g.setColor(color);
		g.fillOval(x + 10, y + 10, 5, 5);
		g.setFont(font(Font.BOLD, 9));
		g.setColor(deep(color));
		drawClippedString(g, title, x + 20, y + 17, w - 30);
	}

	private static String scoreNames(
			List<GraphAnalysisEngine.NodeScore> scores,
			int limit)
	{
		if (scores == null || scores.isEmpty())
		{
			return "none";
		}
		StringBuilder builder = new StringBuilder();
		for (int i = 0; i < scores.size() && i < limit; i++)
		{
			if (builder.length() > 0)
			{
				builder.append(", ");
			}
			builder.append(scores.get(i).name);
		}
		return builder.toString();
	}

	private static Color nodeColor(GraphAnalysisEngine.NodeDetail node)
	{
		if (node == null)
		{
			return BLUE;
		}
		if (node.isolate)
		{
			return MUTED;
		}
		if (node.bridge)
		{
			return PURPLE;
		}
		if (node.recommendationSink)
		{
			return GREEN;
		}
		if (node.hub)
		{
			return AMBER;
		}
		return BLUE;
	}

	private static final class HeaderSignalPanel extends VisualPanel
	{
		protected void paintComponent(Graphics graphics)
		{
			super.paintComponent(graphics);
			Graphics2D g = (Graphics2D)graphics.create();
			prepare(g);
			int w = getWidth();
			int h = getHeight();
			int pad = 14;
			String[] labels = {"Dataset", "Edges", "Communities", "Hubs", "Report"};
			String[] values = {
					datasetShort(snapshot),
					formatCount(analysis.validEdgeCount),
					formatCount(analysis.estimatedCommunityCount),
					analysis.topIncomingNodes.isEmpty()
							? "none" : compact(analysis.topIncomingNodes.get(0).name, 16),
					statusShort(analysis)
			};
			Color[] colors = {BLUE, PURPLE, GREEN, AMBER, statusColor(analysis)};
			int boxW = Math.max(100, (w - pad * 2 - 48) / 5);
			int gap = Math.max(8, (w - pad * 2 - boxW * 5) / 4);
			int y = 16;
			int lineY = y + 31;
			int x = pad;
			for (int i = 0; i < 5; i++)
			{
				g.setColor(SURFACE_ALT);
				g.fillRoundRect(x, y, boxW, 62, RADIUS, RADIUS);
				g.setColor(BORDER);
				g.drawRoundRect(x, y, boxW, 62, RADIUS, RADIUS);
				g.setColor(colors[i]);
				g.fillOval(x + 10, y + 11, 7, 7);
				g.setFont(font(Font.BOLD, 9));
				g.setColor(NebulaDesign.TEXT_TERTIARY);
				drawClippedString(g, labels[i].toUpperCase(Locale.US), x + 23, y + 18, boxW - 32);
				g.setFont(font(Font.BOLD, 14));
				g.setColor(TEXT);
				drawClippedString(g, values[i], x + 11, y + 44, boxW - 21);
				if (i < 4)
				{
					int x1 = x + boxW + 3;
					int x2 = x + boxW + gap - 3;
					g.setStroke(new BasicStroke(1.6f));
					g.setColor(NebulaDesign.BORDER_STRONG);
					g.drawLine(x1, lineY, x2, lineY);
					double t = (phase + i * 0.19) % 1.0;
					int px = (int)(x1 + (x2 - x1) * t);
					g.setColor(colors[i + 1]);
					g.fill(new Ellipse2D.Double(px - 2.5, lineY - 2.5, 5, 5));
				}
				x += boxW + gap;
			}
			g.setFont(NebulaDesign.fontSmall());
			g.setColor(NebulaDesign.TEXT_TERTIARY);
			drawClippedString(g,
					"Reads existing graph artifacts only; recommendation logic is untouched.",
					pad,
					h - 5,
					w - pad * 2);
			g.dispose();
		}

		private static String statusShort(GraphAnalysisEngine.Analysis analysis)
		{
			if (analysis == null || !analysis.available)
			{
				return "Waiting";
			}
			if (analysis.validEdgeCount == 0L)
			{
				return "No edges yet";
			}
			if (analysis.malformedEdgeLines > 0L
					|| analysis.unknownNodeEdges > 0L
					|| analysis.healthScore < 70)
			{
				return "Needs review";
			}
			if (analysis.healthScore < 86)
			{
				return "Usable";
			}
			return "Healthy";
		}
	}

	private static final class HealthPanel extends VisualPanel
	{
		protected void paintComponent(Graphics graphics)
		{
			super.paintComponent(graphics);
			Graphics2D g = (Graphics2D)graphics.create();
			prepare(g);
			int w = getWidth();
			int h = getHeight();
			int pad = 14;
			if (w < 260 || h < 220)
			{
				g.dispose();
				return;
			}

			Color color = statusColor(analysis);
			int heroH = Math.min(126, Math.max(104, h / 3));
			drawHealthHero(g, pad, 8, w - pad * 2, heroH, color);

			int gap = 8;
			int metricY = 8 + heroH + 10;
			int metricH = 44;
			int metricW = Math.max(1, (w - pad * 2 - gap) / 2);
			drawHealthMetric(g, pad, metricY, metricW, metricH,
					"mapped nodes", formatCount(analysis.mappedNodeCount), "all known graph labels", BLUE);
			drawHealthMetric(g, pad + metricW + gap, metricY, metricW, metricH,
					"active nodes", formatCount(analysis.activeNodeCount), "nodes with edges", PURPLE);
			drawHealthMetric(g, pad, metricY + metricH + gap, metricW, metricH,
					"valid edges", formatCount(analysis.validEdgeCount), "usable graph links", GREEN);
			drawHealthMetric(g, pad + metricW + gap, metricY + metricH + gap, metricW, metricH,
					"isolates", formatCount(analysis.isolatedMappedNodeCount), "mapped but unused", AMBER);

			int checksY = metricY + metricH * 2 + gap + 12;
			// Cap the checks well so viewport-stretched cards never leave a
			// huge empty glass box under the three quality rows.
			int checksH = Math.min(h - checksY - 8, 132);
			if (checksH >= 74)
			{
				drawCheckPanel(g, pad, checksY, w - pad * 2, checksH, color);
			}
			g.dispose();
		}

		private void drawHealthHero(Graphics2D g, int x, int y, int w, int h, Color color)
		{
			g.setPaint(new GradientPaint(
					x,
					y,
					themed(NebulaDesign.DAY_SURFACE, NebulaDesign.NIGHT_SURFACE),
					x + w,
					y + h,
					tint(color, 0.86f)));
			g.fillRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setColor(BORDER);
			g.drawRoundRect(x, y, w, h, RADIUS, RADIUS);

			int ring = Math.max(68, Math.min(92, h - 28));
			int ringX = x + 18;
			int ringY = y + (h - ring) / 2;
			g.setStroke(new BasicStroke(9f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			g.setColor(track());
			g.draw(new Arc2D.Double(ringX, ringY, ring, ring, 90, -360, Arc2D.OPEN));
			double sweep = -360.0 * (analysis.healthScore / 100.0);
			if (analysis.healthScore > 0)
			{
				g.setPaint(new GradientPaint(
						ringX, ringY + ring, color,
						ringX + ring, ringY, tint(color, 0.30f)));
				g.draw(new Arc2D.Double(ringX, ringY, ring, ring, 90, sweep, Arc2D.OPEN));
			}
			drawGaugeTick(g, ringX, ringY, ring, 70);
			drawGaugeTick(g, ringX, ringY, ring, 86);
			g.setStroke(new BasicStroke(1f));
			g.setFont(NebulaDesign.fontSemiBold(26f));
			g.setColor(TEXT);
			String score = Integer.toString(analysis.healthScore);
			FontMetrics metrics = g.getFontMetrics();
			g.drawString(score,
					ringX + ring / 2 - metrics.stringWidth(score) / 2,
					ringY + ring / 2 + 9);

			int copyX = ringX + ring + 20;
			int copyW = w - (copyX - x) - 16;
			g.setFont(font(Font.BOLD, 10));
			g.setColor(deep(color));
			drawClippedString(g, "GRAPH HEALTH SCORE", copyX, y + 27, copyW);
			g.setFont(font(Font.BOLD, 18));
			g.setColor(TEXT);
			drawClippedString(g, healthHeadline(), copyX, y + 53, copyW);
			g.setFont(font(Font.PLAIN, 11));
			g.setColor(MUTED);
			drawWrapped(g, healthSentence(), copyX, y + 74, copyW, 2, 14);
		}

		/** Threshold tick on the gauge ring so status hues explain themselves. */
		private void drawGaugeTick(Graphics2D g, int ringX, int ringY, int ring, int score)
		{
			double angle = Math.toRadians(90 - 360.0 * (score / 100.0));
			double cx = ringX + ring / 2.0;
			double cy = ringY + ring / 2.0;
			double inner = ring / 2.0 - 6.5;
			double outer = ring / 2.0 + 6.5;
			g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			g.setColor(NebulaDesign.BORDER_STRONG);
			g.draw(new java.awt.geom.Line2D.Double(
					cx + Math.cos(angle) * inner,
					cy - Math.sin(angle) * inner,
					cx + Math.cos(angle) * outer,
					cy - Math.sin(angle) * outer));
		}

		private void drawHealthMetric(
				Graphics2D g,
				int x,
				int y,
				int w,
				int h,
				String label,
				String value,
				String detail,
				Color color)
		{
			g.setColor(SURFACE_ALT);
			g.fillRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setColor(BORDER);
			g.drawRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setColor(color);
			g.fillOval(x + 10, y + 7, 6, 6);
			g.setFont(font(Font.BOLD, 9));
			g.setColor(NebulaDesign.TEXT_TERTIARY);
			drawClippedString(g, label.toUpperCase(Locale.US), x + 21, y + 14, w - 30);
			g.setFont(font(Font.BOLD, 16));
			g.setColor(TEXT);
			drawClippedString(g, value, x + 11, y + 31, w - 20);
			g.setFont(font(Font.PLAIN, 9));
			g.setColor(NebulaDesign.TEXT_TERTIARY);
			drawClippedString(g, detail, x + 11, y + h - 5, w - 20);
		}

		private void drawCheckPanel(Graphics2D g, int x, int y, int w, int h, Color color)
		{
			g.setColor(glass(210));
			g.fillRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setColor(BORDER);
			g.drawRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setFont(font(Font.BOLD, 10));
			g.setColor(MUTED);
			drawClippedString(g, "QUALITY CHECKS", x + 12, y + 17, w - 24);
			int rowY = y + 30;
			int rowH = Math.max(16, Math.min(22, (h - 38) / 3));
			drawCheckRow(g, x + 12, rowY, w - 24, rowH,
					"edge integrity",
					analysis.malformedEdgeLines == 0L && analysis.unknownNodeEdges == 0L
							? "clean"
							: formatCount(analysis.malformedEdgeLines + analysis.unknownNodeEdges) + " issues",
					analysis.malformedEdgeLines == 0L && analysis.unknownNodeEdges == 0L ? GREEN : RED);
			drawCheckRow(g, x + 12, rowY + rowH + 6, w - 24, rowH,
					"duplicate pressure",
					formatCount(analysis.duplicateEdgeCount) + " repeats",
					analysis.duplicateEdgeCount == 0L ? GREEN : AMBER);
			drawCheckRow(g, x + 12, rowY + (rowH + 6) * 2, w - 24, rowH,
					"graph coverage",
					percent(coverage()) + " active",
					coverage() > 0.35 ? color : AMBER);
		}

		private void drawCheckRow(
				Graphics2D g,
				int x,
				int y,
				int w,
				int h,
				String label,
				String value,
				Color color)
		{
			g.setColor(tint(color, 0.86f));
			g.fillRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setColor(alpha(color, 62));
			g.drawRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setColor(color);
			g.fillOval(x + 9, y + h / 2 - 3, 7, 7);
			g.setFont(NebulaDesign.fontMedium(10.5f));
			g.setColor(TEXT);
			drawClippedString(g, label, x + 24, y + h / 2 + 4, w / 2);
			g.setFont(font(Font.BOLD, 10));
			g.setColor(deep(color));
			drawClippedString(g, value, x + w / 2 + 18, y + h / 2 + 4, w / 2 - 24);
		}

		private String healthHeadline()
		{
			if (!analysis.available)
			{
				return "Waiting for graph artifacts";
			}
			if (analysis.healthScore >= 90)
			{
				return "Graph files look healthy";
			}
			if (analysis.healthScore >= 70)
			{
				return "Usable with warnings";
			}
			return "Needs attention";
		}

		private String healthSentence()
		{
			if (!analysis.available)
			{
				return "Load or generate graph output so this tab can inspect saved node and edge files.";
			}
			if (analysis.validEdgeCount == 0L)
			{
				return "No active graph edges have been written yet, so the graph cannot be interpreted.";
			}
			return formatCount(analysis.validEdgeCount) + " valid edges, "
					+ formatCount(analysis.activeNodeCount) + " active nodes, "
					+ formatCount(analysis.unknownNodeEdges) + " unknown-node edges.";
		}

		private double coverage()
		{
			return analysis.mappedNodeCount <= 0
					? 0.0 : analysis.activeNodeCount / (double)analysis.mappedNodeCount;
		}
	}

	private static final class NetworkShapePanel extends VisualPanel
	{
		protected void paintComponent(Graphics graphics)
		{
			super.paintComponent(graphics);
			Graphics2D g = (Graphics2D)graphics.create();
			prepare(g);
			int w = getWidth();
			int h = getHeight();
			int pad = 14;
			// Full opaque base: matches the host card and keeps the panel
			// self-sufficient when rendered standalone (self-test contract).
			g.setColor(SURFACE);
			g.fillRect(0, 0, w, h);
			if (w < 320 || h < 220)
			{
				g.dispose();
				return;
			}

			int gap = 8;
			int metricW = Math.max(1, (w - pad * 2 - gap * 3) / 4);
			drawMetricTile(g, pad, 8, metricW, 48,
					"components",
					formatCount(analysis.activeComponentCount),
					"separate groups",
					BLUE);
			drawMetricTile(g, pad + (metricW + gap), 8, metricW, 48,
					"communities",
					formatCount(analysis.estimatedCommunityCount),
					communityMetricDetail(analysis),
					GREEN);
			drawMetricTile(g, pad + (metricW + gap) * 2, 8, metricW, 48,
					"density",
					percent(analysis.activeDensity),
					"how connected it is",
					PURPLE);
			drawMetricTile(g, pad + (metricW + gap) * 3, 8, metricW, 48,
					"largest group",
					formatCount(analysis.largestComponentSize),
					"nodes together",
					AMBER);

			int stripH = 58;
			int canvasY = 70;
			int canvasH = h - canvasY - stripH - 18;
			if (canvasH < 130)
			{
				stripH = 48;
				canvasH = h - canvasY - stripH - 14;
			}
			if (canvasH < 100)
			{
				g.dispose();
				return;
			}
			drawTopologyObservatory(g, pad, canvasY, w - pad * 2, canvasH);

			int infoY = canvasY + canvasH + 10;
			int infoW = Math.max(1, (w - pad * 2 - gap * 2) / 3);
			drawInsightCard(g, pad, infoY, infoW, stripH,
					"coverage",
					formatCount(analysis.activeNodeCount) + " active of " + formatCount(analysis.mappedNodeCount),
					BLUE,
					coverage());
			drawInsightCard(g, pad + infoW + gap, infoY, infoW, stripH,
					"concentration",
					percent(analysis.hubConcentration) + " into top hubs",
					analysis.hubConcentration > 0.5 ? AMBER : GREEN,
					analysis.hubConcentration);
			drawInsightCard(g, pad + (infoW + gap) * 2, infoY, infoW, stripH,
					"read",
					structureRead(),
					statusColor(analysis),
					analysis.healthScore / 100.0);
			g.dispose();
		}

		private void drawMetricTile(
				Graphics2D g,
				int x,
				int y,
				int w,
				int h,
				String label,
				String value,
				String detail,
				Color color)
		{
			g.setColor(SURFACE_ALT);
			g.fillRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setColor(BORDER);
			g.drawRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setColor(color);
			g.fillOval(x + 11, y + 7, 6, 6);
			g.setFont(font(Font.BOLD, 9));
			g.setColor(NebulaDesign.TEXT_TERTIARY);
			drawClippedString(g, label.toUpperCase(Locale.US), x + 22, y + 14, w - 31);
			g.setFont(font(Font.BOLD, 18));
			g.setColor(TEXT);
			drawClippedString(g, value, x + 12, y + 33, w - 20);
			g.setFont(font(Font.PLAIN, 9));
			g.setColor(NebulaDesign.TEXT_TERTIARY);
			drawClippedString(g, detail, x + 12, y + h - 6, w - 20);
		}

		private void drawNetworkCanvas(Graphics2D g, int x, int y, int w, int h)
		{
			g.setPaint(new GradientPaint(
					x,
					y,
					themed(NebulaDesign.DAY_SURFACE_ALT, NebulaDesign.NIGHT_INSET),
					x + w,
					y + h,
					themed(NebulaDesign.DAY_INSET, NebulaDesign.NIGHT_CANVAS_BOTTOM)));
			g.fillRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setColor(BORDER);
			g.drawRoundRect(x, y, w, h, RADIUS, RADIUS);

			g.setColor(alpha(BORDER, 110));
			for (int gx = x + 26; gx < x + w - 20; gx += 34)
			{
				g.drawLine(gx, y + 52, gx, y + h - 36);
			}
			for (int gy = y + 66; gy < y + h - 34; gy += 28)
			{
				g.drawLine(x + 20, gy, x + w - 20, gy);
			}

			g.setColor(glass(190));
			g.fillRoundRect(x + 10, y + 10, w - 20, 36, RADIUS, RADIUS);
			g.setColor(BORDER);
			g.drawRoundRect(x + 10, y + 10, w - 20, 36, RADIUS, RADIUS);
			g.setFont(font(Font.BOLD, 10));
			g.setColor(NebulaDesign.TEXT_TERTIARY);
			drawClippedString(g, "ACTIVE GRAPH TOPOLOGY", x + 22, y + 32, w / 2);
			g.setFont(font(Font.PLAIN, 10));
			g.setColor(MUTED);
			drawClippedString(g,
					topologySubtitle(),
					x + w / 2,
					y + 32,
					w / 2 - 24);
		}

		private void drawTopologyObservatory(Graphics2D g, int x, int y, int w, int h)
		{
			drawNetworkCanvas(g, x, y, w, h);

			int footerH = 28;
			boolean cramped = h < 240;
			boolean showBoxes = !cramped && w >= 470;
			boolean roomy = h >= 268 && w >= 690;
			// Clamp the constellation to a centered band so very tall
			// viewport-fitted canvases never leave a void between the
			// source box, the core, and the output box.
			int bandH = Math.min(h - 46 - footerH, 470);
			int bandTop = y + 46 + (h - 46 - footerH - bandH) / 2;
			int centerX = x + w / 2;
			int centerY = bandTop + bandH / 2;
			int sourceW = Math.max(152, Math.min(172, w / 4));
			int outputW = Math.max(156, Math.min(192, w / 4));
			int leftX = x + 20;
			int rightX = x + w - outputW - 20;
			int sourceY = Math.max(y + 56, bandTop + 8);
			int outputY = Math.min(y + h - footerH - 86, bandTop + bandH - 84);
			int clusterRadiusX = Math.max(70, Math.min(142, w / 6));
			int clusterRadiusY = Math.max(34, Math.min(94, bandH / 4));

			if (showBoxes)
			{
				drawSignalNode(g, leftX, sourceY, sourceW, 72, BLUE,
						"SOURCE SET",
						compact(sourceShort(snapshot), 26),
						formatCount(Math.max(analysis.recommendationEdgeCount, analysis.activeNodeCount)));
				drawSignalNode(g, rightX, outputY, outputW, 76, GREEN,
						"TOP OUTPUT",
						compact(outputMeaning(snapshot), 28),
						topName(analysis.topRecommendedTargets, "waiting"));

				drawBackbone(g, leftX + sourceW, sourceY + 36, centerX, centerY, BLUE, 0);
				drawBackbone(g, centerX, centerY, rightX, outputY + 38, GREEN, 3);
			}

			int communityCount = Math.max(3, Math.min(6,
					analysis.topCommunities.isEmpty()
							? Math.max(3, analysis.estimatedCommunityCount)
							: analysis.topCommunities.size()));
			int coreSize = cramped ? 62 : 78;
			for (int i = 0; i < communityCount; i++)
			{
				Color color = communityColor(i);
				GraphAnalysisEngine.NodeScore score = i < analysis.topCommunities.size()
						? analysis.topCommunities.get(i) : null;
				int value = score == null
						? Math.max(1, analysis.largestComponentSize / Math.max(1, communityCount))
						: score.value;
				int nodeSize = Math.max(34, Math.min(62, 30 + (int)Math.sqrt(Math.max(1, value)) * 3));
				int cx;
				int cy;
				if (cramped)
				{
					// Low canvases: a calm horizontal constellation flanking
					// the core instead of a collapsing ellipse.
					nodeSize = Math.max(28, Math.min(38, nodeSize));
					int step = i / 2;
					int side = i % 2 == 0 ? 1 : -1;
					cx = centerX + side * (coreSize / 2 + 38 + step * 56);
					cy = centerY + (step % 2 == 0 ? -8 : 10);
				}
				else
				{
					// Half-step rotation keeps orbits off the vertical axis so
					// their labels never collide with the core.
					double angle = -Math.PI / 2
							+ (Math.PI * 2 * (i + 0.5) / communityCount);
					cx = centerX + (int)(Math.cos(angle) * clusterRadiusX);
					cy = centerY + (int)(Math.sin(angle) * clusterRadiusY);
				}
				drawBackbone(g, centerX, centerY, cx, cy, color, i + 6);
				drawCommunityOrbit(g, cx, cy, nodeSize, color,
						score == null ? "C" + (i + 1) : formatCount(score.value),
						cramped
								? null
								: score == null ? "community" : compact(communityShort(score.name), 20),
						i);
			}

			drawCore(g, centerX, centerY, coreSize);
			if (roomy)
			{
				drawHubStack(g, x + 20, y + h - footerH - 68, Math.min(270, w / 2 - 22), 58);
				drawLegend(g, x + w - Math.min(300, w / 3) - 20, y + 54,
						Math.min(300, w / 3), 66);
			}
			drawCanvasFooter(g, x + 18, y + h - 28, w - 36);
		}

		private void drawSignalNode(
				Graphics2D g,
				int x,
				int y,
				int w,
				int h,
				Color color,
				String title,
				String subtitle,
				String value)
		{
			g.setColor(glass(232));
			g.fillRoundRect(x, y, w, h, RADIUS + 4, RADIUS + 4);
			g.setColor(alpha(color, 86));
			g.drawRoundRect(x, y, w, h, RADIUS + 4, RADIUS + 4);
			g.setColor(alpha(color, 22));
			g.fillRoundRect(x + 8, y + 8, 38, h - 16, RADIUS, RADIUS);
			g.setColor(tint(color, 0.80f));
			g.fillOval(x + 15, y + h / 2 - 14, 28, 28);
			g.setColor(color);
			g.setStroke(new BasicStroke(2f));
			g.drawOval(x + 15, y + h / 2 - 14, 28, 28);
			g.setFont(font(Font.BOLD, 10));
			g.setColor(deep(color));
			drawClippedString(g, title.toUpperCase(Locale.US), x + 54, y + 23, w - 64);
			g.setFont(font(Font.BOLD, 12));
			g.setColor(TEXT);
			drawClippedString(g, subtitle, x + 54, y + 43, w - 64);
			g.setFont(font(Font.PLAIN, 10));
			g.setColor(MUTED);
			drawClippedString(g, value, x + 54, y + 60, w - 64);
		}

		private void drawBackbone(
				Graphics2D g,
				int x1,
				int y1,
				int x2,
				int y2,
				Color color,
				int index)
		{
			int controlX = (x1 + x2) / 2;
			int controlY = (y1 + y2) / 2 + (index % 2 == 0 ? -22 : 22);
			g.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			g.setColor(alpha(color, 76));
			g.draw(new QuadCurve2D.Double(x1, y1, controlX, controlY, x2, y2));
			double t = (phase + index * 0.17) % 1.0;
			double px = (1 - t) * (1 - t) * x1 + 2 * (1 - t) * t * controlX + t * t * x2;
			double py = (1 - t) * (1 - t) * y1 + 2 * (1 - t) * t * controlY + t * t * y2;
			g.setColor(color);
			g.fill(new Ellipse2D.Double(px - 3.5, py - 3.5, 7, 7));
		}

		private void drawCommunityOrbit(
				Graphics2D g,
				int cx,
				int cy,
				int size,
				Color color,
				String value,
				String label,
				int index)
		{
			double breathe = 1.0 + Math.sin(phase * 6.0 + index) * 0.045;
			int outer = (int)(size * breathe) + 14;
			int x = cx - size / 2;
			int y = cy - size / 2;
			g.setStroke(new BasicStroke(1.3f));
			g.setColor(alpha(color, 34));
			g.drawOval(cx - outer / 2, cy - outer / 2, outer, outer);
			g.setColor(glass(232));
			g.fillOval(x - 4, y - 4, size + 8, size + 8);
			g.setColor(tint(color, 0.78f));
			g.fillOval(x, y, size, size);
			g.setColor(color);
			g.setStroke(new BasicStroke(2f));
			g.drawOval(x, y, size, size);
			g.setFont(font(Font.BOLD, Math.max(10, Math.min(13, size / 4))));
			g.setColor(deep(color));
			FontMetrics orbitMetrics = g.getFontMetrics();
			int valueW = orbitMetrics.stringWidth(value);
			drawClippedString(g,
					value,
					valueW <= size - 8 ? x + (size - valueW) / 2 : x + 4,
					y + size / 2 + 4,
					size - 8);
			if (label != null)
			{
				g.setFont(font(Font.PLAIN, 9));
				g.setColor(MUTED);
				drawClippedString(g, label, x - 18, y + size + 14, size + 36);
			}
		}

		private void drawCore(Graphics2D g, int x, int y, int size)
		{
			Color color = statusColor(analysis);
			g.setColor(glass(238));
			g.fillOval(x - size / 2, y - size / 2, size, size);
			g.setColor(alpha(color, 88));
			g.setStroke(new BasicStroke(2.4f));
			g.drawOval(x - size / 2, y - size / 2, size, size);
			g.setFont(font(Font.BOLD, size >= 70 ? 20 : 17));
			g.setColor(TEXT);
			String value = formatCount(analysis.estimatedCommunityCount);
			FontMetrics metrics = g.getFontMetrics();
			g.drawString(value, x - metrics.stringWidth(value) / 2, y - 2);
			g.setFont(font(Font.BOLD, 9));
			g.setColor(deep(color));
			String label = analysis.gephiCommunityCount ? "GEPHI Q" : "ESTIMATE";
			metrics = g.getFontMetrics();
			g.drawString(label, x - metrics.stringWidth(label) / 2, y + (size >= 70 ? 17 : 14));
		}

		private void drawHubStack(Graphics2D g, int x, int y, int w, int h)
		{
			g.setColor(glass(186));
			g.fillRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setColor(BORDER);
			g.drawRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setFont(font(Font.BOLD, 10));
			g.setColor(deep(BLUE));
			drawClippedString(g, "HUB SPOTLIGHT", x + 12, y + 17, w - 24);
			int chipX = x + 12;
			int chipY = y + 28;
			int max = 1;
			for (GraphAnalysisEngine.NodeScore score : analysis.topIncomingNodes)
			{
				max = Math.max(max, score.value);
			}
			for (int i = 0; i < Math.min(3, analysis.topIncomingNodes.size()); i++)
			{
				GraphAnalysisEngine.NodeScore score = analysis.topIncomingNodes.get(i);
				int chipW = Math.max(64, Math.min(88, (int)(70 + 24 * (score.value / (double)max))));
				if (chipX + chipW > x + w - 8)
				{
					break;
				}
				drawHubChip(g, chipX, chipY, chipW, 20, score, i);
				chipX += chipW + 7;
			}
			if (analysis.topIncomingNodes.isEmpty())
			{
				g.setFont(font(Font.PLAIN, 10));
				g.setColor(MUTED);
				drawClippedString(g, "No incoming hubs yet.", x + 12, y + 44, w - 24);
			}
		}

		private void drawHubChip(
				Graphics2D g,
				int x,
				int y,
				int w,
				int h,
				GraphAnalysisEngine.NodeScore score,
				int rank)
		{
			Color color = rank == 0 ? BLUE : rank == 1 ? PURPLE : AMBER;
			g.setColor(tint(color, 0.84f));
			g.fillRoundRect(x, y, w, h, h, h);
			g.setColor(alpha(color, 82));
			g.drawRoundRect(x, y, w, h, h, h);
			g.setFont(font(Font.BOLD, 9));
			g.setColor(deep(color));
			drawClippedString(g, compact(score.name, 11), x + 8, y + 14, w - 16);
		}

		private void drawLegend(Graphics2D g, int x, int y, int w, int h)
		{
			g.setColor(glass(180));
			g.fillRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setColor(BORDER);
			g.drawRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setFont(font(Font.BOLD, 10));
			g.setColor(MUTED);
			drawClippedString(g, "VISUAL READ", x + 12, y + 17, w - 24);
			drawLegendItem(g, x + 12, y + 36, "size = community weight", PURPLE);
			drawLegendItem(g, x + w / 2, y + 36, "motion = edge flow", GREEN);
			drawLegendItem(g, x + 12, y + 54, "center = " + communityLayerLabel(analysis).toLowerCase(Locale.US), BLUE);
		}

		private void drawLegendItem(Graphics2D g, int x, int y, String label, Color color)
		{
			g.setColor(color);
			g.fillOval(x, y - 8, 8, 8);
			g.setFont(font(Font.PLAIN, 9));
			g.setColor(TEXT);
			drawClippedString(g, label, x + 13, y, 130);
		}

		private void drawCanvasFooter(Graphics2D g, int x, int y, int w)
		{
			g.setColor(glass(160));
			g.fillRoundRect(x, y, w, 22, RADIUS, RADIUS);
			g.setFont(font(Font.BOLD, 10));
			g.setColor(TEXT);
			String summary = formatCount(analysis.validEdgeCount) + " valid edges  |  "
					+ formatCount(analysis.duplicateEdgeCount) + " duplicate edges  |  "
					+ formatCount(analysis.unknownNodeEdges) + " unknown-node edges";
			drawClippedString(g, summary, x + 10, y + 15, w - 20);
		}

		private void drawInsightCard(
				Graphics2D g,
				int x,
				int y,
				int w,
				int h,
				String label,
				String value,
				Color color,
				double progress)
		{
			g.setColor(SURFACE_ALT);
			g.fillRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setColor(BORDER);
			g.drawRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setFont(font(Font.BOLD, 9));
			g.setColor(deep(color));
			drawClippedString(g, label.toUpperCase(Locale.US), x + 10, y + 15, w - 20);
			g.setFont(font(Font.BOLD, 11));
			g.setColor(TEXT);
			drawClippedString(g, value, x + 10, y + 33, w - 20);
			int barY = y + h - 13;
			int barW = w - 20;
			g.setColor(track());
			g.fillRoundRect(x + 10, barY, barW, 5, 5, 5);
			g.setColor(color);
			g.fillRoundRect(x + 10, barY, (int)(barW * Math.max(0.0, Math.min(1.0, progress))), 5, 5, 5);
		}

		private double coverage()
		{
			return analysis.mappedNodeCount <= 0
					? 0.0 : analysis.activeNodeCount / (double)analysis.mappedNodeCount;
		}

		private String structureRead()
		{
			if (!analysis.available)
			{
				return "waiting for graph files";
			}
			if (analysis.validEdgeCount == 0L)
			{
				return "no active edges yet";
			}
			if (analysis.activeComponentCount <= 1)
			{
				return "mostly one connected graph";
			}
			if (analysis.hubConcentration > 0.5)
			{
				return "hub-heavy graph";
			}
			return "distributed network";
		}

		private String topName(List<GraphAnalysisEngine.NodeScore> nodes, String fallback)
		{
			return nodes == null || nodes.isEmpty()
					? fallback : compact(nodes.get(0).name, 18);
		}

		private String topologySubtitle()
		{
			if (!analysis.available)
			{
				return "waiting for graph files";
			}
			if (analysis.validEdgeCount == 0L)
			{
				return "no saved recommendation edges yet";
			}
			return formatCount(analysis.validEdgeCount) + " edges, "
					+ formatCount(analysis.activeNodeCount) + " active nodes";
		}

		private Color communityColor(int index)
		{
			return NebulaDesign.chartColor(index);
		}

		private Color alpha(Color color, int amount)
		{
			return new Color(color.getRed(), color.getGreen(), color.getBlue(),
					Math.max(0, Math.min(255, amount)));
		}
	}

	private static final class RecommendationImpactStoryPanel extends VisualPanel
	{
		private final Rectangle keptLaneBounds = new Rectangle();
		private final Rectangle changedLaneBounds = new Rectangle();
		private final Rectangle outputBounds = new Rectangle();
		private final Rectangle inspectorBounds = new Rectangle();

		RecommendationImpactStoryPanel()
		{
			setToolTipText("");
		}

		public String getToolTipText(MouseEvent event)
		{
			if (!hasRecommendationRun())
			{
				return "Run a recommendation query to compare original edges, recommended targets, output spread, and stability.";
			}
			long total = totalRecommendations();
			if (keptLaneBounds.contains(event.getPoint()))
			{
				return "Kept edges: " + formatCount(analysis.recommendationSameAsOriginalCount)
						+ " of " + formatCount(total) + " matched the original target.";
			}
			if (changedLaneBounds.contains(event.getPoint()))
			{
				return "Changed edges: " + formatCount(analysis.recommendationChangedTargetCount)
						+ " were rerouted to a different recommended target.";
			}
			if (outputBounds.contains(event.getPoint()))
			{
				return "Output spread: " + formatCount(analysis.uniqueRecommendedTargets)
						+ " unique targets across " + formatCount(total) + " recommendation edges.";
			}
			if (inspectorBounds.contains(event.getPoint()))
			{
				return "Explanation inspector: artifact confidence, concentration risk, and the next review action.";
			}
			return super.getToolTipText(event);
		}

		protected void paintComponent(Graphics graphics)
		{
			super.paintComponent(graphics);
			Graphics2D g = (Graphics2D)graphics.create();
			prepare(g);
			int w = getWidth();
			int h = getHeight();
			int pad = 14;
			// Full opaque base: matches the host card and keeps the panel
			// self-sufficient when rendered standalone (self-test contract).
			g.setColor(SURFACE);
			g.fillRect(0, 0, w, h);
			if (w < 340 || h < 220)
			{
				g.dispose();
				return;
			}

			clearHoverBounds();
			if (!hasRecommendationRun())
			{
				int heroH = Math.min(132, Math.max(104, h / 3));
				drawNarrativeHero(g, pad, 8, w - pad * 2, heroH, false);
				drawEmptyImpactState(g, pad, heroH + 20, w - pad * 2, h - heroH - 30);
				g.dispose();
				return;
			}

			boolean showcase = h >= 520 && w >= 460;
			boolean compact = h < 410;
			int heroH = showcase ? Math.min(138, Math.max(118, h / 5)) : compact ? 64 : 82;
			drawNarrativeHero(g, pad, 8, w - pad * 2, heroH, true);

			int cursorY = 8 + heroH + 10;

			int metricH = w < 500 ? 104 : showcase ? 68 : 56;
			if (compact)
			{
				metricH = 48;
			}
			drawImpactMetricStrip(g, pad, cursorY, w - pad * 2, metricH);
			cursorY += metricH + 10;

			int judgmentH = compact ? 0 : Math.min(showcase ? 86 : 66,
					Math.max(showcase ? 72 : 56, h / 8));
			int canvasH = h - cursorY - judgmentH - (judgmentH > 0 ? 18 : 10);
			if (canvasH < 134 && judgmentH > 0)
			{
				judgmentH = 0;
				canvasH = h - cursorY - 10;
			}
			if (canvasH >= 118)
			{
				drawDiffStoryCanvas(g, pad, cursorY, w - pad * 2, canvasH);
			}
			if (judgmentH > 0)
			{
				drawJudgmentStrip(g, pad, cursorY + canvasH + 10, w - pad * 2, judgmentH);
			}
			g.dispose();
		}

		private void clearHoverBounds()
		{
			keptLaneBounds.setBounds(0, 0, 0, 0);
			changedLaneBounds.setBounds(0, 0, 0, 0);
			outputBounds.setBounds(0, 0, 0, 0);
			inspectorBounds.setBounds(0, 0, 0, 0);
		}

		private boolean hasRecommendationRun()
		{
			return analysis.available && totalRecommendations() > 0L;
		}

		private long totalRecommendations()
		{
			return Math.max(analysis.recommendationEdgeCount,
					analysis.recommendationChangedTargetCount
							+ analysis.recommendationSameAsOriginalCount);
		}

		private double changedRatio()
		{
			return ratio(analysis.recommendationChangedTargetCount, totalRecommendations());
		}

		private double sameRatio()
		{
			return ratio(analysis.recommendationSameAsOriginalCount, totalRecommendations());
		}

		private double topTargetShare()
		{
			if (analysis.topRecommendedTargets.isEmpty() || totalRecommendations() <= 0L)
			{
				return 0.0;
			}
			return ratio(analysis.topRecommendedTargets.get(0).value, totalRecommendations());
		}

		private void drawNarrativeHero(
				Graphics2D g,
				int x,
				int y,
				int w,
				int h,
				boolean hasRun)
		{
			Color accent = hasRun ? statusColor(analysis) : BLUE;
			g.setPaint(new GradientPaint(
					x,
					y,
					themed(NebulaDesign.DAY_SURFACE, NebulaDesign.NIGHT_SURFACE),
					x + w,
					y + h,
					tint(accent, hasRun ? 0.86f : 0.90f)));
			g.fillRoundRect(x, y, w, h, RADIUS, RADIUS);
			if (hasRun)
			{
				drawHeroEnergy(g, x, y, w, h, accent);
			}
			g.setColor(BORDER);
			g.drawRoundRect(x, y, w, h, RADIUS, RADIUS);

			boolean showcase = hasRun && h >= 108 && w >= 520;
			int badgeW = Math.max(98, Math.min(122, w / 5));
			g.setColor(tint(accent, 0.84f));
			g.fillRoundRect(x + 12, y + 12, badgeW, 24, 12, 12);
			g.setColor(alpha(accent, 84));
			g.drawRoundRect(x + 12, y + 12, badgeW, 24, 12, 12);
			g.setFont(font(Font.BOLD, 9));
			g.setColor(deep(accent));
			drawClippedString(g, hasRun ? "IMPACT STORY" : "READY STATE",
					x + 24, y + 28, badgeW - 24);

			int spotlightW = showcase ? Math.min(270, Math.max(228, w / 3)) : 0;
			int copyX = x + 16;
			int copyY = y + (showcase ? 59 : hasRun ? 50 : 56);
			int copyW = hasRun ? w - 104 : w - 30;
			if (showcase)
			{
				copyW = w - spotlightW - 42;
			}
			g.setFont(font(Font.BOLD, showcase ? 22 : hasRun ? 17 : 18));
			g.setColor(TEXT);
			drawClippedString(g,
					hasRun
							? impactHeadline()
							: "No recommendation run yet",
					copyX,
					copyY,
					copyW);
			g.setFont(font(Font.PLAIN, showcase ? 12 : 11));
			g.setColor(MUTED);
			drawWrapped(g,
					hasRun ? impactNarrative()
							: "Run a query to compare original graph edges, recommended targets, output spread, and stability.",
					copyX,
					copyY + (showcase ? 24 : 20),
					copyW,
					hasRun ? 2 : 3,
					showcase ? 16 : 14);

			if (showcase)
			{
				drawHeroStatStack(g, x + w - spotlightW - 16, y + 16,
						spotlightW, h - 30, accent);
			}
			else
			{
				drawPulseRing(g, x + w - 58, y + 34, 16, accent, hasRun);
			}
		}

		private void drawHeroEnergy(Graphics2D g, int x, int y, int w, int h, Color accent)
		{
			g.setColor(translucent(PURPLE, 14));
			g.fillRoundRect(x + 18, y + h - 30, Math.max(120, w / 3), 18, RADIUS, RADIUS);
			g.setColor(translucent(BLUE, 14));
			g.fillRoundRect(x + w / 3, y + 16, Math.max(160, w / 2), 24, RADIUS, RADIUS);
			g.setColor(translucent(accent, 16));
			g.fillRoundRect(x + w / 5, y + h / 2, Math.max(180, w / 2), 16, RADIUS, RADIUS);
		}

		private void drawHeroStatStack(
				Graphics2D g,
				int x,
				int y,
				int w,
				int h,
				Color accent)
		{
			g.setColor(glass(202));
			g.fillRoundRect(x, y, w, h, RADIUS + 4, RADIUS + 4);
			g.setColor(translucent(accent, 94));
			g.drawRoundRect(x, y, w, h, RADIUS + 4, RADIUS + 4);

			int ringSize = Math.max(52, Math.min(66, h - 28));
			int ringX = x + 14;
			int ringY = y + (h - ringSize) / 2;
			g.setStroke(new BasicStroke(7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			g.setColor(track());
			g.draw(new Arc2D.Double(ringX, ringY, ringSize, ringSize, 90, -360, Arc2D.OPEN));
			g.setPaint(new GradientPaint(
					ringX, ringY + ringSize, BLUE,
					ringX + ringSize, ringY, PURPLE));
			g.draw(new Arc2D.Double(ringX, ringY, ringSize, ringSize, 90,
					-360 * clamp01(changedRatio()), Arc2D.OPEN));
			g.setStroke(new BasicStroke(1f));

			int copyX = ringX + ringSize + 14;
			int copyW = w - (copyX - x) - 12;
			g.setFont(font(Font.BOLD, 9));
			g.setColor(deep(BLUE));
			drawClippedString(g, "CHANGED TARGETS", copyX, y + 23, copyW);
			g.setFont(font(Font.BOLD, 22));
			g.setColor(TEXT);
			drawClippedString(g, percent(changedRatio()), copyX, y + 50, copyW);
			g.setFont(font(Font.PLAIN, 10));
			g.setColor(MUTED);
			drawClippedString(g,
					formatCount(analysis.recommendationChangedTargetCount)
							+ " of " + formatCount(totalRecommendations()) + " decisions",
					copyX,
					y + 67,
					copyW);

			if (h >= 104)
			{
				int chipY = y + h - 30;
				int chipX = copyX;
				int chipW = Math.max(54, (copyW - 8) / 2);
				drawHeroChip(g, chipX, chipY, chipW, 20,
						"kept " + percentWhole(sameRatio()), TEAL);
				drawHeroChip(g, chipX + chipW + 8, chipY, chipW, 20,
						riskChipLabel(), concentrationColor());
			}
		}

		private void drawHeroChip(Graphics2D g, int x, int y, int w, int h, String label, Color color)
		{
			g.setColor(tint(color, 0.85f));
			g.fillRoundRect(x, y, w, h, h, h);
			g.setColor(translucent(color, 92));
			g.drawRoundRect(x, y, w, h, h, h);
			g.setFont(font(Font.BOLD, 9));
			g.setColor(deep(color));
			drawClippedString(g, label, x + 8, y + 14, w - 16);
		}

		private void drawPulseRing(
				Graphics2D g,
				int cx,
				int cy,
				int radius,
				Color color,
				boolean active)
		{
			float sweep = active ? (phase % 1.0f) : 0.12f;
			g.setStroke(new BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			g.setColor(track());
			g.draw(new Arc2D.Double(cx - radius, cy - radius, radius * 2, radius * 2,
					90, -360, Arc2D.OPEN));
			g.setColor(color);
			g.draw(new Arc2D.Double(cx - radius, cy - radius, radius * 2, radius * 2,
					90, -360 * Math.max(0.12, sweep), Arc2D.OPEN));
			g.setStroke(new BasicStroke(1f));
			g.setColor(tint(color, 0.50f));
			g.fillOval(cx - 5, cy - 5, 10, 10);
		}

		private String impactNarrative()
		{
			return "Changed " + percent(changedRatio())
					+ ", preserved " + percent(sameRatio())
					+ ". Diversity " + percent(analysis.recommendationDiversity)
					+ "; concentration risk "
					+ concentrationRisk().toLowerCase(Locale.US) + ".";
		}

		private String impactHeadline()
		{
			if (changedRatio() >= 0.55)
			{
				return "Impact verdict: major reroute";
			}
			if (sameRatio() >= 0.65)
			{
				return "Impact verdict: stable core";
			}
			return "Impact verdict: balanced shift";
		}

		private String riskChipLabel()
		{
			if ("Medium".equals(concentrationRisk()))
			{
				return "med risk";
			}
			return concentrationRisk().toLowerCase(Locale.US) + " risk";
		}

		private Color translucent(Color color, int alpha)
		{
			return new Color(color.getRed(), color.getGreen(), color.getBlue(),
					Math.max(0, Math.min(255, alpha)));
		}

		private void drawImpactMetricStrip(Graphics2D g, int x, int y, int w, int h)
		{
			int gap = 8;
			String[][] labels = {
					{"Edges changed", formatCount(analysis.recommendationChangedTargetCount)
							+ " / " + formatCount(totalRecommendations()), percent(changedRatio())},
					{"Original kept", formatCount(analysis.recommendationSameAsOriginalCount), percent(sameRatio())},
					{"Unique targets", formatCount(analysis.uniqueRecommendedTargets), "recommended outputs"},
					{"Diversity", percent(analysis.recommendationDiversity), spreadRead()}
			};
			Color[] colors = {BLUE, TEAL, GREEN, PURPLE};
			if (w >= 500 || h < 80)
			{
				int cardW = Math.max(1, (w - gap * 3) / 4);
				for (int i = 0; i < 4; i++)
				{
					drawStoryMetric(g, x + (cardW + gap) * i, y, cardW, h,
							labels[i][0], labels[i][1], labels[i][2], colors[i], i);
				}
				return;
			}
			int cardW = Math.max(1, (w - gap) / 2);
			int cardH = Math.max(1, (h - gap) / 2);
			for (int i = 0; i < 4; i++)
			{
				drawStoryMetric(g,
						x + (i % 2) * (cardW + gap),
						y + (i / 2) * (cardH + gap),
						cardW,
						cardH,
						labels[i][0],
						labels[i][1],
						labels[i][2],
						colors[i],
						i);
			}
		}

		private void drawStoryMetric(
				Graphics2D g,
				int x,
				int y,
				int w,
				int h,
				String label,
				String value,
				String detail,
				Color color,
				int index)
		{
			g.setColor(SURFACE_ALT);
			g.fillRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setColor(BORDER);
			g.drawRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setColor(color);
			g.fillOval(x + 11, y + 8, 6, 6);
			g.setFont(font(Font.BOLD, 9));
			g.setColor(NebulaDesign.TEXT_TERTIARY);
			drawClippedString(g, label.toUpperCase(Locale.US), x + 22, y + 15, w - 32);
			g.setFont(font(Font.BOLD, h >= 64 ? 20 : h < 52 ? 14 : 16));
			g.setColor(TEXT);
			drawClippedString(g, value, x + 12, y + (h >= 64 ? 43 : h < 52 ? 32 : 36), w - 24);
			g.setFont(font(Font.PLAIN, 9));
			g.setColor(NebulaDesign.TEXT_TERTIARY);
			drawClippedString(g, detail, x + 12, y + h - 7, w - 24);
		}

		private void drawDiffStoryCanvas(Graphics2D g, int x, int y, int w, int h)
		{
			drawImpactStage(g, x, y, w, h);
			g.setColor(glass(164));
			g.fillRoundRect(x + 10, y + 10, w - 20, 32, RADIUS, RADIUS);
			g.setColor(BORDER);
			g.drawRoundRect(x + 10, y + 10, w - 20, 32, RADIUS, RADIUS);

			g.setFont(font(Font.BOLD, 11));
			g.setColor(TEXT);
			drawClippedString(g, "Before > Recommended Diff Map", x + 22, y + 31, w / 2);
			g.setFont(font(Font.PLAIN, 10));
			g.setColor(MUTED);
			drawClippedString(g, "Hover lanes for detail-on-demand.",
					x + w / 2, y + 31, w / 2 - 28);

			drawLegend(g, x + 18, y + h - 19, Math.min(460, w - 36));

			int contentY = y + 50;
			int contentH = h - 88;
			if (contentH < 82)
			{
				return;
			}
			int inspectorW = w >= 560 ? Math.max(150, Math.min(172, w / 4)) : 0;
			int mapW = inspectorW > 0 ? w - inspectorW - 32 : w - 24;
			drawDiffMap(g, x + 12, contentY, mapW, contentH);
			if (inspectorW > 0)
			{
				drawExplanationInspector(g, x + w - inspectorW - 12, contentY, inspectorW, contentH);
			}
		}

		private void drawImpactStage(Graphics2D g, int x, int y, int w, int h)
		{
			g.setPaint(new GradientPaint(
					x,
					y,
					themed(NebulaDesign.DAY_SURFACE_ALT, NebulaDesign.NIGHT_INSET),
					x + w,
					y + h,
					themed(NebulaDesign.DAY_INSET, NebulaDesign.NIGHT_CANVAS_BOTTOM)));
			g.fillRoundRect(x, y, w, h, RADIUS + 4, RADIUS + 4);
			g.setColor(translucent(TEAL, 14));
			g.fillRoundRect(x + 18, y + 54, w - 36, Math.max(18, h / 10), RADIUS + 10, RADIUS + 10);
			g.setColor(translucent(BLUE, 12));
			g.fillRoundRect(x + 30, y + Math.max(84, h / 3), w - 60, Math.max(18, h / 12),
					RADIUS + 10, RADIUS + 10);
			g.setColor(translucent(PURPLE, 11));
			g.fillRoundRect(x + 18, y + h - Math.max(72, h / 4), w - 36, Math.max(18, h / 11),
					RADIUS + 10, RADIUS + 10);
			g.setColor(BORDER);
			g.drawRoundRect(x, y, w, h, RADIUS + 4, RADIUS + 4);

			g.setColor(alpha(BORDER, 100));
			for (int gx = x + 26; gx < x + w - 20; gx += 36)
			{
				g.drawLine(gx, y + 48, gx, y + h - 36);
			}
			for (int gy = y + 62; gy < y + h - 34; gy += 28)
			{
				g.drawLine(x + 18, gy, x + w - 18, gy);
			}
		}

		private void drawDiffMap(Graphics2D g, int x, int y, int w, int h)
		{
			int sourceW = Math.max(126, Math.min(142, w / 4));
			int outputW = Math.max(114, Math.min(148, w / 4));
			int laneX = x + sourceW + 22;
			int outputX = x + w - outputW;
			int laneW = Math.max(88, outputX - laneX - 18);
			int centerY = y + h / 2;
			int laneH = Math.max(36, Math.min(46, h / 4));
			int keptY = y + Math.max(8, h / 8);
			int changedY = y + h - laneH - Math.max(8, h / 8);
			if (changedY < keptY + laneH + 12)
			{
				keptY = y + 8;
				changedY = keptY + laneH + 10;
			}

			drawQueryNode(g, x, centerY - 38, sourceW, 76);
			drawImpactLane(g, laneX, keptY, laneW, laneH,
					"Kept original",
					formatCount(analysis.recommendationSameAsOriginalCount) + " stable edges",
					sameRatio(),
					TEAL,
					true);
			drawImpactLane(g, laneX, changedY, laneW, laneH,
					"Changed target",
					formatCount(analysis.recommendationChangedTargetCount) + " rerouted edges",
					changedRatio(),
					BLUE,
					false);
			drawOutputSpreadPanel(g, outputX, y + 4, outputW, h - 8);

			drawImpactCurve(g, x + sourceW, centerY - 12,
					laneX, keptY + laneH / 2, TEAL, 0, sameRatio());
			drawImpactCurve(g, x + sourceW, centerY + 12,
					laneX, changedY + laneH / 2, BLUE, 1, changedRatio());
			drawImpactCurve(g, laneX + laneW, keptY + laneH / 2,
					outputX - 8, y + h / 3, TEAL, 2, sameRatio());
			drawImpactCurve(g, laneX + laneW, changedY + laneH / 2,
					outputX - 8, y + (h * 2) / 3, BLUE, 3, changedRatio());
		}

		private void drawQueryNode(Graphics2D g, int x, int y, int w, int h)
		{
			g.setColor(glass(230));
			g.fillRoundRect(x, y, w, h, RADIUS + 4, RADIUS + 4);
			g.setColor(alpha(BLUE, 80));
			g.drawRoundRect(x, y, w, h, RADIUS + 4, RADIUS + 4);
			int cx = x + 28;
			int cy = y + h / 2;
			g.setColor(tint(BLUE, 0.80f));
			g.fillOval(cx - 18, cy - 18, 36, 36);
			g.setStroke(new BasicStroke(2f));
			g.setColor(BLUE);
			g.drawOval(cx - 18, cy - 18, 36, 36);
			g.setStroke(new BasicStroke(1f));

			g.setFont(font(Font.BOLD, 9));
			g.setColor(deep(BLUE));
			drawClippedString(g, "QUERY", x + 52, y + 21, w - 60);
			g.setFont(font(Font.BOLD, 12));
			g.setColor(TEXT);
			drawClippedString(g, compact(sourceWord(snapshot), 22), x + 52, y + 42, w - 58);
			g.setFont(font(Font.PLAIN, 9));
			g.setColor(MUTED);
			drawClippedString(g, formatCount(totalRecommendations()) + " decisions",
					x + 52, y + 61, w - 60);
		}

		private void drawImpactLane(
				Graphics2D g,
				int x,
				int y,
				int w,
				int h,
				String label,
				String detail,
				double ratio,
				Color color,
				boolean kept)
		{
			if (kept)
			{
				keptLaneBounds.setBounds(x, y, w, h);
			}
			else
			{
				changedLaneBounds.setBounds(x, y, w, h);
			}
			g.setColor(glass(232));
			g.fillRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setColor(alpha(color, 76));
			g.drawRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setColor(tint(color, 0.82f));
			g.fillRoundRect(x + 8, y + 7, 42, h - 14, RADIUS, RADIUS);
			g.setFont(font(Font.BOLD, 11));
			g.setColor(deep(color));
			String pct = percent(ratio);
			FontMetrics metrics = g.getFontMetrics();
			g.drawString(pct, x + 29 - metrics.stringWidth(pct) / 2, y + h / 2 + 4);
			g.setFont(font(Font.BOLD, 10));
			g.setColor(TEXT);
			drawClippedString(g, label, x + 60, y + 17, w - 72);
			g.setFont(font(Font.PLAIN, 9));
			g.setColor(MUTED);
			drawClippedString(g, detail, x + 60, y + h - 11, w - 72);
			int barW = Math.max(20, w - 72);
			int barY = y + h - 5;
			g.setColor(track());
			g.fillRoundRect(x + 60, barY, barW, 3, 3, 3);
			g.setColor(color);
			g.fillRoundRect(x + 60, barY,
					Math.max(ratio > 0.0 ? 2 : 0, (int)(barW * clamp01(ratio))),
					3, 3, 3);
		}

		private void drawOutputSpreadPanel(Graphics2D g, int x, int y, int w, int h)
		{
			outputBounds.setBounds(x, y, w, h);
			g.setColor(glass(232));
			g.fillRoundRect(x, y, w, h, RADIUS + 4, RADIUS + 4);
			g.setColor(alpha(GREEN, 80));
			g.drawRoundRect(x, y, w, h, RADIUS + 4, RADIUS + 4);
			g.setFont(font(Font.BOLD, 9));
			g.setColor(deep(GREEN));
			drawClippedString(g, "RECOMMENDED", x + 12, y + 18, w - 24);
			g.setFont(font(Font.BOLD, 18));
			g.setColor(TEXT);
			drawClippedString(g, formatCount(analysis.uniqueRecommendedTargets), x + 12, y + 42, w - 24);
			g.setFont(font(Font.PLAIN, 9));
			g.setColor(MUTED);
			drawClippedString(g, "unique targets", x + 12, y + 58, w - 24);
			if (h < 124)
			{
				return;
			}

			int rowY = y + 72;
			int rowH = Math.max(22, Math.min(28, (h - 86) / 3));
			int max = 1;
			for (GraphAnalysisEngine.NodeScore node : analysis.topRecommendedTargets)
			{
				max = Math.max(max, node.value);
			}
			for (int i = 0; i < Math.min(3, analysis.topRecommendedTargets.size()); i++)
			{
				GraphAnalysisEngine.NodeScore node = analysis.topRecommendedTargets.get(i);
				drawOutputRow(g, x + 10, rowY + i * (rowH + 6), w - 20, rowH, i + 1, node, max);
			}
		}

		private void drawOutputRow(
				Graphics2D g,
				int x,
				int y,
				int w,
				int h,
				int rank,
				GraphAnalysisEngine.NodeScore node,
				int max)
		{
			if (w <= 20 || h <= 16)
			{
				return;
			}
			Color color = GREEN;
			g.setColor(SURFACE_ALT);
			g.fillRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setColor(BORDER);
			g.drawRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setColor(rank == 1 ? color : tint(color, 0.80f));
			g.fillRoundRect(x, y, 22, h, RADIUS, RADIUS);
			g.setFont(font(Font.BOLD, 10));
			g.setColor(rank == 1 ? NebulaDesign.TEXT_ON_ACCENT : deep(color));
			drawClippedString(g, Integer.toString(rank), x + 8, y + h / 2 + 4, 12);
			g.setColor(TEXT);
			drawClippedString(g, compact(node.name, 18), x + 30, y + h / 2 + 4, w - 42);
			int barW = Math.max(18, w - 34);
			int barY = y + h - 4;
			g.setColor(track());
			g.fillRoundRect(x + 30, barY, barW, 3, 3, 3);
			g.setColor(color);
			g.fillRoundRect(x + 30, barY,
					Math.max(2, (int)(barW * (node.value / (double)Math.max(1, max)))),
					3, 3, 3);
		}

		private void drawImpactCurve(
				Graphics2D g,
				int x1,
				int y1,
				int x2,
				int y2,
				Color color,
				int index,
				double weight)
		{
			int controlX = (x1 + x2) / 2;
			int controlY = (y1 + y2) / 2 + (index % 2 == 0 ? -14 : 14);
			float stroke = (float)Math.max(1.6, Math.min(4.6, 1.8 + weight * 4.0));
			g.setStroke(new BasicStroke(stroke + 7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			g.setColor(translucent(color, 28));
			g.draw(new QuadCurve2D.Double(x1, y1, controlX, controlY, x2, y2));
			g.setStroke(new BasicStroke(stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			g.setColor(translucent(color, 96));
			g.draw(new QuadCurve2D.Double(x1, y1, controlX, controlY, x2, y2));
			double t = (phase + index * 0.23) % 1.0;
			double px = (1 - t) * (1 - t) * x1 + 2 * (1 - t) * t * controlX + t * t * x2;
			double py = (1 - t) * (1 - t) * y1 + 2 * (1 - t) * t * controlY + t * t * y2;
			g.setColor(translucent(color, 74));
			g.fill(new Ellipse2D.Double(px - 8.0, py - 8.0, 16, 16));
			g.setColor(color);
			g.fill(new Ellipse2D.Double(px - 4.2, py - 4.2, 8.4, 8.4));
			g.setStroke(new BasicStroke(1f));
		}

		private void drawExplanationInspector(Graphics2D g, int x, int y, int w, int h)
		{
			inspectorBounds.setBounds(x, y, w, h);
			g.setColor(glass(232));
			g.fillRoundRect(x, y, w, h, RADIUS + 4, RADIUS + 4);
			g.setColor(alpha(PURPLE, 80));
			g.drawRoundRect(x, y, w, h, RADIUS + 4, RADIUS + 4);
			g.setFont(font(Font.BOLD, 9));
			g.setColor(deep(PURPLE));
			drawClippedString(g, "WHY THIS HAPPENED", x + 12, y + 18, w - 24);
			g.setFont(font(Font.BOLD, 12));
			g.setColor(TEXT);
			drawWrapped(g, whyChangedLine(), x + 12, y + 40, w - 24, 2, 14);

			int rowY = y + 78;
			if (h > 118)
			{
				drawInspectorRow(g, x + 12, rowY, w - 24, 34,
						"Audit confidence",
						Integer.toString(analysis.healthScore) + "/100",
						statusColor(analysis),
						analysis.healthScore / 100.0);
				rowY += 43;
			}
			if (h > 158)
			{
				drawInspectorRow(g, x + 12, rowY, w - 24, 34,
						"Concentration risk",
						concentrationRisk(),
						concentrationColor(),
						topTargetShare());
				rowY += 46;
			}
			if (h > 204)
			{
				g.setFont(font(Font.BOLD, 9));
				g.setColor(deep(GREEN));
				drawClippedString(g, "NEXT ACTION", x + 12, rowY, w - 24);
				g.setFont(font(Font.PLAIN, 10));
				g.setColor(TEXT);
				drawWrapped(g, nextActionLine(), x + 12, rowY + 18, w - 24, 3, 13);
			}
		}

		private void drawInspectorRow(
				Graphics2D g,
				int x,
				int y,
				int w,
				int h,
				String label,
				String value,
				Color color,
				double progress)
		{
			g.setColor(SURFACE_ALT);
			g.fillRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setColor(BORDER);
			g.drawRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setFont(font(Font.BOLD, 9));
			g.setColor(deep(color));
			drawClippedString(g, label.toUpperCase(Locale.US), x + 9, y + 13, w - 18);
			g.setFont(font(Font.BOLD, 11));
			g.setColor(TEXT);
			drawClippedString(g, value, x + 9, y + 28, w - 18);
			drawProgressBar(g, x + 9, y + h - 5, w - 18, 3, progress, color);
		}

		private void drawLegend(Graphics2D g, int x, int y, int w)
		{
			String[] labels = {"kept", "changed", "unique target", "risk"};
			Color[] colors = {TEAL, BLUE, GREEN, RED};
			int cursor = x;
			for (int i = 0; i < labels.length; i++)
			{
				if (cursor > x + w - 58)
				{
					break;
				}
				g.setColor(colors[i]);
				if (i == 2)
				{
					g.fillRect(cursor, y - 7, 10, 10);
				}
				else
				{
					g.fillOval(cursor, y - 7, 10, 10);
				}
				g.setFont(font(Font.PLAIN, 9));
				g.setColor(MUTED);
				drawClippedString(g, labels[i], cursor + 14, y + 2, 70);
				cursor += 78;
			}
		}

		private void drawJudgmentStrip(Graphics2D g, int x, int y, int w, int h)
		{
			String[][] cards = {
					{"Diversity Lift", percent(analysis.recommendationDiversity), "unique output spread"},
					{"Stability Score", percent(sameRatio()), "original links kept"},
					{"Novelty", percent(changedRatio()), "target reroutes"},
					{"Concentration", concentrationRisk(), spreadRead()},
					{"Alerts", formatCount(analysis.warnings.size()), alertRead()}
			};
			Color[] colors = {GREEN, TEAL, BLUE, concentrationColor(), statusColor(analysis)};
			int gap = 7;
			int cardW = Math.max(74, (w - gap * (cards.length - 1)) / cards.length);
			int cursor = x;
			for (int i = 0; i < cards.length; i++)
			{
				if (cursor + cardW > x + w + 1)
				{
					break;
				}
				drawJudgmentCard(g, cursor, y, cardW, h,
						cards[i][0], cards[i][1], cards[i][2], colors[i]);
				cursor += cardW + gap;
			}
		}

		private void drawJudgmentCard(
				Graphics2D g,
				int x,
				int y,
				int w,
				int h,
				String label,
				String value,
				String detail,
				Color color)
		{
			g.setColor(SURFACE_ALT);
			g.fillRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setColor(BORDER);
			g.drawRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setColor(color);
			g.fillOval(x + 10, y + 8, 6, 6);
			g.setFont(font(Font.BOLD, 8));
			g.setColor(NebulaDesign.TEXT_TERTIARY);
			drawClippedString(g, label.toUpperCase(Locale.US), x + 20, y + 14, w - 30);
			g.setFont(font(Font.BOLD, 11));
			g.setColor(TEXT);
			drawClippedString(g, value, x + 10, y + 32, w - 20);
			g.setFont(font(Font.PLAIN, 8));
			g.setColor(NebulaDesign.TEXT_TERTIARY);
			drawClippedString(g, detail, x + 10, y + h - 8, w - 20);
		}

		private void drawEmptyImpactState(Graphics2D g, int x, int y, int w, int h)
		{
			if (h <= 0)
			{
				return;
			}
			drawImpactStage(g, x, y, w, h);
			int centerX = x + w / 2;
			// Keep the ghost diagram near the intro copy even when the card
			// is viewport-stretched very tall.
			int centerY = y + Math.max(74, Math.min(h / 2 - 12, 330));
			int leftX = Math.max(x + 24, centerX - 172);
			int rightX = Math.min(x + w - 114, centerX + 96);
			int laneY = centerY - 5;

			g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			g.setColor(alpha(NebulaDesign.TEXT_TERTIARY, 105));
			g.draw(new QuadCurve2D.Double(leftX + 68, centerY, centerX, laneY - 42, rightX, centerY - 18));
			g.draw(new QuadCurve2D.Double(leftX + 68, centerY, centerX, laneY + 42, rightX, centerY + 18));
			g.setStroke(new BasicStroke(1f));

			drawGhostNode(g, leftX, centerY - 34, 72, "Original");
			drawGhostNode(g, rightX, centerY - 52, 82, "Recommended");
			drawGhostLane(g, centerX - 54, centerY - 18, 108, 36);

			int copyY = y + 58;
			g.setFont(font(Font.BOLD, 15));
			g.setColor(TEXT);
			drawClippedString(g, "No recommendation run yet", x + 24, copyY, w - 48);
			g.setFont(font(Font.PLAIN, 11));
			g.setColor(MUTED);
			drawWrapped(g,
					"The impact graph will appear here after the simulator saves paired original and recommended edge rows.",
					x + 24,
					copyY + 21,
					w - 48,
					2,
					14);

			int stepY = y + h - 54;
			if (stepY > copyY + 54)
			{
				int gap = 8;
				int stepW = Math.max(1, (w - 48 - gap * 2) / 3);
				drawEmptyStep(g, x + 24, stepY, stepW, 34, "Load graph", BLUE);
				drawEmptyStep(g, x + 24 + stepW + gap, stepY, stepW, 34, "Run query", GREEN);
				drawEmptyStep(g, x + 24 + (stepW + gap) * 2, stepY, stepW, 34, "Inspect diff", PURPLE);
			}
		}

		private void drawGhostNode(Graphics2D g, int x, int y, int size, String label)
		{
			g.setColor(glass(186));
			g.fillOval(x, y, size, size);
			g.setColor(BORDER);
			g.drawOval(x, y, size, size);
			g.setFont(font(Font.BOLD, 9));
			g.setColor(MUTED);
			drawClippedString(g, label, x - 16, y + size + 17, size + 32);
		}

		private void drawGhostLane(Graphics2D g, int x, int y, int w, int h)
		{
			g.setColor(glass(212));
			g.fillRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setColor(BORDER);
			g.drawRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setFont(font(Font.BOLD, 9));
			g.setColor(MUTED);
			drawClippedString(g, "Diff map waiting", x + 12, y + 22, w - 24);
		}

		private void drawEmptyStep(Graphics2D g, int x, int y, int w, int h, String label, Color color)
		{
			g.setColor(tint(color, 0.86f));
			g.fillRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setColor(alpha(color, 70));
			g.drawRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setColor(color);
			g.fillOval(x + 10, y + h / 2 - 4, 8, 8);
			g.setFont(font(Font.BOLD, 9));
			g.setColor(TEXT);
			drawClippedString(g, label, x + 24, y + h / 2 + 4, w - 32);
		}

		private void drawProgressBar(Graphics2D g, int x, int y, int w, int h, double value, Color color)
		{
			g.setColor(track());
			g.fillRoundRect(x, y, w, h, h, h);
			g.setColor(color);
			g.fillRoundRect(x, y, Math.max(value > 0.0 ? 2 : 0, (int)(w * clamp01(value))), h, h, h);
		}

		private double clamp01(double value)
		{
			return Math.max(0.0, Math.min(1.0, value));
		}

		private String whyChangedLine()
		{
			if (changedRatio() >= 0.55)
			{
				return "Recommended targets diverged strongly from original graph edges.";
			}
			if (sameRatio() >= 0.65)
			{
				return "Most recommendations reinforced existing graph structure.";
			}
			return "The run mixed stable original links with meaningful target reroutes.";
		}

		private String nextActionLine()
		{
			if (topTargetShare() >= 0.45 && !analysis.topRecommendedTargets.isEmpty())
			{
				return "Review " + compact(analysis.topRecommendedTargets.get(0).name, 20)
						+ "; it receives " + percent(topTargetShare()) + " of outputs.";
			}
			if (changedRatio() >= 0.50)
			{
				return "Inspect the changed-target lane first, then compare top outputs.";
			}
			return "Spot-check preserved edges and confirm the top recommended targets.";
		}

		private String concentrationRisk()
		{
			if (topTargetShare() >= 0.45 || analysis.recommendationDiversity < 0.10)
			{
				return "High";
			}
			if (topTargetShare() >= 0.28 || analysis.recommendationDiversity < 0.24)
			{
				return "Medium";
			}
			return "Low";
		}

		private Color concentrationColor()
		{
			String risk = concentrationRisk();
			if ("High".equals(risk))
			{
				return RED;
			}
			if ("Medium".equals(risk))
			{
				return AMBER;
			}
			return GREEN;
		}

		private String alertRead()
		{
			return analysis.warnings.size() <= 1 ? "no major blockers" : "review warnings";
		}

		private double ratio(long value, long total)
		{
			return total <= 0L ? 0.0 : value / (double)total;
		}

		private String spreadRead()
		{
			if (!hasRecommendationRun())
			{
				return "waiting";
			}
			if (analysis.recommendationDiversity < 0.10)
			{
				return "very concentrated";
			}
			if (analysis.recommendationDiversity < 0.25)
			{
				return "moderately concentrated";
			}
			return "diverse outputs";
		}
	}

	private static final class RankingsPanel extends VisualPanel
	{
		protected void paintComponent(Graphics graphics)
		{
			super.paintComponent(graphics);
			Graphics2D g = (Graphics2D)graphics.create();
			prepare(g);
			int w = getWidth();
			int h = getHeight();
			int gap = 8;
			int third = Math.max(98, (h - 20 - gap * 2) / 3);
			drawLeaderboard(g, "Top recommended outputs", analysis.topRecommendedTargets,
					10, 4, w - 20, third, GREEN);
			drawLeaderboard(g, "Top incoming hubs", analysis.topIncomingNodes,
					10, 4 + third + gap, w - 20, third, BLUE);
			drawLeaderboard(g, communityListTitle(analysis), analysis.topCommunities,
					10, 4 + (third + gap) * 2, w - 20,
					Math.max(92, h - 12 - (third + gap) * 2), PURPLE);
			g.dispose();
		}

		private void drawLeaderboard(
				Graphics2D g,
				String title,
				List<GraphAnalysisEngine.NodeScore> rows,
				int x,
				int y,
				int w,
				int h,
				Color color)
		{
			g.setPaint(new GradientPaint(
					x,
					y,
					themed(NebulaDesign.DAY_SURFACE, NebulaDesign.NIGHT_SURFACE),
					x + w,
					y + h,
					tint(color, 0.90f)));
			g.fillRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setColor(BORDER);
			g.drawRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setColor(color);
			g.fillOval(x + 12, y + 15, 7, 7);
			g.setFont(font(Font.BOLD, 10));
			g.setColor(deep(color));
			drawClippedString(g, title.toUpperCase(Locale.US), x + 25, y + 24, w - 43);
			if (rows == null || rows.isEmpty())
			{
				g.setFont(font(Font.PLAIN, 11));
				g.setColor(MUTED);
				drawClippedString(g, "No data yet.", x + 18, y + 55, w - 36);
				return;
			}
			int max = 1;
			for (GraphAnalysisEngine.NodeScore row : rows)
			{
				max = Math.max(max, row.value);
			}
			int rowY = y + 48;
			int rowH = Math.max(30, Math.min(38, (h - 56) / 4));
			int limit = Math.min(4, rows.size());
			for (int i = 0; i < limit; i++)
			{
				GraphAnalysisEngine.NodeScore score = rows.get(i);
				drawLeaderboardRow(g, x + 10, rowY, w - 20, rowH,
						i + 1, score, max, color);
				rowY += rowH + 6;
				if (rowY > y + h - 10)
				{
					break;
				}
			}
		}

		private void drawLeaderboardRow(
				Graphics2D g,
				int x,
				int y,
				int w,
				int h,
				int rank,
				GraphAnalysisEngine.NodeScore score,
				int max,
				Color color)
		{
			g.setColor(glass(224));
			g.fillRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setColor(BORDER);
			g.drawRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setColor(rank == 1 ? color : tint(color, 0.80f));
			g.fillRoundRect(x, y, 34, h, RADIUS, RADIUS);
			g.setFont(font(Font.BOLD, 11));
			g.setColor(rank == 1 ? NebulaDesign.TEXT_ON_ACCENT : deep(color));
			drawClippedString(g, Integer.toString(rank), x + 12, y + h / 2 + 4, 16);
			int valueW = 54;
			g.setFont(font(Font.BOLD, 11));
			g.setColor(TEXT);
			drawClippedString(g, score.name, x + 44, y + 15, w - 54 - valueW);
			g.setFont(font(Font.PLAIN, 9));
			g.setColor(MUTED);
			drawClippedString(g, score.detail, x + 44, y + h - 7, w - 54 - valueW);
			g.setFont(font(Font.BOLD, 11));
			g.setColor(deep(color));
			drawClippedString(g, formatCount(score.value), x + w - valueW, y + 17, valueW - 8);
			int barX = x + 44;
			int barY = y + h - 4;
			int barW = w - 54 - valueW;
			g.setColor(track());
			g.fillRoundRect(barX, barY, barW, 3, 3, 3);
			g.setColor(color);
			g.fillRoundRect(barX, barY,
					Math.max(score.value > 0 ? 2 : 0,
							(int)(barW * (score.value / (double)Math.max(1, max)))),
					3,
					3,
					3);
		}
	}

	private static final class EvidencePanel extends VisualPanel
	{
		protected void paintComponent(Graphics graphics)
		{
			super.paintComponent(graphics);
			Graphics2D g = (Graphics2D)graphics.create();
			prepare(g);
			int w = getWidth();
			int h = getHeight();
			if (w < 260 || h < 130)
			{
				g.dispose();
				return;
			}

			int pad = 8;
			int gap = 8;
			boolean stacked = w < 520;
			if (stacked)
			{
				int takeawayH = Math.max(74, Math.min(112, h / 2));
				drawTakeawayBlock(g, pad, pad, w - pad * 2, takeawayH);
				drawArtifactEvidence(g, pad, pad + takeawayH + gap, w - pad * 2,
						h - takeawayH - gap - pad * 2, true);
			}
			else
			{
				int leftW = Math.max(210, (int)((w - pad * 2 - gap) * 0.56));
				drawTakeawayBlock(g, pad, pad, leftW, h - pad * 2);
				drawArtifactEvidence(g, pad + leftW + gap, pad,
						w - leftW - gap - pad * 2, h - pad * 2, false);
			}
			g.dispose();
		}

		private void drawTakeawayBlock(Graphics2D g, int x, int y, int w, int h)
		{
			Color color = statusColor(analysis);
			g.setPaint(new GradientPaint(
					x,
					y,
					tint(color, 0.84f),
					x + w,
					y + h,
					themed(NebulaDesign.DAY_SURFACE, NebulaDesign.NIGHT_SURFACE_ALT)));
			g.fillRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setColor(alpha(color, 88));
			g.drawRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setColor(alpha(color, 60));
			g.fillRoundRect(x, y, 5, h, RADIUS, RADIUS);

			g.setFont(font(Font.BOLD, 9));
			g.setColor(deep(color));
			drawClippedString(g, "INTERPRETATION", x + 15, y + 17, w - 28);
			g.setFont(font(Font.BOLD, 15));
			g.setColor(TEXT);
			drawClippedString(g, sentenceCase(analysis.statusLabel()), x + 15, y + 39, w - 28);
			g.setFont(font(Font.PLAIN, 11));
			g.setColor(MUTED);
			drawWrapped(g, mainTakeaway(), x + 15, y + 60, w - 28,
					h < 116 ? 2 : 3, 14);

			int footerY = y + h - 32;
			if (footerY > y + 84)
			{
				drawEvidenceChip(g, x + 14, footerY, Math.max(74, (w - 38) / 2), 22,
						formatCount(analysis.warnings.size()) + " alerts", statusColor(analysis));
				drawEvidenceChip(g, x + 22 + Math.max(74, (w - 38) / 2), footerY,
						Math.max(74, (w - 38) / 2), 22,
						formatCount(analysis.validEdgeCount) + " edges", GREEN);
			}
		}

		private void drawArtifactEvidence(
				Graphics2D g,
				int x,
				int y,
				int w,
				int h,
				boolean stacked)
		{
			if (h <= 34)
			{
				return;
			}
			g.setColor(SURFACE_ALT);
			g.fillRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setColor(BORDER);
			g.drawRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setFont(font(Font.BOLD, 9));
			g.setColor(deep(PURPLE));
			drawClippedString(g, "ARTIFACTS", x + 12, y + 17, w - 24);

			int chipY = y + 28;
			int chipH = stacked ? 38 : 44;
			drawArtifactChip(g, "Node map", analysis.mappingFile, x + 10, chipY, w - 20, chipH, BLUE);
			drawArtifactChip(g, "Edge list", analysis.edgeFile, x + 10,
					chipY + chipH + 7, w - 20, chipH, GREEN);

			int contractY = chipY + (chipH + 7) * 2 + 4;
			if (contractY + 42 <= y + h)
			{
				g.setColor(glass(190));
				g.fillRoundRect(x + 10, contractY, w - 20, y + h - contractY - 8,
						RADIUS, RADIUS);
				g.setColor(BORDER);
				g.drawRoundRect(x + 10, contractY, w - 20, y + h - contractY - 8,
						RADIUS, RADIUS);
				g.setFont(font(Font.BOLD, 9));
				g.setColor(deep(PURPLE));
				drawClippedString(g, "READ-ONLY", x + 20, contractY + 16, w - 40);
				g.setFont(font(Font.PLAIN, 10));
				g.setColor(TEXT);
				drawWrapped(g,
						"Reads Gephi files only; recommendation scores and exports are untouched.",
						x + 20,
						contractY + 33,
						w - 40,
						stacked ? 2 : 3,
						13);
			}
		}

		private void drawArtifactChip(
				Graphics2D g,
				String label,
				File file,
				int x,
				int y,
				int w,
				int h,
				Color color)
		{
			g.setColor(tint(color, 0.86f));
			g.fillRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setColor(alpha(color, 70));
			g.drawRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setFont(font(Font.BOLD, 8));
			g.setColor(deep(color));
			drawClippedString(g, label.toUpperCase(Locale.US), x + 10, y + 13, w - 20);
			g.setFont(NebulaDesign.fontMono(10f));
			g.setColor(TEXT);
			drawClippedString(g, file == null ? "Not selected" : file.getName(),
					x + 10, y + 29, w - 20);
			if (h >= 42)
			{
				g.setFont(font(Font.PLAIN, 8));
				g.setColor(MUTED);
				drawClippedString(g,
						file != null && file.isFile()
								? formatCount(file.length()) + " bytes"
								: "missing",
						x + 10,
						y + h - 6,
						w - 20);
			}
		}

		private void drawEvidenceChip(
				Graphics2D g,
				int x,
				int y,
				int w,
				int h,
				String label,
				Color color)
		{
			g.setColor(tint(color, 0.85f));
			g.fillRoundRect(x, y, w, h, h, h);
			g.setColor(alpha(color, 78));
			g.drawRoundRect(x, y, w, h, h, h);
			g.setFont(NebulaDesign.fontMedium(9.5f));
			g.setColor(deep(color));
			drawClippedString(g, label, x + 10, y + 15, w - 20);
		}

		private String mainTakeaway()
		{
			if (!analysis.available)
			{
				return "Waiting for the simulator's graph map and edge files.";
			}
			if (analysis.validEdgeCount == 0L)
			{
				return "Users are loaded, but no recommendation edges have been persisted yet.";
			}
			if (analysis.estimatedCommunityCount <= 2 && analysis.activeNodeCount >= 20)
			{
				return communityMetricDetail(analysis)
						+ " finds only " + analysis.estimatedCommunityCount
						+ " groups, so the active graph is clustering into very few communities.";
			}
			if (analysis.hubConcentration >= 0.35)
			{
				return "One or two nodes receive a large share of graph edges.";
			}
			return "The saved graph artifacts are structurally readable and ready for interpretation.";
		}
	}

}
