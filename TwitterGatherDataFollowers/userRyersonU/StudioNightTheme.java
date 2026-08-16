package TwitterGatherDataFollowers.userRyersonU;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.MultipleGradientPaint;
import java.awt.RadialGradientPaint;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
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

/**
 * Compatibility facade for the Nebula design system. The public surface that
 * the rest of the application depends on (mode flag, adaptive colors, theme
 * tree application, tab wrapping, palette constants) is unchanged, but every
 * value and painter now resolves to NebulaDesign tokens so both day and night
 * mode share one coherent visual language.
 */
final class StudioNightTheme
{
	static final Color NIGHT_BACKGROUND = NebulaDesign.NIGHT_CANVAS_TOP;
	static final Color NIGHT_BACKGROUND_BOTTOM = NebulaDesign.NIGHT_CANVAS_BOTTOM;
	static final Color NIGHT_SURFACE = NebulaDesign.NIGHT_SURFACE;
	static final Color NIGHT_SURFACE_ALT = NebulaDesign.NIGHT_SURFACE_ALT;
	static final Color NIGHT_PANEL_INK = NebulaDesign.NIGHT_SURFACE_RAISED;
	static final Color NIGHT_BORDER = NebulaDesign.NIGHT_BORDER_STRONG;
	static final Color NIGHT_TEXT = NebulaDesign.NIGHT_TEXT_PRIMARY;
	static final Color NIGHT_MUTED = NebulaDesign.NIGHT_TEXT_SECONDARY;
	static final Color NIGHT_BLUE = NebulaDesign.NIGHT_ACCENT;
	static final Color NIGHT_GREEN = NebulaDesign.NIGHT_SUCCESS;
	static final Color NIGHT_AMBER = NebulaDesign.NIGHT_WARNING;
	static final Color NIGHT_RED = NebulaDesign.NIGHT_DANGER;
	static final Color NIGHT_PURPLE = NebulaDesign.NIGHT_PURPLE;
	static final Color NIGHT_TEAL = NebulaDesign.NIGHT_INFO;
	static final Color NIGHT_PINK = NebulaDesign.NIGHT_PINK;
	private static final String ORIGINAL_MARKER = "studioNightTheme.original";
	private static final String ORIGINAL_BACKGROUND = "studioNightTheme.background";
	private static final String ORIGINAL_FOREGROUND = "studioNightTheme.foreground";
	private static final String ORIGINAL_BORDER = "studioNightTheme.border";
	private static final String ORIGINAL_OPAQUE = "studioNightTheme.opaque";
	private static final String ORIGINAL_TITLE_COLOR = "studioNightTheme.titleColor";
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

