package TwitterGatherDataFollowers.userRyersonU;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Display helpers for node capacity. Placement lives in
 * {@link PlacementPlanner}, execution in {@link WorkDispatcher}, and live
 * emulation in {@link NodeEmulator}.
 */
final class NodeCapacityScheduler
{
	private NodeCapacityScheduler()
	{
	}

	/**
	 * The node's relative throughput, speed x lanes, where speed is its CPU
	 * multiplier relative to the fastest configured node, divided by its
	 * slowdown. This is exactly the capacity the planner and the dispatcher
	 * schedule with, and the speed a live run emulates.
	 */
	static double schedulingWeight(NodeCapacityProfile profile)
	{
		if (profile == null)
		{
			return 1.0;
		}
		List<NodeCapacityProfile> profiles = new ArrayList<NodeCapacityProfile>(
				NodeCapacityRegistry.snapshot());
		boolean replaced = false;
		for (int index = 0; index < profiles.size(); index++)
		{
			if (profiles.get(index).getNodeIndex() == profile.getNodeIndex())
			{
				profiles.set(index, profile);
				replaced = true;
			}
		}
		if (!replaced)
		{
			profiles.add(profile);
		}
		for (NodeSpec spec : NodeSpec.fromProfiles(profiles, null, true))
		{
			if (spec.getIndex() == profile.getNodeIndex())
			{
				return spec.getThroughput();
			}
		}
		return 1.0;
	}
}
