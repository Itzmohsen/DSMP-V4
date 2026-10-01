package TwitterGatherDataFollowers.userRyersonU;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.border.Border;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.BasicSpinnerUI;

final class AlgorithmSettingsDialog extends JDialog
{
	private static final long serialVersionUID = 1L;

	private static final Color BACKGROUND = NebulaDesign.CANVAS_TOP;
	private static final Color SURFACE = NebulaDesign.SURFACE;
	private static final Color INK = NebulaDesign.TEXT_PRIMARY;
	private static final Color MUTED = NebulaDesign.TEXT_SECONDARY;
	private static final Color BLUE = NebulaDesign.ACCENT;
	private static final Color GREEN = NebulaDesign.SUCCESS;
	private static final Color BORDER = NebulaDesign.BORDER_SUBTLE;
	private static final int NOTE_WIDTH = 552;
	private static final String MLP_ENGINE_LEGACY_LABEL =
			"Neuroph MLP (V3, sigmoid outputs)";
	private static final String MLP_ENGINE_SPARSE_LABEL =
			"Softmax MLP (cross-entropy, early stopping)";
	private static final String[] CN_VARIANTS = {
			AlgorithmParameterSettings.CN_COMMON_NEIGHBOURS,
			AlgorithmParameterSettings.CN_JACCARD,
			AlgorithmParameterSettings.CN_ADAMIC_ADAR,
			AlgorithmParameterSettings.CN_RESOURCE_ALLOCATION};

	private final int algorithm;
	private final AlgorithmParameterSettings draft;
	private AlgorithmParameterSettings accepted;

	private JCheckBox customCheckBox;
	private JLabel profileBadge;
	private Paragraph summaryLabel;
	private JPanel parameterPanel;
	private DimPanel parameterGrid;
	private JSpinner similarityMultiplierSpinner;
	private JSpinner similarityMinimumSpinner;
	private JSpinner kMeansClustersSpinner;
	private JSpinner kMeansIterationsSpinner;
	private JSpinner kMeansSeedSpinner;
	private JSpinner svmCSpinner;
	private JCheckBox svmCalibrationBox;
	private JSpinner scalableEpochsSpinner;
	private JSpinner scalableAggressivenessSpinner;
	private JComboBox<String> mlpEngineCombo;
	private JComboBox<String> cnVariantCombo;
	private JSpinner mlpHiddenSpinner;
	private JSpinner mlpHiddenLayersSpinner;
	private JSpinner mlpLearningRateSpinner;
	private JSpinner mlpMaxErrorSpinner;
	private JSpinner mlpSparseEpochsSpinner;
	private JSpinner mlpSparseL2Spinner;
	private JSpinner mlpFedProxMuSpinner;
	private JSpinner doc2VecDmSpinner;
	private JSpinner doc2VecDbowWordsSpinner;
	private JSpinner doc2VecMinCountSpinner;
	private JSpinner doc2VecNegativeSpinner;
	private JSpinner doc2VecHsSpinner;
	private JSpinner doc2VecSampleSpinner;
	private JSpinner doc2VecWindowSpinner;
	private JSpinner doc2VecVectorSizeSpinner;
	private JSpinner doc2VecEpochsSpinner;

	static AlgorithmParameterSettings showDialog(
			Window owner,
			int algorithm,
			AlgorithmParameterSettings currentSettings)
	{
		AlgorithmSettingsDialog dialog = new AlgorithmSettingsDialog(
				owner,
				algorithm,
				currentSettings == null
						? AlgorithmParameterSettings.defaults()
						: currentSettings.copy());
		dialog.setVisible(true);
		return dialog.accepted == null ? null : dialog.accepted.copy();
	}

	private AlgorithmSettingsDialog(
			Window owner,
			int algorithm,
			AlgorithmParameterSettings currentSettings)
	{
		super(owner, "Advanced Algorithm Settings", ModalityType.APPLICATION_MODAL);
		this.algorithm = algorithm;
		this.draft = currentSettings;
		buildUi();
		setValuesFromSettings(draft);
		refreshState();
		pack();
		setMinimumSize(new Dimension(760, 650));
		setLocationRelativeTo(owner);
	}

	private void buildUi()
	{
		JPanel root = new JPanel(new BorderLayout(0, 0));
		root.setBackground(BACKGROUND);
		root.setBorder(BorderFactory.createEmptyBorder(20, 22, 20, 22));
		root.add(createHeader(), BorderLayout.NORTH);
		root.add(createBody(), BorderLayout.CENTER);
		root.add(createFooter(), BorderLayout.SOUTH);
		setContentPane(root);
	}

	private JComponent createHeader()
	{
		JPanel header = new JPanel(new BorderLayout(16, 0));
		header.setOpaque(false);
		header.setBorder(BorderFactory.createEmptyBorder(0, 0, 16, 0));

		GlyphBadge mark = new GlyphBadge(GlyphBadge.SLIDERS, 44);
		JPanel markHolder = new JPanel(new BorderLayout());
		markHolder.setOpaque(false);
		markHolder.add(mark, BorderLayout.NORTH);
		header.add(markHolder, BorderLayout.WEST);

		JPanel copy = new JPanel();
		copy.setOpaque(false);
		copy.setLayout(new BoxLayout(copy, BoxLayout.Y_AXIS));
		JLabel eyebrow = new JLabel("Algorithm tuning");
		NebulaWidgets.styleSectionLabel(eyebrow);
		eyebrow.setAlignmentX(Component.LEFT_ALIGNMENT);
		JLabel title = new JLabel("Advanced Algorithm Settings");
		title.setFont(NebulaDesign.fontSemiBold(20f));
		title.setForeground(INK);
		title.setAlignmentX(Component.LEFT_ALIGNMENT);
		JLabel subtitle = new JLabel(
				"Editing " + RecommendationModeSupport.algorithmName(algorithm)
				+ ". Defaults preserve the current recommender exactly.");
		subtitle.setFont(NebulaDesign.fontBody(12.5f));
		subtitle.setForeground(MUTED);
		subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
		copy.add(eyebrow);
		copy.add(Box.createRigidArea(new Dimension(0, 4)));
		copy.add(title);
		copy.add(Box.createRigidArea(new Dimension(0, 4)));
		copy.add(subtitle);
		header.add(copy, BorderLayout.CENTER);

		profileBadge = new PillLabel();
		profileBadge.setHorizontalAlignment(SwingConstants.CENTER);
		profileBadge.setFont(NebulaDesign.fontMedium(11.5f));
		profileBadge.setBorder(BorderFactory.createEmptyBorder(6, 14, 6, 14));
		JPanel badgeHolder = new JPanel(new BorderLayout());
		badgeHolder.setOpaque(false);
		badgeHolder.add(profileBadge, BorderLayout.NORTH);
		header.add(badgeHolder, BorderLayout.EAST);
		return header;
	}

