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
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;

import javax.swing.Box;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * Step 2 of the Node Lab: how the next run executes. The execution mode, the
 * scheduling policy (with what each one does, when it shines, and an instant
 * simulated comparison on the current cluster) and an optional scenario of
 * scripted pauses, drains, crashes and policy switches.
 */
final class NodeLabPlanPage extends NodeLabPanel.Page
{
	private static final long serialVersionUID = 1L;

	/** One line on how each policy works and when to use it. */
	private static final EnumMap<SchedulerLabEngine.SchedulingPolicy,String[]> POLICY_TEXT =
			new EnumMap<SchedulerLabEngine.SchedulingPolicy,String[]>(SchedulerLabEngine.SchedulingPolicy.class);

	static
	{
		text(SchedulerLabEngine.SchedulingPolicy.ORIGINAL_DSMP, "Each unit runs on the node that holds its data, as V3 did.",
				"Reproducing V3; the baseline for every comparison.");
		text(SchedulerLabEngine.SchedulingPolicy.STATIC_EQUAL_SPLIT, "Equal contiguous blocks, fixed before the phase.",
				"Identical nodes and units of equal cost.");
		text(SchedulerLabEngine.SchedulingPolicy.ROUND_ROBIN, "Unit k goes to node k mod N.",
				"Identical nodes, many small similar units.");
		text(SchedulerLabEngine.SchedulingPolicy.WEIGHTED_ROUND_ROBIN,
				"Unit counts proportional to each node's throughput.",
				"Nodes of different speeds, units of similar cost.");
		text(SchedulerLabEngine.SchedulingPolicy.LEAST_LOADED_NODE, "Each unit to the node with the least assigned work.",
				"Identical nodes, units of very different cost.");
		text(SchedulerLabEngine.SchedulingPolicy.FASTEST_NODE_FIRST, "Each unit to the node that frees up first.",
				"Heterogeneous nodes, few similar units.");
		text(SchedulerLabEngine.SchedulingPolicy.CAPACITY_WEIGHTED,
				"Largest units first, each where it finishes earliest (LPT-EFT).",
				"Known costs on heterogeneous nodes: a strong static default.");
		text(SchedulerLabEngine.SchedulingPolicy.QUEUE_LENGTH, "Idle lanes pull the next unit from one shared queue.",
				"Unpredictable costs; fast nodes simply take more.");
		text(SchedulerLabEngine.SchedulingPolicy.WORK_STEALING,
				"Starts from the capacity-weighted plan; idle nodes steal.",
				"Unpredictable costs or slowdowns on a good plan.");
		text(SchedulerLabEngine.SchedulingPolicy.DEADLINE_PRIORITY,
				"Urgent units first, each to the fastest idle node.",
				"Some units matter more or have deadlines.");
		text(SchedulerLabEngine.SchedulingPolicy.MEMORY_AWARE_SAFE_WAVE,
				"Largest memory first; a unit starts only where it fits.",
				"Units whose memory needs approach node budgets.");
		text(SchedulerLabEngine.SchedulingPolicy.FAILURE_AWARE_RESCHEDULING,
				"Retries move away from the node that failed them.",
				"Unreliable nodes and transient faults.");
	}

	private static void text(SchedulerLabEngine.SchedulingPolicy policy, String how, String best)
	{
		POLICY_TEXT.put(policy, new String[] {how, best});
	}

