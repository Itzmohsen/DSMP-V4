package TwitterGatherDataFollowers.userRyersonU;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class RecommendationEvidenceEngine
{
	private static final int DATASET_SCAN_LIMIT = 2000000;
	private static final int ROW_SAMPLE_LIMIT = 6;
	private static final int TERM_LIMIT = 12;
	private static final Set<String> STOP_WORDS = buildStopWords();

	private RecommendationEvidenceEngine()
	{
	}

	static EvidenceReport analyze(
			SimulatorHelpPanel.Snapshot snapshot,
			RecommendationEvent event,
			int selectedRank)
	{
		SimulatorHelpPanel.Snapshot safeSnapshot =
				snapshot == null ? SimulatorHelpPanel.Snapshot.empty() : snapshot;
		if (event == null || event.matches.isEmpty())
		{
			return EvidenceReport.empty(safeSnapshot);
		}
		int index = Math.max(0, Math.min(selectedRank, event.matches.size() - 1));
		MatchEvidence selected = event.matches.get(index);
		MutableReport state = new MutableReport(safeSnapshot, event, selected, index + 1);
		readDatasetEvidence(state);
		computeTermEvidence(state);
		readGraphEvidence(state);
		state.finish();
		return state.toReport();
	}

	static final class RecommendationEvent
	{
		final String targetUser;
		final RecommendationCandidateType candidateType;
		final RecommendationResultStatus status;
		final String resultLabel;
		final String message;
		final List<MatchEvidence> matches;
		final long createdAtMillis;

		RecommendationEvent(
				String targetUser,
				RecommendationCandidateType candidateType,
				RecommendationResultStatus status,
				String resultLabel,
				String message,
				List<MatchEvidence> matches)
		{
			this.targetUser = clean(targetUser, "Unknown target");
			this.candidateType = candidateType == null
					? RecommendationCandidateType.USER : candidateType;
			this.status = status == null ? RecommendationResultStatus.OK : status;
			this.resultLabel = resultLabel == null ? "" : resultLabel;
			this.message = message == null ? "" : message;
			this.matches = matches == null
					? Collections.<MatchEvidence>emptyList()
					: Collections.unmodifiableList(new ArrayList<MatchEvidence>(matches));
			this.createdAtMillis = System.currentTimeMillis();
		}
	}

	static final class MatchEvidence
	{
		final int rank;
		final String name;
		final String score;
		final String detail;

		MatchEvidence(int rank, String name, String score, String detail)
		{
			this.rank = Math.max(1, rank);
			this.name = clean(name, "Unknown recommendation");
			this.score = score == null ? "" : score.trim();
			this.detail = detail == null ? "" : detail.trim();
		}
	}

	static final class EvidenceReport
	{
		final boolean available;
		final String datasetName;
		final String datasetPath;
		final String algorithmName;
		final String modeLabel;
		final String targetUser;
		final String candidateName;
		final RecommendationCandidateType candidateType;
		final int rank;
		final String score;
		final int scorePercent;
		final String scoreLabel;
		final String statusTitle;
		final String plainVerdict;
		final String algorithmLensTitle;
		final String algorithmLensDetail;
		final String evidenceQuestion;
		final long rowsScanned;
		final boolean rowScanLimitReached;
		final int distinctUsers;
		final int distinctLabels;
		final int targetRowCount;
		final int candidateRowCount;
		final List<DsmpRow> targetRows;
		final List<DsmpRow> candidateRows;
		final List<TermScore> targetTerms;
		final List<TermScore> candidateTerms;
		final List<TermScore> sharedTerms;
		final List<String> originalLabels;
		final boolean graphAvailable;
		/** Real communities (followed accounts) in the run graph. */
		final int graphCommunities;
		/** Whether the candidate is inside the target's community or bridges to another. */
		final String graphRelation;
		/** How often the candidate was recommended across the run's targets. */
		final String graphExposure;
		/** The candidate's PageRank rank in the run graph. */
		final String graphCandidateRank;
		final String lastOriginalEdge;
		final String lastRecommendedEdge;
		final boolean recommendationMatchesGraphEdge;
		final boolean sameAsOriginalLabel;
		final String graphMessage;
		final List<CounterfactualInsight> counterfactualInsights;
		final ComparisonReport comparison;
		final List<TermLocation> termLocations;
		final String counterfactualSummary;
		final List<String> warnings;

		private EvidenceReport(MutableReport state)
		{
			this.available = true;
			this.datasetName = state.snapshot.datasetName;
			this.datasetPath = state.snapshot.datasetPath;
			this.algorithmName = state.snapshot.algorithmName;
			this.modeLabel = state.snapshot.modeLabel;
			this.targetUser = state.event.targetUser;
			this.candidateName = state.selected.name;
			this.candidateType = state.event.candidateType;
			this.rank = state.rank;
			this.score = state.selected.score;
			this.scorePercent = state.scorePercent;
			this.scoreLabel = state.scoreLabel;
			this.statusTitle = state.statusTitle;
			this.plainVerdict = state.plainVerdict;
			this.algorithmLensTitle = state.algorithmLensTitle;
			this.algorithmLensDetail = state.algorithmLensDetail;
			this.evidenceQuestion = state.evidenceQuestion;
			this.rowsScanned = state.rowsScanned;
			this.rowScanLimitReached = state.rowScanLimitReached;
			this.distinctUsers = state.distinctUsers.size();
			this.distinctLabels = state.distinctLabels.size();
			this.targetRowCount = state.targetRowCount;
			this.candidateRowCount = state.candidateRowCount;
			this.targetRows = immutableRows(state.targetRows);
			this.candidateRows = immutableRows(state.candidateRows);
			this.targetTerms = immutableTerms(state.targetTerms);
			this.candidateTerms = immutableTerms(state.candidateTerms);
			this.sharedTerms = immutableTerms(state.sharedTerms);
			this.originalLabels = Collections.unmodifiableList(
					new ArrayList<String>(state.originalLabels));
			this.graphAvailable = state.graphAvailable;
			this.graphCommunities = state.graphCommunities;
			this.graphRelation = state.graphRelation;
			this.graphExposure = state.graphExposure;
			this.graphCandidateRank = state.graphCandidateRank;
			this.lastOriginalEdge = state.lastOriginalEdge;
			this.lastRecommendedEdge = state.lastRecommendedEdge;
			this.recommendationMatchesGraphEdge = state.recommendationMatchesGraphEdge;
			this.sameAsOriginalLabel = state.sameAsOriginalLabel;
			this.graphMessage = state.graphMessage;
			this.counterfactualInsights = Collections.unmodifiableList(
					new ArrayList<CounterfactualInsight>(state.counterfactualInsights));
			this.comparison = state.comparison;
			this.termLocations = Collections.unmodifiableList(
					new ArrayList<TermLocation>(state.termLocations));
			this.counterfactualSummary = state.counterfactualSummary;
			this.warnings = Collections.unmodifiableList(
					new ArrayList<String>(state.warnings));
		}

		private EvidenceReport(SimulatorHelpPanel.Snapshot snapshot)
		{
			this.available = false;
			this.datasetName = snapshot.datasetName;
			this.datasetPath = snapshot.datasetPath;
			this.algorithmName = snapshot.algorithmName;
			this.modeLabel = snapshot.modeLabel;
			this.targetUser = "No recommendation selected";
			this.candidateName = "Waiting for completed results";
			this.candidateType = snapshot.outputType == null
					? RecommendationCandidateType.USER : snapshot.outputType;
			this.rank = 0;
			this.score = "";
			this.scorePercent = 0;
			this.scoreLabel = "No score";
			this.statusTitle = "Waiting for recommendation evidence";
			this.plainVerdict = "Run the simulator and select a recommendation to inspect why it was produced.";
			this.algorithmLensTitle = "No completed recommendation yet";
			this.algorithmLensDetail = "This tab is read-only and starts explaining results after the recommendation pipeline publishes a score map.";
			this.evidenceQuestion = "Which completed recommendation should be explained?";
			this.rowsScanned = 0L;
			this.rowScanLimitReached = false;
			this.distinctUsers = 0;
			this.distinctLabels = 0;
			this.targetRowCount = 0;
			this.candidateRowCount = 0;
			this.targetRows = Collections.emptyList();
			this.candidateRows = Collections.emptyList();
			this.targetTerms = Collections.emptyList();
			this.candidateTerms = Collections.emptyList();
			this.sharedTerms = Collections.emptyList();
			this.originalLabels = Collections.emptyList();
			this.graphAvailable = false;
			this.graphCommunities = 0;
			this.graphRelation = "\u2014";
			this.graphExposure = "\u2014";
			this.graphCandidateRank = "\u2014";
			this.lastOriginalEdge = "No follow found";
			this.lastRecommendedEdge = "No adoption yet";
			this.recommendationMatchesGraphEdge = false;
			this.sameAsOriginalLabel = false;
			this.graphMessage = "The run graph appears when the run's record is written, at the end of the run.";
			this.counterfactualInsights = Collections.emptyList();
			this.comparison = ComparisonReport.empty();
			this.termLocations = Collections.emptyList();
			this.counterfactualSummary = "No completed recommendation is available for counterfactual evidence yet.";
			this.warnings = Collections.emptyList();
		}

		static EvidenceReport empty(SimulatorHelpPanel.Snapshot snapshot)
		{
			return new EvidenceReport(snapshot);
		}
	}

	static final class DsmpRow
	{
		final String followee;
		final String postId;
		final String date;
		final String userId;
		final String user;
		final String text;

		private DsmpRow(
				String followee,
				String postId,
				String date,
				String userId,
				String user,
				String text)
		{
			this.followee = followee;
			this.postId = postId;
			this.date = date;
			this.userId = userId;
			this.user = user;
			this.text = text;
		}
	}

	static final class TermScore
	{
		final String term;
		final int value;
		final int otherValue;

		private TermScore(String term, int value, int otherValue)
		{
			this.term = term;
			this.value = value;
			this.otherValue = otherValue;
		}
	}

	static final class CounterfactualInsight
	{
		final String title;
		final String action;
		final String outcome;
		final String evidence;
		final String fidelity;
		final int pressurePercent;
		final boolean likelySwitch;

		private CounterfactualInsight(
				String title,
				String action,
				String outcome,
				String evidence,
				String fidelity,
				int pressurePercent,
				boolean likelySwitch)
		{
			this.title = clean(title, "Counterfactual probe");
			this.action = clean(action, "Change source evidence");
			this.outcome = clean(outcome, "The current result would be rechecked.");
			this.evidence = clean(evidence, "Evidence was estimated from inspected rows.");
			this.fidelity = clean(fidelity, "Evidence-side estimate");
			this.pressurePercent = Math.max(0, Math.min(100, pressurePercent));
			this.likelySwitch = likelySwitch;
		}
	}

	static final class ComparisonReport
	{
		final boolean available;
		final MatchEvidence left;
		final MatchEvidence right;
		final String scoreGap;
		final int leftSupport;
		final int rightSupport;
		final List<TermScore> leftSharedTerms;
		final List<TermScore> rightSharedTerms;
		final String verdict;

		private ComparisonReport(
				boolean available,
				MatchEvidence left,
				MatchEvidence right,
				String scoreGap,
				int leftSupport,
				int rightSupport,
				List<TermScore> leftSharedTerms,
				List<TermScore> rightSharedTerms,
				String verdict)
		{
			this.available = available;
			this.left = left;
			this.right = right;
			this.scoreGap = scoreGap == null ? "" : scoreGap;
			this.leftSupport = leftSupport;
			this.rightSupport = rightSupport;
			this.leftSharedTerms = immutableTerms(leftSharedTerms);
			this.rightSharedTerms = immutableTerms(rightSharedTerms);
			this.verdict = verdict == null ? "" : verdict;
		}

		static ComparisonReport empty()
		{
			return new ComparisonReport(
					false,
					new MatchEvidence(0, "No selected result", "", ""),
					new MatchEvidence(0, "No alternate result", "", ""),
					"",
					0,
					0,
					Collections.<TermScore>emptyList(),
					Collections.<TermScore>emptyList(),
					"Run a recommendation with at least two ranked candidates to compare rank 1 against rank 2.");
		}
	}

	static final class TermLocation
	{
		final String term;
		final String role;
		final int targetCount;
		final int candidateCount;
		final int pressurePercent;
		final String counterfactualEffect;
		final List<DsmpRow> targetRows;
		final List<DsmpRow> candidateRows;

		private TermLocation(
				String term,
				String role,
				int targetCount,
				int candidateCount,
				int pressurePercent,
				String counterfactualEffect,
				List<DsmpRow> targetRows,
				List<DsmpRow> candidateRows)
		{
			this.term = clean(term, "term");
			this.role = clean(role, "evidence");
			this.targetCount = Math.max(0, targetCount);
			this.candidateCount = Math.max(0, candidateCount);
			this.pressurePercent = Math.max(0, Math.min(100, pressurePercent));
			this.counterfactualEffect = clean(counterfactualEffect, "Evidence detail unavailable.");
			this.targetRows = immutableRows(targetRows);
			this.candidateRows = immutableRows(candidateRows);
		}
	}

	private static final class MutableReport
	{
		final SimulatorHelpPanel.Snapshot snapshot;
		final RecommendationEvent event;
		final MatchEvidence selected;
		final int rank;
		final List<DsmpRow> targetRows = new ArrayList<DsmpRow>();
		final List<DsmpRow> candidateRows = new ArrayList<DsmpRow>();
		final Map<String,Integer> targetTermsMap = new HashMap<String,Integer>();
		final Map<String,Integer> candidateTermsMap = new HashMap<String,Integer>();
		final Map<String,List<DsmpRow>> rowsByCandidateName =
				new LinkedHashMap<String,List<DsmpRow>>();
		final Map<String,Map<String,Integer>> termsByCandidateName =
				new LinkedHashMap<String,Map<String,Integer>>();
		final Map<String,Integer> rowCountsByCandidateName =
				new LinkedHashMap<String,Integer>();
		final Set<String> distinctUsers = new HashSet<String>();
		final Set<String> distinctLabels = new HashSet<String>();
		final List<String> originalLabels = new ArrayList<String>();
		final List<String> warnings = new ArrayList<String>();
		List<TermScore> targetTerms = Collections.emptyList();
		List<TermScore> candidateTerms = Collections.emptyList();
		List<TermScore> sharedTerms = Collections.emptyList();
		List<CounterfactualInsight> counterfactualInsights =
				new ArrayList<CounterfactualInsight>();
		ComparisonReport comparison = ComparisonReport.empty();
		List<TermLocation> termLocations = new ArrayList<TermLocation>();
		long rowsScanned;
		boolean rowScanLimitReached;
		int targetRowCount;
		int candidateRowCount;
		int scorePercent;
		String scoreLabel = "No score";
		String statusTitle = "Evidence ready";
		String plainVerdict = "";
		String algorithmLensTitle = "";
		String algorithmLensDetail = "";
		String evidenceQuestion = "";
		boolean graphAvailable;
		int graphCommunities;
		String graphRelation = "\u2014";
		String graphExposure = "\u2014";
		String graphCandidateRank = "\u2014";
		String lastOriginalEdge = "No follow found";
		String lastRecommendedEdge = "No adoption yet";
		boolean recommendationMatchesGraphEdge;
		boolean sameAsOriginalLabel;
		String graphMessage = "The run graph appears when the run's record is written, at the end of the run.";
		String counterfactualSummary =
				"Counterfactual evidence will appear after source rows are inspected.";

		MutableReport(
				SimulatorHelpPanel.Snapshot snapshot,
				RecommendationEvent event,
				MatchEvidence selected,
				int rank)
		{
			this.snapshot = snapshot;
			this.event = event;
			this.selected = selected;
			this.rank = rank;
		}

		void finish()
		{
			this.scorePercent = scorePercent(selected.score);
			this.scoreLabel = scoreLabel(selected.score, scorePercent);
			this.statusTitle = event.status == RecommendationResultStatus.OK
					? "Recommendation evidence ready"
					: "Recommendation status: " + event.status;
			this.algorithmLensTitle = algorithmLensTitle(snapshot.algorithm);
			this.algorithmLensDetail = algorithmLensDetail(snapshot, event.candidateType);
			this.evidenceQuestion = questionFor(snapshot, event.candidateType);
			this.plainVerdict = plainVerdict(this);
			this.comparison = buildComparison(this);
			this.counterfactualInsights = buildCounterfactuals(this);
			this.termLocations = buildTermLocations(this);
			this.counterfactualSummary = summarizeCounterfactuals(this);
			if (rowsScanned == 0L)
			{
				warnings.add("No DSMP rows were sampled. Choose a text dataset or run Get Users first.");
			}
			else if (targetRowCount == 0)
			{
				warnings.add("The selected target was not found in the inspected DSMP rows.");
			}
			if (candidateRowCount == 0)
			{
				warnings.add("The recommended candidate had no direct supporting rows in the inspected dataset sample.");
			}
			if (rowScanLimitReached)
			{
				warnings.add("Dataset evidence was sampled from the first "
						+ formatCount(DATASET_SCAN_LIMIT)
						+ " rows to keep the GUI responsive.");
			}
		}

		EvidenceReport toReport()
		{
			return new EvidenceReport(this);
		}
	}

	private static void readDatasetEvidence(MutableReport state)
	{
		if (state.snapshot.datasetPath == null
				|| state.snapshot.datasetPath.trim().length() == 0)
		{
			state.warnings.add("No dataset path is available for source-row evidence.");
			return;
		}
		File file = new File(state.snapshot.datasetPath);
		if (!file.isFile())
		{
			state.warnings.add("Dataset file is not readable: " + state.snapshot.datasetPath);
			return;
		}
		Map<String,Integer> labelCountsForTarget = new LinkedHashMap<String,Integer>();
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(
				new FileInputStream(file), StandardCharsets.UTF_8), 256 * 1024))
		{
			String line;
			while ((line = reader.readLine()) != null)
			{
				if (state.rowsScanned >= DATASET_SCAN_LIMIT)
				{
					state.rowScanLimitReached = true;
					break;
				}
				state.rowsScanned++;
				DsmpRow row = parseRow(line);
				if (row == null)
				{
					continue;
				}
				state.distinctUsers.add(row.user);
				state.distinctLabels.add(row.followee);
				boolean isTarget = sameName(row.user, state.event.targetUser);
				boolean isCandidate = state.event.candidateType == RecommendationCandidateType.USER
						? sameName(row.user, state.selected.name)
						: sameName(row.followee, state.selected.name);
				if (isTarget)
				{
					state.targetRowCount++;
					increment(labelCountsForTarget, row.followee);
					addRowSample(state.targetRows, row);
					addTerms(state.targetTermsMap, row.text);
				}
				if (isCandidate)
				{
					state.candidateRowCount++;
					addRowSample(state.candidateRows, row);
					addTerms(state.candidateTermsMap, row.text);
				}
				for (MatchEvidence match : state.event.matches)
				{
					if (matchesCandidate(row, state.event.candidateType, match.name))
					{
						increment(state.rowCountsByCandidateName, match.name);
						List<DsmpRow> samples = state.rowsByCandidateName.get(match.name);
						if (samples == null)
						{
							samples = new ArrayList<DsmpRow>();
							state.rowsByCandidateName.put(match.name, samples);
						}
						addRowSample(samples, row);
						Map<String,Integer> terms = state.termsByCandidateName.get(match.name);
						if (terms == null)
						{
							terms = new HashMap<String,Integer>();
							state.termsByCandidateName.put(match.name, terms);
						}
						addTerms(terms, row.text);
					}
				}
			}
		}
		catch (IOException exception)
		{
			state.warnings.add("Could not read dataset evidence: " + exception.getMessage());
		}
		state.originalLabels.addAll(topKeys(labelCountsForTarget, 3));
	}

	private static boolean matchesCandidate(
			DsmpRow row,
			RecommendationCandidateType candidateType,
			String candidateName)
	{
		return candidateType == RecommendationCandidateType.USER
				? sameName(row.user, candidateName)
				: sameName(row.followee, candidateName);
	}

	private static void computeTermEvidence(MutableReport state)
	{
		state.targetTerms = topTerms(state.targetTermsMap, TERM_LIMIT);
		state.candidateTerms = topTerms(state.candidateTermsMap, TERM_LIMIT);
		ArrayList<TermScore> shared = new ArrayList<TermScore>();
		for (Map.Entry<String,Integer> entry : state.targetTermsMap.entrySet())
		{
			Integer candidateCount = state.candidateTermsMap.get(entry.getKey());
			if (candidateCount != null)
			{
				shared.add(new TermScore(
						entry.getKey(),
						Math.min(entry.getValue(), candidateCount.intValue()),
						entry.getValue() + candidateCount.intValue()));
			}
		}
		Collections.sort(shared, new Comparator<TermScore>()
		{
			public int compare(TermScore left, TermScore right)
			{
				int byOverlap = Integer.compare(right.value, left.value);
				if (byOverlap != 0)
				{
					return byOverlap;
				}
				int byTotal = Integer.compare(right.otherValue, left.otherValue);
				if (byTotal != 0)
				{
					return byTotal;
				}
				return left.term.compareTo(right.term);
			}
		});
		if (shared.size() > TERM_LIMIT)
		{
			shared = new ArrayList<TermScore>(shared.subList(0, TERM_LIMIT));
		}
		state.sharedTerms = shared;
	}

	/**
	 * Reads the candidate's place in the run graph of the latest recorded
	 * run: the target's observed follow and simulated adoption, whether the
	 * candidate is inside the target's community, its exposure and PageRank.
	 */
	private static void readGraphEvidence(MutableReport state)
	{
		RunGraphRecord.Loaded loaded = RunGraphRecord.latest();
		RunGraph graph = loaded == null ? null : loaded.graph;
		int target = graph == null ? -1 : graph.indexOf(state.event.targetUser);
		if (target < 0 || !graph.isTarget(target))
		{
			return;
		}
		RunGraphAnalysis analysis = loaded.analysis;
		state.graphAvailable = true;
		state.graphCommunities = graph.communityCount();
		int followee = graph.followeeOf(target);
		state.lastOriginalEdge = followee < 0 ? "unknown account" : graph.name(followee);
		RunGraph.Edge adoption = graph.adoptionOf(target);
		state.lastRecommendedEdge = adoption == null ? "no adoption" : graph.name(adoption.target);
		Boolean adoptedInside = adoption == null ? null : graph.inside(adoption);
		state.sameAsOriginalLabel = adoptedInside != null && adoptedInside.booleanValue();
		int candidate = graph.indexOf(state.selected.name);
		state.recommendationMatchesGraphEdge = adoption != null && candidate == adoption.target;
		Boolean inside = null;
		for (RunGraph.Edge edge : graph.recommendationsOf(target))
		{
			if (edge.target == candidate)
			{
				inside = graph.inside(edge);
			}
		}
		if (candidate >= 0)
		{
			int community = graph.candidateType == RecommendationCandidateType.FOLLOWEE
					? graph.anchoredCommunity(candidate) : graph.community(candidate);
			state.graphRelation = inside == null ? (community < 0 ? "Unknown community" : "Outside the top-"
					+ graph.cutoff) : inside.booleanValue() ? "Inside the community"
							: "Bridges to " + graph.communityName(community);
			int exposureRank = 1;
			int candidates = 0;
			for (int node = 0; node < graph.size(); node++)
			{
				if (graph.isCandidate(node))
				{
					candidates++;
					exposureRank += analysis.exposureByNode[node] > analysis.exposureByNode[candidate] ? 1 : 0;
				}
			}
			state.graphExposure = "Recommended to " + formatCount(analysis.recommendedIn[candidate])
					+ (analysis.recommendedIn[candidate] == 1 ? " target" : " targets") + " (#" + exposureRank
					+ " of " + formatCount(candidates) + ")";
			int pageRankPosition = 1;
			for (int node = 0; node < graph.size(); node++)
			{
				pageRankPosition += analysis.pageRank[node] > analysis.pageRank[candidate] ? 1 : 0;
			}
			state.graphCandidateRank = "#" + pageRankPosition + " of " + formatCount(graph.size());
		}
		StringBuilder message = new StringBuilder();
		message.append(state.event.targetUser).append(" follows ").append(state.lastOriginalEdge);
		if (adoption != null)
		{
			message.append(" and adopted ").append(state.lastRecommendedEdge).append(" (rank ").append(adoption.rank)
					.append(", ").append(state.sameAsOriginalLabel ? "inside its community" : "a bridge").append(")");
		}
		message.append(". This candidate: ").append(state.graphRelation.toLowerCase(Locale.US)).append('.');
		state.graphMessage = message.toString();
	}

	private static DsmpRow parseRow(String line)
	{
		if (line == null)
		{
			return null;
		}
		String[] fields = line.split("\\t", 6);
		if (fields.length < 6)
		{
			fields = line.trim().split("\\s+", 6);
		}
		if (fields.length < 6)
		{
			return null;
		}
		return new DsmpRow(
				fields[0].trim(),
				fields[1].trim(),
				fields[2].trim(),
				fields[3].trim(),
				fields[4].trim(),
				fields[5].trim());
	}

	private static void addRowSample(List<DsmpRow> rows, DsmpRow row)
	{
		if (rows.size() < ROW_SAMPLE_LIMIT)
		{
			rows.add(row);
		}
	}

	private static void addTerms(Map<String,Integer> counts, String text)
	{
		if (text == null)
		{
			return;
		}
		String[] terms = text.toLowerCase(Locale.US).split("[^a-z0-9]+");
		for (String term : terms)
		{
			if (term.length() < 3 || STOP_WORDS.contains(term))
			{
				continue;
			}
			increment(counts, term);
		}
	}

	private static List<TermScore> topTerms(Map<String,Integer> counts, int limit)
	{
		ArrayList<TermScore> terms = new ArrayList<TermScore>();
		for (Map.Entry<String,Integer> entry : counts.entrySet())
		{
			terms.add(new TermScore(entry.getKey(), entry.getValue(), 0));
		}
		Collections.sort(terms, new Comparator<TermScore>()
		{
			public int compare(TermScore left, TermScore right)
			{
				int byCount = Integer.compare(right.value, left.value);
				if (byCount != 0)
				{
					return byCount;
				}
				return left.term.compareTo(right.term);
			}
		});
		if (terms.size() > limit)
		{
			return new ArrayList<TermScore>(terms.subList(0, limit));
		}
		return terms;
	}

	private static List<String> topKeys(Map<String,Integer> counts, int limit)
	{
		ArrayList<Map.Entry<String,Integer>> entries =
				new ArrayList<Map.Entry<String,Integer>>(counts.entrySet());
		Collections.sort(entries, new Comparator<Map.Entry<String,Integer>>()
		{
			public int compare(Map.Entry<String,Integer> left, Map.Entry<String,Integer> right)
			{
				int byCount = Integer.compare(right.getValue(), left.getValue());
				if (byCount != 0)
				{
					return byCount;
				}
				return left.getKey().compareToIgnoreCase(right.getKey());
			}
		});
		ArrayList<String> keys = new ArrayList<String>();
		for (int i = 0; i < entries.size() && i < limit; i++)
		{
			keys.add(entries.get(i).getKey());
		}
		return keys;
	}

	private static String algorithmLensTitle(int algorithm)
	{
		switch (algorithm)
		{
			case RecommendationModeSupport.COS_SIM:
				return "Sparse text similarity evidence";
			case RecommendationModeSupport.K_MEANS:
				return "Cluster-membership evidence";
			case RecommendationModeSupport.SVM:
				return "Class-score distribution evidence";
			case RecommendationModeSupport.MLP:
				return "Neural output confidence evidence";
			case RecommendationModeSupport.DOC2VEC:
				return "Document-vector neighborhood evidence";
			case RecommendationModeSupport.COMMON_NEIGHBORS:
				return "Shared-neighbor overlap evidence";
			case RecommendationModeSupport.K_MEANS_EUCLIDEAN:
				return "Euclidean cluster-distance evidence";
			case RecommendationModeSupport.SCALABLE_SVM:
				return "Sparse linear class-score evidence";
			case RecommendationModeSupport.TEMPORAL_GRAPH_FOUNDATION:
				return "Temporal graph foundation evidence";
			default:
				return "Recommendation evidence";
		}
	}

	private static String algorithmLensDetail(
			SimulatorHelpPanel.Snapshot snapshot,
			RecommendationCandidateType type)
	{
		String output = type == RecommendationCandidateType.USER
				? "users/entities from column 5" : "followee/reference labels from column 1";
		switch (snapshot.algorithm)
		{
			case RecommendationModeSupport.COS_SIM:
				return "The simulator compares cleaned column-6 text profiles and ranks "
						+ output + ". Shared terms below are direct evidence from the dataset.";
			case RecommendationModeSupport.K_MEANS:
				return "The simulator clusters text profiles, then ranks candidates from compatible clusters. The terms shown here are a readable proxy for the cluster signal.";
			case RecommendationModeSupport.SVM:
				return "The Weka SMO path predicts followee/reference classes from column-6 text. This panel shows the class score, supporting rows, and graph side effect.";
			case RecommendationModeSupport.MLP:
				return "The neural path produces output probabilities from dense or sparse text features. The score is useful, but term evidence is approximate unless the selected engine exposes sparse weights.";
			case RecommendationModeSupport.DOC2VEC:
				return "Doc2Vec ranks nearby document vectors. This panel does not retrain the model; it shows the source documents and terms that make the vector neighbor interpretable.";
			case RecommendationModeSupport.COMMON_NEIGHBORS:
				return "Common Neighbors ranks candidates by shared textual/neighbor evidence. Shared terms and overlap are the most direct explanation.";
			case RecommendationModeSupport.TEMPORAL_GRAPH_FOUNDATION:
				return "Temporal Graph Foundation ranks users with a fused text, graph, time, bridge, debias, and ablation profile. Candidate details show the strongest signals directly.";
			case RecommendationModeSupport.K_MEANS_EUCLIDEAN:
				return "Euclidean K-Means uses distance to cluster structure. The terms shown are the readable text signal behind the selected cluster comparison.";
			case RecommendationModeSupport.SCALABLE_SVM:
				return "The scalable linear SVM path scores sparse text features against class labels. This is an experimental linear classifier explanation, separate from Weka SMO.";
			default:
				return "The panel explains the result by combining source rows, term overlap, algorithm context, and graph side effects.";
		}
	}

	private static String questionFor(
			SimulatorHelpPanel.Snapshot snapshot,
			RecommendationCandidateType type)
	{
		if (type == RecommendationCandidateType.USER)
		{
			return "For the selected target, which other user/entity has the closest text behavior?";
		}
		return "For the selected target, which followee/reference label should be ranked highest?";
	}

	private static String plainVerdict(MutableReport state)
	{
		StringBuilder builder = new StringBuilder();
		builder.append(state.snapshot.algorithmName)
				.append(" ranked ")
				.append(state.selected.name)
				.append(" for ")
				.append(state.event.targetUser)
				.append(".");
		if (!state.sharedTerms.isEmpty())
		{
			builder.append(" The clearest text evidence is shared language such as ");
			for (int i = 0; i < state.sharedTerms.size() && i < 5; i++)
			{
				if (i > 0)
				{
					builder.append(i == Math.min(5, state.sharedTerms.size()) - 1
							? ", and " : ", ");
				}
				builder.append(state.sharedTerms.get(i).term);
			}
			builder.append(".");
		}
		else if (!state.targetTerms.isEmpty())
		{
			builder.append(" The target text is dominated by terms like ");
			for (int i = 0; i < state.targetTerms.size() && i < 4; i++)
			{
				if (i > 0)
				{
					builder.append(", ");
				}
				builder.append(state.targetTerms.get(i).term);
			}
			builder.append(", but the candidate had little direct text overlap in the sampled rows.");
		}
		if (state.graphAvailable)
		{
			builder.append(" In the run graph, ");
			if (state.recommendationMatchesGraphEdge)
			{
				builder.append(state.event.targetUser).append(" adopted this recommendation, ")
						.append(state.sameAsOriginalLabel ? "staying inside its community."
								: "bridging to another community.");
			}
			else
			{
				builder.append(state.event.targetUser).append(" adopted ").append(state.lastRecommendedEdge)
						.append(" instead; this candidate is ").append(state.graphRelation.toLowerCase(Locale.US))
						.append('.');
			}
		}
		if (state.event.status != RecommendationResultStatus.OK)
		{
			builder.append(" The recommendation status was ")
					.append(state.event.status)
					.append(", so treat this as diagnostic evidence.");
		}
		return builder.toString();
	}

	private static ComparisonReport buildComparison(MutableReport state)
	{
		if (state.event.matches.size() < 2)
		{
			return ComparisonReport.empty();
		}
		int selectedIndex = Math.max(0, Math.min(state.rank - 1, state.event.matches.size() - 1));
		int alternateIndex = selectedIndex == 0 ? 1 : 0;
		MatchEvidence alternate = state.event.matches.get(alternateIndex);
		Map<String,Integer> alternateTerms = state.termsByCandidateName.get(alternate.name);
		if (alternateTerms == null)
		{
			alternateTerms = Collections.emptyMap();
		}
		List<TermScore> alternateShared =
				sharedTermsFor(state.targetTermsMap, alternateTerms, TERM_LIMIT);
		int selectedSupport = supportScore(state.targetTermsMap, state.candidateTermsMap);
		int alternateSupport = supportScore(state.targetTermsMap, alternateTerms);
		String scoreGap = scoreGap(state.selected.score, alternate.score);
		StringBuilder verdict = new StringBuilder();
		verdict.append("Rank ")
				.append(state.rank)
				.append(" is compared with rank ")
				.append(alternate.rank)
				.append(". ");
		if (selectedSupport > alternateSupport)
		{
			verdict.append(state.selected.name)
					.append(" has stronger inspected text overlap (")
					.append(selectedSupport)
					.append(" vs ")
					.append(alternateSupport)
					.append(").");
		}
		else if (selectedSupport < alternateSupport)
		{
			verdict.append(alternate.name)
					.append(" has more inspected text overlap, so the model score is likely using information beyond simple shared terms.");
		}
		else
		{
			verdict.append("The inspected text overlap is tied, so the final ordering comes from the selected algorithm score and merge path.");
		}
		if (scoreGap.length() > 0)
		{
			verdict.append(" Reported score gap: ").append(scoreGap).append(".");
		}
		return new ComparisonReport(
				true,
				state.selected,
				alternate,
				scoreGap,
				selectedSupport,
				alternateSupport,
				state.sharedTerms,
				alternateShared,
				verdict.toString());
	}

	private static List<CounterfactualInsight> buildCounterfactuals(MutableReport state)
	{
		ArrayList<CounterfactualInsight> insights =
				new ArrayList<CounterfactualInsight>();
		int support = supportScore(state.targetTermsMap, state.candidateTermsMap);
		String fidelity = counterfactualFidelity(state.snapshot.algorithm);
		MatchEvidence alternate = state.comparison.available ? state.comparison.right : null;
		int alternateSupport = state.comparison.available ? state.comparison.rightSupport : 0;
		if (support <= 0)
		{
			insights.add(new CounterfactualInsight(
					"No single shared text lever found",
					"Inspect more rows or compare model scores",
					"The sampled source rows do not expose a shared-term change that would clearly move this recommendation.",
					"This can happen for vector, neural, or class-score models where the decisive evidence is not visible as direct word overlap.",
					fidelity,
					0,
					false));
			return insights;
		}
		for (int i = 0; i < state.sharedTerms.size() && i < 3; i++)
		{
			TermScore term = state.sharedTerms.get(i);
			int pressure = percent(term.value, support);
			int remaining = Math.max(0, support - term.value);
			boolean switchLikely = alternate != null
					&& alternateSupport > 0
					&& remaining <= alternateSupport;
			String outcome = switchLikely
					? "The visible text evidence could drop below "
							+ alternate.name + ", so the top recommendation may change."
					: state.selected.name
							+ " probably remains supported by other inspected text evidence.";
			insights.add(new CounterfactualInsight(
					"Remove shared term: " + term.term,
					"Remove or downweight the term '" + term.term + "' from the target/candidate evidence.",
					outcome,
					"Estimated text support would move from "
							+ support + " to " + remaining + ".",
					fidelity,
					pressure,
					switchLikely));
		}
		RowContribution targetRow = strongestRowContribution(
				state.targetRows,
				state.candidateTermsMap);
		RowContribution candidateRow = strongestRowContribution(
				state.candidateRows,
				state.targetTermsMap);
		RowContribution strongest = targetRow.value >= candidateRow.value
				? targetRow : candidateRow;
		if (strongest.row != null)
		{
			int pressure = percent(strongest.value, support);
			boolean switchLikely = alternate != null
					&& alternateSupport > 0
					&& support - strongest.value <= alternateSupport;
			insights.add(new CounterfactualInsight(
					pressure >= 45 ? "Fragile single-row dependency"
							: "Single-row stress test",
					"Remove the most supportive sampled "
							+ (strongest == targetRow ? "target" : "candidate")
							+ " row.",
					switchLikely
							? "That one row carries enough visible evidence that the alternate could overtake it."
							: "Removing the strongest sampled row still leaves enough visible support for the current result.",
					"Row text: " + strongest.row.text,
					fidelity,
					pressure,
					switchLikely));
		}
		int maxPressure = 0;
		boolean anySwitch = false;
		for (CounterfactualInsight insight : insights)
		{
			maxPressure = Math.max(maxPressure, insight.pressurePercent);
			anySwitch = anySwitch || insight.likelySwitch;
		}
		insights.add(new CounterfactualInsight(
				anySwitch ? "Recommendation is sensitive" : "Recommendation is stable in sampled evidence",
				"Apply the listed changes as a what-if audit, not as a dataset edit.",
				anySwitch
						? "At least one small inspected evidence change could plausibly affect the top result."
						: "No single inspected term or sampled row was enough to overturn the current explanation.",
				"Strongest single lever carries " + maxPressure
						+ "% of the visible support.",
				fidelity,
				maxPressure,
				anySwitch));
		return insights;
	}

	private static String summarizeCounterfactuals(MutableReport state)
	{
		if (state.counterfactualInsights.isEmpty())
		{
			return "No counterfactual probes were available for this recommendation.";
		}
		CounterfactualInsight last =
				state.counterfactualInsights.get(state.counterfactualInsights.size() - 1);
		return last.title + ": " + last.outcome;
	}

	private static List<TermLocation> buildTermLocations(MutableReport state)
	{
		ArrayList<TermLocation> locations = new ArrayList<TermLocation>();
		HashSet<String> seen = new HashSet<String>();
		int support = supportScore(state.targetTermsMap, state.candidateTermsMap);
		addTermLocations(locations, seen, state, state.sharedTerms, "shared", support);
		addTermLocations(locations, seen, state, state.targetTerms, "target-only", support);
		addTermLocations(locations, seen, state, state.candidateTerms, "candidate-only", support);
		return locations;
	}

	private static void addTermLocations(
			List<TermLocation> locations,
			Set<String> seen,
			MutableReport state,
			List<TermScore> terms,
			String preferredRole,
			int support)
	{
		for (int i = 0; i < terms.size() && locations.size() < 18; i++)
		{
			String term = terms.get(i).term;
			if (seen.contains(term))
			{
				continue;
			}
			seen.add(term);
			int targetCount = valueFor(state.targetTermsMap, term);
			int candidateCount = valueFor(state.candidateTermsMap, term);
			String role;
			if (targetCount > 0 && candidateCount > 0)
			{
				role = "shared";
			}
			else if (targetCount > 0)
			{
				role = "target-only";
			}
			else
			{
				role = "candidate-only";
			}
			if (!"shared".equals(role) && "shared".equals(preferredRole))
			{
				continue;
			}
			int contribution = targetCount > 0 && candidateCount > 0
					? Math.min(targetCount, candidateCount) : 0;
			int pressure = percent(contribution, support);
			String effect;
			if ("shared".equals(role))
			{
				effect = "Removing this shared term removes about "
						+ pressure + "% of the visible text support.";
			}
			else if ("target-only".equals(role))
			{
				effect = "This term explains the target profile but does not directly support the selected candidate in sampled rows.";
			}
			else
			{
				effect = "This term explains the candidate profile but was not found in the target sampled rows.";
			}
			locations.add(new TermLocation(
					term,
					role,
					targetCount,
					candidateCount,
					pressure,
					effect,
					rowsContaining(state.targetRows, term),
					rowsContaining(state.candidateRows, term)));
		}
	}

	private static List<TermScore> sharedTermsFor(
			Map<String,Integer> targetTerms,
			Map<String,Integer> candidateTerms,
			int limit)
	{
		ArrayList<TermScore> shared = new ArrayList<TermScore>();
		for (Map.Entry<String,Integer> entry : targetTerms.entrySet())
		{
			Integer candidateCount = candidateTerms.get(entry.getKey());
			if (candidateCount != null)
			{
				shared.add(new TermScore(
						entry.getKey(),
						Math.min(entry.getValue(), candidateCount.intValue()),
						entry.getValue() + candidateCount.intValue()));
			}
		}
		Collections.sort(shared, new Comparator<TermScore>()
		{
			public int compare(TermScore left, TermScore right)
			{
				int byOverlap = Integer.compare(right.value, left.value);
				if (byOverlap != 0)
				{
					return byOverlap;
				}
				int byTotal = Integer.compare(right.otherValue, left.otherValue);
				if (byTotal != 0)
				{
					return byTotal;
				}
				return left.term.compareTo(right.term);
			}
		});
		if (shared.size() > limit)
		{
			return new ArrayList<TermScore>(shared.subList(0, limit));
		}
		return shared;
	}

	private static int supportScore(
			Map<String,Integer> targetTerms,
			Map<String,Integer> candidateTerms)
	{
		int score = 0;
		for (Map.Entry<String,Integer> entry : targetTerms.entrySet())
		{
			Integer candidateCount = candidateTerms.get(entry.getKey());
			if (candidateCount != null)
			{
				score += Math.min(entry.getValue(), candidateCount.intValue());
			}
		}
		return score;
	}

	private static RowContribution strongestRowContribution(
			List<DsmpRow> rows,
			Map<String,Integer> peerTerms)
	{
		RowContribution best = new RowContribution(null, 0);
		for (DsmpRow row : rows)
		{
			HashMap<String,Integer> rowTerms = new HashMap<String,Integer>();
			addTerms(rowTerms, row.text);
			int value = supportScore(rowTerms, peerTerms);
			if (value > best.value)
			{
				best = new RowContribution(row, value);
			}
		}
		return best;
	}

	private static List<DsmpRow> rowsContaining(List<DsmpRow> rows, String term)
	{
		ArrayList<DsmpRow> matches = new ArrayList<DsmpRow>();
		for (DsmpRow row : rows)
		{
			if (rowContainsTerm(row, term))
			{
				matches.add(row);
			}
		}
		return matches;
	}

	private static boolean rowContainsTerm(DsmpRow row, String term)
	{
		if (row == null || row.text == null || term == null)
		{
			return false;
		}
		String needle = term.toLowerCase(Locale.US);
		String[] terms = row.text.toLowerCase(Locale.US).split("[^a-z0-9]+");
		for (String value : terms)
		{
			if (needle.equals(value))
			{
				return true;
			}
		}
		return false;
	}

	private static String counterfactualFidelity(int algorithm)
	{
		if (algorithm == RecommendationModeSupport.COS_SIM
				|| algorithm == RecommendationModeSupport.COMMON_NEIGHBORS)
		{
			return "Direct overlap probe";
		}
		if (algorithm == RecommendationModeSupport.TEMPORAL_GRAPH_FOUNDATION)
		{
			return "Model-side ablation ranks";
		}
		return "Evidence-side proxy; model was not rerun";
	}

	private static int valueFor(Map<String,Integer> values, String key)
	{
		Integer value = values.get(key);
		return value == null ? 0 : value.intValue();
	}

	private static int percent(int value, int total)
	{
		if (total <= 0)
		{
			return 0;
		}
		return Math.max(0, Math.min(100,
				(int) Math.round((double) value * 100.0 / (double) total)));
	}

	private static String scoreGap(String left, String right)
	{
		Double leftValue = parseScore(left);
		Double rightValue = parseScore(right);
		if (leftValue == null || rightValue == null)
		{
			return "";
		}
		return String.format(Locale.US, "%.4f", Double.valueOf(Math.abs(
				leftValue.doubleValue() - rightValue.doubleValue())));
	}

	private static Double parseScore(String score)
	{
		if (score == null || score.trim().length() == 0)
		{
			return null;
		}
		try
		{
			return Double.valueOf(Double.parseDouble(score.trim().replace("%", "")));
		}
		catch (NumberFormatException exception)
		{
			return null;
		}
	}

	private static int scorePercent(String score)
	{
		if (score == null || score.trim().length() == 0)
		{
			return 0;
		}
		String cleaned = score.trim().replace("%", "");
		try
		{
			double value = Double.parseDouble(cleaned);
			if (Double.isNaN(value) || Double.isInfinite(value))
			{
				return 0;
			}
			if (value <= 1.0 && value >= 0.0)
			{
				value *= 100.0;
			}
			return Math.max(0, Math.min(100, (int) Math.round(Math.abs(value))));
		}
		catch (NumberFormatException exception)
		{
			return 0;
		}
	}

	private static String scoreLabel(String rawScore, int scorePercent)
	{
		if (rawScore == null || rawScore.trim().length() == 0)
		{
			return "No numeric score reported";
		}
		return rawScore.trim() + " (" + scorePercent + "% visual scale)";
	}

	private static void increment(Map<String,Integer> map, String key)
	{
		map.put(key, Integer.valueOf(map.containsKey(key)
				? map.get(key).intValue() + 1 : 1));
	}

	private static <T> List<T> immutableRows(List<T> rows)
	{
		return Collections.unmodifiableList(new ArrayList<T>(rows));
	}

	private static List<TermScore> immutableTerms(List<TermScore> rows)
	{
		return Collections.unmodifiableList(new ArrayList<TermScore>(rows));
	}

	private static boolean sameName(String left, String right)
	{
		return left != null && right != null
				&& left.trim().equalsIgnoreCase(right.trim());
	}

	private static String clean(String value, String fallback)
	{
		return value == null || value.trim().length() == 0
				? fallback : value.trim();
	}

	static String formatCount(long value)
	{
		return String.format(Locale.US, "%,d", Long.valueOf(value));
	}

	private static Set<String> buildStopWords()
	{
		HashSet<String> words = new HashSet<String>();
		String[] values = {
				"the", "and", "for", "with", "from", "that", "this", "are",
				"was", "were", "has", "have", "had", "not", "but", "you",
				"your", "they", "their", "our", "all", "any", "can", "will",
				"one", "two", "new", "old", "out", "use", "using", "into",
				"about", "after", "before", "over", "under", "more", "less",
				"retweet", "http", "https", "www", "com"
		};
		for (String value : values)
		{
			words.add(value);
		}
		return Collections.unmodifiableSet(words);
	}

	private static final class RowContribution
	{
		final DsmpRow row;
		final int value;

		RowContribution(DsmpRow row, int value)
		{
			this.row = row;
			this.value = Math.max(0, value);
		}
	}
}
