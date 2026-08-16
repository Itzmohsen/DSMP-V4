package TwitterGatherDataFollowers.userRyersonU;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.FontUIResource;

/**
 * Nebula design system: the single source of truth for DSMP Studio's dual-mode
 * (day / night) visual language. Every color, font, radius, and spacing value
 * used by the redesigned interface is defined here so all surfaces stay
 * consistent. StudioNightTheme remains the compatibility facade; its palette
 * constants are built from these tokens.
 */
final class NebulaDesign
{
	// ------------------------------------------------------------------
	// Shape + spacing tokens
	// ------------------------------------------------------------------
	static final int RADIUS_CARD = 14;
	static final int RADIUS_CONTROL = 10;
	static final int RADIUS_CONSOLE = 12;
	static final int RADIUS_SMALL = 8;
	static final int SPACE_CARD_PADDING = 16;
	static final int SPACE_BETWEEN_CARDS = 14;

	// ------------------------------------------------------------------
	// Day palette ("porcelain")
	// ------------------------------------------------------------------
	static final Color DAY_CANVAS_TOP = new Color(247, 248, 251);
	static final Color DAY_CANVAS_BOTTOM = new Color(237, 241, 247);
	static final Color DAY_SURFACE = new Color(255, 255, 255);
	static final Color DAY_SURFACE_ALT = new Color(246, 248, 251);
	static final Color DAY_SURFACE_RAISED = new Color(255, 255, 255);
	static final Color DAY_INSET = new Color(238, 241, 246);
	static final Color DAY_CONSOLE = new Color(250, 251, 253);
	static final Color DAY_CONSOLE_TEXT = new Color(31, 41, 55);
	static final Color DAY_BORDER_SUBTLE = new Color(228, 232, 240);
	static final Color DAY_BORDER_STRONG = new Color(203, 213, 228);
	static final Color DAY_TEXT_PRIMARY = new Color(23, 29, 38);
	static final Color DAY_TEXT_SECONDARY = new Color(75, 85, 101);
	static final Color DAY_TEXT_TERTIARY = new Color(100, 115, 137);
	static final Color DAY_TEXT_ON_ACCENT = new Color(255, 255, 255);
	static final Color DAY_ACCENT = new Color(79, 91, 231);
	static final Color DAY_ACCENT_HOVER = new Color(99, 111, 240);
	static final Color DAY_ACCENT_PRESS = new Color(60, 71, 200);
	static final Color DAY_ACCENT_SOFT = new Color(235, 238, 253);
	static final Color DAY_SELECTION = new Color(223, 229, 252);
	static final Color DAY_SUCCESS = new Color(13, 130, 89);
	static final Color DAY_SUCCESS_SOFT = new Color(231, 247, 240);
	static final Color DAY_WARNING = new Color(155, 104, 12);
	static final Color DAY_WARNING_SOFT = new Color(251, 243, 229);
	static final Color DAY_DANGER = new Color(202, 44, 55);
	static final Color DAY_DANGER_SOFT = new Color(252, 235, 236);
	static final Color DAY_INFO = new Color(12, 125, 143);
	static final Color DAY_INFO_SOFT = new Color(229, 244, 247);
	static final Color DAY_PURPLE = new Color(112, 79, 216);
	static final Color DAY_PURPLE_SOFT = new Color(242, 238, 252);
	static final Color DAY_PINK = new Color(198, 44, 134);
	static final Color DAY_PINK_SOFT = new Color(251, 236, 245);
	static final Color DAY_RAIL = new Color(244, 246, 250);

