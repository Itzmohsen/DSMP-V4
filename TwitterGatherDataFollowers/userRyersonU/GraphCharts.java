package TwitterGatherDataFollowers.userRyersonU;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.GeneralPath;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.Arrays;
import java.util.Locale;

import javax.swing.JComponent;

/**
 * The charts of Graph Insights, drawn with the Nebula tokens so they follow
 * the day and night themes: a matrix heatmap, the Lorenz curve of exposure,
 * a log-log complementary distribution, the Guimer&agrave;-Amaral role map,
 * paired share bars and a thumbnail of the run graph.
 */
final class GraphCharts
{
	private GraphCharts()
	{
	}

	/** Receives a node chosen in a chart (to inspect it in the Explorer). */
	interface NodeListener
	{
		void nodeChosen(int node);
	}

	private static Font label()
	{
		return NebulaDesign.fontMedium(11f);
	}

	private static Font small()
	{
		return NebulaDesign.fontBody(10.5f);
	}

	private static String fit(String text, FontMetrics metrics, double width)
	{
		if (metrics.stringWidth(text) <= width)
		{
			return text;
		}
		int end = text.length();
		while (end > 0 && metrics.stringWidth(text.substring(0, end) + "…") > width)
		{
			end--;
		}
		return end <= 0 ? "" : text.substring(0, end) + "…";
	}

	private static Color mix(Color a, Color b, double t)
	{
		return GraphRenderer.blend(a, b, (float)Math.max(0.0, Math.min(1.0, t)));
	}

	private static Color concrete(Color color)
	{
		return new Color(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha());
	}

	// ------------------------------------------------------------ heatmap

	/** A matrix of cells shaded by intensity, with row and column labels and a text per cell. */
	static final class Heatmap extends JComponent
	{
		private static final long serialVersionUID = 1L;
		private static final int CELL = 30;
		private String[] rows = new String[0];
		private String[] columns = new String[0];
		private double[][] intensity = new double[0][0];
		private String[][] text = new String[0][0];
		private Color[] rowColors;
		private Color[] columnColors;
		private boolean diagonal;
		private String corner = "";
		private int labelWidth = 170;

		Heatmap()
		{
			setOpaque(false);
			setToolTipText("");
		}

		/** Cells shaded by {@code intensity} (0..1), labelled by {@code text}; the diagonal outlined when asked. */
		void set(String[] rowLabels, String[] columnLabels, double[][] values, String[][] cellText,
				Color[] rowSwatches, Color[] columnSwatches, boolean outlineDiagonal, String cornerText)
		{
			rows = rowLabels;
			columns = columnLabels;
			intensity = values;
			text = cellText;
			rowColors = rowSwatches;
			columnColors = columnSwatches;
			diagonal = outlineDiagonal;
			corner = cornerText == null ? "" : cornerText;
			FontMetrics metrics = getFontMetrics(label());
			int widest = 80;
			for (String row : rows)
			{
				widest = Math.max(widest, metrics.stringWidth(row) + 26);
			}
			labelWidth = Math.min(220, widest);
			revalidate();
			repaint();
		}

		public Dimension getPreferredSize()
		{
			return new Dimension(labelWidth + Math.max(1, columns.length) * 64, 46 + Math.max(1, rows.length) * CELL);
		}

		public Dimension getMaximumSize()
		{
			return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
		}

		private double cellWidth()
		{
			return columns.length == 0 ? 0 : Math.max(26.0, (getWidth() - labelWidth) / (double)columns.length);
		}

