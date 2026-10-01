package TwitterGatherDataFollowers.userRyersonU;

import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;

/**
 * Development tool: boots the simulator with its window hidden and renders
 * each application tab (or the tabs whose titles contain the given words) to
 * PNG files, in day and night themes, so the interface can be reviewed
 * without a display session. Not a self-test.
 *
 * Usage: GuiSnapshotTool &lt;output dir&gt; [width] [height] [title filter...]
 */
public final class GuiSnapshotTool
{
	private GuiSnapshotTool()
	{
	}

	public static void main(String[] args) throws Exception
	{
		final File output = new File(args.length > 0 ? args[0] : "gui-snapshots");
		final int width = args.length > 1 ? Integer.parseInt(args[1]) : 1600;
		final int height = args.length > 2 ? Integer.parseInt(args[2]) : 1000;
		final ArrayList<String> filters = new ArrayList<String>();
		for (int index = 3; index < args.length; index++)
		{
			filters.add(args[index].toLowerCase(java.util.Locale.US));
		}
		output.mkdirs();
		System.setProperty("dsmp.gui.hidden", "true");
		jade.core.Runtime runtime = jade.core.Runtime.instance();
		runtime.setCloseVM(false);
		jade.core.Profile profile = new jade.core.ProfileImpl();
		profile.setParameter(jade.core.Profile.LOCAL_HOST, "localhost");
		profile.setParameter(jade.core.Profile.LOCAL_PORT, Integer.toString(freePort()));
		profile.setParameter(jade.core.Profile.GUI, "false");
		profile.setParameter("nomtp", "true");
		jade.wrapper.ContainerController main = runtime.createMainContainer(profile);
		main.createNewAgent("controller", ControllerAgent.class.getName(), null).start();
		long deadline = System.currentTimeMillis() + 120000L;
		while (ControllerAgentGui.latestInstance() == null && System.currentTimeMillis() < deadline)
		{
			Thread.sleep(100L);
		}
		final ControllerAgentGui gui = ControllerAgentGui.latestInstance();
		Field field = ControllerAgentGui.class.getDeclaredField("applicationTabs");
		field.setAccessible(true);
		JTabbedPane found = (JTabbedPane)field.get(gui);
		while (found == null && System.currentTimeMillis() < deadline + 60000L)
		{
			Thread.sleep(200L);
			found = (JTabbedPane)field.get(gui);
		}
		Thread.sleep(1500L);
		final JTabbedPane tabs = found;
		String runDataset = System.getProperty("snapshot.run", "");
		if (runDataset.length() > 0)
		{
			runAndSnapshot(gui, tabs, new File(runDataset), output, width, height);
			System.exit(0);
		}
		final List<String> written = new ArrayList<String>();
		for (final boolean night : new boolean[] {false, true})
		{
			SwingUtilities.invokeAndWait(new Runnable()
			{
				public void run()
				{
					try
					{
						java.lang.reflect.Method method =
								ControllerAgentGui.class.getDeclaredMethod("applyNightMode", boolean.class);
						method.setAccessible(true);
						method.invoke(gui, Boolean.valueOf(night));
					}
					catch (Exception ignored)
					{
						//Older windows have no night mode.
					}
					gui.addNotify();
					gui.setSize(width, height);
					gui.validate();
				}
			});
			for (int index = 0; index < tabs.getTabCount(); index++)
			{
				final int tab = index;
				final String title = tabs.getTitleAt(index);
				if (!filters.isEmpty())
				{
					boolean wanted = false;
					for (String filter : filters)
					{
						wanted |= title.toLowerCase(java.util.Locale.US).contains(filter);
					}
					if (!wanted)
					{
						continue;
					}
				}
				SwingUtilities.invokeAndWait(new Runnable()
				{
					public void run()
					{
						tabs.setSelectedIndex(tab);
						gui.validate();
					}
				});
				Thread.sleep(700L);
				final BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
				SwingUtilities.invokeAndWait(new Runnable()
				{
					public void run()
					{
						gui.validate();
						Component root = gui.getRootPane();
						root.setSize(gui.getContentPane().getParent().getSize());
						Graphics2D g = image.createGraphics();
						g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
						g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
								RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
						gui.getRootPane().paint(g);
						g.dispose();
					}
				});
				String name = String.format("%02d-%s-%s.png", index,
						title.replaceAll("[^A-Za-z0-9]+", "-").toLowerCase(java.util.Locale.US),
						night ? "night" : "day");
				File file = new File(output, name);
				ImageIO.write(image, "png", file);
				written.add(file.getPath());
				Component selected = StudioNightTheme.unwrap(tabs.getSelectedComponent());
				if (selected instanceof NodeLabPanel)
				{
					snapshotNodeLab((NodeLabPanel)selected, gui, output, index, night, width, height, written);
				}
			}
		}
		for (String path : written)
		{
			System.out.println("[snapshot] " + path);
		}
		try
		{
			main.kill();
		}
		catch (Exception ignored)
		{
		}
		System.exit(0);
	}

