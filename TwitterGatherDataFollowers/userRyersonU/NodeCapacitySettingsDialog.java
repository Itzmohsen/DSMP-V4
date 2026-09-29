package TwitterGatherDataFollowers.userRyersonU;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RadialGradientPaint;
import java.awt.Shape;
import java.awt.Window;
import java.awt.geom.RoundRectangle2D;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultCellEditor;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumn;

final class NodeCapacitySettingsDialog extends JDialog
{
	private static final Color BACKGROUND = NebulaDesign.CANVAS_TOP;
	private static final Color SURFACE = NebulaDesign.SURFACE;
	private static final Color SURFACE_ALT = NebulaDesign.SURFACE_ALT;
	private static final Color TEXT = NebulaDesign.TEXT_PRIMARY;
	private static final Color MUTED = NebulaDesign.TEXT_SECONDARY;
	private static final Color BORDER = NebulaDesign.BORDER_SUBTLE;
	private static final Color BLUE = NebulaDesign.ACCENT;
	private static final Color GREEN = NebulaDesign.SUCCESS;
	private static final Color AMBER = NebulaDesign.WARNING;
	private static final DecimalFormat DECIMAL = new DecimalFormat("0.##");

	private final int nodeCount;
	private final int heapMb;
	private final CapacityTableModel tableModel;
	private final JTable table;
	private final JLabel summaryLabel = new JLabel();
	private final Paragraph warningLabel = new Paragraph(
			"", NebulaDesign.fontBody(11.5f), NebulaDesign.TEXT_SECONDARY, 900);
	private boolean applied;

	static boolean showDialog(Window owner, int nodeCount, int heapMb)
	{
		NodeCapacitySettingsDialog dialog =
				new NodeCapacitySettingsDialog(owner, nodeCount, heapMb);
		dialog.setLocationRelativeTo(owner);
		dialog.setVisible(true);
		return dialog.applied;
	}

	private NodeCapacitySettingsDialog(Window owner, int nodeCount, int heapMb)
	{
		super(owner,
				"Node Capacity Configuration",
				ModalityType.APPLICATION_MODAL);
		this.nodeCount = Math.max(1, nodeCount);
		this.heapMb = Math.max(RuntimeResourceSettings.MIN_HEAP_MB, heapMb);
		NodeCapacityRegistry.configureForNodeCount(this.nodeCount, this.heapMb);
		this.tableModel = new CapacityTableModel(this.nodeCount, this.heapMb);
		this.table = new JTable(tableModel);

		setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		setMinimumSize(new Dimension(1120, 650));
		setPreferredSize(new Dimension(1220, 720));
		setContentPane(buildContent());
		configureTable();
		pack();
		updateSummary();
	}

	private JPanel buildContent()
	{
		JPanel root = new JPanel(new BorderLayout(0, 14));
		root.setBackground(BACKGROUND);
		root.setBorder(new EmptyBorder(18, 18, 18, 18));
		root.add(buildHeader(), BorderLayout.NORTH);

		JPanel center = new JPanel(new BorderLayout(0, 12));
		center.setOpaque(false);
		center.add(buildPolicyStrip(), BorderLayout.NORTH);
		center.add(buildTablePanel(), BorderLayout.CENTER);
		root.add(center, BorderLayout.CENTER);
		root.add(buildFooter(), BorderLayout.SOUTH);
		return root;
	}

	private JPanel buildHeader()
	{
		JPanel header = new HeroPanel(new BorderLayout(18, 0));
		header.setBorder(new EmptyBorder(16, 20, 16, 18));

		JPanel copy = new JPanel();
		copy.setOpaque(false);
		copy.setLayout(new BoxLayout(copy, BoxLayout.Y_AXIS));
		JLabel eyebrow = new JLabel("Capacity planning");
		NebulaWidgets.styleSectionLabel(eyebrow);
		eyebrow.setAlignmentX(Component.LEFT_ALIGNMENT);
		JLabel title = new JLabel("Node Capacity Configuration");
		title.setFont(NebulaDesign.fontSemiBold(20f));
		title.setForeground(TEXT);
		title.setAlignmentX(Component.LEFT_ALIGNMENT);
		Paragraph detail = new Paragraph(
				"Set each recommender node's capacity. In the scheduled modes it decides "
				+ "where work runs and is emulated live, so it changes run timing; "
				+ "recommendations are identical for every capacity profile.",
				NebulaDesign.fontBody(12.5f),
				MUTED,
				420);
		copy.add(eyebrow);
		copy.add(Box.createRigidArea(new Dimension(0, 4)));
		copy.add(title);
		copy.add(Box.createRigidArea(new Dimension(0, 5)));
		copy.add(detail);

		JPanel stats = new JPanel(new GridLayout(1, 3, 10, 0));
		stats.setOpaque(false);
		stats.add(new SummaryCard("Nodes", Integer.toString(nodeCount),
				"requested agents", BLUE));
		stats.add(new SummaryCard("Java heap",
				RuntimeResourceSettings.formatMegabytes(heapMb),
				"current run limit", GREEN));
		stats.add(new SummaryCard("Contract",
				"Placement", "changes timing, not scores", AMBER));
		header.add(copy, BorderLayout.CENTER);
		header.add(stats, BorderLayout.EAST);
		return header;
	}

