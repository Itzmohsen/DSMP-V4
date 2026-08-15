package TwitterGatherDataFollowers.userRyersonU;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.border.Border;

final class AlgorithmSettingsDialog extends JDialog
{
	private static final long serialVersionUID = 1L;

	private static final Color BACKGROUND = new Color(246, 248, 252);
	private static final Color SURFACE = Color.WHITE;
	private static final Color INK = new Color(25, 31, 39);
	private static final Color MUTED = new Color(88, 101, 118);
	private static final Color BLUE = new Color(43, 107, 230);
	private static final Color GREEN = new Color(31, 139, 91);
	private static final Color AMBER = new Color(174, 102, 18);
	private static final Color BORDER = new Color(209, 219, 232);
	private static final String MLP_ENGINE_LEGACY_LABEL =
			"Legacy Neuroph MLP (current default)";
	private static final String MLP_ENGINE_SPARSE_LABEL =
			"Sparse Federated MLP (experimental)";

	private final int algorithm;
	private final AlgorithmParameterSettings draft;
	private AlgorithmParameterSettings accepted;

	private JCheckBox customCheckBox;
	private JLabel profileBadge;
	private JLabel summaryLabel;
	private JPanel parameterPanel;
	private JSpinner similarityMultiplierSpinner;
	private JSpinner similarityMinimumSpinner;
	private JSpinner kMeansClustersSpinner;
	private JSpinner kMeansIterationsSpinner;
	private JSpinner svmCSpinner;
	private JCheckBox svmCalibrationBox;
	private JSpinner scalableEpochsSpinner;
	private JSpinner scalableAggressivenessSpinner;
	private JComboBox<String> mlpEngineCombo;
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

		JLabel mark = new JLabel("\u2699");
		mark.setOpaque(true);
		mark.setHorizontalAlignment(SwingConstants.CENTER);
		mark.setForeground(Color.WHITE);
		mark.setBackground(BLUE);
		mark.setFont(font(Font.BOLD, 25));
		mark.setPreferredSize(new Dimension(54, 54));
		mark.setBorder(BorderFactory.createLineBorder(new Color(30, 83, 190), 1));
		header.add(mark, BorderLayout.WEST);

		JPanel copy = new JPanel();
		copy.setOpaque(false);
		copy.setLayout(new BoxLayout(copy, BoxLayout.Y_AXIS));
		JLabel title = new JLabel("Advanced Algorithm Settings");
		title.setFont(font(Font.BOLD, 24));
		title.setForeground(INK);
		JLabel subtitle = new JLabel(
				"Editing " + RecommendationModeSupport.algorithmName(algorithm)
				+ ". Defaults preserve the current recommender exactly.");
		subtitle.setFont(font(Font.PLAIN, 12));
		subtitle.setForeground(MUTED);
		copy.add(title);
		copy.add(Box.createRigidArea(new Dimension(0, 4)));
		copy.add(subtitle);
		header.add(copy, BorderLayout.CENTER);

