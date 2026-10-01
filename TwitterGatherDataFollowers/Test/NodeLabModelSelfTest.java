package TwitterGatherDataFollowers.userRyersonU;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * The physics of the node model, checked against closed-form answers: absolute
 * speeds, queue capacity under latency, link bandwidth and contention, crashes
 * with detection and repair, lower bounds, scenarios, crash-process
 * distributions and cost noise.
 */
public final class NodeLabModelSelfTest
{
	private static final double EPS = 1e-6;

	private NodeLabModelSelfTest()
	{
	}

	public static void main(String[] args) throws Exception
	{
		checkAbsoluteSpeeds();
		checkCapacityBoostHelps();
		checkQueueCapacityUnderLatency();
		checkPullPrefetchHidesLatency();
		checkBandwidthAndContention();
		checkDataLocalityCost();
		checkCrashDetectionAndRepair();
		checkDataBoundUnitsWaitForRepair();
		checkLowerBoundHolds(new Random(7L));
		checkNetworkAwareBoundHolds(new Random(11L));
		checkOriginalDsmpStaysHome();
		checkScenarioParsing();
		checkScenarioDrivesSimulator();
		checkFailureDistributions();
		checkCostNoiseIsMeanPreserving();
		checkProgressTriggers();
		checkTimeDilationChoice();
		checkPayloadSizes();
		System.out.println("NodeLabModelSelfTest passed");
	}

	/** Replacing a normal node by a strong one halves the makespan (the old model left it equal). */
	private static void checkAbsoluteSpeeds()
	{
		int heap = 6144;
		List<NodeCapacityProfile> normals = Arrays.asList(
				preset(1, NodeCapacityProfile.Role.NORMAL, heap),
				preset(2, NodeCapacityProfile.Role.NORMAL, heap),
				preset(3, NodeCapacityProfile.Role.NORMAL, heap));
		List<NodeCapacityProfile> withStrong = Arrays.asList(
				preset(1, NodeCapacityProfile.Role.NORMAL, heap),
				preset(2, NodeCapacityProfile.Role.NORMAL, heap),
				preset(3, NodeCapacityProfile.Role.STRONG, heap));
		double base = makespan(NodeSpec.fromProfiles(normals, null, true), 120, 10.0);
		double strong = makespan(NodeSpec.fromProfiles(withStrong, null, true), 120, 10.0);
		//Presets have real links: descriptors add microseconds, hence the tolerance.
		require(Math.abs(base - 400.0) < 0.05, "3 normal nodes: 1200 work / 3 = 400, got " + base);
		require(Math.abs(strong - 200.0) < 0.05,
				"2 normal + 1 strong (speed 2, 2 lanes) = throughput 6: 1200 / 6 = 200, got " + strong);
		List<NodeSpec> specs = NodeSpec.fromProfiles(withStrong, null, true);
		require(Math.abs(specs.get(0).getSpeed() - 1.0) < EPS,
				"a normal node's speed does not depend on the other nodes");
	}

	/** A live capacity boost of one node speeds the run up and slows nobody. */
	private static void checkCapacityBoostHelps()
	{
		List<NodeCapacityProfile> normals = Arrays.asList(
				preset(1, NodeCapacityProfile.Role.NORMAL, 6144),
				preset(2, NodeCapacityProfile.Role.NORMAL, 6144),
				preset(3, NodeCapacityProfile.Role.NORMAL, 6144));
		List<NodeSpec> boosted = NodeSpec.fromProfiles(normals, new double[] {2.0, 1.0, 1.0}, true);
		require(Math.abs(boosted.get(1).getSpeed() - 1.0) < EPS, "boosting node 1 leaves node 2 alone");
		double result = makespan(boosted, 120, 10.0);
		require(Math.abs(result - 300.0) < 0.05, "throughput 4: 1200 / 4 = 300, got " + result);
	}

