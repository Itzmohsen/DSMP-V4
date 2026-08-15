package TwitterGatherDataFollowers.userRyersonU;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Read-only telemetry for recommender-node execution. It never participates in
 * JADE messaging or recommendation decisions.
 */
final class NodeExecutionMonitor
{
	enum State
	{
		PROVISIONING,
			READY,
			RECEIVING,
			WAITING_FOR_INPUT,
			PREPROCESSING,
		WAITING_FOR_PEERS,
		FEATURES,
		WAITING_FOR_MEMORY,
		WAITING_FOR_SHARED_MODEL,
		MODEL_RUNNING,
		PUBLISHING,
		COMPLETE,
		FAILED,
		STOPPED
	}

	interface Listener
	{
		void onSnapshot(Snapshot snapshot);
	}

	private static final Object LOCK = new Object();
	private static final Map<String,MutableNode> NODES =
			new LinkedHashMap<String,MutableNode>();
	private static final CopyOnWriteArrayList<Listener> LISTENERS =
			new CopyOnWriteArrayList<Listener>();
	private static final long PROGRESS_PUBLISH_INTERVAL_MS = 250L;
	private static final int MAX_NODE_EVENTS = 50;

	private static int requestedNodes;
	private static int runSequence;
	private static String algorithmName = "Not configured";
	private static String runId;
	private static String runPhase = "Configure and initialize an experiment.";
	private static long experimentStartedAt;
	private static long runStartedAt;

	private NodeExecutionMonitor()
	{
	}

	static void beginExperiment(int nodeCount, String algorithm)
	{
		Snapshot snapshot;
		synchronized (LOCK)
		{
			requestedNodes = Math.max(1, nodeCount);
			NodeCapacityRegistry.configureForNodeCount(
					requestedNodes,
					RuntimeResourceSettings.currentMaxHeapMb());
			algorithmName = clean(algorithm, "Recommendation model");
			runId = null;
			runSequence = 0;
			experimentStartedAt = System.currentTimeMillis();
			runStartedAt = 0L;
			runPhase = "Creating " + requestedNodes + " recommender node"
					+ (requestedNodes == 1 ? "." : "s.");
			NODES.clear();
			for (int index = 1; index <= requestedNodes; index++)
			{
				String nodeName = nodeName(index);
				NODES.put(nodeName, new MutableNode(
						index,
						nodeName,
						State.PROVISIONING,
						"Provisioning",
						"Waiting for the JADE agent to start."));
			}
			snapshot = snapshotLocked();
		}
		publish(snapshot);
	}

	static void refreshCapacityProfiles()
	{
		Snapshot snapshot;
		synchronized (LOCK)
		{
			snapshot = snapshotLocked();
		}
		publish(snapshot);
	}

	static void reset()
	{
		Snapshot snapshot;
		synchronized (LOCK)
		{
			requestedNodes = 0;
			runSequence = 0;
			algorithmName = "Not configured";
			runId = null;
			runPhase = "Configure and initialize an experiment.";
			experimentStartedAt = 0L;
			runStartedAt = 0L;
			NODES.clear();
			snapshot = snapshotLocked();
		}
		publish(snapshot);
	}

	static void nodeReady(String owner, int expectedTweets)
	{
		update(owner, State.READY, "Node online",
				"Agent registered and ready for a simulation run.",
				null, Integer.valueOf(expectedTweets), null, true);
	}

	static void beginNodeRun(String owner, String newRunId, int expectedTweets)
	{
		Snapshot snapshot;
		synchronized (LOCK)
		{
			if (newRunId != null && !newRunId.equals(runId))
			{
				runId = newRunId;
				runSequence++;
				runStartedAt = System.currentTimeMillis();
				runPhase = "Distributing dataset records to recommender nodes.";
				for (MutableNode node : NODES.values())
				{
					node.resetForRun();
				}
			}
			MutableNode node = findNodeLocked(owner);
			if (node == null)
			{
				return;
			}
			node.expectedTweets = Math.max(0, expectedTweets);
			node.transition(
					State.RECEIVING,
					"Receiving records",
					node.expectedTweets > 0
							? "Waiting for assigned dataset records."
							: "Waiting for the run input stream.");
			snapshot = snapshotLocked();
		}
		publish(snapshot);
	}

