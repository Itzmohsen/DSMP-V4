package TwitterGatherDataFollowers.userRyersonU;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.Window;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

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
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.table.AbstractTableModel;

final class DynamicQueueLabPanel extends JPanel
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
			new Color(25, 30, 38), StudioNightTheme.NIGHT_TEXT);
	private static final Color MUTED = StudioNightTheme.adaptiveColor(
			new Color(89, 101, 119), StudioNightTheme.NIGHT_MUTED);
	private static final Color BLUE = StudioNightTheme.adaptiveColor(
			new Color(82, 113, 219), StudioNightTheme.NIGHT_BLUE);
	private static final Color GREEN = StudioNightTheme.adaptiveColor(
			new Color(86, 151, 111), StudioNightTheme.NIGHT_GREEN);
	private static final Color PURPLE = StudioNightTheme.adaptiveColor(
			new Color(132, 101, 212), StudioNightTheme.NIGHT_PURPLE);
	private static final Color AMBER = StudioNightTheme.adaptiveColor(
			new Color(190, 145, 66), StudioNightTheme.NIGHT_AMBER);
	private static final Color RED = StudioNightTheme.adaptiveColor(
			new Color(202, 74, 63), StudioNightTheme.NIGHT_RED);
	private static final DecimalFormat PERCENT = new DecimalFormat("0.0%");

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
	private final JSpinner unitsSpinner =
			new JSpinner(new SpinnerNumberModel(160, 1, 5000, 10));
	private final JSpinner scaleSpinner =
			new JSpinner(new SpinnerNumberModel(8, 1, 80, 1));
	private final JSpinner retrySpinner =
			new JSpinner(new SpinnerNumberModel(2, 0, 10, 1));
	private final JButton runButton =
			button("Run dynamic queue", BLUE, Color.WHITE);
	private final JButton cancelButton =
			button("Cancel", SURFACE_ALT, TEXT);
	private final JButton capacityButton =
			button("Node capacity", SURFACE_ALT, TEXT);
	private final JButton defaultsButton =
			button("Restore DSMP defaults", SURFACE_ALT, TEXT);

	private final JLabel statusPill = new JLabel("READY");
	private final JLabel statusLabel =
			new JLabel("Configure a queue run, then press Run dynamic queue.");
	private final JLabel jobsMetric = metricValue("--");
	private final JLabel elapsedMetric = metricValue("--");
	private final JLabel runningMetric = metricValue("--");
	private final JLabel retryMetric = metricValue("--");
	private final JLabel utilizationMetric = metricValue("--");
	private final WorkloadShapeCanvas workloadCanvas = new WorkloadShapeCanvas();
	private final NodeLoadCanvas nodeCanvas = new NodeLoadCanvas();
	private final TimelineCanvas timelineCanvas = new TimelineCanvas();
	private final JTextArea eventLog = new JTextArea();
	private final HistoryTableModel historyModel = new HistoryTableModel();
	private final JTable historyTable = new JTable(historyModel);

	private DynamicQueueSchedulerEngine.Controller controller;
	private DynamicQueueSchedulerEngine.Snapshot currentSnapshot;
	private long runSerial;

	DynamicQueueLabPanel(ControllerAgentGui owner)
	{
		super(new BorderLayout(0, 12));
		setBackground(BACKGROUND);
		setBorder(new EmptyBorder(14, 14, 14, 14));
		buildInterface();
		installActions(owner);
		refreshCapacityFromSimulator();
	}

	void refreshCapacityFromSimulator()
	{
		int nodes = ((Number)nodesSpinner.getValue()).intValue();
		NodeCapacityRegistry.configureForNodeCount(
				nodes,
				RuntimeResourceSettings.currentMaxHeapMb());
		workloadCanvas.repaint();
		nodeCanvas.repaint();
	}

	private void buildInterface()
	{
		add(createHeader(), BorderLayout.NORTH);
		JPanel body = new JPanel(new BorderLayout(0, 12));
		body.setOpaque(false);
		body.add(createControls(), BorderLayout.NORTH);
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
		JLabel title = new JLabel("Dynamic Queue Lab (Demo)");
		title.setFont(font(Font.BOLD, 28));
		title.setForeground(TEXT);
		JLabel subtitle = new JLabel(
				"What-if pull-queue demonstration for v4 scheduling ideas. It does not produce simulator recommendations.");
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

	private JComponent createControls()
	{
		JPanel panel = surface(new BorderLayout(0, 12));
		panel.setBorder(new EmptyBorder(14, 16, 14, 16));
		JPanel grid = new JPanel(new GridBagLayout());
		grid.setOpaque(false);
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.gridy = 0;
		gbc.insets = new Insets(0, 0, 0, 10);
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.weightx = 1.0;
		gbc.gridx = 0;
		grid.add(control("Scheduling policy", policyBox), gbc);
		gbc.gridx++;
		grid.add(control("Workload model", templateBox), gbc);
		gbc.gridx++;
		gbc.weightx = 0.28;
		grid.add(control("Nodes", nodesSpinner), gbc);
		gbc.gridx++;
		grid.add(control("Work units", unitsSpinner), gbc);
		gbc.gridx++;
		grid.add(control("ms per cost", scaleSpinner), gbc);
		gbc.gridx++;
		grid.add(control("Max retries", retrySpinner), gbc);
		gbc.gridx++;
		gbc.weightx = 0.0;
		JPanel buttons = new JPanel(new GridLayout(2, 2, 8, 8));
		buttons.setOpaque(false);
		buttons.add(capacityButton);
		buttons.add(defaultsButton);
		buttons.add(runButton);
		buttons.add(cancelButton);
		grid.add(buttons, gbc);
		panel.add(grid, BorderLayout.NORTH);
		panel.add(createMetricStrip(), BorderLayout.SOUTH);
		return panel;
	}

	private JComponent createMetricStrip()
	{
		JPanel strip = new JPanel(new GridLayout(1, 5, 10, 0));
		strip.setOpaque(false);
		strip.add(metricCard("Jobs", jobsMetric, BLUE));
		strip.add(metricCard("Elapsed", elapsedMetric, PURPLE));
		strip.add(metricCard("Running", runningMetric, AMBER));
		strip.add(metricCard("Retries", retryMetric, RED));
		strip.add(metricCard("Utilization", utilizationMetric, GREEN));
		return strip;
	}

	private JComponent createWorkArea()
	{
		JPanel top = new JPanel(new GridLayout(1, 2, 12, 0));
		top.setOpaque(false);
		top.add(section("Queue Work Units",
				"Independent jobs are placed in the scheduler queue before model state exists.",
				workloadCanvas,
				new Dimension(620, 230)));
		top.add(section("Live Node Load",
				"Strong nodes finish sooner and pull more jobs; weak nodes pull fewer.",
				nodeCanvas,
				new Dimension(620, 230)));

		JPanel timelinePanel = section("Real Dynamic Queue Timeline",
				"Actual wall-clock worker lanes from this run, not a hypothetical animation.",
				null,
				null);
		JScrollPane timelineScroll = new JScrollPane(timelineCanvas);
		timelineScroll.setBorder(BorderFactory.createLineBorder(BORDER));
		timelineScroll.setPreferredSize(new Dimension(1240, 330));
		timelinePanel.add(timelineScroll, BorderLayout.CENTER);

		JPanel lowerLeft = section("Session Comparison History",
				"Every completed queue run is kept so policies can be compared in the same session.",
				null,
				null);
		historyTable.setRowHeight(26);
		historyTable.setFont(font(Font.BOLD, 11));
		historyTable.getTableHeader().setFont(font(Font.BOLD, 10));
		JScrollPane historyScroll = new JScrollPane(historyTable);
		historyScroll.setPreferredSize(new Dimension(600, 190));
		historyScroll.setBorder(BorderFactory.createLineBorder(BORDER));
		lowerLeft.add(historyScroll, BorderLayout.CENTER);

		JPanel lowerRight = section("Dynamic Queue Event Log",
				"Read-only timeline of starts, completions, retries, and failures.",
				null,
				null);
		eventLog.setEditable(false);
		eventLog.setLineWrap(false);
		eventLog.setFont(new Font("Monospaced", Font.PLAIN, 11));
		eventLog.setForeground(new Color(228, 233, 242));
		eventLog.setBackground(new Color(19, 23, 34));
		eventLog.setBorder(new EmptyBorder(10, 10, 10, 10));
		JScrollPane logScroll = new JScrollPane(eventLog);
		logScroll.setPreferredSize(new Dimension(600, 190));
		logScroll.setBorder(BorderFactory.createLineBorder(new Color(31, 38, 52)));
		lowerRight.add(logScroll, BorderLayout.CENTER);

		JPanel lower = new JPanel(new GridLayout(1, 2, 12, 0));
		lower.setOpaque(false);
		lower.add(lowerLeft);
		lower.add(lowerRight);

		JPanel stack = new JPanel(new BorderLayout(0, 12));
		stack.setOpaque(false);
		stack.add(top, BorderLayout.NORTH);
		stack.add(timelinePanel, BorderLayout.CENTER);
		stack.add(lower, BorderLayout.SOUTH);

		JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, top, stack);
		split.setDividerSize(0);
		split.setEnabled(false);
		split.setResizeWeight(0.0);
		split.setBorder(BorderFactory.createEmptyBorder());
		return stack;
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

	private void installActions(final ControllerAgentGui owner)
	{
		nodesSpinner.addChangeListener(new javax.swing.event.ChangeListener()
		{
			public void stateChanged(javax.swing.event.ChangeEvent event)
			{
				refreshCapacityFromSimulator();
			}
		});
		runButton.addActionListener(new java.awt.event.ActionListener()
		{
			public void actionPerformed(java.awt.event.ActionEvent event)
			{
				startRun();
			}
		});
		cancelButton.addActionListener(new java.awt.event.ActionListener()
		{
			public void actionPerformed(java.awt.event.ActionEvent event)
			{
				cancelRun();
			}
		});
		capacityButton.addActionListener(new java.awt.event.ActionListener()
		{
			public void actionPerformed(java.awt.event.ActionEvent event)
			{
				int nodes = ((Number)nodesSpinner.getValue()).intValue();
				Window window = SwingUtilities.getWindowAncestor(
						DynamicQueueLabPanel.this);
				NodeCapacitySettingsDialog.showDialog(
						window,
						nodes,
						RuntimeResourceSettings.currentMaxHeapMb());
				refreshCapacityFromSimulator();
			}
		});
		defaultsButton.addActionListener(new java.awt.event.ActionListener()
		{
			public void actionPerformed(java.awt.event.ActionEvent event)
			{
				int nodes = ((Number)nodesSpinner.getValue()).intValue();
				NodeCapacityRegistry.resetToDefaults(
						nodes,
						RuntimeResourceSettings.currentMaxHeapMb());
				refreshCapacityFromSimulator();
			}
		});
	}

	private void startRun()
	{
		cancelRun();
		final long activeRunSerial = ++runSerial;
		int nodes = ((Number)nodesSpinner.getValue()).intValue();
		NodeCapacityRegistry.configureForNodeCount(
				nodes,
				RuntimeResourceSettings.currentMaxHeapMb());
		currentSnapshot = null;
		eventLog.setText("");
		timelineCanvas.setSnapshot(null);
		nodeCanvas.setSnapshot(null);
		setRunning(true);
		DynamicQueueSchedulerEngine.Config config =
				new DynamicQueueSchedulerEngine.Config(
						NodeCapacityRegistry.snapshot(),
						(SchedulerLabEngine.SchedulingPolicy)
								policyBox.getSelectedItem(),
						(SchedulerLabEngine.WorkloadTemplate)
								templateBox.getSelectedItem(),
						((Number)unitsSpinner.getValue()).intValue(),
						RuntimeResourceSettings.currentMaxHeapMb(),
						((Number)scaleSpinner.getValue()).intValue(),
						((Number)retrySpinner.getValue()).intValue());
		controller = DynamicQueueSchedulerEngine.runAsync(
				config,
				new DynamicQueueSchedulerEngine.Listener()
				{
					public void onSnapshot(
							final DynamicQueueSchedulerEngine.Snapshot snapshot)
					{
						SwingUtilities.invokeLater(new Runnable()
						{
					public void run()
					{
								if (activeRunSerial == runSerial)
								{
									applySnapshot(snapshot);
								}
							}
						});
					}

					public void onFinished(
							final DynamicQueueSchedulerEngine.Result result)
					{
						SwingUtilities.invokeLater(new Runnable()
						{
							public void run()
							{
								if (activeRunSerial != runSerial)
								{
									return;
								}
								applySnapshot(result.getFinalSnapshot());
								historyModel.add(result.getFinalSnapshot());
								setRunning(false);
								statusPill.setText(
										result.getFinalSnapshot().isCancelled()
												? "CANCELLED" : "COMPLETE");
								statusLabel.setText(
										result.getFinalSnapshot().isCancelled()
												? "Dynamic queue run was cancelled."
												: "Dynamic queue run finished. Recommendation scoring was untouched.");
							}
						});
					}

					public void onFailed(
							final String message,
							final Throwable throwable)
					{
						SwingUtilities.invokeLater(new Runnable()
						{
							public void run()
							{
								if (activeRunSerial != runSerial)
								{
									return;
								}
								setRunning(false);
								statusPill.setText("FAILED");
								statusPill.setForeground(RED.darker());
								statusPill.setBackground(new Color(253, 232, 231));
								statusLabel.setText(message == null
										? "Dynamic queue run failed."
										: message);
							}
						});
					}
				});
	}

	private void cancelRun()
	{
		if (controller != null)
		{
			controller.cancel();
			controller = null;
		}
	}

	private void setRunning(boolean running)
	{
		runButton.setEnabled(!running);
		cancelButton.setEnabled(running);
		statusPill.setText(running ? "RUNNING" : "READY");
		statusPill.setForeground(running ? BLUE.darker() : GREEN.darker());
		statusPill.setBackground(running
				? new Color(232, 239, 255)
				: new Color(228, 245, 236));
		statusLabel.setText(running
				? "Nodes are pulling independent work from the dynamic scheduler queue."
				: "Configure a queue run, then press Run dynamic queue.");
	}

	private void applySnapshot(DynamicQueueSchedulerEngine.Snapshot snapshot)
	{
		currentSnapshot = snapshot;
		if (snapshot == null)
		{
			return;
		}
		jobsMetric.setText(snapshot.getCompletedJobs()
				+ " / " + snapshot.getTotalJobs());
		elapsedMetric.setText(formatDuration(snapshot.getElapsedMillis()));
		runningMetric.setText(snapshot.getRunningJobs()
				+ " running | " + snapshot.getWaitingJobs() + " waiting");
		retryMetric.setText(snapshot.getRetriedJobs()
				+ " retries | " + snapshot.getFailedJobs() + " failed");
		utilizationMetric.setText(PERCENT.format(snapshot.utilization()));
		workloadCanvas.setSnapshot(snapshot);
		nodeCanvas.setSnapshot(snapshot);
		timelineCanvas.setSnapshot(snapshot);
		updateEventLog(snapshot);
	}

	private void updateEventLog(DynamicQueueSchedulerEngine.Snapshot snapshot)
	{
		StringBuilder builder = new StringBuilder();
		for (String line : snapshot.getLogLines())
		{
			builder.append(line).append('\n');
		}
		eventLog.setText(builder.toString());
		eventLog.setCaretPosition(eventLog.getDocument().getLength());
	}

	private JComponent control(String label, JComponent component)
	{
		JPanel panel = new JPanel(new BorderLayout(0, 4));
		panel.setOpaque(false);
		JLabel text = new JLabel(label);
		text.setFont(font(Font.BOLD, 10));
		text.setForeground(MUTED);
		panel.add(text, BorderLayout.NORTH);
		panel.add(component, BorderLayout.CENTER);
		return panel;
	}

	private JPanel section(
			String title,
			String subtitle,
			JComponent child,
			Dimension preferred)
	{
		JPanel panel = surface(new BorderLayout(0, 10));
		panel.setBorder(new EmptyBorder(12, 12, 12, 12));
		JLabel titleLabel = new JLabel(title);
		titleLabel.setFont(font(Font.BOLD, 16));
		titleLabel.setForeground(TEXT);
		JLabel subtitleLabel = new JLabel(subtitle);
		subtitleLabel.setFont(font(Font.PLAIN, 11));
		subtitleLabel.setForeground(MUTED);
		JPanel titleStack = new JPanel();
		titleStack.setOpaque(false);
		titleStack.setLayout(new BoxLayout(titleStack, BoxLayout.Y_AXIS));
		titleStack.add(titleLabel);
		titleStack.add(subtitleLabel);
		panel.add(titleStack, BorderLayout.NORTH);
		if (child != null)
		{
			if (preferred != null)
			{
				child.setPreferredSize(preferred);
			}
			panel.add(child, BorderLayout.CENTER);
		}
		return panel;
	}

	private JPanel metricCard(String label, JLabel value, Color accent)
	{
		JPanel card = new JPanel(new BorderLayout(0, 2));
		card.setOpaque(true);
		card.setBackground(SURFACE_ALT);
		card.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createMatteBorder(0, 4, 0, 0, accent),
				new EmptyBorder(10, 12, 10, 10)));
		JLabel labelText = new JLabel(label.toUpperCase());
		labelText.setFont(font(Font.BOLD, 10));
		labelText.setForeground(MUTED);
		card.add(labelText, BorderLayout.NORTH);
		card.add(value, BorderLayout.CENTER);
		return card;
	}

	private JLabel metricValue(String text)
	{
		JLabel value = new JLabel(text);
		value.setFont(font(Font.BOLD, 20));
		value.setForeground(TEXT);
		return value;
	}

	private JPanel surface(BorderLayout layout)
	{
		JPanel panel = new JPanel(layout);
		panel.setOpaque(true);
		panel.setBackground(SURFACE);
		panel.setBorder(BorderFactory.createLineBorder(BORDER));
		return panel;
	}

	private static Font font(int style, int size)
	{
		return new Font(Font.SANS_SERIF, style, size);
	}

	private static Color themed(Color day, Color night)
	{
		return StudioNightTheme.isNightMode() ? night : day;
	}

	private static JButton button(String text, Color background, Color foreground)
	{
		JButton button = new JButton(text);
		button.setUI(new BasicButtonUI());
		button.setOpaque(true);
		button.setContentAreaFilled(true);
		button.setBorderPainted(true);
		button.setFocusPainted(false);
		button.setBackground(background);
		button.setForeground(foreground);
		button.setFont(font(Font.BOLD, 12));
		button.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(darker(background)),
				new EmptyBorder(9, 14, 9, 14)));
		return button;
	}

	private static Color darker(Color color)
	{
		return new Color(
				Math.max(0, color.getRed() - 35),
				Math.max(0, color.getGreen() - 35),
				Math.max(0, color.getBlue() - 35));
	}

	private static JPanel createAttributionBlock()
	{
		JPanel block = new JPanel();
		block.setOpaque(false);
		block.setLayout(new BoxLayout(block, BoxLayout.Y_AXIS));
		block.setBorder(new EmptyBorder(8, 0, 0, 0));
		block.add(attribution("FACULTY ADVISOR", "Dr. Abdolreza Abhari"));
		block.add(attribution("DEVELOPER", "Mohsen Alavi"));
		return block;
	}

	private static JLabel attribution(String role, String name)
	{
		JLabel label = new JLabel(role + "  " + name);
		label.setFont(font(Font.BOLD, 10));
		label.setForeground(new Color(61, 70, 84));
		return label;
	}

	private static String formatDuration(long millis)
	{
		long seconds = Math.max(0L, millis) / 1000L;
		long ms = Math.max(0L, millis) % 1000L;
		if (seconds < 60L)
		{
			return seconds + "." + String.format("%03d", Long.valueOf(ms)) + " s";
		}
		return (seconds / 60L) + "m " + (seconds % 60L) + "s";
	}

	private static Color typeColor(
			SchedulerLabEngine.WorkloadUnitType type)
	{
		if (type == SchedulerLabEngine.WorkloadUnitType.MODEL_TRAINING_JOB)
		{
			return AMBER;
		}
		if (type == SchedulerLabEngine.WorkloadUnitType.CANDIDATE_SHARD)
		{
			return PURPLE;
		}
		if (type == SchedulerLabEngine.WorkloadUnitType.GRAPH_REPORTING_JOB)
		{
			return new Color(93, 134, 174);
		}
		if (type == SchedulerLabEngine.WorkloadUnitType.PREPROCESSING_BATCH)
		{
			return BLUE;
		}
		return GREEN;
	}

	private static final class HeaderSignalPanel extends JPanel
	{
		HeaderSignalPanel()
		{
			setOpaque(false);
			setPreferredSize(new Dimension(420, 80));
		}

		protected void paintComponent(Graphics graphics)
		{
			super.paintComponent(graphics);
			Graphics2D g = (Graphics2D)graphics.create();
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
					RenderingHints.VALUE_ANTIALIAS_ON);
			int width = getWidth();
			int height = getHeight();
			int[] xs = new int[] {30, width / 4, width / 2, width * 3 / 4, width - 28};
			int[] ys = new int[] {height / 2, height / 2 - 12, height / 2 + 6,
					height / 2 - 8, height / 2 + 8};
			g.setStroke(new BasicStroke(2f));
			g.setColor(new Color(207, 221, 245));
			for (int index = 0; index < xs.length - 1; index++)
			{
				g.drawLine(xs[index], ys[index], xs[index + 1], ys[index + 1]);
			}
			for (int index = 0; index < xs.length; index++)
			{
				g.setColor(index % 2 == 0
						? new Color(232, 239, 255)
						: new Color(229, 246, 236));
				g.fillOval(xs[index] - 7, ys[index] - 7, 14, 14);
				g.setColor(index % 2 == 0 ? BLUE : GREEN);
				g.setStroke(new BasicStroke(2f));
				g.drawOval(xs[index] - 7, ys[index] - 7, 14, 14);
			}
			g.dispose();
		}
	}

	private final class WorkloadShapeCanvas extends JPanel
	{
		private DynamicQueueSchedulerEngine.Snapshot snapshot;

		WorkloadShapeCanvas()
		{
			setOpaque(false);
		}

		void setSnapshot(DynamicQueueSchedulerEngine.Snapshot snapshot)
		{
			this.snapshot = snapshot;
			repaint();
		}

		protected void paintComponent(Graphics graphics)
		{
			super.paintComponent(graphics);
			Graphics2D g = graphics2(graphics);
			int width = getWidth();
			int y = 20;
			drawSummaryBar(g, 18, y, width - 36,
					"Dynamic queue contract",
					"Complete independent jobs only. No partial score maps, graph files, or model internals are rewritten.",
					BLUE);
			y += 64;
			int count = currentSnapshot == null
					? ((Number)unitsSpinner.getValue()).intValue()
					: currentSnapshot.getTotalJobs();
			SchedulerLabEngine.WorkloadTemplate template =
					(SchedulerLabEngine.WorkloadTemplate)templateBox.getSelectedItem();
			String[] labels = new String[] {
					"Create " + count + " work units",
					"Queue waits for capable nodes",
					"Nodes pull until empty",
					"Retry complete failed jobs" };
			Color[] colors = new Color[] {BLUE, PURPLE, GREEN, AMBER};
			int cardWidth = (width - 72) / labels.length;
			for (int index = 0; index < labels.length; index++)
			{
				drawMiniCard(g, 18 + index * cardWidth, y, cardWidth - 10, 86,
						labels[index],
						index == 0 && template != null
								? template.getDescription()
								: "scheduler-safe work unit",
						colors[index]);
			}
			g.dispose();
		}
	}

	private final class NodeLoadCanvas extends JPanel
	{
		private DynamicQueueSchedulerEngine.Snapshot snapshot;

		NodeLoadCanvas()
		{
			setOpaque(false);
		}

		void setSnapshot(DynamicQueueSchedulerEngine.Snapshot snapshot)
		{
			this.snapshot = snapshot;
			repaint();
		}

		protected void paintComponent(Graphics graphics)
		{
			super.paintComponent(graphics);
			Graphics2D g = graphics2(graphics);
			List<NodeCapacityProfile> profiles =
					NodeCapacityRegistry.snapshot();
			int width = getWidth();
			int columns = profiles.size() <= 4 ? profiles.size() : 4;
			columns = Math.max(1, columns);
			int cardWidth = Math.max(150, (width - 24 - (columns - 1) * 10) / columns);
			int cardHeight = 84;
			for (int index = 0; index < profiles.size(); index++)
			{
				int row = index / columns;
				int col = index % columns;
				int x = 12 + col * (cardWidth + 10);
				int y = 16 + row * (cardHeight + 10);
				NodeCapacityProfile profile = profiles.get(index);
				DynamicQueueSchedulerEngine.NodeSnapshot node =
						nodeFor(profile.getNodeIndex());
				drawNodeCard(g, x, y, cardWidth, cardHeight, profile, node);
			}
			g.dispose();
		}

		private DynamicQueueSchedulerEngine.NodeSnapshot nodeFor(int nodeIndex)
		{
			if (snapshot == null)
			{
				return null;
			}
			for (DynamicQueueSchedulerEngine.NodeSnapshot node
					: snapshot.getNodes())
			{
				if (node.getProfile().getNodeIndex() == nodeIndex)
				{
					return node;
				}
			}
			return null;
		}
	}

	private final class TimelineCanvas extends JPanel
	{
		private DynamicQueueSchedulerEngine.Snapshot snapshot;

		TimelineCanvas()
		{
			setOpaque(true);
			setBackground(SURFACE_ALT);
			setPreferredSize(new Dimension(1180, 320));
		}

		void setSnapshot(DynamicQueueSchedulerEngine.Snapshot snapshot)
		{
			this.snapshot = snapshot;
			int nodes = snapshot == null ? Math.max(1,
					((Number)nodesSpinner.getValue()).intValue())
					: snapshot.getNodes().size();
			setPreferredSize(new Dimension(
					1180,
					Math.max(320, 70 + nodes * 54)));
			revalidate();
			repaint();
		}

		protected void paintComponent(Graphics graphics)
		{
			super.paintComponent(graphics);
			Graphics2D g = graphics2(graphics);
			int width = getWidth();
			int left = 86;
			int right = width - 34;
			int top = 42;
			g.setFont(font(Font.BOLD, 12));
			g.setColor(TEXT);
			g.drawString("node pull timeline", 18, 22);
			if (snapshot == null || snapshot.getSegments().isEmpty())
			{
				g.setFont(font(Font.BOLD, 15));
				g.setColor(MUTED);
				g.drawString("Run the dynamic queue to see real worker lanes.", 24, 96);
				g.dispose();
				return;
			}
			long max = Math.max(1L, snapshot.getElapsedMillis());
			for (int index = 0; index < snapshot.getNodes().size(); index++)
			{
				DynamicQueueSchedulerEngine.NodeSnapshot node =
						snapshot.getNodes().get(index);
				int laneY = top + index * 54;
				g.setColor(new Color(224, 232, 243));
				g.drawLine(left, laneY + 18, right, laneY + 18);
				g.setColor(TEXT);
				g.setFont(font(Font.BOLD, 12));
				g.drawString("Node " + node.getProfile().getNodeIndex(), 18, laneY + 16);
				g.setFont(font(Font.PLAIN, 10));
				g.setColor(MUTED);
				g.drawString(node.getProfile().shortLabel(), 18, laneY + 32);
			}
			for (DynamicQueueSchedulerEngine.Segment segment
					: snapshot.getSegments())
			{
				int nodeSlot = Math.max(0, segment.getNodeIndex() - 1);
				int y = top + nodeSlot * 54 + 6 + segment.getLaneIndex() * 8;
				int x1 = left + (int)Math.round(
						(right - left) * (segment.getStartMillis()
								/ (double)max));
				int x2 = left + (int)Math.round(
						(right - left) * (segment.getEndMillis()
								/ (double)max));
				x2 = Math.max(x1 + 4, x2);
				g.setColor(segment.isFailed() ? RED : typeColor(segment.getType()));
				g.fillRoundRect(x1, y, x2 - x1, 10, 7, 7);
			}
			g.setColor(MUTED);
			g.setFont(font(Font.BOLD, 10));
			g.drawString("0 ms", left, getHeight() - 22);
			g.drawString(formatDuration(max), right - 64, getHeight() - 22);
			g.dispose();
		}
	}

	private final class HistoryTableModel extends AbstractTableModel
	{
		private final ArrayList<DynamicQueueSchedulerEngine.Snapshot> rows =
				new ArrayList<DynamicQueueSchedulerEngine.Snapshot>();
		private final String[] columns = new String[] {
				"Policy", "Template", "Jobs", "Time", "Utilization",
				"Retries", "Failed" };

		void add(DynamicQueueSchedulerEngine.Snapshot snapshot)
		{
			if (snapshot != null)
			{
				rows.add(0, snapshot);
				fireTableDataChanged();
			}
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

		public Object getValueAt(int row, int column)
		{
			DynamicQueueSchedulerEngine.Snapshot snapshot = rows.get(row);
			if (column == 0)
			{
				return snapshot.getPolicy().getLabel();
			}
			if (column == 1)
			{
				return snapshot.getTemplate().getLabel();
			}
			if (column == 2)
			{
				return snapshot.getCompletedJobs() + "/" + snapshot.getTotalJobs();
			}
			if (column == 3)
			{
				return formatDuration(snapshot.getElapsedMillis());
			}
			if (column == 4)
			{
				return PERCENT.format(snapshot.utilization());
			}
			if (column == 5)
			{
				return Integer.valueOf(snapshot.getRetriedJobs());
			}
			return Integer.valueOf(snapshot.getFailedJobs());
		}
	}

	private static Graphics2D graphics2(Graphics graphics)
	{
		Graphics2D g = (Graphics2D)graphics.create();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
				RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
				RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		return g;
	}

	private static void drawSummaryBar(
			Graphics2D g,
			int x,
			int y,
			int width,
			String title,
			String body,
			Color accent)
	{
		g.setColor(SURFACE_ALT);
		g.fillRoundRect(x, y, width, 52, 10, 10);
		g.setColor(BORDER);
		g.drawRoundRect(x, y, width, 52, 10, 10);
		g.setColor(accent);
		g.fillRoundRect(x + 10, y + 14, 10, 10, 10, 10);
		g.setColor(TEXT);
		g.setFont(font(Font.BOLD, 13));
		g.drawString(title, x + 30, y + 21);
		g.setColor(MUTED);
		g.setFont(font(Font.PLAIN, 11));
		g.drawString(body, x + 30, y + 38);
	}

	private static void drawMiniCard(
			Graphics2D g,
			int x,
			int y,
			int width,
			int height,
			String title,
			String subtitle,
			Color accent)
	{
		g.setColor(SURFACE);
		g.fillRoundRect(x, y, width, height, 10, 10);
		g.setColor(BORDER);
		g.drawRoundRect(x, y, width, height, 10, 10);
		g.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 46));
		g.fillRoundRect(x, y, 8, height, 8, 8);
		g.setColor(accent);
		g.fillOval(x + 16, y + 18, 10, 10);
		g.setColor(TEXT);
		g.setFont(font(Font.BOLD, 13));
		g.drawString(title, x + 34, y + 28);
		g.setColor(MUTED);
		g.setFont(font(Font.PLAIN, 10));
		String clipped = subtitle == null ? "" : subtitle;
		if (clipped.length() > 42)
		{
			clipped = clipped.substring(0, 39) + "...";
		}
		g.drawString(clipped, x + 34, y + 48);
	}

	private static void drawNodeCard(
			Graphics2D g,
			int x,
			int y,
			int width,
			int height,
			NodeCapacityProfile profile,
			DynamicQueueSchedulerEngine.NodeSnapshot node)
	{
		Color accent = profile.getRole() == NodeCapacityProfile.Role.STRONG
				? GREEN
				: (profile.getRole() == NodeCapacityProfile.Role.WEAK
						? AMBER
						: (profile.getRole() == NodeCapacityProfile.Role.UNRELIABLE
								? RED : BLUE));
		g.setColor(SURFACE);
		g.fillRoundRect(x, y, width, height, 10, 10);
		g.setColor(BORDER);
		g.drawRoundRect(x, y, width, height, 10, 10);
		g.setColor(accent);
		g.fillRoundRect(x, y, 6, height, 8, 8);
		g.setColor(TEXT);
		g.setFont(font(Font.BOLD, 13));
		g.drawString("Node " + profile.getNodeIndex(), x + 16, y + 22);
		g.setFont(font(Font.BOLD, 11));
		g.setColor(accent.darker());
		g.drawString(profile.shortLabel(), x + 16, y + 38);
		int done = node == null ? 0 : node.getCompletedJobs();
		int failed = node == null ? 0 : node.getFailedJobs();
		int running = node == null ? 0 : node.getRunningJobs();
		g.setFont(font(Font.PLAIN, 10));
		g.setColor(MUTED);
		g.drawString(done + " done | " + running
				+ " running | " + failed + " failed", x + 16, y + 55);
		g.setColor(new Color(228, 235, 244));
		g.fillRoundRect(x + 16, y + height - 16, width - 32, 5, 5, 5);
		g.setColor(accent);
		int total = node == null ? Math.max(1, done) : Math.max(
				1,
				node.getAssignedJobs());
		int bar = (int)Math.round((width - 32) * (done / (double)total));
		g.fillRoundRect(x + 16, y + height - 16, bar, 5, 5, 5);
	}
}
