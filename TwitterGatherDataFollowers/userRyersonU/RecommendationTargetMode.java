package TwitterGatherDataFollowers.userRyersonU;

public enum RecommendationTargetMode
{
	ALGORITHM_DEFAULT("Algorithm Default"),
	SIMILAR_USERS("Similar Users"),
	FOLLOWEE_ACCOUNTS("Followee Accounts");

	private final String displayName;

	RecommendationTargetMode(String displayName)
	{
		this.displayName = displayName;
	}

	@Override
	public String toString()
	{
		return displayName;
	}
}
