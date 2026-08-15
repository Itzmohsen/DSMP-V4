package TwitterGatherDataFollowers.userRyersonU;

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
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;

final class SchedulerLabPanel extends JPanel
{
	private static final Color BACKGROUND = StudioNightTheme.adaptiveColor(
			new Color(242, 246, 250), StudioNightTheme.NIGHT_BACKGROUND);
	private static final Color SURFACE = StudioNightTheme.adaptiveColor(
			Color.WHITE, StudioNightTheme.NIGHT_SURFACE);
	private static final Color SURFACE_ALT = StudioNightTheme.adaptiveColor(
			new Color(247, 250, 253), StudioNightTheme.NIGHT_SURFACE_ALT);
	private static final Color BORDER = StudioNightTheme.adaptiveColor(
			new Color(207, 216, 227), StudioNightTheme.NIGHT_BORDER);
	private static final Color TEXT = StudioNightTheme.adaptiveColor(
			new Color(24, 29, 36), StudioNightTheme.NIGHT_TEXT);
	private static final Color MUTED = StudioNightTheme.adaptiveColor(
			new Color(91, 101, 116), StudioNightTheme.NIGHT_MUTED);
	private static final Color BLUE = StudioNightTheme.adaptiveColor(
			new Color(83, 113, 220), StudioNightTheme.NIGHT_BLUE);
	private static final Color GREEN = StudioNightTheme.adaptiveColor(
			new Color(88, 154, 112), StudioNightTheme.NIGHT_GREEN);
	private static final Color AMBER = StudioNightTheme.adaptiveColor(
			new Color(190, 145, 66), StudioNightTheme.NIGHT_AMBER);
	private static final Color PURPLE = StudioNightTheme.adaptiveColor(
			new Color(129, 100, 211), StudioNightTheme.NIGHT_PURPLE);
	private static final Color RED = StudioNightTheme.adaptiveColor(
			new Color(202, 74, 63), StudioNightTheme.NIGHT_RED);
	private static final DecimalFormat DECIMAL = new DecimalFormat("0.##");

	private final JComboBox<SchedulerLabEngine.SchedulingPolicy> policyBox =
			new JComboBox<SchedulerLabEngine.SchedulingPolicy>(
					SchedulerLabEngine.SchedulingPolicy.values());
	private final JComboBox<SchedulerLabEngine.WorkloadTemplate> templateBox =
			new JComboBox<SchedulerLabEngine.WorkloadTemplate>(
					SchedulerLabEngine.WorkloadTemplate.values());
	private final JSpinner nodesSpinner = new JSpinner(
			new SpinnerNumberModel(
					Math.max(1, NodeCapacityRegistry.configuredNodeCount()),
					1,
					64,
					1));
	private final JSpinner jobsSpinner =
			new JSpinner(new SpinnerNumberModel(120, 1, 20000, 10));
	private final JButton runButton =
			button("Run schedule", BLUE, Color.WHITE);
	private final JButton stopButton =
			button("Reset playback", SURFACE_ALT, TEXT);
	private final JButton capacityButton =
			button("Node capacity", SURFACE_ALT, TEXT);
	private final JButton defaultsButton =
			button("Restore DSMP defaults", SURFACE_ALT, TEXT);
	private final JLabel policyMetric = metricValue("--");
	private final JLabel jobsMetric = metricValue("--");
	private final JLabel makespanMetric = metricValue("--");
	private final JLabel utilizationMetric = metricValue("--");
	private final JLabel retryMetric = metricValue("--");
	private final JLabel statusPill = new JLabel("READY");
	private final JLabel statusLabel =
			new JLabel("Choose a workload model and scheduling policy.");
	private final JLabel policyDetail = new JLabel();
	private final QueueCanvas queueCanvas = new QueueCanvas();
	private final SchedulerContractCanvas contractCanvas =
			new SchedulerContractCanvas();
	private final PolicyComparisonCanvas comparisonCanvas =
			new PolicyComparisonCanvas();
	private final NodeGridCanvas nodeGridCanvas = new NodeGridCanvas();
	private final TimelineCanvas timelineCanvas = new TimelineCanvas();
	private final NodeTableModel nodeTableModel = new NodeTableModel();
	private final JTable nodeTable = new JTable(nodeTableModel);
	private final JTextArea eventLog = new JTextArea();
	private final Timer playbackTimer;

	private SchedulerLabEngine.Result currentResult;
	private List<SchedulerLabEngine.Result> comparisonResults =
			new ArrayList<SchedulerLabEngine.Result>();
	private boolean comparisonSampled;
	private int comparisonJobCount;
	private PlaybackFrame currentFrame = new PlaybackFrame(1);
	private int playbackTick;

	SchedulerLabPanel(ControllerAgentGui owner)
	{
		super(new BorderLayout(0, 14));
		setBackground(BACKGROUND);
		setBorder(new EmptyBorder(14, 14, 14, 14));
		buildInterface();
		installActions();
		playbackTimer = new Timer(35, new ActionListener()
		{
			public void actionPerformed(ActionEvent event)
			{
				advancePlayback();
			}
		});
		playbackTimer.setCoalesce(true);
		updatePolicyContext();
		runSchedule(false);
	}

	void refreshCapacityFromSimulator()
	{
		int nodes = ((Number)nodesSpinner.getValue()).intValue();
		NodeCapacityRegistry.configureForNodeCount(
				nodes,
				RuntimeResourceSettings.currentMaxHeapMb());
		if (currentResult == null)
		{
			runSchedule(false);
		}
		else
		{
			refreshCapacitySummary();
		}
	}

	private void buildInterface()
	{
		add(createHeader(), BorderLayout.NORTH);
		JPanel body = new JPanel(new BorderLayout(0, 12));
		body.setOpaque(false);
		body.add(createControlBand(), BorderLayout.NORTH);
		body.add(createWorkArea(), BorderLayout.CENTER);
		add(body, BorderLayout.CENTER);
		add(createStatusBar(), BorderLayout.SOUTH);
	}

	private JComponent createHeader()
	{
		JPanel header = surface(new BorderLayout(24, 0));
		header.setBorder(new EmptyBorder(16, 18, 16, 18));

		JPanel titleStack = new JPanel();
		titleStack.setOpaque(false);
		titleStack.setLayout(new BoxLayout(titleStack, BoxLayout.Y_AXIS));
		JLabel title = new JLabel("Scheduler Lab (Demo)");
		title.setFont(font(Font.BOLD, 27));
		title.setForeground(TEXT);
		JLabel subtitle = new JLabel(
				"Run isolated work-unit scheduling demos with queues, retries, and what-if policies without changing recommendation scoring.");
		subtitle.setFont(font(Font.PLAIN, 12));
		subtitle.setForeground(MUTED);
		titleStack.add(title);
		titleStack.add(Box.createRigidArea(new Dimension(0, 4)));
		titleStack.add(subtitle);
		titleStack.add(createAttributionBlock());

		header.add(titleStack, BorderLayout.WEST);
		header.add(new HeaderSignalPanel(), BorderLayout.CENTER);
		return header;
	}

