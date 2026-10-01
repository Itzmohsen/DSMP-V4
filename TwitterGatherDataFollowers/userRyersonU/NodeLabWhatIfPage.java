package TwitterGatherDataFollowers.userRyersonU;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

/**
 * What-If: every scheduling policy on the same work, in the discrete-event
 * simulator that shares the live system's dispatcher. The work is synthetic
 * (a number of units with a cost shape, data sizes and home nodes) or the
 * measured work of a recorded phase; the cluster is the current one, a
 * preset, a saved one or the run's own; faults, estimate noise and a
 * scenario can be added; replications with common random numbers give 95%
 * confidence intervals and paired comparisons against the best policy.
 */
final class NodeLabWhatIfPage extends NodeLabPanel.Page
{
	private static final long serialVersionUID = 1L;

	private final NodeLabPanel lab;
	private final NodeLabUi.Page page = new NodeLabUi.Page(1440);
	// workload
	private final NodeLabUi.Segmented source = new NodeLabUi.Segmented(new String[] {"Synthetic", "Recorded run"},
			new NodeLabUi.Glyph[] {NodeLabUi.Glyph.SPARK, NodeLabUi.Glyph.CHART}, 0);
	private final CardLayout sourceCards = new CardLayout();
	private final JPanel sourcePanel = new JPanel(sourceCards);
	private final NodeLabUi.Slider units = new NodeLabUi.Slider("Units", 60, NodeLabUi.logarithmic(4, 5000), 1.0,
			new NodeLabUi.Formatter()
	{
		public String format(double value)
		{
			return Math.round(value) + " units";
		}
	});
	private final NodeLabUi.Slider meanCost = new NodeLabUi.Slider("Mean unit cost", 25, NodeLabUi.logarithmic(0.5, 5000),
			0.0, new NodeLabUi.Formatter()
	{
		public String format(double value)
		{
			return NodeLabUi.duration(round(value)) + " of reference work";
		}
	});
	private final NodeLabUi.Segmented shape = new NodeLabUi.Segmented(
			new String[] {"Equal", "Uniform", "Heavy-tailed", "Bimodal"}, null, 2);
	private final NodeLabUi.Slider input = new NodeLabUi.Slider("Input per unit", 32 * 1024, BYTES, 0.0,
			new NodeLabUi.Formatter()
	{
		public String format(double value)
		{
			return value <= 0.0 ? "none" : NodeLabUi.bytes(value);
		}
	});
	private final NodeLabUi.Switch homes = new NodeLabUi.Switch(true);
	private final JComboBox<String> runChoice = NodeLabUi.combo(new String[] {"No recorded runs"});
	private final JComboBox<String> phaseChoice = NodeLabUi.combo(new String[] {"—"});
	private final NodeLabUi.Prose recordNote = NodeLabUi.note(" ");
	// cluster
	private final JComboBox<String> clusterChoice = NodeLabUi.combo(new String[] {"Current cluster"});
	private final NodeLabCharts.TopologyView topology = new NodeLabCharts.TopologyView();
	private final NodeLabUi.Prose clusterNote = NodeLabUi.note(" ");
	// conditions
	private final NodeLabUi.Switch faults = new NodeLabUi.Switch(false);
	private final NodeLabUi.Slider noise = new NodeLabUi.Slider("Estimate error", 0.0, NodeLabUi.linear(0.0, 1.0), 0.05,
			new NodeLabUi.Formatter()
	{
		public String format(double value)
		{
			return value <= 0.0 ? "none: costs are known exactly"
					: String.format(Locale.US, "σ = %.2f (typically ±%.0f%%)", value, (Math.exp(value) - 1.0) * 100.0);
		}
	});
	private final JComboBox<String> scenarioChoice = NodeLabUi.combo(new String[] {"No scenario"});
	private final NodeLabUi.Stepper replications = new NodeLabUi.Stepper(10, 1, 200);
	private final NodeLabUi.Field seed = new NodeLabUi.Field("1", 8, "Seed");
	// results
	private final NodeLabCharts.BarChart chart = new NodeLabCharts.BarChart();
	private final NodeLabUi.Table table = new NodeLabUi.Table(NodeLabUi.Table.text("Policy", 1.8f),
			NodeLabUi.Table.number("Mean makespan", 1.1f), NodeLabUi.Table.number("95% CI", 1.5f),
			NodeLabUi.Table.number("vs best", 0.9f), NodeLabUi.Table.text("Paired test", 1.4f),
			NodeLabUi.Table.number("Utilization", 0.9f), NodeLabUi.Table.number("Wasted", 0.8f),
			NodeLabUi.Table.number("Stolen", 0.7f));
	private final NodeLabUi.Chip status = new NodeLabUi.Chip("ready", NodeLabUi.Tone.NEUTRAL, true);
	private final NodeLabUi.Prose verdict = NodeLabUi.prose(" ");
	private final JComboBox<SchedulerLabEngine.SchedulingPolicy> ganttPolicy =
			NodeLabUi.combo(SchedulerLabEngine.SchedulingPolicy.values());
	private final NodeLabCharts.GanttView gantt = new NodeLabCharts.GanttView();
	private final Timer debounce;
	private final ArrayList<RunRecordReader.Entry> runs = new ArrayList<RunRecordReader.Entry>();
	private final ArrayList<ClusterConfig> clusters = new ArrayList<ClusterConfig>();
	private final ArrayList<Scenario> scenarios = new ArrayList<Scenario>();
	private RunRecordReader.Record record;
	private int generation;
	private boolean loading;
	private String runsKey = "";
	private Setup lastSetup;

