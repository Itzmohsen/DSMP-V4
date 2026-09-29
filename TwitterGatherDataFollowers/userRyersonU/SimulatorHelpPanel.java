package TwitterGatherDataFollowers.userRyersonU;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.RadialGradientPaint;
import java.awt.Shape;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;

final class SimulatorHelpPanel extends JPanel
{
	// Nebula token aliases. Hue names are kept because they encode meaning
	// throughout this guide: BLUE = context/data, PURPLE = model input,
	// AMBER = model stage, GREEN = ranked output / healthy, RED = blocked.
	private static final Color SURFACE = NebulaDesign.SURFACE;
	private static final Color SURFACE_ALT = NebulaDesign.SURFACE_ALT;
	private static final Color BORDER = NebulaDesign.BORDER_SUBTLE;
	private static final Color LINE_STRONG = NebulaDesign.BORDER_STRONG;
	private static final Color TEXT = NebulaDesign.TEXT_PRIMARY;
	private static final Color MUTED = NebulaDesign.TEXT_SECONDARY;
	private static final Color FAINT = NebulaDesign.TEXT_TERTIARY;
	private static final Color BLUE = NebulaDesign.ACCENT;
	private static final Color GREEN = NebulaDesign.SUCCESS;
	private static final Color PURPLE = NebulaDesign.PURPLE;
	private static final Color AMBER = NebulaDesign.WARNING;
	private static final Color RED = NebulaDesign.DANGER;
	private static final Color TRACK = NebulaDesign.INSET;
	private static final int RADIUS = NebulaDesign.RADIUS_CONTROL;
	private static final int PREVIEW_ROW_LIMIT = 5;
	private static final int SAMPLE_LIMIT = 5000;

	private final ControllerAgentGui owner;
	private final JLabel eyebrowLabel;
	private final JLabel titleLabel;
	private final JLabel subtitleLabel;
	private final JLabel datasetPill;
	private final JLabel algorithmPill;
	private final JLabel targetPill;
	private final JLabel nodePill;
	private final RunReadinessPanel readinessPanel;
	private final DataShapePanel dataShapePanel;
	private final ExecutionShapePanel executionShapePanel;
	private final NodeMergePanel nodeMergePanel;
	private final PredictionDemoPanel predictionPanel;
	private final SettingsStripPanel settingsPanel;
	private final SignalHeaderPanel signalHeaderPanel;
	private Snapshot snapshot;
	private DatasetPreview preview;
	private String previewPath;
	private int previewRequest;

	SimulatorHelpPanel(ControllerAgentGui owner)
	{
		this.owner = owner;
		this.snapshot = Snapshot.empty();
		this.preview = DatasetPreview.empty("Choose a text dataset to see live rows.");
		setLayout(new BorderLayout(12, 12));
		setBorder(new EmptyBorder(14, 16, 14, 16));
		setOpaque(true);
		setBackground(NebulaDesign.CANVAS_TOP);

		eyebrowLabel = new JLabel("SIMULATOR RUN GUIDE");
		eyebrowLabel.setFont(NebulaDesign.fontSection());
		eyebrowLabel.setForeground(BLUE);
		titleLabel = new JLabel("Simulator Help");
		titleLabel.setFont(NebulaDesign.fontSemiBold(23f));
		titleLabel.setForeground(TEXT);
		subtitleLabel = new JLabel(
				"Live visual guide to the current setup. Nothing runs from this tab.");
		subtitleLabel.setFont(NebulaDesign.fontBody(12f));
		subtitleLabel.setForeground(MUTED);
		datasetPill = pill("Dataset", BLUE);
		algorithmPill = pill("Algorithm", PURPLE);
		targetPill = pill("Output", GREEN);
		nodePill = pill("Nodes", AMBER);
		signalHeaderPanel = new SignalHeaderPanel();
		readinessPanel = new RunReadinessPanel();
		dataShapePanel = new DataShapePanel();
		executionShapePanel = new ExecutionShapePanel();
		nodeMergePanel = new NodeMergePanel();
		predictionPanel = new PredictionDemoPanel();
		settingsPanel = new SettingsStripPanel();

		add(createHeader(), BorderLayout.NORTH);
		add(createDashboard(), BorderLayout.CENTER);

		Timer timer = new Timer(24, new ActionListener()
		{
			public void actionPerformed(ActionEvent event)
			{
				if (!isShowing())
				{
					return;
				}
				signalHeaderPanel.advance();
				nodeMergePanel.advance();
				predictionPanel.advance();
			}
		});
		timer.start();
		refreshFromSimulator();
	}

	protected void paintComponent(Graphics graphics)
	{
		Graphics2D g = (Graphics2D) graphics.create();
		NebulaDesign.prepare(g);
		g.setPaint(new GradientPaint(
				0, 0, NebulaDesign.CANVAS_TOP,
				0, Math.max(1, getHeight()), NebulaDesign.CANVAS_BOTTOM));
		g.fillRect(0, 0, getWidth(), getHeight());
		g.dispose();
	}

	void refreshFromSimulator()
	{
		Snapshot next = owner == null
				? Snapshot.empty()
				: owner.buildSimulatorHelpSnapshot();
		snapshot = next;
		updateLabels();
		maybeLoadPreview(next);
		updateVisualPanels();
	}

	private JPanel createHeader()
	{
		JPanel header = new HeroPanel(new BorderLayout(18, 0));
		header.setPreferredSize(new Dimension(900, 124));
		header.setBorder(new EmptyBorder(14, 18, 12, 18));

		JPanel copy = new JPanel();
		copy.setOpaque(false);
		copy.setLayout(new BoxLayout(copy, BoxLayout.Y_AXIS));
		copy.setPreferredSize(new Dimension(460, 96));
		eyebrowLabel.setAlignmentX(LEFT_ALIGNMENT);
		titleLabel.setAlignmentX(LEFT_ALIGNMENT);
		subtitleLabel.setAlignmentX(LEFT_ALIGNMENT);
		copy.add(eyebrowLabel);
		copy.add(Box.createVerticalStrut(3));
		copy.add(titleLabel);
		copy.add(Box.createVerticalStrut(3));
		copy.add(subtitleLabel);

		JPanel pills = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
		pills.setOpaque(false);
		pills.setAlignmentX(LEFT_ALIGNMENT);
		pills.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
		pills.add(datasetPill);
		pills.add(algorithmPill);
		pills.add(targetPill);
		pills.add(nodePill);
		copy.add(Box.createVerticalStrut(8));
		copy.add(pills);

		signalHeaderPanel.setPreferredSize(new Dimension(760, 92));
		header.add(copy, BorderLayout.WEST);
		header.add(signalHeaderPanel, BorderLayout.CENTER);
		return header;
	}

	private JPanel createDashboard()
	{
		JPanel dashboard = new JPanel(new GridBagLayout());
		dashboard.setOpaque(false);

		GridBagConstraints c = new GridBagConstraints();
		c.fill = GridBagConstraints.BOTH;
		c.insets = new java.awt.Insets(0, 0, 14, 14);
		c.weighty = 0.46;

		c.gridx = 0;
		c.gridy = 0;
		c.weightx = 0.28;
		dashboard.add(card("Run Readiness", readinessPanel), c);

		c.gridx = 1;
		c.weightx = 0.40;
		dashboard.add(card("Data Shape", dataShapePanel), c);

		c.gridx = 2;
		c.weightx = 0.32;
		c.insets = new java.awt.Insets(0, 0, 14, 0);
		dashboard.add(card("Prediction Meaning", predictionPanel), c);

		c.gridy = 1;
		c.insets = new java.awt.Insets(0, 0, 0, 14);
		c.weighty = 0.54;
		c.gridx = 0;
		c.weightx = 0.40;
		dashboard.add(card("Execution Shape", executionShapePanel), c);

		c.gridx = 1;
		c.weightx = 0.32;
		dashboard.add(card("Nodes And Merge", nodeMergePanel), c);

		c.gridx = 2;
		c.weightx = 0.28;
		c.insets = new java.awt.Insets(0, 0, 0, 0);
		dashboard.add(card("Run Contract", settingsPanel), c);
		return dashboard;
	}

	private JPanel card(String title, JPanel body)
	{
		JPanel panel = new RoundedPanel(new BorderLayout(0, 8));
		panel.setBorder(new EmptyBorder(14, 16, 16, 16));
		JLabel label = new JLabel(title);
		label.setFont(NebulaDesign.fontSemiBold(15f));
		label.setForeground(TEXT);
		panel.add(label, BorderLayout.NORTH);
		panel.add(body, BorderLayout.CENTER);
		return panel;
	}

	private void updateLabels()
	{
		titleLabel.setText(snapshot.algorithmName);
		datasetPill.setText(compact(datasetFamily(snapshot), 20));
		datasetPill.setToolTipText(snapshot.datasetSource + ": " + snapshot.datasetName);
		algorithmPill.setText(compact(snapshot.algorithmName, 20));
		algorithmPill.setToolTipText(snapshot.algorithmName + " | "
				+ snapshot.parameterProfile);
		targetPill.setText(snapshot.candidateTypeLabel());
		targetPill.setToolTipText("Predicts: " + snapshot.candidateTypeLabel());
		nodePill.setText(snapshot.nodes + " node"
				+ (snapshot.nodes == 1 ? "" : "s")
				+ " / top " + snapshot.topRecommendations);
		nodePill.setToolTipText(snapshot.nodes + " node"
				+ (snapshot.nodes == 1 ? "" : "s")
				+ " | top " + snapshot.topRecommendations);
	}

	private void updateVisualPanels()
	{
		readinessPanel.setState(snapshot, preview);
		dataShapePanel.setState(snapshot, preview);
		executionShapePanel.setState(snapshot, preview);
		nodeMergePanel.setState(snapshot, preview);
		predictionPanel.setState(snapshot, preview);
		settingsPanel.setState(snapshot, preview);
		signalHeaderPanel.setState(snapshot, preview);
		revalidate();
		repaint();
	}

	private void maybeLoadPreview(final Snapshot next)
	{
		final String path = next.datasetPath == null ? "" : next.datasetPath;
		if (path.length() == 0)
		{
			previewPath = "";
			preview = DatasetPreview.empty(
					next.datasetSource.equals("Database")
							? "Database mode selected. Load users first; rows are queried by the simulator."
							: "Choose a DSMP text dataset to see a live six-column snapshot.");
			updateVisualPanels();
			return;
		}
		if (path.equals(previewPath) && preview != null)
		{
			return;
		}
		previewPath = path;
		preview = DatasetPreview.loading(path);
		updateVisualPanels();
		final int request = ++previewRequest;
		Thread loader = new Thread(new Runnable()
		{
			public void run()
			{
				final DatasetPreview loaded = loadPreview(path, next.primaryTargetName());
				SwingUtilities.invokeLater(new Runnable()
				{
					public void run()
					{
						if (request == previewRequest && path.equals(previewPath))
						{
							preview = loaded;
							updateVisualPanels();
						}
					}
				});
			}
		}, "Simulator-Help-Dataset-Preview");
		loader.setDaemon(true);
		loader.start();
	}

