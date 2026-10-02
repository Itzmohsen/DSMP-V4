package TwitterGatherDataFollowers.userRyersonU;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.border.EmptyBorder;

/**
 * Step 4: the cluster the run uses, the three steps of a run, and the live
 * output (timing, recommendations and the nodes) while it runs.
 */
final class SimulatorRunPage extends SimulatorPanel.Page
{
	private static final long serialVersionUID = 1L;

	private final SimulatorPanel simulator;
	private final ControllerAgentGui gui;
	private final NodeLabUi.Page page = new NodeLabUi.Page(1360);
	private final NodeLabUi.Prose clusterText = NodeLabUi.prose(" ");
	private final NodeLabUi.Stepper nodes = new NodeLabUi.Stepper(1, 1, 64);
	private final NodeLabUi.Button nodeLab;
	private final NodeLabUi.Field heap = new NodeLabUi.Field("", 8, "MB");
	private final NodeLabUi.Button applyHeap;
	private final NodeLabUi.Prose memoryText = NodeLabUi.note(" ");
	private final Step load;
	private final Step initialize;
	private final Step start;
	private final NodeLabUi.Chip status = new NodeLabUi.Chip("Ready", NodeLabUi.Tone.NEUTRAL, true);
	private final NodeLabUi.Prose statusText = NodeLabUi.prose(" ");
	private final NodeLabUi.Switch graph = new NodeLabUi.Switch(true);
	private final JPanel output = new JPanel(new BorderLayout());

	SimulatorRunPage(SimulatorPanel simulator)
	{
		this.simulator = simulator;
		this.gui = simulator.gui();
		NodeLabUi.Card cluster = new NodeLabUi.Card("Cluster",
				"The emulated nodes the run uses. Design their speeds, links and faults in the Node Lab.");
		nodeLab = SimulatorPanel.mirror("Node Lab", NodeLabUi.ButtonKind.SECONDARY, NodeLabUi.Glyph.CLUSTER,
				gui.simulatorNodeLabButton());
		cluster.action(nodeLab);
		SimulatorPanel.bind(nodes, gui.simulatorNodesField());
		cluster.content(NodeLabUi.stack(12, NodeLabUi.row(14, NodeLabUi.labeled("Recommender nodes", nodes)),
				clusterText, NodeLabUi.note("Recommendations are identical on any number of nodes; the cluster"
						+ " changes only when the work runs.")));

		NodeLabUi.Card memory = new NodeLabUi.Card("Memory",
				"The Java heap of the simulator. Applying a new limit restarts it safely.");
		applyHeap = SimulatorPanel.mirror("Apply", NodeLabUi.ButtonKind.SECONDARY, NodeLabUi.Glyph.CHECK,
				gui.simulatorApplyHeapButton());
		SimulatorPanel.bind(heap, gui.simulatorHeapField());
		memory.content(NodeLabUi.stack(10, NodeLabUi.row(8, NodeLabUi.fixedWidth(
				NodeLabUi.labeled("Maximum heap (MB)", heap), 160), applyHeap), memoryText));

		JPanel settings = NodeLabUi.grid(420, 2, 16);
		settings.add(cluster);
		settings.add(memory);

		NodeLabUi.Card run = new NodeLabUi.Card("Run",
				"Three steps. Each becomes available when the one before it has finished.");
		run.action(status);
		load = new Step("1", "Load users", "Read the dataset and list its users.", NodeLabUi.Glyph.REFRESH,
				gui.simulatorLoadUsersButton());
		initialize = new Step("2", "Initialize", "Create the agents and prepare the model for the targets.",
				NodeLabUi.Glyph.CPU, gui.simulatorInitializeButton());
		start = new Step("3", "Run simulation", "Stream the tweets, score, merge and deliver the lists.",
				NodeLabUi.Glyph.PLAY, gui.simulatorStartButton());
		JPanel steps = NodeLabUi.grid(240, 3, 12);
		steps.add(load);
		steps.add(initialize);
		steps.add(start);
		SimulatorPanel.bind(graph, gui.simulatorGraphOutput());
		JPanel graphRow = new JPanel(new BorderLayout(12, 0));
		graphRow.setOpaque(false);
		graphRow.add(graph, BorderLayout.WEST);
		graphRow.add(NodeLabUi.stack(2, NodeLabUi.strong("Lay out and export the run graph"),
				NodeLabUi.note("Every run records its graph and its analysis (Graph Insights); this also lays it"
						+ " out and writes GEXF, GraphML, SVG and PNG into the run record.")), BorderLayout.CENTER);
		run.content(NodeLabUi.stack(14, steps, statusText, graphRow));

		NodeLabUi.Card live = new NodeLabUi.Card("Live output",
				"Timing as the run proceeds, the recommendation cards, earlier runs and every node.");
		output.setOpaque(false);
		JTabbedPane tabs = gui.simulatorOutputTabs();
		if (tabs != null)
		{
			JPanel holder = new JPanel(new BorderLayout())
			{
				private static final long serialVersionUID = 1L;

				public Dimension getPreferredSize()
				{
					return new Dimension(600, 600);
				}

				public Dimension getMaximumSize()
				{
					return new Dimension(Integer.MAX_VALUE, 600);
				}
			};
			holder.setOpaque(false);
			holder.add(tabs, BorderLayout.CENTER);
			output.add(holder, BorderLayout.CENTER);
		}
		live.content(output);

		page.section(settings).section(run).section(live);
		add(NodeLabUi.scroll(page), BorderLayout.CENTER);
		refresh();
	}

