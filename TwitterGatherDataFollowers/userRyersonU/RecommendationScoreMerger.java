package TwitterGatherDataFollowers.userRyersonU;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

final class RecommendationScoreMerger
{
	private RecommendationScoreMerger()
	{
	}

	static Map<String,RecommendationResultPayload> merge(List<RecommendationScorePayload> payloads)
	{
		LinkedHashMap<String,RecommendationResultPayload> results =
				new LinkedHashMap<String,RecommendationResultPayload>();
		if (payloads == null || payloads.isEmpty())
		{
			return results;
		}

		RecommendationScorePayload first = payloads.get(0);
		Set<String> targets = new LinkedHashSet<String>();
		for (RecommendationScorePayload payload : payloads)
		{
			if (payload != null)
			{
				targets.addAll(payload.getTargetUsers());
			}
		}

		if (first.getScoreKind() == RecommendationScoreKind.CLASSIFIER_GUIDED_USERS)
		{
			mergeClassifierGuided(payloads, targets, first, results);
		}
		else
		{
			mergeDirect(payloads, targets, first, results);
		}
		return results;
	}

	private static void mergeDirect(
			List<RecommendationScorePayload> payloads,
			Set<String> targets,
			RecommendationScorePayload first,
			Map<String,RecommendationResultPayload> results)
	{
		for (String target : targets)
		{
			TreeMap<String,Double> mergedScores = new TreeMap<String,Double>();
			TreeMap<String,String> mergedDetails = new TreeMap<String,String>();
			String error = null;
			for (RecommendationScorePayload payload : payloads)
			{
				if (payload == null)
				{
					continue;
				}
				error = combineErrors(error, payload.getErrors().get(target));
				Map<String,Double> nodeScores = payload.getDirectScores().get(target);
				if (nodeScores == null)
				{
					continue;
				}
				for (Map.Entry<String,Double> entry : nodeScores.entrySet())
				{
					double score = finitePositive(entry.getValue());
					if (score <= 0.0)
					{
						continue;
					}
					Double oldScore = mergedScores.get(entry.getKey());
					if (oldScore == null)
					{
						mergedScores.put(entry.getKey(), score);
						copyDetail(payload, target, entry.getKey(), mergedDetails);
					}
					else if (first.getMergePolicy() == RecommendationMergePolicy.MAX)
					{
						if (score > oldScore)
						{
							mergedScores.put(entry.getKey(), score);
							copyDetail(payload, target, entry.getKey(), mergedDetails);
						}
						else if (Double.compare(score, oldScore) == 0)
						{
							String incomingDetail = getDetail(payload, target, entry.getKey());
							String existingDetail = mergedDetails.get(entry.getKey());
							if (incomingDetail != null && (existingDetail == null
									|| incomingDetail.compareTo(existingDetail) < 0))
							{
								mergedDetails.put(entry.getKey(), incomingDetail);
							}
						}
					}
					else
					{
						mergedScores.put(entry.getKey(), oldScore + score);
					}
				}
			}
			results.put(target, buildResult(
					target,
					first.getTargetMode(),
					first.getCandidateType(),
					first.getResultLabel(),
					mergedScores,
					mergedDetails,
					error));
		}
	}

	private static void mergeClassifierGuided(
			List<RecommendationScorePayload> payloads,
			Set<String> targets,
			RecommendationScorePayload first,
			Map<String,RecommendationResultPayload> results)
	{
		for (String target : targets)
		{
			TreeMap<String,Double> mergedClassScores = new TreeMap<String,Double>();
			TreeMap<String,RecommendationCandidateEvidence> mergedEvidence =
					new TreeMap<String,RecommendationCandidateEvidence>();
			String error = null;

			for (RecommendationScorePayload payload : payloads)
			{
				if (payload == null)
				{
					continue;
				}
				error = combineErrors(error, payload.getErrors().get(target));
				Map<String,Double> nodeClassScores = payload.getClassifierScores().get(target);
				if (nodeClassScores != null)
				{
					for (Map.Entry<String,Double> entry : nodeClassScores.entrySet())
					{
						double score = finitePositive(entry.getValue());
						if (score > 0.0)
						{
							Double oldScore = mergedClassScores.get(entry.getKey());
							mergedClassScores.put(
									entry.getKey(),
									(oldScore == null ? 0.0 : oldScore) + score);
						}
					}
				}

				Map<String,RecommendationCandidateEvidence> nodeEvidence =
						payload.getCandidateEvidence().get(target);
				if (nodeEvidence != null)
				{
					for (Map.Entry<String,RecommendationCandidateEvidence> entry : nodeEvidence.entrySet())
					{
						RecommendationCandidateEvidence evidence = entry.getValue();
						if (evidence == null || evidence.getFollowee() == null)
						{
							continue;
						}
						double similarity = finitePositive(evidence.getTextSimilarity());
						if (similarity <= 0.0)
						{
							continue;
						}
						RecommendationCandidateEvidence oldEvidence = mergedEvidence.get(entry.getKey());
						if (oldEvidence == null || similarity > oldEvidence.getTextSimilarity())
						{
							mergedEvidence.put(
									entry.getKey(),
									new RecommendationCandidateEvidence(evidence.getFollowee(), similarity));
						}
					}
				}
			}

			double classTotal = 0.0;
			for (Double classScore : mergedClassScores.values())
			{
				classTotal += finitePositive(classScore);
			}

			TreeMap<String,Double> finalScores = new TreeMap<String,Double>();
			TreeMap<String,String> finalDetails = new TreeMap<String,String>();
			if (classTotal > 0.0)
			{
				for (Map.Entry<String,RecommendationCandidateEvidence> entry : mergedEvidence.entrySet())
				{
					RecommendationCandidateEvidence evidence = entry.getValue();
					double classScore = finitePositive(mergedClassScores.get(evidence.getFollowee()));
					double finalScore = (classScore / classTotal) * evidence.getTextSimilarity();
					if (finalScore > 0.0 && Double.isFinite(finalScore))
					{
						finalScores.put(entry.getKey(), finalScore);
						finalDetails.put(entry.getKey(),
								"Followee class: " + evidence.getFollowee());
					}
				}
			}
			else if (error == null)
			{
				error = "The classifier did not produce a usable followee distribution.";
			}

			results.put(target, buildResult(
					target,
					first.getTargetMode(),
					RecommendationCandidateType.USER,
					first.getResultLabel(),
					finalScores,
					finalDetails,
					error));
		}
	}

