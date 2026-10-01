package TwitterGatherDataFollowers.userRyersonU;

import java.awt.AlphaComposite;
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
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.RadialGradientPaint;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JList;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JOptionPane;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.border.Border;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.BasicProgressBarUI;
import javax.swing.plaf.basic.BasicSpinnerUI;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;

/**
 * Full-page interface for the isolated exact population benchmark. It does not
 * mutate ControllerAgent state or participate in the existing JADE workflow.
 */
final class PopulationSweepPanel extends JPanel
{
	private static final Color BACKGROUND = NebulaDesign.CANVAS_TOP;
	private static final Color SURFACE = NebulaDesign.SURFACE;
	private static final Color SURFACE_ALT = NebulaDesign.SURFACE_ALT;
	private static final Color BORDER = NebulaDesign.BORDER_SUBTLE;
	private static final Color TEXT = NebulaDesign.TEXT_PRIMARY;
	private static final Color MUTED = NebulaDesign.TEXT_SECONDARY;
	private static final Color BLUE = NebulaDesign.ACCENT;
	private static final Color GREEN = NebulaDesign.SUCCESS;
	private static final Color AMBER = NebulaDesign.WARNING;
	private static final Color RED = NebulaDesign.DANGER;
	private static final Color PURPLE = NebulaDesign.PURPLE;

	private static final int BUTTON_PRIMARY = 0;
	private static final int BUTTON_SUCCESS = 1;
	private static final int BUTTON_NEUTRAL = 2;
	private static final int BUTTON_DANGER = 3;

	private final ControllerAgentGui owner;
	private final JComboBox<ExactPopulationSweepEngine.Algorithm> algorithmBox =
			new PinnedComboBox<ExactPopulationSweepEngine.Algorithm>(
					ExactPopulationSweepEngine.Algorithm.values());
	private final JSpinner nodesSpinner = new JSpinner(
			new SpinnerNumberModel(
					Math.max(1, Math.min(8,
							Runtime.getRuntime().availableProcessors())),
					1, 64, 1));
	private final JSpinner targetLimitSpinner =
			new JSpinner(new SpinnerNumberModel(0, 0, 1000000, 10));
	private final JSpinner topResultsSpinner =
			new JSpinner(new SpinnerNumberModel(3, 1, 100, 1));
	private final JTextField beginDateField = new NebulaField("2007-01-01");
	private final JTextField endDateField = new NebulaField("2099-12-31");
	private final JCheckBox removeHashtagsBox =
			new TokenCheckBox("Remove hashtags", true);
	private final JCheckBox removeRetweetsBox =
			new TokenCheckBox("Remove retweets", true);
	private final JCheckBox removeStopWordsBox =
			new TokenCheckBox("Remove stop words", true);
	private final JCheckBox verifyBox =
			new TokenCheckBox("Verify against one-worker baseline", true);
	private final JButton chooseDatasetButton =
			createButton("Choose Dataset", BUTTON_PRIMARY);
	private final JButton useCurrentDatasetButton =
			createButton("Use Simulator Dataset", BUTTON_NEUTRAL);
	private final JButton runButton =
			createButton("Run Verified Sweep", BUTTON_SUCCESS);
	private final JButton cancelButton =
			createButton("Cancel", BUTTON_DANGER);
	private final JButton algorithmSettingsButton =
			createButton("Algorithm Settings", BUTTON_NEUTRAL);
	private final JButton capacityButton =
			createButton("Open Node Lab", BUTTON_NEUTRAL);
	private final TokenLabel datasetNameLabel = new TokenLabel("No dataset selected");
	private final TokenLabel datasetPathLabel =
			new TokenLabel("Choose a six-column simulator dataset.");
	private final TokenLabel algorithmDetailLabel = new TokenLabel("");
	private final TokenLabel outputTypeLabel = new TokenLabel("");
	private final PillLabel algorithmSettingsPill = badge("DEFAULT", GREEN);
	private final JPanel doc2VecCachePanel =
			surface(new BorderLayout(12, 0));
	private final PillLabel doc2VecCachePill = pillLabel("CACHE UNKNOWN");
	private final TokenLabel doc2VecCacheTitle =
			new TokenLabel("Doc2Vec cache transparency");
	private final TokenLabel doc2VecCacheDetail =
			new TokenLabel("Choose a dataset to inspect whether training will run or be skipped.");
	private final TokenLabel doc2VecCacheRows = new TokenLabel("--");
	private final TokenLabel doc2VecCacheUsers = new TokenLabel("--");
	private final TokenLabel doc2VecCacheVectors = new TokenLabel("--");
	private final TokenLabel doc2VecCacheSize = new TokenLabel("--");
	private final TokenLabel doc2VecCacheKey = new TokenLabel("--");
	private final JButton refreshDoc2VecCacheButton =
			createButton("Refresh Cache", BUTTON_NEUTRAL);
	private final JButton removeDoc2VecCacheButton =
			createButton("Remove This Cache", BUTTON_DANGER);
	private final PillLabel statusPill = pillLabel("READY");
	private final TokenLabel statusLabel =
			new TokenLabel("Configure an exact population benchmark.");
	private final TokenLabel baselineMetric = metricValue("--");
	private final TokenLabel parallelMetric = metricValue("--");
	private final TokenLabel speedupMetric = metricValue("--");
	private final TokenLabel efficiencyMetric = metricValue("--");
	private final TokenLabel verificationMetric = metricValue("NOT RUN");
	private final TokenLabel digestMetric = metricValue("--");
	private final JProgressBar overallProgress = new JProgressBar(0, 100);
	private final JPanel workerCardsPanel =
			new JPanel(new GridLayout(0, 4, 12, 12));
	private final Map<Integer,WorkerCard> workerCards =
			new LinkedHashMap<Integer,WorkerCard>();
	private final ResultsTableModel resultsModel = new ResultsTableModel();
	private final JTable resultsTable = new JTable(resultsModel);
	private final JTextArea eventLog = new ConsoleArea();

	private volatile File datasetFile;
	private volatile ExactPopulationSweepEngine.CancellationToken cancellationToken;
	private volatile Thread runThread;
	private volatile boolean running;
	private volatile int cacheRefreshSerial;
	private volatile Doc2VecCacheAdmin.CacheInfo currentDoc2VecCacheInfo;

	PopulationSweepPanel(ControllerAgentGui owner)
	{
		super(new BorderLayout(0, 14));
		this.owner = owner;
		setBackground(BACKGROUND);
		setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
		buildInterface();
		installActions();
		updateAlgorithmContext();
		setRunning(false);
		buildWorkerCards(((Number)nodesSpinner.getValue()).intValue());
	}

	protected void paintComponent(Graphics graphics)
	{
		Graphics2D g2 = (Graphics2D) graphics.create();
		NebulaDesign.prepare(g2);
		boolean night = StudioNightTheme.isNightMode();
		Color top = night ? NebulaDesign.NIGHT_CANVAS_TOP : NebulaDesign.DAY_CANVAS_TOP;
		Color bottom = night ? NebulaDesign.NIGHT_CANVAS_BOTTOM : NebulaDesign.DAY_CANVAS_BOTTOM;
		g2.setPaint(new GradientPaint(0, 0, top, 0, Math.max(1, getHeight()), bottom));
		g2.fillRect(0, 0, getWidth(), getHeight());
		int glow = night ? 22 : 12;
		paintGlow(g2, getWidth() / 6, 0, Math.max(240, getHeight()), BLUE, glow);
		paintGlow(g2, getWidth() * 5 / 6, getHeight(), Math.max(240, getHeight()), PURPLE, glow - 4);
		g2.dispose();
	}

	void cancelCurrentRun()
	{
		ExactPopulationSweepEngine.CancellationToken token = cancellationToken;
		if (token != null)
		{
			token.cancel();
		}
		Thread thread = runThread;
		if (thread != null)
		{
			thread.interrupt();
		}
	}

	private void buildInterface()
	{
		add(createHeader(), BorderLayout.NORTH);

		JPanel body = new JPanel(new BorderLayout(0, 14));
		body.setOpaque(false);
		body.add(createConfigurationBand(), BorderLayout.NORTH);
		body.add(createWorkArea(), BorderLayout.CENTER);
		add(body, BorderLayout.CENTER);
		add(createStatusBar(), BorderLayout.SOUTH);
	}

	private JComponent createHeader()
	{
		JPanel header = heroCard();
		header.setLayout(new BorderLayout(24, 0));
		header.setBorder(BorderFactory.createEmptyBorder(14, 20, 14, 20));

		JPanel headerContent = new JPanel(new BorderLayout(28, 0));
		headerContent.setOpaque(false);

		JPanel titleStack = new JPanel();
		titleStack.setOpaque(false);
		titleStack.setLayout(new BoxLayout(titleStack, BoxLayout.Y_AXIS));
		JLabel eyebrow = label(
				"LABS  ·  EXACT BENCHMARK", BLUE, NebulaDesign.fontSection());
		JLabel title = label(
				"Exact Multi-Node Population Lab",
				TEXT,
				NebulaDesign.fontSemiBold(23f));
		JLabel subtitle = label(
				"Complete target jobs, one canonical dataset, automatic one-node equivalence checking.",
				MUTED,
				NebulaDesign.fontBody(12.5f));
		JLabel attribution = label(
				"Faculty advisor Dr. Abdolreza Abhari  ·  Developer Mohsen Alavi",
				NebulaDesign.TEXT_TERTIARY,
				NebulaDesign.fontSmall());
		titleStack.add(eyebrow);
		titleStack.add(Box.createRigidArea(new Dimension(0, 4)));
		titleStack.add(title);
		titleStack.add(Box.createRigidArea(new Dimension(0, 3)));
		titleStack.add(subtitle);
		titleStack.add(Box.createRigidArea(new Dimension(0, 6)));
		titleStack.add(attribution);

		headerContent.add(titleStack, BorderLayout.WEST);
		headerContent.add(new HeaderSignalPanel(BLUE, GREEN), BorderLayout.CENTER);
		header.add(headerContent, BorderLayout.CENTER);
		return header;
	}

	private static JPanel heroCard()
	{
		JPanel panel = new JPanel()
		{
			protected void paintComponent(Graphics graphics)
			{
				Graphics2D g2 = (Graphics2D) graphics.create();
				NebulaWidgets.paintCard(g2, 0, 0, getWidth(), getHeight(),
						NebulaDesign.RADIUS_CARD);
				Shape oldClip = g2.getClip();
				g2.clip(new RoundRectangle2D.Float(0, 0, getWidth() - 1, getHeight() - 3,
						NebulaDesign.RADIUS_CARD, NebulaDesign.RADIUS_CARD));
				boolean night = StudioNightTheme.isNightMode();
				int alpha = night ? 26 : 15;
				paintGlow(g2, getWidth() / 5, 0, getHeight() * 2, BLUE, alpha);
				paintGlow(g2, getWidth() * 4 / 5, getHeight(), getHeight() * 2,
						PURPLE, Math.max(6, alpha - 4));
				g2.setClip(oldClip);
				g2.dispose();
				super.paintComponent(graphics);
			}
		};
		panel.setOpaque(false);
		return panel;
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

	private JComponent createConfigurationBand()
	{
		JPanel band = surface(new BorderLayout(16, 0));
		band.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));

