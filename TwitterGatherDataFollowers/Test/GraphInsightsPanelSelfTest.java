package TwitterGatherDataFollowers.userRyersonU;

import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;

/**
 * Graph Insights renders every page for a run graph (and its empty state),
 * the Explorer's canvas lays a graph out by itself, and the canvas and the
 * figure exporters draw the same graph.
 */
public final class GraphInsightsPanelSelfTest
{
	private GraphInsightsPanelSelfTest()
	{
	}

	public static void main(final String[] args) throws Exception
	{
		System.setProperty("java.awt.headless", "true");
		File results = Files.createTempDirectory("graph-insights-results").toFile();
		System.setProperty("dsmp.results.dir", results.getPath());
		RunGraph graph = RunGraph.of(RunGraphSelfTest.userSetup(), RunGraphSelfTest.userResults(), "sample.txt");
		RunGraphAnalysis analysis = RunGraphAnalysis.of(graph);
		RunGraphAnalysis.GraphLayoutResult layout = RunGraphRecord.layout(graph, analysis);
		final RunGraphRecord.Loaded withLayout = new RunGraphRecord.Loaded(graph, analysis, layout.x, layout.y, null,
				false);
		final RunGraphRecord.Loaded withoutLayout = new RunGraphRecord.Loaded(graph, analysis, null, null, null, true);
		final GraphInsightsPanel[] panel = new GraphInsightsPanel[1];
		final BufferedImage[] images = new BufferedImage[7];
		SwingUtilities.invokeAndWait(new Runnable()
		{
			public void run()
			{
				panel[0] = new GraphInsightsPanel(null);
				panel[0].setSize(1400, 980);
				String[] keys = {GraphInsightsPanel.OVERVIEW, GraphInsightsPanel.EXPLORER,
					GraphInsightsPanel.COMMUNITIES, GraphInsightsPanel.INFLUENCE, GraphInsightsPanel.ADOPTION,
					GraphInsightsPanel.METHODS};
				for (int index = 0; index < keys.length; index++)
				{
					panel[0].page(keys[index]).show(withLayout, null);
					panel[0].show(keys[index]);
					images[index] = render(panel[0]);
				}
				panel[0].page(GraphInsightsPanel.OVERVIEW).show(null, "No run graph yet.");
				panel[0].show(GraphInsightsPanel.OVERVIEW);
				images[6] = render(panel[0]);
			}
		});
		String[] names = {"overview", "explorer", "communities", "influence", "adoption", "methods", "empty"};
		int[] minimum = {250000, 400000, 250000, 250000, 250000, 250000, 60000};
		for (int index = 0; index < images.length; index++)
		{
			int painted = distinctPixels(images[index]);
			if (painted < minimum[index])
			{
				throw new AssertionError(names[index] + " page is unexpectedly blank: " + painted);
			}
		}
		canvasLaysOutAndExports(withoutLayout, layout);
		if (args.length > 0)
		{
			File output = new File(args[0]);
			output.mkdirs();
			for (int index = 0; index < images.length; index++)
			{
				ImageIO.write(images[index], "png", new File(output, "graph-insights-" + names[index] + ".png"));
			}
		}
		System.out.println("GraphInsightsPanelSelfTest passed");
	}

	/** Without a saved layout the canvas computes the same deterministic layout as the run record. */
	private static void canvasLaysOutAndExports(final RunGraphRecord.Loaded loaded,
			final RunGraphAnalysis.GraphLayoutResult expected) throws Exception
	{
		final GraphCanvas canvas = new GraphCanvas();
		final RunGraphRecord.Loaded[] finished = new RunGraphRecord.Loaded[1];
		SwingUtilities.invokeAndWait(new Runnable()
		{
			public void run()
			{
				canvas.setSize(900, 600);
				canvas.setListener(new GraphCanvas.Listener()
				{
					public void selectionChanged(int node)
					{
					}

					public void layoutProgress(boolean running, int iterations, boolean converged)
					{
					}

					public void layoutFinished(RunGraphRecord.Loaded withLayout)
					{
						finished[0] = withLayout;
					}
				});
				canvas.setLoaded(loaded);
			}
		});
		long deadline = System.currentTimeMillis() + 60000L;
		while (System.currentTimeMillis() < deadline)
		{
			final boolean[] done = new boolean[1];
			SwingUtilities.invokeAndWait(new Runnable()
			{
				public void run()
				{
					done[0] = finished[0] != null;
				}
			});
			if (done[0])
			{
				break;
			}
			Thread.sleep(50L);
		}
		if (finished[0] == null)
		{
			throw new AssertionError("the canvas did not finish its layout");
		}
		for (int node = 0; node < expected.x.length; node++)
		{
			if (finished[0].x[node] != expected.x[node] || finished[0].y[node] != expected.y[node])
			{
				throw new AssertionError("the canvas layout differs from the record's at node " + node);
			}
		}
		final String[] svg = new String[1];
		final BufferedImage[] image = new BufferedImage[1];
		SwingUtilities.invokeAndWait(new Runnable()
		{
			public void run()
			{
				canvas.select(loaded.graph.indexOf("a1"));
				svg[0] = canvas.svg();
				image[0] = canvas.image(1.0);
			}
		});
		if (!svg[0].contains("<svg") || !svg[0].contains(">a1</text>"))
		{
			throw new AssertionError("the SVG export lacks the selected target's label");
		}
		int drawn = distinctPixels(image[0]);
		if (drawn < 8000)
		{
			throw new AssertionError("the canvas image is unexpectedly blank: " + drawn);
		}
	}

	private static BufferedImage render(Component component)
	{
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

	/** Pixels that differ from their row's background (the gradient seen at the left margin). */
	private static int distinctPixels(BufferedImage image)
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
		return painted;
	}
}
