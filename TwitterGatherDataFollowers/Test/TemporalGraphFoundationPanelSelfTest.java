package TwitterGatherDataFollowers.userRyersonU;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.swing.SwingUtilities;

public final class TemporalGraphFoundationPanelSelfTest
{
	private TemporalGraphFoundationPanelSelfTest()
	{
	}

	public static void main(String[] args) throws Exception
	{
		File dataset = File.createTempFile("dsmp-tgf-panel-self-test", ".txt");
		dataset.deleteOnExit();
		writeDataset(dataset);
		TemporalGraphFoundationRegistry.record(
				TemporalGraphFoundationModelSupport.score(
						new TemporalGraphFoundationModelSupport.Input(
								"panel-self-test",
								dataset,
								tfidf(),
								followees(),
								followees(),
								Collections.singletonList("alice"),
								primaryServers(),
								1,
								1,
								5)));

		final RuntimeException[] failure = new RuntimeException[1];
		SwingUtilities.invokeAndWait(new Runnable()
		{
			public void run()
			{
				try
				{
					TemporalGraphFoundationPanel panel =
							new TemporalGraphFoundationPanel(null);
					panel.setSize(1280, 820);
					panel.doLayout();
					BufferedImage image = new BufferedImage(
							1280,
							820,
							BufferedImage.TYPE_INT_ARGB);
					Graphics2D graphics = image.createGraphics();
					try
					{
						panel.paint(graphics);
					}
					finally
					{
						graphics.dispose();
						panel.stopAnimationForTest();
					}
					require(hasVisualVariation(image),
							"TGF Studio render did not produce varied pixels");
				}
				catch (RuntimeException ex)
				{
					failure[0] = ex;
				}
			}
		});
		if (failure[0] != null)
		{
			throw failure[0];
		}
		System.out.println("TemporalGraphFoundationPanelSelfTest passed");
	}

	private static boolean hasVisualVariation(BufferedImage image)
	{
		int first = image.getRGB(0, 0);
		for (int y = 0; y < image.getHeight(); y += 31)
		{
			for (int x = 0; x < image.getWidth(); x += 37)
			{
				if (image.getRGB(x, y) != first)
				{
					return true;
				}
			}
		}
		return false;
	}

	private static void writeDataset(File dataset) throws Exception
	{
		BufferedWriter writer = new BufferedWriter(new FileWriter(dataset));
		try
		{
			writer.write("@research\t1\t2026-01-01\t10\talice\tgraph foundation recommender\n");
			writer.write("@research\t2\t2026-01-02\t11\tbob\tgraph foundation ranking\n");
			writer.write("@lifestyle\t3\t2020-01-01\t12\tcara\trecipe garden travel\n");
			writer.write("@research\t4\t2026-01-03\t11\tbob\tgraph recommender bridge\n");
		}
		finally
		{
			writer.close();
		}
	}

	private static Map<String,Map<String,Double>> tfidf()
	{
		Map<String,Map<String,Double>> tfidf =
				new LinkedHashMap<String,Map<String,Double>>();
		tfidf.put("alice", vector(
				"graph", 1.0,
				"foundation", 0.8,
				"recommender", 0.6));
		tfidf.put("bob", vector(
				"graph", 0.95,
				"foundation", 0.7,
				"ranking", 0.4));
		tfidf.put("cara", vector(
				"recipe", 0.9,
				"garden", 0.6,
				"travel", 0.4));
		return tfidf;
	}

	private static Map<String,String> followees()
	{
		Map<String,String> followees = new LinkedHashMap<String,String>();
		followees.put("alice", "@research");
		followees.put("bob", "@research");
		followees.put("cara", "@lifestyle");
		return followees;
	}

	private static Map<String,Integer> primaryServers()
	{
		Map<String,Integer> primaryServers = new LinkedHashMap<String,Integer>();
		primaryServers.put("bob", Integer.valueOf(1));
		primaryServers.put("cara", Integer.valueOf(1));
		return primaryServers;
	}

	private static Map<String,Double> vector(Object... termsAndWeights)
	{
		Map<String,Double> map = new LinkedHashMap<String,Double>();
		for (int i = 0; i + 1 < termsAndWeights.length; i += 2)
		{
			map.put((String) termsAndWeights[i], (Double) termsAndWeights[i + 1]);
		}
		return map;
	}

	private static void require(boolean condition, String message)
	{
		if (!condition)
		{
			throw new AssertionError(message);
		}
	}
}