	static void updateTweets(String owner, int receivedTweets, int expectedTweets)
	{
		Snapshot snapshot = null;
		synchronized (LOCK)
		{
			MutableNode node = findNodeLocked(owner);
			if (node == null || node.isTerminal())
			{
				return;
			}
			int previousPercent = percent(node.receivedTweets, node.expectedTweets);
			node.receivedTweets = Math.max(node.receivedTweets, receivedTweets);
			node.expectedTweets = Math.max(0, expectedTweets);
			node.state = State.RECEIVING;
			node.stage = "Receiving records";
			node.detail = node.expectedTweets > 0
					? formatCount(node.receivedTweets) + " of "
							+ formatCount(node.expectedTweets) + " records received."
					: formatCount(node.receivedTweets) + " records received.";
			node.updatedAt = System.currentTimeMillis();

			int currentPercent = percent(node.receivedTweets, node.expectedTweets);
			long now = System.currentTimeMillis();
			boolean thresholdCrossed = currentPercent != previousPercent
					&& (currentPercent % 2 == 0 || currentPercent >= 100);
			if (thresholdCrossed
					|| now - node.lastProgressPublishedAt
							>= PROGRESS_PUBLISH_INTERVAL_MS)
			{
				node.lastProgressPublishedAt = now;
				snapshot = snapshotLocked();
			}
		}
		if (snapshot != null)
		{
			publish(snapshot);
		}
	}

	static void updateRuntimeInput(String owner, int retainedTweets)
	{
		Snapshot snapshot = null;
		synchronized (LOCK)
		{
			MutableNode node = findNodeLocked(owner);
			if (node == null || node.isTerminal())
			{
				return;
			}
			int previousTweets = node.receivedTweets;
			node.receivedTweets = Math.max(node.receivedTweets, retainedTweets);
			node.expectedTweets = 0;
			node.state = State.RECEIVING;
			node.stage = "Receiving live-routed records";
			node.detail = "Retained " + formatCount(node.receivedTweets)
					+ " records so far; shared input queue controls the final count.";
			node.updatedAt = System.currentTimeMillis();

			long now = System.currentTimeMillis();
			if (node.receivedTweets != previousTweets
					|| now - node.lastProgressPublishedAt
							>= PROGRESS_PUBLISH_INTERVAL_MS)
			{
				node.lastProgressPublishedAt = now;
				snapshot = snapshotLocked();
			}
		}
		if (snapshot != null)
		{
			publish(snapshot);
		}
	}

	static void preprocessing(String owner, int receivedTweets, int expectedTweets)
	{
		update(owner, State.PREPROCESSING, "Preprocessing text",
				"Cleaning and aggregating " + formatCount(receivedTweets)
						+ " received records.",
				Integer.valueOf(receivedTweets), Integer.valueOf(expectedTweets),
				null, true);
	}

	static void preprocessingRetainedInput(String owner, int retainedTweets)
	{
		update(owner, State.PREPROCESSING, "Preprocessing retained text",
				"Cleaning and aggregating " + formatCount(retainedTweets)
						+ " records retained after live routing.",
				Integer.valueOf(retainedTweets),
				Integer.valueOf(retainedTweets),
				null,
				true);
	}

	static void waitingForInputCompletion(
			String owner, int receivedTweets, int expectedTweets)
	{
		update(owner, State.WAITING_FOR_INPUT, "Input retained",
				"Received all assigned records; waiting for remaining input producers.",
				Integer.valueOf(receivedTweets), Integer.valueOf(expectedTweets),
				null, true);
	}

	static void inputClosedByRuntime(
			String owner, int retainedTweets, int originalExpectedTweets)
	{
		String detail = "Input queue closed; retained "
				+ formatCount(retainedTweets)
				+ " records after live routing";
		if (originalExpectedTweets > 0
				&& originalExpectedTweets != retainedTweets)
		{
			detail += " (startup assignment was "
					+ formatCount(originalExpectedTweets) + ").";
		}
		else
		{
			detail += ".";
		}
		update(owner, State.WAITING_FOR_INPUT, "Input queue closed",
				detail,
				Integer.valueOf(retainedTweets),
				Integer.valueOf(retainedTweets),
				null,
				true);
	}

