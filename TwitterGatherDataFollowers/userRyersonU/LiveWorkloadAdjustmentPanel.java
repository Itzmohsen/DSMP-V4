package TwitterGatherDataFollowers.userRyersonU;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JTextArea;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

/**
 * Live control cockpit for safe scheduler boundaries. This panel is intentionally
 * read/write only for the scheduler registries; it never recomputes scores or
 * edits graph artifacts.
 */
final class LiveWorkloadAdjustmentPanel extends JPanel
		implements NodeExecutionMonitor.Listener
{
	private static final Color BACKGROUND = StudioNightTheme.adaptiveColor(
			new Color(242, 246, 250), StudioNightTheme.NIGHT_BACKGROUND);
	private static final Color SURFACE = StudioNightTheme.adaptiveColor(
			Color.WHITE, StudioNightTheme.NIGHT_SURFACE);
	private static final Color SURFACE_ALT = StudioNightTheme.adaptiveColor(
			new Color(248, 251, 253), StudioNightTheme.NIGHT_SURFACE_ALT);
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

	private final ControllerAgentGui owner;
	private final HeaderSignalPanel headerSignalPanel = new HeaderSignalPanel();
	private final JLabel modeMetric = metricValue("--");
	private final JLabel policyMetric = metricValue("--");
	private final JLabel queueMetric = metricValue("--");
	private final JLabel nodeMetric = metricValue("--");
	private final JLabel statusPill = new JLabel("READY");
	private final JLabel statusLabel =
			new JLabel("Start a simulator run to adjust pending live queue work.");
	private final JComboBox<SchedulerLabEngine.SchedulingPolicy> policyBox =
			new JComboBox<SchedulerLabEngine.SchedulingPolicy>(
					SchedulerLabEngine.SchedulingPolicy.values());
	private final JComboBox<PacingChoice> pacingBox =
			new JComboBox<PacingChoice>(PacingChoice.values());
	private final JButton applyPolicyButton =
			button("Apply policy to remaining work", BLUE, Color.WHITE);
	private final JButton applyPacingButton =
			button("Set action window", AMBER, Color.WHITE);
	private final JButton armInteractiveButton =
			button("Arm interactive run", GREEN, Color.WHITE);
	private final JButton restoreDefaultButton =
			button("Restore DSMP default", SURFACE_ALT, TEXT);
	private final JButton resetButton =
			button("Reset live controls", SURFACE_ALT, TEXT);
	private final JButton capacityButton =
			button("Node capacity", SURFACE_ALT, TEXT);
	private final JLabel phasePill = new JLabel("READY");
	private final JLabel phaseTitle = new JLabel("Arm a live queue before running.");
	private final JLabel phaseDetail = new JLabel(
			"Choose an action window, arm the run, then start the simulator and use node cards while work is still waiting.");
	private final QueueFlowPanel queueFlowPanel = new QueueFlowPanel();
	private final JPanel nodeGrid = new JPanel(new GridLayout(0, 2, 12, 12));
	private final JTextArea eventLog = new JTextArea();
	private final Map<Integer,LiveNodeCard> nodeCards =
			new LinkedHashMap<Integer,LiveNodeCard>();
	private final Timer animationTimer;

	private NodeExecutionMonitor.Snapshot snapshot =
			NodeExecutionMonitor.snapshot();
	private RecommendationWorkQueueRegistry.QueueSnapshot queueSnapshot =
			emptyQueueSnapshot();
	private LiveWorkloadAdjustmentRegistry.Snapshot liveSnapshot =
			LiveWorkloadAdjustmentRegistry.snapshot("");
	private RecommendationSchedulerPlan plan =
			RecommendationSchedulerRegistry.snapshot();
	private long now = System.currentTimeMillis();
	private boolean updatingControls;

	LiveWorkloadAdjustmentPanel(ControllerAgentGui owner)
	{
		super(new BorderLayout(0, 14));
		this.owner = owner;
		setBackground(BACKGROUND);
		setBorder(new EmptyBorder(14, 14, 14, 14));

		add(createHeader(), BorderLayout.NORTH);
		add(createWorkspace(), BorderLayout.CENTER);
		add(createStatusBar(), BorderLayout.SOUTH);
		installActions();

		animationTimer = new Timer(28, new ActionListener()
		{
			public void actionPerformed(ActionEvent event)
			{
				now = System.currentTimeMillis();
				refreshSnapshots(false);
				headerSignalPanel.advance();
				queueFlowPanel.advance();
				for (LiveNodeCard card : nodeCards.values())
				{
					card.advance();
				}
			}
		});
		animationTimer.setCoalesce(true);
	}

	public void addNotify()
	{
		super.addNotify();
		NodeExecutionMonitor.addListener(this);
		animationTimer.start();
		refreshFromSimulator();
	}

	public void removeNotify()
	{
		animationTimer.stop();
		NodeExecutionMonitor.removeListener(this);
		super.removeNotify();
	}

	public void onSnapshot(final NodeExecutionMonitor.Snapshot newSnapshot)
	{
		if (newSnapshot == null)
		{
			return;
		}
		if (SwingUtilities.isEventDispatchThread())
		{
			snapshot = newSnapshot;
			refreshSnapshots(true);
		}
		else
		{
			SwingUtilities.invokeLater(new Runnable()
			{
				public void run()
				{
					snapshot = newSnapshot;
					refreshSnapshots(true);
				}
			});
		}
	}

	void refreshFromSimulator()
	{
		refreshSnapshots(true);
	}

	private JComponent createHeader()
	{
		JPanel header = surface(new BorderLayout(18, 0));
		header.setBorder(new EmptyBorder(18, 20, 18, 20));

		JPanel titleStack = new JPanel();
		titleStack.setOpaque(false);
		titleStack.setLayout(new BoxLayout(titleStack, BoxLayout.Y_AXIS));
		JLabel title = new JLabel("Live Workload Adjustment");
		title.setFont(font(Font.BOLD, 28));
		title.setForeground(TEXT);
		JLabel subtitle = new JLabel(
				"Pause, drain, redirect, throttle, and switch policies while safe queue work is still pending.");
		subtitle.setFont(font(Font.PLAIN, 12));
		subtitle.setForeground(MUTED);
		titleStack.add(title);
		titleStack.add(Box.createRigidArea(new Dimension(0, 4)));
		titleStack.add(subtitle);
		titleStack.add(createAttributionBlock());

		headerSignalPanel.setPreferredSize(new Dimension(650, 88));
		header.add(titleStack, BorderLayout.WEST);
		header.add(headerSignalPanel, BorderLayout.CENTER);
		return header;
	}

	private JComponent createWorkspace()
	{
		JPanel root = new JPanel(new BorderLayout(12, 12));
		root.setOpaque(false);
		root.add(createControlStrip(), BorderLayout.NORTH);

		JPanel body = new JPanel(new GridBagLayout());
		body.setOpaque(false);
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.insets = new Insets(0, 0, 0, 12);
		gbc.fill = GridBagConstraints.BOTH;
		gbc.gridx = 0;
		gbc.gridy = 0;
		gbc.weightx = 0.52;
		gbc.weighty = 1.0;
		body.add(createNodeControlPanel(), gbc);

		gbc.gridx = 1;
		gbc.insets = new Insets(0, 0, 0, 0);
		gbc.weightx = 0.48;
		body.add(createQueuePanel(), gbc);
		root.add(body, BorderLayout.CENTER);
		return root;
	}

	private JComponent createControlStrip()
	{
		JPanel panel = surface(new BorderLayout(12, 12));
		panel.setBorder(new EmptyBorder(12, 14, 12, 14));

		JPanel metrics = new JPanel(new GridLayout(1, 4, 10, 0));
		metrics.setOpaque(false);
		metrics.add(metricCard("MODE", modeMetric, BLUE));
		metrics.add(metricCard("POLICY", policyMetric, PURPLE));
		metrics.add(metricCard("QUEUE", queueMetric, GREEN));
		metrics.add(metricCard("NODES", nodeMetric, AMBER));

		pacingBox.setToolTipText(
				"Adds a short pause after each future work claim so live controls can be tested before the queue is empty.");
		applyPacingButton.setToolTipText(
				"Applies the action window to the active run and to the next run.");
		armInteractiveButton.setToolTipText(
				"Prepares the next simulator run for live workload adjustment with an open queue window.");
		restoreDefaultButton.setToolTipText(
				"Turns off live scheduling and returns the simulator to original DSMP assignment behavior.");

		JPanel setup = new JPanel(new BorderLayout(10, 10));
		setup.setOpaque(false);
		setup.add(createGuidanceCard(), BorderLayout.NORTH);

		JPanel controls = new JPanel(new GridLayout(2, 4, 10, 8));
		controls.setOpaque(false);
		controls.add(control("Remaining-work policy", policyBox));
		controls.add(armInteractiveButton);
		controls.add(control("Action window per claim", pacingBox));
		controls.add(applyPacingButton);
		controls.add(applyPolicyButton);
		controls.add(capacityButton);
		controls.add(resetButton);
		controls.add(restoreDefaultButton);
		setup.add(controls, BorderLayout.CENTER);

		panel.add(metrics, BorderLayout.CENTER);
		panel.add(setup, BorderLayout.SOUTH);
		return panel;
	}

	private JComponent createGuidanceCard()
	{
		JPanel card = new JPanel(new BorderLayout(10, 0));
		card.setOpaque(true);
		card.setBackground(SURFACE_ALT);
		card.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(BORDER),
				new EmptyBorder(10, 12, 10, 12)));
		phasePill.setOpaque(true);
		phasePill.setHorizontalAlignment(SwingConstants.CENTER);
		phasePill.setFont(font(Font.BOLD, 11));
		phasePill.setBorder(new EmptyBorder(5, 12, 5, 12));
		JPanel text = new JPanel();
		text.setOpaque(false);
		text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
		phaseTitle.setFont(font(Font.BOLD, 15));
		phaseTitle.setForeground(TEXT);
		phaseDetail.setFont(font(Font.PLAIN, 12));
		phaseDetail.setForeground(MUTED);
		text.add(phaseTitle);
		text.add(Box.createRigidArea(new Dimension(0, 3)));
		text.add(phaseDetail);
		card.add(phasePill, BorderLayout.WEST);
		card.add(text, BorderLayout.CENTER);
		return card;
	}

	private JComponent createNodeControlPanel()
	{
		JPanel panel = section("Live Node Controls");
		JLabel help = new JLabel(
				"Pause stops future claims; drain returns safe-boundary active work to the queue and moves remaining work to another node.");
		help.setFont(font(Font.BOLD, 11));
		help.setForeground(MUTED);
		help.setBorder(new EmptyBorder(0, 2, 8, 2));
		panel.add(help, BorderLayout.NORTH);

		nodeGrid.setOpaque(false);
		JPanel holder = new JPanel(new BorderLayout());
		holder.setBackground(SURFACE_ALT);
		holder.add(nodeGrid, BorderLayout.NORTH);

		JScrollPane scrollPane = new JScrollPane(holder);
		scrollPane.setBorder(BorderFactory.createEmptyBorder());
		scrollPane.getViewport().setBackground(SURFACE_ALT);
		scrollPane.setHorizontalScrollBarPolicy(
				ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		scrollPane.getVerticalScrollBar().setUnitIncrement(18);
		panel.add(scrollPane, BorderLayout.CENTER);
		return panel;
	}

	private JComponent createQueuePanel()
	{
		JPanel panel = section("Live Queue And Control Trace");
		JPanel body = new JPanel(new BorderLayout(0, 12));
		body.setOpaque(false);
		queueFlowPanel.setPreferredSize(new Dimension(620, 440));
		body.add(queueFlowPanel, BorderLayout.NORTH);

		eventLog.setEditable(false);
		eventLog.setLineWrap(false);
		eventLog.setWrapStyleWord(false);
		eventLog.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
		eventLog.setForeground(new Color(230, 236, 247));
		eventLog.setBackground(new Color(18, 24, 38));
		eventLog.setBorder(new EmptyBorder(12, 12, 12, 12));
		JScrollPane logScroll = new JScrollPane(eventLog);
		logScroll.setBorder(BorderFactory.createEmptyBorder());
		logScroll.setPreferredSize(new Dimension(620, 260));
		body.add(logScroll, BorderLayout.CENTER);
		panel.add(body, BorderLayout.CENTER);
		return panel;
	}

	private JComponent createStatusBar()
	{
		JPanel bar = surface(new BorderLayout(10, 0));
		bar.setBorder(new EmptyBorder(8, 12, 8, 12));
		statusPill.setOpaque(true);
		statusPill.setHorizontalAlignment(SwingConstants.CENTER);
		statusPill.setBorder(new EmptyBorder(4, 14, 4, 14));
		statusPill.setFont(font(Font.BOLD, 11));
		statusLabel.setFont(font(Font.BOLD, 12));
		statusLabel.setForeground(TEXT);
		bar.add(statusPill, BorderLayout.WEST);
		bar.add(statusLabel, BorderLayout.CENTER);
		setStatus("READY",
				"Start a simulator run to adjust pending live queue work.",
				StatusTone.READY);
		return bar;
	}

	private void installActions()
	{
		armInteractiveButton.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent event)
			{
				armInteractiveRun();
			}
		});
		applyPolicyButton.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent event)
			{
				String blockReason = liveAdjustmentBlockReason();
				if (blockReason.length() > 0)
				{
					setStatus("NO QUEUE", blockReason, StatusTone.ERROR);
					return;
				}
				SchedulerLabEngine.SchedulingPolicy policy =
						(SchedulerLabEngine.SchedulingPolicy)
								policyBox.getSelectedItem();
				LiveWorkloadAdjustmentRegistry.setPolicyOverride(
						activeRunId(),
						policy);
				setStatus("LIVE",
						"Remaining unclaimed queue work now uses "
						+ policy.getLabel() + ".",
						StatusTone.READY);
				refreshSnapshots(true);
			}
			});
			pacingBox.addActionListener(new ActionListener()
			{
				public void actionPerformed(ActionEvent event)
				{
					if (updatingControls)
					{
						return;
					}
					applySelectedPacingChoice(false);
				}
			});
			applyPacingButton.addActionListener(new ActionListener()
			{
				public void actionPerformed(ActionEvent event)
				{
					applySelectedPacingChoice(true);
				}
			});
		resetButton.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent event)
			{
				LiveWorkloadAdjustmentRegistry.resetRunControls(activeRunId());
				LiveWorkloadAdjustmentRegistry.setNextRunPacingDelayMs(0);
				setStatus("RESET",
						"Live workload overrides are cleared for the current run.",
						StatusTone.READY);
				refreshSnapshots(true);
			}
		});
		capacityButton.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent event)
			{
				int nodes = effectiveNodeCount();
				int heap = owner.currentHeapSetting();
				NodeCapacityRegistry.configureForNodeCount(nodes, heap);
				if (NodeCapacitySettingsDialog.showDialog(owner, nodes, heap))
				{
					NodeExecutionMonitor.refreshCapacityProfiles();
					setStatus("CAPACITY",
							"Node capacity contract updated. Future queue claims use the new weights.",
							StatusTone.READY);
					refreshSnapshots(true);
				}
			}
			});
		restoreDefaultButton.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent event)
			{
				restoreDsmpDefault();
			}
		});
		}

	private void armInteractiveRun()
	{
		if (!supportsLiveQueueForCurrentAlgorithm())
		{
			setStatus("NOT SUPPORTED",
					"Live workload control is available for safe-boundary simulator runs.",
					StatusTone.ERROR);
			refreshSnapshots(true);
			return;
		}
		int nodes = effectiveNodeCount();
		int heap = owner.currentHeapSetting();
		NodeCapacityRegistry.configureForNodeCount(nodes, heap);
		SchedulerLabEngine.SchedulingPolicy policy =
				(SchedulerLabEngine.SchedulingPolicy)policyBox.getSelectedItem();
		if (policy == null)
		{
			policy = SchedulerLabEngine.SchedulingPolicy.CAPACITY_WEIGHTED;
		}
		RecommendationSchedulerRegistry.apply(
				RecommendationSchedulerPlan.Mode.SCHEDULING_EXPERIMENT,
				policy,
				nodes,
				heap,
				"Live workload adjustment armed an interactive queue run.");
		PacingChoice choice = (PacingChoice)pacingBox.getSelectedItem();
		if (choice == null || choice.delayMs() <= 0)
		{
			choice = PacingChoice.TRAINING;
			updatingControls = true;
			pacingBox.setSelectedItem(choice);
			updatingControls = false;
		}
		LiveWorkloadAdjustmentRegistry.setNextRunPacingDelayMs(
				choice.delayMs());
		if (hasActiveRun())
		{
			LiveWorkloadAdjustmentRegistry.resetRunControls(activeRunId());
		}
		NodeExecutionMonitor.refreshCapacityProfiles();
		setStatus("ARMED",
				"Next simulator run will pause each queued work item for "
						+ choice.label()
						+ ". Start Run Simulation, then pause, redirect, or throttle nodes while safe-boundary work is waiting.",
				StatusTone.READY);
		refreshSnapshots(true);
	}

	private void restoreDsmpDefault()
	{
		int nodes = effectiveNodeCount();
		int heap = owner.currentHeapSetting();
		RecommendationSchedulerRegistry.restoreDefault(nodes, heap);
		LiveWorkloadAdjustmentRegistry.resetRunControls(activeRunId());
		LiveWorkloadAdjustmentRegistry.setNextRunPacingDelayMs(0);
		setStatus("DEFAULT",
				"Live scheduling is off. The simulator will use original DSMP assignment behavior.",
				StatusTone.READY);
		refreshSnapshots(true);
	}

	private void applySelectedPacingChoice(boolean explicitButton)
	{
		PacingChoice choice = (PacingChoice)pacingBox.getSelectedItem();
		if (choice == null)
		{
			choice = PacingChoice.OFF;
		}
		LiveWorkloadAdjustmentRegistry.setNextRunPacingDelayMs(
				choice.delayMs());
		if (hasActiveRun())
		{
			LiveWorkloadAdjustmentRegistry.setPacingDelayMs(
					activeRunId(),
					choice.delayMs());
		}
		if (choice.delayMs() <= 0)
		{
			setStatus("WINDOW OFF",
					"Future queue claims continue immediately.",
					StatusTone.READY);
		}
		else if (hasActiveRun() && queueSnapshot.getWaitingCount() > 0)
		{
			setStatus("ACTION WINDOW",
					"Future work claims pause for "
							+ choice.label()
							+ " before execution, giving you time to pause or redirect nodes.",
					StatusTone.READY);
		}
		else
		{
			setStatus(
					explicitButton ? "ACTION WINDOW" : "WINDOW SET",
					"Next run will pause each claimed work item for "
							+ choice.label()
							+ " so live controls are visible.",
					StatusTone.READY);
		}
		refreshSnapshots(true);
	}

	private void refreshSnapshots(boolean rebuildCards)
	{
		now = System.currentTimeMillis();
		plan = RecommendationSchedulerRegistry.snapshot();
		String runId = activeRunId();
		liveSnapshot = LiveWorkloadAdjustmentRegistry.snapshot(runId);
		queueSnapshot = currentQueueSnapshot();

		SchedulerLabEngine.SchedulingPolicy displayPolicy =
				liveSnapshot.getPolicyOverride() == null
						? plan.getPolicy()
						: liveSnapshot.getPolicyOverride();
		updatingControls = true;
		policyBox.setSelectedItem(displayPolicy);
		int pacingDelay = shouldShowNextRunPacing()
				? LiveWorkloadAdjustmentRegistry.nextRunPacingDelayMs()
				: liveSnapshot.getPacingDelayMs();
		if (!pacingBox.isPopupVisible())
		{
			pacingBox.setSelectedItem(PacingChoice.forDelay(pacingDelay));
		}
		updatingControls = false;

		modeMetric.setText(plan.getMode().getLabel());
		policyMetric.setText(displayPolicy.getLabel());
		queueMetric.setText(queueSnapshot.getCompletedCount() + "/"
				+ queueSnapshot.getTotalShards() + " done");
		nodeMetric.setText(effectiveNodeCount() + " live node"
				+ (effectiveNodeCount() == 1 ? "" : "s"));
		updateGuidanceState();

		headerSignalPanel.setState(currentAlgorithm(), queueSnapshot);
		queueFlowPanel.setContext(
				queueSnapshot,
				liveSnapshot,
				plan,
				currentAlgorithm(),
				effectiveNodeCount(),
				now);
		rebuildNodeCardsIfNeeded(rebuildCards);
		for (LiveNodeCard card : nodeCards.values())
		{
			card.refresh(
					nodeSnapshot(card.nodeIndex),
					queueSnapshot,
					liveSnapshot,
					isLiveQueueAdjustable());
		}
		eventLog.setText(buildEventLog());
		eventLog.setCaretPosition(0);
		repaint();
	}

	private void updateGuidanceState()
	{
		boolean supports = supportsLiveQueueForCurrentAlgorithm();
		boolean armed = plan.isActive()
				&& LiveWorkloadAdjustmentRegistry.nextRunPacingDelayMs() > 0
				&& supports;
		boolean active = hasActiveRun();
		boolean waiting = queueSnapshot.getWaitingCount() > 0;
		boolean inFlight = queueSnapshot.getActiveCount() > 0;
		boolean finished = queueSnapshot.getTotalShards() > 0
				&& !waiting
				&& !inFlight;
		if (!supports)
		{
			setPhase(
					"UNSUPPORTED",
					"Choose a simulator algorithm.",
					"This tab controls queued work where available and phase-boundary pauses for node-local stages.",
					StatusTone.ERROR);
		}
		else if (active && waiting)
		{
			setPhase(
					"LIVE",
					"Queue is open: node controls matter right now.",
					queueSnapshot.getWaitingCount()
							+ " work item(s) are still unclaimed. Pause, drain, redirect, throttle, or switch policy before they are claimed.",
					StatusTone.READY);
		}
		else if (active && inFlight)
		{
			setPhase(
					"CLOSING",
					"All future work has been claimed.",
					"Current work is already inside nodes. Those items finish normally; arm an action window before the next run for more control time.",
					StatusTone.READY);
		}
		else if (active && queueSnapshot.getTotalShards() <= 0)
		{
			setPhase(
					"PHASE CONTROL",
					"No queue is open yet; phase controls still matter.",
					"Pause or mark a node unavailable now and it will stop at the next safe boundary before preprocessing, feature building, model execution, or publishing.",
					StatusTone.READY);
		}
		else if (active && finished && armed)
		{
			setPhase(
					"PHASE CONTROL",
					"Current queue is closed; phase controls still matter.",
					"Pause or mark a node unavailable now and it will stop at the next safe preprocessing, feature, model, or publish boundary. The next run is also armed for "
							+ PacingChoice.formatDelay(
									LiveWorkloadAdjustmentRegistry
											.nextRunPacingDelayMs())
							+ " claim windows.",
					StatusTone.READY);
		}
		else if (active && finished)
		{
			setPhase(
					"PHASE CONTROL",
					"Current queue is closed; phase controls still matter.",
					"No unclaimed queue work remains, but node-local stages can still be paused at their next safe boundary.",
					StatusTone.READY);
		}
		else if (armed)
		{
			setPhase(
					"ARMED",
					"Next run will open a live workload queue.",
					"Start Run Simulation. Each queued work item pauses for "
							+ PacingChoice.formatDelay(
									LiveWorkloadAdjustmentRegistry
											.nextRunPacingDelayMs())
							+ " after claim so you can act before the queue closes.",
					StatusTone.READY);
		}
		else
		{
			setPhase(
					"READY",
					"Arm a live queue before running.",
					"Use Arm interactive run to turn on scheduling experiment mode and a visible action window, then start the simulator.",
					StatusTone.READY);
		}

		armInteractiveButton.setEnabled(supports);
		applyPolicyButton.setEnabled(isLiveQueueAdjustable());
		applyPacingButton.setEnabled(supports);
		restoreDefaultButton.setEnabled(true);
		resetButton.setEnabled(true);
	}

	private boolean shouldShowNextRunPacing()
	{
		if (!hasActiveRun())
		{
			return true;
		}
		return queueSnapshot.getTotalShards() > 0
				&& queueSnapshot.getWaitingCount() <= 0
				&& queueSnapshot.getActiveCount() <= 0
				&& LiveWorkloadAdjustmentRegistry.nextRunPacingDelayMs() > 0;
	}

	private void setPhase(
			String pill,
			String title,
			String detail,
			StatusTone tone)
	{
		phasePill.setText(pill == null ? "READY" : pill);
		phaseTitle.setText(title == null ? "" : title);
		phaseDetail.setText(detail == null ? "" : detail);
		phasePill.setBackground(tone == StatusTone.ERROR
				? themed(new Color(255, 232, 229), StudioNightTheme.NIGHT_PANEL_INK)
				: themed(new Color(224, 244, 233), StudioNightTheme.NIGHT_PANEL_INK));
		phasePill.setForeground(tone == StatusTone.ERROR ? RED : GREEN);
		phasePill.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(tone == StatusTone.ERROR
						? themed(new Color(241, 169, 159), RED)
						: themed(new Color(174, 221, 194), GREEN)),
				new EmptyBorder(5, 12, 5, 12)));
	}

	private void rebuildNodeCardsIfNeeded(boolean force)
	{
		int nodeCount = effectiveNodeCount();
		if (!force && nodeCards.size() == nodeCount)
		{
			return;
		}
		nodeCards.clear();
		nodeGrid.removeAll();
		int columns = nodeCount <= 2 ? 1 : 2;
		nodeGrid.setLayout(new GridLayout(0, columns, 12, 12));
		for (int node = 1; node <= nodeCount; node++)
		{
			LiveNodeCard card = new LiveNodeCard(node);
			nodeCards.put(Integer.valueOf(node), card);
			nodeGrid.add(card);
		}
		nodeGrid.revalidate();
		nodeGrid.repaint();
	}

	private NodeExecutionMonitor.NodeSnapshot nodeSnapshot(int nodeIndex)
	{
		if (snapshot != null)
		{
			for (NodeExecutionMonitor.NodeSnapshot node : snapshot.getNodes())
			{
				if (node.getNodeIndex() == nodeIndex)
				{
					return node;
				}
			}
		}
		return null;
	}

	private RecommendationWorkQueueRegistry.QueueSnapshot currentQueueSnapshot()
	{
		int nodeCount = effectiveNodeCount();
		int algorithm = currentAlgorithm();
		RecommendationWorkQueueRegistry.QueueSnapshot scoringQueue =
				emptyQueueSnapshot();
		if (algorithm == ControllerAgent.Doc2Vec)
		{
			int shardCount = NodeCapacityScheduler.virtualCandidateShardCount(
					nodeCount,
					currentTargetCount());
			scoringQueue = RecommendationWorkQueueRegistry.snapshotDoc2VecQueue(
					activeRunId(),
					nodeCount,
					shardCount);
		}
		else if (algorithm == ControllerAgent.CommonNeighbors
				|| algorithm == ControllerAgent.COS_SIM)
		{
			int shardCount = NodeCapacityScheduler.virtualCandidateShardCount(
					nodeCount,
					currentCandidateCount());
			String algorithmKey = algorithm == ControllerAgent.CommonNeighbors
					? "CommonNeighbors" : "Similarity";
			scoringQueue = RecommendationWorkQueueRegistry.snapshotExperimentalCandidateQueue(
					activeRunId(),
					algorithmKey,
					nodeCount,
					shardCount);
		}
		else if (RecommendationModeSupport.isSvmFamilyAlgorithm(algorithm))
		{
			scoringQueue = RecommendationWorkQueueRegistry.snapshotTargetUserQueue(
					activeRunId(),
					RecommendationModeSupport.algorithmName(algorithm),
					nodeCount,
					currentTargetCount());
		}
		if (scoringQueue.getTotalShards() > 0)
		{
			return scoringQueue;
		}
		RecommendationWorkQueueRegistry.QueueSnapshot inputQueue =
				RecommendationWorkQueueRegistry.snapshotInputBatchQueue(
						activeRunId(),
						nodeCount);
		if (inputQueue.getTotalShards() > 0)
		{
			return inputQueue;
		}
		return scoringQueue;
	}

	private boolean isLiveQueueActive()
	{
		if (!isLiveQueueSupported())
		{
			return false;
		}
		return hasActiveRun()
				&& (queueSnapshot.getWaitingCount() > 0
						|| queueSnapshot.getActiveCount() > 0);
	}

	private boolean isLiveQueueAdjustable()
	{
		return hasActiveRun()
				&& supportsLiveQueueForCurrentAlgorithm();
	}

	private boolean isLiveQueueSupported()
	{
		int algorithm = currentAlgorithm();
		if (queueSnapshot.getTotalShards() <= 0)
		{
			return hasActiveRun() && supportsLiveQueueForCurrentAlgorithm();
		}
		if (isInputQueueSnapshot())
		{
			return true;
		}
		if (algorithm == ControllerAgent.Doc2Vec && plan.isActive())
		{
			return queueSnapshot.getTotalShards() > effectiveNodeCount();
		}
		if (plan.allowsExperimentalSideEffects()
				&& (algorithm == ControllerAgent.COS_SIM
						|| algorithm == ControllerAgent.CommonNeighbors))
		{
			return queueSnapshot.getTotalShards() > effectiveNodeCount();
		}
		return plan.allowsExperimentalSideEffects()
				&& RecommendationModeSupport.isSvmFamilyAlgorithm(algorithm);
	}

	private boolean supportsLiveQueueForCurrentAlgorithm()
	{
		return true;
	}

	private boolean isInputQueueSnapshot()
	{
		String key = queueSnapshot == null ? "" : queueSnapshot.getKey();
		return key.indexOf("|input-batch|") >= 0;
	}

	private String liveAdjustmentBlockReason()
	{
		if (hasStartedRun() && isRunTerminal())
		{
			return "The last simulator run is finished. Arm an interactive run and start a new simulation before using live controls.";
		}
		if (!hasActiveRun())
		{
			return "Start a simulator run first. Live controls act only on an active run.";
		}
		if (!supportsLiveQueueForCurrentAlgorithm())
		{
			return "This algorithm/mode has no live safe-boundary controls.";
		}
		return "";
	}

	private boolean hasActiveRun()
	{
		return hasStartedRun() && !isRunTerminal();
	}

	private boolean hasStartedRun()
	{
		return activeRunId().trim().length() > 0
				&& snapshot != null
				&& snapshot.getRunStartedAt() > 0L;
	}

	private boolean isRunTerminal()
	{
		if (snapshot == null || snapshot.getNodes().isEmpty())
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

	private String activeRunId()
	{
		if (snapshot != null && snapshot.getRunId() != null
				&& snapshot.getRunId().trim().length() > 0)
		{
			return snapshot.getRunId().trim();
		}
		return RecommendationSchedulerRegistry.activeRunId();
	}

	private int effectiveNodeCount()
	{
		if (snapshot != null && !snapshot.getNodes().isEmpty())
		{
			return Math.max(1, snapshot.getNodes().size());
		}
		return Math.max(1, owner.currentNodeCountSetting());
	}

	private int currentTargetCount()
	{
		return Math.max(1, owner.getRecommendationBatchUserCount());
	}

	private int currentCandidateCount()
	{
		return Math.max(1, owner.getCandidateUserCountEstimate());
	}

	private int currentAlgorithm()
	{
		return owner.algorithmSelectionBox == null
				? ControllerAgent.K_MEANS
				: owner.algorithmSelectionBox.getSelectedIndex();
	}

	private boolean canDisableNode(int nodeIndex)
	{
		if (queueSnapshot.getWaitingCount() <= 0)
		{
			return true;
		}
		int eligible = 0;
		for (int node = 1; node <= effectiveNodeCount(); node++)
		{
			if (node == nodeIndex)
			{
				continue;
			}
			LiveWorkloadAdjustmentRegistry.NodeControl control =
					liveSnapshot.controlForNode(node);
			if (!control.isPaused() && !control.isUnavailable())
			{
				eligible++;
			}
		}
		return eligible > 0;
	}

	private void pauseNode(int nodeIndex, boolean paused)
	{
		String blockReason = liveAdjustmentBlockReason();
		if (blockReason.length() > 0)
		{
			setStatus("NO QUEUE", blockReason, StatusTone.ERROR);
			return;
		}
		if (paused && !canDisableNode(nodeIndex))
		{
			setStatus("PROTECTED",
					"At least one node must remain eligible while queue work is waiting.",
					StatusTone.ERROR);
			return;
		}
		LiveWorkloadAdjustmentRegistry.setPaused(activeRunId(), nodeIndex, paused);
		setStatus("LIVE",
				"Node " + nodeIndex
				+ (paused
						? " will stop claiming future work and wait at phase boundaries."
						: " can claim future work and pass phase boundaries again."),
				StatusTone.READY);
		refreshSnapshots(true);
	}

	private void setNodeUnavailable(int nodeIndex, boolean unavailable)
	{
		String blockReason = liveAdjustmentBlockReason();
		if (blockReason.length() > 0)
		{
			setStatus("NO QUEUE", blockReason, StatusTone.ERROR);
			return;
		}
		if (unavailable && !canDisableNode(nodeIndex))
		{
			setStatus("PROTECTED",
					"At least one node must remain eligible while queue work is waiting.",
					StatusTone.ERROR);
			return;
		}
		LiveWorkloadAdjustmentRegistry.setUnavailable(
				activeRunId(),
				nodeIndex,
				unavailable);
		setStatus("LIVE",
				"Node " + nodeIndex
				+ (unavailable
						? " is unavailable for future claims and phase boundaries."
						: " is available for future claims and phase boundaries."),
				StatusTone.READY);
		refreshSnapshots(true);
	}

	private void redirectPendingToNode(int nodeIndex)
	{
		String blockReason = liveAdjustmentBlockReason();
		if (blockReason.length() > 0)
		{
			setStatus("NO QUEUE", blockReason, StatusTone.ERROR);
			return;
		}
		LiveWorkloadAdjustmentRegistry.NodeControl control =
				liveSnapshot.controlForNode(nodeIndex);
		if (control.isPaused() || control.isUnavailable())
		{
			setStatus("BLOCKED",
					"Node " + nodeIndex
					+ " is " + control.statusLabel()
					+ "; make it available before redirecting work.",
					StatusTone.ERROR);
			return;
		}
		int waiting = queueSnapshot.getWaitingCount();
		if (waiting <= 0)
		{
			setStatus("DONE",
					"No unclaimed queue work remains to move.",
					StatusTone.ERROR);
			return;
		}
		LiveWorkloadAdjustmentRegistry.redirectPendingToNode(
				activeRunId(),
				nodeIndex,
				waiting);
		setStatus("REDIRECT",
				"Next " + waiting
				+ " pending work item(s) prefer Node " + nodeIndex + ".",
				StatusTone.READY);
		refreshSnapshots(true);
	}

	private void drainNodeToBestAvailable(int nodeIndex)
	{
		String blockReason = liveAdjustmentBlockReason();
		if (blockReason.length() > 0)
		{
			setStatus("NO QUEUE", blockReason, StatusTone.ERROR);
			return;
		}
		if (queueSnapshot.getWaitingCount() <= 0
				&& queueSnapshot.getActiveCount() <= 0)
		{
			setStatus("NO QUEUE",
					"No movable queued work is open right now. Use Pause or Unavailable to hold this node at its next safe phase boundary.",
					StatusTone.ERROR);
			return;
		}
		if (!canDisableNode(nodeIndex))
		{
			setStatus("PROTECTED",
					"At least one other node must remain eligible to receive drained work.",
					StatusTone.ERROR);
			return;
		}
		int targetNode = bestDrainTargetFor(nodeIndex);
		if (targetNode <= 0)
		{
			setStatus("BLOCKED",
					"No eligible receiving node is available for drain.",
					StatusTone.ERROR);
			return;
		}
		List<Integer> active = queueSnapshot.getActiveByNode().get(
				Integer.valueOf(nodeIndex));
		int activeCount = active == null ? 0 : active.size();
		int pendingCount = Math.max(
				1,
				queueSnapshot.getWaitingCount() + activeCount);
		int moved = LiveWorkloadAdjustmentRegistry.drainNodeTo(
				activeRunId(),
				nodeIndex,
				targetNode,
				pendingCount);
		setStatus("DRAINING",
				"Node " + nodeIndex + " is draining to Node " + targetNode
				+ ". " + moved + " active safe-boundary work item"
				+ (moved == 1 ? "" : "s")
				+ " returned to the waiting queue.",
				StatusTone.READY);
		refreshSnapshots(true);
	}

	private int bestDrainTargetFor(int sourceNode)
	{
		int bestNode = 0;
		double bestWeight = -1.0;
		int nodes = effectiveNodeCount();
		for (int node = 1; node <= nodes; node++)
		{
			if (node == sourceNode)
			{
				continue;
			}
			LiveWorkloadAdjustmentRegistry.NodeControl control =
					liveSnapshot.controlForNode(node);
			if (control.isPaused() || control.isUnavailable())
			{
				continue;
			}
			double weight = NodeCapacityScheduler.schedulingWeight(
					NodeCapacityRegistry.profileForNode(node))
					* control.getCapacityScale();
			if (weight > bestWeight)
			{
				bestWeight = weight;
				bestNode = node;
			}
		}
		return bestNode;
	}

	private void setCapacityScale(int nodeIndex, double scale)
	{
		String blockReason = liveAdjustmentBlockReason();
		if (blockReason.length() > 0)
		{
			setStatus("NO QUEUE", blockReason, StatusTone.ERROR);
			refreshSnapshots(false);
			return;
		}
		LiveWorkloadAdjustmentRegistry.setCapacityScale(
				activeRunId(),
				nodeIndex,
				scale);
		setStatus("LIVE",
				"Node " + nodeIndex + " future-claim capacity set to "
				+ Math.round(scale * 100.0) + "%.",
				StatusTone.READY);
		refreshSnapshots(false);
	}

	private String buildEventLog()
	{
		StringBuilder builder = new StringBuilder();
		builder.append("Live workload controls are applied to safe-boundary queued work and phase checkpoints.\n");
		builder.append("Run: ").append(shortText(activeRunId(), 38)).append('\n');
		builder.append("Algorithm: ")
				.append(RecommendationModeSupport.algorithmName(currentAlgorithm()))
				.append(" | plan: ").append(plan.summary()).append('\n');
		builder.append("Queue: ").append(queueSnapshot.getWaitingCount())
				.append(" waiting, ")
				.append(queueSnapshot.getActiveCount()).append(" active, ")
				.append(queueSnapshot.getCompletedCount()).append("/")
				.append(queueSnapshot.getTotalShards()).append(" complete\n\n");
		builder.append("ACTION WINDOW\n");
		int effectivePacing = shouldShowNextRunPacing()
				? LiveWorkloadAdjustmentRegistry.nextRunPacingDelayMs()
				: liveSnapshot.getPacingDelayMs();
		if (effectivePacing > 0)
		{
			builder.append("  enabled: ")
					.append(PacingChoice.formatDelay(
							effectivePacing))
					.append(hasActiveRun()
							? " pause after each future work claim\n"
							: " pause is armed for the next simulator run\n");
		}
		else
		{
			builder.append("  off: queues can finish too quickly for manual pause/drain/redirect tests\n");
		}
		if (!hasActiveRun())
		{
			builder.append("  start a run to open live safe-boundary controls\n\n");
		}
		else if (!supportsLiveQueueForCurrentAlgorithm())
		{
			builder.append("  current algorithm/mode does not expose live controls\n\n");
		}
		else if (queueSnapshot.getTotalShards() <= 0)
		{
			builder.append("  phase mode: no queue is open; node controls apply at the next safe boundary\n\n");
		}
		else if (queueSnapshot.getWaitingCount() <= 0)
		{
			builder.append("  queue closed: no unclaimed work remains; phase controls still apply during this run\n\n");
		}
		else
		{
			builder.append("  open: ")
					.append(queueSnapshot.getWaitingCount())
					.append(" pending work item(s) can still be reassigned\n\n");
		}

		builder.append("LIVE CONTROL EVENTS\n");
		List<LiveWorkloadAdjustmentRegistry.ControlEvent> controls =
				liveSnapshot.getEvents();
		if (controls.isEmpty())
		{
			builder.append("  no live control actions yet\n");
		}
		else
		{
			int start = Math.max(0, controls.size() - 12);
			for (int i = controls.size() - 1; i >= start; i--)
			{
				LiveWorkloadAdjustmentRegistry.ControlEvent event =
						controls.get(i);
				builder.append("  ")
						.append(formatClock(event.getTime()))
						.append("  ")
						.append(event.getAction())
						.append("  ")
						.append(event.getDetail())
						.append('\n');
			}
		}

		builder.append("\nQUEUE EVENTS\n");
		List<RecommendationWorkQueueRegistry.QueueEvent> events =
				queueSnapshot.getEvents();
		if (events.isEmpty())
		{
			builder.append("  no queue claims yet\n");
		}
		else
		{
			int start = Math.max(0, events.size() - 16);
			for (int i = events.size() - 1; i >= start; i--)
			{
				RecommendationWorkQueueRegistry.QueueEvent event =
						events.get(i);
				builder.append("  ")
						.append(formatClock(event.getTime()))
						.append("  node ")
						.append(event.getNodeIndex())
						.append("  shard ")
						.append(event.getShardNumber())
						.append("  ")
						.append(event.getAction())
						.append("  ")
						.append(event.getDetail())
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

	private enum PacingChoice
	{
		OFF("Off", 0),
		QUICK("250 ms", 250),
		VISIBLE("750 ms", 750),
		SLOW("1.5 s", 1500),
		TRAINING("3 s", 3000),
		WORKSHOP("5 s", 5000),
		LAB("10 s", 10000);

		private final String label;
		private final int delayMs;

		PacingChoice(String label, int delayMs)
		{
			this.label = label;
			this.delayMs = delayMs;
		}

		int delayMs()
		{
			return delayMs;
		}

		String label()
		{
			return label;
		}

		public String toString()
		{
			return label;
		}

		static PacingChoice forDelay(int delayMs)
		{
			PacingChoice best = OFF;
			int bestDelta = Integer.MAX_VALUE;
			for (PacingChoice choice : values())
			{
				int delta = Math.abs(choice.delayMs - Math.max(0, delayMs));
				if (delta < bestDelta)
				{
					best = choice;
					bestDelta = delta;
				}
			}
			return best;
		}

		static String formatDelay(int delayMs)
		{
			int safeDelay = Math.max(0, delayMs);
			for (PacingChoice choice : values())
			{
				if (choice.delayMs == safeDelay)
				{
					return choice.label();
				}
			}
			if (safeDelay >= 1000 && safeDelay % 1000 == 0)
			{
				return (safeDelay / 1000) + " s";
			}
			if (safeDelay >= 1000)
			{
				return String.format(java.util.Locale.US, "%.1f s",
						safeDelay / 1000.0);
			}
			return safeDelay + " ms";
		}
	}

	private void setStatus(String pill, String message, StatusTone tone)
	{
		statusPill.setText(pill == null ? "READY" : pill);
		statusLabel.setText(message == null ? "" : message);
		statusPill.setBackground(tone == StatusTone.ERROR
				? themed(new Color(255, 232, 229), StudioNightTheme.NIGHT_PANEL_INK)
				: themed(new Color(224, 244, 233), StudioNightTheme.NIGHT_PANEL_INK));
		statusPill.setForeground(tone == StatusTone.ERROR ? RED : GREEN);
		statusPill.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(tone == StatusTone.ERROR
						? themed(new Color(241, 169, 159), RED)
						: themed(new Color(174, 221, 194), GREEN)),
				new EmptyBorder(4, 14, 4, 14)));
	}

	private JPanel section(String title)
	{
		JPanel panel = surface(new BorderLayout(0, 10));
		panel.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(BORDER),
				new EmptyBorder(12, 12, 12, 12)));
		JLabel label = new JLabel(title);
		label.setFont(font(Font.BOLD, 16));
		label.setForeground(TEXT);
		panel.add(label, BorderLayout.NORTH);
		return panel;
	}

	private JPanel surface(BorderLayout layout)
	{
		JPanel panel = new JPanel(layout);
		panel.setOpaque(true);
		panel.setBackground(SURFACE);
		return panel;
	}

	private JPanel surface(GridLayout layout)
	{
		JPanel panel = new JPanel(layout);
		panel.setOpaque(true);
		panel.setBackground(SURFACE);
		return panel;
	}

	private JComponent metricCard(String label, JLabel value, Color accent)
	{
		JPanel panel = new JPanel(new BorderLayout(8, 0));
		panel.setBackground(SURFACE_ALT);
		panel.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createMatteBorder(0, 4, 0, 0, accent),
				new EmptyBorder(9, 12, 9, 12)));
		JLabel labelText = new JLabel(label);
		labelText.setFont(font(Font.BOLD, 10));
		labelText.setForeground(MUTED);
		panel.add(labelText, BorderLayout.NORTH);
		panel.add(value, BorderLayout.CENTER);
		return panel;
	}

	private JComponent control(String label, JComponent field)
	{
		JPanel panel = new JPanel(new BorderLayout(0, 4));
		panel.setOpaque(false);
		JLabel caption = new JLabel(label);
		caption.setFont(font(Font.BOLD, 10));
		caption.setForeground(MUTED);
		field.setFont(font(Font.BOLD, 12));
		panel.add(caption, BorderLayout.NORTH);
		panel.add(field, BorderLayout.CENTER);
		return panel;
	}

	private static JLabel metricValue(String text)
	{
		JLabel label = new JLabel(text);
		label.setFont(font(Font.BOLD, 18));
		label.setForeground(TEXT);
		return label;
	}

	private static JButton button(String text, Color background, Color foreground)
	{
		JButton button = new JButton(text);
		button.setFocusPainted(false);
		button.setOpaque(true);
		button.setBackground(background);
		button.setForeground(foreground);
		button.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(BORDER),
				new EmptyBorder(9, 12, 9, 12)));
		button.setFont(font(Font.BOLD, 12));
		button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		return button;
	}

	private static JPanel createAttributionBlock()
	{
		JPanel block = new JPanel();
		block.setOpaque(false);
		block.setLayout(new BoxLayout(block, BoxLayout.Y_AXIS));
		block.setBorder(new EmptyBorder(8, 0, 0, 0));
		block.add(createAttributionLine(
				"Faculty Advisor",
				"Dr. Abdolreza Abhari"));
		block.add(createAttributionLine("Developer", "Mohsen Alavi"));
		return block;
	}

	private static JPanel createAttributionLine(String role, String name)
	{
		JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		row.setOpaque(false);
		JLabel roleLabel = new JLabel(role.toUpperCase(Locale.US));
		roleLabel.setFont(font(Font.BOLD, 8));
		roleLabel.setForeground(MUTED);
		JLabel nameLabel = new JLabel(name);
		nameLabel.setFont(font(Font.BOLD, 10));
		nameLabel.setForeground(TEXT);
		row.add(roleLabel);
		row.add(nameLabel);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		return row;
	}

	private static Font font(int style, int size)
	{
		return new Font("SansSerif", style, size);
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
		return themed(new Color(229, 235, 244),
				StudioNightTheme.NIGHT_BACKGROUND_BOTTOM);
	}

	private static String shortText(String value, int limit)
	{
		if (value == null)
		{
			return "";
		}
		String text = value.trim();
		if (text.length() <= limit)
		{
			return text;
		}
		return text.substring(0, Math.max(0, limit - 3)) + "...";
	}

	private static String formatClock(long time)
	{
		if (time <= 0L)
		{
			return "--:--:--";
		}
		java.text.SimpleDateFormat format =
				new java.text.SimpleDateFormat("HH:mm:ss", Locale.US);
		return format.format(new java.util.Date(time));
	}

	private static String formatCount(long value)
	{
		return String.format(Locale.US, "%,d", Math.max(0L, value));
	}

	private static RecommendationWorkQueueRegistry.QueueSnapshot emptyQueueSnapshot()
	{
		return RecommendationWorkQueueRegistry.emptyQueueSnapshot();
	}

	private static Color stateColor(NodeExecutionMonitor.State state)
	{
		if (state == NodeExecutionMonitor.State.COMPLETE)
		{
			return GREEN;
		}
		if (state == NodeExecutionMonitor.State.FAILED
				|| state == NodeExecutionMonitor.State.STOPPED)
		{
			return RED;
		}
		if (state == NodeExecutionMonitor.State.WAITING_FOR_MEMORY
				|| state == NodeExecutionMonitor.State.WAITING_FOR_SHARED_MODEL)
		{
			return AMBER;
		}
		if (state == NodeExecutionMonitor.State.MODEL_RUNNING
				|| state == NodeExecutionMonitor.State.FEATURES
				|| state == NodeExecutionMonitor.State.PREPROCESSING)
		{
			return BLUE;
		}
		return PURPLE;
	}

	private final class LiveNodeCard extends JPanel
	{
		private final int nodeIndex;
		private final JLabel title = new JLabel();
		private final JLabel role = new JLabel();
		private final JLabel state = new JLabel();
		private final JLabel metrics = new JLabel();
		private final JLabel queueStats = new JLabel();
		private final JProgressBar inputProgress = new JProgressBar(0, 100);
		private final JProgressBar shardProgress = new JProgressBar(0, 100);
		private final JButton pauseButton =
				button("Pause", SURFACE_ALT, TEXT);
	private final JButton offlineButton =
				button("Unavailable", StudioNightTheme.adaptiveColor(
						new Color(255, 236, 234), StudioNightTheme.NIGHT_PANEL_INK), RED);
		private final JButton drainButton =
				button("Drain", AMBER, Color.WHITE);
		private final JButton redirectButton =
				button("Move pending here", GREEN, Color.WHITE);
		private final JSlider capacitySlider = new JSlider(10, 200, 100);
		private double pulse;
		private boolean updating;

		LiveNodeCard(final int nodeIndex)
		{
			super(new BorderLayout(10, 10));
			this.nodeIndex = nodeIndex;
			setOpaque(false);
			setBorder(new EmptyBorder(12, 12, 12, 12));
			setPreferredSize(new Dimension(330, 220));

			JPanel top = new JPanel(new BorderLayout(8, 0));
			top.setOpaque(false);
			JPanel labels = new JPanel();
			labels.setOpaque(false);
			labels.setLayout(new BoxLayout(labels, BoxLayout.Y_AXIS));
			title.setFont(font(Font.BOLD, 16));
			title.setForeground(TEXT);
			role.setFont(font(Font.BOLD, 11));
			role.setForeground(MUTED);
			labels.add(title);
			labels.add(role);
			state.setFont(font(Font.BOLD, 10));
			state.setHorizontalAlignment(SwingConstants.CENTER);
			state.setOpaque(true);
			state.setBorder(new EmptyBorder(5, 8, 5, 8));
			top.add(labels, BorderLayout.CENTER);
			top.add(state, BorderLayout.EAST);

			JPanel center = new JPanel(new GridLayout(4, 1, 0, 7));
			center.setOpaque(false);
			metrics.setFont(font(Font.PLAIN, 11));
			metrics.setForeground(MUTED);
			queueStats.setFont(font(Font.BOLD, 11));
			queueStats.setForeground(TEXT);
			styleProgress(inputProgress, BLUE);
			styleProgress(shardProgress, GREEN);
			center.add(metrics);
			center.add(inputProgress);
			center.add(queueStats);
			center.add(shardProgress);

			capacitySlider.setOpaque(false);
			capacitySlider.setMajorTickSpacing(50);
			capacitySlider.setPaintTicks(false);
			capacitySlider.addChangeListener(new ChangeListener()
			{
				public void stateChanged(ChangeEvent event)
				{
					if (updating || capacitySlider.getValueIsAdjusting())
					{
						return;
					}
					setCapacityScale(
							nodeIndex,
							capacitySlider.getValue() / 100.0);
				}
			});

			JPanel bottom = new JPanel(new BorderLayout(0, 8));
			bottom.setOpaque(false);
			bottom.add(capacitySlider, BorderLayout.NORTH);
			JPanel buttons = new JPanel(new GridLayout(2, 2, 7, 6));
			buttons.setOpaque(false);
			buttons.add(pauseButton);
			buttons.add(offlineButton);
			buttons.add(drainButton);
			buttons.add(redirectButton);
			bottom.add(buttons, BorderLayout.SOUTH);

			pauseButton.addActionListener(new ActionListener()
			{
				public void actionPerformed(ActionEvent event)
				{
					LiveWorkloadAdjustmentRegistry.NodeControl control =
							liveSnapshot.controlForNode(nodeIndex);
					pauseNode(nodeIndex, !control.isPaused());
				}
			});
			offlineButton.addActionListener(new ActionListener()
			{
				public void actionPerformed(ActionEvent event)
				{
					LiveWorkloadAdjustmentRegistry.NodeControl control =
							liveSnapshot.controlForNode(nodeIndex);
					setNodeUnavailable(nodeIndex, !control.isUnavailable());
				}
			});
			drainButton.addActionListener(new ActionListener()
			{
				public void actionPerformed(ActionEvent event)
				{
					drainNodeToBestAvailable(nodeIndex);
				}
			});
			redirectButton.addActionListener(new ActionListener()
			{
				public void actionPerformed(ActionEvent event)
				{
					redirectPendingToNode(nodeIndex);
				}
			});

			add(top, BorderLayout.NORTH);
			add(center, BorderLayout.CENTER);
			add(bottom, BorderLayout.SOUTH);
		}

		void refresh(
				NodeExecutionMonitor.NodeSnapshot node,
				RecommendationWorkQueueRegistry.QueueSnapshot queue,
				LiveWorkloadAdjustmentRegistry.Snapshot live,
				boolean liveQueueActive)
		{
			updating = true;
			NodeCapacityProfile profile =
					NodeCapacityRegistry.profileForNode(nodeIndex);
			LiveWorkloadAdjustmentRegistry.NodeControl control =
					live.controlForNode(nodeIndex);
			NodeExecutionMonitor.State nodeState = node == null
					? NodeExecutionMonitor.State.PROVISIONING
					: node.getState();
			Color accent = stateColor(nodeState);
			title.setText("Node " + nodeIndex);
			role.setText(profile.shortLabel() + "  |  live "
					+ control.statusLabel());
			state.setText(nodeState.name().replace('_', ' '));
			state.setBackground(new Color(
					accent.getRed(),
					accent.getGreen(),
					accent.getBlue(),
					32));
			state.setForeground(accent);
			state.setBorder(BorderFactory.createCompoundBorder(
					BorderFactory.createLineBorder(new Color(
							accent.getRed(),
							accent.getGreen(),
							accent.getBlue(),
							88)),
					new EmptyBorder(5, 8, 5, 8)));

			int inputPercent = node == null ? 0 : node.getProgressPercent();
			inputProgress.setValue(inputPercent);
			inputProgress.setString(inputPercent + "% input");
			int claimed = countFor(queue.getClaimedByNode(), nodeIndex);
			int done = countFor(queue.getCompletedByNode(), nodeIndex);
			List<Integer> active = queue.getActiveByNode().get(
					Integer.valueOf(nodeIndex));
			int activeCount = active == null ? 0 : active.size();
			int shardPercent = claimed <= 0 ? 0
					: (int)Math.round(done * 100.0 / Math.max(1, claimed));
			shardProgress.setValue(Math.max(0, Math.min(100, shardPercent)));
			shardProgress.setString(done + " done / " + claimed + " claimed");
			metrics.setText(node == null
					? "Waiting for JADE node telemetry."
					: node.getStage() + " | "
							+ formatCount(node.getReceivedTweets())
							+ " records");
			String queueMessage;
			if (!hasActiveRun())
			{
				queueMessage = "No live run yet. Start a run to open the queue.";
			}
			else if (!supportsLiveQueueForCurrentAlgorithm())
			{
				queueMessage = "No live controls for this algorithm/mode.";
			}
			else if (queue.getTotalShards() <= 0)
			{
				queueMessage = "Phase controls armed. Node will wait at the next safe boundary.";
			}
			else if (queue.getWaitingCount() <= 0 && activeCount <= 0)
			{
				queueMessage = "Queue closed. Phase controls apply at the next safe boundary.";
			}
			else if (queue.getWaitingCount() <= 0)
			{
				queueMessage = "All work claimed. In-flight work finishes normally.";
			}
			else
			{
				queueMessage = activeCount + " active work item"
						+ (activeCount == 1 ? "" : "s")
						+ " | " + queue.getWaitingCount() + " waiting | "
						+ done + " complete | "
						+ Math.round(control.getCapacityScale() * 100.0)
						+ "% live capacity";
			}
			queueStats.setText(queueMessage);
			capacitySlider.setValue((int)Math.round(
					control.getCapacityScale() * 100.0));
			boolean enabled = hasActiveRun() && liveQueueActive;
			pauseButton.setEnabled(enabled);
			offlineButton.setEnabled(enabled);
			int drainTarget = bestDrainTargetFor(nodeIndex);
			boolean hasNodeWork = activeCount > 0 || queue.getWaitingCount() > 0;
			drainButton.setEnabled(enabled && hasNodeWork && drainTarget > 0);
			redirectButton.setEnabled(enabled && queue.getWaitingCount() > 0);
			capacitySlider.setEnabled(enabled);
			String controlTip = enabled
					? "Pause affects future claims and phase boundaries; drain requeues safe-boundary active work."
					: liveAdjustmentBlockReason();
			pauseButton.setToolTipText(controlTip);
			offlineButton.setToolTipText(controlTip);
			drainButton.setToolTipText(drainTarget > 0
					? "Drain this node to Node " + drainTarget + "."
					: controlTip);
			redirectButton.setToolTipText(controlTip);
			capacitySlider.setToolTipText(controlTip);
			pauseButton.setText(control.isPaused() ? "Resume" : "Pause");
			drainButton.setText(control.isDraining() ? "Draining" : "Drain");
			offlineButton.setText(
					control.isUnavailable() ? "Available" : "Unavailable");
			updating = false;
			repaint();
		}

		void advance()
		{
			pulse += 0.08;
			if (pulse > Math.PI * 2.0)
			{
				pulse = 0.0;
			}
			repaint();
		}

		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D)graphics.create();
			g.setRenderingHint(
					RenderingHints.KEY_ANTIALIASING,
					RenderingHints.VALUE_ANTIALIAS_ON);
			int w = getWidth();
			int h = getHeight();
			LiveWorkloadAdjustmentRegistry.NodeControl control =
					liveSnapshot.controlForNode(nodeIndex);
			Color accent = control.isUnavailable() ? RED
					: control.isDraining() ? AMBER
							: control.isPaused() ? AMBER
							: control.getCapacityScale() > 1.05 ? GREEN : BLUE;
			float glow = (float)(0.08 + 0.07 * (1.0 + Math.sin(pulse)));
			g.setPaint(new GradientPaint(
					0,
					0,
					SURFACE,
					w,
					h,
					new Color(accent.getRed(), accent.getGreen(),
							accent.getBlue(), Math.round(255 * glow))));
			g.fillRoundRect(0, 0, w - 1, h - 1, 16, 16);
			g.setColor(new Color(accent.getRed(), accent.getGreen(),
					accent.getBlue(), 150));
			g.setStroke(new BasicStroke(2f));
			g.drawRoundRect(0, 0, w - 1, h - 1, 16, 16);
			g.setColor(accent);
			g.fillRoundRect(0, 0, 6, h, 16, 16);
			g.dispose();
			super.paintComponent(graphics);
		}
	}

	private final class QueueFlowPanel extends JPanel
	{
		private RecommendationWorkQueueRegistry.QueueSnapshot queue =
				emptyQueueSnapshot();
		private LiveWorkloadAdjustmentRegistry.Snapshot live =
				LiveWorkloadAdjustmentRegistry.snapshot("");
		private RecommendationSchedulerPlan plan =
				RecommendationSchedulerPlan.defaultPlan();
		private int algorithm = ControllerAgent.K_MEANS;
		private int nodeCount = 1;
		private long clock = System.currentTimeMillis();
		private double animation;

		QueueFlowPanel()
		{
			setOpaque(false);
			setBorder(BorderFactory.createLineBorder(BORDER));
		}

		void setContext(
				RecommendationWorkQueueRegistry.QueueSnapshot queue,
				LiveWorkloadAdjustmentRegistry.Snapshot live,
				RecommendationSchedulerPlan plan,
				int algorithm,
				int nodeCount,
				long clock)
		{
			this.queue = queue == null ? emptyQueueSnapshot() : queue;
			this.live = live == null
					? LiveWorkloadAdjustmentRegistry.snapshot("") : live;
			this.plan = plan == null
					? RecommendationSchedulerPlan.defaultPlan() : plan;
			this.algorithm = algorithm;
			this.nodeCount = Math.max(1, nodeCount);
			this.clock = Math.max(0L, clock);
			repaint();
		}

		void advance()
		{
			animation += 0.025;
			if (animation > 1.0)
			{
				animation = 0.0;
			}
			repaint();
		}

		protected void paintComponent(Graphics graphics)
		{
			super.paintComponent(graphics);
			Graphics2D g = (Graphics2D)graphics.create();
			g.setRenderingHint(
					RenderingHints.KEY_ANTIALIASING,
					RenderingHints.VALUE_ANTIALIAS_ON);
			int w = getWidth();
			int h = getHeight();
			Color queueEnd = themed(
					new Color(236, 247, 242),
					StudioNightTheme.blend(
							StudioNightTheme.NIGHT_SURFACE,
							StudioNightTheme.NIGHT_GREEN,
							0.13f));
			g.setPaint(new GradientPaint(
					0,
					0,
					SURFACE,
					w,
					h,
					queueEnd));
			g.fillRoundRect(0, 0, w - 1, h - 1, 18, 18);
			g.setColor(BORDER);
			g.drawRoundRect(0, 0, w - 1, h - 1, 18, 18);
			drawTitle(g, w);
			drawMetrics(g, w);
			drawFlow(g, w, h);
			g.dispose();
		}

		private void drawTitle(Graphics2D g, int width)
		{
			g.setColor(TEXT);
			g.setFont(font(Font.BOLD, 19));
			g.drawString("Safe-Boundary Work Queue", 22, 34);
			g.setColor(MUTED);
			g.setFont(font(Font.PLAIN, 12));
			String detail = RecommendationModeSupport.algorithmName(algorithm)
					+ " | " + plan.getMode().getLabel()
					+ " | " + (live.hasActiveControls()
							? live.compactSummary()
							: "no live overrides");
			g.drawString(shortText(detail, Math.max(24, width / 9)), 22, 54);
		}

		private void drawMetrics(Graphics2D g, int width)
		{
			int x = 22;
			int y = 72;
			int gap = 10;
			int mw = Math.max(76, (width - 44 - gap * 3) / 4);
			drawMetric(g, x, y, mw, "waiting",
					String.valueOf(queue.getWaitingCount()), PURPLE);
			drawMetric(g, x + (mw + gap), y, mw, "active",
					String.valueOf(queue.getActiveCount()), AMBER);
			drawMetric(g, x + (mw + gap) * 2, y, mw, "done",
					queue.getCompletedCount() + "/" + queue.getTotalShards(),
					GREEN);
			drawMetric(g, x + (mw + gap) * 3, y, mw, "policy",
					live.getPolicyOverride() == null
							? plan.getPolicy().getLabel()
							: live.getPolicyOverride().getLabel(),
					BLUE);
		}

		private void drawFlow(Graphics2D g, int width, int height)
		{
			int top = 138;
			int bottom = height - 20;
			int queueX = 24;
			int queueW = Math.max(124, width / 4);
			int mergeW = 104;
			int mergeX = width - mergeW - 24;
			int nodeX = queueX + queueW + 26;
			int nodeW = Math.max(130, mergeX - nodeX - 26);
			int areaH = Math.max(150, bottom - top);

			drawQueueBucket(g, queueX, top, queueW, areaH);
			drawNodeLanes(g, nodeX, top, nodeW, areaH);
			drawMerge(g, mergeX, top + areaH / 2 - 50, mergeW, 100);
			drawArrow(g, queueX + queueW, top + areaH / 2,
					nodeX - 10, top + areaH / 2, BLUE);
			drawArrow(g, nodeX + nodeW + 10, top + areaH / 2,
					mergeX - 10, top + areaH / 2, GREEN);
			drawPacket(g, queueX + queueW + 8, top + areaH / 2,
					nodeX - 18, top + areaH / 2, BLUE, animation);
			drawPacket(g, nodeX + nodeW + 18, top + areaH / 2,
					mergeX - 18, top + areaH / 2, GREEN,
					(animation + 0.45) % 1.0);
		}

		private void drawQueueBucket(
				Graphics2D g,
				int x,
				int y,
				int w,
				int h)
		{
			drawPanel(g, x, y, w, h, "Shared pending queue", PURPLE);
			g.setFont(font(Font.BOLD, 20));
			g.setColor(TEXT);
			g.drawString(queue.getWaitingCount() + " waiting", x + 16, y + 46);
			g.setFont(font(Font.PLAIN, 11));
			g.setColor(MUTED);
			g.drawString("unclaimed work", x + 16, y + 66);
			int tokenX = x + 16;
			int tokenY = y + 88;
			int shown = 0;
			for (Integer shard : queue.getWaitingPreview())
			{
				if (shown >= 24)
				{
					break;
				}
				int tx = tokenX + (shown % 6) * 34;
				int ty = tokenY + (shown / 6) * 30;
				if (ty + 24 > y + h - 16)
				{
					break;
				}
				drawToken(g, tx, ty, String.valueOf(shard), PURPLE);
				shown++;
			}
			if (queue.getWaitingCount() > shown)
			{
				g.setColor(MUTED);
				g.setFont(font(Font.BOLD, 11));
				g.drawString("+" + (queue.getWaitingCount() - shown)
						+ " more", x + 16, y + h - 18);
			}
		}

		private void drawNodeLanes(
				Graphics2D g,
				int x,
				int y,
				int w,
				int h)
		{
			drawPanel(g, x, y, w, h, "Nodes pulling work", BLUE);
			int columns = w > 420 ? 2 : 1;
			int cardGap = 10;
			int cardW = (w - 28 - cardGap * (columns - 1)) / columns;
			int cardH = 66;
			int startY = y + 34;
			for (int node = 1; node <= nodeCount; node++)
			{
				int row = (node - 1) / columns;
				int col = (node - 1) % columns;
				int cx = x + 14 + col * (cardW + cardGap);
				int cy = startY + row * (cardH + 10);
				if (cy + cardH > y + h - 12)
				{
					g.setColor(MUTED);
					g.setFont(font(Font.BOLD, 11));
					g.drawString("Scroll the node cards for all node actions.",
							x + 16,
							y + h - 18);
					break;
				}
				drawQueueNodeCard(g, cx, cy, cardW, cardH, node);
			}
		}

		private void drawQueueNodeCard(
				Graphics2D g,
				int x,
				int y,
				int w,
				int h,
				int node)
		{
			NodeCapacityProfile profile = NodeCapacityRegistry.profileForNode(node);
			LiveWorkloadAdjustmentRegistry.NodeControl control =
					live.controlForNode(node);
			Color accent = control.isUnavailable() ? RED
					: control.isPaused() ? AMBER
							: profile.getRole() == NodeCapacityProfile.Role.STRONG
									? GREEN : BLUE;
			g.setColor(glass(232));
			g.fillRoundRect(x, y, w, h, 12, 12);
			g.setColor(new Color(accent.getRed(), accent.getGreen(),
					accent.getBlue(), 135));
			g.drawRoundRect(x, y, w, h, 12, 12);
			g.setColor(accent);
			g.fillRoundRect(x, y, 5, h, 12, 12);
			g.setColor(TEXT);
			g.setFont(font(Font.BOLD, 12));
			g.drawString("Node " + node, x + 14, y + 20);
			g.setColor(MUTED);
			g.setFont(font(Font.BOLD, 10));
			g.drawString(profile.shortLabel() + " | "
					+ control.statusLabel(), x + 14, y + 38);
			int claimed = countFor(queue.getClaimedByNode(), node);
			int done = countFor(queue.getCompletedByNode(), node);
			g.setColor(TEXT);
			g.setFont(font(Font.BOLD, 10));
			String claimText = claimed + " claimed / " + done + " done";
			if (w < 210)
			{
				g.drawString(shortText(claimText, Math.max(9, w / 9)),
						x + 14,
						y + 53);
			}
			else
			{
				g.drawString(claimText, x + w - 126, y + 20);
			}
			int progressY = w < 210 ? y + h - 8 : y + h - 16;
			drawTinyProgress(g, x + 14, progressY, w - 28,
					queue.getTotalShards() <= 0 ? 0
							: claimed / (double)Math.max(1, queue.getTotalShards()),
					accent);
		}

		private void drawMerge(Graphics2D g, int x, int y, int w, int h)
		{
			g.setColor(themed(new Color(229, 244, 235), StudioNightTheme.NIGHT_PANEL_INK));
			g.fillRoundRect(x, y, w, h, 30, 30);
			g.setColor(GREEN);
			g.setStroke(new BasicStroke(2f));
			g.drawRoundRect(x, y, w, h, 30, 30);
			g.setFont(font(Font.BOLD, 15));
			g.setColor(TEXT);
			g.drawString("MERGE", x + 24, y + 34);
			g.setFont(font(Font.BOLD, 20));
			g.setColor(GREEN);
			g.drawString(queue.getCompletedCount() + "/"
					+ queue.getTotalShards(), x + 20, y + 62);
			g.setFont(font(Font.BOLD, 10));
			g.setColor(MUTED);
			g.drawString("complete work", x + 18, y + 79);
		}
	}

	private static void styleProgress(JProgressBar bar, Color color)
	{
		bar.setStringPainted(true);
		bar.setForeground(color);
		bar.setBackground(track());
		bar.setBorder(BorderFactory.createEmptyBorder());
		bar.setFont(font(Font.BOLD, 10));
	}

	private static int countFor(Map<Integer,Integer> counts, int nodeIndex)
	{
		Integer value = counts.get(Integer.valueOf(nodeIndex));
		return value == null ? 0 : Math.max(0, value.intValue());
	}

	private static void drawTitle(Graphics2D g, String text, int x, int y)
	{
		g.setColor(TEXT);
		g.setFont(font(Font.BOLD, 14));
		g.drawString(text, x, y);
	}

	private static void drawPanel(
			Graphics2D g,
			int x,
			int y,
			int w,
			int h,
			String label,
			Color accent)
	{
		g.setColor(glass(220));
		g.fillRoundRect(x, y, w, h, 16, 16);
		g.setColor(new Color(accent.getRed(), accent.getGreen(),
				accent.getBlue(), 80));
		g.drawRoundRect(x, y, w, h, 16, 16);
		g.setColor(new Color(accent.getRed(), accent.getGreen(),
				accent.getBlue(), 38));
		g.fillRoundRect(x, y, w, 28, 16, 16);
		g.setColor(MUTED);
		g.setFont(font(Font.BOLD, 10));
		g.drawString(label.toUpperCase(Locale.US), x + 14, y + 19);
	}

	private static void drawMetric(
			Graphics2D g,
			int x,
			int y,
			int w,
			String label,
			String value,
			Color accent)
	{
		g.setColor(themed(
				new Color(250, 252, 255),
				StudioNightTheme.blend(
						StudioNightTheme.NIGHT_PANEL_INK,
						accent,
						0.08f)));
		g.fillRoundRect(x, y, w, 52, 12, 12);
		g.setColor(new Color(accent.getRed(), accent.getGreen(),
				accent.getBlue(), 120));
		g.drawRoundRect(x, y, w, 52, 12, 12);
		g.setColor(MUTED);
		g.setFont(font(Font.BOLD, 10));
		g.drawString(label.toUpperCase(Locale.US), x + 11, y + 18);
		g.setColor(TEXT);
		g.setFont(font(Font.BOLD, 18));
		g.drawString(shortText(value, Math.max(8, w / 11)),
				x + 11,
				y + 41);
	}

	private static void drawToken(
			Graphics2D g,
			int x,
			int y,
			String text,
			Color accent)
	{
		g.setColor(new Color(accent.getRed(), accent.getGreen(),
				accent.getBlue(), 35));
		g.fillRoundRect(x, y, 26, 23, 6, 6);
		g.setColor(accent);
		g.drawRoundRect(x, y, 26, 23, 6, 6);
		g.setFont(font(Font.BOLD, 10));
		g.drawString(shortText(text, 3), x + 8, y + 15);
	}

	private static void drawTinyProgress(
			Graphics2D g,
			int x,
			int y,
			int w,
			double progress,
			Color accent)
	{
		double p = Math.max(0.0, Math.min(1.0, progress));
		g.setColor(track());
		g.fillRoundRect(x, y, w, 6, 8, 8);
		g.setColor(accent);
		g.fillRoundRect(x, y, (int)Math.round(w * p), 6, 8, 8);
	}

	private static void drawArrow(
			Graphics2D g,
			int x1,
			int y1,
			int x2,
			int y2,
			Color color)
	{
		g.setColor(new Color(color.getRed(), color.getGreen(),
				color.getBlue(), 75));
		g.setStroke(new BasicStroke(2f));
		g.drawLine(x1, y1, x2, y2);
		g.fillOval(x2 - 4, y2 - 4, 8, 8);
	}

	private static void drawPacket(
			Graphics2D g,
			int x1,
			int y1,
			int x2,
			int y2,
			Color color,
			double phase)
	{
		double p = Math.max(0.0, Math.min(1.0, phase));
		int x = (int)Math.round(x1 + (x2 - x1) * p);
		int y = (int)Math.round(y1 + (y2 - y1) * p);
		g.setColor(color);
		g.fillOval(x - 5, y - 5, 10, 10);
		g.setColor(new Color(color.getRed(), color.getGreen(),
				color.getBlue(), 70));
		g.setStroke(new BasicStroke(2f));
		g.drawOval(x - 11, y - 11, 22, 22);
	}

	private final class HeaderSignalPanel extends JPanel
	{
		private int algorithm = ControllerAgent.K_MEANS;
		private RecommendationWorkQueueRegistry.QueueSnapshot queue =
				emptyQueueSnapshot();
		private double phase;

		HeaderSignalPanel()
		{
			setOpaque(false);
		}

		void setState(
				int algorithm,
				RecommendationWorkQueueRegistry.QueueSnapshot queue)
		{
			this.algorithm = algorithm;
			this.queue = queue == null ? emptyQueueSnapshot() : queue;
			repaint();
		}

		void advance()
		{
			phase += 0.018;
			if (phase > 1.0)
			{
				phase = 0.0;
			}
			repaint();
		}

		protected void paintComponent(Graphics graphics)
		{
			super.paintComponent(graphics);
			Graphics2D g = (Graphics2D)graphics.create();
			g.setRenderingHint(
					RenderingHints.KEY_ANTIALIASING,
					RenderingHints.VALUE_ANTIALIAS_ON);
			int w = getWidth();
			int h = getHeight();
			int[] xs = {
					24,
					w / 4,
					w / 2,
					w * 3 / 4,
					w - 24
			};
			int[] ys = {
					h / 2,
					h / 2 - 10,
					h / 2 + 7,
					h / 2 - 2,
					h / 2 + 12
			};
			g.setStroke(new BasicStroke(2f));
			for (int i = 0; i < xs.length - 1; i++)
			{
				g.setColor(new Color(BLUE.getRed(), BLUE.getGreen(),
						BLUE.getBlue(), 55));
				g.drawLine(xs[i], ys[i], xs[i + 1], ys[i + 1]);
			}
			for (int i = 0; i < xs.length; i++)
			{
				Color accent = i % 2 == 0 ? BLUE : GREEN;
				int pulse = (int)Math.round(
						4 + 3 * Math.sin((phase + i * 0.17) * Math.PI * 2));
				g.setColor(new Color(accent.getRed(), accent.getGreen(),
						accent.getBlue(), 55));
				g.fillOval(xs[i] - 9 - pulse, ys[i] - 9 - pulse,
						18 + pulse * 2, 18 + pulse * 2);
				g.setColor(accent);
				g.drawOval(xs[i] - 8, ys[i] - 8, 16, 16);
				g.setColor(glass(190));
				g.fillOval(xs[i] - 4, ys[i] - 4, 8, 8);
			}
			g.setFont(font(Font.BOLD, 11));
			g.setColor(MUTED);
			String label = RecommendationModeSupport.algorithmName(algorithm)
					+ " live controls | "
					+ queue.getCompletedCount() + "/"
					+ queue.getTotalShards() + " queue work complete";
			g.drawString(shortText(label, Math.max(24, w / 9)), 24, h - 12);
			g.dispose();
		}
	}
}
