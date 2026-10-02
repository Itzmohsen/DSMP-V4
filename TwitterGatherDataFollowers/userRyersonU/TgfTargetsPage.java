package TwitterGatherDataFollowers.userRyersonU;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.swing.DefaultListModel;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

/**
 * Every target of the run and the complete explanation of its prediction:
 * the community probabilities and conformal set, the exact log-odds split by
 * learner, the words, users and months behind them, what each learner says
 * on its own, the smallest change of words that flips the prediction, and
 * the users TGF recommends.
 */
final class TgfTargetsPage extends TemporalGraphFoundationPanel.Page
{
	private static final long serialVersionUID = 1L;

	private final TemporalGraphFoundationPanel studio;
	private final DefaultListModel<TgfReport.Target> model = new DefaultListModel<TgfReport.Target>();
	private final JList<TgfReport.Target> list = new JList<TgfReport.Target>(model);
	private final NodeLabUi.Field search = new NodeLabUi.Field("", 16, "Search targets");
	private final JPanel detail = new JPanel(new BorderLayout());
	private final JPanel content = new JPanel(new BorderLayout(18, 0));
	private TgfReport report;
	private boolean updating;

	TgfTargetsPage(TemporalGraphFoundationPanel studio)
	{
		this.studio = studio;
		list.setOpaque(false);
		list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		list.setCellRenderer(new TargetCell());
		list.setFixedCellHeight(58);
		list.addListSelectionListener(new ListSelectionListener()
		{
			public void valueChanged(ListSelectionEvent event)
			{
				if (!event.getValueIsAdjusting() && !updating)
				{
					showTarget(list.getSelectedValue());
				}
			}
		});
		search.getDocument().addDocumentListener(new DocumentListener()
		{
			public void insertUpdate(DocumentEvent event)
			{
				filter();
			}

			public void removeUpdate(DocumentEvent event)
			{
				filter();
			}

			public void changedUpdate(DocumentEvent event)
			{
				filter();
			}
		});
		detail.setOpaque(false);
		content.setOpaque(false);
		content.setBorder(new EmptyBorder(8, 24, 0, 0));
		NodeLabUi.Card side = new NodeLabUi.Card("Targets", null);
		JScrollPane scroll = NodeLabUi.scroll(list);
		scroll.setPreferredSize(new Dimension(290, 400));
		side.content(NodeLabUi.stack(10, search, scroll));
		JPanel left = new JPanel(new BorderLayout());
		left.setOpaque(false);
		left.setPreferredSize(new Dimension(320, 400));
		left.add(side, BorderLayout.CENTER);
		left.setBorder(new EmptyBorder(0, 0, 24, 0));
		content.add(left, BorderLayout.WEST);
		content.add(detail, BorderLayout.CENTER);
	}

	void show(TgfReport next, String message)
	{
		if (next == null)
		{
			report = null;
			removeAll();
			add(StudioShell.waiting(NodeLabUi.Glyph.TARGET, "No targets yet", message), BorderLayout.CENTER);
			revalidate();
			repaint();
			return;
		}
		if (next == report)
		{
			return;
		}
		report = next;
		removeAll();
		add(content, BorderLayout.CENTER);
		filter();
		String focus = studio.focusedTarget();
		if (focus == null || !focus(focus))
		{
			if (!model.isEmpty())
			{
				list.setSelectedIndex(0);
			}
			else
			{
				showTarget(null);
			}
		}
		revalidate();
		repaint();
	}

	/** Selects a target; false when the report has no such target. */
	boolean focus(String user)
	{
		if (report == null)
		{
			return false;
		}
		search.setText("");
		filter();
		for (int i = 0; i < model.size(); i++)
		{
			if (model.get(i).user.equals(user))
			{
				list.setSelectedIndex(i);
				list.ensureIndexIsVisible(i);
				return true;
			}
		}
		return false;
	}

	private void filter()
	{
		if (report == null)
		{
			return;
		}
		TgfReport.Target selected = list.getSelectedValue();
		String query = search.getText().trim().toLowerCase(Locale.US);
		updating = true;
		model.clear();
		for (TgfReport.Target target : report.targets)
		{
			if (query.isEmpty() || target.user.toLowerCase(Locale.US).contains(query)
					|| (target.predicted != null && target.predicted.toLowerCase(Locale.US).contains(query)))
			{
				model.addElement(target);
			}
		}
		updating = false;
		if (selected != null && model.contains(selected))
		{
			list.setSelectedValue(selected, true);
		}
		else if (!model.isEmpty())
		{
			list.setSelectedIndex(0);
		}
	}

