package TwitterGatherDataFollowers.userRyersonU;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.GeneralPath;
import java.awt.geom.Line2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.plaf.basic.BasicComboBoxUI;

/**
 * The Node Lab's visual toolkit, built on the Nebula design tokens: every
 * colour is an adaptive token resolved at paint time, so one component serves
 * the day and night themes. Components are deliberately few and consistent:
 * pages, cards, a hero header, buttons with vector icons, segmented controls,
 * switches, chips, metric tiles, steppers, sliders, fields, combos, help
 * popovers and empty states.
 */
final class NodeLabUi
{
	private NodeLabUi()
	{
	}

	// ------------------------------------------------------------ tones

	enum Tone
	{
		ACCENT,
		SUCCESS,
		WARNING,
		DANGER,
		INFO,
		PURPLE,
		PINK,
		NEUTRAL
	}

	static Color tone(Tone tone)
	{
		switch (tone)
		{
			case SUCCESS:
				return NebulaDesign.SUCCESS;
			case WARNING:
				return NebulaDesign.WARNING;
			case DANGER:
				return NebulaDesign.DANGER;
			case INFO:
				return NebulaDesign.INFO;
			case PURPLE:
				return NebulaDesign.PURPLE;
			case PINK:
				return NebulaDesign.PINK;
			case NEUTRAL:
				return NebulaDesign.TEXT_SECONDARY;
			default:
				return NebulaDesign.ACCENT;
		}
	}

	static Color softTone(Tone tone)
	{
		switch (tone)
		{
			case SUCCESS:
				return NebulaDesign.SUCCESS_SOFT;
			case WARNING:
				return NebulaDesign.WARNING_SOFT;
			case DANGER:
				return NebulaDesign.DANGER_SOFT;
			case INFO:
				return NebulaDesign.INFO_SOFT;
			case PURPLE:
				return NebulaDesign.PURPLE_SOFT;
			case PINK:
				return NebulaDesign.PINK_SOFT;
			case NEUTRAL:
				return NebulaDesign.INSET;
			default:
				return NebulaDesign.ACCENT_SOFT;
		}
	}

	/** A stable identity colour per node (1-based). */
	static Color nodeColor(int node)
	{
		return NebulaDesign.chartColor(Math.max(0, node - 1));
	}

	/** A concrete colour with alpha (tokens resolve first). */
	static Color alpha(Color color, int alpha)
	{
		return new Color(color.getRed(), color.getGreen(), color.getBlue(),
				Math.max(0, Math.min(255, alpha)));
	}

	static boolean night()
	{
		return StudioNightTheme.isNightMode();
	}

	// ------------------------------------------------------------ labels

	/** A label whose colour is resolved at paint time (theme-safe). */
	static final class Text extends JLabel
	{
		private final Color color;

		Text(String text, Font font, Color color)
		{
			super(text);
			this.color = color;
			setFont(font);
		}

		public Color getForeground()
		{
			return color == null ? super.getForeground() : color;
		}
	}

	static Text eyebrow(String text)
	{
		Text label = new Text(text == null ? "" : text.toUpperCase(Locale.US),
				NebulaDesign.fontSemiBold(10.5f), NebulaDesign.ACCENT);
		return label;
	}

	static Text title(String text)
	{
		return new Text(text, NebulaDesign.fontSemiBold(22f), NebulaDesign.TEXT_PRIMARY);
	}

	static Text heading(String text)
	{
		return new Text(text, NebulaDesign.fontSemiBold(15f), NebulaDesign.TEXT_PRIMARY);
	}

	static Text body(String text)
	{
		return new Text(text, NebulaDesign.fontBody(12.5f), NebulaDesign.TEXT_SECONDARY);
	}

	static Text caption(String text)
	{
		return new Text(text, NebulaDesign.fontBody(11.5f), NebulaDesign.TEXT_TERTIARY);
	}

	static Text strong(String text)
	{
		return new Text(text, NebulaDesign.fontMedium(12.5f), NebulaDesign.TEXT_PRIMARY);
	}

	static Text mono(String text)
	{
		return new Text(text, NebulaDesign.fontMono(12f), NebulaDesign.TEXT_PRIMARY);
	}

	/** A wrapping paragraph (non-editable, transparent). */
	static JTextArea paragraph(String text)
	{
		JTextArea area = new JTextArea(text == null ? "" : text)
		{
			private static final long serialVersionUID = 1L;

			public Color getForeground()
			{
				return NebulaDesign.TEXT_SECONDARY;
			}
		};
		area.setEditable(false);
		area.setFocusable(false);
		area.setLineWrap(true);
		area.setWrapStyleWord(true);
		area.setOpaque(false);
		area.setBorder(BorderFactory.createEmptyBorder());
		area.setFont(NebulaDesign.fontBody(12.5f));
		return area;
	}

	/** Wrapping text in a given font and (token) colour; setText re-wraps. */
	static final class Prose extends JTextArea
	{
		private static final long serialVersionUID = 1L;
		private Color color;

		Prose(String text, Font font, Color color)
		{
			super(text == null ? "" : text);
			this.color = color;
			setEditable(false);
			setFocusable(false);
			setLineWrap(true);
			setWrapStyleWord(true);
			setOpaque(false);
			setBorder(BorderFactory.createEmptyBorder());
			setFont(font);
		}

		void setColor(Color value)
		{
			color = value;
			repaint();
		}

		public Color getForeground()
		{
			return color == null ? super.getForeground() : color;
		}

		public void setText(String text)
		{
			String value = text == null ? "" : text;
			if (!value.equals(getText()))
			{
				super.setText(value);
				revalidate();
			}
		}
	}

	/** Secondary body text that wraps. */
	static Prose prose(String text)
	{
		return new Prose(text, NebulaDesign.fontBody(12.5f), NebulaDesign.TEXT_SECONDARY);
	}

	/** Tertiary caption text that wraps. */
	static Prose note(String text)
	{
		return new Prose(text, NebulaDesign.fontBody(11.5f), NebulaDesign.TEXT_TERTIARY);
	}

	/** Keeps a component at its natural height at the top of a taller area. */
	static JPanel top(Component content)
	{
		JPanel panel = new JPanel(new BorderLayout());
		panel.setOpaque(false);
		panel.add(content, BorderLayout.NORTH);
		return panel;
	}

	/**
	 * Holds a component at a fixed width; its height follows the component's
	 * preferred height at that width.
	 */
	static JPanel fixedWidth(Component content, final int width)
	{
		JPanel panel = new JPanel(new BorderLayout())
		{
			private static final long serialVersionUID = 1L;

			public Dimension getPreferredSize()
			{
				return new Dimension(width, super.getPreferredSize().height);
			}

			public Dimension getMinimumSize()
			{
				return new Dimension(width, super.getMinimumSize().height);
			}
		};
		panel.setOpaque(false);
		panel.add(content, BorderLayout.CENTER);
		return panel;
	}

	// ------------------------------------------------------------ layout

	/**
	 * A page: a vertical stack of sections that tracks the viewport's width
	 * (no horizontal scrolling) and keeps content at a comfortable maximum
	 * width, centred.
	 */
	static final class Page extends JPanel implements Scrollable
	{
		private static final long serialVersionUID = 1L;
		private final int maxWidth;
		private final JPanel column = new JPanel();

