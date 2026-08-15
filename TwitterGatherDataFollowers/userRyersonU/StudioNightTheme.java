package TwitterGatherDataFollowers.userRyersonU;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JViewport;
import javax.swing.border.Border;
import javax.swing.border.TitledBorder;
import javax.swing.plaf.ScrollBarUI;
import javax.swing.plaf.basic.BasicScrollBarUI;

final class StudioNightTheme
{
	static final Color NIGHT_BACKGROUND = new Color(13, 18, 26);
	static final Color NIGHT_BACKGROUND_BOTTOM = new Color(9, 13, 20);
	static final Color NIGHT_SURFACE = new Color(22, 31, 43);
	static final Color NIGHT_SURFACE_ALT = new Color(27, 38, 53);
	static final Color NIGHT_PANEL_INK = new Color(31, 44, 61);
	static final Color NIGHT_BORDER = new Color(63, 84, 106);
	static final Color NIGHT_TEXT = new Color(234, 242, 252);
	static final Color NIGHT_MUTED = new Color(157, 172, 190);
	static final Color NIGHT_BLUE = new Color(94, 166, 255);
	static final Color NIGHT_GREEN = new Color(64, 211, 146);
	static final Color NIGHT_AMBER = new Color(244, 176, 70);
	static final Color NIGHT_RED = new Color(245, 108, 102);
	static final Color NIGHT_PURPLE = new Color(169, 135, 255);
	static final Color NIGHT_TEAL = new Color(64, 213, 217);
	static final Color NIGHT_PINK = new Color(242, 113, 184);
	private static final String ORIGINAL_MARKER = "studioNightTheme.original";
	private static final String ORIGINAL_BACKGROUND = "studioNightTheme.background";
	private static final String ORIGINAL_FOREGROUND = "studioNightTheme.foreground";
	private static final String ORIGINAL_BORDER = "studioNightTheme.border";
	private static final String ORIGINAL_OPAQUE = "studioNightTheme.opaque";
	private static final String ORIGINAL_TITLE_COLOR = "studioNightTheme.titleColor";
	private static final String ORIGINAL_SCROLLBAR_UI = "studioNightTheme.scrollbarUi";
	private static final Object NULL_VALUE = new Object();
	private static volatile boolean nightMode;

	private StudioNightTheme()
	{
	}

	static boolean isNightMode()
	{
		return nightMode;
	}

	static void setNightMode(boolean enabled)
	{
		nightMode = enabled;
	}

	static JComponent wrapTab(Component component, boolean transformInNightMode)
	{
		return new ThemedTabPanel(component, transformInNightMode);
	}

	static Color adaptiveColor(Color day, Color night)
	{
		return new AdaptiveColor(day, night);
	}

	static Component unwrap(Component component)
	{
		if (component instanceof ThemedTabPanel)
		{
			return ((ThemedTabPanel) component).getView();
		}
		return component;
	}

	static void applyThemeTree(Component root)
	{
		if (root == null)
		{
			return;
		}
		if (nightMode)
		{
			applyNightComponent(root);
		}
		else
		{
			restoreComponent(root);
		}
		if (nightMode
				&& root instanceof ThemedTabPanel
				&& !((ThemedTabPanel) root).isTransformInNightMode())
		{
			return;
		}
		if (root instanceof Container)
		{
			Component[] children = ((Container) root).getComponents();
			for (int i = 0; i < children.length; i++)
			{
				applyThemeTree(children[i]);
			}
		}
	}

	static void applyTabbedPaneTheme(Component root)
	{
		if (root == null)
		{
			return;
		}
		if (root instanceof JTabbedPane)
		{
			JTabbedPane tabs = (JTabbedPane) root;
			if (nightMode)
			{
				if (!(tabs.getUI() instanceof StudioNightTabbedPaneUI))
				{
					tabs.setUI(new StudioNightTabbedPaneUI());
				}
				tabs.setForeground(NIGHT_TEXT);
				tabs.setBackground(NIGHT_BACKGROUND);
			}
			else if (tabs.getUI() instanceof StudioNightTabbedPaneUI)
			{
				tabs.updateUI();
			}
		}
		if (root instanceof Container)
		{
			Component[] children = ((Container) root).getComponents();
			for (int i = 0; i < children.length; i++)
			{
				applyTabbedPaneTheme(children[i]);
			}
		}
	}

