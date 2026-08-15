package TwitterGatherDataFollowers.userRyersonU;

public enum RecommendationCandidateType
{
	USER("User"),
	FOLLOWEE("Followee account");

	private final String displayName;

	RecommendationCandidateType(String displayName)
	{
		this.displayName = displayName;
	}

	@Override
	public String toString()
	{
		return displayName;
	}
}
