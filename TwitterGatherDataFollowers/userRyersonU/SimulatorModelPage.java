package TwitterGatherDataFollowers.userRyersonU;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

/**
 * Step 2: the recommendation algorithm, what it recommends, and its
 * parameters.
 */
final class SimulatorModelPage extends SimulatorPanel.Page
{
	private static final long serialVersionUID = 1L;

	/** The algorithms in the order they are shown: user rankers, then classifiers. */
	private static final int[] RANKERS = {
		RecommendationModeSupport.COS_SIM, RecommendationModeSupport.COMMON_NEIGHBORS,
		RecommendationModeSupport.K_MEANS, RecommendationModeSupport.K_MEANS_EUCLIDEAN,
		RecommendationModeSupport.DOC2VEC, RecommendationModeSupport.TEMPORAL_GRAPH_FOUNDATION
	};
	private static final int[] CLASSIFIERS = {
		RecommendationModeSupport.SVM, RecommendationModeSupport.SCALABLE_SVM, RecommendationModeSupport.MLP
	};
	private static final RecommendationTargetMode[] MODES = {
		RecommendationTargetMode.ALGORITHM_DEFAULT, RecommendationTargetMode.SIMILAR_USERS,
		RecommendationTargetMode.FOLLOWEE_ACCOUNTS
	};

	private final ControllerAgentGui gui;
	private final NodeLabUi.Page page = new NodeLabUi.Page(1240);
	private final NodeLabUi.ChoiceTile[] tiles = new NodeLabUi.ChoiceTile[9];
	private final NodeLabUi.Segmented mode = new NodeLabUi.Segmented(
			new String[] {"Algorithm default", "Similar users", "Followee accounts"},
			new NodeLabUi.Glyph[] {NodeLabUi.Glyph.SPARK, NodeLabUi.Glyph.NETWORK, NodeLabUi.Glyph.TARGET}, 0);
	private final NodeLabUi.Text recommends = NodeLabUi.heading(" ");
	private final NodeLabUi.Prose recommendsText = NodeLabUi.prose(" ");
	private final NodeLabUi.Prose relevanceText = NodeLabUi.prose(" ");
	private final NodeLabUi.Chip pipeline = new NodeLabUi.Chip("Native output", NodeLabUi.Tone.SUCCESS, true);
	private final NodeLabUi.Chip profile = new NodeLabUi.Chip("Default", NodeLabUi.Tone.SUCCESS, true);
	private final NodeLabUi.Prose summary = NodeLabUi.prose(" ");
	private final NodeLabUi.Button edit;
	private final NodeLabUi.Card cache = new NodeLabUi.Card("Doc2Vec model cache",
			"Doc2Vec trains on the run's corpus once and reuses the vectors while the corpus and settings match.");
	private final NodeLabUi.Chip cacheStatus = new NodeLabUi.Chip("Unknown", NodeLabUi.Tone.NEUTRAL, true);
	private final NodeLabUi.Prose cacheDetail = NodeLabUi.prose(" ");
	private final NodeLabUi.Button cacheRefresh;
	private final NodeLabUi.Button cacheRemove;

