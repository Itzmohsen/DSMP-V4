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
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

/**
 * One recommendation explained: the reference score split exactly by word,
 * how faithful that reference is to the algorithm, what would change the
 * recommendation, why it beat its rival, the tweets behind it, the ground
 * truth, the run graph and TGF's own view.
 */
final class EvidenceExplainPage extends RecommendationEvidenceStudioPanel.Page
{
	private static final long serialVersionUID = 1L;

	private final RecommendationEvidenceStudioPanel studio;
	private final DefaultListModel<String> targetModel = new DefaultListModel<String>();
	private final JList<String> targets = new JList<String>(targetModel);
	private final DefaultListModel<RecommendationEvidenceEngine.MatchEvidence> matchModel =
			new DefaultListModel<RecommendationEvidenceEngine.MatchEvidence>();
	private final JList<RecommendationEvidenceEngine.MatchEvidence> matches =
			new JList<RecommendationEvidenceEngine.MatchEvidence>(matchModel);
	private final NodeLabUi.Field search = new NodeLabUi.Field("", 16, "Search targets");
	private final JPanel detail = new JPanel(new BorderLayout());
	private final JPanel content = new JPanel(new BorderLayout(18, 0));
	private RecommendationEvidenceEngine.EvidenceRun run;
	private int shownEvents = -1;
	private boolean updating;
	private int request;

