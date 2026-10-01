package TwitterGatherDataFollowers.userRyersonU;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.Ellipse2D;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

/**
 * The Simulator's front page: what the next run will do, the one next step,
 * and the quality of the latest run.
 */
final class SimulatorOverviewPage extends SimulatorPanel.Page
{
	private static final long serialVersionUID = 1L;

	private final SimulatorPanel simulator;
	private final ControllerAgentGui gui;
	private final NodeLabUi.Page page = new NodeLabUi.Page(1360);
	private final NodeLabUi.Button next =
			new NodeLabUi.Button("Load a dataset", NodeLabUi.ButtonKind.PRIMARY, NodeLabUi.Glyph.ARROW_RIGHT);
	private final NodeLabUi.Prose nextHint = NodeLabUi.note(" ");
	private final Row data = new Row("Data", SimulatorPanel.DATA);
	private final Row model = new Row("Model", SimulatorPanel.MODEL);
	private final Row targets = new Row("Targets & evaluation", SimulatorPanel.TARGETS);
	private final Row cluster = new Row("Cluster", SimulatorPanel.RUN);
	private final Row run = new Row("Run", SimulatorPanel.RUN);
	private final JPanel quality = new JPanel(new BorderLayout());
	private RecommendationQualityEvaluator.Report shownReport;
	private Runnable nextAction;

