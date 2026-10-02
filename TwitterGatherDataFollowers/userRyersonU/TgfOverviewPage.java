package TwitterGatherDataFollowers.userRyersonU;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.swing.JPanel;

/**
 * TGF at a glance: how well it identifies a user's community (cross-validated
 * on the labelled users and graded on the run's targets) against the obvious
 * baselines, what drives its predictions, and every target of the run.
 */
final class TgfOverviewPage extends TemporalGraphFoundationPanel.Page
{
	private static final long serialVersionUID = 1L;

	private static final int TABLE_LIMIT = 80;

	private final TemporalGraphFoundationPanel studio;
	private TgfReport shownReport;

	TgfOverviewPage(TemporalGraphFoundationPanel studio)
	{
		this.studio = studio;
	}

	void show(TgfReport report, String message)
	{
		if (report == null)
		{
			shownReport = null;
			removeAll();
			add(StudioShell.waiting(NodeLabUi.Glyph.SPARK, "No TGF run yet", message), BorderLayout.CENTER);
			revalidate();
			repaint();
			return;
		}
		if (report == shownReport)
		{
			return;
		}
		shownReport = report;
		removeAll();
		NodeLabUi.Page page = new NodeLabUi.Page(1360);
		build(page, report);
		add(NodeLabUi.scroll(page), BorderLayout.CENTER);
		revalidate();
		repaint();
	}

	private void build(NodeLabUi.Page page, TgfReport report)
	{
		TgfReport.Validation v = report.validation;
		String dataset = report.dataset == null ? "" : " · " + report.dataset;
		String title;
		String subtitle;
		if (!report.supervised)
		{
			title = "TGF could not learn communities in this run";
			subtitle = report.note;
		}
		else
		{
			double lift = v.accuracy - v.majorityAccuracy;
			title = "Finds the followed account of " + StudioShell.wholePercent(v.accuracy) + " of held-out users";
			subtitle = (lift > 0.005 ? Math.round(100 * lift) + " points above always guessing the largest community ("
					+ StudioShell.wholePercent(v.majorityAccuracy) + "). " : "No better than always guessing the largest"
							+ " community (" + StudioShell.wholePercent(v.majorityAccuracy) + "). ")
					+ "A Super Learner over " + activeLearners(report) + " text, graph and time learners, stacked by a"
					+ " calibrated non-negative opinion pool and checked by " + report.folds + "-fold cross-fitting on"
					+ " the " + StudioShell.count(v.users) + " labelled users who are not targets. Every target gets a "
					+ Math.round(100 * (1 - report.conformalAlpha)) + "% conformal set and an exact explanation.";
		}
		NodeLabUi.Hero hero = new NodeLabUi.Hero("Temporal Graph Foundation" + dataset, title, subtitle,
				NebulaDesign.ACCENT);
		hero.chip(new NodeLabUi.Chip(report.live() ? "Live run" : "Recorded run",
				report.live() ? NodeLabUi.Tone.INFO : NodeLabUi.Tone.SUCCESS, true));
		hero.chip(new NodeLabUi.Chip(report.communities.size() + " accounts", NodeLabUi.Tone.ACCENT, false));
		hero.chip(new NodeLabUi.Chip(StudioShell.count(report.data.users) + " users", NodeLabUi.Tone.NEUTRAL, false));
		hero.chip(new NodeLabUi.Chip(report.targets.size() + (report.targets.size() == 1 ? " target" : " targets"),
				NodeLabUi.Tone.NEUTRAL, false));
		if (report.data.months > 0)
		{
			hero.chip(new NodeLabUi.Chip(report.data.firstMonth + " → " + report.data.lastMonth,
					NodeLabUi.Tone.NEUTRAL, false));
		}
		if (report.graded > 0)
		{
			NodeLabUi.Metric score = new NodeLabUi.Metric("This run's targets",
					report.correct + " / " + report.graded,
					"predicted correctly · " + report.covered + " inside their conformal set",
					report.correct * 2 >= report.graded ? NebulaDesign.SUCCESS : NebulaDesign.WARNING);
			hero.right(score);
		}
		page.section(hero);
		if (report.supervised)
		{
			page.section(tiles(report));
			page.section(drivers(report));
		}
		page.section(targets(report));
		JPanel bottom = NodeLabUi.columns(420, 2, 18);
		bottom.add(dataCard(report));
		bottom.add(cautions(report));
		page.section(bottom);
	}

