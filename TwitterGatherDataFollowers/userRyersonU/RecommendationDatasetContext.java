package TwitterGatherDataFollowers.userRyersonU;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * What the user agents need from the dataset to describe a recommendation in
 * the legacy text outputs: each user's followee and the followed accounts.
 */
final class RecommendationDatasetContext
{
	private static final RecommendationDatasetContext EMPTY =
			new RecommendationDatasetContext(
					Collections.<String, String>emptyMap(),
					Collections.<String>emptySet());

	private final Map<String, String> userFollowees;
	private final Set<String> referenceUsers;

	RecommendationDatasetContext(
			Map<String, String> userFollowees,
			Collection<String> referenceUsers)
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
	}

	static RecommendationDatasetContext empty()
	{
		return EMPTY;
	}

	/** The account a candidate stands for: a user's followee, or the account itself. */
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

	boolean isEmpty()
	{
		return userFollowees.isEmpty() && referenceUsers.isEmpty();
	}
}