	private final NodeLabPanel lab;
	private final NodeLabUi.Page page = new NodeLabUi.Page(1360);
	private final EnumMap<RecommendationSchedulerPlan.Mode,NodeLabUi.ChoiceTile> modeTiles =
			new EnumMap<RecommendationSchedulerPlan.Mode,NodeLabUi.ChoiceTile>(RecommendationSchedulerPlan.Mode.class);
	private final EnumMap<SchedulerLabEngine.SchedulingPolicy,NodeLabUi.ChoiceTile> policyTiles =
			new EnumMap<SchedulerLabEngine.SchedulingPolicy,NodeLabUi.ChoiceTile>(SchedulerLabEngine.SchedulingPolicy.class);
	private final NodeLabCharts.BarChart preview = new NodeLabCharts.BarChart();
	private final NodeLabUi.Segmented previewShape = new NodeLabUi.Segmented(
			new String[] {"Equal", "Uniform", "Heavy-tailed", "Bimodal"}, null, 2);
	private final NodeLabUi.Prose previewCaption = NodeLabUi.note(" ");
	private final NodeLabUi.Prose policyNote = NodeLabUi.note(" ");
	private final JPanel scenarioList = NodeLabUi.stack(6);
	private final NodeLabUi.CodeArea editor = new NodeLabUi.CodeArea(9);
	private final NodeLabUi.Prose validation = NodeLabUi.note(" ");
	private final JPanel steps = NodeLabPanel.flow(6);
	private final NodeLabUi.Prose summary = NodeLabUi.prose(" ");
	private final NodeLabUi.Prose scenarioNote = NodeLabUi.note(" ");
	private final NodeLabUi.Button applyScenario =
			new NodeLabUi.Button("Use this scenario", NodeLabUi.ButtonKind.PRIMARY, NodeLabUi.Glyph.CHECK, true);
	private final NodeLabUi.Button saveScenario =
			new NodeLabUi.Button("Save to library", NodeLabUi.ButtonKind.SECONDARY, NodeLabUi.Glyph.SAVE, true);
	private final NodeLabUi.Button startButton =
			new NodeLabUi.Button("Start a run", NodeLabUi.ButtonKind.PRIMARY, NodeLabUi.Glyph.PLAY);
	private final NodeLabUi.Prose startHint = NodeLabUi.note(" ");
	private Scenario parsed = Scenario.NONE;
	private String previewKey = "";
	private int previewGeneration;
	private boolean loadingEditor;
	private String editorSource;

