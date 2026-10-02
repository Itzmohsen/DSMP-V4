package TwitterGatherDataFollowers.userRyersonU;

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;

/**
 * The interactive run graph: drag to pan, scroll to zoom, click a node to
 * inspect it (its neighbourhood stays bright), double-click to centre it or
 * the background to fit. Without a saved layout the canvas lays the graph out
 * itself with the same deterministic ForceAtlas2 as the run record, showing
 * the layout settle as it runs.
 */
final class GraphCanvas extends JComponent
{
	private static final long serialVersionUID = 1L;

	/** Told about selection and layout progress. */
	interface Listener
	{
		void selectionChanged(int node);

		void layoutProgress(boolean running, int iterations, boolean converged);

		void layoutFinished(RunGraphRecord.Loaded withLayout);
	}

	private RunGraphRecord.Loaded loaded;
	private double[] x;
	private double[] y;
	private final GraphRenderer.Style style = new GraphRenderer.Style();
	private GraphRenderer.Transform transform;
	private double fitScale = 1.0;
	private boolean userMoved;
	private Listener listener;
	private Point pressed;
	private GraphRenderer.Transform pressedTransform;
	private boolean dragged;
	private volatile LayoutWorker worker;
	private boolean legend = true;
	/** The width the legend takes at the canvas's left. */
	private static final int LEGEND_WIDTH = 270;
	/** Room on the right for the labels of the right-most nodes. */
	private static final int LABEL_ROOM = 90;

	GraphCanvas()
	{
		setOpaque(true);
		setFocusable(true);
		setToolTipText("");
		MouseAdapter mouse = new MouseAdapter()
		{
			public void mousePressed(MouseEvent event)
			{
				requestFocusInWindow();
				pressed = event.getPoint();
				pressedTransform = transform;
				dragged = false;
			}

			public void mouseDragged(MouseEvent event)
			{
				if (pressed == null || pressedTransform == null)
				{
					return;
				}
				int dx = event.getX() - pressed.x;
				int dy = event.getY() - pressed.y;
				if (Math.abs(dx) + Math.abs(dy) > 3)
				{
					dragged = true;
					userMoved = true;
					transform = pressedTransform.pan(dx, dy);
					repaint();
				}
			}

			public void mouseReleased(MouseEvent event)
			{
				if (!dragged && event.getClickCount() == 1 && x != null)
				{
					int node = nodeAt(event.getX(), event.getY());
					select(node == style.focus ? -1 : node);
				}
				pressed = null;
				dragged = false;
			}

			public void mouseClicked(MouseEvent event)
			{
				if (event.getClickCount() == 2 && x != null)
				{
					int node = nodeAt(event.getX(), event.getY());
					if (node >= 0)
					{
						select(node);
						center(node, true);
					}
					else
					{
						fit();
					}
				}
			}

			public void mouseMoved(MouseEvent event)
			{
				int node = x == null ? -1 : nodeAt(event.getX(), event.getY());
				if (node != style.hover)
				{
					style.hover = node;
					setCursor(java.awt.Cursor.getPredefinedCursor(node >= 0 ? java.awt.Cursor.HAND_CURSOR
							: java.awt.Cursor.DEFAULT_CURSOR));
					repaint();
				}
			}

			public void mouseExited(MouseEvent event)
			{
				if (style.hover >= 0)
				{
					style.hover = -1;
					repaint();
				}
			}

			public void mouseWheelMoved(MouseWheelEvent event)
			{
				if (transform == null)
				{
					return;
				}
				double factor = Math.pow(1.12, -event.getPreciseWheelRotation());
				zoomAt(factor, event.getX(), event.getY());
			}
		};
		addMouseListener(mouse);
		addMouseMotionListener(mouse);
		addMouseWheelListener(mouse);
		addKeyListener(new KeyAdapter()
		{
			public void keyPressed(KeyEvent event)
			{
				switch (event.getKeyCode())
				{
					case KeyEvent.VK_F:
						fit();
						break;
					case KeyEvent.VK_ESCAPE:
						select(-1);
						break;
					case KeyEvent.VK_PLUS:
					case KeyEvent.VK_EQUALS:
					case KeyEvent.VK_ADD:
						zoomBy(1.25);
						break;
					case KeyEvent.VK_MINUS:
					case KeyEvent.VK_SUBTRACT:
						zoomBy(0.8);
						break;
					default:
						break;
				}
			}
		});
	}

	void setListener(Listener value)
	{
		listener = value;
	}

	GraphRenderer.Style style()
	{
		return style;
	}

	RunGraphRecord.Loaded loaded()
	{
		return loaded;
	}

	boolean hasPositions()
	{
		return x != null;
	}

	boolean isLayingOut()
	{
		return worker != null;
	}

	void setLegend(boolean visible)
	{
		legend = visible;
		if (!userMoved && x != null)
		{
			fit();
		}
		repaint();
	}