	/** Input bytes from 0 (none) through 1 KB to 16 MB, logarithmic. */
	private static final NodeLabUi.Scale BYTES = new NodeLabUi.Scale()
	{
		public double value(double fraction)
		{
			if (fraction <= 0.02)
			{
				return 0.0;
			}
			double kb = Math.pow(2.0, (fraction - 0.02) / 0.98 * 14.0);
			return Math.round(kb) * 1024.0;
		}

		public double fraction(double value)
		{
			if (value < 1024.0)
			{
				return 0.0;
			}
			return 0.02 + 0.98 * Math.log(value / 1024.0) / Math.log(2.0) / 14.0;
		}
	};

	NodeLabWhatIfPage(NodeLabPanel lab)
	{
		this.lab = lab;
		debounce = new Timer(260, new ActionListener()
		{
			public void actionPerformed(ActionEvent event)
			{
				compute();
			}
		});
		debounce.setRepeats(false);
		NodeLabUi.Hero hero = new NodeLabUi.Hero("What-If", "Compare scheduling policies before you run",
				"The discrete-event simulator runs the same work under every policy in milliseconds, on any cluster,"
						+ " with faults, estimate errors and scenarios, repeated with common random numbers for"
						+ " confidence intervals. It shares the live system's dispatcher, so its decisions are the"
						+ " live ones; the Results page shows how closely it predicts real runs.",
				NebulaDesign.PURPLE);
		JPanel inputs = NodeLabUi.grid(330, 3, 16);
		inputs.add(workloadCard());
		inputs.add(clusterCard());
		inputs.add(conditionsCard());
		page.section(hero).section(inputs).section(resultsCard()).section(timelineCard());
		add(NodeLabUi.scroll(page), BorderLayout.CENTER);
		ChangeListener changed = new ChangeListener()
		{
			public void stateChanged(ChangeEvent event)
			{
				if (event.getSource() instanceof NodeLabUi.Slider && ((NodeLabUi.Slider)event.getSource()).isAdjusting())
				{
					return;
				}
				schedule();
			}
		};
		units.addChangeListener(changed);
		meanCost.addChangeListener(changed);
		shape.addChangeListener(changed);
		input.addChangeListener(changed);
		homes.addChangeListener(changed);
		faults.addChangeListener(changed);
		noise.addChangeListener(changed);
		replications.addChangeListener(changed);
		source.addChangeListener(new ChangeListener()
		{
			public void stateChanged(ChangeEvent event)
			{
				sourceCards.show(sourcePanel, source.getSelected() == 0 ? "synthetic" : "recorded");
				refreshClusters();
				schedule();
				page.revalidate();
			}
		});
		ActionListener action = new ActionListener()
		{
			public void actionPerformed(ActionEvent event)
			{
				if (!loading)
				{
					schedule();
				}
			}
		};
		clusterChoice.addActionListener(action);
		scenarioChoice.addActionListener(action);
		phaseChoice.addActionListener(action);
		seed.addActionListener(action);
		runChoice.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent event)
			{
				if (!loading)
				{
					loadRun();
				}
			}
		});
		ganttPolicy.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent event)
			{
				if (!loading)
				{
					lastGanttDefault = false;
					refreshGantt();
				}
			}
		});
		refresh();
	}

	private static double round(double value)
	{
		if (value >= 100.0)
		{
			return Math.round(value);
		}
		if (value >= 10.0)
		{
			return Math.round(value * 2.0) / 2.0;
		}
		return Math.round(value * 10.0) / 10.0;
	}

	// ------------------------------------------------------------ cards

	private JComponent workloadCard()
	{
		NodeLabUi.Card card = new NodeLabUi.Card("Work", "What the nodes have to do.");
		JPanel synthetic = NodeLabUi.stack(14, units, meanCost, NodeLabUi.labeled("Unit costs", shape), input,
				NodeLabUi.spread(NodeLabUi.stack(1, NodeLabUi.strong("Data on home nodes"),
						NodeLabUi.caption("Each unit's input lives on one node, as in V3")), homes));
		JPanel recorded = NodeLabUi.stack(14, NodeLabUi.labeled("Run", runChoice), NodeLabUi.labeled("Phase",
				phaseChoice), recordNote);
		sourcePanel.setOpaque(false);
		sourcePanel.add(synthetic, "synthetic");
		sourcePanel.add(recorded, "recorded");
		card.content(NodeLabUi.stack(16, source, sourcePanel));
		return card;
	}

	private JComponent clusterCard()
	{
		NodeLabUi.Card card = new NodeLabUi.Card("Cluster", "Which nodes run it.");
		topology.setPreferredSize(new java.awt.Dimension(320, 210));
		card.content(NodeLabUi.stack(12, clusterChoice, topology, clusterNote));
		return card;
	}

	private JComponent conditionsCard()
	{
		NodeLabUi.Card card = new NodeLabUi.Card("Conditions", "What can go wrong, and how often to repeat.");
		card.action(NodeLabUi.help("Replications and confidence",
				"Each replication draws new random numbers for unit costs (synthetic work), estimate errors and"
						+ " faults; every policy sees the same draws in the same replication (common random"
						+ " numbers), so their differences are compared pairwise. The interval is the 95% Student-t"
						+ " confidence interval of the mean makespan; the paired test is an exact sign-flip"
						+ " permutation test against the best policy. With estimate error, static policies plan with"
						+ " the estimates while the work takes its actual time: that is where dynamic policies earn"
						+ " their keep."));
		card.content(NodeLabUi.stack(14,
				NodeLabUi.spread(NodeLabUi.stack(1, NodeLabUi.strong("Faults and crashes"),
						NodeLabUi.caption("Each node's transient faults and crash process")), faults),
				noise,
				NodeLabUi.labeled("Scenario", scenarioChoice),
				NodeLabUi.spread(NodeLabUi.labeled("Replications", replications), NodeLabUi.labeled("Seed", seed))));
		return card;
	}

	private JComponent resultsCard()
	{
		NodeLabUi.Card card = new NodeLabUi.Card("Makespan by policy",
				"Mean simulated makespan with its 95% confidence interval; the dashed line is the lower bound.");
		card.action(status);
		chart.setEmptyText("Simulating…");
		card.content(NodeLabUi.stack(14, verdict, chart, table));
		return card;
	}

	private JComponent timelineCard()
	{
		NodeLabUi.Card card = new NodeLabUi.Card("Timeline of one policy",
				"The first replication under the chosen policy, one row per lane.");
		card.action(NodeLabUi.fixedWidth(ganttPolicy, 260));
		gantt.setEmptyText("Choose a policy");
		card.content(gantt);
		return card;
	}

	// ------------------------------------------------------------ refresh

	void refresh()
	{
		loading = true;
		try
		{
			refreshRuns();
			refreshClusters();
			refreshScenarios();
		}
		finally
		{
			loading = false;
		}
		String current = currentClusterKey();
		if (lastSetup == null || (clusterChoice.getSelectedIndex() <= 0 && !current.equals(lastClusterKey)))
		{
			schedule();
		}
	}

	private String lastClusterKey = "";

	private static String currentClusterKey()
	{
		return ClusterConfig.current("Current cluster").toText();
	}

	/** Uses a recorded run's work (from the Results page). */
	void useRecord(RunRecordReader.Record value)
	{
		record = value;
		loading = true;
		try
		{
			refreshRuns();
			for (int index = 0; index < runs.size(); index++)
			{
				if (runs.get(index).getRunId().equals(value.run.runId))
				{
					runChoice.setSelectedIndex(index);
				}
			}
			source.setSelectedQuietly(1);
			sourceCards.show(sourcePanel, "recorded");
			refreshPhases();
			refreshClusters();
			clusterChoice.setSelectedIndex(clusterChoice.getItemCount() - 1);
		}
		finally
		{
			loading = false;
		}
		schedule();
	}

	private void refreshRuns()
	{
		List<RunRecordReader.Entry> index = lab.runIndex();
		String key = Integer.toString(index.size());
		if (key.equals(runsKey) && !runs.isEmpty())
		{
			return;
		}
		runsKey = key;
		String previous = runChoice.getSelectedIndex() >= 0 && runChoice.getSelectedIndex() < runs.size()
				? runs.get(runChoice.getSelectedIndex()).getRunId() : record == null ? "" : record.run.runId;
		runs.clear();
		ArrayList<String> names = new ArrayList<String>();
		for (RunRecordReader.Entry entry : index)
		{
			if (!entry.exists() || Double.isNaN(entry.number("phase_makespan_ms")))
			{
				continue;
			}
			runs.add(entry);
			names.add(entry.get("algorithm") + " · " + entry.get("nodes") + "n · " + entry.get("policy") + " · "
					+ NodeLabPanel.shortTime(entry.get("created")));
		}
		if (names.isEmpty())
		{
			names.add("No recorded runs with unit phases");
		}
		runChoice.setModel(new javax.swing.DefaultComboBoxModel<String>(names.toArray(new String[names.size()])));
		for (int index2 = 0; index2 < runs.size(); index2++)
		{
			if (runs.get(index2).getRunId().equals(previous))
			{
				runChoice.setSelectedIndex(index2);
			}
		}
	}

	private void loadRun()
	{
		int index = runChoice.getSelectedIndex();
		if (index < 0 || index >= runs.size())
		{
			return;
		}
		final RunRecordReader.Entry entry = runs.get(index);
		if (record != null && record.run.runId.equals(entry.getRunId()))
		{
			return;
		}
		recordNote.setText("Loading the run…");
		new SwingWorker<RunRecordReader.Record,Void>()
		{
			protected RunRecordReader.Record doInBackground() throws Exception
			{
				return RunRecordReader.load(entry.directory);
			}

			protected void done()
			{
				try
				{
					record = get();
					loading = true;
					try
					{
						refreshPhases();
						refreshClusters();
						clusterChoice.setSelectedIndex(clusterChoice.getItemCount() - 1);
					}
					finally
					{
						loading = false;
					}
					schedule();
				}
				catch (Exception failure)
				{
					recordNote.setText("The run could not be read: " + failure.getMessage());
				}
			}
		}.execute();
	}

	private void refreshPhases()
	{
		ArrayList<String> names = new ArrayList<String>();
		int main = 0;
		int most = -1;
		if (record != null)
		{
			List<RunRecorder.Phase> phases = record.run.completedPhases();
			for (int index = 0; index < phases.size(); index++)
			{
				RunRecorder.Phase phase = phases.get(index);
				names.add(phase.label + " (" + phase.units + " units)");
				if (phase.units > most)
				{
					most = phase.units;
					main = index;
				}
			}
		}
		if (names.isEmpty())
		{
			names.add("—");
		}
		phaseChoice.setModel(new javax.swing.DefaultComboBoxModel<String>(names.toArray(new String[names.size()])));
		phaseChoice.setSelectedIndex(Math.min(main, names.size() - 1));
	}

	private void refreshClusters()
	{
		boolean previousLoading = loading;
		loading = true;
		try
		{
			String previous = (String)clusterChoice.getSelectedItem();
			clusters.clear();
			ArrayList<String> names = new ArrayList<String>();
			clusters.add(ClusterConfig.current("Current cluster"));
			names.add("Current cluster · " + NodeLabPanel.clusterSummary());
			for (ClusterConfig preset : ClusterConfig.presets(lab.heapMb()))
			{
				clusters.add(preset);
				names.add("Preset · " + preset.getName());
			}
			for (ClusterConfig saved : ClusterConfig.saved())
			{
				clusters.add(saved);
				names.add("Saved · " + saved.getName());
			}
			if (source.getSelected() == 1 && record != null)
			{
				List<NodeCapacityProfile> own = record.profiles();
				if (!own.isEmpty())
				{
					clusters.add(new ClusterConfig("The run's own cluster", own, ClusterSettings.defaults()));
					names.add("The run's own cluster · " + own.size() + " node" + (own.size() == 1 ? "" : "s"));
				}
			}
			clusterChoice.setModel(new javax.swing.DefaultComboBoxModel<String>(names.toArray(new String[names.size()])));
			if (previous != null)
			{
				for (int index = 0; index < names.size(); index++)
				{
					if (names.get(index).equals(previous) || (previous.startsWith("Current cluster")
							&& names.get(index).startsWith("Current cluster")))
					{
						clusterChoice.setSelectedIndex(index);
					}
				}
			}
		}
		finally
		{
			loading = previousLoading;
		}
	}

	private void refreshScenarios()
	{
		String previous = (String)scenarioChoice.getSelectedItem();
		scenarios.clear();
		ArrayList<String> names = new ArrayList<String>();
		for (Scenario scenario : ScenarioLibrary.all())
		{
			scenarios.add(scenario);
			names.add(scenario.getName());
		}
		scenarioChoice.setModel(new javax.swing.DefaultComboBoxModel<String>(names.toArray(new String[names.size()])));
		if (previous != null)
		{
			scenarioChoice.setSelectedItem(previous);
		}
	}

	// ------------------------------------------------------------ computing

	/** Everything one comparison needs, captured on the EDT. */
	private static final class Setup
	{
		boolean recorded;
		RunRecordReader.Record record;
		String phaseKey;
		boolean ownCluster;
		ClusterConfig cluster;
		List<NodeSpec> specs;
		int unitCount;
		double mean;
		NodeLabWorkloads.Shape shape;
		long input;
		boolean homes;
		boolean faults;
		double noise;
		Scenario scenario;
		int replications;
		long seed;
	}

	/** One policy's replications. */
	private static final class Outcome
	{
		final SchedulerLabEngine.SchedulingPolicy policy;
		final double[] makespans;
		final double[] utilization;
		final double[] wasted;
		final double[] stolen;
		int incomplete;

		Outcome(SchedulerLabEngine.SchedulingPolicy policy, int replications)
		{
			this.policy = policy;
			makespans = new double[replications];
			utilization = new double[replications];
			wasted = new double[replications];
			stolen = new double[replications];
		}
	}

	private void schedule()
	{
		status.set("updating", NodeLabUi.Tone.INFO);
		debounce.restart();
	}

	private Setup capture()
	{
		Setup setup = new Setup();
		setup.recorded = source.getSelected() == 1 && record != null;
		int clusterIndex = Math.max(0, clusterChoice.getSelectedIndex());
		//Index 0 is the simulator's cluster as it is now (it may have changed since the list was built).
		setup.cluster = clusterIndex == 0 || clusterIndex >= clusters.size()
				? ClusterConfig.current("Current cluster") : clusters.get(clusterIndex);
		if (clusterIndex == 0)
		{
			lastClusterKey = setup.cluster.toText();
		}
		setup.ownCluster = setup.recorded && clusterIndex == clusters.size() - 1
				&& "The run's own cluster".equals(setup.cluster.getName());
		setup.specs = NodeSpec.fromProfiles(setup.cluster.getProfiles(), null, true);
		if (setup.recorded)
		{
			setup.record = record;
			List<RunRecorder.Phase> phases = record.run.completedPhases();
			int phase = Math.max(0, phaseChoice.getSelectedIndex());
			setup.phaseKey = phase < phases.size() ? phases.get(phase).key : null;
		}
		setup.unitCount = (int)Math.round(units.getValue());
		setup.mean = round(meanCost.getValue());
		setup.shape = NodeLabWorkloads.Shape.values()[shape.getSelected()];
		setup.input = Math.round(input.getValue());
		setup.homes = homes.isOn();
		setup.faults = faults.isOn();
		setup.noise = noise.getValue();
		int scenarioIndex = scenarioChoice.getSelectedIndex();
		setup.scenario = scenarioIndex >= 0 && scenarioIndex < scenarios.size() ? scenarios.get(scenarioIndex)
				: Scenario.NONE;
		setup.replications = replications.getValue();
		setup.seed = JsonText.parseLong(seed.getText(), 1L);
		return setup;
	}

	private void compute()
	{
		final Setup setup = capture();
		if (source.getSelected() == 1 && (record == null || setup.phaseKey == null))
		{
			status.set("choose a recorded run", NodeLabUi.Tone.NEUTRAL);
			verdict.setText(runs.isEmpty() ? "No recorded run has unit phases yet. Start a run, or use synthetic work."
					: "Choose a run and one of its phases.");
			if (record == null && !runs.isEmpty())
			{
				loadRun();
			}
			return;
		}
		describeSetup(setup);
		final int current = ++generation;
		final long started = System.nanoTime();
		status.set("simulating", NodeLabUi.Tone.INFO);
		new SwingWorker<List<Outcome>,Void>()
		{
			private double bound = Double.NaN;

			protected List<Outcome> doInBackground()
			{
				double boundSum = 0.0;
				int bounds = 0;
				ArrayList<Outcome> outcomes = new ArrayList<Outcome>();
				for (SchedulerLabEngine.SchedulingPolicy policy : SchedulerLabEngine.SchedulingPolicy.values())
				{
					outcomes.add(new Outcome(policy, setup.replications));
				}
				for (int replication = 0; replication < setup.replications; replication++)
				{
					if (current != generation)
					{
						return null;
					}
					for (Outcome outcome : outcomes)
					{
						SchedulingSimulator.Trace trace = simulate(setup, outcome.policy, replication);
						outcome.makespans[replication] = trace.isComplete() ? trace.getMakespan() : Double.NaN;
						outcome.utilization[replication] = trace.getUtilization();
						double busy = 0.0;
						int stolen = 0;
						for (SchedulingSimulator.Execution execution : trace.getExecutions())
						{
							busy += execution.getEnd() - execution.getStart();
							if (execution.getSource() == WorkDispatcher.Source.STOLEN)
							{
								stolen++;
							}
						}
						outcome.wasted[replication] = busy <= 0.0 ? 0.0 : trace.getWastedTime() / busy;
						outcome.stolen[replication] = stolen;
						if (!trace.isComplete())
						{
							outcome.incomplete++;
						}
						if (outcome == outcomes.get(0) && !Double.isNaN(trace.getLowerBound()))
						{
							//The bound depends on the work and the nodes, not on the policy.
							boundSum += trace.getLowerBound();
							bounds++;
						}
					}
				}
				bound = bounds == 0 ? Double.NaN : boundSum / bounds;
				return outcomes;
			}

			protected void done()
			{
				if (current != generation)
				{
					return;
				}
				try
				{
					List<Outcome> outcomes = get();
					if (outcomes == null)
					{
						return;
					}
					lastSetup = setup;
					show(outcomes, bound, setup, (System.nanoTime() - started) / 1.0e9);
				}
				catch (Exception failure)
				{
					Throwable cause = failure.getCause() == null ? failure : failure.getCause();
					status.set("failed", NodeLabUi.Tone.DANGER);
					verdict.setText("The simulation failed: " + (cause.getMessage() == null ? cause.toString()
							: cause.getMessage()));
				}
			}
		}.execute();
	}

	private void describeSetup(Setup setup)
	{
		List<NodeCapacityProfile> profiles = setup.cluster.getProfiles();
		ArrayList<NodeLabCharts.NodeView> views = new ArrayList<NodeLabCharts.NodeView>();
		for (NodeCapacityProfile profile : profiles)
		{
			views.add(NodeLabCharts.NodeView.of(profile));
		}
		topology.setNodes(views, setup.cluster.getSettings().getCoordinatorSpeed());
		clusterNote.setText(String.format(Locale.US, "%d node%s · throughput %.2f×", profiles.size(),
				profiles.size() == 1 ? "" : "s", setup.cluster.throughput()));
		if (setup.recorded)
		{
			int nodes = setup.record.nodeCount();
			String text = setup.ownCluster
					? "The phase is replayed exactly as it ran: the measured work of every unit, the nodes' join times,"
							+ " every live control and scenario step, and the run's fault seed for the first replication."
					: profiles.size() == nodes
							? "The measured work, joins and controls of the run on another cluster of " + nodes
									+ " nodes."
							: "The measured work of every unit on a cluster of " + profiles.size() + " nodes (the run had "
									+ nodes + "): data homes wrap round-robin; the run's joins and controls do not"
									+ " carry over.";
			recordNote.setText(text);
			noise.setEnabled(false);
		}
		else
		{
			noise.setEnabled(true);
		}
	}

	/** One replication of one policy. */
	private static SchedulingSimulator.Trace simulate(Setup setup, SchedulerLabEngine.SchedulingPolicy policy,
			int replication)
	{
		long mixed = WorkDispatcher.mix(setup.seed * 0x9E3779B97F4A7C15L + replication);
		if (setup.recorded)
		{
			int nodes = setup.record.nodeCount();
			if (setup.ownCluster || setup.specs.size() == nodes)
			{
				PhaseReplay.Run run = setup.record.run;
				if (!setup.faults && run.faultInjection)
				{
					run = new PhaseReplay.Run(run.runId, run.emulating, run.dilation, false, run.faultSeed,
							run.detectionTimeoutMs, run.recorder);
				}
				return PhaseReplay.replay(run, setup.phaseKey, policy, setup.ownCluster ? null : setup.specs,
						replication == 0 ? null : Long.valueOf(mixed)).getTrace();
			}
			List<WorkUnitSpec> measured = NodeLabWorkloads.onNodes(
					PhaseReplay.measuredUnits(setup.record.run, setup.phaseKey), setup.specs.size());
			NodeLabWorkloads.Setup simulation = new NodeLabWorkloads.Setup(setup.specs, measured);
			simulation.faults = setup.faults;
			simulation.crashes = setup.faults;
			simulation.faultSeed = mixed;
			simulation.settings = setup.cluster.getSettings();
			simulation.scenario = setup.scenario;
			simulation.phaseKey = setup.phaseKey;
			return NodeLabWorkloads.simulate(policy, simulation);
		}
		List<WorkUnitSpec> work = NodeLabWorkloads.synthetic(setup.unitCount, setup.shape, setup.mean, setup.input,
				Math.min(setup.input, 4096L), setup.specs.size(), setup.homes, mixed);
		NodeLabWorkloads.Setup simulation = new NodeLabWorkloads.Setup(setup.specs, work);
		simulation.faults = setup.faults;
		simulation.crashes = setup.faults;
		simulation.faultSeed = mixed;
		simulation.settings = setup.cluster.getSettings();
		simulation.scenario = setup.scenario;
		simulation.costNoise = setup.noise;
		simulation.noiseSeed = mixed ^ 0x5DEECE66DL;
		return NodeLabWorkloads.simulate(policy, simulation);
	}

	private void show(List<Outcome> outcomes, double bound, Setup setup, double seconds)
	{
		ArrayList<Object[]> ranked = new ArrayList<Object[]>();
		for (Outcome outcome : outcomes)
		{
			StudyStatistics.Summary summary = StudyStatistics.summarize(outcome.makespans, 0.95);
			ranked.add(new Object[] {outcome, summary});
		}
		Collections.sort(ranked, new java.util.Comparator<Object[]>()
		{
			public int compare(Object[] first, Object[] second)
			{
				return Double.compare(meanOf(first), meanOf(second));
			}
		});
		Outcome best = (Outcome)ranked.get(0)[0];
		StudyStatistics.Summary bestSummary = (StudyStatistics.Summary)ranked.get(0)[1];
		ArrayList<NodeLabCharts.BarValue> bars = new ArrayList<NodeLabCharts.BarValue>();
		ArrayList<String[]> rows = new ArrayList<String[]>();
		ArrayList<Color[]> colors = new ArrayList<Color[]>();
		int tiedWithBest = 0;
		for (int index = 0; index < ranked.size(); index++)
		{
			Outcome outcome = (Outcome)ranked.get(index)[0];
			StudyStatistics.Summary summary = (StudyStatistics.Summary)ranked.get(index)[1];
			boolean isBest = index == 0;
			String test;
			Color testColor = null;
			if (isBest)
			{
				test = "best";
				testColor = NebulaDesign.SUCCESS;
			}
			else if (setup.replications < 2)
			{
				test = "needs 2+ replications";
			}
			else
			{
				StudyStatistics.Comparison comparison = StudyStatistics.paired(best.makespans, outcome.makespans, 0.95,
						setup.seed);
				boolean significant = comparison.isSignificant(0.05);
				test = significant ? String.format(Locale.US, "slower (p %s)", pText(comparison.pValue))
						: String.format(Locale.US, "≈ best (p %s)", pText(comparison.pValue));
				testColor = significant ? NebulaDesign.TEXT_SECONDARY : NebulaDesign.SUCCESS;
				if (!significant)
				{
					tiedWithBest++;
				}
			}
			double relative = bestSummary.mean > 0.0 ? summary.mean / bestSummary.mean - 1.0 : Double.NaN;
			bars.add(new NodeLabCharts.BarValue(outcome.policy.getLabel(), summary.mean, summary.ciLow, summary.ciHigh,
					isBest ? NebulaDesign.SUCCESS : outcome.policy.isDynamic() ? NebulaDesign.PURPLE : NebulaDesign.INFO,
					outcome.incomplete > 0 ? outcome.incomplete + " run(s) did not finish" : "", isBest));
			rows.add(new String[] {
				outcome.policy.getLabel(),
				NodeLabUi.duration(summary.mean),
				Double.isNaN(summary.ciLow) ? "—" : NodeLabUi.duration(summary.ciLow) + " – "
						+ NodeLabUi.duration(summary.ciHigh),
				isBest ? "—" : String.format(Locale.US, "%+.1f%%", relative * 100.0),
				test,
				NodeLabUi.percent(mean(outcome.utilization)),
				NodeLabUi.percent(mean(outcome.wasted)),
				NodeLabUi.number(mean(outcome.stolen))
			});
			Color[] tones = new Color[8];
			tones[4] = testColor;
			colors.add(tones);
		}
		chart.set(bars, bound, setup.replications > 1 ? "mean lower bound" : "lower bound", new NodeLabUi.Formatter()
		{
			public String format(double value)
			{
				return NodeLabUi.duration(value);
			}
		});
		table.setRows(rows, colors);
		table.setHighlightedRow(0);
		status.set(String.format(Locale.US, "%d policies × %d replication%s · %.2f s", outcomes.size(),
				setup.replications, setup.replications == 1 ? "" : "s", seconds), NodeLabUi.Tone.SUCCESS);
		Outcome original = null;
		for (Outcome outcome : outcomes)
		{
			if (outcome.policy == SchedulerLabEngine.SchedulingPolicy.ORIGINAL_DSMP)
			{
				original = outcome;
			}
		}
		double originalMean = original == null ? Double.NaN : StudyStatistics.summarize(original.makespans, 0.95).mean;
		StringBuilder text = new StringBuilder();
		text.append(String.format(Locale.US, "%s is fastest at %s", best.policy.getLabel(),
				NodeLabUi.duration(bestSummary.mean)));
		if (!Double.isNaN(bound) && bound > 0.0)
		{
			text.append(String.format(Locale.US, ", %.2f× the lower bound of %s%s", bestSummary.mean / bound,
					NodeLabUi.duration(bound), setup.replications > 1 ? " (mean over replications)" : ""));
		}
		text.append('.');
		if (original != null && best != original && originalMean > 0.0)
		{
			text.append(String.format(Locale.US, " It beats the original DSMP placement by %.0f%%.",
					(1.0 - bestSummary.mean / originalMean) * 100.0));
		}
		if (tiedWithBest > 0)
		{
			text.append(String.format(Locale.US, " %d other polic%s cannot be told apart from it at the 5%% level.",
					tiedWithBest, tiedWithBest == 1 ? "y" : "ies"));
		}
		verdict.setText(text.toString());
		loading = true;
		try
		{
			if (ganttPolicy.getSelectedItem() == null || lastGanttDefault)
			{
				ganttPolicy.setSelectedItem(best.policy);
				lastGanttDefault = true;
			}
		}
		finally
		{
			loading = false;
		}
		refreshGantt();
		page.revalidate();
		page.repaint();
	}

	private boolean lastGanttDefault = true;

	private static String pText(double p)
	{
		return Double.isNaN(p) ? "—" : p < 0.001 ? "< 0.001" : String.format(Locale.US, "%.3f", p);
	}

	private static double meanOf(Object[] row)
	{
		double mean = ((StudyStatistics.Summary)row[1]).mean;
		return Double.isNaN(mean) ? Double.POSITIVE_INFINITY : mean;
	}

	private static double mean(double[] values)
	{
		double sum = 0.0;
		int count = 0;
		for (double value : values)
		{
			if (!Double.isNaN(value))
			{
				sum += value;
				count++;
			}
		}
		return count == 0 ? Double.NaN : sum / count;
	}

	private void refreshGantt()
	{
		final Setup setup = lastSetup;
		final SchedulerLabEngine.SchedulingPolicy policy =
				(SchedulerLabEngine.SchedulingPolicy)ganttPolicy.getSelectedItem();
		if (setup == null || policy == null)
		{
			return;
		}
		new SwingWorker<SchedulingSimulator.Trace,Void>()
		{
			protected SchedulingSimulator.Trace doInBackground()
			{
				return simulate(setup, policy, 0);
			}

			protected void done()
			{
				try
				{
					showTrace(get(), setup);
				}
				catch (Exception failure)
				{
					gantt.setEmptyText("The simulation failed.");
				}
			}
		}.execute();
	}

	private void showTrace(SchedulingSimulator.Trace trace, Setup setup)
	{
		ArrayList<String> rows = new ArrayList<String>();
		ArrayList<Color> rowColors = new ArrayList<Color>();
		List<NodeSpec> specs = trace.getDispatcher().nodeSpecs();
		int[] first = new int[specs.size() + 1];
		for (NodeSpec spec : specs)
		{
			first[spec.getIndex()] = rows.size();
			for (int lane = 0; lane < spec.getLanes(); lane++)
			{
				rows.add(spec.getLanes() == 1 ? "Node " + spec.getIndex() : "N" + spec.getIndex() + " · lane " + (lane + 1));
				rowColors.add(NodeLabUi.nodeColor(spec.getIndex()));
			}
		}
		ArrayList<NodeLabCharts.Bar> bars = new ArrayList<NodeLabCharts.Bar>();
		for (SchedulingSimulator.Execution execution : trace.getExecutions())
		{
			if (execution.getNode() < 1 || execution.getNode() >= first.length)
			{
				continue;
			}
			String outcome = execution.isCommitted() ? "committed" : execution.isLostToCrash() ? "revoked"
					: execution.getOutcome() == WorkDispatcher.Outcome.STALE ? "revoked" : "fault";
			bars.add(new NodeLabCharts.Bar(first[execution.getNode()] + Math.max(0, execution.getLane()),
					execution.getStart(), execution.getEnd(), NodeLabUi.nodeColor(execution.getNode()), outcome,
					"unit " + execution.getUnitId() + " · node " + execution.getNode()
							+ (execution.getSource() == WorkDispatcher.Source.STOLEN ? " · stolen" : "")));
		}
		gantt.set(rows, rowColors, bars, Collections.<NodeLabCharts.Marker>emptyList(),
				Collections.<NodeLabCharts.Band>emptyList(), trace.getStartTime(),
				Math.max(trace.getEndTime(), trace.getStartTime() + 1.0));
		page.revalidate();
	}
}
