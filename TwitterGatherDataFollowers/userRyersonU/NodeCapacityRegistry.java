package TwitterGatherDataFollowers.userRyersonU;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class NodeCapacityRegistry
{
	private static final Object LOCK = new Object();
	private static final ArrayList<NodeCapacityProfile> PROFILES =
			new ArrayList<NodeCapacityProfile>();
	private static int configuredNodeCount = 0;
	private static int configuredHeapMb = RuntimeResourceSettings.currentMaxHeapMb();
	private static ClusterSettings clusterSettings = ClusterSettings.defaults();

	private NodeCapacityRegistry()
	{
	}

	static void configureForNodeCount(int nodeCount, int heapMb)
	{
		synchronized (LOCK)
		{
			int safeCount = Math.max(1, nodeCount);
			int safeHeap = Math.max(RuntimeResourceSettings.MIN_HEAP_MB, heapMb);
			if (configuredNodeCount == safeCount && !PROFILES.isEmpty())
			{
				if (configuredHeapMb != safeHeap)
				{
					refreshDefaultProfilesForHeapLocked(safeCount, safeHeap);
				}
				configuredHeapMb = safeHeap;
				return;
			}

			ArrayList<NodeCapacityProfile> next =
					new ArrayList<NodeCapacityProfile>();
			for (int index = 1; index <= safeCount; index++)
			{
				NodeCapacityProfile existing = findByIndexLocked(index);
				next.add(existing == null || isDefaultForLocked(
						existing,
						Math.max(1, configuredNodeCount),
						configuredHeapMb)
						? NodeCapacityProfile.defaultProfile(
								index,
								safeCount,
								safeHeap)
						: existing);
			}
			PROFILES.clear();
			PROFILES.addAll(next);
			configuredNodeCount = safeCount;
			configuredHeapMb = safeHeap;
		}
	}

	static void resetToDefaults(int nodeCount, int heapMb)
	{
		synchronized (LOCK)
		{
			int safeCount = Math.max(1, nodeCount);
			int safeHeap = Math.max(RuntimeResourceSettings.MIN_HEAP_MB, heapMb);
			PROFILES.clear();
			for (int index = 1; index <= safeCount; index++)
			{
				PROFILES.add(NodeCapacityProfile.defaultProfile(
						index,
						safeCount,
						safeHeap));
			}
			configuredNodeCount = safeCount;
			configuredHeapMb = safeHeap;
		}
	}

	static void setProfiles(
			List<NodeCapacityProfile> profiles,
			int heapMb)
	{
		synchronized (LOCK)
		{
			PROFILES.clear();
			if (profiles != null)
			{
				for (NodeCapacityProfile profile : profiles)
				{
					if (profile != null)
					{
						PROFILES.add(profile);
					}
				}
			}
			if (PROFILES.isEmpty())
			{
				PROFILES.add(NodeCapacityProfile.defaultProfile(
						1,
						1,
						heapMb));
			}
			Collections.sort(PROFILES, new java.util.Comparator<NodeCapacityProfile>()
			{
				public int compare(
						NodeCapacityProfile first,
						NodeCapacityProfile second)
				{
					return Integer.compare(
							first.getNodeIndex(),
							second.getNodeIndex());
				}
			});
			configuredNodeCount = PROFILES.size();
			configuredHeapMb = Math.max(
					RuntimeResourceSettings.MIN_HEAP_MB,
					heapMb);
		}
	}

	static NodeCapacityProfile profileForNode(int nodeIndex)
	{
		synchronized (LOCK)
		{
			NodeCapacityProfile profile = findByIndexLocked(nodeIndex);
			if (profile != null)
			{
				return profile;
			}
			int count = Math.max(1, configuredNodeCount);
			return NodeCapacityProfile.defaultProfile(
					Math.max(1, nodeIndex),
					count,
					configuredHeapMb);
		}
	}

	static NodeCapacityProfile profileForAgentName(String agentName)
	{
		return profileForNode(parseNodeIndex(agentName));
	}

	/** Cluster-wide settings (coordinator, time dilation, failure model, seed). */
	static ClusterSettings clusterSettings()
	{
		synchronized (LOCK)
		{
			return clusterSettings;
		}
	}

	static void setClusterSettings(ClusterSettings settings)
	{
		synchronized (LOCK)
		{
			clusterSettings = settings == null ? ClusterSettings.defaults() : settings;
		}
	}

	static List<NodeCapacityProfile> snapshot()
	{
		synchronized (LOCK)
		{
			return Collections.unmodifiableList(
					new ArrayList<NodeCapacityProfile>(PROFILES));
		}
	}

	static int configuredNodeCount()
	{
		synchronized (LOCK)
		{
			return Math.max(1, configuredNodeCount);
		}
	}

	static int configuredHeapMb()
	{
		synchronized (LOCK)
		{
			return Math.max(RuntimeResourceSettings.MIN_HEAP_MB, configuredHeapMb);
		}
	}

	static String summary()
	{
		synchronized (LOCK)
		{
			if (PROFILES.isEmpty())
			{
				return "Balanced default capacity";
			}
			int strong = 0;
			int weak = 0;
			int simulated = 0;
			long memoryTotal = 0L;
			for (NodeCapacityProfile profile : PROFILES)
			{
				if (profile.getRole() == NodeCapacityProfile.Role.STRONG)
				{
					strong++;
				}
				else if (profile.getRole() == NodeCapacityProfile.Role.WEAK)
				{
					weak++;
				}
				if (profile.isSlowdownSimulationEnabled()
						|| profile.isFailureSimulationEnabled()
						|| profile.getNetworkDelayMs() > 0)
				{
					simulated++;
				}
				memoryTotal += profile.getMemoryLimitMb();
			}
			StringBuilder builder = new StringBuilder();
			builder.append(PROFILES.size()).append(" node");
			builder.append(PROFILES.size() == 1 ? "" : "s");
			builder.append(" | ");
			builder.append(RuntimeResourceSettings.formatMegabytes(memoryTotal));
			builder.append(" capacity budget");
			if (strong > 0 || weak > 0)
			{
				builder.append(" | ");
				if (strong > 0)
				{
					builder.append(strong).append(" strong");
				}
				if (strong > 0 && weak > 0)
				{
					builder.append(", ");
				}
				if (weak > 0)
				{
					builder.append(weak).append(" weak");
				}
			}
			if (simulated > 0)
			{
				builder.append(" | ").append(simulated)
						.append(" simulation profile");
				builder.append(simulated == 1 ? "" : "s");
			}
			return builder.toString();
		}
	}

	private static NodeCapacityProfile findByIndexLocked(int nodeIndex)
	{
		for (NodeCapacityProfile profile : PROFILES)
		{
			if (profile.getNodeIndex() == nodeIndex)
			{
				return profile;
			}
		}
		return null;
	}

	private static void refreshDefaultProfilesForHeapLocked(
			int nodeCount,
			int heapMb)
	{
		for (int index = 0; index < PROFILES.size(); index++)
		{
			NodeCapacityProfile profile = PROFILES.get(index);
			if (isDefaultForLocked(
					profile,
					Math.max(1, configuredNodeCount),
					configuredHeapMb))
			{
				PROFILES.set(
						index,
						NodeCapacityProfile.defaultProfile(
								profile.getNodeIndex(),
								nodeCount,
								heapMb));
			}
		}
	}

	private static boolean isDefaultForLocked(
			NodeCapacityProfile profile,
			int nodeCount,
			int heapMb)
	{
		if (profile == null)
		{
			return false;
		}
		return profile.sameSettingsAs(NodeCapacityProfile.defaultProfile(
				profile.getNodeIndex(),
				nodeCount,
				heapMb));
	}

	private static int parseNodeIndex(String agentName)
	{
		if (agentName == null)
		{
			return 1;
		}
		int end = agentName.length() - 1;
		while (end >= 0 && !Character.isDigit(agentName.charAt(end)))
		{
			end--;
		}
		if (end < 0)
		{
			return 1;
		}
		int start = end;
		while (start >= 0 && Character.isDigit(agentName.charAt(start)))
		{
			start--;
		}
		try
		{
			return Math.max(1, Integer.parseInt(
					agentName.substring(start + 1, end + 1)));
		}
		catch (NumberFormatException exception)
		{
			return 1;
		}
	}
}