	static Color withAlpha(Color color, int alpha)
	{
		return new Color(
				color.getRed(),
				color.getGreen(),
				color.getBlue(),
				Math.max(0, Math.min(255, alpha)));
	}

	static Color blend(Color from, Color to, float amount)
	{
		float clamped = Math.max(0.0f, Math.min(1.0f, amount));
		int red = Math.round(from.getRed() + (to.getRed() - from.getRed()) * clamped);
		int green = Math.round(from.getGreen() + (to.getGreen() - from.getGreen()) * clamped);
		int blue = Math.round(from.getBlue() + (to.getBlue() - from.getBlue()) * clamped);
		int alpha = Math.round(from.getAlpha() + (to.getAlpha() - from.getAlpha()) * clamped);
		return new Color(red, green, blue, alpha);
	}

	private static void applyNightComponent(Component component)
	{
		if (component instanceof JComponent)
		{
			remember((JComponent) component);
		}
		if (component instanceof JTextArea)
		{
			JTextArea area = (JTextArea) component;
			area.setOpaque(true);
			area.setBackground(new Color(10, 15, 23));
			area.setForeground(NIGHT_TEXT);
			area.setCaretColor(NIGHT_TEXT);
			area.setSelectionColor(new Color(44, 94, 145));
			area.setSelectedTextColor(Color.WHITE);
		}
		else if (component instanceof JTextField)
		{
			JTextField field = (JTextField) component;
			field.setOpaque(true);
			field.setBackground(NIGHT_SURFACE_ALT);
			field.setForeground(NIGHT_TEXT);
			field.setCaretColor(NIGHT_BLUE);
			field.setBorder(BorderFactory.createCompoundBorder(
					BorderFactory.createLineBorder(NIGHT_BORDER),
					BorderFactory.createEmptyBorder(7, 9, 7, 9)));
		}
		else if (component instanceof JTable)
		{
			JTable table = (JTable) component;
			table.setBackground(NIGHT_SURFACE);
			table.setForeground(NIGHT_TEXT);
			table.setGridColor(NIGHT_BORDER);
			table.setSelectionBackground(new Color(30, 82, 126));
			table.setSelectionForeground(Color.WHITE);
			table.getTableHeader().setBackground(NIGHT_PANEL_INK);
			table.getTableHeader().setForeground(NIGHT_MUTED);
		}
		else if (component instanceof JList)
		{
			JList<?> list = (JList<?>) component;
			list.setBackground(NIGHT_SURFACE);
			list.setForeground(NIGHT_TEXT);
			list.setSelectionBackground(new Color(32, 92, 134));
			list.setSelectionForeground(Color.WHITE);
		}
		else if (component instanceof JComboBox)
		{
			JComboBox<?> comboBox = (JComboBox<?>) component;
			comboBox.setBackground(NIGHT_SURFACE_ALT);
			comboBox.setForeground(NIGHT_TEXT);
			comboBox.setBorder(BorderFactory.createLineBorder(NIGHT_BORDER));
		}
		else if (component instanceof AbstractButton)
		{
			AbstractButton button = (AbstractButton) component;
			button.setForeground(nightForeground(button.getForeground()));
			button.setBackground(NIGHT_PANEL_INK);
		}
		else if (component instanceof JLabel)
		{
			JLabel label = (JLabel) component;
			label.setForeground(nightForeground(label.getForeground()));
			if (label.isOpaque())
			{
				label.setBackground(NIGHT_PANEL_INK);
			}
		}
		else if (component instanceof JScrollBar)
		{
			styleScrollBar((JScrollBar) component);
		}
		else if (component instanceof JViewport)
		{
			((JViewport) component).setBackground(NIGHT_BACKGROUND);
		}
		else if (component instanceof JScrollPane)
		{
			JScrollPane scrollPane = (JScrollPane) component;
			scrollPane.setBackground(NIGHT_BACKGROUND);
			if (scrollPane.getViewport() != null)
			{
				scrollPane.getViewport().setBackground(NIGHT_BACKGROUND);
			}
			styleScrollBar(scrollPane.getVerticalScrollBar());
			styleScrollBar(scrollPane.getHorizontalScrollBar());
		}
		else if (component instanceof JPanel)
		{
			JPanel panel = (JPanel) component;
			if (panel.isOpaque())
			{
				panel.setBackground(NIGHT_BACKGROUND);
			}
		}
		retintBorder(component, true);
	}

