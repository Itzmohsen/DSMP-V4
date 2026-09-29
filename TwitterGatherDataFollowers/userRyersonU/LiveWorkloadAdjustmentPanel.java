package TwitterGatherDataFollowers.userRyersonU;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.RadialGradientPaint;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;
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
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.BasicProgressBarUI;
import javax.swing.plaf.basic.BasicSliderUI;

/**
 * Live control cockpit for safe scheduler boundaries. This panel is intentionally
 * read/write only for the scheduler registries; it never recomputes scores or
 * edits graph artifacts.
 */
final class LiveWorkloadAdjustmentPanel extends JPanel
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
	private static final DecimalFormat DECIMAL = new DecimalFormat("0.##");

	private final ControllerAgentGui owner;
	private final HeaderSignalPanel headerSignalPanel = new HeaderSignalPanel();
	private final JLabel modeMetric = metricValue("--");
	private final JLabel policyMetric = metricValue("--");
	private final JLabel queueMetric = metricValue("--");
	private final JLabel nodeMetric = metricValue("--");
	private final JLabel statusPill = new Pill("READY");
	private final JLabel statusLabel = new LockedLabel(
			"Start a simulator run to adjust pending live queue work.");
	private final JComboBox<SchedulerLabEngine.SchedulingPolicy> policyBox =
			new JComboBox<SchedulerLabEngine.SchedulingPolicy>(
					SchedulerLabEngine.SchedulingPolicy.values())
			{
				protected void paintBorder(Graphics graphics)
				{
					// The Nebula combo delegate paints its own rounded border.
				}
			};
	private final JComboBox<PacingChoice> pacingBox =
			new JComboBox<PacingChoice>(PacingChoice.values())
			{
				protected void paintBorder(Graphics graphics)
				{
					// The Nebula combo delegate paints its own rounded border.
				}
			};
	private final JButton applyPolicyButton =
			button("Apply policy to remaining work", NebulaButton.NEUTRAL);
	private final JButton applyPacingButton =
			button("Set action window", NebulaButton.NEUTRAL);
	private final JButton armInteractiveButton =
			button("Arm interactive run", NebulaButton.PRIMARY);
	private final JButton restoreDefaultButton =
			button("Restore DSMP default", NebulaButton.NEUTRAL);
	private final JButton resetButton =
			button("Reset live controls", NebulaButton.NEUTRAL);
	private final JButton capacityButton =
			button("Node capacity", NebulaButton.NEUTRAL);
	private final JLabel phasePill = new Pill("READY");
	private final JLabel phaseTitle =
			new LockedLabel("Arm a live queue before running.");
	private final JLabel phaseDetail = new LockedLabel(
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
		policyBox.setUI(new NebulaComboUI());
		pacingBox.setUI(new NebulaComboUI());
		styleCombo(policyBox);
		styleCombo(pacingBox);

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
		HeroCard header = new HeroCard(new BorderLayout(18, 0));
		header.setBorder(new EmptyBorder(14, 20, 16, 20));

		JPanel titleStack = new JPanel();
		titleStack.setOpaque(false);
		titleStack.setLayout(new BoxLayout(titleStack, BoxLayout.Y_AXIS));
		LockedLabel eyebrow = new LockedLabel("Mission Control");
		NebulaWidgets.styleSectionLabel(eyebrow);
		eyebrow.setForeground(NebulaDesign.ACCENT);
		eyebrow.lockForeground();
		JLabel title = fixedLabel(
				"Live Workload Adjustment",
				NebulaDesign.fontSemiBold(23f),
				TEXT);
		JLabel subtitle = fixedLabel(
				"Pause, drain, redirect, throttle, and switch policies while safe queue work is still pending.",
				NebulaDesign.fontBody(12.5f),
				MUTED);
		eyebrow.setAlignmentX(Component.LEFT_ALIGNMENT);
		title.setAlignmentX(Component.LEFT_ALIGNMENT);
		subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
		titleStack.add(eyebrow);
		titleStack.add(Box.createRigidArea(new Dimension(0, 3)));
		titleStack.add(title);
		titleStack.add(Box.createRigidArea(new Dimension(0, 3)));
		titleStack.add(subtitle);
		JPanel credits = createAttributionBlock();
		credits.setAlignmentX(Component.LEFT_ALIGNMENT);
		titleStack.add(credits);

		headerSignalPanel.setPreferredSize(new Dimension(650, 84));
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
		JPanel panel = new CardPanel(new BorderLayout(12, 12));
		panel.setBorder(new EmptyBorder(14, 16, 16, 16));

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
		JPanel card = new AltCard(new BorderLayout(12, 0));
		card.setBorder(new EmptyBorder(10, 14, 10, 14));
		phasePill.setHorizontalAlignment(SwingConstants.CENTER);
		phasePill.setBackground(NebulaDesign.SUCCESS_SOFT);
		phasePill.setForeground(GREEN);
		JPanel text = new JPanel();
		text.setOpaque(false);
		text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
		phaseTitle.setFont(NebulaDesign.fontSemiBold(13.5f));
		phaseTitle.setForeground(TEXT);
		((LockedLabel)phaseTitle).lockForeground();
		phaseDetail.setFont(NebulaDesign.fontBody(12f));
		phaseDetail.setForeground(MUTED);
		((LockedLabel)phaseDetail).lockForeground();
		text.add(phaseTitle);
		text.add(Box.createRigidArea(new Dimension(0, 3)));
		text.add(phaseDetail);
		JPanel pillHolder = new JPanel(new GridBagLayout());
		pillHolder.setOpaque(false);
		pillHolder.add(phasePill);
		card.add(pillHolder, BorderLayout.WEST);
		card.add(text, BorderLayout.CENTER);
		return card;
	}

	private JComponent createNodeControlPanel()
	{
		JPanel panel = section("Live Node Controls");
		JPanel heading = new JPanel();
		heading.setOpaque(false);
		heading.setLayout(new BoxLayout(heading, BoxLayout.Y_AXIS));
		JLabel headingTitle = fixedLabel(
				"Live Node Controls",
				NebulaDesign.fontSemiBold(15f),
				TEXT);
		JLabel help = fixedLabel(
				"Pause stops future claims; drain returns safe-boundary active work to the queue and moves remaining work to another node.",
				NebulaDesign.fontBody(11.5f),
				MUTED);
		headingTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
		help.setAlignmentX(Component.LEFT_ALIGNMENT);
		heading.add(headingTitle);
		heading.add(Box.createRigidArea(new Dimension(0, 3)));
		heading.add(help);
		heading.add(Box.createRigidArea(new Dimension(0, 8)));
		panel.add(heading, BorderLayout.NORTH);

		nodeGrid.setOpaque(false);
		JPanel holder = new JPanel(new BorderLayout());
		holder.setOpaque(false);
		holder.add(nodeGrid, BorderLayout.NORTH);

		JScrollPane scrollPane = new JScrollPane(holder);
		scrollPane.setBorder(BorderFactory.createEmptyBorder());
		scrollPane.setOpaque(false);
		scrollPane.getViewport().setOpaque(false);
		scrollPane.setHorizontalScrollBarPolicy(
				ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		scrollPane.getVerticalScrollBar().setUnitIncrement(18);
		scrollPane.getVerticalScrollBar().setUI(new NebulaWidgets.PillScrollBarUI());
		scrollPane.getVerticalScrollBar().setOpaque(false);
		panel.add(scrollPane, BorderLayout.CENTER);
		return panel;
	}

	private JComponent createQueuePanel()
	{
		JPanel panel = section("Live Queue And Control Trace");
		JPanel body = new JPanel(new BorderLayout(0, 12));
		body.setOpaque(false);
		queueFlowPanel.setPreferredSize(new Dimension(620, 360));
		body.add(queueFlowPanel, BorderLayout.NORTH);

		eventLog.setEditable(false);
		eventLog.setLineWrap(false);
		eventLog.setWrapStyleWord(false);
		eventLog.setFont(NebulaDesign.fontMono(12f));
		eventLog.setForeground(NebulaDesign.CONSOLE_TEXT);
		eventLog.setBackground(NebulaDesign.CONSOLE);
		eventLog.setOpaque(false);
		eventLog.setBorder(new EmptyBorder(4, 6, 4, 6));
		JScrollPane logScroll = new JScrollPane(eventLog);
		logScroll.setBorder(BorderFactory.createEmptyBorder());
		logScroll.setOpaque(false);
		logScroll.getViewport().setOpaque(false);
		logScroll.getVerticalScrollBar().setUI(new NebulaWidgets.PillScrollBarUI());
		logScroll.getVerticalScrollBar().setOpaque(false);
		logScroll.getHorizontalScrollBar().setUI(new NebulaWidgets.PillScrollBarUI());
		logScroll.getHorizontalScrollBar().setOpaque(false);
		JPanel console = new ConsoleCard(new BorderLayout());
		console.setBorder(new EmptyBorder(8, 8, 8, 8));
		console.setPreferredSize(new Dimension(620, 210));
		console.add(logScroll, BorderLayout.CENTER);
		body.add(console, BorderLayout.CENTER);
		panel.add(body, BorderLayout.CENTER);
		return panel;
	}

	private JComponent createStatusBar()
	{
		JPanel bar = new CardPanel(new BorderLayout(10, 0));
		bar.setBorder(new EmptyBorder(8, 14, 10, 14));
		statusPill.setHorizontalAlignment(SwingConstants.CENTER);
		statusLabel.setFont(NebulaDesign.fontMedium(12.5f));
		statusLabel.setForeground(TEXT);
		((LockedLabel)statusLabel).lockForeground();
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
		//Capacity emulated, without injected failures: an operator trying live
		//controls should not also get random unit failures unless chosen.
		RecommendationSchedulerRegistry.apply(
				RecommendationSchedulerPlan.Mode.SCORING_SAFE,
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
					"Use Arm interactive run to turn on the capacity-emulated scheduler and a visible action window, then start the simulator.",
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
				? NebulaDesign.DANGER_SOFT : NebulaDesign.SUCCESS_SOFT);
		phasePill.setForeground(tone == StatusTone.ERROR ? RED : GREEN);
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

	/**
	 * The run's current work phase (a model stage's units, else the input
	 * delivery), which the live controls act on.
	 */
	private RecommendationWorkQueueRegistry.QueueSnapshot currentQueueSnapshot()
	{
		return RecommendationWorkQueueRegistry.latestPhaseSnapshot(activeRunId());
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

	/**
	 * Every open work phase (input delivery or a model stage's units) is a
	 * live queue in every scheduler mode; before one opens, controls still act
	 * at phase boundaries of an active run.
	 */
	private boolean isLiveQueueSupported()
	{
		return hasActiveRun() && supportsLiveQueueForCurrentAlgorithm();
	}

	private boolean supportsLiveQueueForCurrentAlgorithm()
	{
		return true;
	}

	private boolean isInputQueueSnapshot()
	{
		return RecommendationWorkQueueRegistry.isInputSnapshot(queueSnapshot);
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
		return owner == null ? 1 : Math.max(1, owner.currentNodeCountSetting());
	}

	private int currentTargetCount()
	{
		return owner == null
				? 1 : Math.max(1, owner.getRecommendationBatchUserCount());
	}

	private int currentCandidateCount()
	{
		return owner == null
				? 1 : Math.max(1, owner.getCandidateUserCountEstimate());
	}

	private int currentAlgorithm()
	{
		return owner == null || owner.algorithmSelectionBox == null
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
				? NebulaDesign.DANGER_SOFT : NebulaDesign.SUCCESS_SOFT);
		statusPill.setForeground(tone == StatusTone.ERROR ? RED : GREEN);
	}

	private JPanel section(String title)
	{
		JPanel panel = new CardPanel(new BorderLayout(0, 10));
		panel.setBorder(new EmptyBorder(14, 16, 16, 16));
		JLabel label = fixedLabel(title, NebulaDesign.fontSemiBold(15f), TEXT);
		panel.add(label, BorderLayout.NORTH);
		return panel;
	}

	private JComponent metricCard(String label, JLabel value, Color accent)
	{
		JPanel panel = new AltCard(new BorderLayout(8, 2));
		panel.setBorder(new EmptyBorder(9, 14, 9, 12));
		JLabel labelText = fixedLabel(
				label,
				NebulaDesign.fontSection(),
				NebulaDesign.TEXT_TERTIARY);
		labelText.setIcon(new DotIcon(accent));
		labelText.setIconTextGap(7);
		panel.add(labelText, BorderLayout.NORTH);
		panel.add(value, BorderLayout.CENTER);
		return panel;
	}

	private JComponent control(String label, JComponent field)
	{
		JPanel panel = new JPanel(new BorderLayout(0, 4));
		panel.setOpaque(false);
		JLabel caption = fixedLabel(label, NebulaDesign.fontMedium(11f), MUTED);
		field.setFont(NebulaDesign.fontMedium(12.5f));
		panel.add(caption, BorderLayout.NORTH);
		panel.add(field, BorderLayout.CENTER);
		return panel;
	}

	private static JLabel metricValue(String text)
	{
		return fixedLabel(text, NebulaDesign.fontSemiBold(18f), TEXT);
	}

	private static JLabel fixedLabel(String text, Font font, Color color)
	{
		LockedLabel label = new LockedLabel(text);
		label.setFont(font);
		label.setForeground(color);
		label.lockForeground();
		return label;
	}

	private static JButton button(String text, int kind)
	{
		return new NebulaButton(text, kind);
	}

	private static void styleCombo(JComboBox<?> box)
	{
		box.setOpaque(false);
		box.setFont(NebulaDesign.fontMedium(12.5f));
		box.setForeground(TEXT);
		box.setBackground(SURFACE_ALT);
		box.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 2));
		box.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
	}

	private static JPanel createAttributionBlock()
	{
		JPanel block = new JPanel(new BorderLayout());
		block.setOpaque(false);
		block.setBorder(new EmptyBorder(7, 0, 0, 0));
		JLabel credits = fixedLabel(
				"Faculty advisor Dr. Abdolreza Abhari  ·  Developer Mohsen Alavi",
				NebulaDesign.fontSmall(),
				NebulaDesign.TEXT_TERTIARY);
		block.add(credits, BorderLayout.WEST);
		return block;
	}

	private static Font font(int style, int size)
	{
		return style == Font.BOLD
				? NebulaDesign.fontSemiBold(size)
				: NebulaDesign.fontBody(size);
	}

	private static Color themed(Color day, Color night)
	{
		return StudioNightTheme.isNightMode() ? night : day;
	}

	private static Color glass(int alpha)
	{
		return themed(
				NebulaDesign.withAlpha(NebulaDesign.DAY_SURFACE, alpha),
				NebulaDesign.withAlpha(
						NebulaDesign.NIGHT_SURFACE_RAISED,
						Math.min(255, alpha + 16)));
	}

	private static Color track()
	{
		return NebulaDesign.INSET;
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
		private final LockedLabel title = new LockedLabel("");
		private final LockedLabel role = new LockedLabel("");
		private final JLabel state = new Pill("");
		private final LockedLabel metrics = new LockedLabel("");
		private final LockedLabel queueStats = new LockedLabel("");
		private final JProgressBar inputProgress = new JProgressBar(0, 100);
		private final JProgressBar shardProgress = new JProgressBar(0, 100);
		private final JButton pauseButton =
				button("Pause", NebulaButton.NEUTRAL);
		private final JButton offlineButton =
				button("Unavailable", NebulaButton.DANGER);
		private final JButton drainButton =
				button("Drain", NebulaButton.WARNING);
		private final JButton redirectButton =
				button("Move pending here", NebulaButton.SUCCESS);
		private final JSlider capacitySlider = new JSlider(10, 200, 100);
		private double pulse;
		private boolean updating;

		LiveNodeCard(final int nodeIndex)
		{
			super(new BorderLayout(10, 10));
			this.nodeIndex = nodeIndex;
			setOpaque(false);
			setBorder(new EmptyBorder(14, 18, 14, 14));
			setPreferredSize(new Dimension(330, 302));

			JPanel top = new JPanel(new BorderLayout(8, 0));
			top.setOpaque(false);
			JPanel labels = new JPanel();
			labels.setOpaque(false);
			labels.setLayout(new BoxLayout(labels, BoxLayout.Y_AXIS));
			title.setFont(NebulaDesign.fontSemiBold(14.5f));
			title.setForeground(TEXT);
			title.lockForeground();
			role.setFont(NebulaDesign.fontMedium(10.5f));
			role.setForeground(MUTED);
			role.lockForeground();
			labels.add(title);
			labels.add(role);
			state.setFont(NebulaDesign.fontMedium(9.5f));
			state.setHorizontalAlignment(SwingConstants.CENTER);
			state.setBorder(new EmptyBorder(4, 10, 4, 10));
			JPanel stateHolder = new JPanel(new GridBagLayout());
			stateHolder.setOpaque(false);
			stateHolder.add(state);
			top.add(labels, BorderLayout.CENTER);
			top.add(stateHolder, BorderLayout.EAST);

			JPanel center = new JPanel(new GridLayout(4, 1, 0, 7));
			center.setOpaque(false);
			metrics.setFont(NebulaDesign.fontBody(11f));
			metrics.setForeground(MUTED);
			metrics.lockForeground();
			queueStats.setFont(NebulaDesign.fontMedium(11f));
			queueStats.setForeground(TEXT);
			queueStats.lockForeground();
			styleProgress(inputProgress, BLUE);
			styleProgress(shardProgress, GREEN);
			inputProgress.setPreferredSize(new Dimension(120, 24));
			shardProgress.setPreferredSize(new Dimension(120, 24));
			center.add(metrics);
			center.add(inputProgress);
			center.add(queueStats);
			center.add(shardProgress);

			capacitySlider.setOpaque(false);
			capacitySlider.setMajorTickSpacing(50);
			capacitySlider.setPaintTicks(false);
			capacitySlider.setUI(new GaugeSliderUI(capacitySlider));
			capacitySlider.setPreferredSize(new Dimension(140, 30));
			capacitySlider.setCursor(
					Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
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
					StudioNightTheme.isNightMode() ? 46 : 30));
			state.setForeground(accent);

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
			NebulaDesign.prepare(g);
			int w = getWidth();
			int h = getHeight();
			LiveWorkloadAdjustmentRegistry.NodeControl control =
					liveSnapshot.controlForNode(nodeIndex);
			Color accent = control.isUnavailable() ? RED
					: control.isDraining() ? AMBER
							: control.isPaused() ? AMBER
							: control.getCapacityScale() > 1.05 ? GREEN : BLUE;
			boolean night = StudioNightTheme.isNightMode();
			if (!night)
			{
				g.setColor(new Color(23, 35, 60, 14));
				g.fillRoundRect(1, 3, w - 2, h - 3, 15, 15);
			}
			int radius = NebulaDesign.RADIUS_CARD;
			float glow = (float)(0.030 + 0.030 * (1.0 + Math.sin(pulse)));
			g.setColor(SURFACE);
			g.fillRoundRect(0, 0, w - 1, h - 2, radius, radius);
			g.setPaint(new GradientPaint(
					0,
					0,
					NebulaDesign.withAlpha(accent, 0),
					w,
					h,
					NebulaDesign.withAlpha(accent,
							Math.round(255 * glow) + (night ? 14 : 0))));
			g.fillRoundRect(0, 0, w - 1, h - 2, radius, radius);
			g.setColor(NebulaDesign.withAlpha(accent, night ? 130 : 100));
			g.setStroke(new BasicStroke(1.2f));
			g.drawRoundRect(0, 0, w - 1, h - 2, radius, radius);
			g.setColor(accent);
			g.fillRoundRect(0, 6, 4, h - 14, 4, 4);
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
			NebulaDesign.prepare(g);
			int w = getWidth();
			int h = getHeight();
			int radius = NebulaDesign.RADIUS_CARD;
			boolean night = StudioNightTheme.isNightMode();
			Color queueEnd = themed(
					StudioNightTheme.blend(
							NebulaDesign.DAY_SURFACE,
							NebulaDesign.DAY_SUCCESS,
							0.05f),
					StudioNightTheme.blend(
							NebulaDesign.NIGHT_SURFACE,
							NebulaDesign.NIGHT_SUCCESS,
							0.08f));
			if (!night)
			{
				g.setColor(new Color(23, 35, 60, 14));
				g.fillRoundRect(1, 3, w - 2, h - 3, radius + 1, radius + 1);
			}
			g.setPaint(new GradientPaint(
					0,
					0,
					SURFACE,
					w,
					h,
					queueEnd));
			g.fillRoundRect(0, 0, w - 1, h - 2, radius, radius);
			g.setColor(BORDER);
			g.drawRoundRect(0, 0, w - 1, h - 2, radius, radius);
			drawTitle(g, w);
			drawMetrics(g, w);
			drawFlow(g, w, h);
			g.dispose();
		}

		private void drawTitle(Graphics2D g, int width)
		{
			g.setColor(TEXT);
			g.setFont(NebulaDesign.fontSemiBold(16f));
			g.drawString("Safe-Boundary Work Queue", 22, 32);
			g.setColor(MUTED);
			g.setFont(NebulaDesign.fontBody(11.5f));
			String detail = RecommendationModeSupport.algorithmName(algorithm)
					+ " | " + plan.getMode().getLabel()
					+ " | " + (live.hasActiveControls()
							? live.compactSummary()
							: "no live overrides");
			g.drawString(shortText(detail, Math.max(24, width / 8)), 22, 52);
		}

		private void drawMetrics(Graphics2D g, int width)
		{
			int x = 22;
			int y = 72;
			int gap = 10;
			String policy = live.getPolicyOverride() == null
					? plan.getPolicy().getLabel()
					: live.getPolicyOverride().getLabel();
			String done = queue.getCompletedCount() + "/" + queue.getTotalShards();
			int columns = width < 580 ? 2 : 4;
			int mw = Math.max(76, (width - 44 - gap * (columns - 1)) / columns);
			int rowHeight = 54;
			for (int i = 0; i < 4; i++)
			{
				int column = i % columns;
				int row = i / columns;
				int cellX = x + (mw + gap) * column;
				int cellY = y + row * rowHeight;
				if (i == 0)
				{
					drawMetric(g, cellX, cellY, mw, "waiting",
							String.valueOf(queue.getWaitingCount()), PURPLE);
				}
				else if (i == 1)
				{
					drawMetric(g, cellX, cellY, mw, "active",
							String.valueOf(queue.getActiveCount()), AMBER);
				}
				else if (i == 2)
				{
					drawMetric(g, cellX, cellY, mw, "done", done, GREEN);
				}
				else
				{
					drawMetric(g, cellX, cellY, mw, "policy", policy, BLUE);
				}
			}
		}

		private void drawFlow(Graphics2D g, int width, int height)
		{
			int top = width < 580 ? 192 : 138;
			int bottom = height - 20;
			if (width < 580)
			{
				drawStackedFlow(g, width, top, Math.max(150, bottom - top));
				return;
			}
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

		/**
		 * Narrow columns cannot fit queue, node lanes, and merge side by side,
		 * so the same three stages stack vertically with downward flow arrows.
		 */
		private void drawStackedFlow(Graphics2D g, int width, int top, int areaH)
		{
			int x = 24;
			int w = Math.max(120, width - 48);
			int gap = 16;
			int mergeH = 88;
			int remaining = Math.max(180, areaH - mergeH - gap * 2);
			int queueH = Math.max(104, Math.round(remaining * 0.46f));
			int nodeH = Math.max(104, remaining - queueH);
			int centerX = x + w / 2;

			drawQueueBucket(g, x, top, w, queueH);
			int nodeY = top + queueH + gap;
			drawNodeLanes(g, x, nodeY, w, nodeH);
			int mergeY = nodeY + nodeH + gap;
			drawMerge(g, x, mergeY, w, mergeH);

			drawArrow(g, centerX, top + queueH + 2, centerX, nodeY - 5, BLUE);
			drawArrow(g, centerX, nodeY + nodeH + 2, centerX, mergeY - 5, GREEN);
			drawPacket(g, centerX, top + queueH + 3, centerX, nodeY - 7,
					BLUE, animation);
			drawPacket(g, centerX, nodeY + nodeH + 3, centerX, mergeY - 7,
					GREEN, (animation + 0.45) % 1.0);
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
			g.setFont(NebulaDesign.fontMono(9.5f));
			String claimText = claimed + " claimed / " + done + " done";
			if (w < 210)
			{
				g.drawString(done + "/" + claimed + " done",
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
			g.setColor(NebulaDesign.SUCCESS_SOFT);
			g.fillRoundRect(x, y, w, h, 16, 16);
			g.setColor(NebulaDesign.withAlpha(GREEN, 130));
			g.setStroke(new BasicStroke(1.4f));
			g.drawRoundRect(x, y, w, h, 16, 16);
			g.setFont(NebulaDesign.fontSection());
			g.setColor(MUTED);
			g.drawString("MERGE", x + 20, y + 26);
			g.setFont(NebulaDesign.fontSemiBold(18f));
			g.setColor(GREEN);
			g.drawString(queue.getCompletedCount() + "/"
					+ queue.getTotalShards(), x + 18, y + 56);
			g.setFont(NebulaDesign.fontSmall());
			g.setColor(MUTED);
			g.drawString("complete work", x + 18, y + 76);
		}
	}

	private static void styleProgress(JProgressBar bar, Color color)
	{
		bar.setStringPainted(true);
		bar.setForeground(color);
		bar.setBackground(track());
		bar.setBorder(BorderFactory.createEmptyBorder());
		bar.setFont(NebulaDesign.fontMono(9.5f));
		bar.setOpaque(false);
		bar.setUI(new MeterUI());
	}

	private static int countFor(Map<Integer,Integer> counts, int nodeIndex)
	{
		Integer value = counts.get(Integer.valueOf(nodeIndex));
		return value == null ? 0 : Math.max(0, value.intValue());
	}

	private static void drawTitle(Graphics2D g, String text, int x, int y)
	{
		g.setColor(TEXT);
		g.setFont(NebulaDesign.fontSemiBold(14f));
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
		g.setColor(glass(216));
		g.fillRoundRect(x, y, w, h, 12, 12);
		g.setColor(NebulaDesign.withAlpha(accent, 70));
		g.drawRoundRect(x, y, w, h, 12, 12);
		g.setColor(NebulaDesign.withAlpha(accent,
				StudioNightTheme.isNightMode() ? 40 : 26));
		g.fillRoundRect(x, y, w, 28, 12, 12);
		g.setColor(MUTED);
		g.setFont(NebulaDesign.fontSection());
		String caption = label.toUpperCase(Locale.US);
		while (caption.length() > 4
				&& g.getFontMetrics().stringWidth(caption) > w - 26)
		{
			caption = caption.substring(0, caption.length() - 2) + "…";
		}
		g.drawString(caption, x + 14, y + 19);
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
		g.setColor(SURFACE_ALT);
		g.fillRoundRect(x, y, w, 52, 12, 12);
		g.setColor(BORDER);
		g.drawRoundRect(x, y, w, 52, 12, 12);
		g.setColor(accent);
		g.fillOval(x + 12, y + 12, 7, 7);
		g.setColor(NebulaDesign.TEXT_TERTIARY);
		g.setFont(NebulaDesign.fontSection());
		g.drawString(label.toUpperCase(Locale.US), x + 25, y + 19);
		g.setColor(TEXT);
		g.setFont(NebulaDesign.fontSemiBold(16f));
		if (g.getFontMetrics().stringWidth(value) > w - 22)
		{
			g.setFont(NebulaDesign.fontSemiBold(12.5f));
		}
		String shown = value;
		while (shown.length() > 4
				&& g.getFontMetrics().stringWidth(shown) > w - 22)
		{
			shown = shown.substring(0, shown.length() - 2) + "…";
		}
		g.drawString(shown, x + 12, y + 41);
	}

	private static void drawToken(
			Graphics2D g,
			int x,
			int y,
			String text,
			Color accent)
	{
		g.setColor(NebulaDesign.withAlpha(accent,
				StudioNightTheme.isNightMode() ? 48 : 32));
		g.fillRoundRect(x, y, 26, 23, 8, 8);
		g.setColor(NebulaDesign.withAlpha(accent, 150));
		g.drawRoundRect(x, y, 26, 23, 8, 8);
		g.setColor(accent);
		g.setFont(NebulaDesign.fontMono(9.5f));
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
		g.fillRoundRect(x, y, w, 6, 6, 6);
		g.setColor(accent);
		g.fillRoundRect(x, y, (int)Math.round(w * p), 6, 6, 6);
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
			g.setFont(NebulaDesign.fontMedium(10.5f));
			g.setColor(MUTED);
			String label = RecommendationModeSupport.algorithmName(algorithm)
					+ " live controls | "
					+ queue.getCompletedCount() + "/"
					+ queue.getTotalShards() + " queue work complete";
			g.drawString(shortText(label, Math.max(24, w / 9)), 24, h - 12);
			g.dispose();
		}
	}

	/** Rounded Nebula card: surface fill, soft day shadow, subtle border. */
	private static class CardPanel extends JPanel
	{
		CardPanel(LayoutManager layout)
		{
			super(layout);
			setOpaque(false);
		}

		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g2 = (Graphics2D)graphics.create();
			NebulaWidgets.paintCard(
					g2, 0, 0, getWidth(), getHeight(),
					NebulaDesign.RADIUS_CARD);
			g2.dispose();
			super.paintComponent(graphics);
		}
	}

	/** Hero card with the Nebula aurora wash. */
	private static final class HeroCard extends JPanel
	{
		HeroCard(LayoutManager layout)
		{
			super(layout);
			setOpaque(false);
		}

		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g2 = (Graphics2D)graphics.create();
			NebulaDesign.prepare(g2);
			int w = getWidth();
			int h = getHeight();
			boolean night = StudioNightTheme.isNightMode();
			if (!night)
			{
				g2.setColor(new Color(23, 35, 60, 14));
				g2.fillRoundRect(1, 3, w - 2, h - 3,
						NebulaDesign.RADIUS_CARD + 1, NebulaDesign.RADIUS_CARD + 1);
			}
			RoundRectangle2D shape = new RoundRectangle2D.Float(
					0, 0, w - 1, h - 2,
					NebulaDesign.RADIUS_CARD, NebulaDesign.RADIUS_CARD);
			g2.setColor(SURFACE);
			g2.fill(shape);
			java.awt.Shape oldClip = g2.getClip();
			g2.clip(shape);
			int accentAlpha = night ? 26 : 15;
			int purpleAlpha = night ? 24 : 13;
			g2.setPaint(new RadialGradientPaint(
					new Point2D.Float(w * 0.22f, 0f),
					Math.max(1f, w * 0.42f),
					new float[] { 0f, 1f },
					new Color[] {
							NebulaDesign.withAlpha(NebulaDesign.ACCENT, accentAlpha),
							NebulaDesign.withAlpha(NebulaDesign.ACCENT, 0)
					}));
			g2.fillRect(0, 0, w, h);
			g2.setPaint(new RadialGradientPaint(
					new Point2D.Float(w * 0.85f, h),
					Math.max(1f, w * 0.5f),
					new float[] { 0f, 1f },
					new Color[] {
							NebulaDesign.withAlpha(NebulaDesign.PURPLE, purpleAlpha),
							NebulaDesign.withAlpha(NebulaDesign.PURPLE, 0)
					}));
			g2.fillRect(0, 0, w, h);
			g2.setClip(oldClip);
			g2.setColor(BORDER);
			g2.draw(shape);
			g2.dispose();
			super.paintComponent(graphics);
		}
	}

	/** Rounded secondary-surface card (guidance, KPI tiles). */
	private static final class AltCard extends JPanel
	{
		AltCard(LayoutManager layout)
		{
			super(layout);
			setOpaque(false);
		}

		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g2 = (Graphics2D)graphics.create();
			NebulaDesign.prepare(g2);
			g2.setColor(SURFACE_ALT);
			g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
			g2.setColor(BORDER);
			g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
			g2.dispose();
			super.paintComponent(graphics);
		}
	}

	/** Rounded console well for the event log. */
	private static final class ConsoleCard extends JPanel
	{
		ConsoleCard(LayoutManager layout)
		{
			super(layout);
			setOpaque(false);
		}

		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g2 = (Graphics2D)graphics.create();
			NebulaDesign.prepare(g2);
			g2.setColor(NebulaDesign.CONSOLE);
			g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1,
					NebulaDesign.RADIUS_CONSOLE, NebulaDesign.RADIUS_CONSOLE);
			g2.setColor(BORDER);
			g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1,
					NebulaDesign.RADIUS_CONSOLE, NebulaDesign.RADIUS_CONSOLE);
			g2.dispose();
			super.paintComponent(graphics);
		}
	}

	/** Soft-tint status pill label; colors come from set background/foreground. */
	private static final class Pill extends JLabel
	{
		Pill(String text)
		{
			super(text);
			setOpaque(false);
			setFont(NebulaDesign.fontSmall());
			setBorder(new EmptyBorder(4, 12, 4, 12));
		}

		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g2 = (Graphics2D)graphics.create();
			NebulaDesign.prepare(g2);
			if (isBackgroundSet())
			{
				g2.setColor(getBackground());
				int h = getHeight();
				g2.fillRoundRect(0, 0, getWidth(), h, h, h);
			}
			g2.dispose();
			super.paintComponent(graphics);
		}
	}

	/** Label that keeps its adaptive foreground through theme-walk repaints. */
	private static final class LockedLabel extends JLabel
	{
		private boolean locked;

		LockedLabel(String text)
		{
			super(text);
		}

		void lockForeground()
		{
			locked = true;
		}

		public void setForeground(Color color)
		{
			if (!locked)
			{
				super.setForeground(color);
			}
		}
	}

	/** Leading 8px hue dot for KPI tile eyebrows. */
	private static final class DotIcon implements javax.swing.Icon
	{
		private final Color hue;

		DotIcon(Color hue)
		{
			this.hue = hue;
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
			Graphics2D g2 = (Graphics2D)graphics.create();
			NebulaDesign.prepare(g2);
			g2.setColor(hue);
			g2.fillOval(x, y, 8, 8);
			g2.dispose();
		}
	}

	/** Nebula button: primary accent, neutral outline, or soft semantic tint. */
	private static final class NebulaButton extends JButton
	{
		static final int PRIMARY = 0;
		static final int NEUTRAL = 1;
		static final int DANGER = 2;
		static final int WARNING = 3;
		static final int SUCCESS = 4;

		private final int kind;

		NebulaButton(String text, int kind)
		{
			super(text);
			this.kind = kind;
			setContentAreaFilled(false);
			setFocusPainted(false);
			setBorderPainted(false);
			setOpaque(false);
			setRolloverEnabled(true);
			setFont(NebulaDesign.fontMedium(12.5f));
			setBorder(new EmptyBorder(8, 14, 8, 14));
			setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		}

		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g2 = (Graphics2D)graphics.create();
			NebulaDesign.prepare(g2);
			int w = getWidth();
			int h = getHeight();
			boolean night = StudioNightTheme.isNightMode();
			boolean pressed = getModel().isArmed() && getModel().isPressed();
			boolean hover = getModel().isRollover();
			Color fill;
			Color textColor;
			Color outline = null;
			if (!isEnabled())
			{
				fill = SURFACE_ALT;
				textColor = NebulaDesign.TEXT_TERTIARY;
				outline = BORDER;
			}
			else if (kind == PRIMARY)
			{
				fill = pressed ? NebulaDesign.ACCENT_PRESS
						: hover ? NebulaDesign.ACCENT_HOVER : NebulaDesign.ACCENT;
				textColor = NebulaDesign.TEXT_ON_ACCENT;
			}
			else if (kind == NEUTRAL)
			{
				fill = hover || pressed
						? NebulaDesign.SURFACE_RAISED
						: (night ? NebulaDesign.NIGHT_SURFACE_RAISED : SURFACE);
				textColor = TEXT;
				outline = hover || pressed
						? NebulaDesign.withAlpha(NebulaDesign.ACCENT, 110)
						: NebulaDesign.BORDER_STRONG;
			}
			else
			{
				Color hue = kind == DANGER ? RED
						: kind == WARNING ? AMBER : GREEN;
				int fillAlpha = night
						? (hover || pressed ? 62 : 40)
						: (hover || pressed ? 44 : 26);
				fill = NebulaDesign.withAlpha(hue, fillAlpha);
				textColor = hue;
				outline = NebulaDesign.withAlpha(hue, hover || pressed ? 170 : 100);
			}
			g2.setColor(fill);
			g2.fillRoundRect(0, 0, w - 1, h - 1,
					NebulaDesign.RADIUS_CONTROL, NebulaDesign.RADIUS_CONTROL);
			if (kind == PRIMARY && hover && isEnabled())
			{
				g2.setColor(NebulaDesign.withAlpha(NebulaDesign.ACCENT, 60));
				g2.setStroke(new BasicStroke(2.4f));
				g2.drawRoundRect(-1, -1, w + 1, h + 1,
						NebulaDesign.RADIUS_CONTROL + 2, NebulaDesign.RADIUS_CONTROL + 2);
				g2.setStroke(new BasicStroke(1f));
			}
			if (outline != null)
			{
				g2.setColor(outline);
				g2.drawRoundRect(0, 0, w - 1, h - 1,
						NebulaDesign.RADIUS_CONTROL, NebulaDesign.RADIUS_CONTROL);
			}
			g2.setColor(textColor);
			g2.setFont(getFont());
			java.awt.FontMetrics metrics = g2.getFontMetrics();
			String label = getText();
			int available = w - 12;
			if (metrics.stringWidth(label) > available)
			{
				while (label.length() > 3
						&& metrics.stringWidth(label + "…") > available)
				{
					label = label.substring(0, label.length() - 1);
				}
				label = label + "…";
			}
			int textX = (w - metrics.stringWidth(label)) / 2;
			int textY = (h - metrics.getHeight()) / 2 + metrics.getAscent();
			g2.drawString(label, textX, textY);
			g2.dispose();
		}
	}

	/** Rounded surface-alt combo with a stroked chevron. */
	private static final class NebulaComboUI extends BasicComboBoxUI
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
							getWidth() / 2 - 1,
							getHeight() / 2 - 1,
							7,
							MUTED);
					g2.dispose();
				}
			};
			arrow.setPreferredSize(new Dimension(24, 24));
			arrow.setBorder(BorderFactory.createEmptyBorder());
			arrow.setContentAreaFilled(false);
			arrow.setFocusable(false);
			arrow.setOpaque(false);
			return arrow;
		}

		public void paint(Graphics graphics, JComponent component)
		{
			Graphics2D g2 = (Graphics2D)graphics.create();
			NebulaDesign.prepare(g2);
			int w = component.getWidth();
			int h = component.getHeight();
			g2.setColor(SURFACE_ALT);
			g2.fillRoundRect(0, 0, w - 1, h - 1,
					NebulaDesign.RADIUS_CONTROL, NebulaDesign.RADIUS_CONTROL);
			g2.setColor(comboBox.isPopupVisible()
					? NebulaDesign.withAlpha(NebulaDesign.ACCENT, 140)
					: NebulaDesign.BORDER_STRONG);
			g2.drawRoundRect(0, 0, w - 1, h - 1,
					NebulaDesign.RADIUS_CONTROL, NebulaDesign.RADIUS_CONTROL);
			g2.dispose();
			Rectangle bounds = rectangleForCurrentValue();
			paintCurrentValue(graphics, bounds, false);
		}

		public void paintCurrentValueBackground(
				Graphics graphics,
				Rectangle bounds,
				boolean hasFocus)
		{
			// The rounded fill is painted in paint().
		}
	}

	/** Slim rounded meter with a mono caption above the track. */
	private static final class MeterUI extends BasicProgressBarUI
	{
		public void paint(Graphics graphics, JComponent component)
		{
			Graphics2D g2 = (Graphics2D)graphics.create();
			NebulaDesign.prepare(g2);
			JProgressBar bar = (JProgressBar)component;
			int w = component.getWidth();
			int h = component.getHeight();
			int trackHeight = 6;
			int trackY = h - trackHeight - 1;
			String text = bar.isStringPainted() ? bar.getString() : null;
			if (text != null && text.length() > 0)
			{
				g2.setFont(bar.getFont());
				g2.setColor(MUTED);
				g2.drawString(text, 0, Math.max(9, trackY - 4));
			}
			g2.setColor(track());
			g2.fillRoundRect(0, trackY, w, trackHeight, trackHeight, trackHeight);
			int fillWidth = (int)Math.round(w * bar.getPercentComplete());
			if (fillWidth > 0)
			{
				g2.setColor(bar.getForeground());
				g2.fillRoundRect(0, trackY, Math.max(trackHeight, fillWidth),
						trackHeight, trackHeight, trackHeight);
			}
			g2.dispose();
		}
	}

	/** Capacity gauge slider: pill track, accent fill, live % readout. */
	private static final class GaugeSliderUI extends BasicSliderUI
	{
		GaugeSliderUI(JSlider slider)
		{
			super(slider);
		}

		public void paint(Graphics graphics, JComponent component)
		{
			super.paint(graphics, component);
			Graphics2D g2 = (Graphics2D)graphics.create();
			NebulaDesign.prepare(g2);
			g2.setFont(NebulaDesign.fontMono(9.5f));
			g2.setColor(NebulaDesign.TEXT_TERTIARY);
			String readout = slider.getValue() + "%";
			int textWidth = g2.getFontMetrics().stringWidth(readout);
			g2.drawString(readout, component.getWidth() - textWidth - 2, 10);
			g2.dispose();
		}

		public void paintTrack(Graphics graphics)
		{
			Graphics2D g2 = (Graphics2D)graphics.create();
			NebulaDesign.prepare(g2);
			int cy = trackRect.y + trackRect.height / 2;
			g2.setColor(track());
			g2.fillRoundRect(trackRect.x, cy - 3, trackRect.width, 6, 6, 6);
			int fillWidth = thumbRect.x + thumbRect.width / 2 - trackRect.x;
			if (fillWidth > 0)
			{
				g2.setColor(slider.isEnabled()
						? NebulaDesign.ACCENT
						: NebulaDesign.BORDER_STRONG);
				g2.fillRoundRect(trackRect.x, cy - 3,
						Math.min(trackRect.width, fillWidth), 6, 6, 6);
			}
			g2.dispose();
		}

		public void paintThumb(Graphics graphics)
		{
			Graphics2D g2 = (Graphics2D)graphics.create();
			NebulaDesign.prepare(g2);
			int cx = thumbRect.x + thumbRect.width / 2;
			int cy = thumbRect.y + thumbRect.height / 2;
			g2.setColor(slider.isEnabled()
					? NebulaDesign.ACCENT
					: NebulaDesign.BORDER_STRONG);
			g2.fillOval(cx - 7, cy - 7, 14, 14);
			g2.setColor(SURFACE);
			g2.fillOval(cx - 3, cy - 3, 6, 6);
			g2.dispose();
		}

		public void paintFocus(Graphics graphics)
		{
			// The accent thumb carries focus emphasis.
		}
	}
}
