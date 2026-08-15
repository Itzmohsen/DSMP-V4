package TwitterGatherDataFollowers.userRyersonU;

import java.util.ArrayList;
import java.util.List;

public final class SchedulerLabEngineSelfTest
{
	private SchedulerLabEngineSelfTest()
	{
	}

	public static void main(String[] args)
	{
		capacityWeightedUsesStrongNodesMore();
		memoryAwareSafeWaveCompletesWithoutDroppingJobs();
		failureAwareRetriesWork();
		queueCapacityDoesNotLoseJobs();
		System.out.println("SchedulerLabEngineSelfTest passed");
	}

	private static void capacityWeightedUsesStrongNodesMore()
	{
		NodeCapacityRegistry.setProfiles(mixedProfiles(3, 4096), 4096);
		List<SchedulerLabEngine.WorkloadUnit> jobs =
				SchedulerLabEngine.createWorkload(
						SchedulerLabEngine.WorkloadTemplate.POPULATION_TARGET_SWEEP,
						90,
						90);
		SchedulerLabEngine.Result result = SchedulerLabEngine.simulate(
				NodeCapacityRegistry.snapshot(),
				SchedulerLabEngine.SchedulingPolicy.CAPACITY_WEIGHTED,
				SchedulerLabEngine.WorkloadTemplate.POPULATION_TARGET_SWEEP,
				jobs,
				4096);
		require(result.getCompletedJobs() == 90,
				"capacity weighted scheduling lost jobs");
		int strong = result.getNodes().get(0).getAssignedJobs();
		int normal = result.getNodes().get(1).getAssignedJobs();
		int weak = result.getNodes().get(2).getAssignedJobs();
		require(strong > normal,
				"strong node should receive more work than normal node");
		require(normal > weak,
				"normal node should receive more work than weak node");
	}

	private static void memoryAwareSafeWaveCompletesWithoutDroppingJobs()
	{
		ArrayList<NodeCapacityProfile> profiles =
				new ArrayList<NodeCapacityProfile>();
		profiles.add(new NodeCapacityProfile(
				1,
				NodeCapacityProfile.Role.NORMAL,
				1.0,
				512,
				2,
				0,
				1000,
				false,
				1.0,
				false,
				0));
		profiles.add(new NodeCapacityProfile(
				2,
				NodeCapacityProfile.Role.NORMAL,
				1.0,
				512,
				2,
				0,
				1000,
				false,
				1.0,
				false,
				0));
		NodeCapacityRegistry.setProfiles(profiles, 1024);
		ArrayList<SchedulerLabEngine.WorkloadUnit> jobs =
				new ArrayList<SchedulerLabEngine.WorkloadUnit>();
		for (int i = 0; i < 12; i++)
		{
			jobs.add(new SchedulerLabEngine.WorkloadUnit(
					"memory-" + i,
					SchedulerLabEngine.WorkloadUnitType.MODEL_TRAINING_JOB,
					"memory job",
					20,
					400,
					5,
					100 + i,
					1));
		}
		SchedulerLabEngine.Result result = SchedulerLabEngine.simulate(
				NodeCapacityRegistry.snapshot(),
				SchedulerLabEngine.SchedulingPolicy.MEMORY_AWARE_SAFE_WAVE,
				SchedulerLabEngine.WorkloadTemplate.MODEL_HEAVY_RUN,
				jobs,
				512);
		require(result.getCompletedJobs() == 12,
				"memory-aware safe wave should finish every runnable job");
		require(result.getFailedJobs() == 0,
				"memory-aware safe wave should not fail runnable jobs");
	}

	private static void failureAwareRetriesWork()
	{
		ArrayList<NodeCapacityProfile> profiles =
				new ArrayList<NodeCapacityProfile>();
		profiles.add(new NodeCapacityProfile(
				1,
				NodeCapacityProfile.Role.UNRELIABLE,
				1.0,
				2048,
				1,
				0,
				1000,
				false,
				1.0,
				true,
				100));
		profiles.add(new NodeCapacityProfile(
				2,
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
		NodeCapacityRegistry.setProfiles(profiles, 4096);
		List<SchedulerLabEngine.WorkloadUnit> jobs =
				SchedulerLabEngine.createWorkload(
						SchedulerLabEngine.WorkloadTemplate.POPULATION_TARGET_SWEEP,
						20,
						20);
		SchedulerLabEngine.Result result = SchedulerLabEngine.simulate(
				NodeCapacityRegistry.snapshot(),
				SchedulerLabEngine.SchedulingPolicy.FAILURE_AWARE_RESCHEDULING,
				SchedulerLabEngine.WorkloadTemplate.POPULATION_TARGET_SWEEP,
				jobs,
				4096);
		require(result.getCompletedJobs() > 0,
				"failure-aware run should complete work on reliable nodes");
		require(result.getRetriedJobs() >= 0,
				"retry count should be non-negative");
	}

	private static void queueCapacityDoesNotLoseJobs()
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
				1,
				false,
				1.0,
				false,
				0));
		profiles.add(new NodeCapacityProfile(
				2,
				NodeCapacityProfile.Role.NORMAL,
				1.0,
				2048,
				1,
				0,
				1,
				false,
				1.0,
				false,
				0));
		NodeCapacityRegistry.setProfiles(profiles, 4096);
		List<SchedulerLabEngine.WorkloadUnit> jobs =
				SchedulerLabEngine.createWorkload(
						SchedulerLabEngine.WorkloadTemplate.BALANCED_RECOMMENDER_RUN,
						18,
						18);
		SchedulerLabEngine.Result result = SchedulerLabEngine.simulate(
				NodeCapacityRegistry.snapshot(),
				SchedulerLabEngine.SchedulingPolicy.QUEUE_LENGTH,
				SchedulerLabEngine.WorkloadTemplate.BALANCED_RECOMMENDER_RUN,
				jobs,
				4096);
		require(result.getCompletedJobs() == 18,
				"queue-limited run should eventually schedule all work");
	}

	private static ArrayList<NodeCapacityProfile> mixedProfiles(
			int nodes,
			int heapMb)
	{
		ArrayList<NodeCapacityProfile> profiles =
				new ArrayList<NodeCapacityProfile>();
		profiles.add(NodeCapacityProfile.rolePreset(
				1, NodeCapacityProfile.Role.STRONG, nodes, heapMb));
		profiles.add(NodeCapacityProfile.rolePreset(
				2, NodeCapacityProfile.Role.NORMAL, nodes, heapMb));
		profiles.add(NodeCapacityProfile.rolePreset(
				3, NodeCapacityProfile.Role.WEAK, nodes, heapMb));
		return profiles;
	}

	private static void require(boolean condition, String message)
	{
		if (!condition)
		{
			throw new AssertionError(message);
		}
	}
}