	/**
	 * Mode-independent chrome pass: installs the Nebula tab UIs and pill
	 * scrollbars across the tree. The delegates resolve colors at paint
	 * time, so this only needs to run when new components appear or the
	 * theme toggles.
	 */
	static void applyTabbedPaneTheme(Component root)
	{
		if (root == null)
		{
			return;
		}
		if (root instanceof JTabbedPane)
		{
			JTabbedPane tabs = (JTabbedPane) root;
			Object style = tabs.getClientProperty(NebulaWidgets.TAB_STYLE_PROPERTY);
			if (!NebulaWidgets.TAB_STYLE_NONE.equals(style)
					&& !NebulaWidgets.TAB_STYLE_RAIL.equals(style)
					&& !(tabs.getUI() instanceof NebulaWidgets.UnderlineTabbedPaneUI))
			{
				tabs.setUI(new NebulaWidgets.UnderlineTabbedPaneUI());
			}
		}
		if (root instanceof JScrollBar)
		{
			installPillScrollBar((JScrollBar) root);
		}
		if (root instanceof javax.swing.JSpinner)
		{
			javax.swing.JSpinner spinner = (javax.swing.JSpinner) root;
			if (!(spinner.getUI() instanceof NebulaWidgets.ChevronSpinnerUI))
			{
				spinner.setUI(new NebulaWidgets.ChevronSpinnerUI());
			}
		}
		if (root instanceof JTable)
		{
			JTable table = (JTable) root;
			if (!(table.getTableHeader() != null
					&& table.getTableHeader().getDefaultRenderer()
							instanceof NebulaWidgets.HeaderRenderer))
			{
				NebulaWidgets.styleTable(table);
			}
		}
		if (root instanceof JScrollPane)
		{
			JScrollPane scrollPane = (JScrollPane) root;
			installPillScrollBar(scrollPane.getVerticalScrollBar());
			installPillScrollBar(scrollPane.getHorizontalScrollBar());
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
			area.setBackground(NebulaDesign.NIGHT_CONSOLE);
			area.setForeground(NebulaDesign.NIGHT_CONSOLE_TEXT);
			area.setCaretColor(NebulaDesign.NIGHT_TEXT_PRIMARY);
			area.setSelectionColor(NebulaDesign.NIGHT_SELECTION);
			area.setSelectedTextColor(Color.WHITE);
		}
		else if (component instanceof JTextField)
		{
			JTextField field = (JTextField) component;
			field.setOpaque(true);
			field.setBackground(NebulaDesign.NIGHT_SURFACE_ALT);
			field.setForeground(NebulaDesign.NIGHT_TEXT_PRIMARY);
			field.setCaretColor(NebulaDesign.NIGHT_ACCENT);
			field.setBorder(BorderFactory.createCompoundBorder(
					BorderFactory.createLineBorder(NebulaDesign.NIGHT_BORDER_SUBTLE),
					BorderFactory.createEmptyBorder(7, 9, 7, 9)));
		}
		else if (component instanceof JTable)
		{
			JTable table = (JTable) component;
			table.setBackground(NebulaDesign.NIGHT_SURFACE);
			table.setForeground(NebulaDesign.NIGHT_TEXT_PRIMARY);
			table.setGridColor(NebulaDesign.NIGHT_BORDER_SUBTLE);
			table.setSelectionBackground(NebulaDesign.NIGHT_SELECTION);
			table.setSelectionForeground(Color.WHITE);
			if (table.getTableHeader() != null)
			{
				table.getTableHeader().setBackground(NebulaDesign.NIGHT_SURFACE_RAISED);
				table.getTableHeader().setForeground(NebulaDesign.NIGHT_TEXT_SECONDARY);
			}
		}
		else if (component instanceof JList)
		{
			JList<?> list = (JList<?>) component;
			list.setBackground(NebulaDesign.NIGHT_SURFACE);
			list.setForeground(NebulaDesign.NIGHT_TEXT_PRIMARY);
			list.setSelectionBackground(NebulaDesign.NIGHT_SELECTION);
			list.setSelectionForeground(Color.WHITE);
		}
		else if (component instanceof JComboBox)
		{
			JComboBox<?> comboBox = (JComboBox<?>) component;
			comboBox.setBackground(NebulaDesign.NIGHT_SURFACE_ALT);
			comboBox.setForeground(NebulaDesign.NIGHT_TEXT_PRIMARY);
			comboBox.setBorder(BorderFactory.createLineBorder(NebulaDesign.NIGHT_BORDER_SUBTLE));
		}
		else if (component instanceof AbstractButton)
		{
			AbstractButton button = (AbstractButton) component;
			button.setForeground(nightForeground(button.getForeground()));
			button.setBackground(NebulaDesign.NIGHT_SURFACE_RAISED);
		}
		else if (component instanceof JLabel)
		{
			JLabel label = (JLabel) component;
			label.setForeground(nightForeground(label.getForeground()));
			if (label.isOpaque())
			{
				label.setBackground(NebulaDesign.NIGHT_SURFACE_RAISED);
			}
		}
		else if (component instanceof JScrollBar)
		{
			installPillScrollBar((JScrollBar) component);
		}
		else if (component instanceof JViewport)
		{
			((JViewport) component).setBackground(NebulaDesign.NIGHT_CANVAS_TOP);
		}
		else if (component instanceof JScrollPane)
		{
			JScrollPane scrollPane = (JScrollPane) component;
			scrollPane.setBackground(NebulaDesign.NIGHT_CANVAS_TOP);
			if (scrollPane.getViewport() != null)
			{
				scrollPane.getViewport().setBackground(NebulaDesign.NIGHT_CANVAS_TOP);
			}
			installPillScrollBar(scrollPane.getVerticalScrollBar());
			installPillScrollBar(scrollPane.getHorizontalScrollBar());
		}
		else if (component instanceof JPanel)
		{
			JPanel panel = (JPanel) component;
			if (panel.isOpaque())
			{
				panel.setBackground(NebulaDesign.NIGHT_CANVAS_TOP);
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
			jComponent.putClientProperty(ORIGINAL_MARKER, null);
			jComponent.putClientProperty(ORIGINAL_BACKGROUND, null);
			jComponent.putClientProperty(ORIGINAL_FOREGROUND, null);
			jComponent.putClientProperty(ORIGINAL_BORDER, null);
			jComponent.putClientProperty(ORIGINAL_OPAQUE, null);
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
	}

	private static void installPillScrollBar(JScrollBar scrollBar)
	{
		if (scrollBar == null)
		{
			return;
		}
		if (!(scrollBar.getUI() instanceof NebulaWidgets.PillScrollBarUI))
		{
			scrollBar.setUI(new NebulaWidgets.PillScrollBarUI());
		}
		scrollBar.setOpaque(false);
		scrollBar.setBorder(BorderFactory.createEmptyBorder());
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
			titled.setTitleColor(NebulaDesign.NIGHT_TEXT_SECONDARY);
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
			return NebulaDesign.NIGHT_TEXT_PRIMARY;
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
			return luminance > 0.55f
					? NebulaDesign.NIGHT_TEXT_SECONDARY
					: NebulaDesign.NIGHT_TEXT_PRIMARY;
		}
		float[] hsb = Color.RGBtoHSB(red, green, blue, null);
		return accentForHue(hsb[0]);
	}

	private static Color accentForHue(float hue)
	{
		if (hue < 0.035f || hue >= 0.94f)
		{
			return NebulaDesign.NIGHT_DANGER;
		}
		if (hue < 0.13f)
		{
			return NebulaDesign.NIGHT_WARNING;
		}
		if (hue < 0.40f)
		{
			return NebulaDesign.NIGHT_SUCCESS;
		}
		if (hue < 0.54f)
		{
			return NebulaDesign.NIGHT_INFO;
		}
		if (hue < 0.70f)
		{
			return NebulaDesign.NIGHT_ACCENT;
		}
		if (hue < 0.84f)
		{
			return NebulaDesign.NIGHT_PURPLE;
		}
		return NebulaDesign.NIGHT_PINK;
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

	/**
	 * Paints the shared Nebula canvas: a vertical gradient with two large,
	 * very soft aurora glows and a faint dot grid. Used by tab content
	 * wrappers and the application shell so every screen sits on the same
	 * calm background in both modes.
	 */
	static BufferedImage renderCanvas(int width, int height, boolean night)
	{
		BufferedImage image = new BufferedImage(
				Math.max(1, width), Math.max(1, height), BufferedImage.TYPE_INT_RGB);
		Graphics2D g2 = image.createGraphics();
		NebulaDesign.prepare(g2);
		Color top = night ? NebulaDesign.NIGHT_CANVAS_TOP : NebulaDesign.DAY_CANVAS_TOP;
		Color bottom = night ? NebulaDesign.NIGHT_CANVAS_BOTTOM : NebulaDesign.DAY_CANVAS_BOTTOM;
		g2.setPaint(new GradientPaint(0, 0, top, 0, Math.max(1, height), bottom));
		g2.fillRect(0, 0, width, height);

		Color accent = night ? NebulaDesign.NIGHT_ACCENT : NebulaDesign.DAY_ACCENT;
		Color purple = night ? NebulaDesign.NIGHT_PURPLE : NebulaDesign.DAY_PURPLE;
		Color teal = night ? NebulaDesign.NIGHT_INFO : NebulaDesign.DAY_INFO;
		int glowAlpha = night ? 26 : 14;
		paintGlow(g2, width * 0.12f, -height * 0.15f, Math.max(width, height) * 0.55f,
				withAlpha(accent, glowAlpha));
		paintGlow(g2, width * 0.92f, height * 0.30f, Math.max(width, height) * 0.50f,
				withAlpha(purple, Math.max(8, glowAlpha - 6)));
		paintGlow(g2, width * 0.45f, height * 1.10f, Math.max(width, height) * 0.60f,
				withAlpha(teal, Math.max(8, glowAlpha - 8)));

		Color dot = night
				? withAlpha(NebulaDesign.NIGHT_BORDER_SUBTLE, 64)
				: withAlpha(NebulaDesign.DAY_BORDER_STRONG, 56);
		g2.setColor(dot);
		for (int y = 12; y < height; y += 24)
		{
			for (int x = 12; x < width; x += 24)
			{
				g2.fillRect(x, y, 1, 1);
			}
		}
		g2.dispose();
		return image;
	}

	private static void paintGlow(Graphics2D g2, float centerX, float centerY, float radius, Color color)
	{
		if (radius <= 0)
		{
			return;
		}
		RadialGradientPaint paint = new RadialGradientPaint(
				new Point2D.Float(centerX, centerY),
				radius,
				new float[] { 0.0f, 1.0f },
				new Color[] { color, withAlpha(color, 0) },
				MultipleGradientPaint.CycleMethod.NO_CYCLE);
		g2.setPaint(paint);
		g2.fillRect(
				Math.round(centerX - radius),
				Math.round(centerY - radius),
				Math.round(radius * 2),
				Math.round(radius * 2));
	}

	static final class ThemedTabPanel extends JPanel
	{
		private final Component view;
		private final boolean transformInNightMode;
		private BufferedImage canvasCache;
		private boolean cachedNight;

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
			int width = getWidth();
			int height = getHeight();
			if (width <= 0 || height <= 0)
			{
				return;
			}
			if (canvasCache == null
					|| canvasCache.getWidth() != width
					|| canvasCache.getHeight() != height
					|| cachedNight != nightMode)
			{
				if (canvasCache != null)
				{
					canvasCache.flush();
				}
				cachedNight = nightMode;
				canvasCache = renderCanvas(width, height, nightMode);
			}
			graphics.drawImage(canvasCache, 0, 0, null);
		}
	}
}