	/**
	 * Static plan, one lane, latency 5 ms per direction, units of 10 ms. With
	 * queue capacity 1 each unit after the first waits for the completion notice
	 * and the next descriptor (2 x 5 ms); with capacity 2 the next unit is
	 * already there.
	 */
	private static void checkQueueCapacityUnderLatency()
	{
		List<WorkUnitSpec> units = units(10, 10.0);
		double tight = SchedulingSimulator.run(config(
				Arrays.asList(spec(1, 1.0, 1, 1, 5, 0.0)), units,
				SchedulerLabEngine.SchedulingPolicy.ROUND_ROBIN).checkInvariants(true)).getMakespan();
		double roomy = SchedulingSimulator.run(config(
				Arrays.asList(spec(1, 1.0, 1, 2, 5, 0.0)), units,
				SchedulerLabEngine.SchedulingPolicy.ROUND_ROBIN).checkInvariants(true)).getMakespan();
		//Capacity 1: plan delivery 5, then 10 units of 10 ms with 9 gaps of 10 ms.
		require(Math.abs(tight - (5.0 + 100.0 + 9 * 10.0)) < EPS, "queue capacity 1 under 5 ms latency: " + tight);
		//Capacity 2: the second unit arrives while the first runs; no gaps.
		require(Math.abs(roomy - (5.0 + 100.0)) < EPS, "queue capacity 2 hides the latency: " + roomy);
	}

	/** Pull without prefetch pays a request/reply round trip per unit; prefetch hides it. */
	private static void checkPullPrefetchHidesLatency()
	{
		List<WorkUnitSpec> units = units(8, 10.0);
		double noPrefetch = SchedulingSimulator.run(config(
				Arrays.asList(spec(1, 1.0, 1, 1, 4, 0.0)), units,
				SchedulerLabEngine.SchedulingPolicy.QUEUE_LENGTH).checkInvariants(true)).getMakespan();
		double prefetch = SchedulingSimulator.run(config(
				Arrays.asList(spec(1, 1.0, 1, 3, 4, 0.0)), units,
				SchedulerLabEngine.SchedulingPolicy.QUEUE_LENGTH).checkInvariants(true)).getMakespan();
		require(Math.abs(noPrefetch - 8 * (8.0 + 10.0)) < EPS,
				"pull with capacity 1: every unit waits a 2 x 4 ms round trip: " + noPrefetch);
		require(Math.abs(prefetch - (8.0 + 80.0)) < EPS,
				"prefetch hides every round trip after the first: " + prefetch);
	}

	/** Serialization is bytes / bandwidth; transfers on one link queue (FIFO). */
	private static void checkBandwidthAndContention()
	{
		ArrayList<NodeSpec> specs = new ArrayList<NodeSpec>();
		specs.add(spec(1, 1.0, 1, 1, 2, NodeSpec.mbpsToBytesPerMs(8.0)));
		NetworkModel network = new NetworkModel(specs, false);
		//8 Mbit/s = 1000 bytes per ms: 5000 bytes take 5 ms, then 2 ms latency.
		double first = network.send(NetworkModel.HUB, 1, 5000L, 0.0);
		double second = network.send(NetworkModel.HUB, 1, 5000L, 0.0);
		require(Math.abs(first - 7.0) < EPS, "5000 B at 1000 B/ms + 2 ms latency arrives at 7: " + first);
		require(Math.abs(second - 12.0) < EPS, "the second transfer waits for the link: " + second);
		double up = network.send(1, NetworkModel.HUB, 1000L, 0.0);
		require(Math.abs(up - 3.0) < EPS, "the up direction is independent (full duplex): " + up);
		require(network.bytesDown(1) == 10000L && network.bytesUp(1) == 1000L, "bytes are counted per link");
	}

	/** A unit that runs away from its home pays both links' latency and serialization. */
	private static void checkDataLocalityCost()
	{
		ArrayList<NodeSpec> specs = new ArrayList<NodeSpec>();
		specs.add(spec(1, 1.0, 1, 1, 3, NodeSpec.mbpsToBytesPerMs(8.0)));
		specs.add(spec(2, 1.0, 1, 1, 3, NodeSpec.mbpsToBytesPerMs(8.0)));
		ArrayList<WorkUnitSpec> units = new ArrayList<WorkUnitSpec>();
		units.add(new WorkUnitSpec(1, "a", 10.0, 0, 0, Double.POSITIVE_INFINITY, 1, new int[] {2})
				.withData(4000L, 0L));
		SchedulingSimulator.Trace trace = SchedulingSimulator.run(config(specs, units,
				SchedulerLabEngine.SchedulingPolicy.ROUND_ROBIN));
		SchedulingSimulator.Execution execution = trace.getExecutions().get(0);
		//Data: up link of node 1 (4 ms + 3 ms), down link of node 2 (4 ms + 3 ms) = 14.
		require(Math.abs(execution.getStart() - 14.0) < EPS,
				"input data crosses both links before the unit starts: " + execution.getStart());
		require(execution.getTransferredBytes() == 4000L, "the moved bytes are recorded");
	}