		Page(int maxWidth)
		{
			super(null);
			this.maxWidth = maxWidth;
			setOpaque(false);
			column.setOpaque(false);
			column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));
			add(column);
		}

		/** Adds a section with the standard gap below it. */
		Page section(JComponent component)
		{
			component.setAlignmentX(Component.LEFT_ALIGNMENT);
			if (column.getComponentCount() > 0)
			{
				column.add(Box.createVerticalStrut(NebulaDesign.SPACE_BETWEEN_CARDS + 2));
			}
			column.add(component);
			return this;
		}

		void clear()
		{
			column.removeAll();
		}

		public void doLayout()
		{
			int width = getWidth();
			int content = Math.min(maxWidth, Math.max(320, width - 48));
			int x = Math.max(24, (width - content) / 2);
			int height = measure(content);
			column.setBounds(x, 20, content, height);
		}

		public Dimension getPreferredSize()
		{
			int width = getParent() == null ? maxWidth + 48 : getParent().getWidth();
			int content = Math.min(maxWidth, Math.max(320, width - 48));
			return new Dimension(width, measure(content) + 48);
		}

		/**
		 * The column's height at a width. Width-dependent children (grids,
		 * wrapped text) know their height only once laid out at their real
		 * width, so the column is laid out and re-measured until it settles.
		 */
		private int measure(int content)
		{
			int height = column.getPreferredSize().height;
			for (int pass = 0; pass < 4; pass++)
			{
				column.setSize(content, height);
				layoutTree(column);
				invalidateTree(column);
				int next = column.getPreferredSize().height;
				if (next == height)
				{
					break;
				}
				height = next;
			}
			return height;
		}

		public Dimension getPreferredScrollableViewportSize()
		{
			return getPreferredSize();
		}

		public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction)
		{
			return 24;
		}

		public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction)
		{
			return Math.max(48, visible.height - 64);
		}

		public boolean getScrollableTracksViewportWidth()
		{
			return true;
		}

		public boolean getScrollableTracksViewportHeight()
		{
			return false;
		}
	}

	/** Lays a component tree out top-down at its current sizes. */
	static void layoutTree(Component component)
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

	/** Drops cached sizes in a tree so preferred sizes are recomputed (for current widths). */
	static void invalidateTree(Component component)
	{
		if (component instanceof Container)
		{
			for (Component child : ((Container)component).getComponents())
			{
				invalidateTree(child);
			}
		}
		component.invalidate();
	}

	/** A borderless, transparent scroll pane for a page, with slim scrollbars. */
	static JScrollPane scroll(Component view)
	{
		JScrollPane pane = new JScrollPane(view);
		pane.setBorder(BorderFactory.createEmptyBorder());
		pane.setViewportBorder(BorderFactory.createEmptyBorder());
		pane.setOpaque(false);
		pane.getViewport().setOpaque(false);
		pane.getVerticalScrollBar().setUnitIncrement(24);
		pane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		StudioNightTheme.applyTabbedPaneTheme(pane);
		return pane;
	}

	/**
	 * Lays children out in equal columns: as many columns of at least
	 * {@code minColumn} pixels as fit (at most {@code maxColumns}), rows as
	 * tall as their tallest child.
	 */
	static final class Grid implements LayoutManager
	{
		private final int minColumn;
		private final int maxColumns;
		private final int gap;
		/** True: every cell of a row is as tall as the row's tallest (cards); false: natural heights. */
		boolean stretch = true;

		Grid(int minColumn, int maxColumns, int gap)
		{
			this.minColumn = minColumn;
			this.maxColumns = Math.max(1, maxColumns);
			this.gap = gap;
		}

		private int columns(int width, int count)
		{
			int fit = Math.max(1, (width + gap) / (minColumn + gap));
			return Math.max(1, Math.min(Math.min(maxColumns, fit), Math.max(1, count)));
		}

		public void addLayoutComponent(String name, Component component)
		{
		}

		public void removeLayoutComponent(Component component)
		{
		}

		public Dimension preferredLayoutSize(Container parent)
		{
			Insets insets = parent.getInsets();
			int width = parent.getWidth() > 0 ? parent.getWidth() - insets.left - insets.right
					: minColumn * Math.min(maxColumns, Math.max(1, parent.getComponentCount()));
			int count = parent.getComponentCount();
			int columns = columns(width, count);
			int columnWidth = Math.max(1, (width - gap * (columns - 1)) / columns);
			int height = 0;
			for (int row = 0; row * columns < count; row++)
			{
				int rowHeight = 0;
				for (int column = 0; column < columns && row * columns + column < count; column++)
				{
					Component child = parent.getComponent(row * columns + column);
					if (child instanceof JComponent)
					{
						((JComponent)child).setSize(columnWidth, child.getHeight());
					}
					rowHeight = Math.max(rowHeight, child.getPreferredSize().height);
				}
				height += rowHeight + (row > 0 ? gap : 0);
			}
			return new Dimension(width + insets.left + insets.right, height + insets.top + insets.bottom);
		}

		public Dimension minimumLayoutSize(Container parent)
		{
			return new Dimension(minColumn, 0);
		}

		public void layoutContainer(Container parent)
		{
			Insets insets = parent.getInsets();
			int width = parent.getWidth() - insets.left - insets.right;
			int count = parent.getComponentCount();
			int columns = columns(width, count);
			int columnWidth = Math.max(1, (width - gap * (columns - 1)) / columns);
			int y = insets.top;
			for (int row = 0; row * columns < count; row++)
			{
				int rowHeight = 0;
				for (int column = 0; column < columns && row * columns + column < count; column++)
				{
					Component child = parent.getComponent(row * columns + column);
					if (child instanceof JComponent)
					{
						((JComponent)child).setSize(columnWidth, child.getHeight());
					}
					rowHeight = Math.max(rowHeight, child.getPreferredSize().height);
				}
				for (int column = 0; column < columns && row * columns + column < count; column++)
				{
					Component child = parent.getComponent(row * columns + column);
					child.setBounds(insets.left + column * (columnWidth + gap), y, columnWidth,
							stretch ? rowHeight : child.getPreferredSize().height);
				}
				y += rowHeight + gap;
			}
		}
	}

	static JPanel grid(int minColumn, int maxColumns, int gap)
	{
		JPanel panel = new JPanel(new Grid(minColumn, maxColumns, gap));
		panel.setOpaque(false);
		return panel;
	}

	/** A grid whose cells keep their natural heights, top-aligned (forms). */
	static JPanel columns(int minColumn, int maxColumns, int gap)
	{
		Grid grid = new Grid(minColumn, maxColumns, gap);
		grid.stretch = false;
		JPanel panel = new JPanel(grid);
		panel.setOpaque(false);
		return panel;
	}

	static JPanel row(int gap, Component... children)
	{
		JPanel panel = new JPanel(new WrapLayout(gap, gap));
		panel.setOpaque(false);
		for (Component child : children)
		{
			if (child != null)
			{
				panel.add(child);
			}
		}
		return panel;
	}

	/** A horizontal row that stretches: left components, flexible space, right components. */
	static JPanel spread(Component left, Component right)
	{
		JPanel panel = new JPanel(new BorderLayout(12, 0))
		{
			private static final long serialVersionUID = 1L;

			public Dimension getMaximumSize()
			{
				return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
			}
		};
		panel.setOpaque(false);
		if (left != null)
		{
			panel.add(left, BorderLayout.CENTER);
		}
		if (right != null)
		{
			panel.add(right, BorderLayout.EAST);
		}
		return panel;
	}

	static JPanel stack(int gap, Component... children)
	{
		JPanel panel = new JPanel()
		{
			private static final long serialVersionUID = 1L;

			/** BoxLayout lines children up by alignment; mixed alignments misplace them. */
			protected void addImpl(Component component, Object constraints, int index)
			{
				if (component instanceof JComponent)
				{
					((JComponent)component).setAlignmentX(Component.LEFT_ALIGNMENT);
				}
				super.addImpl(component, constraints, index);
			}
		};
		panel.setOpaque(false);
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		for (int index = 0; index < children.length; index++)
		{
			if (children[index] == null)
			{
				continue;
			}
			if (panel.getComponentCount() > 0 && gap > 0)
			{
				panel.add(Box.createVerticalStrut(gap));
			}
			if (children[index] instanceof JComponent)
			{
				((JComponent)children[index]).setAlignmentX(Component.LEFT_ALIGNMENT);
			}
			panel.add(children[index]);
		}
		return panel;
	}

	// ------------------------------------------------------------ surfaces

	/** Paints a rounded surface with a soft two-step shadow and a hairline border. */
	static void paintSurface(Graphics2D g, int x, int y, int width, int height, int radius, Color fill,
			boolean shadow)
	{
		if (shadow)
		{
			g.setColor(alpha(Color.BLACK, night() ? 70 : 10));
			g.fill(new RoundRectangle2D.Float(x, y + 2, width, height, radius, radius));
			g.setColor(alpha(Color.BLACK, night() ? 40 : 6));
			g.fill(new RoundRectangle2D.Float(x - 1, y + 4, width + 2, height, radius + 2, radius + 2));
		}
		g.setColor(fill);
		g.fill(new RoundRectangle2D.Float(x, y, width, height, radius, radius));
		g.setColor(NebulaDesign.BORDER_SUBTLE);
		g.draw(new RoundRectangle2D.Float(x + 0.5f, y + 0.5f, width - 1f, height - 1f, radius, radius));
	}

	/**
	 * A card: a rounded surface with an optional header (title, subtitle,
	 * actions on the right) and a body.
	 */
	static class Card extends JPanel
	{
		private static final long serialVersionUID = 1L;
		private final JPanel body = new JPanel(new BorderLayout());
		private final JPanel header;
		private final JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
		private Color fill = NebulaDesign.SURFACE;
		private Color accent;

		Card(String title, String subtitle)
		{
			super(new BorderLayout(0, 14));
			setOpaque(false);
			setBorder(new EmptyBorder(18, 20, 20, 20));
			body.setOpaque(false);
			actions.setOpaque(false);
			if (title != null)
			{
				header = new JPanel(new BorderLayout(12, 0));
				header.setOpaque(false);
				JPanel titles = stack(3, heading(title), subtitle == null ? null : prose(subtitle));
				header.add(titles, BorderLayout.CENTER);
				header.add(actions, BorderLayout.EAST);
				add(header, BorderLayout.NORTH);
			}
			else
			{
				header = null;
			}
			add(body, BorderLayout.CENTER);
		}

		Card content(Component component)
		{
			body.removeAll();
			body.add(component, BorderLayout.CENTER);
			return this;
		}

		Card action(Component component)
		{
			actions.add(component);
			return this;
		}

		Card fill(Color value)
		{
			fill = value;
			return this;
		}

		/** A thin coloured strip on the card's left edge. */
		Card accent(Color value)
		{
			accent = value;
			return this;
		}

		JPanel body()
		{
			return body;
		}

		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D)graphics.create();
			NebulaDesign.prepare(g);
			paintSurface(g, 0, 0, getWidth(), getHeight() - 3, NebulaDesign.RADIUS_CARD, fill, true);
			if (accent != null)
			{
				g.setColor(accent);
				g.fill(new RoundRectangle2D.Float(0, 10, 4, getHeight() - 23, 4, 4));
			}
			g.dispose();
		}
	}

	/**
	 * The page hero: eyebrow, large title, a one-sentence explanation, chips,
	 * and an optional component on the right, over a soft accent glow.
	 */
	static final class Hero extends JPanel
	{
		private static final long serialVersionUID = 1L;
		private final JPanel chips = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
		private final JPanel right = new JPanel(new BorderLayout());
		private final Color glow;

		Hero(String eyebrow, String title, String subtitle, Color glow)
		{
			super(new BorderLayout(24, 0));
			this.glow = glow;
			setOpaque(false);
			setBorder(new EmptyBorder(22, 26, 24, 26));
			chips.setOpaque(false);
			right.setOpaque(false);
			Text titleLabel = new Text(title, NebulaDesign.fontSemiBold(26f), NebulaDesign.TEXT_PRIMARY);
			JTextArea explanation = paragraph(subtitle);
			explanation.setFont(NebulaDesign.fontBody(13f));
			JPanel left = stack(6, eyebrow(eyebrow), titleLabel, explanation, chips);
			add(left, BorderLayout.CENTER);
			add(right, BorderLayout.EAST);
		}

		Hero chip(Chip chip)
		{
			chips.add(chip);
			return this;
		}

		void clearChips()
		{
			chips.removeAll();
			chips.revalidate();
			chips.repaint();
		}

		Hero right(Component component)
		{
			right.removeAll();
			right.add(component, BorderLayout.CENTER);
			return this;
		}

		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D)graphics.create();
			NebulaDesign.prepare(g);
			int w = getWidth();
			int h = getHeight() - 3;
			paintSurface(g, 0, 0, w, h, NebulaDesign.RADIUS_CARD + 4, NebulaDesign.SURFACE, true);
			java.awt.Shape clip = new RoundRectangle2D.Float(1, 1, w - 2, h - 2, NebulaDesign.RADIUS_CARD + 4,
					NebulaDesign.RADIUS_CARD + 4);
			g.clip(clip);
			Color base = glow == null ? NebulaDesign.ACCENT : glow;
			g.setPaint(new java.awt.RadialGradientPaint(w * 0.92f, h * 0.1f, Math.max(w, h) * 0.55f,
					new float[] {0f, 1f},
					new Color[] {alpha(base, night() ? 70 : 38), alpha(base, 0)}));
			g.fillRect(0, 0, w, h);
			g.setPaint(new java.awt.RadialGradientPaint(w * 0.72f, h * 1.1f, Math.max(w, h) * 0.45f,
					new float[] {0f, 1f},
					new Color[] {alpha(NebulaDesign.PURPLE, night() ? 44 : 20), alpha(NebulaDesign.PURPLE, 0)}));
			g.fillRect(0, 0, w, h);
			g.dispose();
		}
	}

	// ------------------------------------------------------------ icons

	enum Glyph
	{
		PLAY, PAUSE, STOP, PLUS, MINUS, TRASH, SAVE, FOLDER, REFRESH, CHART, CLUSTER, BOLT, CLOCK,
		WARNING, CHECK, CROSS, DOWNLOAD, SPARK, NETWORK, CPU, FLASK, LIST, ARROW_RIGHT, INFO, HELP,
		COPY, DRAIN, CRASH, REPAIR, GAUGE, LAYERS, SCRIPT, HOME, SLIDERS, TARGET
	}

	/** A vector line icon in the current theme. */
	static Icon icon(final Glyph glyph, final int size, final Color color)
	{
		return new Icon()
		{
			public int getIconWidth()
			{
				return size;
			}

			public int getIconHeight()
			{
				return size;
			}

			public void paintIcon(Component component, Graphics graphics, int x, int y)
			{
				Graphics2D g = (Graphics2D)graphics.create();
				NebulaDesign.prepare(g);
				Color resolved = color;
				if (color == null && component != null)
				{
					resolved = component.getForeground();
				}
				g.setColor(resolved == null ? NebulaDesign.TEXT_SECONDARY : resolved);
				g.translate(x, y);
				paintGlyph(g, glyph, size);
				g.dispose();
			}
		};
	}

	static void paintGlyph(Graphics2D g, Glyph glyph, int size)
	{
		float s = size / 16f;
		g.setStroke(new BasicStroke(Math.max(1.3f, 1.55f * s), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		switch (glyph)
		{
			case PLAY:
			{
				GeneralPath path = new GeneralPath();
				path.moveTo(4.5f * s, 3f * s);
				path.lineTo(13f * s, 8f * s);
				path.lineTo(4.5f * s, 13f * s);
				path.closePath();
				g.fill(path);
				break;
			}
			case PAUSE:
				g.fill(new RoundRectangle2D.Float(4f * s, 3f * s, 2.8f * s, 10f * s, 1.2f * s, 1.2f * s));
				g.fill(new RoundRectangle2D.Float(9.2f * s, 3f * s, 2.8f * s, 10f * s, 1.2f * s, 1.2f * s));
				break;
			case STOP:
				g.fill(new RoundRectangle2D.Float(3.5f * s, 3.5f * s, 9f * s, 9f * s, 2f * s, 2f * s));
				break;
			case PLUS:
				g.draw(new Line2D.Float(8f * s, 3f * s, 8f * s, 13f * s));
				g.draw(new Line2D.Float(3f * s, 8f * s, 13f * s, 8f * s));
				break;
			case MINUS:
				g.draw(new Line2D.Float(3f * s, 8f * s, 13f * s, 8f * s));
				break;
			case TRASH:
				g.draw(new Line2D.Float(3f * s, 4.5f * s, 13f * s, 4.5f * s));
				g.draw(new RoundRectangle2D.Float(4.5f * s, 4.5f * s, 7f * s, 9f * s, 1.5f * s, 1.5f * s));
				g.draw(new Line2D.Float(6.5f * s, 2.5f * s, 9.5f * s, 2.5f * s));
				break;
			case SAVE:
				g.draw(new RoundRectangle2D.Float(3f * s, 3f * s, 10f * s, 10f * s, 2f * s, 2f * s));
				g.draw(new Line2D.Float(5.5f * s, 3f * s, 5.5f * s, 6.5f * s));
				g.draw(new Line2D.Float(5.5f * s, 6.5f * s, 10.5f * s, 6.5f * s));
				g.draw(new Line2D.Float(10.5f * s, 6.5f * s, 10.5f * s, 3f * s));
				break;
			case FOLDER:
			{
				GeneralPath path = new GeneralPath();
				path.moveTo(2.5f * s, 5f * s);
				path.lineTo(6.5f * s, 5f * s);
				path.lineTo(7.8f * s, 6.3f * s);
				path.lineTo(13.5f * s, 6.3f * s);
				path.lineTo(13.5f * s, 12.5f * s);
				path.lineTo(2.5f * s, 12.5f * s);
				path.closePath();
				g.draw(path);
				break;
			}
			case REFRESH:
				g.draw(new Arc2D.Float(3f * s, 3f * s, 10f * s, 10f * s, 40, 280, Arc2D.OPEN));
				g.draw(new Line2D.Float(12.6f * s, 3.2f * s, 12.6f * s, 6.4f * s));
				g.draw(new Line2D.Float(12.6f * s, 6.4f * s, 9.4f * s, 6.4f * s));
				break;
			case CHART:
				g.draw(new Line2D.Float(3f * s, 13f * s, 13f * s, 13f * s));
				g.fill(new RoundRectangle2D.Float(4f * s, 8f * s, 2.2f * s, 5f * s, 1f * s, 1f * s));
				g.fill(new RoundRectangle2D.Float(7.4f * s, 4f * s, 2.2f * s, 9f * s, 1f * s, 1f * s));
				g.fill(new RoundRectangle2D.Float(10.8f * s, 6.5f * s, 2.2f * s, 6.5f * s, 1f * s, 1f * s));
				break;
			case CLUSTER:
				g.draw(new Ellipse2D.Float(6.3f * s, 6.3f * s, 3.4f * s, 3.4f * s));
				g.draw(new Ellipse2D.Float(1.8f * s, 1.8f * s, 3f * s, 3f * s));
				g.draw(new Ellipse2D.Float(11.2f * s, 1.8f * s, 3f * s, 3f * s));
				g.draw(new Ellipse2D.Float(6.5f * s, 11.2f * s, 3f * s, 3f * s));
				g.draw(new Line2D.Float(4.4f * s, 4.4f * s, 6.6f * s, 6.6f * s));
				g.draw(new Line2D.Float(11.6f * s, 4.4f * s, 9.4f * s, 6.6f * s));
				g.draw(new Line2D.Float(8f * s, 9.8f * s, 8f * s, 11.2f * s));
				break;
			case BOLT:
			{
				GeneralPath path = new GeneralPath();
				path.moveTo(9f * s, 1.8f * s);
				path.lineTo(4f * s, 9f * s);
				path.lineTo(7.8f * s, 9f * s);
				path.lineTo(6.8f * s, 14.2f * s);
				path.lineTo(12f * s, 6.8f * s);
				path.lineTo(8.2f * s, 6.8f * s);
				path.closePath();
				g.fill(path);
				break;
			}
			case CLOCK:
				g.draw(new Ellipse2D.Float(2.5f * s, 2.5f * s, 11f * s, 11f * s));
				g.draw(new Line2D.Float(8f * s, 8f * s, 8f * s, 5f * s));
				g.draw(new Line2D.Float(8f * s, 8f * s, 10.4f * s, 9.4f * s));
				break;
			case WARNING:
			{
				GeneralPath path = new GeneralPath();
				path.moveTo(8f * s, 2.5f * s);
				path.lineTo(14f * s, 13f * s);
				path.lineTo(2f * s, 13f * s);
				path.closePath();
				g.draw(path);
				g.draw(new Line2D.Float(8f * s, 6.5f * s, 8f * s, 9.3f * s));
				g.fill(new Ellipse2D.Float(7.2f * s, 10.4f * s, 1.6f * s, 1.6f * s));
				break;
			}
			case CHECK:
				g.draw(new Line2D.Float(3.5f * s, 8.5f * s, 6.8f * s, 11.6f * s));
				g.draw(new Line2D.Float(6.8f * s, 11.6f * s, 12.5f * s, 4.5f * s));
				break;
			case CROSS:
				g.draw(new Line2D.Float(4f * s, 4f * s, 12f * s, 12f * s));
				g.draw(new Line2D.Float(12f * s, 4f * s, 4f * s, 12f * s));
				break;
			case DOWNLOAD:
				g.draw(new Line2D.Float(8f * s, 2.5f * s, 8f * s, 10f * s));
				g.draw(new Line2D.Float(5f * s, 7f * s, 8f * s, 10f * s));
				g.draw(new Line2D.Float(11f * s, 7f * s, 8f * s, 10f * s));
				g.draw(new Line2D.Float(3f * s, 13f * s, 13f * s, 13f * s));
				break;
			case SPARK:
			{
				GeneralPath path = new GeneralPath();
				path.moveTo(8f * s, 1.5f * s);
				path.quadTo(8.6f * s, 7.4f * s, 14.5f * s, 8f * s);
				path.quadTo(8.6f * s, 8.6f * s, 8f * s, 14.5f * s);
				path.quadTo(7.4f * s, 8.6f * s, 1.5f * s, 8f * s);
				path.quadTo(7.4f * s, 7.4f * s, 8f * s, 1.5f * s);
				g.fill(path);
				break;
			}
			case NETWORK:
				g.draw(new Ellipse2D.Float(2.5f * s, 2.5f * s, 11f * s, 11f * s));
				g.draw(new Line2D.Float(2.5f * s, 8f * s, 13.5f * s, 8f * s));
				g.draw(new Arc2D.Float(5.3f * s, 2.5f * s, 5.4f * s, 11f * s, 0, 360, Arc2D.OPEN));
				break;
			case CPU:
				g.draw(new RoundRectangle2D.Float(4f * s, 4f * s, 8f * s, 8f * s, 2f * s, 2f * s));
				g.draw(new RoundRectangle2D.Float(6.5f * s, 6.5f * s, 3f * s, 3f * s, 1f * s, 1f * s));
				for (int i = 0; i < 3; i++)
				{
					float p = (5.5f + i * 2.5f) * s;
					g.draw(new Line2D.Float(p, 1.8f * s, p, 4f * s));
					g.draw(new Line2D.Float(p, 12f * s, p, 14.2f * s));
					g.draw(new Line2D.Float(1.8f * s, p, 4f * s, p));
					g.draw(new Line2D.Float(12f * s, p, 14.2f * s, p));
				}
				break;
			case FLASK:
			{
				GeneralPath path = new GeneralPath();
				path.moveTo(6f * s, 2.5f * s);
				path.lineTo(6f * s, 6.5f * s);
				path.lineTo(2.8f * s, 12.4f * s);
				path.quadTo(2.6f * s, 13.5f * s, 3.8f * s, 13.5f * s);
				path.lineTo(12.2f * s, 13.5f * s);
				path.quadTo(13.4f * s, 13.5f * s, 13.2f * s, 12.4f * s);
				path.lineTo(10f * s, 6.5f * s);
				path.lineTo(10f * s, 2.5f * s);
				g.draw(path);
				g.draw(new Line2D.Float(5f * s, 2.5f * s, 11f * s, 2.5f * s));
				g.draw(new Line2D.Float(4.5f * s, 10f * s, 11.5f * s, 10f * s));
				break;
			}
			case LIST:
				for (int i = 0; i < 3; i++)
				{
					float yy = (4.5f + i * 3.5f) * s;
					g.fill(new Ellipse2D.Float(2.5f * s, yy - 0.9f * s, 1.8f * s, 1.8f * s));
					g.draw(new Line2D.Float(6f * s, yy, 13.5f * s, yy));
				}
				break;
			case ARROW_RIGHT:
				g.draw(new Line2D.Float(3f * s, 8f * s, 13f * s, 8f * s));
				g.draw(new Line2D.Float(9f * s, 4f * s, 13f * s, 8f * s));
				g.draw(new Line2D.Float(9f * s, 12f * s, 13f * s, 8f * s));
				break;
			case INFO:
				g.draw(new Ellipse2D.Float(2.5f * s, 2.5f * s, 11f * s, 11f * s));
				g.draw(new Line2D.Float(8f * s, 7.3f * s, 8f * s, 11f * s));
				g.fill(new Ellipse2D.Float(7.2f * s, 4.4f * s, 1.6f * s, 1.6f * s));
				break;
			case HELP:
			{
				g.draw(new Ellipse2D.Float(2.5f * s, 2.5f * s, 11f * s, 11f * s));
				GeneralPath path = new GeneralPath();
				path.moveTo(6.3f * s, 6.4f * s);
				path.quadTo(6.3f * s, 4.6f * s, 8f * s, 4.6f * s);
				path.quadTo(9.8f * s, 4.6f * s, 9.8f * s, 6.3f * s);
				path.quadTo(9.8f * s, 7.5f * s, 8f * s, 8.3f * s);
				path.lineTo(8f * s, 9.2f * s);
				g.draw(path);
				g.fill(new Ellipse2D.Float(7.3f * s, 10.6f * s, 1.5f * s, 1.5f * s));
				break;
			}
			case COPY:
				g.draw(new RoundRectangle2D.Float(5.5f * s, 5.5f * s, 8f * s, 8f * s, 1.8f * s, 1.8f * s));
				g.draw(new RoundRectangle2D.Float(2.5f * s, 2.5f * s, 8f * s, 8f * s, 1.8f * s, 1.8f * s));
				break;
			case DRAIN:
				g.draw(new Line2D.Float(3f * s, 5f * s, 13f * s, 5f * s));
				g.draw(new Line2D.Float(8f * s, 5f * s, 8f * s, 13f * s));
				g.draw(new Line2D.Float(5f * s, 10f * s, 8f * s, 13f * s));
				g.draw(new Line2D.Float(11f * s, 10f * s, 8f * s, 13f * s));
				break;
			case CRASH:
			{
				GeneralPath path = new GeneralPath();
				path.moveTo(9.5f * s, 1.5f * s);
				path.lineTo(5.5f * s, 8.2f * s);
				path.lineTo(8.8f * s, 8.2f * s);
				path.lineTo(6.5f * s, 14.5f * s);
				g.draw(path);
				g.draw(new Line2D.Float(10.5f * s, 9.5f * s, 13.5f * s, 12.5f * s));
				g.draw(new Line2D.Float(13.5f * s, 9.5f * s, 10.5f * s, 12.5f * s));
				break;
			}
			case REPAIR:
				g.draw(new Arc2D.Float(2.5f * s, 2.5f * s, 7f * s, 7f * s, 20, 290, Arc2D.OPEN));
				g.draw(new Line2D.Float(8f * s, 8f * s, 13.5f * s, 13.5f * s));
				break;
			case GAUGE:
				g.draw(new Arc2D.Float(2f * s, 3f * s, 12f * s, 12f * s, 0, 180, Arc2D.OPEN));
				g.draw(new Line2D.Float(8f * s, 9f * s, 11.2f * s, 5.4f * s));
				g.fill(new Ellipse2D.Float(7f * s, 8f * s, 2f * s, 2f * s));
				break;
			case LAYERS:
			{
				GeneralPath top = new GeneralPath();
				top.moveTo(8f * s, 2.5f * s);
				top.lineTo(14f * s, 5.8f * s);
				top.lineTo(8f * s, 9f * s);
				top.lineTo(2f * s, 5.8f * s);
				top.closePath();
				g.draw(top);
				GeneralPath bottom = new GeneralPath();
				bottom.moveTo(2f * s, 9.3f * s);
				bottom.lineTo(8f * s, 12.5f * s);
				bottom.lineTo(14f * s, 9.3f * s);
				g.draw(bottom);
				break;
			}
			case SCRIPT:
				g.draw(new RoundRectangle2D.Float(3f * s, 2.5f * s, 10f * s, 11f * s, 2f * s, 2f * s));
				g.draw(new Line2D.Float(5.5f * s, 6f * s, 10.5f * s, 6f * s));
				g.draw(new Line2D.Float(5.5f * s, 8.5f * s, 10.5f * s, 8.5f * s));
				g.draw(new Line2D.Float(5.5f * s, 11f * s, 8.5f * s, 11f * s));
				break;
			case HOME:
			{
				GeneralPath path = new GeneralPath();
				path.moveTo(2.5f * s, 7.5f * s);
				path.lineTo(8f * s, 2.8f * s);
				path.lineTo(13.5f * s, 7.5f * s);
				g.draw(path);
				g.draw(new RoundRectangle2D.Float(4f * s, 7f * s, 8f * s, 6.5f * s, 1.5f * s, 1.5f * s));
				break;
			}
			case SLIDERS:
				for (int i = 0; i < 3; i++)
				{
					float yy = (4f + i * 4f) * s;
					g.draw(new Line2D.Float(2.5f * s, yy, 13.5f * s, yy));
					float knob = (i == 1 ? 10f : i == 0 ? 5f : 7.5f) * s;
					g.fill(new Ellipse2D.Float(knob - 1.6f * s, yy - 1.6f * s, 3.2f * s, 3.2f * s));
				}
				break;
			case TARGET:
				g.draw(new Ellipse2D.Float(2.5f * s, 2.5f * s, 11f * s, 11f * s));
				g.draw(new Ellipse2D.Float(5.5f * s, 5.5f * s, 5f * s, 5f * s));
				g.fill(new Ellipse2D.Float(7.2f * s, 7.2f * s, 1.6f * s, 1.6f * s));
				break;
			default:
				break;
		}
	}

	// ------------------------------------------------------------ buttons

	enum ButtonKind
	{
		PRIMARY,
		SECONDARY,
		GHOST,
		DANGER,
		SUCCESS
	}

	/** A rounded button with an optional leading icon. */
	static final class Button extends JButton
	{
		private static final long serialVersionUID = 1L;
		private final ButtonKind kind;
		private final Glyph glyph;
		private final boolean small;

		Button(String text, ButtonKind kind, Glyph glyph)
		{
			this(text, kind, glyph, false);
		}

		Button(String text, ButtonKind kind, Glyph glyph, boolean small)
		{
			super(text);
			this.kind = kind == null ? ButtonKind.SECONDARY : kind;
			this.glyph = glyph;
			this.small = small;
			setFont(NebulaDesign.fontMedium(small ? 12f : 12.5f));
			setContentAreaFilled(false);
			setBorderPainted(false);
			setFocusPainted(false);
			setOpaque(false);
			setRolloverEnabled(true);
			setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			setHorizontalAlignment(SwingConstants.CENTER);
			setIconTextGap(7);
			int vertical = small ? 5 : 8;
			int horizontal = text == null || text.length() == 0 ? (small ? 7 : 9) : (small ? 11 : 15);
			setBorder(new EmptyBorder(vertical, horizontal, vertical, horizontal));
			if (glyph != null)
			{
				setIcon(icon(glyph, small ? 13 : 14, null));
			}
		}

		public Color getForeground()
		{
			if (kind == null)
			{
				return super.getForeground();
			}
			if (!isEnabled())
			{
				return NebulaDesign.TEXT_TERTIARY;
			}
			switch (kind)
			{
				case PRIMARY:
				case DANGER:
				case SUCCESS:
					return night() ? NebulaDesign.NIGHT_TEXT_ON_ACCENT : NebulaDesign.DAY_TEXT_ON_ACCENT;
				case GHOST:
					return getModel().isRollover() ? NebulaDesign.ACCENT : NebulaDesign.TEXT_SECONDARY;
				default:
					return NebulaDesign.TEXT_PRIMARY;
			}
		}

		public Dimension getPreferredSize()
		{
			Dimension size = super.getPreferredSize();
			return new Dimension(size.width, Math.max(size.height, small ? 28 : 34));
		}

		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D)graphics.create();
			NebulaDesign.prepare(g);
			int w = getWidth();
			int h = getHeight();
			boolean hover = getModel().isRollover() && isEnabled();
			boolean press = getModel().isArmed() && getModel().isPressed();
			int radius = NebulaDesign.RADIUS_CONTROL;
			Color base;
			switch (kind)
			{
				case PRIMARY:
					base = NebulaDesign.ACCENT;
					break;
				case DANGER:
					base = NebulaDesign.DANGER;
					break;
				case SUCCESS:
					base = NebulaDesign.SUCCESS;
					break;
				default:
					base = null;
					break;
			}
			if (base != null)
			{
				Color fill = !isEnabled() ? NebulaDesign.INSET
						: press ? StudioNightTheme.blend(base, Color.BLACK, 0.12f)
						: hover ? StudioNightTheme.blend(base, Color.WHITE, 0.10f) : base;
				if (isEnabled())
				{
					g.setColor(alpha(base, hover ? 70 : 38));
					g.fill(new RoundRectangle2D.Float(0, 1.5f, w, h - 1, radius + 2, radius + 2));
				}
				g.setPaint(new GradientPaint(0, 0, StudioNightTheme.blend(fill, Color.WHITE, isEnabled() ? 0.06f : 0f),
						0, h, fill));
				g.fill(new RoundRectangle2D.Float(0.5f, 0.5f, w - 1f, h - 2f, radius, radius));
			}
			else if (kind == ButtonKind.SECONDARY)
			{
				g.setColor(press ? NebulaDesign.INSET : hover ? NebulaDesign.SURFACE_ALT : NebulaDesign.SURFACE);
				g.fill(new RoundRectangle2D.Float(0.5f, 0.5f, w - 1f, h - 2f, radius, radius));
				g.setColor(hover ? alpha(NebulaDesign.ACCENT, 140) : NebulaDesign.BORDER_STRONG);
				g.draw(new RoundRectangle2D.Float(0.5f, 0.5f, w - 1.5f, h - 2.5f, radius, radius));
			}
			else if (hover || press)
			{
				g.setColor(alpha(NebulaDesign.ACCENT, press ? 34 : 20));
				g.fill(new RoundRectangle2D.Float(0.5f, 0.5f, w - 1f, h - 2f, radius, radius));
			}
			g.dispose();
			super.paintComponent(graphics);
		}
	}

	/** A small square icon-only button with a tooltip. */
	static Button iconButton(Glyph glyph, String tooltip)
	{
		Button button = new Button("", ButtonKind.GHOST, glyph, true);
		button.setToolTipText(tooltip);
		return button;
	}

	// ------------------------------------------------------------ chips

	/** A rounded pill: optional status dot, tone colour on a soft tone fill. */
	static final class Chip extends JComponent
	{
		private static final long serialVersionUID = 1L;
		private String text;
		private Tone tone;
		private final boolean dot;

		Chip(String text, Tone tone, boolean dot)
		{
			this.text = text == null ? "" : text;
			this.tone = tone == null ? Tone.NEUTRAL : tone;
			this.dot = dot;
			setFont(NebulaDesign.fontSemiBold(11f));
			setOpaque(false);
		}

		void set(String value, Tone newTone)
		{
			text = value == null ? "" : value;
			tone = newTone == null ? Tone.NEUTRAL : newTone;
			revalidate();
			repaint();
		}

		String getText()
		{
			return text;
		}

		public Dimension getPreferredSize()
		{
			FontMetrics metrics = getFontMetrics(getFont());
			return new Dimension(metrics.stringWidth(text) + (dot ? 28 : 20), 22);
		}

		public Dimension getMaximumSize()
		{
			return getPreferredSize();
		}

		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D)graphics.create();
			NebulaDesign.prepare(g);
			//A pill of its natural size, centred in whatever space it was given.
			int w = Math.min(getWidth(), getPreferredSize().width);
			int h = Math.min(getHeight(), 22);
			g.translate(0, Math.max(0, (getHeight() - h) / 2));
			g.setColor(softTone(tone));
			g.fill(new RoundRectangle2D.Float(0, 0, w, h, h, h));
			g.setColor(alpha(tone(tone), night() ? 90 : 60));
			g.draw(new RoundRectangle2D.Float(0.5f, 0.5f, w - 1f, h - 1f, h, h));
			int x = 10;
			if (dot)
			{
				g.setColor(tone(tone));
				g.fill(new Ellipse2D.Float(9, h / 2f - 3f, 6, 6));
				x = 19;
			}
			g.setColor(tone(tone));
			g.setFont(getFont());
			FontMetrics metrics = g.getFontMetrics();
			g.drawString(text, x, (h + metrics.getAscent() - metrics.getDescent()) / 2);
			g.dispose();
		}
	}

	/** A pill that toggles on and off (multi-select choices). */
	static final class ToggleChip extends JComponent
	{
		private static final long serialVersionUID = 1L;
		private final String text;
		private boolean selected;
		private boolean hover;
		private final List<ChangeListener> listeners = new ArrayList<ChangeListener>();
		private final Object value;

		ToggleChip(String text, Object value, boolean selected)
		{
			this.text = text == null ? "" : text;
			this.value = value;
			this.selected = selected;
			setFont(NebulaDesign.fontMedium(12f));
			setOpaque(false);
			setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			setFocusable(true);
			addMouseListener(new MouseAdapter()
			{
				public void mouseEntered(MouseEvent event)
				{
					hover = true;
					repaint();
				}

				public void mouseExited(MouseEvent event)
				{
					hover = false;
					repaint();
				}

				public void mousePressed(MouseEvent event)
				{
					if (isEnabled())
					{
						setSelected(!ToggleChip.this.selected);
					}
				}
			});
			addKeyListener(new java.awt.event.KeyAdapter()
			{
				public void keyPressed(java.awt.event.KeyEvent event)
				{
					if (isEnabled() && (event.getKeyCode() == java.awt.event.KeyEvent.VK_SPACE
							|| event.getKeyCode() == java.awt.event.KeyEvent.VK_ENTER))
					{
						setSelected(!ToggleChip.this.selected);
					}
				}
			});
		}

		Object getValue()
		{
			return value;
		}

		String getText()
		{
			return text;
		}

		boolean isSelected()
		{
			return selected;
		}

		void setSelected(boolean next)
		{
			if (next != selected)
			{
				selected = next;
				repaint();
				ChangeEvent event = new ChangeEvent(this);
				for (ChangeListener listener : new ArrayList<ChangeListener>(listeners))
				{
					listener.stateChanged(event);
				}
			}
		}

		void setSelectedQuietly(boolean next)
		{
			selected = next;
			repaint();
		}

		void addChangeListener(ChangeListener listener)
		{
			listeners.add(listener);
		}

		public Dimension getPreferredSize()
		{
			FontMetrics metrics = getFontMetrics(getFont());
			return new Dimension(metrics.stringWidth(text) + (selected ? 40 : 26), 30);
		}

		public Dimension getMaximumSize()
		{
			return getPreferredSize();
		}

		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D)graphics.create();
			prepare(g);
			int w = getWidth();
			int h = getHeight();
			Color fill = selected ? (isEnabled() ? NebulaDesign.ACCENT : NebulaDesign.BORDER_STRONG)
					: hover ? NebulaDesign.SURFACE_ALT : NebulaDesign.INSET;
			g.setColor(fill);
			g.fill(new RoundRectangle2D.Float(0.5f, 0.5f, w - 1f, h - 1f, h, h));
			if (!selected)
			{
				g.setColor(hover ? alpha(NebulaDesign.ACCENT, 120) : NebulaDesign.BORDER_SUBTLE);
				g.draw(new RoundRectangle2D.Float(0.5f, 0.5f, w - 1.5f, h - 1.5f, h, h));
			}
			if (isFocusOwner())
			{
				g.setColor(alpha(NebulaDesign.ACCENT, 90));
				g.setStroke(new BasicStroke(2f));
				g.draw(new RoundRectangle2D.Float(1f, 1f, w - 2.5f, h - 2.5f, h, h));
			}
			int x = 13;
			if (selected)
			{
				g.setColor(night() ? NebulaDesign.NIGHT_TEXT_ON_ACCENT : NebulaDesign.DAY_TEXT_ON_ACCENT);
				g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
				float cy = h / 2f;
				g.draw(new Line2D.Float(12f, cy, 15f, cy + 3f));
				g.draw(new Line2D.Float(15f, cy + 3f, 21f, cy - 3.5f));
				x = 27;
			}
			g.setFont(getFont());
			FontMetrics metrics = g.getFontMetrics();
			g.setColor(selected ? (night() ? NebulaDesign.NIGHT_TEXT_ON_ACCENT : NebulaDesign.DAY_TEXT_ON_ACCENT)
					: isEnabled() ? NebulaDesign.TEXT_SECONDARY : NebulaDesign.TEXT_TERTIARY);
			g.drawString(text, x, (h + metrics.getAscent() - metrics.getDescent()) / 2);
			g.dispose();
		}
	}

	/** A wrapping row of toggle chips (multi-select). */
	static final class ToggleGroup extends JPanel
	{
		private static final long serialVersionUID = 1L;
		private final List<ToggleChip> chips = new ArrayList<ToggleChip>();
		private final List<ChangeListener> listeners = new ArrayList<ChangeListener>();

		ToggleGroup()
		{
			super(new WrapLayout(6, 6));
			setOpaque(false);
		}

		ToggleChip add(String text, Object value, boolean selected)
		{
			ToggleChip chip = new ToggleChip(text, value, selected);
			chip.addChangeListener(new ChangeListener()
			{
				public void stateChanged(ChangeEvent event)
				{
					revalidate();
					ChangeEvent changed = new ChangeEvent(ToggleGroup.this);
					for (ChangeListener listener : new ArrayList<ChangeListener>(listeners))
					{
						listener.stateChanged(changed);
					}
				}
			});
			chips.add(chip);
			add(chip);
			return chip;
		}

		void clear()
		{
			chips.clear();
			removeAll();
		}

		List<Object> selectedValues()
		{
			ArrayList<Object> values = new ArrayList<Object>();
			for (ToggleChip chip : chips)
			{
				if (chip.isSelected())
				{
					values.add(chip.getValue());
				}
			}
			return values;
		}

		List<ToggleChip> chips()
		{
			return chips;
		}

		void addChangeListener(ChangeListener listener)
		{
			listeners.add(listener);
		}

		public void setEnabled(boolean enabled)
		{
			super.setEnabled(enabled);
			for (ToggleChip chip : chips)
			{
				chip.setEnabled(enabled);
			}
		}
	}

	/**
	 * A flow layout that wraps to the container's width and reports the
	 * height it needs at that width (the plain FlowLayout reports one line).
	 */
	static final class WrapLayout implements LayoutManager
	{
		private final int hgap;
		private final int vgap;

		WrapLayout(int hgap, int vgap)
		{
			this.hgap = hgap;
			this.vgap = vgap;
		}

		public void addLayoutComponent(String name, Component component)
		{
		}

		public void removeLayoutComponent(Component component)
		{
		}

		private int width(Container parent)
		{
			int width = parent.getWidth();
			if (width <= 0 && parent.getParent() != null)
			{
				width = parent.getParent().getWidth();
			}
			return width <= 0 ? 600 : width;
		}

		public Dimension preferredLayoutSize(Container parent)
		{
			Insets insets = parent.getInsets();
			int max = width(parent) - insets.left - insets.right;
			int x = 0;
			int y = 0;
			int rowHeight = 0;
			int widest = 0;
			for (Component child : parent.getComponents())
			{
				if (!child.isVisible())
				{
					continue;
				}
				Dimension size = child.getPreferredSize();
				if (x > 0 && x + size.width > max)
				{
					y += rowHeight + vgap;
					x = 0;
					rowHeight = 0;
				}
				x += size.width + hgap;
				widest = Math.max(widest, x - hgap);
				rowHeight = Math.max(rowHeight, size.height);
			}
			return new Dimension(Math.min(widest, max) + insets.left + insets.right,
					y + rowHeight + insets.top + insets.bottom);
		}

		public Dimension minimumLayoutSize(Container parent)
		{
			return new Dimension(0, preferredLayoutSize(parent).height);
		}

		public void layoutContainer(Container parent)
		{
			Insets insets = parent.getInsets();
			int max = parent.getWidth() - insets.left - insets.right;
			int x = 0;
			int y = 0;
			int rowHeight = 0;
			for (Component child : parent.getComponents())
			{
				if (!child.isVisible())
				{
					continue;
				}
				Dimension size = child.getPreferredSize();
				if (x > 0 && x + size.width > max)
				{
					y += rowHeight + vgap;
					x = 0;
					rowHeight = 0;
				}
				child.setBounds(insets.left + x, insets.top + y, size.width, size.height);
				x += size.width + hgap;
				rowHeight = Math.max(rowHeight, size.height);
			}
		}
	}

	// ------------------------------------------------------------ metric tile

	/** A metric tile: eyebrow label, large value, caption, accent dot. */
	static final class Metric extends JPanel
	{
		private static final long serialVersionUID = 1L;
		private final Text value;
		private final Text detail;
		private Color accent;

		Metric(String label, String initial, String caption, Color accent)
		{
			super(new BorderLayout());
			this.accent = accent == null ? NebulaDesign.ACCENT : accent;
			setOpaque(false);
			setBorder(new EmptyBorder(14, 16, 14, 16));
			Text labelText = new Text(label.toUpperCase(Locale.US), NebulaDesign.fontSemiBold(10.5f),
					NebulaDesign.TEXT_TERTIARY);
			labelText.setBorder(new EmptyBorder(0, 14, 0, 0));
			value = new Text(initial, NebulaDesign.fontSemiBold(23f), NebulaDesign.TEXT_PRIMARY);
			detail = caption(caption == null ? " " : caption);
			add(stack(4, labelText, value, detail), BorderLayout.CENTER);
		}

		void set(String newValue, String newDetail)
		{
			value.setText(newValue == null ? "—" : newValue);
			if (newDetail != null)
			{
				detail.setText(newDetail);
			}
		}

		void accent(Color value)
		{
			accent = value;
			repaint();
		}

		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D)graphics.create();
			NebulaDesign.prepare(g);
			paintSurface(g, 0, 0, getWidth(), getHeight() - 3, NebulaDesign.RADIUS_CARD, NebulaDesign.SURFACE, true);
			g.setColor(accent);
			g.fill(new Ellipse2D.Float(16, 18, 7, 7));
			g.dispose();
		}
	}

	// ------------------------------------------------------------ segmented

	/** A segmented control: one of several options, like a macOS picker. */
	static final class Segmented extends JComponent
	{
		private static final long serialVersionUID = 1L;
		private final String[] options;
		private final Glyph[] glyphs;
		private int selected;
		private int hover = -1;
		private final List<ChangeListener> listeners = new ArrayList<ChangeListener>();

		Segmented(String[] options, Glyph[] glyphs, int selected)
		{
			this.options = options;
			this.glyphs = glyphs;
			this.selected = Math.max(0, Math.min(options.length - 1, selected));
			setFont(NebulaDesign.fontMedium(12.5f));
			setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			MouseAdapter mouse = new MouseAdapter()
			{
				public void mouseMoved(MouseEvent event)
				{
					int index = indexAt(event.getX());
					if (index != hover)
					{
						hover = index;
						repaint();
					}
				}

				public void mouseExited(MouseEvent event)
				{
					hover = -1;
					repaint();
				}

				public void mousePressed(MouseEvent event)
				{
					if (!isEnabled())
					{
						return;
					}
					int index = indexAt(event.getX());
					if (index >= 0 && index != Segmented.this.selected)
					{
						setSelected(index);
					}
				}
			};
			addMouseListener(mouse);
			addMouseMotionListener(mouse);
		}

		int getSelected()
		{
			return selected;
		}

		void setSelected(int index)
		{
			if (index < 0 || index >= options.length)
			{
				return;
			}
			selected = index;
			repaint();
			ChangeEvent event = new ChangeEvent(this);
			for (ChangeListener listener : new ArrayList<ChangeListener>(listeners))
			{
				listener.stateChanged(event);
			}
		}

		/** Selects without notifying listeners (programmatic updates). */
		void setSelectedQuietly(int index)
		{
			if (index >= 0 && index < options.length)
			{
				selected = index;
				repaint();
			}
		}

		void addChangeListener(ChangeListener listener)
		{
			listeners.add(listener);
		}

		private int[] widths()
		{
			FontMetrics metrics = getFontMetrics(getFont());
			int[] widths = new int[options.length];
			for (int index = 0; index < options.length; index++)
			{
				widths[index] = metrics.stringWidth(options[index]) + 30
						+ (glyphs != null && glyphs[index] != null ? 20 : 0);
			}
			return widths;
		}

		private int indexAt(int x)
		{
			int[] widths = scaledWidths();
			int position = 4;
			for (int index = 0; index < widths.length; index++)
			{
				if (x >= position && x < position + widths[index])
				{
					return index;
				}
				position += widths[index];
			}
			return -1;
		}

		private int[] scaledWidths()
		{
			int[] natural = widths();
			int total = 0;
			for (int width : natural)
			{
				total += width;
			}
			int available = getWidth() - 8;
			if (available <= total)
			{
				return natural;
			}
			int[] scaled = new int[natural.length];
			int used = 0;
			for (int index = 0; index < natural.length; index++)
			{
				scaled[index] = index == natural.length - 1 ? available - used
						: natural[index] + (available - total) / natural.length;
				used += scaled[index];
			}
			return scaled;
		}

		public Dimension getPreferredSize()
		{
			int total = 8;
			for (int width : widths())
			{
				total += width;
			}
			return new Dimension(total, 38);
		}

		public Dimension getMaximumSize()
		{
			return new Dimension(Integer.MAX_VALUE, 38);
		}

		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D)graphics.create();
			NebulaDesign.prepare(g);
			int h = getHeight();
			g.setColor(NebulaDesign.INSET);
			g.fill(new RoundRectangle2D.Float(0, 0, getWidth(), h, 12, 12));
			int[] widths = scaledWidths();
			int x = 4;
			g.setFont(getFont());
			FontMetrics metrics = g.getFontMetrics();
			for (int index = 0; index < options.length; index++)
			{
				int w = widths[index];
				if (index == selected)
				{
					g.setColor(alpha(Color.BLACK, night() ? 60 : 16));
					g.fill(new RoundRectangle2D.Float(x, 5, w, h - 8, 9, 9));
					g.setColor(night() ? NebulaDesign.NIGHT_SURFACE_RAISED : NebulaDesign.DAY_SURFACE);
					g.fill(new RoundRectangle2D.Float(x, 4, w, h - 8, 9, 9));
				}
				else if (index == hover)
				{
					g.setColor(alpha(NebulaDesign.ACCENT, 18));
					g.fill(new RoundRectangle2D.Float(x, 4, w, h - 8, 9, 9));
				}
				Color color = index == selected ? NebulaDesign.TEXT_PRIMARY
						: isEnabled() ? NebulaDesign.TEXT_SECONDARY : NebulaDesign.TEXT_TERTIARY;
				int textWidth = metrics.stringWidth(options[index]);
				boolean hasGlyph = glyphs != null && glyphs[index] != null;
				int content = textWidth + (hasGlyph ? 20 : 0);
				int start = x + (w - content) / 2;
				if (hasGlyph)
				{
					Graphics2D iconGraphics = (Graphics2D)g.create();
					iconGraphics.setColor(index == selected ? NebulaDesign.ACCENT : color);
					iconGraphics.translate(start, h / 2 - 7);
					paintGlyph(iconGraphics, glyphs[index], 14);
					iconGraphics.dispose();
					start += 20;
				}
				g.setColor(color);
				g.drawString(options[index], start, (h + metrics.getAscent() - metrics.getDescent()) / 2);
				x += w;
			}
			g.dispose();
		}
	}

	// ------------------------------------------------------------ switch

	/** An on/off switch. */
	static final class Switch extends JComponent
	{
		private static final long serialVersionUID = 1L;
		private boolean on;
		private final List<ChangeListener> listeners = new ArrayList<ChangeListener>();

		Switch(boolean on)
		{
			this.on = on;
			setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			addMouseListener(new MouseAdapter()
			{
				public void mousePressed(MouseEvent event)
				{
					if (isEnabled())
					{
						setOn(!Switch.this.on);
					}
				}
			});
		}

		boolean isOn()
		{
			return on;
		}

		void setOn(boolean value)
		{
			on = value;
			repaint();
			ChangeEvent event = new ChangeEvent(this);
			for (ChangeListener listener : new ArrayList<ChangeListener>(listeners))
			{
				listener.stateChanged(event);
			}
		}

		void setOnQuietly(boolean value)
		{
			on = value;
			repaint();
		}

		void addChangeListener(ChangeListener listener)
		{
			listeners.add(listener);
		}

		public Dimension getPreferredSize()
		{
			return new Dimension(38, 22);
		}

		public Dimension getMaximumSize()
		{
			return getPreferredSize();
		}

		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D)graphics.create();
			NebulaDesign.prepare(g);
			int w = 38;
			int h = 22;
			int y = (getHeight() - h) / 2;
			g.setColor(on ? (isEnabled() ? NebulaDesign.ACCENT : NebulaDesign.BORDER_STRONG) : NebulaDesign.INSET);
			g.fill(new RoundRectangle2D.Float(0, y, w, h, h, h));
			if (!on)
			{
				g.setColor(NebulaDesign.BORDER_STRONG);
				g.draw(new RoundRectangle2D.Float(0.5f, y + 0.5f, w - 1f, h - 1f, h, h));
			}
			g.setColor(alpha(Color.BLACK, 40));
			float knobX = on ? w - h + 2 : 2;
			g.fill(new Ellipse2D.Float(knobX, y + 3, h - 4, h - 4));
			g.setColor(Color.WHITE);
			g.fill(new Ellipse2D.Float(knobX, y + 2, h - 4, h - 4));
			g.dispose();
		}
	}

	// ------------------------------------------------------------ stepper

	/** A compact integer stepper: minus, value, plus. */
	static final class Stepper extends JPanel
	{
		private static final long serialVersionUID = 1L;
		private int value;
		private final int minimum;
		private final int maximum;
		private final Text label;
		private final List<ChangeListener> listeners = new ArrayList<ChangeListener>();

		Stepper(int value, int minimum, int maximum)
		{
			super(new BorderLayout(0, 0));
			this.minimum = minimum;
			this.maximum = maximum;
			this.value = Math.max(minimum, Math.min(maximum, value));
			setOpaque(false);
			Button minus = new Button("", ButtonKind.GHOST, Glyph.MINUS, true);
			Button plus = new Button("", ButtonKind.GHOST, Glyph.PLUS, true);
			label = new Text(Integer.toString(this.value), NebulaDesign.fontSemiBold(13f), NebulaDesign.TEXT_PRIMARY);
			label.setHorizontalAlignment(SwingConstants.CENTER);
			label.setPreferredSize(new Dimension(34, 26));
			minus.addActionListener(new ActionListener()
			{
				public void actionPerformed(ActionEvent event)
				{
					setValue(Stepper.this.value - 1);
				}
			});
			plus.addActionListener(new ActionListener()
			{
				public void actionPerformed(ActionEvent event)
				{
					setValue(Stepper.this.value + 1);
				}
			});
			add(minus, BorderLayout.WEST);
			add(label, BorderLayout.CENTER);
			add(plus, BorderLayout.EAST);
		}

		int getValue()
		{
			return value;
		}

		void setValue(int next)
		{
			int clamped = Math.max(minimum, Math.min(maximum, next));
			if (clamped == value)
			{
				return;
			}
			value = clamped;
			label.setText(Integer.toString(value));
			ChangeEvent event = new ChangeEvent(this);
			for (ChangeListener listener : new ArrayList<ChangeListener>(listeners))
			{
				listener.stateChanged(event);
			}
		}

		void setValueQuietly(int next)
		{
			value = Math.max(minimum, Math.min(maximum, next));
			label.setText(Integer.toString(value));
		}

		void addChangeListener(ChangeListener listener)
		{
			listeners.add(listener);
		}

		public Dimension getMaximumSize()
		{
			return getPreferredSize();
		}

		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D)graphics.create();
			NebulaDesign.prepare(g);
			g.setColor(NebulaDesign.INSET);
			g.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 10, 10));
			g.dispose();
		}
	}

	// ------------------------------------------------------------ slider

	/** Maps slider positions to values (linear or logarithmic). */
	interface Scale
	{
		double value(double fraction);

		double fraction(double value);
	}

	static Scale linear(final double minimum, final double maximum)
	{
		return new Scale()
		{
			public double value(double fraction)
			{
				return minimum + (maximum - minimum) * fraction;
			}

			public double fraction(double value)
			{
				return maximum == minimum ? 0.0 : Math.max(0.0, Math.min(1.0, (value - minimum) / (maximum - minimum)));
			}
		};
	}

	/** Logarithmic between minimum and maximum (both positive); 0 maps to the left end. */
	static Scale logarithmic(final double minimum, final double maximum)
	{
		return new Scale()
		{
			public double value(double fraction)
			{
				return minimum * Math.pow(maximum / minimum, fraction);
			}

			public double fraction(double value)
			{
				if (value <= minimum)
				{
					return 0.0;
				}
				return Math.max(0.0, Math.min(1.0, Math.log(value / minimum) / Math.log(maximum / minimum)));
			}
		};
	}

	/** Formats a slider value for its readout. */
	interface Formatter
	{
		String format(double value);
	}

	/** A slider with a label, a live value readout and optional snapping. */
	static final class Slider extends JComponent
	{
		private static final long serialVersionUID = 1L;
		private final String label;
		private final Scale scale;
		private final Formatter formatter;
		private final double step;
		private double value;
		private boolean dragging;
		private boolean changedWhileDragging;
		private Color color = NebulaDesign.ACCENT;
		private final List<ChangeListener> listeners = new ArrayList<ChangeListener>();

		Slider(String label, double value, Scale scale, double step, Formatter formatter)
		{
			this.label = label;
			this.scale = scale;
			this.step = step;
			this.formatter = formatter;
			this.value = value;
			setFont(NebulaDesign.fontMedium(12f));
			setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			MouseAdapter mouse = new MouseAdapter()
			{
				public void mousePressed(MouseEvent event)
				{
					if (isEnabled())
					{
						dragging = true;
						track(event.getX());
					}
				}

				public void mouseDragged(MouseEvent event)
				{
					if (dragging)
					{
						track(event.getX());
					}
				}

				public void mouseReleased(MouseEvent event)
				{
					if (dragging)
					{
						dragging = false;
						if (changedWhileDragging)
						{
							changedWhileDragging = false;
							fire();
						}
					}
					repaint();
				}
			};
			addMouseListener(mouse);
			addMouseMotionListener(mouse);
			setFocusable(true);
			addKeyListener(new java.awt.event.KeyAdapter()
			{
				public void keyPressed(java.awt.event.KeyEvent event)
				{
					if (!isEnabled())
					{
						return;
					}
					int code = event.getKeyCode();
					double delta = code == java.awt.event.KeyEvent.VK_RIGHT || code == java.awt.event.KeyEvent.VK_UP
							? 0.02 : code == java.awt.event.KeyEvent.VK_LEFT || code == java.awt.event.KeyEvent.VK_DOWN
							? -0.02 : 0.0;
					if (delta != 0.0)
					{
						double fraction = Math.max(0.0, Math.min(1.0, Slider.this.scale.fraction(value) + delta));
						double next = Slider.this.scale.value(fraction);
						if (step > 0.0)
						{
							double snapped = Math.round(next / step) * step;
							next = Math.abs(snapped - value) < 1e-12 ? snapped + Math.signum(delta) * step : snapped;
						}
						setValue(next);
						event.consume();
					}
				}
			});
			addFocusListener(new java.awt.event.FocusAdapter()
			{
				public void focusGained(java.awt.event.FocusEvent event)
				{
					repaint();
				}

				public void focusLost(java.awt.event.FocusEvent event)
				{
					repaint();
				}
			});
		}

		/** True while the thumb is being dragged; a final change follows the release. */
		boolean isAdjusting()
		{
			return dragging;
		}

		Slider color(Color value)
		{
			color = value;
			return this;
		}

		private void track(int x)
		{
			int left = 9;
			int right = getWidth() - 9;
			double fraction = Math.max(0.0, Math.min(1.0, (x - left) / (double)Math.max(1, right - left)));
			double next = scale.value(fraction);
			if (step > 0.0)
			{
				next = Math.round(next / step) * step;
			}
			setValue(next);
		}

		double getValue()
		{
			return value;
		}

		void setValue(double next)
		{
			if (Math.abs(next - value) < 1e-12)
			{
				return;
			}
			value = next;
			repaint();
			if (dragging)
			{
				changedWhileDragging = true;
			}
			fire();
		}

		private void fire()
		{
			ChangeEvent event = new ChangeEvent(this);
			for (ChangeListener listener : new ArrayList<ChangeListener>(listeners))
			{
				listener.stateChanged(event);
			}
		}

		void setValueQuietly(double next)
		{
			value = next;
			repaint();
		}

		void addChangeListener(ChangeListener listener)
		{
			listeners.add(listener);
		}

		public Dimension getPreferredSize()
		{
			return new Dimension(180, 44);
		}

		public Dimension getMaximumSize()
		{
			return new Dimension(Integer.MAX_VALUE, 44);
		}

		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D)graphics.create();
			NebulaDesign.prepare(g);
			int w = getWidth();
			g.setFont(NebulaDesign.fontMedium(12f));
			FontMetrics metrics = g.getFontMetrics();
			g.setColor(NebulaDesign.TEXT_SECONDARY);
			g.drawString(label, 0, metrics.getAscent());
			String readout = formatter == null ? String.format(Locale.US, "%.2f", value) : formatter.format(value);
			g.setFont(NebulaDesign.fontSemiBold(12f));
			FontMetrics bold = g.getFontMetrics();
			g.setColor(NebulaDesign.TEXT_PRIMARY);
			g.drawString(readout, w - bold.stringWidth(readout), bold.getAscent());
			int trackY = 30;
			g.setColor(NebulaDesign.INSET);
			g.fill(new RoundRectangle2D.Float(2, trackY - 2, w - 4, 5, 5, 5));
			double fraction = scale.fraction(value);
			float knobX = (float)(9 + fraction * (w - 18));
			Color active = isEnabled() ? color : NebulaDesign.BORDER_STRONG;
			g.setColor(active);
			g.fill(new RoundRectangle2D.Float(2, trackY - 2, Math.max(4f, knobX - 2), 5, 5, 5));
			if (isFocusOwner())
			{
				g.setColor(alpha(active, 60));
				g.fill(new Ellipse2D.Float(knobX - 13, trackY - 13, 26, 26));
			}
			g.setColor(alpha(Color.BLACK, 45));
			g.fill(new Ellipse2D.Float(knobX - 8, trackY - 7, 16, 16));
			g.setColor(Color.WHITE);
			g.fill(new Ellipse2D.Float(knobX - 8, trackY - 8, 16, 16));
			g.setColor(active);
			g.setStroke(new BasicStroke(dragging ? 3f : 2f));
			g.draw(new Ellipse2D.Float(knobX - 7, trackY - 7, 14, 14));
			g.dispose();
		}
	}

	// ------------------------------------------------------------ fields

	/** A text field on an inset rounded background with a focus ring. */
	static final class Field extends JTextField
	{
		private static final long serialVersionUID = 1L;
		private final String placeholder;

		Field(String text, int columns, String placeholder)
		{
			super(text, columns);
			this.placeholder = placeholder;
			setOpaque(false);
			setFont(NebulaDesign.fontBody(12.5f));
			setBorder(new EmptyBorder(7, 11, 7, 11));
			setCaretColor(NebulaDesign.ACCENT);
			addFocusListener(new java.awt.event.FocusAdapter()
			{
				public void focusGained(java.awt.event.FocusEvent event)
				{
					repaint();
				}

				public void focusLost(java.awt.event.FocusEvent event)
				{
					repaint();
				}
			});
		}

		public Color getForeground()
		{
			return NebulaDesign.TEXT_PRIMARY;
		}

		public Color getSelectionColor()
		{
			return NebulaDesign.SELECTION;
		}

		public Dimension getMaximumSize()
		{
			return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
		}

		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D)graphics.create();
			NebulaDesign.prepare(g);
			g.setColor(NebulaDesign.INSET);
			g.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 10, 10));
			g.setColor(isFocusOwner() ? NebulaDesign.ACCENT : NebulaDesign.BORDER_SUBTLE);
			g.setStroke(new BasicStroke(isFocusOwner() ? 1.6f : 1f));
			g.draw(new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1f, getHeight() - 1f, 10, 10));
			g.dispose();
			super.paintComponent(graphics);
			if (getText().length() == 0 && placeholder != null && !isFocusOwner())
			{
				Graphics2D hint = (Graphics2D)graphics.create();
				NebulaDesign.prepare(hint);
				hint.setColor(NebulaDesign.TEXT_TERTIARY);
				hint.setFont(getFont());
				FontMetrics metrics = hint.getFontMetrics();
				hint.drawString(placeholder, getInsets().left,
						(getHeight() + metrics.getAscent() - metrics.getDescent()) / 2);
				hint.dispose();
			}
		}
	}

	/** A combo box in the Node Lab style. */
	static <T> JComboBox<T> combo(T[] items)
	{
		final JComboBox<T> box = new JComboBox<T>(items)
		{
			private static final long serialVersionUID = 1L;

			public Color getForeground()
			{
				return NebulaDesign.TEXT_PRIMARY;
			}

			public Color getBackground()
			{
				return NebulaDesign.INSET;
			}
		};
		box.setFont(NebulaDesign.fontBody(12.5f));
		box.setUI(new ComboUI());
		box.setOpaque(false);
		box.setBorder(new EmptyBorder(3, 8, 3, 4));
		box.setRenderer(new DefaultListCellRenderer()
		{
			private static final long serialVersionUID = 1L;

			public Component getListCellRendererComponent(JList<?> list, Object value, int index,
					boolean selected, boolean focus)
			{
				super.getListCellRendererComponent(list, value, index, selected, focus);
				setFont(NebulaDesign.fontBody(12.5f));
				setBorder(new EmptyBorder(6, 10, 6, 10));
				setForeground(NebulaDesign.currentTextPrimary());
				setBackground(selected ? NebulaDesign.currentSelection()
						: (index < 0 ? new Color(0, 0, 0, 0) : NebulaDesign.currentSurface()));
				setOpaque(index >= 0);
				return this;
			}
		});
		return box;
	}

	private static final class ComboUI extends BasicComboBoxUI
	{
		protected JButton createArrowButton()
		{
			JButton arrow = new JButton()
			{
				private static final long serialVersionUID = 1L;

				protected void paintComponent(Graphics graphics)
				{
					Graphics2D g = (Graphics2D)graphics.create();
					NebulaDesign.prepare(g);
					g.setColor(NebulaDesign.TEXT_SECONDARY);
					g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
					int cx = getWidth() / 2;
					int cy = getHeight() / 2;
					g.draw(new Line2D.Float(cx - 4, cy - 2, cx, cy + 2));
					g.draw(new Line2D.Float(cx, cy + 2, cx + 4, cy - 2));
					g.dispose();
				}
			};
			arrow.setBorder(BorderFactory.createEmptyBorder());
			arrow.setContentAreaFilled(false);
			arrow.setFocusable(false);
			arrow.setPreferredSize(new Dimension(24, 24));
			return arrow;
		}

		public void paint(Graphics graphics, JComponent component)
		{
			Graphics2D g = (Graphics2D)graphics.create();
			NebulaDesign.prepare(g);
			g.setColor(NebulaDesign.INSET);
			g.fill(new RoundRectangle2D.Float(0, 0, component.getWidth(), component.getHeight(), 10, 10));
			g.setColor(comboBox.isPopupVisible() || comboBox.hasFocus() ? NebulaDesign.ACCENT
					: NebulaDesign.BORDER_SUBTLE);
			g.draw(new RoundRectangle2D.Float(0.5f, 0.5f, component.getWidth() - 1f, component.getHeight() - 1f,
					10, 10));
			g.dispose();
			super.paint(graphics, component);
		}

		public void paintCurrentValueBackground(Graphics graphics, Rectangle bounds, boolean focus)
		{
		}
	}

	/** A labelled form row: caption above the control. */
	static JPanel labeled(String caption, Component control)
	{
		JPanel panel = new JPanel(new BorderLayout(0, 6))
		{
			private static final long serialVersionUID = 1L;

			/** A form row keeps its natural height in a taller column. */
			public Dimension getMaximumSize()
			{
				return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
			}
		};
		panel.setOpaque(false);
		panel.add(new Text(caption, NebulaDesign.fontMedium(12f), NebulaDesign.TEXT_SECONDARY), BorderLayout.NORTH);
		panel.add(control, BorderLayout.CENTER);
		return panel;
	}

	// ------------------------------------------------------------ choice tiles

	/**
	 * A selectable tile (one of a group): an icon badge, a title, a short
	 * explanation and an optional footnote, with a radio mark; the selected
	 * tile gets an accent ring and tint.
	 */
	static final class ChoiceTile extends JPanel
	{
		private static final long serialVersionUID = 1L;
		private final Tone tone;
		private boolean selected;
		private boolean hover;
		private final List<ActionListener> listeners = new ArrayList<ActionListener>();
		private final Prose footnoteText;

		ChoiceTile(final Glyph glyph, final Tone tone, String title, String text, String footnote, boolean compact)
		{
			super(new BorderLayout(12, 0));
			this.tone = tone == null ? Tone.ACCENT : tone;
			setOpaque(false);
			setBorder(new EmptyBorder(compact ? 12 : 16, compact ? 14 : 16, compact ? 13 : 16, 40));
			setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			final int size = compact ? 30 : 38;
			if (glyph != null)
			{
				JComponent badge = new JComponent()
				{
					private static final long serialVersionUID = 1L;

					public Dimension getPreferredSize()
					{
						return new Dimension(size, size);
					}

					protected void paintComponent(Graphics graphics)
					{
						Graphics2D g = (Graphics2D)graphics.create();
						prepare(g);
						Tone shown = ChoiceTile.this.isEnabled() ? ChoiceTile.this.tone : Tone.NEUTRAL;
						g.setColor(softTone(shown));
						g.fill(new RoundRectangle2D.Float(0, 0, size, size, size / 3f, size / 3f));
						g.setColor(tone(shown));
						int icon = Math.round(size * 0.5f);
						g.translate((size - icon) / 2, (size - icon) / 2);
						paintGlyph(g, glyph, icon);
						g.dispose();
					}
				};
				JPanel holder = new JPanel(new BorderLayout());
				holder.setOpaque(false);
				holder.add(badge, BorderLayout.NORTH);
				add(holder, BorderLayout.WEST);
			}
			footnoteText = footnote == null ? null : note(footnote);
			if (footnoteText != null)
			{
				footnoteText.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			}
			JTextArea explanation = paragraph(text == null ? "" : text);
			explanation.setFont(NebulaDesign.fontBody(compact ? 12f : 12.5f));
			explanation.setFocusable(false);
			explanation.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			Text heading = new Text(title, NebulaDesign.fontSemiBold(compact ? 13f : 14f), NebulaDesign.TEXT_PRIMARY);
			add(stack(4, heading, explanation, footnoteText), BorderLayout.CENTER);
			MouseAdapter mouse = new MouseAdapter()
			{
				public void mouseEntered(MouseEvent event)
				{
					hover = true;
					repaint();
				}

				public void mouseExited(MouseEvent event)
				{
					hover = false;
					repaint();
				}

				public void mouseReleased(MouseEvent event)
				{
					if (isEnabled() && contains(javax.swing.SwingUtilities.convertPoint(
							(Component)event.getSource(), event.getPoint(), ChoiceTile.this)))
					{
						fire();
					}
				}
			};
			addMouseListener(mouse);
			explanation.addMouseListener(mouse);
			if (footnoteText != null)
			{
				footnoteText.addMouseListener(mouse);
			}
		}

		private void fire()
		{
			ActionEvent event = new ActionEvent(this, ActionEvent.ACTION_PERFORMED, "choose");
			for (ActionListener listener : new ArrayList<ActionListener>(listeners))
			{
				listener.actionPerformed(event);
			}
		}

		void addActionListener(ActionListener listener)
		{
			listeners.add(listener);
		}

		void setSelected(boolean value)
		{
			if (selected != value)
			{
				selected = value;
				repaint();
			}
		}

		boolean isSelected()
		{
			return selected;
		}

		void setFootnote(String text)
		{
			if (footnoteText != null)
			{
				footnoteText.setText(text == null ? " " : text);
			}
		}

		public void setEnabled(boolean enabled)
		{
			super.setEnabled(enabled);
			setCursor(Cursor.getPredefinedCursor(enabled ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
			repaint();
		}

		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D)graphics.create();
			prepare(g);
			int w = getWidth();
			int h = getHeight() - 3;
			Color base = tone(tone);
			Color fill = selected ? StudioNightTheme.blend(NebulaDesign.SURFACE, base, night() ? 0.16f : 0.07f)
					: NebulaDesign.SURFACE;
			paintSurface(g, 0, 0, w, h, NebulaDesign.RADIUS_CARD, fill, !selected);
			if (selected || (hover && isEnabled()))
			{
				g.setColor(selected ? base : alpha(base, 110));
				g.setStroke(new BasicStroke(selected ? 2f : 1.2f));
				g.draw(new RoundRectangle2D.Float(1f, 1f, w - 2f, h - 2f, NebulaDesign.RADIUS_CARD,
						NebulaDesign.RADIUS_CARD));
			}
			float cx = w - 22f;
			float cy = 22f;
			g.setStroke(new BasicStroke(1.6f));
			g.setColor(selected ? base : NebulaDesign.BORDER_STRONG);
			g.draw(new Ellipse2D.Float(cx - 8f, cy - 8f, 16f, 16f));
			if (selected)
			{
				g.fill(new Ellipse2D.Float(cx - 4.5f, cy - 4.5f, 9f, 9f));
			}
			if (!isEnabled())
			{
				g.setColor(alpha(NebulaDesign.CANVAS_TOP, 150));
				g.fill(new RoundRectangle2D.Float(0, 0, w, h, NebulaDesign.RADIUS_CARD, NebulaDesign.RADIUS_CARD));
			}
			g.dispose();
		}
	}

	// ------------------------------------------------------------ code area

	/** A monospaced editor on an inset rounded background, in its own slim scroll pane. */
	static final class CodeArea extends JTextArea
	{
		private static final long serialVersionUID = 1L;

		private String placeholder = "";

		void setPlaceholder(String text)
		{
			placeholder = text == null ? "" : text;
			repaint();
		}

		protected void paintComponent(Graphics graphics)
		{
			super.paintComponent(graphics);
			if (getText().length() == 0 && placeholder.length() > 0)
			{
				Graphics2D g = (Graphics2D)graphics.create();
				NebulaDesign.prepare(g);
				g.setColor(NebulaDesign.TEXT_TERTIARY);
				g.setFont(getFont());
				FontMetrics metrics = g.getFontMetrics();
				int y = getInsets().top + metrics.getAscent();
				for (String line : placeholder.split("\n"))
				{
					g.drawString(line, getInsets().left, y);
					y += metrics.getHeight();
				}
				g.dispose();
			}
		}

		CodeArea(int rows)
		{
			super(rows, 40);
			setOpaque(false);
			setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
			setBorder(new EmptyBorder(10, 12, 10, 12));
			setLineWrap(false);
			setTabSize(4);
			setCaretColor(NebulaDesign.ACCENT);
		}

		public Color getForeground()
		{
			return NebulaDesign.TEXT_PRIMARY;
		}

		public Color getSelectionColor()
		{
			return NebulaDesign.SELECTION;
		}

		public Color getCaretColor()
		{
			return NebulaDesign.ACCENT;
		}

		JScrollPane inScrollPane()
		{
			JScrollPane pane = new JScrollPane(this)
			{
				private static final long serialVersionUID = 1L;

				protected void paintComponent(Graphics graphics)
				{
					Graphics2D g = (Graphics2D)graphics.create();
					NebulaDesign.prepare(g);
					g.setColor(NebulaDesign.INSET);
					g.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 12, 12));
					g.setColor(CodeArea.this.isFocusOwner() ? NebulaDesign.ACCENT : NebulaDesign.BORDER_SUBTLE);
					g.setStroke(new BasicStroke(CodeArea.this.isFocusOwner() ? 1.6f : 1f));
					g.draw(new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1f, getHeight() - 1f, 12, 12));
					g.dispose();
				}
			};
			pane.setOpaque(false);
			pane.getViewport().setOpaque(false);
			pane.setBorder(new EmptyBorder(1, 1, 1, 1));
			pane.setViewportBorder(null);
			StudioNightTheme.applyTabbedPaneTheme(pane);
			addFocusListener(new java.awt.event.FocusAdapter()
			{
				public void focusGained(java.awt.event.FocusEvent event)
				{
					pane.repaint();
				}

				public void focusLost(java.awt.event.FocusEvent event)
				{
					pane.repaint();
				}
			});
			return pane;
		}
	}

	// ------------------------------------------------------------ table

	/**
	 * A painted, read-only table: a header and zebra rows; columns share the
	 * width by weight; numbers align right; a cell may carry its own colour;
	 * truncated cells show their full text as a tooltip.
	 */
	static final class Table extends JComponent
	{
		private static final long serialVersionUID = 1L;
		private static final int HEADER = 30;
		private static final int ROW = 28;

		static final class Column
		{
			final String title;
			final float weight;
			final boolean right;

			Column(String title, float weight, boolean right)
			{
				this.title = title;
				this.weight = weight;
				this.right = right;
			}
		}

		static Column text(String title, float weight)
		{
			return new Column(title, weight, false);
		}

		static Column number(String title, float weight)
		{
			return new Column(title, weight, true);
		}

		private final Column[] columns;
		private List<String[]> rows = new ArrayList<String[]>();
		private List<Color[]> colors = new ArrayList<Color[]>();
		private int highlighted = -1;
		private String emptyText = "Nothing to show";

		Table(Column... columns)
		{
			this.columns = columns;
			setOpaque(false);
			setToolTipText("");
		}

		void setRows(List<String[]> newRows, List<Color[]> newColors)
		{
			rows = newRows == null ? new ArrayList<String[]>() : newRows;
			colors = newColors == null ? new ArrayList<Color[]>() : newColors;
			revalidate();
			repaint();
		}

		void setHighlightedRow(int row)
		{
			highlighted = row;
			repaint();
		}

		void setEmptyText(String text)
		{
			emptyText = text == null ? "" : text;
		}

		public Dimension getPreferredSize()
		{
			int width = 0;
			for (Column column : columns)
			{
				width += Math.max(60, (int)(column.weight * 70));
			}
			return new Dimension(width, HEADER + Math.max(1, rows.size()) * ROW + 4);
		}

		public Dimension getMaximumSize()
		{
			return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
		}

		private float[] edges()
		{
			float total = 0f;
			for (Column column : columns)
			{
				total += column.weight;
			}
			float[] edges = new float[columns.length + 1];
			float x = 0f;
			for (int index = 0; index < columns.length; index++)
			{
				edges[index] = x;
				x += getWidth() * columns[index].weight / total;
			}
			edges[columns.length] = getWidth();
			return edges;
		}

		public String getToolTipText(MouseEvent event)
		{
			int row = (event.getY() - HEADER) / ROW;
			if (event.getY() < HEADER || row < 0 || row >= rows.size())
			{
				return null;
			}
			float[] edges = edges();
			for (int column = 0; column < columns.length; column++)
			{
				if (event.getX() >= edges[column] && event.getX() < edges[column + 1])
				{
					String[] cells = rows.get(row);
					String text = column < cells.length ? cells[column] : "";
					FontMetrics metrics = getFontMetrics(NebulaDesign.fontBody(12f));
					return metrics.stringWidth(text) > edges[column + 1] - edges[column] - 16 ? text : null;
				}
			}
			return null;
		}

		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D)graphics.create();
			prepare(g);
			int w = getWidth();
			float[] edges = edges();
			g.setFont(NebulaDesign.fontMedium(11f));
			FontMetrics header = g.getFontMetrics();
			g.setColor(NebulaDesign.TEXT_TERTIARY);
			for (int column = 0; column < columns.length; column++)
			{
				String title = columns[column].title;
				float left = edges[column] + 8f;
				float right = edges[column + 1] - 8f;
				String shown = fit(title, header, right - left);
				float x = columns[column].right ? right - header.stringWidth(shown) : left;
				g.drawString(shown, x, HEADER - 11);
			}
			g.setColor(NebulaDesign.BORDER_SUBTLE);
			g.fill(new java.awt.Rectangle(0, HEADER - 1, w, 1));
			if (rows.isEmpty())
			{
				g.setFont(NebulaDesign.fontBody(12f));
				g.setColor(NebulaDesign.TEXT_TERTIARY);
				g.drawString(emptyText, 8, HEADER + 19);
				g.dispose();
				return;
			}
			g.setFont(NebulaDesign.fontBody(12f));
			FontMetrics metrics = g.getFontMetrics();
			for (int row = 0; row < rows.size(); row++)
			{
				int y = HEADER + row * ROW;
				if (row == highlighted)
				{
					g.setColor(NebulaDesign.ACCENT_SOFT);
					g.fill(new RoundRectangle2D.Float(0, y + 1, w, ROW - 2, 8, 8));
				}
				else if (row % 2 == 1)
				{
					g.setColor(alpha(NebulaDesign.INSET, 150));
					g.fill(new RoundRectangle2D.Float(0, y + 1, w, ROW - 2, 8, 8));
				}
				String[] cells = rows.get(row);
				Color[] tones = row < colors.size() ? colors.get(row) : null;
				for (int column = 0; column < columns.length; column++)
				{
					String text = column < cells.length && cells[column] != null ? cells[column] : "";
					float left = edges[column] + 8f;
					float right = edges[column + 1] - 8f;
					String shown = fit(text, metrics, right - left);
					Color tone = tones != null && column < tones.length ? tones[column] : null;
					g.setColor(tone != null ? tone : column == 0 ? NebulaDesign.TEXT_PRIMARY : NebulaDesign.TEXT_SECONDARY);
					g.setFont(column == 0 || row == highlighted ? NebulaDesign.fontMedium(12f) : NebulaDesign.fontBody(12f));
					float x = columns[column].right ? right - g.getFontMetrics().stringWidth(shown) : left;
					g.drawString(shown, x, y + (ROW + metrics.getAscent()) / 2 - 2);
				}
			}
			g.dispose();
		}

		private static String fit(String text, FontMetrics metrics, float width)
		{
			if (metrics.stringWidth(text) <= width)
			{
				return text;
			}
			String ellipsis = "…";
			int end = text.length();
			while (end > 0 && metrics.stringWidth(text.substring(0, end) + ellipsis) > width)
			{
				end--;
			}
			return end <= 0 ? "" : text.substring(0, end) + ellipsis;
		}
	}

	// ------------------------------------------------------------ help

	/** A small "?" that explains a concept in plain language in a popover. */
	static JComponent help(final String title, final String explanation)
	{
		final Button button = new Button("", ButtonKind.GHOST, Glyph.HELP, true);
		button.setToolTipText(title);
		button.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent event)
			{
				JPopupMenu popup = new JPopupMenu();
				popup.setBorder(BorderFactory.createEmptyBorder());
				popup.setOpaque(false);
				Card card = new Card(title, null);
				JTextArea text = paragraph(explanation);
				text.setColumns(34);
				text.setSize(new Dimension(320, Short.MAX_VALUE));
				card.content(text);
				card.setPreferredSize(new Dimension(360, card.getPreferredSize().height));
				popup.add(card);
				popup.show(button, 0, button.getHeight() + 4);
			}
		});
		return button;
	}

	// ------------------------------------------------------------ empty state

	/** A friendly empty state: an icon in a soft circle, a title, a hint and an action. */
	static JPanel emptyState(Glyph glyph, String titleText, String hint, JComponent action)
	{
		final Glyph shown = glyph;
		JComponent badge = new JComponent()
		{
			private static final long serialVersionUID = 1L;

			public Dimension getPreferredSize()
			{
				return new Dimension(56, 56);
			}

			protected void paintComponent(Graphics graphics)
			{
				Graphics2D g = (Graphics2D)graphics.create();
				NebulaDesign.prepare(g);
				g.setColor(NebulaDesign.ACCENT_SOFT);
				g.fill(new Ellipse2D.Float(0, 0, 56, 56));
				g.setColor(NebulaDesign.ACCENT);
				g.translate(16, 16);
				paintGlyph(g, shown, 24);
				g.dispose();
			}
		};
		JPanel center = new JPanel();
		center.setOpaque(false);
		center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
		badge.setAlignmentX(Component.CENTER_ALIGNMENT);
		Text heading = heading(titleText);
		heading.setAlignmentX(Component.CENTER_ALIGNMENT);
		Text text = body(hint);
		text.setAlignmentX(Component.CENTER_ALIGNMENT);
		center.add(badge);
		center.add(Box.createVerticalStrut(12));
		center.add(heading);
		center.add(Box.createVerticalStrut(4));
		center.add(text);
		if (action != null)
		{
			action.setAlignmentX(Component.CENTER_ALIGNMENT);
			center.add(Box.createVerticalStrut(14));
			center.add(action);
		}
		JPanel wrapper = new JPanel(new java.awt.GridBagLayout());
		wrapper.setOpaque(false);
		wrapper.setBorder(new EmptyBorder(26, 16, 26, 16));
		wrapper.add(center);
		return wrapper;
	}

	// ------------------------------------------------------------ formatting

	/** Milliseconds as ms, s or min with sensible precision. */
	static String duration(double milliseconds)
	{
		if (Double.isNaN(milliseconds) || Double.isInfinite(milliseconds))
		{
			return "—";
		}
		double magnitude = Math.abs(milliseconds);
		if (magnitude < 1.0)
		{
			return String.format(Locale.US, "%.3f ms", milliseconds);
		}
		if (magnitude < 1000.0)
		{
			return String.format(Locale.US, "%.1f ms", milliseconds);
		}
		if (magnitude < 60000.0)
		{
			return String.format(Locale.US, "%.2f s", milliseconds / 1000.0);
		}
		return String.format(Locale.US, "%.1f min", milliseconds / 60000.0);
	}

	static String bytes(double value)
	{
		if (Double.isNaN(value))
		{
			return "—";
		}
		if (value < 1024.0)
		{
			return String.format(Locale.US, "%.0f B", value);
		}
		if (value < 1024.0 * 1024.0)
		{
			return String.format(Locale.US, "%.1f KB", value / 1024.0);
		}
		if (value < 1024.0 * 1024.0 * 1024.0)
		{
			return String.format(Locale.US, "%.1f MB", value / 1024.0 / 1024.0);
		}
		return String.format(Locale.US, "%.2f GB", value / 1024.0 / 1024.0 / 1024.0);
	}

	static String percent(double fraction)
	{
		return Double.isNaN(fraction) ? "—" : String.format(Locale.US, "%.0f%%", fraction * 100.0);
	}

	static String number(double value)
	{
		if (Double.isNaN(value))
		{
			return "—";
		}
		if (Math.abs(value - Math.rint(value)) < 1e-9 && Math.abs(value) < 1e9)
		{
			return String.format(Locale.US, "%d", (long)Math.rint(value));
		}
		return String.format(Locale.US, "%.2f", value);
	}

	/** Applies anti-aliasing and text hints. */
	static void prepare(Graphics2D g)
	{
		NebulaDesign.prepare(g);
		g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
	}
}
