package TwitterGatherDataFollowers.userRyersonU;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.RadialGradientPaint;
import java.awt.Shape;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

final class RecommendationEvidenceStudioPanel extends JPanel
{
	private static final Color BACKGROUND = NebulaDesign.CANVAS_TOP;
	private static final Color SURFACE = NebulaDesign.SURFACE;
	private static final Color SURFACE_ALT = NebulaDesign.SURFACE_ALT;
	private static final Color BORDER = NebulaDesign.BORDER_SUBTLE;
	private static final Color TEXT = NebulaDesign.TEXT_PRIMARY;
	private static final Color MUTED = NebulaDesign.TEXT_SECONDARY;
	private static final Color BLUE = NebulaDesign.ACCENT;
	private static final Color PURPLE = NebulaDesign.PURPLE;
	private static final Color GREEN = NebulaDesign.SUCCESS;
	private static final Color AMBER = NebulaDesign.WARNING;
	private static final Color RED = NebulaDesign.DANGER;
	private static final int RADIUS = 12;

	private final ControllerAgentGui controller;
	private final DefaultListModel<EvidenceEntry> entryModel =
			new DefaultListModel<EvidenceEntry>();
	private final JList<EvidenceEntry> entryList =
			new PlaceholderList<EvidenceEntry>(
					entryModel,
					"Completed recommendations land here. Start a simulator run to collect evidence.");
	private final DefaultListModel<MatchRow> matchModel =
			new DefaultListModel<MatchRow>();
	private final JList<MatchRow> matchList =
			new PlaceholderList<MatchRow>(
					matchModel,
					"Pick a recommendation on the left to list its ranked candidates.");
	private final TokenLabel heroTitle = new TokenLabel("Recommendation Evidence Studio");
	private final TokenLabel heroSubtitle = new TokenLabel("Click a completed recommendation to see why it happened.");
	private final TokenLabel statusLabel = new TokenLabel("Waiting for recommendation output.");
	private final EvidenceSignalPanel signalPanel = new EvidenceSignalPanel();
	private final MetricStripPanel metricStrip = new MetricStripPanel();
	private final EvidenceFlowPanel flowPanel = new EvidenceFlowPanel();
	private final CounterfactualPanel counterfactualPanel = new CounterfactualPanel();
	private final ComparisonPanel comparisonPanel = new ComparisonPanel();
	private final SourceRowsPanel targetRowsPanel =
			new SourceRowsPanel("Target Profile", BLUE, true);
	private final SourceRowsPanel candidateRowsPanel =
			new SourceRowsPanel("Recommended Candidate", GREEN, false);
	private final TermEvidencePanel termEvidencePanel = new TermEvidencePanel();
	private final TermHighlightPanel termHighlightPanel = new TermHighlightPanel();
	private final GraphImpactPanel graphImpactPanel = new GraphImpactPanel();
	private final VerdictPanel verdictPanel = new VerdictPanel();
	private final EmptyStatePanel emptyStatePanel = new EmptyStatePanel();
	private final JPanel dashboard = new DashboardPanel();
	private final Timer animationTimer;
	private SimulatorHelpPanel.Snapshot latestSnapshot =
			SimulatorHelpPanel.Snapshot.empty();
	private RecommendationEvidenceEngine.EvidenceReport currentReport =
			RecommendationEvidenceEngine.EvidenceReport.empty(latestSnapshot);
	private int requestSequence = 0;

	RecommendationEvidenceStudioPanel(ControllerAgentGui controller)
	{
		super(new BorderLayout(14, 14));
		this.controller = controller;
		setBackground(BACKGROUND);
		setBorder(new EmptyBorder(14, 14, 14, 14));
		add(createHeader(), BorderLayout.NORTH);
		add(createWorkspace(), BorderLayout.CENTER);
		add(createFooter(), BorderLayout.SOUTH);
		termEvidencePanel.setSelectionListener(new TermSelectionListener()
		{
			public void termSelected(RecommendationEvidenceEngine.TermLocation location)
			{
				termHighlightPanel.setTerm(location);
				if (location != null)
				{
					statusLabel.setText("Highlighted term '" + location.term
							+ "' across target and candidate rows.");
				}
			}
		});

		//A run's graph is written with its record, after its recommendations arrived here.
		RunRecords.addListener(new RunRecords.Listener()
		{
			public void runRecorded(SchedulingRunContext context, RunMetrics metrics, java.io.File directory)
			{
				SwingUtilities.invokeLater(new Runnable()
				{
					public void run()
					{
						refreshGraphEvidence();
					}
				});
			}
		});

		animationTimer = new Timer(33, new ActionListener()
		{
			public void actionPerformed(ActionEvent event)
			{
				signalPanel.tick();
				flowPanel.tick();
				graphImpactPanel.tick();
			}
		});
		addHierarchyListener(new HierarchyListener()
		{
			public void hierarchyChanged(HierarchyEvent event)
			{
				if ((event.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0)
				{
					updateAnimationState();
				}
			}
		});
		updateAnimationState();
		renderReport(currentReport);
	}

	public void addNotify()
	{
		super.addNotify();
		updateAnimationState();
	}

	public void removeNotify()
	{
		animationTimer.stop();
		super.removeNotify();
	}

	private void updateAnimationState()
	{
		if (isShowing())
		{
			if (!animationTimer.isRunning())
			{
				animationTimer.start();
			}
		}
		else
		{
			animationTimer.stop();
		}
	}

	protected void paintComponent(Graphics graphics)
	{
		Graphics2D g2 = (Graphics2D) graphics.create();
		prepare(g2);
		boolean night = StudioNightTheme.isNightMode();
		Color top = night ? NebulaDesign.NIGHT_CANVAS_TOP : NebulaDesign.DAY_CANVAS_TOP;
		Color bottom = night ? NebulaDesign.NIGHT_CANVAS_BOTTOM : NebulaDesign.DAY_CANVAS_BOTTOM;
		g2.setPaint(new GradientPaint(0, 0, top, 0, Math.max(1, getHeight()), bottom));
		g2.fillRect(0, 0, getWidth(), getHeight());
		int glow = night ? 22 : 12;
		paintGlow(g2, getWidth() / 6, 0, Math.max(240, getHeight()), BLUE, glow);
		paintGlow(g2, getWidth() * 5 / 6, getHeight(), Math.max(240, getHeight()),
				PURPLE, Math.max(6, glow - 4));
		g2.dispose();
	}

	private static void paintGlow(
			Graphics2D g2,
			int centerX,
			int centerY,
			int radius,
			Color hue,
			int alpha)
	{
		int safeRadius = Math.max(24, radius);
		RadialGradientPaint paint = new RadialGradientPaint(
				new Point2D.Float(centerX, centerY),
				safeRadius,
				new float[] { 0f, 1f },
				new Color[] {
						NebulaDesign.withAlpha(hue, alpha),
						NebulaDesign.withAlpha(hue, 0)
				});
		g2.setPaint(paint);
		g2.fillRect(centerX - safeRadius, centerY - safeRadius,
				safeRadius * 2, safeRadius * 2);
	}

	void refreshFromSimulator()
	{
		if (controller != null)
		{
			latestSnapshot = controller.buildSimulatorHelpSnapshot();
		}
		if (entryModel.isEmpty())
		{
			renderReport(RecommendationEvidenceEngine.EvidenceReport.empty(latestSnapshot));
		}
		else
		{
			refreshGraphEvidence();
		}
	}

	/** Re-reads the selected recommendation once its run's graph exists (it is written at the run's end). */
	private void refreshGraphEvidence()
	{
		if (currentReport != null && currentReport.available && !currentReport.graphAvailable
				&& RunGraphRecord.latest() != null)
		{
			analyzeSelected();
		}
	}

	void clearRecommendations()
	{
		entryModel.clear();
		matchModel.clear();
		currentReport = RecommendationEvidenceEngine.EvidenceReport.empty(latestSnapshot);
		renderReport(currentReport);
		statusLabel.setText("Recommendation evidence cleared for the next run.");
	}

	void recordRecommendation(
			final SimulatorHelpPanel.Snapshot snapshot,
			final RecommendationEvidenceEngine.RecommendationEvent event)
	{
		if (event == null || event.matches.isEmpty())
		{
			return;
		}
		Runnable update = new Runnable()
		{
			public void run()
			{
				latestSnapshot = snapshot == null
						? SimulatorHelpPanel.Snapshot.empty() : snapshot;
				EvidenceEntry entry = new EvidenceEntry(latestSnapshot, event);
				entryModel.add(0, entry);
				entryList.setSelectedIndex(0);
				rebuildMatchList(entry);
				matchList.setSelectedIndex(0);
				statusLabel.setText("Evidence captured for "
						+ event.targetUser + " -> " + event.matches.get(0).name + ".");
			}
		};
		if (SwingUtilities.isEventDispatchThread())
		{
			update.run();
		}
		else
		{
			SwingUtilities.invokeLater(update);
		}
	}

	private JPanel createHeader()
	{
		HeaderPanel header = new HeaderPanel();
		header.setLayout(new BorderLayout(18, 0));
		header.setBorder(new EmptyBorder(14, 22, 14, 22));

		JPanel titleStack = new JPanel();
		titleStack.setOpaque(false);
		titleStack.setLayout(new BoxLayout(titleStack, BoxLayout.Y_AXIS));
		TokenLabel eyebrow = new TokenLabel("STUDIO  ·  EXPLAINABILITY");
		eyebrow.setFont(NebulaDesign.fontSection());
		eyebrow.setToken(BLUE);
		heroTitle.setFont(NebulaDesign.fontSemiBold(23f));
		heroTitle.setToken(TEXT);
		heroSubtitle.setFont(NebulaDesign.fontBody(12.5f));
		heroSubtitle.setToken(MUTED);
		eyebrow.setAlignmentX(Component.LEFT_ALIGNMENT);
		heroTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
		heroSubtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
		titleStack.add(eyebrow);
		titleStack.add(Box.createRigidArea(new Dimension(0, 4)));
		titleStack.add(heroTitle);
		titleStack.add(Box.createRigidArea(new Dimension(0, 3)));
		titleStack.add(heroSubtitle);
		titleStack.add(Box.createRigidArea(new Dimension(0, 6)));
		titleStack.add(createAttribution());
		header.add(titleStack, BorderLayout.WEST);
		header.add(signalPanel, BorderLayout.CENTER);
		return header;
	}

	private Component createAttribution()
	{
		JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		panel.setOpaque(false);
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);
		TokenLabel line = new TokenLabel(
				"Faculty advisor Dr. Abdolreza Abhari  ·  Developer Mohsen Alavi");
		line.setFont(NebulaDesign.fontSmall());
		line.setToken(NebulaDesign.TEXT_TERTIARY);
		panel.add(line);
		return panel;
	}

