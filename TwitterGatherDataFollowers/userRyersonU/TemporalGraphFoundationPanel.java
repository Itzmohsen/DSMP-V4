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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

/**
 * TGF Studio: the Temporal Graph Foundation model of a run.
 *
 * <pre>
 * Overview     what TGF predicted, how well (cross-validated and on the run's targets), and why
 * Targets      every target: its community probabilities, conformal set and complete explanation
 * Model        the learners and their stack weights, calibration, confusion, conformal coverage
 * Communities  what characterises each community: distinctive terms, when its users are active
 * Methods      the algorithm, its parameters, its guarantees and its references
 * </pre>
 *
 * The studio follows the newest TGF run (live while it runs, then from its
 * record) and can open any earlier run whose record holds a TGF report.
 */
final class TemporalGraphFoundationPanel extends JPanel
{
	private static final long serialVersionUID = 1L;

	static final String OVERVIEW = "overview";
	static final String TARGETS = "targets";
	static final String MODEL = "model";
	static final String COMMUNITIES = "communities";
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

		/** Shows a report; null with a message while loading or when there is none. */
		abstract void show(TgfReport report, String message);
	}

	/** A run in the picker: the live run of this session or a record. */
	static final class Choice
	{
		final File directory;
		final String runId;
		final String label;

		Choice(File directory, String runId, String label)
		{
			this.directory = directory;
			this.runId = runId;
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
			"Open this run's TGF files (report.json, predictions.csv)");
	private List<Choice> choices = new ArrayList<Choice>();
	private TgfReport report;
	private Choice shown;
	private String message = "Run TGF in the Simulator: its model and every target's explanation appear here.";
	private boolean followLatest = true;
	private boolean updatingPicker;
	private long liveRevision = -1L;
	private int request;
	private String current = OVERVIEW;
	private String focus;

	TemporalGraphFoundationPanel(ControllerAgentGui controller)
	{
		super(new BorderLayout());
		this.controller = controller;
		setOpaque(false);
		deck.setOpaque(false);
		pages.put(OVERVIEW, new TgfOverviewPage(this));
		pages.put(TARGETS, new TgfTargetsPage(this));
		pages.put(MODEL, new TgfModelPage(this));
		pages.put(COMMUNITIES, new TgfCommunitiesPage(this));
		pages.put(METHODS, new TgfMethodsPage(this));
		for (java.util.Map.Entry<String,Page> page : pages.entrySet())
		{
			deck.add(page.getValue(), page.getKey());
		}
		navigation = new StudioShell.Navigation("TGF Studio", "Temporal Graph Foundation", NodeLabUi.Glyph.SPARK,
				NebulaDesign.ACCENT, NebulaDesign.PURPLE,
				new String[] {OVERVIEW, TARGETS, MODEL, COMMUNITIES, METHODS},
				new String[] {"Overview", "Targets", "Model", "Communities", "Methods"},
				new NodeLabUi.Glyph[] {NodeLabUi.Glyph.HOME, NodeLabUi.Glyph.TARGET, NodeLabUi.Glyph.LAYERS,
						NodeLabUi.Glyph.CLUSTER, NodeLabUi.Glyph.FLASK},
				new StudioShell.PageListener()
				{
					public void pageChosen(String key)
					{
						show(key);
					}
				});
		picker.setPreferredSize(new Dimension(360, 34));
		picker.setToolTipText("The TGF run shown (newest first)");
		picker.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent event)
			{
				if (updatingPicker)
				{
					return;
				}
				Choice choice = (Choice)picker.getSelectedItem();
				if (choice != null)
				{
					followLatest = !choices.isEmpty() && choice == choices.get(0);
					open(choice, true);
				}
			}
		});
		folder.setEnabled(false);
		folder.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent event)
			{
				openFolder(report == null || report.directory == null ? null
						: new File(report.directory, TgfReport.FOLDER));
			}
		});
		NodeLabUi.Button refresh = NodeLabUi.iconButton(NodeLabUi.Glyph.REFRESH, "Re-read the run records");
		refresh.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent event)
			{
				refreshChoices(true);
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
			public void runRecorded(SchedulingRunContext context, RunMetrics metrics, File directory)
			{
				SwingUtilities.invokeLater(new Runnable()
				{
					public void run()
					{
						refreshChoices(false);
					}
				});
			}
		});
		refreshChoices(false);
	}

	ControllerAgentGui controller()
	{
		return controller;
	}

	/** Called when TGF publishes a result, when the tab is shown and when a run is reset. */
	void refreshFromRegistry()
	{
		if (!SwingUtilities.isEventDispatchThread())
		{
			SwingUtilities.invokeLater(new Runnable()
			{
				public void run()
				{
					refreshFromRegistry();
				}
			});
			return;
		}
		long revision = TemporalGraphFoundationRegistry.revision();
		if (revision != liveRevision)
		{
			liveRevision = revision;
			if (TemporalGraphFoundationRegistry.latest() != null)
			{
				//A new live run is the newest one.
				followLatest = true;
			}
			refreshChoices(false);
		}
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

	TgfReport report()
	{
		return report;
	}

	/** Opens the Targets page on one target (from a list on another page). */
	void inspect(String user)
	{
		focus = user;
		show(TARGETS);
		((TgfTargetsPage)pages.get(TARGETS)).focus(user);
	}

	/** The target last asked to be inspected, kept across reloads of the same run. */
	String focusedTarget()
	{
		return focus;
	}

	/** Rebuilds the picker: the live run (until its record exists) and every recorded TGF run. */
	private void refreshChoices(boolean reload)
	{
		ArrayList<Choice> next = new ArrayList<Choice>();
		java.util.HashSet<String> recorded = new java.util.HashSet<String>();
		for (RunRecordReader.Entry entry : RunRecordReader.index())
		{
			if (!entry.exists() || !new File(new File(entry.directory, TgfReport.FOLDER), TgfReport.FILE).isFile())
			{
				continue;
			}
			recorded.add(entry.getRunId());
			next.add(new Choice(entry.directory, entry.getRunId(), label(entry)));
		}
		TemporalGraphFoundationModelSupport.Result live = TemporalGraphFoundationRegistry.latest();
		if (live != null && !recorded.contains(live.getRunId()))
		{
			next.add(0, new Choice(null, live.getRunId(), "Live run · " + live.targetCount()
					+ (live.targetCount() == 1 ? " target" : " targets") + " · "
					+ live.getDatasetName()));
		}
		boolean changed = next.size() != choices.size();
		for (int index = 0; !changed && index < next.size(); index++)
		{
			changed = !same(next.get(index), choices.get(index));
		}
		if (changed)
		{
			choices = next;
			updatingPicker = true;
			picker.removeAllItems();
			for (Choice choice : choices)
			{
				picker.addItem(choice);
			}
			updatingPicker = false;
		}
		if (choices.isEmpty())
		{
			report = null;
			shown = null;
			message = "Run TGF in the Simulator: its model and every target's explanation appear here.";
			publish();
			return;
		}
		Choice target = null;
		if (!followLatest && shown != null)
		{
			for (Choice choice : choices)
			{
				if (same(choice, shown))
				{
					target = choice;
				}
			}
		}
		if (target == null)
		{
			target = choices.get(0);
			followLatest = true;
		}
		updatingPicker = true;
		picker.setSelectedItem(target);
		updatingPicker = false;
		boolean liveChanged = target.directory == null && report != null && report.live()
				&& TemporalGraphFoundationRegistry.latest() != null
				&& !TemporalGraphFoundationRegistry.latest().getRunId().equals(report.runId);
		if (reload || shown == null || !same(target, shown) || liveChanged)
		{
			open(target, reload);
		}
	}

	private static boolean same(Choice a, Choice b)
	{
		if (a.directory == null || b.directory == null)
		{
			return a.directory == b.directory && a.runId.equals(b.runId);
		}
		return a.directory.equals(b.directory);
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
		return when + " · " + entry.get("dataset")
				+ (targets.length() == 0 ? "" : " · " + targets + (targets.equals("1") ? " target" : " targets"));
	}

	private void open(final Choice choice, boolean reload)
	{
		shown = choice;
		if (choice.directory == null)
		{
			report = TgfReport.of(TemporalGraphFoundationRegistry.latest());
			message = report == null ? "The live run has no TGF result yet." : null;
			publish();
			return;
		}
		final int ticket = ++request;
		report = null;
		message = "Loading the TGF report…";
		publish();
		Thread worker = new Thread(new Runnable()
		{
			public void run()
			{
				TgfReport loaded = null;
				String failure = null;
				try
				{
					loaded = TgfReport.load(choice.directory);
					if (loaded == null)
					{
						failure = "This run's record holds no TGF report.";
					}
				}
				catch (Exception | OutOfMemoryError problem)
				{
					failure = "The TGF report could not be read: " + problem.getMessage();
				}
				final TgfReport done = loaded;
				final String reason = failure;
				SwingUtilities.invokeLater(new Runnable()
				{
					public void run()
					{
						if (ticket != request)
						{
							return;
						}
						report = done;
						message = reason;
						publish();
					}
				});
			}
		}, "DSMP-TGF-Studio-Loader");
		worker.setDaemon(true);
		worker.start();
	}

	/** Opens a folder in the system's file browser (recorded runs only). */
	static void openFolder(File directory)
	{
		if (directory == null || !directory.isDirectory() || !java.awt.Desktop.isDesktopSupported())
		{
			return;
		}
		try
		{
			java.awt.Desktop.getDesktop().open(directory);
		}
		catch (Exception unavailable)
		{
			//Opening the folder is a convenience; the record is unchanged.
		}
	}

	private void publish()
	{
		folder.setEnabled(report != null && report.directory != null);
		if (report == null)
		{
			boolean loading = message != null && message.startsWith("Loading");
			status.set(loading ? "Loading" : "No run", loading ? NodeLabUi.Tone.INFO : NodeLabUi.Tone.NEUTRAL);
			status.setToolTipText(message);
		}
		else if (!report.supervised)
		{
			status.set("Unsupervised", NodeLabUi.Tone.WARNING);
			status.setToolTipText(report.note);
		}
		else
		{
			status.set(report.live() ? "Live" : "Recorded", report.live() ? NodeLabUi.Tone.INFO : NodeLabUi.Tone.SUCCESS);
			status.setToolTipText(report.live() ? "The run of this session (its record is written when the run ends)"
					: report.directory.getPath());
		}
		for (Page page : pages.values())
		{
			page.show(report, message);
		}
		navigation.repaint();
	}

	protected void paintComponent(Graphics graphics)
	{
		StudioShell.paintCanvas(graphics, getWidth(), getHeight());
	}
}
