package TwitterGatherDataFollowers.userRyersonU;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

final class NodeExecutionDashboard extends JPanel
		implements NodeExecutionMonitor.Listener
{
	private static final Color BACKGROUND = NebulaDesign.CANVAS_TOP;
	private static final Color SURFACE = NebulaDesign.SURFACE;
	private static final Color SURFACE_ALT = NebulaDesign.SURFACE_ALT;
	private static final Color BORDER = NebulaDesign.BORDER_SUBTLE;
	private static final Color TEXT = NebulaDesign.TEXT_PRIMARY;
	private static final Color MUTED = NebulaDesign.TEXT_SECONDARY;
	private static final Color FAINT = NebulaDesign.TEXT_TERTIARY;
	private static final Color BLUE = NebulaDesign.ACCENT;
	private static final Color GREEN = NebulaDesign.SUCCESS;
	private static final Color AMBER = NebulaDesign.WARNING;
	private static final Color RED = NebulaDesign.DANGER;
	private static final Color PURPLE = NebulaDesign.PURPLE;

	private final JLabel contextLabel =
			new LockedLabel("No node experiment configured");
	private final JLabel metricsLabel =
			new Pill("0 REQUESTED  |  0 ACTIVE  |  0 WAITING");
	private final JLabel phaseLabel =
			new LockedLabel("Configure and initialize an experiment.");
	private final NodeTopologyPanel topologyPanel = new NodeTopologyPanel();
	private final JPanel cardsPanel = new JPanel(new GridLayout(0, 2, 10, 10));
	private final Map<Integer,NodeCard> cards =
			new LinkedHashMap<Integer,NodeCard>();
	private final Timer timer;
	private NodeInspectorDialog inspectorDialog;
	private NodeExecutionMonitor.Snapshot snapshot =
			NodeExecutionMonitor.snapshot();

	NodeExecutionDashboard()
	{
		super(new BorderLayout(0, 12));
		setBackground(BACKGROUND);
		setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

		JPanel header = new JPanel();
		header.setOpaque(false);
		header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));

		JPanel titleRow = new JPanel(new BorderLayout(10, 0));
		titleRow.setOpaque(false);
		JPanel titleStack = new JPanel();
		titleStack.setOpaque(false);
		titleStack.setLayout(new BoxLayout(titleStack, BoxLayout.Y_AXIS));
		LockedLabel eyebrow = new LockedLabel("NODE TELEMETRY");
		eyebrow.setFont(NebulaDesign.fontSection());
		eyebrow.setForeground(NebulaDesign.ACCENT);
		eyebrow.lockForeground();
		LockedLabel title = new LockedLabel("Parallel Node Activity");
		title.setForeground(TEXT);
		title.lockForeground();
		title.setFont(NebulaDesign.fontSemiBold(17f));
		contextLabel.setForeground(MUTED);
		contextLabel.setFont(NebulaDesign.fontBody(11.5f));
		((LockedLabel)contextLabel).lockForeground();
		eyebrow.setAlignmentX(Component.LEFT_ALIGNMENT);
		title.setAlignmentX(Component.LEFT_ALIGNMENT);
		contextLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		titleStack.add(eyebrow);
		titleStack.add(Box.createRigidArea(new Dimension(0, 2)));
		titleStack.add(title);
		titleStack.add(Box.createRigidArea(new Dimension(0, 2)));
		titleStack.add(contextLabel);

		metricsLabel.setForeground(BLUE);
		metricsLabel.setFont(NebulaDesign.fontMedium(9.5f));
		metricsLabel.setBackground(NebulaDesign.ACCENT_SOFT);
		metricsLabel.setBorder(BorderFactory.createEmptyBorder(5, 12, 5, 12));
		titleRow.add(titleStack, BorderLayout.CENTER);

		JPanel metricsRow = new JPanel(new BorderLayout());
		metricsRow.setOpaque(false);
		metricsRow.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
		JPanel metricsHolder = new JPanel(new GridBagLayout());
		metricsHolder.setOpaque(false);
		metricsHolder.add(metricsLabel);
		metricsRow.add(metricsHolder, BorderLayout.WEST);

		phaseLabel.setForeground(MUTED);
		phaseLabel.setFont(NebulaDesign.fontBody(11f));
		((LockedLabel)phaseLabel).lockForeground();
		phaseLabel.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
		phaseLabel.setHorizontalAlignment(SwingConstants.LEFT);
		phaseLabel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
		JPanel phaseRow = new JPanel(new BorderLayout());
		phaseRow.setOpaque(false);
		phaseRow.add(phaseLabel, BorderLayout.CENTER);

		header.add(titleRow);
		header.add(metricsRow);
		header.add(phaseRow);
		header.add(Box.createRigidArea(new Dimension(0, 10)));
		header.add(topologyPanel);

		cardsPanel.setOpaque(false);
		JPanel cardsHolder = new JPanel(new BorderLayout());
		cardsHolder.setBackground(BACKGROUND);
		cardsHolder.add(cardsPanel, BorderLayout.NORTH);

		JScrollPane scrollPane = new JScrollPane(cardsHolder);
		scrollPane.setBorder(BorderFactory.createEmptyBorder());
		scrollPane.getViewport().setBackground(BACKGROUND);
		scrollPane.getVerticalScrollBar().setUnitIncrement(18);
		scrollPane.getVerticalScrollBar().setUI(new NebulaWidgets.PillScrollBarUI());
		scrollPane.getVerticalScrollBar().setOpaque(false);
		scrollPane.setHorizontalScrollBarPolicy(
				ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

		add(header, BorderLayout.NORTH);
		add(scrollPane, BorderLayout.CENTER);

		timer = new Timer(500, new ActionListener()
		{
			public void actionPerformed(ActionEvent event)
			{
				refreshElapsedTimes();
				topologyPanel.advanceAnimation();
				if (inspectorDialog != null)
				{
					inspectorDialog.tick();
				}
			}
		});
		timer.setCoalesce(true);
	}

	public void addNotify()
	{
		super.addNotify();
		NodeExecutionMonitor.addListener(this);
		timer.start();
	}

	public void removeNotify()
	{
		timer.stop();
		NodeExecutionMonitor.removeListener(this);
		super.removeNotify();
	}

	public void onSnapshot(final NodeExecutionMonitor.Snapshot newSnapshot)
	{
		if (newSnapshot == null)
		{
			return;
		}
		if (SwingUtilities.isEventDispatchThread())
		{
			applySnapshot(newSnapshot);
		}
		else
		{
			SwingUtilities.invokeLater(new Runnable()
			{
				public void run()
				{
					applySnapshot(newSnapshot);
				}
			});
		}
	}

	private void applySnapshot(NodeExecutionMonitor.Snapshot newSnapshot)
	{
		snapshot = newSnapshot;
		int requested = newSnapshot.getRequestedNodes();
		int active = newSnapshot.activeCount();
		int waiting = newSnapshot.waitingCount();
		int complete = newSnapshot.count(NodeExecutionMonitor.State.COMPLETE);
		int failed = newSnapshot.count(NodeExecutionMonitor.State.FAILED);
		RecommendationWorkQueueRegistry.QueueSnapshot inputQueue =
				inputQueueSnapshotFor(newSnapshot);

		String context = newSnapshot.getAlgorithmName();
		if (newSnapshot.getRunSequence() > 0)
		{
			context += "  |  Run " + newSnapshot.getRunSequence();
		}
		else if (requested > 0)
		{
			context += "  |  Agents initializing";
		}
		contextLabel.setText(context);
		String metricsText = requested + " REQUESTED  |  " + active
				+ " ACTIVE  |  " + waiting + " WAITING";
		if (hasInputQueue(inputQueue))
		{
			metricsText += "  |  INPUT "
					+ inputQueue.getCompletedCount() + "/"
					+ inputQueue.getTotalShards();
		}
		metricsLabel.setText(metricsText);
		if (hasInputQueue(inputQueue))
		{
			phaseLabel.setText(inputQueuePhaseText(newSnapshot, inputQueue));
			phaseLabel.setToolTipText(inputQueueTooltip(newSnapshot, inputQueue)
					+ "  " + complete + " complete, " + failed + " failed");
		}
		else
		{
			phaseLabel.setText(newSnapshot.getRunPhase());
			phaseLabel.setToolTipText(
					newSnapshot.getRunPhase() + "  "
							+ complete + " complete, " + failed + " failed");
		}

		boolean structureChanged = cards.size() != newSnapshot.getNodes().size();
		if (structureChanged)
		{
			cards.clear();
			cardsPanel.removeAll();
			for (NodeExecutionMonitor.NodeSnapshot node
					: newSnapshot.getNodes())
			{
				NodeCard card = new NodeCard(node.getNodeIndex());
				cards.put(Integer.valueOf(node.getNodeIndex()), card);
				cardsPanel.add(card);
			}
		}

		for (NodeExecutionMonitor.NodeSnapshot node : newSnapshot.getNodes())
		{
			NodeCard card = cards.get(Integer.valueOf(node.getNodeIndex()));
			if (card != null)
			{
				card.update(node, inputQueue);
			}
		}
		topologyPanel.setSnapshot(newSnapshot);
		if (inspectorDialog != null)
		{
			inspectorDialog.updateSnapshot(newSnapshot);
		}
		if (structureChanged)
		{
			cardsPanel.revalidate();
		}
		cardsPanel.repaint();
	}

	private void refreshElapsedTimes()
	{
		for (NodeCard card : cards.values())
		{
			card.refreshElapsed();
		}
	}

	private static Color colorFor(NodeExecutionMonitor.State state)
	{
		switch (state)
		{
			case COMPLETE:
				return GREEN;
				case FAILED:
					return RED;
				case WAITING_FOR_INPUT:
				case WAITING_FOR_MEMORY:
				case WAITING_FOR_SHARED_MODEL:
				return AMBER;
			case FEATURES:
				return PURPLE;
			case MODEL_RUNNING:
			case RECEIVING:
			case PREPROCESSING:
			case PUBLISHING:
				return BLUE;
			case STOPPED:
				return NebulaDesign.TEXT_SECONDARY;
			default:
				return FAINT;
		}
	}

	private static Color themed(Color day, Color night)
	{
		return StudioNightTheme.isNightMode() ? night : day;
	}

	private static Color glass(int alpha)
	{
		return themed(
				NebulaDesign.withAlpha(NebulaDesign.DAY_SURFACE, alpha),
				NebulaDesign.withAlpha(
						NebulaDesign.NIGHT_SURFACE_ALT,
						Math.min(255, alpha + 16)));
	}

	private static String statusText(NodeExecutionMonitor.State state)
	{
		return state.name().replace('_', ' ');
	}

	private static String formatDuration(long milliseconds)
	{
		long totalSeconds = Math.max(0L, milliseconds / 1000L);
		long hours = totalSeconds / 3600L;
		long minutes = (totalSeconds % 3600L) / 60L;
		long seconds = totalSeconds % 60L;
		if (hours > 0L)
		{
			return String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds);
		}
		return String.format(Locale.US, "%02d:%02d", minutes, seconds);
	}

	private static String formatCount(long value)
	{
		if (value < 0L)
		{
			return "-";
		}
		return String.format(Locale.US, "%,d", value);
	}

	private static String formatMillis(long value)
	{
		if (value < 0L)
		{
			return "-";
		}
		if (value < 1000L)
		{
			return value + " ms";
		}
		return String.format(Locale.US, "%.2f s", value / 1000.0);
	}

	private static String safe(String value, String fallback)
	{
		return value == null || value.trim().length() == 0
				? fallback : value.trim();
	}

	private static RecommendationWorkQueueRegistry.QueueSnapshot
			inputQueueSnapshotFor(NodeExecutionMonitor.Snapshot snapshot)
	{
		if (snapshot == null || snapshot.getRunId() == null
				|| snapshot.getRunId().trim().length() == 0
				|| snapshot.getRequestedNodes() <= 0)
		{
			return null;
		}
		RecommendationWorkQueueRegistry.QueueSnapshot queue =
				RecommendationWorkQueueRegistry.snapshotInputBatchQueue(
						snapshot.getRunId(),
						Math.max(1, snapshot.getRequestedNodes()));
		return queue.getTotalShards() > 0 ? queue : null;
	}

	private static boolean hasInputQueue(
			RecommendationWorkQueueRegistry.QueueSnapshot queue)
	{
		return queue != null && queue.getTotalShards() > 0;
	}

	private static boolean isInputQueueOpen(
			RecommendationWorkQueueRegistry.QueueSnapshot queue)
	{
		return hasInputQueue(queue)
				&& (queue.getWaitingCount() > 0 || queue.getActiveCount() > 0)
				&& queue.getCompletedCount() < queue.getTotalShards();
	}

	private static int queueClaimedByNode(
			RecommendationWorkQueueRegistry.QueueSnapshot queue,
			int nodeIndex)
	{
		if (!hasInputQueue(queue))
		{
			return 0;
		}
		Integer value = queue.getClaimedByNode().get(Integer.valueOf(nodeIndex));
		return value == null ? 0 : Math.max(0, value.intValue());
	}

	private static int queueCompletedByNode(
			RecommendationWorkQueueRegistry.QueueSnapshot queue,
			int nodeIndex)
	{
		if (!hasInputQueue(queue))
		{
			return 0;
		}
		Integer value = queue.getCompletedByNode().get(Integer.valueOf(nodeIndex));
		return value == null ? 0 : Math.max(0, value.intValue());
	}

	private static int queueActiveByNode(
			RecommendationWorkQueueRegistry.QueueSnapshot queue,
			int nodeIndex)
	{
		if (!hasInputQueue(queue))
		{
			return 0;
		}
		List<Integer> active = queue.getActiveByNode().get(
				Integer.valueOf(nodeIndex));
		return active == null ? 0 : active.size();
	}

	private static int percentOf(int value, int total)
	{
		if (total <= 0)
		{
			return 0;
		}
		return Math.max(0, Math.min(100,
				(int)Math.round((value * 100.0) / total)));
	}

	private static boolean isRuntimeClosedInput(
			NodeExecutionMonitor.NodeSnapshot node)
	{
		return node != null
				&& ("Input queue closed".equals(node.getStage())
						|| "Preprocessing retained text".equals(node.getStage()));
	}

	private static String inputQueuePhaseText(
			NodeExecutionMonitor.Snapshot snapshot,
			RecommendationWorkQueueRegistry.QueueSnapshot queue)
	{
		if (!hasInputQueue(queue))
		{
			return snapshot == null
					? "Waiting for live input queue." : snapshot.getRunPhase();
		}
		if (isInputQueueOpen(queue))
		{
			return "Shared input queue: " + queue.getWaitingCount()
					+ " waiting, " + queue.getActiveCount()
					+ " in flight, " + queue.getCompletedCount() + "/"
					+ queue.getTotalShards() + " delivered.";
		}
		return "Input queue delivered all " + queue.getTotalShards()
				+ " batch(es); nodes are advancing with retained records.";
	}

	private static String inputQueueTooltip(
			NodeExecutionMonitor.Snapshot snapshot,
			RecommendationWorkQueueRegistry.QueueSnapshot queue)
	{
		String phase = snapshot == null ? "" : snapshot.getRunPhase();
		if (!hasInputQueue(queue))
		{
			return safe(phase, "No shared input queue is active.");
		}
		return "Input batches: " + queue.getCompletedCount()
				+ " delivered, " + queue.getActiveCount()
				+ " in flight, " + queue.getWaitingCount()
				+ " waiting, " + queue.getFailedCount()
				+ " failed. " + safe(phase, "");
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

	private void showNodeInspector(int nodeIndex)
	{
		Window owner = SwingUtilities.getWindowAncestor(this);
		if (inspectorDialog == null || inspectorDialog.getOwner() != owner)
		{
			if (inspectorDialog != null)
			{
				inspectorDialog.dispose();
			}
			inspectorDialog = new NodeInspectorDialog(owner);
		}
		inspectorDialog.setNodeIndex(nodeIndex);
		inspectorDialog.updateSnapshot(snapshot);
		inspectorDialog.setVisible(true);
		inspectorDialog.toFront();
	}

	private static NodeExecutionMonitor.NodeSnapshot findNode(
			NodeExecutionMonitor.Snapshot snapshot,
			int nodeIndex)
	{
		if (snapshot == null)
		{
			return null;
		}
		for (NodeExecutionMonitor.NodeSnapshot node : snapshot.getNodes())
		{
			if (node.getNodeIndex() == nodeIndex)
			{
				return node;
			}
		}
		return null;
	}

	private final class NodeCard extends JPanel
	{
		private final int nodeIndex;
		private final LockedLabel nodeLabel = new LockedLabel("");
		private final JLabel statusLabel = new Pill("");
		private final LockedLabel stageLabel = new LockedLabel("");
		private final LockedLabel detailLabel = new LockedLabel("");
		private final LockedLabel elapsedLabel = new LockedLabel("");
		private final LockedLabel resourceLabel = new LockedLabel("");
		private final JProgressBar progressBar = new JProgressBar(0, 100);
		private NodeExecutionMonitor.NodeSnapshot node;

		NodeCard(int nodeIndex)
		{
			super(new BorderLayout(0, 6));
			this.nodeIndex = nodeIndex;
			setOpaque(false);
			setBorder(BorderFactory.createEmptyBorder(11, 16, 11, 11));
			setPreferredSize(new Dimension(200, 172));
			setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			setToolTipText("Click to inspect Node " + nodeIndex);
			addMouseListener(new MouseAdapter()
			{
				public void mouseClicked(MouseEvent event)
				{
					showNodeInspector(NodeCard.this.nodeIndex);
				}
			});

			JPanel top = new JPanel();
			top.setOpaque(false);
			top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
			JPanel nameRow = new JPanel(new BorderLayout(6, 0));
			nameRow.setOpaque(false);
			nodeLabel.setText("Node " + nodeIndex);
			nodeLabel.setForeground(TEXT);
			nodeLabel.lockForeground();
			nodeLabel.setFont(NebulaDesign.fontSemiBold(12.5f));
			elapsedLabel.setForeground(FAINT);
			elapsedLabel.lockForeground();
			elapsedLabel.setFont(NebulaDesign.fontMono(9f));
			elapsedLabel.setHorizontalAlignment(SwingConstants.RIGHT);
			nameRow.add(nodeLabel, BorderLayout.WEST);
			nameRow.add(elapsedLabel, BorderLayout.EAST);
			statusLabel.setFont(NebulaDesign.fontMedium(8.5f));
			statusLabel.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
			JPanel statusRow = new JPanel(new BorderLayout());
			statusRow.setOpaque(false);
			statusRow.setBorder(BorderFactory.createEmptyBorder(5, 0, 0, 0));
			JPanel statusHolder = new JPanel(new GridBagLayout());
			statusHolder.setOpaque(false);
			statusHolder.add(statusLabel);
			statusRow.add(statusHolder, BorderLayout.WEST);
			nameRow.setAlignmentX(Component.LEFT_ALIGNMENT);
			statusRow.setAlignmentX(Component.LEFT_ALIGNMENT);
			nameRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
			statusRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
			top.add(nameRow);
			top.add(statusRow);

			JPanel center = new JPanel();
			center.setOpaque(false);
			center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
			stageLabel.setForeground(TEXT);
			stageLabel.lockForeground();
			stageLabel.setFont(NebulaDesign.fontSemiBold(12f));
			detailLabel.setForeground(MUTED);
			detailLabel.lockForeground();
			detailLabel.setFont(NebulaDesign.fontBody(10f));
			detailLabel.setVerticalAlignment(SwingConstants.TOP);
			progressBar.setBorderPainted(false);
			progressBar.setStringPainted(true);
			progressBar.setFont(NebulaDesign.fontMono(8.5f));
			progressBar.setOpaque(false);
			progressBar.setBackground(NebulaDesign.INSET);
			progressBar.setUI(new MeterUI());
			progressBar.setPreferredSize(new Dimension(160, 20));
			progressBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
			stageLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
			detailLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
			progressBar.setAlignmentX(Component.LEFT_ALIGNMENT);
			center.add(stageLabel);
			center.add(Box.createRigidArea(new Dimension(0, 3)));
			center.add(detailLabel);
			center.add(Box.createVerticalGlue());
			center.add(progressBar);

			JPanel footer = new JPanel(new BorderLayout(6, 0));
			footer.setOpaque(false);
			resourceLabel.setForeground(FAINT);
			resourceLabel.lockForeground();
			resourceLabel.setFont(NebulaDesign.fontSmall());
			resourceLabel.setHorizontalAlignment(SwingConstants.RIGHT);
			footer.add(resourceLabel, BorderLayout.EAST);

			add(top, BorderLayout.NORTH);
			add(center, BorderLayout.CENTER);
			add(footer, BorderLayout.SOUTH);
		}

		void update(
				NodeExecutionMonitor.NodeSnapshot node,
				RecommendationWorkQueueRegistry.QueueSnapshot inputQueue)
		{
			this.node = node;
			Color accent = colorFor(node.getState());
			statusLabel.setText(statusText(node.getState()));
			statusLabel.setForeground(accent);
			statusLabel.setBackground(new Color(
					accent.getRed(), accent.getGreen(), accent.getBlue(),
					StudioNightTheme.isNightMode() ? 46 : 28));
			stageLabel.setText(node.getStage());
			detailLabel.setText("<html><div style='width:142px'>"
					+ escape(node.getDetail()) + "</div></html>");
			progressBar.setForeground(accent);
			if (isInputQueueOpen(inputQueue)
					&& (node.getState() == NodeExecutionMonitor.State.RECEIVING
							|| node.getState()
									== NodeExecutionMonitor.State.WAITING_FOR_INPUT))
			{
				int claimed = queueClaimedByNode(inputQueue, nodeIndex);
				int done = queueCompletedByNode(inputQueue, nodeIndex);
				int active = queueActiveByNode(inputQueue, nodeIndex);
				int denominator = Math.max(claimed, done + active);
				progressBar.setValue(percentOf(done, denominator));
				progressBar.setString(denominator > 0
						? done + "/" + denominator + " batches"
						: "waiting for batches");
				progressBar.setVisible(true);
			}
			else if (node.getExpectedTweets() > 0)
			{
				progressBar.setValue(node.getProgressPercent());
				progressBar.setString(node.getProgressPercent() + "% "
						+ (isRuntimeClosedInput(node) ? "retained" : "input"));
				progressBar.setVisible(true);
			}
			else
			{
				progressBar.setVisible(false);
			}
			resourceLabel.setText(node.getReservationMb() > 0L
					? node.getReservationMb() + " MB reserved"
					: node.getCapacityProfile().shortLabel());
			setToolTipText("Click to inspect Node " + nodeIndex + " | "
					+ node.getCapacityProfile().compactSummary());
			refreshElapsed();
			repaint();
		}

		void refreshElapsed()
		{
			if (node == null)
			{
				elapsedLabel.setText("");
				return;
			}
			long end = node.getCompletedAt() > 0L
					? node.getCompletedAt() : System.currentTimeMillis();
			long start = node.getRunStartedAt() > 0L
					? node.getRunStartedAt() : node.getStageStartedAt();
			elapsedLabel.setText(formatDuration(end - start) + " elapsed");
		}

		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g2 = (Graphics2D)graphics.create();
			NebulaDesign.prepare(g2);
			Color accent = node == null ? FAINT : colorFor(node.getState());
			int radius = 12;
			if (!StudioNightTheme.isNightMode())
			{
				g2.setColor(new Color(23, 35, 60, 15));
				g2.fillRoundRect(1, 3, getWidth() - 3, getHeight() - 4,
						radius + 1, radius + 1);
			}
			g2.setColor(SURFACE);
			g2.fillRoundRect(0, 0, getWidth() - 2, getHeight() - 4,
					radius, radius);
			g2.setColor(NebulaDesign.withAlpha(accent, 190));
			g2.fillRoundRect(0, 6, 4, getHeight() - 16, 4, 4);
			g2.setColor(BORDER);
			g2.drawRoundRect(0, 0, getWidth() - 2, getHeight() - 4,
					radius, radius);
			g2.dispose();
			super.paintComponent(graphics);
		}
	}

	private final class NodeInspectorDialog extends JDialog
	{
		private final NodeInspectorPanel inspectorPanel =
				new NodeInspectorPanel();
		private int nodeIndex;

		NodeInspectorDialog(Window owner)
		{
			super(owner, "Node Inspector", Dialog.ModalityType.MODELESS);
			setDefaultCloseOperation(JDialog.HIDE_ON_CLOSE);
			setContentPane(inspectorPanel);
			setSize(1180, 820);
			setMinimumSize(new Dimension(1040, 700));
			setLocationRelativeTo(NodeExecutionDashboard.this);
		}

		void setNodeIndex(int nodeIndex)
		{
			this.nodeIndex = nodeIndex;
			setTitle("Node " + nodeIndex + " Inspector");
			inspectorPanel.setNodeIndex(nodeIndex);
			if (!isShowing())
			{
				setLocationRelativeTo(NodeExecutionDashboard.this);
			}
		}

		void updateSnapshot(NodeExecutionMonitor.Snapshot snapshot)
		{
			inspectorPanel.updateSnapshot(snapshot, findNode(snapshot, nodeIndex));
		}

		void tick()
		{
			if (isVisible())
			{
				inspectorPanel.tick();
			}
		}
	}

	private static final class NodeInspectorPanel extends JPanel
	{
		private final HeroPanel heroPanel = new HeroPanel();
		private final MetricStrip metricStrip = new MetricStrip();
		private final PipelinePanel pipelinePanel = new PipelinePanel();
		private final InfoSection dataSection =
				new InfoSection("Data And Model Shape");
		private final InfoSection timingSection =
				new InfoSection("Timing And Transport");
		private final InfoSection outputSection =
				new InfoSection("Output And Safety");
		private final InfoSection capacitySection =
				new InfoSection("Capacity Contract");
		private final TimelinePanel timelinePanel = new TimelinePanel();
		private int nodeIndex;
		private NodeExecutionMonitor.Snapshot snapshot;
		private NodeExecutionMonitor.NodeSnapshot node;

		NodeInspectorPanel()
		{
			super(new BorderLayout());
			setBackground(BACKGROUND);
			setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

			JPanel content = new ScrollableColumn();
			content.setOpaque(false);
			content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
			content.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 18));
			content.add(heroPanel);
			content.add(Box.createRigidArea(new Dimension(0, 12)));
			content.add(metricStrip);
			content.add(Box.createRigidArea(new Dimension(0, 12)));
			content.add(pipelinePanel);
			content.add(Box.createRigidArea(new Dimension(0, 12)));

			JPanel sections = new JPanel(new GridLayout(1, 4, 12, 0));
			sections.setOpaque(false);
			sections.add(dataSection);
			sections.add(timingSection);
			sections.add(outputSection);
			sections.add(capacitySection);
			content.add(sections);
			content.add(Box.createRigidArea(new Dimension(0, 12)));
			content.add(timelinePanel);

			JScrollPane scrollPane = new JScrollPane(content);
			scrollPane.setBorder(BorderFactory.createEmptyBorder());
			scrollPane.getViewport().setBackground(BACKGROUND);
			scrollPane.getVerticalScrollBar().setUnitIncrement(18);
			scrollPane.getVerticalScrollBar().setUI(
					new NebulaWidgets.PillScrollBarUI());
			scrollPane.getVerticalScrollBar().setOpaque(false);
			scrollPane.setHorizontalScrollBarPolicy(
					ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
			add(scrollPane, BorderLayout.CENTER);
		}

		void setNodeIndex(int nodeIndex)
		{
			this.nodeIndex = nodeIndex;
			heroPanel.setFallbackNodeIndex(nodeIndex);
		}

		void updateSnapshot(
				NodeExecutionMonitor.Snapshot snapshot,
				NodeExecutionMonitor.NodeSnapshot node)
		{
			this.snapshot = snapshot;
			this.node = node;
			heroPanel.update(snapshot, node, nodeIndex);
			metricStrip.update(node, snapshot);
			pipelinePanel.update(snapshot, node);
			updateSections(node);
			timelinePanel.update(snapshot, node);
		}

		void tick()
		{
			heroPanel.repaint();
			metricStrip.update(node, snapshot);
			pipelinePanel.advance();
			timelinePanel.repaint();
		}

		private void updateSections(NodeExecutionMonitor.NodeSnapshot node)
		{
			dataSection.clearRows();
			timingSection.clearRows();
			outputSection.clearRows();
			capacitySection.clearRows();
			if (node == null)
			{
				dataSection.addRow("Node", "Waiting for telemetry");
				timingSection.addRow("Stage", "-");
				outputSection.addRow("Safety", "No node selected");
				capacitySection.addRow("Capacity", "Waiting for node profile");
				return;
			}

			NodeExecutionMonitor.NodeMetrics metrics = node.getMetrics();
			NodeCapacityProfile capacityProfile = node.getCapacityProfile();
			RecommendationWorkQueueRegistry.QueueSnapshot inputQueue =
					inputQueueSnapshotFor(snapshot);
			dataSection.addRow(
					isRuntimeClosedInput(node)
							? "Retained records" : "Received records",
					formatCount(node.getReceivedTweets()));
			dataSection.addRow(
					isRuntimeClosedInput(node)
							? "Input total meaning" : "Expected records",
					isRuntimeClosedInput(node)
							? "Live-routed final count"
							: formatCount(node.getExpectedTweets()));
			if (hasInputQueue(inputQueue))
			{
				int claimed = queueClaimedByNode(
						inputQueue, node.getNodeIndex());
				int done = queueCompletedByNode(
						inputQueue, node.getNodeIndex());
				int active = queueActiveByNode(
						inputQueue, node.getNodeIndex());
				dataSection.addRow("Shared input queue",
						inputQueue.getCompletedCount() + " / "
								+ inputQueue.getTotalShards()
								+ " batches delivered");
				dataSection.addRow("Node input batches",
						done + " done, " + active + " in flight, "
								+ Math.max(0, claimed - done - active)
								+ " previously claimed");
			}
			dataSection.addRow("Usable records",
					formatCount(metrics.getUsableRecords()));
			dataSection.addRow("User documents",
					formatCount(metrics.getUserDocuments()));
			dataSection.addRow("Registered users",
					formatCount(metrics.getRegisteredUsers()));
			dataSection.addRow("Feature documents",
					formatCount(metrics.getFeatureDocuments()));
			dataSection.addRow("Feature terms",
					formatCount(metrics.getFeatureCount()));
			dataSection.addRow("Classes / followees",
					formatCount(metrics.getClassCount()));
			dataSection.addRow("Target users",
					formatCount(metrics.getTargetCount()));
			dataSection.addRow("Model",
					safe(metrics.getModelName(), snapshot == null
							? "Selected algorithm" : snapshot.getAlgorithmName()));

			timingSection.addRow("Message bytes",
					formatCount(metrics.getMessageBytes()));
			timingSection.addRow("Message time",
					formatMillis(metrics.getMessageMillis()));
			timingSection.addRow("Text processing",
					formatMillis(metrics.getTextProcessingMillis()));
			timingSection.addRow("TF-IDF / features",
					formatMillis(metrics.getTfidfMillis()));
			timingSection.addRow("Algorithm",
					formatMillis(metrics.getAlgorithmMillis()));
			timingSection.addRow("Train stage",
					formatMillis(metrics.getTrainMillis()));
			timingSection.addRow("Test stage",
					formatMillis(metrics.getTestMillis()));
			timingSection.addRow("Current stage age",
					formatDuration(currentStageMillis(node)));
			timingSection.addRow("Total node age",
					formatDuration(currentRunMillis(node)));
			timingSection.addRow("Heap reservation",
					node.getReservationMb() > 0L
							? node.getReservationMb() + " MB" : "none");

			outputSection.addRow("Output mode",
					safe(metrics.getOutputMode(), "Waiting for publication"));
			outputSection.addRow("Score target maps",
					formatCount(metrics.getScoreTargets()));
			outputSection.addRow("Candidate scores",
					formatCount(metrics.getScoreCandidates()));
			outputSection.addRow("Current state",
					statusText(node.getState()));
			outputSection.addRow("Current stage", node.getStage());
			outputSection.addRow("Stage detail", node.getDetail());
			outputSection.addRow("Model detail",
					safe(metrics.getModelDetail(), "Not reported yet"));
			outputSection.addRow("Failure detail",
					node.getState() == NodeExecutionMonitor.State.FAILED
							? node.getDetail() : "No failure reported");

			capacitySection.addRow("Role",
					capacityProfile.getRoleLabel());
			capacitySection.addRow("Role meaning",
					capacityProfile.getRoleDescription());
			capacitySection.addRow("CPU multiplier",
					String.format(Locale.US, "%.2fx",
							capacityProfile.getCpuMultiplier()));
			capacitySection.addRow("Memory limit",
					RuntimeResourceSettings.formatMegabytes(
							capacityProfile.getMemoryLimitMb()));
			capacitySection.addRow("Max concurrent jobs",
					formatCount(capacityProfile.getMaxConcurrentJobs()));
			capacitySection.addRow("Queue capacity",
					formatCount(capacityProfile.getQueueCapacity()));
			capacitySection.addRow("Network delay",
					capacityProfile.getNetworkDelayMs() + " ms");
			capacitySection.addRow("Slowdown simulation",
					capacityProfile.isSlowdownSimulationEnabled()
							? String.format(Locale.US, "%.2fx",
									capacityProfile.getSlowdownMultiplier())
							: "off");
			capacitySection.addRow("Failure simulation",
					capacityProfile.isFailureSimulationEnabled()
							? capacityProfile.getFailureProbabilityPercent()
									+ "% configured"
							: "off");
			capacitySection.addRow("Scheduling weight",
					String.format(Locale.US, "%.2f",
							NodeCapacityScheduler.schedulingWeight(capacityProfile)));
			capacitySection.addRow("Live scoring effect",
					"Used for capacity-aware work assignment; candidate scoring formulas stay unchanged.");
		}

		private long currentStageMillis(NodeExecutionMonitor.NodeSnapshot node)
		{
			if (node == null || node.getStageStartedAt() <= 0L)
			{
				return 0L;
			}
			long end = node.getCompletedAt() > 0L
					? node.getCompletedAt() : System.currentTimeMillis();
			return Math.max(0L, end - node.getStageStartedAt());
		}

		private long currentRunMillis(NodeExecutionMonitor.NodeSnapshot node)
		{
			if (node == null)
			{
				return 0L;
			}
			long start = node.getRunStartedAt() > 0L
					? node.getRunStartedAt() : node.getStageStartedAt();
			long end = node.getCompletedAt() > 0L
					? node.getCompletedAt() : System.currentTimeMillis();
			return Math.max(0L, end - start);
		}
	}

	private static final class HeroPanel extends JPanel
	{
			private final LockedLabel title = new LockedLabel("");
			private final LockedLabel subtitle = new LockedLabel("");
			private final JLabel badge = new Pill("");
			private final LockedLabel detail = new LockedLabel("");
			private final JPanel orbitSpacer = new JPanel();
			private NodeExecutionMonitor.NodeSnapshot node;
			private int fallbackNodeIndex;
			private float phase;

		HeroPanel()
		{
			super(new BorderLayout(16, 0));
			setOpaque(false);
			setBorder(BorderFactory.createEmptyBorder(20, 22, 20, 22));
			setPreferredSize(new Dimension(760, 132));
			setMaximumSize(new Dimension(Integer.MAX_VALUE, 132));

			JPanel copy = new JPanel();
			copy.setOpaque(false);
			copy.setLayout(new BoxLayout(copy, BoxLayout.Y_AXIS));
			title.setForeground(TEXT);
			title.lockForeground();
			title.setFont(NebulaDesign.fontSemiBold(22f));
			subtitle.setForeground(MUTED);
			subtitle.lockForeground();
			subtitle.setFont(NebulaDesign.fontMedium(12f));
			detail.setForeground(MUTED);
			detail.lockForeground();
			detail.setFont(NebulaDesign.fontBody(12f));
			badge.setForeground(BLUE);
			badge.setFont(NebulaDesign.fontMedium(10.5f));
			badge.setHorizontalAlignment(SwingConstants.CENTER);
			badge.setBorder(BorderFactory.createEmptyBorder(4, 11, 4, 11));

			JPanel metaRow = new JPanel();
			metaRow.setOpaque(false);
			metaRow.setLayout(new BoxLayout(metaRow, BoxLayout.X_AXIS));
			metaRow.add(subtitle);
			metaRow.add(Box.createRigidArea(new Dimension(12, 0)));
			metaRow.add(badge);
			title.setAlignmentX(Component.LEFT_ALIGNMENT);
			metaRow.setAlignmentX(Component.LEFT_ALIGNMENT);
			detail.setAlignmentX(Component.LEFT_ALIGNMENT);

			orbitSpacer.setOpaque(false);
			orbitSpacer.setPreferredSize(new Dimension(132, 1));
			orbitSpacer.setMinimumSize(new Dimension(132, 1));
			copy.add(title);
			copy.add(Box.createRigidArea(new Dimension(0, 5)));
			copy.add(metaRow);
			copy.add(Box.createRigidArea(new Dimension(0, 12)));
			copy.add(detail);
				add(copy, BorderLayout.CENTER);
				add(orbitSpacer, BorderLayout.EAST);
			}

		void setFallbackNodeIndex(int fallbackNodeIndex)
		{
			this.fallbackNodeIndex = fallbackNodeIndex;
		}

		void update(
				NodeExecutionMonitor.Snapshot snapshot,
				NodeExecutionMonitor.NodeSnapshot node,
				int nodeIndex)
		{
			this.node = node;
			this.fallbackNodeIndex = nodeIndex;
			String algorithm = snapshot == null
					? "Recommendation model" : snapshot.getAlgorithmName();
			title.setText("Node " + nodeIndex + " Inspector");
			if (node == null)
			{
				subtitle.setText(algorithm + " | waiting for node telemetry");
				detail.setText("Click another node or start a run to inspect live execution.");
				badge.setText("NO SNAPSHOT");
				badge.setForeground(MUTED);
				badge.setBackground(SURFACE_ALT);
			}
			else
			{
				subtitle.setText(algorithm + " | " + node.getName()
						+ " | " + node.getCapacityProfile().shortLabel());
				detail.setText(node.getStage() + " - " + node.getDetail());
				badge.setText(statusText(node.getState()));
				Color accent = colorFor(node.getState());
				badge.setForeground(accent);
				badge.setBackground(new Color(
						accent.getRed(), accent.getGreen(), accent.getBlue(),
						StudioNightTheme.isNightMode() ? 46 : 28));
			}
			repaint();
		}

		protected void paintComponent(Graphics graphics)
		{
			phase += 0.018f;
			Graphics2D g2 = (Graphics2D)graphics.create();
			NebulaDesign.prepare(g2);
			int width = getWidth();
			int height = getHeight();
			Color accent = node == null
					? BLUE : colorFor(node.getState());
			int radius = NebulaDesign.RADIUS_CARD;
			g2.setColor(SURFACE);
			g2.fillRoundRect(0, 0, width - 1, height - 1, radius, radius);
			GradientPaint paint = new GradientPaint(
					0, 0, NebulaDesign.withAlpha(accent, 0),
					width, height, NebulaDesign.withAlpha(accent,
							StudioNightTheme.isNightMode() ? 40 : 26));
			g2.setPaint(paint);
			g2.fillRoundRect(0, 0, width - 1, height - 1, radius, radius);
			g2.setColor(BORDER);
			g2.drawRoundRect(0, 0, width - 1, height - 1, radius, radius);

			int cx = width - getInsets().right - orbitSpacer.getWidth() / 2;
			if (orbitSpacer.getWidth() <= 0)
			{
				cx = width - getInsets().right - 66;
			}
			int cy = height / 2 + Math.round(
					(float)Math.sin(phase * Math.PI * 2.0) * 3.0f);
			g2.setColor(NebulaDesign.withAlpha(accent, 34));
			g2.fillOval(cx - 42, cy - 42, 84, 84);
			g2.setColor(NebulaDesign.withAlpha(accent, 210));
			g2.setStroke(new BasicStroke(3f));
			g2.drawOval(cx - 31, cy - 31, 62, 62);
			g2.setFont(NebulaDesign.fontSemiBold(18f));
			g2.setColor(accent);
			String index = Integer.toString(
					node == null ? fallbackNodeIndex : node.getNodeIndex());
			int textWidth = g2.getFontMetrics().stringWidth(index);
			g2.drawString(index, cx - textWidth / 2, cy + 7);
			g2.dispose();
			super.paintComponent(graphics);
		}
	}

	private static final class MetricStrip extends JPanel
	{
		private final MiniMetric progress =
				new MiniMetric("Input progress", "-", "records");
		private final MiniMetric stage =
				new MiniMetric("Stage time", "-", "current step");
		private final MiniMetric run =
				new MiniMetric("Run time", "-", "this node");
		private final MiniMetric memory =
				new MiniMetric("Memory", "-", "reservation");
		private final MiniMetric output =
				new MiniMetric("Output", "-", "score maps");

		MetricStrip()
		{
			super(new GridLayout(1, 5, 10, 0));
			setOpaque(false);
			setMaximumSize(new Dimension(Integer.MAX_VALUE, 84));
			add(progress);
			add(stage);
			add(run);
			add(memory);
			add(output);
		}

		void update(
				NodeExecutionMonitor.NodeSnapshot node,
				NodeExecutionMonitor.Snapshot snapshot)
		{
			if (node == null)
			{
				progress.setValue("-", "waiting");
				stage.setValue("-", "waiting");
				run.setValue("-", "waiting");
				memory.setValue("-", "none");
				output.setValue("-", "not published");
				return;
			}
			NodeExecutionMonitor.NodeMetrics metrics = node.getMetrics();
			NodeCapacityProfile capacityProfile = node.getCapacityProfile();
			RecommendationWorkQueueRegistry.QueueSnapshot inputQueue =
					inputQueueSnapshotFor(snapshot);
			if (isInputQueueOpen(inputQueue)
					&& (node.getState() == NodeExecutionMonitor.State.RECEIVING
							|| node.getState()
									== NodeExecutionMonitor.State.WAITING_FOR_INPUT))
			{
				int claimed = queueClaimedByNode(
						inputQueue, node.getNodeIndex());
				int done = queueCompletedByNode(
						inputQueue, node.getNodeIndex());
				int active = queueActiveByNode(
						inputQueue, node.getNodeIndex());
				int denominator = Math.max(claimed, done + active);
				progress.setValue(percentOf(done, denominator) + "%",
						denominator > 0
								? done + " / " + denominator
										+ " input batches"
								: "waiting for input batches");
			}
			else if (isRuntimeClosedInput(node))
			{
				progress.setValue("100%",
						formatCount(node.getReceivedTweets())
								+ " records retained");
			}
			else
			{
				progress.setValue(node.getProgressPercent() + "%",
						formatCount(node.getReceivedTweets()) + " / "
								+ formatCount(node.getExpectedTweets()));
			}
			stage.setValue(formatDuration(stageMillis(node)), node.getStage());
			run.setValue(formatDuration(runMillis(node)), statusText(node.getState()));
			memory.setValue(node.getReservationMb() > 0L
					? node.getReservationMb() + " MB"
					: RuntimeResourceSettings.formatMegabytes(
							capacityProfile.getMemoryLimitMb()),
					node.getState() == NodeExecutionMonitor.State.WAITING_FOR_MEMORY
							? "safe-wave queue"
							: node.getReservationMb() > 0L
									? "current lease"
									: "capacity budget");
			output.setValue(formatCount(metrics.getScoreTargets()),
					formatCount(metrics.getScoreCandidates()) + " candidates");
		}

		private long stageMillis(NodeExecutionMonitor.NodeSnapshot node)
		{
			if (node.getStageStartedAt() <= 0L)
			{
				return 0L;
			}
			long end = node.getCompletedAt() > 0L
					? node.getCompletedAt() : System.currentTimeMillis();
			return Math.max(0L, end - node.getStageStartedAt());
		}

		private long runMillis(NodeExecutionMonitor.NodeSnapshot node)
		{
			long start = node.getRunStartedAt() > 0L
					? node.getRunStartedAt() : node.getStageStartedAt();
			long end = node.getCompletedAt() > 0L
					? node.getCompletedAt() : System.currentTimeMillis();
			return Math.max(0L, end - start);
		}
	}

	private static final class MiniMetric extends JPanel
	{
		private final LockedLabel title = new LockedLabel("");
		private final LockedLabel value = new LockedLabel("");
		private final LockedLabel detail = new LockedLabel("");

		MiniMetric(String titleText, String valueText, String detailText)
		{
			super(new BorderLayout(0, 2));
			setOpaque(false);
			setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
			title.setText(titleText.toUpperCase(Locale.US));
			title.setForeground(FAINT);
			title.lockForeground();
			title.setFont(NebulaDesign.fontSection());
			value.setText(valueText);
			value.setForeground(TEXT);
			value.lockForeground();
			value.setFont(NebulaDesign.fontSemiBold(20f));
			detail.setText(detailText);
			detail.setForeground(FAINT);
			detail.lockForeground();
			detail.setFont(NebulaDesign.fontSmall());
			add(title, BorderLayout.NORTH);
			add(value, BorderLayout.CENTER);
			add(detail, BorderLayout.SOUTH);
		}

		void setValue(String valueText, String detailText)
		{
			value.setText(valueText);
			detail.setText(detailText);
		}

		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g2 = (Graphics2D)graphics.create();
			NebulaDesign.prepare(g2);
			g2.setColor(SURFACE);
			g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
			g2.setColor(BORDER);
			g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
			g2.dispose();
			super.paintComponent(graphics);
		}
	}

	private static final class InfoSection extends JPanel
	{
		private final JPanel rows = new JPanel();
		private final List<JLabel> rowLabels = new ArrayList<JLabel>();

		InfoSection(String title)
		{
			super(new BorderLayout(0, 10));
			setOpaque(false);
			setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
			LockedLabel label = new LockedLabel(title);
			label.setForeground(TEXT);
			label.lockForeground();
			label.setFont(NebulaDesign.fontSemiBold(13.5f));
			rows.setOpaque(false);
			rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));
			add(label, BorderLayout.NORTH);
			add(rows, BorderLayout.CENTER);
		}

		void clearRows()
		{
			rows.removeAll();
			rowLabels.clear();
		}

		void addRow(String name, String value)
		{
			LockedLabel row = new LockedLabel(
					"<html><div style='width:196px'><b>"
					+ escape(name) + "</b><br>"
					+ escape(safe(value, "-")) + "</div></html>");
			row.setFont(NebulaDesign.fontBody(11f));
			row.setForeground(MUTED);
			row.lockForeground();
			row.setBorder(BorderFactory.createCompoundBorder(
					BorderFactory.createMatteBorder(0, 0, 1, 0,
							NebulaDesign.BORDER_SUBTLE),
					BorderFactory.createEmptyBorder(6, 0, 6, 0)));
			rowLabels.add(row);
			rows.add(row);
			rows.revalidate();
			rows.repaint();
		}

		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g2 = (Graphics2D)graphics.create();
			NebulaDesign.prepare(g2);
			g2.setColor(SURFACE);
			g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
			g2.setColor(BORDER);
			g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
			g2.dispose();
			super.paintComponent(graphics);
		}
	}

	private static final class PipelinePanel extends JPanel
	{
		private static final String[] STEPS =
				{"Input", "Text", "Features", "Model", "Publish"};
		private NodeExecutionMonitor.Snapshot snapshot;
		private NodeExecutionMonitor.NodeSnapshot node;
		private float phase;

		PipelinePanel()
		{
			setOpaque(false);
			setPreferredSize(new Dimension(760, 150));
			setMaximumSize(new Dimension(Integer.MAX_VALUE, 150));
		}

		void update(
				NodeExecutionMonitor.Snapshot snapshot,
				NodeExecutionMonitor.NodeSnapshot node)
		{
			this.snapshot = snapshot;
			this.node = node;
			repaint();
		}

		void advance()
		{
			phase += 0.035f;
			if (phase > 1.0f)
			{
				phase = 0.0f;
			}
			repaint();
		}

		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g2 = (Graphics2D)graphics.create();
			NebulaDesign.prepare(g2);
			int width = getWidth();
			int height = getHeight();
			g2.setColor(SURFACE);
			g2.fillRoundRect(0, 0, width - 1, height - 1, 12, 12);
			g2.setColor(BORDER);
			g2.drawRoundRect(0, 0, width - 1, height - 1, 12, 12);
			g2.setFont(NebulaDesign.fontSemiBold(13.5f));
			g2.setColor(TEXT);
			g2.drawString("Live Execution Path", 18, 28);
			g2.setFont(NebulaDesign.fontBody(11f));
			g2.setColor(MUTED);
			g2.drawString(snapshot == null
					? "Waiting for a run."
					: snapshot.getRunPhase(), 18, 46);

			int activeStep = activeStep(node);
			int left = 24;
			int right = width - 24;
			int top = 72;
			int boxWidth = Math.max(88,
					(right - left - (STEPS.length - 1) * 16) / STEPS.length);
			int boxHeight = 50;
			for (int i = 0; i < STEPS.length; i++)
			{
				int x = left + i * (boxWidth + 16);
				if (i > 0)
				{
					int lineY = top + boxHeight / 2;
					g2.setStroke(new BasicStroke(2f));
					g2.setColor(i <= activeStep
							? NebulaDesign.withAlpha(BLUE, 120)
							: NebulaDesign.BORDER_STRONG);
					g2.drawLine(x - 16, lineY, x, lineY);
					if (i <= activeStep)
					{
						int dotX = x - 16 + Math.round(
								(float)Math.abs(Math.sin(
										(phase + i * 0.11f) * Math.PI * 2.0))
										* 16.0f);
						g2.setColor(BLUE);
						g2.fillOval(dotX - 3, lineY - 3, 6, 6);
					}
				}
				Color accent = i == activeStep
						? colorFor(node == null
								? NodeExecutionMonitor.State.PROVISIONING
								: node.getState())
						: i < activeStep ? GREEN : FAINT;
				g2.setColor(i <= activeStep
						? NebulaDesign.withAlpha(accent,
								StudioNightTheme.isNightMode() ? 44 : 32)
						: SURFACE_ALT);
				g2.fillRoundRect(x, top, boxWidth, boxHeight, 12, 12);
				g2.setColor(i == activeStep ? accent : BORDER);
				g2.drawRoundRect(x, top, boxWidth, boxHeight, 12, 12);
				g2.setColor(accent);
				g2.fillOval(x + 12, top + 19, 10, 10);
				g2.setColor(TEXT);
				g2.setFont(NebulaDesign.fontMedium(12f));
				g2.drawString(STEPS[i], x + 30, top + 23);
				g2.setColor(MUTED);
				g2.setFont(NebulaDesign.fontBody(10f));
				g2.drawString(stepDetail(i), x + 30, top + 39);
			}
			g2.dispose();
		}

		private int activeStep(NodeExecutionMonitor.NodeSnapshot node)
		{
			if (node == null)
			{
				return 0;
			}
			switch (node.getState())
			{
				case RECEIVING:
				case WAITING_FOR_INPUT:
					return 0;
				case PREPROCESSING:
				case WAITING_FOR_PEERS:
					return 1;
				case FEATURES:
				case WAITING_FOR_MEMORY:
					return 2;
				case WAITING_FOR_SHARED_MODEL:
				case MODEL_RUNNING:
					return 3;
				case PUBLISHING:
				case COMPLETE:
					return 4;
				case FAILED:
				case STOPPED:
					return Math.max(0, Math.min(4, 3));
				default:
					return 0;
			}
		}

		private String stepDetail(int step)
		{
			switch (step)
			{
				case 0:
					return node == null ? "records" : node.getProgressPercent() + "% input";
				case 1:
					return "clean and aggregate";
				case 2:
					return "TF-IDF / vectors";
				case 3:
					return snapshot == null ? "model" : snapshot.getAlgorithmName();
				default:
					return "score map";
			}
		}
	}

	private static final class TimelinePanel extends JPanel
	{
		private NodeExecutionMonitor.Snapshot snapshot;
		private NodeExecutionMonitor.NodeSnapshot node;

		TimelinePanel()
		{
			setOpaque(false);
			setPreferredSize(new Dimension(760, 250));
			setMaximumSize(new Dimension(Integer.MAX_VALUE, 260));
		}

		void update(
				NodeExecutionMonitor.Snapshot snapshot,
				NodeExecutionMonitor.NodeSnapshot node)
		{
			this.snapshot = snapshot;
			this.node = node;
			repaint();
		}

		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g2 = (Graphics2D)graphics.create();
			NebulaDesign.prepare(g2);
			int width = getWidth();
			int height = getHeight();
			g2.setColor(SURFACE);
			g2.fillRoundRect(0, 0, width - 1, height - 1, 12, 12);
			g2.setColor(BORDER);
			g2.drawRoundRect(0, 0, width - 1, height - 1, 12, 12);
			g2.setFont(NebulaDesign.fontSemiBold(13.5f));
			g2.setColor(TEXT);
			g2.drawString("Node Timeline", 18, 28);
			g2.setFont(NebulaDesign.fontBody(11f));
			g2.setColor(MUTED);
			g2.drawString(snapshot == null
					? "Recent node events appear here."
					: "Last " + (node == null ? 0 : node.getEvents().size())
							+ " state changes for this run.", 18, 46);

			if (node == null || node.getEvents().isEmpty())
			{
				g2.setColor(FAINT);
				g2.drawString("No timeline events have been reported yet.", 18, 92);
				g2.dispose();
				return;
			}

			List<NodeExecutionMonitor.NodeEvent> events = node.getEvents();
			int start = Math.max(0, events.size() - 8);
			int y = 76;
			for (int i = start; i < events.size(); i++)
			{
				NodeExecutionMonitor.NodeEvent event = events.get(i);
				long baseline = node.getRunStartedAt() > 0L
						? node.getRunStartedAt()
						: events.get(0).getTime();
				Color accent = colorFor(event.getState());
				g2.setColor(NebulaDesign.withAlpha(accent, 34));
				g2.fillOval(20, y - 10, 20, 20);
				g2.setColor(accent);
				g2.fillOval(26, y - 4, 8, 8);
				if (i < events.size() - 1)
				{
					g2.setColor(BORDER);
					g2.drawLine(30, y + 10, 30, y + 28);
				}
				g2.setFont(NebulaDesign.fontMedium(12f));
				g2.setColor(TEXT);
				g2.drawString(event.getStage(), 52, y - 1);
				g2.setFont(NebulaDesign.fontBody(10f));
				g2.setColor(MUTED);
				String detail = safe(event.getDetail(), statusText(event.getState()));
				if (detail.length() > 96)
				{
					detail = detail.substring(0, 93) + "...";
				}
				g2.drawString(detail, 52, y + 14);
				g2.setColor(FAINT);
				g2.setFont(NebulaDesign.fontMono(10f));
				g2.drawString(formatDuration(Math.max(0L,
						event.getTime() - baseline)), width - 82, y + 6);
				y += 28;
			}
			g2.dispose();
		}
	}

	private static final class NodeTopologyPanel extends JPanel
	{
		private NodeExecutionMonitor.Snapshot snapshot =
				NodeExecutionMonitor.snapshot();
		private float phase;

		NodeTopologyPanel()
		{
			setOpaque(false);
			setPreferredSize(new Dimension(420, 58));
			setMinimumSize(new Dimension(200, 58));
			setMaximumSize(new Dimension(Integer.MAX_VALUE, 58));
		}

		void setSnapshot(NodeExecutionMonitor.Snapshot snapshot)
		{
			this.snapshot = snapshot;
			repaint();
		}

		void advanceAnimation()
		{
			phase += 0.14f;
			if (phase > 1.0f)
			{
				phase = 0.0f;
			}
			repaint();
		}

		protected void paintComponent(Graphics graphics)
		{
			super.paintComponent(graphics);
			Graphics2D g2 = (Graphics2D)graphics.create();
			NebulaDesign.prepare(g2);
			int width = getWidth();
			int height = getHeight();
			g2.setColor(NebulaDesign.INSET);
			g2.fillRoundRect(0, 0, width - 1, height - 1, 10, 10);
			g2.setColor(BORDER);
			g2.drawRoundRect(0, 0, width - 1, height - 1, 10, 10);

			int count = snapshot == null ? 0 : snapshot.getNodes().size();
			if (count == 0)
			{
				g2.setColor(FAINT);
				g2.setFont(NebulaDesign.fontBody(11f));
				g2.drawString("Node activity will appear after initialization.", 14, 34);
				g2.dispose();
				return;
			}

			int pad = 20;
			int available = Math.max(1, width - pad * 2);
			int y = height / 2;
			g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND,
					BasicStroke.JOIN_ROUND));
			g2.setColor(NebulaDesign.withAlpha(NebulaDesign.BORDER_STRONG, 140));
			g2.drawLine(pad, y, width - pad, y);

			for (int index = 0; index < count; index++)
			{
				NodeExecutionMonitor.NodeSnapshot node =
						snapshot.getNodes().get(index);
				int x = count == 1 ? width / 2
						: pad + Math.round(index * available / (float)(count - 1));
				Color color = colorFor(node.getState());
				int pulse = node.isActive()
						? Math.round(3.0f + (float)Math.sin(
								phase * Math.PI * 2.0 + index) * 2.0f)
						: 2;
				g2.setColor(NebulaDesign.withAlpha(color, 38));
				g2.fillOval(x - 8 - pulse, y - 8 - pulse,
						16 + pulse * 2, 16 + pulse * 2);
				g2.setColor(color);
				g2.fillOval(x - 7, y - 7, 14, 14);
				g2.setColor(NebulaDesign.SURFACE);
				g2.fillOval(x - 3, y - 3, 6, 6);
				g2.setColor(MUTED);
				g2.setFont(NebulaDesign.fontMono(8.5f));
				String label = Integer.toString(node.getNodeIndex());
				int labelWidth = g2.getFontMetrics().stringWidth(label);
				g2.drawString(label, x - labelWidth / 2, y + 20);
			}
			g2.dispose();
		}
	}

	/** Column panel that always matches the viewport width (no horizontal scroll). */
	private static final class ScrollableColumn extends JPanel
			implements javax.swing.Scrollable
	{
		public Dimension getPreferredScrollableViewportSize()
		{
			return getPreferredSize();
		}

		public int getScrollableUnitIncrement(
				java.awt.Rectangle visibleRect,
				int orientation,
				int direction)
		{
			return 18;
		}

		public int getScrollableBlockIncrement(
				java.awt.Rectangle visibleRect,
				int orientation,
				int direction)
		{
			return 90;
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

	/** Soft-tint status pill label; colors come from set background/foreground. */
	private static final class Pill extends JLabel
	{
		Pill(String text)
		{
			super(text);
			setOpaque(false);
			setFont(NebulaDesign.fontSmall());
			setBorder(BorderFactory.createEmptyBorder(4, 12, 4, 12));
		}

		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g2 = (Graphics2D)graphics.create();
			NebulaDesign.prepare(g2);
			if (isBackgroundSet())
			{
				g2.setColor(getBackground());
				int h = getHeight();
				g2.fillRoundRect(0, 0, getWidth(), h, h, h);
			}
			g2.dispose();
			super.paintComponent(graphics);
		}
	}

	/** Label that keeps its adaptive foreground through theme-walk repaints. */
	private static final class LockedLabel extends JLabel
	{
		private boolean locked;

		LockedLabel(String text)
		{
			super(text);
		}

		void lockForeground()
		{
			locked = true;
		}

		public void setForeground(Color color)
		{
			if (!locked)
			{
				super.setForeground(color);
			}
		}
	}

	/** Slim rounded meter with a mono caption above the track. */
	private static final class MeterUI
			extends javax.swing.plaf.basic.BasicProgressBarUI
	{
		public void paint(Graphics graphics, javax.swing.JComponent component)
		{
			Graphics2D g2 = (Graphics2D)graphics.create();
			NebulaDesign.prepare(g2);
			JProgressBar bar = (JProgressBar)component;
			int w = component.getWidth();
			int h = component.getHeight();
			int trackHeight = 6;
			int trackY = h - trackHeight - 1;
			String text = bar.isStringPainted() ? bar.getString() : null;
			if (text != null && text.length() > 0)
			{
				g2.setFont(bar.getFont());
				g2.setColor(MUTED);
				g2.drawString(text, 0, Math.max(9, trackY - 4));
			}
			g2.setColor(NebulaDesign.INSET);
			g2.fillRoundRect(0, trackY, w, trackHeight, trackHeight, trackHeight);
			int fillWidth = (int)Math.round(w * bar.getPercentComplete());
			if (fillWidth > 0)
			{
				g2.setColor(bar.getForeground());
				g2.fillRoundRect(0, trackY, Math.max(trackHeight, fillWidth),
						trackHeight, trackHeight, trackHeight);
			}
			g2.dispose();
		}
	}
}
