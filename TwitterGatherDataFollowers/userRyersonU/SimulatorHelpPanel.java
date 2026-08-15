package TwitterGatherDataFollowers.userRyersonU;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
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
import javax.swing.BorderFactory;
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
	private static final Color BACKGROUND = StudioNightTheme.adaptiveColor(
			new Color(244, 247, 252), StudioNightTheme.NIGHT_BACKGROUND);
	private static final Color SURFACE = StudioNightTheme.adaptiveColor(
			new Color(255, 255, 255), StudioNightTheme.NIGHT_SURFACE);
	private static final Color SURFACE_ALT = StudioNightTheme.adaptiveColor(
			new Color(248, 251, 255), StudioNightTheme.NIGHT_SURFACE_ALT);
	private static final Color BORDER = StudioNightTheme.adaptiveColor(
			new Color(205, 216, 231), StudioNightTheme.NIGHT_BORDER);
	private static final Color TEXT = StudioNightTheme.adaptiveColor(
			new Color(25, 31, 40), StudioNightTheme.NIGHT_TEXT);
	private static final Color MUTED = StudioNightTheme.adaptiveColor(
			new Color(91, 104, 122), StudioNightTheme.NIGHT_MUTED);
	private static final Color BLUE = StudioNightTheme.adaptiveColor(
			new Color(75, 96, 160), StudioNightTheme.NIGHT_BLUE);
	private static final Color BLUE_SOFT = StudioNightTheme.adaptiveColor(
			new Color(244, 247, 253), StudioNightTheme.NIGHT_SURFACE_ALT);
	private static final Color GREEN = StudioNightTheme.adaptiveColor(
			new Color(73, 121, 93), StudioNightTheme.NIGHT_GREEN);
	private static final Color GREEN_SOFT = StudioNightTheme.adaptiveColor(
			new Color(245, 250, 247), StudioNightTheme.NIGHT_PANEL_INK);
	private static final Color PURPLE = StudioNightTheme.adaptiveColor(
			new Color(105, 91, 155), StudioNightTheme.NIGHT_PURPLE);
	private static final Color PURPLE_SOFT = StudioNightTheme.adaptiveColor(
			new Color(247, 245, 252), StudioNightTheme.NIGHT_SURFACE_ALT);
	private static final Color AMBER = StudioNightTheme.adaptiveColor(
			new Color(150, 111, 50), StudioNightTheme.NIGHT_AMBER);
	private static final Color AMBER_SOFT = StudioNightTheme.adaptiveColor(
			new Color(252, 248, 240), StudioNightTheme.NIGHT_PANEL_INK);
	private static final Color RED = StudioNightTheme.adaptiveColor(
			new Color(172, 66, 66), StudioNightTheme.NIGHT_RED);
	private static final Color QUIET_BORDER = StudioNightTheme.adaptiveColor(
			new Color(222, 231, 243), StudioNightTheme.NIGHT_BORDER);
	private static final Color QUIET_LINE = StudioNightTheme.adaptiveColor(
			new Color(214, 224, 239), StudioNightTheme.NIGHT_BORDER);
	private static final Color QUIET_FILL = StudioNightTheme.adaptiveColor(
			new Color(251, 253, 255), StudioNightTheme.NIGHT_SURFACE);
	private static final Color TRACK = StudioNightTheme.adaptiveColor(
			new Color(230, 236, 246), StudioNightTheme.NIGHT_PANEL_INK);
	private static final int RADIUS = 8;
	private static final int PREVIEW_ROW_LIMIT = 5;
	private static final int SAMPLE_LIMIT = 5000;

	private final ControllerAgentGui owner;
	private final JLabel titleLabel;
	private final JLabel subtitleLabel;
	private final JLabel datasetPill;
	private final JLabel algorithmPill;
	private final JLabel targetPill;
	private final JLabel nodePill;
	private final DatasetSnapshotPanel datasetPanel;
	private final ColumnLegendPanel columnPanel;
	private final RunReadinessPanel readinessPanel;
	private final DataShapePanel dataShapePanel;
	private final ExecutionShapePanel executionShapePanel;
	private final FlowStoryboardPanel flowPanel;
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
		setBackground(BACKGROUND);

		titleLabel = new JLabel("Simulator Help");
		titleLabel.setFont(font(Font.BOLD, 26));
		titleLabel.setForeground(TEXT);
		subtitleLabel = new JLabel("");
		subtitleLabel.setFont(font(Font.PLAIN, 12));
		subtitleLabel.setForeground(MUTED);
		datasetPill = pill("Dataset", BLUE);
		algorithmPill = pill("Algorithm", PURPLE);
		targetPill = pill("Output", GREEN);
		nodePill = pill("Nodes", AMBER);
		signalHeaderPanel = new SignalHeaderPanel();
		datasetPanel = new DatasetSnapshotPanel();
		columnPanel = new ColumnLegendPanel();
		readinessPanel = new RunReadinessPanel();
		dataShapePanel = new DataShapePanel();
		executionShapePanel = new ExecutionShapePanel();
		flowPanel = new FlowStoryboardPanel();
		nodeMergePanel = new NodeMergePanel();
		predictionPanel = new PredictionDemoPanel();
		settingsPanel = new SettingsStripPanel();

		add(createHeader(), BorderLayout.NORTH);
		add(createDashboard(), BorderLayout.CENTER);

		Timer timer = new Timer(24, new ActionListener()
		{
			public void actionPerformed(ActionEvent event)
			{
				signalHeaderPanel.advance();
				flowPanel.advance();
				nodeMergePanel.advance();
				predictionPanel.advance();
			}
		});
		timer.start();
		refreshFromSimulator();
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
		JPanel header = new RoundedPanel(new BorderLayout(18, 0));
		header.setPreferredSize(new Dimension(900, 118));
		header.setBorder(new EmptyBorder(16, 18, 16, 18));

		JPanel copy = new JPanel();
		copy.setOpaque(false);
		copy.setLayout(new BoxLayout(copy, BoxLayout.Y_AXIS));
		copy.setPreferredSize(new Dimension(360, 86));
		copy.add(titleLabel);
		copy.add(Box.createVerticalStrut(4));
		copy.add(subtitleLabel);

		JPanel pills = new JPanel(new GridLayout(1, 4, 8, 0));
		pills.setOpaque(false);
		pills.setPreferredSize(new Dimension(360, 28));
		pills.setMaximumSize(new Dimension(360, 28));
		pills.add(datasetPill);
		pills.add(algorithmPill);
		pills.add(targetPill);
		pills.add(nodePill);
		copy.add(Box.createVerticalStrut(10));
		copy.add(pills);

		signalHeaderPanel.setPreferredSize(new Dimension(780, 84));
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
		c.insets = new java.awt.Insets(0, 0, 12, 12);
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
		c.insets = new java.awt.Insets(0, 0, 12, 0);
		dashboard.add(card("Prediction Meaning", predictionPanel), c);

		c.gridy = 1;
		c.insets = new java.awt.Insets(0, 0, 0, 12);
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
		panel.setBorder(new EmptyBorder(12, 12, 12, 12));
		JLabel label = new JLabel(title);
		label.setFont(font(Font.BOLD, 14));
		label.setForeground(TEXT);
		panel.add(label, BorderLayout.NORTH);
		panel.add(body, BorderLayout.CENTER);
		return panel;
	}

	private void updateLabels()
	{
		titleLabel.setText(snapshot.algorithmName + " run guide");
		subtitleLabel.setText("Live visual preview for "
				+ snapshot.datasetName
				+ ". This tab explains the current setup without running the model.");
		datasetPill.setText(compact(datasetFamily(snapshot), 16));
		datasetPill.setToolTipText(snapshot.datasetSource + ": " + snapshot.datasetName);
		algorithmPill.setText(compact(snapshot.algorithmName, 16));
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
		datasetPanel.setState(snapshot, preview);
		columnPanel.setState(snapshot, preview);
		readinessPanel.setState(snapshot, preview);
		dataShapePanel.setState(snapshot, preview);
		executionShapePanel.setState(snapshot, preview);
		flowPanel.setState(snapshot, preview);
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
		JLabel label = new JLabel(text, SwingConstants.CENTER);
		label.setFont(font(Font.BOLD, 10));
		label.setForeground(color.darker());
		label.setOpaque(true);
		label.setBackground(tint(color, 0.12f));
		label.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(tint(color, 0.42f)),
				new EmptyBorder(6, 8, 6, 8)));
		return label;
	}

	private static Font font(int style, int size)
	{
		return new Font("SansSerif", style, size);
	}

	private static Color tint(Color color, float amount)
	{
		if (StudioNightTheme.isNightMode())
		{
			float accentAmount = Math.max(0.10f, Math.min(0.42f, 1.0f - amount));
			Color base = amount > 0.70f
					? StudioNightTheme.NIGHT_SURFACE
					: StudioNightTheme.NIGHT_PANEL_INK;
			return StudioNightTheme.blend(base, color, accentAmount);
		}
		int r = (int) (color.getRed() + (255 - color.getRed()) * amount);
		int g = (int) (color.getGreen() + (255 - color.getGreen()) * amount);
		int b = (int) (color.getBlue() + (255 - color.getBlue()) * amount);
		return new Color(Math.min(255, r), Math.min(255, g), Math.min(255, b));
	}

	private static Color themed(Color day, Color night)
	{
		return StudioNightTheme.isNightMode() ? night : day;
	}

	private static Color glass(int alpha)
	{
		return themed(
				new Color(255, 255, 255, alpha),
				StudioNightTheme.withAlpha(
						StudioNightTheme.NIGHT_SURFACE_ALT,
						Math.min(255, alpha + 16)));
	}

	private static Color alpha(Color color, int opacity)
	{
		return new Color(color.getRed(), color.getGreen(), color.getBlue(),
				Math.max(0, Math.min(255, opacity)));
	}

	private static Color quietTint(Color color)
	{
		return tint(color, 0.92f);
	}

	private static Color quietStroke(Color color)
	{
		return tint(color, 0.64f);
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

	private static String formatBytes(long bytes)
	{
		if (bytes < 1024L)
		{
			return bytes + " B";
		}
		double kb = bytes / 1024.0;
		if (kb < 1024.0)
		{
			return String.format(Locale.US, "%.1f KB", kb);
		}
		double mb = kb / 1024.0;
		if (mb < 1024.0)
		{
			return String.format(Locale.US, "%.1f MB", mb);
		}
		return String.format(Locale.US, "%.2f GB", mb / 1024.0);
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
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
					RenderingHints.VALUE_ANTIALIAS_ON);
			g.setFont(font(Font.PLAIN, 11));
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
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
					RenderingHints.VALUE_ANTIALIAS_ON);
			g.setColor(SURFACE);
			g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1,
					RADIUS, RADIUS);
			g.setColor(BORDER);
			g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1,
					RADIUS, RADIUS);
			g.dispose();
			super.paintComponent(graphics);
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
			int pad = 12;
			int laneY = h / 2 + 10;
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
					compact(target, 22),
					compact(inputMeaning(snapshot), 24),
					snapshot.algorithmName,
					outputColumnName(snapshot)
			};
			Color[] colors = {BLUE, BLUE, PURPLE, AMBER, GREEN};
			int boxW = Math.max(106, (w - pad * 2 - 52) / 5);
			int gap = Math.max(7, (w - pad * 2 - boxW * 5) / 4);
			int x = pad;
			for (int i = 0; i < 5; i++)
			{
				drawIntentBox(g, x, 12, boxW, 58, titles[i], values[i], colors[i]);
				if (i < 4)
				{
					int x1 = x + boxW + 4;
					int x2 = x + boxW + gap - 4;
					g.setStroke(new BasicStroke(2f));
					g.setColor(new Color(203, 213, 230));
					g.drawLine(x1, laneY, x2, laneY);
					double t = (phase + i * 0.18) % 1.0;
					int px = (int) (x1 + (x2 - x1) * t);
					g.setColor(colors[i + 1]);
					g.fill(new Ellipse2D.Double(px - 3, laneY - 3, 6, 6));
				}
				x += boxW + gap;
			}
			g.setFont(font(Font.BOLD, 10));
			g.setColor(MUTED);
			drawClippedString(g,
					"This is a preview of meaning and flow, not a model run.",
					pad,
					h - 7,
					w - pad * 2);
			g.dispose();
		}

		private void drawIntentBox(Graphics2D g, int x, int y, int w, int h,
				String title, String value, Color color)
		{
			g.setColor(tint(color, 0.12f));
			g.fillRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setColor(tint(color, 0.36f));
			g.drawRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setFont(font(Font.BOLD, 9));
			g.setColor(color.darker());
			drawClippedString(g, title.toUpperCase(Locale.US), x + 9, y + 16, w - 18);
			g.setFont(font(Font.BOLD, 12));
			g.setColor(TEXT);
			drawClippedString(g, value, x + 9, y + 38, w - 18);
		}
	}

	private static final class DatasetSnapshotPanel extends VisualPanel
	{
		protected void paintComponent(Graphics graphics)
		{
			super.paintComponent(graphics);
			Graphics2D g = (Graphics2D) graphics.create();
			prepare(g);
			int w = getWidth();
			int h = getHeight();

			drawMetric(g, 10, 8, "ROWS", preview.loading ? "..." : formatCount(preview.validRows), BLUE);
			drawMetric(g, w / 3 + 4, 8, "USERS", preview.loading ? "..." : formatCount(preview.users.size()), PURPLE);
			drawMetric(g, (w * 2) / 3 - 4, 8, "LABELS", preview.loading ? "..." : formatCount(preview.followees.size()), GREEN);

			int tableY = 58;
			int rowH = Math.max(34, (h - tableY - 10) / Math.max(1, PREVIEW_ROW_LIMIT + 1));
			drawTableHeader(g, 10, tableY, w - 20, rowH);
			if (preview.loading || preview.rows.isEmpty())
			{
				g.setColor(MUTED);
				g.setFont(font(Font.BOLD, 12));
				drawClippedString(g, preview.message, 18, tableY + rowH + 26, w - 36);
				g.dispose();
				return;
			}
			int y = tableY + rowH;
			for (int i = 0; i < Math.min(PREVIEW_ROW_LIMIT, preview.rows.size()); i++)
			{
				DsmpRow row = preview.rows.get(i);
				g.setColor(i % 2 == 0 ? SURFACE_ALT : SURFACE);
				g.fillRoundRect(10, y + 2, w - 20, rowH - 4, 6, 6);
				g.setColor(new Color(225, 232, 242));
				g.drawRoundRect(10, y + 2, w - 20, rowH - 4, 6, 6);
				int c1 = 18;
				int c2 = 18 + (w - 36) / 4;
				int c3 = 18 + (w - 36) / 2;
				int c4 = 18 + ((w - 36) * 3) / 4;
				g.setFont(font(Font.BOLD, 11));
				g.setColor(GREEN.darker());
				drawClippedString(g, row.followee, c1, y + 21, c2 - c1 - 10);
				g.setColor(BLUE.darker());
				drawClippedString(g, row.userName, c2, y + 21, c3 - c2 - 10);
				g.setColor(PURPLE.darker());
				drawClippedString(g, compact(row.text, 46), c3, y + 21, c4 - c3 + 80);
				g.setColor(MUTED);
				drawClippedString(g, row.date, c4 + 90, y + 21, w - c4 - 108);
				y += rowH;
			}
			g.dispose();
		}

		private void drawMetric(Graphics2D g, int x, int y, String label, String value, Color color)
		{
			int w = Math.max(92, getWidth() / 3 - 18);
			g.setColor(tint(color, 0.13f));
			g.fillRoundRect(x, y, w, 40, RADIUS, RADIUS);
			g.setColor(tint(color, 0.38f));
			g.drawRoundRect(x, y, w, 40, RADIUS, RADIUS);
			g.setFont(font(Font.BOLD, 9));
			g.setColor(color.darker());
			g.drawString(label, x + 10, y + 15);
			g.setFont(font(Font.BOLD, 15));
			g.setColor(TEXT);
			drawClippedString(g, value, x + 10, y + 32, w - 20);
		}

		private void drawTableHeader(Graphics2D g, int x, int y, int w, int h)
		{
			g.setColor(SURFACE_ALT);
			g.fillRoundRect(x, y, w, h - 4, RADIUS, RADIUS);
			g.setFont(font(Font.BOLD, 10));
			g.setColor(MUTED);
			int c1 = x + 8;
			int c2 = x + 8 + (w - 16) / 4;
			int c3 = x + 8 + (w - 16) / 2;
			int c4 = x + 8 + ((w - 16) * 3) / 4 + 90;
			g.drawString("COL 1 LABEL", c1, y + 20);
			g.drawString("COL 5 USER", c2, y + 20);
			g.drawString("COL 6 TEXT", c3, y + 20);
			g.drawString("DATE", c4, y + 20);
		}
	}

	private static final class ColumnLegendPanel extends VisualPanel
	{
		protected void paintComponent(Graphics graphics)
		{
			super.paintComponent(graphics);
			Graphics2D g = (Graphics2D) graphics.create();
			prepare(g);
			int w = getWidth();
			int h = getHeight();
			int topH = Math.min(76, Math.max(56, h / 3));
			drawColumnTakeaway(g, 6, 6, w - 12, topH);
			String[] labels = {
					"1 Followee",
					"2 Post id",
					"3 Date",
					"4 User id",
					"5 User",
					"6 Text"
			};
			String[] values = preview.sampleColumnValues();
			int gap = 6;
			int startY = topH + 14;
			int boxW = Math.max(64, (w - 10 - gap * 5) / 6);
			int boxH = Math.max(56, h - startY - 8);
			for (int i = 0; i < 6; i++)
			{
				boolean input = i == 5;
				boolean output = (i == 0 && snapshot.outputType == RecommendationCandidateType.FOLLOWEE)
						|| (i == 4 && snapshot.outputType == RecommendationCandidateType.USER);
				Color color = input ? PURPLE : output ? GREEN : BLUE;
				int x = 5 + i * (boxW + gap);
				g.setColor(input || output ? tint(color, 0.12f) : SURFACE_ALT);
				g.fillRoundRect(x, startY, boxW, boxH, RADIUS, RADIUS);
				g.setColor(input || output ? color : BORDER);
				g.drawRoundRect(x, startY, boxW, boxH, RADIUS, RADIUS);
				g.setFont(font(Font.BOLD, 10));
				g.setColor(input || output ? color.darker() : TEXT);
				drawClippedString(g, labels[i], x + 8, startY + 19, boxW - 16);
				g.setFont(font(Font.PLAIN, 10));
				g.setColor(MUTED);
				drawClippedString(g, input ? "INPUT" : output ? "OUTPUT" : "context",
						x + 8, startY + 36, boxW - 16);
				g.setFont(font(Font.BOLD, 10));
				g.setColor(TEXT);
				drawClippedString(g, values[i], x + 8, startY + 54, boxW - 16);
			}
			g.dispose();
		}

		private void drawColumnTakeaway(Graphics2D g, int x, int y, int w, int h)
		{
			g.setColor(SURFACE_ALT);
			g.fillRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setColor(new Color(218, 228, 244));
			g.drawRoundRect(x, y, w, h, RADIUS, RADIUS);
			int split = w / 2;
			drawRoleChip(g, x + 10, y + 10, split - 16, h - 20,
					"MODEL INPUT",
					"column 6 text",
					PURPLE);
			drawRoleChip(g, x + split + 6, y + 10, split - 16, h - 20,
					"RANKED OUTPUT",
					outputColumnName(snapshot),
					GREEN);
			g.setStroke(new BasicStroke(2f));
			g.setColor(new Color(185, 197, 218));
			g.drawLine(x + split - 4, y + h / 2, x + split + 4, y + h / 2);
		}

		private void drawRoleChip(Graphics2D g, int x, int y, int w, int h,
				String label, String value, Color color)
		{
			g.setColor(tint(color, 0.13f));
			g.fillRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setColor(tint(color, 0.38f));
			g.drawRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setFont(font(Font.BOLD, 9));
			g.setColor(color.darker());
			drawClippedString(g, label, x + 10, y + 16, w - 20);
			g.setFont(font(Font.BOLD, 12));
			g.setColor(TEXT);
			drawClippedString(g, value, x + 10, y + 36, w - 20);
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
			int pad = 14;
			if (w < 240 || h < 190)
			{
				g.dispose();
				return;
			}

			int score = readinessScore();
			Color color = readinessColor(score);
			int heroH = Math.min(122, Math.max(98, h / 3));
			drawReadinessHero(g, pad, 8, w - pad * 2, heroH, score, color);

			int metricY = 8 + heroH + 10;
			int metricH = 44;
			int gap = 8;
			int metricW = Math.max(1, (w - pad * 2 - gap) / 2);
			drawReadinessMetric(g, pad, metricY, metricW, metricH,
					"rows", preview.loading ? "..." : formatCount(preview.validRows),
					"usable DSMP rows", BLUE);
			drawReadinessMetric(g, pad + metricW + gap, metricY, metricW, metricH,
					"targets", snapshot.batchEnabled
							? formatCount(snapshot.batchUsers)
							: targetCountLabel(),
					snapshot.batchEnabled ? "batch users" : "selected user", PURPLE);
			drawReadinessMetric(g, pad, metricY + metricH + gap, metricW, metricH,
					"outputs", outputShortLabel(snapshot),
					outputColumnName(snapshot), GREEN);
			drawReadinessMetric(g, pad + metricW + gap, metricY + metricH + gap,
					metricW, metricH,
					"workers", formatCount(snapshot.nodes),
					"parallel node lanes", AMBER);

			int checksY = metricY + metricH * 2 + gap + 12;
			int checksH = h - checksY - 8;
			if (checksH >= 72)
			{
				drawChecklist(g, pad, checksY, w - pad * 2, checksH);
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
					tint(color, 0.80f)));
			g.fillRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setColor(new Color(220, 230, 243));
			g.drawRoundRect(x, y, w, h, RADIUS, RADIUS);

			int ring = Math.max(68, Math.min(92, h - 24));
			int ringX = x + 18;
			int ringY = y + (h - ring) / 2;
			g.setStroke(new BasicStroke(9f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			g.setColor(new Color(226, 234, 246));
			g.draw(new Arc2D.Double(ringX, ringY, ring, ring, 90, -360, Arc2D.OPEN));
			g.setColor(color);
			g.draw(new Arc2D.Double(ringX, ringY, ring, ring, 90,
					-360.0 * (score / 100.0), Arc2D.OPEN));
			g.setFont(font(Font.BOLD, 27));
			g.setColor(TEXT);
			String scoreText = Integer.toString(score);
			FontMetrics metrics = g.getFontMetrics();
			g.drawString(scoreText,
					ringX + ring / 2 - metrics.stringWidth(scoreText) / 2,
					ringY + ring / 2 + 10);

			int copyX = ringX + ring + 20;
			int copyW = w - (copyX - x) - 16;
			g.setFont(font(Font.BOLD, 10));
			g.setColor(color.darker());
			drawClippedString(g, "RUN READINESS", copyX, y + 27, copyW);
			g.setFont(font(Font.BOLD, 18));
			g.setColor(TEXT);
			drawClippedString(g, readinessHeadline(score), copyX, y + 54, copyW);
			g.setFont(font(Font.PLAIN, 11));
			g.setColor(MUTED);
			drawWrapped(g, readinessSentence(), copyX, y + 75, copyW, 2, 14);
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
			g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 56));
			g.fillRoundRect(x, y, 5, h, RADIUS, RADIUS);
			g.setColor(new Color(216, 226, 241));
			g.drawRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setFont(font(Font.BOLD, 9));
			g.setColor(color.darker());
			drawClippedString(g, label.toUpperCase(Locale.US), x + 11, y + 14, w - 20);
			g.setFont(font(Font.BOLD, 16));
			g.setColor(TEXT);
			drawClippedString(g, value, x + 11, y + 31, w - 20);
			g.setFont(font(Font.PLAIN, 9));
			g.setColor(MUTED);
			drawClippedString(g, detail, x + 11, y + h - 5, w - 20);
		}

		private void drawChecklist(Graphics2D g, int x, int y, int w, int h)
		{
			g.setColor(glass(218));
			g.fillRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setColor(new Color(218, 228, 242));
			g.drawRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setFont(font(Font.BOLD, 10));
			g.setColor(MUTED);
			drawClippedString(g, "WHAT IS SAFE TO EXPECT", x + 12, y + 17, w - 24);
			int rowY = y + 30;
			int rowH = Math.max(16, Math.min(22, (h - 38) / 3));
			drawCheckRow(g, x + 12, rowY, w - 24, rowH,
					"dataset",
					preview.validRows > 0 ? "loaded" : "waiting",
					preview.validRows > 0 ? GREEN : AMBER);
			drawCheckRow(g, x + 12, rowY + rowH + 6, w - 24, rowH,
					"algorithm",
					snapshot.workingAlgorithm ? "supported" : "not active",
					snapshot.workingAlgorithm ? GREEN : RED);
			drawCheckRow(g, x + 12, rowY + (rowH + 6) * 2, w - 24, rowH,
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
			g.setColor(tint(color, 0.16f));
			g.fillRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 70));
			g.drawRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setColor(color);
			g.fillOval(x + 8, y + h / 2 - 4, 8, 8);
			g.setFont(font(Font.BOLD, 10));
			g.setColor(TEXT);
			drawClippedString(g, label, x + 24, y + h / 2 + 4, w / 2);
			g.setFont(font(Font.BOLD, 10));
			g.setColor(color.darker());
			drawClippedString(g, value, x + w / 2 + 18, y + h / 2 + 4, w / 2 - 24);
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
			int pad = 14;
			if (w < 300 || h < 220)
			{
				g.dispose();
				return;
			}

			int metricH = 44;
			int metricGap = 8;
			int metricW = Math.max(1, (w - pad * 2 - metricGap * 3) / 4);
			drawSmallMetric(g, pad, 8, metricW, metricH,
					"rows", preview.loading ? "..." : formatCount(preview.validRows),
					BLUE);
			drawSmallMetric(g, pad + (metricW + metricGap), 8, metricW, metricH,
					"users", preview.loading ? "..." : formatCount(preview.users.size()),
					PURPLE);
			drawSmallMetric(g, pad + (metricW + metricGap) * 2, 8, metricW, metricH,
					"labels", preview.loading ? "..." : formatCount(preview.followees.size()),
					GREEN);
			drawSmallMetric(g, pad + (metricW + metricGap) * 3, 8, metricW, metricH,
					"text terms", preview.loading ? "..." : formatCount(preview.terms.size()),
					AMBER);

			int mapY = 8 + metricH + 12;
			int mapH = Math.min(320, Math.max(210, h - mapY - 104));
			drawDataMap(g, pad, mapY, w - pad * 2, mapH);

			int sampleY = mapY + mapH + 10;
			drawSampleStrip(g, pad, sampleY, w - pad * 2, h - sampleY - 6);
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
			g.setColor(SURFACE);
			g.fillRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setColor(alpha(color, 42));
			g.fillRoundRect(x + 8, y + 8, 4, h - 16, 4, 4);
			g.setColor(QUIET_BORDER);
			g.drawRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setFont(font(Font.BOLD, 9));
			g.setColor(MUTED);
			drawClippedString(g, label.toUpperCase(Locale.US), x + 18, y + 15, w - 28);
			g.setFont(font(Font.BOLD, 17));
			g.setColor(TEXT);
			drawClippedString(g, value, x + 18, y + h - 11, w - 28);
		}

		private void drawDataMap(Graphics2D g, int x, int y, int w, int h)
		{
			g.setPaint(new GradientPaint(
					x,
					y,
					QUIET_FILL,
					x + w,
					y + h,
					SURFACE_ALT));
			g.fillRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setColor(QUIET_BORDER);
			g.drawRoundRect(x, y, w, h, RADIUS, RADIUS);

			g.setFont(font(Font.BOLD, 10));
			g.setColor(MUTED);
			drawClippedString(g, "DSMP SIX-COLUMN MEANING", x + 16, y + 22, w - 32);
			g.setFont(font(Font.PLAIN, 10));
			g.setColor(MUTED);
			drawClippedString(g,
					"Current settings decide which DSMP column is model input and which side is ranked.",
					x + 16,
					y + 40,
					w - 32);

			int nodeTop = y + 58;
			int nodeH = Math.min(92, Math.max(72, (h - 130) / 2));
			int gap = 18;
			int nodeW = Math.max(112, (w - 48 - gap * 2) / 3);
			int leftX = x + 18;
			int centerX = leftX + nodeW + gap;
			int rightX = centerX + nodeW + gap;
			int cy = nodeTop + nodeH / 2;
			drawNode(g, leftX, nodeTop, nodeW, nodeH,
					"MODEL INPUT",
					"column 6",
					compact(inputMeaning(snapshot), 28),
					PURPLE);
			drawNode(g, centerX, nodeTop, nodeW, nodeH,
					"ALGORITHM",
					snapshot.algorithmName,
					internalAlgorithmMeaning(snapshot),
					AMBER);
			drawNode(g, rightX, nodeTop, nodeW, nodeH,
					"RANKED OUTPUT",
					outputShortLabel(snapshot),
					compact(outputMeaning(snapshot), 30),
					GREEN);
			drawFlow(g, leftX + nodeW, cy, centerX, cy, BLUE, 0.00f);
			drawFlow(g, centerX + nodeW, cy, rightX, cy, GREEN, 0.31f);

			int contextY = nodeTop + nodeH + 14;
			int contextW = Math.max(1, (w - 60) / 3);
			drawContextChip(g, x + 18, contextY, contextW,
					"Column 1", columnOneRole(snapshot), GREEN);
			drawContextChip(g, x + 30 + contextW, contextY, contextW,
					"Column 5", columnFiveRole(snapshot), BLUE);
			drawContextChip(g, x + 42 + contextW * 2, contextY, contextW,
					"Date", "filter window context", AMBER);

			int meaningY = contextY + 54;
			if (meaningY + 42 <= y + h - 8)
			{
				drawMeaningBand(g, x + 18, meaningY, w - 36, y + h - meaningY - 8);
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
			g.setColor(quietStroke(color));
			g.drawRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setColor(alpha(color, 80));
			g.fillRoundRect(x, y, 8, h, RADIUS, RADIUS);
			g.fillRect(x + 4, y, 6, h);
			g.setColor(alpha(color, 150));
			g.fill(new Ellipse2D.Double(x + 17, y + 18, 10, 10));
			g.setFont(font(Font.BOLD, 9));
			g.setColor(MUTED);
			drawClippedString(g, label, x + 36, y + 18, w - 46);
			g.setFont(font(Font.BOLD, 14));
			g.setColor(TEXT);
			drawClippedString(g, main, x + 16, y + 45, w - 28);
			g.setFont(font(Font.PLAIN, 10));
			g.setColor(MUTED);
			drawClippedString(g, detail, x + 16, y + 65, w - 28);
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
			g.setStroke(new BasicStroke(2f));
			g.setColor(new Color(202, 214, 232));
			g.drawLine(x1 + 8, y1, x2 - 8, y2);
			double t = (phase + offset) % 1.0;
			int px = (int) (x1 + 8 + (x2 - x1 - 16) * t);
			g.setColor(alpha(color, 145));
			g.fill(new Ellipse2D.Double(px - 4, y1 - 4, 8, 8));
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
			g.fillRoundRect(x, y, w, 42, RADIUS, RADIUS);
			g.setColor(alpha(color, 52));
			g.fillRoundRect(x + 7, y + 8, 4, 26, 4, 4);
			g.setColor(QUIET_BORDER);
			g.drawRoundRect(x, y, w, 42, RADIUS, RADIUS);
			g.setFont(font(Font.BOLD, 9));
			g.setColor(MUTED);
			drawClippedString(g, label.toUpperCase(Locale.US), x + 18, y + 15, w - 26);
			g.setFont(font(Font.BOLD, 11));
			g.setColor(TEXT);
			drawClippedString(g, value, x + 18, y + 31, w - 26);
		}

		private void drawMeaningBand(Graphics2D g, int x, int y, int w, int h)
		{
			Color color = snapshot.outputType == RecommendationCandidateType.USER
					? BLUE : GREEN;
			g.setColor(SURFACE);
			g.fillRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setColor(QUIET_BORDER);
			g.drawRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setColor(alpha(color, 58));
			g.fillRoundRect(x + 10, y + 10, 76, h - 20, 12, 12);
			g.setFont(font(Font.BOLD, 10));
			g.setColor(color.darker());
			drawClippedString(g, "MEANING", x + 22, y + h / 2 + 4, 58);
			g.setFont(font(Font.BOLD, 12));
			g.setColor(TEXT);
			drawClippedString(g,
					"Read " + inputMeaning(snapshot) + " -> rank "
							+ outputMeaning(snapshot),
					x + 100,
					y + h / 2 + 4,
					w - 114);
		}

		private void drawSampleStrip(Graphics2D g, int x, int y, int w, int h)
		{
			if (h < 42)
			{
				return;
			}
			g.setColor(SURFACE);
			g.fillRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setColor(QUIET_BORDER);
			g.drawRoundRect(x, y, w, h, RADIUS, RADIUS);
			DsmpRow row = !preview.targetRows.isEmpty()
					? preview.targetRows.get(0)
					: preview.rows.isEmpty() ? null : preview.rows.get(0);
			g.setFont(font(Font.BOLD, 10));
			g.setColor(BLUE.darker());
			drawClippedString(g, "LIVE EXAMPLE ROW", x + 12, y + 17, w - 24);
			g.setFont(font(Font.PLAIN, 10));
			g.setColor(MUTED);
			if (row == null)
			{
				drawClippedString(g, preview.message, x + 12, y + 36, w - 24);
				return;
			}
			int chipY = y + 26;
			int chipH = Math.max(30, Math.min(42, h - 34));
			int gap = 8;
			int chipW = Math.max(1, (w - 24 - gap * 2) / 3);
			drawSampleCell(g, x + 12, chipY, chipW, chipH,
					"column 1", row.followee, GREEN);
			drawSampleCell(g, x + 12 + chipW + gap, chipY, chipW, chipH,
					"column 5", row.userName, BLUE);
			drawSampleCell(g, x + 12 + (chipW + gap) * 2, chipY, chipW, chipH,
					"column 6", row.text, PURPLE);
			if (h > 86)
			{
				g.setFont(font(Font.PLAIN, 10));
				g.setColor(MUTED);
				drawClippedString(g,
						"This exact row shape is what the simulator sends into the selected algorithm.",
						x + 12,
						y + h - 11,
						w - 24);
			}
		}

		private void drawSampleCell(Graphics2D g, int x, int y, int w, int h,
				String label, String value, Color color)
		{
			g.setColor(QUIET_FILL);
			g.fillRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setColor(alpha(color, 48));
			g.fillRoundRect(x + 7, y + 7, 4, h - 14, 4, 4);
			g.setColor(QUIET_BORDER);
			g.drawRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setFont(font(Font.BOLD, 9));
			g.setColor(MUTED);
			drawClippedString(g, label.toUpperCase(Locale.US), x + 18, y + 14, w - 26);
			g.setFont(font(Font.BOLD, 10));
			g.setColor(TEXT);
			drawClippedString(g, value, x + 18, y + h - 10, w - 26);
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
			int pad = 14;
			if (w < 320 || h < 230)
			{
				g.dispose();
				return;
			}

			int questionH = 58;
			drawQuestion(g, pad, 8, w - pad * 2, questionH);

			int workY = 8 + questionH + 10;
			int workH = 88;
			drawWorkbench(g, pad, workY, w - pad * 2, workH);

			int laneY = workY + workH + 12;
			int laneH = h - laneY - 8;
			drawExecutionLane(g, pad, laneY, w - pad * 2, laneH);
			g.dispose();
		}

		private void drawQuestion(Graphics2D g, int x, int y, int w, int h)
		{
			g.setColor(SURFACE);
			g.fillRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setColor(QUIET_BORDER);
			g.drawRoundRect(x, y, w, h, RADIUS, RADIUS);
			int labelW = Math.min(106, Math.max(88, w / 5));
			g.setColor(quietTint(GREEN));
			g.fillRoundRect(x + 12, y + 10, labelW, h - 20, 14, 14);
			g.setColor(quietStroke(GREEN));
			g.drawRoundRect(x + 12, y + 10, labelW, h - 20, 14, 14);
			g.setFont(font(Font.BOLD, 10));
			g.setColor(GREEN.darker());
			drawClippedString(g, "QUESTION", x + 28, y + 30, labelW - 28);
			g.setFont(font(Font.BOLD, 13));
			g.setColor(TEXT);
			drawClippedString(g, beginnerQuestion(snapshot),
					x + labelW + 24,
					y + 30,
					w - labelW - 36);
		}

		private void drawWorkbench(Graphics2D g, int x, int y, int w, int h)
		{
			g.setPaint(new GradientPaint(
					x,
					y,
					QUIET_FILL,
					x + w,
					y + h,
					SURFACE));
			g.fillRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setColor(QUIET_BORDER);
			g.drawRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setFont(font(Font.BOLD, 10));
			g.setColor(PURPLE.darker());
			drawClippedString(g, "ALGORITHM WORKBENCH", x + 14, y + 19, w - 28);
			g.setFont(font(Font.BOLD, 12));
			g.setColor(TEXT);
			drawClippedString(g, snapshot.algorithmName + " builds "
					+ featureSummary(snapshot) + " from cleaned column-6 text.",
					x + 14,
					y + 40,
					w - 28);

			String[] steps = algorithmWorkbenchSteps(snapshot);
			int moduleW = Math.max(82, (w - 58) / 3);
			int moduleY = y + h - 36;
			for (int i = 0; i < 3; i++)
			{
				int moduleX = x + 14 + i * (moduleW + 15);
				Color color = i == 0 ? BLUE : i == 1 ? PURPLE : GREEN;
				g.setColor(SURFACE);
				g.fillRoundRect(moduleX, moduleY, moduleW, 26, 12, 12);
				g.setColor(quietStroke(color));
				g.drawRoundRect(moduleX, moduleY, moduleW, 26, 12, 12);
				g.setColor(alpha(color, 42));
				g.fillRoundRect(moduleX + 6, moduleY + 7, 4, 12, 4, 4);
				g.setFont(font(Font.BOLD, 10));
				g.setColor(TEXT);
				drawClippedString(g, steps[i], moduleX + 16, moduleY + 17, moduleW - 24);
				if (i < 2)
				{
					int ax = moduleX + moduleW + 3;
					int ay = moduleY + 13;
					g.setColor(new Color(183, 195, 214));
					g.setStroke(new BasicStroke(2f));
					g.drawLine(ax, ay, ax + 10, ay);
					g.drawLine(ax + 6, ay - 4, ax + 10, ay);
					g.drawLine(ax + 6, ay + 4, ax + 10, ay);
				}
			}
		}

		private void drawExecutionLane(Graphics2D g, int x, int y, int w, int h)
		{
			g.setColor(SURFACE);
			g.fillRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setColor(QUIET_BORDER);
			g.drawRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setFont(font(Font.BOLD, 10));
			g.setColor(BLUE.darker());
			drawClippedString(g, "EXECUTION LANE", x + 14, y + 21, w - 28);
			g.setFont(font(Font.PLAIN, 10));
			g.setColor(MUTED);
			drawClippedString(g,
					"Same order the simulator follows after Initialize and Run Simulation.",
					x + 112,
					y + 21,
					w - 126);

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
					cleaningSummary(snapshot),
					featureSummary(snapshot),
					snapshot.nodes + " node" + (snapshot.nodes == 1 ? "" : "s"),
					"top " + snapshot.topRecommendations
			};
			Color[] colors = {BLUE, BLUE, PURPLE, AMBER, GREEN};
			int gap = 8;
			int cardW = Math.max(86, (w - 28 - gap * 4) / 5);
			int cardH = Math.max(92, h - 52);
			int cardY = y + 36;
			for (int i = 0; i < 5; i++)
			{
				int cardX = x + 14 + i * (cardW + gap);
				if (i > 0)
				{
					int lineX1 = cardX - gap + 2;
					int lineX2 = cardX - 4;
					int lineY = cardY + 26;
					g.setStroke(new BasicStroke(2f));
					g.setColor(new Color(203, 213, 230));
					g.drawLine(lineX1, lineY, lineX2, lineY);
					double t = (phase + i * 0.16) % 1.0;
					int px = (int) (lineX1 + (lineX2 - lineX1) * t);
					g.setColor(alpha(colors[i], 150));
					g.fill(new Ellipse2D.Double(px - 3, lineY - 3, 6, 6));
				}
				drawStageCard(g, cardX, cardY, cardW, cardH,
						title[i],
						detail[i],
						stageSteps(i, snapshot),
						colors[i]);
			}
		}

		private void drawStageCard(
				Graphics2D g,
				int x,
				int y,
				int w,
				int h,
				String title,
				String detail,
				String[] steps,
				Color color)
		{
			g.setColor(SURFACE);
			g.fillRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setColor(quietStroke(color));
			g.drawRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setColor(alpha(color, 72));
			g.fillRoundRect(x, y, 7, h, RADIUS, RADIUS);
			g.fillRect(x + 4, y, 5, h);
			g.setColor(alpha(color, 150));
			g.fill(new Ellipse2D.Double(x + 14, y + 14, 10, 10));
			g.setFont(font(Font.BOLD, 12));
			g.setColor(TEXT);
			drawClippedString(g, title, x + 31, y + 24, w - 40);
			g.setFont(font(Font.PLAIN, 10));
			g.setColor(MUTED);
			drawClippedString(g, detail, x + 12, y + 45, w - 24);
			int chipY = y + 58;
			int maxSteps = h >= 132 ? 3 : 2;
			for (int i = 0; i < Math.min(maxSteps, steps.length); i++)
			{
				g.setColor(QUIET_FILL);
				g.fillRoundRect(x + 12, chipY, w - 24, 20, 8, 8);
				g.setColor(QUIET_BORDER);
				g.drawRoundRect(x + 12, chipY, w - 24, 20, 8, 8);
				g.setFont(font(Font.PLAIN, 9));
				g.setColor(TEXT);
				drawClippedString(g, steps[i], x + 20, chipY + 14, w - 40);
				chipY += 24;
			}
			g.setColor(TRACK);
			g.fillRoundRect(x + 12, y + h - 14, w - 24, 5, 5, 5);
			g.setColor(alpha(color, 185));
			g.fillRoundRect(x + 12, y + h - 14,
					Math.max(12, (int) ((w - 24) * 0.68)), 5, 5, 5);
		}
	}

	private static final class FlowStoryboardPanel extends VisualPanel
	{
		protected void paintComponent(Graphics graphics)
		{
			super.paintComponent(graphics);
			Graphics2D g = (Graphics2D) graphics.create();
			prepare(g);
			int w = getWidth();
			int h = getHeight();
			int pad = 12;
			int bandH = Math.min(94, Math.max(78, h / 5));
			drawPlainLanguageBand(g, pad, 8, w - pad * 2, bandH);
			int midY = bandH + 18;
			int midH = Math.min(96, Math.max(78, h / 5));
			int questionW = Math.max(250, (w - pad * 2 - 10) * 40 / 100);
			drawQuestionStrip(g, pad, midY, questionW, midH);
			drawAlgorithmWorkbench(g,
					pad + questionW + 10,
					midY,
					w - pad * 2 - questionW - 10,
					midH,
					snapshot);
			int laneY = midY + midH + 14;
			int laneH = Math.max(130, h - laneY - 12);
			String[] title = {
					"Read rows",
					"Clean text",
					algorithmStage(snapshot),
					"Node score maps",
					"Merge top " + snapshot.topRecommendations
			};
			String[] detail = {
					preview.validRows > 0 ? formatCount(preview.validRows) + " DSMP rows" : snapshot.datasetSource,
					cleaningSummary(snapshot),
					featureSummary(snapshot),
					snapshot.nodes + " worker lane" + (snapshot.nodes == 1 ? "" : "s"),
					snapshot.candidateTypeLabel()
			};
			drawLaneFrame(g, pad, laneY, w - pad * 2, laneH);
			int cardTop = laneY + 34;
			int cardH = laneH - 48;
			int boxW = Math.max(108, (w - pad * 2 - 48) / 5);
			int gap = Math.max(8, (w - pad * 2 - boxW * 5) / 4);
			int x = pad;
			for (int i = 0; i < 5; i++)
			{
				if (i > 0)
				{
					int x1 = x - gap + 8;
					int x2 = x - 10;
					g.setColor(new Color(205, 216, 235));
					g.setStroke(new BasicStroke(2f));
					g.drawLine(x1, cardTop + 22, x2, cardTop + 22);
					double t = (phase + i * 0.16) % 1.0;
					int px = (int) (x1 + (x2 - x1) * t);
					g.setColor(i % 2 == 0 ? GREEN : BLUE);
					g.fill(new Ellipse2D.Double(px - 3, cardTop + 19, 6, 6));
				}
				Color color = i == 2 ? PURPLE : i == 4 ? GREEN : BLUE;
				drawStage(g, x, cardTop, boxW, cardH, title[i], detail[i],
						stageSteps(i, snapshot), color);
				x += boxW + gap;
			}
			g.dispose();
		}

		private void drawPlainLanguageBand(Graphics2D g, int x, int y, int w, int h)
		{
			g.setPaint(new GradientPaint(
					x,
					y,
					SURFACE_ALT,
					x + w,
					y + h,
					StudioNightTheme.isNightMode()
							? StudioNightTheme.NIGHT_PANEL_INK
							: new Color(241, 246, 255)));
			g.fillRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setColor(new Color(218, 228, 244));
			g.drawRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setFont(font(Font.BOLD, 10));
			g.setColor(BLUE.darker());
			g.drawString(datasetFamily(snapshot).toUpperCase(Locale.US)
					+ " - WHAT THE SIMULATOR WILL DO", x + 14, y + 20);

			String left = "Read " + inputMeaning(snapshot);
			String middle = algorithmPlainLanguage(snapshot);
			String right = "Return " + outputMeaning(snapshot);
			int cardW = (w - 52) / 3;
			drawMiniConcept(g, x + 14, y + 34, cardW, h - 46, left, PURPLE);
			drawMiniConcept(g, x + 26 + cardW, y + 34, cardW, h - 46, middle, AMBER);
			drawMiniConcept(g, x + 38 + cardW * 2, y + 34, cardW, h - 46, right, GREEN);

			int arrowY = y + h / 2 + 12;
			g.setStroke(new BasicStroke(2f));
			g.setColor(new Color(183, 195, 214));
			g.drawLine(x + 19 + cardW, arrowY, x + 21 + cardW, arrowY);
			g.drawLine(x + 31 + cardW * 2, arrowY, x + 33 + cardW * 2, arrowY);
		}

		private void drawLaneFrame(Graphics2D g, int x, int y, int w, int h)
		{
			g.setColor(SURFACE_ALT);
			g.fillRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setColor(new Color(219, 228, 244));
			g.drawRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setFont(font(Font.BOLD, 10));
			g.setColor(BLUE.darker());
			g.drawString("EXECUTION LANE", x + 14, y + 21);
			g.setFont(font(Font.PLAIN, 10));
			g.setColor(MUTED);
			drawClippedString(g,
					"Follow these cards left to right: data is cleaned, modeled, scored by nodes, then merged.",
					x + 112,
					y + 21,
					w - 126);
		}

		private void drawQuestionStrip(Graphics2D g, int x, int y, int w, int h)
		{
			g.setColor(glass(235));
			g.fillRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setColor(new Color(219, 228, 244));
			g.drawRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setColor(GREEN);
			g.fillRoundRect(x + 12, y + 10, 88, h - 20, 14, 14);
			g.setFont(font(Font.BOLD, 10));
			g.setColor(SURFACE);
			drawClippedString(g, "QUESTION", x + 27, y + 27, 62);
			g.setFont(font(Font.BOLD, 13));
			g.setColor(TEXT);
			drawClippedString(g, beginnerQuestion(snapshot), x + 114, y + 28, w - 128);
		}

		private void drawAlgorithmWorkbench(Graphics2D g, int x, int y, int w, int h,
				Snapshot s)
		{
			g.setColor(glass(232));
			g.fillRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setColor(new Color(219, 228, 244));
			g.drawRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setFont(font(Font.BOLD, 10));
			g.setColor(PURPLE.darker());
			g.drawString("ALGORITHM WORKBENCH", x + 14, y + 18);
			g.setFont(font(Font.BOLD, 12));
			g.setColor(TEXT);
			drawClippedString(g, s.algorithmName + " turns cleaned text into "
					+ featureSummary(s) + ".", x + 14, y + 38, w - 28);

			String[] steps = algorithmWorkbenchSteps(s);
			int moduleW = Math.max(92, (w - 62) / 3);
			int moduleY = y + Math.max(44, h - 42);
			for (int i = 0; i < 3; i++)
			{
				int moduleX = x + 14 + i * (moduleW + 17);
				Color color = i == 0 ? BLUE : i == 1 ? PURPLE : GREEN;
				g.setColor(tint(color, 0.12f));
				g.fillRoundRect(moduleX, moduleY, moduleW, 30, 14, 14);
				g.setColor(tint(color, 0.38f));
				g.drawRoundRect(moduleX, moduleY, moduleW, 30, 14, 14);
				g.setFont(font(Font.BOLD, 10));
				g.setColor(color.darker());
				drawClippedString(g, steps[i], moduleX + 10, moduleY + 19, moduleW - 20);
				if (i < 2)
				{
					int x1 = moduleX + moduleW + 3;
					int x2 = moduleX + moduleW + 14;
					int cy = moduleY + 15;
					g.setColor(new Color(183, 195, 214));
					g.setStroke(new BasicStroke(2f));
					g.drawLine(x1, cy, x2, cy);
					g.drawLine(x2 - 4, cy - 4, x2, cy);
					g.drawLine(x2 - 4, cy + 4, x2, cy);
				}
			}
		}

		private void drawMiniConcept(Graphics2D g, int x, int y, int w, int h,
				String text, Color color)
		{
			g.setColor(tint(color, 0.82f));
			g.fillRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setColor(tint(color, 0.38f));
			g.drawRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setColor(color);
			g.fill(new Ellipse2D.Double(x + 10, y + 12, 10, 10));
			g.setFont(font(Font.BOLD, 12));
			g.setColor(TEXT);
			drawWrapped(g, text, x + 28, y + 18, w - 38, 3, 15);
		}

		private static void drawStage(Graphics2D g, int x, int y, int w, int h,
				String title, String detail, String[] steps, Color color)
		{
			g.setColor(SURFACE);
			g.fillRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setColor(tint(color, 0.45f));
			g.drawRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setColor(tint(color, 0.15f));
			g.fillRoundRect(x, y, 7, h, RADIUS, RADIUS);
			g.fillRect(x + 4, y, 5, h);
			g.setColor(color);
			g.fill(new Ellipse2D.Double(x + 12, y + 14, 10, 10));
			g.setFont(font(Font.BOLD, 12));
			g.setColor(TEXT);
			drawClippedString(g, title, x + 30, y + 24, w - 38);
			g.setFont(font(Font.PLAIN, 10));
			g.setColor(MUTED);
			drawClippedString(g, detail, x + 12, y + 48, w - 24);
			g.setFont(font(Font.BOLD, 9));
			g.setColor(color.darker());
			g.drawString("INSIDE THIS STEP", x + 12, y + 68);
			int chipY = y + 78;
			int maxSteps = h >= 156 ? 3 : h >= 126 ? 2 : 1;
			for (int i = 0; i < Math.min(maxSteps, steps.length); i++)
			{
				g.setColor(tint(color, 0.88f));
				g.fillRoundRect(x + 12, chipY, w - 24, 22, 9, 9);
				g.setColor(tint(color, 0.58f));
				g.drawRoundRect(x + 12, chipY, w - 24, 22, 9, 9);
				g.setFont(font(Font.PLAIN, 9));
				g.setColor(TEXT);
				drawClippedString(g, steps[i], x + 20, chipY + 15, w - 40);
				chipY += 27;
			}
			if (h >= 118)
			{
				g.setColor(tint(color, 0.88f));
				g.fillRoundRect(x + 12, y + h - 18, w - 24, 5, 5, 5);
				g.setColor(color);
				g.fillRoundRect(x + 12, y + h - 18,
						Math.max(18, (int) ((w - 24) * 0.68)), 5, 5, 5);
			}
		}

		private static void drawAlgorithmGlyph(Graphics2D g, int x, int y, int w, int h, Snapshot s)
		{
			g.setColor(glass(230));
			g.fillRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setColor(new Color(220, 228, 242));
			g.drawRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setFont(font(Font.BOLD, 11));
			g.setColor(PURPLE.darker());
			drawClippedString(g, s.algorithmName, x + 12, y + 20, w - 24);
			g.setFont(font(Font.PLAIN, 10));
			g.setColor(MUTED);
			drawClippedString(g, internalAlgorithmMeaning(s),
					x + 12, y + 38, w - 24);
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
			int count = Math.min(snapshot.nodes, 10);
			if (count == 0)
			{
				count = 1;
			}
			g.setFont(font(Font.BOLD, 10));
			g.setColor(BLUE.darker());
			g.drawString("PARALLEL NODE LANES", 12, 18);
			g.setFont(font(Font.PLAIN, 10));
			g.setColor(MUTED);
			drawClippedString(g,
					"Nodes work in parallel, then the organizer combines their candidate-score maps.",
						12,
						34,
						w - 120);
			drawMergeRecipe(g, 12, 45, w - 24, 58);
			int diagramY = 118;
			int diagramH = Math.max(128, h - diagramY - 30);
			drawWorkerFrame(g, 12, diagramY, w - 24, diagramH);
			int hubX = w - 92;
			int hubY = diagramY + diagramH / 2 + 10;
			int nodeAreaX = 30;
			int nodeAreaY = diagramY + 44;
			int nodeAreaW = Math.max(120, hubX - nodeAreaX - 72);
			int nodeAreaH = Math.max(70, diagramH - 62);
			int rows = count == 1 ? 1 : count <= 4 ? 1 : count <= 8 ? 2 : 3;
			int cols = (int) Math.ceil(count / (double) rows);
			int gap = 10;
			int cardW = count == 1
					? Math.min(190, Math.max(132, nodeAreaW / 2))
					: Math.max(72, Math.min(110,
							(nodeAreaW - gap * Math.max(0, cols - 1)) / Math.max(1, cols)));
			int cardH = count == 1
					? Math.min(98, Math.max(72, nodeAreaH - 12))
					: Math.max(44, Math.min(62,
							(nodeAreaH - gap * Math.max(0, rows - 1)) / Math.max(1, rows)));
			int gridW = count == 1
					? cardW
					: cols * cardW + gap * Math.max(0, cols - 1);
			int gridH = rows * cardH + gap * Math.max(0, rows - 1);
			int startX = count == 1 ? nodeAreaX + 18 : nodeAreaX + Math.max(0, (nodeAreaW - gridW) / 2);
			int startY = nodeAreaY + Math.max(0, (nodeAreaH - gridH) / 2);
			for (int i = 0; i < count; i++)
			{
				int row = rows == 1 ? 0 : i / cols;
				int col = rows == 1 ? i : i % cols;
				int x = startX + col * (cardW + gap);
				int y = startY + row * (cardH + gap);
				Color color = i % 2 == 0 ? BLUE : GREEN;
				g.setColor(alpha(color, 42));
				g.setStroke(new BasicStroke(1.5f));
				g.drawLine(x + cardW, y + cardH / 2, hubX - 48, hubY);
				double t = (phase + i * 0.11) % 1.0;
				int px = (int) (x + cardW + (hubX - 48 - x - cardW) * t);
				int py = (int) (y + cardH / 2 + (hubY - y - cardH / 2) * t);
					g.setColor(alpha(color, 145));
					g.fill(new Ellipse2D.Double(px - 3, py - 3, 6, 6));
				drawNodeCard(g, x, y, cardW, cardH, i, color, count == 1);
			}
			drawMergeHub(g, hubX, hubY);
			drawMergeOutcome(g, 24, diagramY + diagramH - 34, hubX - 90, 24);
			g.setFont(font(Font.PLAIN, 10));
			g.setColor(MUTED);
			drawClippedString(g, snapshot.nodes > count
					? "+" + (snapshot.nodes - count) + " more nodes represented"
					: "merge keeps the highest ranked candidates for the target",
					12, h - 12, w - 24);
			g.dispose();
		}

		private void drawMergeRecipe(Graphics2D g, int x, int y, int w, int h)
		{
			String[] labels = {
					"1 receive rows",
					"2 score candidates",
					"3 send score map",
					"4 merge + sort"
			};
			String[] details = {
					"node shard",
					internalAlgorithmMeaning(snapshot),
					"candidate -> score",
					"top " + snapshot.topRecommendations
			};
			Color[] colors = {BLUE, PURPLE, AMBER, GREEN};
			int gap = 8;
			int boxW = Math.max(78, (w - gap * 3) / 4);
			for (int i = 0; i < 4; i++)
			{
				int bx = x + i * (boxW + gap);
				g.setColor(SURFACE);
				g.fillRoundRect(bx, y, boxW, h, RADIUS, RADIUS);
				g.setColor(QUIET_BORDER);
				g.drawRoundRect(bx, y, boxW, h, RADIUS, RADIUS);
				g.setColor(alpha(colors[i], 62));
				g.fillRoundRect(bx, y, 6, h, RADIUS, RADIUS);
				g.fillRect(bx + 3, y, 5, h);
				g.setFont(font(Font.BOLD, 10));
				g.setColor(TEXT);
				drawClippedString(g, labels[i], bx + 9, y + 19, boxW - 18);
				g.setFont(font(Font.PLAIN, 9));
				g.setColor(MUTED);
				drawClippedString(g, details[i], bx + 9, y + 38, boxW - 18);
				if (i < 3)
				{
					int ax = bx + boxW + 1;
					int ay = y + h / 2;
					g.setColor(new Color(183, 195, 214));
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
			g.setColor(QUIET_FILL);
			g.fillRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setColor(QUIET_BORDER);
			g.drawRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setFont(font(Font.BOLD, 9));
			g.setColor(MUTED);
			g.drawString(snapshot.nodes == 1
					? "SINGLE NODE SCORE MAP"
					: "WORKER NODE OUTPUTS", x + 12, y + 18);
			g.setFont(font(Font.PLAIN, 10));
			g.setColor(MUTED);
			drawClippedString(g,
					snapshot.nodes == 1
							? "One recommender builds one candidate-score map, then the organizer still applies the same top-result merge step."
							: "Each recommender returns a candidate-score map; the organizer deduplicates and sorts them.",
					x + 12,
					y + 36,
					w - 24);
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
			g.fillRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setColor(quietStroke(color));
			g.drawRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setColor(alpha(color, 64));
			g.fillRoundRect(x, y, 7, h, RADIUS, RADIUS);
			g.fillRect(x + 4, y, 5, h);
			g.setFont(font(Font.BOLD, 10));
			g.setColor(TEXT);
			drawClippedString(g, "Node " + (index + 1), x + 10, y + 17, w - 20);
			g.setFont(font(Font.PLAIN, 9));
			g.setColor(MUTED);
			drawClippedString(g, single
					? "full target workload"
					: index == 0 ? "target + shard" : "candidate shard",
					x + 10,
					y + 33,
					w - 20);
			if (h >= 60)
			{
				g.setFont(font(Font.BOLD, 9));
				g.setColor(MUTED);
				drawClippedString(g, internalAlgorithmMeaning(snapshot),
						x + 10,
						y + 49,
						w - 20);
			}
			g.setColor(TRACK);
			g.fillRoundRect(x + 10, y + h - 12, w - 20, 5, 5, 5);
			g.setColor(alpha(color, 170));
			int progress = Math.max(12, (int) ((w - 20) * (single ? 0.72 : 0.44 + (index % 4) * 0.12)));
			g.fillRoundRect(x + 10, y + h - 12, progress, 5, 5, 5);
		}

		private void drawMergeOutcome(Graphics2D g, int x, int y, int w, int h)
		{
			if (w <= 80 || h <= 16)
			{
				return;
			}
			g.setColor(SURFACE);
			g.fillRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setColor(QUIET_BORDER);
			g.drawRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setColor(alpha(GREEN, 150));
			g.fillOval(x + 10, y + h / 2 - 4, 8, 8);
			g.setFont(font(Font.BOLD, 10));
			g.setColor(TEXT);
			drawClippedString(g,
					"Organizer keeps the strongest top "
							+ snapshot.topRecommendations
							+ " " + snapshot.candidateTypeLabel().toLowerCase(Locale.US)
							+ " after duplicate candidates are combined.",
					x + 26,
					y + h / 2 + 4,
					w - 36);
		}

		private void drawMergeHub(Graphics2D g, int hubX, int hubY)
		{
			g.setColor(SURFACE);
			g.fill(new Ellipse2D.Double(hubX - 46, hubY - 46, 92, 92));
			g.setColor(quietStroke(GREEN));
			g.setStroke(new BasicStroke(2f));
			g.draw(new Ellipse2D.Double(hubX - 46, hubY - 46, 92, 92));
			g.setColor(alpha(GREEN, 34));
			g.fill(new Ellipse2D.Double(hubX - 37, hubY - 37, 74, 74));
			g.setFont(font(Font.BOLD, 12));
			g.setColor(TEXT);
			drawClippedString(g, "MERGE", hubX - 24, hubY - 17, 52);
			g.setFont(font(Font.BOLD, 9));
			g.setColor(GREEN.darker());
			drawClippedString(g, "dedupe", hubX - 20, hubY + 2, 50);
			drawClippedString(g, "sort", hubX - 13, hubY + 17, 42);
			drawClippedString(g, "top " + snapshot.topRecommendations,
					hubX - 21, hubY + 32, 56);
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
			String target = preview.targetName.length() > 0
					? preview.targetName
					: snapshot.primaryTargetName();
			if (target.length() == 0)
			{
				target = "Selected target";
			}
			drawTarget(g, 12, 8, w - 24, 58, target);
			drawInputTokens(g, 12, 76, w - 24, 90);
			drawPredictionMeaning(g, 12, 176, w - 24, 50);
			int y = 238;
			List<String> candidates = preview.candidatesFor(snapshot.outputType, target);
			if (candidates.isEmpty())
			{
				drawEmptyPrediction(g, 12, y, w - 24, h - y - 12);
			}
			else
			{
				int rowH = Math.max(42, (h - y - 14) / Math.min(4, candidates.size()));
				for (int i = 0; i < Math.min(4, candidates.size()); i++)
				{
					drawCandidate(g, 12, y + i * rowH, w - 24, rowH - 8,
							i + 1,
							candidates.get(i),
							0.88f - i * 0.14f);
				}
			}
			g.setFont(font(Font.BOLD, 9));
			g.setColor(MUTED);
			drawClippedString(g,
					"Preview candidates are sampled from the dataset. Real ranking and scores are produced only by Run Simulation.",
					12,
					h - 4,
					w - 24);
			g.dispose();
		}

		private void drawTarget(Graphics2D g, int x, int y, int w, int h, String target)
		{
			g.setColor(SURFACE);
			g.fillRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setColor(QUIET_BORDER);
			g.drawRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setColor(alpha(BLUE, 185));
			g.fill(new Ellipse2D.Double(x + 14, y + 16, 42, 42));
			g.setFont(font(Font.BOLD, 15));
			g.setColor(SURFACE);
			String initials = target.length() >= 2 ? target.substring(0, 2).toUpperCase(Locale.US) : "T";
			g.drawString(initials, x + 25, y + 43);
			g.setFont(font(Font.BOLD, 10));
			g.setColor(MUTED);
			g.drawString("TARGET USER", x + 68, y + 24);
			g.setFont(font(Font.BOLD, 15));
			g.setColor(TEXT);
			drawClippedString(g, target, x + 68, y + 47, w - 82);
		}

		private void drawInputTokens(Graphics2D g, int x, int y, int w, int h)
		{
			g.setColor(SURFACE_ALT);
			g.fillRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setColor(BORDER);
			g.drawRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setFont(font(Font.BOLD, 10));
			g.setColor(MUTED);
			g.drawString("WHAT THE MODEL READS: COLUMN 6 TEXT", x + 12, y + 18);
			String text = exampleText();
			g.setFont(font(Font.BOLD, 11));
			g.setColor(TEXT);
			drawClippedString(g, text.length() == 0
							? "No selected-target row loaded yet."
							: compact(text, 82),
					x + 12,
					y + 39,
					w - 24);
			List<String> terms = tokensFromText(text);
			if (terms.isEmpty())
			{
				terms = preview.terms;
			}
			int chipX = x + 12;
			int chipY = y + 54;
			for (int i = 0; i < Math.min(6, terms.size()); i++)
			{
				String term = terms.get(i);
				int chipW = Math.min(96, Math.max(42, g.getFontMetrics().stringWidth(term) + 18));
				if (chipX + chipW > x + w - 10)
				{
					break;
				}
				g.setColor(SURFACE);
				g.fillRoundRect(chipX, chipY, chipW, 24, 14, 14);
				g.setColor(QUIET_BORDER);
				g.drawRoundRect(chipX, chipY, chipW, 24, 14, 14);
				g.setFont(font(Font.BOLD, 10));
				g.setColor(PURPLE.darker());
				drawClippedString(g, term, chipX + 9, chipY + 16, chipW - 18);
				chipX += chipW + 7;
			}
			if (terms.isEmpty())
			{
				g.setColor(MUTED);
				g.setFont(font(Font.PLAIN, 11));
				drawClippedString(g, "No text tokens loaded yet.", x + 12, y + 73, w - 24);
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
			g.setColor(quietStroke(color));
			g.drawRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setColor(alpha(color, 56));
			g.fillRoundRect(x + 9, y + 10, 4, h - 20, 4, 4);
			g.setFont(font(Font.BOLD, 10));
			g.setColor(MUTED);
			g.drawString("WHAT WILL BE RANKED", x + 22, y + 17);
			g.setFont(font(Font.BOLD, 12));
			g.setColor(TEXT);
			drawClippedString(g, outputMeaning(snapshot), x + 22, y + 36, w - 32);
		}

		private void drawCandidate(Graphics2D g, int x, int y, int w, int h,
				int rank, String name, float strength)
		{
			Color color = rank == 1 ? GREEN : rank == 2 ? PURPLE : BLUE;
			g.setColor(SURFACE);
			g.fillRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setColor(QUIET_BORDER);
			g.drawRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setColor(alpha(color, 170));
			g.fillRoundRect(x + 10, y + 10, 28, h - 20, 6, 6);
			g.setFont(font(Font.BOLD, 12));
			g.setColor(SURFACE);
			g.drawString(Integer.toString(rank), x + 20, y + h / 2 + 5);
			g.setFont(font(Font.BOLD, 13));
			g.setColor(TEXT);
			drawClippedString(g, name, x + 48, y + 22, w - 62);
			g.setFont(font(Font.PLAIN, 10));
			g.setColor(MUTED);
			drawClippedString(g, snapshot.outputType == RecommendationCandidateType.FOLLOWEE
					? "real label from column 1" : "real user from column 5",
					x + 48, y + 38, w - 62);
			int barW = Math.max(10, (int) ((w - 70) * strength));
			g.setColor(TRACK);
			g.fillRoundRect(x + 48, y + h - 12, w - 64, 4, 4, 4);
			g.setColor(alpha(color, 170));
			g.fillRoundRect(x + 48, y + h - 12, barW, 4, 4, 4);
		}

		private void drawEmptyPrediction(Graphics2D g, int x, int y, int w, int h)
		{
			g.setColor(SURFACE);
			g.fillRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setColor(quietStroke(AMBER));
			g.drawRoundRect(x, y, w, h, RADIUS, RADIUS);
			g.setFont(font(Font.BOLD, 13));
			g.setColor(AMBER.darker());
			drawClippedString(g, "Prediction preview waiting for dataset rows", x + 14, y + 28, w - 28);
			g.setFont(font(Font.PLAIN, 11));
			g.setColor(MUTED);
			drawClippedString(g, preview.message, x + 14, y + 49, w - 28);
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
			g.setFont(font(Font.BOLD, 10));
			g.setColor(BLUE.darker());
			g.drawString("RUN CONTRACT", 8, 15);
			g.setFont(font(Font.PLAIN, 10));
			g.setColor(MUTED);
			drawClippedString(g,
					"These are copied from the Simulator tab. Changing Simulator controls redraws this guide.",
					8,
					31,
					w - 16);
			String[] labels = {
					snapshot.parameterProfile,
					snapshot.batchEnabled ? "Batch " + snapshot.batchUsers : "Single target",
					snapshot.graphEnabled ? "Graph export on" : "Graph export off",
					snapshot.heapMb + " MB heap"
			};
			Color[] colors = {PURPLE, BLUE, snapshot.graphEnabled ? GREEN : AMBER, GREEN};
			int chipH = 34;
			int y = 42;
			for (int i = 0; i < labels.length; i++)
			{
				int x = i % 2 == 0 ? 8 : w / 2 + 4;
				if (i == 2)
				{
					y += chipH + 8;
				}
				int chipW = w / 2 - 14;
					g.setColor(SURFACE);
					g.fillRoundRect(x, y, chipW, chipH, RADIUS, RADIUS);
					g.setColor(alpha(colors[i], 46));
					g.fillRoundRect(x + 8, y + 8, 4, chipH - 16, 4, 4);
					g.setColor(QUIET_BORDER);
					g.drawRoundRect(x, y, chipW, chipH, RADIUS, RADIUS);
					g.setFont(font(Font.BOLD, 11));
					g.setColor(TEXT);
					drawClippedString(g, labels[i], x + 18, y + 22, chipW - 26);
			}
			int noteY = y + chipH + 18;
			drawContractLine(g, 10, noteY, w - 20, "Input", inputMeaning(snapshot), PURPLE);
			drawContractLine(g, 10, noteY + 26, w - 20, "Output", outputMeaning(snapshot), GREEN);
			drawContractLine(g, 10, noteY + 52, w - 20, "Processing", cleaningSummary(snapshot), BLUE);
			drawContractLine(g, 10, noteY + 78, w - 20, "Graph",
					snapshot.graphEnabled
							? "edge data plus graph display/export"
							: "edge data retained, graph display skipped",
					snapshot.graphEnabled ? GREEN : AMBER);
			drawContractLine(g, 10, noteY + 104, w - 20, "Meaning",
					beginnerQuestion(snapshot),
					PURPLE);
			drawContractLine(g, 10, noteY + 130, w - 20, "Capacity",
					snapshot.capacitySummary,
					AMBER);
			g.setFont(font(Font.BOLD, 9));
			g.setColor(MUTED);
			drawClippedString(g, cleaningSummary(snapshot) + " | "
					+ compact(snapshot.parameterSummary, 90),
					10,
					h - 12,
					w - 20);
			g.dispose();
		}

		private void drawContractLine(Graphics2D g, int x, int y, int w,
				String label, String value, Color color)
		{
			g.setColor(SURFACE);
			g.fillRoundRect(x, y - 15, w, 22, 6, 6);
			g.setColor(alpha(color, 38));
			g.fillRoundRect(x + 6, y - 10, 3, 12, 3, 3);
			g.setColor(QUIET_BORDER);
			g.drawRoundRect(x, y - 15, w, 22, 6, 6);
			g.setFont(font(Font.BOLD, 9));
			g.setColor(MUTED);
			drawClippedString(g, label.toUpperCase(Locale.US), x + 15, y, 62);
			g.setFont(font(Font.PLAIN, 10));
			g.setColor(TEXT);
			drawClippedString(g, value, x + 82, y, w - 90);
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
				return "Linear SVM";
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

	private static String algorithmPlainLanguage(Snapshot s)
	{
		switch (s.algorithm)
		{
			case RecommendationModeSupport.COS_SIM:
				return "compare word overlap and direction";
			case RecommendationModeSupport.K_MEANS:
				return "group similar text profiles into clusters";
				case RecommendationModeSupport.K_MEANS_EUCLIDEAN:
					return "group nearby text profiles by distance";
				case RecommendationModeSupport.COMMON_NEIGHBORS:
					return "count shared text terms as common neighbors";
				case RecommendationModeSupport.SVM:
					return "learn which text patterns point to each label";
			case RecommendationModeSupport.SCALABLE_SVM:
				return "learn a sparse linear text classifier";
			case RecommendationModeSupport.MLP:
				return "learn text patterns with a neural network";
			case RecommendationModeSupport.DOC2VEC:
				return "turn each user's text into a learned vector";
			case RecommendationModeSupport.TEMPORAL_GRAPH_FOUNDATION:
				return "fuse text, graph paths, timing, bridge value, and debias signals";
			default:
				return "use a legacy graph relationship rule";
		}
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
				return "sparse linear class scores";
			case RecommendationModeSupport.MLP:
				return "neural class scores";
			case RecommendationModeSupport.DOC2VEC:
				return "document-vector similarity";
			case RecommendationModeSupport.TEMPORAL_GRAPH_FOUNDATION:
				return "text + graph + time foundation score";
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

		String[] sampleColumnValues()
		{
			DsmpRow row = rows.isEmpty() ? null : rows.get(0);
			if (row == null)
			{
				return new String[] {"-", "-", "-", "-", "-", "-"};
			}
			return new String[] {
					compact(row.followee, 18),
					compact(row.postId, 18),
					compact(row.date, 18),
					compact(row.userId, 18),
					compact(row.userName, 18),
					compact(row.text, 18)
			};
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