	private static void restoreComponent(Component component)
	{
		if (!(component instanceof JComponent))
		{
			return;
		}
		JComponent jComponent = (JComponent) component;
		if (Boolean.TRUE.equals(jComponent.getClientProperty(ORIGINAL_MARKER)))
		{
			component.setBackground((Color) restoreValue(
					jComponent.getClientProperty(ORIGINAL_BACKGROUND)));
			component.setForeground((Color) restoreValue(
					jComponent.getClientProperty(ORIGINAL_FOREGROUND)));
			Border border = (Border) restoreValue(
					jComponent.getClientProperty(ORIGINAL_BORDER));
			jComponent.setBorder(border);
			Object opaque = restoreValue(jComponent.getClientProperty(ORIGINAL_OPAQUE));
			if (opaque instanceof Boolean)
			{
				jComponent.setOpaque(((Boolean) opaque).booleanValue());
			}
			if (component instanceof JScrollBar)
			{
				Object ui = restoreValue(
						jComponent.getClientProperty(ORIGINAL_SCROLLBAR_UI));
				if (ui instanceof ScrollBarUI)
				{
					((JScrollBar) component).setUI((ScrollBarUI) ui);
				}
			}
			jComponent.putClientProperty(ORIGINAL_MARKER, null);
			jComponent.putClientProperty(ORIGINAL_BACKGROUND, null);
			jComponent.putClientProperty(ORIGINAL_FOREGROUND, null);
			jComponent.putClientProperty(ORIGINAL_BORDER, null);
			jComponent.putClientProperty(ORIGINAL_OPAQUE, null);
			jComponent.putClientProperty(ORIGINAL_SCROLLBAR_UI, null);
		}
		retintBorder(component, false);
	}

	private static void remember(JComponent component)
	{
		if (Boolean.TRUE.equals(component.getClientProperty(ORIGINAL_MARKER)))
		{
			return;
		}
		component.putClientProperty(ORIGINAL_MARKER, Boolean.TRUE);
		component.putClientProperty(ORIGINAL_BACKGROUND, storeValue(component.getBackground()));
		component.putClientProperty(ORIGINAL_FOREGROUND, storeValue(component.getForeground()));
		component.putClientProperty(ORIGINAL_BORDER, storeValue(component.getBorder()));
		component.putClientProperty(ORIGINAL_OPAQUE, Boolean.valueOf(component.isOpaque()));
		Border border = component.getBorder();
		if (border instanceof TitledBorder)
		{
			component.putClientProperty(
					ORIGINAL_TITLE_COLOR,
					storeValue(((TitledBorder) border).getTitleColor()));
		}
		if (component instanceof JScrollBar)
		{
			component.putClientProperty(
					ORIGINAL_SCROLLBAR_UI,
					storeValue(((JScrollBar) component).getUI()));
		}
	}

	private static void styleScrollBar(JScrollBar scrollBar)
	{
		if (scrollBar == null)
		{
			return;
		}
		remember(scrollBar);
		scrollBar.setOpaque(true);
		scrollBar.setBackground(NIGHT_BACKGROUND);
		scrollBar.setForeground(NIGHT_BORDER);
		scrollBar.setBorder(BorderFactory.createEmptyBorder());
		if (!(scrollBar.getUI() instanceof NightScrollBarUI))
		{
			scrollBar.setUI(new NightScrollBarUI());
		}
	}

	private static Object storeValue(Object value)
	{
		return value == null ? NULL_VALUE : value;
	}

	private static Object restoreValue(Object value)
	{
		return value == NULL_VALUE ? null : value;
	}