	private void showTarget(TgfReport.Target target)
	{
		detail.removeAll();
		if (target == null)
		{
			detail.add(StudioShell.waiting(NodeLabUi.Glyph.TARGET, "No target selected",
					"Pick a target on the left."), BorderLayout.CENTER);
		}
		else
		{
			NodeLabUi.Page page = new NodeLabUi.Page(1080);
			build(page, target);
			detail.add(NodeLabUi.scroll(page), BorderLayout.CENTER);
		}
		detail.revalidate();
		detail.repaint();
	}

	// ============================================================ dossier

	private void build(NodeLabUi.Page page, TgfReport.Target target)
	{
		page.section(header(target));
		if (!target.supervised || target.posterior.length == 0)
		{
			page.section(recommendations(target));
			return;
		}
		JPanel first = NodeLabUi.columns(400, 2, 18);
		first.add(probabilities(target));
		first.add(waterfall(target));
		page.section(first);
		page.section(learners(target));
		page.section(words(target));
		JPanel graphs = StudioShell.column(18);
		graphs.add(neighbours("Through the text graph", "The labelled users whose labels reached " + target.user
				+ " by label propagation over the 10-nearest-neighbour text graph. Influence is exact: the"
				+ " propagated mass of an account is the sum of its users' influence.", target.textNeighbours,
				"linked by words"));
		graphs.add(neighbours("Through the term-time graph", "The same over the graph of users who wrote the same"
				+ " words in the same month: who was talking about the same things at the same time.",
				target.termTimeNeighbours, "shared (word, month)"));
		page.section(graphs);
		if (target.activityAvailable)
		{
			page.section(activity(target));
		}
		page.section(counterfactual(target));
		page.section(recommendations(target));
	}

	private JComponent header(TgfReport.Target target)
	{
		String title = target.supervised && target.predicted != null
				? target.user + " most likely follows " + target.predicted : target.user;
		NodeLabUi.Hero hero = new NodeLabUi.Hero("Target", title, subtitle(target),
				target.correct == null ? NebulaDesign.ACCENT : target.correct.booleanValue() ? NebulaDesign.SUCCESS
						: NebulaDesign.DANGER);
		if (target.supervised && target.predicted != null)
		{
			hero.chip(new NodeLabUi.Chip("p = " + StudioShell.fixed(target.probability, 3), NodeLabUi.Tone.ACCENT,
					false));
			hero.chip(new NodeLabUi.Chip("margin " + StudioShell.fixed(target.margin, 3), NodeLabUi.Tone.NEUTRAL,
					false));
			hero.chip(new NodeLabUi.Chip(target.set.size() + (target.set.size() == 1 ? " account" : " accounts")
					+ " in the " + StudioShell.wholePercent(1 - report.conformalAlpha) + " set",
					target.set.size() == 1 ? NodeLabUi.Tone.SUCCESS : NodeLabUi.Tone.INFO, false));
		}
		if (target.correct != null)
		{
			hero.chip(new NodeLabUi.Chip(target.correct.booleanValue() ? "Correct: follows " + target.truth
					: "Wrong: follows " + target.truth + (Boolean.TRUE.equals(target.inSet) ? " (in the set)" : ""),
					target.correct.booleanValue() ? NodeLabUi.Tone.SUCCESS : Boolean.TRUE.equals(target.inSet)
							? NodeLabUi.Tone.WARNING : NodeLabUi.Tone.DANGER, true));
		}
		hero.chip(new NodeLabUi.Chip(StudioShell.count(target.tweets) + " tweets", NodeLabUi.Tone.NEUTRAL, false));
		if (target.first != null)
		{
			hero.chip(new NodeLabUi.Chip(target.first + " → " + target.last, NodeLabUi.Tone.NEUTRAL, false));
		}
		return hero;
	}

