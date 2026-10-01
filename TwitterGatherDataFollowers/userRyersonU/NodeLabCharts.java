package TwitterGatherDataFollowers.userRyersonU;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.geom.QuadCurve2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import javax.swing.JComponent;

/**
 * The Node Lab's charts, painted directly in the Nebula tokens so they follow
 * the day and night themes: the cluster topology, the execution timeline
 * (Gantt), horizontal bars with confidence intervals, the stage bar and a
 * small line chart.
 */
final class NodeLabCharts
{
	private NodeLabCharts()
	{
	}

	// ------------------------------------------------------------ topology

	/** What the topology shows about one node. */
	static final class NodeView
	{
		final int node;
		final String role;
		final double speed;
		final int lanes;
		final int latencyMs;
		final double bandwidthMbps;
		String state = "active";
		double activity = Double.NaN;
		boolean crashes;
		boolean faults;

		NodeView(int node, String role, double speed, int lanes, int latencyMs, double bandwidthMbps)
		{
			this.node = node;
			this.role = role;
			this.speed = speed;
			this.lanes = lanes;
			this.latencyMs = latencyMs;
			this.bandwidthMbps = bandwidthMbps;
		}

		static NodeView of(NodeCapacityProfile profile)
		{
			NodeView view = new NodeView(profile.getNodeIndex(), profile.getRoleLabel(), profile.effectiveSpeed(),
					profile.getMaxConcurrentJobs(), profile.getNetworkDelayMs(), profile.getBandwidthMbps());
			view.crashes = profile.hasCrashProcess();
			view.faults = profile.isFailureSimulationEnabled() && profile.getFailureProbabilityPercent() > 0;
			return view;
		}
	}

	/** Told when a node of the topology is clicked (0 for the coordinator). */
	interface NodeClickListener
	{
		void nodeClicked(int node);
	}

	/**
	 * The cluster as a star network: the coordinator in the centre, every node
	 * around it. A node's disc grows with its throughput (speed x lanes); a
	 * link's thickness grows with its bandwidth and its colour warms with its
	 * latency. State badges show paused, unavailable, draining and crashed
	 * nodes; an arc shows live activity.
	 */
	static final class TopologyView extends JComponent
	{
		private static final long serialVersionUID = 1L;
		private List<NodeView> nodes = Collections.emptyList();
		private double coordinatorSpeed = 1.0;
		private int selected;
		private int hover = -1;
		private NodeClickListener listener;
		private final ArrayList<Shape> hitAreas = new ArrayList<Shape>();