	private JPanel buildPolicyStrip()
	{
		JPanel panel = new JPanel(new GridLayout(1, 4, 10, 0));
		panel.setOpaque(false);
		panel.add(new PolicyCard(
				"CPU multiplier",
				"Relative speed. Emulated runs slow a node to cpu x scale / fastest / slowdown.",
				BLUE));
		panel.add(new PolicyCard(
				"Memory budget",
				"Memory a node may commit to running units; the memory-aware policy admits units only where they fit.",
				GREEN));
		panel.add(new PolicyCard(
				"Queues and jobs",
				"Lanes run units at once; the queue caps how many units a node holds claimed.",
				AMBER));
		panel.add(new PolicyCard(
				"Failure and slowdown",
				"Link delay and slowdown are emulated in scheduled modes; failures are injected only in fault-injection mode.",
				NebulaDesign.PURPLE));
		return panel;
	}

	private JPanel buildTablePanel()
	{
		JPanel panel = new CardPanel(new BorderLayout(0, 9));
		panel.setBorder(new EmptyBorder(12, 14, 15, 14));

		JLabel title = new JLabel("Per-node contract");
		title.setForeground(TEXT);
		title.setFont(NebulaDesign.fontSemiBold(14f));
		JLabel note = new JLabel(
				"Edit values directly. Role changes apply a safe preset that you can still customize.");
		note.setForeground(NebulaDesign.TEXT_TERTIARY);
		note.setFont(NebulaDesign.fontBody(11f));
		JPanel heading = new JPanel(new BorderLayout());
		heading.setOpaque(false);
		heading.add(title, BorderLayout.WEST);
		heading.add(note, BorderLayout.EAST);

		JScrollPane scrollPane = new JScrollPane(table);
		scrollPane.setBorder(BorderFactory.createLineBorder(BORDER, 1));
		scrollPane.getViewport().setBackground(SURFACE);
		scrollPane.getVerticalScrollBar().setUI(new NebulaWidgets.PillScrollBarUI());
		scrollPane.getVerticalScrollBar().setOpaque(false);
		scrollPane.getHorizontalScrollBar().setUI(new NebulaWidgets.PillScrollBarUI());
		scrollPane.getHorizontalScrollBar().setOpaque(false);
		panel.add(heading, BorderLayout.NORTH);
		panel.add(scrollPane, BorderLayout.CENTER);
		return panel;
	}

