package TwitterGatherDataFollowers.userRyersonU;

import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;

public final class PopulationSweepPanelSelfTest
{
	private PopulationSweepPanelSelfTest()
	{
	}

	public static void main(String[] args) throws Exception
	{
		System.setProperty("java.awt.headless", "true");
		final BufferedImage[] image = new BufferedImage[1];
		SwingUtilities.invokeAndWait(new Runnable()
		{
			public void run()
			{
				PopulationSweepPanel panel = new PopulationSweepPanel(null);
				panel.setSize(1500, 860);
				layoutRecursively(panel);
				BufferedImage rendered = new BufferedImage(
						1500, 860, BufferedImage.TYPE_INT_ARGB);
				Graphics2D graphics = rendered.createGraphics();
				panel.paint(graphics);
				graphics.dispose();
				image[0] = rendered;
			}
		});

		int painted = 0;
		for (int y = 0; y < image[0].getHeight(); y++)
		{
			for (int x = 0; x < image[0].getWidth(); x++)
			{
				if ((image[0].getRGB(x, y) >>> 24) != 0)
				{
					painted++;
				}
			}
		}
		if (painted < 900000)
		{
			throw new AssertionError(
					"population lab render is unexpectedly blank: " + painted);
		}
		if (args.length > 0)
		{
			ImageIO.write(image[0], "png", new File(args[0]));
		}
		System.out.println("PopulationSweepPanelSelfTest passed");
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
}