	private static DatasetPreview loadPreview(String path, String selectedTarget)
	{
		File file = new File(path);
		if (!file.isFile())
		{
			return DatasetPreview.empty("Dataset file could not be found.");
		}
		List<DsmpRow> sampledRows = new ArrayList<DsmpRow>();
		List<DsmpRow> targetRows = new ArrayList<DsmpRow>();
		List<DsmpRow> fallbackRows = new ArrayList<DsmpRow>();
		Set<String> followees = new LinkedHashSet<String>();
		Set<String> users = new LinkedHashSet<String>();
		Map<String, Integer> terms = new LinkedHashMap<String, Integer>();
		int totalRows = 0;
		int validRows = 0;
		int malformedRows = 0;
		String fallbackTarget = "";
		try
		{
			BufferedReader reader = new BufferedReader(new InputStreamReader(
					new FileInputStream(file),
					"UTF-8"));
			try
			{
				String line;
				while ((line = reader.readLine()) != null)
				{
					totalRows++;
					DsmpRow row = parseDsmpRow(line);
					if (row == null)
					{
						malformedRows++;
						continue;
					}
					validRows++;
					if (sampledRows.size() < PREVIEW_ROW_LIMIT)
					{
						sampledRows.add(row);
					}
					else if (validRows % 997 == 0)
					{
						sampledRows.set((validRows / 997) % PREVIEW_ROW_LIMIT, row);
					}
					if (fallbackTarget.length() == 0)
					{
						fallbackTarget = row.userName;
					}
					if (fallbackTarget.equals(row.userName)
							&& fallbackRows.size() < 3)
					{
						fallbackRows.add(row);
					}
					if (selectedTarget != null
							&& selectedTarget.length() > 0
							&& selectedTarget.equals(row.userName)
							&& targetRows.size() < 3)
					{
						targetRows.add(row);
					}
					if (followees.size() < 400)
					{
						followees.add(row.followee);
					}
					if (users.size() < 800)
					{
						users.add(row.userName);
					}
					if (validRows <= SAMPLE_LIMIT
							|| (!targetRows.isEmpty()
							&& selectedTarget.equals(row.userName)))
					{
						addTerms(terms, row.text);
					}
				}
			}
			finally
			{
				reader.close();
			}
		}
		catch (IOException exception)
		{
			return DatasetPreview.empty("Could not read dataset: "
					+ exception.getMessage());
		}
		String target = selectedTarget != null && selectedTarget.length() > 0
				? selectedTarget : fallbackTarget;
		List<DsmpRow> activeRows = targetRows.isEmpty() ? fallbackRows : targetRows;
		return new DatasetPreview(
				file.getName(),
				file.length(),
				totalRows,
				validRows,
				malformedRows,
				sampledRows,
				activeRows,
				new ArrayList<String>(followees),
				new ArrayList<String>(users),
				topTerms(terms, 8),
				target,
				validRows == 0
						? "No valid six-column DSMP rows were found."
						: "Live DSMP snapshot loaded from the selected file.",
				false);
	}

	private static DsmpRow parseDsmpRow(String line)
	{
		if (line == null || line.trim().length() == 0)
		{
			return null;
		}
		String[] parts = line.split("\t", 6);
		if (parts.length < 6)
		{
			parts = line.split(",", 6);
		}
		if (parts.length < 6)
		{
			return null;
		}
		return new DsmpRow(
				parts[0].trim(),
				parts[1].trim(),
				parts[2].trim(),
				parts[3].trim(),
				parts[4].trim(),
				parts[5].trim());
	}

	private static void addTerms(Map<String, Integer> terms, String text)
	{
		if (text == null)
		{
			return;
		}
		String[] tokens = text.toLowerCase(Locale.US).split("[^a-z0-9]+");
		for (int i = 0; i < tokens.length; i++)
		{
			String token = tokens[i];
			if (token.length() < 4 || token.length() > 22)
			{
				continue;
			}
			Integer count = terms.get(token);
			terms.put(token, count == null ? 1 : count + 1);
		}
	}

	private static List<String> topTerms(Map<String, Integer> terms, int limit)
	{
		List<String> result = new ArrayList<String>();
		for (int i = 0; i < limit; i++)
		{
			String best = null;
			int bestCount = -1;
			for (Map.Entry<String, Integer> entry : terms.entrySet())
			{
				if (result.contains(entry.getKey()))
				{
					continue;
				}
				if (entry.getValue() > bestCount)
				{
					best = entry.getKey();
					bestCount = entry.getValue();
				}
			}
			if (best == null)
			{
				break;
			}
			result.add(best);
		}
		return result;
	}

	private static JLabel pill(String text, Color color)
	{
		PillLabel label = new PillLabel(text, color);
		return label;
	}

	private static final class PillLabel extends JLabel
	{
		private final Color hue;

