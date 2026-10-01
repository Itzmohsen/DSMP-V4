package TwitterGatherDataFollowers.userRyersonU;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

/**
 * Step 3 of the Node Lab: the running system, live. Where the run is in its
 * pipeline, how far the current work phase has come against its lower bound,
 * every node's state and load with its controls (pause, drain, take out,
 * crash, repair, capacity), run-wide controls (switch policy, redirect,
 * reset), a live timeline of unit executions and the event feed. Every
 * control is recorded and replayed by the run's model check.
 */
final class NodeLabLivePage extends NodeLabPanel.Page
{
	private static final long serialVersionUID = 1L;
	private static final int FEED_SIZE = 40;
	private static final int MAX_BARS = 4000;

	private final NodeLabPanel lab;
	private final NodeLabUi.Page page = new NodeLabUi.Page(1440);
	private final JPanel idle = new JPanel(new BorderLayout());
	private final JPanel again = new JPanel(new BorderLayout());
	private final NodeLabUi.Button againStart =
			new NodeLabUi.Button("Start another run", NodeLabUi.ButtonKind.PRIMARY, NodeLabUi.Glyph.PLAY, true);
	private final NodeLabUi.Prose againHint = NodeLabUi.note(" ");
	private final JPanel live = NodeLabUi.stack(16);
	private final NodeLabUi.Text title = new NodeLabUi.Text("No run in progress", NebulaDesign.fontSemiBold(24f),
			NebulaDesign.TEXT_PRIMARY);
	private final NodeLabUi.Prose subtitle = NodeLabUi.prose(" ");
	private final JPanel headerChips = NodeLabPanel.flow(6);
	private final NodeLabUi.Text clock = new NodeLabUi.Text("—", NebulaDesign.fontSemiBold(30f),
			NebulaDesign.TEXT_PRIMARY);
	private final NodeLabUi.Text clockCaption = NodeLabUi.caption("model time since the run started");
	private final MilestoneTrack track = new MilestoneTrack();
	private final NodeLabCharts.TopologyView topology = new NodeLabCharts.TopologyView();
	private final NodeLabUi.Text phaseTitle = NodeLabUi.heading("No unit phase yet");
	private final NodeLabUi.Prose phaseDetail = NodeLabUi.note(" ");
	private final ProgressBar phaseProgress = new ProgressBar();
	private final JPanel phaseNumbers = NodeLabUi.grid(110, 4, 10);
	private final JPanel scenarioSteps = NodeLabUi.stack(6);
	private final JPanel nodeCards = NodeLabUi.grid(300, 4, 14);
	private final ArrayList<NodeControl> controls = new ArrayList<NodeControl>();
	private final NodeLabCharts.GanttView gantt = new NodeLabCharts.GanttView();
	private final JPanel feed = NodeLabUi.stack(0);
	private final JComboBox<SchedulerLabEngine.SchedulingPolicy> policyBox =
			NodeLabUi.combo(SchedulerLabEngine.SchedulingPolicy.values());
	private final NodeLabUi.Button switchPolicy =
			new NodeLabUi.Button("Switch remaining work", NodeLabUi.ButtonKind.SECONDARY, NodeLabUi.Glyph.REFRESH, true);
	private final NodeLabUi.Button planPolicy =
			new NodeLabUi.Button("Back to the plan's policy", NodeLabUi.ButtonKind.GHOST, NodeLabUi.Glyph.HOME, true);
	private final NodeLabUi.Stepper redirectCount = new NodeLabUi.Stepper(5, 1, 500);
	private final JComboBox<String> redirectNode = NodeLabUi.combo(new String[] {"Node 1"});
	private final NodeLabUi.Button redirect =
			new NodeLabUi.Button("Redirect", NodeLabUi.ButtonKind.SECONDARY, NodeLabUi.Glyph.ARROW_RIGHT, true);
	private final NodeLabUi.Button reset =
			new NodeLabUi.Button("Reset all controls", NodeLabUi.ButtonKind.GHOST, NodeLabUi.Glyph.REFRESH, true);
	private final NodeLabUi.Prose message = NodeLabUi.note(" ");
	private final NodeLabUi.Button idleStart =
			new NodeLabUi.Button("Start a run", NodeLabUi.ButtonKind.PRIMARY, NodeLabUi.Glyph.PLAY);
	private final NodeLabUi.Prose idleHint = NodeLabUi.note(" ");
	private final NodeLabCharts.TopologyView idleTopology = new NodeLabCharts.TopologyView();
	private final NodeLabUi.Text idleCluster = NodeLabUi.caption(" ");
	private String shownRun = "";
	private int shownEvents = -1;
	private int shownExecutions = -1;
	private String messageText = "";
	private long messageAt;

	NodeLabLivePage(NodeLabPanel lab)
	{
		this.lab = lab;
		page.section(header());
		idle.setOpaque(false);
		idle.add(idleCard(), BorderLayout.CENTER);
		again.setOpaque(false);
		again.add(againCard(), BorderLayout.CENTER);
		again.setVisible(false);
		live.setVisible(false);
		live.add(trackCard());
		JPanel middle = new JPanel(new BorderLayout(16, 0));
		middle.setOpaque(false);
		middle.add(topologyCard(), BorderLayout.CENTER);
		middle.add(NodeLabUi.fixedWidth(phaseCard(), 440), BorderLayout.EAST);
		live.add(middle);
		NodeLabUi.Card nodes = new NodeLabUi.Card("Nodes",
				"Each node's state and load right now, with its controls. Controls act at the next safe boundary"
						+ " and never change what the run computes.");
		nodes.action(NodeLabUi.help("What the controls do",
				"Pause: the node finishes nothing new and holds at its next safe boundary; resume undoes it.\n"
						+ "Take out: the node takes no new work; its queued units move to other nodes.\n"
						+ "Drain: as take out, and its running units are revoked and restart elsewhere.\n"
						+ "Crash: the node fails now, losing its work in progress; the coordinator notices after the"
						+ " detection timeout and reschedules. Repair brings it back with its data.\n"
						+ "Capacity: the node's speed as a share of its profile speed, from now on."));
		nodes.content(nodeCards);
		live.add(nodes);
		live.add(runControlsCard());
		NodeLabUi.Card timeline = new NodeLabUi.Card("Timeline",
				"Every unit execution so far, one row per lane. Hatched bars are executions lost to faults, crashes"
						+ " or revocations; shaded spans are downtime; markers are controls.");
		gantt.setEmptyText("No unit executions yet");
		timeline.content(gantt);
		live.add(timeline);
		NodeLabUi.Card events = new NodeLabUi.Card("Events", "Controls, faults, placements and phase changes, newest first.");
		events.content(feed);
		live.add(events);
		page.section(idle).section(again).section(live);
		add(NodeLabUi.scroll(page), BorderLayout.CENTER);
		refresh();
	}

