package TwitterGatherDataFollowers.userRyersonU;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/**
 * The whole run through its evidence: how much of the algorithm's lists the
 * reference reproduces, target by target; how relevance falls with rank
 * against chance; and the words that carried the run's first
 * recommendations.
 */
final class EvidenceRunPage extends RecommendationEvidenceStudioPanel.Page
{
	private static final long serialVersionUID = 1L;

	private static final int TABLE_LIMIT = 80;

	private final RecommendationEvidenceStudioPanel studio;
	private RecommendationEvidenceEngine.EvidenceRun run;
	private int shownEvents = -1;
	private int request;
	/** Whether an analysis is running (on the event thread's view). */
	private boolean analysing;

	EvidenceRunPage(RecommendationEvidenceStudioPanel studio)
	{
		this.studio = studio;
	}

	void show(final RecommendationEvidenceEngine.EvidenceRun next, String message)
	{
		if (next == null)
		{
			run = null;
			shownEvents = -1;
			removeAll();
			add(StudioShell.waiting(NodeLabUi.Glyph.CHART, "No run to analyse", message), BorderLayout.CENTER);
			revalidate();
			repaint();
			return;
		}
		if (next == run && next.size() == shownEvents)
		{
			return;
		}
		boolean newRun = next != run;
		run = next;
		shownEvents = next.size();
		if (newRun || !analysing)
		{
			removeAll();
			add(StudioShell.waiting(NodeLabUi.Glyph.CHART, "Analysing", "Loading the evidence…"), BorderLayout.CENTER);
			revalidate();
			repaint();
		}
		//One analysis at a time: a live run delivers one target after another, and each analysis
		//reads the run's whole corpus. Changes that arrive meanwhile are analysed once it ends.
		if (!analysing)
		{
			analyse(next);
		}
	}

	private void analyse(final RecommendationEvidenceEngine.EvidenceRun target)
	{
		analysing = true;
		final int ticket = ++request;
		final int size = target.size();
		Thread worker = new Thread(new Runnable()
		{
			public void run()
			{
				RecommendationEvidenceEngine.RunAnalysis result = null;
				try
				{
					result = RecommendationEvidenceEngine.analyzeRun(target);
				}
				finally
				{
					final RecommendationEvidenceEngine.RunAnalysis analysis = result;
					SwingUtilities.invokeLater(new Runnable()
					{
						public void run()
						{
							analysing = false;
							if (run != null && (run != target || run.size() != size))
							{
								analyse(run);
							}
							else if (ticket == request && analysis != null)
							{
								render(target, analysis);
							}
						}
					});
				}
			}
		}, "DSMP-Evidence-Run");
		worker.setDaemon(true);
		worker.start();
	}

	/** Renders an analysis synchronously (tests and snapshots). */
	void renderNow(RecommendationEvidenceEngine.EvidenceRun target, RecommendationEvidenceEngine.RunAnalysis analysis)
	{
		run = target;
		shownEvents = target.size();
		render(target, analysis);
	}

	private void render(RecommendationEvidenceEngine.EvidenceRun target, RecommendationEvidenceEngine.RunAnalysis a)
	{
		removeAll();
		NodeLabUi.Page page = new NodeLabUi.Page(1360);
		String title = !a.textAvailable ? "No text evidence for this run"
				: a.exact ? "The evidence is " + target.algorithmName + "'s own scoring rule"
				: "Shared vocabulary agrees with " + target.algorithmName + "'s lists at rank-biased overlap "
						+ StudioShell.fixed(a.meanRbo, 2);
		String subtitle = !a.textAvailable ? "The run's corpus is not available, so only ground truth and the run graph"
				+ " can be shown." : a.exact ? "Every explanation recomputes the algorithm's own " + a.reference.label
				+ " from the run's corpus; the rank-biased overlap of " + StudioShell.fixed(a.meanRbo, 2)
				+ " confirms the lists are reproduced." : "Rank-biased overlap between each delivered list and the"
				+ " reference ranking (" + a.reference.label + "), averaged over " + a.targets + " targets: how far"
				+ " " + target.algorithmName + "'s choices are explained by shared vocabulary.";
		NodeLabUi.Hero hero = new NodeLabUi.Hero("Run · " + target.algorithmName + " · " + target.datasetName,
				title, subtitle, a.exact ? NebulaDesign.SUCCESS : NebulaDesign.ACCENT);
		hero.chip(new NodeLabUi.Chip(target.live() ? "This session" : "Recorded run",
				target.live() ? NodeLabUi.Tone.INFO : NodeLabUi.Tone.SUCCESS, true));
		hero.chip(new NodeLabUi.Chip(target.candidateType == RecommendationCandidateType.FOLLOWEE ? "Recommends accounts"
				: "Recommends users", NodeLabUi.Tone.ACCENT, false));
		hero.chip(new NodeLabUi.Chip(target.size() + (target.size() == 1 ? " target" : " targets"),
				NodeLabUi.Tone.NEUTRAL, false));
		page.section(hero);
		if (!a.textAvailable || a.targets == 0)
		{
			add(NodeLabUi.scroll(page), BorderLayout.CENTER);
			revalidate();
			repaint();
			return;
		}
		JPanel tiles = NodeLabUi.grid(200, 4, 12);
		tiles.add(new NodeLabUi.Metric("Rank-biased overlap", StudioShell.fixed(a.meanRbo, 3),
				"mean over targets (p = 0.9)", a.meanRbo >= 0.8 ? NebulaDesign.SUCCESS : a.meanRbo >= 0.5
						? NebulaDesign.INFO : NebulaDesign.WARNING));
		tiles.add(new NodeLabUi.Metric("Overlap@k", StudioShell.percent(a.meanOverlap),
				"of each list in the reference's top", NebulaDesign.INFO));
		tiles.add(new NodeLabUi.Metric("Kendall τ", StudioShell.fixed(a.meanKendall, 3),
				"order of the delivered scores", NebulaDesign.PURPLE));
		double relevant = 0.0;
		double delivered = 0.0;
		double chance = 0.0;
		for (double[] rank : a.calibration)
		{
			relevant += rank[0];
			delivered += rank[1];
			chance += rank[2] * rank[1];
		}
		tiles.add(new NodeLabUi.Metric("Relevant", StudioShell.percent(delivered == 0 ? Double.NaN : relevant / delivered),
				"of delivered items · chance " + StudioShell.percent(delivered == 0 ? Double.NaN : chance / delivered),
				NebulaDesign.SUCCESS));
		page.section(tiles);
		JPanel row = NodeLabUi.columns(420, 2, 18);
		row.add(calibration(a));
		row.add(terms(a));
		page.section(row);
		page.section(perTarget(a));
		add(NodeLabUi.scroll(page), BorderLayout.CENTER);
		revalidate();
		repaint();
	}

