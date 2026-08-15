package TwitterGatherDataFollowers.userRyersonU;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class LargeTextAgentProvisioningSelfTest
{
	private LargeTextAgentProvisioningSelfTest()
	{
	}

	public static void main(String[] args)
	{
		preservesDirectProvisioningForOrdinaryDatasets();
		boundsLargeDatasetParticipants();
		System.out.println("LargeTextAgentProvisioningSelfTest passed");
	}

	private static void preservesDirectProvisioningForOrdinaryDatasets()
	{
		require(!ControllerAgent.shouldUseBoundedTextAgentProvisioning(128),
				"ordinary text datasets unexpectedly use virtual provisioning");
		require(ControllerAgent.calculateTextParticipantCount(
				users(40), Arrays.asList("user-1"), false) == 40,
				"ordinary text participant count changed");
	}

	private static void boundsLargeDatasetParticipants()
	{
		List<String> activeUsers = users(38546);
		require(ControllerAgent.supportsBoundedCorpusStreaming(
				ControllerAgent.SVM),
				"SVM large-corpus streaming is not enabled");
		require(ControllerAgent.supportsBoundedCorpusStreaming(
				ControllerAgent.SCALABLE_SVM),
				"Scalable SVM large-corpus streaming is not enabled");
			require(ControllerAgent.supportsBoundedCorpusStreaming(
					ControllerAgent.Doc2Vec),
					"Doc2Vec large-corpus streaming is not enabled");
			require(ControllerAgent.supportsBoundedCorpusStreaming(
					ControllerAgent.CommonNeighbors),
					"Common-Neighbors large-corpus streaming is not enabled");
			require(ControllerAgent.supportsBoundedCorpusStreaming(
					ControllerAgent.COS_SIM),
					"Similarity large-corpus streaming is not enabled");
			require(!ControllerAgent.supportsBoundedCorpusStreaming(
					ControllerAgent.K_MEANS),
					"unvalidated algorithms must retain their existing agent path");
		require(ControllerAgent.shouldUseBoundedTextAgentProvisioning(
				activeUsers.size()),
				"38k journal dataset did not enable bounded provisioning");
		require(ControllerAgent.calculateTextParticipantCount(
				activeUsers, Arrays.asList("user-1"), true) == 2,
				"one target plus one corpus streamer was not provisioned");
		require(ControllerAgent.calculateTextParticipantCount(
				activeUsers,
				Arrays.asList("user-1", "user-2", "user-2", "missing"),
				true) == 3,
				"active targets were not counted uniquely");
		require(ControllerAgent.calculateTextParticipantCount(
				Arrays.asList("user-1", "user-2"),
				Arrays.asList("user-1", "user-2"),
				true) == 2,
				"a phantom corpus streamer was counted when every user is a target");
	}

	private static List<String> users(int count)
	{
		ArrayList<String> users = new ArrayList<String>(count);
		for (int i = 1; i <= count; i++)
		{
			users.add("user-" + i);
		}
		return users;
	}

	private static void require(boolean condition, String message)
	{
		if (!condition)
		{
			throw new AssertionError(message);
		}
	}
}
