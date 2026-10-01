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
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;

/**
 * The Node Lab: everything about the nodes of the distributed recommender in
 * one place, as a guided flow.
 *
 * <pre>
 * Overview   where you are and what the next run will do
 * 1 Cluster  design the nodes, their links and their faults
 * 2 Plan     choose the execution mode, the scheduling policy and a scenario
 * 3 Live     watch and steer the running system
 * 4 Results  every recorded run: timeline, measures, model check, what-ifs
 * What-If    compare policies instantly in the simulator
 * Studies    design and run replicated experiments with statistics
 * </pre>
 */
final class NodeLabPanel extends JPanel
{
	private static final long serialVersionUID = 1L;

	/** A page of the Node Lab. */
	abstract static class Page extends JPanel
	{
		private static final long serialVersionUID = 1L;

		Page()
		{
			super(new BorderLayout());
			setOpaque(false);
		}

		/** Re-reads the simulator's state (called when the page is shown and periodically). */
		abstract void refresh();

		/** How often the page wants a refresh while shown, in ms (0: only when shown). */
		int refreshIntervalMs()
		{
			return 0;
		}
	}

	static final String OVERVIEW = "overview";
	static final String CLUSTER = "cluster";
	static final String PLAN = "plan";
	static final String LIVE = "live";
	static final String RESULTS = "results";
	static final String WHAT_IF = "whatif";
	static final String STUDIES = "studies";

	private final ControllerAgentGui owner;
	private final CardLayout cards = new CardLayout();
	private final JPanel deck = new JPanel(cards);
	private final LinkedHashMap<String,Page> pages = new LinkedHashMap<String,Page>();
	private final NavigationBar navigation;
	private String current = OVERVIEW;
	private final Timer timer;
	private long lastRefresh;
	private final NodeLabRunLauncher launcher;
	private List<RunRecordReader.Entry> indexCache = new ArrayList<RunRecordReader.Entry>();
	private long indexStamp = -1L;
	private long indexLength = -1L;

	NodeLabPanel(ControllerAgentGui owner)
	{
		super(new BorderLayout());
		this.owner = owner;
		this.launcher = new NodeLabRunLauncher(owner);
		setOpaque(false);
		deck.setOpaque(false);
		pages.put(OVERVIEW, new NodeLabOverviewPage(this));
		pages.put(CLUSTER, new NodeLabClusterPage(this));
		pages.put(PLAN, new NodeLabPlanPage(this));
		pages.put(LIVE, new NodeLabLivePage(this));
		pages.put(RESULTS, new NodeLabResultsPage(this));
		pages.put(WHAT_IF, new NodeLabWhatIfPage(this));
		pages.put(STUDIES, new NodeLabStudiesPage(this));
		for (java.util.Map.Entry<String,Page> page : pages.entrySet())
		{
			deck.add(page.getValue(), page.getKey());
		}
		navigation = new NavigationBar();
		add(navigation, BorderLayout.NORTH);
		add(deck, BorderLayout.CENTER);
		timer = new Timer(250, new ActionListener()
		{
			public void actionPerformed(ActionEvent event)
			{
				tick();
			}
		});
		timer.setRepeats(true);
	}

	ControllerAgentGui getOwner()
	{
		return owner;
	}

	/** Shows a page (one of the constants above). */
	void show(String key)
	{
		if (!pages.containsKey(key))
		{
			return;
		}
		current = key;
		cards.show(deck, key);
		navigation.refreshStatus();
		navigation.repaint();
		Page page = pages.get(key);
		page.refresh();
		lastRefresh = System.currentTimeMillis();
	}

	String currentPage()
	{
		return current;
	}

	Page page(String key)
	{
		return pages.get(key);
	}

	/** Called when the Node Lab tab becomes visible. */
	void refreshFromSimulator()
	{
		pages.get(current).refresh();
		navigation.refreshStatus();
		lastRefresh = System.currentTimeMillis();
	}

	public void addNotify()
	{
		super.addNotify();
		timer.start();
	}

	public void removeNotify()
	{
		timer.stop();
		super.removeNotify();
	}

