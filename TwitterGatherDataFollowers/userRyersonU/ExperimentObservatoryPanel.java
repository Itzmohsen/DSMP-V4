package TwitterGatherDataFollowers.userRyersonU;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

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
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.Scrollable;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;

final class ExperimentObservatoryPanel extends JPanel
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
	private static final DecimalFormat DECIMAL = new DecimalFormat("0.##");
	private static final long INITIALIZE_TIMEOUT_MINUTES = 60L;
	private static final long RUN_TIMEOUT_HOURS = 12L;

	private final ControllerAgentGui owner;
	private SimulatorHelpPanel.Snapshot snapshot = SimulatorHelpPanel.Snapshot.empty();
	private ExperimentObservatoryEngine.ExperimentDesign design =
			ExperimentObservatoryEngine.designFromSnapshot(snapshot);
	private ExperimentObservatoryEngine.ObservatoryReport report;
	private final Map<Integer, ChipCheckBox> algorithmChecks =
			new LinkedHashMap<Integer, ChipCheckBox>();
	private final Map<Integer, ChipCheckBox> nodeChecks =
			new LinkedHashMap<Integer, ChipCheckBox>();
	private final Map<String, ChipCheckBox> policyChecks =
			new LinkedHashMap<String, ChipCheckBox>();
	private final ChipCheckBox graphOn = new ChipCheckBox("Graph on", GREEN);
	private final ChipCheckBox graphOff = new ChipCheckBox("Graph off", BLUE);
	private final ChipCheckBox delayOn = new ChipCheckBox("Delay on", AMBER);
	private final ChipCheckBox delayOff = new ChipCheckBox("Delay off", GREEN);
	private final JTextField batchField = new JTextField("1");
	private final JSpinner repetitionSpinner =
			new JSpinner(new SpinnerNumberModel(1, 1, 25, 1));
	private final JLabel plannedRunsValue = metricValue("--");
	private final JLabel artifactValue = metricValue("--");
	private final JLabel reproducibilityValue = metricValue("--");
	private final JLabel runtimeValue = metricValue("--");
	private final JLabel capsuleValue = metricValue("--");
	private final JLabel datasetRead = new JLabel("No simulator snapshot yet");
	private Color statusTone = GREEN;
	private final JLabel statusPill = new JLabel("READY")
	{
		protected void paintComponent(Graphics g)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			NebulaDesign.prepare(g2);
			g2.setColor(NebulaDesign.withAlpha(statusTone,
					StudioNightTheme.isNightMode() ? 42 : 30));
			g2.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
			g2.dispose();
			super.paintComponent(g);
		}
	};
	private final JLabel statusText =
			new JLabel("Sync the Observatory from the simulator, then build a research matrix.");
	private final JLabel runnerCurrentValue = metricValue("idle");
	private final JLabel runnerCompletedValue = metricValue("0");
	private final JLabel runnerFailedValue = metricValue("0");
	private final JLabel runnerArtifactValue = metricValue("not started");
	private final JProgressBar runProgress = new JProgressBar(0, 100);
	private JButton runMatrixButton;
	private JButton stopMatrixButton;
	private final MatrixTableModel matrixTableModel = new MatrixTableModel();
	private final JTable matrixTable = new JTable(matrixTableModel);
	private final JTextArea observatoryLog = new JTextArea();
	private final MatrixBoard matrixBoard = new MatrixBoard();
	private final ComparisonBoard comparisonBoard = new ComparisonBoard();
	private final CapsuleBoard capsuleBoard = new CapsuleBoard();
	private final ArtifactBoard artifactBoard = new ArtifactBoard();
	private final ScalingInsightBoard scalingInsightBoard =
			new ScalingInsightBoard();
	private final JComboBox<String> scalingAlgorithmBox =
			new JComboBox<String>();
	private final JLabel scalingProfileLabel =
			label("Completed matrix archives will appear here.",
					NebulaDesign.fontBody(12f), MUTED);
	private final Timer headerTimer;
	private boolean updatingScalingAlgorithmBox;
	private boolean firstSync = true;
	private volatile boolean stopAfterCurrentRun;
	private ObservatoryMatrixRunner activeRunner;
	private ArrayList<RunExecutionState> runStates =
			new ArrayList<RunExecutionState>();
	private File activeRunDirectory;

	ExperimentObservatoryPanel(ControllerAgentGui owner)
	{
		super(new BorderLayout());
		this.owner = owner;
		setOpaque(true);
		setBackground(BACKGROUND);
		setBorder(new EmptyBorder(14, 14, 14, 14));
		ScrollablePanel content = new ScrollablePanel();
		content.setOpaque(false);
		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
		content.setBackground(BACKGROUND);
		content.add(createHeader());
		content.add(Box.createRigidArea(new Dimension(0, 12)));
		content.add(createDesigner());
		content.add(Box.createRigidArea(new Dimension(0, 12)));
		content.add(createRunControlPanel());
		content.add(Box.createRigidArea(new Dimension(0, 12)));
		content.add(createMetrics());
		content.add(Box.createRigidArea(new Dimension(0, 12)));
		content.add(createMainDashboard());
		content.add(Box.createRigidArea(new Dimension(0, 12)));
		content.add(createLowerDashboard());
		content.add(Box.createRigidArea(new Dimension(0, 12)));
		content.add(createScalingDashboard());
		content.add(Box.createRigidArea(new Dimension(0, 12)));
		content.add(createStatusBar());
		JScrollPane scroll = new JScrollPane(content);
		scroll.setBorder(BorderFactory.createEmptyBorder());
		scroll.setOpaque(false);
		scroll.getViewport().setOpaque(false);
		scroll.getVerticalScrollBar().setUnitIncrement(20);
		add(scroll, BorderLayout.CENTER);
		headerTimer = new Timer(64, new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				if (!isShowing())
				{
					return;
				}
				if (matrixBoard.isShowing())
				{
					matrixBoard.repaint();
				}
			}
		});
		headerTimer.start();
		refreshFromSimulator();
	}

	protected void paintComponent(Graphics g)
	{
		super.paintComponent(g);
		Graphics2D g2 = (Graphics2D) g.create();
		NebulaDesign.prepare(g2);
		g2.setPaint(new GradientPaint(
				0, 0, NebulaDesign.CANVAS_TOP,
				0, Math.max(1, getHeight()), NebulaDesign.CANVAS_BOTTOM));
		g2.fillRect(0, 0, getWidth(), getHeight());
		g2.dispose();
	}

	void refreshFromSimulator()
	{
		if (owner != null)
		{
			snapshot = owner.buildSimulatorHelpSnapshot();
		}
		if (firstSync)
		{
			syncControlsFromSnapshot();
			firstSync = false;
		}
		datasetRead.setText(snapshot.datasetSource + " | " + snapshot.datasetName
				+ " | " + snapshot.algorithmName + " | " + snapshot.modeLabel);
		rebuildDesign();
		updateVisuals();
	}

	private JPanel createHeader()
	{
		JPanel header = new HeroCard(new BorderLayout(18, 0));
		header.setBorder(new EmptyBorder(16, 22, 16, 22));
		JPanel title = transparentColumn();
		JLabel eyebrow = label("RESEARCH INSTRUMENT", NebulaDesign.fontSection(), BLUE);
		JLabel h1 = label("DSMP Experiment Observatory", NebulaDesign.fontDisplay(), TEXT);
		JLabel sub = label(
				"Design controlled benchmark suites, inspect real DSMP artifacts, and export reproducible research capsules.",
				NebulaDesign.fontBody(13f),
				MUTED);
		title.add(eyebrow);
		title.add(Box.createRigidArea(new Dimension(0, 4)));
		title.add(h1);
		title.add(Box.createRigidArea(new Dimension(0, 4)));
		title.add(sub);
		title.add(Box.createRigidArea(new Dimension(0, 6)));
		title.add(attribution());
		header.add(title, BorderLayout.WEST);
		header.add(new ObservatorySignal(), BorderLayout.CENTER);
		JPanel actions = transparentColumn();
		actions.setPreferredSize(new Dimension(196, 86));
		JButton sync = primaryButton("Sync from Simulator", BLUE);
		JButton analyze = primaryButton("Analyze Artifacts", GREEN);
		sync.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				syncControlsFromSnapshot();
				rebuildDesign();
				appendLog("Synchronized Observatory controls with the current simulator tab.");
				updateVisuals();
			}
		});
		analyze.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				analyzeArtifacts();
			}
		});
		actions.add(sync);
		actions.add(Box.createRigidArea(new Dimension(0, 8)));
		actions.add(analyze);
		header.add(actions, BorderLayout.EAST);
		return header;
	}

	private JPanel attribution()
	{
		JPanel panel = transparentColumn();
		panel.add(label("Faculty advisor Dr. Abdolreza Abhari  ·  Developer Mohsen Alavi",
				NebulaDesign.fontSmall(), NebulaDesign.TEXT_TERTIARY));
		return panel;
	}

	private JPanel createDesigner()
	{
		JPanel outer = card(new BorderLayout(12, 12));
		outer.setBorder(new EmptyBorder(16, 18, 16, 18));
		outer.add(sectionTitle("Experiment Designer",
				"Choose the real simulator runs to execute. Each matrix cell is cleaned, run, and archived independently."), BorderLayout.NORTH);
		JPanel grid = new JPanel(new GridBagLayout());
		grid.setOpaque(false);
		GridBagConstraints c = new GridBagConstraints();
		c.insets = new Insets(6, 6, 6, 6);
		c.fill = GridBagConstraints.BOTH;
		c.weightx = 1;
		c.gridy = 0;
		c.gridx = 0;
		grid.add(designBlock("Algorithms", createAlgorithmChoices()), c);
		c.gridx = 1;
		grid.add(designBlock("Node Counts", createNodeChoices()), c);
		c.gridx = 2;
		grid.add(designBlock("Scheduling Policies", createPolicyChoices()), c);
		c.gridy = 1;
		c.gridx = 0;
		grid.add(designBlock("Batch Sizes", batchPanel()), c);
		c.gridx = 1;
		grid.add(designBlock("Graph Output", graphPanel()), c);
		c.gridx = 2;
		grid.add(designBlock("Tweet Delay", delayPanel()), c);
		c.gridy = 2;
		c.gridx = 0;
		grid.add(designBlock("Repetitions", repetitionPanel()), c);
		outer.add(grid, BorderLayout.CENTER);
		JPanel buttons = new JPanel(new GridLayout(1, 3, 10, 0));
		buttons.setOpaque(false);
		JButton build = primaryButton("Build Matrix", BLUE);
		JButton export = primaryButton("Export Reproducibility Capsule", AMBER);
		JButton reset = secondaryButton("Reset To Current Run");
		build.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				rebuildDesign();
				appendLog("Built "
						+ ExperimentObservatoryEngine.buildMatrix(design).size()
						+ " planned run(s) from the selected matrix.");
				updateVisuals();
			}
		});
		export.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				exportCapsule();
			}
		});
		reset.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				syncControlsFromSnapshot();
				rebuildDesign();
				appendLog("Designer reset to the current simulator configuration.");
				updateVisuals();
			}
		});
		buttons.add(build);
		buttons.add(export);
		buttons.add(reset);
		outer.add(buttons, BorderLayout.SOUTH);
		return outer;
	}

	private JPanel createRunControlPanel()
	{
		JPanel outer = card(new BorderLayout(12, 12));
		outer.setBorder(new EmptyBorder(16, 18, 16, 18));
		outer.add(sectionTitle("Automated Matrix Runner",
				"Runs the selected matrix through the Simulator one experiment at a time, with clean artifacts and a saved capsule for each cell."), BorderLayout.NORTH);

		JPanel center = new JPanel(new GridBagLayout());
		center.setOpaque(false);
		GridBagConstraints c = new GridBagConstraints();
		c.insets = new Insets(6, 6, 6, 6);
		c.fill = GridBagConstraints.BOTH;
		c.weightx = 1;
		c.gridy = 0;
		c.gridx = 0;
		center.add(metric("Current run", runnerCurrentValue,
				"algorithm / nodes / batch / policy"), c);
		c.gridx = 1;
		center.add(metric("Completed", runnerCompletedValue,
				"finished matrix cells"), c);
		c.gridx = 2;
		center.add(metric("Failed", runnerFailedValue,
				"blocked or failed cells"), c);
		c.gridx = 3;
		center.add(metric("Run archive", runnerArtifactValue,
				"independent output folder"), c);

		runProgress.setStringPainted(true);
		runProgress.setString("0 / 0 runs complete");
		runProgress.setForeground(GREEN);
		runProgress.setBackground(NebulaDesign.INSET);
		runProgress.setOpaque(false);
		runProgress.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
		runProgress.setUI(new NebulaProgressUI());
		runProgress.setPreferredSize(new Dimension(140, 32));
		c.gridy = 1;
		c.gridx = 0;
		c.gridwidth = 4;
		c.weighty = 0;
		center.add(runProgress, c);

		JPanel buttons = new JPanel(new GridLayout(1, 2, 10, 0));
		buttons.setOpaque(false);
		runMatrixButton = primaryButton("Run Full Matrix", GREEN);
		stopMatrixButton = primaryButton("Stop After Current Run", RED);
		stopMatrixButton.setEnabled(false);
		runMatrixButton.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				runMatrix();
			}
		});
		stopMatrixButton.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				stopAfterCurrentRun = true;
				appendLog("Stop requested. The active simulator run will finish safely, then the matrix runner will stop.");
				setStatus("STOPPING", "The active run is being allowed to finish before the matrix stops.", AMBER);
				stopMatrixButton.setEnabled(false);
			}
		});
		buttons.add(runMatrixButton);
		buttons.add(stopMatrixButton);
		c.gridy = 2;
		c.gridx = 0;
		c.gridwidth = 4;
		center.add(buttons, c);

		outer.add(center, BorderLayout.CENTER);
		return outer;
	}

	private JComponent createAlgorithmChoices()
	{
		JPanel panel = new JPanel(new GridLayout(3, 3, 8, 8));
		panel.setOpaque(false);
		addAlgorithm(panel, RecommendationModeSupport.COS_SIM, "Similarity");
		addAlgorithm(panel, RecommendationModeSupport.K_MEANS, "K-Means");
		addAlgorithm(panel, RecommendationModeSupport.SVM, "SVM");
		addAlgorithm(panel, RecommendationModeSupport.MLP, "MLP");
		addAlgorithm(panel, RecommendationModeSupport.DOC2VEC, "Doc2Vec");
		addAlgorithm(panel, RecommendationModeSupport.COMMON_NEIGHBORS, "Common");
		addAlgorithm(panel, RecommendationModeSupport.K_MEANS_EUCLIDEAN, "Euclidean");
		addAlgorithm(panel, RecommendationModeSupport.SCALABLE_SVM, "Scalable SVM");
		addAlgorithm(panel, RecommendationModeSupport.TEMPORAL_GRAPH_FOUNDATION, "TGF");
		return panel;
	}

	private void addAlgorithm(JPanel panel, int algorithm, String label)
	{
		ChipCheckBox chip = new ChipCheckBox(label, colorForAlgorithm(algorithm));
		algorithmChecks.put(Integer.valueOf(algorithm), chip);
		panel.add(chip);
	}

	private JComponent createNodeChoices()
	{
		JPanel panel = new JPanel(new GridLayout(1, 5, 8, 8));
		panel.setOpaque(false);
		int[] values = new int[] {1, 2, 4, 8, 16};
		for (int value : values)
		{
			ChipCheckBox chip = new ChipCheckBox("×" + value, BLUE);
			chip.setToolTipText(value + " node" + (value == 1 ? "" : "s"));
			nodeChecks.put(Integer.valueOf(value), chip);
			panel.add(chip);
		}
		return panel;
	}

	private JComponent createPolicyChoices()
	{
		JPanel panel = new JPanel(new GridLayout(2, 2, 8, 8));
		panel.setOpaque(false);
		addPolicy(panel, "DSMP default", BLUE);
		addPolicy(panel, "Capacity-weighted", GREEN);
		addPolicy(panel, "Weighted round robin", PURPLE);
		addPolicy(panel, "Memory-aware safe-wave", AMBER);
		return panel;
	}

	private void addPolicy(JPanel panel, String value, Color color)
	{
		ChipCheckBox chip = new ChipCheckBox(value, color);
		policyChecks.put(value, chip);
		panel.add(chip);
	}

	private JPanel batchPanel()
	{
		JPanel panel = transparentColumn();
		batchField.setFont(NebulaDesign.fontBody(13f));
		batchField.setForeground(TEXT);
		batchField.setBackground(SURFACE_ALT);
		batchField.setCaretColor(BLUE);
		batchField.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(BORDER),
				new EmptyBorder(8, 10, 8, 10)));
		panel.add(batchField);
		panel.add(Box.createRigidArea(new Dimension(0, 6)));
		panel.add(label("Comma separated, for example: 1, 5, 20",
				NebulaDesign.fontSmall(), NebulaDesign.TEXT_TERTIARY));
		return panel;
	}

	private JPanel graphPanel()
	{
		JPanel panel = new JPanel(new GridLayout(1, 2, 8, 0));
		panel.setOpaque(false);
		panel.add(graphOn);
		panel.add(graphOff);
		return panel;
	}

	private JPanel delayPanel()
	{
		JPanel panel = transparentColumn();
		JPanel choices = new JPanel(new GridLayout(1, 2, 8, 0));
		choices.setOpaque(false);
		choices.add(delayOn);
		choices.add(delayOff);
		panel.add(choices);
		panel.add(Box.createRigidArea(new Dimension(0, 6)));
		panel.add(label("Off skips the artificial MobileAgent wait.",
				NebulaDesign.fontSmall(), NebulaDesign.TEXT_TERTIARY));
		return panel;
	}

	private JPanel repetitionPanel()
	{
		JPanel panel = transparentColumn();
		repetitionSpinner.setUI(new NebulaSpinnerUI());
		repetitionSpinner.setFont(NebulaDesign.fontBody(13f));
		repetitionSpinner.setBorder(BorderFactory.createLineBorder(BORDER));
		repetitionSpinner.setOpaque(false);
		JComponent editor = repetitionSpinner.getEditor();
		if (editor instanceof JSpinner.DefaultEditor)
		{
			JTextField field = ((JSpinner.DefaultEditor) editor).getTextField();
			field.setFont(NebulaDesign.fontBody(13f));
			field.setForeground(TEXT);
			field.setBackground(SURFACE_ALT);
			field.setCaretColor(BLUE);
			field.setBorder(new EmptyBorder(7, 10, 7, 10));
			field.setHorizontalAlignment(JTextField.LEFT);
		}
		panel.add(repetitionSpinner);
		panel.add(Box.createRigidArea(new Dimension(0, 6)));
		panel.add(label("Repeated runs reveal stability and drift.",
				NebulaDesign.fontSmall(), NebulaDesign.TEXT_TERTIARY));
		return panel;
	}

	private JPanel designBlock(String title, JComponent content)
	{
		JPanel block = card(new BorderLayout(8, 10));
		block.setBorder(new EmptyBorder(14, 14, 14, 14));
		block.add(label(title.toUpperCase(Locale.ROOT),
				NebulaDesign.fontSection(), NebulaDesign.TEXT_TERTIARY), BorderLayout.NORTH);
		block.add(content, BorderLayout.CENTER);
		return block;
	}

	private JPanel createMetrics()
	{
		JPanel metrics = new JPanel(new GridLayout(1, 5, 10, 0));
		metrics.setOpaque(false);
		metrics.add(metric("PLANNED RUNS", plannedRunsValue, "matrix size"));
		metrics.add(metric("ARTIFACTS", artifactValue, "timing / graph / rec files"));
		metrics.add(metric("ARTIFACT COVERAGE", reproducibilityValue, "expected files present"));
		metrics.add(metric("LATEST RUNTIME", runtimeValue, "from resultsTiming.txt"));
		metrics.add(metric("CAPSULE", capsuleValue, "research fingerprint"));
		return metrics;
	}

	private JPanel createMainDashboard()
	{
		JPanel grid = new JPanel(new GridBagLayout());
		grid.setOpaque(false);
		GridBagConstraints c = new GridBagConstraints();
		c.insets = new Insets(0, 0, 0, 10);
		c.fill = GridBagConstraints.BOTH;
		c.weightx = 0.52;
		c.weighty = 1;
		c.gridx = 0;
		grid.add(boardCard("Live Experiment Board",
				"Each tile is one controlled run spec. The simulator still performs the run; this board stores the research contract.",
				matrixBoard), c);
		c.insets = new Insets(0, 0, 0, 0);
		c.gridx = 1;
		c.weightx = 0.48;
		grid.add(boardCard("Comparison Dashboard",
				"Reads saved DSMP artifacts and summarizes speed, stability, graph coverage, and bottlenecks.",
				comparisonBoard), c);
		return grid;
	}

	private JPanel createLowerDashboard()
	{
		JPanel grid = new JPanel(new GridBagLayout());
		grid.setOpaque(false);
		GridBagConstraints c = new GridBagConstraints();
		c.insets = new Insets(0, 0, 0, 10);
		c.fill = GridBagConstraints.BOTH;
		c.weightx = 0.44;
		c.gridx = 0;
		grid.add(createRunTable(), c);
		c.gridx = 1;
		c.weightx = 0.28;
		grid.add(wrappedBoardCard("Reproducibility Capsule",
				"Dataset hash, code/environment signature, and artifact inventory for paper-ready reruns.",
				capsuleBoard), c);
		c.insets = new Insets(0, 0, 0, 0);
		c.gridx = 2;
		c.weightx = 0.28;
		grid.add(wrappedBoardCard("Artifact Scanner",
				"Shows exactly what evidence was found on disk. Missing artifacts are reported, not guessed.",
				artifactBoard), c);
		return grid;
	}

	private JPanel wrappedBoardCard(String title, String subtitle, JComponent content)
	{
		JPanel outer = card(new BorderLayout(8, 8));
		outer.setBorder(new EmptyBorder(14, 14, 14, 14));
		outer.add(sectionTitle(title, subtitle, 300), BorderLayout.NORTH);
		outer.add(content, BorderLayout.CENTER);
		return outer;
	}

	private JPanel createScalingDashboard()
	{
		JPanel outer = card(new BorderLayout(12, 12));
		outer.setBorder(new EmptyBorder(14, 14, 14, 14));
		JPanel header = new JPanel(new BorderLayout(12, 0));
		header.setOpaque(false);
		header.add(sectionTitle("Node Scaling Lens",
				"Compares completed matrix archives for one algorithm across node counts. Missing cells are skipped."),
				BorderLayout.CENTER);

		JPanel controls = new JPanel(new BorderLayout(8, 4));
		controls.setOpaque(false);
		JLabel chooserLabel = label("ALGORITHM",
				NebulaDesign.fontSection(), NebulaDesign.TEXT_TERTIARY);
		scalingAlgorithmBox.setFont(NebulaDesign.fontMedium(12f));
		scalingAlgorithmBox.setForeground(TEXT);
		scalingAlgorithmBox.setBackground(SURFACE_ALT);
		scalingAlgorithmBox.setUI(new NebulaComboUI());
		scalingAlgorithmBox.setOpaque(false);
		scalingAlgorithmBox.setBorder(new EmptyBorder(1, 10, 1, 2));
		scalingAlgorithmBox.setPreferredSize(new Dimension(220, 34));
		scalingAlgorithmBox.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		scalingAlgorithmBox.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				if (updatingScalingAlgorithmBox)
				{
					return;
				}
				refreshScalingInsight();
			}
		});
		controls.add(chooserLabel, BorderLayout.NORTH);
		controls.add(scalingAlgorithmBox, BorderLayout.CENTER);
		header.add(controls, BorderLayout.EAST);

		outer.add(header, BorderLayout.NORTH);
		outer.add(scalingInsightBoard, BorderLayout.CENTER);
		outer.add(scalingProfileLabel, BorderLayout.SOUTH);
		return outer;
	}

	private JPanel createRunTable()
	{
		JPanel outer = card(new BorderLayout(8, 8));
		outer.setBorder(new EmptyBorder(14, 14, 14, 14));
		outer.add(sectionTitle("Run Matrix", "Paper-ready run list generated from the designer."), BorderLayout.NORTH);
		matrixTable.setFont(NebulaDesign.fontBody(12f));
		matrixTable.setRowHeight(30);
		matrixTable.setShowGrid(false);
		matrixTable.setIntercellSpacing(new Dimension(0, 0));
		matrixTable.setBackground(SURFACE);
		matrixTable.setForeground(TEXT);
		matrixTable.setSelectionBackground(NebulaDesign.SELECTION);
		matrixTable.setSelectionForeground(TEXT);
		matrixTable.setFillsViewportHeight(true);
		matrixTable.getTableHeader().setFont(NebulaDesign.fontMedium(11f));
		matrixTable.getTableHeader().setForeground(MUTED);
		matrixTable.getTableHeader().setBackground(NebulaDesign.SURFACE_RAISED);
		matrixTable.getTableHeader().setDefaultRenderer(new HeaderCellRenderer());
		matrixTable.setDefaultRenderer(Object.class, new SoftCellRenderer());
		matrixTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
		int[] columnWidths = {96, 82, 108, 52, 52, 118, 52, 52, 56, 72, 118};
		for (int i = 0; i < columnWidths.length
				&& i < matrixTable.getColumnModel().getColumnCount(); i++)
		{
			matrixTable.getColumnModel().getColumn(i)
					.setPreferredWidth(columnWidths[i]);
		}
		JScrollPane scroll = new JScrollPane(matrixTable);
		scroll.setColumnHeaderView(matrixTable.getTableHeader());
		scroll.setPreferredSize(new Dimension(560, 260));
		scroll.setBorder(BorderFactory.createLineBorder(BORDER));
		scroll.getViewport().setBackground(SURFACE);
		scroll.setBackground(SURFACE);
		outer.add(scroll, BorderLayout.CENTER);
		return outer;
	}

	private JPanel createStatusBar()
	{
		JPanel status = card(new BorderLayout(12, 0));
		status.setBorder(new EmptyBorder(10, 14, 10, 14));
		statusPill.setOpaque(false);
		statusPill.setFont(NebulaDesign.fontSmall());
		statusPill.setForeground(GREEN);
		statusPill.setBorder(new EmptyBorder(5, 12, 5, 12));
		status.add(statusPill, BorderLayout.WEST);
		statusText.setFont(NebulaDesign.fontBody(12.5f));
		statusText.setForeground(MUTED);
		status.add(statusText, BorderLayout.CENTER);
		return status;
	}

	private JPanel boardCard(String title, String subtitle, JComponent content)
	{
		JPanel outer = card(new BorderLayout(8, 8));
		outer.setBorder(new EmptyBorder(14, 14, 14, 14));
		outer.add(sectionTitle(title, subtitle), BorderLayout.NORTH);
		outer.add(content, BorderLayout.CENTER);
		return outer;
	}

	private JPanel sectionTitle(String title, String subtitle)
	{
		JPanel panel = transparentColumn();
		panel.add(label(title, NebulaDesign.fontTitle(), TEXT));
		panel.add(Box.createRigidArea(new Dimension(0, 2)));
		panel.add(label(subtitle, NebulaDesign.fontBody(12f), MUTED));
		return panel;
	}

	/**
	 * Section title whose subtitle wraps at the given pixel width so narrow
	 * cards do not inflate their preferred width and collapse the row grid.
	 */
	private JPanel sectionTitle(String title, String subtitle, int wrapWidth)
	{
		JPanel panel = transparentColumn();
		panel.add(label(title, NebulaDesign.fontTitle(), TEXT));
		panel.add(Box.createRigidArea(new Dimension(0, 2)));
		JLabel sub = new JLabel("<html><div style='width:" + wrapWidth + "px'>"
				+ subtitle + "</div></html>");
		sub.setFont(NebulaDesign.fontBody(12f));
		sub.setForeground(MUTED);
		panel.add(sub);
		return panel;
	}

	private JPanel metric(String title, JLabel value, String subtitle)
	{
		JPanel panel = card(new BorderLayout(0, 2));
		panel.setBorder(new EmptyBorder(12, 14, 12, 14));
		JLabel t = label(title.toUpperCase(Locale.ROOT),
				NebulaDesign.fontSection(), NebulaDesign.TEXT_TERTIARY);
		JLabel s = label(subtitle, NebulaDesign.fontSmall(), NebulaDesign.TEXT_TERTIARY);
		panel.add(t, BorderLayout.NORTH);
		panel.add(value, BorderLayout.CENTER);
		panel.add(s, BorderLayout.SOUTH);
		return panel;
	}

	private static JLabel metricValue(String text)
	{
		JLabel label = new JLabel(text);
		label.setFont(NebulaDesign.fontSemiBold(19f));
		label.setForeground(TEXT);
		return label;
	}

	private void syncControlsFromSnapshot()
	{
		for (Map.Entry<Integer, ChipCheckBox> entry : algorithmChecks.entrySet())
		{
			entry.getValue().setSelected(entry.getKey().intValue() == snapshot.algorithm);
		}
		for (Map.Entry<Integer, ChipCheckBox> entry : nodeChecks.entrySet())
		{
			int value = entry.getKey().intValue();
			entry.getValue().setSelected(value == Math.max(1, snapshot.nodes)
					|| value == 1
					|| (value == 8 && snapshot.nodes < 8));
		}
		for (Map.Entry<String, ChipCheckBox> entry : policyChecks.entrySet())
		{
			entry.getValue().setSelected("DSMP default".equals(entry.getKey()));
		}
		if (snapshot.algorithm == RecommendationModeSupport.DOC2VEC
				|| snapshot.algorithm == RecommendationModeSupport.COS_SIM
				|| snapshot.algorithm == RecommendationModeSupport.COMMON_NEIGHBORS
				|| snapshot.algorithm == RecommendationModeSupport.TEMPORAL_GRAPH_FOUNDATION)
		{
			ChipCheckBox capacity = policyChecks.get("Capacity-weighted");
			if (capacity != null)
			{
				capacity.setSelected(true);
			}
		}
		batchField.setText(String.valueOf(Math.max(1, snapshot.batchUsers)));
		graphOn.setSelected(snapshot.graphEnabled);
		graphOff.setSelected(!snapshot.graphEnabled);
		delayOn.setSelected(snapshot.simulateDelay);
		delayOff.setSelected(!snapshot.simulateDelay);
		repetitionSpinner.setValue(Integer.valueOf(1));
	}

	private void rebuildDesign()
	{
		ArrayList<Integer> algorithms = new ArrayList<Integer>();
		for (Map.Entry<Integer, ChipCheckBox> entry : algorithmChecks.entrySet())
		{
			if (entry.getValue().isSelected())
			{
				algorithms.add(entry.getKey());
			}
		}
		if (algorithms.isEmpty())
		{
			algorithms.add(Integer.valueOf(snapshot.algorithm));
		}
		ArrayList<Integer> nodes = new ArrayList<Integer>();
		for (Map.Entry<Integer, ChipCheckBox> entry : nodeChecks.entrySet())
		{
			if (entry.getValue().isSelected())
			{
				nodes.add(entry.getKey());
			}
		}
		if (nodes.isEmpty())
		{
			nodes.add(Integer.valueOf(Math.max(1, snapshot.nodes)));
		}
		ArrayList<String> policies = new ArrayList<String>();
		for (Map.Entry<String, ChipCheckBox> entry : policyChecks.entrySet())
		{
			if (entry.getValue().isSelected())
			{
				policies.add(entry.getKey());
			}
		}
		if (policies.isEmpty())
		{
			policies.add("DSMP default");
		}
		ArrayList<Boolean> graphOptions = new ArrayList<Boolean>();
		if (graphOn.isSelected())
		{
			graphOptions.add(Boolean.TRUE);
		}
		if (graphOff.isSelected())
		{
			graphOptions.add(Boolean.FALSE);
		}
		if (graphOptions.isEmpty())
		{
			graphOptions.add(Boolean.valueOf(snapshot.graphEnabled));
		}
		ArrayList<Boolean> tweetDelayOptions = new ArrayList<Boolean>();
		if (delayOn.isSelected())
		{
			tweetDelayOptions.add(Boolean.TRUE);
		}
		if (delayOff.isSelected())
		{
			tweetDelayOptions.add(Boolean.FALSE);
		}
		if (tweetDelayOptions.isEmpty())
		{
			tweetDelayOptions.add(Boolean.valueOf(snapshot.simulateDelay));
		}
		design = new ExperimentObservatoryEngine.ExperimentDesign(
				snapshot.datasetName,
				snapshot.datasetPath,
				algorithms,
				nodes,
				parsePositiveList(batchField.getText(), Math.max(1, snapshot.batchUsers)),
				policies,
				graphOptions,
				tweetDelayOptions,
				((Integer) repetitionSpinner.getValue()).intValue(),
				snapshot.topRecommendations,
				snapshot.heapMb,
				snapshot.modeLabel,
				snapshot.parameterProfile,
				snapshot.parameterSummary,
				snapshot.capacitySummary);
	}

	private List<Integer> parsePositiveList(String text, int fallback)
	{
		ArrayList<Integer> values = new ArrayList<Integer>();
		if (text != null)
		{
			String[] parts = text.split(",");
			for (String part : parts)
			{
				try
				{
					int value = Integer.parseInt(part.trim());
					if (value > 0)
					{
						values.add(Integer.valueOf(value));
					}
				}
				catch (NumberFormatException ignored)
				{
				}
			}
		}
		if (values.isEmpty())
		{
			values.add(Integer.valueOf(Math.max(1, fallback)));
		}
		return values;
	}

	private void analyzeArtifacts()
	{
		rebuildDesign();
		setStatus("SCANNING", "Scanning DSMP timing, recommendation, graph, and environment artifacts.", BLUE);
		final ExperimentObservatoryEngine.ExperimentDesign scanDesign = design;
		new SwingWorker<ExperimentObservatoryEngine.ObservatoryReport, Void>()
		{
			protected ExperimentObservatoryEngine.ObservatoryReport doInBackground()
			{
				return ExperimentObservatoryEngine.scanProject(new File("."), scanDesign);
			}

			protected void done()
			{
				try
				{
					report = get();
					appendLog("Artifact scan complete. Capsule "
							+ report.capsuleId + " | "
							+ report.comparison.reproducibilityScore + "/100 artifact completeness.");
					setStatus("READY", "Artifact scan complete. Export a capsule when you want a paper-ready record.", GREEN);
				}
				catch (Exception e)
				{
					appendLog("Artifact scan failed: " + e.getMessage());
					setStatus("FAILED", "Could not scan artifacts: " + e.getMessage(), RED);
				}
				updateVisuals();
			}
		}.execute();
	}

	private void exportCapsule()
	{
		rebuildDesign();
		if (report == null)
		{
			report = ExperimentObservatoryEngine.scanProject(new File("."), design);
		}
		try
		{
			File out = ExperimentObservatoryEngine.writeCapsule(
					new File("reports/observatory"),
					report);
			appendLog("Exported reproducibility capsule: " + out.getPath());
			setStatus("EXPORTED", "Capsule exported to " + out.getPath(), GREEN);
			NebulaDialogs.showSuccess(
					this,
					"Reproducibility capsule exported:\n" + out.getPath(),
					"DSMP Experiment Observatory");
		}
		catch (Exception e)
		{
			appendLog("Capsule export failed: " + e.getMessage());
			setStatus("FAILED", "Capsule export failed: " + e.getMessage(), RED);
		}
		updateVisuals();
	}

	private void runMatrix()
	{
		if (activeRunner != null && !activeRunner.isDone())
		{
			appendLog("A matrix run is already active.");
			return;
		}
		rebuildDesign();
		List<ExperimentObservatoryEngine.RunSpec> matrix =
				ExperimentObservatoryEngine.buildMatrix(design);
		if (matrix.isEmpty())
		{
			NebulaDialogs.showWarning(
					this,
					"No matrix runs were generated. Choose at least one algorithm and node count first.",
					"DSMP Experiment Observatory");
			return;
		}
		if (snapshot.datasetPath == null || snapshot.datasetPath.trim().length() == 0)
		{
			NebulaDialogs.showWarning(
					this,
					"Load a simulator dataset before running an experiment matrix.",
					"DSMP Experiment Observatory");
			return;
		}
		stopAfterCurrentRun = false;
		runStates = new ArrayList<RunExecutionState>();
		for (ExperimentObservatoryEngine.RunSpec run : matrix)
		{
			runStates.add(new RunExecutionState(run));
		}
		activeRunDirectory = createSessionRunDirectory();
		matrixTableModel.setStates(runStates);
		setRunnerButtons(true);
		appendLog("Started automated matrix with " + runStates.size()
				+ " run(s). Run artifacts will be isolated under "
				+ activeRunDirectory.getPath() + ".");
		setStatus("RUNNING", "Automated matrix runner is controlling the simulator.", BLUE);
		updateRunnerProgress();
		activeRunner = new ObservatoryMatrixRunner(runStates, activeRunDirectory);
		activeRunner.execute();
	}

	private final class ObservatoryMatrixRunner extends SwingWorker<Void, Void>
	{
		private final ArrayList<RunExecutionState> states;
		private final File runRoot;

		ObservatoryMatrixRunner(
				ArrayList<RunExecutionState> states,
				File runRoot)
		{
			this.states = states == null
					? new ArrayList<RunExecutionState>()
					: new ArrayList<RunExecutionState>(states);
			this.runRoot = runRoot;
		}

		protected Void doInBackground()
		{
			archivePreExistingArtifacts(runRoot);
			for (RunExecutionState state : states)
			{
				if (stopAfterCurrentRun || isCancelled())
				{
					updateState(state, "SKIPPED", "Stopped before this matrix cell started.");
					continue;
				}
				executeRun(state);
			}
			return null;
		}

		protected void done()
		{
			setRunnerButtons(false);
			activeRunner = null;
			try
			{
				get();
			}
			catch (Exception e)
			{
				appendLog("Matrix worker stopped unexpectedly: " + rootCauseMessage(e));
			}
			report = ExperimentObservatoryEngine.scanProject(new File("."), design);
			updateVisuals();
			int failed = 0;
			int skipped = 0;
			for (RunExecutionState state : states)
			{
				if ("FAILED".equals(state.status))
				{
					failed++;
				}
				else if ("SKIPPED".equals(state.status))
				{
					skipped++;
				}
			}
			if (failed > 0)
			{
				setStatus("ATTENTION",
						"Matrix finished with " + failed
								+ " failed run(s). Review the run table and archives.",
						AMBER);
			}
			else if (skipped > 0)
			{
				setStatus("STOPPED",
						"Matrix stopped after the active run. Completed archives are preserved.",
						AMBER);
			}
			else
			{
				setStatus("COMPLETE",
						"Automated matrix finished. Each run has its own archived artifacts.",
						GREEN);
			}
			appendLog("Matrix runner finished. Archive root: "
					+ (runRoot == null ? "unavailable" : runRoot.getPath()));
		}

		private void executeRun(RunExecutionState state)
		{
			ObservatoryRunSignal signal = new ObservatoryRunSignal(state.run.id());
			try
			{
				updateState(state, "CLEANING", "Removing generated outputs from previous runs.");
				cleanGeneratedArtifactsForRun();
				updateState(state, "CONFIGURING", "Applying matrix row to the simulator.");
				runOnEdtAndWait(new Runnable()
				{
					public void run()
					{
						owner.applyObservatoryRunSpec(state.run);
					}
				});
				owner.beginObservatoryAutomation(state.run.id(), signal);
				updateState(state, "INITIALIZING", "Waiting for simulator initialization.");
				runOnEdtAndWait(new Runnable()
				{
					public void run()
					{
						owner.initializeAgents();
					}
				});
				signal.awaitInitialization();
				updateState(state, "RUNNING", "Simulator is processing recommendations.");
				runOnEdtAndWait(new Runnable()
				{
					public void run()
					{
						owner.startSimulation();
					}
				});
				signal.awaitFinished();
				updateState(state, "ARCHIVING", "Saving this run's artifacts.");
				archiveArtifactsForRun(state, runRoot);
				updateState(state, "COMPLETE", "Run archived successfully.");
				rewriteRunManifestIfPossible(state);
				appendLog("Completed " + state.run.id() + " -> "
						+ (state.outputDirectory == null
								? "archive unavailable"
								: state.outputDirectory.getPath()));
			}
			catch (Exception e)
			{
				String message = rootCauseMessage(e);
				updateState(state, "FAILED", message);
				appendLog("Failed " + state.run.id() + ": " + message);
				updateState(state, "ARCHIVING", "Saving failure manifest and any partial artifacts.");
				archiveArtifactsForRun(state, runRoot);
				updateState(state, "FAILED", message);
				rewriteRunManifestIfPossible(state);
			}
			finally
			{
				owner.endObservatoryAutomation(state.run.id(), signal);
			}
		}

		private void runOnEdtAndWait(Runnable action)
				throws InterruptedException, InvocationTargetException
		{
			if (SwingUtilities.isEventDispatchThread())
			{
				action.run();
				return;
			}
			SwingUtilities.invokeAndWait(action);
		}

		private String rootCauseMessage(Exception e)
		{
			Throwable cause = e;
			while (cause instanceof InvocationTargetException
					&& ((InvocationTargetException) cause).getTargetException() != null)
			{
				cause = ((InvocationTargetException) cause).getTargetException();
			}
			while (cause.getCause() != null)
			{
				cause = cause.getCause();
			}
			String message = cause.getMessage();
			return message == null || message.trim().length() == 0
					? cause.getClass().getSimpleName()
					: message;
		}

		private final class ObservatoryRunSignal
				implements ControllerAgentGui.ObservatoryAutomationListener
		{
			private final String runId;
			private final CountDownLatch initialized = new CountDownLatch(1);
			private final CountDownLatch finished = new CountDownLatch(1);
			private volatile String failureMessage;

			ObservatoryRunSignal(String runId)
			{
				this.runId = runId;
			}

			public void initializationFinished(String eventRunId)
			{
				if (matches(eventRunId))
				{
					initialized.countDown();
				}
			}

			public void simulationFinished(String eventRunId, String message)
			{
				if (matches(eventRunId))
				{
					finished.countDown();
				}
			}

			public void automationFailed(
					String eventRunId,
					String phase,
					String message)
			{
				if (matches(eventRunId))
				{
					failureMessage = phase + ": " + message;
					initialized.countDown();
					finished.countDown();
				}
			}

			void awaitInitialization() throws InterruptedException
			{
				await(initialized, INITIALIZE_TIMEOUT_MINUTES, TimeUnit.MINUTES,
						"initialization");
			}

			void awaitFinished() throws InterruptedException
			{
				await(finished, RUN_TIMEOUT_HOURS, TimeUnit.HOURS,
						"simulation");
			}

			private void await(
					CountDownLatch latch,
					long timeout,
					TimeUnit unit,
					String phase) throws InterruptedException
			{
				long deadline = System.nanoTime() + unit.toNanos(timeout);
				while (latch.getCount() > 0L)
				{
					if (isCancelled())
					{
						throw new IllegalStateException(
								"Matrix runner was cancelled during " + phase + ".");
					}
					if (failureMessage != null)
					{
						throw new IllegalStateException(failureMessage);
					}
					long remaining = deadline - System.nanoTime();
					if (remaining <= 0L)
					{
						throw new IllegalStateException(
								"Timed out waiting for simulator " + phase + ".");
					}
					latch.await(Math.min(TimeUnit.SECONDS.toNanos(2L), remaining),
							TimeUnit.NANOSECONDS);
				}
				if (failureMessage != null)
				{
					throw new IllegalStateException(failureMessage);
				}
			}

			private boolean matches(String eventRunId)
			{
				return runId.equals(eventRunId);
			}
		}
	}

	private static final class RunExecutionState
	{
		final ExperimentObservatoryEngine.RunSpec run;
		String status = "PLANNED";
		String message = "Waiting for the matrix runner.";
		long startedMs;
		long endedMs;
		File outputDirectory;

		RunExecutionState(ExperimentObservatoryEngine.RunSpec run)
		{
			this.run = run;
		}

		boolean isTerminal()
		{
			return "COMPLETE".equals(status)
					|| "FAILED".equals(status)
					|| "SKIPPED".equals(status);
		}

		long elapsedMs()
		{
			if (startedMs == 0L)
			{
				return 0L;
			}
			long end = endedMs == 0L ? System.currentTimeMillis() : endedMs;
			return Math.max(0L, end - startedMs);
		}

		String archiveLabel()
		{
			return outputDirectory == null ? "--" : outputDirectory.getName();
		}
	}

	private static final class ScalingRunRecord
	{
		final String runId;
		final String algorithmName;
		final int algorithmId;
		final int nodes;
		final int batchUsers;
		final String policy;
		final boolean graphEnabled;
		final boolean simulateTweetDelay;
		final long runtimeMs;
		final long computeMs;
		final long elapsedMs;
		final File runDirectory;
		final File archiveRoot;

		ScalingRunRecord(
				String runId,
				String algorithmName,
				int nodes,
				int batchUsers,
				String policy,
				boolean graphEnabled,
				boolean simulateTweetDelay,
				long runtimeMs,
				long computeMs,
				long elapsedMs,
				File runDirectory,
				File archiveRoot)
		{
			this.runId = runId == null ? "" : runId;
			this.algorithmName = algorithmName == null ? "" : algorithmName;
			this.algorithmId = algorithmIdForName(this.algorithmName);
			this.nodes = nodes;
			this.batchUsers = batchUsers;
			this.policy = policy == null ? "DSMP default" : policy;
			this.graphEnabled = graphEnabled;
			this.simulateTweetDelay = simulateTweetDelay;
			this.runtimeMs = runtimeMs;
			this.computeMs = computeMs;
			this.elapsedMs = elapsedMs;
			this.runDirectory = runDirectory;
			this.archiveRoot = archiveRoot;
		}

		String profileKey()
		{
			return batchUsers + "|" + policy + "|" + graphEnabled
					+ "|" + simulateTweetDelay;
		}

		String profileDescription()
		{
			return "Profile: batch " + batchUsers + " / " + policy
					+ " / graph " + (graphEnabled ? "on" : "off")
					+ " / delay " + (simulateTweetDelay ? "on" : "off");
		}
	}

	private static final class ScalingPoint
	{
		final int nodes;
		final long runtimeMs;
		final long computeMs;
		final int repetitions;
		double speedup = 1.0;
		double efficiency = 1.0;
		double computeSpeedup = 1.0;

		ScalingPoint(
				int nodes,
				long runtimeMs,
				long computeMs,
				int repetitions)
		{
			this.nodes = nodes;
			this.runtimeMs = runtimeMs;
			this.computeMs = computeMs;
			this.repetitions = repetitions;
		}
	}

	private static final class ScalingSeries
	{
		final String algorithmName;
		final int algorithmId;
		final String profileDescription;
		final String sourceLabel;
		final List<ScalingPoint> points;
		final int baselineNodes;
		final ScalingPoint fastestPoint;
		final int skippedProfileRows;
		final int skippedMissingNodes;

		private ScalingSeries(
				String algorithmName,
				int algorithmId,
				String profileDescription,
				String sourceLabel,
				List<ScalingPoint> points,
				int baselineNodes,
				ScalingPoint fastestPoint,
				int skippedProfileRows,
				int skippedMissingNodes)
		{
			this.algorithmName = algorithmName == null ? "" : algorithmName;
			this.algorithmId = algorithmId;
			this.profileDescription = profileDescription == null
					? "" : profileDescription;
			this.sourceLabel = sourceLabel == null ? "unknown" : sourceLabel;
			this.points = Collections.unmodifiableList(
					new ArrayList<ScalingPoint>(points));
			this.baselineNodes = baselineNodes;
			this.fastestPoint = fastestPoint;
			this.skippedProfileRows = skippedProfileRows;
			this.skippedMissingNodes = skippedMissingNodes;
		}

		static ScalingSeries empty(String algorithmName)
		{
			return new ScalingSeries(
					algorithmName,
					algorithmIdForName(algorithmName),
					"",
					"",
					Collections.<ScalingPoint>emptyList(),
					1,
					null,
					0,
					0);
		}

		static ScalingSeries from(
				List<ScalingRunRecord> records,
				String algorithmName,
				ExperimentObservatoryEngine.ExperimentDesign design)
		{
			if (records == null || records.isEmpty())
			{
				return empty(algorithmName);
			}
			String selected = algorithmName == null ? "" : algorithmName;
			ArrayList<ScalingRunRecord> matching =
					new ArrayList<ScalingRunRecord>();
			for (ScalingRunRecord record : records)
			{
				if (record.algorithmName.equals(selected))
				{
					matching.add(record);
				}
			}
			if (matching.isEmpty())
			{
				return empty(selected);
			}

			LinkedHashMap<String,ArrayList<ScalingRunRecord>> groups =
					new LinkedHashMap<String,ArrayList<ScalingRunRecord>>();
			for (ScalingRunRecord record : matching)
			{
				String key = record.profileKey();
				ArrayList<ScalingRunRecord> group = groups.get(key);
				if (group == null)
				{
					group = new ArrayList<ScalingRunRecord>();
					groups.put(key, group);
				}
				group.add(record);
			}

			ArrayList<ScalingRunRecord> chosen =
					new ArrayList<ScalingRunRecord>();
			for (ArrayList<ScalingRunRecord> group : groups.values())
			{
				if (isBetterScalingGroup(group, chosen))
				{
					chosen = group;
				}
			}
			if (chosen.isEmpty())
			{
				return empty(selected);
			}

			LinkedHashMap<Integer,ArrayList<Long>> runtimesByNode =
					new LinkedHashMap<Integer,ArrayList<Long>>();
			LinkedHashMap<Integer,ArrayList<Long>> computeByNode =
					new LinkedHashMap<Integer,ArrayList<Long>>();
			Collections.sort(chosen, new Comparator<ScalingRunRecord>()
			{
				public int compare(ScalingRunRecord first, ScalingRunRecord second)
				{
					if (first.nodes != second.nodes)
					{
						return first.nodes - second.nodes;
					}
					return first.runId.compareTo(second.runId);
				}
			});
			for (ScalingRunRecord record : chosen)
			{
				ArrayList<Long> runtimes = runtimesByNode.get(
						Integer.valueOf(record.nodes));
				if (runtimes == null)
				{
					runtimes = new ArrayList<Long>();
					runtimesByNode.put(Integer.valueOf(record.nodes), runtimes);
				}
				runtimes.add(Long.valueOf(record.runtimeMs));
				if (record.computeMs > 0L)
				{
					ArrayList<Long> compute = computeByNode.get(
							Integer.valueOf(record.nodes));
					if (compute == null)
					{
						compute = new ArrayList<Long>();
						computeByNode.put(Integer.valueOf(record.nodes), compute);
					}
					compute.add(Long.valueOf(record.computeMs));
				}
			}

			ArrayList<ScalingPoint> points = new ArrayList<ScalingPoint>();
			for (Map.Entry<Integer,ArrayList<Long>> entry
					: runtimesByNode.entrySet())
			{
				ArrayList<Long> compute = computeByNode.get(entry.getKey());
				points.add(new ScalingPoint(
						entry.getKey().intValue(),
						medianRuntime(entry.getValue()),
						compute == null ? -1L : medianRuntime(compute),
						entry.getValue().size()));
			}
			if (points.isEmpty())
			{
				return empty(selected);
			}
			long baselineRuntime = points.get(0).runtimeMs;
			long baselineCompute = points.get(0).computeMs;
			int baselineNodes = points.get(0).nodes;
			ScalingPoint fastest = points.get(0);
			for (ScalingPoint point : points)
			{
				point.speedup = baselineRuntime <= 0L || point.runtimeMs <= 0L
						? 1.0
						: baselineRuntime / (double) point.runtimeMs;
				double nodeScale = baselineNodes <= 0
						? point.nodes
						: point.nodes / (double) baselineNodes;
				point.efficiency = nodeScale <= 0.0
						? 1.0 : point.speedup / nodeScale;
				point.computeSpeedup = baselineCompute <= 0L
						|| point.computeMs <= 0L
						? 1.0
						: baselineCompute / (double) point.computeMs;
				if (point.runtimeMs < fastest.runtimeMs)
				{
					fastest = point;
				}
			}

			int missing = countMissingPlannedNodeCounts(design, selected, points);
			ScalingRunRecord first = chosen.get(0);
			return new ScalingSeries(
					selected,
					first.algorithmId,
					first.profileDescription(),
					first.archiveRoot == null ? "unknown"
							: first.archiveRoot.getName(),
					points,
					baselineNodes,
					fastest,
					matching.size() - chosen.size(),
					missing);
		}

		long maxRuntimeMs()
		{
			long max = 1L;
			for (ScalingPoint point : points)
			{
				max = Math.max(max, point.runtimeMs);
			}
			return max;
		}

		long maxComputeMs()
		{
			long max = 1L;
			for (ScalingPoint point : points)
			{
				long value = point.computeMs > 0L
						? point.computeMs : point.runtimeMs;
				max = Math.max(max, value);
			}
			return max;
		}

		String fastestLabel()
		{
			if (fastestPoint == null)
			{
				return "--";
			}
			return fastestPoint.nodes + " nodes / "
					+ formatElapsed(fastestPoint.runtimeMs);
		}

		String fastestComputeLabel()
		{
			ScalingPoint best = null;
			for (ScalingPoint point : points)
			{
				if (point.computeMs <= 0L)
				{
					continue;
				}
				if (best == null || point.computeMs < best.computeMs)
				{
					best = point;
				}
			}
			if (best == null)
			{
				return fastestLabel();
			}
			return best.nodes + " nodes / " + formatElapsed(best.computeMs);
		}

		String bestSpeedupLabel()
		{
			if (points.isEmpty())
			{
				return "--";
			}
			ScalingPoint best = points.get(0);
			for (ScalingPoint point : points)
			{
				if (point.speedup > best.speedup)
				{
					best = point;
				}
			}
			return formatSpeedup(best.speedup) + " at "
					+ best.nodes + " nodes";
		}

		String bestComputeSpeedupLabel()
		{
			if (points.isEmpty())
			{
				return "--";
			}
			ScalingPoint best = null;
			for (ScalingPoint point : points)
			{
				if (point.computeMs <= 0L)
				{
					continue;
				}
				if (best == null || point.computeSpeedup > best.computeSpeedup)
				{
					best = point;
				}
			}
			if (best == null)
			{
				return "--";
			}
			return formatSpeedup(best.computeSpeedup) + " at "
					+ best.nodes + " nodes";
		}

		String fullElapsedLabel()
		{
			if (points.isEmpty())
			{
				return "--";
			}
			long min = Long.MAX_VALUE;
			long max = 0L;
			ArrayList<Long> values = new ArrayList<Long>();
			for (ScalingPoint point : points)
			{
				if (point.runtimeMs <= 0L)
				{
					continue;
				}
				min = Math.min(min, point.runtimeMs);
				max = Math.max(max, point.runtimeMs);
				values.add(Long.valueOf(point.runtimeMs));
			}
			if (values.isEmpty())
			{
				return "--";
			}
			long median = medianRuntime(values);
			if (min > 0L && max / (double) min < 1.05)
			{
				return "flat around " + formatElapsed(median);
			}
			return "best " + fastestLabel();
		}

		String bestEfficiencyLabel()
		{
			if (points.isEmpty())
			{
				return "--";
			}
			ScalingPoint best = points.get(0);
			for (ScalingPoint point : points)
			{
				if (point.efficiency > best.efficiency)
				{
					best = point;
				}
			}
			return Math.round(best.efficiency * 100.0) + "% at "
					+ best.nodes + " nodes";
		}
	}

	private static boolean isBetterScalingGroup(
			List<ScalingRunRecord> candidate,
			List<ScalingRunRecord> current)
	{
		if (candidate == null || candidate.isEmpty())
		{
			return false;
		}
		if (current == null || current.isEmpty())
		{
			return true;
		}
		int candidateNodes = distinctNodeCount(candidate);
		int currentNodes = distinctNodeCount(current);
		if (candidateNodes != currentNodes)
		{
			return candidateNodes > currentNodes;
		}
		if (candidate.size() != current.size())
		{
			return candidate.size() > current.size();
		}
		return candidate.get(0).profileKey().compareTo(
				current.get(0).profileKey()) < 0;
	}

	private static int distinctNodeCount(List<ScalingRunRecord> records)
	{
		LinkedHashSet<Integer> nodes = new LinkedHashSet<Integer>();
		for (ScalingRunRecord record : records)
		{
			nodes.add(Integer.valueOf(record.nodes));
		}
		return nodes.size();
	}

	private static long medianRuntime(List<Long> runtimes)
	{
		if (runtimes == null || runtimes.isEmpty())
		{
			return -1L;
		}
		ArrayList<Long> sorted = new ArrayList<Long>(runtimes);
		Collections.sort(sorted);
		int middle = sorted.size() / 2;
		if (sorted.size() % 2 == 1)
		{
			return sorted.get(middle).longValue();
		}
		long first = sorted.get(middle - 1).longValue();
		long second = sorted.get(middle).longValue();
		return first / 2L + second / 2L + (first % 2L + second % 2L) / 2L;
	}

	private static int countMissingPlannedNodeCounts(
			ExperimentObservatoryEngine.ExperimentDesign design,
			String algorithmName,
			List<ScalingPoint> points)
	{
		if (design == null || design.nodeCounts == null
				|| design.algorithms == null)
		{
			return 0;
		}
		int algorithmId = algorithmIdForName(algorithmName);
		boolean plannedAlgorithm = false;
		for (Integer algorithm : design.algorithms)
		{
			if (algorithm.intValue() == algorithmId)
			{
				plannedAlgorithm = true;
				break;
			}
		}
		if (!plannedAlgorithm)
		{
			return 0;
		}
		LinkedHashSet<Integer> completed = new LinkedHashSet<Integer>();
		for (ScalingPoint point : points)
		{
			completed.add(Integer.valueOf(point.nodes));
		}
		int missing = 0;
		for (Integer nodeCount : design.nodeCounts)
		{
			if (!completed.contains(Integer.valueOf(nodeCount.intValue())))
			{
				missing++;
			}
		}
		return missing;
	}

	private static int algorithmIdForName(String algorithmName)
	{
		if (algorithmName == null)
		{
			return RecommendationModeSupport.COS_SIM;
		}
		int[] algorithms = new int[] {
				RecommendationModeSupport.COS_SIM,
				RecommendationModeSupport.K_MEANS,
				RecommendationModeSupport.SVM,
				RecommendationModeSupport.MLP,
				RecommendationModeSupport.DOC2VEC,
				RecommendationModeSupport.COMMON_NEIGHBORS,
				RecommendationModeSupport.K_MEANS_EUCLIDEAN,
				RecommendationModeSupport.SCALABLE_SVM,
				RecommendationModeSupport.TEMPORAL_GRAPH_FOUNDATION};
		for (int algorithm : algorithms)
		{
			if (RecommendationModeSupport.algorithmName(algorithm)
					.equals(algorithmName))
			{
				return algorithm;
			}
		}
		return RecommendationModeSupport.COS_SIM;
	}

	private static String formatSpeedup(double speedup)
	{
		if (Double.isNaN(speedup) || Double.isInfinite(speedup))
		{
			return "--";
		}
		return DECIMAL.format(speedup) + "x";
	}

	private File createSessionRunDirectory()
	{
		File root = new File(
				"reports/observatory/runs/"
				+ new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
						.format(new Date()));
		if (!root.mkdirs() && !root.isDirectory())
		{
			appendLog("Could not create run directory: " + root.getPath());
		}
		return root;
	}

	private boolean stateListMatches(
			List<ExperimentObservatoryEngine.RunSpec> matrix)
	{
		if (matrix == null || runStates == null
				|| matrix.size() != runStates.size())
		{
			return false;
		}
		for (int i = 0; i < matrix.size(); i++)
		{
			if (!matrix.get(i).id().equals(runStates.get(i).run.id()))
			{
				return false;
			}
		}
		return true;
	}

	private void setRunnerButtons(boolean running)
	{
		if (runMatrixButton != null)
		{
			runMatrixButton.setEnabled(!running);
		}
		if (stopMatrixButton != null)
		{
			stopMatrixButton.setEnabled(running);
		}
	}

	private void updateRunnerProgress()
	{
		int total = runStates == null ? 0 : runStates.size();
		int done = 0;
		int failed = 0;
		String current = "idle";
		if (runStates != null)
		{
			for (RunExecutionState state : runStates)
			{
				if (state.isTerminal())
				{
					done++;
				}
				if ("FAILED".equals(state.status))
				{
					failed++;
				}
				if ("idle".equals(current)
						&& !state.isTerminal()
						&& !"PLANNED".equals(state.status))
				{
					current = state.run.algorithmName
							+ " / " + state.run.nodes + " node"
							+ (state.run.nodes == 1 ? "" : "s");
				}
			}
		}
		if (runnerCompletedValue != null)
		{
			runnerCompletedValue.setText(done + " / " + total);
		}
		if (runnerFailedValue != null)
		{
			runnerFailedValue.setText(String.valueOf(failed));
		}
		if (runnerCurrentValue != null)
		{
			runnerCurrentValue.setText(current);
		}
		if (runnerArtifactValue != null)
		{
			runnerArtifactValue.setText(activeRunDirectory == null
					? "not started" : activeRunDirectory.getName());
		}
		if (runProgress != null)
		{
			int percent = total == 0 ? 0 : (int) Math.round(done * 100.0 / total);
			runProgress.setValue(percent);
			runProgress.setString(done + " / " + total + " runs complete");
		}
	}

	private void updateState(
			final RunExecutionState state,
			final String status,
			final String message)
	{
		if (state == null)
		{
			return;
		}
		state.status = status;
		state.message = message == null ? "" : message;
		if ("INITIALIZING".equals(status) || "RUNNING".equals(status)
				|| "CLEANING".equals(status)
				|| "ARCHIVING".equals(status)
				|| "CONFIGURING".equals(status))
		{
			if (state.startedMs == 0L)
			{
				state.startedMs = System.currentTimeMillis();
			}
		}
		if (state.isTerminal())
		{
			state.endedMs = System.currentTimeMillis();
		}
		SwingUtilities.invokeLater(new Runnable()
		{
			public void run()
			{
				matrixTableModel.setStates(runStates);
				updateRunnerProgress();
				if (state.isTerminal())
				{
					refreshScalingInsight();
				}
			}
		});
	}

	private void cleanGeneratedArtifactsForRun()
	{
		deleteKnownGeneratedFile("resultsTiming.txt");
		deleteKnownGeneratedFile("MessagePassingTimes.txt");
		deleteKnownGeneratedFile("recommendations_lists.txt");
		deleteKnownGeneratedFile("recommendations_lists_similar-users.txt");
		deleteKnownGeneratedFile("recommendations_lists_followee-accounts.txt");
		deleteKnownGeneratedFile("numTextProcessed.txt");
		deleteKnownGeneratedFile("tweetCounts.txt");
		deleteKnownGeneratedFile("finalScores_Not_Normalized.txt");
		deleteKnownGeneratedFile("Nodes_and_Edges.txt");
		deleteKnownGeneratedFile("testEnablingStartButton.txt");
		deleteMatchingFiles(new File("."),
				new String[] {"clustersIdTFIDF_"},
				new String[] {"_result.txt"});
		deleteMatchingFiles(new File("important-stuff"),
				new String[] {"edges-numbers-", "name-number-", "graph-", "Graph-", "final-graph-"},
				new String[] {".txt", ".gml", ".png", ".pdf", ".csv"});
		deleteDirectoryContents(new File("Results/Recommendations"));
		deleteDirectoryContents(new File("Results/Timing"));
		deleteDirectoryContents(new File("clusters&scores-results"));
	}

	private void deleteKnownGeneratedFile(String name)
	{
		File file = new File(name);
		if (file.isFile() && !file.delete())
		{
			appendLog("Could not delete generated file before run: " + file.getPath());
		}
	}

	private void deleteMatchingFiles(
			File directory,
			String[] prefixes,
			String[] suffixes)
	{
		if (directory == null || !directory.isDirectory())
		{
			return;
		}
		File[] files = directory.listFiles();
		if (files == null)
		{
			return;
		}
		for (File file : files)
		{
			if (!file.isFile())
			{
				continue;
			}
			String name = file.getName();
			boolean prefixMatch = false;
			for (String prefix : prefixes)
			{
				if (name.startsWith(prefix))
				{
					prefixMatch = true;
					break;
				}
			}
			if (!prefixMatch)
			{
				continue;
			}
			boolean suffixMatch = false;
			for (String suffix : suffixes)
			{
				if (name.endsWith(suffix))
				{
					suffixMatch = true;
					break;
				}
			}
			if (suffixMatch && !file.delete())
			{
				appendLog("Could not delete generated artifact before run: "
						+ file.getPath());
			}
		}
	}

	private void deleteDirectoryContents(File directory)
	{
		if (directory == null || !directory.isDirectory())
		{
			return;
		}
		File[] files = directory.listFiles();
		if (files == null)
		{
			return;
		}
		for (File file : files)
		{
			deleteRecursively(file);
		}
	}

	private void deleteRecursively(File file)
	{
		if (file == null || !file.exists())
		{
			return;
		}
		if (file.isDirectory())
		{
			File[] children = file.listFiles();
			if (children != null)
			{
				for (File child : children)
				{
					deleteRecursively(child);
				}
			}
		}
		if (!file.delete())
		{
			appendLog("Could not delete generated artifact before run: "
					+ file.getPath());
		}
	}

	private void archiveArtifactsForRun(
			RunExecutionState state,
			File runRoot)
	{
		if (state == null || runRoot == null)
		{
			return;
		}
		File runDir = new File(runRoot, state.run.id());
		if (!runDir.mkdirs() && !runDir.isDirectory())
		{
			appendLog("Could not create per-run archive directory: "
					+ runDir.getPath());
			return;
		}
		state.outputDirectory = runDir;
		writeRunManifest(state, runDir);
		copyIfExists(new File("resultsTiming.txt"), runDir);
		copyIfExists(new File("MessagePassingTimes.txt"), runDir);
		copyIfExists(new File("recommendations_lists.txt"), runDir);
		copyIfExists(new File("recommendations_lists_similar-users.txt"), runDir);
		copyIfExists(new File("recommendations_lists_followee-accounts.txt"), runDir);
		copyDirectoryIfExists(new File("Results/Timing"),
				new File(runDir, "Timing"));
		copyDirectoryIfExists(new File("Results/Recommendations"),
				new File(runDir, "Recommendations"));
		copyDirectoryIfExists(new File("clusters&scores-results"),
				new File(runDir, "clusters-and-scores-results"));
		copyMatching(new File("important-stuff"), runDir,
				new String[] {"edges-numbers-", "name-number-", "graph-", "Graph-", "final-graph-"},
				new String[] {".txt", ".gml", ".png", ".pdf", ".csv"});
	}

	private void archivePreExistingArtifacts(File runRoot)
	{
		if (runRoot == null)
		{
			return;
		}
		File preexisting = new File(runRoot, "_preexisting-artifacts");
		if (!preexisting.mkdirs() && !preexisting.isDirectory())
		{
			appendLog("Could not create pre-existing artifact archive: "
					+ preexisting.getPath());
			return;
		}
		writePreExistingReadme(preexisting);
		copyIfExists(new File("resultsTiming.txt"), preexisting);
		copyIfExists(new File("MessagePassingTimes.txt"), preexisting);
		copyIfExists(new File("recommendations_lists.txt"), preexisting);
		copyIfExists(new File("recommendations_lists_similar-users.txt"), preexisting);
		copyIfExists(new File("recommendations_lists_followee-accounts.txt"), preexisting);
		copyIfExists(new File("numTextProcessed.txt"), preexisting);
		copyIfExists(new File("tweetCounts.txt"), preexisting);
		copyDirectoryIfExists(new File("Results/Timing"),
				new File(preexisting, "Timing"));
		copyDirectoryIfExists(new File("Results/Recommendations"),
				new File(preexisting, "Recommendations"));
		copyDirectoryIfExists(new File("clusters&scores-results"),
				new File(preexisting, "clusters-and-scores-results"));
		copyMatching(new File("important-stuff"), preexisting,
				new String[] {"edges-numbers-", "name-number-", "graph-", "Graph-", "final-graph-"},
				new String[] {".txt", ".gml", ".png", ".pdf", ".csv"});
		appendLog("Archived pre-existing generated artifacts before matrix cleanup: "
				+ preexisting.getPath());
	}

	private void writePreExistingReadme(File directory)
	{
		BufferedWriter writer = null;
		try
		{
			writer = new BufferedWriter(new FileWriter(
					new File(directory, "README.txt")));
			writer.write("Generated artifacts that existed before this Observatory matrix started.");
			writer.newLine();
			writer.write("The matrix runner preserves them here before cleaning the workspace for isolated runs.");
			writer.newLine();
		}
		catch (IOException e)
		{
			appendLog("Could not write pre-existing artifact note: " + e.getMessage());
		}
		finally
		{
			if (writer != null)
			{
				try
				{
					writer.close();
				}
				catch (IOException ignored)
				{
				}
			}
		}
	}

	private void writeRunManifest(
			RunExecutionState state,
			File runDir)
	{
		File manifest = new File(runDir, "manifest.txt");
		BufferedWriter writer = null;
		try
		{
			writer = new BufferedWriter(new FileWriter(manifest));
			writer.write("run=" + state.run.id());
			writer.newLine();
			writer.write("status=" + state.status);
			writer.newLine();
			writer.write("message=" + state.message);
			writer.newLine();
			writer.write("algorithm=" + state.run.algorithmName);
			writer.newLine();
			writer.write("nodes=" + state.run.nodes);
			writer.newLine();
			writer.write("batchUsers=" + state.run.batchUsers);
			writer.newLine();
			writer.write("policy=" + state.run.policy);
			writer.newLine();
			writer.write("graphEnabled=" + state.run.graphEnabled);
			writer.newLine();
			writer.write("simulateTweetDelay=" + state.run.simulateTweetDelay);
			writer.newLine();
			writer.write("elapsedMs=" + state.elapsedMs());
			writer.newLine();
			writer.write("dataset=" + design.datasetName);
			writer.newLine();
			writer.write("datasetPath=" + design.datasetPath);
			writer.newLine();
		}
		catch (IOException e)
		{
			appendLog("Could not write run manifest: " + e.getMessage());
		}
		finally
		{
			if (writer != null)
			{
				try
				{
					writer.close();
				}
				catch (IOException ignored)
				{
				}
			}
		}
	}

	private void rewriteRunManifestIfPossible(RunExecutionState state)
	{
		if (state != null && state.outputDirectory != null)
		{
			writeRunManifest(state, state.outputDirectory);
		}
	}

	private void copyIfExists(File source, File destinationDirectory)
	{
		if (source == null || destinationDirectory == null || !source.isFile())
		{
			return;
		}
		try
		{
			Files.copy(source.toPath(),
					new File(destinationDirectory, source.getName()).toPath(),
					StandardCopyOption.REPLACE_EXISTING);
		}
		catch (IOException e)
		{
			appendLog("Could not archive " + source.getPath() + ": "
					+ e.getMessage());
		}
	}

	private void copyDirectoryIfExists(File source, File destination)
	{
		if (source == null || destination == null || !source.isDirectory())
		{
			return;
		}
		if (!destination.mkdirs() && !destination.isDirectory())
		{
			return;
		}
		File[] files = source.listFiles();
		if (files == null)
		{
			return;
		}
		for (File file : files)
		{
			if (file.isFile())
			{
				copyIfExists(file, destination);
			}
			else if (file.isDirectory())
			{
				copyDirectoryIfExists(file, new File(destination, file.getName()));
			}
		}
	}

	private void copyMatching(
			File directory,
			File destinationDirectory,
			String[] prefixes,
			String[] suffixes)
	{
		if (directory == null || destinationDirectory == null
				|| !directory.isDirectory())
		{
			return;
		}
		File[] files = directory.listFiles();
		if (files == null)
		{
			return;
		}
		for (File file : files)
		{
			String name = file.getName();
			boolean prefixMatch = false;
			for (String prefix : prefixes)
			{
				if (name.startsWith(prefix))
				{
					prefixMatch = true;
					break;
				}
			}
			if (!prefixMatch)
			{
				continue;
			}
			boolean suffixMatch = false;
			for (String suffix : suffixes)
			{
				if (name.endsWith(suffix))
				{
					suffixMatch = true;
					break;
				}
			}
			if (suffixMatch)
			{
				copyIfExists(file, destinationDirectory);
			}
		}
	}

	private void updateVisuals()
	{
		List<ExperimentObservatoryEngine.RunSpec> matrix =
				ExperimentObservatoryEngine.buildMatrix(design);
		if (stateListMatches(matrix))
		{
			matrixTableModel.setStates(runStates);
		}
		else
		{
			matrixTableModel.setRuns(matrix);
		}
		matrixBoard.setRuns(matrix);
		comparisonBoard.setReport(report, design);
		capsuleBoard.setReport(report, design);
		artifactBoard.setReport(report);
		refreshScalingInsight();
		plannedRunsValue.setText(String.valueOf(matrix.size()));
		if (report == null)
		{
			artifactValue.setText("--");
			reproducibilityValue.setText("--");
			runtimeValue.setText("--");
			capsuleValue.setText("not scanned");
		}
		else
		{
			int artifactCount = report.inventory.edgeFiles
					+ report.inventory.nodeMapFiles
					+ report.inventory.reportFiles
					+ report.inventory.recommendationArtifacts;
			artifactValue.setText(String.valueOf(artifactCount));
			reproducibilityValue.setText(report.comparison.reproducibilityScore + "/100");
			runtimeValue.setText(report.timingSummary.latestTotalBeforeGraphMs > 0
					? report.timingSummary.latestTotalBeforeGraphMs + " ms"
					: "missing");
			capsuleValue.setText(report.capsuleId);
		}
		matrixBoard.repaint();
		comparisonBoard.repaint();
		capsuleBoard.repaint();
		artifactBoard.repaint();
		scalingInsightBoard.repaint();
		updateRunnerProgress();
	}

	private void refreshScalingInsight()
	{
		List<ScalingRunRecord> records = collectScalingRunRecords();
		updateScalingAlgorithmChoices(records);
		String selectedAlgorithm = selectedScalingAlgorithm();
		scalingInsightBoard.setRecords(records, selectedAlgorithm);
		ScalingSeries series =
				ScalingSeries.from(records, selectedAlgorithm, design);
		if (series.points.isEmpty())
		{
			File archiveRoot = scalingArchiveRoot();
			scalingProfileLabel.setText(archiveRoot == null
					? "No matrix archive is available yet."
					: "No completed timing rows for "
							+ selectedAlgorithm + " under "
							+ archiveRoot.getPath() + ".");
		}
		else
		{
			scalingProfileLabel.setText(series.profileDescription
					+ (series.skippedProfileRows > 0
							? " | skipped " + series.skippedProfileRows
									+ " completed row(s) from different profiles"
							: "")
					+ " | source " + series.sourceLabel);
		}
	}

	private String selectedScalingAlgorithm()
	{
		Object selected = scalingAlgorithmBox.getSelectedItem();
		String value = selected == null ? "" : selected.toString();
		return value.length() == 0 || "No completed runs".equals(value)
				? snapshot.algorithmName : value;
	}

	private void updateScalingAlgorithmChoices(List<ScalingRunRecord> records)
	{
		String previous = selectedScalingAlgorithm();
		LinkedHashSet<String> available = new LinkedHashSet<String>();
		if (design != null)
		{
			for (Integer algorithm : design.algorithms)
			{
				String name =
						RecommendationModeSupport.algorithmName(algorithm.intValue());
				if (containsScalingAlgorithm(records, name))
				{
					available.add(name);
				}
			}
		}
		for (ScalingRunRecord record : records)
		{
			available.add(record.algorithmName);
		}

		updatingScalingAlgorithmBox = true;
		try
		{
			scalingAlgorithmBox.removeAllItems();
			if (available.isEmpty())
			{
				scalingAlgorithmBox.addItem("No completed runs");
				scalingAlgorithmBox.setEnabled(false);
			}
			else
			{
				scalingAlgorithmBox.setEnabled(true);
				for (String algorithm : available)
				{
					scalingAlgorithmBox.addItem(algorithm);
				}
				if (available.contains(previous))
				{
					scalingAlgorithmBox.setSelectedItem(previous);
				}
				else if (available.contains(snapshot.algorithmName))
				{
					scalingAlgorithmBox.setSelectedItem(snapshot.algorithmName);
				}
				else
				{
					scalingAlgorithmBox.setSelectedIndex(0);
				}
			}
		}
		finally
		{
			updatingScalingAlgorithmBox = false;
		}
	}

	private boolean containsScalingAlgorithm(
			List<ScalingRunRecord> records,
			String algorithmName)
	{
		for (ScalingRunRecord record : records)
		{
			if (record.algorithmName.equals(algorithmName))
			{
				return true;
			}
		}
		return false;
	}

	private List<ScalingRunRecord> collectScalingRunRecords()
	{
		File archiveRoot = scalingArchiveRoot();
		if (archiveRoot == null || !archiveRoot.isDirectory())
		{
			return Collections.emptyList();
		}
		File[] runDirectories = archiveRoot.listFiles();
		if (runDirectories == null)
		{
			return Collections.emptyList();
		}
		Arrays.sort(runDirectories, new Comparator<File>()
		{
			public int compare(File first, File second)
			{
				return first.getName().compareTo(second.getName());
			}
		});
		ArrayList<ScalingRunRecord> records = new ArrayList<ScalingRunRecord>();
		for (File runDirectory : runDirectories)
		{
			if (!runDirectory.isDirectory())
			{
				continue;
			}
			Map<String,String> manifest =
					readKeyValueFile(new File(runDirectory, "manifest.txt"));
			if (!"COMPLETE".equals(manifest.get("status")))
			{
				continue;
			}
			String algorithm = manifest.get("algorithm");
			int nodes = parsePositiveInt(manifest.get("nodes"), -1);
			if (algorithm == null || algorithm.trim().length() == 0
					|| nodes <= 0)
			{
				continue;
			}
			long elapsedMs = parseLong(manifest.get("elapsedMs"), -1L);
			File timingFile = new File(runDirectory, "resultsTiming.txt");
			long runtimeMs = elapsedMs;
			if (runtimeMs <= 0L)
			{
				runtimeMs = readArchivedRunRuntime(timingFile);
			}
			if (runtimeMs <= 0L)
			{
				continue;
			}
			long computeMs = readArchivedWorkerRuntime(timingFile);
			records.add(new ScalingRunRecord(
					manifest.get("run"),
					algorithm,
					nodes,
					parsePositiveInt(manifest.get("batchUsers"), 1),
					manifest.get("policy"),
					Boolean.parseBoolean(manifest.get("graphEnabled")),
					Boolean.parseBoolean(manifest.get("simulateTweetDelay")),
					runtimeMs,
					computeMs,
					elapsedMs,
					runDirectory,
					archiveRoot));
		}
		return records;
	}

	private File scalingArchiveRoot()
	{
		if (activeRunDirectory != null && activeRunDirectory.isDirectory())
		{
			return activeRunDirectory;
		}
		if (runStates != null)
		{
			for (RunExecutionState state : runStates)
			{
				if (state.outputDirectory != null
						&& state.outputDirectory.getParentFile() != null
						&& state.outputDirectory.getParentFile().isDirectory())
				{
					return state.outputDirectory.getParentFile();
				}
			}
		}
		File runsRoot = new File("reports/observatory/runs");
		File[] sessions = runsRoot.listFiles();
		if (sessions == null || sessions.length == 0)
		{
			return null;
		}
		Arrays.sort(sessions, new Comparator<File>()
		{
			public int compare(File first, File second)
			{
				return second.getName().compareTo(first.getName());
			}
		});
		for (File session : sessions)
		{
			if (session.isDirectory())
			{
				return session;
			}
		}
		return null;
	}

	private Map<String,String> readKeyValueFile(File file)
	{
		if (file == null || !file.isFile())
		{
			return Collections.emptyMap();
		}
		LinkedHashMap<String,String> values =
				new LinkedHashMap<String,String>();
		try
		{
			List<String> lines = Files.readAllLines(
					file.toPath(), StandardCharsets.UTF_8);
			for (String line : lines)
			{
				int split = line.indexOf('=');
				if (split <= 0)
				{
					continue;
				}
				values.put(line.substring(0, split).trim(),
						line.substring(split + 1).trim());
			}
		}
		catch (IOException ignored)
		{
			return Collections.emptyMap();
		}
		return values;
	}

	private long readArchivedRunRuntime(File timingFile)
	{
		if (timingFile == null || !timingFile.isFile())
		{
			return -1L;
		}
		long latestSimulationTotal = -1L;
		long maxWorkerTotal = -1L;
		try
		{
			List<String> lines = Files.readAllLines(
					timingFile.toPath(), StandardCharsets.UTF_8);
			for (String line : lines)
			{
				if (line.indexOf("SIMULATION total before graph visualization") >= 0)
				{
					long parsed = parseTrailingMs(line);
					if (parsed > 0L)
					{
						latestSimulationTotal = parsed;
					}
				}
				if ((line.startsWith("Mapper")
						|| line.indexOf("Total Tweets Processed") >= 0)
						&& line.indexOf(" Total: ") >= 0)
				{
					long parsed = parseValueBeforeMs(line, " Total: ");
					if (parsed > maxWorkerTotal)
					{
						maxWorkerTotal = parsed;
					}
				}
			}
		}
		catch (IOException ignored)
		{
			return -1L;
		}
		return latestSimulationTotal > 0L
				? latestSimulationTotal : maxWorkerTotal;
	}

	private long readArchivedWorkerRuntime(File timingFile)
	{
		if (timingFile == null || !timingFile.isFile())
		{
			return -1L;
		}
		long maxWorkerTotal = -1L;
		try
		{
			List<String> lines = Files.readAllLines(
					timingFile.toPath(), StandardCharsets.UTF_8);
			for (String line : lines)
			{
				if ((line.startsWith("Mapper")
						|| line.indexOf("Total Tweets Processed") >= 0)
						&& line.indexOf(" Total: ") >= 0)
				{
					long parsed = parseValueBeforeMs(line, " Total: ");
					if (parsed > maxWorkerTotal)
					{
						maxWorkerTotal = parsed;
					}
				}
			}
		}
		catch (IOException ignored)
		{
			return -1L;
		}
		return maxWorkerTotal;
	}

	private static long parseValueBeforeMs(String line, String marker)
	{
		if (line == null || marker == null)
		{
			return -1L;
		}
		int start = line.lastIndexOf(marker);
		if (start < 0)
		{
			return -1L;
		}
		start += marker.length();
		int end = line.indexOf(" ms", start);
		if (end < 0)
		{
			return -1L;
		}
		return parseLong(line.substring(start, end).trim(), -1L);
	}

	private static long parseTrailingMs(String line)
	{
		if (line == null)
		{
			return -1L;
		}
		int ms = line.lastIndexOf(" ms");
		if (ms < 0)
		{
			return -1L;
		}
		int start = ms - 1;
		while (start >= 0 && Character.isDigit(line.charAt(start)))
		{
			start--;
		}
		return parseLong(line.substring(start + 1, ms), -1L);
	}

	private static int parsePositiveInt(String value, int fallback)
	{
		long parsed = parseLong(value, fallback);
		return parsed <= 0L || parsed > Integer.MAX_VALUE
				? fallback : (int) parsed;
	}

	private static long parseLong(String value, long fallback)
	{
		if (value == null)
		{
			return fallback;
		}
		try
		{
			return Long.parseLong(value.trim());
		}
		catch (NumberFormatException ignored)
		{
			return fallback;
		}
	}

	private void setStatus(String pill, String text, Color color)
	{
		statusTone = color == null ? GREEN : color;
		statusPill.setText(pill);
		statusPill.setForeground(statusTone);
		statusPill.repaint();
		statusText.setText(text);
	}

	private void appendLog(String line)
	{
		if (!SwingUtilities.isEventDispatchThread())
		{
			final String lineForEdt = line;
			SwingUtilities.invokeLater(new Runnable()
			{
				public void run()
				{
					appendLog(lineForEdt);
				}
			});
			return;
		}
		if (observatoryLog == null)
		{
			return;
		}
		if (observatoryLog.getText().length() == 0)
		{
			observatoryLog.setText("DSMP OBSERVATORY LOG\n");
		}
		observatoryLog.append(line + "\n");
		observatoryLog.setCaretPosition(observatoryLog.getDocument().getLength());
	}

	private JPanel card(java.awt.LayoutManager layout)
	{
		JPanel panel = new CardPanel(layout);
		panel.setBackground(SURFACE);
		return panel;
	}

	private JPanel transparentColumn()
	{
		JPanel panel = new JPanel();
		panel.setOpaque(false);
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		return panel;
	}

	private JButton primaryButton(String text, Color color)
	{
		return new NebulaButton(text, color);
	}

	private JButton secondaryButton(String text)
	{
		return new NebulaButton(text, null);
	}

	private static JLabel label(String text, Font font, Color color)
	{
		JLabel label = new JLabel(text);
		label.setFont(font);
		label.setForeground(color);
		return label;
	}

	private static Color themed(Color day, Color night)
	{
		return StudioNightTheme.isNightMode() ? night : day;
	}

	private static Color glass(int alpha)
	{
		return themed(
				NebulaDesign.withAlpha(NebulaDesign.DAY_SURFACE, alpha),
				NebulaDesign.withAlpha(NebulaDesign.NIGHT_SURFACE_RAISED,
						Math.min(255, alpha + 16)));
	}

	private static Color track()
	{
		return themed(NebulaDesign.DAY_INSET, NebulaDesign.NIGHT_INSET);
	}

	private static String ellipsize(String value, FontMetrics metrics, int available)
	{
		if (value == null)
		{
			return "";
		}
		if (metrics.stringWidth(value) <= available)
		{
			return value;
		}
		String trimmed = value;
		while (trimmed.length() > 1
				&& metrics.stringWidth(trimmed + "…") > available)
		{
			trimmed = trimmed.substring(0, trimmed.length() - 1);
		}
		return trimmed + "…";
	}

	/** Rounded Nebula card with a soft day shadow and subtle night border. */
	private static final class CardPanel extends JPanel
	{
		CardPanel(java.awt.LayoutManager layout)
		{
			super(layout);
			setOpaque(false);
		}

		protected void paintComponent(Graphics g)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			NebulaWidgets.paintCard(g2, 0, 0, getWidth(), getHeight(),
					NebulaDesign.RADIUS_CARD);
			g2.dispose();
			super.paintComponent(g);
		}
	}

	/** Hero card: Nebula card plus a faint two-tone aurora wash. */
	private static final class HeroCard extends JPanel
	{
		HeroCard(java.awt.LayoutManager layout)
		{
			super(layout);
			setOpaque(false);
		}

		protected void paintComponent(Graphics g)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			NebulaDesign.prepare(g2);
			int w = getWidth();
			int h = getHeight();
			NebulaWidgets.paintCard(g2, 0, 0, w, h, NebulaDesign.RADIUS_CARD);
			g2.clip(new java.awt.geom.RoundRectangle2D.Float(
					1, 1, w - 2, h - 4,
					NebulaDesign.RADIUS_CARD, NebulaDesign.RADIUS_CARD));
			boolean night = StudioNightTheme.isNightMode();
			int glowAlpha = night ? 26 : 15;
			Color accent = NebulaDesign.withAlpha(BLUE, glowAlpha);
			Color purple = NebulaDesign.withAlpha(PURPLE, Math.max(8, glowAlpha - 3));
			Color clear = NebulaDesign.withAlpha(BLUE, 0);
			g2.setPaint(new RadialGradientPaint(
					new java.awt.geom.Point2D.Float(w * 0.22f, 0f),
					Math.max(120f, w * 0.34f),
					new float[] {0f, 1f},
					new Color[] {accent, clear}));
			g2.fillRect(0, 0, w, h);
			g2.setPaint(new RadialGradientPaint(
					new java.awt.geom.Point2D.Float(w * 0.72f, h),
					Math.max(120f, w * 0.30f),
					new float[] {0f, 1f},
					new Color[] {purple, NebulaDesign.withAlpha(PURPLE, 0)}));
			g2.fillRect(0, 0, w, h);
			g2.dispose();
			super.paintComponent(g);
		}
	}

	/** Nebula-styled button: rounded fill (hue) or neutral surface + border. */
	private static final class NebulaButton extends JButton
	{
		private final Color hue;

		NebulaButton(String text, Color hue)
		{
			super(text);
			this.hue = hue;
			setFont(NebulaDesign.fontMedium(12.5f));
			setOpaque(false);
			setContentAreaFilled(false);
			setBorderPainted(false);
			setFocusPainted(false);
			setRolloverEnabled(true);
			setBorder(new EmptyBorder(9, 16, 9, 16));
			setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		}

		public Dimension getPreferredSize()
		{
			FontMetrics metrics = getFontMetrics(getFont());
			int width = metrics.stringWidth(getText() == null ? "" : getText()) + 34;
			int height = Math.max(34, metrics.getHeight() + 16);
			return new Dimension(width, height);
		}

		protected void paintComponent(Graphics g)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			NebulaDesign.prepare(g2);
			int w = getWidth();
			int h = getHeight();
			boolean enabled = getModel().isEnabled();
			boolean hover = getModel().isRollover();
			boolean pressed = getModel().isPressed();
			Color labelColor;
			if (hue != null && enabled)
			{
				Color fill = hue;
				if (pressed)
				{
					fill = StudioNightTheme.blend(hue, Color.BLACK, 0.16f);
				}
				else if (hover)
				{
					fill = StudioNightTheme.blend(hue, Color.WHITE,
							StudioNightTheme.isNightMode() ? 0.14f : 0.10f);
					g2.setColor(NebulaDesign.withAlpha(hue, 55));
					g2.fillRoundRect(-1, 0, w + 1, h, NebulaDesign.RADIUS_CONTROL + 3,
							NebulaDesign.RADIUS_CONTROL + 3);
				}
				g2.setColor(fill);
				g2.fillRoundRect(1, 1, w - 3, h - 3,
						NebulaDesign.RADIUS_CONTROL, NebulaDesign.RADIUS_CONTROL);
				labelColor = NebulaDesign.TEXT_ON_ACCENT;
			}
			else if (!enabled)
			{
				g2.setColor(SURFACE_ALT);
				g2.fillRoundRect(1, 1, w - 3, h - 3,
						NebulaDesign.RADIUS_CONTROL, NebulaDesign.RADIUS_CONTROL);
				g2.setColor(BORDER);
				g2.drawRoundRect(1, 1, w - 3, h - 3,
						NebulaDesign.RADIUS_CONTROL, NebulaDesign.RADIUS_CONTROL);
				labelColor = NebulaDesign.TEXT_TERTIARY;
			}
			else
			{
				g2.setColor(hover ? NebulaDesign.SURFACE_RAISED : SURFACE);
				g2.fillRoundRect(1, 1, w - 3, h - 3,
						NebulaDesign.RADIUS_CONTROL, NebulaDesign.RADIUS_CONTROL);
				g2.setColor(hover
						? NebulaDesign.withAlpha(BLUE, 110)
						: NebulaDesign.BORDER_STRONG);
				g2.drawRoundRect(1, 1, w - 3, h - 3,
						NebulaDesign.RADIUS_CONTROL, NebulaDesign.RADIUS_CONTROL);
				labelColor = TEXT;
			}
			g2.setFont(getFont());
			g2.setColor(labelColor);
			FontMetrics metrics = g2.getFontMetrics();
			String text = ellipsize(getText(), metrics, w - 16);
			int textX = (w - metrics.stringWidth(text)) / 2;
			int textY = (h - metrics.getHeight()) / 2 + metrics.getAscent();
			g2.drawString(text, textX, textY);
			g2.dispose();
		}
	}

	/** Slim rounded progress bar: inset track, accent-to-purple fill, mono label. */
	private static final class NebulaProgressUI
			extends javax.swing.plaf.basic.BasicProgressBarUI
	{
		public void paint(Graphics g, JComponent component)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			NebulaDesign.prepare(g2);
			int w = component.getWidth();
			int h = component.getHeight();
			int trackHeight = 8;
			int trackY = h - trackHeight - 2;
			g2.setColor(track());
			g2.fillRoundRect(0, trackY, w, trackHeight, trackHeight, trackHeight);
			int range = progressBar.getMaximum() - progressBar.getMinimum();
			double fraction = range <= 0
					? 0.0
					: (progressBar.getValue() - progressBar.getMinimum())
							/ (double) range;
			int fillWidth = (int) Math.round(w * Math.max(0.0, Math.min(1.0, fraction)));
			if (fillWidth > 0)
			{
				g2.setPaint(new GradientPaint(0, 0, BLUE, Math.max(1, w), 0, PURPLE));
				g2.fillRoundRect(0, trackY, Math.max(trackHeight, fillWidth),
						trackHeight, trackHeight, trackHeight);
			}
			if (progressBar.isStringPainted() && progressBar.getString() != null)
			{
				g2.setFont(NebulaDesign.fontMono(11f));
				g2.setColor(MUTED);
				g2.drawString(progressBar.getString(), 0, trackY - 6);
			}
			g2.dispose();
		}
	}

	/** Nebula combo box: rounded surface field, custom chevron, themed popup. */
	private static final class NebulaComboUI
			extends javax.swing.plaf.basic.BasicComboBoxUI
	{
		protected JButton createArrowButton()
		{
			JButton button = new JButton()
			{
				protected void paintComponent(Graphics g)
				{
					Graphics2D g2 = (Graphics2D) g.create();
					NebulaWidgets.paintChevron(g2, getWidth() / 2 - 2,
							getHeight() / 2 - 1, 8,
							NebulaDesign.TEXT_SECONDARY);
					g2.dispose();
				}
			};
			button.setPreferredSize(new Dimension(24, 24));
			button.setBorder(BorderFactory.createEmptyBorder());
			button.setOpaque(false);
			button.setContentAreaFilled(false);
			button.setFocusable(false);
			return button;
		}

		public void paint(Graphics g, JComponent component)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			NebulaDesign.prepare(g2);
			int w = component.getWidth();
			int h = component.getHeight();
			g2.setColor(SURFACE_ALT);
			g2.fillRoundRect(0, 0, w - 1, h - 1,
					NebulaDesign.RADIUS_CONTROL, NebulaDesign.RADIUS_CONTROL);
			g2.setColor(comboBox.isPopupVisible() || comboBox.hasFocus()
					? BLUE : NebulaDesign.BORDER_STRONG);
			g2.drawRoundRect(0, 0, w - 1, h - 1,
					NebulaDesign.RADIUS_CONTROL, NebulaDesign.RADIUS_CONTROL);
			g2.dispose();
			Rectangle bounds = rectangleForCurrentValue();
			paintCurrentValue(g, bounds, false);
		}

		public void paintCurrentValueBackground(
				Graphics g,
				Rectangle bounds,
				boolean hasFocus)
		{
			// The rounded field painted in paint() is the background.
		}

		protected javax.swing.plaf.basic.ComboPopup createPopup()
		{
			javax.swing.plaf.basic.BasicComboPopup popup =
					new javax.swing.plaf.basic.BasicComboPopup(comboBox);
			popup.setBorder(BorderFactory.createLineBorder(
					NebulaDesign.BORDER_STRONG));
			popup.getList().setFont(NebulaDesign.fontMedium(12f));
			popup.getList().setBackground(SURFACE);
			popup.getList().setForeground(TEXT);
			popup.getList().setSelectionBackground(NebulaDesign.SELECTION);
			popup.getList().setSelectionForeground(TEXT);
			return popup;
		}
	}

	/** Nebula spinner: quiet chevron steppers instead of stock arrow buttons. */
	private static final class NebulaSpinnerUI
			extends javax.swing.plaf.basic.BasicSpinnerUI
	{
		protected Component createNextButton()
		{
			JButton button = spinnerButton(true);
			installNextButtonListeners(button);
			return button;
		}

		protected Component createPreviousButton()
		{
			JButton button = spinnerButton(false);
			installPreviousButtonListeners(button);
			return button;
		}

		private JButton spinnerButton(final boolean up)
		{
			JButton button = new JButton()
			{
				protected void paintComponent(Graphics g)
				{
					Graphics2D g2 = (Graphics2D) g.create();
					NebulaDesign.prepare(g2);
					g2.setColor(getModel().isRollover()
							? BLUE : NebulaDesign.TEXT_SECONDARY);
					g2.setStroke(new BasicStroke(1.6f,
							BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
					int cx = getWidth() / 2;
					int cy = getHeight() / 2;
					if (up)
					{
						g2.drawLine(cx - 3, cy + 1, cx, cy - 2);
						g2.drawLine(cx, cy - 2, cx + 3, cy + 1);
					}
					else
					{
						g2.drawLine(cx - 3, cy - 1, cx, cy + 2);
						g2.drawLine(cx, cy + 2, cx + 3, cy - 1);
					}
					g2.dispose();
				}
			};
			button.setPreferredSize(new Dimension(20, 12));
			button.setBorder(BorderFactory.createEmptyBorder());
			button.setOpaque(false);
			button.setContentAreaFilled(false);
			button.setFocusable(false);
			button.setRolloverEnabled(true);
			button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			return button;
		}
	}

	private Color colorForAlgorithm(int algorithm)
	{
		switch (algorithm)
		{
			case RecommendationModeSupport.SVM:
			case RecommendationModeSupport.SCALABLE_SVM:
				return AMBER;
			case RecommendationModeSupport.DOC2VEC:
				return GREEN;
			case RecommendationModeSupport.MLP:
				return RED;
			case RecommendationModeSupport.K_MEANS:
			case RecommendationModeSupport.K_MEANS_EUCLIDEAN:
				return PURPLE;
			case RecommendationModeSupport.COMMON_NEIGHBORS:
				return NebulaDesign.PINK;
			case RecommendationModeSupport.TEMPORAL_GRAPH_FOUNDATION:
				return NebulaDesign.INFO;
			case RecommendationModeSupport.COS_SIM:
			default:
				return BLUE;
		}
	}

	/**
	 * Selectable option card: soft hue tint + accent border when selected,
	 * quiet surface with a custom rounded check square when idle.
	 */
	private final class ChipCheckBox extends JCheckBox
	{
		private final Color accent;

		ChipCheckBox(String text, Color accent)
		{
			super(text);
			this.accent = accent;
			setOpaque(false);
			setContentAreaFilled(false);
			setFont(NebulaDesign.fontMedium(12f));
			setForeground(TEXT);
			setFocusPainted(false);
			setRolloverEnabled(true);
			setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			setBorder(new EmptyBorder(9, 10, 9, 8));
		}

		public Dimension getPreferredSize()
		{
			FontMetrics metrics = getFontMetrics(getFont());
			String text = getText() == null ? "" : getText();
			int width = 10 + 16 + 8 + metrics.stringWidth(text) + 10;
			int height = Math.max(36, metrics.getHeight() + 18);
			return new Dimension(width, height);
		}

		protected void paintComponent(Graphics g)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			NebulaDesign.prepare(g2);
			int w = getWidth();
			int h = getHeight();
			boolean selected = isSelected();
			boolean hover = getModel().isRollover();
			boolean night = StudioNightTheme.isNightMode();
			if (selected)
			{
				g2.setColor(NebulaDesign.withAlpha(accent, night ? 42 : 26));
				g2.fillRoundRect(0, 0, w - 1, h - 1,
						NebulaDesign.RADIUS_CONTROL, NebulaDesign.RADIUS_CONTROL);
				g2.setColor(hover
						? StudioNightTheme.blend(accent, Color.WHITE, 0.14f)
						: accent);
				g2.setStroke(new BasicStroke(1.6f));
			}
			else
			{
				g2.setColor(hover ? NebulaDesign.SURFACE_RAISED : SURFACE_ALT);
				g2.fillRoundRect(0, 0, w - 1, h - 1,
						NebulaDesign.RADIUS_CONTROL, NebulaDesign.RADIUS_CONTROL);
				g2.setColor(hover
						? NebulaDesign.withAlpha(accent, 130)
						: BORDER);
				g2.setStroke(new BasicStroke(1f));
			}
			g2.drawRoundRect(0, 0, w - 1, h - 1,
					NebulaDesign.RADIUS_CONTROL, NebulaDesign.RADIUS_CONTROL);
			int boxSize = 16;
			int boxX = 10;
			int boxY = h / 2 - boxSize / 2;
			if (selected)
			{
				g2.setColor(accent);
				g2.fillRoundRect(boxX, boxY, boxSize, boxSize, 5, 5);
				g2.setColor(NebulaDesign.TEXT_ON_ACCENT);
				g2.setStroke(new BasicStroke(2f,
						BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
				g2.drawLine(boxX + 4, boxY + 8, boxX + 7, boxY + 11);
				g2.drawLine(boxX + 7, boxY + 11, boxX + 12, boxY + 5);
			}
			else
			{
				g2.setColor(SURFACE);
				g2.fillRoundRect(boxX, boxY, boxSize, boxSize, 5, 5);
				g2.setColor(hover
						? NebulaDesign.withAlpha(accent, 170)
						: NebulaDesign.BORDER_STRONG);
				g2.setStroke(new BasicStroke(1.2f));
				g2.drawRoundRect(boxX, boxY, boxSize, boxSize, 5, 5);
			}
			g2.setFont(getFont());
			g2.setColor(selected ? TEXT : MUTED);
			FontMetrics metrics = g2.getFontMetrics();
			int textX = boxX + boxSize + 8;
			String text = ellipsize(getText(), metrics, w - textX - 8);
			int textY = (h - metrics.getHeight()) / 2 + metrics.getAscent();
			g2.drawString(text, textX, textY);
			g2.dispose();
		}
	}

	private final class ObservatorySignal extends JPanel
	{
		private int tick;

		ObservatorySignal()
		{
			setOpaque(false);
			setPreferredSize(new Dimension(420, 80));
			new Timer(40, new ActionListener()
			{
				public void actionPerformed(ActionEvent e)
				{
					if (!isShowing())
					{
						return;
					}
					tick++;
					repaint();
				}
			}).start();
		}

		protected void paintComponent(Graphics g)
		{
			super.paintComponent(g);
			Graphics2D g2 = (Graphics2D) g.create();
			NebulaDesign.prepare(g2);
			int h = getHeight();
			int w = getWidth();
			int[] y = new int[] {h / 2 + 8, h / 2 - 10, h / 2 + 4, h / 2 - 16, h / 2 + 2};
			g2.setStroke(new BasicStroke(1.6f));
			for (int i = 0; i < y.length - 1; i++)
			{
				int x1 = 30 + i * (w - 60) / (y.length - 1);
				int x2 = 30 + (i + 1) * (w - 60) / (y.length - 1);
				Color line = i % 2 == 0 ? BLUE : NebulaDesign.INFO;
				g2.setColor(NebulaDesign.withAlpha(line, 60));
				g2.drawLine(x1, y[i], x2, y[i + 1]);
			}
			for (int i = 0; i < y.length; i++)
			{
				int x = 30 + i * (w - 60) / (y.length - 1);
				float pulse = (float) (Math.sin((tick + i * 19) / 18.0) * 2.0 + 7.0);
				Color c = i % 2 == 0 ? BLUE : NebulaDesign.INFO;
				g2.setColor(NebulaDesign.withAlpha(c, 32));
				g2.fillOval((int) (x - pulse - 4), (int) (y[i] - pulse - 4),
						(int) ((pulse + 4) * 2), (int) ((pulse + 4) * 2));
				g2.setColor(NebulaDesign.withAlpha(c, 130));
				g2.setStroke(new BasicStroke(1.8f));
				g2.drawOval(x - 6, y[i] - 6, 12, 12);
				g2.setColor(SURFACE);
				g2.fillOval(x - 3, y[i] - 3, 7, 7);
			}
			g2.dispose();
		}
	}

	private final class MatrixBoard extends JPanel
	{
		private List<ExperimentObservatoryEngine.RunSpec> runs =
				new ArrayList<ExperimentObservatoryEngine.RunSpec>();
		private int tick;

		MatrixBoard()
		{
			setOpaque(false);
			setPreferredSize(new Dimension(700, 330));
			new Timer(60, new ActionListener()
			{
				public void actionPerformed(ActionEvent e)
				{
					if (!isShowing())
					{
						return;
					}
					tick++;
				}
			}).start();
		}

		void setRuns(List<ExperimentObservatoryEngine.RunSpec> runs)
		{
			this.runs = runs == null
					? new ArrayList<ExperimentObservatoryEngine.RunSpec>()
					: new ArrayList<ExperimentObservatoryEngine.RunSpec>(runs);
		}

		protected void paintComponent(Graphics g)
		{
			super.paintComponent(g);
			Graphics2D g2 = prepare(g);
			int w = getWidth();
			int h = getHeight();
			paintSoftBackground(g2, w, h);
			NebulaDesign.paintEyebrow(g2, "Controlled experiment matrix", 22, 30,
					NebulaDesign.TEXT_TERTIARY);
			g2.setFont(NebulaDesign.fontSemiBold(20f));
			g2.setColor(TEXT);
			g2.drawString(runs.size() + " planned run" + (runs.size() == 1 ? "" : "s"), 22, 58);
			int max = Math.min(48, runs.size());
			int cols = Math.max(1, Math.min(8, (w - 44) / 136));
			int tileW = Math.max(110, (w - 44 - (cols - 1) * 8) / cols);
			int tileH = 54;
			int y = 82;
			for (int i = 0; i < max; i++)
			{
				ExperimentObservatoryEngine.RunSpec run = runs.get(i);
				int col = i % cols;
				int row = i / cols;
				int x = 22 + col * (tileW + 8);
				int yy = y + row * (tileH + 8);
				if (yy + tileH > h - 34)
				{
					break;
				}
				Color c = colorForAlgorithm(run.algorithm);
				g2.setColor(glass(235));
				g2.fillRoundRect(x, yy, tileW, tileH, 10, 10);
				g2.setColor(NebulaDesign.withAlpha(c, 90));
				g2.setStroke(new BasicStroke(1.1f));
				g2.drawRoundRect(x, yy, tileW, tileH, 10, 10);
				g2.setColor(c);
				g2.fillRoundRect(x, yy + 8, 3, tileH - 16, 3, 3);
				g2.setColor(TEXT);
				g2.setFont(NebulaDesign.fontMedium(12f));
				g2.drawString("#" + run.index + " " + truncate(run.algorithmName, 13),
						x + 13, yy + 21);
				g2.setColor(MUTED);
				g2.setFont(NebulaDesign.fontMedium(10f));
				g2.drawString(ellipsize(
								run.nodes + " nodes · batch " + run.batchUsers
										+ " · delay " + (run.simulateTweetDelay ? "on" : "off"),
								g2.getFontMetrics(), tileW - 36),
						x + 13, yy + 37);
				int pulseX = x + tileW - 15;
				int pulse = (int) (Math.sin((tick + i * 5) / 10.0) * 2 + 4);
				g2.setColor(NebulaDesign.withAlpha(c, 65));
				g2.fillOval(pulseX - pulse, yy + 14 - pulse, pulse * 2, pulse * 2);
			}
			if (runs.size() > max)
			{
				g2.setColor(MUTED);
				g2.setFont(NebulaDesign.fontMedium(11f));
				g2.drawString("+" + (runs.size() - max)
						+ " more runs listed in the table below", 22, h - 18);
			}
			g2.dispose();
		}
	}

	private final class ComparisonBoard extends JPanel
	{
		private ExperimentObservatoryEngine.ObservatoryReport report;
		private ExperimentObservatoryEngine.ExperimentDesign design;

		ComparisonBoard()
		{
			setOpaque(false);
			setPreferredSize(new Dimension(620, 330));
		}

		void setReport(
				ExperimentObservatoryEngine.ObservatoryReport report,
				ExperimentObservatoryEngine.ExperimentDesign design)
		{
			this.report = report;
			this.design = design;
		}

		protected void paintComponent(Graphics g)
		{
			super.paintComponent(g);
			Graphics2D g2 = prepare(g);
			int w = getWidth();
			int h = getHeight();
			paintSoftBackground(g2, w, h);
			if (report == null)
			{
				paintEmpty(g2, w, h,
						"Analyze artifacts to build the comparison dashboard.",
						"This board will summarize runtime, stability, graph evidence, and artifact completeness.");
				g2.dispose();
				return;
			}
			NebulaDesign.paintEyebrow(g2, "Research readout", 22, 30,
					NebulaDesign.TEXT_TERTIARY);
			drawScoreRing(g2, 92, 112, 54,
					report.comparison.reproducibilityScore,
					report.comparison.reproducibilityScore >= 80 ? GREEN : AMBER);
			g2.setColor(TEXT);
			g2.setFont(NebulaDesign.fontSemiBold(18f));
			g2.drawString("Artifact completeness", 166, 86);
			g2.setFont(NebulaDesign.fontBody(12.5f));
			g2.setColor(MUTED);
			g2.drawString(report.comparison.stabilityRead, 166, 110);
			g2.drawString(report.comparison.graphRead, 166, 130);
			int y = 178;
			drawInsightBar(g2, 24, y, w - 48, "Runtime",
					report.comparison.fastestRead,
					percentFromMs(report.timingSummary.latestTotalBeforeGraphMs),
					BLUE);
			drawInsightBar(g2, 24, y + 48, w - 48, "Bottleneck",
					report.comparison.bottleneckRead,
					report.timingSummary.longestStageMs > 0 ? 78 : 18,
					AMBER);
			drawInsightBar(g2, 24, y + 96, w - 48, "Recommendation artifacts",
					report.recommendationSummary.recommendationLines
							+ " saved recommendation line(s), "
							+ report.recommendationSummary.uniqueOutputs + " unique output(s)",
					Math.min(100, report.recommendationSummary.recommendationLines),
					GREEN);
			g2.dispose();
		}
	}

	private final class ScalingInsightBoard extends JPanel
	{
		private List<ScalingRunRecord> records =
				Collections.emptyList();
		private String selectedAlgorithm = "";
		private ScalingSeries series = ScalingSeries.empty("");

		ScalingInsightBoard()
		{
			setOpaque(false);
			setPreferredSize(new Dimension(1180, 470));
		}

		void setRecords(
				List<ScalingRunRecord> records,
				String selectedAlgorithm)
		{
			this.records = records == null
					? Collections.<ScalingRunRecord>emptyList()
					: new ArrayList<ScalingRunRecord>(records);
			this.selectedAlgorithm = selectedAlgorithm == null
					? "" : selectedAlgorithm;
			this.series = ScalingSeries.from(
					this.records, this.selectedAlgorithm, design);
			repaint();
		}

		protected void paintComponent(Graphics g)
		{
			super.paintComponent(g);
			Graphics2D g2 = prepare(g);
			int w = getWidth();
			int h = getHeight();
			paintSoftBackground(g2, w, h);
			if (series.points.isEmpty())
			{
				paintEmpty(g2, w, h,
						"No completed node-scaling runs yet.",
						"Run the matrix, then this graph will compare completed archives by node count.");
				g2.dispose();
				return;
			}

			Color accent = colorForAlgorithm(series.algorithmId);
			NebulaDesign.paintEyebrow(g2, "Multi-node effect", 24, 32,
					NebulaDesign.TEXT_TERTIARY);
			g2.setFont(NebulaDesign.fontSemiBold(20f));
			g2.setColor(TEXT);
			g2.drawString(series.algorithmName + " node scaling", 24, 60);
			g2.setFont(NebulaDesign.fontBody(12f));
			g2.setColor(MUTED);
			g2.drawString(truncate(series.profileDescription, Math.max(42, w / 9)),
					24, 82);

			if (w >= 940)
			{
				drawScalingMetric(g2, w - 560, 26, 170, "Fastest worker",
						series.fastestComputeLabel(), GREEN);
				drawScalingMetric(g2, w - 380, 26, 170, "Worker speedup",
						series.bestComputeSpeedupLabel(), accent);
				drawScalingMetric(g2, w - 200, 26, 176, "Full elapsed",
						series.fullElapsedLabel(), AMBER);
			}

			int left = 78;
			int right = 34;
			int fullLaneTop = 108;
			int fullLaneHeight = 52;
			int top = 184;
			int bottom = 100;
			int plotW = Math.max(160, w - left - right);
			int plotH = Math.max(160, h - top - bottom);
			int baseY = top + plotH;
			long maxRuntime = Math.max(1L, series.maxRuntimeMs());
			long maxCompute = Math.max(1L, series.maxComputeMs());
			int count = series.points.size();
			int slot = Math.max(1, plotW / Math.max(1, count));
			int barW = Math.max(26, Math.min(84, slot / 2));

			g2.setColor(BORDER);
			g2.setStroke(new BasicStroke(1f));
			for (int i = 0; i <= 4; i++)
			{
				int y = top + plotH - i * plotH / 4;
				g2.drawLine(left, y, left + plotW, y);
				long value = maxCompute * i / 4;
				g2.setColor(NebulaDesign.TEXT_TERTIARY);
				g2.setFont(NebulaDesign.fontMono(10f));
				String label = formatElapsed(value);
				g2.drawString(label.equals("--") ? "0s" : label,
						18, y + 4);
				g2.setColor(BORDER);
			}
			g2.setColor(NebulaDesign.BORDER_STRONG);
			g2.drawLine(left, top, left, baseY);
			g2.drawLine(left, baseY, left + plotW, baseY);
			g2.setFont(NebulaDesign.fontSection());
			g2.setColor(NebulaDesign.TEXT_TERTIARY);
			g2.drawString("WORKER COMPUTE", left, top - 10);
			g2.drawString("FULL RUN ELAPSED", left, fullLaneTop - 8);

			g2.setColor(track());
			g2.fillRoundRect(left, fullLaneTop, plotW, fullLaneHeight, 14, 14);
			g2.setColor(BORDER);
			g2.drawRoundRect(left, fullLaneTop, plotW, fullLaneHeight, 14, 14);

			for (int i = 0; i < count; i++)
			{
				ScalingPoint point = series.points.get(i);
				int centerX = left + slot / 2 + i * slot;
				int railMaxW = Math.max(24, Math.min(112, slot - 18));
				int railW = Math.max(18,
						(int) Math.round(railMaxW * point.runtimeMs
								/ (double) maxRuntime));
				int railX = centerX - railMaxW / 2;
				int railY = fullLaneTop + 24;
				g2.setColor(glass(210));
				g2.fillRoundRect(railX, railY, railMaxW, 12, 10, 10);
				Color railColor = point == series.fastestPoint
						? GREEN : NebulaDesign.TEXT_TERTIARY;
				g2.setColor(NebulaDesign.withAlpha(railColor, 150));
				g2.fillRoundRect(railX, railY, railW, 12, 10, 10);
				g2.setColor(point == series.fastestPoint ? GREEN : MUTED);
				g2.setFont(NebulaDesign.fontMono(10f));
				String full = formatElapsed(point.runtimeMs);
				g2.drawString(full,
						centerX - g2.getFontMetrics().stringWidth(full) / 2,
						fullLaneTop + 17);

				long computeValue = point.computeMs > 0L
						? point.computeMs : point.runtimeMs;
				int barH = Math.max(10,
						(int) Math.round(computeValue * (plotH - 24)
								/ (double) maxCompute));
				int x = centerX - barW / 2;
				int y = baseY - barH;
				Color pointColor = pointColor(point, accent);
				if (!StudioNightTheme.isNightMode())
				{
					g2.setColor(NebulaDesign.withAlpha(NebulaDesign.DAY_TEXT_PRIMARY, 20));
					g2.fillRoundRect(x + 3, y + 5, barW, barH, 12, 12);
				}
				g2.setPaint(new GradientPaint(
						x, y, NebulaDesign.withAlpha(pointColor, 225),
						x, baseY, NebulaDesign.withAlpha(pointColor, 110)));
				g2.fillRoundRect(x, y, barW, barH, 12, 12);
				g2.setColor(glass(130));
				g2.drawRoundRect(x, y, barW, barH, 12, 12);

				boolean fastestWorker =
						computeValue == fastestComputeMs(series.points);
				int dotY = Math.max(top + 18,
						y - (fastestWorker ? 24 : 14));
				g2.setColor(NebulaDesign.withAlpha(pointColor, 36));
				g2.fillOval(centerX - 20, dotY - 20, 40, 40);
				g2.setColor(pointColor);
				g2.fillOval(centerX - 6, dotY - 6, 12, 12);

				String speed = formatSpeedup(point.computeSpeedup);
				g2.setFont(NebulaDesign.fontMono(11f));
				int pillW = Math.max(48,
						g2.getFontMetrics().stringWidth(speed) + 16);
				g2.setColor(glass(226));
				g2.fillRoundRect(centerX - pillW / 2, y - 31, pillW, 22, 11, 11);
				g2.setColor(pointColor);
				g2.drawRoundRect(centerX - pillW / 2, y - 31, pillW, 22, 11, 11);
				g2.drawString(speed,
						centerX - g2.getFontMetrics().stringWidth(speed) / 2,
						y - 16);

				g2.setColor(TEXT);
				g2.setFont(NebulaDesign.fontMonoMedium(12f));
				String runtime = formatElapsed(computeValue);
				g2.drawString(runtime,
						centerX - g2.getFontMetrics().stringWidth(runtime) / 2,
						baseY + 24);
				g2.setColor(MUTED);
				g2.setFont(NebulaDesign.fontMedium(11f));
				String nodeText = point.nodes + " node"
						+ (point.nodes == 1 ? "" : "s");
				g2.drawString(nodeText,
						centerX - g2.getFontMetrics().stringWidth(nodeText) / 2,
						baseY + 42);
				if (point.repetitions > 1)
				{
					String reps = point.repetitions + " reps median";
					g2.drawString(reps,
							centerX - g2.getFontMetrics().stringWidth(reps) / 2,
							baseY + 58);
				}
				else
				{
					String fullText = "full " + formatElapsed(point.runtimeMs);
					g2.drawString(fullText,
							centerX - g2.getFontMetrics().stringWidth(fullText) / 2,
							baseY + 58);
				}
				if (fastestWorker)
				{
					g2.setColor(GREEN);
					g2.setFont(NebulaDesign.fontSection());
					String tag = "FASTEST WORKER";
					g2.drawString(tag,
							centerX - g2.getFontMetrics().stringWidth(tag) / 2,
							Math.max(top + 12, y - 40));
				}
			}

			g2.setColor(NebulaDesign.TEXT_TERTIARY);
			g2.setFont(NebulaDesign.fontBody(11f));
			g2.drawString("Main bars show worker compute speedup from resultsTiming. "
					+ "Top rails show full run elapsed from the same manifest/table. Baseline: "
					+ series.baselineNodes + " node"
					+ (series.baselineNodes == 1 ? "." : "s."),
					left, h - 16);
			if (series.skippedMissingNodes > 0)
			{
				String skipped = series.skippedMissingNodes
						+ " planned node count(s) have no completed timing yet.";
				g2.drawString(skipped,
						w - 34 - g2.getFontMetrics().stringWidth(skipped),
						h - 16);
			}
			g2.dispose();
		}

		private long fastestComputeMs(List<ScalingPoint> points)
		{
			long fastest = Long.MAX_VALUE;
			for (ScalingPoint point : points)
			{
				long value = point.computeMs > 0L
						? point.computeMs : point.runtimeMs;
				if (value > 0L && value < fastest)
				{
					fastest = value;
				}
			}
			return fastest == Long.MAX_VALUE ? -1L : fastest;
		}

		private void drawScalingMetric(
				Graphics2D g2,
				int x,
				int y,
				int w,
				String title,
				String value,
				Color color)
		{
			g2.setColor(glass(235));
			g2.fillRoundRect(x, y, w, 50, 12, 12);
			g2.setColor(BORDER);
			g2.drawRoundRect(x, y, w, 50, 12, 12);
			g2.setColor(color);
			g2.fillOval(x + 14, y + 11, 7, 7);
			g2.setColor(NebulaDesign.TEXT_TERTIARY);
			g2.setFont(NebulaDesign.fontSection());
			g2.drawString(title.toUpperCase(Locale.ROOT), x + 27, y + 18);
			g2.setColor(TEXT);
			g2.setFont(NebulaDesign.fontSemiBold(15f));
			g2.drawString(truncate(value, 18), x + 14, y + 39);
		}

		private Color pointColor(ScalingPoint point, Color accent)
		{
			if (point.computeSpeedup >= 1.08)
			{
				return GREEN;
			}
			if (point.computeSpeedup < 0.98)
			{
				return RED;
			}
			if (Math.abs(point.computeSpeedup - 1.0) < 0.01)
			{
				return accent;
			}
			return AMBER;
		}
	}

	private final class CapsuleBoard extends JPanel
	{
		private ExperimentObservatoryEngine.ObservatoryReport report;
		private ExperimentObservatoryEngine.ExperimentDesign design;

		CapsuleBoard()
		{
			setOpaque(false);
			setPreferredSize(new Dimension(360, 260));
		}

		void setReport(
				ExperimentObservatoryEngine.ObservatoryReport report,
				ExperimentObservatoryEngine.ExperimentDesign design)
		{
			this.report = report;
			this.design = design;
		}

		protected void paintComponent(Graphics g)
		{
			super.paintComponent(g);
			Graphics2D g2 = prepare(g);
			int w = getWidth();
			int h = getHeight();
			paintSoftBackground(g2, w, h);
			if (report == null)
			{
				paintEmpty(g2, w, h,
						"No capsule yet.",
						"Analyze artifacts to compute dataset hash, code state, and environment.");
				g2.dispose();
				return;
			}
			NebulaDesign.paintEyebrow(g2, "Reproducibility capsule", 20, 30,
					NebulaDesign.TEXT_TERTIARY);
			drawCapsuleRow(g2, 22, 58, w - 44, "Capsule ID", report.capsuleId, BLUE);
			drawCapsuleRow(g2, 22, 106, w - 44, "Dataset SHA-256",
					shortHash(report.datasetHash), GREEN);
			drawCapsuleRow(g2, 22, 154, w - 44, "Git commit",
					report.environment.gitCommit, PURPLE);
			drawCapsuleRow(g2, 22, 202, w - 44, "Java / Python",
					report.environment.javaVersion + " | " + report.environment.pythonVersion,
					AMBER);
			g2.dispose();
		}
	}

	private final class ArtifactBoard extends JPanel
	{
		private ExperimentObservatoryEngine.ObservatoryReport report;

		ArtifactBoard()
		{
			setOpaque(false);
			setPreferredSize(new Dimension(360, 260));
			observatoryLog.setEditable(false);
			observatoryLog.setFont(NebulaDesign.fontMono(12f));
			observatoryLog.setForeground(NebulaDesign.CONSOLE_TEXT);
			observatoryLog.setBackground(NebulaDesign.CONSOLE);
			observatoryLog.setCaretColor(NebulaDesign.CONSOLE_TEXT);
			observatoryLog.setSelectionColor(NebulaDesign.SELECTION);
			observatoryLog.setBorder(new EmptyBorder(12, 12, 12, 12));
			observatoryLog.setText("DSMP OBSERVATORY LOG\nReady. No artifact scan yet.\n");
			setLayout(new BorderLayout(0, 8));
			JScrollPane scroll = new JScrollPane(observatoryLog);
			scroll.setBorder(BorderFactory.createLineBorder(BORDER));
			scroll.getViewport().setBackground(NebulaDesign.CONSOLE);
			scroll.setBackground(NebulaDesign.CONSOLE);
			add(scroll, BorderLayout.CENTER);
		}

		void setReport(ExperimentObservatoryEngine.ObservatoryReport report)
		{
			this.report = report;
			if (report != null)
			{
				StringBuilder b = new StringBuilder();
				b.append("DSMP OBSERVATORY LOG\n");
				b.append("Dataset: ").append(report.design.datasetName).append('\n');
				b.append("Capsule: ").append(report.capsuleId).append('\n');
				b.append("Edge files: ").append(report.inventory.edgeFiles).append('\n');
				b.append("Node maps: ").append(report.inventory.nodeMapFiles).append('\n');
				b.append("Reports: ").append(report.inventory.reportFiles).append('\n');
				b.append("Recommendation artifacts: ")
						.append(report.inventory.recommendationArtifacts).append('\n');
				b.append("Timing lines: ").append(report.timingSummary.stageLineCount).append('\n');
				if (!report.warnings.isEmpty())
				{
					b.append("\nWARNINGS\n");
					for (String warning : report.warnings)
					{
						b.append("- ").append(warning).append('\n');
					}
				}
				observatoryLog.setText(b.toString());
			}
		}
	}

	private final class MatrixTableModel extends AbstractTableModel
	{
		private final String[] columns =
				new String[] {"Run", "Status", "Algorithm", "Nodes", "Batch",
						"Policy", "Graph", "Delay", "Repeat", "Elapsed", "Archive"};
		private List<ExperimentObservatoryEngine.RunSpec> runs =
				new ArrayList<ExperimentObservatoryEngine.RunSpec>();
		private List<RunExecutionState> states =
				new ArrayList<RunExecutionState>();

		void setRuns(List<ExperimentObservatoryEngine.RunSpec> runs)
		{
			this.runs = runs == null
					? new ArrayList<ExperimentObservatoryEngine.RunSpec>()
					: new ArrayList<ExperimentObservatoryEngine.RunSpec>(runs);
			this.states = new ArrayList<RunExecutionState>();
			fireTableDataChanged();
		}

		void setStates(List<RunExecutionState> states)
		{
			this.states = states == null
					? new ArrayList<RunExecutionState>()
					: new ArrayList<RunExecutionState>(states);
			ArrayList<ExperimentObservatoryEngine.RunSpec> stateRuns =
					new ArrayList<ExperimentObservatoryEngine.RunSpec>();
			for (RunExecutionState state : this.states)
			{
				stateRuns.add(state.run);
			}
			this.runs = stateRuns;
			fireTableDataChanged();
		}

		public int getRowCount()
		{
			return runs.size();
		}

		public int getColumnCount()
		{
			return columns.length;
		}

		public String getColumnName(int column)
		{
			return columns[column];
		}

		public Object getValueAt(int row, int column)
		{
			ExperimentObservatoryEngine.RunSpec run = runs.get(row);
			RunExecutionState state = row < states.size() ? states.get(row) : null;
			switch (column)
			{
				case 0:
					return run.id();
				case 1:
					return state == null ? "PLANNED" : state.status;
				case 2:
					return run.algorithmName;
				case 3:
					return Integer.valueOf(run.nodes);
				case 4:
					return Integer.valueOf(run.batchUsers);
				case 5:
					return run.policy;
				case 6:
					return run.graphEnabled ? "on" : "off";
				case 7:
					return run.simulateTweetDelay ? "on" : "off";
				case 8:
					return Integer.valueOf(run.repetition);
				case 9:
					return state == null ? "--" : formatElapsed(state.elapsedMs());
				case 10:
					return state == null ? "--" : state.archiveLabel();
				default:
					return "";
			}
		}
	}

	private static final class HeaderCellRenderer extends DefaultTableCellRenderer
	{
		public Component getTableCellRendererComponent(
				JTable table,
				Object value,
				boolean isSelected,
				boolean hasFocus,
				int row,
				int column)
		{
			Component c = super.getTableCellRendererComponent(
					table, value, isSelected, hasFocus, row, column);
			c.setFont(NebulaDesign.fontMedium(11f));
			c.setForeground(MUTED);
			c.setBackground(NebulaDesign.SURFACE_RAISED);
			setOpaque(true);
			setHorizontalAlignment(SwingConstants.LEFT);
			setBorder(BorderFactory.createCompoundBorder(
					BorderFactory.createMatteBorder(0, 0, 1, 0,
							NebulaDesign.BORDER_STRONG),
					new EmptyBorder(6, 8, 6, 8)));
			return c;
		}
	}

	private static final class SoftCellRenderer extends DefaultTableCellRenderer
	{
		public Component getTableCellRendererComponent(
				JTable table,
				Object value,
				boolean isSelected,
				boolean hasFocus,
				int row,
				int column)
		{
			Component c = super.getTableCellRendererComponent(
					table, value, isSelected, hasFocus, row, column);
			boolean numeric = column == 3 || column == 4
					|| column == 8 || column == 9;
			c.setFont(numeric
					? NebulaDesign.fontMono(12f)
					: NebulaDesign.fontBody(12f));
			setHorizontalAlignment(numeric
					? SwingConstants.RIGHT : SwingConstants.LEFT);
			Color tone = TEXT;
			if (column == 1 && value != null)
			{
				String status = value.toString();
				if ("COMPLETE".equals(status))
				{
					tone = GREEN;
				}
				else if ("FAILED".equals(status))
				{
					tone = RED;
				}
				else if ("SKIPPED".equals(status) || "STOPPING".equals(status))
				{
					tone = AMBER;
				}
				else if ("PLANNED".equals(status))
				{
					tone = MUTED;
				}
				else
				{
					tone = BLUE;
				}
				c.setFont(NebulaDesign.fontMedium(11.5f));
			}
			c.setForeground(tone);
			if (!isSelected)
			{
				c.setBackground(SURFACE);
			}
			setBorder(new EmptyBorder(0, 8, 0, 8));
			return c;
		}
	}

	private static final class ScrollablePanel extends JPanel implements Scrollable
	{
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
			return Math.max(24, visibleRect.height - 60);
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

	private Graphics2D prepare(Graphics g)
	{
		Graphics2D g2 = (Graphics2D) g.create();
		NebulaDesign.prepare(g2);
		return g2;
	}

	private void paintSoftBackground(Graphics2D g2, int w, int h)
	{
		g2.setColor(themed(NebulaDesign.DAY_SURFACE_ALT, NebulaDesign.NIGHT_CANVAS_TOP));
		g2.fillRoundRect(0, 0, Math.max(1, w - 1), Math.max(1, h - 1),
				NebulaDesign.RADIUS_CONSOLE, NebulaDesign.RADIUS_CONSOLE);
		g2.setColor(BORDER);
		g2.drawRoundRect(0, 0, Math.max(1, w - 1), Math.max(1, h - 1),
				NebulaDesign.RADIUS_CONSOLE, NebulaDesign.RADIUS_CONSOLE);
	}

	private void paintEmpty(Graphics2D g2, int w, int h, String title, String subtitle)
	{
		g2.setColor(glass(215));
		g2.fillRoundRect(24, h / 2 - 42, w - 48, 84, 12, 12);
		g2.setColor(BORDER);
		g2.drawRoundRect(24, h / 2 - 42, w - 48, 84, 12, 12);
		g2.setColor(TEXT);
		g2.setFont(NebulaDesign.fontSemiBold(15f));
		g2.drawString(ellipsize(title, g2.getFontMetrics(), w - 92), 44, h / 2 - 8);
		g2.setColor(MUTED);
		g2.setFont(NebulaDesign.fontBody(12f));
		g2.drawString(ellipsize(subtitle, g2.getFontMetrics(), w - 92), 44, h / 2 + 16);
	}

	private void drawScoreRing(Graphics2D g2, int cx, int cy, int r, int score, Color color)
	{
		g2.setStroke(new BasicStroke(9f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g2.setColor(track());
		g2.drawArc(cx - r, cy - r, r * 2, r * 2, 90, -360);
		g2.setColor(color);
		g2.drawArc(cx - r, cy - r, r * 2, r * 2, 90,
				(int) Math.round(-360.0 * Math.max(0, Math.min(100, score)) / 100.0));
		g2.setFont(NebulaDesign.fontSemiBold(26f));
		g2.setColor(TEXT);
		String s = String.valueOf(score);
		g2.drawString(s, cx - g2.getFontMetrics().stringWidth(s) / 2, cy + 9);
	}

	private void drawInsightBar(
			Graphics2D g2,
			int x,
			int y,
			int w,
			String title,
			String text,
			int pct,
			Color color)
	{
		g2.setColor(glass(225));
		g2.fillRoundRect(x, y, w, 38, 10, 10);
		g2.setColor(BORDER);
		g2.drawRoundRect(x, y, w, 38, 10, 10);
		g2.setColor(color);
		g2.fillRoundRect(x, y + 8, 3, 22, 3, 3);
		g2.setColor(TEXT);
		g2.setFont(NebulaDesign.fontMedium(12f));
		g2.drawString(title, x + 14, y + 15);
		g2.setColor(MUTED);
		g2.setFont(NebulaDesign.fontBody(11f));
		g2.drawString(truncate(text, 78), x + 120, y + 15);
		g2.setColor(track());
		g2.fillRoundRect(x + 120, y + 23, w - 140, 6, 6, 6);
		g2.setColor(color);
		g2.fillRoundRect(x + 120, y + 23,
				Math.max(4, (w - 140) * Math.max(0, Math.min(100, pct)) / 100),
				6, 6, 6);
	}

	private void drawCapsuleRow(Graphics2D g2, int x, int y, int w, String title, String value, Color color)
	{
		g2.setColor(glass(230));
		g2.fillRoundRect(x, y, w, 34, 10, 10);
		g2.setColor(BORDER);
		g2.drawRoundRect(x, y, w, 34, 10, 10);
		g2.setColor(color);
		g2.fillRoundRect(x, y + 7, 3, 20, 3, 3);
		g2.setColor(NebulaDesign.TEXT_TERTIARY);
		g2.setFont(NebulaDesign.fontSection());
		g2.drawString(title.toUpperCase(Locale.ROOT), x + 14, y + 13);
		g2.setColor(TEXT);
		g2.setFont(NebulaDesign.fontMono(11.5f));
		g2.drawString(truncate(value, 38), x + 14, y + 28);
	}

	private int percentFromMs(long ms)
	{
		if (ms <= 0)
		{
			return 10;
		}
		if (ms < 1000)
		{
			return 95;
		}
		if (ms < 10000)
		{
			return 76;
		}
		if (ms < 60000)
		{
			return 52;
		}
		return 28;
	}

	private static String truncate(String value, int max)
	{
		if (value == null)
		{
			return "";
		}
		return value.length() <= max ? value : value.substring(0, Math.max(0, max - 3)) + "...";
	}

	private static String formatElapsed(long millis)
	{
		if (millis <= 0L)
		{
			return "--";
		}
		long seconds = millis / 1000L;
		long minutes = seconds / 60L;
		long hours = minutes / 60L;
		seconds %= 60L;
		minutes %= 60L;
		if (hours > 0L)
		{
			return hours + "h " + minutes + "m";
		}
		if (minutes > 0L)
		{
			return minutes + "m " + seconds + "s";
		}
		return seconds + "s";
	}

	private static String shortHash(String value)
	{
		if (value == null || value.length() <= 18)
		{
			return value == null ? "" : value;
		}
		return value.substring(0, 14) + "..." + value.substring(value.length() - 6);
	}
}
