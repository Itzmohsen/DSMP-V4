package TwitterGatherDataFollowers.userRyersonU;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

/**
 * Branded replacements for the stock JOptionPane dialogs so every popup
 * matches the Nebula design system in both day and night mode. The helpers
 * mirror the JOptionPane signatures used across the app, so call sites can
 * swap in place.
 */
final class NebulaDialogs
{
	static final int TONE_INFO = 0;
	static final int TONE_SUCCESS = 1;
	static final int TONE_WARNING = 2;
	static final int TONE_ERROR = 3;

	private NebulaDialogs()
	{
	}

	static void showInfo(Component parent, String message, String title)
	{
		showMessage(parent, message, title, TONE_INFO);
	}

	static void showSuccess(Component parent, String message, String title)
	{
		showMessage(parent, message, title, TONE_SUCCESS);
	}

	static void showWarning(Component parent, String message, String title)
	{
		showMessage(parent, message, title, TONE_WARNING);
	}

	static void showError(Component parent, String message, String title)
	{
		showMessage(parent, message, title, TONE_ERROR);
	}

	static void showMessage(Component parent, String message, String title, int tone)
	{
		NebulaDesign.installUiDefaults();
		JOptionPane pane = new JOptionPane(
				buildContent(message, title, tone),
				JOptionPane.PLAIN_MESSAGE,
				JOptionPane.DEFAULT_OPTION,
				null,
				new Object[] { "OK" },
				"OK");
		decorate(pane);
		JDialog dialog = pane.createDialog(anchor(parent), title == null ? "DSMP Studio" : title);
		dialog.setVisible(true);
		dialog.dispose();
	}

	/** Returns JOptionPane.YES_OPTION or NO_OPTION, mirroring showConfirmDialog. */
	static int showConfirm(Component parent, String message, String title, int tone)
	{
		NebulaDesign.installUiDefaults();
		Object[] options = { "Yes", "No" };
		JOptionPane pane = new JOptionPane(
				buildContent(message, title, tone),
				JOptionPane.PLAIN_MESSAGE,
				JOptionPane.YES_NO_OPTION,
				null,
				options,
				options[1]);
		decorate(pane);
		JDialog dialog = pane.createDialog(anchor(parent), title == null ? "DSMP Studio" : title);
		dialog.setVisible(true);
		dialog.dispose();
		Object value = pane.getValue();
		if ("Yes".equals(value))
		{
			return JOptionPane.YES_OPTION;
		}
		return JOptionPane.NO_OPTION;
	}