	/** The transform that fits the shown nodes beside the legend (when it is visible), with room for labels. */
	private GraphRenderer.Transform fitted()
	{
		int left = legend && getWidth() > 700 ? LEGEND_WIDTH : 0;
		int right = style.labels ? LABEL_ROOM : 0;
		GraphRenderer.Transform t = GraphRenderer.fit(x, y, GraphRenderer.shown(loaded.graph, style),
				getWidth() - left - right, getHeight(), 48);
		return new GraphRenderer.Transform(t.centerX - (left - right) / 2.0 / t.scale, t.centerY, t.scale,
				getWidth(), getHeight());
	}

	/** Shows a run graph: its saved layout, or a layout computed here. */
	void setLoaded(RunGraphRecord.Loaded value)
	{
		if (value != null && loaded != null && value.graph == loaded.graph)
		{
			loaded = value;
			if (x == null && value.hasLayout())
			{
				x = value.x;
				y = value.y;
				fit();
			}
			return;
		}
		stopLayout();
		loaded = value;
		style.focus = -1;
		style.hover = -1;
		style.matches = null;
		x = null;
		y = null;
		transform = null;
		userMoved = false;
		if (value == null)
		{
			repaint();
			return;
		}
		if (value.hasLayout())
		{
			x = value.x;
			y = value.y;
			fit();
		}
		else
		{
			startLayout();
		}
		if (listener != null)
		{
			listener.selectionChanged(-1);
		}
		repaint();
	}

	/** Lays the graph out again from its seeded start (the layout is deterministic: same picture). */
	void relayout()
	{
		if (loaded == null)
		{
			return;
		}
		stopLayout();
		userMoved = false;
		startLayout();
	}

	private void startLayout()
	{
		LayoutWorker next = new LayoutWorker(loaded);
		worker = next;
		next.start();
		if (listener != null)
		{
			listener.layoutProgress(true, 0, false);
		}
	}

	void stopLayout()
	{
		LayoutWorker running = worker;
		if (running != null)
		{
			running.cancelled = true;
		}
		worker = null;
	}

	void fit()
	{
		if (x == null || getWidth() <= 0 || getHeight() <= 0)
		{
			transform = null;
			repaint();
			return;
		}
		transform = fitted();
		fitScale = transform.scale;
		userMoved = false;
		repaint();
	}

	void zoomBy(double factor)
	{
		zoomAt(factor, getWidth() / 2.0, getHeight() / 2.0);
	}

	private void zoomAt(double factor, double screenX, double screenY)
	{
		if (transform == null)
		{
			return;
		}
		double next = transform.scale * factor;
		if (next < fitScale * 0.25 || next > fitScale * 40.0)
		{
			return;
		}
		transform = transform.zoom(factor, screenX, screenY);
		userMoved = true;
		repaint();
	}

	/** Brings a node to the centre, zooming in when the view is far out. */
	void center(int node, boolean zoomIn)
	{
		if (x == null || node < 0 || transform == null)
		{
			return;
		}
		double scale = transform.scale;
		if (zoomIn && scale < fitScale * 2.5)
		{
			scale = fitScale * 2.5;
		}
		transform = new GraphRenderer.Transform(x[node], y[node], scale, getWidth(), getHeight());
		userMoved = true;
		repaint();
	}

	/** Selects a node (-1: none); its neighbourhood is emphasised. */
	void select(int node)
	{
		style.focus = node;
		repaint();
		if (listener != null)
		{
			listener.selectionChanged(node);
		}
	}

	int selected()
	{
		return style.focus;
	}

	private double zoomFactor()
	{
		if (transform == null || fitScale <= 0.0)
		{
			return 1.0;
		}
		return Math.max(0.7, Math.min(2.6, Math.sqrt(transform.scale / fitScale)));
	}

	private int nodeAt(int screenX, int screenY)
	{
		if (transform == null || loaded == null)
		{
			return -1;
		}
		String key = style.sizeBy + "|" + style.follows + style.recommendations + style.adoptions + style.hideIsolated
				+ "|" + style.focus;
		if (!key.equals(hitKey) || hitGraph != loaded.graph)
		{
			hitKey = key;
			hitGraph = loaded.graph;
			hitMetric = GraphRenderer.sizeMetric(loaded.analysis, style.sizeBy);
			hitShown = GraphRenderer.shown(loaded.graph, style);
		}
		return GraphRenderer.nodeAt(loaded.graph, x, y, hitMetric, hitShown, transform, zoomFactor(), screenX, screenY);
	}

	/** What hit-testing needs, cached between mouse moves (it changes with the style). */
	private String hitKey;
	private RunGraph hitGraph;
	private double[] hitMetric;
	private boolean[] hitShown;

	public String getToolTipText(MouseEvent event)
	{
		int node = nodeAt(event.getX(), event.getY());
		if (node < 0)
		{
			return null;
		}
		RunGraph graph = loaded.graph;
		RunGraphAnalysis analysis = loaded.analysis;
		int community = graph.community(node);
		StringBuilder text = new StringBuilder("<html><b>").append(RunGraphRecord.xml(graph.name(node))).append("</b><br>")
				.append(graph.kind(node).label).append(graph.isTarget(node) ? " · target" : "")
				.append(community >= 0 ? " · community " + RunGraphRecord.xml(graph.communityName(community)) : "")
				.append("<br>recommended to ").append(analysis.recommendedIn[node]).append(" · PageRank ")
				.append(String.format(java.util.Locale.US, "%.4f", analysis.pageRank[node]));
		return text.append("</html>").toString();
	}