	private void tick()
	{
		if (!isShowing())
		{
			return;
		}
		navigation.refreshStatus();
		Page page = pages.get(current);
		int interval = page.refreshIntervalMs();
		if (interval > 0 && System.currentTimeMillis() - lastRefresh >= interval)
		{
			lastRefresh = System.currentTimeMillis();
			page.refresh();
		}
	}

	/** Every page re-reads the simulator (after a cluster or plan change elsewhere). */
	void refreshAll()
	{
		for (Page page : pages.values())
		{
			page.refresh();
		}
		navigation.refreshStatus();
	}

	/** A short description of the active or latest run, for the status pill. */
	static String runStatus()
	{
		SchedulingRunContext context = SchedulingRuntime.latest();
		if (context == null)
		{
			return "No run yet";
		}
		if (!context.isFinished() && !context.isClosed())
		{
			Double start = context.recorder().milestone("run-start");
			return start == null ? "Initializing run" : "Running · " + NodeLabUi.duration(context.now() - start.doubleValue());
		}
		String status = context.recorder().getStatus();
		return status.startsWith("completed") ? "Last run completed" : "Last run " + status.split(":")[0];
	}

	static boolean isRunning()
	{
		SchedulingRunContext context = SchedulingRuntime.latest();
		return context != null && !context.isFinished() && !context.isClosed();
	}

	// ------------------------------------------------------------ navigation

	private final class NavigationBar extends JPanel
	{
		private static final long serialVersionUID = 1L;
		private final String[] keys = {OVERVIEW, CLUSTER, PLAN, LIVE, RESULTS, WHAT_IF, STUDIES};
		private final String[] labels = {"Overview", "Cluster", "Plan", "Live", "Results", "What-If", "Studies"};
		private final String[] steps = {null, "1", "2", "3", "4", null, null};
		private final NodeLabUi.Glyph[] glyphs = {
			NodeLabUi.Glyph.HOME, NodeLabUi.Glyph.CLUSTER, NodeLabUi.Glyph.SLIDERS, NodeLabUi.Glyph.BOLT,
			NodeLabUi.Glyph.CHART, NodeLabUi.Glyph.SPARK, NodeLabUi.Glyph.FLASK
		};
		private final List<int[]> bounds = new ArrayList<int[]>();
		private int hover = -1;
		private String status = "";
		private boolean running;

