package TwitterGatherDataFollowers.userRyersonU;

import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.Locale;

import javax.swing.JComponent;
import javax.swing.JPanel;

/**
 * What characterises each community (the users who follow one account): when
 * its users are active, month by month and hour by hour, and the words the
 * text model learned to associate with it.
 */
final class TgfCommunitiesPage extends TemporalGraphFoundationPanel.Page
{
	private static final long serialVersionUID = 1L;

	private TgfReport shownReport;

	TgfCommunitiesPage(TemporalGraphFoundationPanel studio)
	{
	}

	void show(TgfReport report, String message)
	{
		if (report == null)
		{
			shownReport = null;
			removeAll();
			add(StudioShell.waiting(NodeLabUi.Glyph.CLUSTER, "No communities yet", message), BorderLayout.CENTER);
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
		if (report.data.months > 0)
		{
			page.section(fingerprints(report));
		}
		NodeLabUi.Card words = new NodeLabUi.Card("The words of each community",
				"The terms whose text-model weight for an account most exceeds their average weight over all"
						+ " accounts: what the model reads as the community's vocabulary.");
		JPanel grid = NodeLabUi.grid(300, 3, 14);
		for (int c = 0; c < report.communities.size(); c++)
		{
			grid.add(community(report, c));
		}
		words.content(grid);
		page.section(words);
		add(NodeLabUi.scroll(page), BorderLayout.CENTER);
		revalidate();
		repaint();
	}

	private JComponent fingerprints(TgfReport report)
	{
		NodeLabUi.Card card = new NodeLabUi.Card("Temporal fingerprints",
				"Each community's tweets per month and per hour of the day (UTC), as shares of its own tweets."
						+ " Communities collected over different periods, or active at different hours, are"
						+ " separable in time alone: this is the signal of the activity learners, and of the"
						+ " term-time graph when it meets the vocabulary.");
		StudioCharts.Lines months = new StudioCharts.Lines();
		StudioCharts.Lines hours = new StudioCharts.Lines();
		ArrayList<StudioCharts.Series> monthly = new ArrayList<StudioCharts.Series>();
		ArrayList<StudioCharts.Series> hourly = new ArrayList<StudioCharts.Series>();
		for (int c = 0; c < report.communities.size(); c++)
		{
			TgfReport.Community community = report.communities.get(c);
			monthly.add(new StudioCharts.Series(community.name, community.monthly, StudioCharts.community(c), false));
			hourly.add(new StudioCharts.Series(community.name, community.hourly, StudioCharts.community(c), false));
		}
		String[] monthLabels = new String[report.data.months];
		for (int m = 0; m < monthLabels.length; m++)
		{
			monthLabels[m] = report.data.month(m);
		}
		String[] hourLabels = new String[24];
		for (int h = 0; h < 24; h++)
		{
			hourLabels[h] = String.format(Locale.US, "%02d", Integer.valueOf(h));
		}
		months.set(monthly, monthLabels, "%", 280);
		hours.set(hourly, hourLabels, "%", 240);
		JPanel body = StudioShell.column(16);
		body.add(NodeLabUi.strong("Tweets per month"));
		body.add(months);
		body.add(NodeLabUi.strong("Tweets per hour of the day (UTC)"));
		body.add(hours);
		card.content(body);
		return card;
	}

	private JComponent community(TgfReport report, int index)
	{
		TgfReport.Community community = report.communities.get(index);
		NodeLabUi.Card card = new NodeLabUi.Card(community.name, community.labelled + " labelled users · "
				+ StudioShell.percent(community.share) + " of them"
				+ (report.supervised ? " · recall " + StudioShell.percent(community.recall) + " · F1 "
						+ StudioShell.fixed(community.f1, 2) : ""));
		card.accent(StudioCharts.community(index));
		if (community.terms.isEmpty())
		{
			card.content(NodeLabUi.note("No distinctive terms (no text model)."));
			return card;
		}
		StudioCharts.Bars bars = new StudioCharts.Bars();
		ArrayList<StudioCharts.Bar> values = new ArrayList<StudioCharts.Bar>();
		for (TgfReport.Term term : community.terms)
		{
			values.add(new StudioCharts.Bar(term.term, term.weight, StudioShell.fixed(term.weight, 2),
					StudioCharts.community(index), false, null, "'" + term.term + "' weighs "
							+ StudioShell.fixed(term.weight, 3) + " more for " + community.name
							+ " than on average"));
		}
		bars.set(values, Double.NaN, null);
		card.content(bars);
		return card;
	}
}
