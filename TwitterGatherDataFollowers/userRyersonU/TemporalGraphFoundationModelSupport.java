package TwitterGatherDataFollowers.userRyersonU;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

final class TemporalGraphFoundationModelSupport
{
	private static final int DATASET_SCAN_LIMIT = 2000000;
	private static final int DETAIL_TERM_LIMIT = 4;
	private static final int GRAPH_SAMPLE_LIMIT = 24;
	private static final int TIMELINE_FRAME_COUNT = 12;
	private static final double CALIBRATION_CONFIDENCE = 0.90;
	private static final double TEXT_WEIGHT = 0.34;
	private static final double GRAPH_WEIGHT = 0.22;
	private static final double TIME_WEIGHT = 0.18;
	private static final double BRIDGE_WEIGHT = 0.14;
	private static final double DEBIAS_WEIGHT = 0.07;
	private static final double ROBUST_WEIGHT = 0.05;
	private static final List<String> DATE_PATTERNS = Arrays.asList(
			"yyyy-MM-dd HH:mm:ss",
			"yyyy-MM-dd HH:mm",
			"yyyy-MM-dd",
			"EEE MMM dd HH:mm:ss Z yyyy",
			"MM/dd/yyyy",
			"yyyy/MM/dd");

	private TemporalGraphFoundationModelSupport()
	{
	}

	static Result score(Input input)
	{
		Input safe = input == null ? Input.empty() : input;
		LinkedHashMap<String,UserProfile> profiles = buildProfiles(safe);
		if (profiles.isEmpty() || safe.targetUsers.isEmpty())
		{
			return Result.empty(safe.runId, safe.datasetFile, "No usable user profiles were available.");
		}

		TemporalCorpus corpus = readTemporalCorpus(safe.datasetFile, profiles);
		applyTemporalNormalization(profiles, corpus);
		GraphContext graph = buildGraphContext(safe, profiles);

		TreeMap<String,TreeMap<String,Double>> scoreRows =
				new TreeMap<String,TreeMap<String,Double>>();
		TreeMap<String,TreeMap<String,String>> detailRows =
				new TreeMap<String,TreeMap<String,String>>();
			LinkedHashMap<String,TargetReport> reports =
					new LinkedHashMap<String,TargetReport>();
			int scoredPairs = 0;

			for (String targetUser : safe.targetUsers)
		{
			UserProfile target = profiles.get(targetUser);
			if (target == null)
			{
				continue;
			}
			ArrayList<CandidateScore> candidateScores =
					new ArrayList<CandidateScore>();
			for (UserProfile candidate : profiles.values())
			{
				if (candidate == null || target.name.equals(candidate.name)
						|| !belongsToThisNode(candidate, safe))
				{
					continue;
				}
				CandidateScore score = scoreCandidate(target, candidate, graph);
				if (score.finalScore > 0.0 && Double.isFinite(score.finalScore))
				{
					candidateScores.add(score);
					scoredPairs++;
					}
				}
				assignRanks(candidateScores);
				attachResearchEvidence(target, candidateScores, graph, corpus);
				assignTimelineRanks(candidateScores);
				Collections.sort(candidateScores, CandidateScore.FINAL_ORDER);
				int keep = safe.localResultLimit <= 0
						? candidateScores.size()
						: Math.min(safe.localResultLimit, candidateScores.size());
			TreeMap<String,Double> rowScores = new TreeMap<String,Double>();
			TreeMap<String,String> rowDetails = new TreeMap<String,String>();
			ArrayList<CandidateScore> keptScores = new ArrayList<CandidateScore>();
			for (int i = 0; i < keep; i++)
			{
				CandidateScore score = candidateScores.get(i);
				rowScores.put(score.candidate.name, score.finalScore);
				rowDetails.put(score.candidate.name, detailFor(score));
				keptScores.add(score);
			}
				scoreRows.put(target.name, rowScores);
				detailRows.put(target.name, rowDetails);
				reports.put(target.name, TargetReport.from(target, keptScores, graph, corpus));
			}

			String summary = "Temporal Graph Foundation scored " + scoredPairs
					+ " target-candidate pairs using text, graph, time, bridge, "
					+ "popularity-debias, and ablation signals.";
			ResearchSummary researchSummary =
					ResearchSummary.from(reports.values(), scoredPairs, corpus, graph);
			return new Result(
					safe.runId,
					safe.datasetFile,
					summary,
					corpus.rowsScanned,
					corpus.datedRows,
					graph.graphArtifactEdges,
					scoreRows,
					detailRows,
					reports,
					researchSummary);
		}

	private static LinkedHashMap<String,UserProfile> buildProfiles(Input input)
	{
		LinkedHashMap<String,UserProfile> profiles =
				new LinkedHashMap<String,UserProfile>();
		for (Map.Entry<String,? extends Map<String,Double>> entry
				: input.tfidfDocuments.entrySet())
		{
			if (entry.getKey() == null || entry.getValue() == null
					|| entry.getValue().isEmpty())
			{
				continue;
			}
			String user = entry.getKey();
			String followee = input.datasetUserFollowees.get(user);
			if (followee == null)
			{
				followee = input.userFollowees.get(user);
			}
			profiles.put(user, new UserProfile(user, followee, entry.getValue()));
		}
		return profiles;
	}

