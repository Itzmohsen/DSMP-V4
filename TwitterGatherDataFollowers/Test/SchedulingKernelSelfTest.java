package TwitterGatherDataFollowers.userRyersonU;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Correctness and quality checks for the scheduling kernel: the placement
 * planner, the work dispatcher, the discrete-event simulator and the blocking
 * work phase.
 */
public final class SchedulingKernelSelfTest
{
	private static final SchedulerLabEngine.SchedulingPolicy[] POLICIES =
			SchedulerLabEngine.SchedulingPolicy.values();

	private SchedulingKernelSelfTest()
	{
	}

	public static void main(String[] args) throws Exception
	{
		Random random = new Random(424242L);
		checkBlockSplit();
		checkRoundRobin();
		checkSmoothWeightedRoundRobin();
		checkOriginalListScheduling(random);
		checkApproximationBounds(random);
		checkDeterminismAndOrderIndependence(random);
		checkMemoryAwarePlacement();
		checkStaticExecutionFollowsPlan(random);
		checkExactlyOnceUnderEverything(random);
		checkPullWorkConservation(random);
		checkFailureAwareExclusion();
		checkDrainRedirectPause();
		checkStealingHelpsMisestimatedSpeeds(random);
		checkInfeasibleMemoryFailsFast();
		checkBindingMemoryAdmission(random);
		checkSimulationDeterminism(random);
		checkThreadedWorkPhase(random);
		System.out.println("SchedulingKernelSelfTest passed");
	}

	// ----------------------------------------------------------- planner

	private static void checkBlockSplit()
	{
		List<NodeSpec> nodes = specs(new double[] {1.0, 0.5, 0.25}, 1);
		List<WorkUnitSpec> units = units(10, 1.0);
		PlacementPlanner.Plan plan = PlacementPlanner.plan(
				SchedulerLabEngine.SchedulingPolicy.STATIC_EQUAL_SPLIT,
				units, nodes, null, false, false, 0.0);
		int[] expected = {4, 3, 3};
		for (int node = 1; node <= 3; node++)
		{
			require(plan.unitCount(node) == expected[node - 1],
					"block sizes are floor/ceil of U/N in order");
		}
		//Blocks are contiguous in canonical order.
		int previousNode = 0;
		List<WorkUnitSpec> sorted = new ArrayList<WorkUnitSpec>(units);
		Collections.sort(sorted, WorkUnitSpec.CANONICAL);
		for (WorkUnitSpec unit : sorted)
		{
			int node = plan.nodeOfUnit(unit.getId());
			require(node >= previousNode, "equal split blocks are contiguous");
			previousNode = node;
		}
		require(Math.abs(plan.unitCount(1) - plan.unitCount(3)) <= 1, "balanced blocks");
	}

	private static void checkRoundRobin()
	{
		List<NodeSpec> nodes = specs(new double[] {1.0, 0.3, 0.6, 0.9}, 1);
		List<WorkUnitSpec> units = units(11, 2.0);
		PlacementPlanner.Plan plan = PlacementPlanner.plan(
				SchedulerLabEngine.SchedulingPolicy.ROUND_ROBIN,
				units, nodes, null, false, false, 0.0);
		List<WorkUnitSpec> sorted = new ArrayList<WorkUnitSpec>(units);
		Collections.sort(sorted, WorkUnitSpec.CANONICAL);
		for (int position = 0; position < sorted.size(); position++)
		{
			require(plan.nodeOfUnit(sorted.get(position).getId()) == position % 4 + 1,
					"round robin sends unit k to node k mod N");
		}
	}

	private static void checkSmoothWeightedRoundRobin()
	{
		double[] speeds = {1.0, 0.5, 0.25};
		int[] lanes = {2, 1, 1};
		List<NodeSpec> nodes = specs(speeds, lanes);
		double totalWeight = 0.0;
		for (NodeSpec node : nodes)
		{
			totalWeight += node.getThroughput();
		}
		for (int count = 1; count <= 40; count++)
		{
			PlacementPlanner.Plan plan = PlacementPlanner.plan(
					SchedulerLabEngine.SchedulingPolicy.WEIGHTED_ROUND_ROBIN,
					units(count, 1.0), nodes, null, true, false, 0.0);
			for (NodeSpec node : nodes)
			{
				double share = count * node.getThroughput() / totalWeight;
				require(Math.abs(plan.unitCount(node.getIndex()) - share) < 1.0 + 1e-9,
						"smooth weighted round robin stays within one unit of its share");
			}
		}
	}