	/**
	 * A node crashes mid-run: its execution is lost, the coordinator reschedules
	 * its work only after the detection timeout, the phase completes exactly
	 * once, and the repaired node rejoins.
	 */
	private static void checkCrashDetectionAndRepair()
	{
		List<NodeSpec> specs = Arrays.asList(spec(1, 1.0, 1, 2, 0, 0.0), spec(2, 1.0, 1, 2, 0, 0.0));
		List<WorkUnitSpec> units = units(20, 10.0);
		SchedulingSimulator.Config config = config(specs, units, SchedulerLabEngine.SchedulingPolicy.ROUND_ROBIN)
				.checkInvariants(true).detectionTimeout(30.0);
		config.control(new SchedulingSimulator.Control(25.0, Double.NaN,
				SchedulingSimulator.Control.Kind.CRASH, 2, 0, null, 60.0));
		SchedulingSimulator.Trace trace = SchedulingSimulator.run(config);
		require(trace.isComplete(), "the phase completes despite the crash");
		require(trace.getCommittedNode().size() == units.size(), "every unit commits exactly once");
		boolean lost = false;
		for (SchedulingSimulator.Execution execution : trace.getExecutions())
		{
			if (execution.isLostToCrash())
			{
				lost = true;
				require(execution.getNode() == 2 && Math.abs(execution.getEnd() - 25.0) < EPS,
						"the running execution is lost at the crash");
			}
			if (execution.getNode() == 1 && execution.getStart() > 25.0 && execution.getStart() < 55.0 - EPS)
			{
				//Node 1 only runs its own plan until the crash is detected at 55.
				require(execution.getUnitId() % 2 == 1 || execution.getStart() >= 55.0 - EPS,
						"node 2's work moves to node 1 only after detection");
			}
			if (execution.getNode() == 2)
			{
				require(execution.getStart() < 25.0 + EPS || execution.getStart() >= 85.0 - EPS,
						"a crashed node starts nothing until its repair at 85");
			}
		}
		require(lost, "the crash lost an execution");
		require(Math.abs(trace.getDownTime(2) - 60.0) < EPS || trace.getMakespan() < 85.0,
				"down time is recorded: " + trace.getDownTime(2));
		require(trace.getCrashes() == 1, "one crash");
	}

	/** Units whose data lives on a crashed node wait for the repair (their data is unreadable). */
	private static void checkDataBoundUnitsWaitForRepair()
	{
		List<NodeSpec> specs = Arrays.asList(spec(1, 1.0, 1, 2, 0, 0.0), spec(2, 1.0, 1, 2, 0, 0.0));
		ArrayList<WorkUnitSpec> units = new ArrayList<WorkUnitSpec>();
		for (int id = 1; id <= 10; id++)
		{
			units.add(new WorkUnitSpec(id, String.format("d%02d", id), 10.0, 0, 0,
					Double.POSITIVE_INFINITY, 2, null).withData(1000L, 0L));
		}
		SchedulingSimulator.Config config = config(specs, units,
				SchedulerLabEngine.SchedulingPolicy.ORIGINAL_DSMP).detectionTimeout(5.0);
		config.control(new SchedulingSimulator.Control(15.0, Double.NaN,
				SchedulingSimulator.Control.Kind.CRASH, 2, 0, null, 100.0));
		SchedulingSimulator.Trace trace = SchedulingSimulator.run(config);
		require(trace.isComplete(), "data-bound work completes after the repair");
		for (SchedulingSimulator.Execution execution : trace.getExecutions())
		{
			require(execution.getStart() < 15.0 + EPS || execution.getStart() >= 115.0 - EPS,
					"no unit homed on node 2 can start while node 2 is down: " + execution.getStart());
		}
		require(trace.getMakespan() >= 115.0 + 80.0 - EPS, "the outage delays the phase: " + trace.getMakespan());
	}