	/** The verdict in one sentence: the runner-up, the strongest evidence and the conformal set. */
	private String subtitle(TgfReport.Target target)
	{
		if (!target.supervised || target.predicted == null)
		{
			return target.headline == null ? "" : target.headline;
		}
		TgfReport.TargetLearner lead = null;
		for (TgfReport.TargetLearner learner : target.learners)
		{
			if (learner.active && (lead == null || learner.contribution > lead.contribution))
			{
				lead = learner;
			}
		}
		StringBuilder text = new StringBuilder("Probability ").append(StudioShell.fixed(target.probability, 3))
				.append(", ahead of ").append(target.runnerUp).append(" by ").append(StudioShell.fixed(target.margin, 3))
				.append('.');
		if (lead != null && lead.contribution > 0.0)
		{
			text.append(" The ").append(lead.name.toLowerCase(Locale.US)).append(" adds the most evidence (")
					.append(StudioShell.signed(lead.contribution, 2)).append(" log-odds).");
		}
		text.append(" The ").append(StudioShell.wholePercent(1 - report.conformalAlpha)).append(" conformal set is {")
				.append(join(target.set)).append("}.");
		return text.toString();
	}

	private JComponent probabilities(TgfReport.Target target)
	{
		NodeLabUi.Card card = new NodeLabUi.Card("Which account " + target.user + " follows",
				"Bold accounts form the " + StudioShell.wholePercent(1 - report.conformalAlpha) + " conformal set:"
						+ " it holds the real account for that share of users. The real follow is tagged.");
		StudioCharts.Bars bars = new StudioCharts.Bars();
		ArrayList<StudioCharts.Bar> values = new ArrayList<StudioCharts.Bar>();
		for (TgfReport.AccountScore account : target.accounts)
		{
			int c = report.communityIndex(account.account);
			values.add(new StudioCharts.Bar(account.account, account.probability,
					StudioShell.percent(account.probability), StudioCharts.community(c), account.inSet,
					account.account.equals(target.truth) ? "real" : null, null));
		}
		bars.set(values, 1.0, null);
		card.content(bars);
		return card;
	}

	private JComponent waterfall(TgfReport.Target target)
	{
		NodeLabUi.Card card = new NodeLabUi.Card("Why " + target.predicted + " over " + target.runnerUp,
				"The log-odds of the two most likely accounts, built up exactly: the prior, then each learner's"
						+ " weighted evidence. The total is ln(p1/p2) = " + StudioShell.fixed(target.logOdds, 2) + ".");
		StudioCharts.Waterfall chart = new StudioCharts.Waterfall();
		ArrayList<StudioCharts.Step> steps = new ArrayList<StudioCharts.Step>();
		steps.add(new StudioCharts.Step("Stack offset", target.prior, "The stack's per-account offset b_c. The"
				+ " learners already carry the accounts' sizes (their own intercepts and class mass), so the offset"
				+ " recalibrates their combined prior and can favour either account."));
		for (TgfReport.TargetLearner learner : target.learners)
		{
			if (!learner.active || learner.weight <= 0.0)
			{
				continue;
			}
			steps.add(new StudioCharts.Step(learner.name, learner.contribution, learner.name + " (weight "
					+ StudioShell.fixed(learner.weight, 2) + ") alone says " + learner.says + " with p = "
					+ StudioShell.fixed(learner.saysProbability, 3)));
		}
		chart.set(steps, "Log-odds", target.predicted, target.runnerUp);
		card.content(chart);
		return card;
	}