	int refreshIntervalMs()
	{
		return NodeLabPanel.isRunning() || lab.launcher().isBusy() ? 400 : 2000;
	}

	// ------------------------------------------------------------ construction

	private JComponent header()
	{
		JPanel card = new JPanel(new BorderLayout(24, 0))
		{
			private static final long serialVersionUID = 1L;

			protected void paintComponent(Graphics graphics)
			{
				Graphics2D g = (Graphics2D)graphics.create();
				NodeLabUi.prepare(g);
				int w = getWidth();
				int h = getHeight() - 3;
				NodeLabUi.paintSurface(g, 0, 0, w, h, NebulaDesign.RADIUS_CARD + 4, NebulaDesign.SURFACE, true);
				g.clip(new RoundRectangle2D.Float(1, 1, w - 2, h - 2, NebulaDesign.RADIUS_CARD + 4,
						NebulaDesign.RADIUS_CARD + 4));
				Color glow = NodeLabPanel.isRunning() ? NebulaDesign.SUCCESS : NebulaDesign.INFO;
				g.setPaint(new java.awt.RadialGradientPaint(w * 0.9f, h * 0.2f, Math.max(w, h) * 0.5f,
						new float[] {0f, 1f}, new Color[] {NodeLabUi.alpha(glow, NodeLabUi.night() ? 60 : 34),
							NodeLabUi.alpha(glow, 0)}));
				g.fillRect(0, 0, w, h);
				g.dispose();
			}
		};
		card.setOpaque(false);
		card.setBorder(new EmptyBorder(22, 26, 24, 26));
		card.add(NodeLabUi.stack(6, NodeLabUi.eyebrow("Step 3 · Live"), title, subtitle, headerChips),
				BorderLayout.CENTER);
		JPanel right = NodeLabUi.stack(2, clock, clockCaption);
		clock.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
		card.add(right, BorderLayout.EAST);
		return card;
	}