	private JPanel tiles(TgfReport report)
	{
		TgfReport.Validation v = report.validation;
		JPanel tiles = NodeLabUi.grid(190, 6, 12);
		NodeLabUi.Metric accuracy = new NodeLabUi.Metric("Accuracy", StudioShell.percent(v.accuracy),
				"majority " + StudioShell.percent(v.majorityAccuracy),
				v.accuracy > v.majorityAccuracy ? NebulaDesign.SUCCESS : NebulaDesign.WARNING);
		accuracy.setToolTipText("Cross-validated top-1 accuracy " + StudioShell.percent(v.accuracy)
				+ "; always guessing the largest community " + StudioShell.percent(v.majorityAccuracy)
				+ "; guessing by the community shares " + StudioShell.percent(v.chanceAccuracy));
		tiles.add(accuracy);
		tiles.add(new NodeLabUi.Metric("Macro-F1", StudioShell.fixed(v.macroF1, 3),
				"every account weighed equally", NebulaDesign.INFO));
		double prior = priorLogLoss(report);
		tiles.add(new NodeLabUi.Metric("Log-loss", StudioShell.fixed(v.logLoss, 3),
				"prior alone " + StudioShell.fixed(prior, 3),
				v.logLoss < prior ? NebulaDesign.SUCCESS : NebulaDesign.WARNING));
		NodeLabUi.Metric calibration = new NodeLabUi.Metric("Calibration error",
				StudioShell.fixed(v.calibrationError, 3), "ECE · Brier " + StudioShell.fixed(v.brier, 2),
				v.calibrationError < 0.08 ? NebulaDesign.SUCCESS : NebulaDesign.WARNING);
		calibration.setToolTipText("Expected calibration error over 10 confidence bins "
				+ StudioShell.fixed(v.calibrationError, 4) + "; Brier score " + StudioShell.fixed(v.brier, 4));
		tiles.add(calibration);
		tiles.add(new NodeLabUi.Metric("Conformal coverage", StudioShell.percent(v.conformalCoverage),
				"target " + StudioShell.wholePercent(1 - report.conformalAlpha) + " · "
						+ StudioShell.fixed(v.conformalSetSize, 1) + " per set",
				Math.abs(v.conformalCoverage - (1 - report.conformalAlpha)) < 0.05 ? NebulaDesign.SUCCESS
						: NebulaDesign.WARNING));
		tiles.add(new NodeLabUi.Metric("Model fit", NodeLabUi.duration(report.fitMillis),
				report.folds + " folds · " + StudioShell.count(report.data.terms) + " terms",
				NebulaDesign.PURPLE));
		return tiles;
	}

	private JPanel drivers(TgfReport report)
	{
		NodeLabUi.Card card = new NodeLabUi.Card("What drives the predictions",
				"Each learner predicts the community on its own; the stack weighs them by how much they improve the"
						+ " cross-validated likelihood. A weight of zero means the learner added nothing the others"
						+ " did not already say.");
		card.action(NodeLabUi.help("How the stack works",
				"Every learner's class scores are cross-fitted: a labelled user is scored by a learner fitted"
						+ " without it. The stack is a weighted logarithmic opinion pool, log p(c) = b_c + sum_m w_m"
						+ " s_m(c) - log Z, with w_m >= 0, fitted by maximum likelihood (Breiman's stacked"
						+ " regression with non-negative weights). Scores are standardised, so weights are"
						+ " comparable."));
		JPanel columns = NodeLabUi.columns(380, 2, 24);
		StudioCharts.Bars weights = new StudioCharts.Bars();
		ArrayList<StudioCharts.Bar> bars = new ArrayList<StudioCharts.Bar>();
		double maximum = 0.0;
		for (TgfReport.Learner learner : report.learners)
		{
			maximum = Math.max(maximum, learner.weight);
		}
		for (int i = 0; i < report.learners.size(); i++)
		{
			TgfReport.Learner learner = report.learners.get(i);
			bars.add(new StudioCharts.Bar(learner.name, learner.weight, learner.active
					? StudioShell.fixed(learner.weight, 2) : "off", learnerColor(i), learner.weight >= 0.5 * maximum,
					null, learner.description));
		}
		weights.set(bars, Double.NaN, "stack weight (standardised scores)");
		StudioCharts.Bars accuracy = new StudioCharts.Bars();
		ArrayList<StudioCharts.Bar> alone = new ArrayList<StudioCharts.Bar>();
		for (int i = 0; i < report.learners.size(); i++)
		{
			TgfReport.Learner learner = report.learners.get(i);
			alone.add(new StudioCharts.Bar(learner.name, learner.active ? learner.accuracy : 0.0,
					learner.active ? StudioShell.percent(learner.accuracy) : "off", learnerColor(i), false, null,
					learner.name + " alone: cross-validated accuracy " + StudioShell.percent(learner.accuracy)
							+ ", log-loss " + StudioShell.fixed(learner.logLoss, 3)));
		}
		alone.add(new StudioCharts.Bar("Stacked TGF", report.validation.accuracy,
				StudioShell.percent(report.validation.accuracy), NebulaDesign.SUCCESS, true, null, null));
		accuracy.set(alone, 1.0, "accuracy of each learner alone, and of the stack");
		columns.add(weights);
		columns.add(accuracy);
		card.content(NodeLabUi.stack(14, columns, NodeLabUi.prose(driversSentence(report))));
		return card;
	}