	static void waitingForPeers(String owner, int usableTweets)
	{
		update(owner, State.WAITING_FOR_PEERS, "Ready for model",
				"Prepared " + formatCount(usableTweets)
						+ " usable records; waiting for peer nodes.",
				null, null, null, true);
	}

	static void buildingFeatures(String owner, String detail)
	{
		update(owner, State.FEATURES, "Building features",
				clean(detail, "Preparing the selected model input."),
				null, null, null, true);
	}

	static void modelRunning(String owner, String detail)
	{
		update(owner, State.MODEL_RUNNING, "Running model",
				clean(detail, "Training or scoring the selected model."),
				null, null, null, true);
	}

	static void recordMessageStats(String owner, long bytes, long milliseconds)
	{
		Snapshot snapshot;
		synchronized (LOCK)
		{
			MutableNode node = findNodeLocked(owner);
			if (node == null)
			{
				return;
			}
			node.metrics.messageBytes = Math.max(0L, bytes);
			node.metrics.messageMillis = Math.max(0L, milliseconds);
			node.addEvent(
					"Transport measured",
					formatCount(bytes) + " bytes in "
							+ Math.max(0L, milliseconds) + " ms.");
			snapshot = snapshotLocked();
		}
		publish(snapshot);
	}

	static void recordTextProcessingStats(
			String owner,
			int usableRecords,
			int userDocuments,
			int registeredUsers,
			long textProcessingNanos)
	{
		Snapshot snapshot;
		synchronized (LOCK)
		{
			MutableNode node = findNodeLocked(owner);
			if (node == null)
			{
				return;
			}
			node.metrics.usableRecords = Math.max(0, usableRecords);
			node.metrics.userDocuments = Math.max(0, userDocuments);
			node.metrics.registeredUsers = Math.max(0, registeredUsers);
			node.metrics.textProcessingMillis = nanosToMillis(textProcessingNanos);
			node.addEvent(
					"Text prepared",
					formatCount(usableRecords) + " usable records, "
							+ formatCount(userDocuments) + " user documents.");
			snapshot = snapshotLocked();
		}
		publish(snapshot);
	}

	static void recordFeatureStats(
			String owner,
			int documents,
			int featureCount,
			int documentFrequencyTerms,
			long tfidfNanos)
	{
		Snapshot snapshot;
		synchronized (LOCK)
		{
			MutableNode node = findNodeLocked(owner);
			if (node == null)
			{
				return;
			}
			node.metrics.featureDocuments = Math.max(0, documents);
			node.metrics.featureCount = Math.max(0, featureCount);
			node.metrics.documentFrequencyTerms =
					Math.max(0, documentFrequencyTerms);
			node.metrics.tfidfMillis = nanosToMillis(tfidfNanos);
			node.addEvent(
					"Features built",
					formatCount(featureCount) + " terms across "
							+ formatCount(documents) + " documents.");
			snapshot = snapshotLocked();
		}
		publish(snapshot);
	}

	static void recordModelShape(
			String owner,
			String modelName,
			int featureCount,
			int documentCount,
			int classCount,
			int targetCount,
			String detail)
	{
		Snapshot snapshot;
		synchronized (LOCK)
		{
			MutableNode node = findNodeLocked(owner);
			if (node == null)
			{
				return;
			}
			node.metrics.modelName = clean(modelName, node.metrics.modelName);
			if (featureCount >= 0)
			{
				node.metrics.featureCount = featureCount;
			}
			if (documentCount >= 0)
			{
				node.metrics.featureDocuments = documentCount;
			}
			if (classCount >= 0)
			{
				node.metrics.classCount = classCount;
			}
			if (targetCount >= 0)
			{
				node.metrics.targetCount = targetCount;
			}
			node.metrics.modelDetail = clean(detail, node.metrics.modelDetail);
			node.addEvent(
					"Model shape captured",
					clean(detail, "Model metadata is available for this node."));
			snapshot = snapshotLocked();
		}
		publish(snapshot);
	}

	static void recordAlgorithmTiming(
			String owner,
			long algorithmNanos,
			long trainNanos,
			long testNanos)
	{
		Snapshot snapshot;
		synchronized (LOCK)
		{
			MutableNode node = findNodeLocked(owner);
			if (node == null)
			{
				return;
			}
			node.metrics.algorithmMillis = nanosToMillis(algorithmNanos);
			node.metrics.trainMillis = nanosToMillis(trainNanos);
			node.metrics.testMillis = nanosToMillis(testNanos);
			node.addEvent(
					"Model timing recorded",
					"Algorithm stage took "
							+ node.metrics.algorithmMillis + " ms.");
			snapshot = snapshotLocked();
		}
		publish(snapshot);
	}