	private static TemporalCorpus readTemporalCorpus(
			File datasetFile,
			Map<String,UserProfile> profiles)
	{
		TemporalCorpus corpus = new TemporalCorpus();
		if (datasetFile == null || !datasetFile.isFile())
		{
			corpus.warning = "No dataset file was available for temporal row scanning.";
			return corpus;
		}
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(
				new FileInputStream(datasetFile), StandardCharsets.UTF_8), 128 * 1024))
		{
			String line;
			long lineNumber = 0L;
			while ((line = reader.readLine()) != null)
			{
				lineNumber++;
				if (lineNumber > DATASET_SCAN_LIMIT)
				{
					corpus.limitReached = true;
					break;
				}
				if (lineNumber == 1 && line.startsWith("\uFEFF"))
				{
					line = line.substring(1);
				}
				String[] fields = line.split("\t", 6);
				if (fields.length != 6)
				{
					continue;
				}
				corpus.rowsScanned++;
				String followee = fields[0].trim();
				String dateText = fields[2].trim();
				String user = fields[4].trim();
				UserProfile profile = profiles.get(user);
				if (profile == null)
				{
					continue;
				}
				if (profile.followee == null || profile.followee.length() == 0)
				{
					profile.followee = followee;
				}
				long time = parseDateMillis(dateText);
				if (time != Long.MIN_VALUE)
				{
					corpus.datedRows++;
				profile.observeTime(time);
				corpus.minTime = Math.min(corpus.minTime, time);
				corpus.maxTime = Math.max(corpus.maxTime, time);
			}
			profile.observeLine(lineNumber);
				corpus.minLine = Math.min(corpus.minLine, lineNumber);
				corpus.maxLine = Math.max(corpus.maxLine, lineNumber);
			}
		}
		catch (IOException exception)
		{
			corpus.warning = "Temporal dataset scan failed: " + exception.getMessage();
		}
		return corpus;
	}

	private static long parseDateMillis(String value)
	{
		if (value == null || value.trim().length() == 0)
		{
			return Long.MIN_VALUE;
		}
		String trimmed = value.trim();
		for (String pattern : DATE_PATTERNS)
		{
			SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.US);
			format.setLenient(false);
			ParsePosition position = new ParsePosition(0);
			Date parsed = format.parse(trimmed, position);
			if (parsed != null && position.getIndex() == trimmed.length())
			{
				return parsed.getTime();
			}
		}
		return Long.MIN_VALUE;
	}

	private static void applyTemporalNormalization(
			Map<String,UserProfile> profiles,
			TemporalCorpus corpus)
	{
		boolean hasDates = corpus.datedRows > 0 && corpus.maxTime > corpus.minTime;
		boolean hasLines = corpus.maxLine > corpus.minLine;
		for (UserProfile profile : profiles.values())
		{
			double normalized;
			if (hasDates && profile.latestTime != Long.MIN_VALUE)
			{
				normalized = (profile.latestTime - corpus.minTime)
						/ (double) Math.max(1L, corpus.maxTime - corpus.minTime);
			}
			else if (hasLines && profile.latestLine > 0L)
			{
				normalized = (profile.latestLine - corpus.minLine)
						/ (double) Math.max(1L, corpus.maxLine - corpus.minLine);
			}
				else
				{
					normalized = 0.50;
				}
				profile.recency = clamp01(0.15 + 0.85 * normalized);
				profile.firstPosition = normalizedPosition(profile, corpus, true);
				profile.latestPosition = normalizedPosition(profile, corpus, false);
			}
		}

		private static double normalizedPosition(
				UserProfile profile,
				TemporalCorpus corpus,
				boolean first)
		{
			if (corpus.datedRows > 0 && corpus.maxTime > corpus.minTime)
			{
				long value = first ? profile.firstTime : profile.latestTime;
				if (value != Long.MAX_VALUE && value != Long.MIN_VALUE)
				{
					return clamp01((value - corpus.minTime)
							/ (double)Math.max(1L, corpus.maxTime - corpus.minTime));
				}
			}
			if (corpus.maxLine > corpus.minLine)
			{
				long value = first ? profile.firstLine : profile.latestLine;
				if (value != Long.MAX_VALUE && value > 0L)
				{
					return clamp01((value - corpus.minLine)
							/ (double)Math.max(1L, corpus.maxLine - corpus.minLine));
				}
			}
			return first ? 0.0 : 0.50;
		}

	private static GraphContext buildGraphContext(
			Input input,
			Map<String,UserProfile> profiles)
	{
		GraphContext graph = new GraphContext();
		for (UserProfile profile : profiles.values())
		{
			String followee = safe(profile.followee);
			if (followee.length() == 0)
			{
				continue;
			}
			LinkedHashSet<String> users = graph.usersByFollowee.get(followee);
			if (users == null)
			{
				users = new LinkedHashSet<String>();
				graph.usersByFollowee.put(followee, users);
			}
			users.add(profile.name);
		}
		graph.maxFolloweeSize = 1;
		for (Set<String> users : graph.usersByFollowee.values())
		{
			graph.maxFolloweeSize = Math.max(graph.maxFolloweeSize, users.size());
		}
		readGraphArtifacts(input.datasetFile, profiles, graph);
		return graph;
	}

	private static void readGraphArtifacts(
			File datasetFile,
			Map<String,UserProfile> profiles,
			GraphContext graph)
	{
		if (datasetFile == null)
		{
			return;
		}
		File importantStuff = new File("important-stuff");
		File mappingFile = GraphAnalysisEngine.mappingFile(
				importantStuff,
				datasetFile.getName());
		if (!mappingFile.isFile())
		{
			return;
		}
		Map<Integer,String> namesById = readMapping(mappingFile);
		if (namesById.isEmpty())
		{
			return;
		}
		String base = GraphAnalysisEngine.graphEdgesBaseName(
				datasetFile.getName(),
				"");
		File[] files = importantStuff.listFiles();
		if (files == null)
		{
			return;
		}
		for (File file : files)
		{
			if (file == null || !file.isFile()
					|| !file.getName().startsWith(base)
					|| !file.getName().endsWith(".txt"))
			{
				continue;
			}
			readEdgeFile(file, namesById, profiles, graph);
		}
	}

	private static Map<Integer,String> readMapping(File mappingFile)
	{
		HashMap<Integer,String> namesById = new HashMap<Integer,String>();
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(
				new FileInputStream(mappingFile), StandardCharsets.UTF_8), 64 * 1024))
		{
			String line;
			while ((line = reader.readLine()) != null)
			{
				String[] parts = line.trim().split("\\s+");
				if (parts.length != 2)
				{
					continue;
				}
				recordMapping(namesById, parts[0], parts[1]);
			}
		}
		catch (IOException ignored)
		{
		}
		return namesById;
	}

	private static void recordMapping(
			Map<Integer,String> namesById,
			String first,
			String second)
	{
		try
		{
			namesById.put(Integer.valueOf(Integer.parseInt(first)), second);
			return;
		}
		catch (NumberFormatException ignored)
		{
		}
		try
		{
			namesById.put(Integer.valueOf(Integer.parseInt(second)), first);
		}
		catch (NumberFormatException ignored)
		{
		}
	}

	private static void readEdgeFile(
			File edgeFile,
			Map<Integer,String> namesById,
			Map<String,UserProfile> profiles,
			GraphContext graph)
	{
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(
				new FileInputStream(edgeFile), StandardCharsets.UTF_8), 64 * 1024))
		{
			String line;
			int sampled = 0;
			while ((line = reader.readLine()) != null)
			{
				String[] parts = line.trim().split("\\s+");
				if (parts.length < 2)
				{
					continue;
				}
				try
				{
					String source = namesById.get(Integer.valueOf(Integer.parseInt(parts[0])));
					String target = namesById.get(Integer.valueOf(Integer.parseInt(parts[1])));
					if (source == null || target == null
							|| !profiles.containsKey(source)
							|| !profiles.containsKey(target)
							|| source.equals(target))
					{
						continue;
					}
					graph.connect(source, target);
					graph.graphArtifactEdges++;
					if (sampled < GRAPH_SAMPLE_LIMIT)
					{
						graph.sampleEdges.add(source + " -> " + target);
						sampled++;
					}
				}
				catch (NumberFormatException ignored)
				{
				}
			}
		}
		catch (IOException ignored)
		{
		}
	}

	private static boolean belongsToThisNode(UserProfile candidate, Input input)
	{
		if (input.numRecAgents <= 1 || input.currentNode <= 0)
		{
			return true;
		}
		Integer primary = input.userPrimaryRecServer.get(candidate.name);
		return primary == null || primary.intValue() == input.currentNode;
	}

	private static CandidateScore scoreCandidate(
			UserProfile target,
			UserProfile candidate,
			GraphContext graph)
	{
		double text = cosine(target.tfidf, candidate.tfidf);
		GraphSignal graphSignal = graphSignal(target, candidate, graph);
		double time = temporalAffinity(target, candidate);
		double bridge = bridgeValue(target, candidate, graph, text, graphSignal.value);
			double debias = popularityDebias(candidate, graph);
			double robust = robustness(text, graphSignal.value, time, bridge);
			double finalScore = fusedScore(
					text,
					graphSignal.value,
					time,
					bridge,
					debias,
					robust);
			if (text <= 0.0 && graphSignal.value <= 0.0)
			{
				finalScore *= 0.35;
			}
			CandidateScore score = new CandidateScore(target, candidate);
				score.text = text;
				score.graph = graphSignal.value;
				score.time = time;
				score.bridge = bridge;
				score.debias = debias;
				score.robust = robust;
				score.finalScore = finalScore;
				score.textContribution = TEXT_WEIGHT * text;
				score.graphContribution = GRAPH_WEIGHT * graphSignal.value;
				score.timeContribution = TIME_WEIGHT * time;
				score.bridgeContribution = BRIDGE_WEIGHT * bridge;
				score.debiasContribution = DEBIAS_WEIGHT * debias;
				score.robustContribution = ROBUST_WEIGHT * robust;
				score.path = graphSignal.path;
				score.sharedTerms = sharedTerms(target.tfidf, candidate.tfidf, DETAIL_TERM_LIMIT);
			score.withoutTextScore = finalScore - TEXT_WEIGHT * text;
			score.withoutGraphScore = finalScore - GRAPH_WEIGHT * graphSignal.value;
			score.withoutTimeScore = finalScore - TIME_WEIGHT * time;
			score.withoutBridgeScore = fusedScore(
					text,
					graphSignal.value,
					time,
					0.0,
					debias,
					robustness(text, graphSignal.value, time, 0.0));
			score.withoutDebiasScore = fusedScore(
					text,
					graphSignal.value,
					time,
					bridge,
					0.0,
					robust);
			score.withoutTopTermText = textWithoutTopTerm(target, candidate, score);
			double bridgeWithoutTerm = bridgeValue(
					target,
					candidate,
					graph,
					score.withoutTopTermText,
					graphSignal.value);
			score.withoutTopTermScore = fusedScore(
					score.withoutTopTermText,
					graphSignal.value,
					time,
					bridgeWithoutTerm,
					debias,
					robustness(
							score.withoutTopTermText,
							graphSignal.value,
							time,
							bridgeWithoutTerm));
			return score;
		}

	static String weightsDescription()
	{
		return "text=" + fmt(TEXT_WEIGHT)
				+ ", graph=" + fmt(GRAPH_WEIGHT)
				+ ", time=" + fmt(TIME_WEIGHT)
				+ ", bridge=" + fmt(BRIDGE_WEIGHT)
				+ ", debias=" + fmt(DEBIAS_WEIGHT)
				+ ", robust=" + fmt(ROBUST_WEIGHT);
	}

	static String methodologySummary()
	{
		return "TGF is an evidence-fusion recommender, not a pretrained graph "
				+ "foundation model. It combines text similarity, graph evidence, "
				+ "temporal alignment, bridge agreement, popularity debiasing, and "
				+ "robustness checks, then reports counterfactual probes, "
				+ "time-split calibration diagnostics, and baseline-lens comparisons.";
	}

		private static double fusedScore(
				double text,
				double graph,
				double time,
				double bridge,
				double debias,
				double robust)
		{
			return TEXT_WEIGHT * text
					+ GRAPH_WEIGHT * graph
					+ TIME_WEIGHT * time
					+ BRIDGE_WEIGHT * bridge
					+ DEBIAS_WEIGHT * debias
					+ ROBUST_WEIGHT * robust;
		}

		private static double textWithoutTopTerm(
				UserProfile target,
				UserProfile candidate,
				CandidateScore score)
		{
			if (score.sharedTerms == null || score.sharedTerms.isEmpty())
			{
				return score.text;
			}
			return cosineExcluding(target.tfidf, candidate.tfidf, score.sharedTerms.get(0));
		}

	private static GraphSignal graphSignal(
			UserProfile target,
			UserProfile candidate,
			GraphContext graph)
	{
		double value = 0.0;
		String path = "No graph path found";
		if (sameLabel(target, candidate))
		{
			value = Math.max(value, 0.72);
			path = target.name + " -> " + safe(target.followee) + " -> "
					+ candidate.name;
		}
		if (graph.areConnected(target.name, candidate.name))
		{
			value = Math.max(value, 0.95);
			path = target.name + " -> prior graph edge -> " + candidate.name;
		}
		int common = graph.commonNeighborCount(target.name, candidate.name);
		if (common > 0)
		{
			double commonScore = Math.min(1.0, Math.log(1.0 + common) / Math.log(6.0));
			if (commonScore > value)
			{
				value = commonScore;
				path = target.name + " -> " + common + " shared graph neighbor"
						+ (common == 1 ? "" : "s") + " -> " + candidate.name;
			}
		}
		return new GraphSignal(clamp01(value), path);
	}

	private static double temporalAffinity(UserProfile target, UserProfile candidate)
	{
		double alignment = 1.0 - Math.abs(target.recency - candidate.recency);
		return clamp01(0.58 * candidate.recency + 0.42 * alignment);
	}

	private static double bridgeValue(
			UserProfile target,
			UserProfile candidate,
			GraphContext graph,
			double text,
			double graphScore)
	{
		if (sameLabel(target, candidate))
		{
			return clamp01(0.15 + 0.35 * graphScore);
		}
		double candidateLongTail = popularityDebias(candidate, graph);
		double targetCommunity = followeeSize(target, graph);
		double candidateCommunity = followeeSize(candidate, graph);
		double sizeBalance = targetCommunity <= 0.0 || candidateCommunity <= 0.0
				? 0.50
				: 1.0 - Math.abs(targetCommunity - candidateCommunity)
						/ Math.max(targetCommunity, candidateCommunity);
		return clamp01(0.45 * text
				+ 0.25 * graphScore
				+ 0.20 * candidateLongTail
				+ 0.10 * sizeBalance);
	}

	private static double popularityDebias(UserProfile candidate, GraphContext graph)
	{
		double size = followeeSize(candidate, graph);
		if (size <= 0.0)
		{
			return 0.50;
		}
		return clamp01(1.0 - (size / Math.max(1.0, graph.maxFolloweeSize)));
	}

	private static double followeeSize(UserProfile profile, GraphContext graph)
	{
		Set<String> users = graph.usersByFollowee.get(safe(profile.followee));
		return users == null ? 0.0 : users.size();
	}

	private static double robustness(
			double text,
			double graph,
			double time,
			double bridge)
	{
		double sum = text + graph + time + bridge;
		if (sum <= 0.0)
		{
			return 0.0;
		}
		double max = Math.max(Math.max(text, graph), Math.max(time, bridge));
		double balance = 1.0 - (max / sum);
		return clamp01(balance * 1.35);
	}

	private static void assignRanks(List<CandidateScore> scores)
	{
		assignRank(scores, CandidateScore.FINAL_ORDER, RankType.FINAL);
		assignRank(scores, CandidateScore.WITHOUT_TEXT_ORDER, RankType.WITHOUT_TEXT);
		assignRank(scores, CandidateScore.WITHOUT_GRAPH_ORDER, RankType.WITHOUT_GRAPH);
		assignRank(scores, CandidateScore.WITHOUT_TIME_ORDER, RankType.WITHOUT_TIME);
		assignRank(scores, CandidateScore.WITHOUT_BRIDGE_ORDER, RankType.WITHOUT_BRIDGE);
		assignRank(scores, CandidateScore.WITHOUT_DEBIAS_ORDER, RankType.WITHOUT_DEBIAS);
		assignRank(scores, CandidateScore.WITHOUT_TOP_TERM_ORDER, RankType.WITHOUT_TOP_TERM);
	}

	private static void assignRank(
			List<CandidateScore> scores,
			Comparator<CandidateScore> comparator,
			RankType type)
	{
		ArrayList<CandidateScore> sorted = new ArrayList<CandidateScore>(scores);
		Collections.sort(sorted, comparator);
		for (int i = 0; i < sorted.size(); i++)
		{
			CandidateScore score = sorted.get(i);
			int rank = i + 1;
			if (type == RankType.FINAL)
			{
				score.rank = rank;
			}
			else if (type == RankType.WITHOUT_TEXT)
			{
				score.withoutTextRank = rank;
			}
			else if (type == RankType.WITHOUT_GRAPH)
			{
				score.withoutGraphRank = rank;
			}
			else if (type == RankType.WITHOUT_TIME)
			{
				score.withoutTimeRank = rank;
			}
			else if (type == RankType.WITHOUT_BRIDGE)
			{
				score.withoutBridgeRank = rank;
			}
			else if (type == RankType.WITHOUT_DEBIAS)
			{
				score.withoutDebiasRank = rank;
			}
			else if (type == RankType.WITHOUT_TOP_TERM)
			{
				score.withoutTopTermRank = rank;
			}
		}
	}

	private static void assignTimelineRanks(List<CandidateScore> scores)
	{
		if (scores == null || scores.isEmpty())
		{
			return;
		}
		for (int frameIndex = 0; frameIndex < TIMELINE_FRAME_COUNT; frameIndex++)
		{
			ArrayList<CandidateScore> frameScores =
					new ArrayList<CandidateScore>(scores);
			final int index = frameIndex;
			Collections.sort(frameScores, new Comparator<CandidateScore>() {
				public int compare(CandidateScore first, CandidateScore second)
				{
					return CandidateScore.compareScore(
							first,
							second,
							frameScore(first, index),
							frameScore(second, index));
				}
			});
			for (int i = 0; i < frameScores.size(); i++)
			{
				CandidateScore score = frameScores.get(i);
				if (score.timeline == null || score.timeline.size() <= frameIndex)
				{
					continue;
				}
				TimelineFrame frame = score.timeline.get(frameIndex);
				frame.rank = i + 1;
				frame.previousRank = frameIndex == 0
						? frame.rank
						: previousFrameRank(score, frameIndex);
			}
		}
	}

	private static double frameScore(CandidateScore score, int frameIndex)
	{
		if (score == null || score.timeline == null
				|| score.timeline.size() <= frameIndex)
		{
			return 0.0;
		}
		return score.timeline.get(frameIndex).score;
	}

	private static int previousFrameRank(CandidateScore score, int frameIndex)
	{
		if (score == null || score.timeline == null || frameIndex <= 0
				|| score.timeline.size() < frameIndex)
		{
			return 0;
		}
		return score.timeline.get(frameIndex - 1).rank;
	}

	private static void attachResearchEvidence(
			UserProfile target,
			List<CandidateScore> scores,
			GraphContext graph,
			TemporalCorpus corpus)
	{
		if (scores == null || scores.isEmpty())
		{
			return;
		}
		ArrayList<CandidateScore> sorted = new ArrayList<CandidateScore>(scores);
		Collections.sort(sorted, CandidateScore.FINAL_ORDER);
		for (int i = 0; i < sorted.size(); i++)
		{
			CandidateScore score = sorted.get(i);
			double nextScore = i + 1 < sorted.size()
					? sorted.get(i + 1).finalScore : 0.0;
			score.marginToNext = Math.max(0.0, score.finalScore - nextScore);
			score.confidenceRadius = confidenceRadius(score);
			score.confidenceLower = clamp01(score.finalScore - score.confidenceRadius);
			score.confidenceUpper = clamp01(score.finalScore + score.confidenceRadius);
				score.stability = stability(score);
				score.timeline = buildTimeline(target, score, graph, corpus);
				score.counterfactuals = buildCounterfactuals(score);
				score.counterfactualFragility = counterfactualFragility(score);
				score.evidenceCoverage = evidenceCoverage(score);
				attachChallenger(score, i == 0
						? (sorted.size() > 1 ? sorted.get(1) : null)
						: sorted.get(0));
				score.evidenceAtoms = buildEvidenceAtoms(score);
			}
			double calibrationThreshold = calibrationThreshold(sorted);
			for (CandidateScore score : sorted)
			{
				attachCalibration(score, sorted, calibrationThreshold);
				score.riskFlags = buildRiskFlags(score, corpus, graph);
				score.reliabilityBand = reliabilityBand(score);
				score.whyThis = whyThis(score);
				score.whyNotChallenger = whyNotChallenger(score);
				score.researchVerdict = verdict(score);
			}
		}

		private static void attachCalibration(
				CandidateScore score,
				List<CandidateScore> calibrationSet,
				double threshold)
		{
			if (score == null)
			{
				return;
			}
			double residual = calibrationResidual(score);
			int denominator = calibrationSet == null ? 0 : calibrationSet.size();
			int atLeastAsWide = 0;
			if (calibrationSet != null)
			{
				for (CandidateScore candidate : calibrationSet)
				{
					if (calibrationResidual(candidate) >= residual)
					{
						atLeastAsWide++;
					}
				}
			}
			score.calibrationResidual = residual;
			score.calibrationThreshold = threshold;
			score.calibrationPValue = denominator <= 0
					? 0.0
					: clamp01((atLeastAsWide + 1.0) / (denominator + 1.0));
			score.calibratedConfidence = clamp01(1.0 - residual
					/ Math.max(0.001, threshold + 0.001));
			boolean covered = residual <= threshold + 1.0e-9;
			if (covered && score.calibrationPValue >= 0.30
					&& score.confidenceLower >= 0.25)
			{
				score.calibrationVerdict =
						Integer.toString((int)Math.round(CALIBRATION_CONFIDENCE * 100.0))
						+ "% stable";
			}
			else if (covered)
			{
				score.calibrationVerdict =
						Integer.toString((int)Math.round(CALIBRATION_CONFIDENCE * 100.0))
						+ "% covered, review";
			}
			else
			{
				score.calibrationVerdict = "abstain: outside calibration band";
			}
			score.calibrationNote = "time-split residual " + fmt(residual)
					+ " vs q" + Integer.toString(
							(int)Math.round(CALIBRATION_CONFIDENCE * 100.0))
					+ " " + fmt(threshold)
					+ ", p~" + fmt(score.calibrationPValue);
		}

		private static double calibrationThreshold(List<CandidateScore> scores)
		{
			if (scores == null || scores.isEmpty())
			{
				return 0.0;
			}
			ArrayList<Double> residuals = new ArrayList<Double>();
			for (CandidateScore score : scores)
			{
				residuals.add(Double.valueOf(calibrationResidual(score)));
			}
			Collections.sort(residuals);
			int index = (int)Math.ceil((residuals.size() + 1)
					* CALIBRATION_CONFIDENCE) - 1;
			index = Math.max(0, Math.min(residuals.size() - 1, index));
			return residuals.get(index).doubleValue();
		}

		private static double calibrationResidual(CandidateScore score)
		{
			if (score == null || score.timeline == null || score.timeline.isEmpty())
			{
				return 0.0;
			}
			int mid = Math.max(0, Math.min(score.timeline.size() - 1,
					score.timeline.size() / 2));
			double midScore = score.timeline.get(mid).score;
			return clamp01(Math.abs(score.finalScore - midScore));
		}

		private static void attachChallenger(
				CandidateScore score,
				CandidateScore challenger)
		{
			if (score == null || challenger == null)
			{
				return;
			}
			score.challengerName = challenger.candidate.name;
			score.challengerFollowee = challenger.candidate.followee;
			score.challengerScore = challenger.finalScore;
			score.challengerText = challenger.text;
			score.challengerGraph = challenger.graph;
			score.challengerTime = challenger.time;
			score.challengerBridge = challenger.bridge;
			score.challengerDebias = challenger.debias;
			score.challengerRobust = challenger.robust;
			score.challengerGap = score.finalScore - challenger.finalScore;
			score.bestContrastSignal = bestContrastSignal(score, challenger);
		}

		private static String bestContrastSignal(
				CandidateScore score,
				CandidateScore challenger)
		{
			double[] deltas = {
					score.textContribution - challenger.textContribution,
					score.graphContribution - challenger.graphContribution,
					score.timeContribution - challenger.timeContribution,
					score.bridgeContribution - challenger.bridgeContribution,
					score.debiasContribution - challenger.debiasContribution,
					score.robustContribution - challenger.robustContribution
			};
			String[] labels = {"text", "graph", "time", "bridge", "debias", "robust"};
			int best = 0;
			for (int i = 1; i < deltas.length; i++)
			{
				if (Math.abs(deltas[i]) > Math.abs(deltas[best]))
				{
					best = i;
				}
			}
			return labels[best] + " "
					+ (deltas[best] >= 0.0 ? "+" : "")
					+ String.format(Locale.US, "%.3f", deltas[best]);
		}

		private static double counterfactualFragility(CandidateScore score)
		{
			if (score == null || score.counterfactuals == null
					|| score.counterfactuals.isEmpty() || score.finalScore <= 0.0)
			{
				return 0.0;
			}
			double worst = 0.0;
			for (CounterfactualProbe probe : score.counterfactuals)
			{
				double drop = probe.scoreDrop / Math.max(0.001, score.finalScore);
				double shift = Math.min(1.0, probe.rankShift / 6.0);
				worst = Math.max(worst, 0.74 * drop + 0.26 * shift);
			}
			return clamp01(worst);
		}

		private static double evidenceCoverage(CandidateScore score)
		{
			if (score == null)
			{
				return 0.0;
			}
			double present = 0.0;
			present += score.text > 0.04 ? 1.0 : score.text * 12.5;
			present += score.graph > 0.04 ? 1.0 : score.graph * 12.5;
			present += score.time > 0.20 ? 1.0 : score.time * 2.5;
			present += score.bridge > 0.04 ? 1.0 : score.bridge * 12.5;
			present += score.debias > 0.20 ? 1.0 : score.debias * 2.5;
			present += score.robust > 0.20 ? 1.0 : score.robust * 2.5;
			return clamp01(present / 6.0);
		}

		private static List<EvidenceAtom> buildEvidenceAtoms(CandidateScore score)
		{
			ArrayList<EvidenceAtom> atoms = new ArrayList<EvidenceAtom>();
			atoms.add(new EvidenceAtom(
					"Text overlap",
					score.text,
					score.textContribution,
					"shared terms: " + joinTerms(score.sharedTerms)));
			atoms.add(new EvidenceAtom(
					"Graph path",
					score.graph,
					score.graphContribution,
					score.path));
			atoms.add(new EvidenceAtom(
					"Temporal alignment",
					score.time,
					score.timeContribution,
					"target/candidate evidence is synchronized in the replay"));
			atoms.add(new EvidenceAtom(
					"Bridge",
					score.bridge,
					score.bridgeContribution,
					"text, graph, and community-size evidence agree"));
			atoms.add(new EvidenceAtom(
					"Debias",
					score.debias,
					score.debiasContribution,
					"discounts popularity-dominated followee communities"));
			atoms.add(new EvidenceAtom(
					"Robustness",
					score.robust,
					score.robustContribution,
					"checks whether one signal is carrying the whole decision"));
			return Collections.unmodifiableList(atoms);
		}

		private static List<String> buildRiskFlags(
				CandidateScore score,
				TemporalCorpus corpus,
				GraphContext graph)
		{
			ArrayList<String> flags = new ArrayList<String>();
			if (score.marginToNext < 0.025 && score.rank == 1)
			{
				flags.add("thin top-2 score margin");
			}
			if (score.confidenceRadius > 0.085)
			{
				flags.add("wide confidence interval");
			}
			if (score.graph <= 0.04)
			{
				flags.add("no usable graph path");
			}
			if (score.robust < 0.34)
			{
				flags.add("single-signal heavy");
			}
			if (score.stability < 0.56)
			{
				flags.add("rank unstable under ablation");
			}
			if (score.counterfactualFragility > 0.52)
			{
				flags.add("high counterfactual fragility");
			}
			if (score.calibrationVerdict != null
					&& score.calibrationVerdict.startsWith("abstain"))
			{
				flags.add("outside time-split calibration band");
			}
			if (corpus == null || corpus.datedRows <= 0)
			{
				flags.add("date coverage unavailable");
			}
			if (graph == null || graph.graphArtifactEdges <= 0)
			{
				flags.add("graph artifact edge file is sparse/empty");
			}
			if (flags.isEmpty())
			{
				flags.add("no critical evidence-limit flags");
			}
			return Collections.unmodifiableList(flags);
		}

		private static String reliabilityBand(CandidateScore score)
		{
			if (score.stability >= 0.78 && score.counterfactualFragility <= 0.34
					&& score.confidenceRadius <= 0.075)
			{
				return "strong evidence";
			}
			if (score.stability >= 0.58 && score.counterfactualFragility <= 0.55)
			{
				return "reviewable";
			}
			return "fragile";
		}

		private static String whyThis(CandidateScore score)
		{
			EvidenceAtom strongest = strongestAtom(score.evidenceAtoms);
			String lead = strongest == null ? "multi-signal evidence"
					: strongest.label.toLowerCase(Locale.US);
			return "Rank " + rankText(score.rank) + " is driven most by " + lead
					+ "; weighted score " + String.format(Locale.US, "%.4f", score.finalScore)
					+ ", evidence coverage " + pct(score.evidenceCoverage)
					+ ", stability " + pct(score.stability) + ".";
		}

		private static String whyNotChallenger(CandidateScore score)
		{
			if (score.challengerName == null || score.challengerName.length() == 0)
			{
				return "No close challenger was retained for contrast.";
			}
			String direction = score.challengerGap >= 0.0 ? "leads" : "trails";
			return score.candidate.name + " " + direction + " "
					+ score.challengerName + " by "
					+ String.format(Locale.US, "%.4f", Math.abs(score.challengerGap))
					+ "; largest contrast: " + score.bestContrastSignal + ".";
		}

		private static EvidenceAtom strongestAtom(List<EvidenceAtom> atoms)
		{
			if (atoms == null || atoms.isEmpty())
			{
				return null;
			}
			EvidenceAtom best = atoms.get(0);
			for (EvidenceAtom atom : atoms)
			{
				if (atom.contribution > best.contribution)
				{
					best = atom;
				}
			}
			return best;
		}

	private static double confidenceRadius(CandidateScore score)
	{
		double evidenceBalance = score.robust;
		double temporalRisk = 1.0 - score.time;
		double marginCredit = Math.min(0.05, score.marginToNext);
		return clamp01(0.035 + 0.075 * (1.0 - evidenceBalance)
				+ 0.035 * temporalRisk - marginCredit);
	}

	private static double stability(CandidateScore score)
	{
		double worstRankShift = Math.max(
				Math.max(rankShift(score.rank, score.withoutTextRank),
						rankShift(score.rank, score.withoutGraphRank)),
				Math.max(rankShift(score.rank, score.withoutBridgeRank),
						rankShift(score.rank, score.withoutTopTermRank)));
		double rankPenalty = Math.min(0.65, worstRankShift / 10.0);
		double intervalPenalty = Math.min(0.25, score.confidenceRadius * 2.0);
		return clamp01(1.0 - rankPenalty - intervalPenalty);
	}

	private static int rankShift(int original, int alternative)
	{
		if (original <= 0 || alternative <= 0)
		{
			return 0;
		}
		return Math.abs(alternative - original);
	}

	private static List<TimelineFrame> buildTimeline(
			UserProfile target,
			CandidateScore score,
			GraphContext graph,
			TemporalCorpus corpus)
	{
		ArrayList<TimelineFrame> frames = new ArrayList<TimelineFrame>();
		double previousScore = 0.0;
		double graphArrival = graph.areConnected(target.name, score.candidate.name)
				? Math.min(target.latestPosition, score.candidate.latestPosition)
				: Math.max(target.latestPosition, score.candidate.latestPosition);
		for (int i = 0; i < TIMELINE_FRAME_COUNT; i++)
		{
			double progress = TIMELINE_FRAME_COUNT <= 1 ? 1.0
					: i / (double)(TIMELINE_FRAME_COUNT - 1);
			double targetReady = profileReadiness(target, progress);
			double candidateReady = profileReadiness(score.candidate, progress);
			double pairReady = Math.sqrt(targetReady * candidateReady);
			double graphReady = progress < graphArrival
					? score.graph * 0.24 * pairReady
					: score.graph * Math.min(1.0, 0.44 + pairReady);
			double textReady = score.text * pairReady;
			double timeReady = clamp01(score.time
					* (0.35 + 0.65 * candidateReady)
					* (0.72 + 0.28 * targetReady));
			double bridgeReady = score.bridge * Math.min(1.0,
					0.30 + 0.42 * pairReady + 0.28 * graphReady);
			double debiasReady = score.debias * (0.50 + 0.50 * candidateReady);
			double robustReady = robustness(textReady, graphReady, timeReady, bridgeReady);
			double frameScore = fusedScore(
					textReady,
					graphReady,
					timeReady,
					bridgeReady,
					debiasReady,
					robustReady);
			String label = progressLabel(progress, corpus);
			String dominantSignal = dominantSignal(
					textReady,
					graphReady,
					timeReady,
					bridgeReady,
					debiasReady,
					robustReady);
			String evidence = frameEvidence(
					progress,
					target,
					score,
					graphReady,
					textReady);
			String auditNote = dominantSignal + " led this frame; score delta "
					+ signedFmt(i == 0 ? frameScore : frameScore - previousScore)
					+ ".";
			frames.add(new TimelineFrame(
					i,
					progress,
					label,
					frameScore,
					textReady,
					graphReady,
					timeReady,
					bridgeReady,
					debiasReady,
					robustReady,
					evidence,
					i == 0 ? frameScore : frameScore - previousScore,
					dominantSignal,
					auditNote));
			previousScore = frameScore;
		}
		return Collections.unmodifiableList(frames);
	}

	private static String dominantSignal(
			double text,
			double graph,
			double time,
			double bridge,
			double debias,
			double robust)
	{
		double[] values = {
				TEXT_WEIGHT * text,
				GRAPH_WEIGHT * graph,
				TIME_WEIGHT * time,
				BRIDGE_WEIGHT * bridge,
				DEBIAS_WEIGHT * debias,
				ROBUST_WEIGHT * robust
		};
		String[] labels = {"text", "graph", "time", "bridge", "debias", "robust"};
		int best = 0;
		for (int i = 1; i < values.length; i++)
		{
			if (values[i] > values[best])
			{
				best = i;
			}
		}
		return labels[best];
	}

	private static double profileReadiness(UserProfile profile, double progress)
	{
		if (profile.latestPosition <= profile.firstPosition)
		{
			return progress >= profile.latestPosition ? 1.0 : 0.0;
		}
		return clamp01((progress - profile.firstPosition)
				/ Math.max(0.001, profile.latestPosition - profile.firstPosition));
	}

	private static String progressLabel(double progress, TemporalCorpus corpus)
	{
		if (corpus.datedRows > 0 && corpus.maxTime > corpus.minTime)
		{
			long time = corpus.minTime
					+ Math.round(progress * (corpus.maxTime - corpus.minTime));
			return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(time));
		}
		if (corpus.maxLine > corpus.minLine)
		{
			long line = corpus.minLine
					+ Math.round(progress * (corpus.maxLine - corpus.minLine));
			return "line " + line;
		}
		return Integer.toString((int)Math.round(progress * 100.0)) + "%";
	}

	private static String frameEvidence(
			double progress,
			UserProfile target,
			CandidateScore score,
			double graphReady,
			double textReady)
	{
		if (progress < Math.min(target.firstPosition, score.candidate.firstPosition))
		{
			return "before paired evidence appears";
		}
		if (textReady >= graphReady && textReady > 0.0)
		{
			return "text evidence leads with " + joinTerms(score.sharedTerms);
		}
		if (graphReady > 0.0)
		{
			return score.path;
		}
		return "temporal activity is warming up";
	}

	private static List<CounterfactualProbe> buildCounterfactuals(
			CandidateScore score)
	{
		ArrayList<CounterfactualProbe> probes =
				new ArrayList<CounterfactualProbe>();
		probes.add(CounterfactualProbe.from(
				"Remove text",
				score.withoutTextScore,
				score.withoutTextRank,
				score.finalScore,
				score.rank));
		probes.add(CounterfactualProbe.from(
				"Remove graph",
				score.withoutGraphScore,
				score.withoutGraphRank,
				score.finalScore,
				score.rank));
		probes.add(CounterfactualProbe.from(
				"Delay time",
				score.withoutTimeScore,
				score.withoutTimeRank,
				score.finalScore,
				score.rank));
		probes.add(CounterfactualProbe.from(
				"Remove bridge",
				score.withoutBridgeScore,
				score.withoutBridgeRank,
				score.finalScore,
				score.rank));
		probes.add(CounterfactualProbe.from(
				"Remove debias",
				score.withoutDebiasScore,
				score.withoutDebiasRank,
				score.finalScore,
				score.rank));
		String term = score.sharedTerms == null || score.sharedTerms.isEmpty()
				? "Remove top term" : "Remove '" + score.sharedTerms.get(0) + "'";
		probes.add(CounterfactualProbe.from(
				term,
				score.withoutTopTermScore,
				score.withoutTopTermRank,
				score.finalScore,
				score.rank));
		return Collections.unmodifiableList(probes);
	}

	private static String verdict(CandidateScore score)
	{
			if (score.stability >= 0.78 && score.confidenceLower >= 0.30)
			{
				return score.reliabilityBand + " multi-signal recommendation";
			}
			if (score.withoutGraphRank > score.rank + 2)
			{
				return "graph-sensitive recommendation";
		}
		if (score.withoutTextRank > score.rank + 2)
		{
			return "text-sensitive recommendation";
		}
		if (score.withoutTimeRank > score.rank + 2)
		{
			return "time-sensitive recommendation";
		}
		return "inspect counterfactual probes";
	}

	private static String detailFor(CandidateScore score)
	{
		return "TGF text " + pct(score.text)
				+ ", graph " + pct(score.graph)
				+ ", time " + pct(score.time)
				+ ", bridge " + pct(score.bridge)
				+ "; " + score.path
				+ "; no-text rank " + rankText(score.withoutTextRank)
				+ ", no-graph rank " + rankText(score.withoutGraphRank)
				+ "; " + score.calibrationVerdict
				+ "; shared " + joinTerms(score.sharedTerms);
	}

	private static String rankText(int rank)
	{
		return rank <= 0 ? "n/a" : "#" + rank;
	}

	private static String pct(double value)
	{
		return Integer.toString((int) Math.round(clamp01(value) * 100.0)) + "%";
	}

	private static String fmt(double value)
	{
		return String.format(Locale.US, "%.3f", Double.isFinite(value) ? value : 0.0);
	}

	private static String signedFmt(double value)
	{
		double safe = Double.isFinite(value) ? value : 0.0;
		return (safe >= 0.0 ? "+" : "") + String.format(Locale.US, "%.3f", safe);
	}

	private static String joinTerms(List<String> terms)
	{
		if (terms == null || terms.isEmpty())
		{
			return "no top shared terms";
		}
		StringBuilder builder = new StringBuilder();
		for (int i = 0; i < terms.size(); i++)
		{
			if (i > 0)
			{
				builder.append(", ");
			}
			builder.append(terms.get(i));
		}
		return builder.toString();
	}

	private static boolean sameLabel(UserProfile first, UserProfile second)
	{
		String a = safe(first.followee);
		String b = safe(second.followee);
		return a.length() > 0 && a.equals(b);
	}

	private static double cosine(Map<String,Double> first, Map<String,Double> second)
	{
		if (first == null || second == null || first.isEmpty() || second.isEmpty())
		{
			return 0.0;
		}
		Map<String,Double> smaller = first.size() <= second.size() ? first : second;
		Map<String,Double> larger = smaller == first ? second : first;
		double dot = 0.0;
		for (Map.Entry<String,Double> entry : smaller.entrySet())
		{
			Double other = larger.get(entry.getKey());
			if (other != null)
			{
				dot += finite(entry.getValue()) * finite(other);
			}
		}
		double norm = vectorNorm(first) * vectorNorm(second);
		return norm <= 0.0 ? 0.0 : clamp01(dot / norm);
	}

	private static double cosineExcluding(
			Map<String,Double> first,
			Map<String,Double> second,
			String excludedTerm)
	{
		if (first == null || second == null || first.isEmpty() || second.isEmpty())
		{
			return 0.0;
		}
		Map<String,Double> smaller = first.size() <= second.size() ? first : second;
		Map<String,Double> larger = smaller == first ? second : first;
		double dot = 0.0;
		for (Map.Entry<String,Double> entry : smaller.entrySet())
		{
			if (entry.getKey().equals(excludedTerm))
			{
				continue;
			}
			Double other = larger.get(entry.getKey());
			if (other != null)
			{
				dot += finite(entry.getValue()) * finite(other);
			}
		}
		double norm = vectorNormExcluding(first, excludedTerm)
				* vectorNormExcluding(second, excludedTerm);
		return norm <= 0.0 ? 0.0 : clamp01(dot / norm);
	}

	private static double vectorNorm(Map<String,Double> vector)
	{
		double total = 0.0;
		for (Double value : vector.values())
		{
			double safe = finite(value);
			total += safe * safe;
		}
		return Math.sqrt(total);
	}

	private static double vectorNormExcluding(
			Map<String,Double> vector,
			String excludedTerm)
	{
		double total = 0.0;
		for (Map.Entry<String,Double> entry : vector.entrySet())
		{
			if (entry.getKey().equals(excludedTerm))
			{
				continue;
			}
			double safe = finite(entry.getValue());
			total += safe * safe;
		}
		return Math.sqrt(total);
	}

	private static List<String> sharedTerms(
			Map<String,Double> first,
			Map<String,Double> second,
			int limit)
	{
		ArrayList<Map.Entry<String,Double>> shared =
				new ArrayList<Map.Entry<String,Double>>();
		if (first == null || second == null)
		{
			return Collections.emptyList();
		}
		for (Map.Entry<String,Double> entry : first.entrySet())
		{
			Double other = second.get(entry.getKey());
			if (other != null)
			{
				double weight = finite(entry.getValue()) + finite(other);
				shared.add(new java.util.AbstractMap.SimpleImmutableEntry<String,Double>(
						entry.getKey(), Double.valueOf(weight)));
			}
		}
		Collections.sort(shared, new Comparator<Map.Entry<String,Double>>() {
			public int compare(Map.Entry<String,Double> first, Map.Entry<String,Double> second)
			{
				int score = Double.compare(second.getValue(), first.getValue());
				return score != 0 ? score : first.getKey().compareTo(second.getKey());
			}
		});
		ArrayList<String> terms = new ArrayList<String>();
		for (int i = 0; i < shared.size() && i < limit; i++)
		{
			terms.add(shared.get(i).getKey());
		}
		return terms;
	}

	private static double finite(Double value)
	{
		return value == null || !Double.isFinite(value) ? 0.0 : value.doubleValue();
	}

	private static double clamp01(double value)
	{
		if (!Double.isFinite(value) || value < 0.0)
		{
			return 0.0;
		}
		return value > 1.0 ? 1.0 : value;
	}

	private static String safe(String value)
	{
		return value == null ? "" : value.trim();
	}

	enum RankType
	{
		FINAL,
		WITHOUT_TEXT,
		WITHOUT_GRAPH,
		WITHOUT_TIME,
		WITHOUT_BRIDGE,
		WITHOUT_DEBIAS,
		WITHOUT_TOP_TERM
	}

	static final class Input
	{
		final String runId;
		final File datasetFile;
		final Map<String,? extends Map<String,Double>> tfidfDocuments;
		final Map<String,String> userFollowees;
		final Map<String,String> datasetUserFollowees;
		final List<String> targetUsers;
		final Map<String,Integer> userPrimaryRecServer;
		final int currentNode;
		final int numRecAgents;
		final int localResultLimit;

		Input(
				String runId,
				File datasetFile,
				Map<String,? extends Map<String,Double>> tfidfDocuments,
				Map<String,String> userFollowees,
				Map<String,String> datasetUserFollowees,
				Collection<String> targetUsers,
				Map<String,Integer> userPrimaryRecServer,
				int currentNode,
				int numRecAgents,
				int localResultLimit)
		{
			this.runId = runId == null ? "" : runId;
			this.datasetFile = datasetFile;
			this.tfidfDocuments = tfidfDocuments == null
					? Collections.<String,Map<String,Double>>emptyMap()
					: tfidfDocuments;
			this.userFollowees = userFollowees == null
					? Collections.<String,String>emptyMap() : userFollowees;
			this.datasetUserFollowees = datasetUserFollowees == null
					? Collections.<String,String>emptyMap() : datasetUserFollowees;
			this.targetUsers = targetUsers == null
					? Collections.<String>emptyList()
					: new ArrayList<String>(targetUsers);
			this.userPrimaryRecServer = userPrimaryRecServer == null
					? Collections.<String,Integer>emptyMap()
					: userPrimaryRecServer;
			this.currentNode = currentNode;
			this.numRecAgents = Math.max(1, numRecAgents);
			this.localResultLimit = Math.max(0, localResultLimit);
		}

		static Input empty()
		{
			return new Input(
					"",
					null,
					Collections.<String,Map<String,Double>>emptyMap(),
					Collections.<String,String>emptyMap(),
					Collections.<String,String>emptyMap(),
					Collections.<String>emptyList(),
					Collections.<String,Integer>emptyMap(),
					1,
					1,
					0);
		}
	}

	static final class Result
	{
		private final String runId;
		private final File datasetFile;
		private final String summary;
		private final long rowsScanned;
		private final long datedRows;
		private final int graphArtifactEdges;
		private final TreeMap<String,TreeMap<String,Double>> scores;
		private final TreeMap<String,TreeMap<String,String>> details;
		private final LinkedHashMap<String,TargetReport> reports;
		private final ResearchSummary researchSummary;

		private Result(
				String runId,
				File datasetFile,
				String summary,
				long rowsScanned,
				long datedRows,
				int graphArtifactEdges,
				Map<String,TreeMap<String,Double>> scores,
				Map<String,TreeMap<String,String>> details,
				Map<String,TargetReport> reports,
				ResearchSummary researchSummary)
		{
			this.runId = runId == null ? "" : runId;
			this.datasetFile = datasetFile;
			this.summary = summary == null ? "" : summary;
			this.rowsScanned = Math.max(0L, rowsScanned);
			this.datedRows = Math.max(0L, datedRows);
			this.graphArtifactEdges = Math.max(0, graphArtifactEdges);
			this.scores = copyNestedDouble(scores);
			this.details = copyNestedString(details);
			this.reports = new LinkedHashMap<String,TargetReport>();
			if (reports != null)
			{
				this.reports.putAll(reports);
			}
			this.researchSummary = researchSummary == null
					? ResearchSummary.empty() : researchSummary;
		}

		static Result empty(String runId, File datasetFile, String summary)
		{
			return new Result(
					runId,
					datasetFile,
					summary,
					0L,
					0L,
					0,
					Collections.<String,TreeMap<String,Double>>emptyMap(),
					Collections.<String,TreeMap<String,String>>emptyMap(),
					Collections.<String,TargetReport>emptyMap(),
					ResearchSummary.empty());
			}

		TreeMap<String,TreeMap<String,Double>> getScores()
		{
			return copyNestedDouble(scores);
		}

		TreeMap<String,TreeMap<String,String>> getDetails()
		{
			return copyNestedString(details);
		}

		String getSummary()
		{
			return summary;
		}

		String getRunId()
		{
			return runId;
		}

		File getDatasetFile()
		{
			return datasetFile;
		}

		long getRowsScanned()
		{
			return rowsScanned;
		}

		long getDatedRows()
		{
			return datedRows;
		}

		int getGraphArtifactEdges()
		{
			return graphArtifactEdges;
		}

		Map<String,TargetReport> getReports()
		{
			return Collections.unmodifiableMap(reports);
		}

		ResearchSummary getResearchSummary()
		{
			return researchSummary;
		}
	}

	static final class TargetReport
	{
		final String targetUser;
		final String targetFollowee;
		final List<CandidateScore> topCandidates;
		final String headline;
		final int relevantRank;
		final double ndcgAtFive;
		final int observedRows;
		final String temporalCoverage;
		final List<BenchmarkRow> benchmarkRows;

		private TargetReport(
				String targetUser,
				String targetFollowee,
				List<CandidateScore> topCandidates,
				String headline,
				int relevantRank,
				double ndcgAtFive,
				int observedRows,
				String temporalCoverage,
				List<BenchmarkRow> benchmarkRows)
		{
			this.targetUser = targetUser;
			this.targetFollowee = targetFollowee;
			this.topCandidates = Collections.unmodifiableList(
					new ArrayList<CandidateScore>(topCandidates));
			this.headline = headline;
			this.relevantRank = relevantRank;
			this.ndcgAtFive = ndcgAtFive;
			this.observedRows = observedRows;
			this.temporalCoverage = temporalCoverage;
			this.benchmarkRows = Collections.unmodifiableList(
					new ArrayList<BenchmarkRow>(benchmarkRows == null
							? Collections.<BenchmarkRow>emptyList()
							: benchmarkRows));
		}

		static TargetReport from(
				UserProfile target,
				List<CandidateScore> keptScores,
				GraphContext graph,
				TemporalCorpus corpus)
		{
			String headline;
			if (keptScores == null || keptScores.isEmpty())
			{
				headline = "No positive temporal graph candidates were found.";
			}
			else
			{
				CandidateScore top = keptScores.get(0);
				headline = top.candidate.name + " leads because "
						+ "text=" + pct(top.text)
						+ ", graph=" + pct(top.graph)
						+ ", time=" + pct(top.time)
							+ ", bridge=" + pct(top.bridge) + ".";
			}
			int relevantRank = firstRelevantRank(target, keptScores);
			double ndcgAtFive = ndcgAtFive(target, keptScores);
			return new TargetReport(
					target.name,
					target.followee,
					keptScores == null
							? Collections.<CandidateScore>emptyList()
							: keptScores,
					headline,
					relevantRank,
					ndcgAtFive,
					target.observedRows,
					coverageLabel(target, corpus),
					benchmarkRows(target, keptScores));
		}

		private static int firstRelevantRank(
				UserProfile target,
				List<CandidateScore> keptScores)
		{
			if (keptScores == null)
			{
				return 0;
			}
			for (int i = 0; i < keptScores.size(); i++)
			{
				if (sameLabel(target, keptScores.get(i).candidate))
				{
					return i + 1;
				}
			}
			return 0;
		}

		private static double ndcgAtFive(
				UserProfile target,
				List<CandidateScore> keptScores)
		{
			if (keptScores == null || keptScores.isEmpty())
			{
				return 0.0;
			}
			double dcg = 0.0;
			int relevant = 0;
			for (int i = 0; i < keptScores.size() && i < 5; i++)
			{
				if (sameLabel(target, keptScores.get(i).candidate))
				{
					dcg += 1.0 / (Math.log(i + 2.0) / Math.log(2.0));
					relevant++;
				}
			}
			if (relevant == 0)
			{
				return 0.0;
			}
			double idcg = 0.0;
			for (int i = 0; i < relevant; i++)
			{
				idcg += 1.0 / (Math.log(i + 2.0) / Math.log(2.0));
			}
			return idcg <= 0.0 ? 0.0 : clamp01(dcg / idcg);
		}

		private static String coverageLabel(UserProfile target, TemporalCorpus corpus)
		{
			if (target.observedRows <= 0)
			{
				return "no observed rows";
			}
			if (corpus.datedRows > 0 && target.latestTime != Long.MIN_VALUE)
			{
				return target.observedRows + " rows, temporal span "
						+ pct(Math.max(0.0, target.latestPosition - target.firstPosition));
			}
			return target.observedRows + " rows, line-order span "
					+ pct(Math.max(0.0, target.latestPosition - target.firstPosition));
		}
	}

	private interface ScoreLens
	{
		double score(CandidateScore score);
	}

	private static List<BenchmarkRow> benchmarkRows(
			UserProfile target,
			List<CandidateScore> keptScores)
	{
		ArrayList<BenchmarkRow> rows = new ArrayList<BenchmarkRow>();
		if (keptScores == null || keptScores.isEmpty())
		{
			return rows;
		}
		rows.add(benchmarkRow(
				"TGF",
				"observed fused evidence model",
				target,
				keptScores,
				new ScoreLens() {
					public double score(CandidateScore score)
					{
						return score.finalScore;
					}
				}));
		rows.add(benchmarkRow(
				"Similarity",
				"text cosine lens",
				target,
				keptScores,
				new ScoreLens() {
					public double score(CandidateScore score)
					{
						return score.text;
					}
				}));
		rows.add(benchmarkRow(
				"Common Neighbors",
				"graph-neighborhood lens",
				target,
				keptScores,
				new ScoreLens() {
					public double score(CandidateScore score)
					{
						return score.graph;
					}
				}));
		rows.add(benchmarkRow(
				"SVM margin proxy",
				"class-separation lens, not a rerun SVM",
				target,
				keptScores,
				new ScoreLens() {
					public double score(CandidateScore score)
					{
						return clamp01(0.42 * score.text
								+ 0.34 * score.graph
								+ 0.16 * score.bridge
								+ 0.08 * score.debias);
					}
				}));
		rows.add(benchmarkRow(
				"Doc2Vec proxy",
				"semantic-temporal lens, not a Python rerun",
				target,
				keptScores,
				new ScoreLens() {
					public double score(CandidateScore score)
					{
						return clamp01(0.70 * score.text
								+ 0.20 * score.time
								+ 0.10 * score.robust);
					}
				}));
		return Collections.unmodifiableList(rows);
	}

	private static BenchmarkRow benchmarkRow(
			String label,
			String note,
			UserProfile target,
			List<CandidateScore> keptScores,
			ScoreLens lens)
	{
		ArrayList<CandidateScore> ordered = new ArrayList<CandidateScore>(keptScores);
		Collections.sort(ordered, new Comparator<CandidateScore>() {
			public int compare(CandidateScore first, CandidateScore second)
			{
				return CandidateScore.compareScore(
						first,
						second,
						lens.score(first),
						lens.score(second));
			}
		});
		int relevantRank = firstRelevantRank(target, ordered);
		double ndcg = ndcgAtFive(target, ordered);
		CandidateScore top = ordered.isEmpty() ? null : ordered.get(0);
		return new BenchmarkRow(
				label,
				top == null ? "" : top.candidate.name,
				top == null ? 0.0 : lens.score(top),
				relevantRank,
				relevantRank == 1 ? 1.0 : 0.0,
				relevantRank > 0 && relevantRank <= 3 ? 1.0 : 0.0,
				relevantRank > 0 ? 1.0 / relevantRank : 0.0,
				ndcg,
				note);
	}

	private static int firstRelevantRank(
			UserProfile target,
			List<CandidateScore> orderedScores)
	{
		if (orderedScores == null)
		{
			return 0;
		}
		for (int i = 0; i < orderedScores.size(); i++)
		{
			if (sameLabel(target, orderedScores.get(i).candidate))
			{
				return i + 1;
			}
		}
		return 0;
	}

	private static double ndcgAtFive(
			UserProfile target,
			List<CandidateScore> orderedScores)
	{
		if (orderedScores == null || orderedScores.isEmpty())
		{
			return 0.0;
		}
		double dcg = 0.0;
		int relevant = 0;
		for (int i = 0; i < orderedScores.size() && i < 5; i++)
		{
			if (sameLabel(target, orderedScores.get(i).candidate))
			{
				dcg += 1.0 / (Math.log(i + 2.0) / Math.log(2.0));
				relevant++;
			}
		}
		if (relevant == 0)
		{
			return 0.0;
		}
		double idcg = 0.0;
		for (int i = 0; i < relevant; i++)
		{
			idcg += 1.0 / (Math.log(i + 2.0) / Math.log(2.0));
		}
		return idcg <= 0.0 ? 0.0 : clamp01(dcg / idcg);
	}

	static final class BenchmarkRow
	{
		final String label;
		final String topCandidate;
		final double topScore;
		final int relevantRank;
		final double hitAtOne;
		final double hitAtThree;
		final double mrr;
		final double ndcgAtFive;
		final String note;

		private BenchmarkRow(
				String label,
				String topCandidate,
				double topScore,
				int relevantRank,
				double hitAtOne,
				double hitAtThree,
				double mrr,
				double ndcgAtFive,
				String note)
		{
			this.label = label == null ? "" : label;
			this.topCandidate = topCandidate == null ? "" : topCandidate;
			this.topScore = clamp01(topScore);
			this.relevantRank = Math.max(0, relevantRank);
			this.hitAtOne = clamp01(hitAtOne);
			this.hitAtThree = clamp01(hitAtThree);
			this.mrr = clamp01(mrr);
			this.ndcgAtFive = clamp01(ndcgAtFive);
			this.note = note == null ? "" : note;
		}
	}

	static final class CandidateScore
	{
		static final Comparator<CandidateScore> FINAL_ORDER =
				new Comparator<CandidateScore>() {
					public int compare(CandidateScore first, CandidateScore second)
					{
						return compareScore(first, second, first.finalScore, second.finalScore);
					}
				};
		static final Comparator<CandidateScore> WITHOUT_TEXT_ORDER =
				new Comparator<CandidateScore>() {
					public int compare(CandidateScore first, CandidateScore second)
					{
						return compareScore(first, second,
								first.withoutTextScore, second.withoutTextScore);
					}
				};
		static final Comparator<CandidateScore> WITHOUT_GRAPH_ORDER =
				new Comparator<CandidateScore>() {
					public int compare(CandidateScore first, CandidateScore second)
					{
						return compareScore(first, second,
								first.withoutGraphScore, second.withoutGraphScore);
					}
				};
		static final Comparator<CandidateScore> WITHOUT_TIME_ORDER =
				new Comparator<CandidateScore>() {
					public int compare(CandidateScore first, CandidateScore second)
					{
						return compareScore(first, second,
								first.withoutTimeScore, second.withoutTimeScore);
					}
				};
		static final Comparator<CandidateScore> WITHOUT_BRIDGE_ORDER =
				new Comparator<CandidateScore>() {
					public int compare(CandidateScore first, CandidateScore second)
					{
						return compareScore(first, second,
								first.withoutBridgeScore, second.withoutBridgeScore);
					}
				};
		static final Comparator<CandidateScore> WITHOUT_DEBIAS_ORDER =
				new Comparator<CandidateScore>() {
					public int compare(CandidateScore first, CandidateScore second)
					{
						return compareScore(first, second,
								first.withoutDebiasScore, second.withoutDebiasScore);
					}
				};
		static final Comparator<CandidateScore> WITHOUT_TOP_TERM_ORDER =
				new Comparator<CandidateScore>() {
					public int compare(CandidateScore first, CandidateScore second)
					{
						return compareScore(first, second,
								first.withoutTopTermScore, second.withoutTopTermScore);
					}
				};

		final UserProfile target;
		final UserProfile candidate;
		double text;
		double graph;
		double time;
		double bridge;
		double debias;
		double robust;
		double finalScore;
		double withoutTextScore;
		double withoutGraphScore;
		double withoutTimeScore;
		double withoutBridgeScore;
		double withoutDebiasScore;
			double withoutTopTermScore;
			double withoutTopTermText;
			double textContribution;
			double graphContribution;
			double timeContribution;
			double bridgeContribution;
			double debiasContribution;
			double robustContribution;
			double marginToNext;
			double confidenceRadius;
			double confidenceLower;
			double confidenceUpper;
			double stability;
			double evidenceCoverage;
		double counterfactualFragility;
		double calibrationResidual;
		double calibrationThreshold;
		double calibrationPValue;
		double calibratedConfidence;
		double challengerScore;
			double challengerGap;
			double challengerText;
			double challengerGraph;
			double challengerTime;
			double challengerBridge;
			double challengerDebias;
			double challengerRobust;
			int rank;
			int withoutTextRank;
			int withoutGraphRank;
			int withoutTimeRank;
			int withoutBridgeRank;
			int withoutDebiasRank;
			int withoutTopTermRank;
			String path;
			String challengerName = "";
			String challengerFollowee = "";
			String bestContrastSignal = "n/a";
			String reliabilityBand = "pending";
			String calibrationVerdict = "calibration pending";
			String calibrationNote = "";
			String whyThis = "";
			String whyNotChallenger = "";
			List<String> sharedTerms = Collections.emptyList();
			List<TimelineFrame> timeline = Collections.emptyList();
			List<CounterfactualProbe> counterfactuals = Collections.emptyList();
			List<EvidenceAtom> evidenceAtoms = Collections.emptyList();
			List<String> riskFlags = Collections.emptyList();
			String researchVerdict = "research evidence pending";

		private CandidateScore(UserProfile target, UserProfile candidate)
		{
			this.target = target;
			this.candidate = candidate;
		}

		String candidateName()
		{
			return candidate.name;
		}

		String candidateFollowee()
		{
			return candidate.followee;
		}

			String impactSummary()
			{
				return whyThis + " " + whyNotChallenger
						+ " Removing text evidence rank "
						+ rankText(withoutTextRank)
						+ "; removing graph evidence rank "
						+ rankText(withoutGraphRank)
					+ "; removing bridge rank "
					+ rankText(withoutBridgeRank)
					+ "; stability " + pct(stability) + ".";
		}

		double counterfactualScore(
				boolean removeGraph,
				boolean removeTime,
				boolean removeBridge,
				boolean removeDebias,
				boolean removeTopTerm)
		{
			double textValue = removeTopTerm ? withoutTopTermText : text;
			double graphValue = removeGraph ? 0.0 : graph;
			double timeValue = removeTime ? 0.0 : time;
			double bridgeValue = removeBridge ? 0.0 : bridge;
			double debiasValue = removeDebias ? 0.0 : debias;
			return clamp01(fusedScore(
					textValue,
					graphValue,
					timeValue,
					bridgeValue,
					debiasValue,
					robustness(textValue, graphValue, timeValue, bridgeValue)));
		}

		private static int compareScore(
				CandidateScore first,
				CandidateScore second,
				double firstScore,
				double secondScore)
		{
			int score = Double.compare(secondScore, firstScore);
			return score != 0
					? score
					: first.candidate.name.compareTo(second.candidate.name);
		}
		}

		static final class EvidenceAtom
		{
			final String label;
			final double value;
			final double contribution;
			final String explanation;

			private EvidenceAtom(
					String label,
					double value,
					double contribution,
					String explanation)
			{
				this.label = label == null ? "" : label;
				this.value = clamp01(value);
				this.contribution = Math.max(0.0, contribution);
				this.explanation = explanation == null ? "" : explanation;
			}
		}

		static final class TimelineFrame
	{
		final int index;
		final double progress;
		final String label;
		final double score;
		final double text;
		final double graph;
		final double time;
		final double bridge;
		final double debias;
		final double robust;
		final String evidence;
		final double scoreDelta;
		final String dominantSignal;
		final String auditNote;
		int rank;
		int previousRank;

		private TimelineFrame(
				int index,
				double progress,
				String label,
				double score,
				double text,
				double graph,
				double time,
				double bridge,
				double debias,
				double robust,
				String evidence,
				double scoreDelta,
				String dominantSignal,
				String auditNote)
		{
			this.index = index;
			this.progress = clamp01(progress);
			this.label = label == null ? "" : label;
			this.score = clamp01(score);
			this.text = clamp01(text);
			this.graph = clamp01(graph);
			this.time = clamp01(time);
			this.bridge = clamp01(bridge);
			this.debias = clamp01(debias);
			this.robust = clamp01(robust);
			this.evidence = evidence == null ? "" : evidence;
			this.scoreDelta = scoreDelta;
			this.dominantSignal = dominantSignal == null ? "" : dominantSignal;
			this.auditNote = auditNote == null ? "" : auditNote;
		}
	}

	static final class CounterfactualProbe
	{
		final String label;
		final double score;
			final int rank;
			final double scoreDrop;
			final boolean rankChanged;
			final int rankShift;

			private CounterfactualProbe(
					String label,
					double score,
					int rank,
					double scoreDrop,
					boolean rankChanged,
					int rankShift)
			{
				this.label = label == null ? "" : label;
				this.score = clamp01(score);
				this.rank = rank;
				this.scoreDrop = Math.max(0.0, scoreDrop);
				this.rankChanged = rankChanged;
				this.rankShift = Math.max(0, rankShift);
			}

		static CounterfactualProbe from(
				String label,
				double score,
				int rank,
				double originalScore,
				int originalRank)
		{
				return new CounterfactualProbe(
						label,
						score,
						rank,
						Math.max(0.0, originalScore - score),
						originalRank > 0 && rank > originalRank,
						originalRank > 0 && rank > 0
								? Math.abs(rank - originalRank)
								: 0);
			}
		}

	static final class ResearchSummary
	{
		final int targetCount;
		final int scoredPairs;
		final int timelineFrames;
		final double hitAtOne;
		final double hitAtThree;
		final double mrr;
		final double ndcgAtFive;
			final double averageStability;
			final double averageConfidenceWidth;
			final double counterfactualFlipRate;
			final double averageEvidenceCoverage;
			final double averageCounterfactualFragility;
			final double averageCalibrationPValue;
			final double abstainRate;
			final int evidenceLimitFlags;
			final List<BenchmarkRow> benchmarkRows;
			final FoundationReadiness foundationReadiness;
			final String headline;

		private ResearchSummary(
				int targetCount,
				int scoredPairs,
				int timelineFrames,
				double hitAtOne,
				double hitAtThree,
				double mrr,
					double ndcgAtFive,
					double averageStability,
					double averageConfidenceWidth,
					double counterfactualFlipRate,
					double averageEvidenceCoverage,
					double averageCounterfactualFragility,
					double averageCalibrationPValue,
					double abstainRate,
					int evidenceLimitFlags,
					List<BenchmarkRow> benchmarkRows,
					FoundationReadiness foundationReadiness,
					String headline)
			{
			this.targetCount = Math.max(0, targetCount);
			this.scoredPairs = Math.max(0, scoredPairs);
			this.timelineFrames = Math.max(0, timelineFrames);
			this.hitAtOne = clamp01(hitAtOne);
			this.hitAtThree = clamp01(hitAtThree);
			this.mrr = clamp01(mrr);
			this.ndcgAtFive = clamp01(ndcgAtFive);
				this.averageStability = clamp01(averageStability);
				this.averageConfidenceWidth = clamp01(averageConfidenceWidth);
				this.counterfactualFlipRate = clamp01(counterfactualFlipRate);
				this.averageEvidenceCoverage = clamp01(averageEvidenceCoverage);
				this.averageCounterfactualFragility =
						clamp01(averageCounterfactualFragility);
				this.averageCalibrationPValue = clamp01(averageCalibrationPValue);
				this.abstainRate = clamp01(abstainRate);
				this.evidenceLimitFlags = Math.max(0, evidenceLimitFlags);
				this.benchmarkRows = Collections.unmodifiableList(
						new ArrayList<BenchmarkRow>(benchmarkRows == null
								? Collections.<BenchmarkRow>emptyList()
								: benchmarkRows));
				this.foundationReadiness = foundationReadiness == null
						? FoundationReadiness.empty() : foundationReadiness;
				this.headline = headline == null ? "" : headline;
			}

		static ResearchSummary empty()
		{
			return new ResearchSummary(
					0,
					0,
					0,
					0.0,
					0.0,
					0.0,
					0.0,
						0.0,
						0.0,
						0.0,
							0.0,
							0.0,
							0.0,
							0.0,
							0,
						Collections.<BenchmarkRow>emptyList(),
						FoundationReadiness.empty(),
						"No TGF research trace has been recorded yet.");
		}

		static ResearchSummary from(
				Collection<TargetReport> reports,
				int scoredPairs,
				TemporalCorpus corpus,
				GraphContext graph)
		{
			if (reports == null || reports.isEmpty())
			{
				return empty();
			}
			int targets = 0;
			int hitOne = 0;
			int hitThree = 0;
			int timelineFrames = 0;
			int candidates = 0;
			int rankSensitive = 0;
			double mrrTotal = 0.0;
			double ndcgTotal = 0.0;
				double stabilityTotal = 0.0;
				double widthTotal = 0.0;
				double coverageTotal = 0.0;
				double fragilityTotal = 0.0;
				double calibrationTotal = 0.0;
				int abstainCount = 0;
				int evidenceLimitFlags = 0;
				for (TargetReport report : reports)
			{
				targets++;
				if (report.relevantRank == 1)
				{
					hitOne++;
				}
				if (report.relevantRank > 0 && report.relevantRank <= 3)
				{
					hitThree++;
				}
				if (report.relevantRank > 0)
				{
					mrrTotal += 1.0 / report.relevantRank;
				}
				ndcgTotal += report.ndcgAtFive;
				for (CandidateScore score : report.topCandidates)
				{
					candidates++;
					timelineFrames += score.timeline.size();
						stabilityTotal += score.stability;
						coverageTotal += score.evidenceCoverage;
						fragilityTotal += score.counterfactualFragility;
						calibrationTotal += score.calibrationPValue;
						if (score.calibrationVerdict != null
								&& score.calibrationVerdict.startsWith("abstain"))
						{
							abstainCount++;
						}
						widthTotal += Math.max(0.0,
								score.confidenceUpper - score.confidenceLower);
						if (score.riskFlags != null)
						{
							for (String flag : score.riskFlags)
							{
								if (flag != null
										&& !"no critical evidence-limit flags".equals(flag))
								{
									evidenceLimitFlags++;
								}
							}
						}
						if (score.withoutGraphRank > score.rank + 1
							|| score.withoutTextRank > score.rank + 1
							|| score.withoutBridgeRank > score.rank + 1)
					{
						rankSensitive++;
					}
				}
			}
			double targetDenominator = Math.max(1, targets);
			double candidateDenominator = Math.max(1, candidates);
			String headline = "Temporal holdout proxy: "
					+ pct(hitOne / targetDenominator)
					+ " Hit@1, " + pct(hitThree / targetDenominator)
					+ " Hit@3, " + scoredPairs + " scored pairs.";
			List<BenchmarkRow> benchmarkRows =
					aggregateBenchmarkRows(reports);
			FoundationReadiness readiness =
					FoundationReadiness.from(
							coverageTotal / candidateDenominator,
							calibrationTotal / candidateDenominator,
							abstainCount / candidateDenominator,
							corpus,
							graph,
							benchmarkRows);
			return new ResearchSummary(
					targets,
					scoredPairs,
					timelineFrames,
					hitOne / targetDenominator,
					hitThree / targetDenominator,
					mrrTotal / targetDenominator,
					ndcgTotal / targetDenominator,
						stabilityTotal / candidateDenominator,
						widthTotal / candidateDenominator,
						rankSensitive / candidateDenominator,
						coverageTotal / candidateDenominator,
						fragilityTotal / candidateDenominator,
						calibrationTotal / candidateDenominator,
						abstainCount / candidateDenominator,
						evidenceLimitFlags,
						benchmarkRows,
						readiness,
						headline);
		}
	}

	private static List<BenchmarkRow> aggregateBenchmarkRows(
			Collection<TargetReport> reports)
	{
		if (reports == null || reports.isEmpty())
		{
			return Collections.emptyList();
		}
		LinkedHashMap<String,BenchmarkAccumulator> accumulators =
				new LinkedHashMap<String,BenchmarkAccumulator>();
		for (TargetReport report : reports)
		{
			if (report == null || report.benchmarkRows == null)
			{
				continue;
			}
			for (BenchmarkRow row : report.benchmarkRows)
			{
				BenchmarkAccumulator accumulator = accumulators.get(row.label);
				if (accumulator == null)
				{
					accumulator = new BenchmarkAccumulator(row.label, row.note);
					accumulators.put(row.label, accumulator);
				}
				accumulator.add(row);
			}
		}
		ArrayList<BenchmarkRow> rows = new ArrayList<BenchmarkRow>();
		for (BenchmarkAccumulator accumulator : accumulators.values())
		{
			rows.add(accumulator.toRow());
		}
		return Collections.unmodifiableList(rows);
	}

	private static final class BenchmarkAccumulator
	{
		private final String label;
		private final String note;
		private int count;
		private double hitOne;
		private double hitThree;
		private double mrr;
		private double ndcg;
		private double topScore;
		private String topCandidate = "";

		BenchmarkAccumulator(String label, String note)
		{
			this.label = label;
			this.note = note;
		}

		void add(BenchmarkRow row)
		{
			count++;
			hitOne += row.hitAtOne;
			hitThree += row.hitAtThree;
			mrr += row.mrr;
			ndcg += row.ndcgAtFive;
			topScore += row.topScore;
			if (topCandidate.length() == 0 && row.topCandidate.length() > 0)
			{
				topCandidate = row.topCandidate;
			}
		}

		BenchmarkRow toRow()
		{
			double denominator = Math.max(1, count);
			return new BenchmarkRow(
					label,
					topCandidate,
					topScore / denominator,
					0,
					hitOne / denominator,
					hitThree / denominator,
					mrr / denominator,
					ndcg / denominator,
					note);
		}
	}

	static final class FoundationReadiness
	{
		final double temporalDepth;
		final double graphCoverage;
		final double evidenceCoverage;
		final double calibrationTrust;
		final double benchmarkHonesty;
		final double overall;
		final String label;
		final String note;

		private FoundationReadiness(
				double temporalDepth,
				double graphCoverage,
				double evidenceCoverage,
				double calibrationTrust,
				double benchmarkHonesty,
				String label,
				String note)
		{
			this.temporalDepth = clamp01(temporalDepth);
			this.graphCoverage = clamp01(graphCoverage);
			this.evidenceCoverage = clamp01(evidenceCoverage);
			this.calibrationTrust = clamp01(calibrationTrust);
			this.benchmarkHonesty = clamp01(benchmarkHonesty);
			this.overall = clamp01(0.22 * this.temporalDepth
					+ 0.18 * this.graphCoverage
					+ 0.24 * this.evidenceCoverage
					+ 0.22 * this.calibrationTrust
					+ 0.14 * this.benchmarkHonesty);
			this.label = label == null ? "" : label;
			this.note = note == null ? "" : note;
		}

		static FoundationReadiness empty()
		{
			return new FoundationReadiness(
					0.0,
					0.0,
					0.0,
					0.0,
					0.0,
					"no evidence twin yet",
					"Run TGF to produce temporal, graph, calibration, and benchmark evidence.");
		}

		static FoundationReadiness from(
				double evidenceCoverage,
				double calibrationPValue,
				double abstainRate,
				TemporalCorpus corpus,
				GraphContext graph,
				List<BenchmarkRow> benchmarkRows)
		{
			double temporalDepth = corpus == null || corpus.rowsScanned <= 0
					? 0.0
					: (corpus.datedRows > 0
							? clamp01(corpus.datedRows
									/ (double)Math.max(1L, corpus.rowsScanned))
							: 0.45);
			double graphCoverage = graph == null
					? 0.0
					: (graph.graphArtifactEdges > 0 ? 1.0
							: graph.usersByFollowee.isEmpty() ? 0.0 : 0.58);
			double benchmarkHonesty = benchmarkRows == null || benchmarkRows.isEmpty()
					? 0.0
					: Math.min(1.0, benchmarkRows.size() / 5.0);
			double calibrationTrust = clamp01(0.74 * calibrationPValue
					+ 0.26 * (1.0 - abstainRate));
			double preliminary = clamp01(0.22 * temporalDepth
					+ 0.18 * graphCoverage
					+ 0.24 * evidenceCoverage
					+ 0.22 * calibrationTrust
					+ 0.14 * benchmarkHonesty);
			String label;
			if (preliminary >= 0.78)
			{
				label = "foundation-ready evidence twin";
			}
			else if (preliminary >= 0.58)
			{
				label = "research-grade evidence scaffold";
			}
			else
			{
				label = "experimental evidence lens";
			}
			String note = "Not a pretrained graph foundation model; readiness reflects "
					+ "temporal depth, graph coverage, evidence coverage, calibration, "
					+ "and benchmark transparency.";
			return new FoundationReadiness(
					temporalDepth,
					graphCoverage,
					evidenceCoverage,
					calibrationTrust,
					benchmarkHonesty,
					label,
					note);
		}
	}

	private static final class UserProfile
	{
		final String name;
		String followee;
		final LinkedHashMap<String,Double> tfidf;
		long firstTime = Long.MAX_VALUE;
		long latestTime = Long.MIN_VALUE;
		long firstLine = Long.MAX_VALUE;
		long latestLine = -1L;
		int rowCount;
		int observedRows;
		double recency = 0.50;
		double firstPosition;
		double latestPosition = 0.50;

		UserProfile(String name, String followee, Map<String,Double> tfidf)
		{
			this.name = name;
			this.followee = followee == null ? "" : followee;
			this.tfidf = new LinkedHashMap<String,Double>();
			this.tfidf.putAll(tfidf);
		}

		void observeTime(long time)
		{
			firstTime = Math.min(firstTime, time);
			latestTime = Math.max(latestTime, time);
			rowCount++;
		}

		void observeLine(long line)
		{
			firstLine = Math.min(firstLine, line);
			latestLine = Math.max(latestLine, line);
			observedRows++;
		}
	}

	private static final class TemporalCorpus
	{
		long rowsScanned;
		long datedRows;
		long minTime = Long.MAX_VALUE;
		long maxTime = Long.MIN_VALUE;
		long minLine = Long.MAX_VALUE;
		long maxLine = Long.MIN_VALUE;
		boolean limitReached;
		String warning;
	}

	private static final class GraphContext
	{
		final LinkedHashMap<String,LinkedHashSet<String>> usersByFollowee =
				new LinkedHashMap<String,LinkedHashSet<String>>();
		final Map<String,LinkedHashSet<String>> graphNeighbors =
				new HashMap<String,LinkedHashSet<String>>();
		final List<String> sampleEdges = new ArrayList<String>();
		int maxFolloweeSize = 1;
		int graphArtifactEdges;

		void connect(String first, String second)
		{
			addNeighbor(first, second);
			addNeighbor(second, first);
		}

		boolean areConnected(String first, String second)
		{
			Set<String> neighbors = graphNeighbors.get(first);
			return neighbors != null && neighbors.contains(second);
		}

		int commonNeighborCount(String first, String second)
		{
			Set<String> firstNeighbors = graphNeighbors.get(first);
			Set<String> secondNeighbors = graphNeighbors.get(second);
			if (firstNeighbors == null || secondNeighbors == null
					|| firstNeighbors.isEmpty() || secondNeighbors.isEmpty())
			{
				return 0;
			}
			Set<String> smaller = firstNeighbors.size() <= secondNeighbors.size()
					? firstNeighbors : secondNeighbors;
			Set<String> larger = smaller == firstNeighbors
					? secondNeighbors : firstNeighbors;
			int count = 0;
			for (String value : smaller)
			{
				if (larger.contains(value))
				{
					count++;
				}
			}
			return count;
		}

		private void addNeighbor(String source, String target)
		{
			LinkedHashSet<String> neighbors = graphNeighbors.get(source);
			if (neighbors == null)
			{
				neighbors = new LinkedHashSet<String>();
				graphNeighbors.put(source, neighbors);
			}
			neighbors.add(target);
		}
	}

	private static final class GraphSignal
	{
		final double value;
		final String path;

		GraphSignal(double value, String path)
		{
			this.value = value;
			this.path = path;
		}
	}

	private static TreeMap<String,TreeMap<String,Double>> copyNestedDouble(
			Map<String,TreeMap<String,Double>> source)
	{
		TreeMap<String,TreeMap<String,Double>> copy =
				new TreeMap<String,TreeMap<String,Double>>();
		if (source != null)
		{
			for (Map.Entry<String,TreeMap<String,Double>> entry : source.entrySet())
			{
				copy.put(entry.getKey(), entry.getValue() == null
						? new TreeMap<String,Double>()
						: new TreeMap<String,Double>(entry.getValue()));
			}
		}
		return copy;
	}

	private static TreeMap<String,TreeMap<String,String>> copyNestedString(
			Map<String,TreeMap<String,String>> source)
	{
		TreeMap<String,TreeMap<String,String>> copy =
				new TreeMap<String,TreeMap<String,String>>();
		if (source != null)
		{
			for (Map.Entry<String,TreeMap<String,String>> entry : source.entrySet())
			{
				copy.put(entry.getKey(), entry.getValue() == null
						? new TreeMap<String,String>()
						: new TreeMap<String,String>(entry.getValue()));
			}
		}
		return copy;
	}
}
