package TwitterGatherDataFollowers.userRyersonU;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.swing.AbstractCellEditor;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultCellEditor;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableColumn;

final class NodeCapacitySettingsDialog extends JDialog
{
	private static final Color BACKGROUND = new Color(246, 248, 251);
	private static final Color SURFACE = new Color(255, 255, 255);
	private static final Color SURFACE_ALT = new Color(248, 250, 252);
	private static final Color TEXT = new Color(24, 29, 36);
	private static final Color MUTED = new Color(91, 101, 116);
	private static final Color BORDER = new Color(207, 216, 227);
	private static final Color BLUE = new Color(83, 113, 220);
	private static final Color GREEN = new Color(88, 154, 112);
	private static final Color AMBER = new Color(190, 145, 66);
	private static final DecimalFormat DECIMAL = new DecimalFormat("0.##");

	private final int nodeCount;
	private final int heapMb;
	private final CapacityTableModel tableModel;
	private final JTable table;
	private final JLabel summaryLabel = new JLabel();
	private final JLabel warningLabel = new JLabel();
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
		JPanel header = new GradientPanel(new BorderLayout(18, 0));
		header.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(BORDER, 1),
				new EmptyBorder(20, 22, 20, 22)));

		JPanel copy = new JPanel();
		copy.setOpaque(false);
		copy.setLayout(new BoxLayout(copy, BoxLayout.Y_AXIS));
		JLabel title = new JLabel("Node Capacity Configuration");
		title.setFont(new Font("SansSerif", Font.BOLD, 26));
		title.setForeground(TEXT);
		JLabel detail = new JLabel(
				"Define the scheduling contract for each recommender node without changing recommendation scoring.");
		detail.setFont(new Font("SansSerif", Font.PLAIN, 12));
		detail.setForeground(MUTED);
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
				"Scoring-safe", "metadata and scheduler hints", AMBER));
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
				"Shows relative processing capacity for scheduling experiments.",
				BLUE));
		panel.add(new PolicyCard(
				"Memory budget",
				"Per-node model budget hint. The global heap and safe-wave scheduler still protect the JVM.",
				GREEN));
		panel.add(new PolicyCard(
				"Queues and jobs",
				"Describes how much work a node should accept in scheduling scenarios.",
				AMBER));
		panel.add(new PolicyCard(
				"Failure and slowdown",
				"Stored for v4 what-if simulation. It is not injected into live scoring by default.",
				new Color(139, 107, 214)));
		return panel;
	}

	private JPanel buildTablePanel()
	{
		JPanel panel = new JPanel(new BorderLayout(0, 9));
		panel.setOpaque(true);
		panel.setBackground(SURFACE);
		panel.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(BORDER, 1),
				new EmptyBorder(12, 12, 12, 12)));

		JLabel title = new JLabel("Per-node contract");
		title.setForeground(TEXT);
		title.setFont(new Font("SansSerif", Font.BOLD, 14));
		JLabel note = new JLabel(
				"Edit values directly. Role changes apply a safe preset that you can still customize.");
		note.setForeground(MUTED);
		note.setFont(new Font("SansSerif", Font.PLAIN, 11));
		JPanel heading = new JPanel(new BorderLayout());
		heading.setOpaque(false);
		heading.add(title, BorderLayout.WEST);
		heading.add(note, BorderLayout.EAST);

		JScrollPane scrollPane = new JScrollPane(table);
		scrollPane.setBorder(BorderFactory.createLineBorder(BORDER, 1));
		scrollPane.getViewport().setBackground(Color.WHITE);
		panel.add(heading, BorderLayout.NORTH);
		panel.add(scrollPane, BorderLayout.CENTER);
		return panel;
	}

	private JPanel buildFooter()
	{
		JPanel footer = new JPanel(new BorderLayout(12, 0));
		footer.setOpaque(false);

		JPanel summary = new JPanel();
		summary.setOpaque(true);
		summary.setBackground(SURFACE_ALT);
		summary.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(BORDER, 1),
				new EmptyBorder(10, 12, 10, 12)));
		summary.setLayout(new BoxLayout(summary, BoxLayout.Y_AXIS));
		summaryLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
		summaryLabel.setForeground(TEXT);
		warningLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
		warningLabel.setForeground(MUTED);
		summary.add(summaryLabel);
		summary.add(Box.createRigidArea(new Dimension(0, 4)));
		summary.add(warningLabel);

		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
		buttons.setOpaque(false);
		JButton balanced = actionButton("Balanced defaults", BLUE);
		JButton heterogeneous = actionButton("Heterogeneous lab", AMBER);
		JButton originalDefault = actionButton(
				"Restore DSMP Default",
				new Color(112, 120, 132));
		JButton cancel = actionButton("Cancel", new Color(148, 163, 184));
		JButton apply = actionButton("Apply capacity", GREEN);
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

		footer.add(summary, BorderLayout.CENTER);
		footer.add(buttons, BorderLayout.EAST);
		return footer;
	}

	private JButton actionButton(String text, Color accent)
	{
		return new CapacityActionButton(text, accent);
	}

	private void configureTable()
	{
		table.setRowHeight(34);
		table.setFillsViewportHeight(true);
		table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		table.setGridColor(new Color(226, 232, 240));
		table.setShowVerticalLines(false);
		table.setFont(new Font("SansSerif", Font.PLAIN, 12));
		table.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 11));
		table.getTableHeader().setForeground(MUTED);
		table.getTableHeader().setReorderingAllowed(false);
		table.setDefaultRenderer(Object.class, new CapacityRenderer());
		table.setDefaultRenderer(Number.class, new CapacityRenderer());
		table.setDefaultRenderer(Boolean.class, table.getDefaultRenderer(Boolean.class));

		JComboBox<NodeCapacityProfile.Role> roleCombo =
				new JComboBox<NodeCapacityProfile.Role>(
						NodeCapacityProfile.Role.values());
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
				: "These values now drive capacity-aware work assignment. "
						+ "They change which node receives more work, not the "
						+ "recommendation model formulas or candidate-score math.";
		if (memoryTotal > heapMb)
		{
			warning += " Total node budget exceeds the Java heap, so safe waves may be needed.";
		}
		if (simulated > 0)
		{
			warning += " Delay, slowdown, and failure simulation are applied in the Exact Multi-Node Lab so what-if scheduling scenarios stay isolated from normal scoring runs.";
		}
		warningLabel.setText("<html><body style='width:720px'>"
				+ escape(warning) + "</body></html>");
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
			JOptionPane.showMessageDialog(
					this,
					exception.getMessage(),
					"Invalid Node Capacity",
					JOptionPane.ERROR_MESSAGE);
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
			label.setFont(new Font("SansSerif",
					column == 0 || column == 1 ? Font.BOLD : Font.PLAIN,
					12));
			label.setHorizontalAlignment(column == 1
					? SwingConstants.LEFT : SwingConstants.CENTER);
			if (!selected)
			{
				label.setBackground(row % 2 == 0
						? Color.WHITE : new Color(249, 251, 253));
				label.setForeground(TEXT);
			}
			return label;
		}
	}

	private static final class CapacityActionButton extends JButton
	{
		private final Color accent;

		CapacityActionButton(String text, Color accent)
		{
			super(text);
			this.accent = accent;
			setFocusPainted(false);
			setRolloverEnabled(true);
			setOpaque(false);
			setContentAreaFilled(false);
			setBorderPainted(false);
			setForeground(Color.WHITE);
			setFont(new Font("SansSerif", Font.BOLD, 12));
			setBorder(new EmptyBorder(10, 16, 10, 16));
			setCursor(java.awt.Cursor.getPredefinedCursor(
					java.awt.Cursor.HAND_CURSOR));
		}

		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g2 = (Graphics2D)graphics.create();
			g2.setRenderingHint(
					RenderingHints.KEY_ANTIALIASING,
					RenderingHints.VALUE_ANTIALIAS_ON);
			Color fill = accent;
			if (getModel().isPressed())
			{
				fill = darken(accent, 0.16f);
			}
			else if (getModel().isRollover())
			{
				fill = brighten(accent, 0.08f);
			}
			g2.setColor(fill);
			g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
			g2.setColor(new Color(0, 0, 0, 45));
			g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
			g2.dispose();
			super.paintComponent(graphics);
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

	private static final class GradientPanel extends JPanel
	{
		GradientPanel(BorderLayout layout)
		{
			super(layout);
			setOpaque(false);
		}

		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g2 = (Graphics2D)graphics.create();
			g2.setRenderingHint(
					RenderingHints.KEY_ANTIALIASING,
					RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setPaint(new GradientPaint(
					0,
					0,
					Color.WHITE,
					getWidth(),
					getHeight(),
					new Color(232, 244, 239)));
			g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
			g2.dispose();
			super.paintComponent(graphics);
		}
	}

	private static final class SummaryCard extends JPanel
	{
		SummaryCard(
				String title,
				String value,
				String detail,
				Color accent)
		{
			super(new BorderLayout(0, 2));
			setOpaque(true);
			setBackground(new Color(
					accent.getRed(), accent.getGreen(), accent.getBlue(), 18));
			setBorder(BorderFactory.createCompoundBorder(
					BorderFactory.createLineBorder(new Color(
							accent.getRed(), accent.getGreen(), accent.getBlue(), 80)),
					new EmptyBorder(10, 12, 10, 12)));
			JLabel titleLabel = new JLabel(title.toUpperCase(Locale.US));
			titleLabel.setForeground(MUTED);
			titleLabel.setFont(new Font("SansSerif", Font.BOLD, 9));
			JLabel valueLabel = new JLabel(value);
			valueLabel.setForeground(TEXT);
			valueLabel.setFont(new Font("SansSerif", Font.BOLD, 18));
			JLabel detailLabel = new JLabel(detail);
			detailLabel.setForeground(MUTED);
			detailLabel.setFont(new Font("SansSerif", Font.PLAIN, 10));
			add(titleLabel, BorderLayout.NORTH);
			add(valueLabel, BorderLayout.CENTER);
			add(detailLabel, BorderLayout.SOUTH);
			setPreferredSize(new Dimension(150, 82));
		}
	}

	private static final class PolicyCard extends JPanel
	{
		PolicyCard(String title, String detail, Color accent)
		{
			super(new BorderLayout(0, 4));
			setOpaque(true);
			setBackground(SURFACE);
			setBorder(BorderFactory.createCompoundBorder(
					BorderFactory.createMatteBorder(0, 5, 0, 0, accent),
					BorderFactory.createCompoundBorder(
							BorderFactory.createLineBorder(BORDER, 1),
							new EmptyBorder(10, 12, 10, 12))));
			JLabel titleLabel = new JLabel(title);
			titleLabel.setForeground(TEXT);
			titleLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
			JLabel detailLabel = new JLabel("<html><body style='width:220px'>"
					+ escape(detail) + "</body></html>");
			detailLabel.setForeground(MUTED);
			detailLabel.setFont(new Font("SansSerif", Font.PLAIN, 10));
			add(titleLabel, BorderLayout.NORTH);
			add(detailLabel, BorderLayout.CENTER);
		}
	}
}