	static void recordPublishStats(
			String owner,
			int scoreTargets,
			int scoreCandidates,
			String outputMode)
	{
		Snapshot snapshot;
		synchronized (LOCK)
		{
			MutableNode node = findNodeLocked(owner);
			if (node == null)
			{
				return;
			}
			node.metrics.scoreTargets = Math.max(0, scoreTargets);
			node.metrics.scoreCandidates = Math.max(0, scoreCandidates);
			node.metrics.outputMode = clean(outputMode, node.metrics.outputMode);
			node.addEvent(
					"Scores prepared",
					formatCount(scoreTargets) + " target maps, "
							+ formatCount(scoreCandidates) + " candidate scores.");
			snapshot = snapshotLocked();
		}
		publish(snapshot);
	}

	static void waitingForMemory(
			String owner, String stage, long reservationMb)
	{
		update(owner, State.WAITING_FOR_MEMORY, "Queued safely",
				"Waiting for heap capacity: " + clean(stage, "model stage") + ".",
				null, null, Long.valueOf(Math.max(0L, reservationMb)), true);
	}

	static void memoryStageActive(
			String owner, String stage, long reservationMb)
	{
		update(owner, State.MODEL_RUNNING, "Model active",
				clean(stage, "Memory-intensive model stage") + " is running.",
				null, null, Long.valueOf(Math.max(0L, reservationMb)), true);
	}

	static void memoryStageReleased(String owner, String stage)
	{
		update(owner, State.MODEL_RUNNING, "Finalizing scores",
				clean(stage, "Model stage") + " completed; preparing output.",
				null, null, Long.valueOf(0L), true);
	}

	static void waitingForSharedModel(String owner, String detail)
	{
		update(owner, State.WAITING_FOR_SHARED_MODEL, "Shared model wait",
				clean(detail, "Another node is training the shared Doc2Vec model."),
				null, null, null, true);
	}

	static void publishing(String owner)
	{
		update(owner, State.PUBLISHING, "Publishing scores",
				"Sending this node's score map to the organizing agent.",
				null, null, null, true);
	}

	static void complete(String owner)
	{
		update(owner, State.COMPLETE, "Node complete",
				"Scores delivered successfully.",
				null, null, Long.valueOf(0L), true);
	}

	static void failed(String owner, Throwable failure)
	{
		String detail = failure == null
				? "The node stopped without producing a valid score map."
				: failure.getClass().getSimpleName()
						+ (failure.getMessage() == null
								? "" : ": " + failure.getMessage());
		update(owner, State.FAILED, "Node failed", detail,
				null, null, Long.valueOf(0L), true);
	}

	static void stopped(String owner)
	{
		Snapshot snapshot;
		synchronized (LOCK)
		{
			MutableNode node = findNodeLocked(owner);
			if (node == null || node.state == State.COMPLETE
					|| node.state == State.FAILED)
			{
				return;
			}
			node.transition(
					State.STOPPED,
					"Node stopped",
					"The recommender agent was shut down.");
			snapshot = snapshotLocked();
		}

		publish(snapshot);
	}

	static void setRunPhase(String phase)
	{
		Snapshot snapshot;
		synchronized (LOCK)
		{
			runPhase = clean(phase, runPhase);
			snapshot = snapshotLocked();
		}
		publish(snapshot);
	}

	static Snapshot snapshot()
	{
		synchronized (LOCK)
		{
			return snapshotLocked();
		}
	}

	static void addListener(Listener listener)
	{
		if (listener == null)
		{
			return;
		}
		LISTENERS.addIfAbsent(listener);
		listener.onSnapshot(snapshot());
	}

	static void removeListener(Listener listener)
	{
		LISTENERS.remove(listener);
	}