	// ------------------------------------------------------------------
	// Night palette ("deep space")
	// ------------------------------------------------------------------
	static final Color NIGHT_CANVAS_TOP = new Color(11, 16, 24);
	static final Color NIGHT_CANVAS_BOTTOM = new Color(7, 11, 18);
	static final Color NIGHT_SURFACE = new Color(18, 26, 38);
	static final Color NIGHT_SURFACE_ALT = new Color(24, 34, 52);
	static final Color NIGHT_SURFACE_RAISED = new Color(29, 41, 64);
	static final Color NIGHT_INSET = new Color(13, 19, 29);
	static final Color NIGHT_CONSOLE = new Color(9, 13, 21);
	static final Color NIGHT_CONSOLE_TEXT = new Color(214, 228, 248);
	static final Color NIGHT_BORDER_SUBTLE = new Color(36, 50, 74);
	static final Color NIGHT_BORDER_STRONG = new Color(58, 77, 110);
	static final Color NIGHT_TEXT_PRIMARY = new Color(236, 242, 251);
	static final Color NIGHT_TEXT_SECONDARY = new Color(167, 180, 199);
	static final Color NIGHT_TEXT_TERTIARY = new Color(118, 132, 153);
	static final Color NIGHT_TEXT_ON_ACCENT = new Color(10, 16, 28);
	static final Color NIGHT_ACCENT = new Color(125, 162, 255);
	static final Color NIGHT_ACCENT_HOVER = new Color(151, 182, 255);
	static final Color NIGHT_ACCENT_PRESS = new Color(95, 135, 240);
	static final Color NIGHT_ACCENT_SOFT = new Color(125, 162, 255, 41);
	static final Color NIGHT_SELECTION = new Color(47, 79, 141);
	static final Color NIGHT_SUCCESS = new Color(69, 224, 162);
	static final Color NIGHT_SUCCESS_SOFT = new Color(69, 224, 162, 38);
	static final Color NIGHT_WARNING = new Color(255, 198, 92);
	static final Color NIGHT_WARNING_SOFT = new Color(255, 198, 92, 38);
	static final Color NIGHT_DANGER = new Color(255, 123, 114);
	static final Color NIGHT_DANGER_SOFT = new Color(255, 123, 114, 38);
	static final Color NIGHT_INFO = new Color(85, 221, 232);
	static final Color NIGHT_INFO_SOFT = new Color(85, 221, 232, 38);
	static final Color NIGHT_PURPLE = new Color(183, 156, 255);
	static final Color NIGHT_PURPLE_SOFT = new Color(183, 156, 255, 38);
	static final Color NIGHT_PINK = new Color(247, 132, 197);
	static final Color NIGHT_PINK_SOFT = new Color(247, 132, 197, 38);
	static final Color NIGHT_RAIL = new Color(9, 14, 22);