	/** No policy ever beats the lower bound (communication-free, fault-free workloads). */
	private static void checkLowerBoundHolds(Random random)
	{
		for (int trial = 0; trial < 60; trial++)
		{
			ArrayList<NodeSpec> specs = new ArrayList<NodeSpec>();
			int nodes = 1 + random.nextInt(4);
			for (int node = 1; node <= nodes; node++)
			{
				int lanes = 1 + random.nextInt(3);
				specs.add(spec(node, 0.25 + random.nextDouble() * 3.0, lanes, lanes + random.nextInt(3), 0, 0.0));
			}
			ArrayList<WorkUnitSpec> units = new ArrayList<WorkUnitSpec>();
			for (int id = 1; id <= 5 + random.nextInt(40); id++)
			{
				units.add(new WorkUnitSpec(id, "u" + random.nextInt(99999), 1.0 + random.nextInt(50))
						.withCost(1.0 + random.nextInt(50), random.nextInt(3) == 0 ? random.nextInt(5) : 0.0));
			}
			double bound = PlacementPlanner.lowerBound(units, specs);
			for (SchedulerLabEngine.SchedulingPolicy policy : SchedulerLabEngine.SchedulingPolicy.values())
			{
				SchedulingSimulator.Trace trace = SchedulingSimulator.run(config(specs, units, policy));
				require(trace.isComplete(), policy + " completes");
				require(trace.getMakespan() >= bound - 1e-9,
						policy + " beat the lower bound: " + trace.getMakespan() + " < " + bound);
			}
		}
	}

	/**
	 * The communication- and availability-aware bound never exceeds what a
	 * policy achieves: random clusters with real links, units with data homes,
	 * late node joins, estimate noise, capacity boosts and drops, faults and
	 * crashes, under every policy.
	 */
	private static void checkNetworkAwareBoundHolds(Random random)
	{
		int checked = 0;
		double tightest = 0.0;
		for (int trial = 0; trial < 150; trial++)
		{
			ArrayList<NodeSpec> specs = new ArrayList<NodeSpec>();
			int nodes = 1 + random.nextInt(5);
			for (int node = 1; node <= nodes; node++)
			{
				int lanes = 1 + random.nextInt(3);
				double bandwidth = random.nextBoolean() ? 0.0 : 10.0 + random.nextInt(20000);
				specs.add(new NodeSpec(node, "N", 1.0, 1.0, 1.0, 0.25 + random.nextDouble() * 3.0, lanes, 1 << 20,
						lanes + random.nextInt(3), random.nextInt(4) == 0 ? 0 : random.nextInt(60), bandwidth,
						random.nextInt(5) == 0 ? 0.05 : 0.0, random.nextInt(6) == 0 ? 300.0 + random.nextInt(3000) : 0.0,
						20.0 + random.nextInt(200)));
			}
			ArrayList<WorkUnitSpec> units = new ArrayList<WorkUnitSpec>();
			int count = 3 + random.nextInt(50);
			for (int id = 1; id <= count; id++)
			{
				int home = random.nextInt(3) == 0 ? 0 : 1 + random.nextInt(nodes);
				int[] allowed = random.nextInt(8) == 0 && home > 0 ? new int[] {home} : null;
				units.add(new WorkUnitSpec(id, "u" + id, 1.0 + random.nextInt(60), 0, random.nextInt(3), Double.POSITIVE_INFINITY,
						home, allowed, 0, random.nextInt(3) == 0 ? 0L : random.nextInt(400000), random.nextInt(20000),
						random.nextInt(4) == 0 ? random.nextInt(8) : 0.0));
			}
			double[] joins = new double[nodes];
			for (int node = 0; node < nodes; node++)
			{
				joins[node] = random.nextInt(3) == 0 ? 50.0 + random.nextInt(400) : Double.NaN;
			}
			boolean noisy = random.nextBoolean();
			boolean faulty = random.nextInt(3) == 0;
			boolean boosted = random.nextInt(3) == 0;
			for (SchedulerLabEngine.SchedulingPolicy policy : SchedulerLabEngine.SchedulingPolicy.values())
			{
				SchedulingSimulator.Config config = new SchedulingSimulator.Config(policy, specs, units,
						new WorkDispatcher.Options().faultInjection(faulty, 99L + trial), 1.0)
						.startTime(10.0)
						.nodeStartTimes(shift(joins, 10.0))
						.detectionTimeout(40.0)
						.horizon(5.0e6);
				if (noisy)
				{
					config.costNoise(0.6, 1234L + trial);
				}
				if (boosted)
				{
					config.control(SchedulingSimulator.Control.atProgress(0.3, SchedulingSimulator.Control.Kind.CAPACITY,
							1, 0, null, 2.5));
					config.control(new SchedulingSimulator.Control(60.0, Double.NaN, SchedulingSimulator.Control.Kind.CAPACITY,
							nodes, 0, null, 0.4));
				}
				if (faulty)
				{
					config.crashes(FailureSchedule.forNodes(specs, ClusterSettings.defaults(), 7L + trial), 40.0);
				}
				SchedulingSimulator.Trace trace = SchedulingSimulator.run(config);
				if (!trace.isComplete())
				{
					continue;
				}
				double bound = trace.getLowerBound();
				require(trace.getMakespan() >= bound - 1e-6, policy + " (trial " + trial + ") beat the network-aware bound: "
						+ trace.getMakespan() + " < " + bound);
				tightest = Math.max(tightest, bound / trace.getMakespan());
				checked++;
			}
		}
		require(checked > 1000, "too few completed phases to check the bound: " + checked);
		require(tightest > 0.9, "the bound should be tight on some phase, best ratio " + tightest);
	}

