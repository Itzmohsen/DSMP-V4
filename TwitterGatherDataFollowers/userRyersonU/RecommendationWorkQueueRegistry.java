package TwitterGatherDataFollowers.userRyersonU;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only views of a run's work phases for the panels.
 *
 * The phases themselves live in the run's {@link SchedulingRunContext}; each
 * is a {@link WorkPhase} over the shared {@link WorkDispatcher}. This class
 * turns a phase (or the input-delivery tracker) into a {@link QueueSnapshot}.
 */
final class RecommendationWorkQueueRegistry
{
	/** Phase keys used by the live simulator. */
	static final String INPUT = "input";
	static final String DOC2VEC = "doc2vec-shards";
	static final String CANDIDATES = "candidate-shards";
	static final String SVM_TARGETS = "svm-targets";
	static final String MLP_TRAINING = "mlp-training";

	static final class QueueEvent
	{
		private final long time;
		private final int nodeIndex;
		private final int shardNumber;
		private final String action;
		private final String detail;

		private QueueEvent(long time, int nodeIndex, int shardNumber, String action, String detail)
		{
			this.time = Math.max(0L, time);
			this.nodeIndex = Math.max(0, nodeIndex);
			this.shardNumber = Math.max(0, shardNumber);
			this.action = action == null ? "" : action;
			this.detail = detail == null ? "" : detail;
		}

		long getTime()
		{
			return time;
		}

		int getNodeIndex()
		{
			return nodeIndex;
		}

		int getShardNumber()
		{
			return shardNumber;
		}

		String getAction()
		{
			return action;
		}

		String getDetail()
		{
			return detail;
		}
	}

	static final class QueueSnapshot
	{
		private final String key;
		private final int totalShards;
		private final int waitingCount;
		private final int activeCount;
		private final int completedCount;
		private final int failedCount;
		private final Map<Integer,List<Integer>> activeByNode;
		private final Map<Integer,Integer> claimedByNode;
		private final Map<Integer,Integer> completedByNode;
		private final List<Integer> waitingPreview;
		private final List<Integer> completedPreview;
		private final List<QueueEvent> events;
		private final String policyLabel;

		private QueueSnapshot(
				String key,
				int totalShards,
				int waitingCount,
				int activeCount,
				int completedCount,
				int failedCount,
				Map<Integer,List<Integer>> activeByNode,
				Map<Integer,Integer> claimedByNode,
				Map<Integer,Integer> completedByNode,
				List<Integer> waitingPreview,
				List<Integer> completedPreview,
				List<QueueEvent> events,
				String policyLabel)
		{
			this.key = key == null ? "" : key;
			this.totalShards = Math.max(0, totalShards);
			this.waitingCount = Math.max(0, waitingCount);
			this.activeCount = Math.max(0, activeCount);
			this.completedCount = Math.max(0, completedCount);
			this.failedCount = Math.max(0, failedCount);
			this.activeByNode = Collections.unmodifiableMap(activeByNode);
			this.claimedByNode = Collections.unmodifiableMap(claimedByNode);
			this.completedByNode = Collections.unmodifiableMap(completedByNode);
			this.waitingPreview = Collections.unmodifiableList(waitingPreview);
			this.completedPreview = Collections.unmodifiableList(completedPreview);
			this.events = Collections.unmodifiableList(events);
			this.policyLabel = policyLabel == null ? "" : policyLabel;
		}

		String getKey()
		{
			return key;
		}

		int getTotalShards()
		{
			return totalShards;
		}

		int getWaitingCount()
		{
			return waitingCount;
		}

		int getActiveCount()
		{
			return activeCount;
		}

		int getCompletedCount()
		{
			return completedCount;
		}

		int getFailedCount()
		{
			return failedCount;
		}

		Map<Integer,List<Integer>> getActiveByNode()
		{
			return activeByNode;
		}

		Map<Integer,Integer> getClaimedByNode()
		{
			return claimedByNode;
		}

		Map<Integer,Integer> getCompletedByNode()
		{
			return completedByNode;
		}

		List<Integer> getWaitingPreview()
		{
			return waitingPreview;
		}