	private static void checkOriginalListScheduling(Random random)
	{
		for (int trial = 0; trial < 200; trial++)
		{
			int nodeCount = 1 + random.nextInt(6);
			ArrayList<WorkUnitSpec> units = new ArrayList<WorkUnitSpec>();
			int count = 1 + random.nextInt(40);
			for (int id = 1; id <= count; id++)
			{
				units.add(new WorkUnitSpec(id, "u" + random.nextInt(1000), random.nextInt(50)));
			}
			PlacementPlanner.Plan plan =
					PlacementPlanner.planOriginalListScheduling(units, nodeCount, null);
			//Reference: the V3 findSmallestBin loop over the same list order.
			long[] bins = new long[nodeCount];
			for (WorkUnitSpec unit : units)
			{
				int smallest = 0;
				for (int index = 0; index < bins.length; index++)
				{
					if (bins[index] < bins[smallest])
					{
						smallest = index;
					}
				}
				bins[smallest] += (long)unit.getCost();
				require(plan.nodeOfUnit(unit.getId()) == smallest + 1,
						"original list scheduling reproduces V3 exactly");
			}
		}
	}

	/**
	 * Graham: list scheduling on identical machines is within 2 - 1/m of the
	 * optimum. Gonzalez, Ibarra and Sahni: LPT with earliest finish time on
	 * uniform machines is within 2m/(m+1). Both are checked against brute force.
	 */
	private static void checkApproximationBounds(Random random)
	{
		for (int trial = 0; trial < 300; trial++)
		{
			int machines = 2 + random.nextInt(2);
			int jobs = 3 + random.nextInt(6);
			double[] speeds = new double[machines];
			for (int index = 0; index < machines; index++)
			{
				speeds[index] = trial % 2 == 0 ? 1.0 : 0.2 + random.nextDouble() * 0.8;
			}
			speeds[0] = 1.0;
			List<NodeSpec> nodes = specs(speeds, 1);
			ArrayList<WorkUnitSpec> units = new ArrayList<WorkUnitSpec>();
			double[] costs = new double[jobs];
			for (int id = 1; id <= jobs; id++)
			{
				costs[id - 1] = 1 + random.nextInt(20);
				units.add(new WorkUnitSpec(id, "j" + id, costs[id - 1]));
			}
			double optimum = bruteForceMakespan(costs, speeds);
			PlacementPlanner.Plan lpt = PlacementPlanner.plan(
					SchedulerLabEngine.SchedulingPolicy.CAPACITY_WEIGHTED,
					units, nodes, null, false, false, 0.0);
			double bound = 2.0 * machines / (machines + 1.0);
			require(lpt.predictedMakespan() <= bound * optimum + 1e-9,
					"LPT-EFT within 2m/(m+1) of optimal: " + lpt.predictedMakespan()
					+ " vs " + optimum);
			if (trial % 2 == 0)
			{
				PlacementPlanner.Plan graham = PlacementPlanner.plan(
						SchedulerLabEngine.SchedulingPolicy.LEAST_LOADED_NODE,
						units, nodes, null, false, false, 0.0);
				require(graham.predictedMakespan()
						<= (2.0 - 1.0 / machines) * optimum + 1e-9,
						"Graham list scheduling within 2 - 1/m of optimal");
				require(lpt.predictedMakespan()
						<= (4.0 / 3.0 - 1.0 / (3.0 * machines)) * optimum + 1e-9,
						"LPT on identical machines within 4/3 - 1/(3m) of optimal");
			}
		}
	}

	private static double bruteForceMakespan(double[] costs, double[] speeds)
	{
		int machines = speeds.length;
		int jobs = costs.length;
		int assignments = (int)Math.pow(machines, jobs);
		double best = Double.POSITIVE_INFINITY;
		for (int code = 0; code < assignments; code++)
		{
			double[] load = new double[machines];
			int value = code;
			for (int job = 0; job < jobs; job++)
			{
				load[value % machines] += costs[job];
				value /= machines;
			}
			double makespan = 0.0;
			for (int machine = 0; machine < machines; machine++)
			{
				makespan = Math.max(makespan, load[machine] / speeds[machine]);
			}
			best = Math.min(best, makespan);
		}
		return best;
	}