	/**
	 * End-to-end: loads a dataset, designs a 3-node heterogeneous cluster and
	 * an emulated plan with a scenario in the Node Lab, starts the run with
	 * the lab's own launcher, and renders the Live page while it runs and the
	 * Results and Overview pages after its record is written.
	 */
	private static void runAndSnapshot(final ControllerAgentGui gui, final JTabbedPane tabs, final File dataset,
			File output, final int width, final int height) throws Exception
	{
		final NodeLabPanel[] lab = new NodeLabPanel[1];
		SwingUtilities.invokeAndWait(new Runnable()
		{
			public void run()
			{
				gui.addNotify();
				gui.setSize(width, height);
				gui.validate();
				for (int index = 0; index < tabs.getTabCount(); index++)
				{
					Component component = StudioNightTheme.unwrap(tabs.getComponentAt(index));
					if (component instanceof NodeLabPanel)
					{
						lab[0] = (NodeLabPanel)component;
						tabs.setSelectedIndex(index);
					}
				}
				gui.loadImportedDataset(dataset.getAbsoluteFile(), "snapshot");
				gui.getUsers();
				try
				{
					Field box = ControllerAgentGui.class.getDeclaredField("algorithmSelectionBox");
					box.setAccessible(true);
					((javax.swing.JComboBox<?>)box.get(gui)).setSelectedIndex(
							Integer.getInteger("snapshot.algorithm", ControllerAgent.COS_SIM).intValue());
					Field graph = ControllerAgentGui.class.getDeclaredField("generateGraphOutput");
					graph.setAccessible(true);
					((javax.swing.AbstractButton)graph.get(gui)).setSelected(false);
				}
				catch (Exception unavailable)
				{
					System.out.println("[snapshot] cannot choose the algorithm: " + unavailable);
				}
			}
		});
		long deadline = System.currentTimeMillis() + 600000L;
		while (!(gui.isReadyToInitialize() && gui.loadedUserCount() > 0))
		{
			if (System.currentTimeMillis() > deadline)
			{
				throw new IllegalStateException("Users were not loaded in time.");
			}
			Thread.sleep(250L);
		}
		final int heap = gui.currentHeapSetting();
		SwingUtilities.invokeAndWait(new Runnable()
		{
			public void run()
			{
				selectLab(tabs);
				ClusterConfig cluster = ClusterConfig.ofRoles("snapshot trio", heap, NodeCapacityProfile.Role.STRONG,
						NodeCapacityProfile.Role.NORMAL, NodeCapacityProfile.Role.WEAK);
				lab[0].applyCluster(cluster.getProfiles(), cluster.getSettings());
				Scenario scenario = Scenario.builtInNamed(System.getProperty("snapshot.scenario", "Drain and return"));
				RecommendationSchedulerRegistry.apply(RecommendationSchedulerPlan.Mode.SCORING_SAFE,
						SchedulerLabEngine.SchedulingPolicy.WORK_STEALING, 3, heap, "snapshot", scenario);
				lab[0].planChanged();
				lab[0].show(NodeLabPanel.OVERVIEW);
			}
		});
		Thread.sleep(800L);
		write(gui, output, "run-0-overview-before", width, height);
		SwingUtilities.invokeAndWait(new Runnable()
		{
			public void run()
			{
				lab[0].launcher().start(null);
				lab[0].show(NodeLabPanel.LIVE);
			}
		});
		//Capture the Live page a few times while the run is going.
		int shots = 0;
		deadline = System.currentTimeMillis() + 1800000L;
		long lastShot = 0L;
		while (System.currentTimeMillis() < deadline)
		{
			NodeLabRunLauncher.State state = lab[0].launcher().getState();
			if (state == NodeLabRunLauncher.State.FINISHED || state == NodeLabRunLauncher.State.FAILED)
			{
				break;
			}
			SchedulingRunContext context = SchedulingRuntime.latest();
			boolean phase = context != null && context.runningPhase() != null;
			if (state == NodeLabRunLauncher.State.RUNNING && System.currentTimeMillis() - lastShot > 1500L
					&& shots < 6 && (phase || shots < 2))
			{
				lastShot = System.currentTimeMillis();
				SwingUtilities.invokeAndWait(new Runnable()
				{
					public void run()
					{
						selectLab(tabs);
						lab[0].show(NodeLabPanel.LIVE);
					}
				});
				Thread.sleep(300L);
				writeFull(lab[0], gui, output, "run-" + (++shots) + "-live", width, height);
			}
			Thread.sleep(200L);
		}
		System.out.println("[snapshot] launcher " + lab[0].launcher().getState() + ": " + lab[0].launcher().getMessage());
		SchedulingRunContext context = SchedulingRuntime.latest();
		if (context != null)
		{
			context.awaitRecord(300000L);
		}
		Thread.sleep(2500L);
		SwingUtilities.invokeAndWait(new Runnable()
		{
			public void run()
			{
				selectLab(tabs);
				lab[0].show(NodeLabPanel.LIVE);
			}
		});
		Thread.sleep(600L);
		writeFull(lab[0], gui, output, "run-8-live-after", width, height);
		final String runId = context == null ? "" : context.getRunId();
		SwingUtilities.invokeAndWait(new Runnable()
		{
			public void run()
			{
				selectLab(tabs);
				lab[0].openRun(runId);
			}
		});
		Thread.sleep(4000L);
		writeFull(lab[0], gui, output, "run-9-results", width, height);
		SwingUtilities.invokeAndWait(new Runnable()
		{
			public void run()
			{
				lab[0].show(NodeLabPanel.OVERVIEW);
			}
		});
		Thread.sleep(800L);
		writeFull(lab[0], gui, output, "run-10-overview-after", width, height);
	}

