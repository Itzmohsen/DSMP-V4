package TwitterGatherDataFollowers.userRyersonU;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.GeneralPath;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.swing.JComponent;

/**
 * Charts of the TGF Studio and Evidence Studio, drawn with the Nebula tokens
 * so they follow the day and night themes: labelled bars (one-sided or
 * diverging), a log-odds waterfall, a reliability diagram and time lines.
 */
final class StudioCharts
{
	private StudioCharts()
	{
	}

	/**
	 * A text (a normalised tweet) with whole-word occurrences of {@code terms}
	 * highlighted in {@code tone}; the highlight colour resolves at paint time,
	 * so it follows the theme.
	 */
	static NodeLabUi.Prose quote(String text, java.util.Collection<String> terms, final Color tone)
	{
		NodeLabUi.Prose prose = NodeLabUi.prose(text);
		javax.swing.text.Highlighter.HighlightPainter painter =
				new javax.swing.text.DefaultHighlighter.DefaultHighlightPainter(null)
		{
			public Color getColor()
			{
				return NodeLabUi.alpha(tone, NodeLabUi.night() ? 95 : 70);
			}
		};
		String body = text == null ? "" : text;
		for (String term : terms)
		{
			if (term == null || term.isEmpty())
			{
				continue;
			}
			int from = 0;
			while (true)
			{
				int at = body.indexOf(term, from);
				if (at < 0)
				{
					break;
				}
				int end = at + term.length();
				boolean whole = (at == 0 || !Character.isLetterOrDigit(body.charAt(at - 1)))
						&& (end == body.length() || !Character.isLetterOrDigit(body.charAt(end)));
				if (whole)
				{
					try
					{
						prose.getHighlighter().addHighlight(at, end, painter);
					}
					catch (javax.swing.text.BadLocationException impossible)
					{
						//The range lies inside the text.
					}
				}
				from = end;
			}
		}
		return prose;
	}

	/** The categorical colour of community {@code index} (the run graph's palette). */
	static Color community(int index)
	{
		return GraphRenderer.category(Math.max(0, index), NodeLabUi.night());
	}

	static Font labelFont()
	{
		return NebulaDesign.fontMedium(11.5f);
	}

	static Font smallFont()
	{
		return NebulaDesign.fontBody(10.5f);
	}

	static Font valueFont()
	{
		return NebulaDesign.fontSemiBold(11.5f);
	}

	static Color concrete(Color color)
	{
		return new Color(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha());
	}

