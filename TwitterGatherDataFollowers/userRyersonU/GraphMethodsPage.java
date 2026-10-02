package TwitterGatherDataFollowers.userRyersonU;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/**
 * Methods: how the run graph is built, every measure's definition and null
 * model, the parameters that make it reproducible, the references, and the
 * files in the run's record (GEXF for Gephi, GraphML, CSV, SVG and PNG).
 */
final class GraphMethodsPage extends GraphInsightsPanel.Page
{
	private static final long serialVersionUID = 1L;

	private static final String[][] MEASURES = {
		{"The graph",
			"Nodes are the run's users and the accounts they follow (one node per handle). Follows come from the"
					+ " dataset: each user follows one account, whose followers form a real community. Recommendation"
					+ " edges link each target to its top-k candidates, weighted 1/log2(1 + rank), the discount of"
					+ " DCG (Järvelin and Kekäläinen 2002). Adoption edges are the follow simulation's"
					+ " choice: one of the top three, with probability proportional to its score, drawn with a"
					+ " generator seeded by the user's name, so the graph shows exactly what the user agents chose."},
		{"Homophily and its null model",
			"The share of recommendation edges that stay inside the target's community (for account candidates:"
					+ " that point to the account the target follows); unweighted it is the micro-averaged"
					+ " precision@k. Under random lists each target's inside count is hypergeometric (k draws from"
					+ " its candidates); the exact distribution of their sum gives the two-sided p-value. The"
					+ " rank-weighted share is tested against 9,999 random rankings (1,999 for very large runs);"
					+ " when every list holds every"
					+ " candidate only the ranking varies, and this is the test that matters."},
		{"Assortativity",
			"Newman's coefficient r = (Σ e_ii − Σ a_i b_i) / (1 − Σ a_i b_i) over the"
					+ " rank-weighted recommendation edges, e_ij the share of weight from community i to j. It is 0"
					+ " when communities mix as their sizes predict, 1 when every edge stays inside. The standard"
					+ " error is the jackknife over edges (Newman 2003)."},
		{"Community detection and recovery",
			"Leiden (Traag, Waltman and van Eck 2019) maximises modularity Q = Σ_c [L_c/m − γ"
					+ " (K_c/2m)²] at resolution γ = 1; unlike Louvain it guarantees connected"
					+ " communities. It runs five times from fixed seeds and keeps the best partition. Agreement"
					+ " with the real communities is the adjusted mutual information (Vinh, Epps and Bailey 2010)"
					+ " and the adjusted Rand index (Hubert and Arabie 1985), with a 999-permutation test."},
		{"Exposure",
			"A candidate's exposure sums 1/log2(1 + rank) over the lists it appears in, the position-based"
					+ " attention of Singh and Joachims (2018). Inequality: the Gini coefficient and the Lorenz"
					+ " curve; coverage: candidates recommended at least once (aggregate diversity); hubness: the"
					+ " skewness of exposure (Radovanović, Nanopoulos and Ivanović 2010); size bias:"
					+ " Spearman's ρ between exposure and the size of the candidate's community."},
		{"Centralities and roles",
			"PageRank (damping 0.85, weighted, dangling mass spread uniformly) on follows, recommendations and"
					+ " adoptions; the attention gained is its difference from PageRank on the follows alone."
					+ " Betweenness by Brandes' algorithm (exact up to 4,000 nodes, otherwise 400 seeded pivots),"
					+ " normalised by (n − 1)(n − 2). k-cores by Batagelj and Zaversnik. Roles by"
					+ " Guimerà and Amaral (2005): participation coefficient and within-community degree in"
					+ " Leiden's communities of the whole network."},
		{"Adoption and the network",
			"The E–I index (Krackhardt and Stern 1988) of the adopted follows, (external − internal) /"
					+ " total, against random adoption, where each target's adoption is inside its community with"
					+ " the share of same-community candidates; the number inside is then Poisson-binomial and"
					+ " its exact distribution gives the p-value. Before and after: modularity of the real"
					+ " communities, components, Leiden communities and their AMI with the real ones."},
		{"Layout",
			"ForceAtlas2 (Jacomy et al. 2014) with LinLog attraction, whose clusters are the modularity"
					+ " communities (Noack 2009), hub dissuasion, Barnes-Hut repulsion (θ = 1.2) on larger"
					+ " graphs and Gephi's adaptive speeds, then a pass that removes node overlaps. Starting"
					+ " positions are seeded by node name: a graph is always drawn the same way."}
	};

