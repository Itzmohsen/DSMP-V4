package TwitterGatherDataFollowers.userRyersonU;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

import javax.swing.AbstractButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * The Simulator: the recommendation experiment as a guided flow.
 *
 * <pre>
 * Overview   what the next run will do, the next step, the latest quality
 * 1 Data     the dataset, its date window, text processing and users
 * 2 Model    the algorithm, what it recommends, its parameters
 * 3 Targets  who it recommends for, the cutoff k, how it is evaluated
 * 4 Run      the cluster, the pipeline and the live output
 * 5 Results  ranking quality with intervals, baselines and tests
 * </pre>
 *
 * The pages drive the simulator window's own controls, which stay the single
 * source of truth that the controller, the help panel and automation read;
 * every page re-reads them while shown, so the tab always shows the real state.
 */
final class SimulatorPanel extends JPanel
{
	private static final long serialVersionUID = 1L;

	static final String OVERVIEW = "overview";
	static final String DATA = "data";
	static final String MODEL = "model";
	static final String TARGETS = "targets";
	static final String RUN = "run";
	static final String RESULTS = "results";

	/** A page of the Simulator. */
	abstract static class Page extends JPanel
	{
		private static final long serialVersionUID = 1L;

		Page()
		{
			super(new BorderLayout());
			setOpaque(false);
		}

		/** Re-reads the simulator's state (when shown and every half second while shown). */
		abstract void refresh();
	}

	private final ControllerAgentGui gui;
	private final CardLayout cards = new CardLayout();
	private final JPanel deck = new JPanel(cards);
	private final LinkedHashMap<String,Page> pages = new LinkedHashMap<String,Page>();
	private final Navigation navigation = new Navigation();
	private final NodeLabUi.Chip status = new NodeLabUi.Chip("Ready", NodeLabUi.Tone.NEUTRAL, true);
	private final Timer timer;
	private String current = OVERVIEW;
	/** Refreshed every half second: how many users are loaded and targets chosen. */
	private int userCount;
	private int targetCount;