	private static void selectLab(JTabbedPane tabs)
	{
		for (int index = 0; index < tabs.getTabCount(); index++)
		{
			if (StudioNightTheme.unwrap(tabs.getComponentAt(index)) instanceof NodeLabPanel)
			{
				tabs.setSelectedIndex(index);
			}
		}
	}

	private static void write(final ControllerAgentGui gui, File output, String name, final int width,
			final int height) throws Exception
	{
		final BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		SwingUtilities.invokeAndWait(new Runnable()
		{
			public void run()
			{
				gui.validate();
				Graphics2D g = image.createGraphics();
				g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				gui.getRootPane().paint(g);
				g.dispose();
			}
		});
		File file = new File(output, name + ".png");
		ImageIO.write(image, "png", file);
		System.out.println("[snapshot] " + file.getPath());
	}

	/** The visible window and the current Node Lab page at full height. */
	private static void writeFull(final NodeLabPanel lab, final ControllerAgentGui gui, File output, String name,
			int width, int height) throws Exception
	{
		write(gui, output, name, width, height);
		final BufferedImage[] full = new BufferedImage[1];
		SwingUtilities.invokeAndWait(new Runnable()
		{
			public void run()
			{
				javax.swing.JScrollPane pane = findScroll(lab.page(lab.currentPage()));
				if (pane == null || pane.getViewport().getView() == null)
				{
					return;
				}
				Component view = pane.getViewport().getView();
				int viewWidth = pane.getViewport().getWidth();
				if (viewWidth <= 0)
				{
					return;
				}
				java.awt.Dimension original = view.getSize();
				view.setSize(viewWidth, 10);
				java.awt.Dimension preferred = view.getPreferredSize();
				view.setSize(viewWidth, preferred.height);
				layoutTree(view);
				BufferedImage image = new BufferedImage(viewWidth, Math.max(1, preferred.height),
						BufferedImage.TYPE_INT_RGB);
				Graphics2D g = image.createGraphics();
				g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g.setPaint(new java.awt.GradientPaint(0, 0, NebulaDesign.CANVAS_TOP, 0, preferred.height,
						NebulaDesign.CANVAS_BOTTOM));
				g.fillRect(0, 0, viewWidth, preferred.height);
				view.paint(g);
				g.dispose();
				full[0] = image;
				view.setSize(original);
				pane.validate();
			}
		});
		if (full[0] != null)
		{
			File file = new File(output, name + "-full.png");
			ImageIO.write(full[0], "png", file);
			System.out.println("[snapshot] " + file.getPath());
		}
	}