	private static void update(
			String owner,
			State state,
			String stage,
			String detail,
			Integer receivedTweets,
			Integer expectedTweets,
			Long reservationMb,
			boolean publish)
	{
		Snapshot snapshot = null;
		synchronized (LOCK)
		{
			MutableNode node = findNodeLocked(owner);
			if (node == null)
			{
				return;
			}
			if ((node.state == State.FAILED || node.state == State.STOPPED)
					&& state != State.FAILED && state != State.STOPPED)
			{
				return;
			}
			if (node.state == State.COMPLETE && state != State.COMPLETE)
			{
				return;
			}
			if (receivedTweets != null)
			{
				node.receivedTweets = Math.max(0, receivedTweets.intValue());
			}
			if (expectedTweets != null)
			{
				node.expectedTweets = Math.max(0, expectedTweets.intValue());
			}
			if (reservationMb != null)
			{
				node.reservationMb = Math.max(0L, reservationMb.longValue());
			}
			node.transition(state, stage, detail);
			if (publish)
			{
				snapshot = snapshotLocked();
			}
		}
		if (snapshot != null)
		{
			publish(snapshot);
		}
	}

	private static MutableNode findNodeLocked(String owner)
	{
		if (owner == null)
		{
			return null;
		}
		MutableNode existing = NODES.get(owner);
		if (existing != null)
		{
			return existing;
		}
		if (!owner.startsWith("Recommender-ServiceAgent"))
		{
			return null;
		}
		try
		{
			int index = Integer.parseInt(
					owner.substring("Recommender-ServiceAgent".length()));
			if (requestedNodes > 0 && index > requestedNodes)
			{
				return null;
			}
			MutableNode node = new MutableNode(
					index,
					owner,
					State.PROVISIONING,
					"Provisioning",
					"Waiting for the JADE agent to start.");
			NODES.put(owner, node);
			requestedNodes = Math.max(requestedNodes, index);
			return node;
		}
		catch (NumberFormatException ignored)
		{
			return null;
		}
	}

	private static Snapshot snapshotLocked()
	{
		ArrayList<NodeSnapshot> nodeSnapshots = new ArrayList<NodeSnapshot>();
		for (MutableNode node : NODES.values())
		{
			nodeSnapshots.add(node.snapshot());
		}
		Collections.sort(nodeSnapshots, new Comparator<NodeSnapshot>()
		{
			public int compare(NodeSnapshot first, NodeSnapshot second)
			{
				return Integer.compare(first.nodeIndex, second.nodeIndex);
			}
		});
		return new Snapshot(
				requestedNodes,
				runSequence,
				algorithmName,
				runId,
				runPhase,
				experimentStartedAt,
				runStartedAt,
				nodeSnapshots);
	}

	private static void publish(Snapshot snapshot)
	{
		for (Listener listener : LISTENERS)
		{
			try
			{
				listener.onSnapshot(snapshot);
			}
			catch (RuntimeException exception)
			{
				System.err.println(
						"Node telemetry listener failed: "
								+ exception.getMessage());
			}
		}
	}

	private static String nodeName(int index)
	{
		return "Recommender-ServiceAgent" + index;
	}

	private static String clean(String value, String fallback)
	{
		return value == null || value.trim().length() == 0
				? fallback : value.trim();
	}

	private static String formatCount(long value)
	{
		return String.format(java.util.Locale.US, "%,d", Math.max(0L, value));
	}

	private static int percent(int completed, int total)
	{
		if (total <= 0)
		{
			return completed > 0 ? 100 : 0;
		}
		return Math.max(0, Math.min(100,
				(int)Math.round(completed * 100.0 / total)));
	}

	private static long nanosToMillis(long nanos)
	{
		if (nanos <= 0L)
		{
			return 0L;
		}
		return Math.max(1L, Math.round(nanos / 1000000.0));
	}

	private static final class MutableNode
	{
		private final int nodeIndex;
		private final String name;
		private State state;
		private String stage;
		private String detail;
		private int receivedTweets;
		private int expectedTweets;
		private long reservationMb;
		private long runStartedAt;
		private long stageStartedAt;
		private long updatedAt;
		private long completedAt;
		private long lastProgressPublishedAt;
		private MutableMetrics metrics = new MutableMetrics();
		private final ArrayList<NodeEvent> events = new ArrayList<NodeEvent>();

