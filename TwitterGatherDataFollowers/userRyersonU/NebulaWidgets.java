package TwitterGatherDataFollowers.userRyersonU;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JTabbedPane;
import javax.swing.plaf.basic.BasicScrollBarUI;
import javax.swing.plaf.basic.BasicTabbedPaneUI;

/**
 * Shared Nebula widget painters and UI delegates: the sidebar-rail tab UI,
 * the underline tab UI for inner tab strips, and the slim pill scrollbar.
 * All delegates resolve colors at paint time so one instance serves both
 * day and night modes.
 */
final class NebulaWidgets
{
	static final String TAB_STYLE_PROPERTY = "nebula.tabStyle";
	static final String TAB_STYLE_RAIL = "rail";
	static final String TAB_STYLE_UNDERLINE = "underline";
	static final String TAB_STYLE_NONE = "none";

	private NebulaWidgets()
	{
	}

	static boolean night()
	{
		return StudioNightTheme.isNightMode();
	}

	// ------------------------------------------------------------------
	// Sidebar rail tab UI (main navigation)
	// ------------------------------------------------------------------
	static final class RailTabbedPaneUI extends BasicTabbedPaneUI
	{
		private static final int RAIL_WIDTH = 226;
		private static final int ITEM_HEIGHT = 34;
		private static final int SECTION_EXTRA = 24;
		private final Map<Integer, String> sections;
		private int hoverIndex = -1;

		RailTabbedPaneUI(Map<Integer, String> sections)
		{
			this.sections = sections;
		}

