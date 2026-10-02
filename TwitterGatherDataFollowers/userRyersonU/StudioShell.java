package TwitterGatherDataFollowers.userRyersonU;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.swing.JPanel;

/**
 * What the studio tabs share: the brand and page navigation bar, a vertical
 * stack, the empty and loading states, and number formatting.
 */
final class StudioShell
{
	private StudioShell()
	{
	}

	/** Receives a page chosen in the navigation bar. */
	interface PageListener
	{
		void pageChosen(String key);
	}

	/** The brand mark, title and page tabs of a studio. */
	static final class Navigation extends JPanel
	{
		private static final long serialVersionUID = 1L;
		private final String title;
		private final String subtitle;
		private final NodeLabUi.Glyph brand;
		private final Color from;
		private final Color to;
		private final String[] keys;
		private final String[] labels;
		private final NodeLabUi.Glyph[] glyphs;
		private final PageListener listener;
		private final List<int[]> bounds = new ArrayList<int[]>();
		private String current;
		private int hover = -1;

		Navigation(String title, String subtitle, NodeLabUi.Glyph brand, Color from, Color to, String[] keys,
				String[] labels, NodeLabUi.Glyph[] glyphs, PageListener listener)
		{
			this.title = title;
			this.subtitle = subtitle;
			this.brand = brand;
			this.from = from;
			this.to = to;
			this.keys = keys;
			this.labels = labels;
			this.glyphs = glyphs;
			this.listener = listener;
			this.current = keys[0];
			setOpaque(false);
			setPreferredSize(new Dimension(760, 70));
			MouseAdapter mouse = new MouseAdapter()
			{
				public void mouseMoved(MouseEvent event)
				{
					int index = indexAt(event.getX(), event.getY());
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
					int index = indexAt(event.getX(), event.getY());
					if (index >= 0)
					{
						Navigation.this.listener.pageChosen(Navigation.this.keys[index]);
					}
				}
			};
			addMouseListener(mouse);
			addMouseMotionListener(mouse);
		}

		void select(String key)
		{
			current = key;
			repaint();
		}

