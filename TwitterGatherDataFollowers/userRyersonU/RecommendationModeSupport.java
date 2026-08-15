package TwitterGatherDataFollowers.userRyersonU;

final class RecommendationModeSupport
{
	static final int COS_SIM = 0;
	static final int K_MEANS = 1;
	static final int SVM = 2;
	static final int MLP = 3;
	static final int DOC2VEC = 4;
	static final int COMMON_NEIGHBORS = 5;
	static final int K_MEANS_EUCLIDEAN = 6;
	static final int SCALABLE_SVM = 7;
	static final int TEMPORAL_GRAPH_FOUNDATION = 8;

	private RecommendationModeSupport()
	{
	}

	static boolean isUserRankingAlgorithm(int algorithm)
	{
		return algorithm == COS_SIM
				|| algorithm == K_MEANS
				|| algorithm == DOC2VEC
				|| algorithm == COMMON_NEIGHBORS
				|| algorithm == K_MEANS_EUCLIDEAN
				|| algorithm == TEMPORAL_GRAPH_FOUNDATION;
	}

	static boolean isClassifierAlgorithm(int algorithm)
	{
		return isSvmFamilyAlgorithm(algorithm) || algorithm == MLP;
	}

	static boolean isSvmFamilyAlgorithm(int algorithm)
	{
		return algorithm == SVM || algorithm == SCALABLE_SVM;
	}

	static boolean isWorkingAlgorithm(int algorithm)
	{
		return isUserRankingAlgorithm(algorithm) || isClassifierAlgorithm(algorithm);
	}

	static RecommendationCandidateType nativeCandidateType(int algorithm)
	{
		if (isUserRankingAlgorithm(algorithm))
		{
			return RecommendationCandidateType.USER;
		}
		if (isClassifierAlgorithm(algorithm))
		{
			return RecommendationCandidateType.FOLLOWEE;
		}
		return null;
	}

	static RecommendationCandidateType resolveCandidateType(RecommendationTargetMode mode, int algorithm)
	{
		if (mode == RecommendationTargetMode.SIMILAR_USERS)
		{
			return RecommendationCandidateType.USER;
		}
		if (mode == RecommendationTargetMode.FOLLOWEE_ACCOUNTS)
		{
			return RecommendationCandidateType.FOLLOWEE;
		}
		return nativeCandidateType(algorithm);
	}

	static boolean requiresConvertedPipeline(RecommendationTargetMode mode, int algorithm)
	{
		if (mode == null || mode == RecommendationTargetMode.ALGORITHM_DEFAULT)
		{
			return false;
		}
		RecommendationCandidateType nativeType = nativeCandidateType(algorithm);
		RecommendationCandidateType requestedType = resolveCandidateType(mode, algorithm);
		return nativeType != null && nativeType != requestedType;
	}

	static String algorithmName(int algorithm)
	{
		switch (algorithm)
		{
			case COS_SIM:
				return "Similarity";
			case K_MEANS:
				return "K-Means";
			case SVM:
				return "SVM";
			case MLP:
				return "MLP";
			case DOC2VEC:
				return "Doc2Vec";
			case COMMON_NEIGHBORS:
				return "Common-Neighbors";
			case K_MEANS_EUCLIDEAN:
				return "K-Means Euclidean";
			case SCALABLE_SVM:
				return "Scalable Linear SVM";
			case TEMPORAL_GRAPH_FOUNDATION:
				return "Temporal Graph Foundation";
			default:
				return "Unknown";
		}
	}
}