	private JPanel buildFooter()
	{
		JPanel footer = new JPanel(new BorderLayout(0, 12));
		footer.setOpaque(false);

		JPanel summary = new InsetPanel();
		summary.setBorder(new EmptyBorder(11, 14, 12, 14));
		summary.setLayout(new BorderLayout(0, 5));
		summaryLabel.setFont(NebulaDesign.fontMedium(12.5f));
		summaryLabel.setForeground(TEXT);
		summary.add(summaryLabel, BorderLayout.NORTH);
		summary.add(warningLabel, BorderLayout.CENTER);

		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
		buttons.setOpaque(false);
		JButton balanced = actionButton("Balanced defaults", null, BLUE);
		JButton heterogeneous = actionButton("Heterogeneous lab", null, AMBER);
		JButton originalDefault = actionButton(
				"Restore DSMP default", null, null);
		JButton cancel = actionButton("Cancel", null, null);
		JButton apply = actionButton("Apply capacity", GREEN, null);
		balanced.setToolTipText(
				"Set every node to the neutral v4 capacity profile.");
		heterogeneous.setToolTipText(
				"Create a mixed-capacity lab scenario for scheduling experiments.");
		originalDefault.setToolTipText(
				"Restore exactly how the selected number of nodes would run without node-capacity customization.");
		balanced.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent event)
			{
				tableModel.applyBalanced();
				updateSummary();
			}
		});
		heterogeneous.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent event)
			{
				tableModel.applyHeterogeneous();
				updateSummary();
			}
		});
		originalDefault.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent event)
			{
				tableModel.applyOriginalDsmpDefault();
				updateSummary();
			}
		});
		cancel.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent event)
			{
				dispose();
			}
		});
		apply.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent event)
			{
				applyProfiles();
			}
		});
		buttons.add(balanced);
		buttons.add(heterogeneous);
		buttons.add(originalDefault);
		buttons.add(cancel);
		buttons.add(apply);

		footer.add(summary, BorderLayout.NORTH);
		footer.add(buttons, BorderLayout.SOUTH);
		return footer;
	}

	private JButton actionButton(String text, Color fill, Color dotHue)
	{
		CapacityActionButton button = new CapacityActionButton(text, fill, dotHue);
		NebulaWidgets.polishButton(button);
		return button;
	}

	private void configureTable()
	{
		table.setRowHeight(34);
		table.setFillsViewportHeight(true);
		table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		table.setGridColor(BORDER);
		table.setShowVerticalLines(false);
		table.setBackground(SURFACE);
		table.setForeground(TEXT);
		table.setSelectionBackground(NebulaDesign.SELECTION);
		table.setSelectionForeground(TEXT);
		table.setFont(NebulaDesign.fontBody(12f));
		table.getTableHeader().setFont(NebulaDesign.fontMedium(11f));
		table.getTableHeader().setForeground(MUTED);
		table.getTableHeader().setBackground(NebulaDesign.SURFACE_RAISED);
		table.getTableHeader().setReorderingAllowed(false);
		table.getTableHeader().setDefaultRenderer(new HeaderRenderer());
		table.setDefaultRenderer(Object.class, new CapacityRenderer());
		table.setDefaultRenderer(Number.class, new CapacityRenderer());
		table.setDefaultRenderer(Boolean.class, new SwitchRenderer());
		JCheckBox booleanEditor = new JCheckBox();
		booleanEditor.setHorizontalAlignment(SwingConstants.CENTER);
		booleanEditor.setOpaque(true);
		booleanEditor.setBackground(NebulaDesign.SELECTION);
		booleanEditor.setBorderPainted(false);
		booleanEditor.setIcon(new CheckBoxIcon());
		table.setDefaultEditor(Boolean.class, new DefaultCellEditor(booleanEditor));

		JComboBox<NodeCapacityProfile.Role> roleCombo =
				new JComboBox<NodeCapacityProfile.Role>(
						NodeCapacityProfile.Role.values());
		roleCombo.setFont(NebulaDesign.fontMedium(12f));
		roleCombo.setBackground(NebulaDesign.SURFACE_ALT);
		roleCombo.setForeground(TEXT);
		roleCombo.setRenderer(new RoleItemRenderer());
		table.getColumnModel().getColumn(1).setCellEditor(
				new DefaultCellEditor(roleCombo));
		int[] widths = {64, 128, 78, 100, 76, 82, 92, 82, 82, 82, 70};
		for (int i = 0; i < widths.length; i++)
		{
			TableColumn column = table.getColumnModel().getColumn(i);
			column.setPreferredWidth(widths[i]);
		}
		tableModel.addTableModelListener(new javax.swing.event.TableModelListener()
		{
			public void tableChanged(javax.swing.event.TableModelEvent event)
			{
				updateSummary();
			}
		});
	}

	private void updateSummary()
	{
		List<NodeCapacityProfile> profiles = tableModel.toProfiles();
		long memoryTotal = 0L;
		double cpuTotal = 0.0;
		int simulated = 0;
		for (NodeCapacityProfile profile : profiles)
		{
			memoryTotal += profile.getMemoryLimitMb();
			cpuTotal += profile.getCpuMultiplier();
			if (profile.getNetworkDelayMs() > 0
					|| profile.isSlowdownSimulationEnabled()
					|| profile.isFailureSimulationEnabled())
			{
				simulated++;
			}
		}
		summaryLabel.setText(
				profiles.size() + " nodes | "
				+ DECIMAL.format(cpuTotal) + "x total relative CPU | "
				+ RuntimeResourceSettings.formatMegabytes(memoryTotal)
				+ " total capacity budget");
		boolean originalDefault = isOriginalDsmpDefault(profiles);
		String warning = originalDefault
				? "Original DSMP default is selected: all requested nodes run "
						+ "with the same neutral capacity contract they would have "
						+ "had before this feature existed."
				: "In the scheduled modes these values decide where work runs and "
						+ "are emulated live (speed, lanes, memory, queue, link delay). "
						+ "They change timing, never the recommendations.";
		if (memoryTotal > heapMb)
		{
			warning += " Total node budget exceeds the Java heap, so safe waves may be needed.";
		}
		if (simulated > 0)
		{
			warning += " Delay and slowdown are emulated in the scheduled modes; failures are injected only in the fault-injection mode, and failed units are retried exactly once each.";
		}
		warningLabel.setText(warning);
	}

	private boolean isOriginalDsmpDefault(List<NodeCapacityProfile> profiles)
	{
		if (profiles == null || profiles.size() != nodeCount)
		{
			return false;
		}
		for (NodeCapacityProfile profile : profiles)
		{
			NodeCapacityProfile expected = NodeCapacityProfile.defaultProfile(
					profile.getNodeIndex(),
					nodeCount,
					heapMb);
			if (profile.getRole() != expected.getRole()
					|| Math.abs(profile.getCpuMultiplier()
							- expected.getCpuMultiplier()) > 0.0001
					|| profile.getMemoryLimitMb()
							!= expected.getMemoryLimitMb()
					|| profile.getMaxConcurrentJobs()
							!= expected.getMaxConcurrentJobs()
					|| profile.getNetworkDelayMs()
							!= expected.getNetworkDelayMs()
					|| profile.getQueueCapacity()
							!= expected.getQueueCapacity()
					|| profile.isSlowdownSimulationEnabled()
							!= expected.isSlowdownSimulationEnabled()
					|| Math.abs(profile.getSlowdownMultiplier()
							- expected.getSlowdownMultiplier()) > 0.0001
					|| profile.isFailureSimulationEnabled()
							!= expected.isFailureSimulationEnabled()
					|| profile.getFailureProbabilityPercent()
							!= expected.getFailureProbabilityPercent())
			{
				return false;
			}
		}
		return true;
	}

	private void applyProfiles()
	{
		try
		{
			List<NodeCapacityProfile> profiles = tableModel.validatedProfiles();
			NodeCapacityRegistry.setProfiles(profiles, heapMb);
			applied = true;
			dispose();
		}
		catch (IllegalArgumentException exception)
		{
			NebulaDialogs.showError(
					this,
					exception.getMessage(),
					"Invalid Node Capacity");
		}
	}

	private static String escape(String value)
	{
		if (value == null)
		{
			return "";
		}
		return value.replace("&", "&amp;")
				.replace("<", "&lt;")
				.replace(">", "&gt;");
	}

	private static final class CapacityTableModel extends AbstractTableModel
	{
		private static final String[] COLUMNS = {
				"Node",
				"Role",
				"CPU x",
				"Memory MB",
				"Max jobs",
				"Queue",
				"Delay ms",
				"Slow",
				"Slow x",
				"Fail",
				"Fail %"
		};

		private final int nodeCount;
		private final int heapMb;
		private final ArrayList<Row> rows = new ArrayList<Row>();

		CapacityTableModel(int nodeCount, int heapMb)
		{
			this.nodeCount = Math.max(1, nodeCount);
			this.heapMb = Math.max(RuntimeResourceSettings.MIN_HEAP_MB, heapMb);
			List<NodeCapacityProfile> existing = NodeCapacityRegistry.snapshot();
			for (int index = 1; index <= this.nodeCount; index++)
			{
				NodeCapacityProfile profile = find(existing, index);
				rows.add(new Row(profile == null
						? NodeCapacityProfile.defaultProfile(
								index,
								this.nodeCount,
								this.heapMb)
						: profile));
			}
		}

		public int getRowCount()
		{
			return rows.size();
		}

		public int getColumnCount()
		{
			return COLUMNS.length;
		}

		public String getColumnName(int column)
		{
			return COLUMNS[column];
		}

		public Class<?> getColumnClass(int column)
		{
			if (column == 1)
			{
				return NodeCapacityProfile.Role.class;
			}
			if (column == 7 || column == 9)
			{
				return Boolean.class;
			}
			if (column == 2 || column == 8)
			{
				return Double.class;
			}
			return column == 0 ? Integer.class : Integer.class;
		}

		public boolean isCellEditable(int row, int column)
		{
			return column > 0;
		}

		public Object getValueAt(int rowIndex, int columnIndex)
		{
			Row row = rows.get(rowIndex);
			switch (columnIndex)
			{
				case 0:
					return Integer.valueOf(row.nodeIndex);
				case 1:
					return row.role;
				case 2:
					return Double.valueOf(row.cpuMultiplier);
				case 3:
					return Integer.valueOf(row.memoryLimitMb);
				case 4:
					return Integer.valueOf(row.maxConcurrentJobs);
				case 5:
					return Integer.valueOf(row.queueCapacity);
				case 6:
					return Integer.valueOf(row.networkDelayMs);
				case 7:
					return Boolean.valueOf(row.slowdownSimulationEnabled);
				case 8:
					return Double.valueOf(row.slowdownMultiplier);
				case 9:
					return Boolean.valueOf(row.failureSimulationEnabled);
				case 10:
					return Integer.valueOf(row.failureProbabilityPercent);
				default:
					return "";
			}
		}

		public void setValueAt(Object value, int rowIndex, int columnIndex)
		{
			Row row = rows.get(rowIndex);
			if (columnIndex == 1 && value instanceof NodeCapacityProfile.Role)
			{
				Row preset = new Row(NodeCapacityProfile.rolePreset(
						row.nodeIndex,
						(NodeCapacityProfile.Role)value,
						nodeCount,
						heapMb));
				rows.set(rowIndex, preset);
				fireTableRowsUpdated(rowIndex, rowIndex);
				return;
			}
			try
			{
				switch (columnIndex)
				{
					case 2:
						row.cpuMultiplier = parseDouble(value);
						break;
					case 3:
						row.memoryLimitMb = parseInt(value);
						break;
					case 4:
						row.maxConcurrentJobs = parseInt(value);
						break;
					case 5:
						row.queueCapacity = parseInt(value);
						break;
					case 6:
						row.networkDelayMs = parseInt(value);
						break;
					case 7:
						row.slowdownSimulationEnabled = parseBoolean(value);
						break;
					case 8:
						row.slowdownMultiplier = parseDouble(value);
						break;
					case 9:
						row.failureSimulationEnabled = parseBoolean(value);
						break;
					case 10:
						row.failureProbabilityPercent = parseInt(value);
						break;
					default:
						break;
				}
				fireTableCellUpdated(rowIndex, columnIndex);
			}
			catch (RuntimeException ignored)
			{
				fireTableCellUpdated(rowIndex, columnIndex);
			}
		}

		void applyBalanced()
		{
			rows.clear();
			for (int index = 1; index <= nodeCount; index++)
			{
				rows.add(new Row(NodeCapacityProfile.defaultProfile(
						index,
						nodeCount,
						heapMb)));
			}
			fireTableDataChanged();
		}

		void applyOriginalDsmpDefault()
		{
			applyBalanced();
		}

		void applyHeterogeneous()
		{
			rows.clear();
			NodeCapacityProfile.Role[] pattern = {
					NodeCapacityProfile.Role.STRONG,
					NodeCapacityProfile.Role.NORMAL,
					NodeCapacityProfile.Role.WEAK,
					NodeCapacityProfile.Role.MEMORY_HEAVY,
					NodeCapacityProfile.Role.UNRELIABLE
			};
			for (int index = 1; index <= nodeCount; index++)
			{
				rows.add(new Row(NodeCapacityProfile.rolePreset(
						index,
						pattern[(index - 1) % pattern.length],
						nodeCount,
						heapMb)));
			}
			fireTableDataChanged();
		}

		List<NodeCapacityProfile> toProfiles()
		{
			ArrayList<NodeCapacityProfile> profiles =
					new ArrayList<NodeCapacityProfile>();
			for (Row row : rows)
			{
				profiles.add(row.toProfile());
			}
			return profiles;
		}

		List<NodeCapacityProfile> validatedProfiles()
		{
			List<NodeCapacityProfile> profiles = toProfiles();
			for (NodeCapacityProfile profile : profiles)
			{
				if (profile.getCpuMultiplier()
						< NodeCapacityProfile.MIN_CPU_MULTIPLIER
						|| profile.getCpuMultiplier()
						> NodeCapacityProfile.MAX_CPU_MULTIPLIER)
				{
					throw new IllegalArgumentException(
							"CPU multiplier must be between "
									+ NodeCapacityProfile.MIN_CPU_MULTIPLIER
									+ " and "
									+ NodeCapacityProfile.MAX_CPU_MULTIPLIER
									+ ".");
				}
			}
			return profiles;
		}

		private static NodeCapacityProfile find(
				List<NodeCapacityProfile> profiles,
				int nodeIndex)
		{
			if (profiles == null)
			{
				return null;
			}
			for (NodeCapacityProfile profile : profiles)
			{
				if (profile.getNodeIndex() == nodeIndex)
				{
					return profile;
				}
			}
			return null;
		}

		private static int parseInt(Object value)
		{
			if (value instanceof Number)
			{
				return ((Number)value).intValue();
			}
			return Integer.parseInt(String.valueOf(value).trim());
		}

		private static double parseDouble(Object value)
		{
			if (value instanceof Number)
			{
				return ((Number)value).doubleValue();
			}
			return Double.parseDouble(String.valueOf(value).trim());
		}

		private static boolean parseBoolean(Object value)
		{
			if (value instanceof Boolean)
			{
				return ((Boolean)value).booleanValue();
			}
			return Boolean.parseBoolean(String.valueOf(value));
		}
	}

	private static final class Row
	{
		private final int nodeIndex;
		private NodeCapacityProfile.Role role;
		private double cpuMultiplier;
		private int memoryLimitMb;
		private int maxConcurrentJobs;
		private int networkDelayMs;
		private int queueCapacity;
		private boolean slowdownSimulationEnabled;
		private double slowdownMultiplier;
		private boolean failureSimulationEnabled;
		private int failureProbabilityPercent;

		Row(NodeCapacityProfile profile)
		{
			nodeIndex = profile.getNodeIndex();
			role = profile.getRole();
			cpuMultiplier = profile.getCpuMultiplier();
			memoryLimitMb = profile.getMemoryLimitMb();
			maxConcurrentJobs = profile.getMaxConcurrentJobs();
			networkDelayMs = profile.getNetworkDelayMs();
			queueCapacity = profile.getQueueCapacity();
			slowdownSimulationEnabled =
					profile.isSlowdownSimulationEnabled();
			slowdownMultiplier = profile.getSlowdownMultiplier();
			failureSimulationEnabled =
					profile.isFailureSimulationEnabled();
			failureProbabilityPercent =
					profile.getFailureProbabilityPercent();
		}

		NodeCapacityProfile toProfile()
		{
			return new NodeCapacityProfile(
					nodeIndex,
					role,
					cpuMultiplier,
					memoryLimitMb,
					maxConcurrentJobs,
					networkDelayMs,
					queueCapacity,
					slowdownSimulationEnabled,
					slowdownMultiplier,
					failureSimulationEnabled,
					failureProbabilityPercent);
		}
	}

	private static final class CapacityRenderer
			extends DefaultTableCellRenderer
	{
		private static final long serialVersionUID = 1L;
		private boolean selectedRow;
		private boolean firstColumn;
		private boolean rolePill;

		CapacityRenderer()
		{
			setBorder(new EmptyBorder(0, 8, 0, 8));
		}

		public Component getTableCellRendererComponent(
				JTable table,
				Object value,
				boolean selected,
				boolean focused,
				int row,
				int column)
		{
			JLabel label = (JLabel)super.getTableCellRendererComponent(
					table,
					value,
					selected,
					focused,
					row,
					column);
			selectedRow = selected;
			firstColumn = column == 0;
			rolePill = column == 1 && value instanceof NodeCapacityProfile.Role;
			if (column == 1)
			{
				label.setFont(NebulaDesign.fontMedium(11.5f));
				label.setBorder(new EmptyBorder(0, 16, 0, 8));
			}
			else
			{
				label.setFont(NebulaDesign.fontMono(12f));
				label.setBorder(new EmptyBorder(0, 8, 0, 8));
			}
			label.setOpaque(!rolePill);
			label.setHorizontalAlignment(column == 1
					? SwingConstants.LEFT : SwingConstants.CENTER);
			if (!selected)
			{
				label.setBackground(NebulaDesign.SURFACE);
			}
			label.setForeground(column == 1 ? roleTone(value)
					: column == 0 ? NebulaDesign.TEXT_SECONDARY : TEXT);
			putClientProperty("nebula.roleValue", value);
			return label;
		}

		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g2 = (Graphics2D)graphics.create();
			NebulaDesign.prepare(g2);
			if (rolePill)
			{
				g2.setColor(selectedRow
						? NebulaDesign.SELECTION : NebulaDesign.SURFACE);
				g2.fillRect(0, 0, getWidth(), getHeight());
				Object value = getClientProperty("nebula.roleValue");
				java.awt.FontMetrics metrics = getFontMetrics(getFont());
				int pillWidth = Math.min(getWidth() - 12,
						metrics.stringWidth(getText()) + 20);
				g2.setColor(roleSoft(value));
				g2.fillRoundRect(6, 6, Math.max(20, pillWidth),
						Math.max(14, getHeight() - 12), 999, 999);
			}
			g2.dispose();
			super.paintComponent(graphics);
			if (selectedRow && firstColumn)
			{
				Graphics2D bar = (Graphics2D)graphics.create();
				NebulaDesign.prepare(bar);
				bar.setColor(NebulaDesign.currentAccent());
				bar.fillRoundRect(0, 4, 3, getHeight() - 8, 3, 3);
				bar.dispose();
			}
		}

		private static Color roleTone(Object value)
		{
			if (value instanceof NodeCapacityProfile.Role)
			{
				switch ((NodeCapacityProfile.Role)value)
				{
					case STRONG:
						return NebulaDesign.SUCCESS;
					case WEAK:
						return NebulaDesign.WARNING;
					case MEMORY_HEAVY:
						return NebulaDesign.INFO;
					case UNRELIABLE:
						return NebulaDesign.DANGER;
					default:
						return NebulaDesign.TEXT_SECONDARY;
				}
			}
			return NebulaDesign.TEXT_PRIMARY;
		}

		private static Color roleSoft(Object value)
		{
			if (value instanceof NodeCapacityProfile.Role)
			{
				switch ((NodeCapacityProfile.Role)value)
				{
					case STRONG:
						return NebulaDesign.SUCCESS_SOFT;
					case WEAK:
						return NebulaDesign.WARNING_SOFT;
					case MEMORY_HEAVY:
						return NebulaDesign.INFO_SOFT;
					case UNRELIABLE:
						return NebulaDesign.DANGER_SOFT;
					default:
						return NebulaDesign.SURFACE_ALT;
				}
			}
			return NebulaDesign.SURFACE_ALT;
		}
	}

	/** Table checkbox cell drawn with the Nebula check mark. */
	private static final class SwitchRenderer extends JCheckBox
			implements javax.swing.table.TableCellRenderer
	{
		private static final long serialVersionUID = 1L;

		SwitchRenderer()
		{
			setHorizontalAlignment(SwingConstants.CENTER);
			setOpaque(true);
			setBorderPainted(false);
			setFocusPainted(false);
			setIcon(new CheckBoxIcon());
		}

		public Component getTableCellRendererComponent(
				JTable table,
				Object value,
				boolean selected,
				boolean focused,
				int row,
				int column)
		{
			setSelected(value instanceof Boolean
					&& ((Boolean)value).booleanValue());
			setBackground(selected
					? NebulaDesign.SELECTION : NebulaDesign.SURFACE);
			return this;
		}
	}

	/** Role popup renderer so the editor list matches the active theme. */
	private static final class RoleItemRenderer
			extends javax.swing.DefaultListCellRenderer
	{
		private static final long serialVersionUID = 1L;

		public Component getListCellRendererComponent(
				javax.swing.JList<?> list,
				Object value,
				int index,
				boolean isSelected,
				boolean cellHasFocus)
		{
			JLabel label = (JLabel)super.getListCellRendererComponent(
					list, value, index, isSelected, cellHasFocus);
			label.setFont(NebulaDesign.fontMedium(12f));
			label.setBorder(new EmptyBorder(6, 12, 6, 12));
			label.setOpaque(true);
			label.setBackground(isSelected
					? NebulaDesign.ACCENT_SOFT : NebulaDesign.SURFACE);
			label.setForeground(NebulaDesign.TEXT_PRIMARY);
			return label;
		}
	}

	/** 16px custom checkbox: rounded well, accent fill and tick when on. */
	private static final class CheckBoxIcon implements javax.swing.Icon
	{
		public void paintIcon(Component component, Graphics graphics, int x, int y)
		{
			Graphics2D g2 = (Graphics2D)graphics.create();
			NebulaDesign.prepare(g2);
			javax.swing.AbstractButton button =
					(javax.swing.AbstractButton)component;
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
				g2.setStroke(new java.awt.BasicStroke(2f,
						java.awt.BasicStroke.CAP_ROUND,
						java.awt.BasicStroke.JOIN_ROUND));
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

	/**
	 * Word-wrapped body copy. Swing's HTML labels measure and draw with
	 * different metrics under the bundled Inter faces, which clipped the last
	 * word of every wrapped line; this paints the wrap itself so the measure
	 * and the drawing always agree.
	 */
	private static final class Paragraph extends javax.swing.JComponent
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
			return new Dimension(measure,
					Math.max(1, lines) * metrics.getHeight());
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
							&& metrics.stringWidth(
									text.substring(start, stop + 1)) <= width)
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

	private static final class HeaderRenderer extends DefaultTableCellRenderer
	{
		private static final long serialVersionUID = 1L;

		public Component getTableCellRendererComponent(
				JTable table,
				Object value,
				boolean selected,
				boolean focused,
				int row,
				int column)
		{
			JLabel label = (JLabel)super.getTableCellRendererComponent(
					table,
					value,
					false,
					false,
					row,
					column);
			label.setOpaque(true);
			label.setBackground(NebulaDesign.SURFACE_RAISED);
			label.setForeground(NebulaDesign.TEXT_SECONDARY);
			label.setFont(NebulaDesign.fontMedium(11f));
			label.setHorizontalAlignment(SwingConstants.CENTER);
			label.setBorder(BorderFactory.createCompoundBorder(
					BorderFactory.createMatteBorder(
							0, 0, 1, 0, NebulaDesign.BORDER_STRONG),
					new EmptyBorder(7, 6, 7, 6)));
			return label;
		}
	}

	private static final class CapacityActionButton extends JButton
	{
		private static final long serialVersionUID = 1L;
		private final Color fill;
		private final Color dotHue;

		CapacityActionButton(String text, Color fill, Color dotHue)
		{
			super(text);
			this.fill = fill;
			this.dotHue = dotHue;
			setFocusPainted(false);
			setRolloverEnabled(true);
			setOpaque(false);
			setContentAreaFilled(false);
			setBorderPainted(false);
			setFont(NebulaDesign.fontMedium(12.5f));
			setBorder(new EmptyBorder(9, 16, 9, 16));
		}

		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g2 = (Graphics2D)graphics.create();
			NebulaDesign.prepare(g2);
			int width = getWidth();
			int height = getHeight();
			boolean hover = getModel().isRollover();
			boolean pressed = getModel().isPressed();
			Color background;
			Color textColor;
			Color outline = null;
			if (!isEnabled())
			{
				background = NebulaDesign.SURFACE_ALT;
				textColor = NebulaDesign.TEXT_TERTIARY;
				outline = NebulaDesign.BORDER_SUBTLE;
			}
			else if (fill != null)
			{
				background = pressed ? darken(fill, 0.14f)
						: hover ? brighten(fill, 0.10f) : fill;
				textColor = NebulaDesign.TEXT_ON_ACCENT;
			}
			else
			{
				background = hover || pressed
						? NebulaDesign.SURFACE_RAISED : NebulaDesign.SURFACE;
				textColor = NebulaDesign.TEXT_PRIMARY;
				outline = hover
						? NebulaDesign.withAlpha(NebulaDesign.currentAccent(), 110)
						: NebulaDesign.BORDER_STRONG;
			}
			g2.setColor(background);
			g2.fillRoundRect(0, 0, width - 1, height - 1,
					NebulaDesign.RADIUS_CONTROL, NebulaDesign.RADIUS_CONTROL);
			if (outline != null)
			{
				g2.setColor(outline);
				g2.drawRoundRect(0, 0, width - 1, height - 1,
						NebulaDesign.RADIUS_CONTROL, NebulaDesign.RADIUS_CONTROL);
			}
			g2.setFont(getFont());
			java.awt.FontMetrics metrics = g2.getFontMetrics();
			int dotSpan = dotHue == null ? 0 : 13;
			int textWidth = metrics.stringWidth(getText());
			int groupX = (width - textWidth - dotSpan) / 2;
			int textY = (height - metrics.getHeight()) / 2 + metrics.getAscent();
			if (dotHue != null)
			{
				g2.setColor(isEnabled() ? dotHue : NebulaDesign.TEXT_TERTIARY);
				g2.fillOval(groupX, height / 2 - 3, 7, 7);
			}
			g2.setColor(textColor);
			g2.drawString(getText(), groupX + dotSpan, textY);
			g2.dispose();
		}

		private static Color brighten(Color color, float amount)
		{
			return blend(color, Color.WHITE, amount);
		}

		private static Color darken(Color color, float amount)
		{
			return blend(color, Color.BLACK, amount);
		}

		private static Color blend(Color from, Color to, float amount)
		{
			float clamped = Math.max(0.0f, Math.min(1.0f, amount));
			int red = Math.round(from.getRed()
					+ (to.getRed() - from.getRed()) * clamped);
			int green = Math.round(from.getGreen()
					+ (to.getGreen() - from.getGreen()) * clamped);
			int blue = Math.round(from.getBlue()
					+ (to.getBlue() - from.getBlue()) * clamped);
			return new Color(red, green, blue);
		}
	}

	/** Hero card: surface fill, faint aurora glows, rounded border. */
	private static final class HeroPanel extends JPanel
	{
		private static final long serialVersionUID = 1L;

		HeroPanel(BorderLayout layout)
		{
			super(layout);
			setOpaque(false);
		}

		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g2 = (Graphics2D)graphics.create();
			NebulaDesign.prepare(g2);
			int width = getWidth();
			int height = getHeight();
			Shape round = new RoundRectangle2D.Float(
					0, 0, width - 1, height - 1,
					NebulaDesign.RADIUS_CARD, NebulaDesign.RADIUS_CARD);
			g2.setColor(NebulaDesign.SURFACE);
			g2.fill(round);
			Shape oldClip = g2.getClip();
			g2.clip(round);
			boolean night = NebulaDesign.night();
			Color accent = night
					? NebulaDesign.NIGHT_ACCENT : NebulaDesign.DAY_ACCENT;
			Color purple = night
					? NebulaDesign.NIGHT_PURPLE : NebulaDesign.DAY_PURPLE;
			int glowAlpha = night ? 26 : 15;
			paintGlow(g2, width * 0.14f, 0f,
					Math.max(width, height) * 0.45f,
					NebulaDesign.withAlpha(accent, glowAlpha));
			paintGlow(g2, width * 0.86f, height,
					Math.max(width, height) * 0.40f,
					NebulaDesign.withAlpha(purple, Math.max(6, glowAlpha - 3)));
			g2.setClip(oldClip);
			g2.setColor(NebulaDesign.BORDER_SUBTLE);
			g2.draw(round);
			g2.dispose();
			super.paintComponent(graphics);
		}

		private static void paintGlow(
				Graphics2D g2,
				float centerX,
				float centerY,
				float radius,
				Color color)
		{
			RadialGradientPaint paint = new RadialGradientPaint(
					new java.awt.geom.Point2D.Float(centerX, centerY),
					Math.max(1f, radius),
					new float[] {0f, 1f},
					new Color[] {color, NebulaDesign.withAlpha(color, 0)});
			g2.setPaint(paint);
			g2.fillRect(
					Math.round(centerX - radius),
					Math.round(centerY - radius),
					Math.round(radius * 2),
					Math.round(radius * 2));
		}
	}

	/** Rounded inset well used by the footer summary. */
	private static final class InsetPanel extends JPanel
	{
		private static final long serialVersionUID = 1L;

		InsetPanel()
		{
			setOpaque(false);
		}

		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g2 = (Graphics2D)graphics.create();
			NebulaDesign.prepare(g2);
			g2.setColor(NebulaDesign.INSET);
			g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1,
					NebulaDesign.RADIUS_CONTROL, NebulaDesign.RADIUS_CONTROL);
			g2.setColor(NebulaDesign.BORDER_SUBTLE);
			g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1,
					NebulaDesign.RADIUS_CONTROL, NebulaDesign.RADIUS_CONTROL);
			g2.dispose();
			super.paintComponent(graphics);
		}
	}

	/** Rounded Nebula card with day shadow / night surface lift. */
	private static final class CardPanel extends JPanel
	{
		private static final long serialVersionUID = 1L;

		CardPanel(BorderLayout layout)
		{
			super(layout);
			setOpaque(false);
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

	/** Small filled circle icon used to key tiles and cards to a hue. */
	private static final class DotIcon implements javax.swing.Icon
	{
		private final Color hue;
		private final int size;

		DotIcon(Color hue, int size)
		{
			this.hue = hue;
			this.size = size;
		}

		public void paintIcon(Component component, Graphics graphics, int x, int y)
		{
			Graphics2D g2 = (Graphics2D)graphics.create();
			NebulaDesign.prepare(g2);
			g2.setColor(hue);
			g2.fillOval(x, y, size, size);
			g2.dispose();
		}

		public int getIconWidth()
		{
			return size;
		}

		public int getIconHeight()
		{
			return size;
		}
	}

	private static final class SummaryCard extends JPanel
	{
		private static final long serialVersionUID = 1L;

		SummaryCard(
				String title,
				String value,
				String detail,
				Color accent)
		{
			super(new BorderLayout(0, 2));
			setOpaque(false);
			setBorder(new EmptyBorder(10, 12, 9, 10));
			JLabel titleLabel = new JLabel(title.toUpperCase(Locale.US));
			titleLabel.setForeground(NebulaDesign.TEXT_TERTIARY);
			titleLabel.setFont(NebulaDesign.fontSemiBold(10f));
			titleLabel.setIcon(new DotIcon(accent, 7));
			titleLabel.setIconTextGap(6);
			JLabel valueLabel = new JLabel(value);
			valueLabel.setForeground(TEXT);
			valueLabel.setFont(NebulaDesign.fontSemiBold(18f));
			JLabel detailLabel = new JLabel(detail);
			detailLabel.setForeground(NebulaDesign.TEXT_TERTIARY);
			detailLabel.setFont(NebulaDesign.fontMedium(10f));
			add(titleLabel, BorderLayout.NORTH);
			add(valueLabel, BorderLayout.CENTER);
			add(detailLabel, BorderLayout.SOUTH);
			setPreferredSize(new Dimension(162, 80));
		}

		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g2 = (Graphics2D)graphics.create();
			NebulaDesign.prepare(g2);
			g2.setColor(NebulaDesign.SURFACE_ALT);
			g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
			g2.setColor(NebulaDesign.BORDER_SUBTLE);
			g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
			g2.dispose();
			super.paintComponent(graphics);
		}
	}

	private static final class PolicyCard extends JPanel
	{
		private static final long serialVersionUID = 1L;

		PolicyCard(String title, String detail, Color accent)
		{
			super(new BorderLayout(0, 4));
			setOpaque(false);
			setBorder(new EmptyBorder(10, 14, 12, 12));
			JLabel titleLabel = new JLabel(title);
			titleLabel.setForeground(TEXT);
			titleLabel.setFont(NebulaDesign.fontMedium(12.5f));
			titleLabel.setIcon(new DotIcon(accent, 7));
			titleLabel.setIconTextGap(7);
			Paragraph detailLabel = new Paragraph(
					detail,
					NebulaDesign.fontBody(11f),
					NebulaDesign.TEXT_TERTIARY,
					206);
			add(titleLabel, BorderLayout.NORTH);
			add(detailLabel, BorderLayout.CENTER);
		}

		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g2 = (Graphics2D)graphics.create();
			NebulaWidgets.paintCard(g2, 0, 0, getWidth(), getHeight(), 12);
			g2.dispose();
			super.paintComponent(graphics);
		}
	}
}