		private MutableNode(
				int nodeIndex,
				String name,
				State state,
				String stage,
				String detail)
		{
			this.nodeIndex = nodeIndex;
			this.name = name;
			this.state = state;
			this.stage = stage;
			this.detail = detail;
			long now = System.currentTimeMillis();
			stageStartedAt = now;
			updatedAt = now;
			addEvent(stage, detail);
		}

		private void resetForRun()
		{
			receivedTweets = 0;
			reservationMb = 0L;
			completedAt = 0L;
			metrics = new MutableMetrics();
			events.clear();
			runStartedAt = NodeExecutionMonitor.runStartedAt;
			transition(
					State.READY,
					"Preparing run",
					"Waiting for assigned dataset records.");
		}

		private void transition(State newState, String newStage, String newDetail)
		{
			long now = System.currentTimeMillis();
			boolean changed = state != newState
					|| !clean(newStage, "").equals(stage)
					|| !clean(newDetail, "").equals(detail);
			if (state != newState || !clean(newStage, "").equals(stage))
			{
				stageStartedAt = now;
			}
			state = newState;
			stage = clean(newStage, "Working");
			detail = clean(newDetail, "");
			updatedAt = now;
			if (changed)
			{
				addEvent(stage, detail);
			}
			if (newState == State.COMPLETE || newState == State.FAILED
					|| newState == State.STOPPED)
			{
				completedAt = now;
			}
			else
			{
				completedAt = 0L;
			}
		}

		private boolean isTerminal()
		{
			return state == State.COMPLETE || state == State.FAILED
					|| state == State.STOPPED;
		}

		private void addEvent(String stage, String detail)
		{
			events.add(new NodeEvent(
					System.currentTimeMillis(),
					state,
					clean(stage, "Working"),
					clean(detail, "")));
			while (events.size() > MAX_NODE_EVENTS)
			{
				events.remove(0);
			}
		}

		private NodeSnapshot snapshot()
		{
			return new NodeSnapshot(
					nodeIndex,
					name,
					state,
					stage,
					detail,
					receivedTweets,
					expectedTweets,
					reservationMb,
					runStartedAt,
					stageStartedAt,
					updatedAt,
					completedAt,
					NodeCapacityRegistry.profileForNode(nodeIndex),
					metrics.snapshot(),
					events);
		}
	}

	private static final class MutableMetrics
	{
		private long messageBytes = -1L;
		private long messageMillis = -1L;
		private long textProcessingMillis = -1L;
		private long tfidfMillis = -1L;
		private long algorithmMillis = -1L;
		private long trainMillis = -1L;
		private long testMillis = -1L;
		private int usableRecords = -1;
		private int userDocuments = -1;
		private int registeredUsers = -1;
		private int featureDocuments = -1;
		private int featureCount = -1;
		private int documentFrequencyTerms = -1;
		private int classCount = -1;
		private int targetCount = -1;
		private int scoreTargets = -1;
		private int scoreCandidates = -1;
		private String modelName = "";
		private String modelDetail = "";
		private String outputMode = "";

		private NodeMetrics snapshot()
		{
			return new NodeMetrics(
					messageBytes,
					messageMillis,
					textProcessingMillis,
					tfidfMillis,
					algorithmMillis,
					trainMillis,
					testMillis,
					usableRecords,
					userDocuments,
					registeredUsers,
					featureDocuments,
					featureCount,
					documentFrequencyTerms,
					classCount,
					targetCount,
					scoreTargets,
					scoreCandidates,
					modelName,
					modelDetail,
					outputMode);
		}
	}

	static final class Snapshot
	{
		private final int requestedNodes;
		private final int runSequence;
		private final String algorithmName;
		private final String runId;
		private final String runPhase;
		private final long experimentStartedAt;
		private final long runStartedAt;
		private final List<NodeSnapshot> nodes;

		private Snapshot(
				int requestedNodes,
				int runSequence,
				String algorithmName,
				String runId,
				String runPhase,
				long experimentStartedAt,
				long runStartedAt,
				List<NodeSnapshot> nodes)
		{
			this.requestedNodes = requestedNodes;
			this.runSequence = runSequence;
			this.algorithmName = algorithmName;
			this.runId = runId;
			this.runPhase = runPhase;
			this.experimentStartedAt = experimentStartedAt;
			this.runStartedAt = runStartedAt;
			this.nodes = Collections.unmodifiableList(
					new ArrayList<NodeSnapshot>(nodes));
		}

