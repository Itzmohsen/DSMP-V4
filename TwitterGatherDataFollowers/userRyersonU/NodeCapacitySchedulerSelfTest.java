package TwitterGatherDataFollowers.userRyersonU;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class NodeCapacitySchedulerSelfTest
{
	private NodeCapacitySchedulerSelfTest()
	{
	}

	public static void main(String[] args)
	{
		verifyDefaultShardContract();
		verifyActiveShardCoverage();
		verifyDeterministicTargetAssignment();
		verifyQueueClaimsEveryShardOnce();
		verifyActiveQueueClaimsRespectCapacity();
		System.out.println("NodeCapacitySchedulerSelfTest passed");
	}

	private static void verifyDefaultShardContract()
	{
		int nodes = 4;
		int heapMb = 4096;
		NodeCapacityRegistry.configureForNodeCount(nodes, heapMb);
		RecommendationSchedulerRegistry.restoreDefault(nodes, heapMb);
		int shardCount =
				NodeCapacityScheduler.virtualCandidateShardCount(nodes, 20);
		assertTrue(shardCount == nodes, "default shard count must match nodes");
		for (int node = 1; node <= nodes; node++)
		{
			List<Integer> shards =
					NodeCapacityScheduler.assignedVirtualCandidateShards(
							node,
							nodes,
							shardCount);
			assertTrue(shards.size() == 1,
					"default node should own exactly one shard");
			assertTrue(shards.get(0).intValue() == node,
					"default shard id should equal physical node id");
		}
	}

	private static void verifyActiveShardCoverage()
	{
		int nodes = 3;
		int heapMb = 4096;
		ArrayList<NodeCapacityProfile> profiles =
				new ArrayList<NodeCapacityProfile>();
		profiles.add(NodeCapacityProfile.rolePreset(
				1,
				NodeCapacityProfile.Role.STRONG,
				nodes,
				heapMb));
		profiles.add(NodeCapacityProfile.rolePreset(
				2,
				NodeCapacityProfile.Role.WEAK,
				nodes,
				heapMb));
		profiles.add(NodeCapacityProfile.rolePreset(
				3,
				NodeCapacityProfile.Role.NORMAL,
				nodes,
				heapMb));
		NodeCapacityRegistry.setProfiles(profiles, heapMb);
		RecommendationSchedulerRegistry.apply(
				RecommendationSchedulerPlan.Mode.SCORING_SAFE,
				SchedulerLabEngine.SchedulingPolicy.CAPACITY_WEIGHTED,
				nodes,
				heapMb,
				"self-test");

		int shardCount =
				NodeCapacityScheduler.virtualCandidateShardCount(nodes, 120);
		Set<Integer> seen = new HashSet<Integer>();
		int strongCount = 0;
		int weakCount = 0;
		for (int node = 1; node <= nodes; node++)
		{
			List<Integer> shards =
					NodeCapacityScheduler.assignedVirtualCandidateShards(
							node,
							nodes,
							shardCount);
			if (node == 1)
			{
				strongCount = shards.size();
			}
			else if (node == 2)
			{
				weakCount = shards.size();
			}
			for (Integer shard : shards)
			{
				assertTrue(seen.add(shard),
						"virtual shard assigned more than once: " + shard);
			}
		}
		assertTrue(seen.size() == shardCount,
				"all virtual shards must be assigned exactly once");
		assertTrue(strongCount > weakCount,
				"strong node should receive more shards than weak node");

		RecommendationSchedulerRegistry.restoreDefault(nodes, heapMb);
	}

	private static void verifyDeterministicTargetAssignment()
	{
		int nodes = 3;
		int heapMb = 4096;
		ArrayList<NodeCapacityProfile> profiles =
				new ArrayList<NodeCapacityProfile>();
		profiles.add(NodeCapacityProfile.rolePreset(
				1,
				NodeCapacityProfile.Role.STRONG,
				nodes,
				heapMb));
		profiles.add(NodeCapacityProfile.rolePreset(
				2,
				NodeCapacityProfile.Role.WEAK,
				nodes,
				heapMb));
		profiles.add(NodeCapacityProfile.rolePreset(
				3,
				NodeCapacityProfile.Role.NORMAL,
				nodes,
				heapMb));
		NodeCapacityRegistry.setProfiles(profiles, heapMb);
		RecommendationSchedulerRegistry.apply(
				RecommendationSchedulerPlan.Mode.SCHEDULING_EXPERIMENT,
				SchedulerLabEngine.SchedulingPolicy.CAPACITY_WEIGHTED,
				nodes,
				heapMb,
				"target assignment self-test");

		ArrayList<String> targets = new ArrayList<String>();
		for (int index = 0; index < 18; index++)
		{
			targets.add("target-" + index);
		}

		List<List<String>> first =
				NodeCapacityScheduler.assignTargetJobsDeterministic(
						targets,
						nodes);
		List<List<String>> second =
				NodeCapacityScheduler.assignTargetJobsDeterministic(
						targets,
						nodes);
		assertTrue(first.equals(second),
				"deterministic target assignment must be stable across callers");

		Set<String> seen = new HashSet<String>();
		for (List<String> bucket : first)
		{
			for (String target : bucket)
			{
				assertTrue(seen.add(target),
						"target assigned more than once: " + target);
			}
		}
		assertTrue(seen.size() == targets.size(),
				"every target should be assigned exactly once");
		assertTrue(first.get(0).size() > first.get(1).size(),
				"strong node should receive more target jobs than weak node");

		RecommendationSchedulerRegistry.restoreDefault(nodes, heapMb);
	}

	private static void verifyQueueClaimsEveryShardOnce()
	{
		int nodes = 4;
		int shards = 16;
		Set<Integer> seen = new HashSet<Integer>();
		int turn = 0;
		while (true)
		{
			int node = (turn % nodes) + 1;
			RecommendationWorkQueueRegistry.ShardClaim claim =
					RecommendationWorkQueueRegistry.claimDoc2VecCandidateShard(
							"queue-self-test",
							node,
							nodes,
							shards);
			if (!claim.hasShard())
			{
				break;
			}
			assertTrue(seen.add(Integer.valueOf(claim.getShardNumber())),
					"queue returned duplicate shard "
					+ claim.getShardNumber());
			turn++;
		}
		assertTrue(seen.size() == shards,
				"queue should return every shard exactly once");
		assertTrue(
				RecommendationWorkQueueRegistry.claimedDoc2VecShardCount(
						"queue-self-test",
						1,
						nodes,
						shards) > 0,
				"queue should track node claims");
	}

	private static void verifyActiveQueueClaimsRespectCapacity()
	{
		int nodes = 3;
		int heapMb = 4096;
		int shards = 18;
		String runId = "active-queue-self-test-" + System.nanoTime();
		ArrayList<NodeCapacityProfile> profiles =
				new ArrayList<NodeCapacityProfile>();
		profiles.add(NodeCapacityProfile.rolePreset(
				1,
				NodeCapacityProfile.Role.STRONG,
				nodes,
				heapMb));
		profiles.add(NodeCapacityProfile.rolePreset(
				2,
				NodeCapacityProfile.Role.WEAK,
				nodes,
				heapMb));
		profiles.add(NodeCapacityProfile.rolePreset(
				3,
				NodeCapacityProfile.Role.NORMAL,
				nodes,
				heapMb));
		NodeCapacityRegistry.setProfiles(profiles, heapMb);
		RecommendationSchedulerRegistry.apply(
				RecommendationSchedulerPlan.Mode.SCORING_SAFE,
				SchedulerLabEngine.SchedulingPolicy.CAPACITY_WEIGHTED,
				nodes,
				heapMb,
				"active queue self-test");

		Set<Integer> seen = new HashSet<Integer>();
		int turn = 0;
		int attempts = 0;
		while (seen.size() < shards && attempts < 5000)
		{
			int node = (turn % nodes) + 1;
			turn++;
			attempts++;
			RecommendationWorkQueueRegistry.ShardClaim claim =
					RecommendationWorkQueueRegistry.claimDoc2VecCandidateShard(
							runId,
							node,
							nodes,
							shards);
			if (claim.hasShard())
			{
				assertTrue(seen.add(Integer.valueOf(claim.getShardNumber())),
						"active queue returned duplicate shard "
						+ claim.getShardNumber());
				RecommendationWorkQueueRegistry.completeDoc2VecCandidateShard(
						runId,
						node,
						nodes,
						shards,
						claim.getShardNumber());
			}
			else if (!claim.isDeferred())
			{
				break;
			}
		}

		assertTrue(seen.size() == shards,
				"active queue should return every shard exactly once");
		int strongClaims =
				RecommendationWorkQueueRegistry.claimedDoc2VecShardCount(
						runId,
						1,
						nodes,
						shards);
		int weakClaims =
				RecommendationWorkQueueRegistry.claimedDoc2VecShardCount(
						runId,
						2,
						nodes,
						shards);
		assertTrue(strongClaims > weakClaims,
				"active queue should let strong node claim more shards than weak node");

		RecommendationSchedulerRegistry.restoreDefault(nodes, heapMb);
	}

	private static void assertTrue(boolean condition, String message)
	{
		if (!condition)
		{
			throw new AssertionError(message);
		}
	}
}