	private JComponent idleCard()
	{
		NodeLabUi.Card card = new NodeLabUi.Card(null, null);
		idleStart.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent event)
			{
				lab.launcher().start(new Runnable()
				{
					public void run()
					{
						refresh();
					}
				});
				refresh();
			}
		});
		NodeLabUi.Button results = new NodeLabUi.Button("See recorded runs", NodeLabUi.ButtonKind.SECONDARY,
				NodeLabUi.Glyph.CHART);
		results.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent event)
			{
				lab.show(NodeLabPanel.RESULTS);
			}
		});
		JPanel actions = NodeLabUi.row(10, idleStart, results);
		JPanel center = NodeLabUi.stack(10, NodeLabOverviewPage.badge(NodeLabUi.Glyph.BOLT, NodeLabUi.Tone.SUCCESS, 56),
				NodeLabUi.title("Nothing is running"),
				NodeLabUi.prose("Start a run and watch it here: the pipeline's progress, every node's load and state,"
						+ " a live timeline of the work, and controls to pause, drain, crash or boost nodes while it"
						+ " runs. Everything you do is recorded and replayed by the model check."),
				actions, idleHint);
		center.setBorder(new EmptyBorder(18, 10, 18, 10));
		idleTopology.setPreferredSize(new Dimension(420, 300));
		idleTopology.setListener(new NodeLabCharts.NodeClickListener()
		{
			public void nodeClicked(int node)
			{
				lab.show(NodeLabPanel.CLUSTER);
			}
		});
		JPanel side = NodeLabUi.stack(6, NodeLabOverviewPage.sectionTitle("The cluster of the next run"), idleTopology,
				idleCluster);
		JPanel body = new JPanel(new BorderLayout(24, 0));
		body.setOpaque(false);
		body.add(NodeLabUi.top(center), BorderLayout.CENTER);
		body.add(NodeLabUi.fixedWidth(side, 440), BorderLayout.EAST);
		card.content(body);
		return card;
	}

	/** A slim card under a finished run: start the next one from here. */
	private JComponent againCard()
	{
		NodeLabUi.Card card = new NodeLabUi.Card(null, null);
		card.fill(NebulaDesign.SURFACE_ALT);
		againStart.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent event)
			{
				lab.launcher().start(new Runnable()
				{
					public void run()
					{
						refresh();
					}
				});
				refresh();
			}
		});
		NodeLabUi.Button edit = new NodeLabUi.Button("Change the plan", NodeLabUi.ButtonKind.GHOST,
				NodeLabUi.Glyph.SLIDERS, true);
		edit.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent event)
			{
				lab.show(NodeLabPanel.PLAN);
			}
		});
		JPanel row = new JPanel(new BorderLayout(14, 0));
		row.setOpaque(false);
		row.add(NodeLabOverviewPage.badge(NodeLabUi.Glyph.CHECK, NodeLabUi.Tone.SUCCESS, 34), BorderLayout.WEST);
		row.add(NodeLabUi.stack(2, NodeLabUi.strong("This run has finished; below is how it went."), againHint),
				BorderLayout.CENTER);
		row.add(NodeLabUi.top(NodeLabUi.row(8, edit, againStart)), BorderLayout.EAST);
		card.content(row);
		return card;
	}

	private JComponent trackCard()
	{
		NodeLabUi.Card card = new NodeLabUi.Card("Pipeline", null);
		card.content(track);
		return card;
	}

	private JComponent topologyCard()
	{
		NodeLabUi.Card card = new NodeLabUi.Card("Cluster now",
				"Node states and how busy each node's lanes are this moment.");
		topology.setListener(new NodeLabCharts.NodeClickListener()
		{
			public void nodeClicked(int node)
			{
				if (node >= 1 && node <= controls.size())
				{
					final NodeControl control = controls.get(node - 1);
					control.scrollRectToVisible(new java.awt.Rectangle(0, 0, control.getWidth(), control.getHeight()));
				}
			}
		});
		card.content(topology);
		return card;
	}

	private JComponent phaseCard()
	{
		NodeLabUi.Card card = new NodeLabUi.Card("Current work phase", null);
		card.content(NodeLabUi.stack(12, phaseTitle, phaseProgress, phaseNumbers, phaseDetail,
				NodeLabOverviewPage.sectionTitle("Scenario"), scenarioSteps));
		return card;
	}

	private JComponent runControlsCard()
	{
		NodeLabUi.Card card = new NodeLabUi.Card("Run-wide controls",
				"Switch the policy for the work that has not started, send queued work to one node, or undo every"
						+ " live control.");
		switchPolicy.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent event)
			{
				final SchedulerLabEngine.SchedulingPolicy policy =
						(SchedulerLabEngine.SchedulingPolicy)policyBox.getSelectedItem();
				act("Policy switched to " + policy.getLabel() + ".", new Action()
				{
					public void run(SchedulingRunContext context)
					{
						context.setPolicyOverride(policy);
					}
				});
			}
		});
		planPolicy.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent event)
			{
				act("The remaining work follows the plan's policy again.", new Action()
				{
					public void run(SchedulingRunContext context)
					{
						context.setPolicyOverride(null);
					}
				});
			}
		});
		redirect.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent event)
			{
				final int node = redirectNode.getSelectedIndex() + 1;
				final int count = redirectCount.getValue();
				act(null, new Action()
				{
					public void run(SchedulingRunContext context)
					{
						int moved = context.redirect(node, count);
						say(moved + " queued unit(s) moved to node " + node + "; the next " + count
								+ " input user(s) go to it.");
					}
				});
			}
		});
		reset.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent event)
			{
				act("Every live control was cleared.", new Action()
				{
					public void run(SchedulingRunContext context)
					{
						context.resetControls();
					}
				});
			}
		});
		JPanel policyRow = new JPanel(new BorderLayout(10, 0));
		policyRow.setOpaque(false);
		policyRow.add(NodeLabUi.top(policyBox), BorderLayout.CENTER);
		policyRow.add(NodeLabUi.row(8, switchPolicy, planPolicy), BorderLayout.EAST);
		JPanel redirectRow = new JPanel(new BorderLayout(10, 0));
		redirectRow.setOpaque(false);
		redirectRow.add(NodeLabUi.row(10, NodeLabUi.caption("Move up to"), redirectCount,
				NodeLabUi.caption("queued units to"), redirectNode), BorderLayout.CENTER);
		redirectRow.add(NodeLabUi.top(redirect), BorderLayout.EAST);
		JPanel grid = NodeLabUi.columns(420, 2, 24);
		grid.add(NodeLabUi.labeled("Scheduling policy", policyRow));
		grid.add(NodeLabUi.labeled("Redirect", redirectRow));
		card.content(NodeLabUi.stack(14, grid, NodeLabUi.spread(message, reset)));
		return card;
	}

	// ------------------------------------------------------------ actions

	private interface Action
	{
		void run(SchedulingRunContext context);
	}

	/** Runs a control on the live run; failures and results show as the page's message. */
	private void act(String success, Action action)
	{
		SchedulingRunContext context = activeContext();
		if (context == null)
		{
			say("No run is in progress.");
			return;
		}
		try
		{
			action.run(context);
			if (success != null)
			{
				say(success);
			}
		}
		catch (RuntimeException refused)
		{
			say(refused.getMessage() == null ? refused.toString() : refused.getMessage());
		}
		shownEvents = -1;
		refresh();
	}

	private void say(String text)
	{
		messageText = text == null ? "" : text;
		messageAt = System.currentTimeMillis();
		message.setText(messageText);
	}

	private static SchedulingRunContext activeContext()
	{
		SchedulingRunContext context = SchedulingRuntime.latest();
		return context == null || context.isClosed() || context.isFinished() ? null : context;
	}

	// ------------------------------------------------------------ refresh

	void refresh()
	{
		SchedulingRunContext context = SchedulingRuntime.latest();
		NodeLabRunLauncher launcher = lab.launcher();
		boolean running = context != null && !context.isFinished() && !context.isClosed();
		boolean show = context != null && (running || launcher.isBusy()
				|| System.currentTimeMillis() - context.getCreatedAtMillis() < 6L * 3600L * 1000L);
		if (context == null || !show)
		{
			live.setVisible(false);
			again.setVisible(false);
			idle.setVisible(true);
			title.setText(launcher.isBusy() ? launcher.getState().getLabel() : "No run in progress");
			subtitle.setText(launcher.isBusy() ? launcher.getMessage() : NodeLabPanel.planSummary() + " on "
					+ NodeLabPanel.clusterSummary() + ".");
			clock.setText("—");
			headerChips.removeAll();
			refreshIdle();
			page.revalidate();
			page.repaint();
			return;
		}
		idle.setVisible(false);
		again.setVisible(!running && !launcher.isBusy());
		refreshIdle();
		String blocker = launcher.blocker();
		againStart.setEnabled(blocker == null);
		againHint.setText(blocker == null ? "The next run uses " + NodeLabPanel.planSummary() + " on "
				+ NodeLabPanel.clusterSummary() + "." : blocker);
		live.setVisible(true);
		if (!context.getRunId().equals(shownRun))
		{
			shownRun = context.getRunId();
			shownEvents = -1;
			shownExecutions = -1;
			buildNodeCards(context);
		}
		refreshHeader(context, running);
		track.update(context);
		refreshTopology(context);
		refreshPhase(context);
		for (NodeControl control : controls)
		{
			control.update(context, running);
		}
		refreshRunControls(context, running);
		RunRecorder recorder = context.recorder();
		List<RunRecorder.Event> events = recorder.events();
		if (events.size() != shownEvents)
		{
			shownEvents = events.size();
			refreshFeed(events);
		}
		List<RunRecorder.UnitExecution> executions = recorder.executions();
		if (executions.size() != shownExecutions || running)
		{
			shownExecutions = executions.size();
			refreshGantt(context, executions, running);
		}
		if (messageText.length() > 0 && System.currentTimeMillis() - messageAt > 12000L)
		{
			say("");
		}
		page.revalidate();
		page.repaint();
	}

	private void refreshIdle()
	{
		ArrayList<NodeLabCharts.NodeView> views = new ArrayList<NodeLabCharts.NodeView>();
		for (NodeCapacityProfile profile : NodeCapacityRegistry.snapshot())
		{
			views.add(NodeLabCharts.NodeView.of(profile));
		}
		idleTopology.setNodes(views, NodeCapacityRegistry.clusterSettings().getCoordinatorSpeed());
		idleCluster.setText(NodeLabPanel.clusterSummary() + " · " + NodeLabPanel.planSummary());
		String blocker = lab.launcher().blocker();
		idleStart.setEnabled(blocker == null);
		NodeLabRunLauncher launcher = lab.launcher();
		idleHint.setText(launcher.getState() == NodeLabRunLauncher.State.FAILED ? launcher.getMessage()
				: blocker == null ? "The run uses " + NodeLabPanel.planSummary() + " on " + NodeLabPanel.clusterSummary()
				+ "." : blocker);
	}

	private void refreshHeader(SchedulingRunContext context, boolean running)
	{
		Double start = context.recorder().milestone("run-start");
		Double end = context.recorder().milestone("run-end");
		double now = running ? context.now() : end != null ? end.doubleValue() : context.now();
		String status = context.recorder().getStatus();
		if (running)
		{
			title.setText(start == null ? "Initializing the run" : "Running");
		}
		else
		{
			title.setText(status.startsWith("completed") ? "Run completed" : "Run " + status.split(":")[0]);
		}
		subtitle.setText(context.getMode().getLabel() + " · " + context.effectivePolicy().getLabel()
				+ (context.getPolicyOverride() != null ? " (switched from " + context.getBasePolicy().getLabel() + ")"
						: "") + " · " + context.getNodeCount() + " node" + (context.getNodeCount() == 1 ? "" : "s")
				+ (context.getScenario().isEmpty() ? "" : " · scenario “" + context.getScenario().getName() + "”")
				+ (!running && !status.startsWith("completed") && status.indexOf(':') > 0
						? " · " + status.substring(status.indexOf(':') + 1).trim() : ""));
		clock.setText(start == null ? "—" : NodeLabUi.duration(now - start.doubleValue()));
		clockCaption.setText(start == null ? "waiting for the run to start" : running
				? "model time since the run started" : "model time, start to end");
		headerChips.removeAll();
		headerChips.add(new NodeLabUi.Chip(running ? "live" : status.startsWith("completed") ? "completed"
				: status.split(":")[0], running ? NodeLabUi.Tone.SUCCESS : status.startsWith("completed")
				? NodeLabUi.Tone.ACCENT : NodeLabUi.Tone.DANGER, true));
		if (context.isEmulating())
		{
			headerChips.add(new NodeLabUi.Chip(String.format(Locale.US, "time dilation %.2f",
					context.clock().getDilation()), NodeLabUi.Tone.PURPLE, false));
		}
		double host = context.hostUtilization();
		if (!Double.isNaN(host) && running)
		{
			headerChips.add(new NodeLabUi.Chip(String.format(Locale.US, "host CPU %.0f%%", host * 100.0),
					host > 0.85 ? NodeLabUi.Tone.WARNING : NodeLabUi.Tone.INFO, false));
		}
		int crashes = 0;
		for (int node = 1; node <= context.getNodeCount(); node++)
		{
			crashes += context.crashCount(node);
		}
		if (crashes > 0)
		{
			headerChips.add(new NodeLabUi.Chip(crashes + " crash" + (crashes == 1 ? "" : "es"), NodeLabUi.Tone.DANGER,
					false));
		}
		if (!running && context.getRecordDirectory() != null)
		{
			final String runId = context.getRunId();
			NodeLabUi.Button open = new NodeLabUi.Button("Open its results", NodeLabUi.ButtonKind.PRIMARY,
					NodeLabUi.Glyph.CHART, true);
			open.addActionListener(new ActionListener()
			{
				public void actionPerformed(ActionEvent event)
				{
					lab.openRun(runId);
				}
			});
			headerChips.add(open);
		}
		headerChips.revalidate();
	}

	private void refreshTopology(SchedulingRunContext context)
	{
		WorkDispatcher dispatcher = currentDispatcher(context);
		Map<Integer,WorkDispatcher.NodeStats> stats = new HashMap<Integer,WorkDispatcher.NodeStats>();
		if (dispatcher != null)
		{
			for (WorkDispatcher.NodeStats stat : dispatcher.nodeStats())
			{
				stats.put(Integer.valueOf(stat.getNode()), stat);
			}
		}
		ArrayList<NodeLabCharts.NodeView> views = new ArrayList<NodeLabCharts.NodeView>();
		List<NodeSpec> specs = context.currentSpecs();
		for (NodeCapacityProfile profile : context.getProfiles())
		{
			int node = profile.getNodeIndex();
			NodeLabCharts.NodeView view = NodeLabCharts.NodeView.of(profile);
			NodeLabCharts.NodeView live = new NodeLabCharts.NodeView(node, view.role,
					node <= specs.size() ? specs.get(node - 1).getSpeed() : view.speed, view.lanes, view.latencyMs,
					view.bandwidthMbps);
			live.crashes = view.crashes;
			live.faults = view.faults;
			live.state = stateText(context, node);
			WorkDispatcher.NodeStats stat = stats.get(Integer.valueOf(node));
			live.activity = stat == null ? Double.NaN : Math.min(1.0, stat.getRunning() / (double)Math.max(1, view.lanes));
			views.add(live);
		}
		topology.setNodes(views, context.getSettings().getCoordinatorSpeed());
	}

	static String stateText(SchedulingRunContext context, int node)
	{
		if (context.isDown(node))
		{
			return "down";
		}
		if (context.isDraining(node))
		{
			return "draining";
		}
		switch (context.operatorState(node))
		{
			case PAUSED:
				return "paused";
			case UNAVAILABLE:
				return "unavailable";
			default:
				return "active";
		}
	}

	private static WorkDispatcher currentDispatcher(SchedulingRunContext context)
	{
		SchedulingRunContext.PhaseEntry running = context.runningPhase();
		SchedulingRunContext.PhaseEntry entry = running != null ? running : context.latestPhase();
		return entry == null ? null : entry.getPhase().getDispatcher();
	}

	private void refreshPhase(SchedulingRunContext context)
	{
		SchedulingRunContext.PhaseEntry running = context.runningPhase();
		SchedulingRunContext.PhaseEntry entry = running != null ? running : context.latestPhase();
		phaseNumbers.removeAll();
		if (entry == null)
		{
			phaseTitle.setText("No unit phase yet");
			phaseDetail.setText("The run is delivering its input; unit phases (candidate scoring, model training,"
					+ " target scoring) open later and are scheduled by the policy.");
			phaseProgress.set(0.0, Double.NaN, false);
		}
		else
		{
			WorkDispatcher dispatcher = entry.getPhase().getDispatcher();
			int total = dispatcher.getUnitCount();
			int committed = dispatcher.getCommittedCount();
			phaseTitle.setText(entry.getLabel() + (running == null ? " · finished" : ""));
			double elapsed = (running == null ? dispatcher.getLastCommitTime() : context.now())
					- entry.getOpenedAtModelMs();
			double bound = dispatcher.lowerBound();
			phaseProgress.set(total == 0 ? 1.0 : committed / (double)total,
					bound > 0.0 && elapsed > 0.0 ? Math.min(1.0, bound / elapsed) : Double.NaN, running != null);
			phaseNumbers.add(number("Done", committed + " / " + total));
			phaseNumbers.add(number("Running", Integer.toString(dispatcher.count(WorkDispatcher.UnitState.RUNNING))));
			phaseNumbers.add(number("Waiting", Integer.toString(dispatcher.count(WorkDispatcher.UnitState.BACKLOG)
					+ dispatcher.count(WorkDispatcher.UnitState.PLANNED) + dispatcher.count(WorkDispatcher.UnitState.RESERVED))));
			phaseNumbers.add(number("Elapsed", NodeLabUi.duration(Math.max(0.0, elapsed))));
			phaseDetail.setText(String.format(Locale.US,
					"%s, %s. Lower bound %s: no schedule could finish this phase faster on these nodes%s.",
					dispatcher.getPolicy().getLabel(), dispatcher.getMode().name().toLowerCase(Locale.US),
					NodeLabUi.duration(bound), Double.isNaN(bound) || elapsed <= 0.0 ? ""
							: String.format(Locale.US, " (%.0f%% of the elapsed time)", 100.0 * Math.min(1.0,
									bound / elapsed))));
		}
		phaseNumbers.revalidate();
		scenarioSteps.removeAll();
		Scenario scenario = context.getScenario();
		if (scenario.isEmpty())
		{
			scenarioSteps.add(NodeLabUi.note("No scenario: only your live controls act on this run."));
		}
		else
		{
			HashSet<Integer> fired = new HashSet<Integer>();
			for (RunRecorder.Event event : context.recorder().events())
			{
				if ("scenario".equals(event.kind) && event.detail.startsWith("Line "))
				{
					String number = event.detail.substring(5, event.detail.indexOf(':') > 5 ? event.detail.indexOf(':')
							: event.detail.length());
					fired.add(Integer.valueOf(JsonText.parseInt(number, -1)));
				}
			}
			for (Scenario.Step step : scenario.getSteps())
			{
				boolean done = fired.contains(Integer.valueOf(step.getLine()));
				scenarioSteps.add(stepRow(step, done));
			}
		}
		scenarioSteps.revalidate();
	}

	private static JComponent stepRow(Scenario.Step step, boolean done)
	{
		JPanel row = new JPanel(new BorderLayout(8, 0));
		row.setOpaque(false);
		row.add(new NodeLabUi.Chip(done ? "done" : "waiting", done ? NodeLabUi.Tone.SUCCESS : NodeLabUi.Tone.NEUTRAL,
				true), BorderLayout.WEST);
		NodeLabUi.Text text = done ? NodeLabUi.strong(step.describe()) : NodeLabUi.caption(step.describe());
		row.add(text, BorderLayout.CENTER);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		return row;
	}

	private static JComponent number(String label, String value)
	{
		return NodeLabUi.stack(2, NodeLabUi.eyebrow(label),
				new NodeLabUi.Text(value, NebulaDesign.fontSemiBold(15f), NebulaDesign.TEXT_PRIMARY));
	}

	private void buildNodeCards(SchedulingRunContext context)
	{
		nodeCards.removeAll();
		controls.clear();
		String[] names = new String[context.getNodeCount()];
		for (int node = 1; node <= context.getNodeCount(); node++)
		{
			NodeControl control = new NodeControl(node, context);
			controls.add(control);
			nodeCards.add(control);
			names[node - 1] = "Node " + node;
		}
		redirectNode.setModel(new javax.swing.DefaultComboBoxModel<String>(names));
		nodeCards.revalidate();
	}

	private void refreshRunControls(SchedulingRunContext context, boolean running)
	{
		boolean emulated = context.getMode() != SchedulingRunContext.Mode.BASELINE;
		policyBox.setEnabled(running && emulated);
		switchPolicy.setEnabled(running && emulated);
		planPolicy.setEnabled(running && emulated && context.getPolicyOverride() != null);
		redirect.setEnabled(running && context.getNodeCount() > 1);
		redirectNode.setEnabled(running);
		reset.setEnabled(running && context.hasActiveControls());
		if (!emulated && running && messageText.length() == 0)
		{
			message.setText("The hardware mode places work as V3 did; policy switches need an emulated mode.");
		}
		else if (messageText.length() == 0)
		{
			message.setText(" ");
		}
	}

	private void refreshFeed(List<RunRecorder.Event> events)
	{
		feed.removeAll();
		int shown = 0;
		for (int index = events.size() - 1; index >= 0 && shown < FEED_SIZE; index--, shown++)
		{
			RunRecorder.Event event = events.get(index);
			JPanel row = new JPanel(new BorderLayout(12, 0));
			row.setOpaque(false);
			row.setBorder(new EmptyBorder(6, 0, 6, 0));
			NodeLabUi.Text time = NodeLabUi.mono(NodeLabUi.duration(event.at));
			time.setPreferredSize(new Dimension(86, time.getPreferredSize().height));
			JPanel left = new JPanel(new BorderLayout(8, 0));
			left.setOpaque(false);
			left.add(time, BorderLayout.WEST);
			left.add(new NodeLabUi.Chip(event.kind, eventTone(event.kind), true), BorderLayout.EAST);
			row.add(left, BorderLayout.WEST);
			row.add(NodeLabUi.note(event.detail), BorderLayout.CENTER);
			row.setAlignmentX(Component.LEFT_ALIGNMENT);
			feed.add(row);
		}
		if (events.isEmpty())
		{
			feed.add(NodeLabUi.note("No events yet."));
		}
		feed.revalidate();
	}

	static NodeLabUi.Tone eventTone(String kind)
	{
		String k = kind == null ? "" : kind.toLowerCase(Locale.US);
		if (k.contains("crash") || k.contains("fail") || k.contains("fault") || k.contains("error"))
		{
			return NodeLabUi.Tone.DANGER;
		}
		if (k.contains("pause") || k.contains("drain") || k.contains("unavailable") || k.contains("revok")
				|| k.contains("stale"))
		{
			return NodeLabUi.Tone.WARNING;
		}
		if (k.contains("repair") || k.contains("resume") || k.contains("available") || k.contains("recover"))
		{
			return NodeLabUi.Tone.SUCCESS;
		}
		if (k.contains("policy") || k.contains("scenario"))
		{
			return NodeLabUi.Tone.PURPLE;
		}
		if (k.contains("phase") || k.contains("capacity") || k.contains("redirect"))
		{
			return NodeLabUi.Tone.INFO;
		}
		return NodeLabUi.Tone.NEUTRAL;
	}

	private void refreshGantt(SchedulingRunContext context, List<RunRecorder.UnitExecution> executions, boolean running)
	{
		NodeLabCharts.GanttData data = NodeLabCharts.GanttData.of(context.getProfiles(), executions,
				context.recorder().controls(), context.recorder().phases(), MAX_BARS);
		if (data.bars.isEmpty())
		{
			gantt.setEmptyText(running ? "No unit executions yet: the run is delivering its input"
					: "This run executed no work units");
			gantt.set(null, null, null, null, null, 0.0, 1.0);
			return;
		}
		double end = running ? context.now() : data.end;
		gantt.set(data.rows, data.colors, data.bars, data.markers, data.bands, data.start, Math.max(end, data.start + 1.0));
	}

	// ------------------------------------------------------------ node control card

	private final class NodeControl extends NodeLabUi.Card
	{
		private static final long serialVersionUID = 1L;
		private final int node;
		private final NodeLabUi.Chip state = new NodeLabUi.Chip("active", NodeLabUi.Tone.SUCCESS, true);
		private final NodeLabUi.Text detail = NodeLabUi.caption(" ");
		private final LoadBar load = new LoadBar();
		private final NodeLabUi.Text counts = NodeLabUi.caption(" ");
		private final NodeLabUi.Button pause = new NodeLabUi.Button("Pause", NodeLabUi.ButtonKind.SECONDARY,
				NodeLabUi.Glyph.PAUSE, true);
		private final NodeLabUi.Button takeOut = new NodeLabUi.Button("Take out", NodeLabUi.ButtonKind.SECONDARY,
				NodeLabUi.Glyph.MINUS, true);
		private final NodeLabUi.Button drain = new NodeLabUi.Button("Drain", NodeLabUi.ButtonKind.SECONDARY,
				NodeLabUi.Glyph.DRAIN, true);
		private final NodeLabUi.Button crash = new NodeLabUi.Button("Crash", NodeLabUi.ButtonKind.DANGER,
				NodeLabUi.Glyph.CRASH, true);
		private final NodeLabUi.Slider capacity;
		private boolean loading;

		NodeControl(final int node, SchedulingRunContext context)
		{
			super(null, null);
			this.node = node;
			accent(NodeLabUi.nodeColor(node));
			NodeCapacityProfile profile = node <= context.getProfiles().size() ? context.getProfiles().get(node - 1)
					: null;
			JPanel head = new JPanel(new BorderLayout(10, 0));
			head.setOpaque(false);
			head.add(NodeLabUi.stack(1, NodeLabUi.heading("Node " + node),
					NodeLabUi.caption(profile == null ? "" : profile.getRoleLabel() + " · " + profile.getMaxConcurrentJobs()
							+ " lane" + (profile.getMaxConcurrentJobs() == 1 ? "" : "s"))), BorderLayout.CENTER);
			JPanel chipHolder = new JPanel(new BorderLayout());
			chipHolder.setOpaque(false);
			chipHolder.add(state, BorderLayout.NORTH);
			head.add(chipHolder, BorderLayout.EAST);
			capacity = new NodeLabUi.Slider("Capacity", 1.0, NodeLabUi.linear(0.25, 2.0), 0.05,
					new NodeLabUi.Formatter()
			{
				public String format(double value)
				{
					return String.format(Locale.US, "%.0f%%", value * 100.0);
				}
			}).color(NodeLabUi.nodeColor(node));
			capacity.addChangeListener(new ChangeListener()
			{
				public void stateChanged(ChangeEvent event)
				{
					if (loading || capacity.isAdjusting())
					{
						return;
					}
					final double value = capacity.getValue();
					act(String.format(Locale.US, "Node %d now runs at %.0f%% of its profile speed.", node,
							value * 100.0), new Action()
					{
						public void run(SchedulingRunContext context)
						{
							context.setCapacityScale(node, value);
						}
					});
				}
			});
			pause.addActionListener(new ActionListener()
			{
				public void actionPerformed(ActionEvent event)
				{
					final boolean resume = "Resume".equals(pause.getText());
					act(resume ? "Node " + node + " resumed." : "Node " + node + " pauses at its next safe boundary.",
							new Action()
					{
						public void run(SchedulingRunContext context)
						{
							context.setPaused(node, !resume);
						}
					});
				}
			});
			takeOut.addActionListener(new ActionListener()
			{
				public void actionPerformed(ActionEvent event)
				{
					final boolean back = "Bring back".equals(takeOut.getText());
					act(back ? "Node " + node + " takes work again." : "Node " + node
							+ " takes no new work; its queued units move to the other nodes.", new Action()
					{
						public void run(SchedulingRunContext context)
						{
							context.setUnavailable(node, !back);
						}
					});
				}
			});
			drain.addActionListener(new ActionListener()
			{
				public void actionPerformed(ActionEvent event)
				{
					act(null, new Action()
					{
						public void run(SchedulingRunContext context)
						{
							int revoked = context.drain(node, 0);
							say("Node " + node + " drained: " + revoked + " running unit(s) restart elsewhere.");
						}
					});
				}
			});
			crash.addActionListener(new ActionListener()
			{
				public void actionPerformed(ActionEvent event)
				{
					final boolean repair = "Repair".equals(crash.getText());
					act(repair ? "Node " + node + " repaired; it rejoins with its data."
							: "Node " + node + " crashed; the coordinator notices after the detection timeout.",
							new Action()
					{
						public void run(SchedulingRunContext context)
						{
							if (repair)
							{
								context.recoverNode(node);
							}
							else
							{
								context.crashNode(node, Double.NaN);
							}
						}
					});
				}
			});
			JPanel buttons = NodeLabUi.grid(96, 4, 6);
			buttons.add(pause);
			buttons.add(takeOut);
			buttons.add(drain);
			buttons.add(crash);
			content(NodeLabUi.stack(10, head, detail, load, counts, buttons, capacity));
		}

		void update(SchedulingRunContext context, boolean running)
		{
			String text = stateText(context, node);
			NodeLabUi.Tone tone = "down".equals(text) ? NodeLabUi.Tone.DANGER : "active".equals(text)
					? NodeLabUi.Tone.SUCCESS : NodeLabUi.Tone.WARNING;
			state.set(text, tone);
			double speed = context.speedOf(node);
			detail.setText(String.format(Locale.US, "speed %.2f× · capacity %.0f%% · %d ms link%s", speed,
					context.capacityScale(node) * 100.0, context.delayOf(node), context.crashCount(node) > 0
							? " · crashed " + context.crashCount(node) + "×" : ""));
			WorkDispatcher dispatcher = currentDispatcher(context);
			WorkDispatcher.NodeStats stat = null;
			if (dispatcher != null)
			{
				for (WorkDispatcher.NodeStats candidate : dispatcher.nodeStats())
				{
					if (candidate.getNode() == node)
					{
						stat = candidate;
					}
				}
			}
			int lanes = context.lanesOf(node);
			if (stat == null)
			{
				load.set(0, lanes, 0);
				counts.setText("No unit phase yet");
			}
			else
			{
				load.set(stat.getRunning(), lanes, stat.getReserved());
				counts.setText(String.format(Locale.US, "%d running · %d queued · %d planned · %d done%s",
						stat.getRunning(), stat.getReserved(), stat.getPlanned(), stat.getCompleted(),
						stat.getStolen() > 0 ? " · " + stat.getStolen() + " stolen" : ""));
			}
			boolean down = context.isDown(node);
			WorkDispatcher.NodeState operator = context.operatorState(node);
			pause.setText(operator == WorkDispatcher.NodeState.PAUSED ? "Resume" : "Pause");
			pause.setIcon(NodeLabUi.icon(operator == WorkDispatcher.NodeState.PAUSED ? NodeLabUi.Glyph.PLAY
					: NodeLabUi.Glyph.PAUSE, 13, null));
			takeOut.setText(operator == WorkDispatcher.NodeState.UNAVAILABLE ? "Bring back" : "Take out");
			crash.setText(down ? "Repair" : "Crash");
			crash.setIcon(NodeLabUi.icon(down ? NodeLabUi.Glyph.REPAIR : NodeLabUi.Glyph.CRASH, 13, null));
			boolean many = context.getNodeCount() > 1;
			pause.setEnabled(running && !down);
			takeOut.setEnabled(running && !down && (many || operator == WorkDispatcher.NodeState.UNAVAILABLE));
			drain.setEnabled(running && !down && many && operator != WorkDispatcher.NodeState.UNAVAILABLE);
			crash.setEnabled(running && context.isEmulating());
			crash.setToolTipText(context.isEmulating() ? null : "Crashes need an emulated mode.");
			capacity.setEnabled(running && context.isEmulating());
			if (!capacity.isAdjusting())
			{
				loading = true;
				capacity.setValueQuietly(context.capacityScale(node));
				loading = false;
			}
		}
	}

	// ------------------------------------------------------------ small painters

	/** Lanes in use as filled pills plus queued units as small dots. */
	private static final class LoadBar extends JComponent
	{
		private static final long serialVersionUID = 1L;
		private int running;
		private int lanes = 1;
		private int queued;

		void set(int busy, int laneCount, int waiting)
		{
			if (busy != running || laneCount != lanes || waiting != queued)
			{
				running = busy;
				lanes = Math.max(1, laneCount);
				queued = waiting;
				repaint();
			}
		}

		public Dimension getPreferredSize()
		{
			return new Dimension(200, 18);
		}

		public Dimension getMaximumSize()
		{
			return new Dimension(Integer.MAX_VALUE, 18);
		}

		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D)graphics.create();
			NodeLabUi.prepare(g);
			int shown = Math.min(lanes, 16);
			float width = Math.min(34f, (getWidth() - 80f) / shown - 4f);
			float x = 0f;
			for (int lane = 0; lane < shown; lane++)
			{
				g.setColor(lane < running ? NebulaDesign.SUCCESS : NebulaDesign.INSET);
				g.fill(new RoundRectangle2D.Float(x, 3, width, 12, 12, 12));
				x += width + 4f;
			}
			x += 8f;
			g.setColor(NebulaDesign.TEXT_TERTIARY);
			for (int unit = 0; unit < Math.min(queued, 12); unit++)
			{
				g.fill(new Ellipse2D.Float(x, 6, 6, 6));
				x += 9f;
			}
			if (queued > 12)
			{
				g.setFont(NebulaDesign.fontMedium(10.5f));
				g.drawString("+" + (queued - 12), x + 2, 13);
			}
			g.dispose();
		}
	}

	/** A progress bar with an optional lower-bound pace marker. */
	private static final class ProgressBar extends JComponent
	{
		private static final long serialVersionUID = 1L;
		private double fraction;
		private double pace = Double.NaN;
		private boolean active;

		void set(double value, double paceValue, boolean running)
		{
			fraction = Math.max(0.0, Math.min(1.0, value));
			pace = paceValue;
			active = running;
			repaint();
		}

		public Dimension getPreferredSize()
		{
			return new Dimension(300, 26);
		}

		public Dimension getMaximumSize()
		{
			return new Dimension(Integer.MAX_VALUE, 26);
		}

		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D)graphics.create();
			NodeLabUi.prepare(g);
			int w = getWidth();
			g.setColor(NebulaDesign.INSET);
			g.fill(new RoundRectangle2D.Float(0, 4, w, 12, 12, 12));
			g.setPaint(new java.awt.GradientPaint(0, 0, NebulaDesign.ACCENT, w, 0,
					active ? NebulaDesign.SUCCESS : NebulaDesign.PURPLE));
			g.fill(new RoundRectangle2D.Float(0, 4, (float)(w * fraction), 12, 12, 12));
			g.setFont(NebulaDesign.fontMedium(10.5f));
			g.setColor(NebulaDesign.TEXT_TERTIARY);
			g.drawString(String.format(Locale.US, "%.0f%% of units committed", fraction * 100.0), 0, 26);
			g.dispose();
		}
	}

	/** The run's pipeline stages as a track with the current stage highlighted. */
	private static final class MilestoneTrack extends JComponent
	{
		private static final long serialVersionUID = 1L;
		private static final String[] STAGES = {"Start", "Input", "Model", "Merge", "Delivery"};
		private static final String[][] SPANS = {
			{null, "run-start"},
			{"run-start", "text-processing-complete"},
			{"model-start", "all-reported"},
			{"all-reported", "merge-complete"},
			{"merge-complete", "run-end"}
		};
		private final Double[] starts = new Double[STAGES.length];
		private final Double[] ends = new Double[STAGES.length];
		private double now;
		private boolean running;

		void update(SchedulingRunContext context)
		{
			Map<String,Double> milestones = context.recorder().milestones();
			for (int index = 0; index < STAGES.length; index++)
			{
				starts[index] = SPANS[index][0] == null ? Double.valueOf(0.0) : milestones.get(SPANS[index][0]);
				ends[index] = milestones.get(SPANS[index][1]);
			}
			now = context.now();
			running = !context.isFinished() && !context.isClosed();
			repaint();
		}

		public Dimension getPreferredSize()
		{
			return new Dimension(600, 74);
		}

		public Dimension getMaximumSize()
		{
			return new Dimension(Integer.MAX_VALUE, 74);
		}

		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D)graphics.create();
			NodeLabUi.prepare(g);
			int w = getWidth();
			int count = STAGES.length;
			float margin = 40f;
			float step = (w - 2 * margin) / (count - 1);
			float y = 22f;
			int current = -1;
			for (int index = 0; index < count; index++)
			{
				if (ends[index] == null)
				{
					current = index;
					break;
				}
			}
			g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			for (int index = 0; index < count - 1; index++)
			{
				boolean done = ends[index + 1] != null || (current > index + 1);
				boolean partial = current == index + 1;
				g.setColor(done ? NebulaDesign.ACCENT : NebulaDesign.INSET);
				g.draw(new Line2D.Float(margin + index * step, y, margin + (index + 1) * step, y));
				if (partial && running)
				{
					g.setColor(NodeLabUi.alpha(NebulaDesign.ACCENT, 150));
					float pulse = (float)(0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 220.0));
					g.draw(new Line2D.Float(margin + index * step, y, margin + index * step + step * (0.35f + 0.3f * pulse),
							y));
				}
			}
			for (int index = 0; index < count; index++)
			{
				float x = margin + index * step;
				boolean done = ends[index] != null;
				boolean active = index == current && running;
				if (active)
				{
					g.setColor(NodeLabUi.alpha(NebulaDesign.ACCENT, 50));
					g.fill(new Ellipse2D.Float(x - 13, y - 13, 26, 26));
				}
				g.setColor(done ? NebulaDesign.ACCENT : active ? NebulaDesign.SURFACE : NebulaDesign.INSET);
				g.fill(new Ellipse2D.Float(x - 8, y - 8, 16, 16));
				if (active)
				{
					g.setColor(NebulaDesign.ACCENT);
					g.setStroke(new BasicStroke(2.5f));
					g.draw(new Ellipse2D.Float(x - 7, y - 7, 14, 14));
				}
				if (done)
				{
					g.setColor(Color.WHITE);
					g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
					g.draw(new Line2D.Float(x - 3.5f, y, x - 1f, y + 3f));
					g.draw(new Line2D.Float(x - 1f, y + 3f, x + 4f, y - 3f));
				}
				g.setFont(done || active ? NebulaDesign.fontSemiBold(12f) : NebulaDesign.fontMedium(12f));
				FontMetrics metrics = g.getFontMetrics();
				g.setColor(done || active ? NebulaDesign.TEXT_PRIMARY : NebulaDesign.TEXT_TERTIARY);
				g.drawString(STAGES[index], x - metrics.stringWidth(STAGES[index]) / 2f, y + 30);
				String time = "";
				if (index > 0 && starts[index] != null)
				{
					double end = ends[index] != null ? ends[index].doubleValue() : now;
					time = NodeLabUi.duration(Math.max(0.0, end - starts[index].doubleValue()));
				}
				else if (index == 0 && ends[0] != null)
				{
					time = "started";
				}
				if (time.length() > 0)
				{
					g.setFont(NebulaDesign.fontBody(11f));
					metrics = g.getFontMetrics();
					g.setColor(NebulaDesign.TEXT_TERTIARY);
					g.drawString(time, x - metrics.stringWidth(time) / 2f, y + 46);
				}
			}
			g.dispose();
		}
	}
}
