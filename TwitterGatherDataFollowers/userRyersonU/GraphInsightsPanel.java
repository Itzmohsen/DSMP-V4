package TwitterGatherDataFollowers.userRyersonU;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
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
 * Graph Insights: the network each recommendation run creates.
 *
 * <pre>
 * Overview     the run's headline findings, tested against chance, and how runs compare
 * Explorer     the graph itself: follows, recommendations and adoptions, interactive
 * Communities  whether the recommendations recover the real communities, and where they flow
 * Influence    who gets the attention: exposure inequality, centralities and network roles
 * Adoption     what the simulated follows do to the network: bridging, modularity, components
 * Methods      every measure's definition, parameters, references and the exported files
 * </pre>
 *
 * Every run's record holds its graph (RunGraphRecord); this tab loads the
 * newest run by default and follows new runs as they finish, or any earlier
 * run chosen in the picker.
 */
final class GraphInsightsPanel extends JPanel
{
	private static final long serialVersionUID = 1L;

	static final String OVERVIEW = "overview";
	static final String EXPLORER = "explorer";
	static final String COMMUNITIES = "communities";
	static final String INFLUENCE = "influence";
	static final String ADOPTION = "adoption";
	static final String METHODS = "methods";

	/** A page of Graph Insights. */
	abstract static class Page extends JPanel
	{
		private static final long serialVersionUID = 1L;

		Page()
		{
			super(new BorderLayout());
			setOpaque(false);
		}

		/** Shows a run's graph; null with a message while loading or when there is none. */
		abstract void show(RunGraphRecord.Loaded loaded, String message);
	}

	/** A run in the picker. */
	static final class RunChoice
	{
		final File directory;
		final String label;
		final String runId;

		RunChoice(File directory, String label, String runId)
		{
			this.directory = directory;
			this.label = label;
			this.runId = runId;
		}

		public String toString()
		{
			return label;
		}
	}

	private final ControllerAgentGui gui;
	private final CardLayout cards = new CardLayout();
	private final JPanel deck = new JPanel(cards);
	private final LinkedHashMap<String,Page> pages = new LinkedHashMap<String,Page>();
	private final Navigation navigation = new Navigation();
	private final JComboBox<RunChoice> picker = NodeLabUi.combo(new RunChoice[0]);
	private final NodeLabUi.Chip status = new NodeLabUi.Chip("No run", NodeLabUi.Tone.NEUTRAL, true);
	private String current = OVERVIEW;
	private RunGraphRecord.Loaded loaded;
	private File loadedDirectory;
	private String message = "Run a simulation: every finished run records its graph here.";
	private boolean followLatest = true;
	private boolean updatingPicker;
	private int request;
	private List<RunChoice> choices = new ArrayList<RunChoice>();

