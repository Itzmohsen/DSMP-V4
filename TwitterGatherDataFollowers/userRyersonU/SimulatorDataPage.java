package TwitterGatherDataFollowers.userRyersonU;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JComponent;
import javax.swing.JPanel;

/**
 * Step 1: where the tweets come from, which of them a run uses, how their
 * text is processed, and the users they belong to.
 */
final class SimulatorDataPage extends SimulatorPanel.Page
{
	private static final long serialVersionUID = 1L;
	private static final int[] SOURCES = {
		ControllerAgentGui.FROM_TEXT, ControllerAgentGui.FROM_DB,
		ControllerAgentGui.FROM_GENERATION, ControllerAgentGui.FROM_ARTIFICIAL
	};

	private final ControllerAgentGui gui;
	private final NodeLabUi.Page page = new NodeLabUi.Page(1240);
	private final NodeLabUi.ChoiceTile[] sourceTiles = new NodeLabUi.ChoiceTile[SOURCES.length];
	private final NodeLabUi.Text sourceTitle = NodeLabUi.strong(" ");
	private final NodeLabUi.Prose sourceDetail = NodeLabUi.note(" ");
	private final NodeLabUi.Field label = new NodeLabUi.Field("", 18, "Name of this experiment");
	private final NodeLabUi.Field from = new NodeLabUi.Field("", 10, "yyyy-mm-dd");
	private final NodeLabUi.Field to = new NodeLabUi.Field("", 10, "yyyy-mm-dd");
	private final NodeLabUi.Field limit = new NodeLabUi.Field("", 8, "0 = all");
	private final NodeLabUi.Switch retweets = new NodeLabUi.Switch(true);
	private final NodeLabUi.Switch stopWords = new NodeLabUi.Switch(true);
	private final NodeLabUi.Switch tweetDelay = new NodeLabUi.Switch(true);
	private final NodeLabUi.Button load;
	private final NodeLabUi.Metric users = new NodeLabUi.Metric("Users", "—", "loaded from the dataset", NebulaDesign.ACCENT);
	private final NodeLabUi.Metric communities = new NodeLabUi.Metric("Communities", "—",
			"followee accounts (the ground truth)", NebulaDesign.PURPLE);
	private final NodeLabUi.Metric largest = new NodeLabUi.Metric("Largest community", "—", " ", NebulaDesign.SUCCESS);
	private final CommunityBars bars = new CommunityBars();
	private int shownUsers = -1;

