package TwitterGatherDataFollowers.userRyersonU;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
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
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;

/**
 * Full-page interface for the isolated exact population benchmark. It does not
 * mutate ControllerAgent state or participate in the existing JADE workflow.
 */
final class PopulationSweepPanel extends JPanel
{
	private static final Color BACKGROUND = StudioNightTheme.adaptiveColor(
			new Color(242, 246, 250), StudioNightTheme.NIGHT_BACKGROUND);
	private static final Color SURFACE = StudioNightTheme.adaptiveColor(
			Color.WHITE, StudioNightTheme.NIGHT_SURFACE);
	private static final Color SURFACE_ALT = StudioNightTheme.adaptiveColor(
			new Color(247, 250, 253), StudioNightTheme.NIGHT_SURFACE_ALT);
	private static final Color BORDER = StudioNightTheme.adaptiveColor(
			new Color(205, 215, 226), StudioNightTheme.NIGHT_BORDER);
	private static final Color TEXT = StudioNightTheme.adaptiveColor(
			new Color(24, 29, 36), StudioNightTheme.NIGHT_TEXT);
	private static final Color MUTED = StudioNightTheme.adaptiveColor(
			new Color(91, 101, 116), StudioNightTheme.NIGHT_MUTED);
	private static final Color BLUE = StudioNightTheme.adaptiveColor(
			new Color(0, 113, 227), StudioNightTheme.NIGHT_BLUE);
	private static final Color GREEN = StudioNightTheme.adaptiveColor(
			new Color(20, 150, 105), StudioNightTheme.NIGHT_GREEN);
	private static final Color AMBER = StudioNightTheme.adaptiveColor(
			new Color(190, 111, 22), StudioNightTheme.NIGHT_AMBER);
	private static final Color RED = StudioNightTheme.adaptiveColor(
			new Color(205, 55, 49), StudioNightTheme.NIGHT_RED);
	private static final Color PURPLE = StudioNightTheme.adaptiveColor(
			new Color(112, 77, 190), StudioNightTheme.NIGHT_PURPLE);