	private JComponent learners(TgfReport.Target target)
	{
		NodeLabUi.Card card = new NodeLabUi.Card("Each learner's view",
				"What every learner says on its own, how much the stack trusts it, and what the prediction would be"
						+ " without it (its weight set to zero, the rest unchanged).");
		NodeLabUi.Table table = new NodeLabUi.Table(NodeLabUi.Table.text("Learner", 1.3f),
				NodeLabUi.Table.text("Says alone", 1.3f), NodeLabUi.Table.number("Its p", 0.6f),
				NodeLabUi.Table.number("Weight", 0.6f), NodeLabUi.Table.number("Evidence", 0.7f),
				NodeLabUi.Table.text("Without it", 1.6f));
		List<String[]> rows = new ArrayList<String[]>();
		List<Color[]> colors = new ArrayList<Color[]>();
		for (TgfReport.TargetLearner learner : target.learners)
		{
			TgfReport.Ablation ablation = null;
			for (TgfReport.Ablation candidate : target.ablations)
			{
				if (candidate.key.equals(learner.key))
				{
					ablation = candidate;
				}
			}
			String without = !learner.active ? "off (no data)" : learner.weight <= 0.0 ? "unchanged (no weight)"
					: ablation == null ? "—" : ablation.changes ? "flips to " + ablation.predicted
							: target.predicted + " stays, p = " + StudioShell.fixed(ablation.probability, 3);
			rows.add(new String[] {learner.name, learner.active ? learner.says : "off",
					learner.active ? StudioShell.fixed(learner.saysProbability, 3) : "—",
					StudioShell.fixed(learner.weight, 2), learner.active && learner.weight > 0.0
							? StudioShell.signed(learner.contribution, 2) : "—", without});
			int says = report.communityIndex(learner.says);
			colors.add(new Color[] {null, says >= 0 ? StudioCharts.community(says) : null, null, null,
					learner.contribution > 0 ? NebulaDesign.SUCCESS : learner.contribution < 0 ? NebulaDesign.DANGER
							: null, ablation != null && ablation.changes ? NebulaDesign.WARNING : null});
		}
		table.setRows(rows, colors);
		card.content(table);
		return card;
	}

	private JComponent words(TgfReport.Target target)
	{
		NodeLabUi.Card card = new NodeLabUi.Card("The words behind the text model",
				"Each word adds its TF-IDF weight times the difference of the text model's weights for the two"
						+ " accounts, scaled into the final log-odds. Exact: these terms plus the model's intercept"
						+ " sum to the text model's contribution.");
		JPanel columns = NodeLabUi.columns(360, 2, 24);
		columns.add(termBars(target.supporting, "for " + target.predicted, NebulaDesign.SUCCESS));
		columns.add(termBars(target.opposing, "for " + target.runnerUp, NebulaDesign.DANGER));
		card.content(columns);
		return card;
	}

	private static JComponent termBars(List<TgfReport.TermEvidence> terms, String caption, Color tone)
	{
		if (terms.isEmpty())
		{
			return NodeLabUi.note("No word " + caption + ".");
		}
		StudioCharts.Bars bars = new StudioCharts.Bars();
		ArrayList<StudioCharts.Bar> values = new ArrayList<StudioCharts.Bar>();
		for (TgfReport.TermEvidence term : terms)
		{
			values.add(new StudioCharts.Bar(term.term, Math.abs(term.contribution),
					StudioShell.signed(term.contribution, 3), tone, false, null,
					"'" + term.term + "': written " + term.count + (term.count == 1 ? " time" : " times")
							+ ", used by " + term.users + " users, TF-IDF " + StudioShell.fixed(term.weight, 3)));
		}
		bars.set(values, Double.NaN, "words " + caption);
		return bars;
	}

	private JComponent neighbours(String title, String subtitle, List<TgfReport.Neighbour> neighbours, String link)
	{
		NodeLabUi.Card card = new NodeLabUi.Card(title, subtitle);
		NodeLabUi.Table table = new NodeLabUi.Table(NodeLabUi.Table.text("User", 1.2f),
				NodeLabUi.Table.text("Follows", 1.2f), NodeLabUi.Table.number("Influence", 0.6f),
				NodeLabUi.Table.text(link, 3.2f));
		List<String[]> rows = new ArrayList<String[]>();
		List<Color[]> colors = new ArrayList<Color[]>();
		for (TgfReport.Neighbour neighbour : neighbours)
		{
			rows.add(new String[] {neighbour.user + (neighbour.direct ? "" : " ·"), neighbour.community,
					StudioShell.percent(neighbour.share), join(neighbour.shared)});
			int c = report.communityIndex(neighbour.community);
			colors.add(new Color[] {null, c >= 0 ? StudioCharts.community(c) : null, neighbour.supports
					? NebulaDesign.SUCCESS : null, null});
		}
		table.setRows(rows, colors);
		table.setEmptyText("This graph learner is off for this run.");
		card.content(NodeLabUi.stack(8, table, NodeLabUi.note("Influence is the user's share of all the label mass"
				+ " that reached the target; · marks users whose labels arrived through others rather than"
				+ " through a direct edge.")));
		return card;
	}

