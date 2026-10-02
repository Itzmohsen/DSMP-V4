package TwitterGatherDataFollowers.userRyersonU;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

/**
 * Studies: replicated experiments with statistics. A design builder for the
 * full factorial design (algorithms × clusters × policies × scenarios, with
 * repetitions under common random numbers, randomized order and warm-up
 * runs), a progress view with an estimate of the time left, and the analysis:
 * the exactness check across every run, means with 95% confidence intervals
 * per cell, and paired comparisons with the baseline policy (Holm-adjusted).
 * Past studies in Results/Studies load back for analysis.
 */
final class NodeLabStudiesPage extends NodeLabPanel.Page
{
	private static final long serialVersionUID = 1L;

	private static final int[] ALGORITHMS = {
		ControllerAgent.COS_SIM, ControllerAgent.K_MEANS, ControllerAgent.K_MEANSEUCLIDEAN,
		ControllerAgent.CommonNeighbors, ControllerAgent.TEMPORAL_GRAPH_FOUNDATION, ControllerAgent.SVM,
		ControllerAgent.SCALABLE_SVM, ControllerAgent.MLP, ControllerAgent.Doc2Vec
	};

	private final NodeLabPanel lab;
	private final NodeLabUi.Page page = new NodeLabUi.Page(1440);
	// design
	private final NodeLabUi.Field name = new NodeLabUi.Field("Policies on my cluster", 22, "Study name");
	private final NodeLabUi.Segmented datasetSource = new NodeLabUi.Segmented(
			new String[] {"The Simulator's dataset", "A file"}, null, 0);
	private final NodeLabUi.Text datasetLabel = NodeLabUi.caption(" ");
	private File datasetFile;
	private final NodeLabUi.ToggleGroup algorithms = new NodeLabUi.ToggleGroup();
	private final NodeLabUi.Stepper targets = new NodeLabUi.Stepper(4, 1, 1000);
	private final NodeLabUi.Segmented mode = new NodeLabUi.Segmented(
			new String[] {"Emulated", "Emulated with faults"}, null, 0);
	private final NodeLabUi.ToggleGroup clusters = new NodeLabUi.ToggleGroup();
	private final NodeLabUi.ToggleGroup policies = new NodeLabUi.ToggleGroup();
	private final JComboBox<SchedulerLabEngine.SchedulingPolicy> baseline =
			NodeLabUi.combo(SchedulerLabEngine.SchedulingPolicy.values());
	private final NodeLabUi.ToggleGroup scenarios = new NodeLabUi.ToggleGroup();
	private final NodeLabUi.Stepper repetitions = new NodeLabUi.Stepper(5, 1, 100);
	private final NodeLabUi.Stepper warmup = new NodeLabUi.Stepper(1, 0, 10);
	private final NodeLabUi.Field seed = new NodeLabUi.Field("42", 8, "Seed");
	private final NodeLabUi.Prose sizeText = NodeLabUi.prose(" ");
	private final NodeLabUi.Button run = new NodeLabUi.Button("Run study", NodeLabUi.ButtonKind.PRIMARY,
			NodeLabUi.Glyph.PLAY);
	private final NodeLabUi.Prose runHint = NodeLabUi.note(" ");
	// progress
	private final NodeLabUi.Card progressCard;
	private final ProgressLine progress = new ProgressLine();
	private final NodeLabUi.Text progressTitle = NodeLabUi.heading(" ");
	private final NodeLabUi.Prose progressDetail = NodeLabUi.note(" ");
	private final NodeLabUi.Button stop = new NodeLabUi.Button("Stop after this run", NodeLabUi.ButtonKind.DANGER,
			NodeLabUi.Glyph.STOP, true);
	private final JPanel log = NodeLabUi.stack(2);
	// results
	private final JPanel results = new JPanel(new BorderLayout());
	private final JPanel past = NodeLabUi.stack(6);
	private volatile StudyRunner runner;
	private StudyResult result;
	private String metric = StudyResult.METRICS[1][0];
	private long studyStartedAt;
	private double finishedSeconds;
	private int finishedRuns;
	private int totalRuns;
	private String pastKey = "";