	private static double[] shift(double[] values, double offset)
	{
		double[] shifted = new double[values.length];
		for (int index = 0; index < values.length; index++)
		{
			shifted[index] = Double.isNaN(values[index]) ? Double.NaN : values[index] + offset;
		}
		return shifted;
	}

	/** The V3 policy keeps every unit on its home node while all nodes are up. */
	private static void checkOriginalDsmpStaysHome()
	{
		List<NodeSpec> specs = Arrays.asList(spec(1, 4.0, 2, 4, 0, 0.0), spec(2, 0.25, 1, 2, 0, 0.0));
		ArrayList<WorkUnitSpec> units = new ArrayList<WorkUnitSpec>();
		for (int id = 1; id <= 12; id++)
		{
			units.add(new WorkUnitSpec(id, String.format("h%02d", id), 5.0, 0, 0,
					Double.POSITIVE_INFINITY, id % 2 == 0 ? 2 : 1, null).withData(100L, 0L));
		}
		SchedulingSimulator.Trace trace = SchedulingSimulator.run(config(specs, units,
				SchedulerLabEngine.SchedulingPolicy.ORIGINAL_DSMP));
		for (Map.Entry<Integer,Integer> commit : trace.getCommittedNode().entrySet())
		{
			int expected = commit.getKey().intValue() % 2 == 0 ? 2 : 1;
			require(commit.getValue().intValue() == expected, "original DSMP runs every unit at home");
		}
		double capacity = SchedulingSimulator.run(config(specs, units,
				SchedulerLabEngine.SchedulingPolicy.CAPACITY_WEIGHTED)).getMakespan();
		require(capacity < trace.getMakespan(), "capacity-aware placement beats V3 on this uneven cluster");
	}

