package TwitterGatherDataFollowers.userRyersonU;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.util.List;
import java.util.Locale;

import javax.swing.Box;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

/**
 * The Node Lab's front page: what the next run will do and whether it can
 * start (with a one-click start), how the latest run went, and the guided
 * steps of the lab.
 */
final class NodeLabOverviewPage extends NodeLabPanel.Page
{
	private static final long serialVersionUID = 1L;

	private final NodeLabPanel lab;
	private final NodeLabUi.Page page = new NodeLabUi.Page(1360);
	private final NodeLabUi.Hero hero;
	private final JPanel checklist = NodeLabUi.stack(0);
	private final NodeLabUi.Button startButton =
			new NodeLabUi.Button("Start a run", NodeLabUi.ButtonKind.PRIMARY, NodeLabUi.Glyph.PLAY);
	private final NodeLabUi.Button heroStart =
			new NodeLabUi.Button("Start a run", NodeLabUi.ButtonKind.PRIMARY, NodeLabUi.Glyph.PLAY);
	private final NodeLabUi.Prose startHint = NodeLabUi.note(" ");
	private final NodeLabUi.Chip launchChip = new NodeLabUi.Chip("Ready", NodeLabUi.Tone.NEUTRAL, true);
	private final JPanel latest = new JPanel(new BorderLayout());
	private final JPanel journey = NodeLabUi.grid(230, 4, 14);
	private String latestKey = "";

	NodeLabOverviewPage(NodeLabPanel lab)
	{
		this.lab = lab;
		hero = new NodeLabUi.Hero("Node Lab", "Emulate a cluster, schedule it, and prove the answers never change",
				"Design heterogeneous nodes, links and faults; run the real recommender on them under any scheduling"
						+ " policy and scenario; watch and steer it live; and analyse every run against a"
						+ " discrete-event model. Recommendations stay bit-identical to a single node, whatever you do.",
				NebulaDesign.ACCENT);
		NodeLabUi.Button design = new NodeLabUi.Button("Design the cluster", NodeLabUi.ButtonKind.SECONDARY,
				NodeLabUi.Glyph.CLUSTER);
		design.addActionListener(go(NodeLabPanel.CLUSTER));
		ActionListener start = new ActionListener()
		{
			public void actionPerformed(ActionEvent event)
			{
				startRun();
			}
		};
		heroStart.addActionListener(start);
		startButton.addActionListener(start);
		hero.right(NodeLabUi.stack(8, heroStart, design));

		NodeLabUi.Card nextRun = new NodeLabUi.Card("Next run",
				"Everything the next run will use. Start it here; the lab initializes the agents for you.");
		nextRun.action(launchChip);
		JPanel footer = new JPanel(new BorderLayout(14, 0));
		footer.setOpaque(false);
		footer.add(startButton, BorderLayout.WEST);
		footer.add(startHint, BorderLayout.CENTER);
		footer.setBorder(new EmptyBorder(14, 0, 0, 0));
		JPanel nextBody = new JPanel(new BorderLayout());
		nextBody.setOpaque(false);
		nextBody.add(checklist, BorderLayout.CENTER);
		nextBody.add(footer, BorderLayout.SOUTH);
		nextRun.content(nextBody);

		NodeLabUi.Card latestCard = new NodeLabUi.Card("Latest run", "The most recent recorded run.");
		NodeLabUi.Button openResults = new NodeLabUi.Button("All results", NodeLabUi.ButtonKind.GHOST,
				NodeLabUi.Glyph.ARROW_RIGHT, true);
		openResults.addActionListener(go(NodeLabPanel.RESULTS));
		latestCard.action(openResults);
		latest.setOpaque(false);
		latestCard.content(latest);

		JPanel top = NodeLabUi.grid(420, 2, 16);
		top.add(nextRun);
		top.add(latestCard);

		JPanel tools = NodeLabUi.grid(360, 2, 16);
		tools.add(tool(NodeLabUi.Glyph.SPARK, NodeLabUi.Tone.PURPLE, "What-If",
				"Compare every scheduling policy on any cluster in milliseconds with the discrete-event simulator,"
						+ " on synthetic work or on the measured work of a recorded run.",
				NodeLabPanel.WHAT_IF));
		tools.add(tool(NodeLabUi.Glyph.FLASK, NodeLabUi.Tone.SUCCESS, "Studies",
				"Replicated experiments: clusters × policies × scenarios, randomized and blocked, with confidence"
						+ " intervals, paired tests and an exactness check across every run.",
				NodeLabPanel.STUDIES));

		NodeLabUi.Card principles = new NodeLabUi.Card("How the lab keeps its promises", null);
		JPanel columns = NodeLabUi.grid(260, 3, 22);
		columns.add(principle(NodeLabUi.Glyph.CLOCK, NodeLabUi.Tone.PURPLE, "One clock",
				"Every node's work is stretched in wall time to its emulated speed, links add latency and"
						+ " bandwidth delays, and all times you see are model milliseconds on one axis."));
		columns.add(principle(NodeLabUi.Glyph.CHECK, NodeLabUi.Tone.SUCCESS, "Exact for any split",
				"Placement, policy, live controls, faults and crashes change only when work runs, never what"
						+ " it computes. Each run's results hash shows it."));
		columns.add(principle(NodeLabUi.Glyph.LAYERS, NodeLabUi.Tone.INFO, "Everything recorded",
				"Each run writes units, phases, stages, collectives and events to Results/Runs, replays its"
						+ " phases in the simulator and reports the model's error."));
		principles.content(columns);

		page.section(hero).section(top).section(sectionTitle("The lab, step by step"))
				.section(journey).section(tools).section(principles);
		add(NodeLabUi.scroll(page), BorderLayout.CENTER);
		refresh();
	}

