package TwitterGatherDataFollowers.userRyersonU;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.swing.JComboBox;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

/**
 * Step 3: who the run recommends for, how many recommendations each target
 * gets, and how the lists will be evaluated, with the chance level of every
 * headline metric before anything runs.
 */
final class SimulatorTargetsPage extends SimulatorPanel.Page
{
	private static final long serialVersionUID = 1L;
	private static final TargetSelection.Mode[] MODES = {
		TargetSelection.Mode.SELECTED, TargetSelection.Mode.SAMPLE, TargetSelection.Mode.ALL
	};

	private final SimulatorPanel simulator;
	private final ControllerAgentGui gui;
	private final NodeLabUi.Page page = new NodeLabUi.Page(1240);
	private final NodeLabUi.Segmented mode = new NodeLabUi.Segmented(
			new String[] {"Selected users", "Stratified sample", "All users"},
			new NodeLabUi.Glyph[] {NodeLabUi.Glyph.LIST, NodeLabUi.Glyph.FLASK, NodeLabUi.Glyph.CLUSTER}, 0);
	private final CardLayout modeCards = new CardLayout();
	/** Shows one mode's controls, as tall as that mode needs. */
	private final JPanel modeDeck = new JPanel(modeCards)
	{
		private static final long serialVersionUID = 1L;

		public Dimension getPreferredSize()
		{
			for (java.awt.Component child : getComponents())
			{
				if (child.isVisible())
				{
					return child.getPreferredSize();
				}
			}
			return super.getPreferredSize();
		}
	};
	private final NodeLabUi.Text selectedCount = NodeLabUi.strong(" ");
	private final CardLayout listCards = new CardLayout();
	private final JPanel listDeck = new JPanel(listCards);
	private boolean listShown;
	private final NodeLabUi.Field sampleSize = new NodeLabUi.Field("50", 6, "n");
	private final NodeLabUi.Field seed = new NodeLabUi.Field(Long.toString(TargetSelection.DEFAULT_SEED), 12, "seed");
	private final NodeLabUi.Prose sampleText = NodeLabUi.prose(" ");
	private final NodeLabUi.Prose allText = NodeLabUi.prose(" ");
	private final NodeLabUi.Stepper cutoff = new NodeLabUi.Stepper(10, 3, 100);
	private final NodeLabUi.Metric targets = new NodeLabUi.Metric("Targets", "—", " ", NebulaDesign.ACCENT);
	private final NodeLabUi.Metric candidates = new NodeLabUi.Metric("Candidates each", "—", " ", NebulaDesign.INFO);
	private final NodeLabUi.Metric relevant = new NodeLabUi.Metric("Relevant each", "—", " ", NebulaDesign.SUCCESS);
	private final NodeLabUi.Metric chancePrecision = new NodeLabUi.Metric("Chance P@k", "—", " ", NebulaDesign.PURPLE);
	private final NodeLabUi.Metric chanceNdcg = new NodeLabUi.Metric("Chance NDCG@k", "—", " ", NebulaDesign.PURPLE);
	private final NodeLabUi.Prose protocol = NodeLabUi.prose(" ");
	private final JPanel warnings = NodeLabUi.stack(6);
	private String previewKey = "";

	SimulatorTargetsPage(SimulatorPanel simulator)
	{
		this.simulator = simulator;
		this.gui = simulator.gui();
		NodeLabUi.Card who = new NodeLabUi.Card("Who gets recommendations",
				"The targets are the queries of the evaluation: every target receives a ranked list.");
		who.action(NodeLabUi.help("Choosing targets",
				"Selected users: the users you pick in the list (click, shift-click or ctrl/cmd-click)."
						+ " Stratified sample: n users drawn with a seed so that every followee community keeps"
						+ " its share; the same seed always gives the same targets. All users: everyone."
						+ " More targets give tighter confidence intervals."));
		mode.addChangeListener(new ChangeListener()
		{
			public void stateChanged(ChangeEvent event)
			{
				applySelection();
			}
		});
		modeDeck.setOpaque(false);
		modeDeck.add(selectedPanel(), TargetSelection.Mode.SELECTED.name());
		modeDeck.add(samplePanel(), TargetSelection.Mode.SAMPLE.name());
		modeDeck.add(NodeLabUi.stack(8, allText), TargetSelection.Mode.ALL.name());
		who.content(NodeLabUi.stack(14, mode, modeDeck));

		NodeLabUi.Card length = new NodeLabUi.Card("List length k",
				"How many recommendations each target receives; every metric is cut off at k.");
		SimulatorPanel.bind(cutoff, gui.simulatorTopKField());
		JPanel lengthRow = new JPanel(new BorderLayout(16, 0));
		lengthRow.setOpaque(false);
		JPanel stepperHolder = new JPanel(new BorderLayout());
		stepperHolder.setOpaque(false);
		stepperHolder.add(cutoff, BorderLayout.NORTH);
		lengthRow.add(stepperHolder, BorderLayout.WEST);
		lengthRow.add(NodeLabUi.prose("P@k counts hits out of k, so a list shorter than k is not rewarded; NDCG@k"
				+ " compares the list with the best possible list of k."), BorderLayout.CENTER);
		length.content(lengthRow);

		NodeLabUi.Card evaluation = new NodeLabUi.Card("How the run will be evaluated",
				"Computed from the loaded users before anything runs.");
		evaluation.action(NodeLabUi.help("Reading the chance level",
				"Chance is the exact expected value of a metric when the candidates are ranked at random"
						+ " (no sampling). A model is only useful by its margin over chance; the Results page"
						+ " tests that margin with a paired t-test over targets."));
		JPanel tiles = NodeLabUi.grid(150, 5, 10);
		tiles.add(targets);
		tiles.add(candidates);
		tiles.add(relevant);
		tiles.add(chancePrecision);
		tiles.add(chanceNdcg);
		evaluation.content(NodeLabUi.stack(14, tiles, protocol, warnings));

		page.section(who).section(length).section(evaluation);
		add(NodeLabUi.scroll(page), BorderLayout.CENTER);
		refresh();
	}

