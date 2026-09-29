package TwitterGatherDataFollowers.userRyersonU;

import java.util.ArrayList;
import java.util.List;

/**
 * Capacity-weighted placement over emulated node profiles: equal nodes get
 * equal shares, faster nodes more, slow and unreliable nodes less, round
 * robin ignores capacity, and the original DSMP rule balances raw work.
 */
public final class NodeCapacityWeightingSelfTest
{
	private NodeCapacityWeightingSelfTest()
	{
	}

	public static void main(String[] args)
	{
		balancedDefaultsStayEven();
		defaultProfilesTrackHeapChanges();
		strongNodesReceiveMoreCompleteTargetJobs();
		slowAndUnreliableProfilesAreDeprioritized();
		roundRobinPolicyIgnoresCapacity();
		originalDsmpRuleBalancesRawWork();
		System.out.println("NodeCapacityWeightingSelfTest passed");
	}

	private static void balancedDefaultsStayEven()
	{
		NodeCapacityRegistry.resetToDefaults(4, 4096);
		PlacementPlanner.Plan plan = place(SchedulerLabEngine.SchedulingPolicy.CAPACITY_WEIGHTED, 40);
		for (int node = 1; node <= 4; node++)
		{
			require(plan.unitCount(node) == 10, "default placement should remain evenly balanced");
		}
	}

	private static void defaultProfilesTrackHeapChanges()
	{
		NodeCapacityRegistry.resetToDefaults(2, 4096);
		require(NodeCapacityRegistry.profileForNode(1).getMemoryLimitMb() == 2048,
				"initial default memory budget should split the heap");
		NodeCapacityRegistry.configureForNodeCount(2, 8192);
		require(NodeCapacityRegistry.profileForNode(1).getMemoryLimitMb() == 4096,
				"default memory budget did not follow the new heap");
		require(Math.abs(NodeCapacityScheduler.schedulingWeight(
						NodeCapacityRegistry.profileForNode(1)) - 1.0) < 0.0000001,
				"default scheduling weight should remain neutral after heap change");
	}

	private static void strongNodesReceiveMoreCompleteTargetJobs()
	{
		ArrayList<NodeCapacityProfile> profiles = new ArrayList<NodeCapacityProfile>();
		profiles.add(NodeCapacityProfile.rolePreset(1, NodeCapacityProfile.Role.STRONG, 3, 4096));
		profiles.add(NodeCapacityProfile.rolePreset(2, NodeCapacityProfile.Role.NORMAL, 3, 4096));
		profiles.add(NodeCapacityProfile.rolePreset(3, NodeCapacityProfile.Role.WEAK, 3, 4096));
		NodeCapacityRegistry.setProfiles(profiles, 4096);
		PlacementPlanner.Plan plan = place(SchedulerLabEngine.SchedulingPolicy.CAPACITY_WEIGHTED, 60);
		int strong = plan.unitCount(1);
		int normal = plan.unitCount(2);
		int weak = plan.unitCount(3);
		require(strong > normal, "strong node did not receive more target jobs than normal node");
		require(normal > weak, "normal node did not receive more target jobs than weak node");
		require(strong + normal + weak == 60, "capacity placement lost or duplicated target jobs");
	}

	private static void slowAndUnreliableProfilesAreDeprioritized()
	{
		ArrayList<NodeCapacityProfile> profiles = new ArrayList<NodeCapacityProfile>();
		profiles.add(new NodeCapacityProfile(1, NodeCapacityProfile.Role.NORMAL,
				1.0, 2048, 1, 0, 1000, false, 1.0, false, 0));
		profiles.add(new NodeCapacityProfile(2, NodeCapacityProfile.Role.UNRELIABLE,
				1.0, 2048, 1, 500, 1000, true, 4.0, true, 50));
		NodeCapacityRegistry.setProfiles(profiles, 4096);
		PlacementPlanner.Plan weighted = place(SchedulerLabEngine.SchedulingPolicy.CAPACITY_WEIGHTED, 30);
		require(weighted.unitCount(1) > weighted.unitCount(2), "slow node was not deprioritized");
		PlacementPlanner.Plan reliable = place(SchedulerLabEngine.SchedulingPolicy.FAILURE_AWARE_RESCHEDULING, 30);
		require(reliable.unitCount(1) >= weighted.unitCount(1),
				"failure-aware placement should not trust an unreliable node more than capacity weighting");
	}

	private static void roundRobinPolicyIgnoresCapacity()
	{
		ArrayList<NodeCapacityProfile> profiles = new ArrayList<NodeCapacityProfile>();
		profiles.add(NodeCapacityProfile.rolePreset(1, NodeCapacityProfile.Role.STRONG, 3, 4096));
		profiles.add(NodeCapacityProfile.rolePreset(2, NodeCapacityProfile.Role.NORMAL, 3, 4096));
		profiles.add(NodeCapacityProfile.rolePreset(3, NodeCapacityProfile.Role.WEAK, 3, 4096));
		NodeCapacityRegistry.setProfiles(profiles, 4096);
		PlacementPlanner.Plan plan = place(SchedulerLabEngine.SchedulingPolicy.ROUND_ROBIN, 9);
		for (int node = 1; node <= 3; node++)
		{
			require(plan.unitCount(node) == 3, "round robin should give every node equal turns");
		}
	}

	private static void originalDsmpRuleBalancesRawWork()
	{
		ArrayList<WorkUnitSpec> units = new ArrayList<WorkUnitSpec>();
		for (int id = 1; id <= 40; id++)
		{
			units.add(new WorkUnitSpec(id, "Target" + id, 1.0));
		}
		PlacementPlanner.Plan plan = PlacementPlanner.planOriginalListScheduling(units, 4, null);
		for (int node = 1; node <= 4; node++)
		{
			require(plan.unitCount(node) == 10, "the original DSMP rule should balance raw work");
		}
	}

	private static PlacementPlanner.Plan place(SchedulerLabEngine.SchedulingPolicy policy, int count)
	{
		List<NodeSpec> nodes = NodeSpec.fromProfiles(NodeCapacityRegistry.snapshot(), null, true);
		ArrayList<WorkUnitSpec> units = new ArrayList<WorkUnitSpec>();
		for (int id = 1; id <= count; id++)
		{
			units.add(new WorkUnitSpec(id, "Target" + id, 1.0));
		}
		return PlacementPlanner.plan(policy, units, nodes, null, true, false, 0.0);
	}

	private static void require(boolean condition, String message)
	{
		if (!condition)
		{
			throw new AssertionError(message);
		}
	}
}