	private JComponent activity(TgfReport.Target target)
	{
		NodeLabUi.Card card = new NodeLabUi.Card("When " + target.user + " tweets",
				"The target's tweets per month against the two most likely communities (each line is a share of its"
						+ " own tweets). Collection periods and the rhythm of events differ between communities,"
						+ " which the activity learners and the term-time graph use.");
		StudioCharts.Lines lines = new StudioCharts.Lines();
		ArrayList<StudioCharts.Series> series = new ArrayList<StudioCharts.Series>();
		series.add(new StudioCharts.Series(target.user, shares(target.monthly), NebulaDesign.TEXT_PRIMARY, true));
		for (String name : new String[] {target.predicted, target.runnerUp})
		{
			int c = report.communityIndex(name);
			if (c >= 0)
			{
				series.add(new StudioCharts.Series(name, report.communities.get(c).monthly, StudioCharts.community(c),
						false));
			}
		}
		String[] labels = new String[report.data.months];
		for (int m = 0; m < labels.length; m++)
		{
			labels[m] = report.data.month(m);
		}
		lines.set(series, labels, "%", 240);
		JPanel body = StudioShell.column(10);
		body.add(lines);
		if (!target.drivers.isEmpty())
		{
			StringBuilder text = new StringBuilder("The activity model (weight ")
					.append(StudioShell.fixed(target.activityWeight, 2)).append(") is moved most by: ");
			for (int i = 0; i < target.drivers.size(); i++)
			{
				TgfReport.Driver driver = target.drivers.get(i);
				text.append(i == 0 ? "" : "; ").append(driver.label).append(" (")
						.append(StudioShell.wholePercent(driver.share)).append(" of tweets, ")
						.append(StudioShell.signed(driver.contribution, 2)).append(")");
			}
			body.add(NodeLabUi.prose(text.append('.').toString()));
		}
		else
		{
			body.add(NodeLabUi.caption("The stack gives the activity model no weight in this run."));
		}
		card.content(body);
		return card;
	}

	private JComponent counterfactual(TgfReport.Target target)
	{
		NodeLabUi.Card card = new NodeLabUi.Card("What would change the prediction",
				"The fewest of the target's words whose removal from the text model's input flips the predicted"
						+ " account, every other learner's evidence held fixed.");
		card.accent(target.counterfactualFound ? NebulaDesign.WARNING : NebulaDesign.SUCCESS);
		JPanel body = StudioShell.column(10);
		if (target.counterfactualFound)
		{
			JPanel chips = new JPanel(new NodeLabUi.WrapLayout(6, 6));
			chips.setOpaque(false);
			for (String term : target.counterfactualTerms)
			{
				chips.add(new NodeLabUi.Chip(term, NodeLabUi.Tone.WARNING, false));
			}
			body.add(chips);
			body.add(NodeLabUi.prose(target.counterfactualNote + " (p = "
					+ StudioShell.fixed(target.counterfactualProbability, 3) + ")."));
		}
		else
		{
			body.add(NodeLabUi.prose(target.counterfactualNote == null ? "No counterfactual was computed."
					: target.counterfactualNote));
		}
		card.content(body);
		return card;
	}

	private JComponent recommendations(TgfReport.Target target)
	{
		NodeLabUi.Card card = new NodeLabUi.Card("Users TGF recommends",
				target.supervised ? "Ranked by the probability of sharing " + target.user + "'s community, ties broken"
						+ " by text similarity. Relevant marks users who really share it." : "Ranked by text similarity.");
		NodeLabUi.Table table = new NodeLabUi.Table(NodeLabUi.Table.number("#", 0.3f),
				NodeLabUi.Table.text("User", 1.2f), NodeLabUi.Table.text("Follows", 1.1f),
				NodeLabUi.Table.number("P(same)", 0.7f), NodeLabUi.Table.number("Cosine", 0.6f),
				NodeLabUi.Table.text("Shared words", 1.6f), NodeLabUi.Table.text("Relevant", 0.7f));
		List<String[]> rows = new ArrayList<String[]>();
		List<Color[]> colors = new ArrayList<Color[]>();
		int rank = 1;
		for (TgfReport.Recommendation recommendation : target.recommendations)
		{
			rows.add(new String[] {Integer.toString(rank++), recommendation.user,
					recommendation.community == null ? "hidden (target)" : recommendation.community,
					StudioShell.fixed(recommendation.probability, 3), StudioShell.fixed(recommendation.similarity, 3),
					join(recommendation.shared), recommendation.relevant == null ? "—"
							: recommendation.relevant.booleanValue() ? "yes" : "no"});
			int c = report.communityIndex(recommendation.community);
			colors.add(new Color[] {null, null, c >= 0 ? StudioCharts.community(c) : null, null, null, null,
					recommendation.relevant == null ? null : recommendation.relevant.booleanValue()
							? NebulaDesign.SUCCESS : NebulaDesign.DANGER});
		}
		table.setRows(rows, colors);
		card.content(table);
		return card;
	}

