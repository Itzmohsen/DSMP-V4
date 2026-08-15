package TwitterGatherDataFollowers.userRyersonU;

import java.util.ArrayList;
import java.util.List;

public final class NodeCapacitySchedulerSelfTest
{
	private NodeCapacitySchedulerSelfTest()
	{
	}

	public static void main(String[] args)
	{
		balancedDefaultsStayEven();
		defaultProfilesTrackHeapChanges();
		strongNodesReceiveMoreCompleteTargetJobs();
		slowAndUnreliableProfilesAreDeprioritized();
		activeRoundRobinPolicyOverridesCapacityWeighting();
		restoreSchedulerDefaultReturnsToBalancedDsmpAssignment();
		System.out.println("NodeCapacitySchedulerSelfTest passed");
	}

	private static void balancedDefaultsStayEven()
	{
		NodeCapacityRegistry.resetToDefaults(4, 4096);
		List<List<String>> assignments =
				NodeCapacityScheduler.assignTargetJobs(targets(40), 4);
		for (List<String> worker : assignments)
		{
			require(worker.size() == 10,
					"default assignment should remain evenly balanced");
		}
	}

	private static void defaultProfilesTrackHeapChanges()
	{
		NodeCapacityRegistry.resetToDefaults(2, 4096);
		require(NodeCapacityRegistry.profileForNode(1).getMemoryLimitMb()
						== 2048,
				"initial default memory budget should split the heap");
		NodeCapacityRegistry.configureForNodeCount(2, 8192);
		require(NodeCapacityRegistry.profileForNode(1).getMemoryLimitMb()
						== 4096,
				"default memory budget did not follow the new heap");
		require(Math.abs(NodeCapacityScheduler.schedulingWeight(
						NodeCapacityRegistry.profileForNode(1)) - 1.0)
						< 0.0000001,
				"default scheduling weight should remain neutral after heap change");
	}

	private static void strongNodesReceiveMoreCompleteTargetJobs()
	{
		ArrayList<NodeCapacityProfile> profiles =
				new ArrayList<NodeCapacityProfile>();
		profiles.add(NodeCapacityProfile.rolePreset(
				1, NodeCapacityProfile.Role.STRONG, 3, 4096));
		profiles.add(NodeCapacityProfile.rolePreset(
				2, NodeCapacityProfile.Role.NORMAL, 3, 4096));
		profiles.add(NodeCapacityProfile.rolePreset(
				3, NodeCapacityProfile.Role.WEAK, 3, 4096));
		NodeCapacityRegistry.setProfiles(profiles, 4096);

		List<List<String>> assignments =
				NodeCapacityScheduler.assignTargetJobs(targets(60), 3);
		int strong = assignments.get(0).size();
		int normal = assignments.get(1).size();
		int weak = assignments.get(2).size();
		require(strong > normal,
				"strong node did not receive more target jobs than normal node");
		require(normal > weak,
				"normal node did not receive more target jobs than weak node");
		require(strong + normal + weak == 60,
				"capacity assignment lost or duplicated target jobs");
	}

	private static void slowAndUnreliableProfilesAreDeprioritized()
	{
		ArrayList<NodeCapacityProfile> profiles =
				new ArrayList<NodeCapacityProfile>();
		profiles.add(new NodeCapacityProfile(
				1,
				NodeCapacityProfile.Role.NORMAL,
				1.0,
				2048,
				1,
				0,
				1000,
				false,
				1.0,
				false,
				0));
		profiles.add(new NodeCapacityProfile(
				2,
				NodeCapacityProfile.Role.UNRELIABLE,
				1.0,
				2048,
				1,
				500,
				1000,
				true,
				4.0,
				true,
				50));
		NodeCapacityRegistry.setProfiles(profiles, 4096);
		List<List<String>> assignments =
				NodeCapacityScheduler.assignTargetJobs(targets(30), 2);
		require(assignments.get(0).size() > assignments.get(1).size(),
				"slow/unreliable node was not deprioritized");
	}

	private static void activeRoundRobinPolicyOverridesCapacityWeighting()
	{
		ArrayList<NodeCapacityProfile> profiles =
				new ArrayList<NodeCapacityProfile>();
		profiles.add(NodeCapacityProfile.rolePreset(
				1, NodeCapacityProfile.Role.STRONG, 3, 4096));
		profiles.add(NodeCapacityProfile.rolePreset(
				2, NodeCapacityProfile.Role.NORMAL, 3, 4096));
		profiles.add(NodeCapacityProfile.rolePreset(
				3, NodeCapacityProfile.Role.WEAK, 3, 4096));
		NodeCapacityRegistry.setProfiles(profiles, 4096);
		RecommendationSchedulerRegistry.apply(
				RecommendationSchedulerPlan.Mode.SCORING_SAFE,
				SchedulerLabEngine.SchedulingPolicy.ROUND_ROBIN,
				3,
				4096,
				"self-test");

		List<List<String>> assignments =
				NodeCapacityScheduler.assignTargetJobs(targets(9), 3);
		for (List<String> worker : assignments)
		{
			require(worker.size() == 3,
					"active round-robin plan should assign equal turns");
		}
	}

	private static void restoreSchedulerDefaultReturnsToBalancedDsmpAssignment()
	{
		NodeCapacityRegistry.resetToDefaults(4, 4096);
		RecommendationSchedulerRegistry.restoreDefault(4, 4096);
		List<List<String>> assignments =
				NodeCapacityScheduler.assignTargetJobs(targets(40), 4);
		for (List<String> worker : assignments)
		{
			require(worker.size() == 10,
					"restored DSMP default should return to balanced assignment");
		}
	}

	private static List<String> targets(int count)
	{
		ArrayList<String> targets = new ArrayList<String>();
		for (int index = 0; index < count; index++)
		{
			targets.add("Target" + index);
		}
		return targets;
	}

	private static void require(boolean condition, String message)
	{
		if (!condition)
		{
			throw new AssertionError(message);
		}
	}
}
