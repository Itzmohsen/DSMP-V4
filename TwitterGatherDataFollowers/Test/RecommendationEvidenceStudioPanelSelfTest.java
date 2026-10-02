package TwitterGatherDataFollowers.userRyersonU;

import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;

/**
 * Evidence Studio renders its empty state and, for a live Similarity run fed
 * through the simulator hook, the explanation of a recommendation, the run
 * analysis and the methods, in both themes.
 */
public final class RecommendationEvidenceStudioPanelSelfTest
{
	private RecommendationEvidenceStudioPanelSelfTest()
	{
	}

	public static void main(final String[] args) throws Exception
	{
		System.setProperty("java.awt.headless", "true");
		File directory = Files.createTempDirectory("evidence-panel").toFile();
		System.setProperty("dsmp.results.dir", new File(directory, "Results").getPath());
		System.setProperty("dsmp.runCorpus.dir", new File(directory, "run-corpus").getPath());
		final File corpusFile = corpus(new File(directory, "run-corpus"));
		RunCorpus.remember("panel-run", corpusFile);
		final EvidenceCorpus corpus = EvidenceCorpus.of(corpusFile);
		final RecommendationEvidenceStudioPanel[] panel = new RecommendationEvidenceStudioPanel[1];
		final BufferedImage[] empty = new BufferedImage[1];
		final BufferedImage[] day = new BufferedImage[3];
		final BufferedImage[] night = new BufferedImage[3];
		SwingUtilities.invokeAndWait(new Runnable()
		{
			public void run()
			{
				panel[0] = new RecommendationEvidenceStudioPanel(null);
				panel[0].setSize(1440, 1000);
				empty[0] = render(panel[0]);
				SimulatorHelpPanel.Snapshot snapshot = SimulatorHelpPanel.Snapshot.empty();
				for (int u = 0; u < 4; u++)
				{
					double[] cosine = corpus.cosines(u);
					List<RecommendationEvidenceEngine.MatchEvidence> matches =
							new ArrayList<RecommendationEvidenceEngine.MatchEvidence>();
					int rank = 1;
					for (int v : order(cosine, u))
					{
						matches.add(new RecommendationEvidenceEngine.MatchEvidence(rank++, corpus.users[v],
								String.format(java.util.Locale.US, "%.6f", Double.valueOf(cosine[v])), ""));
						if (rank > 3)
						{
							break;
						}
					}
					panel[0].recordRecommendation(snapshot, new RecommendationEvidenceEngine.RecommendationEvent(
							"panel-run", corpus.users[u], RecommendationCandidateType.USER,
							RecommendationResultStatus.OK, "Similarity", "", matches));
				}
				RecommendationEvidenceEngine.EvidenceRun run = panel[0].run();
				require(run != null && run.size() == 4, "the live run did not collect its targets");
				EvidenceExplainPage explain =
						(EvidenceExplainPage)panel[0].page(RecommendationEvidenceStudioPanel.EXPLAIN);
				explain.renderNow(RecommendationEvidenceEngine.explain(run, corpus.users[0], 0, null, null));
				EvidenceRunPage runPage = (EvidenceRunPage)panel[0].page(RecommendationEvidenceStudioPanel.RUN);
				runPage.renderNow(run, RecommendationEvidenceEngine.analyzeRun(run));
				String[] keys = {RecommendationEvidenceStudioPanel.EXPLAIN, RecommendationEvidenceStudioPanel.RUN,
					RecommendationEvidenceStudioPanel.METHODS};
				for (int i = 0; i < keys.length; i++)
				{
					panel[0].show(keys[i]);
					day[i] = render(panel[0]);
				}
				boolean wasNight = StudioNightTheme.isNightMode();
				StudioNightTheme.setNightMode(true);
				try
				{
					for (int i = 0; i < keys.length; i++)
					{
						panel[0].show(keys[i]);
						night[i] = render(panel[0]);
					}
				}
				finally
				{
					StudioNightTheme.setNightMode(wasNight);
				}
			}
		});
		//A newer run on the same targets replaces the delivered list and the explanation of the
		//selected target (they once stayed from the previous run).
		RunCorpus.remember("panel-run-2", corpusFile);
		SwingUtilities.invokeAndWait(new Runnable()
		{
			public void run()
			{
				EvidenceExplainPage explain =
						(EvidenceExplainPage)panel[0].page(RecommendationEvidenceStudioPanel.EXPLAIN);
				RecommendationEvidenceEngine.MatchEvidence before = explain.selectedMatch();
				require(before != null, "no recommendation was selected in the first run");
				SimulatorHelpPanel.Snapshot snapshot = SimulatorHelpPanel.Snapshot.empty();
				for (int u = 0; u < 4; u++)
				{
					double[] cosine = corpus.cosines(u);
					List<Integer> order = order(cosine, u);
					java.util.Collections.reverse(order);
					List<RecommendationEvidenceEngine.MatchEvidence> matches =
							new ArrayList<RecommendationEvidenceEngine.MatchEvidence>();
					for (int rank = 1; rank <= 3; rank++)
					{
						int v = order.get(rank - 1).intValue();
						matches.add(new RecommendationEvidenceEngine.MatchEvidence(rank, corpus.users[v],
								String.format(java.util.Locale.US, "%.6f", Double.valueOf(cosine[v])), ""));
					}
					panel[0].recordRecommendation(snapshot, new RecommendationEvidenceEngine.RecommendationEvent(
							"panel-run-2", corpus.users[u], RecommendationCandidateType.USER,
							RecommendationResultStatus.OK, "Similarity", "", matches));
				}
				RecommendationEvidenceEngine.EvidenceRun second = panel[0].run();
				require(second != null && "panel-run-2".equals(second.runId), "the studio did not follow the newer run");
				RecommendationEvidenceEngine.MatchEvidence after = explain.selectedMatch();
				require(after != null && after == second.event(corpus.users[0]).matches.get(0),
						"the delivered list still shows the previous run after switching runs");
				require(!after.name.equals(before.name),
						"the second run's first recommendation should differ from the first run's");
			}
		});
		requirePainted(empty[0], 40000, "empty state");
		String[] names = {"explain", "run", "methods"};
		for (int i = 0; i < names.length; i++)
		{
			requirePainted(day[i], 200000, names[i] + " (day)");
			requirePainted(night[i], 200000, names[i] + " (night)");
		}
		if (args.length > 0)
		{
			File output = new File(args[0]);
			output.mkdirs();
			for (int i = 0; i < names.length; i++)
			{
				ImageIO.write(day[i], "png", new File(output, "evidence-" + names[i] + ".png"));
				ImageIO.write(night[i], "png", new File(output, "evidence-" + names[i] + "-night.png"));
			}
		}
		System.out.println("RecommendationEvidenceStudioPanelSelfTest passed");
	}