	SimulatorPanel(ControllerAgentGui gui)
	{
		super(new BorderLayout());
		this.gui = gui;
		setOpaque(false);
		deck.setOpaque(false);
		pages.put(OVERVIEW, new SimulatorOverviewPage(this));
		pages.put(DATA, new SimulatorDataPage(this));
		pages.put(MODEL, new SimulatorModelPage(this));
		pages.put(TARGETS, new SimulatorTargetsPage(this));
		pages.put(RUN, new SimulatorRunPage(this));
		pages.put(RESULTS, new SimulatorResultsPage(this));
		for (java.util.Map.Entry<String,Page> page : pages.entrySet())
		{
			deck.add(page.getValue(), page.getKey());
		}
		JPanel top = new JPanel(new BorderLayout());
		top.setOpaque(false);
		top.add(navigation, BorderLayout.CENTER);
		NodeLabUi.Button reset = new NodeLabUi.Button("Reset", NodeLabUi.ButtonKind.GHOST,
				NodeLabUi.Glyph.REFRESH, true);
		reset.setToolTipText("Stop the agents and start a new experiment (keeps the settings you see).");
		reset.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent event)
			{
				if (gui.simulatorResetButton() != null && gui.simulatorResetButton().isEnabled())
				{
					gui.simulatorResetButton().doClick();
				}
			}
		});
		JPanel right = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 8, 0));
		right.setOpaque(false);
		right.add(status);
		right.add(reset);
		right.setBorder(new EmptyBorder(22, 0, 0, 24));
		top.add(right, BorderLayout.EAST);
		add(top, BorderLayout.NORTH);
		add(deck, BorderLayout.CENTER);
		gui.addQualityListener(new java.util.function.Consumer<RecommendationQualityEvaluator.Report>()
		{
			public void accept(RecommendationQualityEvaluator.Report report)
			{
				pages.get(RESULTS).refresh();
				pages.get(OVERVIEW).refresh();
			}
		});
		timer = new Timer(500, new ActionListener()
		{
			public void actionPerformed(ActionEvent event)
			{
				tick();
			}
		});
		timer.setRepeats(true);
	}

	ControllerAgentGui gui()
	{
		return gui;
	}

	void show(String key)
	{
		if (!pages.containsKey(key))
		{
			return;
		}
		current = key;
		cards.show(deck, key);
		pages.get(key).refresh();
		refreshStatus();
		navigation.repaint();
	}

	String currentPage()
	{
		return current;
	}

	Page page(String key)
	{
		return pages.get(key);
	}

	/** Called when the Simulator tab becomes visible. */
	void refreshFromSimulator()
	{
		pages.get(current).refresh();
		refreshStatus();
	}

	public void addNotify()
	{
		super.addNotify();
		timer.start();
	}

	public void removeNotify()
	{
		timer.stop();
		super.removeNotify();
	}

	private void tick()
	{
		if (!isShowing())
		{
			return;
		}
		pages.get(current).refresh();
		refreshStatus();
	}

	int userCount()
	{
		return userCount;
	}

	int targetCount()
	{
		return targetCount;
	}

	private void refreshStatus()
	{
		userCount = gui.loadedUsers().size();
		targetCount = userCount == 0 ? 0 : gui.currentTargets().size();
		String title = gui.simulatorStatusTitle();
		status.set(title == null || title.length() == 0 ? "Ready" : capitalize(title), tone(gui.simulatorStatusTone()));
		status.setToolTipText(gui.simulatorStatusDetail());
		navigation.repaint();
	}

	static NodeLabUi.Tone tone(String statusTone)
	{
		if ("RUNNING".equals(statusTone))
		{
			return NodeLabUi.Tone.INFO;
		}
		if ("SUCCESS".equals(statusTone))
		{
			return NodeLabUi.Tone.SUCCESS;
		}
		if ("WARNING".equals(statusTone))
		{
			return NodeLabUi.Tone.WARNING;
		}
		if ("ERROR".equals(statusTone))
		{
			return NodeLabUi.Tone.DANGER;
		}
		return NodeLabUi.Tone.NEUTRAL;
	}

	/** "READY TO RUN" as "Ready to run". */
	static String capitalize(String text)
	{
		String lower = text.toLowerCase(Locale.US);
		return lower.isEmpty() ? lower : Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
	}

	protected void paintComponent(Graphics graphics)
	{
		Graphics2D g = (Graphics2D)graphics.create();
		g.setPaint(new GradientPaint(0, 0, NebulaDesign.CANVAS_TOP, 0, getHeight(), NebulaDesign.CANVAS_BOTTOM));
		g.fillRect(0, 0, getWidth(), getHeight());
		g.dispose();
	}

	// ------------------------------------------------------------ bindings

	/** Keeps a stepper and a simulator field in step; the field's own listeners run on every change. */
	static void bind(final NodeLabUi.Stepper stepper, final JTextField field)
	{
		stepper.addChangeListener(new ChangeListener()
		{
			public void stateChanged(ChangeEvent event)
			{
				write(field, Integer.toString(stepper.getValue()));
			}
		});
	}

	/** The simulator field's value into the stepper (without echoing it back). */
	static void read(NodeLabUi.Stepper stepper, JTextField field, int fallback)
	{
		if (field == null)
		{
			return;
		}
		int value;
		try
		{
			value = Integer.parseInt(field.getText().trim());
		}
		catch (NumberFormatException blank)
		{
			value = fallback;
		}
		if (value != stepper.getValue())
		{
			stepper.setValueQuietly(value);
		}
	}

	/** Mirrors a text field into a simulator field as the user types; Enter or leaving it applies it. */
	static void bind(final NodeLabUi.Field field, final JTextField target)
	{
		field.getDocument().addDocumentListener(new DocumentListener()
		{
			public void insertUpdate(DocumentEvent event)
			{
				copy();
			}

			public void removeUpdate(DocumentEvent event)
			{
				copy();
			}

			public void changedUpdate(DocumentEvent event)
			{
				copy();
			}

			private void copy()
			{
				if (target != null && field.isFocusOwner() && !field.getText().equals(target.getText()))
				{
					target.setText(field.getText());
				}
			}
		});
		field.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent event)
			{
				write(target, field.getText());
				if (target != null)
				{
					target.postActionEvent();
				}
			}
		});
		field.addFocusListener(new FocusAdapter()
		{
			public void focusLost(FocusEvent event)
			{
				if (target != null)
				{
					target.postActionEvent();
				}
			}
		});
	}

	/** The simulator field's text into the field, unless the user is editing it. */
	static void read(NodeLabUi.Field field, JTextField source)
	{
		if (source != null && !field.isFocusOwner() && !field.getText().equals(source.getText()))
		{
			field.setText(source.getText());
		}
	}

	/** Keeps a switch and a simulator check box in step. */
	static void bind(final NodeLabUi.Switch toggle, final JCheckBox box)
	{
		toggle.addChangeListener(new ChangeListener()
		{
			public void stateChanged(ChangeEvent event)
			{
				if (box != null && box.isSelected() != toggle.isOn())
				{
					box.setSelected(toggle.isOn());
				}
			}
		});
	}

	static void read(NodeLabUi.Switch toggle, JCheckBox box)
	{
		if (box == null)
		{
			return;
		}
		if (toggle.isOn() != box.isSelected())
		{
			toggle.setOnQuietly(box.isSelected());
		}
		toggle.setEnabled(box.isEnabled());
	}

	/** A button that presses a simulator button (only while that one is enabled). */
	static NodeLabUi.Button mirror(String text, NodeLabUi.ButtonKind kind, NodeLabUi.Glyph glyph,
			final AbstractButton legacy)
	{
		NodeLabUi.Button button = new NodeLabUi.Button(text, kind, glyph);
		button.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent event)
			{
				if (legacy != null && legacy.isEnabled())
				{
					legacy.doClick();
				}
			}
		});
		return button;
	}

	static void read(NodeLabUi.Button button, AbstractButton legacy)
	{
		boolean enabled = legacy != null && legacy.isEnabled();
		if (button.isEnabled() != enabled)
		{
			button.setEnabled(enabled);
		}
	}

	/** Sets a simulator field and runs its listeners, as if typed and confirmed. */
	static void write(JTextField field, String text)
	{
		if (field == null || text == null || text.equals(field.getText()))
		{
			return;
		}
		field.setText(text);
		field.postActionEvent();
	}

	/** A small section title between cards. */
	static JComponent sectionTitle(String text)
	{
		NodeLabUi.Text title = NodeLabUi.eyebrow(text);
		title.setBorder(new EmptyBorder(6, 4, 0, 0));
		return title;
	}

	/** A clickable step tile: an icon badge, a title, a caption and the step's number. */
	static JComponent tile(final String number, NodeLabUi.Glyph glyph, final NodeLabUi.Tone tone, String title,
			String caption, final Runnable action)
	{
		final JPanel tile = new JPanel(new BorderLayout(0, 10))
		{
			private static final long serialVersionUID = 1L;
			private boolean hover;

			{
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

					public void mouseClicked(MouseEvent event)
					{
						action.run();
					}
				});
				setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			}

			protected void paintComponent(Graphics graphics)
			{
				Graphics2D g = (Graphics2D)graphics.create();
				NodeLabUi.prepare(g);
				NodeLabUi.paintSurface(g, 0, hover ? 0 : 1, getWidth(), getHeight() - 4, NebulaDesign.RADIUS_CARD,
						NebulaDesign.SURFACE, true);
				if (hover)
				{
					g.setColor(NodeLabUi.alpha(NodeLabUi.tone(tone), 150));
					g.draw(new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1.5f, getHeight() - 5f,
							NebulaDesign.RADIUS_CARD, NebulaDesign.RADIUS_CARD));
				}
				if (number != null)
				{
					g.setColor(NodeLabUi.tone(tone));
					g.fill(new Ellipse2D.Float(getWidth() - 40, 16, 24, 24));
					g.setColor(Color.WHITE);
					g.setFont(NebulaDesign.fontSemiBold(12f));
					g.drawString(number, getWidth() - 28 - g.getFontMetrics().stringWidth(number) / 2f, 32.5f);
				}
				g.dispose();
			}
		};
		tile.setOpaque(false);
		tile.setBorder(new EmptyBorder(16, 18, 18, 18));
		JPanel iconRow = new JPanel(new BorderLayout());
		iconRow.setOpaque(false);
		iconRow.add(NodeLabOverviewPage.badge(glyph, tone, 36), BorderLayout.WEST);
		tile.add(iconRow, BorderLayout.NORTH);
		tile.add(NodeLabUi.stack(3, NodeLabUi.heading(title), NodeLabUi.note(caption)), BorderLayout.CENTER);
		return tile;
	}

	/** An explanation column: an icon badge, a title and a paragraph. */
	static JComponent principle(NodeLabUi.Glyph glyph, NodeLabUi.Tone tone, String title, String text)
	{
		JPanel column = new JPanel(new BorderLayout(0, 10));
		column.setOpaque(false);
		JPanel icon = new JPanel(new BorderLayout());
		icon.setOpaque(false);
		icon.add(NodeLabOverviewPage.badge(glyph, tone, 34), BorderLayout.WEST);
		column.add(icon, BorderLayout.NORTH);
		column.add(NodeLabUi.stack(4, NodeLabUi.strong(title), NodeLabUi.prose(text)), BorderLayout.CENTER);
		return column;
	}

	// ------------------------------------------------------------ navigation

	private final class Navigation extends JPanel
	{
		private static final long serialVersionUID = 1L;
		private final String[] keys = {OVERVIEW, DATA, MODEL, TARGETS, RUN, RESULTS};
		private final String[] labels = {"Overview", "Data", "Model", "Targets", "Run", "Results"};
		private final String[] steps = {null, "1", "2", "3", "4", "5"};
		private final NodeLabUi.Glyph[] glyphs = {
			NodeLabUi.Glyph.HOME, NodeLabUi.Glyph.FOLDER, NodeLabUi.Glyph.SPARK, NodeLabUi.Glyph.TARGET,
			NodeLabUi.Glyph.PLAY, NodeLabUi.Glyph.CHART
		};
		private final List<int[]> bounds = new ArrayList<int[]>();
		private int hover = -1;

		Navigation()
		{
			setOpaque(false);
			setPreferredSize(new Dimension(800, 70));
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
						SimulatorPanel.this.show(keys[index]);
					}
				}
			};
			addMouseListener(mouse);
			addMouseMotionListener(mouse);
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

		/** A step is done when its part of the setup is ready. */
		private boolean done(int index)
		{
			switch (index)
			{
				case 1:
					return userCount > 0;
				case 2:
					return gui.simulatorAlgorithmBox() != null && gui.simulatorAlgorithmBox().getSelectedIndex() >= 0
							&& userCount > 0;
				case 3:
					return targetCount > 0;
				case 4:
					return gui.getLatestQualityReport() != null;
				case 5:
					return gui.getLatestQualityReport() != null;
				default:
					return false;
			}
		}

		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D)graphics.create();
			NodeLabUi.prepare(g);
			int barHeight = 46;
			int top = 14;
			int left = 24;
			g.setPaint(new GradientPaint(left, top, NebulaDesign.ACCENT, left + 36, top + 36, NebulaDesign.INFO));
			g.fill(new RoundRectangle2D.Float(left, top + 5, 36, 36, 12, 12));
			g.setColor(Color.WHITE);
			Graphics2D icon = (Graphics2D)g.create();
			icon.translate(left + 8, top + 13);
			NodeLabUi.paintGlyph(icon, NodeLabUi.Glyph.NETWORK, 20);
			icon.dispose();
			g.setFont(NebulaDesign.fontSemiBold(15f));
			g.setColor(NebulaDesign.TEXT_PRIMARY);
			g.drawString("Simulator", left + 46, top + 22);
			g.setFont(NebulaDesign.fontMedium(11f));
			g.setColor(NebulaDesign.TEXT_TERTIARY);
			g.drawString("Data · model · evaluation", left + 46, top + 38);
			int trackX = left + 210;
			g.setFont(NebulaDesign.fontMedium(12.5f));
			FontMetrics metrics = g.getFontMetrics();
			int[] widths = new int[keys.length];
			int total = 0;
			for (int index = 0; index < keys.length; index++)
			{
				widths[index] = metrics.stringWidth(labels[index]) + 44 + (steps[index] != null ? 20 : 0);
				total += widths[index];
			}
			int available = getWidth() - trackX - 12;
			boolean compact = total + 8 > available;
			if (compact)
			{
				total = 0;
				for (int index = 0; index < keys.length; index++)
				{
					widths[index] = metrics.stringWidth(labels[index]) + 26;
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
				int cursor = x + 13;
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
				if (steps[index] != null && !compact)
				{
					int badgeX = cursor + g.getFontMetrics().stringWidth(labels[index]) + 7;
					boolean complete = done(index);
					Color fill = selected ? NebulaDesign.ACCENT
							: complete ? NebulaDesign.SUCCESS : NodeLabUi.alpha(NebulaDesign.TEXT_TERTIARY, 90);
					g.setColor(fill);
					g.fill(new Ellipse2D.Float(badgeX, centre - 8, 16, 16));
					g.setColor(selected || complete || NodeLabUi.night() ? Color.WHITE : NebulaDesign.SURFACE);
					if (!selected && !complete && NodeLabUi.night())
					{
						g.setColor(NebulaDesign.TEXT_PRIMARY);
					}
					if (complete && !selected)
					{
						Graphics2D check = (Graphics2D)g.create();
						check.translate(badgeX + 3, centre - 5);
						NodeLabUi.paintGlyph(check, NodeLabUi.Glyph.CHECK, 10);
						check.dispose();
					}
					else
					{
						g.setFont(NebulaDesign.fontSemiBold(10f));
						FontMetrics small = g.getFontMetrics();
						g.drawString(steps[index], badgeX + 8 - small.stringWidth(steps[index]) / 2f, centre + 4);
					}
				}
				x += width;
			}
			g.dispose();
		}
	}
}