	private static void checkDeterminismAndOrderIndependence(Random random)
	{
		for (int trial = 0; trial < 40; trial++)
		{
			List<NodeSpec> nodes = randomSpecs(random, 2 + random.nextInt(4));
			ArrayList<WorkUnitSpec> units = new ArrayList<WorkUnitSpec>();
			for (int id = 1; id <= 30; id++)
			{
				units.add(new WorkUnitSpec(id, String.format("k%03d", random.nextInt(500)),
						1 + random.nextInt(30), random.nextInt(64), random.nextInt(3),
						random.nextInt(100), 0, null));
			}
			for (SchedulerLabEngine.SchedulingPolicy policy : POLICIES)
			{
				PlacementPlanner.Plan first = PlacementPlanner.plan(
						policy, units, nodes, null, true, false, 0.0);
				ArrayList<WorkUnitSpec> shuffled = new ArrayList<WorkUnitSpec>(units);
				Collections.shuffle(shuffled, random);
				PlacementPlanner.Plan second = PlacementPlanner.plan(
						policy, shuffled, nodes, null, true, false, 0.0);
				for (WorkUnitSpec unit : units)
				{
					require(first.nodeOfUnit(unit.getId()) == second.nodeOfUnit(unit.getId()),
							policy + " placement must not depend on submission order");
				}
			}
		}
	}

	private static void checkMemoryAwarePlacement()
	{
		List<NodeSpec> nodes = new ArrayList<NodeSpec>();
		nodes.add(new NodeSpec(1, "A", 1, 1, 1, 1.0, 1, 100, 1, 0, 0));
		nodes.add(new NodeSpec(2, "B", 1, 1, 1, 0.5, 1, 300, 1, 0, 0));
		ArrayList<WorkUnitSpec> units = new ArrayList<WorkUnitSpec>();
		int[] memory = {90, 80, 70, 60, 50};
		for (int id = 1; id <= memory.length; id++)
		{
			units.add(new WorkUnitSpec(id, "m" + id, 5, memory[id - 1], 0, 0, 0, null));
		}
		PlacementPlanner.Plan plan = PlacementPlanner.plan(
				SchedulerLabEngine.SchedulingPolicy.MEMORY_AWARE_SAFE_WAVE,
				units, nodes, null, false, true, 0.0);
		require(plan.getWarnings().isEmpty(), "memory-aware placement respects budgets");
		units.add(new WorkUnitSpec(6, "m6", 5, 250, 0, 0, 0, null));
		boolean rejected = false;
		try
		{
			PlacementPlanner.plan(SchedulerLabEngine.SchedulingPolicy.MEMORY_AWARE_SAFE_WAVE,
					units, nodes, null, false, true, 0.0);
		}
		catch (IllegalArgumentException expected)
		{
			rejected = true;
		}
		require(rejected, "infeasible memory placement fails with a clear error");
	}

	// ----------------------------------------------------------- dispatcher

	private static void checkStaticExecutionFollowsPlan(Random random)
	{
		SchedulerLabEngine.SchedulingPolicy[] staticPolicies = {
			SchedulerLabEngine.SchedulingPolicy.STATIC_EQUAL_SPLIT,
			SchedulerLabEngine.SchedulingPolicy.ROUND_ROBIN,
			SchedulerLabEngine.SchedulingPolicy.WEIGHTED_ROUND_ROBIN,
			SchedulerLabEngine.SchedulingPolicy.LEAST_LOADED_NODE,
			SchedulerLabEngine.SchedulingPolicy.FASTEST_NODE_FIRST,
			SchedulerLabEngine.SchedulingPolicy.CAPACITY_WEIGHTED
		};
		for (int trial = 0; trial < 20; trial++)
		{
			List<NodeSpec> nodes = randomSpecs(random, 2 + random.nextInt(3));
			List<WorkUnitSpec> units = randomUnits(random, 25, false);
			for (SchedulerLabEngine.SchedulingPolicy policy : staticPolicies)
			{
				PlacementPlanner.Plan plan = PlacementPlanner.plan(
						policy, units, nodes, null, true, false, 100.0);
				SchedulingSimulator.Trace trace = SchedulingSimulator.run(
						new SchedulingSimulator.Config(policy, nodes, units,
								new WorkDispatcher.Options(), 100.0));
				require(trace.isComplete(), policy + " simulation completes");
				for (WorkUnitSpec unit : units)
				{
					require(trace.getCommittedNode().get(Integer.valueOf(unit.getId())).intValue()
							== plan.nodeOfUnit(unit.getId()),
							policy + " executes exactly its static plan");
				}
			}
		}
	}

