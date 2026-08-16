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
import java.awt.Window;
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
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;

final class DynamicQueueLabPanel extends JPanel
{
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
	private static final int BTN_PRIMARY = 0;
	private static final int BTN_NEUTRAL = 1;
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
			button("Run dynamic queue", BTN_PRIMARY);
	private final JButton cancelButton =
			button("Cancel", BTN_NEUTRAL);
	private final JButton capacityButton =
			button("Node capacity", BTN_NEUTRAL);
	private final JButton defaultsButton =
			button("Restore DSMP defaults", BTN_NEUTRAL);

	private final JLabel statusPill = pill("READY", GREEN);
	private final JLabel statusLabel = themedLabel(
			"Configure a queue run, then press Run dynamic queue.",
			TEXT,
			NebulaDesign.fontMedium(12f));
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
		setOpaque(false);
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
		JPanel header = new HeroCardPanel(new BorderLayout(24, 0));
		header.setBorder(new EmptyBorder(13, 20, 15, 18));
		JPanel titleStack = new JPanel();
		titleStack.setOpaque(false);
		titleStack.setLayout(new BoxLayout(titleStack, BoxLayout.Y_AXIS));
		JLabel eyebrow = themedLabel("SCHEDULING", BLUE, NebulaDesign.fontSection());
		eyebrow.setAlignmentX(Component.LEFT_ALIGNMENT);
		JLabel title = themedLabel("Dynamic Queue Lab", TEXT, NebulaDesign.fontDisplay());
		JPanel titleRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
		titleRow.setOpaque(false);
		titleRow.setAlignmentX(Component.LEFT_ALIGNMENT);
		titleRow.add(title);
		titleRow.add(pill("DEMO", PURPLE));
		JLabel subtitle = themedLabel(
				"What-if pull-queue demonstration for v4 scheduling ideas. It does not produce simulator recommendations.",
				MUTED,
				NebulaDesign.fontBody(12.5f));
		subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
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
		strip.setBorder(new EmptyBorder(12, 0, 0, 0));
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
				new Dimension(620, 216)));
		top.add(section("Live Node Load",
				"Strong nodes finish sooner and pull more jobs; weak nodes pull fewer.",
				nodeCanvas,
				new Dimension(620, 216)));

		JPanel timelinePanel = section("Real Dynamic Queue Timeline",
				"Actual wall-clock worker lanes from this run, not a hypothetical animation.",
				null,
				null);
		JScrollPane timelineScroll = new JScrollPane(timelineCanvas);
		timelineScroll.setBorder(BorderFactory.createLineBorder(BORDER));
		timelineScroll.setPreferredSize(new Dimension(1240, 260));
		timelineScroll.getViewport().setBackground(SURFACE);
		timelinePanel.add(timelineScroll, BorderLayout.CENTER);

		JPanel lowerLeft = section("Session Comparison History",
				"Every completed queue run is kept so policies can be compared in the same session.",
				null,
				null);
		configureHistoryTable();
		JScrollPane historyScroll = new JScrollPane(historyTable);
		historyScroll.setPreferredSize(new Dimension(600, 160));
		historyScroll.setBorder(BorderFactory.createLineBorder(BORDER));
		historyScroll.getViewport().setBackground(SURFACE);
		lowerLeft.add(historyScroll, BorderLayout.CENTER);

		JPanel lowerRight = section("Dynamic Queue Event Log",
				"Read-only timeline of starts, completions, retries, and failures.",
				null,
				null);
		eventLog.setEditable(false);
		eventLog.setLineWrap(false);
		eventLog.setFont(NebulaDesign.fontMono(12f));
		eventLog.setForeground(NebulaDesign.CONSOLE_TEXT);
		eventLog.setBackground(NebulaDesign.CONSOLE);
		eventLog.setBorder(new EmptyBorder(12, 12, 12, 12));
		JScrollPane logScroll = new JScrollPane(eventLog);
		logScroll.setPreferredSize(new Dimension(600, 160));
		logScroll.setBorder(BorderFactory.createLineBorder(BORDER));
		logScroll.getViewport().setBackground(NebulaDesign.CONSOLE);
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
		return stack;
	}

	private void configureHistoryTable()
	{
		historyTable.setRowHeight(30);
		historyTable.setFont(NebulaDesign.fontBody(12f));
		historyTable.setForeground(TEXT);
		historyTable.setBackground(SURFACE);
		historyTable.setShowVerticalLines(false);
		historyTable.setGridColor(BORDER);
		historyTable.setSelectionBackground(NebulaDesign.SELECTION);
		historyTable.setSelectionForeground(TEXT);
		historyTable.setIntercellSpacing(new Dimension(0, 1));
		historyTable.getTableHeader().setReorderingAllowed(false);
		historyTable.getTableHeader().setDefaultRenderer(new HeaderRenderer());
	}

	private JComponent createStatusBar()
	{
		JPanel bar = surface(new BorderLayout(12, 0));
		bar.setBorder(new EmptyBorder(8, 14, 10, 14));
		bar.add(statusPill, BorderLayout.WEST);
		bar.add(statusLabel, BorderLayout.CENTER);
		return bar;
	}

	private void setStatusTone(String pillText, Color tone)
	{
		statusPill.setText(pillText);
		statusPill.putClientProperty("nebula.tone", tone);
		statusPill.revalidate();
		statusPill.repaint();
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
								setStatusTone(
										result.getFinalSnapshot().isCancelled()
												? "CANCELLED" : "COMPLETE",
										result.getFinalSnapshot().isCancelled()
												? AMBER : GREEN);
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
								setStatusTone("FAILED", RED);
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
		setStatusTone(running ? "RUNNING" : "READY", running ? BLUE : GREEN);
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
		panel.add(themedLabel(label, MUTED, NebulaDesign.fontMedium(11f)),
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
		if (component instanceof JSpinner)
		{
			JSpinner spinner = (JSpinner)component;
			spinner.setBorder(BorderFactory.createLineBorder(BORDER));
			Component editor = spinner.getEditor();
			if (editor instanceof JSpinner.DefaultEditor)
			{
				javax.swing.JTextField field =
						((JSpinner.DefaultEditor)editor).getTextField();
				field.setFont(NebulaDesign.fontMono(12f));
				field.setBackground(SURFACE_ALT);
				field.setForeground(TEXT);
				field.setBorder(new EmptyBorder(4, 8, 4, 8));
			}
		}
	}

	private JPanel section(
			String title,
			String subtitle,
			JComponent child,
			Dimension preferred)
	{
		JPanel panel = surface(new BorderLayout(0, 9));
		panel.setBorder(new EmptyBorder(14, 16, 15, 16));
		JPanel heading = new JPanel(new BorderLayout(0, 2));
		heading.setOpaque(false);
		heading.add(themedLabel(title, TEXT, NebulaDesign.fontTitle()),
				BorderLayout.WEST);
		heading.add(themedLabel(subtitle, NebulaDesign.TEXT_TERTIARY,
				NebulaDesign.fontBody(11.5f)), BorderLayout.SOUTH);
		panel.add(heading, BorderLayout.NORTH);
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

	private JPanel metricCard(String label, JLabel value, final Color accent)
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
				Graphics2D g2 = prepare(graphics);
				g2.setColor(accent);
				g2.fillOval(0, Math.max(0, (getHeight() - 8) / 2), 8, 8);
				g2.dispose();
			}
		};
		dot.setPreferredSize(new Dimension(8, 13));
		return dot;
	}

	private static JLabel metricValue(String text)
	{
		JLabel value = themedLabel(text, TEXT, NebulaDesign.fontSemiBold(19f));
		value.setBorder(new EmptyBorder(0, 1, 0, 0));
		return value;
	}

	private static JPanel surface(BorderLayout layout)
	{
		return new RoundedPanel(layout);
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

	private static JButton button(String text, final int kind)
	{
		JButton button = new JButton(text)
		{
			protected void paintComponent(Graphics graphics)
			{
				Graphics2D g = prepare(graphics);
				int w = getWidth();
				int h = getHeight();
				boolean hover = getModel().isRollover() && isEnabled();
				boolean pressed = getModel().isPressed() && isEnabled();
				int radius = NebulaDesign.RADIUS_CONTROL;
				if (!isEnabled())
				{
					g.setColor(SURFACE_ALT);
					g.fillRoundRect(1, 1, w - 2, h - 2, radius, radius);
					g.setColor(BORDER);
					g.drawRoundRect(1, 1, w - 3, h - 3, radius, radius);
					g.setColor(NebulaDesign.TEXT_TERTIARY);
				}
				else if (kind == BTN_PRIMARY)
				{
					Color fill = pressed
							? NebulaDesign.ACCENT_PRESS
							: hover ? NebulaDesign.ACCENT_HOVER
									: NebulaDesign.ACCENT;
					if (hover)
					{
						g.setColor(alpha(BLUE, 55));
						g.fillRoundRect(0, 0, w, h, radius + 3, radius + 3);
					}
					g.setColor(fill);
					g.fillRoundRect(1, 1, w - 2, h - 2, radius, radius);
					g.setColor(NebulaDesign.TEXT_ON_ACCENT);
				}
				else
				{
					g.setColor(hover ? NebulaDesign.SURFACE_RAISED : SURFACE);
					g.fillRoundRect(1, 1, w - 2, h - 2, radius, radius);
					g.setColor(hover
							? alpha(BLUE, 120)
							: NebulaDesign.BORDER_STRONG);
					g.drawRoundRect(1, 1, w - 3, h - 3, radius, radius);
					g.setColor(TEXT);
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
		button.setBorder(new EmptyBorder(9, 14, 9, 14));
		NebulaWidgets.polishButton(button);
		return button;
	}

	/**
	 * Self-painting status pill: soft tint capsule + strong hue label. The
	 * tone lives in the "nebula.tone" client property so it survives theme
	 * walks and run status changes.
	 */
	private static JLabel pill(String text, Color tone)
	{
		JLabel label = new JLabel(text, SwingConstants.CENTER)
		{
			protected void paintComponent(Graphics graphics)
			{
				Graphics2D g2 = prepare(graphics);
				Object tone = getClientProperty("nebula.tone");
				Color hue = tone instanceof Color ? (Color)tone : BLUE;
				int w = getWidth();
				int h = getHeight();
				g2.setColor(tint(hue, 0.84f));
				g2.fillRoundRect(0, 0, w, h, h, h);
				g2.setColor(alpha(hue, 84));
				g2.drawRoundRect(0, 0, w - 1, h - 1, h - 1, h - 1);
				g2.setColor(deep(hue));
				g2.setFont(getFont());
				java.awt.FontMetrics metrics = g2.getFontMetrics();
				String value = getText();
				g2.drawString(value,
						(w - metrics.stringWidth(value)) / 2,
						(h - metrics.getHeight()) / 2 + metrics.getAscent());
				g2.dispose();
			}
		};
		label.setFont(NebulaDesign.fontMedium(11f));
		label.setOpaque(false);
		label.setBorder(new EmptyBorder(5, 13, 5, 13));
		label.putClientProperty("nebula.tone", tone);
		return label;
	}

	private static Graphics2D prepare(Graphics graphics)
	{
		Graphics2D g2 = (Graphics2D)graphics.create();
		NebulaDesign.prepare(g2);
		return g2;
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

	private static JComponent createAttributionBlock()
	{
		JLabel credits = themedLabel(
				"Faculty advisor Dr. Abdolreza Abhari   ·   Developer Mohsen Alavi",
				NebulaDesign.TEXT_TERTIARY,
				NebulaDesign.fontMedium(11f));
		credits.setAlignmentX(Component.LEFT_ALIGNMENT);
		return credits;
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
			return TEAL;
		}
		if (type == SchedulerLabEngine.WorkloadUnitType.PREPROCESSING_BATCH)
		{
			return BLUE;
		}
		return GREEN;
	}

	private static String typeLabel(SchedulerLabEngine.WorkloadUnitType type)
	{
		if (type == SchedulerLabEngine.WorkloadUnitType.MODEL_TRAINING_JOB)
		{
			return "Training";
		}
		if (type == SchedulerLabEngine.WorkloadUnitType.CANDIDATE_SHARD)
		{
			return "Shard";
		}
		if (type == SchedulerLabEngine.WorkloadUnitType.GRAPH_REPORTING_JOB)
		{
			return "Graph/report";
		}
		if (type == SchedulerLabEngine.WorkloadUnitType.PREPROCESSING_BATCH)
		{
			return "Preprocess";
		}
		return "Target/scoring";
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
			Graphics2D g2 = (Graphics2D)graphics.create();
			NebulaWidgets.paintCard(g2, 0, 0, getWidth(), getHeight(),
					NebulaDesign.RADIUS_CARD);
			g2.dispose();
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
			Graphics2D g2 = (Graphics2D)graphics.create();
			int w = getWidth();
			int h = getHeight();
			NebulaWidgets.paintCard(g2, 0, 0, w, h, NebulaDesign.RADIUS_CARD);
			boolean night = StudioNightTheme.isNightMode();
			java.awt.Shape oldClip = g2.getClip();
			g2.clip(new java.awt.geom.RoundRectangle2D.Float(
					0, 0, w - 1, h - 3,
					NebulaDesign.RADIUS_CARD, NebulaDesign.RADIUS_CARD));
			g2.setPaint(new java.awt.RadialGradientPaint(
					new java.awt.geom.Point2D.Float(w * 0.24f, 0f),
					Math.max(1f, w * 0.42f),
					new float[] {0f, 1f},
					new Color[] {
							alpha(BLUE, night ? 26 : 15),
							alpha(BLUE, 0)
					}));
			g2.fillRect(0, 0, w, h);
			g2.setPaint(new java.awt.RadialGradientPaint(
					new java.awt.geom.Point2D.Float(w * 0.86f, h),
					Math.max(1f, w * 0.38f),
					new float[] {0f, 1f},
					new Color[] {
							alpha(PURPLE, night ? 22 : 13),
							alpha(PURPLE, 0)
					}));
			g2.fillRect(0, 0, w, h);
			g2.setClip(oldClip);
			g2.dispose();
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
					Graphics2D g2 = (Graphics2D)graphics.create();
					NebulaWidgets.paintChevron(
							g2,
							getWidth() / 2,
							getHeight() / 2,
							7,
							MUTED);
					g2.dispose();
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
				java.awt.Rectangle bounds,
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
			Graphics2D g = prepare(graphics);
			int width = getWidth();
			int height = getHeight();
			int[] xs = new int[] {30, width / 4, width / 2, width * 3 / 4, width - 28};
			int[] ys = new int[] {height / 2, height / 2 - 12, height / 2 + 6,
					height / 2 - 8, height / 2 + 8};
			g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND,
					BasicStroke.JOIN_ROUND));
			g.setColor(alpha(NebulaDesign.currentBorderStrong(), 170));
			for (int index = 0; index < xs.length - 1; index++)
			{
				g.drawLine(xs[index], ys[index], xs[index + 1], ys[index + 1]);
			}
			for (int index = 0; index < xs.length; index++)
			{
				Color hue = index % 2 == 0 ? BLUE : GREEN;
				g.setColor(tint(hue, 0.82f));
				g.fillOval(xs[index] - 7, ys[index] - 7, 14, 14);
				g.setColor(alpha(hue, 190));
				g.setStroke(new BasicStroke(2f));
				g.drawOval(xs[index] - 7, ys[index] - 7, 14, 14);
				g.setColor(alpha(hue, 235));
				g.fillOval(xs[index] - 2, ys[index] - 2, 4, 4);
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
			Graphics2D g = prepare(graphics);
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
			int cardWidth = (width - 46) / 2;
			for (int index = 0; index < labels.length; index++)
			{
				int col = index % 2;
				int row = index / 2;
				drawMiniCard(g,
						18 + col * (cardWidth + 10),
						y + row * 68,
						cardWidth,
						60,
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
			Graphics2D g = prepare(graphics);
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
			setBackground(SURFACE);
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
			Graphics2D g = prepare(graphics);
			int width = getWidth();
			int left = 86;
			int right = width - 34;
			int top = 42;
			g.setFont(NebulaDesign.fontSection());
			g.setColor(NebulaDesign.TEXT_TERTIARY);
			g.drawString("NODE PULL TIMELINE", 18, 22);
			paintLegend(g, width);
			if (snapshot == null || snapshot.getSegments().isEmpty())
			{
				g.setFont(NebulaDesign.fontMedium(13f));
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
				g.setColor(BORDER);
				g.drawLine(left, laneY + 18, right, laneY + 18);
				g.setColor(TEXT);
				g.setFont(NebulaDesign.fontMedium(12f));
				g.drawString("Node " + node.getProfile().getNodeIndex(), 18, laneY + 16);
				g.setFont(NebulaDesign.fontBody(10f));
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
			g.setColor(NebulaDesign.TEXT_TERTIARY);
			g.setFont(NebulaDesign.fontMono(10f));
			g.drawString("0 ms", left, getHeight() - 22);
			g.drawString(formatDuration(max), right - 64, getHeight() - 22);
			g.dispose();
		}

		private void paintLegend(Graphics2D g, int width)
		{
			SchedulerLabEngine.WorkloadUnitType[] types =
					SchedulerLabEngine.WorkloadUnitType.values();
			g.setFont(NebulaDesign.fontMedium(10f));
			java.awt.FontMetrics metrics = g.getFontMetrics();
			java.util.Set<Integer> seen = new java.util.HashSet<Integer>();
			int x = width - 34;
			for (int index = types.length - 1; index >= 0; index--)
			{
				Color hue = typeColor(types[index]);
				if (!seen.add(Integer.valueOf(hue.getRGB())))
				{
					continue;
				}
				String label = typeLabel(types[index]);
				x -= metrics.stringWidth(label);
				g.setColor(MUTED);
				g.drawString(label, x, 22);
				x -= 12;
				g.setColor(hue);
				g.fillRoundRect(x, 14, 8, 8, 4, 4);
				x -= 14;
			}
			String failed = "Failed";
			x -= metrics.stringWidth(failed);
			g.setColor(MUTED);
			g.drawString(failed, x, 22);
			x -= 12;
			g.setColor(RED);
			g.fillRoundRect(x, 14, 8, 8, 4, 4);
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
		g.setFont(NebulaDesign.fontSemiBold(13f));
		g.drawString(title, x + 30, y + 21);
		g.setColor(MUTED);
		g.setFont(NebulaDesign.fontBody(11f));
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
		g.setColor(SURFACE_ALT);
		g.fillRoundRect(x, y, width, height, 10, 10);
		g.setColor(BORDER);
		g.drawRoundRect(x, y, width, height, 10, 10);
		g.setColor(alpha(accent, 46));
		g.fillRoundRect(x, y, 8, height, 8, 8);
		g.setColor(accent);
		g.fillOval(x + 16, y + 16, 10, 10);
		int textWidth = Math.max(30, width - 34 - 12);
		g.setColor(TEXT);
		g.setFont(NebulaDesign.fontSemiBold(12.5f));
		g.drawString(fitText(g, title, textWidth), x + 34, y + 25);
		g.setColor(MUTED);
		g.setFont(NebulaDesign.fontBody(10.5f));
		g.drawString(fitText(g, subtitle == null ? "" : subtitle, textWidth),
				x + 34, y + 43);
	}

	/** Measured ellipsis so painted labels never spill past their card. */
	private static String fitText(Graphics2D g, String text, int maxWidth)
	{
		java.awt.FontMetrics metrics = g.getFontMetrics();
		if (metrics.stringWidth(text) <= maxWidth)
		{
			return text;
		}
		String value = text;
		while (value.length() > 1
				&& metrics.stringWidth(value + "…") > maxWidth)
		{
			value = value.substring(0, value.length() - 1);
		}
		return value + "…";
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
		g.setColor(SURFACE_ALT);
		g.fillRoundRect(x, y, width, height, 10, 10);
		g.setColor(BORDER);
		g.drawRoundRect(x, y, width, height, 10, 10);
		g.setColor(accent);
		g.fillRoundRect(x, y, 6, height, 8, 8);
		g.setColor(TEXT);
		g.setFont(NebulaDesign.fontSemiBold(13f));
		g.drawString("Node " + profile.getNodeIndex(), x + 16, y + 22);
		g.setFont(NebulaDesign.fontMedium(11f));
		g.setColor(deep(accent));
		g.drawString(profile.shortLabel(), x + 16, y + 38);
		int done = node == null ? 0 : node.getCompletedJobs();
		int failed = node == null ? 0 : node.getFailedJobs();
		int running = node == null ? 0 : node.getRunningJobs();
		g.setFont(NebulaDesign.fontBody(10f));
		g.setColor(MUTED);
		g.drawString(done + " done | " + running
				+ " running | " + failed + " failed", x + 16, y + 55);
		g.setColor(NebulaDesign.INSET);
		g.fillRoundRect(x + 16, y + height - 16, width - 32, 5, 5, 5);
		g.setColor(accent);
		int total = node == null ? Math.max(1, done) : Math.max(
				1,
				node.getAssignedJobs());
		int bar = (int)Math.round((width - 32) * (done / (double)total));
		g.fillRoundRect(x + 16, y + height - 16, bar, 5, 5, 5);
	}
}