		public String getToolTipText(MouseEvent event)
		{
			int column = (int)((event.getX() - labelWidth) / Math.max(1.0, cellWidth()));
			int row = (event.getY() - 46) / CELL;
			if (event.getX() < labelWidth || row < 0 || row >= rows.length || column < 0 || column >= columns.length)
			{
				return null;
			}
			return rows[row] + " → " + columns[column] + ": " + text[row][column];
		}

		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D)graphics.create();
			NodeLabUi.prepare(g);
			double cell = cellWidth();
			Color base = concrete(NebulaDesign.ACCENT);
			Color empty = concrete(NebulaDesign.INSET);
			g.setFont(small());
			FontMetrics smallMetrics = g.getFontMetrics();
			g.setColor(NebulaDesign.TEXT_TERTIARY);
			g.drawString(fit(corner, smallMetrics, labelWidth - 8), 0, 30);
			for (int column = 0; column < columns.length; column++)
			{
				double x = labelWidth + column * cell;
				if (columnColors != null && columnColors[column] != null)
				{
					g.setColor(columnColors[column]);
					g.fill(new RoundRectangle2D.Double(x + 4, 37, cell - 8, 4, 4, 4));
				}
				g.setColor(NebulaDesign.TEXT_SECONDARY);
				g.setFont(label());
				String shown = fit(columns[column], g.getFontMetrics(), cell - 6);
				g.drawString(shown, (float)(x + (cell - g.getFontMetrics().stringWidth(shown)) / 2.0), 30f);
			}
			for (int row = 0; row < rows.length; row++)
			{
				int y = 46 + row * CELL;
				if (rowColors != null && rowColors[row] != null)
				{
					g.setColor(rowColors[row]);
					g.fill(new Ellipse2D.Double(2, y + CELL / 2.0 - 4, 8, 8));
				}
				g.setColor(NebulaDesign.TEXT_PRIMARY);
				g.setFont(label());
				g.drawString(fit(rows[row], g.getFontMetrics(), labelWidth - 22), 16, y + CELL / 2 + 4);
				for (int column = 0; column < columns.length; column++)
				{
					double x = labelWidth + column * cell;
					double value = Math.max(0.0, Math.min(1.0, intensity[row][column]));
					Color fill = value <= 0.0 ? empty : mix(mix(empty, base, 0.12), base, value);
					g.setColor(fill);
					g.fill(new RoundRectangle2D.Double(x + 2, y + 2, cell - 4, CELL - 4, 7, 7));
					if (diagonal && row == column)
					{
						g.setColor(NebulaDesign.TEXT_PRIMARY);
						g.setStroke(new BasicStroke(1.6f));
						g.draw(new RoundRectangle2D.Double(x + 2.5, y + 2.5, cell - 5, CELL - 5, 7, 7));
					}
					String cellText = text[row][column];
					if (cellText != null && cellText.length() > 0)
					{
						g.setFont(small());
						g.setColor(value > 0.55 ? Color.WHITE : NebulaDesign.TEXT_SECONDARY);
						String shown = fit(cellText, g.getFontMetrics(), cell - 6);
						g.drawString(shown, (float)(x + (cell - g.getFontMetrics().stringWidth(shown)) / 2.0),
								y + CELL / 2f + 4f);
					}
				}
			}
			g.dispose();
		}
	}

	// ------------------------------------------------------------ Lorenz curve

	/** The Lorenz curve of exposure: the share of all exposure held by the least exposed share of candidates. */
	static final class Lorenz extends JComponent
	{
		private static final long serialVersionUID = 1L;
		private double[][] points = new double[0][];
		private double gini = Double.NaN;

		Lorenz()
		{
			setOpaque(false);
		}

		void set(double[] values, double giniValue)
		{
			points = NetworkStatistics.lorenz(values);
			gini = giniValue;
			repaint();
		}

		public Dimension getPreferredSize()
		{
			return new Dimension(360, 300);
		}

		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D)graphics.create();
			NodeLabUi.prepare(g);
			int left = 46;
			int top = 12;
			int size = Math.max(60, Math.min(getWidth() - left - 16, getHeight() - top - 40));
			drawAxes(g, left, top, size, "Share of candidates (least exposed first)", "Share of exposure");
			g.setColor(NebulaDesign.TEXT_TERTIARY);
			g.setStroke(new BasicStroke(1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10f, new float[] {4f, 4f},
					0f));
			g.draw(new Line2D.Double(left, top + size, left + size, top));
			if (points.length > 1)
			{
				GeneralPath area = new GeneralPath();
				GeneralPath curve = new GeneralPath();
				area.moveTo(left, top + size);
				curve.moveTo(left, top + size);
				for (double[] point : points)
				{
					double x = left + point[0] * size;
					double y = top + size - point[1] * size;
					curve.lineTo(x, y);
					area.lineTo(x, y);
				}
				area.lineTo(left, top + size);
				area.closePath();
				g.setColor(NodeLabUi.alpha(NebulaDesign.ACCENT, 46));
				g.fill(area);
				g.setColor(NebulaDesign.ACCENT);
				g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
				g.draw(curve);
			}
			g.setFont(NebulaDesign.fontSemiBold(13f));
			g.setColor(NebulaDesign.TEXT_PRIMARY);
			g.drawString("Gini " + GraphInsightsPanel.fixed(gini, 3), left + 12, top + 20);
			g.setFont(small());
			g.setColor(NebulaDesign.TEXT_TERTIARY);
			g.drawString("dashed: perfect equality", left + 12, top + 36);
			g.dispose();
		}
	}

	private static void drawAxes(Graphics2D g, int left, int top, int size, String xTitle, String yTitle)
	{
		g.setColor(NodeLabUi.alpha(NebulaDesign.TEXT_TERTIARY, 70));
		g.setStroke(new BasicStroke(1f));
		for (int tick = 0; tick <= 4; tick++)
		{
			double offset = size * tick / 4.0;
			g.draw(new Line2D.Double(left, top + size - offset, left + size, top + size - offset));
			g.draw(new Line2D.Double(left + offset, top, left + offset, top + size));
		}
		g.setFont(small());
		g.setColor(NebulaDesign.TEXT_TERTIARY);
		for (int tick = 0; tick <= 4; tick++)
		{
			String value = tick == 0 ? "0" : tick == 4 ? "1" : String.format(Locale.US, "%.2f", tick / 4.0);
			double offset = size * tick / 4.0;
			g.drawString(value, (float)(left - 6 - g.getFontMetrics().stringWidth(value)),
					(float)(top + size - offset + 4));
			g.drawString(value, (float)(left + offset - g.getFontMetrics().stringWidth(value) / 2.0),
					(float)(top + size + 15));
		}
		g.drawString(xTitle, left, top + size + 32);
		Graphics2D rotated = (Graphics2D)g.create();
		rotated.rotate(-Math.PI / 2);
		rotated.drawString(yTitle, -(top + size), 10);
		rotated.dispose();
	}

	// ------------------------------------------------------------ CCDF

	/** The complementary cumulative distribution P(X >= x) of positive values on log-log axes. */
	static final class Ccdf extends JComponent
	{
		private static final long serialVersionUID = 1L;
		private double[] xs = new double[0];
		private double[] ys = new double[0];
		private String xTitle = "";
		private int zeros;

		Ccdf()
		{
			setOpaque(false);
		}

		void set(double[] values, String title)
		{
			double[] sorted = Arrays.copyOf(values, values.length);
			Arrays.sort(sorted);
			int n = sorted.length;
			int first = 0;
			while (first < n && sorted[first] <= 0.0)
			{
				first++;
			}
			zeros = first;
			int distinct = 0;
			double[] tx = new double[n];
			double[] ty = new double[n];
			for (int index = first; index < n; index++)
			{
				if (index > first && Math.abs(sorted[index] - sorted[index - 1]) <= 1e-12 * Math.max(1.0, sorted[index]))
				{
					continue;
				}
				tx[distinct] = sorted[index];
				ty[distinct] = (n - index) / (double)n;
				distinct++;
			}
			xs = Arrays.copyOf(tx, distinct);
			ys = Arrays.copyOf(ty, distinct);
			xTitle = title;
			repaint();
		}

		public Dimension getPreferredSize()
		{
			return new Dimension(380, 300);
		}

		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D)graphics.create();
			NodeLabUi.prepare(g);
			int left = 52;
			int top = 12;
			int width = Math.max(60, getWidth() - left - 16);
			int height = Math.max(60, getHeight() - top - 42);
			g.setFont(small());
			if (xs.length == 0)
			{
				g.setColor(NebulaDesign.TEXT_TERTIARY);
				g.drawString("No candidate was recommended.", left, top + 20);
				g.dispose();
				return;
			}
			double minX = Math.floor(Math.log10(xs[0]));
			double maxX = Math.ceil(Math.log10(xs[xs.length - 1]));
			if (maxX <= minX)
			{
				maxX = minX + 1;
			}
			double minY = Math.floor(Math.log10(ys[ys.length - 1]));
			if (minY >= 0)
			{
				minY = -1;
			}
			g.setColor(NodeLabUi.alpha(NebulaDesign.TEXT_TERTIARY, 70));
			for (double decade = minX; decade <= maxX; decade++)
			{
				double x = left + (decade - minX) / (maxX - minX) * width;
				g.draw(new Line2D.Double(x, top, x, top + height));
			}
			for (double decade = minY; decade <= 0; decade++)
			{
				double y = top + (0 - decade) / (0 - minY) * height;
				g.draw(new Line2D.Double(left, y, left + width, y));
			}
			g.setColor(NebulaDesign.TEXT_TERTIARY);
			for (double decade = minX; decade <= maxX; decade++)
			{
				double x = left + (decade - minX) / (maxX - minX) * width;
				String value = decade(decade);
				g.drawString(value, (float)(x - g.getFontMetrics().stringWidth(value) / 2.0), top + height + 15);
			}
			for (double decade = minY; decade <= 0; decade++)
			{
				double y = top + (0 - decade) / (0 - minY) * height;
				String value = decade(decade);
				g.drawString(value, (float)(left - 6 - g.getFontMetrics().stringWidth(value)), (float)(y + 4));
			}
			g.drawString(xTitle + (zeros > 0 ? "   (" + zeros + " never recommended, not shown)" : ""), left,
					top + height + 32);
			Graphics2D rotated = (Graphics2D)g.create();
			rotated.rotate(-Math.PI / 2);
			rotated.drawString("P(X ≥ x)", -(top + height), 12);
			rotated.dispose();
			GeneralPath path = new GeneralPath();
			for (int index = 0; index < xs.length; index++)
			{
				double x = left + (Math.log10(xs[index]) - minX) / (maxX - minX) * width;
				double y = top + (0 - Math.log10(ys[index])) / (0 - minY) * height;
				if (index == 0)
				{
					path.moveTo(x, y);
				}
				else
				{
					path.lineTo(x, y);
				}
			}
			g.setColor(NodeLabUi.alpha(NebulaDesign.PURPLE, 150));
			g.setStroke(new BasicStroke(1.6f));
			g.draw(path);
			g.setColor(NebulaDesign.PURPLE);
			for (int index = 0; index < xs.length; index++)
			{
				double x = left + (Math.log10(xs[index]) - minX) / (maxX - minX) * width;
				double y = top + (0 - Math.log10(ys[index])) / (0 - minY) * height;
				g.fill(new Ellipse2D.Double(x - 3, y - 3, 6, 6));
			}
			g.dispose();
		}

		private static String decade(double exponent)
		{
			int e = (int)Math.round(exponent);
			return e == 0 ? "1" : e == 1 ? "10" : e == -1 ? "0.1" : "1e" + e;
		}
	}

	// ------------------------------------------------------------ role map

	/** The role map: each node's participation coefficient against its within-module degree, with the seven role regions. */
	static final class RoleMap extends JComponent
	{
		private static final long serialVersionUID = 1L;
		private RunGraphRecord.Loaded loaded;
		private NodeListener listener;
		private int hover = -1;
		private double minZ = -2;
		private double maxZ = 4;

		RoleMap()
		{
			setOpaque(false);
			setToolTipText("");
			MouseAdapter mouse = new MouseAdapter()
			{
				public void mouseMoved(MouseEvent event)
				{
					int node = nodeAt(event.getX(), event.getY());
					if (node != hover)
					{
						hover = node;
						setCursor(Cursor.getPredefinedCursor(node >= 0 ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
						repaint();
					}
				}

				public void mouseClicked(MouseEvent event)
				{
					int node = nodeAt(event.getX(), event.getY());
					if (node >= 0 && listener != null)
					{
						listener.nodeChosen(node);
					}
				}
			};
			addMouseListener(mouse);
			addMouseMotionListener(mouse);
		}

		void set(RunGraphRecord.Loaded value, NodeListener nodeListener)
		{
			loaded = value;
			listener = nodeListener;
			minZ = -2;
			maxZ = 4;
			if (loaded != null)
			{
				for (int node = 0; node < loaded.graph.size(); node++)
				{
					if (loaded.analysis.degreeFull[node] > 0)
					{
						minZ = Math.min(minZ, Math.floor(loaded.analysis.withinModuleZ[node]));
						maxZ = Math.max(maxZ, Math.ceil(loaded.analysis.withinModuleZ[node]));
					}
				}
			}
			repaint();
		}

		public Dimension getPreferredSize()
		{
			return new Dimension(520, 340);
		}

		private double px(double p)
		{
			return 48 + p * (getWidth() - 64);
		}

		private double py(double z)
		{
			double height = getHeight() - 52;
			return 12 + (maxZ - z) / (maxZ - minZ) * height;
		}

		private int nodeAt(int x, int y)
		{
			if (loaded == null)
			{
				return -1;
			}
			int best = -1;
			double bestDistance = 7.0;
			for (int node = 0; node < loaded.graph.size(); node++)
			{
				if (loaded.analysis.degreeFull[node] == 0)
				{
					continue;
				}
				double dx = px(loaded.analysis.participation[node]) - x;
				double dy = py(loaded.analysis.withinModuleZ[node]) - y;
				double distance = Math.sqrt(dx * dx + dy * dy);
				if (distance < bestDistance)
				{
					best = node;
					bestDistance = distance;
				}
			}
			return best;
		}

		public String getToolTipText(MouseEvent event)
		{
			int node = nodeAt(event.getX(), event.getY());
			if (node < 0)
			{
				return null;
			}
			return loaded.graph.name(node) + " — " + loaded.analysis.roles[node] + String.format(Locale.US,
					" (P %.2f, z %.2f)", loaded.analysis.participation[node], loaded.analysis.withinModuleZ[node]);
		}

		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D)graphics.create();
			NodeLabUi.prepare(g);
			double hubLine = py(2.5);
			g.setColor(NodeLabUi.alpha(NebulaDesign.WARNING, NodeLabUi.night() ? 26 : 18));
			g.fill(new Rectangle2D.Double(px(0), py(maxZ), px(1) - px(0), hubLine - py(maxZ)));
			g.setColor(NodeLabUi.alpha(NebulaDesign.TEXT_TERTIARY, 90));
			g.setStroke(new BasicStroke(1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10f, new float[] {4f, 4f},
					0f));
			g.draw(new Line2D.Double(px(0), hubLine, px(1), hubLine));
			for (double boundary : new double[] {0.05, 0.62, 0.80})
			{
				g.draw(new Line2D.Double(px(boundary), hubLine, px(boundary), py(minZ)));
			}
			for (double boundary : new double[] {0.30, 0.75})
			{
				g.draw(new Line2D.Double(px(boundary), py(maxZ), px(boundary), hubLine));
			}
			g.setFont(small());
			g.setColor(NebulaDesign.TEXT_TERTIARY);
			String[] lowLabels = {"R1", "R2 peripheral", "R3 connector", "R4 kinless"};
			double[] lowStarts = {0.0, 0.05, 0.62, 0.80};
			double[] lowEnds = {0.05, 0.62, 0.80, 1.0};
			for (int index = 0; index < lowLabels.length; index++)
			{
				String text = fit(lowLabels[index], g.getFontMetrics(), px(lowEnds[index]) - px(lowStarts[index]) - 6);
				g.drawString(text, (float)(px(lowStarts[index]) + 4), (float)(py(minZ) - 6));
			}
			String[] hubLabels = {"R5 provincial hubs", "R6 connector hubs", "R7 kinless hubs"};
			double[] hubStarts = {0.0, 0.30, 0.75};
			double[] hubEnds = {0.30, 0.75, 1.0};
			for (int index = 0; index < hubLabels.length; index++)
			{
				String text = fit(hubLabels[index], g.getFontMetrics(), px(hubEnds[index]) - px(hubStarts[index]) - 6);
				g.drawString(text, (float)(px(hubStarts[index]) + 4), (float)(py(maxZ) + 14));
			}
			g.setColor(NodeLabUi.alpha(NebulaDesign.TEXT_TERTIARY, 140));
			g.setStroke(new BasicStroke(1f));
			g.draw(new Line2D.Double(px(0), py(minZ), px(1), py(minZ)));
			g.draw(new Line2D.Double(px(0), py(minZ), px(0), py(maxZ)));
			g.setColor(NebulaDesign.TEXT_TERTIARY);
			for (double p = 0; p <= 1.0001; p += 0.25)
			{
				String text = String.format(Locale.US, "%.2f", p);
				g.drawString(text, (float)(px(p) - g.getFontMetrics().stringWidth(text) / 2.0), (float)(py(minZ) + 15));
			}
			for (double z = Math.ceil(minZ); z <= maxZ; z += Math.max(1, Math.round((maxZ - minZ) / 6)))
			{
				String text = String.format(Locale.US, "%.0f", z);
				g.drawString(text, (float)(px(0) - 8 - g.getFontMetrics().stringWidth(text)), (float)(py(z) + 4));
			}
			g.drawString("Participation coefficient P (links spread over communities)", (float)px(0),
					(float)(py(minZ) + 32));
			Graphics2D rotated = (Graphics2D)g.create();
			rotated.rotate(-Math.PI / 2);
			rotated.drawString("Within-community degree z", (float)-py(minZ), 12f);
			rotated.dispose();
			if (loaded == null)
			{
				g.dispose();
				return;
			}
			boolean night = NodeLabUi.night();
			for (int pass = 0; pass < 2; pass++)
			{
				for (int node = 0; node < loaded.graph.size(); node++)
				{
					if (loaded.analysis.degreeFull[node] == 0 || (pass == 0) == loaded.graph.isAccount(node))
					{
						continue;
					}
					double x = px(loaded.analysis.participation[node]);
					double y = py(loaded.analysis.withinModuleZ[node]);
					double r = loaded.graph.isAccount(node) ? 6.0 : loaded.graph.isTarget(node) ? 4.2 : 3.2;
					Color color = GraphRenderer.nodeColor(loaded.graph, loaded.analysis, node,
							GraphRenderer.ColorBy.REAL, night);
					g.setColor(NodeLabUi.alpha(color, node == hover ? 255 : 200));
					g.fill(new Ellipse2D.Double(x - r, y - r, 2 * r, 2 * r));
					if (loaded.graph.isAccount(node) || node == hover)
					{
						g.setColor(NebulaDesign.TEXT_PRIMARY);
						g.setStroke(new BasicStroke(node == hover ? 2f : 1.2f));
						g.draw(new Ellipse2D.Double(x - r - 1.5, y - r - 1.5, 2 * r + 3, 2 * r + 3));
					}
				}
			}
			g.dispose();
		}
	}

	// ------------------------------------------------------------ share bars

	/** For each community: its share of the candidates against its share of the exposure. */
	static final class ShareBars extends JComponent
	{
		private static final long serialVersionUID = 1L;
		private String[] labels = new String[0];
		private double[] candidateShare = new double[0];
		private double[] exposureShare = new double[0];
		private Color[] colors = new Color[0];

		ShareBars()
		{
			setOpaque(false);
		}

		void set(String[] names, double[] candidates, double[] exposure, Color[] swatches)
		{
			labels = names;
			candidateShare = candidates;
			exposureShare = exposure;
			colors = swatches;
			revalidate();
			repaint();
		}

		public Dimension getPreferredSize()
		{
			return new Dimension(420, 30 + labels.length * 34);
		}

		public Dimension getMaximumSize()
		{
			return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
		}

		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D)graphics.create();
			NodeLabUi.prepare(g);
			int labelWidth = 170;
			int right = 70;
			double max = 0.0;
			for (int index = 0; index < labels.length; index++)
			{
				max = Math.max(max, Math.max(candidateShare[index], exposureShare[index]));
			}
			max = max <= 0.0 ? 1.0 : max;
			double width = Math.max(40, getWidth() - labelWidth - right);
			g.setFont(small());
			g.setColor(NebulaDesign.TEXT_TERTIARY);
			g.drawString("grey: share of candidates   colour: share of recommendation exposure", labelWidth, 14);
			for (int index = 0; index < labels.length; index++)
			{
				int y = 26 + index * 34;
				g.setFont(label());
				g.setColor(NebulaDesign.TEXT_PRIMARY);
				g.drawString(fit(labels[index], g.getFontMetrics(), labelWidth - 12), 0, y + 15);
				g.setColor(concrete(NebulaDesign.INSET));
				g.fill(new RoundRectangle2D.Double(labelWidth, y + 2, width, 10, 6, 6));
				g.setColor(NodeLabUi.alpha(NebulaDesign.TEXT_TERTIARY, 120));
				g.fill(new RoundRectangle2D.Double(labelWidth, y + 2, width * candidateShare[index] / max, 10, 6, 6));
				g.setColor(concrete(NebulaDesign.INSET));
				g.fill(new RoundRectangle2D.Double(labelWidth, y + 15, width, 10, 6, 6));
				g.setColor(colors[index]);
				g.fill(new RoundRectangle2D.Double(labelWidth, y + 15, width * exposureShare[index] / max, 10, 6, 6));
				double ratio = candidateShare[index] > 0.0 ? exposureShare[index] / candidateShare[index] : Double.NaN;
				g.setFont(NebulaDesign.fontSemiBold(11.5f));
				g.setColor(Double.isNaN(ratio) ? NebulaDesign.TEXT_TERTIARY
						: ratio > 1.15 ? NebulaDesign.SUCCESS : ratio < 0.87 ? NebulaDesign.DANGER
								: NebulaDesign.TEXT_SECONDARY);
				String text = Double.isNaN(ratio) ? "—" : String.format(Locale.US, "%.2f×", ratio);
				g.drawString(text, (float)(labelWidth + width + 12), (float)(y + 18));
			}
			g.dispose();
		}
	}

	// ------------------------------------------------------------ thumbnail

	/** A small picture of the run graph (communities coloured), drawn from its layout. */
	static final class Thumbnail extends JComponent
	{
		private static final long serialVersionUID = 1L;
		private RunGraphRecord.Loaded loaded;
		private final int preferredWidth;
		private final int preferredHeight;

		Thumbnail(int width, int height)
		{
			preferredWidth = width;
			preferredHeight = height;
			setOpaque(false);
		}

		void set(RunGraphRecord.Loaded value)
		{
			loaded = value;
			repaint();
		}

		public Dimension getPreferredSize()
		{
			return new Dimension(preferredWidth, preferredHeight);
		}

		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D)graphics.create();
			NodeLabUi.prepare(g);
			boolean night = NodeLabUi.night();
			g.setColor(GraphRenderer.background(night));
			g.fill(new RoundRectangle2D.Double(0, 0, getWidth() - 1, getHeight() - 1, 16, 16));
			g.setColor(concrete(NebulaDesign.BORDER_SUBTLE));
			g.draw(new RoundRectangle2D.Double(0.5, 0.5, getWidth() - 2, getHeight() - 2, 16, 16));
			if (loaded == null || !loaded.hasLayout())
			{
				g.setFont(small());
				g.setColor(NebulaDesign.TEXT_TERTIARY);
				String text = loaded == null ? "No graph" : "Laying out the graph…";
				g.drawString(text, (getWidth() - g.getFontMetrics().stringWidth(text)) / 2, getHeight() / 2);
				g.dispose();
				return;
			}
			g.clip(new RoundRectangle2D.Double(1, 1, getWidth() - 3, getHeight() - 3, 16, 16));
			GraphRenderer.Style style = new GraphRenderer.Style();
			style.night = night;
			style.labels = false;
			GraphRenderer.Transform t = GraphRenderer.fit(loaded.x, loaded.y,
					GraphRenderer.shown(loaded.graph, style), getWidth(), getHeight(), 14);
			double zoom = Math.max(0.35, Math.min(1.0, getWidth() / 900.0));
			GraphRenderer.paint(new GraphRenderer.Graphics2DCanvas(g), loaded.graph, loaded.analysis, loaded.x,
					loaded.y, style, t, zoom);
			g.dispose();
		}
	}
}