	private static void retintBorder(Component component, boolean night)
	{
		if (!(component instanceof JComponent))
		{
			return;
		}
		JComponent jComponent = (JComponent) component;
		Border border = jComponent.getBorder();
		if (!(border instanceof TitledBorder))
		{
			return;
		}
		TitledBorder titled = (TitledBorder) border;
		if (night)
		{
			titled.setTitleColor(NIGHT_MUTED);
		}
		else
		{
			Object value = restoreValue(jComponent.getClientProperty(ORIGINAL_TITLE_COLOR));
			if (value instanceof Color)
			{
				titled.setTitleColor((Color) value);
			}
			jComponent.putClientProperty(ORIGINAL_TITLE_COLOR, null);
		}
	}

	private static Color nightForeground(Color color)
	{
		if (color == null)
		{
			return NIGHT_TEXT;
		}
		int red = color.getRed();
		int green = color.getGreen();
		int blue = color.getBlue();
		float max = Math.max(red, Math.max(green, blue)) / 255.0f;
		float min = Math.min(red, Math.min(green, blue)) / 255.0f;
		float saturation = max <= 0.0001f ? 0.0f : (max - min) / max;
		float luminance = (0.2126f * red + 0.7152f * green + 0.0722f * blue) / 255.0f;
		if (saturation < 0.16f)
		{
			return luminance > 0.55f ? NIGHT_MUTED : NIGHT_TEXT;
		}
		float[] hsb = Color.RGBtoHSB(red, green, blue, null);
		return accentForHue(hsb[0]);
	}

	private static Color accentForHue(float hue)
	{
		if (hue < 0.035f || hue >= 0.94f)
		{
			return NIGHT_RED;
		}
		if (hue < 0.13f)
		{
			return NIGHT_AMBER;
		}
		if (hue < 0.40f)
		{
			return NIGHT_GREEN;
		}
		if (hue < 0.54f)
		{
			return NIGHT_TEAL;
		}
		if (hue < 0.70f)
		{
			return NIGHT_BLUE;
		}
		if (hue < 0.84f)
		{
			return NIGHT_PURPLE;
		}
		return NIGHT_PINK;
	}

	private static final class AdaptiveColor extends Color
	{
		private final Color day;
		private final Color night;

		AdaptiveColor(Color day, Color night)
		{
			super(day.getRGB(), true);
			this.day = day;
			this.night = night;
		}

		private Color active()
		{
			return nightMode ? night : day;
		}

		public int getRed()
		{
			return active().getRed();
		}

		public int getGreen()
		{
			return active().getGreen();
		}

		public int getBlue()
		{
			return active().getBlue();
		}

		public int getAlpha()
		{
			return active().getAlpha();
		}

		public int getRGB()
		{
			return active().getRGB();
		}

		public Color brighter()
		{
			return active().brighter();
		}

		public Color darker()
		{
			return active().darker();
		}
	}

	private static final class NightScrollBarUI extends BasicScrollBarUI
	{
		protected void configureScrollBarColors()
		{
			thumbColor = withAlpha(NIGHT_BORDER, 218);
			thumbDarkShadowColor = withAlpha(NIGHT_BACKGROUND_BOTTOM, 0);
			thumbHighlightColor = withAlpha(NIGHT_BLUE, 90);
			thumbLightShadowColor = withAlpha(NIGHT_GREEN, 50);
			trackColor = NIGHT_BACKGROUND;
			trackHighlightColor = NIGHT_BACKGROUND_BOTTOM;
		}

		protected JButton createDecreaseButton(int orientation)
		{
			return invisibleButton();
		}

		protected JButton createIncreaseButton(int orientation)
		{
			return invisibleButton();
		}

		protected void paintTrack(
				Graphics graphics,
				JComponent component,
				Rectangle trackBounds)
		{
			Graphics2D g2 = (Graphics2D) graphics.create();
			g2.setColor(NIGHT_BACKGROUND_BOTTOM);
			g2.fillRect(
					trackBounds.x,
					trackBounds.y,
					trackBounds.width,
					trackBounds.height);
			g2.dispose();
		}