		protected void installListeners()
		{
			super.installListeners();
			tabPane.addMouseMotionListener(new MouseAdapter()
			{
				public void mouseMoved(MouseEvent event)
				{
					int index = tabForCoordinate(tabPane, event.getX(), event.getY());
					if (index != hoverIndex)
					{
						hoverIndex = index;
						tabPane.setCursor(Cursor.getPredefinedCursor(
								index >= 0 ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
						tabPane.repaint();
					}
				}
			});
			tabPane.addMouseListener(new MouseAdapter()
			{
				public void mouseExited(MouseEvent event)
				{
					hoverIndex = -1;
					tabPane.setCursor(Cursor.getDefaultCursor());
					tabPane.repaint();
				}
			});
		}

		protected Insets getTabAreaInsets(int tabPlacement)
		{
			return new Insets(10, 12, 12, 10);
		}

		protected Insets getContentBorderInsets(int tabPlacement)
		{
			return new Insets(0, 1, 0, 0);
		}

		protected Insets getTabInsets(int tabPlacement, int tabIndex)
		{
			return new Insets(0, 0, 0, 0);
		}

		protected int calculateTabWidth(int tabPlacement, int tabIndex, FontMetrics metrics)
		{
			return RAIL_WIDTH - 22;
		}

		protected int calculateTabHeight(int tabPlacement, int tabIndex, int fontHeight)
		{
			int height = ITEM_HEIGHT + 4;
			if (sections != null && sections.containsKey(Integer.valueOf(tabIndex)))
			{
				height += SECTION_EXTRA;
			}
			return height;
		}

		protected int calculateMaxTabWidth(int tabPlacement)
		{
			return RAIL_WIDTH - 22;
		}

		protected void paintTabArea(Graphics graphics, int tabPlacement, int selectedIndex)
		{
			Graphics2D g2 = (Graphics2D) graphics.create();
			NebulaDesign.prepare(g2);
			int width = calculateTabAreaWidth(tabPlacement, runCount, maxTabWidth);
			g2.setColor(night() ? NebulaDesign.NIGHT_RAIL : NebulaDesign.DAY_RAIL);
			g2.fillRect(0, 0, width, tabPane.getHeight());
			g2.setColor(NebulaDesign.currentBorderSubtle());
			g2.fillRect(width - 1, 0, 1, tabPane.getHeight());
			g2.dispose();
			super.paintTabArea(graphics, tabPlacement, selectedIndex);
		}

		protected void paintTabBackground(
				Graphics graphics,
				int tabPlacement,
				int tabIndex,
				int x,
				int y,
				int w,
				int h,
				boolean isSelected)
		{
			Graphics2D g2 = (Graphics2D) graphics.create();
			NebulaDesign.prepare(g2);
			Rectangle pill = pillBounds(tabIndex, x, y, w, h);
			String section = sections == null ? null : sections.get(Integer.valueOf(tabIndex));
			if (section != null)
			{
				NebulaDesign.paintEyebrow(
						g2,
						section,
						pill.x + 12,
						pill.y - 9,
						night() ? NebulaDesign.NIGHT_TEXT_TERTIARY : NebulaDesign.DAY_TEXT_TERTIARY);
			}
			if (isSelected)
			{
				g2.setColor(night() ? NebulaDesign.NIGHT_ACCENT_SOFT : NebulaDesign.DAY_ACCENT_SOFT);
				g2.fillRoundRect(pill.x, pill.y, pill.width, pill.height, 10, 10);
				g2.setColor(NebulaDesign.currentAccent());
				g2.fillRoundRect(pill.x, pill.y + 8, 3, pill.height - 16, 3, 3);
			}
			else if (tabIndex == hoverIndex)
			{
				g2.setColor(night()
						? NebulaDesign.withAlpha(NebulaDesign.NIGHT_SURFACE_RAISED, 170)
						: NebulaDesign.withAlpha(new Color(226, 231, 240), 160));
				g2.fillRoundRect(pill.x, pill.y, pill.width, pill.height, 10, 10);
			}
			g2.dispose();
		}

		protected void paintTabBorder(
				Graphics graphics,
				int tabPlacement,
				int tabIndex,
				int x,
				int y,
				int w,
				int h,
				boolean isSelected)
		{
			// Borderless rail items.
		}

		protected void paintText(
				Graphics graphics,
				int tabPlacement,
				Font font,
				FontMetrics metrics,
				int tabIndex,
				String title,
				Rectangle textRect,
				boolean isSelected)
		{
			Graphics2D g2 = (Graphics2D) graphics.create();
			NebulaDesign.prepare(g2);
			Rectangle bounds = rects[tabIndex];
			Rectangle pill = pillBounds(tabIndex, bounds.x, bounds.y, bounds.width, bounds.height);
			g2.setFont(isSelected ? NebulaDesign.fontSemiBold(12.5f) : NebulaDesign.fontMedium(12.5f));
			Color color;
			if (isSelected)
			{
				color = NebulaDesign.currentTextPrimary();
			}
			else if (tabIndex == hoverIndex)
			{
				color = night() ? NebulaDesign.NIGHT_TEXT_PRIMARY : NebulaDesign.DAY_TEXT_PRIMARY;
			}
			else
			{
				color = NebulaDesign.currentTextSecondary();
			}
			g2.setColor(color);
			FontMetrics active = g2.getFontMetrics();
			int textX = pill.x + 33;
			int textY = pill.y + (pill.height - active.getHeight()) / 2 + active.getAscent();
			String label = title;
			int available = pill.x + pill.width - 10 - textX;
			if (active.stringWidth(label) > available)
			{
				while (label.length() > 3 && active.stringWidth(label + "…") > available)
				{
					label = label.substring(0, label.length() - 1);
				}
				label = label + "…";
			}
			g2.drawString(label, textX, textY);
			g2.dispose();
		}

		protected void paintIcon(
				Graphics graphics,
				int tabPlacement,
				int tabIndex,
				Icon icon,
				Rectangle iconRect,
				boolean isSelected)
		{
			if (icon == null)
			{
				return;
			}
			Rectangle bounds = rects[tabIndex];
			Rectangle pill = pillBounds(tabIndex, bounds.x, bounds.y, bounds.width, bounds.height);
			int iconY = pill.y + (pill.height - icon.getIconHeight()) / 2;
			icon.paintIcon(tabPane, graphics, pill.x + 11, iconY);
		}

		protected void paintFocusIndicator(
				Graphics graphics,
				int tabPlacement,
				Rectangle[] rectangles,
				int tabIndex,
				Rectangle iconRect,
				Rectangle textRect,
				boolean isSelected)
		{
			// The selection pill carries focus emphasis.
		}

		protected void paintContentBorder(Graphics graphics, int tabPlacement, int selectedIndex)
		{
			// The content paints its own canvas; no bevel borders.
		}

		private Rectangle pillBounds(int tabIndex, int x, int y, int w, int h)
		{
			int top = y + 2;
			if (sections != null && sections.containsKey(Integer.valueOf(tabIndex)))
			{
				top = y + SECTION_EXTRA;
			}
			return new Rectangle(x, top, w, ITEM_HEIGHT);
		}
	}

	// ------------------------------------------------------------------
	// Underline tab UI (inner tab strips)
	// ------------------------------------------------------------------
	static final class UnderlineTabbedPaneUI extends BasicTabbedPaneUI
	{
		private int hoverIndex = -1;

		protected void installListeners()
		{
			super.installListeners();
			tabPane.addMouseMotionListener(new MouseAdapter()
			{
				public void mouseMoved(MouseEvent event)
				{
					int index = tabForCoordinate(tabPane, event.getX(), event.getY());
					if (index != hoverIndex)
					{
						hoverIndex = index;
						tabPane.setCursor(Cursor.getPredefinedCursor(
								index >= 0 ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
						tabPane.repaint();
					}
				}
			});
			tabPane.addMouseListener(new MouseAdapter()
			{
				public void mouseExited(MouseEvent event)
				{
					hoverIndex = -1;
					tabPane.setCursor(Cursor.getDefaultCursor());
					tabPane.repaint();
				}
			});
		}

		protected Insets getTabAreaInsets(int tabPlacement)
		{
			return new Insets(2, 6, 0, 6);
		}

		protected Insets getContentBorderInsets(int tabPlacement)
		{
			return new Insets(8, 0, 0, 0);
		}

		protected Insets getTabInsets(int tabPlacement, int tabIndex)
		{
			return new Insets(8, 14, 8, 14);
		}

		protected void paintTabArea(Graphics graphics, int tabPlacement, int selectedIndex)
		{
			super.paintTabArea(graphics, tabPlacement, selectedIndex);
			Graphics2D g2 = (Graphics2D) graphics.create();
			NebulaDesign.prepare(g2);
			int areaHeight = calculateTabAreaHeight(tabPlacement, runCount, maxTabHeight);
			g2.setColor(NebulaDesign.currentBorderSubtle());
			g2.fillRect(0, areaHeight - 1, tabPane.getWidth(), 1);
			if (selectedIndex >= 0 && selectedIndex < rects.length)
			{
				Rectangle bounds = rects[selectedIndex];
				g2.setColor(NebulaDesign.currentAccent());
				g2.fillRoundRect(bounds.x + 8, areaHeight - 3, bounds.width - 16, 3, 3, 3);
			}
			g2.dispose();
		}

		protected void paintTabBackground(
				Graphics graphics,
				int tabPlacement,
				int tabIndex,
				int x,
				int y,
				int w,
				int h,
				boolean isSelected)
		{
			if (tabIndex == hoverIndex && !isSelected)
			{
				Graphics2D g2 = (Graphics2D) graphics.create();
				NebulaDesign.prepare(g2);
				g2.setColor(night()
						? NebulaDesign.withAlpha(NebulaDesign.NIGHT_SURFACE_RAISED, 160)
						: NebulaDesign.withAlpha(new Color(228, 232, 241), 150));
				g2.fillRoundRect(x + 4, y + 3, w - 8, h - 8, 8, 8);
				g2.dispose();
			}
		}

		protected void paintTabBorder(
				Graphics graphics,
				int tabPlacement,
				int tabIndex,
				int x,
				int y,
				int w,
				int h,
				boolean isSelected)
		{
			// Borderless.
		}

		protected void paintText(
				Graphics graphics,
				int tabPlacement,
				Font font,
				FontMetrics metrics,
				int tabIndex,
				String title,
				Rectangle textRect,
				boolean isSelected)
		{
			Graphics2D g2 = (Graphics2D) graphics.create();
			NebulaDesign.prepare(g2);
			g2.setFont(isSelected ? NebulaDesign.fontSemiBold(12f) : NebulaDesign.fontMedium(12f));
			Color color;
			if (isSelected || tabIndex == hoverIndex)
			{
				color = NebulaDesign.currentTextPrimary();
			}
			else
			{
				color = NebulaDesign.currentTextSecondary();
			}
			g2.setColor(color);
			FontMetrics active = g2.getFontMetrics();
			g2.drawString(title, textRect.x, textRect.y + active.getAscent() - 2);
			g2.dispose();
		}

		protected void paintFocusIndicator(
				Graphics graphics,
				int tabPlacement,
				Rectangle[] rectangles,
				int tabIndex,
				Rectangle iconRect,
				Rectangle textRect,
				boolean isSelected)
		{
			// Underline carries the emphasis.
		}

		protected void paintContentBorder(Graphics graphics, int tabPlacement, int selectedIndex)
		{
			// No bevel borders.
		}
	}

	// ------------------------------------------------------------------
	// Slim pill scrollbar
	// ------------------------------------------------------------------
	static final class PillScrollBarUI extends BasicScrollBarUI
	{
		protected void configureScrollBarColors()
		{
			trackColor = new Color(0, 0, 0, 0);
		}

		public Dimension getPreferredSize(JComponent component)
		{
			if (scrollbar.getOrientation() == javax.swing.JScrollBar.VERTICAL)
			{
				return new Dimension(10, 48);
			}
			return new Dimension(48, 10);
		}

		protected JButton createDecreaseButton(int orientation)
		{
			return invisibleButton();
		}

		protected JButton createIncreaseButton(int orientation)
		{
			return invisibleButton();
		}

		protected void paintTrack(Graphics graphics, JComponent component, Rectangle trackBounds)
		{
			// Transparent track: the surface below shows through.
		}

		protected void paintThumb(Graphics graphics, JComponent component, Rectangle thumbBounds)
		{
			if (thumbBounds.isEmpty() || !component.isEnabled())
			{
				return;
			}
			Graphics2D g2 = (Graphics2D) graphics.create();
			NebulaDesign.prepare(g2);
			boolean active = isDragging || isThumbRollover();
			Color base = NebulaDesign.currentBorderStrong();
			Color accent = NebulaDesign.currentAccent();
			g2.setColor(active
					? NebulaDesign.withAlpha(accent, 150)
					: NebulaDesign.withAlpha(base, 165));
			int pad = 2;
			g2.fillRoundRect(
					thumbBounds.x + pad,
					thumbBounds.y + pad,
					Math.max(4, thumbBounds.width - pad * 2),
					Math.max(4, thumbBounds.height - pad * 2),
					8,
					8);
			g2.dispose();
		}

		private static JButton invisibleButton()
		{
			JButton button = new JButton();
			Dimension zero = new Dimension(0, 0);
			button.setPreferredSize(zero);
			button.setMinimumSize(zero);
			button.setMaximumSize(zero);
			button.setBorder(BorderFactory.createEmptyBorder());
			button.setOpaque(false);
			button.setContentAreaFilled(false);
			button.setFocusable(false);
			return button;
		}
	}

	// ------------------------------------------------------------------
	// Shared paint helpers
	// ------------------------------------------------------------------

	/** Paints a rounded card with a soft drop shadow (day) or lifted surface (night). */
	static void paintCard(Graphics2D g2, int x, int y, int width, int height, int radius)
	{
		NebulaDesign.prepare(g2);
		if (!night())
		{
			g2.setColor(new Color(23, 35, 60, 14));
			g2.fillRoundRect(x + 1, y + 3, width - 2, height - 2, radius + 2, radius + 2);
			g2.setColor(new Color(23, 35, 60, 8));
			g2.fillRoundRect(x + 2, y + 5, width - 4, height - 3, radius + 3, radius + 3);
		}
		g2.setColor(NebulaDesign.currentSurface());
		g2.fillRoundRect(x, y, width - 1, height - 3, radius, radius);
		g2.setColor(NebulaDesign.currentBorderSubtle());
		g2.drawRoundRect(x, y, width - 1, height - 3, radius, radius);
	}

	/** Paints a status pill (soft tint + strong hue label) and returns its width. */
	static int paintStatusPill(
			Graphics2D g2,
			String text,
			int x,
			int y,
			Color hue,
			Color softHue)
	{
		NebulaDesign.prepare(g2);
		g2.setFont(NebulaDesign.fontSmall());
		FontMetrics metrics = g2.getFontMetrics();
		int width = metrics.stringWidth(text) + 20;
		int height = metrics.getHeight() + 6;
		g2.setColor(softHue);
		g2.fillRoundRect(x, y, width, height, height, height);
		g2.setColor(hue);
		g2.drawString(text, x + 10, y + 3 + metrics.getAscent());
		return width;
	}

	/** Paints a small chevron used by combo boxes and disclosure affordances. */
	static void paintChevron(Graphics2D g2, int centerX, int centerY, int size, Color color)
	{
		NebulaDesign.prepare(g2);
		g2.setColor(color);
		g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		int half = size / 2;
		g2.drawLine(centerX - half, centerY - half / 2, centerX, centerY + half / 2);
		g2.drawLine(centerX, centerY + half / 2, centerX + half, centerY - half / 2);
	}

	/** Applies hand cursor + no-focus-paint conventions to any abstract button. */
	static void polishButton(javax.swing.AbstractButton button)
	{
		button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		button.setFocusPainted(false);
	}

	/** Convenience: marks a component's tab style for StudioNightTheme's chrome walk. */
	static void setTabStyle(JTabbedPane tabs, String style)
	{
		tabs.putClientProperty(TAB_STYLE_PROPERTY, style);
	}

	/** Section header label styling shared by cards ("eyebrow" text). */
	static void styleSectionLabel(javax.swing.JLabel label)
	{
		label.setFont(NebulaDesign.fontSection());
		label.setForeground(NebulaDesign.TEXT_TERTIARY);
		label.setText(label.getText() == null ? null : label.getText().toUpperCase());
	}

	/** Returns true when the supplied component sits inside night-transformed chrome. */
	static boolean isNightStyled(Component component)
	{
		return night();
	}

	// ------------------------------------------------------------------
	// Spinner: quiet chevron steppers instead of stock arrow buttons
	// ------------------------------------------------------------------
	static final class ChevronSpinnerUI extends javax.swing.plaf.basic.BasicSpinnerUI
	{
		protected Component createNextButton()
		{
			return new StepperArrow(true, spinner);
		}

		protected Component createPreviousButton()
		{
			return new StepperArrow(false, spinner);
		}
	}

	private static final class StepperArrow extends JButton
	{
		private final boolean up;

		StepperArrow(boolean up, final javax.swing.JSpinner spinner)
		{
			this.up = up;
			setOpaque(false);
			setContentAreaFilled(false);
			setBorderPainted(false);
			setFocusable(false);
			setPreferredSize(new Dimension(18, 11));
			setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			addActionListener(new java.awt.event.ActionListener()
			{
				public void actionPerformed(java.awt.event.ActionEvent event)
				{
					Object value = StepperArrow.this.up
							? spinner.getModel().getNextValue()
							: spinner.getModel().getPreviousValue();
					if (value != null)
					{
						spinner.setValue(value);
					}
				}
			});
		}

		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g2 = (Graphics2D) graphics.create();
			NebulaDesign.prepare(g2);
			Color tint = getModel().isRollover()
					? NebulaDesign.currentAccent()
					: NebulaDesign.currentTextSecondary();
			g2.setColor(tint);
			g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			int cx = getWidth() / 2;
			int cy = getHeight() / 2;
			int half = 3;
			if (up)
			{
				g2.drawLine(cx - half, cy + 2, cx, cy - 2);
				g2.drawLine(cx, cy - 2, cx + half, cy + 2);
			}
			else
			{
				g2.drawLine(cx - half, cy - 2, cx, cy + 2);
				g2.drawLine(cx, cy + 2, cx + half, cy - 2);
			}
			g2.dispose();
		}
	}

	// ------------------------------------------------------------------
	// Table header: flat surface with an eyebrow-style caption
	// ------------------------------------------------------------------
	static final class HeaderRenderer extends javax.swing.table.DefaultTableCellRenderer
	{
		public Component getTableCellRendererComponent(
				javax.swing.JTable table,
				Object value,
				boolean selected,
				boolean focused,
				int row,
				int column)
		{
			super.getTableCellRendererComponent(table, value, selected, focused, row, column);
			setFont(NebulaDesign.fontMedium(11f));
			setForeground(NebulaDesign.currentTextSecondary());
			setBackground(night()
					? NebulaDesign.NIGHT_SURFACE_RAISED
					: NebulaDesign.DAY_SURFACE_ALT);
			setOpaque(true);
			setHorizontalAlignment(LEFT);
			setBorder(BorderFactory.createCompoundBorder(
					BorderFactory.createMatteBorder(0, 0, 1, 0, NebulaDesign.currentBorderStrong()),
					BorderFactory.createEmptyBorder(7, 10, 7, 10)));
			setText(value == null ? "" : value.toString());
			return this;
		}
	}

	/** Applies the Nebula table look: flat header, horizontal rules, roomy rows. */
	static void styleTable(javax.swing.JTable table)
	{
		table.setFont(NebulaDesign.fontBody(12.5f));
		table.setRowHeight(Math.max(28, table.getRowHeight()));
		table.setShowVerticalLines(false);
		table.setShowHorizontalLines(true);
		table.setGridColor(NebulaDesign.currentBorderSubtle());
		table.setIntercellSpacing(new Dimension(0, 1));
		table.setBackground(NebulaDesign.currentSurface());
		table.setForeground(NebulaDesign.currentTextPrimary());
		table.setSelectionBackground(NebulaDesign.currentSelection());
		table.setSelectionForeground(NebulaDesign.currentTextPrimary());
		javax.swing.table.JTableHeader header = table.getTableHeader();
		if (header != null)
		{
			header.setDefaultRenderer(new HeaderRenderer());
			header.setReorderingAllowed(false);
			header.setBorder(BorderFactory.createEmptyBorder());
			header.setBackground(night()
					? NebulaDesign.NIGHT_SURFACE_RAISED
					: NebulaDesign.DAY_SURFACE_ALT);
			header.setPreferredSize(new Dimension(
					header.getPreferredSize().width,
					Math.max(30, header.getPreferredSize().height)));
		}
	}
}