		TopologyView()
		{
			setOpaque(false);
			setToolTipText("");
			MouseAdapter mouse = new MouseAdapter()
			{
				public void mouseMoved(MouseEvent event)
				{
					int index = hit(event.getX(), event.getY());
					if (index != hover)
					{
						hover = index;
						setCursor(Cursor.getPredefinedCursor(index >= 0 ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
						repaint();
					}
				}

				public void mouseExited(MouseEvent event)
				{
					hover = -1;
					repaint();
				}

				public void mouseClicked(MouseEvent event)
				{
					int index = hit(event.getX(), event.getY());
					if (index >= 0 && listener != null)
					{
						listener.nodeClicked(nodes.get(index).node);
					}
				}
			};
			addMouseListener(mouse);
			addMouseMotionListener(mouse);
		}

		void setNodes(List<NodeView> value, double coordinator)
		{
			nodes = value == null ? Collections.<NodeView>emptyList() : new ArrayList<NodeView>(value);
			coordinatorSpeed = coordinator;
			repaint();
		}

		void setSelected(int node)
		{
			selected = node;
			repaint();
		}

		void setListener(NodeClickListener value)
		{
			listener = value;
		}

		private int hit(int x, int y)
		{
			for (int index = 0; index < hitAreas.size() && index < nodes.size(); index++)
			{
				if (hitAreas.get(index).contains(x, y))
				{
					return index;
				}
			}
			return -1;
		}

		public String getToolTipText(MouseEvent event)
		{
			int index = hit(event.getX(), event.getY());
			if (index < 0)
			{
				return null;
			}
			NodeView view = nodes.get(index);
			return String.format(Locale.US, "Node %d (%s): speed %.2f, %d lane(s), link %d ms / %s, %s",
					view.node, view.role, view.speed, view.lanes, view.latencyMs,
					NodeCapacityProfile.formatBandwidth(view.bandwidthMbps), view.state);
		}

		public Dimension getPreferredSize()
		{
			return new Dimension(420, 330);
		}

		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D)graphics.create();
			NodeLabUi.prepare(g);
			int w = getWidth();
			int h = getHeight();
			double cx = w / 2.0;
			double cy = h / 2.0;
			int count = nodes.size();
			double maxThroughput = 1.0;
			for (NodeView view : nodes)
			{
				maxThroughput = Math.max(maxThroughput, view.speed * view.lanes);
			}
			double orbitX = Math.max(60, w / 2.0 - 70);
			double orbitY = Math.max(50, h / 2.0 - 58);
			ArrayList<Point2D.Double> centres = new ArrayList<Point2D.Double>();
			for (int index = 0; index < count; index++)
			{
				double angle = -Math.PI / 2.0 + 2.0 * Math.PI * index / Math.max(1, count);
				centres.add(new Point2D.Double(cx + orbitX * Math.cos(angle), cy + orbitY * Math.sin(angle)));
			}
			//Links first, under the discs.
			Font small = NebulaDesign.fontMedium(10.5f);
			g.setFont(small);
			FontMetrics metrics = g.getFontMetrics();
			for (int index = 0; index < count; index++)
			{
				NodeView view = nodes.get(index);
				Point2D.Double centre = centres.get(index);
				double bandwidth = view.bandwidthMbps <= 0.0 ? 100000.0 : view.bandwidthMbps;
				float thickness = (float)Math.max(1.5, Math.min(7.0, 1.0 + Math.log10(bandwidth) * 1.2));
				Color linkColor = latencyColor(view.latencyMs);
				boolean down = "down".equals(view.state);
				g.setColor(NodeLabUi.alpha(linkColor, down ? 60 : 150));
				float[] dash = down || view.bandwidthMbps <= 0.0 ? new float[] {6f, 6f} : null;
				g.setStroke(new BasicStroke(thickness, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10f, dash, 0f));
				double bend = (index % 2 == 0 ? 1 : -1) * 16.0;
				double mx = (cx + centre.x) / 2.0 + bend * (centre.y - cy) / Math.max(1.0, Math.hypot(centre.x - cx,
						centre.y - cy));
				double my = (cy + centre.y) / 2.0 - bend * (centre.x - cx) / Math.max(1.0, Math.hypot(centre.x - cx,
						centre.y - cy));
				g.draw(new QuadCurve2D.Double(cx, cy, mx, my, centre.x, centre.y));
				String label = view.latencyMs + " ms · " + shortBandwidth(view.bandwidthMbps);
				int labelWidth = metrics.stringWidth(label) + 12;
				double lx = mx - labelWidth / 2.0;
				double ly = my - 9;
				g.setColor(NebulaDesign.SURFACE);
				g.fill(new RoundRectangle2D.Double(lx, ly, labelWidth, 18, 18, 18));
				g.setColor(NodeLabUi.alpha(linkColor, 170));
				g.setStroke(new BasicStroke(1f));
				g.draw(new RoundRectangle2D.Double(lx + 0.5, ly + 0.5, labelWidth - 1, 17, 17, 17));
				g.setColor(NebulaDesign.TEXT_SECONDARY);
				g.drawString(label, (float)(lx + 6), (float)(ly + 13));
			}
			//Coordinator.
			double hubSize = 58;
			NodeLabUi.paintSurface(g, (int)(cx - hubSize / 2), (int)(cy - hubSize / 2), (int)hubSize, (int)hubSize, 18,
					NebulaDesign.SURFACE_RAISED, true);
			g.setColor(NebulaDesign.ACCENT);
			Graphics2D icon = (Graphics2D)g.create();
			icon.translate(cx - 10, cy - 16);
			NodeLabUi.paintGlyph(icon, NodeLabUi.Glyph.CLUSTER, 20);
			icon.dispose();
			g.setFont(NebulaDesign.fontSemiBold(9.5f));
			metrics = g.getFontMetrics();
			String hubLabel = String.format(Locale.US, "HUB %.1f×", coordinatorSpeed);
			g.setColor(NebulaDesign.TEXT_SECONDARY);
			g.drawString(hubLabel, (float)(cx - metrics.stringWidth(hubLabel) / 2.0), (float)(cy + 19));
			//Nodes.
			hitAreas.clear();
			for (int index = 0; index < count; index++)
			{
				NodeView view = nodes.get(index);
				Point2D.Double centre = centres.get(index);
				double throughput = view.speed * view.lanes;
				double radius = 18 + 16 * Math.sqrt(throughput / maxThroughput);
				Color color = NodeLabUi.nodeColor(view.node);
				boolean down = "down".equals(view.state);
				boolean muted = down || "unavailable".equals(view.state) || "draining".equals(view.state);
				Ellipse2D.Double disc = new Ellipse2D.Double(centre.x - radius, centre.y - radius, radius * 2, radius * 2);
				hitAreas.add(disc);
				if (view.node == selected || index == hover)
				{
					g.setColor(NodeLabUi.alpha(color, view.node == selected ? 70 : 40));
					g.fill(new Ellipse2D.Double(centre.x - radius - 7, centre.y - radius - 7, radius * 2 + 14,
							radius * 2 + 14));
				}
				g.setColor(NodeLabUi.alpha(java.awt.Color.BLACK, NodeLabUi.night() ? 80 : 18));
				g.fill(new Ellipse2D.Double(disc.x, disc.y + 2, disc.width, disc.height));
				g.setColor(NebulaDesign.SURFACE);
				g.fill(disc);
				g.setColor(NodeLabUi.alpha(color, muted ? 40 : (NodeLabUi.night() ? 70 : 48)));
				g.fill(disc);
				g.setColor(muted ? NodeLabUi.alpha(color, 110) : color);
				g.setStroke(new BasicStroke(2.4f));
				g.draw(disc);
				if (!Double.isNaN(view.activity) && view.activity > 0.0)
				{
					g.setColor(NodeLabUi.tone(NodeLabUi.Tone.SUCCESS));
					g.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
					g.draw(new java.awt.geom.Arc2D.Double(centre.x - radius - 5, centre.y - radius - 5,
							radius * 2 + 10, radius * 2 + 10, 90, -360.0 * Math.min(1.0, view.activity),
							java.awt.geom.Arc2D.OPEN));
				}
				g.setFont(NebulaDesign.fontSemiBold(13f));
				metrics = g.getFontMetrics();
				String name = "N" + view.node;
				g.setColor(NebulaDesign.TEXT_PRIMARY);
				g.drawString(name, (float)(centre.x - metrics.stringWidth(name) / 2.0), (float)(centre.y + 1));
				g.setFont(NebulaDesign.fontMedium(10f));
				metrics = g.getFontMetrics();
				String speed = String.format(Locale.US, "%.2g×%s", view.speed, view.lanes > 1 ? " ·" + view.lanes : "");
				g.setColor(NebulaDesign.TEXT_SECONDARY);
				g.drawString(speed, (float)(centre.x - metrics.stringWidth(speed) / 2.0),
						(float)(centre.y + 13));
				String role = view.role;
				g.setFont(NebulaDesign.fontMedium(10.5f));
				metrics = g.getFontMetrics();
				g.setColor(NebulaDesign.TEXT_TERTIARY);
				g.drawString(role, (float)(centre.x - metrics.stringWidth(role) / 2.0),
						(float)(centre.y + radius + 15));
				paintBadges(g, view, centre.x + radius * 0.72, centre.y - radius * 0.72);
			}
			if (count == 0)
			{
				g.setFont(NebulaDesign.fontMedium(12f));
				metrics = g.getFontMetrics();
				String empty = "No nodes";
				g.setColor(NebulaDesign.TEXT_TERTIARY);
				g.drawString(empty, (float)(cx - metrics.stringWidth(empty) / 2.0), (float)(cy + 50));
			}
			g.dispose();
		}