	private JComponent calibration(RecommendationEvidenceEngine.RunAnalysis a)
	{
		NodeLabUi.Card card = new NodeLabUi.Card("Relevance by rank",
				"The share of recommendations at each rank that were relevant, against a random list (the chance"
						+ " rate of the target's candidates). A good ranker is most often right at the top.");
		StudioCharts.Bars bars = new StudioCharts.Bars();
		ArrayList<StudioCharts.Bar> values = new ArrayList<StudioCharts.Bar>();
		for (int r = 0; r < a.calibration.length; r++)
		{
			double[] rank = a.calibration[r];
			if (rank[1] <= 0)
			{
				continue;
			}
			double share = rank[0] / rank[1];
			values.add(new StudioCharts.Bar("Rank " + (r + 1), share, StudioShell.percent(share) + " vs "
					+ StudioShell.percent(rank[2]), share > rank[2] ? NebulaDesign.SUCCESS : NebulaDesign.WARNING,
					r == 0, null, (int)rank[0] + " of " + (int)rank[1] + " relevant; chance " + StudioShell.percent(rank[2])));
		}
		bars.set(values, 1.0, "relevant share vs chance");
		card.content(bars);
		return card;
	}

	private JComponent terms(RecommendationEvidenceEngine.RunAnalysis a)
	{
		NodeLabUi.Card card = new NodeLabUi.Card("The words that carried the run",
				"Summed over every target's first recommendation: the words whose reference contributions decided"
						+ " the most.");
		StudioCharts.Bars bars = new StudioCharts.Bars();
		ArrayList<StudioCharts.Bar> values = new ArrayList<StudioCharts.Bar>();
		for (RecommendationEvidenceEngine.TermContribution term : a.runTerms)
		{
			values.add(new StudioCharts.Bar(term.term, term.contribution, StudioShell.fixed(term.contribution, 3),
					NebulaDesign.ACCENT, false, null, "'" + term.term + "' is written by " + term.users + " users"));
		}
		bars.set(values, Double.NaN, null);
		card.content(values.isEmpty() ? NodeLabUi.note("No shared words.") : bars);
		return card;
	}

	private JComponent perTarget(final RecommendationEvidenceEngine.RunAnalysis a)
	{
		NodeLabUi.Card card = new NodeLabUi.Card("Target by target",
				"Rank-biased overlap and overlap@k of each delivered list with the reference ranking. Click a row to"
						+ " explain the target's first recommendation.");
		final NodeLabUi.Table table = new NodeLabUi.Table(NodeLabUi.Table.text("Target", 1.6f),
				NodeLabUi.Table.number("Rank-biased overlap", 1f), NodeLabUi.Table.number("Overlap@k", 0.8f));
		List<String[]> rows = new ArrayList<String[]>();
		List<Color[]> colors = new ArrayList<Color[]>();
		for (int i = 0; i < Math.min(TABLE_LIMIT, a.targetNames.size()); i++)
		{
			double[] values = a.perTarget.get(i);
			rows.add(new String[] {a.targetNames.get(i), StudioShell.fixed(values[0], 3), StudioShell.percent(values[1])});
			colors.add(new Color[] {null, values[0] >= 0.8 ? NebulaDesign.SUCCESS : values[0] >= 0.5 ? NebulaDesign.INFO
					: NebulaDesign.WARNING, null});
		}
		table.setRows(rows, colors);
		table.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		table.addMouseListener(new MouseAdapter()
		{
			public void mouseClicked(MouseEvent event)
			{
				int row = table.rowAt(event.getY());
				if (row >= 0 && row < a.targetNames.size())
				{
					studio.show(RecommendationEvidenceStudioPanel.EXPLAIN);
					((EvidenceExplainPage)studio.page(RecommendationEvidenceStudioPanel.EXPLAIN))
							.focus(a.targetNames.get(row), 0);
				}
			}
		});
		card.content(a.targetNames.size() > TABLE_LIMIT ? NodeLabUi.stack(8, table, NodeLabUi.note("The first "
				+ TABLE_LIMIT + " of " + a.targetNames.size() + " targets; the Explain page lists all of them."))
				: table);
		return card;
	}
}