	SimulatorModelPage(SimulatorPanel simulator)
	{
		this.gui = simulator.gui();
		NodeLabUi.Card rankers = new NodeLabUi.Card("Rankers",
				"Score every pair of users and recommend the most similar ones.");
		JPanel rankerTiles = NodeLabUi.grid(250, 3, 12);
		for (int algorithm : RANKERS)
		{
			rankerTiles.add(tile(algorithm));
		}
		rankers.content(rankerTiles);
		NodeLabUi.Card classifiers = new NodeLabUi.Card("Classifiers",
				"Learn each user's followee from labelled users, and recommend the likely accounts.");
		classifiers.action(NodeLabUi.help("Classifiers never see their targets",
				"A classifier is never trained on a user it then scores. The exact SVM trains one model per"
						+ " target without it. The Scalable SVM and the MLP hold the targets out of training:"
						+ " one model when they are few, otherwise five folds of targets, each scored by a model"
						+ " trained on the rest."));
		JPanel classifierTiles = NodeLabUi.grid(250, 3, 12);
		for (int algorithm : CLASSIFIERS)
		{
			classifierTiles.add(tile(algorithm));
		}
		classifiers.content(classifierTiles);

		NodeLabUi.Card output = new NodeLabUi.Card("What to recommend",
				"Users similar to the target, or the accounts the target is likely to follow.");
		output.action(pipeline);
		mode.addChangeListener(new ChangeListener()
		{
			public void stateChanged(ChangeEvent event)
			{
				JComboBox<RecommendationTargetMode> box = gui.simulatorTargetModeBox();
				if (box != null && box.getSelectedItem() != MODES[mode.getSelected()])
				{
					box.setSelectedItem(MODES[mode.getSelected()]);
				}
				refresh();
			}
		});
		output.content(NodeLabUi.stack(14, mode, NodeLabUi.stack(4, recommends, recommendsText, relevanceText)));

		NodeLabUi.Card parameters = new NodeLabUi.Card("Parameters",
				"Every algorithm runs with documented defaults unless a custom profile is switched on.");
		edit = SimulatorPanel.mirror("Edit parameters", NodeLabUi.ButtonKind.SECONDARY, NodeLabUi.Glyph.SLIDERS,
				gui.simulatorParametersButton());
		parameters.action(edit);
		parameters.content(NodeLabUi.stack(8, profile, summary));

		cacheRefresh = SimulatorPanel.mirror("Check again", NodeLabUi.ButtonKind.SECONDARY, NodeLabUi.Glyph.REFRESH,
				gui.simulatorDoc2VecRefreshButton());
		cacheRemove = SimulatorPanel.mirror("Remove cache", NodeLabUi.ButtonKind.DANGER, NodeLabUi.Glyph.TRASH,
				gui.simulatorDoc2VecRemoveButton());
		cache.action(cacheStatus);
		cache.content(NodeLabUi.stack(12, cacheDetail, NodeLabUi.row(8, cacheRefresh, cacheRemove)));

		page.section(rankers).section(classifiers).section(output).section(parameters).section(cache);
		add(NodeLabUi.scroll(page), BorderLayout.CENTER);
		refresh();
	}

