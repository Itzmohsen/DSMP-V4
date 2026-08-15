package TwitterGatherDataFollowers.userRyersonU;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class RecommendationDatasetContext
{
	private static final RecommendationDatasetContext EMPTY =
			new RecommendationDatasetContext(
					Collections.<String, String>emptyMap(),
					Collections.<String>emptySet(),
					Collections.<String>emptyList());

	private final Map<String, String> userFollowees;
	private final Set<String> referenceUsers;
	private final List<String> graphNodeNames;

	RecommendationDatasetContext(
			Map<String, String> userFollowees,
			Collection<String> referenceUsers,
			Collection<String> graphNodeNames)
	{
		this.userFollowees = Collections.unmodifiableMap(
				new LinkedHashMap<String, String>(
						userFollowees == null
								? Collections.<String, String>emptyMap()
								: userFollowees));
		this.referenceUsers = Collections.unmodifiableSet(
				new LinkedHashSet<String>(
						referenceUsers == null
								? Collections.<String>emptySet()
								: referenceUsers));
		this.graphNodeNames = Collections.unmodifiableList(
				new ArrayList<String>(
						graphNodeNames == null
								? Collections.<String>emptyList()
								: graphNodeNames));
	}

	static RecommendationDatasetContext empty()
	{
		return EMPTY;
	}

	String resolveFirstColumn(String identity)
	{
		if (identity == null || identity.length() == 0)
		{
			return null;
		}

		String followee = userFollowees.get(identity);
		if (followee != null)
		{
			return followee;
		}
		return referenceUsers.contains(identity) ? identity : null;
	}

	List<String> getGraphNodeNames()
	{
		return graphNodeNames;
	}

	boolean hasGraphNodeNames()
	{
		return !graphNodeNames.isEmpty();
	}
}
