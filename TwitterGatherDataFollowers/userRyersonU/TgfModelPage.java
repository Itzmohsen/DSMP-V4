package TwitterGatherDataFollowers.userRyersonU;

import java.awt.BorderLayout;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JComponent;
import javax.swing.JPanel;

/**
 * The fitted model: its learners and stack weights, how well calibrated its
 * probabilities are, which communities it confuses, how its conformal sets
 * cover, and how the fit went. Everything here is cross-validated on the
 * labelled users that are not targets.
 */
final class TgfModelPage extends TemporalGraphFoundationPanel.Page
{
	private static final long serialVersionUID = 1L;

	private TgfReport shownReport;

	TgfModelPage(TemporalGraphFoundationPanel studio)
	{
	}

	void show(TgfReport report, String message)
	{
		if (report == null)
		{
			shownReport = null;
			removeAll();
			add(StudioShell.waiting(NodeLabUi.Glyph.LAYERS, "No model yet", message), BorderLayout.CENTER);
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
		if (!report.supervised)
		{
			NodeLabUi.Card card = new NodeLabUi.Card("No community model", report.note);
			card.content(NodeLabUi.prose("TGF needs at least two followed accounts among at least four labelled users"
					+ " who are not targets to learn which community a user belongs to. This run ranked users by text"
					+ " similarity instead."));
			page.section(card);
		}
		else
		{
			page.section(learners(report));
			JPanel quality = NodeLabUi.columns(420, 2, 18);
			quality.add(calibration(report));
			quality.add(conformal(report));
			page.section(quality);
			page.section(confusion(report));
			page.section(fit(report));
		}
		add(NodeLabUi.scroll(page), BorderLayout.CENTER);
		revalidate();
		repaint();
	}

	private JComponent learners(TgfReport report)
	{
		NodeLabUi.Card card = new NodeLabUi.Card("The learners",
				"Six views of a user, each a complete classifier of the community. Accuracy and log-loss are"
						+ " cross-fitted: every labelled user is scored by a learner that never saw its label.");
		NodeLabUi.Table table = new NodeLabUi.Table(NodeLabUi.Table.text("Learner", 1.1f),
				NodeLabUi.Table.text("What it sees", 3.2f), NodeLabUi.Table.number("Weight", 0.6f),
				NodeLabUi.Table.number("Accuracy", 0.7f), NodeLabUi.Table.number("Log-loss", 0.7f));
		List<String[]> rows = new ArrayList<String[]>();
		List<Color[]> colors = new ArrayList<Color[]>();
		for (int i = 0; i < report.learners.size(); i++)
		{
			TgfReport.Learner learner = report.learners.get(i);
			rows.add(new String[] {learner.name, learner.active ? learner.description : "Off: " + learner.description,
					learner.active ? StudioShell.fixed(learner.weight, 2) : "—",
					learner.active ? StudioShell.percent(learner.accuracy) : "—",
					learner.active ? StudioShell.fixed(learner.logLoss, 3) : "—"});
			colors.add(new Color[] {TgfOverviewPage.learnerColor(i), null, learner.weight > 0.0 ? NebulaDesign.SUCCESS
					: null, null, null});
		}
		rows.add(new String[] {"Stacked TGF", "The non-negative opinion pool of the learners above",
				"", StudioShell.percent(report.validation.accuracy), StudioShell.fixed(report.validation.logLoss, 3)});
		colors.add(new Color[] {NebulaDesign.SUCCESS, null, null, NebulaDesign.SUCCESS, NebulaDesign.SUCCESS});
		table.setRows(rows, colors);
		card.content(table);
		return card;
	}

	private JComponent calibration(TgfReport report)
	{
		TgfReport.Validation v = report.validation;
		NodeLabUi.Card card = new NodeLabUi.Card("Are the probabilities honest?",
				"Users grouped by the probability of the predicted account: a calibrated model is right that often."
						+ " Dot size shows how many users each bin holds.");
		StudioCharts.Reliability chart = new StudioCharts.Reliability();
		chart.set(v.reliability);
		JPanel body = StudioShell.column(10);
		body.add(chart);
		body.add(NodeLabUi.prose("Expected calibration error " + StudioShell.fixed(v.calibrationError, 3)
				+ ", Brier score " + StudioShell.fixed(v.brier, 3) + ", log-loss " + StudioShell.fixed(v.logLoss, 3)
				+ " against " + StudioShell.fixed(TgfOverviewPage.priorLogLoss(report), 3)
				+ " for predicting the account shares. The stack is fitted by maximum likelihood, which is what keeps"
				+ " its probabilities calibrated."));
		card.content(body);
		return card;
	}

	private JComponent conformal(TgfReport report)
	{
		TgfReport.Validation v = report.validation;
		double level = 1 - report.conformalAlpha;
		NodeLabUi.Card card = new NodeLabUi.Card("Conformal prediction sets",
				"Each target gets the set of accounts whose probability is within the conformal threshold, so the"
						+ " set holds its real account with probability at least " + StudioShell.wholePercent(level)
						+ " whenever targets are exchangeable with the labelled users.");
		JPanel tiles = NodeLabUi.grid(150, 3, 10);
		tiles.add(new NodeLabUi.Metric("Coverage", StudioShell.percent(v.conformalCoverage),
				"target " + StudioShell.wholePercent(level),
				Math.abs(v.conformalCoverage - level) < 0.05 ? NebulaDesign.SUCCESS : NebulaDesign.WARNING));
		tiles.add(new NodeLabUi.Metric("Mean set size", StudioShell.fixed(v.conformalSetSize, 2),
				"of " + report.communities.size() + " accounts", NebulaDesign.INFO));
		tiles.add(new NodeLabUi.Metric("Threshold", StudioShell.fixed(report.conformalThreshold, 3),
				"on 1 − p(account)", NebulaDesign.PURPLE));
		JPanel body = StudioShell.column(10);
		body.add(tiles);
		body.add(NodeLabUi.prose("Split-conformal least ambiguous sets (Sadinle, Lei and Wasserman 2019) calibrated on"
				+ " the cross-fitted probabilities of the labelled users. Coverage here is cross-conformal: each fold's"
				+ " sets use the threshold of the other folds, so it is measured on users the threshold never saw."
				+ (report.graded > 0 ? " On this run's graded targets the sets held the real account "
						+ report.covered + " times out of " + report.graded + "." : "")));
		card.content(body);
		return card;
	}

	private JComponent confusion(TgfReport report)
	{
		TgfReport.Validation v = report.validation;
		String[] names = report.communityNames();
		NodeLabUi.Card card = new NodeLabUi.Card("Which communities are confused",
				"Cross-validated predictions of the labelled users: rows are real accounts, columns the predicted"
						+ " ones, shaded by the share of the row. The table gives each account's precision, recall and"
						+ " F1.");
		GraphCharts.Heatmap heatmap = new GraphCharts.Heatmap();
		int k = names.length;
		double[][] intensity = new double[k][k];
		String[][] text = new String[k][k];
		Color[] swatches = new Color[k];
		for (int a = 0; a < k; a++)
		{
			swatches[a] = StudioCharts.community(a);
			int total = 0;
			for (int b = 0; b < k && a < v.confusion.length; b++)
			{
				total += b < v.confusion[a].length ? v.confusion[a][b] : 0;
			}
			for (int b = 0; b < k; b++)
			{
				int value = a < v.confusion.length && b < v.confusion[a].length ? v.confusion[a][b] : 0;
				intensity[a][b] = total == 0 ? 0.0 : value / (double)total;
				text[a][b] = value == 0 ? "" : Integer.toString(value);
			}
		}
		heatmap.set(names, names, intensity, text, swatches, swatches, true, "real ↓ predicted →");
		NodeLabUi.Table table = new NodeLabUi.Table(NodeLabUi.Table.text("Account", 1.4f),
				NodeLabUi.Table.number("Users", 0.6f), NodeLabUi.Table.number("Precision", 0.7f),
				NodeLabUi.Table.number("Recall", 0.7f), NodeLabUi.Table.number("F1", 0.6f));
		List<String[]> rows = new ArrayList<String[]>();
		List<Color[]> colors = new ArrayList<Color[]>();
		for (int c = 0; c < report.communities.size(); c++)
		{
			TgfReport.Community community = report.communities.get(c);
			rows.add(new String[] {community.name, Integer.toString(community.support),
					StudioShell.percent(community.precision), StudioShell.percent(community.recall),
					StudioShell.fixed(community.f1, 3)});
			colors.add(new Color[] {StudioCharts.community(c), null, null, null, null});
		}
		table.setRows(rows, colors);
		JPanel columns = NodeLabUi.columns(420, 2, 24);
		columns.add(heatmap);
		columns.add(table);
		card.content(NodeLabUi.stack(10, columns, NodeLabUi.prose("Macro-F1 " + StudioShell.fixed(v.macroF1, 3)
				+ " weighs every account equally; accuracy " + StudioShell.percent(v.accuracy)
				+ " weighs every user equally.")));
		return card;
	}

	private JComponent fit(TgfReport report)
	{
		NodeLabUi.Card card = new NodeLabUi.Card("How the model was fitted", null);
		NodeLabUi.Table table = new NodeLabUi.Table(NodeLabUi.Table.text("Step", 1.6f),
				NodeLabUi.Table.text("Value", 2f));
		List<String[]> rows = new ArrayList<String[]>();
		rows.add(new String[] {"Cross-fitting folds", report.folds + " (stratified by account)"});
		rows.add(new String[] {"Text model", report.textIterations + " L-BFGS iterations"
				+ (report.textConverged ? ", converged" : ", stopped at the budget")});
		rows.add(new String[] {"Graphs", report.graphNeighbours + " nearest neighbours per user, label propagation"
				+ " alpha " + StudioShell.fixed(report.graphAlpha, 2) + ", solved exactly by conjugate gradients"});
		rows.add(new String[] {"Conformal level", StudioShell.wholePercent(1 - report.conformalAlpha)});
		rows.add(new String[] {"Fit time", NodeLabUi.duration(report.fitMillis)
				+ " (the whole model, once per run, shared by every node)"});
		table.setRows(rows, null);
		card.content(table);
		return card;
	}
}
