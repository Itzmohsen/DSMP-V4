package TwitterGatherDataFollowers.userRyersonU;

import java.awt.Graphics2D;
import java.awt.Container;
import java.awt.Component;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.ImageIO;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

public final class GraphInsightsPanelSelfTest
{
	private GraphInsightsPanelSelfTest()
	{
	}

	public static void main(final String[] args) throws Exception
	{
		System.setProperty("java.awt.headless", "true");
		Path directory = Files.createTempDirectory("graph-insights-panel");
		File mapping = write(directory, "name-number-Retail50K.txt",
				"StoreA\t1\nAlice\t2\nStoreB\t3\nBob\t4\nStoreC\t5\nCarol\t6\nStoreD\t7\nDrew\t8\nStoreE\t9\nEvan\t10\n");
		File edges = write(directory, "edges-numbers-Retail50K.txt",
				"2 1\n2 5\n4 3\n4 5\n6 3\n6 7\n8 1\n8 7\n10 3\n10 9\n");
		final GraphAnalysisEngine.Analysis analysis =
				GraphAnalysisEngine.analyze(mapping, edges);
		final SimulatorHelpPanel.Snapshot snapshot = new SimulatorHelpPanel.Snapshot(
				RecommendationModeSupport.SVM,
				8,
				3,
				5,
				0,
				1424,
				0,
				"Retail dataset",
				"Retail50K.txt",
				mapping.getAbsolutePath(),
				"SVM",
				RecommendationTargetMode.ALGORITHM_DEFAULT,
				"Algorithm Default",
				RecommendationCandidateType.FOLLOWEE,
				RecommendationCandidateType.FOLLOWEE,
				false,
				true,
				true,
				true,
				true,
				true,
				false,
				true,
				"Default",
				"Panel render test",
				"Balanced default capacity",
				"TARGET: Alice");

		final BufferedImage[] rendered = new BufferedImage[4];
		SwingUtilities.invokeAndWait(new Runnable()
		{
			public void run()
			{
				try
				{
					JPanel shape = instantiatePanel("NetworkShapePanel", snapshot, analysis);
					rendered[0] = render(shape, 820, 430);

					JPanel impact = instantiatePanel(
							"RecommendationImpactStoryPanel", snapshot, analysis);
					rendered[1] = render(impact, 650, 540);

					JPanel emptyImpact = instantiatePanel(
							"RecommendationImpactStoryPanel",
							snapshot,
							GraphAnalysisEngine.Analysis.missing(
									mapping,
									edges,
									"No recommendation run yet."));
					rendered[2] = render(emptyImpact, 650, 420);
					JPanel explorer = instantiateExplorationPanel(snapshot, analysis);
					rendered[3] = render(explorer, 1180, 650);
				}
				catch (Exception ex)
				{
					throw new RuntimeException(ex);
				}
			}
		});

		int painted = countPaintedPixels(rendered[0]);
		if (painted < 180000)
		{
			throw new AssertionError(
					"graph insights network render is unexpectedly blank: " + painted);
		}
		int impactPainted = countPaintedPixels(rendered[1]);
		if (impactPainted < 260000)
		{
			throw new AssertionError(
					"recommendation impact render is unexpectedly blank: " + impactPainted);
		}
		int emptyPainted = countPaintedPixels(rendered[2]);
		if (emptyPainted < 190000)
		{
			throw new AssertionError(
					"recommendation impact empty state is unexpectedly blank: " + emptyPainted);
		}
		int explorerPainted = countPaintedPixels(rendered[3]);
		if (explorerPainted < 600000)
		{
			throw new AssertionError(
					"graph explorer render is unexpectedly blank: " + explorerPainted);
		}
		if (args.length > 0)
		{
			File output = new File(args[0]);
			if (output.isDirectory())
			{
				ImageIO.write(rendered[0], "png", new File(output, "network-shape-panel.png"));
				ImageIO.write(rendered[1], "png", new File(output, "recommendation-impact-panel.png"));
				ImageIO.write(rendered[2], "png", new File(output, "recommendation-impact-empty.png"));
				ImageIO.write(rendered[3], "png", new File(output, "graph-explorer-panel.png"));
			}
			else
			{
				ImageIO.write(rendered[3], "png", output);
			}
		}
		System.out.println("GraphInsightsPanelSelfTest passed");
	}

	private static BufferedImage render(JPanel panel, int width, int height)
	{
		panel.setSize(width, height);
		layoutTree(panel);
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();
		panel.paint(graphics);
		graphics.dispose();
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

	private static JPanel instantiatePanel(
			String panelName,
			SimulatorHelpPanel.Snapshot snapshot,
			GraphAnalysisEngine.Analysis analysis) throws Exception
	{
		Class<?> panelClass = Class.forName(
				"TwitterGatherDataFollowers.userRyersonU.GraphInsightsPanel$" + panelName);
		Constructor<?> constructor = panelClass.getDeclaredConstructor();
		constructor.setAccessible(true);
		JPanel panel = (JPanel)constructor.newInstance();
		Method setState = panelClass.getSuperclass().getDeclaredMethod(
				"setState",
				SimulatorHelpPanel.Snapshot.class,
				GraphAnalysisEngine.Analysis.class);
		setState.setAccessible(true);
		setState.invoke(panel, snapshot, analysis);
		return panel;
	}

	private static JPanel instantiateExplorationPanel(
			SimulatorHelpPanel.Snapshot snapshot,
			GraphAnalysisEngine.Analysis analysis) throws Exception
	{
		Class<?> panelClass = Class.forName(
				"TwitterGatherDataFollowers.userRyersonU.GraphInsightsPanel$ExplorationPanel");
		Constructor<?> constructor = panelClass.getDeclaredConstructor();
		constructor.setAccessible(true);
		JPanel panel = (JPanel)constructor.newInstance();
		Method setState = panelClass.getDeclaredMethod(
				"setState",
				SimulatorHelpPanel.Snapshot.class,
				GraphAnalysisEngine.Analysis.class);
		setState.setAccessible(true);
		setState.invoke(panel, snapshot, analysis);
		return panel;
	}

	private static File write(Path directory, String fileName, String content) throws Exception
	{
		Path file = directory.resolve(fileName);
		Files.write(file, content.getBytes(StandardCharsets.UTF_8));
		return file.toFile();
	}

	private static int countPaintedPixels(BufferedImage image)
	{
		int painted = 0;
		for (int y = 0; y < image.getHeight(); y++)
		{
			for (int x = 0; x < image.getWidth(); x++)
			{
				if ((image.getRGB(x, y) >>> 24) != 0)
				{
					painted++;
				}
			}
		}
		return painted;
	}
}