		private int indexAt(int x, int y)
		{
			for (int index = 0; index < bounds.size(); index++)
			{
				int[] box = bounds.get(index);
				if (x >= box[0] && x <= box[0] + box[2] && y >= box[1] && y <= box[1] + box[3])
				{
					return index;
				}
			}
			return -1;
		}

		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D)graphics.create();
			NodeLabUi.prepare(g);
			int barHeight = 46;
			int top = 14;
			int left = 24;
			g.setPaint(new GradientPaint(left, top, from, left + 36, top + 36, to));
			g.fill(new RoundRectangle2D.Float(left, top + 5, 36, 36, 12, 12));
			g.setColor(Color.WHITE);
			Graphics2D icon = (Graphics2D)g.create();
			icon.translate(left + 8, top + 13);
			NodeLabUi.paintGlyph(icon, brand, 20);
			icon.dispose();
			g.setFont(NebulaDesign.fontSemiBold(15f));
			g.setColor(NebulaDesign.TEXT_PRIMARY);
			g.drawString(title, left + 46, top + 22);
			g.setFont(NebulaDesign.fontMedium(11f));
			g.setColor(NebulaDesign.TEXT_TERTIARY);
			g.drawString(subtitle, left + 46, top + 38);
			g.setFont(NebulaDesign.fontSemiBold(15f));
			int brandWidth = Math.max(g.getFontMetrics().stringWidth(title),
					getFontMetrics(NebulaDesign.fontMedium(11f)).stringWidth(subtitle));
			int trackX = left + 46 + brandWidth + 28;
			g.setFont(NebulaDesign.fontMedium(12.5f));
			FontMetrics metrics = g.getFontMetrics();
			int[] widths = new int[keys.length];
			int total = 0;
			for (int index = 0; index < keys.length; index++)
			{
				widths[index] = metrics.stringWidth(labels[index]) + 44;
				total += widths[index];
			}
			boolean compact = total + 8 > getWidth() - trackX - 12;
			if (compact)
			{
				total = 0;
				for (int index = 0; index < keys.length; index++)
				{
					widths[index] = metrics.stringWidth(labels[index]) + 24;
					total += widths[index];
				}
			}
			g.setColor(NebulaDesign.INSET);
			g.fill(new RoundRectangle2D.Float(trackX, top, total + 8, barHeight, 16, 16));
			bounds.clear();
			int x = trackX + 4;
			for (int index = 0; index < keys.length; index++)
			{
				boolean selected = keys[index].equals(current);
				int width = widths[index];
				bounds.add(new int[] {x, top + 4, width, barHeight - 8});
				if (selected)
				{
					g.setColor(NodeLabUi.alpha(Color.BLACK, NodeLabUi.night() ? 70 : 16));
					g.fill(new RoundRectangle2D.Float(x, top + 5, width, barHeight - 8, 12, 12));
					g.setColor(NodeLabUi.night() ? NebulaDesign.NIGHT_SURFACE_RAISED : NebulaDesign.DAY_SURFACE);
					g.fill(new RoundRectangle2D.Float(x, top + 4, width, barHeight - 8, 12, 12));
				}
				else if (index == hover)
				{
					g.setColor(NodeLabUi.alpha(NebulaDesign.ACCENT, 20));
					g.fill(new RoundRectangle2D.Float(x, top + 4, width, barHeight - 8, 12, 12));
				}
				int cursor = x + 12;
				int centre = top + barHeight / 2;
				if (!compact)
				{
					Graphics2D glyph = (Graphics2D)g.create();
					glyph.setColor(selected ? NebulaDesign.ACCENT : NebulaDesign.TEXT_TERTIARY);
					glyph.translate(cursor, centre - 7);
					NodeLabUi.paintGlyph(glyph, glyphs[index], 14);
					glyph.dispose();
					cursor += 21;
				}
				g.setColor(selected ? NebulaDesign.TEXT_PRIMARY : NebulaDesign.TEXT_SECONDARY);
				g.setFont(selected ? NebulaDesign.fontSemiBold(12.5f) : NebulaDesign.fontMedium(12.5f));
				g.drawString(labels[index], cursor, centre + 5);
				x += width;
			}
			g.dispose();
		}
	}

	/** A vertical stack that keeps {@code gap} between the items added to it later. */
	static JPanel column(int gap)
	{
		return GraphInsightsPanel.column(gap);
	}

	/** A page's empty or loading state. */
	static JPanel waiting(NodeLabUi.Glyph glyph, String title, String message)
	{
		NodeLabUi.Card card = new NodeLabUi.Card(null, null);
		boolean loading = message != null && message.startsWith("Loading");
		card.content(NodeLabUi.emptyState(loading ? NodeLabUi.Glyph.CLOCK : glyph, loading ? "Loading" : title,
				message == null ? "" : message, null));
		NodeLabUi.Page page = new NodeLabUi.Page(1100);
		page.section(card);
		JPanel holder = new JPanel(new BorderLayout());
		holder.setOpaque(false);
		holder.add(NodeLabUi.scroll(page), BorderLayout.CENTER);
		return holder;
	}

	/** Paints the studio canvas gradient. */
	static void paintCanvas(Graphics graphics, int width, int height)
	{
		Graphics2D g = (Graphics2D)graphics.create();
		g.setPaint(new GradientPaint(0, 0, NebulaDesign.CANVAS_TOP, 0, height, NebulaDesign.CANVAS_BOTTOM));
		g.fillRect(0, 0, width, height);
		g.dispose();
	}

	static String fixed(double value, int decimals)
	{
		return Double.isNaN(value) || Double.isInfinite(value) ? "—"
				: String.format(Locale.US, "%." + decimals + "f", Double.valueOf(value));
	}

	static String signed(double value, int decimals)
	{
		return Double.isNaN(value) || Double.isInfinite(value) ? "—"
				: String.format(Locale.US, "%+." + decimals + "f", Double.valueOf(value));
	}

	static String percent(double value)
	{
		return Double.isNaN(value) || Double.isInfinite(value) ? "—"
				: String.format(Locale.US, "%.1f%%", Double.valueOf(100.0 * value));
	}

	static String wholePercent(double value)
	{
		return Double.isNaN(value) || Double.isInfinite(value) ? "—"
				: String.format(Locale.US, "%.0f%%", Double.valueOf(100.0 * value));
	}

	static String count(long value)
	{
		return String.format(Locale.US, "%,d", Long.valueOf(value));
	}
}