	public void setBounds(int x, int y, int width, int height)
	{
		boolean resized = width != getWidth() || height != getHeight();
		super.setBounds(x, y, width, height);
		if (resized && this.x != null)
		{
			if (transform == null || !userMoved)
			{
				fit();
			}
			else
			{
				transform = transform.resize(width, height);
			}
		}
	}

	public Dimension getPreferredSize()
	{
		return new Dimension(900, 640);
	}

	protected void paintComponent(Graphics graphics)
	{
		Graphics2D g = (Graphics2D)graphics.create();
		NodeLabUi.prepare(g);
		style.night = NodeLabUi.night();
		g.setColor(GraphRenderer.background(style.night));
		g.fillRect(0, 0, getWidth(), getHeight());
		if (loaded == null)
		{
			g.dispose();
			return;
		}
		if (x == null)
		{
			g.setFont(NebulaDesign.fontMedium(13f));
			g.setColor(NebulaDesign.TEXT_TERTIARY);
			String text = "Laying out the graph…";
			g.drawString(text, (getWidth() - g.getFontMetrics().stringWidth(text)) / 2, getHeight() / 2);
			g.dispose();
			return;
		}
		if (transform == null)
		{
			transform = fitted();
			fitScale = transform.scale;
		}
		if (loaded.graph.allEdges().size() > 20000)
		{
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
		}
		GraphRenderer.paint(new GraphRenderer.Graphics2DCanvas(g), loaded.graph, loaded.analysis, x, y, style,
				transform, zoomFactor());
		if (legend)
		{
			GraphRenderer.legend(new GraphRenderer.Graphics2DCanvas(g), loaded.graph, loaded.analysis, style, 14, 14, 10);
		}
		g.dispose();
	}

	/** The current view as an image, {@code scale} times the canvas size (for sharp exports). */
	BufferedImage image(double scale)
	{
		int width = Math.max(1, getWidth());
		int height = Math.max(1, getHeight());
		BufferedImage image = new BufferedImage((int)(width * scale), (int)(height * scale), BufferedImage.TYPE_INT_RGB);
		Graphics2D g = image.createGraphics();
		g.scale(scale, scale);
		paintComponent(g);
		g.dispose();
		return image;
	}

	/** The current view as an SVG document. */
	String svg()
	{
		GraphRenderer.SvgCanvas canvas = new GraphRenderer.SvgCanvas();
		if (loaded != null && x != null && transform != null)
		{
			GraphRenderer.paint(canvas, loaded.graph, loaded.analysis, x, y, style, transform, zoomFactor());
			if (legend)
			{
				GraphRenderer.legend(canvas, loaded.graph, loaded.analysis, style, 14, 14, 10);
			}
		}
		return canvas.document(Math.max(1, getWidth()), Math.max(1, getHeight()), GraphRenderer.background(style.night),
				loaded == null ? "Run graph" : "Run graph of " + loaded.graph.algorithm + " on " + loaded.graph.dataset);
	}

	/** Runs ForceAtlas2 off the event thread, publishing positions as they settle. */
	private final class LayoutWorker extends Thread
	{
		private final RunGraphRecord.Loaded target;
		private final GraphLayout layout;
		volatile boolean cancelled;

		LayoutWorker(RunGraphRecord.Loaded target)
		{
			super("DSMP-Graph-Layout");
			setDaemon(true);
			this.target = target;
			this.layout = RunGraphRecord.start(target.graph);
		}

		public void run()
		{
			int budget = GraphLayout.budget(target.graph.size(), target.graph.allEdges().size());
			long published = 0L;
			try
			{
				while (!cancelled && !layout.isConverged() && layout.iterations() < budget)
				{
					layout.step();
					long now = System.currentTimeMillis();
					if (now - published > 40L)
					{
						published = now;
						publish(false);
					}
				}
				if (!cancelled)
				{
					RunGraphRecord.finish(layout, target.graph, target.analysis);
					publish(true);
				}
			}
			catch (RuntimeException failure)
			{
				System.err.println("The graph layout failed: " + failure);
			}
		}

		private void publish(final boolean done)
		{
			final double[][] positions = layout.positions();
			final int iterations = layout.iterations();
			final boolean converged = layout.isConverged();
			SwingUtilities.invokeLater(new Runnable()
			{
				public void run()
				{
					if (worker != LayoutWorker.this || loaded == null || loaded.graph != target.graph)
					{
						return;
					}
					x = positions[0];
					y = positions[1];
					if (!userMoved || transform == null)
					{
						transform = fitted();
						fitScale = transform.scale;
					}
					repaint();
					if (done)
					{
						worker = null;
						loaded = loaded.withLayout(x, y);
					}
					if (listener != null)
					{
						listener.layoutProgress(!done, iterations, converged);
						if (done)
						{
							listener.layoutFinished(loaded);
						}
					}
				}
			});
		}
	}
}