	NodeLabStudiesPage(NodeLabPanel lab)
	{
		this.lab = lab;
		NodeLabUi.Hero hero = new NodeLabUi.Hero("Studies", "Replicated experiments, with statistics",
				"A study runs every combination of algorithms, clusters, policies and scenarios several times, with"
						+ " common random numbers, randomized order and warm-up runs, then reports means with 95%"
						+ " confidence intervals, paired tests against a baseline (Holm-adjusted) and whether every run"
						+ " of an algorithm produced exactly the same recommendations.",
				NebulaDesign.SUCCESS);
		NodeLabUi.Button load = new NodeLabUi.Button("Load a design file", NodeLabUi.ButtonKind.SECONDARY,
				NodeLabUi.Glyph.FOLDER);
		load.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent event)
			{
				loadDesignFile();
			}
		});
		NodeLabUi.Button folder = new NodeLabUi.Button("Studies folder", NodeLabUi.ButtonKind.GHOST,
				NodeLabUi.Glyph.FOLDER);
		folder.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent event)
			{
				NodeLabResultsPage.openFolder(new File(System.getProperty("dsmp.results.dir", "Results"), "Studies"));
			}
		});
		hero.right(NodeLabUi.stack(8, load, folder));
		progressCard = new NodeLabUi.Card("Progress", null);
		progressCard.action(stop);
		progressCard.content(NodeLabUi.stack(10, progressTitle, progress, progressDetail, log));
		progressCard.setVisible(false);
		stop.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent event)
			{
				StudyRunner current = runner;
				if (current != null)
				{
					current.cancel();
					stop.setEnabled(false);
					stop.setText("Stopping after this run…");
				}
			}
		});
		results.setOpaque(false);
		NodeLabUi.Card pastCard = new NodeLabUi.Card("Past studies",
				"Every study under Results/Studies; open one to analyse it here.");
		pastCard.content(past);
		page.section(hero).section(designCard()).section(progressCard).section(results).section(pastCard);
		add(NodeLabUi.scroll(page), BorderLayout.CENTER);
		refresh();
	}

	int refreshIntervalMs()
	{
		return runner != null ? 1000 : 5000;
	}

	// ------------------------------------------------------------ design

	private JComponent designCard()
	{
		NodeLabUi.Card card = new NodeLabUi.Card("Design",
				"Pick the factors; every combination becomes a cell, run once per repetition.");
		card.action(NodeLabUi.help("Why these choices",
				"Common random numbers: repetition r of every cell uses the same seeds, so all cells see the same"
						+ " faults and crashes and their differences are compared pairwise, which is much sharper."
						+ " Randomized order: within a repetition the cells run in a random order, so slow drift of"
						+ " this machine cannot pose as a policy effect. Warm-up runs come first and are not analysed,"
						+ " so just-in-time compilation does not favour later cells. Holm's method keeps the chance"
						+ " of any false positive among all comparisons of a measure at 5%."));
		for (int algorithm : ALGORITHMS)
		{
			algorithms.add(RecommendationModeSupport.algorithmName(algorithm), Integer.valueOf(algorithm),
					algorithm == ControllerAgent.K_MEANS);
		}
		for (SchedulerLabEngine.SchedulingPolicy policy : SchedulerLabEngine.SchedulingPolicy.values())
		{
			policies.add(policy.getLabel(), policy, policy == SchedulerLabEngine.SchedulingPolicy.ORIGINAL_DSMP
					|| policy == SchedulerLabEngine.SchedulingPolicy.CAPACITY_WEIGHTED
					|| policy == SchedulerLabEngine.SchedulingPolicy.WORK_STEALING
					|| policy == SchedulerLabEngine.SchedulingPolicy.QUEUE_LENGTH);
		}
		baseline.setSelectedItem(SchedulerLabEngine.SchedulingPolicy.ORIGINAL_DSMP);
		datasetSource.addChangeListener(new ChangeListener()
		{
			public void stateChanged(ChangeEvent event)
			{
				if (datasetSource.getSelected() == 1)
				{
					chooseDataset();
				}
				updateSize();
			}
		});
		ChangeListener changed = new ChangeListener()
		{
			public void stateChanged(ChangeEvent event)
			{
				updateSize();
			}
		};
		algorithms.addChangeListener(changed);
		clusters.addChangeListener(changed);
		policies.addChangeListener(changed);
		scenarios.addChangeListener(changed);
		repetitions.addChangeListener(changed);
		warmup.addChangeListener(changed);
		mode.addChangeListener(changed);
		run.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent event)
			{
				startStudy();
			}
		});
		NodeLabUi.Button save = new NodeLabUi.Button("Save design", NodeLabUi.ButtonKind.SECONDARY,
				NodeLabUi.Glyph.SAVE);
		save.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent event)
			{
				saveDesign();
			}
		});
		JPanel left = NodeLabUi.stack(16,
				NodeLabUi.labeled("Name", name),
				NodeLabUi.labeled("Dataset", NodeLabUi.stack(6, datasetSource, datasetLabel)),
				NodeLabUi.labeled("Algorithms", algorithms),
				NodeLabUi.spread(NodeLabUi.labeled("Target users per run", targets), NodeLabUi.labeled("Mode", mode)));
		JPanel numbers = NodeLabUi.grid(110, 3, 12);
		numbers.add(NodeLabUi.labeled("Repetitions", repetitions));
		numbers.add(NodeLabUi.labeled("Warm-up runs", warmup));
		numbers.add(NodeLabUi.labeled("Seed", seed));
		JPanel right = NodeLabUi.stack(16,
				NodeLabUi.labeled("Clusters", clusters),
				NodeLabUi.labeled("Policies", policies),
				NodeLabUi.labeled("Baseline for comparisons", baseline),
				NodeLabUi.labeled("Scenarios", scenarios),
				numbers);
		JPanel columns = NodeLabUi.grid(420, 2, 28);
		columns.add(NodeLabUi.top(left));
		columns.add(NodeLabUi.top(right));
		JPanel footer = new JPanel(new BorderLayout(16, 0));
		footer.setOpaque(false);
		footer.setBorder(new EmptyBorder(6, 0, 0, 0));
		footer.add(NodeLabUi.stack(4, sizeText, runHint), BorderLayout.CENTER);
		JPanel buttons = new JPanel(new BorderLayout());
		buttons.setOpaque(false);
		buttons.add(NodeLabUi.row(8, save, run), BorderLayout.NORTH);
		footer.add(buttons, BorderLayout.EAST);
		card.content(NodeLabUi.stack(18, columns, footer));
		return card;
	}

	private void chooseDataset()
	{
		JFileChooser chooser = new JFileChooser(new File("Dataset"));
		chooser.setDialogTitle("Dataset for the study");
		if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION)
		{
			datasetFile = chooser.getSelectedFile();
		}
		else if (datasetFile == null)
		{
			datasetSource.setSelectedQuietly(0);
		}
	}

	private void refreshFactors()
	{
		LinkedHashSet<String> selectedClusters = selectedTexts(clusters);
		LinkedHashSet<String> selectedScenarios = selectedTexts(scenarios);
		boolean first = clusters.chips().isEmpty();
		clusters.clear();
		clusters.add("Current cluster", ClusterConfig.current("Current cluster"),
				first || selectedClusters.contains("Current cluster"));
		for (ClusterConfig preset : ClusterConfig.presets(lab.heapMb()))
		{
			clusters.add(preset.getName(), preset, selectedClusters.contains(preset.getName()));
		}
		for (ClusterConfig saved : ClusterConfig.saved())
		{
			clusters.add(saved.getName(), saved, selectedClusters.contains(saved.getName()));
		}
		boolean firstScenarios = scenarios.chips().isEmpty();
		scenarios.clear();
		for (Scenario scenario : ScenarioLibrary.all())
		{
			scenarios.add(scenario.getName(), scenario, (firstScenarios && scenario.isEmpty())
					|| selectedScenarios.contains(scenario.getName()));
		}
		clusters.revalidate();
		scenarios.revalidate();
	}

	private static LinkedHashSet<String> selectedTexts(NodeLabUi.ToggleGroup group)
	{
		LinkedHashSet<String> texts = new LinkedHashSet<String>();
		for (NodeLabUi.ToggleChip chip : group.chips())
		{
			if (chip.isSelected())
			{
				texts.add(chip.getText());
			}
		}
		return texts;
	}

	/** The design as chosen, or null with the reason shown. */
	private StudyDesign design()
	{
		ArrayList<Integer> chosenAlgorithms = new ArrayList<Integer>();
		for (Object value : algorithms.selectedValues())
		{
			chosenAlgorithms.add((Integer)value);
		}
		ArrayList<ClusterConfig> chosenClusters = new ArrayList<ClusterConfig>();
		for (Object value : clusters.selectedValues())
		{
			ClusterConfig cluster = (ClusterConfig)value;
			chosenClusters.add("Current cluster".equals(cluster.getName()) ? ClusterConfig.current("Current cluster")
					: cluster);
		}
		ArrayList<SchedulerLabEngine.SchedulingPolicy> chosenPolicies = new ArrayList<SchedulerLabEngine.SchedulingPolicy>();
		for (Object value : policies.selectedValues())
		{
			chosenPolicies.add((SchedulerLabEngine.SchedulingPolicy)value);
		}
		ArrayList<Scenario> chosenScenarios = new ArrayList<Scenario>();
		for (Object value : scenarios.selectedValues())
		{
			chosenScenarios.add((Scenario)value);
		}
		SchedulerLabEngine.SchedulingPolicy base = (SchedulerLabEngine.SchedulingPolicy)baseline.getSelectedItem();
		if (base != null && !chosenPolicies.contains(base))
		{
			chosenPolicies.add(0, base);
		}
		if (chosenAlgorithms.isEmpty() || chosenClusters.isEmpty() || chosenPolicies.isEmpty())
		{
			return null;
		}
		return new StudyDesign(name.getText(), datasetSource.getSelected() == 1 ? datasetFile : null,
				chosenAlgorithms, targets.getValue(), mode.getSelected() == 1
						? RecommendationSchedulerPlan.Mode.SCHEDULING_EXPERIMENT : RecommendationSchedulerPlan.Mode.SCORING_SAFE,
				chosenPolicies, base, chosenClusters, chosenScenarios, repetitions.getValue(), warmup.getValue(),
				JsonText.parseLong(seed.getText(), 42L));
	}

	private void updateSize()
	{
		datasetLabel.setText(datasetSource.getSelected() == 0
				? (lab.getOwner() == null || lab.getOwner().loadedUserCount() == 0
						? "No users loaded in the Simulator yet."
						: lab.getOwner().loadedDatasetName() + " · " + lab.getOwner().loadedUserCount() + " users loaded")
				: datasetFile == null ? "No file chosen." : datasetFile.getPath());
		StudyDesign design = design();
		if (design == null)
		{
			sizeText.setText("Choose at least one algorithm, cluster and policy.");
			run.setEnabled(false);
			return;
		}
		int cells = design.cells().size();
		int runs = design.runCount();
		double pace = recentRunSeconds();
		sizeText.setText(String.format(Locale.US, "%d algorithm%s × %d cluster%s × %d polic%s × %d scenario%s = %d"
				+ " cell%s; × %d repetition%s + %d warm-up = %d runs%s.",
				design.getAlgorithms().size(), plural(design.getAlgorithms().size()), design.getClusters().size(),
				plural(design.getClusters().size()), design.getPolicies().size(),
				design.getPolicies().size() == 1 ? "y" : "ies", design.getScenarios().size(),
				plural(design.getScenarios().size()), cells, plural(cells), design.getRepetitions(),
				plural(design.getRepetitions()), design.getWarmup(), runs, Double.isNaN(pace) ? ""
						: ", about " + human(pace * runs) + " at the recent pace"));
		String blocker = blocker(design);
		run.setEnabled(blocker == null);
		runHint.setText(blocker == null ? "The lab's cluster and plan are set for each run and restored afterwards."
				: blocker);
	}

	private String blocker(StudyDesign design)
	{
		if (runner != null)
		{
			return "A study is running.";
		}
		if (lab.getOwner() == null)
		{
			return "Studies run through the simulator window.";
		}
		if (NodeLabPanel.isRunning() || lab.launcher().isBusy())
		{
			return "Wait for the current run to finish.";
		}
		if (design.getDataset() == null && lab.getOwner().loadedUserCount() == 0)
		{
			return "Load a dataset and get its users in the Simulator tab, or choose a file.";
		}
		return null;
	}

	private static String plural(int count)
	{
		return count == 1 ? "" : "s";
	}

	/** Seconds per run of the latest recorded runs (wall time), NaN without any. */
	private double recentRunSeconds()
	{
		if (finishedRuns > 0)
		{
			return finishedSeconds / finishedRuns;
		}
		return Double.NaN;
	}

	private static String human(double seconds)
	{
		if (seconds < 90.0)
		{
			return String.format(Locale.US, "%.0f s", seconds);
		}
		if (seconds < 5400.0)
		{
			return String.format(Locale.US, "%.0f min", seconds / 60.0);
		}
		return String.format(Locale.US, "%.1f h", seconds / 3600.0);
	}

	private void saveDesign()
	{
		StudyDesign design = design();
		if (design == null)
		{
			return;
		}
		JFileChooser chooser = new JFileChooser(new File("Studies"));
		chooser.setSelectedFile(new File(design.getName().replaceAll("[^A-Za-z0-9._-]+", "-") + ".study"));
		if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION)
		{
			return;
		}
		try
		{
			File file = chooser.getSelectedFile();
			File parent = file.getAbsoluteFile().getParentFile();
			if (parent != null)
			{
				parent.mkdirs();
			}
			java.nio.file.Files.write(file.toPath(), design.toText().getBytes(java.nio.charset.StandardCharsets.UTF_8));
			runHint.setText("Saved to " + file.getPath() + ". Run it headless with: python build.py study " + file.getPath());
		}
		catch (java.io.IOException failure)
		{
			NebulaDialogs.showError(this, "Could not save the design: " + failure.getMessage(), "Save design");
		}
	}

	private void loadDesignFile()
	{
		JFileChooser chooser = new JFileChooser(new File("Studies"));
		if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION)
		{
			return;
		}
		try
		{
			StudyDesign design = StudyDesign.load(chooser.getSelectedFile(), lab.heapMb());
			apply(design);
		}
		catch (Exception failure)
		{
			NebulaDialogs.showError(this, "Could not read the design: " + failure.getMessage(), "Load design");
		}
	}

	/** Puts a design's choices into the builder. */
	private void apply(StudyDesign design)
	{
		name.setText(design.getName());
		datasetFile = design.getDataset();
		datasetSource.setSelectedQuietly(datasetFile == null ? 0 : 1);
		for (NodeLabUi.ToggleChip chip : algorithms.chips())
		{
			chip.setSelectedQuietly(design.getAlgorithms().contains(chip.getValue()));
		}
		targets.setValueQuietly(design.getTargets());
		mode.setSelectedQuietly(design.getMode() == RecommendationSchedulerPlan.Mode.SCHEDULING_EXPERIMENT ? 1 : 0);
		ArrayList<String> clusterNames = new ArrayList<String>();
		for (ClusterConfig cluster : design.getClusters())
		{
			clusterNames.add(cluster.getName());
		}
		boolean missing = false;
		for (String clusterName : clusterNames)
		{
			boolean found = false;
			for (NodeLabUi.ToggleChip chip : clusters.chips())
			{
				found |= chip.getText().equalsIgnoreCase(clusterName);
			}
			missing |= !found;
		}
		if (missing)
		{
			for (ClusterConfig cluster : design.getClusters())
			{
				clusters.add(cluster.getName(), cluster, true);
			}
		}
		for (NodeLabUi.ToggleChip chip : clusters.chips())
		{
			chip.setSelectedQuietly(clusterNames.contains(chip.getText()));
		}
		for (NodeLabUi.ToggleChip chip : policies.chips())
		{
			chip.setSelectedQuietly(design.getPolicies().contains(chip.getValue()));
		}
		baseline.setSelectedItem(design.getBaseline());
		ArrayList<String> scenarioNames = new ArrayList<String>();
		for (Scenario scenario : design.getScenarios())
		{
			scenarioNames.add(scenario.getName());
		}
		for (NodeLabUi.ToggleChip chip : scenarios.chips())
		{
			chip.setSelectedQuietly(scenarioNames.contains(chip.getText()));
		}
		repetitions.setValueQuietly(design.getRepetitions());
		warmup.setValueQuietly(design.getWarmup());
		seed.setText(Long.toString(design.getSeed()));
		clusters.revalidate();
		updateSize();
		page.revalidate();
		page.repaint();
	}

	// ------------------------------------------------------------ running

	private void startStudy()
	{
		final StudyDesign design = design();
		if (design == null || blocker(design) != null)
		{
			return;
		}
		final ControllerAgentGui gui = lab.getOwner();
		studyStartedAt = System.currentTimeMillis();
		finishedRuns = 0;
		finishedSeconds = 0.0;
		totalRuns = design.runCount();
		log.removeAll();
		progressCard.setVisible(true);
		stop.setEnabled(true);
		stop.setText("Stop after this run");
		progressTitle.setText("Starting “" + design.getName() + "”");
		progress.set(0.0);
		final StudyRunner studyRunner = new StudyRunner(design, new GuiStudyDriver(gui), new StudyRunner.Progress()
		{
			public void runStarting(final int index, final int total, final StudyDesign.RunSlot slot)
			{
				SwingUtilities.invokeLater(new Runnable()
				{
					public void run()
					{
						progressTitle.setText(String.format(Locale.US, "Run %d of %d%s", index, total,
								slot.warmup ? " · warm-up" : " · repetition " + slot.repetition));
						progressDetail.setText(slot.cell.label() + "." + eta(index - 1, total));
						progress.set((index - 1) / (double)Math.max(1, total));
					}
				});
			}

			public void runFinished(final int index, final int total, final StudyResult.RunOutcome outcome)
			{
				SwingUtilities.invokeLater(new Runnable()
				{
					public void run()
					{
						finishedRuns++;
						if (!Double.isNaN(outcome.wallSeconds))
						{
							finishedSeconds += outcome.wallSeconds;
						}
						progress.set(index / (double)Math.max(1, total));
						addLog((outcome.isCompleted() ? "✓ " : "✗ ") + "Run " + index + ": " + outcome.slot.cell.label()
								+ (outcome.isCompleted() ? String.format(Locale.US, " · makespan %s",
										NodeLabUi.duration(outcome.metric("makespan_ms")))
										: " · " + (outcome.error.length() > 0 ? outcome.error : outcome.status)),
								outcome.isCompleted() ? NebulaDesign.TEXT_SECONDARY : NebulaDesign.DANGER);
					}
				});
			}

			public void message(final String text)
			{
				SwingUtilities.invokeLater(new Runnable()
				{
					public void run()
					{
						addLog(text, NebulaDesign.TEXT_TERTIARY);
					}
				});
			}
		}, lab.heapMb());
		runner = studyRunner;
		lab.launcher().setStudyActive(true);
		updateSize();
		new SwingWorker<StudyResult,Void>()
		{
			protected StudyResult doInBackground() throws Exception
			{
				return studyRunner.run();
			}

			protected void done()
			{
				runner = null;
				lab.launcher().setStudyActive(false);
				stop.setEnabled(false);
				try
				{
					result = get();
					progressTitle.setText(studyRunner.isCancelled() ? "Study stopped" : "Study finished");
					progressDetail.setText(String.format(Locale.US, "%d run(s) in %s · written to %s",
							result.outcomes().size(), human((System.currentTimeMillis() - studyStartedAt) / 1000.0),
							result.directory().getPath()));
					showResult(result);
				}
				catch (Exception failure)
				{
					Throwable cause = failure.getCause() == null ? failure : failure.getCause();
					progressTitle.setText("The study stopped with an error");
					progressDetail.setText(cause.getMessage() == null ? cause.toString() : cause.getMessage());
				}
				pastKey = "";
				lab.refreshAll();
			}
		}.execute();
	}

	private String eta(int done, int total)
	{
		if (done <= 0 || finishedRuns == 0)
		{
			return "";
		}
		double perRun = finishedSeconds / finishedRuns;
		return " About " + human(perRun * (total - done)) + " left.";
	}

	private void addLog(String text, Color color)
	{
		NodeLabUi.Prose line = NodeLabUi.note(text);
		line.setColor(color);
		log.add(line, 0);
		while (log.getComponentCount() > 10)
		{
			log.remove(log.getComponentCount() - 1);
		}
		log.revalidate();
		page.revalidate();
	}

	// ------------------------------------------------------------ results

	void refresh()
	{
		if (clusters.chips().isEmpty())
		{
			refreshFactors();
		}
		updateSize();
		refreshPast();
	}

	private void refreshPast()
	{
		List<File> directories = StudyResult.studyDirectories();
		String key = directories.toString();
		if (key.equals(pastKey))
		{
			return;
		}
		pastKey = key;
		past.removeAll();
		if (directories.isEmpty())
		{
			past.add(NodeLabUi.note("No studies yet. Design one above and run it."));
		}
		for (final File directory : directories)
		{
			past.add(pastRow(directory));
		}
		past.revalidate();
	}

	private JComponent pastRow(final File directory)
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
						openStudy(directory);
					}
				});
				setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			}

			protected void paintComponent(Graphics graphics)
			{
				Graphics2D g = (Graphics2D)graphics.create();
				NodeLabUi.prepare(g);
				g.setColor(hover ? NebulaDesign.SURFACE_ALT : NebulaDesign.INSET);
				g.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 12, 12));
				g.dispose();
			}
		};
		row.setOpaque(false);
		row.setBorder(new EmptyBorder(10, 14, 10, 12));
		String folder = directory.getName();
		String when = folder.length() > 15 ? folder.substring(0, 15) : folder;
		String title = folder.length() > 16 ? folder.substring(16).replace('-', ' ') : folder;
		String pretty = when;
		try
		{
			java.util.Date date = new java.text.SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).parse(when);
			pretty = new java.text.SimpleDateFormat("MMM d, HH:mm", Locale.US).format(date);
		}
		catch (java.text.ParseException ignored)
		{
			//Keep the raw stamp.
		}
		row.add(NodeLabUi.stack(2, NodeLabUi.strong(title), NodeLabUi.caption(pretty + " · " + directory.getPath())),
				BorderLayout.CENTER);
		NodeLabUi.Button open = new NodeLabUi.Button("Analyse", NodeLabUi.ButtonKind.GHOST, NodeLabUi.Glyph.CHART, true);
		open.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent event)
			{
				openStudy(directory);
			}
		});
		row.add(open, BorderLayout.EAST);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		return row;
	}

	private void openStudy(final File directory)
	{
		new SwingWorker<StudyResult,Void>()
		{
			protected StudyResult doInBackground() throws Exception
			{
				return StudyResult.load(directory, lab.heapMb());
			}

			protected void done()
			{
				try
				{
					result = get();
					showResult(result);
					javax.swing.SwingUtilities.invokeLater(new Runnable()
					{
						public void run()
						{
							results.scrollRectToVisible(new java.awt.Rectangle(0, 0, 10, 10));
						}
					});
				}
				catch (Exception failure)
				{
					Throwable cause = failure.getCause() == null ? failure : failure.getCause();
					NebulaDialogs.showError(NodeLabStudiesPage.this, "Could not load the study: " + cause.getMessage(),
							"Open study");
				}
			}
		}.execute();
	}

	/** Whether any cell of the study has a value for the measure. */
	private static boolean hasData(StudyResult value, String measure)
	{
		for (StudyResult.CellSummary summary : value.summaries(measure))
		{
			if (summary.summary.n > 0)
			{
				return true;
			}
		}
		return false;
	}

	private void showResult(final StudyResult value)
	{
		results.removeAll();
		NodeLabUi.Card card = new NodeLabUi.Card("Results · " + value.getDesign().getName(), null);
		JPanel chips = NodeLabPanel.flow(6);
		Map<String,LinkedHashSet<String>> hashes = value.resultHashesByAlgorithm();
		boolean exact = value.isExact();
		int checked = 0;
		for (LinkedHashSet<String> set : hashes.values())
		{
			checked += set.isEmpty() ? 0 : 1;
		}
		chips.add(new NodeLabUi.Chip(hashes.isEmpty() ? "no results hashes" : exact
				? "exact: every algorithm's runs are identical" : "NOT exact: results differ between runs",
				hashes.isEmpty() ? NodeLabUi.Tone.NEUTRAL : exact ? NodeLabUi.Tone.SUCCESS : NodeLabUi.Tone.DANGER, true));
		int failed = value.failedRuns();
		chips.add(new NodeLabUi.Chip(value.outcomes().size() + " runs · " + failed + " failed", failed == 0
				? NodeLabUi.Tone.INFO : NodeLabUi.Tone.WARNING, false));
		chips.add(new NodeLabUi.Chip(value.getDesign().cells().size() + " cells · " + value.getDesign().getRepetitions()
				+ " repetitions", NodeLabUi.Tone.NEUTRAL, false));
		NodeLabUi.Button folder = new NodeLabUi.Button("Open folder", NodeLabUi.ButtonKind.SECONDARY,
				NodeLabUi.Glyph.FOLDER, true);
		folder.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent event)
			{
				NodeLabResultsPage.openFolder(value.directory());
			}
		});
		NodeLabUi.Button reuse = new NodeLabUi.Button("Reuse this design", NodeLabUi.ButtonKind.GHOST,
				NodeLabUi.Glyph.COPY, true);
		reuse.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent event)
			{
				apply(value.getDesign());
			}
		});
		card.action(reuse).action(folder);
		//A measure no run produced (the main phase of an algorithm that runs none, such as K-Means)
		//would leave every cell empty: open on the first measure the runs have.
		if (!hasData(value, metric))
		{
			for (String[] candidate : StudyResult.METRICS)
			{
				if (hasData(value, candidate[0]))
				{
					metric = candidate[0];
					break;
				}
			}
		}
		String[] labels = new String[StudyResult.METRICS.length];
		int selected = 0;
		for (int index = 0; index < labels.length; index++)
		{
			labels[index] = StudyResult.METRICS[index][1];
			if (StudyResult.METRICS[index][0].equals(metric))
			{
				selected = index;
			}
		}
		final JComboBox<String> metricChoice = NodeLabUi.combo(labels);
		metricChoice.setSelectedIndex(selected);
		final JPanel analysis = new JPanel(new BorderLayout());
		analysis.setOpaque(false);
		metricChoice.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent event)
			{
				metric = StudyResult.METRICS[metricChoice.getSelectedIndex()][0];
				analysis.removeAll();
				analysis.add(analysis(value), BorderLayout.CENTER);
				analysis.revalidate();
				page.revalidate();
			}
		});
		analysis.add(analysis(value), BorderLayout.CENTER);
		JPanel metricRow = new JPanel(new BorderLayout(10, 0));
		metricRow.setOpaque(false);
		metricRow.add(NodeLabUi.caption("Measure"), BorderLayout.WEST);
		metricRow.add(NodeLabUi.fixedWidth(metricChoice, 320), BorderLayout.CENTER);
		JPanel metricHolder = new JPanel(new BorderLayout());
		metricHolder.setOpaque(false);
		metricHolder.add(metricRow, BorderLayout.WEST);
		JPanel exactness = NodeLabUi.stack(4);
		for (Map.Entry<String,LinkedHashSet<String>> entry : hashes.entrySet())
		{
			exactness.add(NodeLabUi.note(entry.getKey() + ": " + (entry.getValue().size() == 1
					? "1 distinct result over all its runs (" + entry.getValue().iterator().next().substring(0,
							Math.min(12, entry.getValue().iterator().next().length())) + "…)"
					: entry.getValue().size() + " distinct results — scheduling changed what was computed")));
		}
		card.content(NodeLabUi.stack(14, chips, exactness, metricHolder, analysis));
		results.add(card, BorderLayout.CENTER);
		results.revalidate();
		page.revalidate();
		page.repaint();
	}

	private JComponent analysis(StudyResult value)
	{
		String label = metric;
		for (String[] known : StudyResult.METRICS)
		{
			if (known[0].equals(metric))
			{
				label = known[1];
			}
		}
		final boolean time = metric.endsWith("_ms");
		final boolean fraction = metric.contains("efficiency") || metric.contains("utilization")
				|| metric.contains("fraction") || metric.contains("error");
		NodeLabUi.Formatter format = new NodeLabUi.Formatter()
		{
			public String format(double number)
			{
				return time ? NodeLabUi.duration(number) : fraction ? String.format(Locale.US, "%.1f%%", number * 100.0)
						: metric.startsWith("bytes") ? NodeLabUi.bytes(number) : NodeLabUi.number(number);
			}
		};
		NodeLabCharts.BarChart chart = new NodeLabCharts.BarChart();
		ArrayList<NodeLabCharts.BarValue> bars = new ArrayList<NodeLabCharts.BarValue>();
		for (StudyResult.CellSummary summary : value.summaries(metric))
		{
			boolean base = summary.cell.policy == value.getDesign().getBaseline();
			bars.add(new NodeLabCharts.BarValue(summary.cell.label(), summary.summary.mean, summary.summary.ciLow,
					summary.summary.ciHigh, base ? NebulaDesign.TEXT_TERTIARY : summary.cell.policy.isDynamic()
							? NebulaDesign.PURPLE : NebulaDesign.INFO,
					"n = " + summary.summary.n + (base ? " · baseline" : ""), false));
		}
		chart.set(bars, Double.NaN, null, format);
		chart.setEmptyText("No completed runs yet");
		NodeLabUi.Table table = new NodeLabUi.Table(NodeLabUi.Table.text("Group", 2.2f),
				NodeLabUi.Table.text("Policy", 1.4f), NodeLabUi.Table.number("Baseline", 1.0f),
				NodeLabUi.Table.number("Policy", 1.0f), NodeLabUi.Table.number("Change", 0.8f),
				NodeLabUi.Table.number("95% CI of difference", 1.6f), NodeLabUi.Table.number("Effect d", 0.7f),
				NodeLabUi.Table.number("p", 0.7f), NodeLabUi.Table.number("p (Holm)", 0.8f),
				NodeLabUi.Table.text("Verdict", 1.0f));
		ArrayList<String[]> rows = new ArrayList<String[]>();
		ArrayList<Color[]> colors = new ArrayList<Color[]>();
		for (StudyResult.PolicyComparison comparison : value.comparisons(metric))
		{
			StudyStatistics.Comparison c = comparison.comparison;
			boolean significant = !Double.isNaN(c.adjustedPValue) && c.adjustedPValue < 0.05;
			boolean lowerIsBetter = !(metric.contains("efficiency") || metric.contains("utilization"));
			boolean better = lowerIsBetter ? c.meanDifference < 0 : c.meanDifference > 0;
			rows.add(new String[] {
				comparison.cell.algorithmName() + " · " + comparison.cell.cluster.getName()
						+ (comparison.cell.scenario.isEmpty() ? "" : " · " + comparison.cell.scenario.getName()),
				comparison.cell.policy.getLabel(),
				format.format(c.baselineMean),
				format.format(c.treatmentMean),
				Double.isNaN(c.relativeChange) ? "—" : String.format(Locale.US, "%+.1f%%", c.relativeChange * 100.0),
				Double.isNaN(c.ciLow) ? "—" : signed(format, c.ciLow) + " … " + signed(format, c.ciHigh),
				Double.isNaN(c.effectSize) ? "—" : String.format(Locale.US, "%.2f", c.effectSize),
				pText(c.pValue),
				pText(c.adjustedPValue),
				c.n < 2 ? "too few runs" : significant ? (better ? "better" : "worse") : "no difference shown"
			});
			Color[] tones = new Color[10];
			tones[9] = c.n < 2 ? NebulaDesign.TEXT_TERTIARY : significant ? (better ? NebulaDesign.SUCCESS
					: NebulaDesign.DANGER) : NebulaDesign.TEXT_SECONDARY;
			colors.add(tones);
		}
		table.setRows(rows, colors);
		table.setEmptyText("No comparisons: the design needs the baseline policy and at least one other.");
		return NodeLabUi.stack(14, NodeLabOverviewPage.sectionTitle(label + " by cell (mean, 95% CI)"), chart,
				NodeLabOverviewPage.sectionTitle("Against the baseline, paired by repetition"), table);
	}

	private static String signed(NodeLabUi.Formatter format, double value)
	{
		return (value > 0 ? "+" : value < 0 ? "−" : "") + format.format(Math.abs(value));
	}

	private static String pText(double p)
	{
		return Double.isNaN(p) ? "—" : p < 0.001 ? "< 0.001" : String.format(Locale.US, "%.3f", p);
	}

	// ------------------------------------------------------------ progress line

	private static final class ProgressLine extends JComponent
	{
		private static final long serialVersionUID = 1L;
		private double fraction;

		void set(double value)
		{
			fraction = Math.max(0.0, Math.min(1.0, value));
			repaint();
		}

		public java.awt.Dimension getPreferredSize()
		{
			return new java.awt.Dimension(300, 12);
		}

		public java.awt.Dimension getMaximumSize()
		{
			return new java.awt.Dimension(Integer.MAX_VALUE, 12);
		}

		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D)graphics.create();
			NodeLabUi.prepare(g);
			g.setColor(NebulaDesign.INSET);
			g.fill(new RoundRectangle2D.Float(0, 0, getWidth(), 12, 12, 12));
			g.setPaint(new java.awt.GradientPaint(0, 0, NebulaDesign.ACCENT, getWidth(), 0, NebulaDesign.SUCCESS));
			g.fill(new RoundRectangle2D.Float(0, 0, (float)(getWidth() * fraction), 12, 12, 12));
			g.dispose();
		}
	}
}