		NavigationBar()
		{
			setOpaque(false);
			setBorder(new EmptyBorder(14, 24, 6, 24));
			setPreferredSize(new Dimension(800, 70));
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
						NodeLabPanel.this.show(keys[index]);
					}
				}
			};
			addMouseListener(mouse);
			addMouseMotionListener(mouse);
		}

		void refreshStatus()
		{
			String next = runStatus();
			boolean nowRunning = isRunning();
			if (!next.equals(status) || nowRunning != running || nowRunning)
			{
				status = next;
				running = nowRunning;
				repaint();
			}
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
			int w = getWidth();
			int barHeight = 46;
			int top = 14;
			int left = 24;
			//Brand block.
			g.setPaint(new GradientPaint(left, top, NebulaDesign.ACCENT, left + 36, top + 36, NebulaDesign.PURPLE));
			g.fill(new RoundRectangle2D.Float(left, top + 5, 36, 36, 12, 12));
			g.setColor(java.awt.Color.WHITE);
			Graphics2D icon = (Graphics2D)g.create();
			icon.translate(left + 8, top + 13);
			NodeLabUi.paintGlyph(icon, NodeLabUi.Glyph.CLUSTER, 20);
			icon.dispose();
			g.setFont(NebulaDesign.fontSemiBold(15f));
			g.setColor(NebulaDesign.TEXT_PRIMARY);
			g.drawString("Node Lab", left + 46, top + 22);
			g.setFont(NebulaDesign.fontMedium(11f));
			g.setColor(NebulaDesign.TEXT_TERTIARY);
			g.drawString("Nodes · scheduling · faults", left + 46, top + 38);
			//Navigation track.
			int trackX = left + 220;
			g.setFont(NebulaDesign.fontMedium(12.5f));
			FontMetrics metrics = g.getFontMetrics();
			int[] widths = new int[keys.length];
			int total = 0;
			for (int index = 0; index < keys.length; index++)
			{
				widths[index] = metrics.stringWidth(labels[index]) + 44 + (steps[index] != null ? 20 : 0);
				total += widths[index];
			}
			int statusWidth = 220;
			int available = w - trackX - statusWidth - left - 12;
			boolean compact = total + 8 > available;
			if (compact)
			{
				total = 0;
				for (int index = 0; index < keys.length; index++)
				{
					widths[index] = metrics.stringWidth(labels[index]) + 26;
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
					g.setColor(NodeLabUi.alpha(java.awt.Color.BLACK, NodeLabUi.night() ? 70 : 16));
					g.fill(new RoundRectangle2D.Float(x, top + 5, width, barHeight - 8, 12, 12));
					g.setColor(NodeLabUi.night() ? NebulaDesign.NIGHT_SURFACE_RAISED : NebulaDesign.DAY_SURFACE);
					g.fill(new RoundRectangle2D.Float(x, top + 4, width, barHeight - 8, 12, 12));
				}
				else if (index == hover)
				{
					g.setColor(NodeLabUi.alpha(NebulaDesign.ACCENT, 20));
					g.fill(new RoundRectangle2D.Float(x, top + 4, width, barHeight - 8, 12, 12));
				}
				int cursor = x + 13;
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
				if (steps[index] != null && !compact)
				{
					int badgeX = cursor + g.getFontMetrics().stringWidth(labels[index]) + 7;
					g.setColor(selected ? NebulaDesign.ACCENT : NodeLabUi.alpha(NebulaDesign.TEXT_TERTIARY, 90));
					g.fill(new Ellipse2D.Float(badgeX, centre - 8, 16, 16));
					g.setColor(selected || NodeLabUi.night() ? java.awt.Color.WHITE : NebulaDesign.SURFACE);
					if (!selected && NodeLabUi.night())
					{
						g.setColor(NebulaDesign.TEXT_PRIMARY);
					}
					g.setFont(NebulaDesign.fontSemiBold(10f));
					FontMetrics small = g.getFontMetrics();
					g.drawString(steps[index], badgeX + 8 - small.stringWidth(steps[index]) / 2f, centre + 4);
				}
				x += width;
			}
			//Run status pill.
			String text = status;
			g.setFont(NebulaDesign.fontSemiBold(11.5f));
			metrics = g.getFontMetrics();
			int pillWidth = metrics.stringWidth(text) + 34;
			int pillX = w - left - pillWidth;
			int pillY = top + 11;
			Color tone = running ? NebulaDesign.SUCCESS : NebulaDesign.TEXT_TERTIARY;
			g.setColor(running ? NebulaDesign.SUCCESS_SOFT : NebulaDesign.INSET);
			g.fill(new RoundRectangle2D.Float(pillX, pillY, pillWidth, 26, 26, 26));
			g.setColor(tone);
			float pulse = running ? (float)(0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 180.0)) : 1f;
			g.setColor(NodeLabUi.alpha(tone, running ? (int)(90 + 165 * pulse) : 255));
			g.fill(new Ellipse2D.Float(pillX + 11, pillY + 9, 8, 8));
			g.setColor(running ? NebulaDesign.SUCCESS : NebulaDesign.TEXT_SECONDARY);
			g.drawString(text, pillX + 25, pillY + 17);
			g.dispose();
		}
	}

	protected void paintComponent(Graphics graphics)
	{
		Graphics2D g = (Graphics2D)graphics.create();
		g.setPaint(new GradientPaint(0, 0, NebulaDesign.CANVAS_TOP, 0, getHeight(), NebulaDesign.CANVAS_BOTTOM));
		g.fillRect(0, 0, getWidth(), getHeight());
		g.dispose();
	}

	// ------------------------------------------------------------ shared

	/** The simulator's configured heap, for default memory budgets. */
	int heapMb()
	{
		return owner == null ? RuntimeResourceSettings.currentMaxHeapMb() : owner.currentHeapSetting();
	}

	/** Applies a cluster to the simulator (node count, profiles, settings) and refreshes the lab. */
	void applyCluster(List<NodeCapacityProfile> profiles, ClusterSettings settings)
	{
		if (owner != null)
		{
			owner.applyClusterFromNodeLab(profiles, settings);
		}
		else
		{
			NodeCapacityRegistry.setProfiles(profiles, heapMb());
			NodeCapacityRegistry.setClusterSettings(settings);
		}
		navigation.refreshStatus();
	}

	/** Called after a page changed the plan of the next run. */
	void planChanged()
	{
		if (owner != null)
		{
			owner.nodeLabPlanChanged();
		}
		navigation.refreshStatus();
	}

	/** The current plan's summary line for headers. */
	static String planSummary()
	{
		RecommendationSchedulerPlan plan = RecommendationSchedulerRegistry.snapshot();
		return plan.getMode().getLabel() + " · " + policyLabel(plan) + (plan.getScenario().isEmpty() ? ""
				: " · scenario “" + plan.getScenario().getName() + "”");
	}

	/** The policy the plan's runs use (the hardware mode always places as V3 did). */
	static String policyLabel(RecommendationSchedulerPlan plan)
	{
		return plan.isActive() ? plan.getPolicy().getLabel()
				: SchedulerLabEngine.SchedulingPolicy.ORIGINAL_DSMP.getLabel();
	}

	/** An ISO timestamp as "Sep 30, 19:27". */
	static String shortTime(String iso)
	{
		if (iso == null || iso.length() < 16)
		{
			return iso == null ? "" : iso;
		}
		try
		{
			java.time.LocalDateTime time = java.time.LocalDateTime.parse(iso.substring(0, 19));
			return time.format(java.time.format.DateTimeFormatter.ofPattern("MMM d, HH:mm", Locale.US));
		}
		catch (RuntimeException unparsable)
		{
			return iso;
		}
	}

	/** The one-click run launcher shared by the pages. */
	NodeLabRunLauncher launcher()
	{
		return launcher;
	}

	/** The recorded runs, newest first (re-read only when the index file changes). */
	List<RunRecordReader.Entry> runIndex()
	{
		java.io.File file = new java.io.File(RunRecordWriter.runsDirectory(), "runs-index.csv");
		long stamp = file.lastModified();
		long length = file.length();
		if (stamp != indexStamp || length != indexLength)
		{
			indexStamp = stamp;
			indexLength = length;
			indexCache = RunRecordReader.index();
		}
		return indexCache;
	}

	/** Opens the What-If page on a recorded run's work. */
	void whatIf(RunRecordReader.Record record)
	{
		Page page = pages.get(WHAT_IF);
		if (page instanceof NodeLabWhatIfPage)
		{
			((NodeLabWhatIfPage)page).useRecord(record);
		}
		show(WHAT_IF);
	}

	/** Opens the Results page on one run. */
	void openRun(String runId)
	{
		Page results = pages.get(RESULTS);
		if (results instanceof NodeLabResultsPage)
		{
			((NodeLabResultsPage)results).select(runId);
		}
		show(RESULTS);
	}

	static String clusterSummary()
	{
		List<NodeCapacityProfile> profiles = NodeCapacityRegistry.snapshot();
		double throughput = 0.0;
		for (NodeCapacityProfile profile : profiles)
		{
			throughput += profile.effectiveSpeed() * profile.getMaxConcurrentJobs();
		}
		return String.format(Locale.US, "%d node%s · throughput %.2f", profiles.size(),
				profiles.size() == 1 ? "" : "s", throughput);
	}

	static JComponent titled(String title, String text)
	{
		return NodeLabUi.stack(3, NodeLabUi.strong(title), NodeLabUi.caption(text));
	}

	/** A row of items that wraps onto further lines as needed. */
	static JPanel flow(int gap)
	{
		JPanel panel = new JPanel(new NodeLabUi.WrapLayout(gap, gap));
		panel.setOpaque(false);
		return panel;
	}
}
