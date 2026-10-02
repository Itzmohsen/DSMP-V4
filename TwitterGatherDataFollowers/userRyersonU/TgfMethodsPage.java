package TwitterGatherDataFollowers.userRyersonU;

import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JComponent;
import javax.swing.JPanel;

/**
 * The Temporal Graph Foundation model, stated precisely: the task, the
 * representations, the learners, the stack, the rankings, the conformal sets,
 * what makes each explanation exact, determinism, the files of a run record
 * and the references.
 */
final class TgfMethodsPage extends TemporalGraphFoundationPanel.Page
{
	private static final long serialVersionUID = 1L;

	private TgfReport shownReport;
	private boolean built;

	TgfMethodsPage(TemporalGraphFoundationPanel studio)
	{
	}

	void show(TgfReport report, String message)
	{
		if (built && report == shownReport)
		{
			return;
		}
		built = true;
		shownReport = report;
		removeAll();
		NodeLabUi.Page page = new NodeLabUi.Page(1180);
		page.section(new NodeLabUi.Hero("Methods", "How TGF predicts, ranks and explains",
				"Temporal Graph Foundation estimates which account a user follows from what they write, when they"
						+ " write it and whom they resemble, as a cross-fitted Super Learner whose stacked"
						+ " probabilities are calibrated, set-valued with a coverage guarantee and explained"
						+ " exactly.", NebulaDesign.PURPLE));
		page.section(section("The task",
				"A run's users each follow one account; the users of an account form its community. For a target"
						+ " user, whose follow is hidden, TGF estimates p(c), the probability that it belongs to"
						+ " community c. Accounts are recommended by p(c). A user v is recommended by the probability"
						+ " of sharing the target's community: p(account of v) when v's follow is known, and"
						+ " sum over c of p(c) q_v(c) when v is another target (q_v is v's own probability). The"
						+ " target's follow is never read; it only grades the result. Only users who are not targets"
						+ " are labelled."));
		page.section(section("Representations",
				"Built once per run from the run's canonical corpus (the tweets the run used, normalised as the"
						+ " recommender nodes normalise them).\n"
						+ "Text: sublinear TF-IDF, (1 + ln tf) ln(N / df), L2-normalised, over the terms used by at"
						+ " least two users (at most 40,000, the most widely used first).\n"
						+ "Activity: each user's share of tweets per UTC calendar month over the corpus span, per hour"
						+ " of the day and per weekday.\n"
						+ "Term at time: the same TF-IDF over (term, month) pairs used by at least two users (at most"
						+ " 200,000). Two users share such a feature when they wrote the same word in the same month:"
						+ " the edges of the user-(term, month) bipartite graph, whose projection is the temporal"
						+ " co-activity graph."));
		page.section(learners());
		page.section(section("Cross-fitting and the stack",
				"The labelled users are split into 5 stratified folds (3 above 2,000 users). Each learner scores the"
						+ " users of a fold after fitting on the other folds, so every labelled user has scores from"
						+ " learners that never saw its label. Each learner's centred scores are standardised by"
						+ " their root mean square. The stack is a weighted logarithmic opinion pool,\n"
						+ "    log p(c) = b_c + sum_m w_m s_m(c) - log Z,   w_m >= 0,\n"
						+ "fitted by penalised maximum likelihood (projected Newton with Armijo backtracking;"
						+ " ridge 0.01 on w, 0.1 on b). Non-negative weights follow Breiman's stacked regressions: a"
						+ " learner can only add evidence, and one the others make redundant gets weight zero. This"
						+ " is a Super Learner (van der Laan, Polley and Hubbard 2007): asymptotically as good as the"
						+ " best combination of its learners. Final learners are then refitted on every label."));
		page.section(section("Validation",
				"Validation metrics use cross-fitted stack probabilities: the pool is refitted without each fold and"
						+ " applied to it. Accuracy, macro-F1, log-loss, the Brier score and the expected calibration"
						+ " error over 10 confidence bins are reported against two baselines: always predicting the"
						+ " largest community, and the log-loss of predicting the community shares. The run's own"
						+ " targets are graded separately by the run's evaluation (precision@k against chance and"
						+ " MostPop, with confidence intervals)."));
		page.section(section("Conformal prediction sets",
				"Nonconformity is 1 - p(real account) on the cross-fitted probabilities of the labelled users. With"
						+ " n of them, the threshold q is the ceil((n + 1)(1 - alpha))-th smallest score, alpha 0.1. A"
						+ " target's set holds every account c with 1 - p(c) <= q (and always the most likely one):"
						+ " the least ambiguous set-valued classifier of Sadinle, Lei and Wasserman (2019). If targets"
						+ " are exchangeable with the labelled users, the set holds the real account with probability"
						+ " at least 90%. Reported coverage is cross-conformal: each fold uses the threshold of the"
						+ " others."));
		page.section(section("Exact explanations",
				"Learners: the log-odds of the predicted account against the runner-up equal the prior difference"
						+ " plus each learner's w_m (s_m(c1) - s_m(c2)), exactly.\n"
						+ "Words: within the text model, a term t adds x_t (W[c1][t] - W[c2][t]); with the"
						+ " intercept they sum to the model's logit difference.\n"
						+ "Users: label propagation is linear in the labels, F = (1 - alpha)(I - alpha S)^-1 Y, so the"
						+ " mass of account c at the target is the sum over users v labelled c of M[target][v]: each"
						+ " labelled user's influence is exact. The row of M is solved by conjugate gradients.\n"
						+ "Ablations: each learner's weight set to zero, the rest of the stack unchanged.\n"
						+ "Counterfactual: the fewest of the target's most supportive words whose removal (the vector"
						+ " renormalised, the other learners' evidence held fixed) changes the prediction, searched"
						+ " greedily over up to 25 words."));
		page.section(section("Determinism and exactness",
				"Folds use a fixed seed, every loop has a fixed order and ties break by index or name; nothing is"
						+ " random or multi-threaded. The model is fitted once per run from the whole corpus, by one"
						+ " node, and shared through the run's collectives; every target is then scored and"
						+ " explained from that shared state. The recommendations are therefore identical for every"
						+ " node count, placement, policy and live control."));
		if (report != null)
		{
			page.section(parameters(report));
		}
		page.section(section("Files in the run record",
				"tgf/report.json: this studio's content, the model card and every target's explanation"
						+ " (dsmp-tgf/1).\n"
						+ "tgf/predictions.csv: per target, the real and predicted account, its probability, the"
						+ " runner-up and margin, the conformal set, and whether the prediction and the set were"
						+ " right."));
		page.section(references());
		add(NodeLabUi.scroll(page), BorderLayout.CENTER);
		revalidate();
		repaint();
	}

