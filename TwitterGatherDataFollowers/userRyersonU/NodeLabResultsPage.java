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
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * Step 4 of the Node Lab: every recorded run, analysed. A searchable list of
 * runs (Results/Runs) and, for the selected run, its measures, its stages, a
 * timeline of every unit execution per phase, each phase against its lower
 * bound and against the simulator's replay (the model check), what the
 * other policies would have done with exactly the same work, its nodes and
 * its events.
 */
final class NodeLabResultsPage extends NodeLabPanel.Page
{
	private static final long serialVersionUID = 1L;
	private static final int MAX_LISTED = 400;

	private final NodeLabPanel lab;
	private final NodeLabUi.Page page = new NodeLabUi.Page(1480);
	private final NodeLabUi.Field search = new NodeLabUi.Field("", 18, "Search runs");
	private final JPanel list = NodeLabUi.stack(6);
	private final NodeLabUi.Text listCount = NodeLabUi.caption(" ");
	private final JPanel detail = new JPanel(new BorderLayout());
	private String selected = "";
	private String listKey = "";
	private int loadGeneration;
	private RunRecordReader.Record shown;

	NodeLabResultsPage(NodeLabPanel lab)
	{
		this.lab = lab;
		NodeLabUi.Hero hero = new NodeLabUi.Hero("Step 4 · Results", "Every run, measured and checked",
				"Each run is recorded in Results/Runs with its units, phases, stages, collectives and events. Here"
						+ " you see how it went, how close each phase came to its lower bound, how well the simulator"
						+ " predicted it, and what the other policies would have done with exactly the same work.",
				NebulaDesign.SUCCESS);
		NodeLabUi.Button folder = new NodeLabUi.Button("Open Results folder", NodeLabUi.ButtonKind.SECONDARY,
				NodeLabUi.Glyph.FOLDER);
		folder.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent event)
			{
				openFolder(RunRecordWriter.runsDirectory());
			}
		});
		hero.right(NodeLabUi.stack(8, folder));
		search.getDocument().addDocumentListener(new DocumentListener()
		{
			public void insertUpdate(DocumentEvent event)
			{
				listKey = "";
				refreshList();
			}

			public void removeUpdate(DocumentEvent event)
			{
				listKey = "";
				refreshList();
			}

			public void changedUpdate(DocumentEvent event)
			{
				listKey = "";
				refreshList();
			}
		});
		NodeLabUi.Card runs = new NodeLabUi.Card("Runs", null);
		runs.content(NodeLabUi.stack(10, search, listCount, list));
		detail.setOpaque(false);
		JPanel body = new JPanel(new BorderLayout(16, 0));
		body.setOpaque(false);
		JPanel left = new JPanel(new BorderLayout());
		left.setOpaque(false);
		left.add(runs, BorderLayout.NORTH);
		body.add(NodeLabUi.fixedWidth(left, 360), BorderLayout.WEST);
		body.add(detail, BorderLayout.CENTER);
		page.section(hero).section(body);
		add(NodeLabUi.scroll(page), BorderLayout.CENTER);
		refresh();
	}

	int refreshIntervalMs()
	{
		return 2000;
	}

	void refresh()
	{
		refreshList();
		if (shown == null && selected.length() == 0)
		{
			List<RunRecordReader.Entry> index = lab.runIndex();
			if (!index.isEmpty())
			{
				select(index.get(0).getRunId());
			}
			else
			{
				showEmpty();
			}
		}
	}

	/** Shows one run (by run id); it loads in the background. */
	void select(String runId)
	{
		selected = runId == null ? "" : runId;
		listKey = "";
		refreshList();
		RunRecordReader.Entry entry = find(selected);
		if (entry == null || !entry.exists())
		{
			SchedulingRunContext context = SchedulingRuntime.get(selected);
			showMessage(NodeLabUi.Glyph.CLOCK, context != null && !context.isFinished() ? "This run is still going"
					: "Its record is being written", context != null && !context.isFinished()
					? "Watch it on the Live page; its full record appears here when it ends."
					: "This takes a moment after the run ends (the model check replays every phase).",
					context != null && !context.isFinished() ? NodeLabPanel.LIVE : null);
			return;
		}
		load(entry);
	}

	private RunRecordReader.Entry find(String runId)
	{
		for (RunRecordReader.Entry entry : lab.runIndex())
		{
			if (entry.getRunId().equals(runId))
			{
				return entry;
			}
		}
		return null;
	}

	// ------------------------------------------------------------ list

	private void refreshList()
	{
		List<RunRecordReader.Entry> index = lab.runIndex();
		String filter = search.getText().trim().toLowerCase(Locale.US);
		SchedulingRunContext latest = SchedulingRuntime.latest();
		boolean pending = latest != null && find(latest.getRunId()) == null;
		String key = index.size() + "|" + filter + "|" + selected + "|" + (pending ? latest.getRunId()
				+ latest.isFinished() : "");
		if (key.equals(listKey))
		{
			return;
		}
		listKey = key;
		list.removeAll();
		if (pending)
		{
			list.add(pendingRow(latest));
		}
		int listed = 0;
		int matched = 0;
		for (RunRecordReader.Entry entry : index)
		{
			if (filter.length() > 0 && !matches(entry, filter))
			{
				continue;
			}
			matched++;
			if (listed < MAX_LISTED)
			{
				list.add(row(entry));
				listed++;
			}
		}
		listCount.setText(index.isEmpty() ? "No recorded runs yet." : matched + " of " + index.size() + " run"
				+ (index.size() == 1 ? "" : "s") + (matched > listed ? " · showing the newest " + listed : ""));
		list.revalidate();
		list.repaint();
	}

	private static boolean matches(RunRecordReader.Entry entry, String filter)
	{
		for (String column : new String[] {"algorithm", "dataset", "mode", "policy", "scenario", "status", "run_id",
			"created", "study", "cell"})
		{
			if (entry.get(column).toLowerCase(Locale.US).contains(filter))
			{
				return true;
			}
		}
		return (entry.get("nodes") + " nodes").contains(filter);
	}

	private JComponent pendingRow(final SchedulingRunContext context)
	{
		String title = (context.isFinished() ? "Writing record · " : "In progress · ") + context.getNodeCount() + " node"
				+ (context.getNodeCount() == 1 ? "" : "s") + " · " + context.effectivePolicy().getLabel();
		return listRow(title, context.getMode().getLabel(), "", NebulaDesign.SUCCESS, context.getRunId()
				.equals(selected), new Runnable()
				{
					public void run()
					{
						lab.show(NodeLabPanel.LIVE);
					}
				});
	}

	private JComponent row(final RunRecordReader.Entry entry)
	{
		String status = entry.get("status");
		Color dot = status.startsWith("completed") ? NebulaDesign.SUCCESS : status.startsWith("running")
				? NebulaDesign.INFO : NebulaDesign.DANGER;
		String title = entry.get("algorithm") + " · " + entry.get("nodes") + "n · " + entry.get("policy");
		String caption = NodeLabPanel.shortTime(entry.get("created")) + " · " + shortMode(entry.get("mode"))
				+ (entry.get("scenario").length() > 0 && !"No scenario".equals(entry.get("scenario"))
						? " · " + entry.get("scenario") : "")
				+ (entry.get("study").length() > 0 ? " · study" : "");
		return listRow(title, caption, NodeLabUi.duration(entry.number("makespan_ms")), dot,
				entry.getRunId().equals(selected), new Runnable()
				{
					public void run()
					{
						select(entry.getRunId());
					}
				});
	}

	static String shortMode(String mode)
	{
		String lower = mode.toLowerCase(Locale.US);
		return lower.startsWith("hardware") ? "hardware" : lower.contains("fault") ? "emulated + faults"
				: lower.startsWith("emulated") ? "emulated" : mode;
	}

	private JComponent listRow(String title, String caption, String value, final Color dot, final boolean active,
			final Runnable action)
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
						action.run();
					}
				});
				setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			}

			protected void paintComponent(Graphics graphics)
			{
				Graphics2D g = (Graphics2D)graphics.create();
				NodeLabUi.prepare(g);
				g.setColor(active ? NebulaDesign.ACCENT_SOFT : hover ? NebulaDesign.SURFACE_ALT : NebulaDesign.INSET);
				g.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 12, 12));
				if (active)
				{
					g.setColor(NebulaDesign.ACCENT);
					g.fill(new RoundRectangle2D.Float(0, 8, 3, getHeight() - 16, 3, 3));
				}
				g.setColor(dot);
				g.fill(new Ellipse2D.Float(12, getHeight() / 2f - 4, 8, 8));
				g.dispose();
			}
		};
		row.setOpaque(false);
		row.setBorder(new EmptyBorder(9, 28, 9, 12));
		row.add(NodeLabUi.stack(2, NodeLabUi.strong(title), NodeLabUi.caption(caption)), BorderLayout.CENTER);
		if (value.length() > 0)
		{
			NodeLabUi.Text number = NodeLabUi.mono(value);
			JPanel holder = new JPanel(new BorderLayout());
			holder.setOpaque(false);
			holder.add(number, BorderLayout.CENTER);
			row.add(holder, BorderLayout.EAST);
		}
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		return row;
	}

	// ------------------------------------------------------------ loading

	private void load(final RunRecordReader.Entry entry)
	{
		final int generation = ++loadGeneration;
		if (shown != null && shown.run.runId.equals(entry.getRunId()))
		{
			return;
		}
		showMessage(NodeLabUi.Glyph.CLOCK, "Loading the run…", entry.directory.getName(), null);
		new SwingWorker<RunRecordReader.Record,Void>()
		{
			protected RunRecordReader.Record doInBackground() throws Exception
			{
				return RunRecordReader.load(entry.directory);
			}

			protected void done()
			{
				if (generation != loadGeneration)
				{
					return;
				}
				try
				{
					shown = get();
					showRecord(shown, entry);
				}
				catch (Exception failure)
				{
					Throwable cause = failure.getCause() == null ? failure : failure.getCause();
					showMessage(NodeLabUi.Glyph.WARNING, "This run's record could not be read",
							cause.getMessage() == null ? cause.toString() : cause.getMessage(), null);
				}
			}
		}.execute();
	}

	private void showEmpty()
	{
		showMessage(NodeLabUi.Glyph.CHART, "No recorded runs yet",
				"Every run you start is recorded here automatically, with a full analysis.", NodeLabPanel.OVERVIEW);
	}

	private void showMessage(NodeLabUi.Glyph glyph, String title, String text, final String go)
	{
		shown = null;
		detail.removeAll();
		NodeLabUi.Card card = new NodeLabUi.Card(null, null);
		NodeLabUi.Button action = null;
		if (go != null)
		{
			action = new NodeLabUi.Button(NodeLabPanel.LIVE.equals(go) ? "Watch it live" : "Start a run",
					NodeLabUi.ButtonKind.PRIMARY, NodeLabPanel.LIVE.equals(go) ? NodeLabUi.Glyph.BOLT : NodeLabUi.Glyph.PLAY);
			action.addActionListener(new ActionListener()
			{
				public void actionPerformed(ActionEvent event)
				{
					lab.show(go);
				}
			});
		}
		card.content(NodeLabUi.emptyState(glyph, title, text, action));
		detail.add(card, BorderLayout.NORTH);
		detail.revalidate();
		detail.repaint();
		page.revalidate();
	}

	// ------------------------------------------------------------ detail

	private void showRecord(final RunRecordReader.Record record, RunRecordReader.Entry entry)
	{
		detail.removeAll();
		JPanel stack = NodeLabUi.stack(16);
		stack.add(headerCard(record, entry));
		stack.add(metricsGrid(record));
		stack.add(stagesCard(record));
		stack.add(timelineCard(record));
		stack.add(phasesCard(record));
		JComponent counterfactuals = counterfactualCard(record);
		if (counterfactuals != null)
		{
			stack.add(counterfactuals);
		}
		stack.add(nodesCard(record));
		stack.add(eventsCard(record));
		detail.add(stack, BorderLayout.NORTH);
		detail.revalidate();
		detail.repaint();
		page.revalidate();
	}

	private JComponent headerCard(final RunRecordReader.Record record, RunRecordReader.Entry entry)
	{
		NodeLabUi.Card card = new NodeLabUi.Card(null, null);
		String algorithm = record.recorder.properties().containsKey("algorithm")
				? record.recorder.properties().get("algorithm") : entry.get("algorithm");
		String dataset = record.recorder.properties().containsKey("datasetName")
				? record.recorder.properties().get("datasetName") : entry.get("dataset");
		NodeLabUi.Text title = new NodeLabUi.Text(algorithm + (dataset.length() > 0 ? " on " + dataset : ""),
				NebulaDesign.fontSemiBold(20f), NebulaDesign.TEXT_PRIMARY);
		String scenario = record.manifest("scenario.name");
		NodeLabUi.Prose subtitle = NodeLabUi.prose(record.manifest("scheduling.mode") + " · "
				+ record.manifest("scheduling.policy")
				+ (!record.manifest("scheduling.finalPolicy").equals(record.manifest("scheduling.policy"))
						? " → " + record.manifest("scheduling.finalPolicy") : "")
				+ " · " + record.nodeCount() + " node" + (record.nodeCount() == 1 ? "" : "s")
				+ (scenario.length() > 0 && !"No scenario".equals(scenario) ? " · scenario “" + scenario + "”" : "")
				+ " · " + NodeLabPanel.shortTime(record.manifest("created")));
		JPanel chips = NodeLabPanel.flow(6);
		String status = record.recorder.getStatus();
		chips.add(new NodeLabUi.Chip(status.startsWith("completed") ? "completed" : status, status.startsWith("completed")
				? NodeLabUi.Tone.SUCCESS : NodeLabUi.Tone.DANGER, true));
		double tdf = JsonText.number(record.manifest, "scheduling.timeDilation");
		if (!Double.isNaN(tdf) && record.run.emulating)
		{
			chips.add(new NodeLabUi.Chip(String.format(Locale.US, "TDF %.2f", tdf), NodeLabUi.Tone.PURPLE, false));
		}
		String hash = record.manifest("resultsSha256");
		chips.add(exactnessChip(entry, hash));
		String targets = record.recorder.properties().get("targets");
		if (targets != null && targets.length() > 0)
		{
			chips.add(new NodeLabUi.Chip(targets + " target" + ("1".equals(targets) ? "" : "s"), NodeLabUi.Tone.INFO,
					false));
		}
		String study = record.recorder.properties().get("study");
		if (study != null && study.length() > 0)
		{
			chips.add(new NodeLabUi.Chip("study " + study + (record.recorder.properties().containsKey("repetition")
					? " · rep " + record.recorder.properties().get("repetition") : ""), NodeLabUi.Tone.NEUTRAL, false));
		}
		NodeLabUi.Button folder = new NodeLabUi.Button("Open folder", NodeLabUi.ButtonKind.SECONDARY,
				NodeLabUi.Glyph.FOLDER, true);
		folder.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent event)
			{
				openFolder(record.directory);
			}
		});
		NodeLabUi.Button whatIf = new NodeLabUi.Button("Explore in What-If", NodeLabUi.ButtonKind.PRIMARY,
				NodeLabUi.Glyph.SPARK, true);
		whatIf.setEnabled(!record.run.completedPhases().isEmpty());
		whatIf.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent event)
			{
				lab.whatIf(record);
			}
		});
		JPanel actions = new JPanel(new BorderLayout());
		actions.setOpaque(false);
		actions.add(NodeLabUi.row(8, whatIf, folder), BorderLayout.NORTH);
		JPanel head = new JPanel(new BorderLayout(16, 0));
		head.setOpaque(false);
		head.add(NodeLabUi.stack(6, title, subtitle, chips), BorderLayout.CENTER);
		head.add(actions, BorderLayout.EAST);
		List<String> warnings = record.recorder.warnings();
		JPanel body = NodeLabUi.stack(10, head);
		for (String warning : warnings)
		{
			JPanel line = new JPanel(new BorderLayout(8, 0));
			line.setOpaque(false);
			line.add(new NodeLabUi.Chip("warning", NodeLabUi.Tone.WARNING, true), BorderLayout.WEST);
			line.add(NodeLabUi.note(warning), BorderLayout.CENTER);
			body.add(line);
		}
		card.content(body);
		return card;
	}

	/** Whether this run's results hash equals the other runs of the same algorithm on the same dataset. */
	private NodeLabUi.Chip exactnessChip(RunRecordReader.Entry entry, String hash)
	{
		if (hash.length() == 0)
		{
			return new NodeLabUi.Chip("no results hash", NodeLabUi.Tone.NEUTRAL, false);
		}
		int others = 0;
		int same = 0;
		for (RunRecordReader.Entry other : lab.runIndex())
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
		String prefix = "results " + hash.substring(0, Math.min(8, hash.length()));
		if (others == 0)
		{
			return new NodeLabUi.Chip(prefix, NodeLabUi.Tone.NEUTRAL, false);
		}
		return new NodeLabUi.Chip(prefix + (same == others ? " · identical to all " + others + " other run"
				+ (others == 1 ? "" : "s") : " · identical to " + same + " of " + others), same == others
				? NodeLabUi.Tone.SUCCESS : NodeLabUi.Tone.WARNING, true);
	}

	private JComponent metricsGrid(RunRecordReader.Record record)
	{
		JPanel grid = NodeLabUi.grid(200, 4, 12);
		Map<String,String> main = mainPhase(record);
		String mainLabel = main == null ? "Main phase" : "Phase " + Scenario.phaseAlias(main.get("phase"));
		grid.add(tile("Makespan", NodeLabUi.duration(record.summary("makespan_ms")), "Start SIM → last answer",
				NebulaDesign.ACCENT));
		double phase = record.summary("phase_makespan_ms");
		double bound = record.summary("phase_lower_bound_ms");
		grid.add(tile(mainLabel, NodeLabUi.duration(phase), main == null ? "this run had no unit phases"
				: "lower bound " + NodeLabUi.duration(bound), NebulaDesign.PURPLE));
		double efficiency = record.summary("phase_efficiency");
		grid.add(tile("Efficiency", NodeLabUi.percent(efficiency), "lower bound ÷ phase makespan",
				efficiency >= 0.8 ? NebulaDesign.SUCCESS : efficiency >= 0.5 ? NebulaDesign.WARNING : NebulaDesign.DANGER));
		grid.add(tile("Utilization", NodeLabUi.percent(record.summary("phase_utilization")),
				String.format(Locale.US, "imbalance %s (busiest ÷ mean)", NodeLabUi.number(record.summary("phase_imbalance"))),
				NebulaDesign.INFO));
		double wasted = record.summary("wasted_fraction");
		grid.add(tile("Wasted work", NodeLabUi.percent(wasted), String.format(Locale.US, "%s faults · %s crashes · %s revoked",
				NodeLabUi.number(record.summary("faults")), NodeLabUi.number(record.summary("crashes")),
				NodeLabUi.number(record.summary("revocations"))), wasted > 0.05 ? NebulaDesign.WARNING
				: NebulaDesign.SUCCESS));
		grid.add(tile("Data moved", NodeLabUi.bytes(record.summary("bytes_total")),
				"collectives " + NodeLabUi.bytes(record.summary("bytes_collectives")), NebulaDesign.INFO));
		double error = record.summary("replay_main_phase_error");
		grid.add(tile("Model error", Double.isNaN(error) ? "—" : String.format(Locale.US, "%+.1f%%", error * 100.0),
				"replay of the main phase vs live" + (Double.isNaN(record.summary("replay_abs_error")) ? ""
						: String.format(Locale.US, " · mean |error| %.1f%%", record.summary("replay_abs_error") * 100.0)),
				Double.isNaN(error) ? NebulaDesign.TEXT_TERTIARY : Math.abs(error) <= 0.1 ? NebulaDesign.SUCCESS
				: NebulaDesign.WARNING));
		double sleep = record.summary("mean_sleep_error_ms");
		double host = record.summary("host_utilization");
		grid.add(tile("Emulation", Double.isNaN(sleep) ? "—" : String.format(Locale.US, "±%.3f ms", sleep),
				"mean timing error · host CPU " + NodeLabUi.percent(host), Double.isNaN(sleep) ? NebulaDesign.TEXT_TERTIARY
				: sleep < 0.5 ? NebulaDesign.SUCCESS : NebulaDesign.WARNING));
		return grid;
	}

	private static JComponent tile(String label, String value, String caption, Color accent)
	{
		return new NodeLabUi.Metric(label, value, caption, accent);
	}

	private static Map<String,String> mainPhase(RunRecordReader.Record record)
	{
		Map<String,String> main = null;
		for (Map<String,String> phase : record.phases)
		{
			if (main == null || JsonText.parseInt(phase.get("units"), 0) > JsonText.parseInt(main.get("units"), 0))
			{
				main = phase;
			}
		}
		return main;
	}

	private JComponent stagesCard(RunRecordReader.Record record)
	{
		NodeLabUi.Card card = new NodeLabUi.Card("Where the time went",
				"The run's stages between its milestones, in model time.");
		NodeLabCharts.StageBar bar = new NodeLabCharts.StageBar();
		ArrayList<String> names = new ArrayList<String>();
		ArrayList<Double> durations = new ArrayList<Double>();
		String[][] stages = {{"input", "Input delivery"}, {"model", "Model and scoring"}, {"merge", "Merge"},
			{"delivery", "Delivery"}};
		for (String[] stage : stages)
		{
			Double value = record.stages.get(stage[0]);
			names.add(stage[1]);
			durations.add(value == null ? Double.valueOf(Double.NaN) : value);
		}
		bar.set(names, durations);
		card.content(bar);
		return card;
	}

	private JComponent timelineCard(final RunRecordReader.Record record)
	{
		NodeLabUi.Card card = new NodeLabUi.Card("Timeline",
				"Every unit execution, one row per lane. Hatched bars did not commit (faults, revocations, crashes);"
						+ " shaded spans are downtime; markers are controls and phase openings.");
		final NodeLabCharts.GanttView gantt = new NodeLabCharts.GanttView();
		gantt.setEmptyText("This run executed no work units");
		final List<RunRecorder.Phase> phases = record.recorder.phases();
		String[] options = new String[phases.size() + 1];
		options[0] = "All phases";
		for (int index = 0; index < phases.size(); index++)
		{
			RunRecorder.Phase phase = phases.get(index);
			options[index + 1] = phase.label + " (" + phase.units + " units)";
		}
		final JComboBox<String> choice = NodeLabUi.combo(options);
		final List<NodeCapacityProfile> profiles = record.profiles();
		final Runnable update = new Runnable()
		{
			public void run()
			{
				int index = choice.getSelectedIndex();
				String key = index <= 0 ? null : phases.get(index - 1).key;
				NodeLabCharts.GanttData data = NodeLabCharts.GanttData.of(profiles, record.recorder.executions(),
						record.recorder.controls(), phases, 20000, key);
				if (data.bars.isEmpty())
				{
					gantt.set(null, null, null, null, null, 0.0, 1.0);
					return;
				}
				gantt.set(data.rows, data.colors, data.bars, data.markers, data.bands, data.start,
						Math.max(data.end, data.start + 1.0));
			}
		};
		choice.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent event)
			{
				update.run();
			}
		});
		if (phases.size() == 1)
		{
			choice.setSelectedIndex(1);
		}
		update.run();
		card.action(NodeLabUi.fixedWidth(choice, 260));
		card.content(gantt);
		return card;
	}

	private JComponent phasesCard(RunRecordReader.Record record)
	{
		NodeLabUi.Card card = new NodeLabUi.Card("Phases and model check",
				"Each unit phase against its lower bound (no schedule can beat it; W, L, U or P says what sets it)"
						+ " and against the simulator's"
						+ " replay of the same measured work, joins, controls and faults (the model check).");
		card.action(NodeLabUi.help("Reading this table",
				"Makespan: phase opening to its last commit. Lower bound: the largest of W, the measured work spread"
						+ " perfectly over the nodes from the moment each joined the phase; L, the lane time every unit"
						+ " needs; U, the latest earliest finish of a single unit on its best node, its descriptor and"
						+ " (away from home) its input transfer included; P, the work only one node may run. No policy"
						+ " can beat it. Efficiency = lower bound ÷ makespan (1 is a perfect schedule). Utilization: busy lane time over"
						+ " available lane time. Imbalance: the busiest node's busy time over the mean. Off home: units"
						+ " that ran away from the node holding their data. Predicted: the simulator's replay with the"
						+ " phase's own policy; error = (predicted − live) ÷ live."));
		NodeLabUi.Table table = new NodeLabUi.Table(NodeLabUi.Table.text("Phase", 1.5f),
				NodeLabUi.Table.text("Policy", 1.4f), NodeLabUi.Table.number("Units", 0.6f),
				NodeLabUi.Table.number("Makespan", 1.0f), NodeLabUi.Table.number("Bound", 0.9f),
				NodeLabUi.Table.number("Effic.", 0.7f), NodeLabUi.Table.number("Util.", 0.7f),
				NodeLabUi.Table.number("Imbal.", 0.7f), NodeLabUi.Table.number("Stolen", 0.7f),
				NodeLabUi.Table.number("Off home", 0.8f), NodeLabUi.Table.number("Faults", 0.6f),
				NodeLabUi.Table.number("Predicted", 1.0f), NodeLabUi.Table.number("Error", 0.7f));
		ArrayList<String[]> rows = new ArrayList<String[]>();
		ArrayList<Color[]> colors = new ArrayList<Color[]>();
		double errorSum = 0.0;
		double errorMax = 0.0;
		int errors = 0;
		for (Map<String,String> phase : record.phases)
		{
			Map<String,String> replay = null;
			for (Map<String,String> candidate : record.replay)
			{
				if (candidate.get("phase").equals(phase.get("phase")))
				{
					replay = candidate;
				}
			}
			double efficiency = JsonText.parseDouble(phase.get("efficiency"));
			double error = replay == null ? Double.NaN : JsonText.parseDouble(replay.get("relative_error"));
			if (!Double.isNaN(error))
			{
				errorSum += Math.abs(error);
				errorMax = Math.max(errorMax, Math.abs(error));
				errors++;
			}
			rows.add(new String[] {
				phase.get("label") != null && phase.get("label").length() > 0 ? phase.get("label") : phase.get("phase"),
				phase.get("policy"),
				phase.get("units"),
				NodeLabUi.duration(JsonText.parseDouble(phase.get("makespan_ms"))),
				NodeLabUi.duration(JsonText.parseDouble(phase.get("lower_bound_ms")))
						+ boundTag(phase.get("lower_bound_kind")),
				NodeLabUi.percent(efficiency),
				NodeLabUi.percent(JsonText.parseDouble(phase.get("utilization"))),
				NodeLabUi.number(JsonText.parseDouble(phase.get("imbalance"))),
				phase.get("stolen"),
				phase.get("off_home"),
				phase.get("faults"),
				replay == null ? "—" : NodeLabUi.duration(JsonText.parseDouble(replay.get("predicted_makespan_ms"))),
				Double.isNaN(error) ? "—" : String.format(Locale.US, "%+.1f%%", error * 100.0)
			});
			Color[] tones = new Color[13];
			tones[5] = Double.isNaN(efficiency) ? null : efficiency >= 0.8 ? NebulaDesign.SUCCESS
					: efficiency >= 0.5 ? NebulaDesign.WARNING : NebulaDesign.DANGER;
			tones[12] = Double.isNaN(error) ? null : Math.abs(error) <= 0.1 ? NebulaDesign.SUCCESS : NebulaDesign.WARNING;
			colors.add(tones);
		}
		table.setRows(rows, colors);
		table.setEmptyText("This run had no unit phases.");
		NodeLabUi.Prose verdict = NodeLabUi.note(errors == 0 ? "No phase was replayed."
				: String.format(Locale.US, "The simulator predicts this run's %d phase%s within %.1f%% on average"
						+ " (worst %.1f%%). %s", errors, errors == 1 ? "" : "s", errorSum / errors * 100.0,
						errorMax * 100.0, errorMax <= 0.1 ? "The model is faithful for this run."
								: "Treat the counterfactuals below with the corresponding caution."));
		card.content(NodeLabUi.stack(12, table, verdict));
		return card;
	}

	/** A short tag for what sets a phase's lower bound. */
	private static String boundTag(String kind)
	{
		if (kind == null || kind.length() == 0)
		{
			return "";
		}
		return "work".equals(kind) ? " ·W" : "lanes".equals(kind) ? " ·L" : "unit".equals(kind) ? " ·U"
				: "pinned".equals(kind) ? " ·P" : "";
	}

	private JComponent counterfactualCard(RunRecordReader.Record record)
	{
		if (record.counterfactuals.isEmpty())
		{
			return null;
		}
		String phaseKey = record.counterfactuals.get(0).get("phase");
		String livePolicy = "";
		for (Map<String,String> phase : record.phases)
		{
			if (phase.get("phase").equals(phaseKey))
			{
				livePolicy = phase.get("policy");
			}
		}
		NodeLabUi.Card card = new NodeLabUi.Card("What would the other policies have done?",
				"The " + Scenario.phaseAlias(phaseKey) + " phase replayed under every policy with exactly the same"
						+ " measured work, node joins, controls and faults.");
		ArrayList<NodeLabCharts.BarValue> values = new ArrayList<NodeLabCharts.BarValue>();
		double bound = Double.NaN;
		double live = Double.NaN;
		double best = Double.POSITIVE_INFINITY;
		String bestPolicy = "";
		for (Map<String,String> row : record.counterfactuals)
		{
			double value = JsonText.parseDouble(row.get("predicted_makespan_ms"));
			bound = JsonText.parseDouble(row.get("lower_bound_ms"));
			boolean mine = row.get("policy").equals(livePolicy);
			if (mine)
			{
				live = value;
			}
			if (!Double.isNaN(value) && value < best)
			{
				best = value;
				bestPolicy = row.get("policy");
			}
			boolean complete = !"false".equalsIgnoreCase(row.get("complete"));
			SchedulerLabEngine.SchedulingPolicy policy = PhaseReplay.policyNamed(row.get("policy"));
			values.add(new NodeLabCharts.BarValue(row.get("policy"), complete ? value : Double.NaN, Double.NaN,
					Double.NaN, mine ? NebulaDesign.ACCENT : policy.isDynamic() ? NebulaDesign.SUCCESS : NebulaDesign.INFO,
					complete ? (mine ? "the run's policy" : "") : "did not finish", mine));
		}
		Collections.sort(values, new java.util.Comparator<NodeLabCharts.BarValue>()
		{
			public int compare(NodeLabCharts.BarValue first, NodeLabCharts.BarValue second)
			{
				return Double.compare(Double.isNaN(first.value) ? Double.POSITIVE_INFINITY : first.value,
						Double.isNaN(second.value) ? Double.POSITIVE_INFINITY : second.value);
			}
		});
		NodeLabCharts.BarChart chart = new NodeLabCharts.BarChart();
		chart.set(values, bound, "lower bound", new NodeLabUi.Formatter()
		{
			public String format(double value)
			{
				return NodeLabUi.duration(value);
			}
		});
		String summary;
		if (Double.isNaN(live) || bestPolicy.equals(livePolicy))
		{
			summary = livePolicy.length() > 0 && bestPolicy.equals(livePolicy)
					? "The run's own policy, " + livePolicy + ", is the best of all for this phase."
					: "Best: " + bestPolicy + ".";
		}
		else
		{
			summary = String.format(Locale.US, "Best: %s, %.0f%% faster than the run's %s (%s vs %s).", bestPolicy,
					(1.0 - best / live) * 100.0, livePolicy, NodeLabUi.duration(best), NodeLabUi.duration(live));
		}
		card.content(NodeLabUi.stack(10, chart, NodeLabUi.note(summary)));
		return card;
	}

	private JComponent nodesCard(RunRecordReader.Record record)
	{
		NodeLabUi.Card card = new NodeLabUi.Card("Nodes", "What each node did over the whole run.");
		NodeLabUi.Table table = new NodeLabUi.Table(NodeLabUi.Table.text("Node", 1.4f),
				NodeLabUi.Table.number("Speed", 0.7f), NodeLabUi.Table.number("Lanes", 0.6f),
				NodeLabUi.Table.number("Busy", 1.0f), NodeLabUi.Table.number("Wasted", 1.0f),
				NodeLabUi.Table.number("Units", 0.7f), NodeLabUi.Table.number("Crashes", 0.8f),
				NodeLabUi.Table.number("Sent", 0.9f), NodeLabUi.Table.number("Received", 0.9f),
				NodeLabUi.Table.number("Net wait", 0.9f), NodeLabUi.Table.number("Down", 0.9f),
				NodeLabUi.Table.number("Timing ±", 0.9f));
		ArrayList<String[]> rows = new ArrayList<String[]>();
		ArrayList<Color[]> colors = new ArrayList<Color[]>();
		for (Map<String,String> node : record.nodes)
		{
			rows.add(new String[] {
				"N" + node.get("node") + " · " + node.get("role"),
				String.format(Locale.US, "%.2f×", JsonText.parseDouble(node.get("speed"))),
				node.get("lanes"),
				NodeLabUi.duration(JsonText.parseDouble(node.get("busy_ms"))),
				NodeLabUi.duration(JsonText.parseDouble(node.get("wasted_ms"))),
				node.get("committed"),
				node.get("crashes"),
				NodeLabUi.bytes(JsonText.parseDouble(node.get("bytes_up"))),
				NodeLabUi.bytes(JsonText.parseDouble(node.get("bytes_down"))),
				NodeLabUi.duration(JsonText.parseDouble(node.get("network_wait_ms"))),
				NodeLabUi.duration(JsonText.parseDouble(node.get("downtime_ms"))),
				String.format(Locale.US, "±%.3f ms", JsonText.parseDouble(node.get("mean_sleep_error_ms")))
			});
			Color[] tones = new Color[12];
			tones[0] = NodeLabUi.nodeColor(JsonText.parseInt(node.get("node"), 1));
			colors.add(tones);
		}
		table.setRows(rows, colors);
		card.content(table);
		return card;
	}

	private JComponent eventsCard(RunRecordReader.Record record)
	{
		NodeLabUi.Card card = new NodeLabUi.Card("Events", "Controls, scenario steps, faults, crashes and placements.");
		final List<RunRecorder.Event> events = record.recorder.events();
		final JPanel rows = NodeLabUi.stack(0);
		final int[] limit = {Math.min(40, events.size())};
		final NodeLabUi.Button more = new NodeLabUi.Button("Show all " + events.size(), NodeLabUi.ButtonKind.GHOST,
				NodeLabUi.Glyph.LIST, true);
		final Runnable fill = new Runnable()
		{
			public void run()
			{
				rows.removeAll();
				for (int index = 0; index < limit[0]; index++)
				{
					RunRecorder.Event event = events.get(index);
					JPanel row = new JPanel(new BorderLayout(12, 0));
					row.setOpaque(false);
					row.setBorder(new EmptyBorder(5, 0, 5, 0));
					NodeLabUi.Text time = NodeLabUi.mono(NodeLabUi.duration(event.at));
					time.setPreferredSize(new Dimension(86, time.getPreferredSize().height));
					JPanel left = new JPanel(new BorderLayout(8, 0));
					left.setOpaque(false);
					left.add(time, BorderLayout.WEST);
					left.add(new NodeLabUi.Chip(event.kind, NodeLabLivePage.eventTone(event.kind), true),
							BorderLayout.EAST);
					row.add(left, BorderLayout.WEST);
					row.add(NodeLabUi.note(event.detail), BorderLayout.CENTER);
					row.setAlignmentX(Component.LEFT_ALIGNMENT);
					rows.add(row);
				}
				if (events.isEmpty())
				{
					rows.add(NodeLabUi.note("No events were recorded."));
				}
				more.setVisible(limit[0] < events.size());
				rows.revalidate();
				page.revalidate();
			}
		};
		more.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent event)
			{
				limit[0] = events.size();
				fill.run();
			}
		});
		fill.run();
		card.action(more);
		card.content(rows);
		return card;
	}

	static void openFolder(File directory)
	{
		try
		{
			if (!directory.isDirectory())
			{
				directory.mkdirs();
			}
			java.awt.Desktop.getDesktop().open(directory);
		}
		catch (Exception unavailable)
		{
			System.err.println("Cannot open " + directory + ": " + unavailable.getMessage());
		}
	}
}