		PillLabel(String text, Color hue)
		{
			super(text, SwingConstants.CENTER);
			this.hue = hue;
			setOpaque(false);
			setFont(NebulaDesign.fontMedium(11f));
			setForeground(hue);
			setBorder(new EmptyBorder(4, 11, 4, 11));
		}

		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D) graphics.create();
			NebulaDesign.prepare(g);
			int h = getHeight();
			g.setColor(softFill(hue));
			g.fillRoundRect(0, 0, getWidth() - 1, h - 1, h, h);
			g.setColor(softStroke(hue));
			g.drawRoundRect(0, 0, getWidth() - 1, h - 1, h, h);
			g.dispose();
			super.paintComponent(graphics);
		}
	}

	private static Color tint(Color color, float amount)
	{
		if (StudioNightTheme.isNightMode())
		{
			float accentAmount = Math.max(0.10f, Math.min(0.42f, 1.0f - amount));
			Color base = amount > 0.70f
					? NebulaDesign.NIGHT_SURFACE
					: NebulaDesign.NIGHT_SURFACE_RAISED;
			return StudioNightTheme.blend(base, color, accentAmount);
		}
		int r = (int) (color.getRed() + (255 - color.getRed()) * amount);
		int g = (int) (color.getGreen() + (255 - color.getGreen()) * amount);
		int b = (int) (color.getBlue() + (255 - color.getBlue()) * amount);
		return new Color(Math.min(255, r), Math.min(255, g), Math.min(255, b));
	}

	/** Soft chip/badge background for a semantic hue (recipe: soft tint + strong hue text). */
	private static Color softFill(Color hue)
	{
		if (StudioNightTheme.isNightMode())
		{
			return alpha(hue, 34);
		}
		return tint(hue, 0.88f);
	}

	/** Border tone paired with softFill. */
	private static Color softStroke(Color hue)
	{
		if (StudioNightTheme.isNightMode())
		{
			return alpha(hue, 92);
		}
		return tint(hue, 0.62f);
	}

	private static Color alpha(Color color, int opacity)
	{
		return new Color(color.getRed(), color.getGreen(), color.getBlue(),
				Math.max(0, Math.min(255, opacity)));
	}

	private static String compact(String value, int max)
	{
		if (value == null)
		{
			return "";
		}
		String clean = value.replace('\n', ' ').replace('\r', ' ').trim();
		return clean.length() <= max ? clean : clean.substring(0, max - 3) + "...";
	}

	private static String formatCount(int value)
	{
		return String.format(Locale.US, "%,d", value);
	}

	private static void drawClippedString(
			Graphics2D g,
			String value,
			int x,
			int y,
			int maxWidth)
	{
		if (value == null)
		{
			return;
		}
		FontMetrics metrics = g.getFontMetrics();
		String text = value;
		if (metrics.stringWidth(text) <= maxWidth)
		{
			g.drawString(text, x, y);
			return;
		}
		while (text.length() > 4 && metrics.stringWidth(text + "...") > maxWidth)
		{
			text = text.substring(0, text.length() - 1);
		}
		g.drawString(text + "...", x, y);
	}

	private static void drawWrapped(
			Graphics2D g,
			String value,
			int x,
			int y,
			int maxWidth,
			int maxLines,
			int lineHeight)
	{
		if (value == null || value.length() == 0)
		{
			return;
		}
		FontMetrics metrics = g.getFontMetrics();
		String[] words = value.split("\\s+");
		StringBuilder line = new StringBuilder();
		int lineNumber = 0;
		for (int i = 0; i < words.length; i++)
		{
			String candidate = line.length() == 0
					? words[i] : line.toString() + " " + words[i];
			if (metrics.stringWidth(candidate) > maxWidth && line.length() > 0)
			{
				g.drawString(line.toString(), x, y + lineNumber * lineHeight);
				lineNumber++;
				line = new StringBuilder(words[i]);
				if (lineNumber >= maxLines)
				{
					return;
				}
			}
			else
			{
				line = new StringBuilder(candidate);
			}
		}
		if (line.length() > 0 && lineNumber < maxLines)
		{
			String text = line.toString();
			if (metrics.stringWidth(text) > maxWidth)
			{
				drawClippedString(g, text, x, y + lineNumber * lineHeight, maxWidth);
			}
			else
			{
				g.drawString(text, x, y + lineNumber * lineHeight);
			}
		}
	}

	/** Uppercase micro-eyebrow with a gentle letterspacing feel. */
	private static void drawEyebrow(
			Graphics2D g,
			String text,
			int x,
			int baseline,
			Color color,
			int maxWidth)
	{
		if (text == null || text.length() == 0)
		{
			return;
		}
		g.setFont(NebulaDesign.fontSemiBold(9.5f));
		g.setColor(color);
		String upper = text.toUpperCase(Locale.US);
		FontMetrics metrics = g.getFontMetrics();
		int cursor = x;
		for (int i = 0; i < upper.length(); i++)
		{
			String letter = String.valueOf(upper.charAt(i));
			int letterWidth = metrics.stringWidth(letter);
			if (cursor + letterWidth > x + maxWidth)
			{
				break;
			}
			g.drawString(letter, cursor, baseline);
			cursor += letterWidth + 1;
		}
	}

	/** Joins step phrases with a middle dot for one-line pipeline rows. */
	private static String joinSteps(String[] steps)
	{
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < steps.length; i++)
		{
			if (i > 0)
			{
				out.append("  ·  ");
			}
			out.append(steps[i]);
		}
		return out.toString();
	}

	private static abstract class VisualPanel extends JPanel
	{
		Snapshot snapshot = Snapshot.empty();
		DatasetPreview preview = DatasetPreview.empty("");
		float phase;

		VisualPanel()
		{
			setOpaque(false);
		}

		void setState(Snapshot snapshot, DatasetPreview preview)
		{
			this.snapshot = snapshot == null ? Snapshot.empty() : snapshot;
			this.preview = preview == null ? DatasetPreview.empty("") : preview;
			repaint();
		}

		void advance()
		{
			phase += 0.026f;
			repaint();
		}

		void prepare(Graphics2D g)
		{
			NebulaDesign.prepare(g);
			g.setFont(NebulaDesign.fontBody(11f));
		}
	}

	private static final class RoundedPanel extends JPanel
	{
		RoundedPanel(BorderLayout layout)
		{
			super(layout);
			setOpaque(false);
		}

		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D) graphics.create();
			NebulaWidgets.paintCard(g, 0, 0, getWidth(), getHeight(),
					NebulaDesign.RADIUS_CARD);
			g.dispose();
		}
	}

	/** Hero card with the shared Nebula aurora wash. */
	private static final class HeroPanel extends JPanel
	{
		HeroPanel(BorderLayout layout)
		{
			super(layout);
			setOpaque(false);
		}

		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D) graphics.create();
			NebulaDesign.prepare(g);
			int w = getWidth();
			int h = getHeight();
			NebulaWidgets.paintCard(g, 0, 0, w, h, NebulaDesign.RADIUS_CARD);
			Shape oldClip = g.getClip();
			g.clip(new RoundRectangle2D.Double(0, 0, w - 1, h - 3,
					NebulaDesign.RADIUS_CARD, NebulaDesign.RADIUS_CARD));
			boolean nightMode = StudioNightTheme.isNightMode();
			int glowA = nightMode ? 26 : 15;
			int glowB = nightMode ? 22 : 12;
			g.setPaint(new RadialGradientPaint(
					new Point2D.Float(w * 0.18f, 0f),
					Math.max(1f, w * 0.45f),
					new float[] {0f, 1f},
					new Color[] {alpha(BLUE, glowA), alpha(BLUE, 0)}));
			g.fillRect(0, 0, w, h);
			g.setPaint(new RadialGradientPaint(
					new Point2D.Float(w * 0.74f, h),
					Math.max(1f, w * 0.5f),
					new float[] {0f, 1f},
					new Color[] {alpha(PURPLE, glowB), alpha(PURPLE, 0)}));
			g.fillRect(0, 0, w, h);
			g.setClip(oldClip);
			g.dispose();
		}
	}

	private static final class SignalHeaderPanel extends VisualPanel
	{
		protected void paintComponent(Graphics graphics)
		{
			super.paintComponent(graphics);
			Graphics2D g = (Graphics2D) graphics.create();
			prepare(g);
			int w = getWidth();
			int h = getHeight();
			int pad = 10;
			int boxH = Math.min(60, Math.max(48, h - 32));
			int boxY = 6;
			int laneY = boxY + boxH / 2;
			String target = preview.targetName.length() > 0
					? preview.targetName
					: snapshot.primaryTargetName().length() > 0
							? snapshot.primaryTargetName()
							: "selected target";
			String rows = preview.validRows > 0
					? formatCount(preview.validRows) + " rows"
					: snapshot.datasetSource;
			String[] titles = {
					datasetFamily(snapshot),
					"Target",
					"Text input",
					"Model",
					"Ranked output"
			};
			String[] values = {
					compact(snapshot.datasetName, 24) + " / " + rows,
					compact(target, 26),
					compact(inputMeaning(snapshot), 34),
					snapshot.algorithmName,
					outputColumnName(snapshot)
			};
			Color[] colors = {BLUE, BLUE, PURPLE, AMBER, GREEN};
			int boxW = Math.max(112, (w - pad * 2 - 60) / 5);
			int gap = Math.max(8, (w - pad * 2 - boxW * 5) / 4);
			int x = pad;
			for (int i = 0; i < 5; i++)
			{
				drawIntentBox(g, x, boxY, boxW, boxH, titles[i], values[i], colors[i]);
				if (i < 4)
				{
					int x1 = x + boxW + 3;
					int x2 = x + boxW + gap - 3;
					g.setStroke(new BasicStroke(1.6f));
					g.setColor(LINE_STRONG);
					g.drawLine(x1, laneY, x2, laneY);
					double t = (phase + i * 0.18) % 1.0;
					int px = (int) (x1 + (x2 - x1) * t);
					g.setColor(colors[i + 1]);
					g.fill(new Ellipse2D.Double(px - 3, laneY - 3, 6, 6));
				}
				x += boxW + gap;
			}
			g.setFont(NebulaDesign.fontBody(10.5f));
			g.setColor(FAINT);
			drawClippedString(g,
					"This is a preview of meaning and flow, not a model run.",
					pad,
					h - 6,
					w - pad * 2);
			g.dispose();
		}

		private void drawIntentBox(Graphics2D g, int x, int y, int w, int h,
				String title, String value, Color color)
		{
			g.setColor(SURFACE_ALT);
			g.fillRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setColor(BORDER);
			g.drawRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setColor(color);
			g.fillOval(x + 10, y + 10, 7, 7);
			drawEyebrow(g, title, x + 23, y + 17, color, w - 32);
			g.setFont(NebulaDesign.fontMedium(11f));
			g.setColor(TEXT);
			drawWrapped(g, value, x + 10, y + 33, w - 20, 2, 13);
		}
	}

	private static final class RunReadinessPanel extends VisualPanel
	{
		protected void paintComponent(Graphics graphics)
		{
			super.paintComponent(graphics);
			Graphics2D g = (Graphics2D) graphics.create();
			prepare(g);
			int w = getWidth();
			int h = getHeight();
			if (w < 120 || h < 60)
			{
				g.dispose();
				return;
			}

			int score = readinessScore();
			Color color = readinessColor(score);
			int heroH = Math.min(108, Math.max(76, (int) (h * 0.30f)));
			heroH = Math.min(heroH, h);
			drawReadinessHero(g, 0, 0, w, heroH, score, color);

			int y = heroH + 10;
			int metricH = 40;
			int gap = 6;
			int metricW = Math.max(1, (w - gap) / 2);
			if (h - y >= metricH)
			{
				drawReadinessMetric(g, 0, y, metricW, metricH,
						"rows", preview.loading ? "..." : formatCount(preview.validRows),
						"usable DSMP rows", BLUE);
				drawReadinessMetric(g, metricW + gap, y, metricW, metricH,
						"targets", snapshot.batchEnabled
								? formatCount(snapshot.batchUsers)
								: targetCountLabel(),
						snapshot.batchEnabled ? "batch users" : "selected user", PURPLE);
				y += metricH + gap;
			}
			if (h - y >= metricH)
			{
				drawReadinessMetric(g, 0, y, metricW, metricH,
						"outputs", outputShortLabel(snapshot),
						outputColumnName(snapshot), GREEN);
				drawReadinessMetric(g, metricW + gap, y, metricW, metricH,
						"workers", formatCount(snapshot.nodes),
						"parallel node lanes", AMBER);
				y += metricH + gap;
			}
			int checksH = Math.min(104, h - y - 4);
			if (checksH >= 66)
			{
				drawChecklist(g, 0, y + 4, w, checksH);
			}
			g.dispose();
		}

		private void drawReadinessHero(
				Graphics2D g,
				int x,
				int y,
				int w,
				int h,
				int score,
				Color color)
		{
			g.setPaint(new GradientPaint(
					x,
					y,
					SURFACE_ALT,
					x + w,
					y + h,
					softFill(color)));
			g.fillRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setColor(BORDER);
			g.drawRoundRect(x, y, w, h, RADIUS, RADIUS);

			int ring = Math.max(58, Math.min(84, h - 26));
			int ringX = x + 16;
			int ringY = y + (h - ring) / 2;
			g.setStroke(new BasicStroke(8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			g.setColor(alpha(color, StudioNightTheme.isNightMode() ? 46 : 34));
			g.draw(new Arc2D.Double(ringX, ringY, ring, ring, 90, -360, Arc2D.OPEN));
			g.setColor(color);
			g.draw(new Arc2D.Double(ringX, ringY, ring, ring, 90,
					-360.0 * (score / 100.0), Arc2D.OPEN));
			g.setFont(NebulaDesign.fontSemiBold(23f));
			g.setColor(TEXT);
			String scoreText = Integer.toString(score);
			FontMetrics metrics = g.getFontMetrics();
			g.drawString(scoreText,
					ringX + ring / 2 - metrics.stringWidth(scoreText) / 2,
					ringY + ring / 2 + 8);

			int copyX = ringX + ring + 18;
			int copyW = w - (copyX - x) - 14;
			drawEyebrow(g, "Run readiness", copyX, y + 21, color, copyW);
			g.setFont(NebulaDesign.fontSemiBold(15f));
			g.setColor(TEXT);
			drawClippedString(g, readinessHeadline(score), copyX, y + 41, copyW);
			g.setFont(NebulaDesign.fontBody(11f));
			g.setColor(MUTED);
			drawWrapped(g, readinessSentence(), copyX, y + 58, copyW, 2, 14);
		}

		private void drawReadinessMetric(
				Graphics2D g,
				int x,
				int y,
				int w,
				int h,
				String label,
				String value,
				String detail,
				Color color)
		{
			g.setColor(SURFACE_ALT);
			g.fillRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setColor(BORDER);
			g.drawRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setColor(color);
			g.fillOval(x + 10, y + 8, 7, 7);
			drawEyebrow(g, label, x + 22, y + 15, color, w - 32);
			g.setFont(NebulaDesign.fontSemiBold(13f));
			g.setColor(TEXT);
			drawClippedString(g, value, x + 10, y + 27, w - 20);
			g.setFont(NebulaDesign.fontBody(9f));
			g.setColor(FAINT);
			drawClippedString(g, detail, x + 10, y + h - 3, w - 20);
		}

		private void drawChecklist(Graphics2D g, int x, int y, int w, int h)
		{
			g.setColor(SURFACE_ALT);
			g.fillRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setColor(BORDER);
			g.drawRoundRect(x, y, w, h, RADIUS, RADIUS);
			drawEyebrow(g, "What is safe to expect", x + 12, y + 16, FAINT, w - 24);
			int rowY = y + 23;
			int rowH = Math.max(12, Math.min(24, (h - 30) / 3));
			drawCheckRow(g, x + 12, rowY, w - 24, rowH,
					"dataset",
					preview.validRows > 0 ? "loaded" : "waiting",
					preview.validRows > 0 ? GREEN : AMBER);
			drawCheckRow(g, x + 12, rowY + rowH + 3, w - 24, rowH,
					"algorithm",
					snapshot.workingAlgorithm ? "supported" : "not active",
					snapshot.workingAlgorithm ? GREEN : RED);
			drawCheckRow(g, x + 12, rowY + (rowH + 3) * 2, w - 24, rowH,
					"side effects",
					snapshot.graphEnabled ? "graph display on" : "edges retained",
					snapshot.graphEnabled ? GREEN : AMBER);
		}

		private void drawCheckRow(
				Graphics2D g,
				int x,
				int y,
				int w,
				int h,
				String label,
				String value,
				Color color)
		{
			g.setColor(color);
			g.fillOval(x + 1, y + h / 2 - 3, 7, 7);
			g.setFont(NebulaDesign.fontMedium(11f));
			g.setColor(TEXT);
			drawClippedString(g, label, x + 15, y + h / 2 + 4, w / 2 - 20);
			g.setColor(color);
			drawClippedString(g, value, x + w / 2 + 4, y + h / 2 + 4, w / 2 - 8);
		}

		private int readinessScore()
		{
			int score = 100;
			if (preview.loading)
			{
				score -= 12;
			}
			if (preview.validRows == 0)
			{
				score -= 28;
			}
			if (preview.malformedRows > 0)
			{
				score -= 8;
			}
			if (!snapshot.workingAlgorithm)
			{
				score -= 35;
			}
			if (snapshot.outputType == null)
			{
				score -= 24;
			}
			if (!snapshot.batchEnabled && snapshot.batchUsers > 1)
			{
				score -= 8;
			}
			if (snapshot.primaryTargetName().length() == 0 && !snapshot.batchEnabled)
			{
				score -= 5;
			}
			return Math.max(0, Math.min(100, score));
		}

		private Color readinessColor(int score)
		{
			if (score >= 88)
			{
				return GREEN;
			}
			if (score >= 68)
			{
				return AMBER;
			}
			return RED;
		}

		private String readinessHeadline(int score)
		{
			if (score >= 88)
			{
				return "This setup is understandable";
			}
			if (score >= 68)
			{
				return "Readable with warnings";
			}
			return "Needs one more setup step";
		}

		private String readinessSentence()
		{
			if (preview.validRows == 0)
			{
				return "Choose or inspect a text dataset so the guide can show real rows and examples.";
			}
			return snapshot.algorithmName + " will read " + inputMeaning(snapshot)
					+ " and rank " + outputMeaning(snapshot) + ".";
		}

		private String targetCountLabel()
		{
			return snapshot.primaryTargetName().length() == 0 ? "none" : "1";
		}
	}

	private static final class DataShapePanel extends VisualPanel
	{
		protected void paintComponent(Graphics graphics)
		{
			super.paintComponent(graphics);
			Graphics2D g = (Graphics2D) graphics.create();
			prepare(g);
			int w = getWidth();
			int h = getHeight();
			if (w < 160 || h < 60)
			{
				g.dispose();
				return;
			}

			int metricH = 42;
			int metricGap = 8;
			int metricW = Math.max(1, (w - metricGap * 3) / 4);
			drawSmallMetric(g, 0, 0, metricW, metricH,
					"rows", preview.loading ? "..." : formatCount(preview.validRows),
					BLUE);
			drawSmallMetric(g, metricW + metricGap, 0, metricW, metricH,
					"users", preview.loading ? "..." : formatCount(preview.users.size()),
					PURPLE);
			drawSmallMetric(g, (metricW + metricGap) * 2, 0, metricW, metricH,
					"labels", preview.loading ? "..." : formatCount(preview.followees.size()),
					GREEN);
			drawSmallMetric(g, (metricW + metricGap) * 3, 0, metricW, metricH,
					"text terms", preview.loading ? "..." : formatCount(preview.terms.size()),
					AMBER);

			int mapY = metricH + 10;
			boolean sample = h - mapY >= 200;
			int sampleH = sample ? 52 : 0;
			int mapH = h - mapY - (sample ? sampleH + 10 : 0);
			if (mapH >= 70)
			{
				drawDataMap(g, 0, mapY, w, mapH);
			}
			if (sample)
			{
				drawSampleStrip(g, 0, mapY + mapH + 10, w, sampleH);
			}
			g.dispose();
		}

		private void drawSmallMetric(
				Graphics2D g,
				int x,
				int y,
				int w,
				int h,
				String label,
				String value,
				Color color)
		{
			g.setColor(SURFACE_ALT);
			g.fillRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setColor(BORDER);
			g.drawRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setColor(color);
			g.fillOval(x + 10, y + 9, 7, 7);
			drawEyebrow(g, label, x + 22, y + 16, color, w - 32);
			g.setFont(NebulaDesign.fontSemiBold(15f));
			g.setColor(TEXT);
			drawClippedString(g, value, x + 10, y + h - 10, w - 20);
		}

		private void drawDataMap(Graphics2D g, int x, int y, int w, int h)
		{
			g.setColor(SURFACE_ALT);
			g.fillRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setColor(BORDER);
			g.drawRoundRect(x, y, w, h, RADIUS, RADIUS);

			drawEyebrow(g, "DSMP six-column meaning", x + 14, y + 19, FAINT, w - 28);
			g.setFont(NebulaDesign.fontBody(10.5f));
			g.setColor(MUTED);
			drawClippedString(g,
					"Current settings decide the model input column and the ranked side.",
					x + 14,
					y + 36,
					w - 28);

			int contentTop = y + 46;
			int contentBottom = y + h - 10;
			int nodeH = Math.min(88, Math.max(60, (h - 116) / 2));
			nodeH = Math.min(nodeH, Math.max(1, contentBottom - contentTop));
			boolean chipsFit = contentTop + nodeH + 12 + 40 <= y + h - 6;
			int nodeTop = chipsFit
					? contentTop
					: contentTop + Math.max(0, (contentBottom - contentTop - nodeH) / 2);
			if (nodeTop + nodeH > y + h - 6 || nodeH < 44)
			{
				return;
			}
			int gap = 14;
			int nodeW = Math.max(84, (w - 32 - gap * 2) / 3);
			int leftX = x + 14;
			int centerX = leftX + nodeW + gap;
			int rightX = centerX + nodeW + gap;
			int cy = nodeTop + nodeH / 2;
			drawNode(g, leftX, nodeTop, nodeW, nodeH,
					"Input",
					"column 6",
					inputMeaningShort(snapshot),
					PURPLE);
			drawNode(g, centerX, nodeTop, nodeW, nodeH,
					"Algorithm",
					snapshot.algorithmName,
					featureSummary(snapshot),
					AMBER);
			drawNode(g, rightX, nodeTop, nodeW, nodeH,
					"Output",
					outputShortLabel(snapshot),
					outputColumnShort(snapshot),
					GREEN);
			drawFlow(g, leftX + nodeW, cy, centerX, cy, BLUE, 0.00f);
			drawFlow(g, centerX + nodeW, cy, rightX, cy, GREEN, 0.31f);

			int contextY = nodeTop + nodeH + 12;
			if (chipsFit)
			{
				int contextW = Math.max(1, (w - 52) / 3);
				drawContextChip(g, x + 14, contextY, contextW,
						"Column 1", columnOneRole(snapshot), GREEN);
				drawContextChip(g, x + 26 + contextW, contextY, contextW,
						"Column 5", columnFiveRole(snapshot), BLUE);
				drawContextChip(g, x + 38 + contextW * 2, contextY, contextW,
						"Date", "filter window context", AMBER);
				int meaningY = contextY + 50;
				if (meaningY + 34 <= y + h - 8)
				{
					drawMeaningBand(g, x + 14, meaningY, w - 28,
							Math.min(40, y + h - meaningY - 8));
				}
			}
		}

		private void drawNode(
				Graphics2D g,
				int x,
				int y,
				int w,
				int h,
				String label,
				String main,
				String detail,
				Color color)
		{
			g.setColor(SURFACE);
			g.fillRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setColor(softStroke(color));
			g.drawRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setColor(color);
			g.fillOval(x + 10, y + 11, 7, 7);
			drawEyebrow(g, label, x + 22, y + 18, color, w - 30);
			g.setFont(NebulaDesign.fontSemiBold(13f));
			g.setColor(TEXT);
			drawClippedString(g, main, x + 10, y + 40, w - 20);
			if (h >= 62)
			{
				g.setFont(NebulaDesign.fontBody(10f));
				g.setColor(MUTED);
				drawClippedString(g, detail, x + 10, y + 57, w - 20);
			}
		}

		private void drawFlow(
				Graphics2D g,
				int x1,
				int y1,
				int x2,
				int y2,
				Color color,
				float offset)
		{
			g.setStroke(new BasicStroke(1.6f));
			g.setColor(LINE_STRONG);
			g.drawLine(x1 + 6, y1, x2 - 6, y2);
			double t = (phase + offset) % 1.0;
			int px = (int) (x1 + 6 + (x2 - x1 - 12) * t);
			g.setColor(alpha(color, 165));
			g.fill(new Ellipse2D.Double(px - 3.5, y1 - 3.5, 7, 7));
		}

		private void drawContextChip(
				Graphics2D g,
				int x,
				int y,
				int w,
				String label,
				String value,
				Color color)
		{
			g.setColor(SURFACE);
			g.fillRoundRect(x, y, w, 40, RADIUS, RADIUS);
			g.setColor(BORDER);
			g.drawRoundRect(x, y, w, 40, RADIUS, RADIUS);
			g.setColor(color);
			g.fillOval(x + 10, y + 9, 6, 6);
			drawEyebrow(g, label, x + 21, y + 15, color, w - 30);
			g.setFont(NebulaDesign.fontMedium(10.5f));
			g.setColor(TEXT);
			drawClippedString(g, value, x + 10, y + 31, w - 20);
		}

		private void drawMeaningBand(Graphics2D g, int x, int y, int w, int h)
		{
			Color color = snapshot.outputType == RecommendationCandidateType.USER
					? BLUE : GREEN;
			g.setColor(SURFACE);
			g.fillRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setColor(BORDER);
			g.drawRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setColor(softFill(color));
			g.fillRoundRect(x + 8, y + 7, 72, h - 14, 999, 999);
			drawEyebrow(g, "Meaning", x + 20, y + h / 2 + 3, color, 56);
			g.setFont(NebulaDesign.fontMedium(11f));
			g.setColor(TEXT);
			drawClippedString(g,
					"Read " + inputMeaning(snapshot) + " -> rank "
							+ outputMeaning(snapshot),
					x + 92,
					y + h / 2 + 4,
					w - 104);
		}

		private void drawSampleStrip(Graphics2D g, int x, int y, int w, int h)
		{
			if (h < 42)
			{
				return;
			}
			g.setColor(SURFACE);
			g.fillRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setColor(BORDER);
			g.drawRoundRect(x, y, w, h, RADIUS, RADIUS);
			DsmpRow row = !preview.targetRows.isEmpty()
					? preview.targetRows.get(0)
					: preview.rows.isEmpty() ? null : preview.rows.get(0);
			drawEyebrow(g, "Live example row", x + 12, y + 16, BLUE, w - 24);
			if (row == null)
			{
				g.setFont(NebulaDesign.fontBody(10.5f));
				g.setColor(MUTED);
				drawClippedString(g, preview.message, x + 12, y + 34, w - 24);
				return;
			}
			int chipY = y + 22;
			int chipH = Math.max(24, h - 30);
			int gap = 8;
			int chipW = Math.max(1, (w - 24 - gap * 2) / 3);
			drawSampleCell(g, x + 12, chipY, chipW, chipH,
					"column 1", row.followee, GREEN);
			drawSampleCell(g, x + 12 + chipW + gap, chipY, chipW, chipH,
					"column 5", row.userName, BLUE);
			drawSampleCell(g, x + 12 + (chipW + gap) * 2, chipY, chipW, chipH,
					"column 6", row.text, PURPLE);
		}

		private void drawSampleCell(Graphics2D g, int x, int y, int w, int h,
				String label, String value, Color color)
		{
			g.setColor(SURFACE_ALT);
			g.fillRoundRect(x, y, w, h, NebulaDesign.RADIUS_SMALL, NebulaDesign.RADIUS_SMALL);
			g.setColor(BORDER);
			g.drawRoundRect(x, y, w, h, NebulaDesign.RADIUS_SMALL, NebulaDesign.RADIUS_SMALL);
			drawEyebrow(g, label, x + 9, y + 13, color, w - 18);
			g.setFont(NebulaDesign.fontMono(10.5f));
			g.setColor(TEXT);
			drawClippedString(g, value, x + 9, y + h - 8, w - 18);
		}
	}

	private static final class ExecutionShapePanel extends VisualPanel
	{
		protected void paintComponent(Graphics graphics)
		{
			super.paintComponent(graphics);
			Graphics2D g = (Graphics2D) graphics.create();
			prepare(g);
			int w = getWidth();
			int h = getHeight();
			if (w < 160 || h < 60)
			{
				g.dispose();
				return;
			}

			int questionH = 44;
			drawQuestion(g, 0, 0, w, questionH);

			int y = questionH + 10;
			boolean workbench = h - y >= 170;
			if (workbench)
			{
				int workH = 78;
				drawWorkbench(g, 0, y, w, workH);
				y += workH + 10;
			}
			int laneH = h - y;
			if (laneH >= 70)
			{
				drawExecutionLane(g, 0, y, w, laneH);
			}
			g.dispose();
		}

		private void drawQuestion(Graphics2D g, int x, int y, int w, int h)
		{
			g.setColor(SURFACE_ALT);
			g.fillRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setColor(BORDER);
			g.drawRoundRect(x, y, w, h, RADIUS, RADIUS);
			int labelW = 84;
			g.setColor(softFill(GREEN));
			g.fillRoundRect(x + 10, y + 9, labelW, h - 18, 999, 999);
			drawEyebrow(g, "Question", x + 24, y + h / 2 + 3, GREEN, labelW - 20);
			g.setFont(NebulaDesign.fontMedium(12f));
			g.setColor(TEXT);
			drawClippedString(g, beginnerQuestion(snapshot),
					x + labelW + 22,
					y + h / 2 + 4,
					w - labelW - 34);
		}

		private void drawWorkbench(Graphics2D g, int x, int y, int w, int h)
		{
			g.setColor(SURFACE_ALT);
			g.fillRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setColor(BORDER);
			g.drawRoundRect(x, y, w, h, RADIUS, RADIUS);
			drawEyebrow(g, "Algorithm workbench", x + 12, y + 17, PURPLE, w - 24);
			g.setFont(NebulaDesign.fontBody(11f));
			g.setColor(MUTED);
			drawClippedString(g, snapshot.algorithmName + " builds "
					+ featureSummary(snapshot) + " from cleaned column-6 text.",
					x + 12,
					y + 34,
					w - 24);

			String[] steps = algorithmWorkbenchSteps(snapshot);
			int moduleW = Math.max(82, (w - 54) / 3);
			int moduleY = y + h - 32;
			for (int i = 0; i < 3; i++)
			{
				int moduleX = x + 12 + i * (moduleW + 15);
				Color color = i == 0 ? BLUE : i == 1 ? PURPLE : GREEN;
				g.setColor(SURFACE);
				g.fillRoundRect(moduleX, moduleY, moduleW, 24, 999, 999);
				g.setColor(softStroke(color));
				g.drawRoundRect(moduleX, moduleY, moduleW, 24, 999, 999);
				g.setColor(color);
				g.fillOval(moduleX + 10, moduleY + 9, 6, 6);
				g.setFont(NebulaDesign.fontMedium(10.5f));
				g.setColor(TEXT);
				drawClippedString(g, steps[i], moduleX + 21, moduleY + 16, moduleW - 30);
				if (i < 2)
				{
					int ax = moduleX + moduleW + 3;
					int ay = moduleY + 12;
					g.setColor(LINE_STRONG);
					g.setStroke(new BasicStroke(1.6f));
					g.drawLine(ax, ay, ax + 9, ay);
					g.drawLine(ax + 5, ay - 4, ax + 9, ay);
					g.drawLine(ax + 5, ay + 4, ax + 9, ay);
				}
			}
		}

		private void drawExecutionLane(Graphics2D g, int x, int y, int w, int h)
		{
			drawEyebrow(g, "Execution lane", x + 2, y + 12, BLUE, 110);
			g.setFont(NebulaDesign.fontBody(10.5f));
			g.setColor(FAINT);
			drawClippedString(g,
					"Runs in this order after Initialize and Run Simulation.",
					x + 116,
					y + 12,
					w - 120);

			int filters = (snapshot.removeHashTags ? 1 : 0)
					+ (snapshot.removeRetweets ? 1 : 0)
					+ (snapshot.removeStopWords ? 1 : 0);
			String[] title = {
					"Read",
					"Clean",
					algorithmStage(snapshot),
					"Score",
					"Merge"
			};
			String[] detail = {
					preview.validRows > 0
							? formatCount(preview.validRows) + " rows"
							: snapshot.datasetSource,
					filters + " of 3 filters on",
					featureSummary(snapshot),
					snapshot.nodes + " node" + (snapshot.nodes == 1 ? "" : "s"),
					"top " + snapshot.topRecommendations
			};
			Color[] colors = {BLUE, BLUE, PURPLE, AMBER, GREEN};
			int listY = y + 20;
			int rowH = Math.max(17, Math.min(38, (h - 20) / 5));
			boolean twoLine = rowH >= 27;
			for (int i = 0; i < 5; i++)
			{
				int rowY = listY + i * rowH;
				if (rowY + rowH > y + h + 4)
				{
					break;
				}
				if (i > 0)
				{
					g.setColor(BORDER);
					g.fillRect(x + 2, rowY, w - 4, 1);
				}
				int badge = rowH >= 24 ? 18 : 14;
				int badgeY = rowY + rowH / 2 - badge / 2;
				g.setColor(softFill(colors[i]));
				g.fillRoundRect(x + 2, badgeY, badge, badge, 999, 999);
				g.setColor(softStroke(colors[i]));
				g.drawRoundRect(x + 2, badgeY, badge, badge, 999, 999);
				g.setFont(NebulaDesign.fontSemiBold(10f));
				g.setColor(colors[i]);
				String number = Integer.toString(i + 1);
				FontMetrics metrics = g.getFontMetrics();
				g.drawString(number,
						x + 2 + badge / 2 - metrics.stringWidth(number) / 2,
						badgeY + badge / 2 + 4);
				int textX = x + 2 + badge + 10;
				int titleBaseline = twoLine ? rowY + rowH / 2 - 2 : rowY + rowH / 2 + 4;
				g.setFont(NebulaDesign.fontMono(10f));
				int detailW = g.getFontMetrics().stringWidth(detail[i]);
				g.setColor(MUTED);
				g.drawString(detail[i], x + w - 4 - detailW, titleBaseline);
				g.setFont(NebulaDesign.fontMedium(11.5f));
				g.setColor(TEXT);
				drawClippedString(g, title[i], textX, titleBaseline,
						w - (textX - x) - detailW - 16);
				if (twoLine)
				{
					g.setFont(NebulaDesign.fontBody(9.5f));
					g.setColor(FAINT);
					drawClippedString(g, joinSteps(stageSteps(i, snapshot)),
							textX, rowY + rowH / 2 + 12, w - (textX - x) - 6);
				}
			}
		}
	}

	private static final class NodeMergePanel extends VisualPanel
	{
		protected void paintComponent(Graphics graphics)
		{
			super.paintComponent(graphics);
			Graphics2D g = (Graphics2D) graphics.create();
			prepare(g);
			int w = getWidth();
			int h = getHeight();
			if (w < 160 || h < 60)
			{
				g.dispose();
				return;
			}
			int count = Math.min(snapshot.nodes, 10);
			if (count == 0)
			{
				count = 1;
			}
			drawEyebrow(g, "Parallel node lanes", 0, 12, BLUE, w);
			g.setFont(NebulaDesign.fontBody(10.5f));
			g.setColor(MUTED);
			drawClippedString(g,
					"Nodes work in parallel, then the organizer combines their candidate-score maps.",
					0,
					28,
					w);
			int recipeY = 38;
			int recipeH = 54;
			drawMergeRecipe(g, 0, recipeY, w, recipeH);
			int diagramY = recipeY + recipeH + 10;
			int diagramH = h - diagramY;
			if (diagramH < 96)
			{
				if (diagramH >= 24)
				{
					g.setFont(NebulaDesign.fontBody(10.5f));
					g.setColor(FAINT);
					drawClippedString(g,
							"merge keeps the highest ranked candidates for the target",
							0, diagramY + 16, w);
				}
				g.dispose();
				return;
			}
			drawWorkerFrame(g, 0, diagramY, w, diagramH);
			int hubR = Math.min(44, Math.max(30, diagramH / 2 - 22));
			int hubX = w - hubR - 22;
			int hubY = diagramY + 40 + (diagramH - 46) / 2;
			int nodeAreaX = 14;
			int nodeAreaY = diagramY + 52;
			int nodeAreaW = Math.max(110, hubX - hubR - nodeAreaX - 26);
			int nodeAreaH = Math.max(60, diagramH - 64);
			int rows = count == 1 ? 1 : count <= 4 ? 1 : count <= 8 ? 2 : 3;
			int cols = (int) Math.ceil(count / (double) rows);
			int gap = 10;
			int cardW = count == 1
					? Math.min(180, Math.max(126, nodeAreaW / 2))
					: Math.max(68, Math.min(106,
							(nodeAreaW - gap * Math.max(0, cols - 1)) / Math.max(1, cols)));
			int cardH = count == 1
					? Math.min(92, Math.max(64, nodeAreaH - 10))
					: Math.max(42, Math.min(58,
							(nodeAreaH - gap * Math.max(0, rows - 1)) / Math.max(1, rows)));
			int gridW = count == 1
					? cardW
					: cols * cardW + gap * Math.max(0, cols - 1);
			int gridH = rows * cardH + gap * Math.max(0, rows - 1);
			int startX = count == 1 ? nodeAreaX + 12 : nodeAreaX + Math.max(0, (nodeAreaW - gridW) / 2);
			int startY = nodeAreaY + Math.max(0, (nodeAreaH - gridH) / 2);
			for (int i = 0; i < count; i++)
			{
				int row = rows == 1 ? 0 : i / cols;
				int col = rows == 1 ? i : i % cols;
				int x = startX + col * (cardW + gap);
				int y = startY + row * (cardH + gap);
				Color color = i % 2 == 0 ? BLUE : GREEN;
				g.setColor(alpha(color, 60));
				g.setStroke(new BasicStroke(1.4f));
				g.drawLine(x + cardW, y + cardH / 2, hubX - hubR - 2, hubY);
				double t = (phase + i * 0.11) % 1.0;
				int px = (int) (x + cardW + (hubX - hubR - 2 - x - cardW) * t);
				int py = (int) (y + cardH / 2 + (hubY - y - cardH / 2) * t);
				g.setColor(alpha(color, 165));
				g.fill(new Ellipse2D.Double(px - 3, py - 3, 6, 6));
				drawNodeCard(g, x, y, cardW, cardH, i, color, count == 1);
			}
			drawMergeHub(g, hubX, hubY, hubR);
			if (snapshot.nodes > count)
			{
				g.setFont(NebulaDesign.fontBody(10f));
				g.setColor(FAINT);
				drawClippedString(g,
						"+" + (snapshot.nodes - count) + " more nodes represented",
						12, diagramY + diagramH - 10, nodeAreaW);
			}
			g.dispose();
		}

		private void drawMergeRecipe(Graphics2D g, int x, int y, int w, int h)
		{
			String[] labels = {
					"1 receive",
					"2 score",
					"3 send map",
					"4 merge + sort"
			};
			String[] details = {
					"node shard",
					featureSummary(snapshot),
					"candidate -> score",
					"top " + snapshot.topRecommendations
			};
			Color[] colors = {BLUE, PURPLE, AMBER, GREEN};
			int gap = 8;
			int boxW = Math.max(74, (w - gap * 3) / 4);
			for (int i = 0; i < 4; i++)
			{
				int bx = x + i * (boxW + gap);
				g.setColor(SURFACE_ALT);
				g.fillRoundRect(bx, y, boxW, h, RADIUS, RADIUS);
				g.setColor(BORDER);
				g.drawRoundRect(bx, y, boxW, h, RADIUS, RADIUS);
				g.setColor(colors[i]);
				g.fillOval(bx + 9, y + 11, 6, 6);
				g.setFont(NebulaDesign.fontMedium(10.5f));
				g.setColor(TEXT);
				drawClippedString(g, labels[i], bx + 20, y + 19, boxW - 28);
				g.setFont(NebulaDesign.fontBody(9.5f));
				g.setColor(MUTED);
				drawClippedString(g, details[i], bx + 9, y + 37, boxW - 18);
				if (i < 3)
				{
					int ax = bx + boxW + 1;
					int ay = y + h / 2;
					g.setColor(LINE_STRONG);
					g.setStroke(new BasicStroke(1.4f));
					g.drawLine(ax, ay, ax + gap - 2, ay);
				}
			}
		}

		private void drawWorkerFrame(Graphics2D g, int x, int y, int w, int h)
		{
			if (w <= 30 || h <= 30)
			{
				return;
			}
			g.setColor(TRACK);
			g.fillRoundRect(x, y, w, h, NebulaDesign.RADIUS_CONSOLE, NebulaDesign.RADIUS_CONSOLE);
			g.setColor(BORDER);
			g.drawRoundRect(x, y, w, h, NebulaDesign.RADIUS_CONSOLE, NebulaDesign.RADIUS_CONSOLE);
			drawEyebrow(g, snapshot.nodes == 1
					? "Single node score map"
					: "Worker node outputs", x + 12, y + 17, FAINT, w - 24);
			g.setFont(NebulaDesign.fontBody(10f));
			g.setColor(MUTED);
			drawWrapped(g,
					snapshot.nodes == 1
							? "One recommender builds one candidate-score map, then the organizer still applies the same top-result merge step."
							: "Each recommender returns a candidate-score map; the organizer deduplicates and sorts them.",
					x + 12,
					y + 33,
					w - 24,
					2,
					13);
		}

		private void drawNodeCard(
				Graphics2D g,
				int x,
				int y,
				int w,
				int h,
				int index,
				Color color,
				boolean single)
		{
			g.setColor(SURFACE);
			g.fillRoundRect(x, y, w, h, NebulaDesign.RADIUS_SMALL, NebulaDesign.RADIUS_SMALL);
			g.setColor(softStroke(color));
			g.drawRoundRect(x, y, w, h, NebulaDesign.RADIUS_SMALL, NebulaDesign.RADIUS_SMALL);
			g.setColor(color);
			g.fillOval(x + 8, y + 8, 6, 6);
			g.setFont(NebulaDesign.fontMedium(10.5f));
			g.setColor(TEXT);
			drawClippedString(g, "Node " + (index + 1), x + 19, y + 15, w - 28);
			g.setFont(NebulaDesign.fontBody(9.5f));
			g.setColor(MUTED);
			drawClippedString(g, single
					? "full target workload"
					: index == 0 ? "target + shard" : "candidate shard",
					x + 8,
					y + 29,
					w - 16);
			if (h >= 58)
			{
				g.setFont(NebulaDesign.fontBody(9f));
				g.setColor(FAINT);
				drawClippedString(g, internalAlgorithmMeaning(snapshot),
						x + 8,
						y + 42,
						w - 16);
			}
			g.setColor(StudioNightTheme.isNightMode()
					? NebulaDesign.NIGHT_INSET
					: NebulaDesign.DAY_INSET);
			g.fillRoundRect(x + 8, y + h - 10, w - 16, 4, 4, 4);
			g.setColor(alpha(color, 180));
			int progress = Math.max(10, (int) ((w - 16) * (single ? 0.72 : 0.44 + (index % 4) * 0.12)));
			g.fillRoundRect(x + 8, y + h - 10, progress, 4, 4, 4);
		}

		private void drawMergeHub(Graphics2D g, int hubX, int hubY, int r)
		{
			g.setColor(SURFACE);
			g.fill(new Ellipse2D.Double(hubX - r, hubY - r, r * 2, r * 2));
			g.setColor(softStroke(GREEN));
			g.setStroke(new BasicStroke(2f));
			g.draw(new Ellipse2D.Double(hubX - r, hubY - r, r * 2, r * 2));
			g.setColor(alpha(GREEN, StudioNightTheme.isNightMode() ? 30 : 22));
			g.fill(new Ellipse2D.Double(hubX - r + 7, hubY - r + 7, r * 2 - 14, r * 2 - 14));
			g.setFont(NebulaDesign.fontSemiBold(11.5f));
			g.setColor(TEXT);
			FontMetrics metrics = g.getFontMetrics();
			g.drawString("Merge", hubX - metrics.stringWidth("Merge") / 2, hubY - 8);
			g.setFont(NebulaDesign.fontMedium(9f));
			g.setColor(GREEN);
			String mid = "dedupe + sort";
			g.drawString(mid, hubX - g.getFontMetrics().stringWidth(mid) / 2, hubY + 6);
			String top = "top " + snapshot.topRecommendations;
			g.drawString(top, hubX - g.getFontMetrics().stringWidth(top) / 2, hubY + 19);
		}
	}

	private static final class PredictionDemoPanel extends VisualPanel
	{
		protected void paintComponent(Graphics graphics)
		{
			super.paintComponent(graphics);
			Graphics2D g = (Graphics2D) graphics.create();
			prepare(g);
			int w = getWidth();
			int h = getHeight();
			if (w < 140 || h < 50)
			{
				g.dispose();
				return;
			}
			String target = preview.targetName.length() > 0
					? preview.targetName
					: snapshot.primaryTargetName();
			if (target.length() == 0)
			{
				target = "Selected target";
			}
			int y = 0;
			int targetH = 52;
			drawTarget(g, 0, y, w, targetH, target);
			y += targetH + 8;
			if (h - y >= 74)
			{
				drawInputTokens(g, 0, y, w, 78);
				y += 78 + 8;
			}
			if (h - y >= 60)
			{
				drawPredictionMeaning(g, 0, y, w, 40);
				y += 40 + 8;
			}
			int footH = 14;
			int listH = h - y - footH;
			List<String> candidates = preview.candidatesFor(snapshot.outputType, target);
			if (listH >= 40)
			{
				if (candidates.isEmpty())
				{
					drawEmptyPrediction(g, 0, y, w, listH);
				}
				else
				{
					int visible = Math.max(1, Math.min(4, listH / 44));
					visible = Math.min(visible, candidates.size());
					int rowH = listH / visible;
					for (int i = 0; i < visible; i++)
					{
						drawCandidate(g, 0, y + i * rowH, w, rowH - 6,
								i + 1,
								candidates.get(i),
								0.88f - i * 0.14f);
					}
				}
			}
			g.setFont(NebulaDesign.fontBody(9.5f));
			g.setColor(FAINT);
			drawClippedString(g,
					"Candidates are sampled from the dataset; real scores come from Run Simulation.",
					0,
					h - 3,
					w);
			g.dispose();
		}

		private void drawTarget(Graphics2D g, int x, int y, int w, int h, String target)
		{
			g.setColor(SURFACE_ALT);
			g.fillRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setColor(BORDER);
			g.drawRoundRect(x, y, w, h, RADIUS, RADIUS);
			int avatar = h - 18;
			g.setColor(BLUE);
			g.fill(new Ellipse2D.Double(x + 11, y + 9, avatar, avatar));
			g.setFont(NebulaDesign.fontSemiBold(13f));
			g.setColor(NebulaDesign.TEXT_ON_ACCENT);
			String initials = target.length() >= 2
					? target.substring(0, 2).toUpperCase(Locale.US) : "T";
			FontMetrics metrics = g.getFontMetrics();
			g.drawString(initials,
					x + 11 + avatar / 2 - metrics.stringWidth(initials) / 2,
					y + 9 + avatar / 2 + 5);
			int copyX = x + 11 + avatar + 12;
			drawEyebrow(g, "Target user", copyX, y + 20, FAINT, w - (copyX - x) - 12);
			g.setFont(NebulaDesign.fontSemiBold(14f));
			g.setColor(TEXT);
			drawClippedString(g, target, copyX, y + 40, w - (copyX - x) - 12);
		}

		private void drawInputTokens(Graphics2D g, int x, int y, int w, int h)
		{
			g.setColor(SURFACE_ALT);
			g.fillRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setColor(BORDER);
			g.drawRoundRect(x, y, w, h, RADIUS, RADIUS);
			drawEyebrow(g, "What the model reads: column 6 text", x + 11, y + 16, PURPLE, w - 22);
			String text = exampleText();
			g.setFont(NebulaDesign.fontMono(10.5f));
			g.setColor(TEXT);
			drawClippedString(g, text.length() == 0
							? "No selected-target row loaded yet."
							: compact(text, 90),
					x + 11,
					y + 34,
					w - 22);
			List<String> terms = tokensFromText(text);
			if (terms.isEmpty())
			{
				terms = preview.terms;
			}
			int chipX = x + 11;
			int chipY = y + 46;
			g.setFont(NebulaDesign.fontMono(10f));
			for (int i = 0; i < Math.min(6, terms.size()); i++)
			{
				String term = terms.get(i);
				int chipW = Math.min(104, Math.max(38, g.getFontMetrics().stringWidth(term) + 18));
				if (chipX + chipW > x + w - 8)
				{
					break;
				}
				g.setColor(softFill(PURPLE));
				g.fillRoundRect(chipX, chipY, chipW, 21, 999, 999);
				g.setColor(softStroke(PURPLE));
				g.drawRoundRect(chipX, chipY, chipW, 21, 999, 999);
				g.setColor(PURPLE);
				drawClippedString(g, term, chipX + 9, chipY + 15, chipW - 18);
				chipX += chipW + 6;
			}
			if (terms.isEmpty())
			{
				g.setColor(MUTED);
				g.setFont(NebulaDesign.fontBody(10.5f));
				drawClippedString(g, "No text tokens loaded yet.", x + 11, y + 62, w - 22);
			}
		}

		private String exampleText()
		{
			if (!preview.targetRows.isEmpty())
			{
				return preview.targetRows.get(0).text;
			}
			if (!preview.rows.isEmpty())
			{
				return preview.rows.get(0).text;
			}
			return "";
		}

		private List<String> tokensFromText(String text)
		{
			List<String> result = new ArrayList<String>();
			if (text == null)
			{
				return result;
			}
			String[] tokens = text.toLowerCase(Locale.US).split("[^a-z0-9]+");
			for (int i = 0; i < tokens.length && result.size() < 6; i++)
			{
				if (tokens[i].length() >= 4 && !result.contains(tokens[i]))
				{
					result.add(tokens[i]);
				}
			}
			return result;
		}

		private void drawPredictionMeaning(Graphics2D g, int x, int y, int w, int h)
		{
			Color color = snapshot.outputType == RecommendationCandidateType.FOLLOWEE
					? GREEN : BLUE;
			g.setColor(SURFACE);
			g.fillRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setColor(softStroke(color));
			g.drawRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setColor(color);
			g.fillOval(x + 11, y + 9, 6, 6);
			drawEyebrow(g, "What will be ranked", x + 22, y + 15, color, w - 32);
			g.setFont(NebulaDesign.fontMedium(11f));
			g.setColor(TEXT);
			drawClippedString(g, outputMeaning(snapshot), x + 11, y + 32, w - 22);
		}

		private void drawCandidate(Graphics2D g, int x, int y, int w, int h,
				int rank, String name, float strength)
		{
			Color color = rank == 1 ? GREEN : rank == 2 ? PURPLE : BLUE;
			g.setColor(SURFACE_ALT);
			g.fillRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setColor(BORDER);
			g.drawRoundRect(x, y, w, h, RADIUS, RADIUS);
			int badge = Math.min(24, h - 14);
			g.setColor(softFill(color));
			g.fillRoundRect(x + 9, y + (h - badge) / 2, badge, badge, 8, 8);
			g.setFont(NebulaDesign.fontSemiBold(11f));
			g.setColor(color);
			String rankText = Integer.toString(rank);
			FontMetrics metrics = g.getFontMetrics();
			g.drawString(rankText,
					x + 9 + badge / 2 - metrics.stringWidth(rankText) / 2,
					y + h / 2 + 4);
			int copyX = x + 9 + badge + 10;
			g.setFont(NebulaDesign.fontSemiBold(12f));
			g.setColor(TEXT);
			drawClippedString(g, name, copyX, y + 18, w - (copyX - x) - 12);
			if (h >= 38)
			{
				g.setFont(NebulaDesign.fontBody(9.5f));
				g.setColor(FAINT);
				drawClippedString(g, snapshot.outputType == RecommendationCandidateType.FOLLOWEE
						? "real label from column 1" : "real user from column 5",
						copyX, y + 31, w - (copyX - x) - 12);
			}
			int barW = Math.max(10, (int) ((w - (copyX - x) - 14) * strength));
			g.setColor(TRACK);
			g.fillRoundRect(copyX, y + h - 9, w - (copyX - x) - 12, 4, 4, 4);
			g.setColor(alpha(color, 185));
			g.fillRoundRect(copyX, y + h - 9, barW, 4, 4, 4);
		}

		private void drawEmptyPrediction(Graphics2D g, int x, int y, int w, int h)
		{
			g.setColor(SURFACE_ALT);
			g.fillRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setColor(softStroke(AMBER));
			g.drawRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setFont(NebulaDesign.fontSemiBold(12f));
			g.setColor(AMBER);
			drawClippedString(g, "Prediction preview waiting for dataset rows",
					x + 13, y + 24, w - 26);
			g.setFont(NebulaDesign.fontBody(10.5f));
			g.setColor(MUTED);
			drawWrapped(g, preview.message, x + 13, y + 43, w - 26,
					Math.max(1, (h - 48) / 15), 15);
		}
	}

	private static final class SettingsStripPanel extends VisualPanel
	{
		protected void paintComponent(Graphics graphics)
		{
			super.paintComponent(graphics);
			Graphics2D g = (Graphics2D) graphics.create();
			prepare(g);
			int w = getWidth();
			int h = getHeight();
			if (w < 120 || h < 50)
			{
				g.dispose();
				return;
			}
			g.setFont(NebulaDesign.fontBody(10.5f));
			g.setColor(MUTED);
			drawClippedString(g,
					"Copied live from the Simulator tab. Controls there redraw this guide.",
					0,
					11,
					w);
			String[] labels = {
					snapshot.parameterProfile,
					snapshot.batchEnabled ? "Batch " + snapshot.batchUsers : "Single target",
					snapshot.graphEnabled ? "Graph export on" : "Graph export off",
					snapshot.heapMb + " MB heap"
			};
			Color[] colors = {PURPLE, BLUE, snapshot.graphEnabled ? GREEN : AMBER, GREEN};
			int chipH = 32;
			int gap = 8;
			int y = 20;
			for (int i = 0; i < labels.length; i++)
			{
				int x = i % 2 == 0 ? 0 : w / 2 + gap / 2;
				if (i == 2)
				{
					y += chipH + gap;
				}
				int chipW = w / 2 - gap / 2;
				if (y + chipH > h)
				{
					break;
				}
				g.setColor(SURFACE_ALT);
				g.fillRoundRect(x, y, chipW, chipH, RADIUS, RADIUS);
				g.setColor(BORDER);
				g.drawRoundRect(x, y, chipW, chipH, RADIUS, RADIUS);
				g.setColor(colors[i]);
				g.fillOval(x + 10, y + chipH / 2 - 3, 7, 7);
				g.setFont(NebulaDesign.fontMedium(11f));
				g.setColor(TEXT);
				drawClippedString(g, labels[i], x + 23, y + chipH / 2 + 4, chipW - 32);
			}
			y += chipH + 14;
			int lineH = 24;
			String[][] lines = {
					{"Input", inputMeaning(snapshot)},
					{"Output", outputMeaning(snapshot)},
					{"Processing", cleaningSummary(snapshot)},
					{"Graph", snapshot.graphEnabled
							? "edge data plus graph display/export"
							: "edge data retained, graph display skipped"},
					{"Meaning", beginnerQuestion(snapshot)},
					{"Capacity", snapshot.capacitySummary}
			};
			Color[] lineColors = {
					PURPLE,
					GREEN,
					BLUE,
					snapshot.graphEnabled ? GREEN : AMBER,
					PURPLE,
					AMBER
			};
			int footerReserve = 14;
			for (int i = 0; i < lines.length; i++)
			{
				if (y + lineH > h - footerReserve)
				{
					break;
				}
				drawContractLine(g, 0, y, w, lines[i][0], lines[i][1], lineColors[i]);
				if (i < lines.length - 1)
				{
					g.setColor(BORDER);
					g.fillRect(0, y + lineH - 3, w, 1);
				}
				y += lineH;
			}
			if (h - y >= 10)
			{
				g.setFont(NebulaDesign.fontMono(9f));
				g.setColor(FAINT);
				drawClippedString(g, compact(snapshot.parameterSummary, 96),
						0,
						h - 3,
						w);
			}
			g.dispose();
		}

		private void drawContractLine(Graphics2D g, int x, int y, int w,
				String label, String value, Color color)
		{
			g.setColor(color);
			g.fillOval(x + 1, y + 6, 6, 6);
			drawEyebrow(g, label, x + 13, y + 12, FAINT, 80);
			g.setFont(NebulaDesign.fontBody(10.5f));
			g.setColor(TEXT);
			drawClippedString(g, value, x + 96, y + 13, w - 100);
		}
	}

	private static String cleaningSummary(Snapshot s)
	{
		StringBuilder out = new StringBuilder();
		out.append(s.removeHashTags ? "hashtags removed" : "hashtags kept");
		out.append(", ");
		out.append(s.removeRetweets ? "RT removed" : "RT kept");
		out.append(", ");
		out.append(s.removeStopWords ? "stop words removed" : "stop words kept");
		return out.toString();
	}

	private static String algorithmStage(Snapshot s)
	{
		switch (s.algorithm)
		{
			case RecommendationModeSupport.SVM:
				return "Weka SMO";
			case RecommendationModeSupport.SCALABLE_SVM:
				return "Passive-aggressive linear";
			case RecommendationModeSupport.TEMPORAL_GRAPH_FOUNDATION:
				return "Temporal graph foundation";
			case RecommendationModeSupport.DOC2VEC:
				return "Doc2Vec vectors";
			case RecommendationModeSupport.K_MEANS:
				return "K-Means";
				case RecommendationModeSupport.K_MEANS_EUCLIDEAN:
					return "Euclidean K-Means";
				case RecommendationModeSupport.COMMON_NEIGHBORS:
					return "Common Neighbors";
				case RecommendationModeSupport.MLP:
					return "Neural model";
			case RecommendationModeSupport.COS_SIM:
				return "Cosine scores";
			default:
				return "Legacy model";
		}
	}

	private static String featureSummary(Snapshot s)
	{
		switch (s.algorithm)
		{
			case RecommendationModeSupport.DOC2VEC:
				return "document vectors";
			case RecommendationModeSupport.TEMPORAL_GRAPH_FOUNDATION:
				return "text graph time signals";
			case RecommendationModeSupport.SVM:
			case RecommendationModeSupport.SCALABLE_SVM:
			case RecommendationModeSupport.MLP:
				return "TF-IDF class features";
				case RecommendationModeSupport.K_MEANS:
				case RecommendationModeSupport.K_MEANS_EUCLIDEAN:
					return "cluster features";
				case RecommendationModeSupport.COMMON_NEIGHBORS:
					return "shared text neighbors";
				case RecommendationModeSupport.COS_SIM:
					return "sparse vectors";
			default:
				return "legacy features";
		}
	}

	private static String datasetFamily(Snapshot s)
	{
		String value = ((s.datasetName == null ? "" : s.datasetName) + " "
				+ (s.datasetPath == null ? "" : s.datasetPath) + " "
				+ (s.datasetSource == null ? "" : s.datasetSource))
				.toLowerCase(Locale.US);
		if (value.indexOf("retail") >= 0
				|| value.indexOf("online") >= 0
				|| value.indexOf("customer") >= 0)
		{
			return "Retail dataset";
		}
		if (value.indexOf("journal") >= 0
				|| value.indexOf("author") >= 0
				|| value.indexOf("paper") >= 0
				|| value.indexOf("title") >= 0)
		{
			return "Journal dataset";
		}
		if (value.indexOf("tweet") >= 0
				|| value.indexOf("twitter") >= 0
				|| value.indexOf("ryerson") >= 0
				|| value.indexOf("social") >= 0)
		{
			return "Twitter dataset";
		}
		if (s.datasetSource != null && s.datasetSource.length() > 0)
		{
			return s.datasetSource;
		}
		return "DSMP dataset";
	}

	private static String inputMeaning(Snapshot s)
	{
		String family = datasetFamily(s);
		if ("Retail dataset".equals(family))
		{
			return "product descriptions in column 6";
		}
		if ("Journal dataset".equals(family))
		{
			return "paper titles or abstracts in column 6";
		}
		if ("Twitter dataset".equals(family))
		{
			return "tweet or post text in column 6";
		}
		return "document text in column 6";
	}

	/** Column-6 flavor without the trailing " in column 6" (tight spots). */
	private static String inputMeaningShort(Snapshot s)
	{
		String value = inputMeaning(s);
		int cut = value.indexOf(" in column 6");
		return cut > 0 ? value.substring(0, cut) : value;
	}

	/** Which column the ranked side comes from, in a few words. */
	private static String outputColumnShort(Snapshot s)
	{
		if (s.outputType == RecommendationCandidateType.FOLLOWEE)
		{
			return "ranked from column 1";
		}
		if (s.outputType == RecommendationCandidateType.USER)
		{
			return "ranked from column 5";
		}
		return "no supported output";
	}

	private static String outputColumnName(Snapshot s)
	{
		if (s.outputType == RecommendationCandidateType.FOLLOWEE)
		{
			String family = datasetFamily(s);
			if ("Retail dataset".equals(family))
			{
				return "column 1 product/store labels";
			}
			if ("Journal dataset".equals(family))
			{
				return "column 1 journal/venue labels";
			}
			if ("Twitter dataset".equals(family))
			{
				return "column 1 followed accounts";
			}
			return "column 1 followee labels";
		}
		if (s.outputType == RecommendationCandidateType.USER)
		{
			String family = datasetFamily(s);
			if ("Retail dataset".equals(family))
			{
				return "column 5 similar customers";
			}
			if ("Journal dataset".equals(family))
			{
				return "column 5 similar authors";
			}
			if ("Twitter dataset".equals(family))
			{
				return "column 5 similar Twitter users";
			}
			return "column 5 users/entities";
		}
		return "no supported output";
	}

	private static String outputShortLabel(Snapshot s)
	{
		if (s.outputType == RecommendationCandidateType.FOLLOWEE)
		{
			String family = datasetFamily(s);
			if ("Retail dataset".equals(family))
			{
				return "products/stores";
			}
			if ("Journal dataset".equals(family))
			{
				return "journals/venues";
			}
			if ("Twitter dataset".equals(family))
			{
				return "followed accounts";
			}
			return "column 1 labels";
		}
		if (s.outputType == RecommendationCandidateType.USER)
		{
			String family = datasetFamily(s);
			if ("Retail dataset".equals(family))
			{
				return "customers";
			}
			if ("Journal dataset".equals(family))
			{
				return "authors";
			}
			if ("Twitter dataset".equals(family))
			{
				return "Twitter users";
			}
			return "users";
		}
		return "unavailable";
	}

	private static String columnOneRole(Snapshot s)
	{
		if (s.outputType == RecommendationCandidateType.FOLLOWEE)
		{
			return "ranked output";
		}
		String family = datasetFamily(s);
		if ("Retail dataset".equals(family))
		{
			return "product/store context";
		}
		if ("Journal dataset".equals(family))
		{
			return "journal/venue context";
		}
		if ("Twitter dataset".equals(family))
		{
			return "followed-account context";
		}
		return "reference context";
	}

	private static String columnFiveRole(Snapshot s)
	{
		if (s.outputType == RecommendationCandidateType.USER)
		{
			return "ranked output";
		}
		String family = datasetFamily(s);
		if ("Retail dataset".equals(family))
		{
			return "customer target";
		}
		if ("Journal dataset".equals(family))
		{
			return "author target";
		}
		if ("Twitter dataset".equals(family))
		{
			return "Twitter user target";
		}
		return "target entity";
	}

	private static String outputMeaning(Snapshot s)
	{
		if (s.outputType == RecommendationCandidateType.FOLLOWEE)
		{
			String family = datasetFamily(s);
			if ("Retail dataset".equals(family))
			{
				return "product/store reference labels from column 1";
			}
			if ("Journal dataset".equals(family))
			{
				return "journal or venue labels from column 1";
			}
			if ("Twitter dataset".equals(family))
			{
				return "accounts the target may follow from column 1";
			}
			return "followee/reference labels from column 1";
		}
		if (s.outputType == RecommendationCandidateType.USER)
		{
			String family = datasetFamily(s);
			if ("Retail dataset".equals(family))
			{
				return "customers with similar purchase text from column 5";
			}
			if ("Journal dataset".equals(family))
			{
				return "authors with similar paper text from column 5";
			}
			if ("Twitter dataset".equals(family))
			{
				return "Twitter users with similar post text from column 5";
			}
			return "similar users/entities from column 5";
		}
		return "no supported recommendation output";
	}

	private static String beginnerQuestion(Snapshot s)
	{
		if (s.outputType == RecommendationCandidateType.FOLLOWEE)
		{
			return "For the selected target, which reference label should be recommended?";
		}
		if (s.outputType == RecommendationCandidateType.USER)
		{
			return "For the selected target, which other users look most similar?";
		}
		return "The selected mode does not expose a supported recommendation output.";
	}

	private static String[] algorithmWorkbenchSteps(Snapshot s)
	{
		switch (s.algorithm)
		{
			case RecommendationModeSupport.COS_SIM:
				return new String[] {
						"TF-IDF vectors",
						"cosine compare",
						"rank nearest"
				};
			case RecommendationModeSupport.K_MEANS:
				return new String[] {
						"text vectors",
						"cluster users",
						"rank same cluster"
				};
				case RecommendationModeSupport.K_MEANS_EUCLIDEAN:
					return new String[] {
							"text vectors",
							"distance clusters",
							"rank closest"
					};
				case RecommendationModeSupport.COMMON_NEIGHBORS:
					return new String[] {
							"user-term graph",
							"shared neighbors",
							"rank overlap"
					};
				case RecommendationModeSupport.SVM:
					return new String[] {
						"TF-IDF ARFF",
						"Weka SMO model",
						"score labels"
				};
			case RecommendationModeSupport.SCALABLE_SVM:
				return new String[] {
						"sparse TF-IDF",
						"linear model",
						"score labels"
				};
			case RecommendationModeSupport.MLP:
				return new String[] {
						"TF-IDF inputs",
						"neural train",
						"score labels"
				};
			case RecommendationModeSupport.DOC2VEC:
				return new String[] {
						"learn vectors",
						"compare vectors",
						"rank users"
				};
			case RecommendationModeSupport.TEMPORAL_GRAPH_FOUNDATION:
				return new String[] {
						"profile users",
						"fuse signals",
						"ablate ranks"
				};
			default:
				return new String[] {
						"build graph",
						"compare links",
						"rank candidates"
				};
		}
	}

	private static String[] stageSteps(int stage, Snapshot s)
	{
		switch (stage)
		{
			case 0:
				return new String[] {
						"parse six DSMP columns",
						"collect target user rows",
						"send records to nodes"
				};
			case 1:
				return new String[] {
						s.removeHashTags ? "remove hashtags" : "keep hashtags",
						s.removeRetweets ? "remove retweets" : "keep retweets",
						s.removeStopWords ? "drop stop words" : "keep stop words"
				};
			case 2:
				return algorithmWorkbenchSteps(s);
			case 3:
				return new String[] {
						"score local candidates",
						"build candidate -> score map",
						"report node result"
				};
			case 4:
				return new String[] {
						"combine duplicate candidates",
						"sort by final score",
						"keep top " + s.topRecommendations
				};
			default:
				return new String[] {"-", "-", "-"};
		}
	}

	private static String internalAlgorithmMeaning(Snapshot s)
	{
		switch (s.algorithm)
		{
			case RecommendationModeSupport.COS_SIM:
				return "TF-IDF cosine similarity";
			case RecommendationModeSupport.K_MEANS:
				return "cluster membership + similarity";
				case RecommendationModeSupport.K_MEANS_EUCLIDEAN:
					return "cluster distance";
				case RecommendationModeSupport.COMMON_NEIGHBORS:
					return "shared nonzero term count";
				case RecommendationModeSupport.SVM:
					return "Weka SMO class scores";
			case RecommendationModeSupport.SCALABLE_SVM:
				return "sparse passive-aggressive margins";
			case RecommendationModeSupport.MLP:
				return "neural class scores";
			case RecommendationModeSupport.DOC2VEC:
				return "document-vector similarity";
			case RecommendationModeSupport.TEMPORAL_GRAPH_FOUNDATION:
				return "fixed-weight text + graph + time score";
			default:
				return "legacy relationship score";
		}
	}

	static final class Snapshot
	{
		final int algorithm;
		final int nodes;
		final int topRecommendations;
		final int batchUsers;
		final int latestTweets;
		final int heapMb;
		final int iteration;
		final String datasetSource;
		final String datasetName;
		final String datasetPath;
		final String algorithmName;
		final RecommendationTargetMode mode;
		final String modeLabel;
		final RecommendationCandidateType outputType;
		final RecommendationCandidateType nativeOutputType;
		final boolean convertedPipeline;
		final boolean workingAlgorithm;
		final boolean batchEnabled;
		final boolean removeHashTags;
		final boolean removeRetweets;
		final boolean removeStopWords;
		final boolean simulateDelay;
		final boolean graphEnabled;
		final String parameterProfile;
		final String parameterSummary;
		final String capacitySummary;
		final String selectedTargets;

		Snapshot(
				int algorithm,
				int nodes,
				int topRecommendations,
				int batchUsers,
				int latestTweets,
				int heapMb,
				int iteration,
				String datasetSource,
				String datasetName,
				String datasetPath,
				String algorithmName,
				RecommendationTargetMode mode,
				String modeLabel,
				RecommendationCandidateType outputType,
				RecommendationCandidateType nativeOutputType,
				boolean convertedPipeline,
				boolean workingAlgorithm,
				boolean batchEnabled,
				boolean removeHashTags,
				boolean removeRetweets,
				boolean removeStopWords,
				boolean simulateDelay,
				boolean graphEnabled,
				String parameterProfile,
				String parameterSummary,
				String capacitySummary,
				String selectedTargets)
		{
			this.algorithm = algorithm;
			this.nodes = Math.max(1, nodes);
			this.topRecommendations = Math.max(1, topRecommendations);
			this.batchUsers = Math.max(1, batchUsers);
			this.latestTweets = Math.max(0, latestTweets);
			this.heapMb = Math.max(0, heapMb);
			this.iteration = Math.max(0, iteration);
			this.datasetSource = datasetSource == null ? "Dataset" : datasetSource;
			this.datasetName = datasetName == null || datasetName.trim().length() == 0
					? "No dataset selected" : datasetName;
			this.datasetPath = datasetPath == null ? "" : datasetPath;
			this.algorithmName = algorithmName == null ? "Unknown" : algorithmName;
			this.mode = mode == null ? RecommendationTargetMode.ALGORITHM_DEFAULT : mode;
			this.modeLabel = modeLabel == null ? "Algorithm Default" : modeLabel;
			this.outputType = outputType;
			this.nativeOutputType = nativeOutputType;
			this.convertedPipeline = convertedPipeline;
			this.workingAlgorithm = workingAlgorithm;
			this.batchEnabled = batchEnabled;
			this.removeHashTags = removeHashTags;
			this.removeRetweets = removeRetweets;
			this.removeStopWords = removeStopWords;
			this.simulateDelay = simulateDelay;
			this.graphEnabled = graphEnabled;
			this.parameterProfile = parameterProfile == null ? "Default" : parameterProfile;
			this.parameterSummary = parameterSummary == null ? "" : parameterSummary;
			this.capacitySummary = capacitySummary == null
					? "Balanced default capacity" : capacitySummary;
			this.selectedTargets = selectedTargets == null ? "NO TARGET SELECTED" : selectedTargets;
		}

		String candidateTypeLabel()
		{
			if (outputType == RecommendationCandidateType.USER)
			{
				return "Similar users";
			}
			if (outputType == RecommendationCandidateType.FOLLOWEE)
			{
				return "Followee labels";
			}
			return "Unavailable";
		}

		String primaryTargetName()
		{
			if (selectedTargets == null)
			{
				return "";
			}
			if (selectedTargets.startsWith("TARGET: "))
			{
				return selectedTargets.substring("TARGET: ".length()).trim();
			}
			if (selectedTargets.indexOf(" FROM ") >= 0)
			{
				return selectedTargets.substring(
						selectedTargets.indexOf(" FROM ") + 6).trim();
			}
			return "";
		}

		static Snapshot empty()
		{
			return new Snapshot(
					RecommendationModeSupport.K_MEANS,
					1,
					3,
					1,
					0,
					0,
					0,
					"Text dataset",
					"No dataset selected",
					"",
					"K-Means",
					RecommendationTargetMode.ALGORITHM_DEFAULT,
					"Algorithm Default",
					RecommendationCandidateType.USER,
					RecommendationCandidateType.USER,
					false,
					true,
					false,
					true,
					true,
					true,
					false,
					true,
					"Default",
					"Default profile: current DSMP behavior is preserved.",
					"Balanced default capacity",
					"NO TARGET SELECTED");
		}
	}

	private static final class DatasetPreview
	{
		final String fileName;
		final long fileBytes;
		final int totalRows;
		final int validRows;
		final int malformedRows;
		final List<DsmpRow> rows;
		final List<DsmpRow> targetRows;
		final List<String> followees;
		final List<String> users;
		final List<String> terms;
		final String targetName;
		final String message;
		final boolean loading;

		DatasetPreview(
				String fileName,
				long fileBytes,
				int totalRows,
				int validRows,
				int malformedRows,
				List<DsmpRow> rows,
				List<DsmpRow> targetRows,
				List<String> followees,
				List<String> users,
				List<String> terms,
				String targetName,
				String message,
				boolean loading)
		{
			this.fileName = fileName == null ? "" : fileName;
			this.fileBytes = Math.max(0L, fileBytes);
			this.totalRows = Math.max(0, totalRows);
			this.validRows = Math.max(0, validRows);
			this.malformedRows = Math.max(0, malformedRows);
			this.rows = rows == null ? new ArrayList<DsmpRow>() : rows;
			this.targetRows = targetRows == null ? new ArrayList<DsmpRow>() : targetRows;
			this.followees = followees == null ? new ArrayList<String>() : followees;
			this.users = users == null ? new ArrayList<String>() : users;
			this.terms = terms == null ? new ArrayList<String>() : terms;
			this.targetName = targetName == null ? "" : targetName;
			this.message = message == null ? "" : message;
			this.loading = loading;
		}

		static DatasetPreview empty(String message)
		{
			return new DatasetPreview(
					"",
					0L,
					0,
					0,
					0,
					new ArrayList<DsmpRow>(),
					new ArrayList<DsmpRow>(),
					new ArrayList<String>(),
					new ArrayList<String>(),
					new ArrayList<String>(),
					"",
					message,
					false);
		}

		static DatasetPreview loading(String path)
		{
			File file = new File(path);
			return new DatasetPreview(
					file.getName(),
					file.length(),
					0,
					0,
					0,
					new ArrayList<DsmpRow>(),
					new ArrayList<DsmpRow>(),
					new ArrayList<String>(),
					new ArrayList<String>(),
					new ArrayList<String>(),
					"",
					"Scanning selected dataset...",
					true);
		}

		List<String> candidatesFor(RecommendationCandidateType type, String target)
		{
			List<String> source = type == RecommendationCandidateType.FOLLOWEE
					? followees : users;
			List<String> result = new ArrayList<String>();
			for (int i = 0; i < source.size() && result.size() < 8; i++)
			{
				String value = source.get(i);
				if (value == null || value.length() == 0)
				{
					continue;
				}
				if (type == RecommendationCandidateType.USER
						&& target != null
						&& target.equals(value))
				{
					continue;
				}
				if (!result.contains(value))
				{
					result.add(value);
				}
			}
			return result;
		}
	}

	private static final class DsmpRow
	{
		final String followee;
		final String postId;
		final String date;
		final String userId;
		final String userName;
		final String text;

		DsmpRow(
				String followee,
				String postId,
				String date,
				String userId,
				String userName,
				String text)
		{
			this.followee = followee == null ? "" : followee;
			this.postId = postId == null ? "" : postId;
			this.date = date == null ? "" : date;
			this.userId = userId == null ? "" : userId;
			this.userName = userName == null ? "" : userName;
			this.text = text == null ? "" : text;
		}
	}
}
