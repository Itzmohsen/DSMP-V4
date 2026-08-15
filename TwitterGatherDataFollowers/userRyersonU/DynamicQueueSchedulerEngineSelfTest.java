package TwitterGatherDataFollowers.userRyersonU;

import java.util.ArrayList;
import java.util.List;

public final class DynamicQueueSchedulerEngineSelfTest
{
	private DynamicQueueSchedulerEngineSelfTest()
	{
	}

	public static void main(String[] args) throws Exception
	{
		int heapMb = 4096;
		ArrayList<NodeCapacityProfile> profiles =
				new ArrayList<NodeCapacityProfile>();
		profiles.add(NodeCapacityProfile.rolePreset(
				1,
				NodeCapacityProfile.Role.STRONG,
				3,
				heapMb));
		profiles.add(NodeCapacityProfile.rolePreset(
				2,
				NodeCapacityProfile.Role.WEAK,
				3,
				heapMb));
		profiles.add(NodeCapacityProfile.rolePreset(
				3,
				NodeCapacityProfile.Role.NORMAL,
				3,
				heapMb));

		DynamicQueueSchedulerEngine.Config config =
				new DynamicQueueSchedulerEngine.Config(
						profiles,
						SchedulerLabEngine.SchedulingPolicy.CAPACITY_WEIGHTED,
						SchedulerLabEngine.WorkloadTemplate.POPULATION_TARGET_SWEEP,
						90,
						heapMb,
						2,
						1);
		DynamicQueueSchedulerEngine.Result result =
				DynamicQueueSchedulerEngine.runBlocking(config);
		DynamicQueueSchedulerEngine.Snapshot snapshot =
				result.getFinalSnapshot();
		assertTrue(
				snapshot.getCompletedJobs() == snapshot.getTotalJobs(),
				"all work units should complete");
		assertTrue(
				snapshot.getFailedJobs() == 0,
				"no final failures expected");
		DynamicQueueSchedulerEngine.NodeSnapshot strong =
				node(snapshot.getNodes(), 1);
		DynamicQueueSchedulerEngine.NodeSnapshot weak =
				node(snapshot.getNodes(), 2);
		assertTrue(strong != null && weak != null, "expected strong and weak nodes");
		assertTrue(
				strong.getCompletedJobs() > weak.getCompletedJobs(),
				"strong node should pull more jobs than weak node");
		System.out.println("DynamicQueueSchedulerEngineSelfTest passed");
	}

	private static DynamicQueueSchedulerEngine.NodeSnapshot node(
			List<DynamicQueueSchedulerEngine.NodeSnapshot> nodes,
			int nodeIndex)
	{
		for (DynamicQueueSchedulerEngine.NodeSnapshot node : nodes)
		{
			if (node.getProfile().getNodeIndex() == nodeIndex)
			{
				return node;
			}
		}
		return null;
	}

	private static void assertTrue(boolean condition, String message)
	{
		if (!condition)
		{
			throw new AssertionError(message);
		}
	}
}