	private static RecommendationResultPayload buildResult(
			String target,
			RecommendationTargetMode mode,
			RecommendationCandidateType candidateType,
			String resultLabel,
			Map<String,Double> rawScores,
			Map<String,String> candidateDetails,
			String error)
	{
		LinkedHashMap<String,Double> normalized = normalizeAndSort(rawScores);
		LinkedHashMap<String,String> sortedDetails = new LinkedHashMap<String,String>();
		for (String candidate : normalized.keySet())
		{
			String detail = candidateDetails == null ? null : candidateDetails.get(candidate);
			if (detail != null)
			{
				sortedDetails.put(candidate, detail);
			}
		}
		if (!normalized.isEmpty())
		{
			return new RecommendationResultPayload(
					target,
					mode,
					candidateType,
					RecommendationResultStatus.OK,
					resultLabel,
					error,
					normalized,
					sortedDetails);
		}
		return new RecommendationResultPayload(
				target,
				mode,
				candidateType,
				error == null ? RecommendationResultStatus.NO_CANDIDATES
						: RecommendationResultStatus.FAILED,
				resultLabel,
				error == null ? "No positive recommendation candidates were available." : error,
				normalized,
				sortedDetails);
	}

	private static void copyDetail(
			RecommendationScorePayload payload,
			String target,
			String candidate,
			Map<String,String> destination)
	{
		String detail = getDetail(payload, target, candidate);
		if (detail != null)
		{
			destination.put(candidate, detail);
		}
	}

	private static String getDetail(
			RecommendationScorePayload payload,
			String target,
			String candidate)
	{
		Map<String,String> targetDetails = payload.getCandidateDetails().get(target);
		return targetDetails == null ? null : targetDetails.get(candidate);
	}

	static LinkedHashMap<String,Double> normalizeAndSort(Map<String,Double> rawScores)
	{
		ArrayList<Map.Entry<String,Double>> entries =
				new ArrayList<Map.Entry<String,Double>>();
		double maxScore = 0.0;
		if (rawScores != null)
		{
			for (Map.Entry<String,Double> entry : rawScores.entrySet())
			{
				double score = finitePositive(entry.getValue());
				if (entry.getKey() != null && score > 0.0)
				{
					entries.add(new java.util.AbstractMap.SimpleImmutableEntry<String,Double>(
							entry.getKey(), score));
					maxScore = Math.max(maxScore, score);
				}
			}
		}
		if (maxScore <= 0.0)
		{
			return new LinkedHashMap<String,Double>();
		}

		final double normalizationMax = maxScore;
		Collections.sort(entries, new Comparator<Map.Entry<String,Double>>() {
			public int compare(Map.Entry<String,Double> first, Map.Entry<String,Double> second)
			{
				int scoreCompare = Double.compare(second.getValue(), first.getValue());
				return scoreCompare != 0 ? scoreCompare : first.getKey().compareTo(second.getKey());
			}
		});

		LinkedHashMap<String,Double> normalized = new LinkedHashMap<String,Double>();
		for (Map.Entry<String,Double> entry : entries)
		{
			normalized.put(entry.getKey(), (entry.getValue() / normalizationMax) * 100.0);
		}
		return normalized;
	}

	private static double finitePositive(Double value)
	{
		if (value == null || !Double.isFinite(value) || value <= 0.0)
		{
			return 0.0;
		}
		return value;
	}

	private static String combineErrors(String existing, String additional)
	{
		if (additional == null || additional.length() == 0)
		{
			return existing;
		}
		if (existing == null || existing.length() == 0)
		{
			return additional;
		}
		if (existing.contains(additional))
		{
			return existing;
		}
		return existing + " | " + additional;
	}
}