		List<Integer> getCompletedPreview()
		{
			return completedPreview;
		}

		List<QueueEvent> getEvents()
		{
			return events;
		}

		String getPolicyLabel()
		{
			return policyLabel;
		}
	}

	private RecommendationWorkQueueRegistry()
	{
	}

	static QueueSnapshot emptyQueueSnapshot()
	{
		return empty("empty-live-queue");
	}

	/** Label of the input-delivery snapshot. */
	static final String INPUT_LABEL = "Input users";

	/** True when the snapshot shows input delivery rather than a work phase. */
	static boolean isInputSnapshot(QueueSnapshot snapshot)
	{
		return snapshot != null && (INPUT_LABEL.equals(snapshot.getKey()) || INPUT.equals(snapshot.getKey()));
	}

	/** The most recently opened phase of the run (empty id: latest run). */
	static QueueSnapshot latestPhaseSnapshot(String runId)
	{
		SchedulingRunContext context = resolve(runId);
		if (context == null)
		{
			return emptyQueueSnapshot();
		}
		SchedulingRunContext.PhaseEntry entry = context.latestPhase();
		return entry == null ? inputSnapshot(context) : fromPhase(entry);
	}

	/** The phase whose key starts with {@code keyPrefix}, or an empty snapshot. */
	static QueueSnapshot snapshotPhase(String runId, String keyPrefix)
	{
		SchedulingRunContext context = resolve(runId);
		if (context == null)
		{
			return empty(keyPrefix);
		}
		if (INPUT.equals(keyPrefix))
		{
			return inputSnapshot(context);
		}
		SchedulingRunContext.PhaseEntry match = null;
		for (SchedulingRunContext.PhaseEntry entry : context.phases())
		{
			if (entry.getKey().startsWith(keyPrefix))
			{
				match = entry;
			}
		}
		return match == null ? empty(keyPrefix) : fromPhase(match);
	}

	static QueueSnapshot snapshotDoc2VecQueue(String runId, int ignoredNodes, int ignoredShards)
	{
		return snapshotPhase(runId, DOC2VEC);
	}

	static QueueSnapshot snapshotExperimentalCandidateQueue(
			String runId, String ignoredAlgorithm, int ignoredNodes, int ignoredShards)
	{
		return snapshotPhase(runId, CANDIDATES);
	}

	static QueueSnapshot snapshotTargetUserQueue(
			String runId, String ignoredAlgorithm, int ignoredNodes, int ignoredTargets)
	{
		return snapshotPhase(runId, SVM_TARGETS);
	}

	static QueueSnapshot snapshotInputBatchQueue(String runId, int ignoredNodes, int ignoredBatches)
	{
		return snapshotPhase(runId, INPUT);
	}

	static QueueSnapshot snapshotInputBatchQueue(String runId, int ignoredNodes)
	{
		return snapshotPhase(runId, INPUT);
	}

	private static QueueSnapshot fromPhase(SchedulingRunContext.PhaseEntry entry)
	{
		WorkDispatcher dispatcher = entry.getPhase().getDispatcher();
		LinkedHashMap<Integer,List<Integer>> active = new LinkedHashMap<Integer,List<Integer>>();
		LinkedHashMap<Integer,Integer> claimed = new LinkedHashMap<Integer,Integer>();
		LinkedHashMap<Integer,Integer> completed = new LinkedHashMap<Integer,Integer>();
		for (WorkDispatcher.NodeStats stats : dispatcher.nodeStats())
		{
			Integer node = Integer.valueOf(stats.getNode());
			if (!stats.getRunningUnits().isEmpty())
			{
				active.put(node, new ArrayList<Integer>(stats.getRunningUnits()));
			}
			claimed.put(node, Integer.valueOf(stats.getAssigned()));
			completed.put(node, Integer.valueOf(stats.getCompleted()));
		}
		ArrayList<QueueEvent> events = new ArrayList<QueueEvent>();
		List<WorkDispatcher.Event> source = dispatcher.events();
		int from = Math.max(0, source.size() - 80);
		for (int index = source.size() - 1; index >= from; index--)
		{
			WorkDispatcher.Event event = source.get(index);
			events.add(new QueueEvent(
					entry.getOpenedAtMillis() + (long)event.getTime(),
					event.getNode(),
					event.getUnitId(),
					event.getType(),
					event.getDetail()));
		}
		return new QueueSnapshot(
				entry.getLabel(),
				dispatcher.getUnitCount(),
				dispatcher.count(WorkDispatcher.UnitState.BACKLOG)
						+ dispatcher.count(WorkDispatcher.UnitState.RESERVED),
				dispatcher.count(WorkDispatcher.UnitState.RUNNING),
				dispatcher.getCommittedCount(),
				dispatcher.count(WorkDispatcher.UnitState.FAILED),
				active,
				claimed,
				completed,
				new ArrayList<Integer>(dispatcher.waitingUnits(24)),
				new ArrayList<Integer>(dispatcher.committedUnits(24)),
				events,
				dispatcher.getPolicy().getLabel()
						+ (dispatcher.getPolicy().isDynamic() ? " (dynamic)" : " (static)"));
	}