		protected void paintThumb(
				Graphics graphics,
				JComponent component,
				Rectangle thumbBounds)
		{
			if (thumbBounds.isEmpty() || !component.isEnabled())
			{
				return;
			}
			Graphics2D g2 = (Graphics2D) graphics.create();
			g2.setRenderingHint(
					RenderingHints.KEY_ANTIALIASING,
					RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setColor(withAlpha(NIGHT_BORDER, isDragging ? 245 : 196));
			g2.fillRoundRect(
					thumbBounds.x + 2,
					thumbBounds.y + 2,
					Math.max(3, thumbBounds.width - 4),
					Math.max(3, thumbBounds.height - 4),
					8,
					8);
			g2.setColor(withAlpha(NIGHT_BLUE, 70));
			g2.drawRoundRect(
					thumbBounds.x + 2,
					thumbBounds.y + 2,
					Math.max(3, thumbBounds.width - 4),
					Math.max(3, thumbBounds.height - 4),
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

	static final class ThemedTabPanel extends JPanel
	{
		private final Component view;
		private final boolean transformInNightMode;

		ThemedTabPanel(Component view, boolean transformInNightMode)
		{
			super(new BorderLayout());
			this.view = view;
			this.transformInNightMode = transformInNightMode;
			setOpaque(false);
			add(view, BorderLayout.CENTER);
		}

		Component getView()
		{
			return view;
		}

		boolean isTransformInNightMode()
		{
			return transformInNightMode;
		}

		protected void paintComponent(Graphics graphics)
		{
			if (!nightMode)
			{
				super.paintComponent(graphics);
				return;
			}
			Graphics2D g2 = (Graphics2D) graphics.create();
			g2.setRenderingHint(
					RenderingHints.KEY_ANTIALIASING,
					RenderingHints.VALUE_ANTIALIAS_ON);
			int width = getWidth();
			int height = getHeight();
			g2.setPaint(new GradientPaint(
					0,
					0,
					NIGHT_BACKGROUND,
					0,
					Math.max(1, height),
					NIGHT_BACKGROUND_BOTTOM));
			g2.fillRect(0, 0, width, height);
			g2.setComposite(AlphaComposite.SrcOver.derive(0.38f));
			g2.setColor(withAlpha(NIGHT_BORDER, 70));
			for (int x = 0; x < width; x += 34)
			{
				g2.drawLine(x, 0, x, height);
			}
			for (int y = 0; y < height; y += 34)
			{
				g2.drawLine(0, y, width, y);
			}
			paintRibbon(g2, width, height, 0.22f, NIGHT_BLUE, 21.0f);
			paintRibbon(g2, width, height, 0.62f, NIGHT_GREEN, 18.0f);
			paintRibbon(g2, width, height, 0.86f, NIGHT_PURPLE, 16.0f);
			g2.dispose();
		}

		protected void paintChildren(Graphics graphics)
		{
			super.paintChildren(graphics);
			if (nightMode && transformInNightMode)
			{
				paintPolish(graphics);
			}
		}

		private void paintRibbon(
				Graphics2D g2,
				int width,
				int height,
				float anchor,
				Color color,
				float stroke)
		{
			Path2D.Float path = new Path2D.Float();
			float baseY = height * anchor;
			path.moveTo(-50, baseY);
			path.curveTo(
					width * 0.26f,
					baseY - 58,
					width * 0.48f,
					baseY + 42,
					width * 0.72f,
					baseY - 4);
			path.curveTo(
					width * 0.88f,
					baseY - 38,
					width + 20,
					baseY + 28,
					width + 70,
					baseY - 14);
			g2.setStroke(new BasicStroke(
					stroke,
					BasicStroke.CAP_ROUND,
					BasicStroke.JOIN_ROUND));
			g2.setColor(withAlpha(color, 38));
			g2.draw(path);
			g2.setStroke(new BasicStroke(
					1.2f,
					BasicStroke.CAP_ROUND,
					BasicStroke.JOIN_ROUND));
			g2.setColor(withAlpha(color, 86));
			g2.draw(path);
		}

		private void paintPolish(Graphics graphics)
		{
			Graphics2D g2 = (Graphics2D) graphics.create();
			g2.setComposite(AlphaComposite.SrcOver.derive(0.45f));
			g2.setColor(withAlpha(NIGHT_BLUE, 44));
			g2.drawLine(0, 0, getWidth(), 0);
			g2.setColor(withAlpha(NIGHT_GREEN, 28));
			g2.drawLine(0, getHeight() - 1, getWidth(), getHeight() - 1);
			g2.dispose();
		}
	}
}