	private Component createWorkspace()
	{
		JPanel left = createSidePanel();
		JPanel right = new JPanel(new BorderLayout(0, 10));
		right.setOpaque(false);
		right.add(metricStrip, BorderLayout.NORTH);
		dashboard.setOpaque(false);
		dashboard.setBorder(new EmptyBorder(0, 0, 0, 0));
		JScrollPane dashboardScroll = new JScrollPane(dashboard);
		dashboardScroll.setBorder(BorderFactory.createEmptyBorder());
		dashboardScroll.getViewport().setBackground(BACKGROUND);
		dashboardScroll.getVerticalScrollBar().setUnitIncrement(18);
		dashboardScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		right.add(dashboardScroll, BorderLayout.CENTER);

		JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, right);
		splitPane.setResizeWeight(0.16);
		splitPane.setDividerLocation(300);
		splitPane.setBorder(BorderFactory.createEmptyBorder());
		splitPane.setOpaque(false);
		return splitPane;
	}

	private JPanel createSidePanel()
	{
		JPanel panel = new JPanel(new BorderLayout(0, 10));
		panel.setOpaque(false);

		JPanel historyCard = card(new BorderLayout(0, 10));
		historyCard.add(sectionTitle("Completed Recommendations"), BorderLayout.NORTH);
		entryList.setCellRenderer(new EvidenceEntryRenderer());
		entryList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		entryList.addListSelectionListener(new ListSelectionListener()
		{
			public void valueChanged(ListSelectionEvent event)
			{
				if (!event.getValueIsAdjusting())
				{
					rebuildMatchList(entryList.getSelectedValue());
				}
			}
		});
		JScrollPane entryScroll = listWell(entryList);
		historyCard.add(entryScroll, BorderLayout.CENTER);

		JPanel matchCard = card(new BorderLayout(0, 10));
		matchCard.add(sectionTitle("Ranked Candidates"), BorderLayout.NORTH);
		matchList.setCellRenderer(new MatchRenderer());
		matchList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		matchList.addListSelectionListener(new ListSelectionListener()
		{
			public void valueChanged(ListSelectionEvent event)
			{
				if (!event.getValueIsAdjusting())
				{
					analyzeSelected();
				}
			}
		});
		JScrollPane matchScroll = listWell(matchList);
		matchScroll.setPreferredSize(new Dimension(280, 260));
		matchCard.add(matchScroll, BorderLayout.CENTER);

		panel.add(historyCard, BorderLayout.CENTER);
		panel.add(matchCard, BorderLayout.SOUTH);
		return panel;
	}

	private JPanel createFooter()
	{
		JPanel panel = new JPanel(new BorderLayout());
		panel.setOpaque(false);
		statusLabel.setFont(NebulaDesign.fontMedium(12f));
		statusLabel.setToken(MUTED);
		statusLabel.setBorder(new EmptyBorder(8, 12, 8, 12));
		panel.add(statusLabel, BorderLayout.CENTER);
		return panel;
	}

	private void rebuildMatchList(EvidenceEntry entry)
	{
		matchModel.clear();
		if (entry == null)
		{
			renderReport(RecommendationEvidenceEngine.EvidenceReport.empty(latestSnapshot));
			return;
		}
		for (int i = 0; i < entry.event.matches.size(); i++)
		{
			matchModel.addElement(new MatchRow(entry, i));
		}
		if (!matchModel.isEmpty())
		{
			matchList.setSelectedIndex(0);
		}
	}

	private void analyzeSelected()
	{
		final MatchRow row = matchList.getSelectedValue();
		if (row == null)
		{
			return;
		}
		final int request = ++requestSequence;
		statusLabel.setText("Building evidence for "
				+ row.entry.event.targetUser + " -> "
				+ row.entry.event.matches.get(row.matchIndex).name + "...");
		SwingWorker<RecommendationEvidenceEngine.EvidenceReport, Void> worker =
				new SwingWorker<RecommendationEvidenceEngine.EvidenceReport, Void>()
				{
					protected RecommendationEvidenceEngine.EvidenceReport doInBackground()
					{
						return RecommendationEvidenceEngine.analyze(
								row.entry.snapshot,
								row.entry.event,
								row.matchIndex);
					}

					protected void done()
					{
						if (request != requestSequence)
						{
							return;
						}
						try
						{
							currentReport = get();
							renderReport(currentReport);
							statusLabel.setText("Evidence ready: "
									+ currentReport.targetUser + " -> "
									+ currentReport.candidateName + ".");
						}
						catch (Exception exception)
						{
							statusLabel.setText("Evidence analysis failed: "
									+ exception.getMessage());
						}
					}
				};
		worker.execute();
	}

	private void renderReport(RecommendationEvidenceEngine.EvidenceReport report)
	{
		RecommendationEvidenceEngine.EvidenceReport safe =
				report == null
						? RecommendationEvidenceEngine.EvidenceReport.empty(latestSnapshot)
						: report;
		metricStrip.setReport(safe);
		flowPanel.setReport(safe);
		counterfactualPanel.setReport(safe);
		comparisonPanel.setReport(safe);
		targetRowsPanel.setRows(safe.targetRows, safe.targetRowCount, safe.targetUser);
		candidateRowsPanel.setRows(safe.candidateRows, safe.candidateRowCount, safe.candidateName);
		termEvidencePanel.setReport(safe);
		if (safe.termLocations.isEmpty())
		{
			termHighlightPanel.setTerm(null);
		}
		else
		{
			termHighlightPanel.setTerm(safe.termLocations.get(0));
		}
		graphImpactPanel.setReport(safe);
		verdictPanel.setReport(safe);

		dashboard.removeAll();
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.insets = new Insets(0, 0, 10, 0);
		gbc.fill = GridBagConstraints.BOTH;
		gbc.weightx = 1.0;
		gbc.gridx = 0;
		gbc.gridy = 0;
		gbc.gridwidth = 2;
		gbc.weighty = 0.0;
		dashboard.add(flowPanel, gbc);

		gbc.gridy++;
		gbc.gridwidth = 1;
		gbc.weightx = 0.54;
		gbc.insets = new Insets(0, 0, 10, 6);
		dashboard.add(counterfactualPanel, gbc);
		gbc.gridx = 1;
		gbc.weightx = 0.46;
		gbc.insets = new Insets(0, 6, 10, 0);
		dashboard.add(comparisonPanel, gbc);

		gbc.gridx = 0;
		gbc.gridy++;
		gbc.gridwidth = 1;
		gbc.weightx = 0.5;
		gbc.insets = new Insets(0, 0, 10, 6);
		dashboard.add(termEvidencePanel, gbc);
		gbc.gridx = 1;
		gbc.insets = new Insets(0, 6, 10, 0);
		dashboard.add(termHighlightPanel, gbc);

		gbc.gridx = 0;
		gbc.gridy++;
		gbc.gridwidth = 1;
		gbc.weightx = 0.5;
		gbc.insets = new Insets(0, 0, 10, 6);
		dashboard.add(graphImpactPanel, gbc);
		gbc.gridx = 1;
		gbc.insets = new Insets(0, 6, 10, 0);
		dashboard.add(verdictPanel, gbc);

		gbc.gridx = 0;
		gbc.gridy++;
		gbc.gridwidth = 1;
		gbc.weightx = 0.5;
		gbc.insets = new Insets(0, 0, 10, 6);
		dashboard.add(targetRowsPanel, gbc);
		gbc.gridx = 1;
		gbc.insets = new Insets(0, 6, 10, 0);
		dashboard.add(candidateRowsPanel, gbc);

		if (!safe.available)
		{
			gbc.gridx = 0;
			gbc.gridy++;
			gbc.gridwidth = 2;
			gbc.weightx = 1.0;
			gbc.insets = new Insets(0, 0, 0, 0);
			emptyStatePanel.setReport(safe);
			dashboard.add(emptyStatePanel, gbc);
		}
		JPanel filler = new JPanel();
		filler.setOpaque(false);
		gbc.gridx = 0;
		gbc.gridy++;
		gbc.gridwidth = 2;
		gbc.weightx = 1.0;
		gbc.weighty = 1.0;
		gbc.insets = new Insets(0, 0, 0, 0);
		dashboard.add(filler, gbc);
		dashboard.revalidate();
		dashboard.repaint();
	}

	private static JPanel card(java.awt.LayoutManager layout)
	{
		JPanel panel = new JPanel(layout)
		{
			protected void paintComponent(Graphics graphics)
			{
				Graphics2D g2 = (Graphics2D) graphics.create();
				NebulaWidgets.paintCard(g2, 0, 0, getWidth(), getHeight(),
						NebulaDesign.RADIUS_CARD);
				g2.dispose();
				super.paintComponent(graphics);
			}
		};
		panel.setOpaque(false);
		panel.setBackground(SURFACE);
		panel.setBorder(new EmptyBorder(14, 14, 16, 14));
		return panel;
	}

	private static JLabel sectionTitle(String text)
	{
		TokenLabel label = new TokenLabel(text);
		label.setFont(NebulaDesign.fontSemiBold(14f));
		label.setToken(TEXT);
		return label;
	}

	/**
	 * Rounded inset well hosting a list. The scroll pane paints the surface so
	 * the card behind it keeps its rounded corners in both modes.
	 */
	private static JScrollPane listWell(JList<?> list)
	{
		JScrollPane scroll = new JScrollPane(list)
		{
			protected void paintComponent(Graphics graphics)
			{
				Graphics2D g = (Graphics2D) graphics.create();
				prepare(g);
				g.setColor(SURFACE_ALT);
				g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1,
						NebulaDesign.RADIUS_CONTROL, NebulaDesign.RADIUS_CONTROL);
				g.setColor(BORDER);
				g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1,
						NebulaDesign.RADIUS_CONTROL, NebulaDesign.RADIUS_CONTROL);
				g.dispose();
				super.paintComponent(graphics);
			}
		};
		scroll.setOpaque(false);
		scroll.getViewport().setOpaque(false);
		scroll.setBorder(new EmptyBorder(1, 1, 1, 1));
		scroll.getVerticalScrollBar().setUnitIncrement(18);
		scroll.setHorizontalScrollBarPolicy(
				ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		list.setOpaque(false);
		return scroll;
	}

	/** True only for values that read as numerals, so mono stays for numbers. */
	private static boolean numericValue(String value)
	{
		if (value == null || value.length() == 0)
		{
			return false;
		}
		boolean digitSeen = false;
		for (int i = 0; i < value.length(); i++)
		{
			char symbol = value.charAt(i);
			if (Character.isDigit(symbol))
			{
				digitSeen = true;
				continue;
			}
			if (symbol == '.' || symbol == ',' || symbol == '%' || symbol == '/'
					|| symbol == ':' || symbol == '-' || symbol == '+'
					|| symbol == 'x' || symbol == ' ')
			{
				continue;
			}
			return false;
		}
		return digitSeen;
	}

	/**
	 * Label whose painted color stays a Nebula token even when an outer theme
	 * walk re-tints foregrounds, so day and night stay exactly as designed.
	 */
	private static final class TokenLabel extends JLabel
	{
		private Color token;

		TokenLabel(String text)
		{
			super(text);
		}

		void setToken(Color token)
		{
			this.token = token;
			repaint();
		}

		public Color getForeground()
		{
			return token == null ? super.getForeground() : token;
		}
	}

	/** List that paints a friendly hint instead of an empty rectangle. */
	private static final class PlaceholderList<T> extends JList<T>
	{
		private final String message;

		PlaceholderList(DefaultListModel<T> model, String message)
		{
			super(model);
			this.message = message;
		}

		protected void paintComponent(Graphics graphics)
		{
			super.paintComponent(graphics);
			if (getModel().getSize() > 0)
			{
				return;
			}
			Graphics2D g = (Graphics2D) graphics.create();
			prepare(g);
			g.setFont(NebulaDesign.fontBody(12f));
			g.setColor(NebulaDesign.TEXT_TERTIARY);
			drawWrapped(g, message, 16, 32,
					Math.max(60, getWidth() - 32), 4, 17);
			g.dispose();
		}
	}

	private static Font font(int style, int size)
	{
		return style == Font.BOLD
				? NebulaDesign.fontSemiBold(size)
				: NebulaDesign.fontBody(size);
	}

	private static void prepare(Graphics2D g)
	{
		NebulaDesign.prepare(g);
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
		int red = color.getRed() + Math.round((255 - color.getRed()) * amount);
		int green = color.getGreen() + Math.round((255 - color.getGreen()) * amount);
		int blue = color.getBlue() + Math.round((255 - color.getBlue()) * amount);
		return new Color(clamp(red), clamp(green), clamp(blue));
	}

	private static Color themed(Color day, Color night)
	{
		return StudioNightTheme.isNightMode() ? night : day;
	}

	private static Color glass(int alpha)
	{
		return themed(
				StudioNightTheme.withAlpha(NebulaDesign.DAY_SURFACE_ALT, alpha),
				StudioNightTheme.withAlpha(
						NebulaDesign.NIGHT_SURFACE_ALT,
						Math.min(255, alpha + 16)));
	}

	private static Color track()
	{
		return themed(NebulaDesign.DAY_INSET, NebulaDesign.NIGHT_INSET);
	}

	/** Soft chip tint for the supplied hue, resolved in the active mode. */
	private static Color softTint(Color color)
	{
		return new Color(
				color.getRed(),
				color.getGreen(),
				color.getBlue(),
				StudioNightTheme.isNightMode() ? 46 : 26);
	}

	private static int clamp(int value)
	{
		return Math.max(0, Math.min(255, value));
	}

	/** Paints a small soft-tint evidence chip and returns its painted width. */
	private static int drawChip(Graphics2D g, String text, int x, int y, Color hue)
	{
		Font previous = g.getFont();
		g.setFont(NebulaDesign.fontSemiBold(9.5f));
		FontMetrics metrics = g.getFontMetrics();
		int width = metrics.stringWidth(text) + 16;
		int height = metrics.getHeight() + 5;
		g.setColor(softTint(hue));
		g.fillRoundRect(x, y, width, height, height, height);
		g.setColor(hue);
		g.drawString(text, x + 8, y + 3 + metrics.getAscent());
		g.setFont(previous);
		return width;
	}

	private static void drawClipped(
			Graphics2D g, String value, int x, int y, int width)
	{
		String text = value == null ? "" : value;
		FontMetrics metrics = g.getFontMetrics();
		if (metrics.stringWidth(text) <= width)
		{
			g.drawString(text, x, y);
			return;
		}
		String ellipsis = "...";
		int max = Math.max(0, width - metrics.stringWidth(ellipsis));
		while (text.length() > 0 && metrics.stringWidth(text) > max)
		{
			text = text.substring(0, text.length() - 1);
		}
		g.drawString(text + ellipsis, x, y);
	}

	private static void drawWrapped(
			Graphics2D g,
			String text,
			int x,
			int y,
			int width,
			int maxLines,
			int lineHeight)
	{
		if (text == null)
		{
			return;
		}
		String[] words = text.split("\\s+");
		StringBuilder line = new StringBuilder();
		int currentY = y;
		int lines = 0;
		for (String word : words)
		{
			String candidate = line.length() == 0
					? word : line.toString() + " " + word;
			if (g.getFontMetrics().stringWidth(candidate) > width && line.length() > 0)
			{
				drawClipped(g, line.toString(), x, currentY, width);
				lines++;
				if (lines >= maxLines)
				{
					return;
				}
				currentY += lineHeight;
				line = new StringBuilder(word);
			}
			else
			{
				line = new StringBuilder(candidate);
			}
		}
		if (line.length() > 0 && lines < maxLines)
		{
			drawClipped(g, line.toString(), x, currentY, width);
		}
	}

	private static String shortLabel(String text)
	{
		if (text == null || text.trim().length() == 0)
		{
			return "-";
		}
		return text.trim();
	}

	/** GridBag dashboard that always fits the viewport width (no right clipping). */
	private static final class DashboardPanel extends JPanel
			implements javax.swing.Scrollable
	{
		DashboardPanel()
		{
			super(new GridBagLayout());
		}

		public Dimension getPreferredScrollableViewportSize()
		{
			return getPreferredSize();
		}

		public int getScrollableUnitIncrement(
				Rectangle visibleRect, int orientation, int direction)
		{
			return 18;
		}

		public int getScrollableBlockIncrement(
				Rectangle visibleRect, int orientation, int direction)
		{
			return Math.max(48, visibleRect.height - 36);
		}

		public boolean getScrollableTracksViewportWidth()
		{
			return true;
		}

		public boolean getScrollableTracksViewportHeight()
		{
			return false;
		}
	}

	private static final class EvidenceEntry
	{
		final SimulatorHelpPanel.Snapshot snapshot;
		final RecommendationEvidenceEngine.RecommendationEvent event;

		EvidenceEntry(
				SimulatorHelpPanel.Snapshot snapshot,
				RecommendationEvidenceEngine.RecommendationEvent event)
		{
			this.snapshot = snapshot;
			this.event = event;
		}

		public String toString()
		{
			return event.targetUser;
		}
	}

	private static final class MatchRow
	{
		final EvidenceEntry entry;
		final int matchIndex;

		MatchRow(EvidenceEntry entry, int matchIndex)
		{
			this.entry = entry;
			this.matchIndex = matchIndex;
		}
	}

	private static final class HeaderPanel extends JPanel
	{
		HeaderPanel()
		{
			setOpaque(false);
		}

		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D) graphics.create();
			prepare(g);
			NebulaWidgets.paintCard(g, 0, 0, getWidth(), getHeight(),
					NebulaDesign.RADIUS_CARD);
			Shape oldClip = g.getClip();
			g.clip(new RoundRectangle2D.Float(0, 0, getWidth() - 1, getHeight() - 3,
					NebulaDesign.RADIUS_CARD, NebulaDesign.RADIUS_CARD));
			boolean night = StudioNightTheme.isNightMode();
			int alpha = night ? 26 : 15;
			paintGlow(g, getWidth() / 5, 0, getHeight() * 2, BLUE, alpha);
			paintGlow(g, getWidth() * 4 / 5, getHeight(), getHeight() * 2,
					PURPLE, Math.max(6, alpha - 4));
			g.setClip(oldClip);
			g.dispose();
			super.paintComponent(graphics);
		}
	}

	private static final class EvidenceSignalPanel extends JPanel
	{
		private double phase;

		EvidenceSignalPanel()
		{
			setOpaque(false);
			setPreferredSize(new Dimension(420, 80));
		}

		void tick()
		{
			phase += 0.035;
			repaint();
		}

		protected void paintComponent(Graphics graphics)
		{
			super.paintComponent(graphics);
			Graphics2D g = (Graphics2D) graphics.create();
			prepare(g);
			int w = getWidth();
			int h = getHeight();
			int y = h / 2;
			int[] xs = { 20, w / 4, w / 2, w * 3 / 4, w - 24 };
			Color[] colors = { BLUE, PURPLE, AMBER, GREEN, BLUE };
			g.setStroke(new BasicStroke(2f));
			for (int i = 0; i < xs.length - 1; i++)
			{
				g.setColor(new Color(colors[i].getRed(), colors[i].getGreen(),
						colors[i].getBlue(), 76));
				g.drawLine(xs[i], y + (i % 2 == 0 ? -5 : 5),
						xs[i + 1], y + (i % 2 == 0 ? 5 : -5));
				double p = (phase + i * 0.23) % 1.0;
				int dotX = xs[i] + (int) ((xs[i + 1] - xs[i]) * p);
				int dotY = (int) ((y + (i % 2 == 0 ? -5 : 5))
						+ ((i % 2 == 0 ? 10 : -10) * p));
				g.setColor(new Color(colors[i].getRed(), colors[i].getGreen(),
						colors[i].getBlue(), 78));
				g.fillOval(dotX - 10, dotY - 10, 20, 20);
				g.setColor(colors[i]);
				g.fillOval(dotX - 4, dotY - 4, 8, 8);
			}
			for (int i = 0; i < xs.length; i++)
			{
				g.setColor(tint(colors[i], 0.82f));
				g.fillOval(xs[i] - 8, y - 8, 16, 16);
				g.setColor(colors[i]);
				g.setStroke(new BasicStroke(2f));
				g.drawOval(xs[i] - 8, y - 8, 16, 16);
			}
			g.dispose();
		}
	}

	private static final class MetricStripPanel extends JPanel
	{
		private RecommendationEvidenceEngine.EvidenceReport report =
				RecommendationEvidenceEngine.EvidenceReport.empty(
						SimulatorHelpPanel.Snapshot.empty());

		MetricStripPanel()
		{
			setOpaque(false);
			setPreferredSize(new Dimension(800, 104));
		}

		void setReport(RecommendationEvidenceEngine.EvidenceReport report)
		{
			this.report = report;
			revalidate();
			repaint();
		}

		public Dimension getPreferredSize()
		{
			int width = getWidth();
			return new Dimension(800, width > 0 && width < 760 ? 158 : 104);
		}

		protected void paintComponent(Graphics graphics)
		{
			super.paintComponent(graphics);
			Graphics2D g = (Graphics2D) graphics.create();
			prepare(g);
			int w = getWidth();
			int columns = w < 760 ? 2 : 4;
			int gap = 10;
			int cardW = Math.max(120, (w - gap * (columns - 1)) / columns);
			int cardH = columns == 2 ? 62 : 72;
			String[] labels = { "Algorithm", "Target", "Recommendation", "Evidence" };
			String[] values = {
					report.algorithmName,
					report.targetUser,
					report.candidateName,
					report.sharedTerms.size() + " shared terms"
			};
			String[] details = {
					report.modeLabel,
					"selected user/entity",
					"rank " + report.rank,
					report.graphAvailable ? "run graph evidence" : "dataset evidence"
			};
			Color[] colors = { BLUE, PURPLE, GREEN, AMBER };
			for (int i = 0; i < labels.length; i++)
			{
				int row = i / columns;
				int col = i % columns;
				drawMetric(g, col * (cardW + gap), 8 + row * (cardH + 10),
						cardW, cardH, labels[i], values[i], details[i], colors[i]);
			}
			g.dispose();
		}

		private void drawMetric(
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
			NebulaWidgets.paintCard(g, x, y, w, h, RADIUS);
			drawChip(g, label.toUpperCase(Locale.US), x + 12, y + 8, color);
			boolean numeric = numericValue(value);
			g.setFont(numeric
					? NebulaDesign.fontMono(15f)
					: NebulaDesign.fontSemiBold(16f));
			g.setColor(TEXT);
			drawClipped(g, shortLabel(value), x + 14, y + 44, w - 28);
			g.setFont(NebulaDesign.fontMedium(10f));
			g.setColor(NebulaDesign.TEXT_TERTIARY);
			drawClipped(g, detail, x + 14, y + h - 12, w - 28);
		}
	}

	private static final class EvidenceFlowPanel extends JPanel
	{
		private RecommendationEvidenceEngine.EvidenceReport report =
				RecommendationEvidenceEngine.EvidenceReport.empty(
						SimulatorHelpPanel.Snapshot.empty());
		private double phase;

		EvidenceFlowPanel()
		{
			setOpaque(false);
			setPreferredSize(new Dimension(900, 420));
			setMinimumSize(new Dimension(520, 360));
			setBorder(new EmptyBorder(10, 10, 12, 10));
		}

		void setReport(RecommendationEvidenceEngine.EvidenceReport report)
		{
			this.report = report;
			revalidate();
			repaint();
		}

		public Dimension getPreferredSize()
		{
			int width = getWidth();
			return new Dimension(900, width > 0 && width < 980 ? 430 : 360);
		}

		void tick()
		{
			phase += 0.045;
			repaint();
		}

		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D) graphics.create();
			prepare(g);
			NebulaWidgets.paintCard(g, 0, 0, getWidth(), getHeight(),
					NebulaDesign.RADIUS_CARD);
			g.setFont(NebulaDesign.fontTitle());
			g.setColor(TEXT);
			g.drawString("Evidence Trail", 18, 32);
			g.setFont(font(Font.PLAIN, 11));
			g.setColor(MUTED);
			drawClipped(g, report.evidenceQuestion, 18, 52, getWidth() - 36);

			int margin = 18;
			int y = 86;
			int gap = 14;
			int usable = Math.max(1, getWidth() - margin * 2);
			int compact = getWidth() < 980 ? 1 : 0;
			int mainCards = compact == 1 ? 2 : 4;
			int cardW = Math.max(170, (usable - gap * (mainCards - 1)) / mainCards);
			int cardH = 104;
			if (compact == 1)
			{
				drawFlowCard(g, margin, y, cardW, cardH, "1 Target",
						report.targetUser, "source rows " + report.targetRowCount, BLUE);
				drawFlowCard(g, margin + cardW + gap, y, cardW, cardH, "2 Text signal",
						primaryTerm(), report.sharedTerms.size() + " shared cues", PURPLE);
				drawConnector(g, margin + cardW, y + cardH / 2,
						margin + cardW + gap, y + cardH / 2, BLUE, 0);
				int y2 = y + cardH + gap;
				drawFlowCard(g, margin, y2, cardW, cardH, "3 Model",
						report.algorithmName, report.algorithmLensTitle, AMBER);
				drawFlowCard(g, margin + cardW + gap, y2, cardW, cardH,
						"4 Ranked output", report.candidateName, report.scoreLabel, GREEN);
				drawConnector(g, margin + cardW, y2 + cardH / 2,
						margin + cardW + gap, y2 + cardH / 2, GREEN, 2);
				drawGraphBranch(g, margin, y2 + cardH + 16, usable,
						getHeight() - (y2 + cardH + 32));
			}
			else
			{
				int x1 = margin;
				int x2 = x1 + cardW + gap;
				int x3 = x2 + cardW + gap;
				int x4 = x3 + cardW + gap;
				drawFlowCard(g, x1, y, cardW, cardH, "1 Target",
						report.targetUser, "source rows " + report.targetRowCount, BLUE);
				drawFlowCard(g, x2, y, cardW, cardH, "2 Text signal",
						primaryTerm(), report.sharedTerms.size() + " shared cues", PURPLE);
				drawFlowCard(g, x3, y, cardW, cardH, "3 Model",
						report.algorithmName, report.algorithmLensTitle, AMBER);
				drawFlowCard(g, x4, y, cardW, cardH, "4 Ranked output",
						report.candidateName, report.scoreLabel, GREEN);
				drawConnector(g, x1 + cardW, y + cardH / 2, x2, y + cardH / 2, BLUE, 0);
				drawConnector(g, x2 + cardW, y + cardH / 2, x3, y + cardH / 2, PURPLE, 1);
				drawConnector(g, x3 + cardW, y + cardH / 2, x4, y + cardH / 2, GREEN, 2);
				drawGraphBranch(g, margin, y + cardH + 20, usable,
						getHeight() - (y + cardH + 38));
			}
			g.dispose();
		}

		private String primaryTerm()
		{
			return report.sharedTerms.isEmpty()
					? "column 6 text"
					: report.sharedTerms.get(0).term;
		}

		private void drawConnector(
				Graphics2D g,
				int x1,
				int y1,
				int x2,
				int y2,
				Color color,
				int index)
		{
			g.setStroke(new BasicStroke(2f));
			g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 72));
			g.drawLine(x1 + 4, y1, x2 - 4, y2);
			int distance = Math.max(1, x2 - x1 - 18);
			double p = (phase * 0.85 + index * 0.28) % 1.0;
			int dotX = x1 + 9 + (int) (distance * p);
			g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 60));
			g.fillOval(dotX - 10, y1 - 10, 20, 20);
			g.setColor(color);
			g.fillOval(dotX - 5, y1 - 5, 10, 10);
		}

		private void drawGraphBranch(
				Graphics2D g,
				int x,
				int y,
				int w,
				int h)
		{
			int panelH = Math.max(86, h);
			g.setColor(glass(178));
			g.fillRoundRect(x, y, w, panelH, RADIUS, RADIUS);
			g.setColor(BORDER);
			g.drawRoundRect(x, y, w, panelH, RADIUS, RADIUS);
			g.setFont(NebulaDesign.fontSection());
			g.setColor(BLUE);
			g.drawString("IN THE RUN GRAPH", x + 14, y + 20);
			g.setFont(NebulaDesign.fontMedium(12.5f));
			g.setColor(TEXT);
			drawClipped(g, report.graphAvailable
					? "Follows " + report.lastOriginalEdge + "; adopted "
							+ report.lastRecommendedEdge
					: "The run graph appears when the run's record is written.",
					x + 14, y + 43, w - 28);
			g.setFont(font(Font.PLAIN, 11));
			g.setColor(MUTED);
			drawWrapped(g, report.plainVerdict, x + 14, y + 66,
					w - 28, Math.max(1, (panelH - 76) / 15), 15);
		}

		private void drawFlowCard(
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
			g.setColor(tint(color, 0.92f));
			g.fillRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 105));
			g.drawRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setColor(tint(color, 0.30f));
			g.fillRoundRect(x, y + 10, 3, h - 20, 3, 3);
			drawChip(g, label.toUpperCase(Locale.US), x + 12, y + 9, color);
			g.setFont(NebulaDesign.fontSemiBold(15f));
			g.setColor(TEXT);
			drawClipped(g, value, x + 13, y + 50, w - 26);
			g.setFont(font(Font.PLAIN, 10));
			g.setColor(MUTED);
			drawWrapped(g, detail, x + 13, y + 72, w - 26, 2, 14);
		}
	}

	private static final class CounterfactualPanel extends JPanel
	{
		private RecommendationEvidenceEngine.EvidenceReport report =
				RecommendationEvidenceEngine.EvidenceReport.empty(
						SimulatorHelpPanel.Snapshot.empty());

		CounterfactualPanel()
		{
			setOpaque(false);
			setPreferredSize(new Dimension(520, 386));
			setMinimumSize(new Dimension(420, 340));
			setBorder(new EmptyBorder(10, 10, 12, 10));
		}

		void setReport(RecommendationEvidenceEngine.EvidenceReport report)
		{
			this.report = report;
			repaint();
		}

		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D) graphics.create();
			prepare(g);
			NebulaWidgets.paintCard(g, 0, 0, getWidth(), getHeight(),
					NebulaDesign.RADIUS_CARD);
			g.setFont(NebulaDesign.fontTitle());
			g.setColor(TEXT);
			g.drawString("Counterfactual Evidence Mode", 16, 28);
			g.setFont(font(Font.PLAIN, 11));
			g.setColor(MUTED);
			drawClipped(g,
					"What would need to change for this recommendation to move?",
					16, 47, getWidth() - 32);

			drawSummary(g, 16, 62, getWidth() - 32, 62);
			int y = 138;
			if (report.counterfactualInsights.isEmpty())
			{
				g.setFont(font(Font.BOLD, 13));
				g.setColor(MUTED);
				drawWrapped(g,
						"Run a recommendation to see term-removal and row-removal stress tests.",
						18, y + 18, getWidth() - 36, 3, 16);
				g.dispose();
				return;
			}
			int count = Math.max(1, Math.min(4, report.counterfactualInsights.size()));
			int gap = 8;
			int available = Math.max(44, getHeight() - y - 18);
			int cardH = Math.max(38, Math.min(58,
					(available - gap * (count - 1)) / count));
			for (int i = 0; i < count; i++)
			{
				RecommendationEvidenceEngine.CounterfactualInsight insight =
						report.counterfactualInsights.get(i);
				drawInsight(g, 16, y + i * (cardH + gap),
						getWidth() - 32, cardH, insight);
			}
			g.dispose();
		}

		private void drawSummary(Graphics2D g, int x, int y, int w, int h)
		{
			g.setColor(SURFACE_ALT);
			g.fillRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setColor(BORDER);
			g.drawRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setFont(NebulaDesign.fontSection());
			g.setColor(PURPLE);
			g.drawString("ROBUSTNESS READ", x + 12, y + 18);
			g.setFont(NebulaDesign.fontMedium(12.5f));
			g.setColor(TEXT);
			drawWrapped(g, report.counterfactualSummary,
					x + 12, y + 37, w - 24, 2, 16);
		}

		private void drawInsight(
				Graphics2D g,
				int x,
				int y,
				int w,
				int h,
				RecommendationEvidenceEngine.CounterfactualInsight insight)
		{
			Color color = insight.likelySwitch ? RED
					: (insight.pressurePercent >= 35 ? AMBER : GREEN);
			g.setColor(tint(color, 0.94f));
			g.fillRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 95));
			g.drawRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setColor(tint(color, 0.30f));
			g.fillRoundRect(x, y + 8, 3, h - 16, 3, 3);
			g.setFont(NebulaDesign.fontMedium(11.5f));
			g.setColor(TEXT);
			drawClipped(g, insight.title, x + 14, y + 17, w - 126);
			g.setFont(font(Font.PLAIN, 10));
			g.setColor(MUTED);
			drawClipped(g, insight.action, x + 14, y + 33, w - 126);
			if (h > 48)
			{
				drawClipped(g, insight.outcome, x + 14, y + 49, w - 126);
			}
			g.setColor(track());
			g.fillRoundRect(x + w - 98, y + h / 2 - 11, 78, 9, 9, 9);
			g.setColor(color);
			g.fillRoundRect(x + w - 98, y + h / 2 - 11,
					Math.max(6, 78 * insight.pressurePercent / 100), 9, 9, 9);
			g.setFont(NebulaDesign.fontMono(9.5f));
			g.setColor(color);
			drawClipped(g, insight.pressurePercent + "% pressure",
					x + w - 100, y + h / 2 + 15, 84);
		}
	}

	private static final class ComparisonPanel extends JPanel
	{
		private RecommendationEvidenceEngine.EvidenceReport report =
				RecommendationEvidenceEngine.EvidenceReport.empty(
						SimulatorHelpPanel.Snapshot.empty());

		ComparisonPanel()
		{
			setOpaque(false);
			setPreferredSize(new Dimension(460, 330));
			setMinimumSize(new Dimension(380, 300));
			setBorder(new EmptyBorder(10, 10, 12, 10));
		}

		void setReport(RecommendationEvidenceEngine.EvidenceReport report)
		{
			this.report = report;
			repaint();
		}

		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D) graphics.create();
			prepare(g);
			NebulaWidgets.paintCard(g, 0, 0, getWidth(), getHeight(),
					NebulaDesign.RADIUS_CARD);
			g.setFont(NebulaDesign.fontTitle());
			g.setColor(TEXT);
			g.drawString("Why This, Not That?", 16, 28);
			g.setFont(font(Font.PLAIN, 11));
			g.setColor(MUTED);
			drawClipped(g,
					"Compare the selected rank against the strongest alternate.",
					16, 47, getWidth() - 32);
			if (!report.comparison.available)
			{
				g.setFont(font(Font.BOLD, 13));
				g.setColor(MUTED);
				drawWrapped(g, report.comparison.verdict,
						18, 88, getWidth() - 36, 4, 17);
				g.dispose();
				return;
			}
			int mid = getWidth() / 2;
			drawCandidate(g, 16, 70, mid - 24, 82,
					report.comparison.left, report.comparison.leftSupport, GREEN);
			drawCandidate(g, mid + 8, 70, mid - 24, 82,
					report.comparison.right, report.comparison.rightSupport, PURPLE);
			drawSupport(g, 16, 168, getWidth() - 32, 56);
			drawTermLane(g, 16, 240, getWidth() / 2 - 26,
					"winner shared cues", report.comparison.leftSharedTerms, GREEN);
			drawTermLane(g, getWidth() / 2 + 10, 240, getWidth() / 2 - 26,
					"alternate shared cues", report.comparison.rightSharedTerms, PURPLE);
			g.dispose();
		}

		private void drawCandidate(
				Graphics2D g,
				int x,
				int y,
				int w,
				int h,
				RecommendationEvidenceEngine.MatchEvidence match,
				int support,
				Color color)
		{
			g.setColor(tint(color, 0.92f));
			g.fillRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 105));
			g.drawRoundRect(x, y, w, h, RADIUS, RADIUS);
			drawChip(g, "RANK " + match.rank, x + 10, y + 8, color);
			g.setFont(NebulaDesign.fontSemiBold(15f));
			g.setColor(TEXT);
			drawClipped(g, match.name, x + 12, y + 47, w - 24);
			g.setFont(NebulaDesign.fontMono(9.5f));
			g.setColor(MUTED);
			drawClipped(g, "score " + (match.score.length() == 0 ? "-" : match.score)
					+ " | text support " + support, x + 12, y + 65, w - 24);
		}

		private void drawSupport(Graphics2D g, int x, int y, int w, int h)
		{
			int total = Math.max(1,
					report.comparison.leftSupport + report.comparison.rightSupport);
			int leftW = Math.max(8, w * report.comparison.leftSupport / total);
			g.setFont(NebulaDesign.fontMedium(11.5f));
			g.setColor(TEXT);
			drawClipped(g, "Support gap  ·  score gap " + report.comparison.scoreGap,
					x, y + 11, w);
			int barY = y + 20;
			g.setColor(track());
			g.fillRoundRect(x, barY, w, 10, 10, 10);
			g.setColor(GREEN);
			g.fillRoundRect(x, barY, leftW, 10, 10, 10);
			g.setColor(PURPLE);
			g.fillRoundRect(x + leftW + 2, barY,
					Math.max(6, w - leftW - 2), 10, 10, 10);
			g.setFont(NebulaDesign.fontBody(10.5f));
			g.setColor(MUTED);
			drawClipped(g, report.comparison.verdict, x, barY + 26, w);
		}

		private void drawTermLane(
				Graphics2D g,
				int x,
				int y,
				int w,
				String label,
				List<RecommendationEvidenceEngine.TermScore> terms,
				Color color)
		{
			g.setColor(SURFACE_ALT);
			g.fillRoundRect(x, y, w, 70, RADIUS, RADIUS);
			g.setColor(BORDER);
			g.drawRoundRect(x, y, w, 70, RADIUS, RADIUS);
			g.setFont(NebulaDesign.fontSection());
			g.setColor(color);
			drawClipped(g, label.toUpperCase(Locale.US), x + 10, y + 17, w - 20);
			int chipX = x + 10;
			int chipY = y + 30;
			for (int i = 0; i < terms.size() && i < 4; i++)
			{
				String term = terms.get(i).term;
				g.setFont(NebulaDesign.fontMedium(10f));
				int chipW = Math.min(118, Math.max(46,
						g.getFontMetrics().stringWidth(term) + 22));
				if (chipX + chipW > x + w - 8)
				{
					break;
				}
				g.setColor(softTint(color));
				g.fillRoundRect(chipX, chipY, chipW, 23, 23, 23);
				g.setColor(color);
				drawClipped(g, term, chipX + 11, chipY + 15, chipW - 22);
				chipX += chipW + 6;
			}
		}
	}

	private static final class SourceRowsPanel extends JPanel
	{
		private final String title;
		private final Color color;
		private final boolean target;
		private List<RecommendationEvidenceEngine.DsmpRow> rows =
				new ArrayList<RecommendationEvidenceEngine.DsmpRow>();
		private int totalRows;
		private String entity = "";

		SourceRowsPanel(String title, Color color, boolean target)
		{
			this.title = title;
			this.color = color;
			this.target = target;
			setOpaque(false);
			setPreferredSize(new Dimension(420, 360));
			setMinimumSize(new Dimension(320, 360));
			setBorder(new EmptyBorder(10, 10, 12, 10));
		}

		void setRows(
				List<RecommendationEvidenceEngine.DsmpRow> rows,
				int totalRows,
				String entity)
		{
			this.rows = rows == null
					? new ArrayList<RecommendationEvidenceEngine.DsmpRow>()
					: new ArrayList<RecommendationEvidenceEngine.DsmpRow>(rows);
			this.totalRows = totalRows;
			this.entity = entity == null ? "" : entity;
			int visibleRows = Math.min(10, this.rows.size());
			int dynamicHeight = 110 + Math.max(3, visibleRows) * 50;
			setPreferredSize(new Dimension(420, Math.min(640, dynamicHeight)));
			setMinimumSize(new Dimension(320, Math.min(640, dynamicHeight)));
			revalidate();
			repaint();
		}

		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D) graphics.create();
			prepare(g);
			NebulaWidgets.paintCard(g, 0, 0, getWidth(), getHeight(),
					NebulaDesign.RADIUS_CARD);
			g.setFont(NebulaDesign.fontTitle());
			g.setColor(TEXT);
			g.drawString(title, 16, 27);
			g.setFont(NebulaDesign.fontMedium(10.5f));
			g.setColor(color);
			drawClipped(g, entity + " | " + totalRows + " matching rows",
					16, 46, getWidth() - 32);
			int y = 66;
			if (rows.isEmpty())
			{
				g.setFont(NebulaDesign.fontMedium(12.5f));
				g.setColor(MUTED);
				drawWrapped(g,
						target
								? "No source rows were found for this target in the inspected dataset sample."
								: "No direct source rows were found for this recommendation in the inspected dataset sample.",
						18, y + 24, getWidth() - 36, 3, 16);
				g.dispose();
				return;
			}
			int available = Math.max(44, getHeight() - y - 40);
			int maxVisible = Math.max(1, Math.min(rows.size(), available / 46));
			int rowH = Math.max(42, Math.min(54, available / Math.max(1, maxVisible)));
			for (int i = 0; i < maxVisible; i++)
			{
				RecommendationEvidenceEngine.DsmpRow row = rows.get(i);
				int rowY = y + i * rowH;
				g.setColor(i % 2 == 0 ? SURFACE_ALT : SURFACE);
				g.fillRoundRect(14, rowY, getWidth() - 28, rowH - 5, 8, 8);
				g.setColor(tint(color, 0.42f));
				g.fillRoundRect(14, rowY + 4, 3, rowH - 13, 3, 3);
				g.setFont(NebulaDesign.fontMedium(11f));
				g.setColor(TEXT);
				drawClipped(g, row.followee + "  |  " + row.user, 26, rowY + 17,
						getWidth() - 170);
				g.setFont(NebulaDesign.fontMono(9.5f));
				g.setColor(NebulaDesign.TEXT_TERTIARY);
				drawClipped(g, row.date, getWidth() - 120, rowY + 17, 100);
				g.setFont(font(Font.PLAIN, 11));
				g.setColor(MUTED);
				drawClipped(g, row.text, 26, rowY + 36, getWidth() - 52);
			}
			if (rows.size() > maxVisible)
			{
				g.setFont(NebulaDesign.fontMedium(10f));
				g.setColor(color);
				drawClipped(g, "+" + (rows.size() - maxVisible)
						+ " more inspected rows are available in this evidence sample.",
						18, getHeight() - 18, getWidth() - 36);
			}
			g.dispose();
		}
	}

	private interface TermSelectionListener
	{
		void termSelected(RecommendationEvidenceEngine.TermLocation location);
	}

	private static final class TermEvidencePanel extends JPanel
	{
		private RecommendationEvidenceEngine.EvidenceReport report =
				RecommendationEvidenceEngine.EvidenceReport.empty(
						SimulatorHelpPanel.Snapshot.empty());
		private final List<TermHit> termHits = new ArrayList<TermHit>();
		private TermSelectionListener selectionListener;
		private String selectedTerm = "";

		TermEvidencePanel()
		{
			setOpaque(false);
			setPreferredSize(new Dimension(900, 360));
			setMinimumSize(new Dimension(380, 360));
			setBorder(new EmptyBorder(10, 10, 12, 10));
			addMouseListener(new MouseAdapter()
			{
				public void mouseClicked(MouseEvent event)
				{
					for (int i = 0; i < termHits.size(); i++)
					{
						TermHit hit = termHits.get(i);
						if (hit.bounds.contains(event.getPoint()))
						{
							selectedTerm = hit.location.term;
							if (selectionListener != null)
							{
								selectionListener.termSelected(hit.location);
							}
							repaint();
							return;
						}
					}
				}
			});
		}

		void setSelectionListener(TermSelectionListener selectionListener)
		{
			this.selectionListener = selectionListener;
		}

		void setReport(RecommendationEvidenceEngine.EvidenceReport report)
		{
			this.report = report;
			if (selectedTerm.length() == 0 || findLocation(selectedTerm) == null)
			{
				selectedTerm = report.termLocations.isEmpty()
						? "" : report.termLocations.get(0).term;
			}
			revalidate();
			repaint();
		}

		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D) graphics.create();
			prepare(g);
			termHits.clear();
			NebulaWidgets.paintCard(g, 0, 0, getWidth(), getHeight(),
					NebulaDesign.RADIUS_CARD);
			g.setFont(NebulaDesign.fontTitle());
			g.setColor(TEXT);
			g.drawString("Interactive Text Evidence", 16, 28);
			g.setFont(font(Font.PLAIN, 11));
			g.setColor(MUTED);
			drawClipped(g,
					"Click a term to highlight supporting rows and its counterfactual pressure.",
					16, 47, getWidth() - 32);

			boolean stacked = getWidth() < 760;
			if (stacked)
			{
				int sectionH = Math.max(96, (getHeight() - 100) / 3);
				drawTerms(g, 16, 70, getWidth() - 32, sectionH, "Shared terms",
						report.sharedTerms, GREEN, true);
				drawTerms(g, 16, 78 + sectionH, getWidth() - 32, sectionH,
						"Target signal", report.targetTerms, BLUE, false);
				drawTerms(g, 16, 86 + sectionH * 2, getWidth() - 32, sectionH,
						"Candidate signal", report.candidateTerms, PURPLE, false);
			}
			else
			{
				int third = Math.max(180, (getWidth() - 48) / 3);
				drawTerms(g, 16, 70, third, getHeight() - 88, "Shared terms",
						report.sharedTerms, GREEN, true);
				drawTerms(g, 16 + third + 8, 70, third, getHeight() - 88,
						"Target signal", report.targetTerms, BLUE, false);
				drawTerms(g, 16 + (third + 8) * 2, 70, third, getHeight() - 88,
						"Candidate signal", report.candidateTerms, PURPLE, false);
			}
			g.dispose();
		}

		private void drawTerms(
				Graphics2D g,
				int x,
				int y,
				int w,
				int h,
				String title,
				List<RecommendationEvidenceEngine.TermScore> terms,
				Color color,
				boolean shared)
		{
			g.setColor(SURFACE_ALT);
			g.fillRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setColor(BORDER);
			g.drawRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setFont(NebulaDesign.fontSection());
			g.setColor(color);
			g.drawString(title.toUpperCase(Locale.US), x + 12, y + 18);
			if (terms.isEmpty())
			{
				g.setFont(font(Font.PLAIN, 11));
				g.setColor(MUTED);
				drawWrapped(g, "No readable term evidence found in the inspected rows.",
						x + 12, y + 42, w - 24, 3, 15);
				return;
			}
			int max = Math.max(1, terms.get(0).value);
			int rowY = y + 34;
			int maxRows = Math.max(1, Math.min(8, (h - 42) / 19));
			for (int i = 0; i < terms.size() && i < maxRows; i++)
			{
				RecommendationEvidenceEngine.TermScore term = terms.get(i);
				RecommendationEvidenceEngine.TermLocation location =
						findLocation(term.term);
				int barW = Math.max(12, (w - 96) * term.value / max);
				Rectangle hitBounds = new Rectangle(x + 8, rowY - 4, w - 16, 20);
				boolean selected = selectedTerm.equals(term.term);
				if (location != null)
				{
					termHits.add(new TermHit(hitBounds, location));
				}
				if (selected)
				{
					g.setColor(softTint(color));
					g.fillRoundRect(hitBounds.x, hitBounds.y,
							hitBounds.width, hitBounds.height, 8, 8);
					g.setColor(NebulaDesign.withAlpha(color, 120));
					g.drawRoundRect(hitBounds.x, hitBounds.y,
							hitBounds.width, hitBounds.height, 8, 8);
				}
				g.setFont(NebulaDesign.fontMedium(11f));
				g.setColor(selected ? color : TEXT);
				drawClipped(g, term.term, x + 12, rowY + 10, 76);
				int trackW = Math.max(24, w - 94 - 74);
				int fillW = Math.max(8, trackW * term.value / max);
				g.setColor(track());
				g.fillRoundRect(x + 94, rowY, trackW, 12, 6, 6);
				g.setColor(color);
				g.fillRoundRect(x + 94, rowY, fillW, 12, 6, 6);
				g.setFont(NebulaDesign.fontMono(9f));
				g.setColor(MUTED);
				String value = shared
						? "overlap " + term.value
						: Integer.toString(term.value);
				drawClipped(g, value, x + 94 + trackW + 8, rowY + 10, 62);
				rowY += 19;
			}
		}

		private RecommendationEvidenceEngine.TermLocation findLocation(String term)
		{
			if (term == null)
			{
				return null;
			}
			for (int i = 0; i < report.termLocations.size(); i++)
			{
				RecommendationEvidenceEngine.TermLocation location =
						report.termLocations.get(i);
				if (term.equals(location.term))
				{
					return location;
				}
			}
			return null;
		}

		private static final class TermHit
		{
			final Rectangle bounds;
			final RecommendationEvidenceEngine.TermLocation location;

			TermHit(
					Rectangle bounds,
					RecommendationEvidenceEngine.TermLocation location)
			{
				this.bounds = bounds;
				this.location = location;
			}
		}
	}

	private static final class TermHighlightPanel extends JPanel
	{
		private RecommendationEvidenceEngine.TermLocation location;

		TermHighlightPanel()
		{
			setOpaque(false);
			setPreferredSize(new Dimension(420, 360));
			setMinimumSize(new Dimension(320, 360));
			setBorder(new EmptyBorder(10, 10, 12, 10));
		}

		void setTerm(RecommendationEvidenceEngine.TermLocation location)
		{
			this.location = location;
			int rowCount = location == null ? 3
					: Math.max(location.targetRows.size(), location.candidateRows.size());
			int dynamicHeight = 180 + Math.max(3, Math.min(6, rowCount)) * 46;
			setPreferredSize(new Dimension(420, Math.min(460, dynamicHeight)));
			setMinimumSize(new Dimension(320, Math.min(460, dynamicHeight)));
			revalidate();
			repaint();
		}

		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D) graphics.create();
			prepare(g);
			NebulaWidgets.paintCard(g, 0, 0, getWidth(), getHeight(),
					NebulaDesign.RADIUS_CARD);
			g.setFont(NebulaDesign.fontTitle());
			g.setColor(TEXT);
			g.drawString("Term Impact Lens", 16, 28);
			if (location == null)
			{
				g.setFont(font(Font.PLAIN, 12));
				g.setColor(MUTED);
				drawWrapped(g,
						"Click any term in the Text Evidence panel to see exactly where it appears.",
						18, 58, getWidth() - 36, 4, 16);
				g.dispose();
				return;
			}

			Color roleColor = roleColor(location.role);
			drawTermHeader(g, 16, 46, getWidth() - 32, 60, roleColor);
			if (getWidth() < 620)
			{
				int columnH = Math.max(96, (getHeight() - 148) / 2);
				drawRowColumn(g, 16, 122, getWidth() - 32, columnH,
						"Target rows", location.targetRows, location.term, BLUE);
				drawRowColumn(g, 16, 132 + columnH, getWidth() - 32, columnH,
						"Candidate rows", location.candidateRows, location.term, GREEN);
			}
			else
			{
				int columnW = (getWidth() - 42) / 2;
				drawRowColumn(g, 16, 122, columnW, getHeight() - 140,
						"Target rows", location.targetRows, location.term, BLUE);
				drawRowColumn(g, 26 + columnW, 122, columnW, getHeight() - 140,
						"Candidate rows", location.candidateRows, location.term, GREEN);
			}
			g.dispose();
		}

		private void drawTermHeader(
				Graphics2D g,
				int x,
				int y,
				int w,
				int h,
				Color color)
		{
			g.setColor(tint(color, 0.92f));
			g.fillRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 105));
			g.drawRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setColor(tint(color, 0.30f));
			g.fillRoundRect(x, y + 8, 3, h - 16, 3, 3);
			drawChip(g, location.role.toUpperCase(Locale.US), x + 12, y + 8, color);
			g.setFont(NebulaDesign.fontSemiBold(16f));
			g.setColor(TEXT);
			drawClipped(g, location.term, x + 16, y + 46, Math.max(80, w / 3));
			int infoX = x + Math.max(120, w / 3);
			int meterX = x + w - 112;
			g.setFont(NebulaDesign.fontMono(9.5f));
			g.setColor(MUTED);
			drawClipped(g,
					"target " + location.targetCount + " | candidate "
							+ location.candidateCount,
					infoX, y + 24, Math.max(40, meterX - infoX - 12));
			g.setFont(font(Font.PLAIN, 10));
			drawClipped(g, location.counterfactualEffect,
					infoX, y + 43, Math.max(40, meterX - infoX - 12));
			g.setColor(track());
			g.fillRoundRect(meterX, y + 18, 82, 9, 8, 8);
			g.setColor(color);
			g.fillRoundRect(meterX, y + 18,
					Math.max(5, 82 * location.pressurePercent / 100), 9, 8, 8);
			g.setFont(NebulaDesign.fontMono(9f));
			g.setColor(color);
			drawClipped(g, location.pressurePercent + "% effect",
					meterX, y + 42, 86);
		}

		private void drawRowColumn(
				Graphics2D g,
				int x,
				int y,
				int w,
				int h,
				String title,
				List<RecommendationEvidenceEngine.DsmpRow> rows,
				String term,
				Color color)
		{
			g.setColor(SURFACE_ALT);
			g.fillRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setColor(BORDER);
			g.drawRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setFont(NebulaDesign.fontSection());
			g.setColor(color);
			drawClipped(g, title.toUpperCase(Locale.US), x + 10, y + 16, w - 20);
			if (rows.isEmpty())
			{
				g.setFont(font(Font.PLAIN, 11));
				g.setColor(MUTED);
				drawWrapped(g, "No sampled row contains this term on this side.",
						x + 10, y + 44, w - 20, 3, 15);
				return;
			}
			int availableRows = Math.max(1, (h - 42) / 42);
			int visibleRows = Math.min(rows.size(), Math.min(5, availableRows));
			int rowH = Math.max(36, Math.min(48,
					(h - 38) / Math.max(1, visibleRows)));
			for (int i = 0; i < visibleRows; i++)
			{
				RecommendationEvidenceEngine.DsmpRow row = rows.get(i);
				int rowY = y + 30 + i * rowH;
				g.setColor(SURFACE);
				g.fillRoundRect(x + 8, rowY, w - 16, rowH - 5, 8, 8);
				g.setColor(tint(color, 0.42f));
				g.fillRoundRect(x + 8, rowY + 4, 3, rowH - 13, 3, 3);
				g.setFont(NebulaDesign.fontMedium(10f));
				g.setColor(TEXT);
				drawClipped(g, row.user + " | " + row.followee,
						x + 20, rowY + 14, w - 40);
				g.setFont(font(Font.PLAIN, 10));
				g.setColor(MUTED);
				drawClipped(g, highlightSnippet(row.text, term),
						x + 20, rowY + 31, w - 40);
			}
			if (rows.size() > visibleRows)
			{
				g.setFont(NebulaDesign.fontMedium(9.5f));
				g.setColor(color);
				drawClipped(g, "+" + (rows.size() - visibleRows) + " more rows with this term",
						x + 10, y + h - 10, w - 20);
			}
		}

		private String highlightSnippet(String text, String term)
		{
			if (text == null || text.length() == 0)
			{
				return "";
			}
			String lower = text.toLowerCase(Locale.US);
			String needle = term == null ? "" : term.toLowerCase(Locale.US);
			int index = needle.length() == 0 ? -1 : lower.indexOf(needle);
			if (index < 0)
			{
				return text;
			}
			int start = Math.max(0, index - 28);
			int end = Math.min(text.length(), index + needle.length() + 42);
			String before = start > 0 ? "... " : "";
			String after = end < text.length() ? " ..." : "";
			return before + text.substring(start, index)
					+ text.substring(index, index + needle.length()).toUpperCase(Locale.US)
					+ text.substring(index + needle.length(), end) + after;
		}

		private Color roleColor(String role)
		{
			if ("shared".equals(role))
			{
				return GREEN;
			}
			if ("target-only".equals(role))
			{
				return BLUE;
			}
			if ("candidate-only".equals(role))
			{
				return PURPLE;
			}
			return AMBER;
		}
	}

	private static final class GraphImpactPanel extends JPanel
	{
		private RecommendationEvidenceEngine.EvidenceReport report =
				RecommendationEvidenceEngine.EvidenceReport.empty(
						SimulatorHelpPanel.Snapshot.empty());
		private double phase;

		GraphImpactPanel()
		{
			setOpaque(false);
			setPreferredSize(new Dimension(520, 380));
			setMinimumSize(new Dimension(320, 380));
			setBorder(new EmptyBorder(10, 10, 12, 10));
		}

		void setReport(RecommendationEvidenceEngine.EvidenceReport report)
		{
			this.report = report;
			revalidate();
			repaint();
		}

		void tick()
		{
			phase += 0.035;
			repaint();
		}

		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D) graphics.create();
			prepare(g);
			NebulaWidgets.paintCard(g, 0, 0, getWidth(), getHeight(),
					NebulaDesign.RADIUS_CARD);
			g.setFont(NebulaDesign.fontTitle());
			g.setColor(TEXT);
			g.drawString("Graph Impact", 16, 28);
			g.setFont(font(Font.PLAIN, 11));
			g.setColor(MUTED);
			drawClipped(g, report.graphMessage, 16, 47, getWidth() - 32);
			int metricRows = getWidth() < 560 ? 2 : 1;
			int metricBlockH = metricRows == 2 ? 102 : 54;
			int graphTop = 64;
			int graphBottom = Math.max(graphTop + 130, getHeight() - metricBlockH - 18);
			int centerY = graphTop + (graphBottom - graphTop) / 2;
			int nodeW = Math.max(94, Math.min(132, getWidth() / 5));
			int leftX = 24;
			int rightW = Math.max(132, Math.min(184, getWidth() / 3));
			int rightX = Math.max(leftX + nodeW + 86, getWidth() - rightW - 24);
			drawNode(g, leftX, centerY - 31, nodeW, 62, "Target", report.targetUser, BLUE);
			drawNode(g, rightX, graphTop + 6, rightW, 58, "Follows (observed)",
					report.lastOriginalEdge, AMBER);
			drawNode(g, rightX, graphBottom - 64, rightW, 58, "Adopted (simulated)",
					report.lastRecommendedEdge, GREEN);
			g.setStroke(new BasicStroke(2f));
			g.setColor(NebulaDesign.withAlpha(AMBER, 125));
			g.drawLine(leftX + nodeW, centerY, rightX, graphTop + 35);
			g.setColor(NebulaDesign.withAlpha(GREEN, 135));
			g.drawLine(leftX + nodeW, centerY, rightX, graphBottom - 35);
			drawMovingDot(g, leftX + nodeW, centerY, rightX, graphTop + 35,
					AMBER, 0);
			drawMovingDot(g, leftX + nodeW, centerY, rightX, graphBottom - 35,
					GREEN, 1);

			drawMetrics(g, getHeight() - metricBlockH - 4, metricRows);
			g.dispose();
		}

		private void drawMovingDot(
				Graphics2D g,
				int x1,
				int y1,
				int x2,
				int y2,
				Color color,
				int offset)
		{
			double p = (phase * 0.48 + offset * 0.42) % 1.0;
			int dotX = x1 + (int) ((x2 - x1) * p);
			int dotY = y1 + (int) ((y2 - y1) * p);
			g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 52));
			g.fillOval(dotX - 10, dotY - 10, 20, 20);
			g.setColor(color);
			g.fillOval(dotX - 4, dotY - 4, 8, 8);
		}

		private void drawMetrics(Graphics2D g, int y, int rows)
		{
			String[] labels = { "Candidate", "Exposure", "PageRank", "Communities" };
			String[] values = {
					report.graphRelation,
					report.graphExposure,
					report.graphCandidateRank,
					Integer.toString(report.graphCommunities)
			};
			Color[] colors = { BLUE, PURPLE, GREEN, AMBER };
			int columns = rows == 2 ? 2 : 4;
			int gap = 8;
			int metricW = Math.max(100, (getWidth() - 32 - gap * (columns - 1)) / columns);
			int metricH = rows == 2 ? 42 : 48;
			for (int i = 0; i < labels.length; i++)
			{
				int row = i / columns;
				int col = i % columns;
				drawMiniMetric(g, 16 + col * (metricW + gap),
						y + row * (metricH + 8), metricW, metricH,
						labels[i], values[i], colors[i]);
			}
		}

		private void drawNode(
				Graphics2D g,
				int x,
				int y,
				int w,
				int h,
				String label,
				String value,
				Color color)
		{
			g.setColor(tint(color, 0.93f));
			g.fillRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 105));
			g.drawRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setColor(tint(color, 0.30f));
			g.fillRoundRect(x, y + 8, 3, h - 16, 3, 3);
			g.setFont(NebulaDesign.fontSection());
			g.setColor(color);
			drawClipped(g, label.toUpperCase(Locale.US), x + 14, y + 18, w - 28);
			g.setFont(NebulaDesign.fontMedium(12.5f));
			g.setColor(TEXT);
			drawClipped(g, shortLabel(value), x + 14, y + 40, w - 28);
		}

		private void drawMiniMetric(
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
			g.setFont(NebulaDesign.fontSemiBold(9f));
			g.setColor(color);
			drawClipped(g, label.toUpperCase(Locale.US), x + 9, y + 15, w - 18);
			g.setFont(NebulaDesign.fontMono(12f));
			g.setColor(TEXT);
			drawClipped(g, value, x + 9, y + 34, w - 18);
		}
	}

	private static final class VerdictPanel extends JPanel
	{
		private RecommendationEvidenceEngine.EvidenceReport report =
				RecommendationEvidenceEngine.EvidenceReport.empty(
						SimulatorHelpPanel.Snapshot.empty());

		VerdictPanel()
		{
			setOpaque(false);
			setPreferredSize(new Dimension(420, 380));
			setMinimumSize(new Dimension(320, 380));
			setBorder(new EmptyBorder(10, 10, 12, 10));
		}

		void setReport(RecommendationEvidenceEngine.EvidenceReport report)
		{
			this.report = report;
			revalidate();
			repaint();
		}

		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D) graphics.create();
			prepare(g);
			NebulaWidgets.paintCard(g, 0, 0, getWidth(), getHeight(),
					NebulaDesign.RADIUS_CARD);
			g.setFont(NebulaDesign.fontTitle());
			g.setColor(TEXT);
			g.drawString("Plain-Language Verdict", 16, 28);

			int confidenceH = getWidth() < 420 ? 82 : 70;
			drawConfidence(g, 18, 48, getWidth() - 36, confidenceH);
			int verdictY = 48 + confidenceH + 34;
			g.setFont(NebulaDesign.fontSection());
			g.setColor(GREEN);
			g.drawString("WHAT THIS MEANS", 18, verdictY);
			g.setFont(font(Font.PLAIN, 13));
			g.setColor(TEXT);
			drawWrapped(g, report.plainVerdict, 18, verdictY + 23,
					getWidth() - 36, Math.max(3, (getHeight() - verdictY - 96) / 18), 18);

			int y = Math.max(verdictY + 114, getHeight() - 92);
			if (!report.warnings.isEmpty())
			{
				g.setFont(NebulaDesign.fontSection());
				g.setColor(AMBER);
				g.drawString("CAREFUL NOTES", 18, y);
				g.setFont(font(Font.PLAIN, 11));
				g.setColor(MUTED);
				int lineY = y + 20;
				for (int i = 0; i < report.warnings.size() && i < 3; i++)
				{
					drawClipped(g, "- " + report.warnings.get(i),
							18, lineY, getWidth() - 36);
					lineY += 16;
				}
			}
			g.dispose();
		}

		private void drawConfidence(Graphics2D g, int x, int y, int w, int h)
		{
			g.setPaint(new GradientPaint(
					x, y, tint(BLUE, 0.90f),
					x + w, y + h, tint(GREEN, 0.90f)));
			g.fillRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setColor(BORDER);
			g.drawRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setFont(NebulaDesign.fontSection());
			g.setColor(MUTED);
			g.drawString("MODEL SCORE", x + 14, y + 18);
			g.setFont(NebulaDesign.fontSemiBold(26f));
			g.setColor(TEXT);
			g.drawString(report.scorePercent + "%", x + 14, y + 52);
			int barX = x + 116;
			int barY = y + 36;
			int barW = Math.max(40, w - 136);
			if (w < 260)
			{
				barX = x + 14;
				barY = y + 60;
				barW = w - 28;
			}
			g.setColor(track());
			g.fillRoundRect(barX, barY, barW, 10, 8, 8);
			g.setColor(GREEN);
			g.fillRoundRect(barX, barY,
					Math.max(8, barW * report.scorePercent / 100), 10, 8, 8);
			g.setFont(font(Font.PLAIN, 10));
			g.setColor(MUTED);
			drawClipped(g, report.scoreLabel, barX, y + 27, barW);
		}
	}

	private static final class EmptyStatePanel extends JPanel
	{
		private RecommendationEvidenceEngine.EvidenceReport report =
				RecommendationEvidenceEngine.EvidenceReport.empty(
						SimulatorHelpPanel.Snapshot.empty());

		EmptyStatePanel()
		{
			setOpaque(false);
			setPreferredSize(new Dimension(900, 180));
			setMinimumSize(new Dimension(400, 180));
			setBorder(new EmptyBorder(10, 10, 12, 10));
		}

		void setReport(RecommendationEvidenceEngine.EvidenceReport report)
		{
			this.report = report;
		}

		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D) graphics.create();
			prepare(g);
			NebulaWidgets.paintCard(g, 0, 0, getWidth(), getHeight(),
					NebulaDesign.RADIUS_CARD);
			g.setFont(NebulaDesign.fontSemiBold(19f));
			g.setColor(TEXT);
			g.drawString("Run a recommendation to unlock evidence", 24, 46);
			g.setFont(font(Font.PLAIN, 13));
			g.setColor(MUTED);
			drawWrapped(g, report.plainVerdict, 24, 74, getWidth() - 48, 2, 18);
			int stepY = getHeight() - 42;
			int stepX = 24;
			String[] steps = {
					"1  Start a simulator run",
					"2  Wait for a completed recommendation",
					"3  Click it in the list on the left"
			};
			Color[] hues = { BLUE, PURPLE, GREEN };
			for (int i = 0; i < steps.length; i++)
			{
				stepX += drawChip(g, steps[i], stepX, stepY, hues[i]) + 10;
			}
			g.dispose();
		}
	}

	private static final class EvidenceEntryRenderer extends DefaultListCellRenderer
	{
		public Component getListCellRendererComponent(
				JList<?> list,
				Object value,
				int index,
				boolean isSelected,
				boolean cellHasFocus)
		{
			return new EntryCell((EvidenceEntry) value, isSelected);
		}
	}

	/** Self-painting history cell: no nested layout, theme-resolved at paint. */
	private static final class EntryCell extends JPanel
	{
		private final EvidenceEntry entry;
		private final boolean selected;

		EntryCell(EvidenceEntry entry, boolean selected)
		{
			this.entry = entry;
			this.selected = selected;
			setOpaque(true);
		}

		public Dimension getPreferredSize()
		{
			return new Dimension(220, 52);
		}

		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D) graphics.create();
			prepare(g);
			g.setColor(selected
					? themed(NebulaDesign.DAY_ACCENT_SOFT, NebulaDesign.NIGHT_SELECTION)
					: SURFACE);
			g.fillRect(0, 0, getWidth(), getHeight());
			if (selected)
			{
				g.setColor(BLUE);
				g.fillRoundRect(0, 8, 3, getHeight() - 16, 3, 3);
			}
			g.setFont(NebulaDesign.fontMedium(13f));
			g.setColor(TEXT);
			drawClipped(g, entry.event.targetUser, 14, 22, getWidth() - 28);
			g.setFont(font(Font.PLAIN, 10));
			g.setColor(MUTED);
			drawClipped(g, entry.snapshot.algorithmName + " | "
					+ entry.event.matches.size() + " ranked",
					14, 39, getWidth() - 28);
			g.dispose();
		}
	}

	private static final class MatchRenderer extends DefaultListCellRenderer
	{
		public Component getListCellRendererComponent(
				JList<?> list,
				Object value,
				int index,
				boolean isSelected,
				boolean cellHasFocus)
		{
			return new MatchCell((MatchRow) value, isSelected);
		}
	}

	/** Self-painting candidate cell with a soft-tint rank chip. */
	private static final class MatchCell extends JPanel
	{
		private final MatchRow row;
		private final boolean selected;

		MatchCell(MatchRow row, boolean selected)
		{
			this.row = row;
			this.selected = selected;
			setOpaque(true);
		}

		public Dimension getPreferredSize()
		{
			return new Dimension(220, 54);
		}

		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D) graphics.create();
			prepare(g);
			RecommendationEvidenceEngine.MatchEvidence match =
					row.entry.event.matches.get(row.matchIndex);
			Color hue = row.matchIndex == 0 ? GREEN
					: (row.matchIndex == 1 ? PURPLE : BLUE);
			g.setColor(selected
					? themed(NebulaDesign.DAY_SUCCESS_SOFT, NebulaDesign.NIGHT_SELECTION)
					: SURFACE);
			g.fillRect(0, 0, getWidth(), getHeight());
			if (selected)
			{
				g.setColor(hue);
				g.fillRoundRect(0, 8, 3, getHeight() - 16, 3, 3);
			}
			g.setColor(softTint(hue));
			g.fillRoundRect(12, 11, 32, 32, 12, 12);
			g.setFont(NebulaDesign.fontSemiBold(14f));
			g.setColor(hue);
			String rankText = Integer.toString(match.rank);
			FontMetrics rankMetrics = g.getFontMetrics();
			g.drawString(rankText,
					12 + (32 - rankMetrics.stringWidth(rankText)) / 2,
					11 + (32 + rankMetrics.getAscent() - rankMetrics.getDescent()) / 2);
			g.setFont(NebulaDesign.fontMedium(13f));
			g.setColor(TEXT);
			drawClipped(g, match.name, 54, 23, getWidth() - 68);
			g.setFont(NebulaDesign.fontMono(9.5f));
			g.setColor(MUTED);
			drawClipped(g, match.score == null || match.score.length() == 0
					? "score unavailable" : "score " + match.score,
					54, 40, getWidth() - 68);
			g.dispose();
		}
	}
}