	private static void checkExactlyOnceUnderEverything(Random random)
	{
		for (int trial = 0; trial < 60; trial++)
		{
			int nodeCount = 2 + random.nextInt(4);
			List<NodeSpec> nodes = randomSpecsWithFailures(random, nodeCount);
			List<WorkUnitSpec> units = randomUnits(random, 20 + random.nextInt(40), true);
			for (SchedulerLabEngine.SchedulingPolicy policy : POLICIES)
			{
				SchedulingSimulator.Config config = new SchedulingSimulator.Config(
						policy, nodes, units,
						new WorkDispatcher.Options()
								.faultInjection(true, trial)
								.maxFailuresPerUnit(60),
						100.0).checkInvariants(true).phaseKey("trial-" + trial);
				int victim = 1 + random.nextInt(nodeCount);
				config.control(new SchedulingSimulator.Control(3.0 + random.nextInt(20),
						SchedulingSimulator.Control.Kind.DRAIN, victim, 0, null));
				config.control(new SchedulingSimulator.Control(30.0 + random.nextInt(20),
						SchedulingSimulator.Control.Kind.RESUME, victim, 0, null));
				int other = 1 + random.nextInt(nodeCount);
				config.control(new SchedulingSimulator.Control(10.0 + random.nextInt(10),
						SchedulingSimulator.Control.Kind.REDIRECT, other, 3, null));
				config.control(new SchedulingSimulator.Control(15.0 + random.nextInt(10),
						SchedulingSimulator.Control.Kind.SWITCH_POLICY, 0, 0,
						POLICIES[random.nextInt(POLICIES.length)]));
				SchedulingSimulator.Trace trace = SchedulingSimulator.run(config);
				require(trace.getViolations().isEmpty(),
						policy + " violated an invariant: " + trace.getViolations());
				require(trace.isComplete(), policy + " must complete; failure="
						+ trace.getDispatcher().getFailureReason());
				require(trace.getCommittedNode().size() == units.size(),
						policy + " commits every unit exactly once");
				for (WorkUnitSpec unit : units)
				{
					require(trace.getDispatcher().stateOf(unit.getId())
							== WorkDispatcher.UnitState.COMMITTED, "all committed");
				}
			}
		}
	}

	/** With no failures, delays or memory limits, pull policies never idle a lane early. */
	private static void checkPullWorkConservation(Random random)
	{
		SchedulerLabEngine.SchedulingPolicy[] pull = {
			SchedulerLabEngine.SchedulingPolicy.QUEUE_LENGTH,
			SchedulerLabEngine.SchedulingPolicy.DEADLINE_PRIORITY,
			SchedulerLabEngine.SchedulingPolicy.MEMORY_AWARE_SAFE_WAVE,
			SchedulerLabEngine.SchedulingPolicy.FAILURE_AWARE_RESCHEDULING
		};
		for (int trial = 0; trial < 30; trial++)
		{
			List<NodeSpec> nodes = specs(new double[] {1.0, 0.7, 0.4}, new int[] {2, 1, 1});
			List<WorkUnitSpec> units = randomUnits(random, 30, false);
			for (SchedulerLabEngine.SchedulingPolicy policy : pull)
			{
				SchedulingSimulator.Trace trace = SchedulingSimulator.run(
						new SchedulingSimulator.Config(policy, nodes, units,
								new WorkDispatcher.Options(), 100.0));
				require(trace.isComplete(), "pull simulation completes");
				double lastStart = 0.0;
				for (SchedulingSimulator.Execution execution : trace.getExecutions())
				{
					lastStart = Math.max(lastStart, execution.getStart());
				}
				Map<String,List<SchedulingSimulator.Execution>> byLane =
						new HashMap<String,List<SchedulingSimulator.Execution>>();
				for (SchedulingSimulator.Execution execution : trace.getExecutions())
				{
					String lane = execution.getNode() + "/" + execution.getLane();
					List<SchedulingSimulator.Execution> list = byLane.get(lane);
					if (list == null)
					{
						list = new ArrayList<SchedulingSimulator.Execution>();
						byLane.put(lane, list);
					}
					list.add(execution);
				}
				for (NodeSpec node : nodes)
				{
					for (int lane = 0; lane < node.getLanes(); lane++)
					{
						List<SchedulingSimulator.Execution> list =
								byLane.get(node.getIndex() + "/" + lane);
						double free = 0.0;
						if (list != null)
						{
							for (SchedulingSimulator.Execution execution : list)
							{
								require(execution.getStart() - free < 1e-9,
										policy + " left lane " + node.getIndex() + "/" + lane
										+ " idle while work was waiting");
								free = execution.getEnd();
							}
						}
						require(free >= lastStart - 1e-9 || list == null && lastStart <= 0.0
								|| free >= lastStart - 1e-9,
								policy + " lane went idle before the last unit started");
					}
				}
			}
		}
	}