	NodeLabPlanPage(NodeLabPanel lab)
	{
		this.lab = lab;
		NodeLabUi.Hero hero = new NodeLabUi.Hero("Step 2 · Plan", "Plan the run",
				"Choose how the run executes, which scheduling policy places and dispatches its work, and, if you"
						+ " like, a scenario of scripted pauses, drains, crashes and policy switches. The plan is"
						+ " frozen when the run starts, so every run is reproducible.",
				NebulaDesign.PURPLE);
		NodeLabUi.Button next = new NodeLabUi.Button("Next: go live", NodeLabUi.ButtonKind.SECONDARY,
				NodeLabUi.Glyph.ARROW_RIGHT);
		next.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent event)
			{
				NodeLabPlanPage.this.lab.show(NodeLabPanel.LIVE);
			}
		});
		hero.right(NodeLabUi.stack(8, next));

		page.section(hero).section(modeCard()).section(policyCard()).section(scenarioCard()).section(footer());
		add(NodeLabUi.scroll(page), BorderLayout.CENTER);
		refresh();
	}

	// ------------------------------------------------------------ mode

	private JComponent modeCard()
	{
		NodeLabUi.Card card = new NodeLabUi.Card("Execution mode",
				"Recommendations are identical in all three modes; what changes is how the work runs.");
		JPanel grid = NodeLabUi.grid(280, 3, 14);
		mode(grid, RecommendationSchedulerPlan.Mode.DSMP_DEFAULT, NodeLabUi.Glyph.CPU, NodeLabUi.Tone.NEUTRAL,
				"Runs on this machine as V3 did: V3 placement, no emulation, node profiles ignored. The exactness"
						+ " reference.");
		mode(grid, RecommendationSchedulerPlan.Mode.SCORING_SAFE, NodeLabUi.Glyph.CLUSTER, NodeLabUi.Tone.ACCENT,
				"Every node's speed, lanes, memory, queue, latency and bandwidth emulated on one time scale; the"
						+ " policy places and dispatches the work.");
		mode(grid, RecommendationSchedulerPlan.Mode.SCHEDULING_EXPERIMENT, NodeLabUi.Glyph.CRASH,
				NodeLabUi.Tone.DANGER,
				"As emulated, plus the nodes' transient faults and crash-and-repair processes, seeded and"
						+ " reproducible, retried with exactly-once commits.");
		card.content(grid);
		return card;
	}

	private void mode(JPanel grid, final RecommendationSchedulerPlan.Mode mode, NodeLabUi.Glyph glyph,
			NodeLabUi.Tone tone, String text)
	{
		NodeLabUi.ChoiceTile tile = new NodeLabUi.ChoiceTile(glyph, tone, mode.getLabel(), text, null, false);
		tile.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent event)
			{
				applyPlan(mode, null, null);
			}
		});
		modeTiles.put(mode, tile);
		grid.add(tile);
	}

	// ------------------------------------------------------------ policy

	private JComponent policyCard()
	{
		NodeLabUi.Card card = new NodeLabUi.Card("Scheduling policy",
				"Static policies fix the placement when a phase opens; dynamic ones decide as the work runs."
						+ " Each has one precise definition shared by the live system and the simulator.");
		card.action(NodeLabUi.help("How to choose",
				"A static policy is only as good as its cost estimates: with accurate estimates on heterogeneous"
						+ " nodes, Capacity-weighted (LPT-EFT) is hard to beat. When costs are unpredictable, or nodes"
						+ " slow down or leave, dynamic policies adapt: Queue-length lets fast nodes take more, Work"
						+ " stealing keeps a good plan and repairs it. Original DSMP keeps data where V3 put it and is"
						+ " the baseline. The preview on the right simulates every policy on your cluster; What-If"
						+ " does the same on your own workloads or on a recorded run."));
		JPanel groups = NodeLabUi.stack(16);
		groups.add(policyGroup("Baseline", new SchedulerLabEngine.SchedulingPolicy[] {
			SchedulerLabEngine.SchedulingPolicy.ORIGINAL_DSMP}, NodeLabUi.Glyph.HOME, NodeLabUi.Tone.NEUTRAL));
		groups.add(policyGroup("Static placement", new SchedulerLabEngine.SchedulingPolicy[] {
			SchedulerLabEngine.SchedulingPolicy.CAPACITY_WEIGHTED,
			SchedulerLabEngine.SchedulingPolicy.WEIGHTED_ROUND_ROBIN,
			SchedulerLabEngine.SchedulingPolicy.LEAST_LOADED_NODE,
			SchedulerLabEngine.SchedulingPolicy.FASTEST_NODE_FIRST,
			SchedulerLabEngine.SchedulingPolicy.ROUND_ROBIN,
			SchedulerLabEngine.SchedulingPolicy.STATIC_EQUAL_SPLIT}, NodeLabUi.Glyph.LAYERS, NodeLabUi.Tone.ACCENT));
		groups.add(policyGroup("Dynamic dispatch", new SchedulerLabEngine.SchedulingPolicy[] {
			SchedulerLabEngine.SchedulingPolicy.WORK_STEALING,
			SchedulerLabEngine.SchedulingPolicy.QUEUE_LENGTH,
			SchedulerLabEngine.SchedulingPolicy.DEADLINE_PRIORITY,
			SchedulerLabEngine.SchedulingPolicy.MEMORY_AWARE_SAFE_WAVE,
			SchedulerLabEngine.SchedulingPolicy.FAILURE_AWARE_RESCHEDULING}, NodeLabUi.Glyph.BOLT,
				NodeLabUi.Tone.SUCCESS));
		groups.add(policyNote);

		NodeLabUi.Card previewCard = new NodeLabUi.Card("Instant preview on this cluster",
				"Simulated makespan of a synthetic candidate phase under every policy.");
		previewCard.fill(NebulaDesign.SURFACE_ALT);
		preview.setEmptyText("Simulating…");
		previewShape.addChangeListener(new ChangeListener()
		{
			public void stateChanged(ChangeEvent event)
			{
				previewKey = "";
				refreshPreview();
			}
		});
		NodeLabUi.Button whatIf = new NodeLabUi.Button("Explore in What-If", NodeLabUi.ButtonKind.GHOST,
				NodeLabUi.Glyph.SPARK, true);
		whatIf.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent event)
			{
				lab.show(NodeLabPanel.WHAT_IF);
			}
		});
		previewCard.content(NodeLabUi.stack(12, NodeLabUi.labeled("Unit costs", previewShape), preview,
				previewCaption, whatIf));

		JPanel body = new JPanel(new BorderLayout(18, 0));
		body.setOpaque(false);
		body.add(groups, BorderLayout.CENTER);
		JPanel side = new JPanel(new BorderLayout());
		side.setOpaque(false);
		side.add(NodeLabUi.fixedWidth(previewCard, 420), BorderLayout.NORTH);
		body.add(side, BorderLayout.EAST);
		card.content(body);
		return card;
	}

	private JComponent policyGroup(String title, SchedulerLabEngine.SchedulingPolicy[] policies,
			NodeLabUi.Glyph glyph, NodeLabUi.Tone tone)
	{
		JPanel grid = NodeLabUi.grid(250, 3, 12);
		for (final SchedulerLabEngine.SchedulingPolicy policy : policies)
		{
			String[] text = POLICY_TEXT.get(policy);
			NodeLabUi.ChoiceTile tile = new NodeLabUi.ChoiceTile(glyph, tone, policy.getLabel(), text[0],
					"Best when: " + text[1], true);
			tile.addActionListener(new ActionListener()
			{
				public void actionPerformed(ActionEvent event)
				{
					RecommendationSchedulerPlan plan = RecommendationSchedulerRegistry.snapshot();
					RecommendationSchedulerPlan.Mode mode = plan.isActive() ? plan.getMode()
							: RecommendationSchedulerPlan.Mode.SCORING_SAFE;
					applyPlan(mode, policy, null);
				}
			});
			policyTiles.put(policy, tile);
			grid.add(tile);
		}
		return NodeLabUi.stack(8, NodeLabOverviewPage.sectionTitle(title), grid);
	}

	// ------------------------------------------------------------ scenario

	private JComponent scenarioCard()
	{
		NodeLabUi.Card card = new NodeLabUi.Card("Scenario",
				"A script of node adjustments and faults, replayed identically in every run of this plan. Pick"
						+ " one from the library or write your own.");
		card.action(NodeLabUi.help("Scenario language",
				"One step per line: a trigger, then an action.\n\n"
						+ "Triggers: at 1.5s (model time since the run started) · at 40% (of the running unit"
						+ " phase) · at 40% of candidates|svm|mlp|doc2vec|tgf · at 50% of input · when svm opens + 200ms\n\n"
						+ "Actions: pause node 2 · resume node 2 · unavailable node 1 · available node 1 · drain node 3"
						+ " [to node 1] · redirect 5 units to node 1 · capacity node 1 = 50% · policy work stealing"
						+ " (or policy plan) · crash node 2 [for 2s] · recover node 2 · reset\n\n"
						+ "Lines starting with # are comments; \"name:\" and \"description:\" lines name the scenario."));
		editor.setPlaceholder("# One step per line: a trigger, then an action. For example:\n"
				+ "at 30% pause node 2\n"
				+ "at 60% resume node 2\n"
				+ "when svm opens + 200ms crash node 3 for 2s\n"
				+ "at 3s policy work stealing\n\n"
				+ "# Or pick a scenario from the library on the left.");
		editor.getDocument().addDocumentListener(new DocumentListener()
		{
			public void insertUpdate(DocumentEvent event)
			{
				validateEditor();
			}

			public void removeUpdate(DocumentEvent event)
			{
				validateEditor();
			}

			public void changedUpdate(DocumentEvent event)
			{
				validateEditor();
			}
		});
		applyScenario.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent event)
			{
				if (parsed != null)
				{
					RecommendationSchedulerPlan plan = RecommendationSchedulerRegistry.snapshot();
					applyPlan(plan.getMode(), plan.getPolicy(), parsed);
				}
			}
		});
		saveScenario.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent event)
			{
				if (parsed == null || parsed.isEmpty())
				{
					return;
				}
				try
				{
					ScenarioLibrary.save(parsed);
					refreshScenarioList();
				}
				catch (java.io.IOException failure)
				{
					NebulaDialogs.showError(NodeLabPlanPage.this, "Could not save the scenario: "
							+ failure.getMessage(), "Save scenario");
				}
			}
		});
		NodeLabUi.Button clear = new NodeLabUi.Button("No scenario", NodeLabUi.ButtonKind.GHOST, NodeLabUi.Glyph.CROSS,
				true);
		clear.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent event)
			{
				RecommendationSchedulerPlan plan = RecommendationSchedulerRegistry.snapshot();
				applyPlan(plan.getMode(), plan.getPolicy(), Scenario.NONE);
			}
		});
		JPanel left = NodeLabUi.stack(8, NodeLabOverviewPage.sectionTitle("Library"), scenarioList);
		JPanel buttons = NodeLabUi.row(8, applyScenario, saveScenario, clear);
		JPanel right = new JPanel(new BorderLayout(0, 10));
		right.setOpaque(false);
		right.add(NodeLabOverviewPage.sectionTitle("Script"), BorderLayout.NORTH);
		right.add(editor.inScrollPane(), BorderLayout.CENTER);
		right.add(NodeLabUi.stack(10, validation, steps, buttons, scenarioNote), BorderLayout.SOUTH);
		JPanel body = new JPanel(new BorderLayout(22, 0));
		body.setOpaque(false);
		body.add(NodeLabUi.fixedWidth(left, 330), BorderLayout.WEST);
		body.add(right, BorderLayout.CENTER);
		card.content(body);
		return card;
	}

	private void refreshScenarioList()
	{
		scenarioList.removeAll();
		RecommendationSchedulerPlan plan = RecommendationSchedulerRegistry.snapshot();
		String active = plan.getScenario().fingerprint();
		for (final Scenario scenario : ScenarioLibrary.all())
		{
			boolean selected = scenario.fingerprint().equals(active);
			scenarioList.add(scenarioRow(scenario, selected));
		}
		scenarioList.revalidate();
		scenarioList.repaint();
	}

	private JComponent scenarioRow(final Scenario scenario, final boolean selected)
	{
		final JPanel row = new JPanel(new BorderLayout(10, 0))
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
						loadEditor(scenario);
						RecommendationSchedulerPlan plan = RecommendationSchedulerRegistry.snapshot();
						applyPlan(plan.getMode(), plan.getPolicy(), scenario);
					}
				});
				setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			}

			protected void paintComponent(Graphics graphics)
			{
				Graphics2D g = (Graphics2D)graphics.create();
				NodeLabUi.prepare(g);
				Color fill = selected ? NebulaDesign.ACCENT_SOFT : hover ? NebulaDesign.SURFACE_ALT : NebulaDesign.INSET;
				g.setColor(fill);
				g.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 12, 12));
				if (selected)
				{
					g.setColor(NebulaDesign.ACCENT);
					g.fill(new RoundRectangle2D.Float(0, 8, 3, getHeight() - 16, 3, 3));
				}
				g.dispose();
			}
		};
		row.setOpaque(false);
		row.setBorder(new EmptyBorder(9, 14, 9, 12));
		String description = scenario.isEmpty() ? "Nothing is scripted; you can still steer the run live."
				: scenario.getDescription().length() > 0 ? scenario.getDescription()
				: scenario.getSteps().size() + " step(s)";
		NodeLabUi.Text title = NodeLabUi.strong(scenario.getName());
		row.add(NodeLabUi.stack(2, title, NodeLabUi.note(description)), BorderLayout.CENTER);
		if (!scenario.isEmpty())
		{
			JPanel badge = new JPanel(new java.awt.GridBagLayout());
			badge.setOpaque(false);
			badge.add(new NodeLabUi.Chip(scenario.getSteps().size() + " step" + (scenario.getSteps().size() == 1 ? ""
					: "s"), selected ? NodeLabUi.Tone.ACCENT : NodeLabUi.Tone.NEUTRAL, false));
			row.add(badge, BorderLayout.EAST);
		}
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		return row;
	}

	private void loadEditor(Scenario scenario)
	{
		loadingEditor = true;
		try
		{
			String text = scenario.isEmpty() ? "" : scenario.toText();
			if (!scenario.isEmpty() && !text.toLowerCase(Locale.US).startsWith("name:"))
			{
				text = "name: " + scenario.getName() + "\n" + text;
			}
			editor.setText(text);
			editor.setCaretPosition(0);
		}
		finally
		{
			loadingEditor = false;
		}
		validateEditor();
	}

	private void validateEditor()
	{
		String text = editor.getText();
		steps.removeAll();
		if (text.trim().length() == 0)
		{
			parsed = Scenario.NONE;
			validation.setText("Empty: the run has no scenario.");
			validation.setColor(NebulaDesign.TEXT_TERTIARY);
		}
		else
		{
			try
			{
				parsed = Scenario.parse(text);
				validation.setText(String.format(Locale.US, "✓ Valid · %d step%s%s", parsed.getSteps().size(),
						parsed.getSteps().size() == 1 ? "" : "s", parsed.highestNode() > NodeCapacityRegistry
								.configuredNodeCount() ? " · names node " + parsed.highestNode()
								+ ", but the cluster has " + NodeCapacityRegistry.configuredNodeCount() : ""));
				validation.setColor(parsed.highestNode() > NodeCapacityRegistry.configuredNodeCount()
						? NebulaDesign.WARNING : NebulaDesign.SUCCESS);
				for (Scenario.Step step : parsed.getSteps())
				{
					steps.add(new NodeLabUi.Chip(step.describe(), toneOf(step.getAction().getKind()), true));
				}
			}
			catch (Scenario.ScenarioException invalid)
			{
				parsed = null;
				validation.setText("✗ " + invalid.getMessage());
				validation.setColor(NebulaDesign.DANGER);
			}
		}
		applyScenario.setEnabled(parsed != null);
		saveScenario.setEnabled(parsed != null && !parsed.isEmpty());
		steps.revalidate();
		steps.repaint();
	}

	static NodeLabUi.Tone toneOf(Scenario.ActionKind kind)
	{
		switch (kind)
		{
			case CRASH:
				return NodeLabUi.Tone.DANGER;
			case RECOVER:
			case RESUME:
			case AVAILABLE:
				return NodeLabUi.Tone.SUCCESS;
			case PAUSE:
			case UNAVAILABLE:
			case DRAIN:
				return NodeLabUi.Tone.WARNING;
			case POLICY:
				return NodeLabUi.Tone.PURPLE;
			case CAPACITY:
			case REDIRECT:
				return NodeLabUi.Tone.INFO;
			default:
				return NodeLabUi.Tone.NEUTRAL;
		}
	}

	// ------------------------------------------------------------ footer

	private JComponent footer()
	{
		NodeLabUi.Card card = new NodeLabUi.Card(null, null);
		card.fill(NebulaDesign.SURFACE_ALT);
		startButton.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent event)
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
		});
		JPanel row = new JPanel(new BorderLayout(18, 0));
		row.setOpaque(false);
		row.add(NodeLabOverviewPage.badge(NodeLabUi.Glyph.TARGET, NodeLabUi.Tone.ACCENT, 40), BorderLayout.WEST);
		row.add(NodeLabUi.stack(3, NodeLabUi.eyebrow("The next run"), summary, startHint), BorderLayout.CENTER);
		JPanel holder = new JPanel(new BorderLayout());
		holder.setOpaque(false);
		holder.add(startButton, BorderLayout.NORTH);
		row.add(holder, BorderLayout.EAST);
		card.content(row);
		return card;
	}

	// ------------------------------------------------------------ plan

	/** Applies a plan change; null policy or scenario keeps the current one. */
	private void applyPlan(RecommendationSchedulerPlan.Mode mode, SchedulerLabEngine.SchedulingPolicy policy,
			Scenario scenario)
	{
		RecommendationSchedulerPlan current = RecommendationSchedulerRegistry.snapshot();
		SchedulerLabEngine.SchedulingPolicy chosen = policy == null ? current.getPolicy() : policy;
		int nodes = Math.max(1, NodeCapacityRegistry.configuredNodeCount());
		int heap = lab.heapMb();
		if (mode == RecommendationSchedulerPlan.Mode.DSMP_DEFAULT)
		{
			RecommendationSchedulerRegistry.restoreDefault(nodes, heap);
			if (scenario != null)
			{
				RecommendationSchedulerRegistry.applyScenario(scenario);
			}
		}
		else
		{
			RecommendationSchedulerRegistry.apply(mode, chosen, nodes, heap, "Node Lab plan", scenario);
		}
		lab.planChanged();
		refresh();
	}

	void refresh()
	{
		RecommendationSchedulerPlan plan = RecommendationSchedulerRegistry.snapshot();
		for (java.util.Map.Entry<RecommendationSchedulerPlan.Mode,NodeLabUi.ChoiceTile> tile : modeTiles.entrySet())
		{
			tile.getValue().setSelected(tile.getKey() == plan.getMode());
		}
		boolean active = plan.isActive();
		for (java.util.Map.Entry<SchedulerLabEngine.SchedulingPolicy,NodeLabUi.ChoiceTile> tile
				: policyTiles.entrySet())
		{
			boolean selected = active ? tile.getKey() == plan.getPolicy()
					: tile.getKey() == SchedulerLabEngine.SchedulingPolicy.ORIGINAL_DSMP;
			tile.getValue().setSelected(selected);
		}
		policyNote.setText(active ? " "
				: "The hardware mode always places work as V3 did. Choosing a policy switches to the emulated cluster.");
		scenarioNote.setText(active ? "The scenario's times are model time since the run started."
				: "Scenarios act on emulated runs only; the hardware mode runs without one.");
		String planPrint = plan.getScenario().fingerprint();
		if (!planPrint.equals(editorSource) && !editor.isFocusOwner())
		{
			editorSource = planPrint;
			loadEditor(plan.getScenario());
		}
		refreshScenarioList();
		summary.setText(NodeLabPanel.clusterSummary() + " · " + NodeLabPanel.planSummary());
		String blocker = lab.launcher().blocker();
		startButton.setEnabled(blocker == null);
		startHint.setText(blocker == null ? "Starting initializes the agents with this plan, then runs." : blocker);
		refreshPreview();
		page.revalidate();
		page.repaint();
	}

	// ------------------------------------------------------------ preview

	private void refreshPreview()
	{
		final RecommendationSchedulerPlan plan = RecommendationSchedulerRegistry.snapshot();
		final List<NodeCapacityProfile> profiles = NodeCapacityRegistry.snapshot();
		final ClusterSettings settings = NodeCapacityRegistry.clusterSettings();
		final NodeLabWorkloads.Shape shape = NodeLabWorkloads.Shape.values()[previewShape.getSelected()];
		StringBuilder key = new StringBuilder();
		for (NodeCapacityProfile profile : profiles)
		{
			key.append(profile.toReportLine()).append('|');
		}
		key.append(plan.getMode()).append('|').append(plan.getPolicy()).append('|')
				.append(plan.getScenario().fingerprint()).append('|').append(shape).append('|')
				.append(settings.describe());
		if (key.toString().equals(previewKey))
		{
			return;
		}
		previewKey = key.toString();
		final int generation = ++previewGeneration;
		final boolean emulate = plan.isActive();
		final boolean faults = plan.injectsFailures();
		new SwingWorker<List<NodeLabCharts.BarValue>,Void>()
		{
			private double lowerBound = Double.NaN;

			protected List<NodeLabCharts.BarValue> doInBackground()
			{
				List<NodeSpec> specs = NodeLabWorkloads.specsOf(profiles, emulate);
				List<WorkUnitSpec> units = NodeLabWorkloads.synthetic(60, shape, 25.0, 32L * 1024L, 2L * 1024L,
						specs.size(), true, 7L);
				lowerBound = PlacementPlanner.lowerBound(units, specs);
				ArrayList<NodeLabCharts.BarValue> values = new ArrayList<NodeLabCharts.BarValue>();
				SchedulerLabEngine.SchedulingPolicy[] order = emulate ? SchedulerLabEngine.SchedulingPolicy.values()
						: new SchedulerLabEngine.SchedulingPolicy[] {SchedulerLabEngine.SchedulingPolicy.ORIGINAL_DSMP};
				for (SchedulerLabEngine.SchedulingPolicy policy : order)
				{
					NodeLabWorkloads.Setup setup = new NodeLabWorkloads.Setup(specs, units);
					setup.emulate = emulate;
					setup.faults = faults;
					setup.crashes = faults;
					setup.settings = settings;
					setup.faultSeed = settings.getSeed();
					setup.scenario = emulate ? plan.getScenario() : Scenario.NONE;
					SchedulingSimulator.Trace trace = NodeLabWorkloads.simulate(policy, setup);
					boolean chosen = emulate ? policy == plan.getPolicy()
							: policy == SchedulerLabEngine.SchedulingPolicy.ORIGINAL_DSMP;
					values.add(new NodeLabCharts.BarValue(policy.getLabel(), trace.isComplete()
							? trace.getMakespan() : Double.NaN, Double.NaN, Double.NaN,
							chosen ? NebulaDesign.ACCENT : policy.isDynamic() ? NebulaDesign.SUCCESS
									: NebulaDesign.INFO, trace.isComplete() ? "" : "did not finish", chosen));
				}
				java.util.Collections.sort(values, new java.util.Comparator<NodeLabCharts.BarValue>()
				{
					public int compare(NodeLabCharts.BarValue first, NodeLabCharts.BarValue second)
					{
						return Double.compare(orInfinity(first.value), orInfinity(second.value));
					}
				});
				return values;
			}

			protected void done()
			{
				if (generation != previewGeneration)
				{
					return;
				}
				try
				{
					List<NodeLabCharts.BarValue> values = get();
					preview.set(values, lowerBound, "lower bound", new NodeLabUi.Formatter()
					{
						public String format(double value)
						{
							return NodeLabUi.duration(value);
						}
					});
					int rank = 1;
					for (NodeLabCharts.BarValue value : values)
					{
						if (value.highlight)
						{
							break;
						}
						rank++;
					}
					previewCaption.setText(emulate
							? String.format(Locale.US, "60 %s units, mean 25 ms of reference work, 32 KB of input each on"
									+ " its home node. Your policy ranks %d of %d here%s.",
									shape.getLabel().toLowerCase(Locale.US), rank, values.size(),
									plan.getScenario().isEmpty() ? "" : ", with the scenario")
							: "The hardware mode runs V3 placement at host speed with free links.");
					preview.revalidate();
					page.revalidate();
				}
				catch (Exception failure)
				{
					previewCaption.setText("The preview failed: " + failure.getMessage());
				}
			}
		}.execute();
	}

	private static double orInfinity(double value)
	{
		return Double.isNaN(value) ? Double.POSITIVE_INFINITY : value;
	}
}
