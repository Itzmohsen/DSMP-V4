package TwitterGatherDataFollowers.userRyersonU;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

/**
 * Step 1 of the Node Lab: design the cluster. The topology shows the whole
 * cluster at a glance; each node has a card with the settings that matter
 * most (speed, lanes, link), the rest one click away; presets and saved
 * clusters start a design in one click; the insights panel explains what the
 * design means in plain language. Every edit applies to the next run at once.
 */
final class NodeLabClusterPage extends NodeLabPanel.Page
{
	private static final long serialVersionUID = 1L;
	private static final int MAX_NODES = 32;

	private final NodeLabPanel lab;
	private final NodeLabUi.Page page = new NodeLabUi.Page(1360);
	private final NodeLabCharts.TopologyView topology = new NodeLabCharts.TopologyView();
	private final JPanel nodeGrid = NodeLabUi.grid(300, 4, 14);
	private final ArrayList<NodeCard> cards = new ArrayList<NodeCard>();
	private final NodeLabUi.Metric nodesMetric = new NodeLabUi.Metric("Nodes", "—", " ", NebulaDesign.ACCENT);
	private final NodeLabUi.Metric throughputMetric = new NodeLabUi.Metric("Throughput", "—", " ", NebulaDesign.SUCCESS);
	private final NodeLabUi.Metric dilationMetric = new NodeLabUi.Metric("Time dilation", "—", " ", NebulaDesign.PURPLE);
	private final NodeLabUi.Metric fidelityMetric = new NodeLabUi.Metric("Host load", "—", " ", NebulaDesign.INFO);
	private final JPanel insights = NodeLabUi.stack(10);
	private final JPanel savedList = NodeLabUi.stack(8);
	private final NodeLabUi.Field saveName = new NodeLabUi.Field("", 14, "Name this cluster");
	private final NodeLabUi.Hero hero;
	private final SettingsCard settingsCard;
	private List<NodeCapacityProfile> profiles = new ArrayList<NodeCapacityProfile>();
	private ClusterSettings settings = ClusterSettings.defaults();
	private int selectedNode = 1;
	private boolean updating;