	private JComponent createBody()
	{
		ScrollBody body = new ScrollBody();
		body.setOpaque(false);
		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));

		JPanel modeCard = card(new BorderLayout(14, 0));
		customCheckBox = new JCheckBox("Use custom parameters for this run profile");
		styleCheck(customCheckBox);
		customCheckBox.setFont(NebulaDesign.fontMedium(13f));
		customCheckBox.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent event)
			{
				draft.setCustomEnabled(customCheckBox.isSelected());
				refreshState();
			}
		});
		summaryLabel = new Paragraph(
				"", NebulaDesign.fontBody(12f), MUTED, NOTE_WIDTH);
		JPanel modeCopy = new JPanel();
		modeCopy.setOpaque(false);
		modeCopy.setLayout(new BoxLayout(modeCopy, BoxLayout.Y_AXIS));
		modeCopy.add(customCheckBox);
		modeCopy.add(Box.createRigidArea(new Dimension(0, 5)));
		modeCopy.add(summaryLabel);
		modeCard.add(modeCopy, BorderLayout.CENTER);
		capHeight(modeCard);
		body.add(modeCard);
		body.add(Box.createRigidArea(new Dimension(0, 12)));

		parameterPanel = card(new BorderLayout(0, 14));
		parameterPanel.add(sectionTitle(), BorderLayout.NORTH);
		DimPanel gridHolder = new DimPanel();
		gridHolder.add(parametersForAlgorithm(), BorderLayout.NORTH);
		parameterGrid = gridHolder;
		parameterPanel.add(gridHolder, BorderLayout.CENTER);
		capHeight(parameterPanel);
		body.add(parameterPanel);
		body.add(Box.createRigidArea(new Dimension(0, 12)));
		JComponent note = safetyNote();
		capHeight(note);
		body.add(note);

		JScrollPane scroll = new JScrollPane(body);
		scroll.setOpaque(false);
		scroll.getViewport().setOpaque(false);
		scroll.setBorder(BorderFactory.createEmptyBorder());
		scroll.setHorizontalScrollBarPolicy(
				JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.getVerticalScrollBar().setUI(new NebulaWidgets.PillScrollBarUI());
		scroll.getVerticalScrollBar().setOpaque(false);
		scroll.getVerticalScrollBar().setUnitIncrement(18);
		scroll.setPreferredSize(new Dimension(716, 430));
		return scroll;
	}

	/**
	 * Keeps a card at its natural height inside the vertical stack so the
	 * parameter card absorbs the leftover space instead of every card
	 * stretching into empty air.
	 */
	private void capHeight(JComponent card)
	{
		if (card instanceof CardPanel)
		{
			((CardPanel)card).setCapped(true);
		}
	}


	private JComponent sectionTitle()
	{
		JPanel titlePanel = new JPanel(new BorderLayout(10, 0));
		titlePanel.setOpaque(false);
		JLabel title = new JLabel(
				RecommendationModeSupport.algorithmName(algorithm)
				+ " tunable controls");
		title.setFont(NebulaDesign.fontSemiBold(14f));
		title.setForeground(INK);
		JLabel note = new JLabel("Validated before Initialize or Apply.");
		note.setFont(NebulaDesign.fontMedium(11f));
		note.setForeground(BLUE);
		titlePanel.add(title, BorderLayout.WEST);
		titlePanel.add(note, BorderLayout.EAST);
		return titlePanel;
	}

	private JComponent parametersForAlgorithm()
	{
		JPanel grid = new JPanel(new GridBagLayout());
		grid.setOpaque(false);
		int row = 0;
			switch (algorithm)
			{
				case RecommendationModeSupport.COS_SIM:
				case RecommendationModeSupport.COMMON_NEIGHBORS:
				case RecommendationModeSupport.TEMPORAL_GRAPH_FOUNDATION:
					if (algorithm == RecommendationModeSupport.COMMON_NEIGHBORS)
					{
						String[] labels = new String[CN_VARIANTS.length];
						for (int index = 0; index < CN_VARIANTS.length; index++)
						{
							labels[index] = AlgorithmParameterSettings.cnVariantLabel(CN_VARIANTS[index]);
						}
						cnVariantCombo = new JComboBox<String>(labels);
						styleCombo(cnVariantCombo);
						row = addRow(grid, row,
								"Link score",
								"How shared terms score a pair of users: their count (ties by rarity),"
										+ " Jaccard overlap, Adamic-Adar (1/ln degree) or resource allocation"
										+ " (1/degree).",
								cnVariantCombo);
					}
					similarityMultiplierSpinner = intSpinner(1, 100, 1);
					similarityMinimumSpinner = intSpinner(1, 10000, 1);
					row = addRow(grid, row,
							"Candidate buffer multiplier",
						"How many local candidates each node keeps before the final merge.",
						similarityMultiplierSpinner);
				addRow(grid, row,
						"Minimum local candidates",
						"Lower bound for each node's local similarity shortlist.",
						similarityMinimumSpinner);
				break;
			case RecommendationModeSupport.K_MEANS:
			case RecommendationModeSupport.K_MEANS_EUCLIDEAN:
				kMeansClustersSpinner = intSpinner(2, 200, 1);
				kMeansIterationsSpinner = intSpinner(1, 100, 1);
				kMeansSeedSpinner = intSpinner(0, 1000000, 1);
				row = addRow(grid, row,
						"Number of clusters",
						"Default is 3, matching the current K-Means logic.",
						kMeansClustersSpinner);
				row = addRow(grid, row,
						"Maximum iterations",
						"Stops clustering if assignments have not converged earlier.",
						kMeansIterationsSpinner);
				addRow(grid, row,
						"Cluster seed",
						"Picks the first centroid. The same seed always reproduces the same clustering.",
						kMeansSeedSpinner);
				break;
			case RecommendationModeSupport.SVM:
				svmCSpinner = doubleSpinner(0.001, 100.0, 0.01);
				svmCalibrationBox = new JCheckBox("Build calibrated models");
				styleCheck(svmCalibrationBox);
				row = addRow(grid, row,
						"Complexity C",
						"Exact Weka SMO regularization strength. Default is 0.1.",
						svmCSpinner);
				addRow(grid, row,
						"Probability calibration",
						"Default on, matching the current exact SVM score output.",
						svmCalibrationBox);
				break;
			case RecommendationModeSupport.SCALABLE_SVM:
				scalableEpochsSpinner = intSpinner(1, 200, 1);
				scalableAggressivenessSpinner = doubleSpinner(0.0001, 10.0, 0.01);
				row = addRow(grid, row,
						"Training epochs",
						"Passes over the shuffled training rows. The linear model is learned online"
								+ " with Passive-Aggressive PA-I updates on the hinge loss"
								+ " (Crammer et al., 2006), one-vs-rest.",
						scalableEpochsSpinner);
				addRow(grid, row,
						"Aggressiveness C",
						"PA-I's cap on each update (larger fits noise more). Default is 0.1.",
						scalableAggressivenessSpinner);
				break;
			case RecommendationModeSupport.MLP:
				mlpEngineCombo = new JComboBox<String>(new String[] {
						MLP_ENGINE_LEGACY_LABEL,
						MLP_ENGINE_SPARSE_LABEL
				});
				styleCombo(mlpEngineCombo);
				mlpHiddenSpinner = intSpinner(1, 512, 1);
				mlpHiddenLayersSpinner = intSpinner(1, 8, 1);
				mlpLearningRateSpinner = doubleSpinner(0.00001, 1.0, 0.01);
				mlpMaxErrorSpinner = doubleSpinner(0.000001, 0.5, 0.001);
				mlpSparseEpochsSpinner = intSpinner(1, 5000, 10);
				mlpSparseL2Spinner = doubleSpinner(0.0, 1.0, 0.0001);
				mlpFedProxMuSpinner = doubleSpinner(0.0, 10.0, 0.001);
				row = addRow(grid, row,
						"Execution engine",
						"Softmax MLP (default) trains with cross-entropy and stops early on a held-out"
								+ " validation split. Neuroph reproduces the V3 network.",
						mlpEngineCombo);
				row = addRow(grid, row,
						"Hidden neurons",
						"Neurons per hidden layer. Default is 10.",
						mlpHiddenSpinner);
				row = addRow(grid, row,
						"Hidden layers",
						"Default is 1. Use 5 to match the shared multi-layer MLP note.",
						mlpHiddenLayersSpinner);
				row = addRow(grid, row,
						"Learning rate",
						"Backpropagation learning rate.",
						mlpLearningRateSpinner);
				row = addRow(grid, row,
						"Max error (Neuroph)",
						"Neuroph's mean squared error target; it also stops at dsmp.mlp.maxEpochs"
								+ " (default 10000).",
						mlpMaxErrorSpinner);
				row = addRow(grid, row,
						"Epoch limit (Softmax)",
						"Upper bound on training passes. Training stops earlier when the validation"
								+ " loss has not improved for " + AlgorithmParameterSettings.MLP_EARLY_STOPPING_PATIENCE
								+ " epochs, and keeps the best epoch's weights.",
						mlpSparseEpochsSpinner);
				addRow(grid, row,
						"L2 (Softmax)",
						"Weight decay of the softmax engine; 0 disables it.",
						mlpSparseL2Spinner);
				break;
			case RecommendationModeSupport.DOC2VEC:
				doc2VecDmSpinner = intSpinner(0, 1, 1);
				doc2VecDbowWordsSpinner = intSpinner(0, 1, 1);
				doc2VecMinCountSpinner = intSpinner(1, 100, 1);
				doc2VecNegativeSpinner = intSpinner(1, 50, 1);
				doc2VecHsSpinner = intSpinner(0, 1, 1);
				doc2VecSampleSpinner = doubleSpinner(0.0, 0.1, 0.0001);
				doc2VecWindowSpinner = intSpinner(1, 50, 1);
				doc2VecVectorSizeSpinner = intSpinner(20, 1000, 10);
				doc2VecEpochsSpinner = intSpinner(1, 200, 1);
				row = addRow(grid, row, "Training mode dm",
						"0 = distributed bag of words, 1 = distributed memory.",
						doc2VecDmSpinner);
				row = addRow(grid, row, "DBOW words",
						"Whether DBOW also trains word vectors. Default is 1.",
						doc2VecDbowWordsSpinner);
				row = addRow(grid, row, "Minimum term count",
						"Terms below this corpus count are ignored.",
						doc2VecMinCountSpinner);
				row = addRow(grid, row, "Negative samples",
						"Negative-sampling examples per update.",
						doc2VecNegativeSpinner);
				row = addRow(grid, row, "Hierarchical softmax",
						"0 keeps current negative-sampling behavior.",
						doc2VecHsSpinner);
				row = addRow(grid, row, "Subsampling",
						"Frequent-word downsampling value.",
						doc2VecSampleSpinner);
				row = addRow(grid, row, "Window",
						"Context window size.",
						doc2VecWindowSpinner);
				row = addRow(grid, row, "Vector size",
						"Document-vector dimensions. Default is 300.",
						doc2VecVectorSizeSpinner);
				addRow(grid, row, "Epochs",
						"Training passes over the tagged documents.",
						doc2VecEpochsSpinner);
				break;
			default:
				JLabel unavailableTitle = new JLabel(
						"No safe active parameters are exposed");
				unavailableTitle.setFont(NebulaDesign.fontMedium(12.5f));
				unavailableTitle.setForeground(INK);
				grid.add(unavailableTitle, baseConstraints(0));
				Paragraph unavailable = new Paragraph(
						"This algorithm is inactive or unsupported in the current "
						+ "working recommender path.",
						NebulaDesign.fontBody(12f),
						MUTED,
						NOTE_WIDTH);
				grid.add(unavailable, baseConstraints(1));
				break;
		}
		return grid;
	}

	private JComponent safetyNote()
	{
		JPanel panel = card(new BorderLayout(14, 0));
		GlyphBadge icon = new GlyphBadge(GlyphBadge.CHECK, 34);
		JPanel iconHolder = new JPanel(new BorderLayout());
		iconHolder.setOpaque(false);
		iconHolder.add(icon, BorderLayout.NORTH);
		panel.add(iconHolder, BorderLayout.WEST);

		JPanel copy = new JPanel();
		copy.setOpaque(false);
		copy.setLayout(new BoxLayout(copy, BoxLayout.Y_AXIS));
		JLabel heading = new JLabel("Safety rule");
		heading.setFont(NebulaDesign.fontSemiBold(12.5f));
		heading.setForeground(INK);
		heading.setAlignmentX(Component.LEFT_ALIGNMENT);
		Paragraph label = new Paragraph(
				"Defaults are the baseline. Custom values are range-checked before "
				+ "they reach Java workers or Python Doc2Vec, and Doc2Vec cache keys "
				+ "include these values so a changed model cannot reuse the wrong cache.",
				NebulaDesign.fontBody(12f),
				MUTED,
				NOTE_WIDTH);
		copy.add(heading);
		copy.add(Box.createRigidArea(new Dimension(0, 3)));
		copy.add(label);
		panel.add(copy, BorderLayout.CENTER);
		return panel;
	}

	private JComponent createFooter()
	{
		JPanel footer = new JPanel(new BorderLayout(12, 0));
		footer.setOpaque(false);
		footer.setBorder(BorderFactory.createEmptyBorder(16, 0, 0, 0));
		JPanel rule = new JPanel();
		rule.setBackground(BORDER);
		rule.setPreferredSize(new Dimension(10, 1));
		footer.add(rule, BorderLayout.NORTH);

		JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 12));
		actions.setOpaque(false);
		JButton reset = button("Reset to defaults", false);
		reset.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent event)
			{
				draft.resetToDefaults();
				setValuesFromSettings(draft);
				refreshState();
			}
		});
		JButton cancel = button("Cancel", false);
		cancel.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent event)
			{
				accepted = null;
				dispose();
			}
		});
		JButton apply = button("Apply settings", true);
		apply.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent event)
			{
				applySettings();
			}
		});
		actions.add(reset);
		actions.add(cancel);
		actions.add(apply);
		footer.add(actions, BorderLayout.CENTER);
		return footer;
	}

	private JButton button(String text, boolean primary)
	{
		NebulaButton button = new NebulaButton(text, primary);
		NebulaWidgets.polishButton(button);
		return button;
	}

	private JPanel card(java.awt.LayoutManager layout)
	{
		JPanel panel = new CardPanel(layout);
		panel.setBorder(BorderFactory.createEmptyBorder(14, 16, 16, 16));
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);
		return panel;
	}

	private int addRow(
			JPanel grid,
			int row,
			String title,
			String detail,
			JComponent control)
	{
		GridBagConstraints labelConstraints = new GridBagConstraints();
		labelConstraints.gridx = 0;
		labelConstraints.gridy = row;
		labelConstraints.weightx = 1.0;
		labelConstraints.fill = GridBagConstraints.HORIZONTAL;
		labelConstraints.insets = new Insets(row == 0 ? 0 : 10, 0, 0, 16);
		JPanel copy = new JPanel();
		copy.setOpaque(false);
		copy.setLayout(new BoxLayout(copy, BoxLayout.Y_AXIS));
		JLabel titleLabel = new JLabel(title);
		titleLabel.setFont(NebulaDesign.fontMedium(12.5f));
		titleLabel.setForeground(INK);
		JLabel detailLabel = new JLabel(detail);
		detailLabel.setFont(NebulaDesign.fontBody(11f));
		detailLabel.setForeground(NebulaDesign.TEXT_TERTIARY);
		copy.add(titleLabel);
		copy.add(Box.createRigidArea(new Dimension(0, 2)));
		copy.add(detailLabel);
		grid.add(copy, labelConstraints);

		GridBagConstraints controlConstraints = new GridBagConstraints();
		controlConstraints.gridx = 1;
		controlConstraints.gridy = row;
		controlConstraints.weightx = 0.0;
		controlConstraints.fill = GridBagConstraints.HORIZONTAL;
		controlConstraints.insets = new Insets(row == 0 ? 0 : 10, 0, 0, 0);
		control.setPreferredSize(new Dimension(190, 34));
		grid.add(control, controlConstraints);
		return row + 1;
	}

	private GridBagConstraints baseConstraints(int row)
	{
		GridBagConstraints constraints = new GridBagConstraints();
		constraints.gridx = 0;
		constraints.gridy = row;
		constraints.gridwidth = 2;
		constraints.weightx = 1.0;
		constraints.fill = GridBagConstraints.HORIZONTAL;
		constraints.insets = new Insets(2, 0, 2, 0);
		return constraints;
	}

	private JSpinner intSpinner(int minimum, int maximum, int step)
	{
		JSpinner spinner = new JSpinner(
				new SpinnerNumberModel(minimum, minimum, maximum, step));
		styleSpinner(spinner);
		return spinner;
	}

	private JSpinner doubleSpinner(double minimum, double maximum, double step)
	{
		JSpinner spinner = new JSpinner(
				new SpinnerNumberModel(minimum, minimum, maximum, step));
		styleSpinner(spinner);
		return spinner;
	}

	private void styleSpinner(JSpinner spinner)
	{
		spinner.setUI(new NebulaSpinnerUI());
		spinner.setFont(NebulaDesign.fontMono(12f));
		spinner.setOpaque(false);
		spinner.setBorder(new FieldBorder());
		JComponent editor = spinner.getEditor();
		if (editor instanceof JSpinner.DefaultEditor)
		{
			editor.setOpaque(false);
			javax.swing.JTextField field =
					((JSpinner.DefaultEditor)editor).getTextField();
			field.setHorizontalAlignment(SwingConstants.CENTER);
			field.setFont(NebulaDesign.fontMono(12f));
			field.setOpaque(false);
			field.setForeground(INK);
			field.setDisabledTextColor(NebulaDesign.TEXT_TERTIARY);
			field.setCaretColor(NebulaDesign.ACCENT);
			field.setSelectionColor(NebulaDesign.SELECTION);
			field.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 2));
		}
	}

	private void styleCheck(JCheckBox checkBox)
	{
		checkBox.setOpaque(false);
		checkBox.setForeground(INK);
		checkBox.setFont(NebulaDesign.fontMedium(12f));
		checkBox.setIcon(new CheckBoxIcon());
		checkBox.setIconTextGap(9);
		NebulaWidgets.polishButton(checkBox);
	}

	private void styleCombo(JComboBox<String> comboBox)
	{
		comboBox.setUI(new NebulaComboUI());
		comboBox.setFont(NebulaDesign.fontMedium(12f));
		comboBox.setForeground(INK);
		comboBox.setOpaque(false);
		comboBox.setRenderer(new ComboItemRenderer());
		comboBox.setBorder(BorderFactory.createEmptyBorder());
	}

	private void setValuesFromSettings(AlgorithmParameterSettings settings)
	{
		customCheckBox.setSelected(settings.isCustomEnabled());
		if (cnVariantCombo != null)
		{
			String variant = settings.getCommonNeighborsVariant();
			for (int index = 0; index < CN_VARIANTS.length; index++)
			{
				if (CN_VARIANTS[index].equals(variant))
				{
					cnVariantCombo.setSelectedIndex(index);
				}
			}
		}
		if (similarityMultiplierSpinner != null)
		{
			similarityMultiplierSpinner.setValue(
					settings.getSimilarityLocalTopMultiplier());
			similarityMinimumSpinner.setValue(
					settings.getSimilarityLocalTopMinimum());
		}
		if (kMeansClustersSpinner != null)
		{
			kMeansClustersSpinner.setValue(settings.getKMeansClusters());
			kMeansIterationsSpinner.setValue(settings.getKMeansMaxIterations());
			kMeansSeedSpinner.setValue(Integer.valueOf(
					(int)Math.min(1000000L, Math.max(0L, settings.getKMeansSeed()))));
		}
		if (svmCSpinner != null)
		{
			svmCSpinner.setValue(settings.getSvmC());
			svmCalibrationBox.setSelected(settings.isSvmCalibrationEnabled());
		}
		if (scalableEpochsSpinner != null)
		{
			scalableEpochsSpinner.setValue(settings.getScalableSvmEpochs());
			scalableAggressivenessSpinner.setValue(
					settings.getScalableSvmAggressiveness());
		}
		if (mlpHiddenSpinner != null)
		{
			mlpEngineCombo.setSelectedItem(
					AlgorithmParameterSettings.MLP_ENGINE_SPARSE_FEDERATED.equals(
							settings.getMlpEngine())
							? MLP_ENGINE_SPARSE_LABEL
							: MLP_ENGINE_LEGACY_LABEL);
			mlpHiddenSpinner.setValue(settings.getMlpHiddenNeurons());
			mlpHiddenLayersSpinner.setValue(settings.getMlpHiddenLayers());
			mlpLearningRateSpinner.setValue(settings.getMlpLearningRate());
			mlpMaxErrorSpinner.setValue(settings.getMlpMaxError());
			mlpSparseEpochsSpinner.setValue(settings.getMlpSparseEpochs());
			mlpSparseL2Spinner.setValue(settings.getMlpSparseL2());
			mlpFedProxMuSpinner.setValue(settings.getMlpFedProxMu());
		}
		if (doc2VecDmSpinner != null)
		{
			doc2VecDmSpinner.setValue(settings.getDoc2VecDm());
			doc2VecDbowWordsSpinner.setValue(settings.getDoc2VecDbowWords());
			doc2VecMinCountSpinner.setValue(settings.getDoc2VecMinCount());
			doc2VecNegativeSpinner.setValue(settings.getDoc2VecNegative());
			doc2VecHsSpinner.setValue(settings.getDoc2VecHs());
			doc2VecSampleSpinner.setValue(settings.getDoc2VecSample());
			doc2VecWindowSpinner.setValue(settings.getDoc2VecWindow());
			doc2VecVectorSizeSpinner.setValue(settings.getDoc2VecVectorSize());
			doc2VecEpochsSpinner.setValue(settings.getDoc2VecEpochs());
		}
	}

	private void refreshState()
	{
		boolean custom = customCheckBox.isSelected();
		profileBadge.setText(custom ? "Custom profile" : "Default profile");
		profileBadge.setForeground(custom
				? NebulaDesign.WARNING : NebulaDesign.SUCCESS);
		profileBadge.setBackground(custom
				? NebulaDesign.WARNING_SOFT : NebulaDesign.SUCCESS_SOFT);
		summaryLabel.setText(custom
				? draft.summaryForAlgorithm(algorithm)
				: "Using the exact parameters currently built into the program.");
		setChildrenEnabled(parameterGrid, custom);
		if (parameterGrid != null)
		{
			parameterGrid.setDimmed(!custom);
		}
		customCheckBox.setEnabled(true);
		revalidate();
		repaint();
	}

	private void setChildrenEnabled(Component component, boolean enabled)
	{
		if (component != customCheckBox)
		{
			component.setEnabled(enabled);
		}
		if (component instanceof java.awt.Container)
		{
			Component[] children = ((java.awt.Container)component).getComponents();
			for (int index = 0; index < children.length; index++)
			{
				setChildrenEnabled(children[index], enabled);
			}
		}
	}

	private void applySettings()
	{
		draft.setCustomEnabled(customCheckBox.isSelected());
		readVisibleControlsIntoSettings(draft);
		List<String> errors = draft.validateForAlgorithm(algorithm);
		if (!errors.isEmpty())
		{
			NebulaDialogs.showWarning(
					this,
					formatErrors(errors),
					"Invalid Algorithm Parameters");
			return;
		}
		accepted = draft.copy();
		dispose();
	}

	private String formatErrors(List<String> errors)
	{
		StringBuilder builder = new StringBuilder("Fix these settings:");
		for (String error : errors)
		{
			builder.append("\n• ").append(error);
		}
		return builder.toString();
	}

	private void readVisibleControlsIntoSettings(
			AlgorithmParameterSettings settings)
	{
		if (cnVariantCombo != null && cnVariantCombo.getSelectedIndex() >= 0)
		{
			settings.setCommonNeighborsVariant(CN_VARIANTS[cnVariantCombo.getSelectedIndex()]);
		}
		if (similarityMultiplierSpinner != null)
		{
			settings.setSimilarityLocalTopMultiplier(
					intValue(similarityMultiplierSpinner));
			settings.setSimilarityLocalTopMinimum(
					intValue(similarityMinimumSpinner));
		}
		if (kMeansClustersSpinner != null)
		{
			settings.setKMeansClusters(intValue(kMeansClustersSpinner));
			settings.setKMeansMaxIterations(intValue(kMeansIterationsSpinner));
			settings.setKMeansSeed(intValue(kMeansSeedSpinner));
		}
		if (svmCSpinner != null)
		{
			settings.setSvmC(doubleValue(svmCSpinner));
			settings.setSvmCalibration(svmCalibrationBox.isSelected());
		}
		if (scalableEpochsSpinner != null)
		{
			settings.setScalableSvmEpochs(intValue(scalableEpochsSpinner));
			settings.setScalableSvmAggressiveness(
					doubleValue(scalableAggressivenessSpinner));
		}
		if (mlpHiddenSpinner != null)
		{
			settings.setMlpEngine(MLP_ENGINE_SPARSE_LABEL.equals(
					mlpEngineCombo.getSelectedItem())
					? AlgorithmParameterSettings.MLP_ENGINE_SPARSE_FEDERATED
					: AlgorithmParameterSettings.MLP_ENGINE_LEGACY_NEUROPH);
			settings.setMlpHiddenNeurons(intValue(mlpHiddenSpinner));
			settings.setMlpHiddenLayers(intValue(mlpHiddenLayersSpinner));
			settings.setMlpLearningRate(doubleValue(mlpLearningRateSpinner));
			settings.setMlpMaxError(doubleValue(mlpMaxErrorSpinner));
			settings.setMlpSparseEpochs(intValue(mlpSparseEpochsSpinner));
			settings.setMlpSparseL2(doubleValue(mlpSparseL2Spinner));
			settings.setMlpFedProxMu(doubleValue(mlpFedProxMuSpinner));
		}
		if (doc2VecDmSpinner != null)
		{
			settings.setDoc2VecDm(intValue(doc2VecDmSpinner));
			settings.setDoc2VecDbowWords(intValue(doc2VecDbowWordsSpinner));
			settings.setDoc2VecMinCount(intValue(doc2VecMinCountSpinner));
			settings.setDoc2VecNegative(intValue(doc2VecNegativeSpinner));
			settings.setDoc2VecHs(intValue(doc2VecHsSpinner));
			settings.setDoc2VecSample(doubleValue(doc2VecSampleSpinner));
			settings.setDoc2VecWindow(intValue(doc2VecWindowSpinner));
			settings.setDoc2VecVectorSize(intValue(doc2VecVectorSizeSpinner));
			settings.setDoc2VecEpochs(intValue(doc2VecEpochsSpinner));
		}
	}

	private int intValue(JSpinner spinner)
	{
		return ((Number)spinner.getValue()).intValue();
	}

	private double doubleValue(JSpinner spinner)
	{
		return ((Number)spinner.getValue()).doubleValue();
	}

	/** Rounded Nebula card: soft shadow in day mode, lifted surface at night. */
	private static final class CardPanel extends JPanel
	{
		private static final long serialVersionUID = 1L;
		private boolean capped;

		CardPanel(java.awt.LayoutManager layout)
		{
			super(layout);
			setOpaque(false);
		}

		void setCapped(boolean capped)
		{
			this.capped = capped;
		}

		public Dimension getMaximumSize()
		{
			if (capped)
			{
				return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
			}
			return super.getMaximumSize();
		}

		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g2 = (Graphics2D)graphics.create();
			NebulaWidgets.paintCard(g2, 0, 0, getWidth(), getHeight(),
					NebulaDesign.RADIUS_CARD);
			g2.dispose();
			super.paintComponent(graphics);
		}
	}

	/**
	 * Word-wrapped body copy. Swing's HTML labels measure and draw with
	 * different metrics under the bundled Inter faces, which clipped the last
	 * word of every wrapped line; this paints the wrap itself so the measure
	 * and the drawing always agree.
	 */
	private static final class Paragraph extends JComponent
	{
		private static final long serialVersionUID = 1L;
		private final int measure;
		private String text;

		Paragraph(String text, Font font, Color color, int measure)
		{
			this.text = text == null ? "" : text;
			this.measure = measure;
			setFont(font);
			setForeground(color);
			setOpaque(false);
			setAlignmentX(Component.LEFT_ALIGNMENT);
		}

		void setText(String value)
		{
			text = value == null ? "" : value;
			revalidate();
			repaint();
		}

		public Dimension getPreferredSize()
		{
			java.awt.FontMetrics metrics = getFontMetrics(getFont());
			int lines = layoutLines(metrics, measure, null);
			return new Dimension(measure, Math.max(1, lines) * metrics.getHeight());
		}

		public Dimension getMinimumSize()
		{
			return getPreferredSize();
		}

		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g2 = (Graphics2D)graphics.create();
			NebulaDesign.prepare(g2);
			g2.setFont(getFont());
			g2.setColor(getForeground());
			layoutLines(g2.getFontMetrics(), Math.max(1, getWidth()), g2);
			g2.dispose();
		}

		private int layoutLines(
				java.awt.FontMetrics metrics,
				int width,
				Graphics2D g2)
		{
			int lines = 0;
			int baseline = metrics.getAscent();
			int start = 0;
			while (start < text.length())
			{
				int end = start;
				int lastBreak = -1;
				while (end < text.length())
				{
					if (text.charAt(end) == ' ')
					{
						if (metrics.stringWidth(text.substring(start, end)) > width)
						{
							break;
						}
						lastBreak = end;
					}
					end++;
				}
				int stop;
				if (end >= text.length()
						&& metrics.stringWidth(text.substring(start)) <= width)
				{
					stop = text.length();
				}
				else if (lastBreak > start)
				{
					stop = lastBreak;
				}
				else
				{
					stop = Math.min(text.length(), start + 1);
					while (stop < text.length()
							&& metrics.stringWidth(text.substring(start, stop + 1)) <= width)
					{
						stop++;
					}
				}
				if (g2 != null)
				{
					g2.drawString(text.substring(start, stop).trim(), 0, baseline);
					baseline += metrics.getHeight();
				}
				lines++;
				start = stop;
				while (start < text.length() && text.charAt(start) == ' ')
				{
					start++;
				}
			}
			return lines;
		}
	}

	/** Parameter well that ghosts its controls while the default profile is on. */
	private static final class DimPanel extends JPanel
	{
		private static final long serialVersionUID = 1L;
		private boolean dimmed;

		DimPanel()
		{
			super(new BorderLayout());
			setOpaque(false);
		}

		void setDimmed(boolean dimmed)
		{
			this.dimmed = dimmed;
			repaint();
		}

		protected void paintChildren(Graphics graphics)
		{
			super.paintChildren(graphics);
			if (!dimmed)
			{
				return;
			}
			Graphics2D g2 = (Graphics2D)graphics.create();
			NebulaDesign.prepare(g2);
			g2.setColor(NebulaDesign.withAlpha(
					NebulaDesign.night()
							? NebulaDesign.NIGHT_SURFACE : NebulaDesign.DAY_SURFACE,
					122));
			g2.fillRect(0, 0, getWidth(), getHeight());
			g2.dispose();
		}
	}

	/** Body host that keeps the card stack at the viewport width. */
	private static final class ScrollBody extends JPanel
			implements javax.swing.Scrollable
	{
		private static final long serialVersionUID = 1L;

		public Dimension getPreferredScrollableViewportSize()
		{
			return getPreferredSize();
		}

		public int getScrollableUnitIncrement(
				java.awt.Rectangle visible,
				int orientation,
				int direction)
		{
			return 18;
		}

		public int getScrollableBlockIncrement(
				java.awt.Rectangle visible,
				int orientation,
				int direction)
		{
			return Math.max(36, visible.height - 36);
		}

		public boolean getScrollableTracksViewportWidth()
		{
			return true;
		}

		public boolean getScrollableTracksViewportHeight()
		{
			return false;
		}
	}

	/** Rounded field chrome: fills the control and strokes its outline. */
	private static final class FieldBorder implements Border
	{
		public void paintBorder(
				Component component,
				Graphics graphics,
				int x,
				int y,
				int width,
				int height)
		{
			Graphics2D g2 = (Graphics2D)graphics.create();
			NebulaDesign.prepare(g2);
			boolean enabled = component.isEnabled();
			g2.setColor(enabled ? NebulaDesign.SURFACE_ALT : NebulaDesign.INSET);
			g2.fillRoundRect(x, y, width - 1, height - 1,
					NebulaDesign.RADIUS_CONTROL, NebulaDesign.RADIUS_CONTROL);
			g2.setColor(enabled
					? NebulaDesign.BORDER_STRONG : NebulaDesign.BORDER_SUBTLE);
			g2.drawRoundRect(x, y, width - 1, height - 1,
					NebulaDesign.RADIUS_CONTROL, NebulaDesign.RADIUS_CONTROL);
			g2.dispose();
		}

		public Insets getBorderInsets(Component component)
		{
			return new Insets(1, 2, 1, 2);
		}

		public boolean isBorderOpaque()
		{
			return false;
		}
	}

	/** 16px custom checkbox: rounded well, accent fill and white tick when on. */
	private static final class CheckBoxIcon implements Icon
	{
		public void paintIcon(Component component, Graphics graphics, int x, int y)
		{
			Graphics2D g2 = (Graphics2D)graphics.create();
			NebulaDesign.prepare(g2);
			AbstractButton button = (AbstractButton)component;
			boolean on = button.isSelected();
			boolean enabled = button.isEnabled();
			int size = getIconWidth();
			if (on && enabled)
			{
				g2.setColor(NebulaDesign.ACCENT);
				g2.fillRoundRect(x, y, size, size, 5, 5);
			}
			else
			{
				g2.setColor(enabled ? NebulaDesign.SURFACE_ALT : NebulaDesign.INSET);
				g2.fillRoundRect(x, y, size, size, 5, 5);
				g2.setColor(enabled
						? NebulaDesign.BORDER_STRONG : NebulaDesign.BORDER_SUBTLE);
				g2.drawRoundRect(x, y, size - 1, size - 1, 5, 5);
			}
			if (on)
			{
				g2.setColor(enabled
						? NebulaDesign.TEXT_ON_ACCENT : NebulaDesign.TEXT_TERTIARY);
				g2.setStroke(new BasicStroke(2f,
						BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
				g2.drawLine(x + 4, y + 8, x + 7, y + 11);
				g2.drawLine(x + 7, y + 11, x + 12, y + 5);
			}
			g2.dispose();
		}

		public int getIconWidth()
		{
			return 16;
		}

		public int getIconHeight()
		{
			return 16;
		}
	}

	/** Spinner delegate with chevron steppers instead of bevelled arrows. */
	private static final class NebulaSpinnerUI extends BasicSpinnerUI
	{
		protected Component createNextButton()
		{
			JButton button = stepper(true);
			installNextButtonListeners(button);
			return button;
		}

		protected Component createPreviousButton()
		{
			JButton button = stepper(false);
			installPreviousButtonListeners(button);
			return button;
		}

		private static JButton stepper(final boolean up)
		{
			JButton button = new JButton()
			{
				private static final long serialVersionUID = 1L;

				protected void paintComponent(Graphics graphics)
				{
					Graphics2D g2 = (Graphics2D)graphics.create();
					NebulaDesign.prepare(g2);
					Color tone = isEnabled()
							? NebulaDesign.TEXT_SECONDARY : NebulaDesign.TEXT_TERTIARY;
					if (getModel().isRollover() && isEnabled())
					{
						tone = NebulaDesign.ACCENT;
					}
					g2.setColor(tone);
					g2.setStroke(new BasicStroke(1.6f,
							BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
					int cx = getWidth() / 2;
					int cy = getHeight() / 2;
					if (up)
					{
						g2.drawLine(cx - 4, cy + 2, cx, cy - 2);
						g2.drawLine(cx, cy - 2, cx + 4, cy + 2);
					}
					else
					{
						g2.drawLine(cx - 4, cy - 2, cx, cy + 2);
						g2.drawLine(cx, cy + 2, cx + 4, cy - 2);
					}
					g2.dispose();
				}
			};
			button.setOpaque(false);
			button.setContentAreaFilled(false);
			button.setBorderPainted(false);
			button.setFocusable(false);
			button.setRolloverEnabled(true);
			button.setPreferredSize(new Dimension(22, 13));
			return button;
		}
	}

	/** Combo delegate: rounded field, chevron, no bevelled arrow button. */
	private static final class NebulaComboUI extends BasicComboBoxUI
	{
		protected JButton createArrowButton()
		{
			JButton button = new JButton()
			{
				private static final long serialVersionUID = 1L;

				protected void paintComponent(Graphics graphics)
				{
					Graphics2D g2 = (Graphics2D)graphics.create();
					NebulaWidgets.paintChevron(g2, getWidth() / 2, getHeight() / 2 - 1, 9,
							isEnabled()
									? NebulaDesign.TEXT_SECONDARY
									: NebulaDesign.TEXT_TERTIARY);
					g2.dispose();
				}
			};
			button.setOpaque(false);
			button.setContentAreaFilled(false);
			button.setBorderPainted(false);
			button.setFocusable(false);
			button.setPreferredSize(new Dimension(26, 26));
			return button;
		}

		public void paint(Graphics graphics, JComponent component)
		{
			Graphics2D g2 = (Graphics2D)graphics.create();
			NebulaDesign.prepare(g2);
			boolean enabled = component.isEnabled();
			g2.setColor(enabled ? NebulaDesign.SURFACE_ALT : NebulaDesign.INSET);
			g2.fillRoundRect(0, 0, component.getWidth() - 1, component.getHeight() - 1,
					NebulaDesign.RADIUS_CONTROL, NebulaDesign.RADIUS_CONTROL);
			g2.setColor(enabled
					? NebulaDesign.BORDER_STRONG : NebulaDesign.BORDER_SUBTLE);
			g2.drawRoundRect(0, 0, component.getWidth() - 1, component.getHeight() - 1,
					NebulaDesign.RADIUS_CONTROL, NebulaDesign.RADIUS_CONTROL);
			g2.dispose();
			super.paint(graphics, component);
		}

		public void paintCurrentValueBackground(
				Graphics graphics,
				java.awt.Rectangle bounds,
				boolean hasFocus)
		{
			// The rounded field fill is painted once in paint().
		}
	}

	/** Popup + closed-state renderer for Nebula combo boxes. */
	private static final class ComboItemRenderer extends DefaultListCellRenderer
	{
		private static final long serialVersionUID = 1L;

		public Component getListCellRendererComponent(
				JList<?> list,
				Object value,
				int index,
				boolean isSelected,
				boolean cellHasFocus)
		{
			JLabel label = (JLabel)super.getListCellRendererComponent(
					list, value, index, isSelected, cellHasFocus);
			label.setFont(NebulaDesign.fontMedium(12f));
			if (index < 0)
			{
				label.setOpaque(false);
				label.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 4));
				label.setForeground(NebulaDesign.TEXT_PRIMARY);
			}
			else
			{
				label.setOpaque(true);
				label.setBorder(BorderFactory.createEmptyBorder(7, 12, 7, 12));
				label.setBackground(isSelected
						? NebulaDesign.ACCENT_SOFT : NebulaDesign.SURFACE);
				label.setForeground(isSelected
						? NebulaDesign.ACCENT : NebulaDesign.TEXT_PRIMARY);
			}
			return label;
		}
	}

	/** Status pill label: soft tint capsule behind strong hue text. */
	private static final class PillLabel extends JLabel
	{
		private static final long serialVersionUID = 1L;

		PillLabel()
		{
			setOpaque(false);
		}

		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g2 = (Graphics2D)graphics.create();
			NebulaDesign.prepare(g2);
			Color background = getBackground();
			if (background != null)
			{
				g2.setColor(background);
				g2.fillRoundRect(0, 0, getWidth(), getHeight(),
						getHeight(), getHeight());
			}
			g2.dispose();
			super.paintComponent(graphics);
		}
	}

	/** Soft-tint icon square with a vector glyph (sliders or check). */
	private static final class GlyphBadge extends JComponent
	{
		static final int SLIDERS = 0;
		static final int CHECK = 1;
		private static final long serialVersionUID = 1L;
		private final int glyph;

		GlyphBadge(int glyph, int size)
		{
			this.glyph = glyph;
			Dimension dimension = new Dimension(size, size);
			setPreferredSize(dimension);
			setMinimumSize(dimension);
			setMaximumSize(dimension);
		}

		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g2 = (Graphics2D)graphics.create();
			NebulaDesign.prepare(g2);
			int size = Math.min(getWidth(), getHeight());
			Color hue = glyph == CHECK
					? NebulaDesign.SUCCESS : NebulaDesign.ACCENT;
			Color soft = glyph == CHECK
					? NebulaDesign.SUCCESS_SOFT : NebulaDesign.ACCENT_SOFT;
			g2.setColor(soft);
			g2.fillRoundRect(0, 0, size, size, 12, 12);
			g2.setColor(hue);
			g2.setStroke(new BasicStroke(2f,
					BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			int cx = size / 2;
			int cy = size / 2;
			if (glyph == CHECK)
			{
				g2.drawLine(cx - 6, cy, cx - 2, cy + 5);
				g2.drawLine(cx - 2, cy + 5, cx + 6, cy - 5);
			}
			else
			{
				int inset = Math.round(size * 0.27f);
				int gap = Math.max(5, Math.round(size * 0.16f));
				int[] rows = {cy - gap, cy, cy + gap};
				int[] knobs = {size - inset - 3, inset + 3, cx + 2};
				for (int i = 0; i < rows.length; i++)
				{
					g2.drawLine(inset, rows[i], size - inset, rows[i]);
					g2.fillOval(knobs[i] - 3, rows[i] - 3, 7, 7);
				}
			}
			g2.dispose();
		}
	}

	/** Nebula button: primary accent fill or neutral outline, painted per mode. */
	private static final class NebulaButton extends JButton
	{
		private static final long serialVersionUID = 1L;
		private final boolean primary;

		NebulaButton(String text, boolean primary)
		{
			super(text);
			this.primary = primary;
			setFont(NebulaDesign.fontMedium(12.5f));
			setFocusPainted(false);
			setRolloverEnabled(true);
			setOpaque(false);
			setContentAreaFilled(false);
			setBorderPainted(false);
			setBorder(BorderFactory.createEmptyBorder(9, 18, 9, 18));
		}

		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g2 = (Graphics2D)graphics.create();
			NebulaDesign.prepare(g2);
			int width = getWidth();
			int height = getHeight();
			boolean hover = getModel().isRollover();
			boolean pressed = getModel().isPressed();
			Color fill;
			Color text;
			Color outline = null;
			if (!isEnabled())
			{
				fill = NebulaDesign.SURFACE_ALT;
				text = NebulaDesign.TEXT_TERTIARY;
				outline = NebulaDesign.BORDER_SUBTLE;
			}
			else if (primary)
			{
				fill = pressed ? NebulaDesign.ACCENT_PRESS
						: hover ? NebulaDesign.ACCENT_HOVER : NebulaDesign.ACCENT;
				text = NebulaDesign.TEXT_ON_ACCENT;
			}
			else
			{
				fill = hover || pressed
						? NebulaDesign.SURFACE_RAISED : NebulaDesign.SURFACE;
				text = NebulaDesign.TEXT_PRIMARY;
				outline = hover
						? NebulaDesign.withAlpha(NebulaDesign.currentAccent(), 110)
						: NebulaDesign.BORDER_STRONG;
			}
			g2.setColor(fill);
			g2.fillRoundRect(0, 0, width - 1, height - 1,
					NebulaDesign.RADIUS_CONTROL, NebulaDesign.RADIUS_CONTROL);
			if (outline != null)
			{
				g2.setColor(outline);
				g2.drawRoundRect(0, 0, width - 1, height - 1,
						NebulaDesign.RADIUS_CONTROL, NebulaDesign.RADIUS_CONTROL);
			}
			g2.setFont(getFont());
			g2.setColor(text);
			java.awt.FontMetrics metrics = g2.getFontMetrics();
			int textX = (width - metrics.stringWidth(getText())) / 2;
			int textY = (height - metrics.getHeight()) / 2 + metrics.getAscent();
			g2.drawString(getText(), textX, textY);
			g2.dispose();
		}
	}
}
