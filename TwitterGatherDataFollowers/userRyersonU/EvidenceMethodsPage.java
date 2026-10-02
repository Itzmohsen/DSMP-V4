package TwitterGatherDataFollowers.userRyersonU;

import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JComponent;
import javax.swing.JPanel;

/**
 * How Evidence Studio explains: the reference models and when they are exact,
 * the decomposition, the counterfactuals, the fidelity measures and the
 * references.
 */
final class EvidenceMethodsPage extends RecommendationEvidenceStudioPanel.Page
{
	private static final long serialVersionUID = 1L;
	private boolean built;

	void show(RecommendationEvidenceEngine.EvidenceRun run, String message)
	{
		if (built)
		{
			return;
		}
		built = true;
		NodeLabUi.Page page = new NodeLabUi.Page(1180);
		page.section(new NodeLabUi.Hero("Methods", "Exact where possible, measured everywhere else",
				"Every recommendation is explained against a reference model recomputed from the run's own corpus."
						+ " Where the reference is the algorithm's scoring rule the explanation is the model's; where it"
						+ " is not, its fidelity to the algorithm's list is measured and shown.", NebulaDesign.SUCCESS));
		page.section(section("The run's text",
				"Every run writes its canonical corpus: the tweets it used, normalised exactly as the recommender nodes"
						+ " normalise them, content-addressed so it is written once (.runtime/run-corpus). Evidence"
						+ " Studio recomputes the agents' TF-IDF from it: raw term frequency times log2(N / df), N the"
						+ " users with a document and df the users writing the term, L2-normalised per user. The"
						+ " cosine of two such rows is the Similarity algorithm's score."));
		page.section(references());
		page.section(section("Exact decomposition",
				"A cosine of unit vectors is the sum over shared terms of a_t b_t, so each word's share is exact. The"
						+ " link-prediction scores are sums over shared terms as well: common neighbours counts each"
						+ " shared term once and adds the Adamic-Adar tie-break, shared here in proportion to each"
						+ " term's 1 / ln(degree); Adamic-Adar adds 1 / ln(degree); resource allocation 1 / degree;"
						+ " Jaccard 1 / |union| (degree: the users writing the term, at least 2). An account recommended"
						+ " by a user ranker scores its best follower, so its evidence is that follower's."));
		page.section(section("Counterfactuals",
				"Removing a word means removing it from the target's profile while the corpus statistics (every"
						+ " other user's vector and the document frequencies) stay fixed: the usual perturbation of"
						+ " the instance, not of the model. Scores are updated exactly through the term's postings."
						+ " The minimal sets remove the target's words in order of their contribution until the"
						+ " recommendation loses its reference rank, and until it leaves the delivered list's length:"
						+ " the minimal counterfactual explanation of Ghazimatin et al. (PRINCE), on the reference."));
		page.section(section("Fidelity",
				"The delivered list and the reference ranking (both truncated at the list's length k) are compared"
						+ " by rank-biased overlap (Webber, Moffat and Zobel 2010, extrapolated, persistence 0.9: the"
						+ " first ranks weigh most; 1 for the same list), overlap@k, and Kendall's tau-b between the"
						+ " delivered scores and the reference scores of the delivered items. For exact references the"
						+ " studio also reports the largest relative difference between the reported and recomputed"
						+ " score ratios (delivered scores may be rescaled so the first is 100)."));
		page.section(section("Ground truth and context",
				"Relevance uses the follows recorded in the corpus (never shown to the algorithms): a user is relevant"
						+ " when it shares the target's account, an account when the target follows it. Relevance by"
						+ " rank is compared with the chance rate of a random list from the target's candidates. The"
						+ " run graph adds whether the recommendation stays inside the target's community, its exposure"
						+ " and PageRank, and the simulated adoption; on TGF runs, TGF's own exact explanation is"
						+ " shown alongside."));
		page.section(literature());
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

	private static JComponent references()
	{
		NodeLabUi.Card card = new NodeLabUi.Card("Reference model per algorithm", null);
		NodeLabUi.Table table = new NodeLabUi.Table(NodeLabUi.Table.text("Algorithm", 1.1f),
				NodeLabUi.Table.text("Users", 1.6f), NodeLabUi.Table.text("Accounts", 1.6f));
		List<String[]> rows = new ArrayList<String[]>();
		rows.add(new String[] {"Similarity", "TF-IDF cosine (exact)", "best follower's cosine (exact)"});
		rows.add(new String[] {"Common Neighbors", "the run's link-prediction variant (exact)",
				"best follower's link score (exact)"});
		rows.add(new String[] {"K-Means, K-Means Euclidean, Doc2Vec", "TF-IDF cosine (fidelity measured)",
				"best follower's cosine (fidelity measured)"});
		rows.add(new String[] {"SVM, Scalable SVM, MLP", "TF-IDF cosine (fidelity measured)",
				"cosine to the account's follower centroid (fidelity measured)"});
		rows.add(new String[] {"Temporal Graph Foundation", "TF-IDF cosine, plus TGF's own exact explanation",
				"centroid cosine, plus TGF's own exact explanation"});
		table.setRows(rows, null);
		card.content(table);
		return card;
	}

	private static JComponent literature()
	{
		NodeLabUi.Card card = new NodeLabUi.Card("References", null);
		JPanel list = StudioShell.column(6);
		String[] references = {
			"Webber, W., Moffat, A. and Zobel, J. (2010). A similarity measure for indefinite rankings. ACM"
					+ " Transactions on Information Systems 28(4).",
			"Ghazimatin, A., Balalau, O., Saha Roy, R. and Weikum, G. (2020). PRINCE: Provider-side interpretability"
					+ " with counterfactual explanations in recommender systems. WSDM.",
			"Kendall, M. G. (1945). The treatment of ties in ranking problems. Biometrika 33(3), 239-251.",
			"Liben-Nowell, D. and Kleinberg, J. (2007). The link-prediction problem for social networks. JASIST"
					+ " 58(7), 1019-1031.",
			"Adamic, L. A. and Adar, E. (2003). Friends and neighbors on the web. Social Networks 25(3), 211-230.",
			"Zhou, T., Lü, L. and Zhang, Y.-C. (2009). Predicting missing links via local information. European"
					+ " Physical Journal B 71, 623-630 (resource allocation).",
			"Guidotti, R. et al. (2018). A survey of methods for explaining black box models. ACM Computing Surveys"
					+ " 51(5) (fidelity of surrogate explanations).",
			"Lundberg, S. M. and Lee, S.-I. (2017). A unified approach to interpreting model predictions. NeurIPS"
					+ " (additive feature attributions).",
			"Salton, G. and Buckley, C. (1988). Term-weighting approaches in automatic text retrieval. Information"
					+ " Processing and Management 24(5), 513-523."};
		for (String reference : references)
		{
			list.add(NodeLabUi.prose(reference));
		}
		card.content(list);
		return card;
	}
}