	/** Renders every Node Lab page: the window, and the whole scrollable page at full height. */
	private static void snapshotNodeLab(final NodeLabPanel lab, final ControllerAgentGui gui, File output,
			int index, boolean night, final int width, final int height, List<String> written) throws Exception
	{
		String[] keys = {NodeLabPanel.OVERVIEW, NodeLabPanel.CLUSTER, NodeLabPanel.PLAN, NodeLabPanel.LIVE,
			NodeLabPanel.RESULTS, NodeLabPanel.WHAT_IF, NodeLabPanel.STUDIES};
		String only = System.getProperty("snapshot.pages", "");
		for (final String key : keys)
		{
			if (only.length() > 0 && !java.util.Arrays.asList(only.split(",")).contains(key))
			{
				continue;
			}
			SwingUtilities.invokeAndWait(new Runnable()
			{
				public void run()
				{
					lab.show(key);
					gui.validate();
				}
			});
			Thread.sleep(Long.getLong("snapshot.settle", 900L).longValue());
			final BufferedImage[] images = new BufferedImage[2];
			SwingUtilities.invokeAndWait(new Runnable()
			{
				public void run()
				{
					gui.validate();
					BufferedImage window = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
					Graphics2D g = window.createGraphics();
					g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
					gui.getRootPane().paint(g);
					g.dispose();
					images[0] = window;
					javax.swing.JScrollPane pane = findScroll(lab.page(key));
					if (pane != null && pane.getViewport().getView() != null)
					{
						Component view = pane.getViewport().getView();
						int viewWidth = pane.getViewport().getWidth();
						java.awt.Dimension original = view.getSize();
						view.setSize(viewWidth, 10);
						java.awt.Dimension preferred = view.getPreferredSize();
						view.setSize(viewWidth, preferred.height);
						layoutTree(view);
						BufferedImage full = new BufferedImage(viewWidth, Math.max(1, preferred.height),
								BufferedImage.TYPE_INT_RGB);
						Graphics2D fg = full.createGraphics();
						fg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
						fg.setPaint(new java.awt.GradientPaint(0, 0, NebulaDesign.CANVAS_TOP, 0, preferred.height,
								NebulaDesign.CANVAS_BOTTOM));
						fg.fillRect(0, 0, viewWidth, preferred.height);
						view.paint(fg);
						fg.dispose();
						images[1] = full;
						view.setSize(original);
						pane.getViewport().doLayout();
						pane.validate();
					}
				}
			});
			String base = String.format("%02d-node-lab-%s-%s", index, key, night ? "night" : "day");
			File window = new File(output, base + ".png");
			ImageIO.write(images[0], "png", window);
			written.add(window.getPath());
			if (images[1] != null)
			{
				File full = new File(output, base + "-full.png");
				ImageIO.write(images[1], "png", full);
				written.add(full.getPath());
			}
		}
	}

	private static javax.swing.JScrollPane findScroll(Component component)
	{
		if (component instanceof javax.swing.JScrollPane)
		{
			return (javax.swing.JScrollPane)component;
		}
		if (component instanceof java.awt.Container)
		{
			for (Component child : ((java.awt.Container)component).getComponents())
			{
				javax.swing.JScrollPane found = findScroll(child);
				if (found != null)
				{
					return found;
				}
			}
		}
		return null;
	}

	private static void layoutTree(Component component)
	{
		component.doLayout();
		if (component instanceof java.awt.Container)
		{
			for (Component child : ((java.awt.Container)component).getComponents())
			{
				layoutTree(child);
			}
		}
	}

	private static int freePort() throws Exception
	{
		ServerSocket socket = new ServerSocket(0);
		try
		{
			return socket.getLocalPort();
		}
		finally
		{
			socket.close();
		}
	}
}