	private static JComponent section(String title, String text)
	{
		NodeLabUi.Card card = new NodeLabUi.Card(title, null);
		card.content(NodeLabUi.prose(text));
		return card;
	}

	private static JComponent learners()
	{
		NodeLabUi.Card card = new NodeLabUi.Card("The six learners",
				"Each is a complete classifier of the community from one view of the user.");
		NodeLabUi.Table table = new NodeLabUi.Table(NodeLabUi.Table.text("Learner", 1f),
				NodeLabUi.Table.text("Definition", 3.4f));
		List<String[]> rows = new ArrayList<String[]>();
		for (int m = 0; m < TgfModel.CHANNELS; m++)
		{
			rows.add(new String[] {TgfModel.CHANNEL_NAMES[m], TgfModel.CHANNEL_DESCRIPTIONS[m]});
		}
		table.setRows(rows, null);
		JPanel body = StudioShell.column(10);
		body.add(table);
		body.add(NodeLabUi.prose("Label propagation is learning with local and global consistency (Zhou et al. 2004):"
				+ " F = (1 - alpha)(I - alpha S)^-1 Y with S = D^-1/2 W D^-1/2 on the symmetrised cosine"
				+ " 10-nearest-neighbour graph; the logistic models use L-BFGS on the mean log-loss with an L2"
				+ " penalty (the intercepts are not penalised); the votes weigh the 10 most similar labelled users"
				+ " by cosine, Laplace-smoothed."));
		card.content(body);
		return card;
	}

	private static JComponent parameters(TgfReport report)
	{
		NodeLabUi.Card card = new NodeLabUi.Card("This run's parameters", null);
		NodeLabUi.Table table = new NodeLabUi.Table(NodeLabUi.Table.text("Parameter", 1.5f),
				NodeLabUi.Table.text("Value", 2f));
		List<String[]> rows = new ArrayList<String[]>();
		rows.add(new String[] {"Graph neighbours", Integer.toString(report.graphNeighbours)});
		rows.add(new String[] {"Propagation alpha", StudioShell.fixed(report.graphAlpha, 2)});
		rows.add(new String[] {"Text model penalty", "1e-4 (L2)"});
		rows.add(new String[] {"Activity model penalty", "1e-3 (L2)"});
		rows.add(new String[] {"Cross-fitting folds", Integer.toString(report.folds)});
		rows.add(new String[] {"Conformal level", StudioShell.wholePercent(1 - report.conformalAlpha)});
		rows.add(new String[] {"Conformal threshold", StudioShell.fixed(report.conformalThreshold, 4)});
		table.setRows(rows, null);
		card.content(table);
		return card;
	}

	private static JComponent references()
	{
		NodeLabUi.Card card = new NodeLabUi.Card("References", null);
		JPanel list = StudioShell.column(6);
		String[] references = {
			"Zhou, D., Bousquet, O., Lal, T. N., Weston, J. and Schölkopf, B. (2004). Learning with local and"
					+ " global consistency. NeurIPS 16.",
			"van der Laan, M. J., Polley, E. C. and Hubbard, A. E. (2007). Super learner. Statistical Applications in"
					+ " Genetics and Molecular Biology 6(1).",
			"Breiman, L. (1996). Stacked regressions. Machine Learning 24(1), 49-64.",
			"Genest, C. and Zidek, J. V. (1986). Combining probability distributions: a critique and an annotated"
					+ " bibliography. Statistical Science 1(1), 114-135.",
			"Sadinle, M., Lei, J. and Wasserman, L. (2019). Least ambiguous set-valued classifiers with bounded error"
					+ " levels. Journal of the American Statistical Association 114(525), 223-234.",
			"Vovk, V., Gammerman, A. and Shafer, G. (2005). Algorithmic Learning in a Random World. Springer.",
			"Huang, Q., He, H., Singh, A., Lim, S.-N. and Benson, A. R. (2021). Combining label propagation and simple"
					+ " models out-performs graph neural networks. ICLR.",
			"Liu, D. C. and Nocedal, J. (1989). On the limited memory BFGS method for large scale optimization."
					+ " Mathematical Programming 45, 503-528.",
			"Manning, C. D., Raghavan, P. and Schütze, H. (2008). Introduction to Information Retrieval, ch. 6"
					+ " (sublinear tf scaling). Cambridge University Press.",
			"Guo, C., Pleiss, G., Sun, Y. and Weinberger, K. Q. (2017). On calibration of modern neural networks."
					+ " ICML (expected calibration error)."};
		for (String reference : references)
		{
			list.add(NodeLabUi.prose(reference));
		}
		card.content(list);
		return card;
	}
}