	EvidenceExplainPage(RecommendationEvidenceStudioPanel studio)
	{
		this.studio = studio;
		targets.setOpaque(false);
		targets.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		targets.setCellRenderer(new TargetCell());
		targets.setFixedCellHeight(52);
		targets.addListSelectionListener(new ListSelectionListener()
		{
			public void valueChanged(ListSelectionEvent event)
			{
				if (!event.getValueIsAdjusting() && !updating)
				{
					showMatches();
				}
			}
		});
		matches.setOpaque(false);
		matches.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		matches.setCellRenderer(new MatchCell());
		matches.setFixedCellHeight(46);
		matches.addListSelectionListener(new ListSelectionListener()
		{
			public void valueChanged(ListSelectionEvent event)
			{
				if (!event.getValueIsAdjusting() && !updating)
				{
					explain();
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
		NodeLabUi.Card targetCard = new NodeLabUi.Card("Targets", null);
		JScrollPane targetScroll = NodeLabUi.scroll(targets);
		targetScroll.setPreferredSize(new Dimension(290, 280));
		targetCard.content(NodeLabUi.stack(10, search, targetScroll));
		NodeLabUi.Card matchCard = new NodeLabUi.Card("Delivered list", null);
		JScrollPane matchScroll = NodeLabUi.scroll(matches);
		matchScroll.setPreferredSize(new Dimension(290, 260));
		matchCard.content(matchScroll);
		JPanel left = new JPanel(new BorderLayout(0, 14));
		left.setOpaque(false);
		left.setPreferredSize(new Dimension(330, 400));
		left.add(targetCard, BorderLayout.CENTER);
		left.add(matchCard, BorderLayout.SOUTH);
		left.setBorder(new EmptyBorder(0, 0, 24, 0));
		content.add(left, BorderLayout.WEST);
		content.add(detail, BorderLayout.CENTER);
	}

	void show(RecommendationEvidenceEngine.EvidenceRun next, String message)
	{
		if (next == null)
		{
			run = null;
			shownEvents = -1;
			removeAll();
			add(StudioShell.waiting(NodeLabUi.Glyph.TARGET, "No recommendation to explain", message),
					BorderLayout.CENTER);
			revalidate();
			repaint();
			return;
		}
		if (next == run && next.size() == shownEvents)
		{
			return;
		}
		boolean sameRun = next == run;
		run = next;
		shownEvents = next.size();
		if (!sameRun)
		{
			removeAll();
			add(content, BorderLayout.CENTER);
		}
		filter(!sameRun);
		if (targets.getSelectedIndex() < 0 && !targetModel.isEmpty())
		{
			targets.setSelectedIndex(0);
		}
		revalidate();
		repaint();
	}

	/** Selects a target and a rank (from the Run page). */
	void focus(String target, int index)
	{
		search.setText("");
		filter();
		int position = targetModel.indexOf(target);
		if (position >= 0)
		{
			targets.setSelectedIndex(position);
			targets.ensureIndexIsVisible(position);
			if (index >= 0 && index < matchModel.size())
			{
				matches.setSelectedIndex(index);
			}
		}
	}

	private void filter()
	{
		filter(false);
	}

	/**
	 * Lists the run's targets that match the search, keeping the selected
	 * target. {@code newRun}: the run changed, so the kept target's delivered
	 * list and explanation are rebuilt from the new run.
	 */
	private void filter(boolean newRun)
	{
		if (run == null)
		{
			return;
		}
		String selected = targets.getSelectedValue();
		String query = search.getText().trim().toLowerCase(Locale.US);
		updating = true;
		targetModel.clear();
		for (String target : run.targets())
		{
			if (query.isEmpty() || target.toLowerCase(Locale.US).contains(query))
			{
				targetModel.addElement(target);
			}
		}
		updating = false;
		if (selected != null && targetModel.contains(selected))
		{
			updating = true;
			targets.setSelectedValue(selected, false);
			updating = false;
			if (newRun)
			{
				showMatches();
			}
		}
		else if (!targetModel.isEmpty())
		{
			targets.setSelectedIndex(0);
		}
		else
		{
			showMatches();
		}
	}

	private void showMatches()
	{
		String target = targets.getSelectedValue();
		updating = true;
		matchModel.clear();
		RecommendationEvidenceEngine.RecommendationEvent event = target == null ? null : run.event(target);
		if (event != null)
		{
			for (RecommendationEvidenceEngine.MatchEvidence match : event.matches)
			{
				matchModel.addElement(match);
			}
		}
		updating = false;
		if (!matchModel.isEmpty())
		{
			matches.setSelectedIndex(0);
		}
		else
		{
			explain();
		}
	}

	private void explain()
	{
		final String target = targets.getSelectedValue();
		final int index = matches.getSelectedIndex();
		detail.removeAll();
		if (target == null || index < 0)
		{
			detail.add(StudioShell.waiting(NodeLabUi.Glyph.TARGET, "Pick a recommendation",
					"Choose a target and one of its recommendations."), BorderLayout.CENTER);
			detail.revalidate();
			detail.repaint();
			return;
		}
		detail.add(StudioShell.waiting(NodeLabUi.Glyph.CLOCK, "Explaining",
				"Loading the evidence…"), BorderLayout.CENTER);
		detail.revalidate();
		detail.repaint();
		final int ticket = ++request;
		final RecommendationEvidenceEngine.EvidenceRun target_run = run;
		studio.context(target_run, new RecommendationEvidenceStudioPanel.ContextListener()
		{
			public void loaded(final RunGraphRecord.Loaded graph, final TgfReport tgf)
			{
				Thread worker = new Thread(new Runnable()
				{
					public void run()
					{
						final RecommendationEvidenceEngine.Explanation explanation =
								RecommendationEvidenceEngine.explain(target_run, target, index, graph, tgf);
						SwingUtilities.invokeLater(new Runnable()
						{
							public void run()
							{
								if (ticket == request)
								{
									render(explanation);
								}
							}
						});
					}
				}, "DSMP-Evidence-Explain");
				worker.setDaemon(true);
				worker.start();
			}
		});
	}

	/** Renders an explanation synchronously (tests and snapshots). */
	void renderNow(RecommendationEvidenceEngine.Explanation explanation)
	{
		render(explanation);
	}

	/** The recommendation selected in the delivered list (tests). */
	RecommendationEvidenceEngine.MatchEvidence selectedMatch()
	{
		return matches.getSelectedValue();
	}

	private void render(RecommendationEvidenceEngine.Explanation x)
	{
		detail.removeAll();
		NodeLabUi.Page page = new NodeLabUi.Page(1080);
		page.section(hero(x));
		if (x.textAvailable && !Double.isNaN(x.referenceScore))
		{
			JPanel first = NodeLabUi.columns(400, 2, 18);
			first.add(words(x));
			first.add(fidelity(x));
			page.section(first);
			page.section(counterfactuals(x));
			if (x.rival != null)
			{
				page.section(contrast(x));
			}
			page.section(tweets(x));
		}
		JPanel context = NodeLabUi.columns(320, 3, 18);
		context.add(truth(x));
		context.add(graph(x));
		if (x.tgf != null)
		{
			context.add(tgf(x));
		}
		page.section(context);
		if (!x.warnings.isEmpty())
		{
			NodeLabUi.Card card = new NodeLabUi.Card("Read with care", null);
			card.accent(NebulaDesign.WARNING);
			JPanel lines = StudioShell.column(8);
			for (String warning : x.warnings)
			{
				lines.add(NodeLabUi.prose(warning));
			}
			card.content(lines);
			page.section(card);
		}
		detail.add(NodeLabUi.scroll(page), BorderLayout.CENTER);
		detail.revalidate();
		detail.repaint();
	}

	private JComponent hero(RecommendationEvidenceEngine.Explanation x)
	{
		Color glow = x.relevant == null ? NebulaDesign.ACCENT : x.relevant.booleanValue() ? NebulaDesign.SUCCESS
				: NebulaDesign.WARNING;
		NodeLabUi.Hero hero = new NodeLabUi.Hero("Recommendation #" + x.rank + " · " + x.algorithmName,
				"Why " + x.candidate + " for " + x.target, x.verdict, glow);
		if (x.textAvailable)
		{
			hero.chip(new NodeLabUi.Chip(x.exact ? "Exact: " + x.reference.label : "Reference: " + x.reference.label,
					x.exact ? NodeLabUi.Tone.SUCCESS : NodeLabUi.Tone.INFO, true));
		}
		if (x.relevant != null)
		{
			hero.chip(new NodeLabUi.Chip(x.relevant.booleanValue() ? "Relevant" : "Not relevant",
					x.relevant.booleanValue() ? NodeLabUi.Tone.SUCCESS : NodeLabUi.Tone.WARNING, true));
		}
		if (x.reportedScore.length() > 0)
		{
			hero.chip(new NodeLabUi.Chip("score " + x.reportedScore, NodeLabUi.Tone.NEUTRAL, false));
		}
		if (x.textAvailable && !Double.isNaN(x.referenceScore))
		{
			hero.chip(new NodeLabUi.Chip("reference rank #" + x.referenceRank + " of "
					+ StudioShell.count(x.candidates), NodeLabUi.Tone.NEUTRAL, false));
		}
		return hero;
	}

	private JComponent words(RecommendationEvidenceEngine.Explanation x)
	{
		String subject = x.explainingUser != null ? x.explainingUser + " (for " + x.candidate + ")" : x.candidate;
		NodeLabUi.Card card = new NodeLabUi.Card("The words behind it",
				"Each word's exact share of " + x.referenceMeaning + " (" + x.target + " and " + subject
						+ "). The shares sum to the whole score.");
		StudioCharts.Bars bars = new StudioCharts.Bars();
		ArrayList<StudioCharts.Bar> values = new ArrayList<StudioCharts.Bar>();
		for (RecommendationEvidenceEngine.TermContribution term : x.terms)
		{
			values.add(new StudioCharts.Bar(term.term, term.share, StudioShell.percent(term.share),
					NebulaDesign.SUCCESS, false, null, "'" + term.term + "': " + term.targetCount + " times by "
							+ x.target + ", " + term.candidateCount + " by " + (x.explainingUser != null
									? x.explainingUser : x.candidate) + "; written by " + term.users + " users"));
		}
		bars.set(values, Double.NaN, null);
		JPanel body = StudioShell.column(10);
		if (values.isEmpty())
		{
			body.add(NodeLabUi.note("No shared words: the reference score is zero."));
		}
		else
		{
			body.add(bars);
			body.add(NodeLabUi.note("The top five words carry " + StudioShell.percent(x.topShare) + " of the score; "
					+ x.sharedTerms + (x.sharedTerms == 1 ? " word is" : " words are") + " shared in all."));
		}
		card.content(body);
		return card;
	}

	private JComponent fidelity(RecommendationEvidenceEngine.Explanation x)
	{
		NodeLabUi.Card card = new NodeLabUi.Card("How faithful is this evidence?",
				x.exact ? "The reference is " + x.algorithmName + "'s own scoring rule, recomputed from the run's"
						+ " corpus: the explanation is the model's."
						: "The reference is shared vocabulary, not " + x.algorithmName + " itself. These measures say"
								+ " how much of " + x.algorithmName + "'s list it reproduces.");
		card.accent(x.exact ? NebulaDesign.SUCCESS : x.rbo >= 0.5 ? NebulaDesign.INFO : NebulaDesign.WARNING);
		JPanel tiles = NodeLabUi.grid(140, 2, 10);
		tiles.add(new NodeLabUi.Metric("Rank-biased overlap", StudioShell.fixed(x.rbo, 2),
				"top-weighted, 1 = same list", x.rbo >= 0.8 ? NebulaDesign.SUCCESS : x.rbo >= 0.5 ? NebulaDesign.INFO
						: NebulaDesign.WARNING));
		tiles.add(new NodeLabUi.Metric("Overlap@" + x.listLength, StudioShell.percent(x.overlap),
				"in the reference's top " + x.listLength, NebulaDesign.INFO));
		tiles.add(new NodeLabUi.Metric("Kendall τ", StudioShell.fixed(x.kendall, 2),
				"order of the delivered scores", NebulaDesign.PURPLE));
		tiles.add(new NodeLabUi.Metric(x.exact ? "Reproduction" : "Reference rank",
				x.exact ? (Double.isNaN(x.reproductionError) ? "—" : String.format(Locale.US, "%.1e",
						x.reproductionError)) : "#" + x.referenceRank,
				x.exact ? "max relative score error" : "of " + StudioShell.count(x.candidates) + " candidates",
				NebulaDesign.ACCENT));
		card.content(NodeLabUi.stack(10, tiles, NodeLabUi.prose(x.fidelityLabel + ".")));
		return card;
	}

	private JComponent counterfactuals(RecommendationEvidenceEngine.Explanation x)
	{
		NodeLabUi.Card card = new NodeLabUi.Card("What would change it",
				"Each decisive word removed from " + x.target + "'s profile (the corpus statistics held fixed), and"
						+ " the fewest words whose removal costs the recommendation its place. Exact for the"
						+ " reference" + (x.exact ? ", and so for " + x.algorithmName + "." : "."));
		NodeLabUi.Table table = new NodeLabUi.Table(NodeLabUi.Table.text("Without the word", 1.2f),
				NodeLabUi.Table.number("Score", 0.7f), NodeLabUi.Table.number("Rank", 0.5f),
				NodeLabUi.Table.text("Its place goes to", 1.4f));
		List<String[]> rows = new ArrayList<String[]>();
		List<Color[]> colors = new ArrayList<Color[]>();
		rows.add(new String[] {"(nothing removed)", StudioShell.fixed(x.referenceScore, 4), "#" + x.referenceRank, ""});
		colors.add(new Color[] {null, null, null, null});
		for (RecommendationEvidenceEngine.Removal removal : x.removals)
		{
			rows.add(new String[] {removal.term, StudioShell.fixed(removal.score, 4), "#" + removal.rank,
					removal.overtakenBy == null ? "keeps it" : removal.overtakenBy});
			colors.add(new Color[] {null, null, removal.rank > x.referenceRank ? NebulaDesign.WARNING : null, null});
		}
		table.setRows(rows, colors);
		JPanel body = StudioShell.column(12);
		body.add(table);
		body.add(minimal("Fewest words to lose its place (#" + x.referenceRank + ")", x.loseRank,
				x.loseRank.isEmpty() ? "No set of its words moves it: it would stay #" + x.referenceRank + "."
						: "Then " + x.loseRankTo + " moves ahead of it."));
		body.add(minimal("Fewest words to leave the top " + x.listLength, x.leaveTop, x.leaveTopFound
				? "Without them it is no longer in the top " + x.listLength + "."
				: "Even the strongest " + Math.min(300, x.sharedTerms) + " words do not push it out of the top "
						+ x.listLength + "."));
		card.content(body);
		return card;
	}

	private static JComponent minimal(String title, List<String> terms, String note)
	{
		JPanel block = StudioShell.column(6);
		block.add(NodeLabUi.strong(title));
		if (!terms.isEmpty())
		{
			JPanel chips = new JPanel(new NodeLabUi.WrapLayout(6, 6));
			chips.setOpaque(false);
			for (int i = 0; i < Math.min(30, terms.size()); i++)
			{
				chips.add(new NodeLabUi.Chip(terms.get(i), NodeLabUi.Tone.WARNING, false));
			}
			if (terms.size() > 30)
			{
				chips.add(new NodeLabUi.Chip("and " + (terms.size() - 30) + " more", NodeLabUi.Tone.NEUTRAL, false));
			}
			block.add(chips);
			block.add(NodeLabUi.caption(terms.size() + (terms.size() == 1 ? " word" : " words")
					+ ", removed in order of their contribution"));
		}
		block.add(NodeLabUi.note(note));
		return block;
	}

	private JComponent contrast(RecommendationEvidenceEngine.Explanation x)
	{
		NodeLabUi.Card card = new NodeLabUi.Card("Why " + x.candidate + " and not " + x.rival,
				"Word by word, the reference score of " + x.candidate + " minus that of " + x.rival + " (delivered #"
						+ x.rivalRank + ").");
		JPanel columns = NodeLabUi.columns(340, 2, 24);
		columns.add(differenceBars(x.favouring, "for " + x.candidate, NebulaDesign.SUCCESS));
		columns.add(differenceBars(x.rivalFavouring, "for " + x.rival, NebulaDesign.DANGER));
		card.content(columns);
		return card;
	}

	private static JComponent differenceBars(List<RecommendationEvidenceEngine.TermContribution> terms,
			String caption, Color tone)
	{
		if (terms.isEmpty())
		{
			return NodeLabUi.note("No word favours it " + caption.substring(4) + ".");
		}
		StudioCharts.Bars bars = new StudioCharts.Bars();
		ArrayList<StudioCharts.Bar> values = new ArrayList<StudioCharts.Bar>();
		for (RecommendationEvidenceEngine.TermContribution term : terms)
		{
			values.add(new StudioCharts.Bar(term.term, term.contribution, StudioShell.fixed(term.contribution, 4), tone,
					false, null, null));
		}
		bars.set(values, Double.NaN, "words " + caption);
		return bars;
	}

	private JComponent tweets(RecommendationEvidenceEngine.Explanation x)
	{
		NodeLabUi.Card card = new NodeLabUi.Card("The tweets behind it",
				"Tweets of both users holding the decisive words (highlighted), the strongest first, as the run read"
						+ " them (normalised).");
		ArrayList<String> decisive = new ArrayList<String>();
		for (RecommendationEvidenceEngine.TermContribution term : x.terms)
		{
			decisive.add(term.term);
		}
		JPanel columns = NodeLabUi.columns(360, 2, 24);
		columns.add(quoteColumn(x.target, x.targetQuotes, decisive, NebulaDesign.ACCENT));
		String other = x.explainingUser != null ? x.explainingUser : x.candidate;
		columns.add(quoteColumn(other, x.candidateQuotes, decisive, NebulaDesign.SUCCESS));
		JPanel body = StudioShell.column(14);
		body.add(columns);
		if (!x.sharedMoments.isEmpty())
		{
			body.add(NodeLabUi.strong("When both wrote them"));
			JPanel chips = new JPanel(new NodeLabUi.WrapLayout(6, 6));
			chips.setOpaque(false);
			for (String moment : x.sharedMoments)
			{
				chips.add(new NodeLabUi.Chip(moment, NodeLabUi.Tone.INFO, false));
			}
			body.add(chips);
		}
		if (!Double.isNaN(x.activityOverlap))
		{
			body.add(NodeLabUi.note("Monthly activity overlap " + StudioShell.fixed(x.activityOverlap, 2)
					+ " (cosine of tweets per month); active together in " + x.commonMonths
					+ (x.commonMonths == 1 ? " month." : " months.")));
		}
		card.content(body);
		return card;
	}

	private static JComponent quoteColumn(String author, List<RecommendationEvidenceEngine.Quote> quotes,
			List<String> decisive, Color tone)
	{
		JPanel column = StudioShell.column(10);
		column.add(NodeLabUi.strong(author));
		if (quotes.isEmpty())
		{
			column.add(NodeLabUi.note("No tweet holds the decisive words."));
		}
		for (RecommendationEvidenceEngine.Quote quote : quotes)
		{
			JPanel block = StudioShell.column(2);
			block.add(NodeLabUi.caption(quote.date));
			block.add(StudioCharts.quote(quote.text, decisive, tone));
			column.add(block);
		}
		return column;
	}

	private JComponent truth(RecommendationEvidenceEngine.Explanation x)
	{
		NodeLabUi.Card card = new NodeLabUi.Card("Ground truth", "Never shown to the algorithm; it grades the result.");
		card.accent(x.relevant == null ? NebulaDesign.TEXT_TERTIARY : x.relevant.booleanValue() ? NebulaDesign.SUCCESS
				: NebulaDesign.WARNING);
		JPanel lines = StudioShell.column(6);
		lines.add(NodeLabUi.prose(x.target + " follows " + (x.targetCommunity == null ? "an unknown account"
				: x.targetCommunity) + "."));
		if (x.type == RecommendationCandidateType.USER)
		{
			lines.add(NodeLabUi.prose(x.candidate + " follows " + (x.candidateCommunity == null ? "an unknown account"
					: x.candidateCommunity) + "."));
		}
		lines.add(NodeLabUi.strong(x.relevant == null ? "Relevance unknown" : x.relevant.booleanValue()
				? "Relevant: the same community" : "Not relevant: another community"));
		card.content(lines);
		return card;
	}

	private JComponent graph(RecommendationEvidenceEngine.Explanation x)
	{
		NodeLabUi.Card card = new NodeLabUi.Card("In the run graph", x.graphAvailable ? null
				: "The run graph is written with the run's record, when the run ends.");
		JPanel lines = StudioShell.column(6);
		if (x.graphAvailable)
		{
			lines.add(NodeLabUi.strong(x.graphRelation));
			lines.add(NodeLabUi.prose(x.graphExposure + "; " + x.graphPageRank + "."));
			lines.add(NodeLabUi.prose(x.graphAdoption));
		}
		card.content(lines);
		return card;
	}

	private JComponent tgf(RecommendationEvidenceEngine.Explanation x)
	{
		RecommendationEvidenceEngine.TgfView view = x.tgf;
		NodeLabUi.Card card = new NodeLabUi.Card("TGF's own view", "The model's exact explanation (TGF Studio has all"
				+ " of it).");
		card.accent(NebulaDesign.PURPLE);
		JPanel lines = StudioShell.column(6);
		lines.add(NodeLabUi.prose(x.target + " most likely follows " + view.predicted + " (p = "
				+ StudioShell.fixed(view.probability, 3) + "); conformal set {" + join(view.set) + "}."));
		if (!Double.isNaN(view.candidateProbability))
		{
			lines.add(NodeLabUi.prose(x.type == RecommendationCandidateType.USER
					? "P(" + x.candidate + " shares the community) = " + StudioShell.fixed(view.candidateProbability, 3)
							+ "."
					: "P(follows " + x.candidate + ") = " + StudioShell.fixed(view.candidateProbability, 3) + "."));
		}
		StringBuilder learners = new StringBuilder("Evidence by learner (log-odds): ");
		for (int i = 0; i < view.learners.size(); i++)
		{
			String[] learner = view.learners.get(i);
			learners.append(i == 0 ? "" : ", ").append(learner[0]).append(' ').append(learner[1]);
		}
		lines.add(NodeLabUi.note(learners.toString()));
		card.content(lines);
		return card;
	}

	private static String join(List<String> values)
	{
		StringBuilder text = new StringBuilder();
		for (String value : values)
		{
			text.append(text.length() == 0 ? "" : ", ").append(value);
		}
		return text.toString();
	}

	// ============================================================ list cells

	private final class TargetCell extends JComponent implements ListCellRenderer<String>
	{
		private static final long serialVersionUID = 1L;
		private String target;
		private boolean selected;

		public Component getListCellRendererComponent(JList<? extends String> owner, String value, int index,
				boolean isSelected, boolean focus)
		{
			target = value;
			selected = isSelected;
			return this;
		}

		public Dimension getPreferredSize()
		{
			return new Dimension(260, 52);
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
			RecommendationEvidenceEngine.RecommendationEvent event = run == null || target == null ? null
					: run.event(target);
			g.setFont(NebulaDesign.fontSemiBold(12.5f));
			g.setColor(NebulaDesign.TEXT_PRIMARY);
			g.drawString(StudioCharts.fit(target, g.getFontMetrics(), getWidth() - 24), 12, 22);
			g.setFont(NebulaDesign.fontBody(11f));
			g.setColor(NebulaDesign.TEXT_SECONDARY);
			String line = event == null || event.matches.isEmpty() ? "no recommendation"
					: "#1 " + event.matches.get(0).name + " · " + event.matches.size() + " delivered";
			g.drawString(StudioCharts.fit(line, g.getFontMetrics(), getWidth() - 24), 12, 40);
			g.dispose();
		}
	}

	private final class MatchCell extends JComponent
			implements ListCellRenderer<RecommendationEvidenceEngine.MatchEvidence>
	{
		private static final long serialVersionUID = 1L;
		private RecommendationEvidenceEngine.MatchEvidence match;
		private boolean selected;

		public Component getListCellRendererComponent(JList<? extends RecommendationEvidenceEngine.MatchEvidence> owner,
				RecommendationEvidenceEngine.MatchEvidence value, int index, boolean isSelected, boolean focus)
		{
			match = value;
			selected = isSelected;
			return this;
		}

		public Dimension getPreferredSize()
		{
			return new Dimension(260, 46);
		}

		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D)graphics.create();
			NodeLabUi.prepare(g);
			if (selected)
			{
				g.setColor(NodeLabUi.alpha(NebulaDesign.SUCCESS, NodeLabUi.night() ? 60 : 30));
				g.fillRoundRect(2, 2, getWidth() - 4, getHeight() - 4, 12, 12);
			}
			if (match == null)
			{
				g.dispose();
				return;
			}
			g.setColor(NodeLabUi.alpha(NebulaDesign.ACCENT, 50));
			g.fill(new Ellipse2D.Double(10, 11, 24, 24));
			g.setFont(NebulaDesign.fontSemiBold(11f));
			g.setColor(NebulaDesign.ACCENT);
			String rank = Integer.toString(match.rank);
			g.drawString(rank, 22 - g.getFontMetrics().stringWidth(rank) / 2, 27);
			g.setFont(NebulaDesign.fontMedium(12f));
			g.setColor(NebulaDesign.TEXT_PRIMARY);
			int scoreWidth = 70;
			g.drawString(StudioCharts.fit(match.name, g.getFontMetrics(), getWidth() - 46 - scoreWidth), 42, 28);
			g.setFont(NebulaDesign.fontMono(11f));
			g.setColor(NebulaDesign.TEXT_SECONDARY);
			String score = match.score.length() > 9 ? match.score.substring(0, 9) : match.score;
			g.drawString(score, getWidth() - g.getFontMetrics().stringWidth(score) - 10, 28);
			g.dispose();
		}
	}
}