	/** Scrollable long-form text dialog (help walls, logs, reports). */
	static void showLongText(Component parent, String title, String body)
	{
		NebulaDesign.installUiDefaults();
		JTextArea area = new JTextArea(body);
		area.setEditable(false);
		area.setLineWrap(true);
		area.setWrapStyleWord(true);
		area.setFont(NebulaDesign.fontBody(12.5f));
		area.setBackground(NebulaDesign.night()
				? NebulaDesign.NIGHT_CONSOLE
				: NebulaDesign.DAY_CONSOLE);
		area.setForeground(NebulaDesign.night()
				? NebulaDesign.NIGHT_CONSOLE_TEXT
				: NebulaDesign.DAY_CONSOLE_TEXT);
		area.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));
		area.setCaretPosition(0);
		JScrollPane scroll = new JScrollPane(area);
		scroll.setBorder(BorderFactory.createLineBorder(NebulaDesign.currentBorderSubtle(), 1));
		scroll.setPreferredSize(new Dimension(640, 460));
		scroll.getVerticalScrollBar().setUI(new NebulaWidgets.PillScrollBarUI());
		scroll.getVerticalScrollBar().setOpaque(false);

		JPanel content = new JPanel(new BorderLayout(0, 10));
		content.setOpaque(false);
		JLabel heading = new JLabel(title);
		heading.setFont(NebulaDesign.fontSemiBold(16f));
		heading.setForeground(NebulaDesign.currentTextPrimary());
		content.add(heading, BorderLayout.NORTH);
		content.add(scroll, BorderLayout.CENTER);

		JOptionPane pane = new JOptionPane(
				content,
				JOptionPane.PLAIN_MESSAGE,
				JOptionPane.DEFAULT_OPTION,
				null,
				new Object[] { "Close" },
				"Close");
		decorate(pane);
		JDialog dialog = pane.createDialog(anchor(parent), title);
		dialog.setResizable(true);
		dialog.setVisible(true);
		dialog.dispose();
	}

	private static Component anchor(Component parent)
	{
		if (parent != null)
		{
			return parent;
		}
		java.awt.Frame[] frames = java.awt.Frame.getFrames();
		for (int i = 0; i < frames.length; i++)
		{
			if (frames[i].isShowing() && frames[i] instanceof ControllerAgentGui)
			{
				return frames[i];
			}
		}
		return null;
	}

	private static JComponent buildContent(String message, String title, int tone)
	{
		JPanel content = new JPanel(new BorderLayout(12, 0));
		content.setOpaque(false);
		content.add(new ToneBadge(tone), BorderLayout.WEST);

		JPanel textStack = new JPanel();
		textStack.setOpaque(false);
		textStack.setLayout(new javax.swing.BoxLayout(textStack, javax.swing.BoxLayout.Y_AXIS));
		if (title != null && title.length() > 0)
		{
			JLabel heading = new JLabel(title);
			heading.setFont(NebulaDesign.fontSemiBold(14f));
			heading.setForeground(NebulaDesign.currentTextPrimary());
			heading.setAlignmentX(Component.LEFT_ALIGNMENT);
			textStack.add(heading);
			textStack.add(javax.swing.Box.createVerticalStrut(5));
		}
		JTextArea body = new JTextArea(message == null ? "" : message);
		body.setEditable(false);
		body.setLineWrap(true);
		body.setWrapStyleWord(true);
		body.setOpaque(false);
		body.setFont(NebulaDesign.fontBody(13f));
		body.setForeground(NebulaDesign.currentTextSecondary());
		body.setBorder(null);
		body.setAlignmentX(Component.LEFT_ALIGNMENT);
		body.setSize(new Dimension(420, Integer.MAX_VALUE));
		Dimension preferred = body.getPreferredSize();
		body.setPreferredSize(new Dimension(420, Math.min(preferred.height, 380)));
		textStack.add(body);
		content.add(textStack, BorderLayout.CENTER);
		content.setBorder(BorderFactory.createEmptyBorder(8, 4, 6, 4));
		return content;
	}

	private static void decorate(JOptionPane pane)
	{
		pane.setBackground(NebulaDesign.currentSurface());
		pane.setBorder(BorderFactory.createEmptyBorder(10, 12, 8, 12));
		applySurface(pane);
	}

	private static void applySurface(Component component)
	{
		if (component instanceof JPanel || component instanceof JOptionPane)
		{
			((JComponent) component).setOpaque(true);
			component.setBackground(NebulaDesign.currentSurface());
		}
		if (component instanceof javax.swing.JButton)
		{
			javax.swing.JButton button = (javax.swing.JButton) component;
			button.setFont(NebulaDesign.fontMedium(12.5f));
			button.setBackground(NebulaDesign.night()
					? NebulaDesign.NIGHT_SURFACE_RAISED
					: NebulaDesign.DAY_SURFACE);
			button.setForeground(NebulaDesign.currentTextPrimary());
			button.setFocusPainted(false);
			button.setBorder(BorderFactory.createCompoundBorder(
					BorderFactory.createLineBorder(NebulaDesign.currentBorderStrong(), 1),
					BorderFactory.createEmptyBorder(6, 18, 6, 18)));
			NebulaWidgets.polishButton(button);
		}
		if (component instanceof java.awt.Container)
		{
			Component[] children = ((java.awt.Container) component).getComponents();
			for (int i = 0; i < children.length; i++)
			{
				applySurface(children[i]);
			}
		}
	}

	/** Rounded tone icon: info dot, success check, warning triangle, error cross. */
	private static final class ToneBadge extends JComponent
	{
		private final int tone;

		ToneBadge(int tone)
		{
			this.tone = tone;
			setPreferredSize(new Dimension(38, 38));
			setMinimumSize(new Dimension(38, 38));
			setMaximumSize(new Dimension(38, 38));
		}

		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g2 = (Graphics2D) graphics.create();
			NebulaDesign.prepare(g2);
			boolean night = NebulaDesign.night();
			Color strong;
			Color soft;
			switch (tone)
			{
				case TONE_SUCCESS:
					strong = night ? NebulaDesign.NIGHT_SUCCESS : NebulaDesign.DAY_SUCCESS;
					soft = night ? NebulaDesign.NIGHT_SUCCESS_SOFT : NebulaDesign.DAY_SUCCESS_SOFT;
					break;
				case TONE_WARNING:
					strong = night ? NebulaDesign.NIGHT_WARNING : NebulaDesign.DAY_WARNING;
					soft = night ? NebulaDesign.NIGHT_WARNING_SOFT : NebulaDesign.DAY_WARNING_SOFT;
					break;
				case TONE_ERROR:
					strong = night ? NebulaDesign.NIGHT_DANGER : NebulaDesign.DAY_DANGER;
					soft = night ? NebulaDesign.NIGHT_DANGER_SOFT : NebulaDesign.DAY_DANGER_SOFT;
					break;
				default:
					strong = night ? NebulaDesign.NIGHT_ACCENT : NebulaDesign.DAY_ACCENT;
					soft = night ? NebulaDesign.NIGHT_ACCENT_SOFT : NebulaDesign.DAY_ACCENT_SOFT;
					break;
			}
			int size = 34;
			int x = (getWidth() - size) / 2;
			int y = 2;
			g2.setColor(soft);
			g2.fillRoundRect(x, y, size, size, 12, 12);
			g2.setColor(strong);
			g2.setStroke(new java.awt.BasicStroke(2.0f,
					java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
			int cx = x + size / 2;
			int cy = y + size / 2;
			switch (tone)
			{
				case TONE_SUCCESS:
					g2.drawLine(cx - 6, cy, cx - 2, cy + 5);
					g2.drawLine(cx - 2, cy + 5, cx + 6, cy - 5);
					break;
				case TONE_WARNING:
					g2.drawLine(cx, cy - 6, cx, cy + 2);
					g2.fillOval(cx - 1, cy + 5, 3, 3);
					break;
				case TONE_ERROR:
					g2.drawLine(cx - 5, cy - 5, cx + 5, cy + 5);
					g2.drawLine(cx + 5, cy - 5, cx - 5, cy + 5);
					break;
				default:
					g2.fillOval(cx - 1, cy - 7, 3, 3);
					g2.drawLine(cx, cy - 1, cx, cy + 6);
					break;
			}
			g2.dispose();
		}
	}
}