	SimulatorOverviewPage(SimulatorPanel simulator)
	{
		this.simulator = simulator;
		this.gui = simulator.gui();
		NodeLabUi.Hero hero = new NodeLabUi.Hero("Workspace", "Distributed Recommender Simulator",
				"Load a dataset, choose a recommendation model and who it recommends for, run it on the emulated"
						+ " cluster, and read its ranking quality against chance with confidence intervals.",
				NebulaDesign.ACCENT);
		NodeLabUi.Button nodeLab = new NodeLabUi.Button("Design the cluster", NodeLabUi.ButtonKind.SECONDARY,
				NodeLabUi.Glyph.CLUSTER);
		nodeLab.addActionListener(press(gui.simulatorNodeLabButton()));
		next.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent event)
			{
				if (nextAction != null)
				{
					nextAction.run();
				}
			}
		});
		hero.right(NodeLabUi.stack(8, next, nodeLab));

		NodeLabUi.Card nextRun = new NodeLabUi.Card("Next run",
				"What the next run will use. Each step can be changed on its page.");
		JPanel checklist = NodeLabUi.stack(0, data, model, targets, cluster, run);
		JPanel footer = new JPanel(new BorderLayout(14, 0));
		footer.setOpaque(false);
		footer.setBorder(new EmptyBorder(12, 0, 0, 0));
		footer.add(nextHint, BorderLayout.CENTER);
		JPanel body = new JPanel(new BorderLayout());
		body.setOpaque(false);
		body.add(checklist, BorderLayout.CENTER);
		body.add(footer, BorderLayout.SOUTH);
		nextRun.content(body);

		NodeLabUi.Card latest = new NodeLabUi.Card("Latest quality",
				"How well the last run's lists recover each target's community, against chance.");
		NodeLabUi.Button all = new NodeLabUi.Button("Results", NodeLabUi.ButtonKind.GHOST,
				NodeLabUi.Glyph.ARROW_RIGHT, true);
		all.addActionListener(go(SimulatorPanel.RESULTS));
		latest.action(all);
		quality.setOpaque(false);
		latest.content(quality);

		JPanel top = NodeLabUi.grid(420, 2, 16);
		top.add(nextRun);
		top.add(latest);

		JPanel journey = NodeLabUi.grid(220, 5, 14);
		journey.add(step("1", NodeLabUi.Glyph.FOLDER, NodeLabUi.Tone.ACCENT, "Data",
				"A text file, the database or a generated network; its date window, text processing and users.",
				SimulatorPanel.DATA));
		journey.add(step("2", NodeLabUi.Glyph.SPARK, NodeLabUi.Tone.PURPLE, "Model",
				"Nine algorithms, from cosine similarity to a learned temporal graph model; what they recommend.",
				SimulatorPanel.MODEL));
		journey.add(step("3", NodeLabUi.Glyph.TARGET, NodeLabUi.Tone.SUCCESS, "Targets",
				"Who gets recommendations: chosen users, a stratified sample or everyone; the cutoff k.",
				SimulatorPanel.TARGETS));
		journey.add(step("4", NodeLabUi.Glyph.PLAY, NodeLabUi.Tone.INFO, "Run",
				"Initialize the agents and run on the emulated cluster; follow it live, node by node.",
				SimulatorPanel.RUN));
		journey.add(step("5", NodeLabUi.Glyph.CHART, NodeLabUi.Tone.WARNING, "Results",
				"Precision, recall, NDCG, MRR and MAP with intervals, chance baselines and paired tests.",
				SimulatorPanel.RESULTS));

		NodeLabUi.Card honest = new NodeLabUi.Card("What the numbers mean", null);
		JPanel columns = NodeLabUi.grid(260, 3, 22);
		columns.add(SimulatorPanel.principle(NodeLabUi.Glyph.TARGET, NodeLabUi.Tone.SUCCESS, "Ground truth from the data",
				"A recommended user is relevant when it follows the target's own account (same community);"
						+ " a recommended account is relevant when the target follows it."));
		columns.add(SimulatorPanel.principle(NodeLabUi.Glyph.GAUGE, NodeLabUi.Tone.PURPLE, "Always against chance",
				"Every metric is shown with its exact expectation under a random ranking and a most-popular"
						+ " baseline, a 95% interval over targets and a paired test."));
		columns.add(SimulatorPanel.principle(NodeLabUi.Glyph.CHECK, NodeLabUi.Tone.INFO, "Never trained on the answer",
				"Classifiers never learn from the targets they score: targets are held out of their own"
						+ " model. Results are identical on any number of nodes."));
		honest.content(columns);

		page.section(hero).section(top).section(SimulatorPanel.sectionTitle("The experiment, step by step"))
				.section(journey).section(honest);
		add(NodeLabUi.scroll(page), BorderLayout.CENTER);
		refresh();
	}

	void refresh()
	{
		List<String> users = gui.loadedUsers();
		Map<String,String> followees = gui.simulatorFollowees();
		HashSet<String> communities = new HashSet<String>();
		for (String user : users)
		{
			String followee = followees.get(user);
			if (followee != null && followee.length() > 0)
			{
				communities.add(followee);
			}
		}
		String dataset = gui.simulatorDatasetName();
		boolean loaded = !users.isEmpty();
		data.set(loaded,
				loaded ? (dataset == null || dataset.isEmpty() ? gui.simulatorSourceTitle() : dataset)
						+ " · " + users.size() + " users · " + communities.size() + " communities"
						: dataset == null || dataset.isEmpty() ? "No dataset yet" : dataset + " · users not loaded");
		String algorithm = gui.simulatorAlgorithmBox() == null ? ""
				: String.valueOf(gui.simulatorAlgorithmBox().getSelectedItem());
		Object mode = gui.simulatorTargetModeBox() == null ? null : gui.simulatorTargetModeBox().getSelectedItem();
		model.set(true, algorithm + " · " + (mode == null ? "Algorithm default" : mode) + " · "
				+ gui.simulatorParameterProfile().toLowerCase(Locale.US) + " parameters");
		int targetCount = simulator.targetCount();
		targets.set(targetCount > 0, gui.getTargetSelectionMode() + " · " + targetCount
				+ (targetCount == 1 ? " target" : " targets") + " · k = " + gui.simulatorTopKField().getText().trim());
		cluster.set(true, NodeLabPanel.clusterSummary() + " · " + NodeLabPanel.planSummary());
		String status = gui.simulatorStatusTitle();
		RecommendationQualityEvaluator.Report latestReport = gui.getLatestQualityReport();
		boolean finished = latestReport != null && latestReport.setup.runId.equals(gui.getCurrentRunId());
		run.set(gui.simulatorInitialized() || finished, SimulatorPanel.capitalize(status.isEmpty() ? "Ready" : status)
				+ (gui.simulatorStatusDetail().isEmpty() ? "" : " · " + gui.simulatorStatusDetail()));
		chooseNextStep(loaded, targetCount);
		RecommendationQualityEvaluator.Report report = gui.getLatestQualityReport();
		if (report != shownReport || quality.getComponentCount() == 0)
		{
			shownReport = report;
			quality.removeAll();
			quality.add(report == null ? NodeLabUi.emptyState(NodeLabUi.Glyph.CHART, "No results yet",
					"Run a simulation: its quality appears here.", null) : summary(report), BorderLayout.CENTER);
			quality.revalidate();
			quality.repaint();
		}
	}

	/** The one next step: the first thing missing, else run, else read the results. */
	private void chooseNextStep(boolean loaded, int targetCount)
	{
		final javax.swing.JButton load = gui.simulatorLoadUsersButton();
		final javax.swing.JButton initialize = gui.simulatorInitializeButton();
		final javax.swing.JButton start = gui.simulatorStartButton();
		String dataset = gui.simulatorDatasetName();
		if (!loaded && (dataset == null || dataset.isEmpty()) && gui.simulatorSourceMode() == ControllerAgentGui.FROM_TEXT)
		{
			setNext("Choose a dataset", "Pick a dataset file to begin.", new Runnable()
			{
				public void run()
				{
					simulator.show(SimulatorPanel.DATA);
				}
			});
		}
		else if (!loaded)
		{
			setNext("Load users", "Reads the dataset and lists its users.", pressing(load));
			next.setEnabled(load != null && load.isEnabled());
			return;
		}
		else if (targetCount == 0)
		{
			setNext("Choose targets", "Select users, or take a stratified sample, on the Targets page.",
					showing(SimulatorPanel.TARGETS));
		}
		else if (gui.simulatorInitializing())
		{
			setNext("Initializing...", "Creating the agents and preparing the model.", showing(SimulatorPanel.RUN));
		}
		else if (!gui.simulatorInitialized() && gui.getLatestQualityReport() != null
				&& gui.getLatestQualityReport().setup.runId.equals(gui.getCurrentRunId()))
		{
			setNext("View results", "The run is complete. Initialize again to run with new settings.",
					showing(SimulatorPanel.RESULTS));
		}
		else if (!gui.simulatorInitialized())
		{
			setNext("Initialize", "Creates the agents for " + targetCount + (targetCount == 1 ? " target." : " targets."),
					pressing(initialize));
			next.setEnabled(initialize != null && initialize.isEnabled());
			return;
		}
		else if (start != null && start.isEnabled())
		{
			setNext("Run simulation", "Runs the initialized experiment.", new Runnable()
			{
				public void run()
				{
					simulator.show(SimulatorPanel.RUN);
					start.doClick();
				}
			});
		}
		else if (gui.getLatestQualityReport() != null)
		{
			setNext("View results", "The latest run's quality, with intervals and baselines.",
					showing(SimulatorPanel.RESULTS));
		}
		else
		{
			setNext("Watch the run", gui.simulatorStatusDetail(), showing(SimulatorPanel.RUN));
		}
		next.setEnabled(true);
	}

	private void setNext(String label, String hint, Runnable action)
	{
		if (!label.equals(next.getText()))
		{
			next.setText(label);
		}
		nextHint.setText(hint);
		nextAction = action;
	}

	private Runnable pressing(final javax.swing.JButton button)
	{
		return new Runnable()
		{
			public void run()
			{
				if (button != null && button.isEnabled())
				{
					button.doClick();
				}
			}
		};
	}

	private Runnable showing(final String key)
	{
		return new Runnable()
		{
			public void run()
			{
				simulator.show(key);
			}
		};
	}

	/** The headline metric with its interval and lift, and three more figures. */
	private JComponent summary(RecommendationQualityEvaluator.Report report)
	{
		int k = report.setup.cutoff;
		RecommendationQualityEvaluator.MetricSummary ndcg = report.metric("ndcg");
		NodeLabUi.Text headline = new NodeLabUi.Text(SimulatorResultsPage.format(ndcg.mean),
				NebulaDesign.fontSemiBold(34f), NebulaDesign.TEXT_PRIMARY);
		NodeLabUi.Text label = NodeLabUi.strong("NDCG@" + k + " · " + report.setup.algorithm + " · "
				+ report.evaluated() + (report.evaluated() == 1 ? " target" : " targets"));
		NodeLabUi.Prose detail = NodeLabUi.prose(SimulatorResultsPage.intervalText(ndcg) + ". Chance: "
				+ SimulatorResultsPage.format(ndcg.random) + " (" + SimulatorResultsPage.liftText(ndcg) + ").");
		NodeLabUi.Chip significance = SimulatorResultsPage.significanceChip(ndcg);
		JPanel figures = NodeLabUi.grid(130, 4, 10);
		figures.add(figure("P@" + k, report.metric("precision")));
		figures.add(figure("Hit rate", report.metric("hit_rate")));
		figures.add(figure("MRR", report.metric("mrr")));
		NodeLabUi.Metric accuracy = new NodeLabUi.Metric("Top-1 followee", SimulatorResultsPage.percent(report.accuracy),
				"macro-F1 " + SimulatorResultsPage.format(report.macroF1), NebulaDesign.PURPLE);
		figures.add(accuracy);
		return NodeLabUi.stack(10, label, NodeLabUi.row(12, headline, significance), detail, figures);
	}

	private static JComponent figure(String label, RecommendationQualityEvaluator.MetricSummary metric)
	{
		return new NodeLabUi.Metric(label, SimulatorResultsPage.format(metric.mean),
				"chance " + SimulatorResultsPage.format(metric.random), NebulaDesign.ACCENT);
	}

	private ActionListener go(final String key)
	{
		return new ActionListener()
		{
			public void actionPerformed(ActionEvent event)
			{
				simulator.show(key);
			}
		};
	}

	private static ActionListener press(final javax.swing.JButton button)
	{
		return new ActionListener()
		{
			public void actionPerformed(ActionEvent event)
			{
				if (button != null && button.isEnabled())
				{
					button.doClick();
				}
			}
		};
	}

	private JComponent step(String number, NodeLabUi.Glyph glyph, NodeLabUi.Tone tone, String title, String text,
			String key)
	{
		return SimulatorPanel.tile(number, glyph, tone, title, text, showing(key));
	}

	/** One line of the checklist: a status dot, the step, its value and a link to its page. */
	private final class Row extends JPanel
	{
		private static final long serialVersionUID = 1L;
		private final Dot dot = new Dot();
		private final NodeLabUi.Prose value = NodeLabUi.prose(" ");

		Row(String title, final String key)
		{
			super(new BorderLayout(12, 0));
			setOpaque(false);
			setBorder(new EmptyBorder(9, 0, 9, 0));
			JPanel left = new JPanel(new BorderLayout(10, 0));
			left.setOpaque(false);
			left.add(dot, BorderLayout.WEST);
			NodeLabUi.Text name = NodeLabUi.strong(title);
			name.setPreferredSize(new Dimension(150, name.getPreferredSize().height));
			left.add(name, BorderLayout.CENTER);
			add(left, BorderLayout.WEST);
			add(value, BorderLayout.CENTER);
			NodeLabUi.Button change = new NodeLabUi.Button("Change", NodeLabUi.ButtonKind.GHOST, null, true);
			change.addActionListener(go(key));
			add(change, BorderLayout.EAST);
		}

		void set(boolean ready, String text)
		{
			dot.ready = ready;
			dot.repaint();
			value.setText(text);
		}

		public Dimension getMaximumSize()
		{
			return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
		}
	}

	private static final class Dot extends JComponent
	{
		private static final long serialVersionUID = 1L;
		private boolean ready;

		public Dimension getPreferredSize()
		{
			return new Dimension(18, 18);
		}

		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D)graphics.create();
			NodeLabUi.prepare(g);
			Color color = ready ? NebulaDesign.SUCCESS : NebulaDesign.WARNING;
			g.setColor(NodeLabUi.alpha(color, 40));
			g.fill(new Ellipse2D.Float(0, 0, 18, 18));
			g.setColor(color);
			if (ready)
			{
				g.translate(4, 4);
				NodeLabUi.paintGlyph(g, NodeLabUi.Glyph.CHECK, 10);
			}
			else
			{
				g.fill(new Ellipse2D.Float(6, 6, 6, 6));
			}
			g.dispose();
		}
	}
}