	private static void checkScenarioParsing() throws Exception
	{
		Scenario scenario = Scenario.parse(
				"name: Test\n"
				+ "# comment\n"
				+ "at 1.5s pause node 2\n"
				+ "at 2000ms resume node 2\n"
				+ "at 40% of candidates drain node 3 to node 1\n"
				+ "at 25% crash node 1 for 2s\n"
				+ "when svm opens + 200ms capacity node 1 = 50%\n"
				+ "at 3 policy work stealing\n"
				+ "at 4s redirect 5 units to node 2\n"
				+ "at 50% of input unavailable node 1\n"
				+ "at 6s policy plan\n"
				+ "at 7s recover node 1\n"
				+ "at 8s action window 40ms\n"
				+ "at 9s reset\n");
		require(scenario.getSteps().size() == 12, "every step parses: " + scenario.getSteps().size());
		Scenario.Step pause = scenario.getSteps().get(0);
		require(pause.getTrigger().getKind() == Scenario.TriggerKind.TIME
				&& Math.abs(pause.getTrigger().getTimeMs() - 1500.0) < EPS, "1.5s is 1500 ms");
		Scenario.Step drain = scenario.getSteps().get(2);
		require(drain.getTrigger().getKind() == Scenario.TriggerKind.PROGRESS
				&& drain.getTrigger().getPhase().equals(RecommendationWorkQueueRegistry.CANDIDATES)
				&& Math.abs(drain.getTrigger().getFraction() - 0.4) < EPS
				&& drain.getAction().getTarget() == 1, "progress trigger with a drain target");
		Scenario.Step crash = scenario.getSteps().get(3);
		require(crash.getTrigger().getPhase().length() == 0
				&& Math.abs(crash.getAction().getValue() - 2000.0) < EPS, "crash outage of 2 s");
		Scenario.Step policy = scenario.getSteps().get(5);
		require(Math.abs(policy.getTrigger().getTimeMs() - 3000.0) < EPS
				&& policy.getAction().getPolicy() == SchedulerLabEngine.SchedulingPolicy.WORK_STEALING,
				"plain numbers are seconds; policy names resolve");
		require(scenario.getSteps().get(7).getTrigger().getKind() == Scenario.TriggerKind.INPUT_PROGRESS,
				"input progress trigger");
		require(scenario.getSteps().get(8).getAction().getPolicy() == null, "policy plan returns to the plan");
		String[] invalid = {
			"at 2s pause",
			"at 120% pause node 1",
			"sometime pause node 1",
			"at 2s capacity node 1 = 0%",
			"at 2s policy fastest possible",
			"at 2s redirect 0 units to node 1"
		};
		for (int index = 0; index < invalid.length; index++)
		{
			boolean rejected = false;
			try
			{
				Scenario.parse("name: bad\n\n" + invalid[index]);
			}
			catch (Scenario.ScenarioException expected)
			{
				rejected = expected.getLine() == 3;
			}
			require(rejected, "rejects with the right line: " + invalid[index]);
		}
		for (Scenario builtIn : Scenario.library())
		{
			Scenario reparsed = Scenario.parse(builtIn.toText());
			require(reparsed.getSteps().size() == builtIn.getSteps().size(), "built-in scenarios round-trip");
		}
	}

	/** A scenario turned into controls acts exactly like the same controls given directly. */
	private static void checkScenarioDrivesSimulator() throws Exception
	{
		List<NodeSpec> specs = Arrays.asList(spec(1, 1.0, 1, 2, 1, 0.0), spec(2, 1.0, 1, 2, 1, 0.0),
				spec(3, 1.0, 1, 2, 1, 0.0));
		List<WorkUnitSpec> units = units(30, 7.0);
		Scenario scenario = Scenario.parse("at 20ms drain node 2\nat 50% pause node 3\nat 80% resume node 3\n");
		SchedulingSimulator.Config fromScenario = config(specs, units,
				SchedulerLabEngine.SchedulingPolicy.QUEUE_LENGTH).checkInvariants(true);
		for (SchedulingSimulator.Control control : scenario.controlsForPhase("p", 0.0, 1.0))
		{
			fromScenario.control(control);
		}
		SchedulingSimulator.Config direct = config(specs, units,
				SchedulerLabEngine.SchedulingPolicy.QUEUE_LENGTH).checkInvariants(true);
		direct.control(new SchedulingSimulator.Control(20.0, SchedulingSimulator.Control.Kind.DRAIN, 2, 0, null));
		direct.control(SchedulingSimulator.Control.atProgress(0.5, SchedulingSimulator.Control.Kind.PAUSE, 3, 0,
				null, Double.NaN));
		direct.control(SchedulingSimulator.Control.atProgress(0.8, SchedulingSimulator.Control.Kind.RESUME, 3, 0,
				null, Double.NaN));
		SchedulingSimulator.Trace a = SchedulingSimulator.run(fromScenario);
		SchedulingSimulator.Trace b = SchedulingSimulator.run(direct);
		require(a.getViolations().isEmpty() && b.getViolations().isEmpty(), "no invariant violations");
		require(a.isComplete() && Math.abs(a.getMakespan() - b.getMakespan()) < EPS,
				"scenario and direct controls agree: " + a.getMakespan() + " vs " + b.getMakespan());
		for (SchedulingSimulator.Execution execution : a.getExecutions())
		{
			if (execution.getNode() == 2)
			{
				require(execution.getStart() < 20.0 + EPS, "the drained node starts nothing after the drain");
			}
		}
	}

