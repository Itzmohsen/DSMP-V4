package TwitterGatherDataFollowers.userRyersonU;

import java.util.ArrayList;

public final class ExperimentalCandidateQueueSelfTest
{
	private ExperimentalCandidateQueueSelfTest()
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
		NodeCapacityRegistry.setProfiles(profiles, heapMb);
		RecommendationSchedulerRegistry.apply(
				RecommendationSchedulerPlan.Mode.SCHEDULING_EXPERIMENT,
				SchedulerLabEngine.SchedulingPolicy.CAPACITY_WEIGHTED,
				3,
				heapMb,
				"self-test");

		String runId = "experimental-queue-self-test";
		String algorithmKey = "Similarity";
		int[] completedByNode = new int[3];
		int completed = 0;
		int attempts = 0;
		while (completed < 30 && attempts < 600)
		{
			attempts++;
			for (int node = 1; node <= 3 && completed < 30; node++)
			{
				RecommendationWorkQueueRegistry.ShardClaim claim =
						RecommendationWorkQueueRegistry
								.claimExperimentalCandidateShard(
										runId,
										algorithmKey,
										node,
										3,
										30);
				if (!claim.hasShard())
				{
					if (claim.isDeferred())
					{
						Thread.sleep(5L);
					}
					continue;
				}
				RecommendationWorkQueueRegistry
						.completeExperimentalCandidateShard(
								runId,
								algorithmKey,
								node,
								3,
								30,
								claim.getShardNumber());
				completedByNode[node - 1]++;
				completed++;
			}
		}

		RecommendationWorkQueueRegistry.QueueSnapshot snapshot =
				RecommendationWorkQueueRegistry
						.snapshotExperimentalCandidateQueue(
								runId,
								algorithmKey,
								3,
								30);
		assertTrue(
				snapshot.getCompletedCount() == 30,
				"all experimental candidate shards should complete");
		assertTrue(
				completedByNode[0] > completedByNode[1],
				"strong node should claim more shards than weak node");
		assertTrue(
				completedByNode[2] >= completedByNode[1],
				"normal node should not receive less work than weak node");
		RecommendationSchedulerRegistry.restoreDefault(3, heapMb);
		System.out.println("ExperimentalCandidateQueueSelfTest passed");
	}

	private static void assertTrue(boolean condition, String message)
	{
		if (!condition)
		{
			throw new AssertionError(message);
		}
	}
}