	private static final String[] REFERENCES = {
		"Batagelj, V. and Zaversnik, M. (2003). An O(m) algorithm for cores decomposition of networks. arXiv:cs/0310049.",
		"Brandes, U. (2001). A faster algorithm for betweenness centrality. Journal of Mathematical Sociology 25(2), 163–177.",
		"Brandes, U. and Pich, C. (2007). Centrality estimation in large networks. International Journal of Bifurcation and Chaos 17(7), 2303–2318.",
		"Guimerà, R. and Amaral, L. A. N. (2005). Functional cartography of complex metabolic networks. Nature 433, 895–900.",
		"Hubert, L. and Arabie, P. (1985). Comparing partitions. Journal of Classification 2, 193–218.",
		"Jacomy, M., Venturini, T., Heymann, S. and Bastian, M. (2014). ForceAtlas2, a continuous graph layout algorithm for handy network visualization designed for the Gephi software. PLoS ONE 9(6), e98679.",
		"Järvelin, K. and Kekäläinen, J. (2002). Cumulated gain-based evaluation of IR techniques. ACM Transactions on Information Systems 20(4), 422–446.",
		"Krackhardt, D. and Stern, R. N. (1988). Informal networks and organizational crises: an experimental simulation. Social Psychology Quarterly 51(2), 123–140.",
		"Newman, M. E. J. (2003). Mixing patterns in networks. Physical Review E 67, 026126.",
		"Newman, M. E. J. and Girvan, M. (2004). Finding and evaluating community structure in networks. Physical Review E 69, 026113.",
		"Noack, A. (2009). Modularity clustering is force-directed layout. Physical Review E 79, 026102.",
		"Page, L., Brin, S., Motwani, R. and Winograd, T. (1999). The PageRank citation ranking: bringing order to the web. Stanford InfoLab.",
		"Radovanović, M., Nanopoulos, A. and Ivanović, M. (2010). Hubs in space: popular nearest neighbors in high-dimensional data. Journal of Machine Learning Research 11, 2487–2531.",
		"Singh, A. and Joachims, T. (2018). Fairness of exposure in rankings. Proceedings of KDD 2018, 2219–2228.",
		"Traag, V. A., Waltman, L. and van Eck, N. J. (2019). From Louvain to Leiden: guaranteeing well-connected communities. Scientific Reports 9, 5233.",
		"Vinh, N. X., Epps, J. and Bailey, J. (2010). Information theoretic measures for clusterings comparison. Journal of Machine Learning Research 11, 2837–2854.",
		"Watts, D. J. and Strogatz, S. H. (1998). Collective dynamics of ‘small-world’ networks. Nature 393, 440–442."
	};

	private static final String[][] FILES = {
		{"graph.json", "the analysis: every measure, test, finding and parameter"},
		{"nodes.csv", "every node with its kind, community, centralities, role and position"},
		{"edges.csv", "every edge with its layer, rank, score, weight and community relation"},
		{"graph.gexf", "for Gephi (positions, community colours and sizes included)"},
		{"graph.graphml", "for NetworkX, igraph, Cytoscape and yEd"},
		{"graph.svg", "the figure as vector graphics (papers and slides)"},
		{"graph.png", "the figure at 3200 × 2000"}
	};

	private final NodeLabUi.Page page = new NodeLabUi.Page(1200);
	private final JPanel files = new JPanel(new BorderLayout());
	private RunGraphRecord.Loaded shown;

	GraphMethodsPage(GraphInsightsPanel panel)
	{
		files.setOpaque(false);
	}

	void show(RunGraphRecord.Loaded loaded, String message)
	{
		if (loaded == null)
		{
			shown = null;
			removeAll();
			add(GraphInsightsPanel.waiting(message), BorderLayout.CENTER);
			revalidate();
			repaint();
			return;
		}
		if (shown != null && shown.graph == loaded.graph)
		{
			shown = loaded;
			return;
		}
		shown = loaded;
		removeAll();
		page.clear();
		build(loaded);
		add(NodeLabUi.scroll(page), BorderLayout.CENTER);
		revalidate();
		repaint();
	}