		profileBadge = new JLabel();
		profileBadge.setHorizontalAlignment(SwingConstants.CENTER);
		profileBadge.setFont(font(Font.BOLD, 11));
		profileBadge.setOpaque(true);
		profileBadge.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
		header.add(profileBadge, BorderLayout.EAST);
		return header;
	}

	private JComponent createBody()
	{
		JPanel body = new JPanel();
		body.setOpaque(false);
		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));

		JPanel modeCard = card(new BorderLayout(14, 0));
		customCheckBox = new JCheckBox("Use custom parameters for this run profile");
		customCheckBox.setOpaque(false);
		customCheckBox.setFont(font(Font.BOLD, 13));
		customCheckBox.setForeground(INK);
		customCheckBox.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent event)
			{
				draft.setCustomEnabled(customCheckBox.isSelected());
				refreshState();
			}
		});
		summaryLabel = new JLabel();
		summaryLabel.setFont(font(Font.PLAIN, 12));
		summaryLabel.setForeground(MUTED);
		JPanel modeCopy = new JPanel();
		modeCopy.setOpaque(false);
		modeCopy.setLayout(new BoxLayout(modeCopy, BoxLayout.Y_AXIS));
		modeCopy.add(customCheckBox);
		modeCopy.add(Box.createRigidArea(new Dimension(0, 5)));
		modeCopy.add(summaryLabel);
		modeCard.add(modeCopy, BorderLayout.CENTER);
		body.add(modeCard);
		body.add(Box.createRigidArea(new Dimension(0, 12)));

		parameterPanel = card(new BorderLayout(0, 12));
		parameterPanel.add(sectionTitle(), BorderLayout.NORTH);
		parameterPanel.add(parametersForAlgorithm(), BorderLayout.CENTER);
		body.add(parameterPanel);
		body.add(Box.createRigidArea(new Dimension(0, 12)));
		body.add(safetyNote());
		return body;
	}

	private JComponent sectionTitle()
	{
		JPanel titlePanel = new JPanel(new BorderLayout(10, 0));
		titlePanel.setOpaque(false);
		JLabel title = new JLabel(
				RecommendationModeSupport.algorithmName(algorithm)
				+ " tunable controls");
		title.setFont(font(Font.BOLD, 14));
		title.setForeground(INK);
		JLabel note = new JLabel("Validated before Initialize or Apply.");
		note.setFont(font(Font.BOLD, 11));
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
				row = addRow(grid, row,
						"Number of clusters",
						"Default is 3, matching the current K-Means logic.",
						kMeansClustersSpinner);
				addRow(grid, row,
						"Maximum iterations",
						"Stops clustering if assignments have not converged earlier.",
						kMeansIterationsSpinner);
				break;
			case RecommendationModeSupport.SVM:
				svmCSpinner = doubleSpinner(0.001, 100.0, 0.01);
				svmCalibrationBox = new JCheckBox("Build calibrated probability models");
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
						"Number of deterministic passes over sparse training rows.",
						scalableEpochsSpinner);
				addRow(grid, row,
						"Aggressiveness",
						"Passive-aggressive update size. Default is 0.1.",
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
				mlpSparseEpochsSpinner = intSpinner(1, 200, 1);
				mlpSparseL2Spinner = doubleSpinner(0.0, 1.0, 0.0001);
				mlpFedProxMuSpinner = doubleSpinner(0.0, 10.0, 0.001);
				row = addRow(grid, row,
						"Execution engine",
						"Legacy preserves current Neuroph behavior. Sparse is opt-in.",
						mlpEngineCombo);
				row = addRow(grid, row,
						"Hidden neurons",
						"Default is 10, matching the current Neuroph architecture.",
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
						"Max error",
						"Training target error threshold.",
						mlpMaxErrorSpinner);
				row = addRow(grid, row,
						"Sparse epochs",
						"Only used by Sparse Federated MLP.",
						mlpSparseEpochsSpinner);
				row = addRow(grid, row,
						"Sparse L2",
						"Optional L2 regularization for sparse SGD.",
						mlpSparseL2Spinner);
				addRow(grid, row,
						"FedProx mu",
						"0.0 is FedAvg. Larger values keep local models closer.",
						mlpFedProxMuSpinner);
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
				JLabel unavailable = new JLabel(
						"<html><b>No safe active parameters are exposed.</b><br>"
						+ "This algorithm is inactive or unsupported in the current "
						+ "working recommender path.</html>");
				unavailable.setFont(font(Font.PLAIN, 13));
				unavailable.setForeground(MUTED);
				grid.add(unavailable, baseConstraints(0));
				break;
		}
		return grid;
	}

	private JComponent safetyNote()
	{
		JPanel panel = card(new BorderLayout(12, 0));
		JLabel icon = new JLabel("\u2713");
		icon.setOpaque(true);
		icon.setHorizontalAlignment(SwingConstants.CENTER);
		icon.setForeground(Color.WHITE);
		icon.setBackground(GREEN);
		icon.setPreferredSize(new Dimension(34, 34));
		icon.setFont(font(Font.BOLD, 16));
		panel.add(icon, BorderLayout.WEST);
		JLabel label = new JLabel(
				"<html><b>Safety rule:</b> defaults are the baseline. Custom values "
				+ "are range-checked before they reach Java workers or Python Doc2Vec. "
				+ "Doc2Vec cache keys include these values, so changed models cannot "
				+ "reuse the wrong cache.</html>");
		label.setFont(font(Font.PLAIN, 12));
		label.setForeground(MUTED);
		panel.add(label, BorderLayout.CENTER);
		return panel;
	}

	private JComponent createFooter()
	{
		JPanel footer = new JPanel(new BorderLayout(12, 0));
		footer.setOpaque(false);
		footer.setBorder(BorderFactory.createEmptyBorder(16, 0, 0, 0));
		footer.add(new JSeparator(), BorderLayout.NORTH);

		JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 12));
		actions.setOpaque(false);
		JButton reset = button("Reset to Defaults", new Color(235, 240, 247), INK);
		reset.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent event)
			{
				draft.resetToDefaults();
				setValuesFromSettings(draft);
				refreshState();
			}
		});
		JButton cancel = button("Cancel", new Color(235, 240, 247), INK);
		cancel.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent event)
			{
				accepted = null;
				dispose();
			}
		});
		JButton apply = button("Apply Settings", BLUE, Color.WHITE);
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

	private JButton button(String text, Color background, Color foreground)
	{
		JButton button = new JButton(text);
		button.setFont(font(Font.BOLD, 12));
		button.setForeground(foreground);
		button.setBackground(background);
		button.setFocusPainted(false);
		button.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(BORDER, 1),
				BorderFactory.createEmptyBorder(9, 16, 9, 16)));
		return button;
	}

	private JPanel card(java.awt.LayoutManager layout)
	{
		JPanel panel = new JPanel(layout);
		panel.setBackground(SURFACE);
		panel.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(BORDER, 1),
				BorderFactory.createEmptyBorder(14, 16, 14, 16)));
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
		titleLabel.setFont(font(Font.BOLD, 12));
		titleLabel.setForeground(INK);
		JLabel detailLabel = new JLabel(detail);
		detailLabel.setFont(font(Font.PLAIN, 10));
		detailLabel.setForeground(MUTED);
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
		spinner.setFont(font(Font.BOLD, 12));
		spinner.setBorder(BorderFactory.createLineBorder(BORDER, 1));
		JComponent editor = spinner.getEditor();
		if (editor instanceof JSpinner.DefaultEditor)
		{
			((JSpinner.DefaultEditor)editor).getTextField().setHorizontalAlignment(
					SwingConstants.CENTER);
			((JSpinner.DefaultEditor)editor).getTextField().setFont(
					font(Font.BOLD, 12));
		}
	}

	private void styleCheck(JCheckBox checkBox)
	{
		checkBox.setOpaque(false);
		checkBox.setForeground(INK);
		checkBox.setFont(font(Font.BOLD, 12));
	}

	private void styleCombo(JComboBox<String> comboBox)
	{
		comboBox.setFont(font(Font.BOLD, 12));
		comboBox.setForeground(INK);
		comboBox.setBackground(SURFACE);
		comboBox.setBorder(BorderFactory.createLineBorder(BORDER, 1));
	}

	private Font font(int style, int size)
	{
		Font base = UIManager.getFont("Label.font");
		if (base == null)
		{
			base = new Font("SansSerif", Font.PLAIN, size);
		}
		return base.deriveFont(style, (float)size);
	}

	private void setValuesFromSettings(AlgorithmParameterSettings settings)
	{
		customCheckBox.setSelected(settings.isCustomEnabled());
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
		profileBadge.setText(custom ? "CUSTOM PROFILE" : "DEFAULT PROFILE");
		profileBadge.setForeground(custom ? new Color(102, 66, 0)
				: new Color(0, 88, 55));
		profileBadge.setBackground(custom ? new Color(255, 241, 209)
				: new Color(222, 246, 235));
		summaryLabel.setText(custom
				? draft.summaryForAlgorithm(algorithm)
				: "Using the exact parameters currently built into the program.");
		setChildrenEnabled(parameterPanel, custom);
		customCheckBox.setEnabled(true);
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
			JOptionPane.showMessageDialog(
					this,
					formatErrors(errors),
					"Invalid Algorithm Parameters",
					JOptionPane.WARNING_MESSAGE);
			return;
		}
		accepted = draft.copy();
		dispose();
	}

	private String formatErrors(List<String> errors)
	{
		StringBuilder builder = new StringBuilder("<html><b>Fix these settings:</b><br>");
		for (String error : errors)
		{
			builder.append("&bull; ").append(error).append("<br>");
		}
		builder.append("</html>");
		return builder.toString();
	}

	private void readVisibleControlsIntoSettings(
			AlgorithmParameterSettings settings)
	{
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
}