	private static void checkFailureAwareExclusion()
	{
		List<NodeSpec> nodes = new ArrayList<NodeSpec>();
		nodes.add(new NodeSpec(1, "Flaky", 1, 1, 1, 1.0, 1, 1000, 1, 0, 1.0));
		nodes.add(new NodeSpec(2, "Solid", 1, 1, 1, 0.5, 1, 1000, 1, 0, 0.0));
		List<WorkUnitSpec> units = units(12, 3.0);
		SchedulingSimulator.Trace trace = SchedulingSimulator.run(
				new SchedulingSimulator.Config(
						SchedulerLabEngine.SchedulingPolicy.FAILURE_AWARE_RESCHEDULING,
						nodes, units,
						new WorkDispatcher.Options().faultInjection(true, 7L).maxFailuresPerUnit(3),
						100.0));
		require(trace.isComplete(), "failure-aware completes despite a node that always fails");
		Map<Integer,Integer> failuresOnNode1 = new HashMap<Integer,Integer>();
		for (SchedulingSimulator.Execution execution : trace.getExecutions())
		{
			if (execution.getNode() == 1)
			{
				Integer count = failuresOnNode1.get(Integer.valueOf(execution.getUnitId()));
				failuresOnNode1.put(Integer.valueOf(execution.getUnitId()),
						Integer.valueOf(count == null ? 1 : count.intValue() + 1));
			}
		}
		for (Integer count : failuresOnNode1.values())
		{
			require(count.intValue() <= 1,
					"failure-aware never retries a unit on the node that failed it");
		}
		for (Integer node : trace.getCommittedNode().values())
		{
			require(node.intValue() == 2, "only the reliable node commits");
		}
		//A static policy retries on the same node; with p=1 it must fail cleanly.
		SchedulingSimulator.Trace staticTrace = SchedulingSimulator.run(
				new SchedulingSimulator.Config(
						SchedulerLabEngine.SchedulingPolicy.ROUND_ROBIN,
						nodes, units,
						new WorkDispatcher.Options().faultInjection(true, 7L).maxFailuresPerUnit(3),
						100.0));
		require(staticTrace.getDispatcher().hasFailed(),
				"a static policy on an always-failing node fails with a clear reason");
		require(staticTrace.getDispatcher().getFailureReason().indexOf("failed 3 time") >= 0,
				"failure reason names the retry budget");
	}

	private static void checkDrainRedirectPause()
	{
		List<NodeSpec> nodes = specs(new double[] {1.0, 1.0, 1.0}, 1);
		List<WorkUnitSpec> units = units(30, 4.0);
		for (SchedulerLabEngine.SchedulingPolicy policy : POLICIES)
		{
			SchedulingSimulator.Config config = new SchedulingSimulator.Config(
					policy, nodes, units, new WorkDispatcher.Options(), 100.0);
			config.control(new SchedulingSimulator.Control(1.0,
					SchedulingSimulator.Control.Kind.DRAIN, 1, 0, null));
			SchedulingSimulator.Trace trace = SchedulingSimulator.run(config);
			require(trace.isComplete(), policy + " completes after a drain");
			for (Map.Entry<Integer,Integer> commit : trace.getCommittedNode().entrySet())
			{
				require(commit.getValue().intValue() != 1,
						policy + ": a drained node commits nothing started after the drain");
			}
			//Draining must not serialize the rest of the run onto one node.
			require(trace.getLastFinish(2) > 0.0 && trace.getLastFinish(3) > 0.0,
					policy + ": remaining nodes share the drained work");
			double ideal = 30 * 4.0 / 2.0;
			require(trace.getMakespan() <= ideal + 4.0 + 1e-9 || !isBalancingPolicy(policy),
					policy + ": drained work is rebalanced, makespan " + trace.getMakespan());

			SchedulingSimulator.Config paused = new SchedulingSimulator.Config(
					policy, nodes, units, new WorkDispatcher.Options(), 100.0);
			paused.control(new SchedulingSimulator.Control(0.5,
					SchedulingSimulator.Control.Kind.PAUSE, 2, 0, null));
			paused.control(new SchedulingSimulator.Control(20.0,
					SchedulingSimulator.Control.Kind.RESUME, 2, 0, null));
			SchedulingSimulator.Trace pausedTrace = SchedulingSimulator.run(paused);
			require(pausedTrace.isComplete(), policy + " completes after pause/resume");
			for (SchedulingSimulator.Execution execution : pausedTrace.getExecutions())
			{
				if (execution.getNode() == 2)
				{
					require(execution.getStart() < 0.5 + 1e-9 || execution.getStart() >= 20.0 - 1e-9,
							policy + ": a paused node starts nothing while paused");
				}
			}

			SchedulingSimulator.Config redirected = new SchedulingSimulator.Config(
					policy, nodes, units, new WorkDispatcher.Options(), 100.0);
			redirected.control(new SchedulingSimulator.Control(0.0,
					SchedulingSimulator.Control.Kind.REDIRECT, 3, 5, null));
			SchedulingSimulator.Trace redirectedTrace = SchedulingSimulator.run(redirected);
			require(redirectedTrace.isComplete(), policy + " completes after a redirect");
			require(redirectedTrace.getLastFinish(1) > 0.0 && redirectedTrace.getLastFinish(2) > 0.0,
					policy + ": a redirect never stops the other nodes");
		}
	}