	private NodeLabUi.ChoiceTile tile(final int algorithm)
	{
		NodeLabUi.ChoiceTile tile = new NodeLabUi.ChoiceTile(glyph(algorithm),
				RecommendationModeSupport.isClassifierAlgorithm(algorithm) ? NodeLabUi.Tone.PURPLE : NodeLabUi.Tone.ACCENT,
				RecommendationModeSupport.algorithmName(algorithm), description(algorithm),
				RecommendationModeSupport.isClassifierAlgorithm(algorithm) ? "Predicts followee accounts"
						: "Ranks users", true);
		tile.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent event)
			{
				JComboBox<String> box = gui.simulatorAlgorithmBox();
				if (box != null && box.isEnabled() && box.getSelectedIndex() != algorithm)
				{
					box.setSelectedIndex(algorithm);
				}
				refresh();
			}
		});
		tiles[algorithm] = tile;
		return tile;
	}

	static String description(int algorithm)
	{
		switch (algorithm)
		{
			case RecommendationModeSupport.COS_SIM:
				return "TF-IDF word vectors compared by cosine similarity.";
			case RecommendationModeSupport.COMMON_NEIGHBORS:
				return "Shared terms on the user-term graph, scored by count, Jaccard, Adamic-Adar or resource"
						+ " allocation.";
			case RecommendationModeSupport.K_MEANS:
				return "K-Means clusters of TF-IDF vectors (cosine, farthest-first seeding); recommends the"
						+ " target's cluster mates.";
			case RecommendationModeSupport.K_MEANS_EUCLIDEAN:
				return "The same clustering with Euclidean distance.";
			case RecommendationModeSupport.DOC2VEC:
				return "Paragraph vectors learned from the run's corpus; recommends the nearest documents.";
			case RecommendationModeSupport.TEMPORAL_GRAPH_FOUNDATION:
				return "A logistic fusion of text, graph diffusion and time co-activity, learned from labelled"
						+ " non-target users.";
			case RecommendationModeSupport.SVM:
				return "Exact Weka SMO with calibrated probabilities; one model per target, trained without it.";
			case RecommendationModeSupport.SCALABLE_SVM:
				return "A linear model learned online with Passive-Aggressive (PA-I) hinge-loss updates.";
			case RecommendationModeSupport.MLP:
				return "A softmax network trained with cross-entropy and early stopping (or the V3 Neuroph net).";
			default:
				return "";
		}
	}

	private static NodeLabUi.Glyph glyph(int algorithm)
	{
		switch (algorithm)
		{
			case RecommendationModeSupport.COS_SIM:
				return NodeLabUi.Glyph.NETWORK;
			case RecommendationModeSupport.COMMON_NEIGHBORS:
				return NodeLabUi.Glyph.CLUSTER;
			case RecommendationModeSupport.K_MEANS:
				return NodeLabUi.Glyph.LAYERS;
			case RecommendationModeSupport.K_MEANS_EUCLIDEAN:
				return NodeLabUi.Glyph.GAUGE;
			case RecommendationModeSupport.DOC2VEC:
				return NodeLabUi.Glyph.SCRIPT;
			case RecommendationModeSupport.TEMPORAL_GRAPH_FOUNDATION:
				return NodeLabUi.Glyph.CLOCK;
			case RecommendationModeSupport.SVM:
				return NodeLabUi.Glyph.TARGET;
			case RecommendationModeSupport.SCALABLE_SVM:
				return NodeLabUi.Glyph.BOLT;
			default:
				return NodeLabUi.Glyph.CPU;
		}
	}

	void refresh()
	{
		JComboBox<String> box = gui.simulatorAlgorithmBox();
		int algorithm = box == null ? 0 : box.getSelectedIndex();
		boolean editable = box != null && box.isEnabled();
		for (int index = 0; index < tiles.length; index++)
		{
			if (tiles[index] == null)
			{
				continue;
			}
			if (tiles[index].isSelected() != (index == algorithm))
			{
				tiles[index].setSelected(index == algorithm);
			}
			if (tiles[index].isEnabled() != editable)
			{
				tiles[index].setEnabled(editable);
			}
		}
		JComboBox<RecommendationTargetMode> modeBox = gui.simulatorTargetModeBox();
		RecommendationTargetMode selected = modeBox == null || modeBox.getSelectedItem() == null
				? RecommendationTargetMode.ALGORITHM_DEFAULT : (RecommendationTargetMode)modeBox.getSelectedItem();
		for (int index = 0; index < MODES.length; index++)
		{
			if (MODES[index] == selected && mode.getSelected() != index)
			{
				mode.setSelectedQuietly(index);
			}
		}
		mode.setEnabled(modeBox != null && modeBox.isEnabled());
		String name = RecommendationModeSupport.algorithmName(algorithm);
		RecommendationCandidateType type = RecommendationModeSupport.resolveCandidateType(selected, algorithm);
		boolean converted = RecommendationModeSupport.requiresConvertedPipeline(selected, algorithm);
		boolean users = type == RecommendationCandidateType.USER;
		recommends.setText(users ? "Recommends similar users" : "Recommends followee accounts");
		if (!converted)
		{
			recommendsText.setText(users
					? name + " ranks the other users by its own score."
					: name + " ranks the followee accounts by the probability that the target follows each.");
			pipeline.set("Native output", NodeLabUi.Tone.SUCCESS);
		}
		else if (users)
		{
			recommendsText.setText(name + " predicts followee accounts; users are ranked by the probability of"
					+ " their account times their text similarity to the target.");
			pipeline.set("Converted output", NodeLabUi.Tone.INFO);
		}
		else
		{
			recommendsText.setText(name + " ranks users; each account scores as its best-scored follower.");
			pipeline.set("Converted output", NodeLabUi.Tone.INFO);
		}
		relevanceText.setText(users
				? "Evaluation: a recommended user is relevant when it follows the target's own followee"
						+ " (same community)."
				: "Evaluation: a recommended account is relevant when it is the target's own followee.");
		String profileText = gui.simulatorParameterProfile();
		profile.set(profileText == null || profileText.isEmpty() ? "Default" : profileText,
				profileText != null && profileText.toLowerCase(java.util.Locale.US).contains("custom")
						? NodeLabUi.Tone.WARNING : NodeLabUi.Tone.SUCCESS);
		summary.setText(gui.simulatorParameterSummary());
		SimulatorPanel.read(edit, gui.simulatorParametersButton());
		boolean doc2vec = algorithm == RecommendationModeSupport.DOC2VEC;
		if (cache.isVisible() != doc2vec)
		{
			cache.setVisible(doc2vec);
			page.revalidate();
		}
		if (doc2vec)
		{
			String status = gui.simulatorDoc2VecCacheStatus();
			cacheStatus.set(status == null || status.isEmpty() ? "Unknown" : SimulatorPanel.capitalize(status),
					status != null && status.toUpperCase(java.util.Locale.US).contains("READY")
							? NodeLabUi.Tone.SUCCESS : NodeLabUi.Tone.NEUTRAL);
			cacheDetail.setText(gui.simulatorDoc2VecCacheDetail());
			SimulatorPanel.read(cacheRefresh, gui.simulatorDoc2VecRefreshButton());
			SimulatorPanel.read(cacheRemove, gui.simulatorDoc2VecRemoveButton());
		}
	}
}
