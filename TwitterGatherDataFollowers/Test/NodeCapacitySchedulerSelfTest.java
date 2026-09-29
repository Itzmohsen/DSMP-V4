package TwitterGatherDataFollowers.userRyersonU;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Work phases over node profiles: in the original DSMP mode every homed unit
 * runs on its home node; in a scheduled mode every unit is committed exactly
 * once whatever the interleaving of claims, placement is deterministic, and
 * a stronger node completes more of a pulled queue.
 */
public final class NodeCapacitySchedulerSelfTest
{
	private NodeCapacitySchedulerSelfTest()
	{
	}

	public static void main(String[] args) throws Exception
	{
		verifyBaselineShardsStayHome();
		verifyEveryUnitCommittedOnce();
		verifyDeterministicPlacement();
		verifyStrongNodeCompletesMoreOfAPulledQueue();
		System.out.println("NodeCapacitySchedulerSelfTest passed");
	}

	private static void verifyBaselineShardsStayHome()
	{
		int nodes = 4;
		NodeCapacityRegistry.configureForNodeCount(nodes, 4096);
		RecommendationSchedulerRegistry.restoreDefault(nodes, 4096);
		SchedulingRunContext context = RecommendationSchedulerRegistry.beginRun(
				"capacity-scheduler-baseline-" + System.nanoTime(), nodes, 4096, ControllerAgent.Doc2Vec, 20);
		WorkPhase phase = context.openPhase("doc2vec-shards", "baseline shards", units(nodes, true));
		for (int node = nodes; node >= 1; node--)
		{
			WorkDispatcher.Assignment assignment = phase.getDispatcher().request(node, 0, phase.now());
			require(assignment != null && assignment.getUnitId() == node,
					"in the original mode node " + node + " must run exactly its own shard");
			require(phase.complete(assignment) == WorkDispatcher.Outcome.COMMITTED, "shard did not commit");
			require(phase.getDispatcher().request(node, 0, phase.now()) == null
					|| phase.getDispatcher().isComplete(),
					"a node took another node's homed shard");
		}
		require(phase.getDispatcher().isComplete(), "baseline phase did not complete");
		context.close("test finished");
	}

	private static void verifyEveryUnitCommittedOnce()
	{
		int nodes = 3;
		ArrayList<NodeCapacityProfile> profiles = new ArrayList<NodeCapacityProfile>();
		profiles.add(NodeCapacityProfile.rolePreset(1, NodeCapacityProfile.Role.STRONG, nodes, 4096));
		profiles.add(NodeCapacityProfile.rolePreset(2, NodeCapacityProfile.Role.WEAK, nodes, 4096));
		profiles.add(NodeCapacityProfile.rolePreset(3, NodeCapacityProfile.Role.NORMAL, nodes, 4096));
		NodeCapacityRegistry.setProfiles(profiles, 4096);
		for (SchedulerLabEngine.SchedulingPolicy policy : SchedulerLabEngine.SchedulingPolicy.values())
		{
			RecommendationSchedulerRegistry.apply(
					RecommendationSchedulerPlan.Mode.SCORING_SAFE, policy, nodes, 4096, "self-test");
			SchedulingRunContext context = RecommendationSchedulerRegistry.beginRun(
					"capacity-scheduler-" + policy.name() + "-" + System.nanoTime(),
					nodes, 4096, ControllerAgent.COS_SIM, 20);
			WorkPhase phase = context.openPhase("candidate-shards", "units", units(40, false));
			Random random = new Random(policy.ordinal() + 17L);
			Set<Integer> committed = new HashSet<Integer>();
			int guard = 0;
			while (!phase.getDispatcher().isComplete() && guard++ < 10000)
			{
				int node = 1 + random.nextInt(nodes);
				WorkDispatcher.Assignment assignment =
						phase.getDispatcher().request(node, random.nextInt(2), phase.now());
				if (assignment == null)
				{
					continue;
				}
				if (phase.complete(assignment) == WorkDispatcher.Outcome.COMMITTED)
				{
					require(committed.add(Integer.valueOf(assignment.getUnitId())),
							policy + " committed unit " + assignment.getUnitId() + " twice");
				}
			}
			require(committed.size() == 40, policy + " committed " + committed.size() + " of 40 units");
			context.close("test finished");
		}
		RecommendationSchedulerRegistry.restoreDefault(nodes, 4096);
	}

	private static void verifyDeterministicPlacement()
	{
		NodeCapacityRegistry.resetToDefaults(3, 4096);
		List<NodeSpec> nodes = NodeSpec.fromProfiles(NodeCapacityRegistry.snapshot(), null, true);
		ArrayList<WorkUnitSpec> units = new ArrayList<WorkUnitSpec>();
		Random random = new Random(5L);
		for (int id = 1; id <= 50; id++)
		{
			units.add(new WorkUnitSpec(id, "u" + id, 1.0 + random.nextInt(20)));
		}
		for (SchedulerLabEngine.SchedulingPolicy policy : SchedulerLabEngine.SchedulingPolicy.values())
		{
			PlacementPlanner.Plan first = PlacementPlanner.plan(policy, units, nodes, null, true, false, 0.0);
			PlacementPlanner.Plan second = PlacementPlanner.plan(policy, units, nodes, null, true, false, 0.0);
			for (WorkUnitSpec unit : units)
			{
				require(first.nodeOfUnit(unit.getId()) == second.nodeOfUnit(unit.getId()),
						policy + " placement is not deterministic");
			}
		}
	}

	private static void verifyStrongNodeCompletesMoreOfAPulledQueue()
	{
		ArrayList<NodeCapacityProfile> profiles = new ArrayList<NodeCapacityProfile>();
		profiles.add(NodeCapacityProfile.rolePreset(1, NodeCapacityProfile.Role.STRONG, 2, 4096));
		profiles.add(NodeCapacityProfile.rolePreset(2, NodeCapacityProfile.Role.WEAK, 2, 4096));
		List<NodeSpec> nodes = NodeSpec.fromProfiles(profiles, null, true);
		ArrayList<WorkUnitSpec> units = new ArrayList<WorkUnitSpec>();
		for (int id = 1; id <= 60; id++)
		{
			units.add(new WorkUnitSpec(id, "u" + id, 10.0));
		}
		SchedulingSimulator.Trace trace = SchedulingSimulator.run(new SchedulingSimulator.Config(
				SchedulerLabEngine.SchedulingPolicy.QUEUE_LENGTH, nodes, units,
				new WorkDispatcher.Options(), 1.0));
		int strong = 0;
		int weak = 0;
		for (WorkUnitSpec unit : units)
		{
			if (trace.getCommittedNode().get(Integer.valueOf(unit.getId())).intValue() == 1)
			{
				strong++;
			}
			else
			{
				weak++;
			}
		}
		require(strong + weak == 60, "the pulled queue lost units");
		require(strong > 2 * weak, "a node four times faster should complete far more of a pulled queue ("
				+ strong + " vs " + weak + ")");
	}

	private static SchedulingRunContext.UnitSupplier units(final int count, final boolean homed)
	{
		return new SchedulingRunContext.UnitSupplier()
		{
			public List<WorkUnitSpec> units()
			{
				ArrayList<WorkUnitSpec> list = new ArrayList<WorkUnitSpec>();
				for (int id = 1; id <= count; id++)
				{
					list.add(new WorkUnitSpec(id, "unit-" + id, 1.0, 0, 0,
							Double.POSITIVE_INFINITY, homed ? id : 0, null));
				}
				return list;
			}
		};
	}

	private static void require(boolean condition, String message)
	{
		if (!condition)
		{
			throw new AssertionError(message);
		}
	}
}