	private static boolean isBalancingPolicy(SchedulerLabEngine.SchedulingPolicy policy)
	{
		return WorkDispatcher.modeOf(policy) != WorkDispatcher.ExecutionMode.STATIC
				|| policy == SchedulerLabEngine.SchedulingPolicy.CAPACITY_WEIGHTED
				|| policy == SchedulerLabEngine.SchedulingPolicy.LEAST_LOADED_NODE
				|| policy == SchedulerLabEngine.SchedulingPolicy.ROUND_ROBIN
				|| policy == SchedulerLabEngine.SchedulingPolicy.STATIC_EQUAL_SPLIT;
	}

	private static void checkStealingHelpsMisestimatedSpeeds(Random random)
	{
		int wins = 0;
		int trials = 20;
		for (int trial = 0; trial < trials; trial++)
		{
			List<NodeSpec> nodes = specs(new double[] {1.0, 1.0, 1.0, 1.0}, 1);
			List<WorkUnitSpec> units = randomUnits(random, 60, false);
			//Reality differs from the plan: node 1 is four times slower.
			double[] actual = {0.25, 1.0, 1.0, 1.0};
			double planned = SchedulingSimulator.run(new SchedulingSimulator.Config(
					SchedulerLabEngine.SchedulingPolicy.CAPACITY_WEIGHTED, nodes, units,
					new WorkDispatcher.Options(), 100.0).actualSpeeds(actual)).getMakespan();
			double stealing = SchedulingSimulator.run(new SchedulingSimulator.Config(
					SchedulerLabEngine.SchedulingPolicy.WORK_STEALING, nodes, units,
					new WorkDispatcher.Options(), 100.0).actualSpeeds(actual)).getMakespan();
			double pull = SchedulingSimulator.run(new SchedulingSimulator.Config(
					SchedulerLabEngine.SchedulingPolicy.QUEUE_LENGTH, nodes, units,
					new WorkDispatcher.Options(), 100.0).actualSpeeds(actual)).getMakespan();
			require(stealing <= planned + 1e-9, "stealing is never worse than its own static plan here");
			require(pull <= planned + 1e-9, "pull adapts to a misestimated node");
			if (stealing < planned - 1e-9)
			{
				wins++;
			}
		}
		require(wins >= trials * 3 / 4, "stealing recovers most of the misestimation loss");
	}

	private static void checkInfeasibleMemoryFailsFast()
	{
		List<NodeSpec> nodes = new ArrayList<NodeSpec>();
		nodes.add(new NodeSpec(1, "A", 1, 1, 1, 1.0, 1, 100, 1, 0, 0));
		List<WorkUnitSpec> units = new ArrayList<WorkUnitSpec>();
		units.add(new WorkUnitSpec(1, "huge", 1.0, 200, 0, 0, 0, null));
		boolean rejected = false;
		try
		{
			new WorkDispatcher("p", SchedulerLabEngine.SchedulingPolicy.ROUND_ROBIN,
					nodes, units, new WorkDispatcher.Options());
		}
		catch (IllegalArgumentException expected)
		{
			rejected = true;
		}
		require(rejected, "a unit that can never fit is rejected up front instead of livelocking");
	}