		JPanel datasetPanel = new JPanel(new BorderLayout(12, 0));
		datasetPanel.setOpaque(false);
		DatasetIcon icon = new DatasetIcon();
		datasetPanel.add(icon, BorderLayout.WEST);
		JPanel datasetText = new JPanel();
		datasetText.setOpaque(false);
		datasetText.setLayout(new BoxLayout(datasetText, BoxLayout.Y_AXIS));
		datasetNameLabel.setFont(NebulaDesign.fontSemiBold(13f));
		datasetNameLabel.setToken(TEXT);
		datasetPathLabel.setFont(NebulaDesign.fontBody(11f));
		datasetPathLabel.setToken(NebulaDesign.TEXT_TERTIARY);
		datasetText.add(datasetNameLabel);
		datasetText.add(Box.createRigidArea(new Dimension(0, 3)));
		datasetText.add(datasetPathLabel);
		datasetPanel.add(datasetText, BorderLayout.CENTER);

		JPanel datasetActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 7, 0));
		datasetActions.setOpaque(false);
		datasetActions.add(useCurrentDatasetButton);
		datasetActions.add(chooseDatasetButton);
		datasetPanel.add(datasetActions, BorderLayout.EAST);

		JPanel controls = new JPanel(new GridBagLayout());
		controls.setOpaque(false);
		addControl(controls, field("Algorithm", algorithmBox), 0, 1.5);
		addControl(controls, field("Workers", nodesSpinner), 1, 0.7);
		addControl(controls, field("Target limit", targetLimitSpinner), 2, 0.8);
		addControl(controls, field("Top results", topResultsSpinner), 3, 0.8);
		addControl(controls, field("From", beginDateField), 4, 1.0);
		addControl(controls, field("To", endDateField), 5, 1.0);

		JPanel context = new JPanel(new BorderLayout(12, 0))
		{
			protected void paintComponent(Graphics graphics)
			{
				Graphics2D g2 = (Graphics2D) graphics.create();
				NebulaDesign.prepare(g2);
				boolean night = StudioNightTheme.isNightMode();
				g2.setColor(night
						? NebulaDesign.NIGHT_ACCENT_SOFT
						: NebulaDesign.DAY_ACCENT_SOFT);
				g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1,
						NebulaDesign.RADIUS_CONTROL, NebulaDesign.RADIUS_CONTROL);
				g2.setColor(NebulaDesign.withAlpha(BLUE, night ? 90 : 60));
				g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1,
						NebulaDesign.RADIUS_CONTROL, NebulaDesign.RADIUS_CONTROL);
				g2.dispose();
				super.paintComponent(graphics);
			}
		};
		context.setOpaque(false);
		context.setBorder(BorderFactory.createEmptyBorder(9, 12, 9, 12));
		JPanel contextText = new JPanel();
		contextText.setOpaque(false);
		contextText.setLayout(new BoxLayout(contextText, BoxLayout.Y_AXIS));
		algorithmDetailLabel.setFont(NebulaDesign.fontMedium(12f));
		algorithmDetailLabel.setToken(TEXT);
		outputTypeLabel.setFont(NebulaDesign.fontBody(11f));
		outputTypeLabel.setToken(MUTED);
		contextText.add(algorithmDetailLabel);
		contextText.add(Box.createRigidArea(new Dimension(0, 2)));
			contextText.add(outputTypeLabel);
			context.add(contextText, BorderLayout.CENTER);
			JPanel contextActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
			contextActions.setOpaque(false);
			capacityButton.setPreferredSize(new Dimension(150, 32));
			algorithmSettingsButton.setPreferredSize(new Dimension(170, 32));
			contextActions.add(algorithmSettingsPill);
			contextActions.add(capacityButton);
			contextActions.add(algorithmSettingsButton);
			context.add(contextActions, BorderLayout.EAST);

			JPanel processing = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
		processing.setOpaque(false);
		styleCheckBox(removeHashtagsBox);
		styleCheckBox(removeRetweetsBox);
		styleCheckBox(removeStopWordsBox);
		styleCheckBox(verifyBox);
		processing.add(removeHashtagsBox);
		processing.add(removeRetweetsBox);
		processing.add(removeStopWordsBox);
		processing.add(verifyBox);

		JPanel stack = new JPanel();
		stack.setOpaque(false);
		stack.setLayout(new BoxLayout(stack, BoxLayout.Y_AXIS));
		datasetPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		controls.setAlignmentX(Component.LEFT_ALIGNMENT);
		context.setAlignmentX(Component.LEFT_ALIGNMENT);
		doc2VecCachePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		processing.setAlignmentX(Component.LEFT_ALIGNMENT);
		stack.add(datasetPanel);
		stack.add(Box.createRigidArea(new Dimension(0, 12)));
		stack.add(controls);
		stack.add(Box.createRigidArea(new Dimension(0, 10)));
		stack.add(context);
		stack.add(Box.createRigidArea(new Dimension(0, 8)));
		stack.add(createDoc2VecCachePanel());
		stack.add(Box.createRigidArea(new Dimension(0, 8)));
		stack.add(processing);
		band.add(stack, BorderLayout.CENTER);

		JPanel runActions = new JPanel();
		runActions.setOpaque(false);
		runActions.setLayout(new BoxLayout(runActions, BoxLayout.Y_AXIS));
		runButton.setMaximumSize(new Dimension(190, 42));
		cancelButton.setMaximumSize(new Dimension(190, 38));
		runActions.add(runButton);
		runActions.add(Box.createRigidArea(new Dimension(0, 8)));
		runActions.add(cancelButton);
		band.add(runActions, BorderLayout.EAST);
		return band;
	}

	private JComponent createDoc2VecCachePanel()
	{
		doc2VecCachePanel.setBorder(BorderFactory.createEmptyBorder(10, 14, 12, 14));

		JPanel textStack = new JPanel();
		textStack.setOpaque(false);
		textStack.setLayout(new BoxLayout(textStack, BoxLayout.Y_AXIS));
		JPanel heading = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
		heading.setOpaque(false);
		doc2VecCacheTitle.setFont(NebulaDesign.fontSemiBold(13f));
		doc2VecCacheTitle.setToken(TEXT);
		doc2VecCachePill.setHorizontalAlignment(SwingConstants.CENTER);
		heading.add(doc2VecCacheTitle);
		heading.add(doc2VecCachePill);
		doc2VecCacheDetail.setFont(NebulaDesign.fontBody(11f));
		doc2VecCacheDetail.setToken(MUTED);
		textStack.add(heading);
		textStack.add(Box.createRigidArea(new Dimension(0, 4)));
		textStack.add(doc2VecCacheDetail);

		JPanel stats = new JPanel(new GridLayout(1, 5, 8, 0));
		stats.setOpaque(false);
		stats.add(statBlock("Dataset rows", doc2VecCacheRows));
		stats.add(statBlock("Target users", doc2VecCacheUsers));
		stats.add(statBlock("User vectors", doc2VecCacheVectors));
		stats.add(statBlock("Cache size", doc2VecCacheSize));
		stats.add(statBlock("Cache key", doc2VecCacheKey));
		textStack.add(Box.createRigidArea(new Dimension(0, 8)));
		textStack.add(stats);

		JPanel actions = new JPanel();
		actions.setOpaque(false);
		actions.setLayout(new BoxLayout(actions, BoxLayout.Y_AXIS));
		refreshDoc2VecCacheButton.setMaximumSize(new Dimension(158, 34));
		removeDoc2VecCacheButton.setMaximumSize(new Dimension(158, 34));
		refreshDoc2VecCacheButton.setToolTipText(
				"Re-check whether this dataset has a valid Doc2Vec cache.");
		removeDoc2VecCacheButton.setToolTipText(
				"Delete only the Doc2Vec cache files for the selected dataset.");
		actions.add(refreshDoc2VecCacheButton);
		actions.add(Box.createRigidArea(new Dimension(0, 7)));
		actions.add(removeDoc2VecCacheButton);

		doc2VecCachePanel.putClientProperty("nebula.inset", Boolean.TRUE);
		doc2VecCachePanel.add(textStack, BorderLayout.CENTER);
		doc2VecCachePanel.add(actions, BorderLayout.EAST);
		applyDoc2VecCacheMessage(
				"CACHE UNKNOWN",
				"Choose a dataset to inspect whether training will run or be skipped.",
				MUTED);
		return doc2VecCachePanel;
	}

	private JComponent createWorkArea()
	{
		JPanel left = new JPanel(new BorderLayout(0, 12));
		left.setOpaque(false);
		left.add(createMetricsPanel(), BorderLayout.NORTH);
		left.add(createWorkersPanel(), BorderLayout.CENTER);

		JPanel right = new JPanel(new BorderLayout(0, 12));
		right.setOpaque(false);
		right.add(createResultsPanel(), BorderLayout.CENTER);
		right.add(createLimitationsPanel(), BorderLayout.SOUTH);

		JSplitPane split = new JSplitPane(
				JSplitPane.HORIZONTAL_SPLIT, left, right);
		split.setBorder(null);
		split.setOpaque(false);
		split.setDividerSize(8);
		split.setResizeWeight(0.63);
		SwingUtilities.invokeLater(new Runnable()
		{
			public void run()
			{
				split.setDividerLocation(0.63);
			}
		});
		return split;
	}

	private JComponent createMetricsPanel()
	{
		baselineMetric.setFont(NebulaDesign.fontMono(15f));
		parallelMetric.setFont(NebulaDesign.fontMono(15f));
		speedupMetric.setFont(NebulaDesign.fontMono(15f));
		efficiencyMetric.setFont(NebulaDesign.fontMono(15f));
		digestMetric.setFont(NebulaDesign.fontMono(15f));
		verificationMetric.setFont(NebulaDesign.fontSemiBold(14f));
		JPanel metrics = new JPanel(new GridLayout(1, 6, 10, 0));
		metrics.setOpaque(false);
		metrics.add(metricCard("One worker", baselineMetric, "Measured baseline", 0));
		metrics.add(metricCard("Multi worker", parallelMetric, "Parallel wall time", 1));
		metrics.add(metricCard("Speedup", speedupMetric, "Baseline / parallel", 4));
		metrics.add(metricCard("Efficiency", efficiencyMetric, "Speedup / workers", 3));
		metrics.add(metricCard("Verification", verificationMetric, "Result fingerprints", 5));
		metrics.add(metricCard("Result digest", digestMetric, "Canonical SHA-256", 2));
		return metrics;
	}

	private JComponent createWorkersPanel()
	{
		JPanel panel = surface(new BorderLayout(0, 12));
		panel.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
		JPanel heading = new JPanel(new BorderLayout());
		heading.setOpaque(false);
		JLabel title = label(
				"Parallel Worker Activity", TEXT, NebulaDesign.fontTitle());
		JLabel description = label(
				"Each card owns complete target users. No target is split between workers.",
				NebulaDesign.TEXT_TERTIARY,
				NebulaDesign.fontSmall());
		heading.add(title, BorderLayout.WEST);
		heading.add(description, BorderLayout.EAST);

		workerCardsPanel.setOpaque(false);
		JPanel holder = new JPanel(new BorderLayout());
		holder.setOpaque(false);
		holder.add(workerCardsPanel, BorderLayout.NORTH);
		JScrollPane scroll = new JScrollPane(holder);
		scroll.setBorder(null);
		scroll.setOpaque(false);
		scroll.getViewport().setOpaque(false);
		scroll.getVerticalScrollBar().setUnitIncrement(16);
		scroll.setHorizontalScrollBarPolicy(
				javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

		panel.add(heading, BorderLayout.NORTH);
		panel.add(scroll, BorderLayout.CENTER);
		return panel;
	}

	private JComponent createResultsPanel()
	{
		JPanel panel = surface(new BorderLayout(0, 10));
		panel.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
		JPanel heading = new JPanel(new BorderLayout());
		heading.setOpaque(false);
		JLabel title = label("Population Results", TEXT, NebulaDesign.fontTitle());
		JLabel detail = label(
				"Raw model scores", NebulaDesign.TEXT_TERTIARY, NebulaDesign.fontSmall());
		heading.add(title, BorderLayout.WEST);
		heading.add(detail, BorderLayout.EAST);

		resultsTable.setRowHeight(32);
		resultsTable.setFont(NebulaDesign.fontBody(12f));
		resultsTable.setForeground(TEXT);
		resultsTable.setBackground(SURFACE);
		resultsTable.setOpaque(false);
		resultsTable.setGridColor(BORDER);
		resultsTable.setShowVerticalLines(false);
		resultsTable.setIntercellSpacing(new Dimension(0, 1));
		resultsTable.setSelectionBackground(NebulaDesign.SELECTION);
		resultsTable.setSelectionForeground(TEXT);
		resultsTable.setFillsViewportHeight(true);
		resultsTable.getColumnModel().getColumn(1).setMaxWidth(56);
		resultsTable.getColumnModel().getColumn(3).setPreferredWidth(96);
		styleTableHeader(resultsTable.getTableHeader());
		DefaultTableCellRenderer scoreRenderer = new DefaultTableCellRenderer()
		{
			public Component getTableCellRendererComponent(
					JTable table,
					Object value,
					boolean isSelected,
					boolean hasFocus,
					int row,
					int column)
			{
				Component component = super.getTableCellRendererComponent(
						table, value, isSelected, hasFocus, row, column);
				component.setFont(NebulaDesign.fontMono(11.5f));
				return component;
			}
		};
		scoreRenderer.setHorizontalAlignment(SwingConstants.RIGHT);
		resultsTable.getColumnModel().getColumn(3).setCellRenderer(scoreRenderer);

		JScrollPane tableScroll = well(
				resultsTable,
				false,
				"Ranked results stream in here as workers finish complete target users.");
		tableScroll.setColumnHeaderView(resultsTable.getTableHeader());
		tableScroll.getColumnHeader().setOpaque(false);

		eventLog.setEditable(false);
		eventLog.setRows(4);
		eventLog.setFont(NebulaDesign.fontMono(11.5f));
		pinBorder(eventLog, BorderFactory.createEmptyBorder(11, 13, 11, 13));
		JScrollPane logScroll = well(
				eventLog,
				true,
				"Run events, dataset digests, and cache notes appear here.");
		logScroll.setPreferredSize(new Dimension(100, 98));

		panel.add(heading, BorderLayout.NORTH);
		panel.add(tableScroll, BorderLayout.CENTER);
		panel.add(logScroll, BorderLayout.SOUTH);
		return panel;
	}

	private JComponent createLimitationsPanel()
	{
		JPanel panel = surface(new BorderLayout(0, 4));
		panel.setBorder(BorderFactory.createEmptyBorder(11, 13, 11, 13));
		panel.setPreferredSize(new Dimension(100, 92));
		JLabel title = label(
				"Why only three algorithms?", AMBER, NebulaDesign.fontSemiBold(12f));
		JLabel text = label(
				"<html>Excluded because their current multi-node mathematics cannot guarantee one-node equivalence: "
				+ "K-Means, MLP, Euclidean K-Means, and Common Neighbors. They remain unchanged in Simulator.</html>",
				MUTED,
				NebulaDesign.fontBody(11f));
		text.setVerticalAlignment(SwingConstants.TOP);
		panel.add(title, BorderLayout.NORTH);
		panel.add(text, BorderLayout.CENTER);
		return panel;
	}

	private JComponent createStatusBar()
	{
		JPanel panel = surface(new BorderLayout(12, 0));
		panel.setBorder(BorderFactory.createEmptyBorder(9, 14, 11, 14));
		statusPill.setHorizontalAlignment(SwingConstants.CENTER);
		statusLabel.setFont(NebulaDesign.fontMedium(12f));
		statusLabel.setToken(TEXT);
		overallProgress.setStringPainted(true);
		overallProgress.setFont(NebulaDesign.fontMono(10.5f));
		overallProgress.setPreferredSize(new Dimension(280, 20));
		overallProgress.setForeground(BLUE);
		overallProgress.setBackground(NebulaDesign.INSET);
		overallProgress.setBorder(BorderFactory.createEmptyBorder());
		overallProgress.setUI(new NebulaProgressBarUI(true));
		panel.add(statusPill, BorderLayout.WEST);
		panel.add(statusLabel, BorderLayout.CENTER);
		panel.add(overallProgress, BorderLayout.EAST);
		setStatus("READY", "Configure an exact population benchmark.", BLUE);
		return panel;
	}

	private void installActions()
	{
		chooseDatasetButton.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent event)
			{
				chooseDataset();
			}
		});
		useCurrentDatasetButton.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent event)
			{
				useSimulatorDataset();
			}
		});
			algorithmBox.addActionListener(new ActionListener()
			{
				public void actionPerformed(ActionEvent event)
				{
					updateAlgorithmContext();
					refreshDoc2VecCacheStatus();
				}
			});
			algorithmSettingsButton.addActionListener(new ActionListener()
			{
				public void actionPerformed(ActionEvent event)
				{
					openAlgorithmSettingsDialog();
				}
			});
			capacityButton.addActionListener(new ActionListener()
			{
				public void actionPerformed(ActionEvent event)
				{
					if (owner != null)
					{
						owner.showNodeLab(NodeLabPanel.CLUSTER);
					}
				}
			});
		nodesSpinner.addChangeListener(new ChangeListener()
		{
			public void stateChanged(ChangeEvent event)
			{
				if (runThread == null)
				{
					buildWorkerCards(
							((Number)nodesSpinner.getValue()).intValue());
				}
			}
		});
		runButton.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent event)
			{
				startRun();
			}
		});
		cancelButton.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent event)
			{
				cancelCurrentRun();
				setStatus(
						"CANCELLING",
						"Stopping workers after their current operation.",
						AMBER);
			}
		});
		refreshDoc2VecCacheButton.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent event)
			{
				refreshDoc2VecCacheStatus();
			}
		});
		removeDoc2VecCacheButton.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent event)
			{
				removeCurrentDoc2VecCache();
			}
		});
	}

	private void chooseDataset()
	{
		File currentDirectory = datasetFile != null
				? datasetFile.getParentFile()
				: owner != null && owner.fileChooser != null
						? owner.fileChooser.getCurrentDirectory()
						: new File(System.getProperty("user.dir"));
		File selected = ModernFilePicker.chooseDataset(
				this, currentDirectory, datasetFile);
		if (selected != null)
		{
			setDataset(selected);
		}
	}

	private void useSimulatorDataset()
	{
		File selected = owner == null || owner.fileChooser == null
				? null : owner.fileChooser.getSelectedFile();
		if (selected == null || !selected.isFile())
		{
			setStatus(
					"DATASET NEEDED",
					"The simulator does not currently have a text dataset selected.",
					AMBER);
			return;
		}
		setDataset(selected);
	}

	private void setDataset(File selected)
	{
		datasetFile = selected.getAbsoluteFile();
		datasetNameLabel.setText(selected.getName());
		datasetPathLabel.setText(selected.getAbsolutePath());
		datasetPathLabel.setToolTipText(selected.getAbsolutePath());
		setStatus(
				"READY",
				"Dataset selected. The lab will read its own immutable snapshot.",
				GREEN);
		refreshDoc2VecCacheStatus();
	}

	private void updateAlgorithmContext()
	{
		ExactPopulationSweepEngine.Algorithm algorithm =
				(ExactPopulationSweepEngine.Algorithm)algorithmBox.getSelectedItem();
		boolean doc2Vec = algorithm == ExactPopulationSweepEngine.Algorithm.DOC2VEC;
		removeHashtagsBox.setEnabled(!doc2Vec);
		removeRetweetsBox.setEnabled(!doc2Vec);
		removeStopWordsBox.setEnabled(!doc2Vec);
		doc2VecCachePanel.setVisible(doc2Vec);
		beginDateField.setEnabled(!running && !doc2Vec);
		endDateField.setEnabled(!running && !doc2Vec);
		if (algorithm == ExactPopulationSweepEngine.Algorithm.SIMILARITY)
		{
			algorithmDetailLabel.setText(
					"Global TF-IDF is built once; workers rank different complete target users.");
			outputTypeLabel.setText(
					"Output: similar users. Exact raw cosine scores are fingerprinted.");
		}
		else if (algorithm == ExactPopulationSweepEngine.Algorithm.SVM)
		{
			algorithmDetailLabel.setText(
					"Every target receives an independent deterministic SMO classification job.");
			outputTypeLabel.setText(
					"Output: field-0 followee accounts. Requires at least two classes.");
		}
		else
		{
			algorithmDetailLabel.setText(
					"One shared cached Doc2Vec model; workers score disjoint target-user groups.");
			outputTypeLabel.setText(
					"Output: similar users. The shared model uses the complete file; Top-K scores are verified.");
			}
			updateAlgorithmSettingsBadge();
			doc2VecCachePanel.revalidate();
		}

	private void openAlgorithmSettingsDialog()
	{
		if (owner == null)
		{
			return;
		}
		AlgorithmParameterSettings updated = AlgorithmSettingsDialog.showDialog(
				owner,
				selectedSimulatorAlgorithm(),
				owner.getAlgorithmParameterSettings());
		if (updated == null)
		{
			return;
		}
		owner.setAlgorithmParameterSettings(updated);
		updateAlgorithmSettingsBadge();
		refreshDoc2VecCacheStatus();
	}

	private int selectedSimulatorAlgorithm()
	{
		ExactPopulationSweepEngine.Algorithm algorithm =
				(ExactPopulationSweepEngine.Algorithm)algorithmBox.getSelectedItem();
		if (algorithm == ExactPopulationSweepEngine.Algorithm.SVM)
		{
			return RecommendationModeSupport.SVM;
		}
		if (algorithm == ExactPopulationSweepEngine.Algorithm.DOC2VEC)
		{
			return RecommendationModeSupport.DOC2VEC;
		}
		return RecommendationModeSupport.COS_SIM;
	}

	private void updateAlgorithmSettingsBadge()
	{
		if (owner == null)
		{
			return;
		}
		AlgorithmParameterSettings settings = owner.getAlgorithmParameterSettings();
		algorithmSettingsPill.setText(
				settings.isCustomEnabled() ? "CUSTOM" : "DEFAULT");
		algorithmSettingsPill.setHue(settings.isCustomEnabled()
				? AMBER : GREEN);
		algorithmSettingsButton.setToolTipText(
				settings.summaryForAlgorithm(selectedSimulatorAlgorithm()));
	}

	void refreshAlgorithmSettingsSummary()
	{
		updateAlgorithmSettingsBadge();
		refreshDoc2VecCacheStatus();
	}

	private String formatParameterErrors(List<String> errors)
	{
		StringBuilder builder = new StringBuilder("Fix these settings:");
		for (String error : errors)
		{
			builder.append("\n• ").append(error);
		}
		return builder.toString();
	}

	private void startRun()
	{
		if (runThread != null)
		{
			return;
		}
		if (datasetFile == null || !datasetFile.isFile())
		{
			setStatus(
					"DATASET NEEDED",
					"Choose a readable dataset before starting.",
					AMBER);
			return;
		}
			if (simulatorRunIsActive())
			{
				setStatus(
						"SIMULATOR BUSY",
						"Finish or reset the active simulator run before starting the isolated lab.",
						AMBER);
				return;
			}
			List<String> parameterErrors = owner.getAlgorithmParameterSettings()
					.validateForAlgorithm(selectedSimulatorAlgorithm());
			if (!parameterErrors.isEmpty())
			{
				setStatus(
						"SETTINGS NEEDED",
						"Fix advanced algorithm parameters before starting.",
						AMBER);
				NebulaDialogs.showWarning(
						this,
						formatParameterErrors(parameterErrors),
						"Invalid Algorithm Parameters");
				return;
			}

			final ExactPopulationSweepEngine.Config config =
				new ExactPopulationSweepEngine.Config(
						datasetFile,
						(ExactPopulationSweepEngine.Algorithm)
								algorithmBox.getSelectedItem(),
						((Number)nodesSpinner.getValue()).intValue(),
						((Number)topResultsSpinner.getValue()).intValue(),
						((Number)targetLimitSpinner.getValue()).intValue(),
						beginDateField.getText(),
						endDateField.getText(),
						removeHashtagsBox.isSelected(),
						removeRetweetsBox.isSelected(),
							removeStopWordsBox.isSelected(),
							verifyBox.isSelected(),
							System.getProperty(
									"recommender.python.command", "python3"),
							owner.getAlgorithmParameterSettings());
		final ExactPopulationSweepEngine.CancellationToken token =
				new ExactPopulationSweepEngine.CancellationToken();
		cancellationToken = token;
		resultsModel.clear();
		eventLog.setText("");
		clearMetrics();
		buildWorkerCards(config.nodes);
		overallProgress.setValue(0);
		overallProgress.setString("Preparing");
		setRunning(true);

		Thread thread = new Thread(new Runnable()
		{
			public void run()
			{
				try
				{
					final ExactPopulationSweepEngine.Report report =
							ExactPopulationSweepEngine.run(
									config,
									createListener(config.nodes),
									token);
					SwingUtilities.invokeLater(new Runnable()
					{
						public void run()
						{
							applyReport(report, config.nodes);
							finishRun();
						}
					});
				}
				catch (final InterruptedException exception)
				{
					SwingUtilities.invokeLater(new Runnable()
					{
						public void run()
						{
							setStatus(
									"CANCELLED",
									"Population sweep cancelled. No simulator state was changed.",
									AMBER);
							appendLog("Run cancelled.");
							finishRun();
						}
					});
				}
				catch (final Throwable failure)
				{
					SwingUtilities.invokeLater(new Runnable()
					{
						public void run()
						{
							verificationMetric.setText("FAILED");
							verificationMetric.setToken(RED);
							setStatus(
									"FAILED",
									failure.getMessage() == null
											? failure.getClass().getSimpleName()
											: failure.getMessage(),
									RED);
							appendLog(
									"FAILED: " + failure.getClass().getSimpleName()
											+ ": " + failure.getMessage());
							finishRun();
						}
					});
				}
			}
		}, "Exact-Population-Sweep");
		thread.setDaemon(true);
		runThread = thread;
		thread.start();
	}

	private static boolean simulatorRunIsActive()
	{
		NodeExecutionMonitor.Snapshot snapshot =
				NodeExecutionMonitor.snapshot();
		if (snapshot.activeCount() > 0)
		{
			return true;
		}
		if (snapshot.getRunStartedAt() <= 0L
				|| snapshot.getNodes().isEmpty())
		{
			return false;
		}
		int terminal = snapshot.count(NodeExecutionMonitor.State.COMPLETE)
				+ snapshot.count(NodeExecutionMonitor.State.FAILED)
				+ snapshot.count(NodeExecutionMonitor.State.STOPPED);
		return terminal < snapshot.getNodes().size();
	}

	private ExactPopulationSweepEngine.Listener createListener(
			final int requestedWorkers)
	{
		return new ExactPopulationSweepEngine.Listener()
		{
			public void onPhase(final String phase, final String detail)
			{
				onEdt(new Runnable()
				{
					public void run()
					{
						Color color = "COMPLETE".equals(phase)
								? GREEN
								: "VERIFYING".equals(phase) ? PURPLE : BLUE;
						setStatus(phase, detail, color);
						appendLog(phase + ": " + detail);
					}
				});
			}

			public void onProgress(
					final boolean baseline,
					final int completed,
					final int total)
			{
				onEdt(new Runnable()
				{
					public void run()
					{
						int percent = total <= 0 ? 0
								: (int)Math.round(completed * 100.0 / total);
						overallProgress.setValue(percent);
						overallProgress.setString(
								(baseline ? "Baseline " : "Parallel ")
										+ completed + " / " + total);
					}
				});
			}

			public void onWorker(
					final int workerIndex,
					final ExactPopulationSweepEngine.WorkerState state,
					final String target,
					final int completed,
					final int assigned,
					final long elapsedMillis,
					final String detail)
			{
				onEdt(new Runnable()
				{
					public void run()
					{
						WorkerCard card = workerCards.get(
								Integer.valueOf(workerIndex));
						if (card != null)
						{
							card.update(
									state,
									target,
									completed,
									assigned,
									elapsedMillis,
									detail);
						}
					}
				});
			}

			public void onTargetResult(
					final String target,
					final LinkedHashMap<String,Double> scores)
			{
				onEdt(new Runnable()
				{
					public void run()
					{
						resultsModel.addTarget(target, scores);
					}
				});
			}
		};
	}

	private void applyReport(
			ExactPopulationSweepEngine.Report report,
			int requestedWorkers)
	{
		baselineMetric.setText(report.isBaselineExecuted()
				? formatDuration(report.getBaselineMillis()) : "SKIPPED");
		parallelMetric.setText(formatDuration(report.getParallelMillis()));
		double speedup = report.getSpeedup();
		speedupMetric.setText(Double.isNaN(speedup)
				? "--" : String.format(Locale.US, "%.2fx", speedup));
		int activeWorkers = Math.max(1, report.getWorkers().size());
		efficiencyMetric.setText(Double.isNaN(speedup)
				? "--"
				: String.format(
						Locale.US,
						"%.0f%%",
						speedup / activeWorkers * 100.0));
		verificationMetric.setText(report.isVerified()
				? "EXACT MATCH"
				: report.isBaselineExecuted() ? "FAILED" : "NOT CHECKED");
		verificationMetric.setToken(
				report.isVerified() ? GREEN
						: report.isBaselineExecuted() ? RED : AMBER);
		String digest = report.getResultDigest();
		digestMetric.setText(digest.length() > 10
				? digest.substring(0, 10) : digest);
		digestMetric.setToolTipText(digest);
		for (int workerIndex = activeWorkers + 1;
				workerIndex <= requestedWorkers;
				workerIndex++)
		{
			WorkerCard idleCard = workerCards.get(
					Integer.valueOf(workerIndex));
			if (idleCard != null)
			{
				idleCard.update(
						ExactPopulationSweepEngine.WorkerState.COMPLETE,
						null,
						0,
						0,
						0L,
						"No target was assigned because the target count was smaller than the requested worker count.");
			}
		}
		overallProgress.setValue(100);
		overallProgress.setString(report.getTotalTargets() + " targets complete");
		appendLog(
				"Dataset SHA-256: " + report.getDatasetDigest());
		appendLog(
				"Result SHA-256: " + report.getResultDigest());
		appendLog(
				"Preparation: " + formatDuration(report.getPreparationMillis())
						+ ", baseline: "
						+ (report.isBaselineExecuted()
								? formatDuration(report.getBaselineMillis())
								: "skipped")
						+ ", parallel: "
						+ formatDuration(report.getParallelMillis()));
		if (algorithmBox.getSelectedItem()
				== ExactPopulationSweepEngine.Algorithm.DOC2VEC)
		{
			refreshDoc2VecCacheStatus();
		}
		setStatus(
				report.isVerified() ? "VERIFIED" : "COMPLETE",
				report.isVerified()
						? "All " + report.getTotalTargets()
								+ " target fingerprints match one-worker execution."
						: "Parallel population scoring completed without a baseline comparison.",
				report.isVerified() ? GREEN : BLUE);
	}

	private void finishRun()
	{
		setRunning(false);
		cancellationToken = null;
		runThread = null;
	}

	private void setRunning(boolean running)
	{
		this.running = running;
		algorithmBox.setEnabled(!running);
		nodesSpinner.setEnabled(!running);
		targetLimitSpinner.setEnabled(!running);
		topResultsSpinner.setEnabled(!running);
		beginDateField.setEnabled(!running);
		endDateField.setEnabled(!running);
		chooseDatasetButton.setEnabled(!running);
		useCurrentDatasetButton.setEnabled(!running);
		verifyBox.setEnabled(!running);
		runButton.setEnabled(!running);
			cancelButton.setEnabled(running);
			algorithmSettingsButton.setEnabled(!running);
			capacityButton.setEnabled(!running);
			refreshDoc2VecCacheButton.setEnabled(!running);
			removeDoc2VecCacheButton.setEnabled(
					!running
							&& currentDoc2VecCacheInfo != null
							&& currentDoc2VecCacheInfo.hasExactCache());
		updateAlgorithmContext();
	}

	private void refreshDoc2VecCacheStatus()
	{
		if (algorithmBox.getSelectedItem()
				!= ExactPopulationSweepEngine.Algorithm.DOC2VEC)
		{
			return;
		}
		final File selectedDataset = datasetFile;
		if (selectedDataset == null || !selectedDataset.isFile())
		{
			currentDoc2VecCacheInfo = null;
			doc2VecCacheRows.setText("--");
			doc2VecCacheUsers.setText("--");
			doc2VecCacheVectors.setText("--");
			doc2VecCacheSize.setText("--");
			doc2VecCacheKey.setText("--");
			applyDoc2VecCacheMessage(
					"CACHE UNKNOWN",
					"Choose a dataset to inspect whether training will run or be skipped.",
					MUTED);
			removeDoc2VecCacheButton.setEnabled(false);
			return;
		}

		final int request = ++cacheRefreshSerial;
		applyDoc2VecCacheMessage(
				"CHECKING",
				"Reading Doc2Vec metadata and dataset shape in the background.",
				BLUE);
		refreshDoc2VecCacheButton.setEnabled(false);
		removeDoc2VecCacheButton.setEnabled(false);
		Thread thread = new Thread(new Runnable()
		{
			public void run()
			{
				try
				{
					final Doc2VecCacheAdmin.CacheInfo info =
							Doc2VecCacheAdmin.inspect(
									System.getProperty(
											"recommender.python.command", "python3"),
									selectedDataset,
									Doc2VecCacheAdmin.trainingWorkers(),
									owner.getAlgorithmParameterSettings());
					onEdt(new Runnable()
					{
						public void run()
						{
							if (request == cacheRefreshSerial
									&& selectedDataset.equals(datasetFile)
									&& algorithmBox.getSelectedItem()
											== ExactPopulationSweepEngine.Algorithm.DOC2VEC)
							{
								applyDoc2VecCacheInfo(info);
							}
						}
					});
				}
				catch (final Throwable failure)
				{
					onEdt(new Runnable()
					{
						public void run()
						{
							if (request == cacheRefreshSerial
									&& selectedDataset.equals(datasetFile)
									&& algorithmBox.getSelectedItem()
											== ExactPopulationSweepEngine.Algorithm.DOC2VEC)
							{
								currentDoc2VecCacheInfo = null;
								applyDoc2VecCacheMessage(
										"CACHE CHECK FAILED",
										failure.getMessage() == null
												? failure.getClass().getSimpleName()
												: failure.getMessage(),
										RED);
								refreshDoc2VecCacheButton.setEnabled(!running);
								removeDoc2VecCacheButton.setEnabled(false);
							}
						}
					});
				}
			}
		}, "Doc2Vec-Cache-Inspector");
		thread.setDaemon(true);
		thread.start();
	}

	private void removeCurrentDoc2VecCache()
	{
		if (running || datasetFile == null || !datasetFile.isFile())
		{
			return;
		}
			Doc2VecCacheAdmin.CacheInfo info = currentDoc2VecCacheInfo;
			if (info == null || !info.hasExactCache())
		{
			return;
		}
		int answer = NebulaDialogs.showConfirm(
				this,
				"Remove the Doc2Vec cache for this selected dataset? "
						+ "The dataset file is not touched. The next Doc2Vec run "
						+ "will retrain the shared model before scoring.",
				"Remove Doc2Vec Cache",
				NebulaDialogs.TONE_WARNING);
		if (answer != JOptionPane.YES_OPTION)
		{
			return;
		}

		final File selectedDataset = datasetFile;
		final int request = ++cacheRefreshSerial;
		applyDoc2VecCacheMessage(
					"CLEARING CACHE",
					"Removing only Doc2Vec cache files tied to the selected dataset and parameter profile.",
					AMBER);
		refreshDoc2VecCacheButton.setEnabled(false);
		removeDoc2VecCacheButton.setEnabled(false);
		Thread thread = new Thread(new Runnable()
		{
			public void run()
			{
				try
				{
					final Doc2VecCacheAdmin.DeleteResult result =
							Doc2VecCacheAdmin.delete(
									System.getProperty(
											"recommender.python.command", "python3"),
									selectedDataset,
									Doc2VecCacheAdmin.trainingWorkers(),
									owner.getAlgorithmParameterSettings());
					onEdt(new Runnable()
					{
						public void run()
						{
							if (request == cacheRefreshSerial
									&& selectedDataset.equals(datasetFile)
									&& algorithmBox.getSelectedItem()
											== ExactPopulationSweepEngine.Algorithm.DOC2VEC)
							{
								appendLog(
										"DOC2VEC CACHE: removed "
												+ result.getDeletedFiles()
												+ " file(s), "
												+ formatBytes(result.getDeletedBytes())
												+ ".");
								applyDoc2VecCacheInfo(
										result.getCacheInfoAfterDelete());
								setStatus(
										"CACHE CLEARED",
										"Doc2Vec will retrain this dataset on the next run.",
										AMBER);
							}
						}
					});
				}
				catch (final Throwable failure)
				{
					onEdt(new Runnable()
					{
						public void run()
						{
							if (request == cacheRefreshSerial
									&& selectedDataset.equals(datasetFile)
									&& algorithmBox.getSelectedItem()
											== ExactPopulationSweepEngine.Algorithm.DOC2VEC)
							{
								applyDoc2VecCacheMessage(
										"CACHE CLEAR FAILED",
										failure.getMessage() == null
												? failure.getClass().getSimpleName()
												: failure.getMessage(),
										RED);
								refreshDoc2VecCacheButton.setEnabled(!running);
								removeDoc2VecCacheButton.setEnabled(
											currentDoc2VecCacheInfo != null
													&& currentDoc2VecCacheInfo
															.hasExactCache()
													&& !running);
							}
						}
					});
				}
			}
		}, "Doc2Vec-Cache-Delete");
		thread.setDaemon(true);
		thread.start();
	}

	private void applyDoc2VecCacheInfo(Doc2VecCacheAdmin.CacheInfo info)
	{
		currentDoc2VecCacheInfo = info;
		doc2VecCacheRows.setText(formatCount(info.getDatasetRows()));
		doc2VecCacheUsers.setText(formatCount(info.getDatasetUsers()));
		long vectors = info.getVectorRows() > 0L
				? info.getVectorRows() : info.getTagCount();
		doc2VecCacheVectors.setText(
				vectors > 0L
						? formatCount(vectors) + " x "
								+ Math.max(0L, info.getVectorDims())
						: "--");
		doc2VecCacheSize.setText(formatBytes(info.getDatasetCacheBytes()));
		String cacheKey = info.getCacheKey();
		doc2VecCacheKey.setText(cacheKey == null || cacheKey.length() == 0
				? "--"
				: cacheKey.length() > 10 ? cacheKey.substring(0, 10) : cacheKey);
		doc2VecCacheKey.setToolTipText(cacheKey);

		if (info.isWarm())
		{
			applyDoc2VecCacheMessage(
					"WARM CACHE",
						"Training will be skipped. The lab will load "
								+ formatCount(vectors)
								+ " cached user vectors for "
								+ owner.getAlgorithmParameterSettings()
										.summaryForAlgorithm(RecommendationModeSupport.DOC2VEC)
								+ ".",
						GREEN);
		}
		else if (info.hasAnyDatasetCache())
		{
				applyDoc2VecCacheMessage(
						"STALE CACHE",
						"Cache files exist for this dataset path, but not for the exact current file and Doc2Vec parameter signature. The next run will retrain.",
						AMBER);
		}
		else
		{
			applyDoc2VecCacheMessage(
					"COLD CACHE",
					"No Doc2Vec cache exists for this selected dataset. The next run will train first, then score.",
					BLUE);
		}
		refreshDoc2VecCacheButton.setEnabled(!running);
			removeDoc2VecCacheButton.setEnabled(
					!running && info.hasExactCache());
	}

	private void applyDoc2VecCacheMessage(
			String state,
			String detail,
			Color color)
	{
		doc2VecCachePill.setText(state);
		doc2VecCachePill.setHue(color);
		doc2VecCacheDetail.setText(detail);
		doc2VecCacheDetail.setToolTipText(detail);
	}

	/** Resolves a soft chip tint for the supplied hue in the active mode. */
	private static Color softTint(Color color)
	{
		return new Color(
				color.getRed(),
				color.getGreen(),
				color.getBlue(),
				StudioNightTheme.isNightMode() ? 46 : 30);
	}

	private void clearMetrics()
	{
		baselineMetric.setText("--");
		parallelMetric.setText("--");
		speedupMetric.setText("--");
		efficiencyMetric.setText("--");
		verificationMetric.setText("RUNNING");
		verificationMetric.setToken(BLUE);
		digestMetric.setText("--");
		digestMetric.setToolTipText(null);
	}

	/** Re-reads the node profiles (after the Node Lab changed them). */
	void refreshWorkerCards()
	{
		if (runThread == null)
		{
			buildWorkerCards(((Number)nodesSpinner.getValue()).intValue());
		}
	}

	private void buildWorkerCards(int requestedWorkers)
	{
		//Shows the simulator's node profiles for the workers that have one and
		//defaults for the rest, without resizing the simulator's cluster.
		java.util.List<NodeCapacityProfile> configured = NodeCapacityRegistry.snapshot();
		workerCards.clear();
		workerCardsPanel.removeAll();
		int columns = requestedWorkers <= 2 ? requestedWorkers
				: requestedWorkers <= 6 ? 3 : 4;
		workerCardsPanel.setLayout(new GridLayout(0, Math.max(1, columns), 12, 12));
		for (int index = 1; index <= requestedWorkers; index++)
		{
			WorkerCard card = new WorkerCard(
					index,
					index <= configured.size()
							? configured.get(index - 1).withNodeIndex(index)
							: NodeCapacityProfile.defaultProfile(
									index,
									requestedWorkers,
									RuntimeResourceSettings.currentMaxHeapMb()));
			workerCards.put(Integer.valueOf(index), card);
			workerCardsPanel.add(card);
		}
		workerCardsPanel.revalidate();
		workerCardsPanel.repaint();
	}

	private void setStatus(String status, String detail, Color color)
	{
		statusPill.setText(status);
		statusPill.setHue(color);
		statusLabel.setText(detail);
		statusLabel.setToolTipText(detail);
	}

	private void appendLog(String message)
	{
		eventLog.append(message + "\n");
		eventLog.setCaretPosition(eventLog.getDocument().getLength());
	}

	private static void onEdt(Runnable runnable)
	{
		if (SwingUtilities.isEventDispatchThread())
		{
			runnable.run();
		}
		else
		{
			SwingUtilities.invokeLater(runnable);
		}
	}

	private static JPanel surface(java.awt.LayoutManager layout)
	{
		JPanel panel = new JPanel(layout)
		{
			protected void paintComponent(Graphics graphics)
			{
				Graphics2D g2 = (Graphics2D) graphics.create();
				if (Boolean.TRUE.equals(getClientProperty("nebula.inset")))
				{
					NebulaDesign.prepare(g2);
					g2.setColor(NebulaDesign.currentSurfaceAlt());
					g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1,
							NebulaDesign.RADIUS_CONTROL, NebulaDesign.RADIUS_CONTROL);
					g2.setColor(NebulaDesign.currentBorderSubtle());
					g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1,
							NebulaDesign.RADIUS_CONTROL, NebulaDesign.RADIUS_CONTROL);
				}
				else
				{
					NebulaWidgets.paintCard(g2, 0, 0, getWidth(), getHeight(),
							NebulaDesign.RADIUS_CARD);
				}
				g2.dispose();
				super.paintComponent(graphics);
			}
		};
		panel.setOpaque(false);
		panel.setBackground(SURFACE);
		return panel;
	}

	private static PillLabel pillLabel(String text)
	{
		PillLabel label = new PillLabel(text);
		label.setFont(NebulaDesign.fontSemiBold(10f));
		label.setBorder(BorderFactory.createEmptyBorder(5, 12, 5, 12));
		label.setHue(MUTED);
		return label;
	}

	private static JPanel field(String label, JComponent component)
	{
		JPanel panel = new JPanel(new BorderLayout(0, 6));
		panel.setOpaque(false);
		panel.add(
				label(label, NebulaDesign.TEXT_SECONDARY, NebulaDesign.fontMedium(11.5f)),
				BorderLayout.NORTH);
		component.setPreferredSize(new Dimension(115, 36));
		styleInput(component);
		panel.add(component, BorderLayout.CENTER);
		return panel;
	}

	private static void styleInput(JComponent component)
	{
		component.setFont(NebulaDesign.fontBody(13f));
		if (component instanceof JTextField)
		{
			JTextField fieldComponent = (JTextField) component;
			fieldComponent.setOpaque(false);
			fieldComponent.setCaretColor(NebulaDesign.ACCENT);
			pinBorder(fieldComponent,
					BorderFactory.createEmptyBorder(7, 11, 7, 11));
		}
		else if (component instanceof JSpinner)
		{
			JSpinner spinner = (JSpinner) component;
			spinner.setUI(new NebulaSpinnerUI());
			spinner.setOpaque(false);
			pinBorder(spinner, BorderFactory.createEmptyBorder(1, 1, 1, 1));
			JComponent editor = spinner.getEditor();
			if (editor instanceof JSpinner.DefaultEditor)
			{
				JTextField editorField =
						((JSpinner.DefaultEditor) editor).getTextField();
				editorField.setFont(NebulaDesign.fontMono(12.5f));
				editorField.setOpaque(false);
				editorField.setForeground(TEXT);
				editorField.setCaretColor(NebulaDesign.ACCENT);
				editor.setOpaque(false);
				pinBorder(editorField,
						BorderFactory.createEmptyBorder(6, 10, 6, 6));
			}
		}
		else if (component instanceof JComboBox)
		{
			JComboBox<?> combo = (JComboBox<?>) component;
			combo.setUI(new NebulaComboBoxUI());
			combo.setOpaque(false);
			combo.setForeground(TEXT);
			combo.setRenderer(new ComboItemRenderer());
			pinBorder(combo, BorderFactory.createEmptyBorder(0, 8, 0, 0));
		}
	}

	/**
	 * Keeps a Nebula border pinned to a control even when an outer theme walk
	 * swaps borders while toggling day and night.
	 */
	private static void pinBorder(final JComponent component, final Border border)
	{
		component.setBorder(border);
		component.addPropertyChangeListener("border", new PropertyChangeListener()
		{
			public void propertyChange(PropertyChangeEvent event)
			{
				if (component.getBorder() != border)
				{
					component.setBorder(border);
				}
			}
		});
	}

	/** Rounded inset chrome shared by inputs so both modes match the spec. */
	private static void paintInputChrome(Graphics2D g2, JComponent component)
	{
		NebulaDesign.prepare(g2);
		int width = component.getWidth();
		int height = component.getHeight();
		g2.setColor(NebulaDesign.currentSurfaceAlt());
		g2.fillRoundRect(0, 0, width - 1, height - 1,
				NebulaDesign.RADIUS_CONTROL, NebulaDesign.RADIUS_CONTROL);
		g2.setColor(component.isFocusOwner()
				? NebulaDesign.currentAccent()
				: NebulaDesign.currentBorderSubtle());
		g2.drawRoundRect(0, 0, width - 1, height - 1,
				NebulaDesign.RADIUS_CONTROL, NebulaDesign.RADIUS_CONTROL);
	}

	private static void addControl(
			JPanel panel,
			JComponent component,
			int gridX,
			double weightX)
	{
		GridBagConstraints constraints = new GridBagConstraints();
		constraints.gridx = gridX;
		constraints.gridy = 0;
		constraints.weightx = weightX;
		constraints.fill = GridBagConstraints.HORIZONTAL;
		constraints.insets = new Insets(0, gridX == 0 ? 0 : 6, 0, 6);
		panel.add(component, constraints);
	}

	private static JPanel metricCard(
			String title,
			JLabel value,
			String detail,
			int hueIndex)
	{
		JPanel panel = surface(new BorderLayout(0, 5));
		panel.setBorder(BorderFactory.createEmptyBorder(12, 14, 13, 12));
		TokenLabel titleLabel = label(
				title.toUpperCase(Locale.US),
				NebulaDesign.TEXT_TERTIARY,
				NebulaDesign.fontSection());
		titleLabel.setIcon(new DotIcon(hueIndex));
		titleLabel.setIconTextGap(6);
		panel.add(titleLabel, BorderLayout.NORTH);
		panel.add(value, BorderLayout.CENTER);
		panel.add(
				label(detail, NebulaDesign.TEXT_TERTIARY, NebulaDesign.fontMedium(10f)),
				BorderLayout.SOUTH);
		return panel;
	}

	private static TokenLabel metricValue(String text)
	{
		return label(text, TEXT, NebulaDesign.fontSemiBold(18f));
	}

	private static JPanel statBlock(String title, TokenLabel value)
	{
		JPanel panel = new JPanel(new BorderLayout(0, 3));
		panel.setOpaque(false);
		panel.add(
				label(title, NebulaDesign.TEXT_TERTIARY, NebulaDesign.fontSmall()),
				BorderLayout.NORTH);
		value.setToken(TEXT);
		value.setFont(NebulaDesign.fontMono(12f));
		panel.add(value, BorderLayout.CENTER);
		return panel;
	}

	private static PillLabel badge(String text, Color color)
	{
		PillLabel label = pillLabel(text);
		label.setHue(color);
		return label;
	}

	/** Builds a label whose color stays pinned to a Nebula token. */
	private static TokenLabel label(String text, Color token, Font font)
	{
		TokenLabel label = new TokenLabel(text);
		label.setFont(font);
		label.setToken(token);
		return label;
	}

	/**
	 * Rounded well that hosts a table or console. The scroll pane paints the
	 * surface so the card behind it keeps clean rounded corners in both modes.
	 */
	private static JScrollPane well(
			final Component view,
			final boolean console,
			final String emptyHint)
	{
		JScrollPane scroll = new JScrollPane(view)
		{
			protected void paintComponent(Graphics graphics)
			{
				Graphics2D g2 = (Graphics2D) graphics.create();
				NebulaDesign.prepare(g2);
				int radius = console
						? NebulaDesign.RADIUS_CONSOLE : NebulaDesign.RADIUS_CONTROL;
				g2.setColor(console ? NebulaDesign.CONSOLE : NebulaDesign.SURFACE);
				g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius, radius);
				g2.setColor(NebulaDesign.BORDER_SUBTLE);
				g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius, radius);
				if (emptyHint != null && isEmptyView(view))
				{
					g2.setFont(NebulaDesign.fontBody(11.5f));
					g2.setColor(NebulaDesign.TEXT_TERTIARY);
					g2.drawString(emptyHint, 16, Math.min(getHeight() - 12, 48));
				}
				g2.dispose();
				super.paintComponent(graphics);
			}
		};
		scroll.setOpaque(false);
		scroll.getViewport().setOpaque(false);
		pinBorder(scroll, BorderFactory.createEmptyBorder(1, 1, 1, 1));
		scroll.getVerticalScrollBar().setUnitIncrement(16);
		return scroll;
	}

	private static boolean isEmptyView(Component view)
	{
		if (view instanceof JTable)
		{
			return ((JTable) view).getRowCount() == 0;
		}
		if (view instanceof JTextArea)
		{
			return ((JTextArea) view).getDocument().getLength() == 0;
		}
		return false;
	}

	/** Nebula table header: caps eyebrow text over a single strong hairline. */
	private static void styleTableHeader(final JTableHeader header)
	{
		if (header == null)
		{
			return;
		}
		header.setOpaque(false);
		header.setReorderingAllowed(false);
		header.setPreferredSize(new Dimension(80, 32));
		header.setFont(NebulaDesign.fontSection());
		header.setDefaultRenderer(new TableCellRenderer()
		{
			public Component getTableCellRendererComponent(
					JTable table,
					Object value,
					boolean isSelected,
					boolean hasFocus,
					int row,
					int column)
			{
				boolean numeric = table != null
						&& column == table.getColumnCount() - 1;
				return new HeaderCell(
						value == null ? "" : value.toString(), numeric);
			}
		});
	}

	/** Painted header cell so day and night both stay flat and modern. */
	private static final class HeaderCell extends JComponent
	{
		private final String caption;
		private final boolean trailing;

		HeaderCell(String caption, boolean trailing)
		{
			this.caption = caption;
			this.trailing = trailing;
		}

		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g2 = (Graphics2D) graphics.create();
			NebulaDesign.prepare(g2);
			g2.setColor(NebulaDesign.SURFACE);
			g2.fillRect(0, 0, getWidth(), getHeight());
			g2.setColor(NebulaDesign.BORDER_STRONG);
			g2.fillRect(0, getHeight() - 1, getWidth(), 1);
			g2.setFont(NebulaDesign.fontSection());
			g2.setColor(NebulaDesign.TEXT_TERTIARY);
			FontMetrics metrics = g2.getFontMetrics();
			String text = caption.toUpperCase(Locale.US);
			int textX = trailing
					? Math.max(6, getWidth() - metrics.stringWidth(text) - 6)
					: 12;
			g2.drawString(
					text,
					textX,
					(getHeight() - metrics.getHeight()) / 2 + metrics.getAscent());
			g2.dispose();
		}
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

	/** Status chip: soft hue tint plus strong hue label, resolved at paint. */
	private static final class PillLabel extends JLabel
	{
		private Color hue = NebulaDesign.TEXT_SECONDARY;

		PillLabel(String text)
		{
			super(text);
			setOpaque(false);
		}

		void setHue(Color hue)
		{
			this.hue = hue == null ? NebulaDesign.TEXT_SECONDARY : hue;
			repaint();
		}

		public Color getForeground()
		{
			return hue;
		}

		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g2 = (Graphics2D) graphics.create();
			NebulaDesign.prepare(g2);
			g2.setColor(softTint(hue));
			g2.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
			g2.setColor(NebulaDesign.withAlpha(hue, 70));
			g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1,
					getHeight(), getHeight());
			g2.dispose();
			super.paintComponent(graphics);
		}
	}

	/** Text field with rounded Nebula chrome that survives theme walks. */
	private static final class NebulaField extends JTextField
	{
		NebulaField(String text)
		{
			super(text);
			setOpaque(false);
		}

		public Color getBackground()
		{
			return NebulaDesign.SURFACE_ALT;
		}

		public Color getForeground()
		{
			return isEnabled() ? NebulaDesign.TEXT_PRIMARY : NebulaDesign.TEXT_TERTIARY;
		}

		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g2 = (Graphics2D) graphics.create();
			paintInputChrome(g2, this);
			g2.dispose();
			super.paintComponent(graphics);
		}
	}

	/** Console log surface that stays transparent so the well shows through. */
	private static final class ConsoleArea extends JTextArea
	{
		ConsoleArea()
		{
			setOpaque(false);
		}

		public boolean isOpaque()
		{
			return false;
		}

		public Color getBackground()
		{
			return NebulaDesign.CONSOLE;
		}

		public Color getForeground()
		{
			return NebulaDesign.CONSOLE_TEXT;
		}
	}

	/** Check box whose caption keeps the Nebula primary text token. */
	private static final class TokenCheckBox extends JCheckBox
	{
		TokenCheckBox(String text, boolean selected)
		{
			super(text, selected);
		}

		public Color getForeground()
		{
			return isEnabled() ? NebulaDesign.TEXT_PRIMARY : NebulaDesign.TEXT_TERTIARY;
		}
	}

	/** Combo box that keeps its Nebula border through theme walks. */
	private static final class PinnedComboBox<T> extends JComboBox<T>
	{
		PinnedComboBox(T[] items)
		{
			super(items);
		}

		public Color getForeground()
		{
			return isEnabled() ? NebulaDesign.TEXT_PRIMARY : NebulaDesign.TEXT_TERTIARY;
		}
	}

	/** Popup rows on surface with an accent-soft selection pill. */
	private static final class ComboItemRenderer extends DefaultListCellRenderer
	{
		public Component getListCellRendererComponent(
				JList<?> list,
				Object value,
				int index,
				boolean isSelected,
				boolean cellHasFocus)
		{
			super.getListCellRendererComponent(
					list, value, index, isSelected, cellHasFocus);
			setFont(NebulaDesign.fontBody(12.5f));
			setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
			if (index < 0)
			{
				setOpaque(false);
				setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 0));
			}
			else
			{
				setOpaque(true);
				setBackground(isSelected
						? NebulaDesign.currentSelection()
						: NebulaDesign.currentSurface());
			}
			setForeground(NebulaDesign.TEXT_PRIMARY);
			return this;
		}
	}

	/** Combo UI with rounded chrome and a hand-drawn chevron. */
	private static final class NebulaComboBoxUI extends BasicComboBoxUI
	{
		protected JButton createArrowButton()
		{
			JButton button = new JButton()
			{
				protected void paintComponent(Graphics graphics)
				{
					Graphics2D g2 = (Graphics2D) graphics.create();
					NebulaWidgets.paintChevron(
							g2,
							getWidth() / 2,
							getHeight() / 2 - 1,
							9,
							NebulaDesign.TEXT_SECONDARY);
					g2.dispose();
				}
			};
			button.setPreferredSize(new Dimension(26, 26));
			button.setContentAreaFilled(false);
			button.setBorderPainted(false);
			button.setOpaque(false);
			button.setFocusable(false);
			return button;
		}

		public void paint(Graphics graphics, JComponent component)
		{
			Graphics2D g2 = (Graphics2D) graphics.create();
			paintInputChrome(g2, component);
			g2.dispose();
			super.paint(graphics, component);
		}

		public void paintCurrentValueBackground(
				Graphics graphics, Rectangle bounds, boolean hasFocus)
		{
			// The rounded chrome already painted the field surface.
		}
	}

	/** Spinner UI with stacked Nebula chevrons instead of stock arrows. */
	private static final class NebulaSpinnerUI extends BasicSpinnerUI
	{
		protected Component createNextButton()
		{
			JButton button = chevronButton(true);
			installNextButtonListeners(button);
			return button;
		}

		protected Component createPreviousButton()
		{
			JButton button = chevronButton(false);
			installPreviousButtonListeners(button);
			return button;
		}

		public void paint(Graphics graphics, JComponent component)
		{
			Graphics2D g2 = (Graphics2D) graphics.create();
			paintInputChrome(g2, component);
			g2.dispose();
			super.paint(graphics, component);
		}

		private static JButton chevronButton(final boolean up)
		{
			JButton button = new JButton()
			{
				protected void paintComponent(Graphics graphics)
				{
					Graphics2D g2 = (Graphics2D) graphics.create();
					NebulaDesign.prepare(g2);
					int centerX = getWidth() / 2;
					int centerY = getHeight() / 2;
					g2.setColor(NebulaDesign.TEXT_SECONDARY);
					g2.setStroke(new BasicStroke(1.6f,
							BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
					int reach = 4;
					int lift = up ? 2 : -2;
					g2.drawLine(centerX - reach, centerY + lift,
							centerX, centerY - lift);
					g2.drawLine(centerX, centerY - lift,
							centerX + reach, centerY + lift);
					g2.dispose();
				}
			};
			button.setPreferredSize(new Dimension(22, 15));
			button.setContentAreaFilled(false);
			button.setBorderPainted(false);
			button.setOpaque(false);
			button.setFocusable(false);
			button.setCursor(java.awt.Cursor.getPredefinedCursor(
					java.awt.Cursor.HAND_CURSOR));
			return button;
		}
	}

	/**
	 * Pill progress meter: inset track, hue fill, and a mono caption that flips
	 * contrast across the fill boundary so it stays readable everywhere.
	 */
	private static final class NebulaProgressBarUI extends BasicProgressBarUI
	{
		private final boolean aurora;

		NebulaProgressBarUI(boolean aurora)
		{
			this.aurora = aurora;
		}

		protected void paintDeterminate(Graphics graphics, JComponent component)
		{
			paintMeter(graphics, progressBar.getPercentComplete());
		}

		protected void paintIndeterminate(Graphics graphics, JComponent component)
		{
			paintMeter(graphics, 0.0);
		}

		private void paintMeter(Graphics graphics, double fraction)
		{
			Graphics2D g2 = (Graphics2D) graphics.create();
			NebulaDesign.prepare(g2);
			int width = progressBar.getWidth();
			int height = Math.min(progressBar.getHeight(), 20);
			int top = (progressBar.getHeight() - height) / 2;
			g2.setColor(NebulaDesign.INSET);
			g2.fillRoundRect(0, top, width, height, height, height);
			g2.setColor(NebulaDesign.BORDER_SUBTLE);
			g2.drawRoundRect(0, top, width - 1, height - 1, height, height);
			Color hue = progressBar.getForeground();
			int filled = (int) Math.round(width * Math.max(0.0, Math.min(1.0, fraction)));
			if (filled > 0)
			{
				if (aurora)
				{
					g2.setPaint(new GradientPaint(
							0, top, hue, width, top, NebulaDesign.PURPLE));
				}
				else
				{
					g2.setColor(hue);
				}
				g2.fillRoundRect(0, top, Math.max(height, filled), height, height, height);
			}
			if (progressBar.isStringPainted())
			{
				String caption = progressBar.getString();
				if (caption != null && caption.length() > 0)
				{
					g2.setFont(progressBar.getFont());
					FontMetrics metrics = g2.getFontMetrics();
					int textX = Math.max(8, (width - metrics.stringWidth(caption)) / 2);
					int textY = top + (height - metrics.getHeight()) / 2
							+ metrics.getAscent();
					Shape clip = g2.getClip();
					g2.clipRect(0, 0, Math.max(height, filled), progressBar.getHeight());
					g2.setColor(NebulaDesign.TEXT_ON_ACCENT);
					g2.drawString(caption, textX, textY);
					g2.setClip(clip);
					g2.clipRect(Math.max(height, filled), 0, width, progressBar.getHeight());
					g2.setColor(NebulaDesign.TEXT_SECONDARY);
					g2.drawString(caption, textX, textY);
					g2.setClip(clip);
				}
			}
			g2.dispose();
		}
	}

	/** Small categorical hue dot for KPI tile eyebrows. */
	private static final class DotIcon implements Icon
	{
		private final int hueIndex;

		DotIcon(int hueIndex)
		{
			this.hueIndex = hueIndex;
		}

		public int getIconWidth()
		{
			return 8;
		}

		public int getIconHeight()
		{
			return 8;
		}

		public void paintIcon(Component component, Graphics graphics, int x, int y)
		{
			Graphics2D g2 = (Graphics2D) graphics.create();
			NebulaDesign.prepare(g2);
			g2.setColor(NebulaDesign.chartColor(hueIndex));
			g2.fillOval(x, y + 1, 7, 7);
			g2.dispose();
		}
	}

	/** Custom-drawn Nebula checkbox mark resolved per theme at paint time. */
	private static final class CheckIcon implements Icon
	{
		public int getIconWidth()
		{
			return 17;
		}

		public int getIconHeight()
		{
			return 17;
		}

		public void paintIcon(Component component, Graphics graphics, int x, int y)
		{
			Graphics2D g2 = (Graphics2D) graphics.create();
			NebulaDesign.prepare(g2);
			AbstractButton box = (AbstractButton) component;
			boolean selected = box.isSelected();
			boolean enabled = box.isEnabled();
			int size = 15;
			int top = y + 1;
			if (selected)
			{
				g2.setColor(enabled
						? NebulaDesign.currentAccent()
						: NebulaDesign.currentBorderStrong());
				g2.fillRoundRect(x, top, size, size, 5, 5);
				g2.setColor(StudioNightTheme.isNightMode()
						? NebulaDesign.NIGHT_TEXT_ON_ACCENT
						: Color.WHITE);
				g2.setStroke(new BasicStroke(1.8f,
						BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
				g2.drawLine(x + 3, top + 8, x + 6, top + 11);
				g2.drawLine(x + 6, top + 11, x + 11, top + 4);
			}
			else
			{
				g2.setColor(NebulaDesign.currentSurfaceAlt());
				g2.fillRoundRect(x, top, size, size, 5, 5);
				g2.setColor(enabled
						? NebulaDesign.currentBorderStrong()
						: NebulaDesign.currentBorderSubtle());
				g2.drawRoundRect(x, top, size, size, 5, 5);
			}
			g2.dispose();
		}
	}

	private static final class HeaderSignalPanel extends JPanel
	{
		private static final long serialVersionUID = 1L;
		private final Color primary;
		private final Color secondary;
		private final javax.swing.Timer timer;
		private float phase;

		HeaderSignalPanel(Color primary, Color secondary)
		{
			this.primary = primary;
			this.secondary = secondary;
			setOpaque(false);
			setPreferredSize(new Dimension(520, 74));
			timer = new javax.swing.Timer(16, new ActionListener() {
				public void actionPerformed(ActionEvent event)
				{
					phase += 0.018f;
					if (phase > 1.0f)
					{
						phase -= 1.0f;
					}
					repaint();
				}
			});
			timer.setCoalesce(true);
			addHierarchyListener(new HierarchyListener() {
				public void hierarchyChanged(HierarchyEvent event)
				{
					if ((event.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0)
					{
						updateAnimationState();
					}
				}
			});
		}

		public void addNotify()
		{
			super.addNotify();
			updateAnimationState();
		}

		public void removeNotify()
		{
			timer.stop();
			super.removeNotify();
		}

		private void updateAnimationState()
		{
			if (isShowing())
			{
				if (!timer.isRunning())
				{
					timer.start();
				}
			}
			else
			{
				timer.stop();
			}
		}

		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g2 = (Graphics2D) graphics.create();
			NebulaDesign.prepare(g2);
			int width = getWidth();
			int height = getHeight();
			int pad = 18;
			int[] xs = {
					pad,
					width / 5,
					width / 2 - 18,
					width / 2 + 42,
					width - width / 5,
					width - pad
			};
			int[] ys = new int[xs.length];
			for (int i = 0; i < xs.length; i++)
			{
				ys[i] = Math.round(height / 2.0f
						+ (float) Math.sin((phase + i * 0.17f) * Math.PI * 2.0f) * 14.0f);
			}

			g2.setComposite(AlphaComposite.SrcOver.derive(0.55f));
			g2.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			g2.setColor(new Color(primary.getRed(), primary.getGreen(), primary.getBlue(), 86));
			for (int i = 0; i < xs.length - 1; i++)
			{
				g2.drawLine(xs[i], ys[i], xs[i + 1], ys[i + 1]);
			}
			g2.setColor(new Color(secondary.getRed(), secondary.getGreen(), secondary.getBlue(), 70));
			g2.drawLine(xs[1], ys[1], xs[3], ys[3]);
			g2.drawLine(xs[2], ys[2], xs[4], ys[4]);

			g2.setComposite(AlphaComposite.SrcOver);
			for (int i = 0; i < xs.length; i++)
			{
				Color fill = i % 2 == 0 ? primary : secondary;
				g2.setColor(new Color(fill.getRed(), fill.getGreen(), fill.getBlue(), 42));
				g2.fillOval(xs[i] - 7, ys[i] - 7, 14, 14);
				g2.setColor(new Color(fill.getRed(), fill.getGreen(), fill.getBlue(), 160));
				g2.drawOval(xs[i] - 7, ys[i] - 7, 14, 14);
				g2.setColor(NebulaDesign.withAlpha(NebulaDesign.currentSurface(), 235));
				g2.fillOval(xs[i] - 3, ys[i] - 3, 6, 6);
			}
			g2.dispose();
			super.paintComponent(graphics);
		}
	}

	private static JButton createButton(String text, int tone)
	{
		return new NebulaButton(text, tone);
	}

	/**
	 * Self-painting Nebula button. It resolves every colour at paint time from
	 * the active theme, so it stays correct in both modes and never inherits
	 * stock Swing chrome.
	 */
	private static final class NebulaButton extends JButton
	{
		private final int tone;
		private boolean hover;

		NebulaButton(String text, int tone)
		{
			super(text);
			this.tone = tone;
			setFont(NebulaDesign.fontMedium(12.5f));
			setContentAreaFilled(false);
			setBorderPainted(false);
			setOpaque(false);
			setBorder(BorderFactory.createEmptyBorder(9, 16, 9, 16));
			NebulaWidgets.polishButton(this);
			addMouseListener(new MouseAdapter()
			{
				public void mouseEntered(MouseEvent event)
				{
					hover = true;
					repaint();
				}

				public void mouseExited(MouseEvent event)
				{
					hover = false;
					repaint();
				}
			});
		}

		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g2 = (Graphics2D) graphics.create();
			NebulaDesign.prepare(g2);
			boolean enabled = isEnabled();
			boolean pressed = getModel().isArmed() && getModel().isPressed();
			boolean lit = enabled && hover;
			Color fill;
			Color line;
			Color text;
			if (!enabled)
			{
				fill = NebulaDesign.currentSurfaceAlt();
				line = NebulaDesign.currentBorderSubtle();
				text = NebulaDesign.TEXT_TERTIARY;
			}
			else if (tone == BUTTON_PRIMARY)
			{
				fill = pressed ? NebulaDesign.ACCENT_PRESS
						: lit ? NebulaDesign.ACCENT_HOVER : NebulaDesign.ACCENT;
				line = fill;
				text = NebulaDesign.TEXT_ON_ACCENT;
			}
			else if (tone == BUTTON_SUCCESS)
			{
				fill = pressed ? GREEN.darker() : lit ? GREEN.brighter() : GREEN;
				line = fill;
				text = NebulaDesign.TEXT_ON_ACCENT;
			}
			else if (tone == BUTTON_DANGER)
			{
				fill = NebulaDesign.withAlpha(RED,
						pressed ? 92 : lit ? 68 : StudioNightTheme.isNightMode() ? 46 : 32);
				line = NebulaDesign.withAlpha(RED, lit ? 190 : 130);
				text = RED;
			}
			else
			{
				fill = lit || pressed
						? NebulaDesign.currentSurfaceAlt()
						: NebulaDesign.currentSurface();
				line = lit
						? NebulaDesign.withAlpha(NebulaDesign.currentAccent(), 130)
						: NebulaDesign.currentBorderStrong();
				text = NebulaDesign.TEXT_PRIMARY;
			}
			int width = getWidth();
			int height = getHeight();
			if (lit && (tone == BUTTON_PRIMARY || tone == BUTTON_SUCCESS))
			{
				g2.setColor(NebulaDesign.withAlpha(fill, 60));
				g2.fillRoundRect(0, 0, width - 1, height - 1,
						NebulaDesign.RADIUS_CONTROL + 3, NebulaDesign.RADIUS_CONTROL + 3);
			}
			g2.setColor(fill);
			g2.fillRoundRect(1, 1, width - 3, height - 3,
					NebulaDesign.RADIUS_CONTROL, NebulaDesign.RADIUS_CONTROL);
			g2.setColor(line);
			g2.drawRoundRect(1, 1, width - 3, height - 3,
					NebulaDesign.RADIUS_CONTROL, NebulaDesign.RADIUS_CONTROL);
			g2.setFont(getFont());
			FontMetrics metrics = g2.getFontMetrics();
			String caption = getText() == null ? "" : getText();
			g2.setColor(text);
			g2.drawString(
					caption,
					Math.max(6, (width - metrics.stringWidth(caption)) / 2),
					(height - metrics.getHeight()) / 2 + metrics.getAscent());
			g2.dispose();
		}
	}

	private static void styleCheckBox(JCheckBox checkBox)
	{
		checkBox.setOpaque(false);
		checkBox.setForeground(TEXT);
		checkBox.setFont(NebulaDesign.fontBody(12f));
		checkBox.setIcon(new CheckIcon());
		checkBox.setIconTextGap(7);
		NebulaWidgets.polishButton(checkBox);
	}

	private static String formatDuration(long milliseconds)
	{
		if (milliseconds < 1000L)
		{
			return milliseconds + " ms";
		}
		if (milliseconds < 60000L)
		{
			return String.format(Locale.US, "%.2f s", milliseconds / 1000.0);
		}
		long seconds = milliseconds / 1000L;
		return String.format(
				Locale.US,
				"%d:%02d",
				seconds / 60L,
				seconds % 60L);
	}

	private static String formatCount(long value)
	{
		return String.format(Locale.US, "%,d", Long.valueOf(Math.max(0L, value)));
	}

	private static String formatBytes(long bytes)
	{
		if (bytes < 1024L)
		{
			return bytes + " B";
		}
		double value = bytes;
		String[] units = {"B", "KB", "MB", "GB", "TB"};
		int unit = 0;
		while (value >= 1024.0 && unit < units.length - 1)
		{
			value /= 1024.0;
			unit++;
		}
		return String.format(Locale.US, "%.1f %s", value, units[unit]);
	}

	private static final class WorkerCard extends JPanel
	{
		private final TokenLabel stateLabel = new TokenLabel("READY");
		private final TokenLabel targetLabel = new TokenLabel("Waiting for run");
		private final TokenLabel detailLabel = new TokenLabel("No targets assigned yet.");
		private final TokenLabel elapsedLabel = new TokenLabel("0 ms");
		private final JProgressBar progress = new JProgressBar(0, 100);
		private Color stateColor = NebulaDesign.BORDER_STRONG;

		private WorkerCard(
				int workerIndex,
				NodeCapacityProfile profile)
		{
			super(new BorderLayout(0, 8));
			setOpaque(false);
			setBorder(BorderFactory.createEmptyBorder(11, 14, 12, 11));
			setPreferredSize(new Dimension(205, 142));

			JPanel top = new JPanel(new BorderLayout());
			top.setOpaque(false);
			JLabel workerLabel = label(
					"WORKER " + workerIndex,
					NebulaDesign.TEXT_TERTIARY,
					NebulaDesign.fontSection());
			stateLabel.setFont(NebulaDesign.fontSemiBold(10f));
			stateLabel.setToken(MUTED);
			top.add(workerLabel, BorderLayout.WEST);
			top.add(stateLabel, BorderLayout.EAST);

			JPanel center = new JPanel();
			center.setOpaque(false);
			center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
			targetLabel.setFont(NebulaDesign.fontSemiBold(12f));
			targetLabel.setToken(TEXT);
			detailLabel.setFont(NebulaDesign.fontBody(10f));
			detailLabel.setToken(MUTED);
			detailLabel.setVerticalAlignment(SwingConstants.TOP);
			if (profile != null)
			{
				stateLabel.setText(profile.shortLabel().toUpperCase(Locale.US));
				detailLabel.setText(
						"<html><body style='width:175px'>"
						+ escape(profile.compactSummary())
						+ "<br>Scheduling weight "
						+ String.format(
								Locale.US,
								"%.2f",
								NodeCapacityScheduler.schedulingWeight(profile))
						+ "</body></html>");
			}
			center.add(targetLabel);
			center.add(Box.createRigidArea(new Dimension(0, 4)));
			center.add(detailLabel);

			progress.setStringPainted(true);
			progress.setFont(NebulaDesign.fontMono(9.5f));
			progress.setForeground(BLUE);
			progress.setBackground(NebulaDesign.INSET);
			progress.setBorder(BorderFactory.createEmptyBorder());
			progress.setPreferredSize(new Dimension(90, 18));
			progress.setUI(new NebulaProgressBarUI(false));
			elapsedLabel.setToken(NebulaDesign.TEXT_TERTIARY);
			elapsedLabel.setFont(NebulaDesign.fontMono(9.5f));

			JPanel footer = new JPanel(new BorderLayout(8, 0));
			footer.setOpaque(false);
			footer.add(progress, BorderLayout.CENTER);
			footer.add(elapsedLabel, BorderLayout.EAST);

			add(top, BorderLayout.NORTH);
			add(center, BorderLayout.CENTER);
			add(footer, BorderLayout.SOUTH);
		}

		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g2 = (Graphics2D) graphics.create();
			NebulaWidgets.paintCard(g2, 0, 0, getWidth(), getHeight(), 12);
			g2.setColor(stateColor);
			g2.fillRoundRect(0, 12, 3, Math.max(6, getHeight() - 27), 3, 3);
			g2.dispose();
			super.paintComponent(graphics);
		}

		private void update(
				ExactPopulationSweepEngine.WorkerState state,
				String target,
				int completed,
				int assigned,
				long elapsedMillis,
				String detail)
		{
			Color color = state == ExactPopulationSweepEngine.WorkerState.COMPLETE
					? GREEN
					: state == ExactPopulationSweepEngine.WorkerState.FAILED
					? RED
					: state == ExactPopulationSweepEngine.WorkerState.CANCELLED
					? AMBER
					: state == ExactPopulationSweepEngine.WorkerState.RUNNING
					? BLUE : MUTED;
			stateColor = color;
			stateLabel.setText(state.toString());
			stateLabel.setToken(color);
			repaint();
			targetLabel.setText(target == null || target.length() == 0
					? state == ExactPopulationSweepEngine.WorkerState.COMPLETE
							? "Assignment complete" : "Preparing assignment"
					: target);
			detailLabel.setText(
					"<html><body style='width:175px'>"
							+ escape(detail) + "</body></html>");
			int percent = assigned <= 0 ? 0
					: (int)Math.round(completed * 100.0 / assigned);
			progress.setValue(percent);
			progress.setString(completed + " / " + assigned);
			progress.setForeground(color);
			elapsedLabel.setText(formatDuration(elapsedMillis));
		}
	}

	private static final class ResultsTableModel extends AbstractTableModel
	{
		private final String[] columns =
				{"Target", "Rank", "Recommendation", "Raw score"};
		private final List<ResultRow> rows = new ArrayList<ResultRow>();

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

		public Object getValueAt(int rowIndex, int columnIndex)
		{
			ResultRow row = rows.get(rowIndex);
			switch (columnIndex)
			{
				case 0:
					return row.target;
				case 1:
					return Integer.valueOf(row.rank);
				case 2:
					return row.candidate;
				case 3:
					return String.format(Locale.US, "%.8f", row.score);
				default:
					return "";
			}
		}

		private void addTarget(
				String target,
				LinkedHashMap<String,Double> scores)
		{
			int firstRow = rows.size();
			int rank = 1;
			for (Map.Entry<String,Double> entry : scores.entrySet())
			{
				rows.add(new ResultRow(
						target, rank++, entry.getKey(), entry.getValue()));
			}
			if (rows.size() > firstRow)
			{
				fireTableRowsInserted(firstRow, rows.size() - 1);
			}
		}

		private void clear()
		{
			int previousSize = rows.size();
			rows.clear();
			if (previousSize > 0)
			{
				fireTableRowsDeleted(0, previousSize - 1);
			}
		}
	}

	private static final class ResultRow
	{
		private final String target;
		private final int rank;
		private final String candidate;
		private final double score;

		private ResultRow(
				String target,
				int rank,
				String candidate,
				double score)
		{
			this.target = target;
			this.rank = rank;
			this.candidate = candidate;
			this.score = score;
		}
	}

	private static final class DatasetIcon extends JComponent
	{
		private DatasetIcon()
		{
			setPreferredSize(new Dimension(42, 42));
		}

		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g2 = (Graphics2D)graphics.create();
			NebulaDesign.prepare(g2);
			g2.setColor(StudioNightTheme.isNightMode()
					? NebulaDesign.NIGHT_ACCENT_SOFT
					: NebulaDesign.DAY_ACCENT_SOFT);
			g2.fillRoundRect(0, 0, 42, 42, 10, 10);
			g2.setColor(BLUE);
			g2.setStroke(new BasicStroke(1.8f));
			g2.drawRoundRect(10, 8, 22, 26, 3, 3);
			g2.drawLine(14, 15, 28, 15);
			g2.drawLine(14, 21, 28, 21);
			g2.drawLine(14, 27, 24, 27);
			g2.dispose();
		}
	}

	private static String escape(String value)
	{
		if (value == null)
		{
			return "";
		}
		return value.replace("&", "&amp;")
				.replace("<", "&lt;")
				.replace(">", "&gt;");
	}
}