	private final ControllerAgentGui owner;
	private final JComboBox<ExactPopulationSweepEngine.Algorithm> algorithmBox =
			new JComboBox<ExactPopulationSweepEngine.Algorithm>(
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
	private final JTextField beginDateField = new JTextField("2007-01-01");
	private final JTextField endDateField = new JTextField("2099-12-31");
	private final JCheckBox removeHashtagsBox =
			new JCheckBox("Remove hashtags", true);
	private final JCheckBox removeRetweetsBox =
			new JCheckBox("Remove retweets", true);
	private final JCheckBox removeStopWordsBox =
			new JCheckBox("Remove stop words", true);
	private final JCheckBox verifyBox =
			new JCheckBox("Verify against one-worker baseline", true);
	private final JButton chooseDatasetButton =
			createButton("Choose Dataset", BLUE, Color.WHITE);
	private final JButton useCurrentDatasetButton =
			createButton("Use Simulator Dataset", SURFACE_ALT, TEXT);
	private final JButton runButton =
			createButton("Run Verified Sweep", GREEN, Color.WHITE);
	private final JButton cancelButton =
			createButton("Cancel", StudioNightTheme.adaptiveColor(
					new Color(244, 228, 227), StudioNightTheme.NIGHT_PANEL_INK), RED);
	private final JButton algorithmSettingsButton =
			createButton("Algorithm Settings", SURFACE_ALT, TEXT);
	private final JButton capacityButton =
			createButton("Node Capacity", SURFACE_ALT, TEXT);
	private final JLabel datasetNameLabel = new JLabel("No dataset selected");
	private final JLabel datasetPathLabel =
			new JLabel("Choose a six-column simulator dataset.");
	private final JLabel algorithmDetailLabel = new JLabel();
	private final JLabel outputTypeLabel = new JLabel();
	private final JLabel algorithmSettingsPill = badge("DEFAULT", GREEN);
	private final JPanel doc2VecCachePanel =
			surface(new BorderLayout(12, 0));
	private final JLabel doc2VecCachePill = new JLabel("CACHE UNKNOWN");
	private final JLabel doc2VecCacheTitle = new JLabel("Doc2Vec cache transparency");
	private final JLabel doc2VecCacheDetail =
			new JLabel("Choose a dataset to inspect whether training will run or be skipped.");
	private final JLabel doc2VecCacheRows = new JLabel("--");
	private final JLabel doc2VecCacheUsers = new JLabel("--");
	private final JLabel doc2VecCacheVectors = new JLabel("--");
	private final JLabel doc2VecCacheSize = new JLabel("--");
	private final JLabel doc2VecCacheKey = new JLabel("--");
	private final JButton refreshDoc2VecCacheButton =
			createButton("Refresh Cache", SURFACE_ALT, TEXT);
	private final JButton removeDoc2VecCacheButton =
			createButton("Remove This Cache", StudioNightTheme.adaptiveColor(
					new Color(244, 228, 227), StudioNightTheme.NIGHT_PANEL_INK), RED);
	private final JLabel statusPill = new JLabel("READY");
	private final JLabel statusLabel =
			new JLabel("Configure an exact population benchmark.");
	private final JLabel baselineMetric = metricValue("--");
	private final JLabel parallelMetric = metricValue("--");
	private final JLabel speedupMetric = metricValue("--");
	private final JLabel efficiencyMetric = metricValue("--");
	private final JLabel verificationMetric = metricValue("NOT RUN");
	private final JLabel digestMetric = metricValue("--");
	private final JProgressBar overallProgress = new JProgressBar(0, 100);
	private final JPanel workerCardsPanel =
			new JPanel(new GridLayout(0, 4, 12, 12));
	private final Map<Integer,WorkerCard> workerCards =
			new LinkedHashMap<Integer,WorkerCard>();
	private final ResultsTableModel resultsModel = new ResultsTableModel();
	private final JTable resultsTable = new JTable(resultsModel);
	private final JTextArea eventLog = new JTextArea();

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
		JPanel header = surface(new BorderLayout(24, 0));
		header.setBorder(BorderFactory.createEmptyBorder(16, 18, 16, 18));

		JPanel headerContent = new JPanel(new BorderLayout(28, 0));
		headerContent.setOpaque(false);

		JPanel titleStack = new JPanel();
		titleStack.setOpaque(false);
		titleStack.setLayout(new BoxLayout(titleStack, BoxLayout.Y_AXIS));
		JLabel title = new JLabel("Exact Multi-Node Population Lab");
		title.setFont(font(Font.BOLD, 25));
		title.setForeground(TEXT);
		JLabel subtitle = new JLabel(
				"Complete target jobs, one canonical dataset, automatic one-node equivalence checking.");
		subtitle.setFont(font(Font.PLAIN, 12));
		subtitle.setForeground(MUTED);
		titleStack.add(title);
		titleStack.add(Box.createRigidArea(new Dimension(0, 4)));
		titleStack.add(subtitle);
		titleStack.add(createAttributionBlock());

		headerContent.add(titleStack, BorderLayout.WEST);
		headerContent.add(new HeaderSignalPanel(BLUE, GREEN), BorderLayout.CENTER);
		header.add(headerContent, BorderLayout.CENTER);
		return header;
	}

	private static JPanel createAttributionBlock()
	{
		JPanel block = new JPanel();
		block.setOpaque(false);
		block.setLayout(new BoxLayout(block, BoxLayout.Y_AXIS));
		block.setAlignmentX(Component.LEFT_ALIGNMENT);
		block.setBorder(BorderFactory.createEmptyBorder(5, 1, 0, 0));
		block.add(createAttributionLine("Faculty Advisor", "Dr. Abdolreza Abhari"));
		block.add(Box.createRigidArea(new Dimension(0, 3)));
		block.add(createAttributionLine("Developer", "Mohsen Alavi"));
		return block;
	}