	private JComponent createControlBand()
	{
		JPanel band = surface(new BorderLayout(16, 0));
		band.setBorder(new EmptyBorder(14, 16, 14, 16));

		JPanel controls = new JPanel(new GridBagLayout());
		controls.setOpaque(false);
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.insets = new Insets(0, 0, 0, 10);
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.weightx = 1.0;
		gbc.gridx = 0;
		gbc.gridy = 0;
		controls.add(control("Scheduling policy", policyBox), gbc);
		gbc.gridx++;
		controls.add(control("Workload model", templateBox), gbc);
		gbc.gridx++;
		gbc.weightx = 0.35;
		controls.add(control("Nodes", nodesSpinner), gbc);
		gbc.gridx++;
		controls.add(control("Work units", jobsSpinner), gbc);
		gbc.gridx++;
		gbc.weightx = 0.0;
		JPanel actionRow = new JPanel(new GridLayout(2, 2, 8, 8));
		actionRow.setOpaque(false);
		actionRow.add(capacityButton);
		actionRow.add(defaultsButton);
		actionRow.add(runButton);
		actionRow.add(stopButton);
		controls.add(actionRow, gbc);

		JPanel context = new JPanel(new BorderLayout(10, 0));
		context.setOpaque(true);
		context.setBackground(SURFACE_ALT);
		context.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(new Color(220, 227, 236)),
				new EmptyBorder(10, 12, 10, 12)));
		JLabel label = new JLabel("Policy contract");
		label.setFont(font(Font.BOLD, 11));
		label.setForeground(BLUE);
		policyDetail.setFont(font(Font.PLAIN, 11));
		policyDetail.setForeground(MUTED);
		context.add(label, BorderLayout.WEST);
		context.add(policyDetail, BorderLayout.CENTER);

		JPanel stack = new JPanel();
		stack.setOpaque(false);
		stack.setLayout(new BoxLayout(stack, BoxLayout.Y_AXIS));
		stack.add(controls);
		stack.add(Box.createRigidArea(new Dimension(0, 10)));
		stack.add(context);
		band.add(stack, BorderLayout.CENTER);
		band.add(createMetricStrip(), BorderLayout.SOUTH);
		return band;
	}

	private JPanel createMetricStrip()
	{
		JPanel strip = new JPanel(new GridLayout(1, 5, 10, 0));
		strip.setOpaque(false);
		strip.setBorder(new EmptyBorder(12, 0, 0, 0));
		strip.add(metricCard("Policy", policyMetric, BLUE));
		strip.add(metricCard("Jobs", jobsMetric, PURPLE));
		strip.add(metricCard("Makespan", makespanMetric, AMBER));
		strip.add(metricCard("Utilization", utilizationMetric, GREEN));
		strip.add(metricCard("Retries", retryMetric, RED));
		return strip;
	}

	private JComponent createWorkArea()
	{
		JPanel cockpit = new JPanel(new GridLayout(1, 2, 12, 0));
		cockpit.setOpaque(false);
		cockpit.add(createQueuePanel());
		cockpit.add(createPolicyPanel());

		JPanel left = new JPanel(new BorderLayout(0, 12));
		left.setOpaque(false);
		left.add(createNodePanel(), BorderLayout.CENTER);

		JPanel right = new JPanel(new BorderLayout(0, 12));
		right.setOpaque(false);
		right.add(createTimelinePanel(), BorderLayout.CENTER);
		right.add(createLogPanel(), BorderLayout.SOUTH);

		JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, right);
		split.setResizeWeight(0.58);
		split.setDividerSize(8);
		split.setBorder(BorderFactory.createEmptyBorder());
		split.setOpaque(false);

		JPanel stack = new JPanel(new BorderLayout(0, 12));
		stack.setOpaque(false);
		stack.add(cockpit, BorderLayout.NORTH);
		stack.add(split, BorderLayout.CENTER);
		return stack;
	}

	private JComponent createQueuePanel()
	{
		JPanel panel = section("Workload Unit Model",
				"Formal jobs: preprocessing batches, target-user jobs, candidate shards, model training, scoring, and graph/reporting.");
		queueCanvas.setPreferredSize(new Dimension(600, 210));
		panel.add(queueCanvas, BorderLayout.CENTER);
		return panel;
	}

	private JComponent createPolicyPanel()
	{
		JPanel panel = section("Policy Comparison Board",
				"Runs the same workload through every strategy and shows the scheduling tradeoff.");
		JPanel stack = new JPanel(new BorderLayout(0, 10));
		stack.setOpaque(false);
		contractCanvas.setPreferredSize(new Dimension(600, 84));
		comparisonCanvas.setPreferredSize(new Dimension(600, 126));
		stack.add(contractCanvas, BorderLayout.NORTH);
		stack.add(comparisonCanvas, BorderLayout.CENTER);
		panel.add(stack, BorderLayout.CENTER);
		return panel;
	}

	private JComponent createNodePanel()
	{
		JPanel panel = section("Live Node Load",
				"Cards use the capacity contract: CPU multiplier, memory budget, queue capacity, delay, slowdown, and failures.");
		nodeGridCanvas.setPreferredSize(new Dimension(600, 285));
		panel.add(nodeGridCanvas, BorderLayout.CENTER);
		JPanel tablePanel = new JPanel(new BorderLayout());
		tablePanel.setOpaque(false);
		tablePanel.setBorder(new EmptyBorder(8, 0, 0, 0));
		configureNodeTable();
		JScrollPane scroll = new JScrollPane(nodeTable);
		scroll.setPreferredSize(new Dimension(600, 150));
		scroll.setBorder(BorderFactory.createLineBorder(BORDER));
		tablePanel.add(scroll, BorderLayout.CENTER);
		panel.add(tablePanel, BorderLayout.SOUTH);
		return panel;
	}

	private JComponent createTimelinePanel()
	{
		JPanel panel = section("Scheduling Strategy View",
				"Shows how the selected policy assigns work, starts jobs, retries failures, and keeps nodes busy.");
		timelineCanvas.setPreferredSize(new Dimension(640, 415));
		panel.add(timelineCanvas, BorderLayout.CENTER);
		return panel;
	}

	private JComponent createLogPanel()
	{
		JPanel panel = section("Scheduler Event Log",
				"Read-only simulation log. It does not start JADE agents, write graph files, or change recommendation formulas.");
		eventLog.setEditable(false);
		eventLog.setLineWrap(false);
		eventLog.setFont(new Font("Monospaced", Font.PLAIN, 11));
		eventLog.setForeground(new Color(228, 233, 242));
		eventLog.setBackground(new Color(19, 23, 34));
		eventLog.setBorder(new EmptyBorder(10, 10, 10, 10));
		JScrollPane scroll = new JScrollPane(eventLog);
		scroll.setPreferredSize(new Dimension(640, 180));
		scroll.setBorder(BorderFactory.createLineBorder(new Color(31, 38, 52)));
		panel.add(scroll, BorderLayout.CENTER);
		return panel;
	}

	private JComponent createStatusBar()
	{
		JPanel bar = surface(new BorderLayout(10, 0));
		bar.setBorder(new EmptyBorder(9, 12, 9, 12));
		statusPill.setOpaque(true);
		statusPill.setHorizontalAlignment(SwingConstants.CENTER);
		statusPill.setFont(font(Font.BOLD, 10));
		statusPill.setForeground(GREEN.darker());
		statusPill.setBackground(new Color(228, 245, 236));
		statusPill.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(new Color(183, 220, 199)),
				new EmptyBorder(4, 10, 4, 10)));
		statusLabel.setFont(font(Font.BOLD, 11));
		statusLabel.setForeground(TEXT);
		bar.add(statusPill, BorderLayout.WEST);
		bar.add(statusLabel, BorderLayout.CENTER);
		return bar;
	}

	private void installActions()
	{
		policyBox.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent event)
			{
				updatePolicyContext();
				runSchedule(false);
			}
		});
		nodesSpinner.addChangeListener(new javax.swing.event.ChangeListener()
		{
			public void stateChanged(javax.swing.event.ChangeEvent event)
			{
				int nodes = ((Number)nodesSpinner.getValue()).intValue();
				NodeCapacityRegistry.configureForNodeCount(
						nodes,
						RuntimeResourceSettings.currentMaxHeapMb());
				runSchedule(false);
			}
		});
		jobsSpinner.addChangeListener(new javax.swing.event.ChangeListener()
		{
			public void stateChanged(javax.swing.event.ChangeEvent event)
			{
				runSchedule(false);
			}
		});
		templateBox.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent event)
			{
				runSchedule(false);
			}
		});
		runButton.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent event)
			{
				runSchedule(true);
			}
		});
		stopButton.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent event)
			{
				stopPlayback();
			}
		});
		capacityButton.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent event)
			{
				int nodes = ((Number)nodesSpinner.getValue()).intValue();
				Window window = SwingUtilities.getWindowAncestor(
						SchedulerLabPanel.this);
				NodeCapacitySettingsDialog.showDialog(
						window,
						nodes,
						RuntimeResourceSettings.currentMaxHeapMb());
				runSchedule(false);
			}
		});
		defaultsButton.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent event)
			{
				int nodes = ((Number)nodesSpinner.getValue()).intValue();
				NodeCapacityRegistry.resetToDefaults(
						nodes,
						RuntimeResourceSettings.currentMaxHeapMb());
				runSchedule(false);
			}
		});
	}

	private void updatePolicyContext()
	{
		SchedulerLabEngine.SchedulingPolicy policy =
				(SchedulerLabEngine.SchedulingPolicy)policyBox.getSelectedItem();
		if (policy != null)
		{
			policyDetail.setText(policy.getDescription());
		}
		contractCanvas.repaint();
	}

	private void refreshCapacitySummary()
	{
		if (currentResult != null)
		{
			nodeTableModel.setResult(currentResult, currentFrame);
			nodeGridCanvas.setResult(currentResult, currentFrame);
			timelineCanvas.setResult(currentResult, currentFrame);
		}
	}

	private void runSchedule(boolean animate)
	{
		playbackTimer.stop();
		int nodes = ((Number)nodesSpinner.getValue()).intValue();
		int jobs = ((Number)jobsSpinner.getValue()).intValue();
		NodeCapacityRegistry.configureForNodeCount(
				nodes,
				RuntimeResourceSettings.currentMaxHeapMb());
		List<SchedulerLabEngine.WorkloadUnit> workload =
				SchedulerLabEngine.createWorkload(
						(SchedulerLabEngine.WorkloadTemplate)
								templateBox.getSelectedItem(),
						jobs,
						jobs);
		List<NodeCapacityProfile> profiles = NodeCapacityRegistry.snapshot();
		SchedulerLabEngine.WorkloadTemplate template =
				(SchedulerLabEngine.WorkloadTemplate)templateBox.getSelectedItem();
		int heapMb = RuntimeResourceSettings.currentMaxHeapMb();
		currentResult = SchedulerLabEngine.simulate(
				profiles,
				(SchedulerLabEngine.SchedulingPolicy)policyBox.getSelectedItem(),
				template,
				workload,
				heapMb);
		comparisonResults = buildPolicyComparisons(
				profiles,
				template,
				workload,
				heapMb);
		playbackTick = animate ? 0 : currentResult.getMakespanTicks();
		currentFrame = buildFrame(playbackTick);
		updateDisplays();
		if (animate)
		{
			statusPill.setText("RUNNING");
			statusPill.setForeground(BLUE.darker());
			statusPill.setBackground(new Color(232, 239, 255));
			statusLabel.setText("Scheduling policy is being animated from queued jobs to completion.");
			playbackTimer.start();
		}
		else
		{
			statusPill.setText("READY");
			statusPill.setForeground(GREEN.darker());
			statusPill.setBackground(new Color(228, 245, 236));
			statusLabel.setText("Preview is ready. Press Run schedule to animate the selected policy.");
		}
	}

	private void stopPlayback()
	{
		playbackTimer.stop();
		if (currentResult != null)
		{
			playbackTick = 0;
			currentFrame = buildFrame(playbackTick);
			updateDisplays();
		}
		statusPill.setText("READY");
		statusPill.setForeground(GREEN.darker());
		statusPill.setBackground(new Color(228, 245, 236));
		statusLabel.setText("Playback reset. No simulator state was changed.");
	}

	private void advancePlayback()
	{
		if (currentResult == null)
		{
			playbackTimer.stop();
			return;
		}
		int step = Math.max(1, currentResult.getMakespanTicks() / 150);
		playbackTick += step;
		if (playbackTick >= currentResult.getMakespanTicks())
		{
			playbackTick = currentResult.getMakespanTicks();
			playbackTimer.stop();
			statusPill.setText("COMPLETE");
			statusPill.setForeground(GREEN.darker());
			statusPill.setBackground(new Color(228, 245, 236));
			statusLabel.setText("Scheduler what-if run finished. Recommendation scoring was not touched.");
		}
		currentFrame = buildFrame(playbackTick);
		updateDisplays();
	}

	private PlaybackFrame buildFrame(int tick)
	{
		int nodeCount = currentResult == null
				? Math.max(1, ((Number)nodesSpinner.getValue()).intValue())
				: currentResult.getNodes().size();
		PlaybackFrame frame = new PlaybackFrame(nodeCount);
		if (currentResult == null)
		{
			return frame;
		}
		frame.tick = Math.max(0, tick);
		frame.totalJobs = currentResult.getTotalJobs();
		for (SchedulerLabEngine.TimelineEvent event : currentResult.getEvents())
		{
			if (event.getTick() > tick)
			{
				continue;
			}
			int nodeSlot = Math.max(0, event.getNodeIndex() - 1);
			if (nodeSlot >= nodeCount)
			{
				nodeSlot = 0;
			}
			if (event.getType() == SchedulerLabEngine.EventType.ASSIGNED)
			{
				frame.assigned++;
				frame.nodeAssigned[nodeSlot]++;
			}
			else if (event.getType() == SchedulerLabEngine.EventType.STARTED)
			{
				frame.started++;
				frame.nodeStarted[nodeSlot]++;
			}
			else if (event.getType() == SchedulerLabEngine.EventType.COMPLETED)
			{
				frame.completed++;
				frame.nodeCompleted[nodeSlot]++;
			}
			else if (event.getType() == SchedulerLabEngine.EventType.FAILED)
			{
				frame.failed++;
				frame.nodeFailed[nodeSlot]++;
			}
			else if (event.getType() == SchedulerLabEngine.EventType.RETRIED)
			{
				frame.retried++;
				frame.nodeRetried[nodeSlot]++;
			}
			else if (event.getType() == SchedulerLabEngine.EventType.STOLEN)
			{
				frame.stolen++;
				frame.nodeStolen[nodeSlot]++;
			}
		}
		frame.queued = Math.max(0, frame.assigned - frame.started);
		for (int index = 0; index < nodeCount; index++)
		{
			frame.nodeRunning[index] = Math.max(
					0,
					frame.nodeStarted[index]
							- frame.nodeCompleted[index]
							- frame.nodeFailed[index]);
			frame.running += frame.nodeRunning[index];
		}
		frame.waiting = Math.max(
				0,
				frame.totalJobs - frame.assigned);
		return frame;
	}

	private void updateDisplays()
	{
		if (currentResult == null)
		{
			return;
		}
		policyMetric.setText(currentResult.getPolicy().getLabel());
		jobsMetric.setText(currentFrame.completed + " / "
				+ currentResult.getTotalJobs());
		makespanMetric.setText(currentResult.getMakespanTicks() + " ticks");
		utilizationMetric.setText(
				DECIMAL.format(currentResult.utilization() * 100.0) + "%");
		retryMetric.setText(currentFrame.retried + " retry events");

		queueCanvas.setResult(currentResult, currentFrame);
		contractCanvas.setResult(currentResult);
		comparisonCanvas.setResults(
				currentResult,
				comparisonResults,
				comparisonSampled,
				comparisonJobCount);
		nodeGridCanvas.setResult(currentResult, currentFrame);
		timelineCanvas.setResult(currentResult, currentFrame);
		nodeTableModel.setResult(currentResult, currentFrame);
		updateLog();
	}

	private List<SchedulerLabEngine.Result> buildPolicyComparisons(
			List<NodeCapacityProfile> profiles,
			SchedulerLabEngine.WorkloadTemplate template,
			List<SchedulerLabEngine.WorkloadUnit> workload,
			int heapMb)
	{
		ArrayList<SchedulerLabEngine.Result> results =
				new ArrayList<SchedulerLabEngine.Result>();
		List<SchedulerLabEngine.WorkloadUnit> comparisonWorkload = workload;
		comparisonSampled = false;
		comparisonJobCount = workload == null ? 0 : workload.size();
		if (workload != null && workload.size() > 1500)
		{
			comparisonWorkload =
					new ArrayList<SchedulerLabEngine.WorkloadUnit>(
							workload.subList(0, 1500));
			comparisonSampled = true;
			comparisonJobCount = comparisonWorkload.size();
		}
		for (SchedulerLabEngine.SchedulingPolicy policy :
				SchedulerLabEngine.SchedulingPolicy.values())
		{
			results.add(SchedulerLabEngine.simulate(
					profiles,
					policy,
					template,
					comparisonWorkload,
					heapMb));
		}
		return results;
	}

	private void updateLog()
	{
		StringBuilder builder = new StringBuilder();
		if (currentResult != null)
		{
			builder.append("SCHEDULER LAB RUN\n");
			builder.append("Policy: ")
					.append(currentResult.getPolicy().getLabel())
					.append(" | Workload: ")
					.append(currentResult.getTemplate().getLabel())
					.append(" | Nodes: ")
					.append(currentResult.getNodes().size())
					.append(" | Jobs: ")
					.append(currentResult.getTotalJobs())
					.append('\n');
			builder.append("Outcome: ")
					.append(currentResult.getCompletedJobs())
					.append(" completed, ")
					.append(currentResult.getFailedJobs())
					.append(" failed, ")
					.append(currentResult.getRetriedJobs())
					.append(" retried, makespan ")
					.append(currentResult.getMakespanTicks())
					.append(" ticks, utilization ")
					.append(DECIMAL.format(
							currentResult.utilization() * 100.0))
					.append("%\n");
			builder.append("Contract: ")
					.append(currentResult.getPolicy().getDescription())
					.append("\n\n");
			for (String line : currentResult.getLogLines())
			{
				builder.append(line).append('\n');
			}
			int shown = 0;
			for (SchedulerLabEngine.TimelineEvent event : currentResult.getEvents())
			{
				if (event.getTick() > playbackTick)
				{
					continue;
				}
				if (event.getType() != SchedulerLabEngine.EventType.STARTED
						&& event.getType()
								!= SchedulerLabEngine.EventType.COMPLETED
						&& event.getType()
								!= SchedulerLabEngine.EventType.STOLEN)
				{
					continue;
				}
				if (shown++ > 120)
				{
					break;
				}
				builder.append(String.format(
						Locale.US,
						"t=%04d  node %d  %-9s  %-18s  %s%n",
						Integer.valueOf(event.getTick()),
						Integer.valueOf(event.getNodeIndex()),
						event.getType().toString().toLowerCase(Locale.US),
						event.getJobId(),
						event.getDetail()));
			}
		}
		eventLog.setText(builder.toString());
		eventLog.setCaretPosition(Math.max(0, eventLog.getDocument().getLength()));
	}

	private void configureNodeTable()
	{
		nodeTable.setRowHeight(28);
		nodeTable.setFillsViewportHeight(true);
		nodeTable.setGridColor(new Color(228, 234, 242));
		nodeTable.setShowVerticalLines(false);
		nodeTable.setFont(font(Font.PLAIN, 11));
		nodeTable.getTableHeader().setFont(font(Font.BOLD, 10));
		nodeTable.getTableHeader().setForeground(MUTED);
		nodeTable.setDefaultRenderer(Object.class, new NodeCellRenderer());
	}

	private static JPanel createAttributionBlock()
	{
		JPanel block = new JPanel();
		block.setOpaque(false);
		block.setLayout(new BoxLayout(block, BoxLayout.Y_AXIS));
		block.setAlignmentX(Component.LEFT_ALIGNMENT);
		block.setBorder(new EmptyBorder(5, 1, 0, 0));
		block.add(attributionLine("Faculty Advisor", "Dr. Abdolreza Abhari"));
		block.add(Box.createRigidArea(new Dimension(0, 3)));
		block.add(attributionLine("Developer", "Mohsen Alavi"));
		return block;
	}

	private static JPanel attributionLine(String role, String name)
	{
		JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 7, 0));
		row.setOpaque(false);
		JLabel roleLabel = new JLabel(role.toUpperCase(Locale.US));
		roleLabel.setFont(font(Font.BOLD, 9));
		roleLabel.setForeground(MUTED);
		JLabel nameLabel = new JLabel(name);
		nameLabel.setFont(font(Font.BOLD, 12));
		nameLabel.setForeground(TEXT);
		row.add(roleLabel);
		row.add(nameLabel);
		return row;
	}

	private static JPanel surface(BorderLayout layout)
	{
		JPanel panel = new JPanel(layout);
		panel.setOpaque(true);
		panel.setBackground(SURFACE);
		panel.setBorder(BorderFactory.createLineBorder(BORDER));
		return panel;
	}

	private static JPanel section(String title, String subtitle)
	{
		JPanel panel = surface(new BorderLayout(0, 9));
		panel.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(BORDER),
				new EmptyBorder(12, 12, 12, 12)));
		JPanel heading = new JPanel(new BorderLayout());
		heading.setOpaque(false);
		JLabel titleLabel = new JLabel(title);
		titleLabel.setFont(font(Font.BOLD, 16));
		titleLabel.setForeground(TEXT);
		JLabel subtitleLabel = new JLabel(subtitle);
		subtitleLabel.setFont(font(Font.PLAIN, 11));
		subtitleLabel.setForeground(MUTED);
		heading.add(titleLabel, BorderLayout.WEST);
		heading.add(subtitleLabel, BorderLayout.SOUTH);
		panel.add(heading, BorderLayout.NORTH);
		return panel;
	}

	private static JComponent control(String label, JComponent component)
	{
		JPanel panel = new JPanel(new BorderLayout(0, 4));
		panel.setOpaque(false);
		JLabel title = new JLabel(label);
		title.setFont(font(Font.BOLD, 10));
		title.setForeground(MUTED);
		component.setFont(font(Font.BOLD, 12));
		panel.add(title, BorderLayout.NORTH);
		panel.add(component, BorderLayout.CENTER);
		return panel;
	}

	private static JPanel metricCard(
			String label,
			JLabel value,
			Color accent)
	{
		JPanel card = new JPanel(new BorderLayout(0, 2));
		card.setOpaque(true);
		card.setBackground(SURFACE_ALT);
		card.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createMatteBorder(0, 4, 0, 0, accent),
				new EmptyBorder(8, 10, 8, 10)));
		JLabel labelView = new JLabel(label.toUpperCase(Locale.US));
		labelView.setFont(font(Font.BOLD, 9));
		labelView.setForeground(MUTED);
		card.add(labelView, BorderLayout.NORTH);
		card.add(value, BorderLayout.CENTER);
		return card;
	}

	private static JLabel metricValue(String value)
	{
		JLabel label = new JLabel(value);
		label.setFont(font(Font.BOLD, 16));
		label.setForeground(TEXT);
		return label;
	}

	private static JButton button(String text, Color background, Color foreground)
	{
		JButton button = new JButton(text);
		button.setFocusPainted(false);
		button.setOpaque(true);
		button.setContentAreaFilled(true);
		button.setBorderPainted(false);
		button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		button.setBackground(background);
		button.setForeground(foreground);
		button.setFont(font(Font.BOLD, 11));
		button.setBorder(new EmptyBorder(8, 12, 8, 12));
		return button;
	}

	private static Font font(int style, int size)
	{
		return new Font("SansSerif", style, size);
	}

	private static Color themed(Color day, Color night)
	{
		return StudioNightTheme.isNightMode() ? night : day;
	}

	private static Color accentBlend(Color accent, double amountToSurface)
	{
		return blend(accent,
				StudioNightTheme.isNightMode()
						? StudioNightTheme.NIGHT_SURFACE_ALT : Color.WHITE,
				amountToSurface);
	}

	private static Color blend(Color first, Color second, double amount)
	{
		double clamped = Math.max(0.0, Math.min(1.0, amount));
		int red = (int)Math.round(first.getRed()
				+ (second.getRed() - first.getRed()) * clamped);
		int green = (int)Math.round(first.getGreen()
				+ (second.getGreen() - first.getGreen()) * clamped);
		int blue = (int)Math.round(first.getBlue()
				+ (second.getBlue() - first.getBlue()) * clamped);
		return new Color(red, green, blue);
	}

	private static final class PlaybackFrame
	{
		int tick;
		int totalJobs;
		int assigned;
		int started;
		int completed;
		int failed;
		int retried;
		int stolen;
		int waiting;
		int queued;
		int running;
		final int[] nodeAssigned;
		final int[] nodeStarted;
		final int[] nodeCompleted;
		final int[] nodeFailed;
		final int[] nodeRetried;
		final int[] nodeStolen;
		final int[] nodeRunning;

		PlaybackFrame(int nodeCount)
		{
			int safeCount = Math.max(1, nodeCount);
			nodeAssigned = new int[safeCount];
			nodeStarted = new int[safeCount];
			nodeCompleted = new int[safeCount];
			nodeFailed = new int[safeCount];
			nodeRetried = new int[safeCount];
			nodeStolen = new int[safeCount];
			nodeRunning = new int[safeCount];
		}
	}

	private final class NodeTableModel extends AbstractTableModel
	{
		private final String[] columns = {
				"Node", "Role", "Weight", "Assigned", "Complete",
				"Running", "Retry", "Fail", "Busy", "Queue peak"
		};
		private SchedulerLabEngine.Result result;
		private PlaybackFrame frame = new PlaybackFrame(1);

		void setResult(
				SchedulerLabEngine.Result result,
				PlaybackFrame frame)
		{
			this.result = result;
			this.frame = frame == null ? new PlaybackFrame(1) : frame;
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
			int slot = Math.min(row, frame.nodeAssigned.length - 1);
			switch (column)
			{
				case 0:
					return Integer.valueOf(profile.getNodeIndex());
				case 1:
					return profile.getRoleLabel();
				case 2:
					return DECIMAL.format(
							NodeCapacityScheduler.schedulingWeight(profile));
				case 3:
					return Integer.valueOf(frame.nodeAssigned[slot]);
				case 4:
					return Integer.valueOf(frame.nodeCompleted[slot]);
				case 5:
					return Integer.valueOf(frame.nodeRunning[slot]);
				case 6:
					return Integer.valueOf(frame.nodeRetried[slot]);
				case 7:
					return Integer.valueOf(frame.nodeFailed[slot]);
				case 8:
					return summary.getBusyTicks() + " ticks";
				case 9:
					return Integer.valueOf(summary.getQueuePeak());
				default:
					return "";
			}
		}
	}

	private static final class NodeCellRenderer
			extends DefaultTableCellRenderer
	{
		public Component getTableCellRendererComponent(
				JTable table,
				Object value,
				boolean selected,
				boolean focused,
				int row,
				int column)
		{
			Component component = super.getTableCellRendererComponent(
					table,
					value,
					selected,
					focused,
					row,
					column);
			component.setFont(font(column == 0 || column == 1
					? Font.BOLD : Font.PLAIN, 11));
			component.setForeground(TEXT);
			component.setBackground(row % 2 == 0
					? SURFACE : SURFACE_ALT);
			if (selected)
			{
				component.setBackground(themed(
						new Color(232, 239, 255), StudioNightTheme.NIGHT_PANEL_INK));
			}
			setBorder(new EmptyBorder(0, 8, 0, 8));
			return component;
		}
	}

	private final class QueueCanvas extends JPanel
	{
		private SchedulerLabEngine.Result result;
		private PlaybackFrame frame = new PlaybackFrame(1);

		QueueCanvas()
		{
			setOpaque(false);
		}

		void setResult(
				SchedulerLabEngine.Result result,
				PlaybackFrame frame)
		{
			this.result = result;
			this.frame = frame == null ? new PlaybackFrame(1) : frame;
			repaint();
		}

		protected void paintComponent(Graphics graphics)
		{
			super.paintComponent(graphics);
			Graphics2D g2 = prepare(graphics);
			int width = getWidth();
			int height = getHeight();
			paintRounded(g2, 0, 0, width - 1, height - 1,
					SURFACE, BORDER);
			if (result == null)
			{
				g2.dispose();
				return;
			}
			int pad = 18;
			g2.setFont(font(Font.BOLD, 11));
			g2.setColor(BLUE.darker());
			g2.drawString("SCHEDULABLE WORK UNITS", pad, 24);
			g2.setFont(font(Font.PLAIN, 10));
			g2.setColor(MUTED);
			g2.drawString("The lab converts the run into explicit jobs before a policy places them on nodes.",
					pad + 165, 24);

			EnumMap<SchedulerLabEngine.WorkloadUnitType, Integer> counts =
					workloadCounts(result.getJobs());
			int chipY = 42;
			int chipW = Math.max(116,
					(width - pad * 2 - 5 * 8) / 6);
			int index = 0;
			for (SchedulerLabEngine.WorkloadUnitType type :
					SchedulerLabEngine.WorkloadUnitType.values())
			{
				int x = pad + index * (chipW + 8);
				drawTypeChip(g2, x, chipY, chipW, 56,
						type,
						counts.containsKey(type)
								? counts.get(type).intValue() : 0);
				index++;
			}

			int flowY = 122;
			int stageW = Math.max(112,
					(width - pad * 2 - 3 * 12) / 4);
			drawStage(g2, pad, flowY, stageW, "Waiting",
					frame.waiting,
					result.getTotalJobs(),
					BLUE);
			drawConnector(g2, pad + stageW, flowY + 32,
					pad + stageW + 12, flowY + 32);
			drawStage(g2, pad + stageW + 12, flowY, stageW, "Queued",
					frame.queued,
					result.getTotalJobs(),
					PURPLE);
			drawConnector(g2, pad + stageW * 2 + 12, flowY + 32,
					pad + stageW * 2 + 24, flowY + 32);
			drawStage(g2, pad + stageW * 2 + 24, flowY, stageW, "Running",
					frame.running,
					result.getTotalJobs(),
					AMBER);
			drawConnector(g2, pad + stageW * 3 + 24, flowY + 32,
					pad + stageW * 3 + 36, flowY + 32);
			drawStage(g2, pad + stageW * 3 + 36, flowY, stageW, "Finished",
					frame.completed,
					result.getTotalJobs(),
					GREEN);
			String detail = result.getTemplate().getLabel() + " | "
					+ result.getPolicy().getLabel() + " | tick "
					+ frame.tick + " of " + result.getMakespanTicks()
					+ " | failed " + frame.failed + " | retries "
					+ frame.retried;
			g2.setFont(font(Font.BOLD, 11));
			g2.setColor(MUTED);
			g2.drawString(detail, pad, height - 16);
			g2.dispose();
		}

		private void drawTypeChip(
				Graphics2D g2,
				int x,
				int y,
				int width,
				int height,
				SchedulerLabEngine.WorkloadUnitType type,
				int count)
		{
			Color accent = workloadColor(type);
			paintRounded(g2, x, y, width, height,
					SURFACE, BORDER);
			g2.setColor(accentBlend(accent, 0.78));
			g2.fillRoundRect(x + 8, y + 9, 28, 28, 12, 12);
			g2.setColor(accent);
			g2.fillOval(x + 18, y + 19, 8, 8);
			g2.setFont(font(Font.BOLD, 10));
			g2.setColor(TEXT);
			g2.drawString(type.getLabel(), x + 44, y + 19);
			g2.setFont(font(Font.BOLD, 16));
			g2.drawString(Integer.toString(count), x + 44, y + 40);
			g2.setFont(font(Font.PLAIN, 9));
			g2.setColor(MUTED);
			String description = type.getDescription();
			if (description.length() > 22)
			{
				description = description.substring(0, 22) + "...";
			}
			g2.drawString(description, x + 72, y + 41);
		}
	}

	private final class SchedulerContractCanvas extends JPanel
	{
		private SchedulerLabEngine.Result result;

		SchedulerContractCanvas()
		{
			setOpaque(false);
		}

		void setResult(SchedulerLabEngine.Result result)
		{
			this.result = result;
			repaint();
		}

		protected void paintComponent(Graphics graphics)
		{
			super.paintComponent(graphics);
			Graphics2D g2 = prepare(graphics);
			int width = getWidth();
			int height = getHeight();
			paintRounded(g2, 0, 0, width - 1, height - 1,
					SURFACE, BORDER);
			SchedulerLabEngine.SchedulingPolicy policy =
					result == null
							? (SchedulerLabEngine.SchedulingPolicy)
									policyBox.getSelectedItem()
							: result.getPolicy();
			if (policy == null)
			{
				g2.dispose();
				return;
			}
			g2.setFont(font(Font.BOLD, 11));
			g2.setColor(TEXT);
			g2.drawString(policy.getLabel(), 16, 22);
			g2.setFont(font(Font.PLAIN, 10));
			g2.setColor(MUTED);
			g2.drawString(policy.getDescription(), 16, 40);
			String[] features = {
					"capacity",
					"queue",
					"memory",
					"deadline",
					"stealing",
					"retry"
			};
			int chipW = Math.max(74, (width - 32 - 5 * 7) / 6);
			for (int i = 0; i < features.length; i++)
			{
				boolean active = policyUses(policy, features[i]);
				int x = 16 + i * (chipW + 7);
				int y = height - 32;
				Color accent = active
						? GREEN
						: themed(new Color(174, 184, 198), MUTED);
				g2.setColor(active
						? themed(new Color(231, 246, 237),
								accentBlend(GREEN, 0.78))
						: themed(new Color(240, 243, 247),
								StudioNightTheme.NIGHT_PANEL_INK));
				g2.fillRoundRect(x, y, chipW, 22, 8, 8);
				g2.setColor(active
						? themed(new Color(155, 210, 177), GREEN)
						: BORDER);
				g2.drawRoundRect(x, y, chipW, 22, 8, 8);
				g2.setColor(active ? GREEN : accent);
				g2.setFont(font(Font.BOLD, 9));
				g2.drawString(features[i].toUpperCase(Locale.US),
						x + 9, y + 15);
			}
			g2.dispose();
		}
	}

	private final class PolicyComparisonCanvas extends JPanel
	{
		private SchedulerLabEngine.Result selected;
		private List<SchedulerLabEngine.Result> results =
				new ArrayList<SchedulerLabEngine.Result>();
		private boolean sampled;
		private int sampledJobs;

		PolicyComparisonCanvas()
		{
			setOpaque(false);
		}

		void setResults(
				SchedulerLabEngine.Result selected,
				List<SchedulerLabEngine.Result> results,
				boolean sampled,
				int sampledJobs)
		{
			this.selected = selected;
			this.results = results == null
					? new ArrayList<SchedulerLabEngine.Result>() : results;
			this.sampled = sampled;
			this.sampledJobs = sampledJobs;
			repaint();
		}

		protected void paintComponent(Graphics graphics)
		{
			super.paintComponent(graphics);
			Graphics2D g2 = prepare(graphics);
			int width = getWidth();
			int height = getHeight();
			paintRounded(g2, 0, 0, width - 1, height - 1,
					SURFACE, BORDER);
			if (selected == null || results.isEmpty())
			{
				g2.dispose();
				return;
			}
			int pad = 16;
			int maxTick = 1;
			for (SchedulerLabEngine.Result result : results)
			{
				maxTick = Math.max(maxTick, result.getMakespanTicks());
			}
			g2.setFont(font(Font.BOLD, 11));
			g2.setColor(BLUE.darker());
			g2.drawString("POLICY RACE", pad, 20);
			g2.setFont(font(Font.PLAIN, 10));
			g2.setColor(MUTED);
			String note = sampled
					? "comparison uses first " + sampledJobs
							+ " jobs to keep the UI responsive"
					: "all jobs compared with the same node capacity contract";
			g2.drawString(note, pad + 90, 20);

			int rows = Math.min(results.size(), 11);
			int rowH = Math.max(16, (height - 34) / rows);
			for (int row = 0; row < rows; row++)
			{
				SchedulerLabEngine.Result result = results.get(row);
				int y = 32 + row * rowH;
				boolean isSelected =
						result.getPolicy() == selected.getPolicy();
				Color accent = isSelected ? BLUE : new Color(142, 156, 178);
				g2.setFont(font(isSelected ? Font.BOLD : Font.PLAIN, 10));
				g2.setColor(isSelected ? TEXT : MUTED);
				String label = result.getPolicy().getLabel();
				if (label.length() > 22)
				{
					label = label.substring(0, 21) + "...";
				}
				g2.drawString(label, pad, y + 11);
				int barX = pad + 150;
				int barW = width - barX - 96;
				double normalized =
						result.getMakespanTicks() / (double)maxTick;
				g2.setColor(themed(new Color(226, 233, 242),
						StudioNightTheme.NIGHT_BACKGROUND_BOTTOM));
				g2.fillRoundRect(barX, y + 3, barW, 8, 8, 8);
				g2.setColor(accent);
				g2.fillRoundRect(barX, y + 3,
						Math.max(3, (int)Math.round(barW * normalized)),
						8, 8, 8);
				g2.setFont(font(Font.BOLD, 9));
				g2.setColor(result.getFailedJobs() > 0 ? RED : MUTED);
				g2.drawString(result.getMakespanTicks() + "t"
						+ "  " + Math.round(result.utilization() * 100.0)
						+ "%  r" + result.getRetriedJobs(),
						width - 86, y + 11);
			}
			g2.dispose();
		}
	}

	private final class NodeGridCanvas extends JPanel
	{
		private SchedulerLabEngine.Result result;
		private PlaybackFrame frame = new PlaybackFrame(1);

		NodeGridCanvas()
		{
			setOpaque(false);
		}

		void setResult(
				SchedulerLabEngine.Result result,
				PlaybackFrame frame)
		{
			this.result = result;
			this.frame = frame == null ? new PlaybackFrame(1) : frame;
			repaint();
		}

		protected void paintComponent(Graphics graphics)
		{
			super.paintComponent(graphics);
			Graphics2D g2 = prepare(graphics);
			int width = getWidth();
			int height = getHeight();
			paintRounded(g2, 0, 0, width - 1, height - 1,
					SURFACE, BORDER);
			if (result == null)
			{
				g2.dispose();
				return;
			}
			int nodes = Math.max(1, result.getNodes().size());
			int columns = Math.min(4, Math.max(1,
					(int)Math.ceil(Math.sqrt(nodes))));
			int rows = (int)Math.ceil(nodes / (double)columns);
			int gap = 12;
			int pad = 18;
			int cardW = (width - pad * 2 - gap * (columns - 1)) / columns;
			int cardH = Math.max(74,
					(height - pad * 2 - gap * (rows - 1)) / rows);
			for (int index = 0; index < nodes; index++)
			{
				int col = index % columns;
				int row = index / columns;
				int x = pad + col * (cardW + gap);
				int y = pad + row * (cardH + gap);
				paintNodeCard(g2, x, y, cardW, cardH, index);
			}
			g2.dispose();
		}

		private void paintNodeCard(
				Graphics2D g2,
				int x,
				int y,
				int width,
				int height,
				int index)
		{
			SchedulerLabEngine.NodeSummary summary =
					result.getNodes().get(index);
			NodeCapacityProfile profile = summary.getProfile();
			Color accent = roleColor(profile.getRole());
			paintRounded(g2, x, y, width, height, SURFACE, BORDER);
			g2.setColor(accent);
			g2.fillRoundRect(x, y, 5, height, 5, 5);
			g2.setFont(font(Font.BOLD, 12));
			g2.setColor(TEXT);
			g2.drawString("Node " + profile.getNodeIndex(), x + 14, y + 22);
			g2.setFont(font(Font.BOLD, 10));
			g2.setColor(accent.darker());
			g2.drawString(profile.shortLabel(), x + 14, y + 39);
			int slot = Math.min(index, frame.nodeAssigned.length - 1);
			String detail = frame.nodeCompleted[slot] + " done | "
					+ frame.nodeRunning[slot] + " running | "
					+ frame.nodeRetried[slot] + " retry";
			g2.setFont(font(Font.PLAIN, 10));
			g2.setColor(MUTED);
			g2.drawString(detail, x + 14, y + 56);
			double progress = summary.getAssignedJobs() <= 0
					? 0.0
					: frame.nodeCompleted[slot]
							/ (double)Math.max(1, summary.getAssignedJobs());
			paintProgress(g2, x + 14, y + height - 18,
					width - 28, 6, progress, accent);
		}
	}

	private final class TimelineCanvas extends JPanel
	{
		private SchedulerLabEngine.Result result;
		private PlaybackFrame frame = new PlaybackFrame(1);

		TimelineCanvas()
		{
			setOpaque(false);
		}

		void setResult(
				SchedulerLabEngine.Result result,
				PlaybackFrame frame)
		{
			this.result = result;
			this.frame = frame == null ? new PlaybackFrame(1) : frame;
			repaint();
		}

		protected void paintComponent(Graphics graphics)
		{
			super.paintComponent(graphics);
			Graphics2D g2 = prepare(graphics);
			int width = getWidth();
			int height = getHeight();
			paintRounded(g2, 0, 0, width - 1, height - 1,
					SURFACE, BORDER);
			if (result == null)
			{
				g2.dispose();
				return;
			}
			int pad = 28;
			int top = 42;
			int bottom = height - 58;
			int laneHeight = Math.max(22,
					(bottom - top) / Math.max(1, result.getNodes().size()));
			g2.setFont(font(Font.BOLD, 11));
			g2.setColor(TEXT);
			g2.drawString("Policy: " + result.getPolicy().getLabel(),
					pad, 24);
			g2.setColor(MUTED);
			g2.setFont(font(Font.PLAIN, 10));
			g2.drawString(result.getPolicy().getDescription(), pad + 170, 24);
			for (int index = 0; index < result.getNodes().size(); index++)
			{
				SchedulerLabEngine.NodeSummary node =
						result.getNodes().get(index);
				int y = top + index * laneHeight;
				Color accent = roleColor(node.getProfile().getRole());
				g2.setColor(themed(new Color(226, 233, 242),
						StudioNightTheme.NIGHT_BACKGROUND_BOTTOM));
				g2.drawLine(pad + 90, y + 10, width - pad, y + 10);
				g2.setColor(TEXT);
				g2.setFont(font(Font.BOLD, 10));
				g2.drawString("Node " + node.getNodeIndex(), pad, y + 14);
				g2.setFont(font(Font.PLAIN, 9));
				g2.setColor(MUTED);
				g2.drawString(node.getCompletedJobs() + " done | "
						+ node.getAssignedJobs() + " assigned",
						pad, y + 27);
			}
			drawTimelineSegments(g2, pad + 90, width - pad,
					top, laneHeight);
			double play = result.getMakespanTicks() <= 0
					? 0.0 : frame.tick / (double)result.getMakespanTicks();
			int markerX = pad + 90
					+ (int)Math.round((width - pad * 2 - 90) * play);
			g2.setColor(new Color(20, 150, 105, 190));
			g2.setStroke(new BasicStroke(2f));
			g2.drawLine(markerX, top - 8, markerX, bottom + 8);
			g2.setFont(font(Font.BOLD, 10));
			g2.drawString("t=" + frame.tick, markerX + 5, bottom + 24);
			drawTimelineLegend(g2, pad, height - 28);
			g2.dispose();
		}

		private void drawTimelineSegments(
				Graphics2D g2,
				int lineStart,
				int lineEnd,
				int top,
				int laneHeight)
		{
			Map<String, SchedulerLabEngine.TimelineEvent> starts =
					new HashMap<String, SchedulerLabEngine.TimelineEvent>();
			int segmentCount = 0;
			int lineWidth = Math.max(1, lineEnd - lineStart);
			for (SchedulerLabEngine.TimelineEvent event : result.getEvents())
			{
				if (event.getTick() > frame.tick)
				{
					break;
				}
				String key = event.getNodeIndex() + "|" + event.getJobId();
				if (event.getType() == SchedulerLabEngine.EventType.STARTED)
				{
					starts.put(key, event);
				}
				else if (event.getType()
							== SchedulerLabEngine.EventType.COMPLETED
						|| event.getType()
							== SchedulerLabEngine.EventType.FAILED)
				{
					SchedulerLabEngine.TimelineEvent start = starts.remove(key);
					if (start == null)
					{
						continue;
					}
					drawSegment(g2, start, event, lineStart,
							lineWidth, top, laneHeight);
					if (++segmentCount > 700)
					{
						break;
					}
				}
			}
			for (SchedulerLabEngine.TimelineEvent start : starts.values())
			{
				drawSegment(g2, start, null, lineStart, lineWidth,
						top, laneHeight);
				if (++segmentCount > 700)
				{
					break;
				}
			}
		}

		private void drawSegment(
				Graphics2D g2,
				SchedulerLabEngine.TimelineEvent start,
				SchedulerLabEngine.TimelineEvent end,
				int lineStart,
				int lineWidth,
				int top,
				int laneHeight)
		{
			int nodeSlot = Math.max(0, start.getNodeIndex() - 1);
			if (nodeSlot >= Math.max(1, result.getNodes().size()))
			{
				return;
			}
			int startTick = start.getTick();
			int endTick = end == null ? frame.tick : end.getTick();
			if (endTick < startTick)
			{
				endTick = startTick;
			}
			double total = Math.max(1.0, result.getMakespanTicks());
			int x = lineStart + (int)Math.round(lineWidth * startTick / total);
			int x2 = lineStart + (int)Math.round(lineWidth * endTick / total);
			int y = top + nodeSlot * laneHeight + 3;
			Color accent = workloadColor(start.getJobType());
			if (end != null
					&& end.getType() == SchedulerLabEngine.EventType.FAILED)
			{
				accent = RED;
			}
			g2.setColor(accentBlend(accent, 0.22));
			g2.fillRoundRect(x, y, Math.max(5, x2 - x), 14, 7, 7);
			g2.setColor(accent.darker());
			g2.drawRoundRect(x, y, Math.max(5, x2 - x), 14, 7, 7);
		}

		private void drawTimelineLegend(Graphics2D g2, int x, int y)
		{
			int offset = 0;
			for (SchedulerLabEngine.WorkloadUnitType type :
					SchedulerLabEngine.WorkloadUnitType.values())
			{
				Color color = workloadColor(type);
				g2.setColor(color);
				g2.fillRoundRect(x + offset, y - 9, 18, 8, 8, 8);
				g2.setColor(MUTED);
				g2.setFont(font(Font.PLAIN, 9));
				g2.drawString(type.getLabel(), x + offset + 23, y - 2);
				offset += 118;
				if (x + offset > getWidth() - 120)
				{
					break;
				}
			}
		}
	}

	private static final class HeaderSignalPanel extends JPanel
	{
		private final Timer timer;
		private float phase;

		HeaderSignalPanel()
		{
			setOpaque(false);
			setPreferredSize(new Dimension(520, 74));
			timer = new Timer(16, new ActionListener()
			{
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
			addHierarchyListener(new HierarchyListener()
			{
				public void hierarchyChanged(HierarchyEvent event)
				{
					if ((event.getChangeFlags()
							& HierarchyEvent.SHOWING_CHANGED) != 0)
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
			Graphics2D g2 = prepare(graphics);
			int width = getWidth();
			int height = getHeight();
			int[] xs = {
					20,
					width / 4,
					width / 2,
					width * 3 / 4,
					width - 20
			};
			int[] ys = {
					height / 2 + 8,
					height / 2 - 7,
					height / 2 + 2,
					height / 2 - 10,
					height / 2 + 7
			};
			g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND,
					BasicStroke.JOIN_ROUND));
			g2.setColor(new Color(194, 213, 240, 150));
			for (int i = 0; i < xs.length - 1; i++)
			{
				g2.drawLine(xs[i], ys[i], xs[i + 1], ys[i + 1]);
			}
			for (int i = 0; i < xs.length; i++)
			{
				float pulse = (float)Math.abs(Math.sin(
						(phase + i * 0.19f) * Math.PI * 2.0));
				int radius = 8 + Math.round(pulse * 3f);
				Color color = i % 2 == 0 ? BLUE : GREEN;
				g2.setColor(new Color(color.getRed(), color.getGreen(),
						color.getBlue(), 60));
				g2.fillOval(xs[i] - radius, ys[i] - radius,
						radius * 2, radius * 2);
				g2.setColor(color);
				g2.drawOval(xs[i] - 6, ys[i] - 6, 12, 12);
				g2.setColor(StudioNightTheme.isNightMode()
						? StudioNightTheme.NIGHT_TEXT : Color.WHITE);
				g2.fillOval(xs[i] - 3, ys[i] - 3, 6, 6);
			}
			g2.dispose();
		}
	}

	private static Graphics2D prepare(Graphics graphics)
	{
		Graphics2D g2 = (Graphics2D)graphics.create();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
				RenderingHints.VALUE_ANTIALIAS_ON);
		g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
				RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		return g2;
	}

	private static void paintRounded(
			Graphics2D g2,
			int x,
			int y,
			int width,
			int height,
			Color fill,
			Color stroke)
	{
		g2.setColor(fill);
		g2.fillRoundRect(x, y, width, height, 10, 10);
		g2.setColor(stroke);
		g2.drawRoundRect(x, y, width, height, 10, 10);
	}

	private static void drawStage(
			Graphics2D g2,
			int x,
			int y,
			int width,
			String title,
			int value,
			int total,
			Color accent)
	{
		paintRounded(g2, x, y, width, 64, SURFACE, BORDER);
		g2.setColor(accent);
		g2.fillRoundRect(x, y, 5, 64, 5, 5);
		g2.setFont(font(Font.BOLD, 11));
		g2.setColor(TEXT);
		g2.drawString(title, x + 14, y + 22);
		g2.setFont(font(Font.BOLD, 18));
		g2.drawString(Integer.toString(Math.max(0, value)), x + 14, y + 47);
		paintProgress(g2, x + 78, y + 39, width - 94, 6,
				total <= 0 ? 0.0 : value / (double)total, accent);
	}

	private static void drawConnector(
			Graphics2D g2,
			int x1,
			int y1,
			int x2,
			int y2)
	{
		g2.setColor(new Color(195, 211, 232));
		g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND,
				BasicStroke.JOIN_ROUND));
		g2.drawLine(x1 + 10, y1, x2 - 10, y2);
		g2.fillOval((x1 + x2) / 2 - 3, y1 - 3, 6, 6);
	}

	private static void paintProgress(
			Graphics2D g2,
			int x,
			int y,
			int width,
			int height,
			double progress,
			Color accent)
	{
		g2.setColor(themed(new Color(228, 234, 242),
				StudioNightTheme.NIGHT_BACKGROUND_BOTTOM));
		g2.fillRoundRect(x, y, width, height, height, height);
		g2.setColor(accent);
		g2.fillRoundRect(x, y,
				Math.max(1, (int)Math.round(width
						* Math.max(0.0, Math.min(1.0, progress)))),
				height,
				height,
				height);
	}

	private static Color roleColor(NodeCapacityProfile.Role role)
	{
		if (role == NodeCapacityProfile.Role.STRONG)
		{
			return GREEN;
		}
		if (role == NodeCapacityProfile.Role.WEAK)
		{
			return AMBER;
		}
		if (role == NodeCapacityProfile.Role.MEMORY_HEAVY)
		{
			return BLUE;
		}
		if (role == NodeCapacityProfile.Role.UNRELIABLE)
		{
			return RED;
		}
		return PURPLE;
	}

	private static EnumMap<SchedulerLabEngine.WorkloadUnitType, Integer>
			workloadCounts(List<SchedulerLabEngine.WorkloadUnit> jobs)
	{
		EnumMap<SchedulerLabEngine.WorkloadUnitType, Integer> counts =
				new EnumMap<SchedulerLabEngine.WorkloadUnitType, Integer>(
						SchedulerLabEngine.WorkloadUnitType.class);
		for (SchedulerLabEngine.WorkloadUnitType type :
				SchedulerLabEngine.WorkloadUnitType.values())
		{
			counts.put(type, Integer.valueOf(0));
		}
		if (jobs != null)
		{
			for (SchedulerLabEngine.WorkloadUnit job : jobs)
			{
				if (job == null)
				{
					continue;
				}
				Integer current = counts.get(job.getType());
				counts.put(job.getType(), Integer.valueOf(
						(current == null ? 0 : current.intValue()) + 1));
			}
		}
		return counts;
	}

	private static Color workloadColor(
			SchedulerLabEngine.WorkloadUnitType type)
	{
		if (type == SchedulerLabEngine.WorkloadUnitType.PREPROCESSING_BATCH)
		{
			return BLUE;
		}
		if (type == SchedulerLabEngine.WorkloadUnitType.TARGET_RECOMMENDATION_JOB)
		{
			return GREEN;
		}
		if (type == SchedulerLabEngine.WorkloadUnitType.CANDIDATE_SHARD)
		{
			return PURPLE;
		}
		if (type == SchedulerLabEngine.WorkloadUnitType.MODEL_TRAINING_JOB)
		{
			return AMBER;
		}
		if (type == SchedulerLabEngine.WorkloadUnitType.GRAPH_REPORTING_JOB)
		{
			return new Color(92, 141, 190);
		}
		return new Color(96, 126, 206);
	}

	private static boolean policyUses(
			SchedulerLabEngine.SchedulingPolicy policy,
			String feature)
	{
		if (policy == null || feature == null)
		{
			return false;
		}
		if ("capacity".equals(feature))
		{
			return policy == SchedulerLabEngine.SchedulingPolicy.WEIGHTED_ROUND_ROBIN
					|| policy == SchedulerLabEngine.SchedulingPolicy.FASTEST_NODE_FIRST
					|| policy == SchedulerLabEngine.SchedulingPolicy.CAPACITY_WEIGHTED
					|| policy == SchedulerLabEngine.SchedulingPolicy.FAILURE_AWARE_RESCHEDULING;
		}
		if ("queue".equals(feature))
		{
			return policy == SchedulerLabEngine.SchedulingPolicy.LEAST_LOADED_NODE
					|| policy == SchedulerLabEngine.SchedulingPolicy.QUEUE_LENGTH
					|| policy == SchedulerLabEngine.SchedulingPolicy.CAPACITY_WEIGHTED
					|| policy == SchedulerLabEngine.SchedulingPolicy.WORK_STEALING;
		}
		if ("memory".equals(feature))
		{
			return policy == SchedulerLabEngine.SchedulingPolicy.MEMORY_AWARE_SAFE_WAVE
					|| policy == SchedulerLabEngine.SchedulingPolicy.CAPACITY_WEIGHTED;
		}
		if ("deadline".equals(feature))
		{
			return policy == SchedulerLabEngine.SchedulingPolicy.DEADLINE_PRIORITY;
		}
		if ("stealing".equals(feature))
		{
			return policy == SchedulerLabEngine.SchedulingPolicy.WORK_STEALING;
		}
		if ("retry".equals(feature))
		{
			return policy == SchedulerLabEngine.SchedulingPolicy.FAILURE_AWARE_RESCHEDULING;
		}
		return false;
	}

}