		int getRequestedNodes()
		{
			return requestedNodes;
		}

		int getRunSequence()
		{
			return runSequence;
		}

		String getAlgorithmName()
		{
			return algorithmName;
		}

		String getRunId()
		{
			return runId;
		}

		String getRunPhase()
		{
			return runPhase;
		}

		long getExperimentStartedAt()
		{
			return experimentStartedAt;
		}

		long getRunStartedAt()
		{
			return runStartedAt;
		}

		List<NodeSnapshot> getNodes()
		{
			return nodes;
		}

		int count(State state)
		{
			int count = 0;
			for (NodeSnapshot node : nodes)
			{
				if (node.state == state)
				{
					count++;
				}
			}
			return count;
		}

		int activeCount()
		{
			int count = 0;
			for (NodeSnapshot node : nodes)
			{
				if (node.isActive())
				{
					count++;
				}
			}
			return count;
		}

		int waitingCount()
		{
			int count = 0;
			for (NodeSnapshot node : nodes)
			{
				if (node.isWaiting())
				{
					count++;
				}
			}
			return count;
		}
	}

	static final class NodeSnapshot
	{
		private final int nodeIndex;
		private final String name;
		private final State state;
		private final String stage;
		private final String detail;
		private final int receivedTweets;
		private final int expectedTweets;
		private final long reservationMb;
		private final long runStartedAt;
		private final long stageStartedAt;
		private final long updatedAt;
		private final long completedAt;
		private final NodeCapacityProfile capacityProfile;
		private final NodeMetrics metrics;
		private final List<NodeEvent> events;

		private NodeSnapshot(
				int nodeIndex,
				String name,
				State state,
				String stage,
				String detail,
				int receivedTweets,
				int expectedTweets,
				long reservationMb,
				long runStartedAt,
				long stageStartedAt,
				long updatedAt,
				long completedAt,
				NodeCapacityProfile capacityProfile,
				NodeMetrics metrics,
				List<NodeEvent> events)
		{
			this.nodeIndex = nodeIndex;
			this.name = name;
			this.state = state;
			this.stage = stage;
			this.detail = detail;
			this.receivedTweets = receivedTweets;
			this.expectedTweets = expectedTweets;
			this.reservationMb = reservationMb;
			this.runStartedAt = runStartedAt;
			this.stageStartedAt = stageStartedAt;
			this.updatedAt = updatedAt;
			this.completedAt = completedAt;
			this.capacityProfile = capacityProfile == null
					? NodeCapacityProfile.defaultProfile(nodeIndex, 1,
							RuntimeResourceSettings.currentMaxHeapMb())
					: capacityProfile;
			this.metrics = metrics == null ? NodeMetrics.empty() : metrics;
			this.events = Collections.unmodifiableList(
					new ArrayList<NodeEvent>(events));
		}

		int getNodeIndex()
		{
			return nodeIndex;
		}

		String getName()
		{
			return name;
		}

		State getState()
		{
			return state;
		}

		String getStage()
		{
			return stage;
		}

		String getDetail()
		{
			return detail;
		}

		int getReceivedTweets()
		{
			return receivedTweets;
		}

		int getExpectedTweets()
		{
			return expectedTweets;
		}

		long getReservationMb()
		{
			return reservationMb;
		}

		long getRunStartedAt()
		{
			return runStartedAt;
		}

		long getStageStartedAt()
		{
			return stageStartedAt;
		}

		long getUpdatedAt()
		{
			return updatedAt;
		}

		long getCompletedAt()
		{
			return completedAt;
		}

		NodeMetrics getMetrics()
		{
			return metrics;
		}

		NodeCapacityProfile getCapacityProfile()
		{
			return capacityProfile;
		}

		List<NodeEvent> getEvents()
		{
			return events;
		}

		int getProgressPercent()
		{
			return percent(receivedTweets, expectedTweets);
		}

		boolean isActive()
		{
			switch (state)
			{
				case RECEIVING:
				case PREPROCESSING:
				case FEATURES:
				case MODEL_RUNNING:
				case PUBLISHING:
					return true;
				default:
					return false;
			}
		}