	// ------------------------------------------------------------------
	// Adaptive tokens (resolve day/night automatically via StudioNightTheme)
	// ------------------------------------------------------------------
	static final Color CANVAS_TOP = StudioNightTheme.adaptiveColor(DAY_CANVAS_TOP, NIGHT_CANVAS_TOP);
	static final Color CANVAS_BOTTOM = StudioNightTheme.adaptiveColor(DAY_CANVAS_BOTTOM, NIGHT_CANVAS_BOTTOM);
	static final Color SURFACE = StudioNightTheme.adaptiveColor(DAY_SURFACE, NIGHT_SURFACE);
	static final Color SURFACE_ALT = StudioNightTheme.adaptiveColor(DAY_SURFACE_ALT, NIGHT_SURFACE_ALT);
	static final Color SURFACE_RAISED = StudioNightTheme.adaptiveColor(DAY_SURFACE_RAISED, NIGHT_SURFACE_RAISED);
	static final Color INSET = StudioNightTheme.adaptiveColor(DAY_INSET, NIGHT_INSET);
	static final Color CONSOLE = StudioNightTheme.adaptiveColor(DAY_CONSOLE, NIGHT_CONSOLE);
	static final Color CONSOLE_TEXT = StudioNightTheme.adaptiveColor(DAY_CONSOLE_TEXT, NIGHT_CONSOLE_TEXT);
	static final Color BORDER_SUBTLE = StudioNightTheme.adaptiveColor(DAY_BORDER_SUBTLE, NIGHT_BORDER_SUBTLE);
	static final Color BORDER_STRONG = StudioNightTheme.adaptiveColor(DAY_BORDER_STRONG, NIGHT_BORDER_STRONG);
	static final Color TEXT_PRIMARY = StudioNightTheme.adaptiveColor(DAY_TEXT_PRIMARY, NIGHT_TEXT_PRIMARY);
	static final Color TEXT_SECONDARY = StudioNightTheme.adaptiveColor(DAY_TEXT_SECONDARY, NIGHT_TEXT_SECONDARY);
	static final Color TEXT_TERTIARY = StudioNightTheme.adaptiveColor(DAY_TEXT_TERTIARY, NIGHT_TEXT_TERTIARY);
	static final Color TEXT_ON_ACCENT = StudioNightTheme.adaptiveColor(DAY_TEXT_ON_ACCENT, NIGHT_TEXT_ON_ACCENT);
	static final Color ACCENT = StudioNightTheme.adaptiveColor(DAY_ACCENT, NIGHT_ACCENT);
	static final Color ACCENT_HOVER = StudioNightTheme.adaptiveColor(DAY_ACCENT_HOVER, NIGHT_ACCENT_HOVER);
	static final Color ACCENT_PRESS = StudioNightTheme.adaptiveColor(DAY_ACCENT_PRESS, NIGHT_ACCENT_PRESS);
	static final Color ACCENT_SOFT = StudioNightTheme.adaptiveColor(DAY_ACCENT_SOFT, NIGHT_ACCENT_SOFT);
	static final Color SELECTION = StudioNightTheme.adaptiveColor(DAY_SELECTION, NIGHT_SELECTION);
	static final Color SUCCESS = StudioNightTheme.adaptiveColor(DAY_SUCCESS, NIGHT_SUCCESS);
	static final Color SUCCESS_SOFT = StudioNightTheme.adaptiveColor(DAY_SUCCESS_SOFT, NIGHT_SUCCESS_SOFT);
	static final Color WARNING = StudioNightTheme.adaptiveColor(DAY_WARNING, NIGHT_WARNING);
	static final Color WARNING_SOFT = StudioNightTheme.adaptiveColor(DAY_WARNING_SOFT, NIGHT_WARNING_SOFT);
	static final Color DANGER = StudioNightTheme.adaptiveColor(DAY_DANGER, NIGHT_DANGER);
	static final Color DANGER_SOFT = StudioNightTheme.adaptiveColor(DAY_DANGER_SOFT, NIGHT_DANGER_SOFT);
	static final Color INFO = StudioNightTheme.adaptiveColor(DAY_INFO, NIGHT_INFO);
	static final Color INFO_SOFT = StudioNightTheme.adaptiveColor(DAY_INFO_SOFT, NIGHT_INFO_SOFT);
	static final Color PURPLE = StudioNightTheme.adaptiveColor(DAY_PURPLE, NIGHT_PURPLE);
	static final Color PURPLE_SOFT = StudioNightTheme.adaptiveColor(DAY_PURPLE_SOFT, NIGHT_PURPLE_SOFT);
	static final Color PINK = StudioNightTheme.adaptiveColor(DAY_PINK, NIGHT_PINK);
	static final Color PINK_SOFT = StudioNightTheme.adaptiveColor(DAY_PINK_SOFT, NIGHT_PINK_SOFT);
	static final Color RAIL = StudioNightTheme.adaptiveColor(DAY_RAIL, NIGHT_RAIL);

	private static final Color[] DAY_CHART = {
			new Color(79, 91, 231),
			new Color(13, 148, 136),
			new Color(217, 119, 6),
			new Color(219, 39, 119),
			new Color(124, 58, 237),
			new Color(22, 163, 74)
	};
	private static final Color[] NIGHT_CHART = {
			new Color(125, 162, 255),
			new Color(45, 212, 191),
			new Color(251, 191, 36),
			new Color(244, 114, 182),
			new Color(167, 139, 250),
			new Color(74, 222, 128)
	};

	private static final Map<String, Font> FONT_CACHE = new HashMap<String, Font>();
	private static boolean fontsLoaded;
	private static boolean fontsAvailable;

	private NebulaDesign()
	{
	}

	// ------------------------------------------------------------------
	// Chart palette
	// ------------------------------------------------------------------
	static Color chartColor(int index)
	{
		Color[] palette = StudioNightTheme.isNightMode() ? NIGHT_CHART : DAY_CHART;
		return palette[Math.floorMod(index, palette.length)];
	}

	static int chartColorCount()
	{
		return DAY_CHART.length;
	}