	private static JPanel createAttributionLine(String role, String name)
	{
		JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 7, 0));
		row.setOpaque(false);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel roleLabel = new JLabel(role.toUpperCase(Locale.US));
		roleLabel.setFont(font(Font.BOLD, 9));
		roleLabel.setForeground(MUTED);
		roleLabel.setBorder(BorderFactory.createEmptyBorder(1, 0, 0, 0));

		JLabel nameLabel = new JLabel(name);
		nameLabel.setFont(font(Font.BOLD, 12));
		nameLabel.setForeground(TEXT);

		row.add(roleLabel);
		row.add(nameLabel);
		return row;
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
		datasetNameLabel.setFont(font(Font.BOLD, 13));
		datasetNameLabel.setForeground(TEXT);
		datasetPathLabel.setFont(font(Font.PLAIN, 10));
		datasetPathLabel.setForeground(MUTED);
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

		JPanel context = new JPanel(new BorderLayout(12, 0));
		context.setBackground(new Color(242, 248, 255));
		context.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(new Color(187, 213, 240)),
				BorderFactory.createEmptyBorder(9, 11, 9, 11)));
		JPanel contextText = new JPanel();
		contextText.setOpaque(false);
		contextText.setLayout(new BoxLayout(contextText, BoxLayout.Y_AXIS));
		algorithmDetailLabel.setFont(font(Font.BOLD, 11));
		algorithmDetailLabel.setForeground(TEXT);
		outputTypeLabel.setFont(font(Font.PLAIN, 10));
		outputTypeLabel.setForeground(MUTED);
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
		doc2VecCachePanel.setBackground(SURFACE_ALT);
		doc2VecCachePanel.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(new Color(190, 218, 204)),
				BorderFactory.createEmptyBorder(10, 12, 10, 12)));

		JPanel textStack = new JPanel();
		textStack.setOpaque(false);
		textStack.setLayout(new BoxLayout(textStack, BoxLayout.Y_AXIS));
		JPanel heading = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
		heading.setOpaque(false);
		doc2VecCacheTitle.setFont(font(Font.BOLD, 12));
		doc2VecCacheTitle.setForeground(TEXT);
		doc2VecCachePill.setFont(font(Font.BOLD, 9));
		doc2VecCachePill.setHorizontalAlignment(SwingConstants.CENTER);
		doc2VecCachePill.setBorder(BorderFactory.createEmptyBorder(5, 7, 5, 7));
		heading.add(doc2VecCacheTitle);
		heading.add(doc2VecCachePill);
		doc2VecCacheDetail.setFont(font(Font.PLAIN, 10));
		doc2VecCacheDetail.setForeground(MUTED);
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
		JPanel metrics = new JPanel(new GridLayout(1, 6, 10, 0));
		metrics.setOpaque(false);
		metrics.add(metricCard("ONE WORKER", baselineMetric, "Measured baseline"));
		metrics.add(metricCard("MULTI WORKER", parallelMetric, "Parallel wall time"));
		metrics.add(metricCard("SPEEDUP", speedupMetric, "Baseline / parallel"));
		metrics.add(metricCard("EFFICIENCY", efficiencyMetric, "Speedup / workers"));
		metrics.add(metricCard("VERIFICATION", verificationMetric, "Result fingerprints"));
		metrics.add(metricCard("RESULT DIGEST", digestMetric, "Canonical SHA-256"));
		return metrics;
	}

	private JComponent createWorkersPanel()
	{
		JPanel panel = surface(new BorderLayout(0, 12));
		panel.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
		JPanel heading = new JPanel(new BorderLayout());
		heading.setOpaque(false);
		JLabel title = new JLabel("Parallel Worker Activity");
		title.setForeground(TEXT);
		title.setFont(font(Font.BOLD, 15));
		JLabel description = new JLabel(
				"Each card owns complete target users. No target is split between workers.");
		description.setForeground(MUTED);
		description.setFont(font(Font.PLAIN, 10));
		heading.add(title, BorderLayout.WEST);
		heading.add(description, BorderLayout.EAST);

		workerCardsPanel.setOpaque(false);
		JPanel holder = new JPanel(new BorderLayout());
		holder.setOpaque(false);
		holder.add(workerCardsPanel, BorderLayout.NORTH);
		JScrollPane scroll = new JScrollPane(holder);
		scroll.setBorder(null);
		scroll.getViewport().setBackground(SURFACE);
		scroll.getVerticalScrollBar().setUnitIncrement(16);

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
		JLabel title = new JLabel("Population Results");
		title.setFont(font(Font.BOLD, 15));
		title.setForeground(TEXT);
		JLabel detail = new JLabel("Raw model scores");
		detail.setFont(font(Font.BOLD, 10));
		detail.setForeground(BLUE);
		heading.add(title, BorderLayout.WEST);
		heading.add(detail, BorderLayout.EAST);

		resultsTable.setRowHeight(27);
		resultsTable.setFont(font(Font.PLAIN, 11));
		resultsTable.getTableHeader().setFont(font(Font.BOLD, 10));
		resultsTable.getTableHeader().setForeground(MUTED);
		resultsTable.setGridColor(new Color(229, 234, 240));
		resultsTable.setSelectionBackground(new Color(220, 238, 255));
		resultsTable.setFillsViewportHeight(true);
		resultsTable.getColumnModel().getColumn(1).setMaxWidth(50);
		resultsTable.getColumnModel().getColumn(3).setPreferredWidth(80);
		DefaultTableCellRenderer scoreRenderer = new DefaultTableCellRenderer();
		scoreRenderer.setHorizontalAlignment(SwingConstants.RIGHT);
		resultsTable.getColumnModel().getColumn(3).setCellRenderer(scoreRenderer);

		JScrollPane tableScroll = new JScrollPane(resultsTable);
		tableScroll.setBorder(BorderFactory.createLineBorder(BORDER));

		eventLog.setEditable(false);
		eventLog.setRows(4);
		eventLog.setFont(new Font("Monospaced", Font.PLAIN, 10));
		eventLog.setForeground(new Color(225, 235, 244));
		eventLog.setBackground(new Color(20, 26, 34));
		eventLog.setBorder(BorderFactory.createEmptyBorder(8, 9, 8, 9));
		JScrollPane logScroll = new JScrollPane(eventLog);
		logScroll.setBorder(null);
		logScroll.setPreferredSize(new Dimension(100, 92));

		panel.add(heading, BorderLayout.NORTH);
		panel.add(tableScroll, BorderLayout.CENTER);
		panel.add(logScroll, BorderLayout.SOUTH);
		return panel;
	}

	private JComponent createLimitationsPanel()
	{
		JPanel panel = surface(new BorderLayout(0, 4));
		panel.setBorder(BorderFactory.createEmptyBorder(11, 13, 11, 13));
		panel.setPreferredSize(new Dimension(100, 90));
		JLabel title = new JLabel("Why only three algorithms?");
		title.setFont(font(Font.BOLD, 11));
		title.setForeground(AMBER);
		JLabel text = new JLabel(
				"<html>Excluded because their current multi-node mathematics cannot guarantee one-node equivalence: "
				+ "K-Means, MLP, Euclidean K-Means, and Common Neighbors. They remain unchanged in Simulator.</html>");
		text.setFont(font(Font.PLAIN, 10));
		text.setForeground(MUTED);
		panel.add(title, BorderLayout.NORTH);
		panel.add(text, BorderLayout.CENTER);
		return panel;
	}

	private JComponent createStatusBar()
	{
		JPanel panel = surface(new BorderLayout(12, 0));
		panel.setBorder(BorderFactory.createEmptyBorder(9, 12, 9, 12));
		statusPill.setFont(font(Font.BOLD, 9));
		statusPill.setHorizontalAlignment(SwingConstants.CENTER);
		statusPill.setPreferredSize(new Dimension(82, 24));
		statusLabel.setFont(font(Font.BOLD, 11));
		statusLabel.setForeground(TEXT);
		overallProgress.setStringPainted(true);
		overallProgress.setFont(font(Font.BOLD, 9));
		overallProgress.setPreferredSize(new Dimension(260, 17));
		overallProgress.setForeground(BLUE);
		overallProgress.setBackground(new Color(228, 234, 241));
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
					int workers = ((Number)nodesSpinner.getValue()).intValue();
					NodeCapacitySettingsDialog.showDialog(
							SwingUtilities.getWindowAncestor(PopulationSweepPanel.this),
							workers,
							RuntimeResourceSettings.currentMaxHeapMb());
					buildWorkerCards(workers);
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
		algorithmSettingsPill.setForeground(settings.isCustomEnabled()
				? AMBER.darker() : GREEN.darker());
		algorithmSettingsPill.setBackground(settings.isCustomEnabled()
				? StudioNightTheme.adaptiveColor(
						new Color(255, 241, 210), StudioNightTheme.NIGHT_PANEL_INK)
				: StudioNightTheme.adaptiveColor(
						new Color(20, 150, 105, 24), StudioNightTheme.NIGHT_PANEL_INK));
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
		StringBuilder builder = new StringBuilder(
				"<html><b>Fix these settings:</b><br>");
		for (String error : errors)
		{
			builder.append("&bull; ").append(error).append("<br>");
		}
		builder.append("</html>");
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
				JOptionPane.showMessageDialog(
						this,
						formatParameterErrors(parameterErrors),
						"Invalid Algorithm Parameters",
						JOptionPane.WARNING_MESSAGE);
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
							verificationMetric.setForeground(RED);
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
		verificationMetric.setForeground(
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
		int answer = JOptionPane.showConfirmDialog(
				this,
				"<html><b>Remove Doc2Vec cache for this selected dataset?</b><br>"
						+ "The dataset file is not touched. The next Doc2Vec run "
						+ "will retrain the shared model before scoring.</html>",
				"Remove Doc2Vec Cache",
				JOptionPane.OK_CANCEL_OPTION,
				JOptionPane.WARNING_MESSAGE);
		if (answer != JOptionPane.OK_OPTION)
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
		doc2VecCachePill.setForeground(color.darker());
		doc2VecCachePill.setOpaque(true);
		doc2VecCachePill.setBackground(new Color(
				color.getRed(), color.getGreen(), color.getBlue(), 26));
		doc2VecCachePill.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(
						new Color(
								color.getRed(),
								color.getGreen(),
								color.getBlue(),
								95)),
				BorderFactory.createEmptyBorder(5, 7, 5, 7)));
		doc2VecCacheDetail.setText(detail);
		doc2VecCacheDetail.setToolTipText(detail);
	}

	private void clearMetrics()
	{
		baselineMetric.setText("--");
		parallelMetric.setText("--");
		speedupMetric.setText("--");
		efficiencyMetric.setText("--");
		verificationMetric.setText("RUNNING");
		verificationMetric.setForeground(BLUE);
		digestMetric.setText("--");
		digestMetric.setToolTipText(null);
	}

	private void buildWorkerCards(int requestedWorkers)
	{
		NodeCapacityRegistry.configureForNodeCount(
				requestedWorkers,
				RuntimeResourceSettings.currentMaxHeapMb());
		workerCards.clear();
		workerCardsPanel.removeAll();
		int columns = requestedWorkers <= 2 ? requestedWorkers
				: requestedWorkers <= 6 ? 3 : 4;
		workerCardsPanel.setLayout(new GridLayout(0, Math.max(1, columns), 12, 12));
		for (int index = 1; index <= requestedWorkers; index++)
		{
			WorkerCard card = new WorkerCard(
					index,
					NodeCapacityRegistry.profileForNode(index));
			workerCards.put(Integer.valueOf(index), card);
			workerCardsPanel.add(card);
		}
		workerCardsPanel.revalidate();
		workerCardsPanel.repaint();
	}

	private void setStatus(String status, String detail, Color color)
	{
		statusPill.setText(status);
		statusPill.setForeground(color.darker());
		statusPill.setBackground(new Color(
				color.getRed(), color.getGreen(), color.getBlue(), 28));
		statusPill.setOpaque(true);
		statusPill.setBorder(BorderFactory.createLineBorder(
				new Color(color.getRed(), color.getGreen(), color.getBlue(), 100)));
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
		JPanel panel = new JPanel(layout);
		panel.setBackground(SURFACE);
		panel.setBorder(BorderFactory.createLineBorder(BORDER));
		return panel;
	}

	private static JPanel field(String label, JComponent component)
	{
		JPanel panel = new JPanel(new BorderLayout(0, 5));
		panel.setOpaque(false);
		JLabel title = new JLabel(label);
		title.setForeground(MUTED);
		title.setFont(font(Font.BOLD, 10));
		panel.add(title, BorderLayout.NORTH);
		component.setPreferredSize(new Dimension(115, 34));
		panel.add(component, BorderLayout.CENTER);
		return panel;
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
			String detail)
	{
		JPanel panel = surface(new BorderLayout(0, 4));
		panel.setBorder(BorderFactory.createEmptyBorder(10, 11, 10, 11));
		JLabel titleLabel = new JLabel(title);
		titleLabel.setForeground(MUTED);
		titleLabel.setFont(font(Font.BOLD, 9));
		JLabel detailLabel = new JLabel(detail);
		detailLabel.setForeground(MUTED);
		detailLabel.setFont(font(Font.PLAIN, 9));
		panel.add(titleLabel, BorderLayout.NORTH);
		panel.add(value, BorderLayout.CENTER);
		panel.add(detailLabel, BorderLayout.SOUTH);
		return panel;
	}

	private static JLabel metricValue(String text)
	{
		JLabel label = new JLabel(text);
		label.setFont(font(Font.BOLD, 17));
		label.setForeground(TEXT);
		return label;
	}

	private static JPanel statBlock(String title, JLabel value)
	{
		JPanel panel = new JPanel(new BorderLayout(0, 2));
		panel.setOpaque(false);
		JLabel titleLabel = new JLabel(title);
		titleLabel.setForeground(MUTED);
		titleLabel.setFont(font(Font.BOLD, 9));
		value.setForeground(TEXT);
		value.setFont(font(Font.BOLD, 12));
		panel.add(titleLabel, BorderLayout.NORTH);
		panel.add(value, BorderLayout.CENTER);
		return panel;
	}

	private static JLabel badge(String text, Color color)
	{
		JLabel label = new JLabel(text);
		label.setFont(font(Font.BOLD, 9));
		label.setForeground(color.darker());
		label.setOpaque(true);
		label.setBackground(new Color(
				color.getRed(), color.getGreen(), color.getBlue(), 24));
		label.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(
						new Color(color.getRed(), color.getGreen(), color.getBlue(), 90)),
				BorderFactory.createEmptyBorder(6, 8, 6, 8)));
		return label;
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
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
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
				g2.setColor(new Color(255, 255, 255, 225));
				g2.fillOval(xs[i] - 3, ys[i] - 3, 6, 6);
			}
			g2.dispose();
			super.paintComponent(graphics);
		}
	}

	private static JButton createButton(
			String text,
			Color background,
			Color foreground)
	{
		JButton button = new JButton(text);
		button.setFont(font(Font.BOLD, 11));
		button.setForeground(foreground);
		button.setBackground(background);
		button.setFocusPainted(false);
		button.setOpaque(true);
		button.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(
						background.equals(BLUE) || background.equals(GREEN)
								? background.darker() : BORDER),
				BorderFactory.createEmptyBorder(8, 12, 8, 12)));
		button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		return button;
	}

	private static void styleCheckBox(JCheckBox checkBox)
	{
		checkBox.setOpaque(false);
		checkBox.setForeground(TEXT);
		checkBox.setFont(font(Font.BOLD, 10));
	}

	private static Font font(int style, int size)
	{
		return new Font("SansSerif", style, size);
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
		private final JLabel stateLabel = new JLabel("READY");
		private final JLabel targetLabel = new JLabel("Waiting for run");
		private final JLabel detailLabel = new JLabel("No targets assigned yet.");
		private final JLabel elapsedLabel = new JLabel("0 ms");
		private final JProgressBar progress = new JProgressBar(0, 100);

		private WorkerCard(
				int workerIndex,
				NodeCapacityProfile profile)
		{
			super(new BorderLayout(0, 8));
			setOpaque(true);
			setBackground(SURFACE_ALT);
			setBorder(BorderFactory.createCompoundBorder(
					BorderFactory.createLineBorder(BORDER),
					BorderFactory.createEmptyBorder(11, 11, 11, 11)));
			setPreferredSize(new Dimension(205, 142));

			JPanel top = new JPanel(new BorderLayout());
			top.setOpaque(false);
			JLabel workerLabel = new JLabel("WORKER " + workerIndex);
			workerLabel.setForeground(TEXT);
			workerLabel.setFont(font(Font.BOLD, 11));
			stateLabel.setFont(font(Font.BOLD, 9));
			stateLabel.setForeground(MUTED);
			top.add(workerLabel, BorderLayout.WEST);
			top.add(stateLabel, BorderLayout.EAST);

			JPanel center = new JPanel();
			center.setOpaque(false);
			center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
			targetLabel.setFont(font(Font.BOLD, 12));
			targetLabel.setForeground(TEXT);
			detailLabel.setFont(font(Font.PLAIN, 9));
			detailLabel.setForeground(MUTED);
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
			progress.setFont(font(Font.BOLD, 9));
			progress.setForeground(BLUE);
			progress.setBackground(new Color(226, 233, 241));
			elapsedLabel.setForeground(MUTED);
			elapsedLabel.setFont(font(Font.BOLD, 9));

			JPanel footer = new JPanel(new BorderLayout(8, 0));
			footer.setOpaque(false);
			footer.add(progress, BorderLayout.CENTER);
			footer.add(elapsedLabel, BorderLayout.EAST);

			add(top, BorderLayout.NORTH);
			add(center, BorderLayout.CENTER);
			add(footer, BorderLayout.SOUTH);
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
			stateLabel.setText(state.toString());
			stateLabel.setForeground(color.darker());
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
			g2.setRenderingHint(
					RenderingHints.KEY_ANTIALIASING,
					RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setColor(new Color(231, 243, 255));
			g2.fillRoundRect(0, 0, 42, 42, 8, 8);
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