	private static double[] shares(double[] counts)
	{
		double total = 0.0;
		for (double value : counts)
		{
			total += value;
		}
		double[] result = new double[counts.length];
		for (int i = 0; i < counts.length; i++)
		{
			result[i] = total > 0.0 ? counts[i] / total : 0.0;
		}
		return result;
	}

	private static String join(List<String> values)
	{
		StringBuilder text = new StringBuilder();
		for (String value : values)
		{
			if (text.length() > 0)
			{
				text.append(", ");
			}
			text.append(value);
		}
		return text.length() == 0 ? "—" : text.toString();
	}

	// ============================================================ list cell

	/** A target in the list: name, predicted account (its colour), probability and grade. */
	private final class TargetCell extends JComponent implements ListCellRenderer<TgfReport.Target>
	{
		private static final long serialVersionUID = 1L;
		private TgfReport.Target target;
		private boolean selected;

		public Component getListCellRendererComponent(JList<? extends TgfReport.Target> owner,
				TgfReport.Target value, int index, boolean isSelected, boolean focus)
		{
			target = value;
			selected = isSelected;
			return this;
		}

		public Dimension getPreferredSize()
		{
			return new Dimension(260, 58);
		}

		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D)graphics.create();
			NodeLabUi.prepare(g);
			if (selected)
			{
				g.setColor(NodeLabUi.alpha(NebulaDesign.ACCENT, NodeLabUi.night() ? 60 : 30));
				g.fillRoundRect(2, 2, getWidth() - 4, getHeight() - 4, 12, 12);
			}
			if (target == null)
			{
				g.dispose();
				return;
			}
			int c = report == null ? -1 : report.communityIndex(target.predicted);
			g.setColor(c >= 0 ? StudioCharts.community(c) : NebulaDesign.TEXT_TERTIARY);
			g.fill(new Ellipse2D.Double(12, 15, 10, 10));
			g.setFont(NebulaDesign.fontSemiBold(12.5f));
			g.setColor(NebulaDesign.TEXT_PRIMARY);
			int right = 78;
			g.drawString(StudioCharts.fit(target.user, g.getFontMetrics(), getWidth() - 30 - right), 30, 25);
			g.setFont(NebulaDesign.fontBody(11f));
			g.setColor(NebulaDesign.TEXT_SECONDARY);
			String line = target.predicted == null ? "text similarity"
					: target.predicted + " · p " + StudioShell.fixed(target.probability, 2);
			g.drawString(StudioCharts.fit(line, g.getFontMetrics(), getWidth() - 30 - right), 30, 43);
			if (target.correct != null)
			{
				boolean ok = target.correct.booleanValue();
				Color tone = ok ? NebulaDesign.SUCCESS : Boolean.TRUE.equals(target.inSet) ? NebulaDesign.WARNING
						: NebulaDesign.DANGER;
				String text = ok ? "correct" : "wrong";
				g.setFont(NebulaDesign.fontSemiBold(10f));
				int width = g.getFontMetrics().stringWidth(text) + 14;
				g.setColor(NodeLabUi.alpha(tone, 40));
				g.fillRoundRect(getWidth() - width - 10, 18, width, 20, 10, 10);
				g.setColor(tone);
				g.drawString(text, getWidth() - width - 3, 32);
			}
			g.dispose();
		}
	}
}