	int refreshIntervalMs()
	{
		return 1000;
	}

	private ActionListener go(final String key)
	{
		return new ActionListener()
		{
			public void actionPerformed(ActionEvent event)
			{
				lab.show(key);
			}
		};
	}

	private void startRun()
	{
		lab.launcher().start(new Runnable()
		{
			public void run()
			{
				refresh();
				if (lab.launcher().getState() == NodeLabRunLauncher.State.RUNNING)
				{
					lab.show(NodeLabPanel.LIVE);
				}
			}
		});
		refresh();
	}

	void refresh()
	{
		refreshChecklist();
		refreshLatest();
		refreshJourney();
		hero.clearChips();
		hero.chip(new NodeLabUi.Chip(NodeLabPanel.clusterSummary(), NodeLabUi.Tone.ACCENT, true));
		RecommendationSchedulerPlan plan = RecommendationSchedulerRegistry.snapshot();
		hero.chip(new NodeLabUi.Chip(plan.getMode().getLabel(), NodeLabUi.Tone.PURPLE, false));
		hero.chip(new NodeLabUi.Chip(NodeLabPanel.policyLabel(plan), NodeLabUi.Tone.INFO, false));
		if (!plan.getScenario().isEmpty())
		{
			hero.chip(new NodeLabUi.Chip("scenario: " + plan.getScenario().getName(), NodeLabUi.Tone.WARNING, false));
		}
		page.revalidate();
		page.repaint();
	}

	// ------------------------------------------------------------ next run

