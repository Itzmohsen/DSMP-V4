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
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
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
	private static final Color BACKGROUND = StudioNightTheme.adaptiveColor(
			new Color(244, 247, 252), StudioNightTheme.NIGHT_BACKGROUND);
	private static final Color SURFACE = StudioNightTheme.adaptiveColor(
			new Color(255, 255, 255), StudioNightTheme.NIGHT_SURFACE);
	private static final Color SURFACE_ALT = StudioNightTheme.adaptiveColor(
			new Color(248, 251, 255), StudioNightTheme.NIGHT_SURFACE_ALT);
	private static final Color BORDER = StudioNightTheme.adaptiveColor(
			new Color(207, 218, 232), StudioNightTheme.NIGHT_BORDER);
	private static final Color TEXT = StudioNightTheme.adaptiveColor(
			new Color(28, 34, 43), StudioNightTheme.NIGHT_TEXT);
	private static final Color MUTED = StudioNightTheme.adaptiveColor(
			new Color(86, 99, 118), StudioNightTheme.NIGHT_MUTED);
	private static final Color BLUE = StudioNightTheme.adaptiveColor(
			new Color(82, 110, 220), StudioNightTheme.NIGHT_BLUE);
	private static final Color PURPLE = StudioNightTheme.adaptiveColor(
			new Color(133, 104, 215), StudioNightTheme.NIGHT_PURPLE);
	private static final Color GREEN = StudioNightTheme.adaptiveColor(
			new Color(78, 151, 111), StudioNightTheme.NIGHT_GREEN);
	private static final Color AMBER = StudioNightTheme.adaptiveColor(
			new Color(189, 140, 51), StudioNightTheme.NIGHT_AMBER);
	private static final Color RED = StudioNightTheme.adaptiveColor(
			new Color(204, 77, 67), StudioNightTheme.NIGHT_RED);
	private static final int RADIUS = 10;

	private final ControllerAgentGui controller;
	private final DefaultListModel<EvidenceEntry> entryModel =
			new DefaultListModel<EvidenceEntry>();
	private final JList<EvidenceEntry> entryList =
			new JList<EvidenceEntry>(entryModel);
	private final DefaultListModel<MatchRow> matchModel =
			new DefaultListModel<MatchRow>();
	private final JList<MatchRow> matchList = new JList<MatchRow>(matchModel);
	private final JLabel heroTitle = new JLabel("Recommendation Evidence Studio");
	private final JLabel heroSubtitle = new JLabel("Click a completed recommendation to see why it happened.");
	private final JLabel statusLabel = new JLabel("Waiting for recommendation output.");
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
	private final JPanel dashboard = new JPanel(new GridBagLayout());
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

		animationTimer = new Timer(33, new ActionListener()
		{
			public void actionPerformed(ActionEvent event)
			{
				signalPanel.tick();
				flowPanel.tick();
				graphImpactPanel.tick();
			}
		});
		animationTimer.start();
		renderReport(currentReport);
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
		header.setBorder(new EmptyBorder(18, 22, 18, 22));

		JPanel titleStack = new JPanel();
		titleStack.setOpaque(false);
		titleStack.setLayout(new BoxLayout(titleStack, BoxLayout.Y_AXIS));
		heroTitle.setFont(font(Font.BOLD, 28));
		heroTitle.setForeground(TEXT);
		heroSubtitle.setFont(font(Font.BOLD, 12));
		heroSubtitle.setForeground(MUTED);
		titleStack.add(heroTitle);
		titleStack.add(Box.createRigidArea(new Dimension(0, 4)));
		titleStack.add(heroSubtitle);
		titleStack.add(Box.createRigidArea(new Dimension(0, 8)));
		titleStack.add(createAttribution());
		header.add(titleStack, BorderLayout.WEST);
		header.add(signalPanel, BorderLayout.CENTER);
		return header;
	}

	private Component createAttribution()
	{
		JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
		panel.setOpaque(false);
		panel.add(tinyLabel("FACULTY ADVISOR"));
		panel.add(strongTinyLabel("Dr. Abdolreza Abhari"));
		panel.add(tinyLabel("DEVELOPER"));
		panel.add(strongTinyLabel("Mohsen Alavi"));
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

		JPanel historyCard = card(new BorderLayout(0, 8));
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
		JScrollPane entryScroll = new JScrollPane(entryList);
		entryScroll.setBorder(BorderFactory.createLineBorder(BORDER));
		historyCard.add(entryScroll, BorderLayout.CENTER);

		JPanel matchCard = card(new BorderLayout(0, 8));
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
		JScrollPane matchScroll = new JScrollPane(matchList);
		matchScroll.setBorder(BorderFactory.createLineBorder(BORDER));
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
		statusLabel.setFont(font(Font.BOLD, 12));
		statusLabel.setForeground(MUTED);
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
		dashboard.revalidate();
		dashboard.repaint();
	}

	private static JPanel card(java.awt.LayoutManager layout)
	{
		JPanel panel = new JPanel(layout);
		panel.setBackground(SURFACE);
		panel.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(BORDER),
				new EmptyBorder(12, 12, 12, 12)));
		return panel;
	}

	private static JLabel sectionTitle(String text)
	{
		JLabel label = new JLabel(text);
		label.setFont(font(Font.BOLD, 14));
		label.setForeground(TEXT);
		return label;
	}

	private static JLabel tinyLabel(String text)
	{
		JLabel label = new JLabel(text);
		label.setFont(font(Font.BOLD, 9));
		label.setForeground(MUTED);
		return label;
	}

	private static JLabel strongTinyLabel(String text)
	{
		JLabel label = tinyLabel(text);
		label.setForeground(TEXT);
		return label;
	}

	private static Font font(int style, int size)
	{
		return new Font("SansSerif", style, size);
	}

	private static void prepare(Graphics2D g)
	{
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
				RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
				RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
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
				new Color(255, 255, 255, alpha),
				StudioNightTheme.withAlpha(
						StudioNightTheme.NIGHT_SURFACE_ALT,
						Math.min(255, alpha + 16)));
	}

	private static Color track()
	{
		return themed(new Color(226, 234, 246),
				StudioNightTheme.NIGHT_BACKGROUND_BOTTOM);
	}

	private static int clamp(int value)
	{
		return Math.max(0, Math.min(255, value));
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
			super.paintComponent(graphics);
			Graphics2D g = (Graphics2D) graphics.create();
			prepare(g);
			g.setPaint(new GradientPaint(
					0, 0, SURFACE,
					getWidth(), getHeight(),
					themed(new Color(237, 247, 243), StudioNightTheme.NIGHT_PANEL_INK)));
			g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, RADIUS, RADIUS);
			g.setColor(BORDER);
			g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, RADIUS, RADIUS);
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
					report.graphAvailable ? "graph evidence available" : "dataset evidence"
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
			g.setColor(SURFACE);
			g.fillRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setColor(tint(color, 0.62f));
			g.fillRoundRect(x, y, 5, h, RADIUS, RADIUS);
			g.setColor(BORDER);
			g.drawRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setFont(font(Font.BOLD, 10));
			g.setColor(color.darker());
			drawClipped(g, label.toUpperCase(Locale.US), x + 14, y + 18, w - 28);
			g.setFont(font(Font.BOLD, 18));
			g.setColor(TEXT);
			drawClipped(g, shortLabel(value), x + 14, y + 42, w - 28);
			g.setFont(font(Font.PLAIN, 10));
			g.setColor(MUTED);
			drawClipped(g, detail, x + 14, y + h - 10, w - 28);
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
			setBorder(BorderFactory.createCompoundBorder(
					BorderFactory.createLineBorder(BORDER),
					new EmptyBorder(10, 10, 10, 10)));
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
			g.setPaint(new GradientPaint(
					0, 0, SURFACE,
					getWidth(), getHeight(),
					themed(new Color(238, 248, 244), StudioNightTheme.NIGHT_PANEL_INK)));
			g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, RADIUS, RADIUS);
			g.setColor(BORDER);
			g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, RADIUS, RADIUS);
			g.setFont(font(Font.BOLD, 18));
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
			g.setFont(font(Font.BOLD, 10));
			g.setColor(BLUE.darker());
			g.drawString("GRAPH SIDE EFFECT READ", x + 14, y + 20);
			g.setFont(font(Font.BOLD, 13));
			g.setColor(TEXT);
			drawClipped(g, report.graphAvailable
					? "Persisted edge changed from "
							+ report.lastOriginalEdge + " to "
							+ report.lastRecommendedEdge
					: "Graph artifacts are not available for this recommendation yet.",
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
			g.setPaint(new GradientPaint(
					x, y, SURFACE,
					x + w, y + h, tint(color, 0.88f)));
			g.fillRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setColor(tint(color, 0.58f));
			g.fillRoundRect(x, y, 6, h, RADIUS, RADIUS);
			g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 125));
			g.drawRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setFont(font(Font.BOLD, 10));
			g.setColor(color.darker());
			drawClipped(g, label.toUpperCase(Locale.US), x + 13, y + 18, w - 26);
			g.setFont(font(Font.BOLD, 16));
			g.setColor(TEXT);
			drawClipped(g, value, x + 13, y + 48, w - 26);
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
			setPreferredSize(new Dimension(520, 330));
			setMinimumSize(new Dimension(420, 300));
			setBorder(BorderFactory.createCompoundBorder(
					BorderFactory.createLineBorder(BORDER),
					new EmptyBorder(10, 10, 10, 10)));
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
			g.setPaint(new GradientPaint(
					0, 0, SURFACE,
					getWidth(), getHeight(),
					themed(new Color(248, 244, 255), StudioNightTheme.NIGHT_PANEL_INK)));
			g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, RADIUS, RADIUS);
			g.setColor(BORDER);
			g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, RADIUS, RADIUS);
			g.setFont(font(Font.BOLD, 15));
			g.setColor(TEXT);
			g.drawString("Counterfactual Evidence Mode", 16, 28);
			g.setFont(font(Font.PLAIN, 11));
			g.setColor(MUTED);
			drawClipped(g,
					"What would need to change for this recommendation to move?",
					16, 47, getWidth() - 32);

			drawSummary(g, 16, 62, getWidth() - 32, 56);
			int y = 132;
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
			int cardH = Math.max(42, Math.min(56,
					(getHeight() - y - 18)
							/ Math.max(1, Math.min(4, report.counterfactualInsights.size()))));
			for (int i = 0; i < report.counterfactualInsights.size() && i < 4; i++)
			{
				RecommendationEvidenceEngine.CounterfactualInsight insight =
						report.counterfactualInsights.get(i);
				drawInsight(g, 16, y + i * (cardH + 8),
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
			g.setFont(font(Font.BOLD, 11));
			g.setColor(PURPLE.darker());
			g.drawString("ROBUSTNESS READ", x + 12, y + 18);
			g.setFont(font(Font.BOLD, 13));
			g.setColor(TEXT);
			drawClipped(g, report.counterfactualSummary, x + 12, y + 40, w - 24);
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
			g.setColor(SURFACE);
			g.fillRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setColor(tint(color, 0.62f));
			g.fillRoundRect(x, y, 6, h, RADIUS, RADIUS);
			g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 120));
			g.drawRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setFont(font(Font.BOLD, 11));
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
			g.fillRoundRect(x + w - 98, y + h / 2 - 5, 78, 10, 8, 8);
			g.setColor(color);
			g.fillRoundRect(x + w - 98, y + h / 2 - 5,
					Math.max(6, 78 * insight.pressurePercent / 100), 10, 8, 8);
			g.setFont(font(Font.BOLD, 10));
			g.setColor(color.darker());
			drawClipped(g, insight.pressurePercent + "% pressure",
					x + w - 100, y + h - 9, 84);
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
			setBorder(BorderFactory.createCompoundBorder(
					BorderFactory.createLineBorder(BORDER),
					new EmptyBorder(10, 10, 10, 10)));
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
			g.setColor(SURFACE);
			g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, RADIUS, RADIUS);
			g.setColor(BORDER);
			g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, RADIUS, RADIUS);
			g.setFont(font(Font.BOLD, 15));
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
			drawSupport(g, 16, 170, getWidth() - 32, 48);
			drawTermLane(g, 16, 236, getWidth() / 2 - 26,
					"winner shared cues", report.comparison.leftSharedTerms, GREEN);
			drawTermLane(g, getWidth() / 2 + 10, 236, getWidth() / 2 - 26,
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
			g.setColor(tint(color, 0.86f));
			g.fillRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 135));
			g.drawRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setFont(font(Font.BOLD, 10));
			g.setColor(color.darker());
			g.drawString("RANK " + match.rank, x + 12, y + 18);
			g.setFont(font(Font.BOLD, 16));
			g.setColor(TEXT);
			drawClipped(g, match.name, x + 12, y + 43, w - 24);
			g.setFont(font(Font.PLAIN, 10));
			g.setColor(MUTED);
			drawClipped(g, "score " + (match.score.length() == 0 ? "-" : match.score)
					+ " | text support " + support, x + 12, y + 63, w - 24);
		}

		private void drawSupport(Graphics2D g, int x, int y, int w, int h)
		{
			int total = Math.max(1,
					report.comparison.leftSupport + report.comparison.rightSupport);
			int leftW = Math.max(4, w * report.comparison.leftSupport / total);
			g.setColor(themed(new Color(235, 240, 248), StudioNightTheme.NIGHT_PANEL_INK));
			g.fillRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setColor(GREEN);
			g.fillRoundRect(x, y, leftW, h, RADIUS, RADIUS);
			g.setColor(PURPLE);
			g.fillRoundRect(x + leftW, y, Math.max(4, w - leftW), h, RADIUS, RADIUS);
			g.setFont(font(Font.BOLD, 11));
			g.setColor(StudioNightTheme.isNightMode() ? StudioNightTheme.NIGHT_TEXT : Color.WHITE);
			drawClipped(g, "support gap | score gap " + report.comparison.scoreGap,
					x + 12, y + 22, w - 24);
			g.setFont(font(Font.PLAIN, 10));
			drawClipped(g, report.comparison.verdict, x + 12, y + 40, w - 24);
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
			g.setFont(font(Font.BOLD, 10));
			g.setColor(color.darker());
			drawClipped(g, label.toUpperCase(Locale.US), x + 10, y + 17, w - 20);
			int chipX = x + 10;
			int chipY = y + 30;
			for (int i = 0; i < terms.size() && i < 4; i++)
			{
				String term = terms.get(i).term;
				int chipW = Math.min(82, Math.max(46,
						g.getFontMetrics().stringWidth(term) + 18));
				if (chipX + chipW > x + w - 8)
				{
					break;
				}
				g.setColor(tint(color, 0.84f));
				g.fillRoundRect(chipX, chipY, chipW, 23, 8, 8);
				g.setColor(color);
				g.drawRoundRect(chipX, chipY, chipW, 23, 8, 8);
				g.setFont(font(Font.BOLD, 10));
				g.setColor(color.darker());
				drawClipped(g, term, chipX + 8, chipY + 15, chipW - 16);
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
			setBorder(BorderFactory.createCompoundBorder(
					BorderFactory.createLineBorder(BORDER),
					new EmptyBorder(10, 10, 10, 10)));
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
			revalidate();
			repaint();
		}

		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D) graphics.create();
			prepare(g);
			g.setColor(SURFACE);
			g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, RADIUS, RADIUS);
			g.setColor(BORDER);
			g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, RADIUS, RADIUS);
			g.setFont(font(Font.BOLD, 15));
			g.setColor(TEXT);
			g.drawString(title, 16, 27);
			g.setFont(font(Font.BOLD, 10));
			g.setColor(color.darker());
			drawClipped(g, entity + " | " + totalRows + " matching rows",
					16, 46, getWidth() - 32);
			int y = 66;
			if (rows.isEmpty())
			{
				g.setFont(font(Font.BOLD, 13));
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
				g.setColor(tint(color, 0.65f));
				g.fillRoundRect(14, rowY, 5, rowH - 5, 8, 8);
				g.setFont(font(Font.BOLD, 11));
				g.setColor(TEXT);
				drawClipped(g, row.followee + "  |  " + row.user, 26, rowY + 17,
						getWidth() - 170);
				g.setFont(font(Font.BOLD, 10));
				g.setColor(MUTED);
				drawClipped(g, row.date, getWidth() - 120, rowY + 17, 100);
				g.setFont(font(Font.PLAIN, 11));
				g.setColor(MUTED);
				drawClipped(g, row.text, 26, rowY + 36, getWidth() - 52);
			}
			if (rows.size() > maxVisible)
			{
				g.setFont(font(Font.BOLD, 10));
				g.setColor(color.darker());
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
			setBorder(BorderFactory.createCompoundBorder(
					BorderFactory.createLineBorder(BORDER),
					new EmptyBorder(10, 10, 10, 10)));
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
			g.setColor(SURFACE);
			g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, RADIUS, RADIUS);
			g.setColor(BORDER);
			g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, RADIUS, RADIUS);
			g.setFont(font(Font.BOLD, 15));
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
			g.setFont(font(Font.BOLD, 11));
			g.setColor(color.darker());
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
					g.setColor(tint(color, 0.78f));
					g.fillRoundRect(hitBounds.x, hitBounds.y,
							hitBounds.width, hitBounds.height, 8, 8);
				}
				g.setFont(font(Font.BOLD, 11));
				g.setColor(selected ? color.darker() : TEXT);
				drawClipped(g, term.term, x + 12, rowY + 10, 76);
				g.setColor(track());
				g.fillRoundRect(x + 94, rowY, w - 110, 12, 6, 6);
				g.setColor(color);
				g.fillRoundRect(x + 94, rowY, barW, 12, 6, 6);
				g.setFont(font(Font.PLAIN, 9));
				g.setColor(MUTED);
				String value = shared
						? "overlap " + term.value
						: Integer.toString(term.value);
				drawClipped(g, value, x + w - 54, rowY + 10, 42);
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
			setBorder(BorderFactory.createCompoundBorder(
					BorderFactory.createLineBorder(BORDER),
					new EmptyBorder(10, 10, 10, 10)));
		}

		void setTerm(RecommendationEvidenceEngine.TermLocation location)
		{
			this.location = location;
			int rowCount = location == null ? 3
					: Math.max(location.targetRows.size(), location.candidateRows.size());
			int dynamicHeight = 180 + Math.max(3, Math.min(6, rowCount)) * 46;
			setPreferredSize(new Dimension(420, Math.min(460, dynamicHeight)));
			revalidate();
			repaint();
		}

		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D) graphics.create();
			prepare(g);
			g.setPaint(new GradientPaint(
					0, 0, SURFACE,
					getWidth(), getHeight(),
					themed(new Color(247, 251, 255), StudioNightTheme.NIGHT_PANEL_INK)));
			g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, RADIUS, RADIUS);
			g.setColor(BORDER);
			g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, RADIUS, RADIUS);
			g.setFont(font(Font.BOLD, 15));
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
			g.setColor(tint(color, 0.84f));
			g.fillRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 135));
			g.drawRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setColor(color);
			g.fillRoundRect(x, y, 7, h, RADIUS, RADIUS);
			g.setFont(font(Font.BOLD, 10));
			g.setColor(color.darker());
			drawClipped(g, location.role.toUpperCase(Locale.US), x + 16, y + 18, w - 32);
			g.setFont(font(Font.BOLD, 18));
			g.setColor(TEXT);
			drawClipped(g, location.term, x + 16, y + 42, Math.max(80, w / 3));
			g.setFont(font(Font.PLAIN, 10));
			g.setColor(MUTED);
			drawClipped(g,
					"target " + location.targetCount + " | candidate "
							+ location.candidateCount,
					x + Math.max(120, w / 3), y + 24, w / 3);
			drawClipped(g, location.counterfactualEffect,
					x + Math.max(120, w / 3), y + 43, w - Math.max(150, w / 3) - 12);
			g.setColor(track());
			g.fillRoundRect(x + w - 112, y + 24, 82, 9, 8, 8);
			g.setColor(color);
			g.fillRoundRect(x + w - 112, y + 24,
					Math.max(5, 82 * location.pressurePercent / 100), 9, 8, 8);
			g.setFont(font(Font.BOLD, 9));
			g.setColor(color.darker());
			drawClipped(g, location.pressurePercent + "% effect",
					x + w - 112, y + 49, 86);
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
			g.setColor(SURFACE);
			g.fillRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setColor(BORDER);
			g.drawRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setFont(font(Font.BOLD, 10));
			g.setColor(color.darker());
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
				g.setColor(i % 2 == 0 ? SURFACE_ALT : SURFACE);
				g.fillRoundRect(x + 8, rowY, w - 16, rowH - 5, 8, 8);
				g.setColor(tint(color, 0.58f));
				g.fillRoundRect(x + 8, rowY, 5, rowH - 5, 8, 8);
				g.setFont(font(Font.BOLD, 10));
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
				g.setFont(font(Font.BOLD, 9));
				g.setColor(color.darker());
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
			setBorder(BorderFactory.createCompoundBorder(
					BorderFactory.createLineBorder(BORDER),
					new EmptyBorder(10, 10, 10, 10)));
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
			g.setPaint(new GradientPaint(
					0, 0, SURFACE,
					getWidth(), getHeight(),
					themed(new Color(238, 248, 244), StudioNightTheme.NIGHT_PANEL_INK)));
			g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, RADIUS, RADIUS);
			g.setColor(BORDER);
			g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, RADIUS, RADIUS);
			g.setFont(font(Font.BOLD, 15));
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
			int rightW = Math.max(110, Math.min(148, getWidth() / 4));
			int rightX = Math.max(leftX + nodeW + 86, getWidth() - rightW - 24);
			drawNode(g, leftX, centerY - 31, nodeW, 62, "Target", report.targetUser, BLUE);
			drawNode(g, rightX, graphTop + 6, rightW, 58, "Original edge",
					report.lastOriginalEdge, AMBER);
			drawNode(g, rightX, graphBottom - 64, rightW, 58, "Recommended edge",
					report.lastRecommendedEdge, GREEN);
			g.setStroke(new BasicStroke(2f));
			g.setColor(new Color(190, 150, 70, 125));
			g.drawLine(leftX + nodeW, centerY, rightX, graphTop + 35);
			g.setColor(new Color(76, 151, 111, 135));
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
			String[] labels = { "Health", "Communities", "Edges", "Candidate" };
			String[] values = {
					report.graphHealthScore + "/100",
					Integer.toString(report.graphCommunities),
					RecommendationEvidenceEngine.formatCount(report.graphEdges),
					report.graphCandidateRank
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
			g.setColor(SURFACE);
			g.fillRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setColor(tint(color, 0.54f));
			g.fillRoundRect(x, y, 6, h, RADIUS, RADIUS);
			g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 120));
			g.drawRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setFont(font(Font.BOLD, 10));
			g.setColor(color.darker());
			drawClipped(g, label.toUpperCase(Locale.US), x + 14, y + 18, w - 28);
			g.setFont(font(Font.BOLD, 13));
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
			g.setColor(SURFACE);
			g.fillRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setColor(BORDER);
			g.drawRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setFont(font(Font.BOLD, 9));
			g.setColor(color.darker());
			drawClipped(g, label.toUpperCase(Locale.US), x + 9, y + 15, w - 18);
			g.setFont(font(Font.BOLD, 13));
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
			setBorder(BorderFactory.createCompoundBorder(
					BorderFactory.createLineBorder(BORDER),
					new EmptyBorder(10, 10, 10, 10)));
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
			g.setColor(SURFACE);
			g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, RADIUS, RADIUS);
			g.setColor(BORDER);
			g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, RADIUS, RADIUS);
			g.setFont(font(Font.BOLD, 15));
			g.setColor(TEXT);
			g.drawString("Plain-Language Verdict", 16, 28);

			int confidenceH = getWidth() < 420 ? 82 : 70;
			drawConfidence(g, 18, 48, getWidth() - 36, confidenceH);
			int verdictY = 48 + confidenceH + 34;
			g.setFont(font(Font.BOLD, 11));
			g.setColor(GREEN.darker());
			g.drawString("WHAT THIS MEANS", 18, verdictY);
			g.setFont(font(Font.PLAIN, 13));
			g.setColor(TEXT);
			drawWrapped(g, report.plainVerdict, 18, verdictY + 23,
					getWidth() - 36, Math.max(3, (getHeight() - verdictY - 96) / 18), 18);

			int y = Math.max(verdictY + 114, getHeight() - 92);
			if (!report.warnings.isEmpty())
			{
				g.setFont(font(Font.BOLD, 11));
				g.setColor(AMBER.darker());
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
					x, y, tint(BLUE, 0.82f),
					x + w, y + h, tint(GREEN, 0.82f)));
			g.fillRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setColor(themed(new Color(210, 224, 240), StudioNightTheme.NIGHT_BORDER));
			g.drawRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setFont(font(Font.BOLD, 10));
			g.setColor(MUTED);
			g.drawString("MODEL SCORE", x + 14, y + 18);
			g.setFont(font(Font.BOLD, 28));
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
			setBorder(BorderFactory.createCompoundBorder(
					BorderFactory.createLineBorder(BORDER),
					new EmptyBorder(10, 10, 10, 10)));
		}

		void setReport(RecommendationEvidenceEngine.EvidenceReport report)
		{
			this.report = report;
		}

		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D) graphics.create();
			prepare(g);
			g.setColor(SURFACE);
			g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, RADIUS, RADIUS);
			g.setColor(BORDER);
			g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, RADIUS, RADIUS);
			g.setFont(font(Font.BOLD, 22));
			g.setColor(TEXT);
			g.drawString("Run a recommendation to unlock evidence", 24, 50);
			g.setFont(font(Font.PLAIN, 13));
			g.setColor(MUTED);
			drawWrapped(g, report.plainVerdict, 24, 78, getWidth() - 48, 3, 18);
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
			JPanel panel = new JPanel(new BorderLayout(8, 0));
			panel.setBorder(new EmptyBorder(10, 10, 10, 10));
			panel.setBackground(isSelected ? tint(BLUE, 0.82f) : SURFACE);
			EvidenceEntry entry = (EvidenceEntry) value;
			JLabel title = new JLabel(entry.event.targetUser);
			title.setFont(font(Font.BOLD, 13));
			title.setForeground(TEXT);
			JLabel detail = new JLabel(entry.snapshot.algorithmName + " | "
					+ entry.event.matches.size() + " ranked");
			detail.setFont(font(Font.PLAIN, 10));
			detail.setForeground(MUTED);
			JPanel stack = new JPanel();
			stack.setOpaque(false);
			stack.setLayout(new BoxLayout(stack, BoxLayout.Y_AXIS));
			stack.add(title);
			stack.add(detail);
			panel.add(stack, BorderLayout.CENTER);
			return panel;
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
			MatchRow row = (MatchRow) value;
			RecommendationEvidenceEngine.MatchEvidence match =
					row.entry.event.matches.get(row.matchIndex);
			JPanel panel = new JPanel(new BorderLayout(10, 0));
			panel.setBorder(new EmptyBorder(10, 10, 10, 10));
			panel.setBackground(isSelected ? tint(GREEN, 0.80f) : SURFACE);
			JLabel rank = new JLabel(Integer.toString(match.rank), SwingConstants.CENTER);
			rank.setFont(font(Font.BOLD, 16));
			rank.setForeground(Color.WHITE);
			rank.setOpaque(true);
			rank.setBackground(row.matchIndex == 0 ? GREEN
					: (row.matchIndex == 1 ? PURPLE : BLUE));
			rank.setPreferredSize(new Dimension(34, 34));
			JLabel title = new JLabel(match.name);
			title.setFont(font(Font.BOLD, 13));
			title.setForeground(TEXT);
			JLabel detail = new JLabel(match.score == null || match.score.length() == 0
					? "score unavailable" : "score " + match.score);
			detail.setFont(font(Font.PLAIN, 10));
			detail.setForeground(MUTED);
			JPanel stack = new JPanel();
			stack.setOpaque(false);
			stack.setLayout(new BoxLayout(stack, BoxLayout.Y_AXIS));
			stack.add(title);
			stack.add(detail);
			panel.add(rank, BorderLayout.WEST);
			panel.add(stack, BorderLayout.CENTER);
			return panel;
		}
	}
}