	static String fit(String text, FontMetrics metrics, double width)
	{
		if (text == null)
		{
			return "";
		}
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

	// =============================================================== bars

	/** One bar of a {@link Bars} chart. */
	static final class Bar
	{
		final String label;
		final double value;
		final String text;
		final Color color;
		/** Drawn emphasised (for example inside a conformal set). */
		final boolean strong;
		/** A short tag drawn after the label (for example "truth"). */
		final String tag;
		final String tooltip;

		Bar(String label, double value, String text, Color color, boolean strong, String tag, String tooltip)
		{
			this.label = label == null ? "" : label;
			this.value = Double.isFinite(value) ? value : 0.0;
			this.text = text == null ? "" : text;
			this.color = color;
			this.strong = strong;
			this.tag = tag;
			this.tooltip = tooltip;
		}
	}

	/**
	 * Horizontal bars with a label column and a value column. Values of one
	 * sign grow from the left; with negative values the bars diverge from a
	 * centre line.
	 */
	static final class Bars extends JComponent
	{
		private static final long serialVersionUID = 1L;
		private static final int ROW = 26;
		private List<Bar> bars = new ArrayList<Bar>();
		private double scale = Double.NaN;
		private int labelWidth = 160;
		private String caption;

		Bars()
		{
			setOpaque(false);
			setToolTipText("");
		}

		/** Sets the bars; {@code maximum} fixes the axis (NaN: the largest magnitude). */
		void set(List<Bar> values, double maximum, String note)
		{
			bars = values == null ? new ArrayList<Bar>() : new ArrayList<Bar>(values);
			scale = maximum;
			caption = note;
			FontMetrics metrics = getFontMetrics(labelFont());
			int widest = 60;
			for (Bar bar : bars)
			{
				widest = Math.max(widest, metrics.stringWidth(bar.label) + (bar.tag == null ? 0
						: metrics.stringWidth(bar.tag) + 22) + 14);
			}
			labelWidth = Math.min(240, widest);
			revalidate();
			repaint();
		}

		private int top()
		{
			return caption == null ? 4 : 22;
		}

		public Dimension getPreferredSize()
		{
			return new Dimension(380, top() + Math.max(1, bars.size()) * ROW + 6);
		}

		public Dimension getMaximumSize()
		{
			return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
		}

		public String getToolTipText(MouseEvent event)
		{
			int index = (event.getY() - top()) / ROW;
			if (index < 0 || index >= bars.size())
			{
				return null;
			}
			Bar bar = bars.get(index);
			return bar.tooltip != null ? bar.tooltip : bar.label + ": " + bar.text;
		}

		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D)graphics.create();
			NodeLabUi.prepare(g);
			if (caption != null)
			{
				g.setFont(smallFont());
				g.setColor(NebulaDesign.TEXT_TERTIARY);
				g.drawString(fit(caption, g.getFontMetrics(), getWidth()), 0, 13);
			}
			boolean diverging = false;
			double maximum = 0.0;
			for (Bar bar : bars)
			{
				diverging |= bar.value < 0.0;
				maximum = Math.max(maximum, Math.abs(bar.value));
			}
			if (Double.isFinite(scale) && scale > 0.0)
			{
				maximum = scale;
			}
			maximum = maximum <= 0.0 ? 1.0 : maximum;
			g.setFont(valueFont());
			int valueWidth = 56;
			for (Bar bar : bars)
			{
				valueWidth = Math.max(valueWidth, g.getFontMetrics().stringWidth(bar.text) + 10);
			}
			double x0 = labelWidth;
			double width = Math.max(30, getWidth() - labelWidth - valueWidth);
			double centre = diverging ? x0 + width / 2 : x0;
			double half = diverging ? width / 2 : width;
			if (diverging)
			{
				g.setColor(NodeLabUi.alpha(NebulaDesign.TEXT_TERTIARY, 110));
				g.draw(new Line2D.Double(centre, top(), centre, top() + bars.size() * ROW));
			}
			for (int index = 0; index < bars.size(); index++)
			{
				Bar bar = bars.get(index);
				int y = top() + index * ROW;
				g.setFont(bar.strong ? NebulaDesign.fontSemiBold(11.5f) : labelFont());
				g.setColor(bar.strong ? NebulaDesign.TEXT_PRIMARY : NebulaDesign.TEXT_SECONDARY);
				FontMetrics metrics = g.getFontMetrics();
				int tagWidth = bar.tag == null ? 0 : metrics.stringWidth(bar.tag) + 22;
				String label = fit(bar.label, metrics, labelWidth - 12 - tagWidth);
				g.drawString(label, 0, y + 17);
				if (bar.tag != null)
				{
					int tx = metrics.stringWidth(label) + 8;
					g.setFont(NebulaDesign.fontSemiBold(9.5f));
					int tw = g.getFontMetrics().stringWidth(bar.tag) + 10;
					g.setColor(NodeLabUi.alpha(NebulaDesign.SUCCESS, 40));
					g.fill(new RoundRectangle2D.Double(tx, y + 5, tw, 15, 8, 8));
					g.setColor(NebulaDesign.SUCCESS);
					g.drawString(bar.tag, tx + 5, y + 16);
				}
				if (!diverging)
				{
					g.setColor(concrete(NebulaDesign.INSET));
					g.fill(new RoundRectangle2D.Double(x0, y + 7, width, 11, 7, 7));
				}
				double length = half * Math.min(1.0, Math.abs(bar.value) / maximum);
				double left = bar.value >= 0.0 ? centre : centre - length;
				Color fill = bar.color == null ? NebulaDesign.ACCENT : bar.color;
				g.setColor(bar.strong ? fill : NodeLabUi.alpha(fill, 150));
				g.fill(new RoundRectangle2D.Double(left, y + 7, Math.max(2.0, length), 11, 7, 7));
				g.setFont(valueFont());
				g.setColor(bar.strong ? NebulaDesign.TEXT_PRIMARY : NebulaDesign.TEXT_SECONDARY);
				g.drawString(bar.text, (float)(x0 + width + 8), (float)(y + 17));
			}
			g.dispose();
		}
	}

	// =========================================================== waterfall

	/** One step of a {@link Waterfall}. */
	static final class Step
	{
		final String label;
		final double delta;
		final String note;

		Step(String label, double delta, String note)
		{
			this.label = label;
			this.delta = Double.isFinite(delta) ? delta : 0.0;
			this.note = note;
		}
	}

	/**
	 * The log-odds of one outcome against another built up step by step: each
	 * row moves the running total by its contribution (green toward the first
	 * outcome, red toward the second), the last row is the total.
	 */
	static final class Waterfall extends JComponent
	{
		private static final long serialVersionUID = 1L;
		private static final int ROW = 28;
		private List<Step> steps = new ArrayList<Step>();
		private String total = "";
		private String positive = "";
		private String negative = "";
		private int labelWidth = 170;

		Waterfall()
		{
			setOpaque(false);
			setToolTipText("");
		}

		void set(List<Step> values, String totalLabel, String towards, String against)
		{
			steps = new ArrayList<Step>(values);
			total = totalLabel;
			positive = towards;
			negative = against;
			FontMetrics metrics = getFontMetrics(labelFont());
			int widest = 90;
			for (Step step : steps)
			{
				widest = Math.max(widest, metrics.stringWidth(step.label) + 16);
			}
			labelWidth = Math.min(220, Math.max(widest, metrics.stringWidth(totalLabel) + 16));
			revalidate();
			repaint();
		}

		public Dimension getPreferredSize()
		{
			return new Dimension(460, 34 + (steps.size() + 1) * ROW + 10);
		}

		public Dimension getMaximumSize()
		{
			return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
		}

		public String getToolTipText(MouseEvent event)
		{
			int index = (event.getY() - 30) / ROW;
			if (index < 0 || index >= steps.size())
			{
				return null;
			}
			Step step = steps.get(index);
			return step.note == null ? step.label : step.note;
		}

		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D)graphics.create();
			NodeLabUi.prepare(g);
			double running = 0.0;
			double low = 0.0;
			double high = 0.0;
			for (Step step : steps)
			{
				running += step.delta;
				low = Math.min(low, running);
				high = Math.max(high, running);
			}
			double span = Math.max(1e-9, high - low);
			double valueWidth = 64;
			double x0 = labelWidth;
			double width = Math.max(40, getWidth() - labelWidth - valueWidth);
			g.setFont(smallFont());
			g.setColor(NebulaDesign.SUCCESS);
			String right = "→ " + positive;
			g.drawString(fit(right, g.getFontMetrics(), width / 2), (float)(x0 + width - Math.min(width / 2,
					g.getFontMetrics().stringWidth(right))), 14f);
			g.setColor(NebulaDesign.DANGER);
			g.drawString(fit(negative + " ←", g.getFontMetrics(), width / 2), (float)x0, 14f);
			double zero = x0 + (0.0 - low) / span * width;
			g.setColor(NodeLabUi.alpha(NebulaDesign.TEXT_TERTIARY, 120));
			g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[] {3f, 3f},
					0f));
			g.draw(new Line2D.Double(zero, 22, zero, 30 + (steps.size() + 1) * ROW));
			g.setStroke(new BasicStroke(1f));
			running = 0.0;
			for (int index = 0; index < steps.size(); index++)
			{
				Step step = steps.get(index);
				int y = 30 + index * ROW;
				double from = x0 + (running - low) / span * width;
				running += step.delta;
				double to = x0 + (running - low) / span * width;
				g.setFont(labelFont());
				g.setColor(NebulaDesign.TEXT_SECONDARY);
				g.drawString(fit(step.label, g.getFontMetrics(), labelWidth - 10), 0, y + 18);
				Color tone = step.delta >= 0.0 ? NebulaDesign.SUCCESS : NebulaDesign.DANGER;
				g.setColor(NodeLabUi.alpha(tone, 190));
				double left = Math.min(from, to);
				g.fill(new RoundRectangle2D.Double(left, y + 7, Math.max(2.0, Math.abs(to - from)), 14, 6, 6));
				if (index + 1 < steps.size())
				{
					g.setColor(NodeLabUi.alpha(NebulaDesign.TEXT_TERTIARY, 110));
					g.draw(new Line2D.Double(to, y + 21, to, y + ROW + 7));
				}
				g.setFont(valueFont());
				g.setColor(tone);
				g.drawString(String.format(Locale.US, "%+.2f", Double.valueOf(step.delta)), (float)(x0 + width + 8),
						(float)(y + 18));
			}
			int y = 30 + steps.size() * ROW;
			double end = x0 + (running - low) / span * width;
			g.setFont(NebulaDesign.fontSemiBold(11.5f));
			g.setColor(NebulaDesign.TEXT_PRIMARY);
			g.drawString(fit(total, g.getFontMetrics(), labelWidth - 10), 0, y + 18);
			Color tone = running >= 0.0 ? NebulaDesign.SUCCESS : NebulaDesign.DANGER;
			g.setColor(tone);
			g.fill(new RoundRectangle2D.Double(Math.min(zero, end), y + 6, Math.max(2.0, Math.abs(end - zero)), 16, 6,
					6));
			g.drawString(String.format(Locale.US, "%+.2f", Double.valueOf(running)), (float)(x0 + width + 8),
					(float)(y + 18));
			g.dispose();
		}
	}

	// ========================================================= reliability

	/**
	 * A reliability diagram: for each confidence bin, the observed accuracy
	 * against the mean confidence (a calibrated model lies on the diagonal);
	 * dot area shows how many users fall in the bin.
	 */
	static final class Reliability extends JComponent
	{
		private static final long serialVersionUID = 1L;
		private double[][] bins = new double[0][];

		Reliability()
		{
			setOpaque(false);
		}

		void set(double[][] reliability)
		{
			bins = reliability == null ? new double[0][] : reliability;
			repaint();
		}

		public Dimension getPreferredSize()
		{
			return new Dimension(320, 300);
		}

		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D)graphics.create();
			NodeLabUi.prepare(g);
			int left = 46;
			int bottom = 34;
			int size = Math.max(60, Math.min(getWidth() - left - 12, getHeight() - bottom - 12));
			int x0 = left;
			int y0 = 8;
			g.setColor(concrete(NebulaDesign.INSET));
			g.fill(new Rectangle2D.Double(x0, y0, size, size));
			g.setColor(NodeLabUi.alpha(NebulaDesign.TEXT_TERTIARY, 70));
			for (int i = 1; i < 5; i++)
			{
				double t = i / 5.0;
				g.draw(new Line2D.Double(x0 + t * size, y0, x0 + t * size, y0 + size));
				g.draw(new Line2D.Double(x0, y0 + size - t * size, x0 + size, y0 + size - t * size));
			}
			g.setColor(NodeLabUi.alpha(NebulaDesign.TEXT_TERTIARY, 160));
			g.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f,
					new float[] {4f, 4f}, 0f));
			g.draw(new Line2D.Double(x0, y0 + size, x0 + size, y0));
			g.setStroke(new BasicStroke(2f));
			double total = 0.0;
			for (double[] bin : bins)
			{
				total += bin.length > 0 ? bin[0] : 0.0;
			}
			GeneralPath path = new GeneralPath();
			boolean started = false;
			for (double[] bin : bins)
			{
				if (bin.length < 3 || bin[0] <= 0)
				{
					continue;
				}
				double x = x0 + bin[1] * size;
				double y = y0 + size - bin[2] * size;
				if (!started)
				{
					path.moveTo(x, y);
					started = true;
				}
				else
				{
					path.lineTo(x, y);
				}
			}
			g.setColor(NebulaDesign.ACCENT);
			g.draw(path);
			for (double[] bin : bins)
			{
				if (bin.length < 3 || bin[0] <= 0)
				{
					continue;
				}
				double radius = 3.5 + 9.0 * Math.sqrt(bin[0] / Math.max(1.0, total));
				double x = x0 + bin[1] * size;
				double y = y0 + size - bin[2] * size;
				g.setColor(NodeLabUi.alpha(NebulaDesign.ACCENT, 90));
				g.fill(new Ellipse2D.Double(x - radius, y - radius, 2 * radius, 2 * radius));
				g.setColor(NebulaDesign.ACCENT);
				g.draw(new Ellipse2D.Double(x - radius, y - radius, 2 * radius, 2 * radius));
			}
			g.setFont(smallFont());
			g.setColor(NebulaDesign.TEXT_TERTIARY);
			for (int i = 0; i <= 5; i++)
			{
				String text = String.format(Locale.US, "%.1f", Double.valueOf(i / 5.0));
				g.drawString(text, (float)(x0 + i / 5.0 * size - 8), (float)(y0 + size + 14));
				g.drawString(text, (float)(x0 - 26), (float)(y0 + size - i / 5.0 * size + 4));
			}
			g.setColor(NebulaDesign.TEXT_SECONDARY);
			g.drawString("predicted probability", (float)(x0 + size / 2 - 50), (float)(y0 + size + 28));
			Graphics2D rotated = (Graphics2D)g.create();
			rotated.rotate(-Math.PI / 2);
			rotated.drawString("observed accuracy", (float)(-(y0 + size / 2) - 44), 10f);
			rotated.dispose();
			g.dispose();
		}
	}

	/** The smallest of 1, 2, 2.5 and 5 times a power of ten that is at least {@code raw}. */
	static double niceStep(double raw)
	{
		double magnitude = Math.pow(10, Math.floor(Math.log10(Math.max(1e-12, raw))));
		for (double factor : new double[] {1.0, 2.0, 2.5, 5.0, 10.0})
		{
			if (factor * magnitude >= raw)
			{
				return factor * magnitude;
			}
		}
		return 10.0 * magnitude;
	}

	// ============================================================== lines

	/** One series of a {@link Lines} chart. */
	static final class Series
	{
		final String name;
		final double[] values;
		final Color color;
		final boolean emphasis;

		Series(String name, double[] values, Color color, boolean emphasis)
		{
			this.name = name;
			this.values = values == null ? new double[0] : values;
			this.color = color;
			this.emphasis = emphasis;
		}
	}

	/**
	 * Series over a common axis (months, hours): lines with a legend, x labels
	 * thinned to fit, values on a shared y scale starting at zero.
	 */
	static final class Lines extends JComponent
	{
		private static final long serialVersionUID = 1L;
		private List<Series> series = new ArrayList<Series>();
		private String[] labels = new String[0];
		private String unit = "";
		private int height = 230;

		Lines()
		{
			setOpaque(false);
			setToolTipText("");
		}

		void set(List<Series> values, String[] axisLabels, String yUnit, int preferredHeight)
		{
			series = new ArrayList<Series>(values);
			labels = axisLabels == null ? new String[0] : axisLabels;
			unit = yUnit == null ? "" : yUnit;
			height = preferredHeight;
			revalidate();
			repaint();
		}

		public Dimension getPreferredSize()
		{
			return new Dimension(520, height);
		}

		public Dimension getMaximumSize()
		{
			return new Dimension(Integer.MAX_VALUE, height);
		}

		private int length()
		{
			int length = labels.length;
			for (Series s : series)
			{
				length = Math.max(length, s.values.length);
			}
			return length;
		}

		public String getToolTipText(MouseEvent event)
		{
			int length = length();
			int x0 = 44;
			double width = Math.max(10, getWidth() - x0 - 10);
			if (length < 2)
			{
				return null;
			}
			int index = (int)Math.round((event.getX() - x0) / width * (length - 1));
			if (index < 0 || index >= length)
			{
				return null;
			}
			StringBuilder text = new StringBuilder("<html><b>").append(index < labels.length ? labels[index] : "")
					.append("</b>");
			for (Series s : series)
			{
				if (index < s.values.length)
				{
					text.append("<br>").append(s.name).append(": ")
							.append(String.format(Locale.US, "%.1f%%", Double.valueOf(100 * s.values[index])));
				}
			}
			return text.append("</html>").toString();
		}

		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D)graphics.create();
			NodeLabUi.prepare(g);
			int legendHeight = 18 * ((series.size() + 3) / 4);
			int x0 = 44;
			int y0 = 8 + legendHeight;
			int bottom = 26;
			double width = Math.max(10, getWidth() - x0 - 10);
			double plot = Math.max(20, getHeight() - y0 - bottom);
			double maximum = 0.0;
			for (Series s : series)
			{
				for (double value : s.values)
				{
					maximum = Math.max(maximum, value);
				}
			}
			double tick = niceStep(maximum <= 0.0 ? 0.25 : maximum * 1.04 / 4.0);
			maximum = tick * 4.0;
			g.setFont(smallFont());
			for (int i = 0; i < series.size(); i++)
			{
				Series s = series.get(i);
				int lx = (int)(x0 + (i % 4) * (width / 4));
				int ly = 4 + (i / 4) * 18;
				g.setColor(s.color);
				g.fill(new RoundRectangle2D.Double(lx, ly + 4, 12, 4, 3, 3));
				g.setColor(NebulaDesign.TEXT_SECONDARY);
				g.drawString(fit(s.name, g.getFontMetrics(), width / 4 - 22), lx + 16, ly + 10);
			}
			g.setColor(concrete(NebulaDesign.INSET));
			g.fill(new Rectangle2D.Double(x0, y0, width, plot));
			g.setColor(NodeLabUi.alpha(NebulaDesign.TEXT_TERTIARY, 60));
			for (int i = 1; i < 4; i++)
			{
				double y = y0 + plot - i / 4.0 * plot;
				g.draw(new Line2D.Double(x0, y, x0 + width, y));
			}
			g.setColor(NebulaDesign.TEXT_TERTIARY);
			for (int i = 0; i <= 4; i++)
			{
				double value = maximum * i / 4.0;
				String text = unit.equals("%") ? String.format(Locale.US, tick * 100 >= 1.0 ? "%.0f%%" : "%.1f%%",
						Double.valueOf(100 * value)) : String.format(Locale.US, "%.2f", Double.valueOf(value));
				g.drawString(text, 2, (float)(y0 + plot - i / 4.0 * plot + 4));
			}
			int length = length();
			if (length >= 1)
			{
				FontMetrics metrics = g.getFontMetrics();
				int step = 1;
				int widest = 10;
				for (String label : labels)
				{
					widest = Math.max(widest, metrics.stringWidth(label) + 12);
				}
				while (length > 1 && width / ((length - 1.0) / step) < widest)
				{
					step++;
				}
				for (int i = 0; i < labels.length; i += step)
				{
					double x = x0 + (length == 1 ? width / 2 : i / (length - 1.0) * width);
					String label = labels[i];
					g.drawString(label, (float)(x - metrics.stringWidth(label) / 2.0), (float)(y0 + plot + 16));
				}
			}
			for (Series s : series)
			{
				if (s.values.length == 0)
				{
					continue;
				}
				GeneralPath path = new GeneralPath();
				for (int i = 0; i < s.values.length; i++)
				{
					double x = x0 + (length == 1 ? width / 2 : i / (length - 1.0) * width);
					double y = y0 + plot - s.values[i] / maximum * plot;
					if (i == 0)
					{
						path.moveTo(x, y);
					}
					else
					{
						path.lineTo(x, y);
					}
				}
				g.setColor(s.emphasis ? s.color : NodeLabUi.alpha(s.color, 170));
				g.setStroke(new BasicStroke(s.emphasis ? 2.6f : 1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
				g.draw(path);
			}
			g.dispose();
		}
	}
}
