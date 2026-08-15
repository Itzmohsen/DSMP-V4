package TwitterGatherDataFollowers.userRyersonU;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.SwingConstants;
import javax.swing.plaf.basic.BasicTabbedPaneUI;
import javax.swing.text.View;

final class StudioNightTabbedPaneUI extends BasicTabbedPaneUI
{
	protected void installDefaults()
	{
		super.installDefaults();
		tabAreaInsets = new Insets(7, 9, 0, 9);
		tabInsets = new Insets(8, 13, 8, 13);
		selectedTabPadInsets = new Insets(2, 2, 2, 2);
		contentBorderInsets = new Insets(2, 2, 2, 2);
	}

	protected int calculateTabHeight(int tabPlacement, int tabIndex, int fontHeight)
	{
		return super.calculateTabHeight(tabPlacement, tabIndex, fontHeight) + 6;
	}

	protected int calculateTabWidth(int tabPlacement, int tabIndex, FontMetrics metrics)
	{
		return super.calculateTabWidth(tabPlacement, tabIndex, metrics) + 10;
	}

	protected void paintTabArea(Graphics graphics, int tabPlacement, int selectedIndex)
	{
		Graphics2D g2 = (Graphics2D) graphics.create();
		g2.setColor(StudioNightTheme.NIGHT_BACKGROUND);
		g2.fillRect(0, 0, tabPane.getWidth(), calculateTabAreaHeight(tabPlacement, runCount, maxTabHeight));
		g2.dispose();
		super.paintTabArea(graphics, tabPlacement, selectedIndex);
	}

	protected void paintTabBackground(
			Graphics graphics,
			int tabPlacement,
			int tabIndex,
			int x,
			int y,
			int width,
			int height,
			boolean isSelected)
	{
		Graphics2D g2 = (Graphics2D) graphics.create();
		g2.setRenderingHint(
				RenderingHints.KEY_ANTIALIASING,
				RenderingHints.VALUE_ANTIALIAS_ON);
		Color fill;
		if (isSelected)
		{
			fill = StudioNightTheme.blend(
					StudioNightTheme.NIGHT_PANEL_INK,
					StudioNightTheme.NIGHT_BLUE,
					0.16f);
		}
		else
		{
			fill = StudioNightTheme.NIGHT_SURFACE;
		}
		g2.setColor(fill);
		g2.fillRoundRect(x + 2, y + 4, width - 4, height - 7, 8, 8);
		if (isSelected)
		{
			g2.setColor(StudioNightTheme.withAlpha(StudioNightTheme.NIGHT_BLUE, 68));
			g2.fillRoundRect(x + 2, y + height - 8, width - 4, 4, 6, 6);
		}
		g2.dispose();
	}

	protected void paintTabBorder(
			Graphics graphics,
			int tabPlacement,
			int tabIndex,
			int x,
			int y,
			int width,
			int height,
			boolean isSelected)
	{
		Graphics2D g2 = (Graphics2D) graphics.create();
		g2.setRenderingHint(
				RenderingHints.KEY_ANTIALIASING,
				RenderingHints.VALUE_ANTIALIAS_ON);
		g2.setColor(isSelected
				? StudioNightTheme.withAlpha(StudioNightTheme.NIGHT_BLUE, 145)
				: StudioNightTheme.withAlpha(StudioNightTheme.NIGHT_BORDER, 120));
		g2.drawRoundRect(x + 2, y + 4, width - 5, height - 8, 8, 8);
		g2.dispose();
	}

	protected void paintContentBorder(Graphics graphics, int tabPlacement, int selectedIndex)
	{
		Graphics2D g2 = (Graphics2D) graphics.create();
		int x = 0;
		int y = 0;
		int width = tabPane.getWidth();
		int height = tabPane.getHeight();
		int tabAreaHeight = calculateTabAreaHeight(
				tabPlacement,
				runCount,
				maxTabHeight);
		if (tabPlacement == SwingConstants.TOP)
		{
			y = tabAreaHeight;
			height -= tabAreaHeight;
		}
		else if (tabPlacement == SwingConstants.BOTTOM)
		{
			height -= tabAreaHeight;
		}
		else
		{
			int tabAreaWidth = calculateTabAreaWidth(
					tabPlacement,
					runCount,
					maxTabWidth);
			if (tabPlacement == SwingConstants.LEFT)
			{
				x = tabAreaWidth;
			}
			width -= tabAreaWidth;
		}
		g2.setColor(StudioNightTheme.NIGHT_BACKGROUND);
		g2.fillRect(x, y, width, height);
		g2.setColor(StudioNightTheme.withAlpha(StudioNightTheme.NIGHT_BORDER, 130));
		g2.drawRect(x, y, width - 1, height - 1);
		g2.dispose();
	}

	protected void paintFocusIndicator(
			Graphics graphics,
			int tabPlacement,
			Rectangle[] rects,
			int tabIndex,
			Rectangle iconRect,
			Rectangle textRect,
			boolean isSelected)
	{
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
		g2.setFont(font.deriveFont(Font.BOLD));
		g2.setColor(isSelected
				? StudioNightTheme.NIGHT_TEXT
				: StudioNightTheme.NIGHT_MUTED);
		View view = getTextViewForTab(tabIndex);
		if (view != null)
		{
			view.paint(g2, textRect);
		}
		else
		{
			g2.drawString(title, textRect.x, textRect.y + metrics.getAscent());
		}
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
		icon.paintIcon(tabPane, graphics, iconRect.x, iconRect.y);
	}

	public void update(Graphics graphics, JComponent component)
	{
		graphics.setColor(StudioNightTheme.NIGHT_BACKGROUND);
		graphics.fillRect(0, 0, component.getWidth(), component.getHeight());
		paint(graphics, component);
	}
}