	/** Seeded crash processes have the right mean up-time and are reproducible. */
	private static void checkFailureDistributions()
	{
		for (ClusterSettings.UptimeDistribution distribution : ClusterSettings.UptimeDistribution.values())
		{
			ClusterSettings settings = ClusterSettings.defaults().withFailureDistributions(
					distribution, 2.0, ClusterSettings.RepairDistribution.EXPONENTIAL);
			FailureSchedule schedule = new FailureSchedule(1, 1000.0, 100.0, settings, 42L);
			List<FailureSchedule.Outage> outages = schedule.outagesBefore(2.0e6);
			double upSum = 0.0;
			double downSum = 0.0;
			double previousEnd = 0.0;
			for (FailureSchedule.Outage outage : outages)
			{
				upSum += outage.getCrashAtMs() - previousEnd;
				downSum += outage.getDowntimeMs();
				previousEnd = outage.getRecoverAtMs();
			}
			double meanUp = upSum / outages.size();
			double meanDown = downSum / outages.size();
			require(outages.size() > 1000, distribution + " produced enough outages");
			require(Math.abs(meanUp - 1000.0) < 60.0, distribution + " mean up-time " + meanUp + " vs MTBF 1000");
			require(Math.abs(meanDown - 100.0) < 8.0, "exponential repairs have mean MTTR: " + meanDown);
			FailureSchedule again = new FailureSchedule(1, 1000.0, 100.0, settings, 42L);
			require(Math.abs(again.outagesBefore(2.0e6).get(17).getCrashAtMs()
					- outages.get(17).getCrashAtMs()) < EPS, "the same seed gives the same crashes");
		}
		require(Math.abs(FailureSchedule.gamma(5.0) - 24.0) < 1e-9, "gamma(5) = 4!");
		require(Math.abs(FailureSchedule.gamma(0.5) - Math.sqrt(Math.PI)) < 1e-9, "gamma(1/2) = sqrt(pi)");
	}

	/** Log-normal cost noise leaves the mean cost unchanged. */
	private static void checkCostNoiseIsMeanPreserving()
	{
		List<NodeSpec> specs = Arrays.asList(spec(1, 1.0, 1, 1, 0, 0.0));
		List<WorkUnitSpec> units = units(4000, 10.0);
		SchedulingSimulator.Trace trace = SchedulingSimulator.run(config(specs, units,
				SchedulerLabEngine.SchedulingPolicy.ROUND_ROBIN).costNoise(0.5, 99L));
		double mean = trace.getMakespan() / units.size();
		require(Math.abs(mean - 10.0) < 0.35, "noisy costs average to the estimate: " + mean);
		double spread = 0.0;
		for (SchedulingSimulator.Execution execution : trace.getExecutions())
		{
			spread = Math.max(spread, execution.getEnd() - execution.getStart());
		}
		require(spread > 20.0, "noise really varies unit costs");
	}

	/** A progress-triggered control fires when the committed fraction first reaches it. */
	private static void checkProgressTriggers()
	{
		List<NodeSpec> specs = Arrays.asList(spec(1, 1.0, 1, 2, 0, 0.0), spec(2, 1.0, 1, 2, 0, 0.0));
		List<WorkUnitSpec> units = units(20, 10.0);
		SchedulingSimulator.Config config = config(specs, units, SchedulerLabEngine.SchedulingPolicy.QUEUE_LENGTH);
		config.control(SchedulingSimulator.Control.atProgress(0.5, SchedulingSimulator.Control.Kind.PAUSE, 2, 0,
				null, Double.NaN));
		SchedulingSimulator.Trace trace = SchedulingSimulator.run(config);
		require(trace.isComplete(), "completes after the pause");
		//Half the units (10) commit by t=50; node 2 starts nothing after that.
		for (SchedulingSimulator.Execution execution : trace.getExecutions())
		{
			if (execution.getNode() == 2)
			{
				require(execution.getStart() < 50.0 + EPS, "paused at 50% progress");
			}
		}
		require(Math.abs(trace.getMakespan() - 150.0) < EPS,
				"the second half runs on node 1 alone: " + trace.getMakespan());
	}