	/**
	 * Memory that allows fewer concurrent units than lanes: every policy must
	 * keep each node within budget at all times and still finish (safe waves).
	 */
	private static void checkBindingMemoryAdmission(Random random)
	{
		List<NodeSpec> nodes = new ArrayList<NodeSpec>();
		nodes.add(new NodeSpec(1, "A", 1, 1, 1, 1.0, 3, 100, 3, 0, 0));
		nodes.add(new NodeSpec(2, "B", 1, 1, 1, 0.5, 3, 100, 3, 0, 0));
		ArrayList<WorkUnitSpec> units = new ArrayList<WorkUnitSpec>();
		for (int id = 1; id <= 24; id++)
		{
			units.add(new WorkUnitSpec(id, String.format("mem-%02d", id),
					1 + random.nextInt(6), 30 + random.nextInt(30), 0, 0, 0, null));
		}
		for (SchedulerLabEngine.SchedulingPolicy policy : POLICIES)
		{
			SchedulingSimulator.Trace trace = SchedulingSimulator.run(
					new SchedulingSimulator.Config(policy, nodes, units,
							new WorkDispatcher.Options(), 100.0).checkInvariants(true));
			require(trace.getViolations().isEmpty(),
					policy + " exceeded memory: " + trace.getViolations());
			require(trace.isComplete(), policy + " finishes in memory-safe waves");
			//Independently recompute peak concurrent memory per node from the trace.
			for (NodeSpec node : nodes)
			{
				ArrayList<double[]> spans = new ArrayList<double[]>();
				for (SchedulingSimulator.Execution execution : trace.getExecutions())
				{
					if (execution.getNode() == node.getIndex())
					{
						int memory = units.get(execution.getUnitId() - 1).getMemoryMb();
						spans.add(new double[] {execution.getStart(), execution.getEnd(), memory});
					}
				}
				for (double[] probe : spans)
				{
					double time = probe[0];
					double inUse = 0.0;
					for (double[] span : spans)
					{
						if (span[0] <= time && time < span[1])
						{
							inUse += span[2];
						}
					}
					require(inUse <= node.getMemoryMb() + 1e-9,
							policy + " ran " + inUse + " MB concurrently on node " + node.getIndex());
				}
			}
		}
	}

	private static void checkSimulationDeterminism(Random random)
	{
		List<NodeSpec> nodes = randomSpecsWithFailures(random, 4);
		List<WorkUnitSpec> units = randomUnits(random, 40, true);
		for (SchedulerLabEngine.SchedulingPolicy policy : POLICIES)
		{
			SchedulingSimulator.Trace first = SchedulingSimulator.run(
					new SchedulingSimulator.Config(policy, nodes, units,
							new WorkDispatcher.Options().faultInjection(true, 99L)
									.maxFailuresPerUnit(50), 100.0));
			SchedulingSimulator.Trace second = SchedulingSimulator.run(
					new SchedulingSimulator.Config(policy, nodes, units,
							new WorkDispatcher.Options().faultInjection(true, 99L)
									.maxFailuresPerUnit(50), 100.0));
			require(first.getExecutions().size() == second.getExecutions().size(),
					policy + " simulation is deterministic");
			for (int index = 0; index < first.getExecutions().size(); index++)
			{
				SchedulingSimulator.Execution a = first.getExecutions().get(index);
				SchedulingSimulator.Execution b = second.getExecutions().get(index);
				require(a.getUnitId() == b.getUnitId() && a.getNode() == b.getNode()
						&& a.getStart() == b.getStart() && a.getOutcome() == b.getOutcome(),
						policy + " simulation is deterministic event by event");
			}
		}
	}

	// ----------------------------------------------------------- threads

