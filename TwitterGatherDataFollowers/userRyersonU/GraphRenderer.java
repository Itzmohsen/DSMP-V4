package TwitterGatherDataFollowers.userRyersonU;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.GeneralPath;
import java.awt.geom.Line2D;
import java.awt.geom.QuadCurve2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Draws a laid-out run graph: on screen, into a PNG and as SVG, from one
 * drawing routine so every picture of a run looks the same.
 *
 * Nodes are coloured by community (or another attribute) and sized by a
 * centrality; accounts are larger rings, targets carry an outline. Follows
 * are hairlines in their account's colour; recommendations are curved arrows,
 * coloured when they stay inside the target's community and neutral and
 * dashed when they cross to another; adoptions are thick arrows. Labels are
 * placed greedily without overlaps, most important first.
 */
final class GraphRenderer
{
	enum ColorBy
	{
		REAL("Real community", "Community"),
		DETECTED("Detected community", "Detected"),
		KIND("Node kind", "Kind"),
		CORE("k-core", "k-core"),
		ROLE("Network role", "Role");

		final String label;
		/** The label in the Explorer's toolbar. */
		final String shortLabel;

		ColorBy(String label, String shortLabel)
		{
			this.label = label;
			this.shortLabel = shortLabel;
		}
	}

	enum SizeBy
	{
		DEGREE("Degree"),
		PAGERANK("PageRank"),
		BETWEENNESS("Betweenness"),
		EXPOSURE("Exposure");

		final String label;

		SizeBy(String label)
		{
			this.label = label;
		}
	}

	/** What to draw and how. */
	static final class Style
	{
		ColorBy colorBy = ColorBy.REAL;
		SizeBy sizeBy = SizeBy.DEGREE;
		boolean follows = true;
		boolean recommendations = true;
		boolean adoptions = true;
		boolean labels = true;
		boolean night;
		/** Node to emphasise with its neighbourhood (-1: none). */
		int focus = -1;
		int hover = -1;
		/** Nodes matching a search (null: none). */
		boolean[] matches;
		/** Hide nodes without edges in the shown layers. */
		boolean hideIsolated = true;

		Style copy()
		{
			Style style = new Style();
			style.colorBy = colorBy;
			style.sizeBy = sizeBy;
			style.follows = follows;
			style.recommendations = recommendations;
			style.adoptions = adoptions;
			style.labels = labels;
			style.night = night;
			style.focus = focus;
			style.hover = hover;
			style.matches = matches;
			style.hideIsolated = hideIsolated;
			return style;
		}
	}

	/** World (layout) to screen: screen = (world - centre) * scale + half the canvas. */
	static final class Transform
	{
		final double centerX;
		final double centerY;
		final double scale;
		final double width;
		final double height;

		Transform(double centerX, double centerY, double scale, double width, double height)
		{
			this.centerX = centerX;
			this.centerY = centerY;
			this.scale = scale;
			this.width = width;
			this.height = height;
		}

		double x(double worldX)
		{
			return (worldX - centerX) * scale + width / 2.0;
		}

		double y(double worldY)
		{
			return (worldY - centerY) * scale + height / 2.0;
		}

		double worldX(double screenX)
		{
			return (screenX - width / 2.0) / scale + centerX;
		}

		double worldY(double screenY)
		{
			return (screenY - height / 2.0) / scale + centerY;
		}

		Transform zoom(double factor, double screenX, double screenY)
		{
			double wx = worldX(screenX);
			double wy = worldY(screenY);
			double next = scale * factor;
			double cx = wx - (screenX - width / 2.0) / next;
			double cy = wy - (screenY - height / 2.0) / next;
			return new Transform(cx, cy, next, width, height);
		}

		Transform pan(double screenDx, double screenDy)
		{
			return new Transform(centerX - screenDx / scale, centerY - screenDy / scale, scale, width, height);
		}

		Transform resize(double newWidth, double newHeight)
		{
			return new Transform(centerX, centerY, scale, newWidth, newHeight);
		}
	}

	/** Where drawing goes: a Graphics2D or an SVG document. */
	interface Canvas
	{
		void line(double x1, double y1, double x2, double y2, Color color, double width, boolean dashed);

		void curve(double x1, double y1, double cx, double cy, double x2, double y2, Color color, double width,
				boolean dashed);

		void polygon(double[] xs, double[] ys, Color fill);

		void circle(double cx, double cy, double r, Color fill, Color stroke, double strokeWidth);

		void rect(double x, double y, double w, double h, double radius, Color fill, Color stroke);

		void text(String text, double x, double y, Font font, Color color, Color halo, boolean centred);

		double textWidth(String text, Font font);
	}

	// ------------------------------------------------------------ palette