	private JPanel targets(TgfReport report)
	{
		NodeLabUi.Card card = new NodeLabUi.Card("Targets",
				report.supervised ? "The predicted account of every target, its probability and conformal set; the"
						+ " real follow (never shown to the model) grades it. Click a row to open its explanation."
						: "Targets ranked without a community model. Click a row to open its details.");
		final NodeLabUi.Table table = new NodeLabUi.Table(
				NodeLabUi.Table.text("Target", 1.4f), NodeLabUi.Table.text("Predicted account", 1.4f),
				NodeLabUi.Table.number("Probability", 0.8f), NodeLabUi.Table.number("Margin", 0.7f),
				NodeLabUi.Table.number("Set", 0.5f), NodeLabUi.Table.text("Real follow", 1.3f),
				NodeLabUi.Table.text("Graded", 0.9f));
		List<String[]> rows = new ArrayList<String[]>();
		List<Color[]> colors = new ArrayList<Color[]>();
		final List<String> users = new ArrayList<String>();
		int shown = 0;
		for (TgfReport.Target target : report.targets)
		{
			if (++shown > TABLE_LIMIT)
			{
				break;
			}
			users.add(target.user);
			String graded = target.correct == null ? "—" : target.correct.booleanValue() ? "Correct"
					: Boolean.TRUE.equals(target.inSet) ? "Wrong, in set" : "Wrong";
			rows.add(new String[] {target.user, target.predicted == null ? "—" : target.predicted,
					StudioShell.fixed(target.probability, 3), StudioShell.fixed(target.margin, 3),
					target.set.isEmpty() ? "—" : Integer.toString(target.set.size()),
					target.truth == null ? "unknown" : target.truth, graded});
			Color tone = target.correct == null ? null : target.correct.booleanValue() ? NebulaDesign.SUCCESS
					: Boolean.TRUE.equals(target.inSet) ? NebulaDesign.WARNING : NebulaDesign.DANGER;
			int c = report.communityIndex(target.predicted);
			colors.add(new Color[] {null, c >= 0 ? StudioCharts.community(c) : null, null, null, null, null, tone});
		}
		table.setRows(rows, colors);
		table.setEmptyText("No target was scored.");
		if (report.targets.size() > TABLE_LIMIT)
		{
			card.content(NodeLabUi.stack(8, table, NodeLabUi.note("The first " + TABLE_LIMIT + " of "
					+ report.targets.size() + " targets; the Targets page lists and explains all of them.")));
			attach(table, users);
			return card;
		}
		attach(table, users);
		card.content(table);
		return card;
	}

	private void attach(final NodeLabUi.Table table, final List<String> users)
	{
		table.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		table.addMouseListener(new MouseAdapter()
		{
			public void mouseClicked(MouseEvent event)
			{
				int row = table.rowAt(event.getY());
				if (row >= 0 && row < users.size())
				{
					studio.inspect(users.get(row));
				}
			}
		});
	}

