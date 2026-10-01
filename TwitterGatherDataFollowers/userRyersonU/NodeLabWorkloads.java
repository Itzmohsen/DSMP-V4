package TwitterGatherDataFollowers.userRyersonU;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Synthetic work phases and one-call simulations for the Node Lab's instant
 * previews (Plan) and what-if comparisons (What-If).
 *
 * A synthetic phase models the recommender's unit phases: {@code count} units
 * whose reference costs follow a chosen shape around a mean, each with its
 * input data on a home node (round-robin, as V3 spreads users over nodes) and
 * a small result. Costs are in reference-core milliseconds, so a node of speed
 * s runs a unit of cost c in c / s ms, exactly as the live emulation does.
 */
final class NodeLabWorkloads
{
	enum Shape
	{
		EQUAL("Equal", "every unit costs exactly the mean"),
		UNIFORM("Uniform", "costs spread evenly between half and one and a half times the mean"),
		HEAVY_TAIL("Heavy-tailed", "log-normal costs (sigma 1): a few units dominate the phase"),
		BIMODAL("Bimodal", "nine in ten units are small, one in ten is ten times larger");

		private final String label;
		private final String description;

		Shape(String label, String description)
		{
			this.label = label;
			this.description = description;
		}

		String getLabel()
		{
			return label;
		}

		String getDescription()
		{
			return description;
		}

		public String toString()
		{
			return label;
		}
	}

	private NodeLabWorkloads()
	{
	}

	/**
	 * A synthetic phase whose mean unit cost is exactly {@code meanMs} (the
	 * sampled costs are rescaled so that shapes differ only in spread).
	 *
	 * @param homes true gives every unit a home node holding its input
	 *              (round-robin over {@code nodes}); false keeps all data at
	 *              the coordinator
	 */
	static List<WorkUnitSpec> synthetic(int count, Shape shape, double meanMs, long inputBytes,
			long outputBytes, int nodes, boolean homes, long seed)
	{
		int safeCount = Math.max(1, count);
		double mean = meanMs > 0.0 ? meanMs : 1.0;
		FailureSchedule.SplitMix random = new FailureSchedule.SplitMix(WorkDispatcher.mix(seed ^ 0x57A7E5L));
		double[] costs = new double[safeCount];
		double total = 0.0;
		for (int index = 0; index < safeCount; index++)
		{
			double value;
			switch (shape == null ? Shape.EQUAL : shape)
			{
				case UNIFORM:
					value = 0.5 + random.nextUnit();
					break;
				case HEAVY_TAIL:
					value = Math.exp(random.nextGaussian());
					break;
				case BIMODAL:
					value = random.nextUnit() < 0.1 ? 10.0 : 1.0;
					break;
				default:
					value = 1.0;
					break;
			}
			costs[index] = value;
			total += value;
		}
		double scale = mean * safeCount / total;
		ArrayList<WorkUnitSpec> units = new ArrayList<WorkUnitSpec>();
		for (int index = 0; index < safeCount; index++)
		{
			int home = homes && nodes > 0 ? index % nodes + 1 : 0;
			units.add(new WorkUnitSpec(index + 1, String.format(Locale.US, "unit-%05d", index + 1),
					costs[index] * scale, 0, 0, Double.POSITIVE_INFINITY, home, null, 0,
					Math.max(0L, inputBytes), Math.max(0L, outputBytes), 0.0));
		}
		return Collections.unmodifiableList(units);
	}

	/**
	 * Units moved onto a cluster of {@code nodes} nodes: homes beyond the
	 * cluster wrap round-robin (as V3 would have spread the users), node
	 * restrictions are dropped (they named nodes of the old cluster).
	 */
	static List<WorkUnitSpec> onNodes(List<WorkUnitSpec> units, int nodes)
	{
		ArrayList<WorkUnitSpec> moved = new ArrayList<WorkUnitSpec>();
		for (WorkUnitSpec unit : units)
		{
			int home = unit.getHomeNode() <= 0 ? 0 : (unit.getHomeNode() - 1) % Math.max(1, nodes) + 1;
			moved.add(unit.withNodes(home, null));
		}
		return moved;
	}

	/** What a simulation of one phase needs besides the policy. */
	static final class Setup
	{
		List<NodeSpec> nodes;
		List<WorkUnitSpec> units;
		boolean faults;
		long faultSeed = ClusterSettings.DEFAULT_SEED;
		ClusterSettings settings = ClusterSettings.defaults();
		boolean crashes;
		Scenario scenario = Scenario.NONE;
		String phaseKey = RecommendationWorkQueueRegistry.CANDIDATES;
		double costNoise;
		long noiseSeed = 1L;
		boolean emulate = true;

		Setup(List<NodeSpec> nodes, List<WorkUnitSpec> units)
		{
			this.nodes = nodes;
			this.units = units;
		}
	}

	/** Simulates the phase under one policy (time unit: model milliseconds). */
	static SchedulingSimulator.Trace simulate(SchedulerLabEngine.SchedulingPolicy policy, Setup setup)
	{
		SchedulingSimulator.Config config = new SchedulingSimulator.Config(policy, setup.nodes, setup.units,
				new WorkDispatcher.Options()
						.faultInjection(setup.faults, setup.faultSeed)
						.maxFailuresPerUnit(RecommendationSchedulerRegistry.MAX_FAILURES_PER_UNIT),
				1.0)
				.phaseKey("node-lab/" + setup.phaseKey)
				.detectionTimeout(setup.settings.getDetectionTimeoutMs())
				.horizon(3.6e6);
		if (!setup.emulate)
		{
			config.network(NetworkModel.free(setup.nodes.size()));
		}
		if (setup.costNoise > 0.0)
		{
			config.costNoise(setup.costNoise, setup.noiseSeed);
		}
		if (setup.crashes)
		{
			config.crashes(FailureSchedule.forNodes(setup.nodes, setup.settings, setup.faultSeed),
					setup.settings.getDetectionTimeoutMs());
		}
		if (setup.scenario != null && !setup.scenario.isEmpty())
		{
			for (SchedulingSimulator.Control control : setup.scenario.controlsForPhase(setup.phaseKey, 0.0, 1.0))
			{
				if (control.getNode() <= setup.nodes.size())
				{
					config.control(control);
				}
			}
		}
		return SchedulingSimulator.run(config);
	}

	/** The node specs the next run would emulate (hardware mode: the unemulated model). */
	static List<NodeSpec> specsOf(List<NodeCapacityProfile> profiles, boolean emulate)
	{
		return NodeSpec.fromProfiles(profiles, null, emulate);
	}
}