	/** The automatic TDF covers the fastest node and keeps the host within its cores. */
	private static void checkTimeDilationChoice()
	{
		ClusterSettings settings = ClusterSettings.defaults();
		List<NodeSpec> slow = Arrays.asList(spec(1, 0.5, 1, 1, 0, 0.0), spec(2, 0.25, 1, 1, 0, 0.0));
		require(Math.abs(SchedulingRunContext.chooseDilation(settings, slow, true) - 1.0) < EPS,
				"TDF is at least 1");
		List<NodeSpec> fast = Arrays.asList(spec(1, 3.0, 1, 1, 0, 0.0), spec(2, 1.0, 1, 1, 0, 0.0));
		require(SchedulingRunContext.chooseDilation(settings, fast, true) >= 3.0 - EPS,
				"TDF covers the fastest node");
		require(SchedulingRunContext.chooseDilation(settings.withBoostHeadroom(2.0), fast, true) >= 6.0 - EPS,
				"TDF covers the boost headroom");
		require(Math.abs(SchedulingRunContext.chooseDilation(settings.withTimeDilation(5.0), fast, true) - 5.0)
				< EPS, "a fixed TDF is used as given");
		ArrayList<NodeSpec> many = new ArrayList<NodeSpec>();
		for (int node = 1; node <= 64; node++)
		{
			many.add(spec(node, 1.0, 4, 4, 0, 0.0));
		}
		int usable = Math.max(1, Runtime.getRuntime().availableProcessors() - 2);
		require(SchedulingRunContext.chooseDilation(settings, many, true) >= 256.0 / usable - EPS,
				"TDF keeps the busy lanes within the host's cores");
		require(Math.abs(SchedulingRunContext.chooseDilation(settings, many, false) - 1.0) < EPS,
				"unemulated runs have TDF 1");
	}

	private static void checkPayloadSizes()
	{
		require(PayloadSize.of("abc") == 5L, "string: 2 + UTF-8 bytes");
		require(PayloadSize.of("é") == 4L, "non-ASCII strings use UTF-8");
		HashMap<String,Double> vector = new HashMap<String,Double>();
		vector.put("term", Double.valueOf(1.0));
		require(PayloadSize.of(vector) == 4L + 6L + 8L, "map of term to weight");
		require(PayloadSize.sparseVector(vector) == PayloadSize.of(vector), "sparse vector size agrees");
		require(PayloadSize.of(new double[3]) == 28L, "double[3]");
		require(PayloadSize.of(Arrays.asList("a", "b")) == 4L + 3L + 3L, "list of strings");
	}

	// ------------------------------------------------------------ helpers

	private static NodeCapacityProfile preset(int node, NodeCapacityProfile.Role role, int heap)
	{
		return NodeCapacityProfile.rolePreset(node, role, 3, heap);
	}

	private static NodeSpec spec(int index, double speed, int lanes, int queue, int delayMs,
			double bytesPerMs)
	{
		return new NodeSpec(index, "T", 1.0, 1.0, 1.0, speed, lanes, 1 << 20, queue, delayMs,
				bytesPerMs, 0.0, 0.0, 0.0);
	}

	private static List<WorkUnitSpec> units(int count, double cost)
	{
		ArrayList<WorkUnitSpec> units = new ArrayList<WorkUnitSpec>();
		for (int id = 1; id <= count; id++)
		{
			units.add(new WorkUnitSpec(id, String.format("u%04d", id), cost));
		}
		return units;
	}

	private static SchedulingSimulator.Config config(List<NodeSpec> specs, List<WorkUnitSpec> units,
			SchedulerLabEngine.SchedulingPolicy policy)
	{
		return new SchedulingSimulator.Config(policy, specs, units, new WorkDispatcher.Options(), 1.0);
	}

	private static double makespan(List<NodeSpec> specs, int count, double cost)
	{
		return SchedulingSimulator.run(config(specs, units(count, cost),
				SchedulerLabEngine.SchedulingPolicy.CAPACITY_WEIGHTED)).getMakespan();
	}

	private static void require(boolean condition, String message)
	{
		if (!condition)
		{
			throw new AssertionError(message);
		}
	}
}