	private void build(RunGraphRecord.Loaded loaded)
	{
		NodeLabUi.Hero hero = new NodeLabUi.Hero("Methods", "How the run graph is built and measured",
				"Every number in Graph Insights is computed from the run's own lists and the dataset's follows,"
						+ " deterministically: the same run always gives the same graph, communities, tests and"
						+ " picture. The analysis is verified against NetworkX, scikit-learn and SciPy.",
				NebulaDesign.SUCCESS);
		hero.chip(new NodeLabUi.Chip("Leiden γ = " + RunGraphAnalysis.RESOLUTION, NodeLabUi.Tone.NEUTRAL, false));
		hero.chip(new NodeLabUi.Chip("Seed " + Long.toHexString(RunGraphAnalysis.SEED), NodeLabUi.Tone.NEUTRAL, false));
		page.section(hero);

		JPanel measures = NodeLabUi.columns(420, 2, 18);
		for (String[] measure : MEASURES)
		{
			NodeLabUi.Card card = new NodeLabUi.Card(measure[0], null);
			card.content(NodeLabUi.prose(measure[1]));
			measures.add(card);
		}
		page.section(measures);

		NodeLabUi.Card parameters = new NodeLabUi.Card("Parameters of this run's analysis", null);
		RunGraph graph = loaded.graph;
		NodeLabUi.Table table = new NodeLabUi.Table(NodeLabUi.Table.text("Parameter", 1.2f),
				NodeLabUi.Table.text("Value", 2.4f));
		List<String[]> rows = new ArrayList<String[]>();
		rows.add(new String[] {"Run", graph.runId});
		rows.add(new String[] {"Algorithm, dataset", graph.algorithm + ", " + graph.dataset});
		rows.add(new String[] {"Candidates", (graph.candidateType == RecommendationCandidateType.FOLLOWEE ? "accounts"
				: "users") + ", " + graph.candidateCount() + "; k = " + graph.cutoff});
		rows.add(new String[] {"Leiden", "modularity, resolution " + RunGraphAnalysis.RESOLUTION + ", randomness "
				+ LeidenClustering.RANDOMNESS + ", best of " + RunGraphAnalysis.LEIDEN_STARTS + " seeded starts,"
				+ " iterated until stable"});
		rows.add(new String[] {"Permutation tests", RunGraphAnalysis.PERMUTATIONS + " label permutations (AMI), "
				+ String.format(Locale.US, "%,d", loaded.analysis.listPermutations)
				+ " random rankings (rank-weighted homophily)"});
		rows.add(new String[] {"PageRank", "damping " + RunGraphAnalysis.DAMPING + ", tolerance 1e-12"});
		rows.add(new String[] {"Betweenness", loaded.analysis.betweenness.exact ? "exact (Brandes)"
				: "estimated from " + loaded.analysis.betweenness.sources + " seeded pivots"});
		rows.add(new String[] {"Layout", GraphLayout.Settings.forSize(graph.size()).describe()});
		rows.add(new String[] {"Seed", String.format(Locale.US, "0x%016X", RunGraphAnalysis.SEED)});
		table.setRows(rows, null);
		parameters.content(table);
		page.section(parameters);

		NodeLabUi.Card filesCard = new NodeLabUi.Card("Files in the run record",
				"Every run writes its graph into the graph folder of its record; the layout and figures are written"
						+ " when the run's Generate Final Graph Output switch is on.");
		updateFiles(loaded);
		filesCard.content(files);
		page.section(filesCard);

		NodeLabUi.Card references = new NodeLabUi.Card("References", null);
		JPanel list = GraphInsightsPanel.column(6);
		for (String reference : REFERENCES)
		{
			list.add(NodeLabUi.prose(reference));
		}
		references.content(list);
		page.section(references);
	}

	private void updateFiles(final RunGraphRecord.Loaded loaded)
	{
		files.removeAll();
		final File folder = loaded.directory == null ? null : new File(loaded.directory, RunGraphRecord.FOLDER);
		NodeLabUi.Table table = new NodeLabUi.Table(NodeLabUi.Table.text("File", 0.8f),
				NodeLabUi.Table.text("Holds", 2.4f), NodeLabUi.Table.number("Size", 0.6f));
		List<String[]> rows = new ArrayList<String[]>();
		boolean complete = folder != null;
		for (String[] file : FILES)
		{
			File path = folder == null ? null : new File(folder, file[0]);
			boolean present = path != null && path.isFile();
			complete &= present;
			rows.add(new String[] {file[0], file[1], present ? NodeLabUi.bytes(path.length()) : "not written"});
		}
		table.setRows(rows, null);
		NodeLabUi.Button open = new NodeLabUi.Button("Open folder", NodeLabUi.ButtonKind.SECONDARY,
				NodeLabUi.Glyph.FOLDER, true);
		open.setEnabled(folder != null && folder.isDirectory());
		open.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent event)
			{
				try
				{
					if (java.awt.Desktop.isDesktopSupported())
					{
						java.awt.Desktop.getDesktop().open(folder);
					}
				}
				catch (Exception unavailable)
				{
					System.err.println("Could not open " + folder + ": " + unavailable.getMessage());
				}
			}
		});
		final NodeLabUi.Button write = new NodeLabUi.Button(complete ? "Rewrite files" : "Write all files",
				complete ? NodeLabUi.ButtonKind.GHOST : NodeLabUi.ButtonKind.PRIMARY, NodeLabUi.Glyph.SAVE, true);
		write.setEnabled(loaded.directory != null);
		write.setToolTipText("Lay the graph out and write every file above into this run's record");
		write.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent event)
			{
				write.setEnabled(false);
				write.setText("Writing…");
				Thread worker = new Thread(new Runnable()
				{
					public void run()
					{
						try
						{
							RunGraphRecord.write(loaded.directory, loaded.graph, true);
						}
						catch (Exception failure)
						{
							System.err.println("Could not write the run graph: " + failure.getMessage());
						}
						SwingUtilities.invokeLater(new Runnable()
						{
							public void run()
							{
								updateFiles(loaded);
							}
						});
					}
				}, "DSMP-Graph-Writer");
				worker.setDaemon(true);
				worker.start();
			}
		});
		JComponent note = NodeLabUi.mono(folder == null ? "No record folder" : folder.getPath());
		files.add(NodeLabUi.stack(10, table, note, NodeLabUi.row(8, open, write)), BorderLayout.CENTER);
		files.revalidate();
		files.repaint();
	}
}