		boolean isWaiting()
		{
			return state == State.PROVISIONING
					|| state == State.READY
					|| state == State.WAITING_FOR_INPUT
					|| state == State.WAITING_FOR_PEERS
					|| state == State.WAITING_FOR_MEMORY
					|| state == State.WAITING_FOR_SHARED_MODEL;
		}
	}

	static final class NodeMetrics
	{
		private final long messageBytes;
		private final long messageMillis;
		private final long textProcessingMillis;
		private final long tfidfMillis;
		private final long algorithmMillis;
		private final long trainMillis;
		private final long testMillis;
		private final int usableRecords;
		private final int userDocuments;
		private final int registeredUsers;
		private final int featureDocuments;
		private final int featureCount;
		private final int documentFrequencyTerms;
		private final int classCount;
		private final int targetCount;
		private final int scoreTargets;
		private final int scoreCandidates;
		private final String modelName;
		private final String modelDetail;
		private final String outputMode;

		private NodeMetrics(
				long messageBytes,
				long messageMillis,
				long textProcessingMillis,
				long tfidfMillis,
				long algorithmMillis,
				long trainMillis,
				long testMillis,
				int usableRecords,
				int userDocuments,
				int registeredUsers,
				int featureDocuments,
				int featureCount,
				int documentFrequencyTerms,
				int classCount,
				int targetCount,
				int scoreTargets,
				int scoreCandidates,
				String modelName,
				String modelDetail,
				String outputMode)
		{
			this.messageBytes = messageBytes;
			this.messageMillis = messageMillis;
			this.textProcessingMillis = textProcessingMillis;
			this.tfidfMillis = tfidfMillis;
			this.algorithmMillis = algorithmMillis;
			this.trainMillis = trainMillis;
			this.testMillis = testMillis;
			this.usableRecords = usableRecords;
			this.userDocuments = userDocuments;
			this.registeredUsers = registeredUsers;
			this.featureDocuments = featureDocuments;
			this.featureCount = featureCount;
			this.documentFrequencyTerms = documentFrequencyTerms;
			this.classCount = classCount;
			this.targetCount = targetCount;
			this.scoreTargets = scoreTargets;
			this.scoreCandidates = scoreCandidates;
			this.modelName = clean(modelName, "");
			this.modelDetail = clean(modelDetail, "");
			this.outputMode = clean(outputMode, "");
		}

		private static NodeMetrics empty()
		{
			return new NodeMetrics(
					-1L, -1L, -1L, -1L, -1L, -1L, -1L,
					-1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
					"", "", "");
		}

		long getMessageBytes()
		{
			return messageBytes;
		}

		long getMessageMillis()
		{
			return messageMillis;
		}

		long getTextProcessingMillis()
		{
			return textProcessingMillis;
		}

		long getTfidfMillis()
		{
			return tfidfMillis;
		}

		long getAlgorithmMillis()
		{
			return algorithmMillis;
		}

		long getTrainMillis()
		{
			return trainMillis;
		}

		long getTestMillis()
		{
			return testMillis;
		}

		int getUsableRecords()
		{
			return usableRecords;
		}

		int getUserDocuments()
		{
			return userDocuments;
		}

		int getRegisteredUsers()
		{
			return registeredUsers;
		}

		int getFeatureDocuments()
		{
			return featureDocuments;
		}

		int getFeatureCount()
		{
			return featureCount;
		}

		int getDocumentFrequencyTerms()
		{
			return documentFrequencyTerms;
		}

		int getClassCount()
		{
			return classCount;
		}

		int getTargetCount()
		{
			return targetCount;
		}

		int getScoreTargets()
		{
			return scoreTargets;
		}

		int getScoreCandidates()
		{
			return scoreCandidates;
		}

		String getModelName()
		{
			return modelName;
		}

		String getModelDetail()
		{
			return modelDetail;
		}

		String getOutputMode()
		{
			return outputMode;
		}
	}

	static final class NodeEvent
	{
		private final long time;
		private final State state;
		private final String stage;
		private final String detail;

		private NodeEvent(long time, State state, String stage, String detail)
		{
			this.time = time;
			this.state = state;
			this.stage = clean(stage, "Working");
			this.detail = clean(detail, "");
		}

		long getTime()
		{
			return time;
		}

		State getState()
		{
			return state;
		}

		String getStage()
		{
			return stage;
		}

		String getDetail()
		{
			return detail;
		}
	}
}