	SimulatorDataPage(SimulatorPanel simulator)
	{
		this.gui = simulator.gui();
		NodeLabUi.Card source = new NodeLabUi.Card("Dataset", "Where the tweets come from.");
		JPanel tiles = NodeLabUi.grid(220, 4, 12);
		String[][] text = {
			{"Text file", "A tab-separated file: followee, tweet id, date, user id, user, text."},
			{"Database", "The configured MySQL store (table usertweet)."},
			{"Generated", "A synthetic network of followees and their followers."},
			{"Artificial", "Artificial tweets drawn from each user's word statistics."}};
		NodeLabUi.Glyph[] glyphs = {NodeLabUi.Glyph.SCRIPT, NodeLabUi.Glyph.LAYERS, NodeLabUi.Glyph.SPARK,
				NodeLabUi.Glyph.FLASK};
		for (int index = 0; index < SOURCES.length; index++)
		{
			final int mode = SOURCES[index];
			sourceTiles[index] = new NodeLabUi.ChoiceTile(glyphs[index], NodeLabUi.Tone.ACCENT, text[index][0],
					text[index][1], null, true);
			sourceTiles[index].addActionListener(new ActionListener()
			{
				public void actionPerformed(ActionEvent event)
				{
					gui.simulatorChooseSource(mode);
					refresh();
				}
			});
			tiles.add(sourceTiles[index]);
		}
		NodeLabUi.Button choose = new NodeLabUi.Button("Choose file", NodeLabUi.ButtonKind.SECONDARY,
				NodeLabUi.Glyph.FOLDER, true);
		choose.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent event)
			{
				gui.simulatorChooseSource(ControllerAgentGui.FROM_TEXT);
				refresh();
			}
		});
		source.action(choose);
		source.content(NodeLabUi.stack(14, tiles, NodeLabUi.stack(2, sourceTitle, sourceDetail),
				NodeLabUi.row(0, NodeLabUi.fixedWidth(NodeLabUi.labeled("Experiment label", label), 320))));
		SimulatorPanel.bind(label, gui.simulatorLabelField());

		NodeLabUi.Card window = new NodeLabUi.Card("Tweets in the run",
				"The date window (both days included) and how many of the newest tweets to keep.");
		window.action(NodeLabUi.help("Which tweets a run uses",
				"Only tweets dated inside the window are read; tweets without a readable date are kept."
						+ " A tweet limit keeps the newest N tweets of the window by date (tweet id breaks ties);"
						+ " 0 keeps them all. Every algorithm then sees the same tweets."));
		JPanel windowRow = NodeLabUi.columns(160, 3, 14);
		windowRow.add(NodeLabUi.labeled("From", from));
		windowRow.add(NodeLabUi.labeled("To", to));
		windowRow.add(NodeLabUi.labeled("Newest tweets (0 = all)", limit));
		window.content(windowRow);
		SimulatorPanel.bind(from, gui.simulatorBeginDateField());
		SimulatorPanel.bind(to, gui.simulatorEndDateField());
		SimulatorPanel.bind(limit, gui.simulatorTweetLimitField());

		NodeLabUi.Card processing = new NodeLabUi.Card("Text processing",
				"Every algorithm reads the same normalised words.");
		processing.action(NodeLabUi.help("How tweets become words",
				"Links and photo captions are removed; letters of every script are kept (accents included);"
						+ " digits, symbols and punctuation, # and @ among them, separate words; text is"
						+ " lower-cased. A tweet needs at least three words left to count. Doc2Vec and the"
						+ " temporal graph model read the run's normalised corpus, so they see exactly the words"
						+ " the other algorithms see."));
		JPanel switches = NodeLabUi.columns(260, 3, 14);
		switches.add(switchRow(retweets, "Drop retweets", "Tweets that quote another (RT @...) are left out."));
		switches.add(switchRow(stopWords, "Remove stop words", "Common words (the, and, ...) are dropped."));
		switches.add(switchRow(tweetDelay, "Simulate tweet delay",
				"Users post with the original DSMP timing; off delivers all tweets at once."));
		processing.content(switches);
		SimulatorPanel.bind(retweets, gui.simulatorRemoveRetweets());
		SimulatorPanel.bind(stopWords, gui.simulatorRemoveStopWords());
		SimulatorPanel.bind(tweetDelay, gui.simulatorTweetDelay());

		NodeLabUi.Card people = new NodeLabUi.Card("Users",
				"Reads the dataset with these settings and lists its users and communities.");
		load = SimulatorPanel.mirror("Load users", NodeLabUi.ButtonKind.PRIMARY, NodeLabUi.Glyph.REFRESH,
				gui.simulatorLoadUsersButton());
		people.action(load);
		JPanel tilesRow = NodeLabUi.grid(180, 3, 12);
		tilesRow.add(users);
		tilesRow.add(communities);
		tilesRow.add(largest);
		people.content(NodeLabUi.stack(14, tilesRow, bars));

		page.section(source).section(window).section(processing).section(people);
		add(NodeLabUi.scroll(page), BorderLayout.CENTER);
		refresh();
	}

	private static JComponent switchRow(NodeLabUi.Switch toggle, String title, String text)
	{
		JPanel row = new JPanel(new BorderLayout(12, 0));
		row.setOpaque(false);
		JPanel holder = new JPanel(new BorderLayout());
		holder.setOpaque(false);
		holder.add(toggle, BorderLayout.NORTH);
		row.add(holder, BorderLayout.WEST);
		row.add(NodeLabUi.stack(2, NodeLabUi.strong(title), NodeLabUi.note(text)), BorderLayout.CENTER);
		return row;
	}

	void refresh()
	{
		int mode = gui.simulatorSourceMode();
		for (int index = 0; index < SOURCES.length; index++)
		{
			boolean selected = SOURCES[index] == mode;
			if (sourceTiles[index].isSelected() != selected)
			{
				sourceTiles[index].setSelected(selected);
			}
		}
		sourceTitle.setText(gui.simulatorSourceTitle());
		String path = gui.simulatorDatasetPath();
		sourceDetail.setText(path == null || path.isEmpty() ? gui.simulatorSourceDetail() : path);
		SimulatorPanel.read(label, gui.simulatorLabelField());
		SimulatorPanel.read(from, gui.simulatorBeginDateField());
		SimulatorPanel.read(to, gui.simulatorEndDateField());
		SimulatorPanel.read(limit, gui.simulatorTweetLimitField());
		SimulatorPanel.read(retweets, gui.simulatorRemoveRetweets());
		SimulatorPanel.read(stopWords, gui.simulatorRemoveStopWords());
		SimulatorPanel.read(tweetDelay, gui.simulatorTweetDelay());
		SimulatorPanel.read(load, gui.simulatorLoadUsersButton());
		List<String> loaded = gui.loadedUsers();
		if (loaded.size() != shownUsers)
		{
			shownUsers = loaded.size();
			Map<String,String> followees = gui.simulatorFollowees();
			final HashMap<String,Integer> counts = new HashMap<String,Integer>();
			int labelled = 0;
			for (String user : loaded)
			{
				String followee = followees.get(user);
				if (followee != null && followee.length() > 0)
				{
					Integer old = counts.get(followee);
					counts.put(followee, Integer.valueOf(old == null ? 1 : old.intValue() + 1));
					labelled++;
				}
			}
			ArrayList<String> order = new ArrayList<String>(counts.keySet());
			Collections.sort(order, new Comparator<String>()
			{
				public int compare(String first, String second)
				{
					int byCount = counts.get(second).compareTo(counts.get(first));
					return byCount != 0 ? byCount : first.compareTo(second);
				}
			});
			users.set(loaded.isEmpty() ? "—" : Integer.toString(loaded.size()),
					loaded.isEmpty() ? "Load users to see them" : labelled + " with a known followee");
			communities.set(counts.isEmpty() ? "—" : Integer.toString(counts.size()), null);
			if (order.isEmpty())
			{
				largest.set("—", " ");
			}
			else
			{
				int top = counts.get(order.get(0)).intValue();
				largest.set(NodeLabUi.percent(top / (double)Math.max(1, labelled)),
						order.get(0) + " · " + top + " users (the majority-class baseline)");
			}
			bars.set(order, counts);
		}
	}

	/** Community sizes as horizontal bars, largest first. */
	private static final class CommunityBars extends JComponent
	{
		private static final long serialVersionUID = 1L;
		private static final int ROW = 24;
		private static final int SHOWN = 12;
		private List<String> order = new ArrayList<String>();
		private Map<String,Integer> counts = new HashMap<String,Integer>();

		void set(List<String> order, Map<String,Integer> counts)
		{
			this.order = order;
			this.counts = counts;
			revalidate();
			repaint();
		}

		public Dimension getPreferredSize()
		{
			int rows = Math.min(SHOWN, order.size()) + (order.size() > SHOWN ? 1 : 0);
			return new Dimension(400, Math.max(1, rows) * ROW + 4);
		}

		public Dimension getMaximumSize()
		{
			return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
		}

		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D)graphics.create();
			NodeLabUi.prepare(g);
			if (order.isEmpty())
			{
				g.setFont(NebulaDesign.fontBody(12f));
				g.setColor(NebulaDesign.TEXT_TERTIARY);
				g.drawString("No users loaded yet.", 0, 16);
				g.dispose();
				return;
			}
			int max = counts.get(order.get(0)).intValue();
			int total = 0;
			for (Integer count : counts.values())
			{
				total += count.intValue();
			}
			g.setFont(NebulaDesign.fontBody(12f));
			FontMetrics metrics = g.getFontMetrics();
			int nameWidth = Math.min(220, getWidth() / 3);
			int barX = nameWidth + 12;
			int barWidth = Math.max(40, getWidth() - barX - 110);
			for (int index = 0; index < Math.min(SHOWN, order.size()); index++)
			{
				String name = order.get(index);
				int count = counts.get(name).intValue();
				int y = index * ROW;
				g.setColor(NebulaDesign.TEXT_PRIMARY);
				String shown = name;
				while (shown.length() > 3 && metrics.stringWidth(shown) > nameWidth)
				{
					shown = shown.substring(0, shown.length() - 2);
				}
				g.drawString(shown.equals(name) ? name : shown + "…", 0, y + 16);
				g.setColor(NebulaDesign.INSET);
				g.fill(new RoundRectangle2D.Float(barX, y + 6, barWidth, 12, 12, 12));
				g.setColor(NodeLabUi.nodeColor(index + 1));
				g.fill(new RoundRectangle2D.Float(barX, y + 6, Math.max(4, barWidth * count / (float)max), 12, 12, 12));
				g.setColor(NebulaDesign.TEXT_SECONDARY);
				g.drawString(count + "  (" + NodeLabUi.percent(count / (double)Math.max(1, total)) + ")",
						barX + barWidth + 10, y + 16);
			}
			if (order.size() > SHOWN)
			{
				g.setColor(NebulaDesign.TEXT_TERTIARY);
				g.drawString("and " + (order.size() - SHOWN) + " more communities", 0, SHOWN * ROW + 16);
			}
			g.dispose();
		}
	}
}