	NodeLabClusterPage(NodeLabPanel lab)
	{
		this.lab = lab;
		hero = new NodeLabUi.Hero("Step 1 · Cluster", "Design your cluster",
				"Every node has a speed, a number of lanes, a network link and, if you want, faults and crashes."
						+ " What you design here is exactly what the next emulated run reproduces, on one common time scale.",
				NebulaDesign.ACCENT);
		NodeLabUi.Button addNode = new NodeLabUi.Button("Add node", NodeLabUi.ButtonKind.PRIMARY, NodeLabUi.Glyph.PLUS);
		addNode.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent event)
			{
				addNode(NodeCapacityProfile.Role.NORMAL);
			}
		});
		NodeLabUi.Button next = new NodeLabUi.Button("Next: plan the run", NodeLabUi.ButtonKind.SECONDARY,
				NodeLabUi.Glyph.ARROW_RIGHT);
		next.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent event)
			{
				NodeLabClusterPage.this.lab.show(NodeLabPanel.PLAN);
			}
		});
		hero.right(NodeLabUi.stack(8, addNode, next));

		JPanel metrics = NodeLabUi.grid(200, 4, 14);
		metrics.add(nodesMetric);
		metrics.add(throughputMetric);
		metrics.add(dilationMetric);
		metrics.add(fidelityMetric);

		topology.setListener(new NodeLabCharts.NodeClickListener()
		{
			public void nodeClicked(int node)
			{
				select(node);
			}
		});
		NodeLabUi.Card topologyCard = new NodeLabUi.Card("Topology",
				"A star network: every node talks to the coordinator over its own link. Bigger discs do more work;"
						+ " thicker links carry more bandwidth; warmer links are slower.");
		topologyCard.action(NodeLabUi.help("Reading the topology",
				"Disc size grows with throughput (speed × lanes). Link thickness grows with bandwidth, and its colour"
						+ " goes green → teal → amber → red as latency grows (≤2 ms, ≤30 ms, ≤120 ms, more)."
						+ " A dashed link is unlimited. A badge marks nodes with faults or crashes. Click a node to"
						+ " jump to its card."));
		topologyCard.content(NodeLabUi.stack(14, topology, topologyLegend()));

		NodeLabUi.Card presetsCard = new NodeLabUi.Card("Start from a preset",
				"One click replaces the cluster; you can then fine-tune any node.");
		JPanel presets = NodeLabUi.stack(8);
		for (final ClusterConfig preset : ClusterConfig.presets(lab.heapMb()))
		{
			presets.add(presetTile(preset));
		}
		presetsCard.content(presets);

		JPanel upper = new JPanel(new BorderLayout(16, 0));
		upper.setOpaque(false);
		upper.add(topologyCard, BorderLayout.CENTER);
		JPanel side = NodeLabUi.stack(16, presetsCard, savedCard());
		upper.add(NodeLabUi.fixedWidth(side, 360), BorderLayout.EAST);

		NodeLabUi.Card nodesCard = new NodeLabUi.Card("Nodes",
				"Speed is in reference cores: 2× does the same work in half the time. Lanes run units in parallel."
						+ " Open “More” on a card for memory, queue, slowdown, faults and crashes.");
		NodeLabUi.Button addWeak = new NodeLabUi.Button("Weak", NodeLabUi.ButtonKind.GHOST, NodeLabUi.Glyph.PLUS, true);
		NodeLabUi.Button addStrong = new NodeLabUi.Button("Strong", NodeLabUi.ButtonKind.GHOST, NodeLabUi.Glyph.PLUS, true);
		NodeLabUi.Button addUnreliable = new NodeLabUi.Button("Unreliable", NodeLabUi.ButtonKind.GHOST,
				NodeLabUi.Glyph.PLUS, true);
		addWeak.addActionListener(addRole(NodeCapacityProfile.Role.WEAK));
		addStrong.addActionListener(addRole(NodeCapacityProfile.Role.STRONG));
		addUnreliable.addActionListener(addRole(NodeCapacityProfile.Role.UNRELIABLE));
		nodesCard.action(addWeak).action(addStrong).action(addUnreliable);
		nodesCard.content(nodeGrid);

		NodeLabUi.Card insightsCard = new NodeLabUi.Card("What this cluster means",
				"Plain-language consequences of the design, updated as you edit.");
		insightsCard.content(insights);
		settingsCard = new SettingsCard();

		page.section(hero).section(metrics).section(upper).section(nodesCard).section(insightsCard)
				.section(settingsCard);
		add(NodeLabUi.scroll(page), BorderLayout.CENTER);
		refresh();
	}

	private ActionListener addRole(final NodeCapacityProfile.Role role)
	{
		return new ActionListener()
		{
			public void actionPerformed(ActionEvent event)
			{
				addNode(role);
			}
		};
	}

	void refresh()
	{
		if (updating)
		{
			return;
		}
		List<NodeCapacityProfile> current = NodeCapacityRegistry.snapshot();
		ClusterSettings currentSettings = NodeCapacityRegistry.clusterSettings();
		boolean same = current.size() == profiles.size();
		for (int index = 0; same && index < current.size(); index++)
		{
			same = current.get(index).sameSettingsAs(profiles.get(index));
		}
		profiles = new ArrayList<NodeCapacityProfile>(current);
		settings = currentSettings;
		if (!same || cards.size() != profiles.size())
		{
			rebuildCards();
		}
		settingsCard.load(settings);
		refreshSaved();
		updateSummary();
	}

	private void rebuildCards()
	{
		nodeGrid.removeAll();
		cards.clear();
		for (int index = 0; index < profiles.size(); index++)
		{
			NodeCard card = new NodeCard(index);
			cards.add(card);
			nodeGrid.add(card);
		}
		if (profiles.size() < MAX_NODES)
		{
			nodeGrid.add(addTile());
		}
		nodeGrid.revalidate();
		page.revalidate();
		repaint();
	}

	/** Applies the working profiles and settings to the simulator. */
	private void apply()
	{
		updating = true;
		try
		{
			ArrayList<NodeCapacityProfile> renumbered = new ArrayList<NodeCapacityProfile>();
			for (int index = 0; index < profiles.size(); index++)
			{
				renumbered.add(profiles.get(index).withNodeIndex(index + 1));
			}
			profiles = renumbered;
			lab.applyCluster(profiles, settings);
		}
		finally
		{
			updating = false;
		}
		updateSummary();
	}

	private void addNode(NodeCapacityProfile.Role role)
	{
		if (profiles.size() >= MAX_NODES)
		{
			return;
		}
		profiles.add(NodeCapacityProfile.rolePreset(profiles.size() + 1, role, profiles.size() + 1, lab.heapMb()));
		apply();
		rebuildCards();
		select(profiles.size());
	}

	private void removeNode(int index)
	{
		if (profiles.size() <= 1)
		{
			return;
		}
		profiles.remove(index);
		apply();
		rebuildCards();
		select(Math.min(selectedNode, profiles.size()));
	}

	private void select(int node)
	{
		selectedNode = node;
		topology.setSelected(node);
		for (NodeCard card : cards)
		{
			card.repaint();
		}
		if (node >= 1 && node <= cards.size())
		{
			final NodeCard card = cards.get(node - 1);
			javax.swing.SwingUtilities.invokeLater(new Runnable()
			{
				public void run()
				{
					card.scrollRectToVisible(new java.awt.Rectangle(0, 0, card.getWidth(), card.getHeight()));
				}
			});
		}
	}

	private void updateSummary()
	{
		ArrayList<NodeLabCharts.NodeView> views = new ArrayList<NodeLabCharts.NodeView>();
		double throughput = 0.0;
		double fastest = 0.0;
		double slowest = Double.POSITIVE_INFINITY;
		for (NodeCapacityProfile profile : profiles)
		{
			views.add(NodeLabCharts.NodeView.of(profile));
			double speed = profile.effectiveSpeed();
			throughput += speed * profile.getMaxConcurrentJobs();
			fastest = Math.max(fastest, speed);
			slowest = Math.min(slowest, speed);
		}
		topology.setNodes(views, settings.getCoordinatorSpeed());
		topology.setSelected(selectedNode);
		List<NodeSpec> specs = NodeSpec.fromProfiles(profiles, null, true);
		double dilation = SchedulingRunContext.chooseDilation(settings, specs, true);
		double load = 0.0;
		for (NodeSpec spec : specs)
		{
			load += spec.getLanes() * Math.min(spec.getSpeed(), dilation) / dilation;
		}
		int cores = Runtime.getRuntime().availableProcessors();
		nodesMetric.set(Integer.toString(profiles.size()), roleSummary());
		throughputMetric.set(String.format(Locale.US, "%.2f×", throughput),
				"reference cores, all lanes busy");
		dilationMetric.set(String.format(Locale.US, "%.2f", dilation),
				settings.isAutomaticTimeDilation() ? "automatic · model time = wall / TDF" : "fixed by you");
		boolean healthy = load <= Math.max(1, cores - 2);
		fidelityMetric.set(String.format(Locale.US, "%.1f / %d", load, cores),
				healthy ? "busy cores of this machine · faithful" : "busy cores · raise the TDF");
		fidelityMetric.accent(healthy ? NebulaDesign.SUCCESS : NebulaDesign.WARNING);
		rebuildInsights(throughput, fastest, slowest, dilation, load, cores);
		hero.clearChips();
		hero.chip(new NodeLabUi.Chip(profiles.size() + " node" + (profiles.size() == 1 ? "" : "s"),
				NodeLabUi.Tone.ACCENT, true));
		hero.chip(new NodeLabUi.Chip(String.format(Locale.US, "throughput %.2f×", throughput), NodeLabUi.Tone.SUCCESS,
				false));
		boolean faulty = false;
		for (NodeCapacityProfile profile : profiles)
		{
			faulty |= profile.hasCrashProcess() || profile.isFailureSimulationEnabled();
		}
		if (faulty)
		{
			hero.chip(new NodeLabUi.Chip("faults configured", NodeLabUi.Tone.WARNING, true));
		}
		hero.revalidate();
		page.revalidate();
		page.repaint();
	}

	private String roleSummary()
	{
		java.util.LinkedHashMap<String,Integer> roles = new java.util.LinkedHashMap<String,Integer>();
		for (NodeCapacityProfile profile : profiles)
		{
			Integer count = roles.get(profile.getRoleLabel());
			roles.put(profile.getRoleLabel(), Integer.valueOf(count == null ? 1 : count.intValue() + 1));
		}
		StringBuilder builder = new StringBuilder();
		for (java.util.Map.Entry<String,Integer> role : roles.entrySet())
		{
			if (builder.length() > 0)
			{
				builder.append(", ");
			}
			builder.append(role.getValue()).append(' ').append(role.getKey().toLowerCase(Locale.US));
		}
		return builder.toString();
	}

	private void rebuildInsights(double throughput, double fastest, double slowest, double dilation,
			double load, int cores)
	{
		insights.removeAll();
		insight(NodeLabUi.Glyph.GAUGE, NodeLabUi.Tone.SUCCESS, String.format(Locale.US,
				"The cluster does %.2f× the work of one reference core per unit of time%s.", throughput,
				profiles.size() > 1 && slowest > 0.0 && fastest / slowest > 1.01
						? String.format(Locale.US, "; its fastest node is %.1f× its slowest, so placement matters",
								fastest / slowest)
						: "; all nodes are equally fast"));
		insight(NodeLabUi.Glyph.CLOCK, NodeLabUi.Tone.PURPLE, String.format(Locale.US,
				"Runs emulate this cluster with a time dilation of %.2f: wall time is %.2f× model time, and every"
						+ " reported time is model time, comparable across clusters.", dilation, dilation));
		insight(NodeLabUi.Glyph.CPU, load <= Math.max(1, cores - 2) ? NodeLabUi.Tone.INFO : NodeLabUi.Tone.WARNING,
				String.format(Locale.US, "This machine has %d cores; the emulation keeps about %.1f of them busy%s.",
						cores, load, load <= Math.max(1, cores - 2) ? ", leaving room so timings stay faithful"
								: " — raise the time dilation factor for faithful timings"));
		for (NodeCapacityProfile profile : profiles)
		{
			if (profile.getNetworkDelayMs() >= 50)
			{
				insight(NodeLabUi.Glyph.NETWORK, NodeLabUi.Tone.WARNING, String.format(Locale.US,
						"Node %d has a %d ms link: every request/reply costs at least %d ms, and input batches are sent"
								+ " stop-and-wait, so input delivery to it is slow.", profile.getNodeIndex(),
						profile.getNetworkDelayMs(), 2 * profile.getNetworkDelayMs()));
			}
			if (profile.hasCrashProcess())
			{
				double availability = profile.getMtbfSeconds() / (profile.getMtbfSeconds() + profile.getMttrSeconds());
				insight(NodeLabUi.Glyph.CRASH, NodeLabUi.Tone.DANGER, String.format(Locale.US,
						"Node %d crashes every %s on average and takes %s to repair (available %.0f%% of the time)."
								+ " Crashes only happen in the “with faults” mode.",
						profile.getNodeIndex(), seconds(profile.getMtbfSeconds()), seconds(profile.getMttrSeconds()),
						availability * 100.0));
			}
			if (profile.isFailureSimulationEnabled() && profile.getFailureProbabilityPercent() > 0)
			{
				insight(NodeLabUi.Glyph.WARNING, NodeLabUi.Tone.WARNING, String.format(Locale.US,
						"Node %d loses %d%% of its unit executions to transient faults (they are retried, results stay exact).",
						profile.getNodeIndex(), profile.getFailureProbabilityPercent()));
			}
		}
		insights.revalidate();
	}

	private static String seconds(double value)
	{
		if (value >= 3600.0)
		{
			return String.format(Locale.US, "%.1f h", value / 3600.0);
		}
		if (value >= 60.0)
		{
			return String.format(Locale.US, "%.1f min", value / 60.0);
		}
		return String.format(Locale.US, "%.1f s", value);
	}

	private void insight(final NodeLabUi.Glyph glyph, final NodeLabUi.Tone tone, String text)
	{
		JComponent badge = new JComponent()
		{
			private static final long serialVersionUID = 1L;

			public Dimension getPreferredSize()
			{
				return new Dimension(30, 30);
			}

			protected void paintComponent(Graphics graphics)
			{
				Graphics2D g = (Graphics2D)graphics.create();
				NodeLabUi.prepare(g);
				g.setColor(NodeLabUi.softTone(tone));
				g.fill(new java.awt.geom.RoundRectangle2D.Float(0, 0, 30, 30, 10, 10));
				g.setColor(NodeLabUi.tone(tone));
				g.translate(8, 8);
				NodeLabUi.paintGlyph(g, glyph, 14);
				g.dispose();
			}
		};
		JPanel row = new JPanel(new BorderLayout(12, 0));
		row.setOpaque(false);
		JPanel holder = new JPanel(new BorderLayout());
		holder.setOpaque(false);
		holder.add(badge, BorderLayout.NORTH);
		row.add(holder, BorderLayout.WEST);
		row.add(NodeLabUi.paragraph(text), BorderLayout.CENTER);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		if (insights.getComponentCount() > 0)
		{
			insights.add(Box.createVerticalStrut(10));
		}
		insights.add(row);
	}

	// ------------------------------------------------------------ presets

	private JComponent presetTile(final ClusterConfig preset)
	{
		final JPanel tile = new JPanel(new BorderLayout(10, 0))
		{
			private static final long serialVersionUID = 1L;
			boolean hover;

			{
				addMouseListener(new MouseAdapter()
				{
					public void mouseEntered(MouseEvent event)
					{
						hover = true;
						repaint();
					}

					public void mouseExited(MouseEvent event)
					{
						hover = false;
						repaint();
					}

					public void mouseClicked(MouseEvent event)
					{
						usePreset(preset);
					}
				});
				setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
			}

			protected void paintComponent(Graphics graphics)
			{
				Graphics2D g = (Graphics2D)graphics.create();
				NodeLabUi.prepare(g);
				g.setColor(hover ? NebulaDesign.ACCENT_SOFT : NebulaDesign.SURFACE_ALT);
				g.fill(new java.awt.geom.RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 12, 12));
				g.setColor(hover ? NodeLabUi.alpha(NebulaDesign.ACCENT, 120) : NebulaDesign.BORDER_SUBTLE);
				g.draw(new java.awt.geom.RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1f, getHeight() - 1f, 12, 12));
				g.dispose();
			}
		};
		tile.setOpaque(false);
		tile.setBorder(new EmptyBorder(9, 12, 9, 12));
		JPanel dots = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 0))
		{
			private static final long serialVersionUID = 1L;

			public Dimension getPreferredSize()
			{
				return new Dimension(Math.min(8, preset.getNodeCount()) * 12 + 6, 24);
			}

			protected void paintComponent(Graphics graphics)
			{
				Graphics2D g = (Graphics2D)graphics.create();
				NodeLabUi.prepare(g);
				int x = 0;
				for (NodeCapacityProfile profile : preset.getProfiles())
				{
					if (x > 7 * 12)
					{
						break;
					}
					double size = 6 + 4 * Math.min(2.0, Math.sqrt(profile.effectiveSpeed() * profile.getMaxConcurrentJobs()));
					g.setColor(NodeLabUi.nodeColor(profile.getNodeIndex()));
					g.fill(new java.awt.geom.Ellipse2D.Double(x + (12 - size) / 2, 12 - size / 2, size, size));
					x += 12;
				}
				g.dispose();
			}
		};
		dots.setOpaque(false);
		tile.add(NodeLabUi.stack(2, NodeLabUi.strong(preset.getName()), NodeLabUi.caption(String.format(Locale.US,
				"throughput %.2f×%s", preset.throughput(), faulty(preset) ? " · faults" : ""))), BorderLayout.CENTER);
		tile.add(dots, BorderLayout.EAST);
		return tile;
	}

	private static boolean faulty(ClusterConfig config)
	{
		for (NodeCapacityProfile profile : config.getProfiles())
		{
			if (profile.hasCrashProcess() || profile.isFailureSimulationEnabled())
			{
				return true;
			}
		}
		return false;
	}

	/** The topology's visual vocabulary in one line. */
	private static JComponent topologyLegend()
	{
		JPanel legend = NodeLabPanel.flow(14);
		legend.add(legendItem(NodeLabCharts.latencyColor(0), "link ≤ 2 ms"));
		legend.add(legendItem(NodeLabCharts.latencyColor(20), "≤ 30 ms"));
		legend.add(legendItem(NodeLabCharts.latencyColor(100), "≤ 120 ms"));
		legend.add(legendItem(NodeLabCharts.latencyColor(400), "slower"));
		legend.add(NodeLabUi.caption("·  disc area = throughput  ·  link width = bandwidth  ·  click a node to edit it"));
		return legend;
	}

	private static JComponent legendItem(final Color color, String text)
	{
		JComponent swatch = new JComponent()
		{
			private static final long serialVersionUID = 1L;

			public Dimension getPreferredSize()
			{
				return new Dimension(18, 12);
			}

			protected void paintComponent(Graphics graphics)
			{
				Graphics2D g = (Graphics2D)graphics.create();
				NodeLabUi.prepare(g);
				g.setColor(color);
				g.setStroke(new java.awt.BasicStroke(3f, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
				g.drawLine(2, 6, 16, 6);
				g.dispose();
			}
		};
		JPanel item = new JPanel(new BorderLayout(5, 0));
		item.setOpaque(false);
		item.add(swatch, BorderLayout.WEST);
		item.add(NodeLabUi.caption(text), BorderLayout.CENTER);
		return item;
	}

	private void usePreset(ClusterConfig preset)
	{
		profiles = new ArrayList<NodeCapacityProfile>(preset.getProfiles());
		apply();
		rebuildCards();
		select(1);
	}

	private JComponent savedCard()
	{
		NodeLabUi.Card card = new NodeLabUi.Card("Saved clusters",
				"Save a design to reuse it in studies; files live in Clusters/.");
		NodeLabUi.Button save = new NodeLabUi.Button("Save", NodeLabUi.ButtonKind.SECONDARY, NodeLabUi.Glyph.SAVE, true);
		save.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent event)
			{
				saveCurrent();
			}
		});
		JPanel saveRow = new JPanel(new BorderLayout(8, 0));
		saveRow.setOpaque(false);
		saveRow.add(saveName, BorderLayout.CENTER);
		saveRow.add(save, BorderLayout.EAST);
		card.content(NodeLabUi.stack(12, saveRow, savedList));
		return card;
	}

	private void saveCurrent()
	{
		String name = saveName.getText().trim();
		if (name.length() == 0)
		{
			name = "Cluster " + new java.text.SimpleDateFormat("MMM d HH-mm", Locale.US).format(new java.util.Date());
		}
		ClusterConfig config = new ClusterConfig(name, profiles, settings);
		String file = name.replaceAll("[^A-Za-z0-9._-]+", "-").replaceAll("^-|-$", "");
		try
		{
			config.save(new File(ClusterConfig.directory(), (file.length() == 0 ? "cluster" : file) + ".cluster"));
			saveName.setText("");
			refreshSaved();
		}
		catch (java.io.IOException failure)
		{
			NebulaDialogs.showError(this, "Could not save the cluster: " + failure.getMessage(), "Save cluster");
		}
	}

	private void refreshSaved()
	{
		savedList.removeAll();
		List<ClusterConfig> saved = ClusterConfig.saved();
		if (saved.isEmpty())
		{
			savedList.add(NodeLabUi.caption("No saved clusters yet."));
		}
		for (final ClusterConfig config : saved)
		{
			NodeLabUi.Button load = new NodeLabUi.Button("Use", NodeLabUi.ButtonKind.GHOST, NodeLabUi.Glyph.DOWNLOAD, true);
			load.addActionListener(new ActionListener()
			{
				public void actionPerformed(ActionEvent event)
				{
					settings = config.getSettings();
					usePreset(config);
				}
			});
			JPanel row = NodeLabUi.spread(NodeLabUi.stack(1, NodeLabUi.strong(config.getName()),
					NodeLabUi.caption(config.describe())), load);
			row.setAlignmentX(Component.LEFT_ALIGNMENT);
			if (savedList.getComponentCount() > 0)
			{
				savedList.add(Box.createVerticalStrut(8));
			}
			savedList.add(row);
		}
		savedList.revalidate();
	}

	private JComponent addTile()
	{
		JPanel tile = new JPanel(new java.awt.GridBagLayout())
		{
			private static final long serialVersionUID = 1L;

			protected void paintComponent(Graphics graphics)
			{
				Graphics2D g = (Graphics2D)graphics.create();
				NodeLabUi.prepare(g);
				g.setColor(NebulaDesign.BORDER_STRONG);
				g.setStroke(new java.awt.BasicStroke(1.4f, java.awt.BasicStroke.CAP_ROUND,
						java.awt.BasicStroke.JOIN_ROUND, 10f, new float[] {6f, 5f}, 0f));
				g.draw(new java.awt.geom.RoundRectangle2D.Float(1, 1, getWidth() - 3, getHeight() - 5,
						NebulaDesign.RADIUS_CARD, NebulaDesign.RADIUS_CARD));
				g.dispose();
			}
		};
		tile.setOpaque(false);
		NodeLabUi.Button add = new NodeLabUi.Button("Add a node", NodeLabUi.ButtonKind.SECONDARY, NodeLabUi.Glyph.PLUS);
		add.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent event)
			{
				addNode(NodeCapacityProfile.Role.NORMAL);
			}
		});
		tile.add(NodeLabUi.stack(8, add, NodeLabUi.caption("up to " + MAX_NODES + " nodes")));
		tile.setPreferredSize(new Dimension(300, 200));
		return tile;
	}

	// ------------------------------------------------------------ node card

	private static final NodeLabUi.Scale LATENCY = new NodeLabUi.Scale()
	{
		public double value(double fraction)
		{
			return Math.round(600.0 * fraction * fraction);
		}

		public double fraction(double value)
		{
			return Math.sqrt(Math.max(0.0, Math.min(600.0, value)) / 600.0);
		}
	};

	/** Bandwidth from 1 Mbit/s to 100 Gbit/s, the right end meaning unlimited. */
	private static final NodeLabUi.Scale BANDWIDTH = new NodeLabUi.Scale()
	{
		public double value(double fraction)
		{
			if (fraction >= 0.985)
			{
				return 0.0;
			}
			double mbps = Math.pow(10.0, fraction / 0.985 * 5.0);
			double magnitude = Math.pow(10.0, Math.floor(Math.log10(mbps)));
			return Math.max(1.0, Math.round(mbps / magnitude * 2.0) / 2.0 * magnitude);
		}

		public double fraction(double value)
		{
			if (value <= 0.0)
			{
				return 1.0;
			}
			return Math.max(0.0, Math.min(0.985, Math.log10(value) / 5.0 * 0.985));
		}
	};

	private final class NodeCard extends NodeLabUi.Card
	{
		private static final long serialVersionUID = 1L;
		private final int index;
		private final NodeLabUi.Slider speed;
		private final NodeLabUi.Stepper lanes;
		private final NodeLabUi.Stepper queue;
		private final NodeLabUi.Slider latency;
		private final NodeLabUi.Slider bandwidth;
		private final NodeLabUi.Slider memory;
		private final NodeLabUi.Slider slowdown;
		private final NodeLabUi.Slider faults;
		private final NodeLabUi.Switch crashes;
		private final NodeLabUi.Slider mtbf;
		private final NodeLabUi.Slider mttr;
		private final JComboBox<NodeCapacityProfile.Role> role;
		private final JPanel advanced;
		private final NodeLabUi.Button more;
		private final NodeLabUi.Text summary;
		private boolean loading;

		NodeCard(int index)
		{
			super(null, null);
			this.index = index;
			NodeCapacityProfile profile = profiles.get(index);
			final Color color = NodeLabUi.nodeColor(index + 1);
			accent(color);
			role = NodeLabUi.combo(NodeCapacityProfile.Role.values());
			role.setSelectedItem(profile.getRole());
			NodeLabUi.Button remove = NodeLabUi.iconButton(NodeLabUi.Glyph.TRASH, "Remove this node");
			remove.setEnabled(profiles.size() > 1);
			remove.addActionListener(new ActionListener()
			{
				public void actionPerformed(ActionEvent event)
				{
					removeNode(NodeCard.this.index);
				}
			});
			JComponent dot = new JComponent()
			{
				private static final long serialVersionUID = 1L;

				public Dimension getPreferredSize()
				{
					return new Dimension(30, 30);
				}

				protected void paintComponent(Graphics graphics)
				{
					Graphics2D g = (Graphics2D)graphics.create();
					NodeLabUi.prepare(g);
					g.setColor(NodeLabUi.alpha(color, NodeLabUi.night() ? 70 : 45));
					g.fill(new java.awt.geom.Ellipse2D.Float(1, 1, 28, 28));
					g.setColor(color);
					g.setStroke(new java.awt.BasicStroke(2f));
					g.draw(new java.awt.geom.Ellipse2D.Float(2, 2, 26, 26));
					g.setFont(NebulaDesign.fontSemiBold(11f));
					String label = "N" + (NodeCard.this.index + 1);
					java.awt.FontMetrics metrics = g.getFontMetrics();
					g.setColor(NebulaDesign.TEXT_PRIMARY);
					g.drawString(label, 15 - metrics.stringWidth(label) / 2f, 19);
					g.dispose();
				}
			};
			JPanel header = new JPanel(new BorderLayout(10, 0));
			header.setOpaque(false);
			header.add(dot, BorderLayout.WEST);
			summary = NodeLabUi.caption(describe(profile));
			header.add(NodeLabUi.stack(1, NodeLabUi.heading("Node " + (index + 1)), summary), BorderLayout.CENTER);
			header.add(remove, BorderLayout.EAST);

			speed = new NodeLabUi.Slider("Speed", profile.getCpuMultiplier(), NodeLabUi.logarithmic(0.1, 16.0), 0.05,
					new NodeLabUi.Formatter()
			{
				public String format(double value)
				{
					return String.format(Locale.US, "%.2f×", value);
				}
			}).color(color);
			lanes = new NodeLabUi.Stepper(profile.getMaxConcurrentJobs(), 1, 64);
			queue = new NodeLabUi.Stepper(profile.getQueueCapacity(), 1, 256);
			latency = new NodeLabUi.Slider("Link latency", profile.getNetworkDelayMs(), LATENCY, 1.0,
					new NodeLabUi.Formatter()
			{
				public String format(double value)
				{
					return Math.round(value) + " ms";
				}
			}).color(color);
			bandwidth = new NodeLabUi.Slider("Bandwidth", profile.getBandwidthMbps(), BANDWIDTH, 0.0,
					new NodeLabUi.Formatter()
			{
				public String format(double value)
				{
					return NodeCapacityProfile.formatBandwidth(value);
				}
			}).color(color);
			memory = new NodeLabUi.Slider("Memory", profile.getMemoryLimitMb(), NodeLabUi.logarithmic(128, 262144), 0.0,
					new NodeLabUi.Formatter()
			{
				public String format(double value)
				{
					return RuntimeResourceSettings.formatMegabytes((long)Math.round(value));
				}
			}).color(color);
			slowdown = new NodeLabUi.Slider("Slowdown", profile.isSlowdownSimulationEnabled()
					? profile.getSlowdownMultiplier() : 1.0, NodeLabUi.logarithmic(1.0, 10.0), 0.05,
					new NodeLabUi.Formatter()
			{
				public String format(double value)
				{
					return value <= 1.001 ? "none" : String.format(Locale.US, "÷ %.2f", value);
				}
			}).color(color);
			faults = new NodeLabUi.Slider("Transient faults", profile.isFailureSimulationEnabled()
					? profile.getFailureProbabilityPercent() : 0, NodeLabUi.linear(0, 50), 1.0,
					new NodeLabUi.Formatter()
			{
				public String format(double value)
				{
					return Math.round(value) + "% of executions";
				}
			}).color(NebulaDesign.WARNING);
			crashes = new NodeLabUi.Switch(profile.hasCrashProcess());
			mtbf = new NodeLabUi.Slider("Mean time between crashes", profile.hasCrashProcess()
					? profile.getMtbfSeconds() : 30.0, NodeLabUi.logarithmic(1.0, 86400.0), 0.0,
					new NodeLabUi.Formatter()
			{
				public String format(double value)
				{
					return seconds(value);
				}
			}).color(NebulaDesign.DANGER);
			mttr = new NodeLabUi.Slider("Mean time to repair", profile.hasCrashProcess() ? Math.max(0.1,
					profile.getMttrSeconds()) : 2.0, NodeLabUi.logarithmic(0.1, 3600.0), 0.0,
					new NodeLabUi.Formatter()
			{
				public String format(double value)
				{
					return seconds(value);
				}
			}).color(NebulaDesign.DANGER);

			JPanel steppers = NodeLabUi.grid(110, 2, 12);
			steppers.add(NodeLabUi.labeled("Lanes", lanes));
			steppers.add(NodeLabUi.labeled("Queue capacity", queue));
			JPanel crashRow = NodeLabUi.spread(NodeLabUi.stack(1, NodeLabUi.strong("Crashes and repairs"),
					NodeLabUi.caption("Only in the “with faults” mode")), crashes);
			advanced = NodeLabUi.stack(10, memory, slowdown, faults, crashRow, mtbf, mttr);
			advanced.setVisible(false);
			more = new NodeLabUi.Button("More", NodeLabUi.ButtonKind.GHOST, NodeLabUi.Glyph.SLIDERS, true);
			more.addActionListener(new ActionListener()
			{
				public void actionPerformed(ActionEvent event)
				{
					advanced.setVisible(!advanced.isVisible());
					more.setText(advanced.isVisible() ? "Less" : "More");
					nodeGrid.revalidate();
					page.revalidate();
				}
			});
			JPanel roleRow = new JPanel(new BorderLayout(8, 0));
			roleRow.setOpaque(false);
			roleRow.add(NodeLabUi.caption("Preset"), BorderLayout.WEST);
			roleRow.add(role, BorderLayout.CENTER);
			roleRow.add(more, BorderLayout.EAST);
			content(NodeLabUi.stack(12, header, roleRow, speed, steppers, latency, bandwidth, advanced));
			ChangeListener changed = new ChangeListener()
			{
				public void stateChanged(ChangeEvent event)
				{
					if (!loading)
					{
						store();
					}
				}
			};
			speed.addChangeListener(changed);
			lanes.addChangeListener(changed);
			queue.addChangeListener(changed);
			latency.addChangeListener(changed);
			bandwidth.addChangeListener(changed);
			memory.addChangeListener(changed);
			slowdown.addChangeListener(changed);
			faults.addChangeListener(changed);
			crashes.addChangeListener(changed);
			mtbf.addChangeListener(changed);
			mttr.addChangeListener(changed);
			role.addActionListener(new ActionListener()
			{
				public void actionPerformed(ActionEvent event)
				{
					if (!loading)
					{
						usePresetRole((NodeCapacityProfile.Role)role.getSelectedItem());
					}
				}
			});
			updateEnabled();
			addMouseListener(new MouseAdapter()
			{
				public void mousePressed(MouseEvent event)
				{
					selectedNode = NodeCard.this.index + 1;
					topology.setSelected(selectedNode);
				}
			});
		}

		private void updateEnabled()
		{
			mtbf.setEnabled(crashes.isOn());
			mttr.setEnabled(crashes.isOn());
		}

		private void usePresetRole(NodeCapacityProfile.Role value)
		{
			NodeCapacityProfile preset = NodeCapacityProfile.rolePreset(index + 1, value, profiles.size(), lab.heapMb());
			profiles.set(index, preset);
			load(preset);
			apply();
		}

		private void load(NodeCapacityProfile profile)
		{
			loading = true;
			try
			{
				speed.setValueQuietly(profile.getCpuMultiplier());
				lanes.setValueQuietly(profile.getMaxConcurrentJobs());
				queue.setValueQuietly(profile.getQueueCapacity());
				latency.setValueQuietly(profile.getNetworkDelayMs());
				bandwidth.setValueQuietly(profile.getBandwidthMbps());
				memory.setValueQuietly(profile.getMemoryLimitMb());
				slowdown.setValueQuietly(profile.isSlowdownSimulationEnabled() ? profile.getSlowdownMultiplier() : 1.0);
				faults.setValueQuietly(profile.isFailureSimulationEnabled() ? profile.getFailureProbabilityPercent() : 0);
				crashes.setOnQuietly(profile.hasCrashProcess());
				if (profile.hasCrashProcess())
				{
					mtbf.setValueQuietly(profile.getMtbfSeconds());
					mttr.setValueQuietly(Math.max(0.1, profile.getMttrSeconds()));
				}
				updateEnabled();
				summary.setText(describe(profile));
			}
			finally
			{
				loading = false;
			}
		}

		private String describe(NodeCapacityProfile profile)
		{
			return String.format(Locale.US, "%s · does %.2f× work", profile.getRoleLabel(),
					profile.effectiveSpeed() * profile.getMaxConcurrentJobs());
		}

		private void store()
		{
			updateEnabled();
			NodeCapacityProfile old = profiles.get(index);
			int laneCount = lanes.getValue();
			if (queue.getValue() < laneCount)
			{
				queue.setValueQuietly(laneCount);
			}
			double slowdownValue = slowdown.getValue();
			int faultPercent = (int)Math.round(faults.getValue());
			NodeCapacityProfile updated = new NodeCapacityProfile(
					index + 1,
					old.getRole(),
					speed.getValue(),
					(int)Math.round(memory.getValue()),
					laneCount,
					(int)Math.round(latency.getValue()),
					Math.max(laneCount, queue.getValue()),
					slowdownValue > 1.001,
					slowdownValue,
					faultPercent > 0,
					faultPercent,
					bandwidth.getValue(),
					crashes.isOn() ? mtbf.getValue() : 0.0,
					crashes.isOn() ? mttr.getValue() : 0.0);
			profiles.set(index, updated);
			summary.setText(describe(updated));
			if (adjusting())
			{
				updateSummary();
			}
			else
			{
				apply();
			}
		}

		/** True while any of this card's sliders is being dragged. */
		private boolean adjusting()
		{
			return speed.isAdjusting() || latency.isAdjusting() || bandwidth.isAdjusting() || memory.isAdjusting()
					|| slowdown.isAdjusting() || faults.isAdjusting() || mtbf.isAdjusting() || mttr.isAdjusting();
		}

		protected void paintComponent(Graphics graphics)
		{
			super.paintComponent(graphics);
			if (selectedNode == index + 1)
			{
				Graphics2D g = (Graphics2D)graphics.create();
				NodeLabUi.prepare(g);
				g.setColor(NodeLabUi.alpha(NodeLabUi.nodeColor(index + 1), 150));
				g.setStroke(new java.awt.BasicStroke(1.6f));
				g.draw(new java.awt.geom.RoundRectangle2D.Float(1, 1, getWidth() - 3, getHeight() - 6,
						NebulaDesign.RADIUS_CARD, NebulaDesign.RADIUS_CARD));
				g.dispose();
			}
		}
	}

	// ------------------------------------------------------------ cluster-wide settings

	private final class SettingsCard extends NodeLabUi.Card
	{
		private static final long serialVersionUID = 1L;
		private final NodeLabUi.Slider coordinator;
		private final NodeLabUi.Switch automatic;
		private final NodeLabUi.Slider dilation;
		private final NodeLabUi.Slider headroom;
		private final NodeLabUi.Slider detection;
		private final NodeLabUi.Segmented uptime;
		private final NodeLabUi.Slider shape;
		private final NodeLabUi.Segmented repair;
		private final NodeLabUi.Field seed;
		private final JPanel body;
		private final NodeLabUi.Button toggle;
		private boolean loading;

		SettingsCard()
		{
			super("Cluster-wide settings",
					"The coordinator, the time scale of the emulation, crash detection and the fault seed.");
			coordinator = new NodeLabUi.Slider("Coordinator speed", 1.0, NodeLabUi.logarithmic(0.25, 16.0), 0.05,
					new NodeLabUi.Formatter()
			{
				public String format(double value)
				{
					return String.format(Locale.US, "%.2f×", value);
				}
			});
			automatic = new NodeLabUi.Switch(true);
			dilation = new NodeLabUi.Slider("Fixed time dilation factor", 1.0, NodeLabUi.logarithmic(0.5, 64.0), 0.05,
					new NodeLabUi.Formatter()
			{
				public String format(double value)
				{
					return String.format(Locale.US, "%.2f", value);
				}
			});
			headroom = new NodeLabUi.Slider("Boost headroom", 1.0, NodeLabUi.linear(1.0, 4.0), 0.25,
					new NodeLabUi.Formatter()
			{
				public String format(double value)
				{
					return String.format(Locale.US, "boosts up to %.2f×", value);
				}
			});
			detection = new NodeLabUi.Slider("Crash detection timeout", 500, NodeLabUi.logarithmic(10, 60000), 0.0,
					new NodeLabUi.Formatter()
			{
				public String format(double value)
				{
					return NodeLabUi.duration(value);
				}
			});
			uptime = new NodeLabUi.Segmented(new String[] {"Exponential", "Weibull"}, null, 0);
			shape = new NodeLabUi.Slider("Weibull shape k", 1.5, NodeLabUi.linear(0.3, 5.0), 0.05,
					new NodeLabUi.Formatter()
			{
				public String format(double value)
				{
					return String.format(Locale.US, "k = %.2f%s", value,
							value < 1.0 ? " (early failures)" : value > 1.0 ? " (wear-out)" : " (memoryless)");
				}
			});
			repair = new NodeLabUi.Segmented(new String[] {"Fixed repair", "Exponential repair"}, null, 0);
			seed = new NodeLabUi.Field("", 12, "Seed");
			JPanel tdfRow = NodeLabUi.spread(NodeLabUi.stack(1, NodeLabUi.strong("Automatic time dilation"),
					NodeLabUi.caption("Chosen from the fastest node, the headroom and this machine's cores")), automatic);
			JPanel left = NodeLabUi.stack(14, coordinator, tdfRow, dilation, headroom);
			JPanel right = NodeLabUi.stack(14, detection, NodeLabUi.labeled("Time between crashes", uptime), shape,
					NodeLabUi.labeled("Repair time", repair), NodeLabUi.labeled("Fault and crash seed", seed));
			body = NodeLabUi.grid(320, 2, 28);
			body.add(NodeLabUi.top(left));
			body.add(NodeLabUi.top(right));
			body.setVisible(false);
			toggle = new NodeLabUi.Button("Show", NodeLabUi.ButtonKind.SECONDARY, NodeLabUi.Glyph.SLIDERS, true);
			toggle.addActionListener(new ActionListener()
			{
				public void actionPerformed(ActionEvent event)
				{
					body.setVisible(!body.isVisible());
					toggle.setText(body.isVisible() ? "Hide" : "Show");
					page.revalidate();
				}
			});
			action(NodeLabUi.help("Time dilation, explained",
					"All nodes run on this one machine. To make a node twice as slow, the emulator stretches its work"
							+ " in wall time. To let a node run FASTER than a host core, every node is stretched by the"
							+ " time dilation factor (TDF): model time = wall time / TDF. All reported times are model"
							+ " times, so clusters with different TDFs are directly comparable. A larger TDF also"
							+ " keeps the machine's cores from saturating, which keeps timings faithful."));
			action(toggle);
			content(body);
			ChangeListener changed = new ChangeListener()
			{
				public void stateChanged(ChangeEvent event)
				{
					if (!loading)
					{
						store();
					}
				}
			};
			coordinator.addChangeListener(changed);
			automatic.addChangeListener(changed);
			dilation.addChangeListener(changed);
			headroom.addChangeListener(changed);
			detection.addChangeListener(changed);
			uptime.addChangeListener(changed);
			shape.addChangeListener(changed);
			repair.addChangeListener(changed);
			seed.addActionListener(new ActionListener()
			{
				public void actionPerformed(ActionEvent event)
				{
					store();
				}
			});
			seed.addFocusListener(new java.awt.event.FocusAdapter()
			{
				public void focusLost(java.awt.event.FocusEvent event)
				{
					store();
				}
			});
		}

		void load(ClusterSettings value)
		{
			loading = true;
			try
			{
				coordinator.setValueQuietly(value.getCoordinatorSpeed());
				automatic.setOnQuietly(value.isAutomaticTimeDilation());
				dilation.setValueQuietly(value.isAutomaticTimeDilation() ? 1.0 : value.getTimeDilation());
				dilation.setEnabled(!value.isAutomaticTimeDilation());
				headroom.setValueQuietly(value.getBoostHeadroom());
				headroom.setEnabled(value.isAutomaticTimeDilation());
				detection.setValueQuietly(value.getDetectionTimeoutMs());
				uptime.setSelectedQuietly(value.getUptimeDistribution() == ClusterSettings.UptimeDistribution.WEIBULL
						? 1 : 0);
				shape.setValueQuietly(value.getWeibullShape());
				shape.setEnabled(value.getUptimeDistribution() == ClusterSettings.UptimeDistribution.WEIBULL);
				repair.setSelectedQuietly(value.getRepairDistribution() == ClusterSettings.RepairDistribution.EXPONENTIAL
						? 1 : 0);
				if (!seed.isFocusOwner())
				{
					seed.setText(Long.toString(value.getSeed()));
				}
			}
			finally
			{
				loading = false;
			}
		}

		private void store()
		{
			long seedValue = settings.getSeed();
			try
			{
				seedValue = Long.parseLong(seed.getText().trim());
			}
			catch (NumberFormatException ignored)
			{
				//Keep the previous seed.
			}
			settings = new ClusterSettings(
					coordinator.getValue(),
					automatic.isOn() ? 0.0 : dilation.getValue(),
					headroom.getValue(),
					(int)Math.round(detection.getValue()),
					uptime.getSelected() == 1 ? ClusterSettings.UptimeDistribution.WEIBULL
							: ClusterSettings.UptimeDistribution.EXPONENTIAL,
					shape.getValue(),
					repair.getSelected() == 1 ? ClusterSettings.RepairDistribution.EXPONENTIAL
							: ClusterSettings.RepairDistribution.FIXED,
					seedValue);
			dilation.setEnabled(!automatic.isOn());
			headroom.setEnabled(automatic.isOn());
			shape.setEnabled(uptime.getSelected() == 1);
			if (coordinator.isAdjusting() || dilation.isAdjusting() || headroom.isAdjusting()
					|| detection.isAdjusting() || shape.isAdjusting())
			{
				updateSummary();
			}
			else
			{
				apply();
			}
		}
	}
}