	private static void checkThreadedWorkPhase(Random random) throws Exception
	{
		for (int trial = 0; trial < 12; trial++)
		{
			final int nodeCount = 3;
			final List<NodeSpec> nodes = randomSpecsWithFailures(random, nodeCount);
			final List<WorkUnitSpec> units = randomUnits(random, 50, false);
			SchedulerLabEngine.SchedulingPolicy policy = POLICIES[trial % POLICIES.length];
			final WorkPhase phase = new WorkPhase(new WorkDispatcher(
					"threaded-" + trial, policy, nodes, units,
					new WorkDispatcher.Options().faultInjection(true, trial).maxFailuresPerUnit(60)
							.maxRealFailuresPerUnit(60)),
					null, 60000L);
			final Set<Integer> committed = java.util.Collections.synchronizedSet(new HashSet<Integer>());
			final AtomicInteger duplicates = new AtomicInteger();
			final List<Throwable> errors = java.util.Collections.synchronizedList(new ArrayList<Throwable>());
			ArrayList<Thread> threads = new ArrayList<Thread>();
			for (final NodeSpec node : nodes)
			{
				for (int lane = 0; lane < node.getLanes(); lane++)
				{
					final int laneIndex = lane;
					final Random laneRandom = new Random(random.nextLong());
					Thread thread = new Thread(new Runnable()
					{
						public void run()
						{
							try
							{
								while (true)
								{
									WorkDispatcher.Assignment assignment =
											phase.claim(node.getIndex(), laneIndex);
									if (assignment == null)
									{
										return;
									}
									Thread.sleep(laneRandom.nextInt(3));
									if (laneRandom.nextInt(20) == 0)
									{
										phase.fail(assignment, new RuntimeException("synthetic"));
										continue;
									}
									WorkDispatcher.Outcome outcome = phase.complete(assignment);
									if (outcome == WorkDispatcher.Outcome.COMMITTED
											&& !committed.add(Integer.valueOf(assignment.getUnitId())))
									{
										duplicates.incrementAndGet();
									}
								}
							}
							catch (Throwable failure)
							{
								errors.add(failure);
							}
						}
					});
					threads.add(thread);
					thread.start();
				}
			}
			Thread operator = new Thread(new Runnable()
			{
				public void run()
				{
					try
					{
						Thread.sleep(5L);
						phase.drain(1);
						Thread.sleep(5L);
						phase.redirect(2, 4);
						Thread.sleep(5L);
						phase.setNodeState(1, WorkDispatcher.NodeState.ACTIVE);
						phase.switchPolicy(SchedulerLabEngine.SchedulingPolicy.QUEUE_LENGTH);
					}
					catch (InterruptedException ignored)
					{
					}
				}
			});
			operator.start();
			for (Thread thread : threads)
			{
				thread.join(60000L);
				require(!thread.isAlive(), "threaded lanes finish (no deadlock)");
			}
			operator.join();
			require(errors.isEmpty(), "threaded phase raised " + errors);
			require(duplicates.get() == 0, "threaded phase commits each unit once");
			require(committed.size() == units.size(), "threaded phase commits every unit");
		}
	}

	// ----------------------------------------------------------- helpers

	private static List<NodeSpec> specs(double[] speeds, int lanes)
	{
		int[] laneCounts = new int[speeds.length];
		java.util.Arrays.fill(laneCounts, lanes);
		return specs(speeds, laneCounts);
	}

	private static List<NodeSpec> specs(double[] speeds, int[] lanes)
	{
		ArrayList<NodeSpec> nodes = new ArrayList<NodeSpec>();
		for (int index = 0; index < speeds.length; index++)
		{
			nodes.add(new NodeSpec(index + 1, "N", 1, 1, 1, speeds[index], lanes[index],
					1 << 20, lanes[index], 0, 0.0));
		}
		return nodes;
	}

	private static List<NodeSpec> randomSpecs(Random random, int count)
	{
		ArrayList<NodeSpec> nodes = new ArrayList<NodeSpec>();
		for (int index = 1; index <= count; index++)
		{
			int lanes = 1 + random.nextInt(2);
			nodes.add(new NodeSpec(index, "R", 1, 1, 1,
					index == 1 ? 1.0 : 0.2 + random.nextDouble() * 0.8,
					lanes, 256 + random.nextInt(512), lanes + random.nextInt(2),
					random.nextInt(40), 0.0));
		}
		return nodes;
	}

	private static List<NodeSpec> randomSpecsWithFailures(Random random, int count)
	{
		ArrayList<NodeSpec> nodes = new ArrayList<NodeSpec>();
		for (int index = 1; index <= count; index++)
		{
			int lanes = 1 + random.nextInt(2);
			nodes.add(new NodeSpec(index, "R", 1, 1, 1,
					index == 1 ? 1.0 : 0.2 + random.nextDouble() * 0.8,
					lanes, 256 + random.nextInt(512), lanes + random.nextInt(2),
					random.nextInt(40), index == count ? 0.0 : random.nextDouble() * 0.4));
		}
		return nodes;
	}

	private static List<WorkUnitSpec> units(int count, double cost)
	{
		ArrayList<WorkUnitSpec> units = new ArrayList<WorkUnitSpec>();
		for (int id = 1; id <= count; id++)
		{
			units.add(new WorkUnitSpec(id, String.format("unit-%04d", id), cost));
		}
		return units;
	}

	private static List<WorkUnitSpec> randomUnits(Random random, int count, boolean withHomes)
	{
		ArrayList<WorkUnitSpec> units = new ArrayList<WorkUnitSpec>();
		for (int id = 1; id <= count; id++)
		{
			units.add(new WorkUnitSpec(id, String.format("u-%04d", random.nextInt(10000)),
					0.5 + random.nextInt(20), random.nextInt(128), random.nextInt(3),
					random.nextInt(200), withHomes ? random.nextInt(3) : 0, null));
		}
		return units;
	}

	private static void require(boolean condition, String message)
	{
		if (!condition)
		{
			throw new AssertionError(message);
		}
	}
}