	private void refreshChecklist()
	{
		checklist.removeAll();
		ControllerAgentGui gui = lab.getOwner();
		String dataset = gui == null ? "" : gui.loadedDatasetName();
		int users = gui == null ? 0 : gui.loadedUserCount();
		checklist.add(item(NodeLabUi.Glyph.FOLDER, "Dataset",
				users > 0 ? (dataset.length() > 0 ? dataset + " · " : "") + users + " users loaded"
						: "No users loaded yet: load a dataset and get its users in the Simulator tab",
				users > 0, "Simulator", new Runnable()
				{
					public void run()
					{
						if (lab.getOwner() != null)
						{
							lab.getOwner().showSimulator();
						}
					}
				}));
		String algorithm = gui == null ? "" : gui.selectedAlgorithmLabel();
		int targets = gui == null ? 0 : gui.selectedTargetCount();
		checklist.add(item(NodeLabUi.Glyph.TARGET, "Algorithm and targets",
				(algorithm.length() == 0 ? "—" : algorithm) + " · " + targets + " target user" + (targets == 1 ? "" : "s"),
				algorithm.length() > 0, "Simulator", new Runnable()
				{
					public void run()
					{
						if (lab.getOwner() != null)
						{
							lab.getOwner().showSimulator();
						}
					}
				}));
		checklist.add(item(NodeLabUi.Glyph.CLUSTER, "Cluster", NodeLabPanel.clusterSummary(), true, "Edit",
				show(NodeLabPanel.CLUSTER)));
		RecommendationSchedulerPlan plan = RecommendationSchedulerRegistry.snapshot();
		checklist.add(item(NodeLabUi.Glyph.SLIDERS, "Plan", plan.getMode().getLabel() + " · "
				+ NodeLabPanel.policyLabel(plan) + (plan.getScenario().isEmpty() ? " · no scenario"
						: " · scenario “" + plan.getScenario().getName() + "”"), true, "Edit",
				show(NodeLabPanel.PLAN)));
		checklist.revalidate();

		NodeLabRunLauncher launcher = lab.launcher();
		String blocker = launcher.blocker();
		boolean canStart = blocker == null;
		startButton.setEnabled(canStart);
		heroStart.setEnabled(canStart);
		NodeLabRunLauncher.State state = launcher.getState();
		if (launcher.isBusy() || NodeLabPanel.isRunning())
		{
			launchChip.set(NodeLabPanel.isRunning() ? "Running" : state.getLabel(), NodeLabUi.Tone.SUCCESS);
			startHint.setText(launcher.isBusy() ? launcher.getMessage() : "A run is in progress. Watch it on the Live page.");
		}
		else if (state == NodeLabRunLauncher.State.FAILED)
		{
			launchChip.set("Last start failed", NodeLabUi.Tone.DANGER);
			startHint.setText(launcher.getMessage());
		}
		else if (canStart)
		{
			launchChip.set("Ready to start", NodeLabUi.Tone.SUCCESS);
			startHint.setText(gui != null && gui.isSimulatorInitialized()
					? "Agents are initialized; starting re-initializes them with this cluster and plan."
					: "Starting initializes the agents with this cluster and plan, then runs.");
		}
		else
		{
			launchChip.set("Not ready", NodeLabUi.Tone.WARNING);
			startHint.setText(blocker);
		}
	}

	private Runnable show(final String key)
	{
		return new Runnable()
		{
			public void run()
			{
				lab.show(key);
			}
		};
	}