	private static final Color[] DAY_PALETTE = {
		new Color(79, 91, 231), new Color(13, 148, 136), new Color(217, 119, 6), new Color(219, 39, 119),
		new Color(124, 58, 237), new Color(22, 163, 74), new Color(37, 99, 235), new Color(220, 38, 38),
		new Color(8, 145, 178), new Color(161, 98, 7), new Color(147, 51, 234), new Color(101, 163, 13),
		new Color(225, 29, 72), new Color(3, 105, 161), new Color(194, 65, 12), new Color(77, 124, 15),
		new Color(192, 38, 211), new Color(15, 118, 110), new Color(180, 83, 9), new Color(71, 85, 105)
	};
	private static final Color[] NIGHT_PALETTE = {
		new Color(125, 162, 255), new Color(45, 212, 191), new Color(251, 191, 36), new Color(244, 114, 182),
		new Color(167, 139, 250), new Color(74, 222, 128), new Color(96, 165, 250), new Color(248, 113, 113),
		new Color(34, 211, 238), new Color(250, 204, 21), new Color(192, 132, 252), new Color(163, 230, 53),
		new Color(251, 113, 133), new Color(56, 189, 248), new Color(251, 146, 60), new Color(190, 242, 100),
		new Color(232, 121, 249), new Color(94, 234, 212), new Color(253, 224, 71), new Color(148, 163, 184)
	};

	/** A categorical colour (communities beyond the palette cycle in a lighter tone). */
	static Color category(int index, boolean night)
	{
		if (index < 0)
		{
			return night ? new Color(100, 116, 139) : new Color(148, 163, 184);
		}
		Color[] palette = night ? NIGHT_PALETTE : DAY_PALETTE;
		Color base = palette[index % palette.length];
		int round = index / palette.length;
		if (round == 0)
		{
			return base;
		}
		float mix = Math.min(0.6f, 0.25f * round);
		Color toward = night ? new Color(15, 23, 42) : Color.WHITE;
		return blend(base, toward, mix);
	}