	// ------------------------------------------------------------------
	// Fonts
	// ------------------------------------------------------------------
	static synchronized void loadFonts()
	{
		if (fontsLoaded)
		{
			return;
		}
		fontsLoaded = true;
		String[] families = {
				"Inter-Regular.ttf",
				"Inter-Medium.ttf",
				"Inter-SemiBold.ttf",
				"Inter-Bold.ttf",
				"Inter-Italic.ttf",
				"JetBrainsMono-Regular.ttf",
				"JetBrainsMono-Medium.ttf",
				"JetBrainsMono-Bold.ttf"
		};
		File fontDir = new File("fonts");
		if (!fontDir.isDirectory())
		{
			File fallback = new File(System.getProperty("dsmp.project.root", "."), "fonts");
			if (fallback.isDirectory())
			{
				fontDir = fallback;
			}
		}
		GraphicsEnvironment environment = GraphicsEnvironment.getLocalGraphicsEnvironment();
		boolean anyLoaded = false;
		for (int i = 0; i < families.length; i++)
		{
			File file = new File(fontDir, families[i]);
			if (!file.isFile())
			{
				continue;
			}
			try
			{
				Font font = Font.createFont(Font.TRUETYPE_FONT, file);
				environment.registerFont(font);
				anyLoaded = true;
			}
			catch (Exception ignored)
			{
				// A missing or corrupt font file falls back to the system family.
			}
		}
		fontsAvailable = anyLoaded;
	}

	static boolean hasBundledFonts()
	{
		loadFonts();
		return fontsAvailable;
	}

	private static Font namedFont(String family, String fallbackFamily, int style, float size)
	{
		loadFonts();
		String key = family + "|" + style + "|" + size;
		Font cached = FONT_CACHE.get(key);
		if (cached != null)
		{
			return cached;
		}
		Font font = new Font(fontsAvailable ? family : fallbackFamily, style, Math.round(size));
		if (fontsAvailable && !font.getFamily().equals(family))
		{
			font = new Font(fallbackFamily, style, Math.round(size));
		}
		FONT_CACHE.put(key, font);
		return font;
	}

	/** Regular body text. */
	static Font fontBody(float size)
	{
		return namedFont("Inter", "SansSerif", Font.PLAIN, size);
	}

	/** Medium weight: labels, buttons, list items. */
	static Font fontMedium(float size)
	{
		return namedFont("Inter Medium", "SansSerif", Font.PLAIN, size);
	}

	/** SemiBold weight: titles, values, emphasis. */
	static Font fontSemiBold(float size)
	{
		return namedFont("Inter SemiBold", "SansSerif", Font.PLAIN, size);
	}

	/** Bold weight: rare, reserved for hero numerals. */
	static Font fontBold(float size)
	{
		return namedFont("Inter", "SansSerif", Font.BOLD, size);
	}

	/** Monospace: consoles, logs, aligned numerals. */
	static Font fontMono(float size)
	{
		return namedFont("JetBrains Mono", "Monospaced", Font.PLAIN, size);
	}

	/** Monospace medium for console emphasis. */
	static Font fontMonoMedium(float size)
	{
		return namedFont("JetBrains Mono Medium", "Monospaced", Font.PLAIN, size);
	}

	static Font fontDisplay()
	{
		return fontSemiBold(24f);
	}

	static Font fontTitle()
	{
		return fontSemiBold(16f);
	}

	static Font fontSection()
	{
		return fontSemiBold(11f);
	}

	static Font fontLabel()
	{
		return fontMedium(12f);
	}

	static Font fontSmall()
	{
		return fontMedium(11f);
	}

	// ------------------------------------------------------------------
	// Painting helpers
	// ------------------------------------------------------------------
	static void prepare(Graphics2D g2)
	{
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
		g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
	}

	static Color withAlpha(Color color, int alpha)
	{
		return new Color(color.getRed(), color.getGreen(), color.getBlue(),
				Math.max(0, Math.min(255, alpha)));
	}

	/** Paints uppercase eyebrow text with subtle letterspacing. */
	static void paintEyebrow(Graphics2D g2, String text, int x, int baseline, Color color)
	{
		prepare(g2);
		g2.setFont(fontSection());
		g2.setColor(color);
		String upper = text.toUpperCase();
		FontMetrics metrics = g2.getFontMetrics();
		int cursor = x;
		for (int i = 0; i < upper.length(); i++)
		{
			String letter = String.valueOf(upper.charAt(i));
			g2.drawString(letter, cursor, baseline);
			cursor += metrics.stringWidth(letter) + 1;
		}
	}

	// ------------------------------------------------------------------
	// Borders
	// ------------------------------------------------------------------
	static Border cardPadding()
	{
		return BorderFactory.createEmptyBorder(
				SPACE_CARD_PADDING, SPACE_CARD_PADDING,
				SPACE_CARD_PADDING, SPACE_CARD_PADDING);
	}

