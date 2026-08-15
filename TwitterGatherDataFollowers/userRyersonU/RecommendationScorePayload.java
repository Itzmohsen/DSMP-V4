package TwitterGatherDataFollowers.userRyersonU;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

final class RecommendationScorePayload implements Serializable
{
	private static final long serialVersionUID = 1L;

	private final RecommendationTargetMode targetMode;
	private final RecommendationCandidateType candidateType;
	private final RecommendationScoreKind scoreKind;
	private final RecommendationMergePolicy mergePolicy;
	private final String resultLabel;
	private final ArrayList<String> targetUsers;
	private final TreeMap<String,TreeMap<String,Double>> directScores;
	private final TreeMap<String,TreeMap<String,Double>> classifierScores;
	private final TreeMap<String,TreeMap<String,RecommendationCandidateEvidence>> candidateEvidence;
	private final TreeMap<String,TreeMap<String,String>> candidateDetails;
	private final TreeMap<String,String> errors;

	private RecommendationScorePayload(
			RecommendationTargetMode targetMode,
			RecommendationCandidateType candidateType,
			RecommendationScoreKind scoreKind,
			RecommendationMergePolicy mergePolicy,
			String resultLabel,
			List<String> targetUsers,
			Map<String,? extends Map<String,Double>> directScores,
			Map<String,? extends Map<String,Double>> classifierScores,
			Map<String,? extends Map<String,RecommendationCandidateEvidence>> candidateEvidence,
			Map<String,? extends Map<String,String>> candidateDetails,
			Map<String,String> errors)
	{
		this.targetMode = targetMode;
		this.candidateType = candidateType;
		this.scoreKind = scoreKind;
		this.mergePolicy = mergePolicy;
		this.resultLabel = resultLabel;
		this.targetUsers = new ArrayList<String>();
		if (targetUsers != null)
		{
			this.targetUsers.addAll(targetUsers);
		}
		this.directScores = copyDoubleMaps(directScores);
		this.classifierScores = copyDoubleMaps(classifierScores);
		this.candidateEvidence = copyEvidenceMaps(candidateEvidence);
		this.candidateDetails = copyStringMaps(candidateDetails);
		this.errors = errors == null
				? new TreeMap<String,String>() : new TreeMap<String,String>(errors);
	}

	static RecommendationScorePayload direct(
			RecommendationTargetMode mode,
			RecommendationCandidateType candidateType,
			RecommendationMergePolicy mergePolicy,
			List<String> targetUsers,
			Map<String,? extends Map<String,Double>> scores,
			Map<String,String> errors)
	{
		return new RecommendationScorePayload(
				mode,
				candidateType,
				RecommendationScoreKind.DIRECT,
				mergePolicy,
				null,
				targetUsers,
				scores,
				null,
				null,
				null,
				errors);
	}

	static RecommendationScorePayload directWithDetails(
			RecommendationTargetMode mode,
			RecommendationCandidateType candidateType,
			RecommendationMergePolicy mergePolicy,
			List<String> targetUsers,
			Map<String,? extends Map<String,Double>> scores,
			Map<String,? extends Map<String,String>> candidateDetails,
			Map<String,String> errors)
	{
		return new RecommendationScorePayload(
				mode,
				candidateType,
				RecommendationScoreKind.DIRECT,
				mergePolicy,
				null,
				targetUsers,
				scores,
				null,
				null,
				candidateDetails,
				errors);
	}

	static RecommendationScorePayload classifierGuidedUsers(
			RecommendationTargetMode mode,
			List<String> targetUsers,
			Map<String,? extends Map<String,Double>> classifierScores,
			Map<String,? extends Map<String,RecommendationCandidateEvidence>> candidateEvidence,
			Map<String,String> errors)
	{
		return classifierGuidedUsers(
				mode, targetUsers, classifierScores, candidateEvidence, null, errors);
	}

	static RecommendationScorePayload classifierGuidedUsers(
			RecommendationTargetMode mode,
			List<String> targetUsers,
			Map<String,? extends Map<String,Double>> classifierScores,
			Map<String,? extends Map<String,RecommendationCandidateEvidence>> candidateEvidence,
			String resultLabel,
			Map<String,String> errors)
	{
		return new RecommendationScorePayload(
				mode,
				RecommendationCandidateType.USER,
				RecommendationScoreKind.CLASSIFIER_GUIDED_USERS,
				RecommendationMergePolicy.SUM,
				resultLabel,
				targetUsers,
				null,
				classifierScores,
				candidateEvidence,
				null,
				errors);
	}

	RecommendationTargetMode getTargetMode()
	{
		return targetMode;
	}

	RecommendationCandidateType getCandidateType()
	{
		return candidateType;
	}

	RecommendationScoreKind getScoreKind()
	{
		return scoreKind;
	}

	RecommendationMergePolicy getMergePolicy()
	{
		return mergePolicy;
	}

	String getResultLabel()
	{
		return resultLabel;
	}

	List<String> getTargetUsers()
	{
		return Collections.unmodifiableList(targetUsers);
	}

	TreeMap<String,TreeMap<String,Double>> getDirectScores()
	{
		return directScores;
	}

	TreeMap<String,TreeMap<String,Double>> getClassifierScores()
	{
		return classifierScores;
	}

	TreeMap<String,TreeMap<String,RecommendationCandidateEvidence>> getCandidateEvidence()
	{
		return candidateEvidence;
	}

	TreeMap<String,TreeMap<String,String>> getCandidateDetails()
	{
		return candidateDetails;
	}

	TreeMap<String,String> getErrors()
	{
		return errors;
	}

	private static TreeMap<String,TreeMap<String,Double>> copyDoubleMaps(
			Map<String,? extends Map<String,Double>> source)
	{
		TreeMap<String,TreeMap<String,Double>> copy =
				new TreeMap<String,TreeMap<String,Double>>();
		if (source == null)
		{
			return copy;
		}
		for (Map.Entry<String,? extends Map<String,Double>> entry : source.entrySet())
		{
			copy.put(entry.getKey(), entry.getValue() == null
					? new TreeMap<String,Double>()
					: new TreeMap<String,Double>(entry.getValue()));
		}
		return copy;
	}

	private static TreeMap<String,TreeMap<String,RecommendationCandidateEvidence>> copyEvidenceMaps(
			Map<String,? extends Map<String,RecommendationCandidateEvidence>> source)
	{
		TreeMap<String,TreeMap<String,RecommendationCandidateEvidence>> copy =
				new TreeMap<String,TreeMap<String,RecommendationCandidateEvidence>>();
		if (source == null)
		{
			return copy;
		}
		for (Map.Entry<String,? extends Map<String,RecommendationCandidateEvidence>> entry : source.entrySet())
		{
			copy.put(entry.getKey(), entry.getValue() == null
					? new TreeMap<String,RecommendationCandidateEvidence>()
					: new TreeMap<String,RecommendationCandidateEvidence>(entry.getValue()));
		}
		return copy;
	}

	private static TreeMap<String,TreeMap<String,String>> copyStringMaps(
			Map<String,? extends Map<String,String>> source)
	{
		TreeMap<String,TreeMap<String,String>> copy =
				new TreeMap<String,TreeMap<String,String>>();
		if (source == null)
		{
			return copy;
		}
		for (Map.Entry<String,? extends Map<String,String>> entry : source.entrySet())
		{
			copy.put(entry.getKey(), entry.getValue() == null
					? new TreeMap<String,String>()
					: new TreeMap<String,String>(entry.getValue()));
		}
		return copy;
	}
}