	GraphInsightsPanel(ControllerAgentGui gui)
	{
		super(new BorderLayout());
		this.gui = gui;
		setOpaque(false);
		deck.setOpaque(false);
		pages.put(OVERVIEW, new GraphOverviewPage(this));
		pages.put(EXPLORER, new GraphExplorerPage(this));
		pages.put(COMMUNITIES, new GraphCommunitiesPage(this));
		pages.put(INFLUENCE, new GraphInfluencePage(this));
		pages.put(ADOPTION, new GraphAdoptionPage(this));
		pages.put(METHODS, new GraphMethodsPage(this));
		for (java.util.Map.Entry<String,Page> page : pages.entrySet())
		{
			deck.add(page.getValue(), page.getKey());
		}
		picker.setPreferredSize(new Dimension(360, 34));
		picker.setToolTipText("The run whose graph is shown (newest first)");
		picker.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent event)
			{
				if (updatingPicker)
				{
					return;
				}
				RunChoice choice = (RunChoice)picker.getSelectedItem();
				if (choice != null)
				{
					followLatest = !choices.isEmpty() && choice == choices.get(0);
					load(choice.directory);
				}
			}
		});
		NodeLabUi.Button refresh = new NodeLabUi.Button("", NodeLabUi.ButtonKind.GHOST, NodeLabUi.Glyph.REFRESH, true);
		refresh.setToolTipText("Re-read the run records");
		refresh.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent event)
			{
				refreshIndex(true);
			}
		});
		JPanel top = new JPanel(new BorderLayout());
		top.setOpaque(false);
		top.add(navigation, BorderLayout.CENTER);
		JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
		right.setOpaque(false);
		right.add(status);
		right.add(picker);
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
						refreshIndex(false);
					}
				});
			}
		});
		refreshIndex(false);
	}

	ControllerAgentGui gui()
	{
		return gui;
	}

	/** Called when the tab becomes visible or the simulator reports progress. */
	void refreshFromSimulator()
	{
		refreshIndex(false);
	}

	void show(String key)
	{
		if (!pages.containsKey(key))
		{
			return;
		}
		current = key;
		cards.show(deck, key);
		navigation.repaint();
	}

	String currentPage()
	{
		return current;
	}

	Page page(String key)
	{
		return pages.get(key);
	}

	RunGraphRecord.Loaded loaded()
	{
		return loaded;
	}

	/** Opens the Explorer on a node (from a chart or table of another page). */
	void inspect(int node)
	{
		show(EXPLORER);
		((GraphExplorerPage)pages.get(EXPLORER)).focus(node);
	}

	/** Selects a run of the picker by its directory (for example from the overview's run table). */
	void select(File directory)
	{
		for (RunChoice choice : choices)
		{
			if (choice.directory.equals(directory))
			{
				picker.setSelectedItem(choice);
				return;
			}
		}
	}

	/** A layout computed by the Explorer: kept for this session and shared with the other pages. */
	void layoutReady(RunGraphRecord.Loaded withLayout)
	{
		if (loaded != null && withLayout.graph == loaded.graph)
		{
			loaded = withLayout;
			RunGraphRecord.rememberLayout(withLayout, withLayout.x, withLayout.y);
			for (java.util.Map.Entry<String,Page> page : pages.entrySet())
			{
				if (!EXPLORER.equals(page.getKey()))
				{
					page.getValue().show(loaded, null);
				}
			}
		}
	}

	/** Re-reads runs-index.csv; loads the newest run when following the latest (or forced). */
	private void refreshIndex(boolean reload)
	{
		List<RunChoice> next = new ArrayList<RunChoice>();
		for (RunRecordReader.Entry entry : RunRecordReader.index())
		{
			if (!entry.exists() || !(RunGraphRecord.hasGraph(entry.directory)
					|| new File(entry.directory, "recommendations.csv").isFile()))
			{
				continue;
			}
			next.add(new RunChoice(entry.directory, label(entry), entry.getRunId()));
		}
		boolean changed = next.size() != choices.size();
		for (int index = 0; !changed && index < next.size(); index++)
		{
			changed = !next.get(index).directory.equals(choices.get(index).directory);
		}
		if (changed)
		{
			choices = next;
			updatingPicker = true;
			picker.removeAllItems();
			for (RunChoice choice : choices)
			{
				picker.addItem(choice);
			}
			updatingPicker = false;
		}
		if (choices.isEmpty())
		{
			loaded = null;
			loadedDirectory = null;
			message = "Run a simulation: every finished run records its graph here.";
			publish();
			return;
		}
		RunChoice target = null;
		if (followLatest)
		{
			target = choices.get(0);
		}
		else
		{
			for (RunChoice choice : choices)
			{
				if (choice.directory.equals(loadedDirectory))
				{
					target = choice;
				}
			}
			if (target == null)
			{
				target = choices.get(0);
				followLatest = true;
			}
		}
		updatingPicker = true;
		picker.setSelectedItem(target);
		updatingPicker = false;
		if (reload || !target.directory.equals(loadedDirectory))
		{
			load(target.directory);
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

	private void load(final File directory)
	{
		final int ticket = ++request;
		loadedDirectory = directory;
		loaded = null;
		message = "Loading the run graph…";
		publish();
		Thread worker = new Thread(new Runnable()
		{
			public void run()
			{
				RunGraphRecord.Loaded result = null;
				String failure = null;
				try
				{
					result = RunGraphRecord.load(directory);
					if (result == null)
					{
						failure = "This run has no recommendation lists to build a graph from.";
					}
				}
				catch (Exception | OutOfMemoryError problem)
				{
					failure = "The run graph could not be read: " + problem.getMessage();
				}
				final RunGraphRecord.Loaded done = result;
				final String reason = failure;
				SwingUtilities.invokeLater(new Runnable()
				{
					public void run()
					{
						if (ticket != request)
						{
							return;
						}
						loaded = done;
						message = reason;
						publish();
					}
				});
			}
		}, "DSMP-Graph-Insights-Loader");
		worker.setDaemon(true);
		worker.start();
	}

	private void publish()
	{
		if (loaded == null)
		{
			status.set(message != null && message.startsWith("Loading") ? "Loading" : "No graph",
					message != null && message.startsWith("Loading") ? NodeLabUi.Tone.INFO : NodeLabUi.Tone.NEUTRAL);
		}
		else
		{
			status.set(loaded.reconstructed ? "Rebuilt from record" : loaded.graph.size() + " nodes",
					loaded.reconstructed ? NodeLabUi.Tone.WARNING : NodeLabUi.Tone.SUCCESS);
			status.setToolTipText(loaded.reconstructed
					? "This run was recorded before run graphs existed; its graph was rebuilt from"
							+ " recommendations.csv and the dataset."
					: loaded.graph.describe());
		}
		for (Page page : pages.values())
		{
			page.show(loaded, message);
		}
		navigation.repaint();
	}

	protected void paintComponent(Graphics graphics)
	{
		Graphics2D g = (Graphics2D)graphics.create();
		g.setPaint(new GradientPaint(0, 0, NebulaDesign.CANVAS_TOP, 0, getHeight(), NebulaDesign.CANVAS_BOTTOM));
		g.fillRect(0, 0, getWidth(), getHeight());
		g.dispose();
	}

	// ------------------------------------------------------------ shared formatting

	static String fixed(double value, int decimals)
	{
		return Double.isNaN(value) || Double.isInfinite(value) ? "—"
				: String.format(Locale.US, "%." + decimals + "f", value);
	}

	static String percent(double value)
	{
		return Double.isNaN(value) ? "—" : String.format(Locale.US, "%.1f%%", 100.0 * value);
	}

	static NodeLabUi.Tone tone(String findingTone)
	{
		return "good".equals(findingTone) ? NodeLabUi.Tone.SUCCESS : "bad".equals(findingTone) ? NodeLabUi.Tone.DANGER
				: "warning".equals(findingTone) ? NodeLabUi.Tone.WARNING : NodeLabUi.Tone.ACCENT;
	}

	/** A chip with a p-value's reading: significant above or below a baseline, or not distinguishable. */
	static NodeLabUi.Chip testChip(double p, boolean above, String aboveText, String belowText)
	{
		if (Double.isNaN(p))
		{
			return new NodeLabUi.Chip("Not testable", NodeLabUi.Tone.NEUTRAL, true);
		}
		if (p < 0.05)
		{
			return new NodeLabUi.Chip((above ? aboveText : belowText) + " (" + NetworkStatistics.pText(p) + ")",
					above ? NodeLabUi.Tone.SUCCESS : NodeLabUi.Tone.DANGER, true);
		}
		return new NodeLabUi.Chip("Like chance (" + NetworkStatistics.pText(p) + ")", NodeLabUi.Tone.WARNING, true);
	}

	/** Like {@link #testChip}, for a direction that is neither good nor bad (bridging or reinforcing). */
	static NodeLabUi.Chip neutralTestChip(double p, boolean above, String aboveText, String belowText)
	{
		if (Double.isNaN(p))
		{
			return new NodeLabUi.Chip("Not testable", NodeLabUi.Tone.NEUTRAL, true);
		}
		if (p < 0.05)
		{
			return new NodeLabUi.Chip((above ? aboveText : belowText) + " (" + NetworkStatistics.pText(p) + ")",
					NodeLabUi.Tone.INFO, true);
		}
		return new NodeLabUi.Chip("Like chance (" + NetworkStatistics.pText(p) + ")", NodeLabUi.Tone.WARNING, true);
	}

	/**
	 * A vertical stack that keeps {@code gap} between the items added to it
	 * later ({@link NodeLabUi#stack} spaces only the children it is given).
	 */
	static JPanel column(final int gap)
	{
		JPanel panel = new JPanel()
		{
			private static final long serialVersionUID = 1L;

			protected void addImpl(java.awt.Component component, Object constraints, int index)
			{
				if (component instanceof javax.swing.JComponent)
				{
					((javax.swing.JComponent)component).setAlignmentX(LEFT_ALIGNMENT);
				}
				if (getComponentCount() > 0 && gap > 0)
				{
					super.addImpl(javax.swing.Box.createVerticalStrut(gap), null, -1);
				}
				super.addImpl(component, constraints, index);
			}
		};
		panel.setOpaque(false);
		panel.setLayout(new javax.swing.BoxLayout(panel, javax.swing.BoxLayout.Y_AXIS));
		return panel;
	}

	/** The empty or loading state every page shows without a graph. */
	static JPanel waiting(String message)
	{
		NodeLabUi.Card card = new NodeLabUi.Card(null, null);
		boolean loading = message != null && message.startsWith("Loading");
		card.content(NodeLabUi.emptyState(loading ? NodeLabUi.Glyph.CLOCK : NodeLabUi.Glyph.NETWORK,
				loading ? "Loading the run graph" : "No run graph yet",
				message == null ? "Run a simulation: every finished run records its graph here." : message, null));
		NodeLabUi.Page page = new NodeLabUi.Page(1100);
		page.section(card);
		JPanel holder = new JPanel(new BorderLayout());
		holder.setOpaque(false);
		holder.add(NodeLabUi.scroll(page), BorderLayout.CENTER);
		return holder;
	}

	// ------------------------------------------------------------ navigation

	private final class Navigation extends JPanel
	{
		private static final long serialVersionUID = 1L;
		private final String[] keys = {OVERVIEW, EXPLORER, COMMUNITIES, INFLUENCE, ADOPTION, METHODS};
		private final String[] labels = {"Overview", "Explorer", "Communities", "Influence", "Adoption", "Methods"};
		private final NodeLabUi.Glyph[] glyphs = {
			NodeLabUi.Glyph.HOME, NodeLabUi.Glyph.NETWORK, NodeLabUi.Glyph.CLUSTER, NodeLabUi.Glyph.SPARK,
			NodeLabUi.Glyph.ARROW_RIGHT, NodeLabUi.Glyph.FLASK
		};
		private final List<int[]> bounds = new ArrayList<int[]>();
		private int hover = -1;

		Navigation()
		{
			setOpaque(false);
			setPreferredSize(new Dimension(760, 70));
			MouseAdapter mouse = new MouseAdapter()
			{
				public void mouseMoved(MouseEvent event)
				{
					int index = indexAt(event.getX(), event.getY());
					if (index != hover)
					{
						hover = index;
						setCursor(Cursor.getPredefinedCursor(index >= 0 ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
						repaint();
					}
				}

				public void mouseExited(MouseEvent event)
				{
					hover = -1;
					repaint();
				}

				public void mouseClicked(MouseEvent event)
				{
					int index = indexAt(event.getX(), event.getY());
					if (index >= 0)
					{
						GraphInsightsPanel.this.show(keys[index]);
					}
				}
			};
			addMouseListener(mouse);
			addMouseMotionListener(mouse);
		}

		private int indexAt(int x, int y)
		{
			for (int index = 0; index < bounds.size(); index++)
			{
				int[] box = bounds.get(index);
				if (x >= box[0] && x <= box[0] + box[2] && y >= box[1] && y <= box[1] + box[3])
				{
					return index;
				}
			}
			return -1;
		}

		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D)graphics.create();
			NodeLabUi.prepare(g);
			int barHeight = 46;
			int top = 14;
			int left = 24;
			g.setPaint(new GradientPaint(left, top, NebulaDesign.PURPLE, left + 36, top + 36, NebulaDesign.ACCENT));
			g.fill(new RoundRectangle2D.Float(left, top + 5, 36, 36, 12, 12));
			g.setColor(Color.WHITE);
			Graphics2D icon = (Graphics2D)g.create();
			icon.translate(left + 8, top + 13);
			NodeLabUi.paintGlyph(icon, NodeLabUi.Glyph.NETWORK, 20);
			icon.dispose();
			g.setFont(NebulaDesign.fontSemiBold(15f));
			g.setColor(NebulaDesign.TEXT_PRIMARY);
			g.drawString("Graph Insights", left + 46, top + 22);
			g.setFont(NebulaDesign.fontMedium(11f));
			g.setColor(NebulaDesign.TEXT_TERTIARY);
			g.drawString("The network each run creates", left + 46, top + 38);
			int trackX = left + 236;
			g.setFont(NebulaDesign.fontMedium(12.5f));
			FontMetrics metrics = g.getFontMetrics();
			int[] widths = new int[keys.length];
			int total = 0;
			for (int index = 0; index < keys.length; index++)
			{
				widths[index] = metrics.stringWidth(labels[index]) + 44;
				total += widths[index];
			}
			boolean compact = total + 8 > getWidth() - trackX - 12;
			if (compact)
			{
				total = 0;
				for (int index = 0; index < keys.length; index++)
				{
					widths[index] = metrics.stringWidth(labels[index]) + 24;
					total += widths[index];
				}
			}
			g.setColor(NebulaDesign.INSET);
			g.fill(new RoundRectangle2D.Float(trackX, top, total + 8, barHeight, 16, 16));
			bounds.clear();
			int x = trackX + 4;
			for (int index = 0; index < keys.length; index++)
			{
				boolean selected = keys[index].equals(current);
				int width = widths[index];
				bounds.add(new int[] {x, top + 4, width, barHeight - 8});
				if (selected)
				{
					g.setColor(NodeLabUi.alpha(Color.BLACK, NodeLabUi.night() ? 70 : 16));
					g.fill(new RoundRectangle2D.Float(x, top + 5, width, barHeight - 8, 12, 12));
					g.setColor(NodeLabUi.night() ? NebulaDesign.NIGHT_SURFACE_RAISED : NebulaDesign.DAY_SURFACE);
					g.fill(new RoundRectangle2D.Float(x, top + 4, width, barHeight - 8, 12, 12));
				}
				else if (index == hover)
				{
					g.setColor(NodeLabUi.alpha(NebulaDesign.ACCENT, 20));
					g.fill(new RoundRectangle2D.Float(x, top + 4, width, barHeight - 8, 12, 12));
				}
				int cursor = x + 12;
				int centre = top + barHeight / 2;
				if (!compact)
				{
					Graphics2D glyph = (Graphics2D)g.create();
					glyph.setColor(selected ? NebulaDesign.ACCENT : NebulaDesign.TEXT_TERTIARY);
					glyph.translate(cursor, centre - 7);
					NodeLabUi.paintGlyph(glyph, glyphs[index], 14);
					glyph.dispose();
					cursor += 21;
				}
				g.setColor(selected ? NebulaDesign.TEXT_PRIMARY : NebulaDesign.TEXT_SECONDARY);
				g.setFont(selected ? NebulaDesign.fontSemiBold(12.5f) : NebulaDesign.fontMedium(12.5f));
				g.drawString(labels[index], cursor, centre + 5);
				x += width;
			}
			g.dispose();
		}
	}
}