	static Color blend(Color a, Color b, float t)
	{
		return new Color(Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
				Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
				Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t));
	}

	static Color alpha(Color color, int alpha)
	{
		return new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.max(0, Math.min(255, alpha)));
	}

	static Color background(boolean night)
	{
		return night ? new Color(11, 17, 27) : new Color(252, 253, 255);
	}

	static Color ink(boolean night)
	{
		return night ? new Color(226, 232, 240) : new Color(30, 41, 59);
	}

	/** The colour of edges that leave the target's community. */
	static Color bridge(boolean night)
	{
		return night ? new Color(226, 232, 240) : new Color(51, 65, 85);
	}

	private static final String[] ROLE_ORDER = {
		"R1 ultra-peripheral", "R2 peripheral", "R3 connector", "R4 kinless", "R5 provincial hub",
		"R6 connector hub", "R7 kinless hub", "Isolated"
	};

	static int roleIndex(String role)
	{
		for (int index = 0; index < ROLE_ORDER.length; index++)
		{
			if (ROLE_ORDER[index].equals(role))
			{
				return index;
			}
		}
		return ROLE_ORDER.length - 1;
	}

	static String[] roles()
	{
		return ROLE_ORDER.clone();
	}

	/** A node's fill colour under a style. */
	static Color nodeColor(RunGraph graph, RunGraphAnalysis analysis, int node, ColorBy colorBy, boolean night)
	{
		switch (colorBy)
		{
			case DETECTED:
				return category(analysis.full.communities.membership[node], night);
			case KIND:
				return graph.kind(node) == RunGraph.Kind.ACCOUNT ? category(2, night)
						: graph.kind(node) == RunGraph.Kind.BOTH ? category(4, night)
								: graph.isTarget(node) ? category(0, night) : category(1, night);
			case CORE:
			{
				float t = analysis.core[node] / (float)Math.max(1, analysis.maxCore);
				Color low = night ? new Color(51, 65, 85) : new Color(203, 213, 225);
				Color high = night ? new Color(251, 191, 36) : new Color(194, 65, 12);
				return blend(low, high, t);
			}
			case ROLE:
			{
				int role = roleIndex(analysis.roles[node]);
				return role == ROLE_ORDER.length - 1 ? category(-1, night) : category(role + 6, night);
			}
			default:
				return category(graph.community(node), night);
		}
	}

	/** An account's colour: the community it anchors (its followers'). */
	static Color accountColor(RunGraph graph, int node, boolean night)
	{
		int anchored = graph.anchoredCommunity(node);
		return category(anchored >= 0 ? anchored : graph.community(node), night);
	}

	// ------------------------------------------------------------ geometry

	/** Normalised size metric in [0, 1] for each node. */
	static double[] sizeMetric(RunGraphAnalysis analysis, SizeBy sizeBy)
	{
		int n = analysis.graph.size();
		double[] raw = new double[n];
		for (int node = 0; node < n; node++)
		{
			switch (sizeBy)
			{
				case PAGERANK:
					raw[node] = analysis.pageRank[node];
					break;
				case BETWEENNESS:
					raw[node] = analysis.betweenness.values[node];
					break;
				case EXPOSURE:
					raw[node] = analysis.recommendedIn[node];
					break;
				default:
					raw[node] = analysis.degreeFull[node];
					break;
			}
		}
		double maxUser = 0.0;
		double maxAccount = 0.0;
		for (int node = 0; node < n; node++)
		{
			if (analysis.graph.isAccount(node))
			{
				maxAccount = Math.max(maxAccount, raw[node]);
			}
			else
			{
				maxUser = Math.max(maxUser, raw[node]);
			}
		}
		double[] normalised = new double[n];
		for (int node = 0; node < n; node++)
		{
			double max = analysis.graph.isAccount(node) ? maxAccount : maxUser;
			normalised[node] = max <= 0.0 ? 0.0 : Math.sqrt(raw[node] / max);
		}
		return normalised;
	}

	/** A node's radius in pixels at the fitted zoom. */
	static double radius(RunGraph graph, double metric, int node)
	{
		return graph.isAccount(node) ? 9.0 + 13.0 * metric : 2.6 + 6.4 * metric;
	}

	/** The transform that fits the positions into a canvas with a margin. */
	static Transform fit(double[] x, double[] y, boolean[] shown, double width, double height, double margin)
	{
		double minX = Double.MAX_VALUE;
		double maxX = -Double.MAX_VALUE;
		double minY = Double.MAX_VALUE;
		double maxY = -Double.MAX_VALUE;
		for (int node = 0; node < x.length; node++)
		{
			if (shown != null && !shown[node])
			{
				continue;
			}
			minX = Math.min(minX, x[node]);
			maxX = Math.max(maxX, x[node]);
			minY = Math.min(minY, y[node]);
			maxY = Math.max(maxY, y[node]);
		}
		if (minX > maxX)
		{
			return new Transform(0, 0, 1, width, height);
		}
		double spanX = Math.max(1e-9, maxX - minX);
		double spanY = Math.max(1e-9, maxY - minY);
		double scale = Math.min((width - 2 * margin) / spanX, (height - 2 * margin) / spanY);
		if (!(scale > 0.0) || Double.isInfinite(scale))
		{
			scale = 1.0;
		}
		return new Transform((minX + maxX) / 2.0, (minY + maxY) / 2.0, scale, width, height);
	}

	/** Which nodes a style shows: those with an edge in a shown layer, or all when isolated nodes are kept. */
	static boolean[] shown(RunGraph graph, Style style)
	{
		boolean[] shown = new boolean[graph.size()];
		if (!style.hideIsolated)
		{
			Arrays.fill(shown, true);
			return shown;
		}
		for (RunGraph.Layer layer : RunGraph.Layer.values())
		{
			if (!visible(layer, style))
			{
				continue;
			}
			for (RunGraph.Edge edge : graph.edges(layer))
			{
				shown[edge.source] = true;
				shown[edge.target] = true;
			}
		}
		for (int node = 0; node < graph.size(); node++)
		{
			shown[node] |= graph.isTarget(node) || node == style.focus;
		}
		return shown;
	}

	static boolean visible(RunGraph.Layer layer, Style style)
	{
		return layer == RunGraph.Layer.FOLLOW ? style.follows
				: layer == RunGraph.Layer.RECOMMEND ? style.recommendations : style.adoptions;
	}

	/** The node under a screen point, or -1 (the nearest within its radius plus a margin). */
	static int nodeAt(RunGraph graph, double[] x, double[] y, double[] metric, boolean[] shown, Transform t,
			double zoomFactor, double screenX, double screenY)
	{
		int best = -1;
		double bestDistance = Double.MAX_VALUE;
		for (int node = 0; node < graph.size(); node++)
		{
			if (shown != null && !shown[node])
			{
				continue;
			}
			double dx = t.x(x[node]) - screenX;
			double dy = t.y(y[node]) - screenY;
			double distance = Math.sqrt(dx * dx + dy * dy);
			double reach = radius(graph, metric[node], node) * zoomFactor + 4.0;
			if (distance <= reach && distance < bestDistance)
			{
				best = node;
				bestDistance = distance;
			}
		}
		return best;
	}

	// ------------------------------------------------------------ drawing

	/**
	 * Draws the graph. {@code zoomFactor} scales node radii (1 at the fitted
	 * zoom; the canvas grows nodes with the square root of the zoom).
	 */
	static void paint(Canvas canvas, RunGraph graph, RunGraphAnalysis analysis, double[] x, double[] y, Style style,
			Transform t, double zoomFactor)
	{
		boolean night = style.night;
		double[] metric = sizeMetric(analysis, style.sizeBy);
		boolean[] shown = shown(graph, style);
		boolean[] near = null;
		if (style.focus >= 0)
		{
			near = new boolean[graph.size()];
			near[style.focus] = true;
			for (RunGraph.Edge edge : graph.allEdges())
			{
				if (!visible(edge.layer, style))
				{
					continue;
				}
				if (edge.source == style.focus)
				{
					near[edge.target] = true;
				}
				if (edge.target == style.focus)
				{
					near[edge.source] = true;
				}
			}
		}
		double[] r = new double[graph.size()];
		for (int node = 0; node < r.length; node++)
		{
			r[node] = radius(graph, metric[node], node) * zoomFactor;
		}
		int edgeCount = graph.allEdges().size();
		double fade = edgeCount > 4000 ? 0.55 : edgeCount > 1500 ? 0.75 : 1.0;
		if (style.follows)
		{
			for (RunGraph.Edge edge : graph.edges(RunGraph.Layer.FOLLOW))
			{
				boolean emphasised = near == null || edge.source == style.focus || edge.target == style.focus;
				int anchored = graph.anchoredCommunity(edge.target);
				Color color = category(anchored >= 0 ? anchored : graph.community(edge.target), night);
				int alphaValue = (int)((night ? 80 : 64) * fade * (emphasised ? 1.0 : 0.18));
				if (near != null && emphasised)
				{
					alphaValue = 200;
				}
				canvas.line(t.x(x[edge.source]), t.y(y[edge.source]), t.x(x[edge.target]), t.y(y[edge.target]),
						alpha(color, alphaValue), near != null && emphasised ? 1.4 : 0.7, false);
			}
		}
		if (style.recommendations)
		{
			for (RunGraph.Edge edge : graph.edges(RunGraph.Layer.RECOMMEND))
			{
				drawArrow(canvas, graph, edge, x, y, r, t, style, near, fade, false);
			}
		}
		if (style.adoptions)
		{
			for (RunGraph.Edge edge : graph.edges(RunGraph.Layer.ADOPT))
			{
				drawArrow(canvas, graph, edge, x, y, r, t, style, near, fade, true);
			}
		}
		Integer[] order = new Integer[graph.size()];
		for (int node = 0; node < order.length; node++)
		{
			order[node] = Integer.valueOf(node);
		}
		final double[] radii = r;
		Arrays.sort(order, new Comparator<Integer>()
		{
			public int compare(Integer left, Integer right)
			{
				return Double.compare(radii[left.intValue()], radii[right.intValue()]);
			}
		});
		Color backgroundColor = background(night);
		for (Integer boxed : order)
		{
			int node = boxed.intValue();
			if (!shown[node])
			{
				continue;
			}
			boolean dim = near != null && !near[node]
					|| style.matches != null && !style.matches[node] && near == null;
			Color fill = nodeColor(graph, analysis, node, style.colorBy, night);
			double sx = t.x(x[node]);
			double sy = t.y(y[node]);
			if (sx < -40 || sy < -40 || sx > t.width + 40 || sy > t.height + 40)
			{
				continue;
			}
			int alphaValue = dim ? 55 : 255;
			if (graph.isAccount(node))
			{
				//The ring is the community the account anchors; the dot, the one it follows (if it is also a user).
				Color ring = style.colorBy == ColorBy.REAL ? accountColor(graph, node, night) : fill;
				canvas.circle(sx, sy, r[node] + 2.2, alpha(backgroundColor, alphaValue), null, 0);
				canvas.circle(sx, sy, r[node], alpha(blend(ring, backgroundColor, night ? 0.55f : 0.78f), alphaValue),
						alpha(ring, alphaValue), 2.6);
				canvas.circle(sx, sy, Math.max(2.5, r[node] * 0.38), alpha(fill, alphaValue), null, 0);
			}
			else
			{
				canvas.circle(sx, sy, r[node], alpha(fill, alphaValue),
						alpha(graph.isTarget(node) ? ink(night) : backgroundColor, alphaValue),
						graph.isTarget(node) ? 1.6 : 0.8);
			}
			if (node == style.focus || node == style.hover)
			{
				canvas.circle(sx, sy, r[node] + 4.5, null, alpha(night ? Color.WHITE : new Color(15, 23, 42), 210),
						2.0);
			}
			if (style.matches != null && style.matches[node])
			{
				canvas.circle(sx, sy, r[node] + 7.0, null, alpha(new Color(234, 179, 8), 230), 2.4);
			}
		}
		if (style.labels)
		{
			drawLabels(canvas, graph, analysis, x, y, r, shown, near, t, style);
		}
	}

	private static void drawArrow(Canvas canvas, RunGraph graph, RunGraph.Edge edge, double[] x, double[] y,
			double[] r, Transform t, Style style, boolean[] near, double fade, boolean adoption)
	{
		boolean night = style.night;
		double x1 = t.x(x[edge.source]);
		double y1 = t.y(y[edge.source]);
		double x2 = t.x(x[edge.target]);
		double y2 = t.y(y[edge.target]);
		double dx = x2 - x1;
		double dy = y2 - y1;
		double length = Math.sqrt(dx * dx + dy * dy);
		if (length < 1.0)
		{
			return;
		}
		boolean emphasised = near == null || edge.source == style.focus || edge.target == style.focus;
		Boolean inside = graph.inside(edge);
		Color base = inside == null ? category(-1, night) : inside.booleanValue()
				? category(graph.community(edge.source), night) : bridge(night);
		double rankWeight = adoption ? 1.0 : RunGraph.rankWeight(edge.rank);
		int alphaValue = adoption ? 235 : (int)((100 + 90 * rankWeight) * fade);
		if (!emphasised)
		{
			alphaValue = (int)(alphaValue * 0.14);
		}
		else if (near != null)
		{
			alphaValue = 245;
		}
		Color color = alpha(base, alphaValue);
		double bend = adoption ? 0.20 : 0.12;
		double cx = (x1 + x2) / 2.0 - dy * bend;
		double cy = (y1 + y2) / 2.0 + dx * bend;
		double ex = x2 - cx;
		double ey = y2 - cy;
		double el = Math.sqrt(ex * ex + ey * ey);
		double inset = r[edge.target] + (adoption ? 2.5 : 1.5);
		double tipX = x2 - ex / el * inset;
		double tipY = y2 - ey / el * inset;
		double width = adoption ? 2.6 : 0.6 + 1.1 * rankWeight;
		double head = adoption ? 9.0 : 4.5 + 2.5 * rankWeight;
		double baseX = tipX - ex / el * head;
		double baseY = tipY - ey / el * head;
		boolean dashed = inside != null && !inside.booleanValue() && !adoption;
		canvas.curve(x1, y1, cx, cy, baseX, baseY, color, width, dashed);
		double nx = -ey / el;
		double ny = ex / el;
		double halfWidth = head * 0.45;
		canvas.polygon(new double[] {tipX, baseX + nx * halfWidth, baseX - nx * halfWidth},
				new double[] {tipY, baseY + ny * halfWidth, baseY - ny * halfWidth}, color);
	}

	private static void drawLabels(Canvas canvas, final RunGraph graph, RunGraphAnalysis analysis, double[] x,
			double[] y, double[] r, boolean[] shown, boolean[] near, Transform t, Style style)
	{
		boolean night = style.night;
		List<Integer> candidates = new ArrayList<Integer>();
		for (int node = 0; node < graph.size(); node++)
		{
			if (!shown[node])
			{
				continue;
			}
			double sx = t.x(x[node]);
			double sy = t.y(y[node]);
			if (sx < 0 || sy < 0 || sx > t.width || sy > t.height)
			{
				continue;
			}
			candidates.add(Integer.valueOf(node));
		}
		final double[] priority = new double[graph.size()];
		for (int node = 0; node < graph.size(); node++)
		{
			priority[node] = (node == style.focus || node == style.hover ? 1e6 : 0.0)
					+ (near != null && near[node] ? 1e5 : 0.0)
					+ (style.matches != null && style.matches[node] ? 5e5 : 0.0)
					+ (graph.isAccount(node) ? 1e4 : 0.0) + (graph.isTarget(node) ? 1e3 : 0.0)
					+ (analysis.recommendedIn[node] > 0 ? 1e2 : 0.0) + r[node];
		}
		java.util.Collections.sort(candidates, new Comparator<Integer>()
		{
			public int compare(Integer left, Integer right)
			{
				int byPriority = Double.compare(priority[right.intValue()], priority[left.intValue()]);
				return byPriority != 0 ? byPriority : graph.name(left.intValue()).compareTo(graph.name(right.intValue()));
			}
		});
		List<Rectangle2D> placed = new ArrayList<Rectangle2D>();
		Font accountFont = NebulaDesign.fontSemiBold(12f);
		Font userFont = NebulaDesign.fontMedium(10.5f);
		Color halo = alpha(background(night), 235);
		int budget = Math.max(12, (int)(t.width * t.height / 9000.0));
		for (Integer boxed : candidates)
		{
			int node = boxed.intValue();
			boolean important = priority[node] >= 1e4;
			if (!important && placed.size() >= budget)
			{
				continue;
			}
			//Users are named when they are targets or were recommended (or in focus).
			if (!important && priority[node] < 1e2)
			{
				continue;
			}
			Font font = graph.isAccount(node) ? accountFont : userFont;
			String text = graph.name(node);
			double width = canvas.textWidth(text, font);
			double sx = t.x(x[node]);
			double sy = t.y(y[node]);
			double lx = graph.isAccount(node) ? sx - width / 2.0 : sx + r[node] + 4.0;
			double ly = graph.isAccount(node) ? sy + r[node] + 19.0 : sy + 4.0;
			Rectangle2D box = graph.isAccount(node) ? new Rectangle2D.Double(lx - 8, ly - 14, width + 16, 20)
					: new Rectangle2D.Double(lx - 2, ly - 12, width + 4, 16);
			boolean overlaps = false;
			for (Rectangle2D other : placed)
			{
				if (other.intersects(box))
				{
					overlaps = true;
					break;
				}
			}
			if (overlaps && priority[node] < 1e5)
			{
				continue;
			}
			placed.add(box);
			boolean dim = near != null && !near[node];
			if (graph.isAccount(node))
			{
				Color fill = style.colorBy == ColorBy.REAL ? accountColor(graph, node, night)
						: nodeColor(graph, analysis, node, style.colorBy, night);
				canvas.rect(lx - 7, ly - 13, width + 14, 18, 9, alpha(blend(fill, background(night), night ? 0.7f : 0.86f),
						dim ? 120 : 245), alpha(fill, dim ? 90 : 200));
				canvas.text(text, lx, ly, font, alpha(blend(fill, ink(night), 0.55f), dim ? 110 : 255), null, false);
				continue;
			}
			canvas.text(text, lx, ly, font, alpha(ink(night), dim ? 90 : 255), halo, false);
		}
	}

	// ------------------------------------------------------------ legend

	/** Draws a legend box (communities, edge kinds) at the top-left. Returns its height. */
	static double legend(Canvas canvas, RunGraph graph, RunGraphAnalysis analysis, Style style, double left,
			double top, int maxCommunities)
	{
		boolean night = style.night;
		Font title = NebulaDesign.fontSemiBold(11.5f);
		Font body = NebulaDesign.fontBody(11f);
		List<String> labels = new ArrayList<String>();
		List<Color> colors = new ArrayList<Color>();
		if (style.colorBy == ColorBy.REAL)
		{
			int[] sizes = new int[graph.communityCount()];
			for (int node = 0; node < graph.size(); node++)
			{
				if (graph.community(node) >= 0)
				{
					sizes[graph.community(node)]++;
				}
			}
			for (int community = 0; community < Math.min(maxCommunities, graph.communityCount()); community++)
			{
				labels.add(graph.communityName(community) + "  (" + sizes[community] + ")");
				colors.add(category(community, night));
			}
			if (graph.communityCount() > maxCommunities)
			{
				labels.add("+ " + (graph.communityCount() - maxCommunities) + " more communities");
				colors.add(category(-1, night));
			}
		}
		else if (style.colorBy == ColorBy.DETECTED)
		{
			int[] sizes = NetworkAlgorithms.groupSizes(analysis.full.communities.membership);
			for (int community = 0; community < Math.min(maxCommunities, sizes.length); community++)
			{
				labels.add("Community " + (community + 1) + "  (" + sizes[community] + ")");
				colors.add(category(community, night));
			}
		}
		double width = 230;
		for (String label : labels)
		{
			width = Math.max(width, canvas.textWidth(label, body) + 44);
		}
		double rowHeight = 18;
		double height = 30 + labels.size() * rowHeight + 4 * rowHeight + 12;
		canvas.rect(left, top, width, height, 10, alpha(background(night), 236),
				alpha(night ? new Color(51, 65, 85) : new Color(226, 232, 240), 255));
		double cursor = top + 20;
		canvas.text(style.colorBy.label.toUpperCase(Locale.US), left + 12, cursor, title,
				alpha(ink(night), 200), null, false);
		cursor += 8;
		for (int index = 0; index < labels.size(); index++)
		{
			cursor += rowHeight;
			canvas.circle(left + 18, cursor - 4, 5, colors.get(index), null, 0);
			canvas.text(labels.get(index), left + 30, cursor, body, ink(night), null, false);
		}
		cursor += rowHeight;
		Color neutral = category(-1, night);
		canvas.line(left + 10, cursor - 4, left + 26, cursor - 4, alpha(neutral, 160), 0.8, false);
		canvas.text("Follows (observed)", left + 30, cursor, body, ink(night), null, false);
		cursor += rowHeight;
		canvas.line(left + 10, cursor - 4, left + 26, cursor - 4, alpha(category(0, night), 220), 1.5, false);
		canvas.text("Recommendation inside the community", left + 30, cursor, body, ink(night), null, false);
		cursor += rowHeight;
		canvas.line(left + 10, cursor - 4, left + 26, cursor - 4, alpha(bridge(night), 220), 1.5, true);
		canvas.text("Recommendation to another community", left + 30, cursor, body, ink(night), null, false);
		cursor += rowHeight;
		canvas.line(left + 10, cursor - 4, left + 26, cursor - 4, alpha(ink(night), 235), 2.8, false);
		canvas.text("Adopted follow", left + 30, cursor, body, ink(night), null, false);
		return height;
	}

	// ------------------------------------------------------------ canvases

	/** Draws on a Graphics2D. */
	static final class Graphics2DCanvas implements Canvas
	{
		private final Graphics2D g;

		Graphics2DCanvas(Graphics2D g)
		{
			this.g = g;
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
			g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		}

		private static BasicStroke stroke(double width, boolean dashed)
		{
			return dashed ? new BasicStroke((float)width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10f,
					new float[] {4f, 3f}, 0f) : new BasicStroke((float)width, BasicStroke.CAP_ROUND,
					BasicStroke.JOIN_ROUND);
		}

		public void line(double x1, double y1, double x2, double y2, Color color, double width, boolean dashed)
		{
			g.setColor(color);
			g.setStroke(stroke(width, dashed));
			g.draw(new Line2D.Double(x1, y1, x2, y2));
		}

		public void curve(double x1, double y1, double cx, double cy, double x2, double y2, Color color, double width,
				boolean dashed)
		{
			g.setColor(color);
			g.setStroke(stroke(width, dashed));
			g.draw(new QuadCurve2D.Double(x1, y1, cx, cy, x2, y2));
		}

		public void polygon(double[] xs, double[] ys, Color fill)
		{
			GeneralPath path = new GeneralPath();
			path.moveTo(xs[0], ys[0]);
			for (int index = 1; index < xs.length; index++)
			{
				path.lineTo(xs[index], ys[index]);
			}
			path.closePath();
			g.setColor(fill);
			g.fill(path);
		}

		public void circle(double cx, double cy, double r, Color fill, Color stroke, double strokeWidth)
		{
			Ellipse2D shape = new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r);
			if (fill != null)
			{
				g.setColor(fill);
				g.fill(shape);
			}
			if (stroke != null && strokeWidth > 0)
			{
				g.setColor(stroke);
				g.setStroke(new BasicStroke((float)strokeWidth));
				g.draw(shape);
			}
		}

		public void rect(double x, double y, double w, double h, double radius, Color fill, Color stroke)
		{
			RoundRectangle2D shape = new RoundRectangle2D.Double(x, y, w, h, radius * 2, radius * 2);
			if (fill != null)
			{
				g.setColor(fill);
				g.fill(shape);
			}
			if (stroke != null)
			{
				g.setColor(stroke);
				g.setStroke(new BasicStroke(1f));
				g.draw(shape);
			}
		}

		public void text(String text, double x, double y, Font font, Color color, Color halo, boolean centred)
		{
			if (text == null || text.length() == 0)
			{
				return;
			}
			FontRenderContext context = g.getFontRenderContext();
			TextLayout layout = new TextLayout(text, font, context);
			double left = centred ? x - layout.getAdvance() / 2.0 : x;
			if (halo != null)
			{
				Shape outline = layout.getOutline(AffineTransform.getTranslateInstance(left, y));
				g.setColor(halo);
				g.setStroke(new BasicStroke(3.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
				g.draw(outline);
			}
			g.setColor(color);
			layout.draw(g, (float)left, (float)y);
		}

		public double textWidth(String text, Font font)
		{
			FontMetrics metrics = g.getFontMetrics(font);
			return metrics.stringWidth(text);
		}
	}

	/** Writes SVG elements. */
	static final class SvgCanvas implements Canvas
	{
		private final StringBuilder out = new StringBuilder();
		private final Graphics2D measure;

		SvgCanvas()
		{
			measure = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics();
		}

		String document(double width, double height, Color backgroundColor, String title)
		{
			StringBuilder svg = new StringBuilder();
			svg.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
			svg.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"").append(number(width))
					.append("\" height=\"").append(number(height)).append("\" viewBox=\"0 0 ")
					.append(number(width)).append(' ').append(number(height)).append("\">\n");
			svg.append("<title>").append(escape(title)).append("</title>\n");
			svg.append("<rect width=\"100%\" height=\"100%\" fill=\"").append(hex(backgroundColor)).append("\"/>\n");
			svg.append(out);
			svg.append("</svg>\n");
			return svg.toString();
		}

		private static String stroke(Color color, double width, boolean dashed)
		{
			return " stroke=\"" + hex(color) + "\"" + opacity("stroke-opacity", color) + " stroke-width=\""
					+ number(width) + "\" stroke-linecap=\"round\"" + (dashed ? " stroke-dasharray=\"4 3\"" : "");
		}

		public void line(double x1, double y1, double x2, double y2, Color color, double width, boolean dashed)
		{
			out.append("<line x1=\"").append(number(x1)).append("\" y1=\"").append(number(y1)).append("\" x2=\"")
					.append(number(x2)).append("\" y2=\"").append(number(y2)).append('"')
					.append(stroke(color, width, dashed)).append("/>\n");
		}

		public void curve(double x1, double y1, double cx, double cy, double x2, double y2, Color color, double width,
				boolean dashed)
		{
			out.append("<path d=\"M").append(number(x1)).append(' ').append(number(y1)).append(" Q")
					.append(number(cx)).append(' ').append(number(cy)).append(' ').append(number(x2)).append(' ')
					.append(number(y2)).append("\" fill=\"none\"").append(stroke(color, width, dashed)).append("/>\n");
		}

		public void polygon(double[] xs, double[] ys, Color fill)
		{
			out.append("<polygon points=\"");
			for (int index = 0; index < xs.length; index++)
			{
				out.append(index == 0 ? "" : " ").append(number(xs[index])).append(',').append(number(ys[index]));
			}
			out.append("\" fill=\"").append(hex(fill)).append('"').append(opacity("fill-opacity", fill))
					.append("/>\n");
		}

		public void circle(double cx, double cy, double r, Color fill, Color stroke, double strokeWidth)
		{
			out.append("<circle cx=\"").append(number(cx)).append("\" cy=\"").append(number(cy)).append("\" r=\"")
					.append(number(r)).append('"');
			out.append(fill == null ? " fill=\"none\"" : " fill=\"" + hex(fill) + "\"" + opacity("fill-opacity", fill));
			if (stroke != null && strokeWidth > 0)
			{
				out.append(stroke(stroke, strokeWidth, false));
			}
			out.append("/>\n");
		}

		public void rect(double x, double y, double w, double h, double radius, Color fill, Color stroke)
		{
			out.append("<rect x=\"").append(number(x)).append("\" y=\"").append(number(y)).append("\" width=\"")
					.append(number(w)).append("\" height=\"").append(number(h)).append("\" rx=\"")
					.append(number(radius)).append('"');
			out.append(fill == null ? " fill=\"none\"" : " fill=\"" + hex(fill) + "\"" + opacity("fill-opacity", fill));
			if (stroke != null)
			{
				out.append(stroke(stroke, 1.0, false));
			}
			out.append("/>\n");
		}

		public void text(String text, double x, double y, Font font, Color color, Color halo, boolean centred)
		{
			out.append("<text x=\"").append(number(x)).append("\" y=\"").append(number(y))
					.append("\" font-family=\"Inter, Helvetica, Arial, sans-serif\" font-size=\"")
					.append(number(font.getSize2D())).append("\" font-weight=\"")
					.append(font.getFontName().contains("SemiBold") || font.isBold() ? "600"
							: font.getFontName().contains("Medium") ? "500" : "400")
					.append("\" fill=\"").append(hex(color)).append('"').append(opacity("fill-opacity", color));
			if (centred)
			{
				out.append(" text-anchor=\"middle\"");
			}
			if (halo != null)
			{
				out.append(" stroke=\"").append(hex(halo)).append('"').append(opacity("stroke-opacity", halo))
						.append(" stroke-width=\"3.2\" stroke-linejoin=\"round\" paint-order=\"stroke\"");
			}
			out.append('>').append(escape(text)).append("</text>\n");
		}

		public double textWidth(String text, Font font)
		{
			return measure.getFontMetrics(font).stringWidth(text);
		}

		private static String hex(Color color)
		{
			return String.format(Locale.US, "#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
		}

		private static String opacity(String attribute, Color color)
		{
			return color.getAlpha() >= 255 ? ""
					: " " + attribute + "=\"" + number(color.getAlpha() / 255.0) + "\"";
		}

		private static String number(double value)
		{
			if (Math.abs(value - Math.rint(value)) < 1e-9)
			{
				return Long.toString(Math.round(value));
			}
			return String.format(Locale.US, "%.2f", value);
		}

		static String escape(String text)
		{
			StringBuilder escaped = new StringBuilder(text.length());
			for (int index = 0; index < text.length(); index++)
			{
				char c = text.charAt(index);
				switch (c)
				{
					case '<':
						escaped.append("&lt;");
						break;
					case '>':
						escaped.append("&gt;");
						break;
					case '&':
						escaped.append("&amp;");
						break;
					case '"':
						escaped.append("&quot;");
						break;
					default:
						if (c < 0x20 && c != '\t' && c != '\n' && c != '\r')
						{
							escaped.append(' ');
						}
						else
						{
							escaped.append(c);
						}
				}
			}
			return escaped.toString();
		}
	}

	// ------------------------------------------------------------ figures

	/** The publication figure: the graph fitted to the canvas with its legend. */
	static void figure(Canvas canvas, RunGraph graph, RunGraphAnalysis analysis, double[] x, double[] y,
			double width, double height, Style style)
	{
		//The graph fills the area right of the legend (300 units wide), with room for labels on the right.
		Transform t = fit(x, y, shown(graph, style), width - 300 - 100, height, 40);
		paint(canvas, graph, analysis, x, y, style,
				new Transform(t.centerX - 100 / t.scale, t.centerY, t.scale, width, height), 1.0);
		legend(canvas, graph, analysis, style, 20, 20, 16);
	}

	/** The figure as an SVG document. */
	static String svg(RunGraph graph, RunGraphAnalysis analysis, double[] x, double[] y, double width,
			double height, Style style)
	{
		SvgCanvas canvas = new SvgCanvas();
		figure(canvas, graph, analysis, x, y, width, height, style);
		return canvas.document(width, height, background(style.night),
				"Run graph of " + graph.algorithm + " on " + graph.dataset);
	}

	/** The figure as an image ({@code scale} pixels per unit, for sharp prints). */
	static BufferedImage image(RunGraph graph, RunGraphAnalysis analysis, double[] x, double[] y, int width,
			int height, double scale, Style style)
	{
		BufferedImage image = new BufferedImage((int)Math.round(width * scale), (int)Math.round(height * scale),
				BufferedImage.TYPE_INT_RGB);
		Graphics2D g = image.createGraphics();
		g.setColor(background(style.night));
		g.fillRect(0, 0, image.getWidth(), image.getHeight());
		g.scale(scale, scale);
		figure(new Graphics2DCanvas(g), graph, analysis, x, y, width, height, style);
		g.dispose();
		return image;
	}
}
