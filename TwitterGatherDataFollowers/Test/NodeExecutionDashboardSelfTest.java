package TwitterGatherDataFollowers.userRyersonU;

import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;

public final class NodeExecutionDashboardSelfTest
{
	private NodeExecutionDashboardSelfTest()
	{
	}

	public static void main(final String[] args) throws Exception
	{
		System.setProperty("java.awt.headless", "true");
		seedEightNodeSnapshot();
		final BufferedImage[] rendered = new BufferedImage[1];

		SwingUtilities.invokeAndWait(new Runnable()
		{
			public void run()
			{
				NodeExecutionDashboard dashboard =
						new NodeExecutionDashboard();
				dashboard.setSize(460, 820);
				dashboard.onSnapshot(NodeExecutionMonitor.snapshot());
				layoutRecursively(dashboard);

				BufferedImage image = new BufferedImage(
						460, 820, BufferedImage.TYPE_INT_ARGB);
				Graphics2D graphics = image.createGraphics();
				dashboard.paint(graphics);
				graphics.dispose();
				rendered[0] = image;
			}
		});

		int paintedPixels = countPaintedPixels(rendered[0]);
		if (paintedPixels < 100000)
		{
			throw new AssertionError(
					"dashboard render is unexpectedly blank: "
							+ paintedPixels + " painted pixels");
		}
		if (args.length > 0)
		{
			ImageIO.write(rendered[0], "png", new File(args[0]));
		}
		NodeExecutionMonitor.reset();
		System.out.println("NodeExecutionDashboardSelfTest passed");
	}

	private static void seedEightNodeSnapshot()
	{
		NodeExecutionMonitor.beginExperiment(8, "Doc2Vec");
		for (int index = 1; index <= 8; index++)
		{
			String owner = "Recommender-ServiceAgent" + index;
			NodeExecutionMonitor.nodeReady(owner, 1000000);
			NodeExecutionMonitor.beginNodeRun(owner, "visual-run", 1000000);
		}
		NodeExecutionMonitor.updateTweets(
				"Recommender-ServiceAgent1", 630000, 1000000);
		NodeExecutionMonitor.preprocessing(
				"Recommender-ServiceAgent2", 1000000, 1000000);
		NodeExecutionMonitor.waitingForPeers(
				"Recommender-ServiceAgent3", 124000);
		NodeExecutionMonitor.buildingFeatures(
				"Recommender-ServiceAgent4",
				"Building sparse TF-IDF features for the assigned shard.");
		NodeExecutionMonitor.waitingForMemory(
				"Recommender-ServiceAgent5", "SVM training", 640);
		NodeExecutionMonitor.waitingForSharedModel(
				"Recommender-ServiceAgent6",
				"Another node is training the shared Doc2Vec model.");
		NodeExecutionMonitor.modelRunning(
				"Recommender-ServiceAgent7",
				"Scoring this node's candidate shard.");
		NodeExecutionMonitor.complete("Recommender-ServiceAgent8");
		NodeExecutionMonitor.setRunPhase(
				"Nodes are processing their assigned shards in parallel.");
	}

	private static void layoutRecursively(Container container)
	{
		container.doLayout();
		for (Component component : container.getComponents())
		{
			if (component instanceof Container)
			{
				layoutRecursively((Container)component);
			}
		}
	}

	private static int countPaintedPixels(BufferedImage image)
	{
		int painted = 0;
		for (int y = 0; y < image.getHeight(); y++)
		{
			for (int x = 0; x < image.getWidth(); x++)
			{
				int alpha = image.getRGB(x, y) >>> 24;
				if (alpha != 0)
				{
					painted++;
				}
			}
		}
		return painted;
	}
}
