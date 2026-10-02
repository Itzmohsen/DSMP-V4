package TwitterGatherDataFollowers.userRyersonU;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

/**
 * Evidence Studio: why each recommendation was made.
 *
 * <pre>
 * Explain   one recommendation: its exact evidence, how faithful that evidence is to the algorithm,
 *           what would change it, the tweets behind it, the ground truth, the run graph and TGF
 * Run       how well the evidence explains the whole run, and how relevance falls with rank
 * Methods   the reference models, the measures and the references
 * </pre>
 *
 * The studio explains the runs of this session as their results arrive and
 * any recorded run (its recommendations, corpus, graph and TGF report).
 */
final class RecommendationEvidenceStudioPanel extends JPanel
{
	private static final long serialVersionUID = 1L;

	static final String EXPLAIN = "explain";
	static final String RUN = "run";
	static final String METHODS = "methods";

	/** A page of the studio. */
	abstract static class Page extends JPanel
	{
		private static final long serialVersionUID = 1L;

		Page()
		{
			super(new BorderLayout());
			setOpaque(false);
		}

		/** Shows a run; null with a message when there is none. */
		abstract void show(RecommendationEvidenceEngine.EvidenceRun run, String message);
	}

	/** A run in the picker. */
	static final class Choice
	{
		final String runId;
		final File record;
		final String label;

		Choice(String runId, File record, String label)
		{
			this.runId = runId;
			this.record = record;
			this.label = label;
		}

		public String toString()
		{
			return label;
		}
	}

	private final ControllerAgentGui controller;
	private final CardLayout cards = new CardLayout();
	private final JPanel deck = new JPanel(cards);
	private final LinkedHashMap<String,Page> pages = new LinkedHashMap<String,Page>();
	private final StudioShell.Navigation navigation;
	private final JComboBox<Choice> picker = NodeLabUi.combo(new Choice[0]);
	private final NodeLabUi.Chip status = new NodeLabUi.Chip("No run", NodeLabUi.Tone.NEUTRAL, true);
	private final NodeLabUi.Button folder = NodeLabUi.iconButton(NodeLabUi.Glyph.FOLDER,
			"Open this run's record folder");
	/** This session's runs, newest last. */
	private final LinkedHashMap<String,RecommendationEvidenceEngine.EvidenceRun> live =
			new LinkedHashMap<String,RecommendationEvidenceEngine.EvidenceRun>();
	/** When each of this session's runs delivered its first list, so the picker tells them apart. */
	private final HashMap<String,String> liveStarted = new HashMap<String,String>();
	private final HashMap<String,RecommendationEvidenceEngine.EvidenceRun> recorded =
			new HashMap<String,RecommendationEvidenceEngine.EvidenceRun>();
	private final HashMap<String,Object[]> context = new HashMap<String,Object[]>();
	private List<Choice> choices = new ArrayList<Choice>();
	private RecommendationEvidenceEngine.EvidenceRun run;
	private String message = "Run the Simulator: every recommendation it makes can be explained here.";
	private boolean followLatest = true;
	private boolean updating;
	private int request;
	private String current = EXPLAIN;