		private void paintBadges(Graphics2D g, NodeView view, double x, double y)
		{
			NodeLabUi.Glyph glyph = null;
			Color tone = null;
			if ("down".equals(view.state))
			{
				glyph = NodeLabUi.Glyph.CRASH;
				tone = NebulaDesign.DANGER;
			}
			else if ("paused".equals(view.state))
			{
				glyph = NodeLabUi.Glyph.PAUSE;
				tone = NebulaDesign.WARNING;
			}
			else if ("draining".equals(view.state))
			{
				glyph = NodeLabUi.Glyph.DRAIN;
				tone = NebulaDesign.WARNING;
			}
			else if ("unavailable".equals(view.state))
			{
				glyph = NodeLabUi.Glyph.CROSS;
				tone = NebulaDesign.TEXT_SECONDARY;
			}
			else if (view.crashes || view.faults)
			{
				glyph = NodeLabUi.Glyph.WARNING;
				tone = NebulaDesign.WARNING;
			}
			if (glyph == null)
			{
				return;
			}
			g.setColor(NebulaDesign.SURFACE);
			g.fill(new Ellipse2D.Double(x - 11, y - 11, 22, 22));
			g.setColor(tone);
			g.setStroke(new BasicStroke(1.4f));
			g.draw(new Ellipse2D.Double(x - 11, y - 11, 22, 22));
			Graphics2D icon = (Graphics2D)g.create();
			icon.translate(x - 7, y - 7);
			NodeLabUi.paintGlyph(icon, glyph, 14);
			icon.dispose();
		}
	}

	static Color latencyColor(int latencyMs)
	{
		if (latencyMs <= 2)
		{
			return NebulaDesign.SUCCESS;
		}
		if (latencyMs <= 30)
		{
			return NebulaDesign.INFO;
		}
		if (latencyMs <= 120)
		{
			return NebulaDesign.WARNING;
		}
		return NebulaDesign.DANGER;
	}

	static String shortBandwidth(double mbps)
	{
		if (mbps <= 0.0)
		{
			return "∞";
		}
		if (mbps >= 1000.0)
		{
			return String.format(Locale.US, "%.3g Gb/s", mbps / 1000.0).replace(".00", "");
		}
		return String.format(Locale.US, "%.3g Mb/s", mbps);
	}

	// ------------------------------------------------------------ gantt

	/** One bar of the timeline. */
	static final class Bar
	{
		final int row;
		final double start;
		final double end;
		final Color color;
		final String outcome;
		final String label;

		Bar(int row, double start, double end, Color color, String outcome, String label)
		{
			this.row = row;
			this.start = start;
			this.end = end;
			this.color = color;
			this.outcome = outcome == null ? "committed" : outcome;
			this.label = label == null ? "" : label;
		}
	}

	/** A vertical marker at a time (a control, a crash, a milestone). */
	static final class Marker
	{
		final double time;
		final String label;
		final Color color;
		final int row;

		Marker(double time, String label, Color color, int row)
		{
			this.time = time;
			this.label = label == null ? "" : label;
			this.color = color;
			this.row = row;
		}
	}

	/** A shaded interval on one row (a node's downtime) or across all rows (row -1). */
	static final class Band
	{
		final int row;
		final double start;
		final double end;
		final Color color;
		final String label;

		Band(int row, double start, double end, Color color, String label)
		{
			this.row = row;
			this.start = start;
			this.end = end;
			this.color = color;
			this.label = label == null ? "" : label;
		}
	}

	/**
	 * A timeline of unit executions: one row per lane, bars coloured by node
	 * and outcome (committed solid; transient faults, revocations and crash
	 * losses hatched), markers for controls and faults, shaded downtime.
	 */
	static final class GanttView extends JComponent
	{
		private static final long serialVersionUID = 1L;
		private List<String> rows = Collections.emptyList();
		private List<Color> rowColors = Collections.emptyList();
		private List<Bar> bars = Collections.emptyList();
		private List<Marker> markers = Collections.emptyList();
		private List<Band> bands = Collections.emptyList();
		private double from;
		private double to = 1.0;
		private String emptyText = "No executions yet";
		private static final int LABEL_WIDTH = 92;
		private static final int ROW_HEIGHT = 22;
		private static final int AXIS_HEIGHT = 26;
		private static final int TOP = 20;