	private static List<Integer> order(final double[] scores, int u)
	{
		ArrayList<Integer> order = new ArrayList<Integer>();
		for (int v = 0; v < scores.length; v++)
		{
			if (v != u)
			{
				order.add(Integer.valueOf(v));
			}
		}
		java.util.Collections.sort(order, new java.util.Comparator<Integer>()
		{
			public int compare(Integer a, Integer b)
			{
				return Double.compare(scores[b.intValue()], scores[a.intValue()]);
			}
		});
		return order;
	}

	private static File corpus(File folder) throws Exception
	{
		String[][] users = {
			{"Alice", "StoreA", "graph neural recommender model|neural ranking evidence notes|graph ranking model study"},
			{"Bob", "StoreB", "graph neural model ranking|neural graph study notes|ranking model evidence"},
			{"Carol", "StoreC", "fresh fruit market|fruit market prices today|fresh bread market"},
			{"Dave", "StoreA", "neural model training|graph model study|recommender evidence notes"},
			{"Erin", "StoreC", "market prices fruit|fresh fruit bread|bread market morning"},
			{"Frank", "StoreB", "graph recommender ranking|ranking study notes|neural notes model"}};
		LinkedHashMap<String,List<Tweet>> tweets = new LinkedHashMap<String,List<Tweet>>();
		LinkedHashMap<String,String> followees = new LinkedHashMap<String,String>();
		long id = 1;
		for (String[] user : users)
		{
			ArrayList<Tweet> list = new ArrayList<Tweet>();
			int month = 1;
			for (String text : user[2].split("\\|"))
			{
				list.add(new Tweet(text, id++, "2026-0" + month++ + "-15 10:00:00", user[0]));
			}
			tweets.put(user[0], list);
			followees.put(user[0], user[1]);
		}
		return RunCorpus.write(folder, tweets.keySet(), tweets, followees, true, true, false,
				java.util.Collections.<String>emptySet());
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