	private JPanel selectedPanel()
	{
		JList list = gui.simulatorUserList();
		JScrollPane scroll = NodeLabUi.scroll(list);
		scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		JPanel listHolder = new JPanel(new BorderLayout())
		{
			private static final long serialVersionUID = 1L;

			public Dimension getPreferredSize()
			{
				return new Dimension(300, 340);
			}

			public Dimension getMaximumSize()
			{
				return new Dimension(Integer.MAX_VALUE, 340);
			}
		};
		listHolder.setOpaque(false);
		listDeck.setOpaque(false);
		NodeLabUi.Button goData = new NodeLabUi.Button("Go to Data", NodeLabUi.ButtonKind.SECONDARY,
				NodeLabUi.Glyph.ARROW_RIGHT, true);
		goData.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent event)
			{
				simulator.show(SimulatorPanel.DATA);
			}
		});
		listDeck.add(NodeLabUi.emptyState(NodeLabUi.Glyph.LIST, "No users yet",
				"Load a dataset's users on the Data page, then pick targets here.", goData), "empty");
		listDeck.add(scroll, "list");
		listHolder.add(listDeck, BorderLayout.CENTER);
		NodeLabUi.Button all = new NodeLabUi.Button("Select all", NodeLabUi.ButtonKind.GHOST, NodeLabUi.Glyph.CHECK,
				true);
		all.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent event)
			{
				JList users = gui.simulatorUserList();
				if (users != null && users.getModel().getSize() > 0)
				{
					users.setSelectionInterval(0, users.getModel().getSize() - 1);
				}
			}
		});
		NodeLabUi.Button clear = new NodeLabUi.Button("Clear", NodeLabUi.ButtonKind.GHOST, NodeLabUi.Glyph.CROSS, true);
		clear.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent event)
			{
				JList users = gui.simulatorUserList();
				if (users != null)
				{
					users.clearSelection();
				}
			}
		});
		return NodeLabUi.stack(10, NodeLabUi.spread(selectedCount, NodeLabUi.row(6, all, clear)), listHolder);
	}

	private JPanel samplePanel()
	{
		sampleSize.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent event)
			{
				applySelection();
			}
		});
		sampleSize.addFocusListener(new java.awt.event.FocusAdapter()
		{
			public void focusLost(java.awt.event.FocusEvent event)
			{
				applySelection();
			}
		});
		seed.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent event)
			{
				applySelection();
			}
		});
		seed.addFocusListener(new java.awt.event.FocusAdapter()
		{
			public void focusLost(java.awt.event.FocusEvent event)
			{
				applySelection();
			}
		});
		NodeLabUi.Button shuffle = new NodeLabUi.Button("New seed", NodeLabUi.ButtonKind.GHOST, NodeLabUi.Glyph.REFRESH,
				true);
		shuffle.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent event)
			{
				seed.setText(Long.toString(new java.util.Random().nextInt(1000000)));
				applySelection();
			}
		});
		JPanel presets = NodeLabUi.row(4);
		for (final int preset : new int[] {20, 40, 100, 200})
		{
			NodeLabUi.Button button = new NodeLabUi.Button(Integer.toString(preset), NodeLabUi.ButtonKind.GHOST,
					null, true);
			button.addActionListener(new ActionListener()
			{
				public void actionPerformed(ActionEvent event)
				{
					sampleSize.setText(Integer.toString(preset));
					applySelection();
				}
			});
			presets.add(button);
		}
		JPanel controls = NodeLabUi.row(16, NodeLabUi.labeled("Targets", NodeLabUi.row(6, sampleSize, presets)),
				NodeLabUi.labeled("Seed", NodeLabUi.row(6, seed, shuffle)));
		return NodeLabUi.stack(12, controls, sampleText);
	}

	private void applySelection()
	{
		long seedValue;
		try
		{
			seedValue = Long.parseLong(seed.getText().trim());
		}
		catch (NumberFormatException invalid)
		{
			seedValue = TargetSelection.DEFAULT_SEED;
		}
		int size;
		try
		{
			size = Math.max(1, Math.min(1000000, Integer.parseInt(sampleSize.getText().trim())));
		}
		catch (NumberFormatException invalid)
		{
			size = gui.getTargetSampleSize();
		}
		TargetSelection.Mode chosen = MODES[Math.max(0, mode.getSelected())];
		gui.setTargetSelection(chosen, size, seedValue);
		modeCards.show(modeDeck, chosen.name());
		previewKey = "";
		refresh();
	}

	void refresh()
	{
		TargetSelection.Mode current = gui.getTargetSelectionMode();
		for (int index = 0; index < MODES.length; index++)
		{
			if (MODES[index] == current && mode.getSelected() != index)
			{
				mode.setSelectedQuietly(index);
				modeCards.show(modeDeck, current.name());
			}
		}
		if (!sampleSize.isFocusOwner()
				&& !sampleSize.getText().trim().equals(Integer.toString(gui.getTargetSampleSize())))
		{
			sampleSize.setText(Integer.toString(gui.getTargetSampleSize()));
		}
		if (!seed.isFocusOwner() && !seed.getText().equals(Long.toString(gui.getTargetSampleSeed())))
		{
			seed.setText(Long.toString(gui.getTargetSampleSeed()));
		}
		SimulatorPanel.read(cutoff, gui.simulatorTopKField(), 10);
		List<String> users = gui.loadedUsers();
		if (listShown == users.isEmpty())
		{
			listShown = !users.isEmpty();
			listCards.show(listDeck, listShown ? "list" : "empty");
		}
		List<String> chosen = users.isEmpty() ? new ArrayList<String>() : gui.currentTargets();
		int selected = gui.selectedUsers().size();
		selectedCount.setText(users.isEmpty() ? "Load users on the Data page first."
				: selected + " of " + users.size() + " users selected");
		allText.setText(users.isEmpty() ? "Load users on the Data page first."
				: "All " + users.size() + " users are targets. Every list is scored, so the intervals are as"
						+ " tight as the data allows; the run takes longest.");
		String key = current + "|" + chosen.hashCode() + "|" + cutoff.getValue() + "|" + algorithm() + "|"
				+ targetMode() + "|" + users.size();
		if (!key.equals(previewKey))
		{
			previewKey = key;
			preview(users, chosen);
		}
	}

	private int algorithm()
	{
		JComboBox<String> box = gui.simulatorAlgorithmBox();
		return box == null ? 0 : Math.max(0, box.getSelectedIndex());
	}

	private RecommendationTargetMode targetMode()
	{
		JComboBox<RecommendationTargetMode> box = gui.simulatorTargetModeBox();
		return box == null || box.getSelectedItem() == null ? RecommendationTargetMode.ALGORITHM_DEFAULT
				: (RecommendationTargetMode)box.getSelectedItem();
	}

	/** The evaluation preview: sizes, chance levels and anything that makes a figure trivial. */
	private void preview(List<String> users, List<String> chosen)
	{
		Map<String,String> labels = gui.simulatorFollowees();
		HashMap<String,Integer> community = new HashMap<String,Integer>();
		for (String user : users)
		{
			String label = labels.get(user);
			if (label != null && label.length() > 0)
			{
				Integer old = community.get(label);
				community.put(label, Integer.valueOf(old == null ? 1 : old.intValue() + 1));
			}
		}
		int k = Math.max(1, cutoff.getValue());
		boolean followeeMode = RecommendationModeSupport.resolveCandidateType(targetMode(), algorithm())
				== RecommendationCandidateType.FOLLOWEE;
		int scorable = 0;
		int alone = 0;
		int unlabelled = 0;
		double candidateSum = 0.0;
		double relevantSum = 0.0;
		double precision = 0.0;
		double ndcg = 0.0;
		TreeMap<String,Integer> sampleCommunities = new TreeMap<String,Integer>();
		for (String target : chosen)
		{
			String label = labels.get(target);
			if (label == null || label.length() == 0)
			{
				unlabelled++;
				continue;
			}
			Integer old = sampleCommunities.get(label);
			sampleCommunities.put(label, Integer.valueOf(old == null ? 1 : old.intValue() + 1));
			int n = followeeMode ? community.size() : users.size() - 1;
			int m = followeeMode ? 1 : community.get(label).intValue() - 1;
			if (m <= 0 || n <= 0)
			{
				alone++;
				continue;
			}
			scorable++;
			candidateSum += n;
			relevantSum += m;
			double[] chance = RecommendationQualityEvaluator.randomExpectation(n, m, k);
			precision += chance[1];
			ndcg += chance[3];
		}
		targets.set(chosen.isEmpty() ? "—" : Integer.toString(chosen.size()),
				scorable + " can be scored");
		candidates.set(scorable == 0 ? "—" : NodeLabUi.number(candidateSum / scorable),
				followeeMode ? "followee accounts" : "other users");
		relevant.set(scorable == 0 ? "—" : NodeLabUi.number(relevantSum / scorable),
				followeeMode ? "the target's own account" : "users of the same community");
		chancePrecision.set(scorable == 0 ? "—" : SimulatorResultsPage.format(precision / scorable),
				"P@" + k + " of a random ranking");
		chanceNdcg.set(scorable == 0 ? "—" : SimulatorResultsPage.format(ndcg / scorable),
				"NDCG@" + k + " of a random ranking");
		if (gui.getTargetSelectionMode() == TargetSelection.Mode.SAMPLE)
		{
			StringBuilder text = new StringBuilder();
			text.append(chosen.size()).append(" targets from ").append(sampleCommunities.size())
					.append(" communities");
			int shown = 0;
			for (Map.Entry<String,Integer> entry : sampleCommunities.entrySet())
			{
				text.append(shown == 0 ? ": " : ", ").append(entry.getKey()).append(" ").append(entry.getValue());
				if (++shown == 8)
				{
					if (sampleCommunities.size() > 8)
					{
						text.append(", ...");
					}
					break;
				}
			}
			sampleText.setText(users.isEmpty() ? "Load users on the Data page first." : text.append('.').toString());
		}
		int algorithm = algorithm();
		if (RecommendationModeSupport.isClassifierAlgorithm(algorithm))
		{
			int labelled = 0;
			for (String user : users)
			{
				String label = labels.get(user);
				if (label != null && label.length() > 0)
				{
					labelled++;
				}
			}
			protocol.setText(algorithm == RecommendationModeSupport.SVM
					? "Training: one SMO model per target, trained on every other labelled user, so no target is"
							+ " ever learned from."
					: "Training: " + TargetFolds.describe(TargetFolds.of(chosen, labels, labelled), labelled) + ".");
		}
		else if (algorithm == RecommendationModeSupport.TEMPORAL_GRAPH_FOUNDATION)
		{
			protocol.setText("Training: the fusion weights are learned from labelled pairs of non-target users;"
					+ " the targets' own followees are never used.");
		}
		else
		{
			protocol.setText("Training: none. " + RecommendationModeSupport.algorithmName(algorithm)
					+ " does not learn from followees, so every user can be a target.");
		}
		warnings.removeAll();
		if (followeeMode && community.size() > 0 && k >= community.size())
		{
			warnings.add(new NodeLabUi.Chip("k = " + k + " covers all " + community.size()
					+ " accounts: hit rate and recall are 100% for any complete list. Use k < "
					+ community.size() + " or read NDCG and MRR.", NodeLabUi.Tone.WARNING, true));
		}
		if (alone > 0)
		{
			warnings.add(new NodeLabUi.Chip(alone + " target(s) are alone in their community and cannot be scored.",
					NodeLabUi.Tone.WARNING, true));
		}
		if (unlabelled > 0)
		{
			warnings.add(new NodeLabUi.Chip(unlabelled + " target(s) have no known followee and are not scored.",
					NodeLabUi.Tone.WARNING, true));
		}
		if (scorable > 0 && scorable < 10)
		{
			warnings.add(new NodeLabUi.Chip("Fewer than ten scored targets: intervals will be wide.",
					NodeLabUi.Tone.INFO, true));
		}
		HashSet<String> distinct = new HashSet<String>(labels.values());
		if (!users.isEmpty() && distinct.size() == 1)
		{
			warnings.add(new NodeLabUi.Chip("Every user follows the same account: every candidate is relevant.",
					NodeLabUi.Tone.WARNING, true));
		}
		warnings.revalidate();
		warnings.repaint();
	}
}