	void refresh()
	{
		clusterText.setText(NodeLabPanel.clusterSummary() + " · " + NodeLabPanel.planSummary());
		SimulatorPanel.read(nodes, gui.simulatorNodesField(), 1);
		nodes.setEnabled(gui.simulatorNodesField() != null && gui.simulatorNodesField().isEnabled());
		SimulatorPanel.read(nodeLab, gui.simulatorNodeLabButton());
		SimulatorPanel.read(heap, gui.simulatorHeapField());
		SimulatorPanel.read(applyHeap, gui.simulatorApplyHeapButton());
		memoryText.setText(gui.simulatorRuntimeMemory()
				+ (gui.simulatorRuntimeScheduler().isEmpty() ? "" : " · " + gui.simulatorRuntimeScheduler()));
		boolean loaded = simulator.userCount() > 0;
		boolean initialized = gui.simulatorInitialized();
		boolean running = "RUNNING".equals(gui.simulatorStatusTone()) && initialized;
		boolean targets = simulator.targetCount() > 0;
		load.update(loaded ? State.DONE : State.NEXT, true);
		initialize.update(gui.simulatorInitializing() ? State.WORKING
				: initialized ? State.DONE : loaded && targets ? State.NEXT : State.WAITING, loaded && targets);
		start.update(running ? State.WORKING : gui.getLatestQualityReport() != null && !initialized ? State.DONE
				: initialized ? State.NEXT : State.WAITING, initialized);
		String title = gui.simulatorStatusTitle();
		status.set(SimulatorPanel.capitalize(title.isEmpty() ? "Ready" : title),
				SimulatorPanel.tone(gui.simulatorStatusTone()));
		statusText.setText(gui.simulatorStatusDetail());
		SimulatorPanel.read(graph, gui.simulatorGraphOutput());
	}

	private enum State
	{
		WAITING, NEXT, WORKING, DONE
	}

	/** One step of the run: its number, title, explanation and button. */
	private final class Step extends JPanel
	{
		private static final long serialVersionUID = 1L;
		private final JButton legacy;
		private final NodeLabUi.Button button;
		private final Badge badge;
		private State state = State.WAITING;

		Step(String number, String title, String text, NodeLabUi.Glyph glyph, JButton legacy)
		{
			super(new BorderLayout(0, 12));
			this.legacy = legacy;
			setOpaque(false);
			setBorder(new EmptyBorder(16, 18, 16, 18));
			badge = new Badge(number);
			JPanel head = new JPanel(new BorderLayout(10, 0));
			head.setOpaque(false);
			head.add(badge, BorderLayout.WEST);
			head.add(NodeLabUi.stack(2, NodeLabUi.heading(title), NodeLabUi.note(text)), BorderLayout.CENTER);
			add(head, BorderLayout.NORTH);
			button = SimulatorPanel.mirror(title, NodeLabUi.ButtonKind.PRIMARY, glyph, legacy);
			JPanel foot = new JPanel(new BorderLayout());
			foot.setOpaque(false);
			foot.add(button, BorderLayout.WEST);
			add(foot, BorderLayout.SOUTH);
		}

		/** {@code allowed}: the step's own precondition, on top of the simulator's button state. */
		void update(State next, boolean allowed)
		{
			SimulatorPanel.read(button, legacy);
			if (!allowed && button.isEnabled())
			{
				button.setEnabled(false);
			}
			if (next != state)
			{
				state = next;
				badge.state = next;
				repaint();
			}
		}

		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D)graphics.create();
			NodeLabUi.prepare(g);
			Color fill = state == State.NEXT ? NodeLabUi.alpha(NebulaDesign.ACCENT_SOFT, 255) : NebulaDesign.SURFACE;
			NodeLabUi.paintSurface(g, 0, 0, getWidth(), getHeight() - 3, NebulaDesign.RADIUS_CARD, fill,
					state == State.NEXT);
			if (state == State.NEXT || state == State.WORKING)
			{
				g.setColor(NodeLabUi.alpha(NebulaDesign.ACCENT, 150));
				g.draw(new java.awt.geom.RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1.5f, getHeight() - 4f,
						NebulaDesign.RADIUS_CARD, NebulaDesign.RADIUS_CARD));
			}
			g.dispose();
		}
	}

	private static final class Badge extends JComponent
	{
		private static final long serialVersionUID = 1L;
		private final String number;
		private State state = State.WAITING;

		Badge(String number)
		{
			this.number = number;
		}

		public Dimension getPreferredSize()
		{
			return new Dimension(30, 30);
		}

		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D)graphics.create();
			NodeLabUi.prepare(g);
			Color color = state == State.DONE ? NebulaDesign.SUCCESS
					: state == State.WAITING ? NebulaDesign.TEXT_TERTIARY : NebulaDesign.ACCENT;
			if (state == State.WORKING)
			{
				float pulse = (float)(0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 180.0));
				g.setColor(NodeLabUi.alpha(color, (int)(40 + 60 * pulse)));
				g.fill(new Ellipse2D.Float(0, 0, 30, 30));
			}
			g.setColor(color);
			g.fill(new Ellipse2D.Float(3, 3, 24, 24));
			g.setColor(Color.WHITE);
			if (state == State.DONE)
			{
				g.translate(9, 9);
				NodeLabUi.paintGlyph(g, NodeLabUi.Glyph.CHECK, 12);
			}
			else
			{
				g.setFont(NebulaDesign.fontSemiBold(12f));
				g.drawString(number, 15 - g.getFontMetrics().stringWidth(number) / 2f, 19.5f);
			}
			g.dispose();
		}
	}
}
