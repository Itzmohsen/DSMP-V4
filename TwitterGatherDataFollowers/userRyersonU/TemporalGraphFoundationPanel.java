package TwitterGatherDataFollowers.userRyersonU;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JSlider;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

final class TemporalGraphFoundationPanel extends JPanel
{
	// Nebula adaptive tokens: every constant resolves day/night at paint time,
	// because this tab is exempt from the night-mode component walk.
	private static final Color BACKGROUND = StudioNightTheme.adaptiveColor(
			NebulaDesign.DAY_CANVAS_TOP, NebulaDesign.NIGHT_CANVAS_TOP);
	private static final Color SURFACE = NebulaDesign.SURFACE;
	private static final Color SURFACE_ALT = NebulaDesign.SURFACE_ALT;
	private static final Color PANEL_INK = StudioNightTheme.adaptiveColor(
			NebulaDesign.DAY_SURFACE_ALT, NebulaDesign.NIGHT_SURFACE_RAISED);
	private static final Color BORDER = NebulaDesign.BORDER_SUBTLE;
	private static final Color BORDER_STRONG = NebulaDesign.BORDER_STRONG;
	private static final Color TEXT = NebulaDesign.TEXT_PRIMARY;
	private static final Color MUTED = NebulaDesign.TEXT_SECONDARY;
	private static final Color FAINT = NebulaDesign.TEXT_TERTIARY;
	// Data-encoding hues: signal identities and status tones map to the
	// semantic Nebula palette so meaning survives the theme switch.
	private static final Color BLUE = NebulaDesign.ACCENT;
	private static final Color GREEN = NebulaDesign.SUCCESS;
	private static final Color AMBER = NebulaDesign.WARNING;
	private static final Color RED = NebulaDesign.DANGER;
	private static final Color PURPLE = NebulaDesign.PURPLE;
	private static final Color TEAL = NebulaDesign.INFO;
	private static final Color PINK = NebulaDesign.PINK;
	// Meter tracks, inner plates, and wells over the painted canvases.
	private static final Color TRACK = StudioNightTheme.adaptiveColor(
			NebulaDesign.DAY_BORDER_SUBTLE, NebulaDesign.NIGHT_SURFACE_RAISED);
	private static final Color PLATE = StudioNightTheme.adaptiveColor(
			NebulaDesign.DAY_SURFACE, NebulaDesign.NIGHT_INSET);
	private static final Color INK = StudioNightTheme.adaptiveColor(
			NebulaDesign.DAY_SURFACE_ALT, NebulaDesign.NIGHT_INSET);
	private static final NumberFormat COUNT_FORMAT =
			NumberFormat.getIntegerInstance(Locale.US);

	private final ControllerAgentGui controller;
	private final JLabel statusLabel =
			new JLabel("Waiting for a Temporal Graph Foundation run.");
	private final JLabel runMetric = metricValue("--");
	private final JLabel rowsMetric = metricValue("--");
		private final JLabel datedMetric = metricValue("--");
			private final JLabel graphMetric = metricValue("--");
			private final JLabel targetsMetric = metricValue("--");
			private final JLabel eventsMetric = metricValue("--");
			private final JLabel researchMetric = metricValue("--");
			private final JLabel coverageMetric = metricValue("--");
			private final JLabel fragilityMetric = metricValue("--");
			private final JLabel dossierReliability = dossierValue("--");
			private final JLabel dossierCoverage = dossierValue("--");
			private final JLabel dossierFragility = dossierValue("--");
			private final JLabel dossierGap = dossierValue("--");
			private final JLabel dossierWhy = dossierText("--");
			private final JLabel dossierWhyNot = dossierText("--");
			private final JLabel dossierRisks = dossierText("--");
			private final JLabel dossierEvidence = dossierText("--");
		private final DefaultListModel<TemporalGraphFoundationModelSupport.TargetReport>
				targetModel =
				new DefaultListModel<TemporalGraphFoundationModelSupport.TargetReport>();
	private final JList<TemporalGraphFoundationModelSupport.TargetReport>
			targetList =
			new JList<TemporalGraphFoundationModelSupport.TargetReport>(
					targetModel);
	private final DefaultListModel<TemporalGraphFoundationModelSupport.CandidateScore>
			candidateModel =
			new DefaultListModel<TemporalGraphFoundationModelSupport.CandidateScore>();
	private final JList<TemporalGraphFoundationModelSupport.CandidateScore>
			candidateList =
				new JList<TemporalGraphFoundationModelSupport.CandidateScore>(
						candidateModel);
		private final FoundationCanvas foundationCanvas = new FoundationCanvas();
		private final ResearchCanvas researchCanvas = new ResearchCanvas();
		private final ReplayScrubber replayScrubber = new ReplayScrubber();
		private final JSlider timeSlider = new JSlider(0, 11, 11);
		private final JLabel timeReadout = new JLabel("final frame");
		private final JCheckBox removeGraphBox = controlToggle("Graph off");
		private final JCheckBox removeTimeBox = controlToggle("Time off");
		private final JCheckBox removeBridgeBox = controlToggle("Bridge off");
		private final JCheckBox removeDebiasBox = controlToggle("Debias off");
		private final JCheckBox removeTopTermBox = controlToggle("Top term off");
		private final Timer animationTimer;
		private TemporalGraphFoundationModelSupport.Result latestResult;
		private long visibleRevision = -1L;
		private java.awt.image.BufferedImage canvasCache;
		private boolean canvasCacheNight;

