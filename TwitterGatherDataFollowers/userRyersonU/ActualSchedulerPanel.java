package TwitterGatherDataFollowers.userRyersonU;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
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
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.Scrollable;
import javax.swing.JSlider;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;

final class ActualSchedulerPanel extends JPanel
		implements NodeExecutionMonitor.Listener
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
	private static final Color PURPLE = NebulaDesign.PURPLE;
	private static final Color RED = NebulaDesign.DANGER;
	private static final int BTN_PRIMARY = 0;
	private static final int BTN_SUCCESS = 1;
	private static final int BTN_NEUTRAL = 2;
	private static final int BTN_DANGER = 3;
	private static final DecimalFormat DECIMAL = new DecimalFormat("0.##");

	private final ControllerAgentGui owner;
	private final JComboBox<RecommendationSchedulerPlan.Mode> modeBox =
			new JComboBox<RecommendationSchedulerPlan.Mode>(
					RecommendationSchedulerPlan.Mode.values());
	private final JComboBox<SchedulerLabEngine.SchedulingPolicy> policyBox =
			new JComboBox<SchedulerLabEngine.SchedulingPolicy>(
					SchedulerLabEngine.SchedulingPolicy.values());
	private final JButton applyButton = button("Apply to simulator", BTN_PRIMARY);
	private final JButton restoreButton =
			button("Restore DSMP default", BTN_NEUTRAL);
	private final JButton capacityButton =
			button("Node capacity", BTN_NEUTRAL);
	private final JButton timelineButton =
			button("Save live run", BTN_SUCCESS);
	private final JComboBox<Integer> liveNodeBox = new JComboBox<Integer>();
	private final JComboBox<SchedulerLabEngine.SchedulingPolicy> livePolicyBox =
			new JComboBox<SchedulerLabEngine.SchedulingPolicy>(
					SchedulerLabEngine.SchedulingPolicy.values());
	private final JSlider liveCapacitySlider = new JSlider(10, 200, 100);
	private final JButton livePauseButton =
			button("Pause node", BTN_NEUTRAL);
	private final JButton liveResumeButton =
			button("Resume", BTN_NEUTRAL);
	private final JButton liveUnavailableButton =
			button("Unavailable", BTN_DANGER);
	private final JButton liveAvailableButton =
			button("Available", BTN_SUCCESS);
	private final JButton liveRedirectButton =
			button("Move pending here", BTN_SUCCESS);
	private final JButton liveApplyPolicyButton =
			button("Switch remaining policy", BTN_PRIMARY);
	private final JButton liveResetButton =
			button("Reset live controls", BTN_NEUTRAL);
	private final JLabel liveControlStatus = new JLabel("Live controls idle.");
	private final JLabel modeMetric = metricValue("--");
	private final JLabel policyMetric = metricValue("--");
	private final JLabel nodeMetric = metricValue("--");
	private final JLabel heapMetric = metricValue("--");
	private final JLabel contractMetric = metricValue("--");
	private final JLabel statusPill = pill("READY", GREEN);
	private final JLabel statusLabel = themedLabel(
			"Choose a scheduling contract before running the simulator.",
			TEXT,
			NebulaDesign.fontMedium(12f));
	private final JTextArea planLog = new JTextArea();
	private final PlanCanvas planCanvas = new PlanCanvas();
	private final WorkQueueCanvas queueCanvas = new WorkQueueCanvas();
	private final TimelineCanvas timelineCanvas = new TimelineCanvas();
	private final HistoryComparisonCanvas historyCanvas =
			new HistoryComparisonCanvas();
	private final NodeAssignmentTableModel nodeTableModel =
			new NodeAssignmentTableModel();
	private final JTable nodeTable = new JTable(nodeTableModel);
	private final ScheduleHistoryTableModel historyTableModel =
			new ScheduleHistoryTableModel();
	private final JTable historyTable = new JTable(historyTableModel);
	private final ArrayList<ScheduleHistoryEntry> runHistory =
			new ArrayList<ScheduleHistoryEntry>();
	private final Timer timelineTimer;

	private SchedulerLabEngine.Result previewResult;
	private RecommendationSchedulerPlan previewPlan =
			RecommendationSchedulerPlan.defaultPlan();
	private int runSequence;
	private NodeExecutionMonitor.Snapshot actualSnapshot =
			NodeExecutionMonitor.snapshot();
	private long actualNow = System.currentTimeMillis();
	private String lastAutoSavedRunKey = "";
	private boolean updatingLiveControls;

	ActualSchedulerPanel(ControllerAgentGui owner)
	{
		super(new BorderLayout(0, 14));
		this.owner = owner;
		setOpaque(false);
		setBackground(BACKGROUND);
		setBorder(new EmptyBorder(14, 16, 14, 16));
		timelineTimer = new Timer(22, new ActionListener()
		{
			public void actionPerformed(ActionEvent event)
			{
				refreshActualTimelineClock();
			}
		});
		timelineTimer.setCoalesce(true);
		configureLiveControls();
		buildInterface();
		installActions();
		refreshFromSimulator();
	}

	public void addNotify()
	{
		super.addNotify();
		NodeExecutionMonitor.addListener(this);
		timelineTimer.start();
	}

	public void removeNotify()
	{
		timelineTimer.stop();
		NodeExecutionMonitor.removeListener(this);
		super.removeNotify();
	}

	public void onSnapshot(final NodeExecutionMonitor.Snapshot snapshot)
	{
		if (snapshot == null)
		{
			return;
		}
		if (SwingUtilities.isEventDispatchThread())
		{
			applyActualSnapshot(snapshot);
		}
		else
		{
			SwingUtilities.invokeLater(new Runnable()
			{
				public void run()
				{
					applyActualSnapshot(snapshot);
				}
			});
		}
	}

	private void applyActualSnapshot(NodeExecutionMonitor.Snapshot snapshot)
	{
		actualSnapshot = snapshot;
		actualNow = System.currentTimeMillis();
		timelineCanvas.setSnapshot(
				actualSnapshot,
				timelinePlanForSnapshot(actualSnapshot),
				actualNow);
		queueCanvas.setContext(
				actualSnapshot,
				timelinePlanForSnapshot(actualSnapshot),
				currentAlgorithm(),
				currentTargetCount(),
				actualNow);
		updateLiveControlsDisplay();
		maybeRecordCompletedActualRun(snapshot);
	}

	void refreshFromSimulator()
	{
		int nodes = Math.max(1, ownerNodeCount());
		int heap = Math.max(
				RuntimeResourceSettings.MIN_HEAP_MB,
				ownerHeapSetting());
		NodeCapacityRegistry.configureForNodeCount(nodes, heap);
		RecommendationSchedulerPlan plan =
				RecommendationSchedulerRegistry.snapshot();
		modeBox.setSelectedItem(plan.getMode());
		policyBox.setSelectedItem(plan.getPolicy());
		updatePreview(plan);
		updateLiveControlsDisplay();
	}

	private void buildInterface()
	{
		add(createHeader(), BorderLayout.NORTH);

		JPanel center = new JPanel(new BorderLayout(0, 12));
		center.setOpaque(false);
		center.add(createControlBand(), BorderLayout.NORTH);
		center.add(createMainGrid(), BorderLayout.CENTER);
		add(center, BorderLayout.CENTER);
		add(createStatusBar(), BorderLayout.SOUTH);
	}

	private void configureLiveControls()
	{
		liveCapacitySlider.setMajorTickSpacing(50);
		liveCapacitySlider.setMinorTickSpacing(10);
		liveCapacitySlider.setPaintTicks(true);
		liveCapacitySlider.setPaintLabels(true);
		liveCapacitySlider.setOpaque(false);
		liveControlStatus.setFont(font(Font.BOLD, 11));
		liveControlStatus.setForeground(MUTED);
		refreshLiveNodeChoices();
	}

	private JComponent createHeader()
	{
		JPanel header = new HeroCardPanel(new BorderLayout(24, 0));
		header.setBorder(new EmptyBorder(13, 20, 15, 18));

		JPanel titleStack = new JPanel();
		titleStack.setOpaque(false);
		titleStack.setLayout(new BoxLayout(titleStack, BoxLayout.Y_AXIS));
		JLabel eyebrow = themedLabel("SCHEDULING", BLUE, NebulaDesign.fontSection());
		eyebrow.setAlignmentX(Component.LEFT_ALIGNMENT);
		JLabel title = themedLabel("Scheduler Lab", TEXT, NebulaDesign.fontDisplay());
		JLabel subtitle = themedLabel(
				"Apply a real scheduling plan to safe simulator assignment points; Doc2Vec uses live dynamic candidate shards.",
				MUTED,
				NebulaDesign.fontBody(12.5f));
		subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
		JPanel titleRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
		titleRow.setOpaque(false);
		titleRow.setAlignmentX(Component.LEFT_ALIGNMENT);
		titleRow.add(title);
		titleRow.add(pill("LIVE SIMULATOR", GREEN));
		titleStack.add(eyebrow);
		titleStack.add(Box.createRigidArea(new Dimension(0, 2)));
		titleStack.add(titleRow);
		titleStack.add(Box.createRigidArea(new Dimension(0, 2)));
		titleStack.add(subtitle);
		titleStack.add(Box.createRigidArea(new Dimension(0, 6)));
		titleStack.add(createAttributionBlock());

		header.add(titleStack, BorderLayout.WEST);
		header.add(new HeaderSignalPanel(), BorderLayout.CENTER);
		return header;
	}

	private JComponent createControlBand()
	{
		JPanel band = surface(new BorderLayout(16, 10));
		band.setBorder(new EmptyBorder(14, 16, 14, 16));

		JPanel controls = new JPanel(new BorderLayout(0, 10));
		controls.setOpaque(false);

		JPanel selectors = new JPanel(new GridLayout(1, 2, 12, 0));
		selectors.setOpaque(false);
		selectors.add(control("Active mode", modeBox));
		selectors.add(control("Scheduling policy", policyBox));

		JPanel actions = new JPanel(new GridLayout(1, 4, 10, 0));
		actions.setOpaque(false);
		actions.add(capacityButton);
		actions.add(restoreButton);
		actions.add(timelineButton);
		actions.add(applyButton);

		JPanel actionRow = new JPanel(new BorderLayout());
		actionRow.setOpaque(false);
		actionRow.add(actions, BorderLayout.CENTER);
		controls.add(selectors, BorderLayout.CENTER);
		controls.add(actionRow, BorderLayout.SOUTH);

		JPanel metrics = new JPanel(new GridLayout(1, 5, 10, 0));
		metrics.setOpaque(false);
		metrics.add(metricCard("Mode", modeMetric, BLUE));
		metrics.add(metricCard("Policy", policyMetric, PURPLE));
		metrics.add(metricCard("Nodes", nodeMetric, BLUE));
		metrics.add(metricCard("Heap", heapMetric, GREEN));
		metrics.add(metricCard("Contract", contractMetric, AMBER));

		band.add(controls, BorderLayout.NORTH);
		band.add(metrics, BorderLayout.CENTER);
		return band;
	}

	private JComponent createMainGrid()
	{
		JPanel grid = new JPanel(new GridBagLayout());
		grid.setOpaque(false);
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.insets = new Insets(0, 0, 12, 12);
		gbc.fill = GridBagConstraints.BOTH;
		gbc.weightx = 0.55;
		gbc.weighty = 0.34;
		gbc.gridx = 0;
		gbc.gridy = 0;
		grid.add(createPlanPanel(), gbc);

		gbc.gridx = 1;
		gbc.weightx = 0.45;
		gbc.insets = new Insets(0, 0, 12, 0);
		grid.add(createContractPanel(), gbc);

		gbc.gridx = 0;
		gbc.gridy = 1;
		gbc.gridwidth = 2;
		gbc.weightx = 1.0;
		gbc.weighty = 0.32;
		gbc.insets = new Insets(0, 0, 12, 0);
		grid.add(createTimelinePanel(), gbc);

		gbc.gridwidth = 1;
		gbc.gridx = 0;
		gbc.gridy = 2;
		gbc.weightx = 0.55;
		gbc.weighty = 0.34;
		gbc.insets = new Insets(0, 0, 0, 12);
		grid.add(createNodeTablePanel(), gbc);

		gbc.gridx = 1;
		gbc.weightx = 0.45;
		gbc.insets = new Insets(0, 0, 0, 0);
		grid.add(createHistoryPanel(), gbc);
		return grid;
	}

	private JComponent createPlanPanel()
	{
		JPanel panel = section("Active Recommendation Plan");
		panel.add(planCanvas, BorderLayout.CENTER);
		return panel;
	}

	private JComponent createContractPanel()
	{
		JPanel panel = section("Live Candidate Work Queue");
		JPanel body = new JPanel(new BorderLayout(0, 8));
		body.setOpaque(false);
		JScrollPane queueScroll = new JScrollPane(queueCanvas);
		queueScroll.setBorder(BorderFactory.createEmptyBorder());
		queueScroll.setViewportBorder(BorderFactory.createEmptyBorder());
		queueScroll.getViewport().setBackground(SURFACE);
		queueScroll.setHorizontalScrollBarPolicy(
				JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		queueScroll.setVerticalScrollBarPolicy(
				JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
		body.add(queueScroll, BorderLayout.CENTER);
		panel.add(body, BorderLayout.CENTER);
		JLabel help = themedLabel(
				"Shows real candidate-shard claims. Use the Live Workload tab to pause, redirect, throttle, or switch remaining queue work.",
				NebulaDesign.TEXT_TERTIARY,
				NebulaDesign.fontBody(11.5f));
		help.setBorder(new EmptyBorder(6, 2, 0, 2));
		panel.add(help, BorderLayout.SOUTH);
		return panel;
	}

	private JComponent createLiveAdjustmentPanel()
	{
		JPanel panel = new JPanel(new BorderLayout(10, 8));
		panel.setBackground(SURFACE_ALT);
		panel.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(BORDER),
				new EmptyBorder(10, 12, 10, 12)));

		JPanel title = new JPanel(new BorderLayout(10, 2));
		title.setOpaque(false);
		JLabel heading = new JLabel("Live Workload Adjustment");
		heading.setFont(font(Font.BOLD, 14));
		heading.setForeground(TEXT);
		JLabel subheading = new JLabel(
				"Changes apply only at safe boundaries: unclaimed queue shards or future assignments.");
		subheading.setFont(font(Font.PLAIN, 11));
		subheading.setForeground(MUTED);
		title.add(heading, BorderLayout.WEST);
		title.add(subheading, BorderLayout.CENTER);
		panel.add(title, BorderLayout.NORTH);

		JPanel selectors = new JPanel(new GridLayout(1, 3, 10, 0));
		selectors.setOpaque(false);
		selectors.add(control("Node", liveNodeBox));
		selectors.add(control("Future-claim capacity", liveCapacitySlider));
		selectors.add(control("Policy for remaining shards", livePolicyBox));

		JPanel buttons = new JPanel(new GridLayout(2, 4, 8, 8));
		buttons.setOpaque(false);
		buttons.add(livePauseButton);
		buttons.add(liveResumeButton);
		buttons.add(liveUnavailableButton);
		buttons.add(liveAvailableButton);
		buttons.add(liveRedirectButton);
		buttons.add(liveApplyPolicyButton);
		buttons.add(liveResetButton);
		JLabel spacer = new JLabel("");
		spacer.setOpaque(false);
		buttons.add(spacer);

		JPanel center = new JPanel(new BorderLayout(10, 8));
		center.setOpaque(false);
		center.add(selectors, BorderLayout.NORTH);
		center.add(buttons, BorderLayout.CENTER);
		panel.add(center, BorderLayout.CENTER);
		panel.add(liveControlStatus, BorderLayout.SOUTH);
		return panel;
	}

	private JComponent createNodeTablePanel()
	{
		JPanel panel = section("Node Assignment Preview");
		configureTable(nodeTable, 32);
		nodeTable.setDefaultRenderer(Object.class, new NodeCellRenderer());
		applyColumnWidths(nodeTable,
				new int[] { 44, 88, 56, 66, 50, 56, 48, 48 });
		panel.add(wrapTable(nodeTable), BorderLayout.CENTER);
		return panel;
	}

	private static void configureTable(JTable table, int rowHeight)
	{
		table.setRowHeight(rowHeight);
		table.setFillsViewportHeight(true);
		table.setShowGrid(false);
		table.setIntercellSpacing(new Dimension(0, 0));
		table.setBackground(SURFACE);
		table.setForeground(TEXT);
		table.setSelectionBackground(NebulaDesign.SELECTION);
		table.setSelectionForeground(TEXT);
		table.setFont(NebulaDesign.fontBody(12f));
		table.getTableHeader().setDefaultRenderer(new HeaderRenderer());
		table.getTableHeader().setReorderingAllowed(false);
	}

	private static JScrollPane wrapTable(JTable table)
	{
		JScrollPane scroll = new JScrollPane(table);
		scroll.setBorder(BorderFactory.createLineBorder(BORDER));
		scroll.getViewport().setBackground(SURFACE);
		scroll.setColumnHeaderView(table.getTableHeader());
		scroll.getColumnHeader().setBackground(NebulaDesign.SURFACE_RAISED);
		scroll.setOpaque(false);
		return scroll;
	}

	private static void applyColumnWidths(JTable table, int[] widths)
	{
		int count = Math.min(widths.length, table.getColumnCount());
		for (int index = 0; index < count; index++)
		{
			table.getColumnModel().getColumn(index)
					.setPreferredWidth(widths[index]);
		}
	}

	private JComponent createTimelinePanel()
	{
		JPanel panel = section("Actual Simulator Node Timeline");
		JScrollPane timelineScroll = new JScrollPane(timelineCanvas);
		timelineScroll.setBorder(BorderFactory.createEmptyBorder());
		timelineScroll.getViewport().setBackground(SURFACE_ALT);
		timelineScroll.setHorizontalScrollBarPolicy(
				JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		timelineScroll.setVerticalScrollBarPolicy(
				JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
		panel.add(timelineScroll, BorderLayout.CENTER);
		JLabel help = themedLabel(
				"Live timing comes from the real Simulator run. Completed runs are stored in this session history.",
				NebulaDesign.TEXT_TERTIARY,
				NebulaDesign.fontBody(11.5f));
		help.setBorder(new EmptyBorder(6, 2, 0, 2));
		panel.add(help, BorderLayout.SOUTH);
		return panel;
	}

	private JComponent createHistoryPanel()
	{
		JPanel panel = section("Session Schedule History");
		JPanel body = new JPanel(new GridBagLayout());
		body.setOpaque(false);
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.insets = new Insets(0, 0, 8, 0);
		gbc.fill = GridBagConstraints.BOTH;
		gbc.weightx = 1.0;
		gbc.weighty = 0.52;
		gbc.gridx = 0;
		gbc.gridy = 0;
		body.add(historyCanvas, gbc);

		configureTable(historyTable, 28);
		historyTable.setDefaultRenderer(Object.class, new NodeCellRenderer());
		applyColumnWidths(historyTable,
				new int[] { 64, 72, 96, 110, 84, 52, 84, 84, 56, 76, 56 });
		gbc.gridy = 1;
		gbc.weighty = 0.48;
		gbc.insets = new Insets(0, 0, 0, 0);
		body.add(wrapTable(historyTable), gbc);
		panel.add(body, BorderLayout.CENTER);
		return panel;
	}

	private JComponent createLogPanel()
	{
		JPanel panel = section("Plan Log");
		planLog.setEditable(false);
		planLog.setFont(NebulaDesign.fontMono(12f));
		planLog.setForeground(NebulaDesign.CONSOLE_TEXT);
		planLog.setBackground(NebulaDesign.CONSOLE);
		planLog.setBorder(new EmptyBorder(12, 12, 12, 12));
		JScrollPane scroll = new JScrollPane(planLog);
		scroll.setBorder(BorderFactory.createLineBorder(BORDER));
		scroll.getViewport().setBackground(NebulaDesign.CONSOLE);
		panel.add(scroll, BorderLayout.CENTER);
		return panel;
	}

	private JComponent createStatusBar()
	{
		JPanel bar = surface(new BorderLayout(12, 0));
		bar.setBorder(new EmptyBorder(8, 14, 10, 14));
		bar.add(statusPill, BorderLayout.WEST);
		bar.add(statusLabel, BorderLayout.CENTER);
		return bar;
	}

	private void installActions()
	{
		ActionListener refresh = new ActionListener()
		{
			public void actionPerformed(ActionEvent event)
			{
				updatePreview(RecommendationSchedulerPlan.create(
						(RecommendationSchedulerPlan.Mode)modeBox.getSelectedItem(),
						(SchedulerLabEngine.SchedulingPolicy)policyBox.getSelectedItem(),
						RecommendationSchedulerRegistry.snapshot().getRevision(),
						ownerNodeCount(),
						ownerHeapSetting(),
						"Preview only."));
			}
		};
		modeBox.addActionListener(refresh);
		policyBox.addActionListener(refresh);

		applyButton.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent event)
			{
				applyPlan();
			}
		});
		restoreButton.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent event)
			{
				restoreDefault();
			}
		});
		capacityButton.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent event)
			{
				int nodes = ownerNodeCount();
				int heap = ownerHeapSetting();
				NodeCapacityRegistry.configureForNodeCount(nodes, heap);
				if (NodeCapacitySettingsDialog.showDialog(
						owner,
						nodes,
						heap))
				{
					NodeExecutionMonitor.refreshCapacityProfiles();
					updatePreview(RecommendationSchedulerRegistry.snapshot());
					setStatus("CAPACITY",
							"Node capacity contract updated. Apply a scheduler plan before the next run.",
							StatusTone.READY);
				}
			}
		});
		timelineButton.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent event)
			{
				saveCurrentActualRun("Manual capture");
			}
		});
		liveNodeBox.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent event)
			{
				updateLiveControlsDisplay();
			}
		});
		liveCapacitySlider.addChangeListener(new ChangeListener()
		{
			public void stateChanged(ChangeEvent event)
			{
				if (updatingLiveControls
						|| liveCapacitySlider.getValueIsAdjusting())
				{
					return;
				}
				String runId = activeLiveRunId();
				int node = selectedLiveNode();
				LiveWorkloadAdjustmentRegistry.setCapacityScale(
						runId,
						node,
						liveCapacitySlider.getValue() / 100.0);
				updateLiveControlsDisplay();
			}
		});
		livePauseButton.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent event)
			{
				int node = selectedLiveNode();
				LiveWorkloadAdjustmentRegistry.setPaused(
						activeLiveRunId(),
						node,
						true);
				updateLiveControlsDisplay();
				setStatus("LIVE",
						"Node " + node
						+ " will finish current work but stop claiming new pending shards.",
						StatusTone.READY);
			}
		});
		liveResumeButton.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent event)
			{
				int node = selectedLiveNode();
				LiveWorkloadAdjustmentRegistry.setPaused(
						activeLiveRunId(),
						node,
						false);
				updateLiveControlsDisplay();
				setStatus("LIVE",
						"Node " + node + " can claim pending shards again.",
						StatusTone.READY);
			}
		});
		liveUnavailableButton.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent event)
			{
				int node = selectedLiveNode();
				LiveWorkloadAdjustmentRegistry.setUnavailable(
						activeLiveRunId(),
						node,
						true);
				updateLiveControlsDisplay();
				setStatus("LIVE",
						"Node " + node
						+ " is unavailable for future work claims.",
						StatusTone.READY);
			}
		});
		liveAvailableButton.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent event)
			{
				int node = selectedLiveNode();
				LiveWorkloadAdjustmentRegistry.setUnavailable(
						activeLiveRunId(),
						node,
						false);
				updateLiveControlsDisplay();
				setStatus("LIVE",
						"Node " + node
						+ " is back in the future-work pool.",
						StatusTone.READY);
			}
		});
		liveRedirectButton.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent event)
			{
				int node = selectedLiveNode();
				RecommendationWorkQueueRegistry.QueueSnapshot queue =
						currentLiveQueueSnapshot();
				int waiting = queue == null ? 0 : queue.getWaitingCount();
				if (waiting <= 0)
				{
					setStatus("LIVE",
							"No pending queue shards are available to redirect.",
							StatusTone.ERROR);
					return;
				}
				LiveWorkloadAdjustmentRegistry.redirectPendingToNode(
						activeLiveRunId(),
						node,
						waiting);
				updateLiveControlsDisplay();
				setStatus("LIVE",
						"Remaining " + waiting
						+ " pending shard(s) now prefer Node " + node + ".",
						StatusTone.READY);
			}
		});
		liveApplyPolicyButton.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent event)
			{
				SchedulerLabEngine.SchedulingPolicy policy =
						(SchedulerLabEngine.SchedulingPolicy)
								livePolicyBox.getSelectedItem();
				LiveWorkloadAdjustmentRegistry.setPolicyOverride(
						activeLiveRunId(),
						policy);
				updateLiveControlsDisplay();
				setStatus("LIVE",
						"Remaining pending shards now use "
						+ (policy == null ? "the plan policy"
								: policy.getLabel()) + ".",
						StatusTone.READY);
			}
		});
		liveResetButton.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent event)
			{
				LiveWorkloadAdjustmentRegistry.resetRunControls(activeLiveRunId());
				updateLiveControlsDisplay();
				setStatus("LIVE",
						"Live workload overrides cleared for this run.",
						StatusTone.READY);
			}
		});
	}

	private void refreshLiveNodeChoices()
	{
		int selected = selectedLiveNode();
		int nodes = Math.max(1, ownerNodeCount());
		if (actualSnapshot != null && !actualSnapshot.getNodes().isEmpty())
		{
			nodes = Math.max(nodes, actualSnapshot.getNodes().size());
		}
		if (liveNodeBox.getItemCount() == nodes)
		{
			return;
		}
		updatingLiveControls = true;
		liveNodeBox.removeAllItems();
		for (int node = 1; node <= nodes; node++)
		{
			liveNodeBox.addItem(Integer.valueOf(node));
		}
		liveNodeBox.setSelectedItem(Integer.valueOf(
				Math.max(1, Math.min(selected, nodes))));
		updatingLiveControls = false;
	}

	private int selectedLiveNode()
	{
		Object value = liveNodeBox.getSelectedItem();
		if (value instanceof Integer)
		{
			return Math.max(1, ((Integer)value).intValue());
		}
		return 1;
	}

	private String activeLiveRunId()
	{
		if (actualSnapshot != null && actualSnapshot.getRunId() != null
				&& actualSnapshot.getRunId().trim().length() > 0)
		{
			return actualSnapshot.getRunId().trim();
		}
		return RecommendationSchedulerRegistry.activeRunId();
	}

	/** The run's most recent work phase (input, Doc2Vec shards, candidates, targets, ...). */
	private RecommendationWorkQueueRegistry.QueueSnapshot currentLiveQueueSnapshot()
	{
		return RecommendationWorkQueueRegistry.latestPhaseSnapshot(activeLiveRunId());
	}

	private void updateLiveControlsDisplay()
	{
		if (updatingLiveControls)
		{
			return;
		}
		refreshLiveNodeChoices();
		updatingLiveControls = true;
		String runId = activeLiveRunId();
		LiveWorkloadAdjustmentRegistry.Snapshot live =
				LiveWorkloadAdjustmentRegistry.snapshot(runId);
		int node = selectedLiveNode();
		LiveWorkloadAdjustmentRegistry.NodeControl control =
				live.controlForNode(node);
		liveCapacitySlider.setValue((int)Math.round(
				control.getCapacityScale() * 100.0));
		SchedulerLabEngine.SchedulingPolicy selectedPolicy =
				live.getPolicyOverride() == null
						? RecommendationSchedulerRegistry.snapshot().getPolicy()
						: live.getPolicyOverride();
		if (selectedPolicy != null)
		{
			livePolicyBox.setSelectedItem(selectedPolicy);
		}
		boolean hasRun = runId != null && runId.trim().length() > 0;
		String state = hasRun
				? "Run " + shortText(runId, 18) + " | Node " + node
						+ " is " + control.statusLabel() + ". "
						+ live.compactSummary()
				: "No active simulator run yet. These controls affect live queue work after the next run starts.";
		liveControlStatus.setText(state);
		updatingLiveControls = false;
		queueCanvas.repaint();
	}

	private void applyPlan()
	{
		int nodes = ownerNodeCount();
		int heap = ownerHeapSetting();
		NodeCapacityRegistry.configureForNodeCount(nodes, heap);
		RecommendationSchedulerPlan.Mode mode =
				(RecommendationSchedulerPlan.Mode)modeBox.getSelectedItem();
		SchedulerLabEngine.SchedulingPolicy policy =
				(SchedulerLabEngine.SchedulingPolicy)policyBox.getSelectedItem();
		if (mode == RecommendationSchedulerPlan.Mode.DSMP_DEFAULT)
		{
			restoreDefault();
			return;
		}
		RecommendationSchedulerPlan plan =
				RecommendationSchedulerRegistry.apply(
						mode,
						policy,
						nodes,
						heap,
						mode.getDescription());
		updatePreview(plan);
		setStatus("ACTIVE",
				plan.summary() + " will be used by the next simulator initialization.",
				StatusTone.READY);
	}

	private void restoreDefault()
	{
		int nodes = ownerNodeCount();
		int heap = ownerHeapSetting();
		NodeCapacityRegistry.resetToDefaults(nodes, heap);
		NodeExecutionMonitor.refreshCapacityProfiles();
		RecommendationSchedulerPlan plan =
				RecommendationSchedulerRegistry.restoreDefault(nodes, heap);
		LiveWorkloadAdjustmentRegistry.resetRunControls(activeLiveRunId());
		modeBox.setSelectedItem(plan.getMode());
		policyBox.setSelectedItem(plan.getPolicy());
		updatePreview(plan);
		setStatus("DEFAULT",
				"Original DSMP node assignment is restored for the next run.",
				StatusTone.READY);
	}

	private void updatePreview(RecommendationSchedulerPlan plan)
	{
		int nodes = ownerNodeCount();
		int heap = ownerHeapSetting();
		int targetCount = Math.max(1, ownerTargetUserCount());
		int algorithm = currentAlgorithm();
		int workUnits = Math.max(24, Math.min(300,
				targetCount > 1 ? targetCount : nodes * 24));
		SchedulerLabEngine.WorkloadTemplate template =
				targetCount > 1
						? SchedulerLabEngine.WorkloadTemplate.POPULATION_TARGET_SWEEP
						: SchedulerLabEngine.WorkloadTemplate.BALANCED_RECOMMENDER_RUN;
		List<SchedulerLabEngine.WorkloadUnit> jobs =
				SchedulerLabEngine.createWorkload(template, workUnits, targetCount);
		previewResult = SchedulerLabEngine.simulate(
				NodeCapacityRegistry.snapshot(),
				plan.getPolicy(),
				template,
				jobs,
				heap);
		previewPlan = plan;
		modeMetric.setText(plan.getMode().getLabel());
		policyMetric.setText(plan.getPolicy().getLabel());
		nodeMetric.setText(String.valueOf(nodes));
		heapMetric.setText(RuntimeResourceSettings.formatMegabytes(heap));
		contractMetric.setText(plan.isActive() ? "Active" : "Default");
		nodeTableModel.setResult(previewResult);
		planCanvas.setResult(previewResult, plan, algorithm);
		timelineCanvas.setSnapshot(
				actualSnapshot,
				timelinePlanForSnapshot(actualSnapshot),
				actualNow);
		queueCanvas.setContext(
				actualSnapshot,
				plan,
				algorithm,
				targetCount,
				actualNow);
		updateLiveControlsDisplay();
		planLog.setText(buildPlanLog(plan, algorithm, targetCount));
		planLog.setCaretPosition(0);
		repaint();
	}

	private void saveCurrentActualRun(String source)
	{
		if (actualSnapshot == null || actualSnapshot.getRunStartedAt() <= 0L)
		{
			setStatus("NO RUN",
					"No real simulator timing is available yet. Run the Simulator first.",
					StatusTone.ERROR);
			return;
		}
		recordActualRun(source, actualSnapshot, false);
	}

	private void maybeRecordCompletedActualRun(
			NodeExecutionMonitor.Snapshot snapshot)
	{
		if (!isActualRunTerminal(snapshot))
		{
			return;
		}
		recordActualRun("Completed simulator run", snapshot, true);
	}

	private void recordActualRun(
			String source,
			NodeExecutionMonitor.Snapshot snapshot,
			boolean auto)
	{
		String key = actualRunKey(snapshot);
		if (auto && key.equals(lastAutoSavedRunKey))
		{
			return;
		}
		runSequence++;
		RecommendationSchedulerPlan actualPlan =
				RecommendationSchedulerRegistry.snapshot();
		ScheduleHistoryEntry entry = new ScheduleHistoryEntry(
				runSequence,
				source,
				actualPlan,
				snapshot,
				currentAlgorithm());
		runHistory.add(0, entry);
		while (runHistory.size() > 18)
		{
			runHistory.remove(runHistory.size() - 1);
		}
		historyTableModel.setHistory(runHistory);
		historyCanvas.setHistory(runHistory);
		if (auto)
		{
			lastAutoSavedRunKey = key;
		}
		setStatus("CAPTURED",
				"Saved " + entry.label()
				+ ": actual makespan " + formatDuration(entry.makespanMillis)
				+ "; last node " + (entry.slowestNode <= 0
						? "unknown" : ("Node " + entry.slowestNode)) + ".",
				StatusTone.READY);
	}

	private void refreshActualTimelineClock()
	{
		actualNow = System.currentTimeMillis();
		timelineCanvas.setSnapshot(
				actualSnapshot,
				timelinePlanForSnapshot(actualSnapshot),
				actualNow);
		queueCanvas.setContext(
				actualSnapshot,
				timelinePlanForSnapshot(actualSnapshot),
				currentAlgorithm(),
				currentTargetCount(),
				actualNow);
		updateLiveControlsDisplay();
	}

	private int currentAlgorithm()
	{
		return owner == null || owner.algorithmSelectionBox == null
				? ControllerAgent.K_MEANS
				: owner.algorithmSelectionBox.getSelectedIndex();
	}

	private int currentTargetCount()
	{
		return Math.max(1, ownerTargetUserCount());
	}

	private int currentCandidateCount()
	{
		return Math.max(1, ownerCandidateEstimate());
	}

	private int ownerNodeCount()
	{
		return owner == null ? 1 : owner.currentNodeCountSetting();
	}

	private int ownerHeapSetting()
	{
		return owner == null
				? RuntimeResourceSettings.MIN_HEAP_MB
				: owner.currentHeapSetting();
	}

	private int ownerTargetUserCount()
	{
		return owner == null ? 1 : owner.getRecommendationBatchUserCount();
	}

	private int ownerCandidateEstimate()
	{
		return owner == null ? 1 : owner.getCandidateUserCountEstimate();
	}

	private RecommendationSchedulerPlan timelinePlanForSnapshot(
			NodeExecutionMonitor.Snapshot snapshot)
	{
		if (snapshot != null && snapshot.getRunStartedAt() > 0L)
		{
			return RecommendationSchedulerRegistry.snapshot();
		}
		return previewPlan;
	}

	private static boolean isActualRunTerminal(
			NodeExecutionMonitor.Snapshot snapshot)
	{
		if (snapshot == null || snapshot.getRunStartedAt() <= 0L
				|| snapshot.getNodes().isEmpty())
		{
			return false;
		}
		for (NodeExecutionMonitor.NodeSnapshot node : snapshot.getNodes())
		{
			NodeExecutionMonitor.State state = node.getState();
			if (state != NodeExecutionMonitor.State.COMPLETE
					&& state != NodeExecutionMonitor.State.FAILED
					&& state != NodeExecutionMonitor.State.STOPPED)
			{
				return false;
			}
		}
		return true;
	}

	/** How an algorithm's model stage is cut into scheduled work, for the plan log. */
	static String modelStageDescription(int algorithm)
	{
		if (algorithm == ControllerAgent.Doc2Vec)
		{
			return "Doc2Vec index shards (one per node in the original mode, four per lane otherwise), "
					+ "scored in persistent workers; exact global top n";
		}
		if (algorithm == ControllerAgent.COS_SIM || algorithm == ControllerAgent.CommonNeighbors)
		{
			return "candidate shards homed on the nodes that own them; exact global top L";
		}
		if (algorithm == ControllerAgent.K_MEANS || algorithm == ControllerAgent.K_MEANSEUCLIDEAN)
		{
			return "exact distributed clustering: each node assigns its own users, "
					+ "centroids are exact sums combined in node order";
		}
		if (algorithm == ControllerAgent.TEMPORAL_GRAPH_FOUNDATION)
		{
			return "one unit per target, scored against the whole run's candidates";
		}
		if (algorithm == ControllerAgent.MLP)
		{
			return "one training unit on the whole run's users, targets held out";
		}
		if (algorithm == ControllerAgent.SCALABLE_SVM)
		{
			return "one shared Scalable Linear SVM for the whole run";
		}
		if (RecommendationModeSupport.isSvmFamilyAlgorithm(algorithm))
		{
			return "one unit per target: an SMO model on every other user of the run";
		}
		return "not scheduled as work units";
	}

	private static String actualRunKey(NodeExecutionMonitor.Snapshot snapshot)
	{
		if (snapshot == null)
		{
			return "";
		}
		return snapshot.getRunId() + "|" + snapshot.getRunSequence()
				+ "|" + snapshot.getRunStartedAt();
	}

	private String buildPlanLog(
			RecommendationSchedulerPlan plan,
			int algorithm,
			int targetCount)
	{
		StringBuilder builder = new StringBuilder();
		builder.append("mode: ").append(plan.getMode().getLabel()).append('\n');
		builder.append("policy: ").append(plan.getPolicy().getLabel()).append('\n');
		builder.append("algorithm: ")
				.append(RecommendationModeSupport.algorithmName(algorithm))
				.append('\n');
		builder.append("target jobs: ").append(Math.max(1, targetCount)).append('\n');
		builder.append("nodes: ").append(ownerNodeCount()).append('\n');
		builder.append("heap: ")
				.append(RuntimeResourceSettings.formatMegabytes(
						ownerHeapSetting()))
				.append('\n');
		builder.append("contract: recommendations are identical for every node count, placement, "
				+ "policy, capacity profile, live control and failure pattern\n");
		builder.append("input: whole users per batch; live controls move users, never parts of a user\n");
		builder.append("model stage: ").append(modelStageDescription(algorithm)).append('\n');
		builder.append(plan.getMode() == RecommendationSchedulerPlan.Mode.DSMP_DEFAULT
				? "placement: original DSMP list scheduling; work with a home node stays there; no emulation\n"
				: "placement: " + plan.getPolicy().getLabel()
						+ "; node speed, lanes, memory, queue and link delay are emulated live\n");
		builder.append("note: ").append(plan.getNote()).append('\n');
		if (previewResult != null)
		{
			builder.append("\npreview assignments:\n");
			for (SchedulerLabEngine.NodeSummary summary : previewResult.getNodes())
			{
				builder.append("  node ")
						.append(summary.getNodeIndex())
						.append(" -> ")
						.append(summary.getAssignedJobs())
						.append(" units, profile ")
						.append(summary.getProfile().shortLabel())
						.append('\n');
			}
		}
		return builder.toString();
	}

	private enum StatusTone
	{
		READY,
		ERROR
	}

	private void setStatus(String label, String message, StatusTone tone)
	{
		statusPill.setText(label);
		statusPill.putClientProperty("nebula.tone",
				tone == StatusTone.ERROR ? RED : GREEN);
		statusPill.revalidate();
		statusPill.repaint();
		statusLabel.setText(message);
	}

	private JPanel surface(BorderLayout layout)
	{
		return new RoundedPanel(layout);
	}

	private JPanel section(String title)
	{
		JPanel panel = new RoundedPanel(new BorderLayout(0, 8));
		panel.setBorder(new EmptyBorder(14, 16, 15, 16));
		panel.add(themedLabel(title, TEXT, NebulaDesign.fontTitle()),
				BorderLayout.NORTH);
		return panel;
	}

	private JComponent control(String title, JComponent component)
	{
		JPanel panel = new JPanel(new BorderLayout(0, 4));
		panel.setOpaque(false);
		panel.add(themedLabel(title, MUTED, NebulaDesign.fontMedium(11f)),
				BorderLayout.NORTH);
		styleControl(component);
		panel.add(component, BorderLayout.CENTER);
		return panel;
	}

	private static void styleControl(JComponent component)
	{
		component.setFont(NebulaDesign.fontBody(12.5f));
		if (component instanceof JComboBox)
		{
			JComboBox<?> combo = (JComboBox<?>)component;
			combo.setUI(new FlatComboUI());
			combo.setBackground(SURFACE_ALT);
			combo.setForeground(TEXT);
			combo.setBorder(BorderFactory.createLineBorder(BORDER));
			combo.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		}
	}

	private JPanel metricCard(String label, JLabel value, Color accent)
	{
		JPanel card = new RoundedPanel(new BorderLayout(0, 3));
		card.setBorder(new EmptyBorder(9, 13, 9, 12));
		JPanel headingRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
		headingRow.setOpaque(false);
		headingRow.add(hueDot(accent));
		headingRow.add(themedLabel(
				label.toUpperCase(Locale.US),
				NebulaDesign.TEXT_TERTIARY,
				NebulaDesign.fontSection()));
		card.add(headingRow, BorderLayout.NORTH);
		card.add(value, BorderLayout.CENTER);
		return card;
	}

	private static JComponent hueDot(final Color accent)
	{
		JComponent dot = new JComponent()
		{
			protected void paintComponent(Graphics graphics)
			{
				Graphics2D g = (Graphics2D)graphics.create();
				NebulaDesign.prepare(g);
				g.setColor(accent);
				g.fillOval(0, Math.max(0, (getHeight() - 8) / 2), 8, 8);
				g.dispose();
			}
		};
		dot.setPreferredSize(new Dimension(8, 13));
		return dot;
	}

	private JPanel explainCard(String title, String detail, Color accent)
	{
		JPanel card = new JPanel(new BorderLayout(10, 0));
		card.setBackground(SURFACE_ALT);
		card.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(new Color(
						accent.getRed(), accent.getGreen(), accent.getBlue(), 95)),
				new EmptyBorder(10, 12, 10, 12)));
		JLabel number = new JLabel(" ");
		number.setOpaque(true);
		number.setBackground(accent);
		number.setPreferredSize(new Dimension(7, 1));
		JPanel text = new JPanel();
		text.setOpaque(false);
		text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
		JLabel titleLabel = new JLabel(title);
		titleLabel.setFont(font(Font.BOLD, 13));
		titleLabel.setForeground(TEXT);
		JLabel detailLabel = new JLabel(detail);
		detailLabel.setFont(font(Font.PLAIN, 12));
		detailLabel.setForeground(MUTED);
		text.add(titleLabel);
		text.add(Box.createRigidArea(new Dimension(0, 3)));
		text.add(detailLabel);
		card.add(number, BorderLayout.WEST);
		card.add(text, BorderLayout.CENTER);
		return card;
	}

	private static JButton button(String text, final int kind)
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
				int radius = NebulaDesign.RADIUS_CONTROL;
				if (kind == BTN_PRIMARY || kind == BTN_SUCCESS)
				{
					Color base = kind == BTN_PRIMARY ? BLUE : GREEN;
					Color fill;
					if (kind == BTN_PRIMARY)
					{
						fill = pressed
								? NebulaDesign.ACCENT_PRESS
								: hover ? NebulaDesign.ACCENT_HOVER
										: NebulaDesign.ACCENT;
					}
					else
					{
						fill = pressed
								? StudioNightTheme.blend(base, Color.BLACK, 0.14f)
								: hover ? StudioNightTheme.blend(
										base, Color.WHITE, 0.12f) : base;
					}
					if (hover)
					{
						g.setColor(alpha(base, 55));
						g.fillRoundRect(0, 0, w, h, radius + 3, radius + 3);
					}
					g.setColor(fill);
					g.fillRoundRect(1, 1, w - 2, h - 2, radius, radius);
					g.setColor(kind == BTN_PRIMARY
							? NebulaDesign.TEXT_ON_ACCENT
							: themed(Color.WHITE, NebulaDesign.NIGHT_TEXT_ON_ACCENT));
				}
				else
				{
					g.setColor(hover ? NebulaDesign.SURFACE_RAISED : SURFACE);
					g.fillRoundRect(1, 1, w - 2, h - 2, radius, radius);
					g.setColor(hover
							? alpha(BLUE, 120)
							: NebulaDesign.BORDER_STRONG);
					g.drawRoundRect(1, 1, w - 3, h - 3, radius, radius);
					g.setColor(kind == BTN_DANGER ? RED : TEXT);
				}
				g.setFont(getFont());
				java.awt.FontMetrics metrics = g.getFontMetrics();
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

	private static JLabel metricValue(String text)
	{
		JLabel label = themedLabel(text, TEXT, NebulaDesign.fontSemiBold(19f));
		label.setBorder(new EmptyBorder(0, 1, 0, 0));
		return label;
	}

	/**
	 * Nebula typography bridge: BOLD requests become Medium (small sizes) or
	 * SemiBold, PLAIN requests become Inter Regular.
	 */
	private static Font font(int style, int size)
	{
		if (style == Font.BOLD)
		{
			return size <= 10
					? NebulaDesign.fontMedium(size)
					: NebulaDesign.fontSemiBold(size);
		}
		return NebulaDesign.fontBody(size);
	}

	private static JLabel themedLabel(String text, final Color color, Font font)
	{
		JLabel label = new JLabel(text)
		{
			public Color getForeground()
			{
				return color;
			}
		};
		label.setFont(font);
		return label;
	}

	/**
	 * Self-painting status pill: soft tint capsule + strong hue label. The
	 * tone lives in the "nebula.tone" client property so it survives theme
	 * walks and can be switched by setStatus.
	 */
	private static JLabel pill(String text, Color tone)
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
				java.awt.FontMetrics metrics = g.getFontMetrics();
				String value = getText();
				g.drawString(value,
						(w - metrics.stringWidth(value)) / 2,
						(h - metrics.getHeight()) / 2 + metrics.getAscent());
				g.dispose();
			}
		};
		label.setFont(NebulaDesign.fontMedium(11f));
		label.setOpaque(false);
		label.setBorder(new EmptyBorder(5, 13, 5, 13));
		label.putClientProperty("nebula.tone", tone);
		return label;
	}

	private static Color themed(Color day, Color night)
	{
		return StudioNightTheme.isNightMode() ? night : day;
	}

	private static Color glass(int alphaValue)
	{
		return themed(
				StudioNightTheme.withAlpha(NebulaDesign.DAY_SURFACE, alphaValue),
				StudioNightTheme.withAlpha(
						NebulaDesign.NIGHT_SURFACE_ALT,
						Math.min(255, alphaValue + 16)));
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

	/** Soft chip tint: day = toward white, night = toward the dark surface. */
	private static Color tint(Color color, float amount)
	{
		if (StudioNightTheme.isNightMode())
		{
			float accentAmount = Math.max(0.10f, Math.min(0.42f, 1.0f - amount));
			Color base = amount > 0.70f
					? NebulaDesign.NIGHT_SURFACE
					: StudioNightTheme.NIGHT_PANEL_INK;
			return StudioNightTheme.blend(base, color, accentAmount);
		}
		int r = (int)(color.getRed() + (255 - color.getRed()) * amount);
		int g = (int)(color.getGreen() + (255 - color.getGreen()) * amount);
		int b = (int)(color.getBlue() + (255 - color.getBlue()) * amount);
		return new Color(Math.min(255, r), Math.min(255, g), Math.min(255, b));
	}

	/** Rounded Nebula card panel: soft shadow in day, lifted surface at night. */
	private static class RoundedPanel extends JPanel
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
					0, 0, w - 1, h - 3,
					NebulaDesign.RADIUS_CARD, NebulaDesign.RADIUS_CARD));
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

	/** Flat Nebula combo box delegate: quiet field + hand-drawn chevron. */
	private static final class FlatComboUI
			extends javax.swing.plaf.basic.BasicComboBoxUI
	{
		protected JButton createArrowButton()
		{
			JButton arrow = new JButton()
			{
				protected void paintComponent(Graphics graphics)
				{
					Graphics2D g = (Graphics2D)graphics.create();
					NebulaWidgets.paintChevron(
							g,
							getWidth() / 2,
							getHeight() / 2,
							7,
							MUTED);
					g.dispose();
				}
			};
			arrow.setBorder(BorderFactory.createEmptyBorder());
			arrow.setContentAreaFilled(false);
			arrow.setFocusable(false);
			arrow.setOpaque(false);
			return arrow;
		}

		public void paintCurrentValueBackground(
				Graphics graphics,
				Rectangle bounds,
				boolean hasFocus)
		{
			graphics.setColor(SURFACE_ALT);
			graphics.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
		}
	}

	/** Flat table header: raised surface, caps label, strong bottom rule. */
	private static final class HeaderRenderer extends DefaultTableCellRenderer
	{
		public Component getTableCellRendererComponent(
				JTable table,
				Object value,
				boolean isSelected,
				boolean hasFocus,
				int row,
				int column)
		{
			super.getTableCellRendererComponent(
					table, value, false, false, row, column);
			setFont(NebulaDesign.fontMedium(11f));
			setForeground(MUTED);
			setBackground(NebulaDesign.SURFACE_RAISED);
			setOpaque(true);
			setBorder(BorderFactory.createCompoundBorder(
					BorderFactory.createMatteBorder(
							0, 0, 1, 0, NebulaDesign.BORDER_STRONG),
					new EmptyBorder(6, 8, 6, 8)));
			return this;
		}
	}

	private JComponent createAttributionBlock()
	{
		JLabel credits = themedLabel(
				"Faculty advisor Dr. Abdolreza Abhari   ·   Developer Mohsen Alavi",
				NebulaDesign.TEXT_TERTIARY,
				NebulaDesign.fontMedium(11f));
		credits.setAlignmentX(Component.LEFT_ALIGNMENT);
		return credits;
	}

	private final class PlanCanvas extends JPanel
	{
		private SchedulerLabEngine.Result result;
		private RecommendationSchedulerPlan plan =
				RecommendationSchedulerPlan.defaultPlan();
		private int algorithm = ControllerAgent.K_MEANS;

		PlanCanvas()
		{
			setOpaque(false);
			setPreferredSize(new Dimension(620, 420));
		}

		void setResult(
				SchedulerLabEngine.Result result,
				RecommendationSchedulerPlan plan,
				int algorithm)
		{
			this.result = result;
			this.plan = plan;
			this.algorithm = algorithm;
			repaint();
		}

		protected void paintComponent(Graphics graphics)
		{
			super.paintComponent(graphics);
			Graphics2D g = (Graphics2D)graphics.create();
			NebulaDesign.prepare(g);
			int width = getWidth();
			int height = getHeight();
			g.setColor(SURFACE_ALT);
			g.fillRoundRect(0, 0, width - 1, height - 1, 12, 12);
			g.setColor(BORDER);
			g.drawRoundRect(0, 0, width - 1, height - 1, 12, 12);
			if (height < 265)
			{
				drawCompactPlan(g, width, height);
				g.dispose();
				return;
			}
			int top = 36;
			drawTitle(g, "Safe Scheduling Boundary", 22, 24);
			drawSubtitle(g,
					"Policy changes assignment before node-local model state exists.",
					22,
					43);
			String[] labels = {
					"Simulator settings",
					"Scheduler plan",
					"Complete node jobs",
					"Existing organizer merge",
					"Final recommendations"
			};
			String[] details = {
					RecommendationModeSupport.algorithmName(algorithm),
					plan.getPolicy().getLabel(),
					"no partial maps",
					"dedupe + rank",
					"same output path"
			};
			Color[] colors = { BLUE, PURPLE, AMBER, GREEN, BLUE };
			int gap = 12;
			int boxWidth = Math.max(88,
					(width - 44 - gap * (labels.length - 1)) / labels.length);
			int y = top + 70;
			for (int index = 0; index < labels.length; index++)
			{
				int x = 22 + index * (boxWidth + gap);
				drawCard(g, x, y, boxWidth, 78, colors[index],
						labels[index], details[index]);
				if (index < labels.length - 1)
				{
					g.setColor(NebulaDesign.BORDER_STRONG);
					g.setStroke(new BasicStroke(2f));
					int ax = x + boxWidth;
					int ay = y + 39;
					g.drawLine(ax, ay, ax + gap - 2, ay);
					g.fillOval(ax + gap - 5, ay - 3, 6, 6);
				}
			}
			drawNodePreview(g, 28, y + 120, width - 56, height - y - 150);
			g.dispose();
		}

		private void drawCompactPlan(Graphics2D g, int width, int height)
		{
			drawTitle(g, "Safe Scheduling Boundary", 18, 27);
			if (height > 118)
			{
				drawSubtitle(g,
						"Capacity policy is applied before node-local model state exists.",
						18,
						46);
			}
			String[] labels = {
					"Settings",
					"Plan",
					"Node jobs",
					"Merge",
					"Results"
			};
			String[] details = {
					RecommendationModeSupport.algorithmName(algorithm),
					plan.getPolicy().getLabel(),
					"full maps",
					"dedupe",
					"same path"
			};
			Color[] colors = { BLUE, PURPLE, AMBER, GREEN, BLUE };
			int gap = 8;
			int cardY = height > 150 ? 78 : 58;
			int cardHeight = Math.max(42, Math.min(58, height - cardY - 16));
			int cardWidth = Math.max(64,
					(width - 36 - gap * (labels.length - 1)) / labels.length);
			for (int index = 0; index < labels.length; index++)
			{
				int x = 18 + index * (cardWidth + gap);
				if (x + cardWidth > width - 10)
				{
					break;
				}
				drawCard(g, x, cardY, cardWidth, cardHeight, colors[index],
						labels[index], details[index]);
				if (index < labels.length - 1
						&& x + cardWidth + gap < width - 8)
				{
					g.setColor(NebulaDesign.BORDER_STRONG);
					g.setStroke(new BasicStroke(1.6f));
					int ay = cardY + cardHeight / 2;
					g.drawLine(x + cardWidth, ay,
							x + cardWidth + gap - 2, ay);
					g.fillOval(x + cardWidth + gap - 5, ay - 3, 6, 6);
				}
			}
		}

		private void drawNodePreview(
				Graphics2D g,
				int x,
				int y,
				int width,
				int height)
		{
			if (result == null)
			{
				return;
			}
			height = Math.max(120, height);
			g.setColor(glass(210));
			g.fillRoundRect(x, y, width, height, 14, 14);
			g.setColor(BORDER);
			g.drawRoundRect(x, y, width, height, 14, 14);
			drawSubtitle(g, "Assignment preview from current node capacity", x + 16, y + 25);
			List<SchedulerLabEngine.NodeSummary> nodes = result.getNodes();
			int maxAssigned = 1;
			for (SchedulerLabEngine.NodeSummary summary : nodes)
			{
				maxAssigned = Math.max(maxAssigned, summary.getAssignedJobs());
			}
			int available = Math.max(1, height - 58);
			if (nodes.size() <= 5
					&& available / Math.max(1, nodes.size()) >= 22)
			{
				drawCompactNodeBars(g, x, y, width, height, nodes, maxAssigned);
			}
			else
			{
				drawNodeTileMap(g, x, y, width, height, nodes, maxAssigned);
			}
		}

		private void drawCompactNodeBars(
				Graphics2D g,
				int x,
				int y,
				int width,
				int height,
				List<SchedulerLabEngine.NodeSummary> nodes,
				int maxAssigned)
		{
			int rowY = y + 48;
			int available = Math.max(1, height - 58);
			int rowHeight = Math.max(22, Math.min(34,
					available / Math.max(1, nodes.size())));
			int labelRightWidth = 146;
			int barX = x + 112;
			int barRight = x + width - labelRightWidth;
			int barTrackWidth = Math.max(60, barRight - barX);
			for (SchedulerLabEngine.NodeSummary summary : nodes)
			{
				if (rowY + rowHeight > y + height - 4)
				{
					break;
				}
				int barWidth = (int)Math.round(barTrackWidth
						* summary.getAssignedJobs() / (double)maxAssigned);
				Color accent = summary.getProfile().getRole()
						== NodeCapacityProfile.Role.NORMAL
						? BLUE : roleColor(summary.getProfile());
				g.setColor(TEXT);
				g.setFont(font(Font.BOLD, 12));
				g.drawString("Node " + summary.getNodeIndex(),
						x + 16,
						rowY + 14);
				g.setColor(track());
				g.fillRoundRect(barX, rowY + 4, barTrackWidth, 11, 10, 10);
				g.setColor(accent);
				g.fillRoundRect(barX, rowY + 4, Math.max(4, barWidth),
						11, 10, 10);
				g.setColor(MUTED);
				g.setFont(font(Font.PLAIN, 11));
				String value = summary.getAssignedJobs() + " units | "
						+ summary.getProfile().shortLabel();
				g.drawString(shortText(value, 24), barRight + 12, rowY + 14);
				rowY += rowHeight;
			}
		}

		private void drawNodeTileMap(
				Graphics2D g,
				int x,
				int y,
				int width,
				int height,
				List<SchedulerLabEngine.NodeSummary> nodes,
				int maxAssigned)
		{
			int top = y + 48;
			int left = x + 16;
			int gap = 8;
			int columns = Math.max(2, Math.min(4, width / 170));
			int rows = (int)Math.ceil(nodes.size() / (double)columns);
			int tileWidth = Math.max(110,
					(width - 32 - (columns - 1) * gap) / columns);
			int tileHeight = Math.max(46,
					Math.min(64, (height - 58 - (rows - 1) * gap)
							/ Math.max(1, rows)));
			for (int i = 0; i < nodes.size(); i++)
			{
				SchedulerLabEngine.NodeSummary summary = nodes.get(i);
				int column = i % columns;
				int row = i / columns;
				int tx = left + column * (tileWidth + gap);
				int ty = top + row * (tileHeight + gap);
				if (ty + tileHeight > y + height - 6)
				{
					break;
				}
				Color accent = summary.getProfile().getRole()
						== NodeCapacityProfile.Role.NORMAL
						? BLUE : roleColor(summary.getProfile());
				g.setColor(SURFACE_ALT);
				g.fillRoundRect(tx, ty, tileWidth, tileHeight, 10, 10);
				g.setColor(new Color(accent.getRed(), accent.getGreen(),
						accent.getBlue(), 125));
				g.drawRoundRect(tx, ty, tileWidth, tileHeight, 10, 10);
				g.setColor(TEXT);
				g.setFont(font(Font.BOLD, 12));
				g.drawString("Node " + summary.getNodeIndex(), tx + 10, ty + 18);
				g.setColor(MUTED);
				g.setFont(font(Font.PLAIN, 10));
				g.drawString(shortText(summary.getProfile().shortLabel(), 18),
						tx + 10,
						ty + 34);
				int trackWidth = tileWidth - 20;
				int barWidth = (int)Math.round(trackWidth
						* summary.getAssignedJobs() / (double)maxAssigned);
				g.setColor(track());
				g.fillRoundRect(tx + 10, ty + tileHeight - 13,
						trackWidth, 7, 7, 7);
				g.setColor(accent);
				g.fillRoundRect(tx + 10, ty + tileHeight - 13,
						Math.max(4, barWidth), 7, 7, 7);
				g.setColor(MUTED);
				g.setFont(font(Font.BOLD, 10));
				g.drawString(summary.getAssignedJobs() + " units",
						tx + tileWidth - 58,
						ty + 18);
			}
		}

		private void drawCard(
				Graphics2D g,
				int x,
				int y,
				int width,
				int height,
				Color accent,
				String title,
				String detail)
		{
			g.setColor(SURFACE);
			g.fillRoundRect(x, y, width, height, 12, 12);
			g.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 140));
			g.setStroke(new BasicStroke(1.5f));
			g.drawRoundRect(x, y, width, height, 12, 12);
			g.setColor(accent);
			g.fillRoundRect(x, y, 8, height, 12, 12);
			int available = width - 26;
			g.setColor(TEXT);
			g.setFont(font(Font.BOLD, 13));
			g.drawString(fitText(g, title, available),
					x + 18,
					y + Math.min(30, Math.max(20, height / 2 - 1)));
			g.setColor(MUTED);
			g.setFont(font(Font.PLAIN, 11));
			if (height >= 52)
			{
				g.drawString(fitText(g, detail, available),
						x + 18,
						y + 51);
			}
		}
	}

	private final class WorkQueueCanvas extends JPanel implements Scrollable
	{
		private NodeExecutionMonitor.Snapshot snapshot =
				NodeExecutionMonitor.snapshot();
		private RecommendationSchedulerPlan plan =
				RecommendationSchedulerPlan.defaultPlan();
		private int algorithm = ControllerAgent.K_MEANS;
		private int targetCount = 1;
		private long now = System.currentTimeMillis();

		WorkQueueCanvas()
		{
			setOpaque(true);
			setBackground(SURFACE);
			setPreferredSize(new Dimension(520, 420));
		}

		void setContext(
				NodeExecutionMonitor.Snapshot snapshot,
				RecommendationSchedulerPlan plan,
				int algorithm,
				int targetCount,
				long now)
		{
			this.snapshot = snapshot == null
					? NodeExecutionMonitor.snapshot() : snapshot;
			this.plan = plan == null
					? RecommendationSchedulerPlan.defaultPlan() : plan;
			this.algorithm = algorithm;
			this.targetCount = Math.max(1, targetCount);
			this.now = Math.max(0L, now);
			updatePreferredQueueSize();
			repaint();
		}

		private void updatePreferredQueueSize()
		{
			int nodeCount = Math.max(1, effectiveNodeCount());
			int rows = (int)Math.ceil(nodeCount / 4.0);
			int preferredHeight = Math.max(420, 318 + rows * 74);
			Dimension preferred = getPreferredSize();
			if (preferred.height != preferredHeight)
			{
				setPreferredSize(new Dimension(preferred.width, preferredHeight));
				revalidate();
			}
		}

		protected void paintComponent(Graphics graphics)
		{
			super.paintComponent(graphics);
			Graphics2D g = (Graphics2D)graphics.create();
			NebulaDesign.prepare(g);
			int width = getWidth();
			int height = getHeight();
			g.setColor(SURFACE);
			g.fillRoundRect(0, 0, width - 1, height - 1, 14, 14);
			g.setColor(BORDER);
			g.drawRoundRect(0, 0, width - 1, height - 1, 14, 14);

			int nodeCount = effectiveNodeCount();
			int workItemCount = liveQueueWorkItemCount(nodeCount);
			boolean activeLiveQueue = isLiveCandidateQueueActive(
					nodeCount,
					workItemCount);
			RecommendationWorkQueueRegistry.QueueSnapshot queue =
					snapshotLiveCandidateQueue(nodeCount, workItemCount);
			drawQueueHeader(g, width, queue, activeLiveQueue);
			if (!activeLiveQueue)
			{
				drawInactiveQueue(g, width, height, nodeCount, workItemCount);
				g.dispose();
				return;
			}
			drawQueueFlow(g, width, height, nodeCount, queue);
			g.dispose();
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
			return 18;
		}

		public int getScrollableBlockIncrement(
				Rectangle visibleRect,
				int orientation,
				int direction)
		{
			return Math.max(80, visibleRect.height - 40);
		}

		public boolean getScrollableTracksViewportWidth()
		{
			return true;
		}

		public boolean getScrollableTracksViewportHeight()
		{
			return false;
		}

		private void drawCompactQueue(
				Graphics2D g,
				int width,
				int height,
				int nodeCount,
				int shardCount,
				RecommendationWorkQueueRegistry.QueueSnapshot queue,
				boolean activeLiveQueue)
		{
			drawTitle(g, "Dynamic Live Work Queue", 18, 27);
			if (height > 112)
			{
				drawSubtitle(g,
						activeLiveQueue
								? "Live queue: nodes pull the next available safe-boundary work item."
								: "Dynamic live queue is idle for the current simulator settings.",
						18,
						46);
			}
			int metricY = height > 132 ? 62 : 50;
			int metricWidth = Math.max(72, (width - 46) / 4);
			drawQueueMetric(g, 18, metricY, metricWidth - 8,
					"items", String.valueOf(queue.getTotalShards()), BLUE);
			drawQueueMetric(g, 18 + metricWidth, metricY, metricWidth - 8,
					"waiting", String.valueOf(queue.getWaitingCount()), PURPLE);
			drawQueueMetric(g, 18 + metricWidth * 2, metricY, metricWidth - 8,
					"active", String.valueOf(queue.getActiveCount()), AMBER);
			drawQueueMetric(g, 18 + metricWidth * 3, metricY, metricWidth - 8,
					"done", String.valueOf(queue.getCompletedCount()), GREEN);
			if (height < 160)
			{
				return;
			}
			int flowY = metricY + 62;
			if (activeLiveQueue)
			{
				drawMiniQueueFlow(g, 22, flowY, width - 44,
						Math.max(44, height - flowY - 12), queue);
			}
			else
			{
				drawSoftCard(g, 22, flowY, width - 44,
						Math.max(44, height - flowY - 12), BLUE,
						"Preview",
						shardCount + " work items across " + nodeCount
								+ " node" + (nodeCount == 1 ? "" : "s")
								+ "; no live claims yet.");
			}
		}

		private void drawMiniQueueFlow(
				Graphics2D g,
				int x,
				int y,
				int width,
				int height,
				RecommendationWorkQueueRegistry.QueueSnapshot queue)
		{
			drawSoftPanel(g, x, y, width, height, "Live pull pattern", GREEN);
			int centerY = y + height / 2 + 7;
			int queueW = Math.max(86, width / 4);
			int nodeW = Math.max(120, width / 3);
			int mergeW = Math.max(86, width / 5);
			drawSoftCard(g, x + 12, centerY - 25, queueW, 50, PURPLE,
					queue.getWaitingCount() + " waiting", "shared queue");
			drawSoftCard(g, x + width / 2 - nodeW / 2, centerY - 25,
					nodeW, 50, BLUE,
					queue.getActiveCount() + " active",
					"nodes pulling work");
			drawSoftCard(g, x + width - mergeW - 12, centerY - 25,
					mergeW, 50, GREEN,
					queue.getCompletedCount() + "/"
							+ queue.getTotalShards(),
					"completed");
			drawArrow(g, x + 12 + queueW, centerY,
					x + width / 2 - nodeW / 2 - 8, centerY, BLUE);
			drawArrow(g, x + width / 2 + nodeW / 2 + 8, centerY,
					x + width - mergeW - 20, centerY, GREEN);
		}

		private int effectiveNodeCount()
		{
			if (snapshot != null && !snapshot.getNodes().isEmpty())
			{
				return Math.max(1, snapshot.getNodes().size());
			}
			return Math.max(1, ownerNodeCount());
		}

		private String effectiveRunId()
		{
			if (snapshot != null && snapshot.getRunId() != null
					&& snapshot.getRunId().trim().length() > 0)
			{
				return snapshot.getRunId();
			}
			return RecommendationSchedulerRegistry.activeRunId();
		}

		private boolean isLiveCandidateQueueActive(
				int nodeCount,
				int workItemCount)
		{
			return workItemCount > 0;
		}

		private RecommendationWorkQueueRegistry.QueueSnapshot snapshotLiveCandidateQueue(
				int nodeCount,
				int workItemCount)
		{
			return RecommendationWorkQueueRegistry.latestPhaseSnapshot(effectiveRunId());
		}

		private int liveQueueWorkItemCount(int nodeCount)
		{
			return RecommendationWorkQueueRegistry.latestPhaseSnapshot(effectiveRunId())
					.getTotalShards();
		}

		private void drawQueueHeader(
				Graphics2D g,
				int width,
				RecommendationWorkQueueRegistry.QueueSnapshot queue,
				boolean activeLiveQueue)
		{
			drawTitle(g, "Dynamic Live Work Queue", 18, 27);
			drawSubtitle(g,
					activeLiveQueue
							? "The run's current work phase: every unit is committed exactly once; revoked and failed units run again elsewhere."
							: "No work phase is open yet: the queue appears when a run reaches its input or model stage.",
					18,
					46);
			int metricY = 60;
			int metricWidth = Math.max(78, (width - 46) / 4);
			drawQueueMetric(g, 18, metricY, metricWidth - 8,
					"items", String.valueOf(queue.getTotalShards()), BLUE);
			drawQueueMetric(g, 18 + metricWidth, metricY, metricWidth - 8,
					"waiting", String.valueOf(queue.getWaitingCount()), PURPLE);
			drawQueueMetric(g, 18 + metricWidth * 2, metricY, metricWidth - 8,
					"active", String.valueOf(queue.getActiveCount()), AMBER);
			drawQueueMetric(g, 18 + metricWidth * 3, metricY, metricWidth - 8,
					"complete", String.valueOf(queue.getCompletedCount()), GREEN);
		}

		private void drawInactiveQueue(
				Graphics2D g,
				int width,
				int height,
				int nodeCount,
				int workItemCount)
		{
			int y = 126;
			drawSoftCard(g, 22, y, width - 44, 82, BLUE,
					"Current Simulator path",
					RecommendationModeSupport.algorithmName(algorithm) + ": "
							+ modelStageDescription(algorithm) + ".");
			drawSoftCard(g, 22, y + 96, width - 44, 82, PURPLE,
					"Queue preview",
					workItemCount + " work item"
							+ (workItemCount == 1 ? "" : "s")
							+ " across " + nodeCount + " node"
							+ (nodeCount == 1 ? "" : "s") + ".");
			drawSoftCard(g, 22, y + 192, width - 44, 82, GREEN,
					"When it appears",
					"The queue opens when a run reaches input delivery or a scheduled model stage.");
			drawSubtitle(g,
					"Scheduling never changes recommendations: every unit is committed exactly once.",
					24,
					Math.min(height - 24, y + 296));
		}

		private void drawQueueFlow(
				Graphics2D g,
				int width,
				int height,
				int nodeCount,
				RecommendationWorkQueueRegistry.QueueSnapshot queue)
		{
			int top = 126;
			int queueX = 22;
			int queueW = Math.max(112, width / 4 - 12);
			int nodeX = queueX + queueW + 24;
			int mergeW = 92;
			int mergeX = width - mergeW - 22;
			int nodeW = Math.max(104, mergeX - nodeX - 28);
			int flowHeight = Math.max(168, height - top - 112);

			drawQueueBox(g, queueX, top, queueW, flowHeight, queue);
			drawNodeClaimBoard(g, nodeX, top, nodeW, flowHeight, nodeCount, queue);
			drawMergeBox(g, mergeX, top + flowHeight / 2 - 42, mergeW, 84, queue);
			drawArrow(g, queueX + queueW, top + flowHeight / 2,
					nodeX - 8, top + flowHeight / 2, BLUE);
			drawArrow(g, nodeX + nodeW + 8, top + flowHeight / 2,
					mergeX - 8, top + flowHeight / 2, GREEN);
			drawEventStrip(g, 22, top + flowHeight + 12,
					width - 44,
					Math.max(82, height - (top + flowHeight + 28)),
					queue);
		}

		private void drawQueueBox(
				Graphics2D g,
				int x,
				int y,
				int width,
				int height,
				RecommendationWorkQueueRegistry.QueueSnapshot queue)
		{
			drawSoftPanel(g, x, y, width, height, "Shared queue", PURPLE);
			g.setFont(font(Font.BOLD, 12));
			g.setColor(TEXT);
			g.drawString(queue.getWaitingCount() + " waiting shard"
					+ (queue.getWaitingCount() == 1 ? "" : "s"), x + 14, y + 44);
			LiveWorkloadAdjustmentRegistry.Snapshot live =
					LiveWorkloadAdjustmentRegistry.snapshot(effectiveRunId());
			int tokenY = y + 62;
			if (live.hasActiveControls())
			{
				g.setColor(MUTED);
				g.setFont(font(Font.BOLD, 10));
				g.drawString(shortText(live.compactSummary(), Math.max(20, width / 7)),
						x + 14,
						y + 60);
				tokenY = y + 76;
			}
			drawShardTokens(g, queue.getWaitingPreview(), x + 14, tokenY,
					width - 28, Math.max(1, height - (tokenY - y) - 16),
					PURPLE, "wait");
		}

		private void drawNodeClaimBoard(
				Graphics2D g,
				int x,
				int y,
				int width,
				int height,
				int nodeCount,
				RecommendationWorkQueueRegistry.QueueSnapshot queue)
		{
			drawSoftPanel(g, x, y, width, height, "Nodes pulling work", BLUE);
			int gap = 8;
			int innerW = Math.max(1, width - 28);
			int innerH = Math.max(1, height - 44);
			int columns = chooseNodeBoardColumns(nodeCount, innerW, innerH, gap);
			int rows = (int)Math.ceil(nodeCount / (double)columns);
			int cardW = Math.max(88,
					(innerW - (columns - 1) * gap) / columns);
			int cardH = Math.max(44, Math.min(74,
					(innerH - (rows - 1) * gap) / Math.max(1, rows)));
			for (int node = 1; node <= nodeCount; node++)
			{
				int index = node - 1;
				int column = index % columns;
				int row = index / columns;
				int cardX = x + 14 + column * (cardW + gap);
				int cardY = y + 34 + row * (cardH + gap);
				drawNodeShardCard(g, cardX, cardY, cardW, cardH, node, queue);
			}
		}

		private int chooseNodeBoardColumns(
				int nodeCount,
				int innerW,
				int innerH,
				int gap)
		{
			int minCardW = 124;
			int minCardH = 50;
			int maxColumns = Math.max(1,
					Math.min(nodeCount, innerW / minCardW));
			if (maxColumns <= 1 && nodeCount > 1 && innerW >= 180)
			{
				maxColumns = 2;
			}
			int bestColumns = Math.max(1, maxColumns);
			int bestPenalty = Integer.MAX_VALUE;
			for (int candidate = Math.max(1, maxColumns); candidate >= 1;
					candidate--)
			{
				int rows = (int)Math.ceil(nodeCount / (double)candidate);
				int cardW = (innerW - (candidate - 1) * gap) / candidate;
				int cardH = (innerH - (rows - 1) * gap) / Math.max(1, rows);
				int penalty = Math.max(0, minCardW - cardW)
						+ Math.max(0, minCardH - cardH) * 3
						+ rows * 2 - candidate;
				if (cardW >= minCardW && cardH >= minCardH)
				{
					return candidate;
				}
				if (penalty < bestPenalty)
				{
					bestPenalty = penalty;
					bestColumns = candidate;
				}
			}
			return Math.max(1, bestColumns);
		}

		private void drawNodeShardCard(
				Graphics2D g,
				int x,
				int y,
				int width,
				int height,
				int nodeIndex,
				RecommendationWorkQueueRegistry.QueueSnapshot queue)
		{
			NodeCapacityProfile profile =
					NodeCapacityRegistry.profileForNode(nodeIndex);
			Color accent = roleColor(profile);
			LiveWorkloadAdjustmentRegistry.Snapshot live =
					LiveWorkloadAdjustmentRegistry.snapshot(effectiveRunId());
			LiveWorkloadAdjustmentRegistry.NodeControl control =
					live.controlForNode(nodeIndex);
			boolean blocked = control.isUnavailable() || control.isPaused();
			if (control.isUnavailable())
			{
				accent = RED;
			}
			else if (control.isPaused())
			{
				accent = AMBER;
			}
			Integer claimed = queue.getClaimedByNode().get(Integer.valueOf(nodeIndex));
			Integer completed = queue.getCompletedByNode().get(Integer.valueOf(nodeIndex));
			List<Integer> active = queue.getActiveByNode().get(Integer.valueOf(nodeIndex));
			int claimCount = claimed == null ? 0 : claimed.intValue();
			int completeCount = completed == null ? 0 : completed.intValue();
			String activeText = active == null || active.isEmpty()
					? (blocked ? control.statusLabel() : control.statusLabel())
					: "shard " + active.get(0);
			g.setColor(blocked ? tint(accent, 0.92f) : SURFACE);
			g.fillRoundRect(x, y, width, height, 10, 10);
			g.setColor(new Color(accent.getRed(), accent.getGreen(),
					accent.getBlue(), 145));
			g.drawRoundRect(x, y, width, height, 10, 10);
			g.setColor(accent);
			g.fillRoundRect(x, y, 6, height, 10, 10);
			g.setColor(TEXT);
			g.setFont(font(Font.BOLD, 11));
			g.drawString("Node " + nodeIndex, x + 13, y + 18);
			g.setColor(MUTED);
			g.setFont(font(Font.PLAIN, 9));
			if (height >= 42)
			{
				String profileText = profile.shortLabel();
				if (Math.abs(control.getCapacityScale() - 1.0) > 0.0000001)
				{
					profileText += " | "
							+ Math.round(control.getCapacityScale() * 100.0)
							+ "%";
				}
				g.drawString(shortText(profileText, 22), x + 13, y + 32);
			}
			if (height >= 56)
			{
				g.setColor(deep(accent));
				g.setFont(font(Font.BOLD, 10));
				g.drawString(shortText(activeText, 18), x + 13, y + 47);
			}
			int trackWidth = width - 24;
			int fill = queue.getTotalShards() <= 0 ? 0
					: (int)Math.round(trackWidth
							* completeCount / (double)queue.getTotalShards());
			g.setColor(track());
			g.fillRoundRect(x + 13, y + height - 13, trackWidth, 7, 7, 7);
			g.setColor(accent);
			g.fillRoundRect(x + 13, y + height - 13,
					Math.max(completeCount > 0 ? 4 : 0, fill), 7, 7, 7);
			g.setColor(MUTED);
			g.setFont(font(Font.BOLD, 9));
			String claimLabel = width >= 150
					? claimCount + " claimed / " + completeCount + " done"
					: claimCount + "/" + completeCount;
			int claimX = x + width - 10
					- g.getFontMetrics().stringWidth(claimLabel);
			if (claimX > x + 58)
			{
				g.drawString(claimLabel, claimX, y + 18);
			}
		}

		private void drawMergeBox(
				Graphics2D g,
				int x,
				int y,
				int width,
				int height,
				RecommendationWorkQueueRegistry.QueueSnapshot queue)
		{
			g.setColor(tint(GREEN, 0.84f));
			g.fillRoundRect(x, y, width, height, 42, 42);
			g.setColor(GREEN);
			g.setStroke(new BasicStroke(2f));
			g.drawRoundRect(x, y, width, height, 42, 42);
			g.setColor(TEXT);
			g.setFont(font(Font.BOLD, 13));
			g.drawString("MERGE", x + 24, y + 28);
			g.setColor(deep(GREEN));
			g.setFont(font(Font.BOLD, 11));
			g.drawString(queue.getCompletedCount() + "/"
					+ queue.getTotalShards(), x + 26, y + 49);
			g.setColor(MUTED);
			g.setFont(font(Font.PLAIN, 10));
			g.drawString("shards", x + 27, y + 64);
		}

		private void drawEventStrip(
				Graphics2D g,
				int x,
				int y,
				int width,
				int height,
				RecommendationWorkQueueRegistry.QueueSnapshot queue)
		{
			drawSoftPanel(g, x, y, width, height, "Live queue events", GREEN);
			List<RecommendationWorkQueueRegistry.QueueEvent> events =
					queue.getEvents();
			LiveWorkloadAdjustmentRegistry.Snapshot live =
					LiveWorkloadAdjustmentRegistry.snapshot(effectiveRunId());
			if (events.isEmpty() && live.getEvents().isEmpty())
			{
				drawSubtitle(g,
						"Waiting for the first Doc2Vec node to claim a candidate shard.",
						x + 14,
						y + 44);
				return;
			}
			long start = snapshot == null ? 0L : actualStartMillis(snapshot);
			int rowY = y + 42;
			int maxRows = Math.max(1, (height - 48) / 17);
			int drawn = 0;
			for (LiveWorkloadAdjustmentRegistry.ControlEvent event
					: live.getEvents())
			{
				if (drawn >= Math.min(3, maxRows))
				{
					break;
				}
				Color accent = "pause".equals(event.getAction())
						|| "unavailable".equals(event.getAction())
						|| "redirect blocked".equals(event.getAction())
						? AMBER : GREEN;
				g.setColor(accent);
				g.fillOval(x + 14, rowY - 8, 8, 8);
				g.setColor(TEXT);
				g.setFont(font(Font.BOLD, 10));
				g.drawString(controlEventTime(event, start), x + 28, rowY);
				g.setColor(MUTED);
				g.setFont(font(Font.PLAIN, 10));
				g.drawString(shortText(
						"live " + event.getAction() + ": "
								+ event.getDetail(),
						Math.max(36, width / 8)),
						x + 86,
						rowY);
				rowY += 17;
				drawn++;
			}
			for (int i = 0; i < events.size() && drawn < maxRows; i++, drawn++)
			{
				RecommendationWorkQueueRegistry.QueueEvent event = events.get(i);
				Color accent = "completed".equals(event.getAction())
						? GREEN : ("failed".equals(event.getAction()) ? RED : BLUE);
				g.setColor(accent);
				g.fillOval(x + 14, rowY - 8, 8, 8);
				g.setColor(TEXT);
				g.setFont(font(Font.BOLD, 10));
				g.drawString(eventTime(event, start), x + 28, rowY);
				g.setColor(MUTED);
				g.setFont(font(Font.PLAIN, 10));
				g.drawString(shortText(event.getDetail(), Math.max(36, width / 8)),
						x + 86,
						rowY);
				rowY += 17;
			}
		}

		private String controlEventTime(
				LiveWorkloadAdjustmentRegistry.ControlEvent event,
				long start)
		{
			if (start <= 0L)
			{
				return "live";
			}
			return "t+" + formatDuration(Math.max(0L, event.getTime() - start));
		}

		private String eventTime(
				RecommendationWorkQueueRegistry.QueueEvent event,
				long start)
		{
			if (start <= 0L)
			{
				return "live";
			}
			return "t+" + formatDuration(Math.max(0L, event.getTime() - start));
		}

		private void drawShardTokens(
				Graphics2D g,
				List<Integer> shards,
				int x,
				int y,
				int width,
				int height,
				Color accent,
				String label)
		{
			int token = 25;
			int gap = 5;
			int columns = Math.max(1, width / (token + gap));
			int maxRows = Math.max(1, height / (token + gap));
			int maxTokens = columns * maxRows;
			for (int i = 0; i < shards.size() && i < maxTokens; i++)
			{
				int column = i % columns;
				int row = i / columns;
				int tx = x + column * (token + gap);
				int ty = y + row * (token + gap);
				g.setColor(new Color(accent.getRed(), accent.getGreen(),
						accent.getBlue(), 35));
				g.fillRoundRect(tx, ty, token, token, 8, 8);
				g.setColor(new Color(accent.getRed(), accent.getGreen(),
						accent.getBlue(), 150));
				g.drawRoundRect(tx, ty, token, token, 8, 8);
				g.setColor(deep(accent));
				g.setFont(font(Font.BOLD, 9));
				g.drawString(String.valueOf(shards.get(i).intValue()),
						tx + 6,
						ty + 16);
			}
			if (shards.size() > maxTokens)
			{
				g.setColor(MUTED);
				g.setFont(font(Font.BOLD, 10));
				g.drawString("+" + (shards.size() - maxTokens) + " more "
						+ label, x, y + maxRows * (token + gap) + 13);
			}
		}

		private void drawQueueMetric(
				Graphics2D g,
				int x,
				int y,
				int width,
				String label,
				String value,
				Color accent)
		{
			g.setColor(SURFACE_ALT);
			g.fillRoundRect(x, y, width, 46, 10, 10);
			g.setColor(new Color(accent.getRed(), accent.getGreen(),
					accent.getBlue(), 95));
			g.drawRoundRect(x, y, width, 46, 10, 10);
			g.setColor(MUTED);
			g.setFont(font(Font.BOLD, 9));
			g.drawString(label.toUpperCase(Locale.US), x + 10, y + 16);
			g.setColor(TEXT);
			g.setFont(font(Font.BOLD, 18));
			g.drawString(value, x + 10, y + 36);
		}

		private void drawSoftCard(
				Graphics2D g,
				int x,
				int y,
				int width,
				int height,
				Color accent,
				String title,
				String detail)
		{
			g.setColor(new Color(accent.getRed(), accent.getGreen(),
					accent.getBlue(), 20));
			g.fillRoundRect(x, y, width, height, 12, 12);
			g.setColor(new Color(accent.getRed(), accent.getGreen(),
					accent.getBlue(), 110));
			g.drawRoundRect(x, y, width, height, 12, 12);
			g.setColor(accent);
			g.fillRoundRect(x, y, 7, height, 12, 12);
			g.setColor(TEXT);
			g.setFont(font(Font.BOLD, 13));
			g.drawString(title, x + 18, y + 28);
			g.setColor(MUTED);
			g.setFont(font(Font.PLAIN, 11));
			g.drawString(shortText(detail, Math.max(34, width / 7)),
					x + 18,
					y + 51);
		}

		private void drawSoftPanel(
				Graphics2D g,
				int x,
				int y,
				int width,
				int height,
				String title,
				Color accent)
		{
			g.setColor(SURFACE_ALT);
			g.fillRoundRect(x, y, width, height, 14, 14);
			g.setColor(new Color(accent.getRed(), accent.getGreen(),
					accent.getBlue(), 85));
			g.drawRoundRect(x, y, width, height, 14, 14);
			g.setColor(MUTED);
			g.setFont(font(Font.BOLD, 10));
			g.drawString(title.toUpperCase(Locale.US), x + 14, y + 22);
		}

		private void drawArrow(
				Graphics2D g,
				int x1,
				int y1,
				int x2,
				int y2,
				Color accent)
		{
			g.setColor(new Color(accent.getRed(), accent.getGreen(),
					accent.getBlue(), 110));
			g.setStroke(new BasicStroke(2f));
			g.drawLine(x1, y1, x2, y2);
			g.fillOval(x2 - 4, y2 - 4, 8, 8);
		}
	}

	private static void drawTitle(Graphics2D g, String text, int x, int y)
	{
		g.setColor(TEXT);
		g.setFont(font(Font.BOLD, 15));
		g.drawString(text, x, y);
	}

	private static void drawSubtitle(Graphics2D g, String text, int x, int y)
	{
		g.setColor(MUTED);
		g.setFont(font(Font.PLAIN, 12));
		g.drawString(text, x, y);
	}

	private static Color roleColor(NodeCapacityProfile profile)
	{
		if (profile == null)
		{
			return BLUE;
		}
		switch (profile.getRole())
		{
			case STRONG:
				return GREEN;
			case WEAK:
				return AMBER;
			case MEMORY_HEAVY:
				return BLUE;
			case UNRELIABLE:
				return RED;
			default:
				return PURPLE;
		}
	}

	/** Workload types map onto the fixed Nebula categorical chart palette. */
	private static Color workloadColor(SchedulerLabEngine.WorkloadUnitType type)
	{
		if (type == null)
		{
			return NebulaDesign.chartColor(0);
		}
		switch (type)
		{
			case PREPROCESSING_BATCH:
				return NebulaDesign.chartColor(0);
			case TARGET_RECOMMENDATION_JOB:
				return NebulaDesign.chartColor(5);
			case CANDIDATE_SHARD:
				return NebulaDesign.chartColor(4);
			case MODEL_TRAINING_JOB:
				return NebulaDesign.chartColor(2);
			case SCORING_JOB:
				return NebulaDesign.chartColor(1);
			case GRAPH_REPORTING_JOB:
				return NebulaDesign.chartColor(3);
			default:
				return NebulaDesign.chartColor(0);
		}
	}

	/**
	 * Execution states keep dedicated hues so timeline segments and the
	 * legend stay readable in both modes: idle states are quiet grays,
	 * working states use the semantic/chart hues.
	 */
	private static Color stateColor(NodeExecutionMonitor.State state)
	{
		if (state == null)
		{
			return BLUE;
		}
		switch (state)
		{
			case PROVISIONING:
				return NebulaDesign.BORDER_STRONG;
			case READY:
				return NebulaDesign.TEXT_TERTIARY;
			case RECEIVING:
				return BLUE;
			case WAITING_FOR_INPUT:
			case WAITING_FOR_PEERS:
			case WAITING_FOR_SHARED_MODEL:
				return StudioNightTheme.blend(BLUE, NebulaDesign.TEXT_TERTIARY, 0.55f);
			case PREPROCESSING:
				return PURPLE;
			case FEATURES:
				return NebulaDesign.PINK;
			case WAITING_FOR_MEMORY:
				return AMBER;
			case MODEL_RUNNING:
				return GREEN;
			case PUBLISHING:
				return NebulaDesign.INFO;
			case COMPLETE:
				return StudioNightTheme.blend(GREEN, NebulaDesign.TEXT_TERTIARY, 0.35f);
			case FAILED:
				return RED;
			case STOPPED:
				return NebulaDesign.TEXT_TERTIARY;
			default:
				return BLUE;
		}
	}

	private static boolean isWorkingState(NodeExecutionMonitor.State state)
	{
		if (state == null)
		{
			return false;
		}
		switch (state)
		{
			case RECEIVING:
			case PREPROCESSING:
			case FEATURES:
			case MODEL_RUNNING:
			case PUBLISHING:
				return true;
			default:
				return false;
		}
	}

	private static long actualStartMillis(NodeExecutionMonitor.Snapshot snapshot)
	{
		if (snapshot == null)
		{
			return 0L;
		}
		if (snapshot.getRunStartedAt() > 0L)
		{
			return snapshot.getRunStartedAt();
		}
		if (snapshot.getExperimentStartedAt() > 0L)
		{
			return snapshot.getExperimentStartedAt();
		}
		long earliest = Long.MAX_VALUE;
		for (NodeExecutionMonitor.NodeSnapshot node : snapshot.getNodes())
		{
			if (node.getRunStartedAt() > 0L)
			{
				earliest = Math.min(earliest, node.getRunStartedAt());
			}
			else if (node.getUpdatedAt() > 0L)
			{
				earliest = Math.min(earliest, node.getUpdatedAt());
			}
		}
		return earliest == Long.MAX_VALUE ? 0L : earliest;
	}

	private static long actualEndMillis(
			NodeExecutionMonitor.Snapshot snapshot,
			long now)
	{
		long start = actualStartMillis(snapshot);
		long end = start;
		if (snapshot == null || snapshot.getNodes().isEmpty())
		{
			return Math.max(start, now);
		}
		for (NodeExecutionMonitor.NodeSnapshot node : snapshot.getNodes())
		{
			end = Math.max(end, nodeEndMillis(node, now));
		}
		return Math.max(start, end);
	}

	private static long nodeRunStartMillis(
			NodeExecutionMonitor.NodeSnapshot node,
			long fallback)
	{
		if (node != null && node.getRunStartedAt() > 0L)
		{
			return node.getRunStartedAt();
		}
		return Math.max(0L, fallback);
	}

	private static long nodeEndMillis(
			NodeExecutionMonitor.NodeSnapshot node,
			long now)
	{
		if (node == null)
		{
			return Math.max(0L, now);
		}
		NodeExecutionMonitor.State state = node.getState();
		if (state == NodeExecutionMonitor.State.COMPLETE
				|| state == NodeExecutionMonitor.State.FAILED
				|| state == NodeExecutionMonitor.State.STOPPED)
		{
			if (node.getCompletedAt() > 0L)
			{
				return node.getCompletedAt();
			}
			if (node.getUpdatedAt() > 0L)
			{
				return node.getUpdatedAt();
			}
		}
		return Math.max(now, nodeRunStartMillis(node, now));
	}

	private static NodeExecutionMonitor.State stateAtStart(
			NodeExecutionMonitor.NodeSnapshot node,
			long start)
	{
		NodeExecutionMonitor.State state = node == null
				? NodeExecutionMonitor.State.READY : node.getState();
		if (node == null)
		{
			return state;
		}
		for (NodeExecutionMonitor.NodeEvent event : node.getEvents())
		{
			if (event.getTime() <= start)
			{
				state = event.getState();
			}
			else
			{
				break;
			}
		}
		return state;
	}

	private static double computeActualUtilization(
			NodeExecutionMonitor.Snapshot snapshot,
			long now)
	{
		if (snapshot == null || snapshot.getNodes().isEmpty())
		{
			return 0.0;
		}
		long start = actualStartMillis(snapshot);
		long end = actualEndMillis(snapshot, now);
		long span = Math.max(1L, end - start);
		long busy = 0L;
		for (NodeExecutionMonitor.NodeSnapshot node : snapshot.getNodes())
		{
			long nodeEnd = Math.min(end, nodeEndMillis(node, now));
			if (nodeEnd <= start)
			{
				continue;
			}
			NodeExecutionMonitor.State state = stateAtStart(node, start);
			long cursor = Math.max(start, nodeRunStartMillis(node, start));
			for (NodeExecutionMonitor.NodeEvent event : node.getEvents())
			{
				if (event.getTime() < start)
				{
					state = event.getState();
					continue;
				}
				if (event.getTime() > nodeEnd)
				{
					break;
				}
				if (event.getTime() > cursor && isWorkingState(state))
				{
					busy += event.getTime() - cursor;
				}
				cursor = event.getTime();
				state = event.getState();
			}
			if (nodeEnd > cursor && isWorkingState(state))
			{
				busy += nodeEnd - cursor;
			}
		}
		return busy / (double)(span * Math.max(1, snapshot.getNodes().size()));
	}

	private static String lastNodeLabel(
			NodeExecutionMonitor.Snapshot snapshot,
			long now)
	{
		if (snapshot == null || snapshot.getNodes().isEmpty())
		{
			return "--";
		}
		if (!isActualRunTerminal(snapshot))
		{
			return "live";
		}
		long last = 0L;
		int nodeIndex = 0;
		for (NodeExecutionMonitor.NodeSnapshot node : snapshot.getNodes())
		{
			long end = nodeEndMillis(node, now);
			if (end > last)
			{
				last = end;
				nodeIndex = node.getNodeIndex();
			}
		}
		return nodeIndex <= 0 ? "--" : "Node " + nodeIndex;
	}

	private static String cleanText(String value, String fallback)
	{
		return value == null || value.trim().length() == 0
				? fallback : value.trim();
	}

	/**
	 * Fits text into the available pixel width using the current font.
	 * Prefers a clean break at a space or hyphen over a mid-word ellipsis.
	 */
	private static String fitText(Graphics2D g, String value, int available)
	{
		String text = cleanText(value, "");
		FontMetrics metrics = g.getFontMetrics();
		if (metrics.stringWidth(text) <= available)
		{
			return text;
		}
		int breakAt = -1;
		for (int index = 0; index < text.length(); index++)
		{
			char letter = text.charAt(index);
			if (letter == ' ' || letter == '-' || letter == '/')
			{
				String head = text.substring(0, index);
				if (metrics.stringWidth(head) <= available)
				{
					breakAt = index;
				}
				else
				{
					break;
				}
			}
		}
		if (breakAt > 2)
		{
			return text.substring(0, breakAt);
		}
		String trimmed = text;
		while (trimmed.length() > 1
				&& metrics.stringWidth(trimmed + "…") > available)
		{
			trimmed = trimmed.substring(0, trimmed.length() - 1);
		}
		return trimmed + "…";
	}

	private static String shortText(String value, int maxLength)
	{
		String text = cleanText(value, "");
		if (text.length() <= maxLength)
		{
			return text;
		}
		if (maxLength <= 3)
		{
			return text.substring(0, Math.max(0, maxLength));
		}
		return text.substring(0, maxLength - 3) + "...";
	}

	private static String percentage(double value)
	{
		return DECIMAL.format(Math.max(0.0, Math.min(100.0, value * 100.0)))
				+ "%";
	}

	private static String formatDuration(long milliseconds)
	{
		long totalSeconds = Math.max(0L, milliseconds / 1000L);
		long hours = totalSeconds / 3600L;
		long minutes = (totalSeconds % 3600L) / 60L;
		long seconds = totalSeconds % 60L;
		if (hours > 0L)
		{
			return String.format(Locale.US,
					"%d:%02d:%02d",
					Long.valueOf(hours),
					Long.valueOf(minutes),
					Long.valueOf(seconds));
		}
		return String.format(Locale.US,
				"%02d:%02d",
				Long.valueOf(minutes),
				Long.valueOf(seconds));
	}

	private static String formatMillis(long milliseconds)
	{
		if (milliseconds < 0L)
		{
			return "--";
		}
		if (milliseconds < 1000L)
		{
			return milliseconds + " ms";
		}
		return String.format(Locale.US, "%.2f s", milliseconds / 1000.0);
	}

	private static final class ScheduleHistoryEntry
	{
		private final int sequence;
		private final String source;
		private final RecommendationSchedulerPlan plan;
		private final String policyLabel;
		private final String runPhase;
		private final String runId;
		private final int algorithm;
		private final int nodeCount;
		private final int completeNodes;
		private final int failedNodes;
		private final int stoppedNodes;
		private final long startedAt;
		private final long endedAt;
		private final long makespanMillis;
		private final double utilization;
		private final int slowestNode;
		private final long slowestNodeMillis;

		ScheduleHistoryEntry(
				int sequence,
				String source,
				RecommendationSchedulerPlan plan,
				NodeExecutionMonitor.Snapshot snapshot,
				int algorithm)
		{
			this.sequence = Math.max(1, sequence);
			this.source = source == null ? "Run" : source;
			this.plan = plan == null
					? RecommendationSchedulerPlan.defaultPlan() : plan;
			this.policyLabel = this.plan.getPolicy().getLabel();
			this.algorithm = algorithm;
			this.runPhase = snapshot == null
					? "No run" : snapshot.getRunPhase();
			this.runId = snapshot == null ? "" : snapshot.getRunId();
			this.nodeCount = snapshot == null ? 0 : snapshot.getNodes().size();
			this.completeNodes = snapshot == null
					? 0 : snapshot.count(NodeExecutionMonitor.State.COMPLETE);
			this.failedNodes = snapshot == null
					? 0 : snapshot.count(NodeExecutionMonitor.State.FAILED);
			this.stoppedNodes = snapshot == null
					? 0 : snapshot.count(NodeExecutionMonitor.State.STOPPED);
			this.startedAt = actualStartMillis(snapshot);
			this.endedAt = actualEndMillis(snapshot, System.currentTimeMillis());
			this.makespanMillis = Math.max(0L, endedAt - startedAt);
			this.utilization = computeActualUtilization(
					snapshot,
					endedAt > 0L ? endedAt : System.currentTimeMillis());

			int slowNode = 0;
			long slowMillis = 0L;
			if (snapshot != null)
			{
				for (NodeExecutionMonitor.NodeSnapshot node : snapshot.getNodes())
				{
					long nodeStart = nodeRunStartMillis(node, startedAt);
					long nodeEnd = nodeEndMillis(node,
							endedAt > 0L ? endedAt : System.currentTimeMillis());
					long nodeMillis = Math.max(0L, nodeEnd - nodeStart);
					if (nodeMillis > slowMillis)
					{
						slowMillis = nodeMillis;
						slowNode = node.getNodeIndex();
					}
				}
			}
			this.slowestNode = slowNode;
			this.slowestNodeMillis = slowMillis;
		}

		String label()
		{
			return "Run " + sequence;
		}
	}

	private final class TimelineCanvas extends JPanel
	{
		private NodeExecutionMonitor.Snapshot snapshot =
				NodeExecutionMonitor.snapshot();
		private RecommendationSchedulerPlan plan =
				RecommendationSchedulerPlan.defaultPlan();
		private long now = System.currentTimeMillis();

		TimelineCanvas()
		{
			setOpaque(true);
			setBackground(SURFACE);
			setPreferredSize(new Dimension(920, timelinePreferredHeight(0)));
		}

		void setSnapshot(
				NodeExecutionMonitor.Snapshot snapshot,
				RecommendationSchedulerPlan plan,
				long now)
		{
			this.snapshot = snapshot;
			this.plan = plan == null
					? RecommendationSchedulerPlan.defaultPlan() : plan;
			this.now = Math.max(0L, now);
			int nodeCount = this.snapshot == null
					? 0 : this.snapshot.getNodes().size();
			int preferredHeight = timelinePreferredHeight(nodeCount);
			Dimension preferred = getPreferredSize();
			if (preferred.height != preferredHeight)
			{
				setPreferredSize(new Dimension(
						Math.max(920, preferred.width),
						preferredHeight));
				revalidate();
			}
			repaint();
		}

		private int timelinePreferredHeight(int nodeCount)
		{
			int lanes = Math.max(1, nodeCount);
			return Math.max(320, 124 + lanes * 42 + 58);
		}

		protected void paintComponent(Graphics graphics)
		{
			super.paintComponent(graphics);
			Graphics2D g = (Graphics2D)graphics.create();
			NebulaDesign.prepare(g);
			int width = getWidth();
			int height = getHeight();
			g.setColor(SURFACE);
			g.fillRoundRect(0, 0, width - 1, height - 1, 14, 14);
			g.setColor(BORDER);
			g.drawRoundRect(0, 0, width - 1, height - 1, 14, 14);
			if (snapshot == null || snapshot.getNodes().isEmpty())
			{
				drawTitle(g, "No real simulator run yet", 24, 32);
				drawSubtitle(g,
						"Run the Simulator tab; actual node timing will appear here.",
						24,
						52);
				g.dispose();
				return;
			}
			drawTimelineHeader(g, width);
			int contentBottom = drawNodeLanes(g, width, height);
			drawTimelineLegend(g, 24, Math.min(height - 18, contentBottom + 34));
			g.dispose();
		}

		private void drawTimelineHeader(Graphics2D g, int width)
		{
			String policy = plan.getPolicy().getLabel();
			String contract = plan.isActive()
					? plan.getMode().getLabel() : "DSMP default preview";
			long start = actualStartMillis(snapshot);
			long end = actualEndMillis(snapshot, now);
			long elapsed = Math.max(0L, end - start);
			drawTitle(g, "Real Simulator Timeline", 24, 27);
			drawSubtitle(g, snapshot.getAlgorithmName() + " | " + contract
					+ " | policy " + policy + " | "
					+ cleanText(snapshot.getRunPhase(), "Waiting for run."),
					24,
					46);
			int boxY = 16;
			String[] labels = { "Nodes", "Elapsed", "Last node", "Busy" };
			String[] values = {
					snapshot.getNodes().size() + "",
					formatDuration(elapsed),
					lastNodeLabel(snapshot, now),
					percentage(computeActualUtilization(snapshot, now))
			};
			int[] offsets = { 520, 394, 268, 142 };
			int[] widths = { 120, 118, 118, 118 };
			for (int index = 0; index < labels.length; index++)
			{
				int boxX = width - offsets[index];
				if (boxX < 560)
				{
					continue;
				}
				drawSmallMetric(g, boxX, boxY, widths[index],
						labels[index], values[index]);
			}
		}

		private int drawNodeLanes(Graphics2D g, int width, int height)
		{
			int left = 94;
			int right = width - 140;
			int top = 78;
			List<NodeExecutionMonitor.NodeSnapshot> nodes = snapshot.getNodes();
			int laneHeight = 42;
			int bottom = top + nodes.size() * laneHeight;
			int lineWidth = Math.max(1, right - left);
			long start = actualStartMillis(snapshot);
			long end = actualEndMillis(snapshot, now);
			long span = Math.max(1000L, end - start);
			for (int i = 0; i < nodes.size(); i++)
			{
				NodeExecutionMonitor.NodeSnapshot node = nodes.get(i);
				int y = top + i * laneHeight;
				Color accent = stateColor(node.getState());
				g.setColor(i % 2 == 0 ? SURFACE_ALT : SURFACE);
				g.fillRoundRect(18, y - 7, width - 36, laneHeight - 5, 8, 8);
				g.setColor(BORDER);
				g.drawLine(left, y + 14, right, y + 14);
				g.setColor(TEXT);
				g.setFont(font(Font.BOLD, 11));
				g.drawString("Node " + node.getNodeIndex(), 24, y + 10);
				g.setColor(MUTED);
				g.setFont(font(Font.PLAIN, 10));
				g.drawString(shortText(node.getCapacityProfile().shortLabel(), 18),
						24,
						y + 25);
				long nodeEnd = nodeEndMillis(node, now);
				int finishX = left + (int)Math.round(
						lineWidth * (nodeEnd - start) / (double)span);
				g.setColor(new Color(accent.getRed(), accent.getGreen(),
						accent.getBlue(), 130));
				g.fillOval(finishX - 4, y + 10, 8, 8);
				g.setColor(MUTED);
				g.setFont(font(Font.BOLD, 10));
				g.drawString(formatDuration(Math.max(0L, nodeEnd - start)),
						right + 12,
						y + 12);
			}
			drawActualSegments(g, nodes, left, lineWidth, top, laneHeight,
					start, span, end);
			if (!isActualRunTerminal(snapshot))
			{
				int markerX = left + (int)Math.round(
						lineWidth * (Math.max(start, now) - start) / (double)span);
				markerX = Math.max(left, Math.min(right, markerX));
				g.setColor(alpha(GREEN, 190));
				g.setStroke(new BasicStroke(2f));
				g.drawLine(markerX, top - 10, markerX, bottom + 6);
				g.setFont(font(Font.BOLD, 10));
				g.drawString("now", markerX + 5, top - 12);
			}
			g.setColor(MUTED);
			g.setFont(font(Font.BOLD, 11));
			g.drawString("Total time until last node: "
					+ formatDuration(Math.max(0L, end - start)),
					24,
					bottom + 18);
			return bottom + 18;
		}

		private void drawActualSegments(
				Graphics2D g,
				List<NodeExecutionMonitor.NodeSnapshot> nodes,
				int lineStart,
				int lineWidth,
				int top,
				int laneHeight,
				long start,
				long span,
				long end)
		{
			for (int index = 0; index < nodes.size(); index++)
			{
				NodeExecutionMonitor.NodeSnapshot node = nodes.get(index);
				long nodeEnd = Math.min(end, nodeEndMillis(node, now));
				if (nodeEnd <= start)
				{
					continue;
				}
				NodeExecutionMonitor.State state = stateAtStart(node, start);
				String stage = cleanText(node.getStage(), "Working");
				long cursor = Math.max(start, nodeRunStartMillis(node, start));
				for (NodeExecutionMonitor.NodeEvent event : node.getEvents())
				{
					if (event.getTime() < start)
					{
						state = event.getState();
						stage = cleanText(event.getStage(), stage);
						continue;
					}
					if (event.getTime() > nodeEnd)
					{
						break;
					}
					drawStateSegment(g, index, lineStart, lineWidth, top,
							laneHeight, start, span, cursor, event.getTime(),
							state, stage);
					cursor = event.getTime();
					state = event.getState();
					stage = cleanText(event.getStage(), stage);
				}
				drawStateSegment(g, index, lineStart, lineWidth, top,
						laneHeight, start, span, cursor, nodeEnd, state, stage);
				drawNodeDuration(g, node, lineStart, lineWidth, top, laneHeight,
						start, span, nodeEnd, index);
			}
		}

		private void drawStateSegment(
				Graphics2D g,
				int nodeSlot,
				int lineStart,
				int lineWidth,
				int top,
				int laneHeight,
				long start,
				long span,
				long segmentStart,
				long segmentEnd,
				NodeExecutionMonitor.State state,
				String stage)
		{
			if (segmentEnd < segmentStart)
			{
				segmentEnd = segmentStart;
			}
			int x = lineStart + (int)Math.round(lineWidth
					* (segmentStart - start) / (double)span);
			int x2 = lineStart + (int)Math.round(lineWidth
					* (segmentEnd - start) / (double)span);
			int y = top + nodeSlot * laneHeight + 6;
			Color accent = stateColor(state);
			int segmentWidth = Math.max(6, x2 - x);
			g.setColor(new Color(accent.getRed(), accent.getGreen(),
					accent.getBlue(), isWorkingState(state) ? 170 : 90));
			g.fillRoundRect(x, y, segmentWidth, 17, 9, 9);
			g.setColor(accent.darker());
			g.drawRoundRect(x, y, segmentWidth, 17, 9, 9);
			if (segmentWidth > 108)
			{
				g.setFont(font(Font.BOLD, 9));
				g.setColor(themed(Color.WHITE, NebulaDesign.NIGHT_TEXT_ON_ACCENT));
				g.drawString(shortText(stage, 22), x + 6, y + 12);
			}
		}

		private void drawNodeDuration(
				Graphics2D g,
				NodeExecutionMonitor.NodeSnapshot node,
				int lineStart,
				int lineWidth,
				int top,
				int laneHeight,
				long start,
				long span,
				long nodeEnd,
				int index)
		{
			int y = top + index * laneHeight;
			long nodeStart = nodeRunStartMillis(node, start);
			String stateText = node.getState().name().toLowerCase(Locale.US);
			g.setColor(MUTED);
			g.setFont(font(Font.PLAIN, 10));
			g.drawString(formatDuration(Math.max(0L, nodeEnd - nodeStart))
					+ " | " + stateText,
					lineStart + lineWidth + 12,
					y + 27);
		}

		private void drawSmallMetric(
				Graphics2D g,
				int x,
				int y,
				int width,
				String label,
				String value)
		{
			g.setColor(SURFACE_ALT);
			g.fillRoundRect(x, y, width, 42, 10, 10);
			g.setColor(BORDER);
			g.drawRoundRect(x, y, width, 42, 10, 10);
			g.setColor(MUTED);
			g.setFont(font(Font.BOLD, 9));
			g.drawString(label.toUpperCase(Locale.US), x + 10, y + 15);
			g.setColor(TEXT);
			g.setFont(font(Font.BOLD, 17));
			g.drawString(value, x + 10, y + 33);
		}

		private void drawTimelineLegend(Graphics2D g, int x, int y)
		{
			NodeExecutionMonitor.State[] states = {
					NodeExecutionMonitor.State.RECEIVING,
					NodeExecutionMonitor.State.PREPROCESSING,
					NodeExecutionMonitor.State.FEATURES,
					NodeExecutionMonitor.State.MODEL_RUNNING,
					NodeExecutionMonitor.State.PUBLISHING,
					NodeExecutionMonitor.State.WAITING_FOR_MEMORY,
					NodeExecutionMonitor.State.COMPLETE,
					NodeExecutionMonitor.State.FAILED
			};
			int offset = 0;
			for (NodeExecutionMonitor.State state : states)
			{
				Color color = stateColor(state);
				g.setColor(color);
				g.fillRoundRect(x + offset, y - 10, 16, 8, 8, 8);
				g.setColor(MUTED);
				g.setFont(font(Font.PLAIN, 9));
				g.drawString(state.name().toLowerCase(Locale.US),
						x + offset + 21, y - 2);
				offset += 118;
				if (x + offset > getWidth() - 120)
				{
					break;
				}
			}
		}
	}

	private final class HistoryComparisonCanvas extends JPanel
	{
		private List<ScheduleHistoryEntry> history =
				new ArrayList<ScheduleHistoryEntry>();

		HistoryComparisonCanvas()
		{
			setOpaque(true);
			setBackground(SURFACE);
			setPreferredSize(new Dimension(420, 180));
		}

		void setHistory(List<ScheduleHistoryEntry> history)
		{
			this.history = history == null
					? new ArrayList<ScheduleHistoryEntry>()
					: new ArrayList<ScheduleHistoryEntry>(history);
			repaint();
		}

		protected void paintComponent(Graphics graphics)
		{
			super.paintComponent(graphics);
			Graphics2D g = (Graphics2D)graphics.create();
			NebulaDesign.prepare(g);
			int width = getWidth();
			int height = getHeight();
			g.setColor(SURFACE);
			g.fillRoundRect(0, 0, width - 1, height - 1, 12, 12);
			g.setColor(BORDER);
			g.drawRoundRect(0, 0, width - 1, height - 1, 12, 12);
			drawTitle(g, "Visual Comparison", 18, 26);
			if (height >= 70)
			{
				drawSubtitle(g,
						"Shorter bars finish earlier; utilization shows how much node time was busy.",
						18,
						45);
			}
			if (height < 100)
			{
				g.dispose();
				return;
			}
			if (history.isEmpty())
			{
				int boxHeight = Math.min(42, height - 66 - 10);
				g.setColor(SURFACE_ALT);
				g.fillRoundRect(20, 66, width - 40, boxHeight, 12, 12);
				g.setColor(MUTED);
				g.setFont(font(Font.BOLD, 12));
				FontMetrics metrics = g.getFontMetrics();
				g.drawString("Run the Simulator to save the first real timing comparison.",
						34,
						66 + (boxHeight + metrics.getAscent()
								- metrics.getDescent()) / 2);
				g.dispose();
				return;
			}
			int startY = 68;
			int count = Math.min(Math.min(6, history.size()),
					Math.max(1, (height - startY - 14) / 22));
			long max = 1L;
			for (int i = 0; i < count; i++)
			{
				max = Math.max(max, history.get(i).makespanMillis);
			}
			int rowHeight = Math.max(22, (height - startY - 18) / count);
			int gutter = 122;
			int barX = 100;
			int trackWidth = Math.max(40, width - barX - gutter);
			for (int i = 0; i < count; i++)
			{
				ScheduleHistoryEntry entry = history.get(i);
				int y = startY + i * rowHeight;
				int barWidth = Math.max(6,
						(int)Math.round(trackWidth
								* entry.makespanMillis / (double)max));
				Color accent = i == 0 ? GREEN : BLUE;
				g.setColor(TEXT);
				g.setFont(font(Font.BOLD, 11));
				g.drawString(fitText(g, entry.label(), barX - 26), 18, y + 12);
				g.setColor(track());
				g.fillRoundRect(barX, y + 3, trackWidth, 10, 10, 10);
				g.setColor(new Color(accent.getRed(), accent.getGreen(),
						accent.getBlue(), 170));
				g.fillRoundRect(barX, y + 3, barWidth, 10, 10, 10);
				g.setColor(MUTED);
				g.setFont(NebulaDesign.fontMono(10f));
				g.drawString(formatDuration(entry.makespanMillis) + " | "
						+ percentage(entry.utilization),
						barX + trackWidth + 12,
						y + 12);
			}
			g.dispose();
		}
	}

	private final class NodeAssignmentTableModel extends AbstractTableModel
	{
		private final String[] columns = {
				"Node", "Role", "Weight", "Assigned", "Done", "Retries",
				"Peak", "Busy"
		};
		private SchedulerLabEngine.Result result;

		void setResult(SchedulerLabEngine.Result result)
		{
			this.result = result;
			fireTableDataChanged();
		}

		public int getRowCount()
		{
			return result == null ? 0 : result.getNodes().size();
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
			SchedulerLabEngine.NodeSummary summary =
					result.getNodes().get(row);
			NodeCapacityProfile profile = summary.getProfile();
			switch (column)
			{
				case 0:
					return summary.getNodeIndex();
				case 1:
					return profile.getRole().getLabel();
				case 2:
					return DECIMAL.format(
							NodeCapacityScheduler.schedulingWeight(profile));
				case 3:
					return summary.getAssignedJobs();
				case 4:
					return summary.getCompletedJobs();
				case 5:
					return summary.getRetriedJobs();
				case 6:
					return summary.getQueuePeak();
				case 7:
					return summary.getBusyTicks();
				default:
					return "";
			}
		}
	}

	private final class ScheduleHistoryTableModel extends AbstractTableModel
	{
		private final String[] columns = {
				"Run", "Source", "Mode", "Policy", "Algorithm", "Nodes",
				"Makespan", "Last node", "Busy", "Complete", "Failed"
		};
		private List<ScheduleHistoryEntry> history =
				new ArrayList<ScheduleHistoryEntry>();

		void setHistory(List<ScheduleHistoryEntry> history)
		{
			this.history = history == null
					? new ArrayList<ScheduleHistoryEntry>()
					: new ArrayList<ScheduleHistoryEntry>(history);
			fireTableDataChanged();
		}

		public int getRowCount()
		{
			return history.size();
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
			ScheduleHistoryEntry entry = history.get(row);
			switch (column)
			{
				case 0:
					return entry.label();
				case 1:
					return entry.source;
				case 2:
					return entry.plan.getMode().getLabel();
				case 3:
					return entry.policyLabel;
				case 4:
					return RecommendationModeSupport.algorithmName(entry.algorithm);
				case 5:
					return entry.nodeCount;
				case 6:
					return formatDuration(entry.makespanMillis);
				case 7:
					return entry.slowestNode <= 0
							? "--"
							: "Node " + entry.slowestNode
									+ " | " + formatDuration(
											entry.slowestNodeMillis);
				case 8:
					return percentage(entry.utilization);
				case 9:
					return entry.completeNodes + "/"
							+ Math.max(0, entry.nodeCount);
				case 10:
					return entry.failedNodes
							+ (entry.stoppedNodes > 0
									? " stopped " + entry.stoppedNodes : "");
				default:
					return "";
			}
		}
	}

	private final class NodeCellRenderer extends DefaultTableCellRenderer
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
					table,
					value,
					isSelected,
					hasFocus,
					row,
					column);
			String text = value == null ? "" : String.valueOf(value);
			boolean numeric = text.length() > 0
					&& (Character.isDigit(text.charAt(0))
							|| text.charAt(0) == '-');
			if (column <= 1 && !numeric)
			{
				component.setFont(NebulaDesign.fontMedium(12f));
			}
			else if (numeric)
			{
				component.setFont(NebulaDesign.fontMono(11.5f));
			}
			else
			{
				component.setFont(NebulaDesign.fontBody(12f));
			}
			component.setForeground(TEXT);
			component.setBackground(isSelected
					? NebulaDesign.SELECTION
					: SURFACE);
			setBorder(new EmptyBorder(0, 10, 0, 10));
			return component;
		}
	}

	private final class HeaderSignalPanel extends JPanel
	{
		HeaderSignalPanel()
		{
			setOpaque(false);
			setPreferredSize(new Dimension(360, 74));
		}

		protected void paintComponent(Graphics graphics)
		{
			super.paintComponent(graphics);
			Graphics2D g = (Graphics2D)graphics.create();
			NebulaDesign.prepare(g);
			int width = getWidth();
			int height = getHeight();
			int[] xs = { 20, width / 4, width / 2, width * 3 / 4, width - 24 };
			int[] ys = { height / 2 + 5, height / 2 - 5,
					height / 2 + 8, height / 2 - 2, height / 2 + 6 };
			g.setStroke(new BasicStroke(2f));
			for (int i = 0; i < xs.length - 1; i++)
			{
				g.setColor(alpha(BLUE, 70));
				g.drawLine(xs[i], ys[i], xs[i + 1], ys[i + 1]);
			}
			for (int i = 0; i < xs.length; i++)
			{
				Color hue = i % 2 == 0 ? BLUE : GREEN;
				g.setColor(tint(hue, 0.86f));
				g.fillOval(xs[i] - 7, ys[i] - 7, 14, 14);
				g.setColor(hue);
				g.drawOval(xs[i] - 7, ys[i] - 7, 14, 14);
			}
			g.dispose();
		}
	}
}