	private JComponent item(NodeLabUi.Glyph glyph, String title, String detail, boolean ok, String actionText,
			final Runnable action)
	{
		JPanel row = new JPanel(new BorderLayout(12, 0));
		row.setOpaque(false);
		row.setBorder(new EmptyBorder(9, 0, 9, 0));
		row.add(badge(glyph, ok ? NodeLabUi.Tone.SUCCESS : NodeLabUi.Tone.WARNING, 30), BorderLayout.WEST);
		row.add(NodeLabUi.stack(1, NodeLabUi.strong(title), NodeLabUi.note(detail)), BorderLayout.CENTER);
		NodeLabUi.Button button = new NodeLabUi.Button(actionText, NodeLabUi.ButtonKind.GHOST, NodeLabUi.Glyph.ARROW_RIGHT,
				true);
		button.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent event)
			{
				action.run();
			}
		});
		row.add(button, BorderLayout.EAST);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		return row;
	}

	static JComponent badge(final NodeLabUi.Glyph glyph, final NodeLabUi.Tone tone, final int size)
	{
		JComponent badge = new JComponent()
		{
			private static final long serialVersionUID = 1L;

			public Dimension getPreferredSize()
			{
				return new Dimension(size, size);
			}

			protected void paintComponent(Graphics graphics)
			{
				Graphics2D g = (Graphics2D)graphics.create();
				NodeLabUi.prepare(g);
				g.setColor(NodeLabUi.softTone(tone));
				g.fill(new RoundRectangle2D.Float(0, 0, size, size, size / 3f, size / 3f));
				g.setColor(NodeLabUi.tone(tone));
				int icon = Math.round(size * 0.5f);
				g.translate((size - icon) / 2, (size - icon) / 2);
				NodeLabUi.paintGlyph(g, glyph, icon);
				g.dispose();
			}
		};
		JPanel holder = new JPanel(new BorderLayout());
		holder.setOpaque(false);
		holder.add(badge, BorderLayout.NORTH);
		return holder;
	}

	// ------------------------------------------------------------ latest run

	private void refreshLatest()
	{
		SchedulingRunContext context = SchedulingRuntime.latest();
		List<RunRecordReader.Entry> index = lab.runIndex();
		RunRecordReader.Entry entry = index.isEmpty() ? null : index.get(0);
		String key = (context == null ? "" : context.getRunId() + context.isFinished() + (context.getRecordDirectory() != null))
				+ "|" + (entry == null ? "" : entry.getRunId()) + "|" + NodeLabPanel.isRunning();
		if (key.equals(latestKey) && !NodeLabPanel.isRunning())
		{
			return;
		}
		latestKey = key;
		latest.removeAll();
		if (context != null && !context.isFinished() && !context.isClosed())
		{
			NodeLabUi.Button watch = new NodeLabUi.Button("Watch live", NodeLabUi.ButtonKind.PRIMARY,
					NodeLabUi.Glyph.BOLT);
			watch.addActionListener(go(NodeLabPanel.LIVE));
			Double start = context.recorder().milestone("run-start");
			latest.add(NodeLabUi.emptyState(NodeLabUi.Glyph.BOLT, "A run is in progress",
					context.getMode().getLabel() + " · " + context.effectivePolicy().getLabel()
							+ (start == null ? " · initializing" : " · " + NodeLabUi.duration(context.now()
									- start.doubleValue()) + " so far"), watch), BorderLayout.CENTER);
		}
		else if (entry != null)
		{
			latest.add(latestFromIndex(entry, index), BorderLayout.CENTER);
		}
		else
		{
			latest.add(NodeLabUi.emptyState(NodeLabUi.Glyph.CHART, "No recorded runs yet",
					"Start a run: its full record appears here and on the Results page.", null), BorderLayout.CENTER);
		}
		latest.revalidate();
		latest.repaint();
	}

	private JComponent latestFromIndex(final RunRecordReader.Entry entry, List<RunRecordReader.Entry> index)
	{
		String status = entry.get("status");
		boolean completed = status.startsWith("completed");
		JPanel head = new JPanel(new BorderLayout(10, 0));
		head.setOpaque(false);
		head.add(NodeLabUi.stack(2, NodeLabUi.strong(entry.get("algorithm") + " · " + entry.get("nodes") + " node"
				+ ("1".equals(entry.get("nodes")) ? "" : "s") + " · " + entry.get("policy")),
				NodeLabUi.caption(entry.get("mode") + " · " + NodeLabPanel.shortTime(entry.get("created"))
						+ (entry.get("scenario").length() > 0 && !"No scenario".equals(entry.get("scenario"))
								? " · " + entry.get("scenario") : ""))), BorderLayout.CENTER);
		head.add(new NodeLabUi.Chip(completed ? "completed" : status.split(":")[0],
				completed ? NodeLabUi.Tone.SUCCESS : NodeLabUi.Tone.DANGER, true), BorderLayout.EAST);
		JPanel metrics = NodeLabUi.grid(150, 3, 10);
		metrics.add(small("Makespan", NodeLabUi.duration(entry.number("makespan_ms")), "run start → end"));
		double efficiency = entry.number("phase_efficiency");
		metrics.add(small("Efficiency", Double.isNaN(efficiency) ? "—" : NodeLabUi.percent(efficiency),
				"main phase: bound ÷ makespan"));
		double error = entry.number("replay_main_phase_error");
		metrics.add(small("Model error", Double.isNaN(error) ? "—"
				: String.format(Locale.US, "%+.1f%%", error * 100.0), "replay vs live"));
		String hash = entry.get("results_sha256");
		int same = 0;
		int others = 0;
		for (RunRecordReader.Entry other : index)
		{
			if (other != entry && other.get("algorithm").equals(entry.get("algorithm"))
					&& other.get("dataset").equals(entry.get("dataset")) && other.get("results_sha256").length() > 0)
			{
				others++;
				if (other.get("results_sha256").equals(hash))
				{
					same++;
				}
			}
		}
		String exactText = hash.length() == 0 ? "no results hash"
				: others == 0 ? "results " + hash.substring(0, Math.min(10, hash.length())) + "…"
				: String.format(Locale.US, "results hash equals %d of %d other run%s of %s on this dataset", same,
						others, others == 1 ? "" : "s", entry.get("algorithm"));
		NodeLabUi.Chip exact = new NodeLabUi.Chip(exactText,
				hash.length() == 0 ? NodeLabUi.Tone.NEUTRAL : others == 0 || same == others ? NodeLabUi.Tone.SUCCESS
						: NodeLabUi.Tone.WARNING, false);
		NodeLabUi.Button open = new NodeLabUi.Button("Open run", NodeLabUi.ButtonKind.SECONDARY,
				NodeLabUi.Glyph.CHART, true);
		open.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent event)
			{
				lab.openRun(entry.getRunId());
			}
		});
		JPanel bottom = new JPanel(new BorderLayout(10, 0));
		bottom.setOpaque(false);
		JPanel chipHolder = new JPanel(new BorderLayout());
		chipHolder.setOpaque(false);
		chipHolder.add(exact, BorderLayout.WEST);
		bottom.add(chipHolder, BorderLayout.CENTER);
		bottom.add(open, BorderLayout.EAST);
		return NodeLabUi.stack(14, head, metrics, bottom);
	}

	private static JComponent small(String label, String value, String caption)
	{
		JPanel tile = new JPanel(new BorderLayout())
		{
			private static final long serialVersionUID = 1L;

			protected void paintComponent(Graphics graphics)
			{
				Graphics2D g = (Graphics2D)graphics.create();
				NodeLabUi.prepare(g);
				g.setColor(NebulaDesign.INSET);
				g.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 12, 12));
				g.dispose();
			}
		};
		tile.setOpaque(false);
		tile.setBorder(new EmptyBorder(10, 12, 10, 12));
		NodeLabUi.Text title = NodeLabUi.eyebrow(label);
		NodeLabUi.Text number = new NodeLabUi.Text(value, NebulaDesign.fontSemiBold(18f), NebulaDesign.TEXT_PRIMARY);
		tile.add(NodeLabUi.stack(3, title, number, NodeLabUi.caption(caption)), BorderLayout.CENTER);
		return tile;
	}

	// ------------------------------------------------------------ journey

	private void refreshJourney()
	{
		journey.removeAll();
		RecommendationSchedulerPlan plan = RecommendationSchedulerRegistry.snapshot();
		SchedulingRunContext context = SchedulingRuntime.latest();
		boolean running = NodeLabPanel.isRunning();
		journey.add(step(1, NodeLabUi.Glyph.CLUSTER, "Cluster", "Nodes, speeds, lanes, links, faults",
				NodeLabPanel.clusterSummary(), NodeLabUi.Tone.ACCENT, NodeLabPanel.CLUSTER));
		journey.add(step(2, NodeLabUi.Glyph.SLIDERS, "Plan", "Mode, scheduling policy, scenario",
				plan.getMode().getLabel() + " · " + NodeLabPanel.policyLabel(plan), NodeLabUi.Tone.PURPLE,
				NodeLabPanel.PLAN));
		journey.add(step(3, NodeLabUi.Glyph.BOLT, "Live", "Watch and steer the running system",
				running ? "Running now" : context == null ? "No run yet" : "Last run " + context.recorder().getStatus()
						.split(":")[0], running ? NodeLabUi.Tone.SUCCESS : NodeLabUi.Tone.INFO, NodeLabPanel.LIVE));
		int runs = lab.runIndex().size();
		journey.add(step(4, NodeLabUi.Glyph.CHART, "Results", "Timelines, measures, model check, what-ifs",
				runs == 0 ? "No recorded runs" : runs + " recorded run" + (runs == 1 ? "" : "s"), NodeLabUi.Tone.SUCCESS,
				NodeLabPanel.RESULTS));
		journey.revalidate();
	}

	private JComponent step(final int number, final NodeLabUi.Glyph glyph, String title, String what,
			String status, final NodeLabUi.Tone tone, final String key)
	{
		final JPanel tile = new JPanel(new BorderLayout(0, 10))
		{
			private static final long serialVersionUID = 1L;
			boolean hover;

			{
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

					public void mouseClicked(MouseEvent event)
					{
						lab.show(key);
					}
				});
				setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			}

			protected void paintComponent(Graphics graphics)
			{
				Graphics2D g = (Graphics2D)graphics.create();
				NodeLabUi.prepare(g);
				NodeLabUi.paintSurface(g, 0, hover ? 0 : 1, getWidth(), getHeight() - 4, NebulaDesign.RADIUS_CARD,
						NebulaDesign.SURFACE, true);
				if (hover)
				{
					g.setColor(NodeLabUi.alpha(NodeLabUi.tone(tone), 150));
					g.draw(new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1.5f, getHeight() - 5f,
							NebulaDesign.RADIUS_CARD, NebulaDesign.RADIUS_CARD));
				}
				g.setColor(NodeLabUi.tone(tone));
				g.fill(new Ellipse2D.Float(getWidth() - 40, 16, 24, 24));
				g.setColor(Color.WHITE);
				g.setFont(NebulaDesign.fontSemiBold(12f));
				String text = Integer.toString(number);
				g.drawString(text, getWidth() - 28 - g.getFontMetrics().stringWidth(text) / 2f, 32.5f);
				g.dispose();
			}
		};
		tile.setOpaque(false);
		tile.setBorder(new EmptyBorder(16, 18, 18, 18));
		JPanel iconRow = new JPanel(new BorderLayout());
		iconRow.setOpaque(false);
		iconRow.add(badge(glyph, tone, 36), BorderLayout.WEST);
		tile.add(iconRow, BorderLayout.NORTH);
		tile.add(NodeLabUi.stack(3, NodeLabUi.heading(title), NodeLabUi.caption(what), Box.createVerticalStrut(6),
				new NodeLabUi.Chip(status, tone, true)), BorderLayout.CENTER);
		return tile;
	}

	private JComponent tool(NodeLabUi.Glyph glyph, NodeLabUi.Tone tone, String title, String text, String key)
	{
		NodeLabUi.Card card = new NodeLabUi.Card(null, null);
		JPanel row = new JPanel(new BorderLayout(16, 0));
		row.setOpaque(false);
		row.add(badge(glyph, tone, 44), BorderLayout.WEST);
		row.add(NodeLabUi.stack(4, NodeLabUi.heading(title), NodeLabUi.paragraph(text)), BorderLayout.CENTER);
		NodeLabUi.Button open = new NodeLabUi.Button("Open", NodeLabUi.ButtonKind.SECONDARY, NodeLabUi.Glyph.ARROW_RIGHT,
				true);
		open.addActionListener(go(key));
		JPanel side = new JPanel(new BorderLayout());
		side.setOpaque(false);
		side.add(open, BorderLayout.NORTH);
		row.add(side, BorderLayout.EAST);
		card.content(row);
		return card;
	}

	private static JComponent principle(NodeLabUi.Glyph glyph, NodeLabUi.Tone tone, String title, String text)
	{
		JPanel column = new JPanel(new BorderLayout(0, 10));
		column.setOpaque(false);
		JPanel icon = new JPanel(new BorderLayout());
		icon.setOpaque(false);
		icon.add(badge(glyph, tone, 34), BorderLayout.WEST);
		column.add(icon, BorderLayout.NORTH);
		column.add(NodeLabUi.stack(4, NodeLabUi.strong(title), NodeLabUi.paragraph(text)), BorderLayout.CENTER);
		return column;
	}

	static JComponent sectionTitle(String text)
	{
		NodeLabUi.Text title = NodeLabUi.eyebrow(text);
		title.setBorder(new EmptyBorder(6, 4, 0, 0));
		return title;
	}
}