	TemporalGraphFoundationPanel(ControllerAgentGui controller)
	{
		super(new BorderLayout());
		this.controller = controller;
		setBackground(BACKGROUND);
		JPanel content = new WidthTrackingPage(new BorderLayout(14, 14));
		content.setOpaque(false);
		content.setBorder(new EmptyBorder(14, 14, 14, 14));
		content.add(createHeader(), BorderLayout.NORTH);
		content.add(createWorkspace(), BorderLayout.CENTER);
		content.add(createFooter(), BorderLayout.SOUTH);
		JScrollPane pageScroll = new JScrollPane(content);
		pageScroll.setBorder(BorderFactory.createEmptyBorder());
		pageScroll.setVerticalScrollBarPolicy(
				ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
		pageScroll.setHorizontalScrollBarPolicy(
				ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		pageScroll.setOpaque(false);
		pageScroll.getViewport().setOpaque(false);
		pageScroll.getViewport().setScrollMode(
				javax.swing.JViewport.SIMPLE_SCROLL_MODE);
		pageScroll.getViewport().setBackground(BACKGROUND);
		pageScroll.setBackground(BACKGROUND);
		pageScroll.getVerticalScrollBar().setUnitIncrement(18);
		pageScroll.getVerticalScrollBar().setOpaque(false);
		pageScroll.getVerticalScrollBar().setUI(
				new NebulaWidgets.PillScrollBarUI());
		pageScroll.getHorizontalScrollBar().setOpaque(false);
		pageScroll.getHorizontalScrollBar().setUI(
				new NebulaWidgets.PillScrollBarUI());
		add(pageScroll, BorderLayout.CENTER);
		installListeners();
		animationTimer = new Timer(40, new ActionListener()
		{
			public void actionPerformed(ActionEvent event)
			{
				if (foundationCanvas.isShowing())
				{
					foundationCanvas.advance();
				}
			}
		});
		animationTimer.start();
		refreshFromRegistry();
	}

	void stopAnimationForTest()
	{
		animationTimer.stop();
	}

	protected void paintComponent(Graphics graphics)
	{
		super.paintComponent(graphics);
		int width = getWidth();
		int height = getHeight();
		if (width <= 0 || height <= 0)
		{
			return;
		}
		boolean night = StudioNightTheme.isNightMode();
		if (canvasCache == null
				|| canvasCache.getWidth() != width
				|| canvasCache.getHeight() != height
				|| canvasCacheNight != night)
		{
			if (canvasCache != null)
			{
				canvasCache.flush();
			}
			canvasCacheNight = night;
			canvasCache = StudioNightTheme.renderCanvas(width, height, night);
		}
		graphics.drawImage(canvasCache, 0, 0, null);
	}

	public void doLayout()
	{
		super.doLayout();
		Component[] children = getComponents();
		for (int i = 0; i < children.length; i++)
		{
			layoutDescendants(children[i]);
		}
	}

	private static void layoutDescendants(Component component)
	{
		if (component instanceof Container)
		{
			Container container = (Container)component;
			container.doLayout();
			Component[] children = container.getComponents();
			for (int i = 0; i < children.length; i++)
			{
				layoutDescendants(children[i]);
			}
		}
	}

	void refreshFromRegistry()
	{
		if (!SwingUtilities.isEventDispatchThread())
		{
			SwingUtilities.invokeLater(new Runnable()
			{
				public void run()
				{
					refreshFromRegistry();
				}
			});
			return;
		}
		long revision = TemporalGraphFoundationRegistry.revision();
		TemporalGraphFoundationModelSupport.Result result =
				TemporalGraphFoundationRegistry.latest();
		if (revision == visibleRevision && result == latestResult)
		{
			foundationCanvas.repaint();
			return;
		}
		visibleRevision = revision;
		latestResult = result;
		targetModel.clear();
		candidateModel.clear();
		updateMetrics(result);
			if (result == null)
			{
				statusLabel.setText("No Temporal Graph Foundation output yet.");
				updateStudioSelection();
				return;
			}
		for (TemporalGraphFoundationModelSupport.TargetReport report
				: result.getReports().values())
		{
			targetModel.addElement(report);
		}
		if (targetModel.getSize() > 0)
		{
			targetList.setSelectedIndex(0);
		}
			else
			{
				statusLabel.setText(result.getSummary());
				updateStudioSelection();
			}
		}

	/**
	 * Page container that fits the viewport width (squeezing the KPI strip and
	 * workspace instead of spawning a horizontal scrollbar) while keeping
	 * vertical scrolling. Very narrow viewports still fall back to horizontal
	 * scrolling so fixed-size canvases stay usable.
	 */
	private static final class WidthTrackingPage extends JPanel
			implements javax.swing.Scrollable
	{
		WidthTrackingPage(java.awt.LayoutManager layout)
		{
			super(layout);
		}

		public Dimension getPreferredScrollableViewportSize()
		{
			return getPreferredSize();
		}

		public int getScrollableUnitIncrement(
				java.awt.Rectangle visible, int orientation, int direction)
		{
			return 18;
		}

		public int getScrollableBlockIncrement(
				java.awt.Rectangle visible, int orientation, int direction)
		{
			return 130;
		}

		public boolean getScrollableTracksViewportWidth()
		{
			java.awt.Container parent = getParent();
			return parent instanceof javax.swing.JViewport
					&& parent.getWidth() >= 1000;
		}

		public boolean getScrollableTracksViewportHeight()
		{
			return false;
		}
	}

	private Component createHeader()
	{
		JPanel header = new HeroPanel(new BorderLayout(18, 14));
		header.setBorder(new EmptyBorder(18, 22, 18, 22));

		JPanel titleStack = new JPanel(new BorderLayout(0, 3));
		titleStack.setOpaque(false);
		JLabel eyebrow = new JLabel("DECISION INTELLIGENCE");
		eyebrow.setFont(NebulaDesign.fontSection());
		eyebrow.setForeground(BLUE);
		JLabel title = new JLabel("Temporal Graph Foundation Studio");
		title.setFont(NebulaDesign.fontDisplay());
		title.setForeground(TEXT);
		JLabel subtitle = new JLabel(
				"Decision intelligence cockpit for temporal evidence, causal stress tests, uncertainty, and reproducibility.");
		subtitle.setFont(NebulaDesign.fontBody(13f));
		subtitle.setForeground(MUTED);
		titleStack.add(eyebrow, BorderLayout.NORTH);
		titleStack.add(title, BorderLayout.CENTER);
		titleStack.add(subtitle, BorderLayout.SOUTH);
		header.add(titleStack, BorderLayout.WEST);

		JPanel actions = new JPanel(new GridLayout(2, 1, 0, 8));
		actions.setOpaque(false);
		JButton refresh = new NebulaButton("Refresh run", true, BLUE);
		refresh.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent event)
			{
				visibleRevision = -1L;
				refreshFromRegistry();
			}
		});
		JButton export = new NebulaButton("Export capsule", false, GREEN);
		export.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent event)
			{
				exportResearchCapsule();
			}
		});
		actions.add(refresh);
		actions.add(export);
		header.add(actions, BorderLayout.EAST);

		header.add(createMetrics(), BorderLayout.SOUTH);
		return header;
	}

		private Component createMetrics()
		{
				JPanel metrics = new JPanel(new GridLayout(1, 9, 10, 0));
				metrics.setOpaque(false);
				metrics.add(metricCard("RUN", runMetric, BLUE));
				metrics.add(metricCard("ROWS", rowsMetric, GREEN));
				metrics.add(metricCard("DATED", datedMetric, TEAL));
				metrics.add(metricCard("GRAPH EDGES", graphMetric, AMBER));
				metrics.add(metricCard("TARGETS", targetsMetric, PURPLE));
				metrics.add(metricCard("TRACE FRAMES", eventsMetric, RED));
				metrics.add(metricCard("HIT@1", researchMetric, GREEN));
				metrics.add(metricCard("EVIDENCE", coverageMetric, TEAL));
				metrics.add(metricCard("FRAGILE", fragilityMetric, PINK));
				return metrics;
			}

	private Component createWorkspace()
	{
		targetList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		targetList.setCellRenderer(new TargetRenderer());
		targetList.setFixedCellHeight(72);
		targetList.setBorder(new EmptyBorder(8, 8, 8, 8));
		targetList.setBackground(SURFACE);

		candidateList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		candidateList.setCellRenderer(new CandidateRenderer());
		candidateList.setFixedCellHeight(92);
		candidateList.setBorder(new EmptyBorder(8, 8, 8, 8));
		candidateList.setBackground(SURFACE);

		JPanel left = surfacePanel(new BorderLayout(0, 8));
		left.add(sectionHeader("Targets", "latest scored profiles"), BorderLayout.NORTH);
		left.add(scroller(targetList), BorderLayout.CENTER);

			JPanel right = new JPanel(new BorderLayout(12, 12));
			right.setOpaque(false);
			JPanel cockpit = new JPanel(new BorderLayout(0, 10));
			cockpit.setOpaque(false);
			cockpit.add(foundationCanvas, BorderLayout.CENTER);
			cockpit.add(createResearchDock(), BorderLayout.SOUTH);
			right.add(cockpit, BorderLayout.CENTER);
				JPanel candidatePanel = surfacePanel(new BorderLayout(0, 8));
				candidatePanel.setPreferredSize(new Dimension(340, 10));
				candidatePanel.add(sectionHeader("Top Candidates", "ranked explanations"),
						BorderLayout.NORTH);
			candidatePanel.add(scroller(candidateList), BorderLayout.CENTER);
			candidatePanel.add(createDecisionDossier(), BorderLayout.SOUTH);
			right.add(candidatePanel, BorderLayout.EAST);

		JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, right);
			split.setBorder(BorderFactory.createEmptyBorder());
			split.setDividerSize(8);
			split.setResizeWeight(0.23);
			split.setPreferredSize(new Dimension(1420, 1320));
			split.setOpaque(false);
				return split;
			}

			private Component createDecisionDossier()
			{
				JPanel dossier = new RoundedPanel(PANEL_INK);
				dossier.setLayout(new BorderLayout(0, 9));
				dossier.setBorder(new EmptyBorder(12, 12, 12, 12));
				dossier.setPreferredSize(new Dimension(320, 324));

				JPanel header = new JPanel(new BorderLayout());
				header.setOpaque(false);
				JLabel title = new JLabel("Decision Dossier");
				title.setFont(NebulaDesign.fontSemiBold(13f));
				title.setForeground(TEXT);
				JLabel mode = new JLabel("why / why-not / limits");
				mode.setFont(NebulaDesign.fontMedium(10f));
				mode.setForeground(FAINT);
				header.add(title, BorderLayout.WEST);
				header.add(mode, BorderLayout.EAST);
				dossier.add(header, BorderLayout.NORTH);

				JPanel metrics = new JPanel(new GridLayout(1, 4, 6, 0));
				metrics.setOpaque(false);
				metrics.add(dossierMetric("Band", dossierReliability, GREEN));
				metrics.add(dossierMetric("Cover", dossierCoverage, TEAL));
				metrics.add(dossierMetric("Fragile", dossierFragility, PINK));
				metrics.add(dossierMetric("Gap", dossierGap, AMBER));

				JPanel body = new JPanel();
				body.setOpaque(false);
				body.setLayout(new GridLayout(5, 1, 0, 6));
				body.add(metrics);
				body.add(dossierLine("WHY THIS", dossierWhy, BLUE));
				body.add(dossierLine("WHY NOT", dossierWhyNot, AMBER));
				body.add(dossierLine("LIMITS", dossierRisks, RED));
				body.add(dossierLine("EVIDENCE LEDGER", dossierEvidence, GREEN));
				dossier.add(body, BorderLayout.CENTER);
				return dossier;
			}

		private Component createResearchDock()
		{
			JPanel dock = new JPanel(new BorderLayout(0, 12));
			dock.setOpaque(false);
			dock.setPreferredSize(new Dimension(620, 520));
			dock.add(createReplayTimelinePanel(), BorderLayout.NORTH);
			dock.add(createAnalysisCockpitPanel(), BorderLayout.CENTER);
			return dock;
		}

		private Component createReplayTimelinePanel()
		{
			JPanel panel = surfacePanel(new BorderLayout(0, 10));
			panel.setPreferredSize(new Dimension(620, 202));
			panel.add(sectionHeader("Replay Timeline",
					"selected frame, date, score motion"), BorderLayout.NORTH);

			replayScrubber.setPreferredSize(new Dimension(620, 122));
			replayScrubber.setMinimumSize(new Dimension(420, 104));
			timeReadout.setFont(NebulaDesign.fontMono(11f));
			timeReadout.setForeground(MUTED);
			timeSlider.setOpaque(false);
			timeSlider.setPaintTicks(false);
			timeSlider.setSnapToTicks(true);
			panel.add(replayScrubber, BorderLayout.CENTER);

			JPanel toggles = new JPanel(new GridLayout(1, 5, 5, 0));
			toggles.setOpaque(false);
			toggles.add(removeGraphBox);
			toggles.add(removeTimeBox);
			toggles.add(removeBridgeBox);
			toggles.add(removeDebiasBox);
			toggles.add(removeTopTermBox);
			panel.add(toggles, BorderLayout.SOUTH);
			return panel;
		}

		private Component createAnalysisCockpitPanel()
		{
			JPanel panel = surfacePanel(new BorderLayout(0, 10));
			panel.setPreferredSize(new Dimension(620, 306));
			panel.add(sectionHeader("Evidence Cockpit",
					"readiness, probes, benchmark state"), BorderLayout.NORTH);
			researchCanvas.setPreferredSize(new Dimension(620, 260));
			panel.add(researchCanvas, BorderLayout.CENTER);
			return panel;
		}

	private Component createFooter()
	{
		JPanel footer = surfacePanel(new BorderLayout());
		footer.setBorder(new EmptyBorder(10, 16, 12, 16));
		statusLabel.setFont(NebulaDesign.fontMedium(12f));
		statusLabel.setForeground(MUTED);
		footer.add(statusLabel, BorderLayout.WEST);
		return footer;
	}

	private void installListeners()
	{
		targetList.addListSelectionListener(new ListSelectionListener()
		{
			public void valueChanged(ListSelectionEvent event)
			{
				if (!event.getValueIsAdjusting())
				{
					updateCandidateSelection();
				}
			}
		});
			candidateList.addListSelectionListener(new ListSelectionListener()
			{
				public void valueChanged(ListSelectionEvent event)
				{
					if (!event.getValueIsAdjusting())
					{
						updateStudioSelection();
					}
				}
			});
			timeSlider.addChangeListener(new ChangeListener()
			{
				public void stateChanged(ChangeEvent event)
				{
					updateStudioSelection();
				}
			});
			ActionListener counterfactualListener = new ActionListener()
			{
				public void actionPerformed(ActionEvent event)
				{
					updateStudioSelection();
				}
			};
			removeGraphBox.addActionListener(counterfactualListener);
			removeTimeBox.addActionListener(counterfactualListener);
			removeBridgeBox.addActionListener(counterfactualListener);
			removeDebiasBox.addActionListener(counterfactualListener);
			removeTopTermBox.addActionListener(counterfactualListener);
		}

	private void updateCandidateSelection()
	{
		TemporalGraphFoundationModelSupport.TargetReport report =
				targetList.getSelectedValue();
		candidateModel.clear();
			if (report == null)
			{
				statusLabel.setText(latestResult == null
						? "No Temporal Graph Foundation output yet."
						: latestResult.getSummary());
				updateStudioSelection();
				return;
			}
		for (TemporalGraphFoundationModelSupport.CandidateScore score
				: report.topCandidates)
		{
			candidateModel.addElement(score);
		}
		statusLabel.setText(report.headline);
			if (candidateModel.getSize() > 0)
			{
				timeSlider.setValue(timeSlider.getMaximum());
				candidateList.setSelectedIndex(0);
			}
			else
			{
				updateStudioSelection();
			}
		}

		private void updateStudioSelection()
		{
			TemporalGraphFoundationModelSupport.TargetReport report =
					targetList.getSelectedValue();
				TemporalGraphFoundationModelSupport.CandidateScore candidate =
						candidateList.getSelectedValue();
				updateDecisionDossier(report, candidate);
				updateTimeReadout(candidate);
				replayScrubber.setSelection(
						candidate,
						selectedFrameIndex(candidate),
						removeGraphBox.isSelected(),
						removeTimeBox.isSelected(),
						removeBridgeBox.isSelected(),
						removeDebiasBox.isSelected(),
						removeTopTermBox.isSelected());
				foundationCanvas.setSelection(
					latestResult,
					report,
					candidate,
					selectedFrameIndex(candidate),
					removeGraphBox.isSelected(),
					removeTimeBox.isSelected(),
					removeBridgeBox.isSelected(),
					removeDebiasBox.isSelected(),
					removeTopTermBox.isSelected());
			researchCanvas.setSelection(
					latestResult,
					report,
					candidate,
					selectedFrameIndex(candidate),
					removeGraphBox.isSelected(),
					removeTimeBox.isSelected(),
					removeBridgeBox.isSelected(),
					removeDebiasBox.isSelected(),
					removeTopTermBox.isSelected());
		}

		private int selectedFrameIndex(
				TemporalGraphFoundationModelSupport.CandidateScore candidate)
		{
			if (candidate == null || candidate.timeline.isEmpty())
			{
				return 0;
			}
			int max = Math.max(0, candidate.timeline.size() - 1);
			return Math.max(0, Math.min(max, timeSlider.getValue()));
		}

		private void updateTimeReadout(
				TemporalGraphFoundationModelSupport.CandidateScore candidate)
		{
			if (candidate == null || candidate.timeline.isEmpty())
			{
				timeReadout.setText("no timeline");
				return;
			}
			TemporalGraphFoundationModelSupport.TimelineFrame frame =
					candidate.timeline.get(selectedFrameIndex(candidate));
			timeReadout.setText("frame " + (frame.index + 1)
					+ "/" + candidate.timeline.size()
					+ " | " + frame.label);
		}

	private void updateMetrics(
			TemporalGraphFoundationModelSupport.Result result)
	{
		if (result == null)
		{
			runMetric.setText("--");
			rowsMetric.setText("--");
				datedMetric.setText("--");
				graphMetric.setText("--");
					targetsMetric.setText("--");
					eventsMetric.setText("--");
					researchMetric.setText("--");
					coverageMetric.setText("--");
					fragilityMetric.setText("--");
					return;
				}
			TemporalGraphFoundationModelSupport.ResearchSummary research =
					result.getResearchSummary();
			runMetric.setText(shortRunId(result.getRunId()));
			rowsMetric.setText(COUNT_FORMAT.format(result.getRowsScanned()));
			datedMetric.setText(COUNT_FORMAT.format(result.getDatedRows()));
			graphMetric.setText(COUNT_FORMAT.format(result.getGraphArtifactEdges()));
				targetsMetric.setText(COUNT_FORMAT.format(result.getReports().size()));
				eventsMetric.setText(COUNT_FORMAT.format(research.timelineFrames));
				researchMetric.setText(percent(research.hitAtOne));
				coverageMetric.setText(percent(research.averageEvidenceCoverage));
				fragilityMetric.setText(percent(research.averageCounterfactualFragility));
				File dataset = result.getDatasetFile();
				runMetric.setToolTipText(dataset == null ? result.getRunId()
						: result.getRunId() + " | " + dataset.getPath());
				researchMetric.setToolTipText(research.headline);
				coverageMetric.setToolTipText("Mean evidence coverage; readiness "
						+ percent(research.foundationReadiness.overall)
						+ " (" + research.foundationReadiness.label + ").");
				fragilityMetric.setToolTipText("Mean counterfactual fragility; calibration p~"
						+ decimal(research.averageCalibrationPValue)
						+ ", abstain " + percent(research.abstainRate) + ".");
			}

			private void updateDecisionDossier(
					TemporalGraphFoundationModelSupport.TargetReport report,
					TemporalGraphFoundationModelSupport.CandidateScore candidate)
			{
				if (report == null || candidate == null)
				{
					dossierReliability.setText("--");
					dossierCoverage.setText("--");
					dossierFragility.setText("--");
					dossierGap.setText("--");
					dossierWhy.setText(html("Select a target and candidate after a TGF run."));
					dossierWhyNot.setText(html("--"));
					dossierRisks.setText(html("--"));
					dossierEvidence.setText(html("--"));
					return;
				}
				dossierReliability.setText(firstWord(candidate.reliabilityBand));
				dossierReliability.setToolTipText(candidate.reliabilityBand);
				dossierCoverage.setText(percent(candidate.evidenceCoverage));
				dossierFragility.setText(percent(candidate.counterfactualFragility));
				dossierGap.setText(candidate.challengerName.length() == 0
						? "solo" : signedDecimal(candidate.challengerGap));
				dossierWhy.setText(html(candidate.whyThis));
				dossierWhyNot.setText(html(candidate.whyNotChallenger));
				dossierRisks.setText(html(candidate.calibrationVerdict
						+ "; " + join(candidate.riskFlags, 4)));
				dossierEvidence.setText(html(evidenceLedger(candidate)
						+ "; " + candidate.calibrationNote));
			}

			private static String evidenceLedger(
					TemporalGraphFoundationModelSupport.CandidateScore candidate)
			{
				if (candidate == null || candidate.evidenceAtoms.isEmpty())
				{
					return "--";
				}
				ArrayList<String> values = new ArrayList<String>();
				for (TemporalGraphFoundationModelSupport.EvidenceAtom atom
						: candidate.evidenceAtoms)
				{
					values.add(atom.label + " "
							+ decimal(atom.contribution));
					if (values.size() >= 3)
					{
						break;
					}
				}
				return join(values, values.size());
			}

	private static JPanel metricCard(String label, JLabel value, Color accent)
	{
		JPanel panel = new MetricPanel(accent);
		panel.setLayout(new BorderLayout(0, 3));
		panel.setBorder(new EmptyBorder(9, 11, 11, 11));
		JLabel caption = new JLabel(label);
		caption.setFont(NebulaDesign.fontSection());
		caption.setForeground(FAINT);
		caption.setBorder(new EmptyBorder(0, 12, 0, 0));
		value.setForeground(TEXT);
		panel.add(caption, BorderLayout.NORTH);
		panel.add(value, BorderLayout.CENTER);
		return panel;
	}

			private static JLabel metricValue(String text)
			{
				JLabel label = new JLabel(text);
				label.setFont(NebulaDesign.fontSemiBold(18f));
				label.setForeground(TEXT);
				label.setHorizontalAlignment(SwingConstants.LEFT);
				return label;
			}

			private static JLabel dossierValue(String text)
			{
				JLabel label = new JLabel(text);
				label.setFont(NebulaDesign.fontSemiBold(13f));
				label.setForeground(TEXT);
				label.setHorizontalAlignment(SwingConstants.LEFT);
				return label;
			}

			private static JLabel dossierText(String text)
			{
				JLabel label = new JLabel(html(text));
				label.setFont(NebulaDesign.fontBody(10.5f));
				label.setForeground(MUTED);
				label.setVerticalAlignment(SwingConstants.TOP);
				return label;
			}

			private static JPanel dossierMetric(String label, JLabel value, Color accent)
			{
				JPanel panel = new MetricPanel(accent);
				panel.setLayout(new BorderLayout(0, 2));
				panel.setBorder(new EmptyBorder(8, 9, 8, 8));
				JLabel caption = new JLabel(label.toUpperCase(Locale.US));
				caption.setFont(NebulaDesign.fontSemiBold(9f));
				caption.setForeground(FAINT);
				caption.setBorder(new EmptyBorder(0, 11, 0, 0));
				panel.add(caption, BorderLayout.NORTH);
				panel.add(value, BorderLayout.CENTER);
				return panel;
			}

			private static JPanel dossierLine(String title, JLabel text, Color accent)
			{
				JPanel panel = new JPanel(new BorderLayout(0, 2));
				panel.setOpaque(false);
				JLabel caption = new JLabel(title);
				caption.setFont(NebulaDesign.fontSemiBold(9.5f));
				caption.setForeground(accent);
				panel.add(caption, BorderLayout.NORTH);
				panel.add(text, BorderLayout.CENTER);
				return panel;
			}

			private static String html(String text)
			{
				String safe = text == null ? "" : text;
				safe = safe.replace("&", "&amp;")
						.replace("<", "&lt;")
						.replace(">", "&gt;");
				return "<html><body style='width:292px;'>"
						+ safe + "</body></html>";
			}

		private static JCheckBox controlToggle(String text)
		{
			JCheckBox box = new JCheckBox(text);
				box.setOpaque(false);
				box.setFont(NebulaDesign.fontMedium(11f));
				box.setForeground(MUTED);
			box.setFocusPainted(false);
			box.setIcon(new ToggleIcon());
			box.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			box.setIconTextGap(7);
			return box;
		}

			private static String decimal(double value)
			{
				return String.format(Locale.US, "%.3f", value);
			}

			private static String signedDecimal(double value)
			{
				return (value >= 0.0 ? "+" : "")
						+ String.format(Locale.US, "%.3f", value);
			}

		private static TemporalGraphFoundationModelSupport.TimelineFrame frameAt(
				TemporalGraphFoundationModelSupport.CandidateScore candidate,
				int index)
		{
			if (candidate == null || candidate.timeline.isEmpty())
			{
				return null;
			}
			int safe = Math.max(0, Math.min(candidate.timeline.size() - 1, index));
			return candidate.timeline.get(safe);
		}

		private static String join(List<String> values, int limit)
		{
			ArrayList<String> safeValues = new ArrayList<String>();
			for (String value : values)
			{
				if (value != null && value.trim().length() > 0)
				{
					safeValues.add(value.trim());
				}
				if (safeValues.size() >= limit)
				{
					break;
				}
			}
			if (safeValues.isEmpty())
			{
				return "-";
			}
			StringBuilder builder = new StringBuilder();
			for (int i = 0; i < safeValues.size(); i++)
			{
				if (i > 0)
				{
					builder.append(", ");
				}
				builder.append(safeValues.get(i));
			}
			return builder.toString();
		}

		private void exportResearchCapsule()
		{
			if (latestResult == null)
			{
				NebulaDialogs.showInfo(
						this,
						"No TGF run has been recorded yet.",
						"Temporal Graph Foundation Studio");
				return;
			}
			File dir = new File("reports/tgf-capsules");
			if (!dir.exists() && !dir.mkdirs())
			{
				NebulaDialogs.showWarning(
						this,
						"Could not create capsule directory:\n" + dir.getAbsolutePath(),
						"Temporal Graph Foundation Studio");
				return;
			}
			File capsule = new File(
					dir,
					"tgf-research-capsule-"
							+ new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date())
							+ ".md");
			try
			{
				writeCapsule(capsule);
				if (Desktop.isDesktopSupported())
				{
					try
					{
						Desktop.getDesktop().open(capsule);
					}
					catch (Exception ignored)
					{
						// The capsule was written; opening it is a convenience.
					}
				}
				NebulaDialogs.showSuccess(
						this,
						"TGF research capsule written to:\n" + capsule.getAbsolutePath(),
						"Temporal Graph Foundation Studio");
			}
			catch (IOException ex)
			{
				NebulaDialogs.showWarning(
						this,
						"Could not write TGF research capsule:\n" + ex.getMessage(),
						"Temporal Graph Foundation Studio");
			}
		}

		private void writeCapsule(File capsule) throws IOException
		{
			BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
					new FileOutputStream(capsule), StandardCharsets.UTF_8));
			try
			{
				TemporalGraphFoundationModelSupport.ResearchSummary research =
						latestResult.getResearchSummary();
				writer.write("# DSMP Temporal Graph Foundation Research Capsule");
				writer.newLine();
				writer.newLine();
				writer.write("Generated: "
						+ new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
				writer.newLine();
				writer.write("Run: " + latestResult.getRunId());
				writer.newLine();
				writer.write("Dataset: "
						+ (latestResult.getDatasetFile() == null
								? "not available"
								: latestResult.getDatasetFile().getPath()));
				writer.newLine();
				writer.write("Dataset SHA-256: "
						+ datasetSha256(latestResult.getDatasetFile()));
				writer.newLine();
				writer.write("Git revision: " + gitRevision());
				writer.newLine();
				writer.write("Java runtime: " + System.getProperty("java.version"));
				writer.newLine();
				writer.write("Model weights: "
						+ TemporalGraphFoundationModelSupport.weightsDescription());
				writer.newLine();
				writer.write("Summary: " + latestResult.getSummary());
				writer.newLine();
				writer.write("Methodology: "
						+ TemporalGraphFoundationModelSupport.methodologySummary());
				writer.newLine();
				writer.newLine();
				writer.write("## Research Metrics");
				writer.newLine();
				writer.write("- Scored pairs: " + research.scoredPairs);
				writer.newLine();
				writer.write("- Targets: " + research.targetCount);
				writer.newLine();
				writer.write("- Timeline frames: " + research.timelineFrames);
				writer.newLine();
				writer.write("- Hit@1 proxy: " + percent(research.hitAtOne));
				writer.newLine();
				writer.write("- Hit@3 proxy: " + percent(research.hitAtThree));
				writer.newLine();
				writer.write("- MRR proxy: " + decimal(research.mrr));
				writer.newLine();
				writer.write("- NDCG@5 proxy: " + decimal(research.ndcgAtFive));
				writer.newLine();
				writer.write("- Mean stability: " + percent(research.averageStability));
				writer.newLine();
					writer.write("- Counterfactual sensitivity: "
							+ percent(research.counterfactualFlipRate));
					writer.newLine();
					writer.write("- Evidence coverage: "
							+ percent(research.averageEvidenceCoverage));
					writer.newLine();
					writer.write("- Counterfactual fragility: "
							+ percent(research.averageCounterfactualFragility));
					writer.newLine();
					writer.write("- Calibration p-value proxy: "
							+ decimal(research.averageCalibrationPValue));
					writer.newLine();
					writer.write("- Calibration abstain rate: "
							+ percent(research.abstainRate));
					writer.newLine();
					writer.write("- Evidence-limit flags: "
							+ research.evidenceLimitFlags);
					writer.newLine();
					writer.write("- Foundation readiness: "
							+ percent(research.foundationReadiness.overall)
							+ " (" + research.foundationReadiness.label + ")");
					writer.newLine();
					writer.write("- Foundation honesty note: "
							+ research.foundationReadiness.note);
					writer.newLine();
					writer.newLine();
					writer.write("## Benchmark Board");
					writer.newLine();
					for (TemporalGraphFoundationModelSupport.BenchmarkRow row
							: research.benchmarkRows)
					{
						writer.write("- " + row.label
								+ ": Hit@1 " + percent(row.hitAtOne)
								+ ", Hit@3 " + percent(row.hitAtThree)
								+ ", MRR " + decimal(row.mrr)
								+ ", NDCG@5 " + decimal(row.ndcgAtFive)
								+ ", top " + safe(row.topCandidate)
								+ " - " + row.note);
						writer.newLine();
					}
					writer.newLine();
					writer.write("## Current Selection");
				writer.newLine();
				TemporalGraphFoundationModelSupport.TargetReport report =
						targetList.getSelectedValue();
				TemporalGraphFoundationModelSupport.CandidateScore candidate =
						candidateList.getSelectedValue();
				if (report == null || candidate == null)
				{
					writer.write("No target/candidate is currently selected.");
					writer.newLine();
				}
				else
				{
					writer.write("- Target: " + report.targetUser
							+ " (" + report.targetFollowee + ")");
					writer.newLine();
					writer.write("- Candidate: " + candidate.candidateName()
							+ " (" + candidate.candidateFollowee() + ")");
					writer.newLine();
					writer.write("- Score: " + decimal(candidate.finalScore)
							+ " | rank #" + candidate.rank);
					writer.newLine();
					writer.write("- Confidence interval: ["
							+ decimal(candidate.confidenceLower)
							+ ", " + decimal(candidate.confidenceUpper) + "]");
					writer.newLine();
						writer.write("- Verdict: " + candidate.researchVerdict);
						writer.newLine();
						writer.write("- Reliability band: " + candidate.reliabilityBand);
						writer.newLine();
						writer.write("- Evidence coverage: "
								+ percent(candidate.evidenceCoverage));
						writer.newLine();
						writer.write("- Counterfactual fragility: "
								+ percent(candidate.counterfactualFragility));
						writer.newLine();
						writer.write("- Calibration verdict: "
								+ candidate.calibrationVerdict);
						writer.newLine();
						writer.write("- Calibration note: "
								+ candidate.calibrationNote);
						writer.newLine();
						writer.write("- Why this: " + candidate.whyThis);
						writer.newLine();
						writer.write("- Why not challenger: "
								+ candidate.whyNotChallenger);
						writer.newLine();
						writer.write("- Evidence limits: "
								+ join(candidate.riskFlags, 8));
						writer.newLine();
						writer.write("- Graph path: " + candidate.path);
						writer.newLine();
					writer.write("- Shared terms: "
							+ (candidate.sharedTerms.isEmpty()
									? "none"
									: join(candidate.sharedTerms, 8)));
					writer.newLine();
						writer.newLine();
						writer.write("### Evidence Ledger");
						writer.newLine();
						for (TemporalGraphFoundationModelSupport.EvidenceAtom atom
								: candidate.evidenceAtoms)
						{
							writer.write("- " + atom.label
									+ ": value " + percent(atom.value)
									+ ", weighted contribution "
									+ decimal(atom.contribution)
									+ " - " + atom.explanation);
							writer.newLine();
						}
						writer.newLine();
						writer.write("### Target Benchmark Lenses");
						writer.newLine();
						for (TemporalGraphFoundationModelSupport.BenchmarkRow row
								: report.benchmarkRows)
						{
							writer.write("- " + row.label
									+ ": top " + safe(row.topCandidate)
									+ ", relevant rank "
									+ (row.relevantRank <= 0
											? "n/a" : "#" + row.relevantRank)
									+ ", MRR " + decimal(row.mrr)
									+ ", NDCG@5 " + decimal(row.ndcgAtFive)
									+ " - " + row.note);
							writer.newLine();
						}
						writer.newLine();
						writer.write("### Counterfactual Probes");
					writer.newLine();
					for (TemporalGraphFoundationModelSupport.CounterfactualProbe probe
							: candidate.counterfactuals)
					{
						writer.write("- " + probe.label
								+ ": score " + decimal(probe.score)
								+ ", rank " + (probe.rank <= 0 ? "n/a" : "#" + probe.rank)
								+ ", drop " + decimal(probe.scoreDrop));
						writer.newLine();
					}
					writer.newLine();
					writer.write("### Timeline Frames");
					writer.newLine();
					for (TemporalGraphFoundationModelSupport.TimelineFrame frame
							: candidate.timeline)
					{
						writer.write("- " + frame.label
								+ ": score " + decimal(frame.score)
								+ ", text " + percent(frame.text)
								+ ", graph " + percent(frame.graph)
								+ ", time " + percent(frame.time)
								+ ", rank #" + frame.rank
								+ ", dominant " + frame.dominantSignal
								+ " - " + frame.auditNote + " " + frame.evidence);
						writer.newLine();
					}
					writer.newLine();
					writer.write("### Retained Candidate Table");
					writer.newLine();
					for (TemporalGraphFoundationModelSupport.CandidateScore score
							: report.topCandidates)
					{
						writer.write("- #" + score.rank + " "
								+ score.candidateName()
								+ ": score " + decimal(score.finalScore)
								+ ", CI [" + decimal(score.confidenceLower)
								+ ", " + decimal(score.confidenceUpper) + "]"
								+ ", calibration " + score.calibrationVerdict
								+ ", fragile "
								+ percent(score.counterfactualFragility)
								+ ", evidence "
								+ percent(score.evidenceCoverage));
						writer.newLine();
					}
				}
			}
			finally
			{
				writer.close();
			}
		}

		private static String datasetSha256(File file)
		{
			if (file == null || !file.isFile())
			{
				return "not available";
			}
			try
			{
				MessageDigest digest = MessageDigest.getInstance("SHA-256");
				FileInputStream input = new FileInputStream(file);
				try
				{
					byte[] buffer = new byte[64 * 1024];
					int read;
					while ((read = input.read(buffer)) >= 0)
					{
						if (read > 0)
						{
							digest.update(buffer, 0, read);
						}
					}
				}
				finally
				{
					input.close();
				}
				byte[] hash = digest.digest();
				StringBuilder builder = new StringBuilder();
				for (byte value : hash)
				{
					builder.append(String.format(Locale.US, "%02x",
							Integer.valueOf(value & 0xff)));
				}
				return builder.toString();
			}
			catch (IOException ex)
			{
				return "unreadable: " + ex.getMessage();
			}
			catch (NoSuchAlgorithmException ex)
			{
				return "sha-256 unavailable";
			}
		}

		private static String gitRevision()
		{
			File head = new File(".git/HEAD");
			if (!head.isFile())
			{
				return "not available";
			}
			String text = readSmallFile(head);
			if (text.startsWith("ref:"))
			{
				String ref = text.substring(4).trim();
				String value = readSmallFile(new File(".git", ref));
				return value.length() == 0 ? ref : value;
			}
			return text.length() == 0 ? "not available" : text;
		}

		private static String readSmallFile(File file)
		{
			if (file == null || !file.isFile() || file.length() > 1024 * 1024)
			{
				return "";
			}
			FileInputStream input = null;
			try
			{
				input = new FileInputStream(file);
				byte[] bytes = new byte[(int)file.length()];
				int total = 0;
				while (total < bytes.length)
				{
					int read = input.read(bytes, total, bytes.length - total);
					if (read < 0)
					{
						break;
					}
					total += read;
				}
				return new String(bytes, 0, total, StandardCharsets.UTF_8).trim();
			}
			catch (IOException ignored)
			{
				return "";
			}
			finally
			{
				if (input != null)
				{
					try
					{
						input.close();
					}
					catch (IOException ignored)
					{
					}
				}
			}
		}

		private static JPanel surfacePanel(BorderLayout layout)
		{
			JPanel panel = new RoundedPanel(SURFACE);
			panel.setLayout(layout);
			panel.setBorder(new EmptyBorder(14, 14, 14, 14));
		return panel;
	}

	private static JScrollPane scroller(Component component)
	{
		JScrollPane scroll = new JScrollPane(component);
			scroll.setBorder(BorderFactory.createLineBorder(BORDER));
			scroll.setHorizontalScrollBarPolicy(
					ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
			scroll.getViewport().setBackground(SURFACE);
			scroll.setBackground(SURFACE);
			scroll.getVerticalScrollBar().setUnitIncrement(16);
			scroll.getVerticalScrollBar().setOpaque(false);
			scroll.getVerticalScrollBar().setUI(
					new NebulaWidgets.PillScrollBarUI());
			return scroll;
		}

	private static Component sectionHeader(String title, String detail)
	{
		JPanel panel = new JPanel(new BorderLayout());
		panel.setOpaque(false);
			JLabel titleLabel = new JLabel(title);
			titleLabel.setFont(NebulaDesign.fontSemiBold(15f));
			titleLabel.setForeground(TEXT);
			JLabel detailLabel = new JLabel(detail);
		detailLabel.setFont(NebulaDesign.fontMedium(11f));
		detailLabel.setForeground(FAINT);
		panel.add(titleLabel, BorderLayout.WEST);
		panel.add(detailLabel, BorderLayout.EAST);
		return panel;
	}

	private static Font font(int style, int size)
	{
		if (style == Font.BOLD)
		{
			return NebulaDesign.fontSemiBold(Math.max(9, size));
		}
		return NebulaDesign.fontBody(Math.max(10, size));
	}

	private static Color alphaOf(Color color, int alpha)
	{
		return new Color(color.getRed(), color.getGreen(), color.getBlue(),
				Math.max(0, Math.min(255, alpha)));
	}

	private static Color mix(Color base, Color tint, float amount)
	{
		float clamped = Math.max(0.0f, Math.min(1.0f, amount));
		int r = Math.round(base.getRed()
				+ (tint.getRed() - base.getRed()) * clamped);
		int g = Math.round(base.getGreen()
				+ (tint.getGreen() - base.getGreen()) * clamped);
		int b = Math.round(base.getBlue()
				+ (tint.getBlue() - base.getBlue()) * clamped);
		return new Color(r, g, b);
	}

	private static String hex(Color color)
	{
		return String.format(Locale.US, "#%02X%02X%02X",
				Integer.valueOf(color.getRed()),
				Integer.valueOf(color.getGreen()),
				Integer.valueOf(color.getBlue()));
	}

	private static String shortRunId(String runId)
	{
		if (runId == null || runId.trim().length() == 0)
		{
			return "local";
		}
		String trimmed = runId.trim();
		return trimmed.length() <= 8 ? trimmed : trimmed.substring(0, 8);
	}

	private static String firstWord(String value)
	{
		String trimmed = value == null ? "" : value.trim();
		if (trimmed.length() == 0)
		{
			return "--";
		}
		int space = trimmed.indexOf(' ');
		return space > 0 ? trimmed.substring(0, space) : trimmed;
	}

	private static String percent(double value)
	{
		double clamped = Math.max(0.0, Math.min(1.0, value));
		return Integer.toString((int) Math.round(clamped * 100.0)) + "%";
	}

	private static String scoreText(double value)
	{
		return String.format(Locale.US, "%.4f", value);
	}

	private static String safe(String value)
	{
		return value == null || value.trim().length() == 0
				? "-" : value.trim();
	}

	private static String ellipsize(String value, FontMetrics metrics, int maxWidth)
	{
		String text = safe(value);
		if (metrics.stringWidth(text) <= maxWidth)
		{
			return text;
		}
		String suffix = "...";
		int limit = Math.max(1, maxWidth - metrics.stringWidth(suffix));
		while (text.length() > 1 && metrics.stringWidth(text) > limit)
		{
			text = text.substring(0, text.length() - 1);
		}
		return text + suffix;
	}

	private static final class TargetRenderer extends DefaultListCellRenderer
	{
		public Component getListCellRendererComponent(
				JList<?> list,
				Object value,
				int index,
				boolean isSelected,
				boolean cellHasFocus)
		{
			JLabel label = (JLabel) super.getListCellRendererComponent(
					list, value, index, isSelected, cellHasFocus);
			TemporalGraphFoundationModelSupport.TargetReport report =
					(TemporalGraphFoundationModelSupport.TargetReport) value;
				String count = report.topCandidates.size() + " candidates";
				String top = report.topCandidates.isEmpty()
						? "No positive candidates"
						: "Top: " + report.topCandidates.get(0).candidateName();
					label.setText("<html><b>" + safe(report.targetUser)
							+ "</b><br><span style='color:"
							+ hex(NebulaDesign.currentTextSecondary()) + ";'>"
							+ safe(report.targetFollowee) + " | " + count
							+ " | NDCG " + percent(report.ndcgAtFive)
							+ "</span><br>" + top + "</html>");
			label.setFont(NebulaDesign.fontBody(12f));
				label.setForeground(TEXT);
				if (isSelected)
				{
					label.setBackground(NebulaDesign.currentSelection());
					label.setBorder(BorderFactory.createCompoundBorder(
							BorderFactory.createMatteBorder(0, 3, 0, 0,
									NebulaDesign.currentAccent()),
							new EmptyBorder(8, 9, 8, 10)));
				}
				else
				{
					label.setBackground(NebulaDesign.currentSurface());
					label.setBorder(new EmptyBorder(8, 12, 8, 10));
				}
				return label;
			}
	}

	private static final class CandidateRenderer extends DefaultListCellRenderer
	{
		public Component getListCellRendererComponent(
				JList<?> list,
				Object value,
				int index,
				boolean isSelected,
				boolean cellHasFocus)
		{
			JLabel label = (JLabel) super.getListCellRendererComponent(
					list, value, index, isSelected, cellHasFocus);
			TemporalGraphFoundationModelSupport.CandidateScore score =
					(TemporalGraphFoundationModelSupport.CandidateScore) value;
					label.setText("<html><b>#" + score.rank + " "
							+ safe(score.candidateName()) + "</b><br>"
							+ "<span style='color:"
							+ hex(NebulaDesign.currentTextSecondary()) + ";'>"
							+ safe(score.candidateFollowee()) + " | score "
							+ scoreText(score.finalScore) + "</span><br>"
							+ score.reliabilityBand + " | stable " + percent(score.stability)
							+ " | fragile " + percent(score.counterfactualFragility)
							+ "<br><span style='color:"
							+ hex(NebulaDesign.currentTextSecondary()) + ";'>"
							+ score.calibrationVerdict
							+ ", CI [" + scoreText(score.confidenceLower)
							+ ", " + scoreText(score.confidenceUpper) + "]"
							+ "</span></html>");
			label.setFont(NebulaDesign.fontBody(12f));
				label.setForeground(TEXT);
				if (isSelected)
				{
					label.setBackground(mix(NebulaDesign.currentSurface(),
							GREEN, StudioNightTheme.isNightMode() ? 0.22f : 0.16f));
					label.setBorder(BorderFactory.createCompoundBorder(
							BorderFactory.createMatteBorder(0, 3, 0, 0, GREEN),
							new EmptyBorder(8, 9, 8, 10)));
				}
				else
				{
					label.setBackground(NebulaDesign.currentSurface());
					label.setBorder(new EmptyBorder(8, 12, 8, 10));
				}
				return label;
			}
	}

		private final class ReplayScrubber extends RoundedPanel
		{
			private TemporalGraphFoundationModelSupport.CandidateScore candidate;
			private int frameIndex;
			private boolean removeGraph;
			private boolean removeTime;
			private boolean removeBridge;
			private boolean removeDebias;
			private boolean removeTopTerm;

			ReplayScrubber()
			{
				super(PANEL_INK);
				setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
				MouseAdapter adapter = new MouseAdapter()
				{
					public void mousePressed(MouseEvent event)
					{
						selectFrame(event);
					}

					public void mouseDragged(MouseEvent event)
					{
						selectFrame(event);
					}
				};
				addMouseListener(adapter);
				addMouseMotionListener(adapter);
			}

			void setSelection(
					TemporalGraphFoundationModelSupport.CandidateScore candidate,
					int frameIndex,
					boolean removeGraph,
					boolean removeTime,
					boolean removeBridge,
					boolean removeDebias,
					boolean removeTopTerm)
			{
				this.candidate = candidate;
				this.frameIndex = frameIndex;
				this.removeGraph = removeGraph;
				this.removeTime = removeTime;
				this.removeBridge = removeBridge;
				this.removeDebias = removeDebias;
				this.removeTopTerm = removeTopTerm;
				repaint();
			}

			private void selectFrame(MouseEvent event)
			{
				if (candidate == null || candidate.timeline.isEmpty())
				{
					return;
				}
				int[] rail = railBounds(getWidth(), getHeight());
				double ratio = (event.getX() - rail[0])
						/ (double)Math.max(1, rail[2]);
				int max = candidate.timeline.size() - 1;
				int index = (int)Math.round(Math.max(0.0,
						Math.min(1.0, ratio)) * max);
				timeSlider.setValue(index);
			}

			protected void paintComponent(Graphics graphics)
			{
				super.paintComponent(graphics);
				Graphics2D g = (Graphics2D)graphics.create();
				NebulaDesign.prepare(g);
				int w = getWidth();
				int h = getHeight();
				if (candidate == null || candidate.timeline.isEmpty())
				{
					drawEmpty(g, w, h);
					g.dispose();
					return;
				}
				drawScrubber(g, w, h);
				g.dispose();
			}

			private void drawScrubber(Graphics2D g, int w, int h)
			{
				int[] rail = railBounds(w, h);
				int railX = rail[0];
				int railY = rail[1];
				int railW = rail[2];
				TemporalGraphFoundationModelSupport.TimelineFrame frame =
						frameAt(candidate, frameIndex);
				TemporalGraphFoundationModelSupport.TimelineFrame first =
						candidate.timeline.get(0);
				TemporalGraphFoundationModelSupport.TimelineFrame last =
						candidate.timeline.get(candidate.timeline.size() - 1);

				int leftW = Math.min(194, Math.max(156, w / 5));
				int rightW = Math.min(276, Math.max(204, w / 4));
				drawDatePlate(g, 12, 9, leftW, h - 18, frame);
				drawStateCard(g, w - rightW - 12, 9, rightW, h - 18, frame);

				g.setFont(font(Font.BOLD, 9));
				g.setColor(TEAL);
				drawClipped(g, "REPLAY TRAJECTORY", railX, 16, 124);
				g.setFont(font(Font.PLAIN, 9));
				g.setColor(MUTED);
				drawClipped(g, trajectorySummary(frame),
						railX + 130, 16, Math.max(40, railW - 132));

				g.setColor(alphaOf(TRACK, 220));
				g.fillRoundRect(railX, railY - 4, railW, 8, 8, 8);
				g.setColor(alphaOf(BORDER_STRONG, 110));
				g.drawLine(railX, railY - 14, railX + railW, railY - 14);
				int previousX = -1;
				int previousY = -1;
				for (int i = 0; i < candidate.timeline.size(); i++)
				{
					TemporalGraphFoundationModelSupport.TimelineFrame point =
							candidate.timeline.get(i);
					int px = railX + (int)Math.round(railW * point.progress);
					int py = railY - 11
							- (int)Math.round(21.0 * displayScore(point));
					if (previousX >= 0)
					{
						Color tone = signalColor(point.dominantSignal);
						g.setStroke(new BasicStroke(3.0f, BasicStroke.CAP_ROUND,
								BasicStroke.JOIN_ROUND));
						g.setColor(new Color(tone.getRed(), tone.getGreen(),
								tone.getBlue(), 210));
						g.drawLine(previousX, previousY, px, py);
						g.setStroke(new BasicStroke(8.0f, BasicStroke.CAP_ROUND,
								BasicStroke.JOIN_ROUND));
						g.setColor(new Color(tone.getRed(), tone.getGreen(),
								tone.getBlue(), 28));
						g.drawLine(previousX, previousY, px, py);
					}
					previousX = px;
					previousY = py;
				}
				int selectedX = railX;
				int selectedCurveY = railY - 18;
				for (int i = 0; i < candidate.timeline.size(); i++)
				{
					TemporalGraphFoundationModelSupport.TimelineFrame point =
							candidate.timeline.get(i);
					int px = railX + (int)Math.round(railW * point.progress);
					boolean selected = i == frameIndex;
					if (selected)
					{
						selectedX = px;
						selectedCurveY = railY - 11
								- (int)Math.round(21.0 * displayScore(point));
					}
					Color tone = selected ? GREEN : signalColor(point.dominantSignal);
					g.setColor(new Color(tone.getRed(), tone.getGreen(),
							tone.getBlue(), selected ? 82 : 36));
					g.fillOval(px - (selected ? 8 : 5), railY - (selected ? 8 : 5),
							selected ? 16 : 10, selected ? 16 : 10);
					g.setColor(tone);
					g.fillOval(px - (selected ? 4 : 3), railY - (selected ? 4 : 3),
							selected ? 8 : 6, selected ? 8 : 6);
				}
				g.setColor(new Color(GREEN.getRed(), GREEN.getGreen(),
						GREEN.getBlue(), 130));
				g.drawLine(selectedX, 21, selectedX, h - 24);
				g.setColor(new Color(GREEN.getRed(), GREEN.getGreen(),
						GREEN.getBlue(), 46));
				g.fillOval(selectedX - 17, selectedCurveY - 17, 34, 34);
				g.setColor(GREEN);
				g.fillOval(selectedX - 5, selectedCurveY - 5, 10, 10);
				g.fillRoundRect(selectedX - 20, h - 24, 40, 6, 6, 6);

				if (frame != null && railW > 260)
				{
					String callout = "score " + scoreText(displayScore(frame))
							+ " | " + signedDecimal(frame.scoreDelta);
					FontMetrics calloutMetrics = g.getFontMetrics(font(Font.BOLD, 9));
					int calloutW = Math.min(164,
							calloutMetrics.stringWidth(callout) + 18);
					int calloutX = Math.max(railX, Math.min(railX + railW - calloutW,
							selectedX - calloutW / 2));
					int calloutY = Math.max(22, selectedCurveY - 24);
					g.setColor(alphaOf(PLATE, 242));
					g.fillRoundRect(calloutX, calloutY, calloutW, 18, 8, 8);
					g.setColor(new Color(GREEN.getRed(), GREEN.getGreen(),
							GREEN.getBlue(), 150));
					g.drawRoundRect(calloutX, calloutY, calloutW, 18, 8, 8);
					g.setFont(font(Font.BOLD, 9));
					g.setColor(TEXT);
					drawClipped(g, callout, calloutX + 9, calloutY + 13,
							calloutW - 18);
				}

				g.setFont(font(Font.BOLD, 9));
				FontMetrics metrics = g.getFontMetrics();
				String selectedLabel = frame == null ? "-" : frame.label;
				int selectedLabelW = metrics.stringWidth(selectedLabel);
				int selectedLabelX = Math.max(railX, Math.min(railX + railW
						- selectedLabelW,
						selectedX - selectedLabelW / 2));
				g.setColor(MUTED);
				if (selectedLabelX > railX + metrics.stringWidth(first.label) + 10)
				{
					drawClipped(g, first.label, railX, h - 8, 74);
				}
				String lastLabel = last.label;
				int lastLabelX = railX + railW - metrics.stringWidth(lastLabel);
				if (lastLabelX > selectedLabelX + selectedLabelW + 10)
				{
					g.drawString(lastLabel, lastLabelX, h - 8);
				}
				g.setColor(GREEN);
				g.drawString(selectedLabel, selectedLabelX, h - 8);
			}

			private int[] railBounds(int w, int h)
			{
				int leftReserve = Math.min(218, Math.max(178, w / 5 + 18));
				int rightReserve = Math.min(304, Math.max(226, w / 4 + 18));
				int x = leftReserve;
				int width = Math.max(140, w - leftReserve - rightReserve);
				int y = Math.max(42, h / 2 + 12);
				return new int[] {x, y, width};
			}

			private String trajectorySummary(
					TemporalGraphFoundationModelSupport.TimelineFrame frame)
			{
				if (frame == null)
				{
					return "no selected replay frame";
				}
				return "score motion " + signedDecimal(frame.scoreDelta)
						+ " | dominant " + safe(frame.dominantSignal).toLowerCase(Locale.US)
						+ " | " + (frameIndex + 1) + "/" + candidate.timeline.size()
						+ " frames";
			}

			private void drawDatePlate(
					Graphics2D g,
					int x,
					int y,
					int w,
					int h,
					TemporalGraphFoundationModelSupport.TimelineFrame frame)
			{
				g.setColor(alphaOf(PLATE, 240));
				g.fillRoundRect(x, y, w, h, 9, 9);
				g.setColor(alphaOf(BLUE,
						StudioNightTheme.isNightMode() ? 34 : 16));
				g.fillRoundRect(x, y, w, h, 9, 9);
				g.setColor(alphaOf(BLUE, 125));
				g.drawRoundRect(x, y, w, h, 9, 9);
				g.setFont(font(Font.BOLD, 8));
				g.setColor(TEAL);
				drawClipped(g, "SELECTED DATE", x + 10, y + 13, w - 20);
				g.setFont(font(Font.BOLD, 17));
				g.setColor(TEXT);
				drawClipped(g, frame == null ? "-" : frame.label,
						x + 10, y + 33, w - 20);
				g.setFont(font(Font.BOLD, 9));
				g.setColor(MUTED);
				String frameText = frame == null ? "frame -"
						: "frame " + (frameIndex + 1) + "/" + candidate.timeline.size()
								+ " | rank #" + frame.rank;
				drawClipped(g, frameText, x + 10, y + 48, w - 20);
				drawProgressPill(g, x + 10, y + h - 8, w - 20,
						frame == null ? 0.0 : frame.progress,
						frame == null ? MUTED : signalColor(frame.dominantSignal));
			}

			private void drawStateCard(
					Graphics2D g,
					int x,
					int y,
					int w,
					int h,
					TemporalGraphFoundationModelSupport.TimelineFrame frame)
			{
				g.setColor(alphaOf(PLATE, 232));
				g.fillRoundRect(x, y, w, h, 9, 9);
				g.setColor(alphaOf(BORDER_STRONG, 120));
				g.drawRoundRect(x, y, w, h, 9, 9);
				int third = Math.max(56, (w - 28) / 3);
				drawMiniBadge(g, x + 8, y + 7, third, 32, "score",
						frame == null ? "--" : scoreText(displayScore(frame)), GREEN);
				drawMiniBadge(g, x + 14 + third, y + 7, third, 32, "rank",
						frame == null ? "--" : "#" + frame.rank, AMBER);
				drawMiniBadge(g, x + 20 + third * 2, y + 7,
						Math.max(56, w - 28 - third * 2), 32, "delta",
						frame == null ? "--" : signedDecimal(frame.scoreDelta), TEAL);
				g.setFont(font(Font.BOLD, 8));
				g.setColor(activeToggleCount() > 0 ? AMBER : GREEN);
				drawClipped(g, activeToggleCount() > 0
						? "COUNTERFACTUAL STATE" : "OBSERVED STATE",
						x + 10, y + h - 18, w - 20);
				g.setFont(font(Font.PLAIN, 9));
				g.setColor(MUTED);
				drawClipped(g, activeToggleText(), x + 10, y + h - 6, w - 20);
			}

			private void drawProgressPill(
					Graphics2D g,
					int x,
					int y,
					int w,
					double progress,
					Color tone)
			{
				g.setColor(alphaOf(TRACK, 220));
				g.fillRoundRect(x, y, w, 5, 5, 5);
				int fill = (int)Math.round(w * Math.max(0.0,
						Math.min(1.0, progress)));
				g.setColor(tone);
				g.fillRoundRect(x, y, Math.max(5, fill), 5, 5, 5);
			}

			private void drawMiniBadge(
					Graphics2D g,
					int x,
					int y,
					int w,
					int h,
					String label,
					String value,
					Color tone)
			{
				g.setColor(new Color(tone.getRed(), tone.getGreen(), tone.getBlue(), 34));
				g.fillRoundRect(x, y, w, h, 7, 7);
				g.setColor(new Color(tone.getRed(), tone.getGreen(), tone.getBlue(), 132));
				g.drawRoundRect(x, y, w, h, 7, 7);
				g.setFont(font(Font.BOLD, 8));
				g.setColor(tone);
				drawClipped(g, label.toUpperCase(Locale.US), x + 7, y + 11, w - 14);
				g.setFont(font(Font.BOLD, 12));
				g.setColor(TEXT);
				drawClipped(g, value, x + 7, y + 27, w - 14);
			}

			private void drawEmpty(Graphics2D g, int w, int h)
			{
				NebulaDesign.prepare(g);
				g.setColor(alphaOf(TRACK, 200));
				g.fillRoundRect(14, h / 2 + 12, w - 28, 6, 6, 6);
				g.setFont(font(Font.BOLD, 11));
				g.setColor(MUTED);
				String text = "No replay frames available yet.";
				FontMetrics metrics = g.getFontMetrics();
				g.drawString(ellipsize(text, metrics, w - 24),
						Math.max(12, (w - metrics.stringWidth(text)) / 2),
						h / 2 - 2);
			}

			private double displayScore(
					TemporalGraphFoundationModelSupport.TimelineFrame frame)
			{
				double base = frame == null ? 0.0 : frame.score;
				if (candidate == null || (!removeGraph && !removeTime && !removeBridge
						&& !removeDebias && !removeTopTerm))
				{
					return base;
				}
				double counterfactual = candidate.counterfactualScore(
						removeGraph,
						removeTime,
						removeBridge,
						removeDebias,
						removeTopTerm);
				return candidate.finalScore <= 0.0 ? counterfactual
						: Math.max(0.0, Math.min(1.0,
								base * (counterfactual / candidate.finalScore)));
			}

			private Color signalColor(String signal)
			{
				String value = signal == null ? "" : signal.toLowerCase(Locale.US);
				if (value.indexOf("graph") >= 0)
				{
					return GREEN;
				}
				if (value.indexOf("time") >= 0
						|| value.indexOf("temporal") >= 0)
				{
					return TEAL;
				}
				if (value.indexOf("bridge") >= 0)
				{
					return AMBER;
				}
				if (value.indexOf("debias") >= 0)
				{
					return RED;
				}
				if (value.indexOf("robust") >= 0)
				{
					return PURPLE;
				}
				return BLUE;
			}

			private int activeToggleCount()
			{
				int count = 0;
				count += removeGraph ? 1 : 0;
				count += removeTime ? 1 : 0;
				count += removeBridge ? 1 : 0;
				count += removeDebias ? 1 : 0;
				count += removeTopTerm ? 1 : 0;
				return count;
			}

			private String activeToggleText()
			{
				ArrayList<String> active = new ArrayList<String>();
				if (removeGraph)
				{
					active.add("graph");
				}
				if (removeTime)
				{
					active.add("time");
				}
				if (removeBridge)
				{
					active.add("bridge");
				}
				if (removeDebias)
				{
					active.add("debias");
				}
				if (removeTopTerm)
				{
					active.add("top term");
				}
				return active.isEmpty()
						? "observed replay"
						: "counterfactual: " + join(active, 5) + " off";
			}

			private void drawClipped(
					Graphics2D g,
					String value,
					int x,
					int y,
					int width)
			{
				FontMetrics metrics = g.getFontMetrics();
				g.drawString(ellipsize(value, metrics, Math.max(4, width)), x, y);
			}
		}

		private static final class FoundationCanvas extends RoundedPanel
		{
			private static final String[] SIGNAL_LABELS = {
					"Text", "Graph", "Time", "Bridge", "Debias", "Robust"};
			private static final double[] SIGNAL_WEIGHTS = {
					0.34, 0.22, 0.18, 0.14, 0.07, 0.05};
			private static final Color[] SIGNAL_COLORS = {
					BLUE, GREEN, TEAL, AMBER, RED, PURPLE};
			private TemporalGraphFoundationModelSupport.Result result;
			private TemporalGraphFoundationModelSupport.TargetReport report;
			private TemporalGraphFoundationModelSupport.CandidateScore candidate;
			private int frameIndex;
			private boolean removeGraph;
			private boolean removeTime;
			private boolean removeBridge;
			private boolean removeDebias;
			private boolean removeTopTerm;
			private double phase;

		FoundationCanvas()
		{
			super(SURFACE);
			setPreferredSize(new Dimension(620, 790));
			setMinimumSize(new Dimension(480, 760));
			setBorder(new EmptyBorder(14, 14, 14, 14));
		}

			void setSelection(
					TemporalGraphFoundationModelSupport.Result result,
					TemporalGraphFoundationModelSupport.TargetReport report,
					TemporalGraphFoundationModelSupport.CandidateScore candidate,
					int frameIndex,
					boolean removeGraph,
					boolean removeTime,
					boolean removeBridge,
					boolean removeDebias,
					boolean removeTopTerm)
			{
				this.result = result;
				this.report = report;
				this.candidate = candidate;
				this.frameIndex = frameIndex;
				this.removeGraph = removeGraph;
				this.removeTime = removeTime;
				this.removeBridge = removeBridge;
				this.removeDebias = removeDebias;
				this.removeTopTerm = removeTopTerm;
				repaint();
			}

		void advance()
		{
			phase += 0.025;
			if (phase > Math.PI * 2.0)
			{
				phase = 0.0;
			}
			repaint();
		}

		protected void paintComponent(Graphics graphics)
		{
			super.paintComponent(graphics);
			Graphics2D g = (Graphics2D) graphics.create();
			NebulaDesign.prepare(g);
			int w = getWidth();
			int h = getHeight();
			paintBackground(g, w, h);
			if (result == null)
			{
				paintEmpty(g, w, h, "No TGF run has been recorded.");
				g.dispose();
				return;
			}
			if (report == null)
			{
				paintEmpty(g, w, h, result.getSummary());
				g.dispose();
				return;
			}
			if (candidate == null)
			{
				paintEmpty(g, w, h, report.headline);
				g.dispose();
				return;
			}
			paintFoundation(g, w, h);
			g.dispose();
		}

			private void paintBackground(Graphics2D g, int w, int h)
			{
				NebulaDesign.prepare(g);
				boolean night = StudioNightTheme.isNightMode();
				Color top = night ? NebulaDesign.NIGHT_SURFACE
						: NebulaDesign.DAY_SURFACE;
				Color low = night
						? mix(NebulaDesign.NIGHT_SURFACE,
								NebulaDesign.NIGHT_PURPLE, 0.10f)
						: mix(NebulaDesign.DAY_SURFACE,
								NebulaDesign.DAY_ACCENT_SOFT, 0.55f);
				g.setPaint(new GradientPaint(0, 0, top, w, h, low));
				g.fillRoundRect(0, 0, w - 1, h - 1,
						NebulaDesign.RADIUS_CARD, NebulaDesign.RADIUS_CARD);
				g.setPaint(null);
				g.setColor(alphaOf(BORDER, night ? 110 : 130));
				for (int x = 24; x < w; x += 42)
				{
					g.drawLine(x, 12, x, h - 14);
				}
				for (int y = 24; y < h; y += 42)
				{
					g.drawLine(12, y, w - 14, y);
				}
				g.setColor(alphaOf(TEAL, night ? 50 : 34));
				for (int i = 0; i < 5; i++)
				{
					int x = w - 230 + i * 42;
					int y = 34 + (i % 3) * 28;
					g.fillOval(x, y, 6, 6);
					if (i > 0)
					{
						g.drawLine(x - 42 + 3, y - ((i % 3) - ((i - 1) % 3)) * 28 + 3,
								x + 3, y + 3);
					}
				}
				g.setColor(BORDER);
				g.drawRoundRect(0, 0, w - 1, h - 1,
						NebulaDesign.RADIUS_CARD, NebulaDesign.RADIUS_CARD);
			}

		private void paintEmpty(Graphics2D g, int w, int h, String message)
		{
			int cx = w / 2;
			int cy = h / 2 - 34;
			g.setColor(alphaOf(BLUE, 22));
			g.fillOval(cx - 44, cy - 44, 88, 88);
			g.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND,
					BasicStroke.JOIN_ROUND));
			g.setColor(alphaOf(BLUE, 170));
			g.drawOval(cx - 28, cy - 28, 56, 56);
			g.setColor(alphaOf(TEAL, 200));
			g.fillOval(cx - 21, cy - 9, 8, 8);
			g.fillOval(cx + 12, cy - 20, 8, 8);
			g.fillOval(cx + 2, cy + 12, 8, 8);
			g.setColor(alphaOf(TEAL, 120));
			g.drawLine(cx - 17, cy - 5, cx + 16, cy - 16);
			g.drawLine(cx + 16, cy - 16, cx + 6, cy + 16);
			g.setStroke(new BasicStroke(1.0f));
			g.setFont(font(Font.BOLD, 18));
			g.setColor(TEXT);
			String title = "Temporal Graph Foundation";
			FontMetrics titleMetrics = g.getFontMetrics();
			g.drawString(title, (w - titleMetrics.stringWidth(title)) / 2,
					h / 2 + 44);
			g.setFont(font(Font.PLAIN, 13));
			g.setColor(MUTED);
			FontMetrics metrics = g.getFontMetrics();
			String body = ellipsize(message, metrics, Math.max(100, w - 80));
			g.drawString(body, (w - metrics.stringWidth(body)) / 2, h / 2 + 68);
		}

			private void paintFoundation(Graphics2D g, int w, int h)
			{
				int pad = 24;
				TemporalGraphFoundationModelSupport.TimelineFrame frame =
						frameAt(candidate, frameIndex);
				boolean compact = h < 330;
				drawTitle(g, pad, compact ? 32 : 38, w - pad * 2, frame);
				int bodyY = compact ? 64 : 82;
				int footerH = compact ? 0 : (h < 430 ? 56 : 68);
				int footerY = h - pad - footerH;
				if (footerH > 0 && footerY - bodyY < 190)
				{
					footerH = 0;
					footerY = h - pad;
				}
				int bodyH = Math.max(compact ? 126 : 164,
						footerY - bodyY - (footerH > 0 ? 14 : 0));
				int ledgerW = Math.min(350, Math.max(250, w / 4));
				if (w < 760)
				{
					ledgerW = Math.min(260, Math.max(206, w / 3));
				}
				int ledgerX = w - pad - ledgerW;
				int mapW = Math.max(220, ledgerX - pad - 14);
				drawDecisionMap(g, pad, bodyY, mapW, bodyH, frame);
				drawSignalLedger(g, ledgerX, bodyY, ledgerW, bodyH, frame);
				if (footerH > 0)
				{
					drawEvidenceFooter(g, pad, footerY, w - pad * 2,
							Math.max(48, h - pad - footerY), frame);
				}
			}

			private void drawTitle(
					Graphics2D g,
					int x,
					int y,
					int width,
					TemporalGraphFoundationModelSupport.TimelineFrame frame)
			{
				int rightReserved = width > 760 ? 420 : 0;
				g.setFont(font(Font.BOLD, 18));
				g.setColor(TEXT);
				FontMetrics titleMetrics = g.getFontMetrics();
				g.drawString(
						ellipsize(report.targetUser + " -> "
								+ candidate.candidateName(), titleMetrics,
								Math.max(140, width - rightReserved)),
						x, y);
				g.setFont(font(Font.PLAIN, 12));
				g.setColor(MUTED);
				FontMetrics bodyMetrics = g.getFontMetrics();
				String frameText = frame == null ? candidate.path
					: frame.label + " | replay score "
							+ scoreText(displayScore(frame))
							+ " | frame rank #" + frame.rank
							+ " -> active #" + activeCounterfactualRank(candidate)
							+ " | " + frame.auditNote;
				g.drawString(ellipsize(frameText, bodyMetrics,
						Math.max(140, width - rightReserved)), x, y + 22);
				if (rightReserved > 0)
				{
					int pillX = x + width - rightReserved + 8;
					drawMetricPill(g, pillX, y - 25, 122, 36, "Score",
							scoreText(displayScore(frame)), GREEN);
					drawMetricPill(g, pillX + 132, y - 25, 126, 36, "CI",
							scoreText(candidate.confidenceLower) + "-"
									+ scoreText(candidate.confidenceUpper), TEAL);
					drawMetricPill(g, pillX + 268, y - 25, 138, 36, "Rank",
							"#" + candidate.rank + " -> #"
									+ activeCounterfactualRank(candidate),
							AMBER);
				}
			}

			private void drawDecisionMap(
					Graphics2D g,
					int x,
					int y,
					int width,
					int height,
					TemporalGraphFoundationModelSupport.TimelineFrame frame)
			{
				drawPanel(g, x, y, width, height, alphaOf(INK, 235),
						alphaOf(BORDER_STRONG, 150));
				g.setFont(font(Font.BOLD, 11));
				g.setColor(TEAL);
				drawClipped(g, "FUSED EVIDENCE CONSTELLATION", x + 16, y + 22,
						width - 32);
				double[] values = activeSignalValues(frame);
				int cx = x + width / 2;
				int cy = y + Math.max(76,
						Math.min(Math.max(76, height - 64), height / 2 + 14));
				int sidePad = Math.max(46, Math.min(86, width / 6));
				int leftX = x + sidePad;
				int rightX = x + width - sidePad;
				drawConstellationBackdrop(g, x, y, width, height, cx, cy, frame);
				int visibleSignals = height < 210 ? 4 : SIGNAL_LABELS.length;
				int[] signalOrder = visibleSignals == SIGNAL_LABELS.length
						? naturalSignalOrder()
						: sortedSignalOrder(values);
				double[] offsets = {-0.72, -0.42, -0.16, 0.16, 0.42, 0.72};
				for (int row = visibleSignals - 1; row >= 0; row--)
				{
					int i = signalOrder[row];
					double offset = offsets[Math.min(row, offsets.length - 1)]
							* Math.max(58, Math.min(126, height / 3));
					drawSignalArc(g, leftX + 36, cy, rightX - 36, cy,
							cx, cy + (int)Math.round(offset),
							SIGNAL_LABELS[i], values[i], SIGNAL_COLORS[i]);
				}
				String targetFollowee = height < 170 ? "" : safe(report.targetFollowee);
				String candidateFollowee = height < 170
						? "" : safe(candidate.candidateFollowee());
				drawIdentityNode(g, leftX, cy, BLUE, "TARGET", report.targetUser,
						targetFollowee);
				drawIdentityNode(g, rightX, cy, GREEN, "CANDIDATE",
						candidate.candidateName(), candidateFollowee);
				drawFusionCore(g, cx, cy, Math.max(42,
						Math.min(64, Math.min(width, height) / 4)), frame, values);
				if (height >= 188)
				{
					drawPathBanner(g, x + 18, y + 40, width - 36,
							Math.min(34, Math.max(26, height / 8)));
				}
				drawSignalBadges(g, x, y, width, height, cx, cy,
						Math.max(120, width / 4), Math.max(78, height / 3),
						visibleSignals, signalOrder, values);
				if (height >= 224)
				{
					drawReplayTrace(g, x + 18, y + height - 52,
							Math.max(80, width - 36), 34, frame);
				}
			}

			private void drawConstellationBackdrop(
					Graphics2D g,
					int x,
					int y,
					int width,
					int height,
					int cx,
					int cy,
					TemporalGraphFoundationModelSupport.TimelineFrame frame)
			{
				int haloW = Math.max(180, Math.min(width - 60, width * 2 / 3));
				int haloH = Math.max(92, Math.min(height - 34, height * 2 / 3));
				g.setStroke(new BasicStroke(1.0f));
				for (int i = 0; i < 4; i++)
				{
					int inset = i * 22;
					int alpha = 54 - i * 9;
					g.setColor(alphaOf(TEAL, Math.max(16, alpha)));
					g.drawOval(cx - haloW / 2 + inset,
							cy - haloH / 2 + inset / 2,
							Math.max(12, haloW - inset * 2),
							Math.max(12, haloH - inset));
				}
				g.setColor(alphaOf(BLUE, 48));
				g.drawLine(x + 32, cy, x + width - 32, cy);
				if (height < 224)
				{
					// The replay trace band carries this caption when it is
					// visible; only paint it here on short canvases.
					g.setFont(font(Font.BOLD, 9));
					g.setColor(MUTED);
					String label = frame == null ? candidate.path
							: frame.label + " | " + frame.auditNote;
					drawClipped(g, label, x + 18, y + height - 14, width - 36);
				}
			}

			private void drawSignalArc(
					Graphics2D g,
					int x1,
					int y1,
					int x2,
					int y2,
					int cx,
					int controlY,
					String label,
					double value,
					Color color)
			{
				double clamped = clamp01(value);
				int c1x = x1 + Math.max(70, (cx - x1) / 2);
				int c2x = x2 - Math.max(70, (x2 - cx) / 2);
				java.awt.geom.CubicCurve2D.Double curve =
						new java.awt.geom.CubicCurve2D.Double(
								x1,
								y1,
								c1x,
								controlY,
								c2x,
								controlY,
								x2,
								y2);
				g.setStroke(new BasicStroke((float)(10.0 + clamped * 11.0),
						BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
				g.setColor(new Color(color.getRed(), color.getGreen(),
						color.getBlue(), alpha(22 + (int)Math.round(44 * clamped))));
				g.draw(curve);
				g.setStroke(new BasicStroke((float)(1.5 + clamped * 5.5),
						BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
				g.setColor(new Color(color.getRed(), color.getGreen(),
						color.getBlue(), alpha(86 + (int)Math.round(132 * clamped))));
				g.draw(curve);
				double t = (phase * 0.18 + clamped) % 1.0;
				int px = cubicPoint(x1, c1x, c2x, x2, t);
				int py = cubicPoint(y1, controlY, controlY, y2, t);
				int dot = Math.max(4, Math.min(9, (int)Math.round(4 + clamped * 7)));
				g.setColor(new Color(color.getRed(), color.getGreen(),
						color.getBlue(), alpha(110 + (int)Math.round(105 * clamped))));
				g.fillOval(px - dot / 2, py - dot / 2, dot, dot);
				g.setStroke(new BasicStroke(1.0f));
			}

			private void drawPathBanner(
					Graphics2D g,
					int x,
					int y,
					int width,
					int height)
			{
				drawPanel(g, x, y, width, height, alphaOf(PLATE, 205),
						alphaOf(AMBER, 112));
				String title = "GRAPH BRIDGE";
				String detail = candidate.path;
				if (removeGraph)
				{
					title = "GRAPH COUNTERFACTUAL";
					detail = "graph signal intentionally removed in this replay";
				}
				else if (candidate.graph <= 0.04)
				{
					title = "TEXT-LED CANDIDATE";
					detail = "no same-community, direct-edge, or common-neighbor bridge for this pair";
				}
				g.setFont(font(Font.BOLD, 9));
				g.setColor(AMBER);
				drawClipped(g, title, x + 12, y + 13, 124);
				g.setFont(font(Font.PLAIN, 9));
				g.setColor(MUTED);
				drawClipped(g, detail, x + 144, y + 13, width - 156);
			}

			private void drawSignalBadges(
					Graphics2D g,
					int mapX,
					int mapY,
					int mapW,
					int mapH,
					int cx,
					int cy,
					int radiusX,
					int radiusY,
					int visibleSignals,
					int[] signalOrder,
					double[] values)
			{
				int rows = Math.max(1, (visibleSignals + 1) / 2);
				for (int row = 0; row < visibleSignals; row++)
				{
					int signal = signalOrder[row];
					int side = row % 2 == 0 ? -1 : 1;
					int band = row / 2;
					int dy = rows <= 1 ? 0
							: -radiusY + (int)Math.round(
									(2.0 * radiusY * band) / Math.max(1, rows - 1));
					int badgeW = 118;
					int badgeH = 25;
					int bx = cx + side * radiusX - badgeW / 2;
					int by = cy + dy - badgeH / 2;
					if (dy == 0 && rows >= 3)
					{
						// Keep the middle band clear of the identity nodes:
						// float those chips just above the node halo.
						by = cy - 60 - badgeH;
					}
					bx = Math.max(mapX + 12, Math.min(mapX + mapW - badgeW - 12, bx));
					by = Math.max(mapY + 34, Math.min(mapY + mapH - badgeH - 12, by));
					drawSignalBadge(g, bx, by, badgeW, badgeH,
							signal, values[signal]);
				}
			}

			private void drawSignalBadge(
					Graphics2D g,
					int x,
					int y,
					int width,
					int height,
					int signal,
					double value)
			{
				Color color = SIGNAL_COLORS[signal];
				drawPanel(g, x, y, width, height,
						alphaOf(PLATE, 225),
						alphaOf(color, 150));
				int barW = Math.max(20, width - 18);
				g.setColor(TRACK);
				g.fillRoundRect(x + 9, y + height - 7, barW, 4, 4, 4);
				g.setColor(color);
				g.fillRoundRect(x + 9, y + height - 7,
						Math.max(2, (int)Math.round(barW * clamp01(value))),
						4, 4, 4);
				g.setFont(font(Font.BOLD, 9));
				g.setColor(TEXT);
				drawClipped(g, SIGNAL_LABELS[signal] + " " + percent(value),
						x + 9, y + 13, width - 60);
				g.setColor(color);
				drawClipped(g, "+" + decimal(SIGNAL_WEIGHTS[signal] * value),
						x + width - 48, y + 13, 42);
			}

			private void drawSignalLedger(
					Graphics2D g,
					int x,
					int y,
					int width,
					int height,
					TemporalGraphFoundationModelSupport.TimelineFrame frame)
			{
				drawPanel(g, x, y, width, height, alphaOf(INK, 240),
						alphaOf(BORDER_STRONG, 160));
				g.setFont(font(Font.BOLD, 13));
				g.setColor(TEXT);
				drawClipped(g, "Signal Contribution Ledger", x + 14, y + 24,
						width - 28);
				g.setFont(font(Font.PLAIN, 10));
				g.setColor(MUTED);
				String dominant = frame == null ? "dominant "
						+ strongestSignal(activeSignalValues(frame))
						: "dominant " + frame.dominantSignal;
				drawClipped(g, dominant + " | "
						+ activeCounterfactualLabel(), x + 14, y + 41, width - 28);
				drawScoreBand(g, x + 14, y + 52, width - 28, 26, frame);
				double[] values = activeSignalValues(frame);
				int rowY = y + 91;
				if (height >= 310)
				{
					drawSignalRadar(g, x + 20, y + 88, width - 40, 100, values);
					rowY = y + 204;
				}
				int available = Math.max(1, height - (rowY - y) - 12);
				int visibleRows = SIGNAL_LABELS.length;
				if (available < SIGNAL_LABELS.length * 23)
				{
					visibleRows = Math.max(3,
							Math.min(SIGNAL_LABELS.length, available / 18));
				}
				int rowGap = Math.max(17, Math.min(32,
						available / Math.max(1, visibleRows)));
				int rowH = Math.max(15, Math.min(27, rowGap - 5));
				int[] order = visibleRows == SIGNAL_LABELS.length
						? naturalSignalOrder()
						: sortedSignalOrder(values);
				for (int i = 0; i < visibleRows; i++)
				{
					int signal = order[i];
					drawLedgerRow(g, x + 14, rowY + i * rowGap,
							width - 28, rowH, signal, values[signal]);
				}
			}

			private void drawSignalRadar(
					Graphics2D g,
					int x,
					int y,
					int width,
					int height,
					double[] values)
			{
				int cx = x + width / 2;
				int cy = y + height / 2;
				int radius = Math.max(34, Math.min(width / 2 - 38, height / 2 - 6));
				g.setStroke(new BasicStroke(1.0f));
				for (int level = 1; level <= 4; level++)
				{
					double scale = level / 4.0;
					java.awt.Polygon grid = radarPolygon(cx, cy, radius * scale,
							filledValues(1.0));
					g.setColor(alphaOf(BORDER_STRONG, 70 + level * 12));
					g.drawPolygon(grid);
				}
				for (int i = 0; i < SIGNAL_LABELS.length; i++)
				{
					double angle = radarAngle(i);
					int ex = cx + (int)Math.round(Math.cos(angle) * radius);
					int ey = cy + (int)Math.round(Math.sin(angle) * radius);
					g.setColor(alphaOf(BORDER_STRONG, 140));
					g.drawLine(cx, cy, ex, ey);
					g.setFont(font(Font.BOLD, 8));
					g.setColor(SIGNAL_COLORS[i]);
					drawClipped(g, SIGNAL_LABELS[i].substring(0, 1),
							ex - 4, ey + 3, 12);
				}
				double[] challenger = challengerSignalValues();
				java.awt.Polygon challengerShape =
						radarPolygon(cx, cy, radius, challenger);
				g.setColor(alphaOf(AMBER, 40));
				g.fillPolygon(challengerShape);
				g.setColor(alphaOf(AMBER, 120));
				g.drawPolygon(challengerShape);
				java.awt.Polygon shape = radarPolygon(cx, cy, radius, values);
				g.setColor(alphaOf(GREEN, 64));
				g.fillPolygon(shape);
				g.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND,
						BasicStroke.JOIN_ROUND));
				g.setColor(alphaOf(GREEN, 210));
				g.drawPolygon(shape);
				g.setStroke(new BasicStroke(1.0f));
				g.setFont(font(Font.BOLD, 9));
				g.setColor(MUTED);
				centerString(g, "candidate shape", cx, y + height + 11);
			}

			private void drawEvidenceFooter(
					Graphics2D g,
					int x,
					int y,
					int width,
					int height,
					TemporalGraphFoundationModelSupport.TimelineFrame frame)
			{
				drawPanel(g, x, y, width, height, alphaOf(INK, 235),
						alphaOf(BORDER_STRONG, 160));
				int gap = 8;
				int columns = width >= 980 ? 4 : 3;
				int colW = Math.max(96, (width - gap * (columns + 1)) / columns);
				int colX = x + gap;
				drawFooterColumn(g, colX, y + 8, colW, height - 16,
						"SHARED TERMS", join(candidate.sharedTerms, 4),
						BLUE, true);
				colX += colW + gap;
				drawFooterColumn(g, colX, y + 8, colW, height - 16,
						"EVIDENCE LIMITS", evidenceLimitText(),
						RED, true);
				colX += colW + gap;
				drawFooterColumn(g, colX, y + 8, colW, height - 16,
						"CHALLENGER", challengerFooterText(),
						AMBER, false);
				if (columns > 3)
				{
					colX += colW + gap;
					String replay = (frame == null ? "final frame" : frame.label)
							+ " | " + activeCounterfactualLabel()
							+ " | " + scoreText(displayScore(frame));
					drawFooterColumn(g, colX, y + 8, colW, height - 16,
							"REPLAY STATE", replay, TEAL, false);
				}
			}

			private void drawMetricPill(
					Graphics2D g,
					int x,
					int y,
					int w,
					int h,
					String label,
					String value,
					Color color)
			{
				drawPanel(g, x, y, w, h, new Color(color.getRed(), color.getGreen(),
						color.getBlue(), 34), new Color(color.getRed(),
						color.getGreen(), color.getBlue(), 128));
				g.setFont(font(Font.BOLD, 8));
				g.setColor(color);
				drawClipped(g, label.toUpperCase(Locale.US), x + 9, y + 12, w - 18);
				g.setFont(font(Font.BOLD, 13));
				g.setColor(TEXT);
				drawClipped(g, value, x + 9, y + 28, w - 18);
			}

			private void drawSignalLane(
					Graphics2D g,
					int x1,
					int y1,
					int x2,
					int y2,
					String label,
					double value,
					Color color,
					double contribution,
					boolean drawLabel)
			{
				if (x2 <= x1)
				{
					return;
				}
				float stroke = (float)(1.2 + 7.0 * clamp01(value));
				g.setStroke(new BasicStroke(stroke, BasicStroke.CAP_ROUND,
						BasicStroke.JOIN_ROUND));
				g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(),
						alpha(38 + (int)Math.round(152.0 * clamp01(value)))));
				g.drawLine(x1, y1, x2, y2);
				int pulse = x1 + (int)Math.round((x2 - x1)
						* ((phase + value) % 1.0));
				g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(),
						alpha(80 + (int)Math.round(100.0 * clamp01(value)))));
				int dot = Math.max(3, Math.min(7, (int)Math.round(3 + 5 * value)));
				g.fillOval(pulse - dot / 2, y1 - dot / 2, dot, dot);
				if (drawLabel)
				{
					int chipW = 112;
					int chipX = Math.max(x1, Math.min(x2 - chipW, x2 - 142));
					drawPanel(g, chipX, y1 - 11, chipW, 22,
							alphaOf(PLATE, 228),
							alphaOf(color, 128));
					g.setFont(font(Font.BOLD, 9));
					g.setColor(TEXT);
					drawClipped(g, label + " " + percent(value),
							chipX + 7, y1 + 4, chipW - 14);
					g.setFont(font(Font.PLAIN, 9));
					g.setColor(MUTED);
					drawClipped(g, "+" + decimal(contribution),
							chipX + 68, y1 + 4, 38);
				}
				g.setStroke(new BasicStroke(1.0f));
			}

			private void drawIdentityNode(
					Graphics2D g,
					int cx,
					int cy,
					Color color,
					String caption,
					String name,
					String followee)
			{
				int glow = 8 + (int)Math.round(3.0 * Math.sin(phase));
				g.setColor(alphaOf(color, 34));
				g.fillOval(cx - 34 - glow, cy - 34 - glow,
						68 + glow * 2, 68 + glow * 2);
				g.setColor(alphaOf(PLATE, 235));
				g.fillOval(cx - 34, cy - 34, 68, 68);
				g.setStroke(new BasicStroke(3.0f));
				g.setColor(color);
				g.drawOval(cx - 34, cy - 34, 68, 68);
				g.setFont(font(Font.BOLD, 9));
				g.setColor(color);
				centerString(g, caption, cx, cy - 7);
				g.setFont(font(Font.BOLD, 11));
				g.setColor(TEXT);
				centerString(g, ellipsize(name, g.getFontMetrics(), 112), cx, cy + 9);
				g.setFont(font(Font.PLAIN, 10));
				g.setColor(MUTED);
				if (followee != null && followee.trim().length() > 0)
				{
					centerString(g, ellipsize(followee, g.getFontMetrics(), 128),
							cx, cy + 50);
				}
			}

			private void drawFusionCore(
					Graphics2D g,
					int cx,
					int cy,
					int radius,
					TemporalGraphFoundationModelSupport.TimelineFrame frame,
					double[] values)
			{
				double score = displayScore(frame);
				g.setColor(alphaOf(PLATE, 240));
				g.fillOval(cx - radius, cy - radius, radius * 2, radius * 2);
				g.setStroke(new BasicStroke(10.0f, BasicStroke.CAP_ROUND,
						BasicStroke.JOIN_ROUND));
				g.setColor(alphaOf(TRACK, 220));
				g.drawOval(cx - radius, cy - radius, radius * 2, radius * 2);
				int ciStart = 90 - (int)Math.round(candidate.confidenceUpper * 360.0);
				int ciExtent = Math.max(3, (int)Math.round(
						(candidate.confidenceUpper - candidate.confidenceLower) * 360.0));
				g.setColor(alphaOf(TEAL, 160));
				g.drawArc(cx - radius, cy - radius, radius * 2, radius * 2,
						ciStart, ciExtent);
				g.setStroke(new BasicStroke(7.0f, BasicStroke.CAP_ROUND,
						BasicStroke.JOIN_ROUND));
				g.setColor(score >= candidate.challengerScore ? GREEN : AMBER);
				g.drawArc(cx - radius + 8, cy - radius + 8,
						(radius - 8) * 2, (radius - 8) * 2, 90,
						-(int)Math.round(score * 360.0));
				g.setFont(font(Font.BOLD, 19));
				g.setColor(TEXT);
				centerString(g, scoreText(score), cx, cy - 2);
				g.setFont(font(Font.BOLD, 9));
				g.setColor(MUTED);
				centerString(g, "TGF SCORE", cx, cy + 16);
				g.setFont(font(Font.BOLD, 10));
				g.setColor(TEAL);
				centerString(g, strongestSignal(values).toUpperCase(Locale.US),
						cx, cy - radius - 14);
				g.setStroke(new BasicStroke(1.0f));
			}

			private void drawBridgeBadge(Graphics2D g, int cx, int y, int width)
			{
				int x = cx - width / 2;
				drawPanel(g, x, y - 15, width, 30, alphaOf(AMBER, 28),
						alphaOf(AMBER, 112));
				g.setFont(font(Font.BOLD, 9));
				g.setColor(AMBER);
				centerString(g, "GRAPH BRIDGE", cx, y - 2);
				g.setFont(font(Font.PLAIN, 9));
				g.setColor(MUTED);
				centerString(g, ellipsize(candidate.path, g.getFontMetrics(),
						Math.max(40, width - 18)), cx, y + 11);
			}

			private void drawReplayTrace(
					Graphics2D g,
					int x,
					int y,
					int width,
					int height,
					TemporalGraphFoundationModelSupport.TimelineFrame frame)
			{
				drawPanel(g, x, y, width, height, alphaOf(PLATE, 205),
						alphaOf(BORDER_STRONG, 140));
				if (candidate.timeline == null || candidate.timeline.isEmpty())
				{
					return;
				}
				int lineX = x + 12;
				int lineY = y + 19;
				int lineW = Math.max(20, width - 24);
				g.setColor(alphaOf(BORDER_STRONG, 190));
				g.drawLine(lineX, lineY, lineX + lineW, lineY);
				int previousX = -1;
				int previousY = -1;
				for (int i = 0; i < candidate.timeline.size(); i++)
				{
					TemporalGraphFoundationModelSupport.TimelineFrame point =
							candidate.timeline.get(i);
					int px = lineX + (int)Math.round(lineW * point.progress);
					int py = lineY - (int)Math.round(13.0 * displayScore(point));
					if (previousX >= 0)
					{
						g.setColor(new Color(BLUE.getRed(), BLUE.getGreen(),
								BLUE.getBlue(), 150));
						g.drawLine(previousX, previousY, px, py);
					}
					g.setColor(i == frameIndex ? GREEN : BLUE);
					g.fillOval(px - 3, py - 3, 6, 6);
					previousX = px;
					previousY = py;
				}
				g.setFont(font(Font.BOLD, 9));
				g.setColor(MUTED);
				String text = frame == null ? "final replay"
						: frame.label + " | " + frame.evidence;
				drawClipped(g, text, x + 12, y + height - 6, width - 24);
			}

			private void drawScoreBand(
					Graphics2D g,
					int x,
					int y,
					int width,
					int height,
					TemporalGraphFoundationModelSupport.TimelineFrame frame)
			{
				g.setColor(TRACK);
				g.fillRoundRect(x, y + height - 9, width, 7, 7, 7);
				int lo = x + (int)Math.round(width * candidate.confidenceLower);
				int hi = x + (int)Math.round(width * candidate.confidenceUpper);
				g.setColor(alphaOf(TEAL, 100));
				g.fillRoundRect(Math.min(lo, hi), y + height - 10,
						Math.max(3, Math.abs(hi - lo)), 9, 9, 9);
				int scoreX = x + (int)Math.round(width * displayScore(frame));
				g.setColor(GREEN);
				g.fillRoundRect(scoreX - 2, y + height - 14, 4, 15, 4, 4);
				g.setFont(font(Font.BOLD, 9));
				g.setColor(MUTED);
				drawClipped(g, "CI " + scoreText(candidate.confidenceLower)
						+ "-" + scoreText(candidate.confidenceUpper)
						+ " | p~" + decimal(candidate.calibrationPValue),
						x, y + 10, width);
			}

			private void drawLedgerRow(
					Graphics2D g,
					int x,
					int y,
					int width,
					int height,
					int index,
					double value)
			{
				Color color = SIGNAL_COLORS[index];
				double contribution = value * SIGNAL_WEIGHTS[index];
				double delta = contribution - challengerContribution(index);
				g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(),
						34));
				g.fillRoundRect(x, y, width, height, 7, 7);
				g.setColor(alphaOf(BORDER_STRONG, 170));
				g.drawRoundRect(x, y, width, height, 7, 7);
				g.setFont(font(Font.BOLD, 10));
				g.setColor(TEXT);
				drawClipped(g, SIGNAL_LABELS[index], x + 8, y + 14,
						Math.max(44, width / 4));
				int barX = x + Math.max(58, width / 4);
				int barW = Math.max(34, width - (barX - x) - 76);
				g.setColor(TRACK);
				g.fillRoundRect(barX, y + height / 2 - 4, barW, 8, 8, 8);
				g.setColor(color);
				g.fillRoundRect(barX, y + height / 2 - 4,
						Math.max(2, (int)Math.round(barW * clamp01(value))),
						8, 8, 8);
				g.setFont(font(Font.BOLD, 9));
				g.setColor(MUTED);
				drawClipped(g, percent(value), barX + barW + 7, y + 12, 42);
				g.setColor(delta >= 0.0 ? GREEN : RED);
				drawClipped(g, (delta >= 0.0 ? "+" : "")
						+ decimal(delta), barX + barW + 7, y + height - 4, 48);
			}

			private void drawFooterColumn(
					Graphics2D g,
					int x,
					int y,
					int width,
					int height,
					String title,
					String value,
					Color color,
					boolean chips)
			{
				drawPanel(g, x, y, width, height, new Color(color.getRed(),
						color.getGreen(), color.getBlue(), 24),
						new Color(color.getRed(), color.getGreen(),
								color.getBlue(), 98));
				g.setFont(font(Font.BOLD, 9));
				g.setColor(color);
				drawClipped(g, title, x + 9, y + 13, width - 18);
				if (chips)
				{
					drawChipStrip(g, value, x + 9, y + 22, width - 18,
							height - 26, color);
				}
				else
				{
					g.setFont(font(Font.BOLD, 10));
					g.setColor(TEXT);
					drawClipped(g, value, x + 9, y + 33, width - 18);
					g.setFont(font(Font.PLAIN, 9));
					g.setColor(MUTED);
					drawClipped(g, candidate.reliabilityBand + " | "
							+ candidate.calibrationVerdict,
							x + 9, y + Math.min(height - 6, 49), width - 18);
				}
			}

			private void drawChipStrip(
					Graphics2D g,
					String value,
					int x,
					int y,
					int width,
					int height,
					Color color)
			{
				String[] parts = safe(value).split(",");
				int cx = x;
				int cy = y;
				g.setFont(font(Font.BOLD, 9));
				for (int i = 0; i < parts.length && i < 4; i++)
				{
					String part = parts[i].trim();
					if (part.length() == 0)
					{
						continue;
					}
					FontMetrics metrics = g.getFontMetrics();
					int chipW = Math.min(width, metrics.stringWidth(part) + 16);
					if (cx + chipW > x + width)
					{
						cx = x;
						cy += 20;
					}
					if (cy + 17 > y + height)
					{
						return;
					}
					g.setColor(new Color(color.getRed(), color.getGreen(),
							color.getBlue(), 46));
					g.fillRoundRect(cx, cy, chipW, 17, 8, 8);
					g.setColor(new Color(color.getRed(), color.getGreen(),
							color.getBlue(), 128));
					g.drawRoundRect(cx, cy, chipW, 17, 8, 8);
					g.setColor(TEXT);
					drawClipped(g, part, cx + 8, cy + 12, chipW - 16);
					cx += chipW + 6;
				}
			}

			private void drawPanel(
					Graphics2D g,
					int x,
					int y,
					int width,
					int height,
					Color fill,
					Color border)
			{
				if (width <= 0 || height <= 0)
				{
					return;
				}
				g.setColor(fill);
				g.fillRoundRect(x, y, width, height, 10, 10);
				g.setColor(border);
				g.drawRoundRect(x, y, width, height, 10, 10);
			}

			private double[] activeSignalValues(
					TemporalGraphFoundationModelSupport.TimelineFrame frame)
			{
				double textValue = removeTopTerm ? candidate.withoutTopTermText
						: valueFromFrame(frame, candidate.text, 0);
				double graphValue = removeGraph ? 0.0
						: valueFromFrame(frame, candidate.graph, 1);
				double timeValue = removeTime ? 0.0
						: valueFromFrame(frame, candidate.time, 2);
				double bridgeValue = removeBridge ? 0.0
						: valueFromFrame(frame, candidate.bridge, 3);
				double debiasValue = removeDebias ? 0.0
						: valueFromFrame(frame, candidate.debias, 4);
				double robustValue = (!removeGraph && !removeTime && !removeBridge
						&& !removeTopTerm)
						? valueFromFrame(frame, candidate.robust, 5)
						: visualRobustness(textValue, graphValue, timeValue, bridgeValue);
				return new double[] {
						clamp01(textValue),
						clamp01(graphValue),
						clamp01(timeValue),
						clamp01(bridgeValue),
						clamp01(debiasValue),
						clamp01(robustValue)};
			}

			private java.awt.Polygon radarPolygon(
					int cx,
					int cy,
					double radius,
					double[] values)
			{
				java.awt.Polygon polygon = new java.awt.Polygon();
				for (int i = 0; i < SIGNAL_LABELS.length; i++)
				{
					double angle = radarAngle(i);
					double value = i < values.length ? clamp01(values[i]) : 0.0;
					polygon.addPoint(
							cx + (int)Math.round(Math.cos(angle) * radius * value),
							cy + (int)Math.round(Math.sin(angle) * radius * value));
				}
				return polygon;
			}

			private double[] filledValues(double value)
			{
				double[] values = new double[SIGNAL_LABELS.length];
				for (int i = 0; i < values.length; i++)
				{
					values[i] = clamp01(value);
				}
				return values;
			}

			private double radarAngle(int signal)
			{
				return -Math.PI / 2.0
						+ Math.PI * 2.0 * signal / SIGNAL_LABELS.length;
			}

			private double[] challengerSignalValues()
			{
				return new double[] {
						removeTopTerm ? 0.0 : clamp01(candidate.challengerText),
						removeGraph ? 0.0 : clamp01(candidate.challengerGraph),
						removeTime ? 0.0 : clamp01(candidate.challengerTime),
						removeBridge ? 0.0 : clamp01(candidate.challengerBridge),
						removeDebias ? 0.0 : clamp01(candidate.challengerDebias),
						clamp01(candidate.challengerRobust)};
			}

			private double challengerContribution(int signal)
			{
				double value = 0.0;
				if (signal == 0)
				{
					value = removeTopTerm ? 0.0 : candidate.challengerText;
				}
				else if (signal == 1)
				{
					value = removeGraph ? 0.0 : candidate.challengerGraph;
				}
				else if (signal == 2)
				{
					value = removeTime ? 0.0 : candidate.challengerTime;
				}
				else if (signal == 3)
				{
					value = removeBridge ? 0.0 : candidate.challengerBridge;
				}
				else if (signal == 4)
				{
					value = removeDebias ? 0.0 : candidate.challengerDebias;
				}
				else
				{
					value = candidate.challengerRobust;
				}
				return SIGNAL_WEIGHTS[signal] * clamp01(value);
			}

			private double visualRobustness(
					double text,
					double graph,
					double time,
					double bridge)
			{
				double sum = text + graph + time + bridge;
				if (sum <= 0.0)
				{
					return 0.0;
				}
				double max = Math.max(Math.max(text, graph), Math.max(time, bridge));
				return clamp01((1.0 - max / sum) * 1.35);
			}

			private String strongestSignal(double[] values)
			{
				int best = 0;
				for (int i = 1; i < values.length; i++)
				{
					if (values[i] * SIGNAL_WEIGHTS[i]
							> values[best] * SIGNAL_WEIGHTS[best])
					{
						best = i;
					}
				}
				return SIGNAL_LABELS[best];
			}

			private int[] naturalSignalOrder()
			{
				int[] order = new int[SIGNAL_LABELS.length];
				for (int i = 0; i < order.length; i++)
				{
					order[i] = i;
				}
				return order;
			}

			private int[] sortedSignalOrder(double[] values)
			{
				int[] order = naturalSignalOrder();
				for (int i = 0; i < order.length; i++)
				{
					for (int j = i + 1; j < order.length; j++)
					{
						double left = values[order[i]] * SIGNAL_WEIGHTS[order[i]];
						double right = values[order[j]] * SIGNAL_WEIGHTS[order[j]];
						if (right > left)
						{
							int tmp = order[i];
							order[i] = order[j];
							order[j] = tmp;
						}
					}
				}
				return order;
			}

			private String challengerFooterText()
			{
				if (candidate.challengerName == null
						|| candidate.challengerName.length() == 0)
				{
					return "No retained challenger";
				}
				return candidate.candidateName() + " "
						+ (candidate.challengerGap >= 0.0 ? "leads " : "trails ")
						+ candidate.challengerName + " by "
						+ scoreText(Math.abs(candidate.challengerGap))
						+ " | " + candidate.bestContrastSignal;
			}

			private String evidenceLimitText()
			{
				if (candidate.riskFlags == null || candidate.riskFlags.isEmpty())
				{
					return "-";
				}
				ArrayList<String> values = new ArrayList<String>();
				for (String flag : candidate.riskFlags)
				{
					if ("no usable graph path".equals(flag))
					{
						values.add("text-led: no pair bridge");
					}
					else if ("graph artifact edge file is sparse/empty".equals(flag))
					{
						values.add("sparse graph artifact");
					}
					else
					{
						values.add(flag);
					}
					if (values.size() >= 3)
					{
						break;
					}
				}
				return join(values, values.size());
			}

			private void centerString(Graphics2D g, String value, int cx, int y)
			{
				FontMetrics metrics = g.getFontMetrics();
				String text = safe(value);
				g.drawString(text, cx - metrics.stringWidth(text) / 2, y);
			}

			private static int alpha(int value)
			{
				return Math.max(0, Math.min(255, value));
			}

			private static int cubicPoint(
					int p0,
					int p1,
					int p2,
					int p3,
					double t)
			{
				double one = 1.0 - t;
				double value = one * one * one * p0
						+ 3.0 * one * one * t * p1
						+ 3.0 * one * t * t * p2
						+ t * t * t * p3;
				return (int)Math.round(value);
			}

			private static double clamp01(double value)
			{
				return Math.max(0.0, Math.min(1.0, value));
			}

				private double displayScore(
						TemporalGraphFoundationModelSupport.TimelineFrame frame)
				{
				double base = frame == null ? candidate.finalScore : frame.score;
				if (!removeGraph && !removeTime && !removeBridge
						&& !removeDebias && !removeTopTerm)
				{
					return base;
				}
				double counterfactual = candidate.counterfactualScore(
						removeGraph,
						removeTime,
						removeBridge,
						removeDebias,
						removeTopTerm);
				if (candidate.finalScore <= 0.0)
				{
					return counterfactual;
				}
					return Math.max(0.0, Math.min(1.0,
							base * (counterfactual / candidate.finalScore)));
				}

				private int activeCounterfactualRank(
						TemporalGraphFoundationModelSupport.CandidateScore score)
				{
					if (report == null || score == null || report.topCandidates.isEmpty())
					{
						return 0;
					}
					double selected = activeCounterfactualScore(score);
					int rank = 1;
					for (TemporalGraphFoundationModelSupport.CandidateScore other
							: report.topCandidates)
					{
						if (other == score)
						{
							continue;
						}
						double otherScore = activeCounterfactualScore(other);
						if (otherScore > selected
								|| (otherScore == selected
										&& other.candidateName().compareTo(
												score.candidateName()) < 0))
						{
							rank++;
						}
					}
					return rank;
				}

				private double activeCounterfactualScore(
						TemporalGraphFoundationModelSupport.CandidateScore score)
				{
					if (score == null)
					{
						return 0.0;
					}
					return score.counterfactualScore(
							removeGraph,
							removeTime,
							removeBridge,
							removeDebias,
							removeTopTerm);
				}

				private void drawClipped(
						Graphics2D g,
						String value,
						int x,
						int y,
						int width)
				{
					FontMetrics metrics = g.getFontMetrics();
					g.drawString(ellipsize(value, metrics, Math.max(4, width)), x, y);
				}

				private double valueFromFrame(
						TemporalGraphFoundationModelSupport.TimelineFrame frame,
					double fallback,
					int signal)
			{
				if (frame == null)
				{
					return fallback;
				}
				if (signal == 0)
				{
					return frame.text;
				}
				if (signal == 1)
				{
					return frame.graph;
				}
				if (signal == 2)
				{
					return frame.time;
				}
				if (signal == 3)
				{
					return frame.bridge;
				}
				if (signal == 4)
				{
					return frame.debias;
				}
				return frame.robust;
			}

			private String activeCounterfactualLabel()
			{
				ArrayList<String> active = new ArrayList<String>();
				if (removeGraph)
				{
					active.add("graph off");
				}
				if (removeTime)
				{
					active.add("time off");
				}
				if (removeBridge)
				{
					active.add("bridge off");
				}
				if (removeDebias)
				{
					active.add("debias off");
				}
				if (removeTopTerm)
				{
					active.add("top term off");
				}
				return active.isEmpty() ? "observed replay" : join(active, 5);
			}

		private static String join(List<String> values, int limit)
		{
			ArrayList<String> safeValues = new ArrayList<String>();
			for (String value : values)
			{
				if (value != null && value.trim().length() > 0)
				{
					safeValues.add(value.trim());
				}
				if (safeValues.size() >= limit)
				{
					break;
				}
			}
			if (safeValues.isEmpty())
			{
				return "-";
			}
			StringBuilder builder = new StringBuilder();
			for (int i = 0; i < safeValues.size(); i++)
			{
				if (i > 0)
				{
					builder.append(", ");
				}
				builder.append(safeValues.get(i));
			}
			return builder.toString();
			}
		}

		private static final class ResearchCanvas extends RoundedPanel
		{
			private TemporalGraphFoundationModelSupport.Result result;
			private TemporalGraphFoundationModelSupport.TargetReport report;
			private TemporalGraphFoundationModelSupport.CandidateScore candidate;
			private int frameIndex;
			private boolean removeGraph;
			private boolean removeTime;
			private boolean removeBridge;
			private boolean removeDebias;
			private boolean removeTopTerm;

			ResearchCanvas()
			{
				super(SURFACE_ALT);
				setBorder(new EmptyBorder(8, 8, 8, 8));
			}

			void setSelection(
					TemporalGraphFoundationModelSupport.Result result,
					TemporalGraphFoundationModelSupport.TargetReport report,
					TemporalGraphFoundationModelSupport.CandidateScore candidate,
					int frameIndex,
					boolean removeGraph,
					boolean removeTime,
					boolean removeBridge,
					boolean removeDebias,
					boolean removeTopTerm)
			{
				this.result = result;
				this.report = report;
				this.candidate = candidate;
				this.frameIndex = frameIndex;
				this.removeGraph = removeGraph;
				this.removeTime = removeTime;
				this.removeBridge = removeBridge;
				this.removeDebias = removeDebias;
				this.removeTopTerm = removeTopTerm;
				repaint();
			}

			protected void paintComponent(Graphics graphics)
			{
				super.paintComponent(graphics);
				Graphics2D g = (Graphics2D) graphics.create();
				NebulaDesign.prepare(g);
				int w = getWidth();
				int h = getHeight();
				if (result == null)
				{
					drawEmpty(g, w, h, "Waiting for TGF research trace.");
					g.dispose();
					return;
				}
				drawResearch(g, w, h);
				g.dispose();
			}

			private void drawResearch(Graphics2D g, int w, int h)
			{
				int pad = 10;
				int metricH = h < 220 ? 42 : 50;
				TemporalGraphFoundationModelSupport.ResearchSummary summary =
						result.getResearchSummary();
				int metricW = Math.max(68, (w - pad * 2 - 24) / 5);
				drawMetric(g, pad, pad, metricW, metricH, "Hit@1",
						percent(summary.hitAtOne), GREEN, summary.hitAtOne);
				drawMetric(g, pad + metricW + 6, pad, metricW, metricH, "MRR",
						decimal(summary.mrr), BLUE, summary.mrr);
				drawMetric(g, pad + (metricW + 6) * 2, pad, metricW, metricH,
						"Cal P", decimal(summary.averageCalibrationPValue), TEAL,
						summary.averageCalibrationPValue);
				drawMetric(g, pad + (metricW + 6) * 3, pad, metricW, metricH,
						"Ready", percent(summary.foundationReadiness.overall), AMBER,
						summary.foundationReadiness.overall);
				drawMetric(g, pad + (metricW + 6) * 4, pad, metricW, metricH,
						"Fragile", percent(summary.averageCounterfactualFragility), PINK,
						summary.averageCounterfactualFragility);

				int y = pad + metricH + 10;
				int bodyH = Math.max(62, h - y - pad);
				int gap = 8;
				int availableW = w - pad * 2 - gap * 2;
				int replayW = Math.max(260, (int)Math.round(availableW * 0.42));
				int counterW = Math.max(220, (int)Math.round(availableW * 0.30));
				int readyW = Math.max(180, availableW - replayW - counterW);
				if (replayW + counterW + readyW > availableW)
				{
					int overflow = replayW + counterW + readyW - availableW;
					replayW = Math.max(230, replayW - overflow);
				}
				drawTimeline(g, pad, y, replayW, bodyH);
				drawCounterfactuals(g, pad + replayW + gap, y, counterW, bodyH);
				drawBenchmarkBoard(g, pad + replayW + gap + counterW + gap, y,
						readyW, bodyH);
			}

			private void drawTimeline(Graphics2D g, int x, int y, int w, int h)
			{
				drawPanel(g, x, y, w, h);
				g.setFont(font(Font.BOLD, 10));
				g.setColor(BLUE);
				drawClipped(g, "REPLAY DIRECTOR", x + 12, y + 17, w - 24);
				if (candidate == null || candidate.timeline.isEmpty())
				{
					g.setFont(font(Font.PLAIN, 11));
					g.setColor(MUTED);
					drawClipped(g, result.getResearchSummary().headline,
							x + 12, y + 42, w - 24);
					return;
				}
				TemporalGraphFoundationModelSupport.TimelineFrame selected =
						frameAt(candidate, frameIndex);
				g.setFont(font(Font.BOLD, 10));
				g.setColor(TEXT);
				drawClipped(g, selected == null ? "No frame selected"
						: selected.label + " | rank #" + selected.rank
								+ " | " + selected.dominantSignal,
						x + 128, y + 17, w - 140);
				int graphX = x + 18;
				int graphY = y + 34;
				int graphW = Math.max(80, w - 36);
				int graphH = Math.max(36, h - 94);
				int axisY = graphY + graphH;
				g.setStroke(new BasicStroke(1.0f));
				for (int i = 0; i <= 3; i++)
				{
					int gridY = graphY + (int)Math.round(graphH * i / 3.0);
					g.setColor(alphaOf(BORDER_STRONG, 100));
					g.drawLine(graphX, gridY, graphX + graphW, gridY);
				}
				g.setColor(alphaOf(BORDER_STRONG, 200));
				g.drawLine(graphX, axisY, graphX + graphW, axisY);
				int previousX = -1;
				int previousY = -1;
				java.awt.Polygon area = new java.awt.Polygon();
				area.addPoint(graphX, axisY);
				for (int i = 0; i < candidate.timeline.size(); i++)
				{
					TemporalGraphFoundationModelSupport.TimelineFrame frame =
							candidate.timeline.get(i);
					int px = graphX + (int)Math.round(graphW * frame.progress);
					int py = graphY + graphH
							- (int)Math.round(graphH * displayScore(frame));
					area.addPoint(px, py);
					if (previousX >= 0)
					{
						g.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND,
								BasicStroke.JOIN_ROUND));
						Color tone = signalColor(frame.dominantSignal);
						g.setColor(new Color(tone.getRed(), tone.getGreen(),
								tone.getBlue(), 210));
						g.drawLine(previousX, previousY, px, py);
					}
					previousX = px;
					previousY = py;
				}
				area.addPoint(graphX + graphW, axisY);
				g.setColor(alphaOf(BLUE, 30));
				g.fillPolygon(area);
				for (int i = 0; i < candidate.timeline.size(); i++)
				{
					TemporalGraphFoundationModelSupport.TimelineFrame frame =
							candidate.timeline.get(i);
					int px = graphX + (int)Math.round(graphW * frame.progress);
					int py = graphY + graphH
							- (int)Math.round(graphH * displayScore(frame));
					boolean current = i == frameIndex;
					Color tone = current ? GREEN : signalColor(frame.dominantSignal);
					g.setColor(new Color(tone.getRed(), tone.getGreen(),
							tone.getBlue(), current ? 72 : 28));
					g.fillOval(px - (current ? 8 : 5), py - (current ? 8 : 5),
							current ? 16 : 10, current ? 16 : 10);
					g.setColor(tone);
					g.fillOval(px - (current ? 4 : 3), py - (current ? 4 : 3),
							current ? 8 : 6, current ? 8 : 6);
					if (current)
					{
						g.setColor(new Color(tone.getRed(), tone.getGreen(),
								tone.getBlue(), 105));
						g.drawLine(px, graphY, px, axisY + 8);
					}
				}
				g.setFont(font(Font.BOLD, 11));
				g.setColor(TEXT);
				String score = selected == null ? "--" : scoreText(displayScore(selected));
				String delta = selected == null ? "--" : signedDecimal(selected.scoreDelta);
				drawClipped(g, "score " + score + " | delta " + delta,
						x + 12, y + h - 32, w / 2);
				g.setFont(font(Font.PLAIN, 10));
				g.setColor(MUTED);
				drawClipped(g,
						selected == null ? "" : selected.auditNote + " " + selected.evidence,
						x + 12,
						y + h - 14,
						w - 24);
			}

			private void drawCounterfactuals(Graphics2D g, int x, int y, int w, int h)
			{
				drawPanel(g, x, y, w, h);
				g.setFont(font(Font.BOLD, 10));
				g.setColor(AMBER);
				drawClipped(g, "COUNTERFACTUAL COCKPIT", x + 12, y + 17, w - 24);
				if (candidate == null || report == null)
				{
					g.setFont(font(Font.PLAIN, 11));
					g.setColor(MUTED);
					drawClipped(g, "Select a candidate to inspect rank stress tests.",
							x + 12, y + 42, w - 24);
					return;
				}
				int observedRank = candidate.rank;
				int activeRank = activeCounterfactualRank(candidate);
				double activeScore = activeCounterfactualScore();
				int cardY = y + 28;
				int cardH = 44;
				int cardW = Math.max(76, (w - 34) / 2);
				drawImpactCard(g, x + 12, cardY, cardW, cardH,
						"Observed", "#" + observedRank,
						scoreText(candidate.finalScore), BLUE);
				Color activeTone = activeRank > observedRank ? RED
						: activeRank < observedRank ? GREEN : AMBER;
				drawImpactCard(g, x + 22 + cardW, cardY, w - 34 - cardW,
						cardH, "Active", "#" + activeRank,
						scoreText(activeScore), activeTone);
				g.setFont(font(Font.BOLD, 11));
				g.setColor(TEXT);
				drawClipped(g, activeCounterfactualLabel(),
						x + 12, cardY + cardH + 18, w - 24);
				g.setFont(font(Font.PLAIN, 10));
				g.setColor(MUTED);
				drawClipped(g, "probes ranked by score drop",
						x + 12, cardY + cardH + 33, w - 24);
				int rowY = cardY + cardH + 42;
				int rowH = Math.max(17, Math.min(22, (y + h - rowY - 10) / 3));
				int[] probeOrder = sortedProbeOrder();
				for (int i = 0; i < probeOrder.length && i < 4
						&& rowY + rowH < y + h - 8; i++)
				{
					drawProbe(g, x + 12, rowY, w - 24, rowH,
							candidate.counterfactuals.get(probeOrder[i]));
					rowY += rowH + 4;
				}
			}

			private void drawImpactCard(
					Graphics2D g,
					int x,
					int y,
					int w,
					int h,
					String title,
					String rank,
					String score,
					Color tone)
			{
				g.setColor(new Color(tone.getRed(), tone.getGreen(),
						tone.getBlue(), 36));
				g.fillRoundRect(x, y, w, h, 8, 8);
				g.setColor(new Color(tone.getRed(), tone.getGreen(),
						tone.getBlue(), 135));
				g.drawRoundRect(x, y, w, h, 8, 8);
				g.setFont(font(Font.BOLD, 8));
				g.setColor(tone);
				drawClipped(g, title.toUpperCase(Locale.US), x + 9, y + 12, w - 18);
				g.setFont(font(Font.BOLD, 16));
				g.setColor(TEXT);
				drawClipped(g, rank, x + 9, y + 31, Math.max(36, w / 2));
				g.setFont(font(Font.BOLD, 10));
				g.setColor(MUTED);
				drawClipped(g, score, x + Math.max(44, w / 2), y + 31,
						Math.max(34, w / 2 - 10));
			}

			private int[] sortedProbeOrder()
			{
				int count = candidate == null || candidate.counterfactuals == null
						? 0 : candidate.counterfactuals.size();
				int[] order = new int[count];
				for (int i = 0; i < count; i++)
				{
					order[i] = i;
				}
				for (int i = 0; i < order.length; i++)
				{
					for (int j = i + 1; j < order.length; j++)
					{
						TemporalGraphFoundationModelSupport.CounterfactualProbe left =
								candidate.counterfactuals.get(order[i]);
						TemporalGraphFoundationModelSupport.CounterfactualProbe right =
								candidate.counterfactuals.get(order[j]);
						double leftImpact = left.scoreDrop + left.rankShift * 0.025;
						double rightImpact = right.scoreDrop + right.rankShift * 0.025;
						if (rightImpact > leftImpact)
						{
							int tmp = order[i];
							order[i] = order[j];
							order[j] = tmp;
						}
					}
				}
				return order;
			}

			private Color signalColor(String signal)
			{
				String value = signal == null ? "" : signal.toLowerCase(Locale.US);
				if (value.indexOf("graph") >= 0)
				{
					return GREEN;
				}
				if (value.indexOf("time") >= 0
						|| value.indexOf("temporal") >= 0)
				{
					return TEAL;
				}
				if (value.indexOf("bridge") >= 0)
				{
					return AMBER;
				}
				if (value.indexOf("debias") >= 0)
				{
					return RED;
				}
				if (value.indexOf("robust") >= 0)
				{
					return PURPLE;
				}
				return BLUE;
			}

			private void centerString(Graphics2D g, String value, int cx, int y)
			{
				FontMetrics metrics = g.getFontMetrics();
				String text = safe(value);
				g.drawString(text, cx - metrics.stringWidth(text) / 2, y);
			}

			/** Draws up to two lines, wrapping at a word break instead of clipping. */
			private void drawNoteLines(Graphics2D g, String value, int x, int y, int w)
			{
				String text = safe(value);
				FontMetrics metrics = g.getFontMetrics();
				if (metrics.stringWidth(text) <= w)
				{
					g.drawString(text, x, y + 4);
					return;
				}
				int split = -1;
				for (int i = text.length() - 1; i > 0; i--)
				{
					if (text.charAt(i) == ' '
							&& metrics.stringWidth(text.substring(0, i)) <= w)
					{
						split = i;
						break;
					}
				}
				if (split <= 0)
				{
					drawClipped(g, text, x, y + 4, w);
					return;
				}
				g.drawString(text.substring(0, split), x, y);
				drawClipped(g, text.substring(split + 1), x, y + 13, w);
			}

			private void drawRankShiftRow(
					Graphics2D g,
					int x,
					int y,
					int w,
					int h,
					TemporalGraphFoundationModelSupport.CandidateScore score)
			{
				int activeRank = activeCounterfactualRank(score);
				double activeScore = activeCounterfactualScore(score);
				boolean selected = score == candidate;
				Color tone = activeRank < score.rank ? GREEN
						: activeRank > score.rank ? RED : BLUE;
				g.setColor(new Color(tone.getRed(), tone.getGreen(),
						tone.getBlue(), selected ? 74 : 34));
				g.fillRoundRect(x, y, w, h, 6, 6);
				g.setColor(selected ? tone : BORDER_STRONG);
				g.drawRoundRect(x, y, w, h, 6, 6);
				g.setFont(font(Font.BOLD, 9));
				g.setColor(TEXT);
				String label = "#" + score.rank + "->#" + activeRank
						+ " " + safe(score.candidateName());
				drawClipped(g, label, x + 7, y + 12, Math.max(50, w / 2));
				int barW = Math.max(34, w / 3);
				int barX = x + w - barW - 7;
				g.setColor(TRACK);
				g.fillRoundRect(barX, y + 5, barW, 6, 6, 6);
				g.setColor(tone);
				g.fillRoundRect(barX, y + 5,
						Math.max(2, (int)Math.round(barW * activeScore)),
						6, 6, 6);
			}

			private void drawBenchmarkBoard(Graphics2D g, int x, int y, int w, int h)
			{
				drawPanel(g, x, y, w, h);
				g.setFont(font(Font.BOLD, 10));
				g.setColor(TEAL);
				drawClipped(g, "READINESS + BENCHMARK", x + 12, y + 17, w - 24);
				TemporalGraphFoundationModelSupport.ResearchSummary summary =
						result.getResearchSummary();
				TemporalGraphFoundationModelSupport.FoundationReadiness readiness =
						summary.foundationReadiness;
				int ring = Math.min(44, Math.max(34, h / 4));
				int ringX = x + 14;
				int ringY = y + 28;
				drawReadinessRing(g, ringX + ring / 2, ringY + ring / 2,
						ring / 2, readiness.overall);
				g.setFont(font(Font.BOLD, 10));
				g.setColor(TEXT);
				drawNoteLines(g, readiness.label, x + ring + 20, y + 36,
						w - ring - 32);
				g.setFont(font(Font.PLAIN, 10));
				g.setColor(MUTED);
				drawNoteLines(g, readiness.note, x + ring + 20, y + 62,
						w - ring - 32);
				int barY = y + 30 + ring + 12;
				int barW = Math.max(90, w - 26);
				drawReadinessBar(g, x + 13, barY, barW, "temporal",
						readiness.temporalDepth, TEAL);
				drawReadinessBar(g, x + 13, barY + 17, barW, "graph",
						readiness.graphCoverage, GREEN);
				drawReadinessBar(g, x + 13, barY + 34, barW, "evidence",
						readiness.evidenceCoverage, BLUE);
				List<TemporalGraphFoundationModelSupport.BenchmarkRow> rows =
						report == null || report.benchmarkRows.isEmpty()
								? summary.benchmarkRows
								: report.benchmarkRows;
				int rowY = barY + 56;
				int rowH = Math.max(18, Math.min(22, (y + h - rowY - 8) / 3));
				for (int i = 0; i < rows.size() && i < 5
						&& rowY + rowH < y + h - 8; i++)
				{
					drawBenchmarkRow(g, x + 12, rowY, w - 24, rowH, rows.get(i));
					rowY += rowH + 5;
				}
			}

			private void drawReadinessRing(
					Graphics2D g,
					int cx,
					int cy,
					int radius,
					double value)
			{
				g.setStroke(new BasicStroke(6.0f, BasicStroke.CAP_ROUND,
						BasicStroke.JOIN_ROUND));
				g.setColor(alphaOf(TRACK, 220));
				g.drawOval(cx - radius, cy - radius, radius * 2, radius * 2);
				Color tone = value >= 0.75 ? GREEN : value >= 0.55 ? AMBER : RED;
				g.setColor(tone);
				g.drawArc(cx - radius, cy - radius, radius * 2, radius * 2,
						90, -(int)Math.round(value * 360.0));
				g.setStroke(new BasicStroke(1.0f));
				g.setFont(font(Font.BOLD, 11));
				g.setColor(TEXT);
				centerString(g, percent(value), cx, cy + 4);
			}

			private void drawReadinessBar(
					Graphics2D g,
					int x,
					int y,
					int w,
					String label,
					double value,
					Color tone)
			{
				g.setFont(font(Font.BOLD, 8));
				g.setColor(tone);
				drawClipped(g, label.toUpperCase(Locale.US), x, y + 9, 58);
				int barX = x + 62;
				int barW = Math.max(32, w - 106);
				g.setColor(TRACK);
				g.fillRoundRect(barX, y + 3, barW, 7, 7, 7);
				g.setColor(tone);
				g.fillRoundRect(barX, y + 3,
						Math.max(2, (int)Math.round(barW
								* Math.max(0.0, Math.min(1.0, value)))),
						7, 7, 7);
				g.setColor(MUTED);
				drawClipped(g, percent(value), barX + barW + 7, y + 9, 38);
			}

			private void drawBenchmarkRow(
					Graphics2D g,
					int x,
					int y,
					int w,
					int h,
					TemporalGraphFoundationModelSupport.BenchmarkRow row)
			{
				Color tone = "TGF".equals(row.label) ? GREEN
						: row.hitAtOne >= 1.0 ? BLUE : AMBER;
				g.setColor(new Color(tone.getRed(), tone.getGreen(), tone.getBlue(), 36));
				g.fillRoundRect(x, y, w, h, 6, 6);
				g.setColor(BORDER_STRONG);
				g.drawRoundRect(x, y, w, h, 6, 6);
				if (h >= 22)
				{
					int barW = Math.max(40, w / 3);
					g.setColor(TRACK);
					g.fillRoundRect(x + w - barW - 7, y + h - 7,
							barW, 4, 4, 4);
					g.setColor(tone);
					g.fillRoundRect(x + w - barW - 7, y + h - 7,
							Math.max(2, (int)Math.round(barW * row.ndcgAtFive)),
							4, 4, 4);
				}
				g.setFont(font(Font.BOLD, 9));
				g.setColor(TEXT);
				drawClipped(g,
						row.label + " | H@1 " + percent(row.hitAtOne)
								+ " MRR " + String.format(Locale.US, "%.2f",
										Double.valueOf(row.mrr)),
						x + 7,
						y + 13,
						Math.max(50, w - 44));
				g.setColor(MUTED);
				drawClipped(g,
						row.topCandidate.length() == 0 ? "-"
								: "#" + (row.relevantRank <= 0 ? "n/a"
										: Integer.toString(row.relevantRank)),
						x + w - 32,
						y + 13,
						28);
			}

			private void drawProbe(
					Graphics2D g,
					int x,
					int y,
					int w,
					int h,
					TemporalGraphFoundationModelSupport.CounterfactualProbe probe)
			{
				Color tone = probe.rankChanged ? RED
						: probe.scoreDrop > 0.05 ? AMBER : GREEN;
				g.setColor(new Color(tone.getRed(), tone.getGreen(),
						tone.getBlue(), 38));
				g.fillRoundRect(x, y, w, h, 6, 6);
				g.setColor(new Color(tone.getRed(), tone.getGreen(),
						tone.getBlue(), 130));
				g.drawRoundRect(x, y, w, h, 6, 6);
				int barW = Math.max(36, w / 3);
				int barX = x + w - barW - 7;
				g.setColor(TRACK);
				g.fillRoundRect(barX, y + h / 2 - 4, barW, 8, 8, 8);
				g.setColor(tone);
				g.fillRoundRect(barX, y + h / 2 - 4,
						Math.max(2, (int)Math.round(barW
								* Math.min(1.0, probe.scoreDrop
										/ Math.max(0.001, candidate.finalScore)))),
						8, 8, 8);
				g.setFont(font(Font.BOLD, 9));
				g.setColor(TEXT);
				drawClipped(g, probe.label, x + 8, y + 13, w - barW - 26);
				g.setColor(MUTED);
				String rank = probe.rank <= 0 ? "n/a" : "#" + probe.rank;
				drawClipped(g, rank + " -" + decimal(probe.scoreDrop),
						x + w - barW - 58, y + 13, 52);
			}

			private void drawMetric(
					Graphics2D g,
					int x,
					int y,
					int w,
					int h,
					String label,
					String value,
					Color color,
					double progress)
			{
				drawPanel(g, x, y, w, h);
				g.setColor(color);
				g.fillRoundRect(x + 8, y + h - 8,
						Math.max(2, (int)Math.round((w - 16)
								* Math.max(0.0, Math.min(1.0, progress)))),
						4, 4, 4);
				g.setFont(font(Font.BOLD, 9));
				g.setColor(color);
				drawClipped(g, label.toUpperCase(Locale.US), x + 10, y + 15, w - 20);
				g.setFont(font(Font.BOLD, h < 46 ? 12 : 14));
				g.setColor(TEXT);
				drawClipped(g, value, x + 10, y + h - 14, w - 20);
			}

			private void drawPanel(Graphics2D g, int x, int y, int w, int h)
			{
					g.setColor(alphaOf(PLATE, 235));
					g.fillRoundRect(x, y, w, h, 8, 8);
					g.setColor(alphaOf(BORDER_STRONG, 150));
					g.drawRoundRect(x, y, w, h, 8, 8);
			}

			private void drawEmpty(Graphics2D g, int w, int h, String text)
			{
				NebulaDesign.prepare(g);
				int cx = w / 2;
				g.setColor(alphaOf(TEAL, 26));
				g.fillOval(cx - 26, h / 2 - 46, 52, 52);
				g.setColor(alphaOf(TEAL, 170));
				g.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND,
						BasicStroke.JOIN_ROUND));
				g.drawOval(cx - 15, h / 2 - 35, 30, 30);
				g.setStroke(new BasicStroke(1.0f));
				g.setFont(font(Font.BOLD, 13));
				g.setColor(TEXT);
				FontMetrics metrics = g.getFontMetrics();
				g.drawString(text, (w - metrics.stringWidth(text)) / 2, h / 2 + 24);
			}

			private void drawClipped(
					Graphics2D g,
					String value,
					int x,
					int y,
					int width)
			{
				FontMetrics metrics = g.getFontMetrics();
				g.drawString(ellipsize(value, metrics, Math.max(4, width)), x, y);
			}

			private double displayScore(
					TemporalGraphFoundationModelSupport.TimelineFrame frame)
			{
				double base = frame == null ? candidate.finalScore : frame.score;
				if (!removeGraph && !removeTime && !removeBridge
						&& !removeDebias && !removeTopTerm)
				{
					return base;
				}
				if (candidate.finalScore <= 0.0)
				{
					return activeCounterfactualScore();
				}
				return Math.max(0.0, Math.min(1.0,
						base * (activeCounterfactualScore() / candidate.finalScore)));
			}

			private double activeCounterfactualScore()
			{
				if (candidate == null)
				{
					return 0.0;
				}
				return activeCounterfactualScore(candidate);
			}

			private double activeCounterfactualScore(
					TemporalGraphFoundationModelSupport.CandidateScore score)
			{
				if (score == null)
				{
					return 0.0;
				}
				return score.counterfactualScore(
						removeGraph,
						removeTime,
						removeBridge,
						removeDebias,
						removeTopTerm);
			}

			private int activeCounterfactualRank(
					TemporalGraphFoundationModelSupport.CandidateScore score)
			{
				if (report == null || score == null || report.topCandidates.isEmpty())
				{
					return 0;
				}
				double selected = activeCounterfactualScore(score);
				int rank = 1;
				for (TemporalGraphFoundationModelSupport.CandidateScore other
						: report.topCandidates)
				{
					if (other == score)
					{
						continue;
					}
					double otherScore = activeCounterfactualScore(other);
					if (otherScore > selected
							|| (otherScore == selected
									&& other.candidateName().compareTo(
											score.candidateName()) < 0))
					{
						rank++;
					}
				}
				return rank;
			}

			private String activeCounterfactualLabel()
			{
				ArrayList<String> active = new ArrayList<String>();
				if (removeGraph)
				{
					active.add("graph off");
				}
				if (removeTime)
				{
					active.add("time off");
				}
				if (removeBridge)
				{
					active.add("bridge off");
				}
				if (removeDebias)
				{
					active.add("debias off");
				}
				if (removeTopTerm)
				{
					active.add("top term off");
				}
				return active.isEmpty() ? "observed replay" : join(active, 5);
			}
		}

		private static class RoundedPanel extends JPanel
		{
		private final Color fill;
		private final int radius;

		RoundedPanel(Color fill)
		{
			this(fill, NebulaDesign.RADIUS_CARD);
		}

		RoundedPanel(Color fill, int radius)
		{
			this.fill = fill;
			this.radius = radius;
			setOpaque(false);
		}

			protected void paintComponent(Graphics graphics)
			{
				Graphics2D g = (Graphics2D) graphics.create();
				NebulaDesign.prepare(g);
				int w = getWidth();
				int h = getHeight();
				if (!StudioNightTheme.isNightMode())
				{
					g.setColor(alphaOf(NebulaDesign.DAY_TEXT_PRIMARY, 12));
					g.fillRoundRect(1, 3, w - 2, h - 4, radius + 2, radius + 2);
				}
				g.setColor(fill);
				g.fillRoundRect(0, 0, w - 1, h - 3, radius, radius);
				g.setColor(BORDER);
				g.drawRoundRect(0, 0, w - 1, h - 3, radius, radius);
				g.dispose();
				super.paintComponent(graphics);
		}
	}

	private static final class HeroPanel extends RoundedPanel
	{
		HeroPanel(BorderLayout layout)
		{
			super(SURFACE);
			setLayout(layout);
		}

		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D) graphics.create();
			NebulaDesign.prepare(g);
			int w = getWidth();
			int h = getHeight();
			boolean night = StudioNightTheme.isNightMode();
			if (!night)
			{
				g.setColor(alphaOf(NebulaDesign.DAY_TEXT_PRIMARY, 12));
				g.fillRoundRect(1, 3, w - 2, h - 4,
						NebulaDesign.RADIUS_CARD + 2, NebulaDesign.RADIUS_CARD + 2);
			}
			g.setColor(SURFACE);
			g.fillRoundRect(0, 0, w - 1, h - 3,
					NebulaDesign.RADIUS_CARD, NebulaDesign.RADIUS_CARD);
			java.awt.Shape oldClip = g.getClip();
			g.clip(new java.awt.geom.RoundRectangle2D.Float(
					0, 0, w - 1, h - 3,
					NebulaDesign.RADIUS_CARD, NebulaDesign.RADIUS_CARD));
			paintGlow(g, w * 0.18f, -h * 0.4f, Math.max(w, h) * 0.5f,
					alphaOf(BLUE, night ? 26 : 15));
			paintGlow(g, w * 0.86f, h * 1.1f, Math.max(w, h) * 0.45f,
					alphaOf(PURPLE, night ? 22 : 13));
			g.setColor(alphaOf(TEAL, night ? 46 : 30));
			for (int i = 0; i < 9; i++)
			{
				int x = w - 330 + i * 37;
				int y = 26 + (i % 4) * 20;
				g.fillOval(x, y, 6, 6);
				if (i > 0)
				{
					g.drawLine(x - 37 + 3, y - ((i % 4) - ((i - 1) % 4)) * 20 + 3,
							x + 3, y + 3);
				}
			}
			g.setClip(oldClip);
			g.setColor(BORDER);
			g.drawRoundRect(0, 0, w - 1, h - 3,
					NebulaDesign.RADIUS_CARD, NebulaDesign.RADIUS_CARD);
			g.dispose();
		}

		private static void paintGlow(
				Graphics2D g,
				float centerX,
				float centerY,
				float radius,
				Color color)
		{
			if (radius <= 0.0f)
			{
				return;
			}
			java.awt.RadialGradientPaint paint = new java.awt.RadialGradientPaint(
					new java.awt.geom.Point2D.Float(centerX, centerY),
					radius,
					new float[] {0.0f, 1.0f},
					new Color[] {color, alphaOf(color, 0)},
					java.awt.MultipleGradientPaint.CycleMethod.NO_CYCLE);
			g.setPaint(paint);
			g.fillRect(Math.round(centerX - radius), Math.round(centerY - radius),
					Math.round(radius * 2), Math.round(radius * 2));
			g.setPaint(null);
		}
	}

	private static final class MetricPanel extends RoundedPanel
	{
		private final Color accent;

			MetricPanel(Color accent)
			{
				super(SURFACE, 12);
				this.accent = accent;
			}

		protected void paintComponent(Graphics graphics)
		{
			super.paintComponent(graphics);
			Graphics2D g = (Graphics2D) graphics.create();
			NebulaDesign.prepare(g);
			g.setColor(accent);
			g.fillOval(11, 13, 7, 7);
			g.dispose();
		}
	}

	private static final class NebulaButton extends JButton
	{
		private final boolean primary;
		private final Color hue;

		NebulaButton(String label, boolean primary, Color hue)
		{
			super(label);
			this.primary = primary;
			this.hue = hue;
			setFont(NebulaDesign.fontMedium(12.5f));
			setContentAreaFilled(false);
			setOpaque(false);
			setFocusPainted(false);
			setBorder(new EmptyBorder(8, 16, 8, 16));
			setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			setForeground(primary ? NebulaDesign.TEXT_ON_ACCENT : hue);
		}

		public Color getForeground()
		{
			// Resolved at paint time so theme walks cannot strand the label
			// color on the wrong mode.
			if (primary)
			{
				return NebulaDesign.TEXT_ON_ACCENT;
			}
			return hue == null ? super.getForeground() : hue;
		}

		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D) graphics.create();
			NebulaDesign.prepare(g);
			int w = getWidth();
			int h = getHeight();
			boolean hover = getModel().isRollover();
			boolean press = getModel().isArmed() && getModel().isPressed();
			int radius = NebulaDesign.RADIUS_CONTROL;
			if (primary)
			{
				Color fill = press ? NebulaDesign.ACCENT_PRESS
						: hover ? NebulaDesign.ACCENT_HOVER : NebulaDesign.ACCENT;
				if (hover)
				{
					g.setColor(alphaOf(NebulaDesign.ACCENT, 60));
					g.fillRoundRect(0, 0, w, h, radius + 4, radius + 4);
				}
				g.setColor(fill);
				g.fillRoundRect(1, 1, w - 2, h - 2, radius, radius);
			}
			else
			{
				g.setColor(press || hover
						? alphaOf(hue, StudioNightTheme.isNightMode() ? 46 : 26)
						: SURFACE);
				g.fillRoundRect(1, 1, w - 2, h - 2, radius, radius);
				g.setColor(alphaOf(hue, hover ? 200 : 120));
				g.drawRoundRect(1, 1, w - 3, h - 3, radius, radius);
			}
			g.dispose();
			super.paintComponent(graphics);
		}
	}

	private static final class ToggleIcon implements javax.swing.Icon
	{
		public int getIconWidth()
		{
			return 15;
		}

		public int getIconHeight()
		{
			return 15;
		}

		public void paintIcon(Component component, Graphics graphics, int x, int y)
		{
			Graphics2D g = (Graphics2D) graphics.create();
			NebulaDesign.prepare(g);
			boolean selected = component instanceof javax.swing.AbstractButton
					&& ((javax.swing.AbstractButton) component).isSelected();
			if (selected)
			{
				g.setColor(NebulaDesign.currentAccent());
				g.fillRoundRect(x, y, 14, 14, 5, 5);
				g.setColor(StudioNightTheme.isNightMode()
						? NebulaDesign.NIGHT_TEXT_ON_ACCENT
						: NebulaDesign.DAY_TEXT_ON_ACCENT);
				g.setStroke(new BasicStroke(1.9f, BasicStroke.CAP_ROUND,
						BasicStroke.JOIN_ROUND));
				g.drawLine(x + 3, y + 7, x + 6, y + 10);
				g.drawLine(x + 6, y + 10, x + 11, y + 4);
			}
			else
			{
				g.setColor(SURFACE_ALT);
				g.fillRoundRect(x, y, 14, 14, 5, 5);
				g.setColor(BORDER_STRONG);
				g.drawRoundRect(x, y, 14, 14, 5, 5);
			}
			g.dispose();
		}
	}
}
