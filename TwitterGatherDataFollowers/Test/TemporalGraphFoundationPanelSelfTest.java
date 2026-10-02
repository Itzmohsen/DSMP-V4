package TwitterGatherDataFollowers.userRyersonU;

import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import javax.imageio.ImageIO;
import javax.swing.JList;
import javax.swing.SwingUtilities;

/**
 * TGF Studio renders every page of a live TGF run in both themes, opens a
 * target's explanation from another page, and shows its empty state without
 * a run.
 */
public final class TemporalGraphFoundationPanelSelfTest
{
	private TemporalGraphFoundationPanelSelfTest()
	{
	}

	public static void main(final String[] args) throws Exception
	{
		System.setProperty("java.awt.headless", "true");
		File directory = Files.createTempDirectory("dsmp-tgf-panel").toFile();
		System.setProperty("dsmp.results.dir", new File(directory, "Results").getPath());
		File dataset = new File(directory, "tgf-panel.txt");
		Map<String,Map<String,Double>> tfidf = new LinkedHashMap<String,Map<String,Double>>();
		Map<String,String> followees = new LinkedHashMap<String,String>();
		List<String> targets = new ArrayList<String>();
		writeDataset(dataset, tfidf, followees, targets);
		TemporalGraphFoundationRegistry.clear();
		final TemporalGraphFoundationPanel[] panel = new TemporalGraphFoundationPanel[1];
		final BufferedImage[] empty = new BufferedImage[1];
		SwingUtilities.invokeAndWait(new Runnable()
		{
			public void run()
			{
				panel[0] = new TemporalGraphFoundationPanel(null);
				panel[0].setSize(1440, 1000);
				empty[0] = render(panel[0]);
			}
		});
		requirePainted(empty[0], 40000, "empty state");
		TemporalGraphFoundationRegistry.record(TemporalGraphFoundationModelSupport.score(
				new TemporalGraphFoundationModelSupport.Input("panel-self-test", dataset, tfidf, followees, followees,
						targets, Collections.<String,Integer>emptyMap(), 1, 1, 30)));
		final String[] keys = {TemporalGraphFoundationPanel.OVERVIEW, TemporalGraphFoundationPanel.TARGETS,
			TemporalGraphFoundationPanel.MODEL, TemporalGraphFoundationPanel.COMMUNITIES,
			TemporalGraphFoundationPanel.METHODS};
		final BufferedImage[] day = new BufferedImage[keys.length];
		final BufferedImage[] night = new BufferedImage[keys.length];
		final String[] selected = new String[1];
		SwingUtilities.invokeAndWait(new Runnable()
		{
			public void run()
			{
				panel[0].refreshFromRegistry();
			}
		});
		SwingUtilities.invokeAndWait(new Runnable()
		{
			public void run()
			{
				require(panel[0].report() != null && panel[0].report().live(), "the studio did not follow the live run");
				for (int index = 0; index < keys.length; index++)
				{
					panel[0].show(keys[index]);
					day[index] = render(panel[0]);
				}
				boolean wasNight = StudioNightTheme.isNightMode();
				StudioNightTheme.setNightMode(true);
				try
				{
					for (int index = 0; index < keys.length; index++)
					{
						panel[0].show(keys[index]);
						night[index] = render(panel[0]);
					}
				}
				finally
				{
					StudioNightTheme.setNightMode(wasNight);
				}
				panel[0].inspect("user-2-1");
				JList<?> list = find(panel[0].page(TemporalGraphFoundationPanel.TARGETS), JList.class);
				Object value = list == null ? null : list.getSelectedValue();
				selected[0] = value instanceof TgfReport.Target ? ((TgfReport.Target)value).user : null;
				require(TemporalGraphFoundationPanel.TARGETS.equals(panel[0].currentPage()),
						"inspecting a target did not open the Targets page");
			}
		});
		require("user-2-1".equals(selected[0]), "inspecting user-2-1 selected " + selected[0]);
		int[] minimum = {250000, 250000, 250000, 250000, 200000};
		for (int index = 0; index < keys.length; index++)
		{
			requirePainted(day[index], minimum[index], keys[index] + " (day)");
			requirePainted(night[index], minimum[index], keys[index] + " (night)");
		}
		if (args.length > 0)
		{
			File output = new File(args[0]);
			output.mkdirs();
			for (int index = 0; index < keys.length; index++)
			{
				ImageIO.write(day[index], "png", new File(output, "tgf-" + keys[index] + ".png"));
				ImageIO.write(night[index], "png", new File(output, "tgf-" + keys[index] + "-night.png"));
			}
		}
		TemporalGraphFoundationRegistry.clear();
		System.out.println("TemporalGraphFoundationPanelSelfTest passed");
	}