		GanttView()
		{
			setOpaque(false);
			setToolTipText("");
		}

		void set(List<String> rowNames, List<Color> colors, List<Bar> newBars, List<Marker> newMarkers,
				List<Band> newBands, double start, double end)
		{
			rows = rowNames == null ? Collections.<String>emptyList() : rowNames;
			rowColors = colors == null ? Collections.<Color>emptyList() : colors;
			bars = newBars == null ? Collections.<Bar>emptyList() : newBars;
			markers = newMarkers == null ? Collections.<Marker>emptyList() : newMarkers;
			bands = newBands == null ? Collections.<Band>emptyList() : newBands;
			from = start;
			to = Math.max(start + 1e-6, end);
			revalidate();
			repaint();
		}

		void setEmptyText(String text)
		{
			emptyText = text;
			repaint();
		}

		public Dimension getPreferredSize()
		{
			return new Dimension(600, Math.max(140, TOP + AXIS_HEIGHT + rows.size() * ROW_HEIGHT + 12));
		}

		private double x(double time, int width)
		{
			return LABEL_WIDTH + (time - from) / (to - from) * (width - LABEL_WIDTH - 14);
		}

		public String getToolTipText(MouseEvent event)
		{
			int w = getWidth();
			for (Bar bar : bars)
			{
				double x0 = x(bar.start, w);
				double x1 = Math.max(x0 + 2, x(bar.end, w));
				double y0 = TOP + bar.row * ROW_HEIGHT + 3;
				if (event.getX() >= x0 && event.getX() <= x1 && event.getY() >= y0 && event.getY() <= y0 + ROW_HEIGHT - 6)
				{
					return String.format(Locale.US, "%s — %s, %s → %s (%s)", bar.label, bar.outcome,
							NodeLabUi.duration(bar.start), NodeLabUi.duration(bar.end),
							NodeLabUi.duration(bar.end - bar.start));
				}
			}
			for (Marker marker : markers)
			{
				if (Math.abs(event.getX() - x(marker.time, w)) <= 3)
				{
					return NodeLabUi.duration(marker.time) + ": " + marker.label;
				}
			}
			return null;
		}

		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D)graphics.create();
			NodeLabUi.prepare(g);
			int w = getWidth();
			int h = getHeight();
			if (rows.isEmpty())
			{
				g.setFont(NebulaDesign.fontMedium(12f));
				FontMetrics metrics = g.getFontMetrics();
				g.setColor(NebulaDesign.TEXT_TERTIARY);
				g.drawString(emptyText, (w - metrics.stringWidth(emptyText)) / 2, h / 2);
				g.dispose();
				return;
			}
			int plotBottom = TOP + rows.size() * ROW_HEIGHT;
			//Row stripes and labels.
			g.setFont(NebulaDesign.fontMedium(10.5f));
			FontMetrics metrics = g.getFontMetrics();
			for (int row = 0; row < rows.size(); row++)
			{
				int y = TOP + row * ROW_HEIGHT;
				if (row % 2 == 0)
				{
					g.setColor(NodeLabUi.alpha(NebulaDesign.INSET, 140));
					g.fill(new Rectangle2D.Double(LABEL_WIDTH, y, w - LABEL_WIDTH - 14, ROW_HEIGHT));
				}
				Color color = row < rowColors.size() ? rowColors.get(row) : NebulaDesign.TEXT_SECONDARY;
				g.setColor(color);
				g.fill(new RoundRectangle2D.Double(6, y + ROW_HEIGHT / 2.0 - 4, 8, 8, 3, 3));
				g.setColor(NebulaDesign.TEXT_SECONDARY);
				g.drawString(rows.get(row), 20, y + (ROW_HEIGHT + metrics.getAscent()) / 2 - 1);
			}
			//Axis ticks and grid.
			double[] ticks = niceTicks(from, to, Math.max(2, (w - LABEL_WIDTH) / 110));
			g.setFont(NebulaDesign.fontMedium(10f));
			metrics = g.getFontMetrics();
			for (double tick : ticks)
			{
				double x = x(tick, w);
				g.setColor(NodeLabUi.alpha(NebulaDesign.BORDER_SUBTLE, 200));
				g.setStroke(new BasicStroke(1f));
				g.draw(new Line2D.Double(x, TOP, x, plotBottom));
				String label = NodeLabUi.duration(tick - from);
				g.setColor(NebulaDesign.TEXT_TERTIARY);
				g.drawString(label, (float)(x - metrics.stringWidth(label) / 2.0), plotBottom + 16);
			}
			//Bands.
			for (Band band : bands)
			{
				double x0 = x(band.start, w);
				double x1 = x(band.end, w);
				double y0 = band.row < 0 ? TOP : TOP + band.row * ROW_HEIGHT;
				double height = band.row < 0 ? rows.size() * ROW_HEIGHT : ROW_HEIGHT;
				g.setColor(band.color);
				g.fill(new Rectangle2D.Double(x0, y0, Math.max(1, x1 - x0), height));
			}
			//Bars.
			for (Bar bar : bars)
			{
				double x0 = x(bar.start, w);
				double x1 = Math.max(x0 + 1.5, x(bar.end, w));
				double y0 = TOP + bar.row * ROW_HEIGHT + 4;
				double height = ROW_HEIGHT - 8;
				RoundRectangle2D.Double shape = new RoundRectangle2D.Double(x0, y0, x1 - x0, height, 5, 5);
				boolean committed = "committed".equals(bar.outcome);
				if (committed)
				{
					g.setColor(bar.color);
					g.fill(shape);
				}
				else
				{
					Color tone = "fault".equals(bar.outcome) || "error".equals(bar.outcome)
							? NebulaDesign.DANGER : NebulaDesign.TEXT_TERTIARY;
					g.setColor(NodeLabUi.alpha(tone, 60));
					g.fill(shape);
					Graphics2D hatch = (Graphics2D)g.create();
					hatch.clip(shape);
					hatch.setColor(NodeLabUi.alpha(tone, 160));
					hatch.setStroke(new BasicStroke(1.2f));
					for (double offset = x0 - height; offset < x1; offset += 5)
					{
						hatch.draw(new Line2D.Double(offset, y0 + height, offset + height, y0));
					}
					hatch.dispose();
				}
			}
			//Markers.
			g.setFont(NebulaDesign.fontSemiBold(9.5f));
			metrics = g.getFontMetrics();
			double lastLabelEnd = -1e9;
			for (Marker marker : markers)
			{
				double x = x(marker.time, w);
				g.setColor(marker.color);
				g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10f,
						new float[] {4f, 3f}, 0f));
				double y0 = marker.row < 0 ? TOP - 4 : TOP + marker.row * ROW_HEIGHT;
				double y1 = marker.row < 0 ? plotBottom : TOP + (marker.row + 1) * ROW_HEIGHT;
				g.draw(new Line2D.Double(x, y0, x, y1));
				g.fill(new Ellipse2D.Double(x - 3, y0 - 3, 6, 6));
				if (marker.label.length() > 0 && x > lastLabelEnd + 6)
				{
					g.drawString(marker.label, (float)(x + 4), TOP - 8);
					lastLabelEnd = x + 4 + metrics.stringWidth(marker.label);
				}
			}
			g.dispose();
		}
	}

	/**
	 * Timeline data from a run's records (live or loaded from disk): one row
	 * per lane, a bar per execution, markers for controls and phase openings,
	 * and shaded downtime between each crash and its repair.
	 */
	static final class GanttData
	{
		final List<String> rows = new ArrayList<String>();
		final List<Color> colors = new ArrayList<Color>();
		final List<Bar> bars = new ArrayList<Bar>();
		final List<Marker> markers = new ArrayList<Marker>();
		final List<Band> bands = new ArrayList<Band>();
		double start = Double.NaN;
		double end = Double.NaN;

		/**
		 * @param phaseKey only this phase's executions (null: every phase)
		 * @param maxBars  only the latest executions beyond this many
		 */
		static GanttData of(List<NodeCapacityProfile> profiles, List<RunRecorder.UnitExecution> executions,
				List<RunRecorder.Control> controls, List<RunRecorder.Phase> phases, int maxBars)
		{
			return of(profiles, executions, controls, phases, maxBars, null);
		}

		static GanttData of(List<NodeCapacityProfile> profiles, List<RunRecorder.UnitExecution> executions,
				List<RunRecorder.Control> controls, List<RunRecorder.Phase> phases, int maxBars, String phaseKey)
		{
			GanttData data = new GanttData();
			java.util.TreeMap<Integer,Integer> lanes = new java.util.TreeMap<Integer,Integer>();
			for (NodeCapacityProfile profile : profiles)
			{
				lanes.put(Integer.valueOf(profile.getNodeIndex()), Integer.valueOf(profile.getMaxConcurrentJobs()));
			}
			ArrayList<RunRecorder.UnitExecution> shown = new ArrayList<RunRecorder.UnitExecution>();
			for (RunRecorder.UnitExecution execution : executions)
			{
				if (phaseKey == null || phaseKey.equals(execution.phase))
				{
					shown.add(execution);
				}
			}
			if (shown.size() > maxBars)
			{
				shown = new ArrayList<RunRecorder.UnitExecution>(shown.subList(shown.size() - maxBars, shown.size()));
			}
			for (RunRecorder.UnitExecution execution : shown)
			{
				Integer known = lanes.get(Integer.valueOf(execution.node));
				int needed = Math.max(1, execution.lane + 1);
				if (known == null || known.intValue() < needed)
				{
					lanes.put(Integer.valueOf(execution.node), Integer.valueOf(Math.max(needed,
							known == null ? 1 : known.intValue())));
				}
			}
			java.util.HashMap<Integer,Integer> firstRow = new java.util.HashMap<Integer,Integer>();
			for (java.util.Map.Entry<Integer,Integer> node : lanes.entrySet())
			{
				firstRow.put(node.getKey(), Integer.valueOf(data.rows.size()));
				int count = node.getValue().intValue();
				for (int lane = 0; lane < count; lane++)
				{
					data.rows.add(count == 1 ? "Node " + node.getKey() : "N" + node.getKey() + " · lane " + (lane + 1));
					data.colors.add(NodeLabUi.nodeColor(node.getKey().intValue()));
				}
			}
			double first = Double.POSITIVE_INFINITY;
			double last = Double.NEGATIVE_INFINITY;
			for (RunRecorder.UnitExecution execution : shown)
			{
				Integer row = firstRow.get(Integer.valueOf(execution.node));
				if (row == null || Double.isNaN(execution.startAt) || Double.isNaN(execution.endAt))
				{
					continue;
				}
				data.bars.add(new Bar(row.intValue() + Math.max(0, execution.lane), execution.startAt, execution.endAt,
						NodeLabUi.nodeColor(execution.node), execution.outcome,
						Scenario.phaseAlias(execution.phase) + " · " + execution.key + " · node " + execution.node
								+ (execution.execution > 1 ? " · attempt " + execution.execution : "")));
				first = Math.min(first, execution.startAt);
				last = Math.max(last, execution.endAt);
			}
			for (RunRecorder.Phase phase : phases)
			{
				if (phaseKey != null && !phaseKey.equals(phase.key))
				{
					continue;
				}
				if (!Double.isNaN(phase.openedAt))
				{
					data.markers.add(new Marker(phase.openedAt, Scenario.phaseAlias(phase.key) + " opens",
							NebulaDesign.TEXT_TERTIARY, -1));
					first = Math.min(first, phase.openedAt);
				}
				if (!Double.isNaN(phase.completedAt))
				{
					last = Math.max(last, phase.completedAt);
				}
			}
			java.util.HashMap<Integer,Double> downSince = new java.util.HashMap<Integer,Double>();
			for (RunRecorder.Control control : controls)
			{
				Integer row = firstRow.get(Integer.valueOf(control.node));
				int count = row == null ? 0 : lanes.get(Integer.valueOf(control.node)).intValue();
				if (control.kind == Scenario.ActionKind.CRASH)
				{
					downSince.put(Integer.valueOf(control.node), Double.valueOf(control.at));
				}
				else if (control.kind == Scenario.ActionKind.RECOVER)
				{
					Double since = downSince.remove(Integer.valueOf(control.node));
					if (since != null && row != null)
					{
						for (int lane = 0; lane < count; lane++)
						{
							data.bands.add(new Band(row.intValue() + lane, since.doubleValue(), control.at,
									NodeLabUi.alpha(NebulaDesign.DANGER, 34), "down"));
						}
					}
				}
				String label = control.kind.name().toLowerCase(java.util.Locale.US)
						+ (control.node > 0 ? " N" + control.node : "")
						+ (control.policy != null && control.kind == Scenario.ActionKind.POLICY
								? " → " + control.policy.getLabel() : "");
				data.markers.add(new Marker(control.at, label, NodeLabUi.tone(NodeLabPlanPage.toneOf(control.kind)),
						-1));
			}
			data.start = first == Double.POSITIVE_INFINITY ? 0.0 : first;
			data.end = last == Double.NEGATIVE_INFINITY ? data.start + 1.0 : last;
			for (java.util.Map.Entry<Integer,Double> open : downSince.entrySet())
			{
				Integer row = firstRow.get(open.getKey());
				if (row == null)
				{
					continue;
				}
				int count = lanes.get(open.getKey()).intValue();
				for (int lane = 0; lane < count; lane++)
				{
					data.bands.add(new Band(row.intValue() + lane, open.getValue().doubleValue(),
							Math.max(data.end, open.getValue().doubleValue()), NodeLabUi.alpha(NebulaDesign.DANGER, 34),
							"down"));
				}
			}
			return data;
		}
	}

	/** About {@code count} round tick values between from and to. */
	static double[] niceTicks(double from, double to, int count)
	{
		double span = Math.max(1e-9, to - from);
		double raw = span / Math.max(1, count);
		double magnitude = Math.pow(10, Math.floor(Math.log10(raw)));
		double[] steps = {1, 2, 2.5, 5, 10};
		double step = magnitude;
		for (double candidate : steps)
		{
			if (candidate * magnitude >= raw)
			{
				step = candidate * magnitude;
				break;
			}
		}
		ArrayList<Double> ticks = new ArrayList<Double>();
		double first = Math.ceil((from - from) / step) * step + from;
		for (double tick = first; tick <= to + step * 1e-9; tick += step)
		{
			ticks.add(Double.valueOf(tick));
			if (ticks.size() > 50)
			{
				break;
			}
		}
		double[] values = new double[ticks.size()];
		for (int index = 0; index < values.length; index++)
		{
			values[index] = ticks.get(index).doubleValue();
		}
		return values;
	}

	// ------------------------------------------------------------ bars

	/** One horizontal bar: value with an optional confidence interval. */
	static final class BarValue
	{
		final String label;
		final double value;
		final double low;
		final double high;
		final Color color;
		final String note;
		final boolean highlight;

		BarValue(String label, double value, double low, double high, Color color, String note, boolean highlight)
		{
			this.label = label;
			this.value = value;
			this.low = low;
			this.high = high;
			this.color = color;
			this.note = note == null ? "" : note;
			this.highlight = highlight;
		}
	}

	/**
	 * Horizontal bars with value labels and optional 95% confidence whiskers;
	 * an optional reference line (a lower bound, a baseline).
	 */
	static final class BarChart extends JComponent
	{
		private static final long serialVersionUID = 1L;
		private List<BarValue> values = Collections.emptyList();
		private double reference = Double.NaN;
		private String referenceLabel = "";
		private NodeLabUi.Formatter formatter;
		private String emptyText = "No data yet";
		private static final int ROW = 30;

		BarChart()
		{
			setOpaque(false);
		}

		void set(List<BarValue> newValues, double referenceValue, String referenceText, NodeLabUi.Formatter format)
		{
			values = newValues == null ? Collections.<BarValue>emptyList() : newValues;
			reference = referenceValue;
			referenceLabel = referenceText == null ? "" : referenceText;
			formatter = format;
			revalidate();
			repaint();
		}

		void setEmptyText(String text)
		{
			emptyText = text;
		}

		public Dimension getPreferredSize()
		{
			return new Dimension(420, Math.max(90, values.size() * ROW + 44));
		}

		private String format(double value)
		{
			return formatter == null ? NodeLabUi.number(value) : formatter.format(value);
		}

		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D)graphics.create();
			NodeLabUi.prepare(g);
			int w = getWidth();
			int h = getHeight();
			if (values.isEmpty())
			{
				g.setFont(NebulaDesign.fontMedium(12f));
				FontMetrics metrics = g.getFontMetrics();
				g.setColor(NebulaDesign.TEXT_TERTIARY);
				g.drawString(emptyText, (w - metrics.stringWidth(emptyText)) / 2, h / 2);
				g.dispose();
				return;
			}
			g.setFont(NebulaDesign.fontMedium(12f));
			FontMetrics metrics = g.getFontMetrics();
			int labelWidth = 0;
			double max = Double.isNaN(reference) ? 0.0 : reference;
			for (BarValue value : values)
			{
				labelWidth = Math.max(labelWidth, metrics.stringWidth(value.label));
				max = Math.max(max, Double.isNaN(value.high) ? value.value : Math.max(value.value, value.high));
			}
			labelWidth = Math.min(labelWidth + 16, w / 3);
			int valueWidth = 110;
			int left = labelWidth;
			int right = w - valueWidth;
			double scale = max <= 0.0 ? 1.0 : (right - left) / (max * 1.05);
			int y = 10;
			for (BarValue value : values)
			{
				String label = value.label;
				while (metrics.stringWidth(label) > labelWidth - 12 && label.length() > 4)
				{
					label = label.substring(0, label.length() - 2);
				}
				if (!label.equals(value.label))
				{
					label = label + "…";
				}
				g.setFont(value.highlight ? NebulaDesign.fontSemiBold(12f) : NebulaDesign.fontMedium(12f));
				g.setColor(value.highlight ? NebulaDesign.TEXT_PRIMARY : NebulaDesign.TEXT_SECONDARY);
				g.drawString(label, 0, y + ROW / 2 + 4);
				g.setColor(NebulaDesign.INSET);
				g.fill(new RoundRectangle2D.Double(left, y + 6, right - left, ROW - 12, 8, 8));
				if (!Double.isNaN(value.value))
				{
					double length = Math.max(3, value.value * scale);
					Color color = value.color == null ? NebulaDesign.ACCENT : value.color;
					g.setColor(value.highlight ? color : NodeLabUi.alpha(color, 200));
					g.fill(new RoundRectangle2D.Double(left, y + 6, length, ROW - 12, 8, 8));
					if (!Double.isNaN(value.low) && !Double.isNaN(value.high))
					{
						double lx = left + value.low * scale;
						double hx = left + value.high * scale;
						double cy = y + ROW / 2.0;
						g.setColor(NebulaDesign.TEXT_PRIMARY);
						g.setStroke(new BasicStroke(1.4f));
						g.draw(new Line2D.Double(lx, cy, hx, cy));
						g.draw(new Line2D.Double(lx, cy - 5, lx, cy + 5));
						g.draw(new Line2D.Double(hx, cy - 5, hx, cy + 5));
					}
				}
				g.setFont(NebulaDesign.fontSemiBold(12f));
				g.setColor(NebulaDesign.TEXT_PRIMARY);
				String text = Double.isNaN(value.value) ? "—" : format(value.value);
				g.drawString(text, right + 10, y + ROW / 2 + 4);
				if (value.note.length() > 0)
				{
					g.setFont(NebulaDesign.fontMedium(10.5f));
					g.setColor(NebulaDesign.TEXT_TERTIARY);
					g.drawString(value.note, right + 12 + g.getFontMetrics(NebulaDesign.fontSemiBold(12f))
							.stringWidth(text), y + ROW / 2 + 4);
				}
				y += ROW;
			}
			if (!Double.isNaN(reference) && reference > 0.0)
			{
				double x = left + reference * scale;
				g.setColor(NebulaDesign.SUCCESS);
				g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10f,
						new float[] {5f, 4f}, 0f));
				g.draw(new Line2D.Double(x, 4, x, y + 2));
				g.setFont(NebulaDesign.fontSemiBold(10f));
				g.drawString(referenceLabel, (float)Math.min(x + 4, right - 80), y + 16);
			}
			g.dispose();
		}
	}

	// ------------------------------------------------------------ stage bar

	/** A run's stages as one proportional bar with a legend. */
	static final class StageBar extends JComponent
	{
		private static final long serialVersionUID = 1L;
		private final ArrayList<String> names = new ArrayList<String>();
		private final ArrayList<Double> durations = new ArrayList<Double>();
		private static final NodeLabUi.Tone[] TONES = {
			NodeLabUi.Tone.ACCENT, NodeLabUi.Tone.PURPLE, NodeLabUi.Tone.INFO, NodeLabUi.Tone.SUCCESS,
			NodeLabUi.Tone.PINK, NodeLabUi.Tone.WARNING
		};

		StageBar()
		{
			setOpaque(false);
		}

		void set(List<String> stageNames, List<Double> stageDurations)
		{
			names.clear();
			durations.clear();
			for (int index = 0; index < stageNames.size(); index++)
			{
				double value = stageDurations.get(index).doubleValue();
				if (!Double.isNaN(value) && value > 0.0)
				{
					names.add(stageNames.get(index));
					durations.add(Double.valueOf(value));
				}
			}
			repaint();
		}

		public Dimension getPreferredSize()
		{
			return new Dimension(400, 64);
		}

		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D)graphics.create();
			NodeLabUi.prepare(g);
			int w = getWidth();
			double total = 0.0;
			for (Double value : durations)
			{
				total += value.doubleValue();
			}
			g.setColor(NebulaDesign.INSET);
			g.fill(new RoundRectangle2D.Double(0, 0, w, 16, 10, 10));
			if (total <= 0.0)
			{
				g.dispose();
				return;
			}
			Shape clip = new RoundRectangle2D.Double(0, 0, w, 16, 10, 10);
			Graphics2D bar = (Graphics2D)g.create();
			bar.clip(clip);
			double x = 0.0;
			for (int index = 0; index < durations.size(); index++)
			{
				double width = durations.get(index).doubleValue() / total * w;
				bar.setColor(NodeLabUi.tone(TONES[index % TONES.length]));
				bar.fill(new Rectangle2D.Double(x, 0, width + 1, 16));
				x += width;
			}
			bar.dispose();
			g.setFont(NebulaDesign.fontMedium(11f));
			FontMetrics metrics = g.getFontMetrics();
			int lx = 0;
			for (int index = 0; index < durations.size(); index++)
			{
				String label = names.get(index) + " " + NodeLabUi.duration(durations.get(index).doubleValue())
						+ String.format(Locale.US, " (%.0f%%)", durations.get(index).doubleValue() / total * 100.0);
				g.setColor(NodeLabUi.tone(TONES[index % TONES.length]));
				g.fill(new Ellipse2D.Double(lx, 29, 8, 8));
				g.setColor(NebulaDesign.TEXT_SECONDARY);
				g.drawString(label, lx + 12, 37);
				lx += metrics.stringWidth(label) + 28;
			}
			g.dispose();
		}
	}

	// ------------------------------------------------------------ line chart

	/** A named series of points. */
	static final class Series
	{
		final String name;
		final Color color;
		final List<double[]> points = new ArrayList<double[]>();

		Series(String name, Color color)
		{
			this.name = name;
			this.color = color;
		}

		Series add(double x, double y)
		{
			points.add(new double[] {x, y});
			return this;
		}
	}

	/** A small multi-series line chart with a legend. */
	static final class LineChart extends JComponent
	{
		private static final long serialVersionUID = 1L;
		private List<Series> series = Collections.emptyList();
		private NodeLabUi.Formatter xFormat;
		private NodeLabUi.Formatter yFormat;

		LineChart()
		{
			setOpaque(false);
		}

		void set(List<Series> value, NodeLabUi.Formatter xFormatter, NodeLabUi.Formatter yFormatter)
		{
			series = value == null ? Collections.<Series>emptyList() : value;
			xFormat = xFormatter;
			yFormat = yFormatter;
			repaint();
		}

		public Dimension getPreferredSize()
		{
			return new Dimension(420, 200);
		}

		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D)graphics.create();
			NodeLabUi.prepare(g);
			int w = getWidth();
			int h = getHeight();
			double minX = Double.POSITIVE_INFINITY;
			double maxX = Double.NEGATIVE_INFINITY;
			double maxY = 0.0;
			for (Series line : series)
			{
				for (double[] point : line.points)
				{
					minX = Math.min(minX, point[0]);
					maxX = Math.max(maxX, point[0]);
					maxY = Math.max(maxY, point[1]);
				}
			}
			if (Double.isInfinite(minX))
			{
				g.dispose();
				return;
			}
			if (maxX <= minX)
			{
				maxX = minX + 1.0;
			}
			if (maxY <= 0.0)
			{
				maxY = 1.0;
			}
			int left = 46;
			int top = 26;
			int bottom = h - 22;
			int right = w - 10;
			g.setFont(NebulaDesign.fontMedium(10f));
			FontMetrics metrics = g.getFontMetrics();
			for (double tick : niceTicks(0.0, maxY, 4))
			{
				double y = bottom - tick / maxY * (bottom - top);
				g.setColor(NodeLabUi.alpha(NebulaDesign.BORDER_SUBTLE, 200));
				g.draw(new Line2D.Double(left, y, right, y));
				String label = yFormat == null ? NodeLabUi.number(tick) : yFormat.format(tick);
				g.setColor(NebulaDesign.TEXT_TERTIARY);
				g.drawString(label, left - 6 - metrics.stringWidth(label), (float)(y + 4));
			}
			for (double tick : niceTicks(minX, maxX, 5))
			{
				double x = left + (tick - minX) / (maxX - minX) * (right - left);
				String label = xFormat == null ? NodeLabUi.number(tick) : xFormat.format(tick);
				g.setColor(NebulaDesign.TEXT_TERTIARY);
				g.drawString(label, (float)(x - metrics.stringWidth(label) / 2.0), bottom + 15);
			}
			int legendX = left;
			for (Series line : series)
			{
				g.setColor(line.color);
				g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
				java.awt.geom.Path2D.Double path = new java.awt.geom.Path2D.Double();
				boolean first = true;
				for (double[] point : line.points)
				{
					double x = left + (point[0] - minX) / (maxX - minX) * (right - left);
					double y = bottom - point[1] / maxY * (bottom - top);
					if (first)
					{
						path.moveTo(x, y);
						first = false;
					}
					else
					{
						path.lineTo(x, y);
					}
				}
				g.draw(path);
				g.fill(new RoundRectangle2D.Double(legendX, 6, 14, 4, 4, 4));
				g.setColor(NebulaDesign.TEXT_SECONDARY);
				g.drawString(line.name, legendX + 18, 12);
				legendX += metrics.stringWidth(line.name) + 36;
			}
			g.dispose();
		}
	}
}