	RecommendationEvidenceStudioPanel(ControllerAgentGui controller)
	{
		super(new BorderLayout());
		this.controller = controller;
		setOpaque(false);
		deck.setOpaque(false);
		pages.put(EXPLAIN, new EvidenceExplainPage(this));
		pages.put(RUN, new EvidenceRunPage(this));
		pages.put(METHODS, new EvidenceMethodsPage());
		for (java.util.Map.Entry<String,Page> page : pages.entrySet())
		{
			deck.add(page.getValue(), page.getKey());
		}
		navigation = new StudioShell.Navigation("Evidence Studio", "Why each recommendation was made",
				NodeLabUi.Glyph.FLASK, NebulaDesign.SUCCESS, NebulaDesign.ACCENT,
				new String[] {EXPLAIN, RUN, METHODS}, new String[] {"Explain", "Run", "Methods"},
				new NodeLabUi.Glyph[] {NodeLabUi.Glyph.TARGET, NodeLabUi.Glyph.CHART, NodeLabUi.Glyph.FLASK},
				new StudioShell.PageListener()
				{
					public void pageChosen(String key)
					{
						show(key);
					}
				});
		picker.setPreferredSize(new Dimension(380, 34));
		picker.setToolTipText("The run whose recommendations are explained (newest first)");
		picker.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent event)
			{
				if (updating)
				{
					return;
				}
				Choice choice = (Choice)picker.getSelectedItem();
				if (choice != null)
				{
					followLatest = !choices.isEmpty() && choice == choices.get(0);
					open(choice);
				}
			}
		});
		folder.setEnabled(false);
		folder.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent event)
			{
				TemporalGraphFoundationPanel.openFolder(run == null ? null : run.record);
			}
		});
		NodeLabUi.Button refresh = NodeLabUi.iconButton(NodeLabUi.Glyph.REFRESH, "Re-read the run records");
		refresh.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent event)
			{
				refreshChoices();
			}
		});
		JPanel top = new JPanel(new BorderLayout());
		top.setOpaque(false);
		top.add(navigation, BorderLayout.CENTER);
		JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
		right.setOpaque(false);
		right.add(status);
		right.add(picker);
		right.add(folder);
		right.add(refresh);
		right.setBorder(new EmptyBorder(20, 0, 0, 24));
		top.add(right, BorderLayout.EAST);
		add(top, BorderLayout.NORTH);
		add(deck, BorderLayout.CENTER);
		RunRecords.addListener(new RunRecords.Listener()
		{
			public void runRecorded(SchedulingRunContext runContext, RunMetrics metrics, final File directory)
			{
				SwingUtilities.invokeLater(new Runnable()
				{
					public void run()
					{
						//The run's graph and TGF report now exist: load them for this run.
						context.remove(runContext.getRunId());
						refreshChoices();
						if (run != null && run.runId.equals(runContext.getRunId()))
						{
							publish();
						}
					}
				});
			}
		});
		refreshChoices();
	}

	// ======================================================== simulator hooks

	/** Called when the tab is shown. */
	void refreshFromSimulator()
	{
		refreshChoices();
	}

	/** Called when a new run starts: its results replace nothing, they become the newest run. */
	void clearRecommendations()
	{
		followLatest = true;
	}

	/** One target's results, as they arrive. */
	void recordRecommendation(final SimulatorHelpPanel.Snapshot snapshot,
			final RecommendationEvidenceEngine.RecommendationEvent event)
	{
		if (event == null || event.matches.isEmpty())
		{
			return;
		}
		Runnable update = new Runnable()
		{
			public void run()
			{
				RecommendationEvidenceEngine.EvidenceRun target = live.get(event.runId);
				if (target == null)
				{
					SimulatorHelpPanel.Snapshot safe = snapshot == null ? SimulatorHelpPanel.Snapshot.empty() : snapshot;
					String variant = controller == null ? AlgorithmParameterSettings.DEFAULT_CN_VARIANT
							: controller.getAlgorithmParameterSettings().getCommonNeighborsVariant();
					target = new RecommendationEvidenceEngine.EvidenceRun(event.runId, safe.algorithm,
							event.candidateType, safe.datasetName, RunCorpus.of(event.runId), null,
							Math.max(1, safe.topRecommendations), variant);
					live.put(event.runId, target);
					liveStarted.put(event.runId, new SimpleDateFormat("HH:mm:ss", Locale.US).format(new java.util.Date()));
				}
				target.add(event);
				refreshChoices();
				if (run == target)
				{
					publish();
				}
			}
		};
		if (SwingUtilities.isEventDispatchThread())
		{
			update.run();
		}
		else
		{
			SwingUtilities.invokeLater(update);
		}
	}

	// ============================================================== shared

	ControllerAgentGui controller()
	{
		return controller;
	}

	void show(String key)
	{
		if (!pages.containsKey(key))
		{
			return;
		}
		current = key;
		cards.show(deck, key);
		navigation.select(key);
	}

	String currentPage()
	{
		return current;
	}

	Page page(String key)
	{
		return pages.get(key);
	}

	RecommendationEvidenceEngine.EvidenceRun run()
	{
		return run;
	}

	/**
	 * The run graph and TGF report of a run, loaded once (off the event thread)
	 * and handed to {@code ready} on it; null parts when the run has none yet.
	 */
	void context(final RecommendationEvidenceEngine.EvidenceRun target, final ContextListener ready)
	{
		Object[] known = context.get(target.runId);
		if (known != null)
		{
			ready.loaded((RunGraphRecord.Loaded)known[0], (TgfReport)known[1]);
			return;
		}
		Thread worker = new Thread(new Runnable()
		{
			public void run()
			{
				RunGraphRecord.Loaded graph = null;
				TgfReport tgf = null;
				try
				{
					if (target.record != null)
					{
						graph = RunGraphRecord.load(target.record);
						tgf = TgfReport.load(target.record);
					}
					else
					{
						RunGraphRecord.Loaded latest = RunGraphRecord.latest();
						graph = latest != null && target.runId.equals(latest.graph.runId) ? latest : null;
						TemporalGraphFoundationModelSupport.Result result = TemporalGraphFoundationRegistry.latest();
						tgf = result != null && target.runId.equals(result.getRunId()) ? TgfReport.of(result) : null;
					}
				}
				catch (Exception | OutOfMemoryError unreadable)
				{
					//Explanations stay available without the graph and TGF parts.
				}
				final RunGraphRecord.Loaded loadedGraph = graph;
				final TgfReport loadedTgf = tgf;
				SwingUtilities.invokeLater(new Runnable()
				{
					public void run()
					{
						if (target.record != null || loadedGraph != null)
						{
							context.put(target.runId, new Object[] {loadedGraph, loadedTgf});
						}
						ready.loaded(loadedGraph, loadedTgf);
					}
				});
			}
		}, "DSMP-Evidence-Context");
		worker.setDaemon(true);
		worker.start();
	}

	/** Receives a run's graph and TGF report. */
	interface ContextListener
	{
		void loaded(RunGraphRecord.Loaded graph, TgfReport tgf);
	}

	// ============================================================== choices

	private void refreshChoices()
	{
		ArrayList<Choice> next = new ArrayList<Choice>();
		ArrayList<String> liveIds = new ArrayList<String>(live.keySet());
		for (int i = liveIds.size() - 1; i >= 0; i--)
		{
			RecommendationEvidenceEngine.EvidenceRun session = live.get(liveIds.get(i));
			String started = liveStarted.get(session.runId);
			next.add(new Choice(session.runId, null, "This session"
					+ (started == null ? "" : " " + started) + " · " + session.algorithmName + " · "
					+ session.datasetName + " · " + session.size()
					+ (session.size() == 1 ? " target" : " targets")));
		}
		for (RunRecordReader.Entry entry : RunRecordReader.index())
		{
			if (!entry.exists() || live.containsKey(entry.getRunId())
					|| !new File(entry.directory, "recommendations.csv").isFile())
			{
				continue;
			}
			next.add(new Choice(entry.getRunId(), entry.directory, label(entry)));
		}
		boolean changed = next.size() != choices.size();
		for (int i = 0; !changed && i < next.size(); i++)
		{
			changed = !next.get(i).label.equals(choices.get(i).label);
		}
		if (changed)
		{
			Choice selected = (Choice)picker.getSelectedItem();
			choices = next;
			updating = true;
			picker.removeAllItems();
			for (Choice choice : choices)
			{
				picker.addItem(choice);
			}
			Choice keep = null;
			if (!followLatest && selected != null)
			{
				for (Choice choice : choices)
				{
					if (choice.runId.equals(selected.runId))
					{
						keep = choice;
					}
				}
			}
			if (keep == null && !choices.isEmpty())
			{
				keep = choices.get(0);
				followLatest = true;
			}
			picker.setSelectedItem(keep);
			updating = false;
			if (keep != null && (run == null || !run.runId.equals(keep.runId)))
			{
				open(keep);
			}
		}
		if (choices.isEmpty() && run == null)
		{
			publish();
		}
	}

	private static String label(RunRecordReader.Entry entry)
	{
		String created = entry.get("created");
		String when = created;
		try
		{
			java.util.Date date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).parse(created);
			when = new SimpleDateFormat("MMM d HH:mm", Locale.US).format(date);
		}
		catch (java.text.ParseException unreadable)
		{
			// The raw timestamp is shown.
		}
		String targets = entry.get("quality_targets");
		return when + " · " + entry.get("algorithm") + " · " + entry.get("dataset")
				+ (targets.length() == 0 ? "" : " · " + targets + (targets.equals("1") ? " target" : " targets"));
	}

	private void open(final Choice choice)
	{
		if (choice.record == null)
		{
			run = live.get(choice.runId);
			message = null;
			publish();
			return;
		}
		RecommendationEvidenceEngine.EvidenceRun known = recorded.get(choice.runId);
		if (known != null)
		{
			run = known;
			message = null;
			publish();
			return;
		}
		final int ticket = ++request;
		run = null;
		message = "Loading the run…";
		publish();
		Thread worker = new Thread(new Runnable()
		{
			public void run()
			{
				RecommendationEvidenceEngine.EvidenceRun loaded = null;
				String failure = null;
				try
				{
					loaded = RecommendationEvidenceEngine.EvidenceRun.fromRecord(choice.record);
				}
				catch (Exception | OutOfMemoryError problem)
				{
					failure = "The run record could not be read: " + problem.getMessage();
				}
				final RecommendationEvidenceEngine.EvidenceRun done = loaded;
				final String reason = failure;
				SwingUtilities.invokeLater(new Runnable()
				{
					public void run()
					{
						if (ticket != request)
						{
							return;
						}
						if (done != null)
						{
							recorded.put(done.runId, done);
						}
						RecommendationEvidenceStudioPanel.this.run = done;
						message = reason;
						publish();
					}
				});
			}
		}, "DSMP-Evidence-Loader");
		worker.setDaemon(true);
		worker.start();
	}

	private void publish()
	{
		folder.setEnabled(run != null && run.record != null);
		if (run == null)
		{
			boolean loading = message != null && message.startsWith("Loading");
			status.set(loading ? "Loading" : "No run", loading ? NodeLabUi.Tone.INFO : NodeLabUi.Tone.NEUTRAL);
			status.setToolTipText(message);
		}
		else
		{
			status.set(run.live() ? "Live" : "Recorded", run.live() ? NodeLabUi.Tone.INFO : NodeLabUi.Tone.SUCCESS);
			status.setToolTipText(run.corpusFile == null ? "No corpus: ground truth and run graph only"
					: "Corpus " + run.corpusFile.getName());
		}
		for (Page page : pages.values())
		{
			page.show(run, message);
		}
		navigation.repaint();
	}

	protected void paintComponent(Graphics graphics)
	{
		StudioShell.paintCanvas(graphics, getWidth(), getHeight());
	}
}