	/** Four communities of ten users, each with its own vocabulary and season. */
	private static void writeDataset(File dataset, Map<String,Map<String,Double>> tfidf, Map<String,String> followees,
			List<String> targets) throws Exception
	{
		String[] communities = {"@research", "@kitchen", "@travel", "@music"};
		String[][] vocabulary = {
			{"graph", "foundation", "ranking", "model", "embedding", "citation", "theorem"},
			{"recipe", "garden", "oven", "pasta", "harvest", "spice", "bread"},
			{"flight", "hotel", "beach", "passport", "museum", "train", "island"},
			{"guitar", "concert", "album", "melody", "drummer", "stage", "chorus"}};
		String[] shared = {"sunny", "weekend", "coffee", "photo", "friends", "city"};
		Random random = new Random(5L);
		BufferedWriter writer = new BufferedWriter(new FileWriter(dataset));
		try
		{
			long id = 1;
			for (int c = 0; c < communities.length; c++)
			{
				for (int member = 0; member < 10; member++)
				{
					String user = "user-" + c + "-" + member;
					followees.put(user, communities[c]);
					LinkedHashMap<String,Double> vector = new LinkedHashMap<String,Double>();
					for (int tweet = 0; tweet < 8; tweet++)
					{
						StringBuilder text = new StringBuilder();
						for (int word = 0; word < 4; word++)
						{
							String term = random.nextInt(5) == 0 ? vocabulary[random.nextInt(4)][random.nextInt(7)]
									: random.nextInt(4) == 0 ? shared[random.nextInt(shared.length)]
											: vocabulary[c][random.nextInt(7)];
							text.append(word == 0 ? "" : " ").append(term);
							vector.put(term, Double.valueOf(1.0));
						}
						int month = 1 + (c * 3 + random.nextInt(5)) % 12;
						writer.write(communities[c] + "\t" + (id++) + "\t2025-" + (month < 10 ? "0" : "") + month
								+ "-1" + random.nextInt(9) + " " + (8 + c * 3 + random.nextInt(3)) + ":00:00\t" + (100 + c)
								+ "\t" + user + "\t" + text + "\n");
					}
					tfidf.put(user, vector);
					if (member < 2)
					{
						targets.add(user);
					}
				}
			}
		}
		finally
		{
			writer.close();
		}
	}

	private static BufferedImage render(Component component)
	{
		layoutTree(component);
		layoutTree(component);
		BufferedImage image = new BufferedImage(component.getWidth(), component.getHeight(), BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		component.paint(g);
		g.dispose();
		return image;
	}

	private static void layoutTree(Component component)
	{
		if (component instanceof Container)
		{
			Container container = (Container)component;
			container.doLayout();
			for (Component child : container.getComponents())
			{
				layoutTree(child);
			}
		}
	}

	private static <T> T find(Component root, Class<T> type)
	{
		if (type.isInstance(root))
		{
			return type.cast(root);
		}
		if (root instanceof Container)
		{
			for (Component child : ((Container)root).getComponents())
			{
				T found = find(child, type);
				if (found != null)
				{
					return found;
				}
			}
		}
		return null;
	}

	private static void requirePainted(BufferedImage image, int minimum, String name)
	{
		int painted = 0;
		for (int y = 0; y < image.getHeight(); y++)
		{
			int background = image.getRGB(0, y);
			for (int x = 0; x < image.getWidth(); x++)
			{
				int rgb = image.getRGB(x, y);
				int difference = Math.abs((rgb >> 16 & 255) - (background >> 16 & 255))
						+ Math.abs((rgb >> 8 & 255) - (background >> 8 & 255)) + Math.abs((rgb & 255) - (background & 255));
				if ((rgb >>> 24) != 0 && difference > 6)
				{
					painted++;
				}
			}
		}
		require(painted >= minimum, name + " is unexpectedly blank: " + painted + " painted pixels");
	}

	private static void require(boolean condition, String message)
	{
		if (!condition)
		{
			throw new AssertionError(message);
		}
	}
}
