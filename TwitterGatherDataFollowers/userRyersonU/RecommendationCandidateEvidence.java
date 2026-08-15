package TwitterGatherDataFollowers.userRyersonU;

import java.io.Serializable;

final class RecommendationCandidateEvidence implements Serializable
{
	private static final long serialVersionUID = 1L;

	private final String followee;
	private final double textSimilarity;

	RecommendationCandidateEvidence(String followee, double textSimilarity)
	{
		this.followee = followee;
		this.textSimilarity = textSimilarity;
	}

	String getFollowee()
	{
		return followee;
	}

	double getTextSimilarity()
	{
		return textSimilarity;
	}
}