	static Border fieldBorder()
	{
		return BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(currentBorderSubtle(), 1),
				BorderFactory.createEmptyBorder(8, 10, 8, 10));
	}

	// ------------------------------------------------------------------
	// Mode-resolved plain colors (for places that must not hold adaptive
	// instances, e.g. UIManager defaults refreshed on toggle)
	// ------------------------------------------------------------------
	static boolean night()
	{
		return StudioNightTheme.isNightMode();
	}

	static Color currentSurface()
	{
		return night() ? NIGHT_SURFACE : DAY_SURFACE;
	}

	static Color currentSurfaceAlt()
	{
		return night() ? NIGHT_SURFACE_ALT : DAY_SURFACE_ALT;
	}

	static Color currentSurfaceRaised()
	{
		return night() ? NIGHT_SURFACE_RAISED : DAY_SURFACE_RAISED;
	}

	static Color currentTextPrimary()
	{
		return night() ? NIGHT_TEXT_PRIMARY : DAY_TEXT_PRIMARY;
	}

	static Color currentTextSecondary()
	{
		return night() ? NIGHT_TEXT_SECONDARY : DAY_TEXT_SECONDARY;
	}

	static Color currentBorderSubtle()
	{
		return night() ? NIGHT_BORDER_SUBTLE : DAY_BORDER_SUBTLE;
	}

	static Color currentBorderStrong()
	{
		return night() ? NIGHT_BORDER_STRONG : DAY_BORDER_STRONG;
	}

	static Color currentAccent()
	{
		return night() ? NIGHT_ACCENT : DAY_ACCENT;
	}

	static Color currentSelection()
	{
		return night() ? NIGHT_SELECTION : DAY_SELECTION;
	}

	/**
	 * Installs mode-appropriate UIManager defaults so popups, tooltips,
	 * option panes, menus, and file choosers match the active theme.
	 * Call whenever the theme toggles, before applyThemeTree.
	 */
	static void installUiDefaults()
	{
		loadFonts();
		FontUIResource body = new FontUIResource(fontBody(13f));
		FontUIResource label = new FontUIResource(fontMedium(12f));
		Object[] fontKeys = {
				"Label.font", body,
				"Button.font", label,
				"ToggleButton.font", label,
				"CheckBox.font", body,
				"RadioButton.font", body,
				"ComboBox.font", body,
				"TextField.font", body,
				"TextArea.font", body,
				"List.font", body,
				"Table.font", body,
				"TableHeader.font", new FontUIResource(fontMedium(11f)),
				"TabbedPane.font", label,
				"OptionPane.messageFont", body,
				"OptionPane.buttonFont", label,
				"ToolTip.font", new FontUIResource(fontMedium(11.5f)),
				"Menu.font", body,
				"MenuItem.font", body,
				"TitledBorder.font", new FontUIResource(fontSemiBold(12f))
		};
		for (int i = 0; i < fontKeys.length; i += 2)
		{
			UIManager.put(fontKeys[i], fontKeys[i + 1]);
		}
		Color surface = currentSurface();
		Color text = currentTextPrimary();
		Color border = currentBorderStrong();
		UIManager.put("ToolTip.background", new ColorUIResource(currentSurfaceRaised()));
		UIManager.put("ToolTip.foreground", new ColorUIResource(text));
		UIManager.put("ToolTip.border", BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(border, 1),
				BorderFactory.createEmptyBorder(5, 8, 5, 8)));
		UIManager.put("OptionPane.background", new ColorUIResource(surface));
		UIManager.put("Panel.background", new ColorUIResource(surface));
		UIManager.put("OptionPane.messageForeground", new ColorUIResource(text));
		UIManager.put("TextField.selectionBackground", new ColorUIResource(currentSelection()));
		UIManager.put("TextArea.selectionBackground", new ColorUIResource(currentSelection()));
		UIManager.put("List.selectionBackground", new ColorUIResource(currentSelection()));
		UIManager.put("List.selectionForeground", new ColorUIResource(text));
		UIManager.put("ComboBox.selectionBackground", new ColorUIResource(currentSelection()));
		UIManager.put("ComboBox.selectionForeground", new ColorUIResource(text));
	}
}
