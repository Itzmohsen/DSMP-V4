package TwitterGatherDataFollowers.userRyersonU;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

final class RecommendationResultPayload implements Serializable
{
	private static final long serialVersionUID = 1L;

	private final String targetUser;
	private final RecommendationTargetMode targetMode;
	private final RecommendationCandidateType candidateType;
	private final RecommendationResultStatus status;
	private final String resultLabel;
	private final String message;
	private final LinkedHashMap<String,Double> scores;
	private final LinkedHashMap<String,String> candidateDetails;

	RecommendationResultPayload(
			String targetUser,
			RecommendationTargetMode targetMode,
			RecommendationCandidateType candidateType,
			RecommendationResultStatus status,
			String message,
			Map<String,Double> scores)
	{
		this(targetUser, targetMode, candidateType, status, null, message, scores, null);
	}

	RecommendationResultPayload(
			String targetUser,
			RecommendationTargetMode targetMode,
			RecommendationCandidateType candidateType,
			RecommendationResultStatus status,
			String message,
			Map<String,Double> scores,
			Map<String,String> candidateDetails)
	{
		this(targetUser, targetMode, candidateType, status, null,
				message, scores, candidateDetails);
	}

	RecommendationResultPayload(
			String targetUser,
			RecommendationTargetMode targetMode,
			RecommendationCandidateType candidateType,
			RecommendationResultStatus status,
			String resultLabel,
			String message,
			Map<String,Double> scores,
			Map<String,String> candidateDetails)
	{
		this.targetUser = targetUser;
		this.targetMode = targetMode;
		this.candidateType = candidateType;
		this.status = status;
		this.resultLabel = resultLabel;
		this.message = message;
		this.scores = new LinkedHashMap<String,Double>();
		if (scores != null)
		{
			this.scores.putAll(scores);
		}
		this.candidateDetails = new LinkedHashMap<String,String>();
		if (candidateDetails != null)
		{
			this.candidateDetails.putAll(candidateDetails);
		}
	}

	String getTargetUser()
	{
		return targetUser;
	}

	RecommendationTargetMode getTargetMode()
	{
		return targetMode;
	}

	RecommendationCandidateType getCandidateType()
	{
		return candidateType;
	}

	RecommendationResultStatus getStatus()
	{
		return status;
	}

	String getResultLabel()
	{
		return resultLabel;
	}

	String getMessage()
	{
		return message;
	}

	LinkedHashMap<String,Double> getScores()
	{
		return new LinkedHashMap<String,Double>(scores);
	}

	LinkedHashMap<String,String> getCandidateDetails()
	{
		return new LinkedHashMap<String,String>(candidateDetails);
	}
}