	private JPanel dataCard(TgfReport report)
	{
		NodeLabUi.Card card = new NodeLabUi.Card("What TGF learned from",
				report.data.fromCorpus ? "The run's canonical corpus: every tweet the run used, normalised exactly as"
						+ " the recommender nodes normalise it." : "The run's TF-IDF vectors (no corpus was available).");
		NodeLabUi.Table table = new NodeLabUi.Table(NodeLabUi.Table.text("Quantity", 1.6f),
				NodeLabUi.Table.number("Value", 1f));
		List<String[]> rows = new ArrayList<String[]>();
		rows.add(new String[] {"Users with text", StudioShell.count(report.data.users)});
		rows.add(new String[] {"Labelled users (not targets)", StudioShell.count(report.data.labelledUsers)});
		rows.add(new String[] {"Targets", StudioShell.count(report.data.targets)});
		rows.add(new String[] {"Accounts (communities)", StudioShell.count(report.communities.size())});
		rows.add(new String[] {"Terms used by two or more users", StudioShell.count(report.data.terms)});
		rows.add(new String[] {"(term, month) pairs shared", StudioShell.count(report.data.termMonthPairs)});
		rows.add(new String[] {"Tweets read", StudioShell.count(report.data.rowsScanned)});
		rows.add(new String[] {"Dated tweets", StudioShell.count(report.data.datedRows)});
		rows.add(new String[] {"Months spanned", report.data.months == 0 ? "undated"
				: report.data.months + " (" + report.data.firstMonth + " to " + report.data.lastMonth + ")"});
		rows.add(new String[] {"Graph edges: text / term-time / activity", StudioShell.count(report.textEdges) + " / "
				+ StudioShell.count(report.termTimeEdges) + " / " + StudioShell.count(report.activityEdges)});
		table.setRows(rows, null);
		card.content(table);
		return card;
	}

	private JPanel cautions(TgfReport report)
	{
		NodeLabUi.Card card = new NodeLabUi.Card("Read with care", null);
		card.accent(NebulaDesign.WARNING);
		JPanel lines = StudioShell.column(8);
		ArrayList<String> notes = new ArrayList<String>(report.warnings);
		if (!report.supervised)
		{
			notes.add(report.note);
		}
		TgfReport.Validation v = report.validation;
		if (report.supervised && v.users < 50)
		{
			notes.add("Only " + v.users + " labelled users: the cross-validated metrics have wide confidence intervals.");
		}
		if (report.supervised && v.macroF1 < 0.5 * v.accuracy)
		{
			notes.add("Macro-F1 is far below accuracy: small communities are rarely predicted. Accuracy rewards the"
					+ " largest community, which is what the run's precision@k rewards too.");
		}
		if (report.data.months == 0)
		{
			notes.add("The corpus is undated, so the term-time graph and both activity learners are off.");
		}
		if (!report.textConverged && report.supervised)
		{
			notes.add("The text model stopped at its iteration budget (" + report.textIterations
					+ " L-BFGS iterations) before converging; it is still a valid, slightly more regularised learner.");
		}
		notes.add("Cross-validated metrics describe the labelled users; the run's targets are graded by its"
				+ " evaluation (Simulator › Results), which also compares TGF with chance and MostPop.");
		for (String note : notes)
		{
			lines.add(NodeLabUi.prose(note));
		}
		card.content(lines);
		return card;
	}

	private static String driversSentence(TgfReport report)
	{
		TgfReport.Learner best = null;
		for (TgfReport.Learner learner : report.learners)
		{
			if (learner.active && (best == null || learner.weight > best.weight))
			{
				best = learner;
			}
		}
		if (best == null)
		{
			return "No learner was active.";
		}
		StringBuilder text = new StringBuilder("The stack leans most on the ").append(best.name.toLowerCase(Locale.US))
				.append(" (weight ").append(StudioShell.fixed(best.weight, 2)).append("). ");
		TgfReport.Learner termTime = report.learner("term-time-graph");
		if (termTime != null && termTime.active && termTime.weight > 0.0)
		{
			text.append("The term-time graph links users who wrote the same words in the same month: a temporal"
					+ " signal plain text similarity cannot see. ");
		}
		int off = 0;
		for (TgfReport.Learner learner : report.learners)
		{
			off += learner.active && learner.weight <= 0.0 ? 1 : 0;
		}
		if (off > 0)
		{
			text.append(off).append(off == 1 ? " learner gets" : " learners get")
					.append(" no weight: their evidence is already carried by the others.");
		}
		return text.toString();
	}

	static Color learnerColor(int index)
	{
		Color[] palette = {NebulaDesign.ACCENT, NebulaDesign.INFO, NebulaDesign.PURPLE, NebulaDesign.PINK,
				NebulaDesign.SUCCESS, NebulaDesign.WARNING};
		return palette[Math.max(0, index) % palette.length];
	}

	static int activeLearners(TgfReport report)
	{
		int count = 0;
		for (TgfReport.Learner learner : report.learners)
		{
			count += learner.active ? 1 : 0;
		}
		return count;
	}

	/** Log-loss of predicting the labelled class shares for everyone. */
	static double priorLogLoss(TgfReport report)
	{
		double total = 0.0;
		for (TgfReport.Community community : report.communities)
		{
			if (community.share > 0.0)
			{
				total -= community.share * Math.log(community.share);
			}
		}
		return total;
	}
}