	private static QueueSnapshot inputSnapshot(SchedulingRunContext context)
	{
		Map<String,Integer> inFlight = context.inputInFlightByUser();
		Map<String,Integer> delivered = context.inputDeliveredByUser();
		int planned = context.getInputUsersPlanned();
		if (planned <= 0 && inFlight.isEmpty() && delivered.isEmpty())
		{
			return empty(INPUT);
		}
		LinkedHashMap<Integer,List<Integer>> active = new LinkedHashMap<Integer,List<Integer>>();
		LinkedHashMap<Integer,Integer> claimed = new LinkedHashMap<Integer,Integer>();
		LinkedHashMap<Integer,Integer> completed = new LinkedHashMap<Integer,Integer>();
		int ordinal = 0;
		for (Map.Entry<String,Integer> entry : delivered.entrySet())
		{
			ordinal++;
			increment(claimed, entry.getValue());
			increment(completed, entry.getValue());
		}
		for (Map.Entry<String,Integer> entry : inFlight.entrySet())
		{
			ordinal++;
			increment(claimed, entry.getValue());
			List<Integer> list = active.get(entry.getValue());
			if (list == null)
			{
				list = new ArrayList<Integer>();
				active.put(entry.getValue(), list);
			}
			list.add(Integer.valueOf(ordinal));
		}
		int total = Math.max(planned, delivered.size() + inFlight.size());
		ArrayList<QueueEvent> events = new ArrayList<QueueEvent>();
		for (SchedulingRunContext.ControlEvent event : context.events())
		{
			if (event.getAction().startsWith("input"))
			{
				events.add(new QueueEvent(event.getTime(), 0, 0, event.getAction(), event.getDetail()));
			}
		}
		return new QueueSnapshot(
				INPUT_LABEL,
				total,
				Math.max(0, total - delivered.size() - inFlight.size()),
				inFlight.size(),
				delivered.size(),
				0,
				active,
				claimed,
				completed,
				new ArrayList<Integer>(),
				new ArrayList<Integer>(),
				events,
				"per-user routing");
	}

	private static void increment(Map<Integer,Integer> counts, Integer node)
	{
		Integer current = counts.get(node);
		counts.put(node, Integer.valueOf(current == null ? 1 : current.intValue() + 1));
	}

	private static QueueSnapshot empty(String key)
	{
		return new QueueSnapshot(
				key,
				0,
				0,
				0,
				0,
				0,
				new LinkedHashMap<Integer,List<Integer>>(),
				new LinkedHashMap<Integer,Integer>(),
				new LinkedHashMap<Integer,Integer>(),
				new ArrayList<Integer>(),
				new ArrayList<Integer>(),
				new ArrayList<QueueEvent>(),
				"");
	}

	private static SchedulingRunContext resolve(String runId)
	{
		if (runId == null || runId.trim().length() == 0)
		{
			return SchedulingRuntime.latest();
		}
		return SchedulingRuntime.get(runId.trim());
	}
}
