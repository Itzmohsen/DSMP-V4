package TwitterGatherDataFollowers.userRyersonU;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Everything scheduling-related about one simulator run, frozen when the run is
 * initialized and shared by every agent of the run.
 *
 * The controller creates it from the scheduler plan, the node capacity
 * profiles, the cluster settings and the scenario. From then on only the live
 * controls (operator actions, scenario steps and faults) change it, and each
 * applies at once to every open work phase and to input routing.
 *
 * It owns the run's model clock (time dilation), network, emulators,
 * collectives, recorder and timeline, so every part of the run measures and
 * emulates on one time scale.
 */
final class SchedulingRunContext
{
	enum Mode
	{
		BASELINE("Hardware (unemulated)",
				"Runs on this machine's hardware with the original DSMP placement; node profiles are ignored and nothing is emulated. The exactness reference."),
		EMULATED("Emulated cluster",
				"Every node's speed, lanes, memory, queue, link latency and bandwidth are physically emulated on a common time scale."),
		FAULT_INJECTION("Emulated cluster with faults",
				"As emulated, plus transient unit faults and node crashes with repairs, all seeded and reproducible.");

		private final String label;
		private final String description;

		Mode(String label, String description)
		{
			this.label = label;
			this.description = description;
		}

		String getLabel()
		{
			return label;
		}

		String getDescription()
		{
			return description;
		}

		static Mode fromPlan(RecommendationSchedulerPlan plan)
		{
			if (plan == null || plan.getMode() == RecommendationSchedulerPlan.Mode.DSMP_DEFAULT)
			{
				return BASELINE;
			}
			return plan.getMode() == RecommendationSchedulerPlan.Mode.SCHEDULING_EXPERIMENT
					? FAULT_INJECTION : EMULATED;
		}
	}

	/** Supplies a phase's units; called once, by whichever node opens it first. */
	interface UnitSupplier
	{
		List<WorkUnitSpec> units();
	}

	/** An open work phase and its display metadata. */
	static final class PhaseEntry
	{
		private final String key;
		private final String label;
		private final WorkPhase phase;
		private final long openedAtMillis;
		private final double openedAtModelMs;

		private PhaseEntry(String key, String label, WorkPhase phase, double openedAtModelMs)
		{
			this.key = key;
			this.label = label;
			this.phase = phase;
			this.openedAtMillis = System.currentTimeMillis();
			this.openedAtModelMs = openedAtModelMs;
		}

		String getKey()
		{
			return key;
		}

		String getLabel()
		{
			return label;
		}

		WorkPhase getPhase()
		{
			return phase;
		}

		long getOpenedAtMillis()
		{
			return openedAtMillis;
		}

		double getOpenedAtModelMs()
		{
			return openedAtModelMs;
		}
	}

	static final class ControlEvent
	{
		private final long time;
		private final double modelMs;
		private final String action;
		private final String detail;

		private ControlEvent(long time, double modelMs, String action, String detail)
		{
			this.time = time;
			this.modelMs = modelMs;
			this.action = action;
			this.detail = detail;
		}

		long getTime()
		{
			return time;
		}

		double getModelMs()
		{
			return modelMs;
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

	private static final int MAX_EVENTS = 400;

	private final String runId;
	private final Mode mode;
	private final SchedulerLabEngine.SchedulingPolicy basePolicy;
	private final int nodeCount;
	private final int algorithm;
	private final List<NodeCapacityProfile> profiles;
	private final ClusterSettings settings;
	private final Scenario scenario;
	private final long faultSeed;
	private final int maxFailuresPerUnit;
	private final long stallTimeoutMs;
	private final long createdAtMillis = System.currentTimeMillis();

	private final WorkDispatcher.NodeState[] nodeStates;
	private final boolean[] draining;
	private final boolean[] down;
	private final boolean[] crashDetected;
	private final int[] crashEpoch;
	private final double[] downSince;
	private final int[] crashCount;
	private final int[] waitingForRepair;
	private final double[] capacityScales;
	private SchedulerLabEngine.SchedulingPolicy policyOverride;
	private int pacingMs;
	private List<NodeSpec> specs;

	private final ModelClock clock;
	private final NetworkModel network;
	private final RunRecorder recorder;
	private final RunTimeline timeline;
	private final NodeEmulator[] emulators;
	private final RunCollectives collectives;
	private final LinkedHashMap<String,PhaseEntry> phases = new LinkedHashMap<String,PhaseEntry>();
	private final ArrayList<ControlEvent> events = new ArrayList<ControlEvent>();
	private final long hostCpuAtStartNanos;
	private final long hostWallAtStartNanos;

	//Input routing under live controls: one receiver per user, decided once.
	private final LinkedHashMap<String,Integer> userReceiver = new LinkedHashMap<String,Integer>();
	private final double[] reroutedWork;
	private int redirectTarget;
	private int redirectRemainingUsers;
	private int drainPreferredTarget;
	private String placementSummary = "";
	private final List<String> placementWarnings = new ArrayList<String>();
	private volatile boolean closed;
	private boolean finished;
	private RunMetrics recordedMetrics;
	private java.io.File recordDirectory;
	private final java.util.concurrent.CountDownLatch recordLatch = new java.util.concurrent.CountDownLatch(1);

	/** A context with the registry's cluster settings (seeded by {@code faultSeed}) and no scenario. */
	SchedulingRunContext(
			String runId,
			Mode mode,
			SchedulerLabEngine.SchedulingPolicy policy,
			List<NodeCapacityProfile> profiles,
			int nodeCount,
			int algorithm,
			long faultSeed,
			int maxFailuresPerUnit,
			long stallTimeoutMs,
			int pacingMs)
	{
		this(runId, mode, policy, profiles, nodeCount, algorithm,
				NodeCapacityRegistry.clusterSettings().withSeed(faultSeed), Scenario.NONE,
				maxFailuresPerUnit, stallTimeoutMs, pacingMs);
	}

	SchedulingRunContext(
			String runId,
			Mode mode,
			SchedulerLabEngine.SchedulingPolicy policy,
			List<NodeCapacityProfile> profiles,
			int nodeCount,
			int algorithm,
			ClusterSettings settings,
			Scenario scenario,
			int maxFailuresPerUnit,
			long stallTimeoutMs,
			int pacingMs)
	{
		this.runId = runId == null ? "" : runId;
		this.mode = mode == null ? Mode.BASELINE : mode;
		this.basePolicy = policy == null
				? SchedulerLabEngine.SchedulingPolicy.CAPACITY_WEIGHTED : policy;
		this.nodeCount = Math.max(1, nodeCount);
		this.algorithm = algorithm;
		this.profiles = Collections.unmodifiableList(denseProfiles(profiles, this.nodeCount));
		this.settings = settings == null ? ClusterSettings.defaults() : settings;
		this.scenario = scenario == null ? Scenario.NONE : scenario;
		this.faultSeed = this.settings.getSeed();
		this.maxFailuresPerUnit = Math.max(1, maxFailuresPerUnit);
		this.stallTimeoutMs = stallTimeoutMs <= 0L ? Long.MAX_VALUE / 4 : stallTimeoutMs;
		this.pacingMs = Math.max(0, pacingMs);
		nodeStates = new WorkDispatcher.NodeState[this.nodeCount];
		java.util.Arrays.fill(nodeStates, WorkDispatcher.NodeState.ACTIVE);
		draining = new boolean[this.nodeCount];
		down = new boolean[this.nodeCount];
		crashDetected = new boolean[this.nodeCount];
		crashEpoch = new int[this.nodeCount];
		downSince = new double[this.nodeCount];
		crashCount = new int[this.nodeCount];
		waitingForRepair = new int[this.nodeCount];
		capacityScales = new double[this.nodeCount];
		java.util.Arrays.fill(capacityScales, 1.0);
		reroutedWork = new double[this.nodeCount];
		specs = NodeSpec.fromProfiles(this.profiles, capacityScales, isEmulating());
		clock = new ModelClock(chooseDilation(this.settings, specs, isEmulating()));
		network = new NetworkModel(specs, !isEmulating());
		recorder = new RunRecorder(this.runId);
		emulators = new NodeEmulator[this.nodeCount];
		for (int node = 1; node <= this.nodeCount; node++)
		{
			emulators[node - 1] = new NodeEmulator(this, node);
		}
		collectives = new RunCollectives(this, this.nodeCount);
		hostCpuAtStartNanos = processCpuNanos();
		hostWallAtStartNanos = System.nanoTime();
		List<FailureSchedule> crashSchedules = mode == Mode.FAULT_INJECTION
				? FailureSchedule.forNodes(specs, this.settings, this.settings.getSeed())
				: new ArrayList<FailureSchedule>();
		timeline = new RunTimeline(this, this.scenario, crashSchedules);
		addEvent("run", "Run " + this.runId + ": " + this.mode.getLabel() + ", policy "
				+ effectivePolicy().getLabel() + ", " + this.nodeCount + " node(s), TDF "
				+ String.format(Locale.US, "%.2f", clock.getDilation())
				+ (this.scenario.isEmpty() ? "" : ", scenario \"" + this.scenario.getName() + "\"")
				+ ".");
		for (NodeSpec spec : specs)
		{
			if (isEmulating() && spec.getSpeed() > clock.getDilation() + 1e-9)
			{
				recorder.warning(String.format(Locale.US,
						"Node %d's speed %.2f exceeds the time dilation factor %.2f: it runs at speed %.2f (host speed).",
						spec.getIndex(), spec.getSpeed(), clock.getDilation(), clock.getDilation()));
			}
		}
		if (this.scenario.highestNode() > this.nodeCount)
		{
			recorder.warning("The scenario names node " + this.scenario.highestNode()
					+ " but the run has " + this.nodeCount + " node(s); those steps will be refused.");
		}
	}

	/**
	 * The time dilation factor of a run: the configured one, or automatically
	 * the smallest factor that (a) lets the fastest node and the coordinator
	 * run at their speeds with the configured boost headroom and (b) keeps the
	 * expected number of busy host cores within the machine's cores (each lane
	 * of a speed-s node keeps a host core busy s / TDF of the time).
	 */
	static double chooseDilation(ClusterSettings settings, List<NodeSpec> specs, boolean emulate)
	{
		if (!emulate)
		{
			return 1.0;
		}
		ClusterSettings safe = settings == null ? ClusterSettings.defaults() : settings;
		if (!safe.isAutomaticTimeDilation())
		{
			return safe.getTimeDilation();
		}
		double fastest = safe.getCoordinatorSpeed();
		double load = 0.0;
		for (NodeSpec spec : specs)
		{
			fastest = Math.max(fastest, spec.getSpeed());
			load += spec.getLanes() * spec.getSpeed();
		}
		int usableCores = Math.max(1, Runtime.getRuntime().availableProcessors() - 2);
		double dilation = Math.max(1.0, Math.max(fastest * safe.getBoostHeadroom(), load / usableCores));
		return Math.ceil(dilation * 100.0 - 1e-9) / 100.0;
	}

	/** Starts the run's timeline (scenario and crash processes). */
	void start()
	{
		timeline.start();
	}

	private static List<NodeCapacityProfile> denseProfiles(
			List<NodeCapacityProfile> profiles, int nodeCount)
	{
		ArrayList<NodeCapacityProfile> dense = new ArrayList<NodeCapacityProfile>();
		for (int node = 1; node <= nodeCount; node++)
		{
			NodeCapacityProfile match = null;
			if (profiles != null)
			{
				for (NodeCapacityProfile profile : profiles)
				{
					if (profile != null && profile.getNodeIndex() == node)
					{
						match = profile;
					}
				}
			}
			dense.add(match != null ? match
					: NodeCapacityProfile.defaultProfile(node, nodeCount,
							RuntimeResourceSettings.currentMaxHeapMb()));
		}
		return dense;
	}

	// ------------------------------------------------------------ identity

	String getRunId()
	{
		return runId;
	}

	Mode getMode()
	{
		return mode;
	}

	int getNodeCount()
	{
		return nodeCount;
	}

	int getAlgorithm()
	{
		return algorithm;
	}

	List<NodeCapacityProfile> getProfiles()
	{
		return profiles;
	}

	ClusterSettings getSettings()
	{
		return settings;
	}

	Scenario getScenario()
	{
		return scenario;
	}

	long getFaultSeed()
	{
		return faultSeed;
	}

	long getCreatedAtMillis()
	{
		return createdAtMillis;
	}

	boolean isEmulating()
	{
		return mode != Mode.BASELINE;
	}

	boolean isFaultInjection()
	{
		return mode == Mode.FAULT_INJECTION;
	}

	long getStallTimeoutMs()
	{
		return stallTimeoutMs;
	}

	ModelClock clock()
	{
		return clock;
	}

	NetworkModel network()
	{
		return network;
	}

	RunRecorder recorder()
	{
		return recorder;
	}

	RunTimeline timeline()
	{
		return timeline;
	}

	/** Model milliseconds since the run's context was created. */
	double now()
	{
		return clock.nowMs();
	}

	synchronized SchedulerLabEngine.SchedulingPolicy getBasePolicy()
	{
		return basePolicy;
	}

	/** The policy work phases use now: the live override, else the plan's policy. */
	synchronized SchedulerLabEngine.SchedulingPolicy effectivePolicy()
	{
		if (mode == Mode.BASELINE)
		{
			return SchedulerLabEngine.SchedulingPolicy.ORIGINAL_DSMP;
		}
		return policyOverride != null ? policyOverride : basePolicy;
	}

	synchronized SchedulerLabEngine.SchedulingPolicy getPolicyOverride()
	{
		return policyOverride;
	}

	synchronized List<NodeSpec> currentSpecs()
	{
		return specs;
	}

	synchronized double speedOf(int node)
	{
		return specs.get(node - 1).getSpeed();
	}

	synchronized int delayOf(int node)
	{
		return specs.get(node - 1).getDelayMs();
	}

	synchronized int lanesOf(int node)
	{
		return specs.get(node - 1).getLanes();
	}

	NodeEmulator emulator(int node)
	{
		if (node < 1 || node > nodeCount)
		{
			throw new IllegalArgumentException("Node " + node + " is outside 1.." + nodeCount + ".");
		}
		return emulators[node - 1];
	}

	RunCollectives collectives()
	{
		return collectives;
	}

	boolean isAborted()
	{
		return closed || RecommendationRunAbortRegistry.isAborted(runId);
	}

	void requireActive()
	{
		if (RecommendationRunAbortRegistry.isAborted(runId))
		{
			RecommendationRunAbortRegistry.requireActive(runId);
			throw new IllegalStateException("Run " + runId + " was aborted.");
		}
		if (closed)
		{
			throw new IllegalStateException("Run " + runId + " has ended.");
		}
	}

	synchronized int getPacingMs()
	{
		return pacingMs;
	}

	// ------------------------------------------------------------ agreement

	private final LinkedHashMap<String,Integer> agreedValues = new LinkedHashMap<String,Integer>();

	/**
	 * Run-wide agreement on a setting every node must use identically (for
	 * example the requested top-N, which fixes where every node cuts its
	 * rows): the first node's proposal wins and every node gets it back.
	 */
	synchronized int agree(String key, int proposal)
	{
		Integer agreed = agreedValues.get(key);
		if (agreed == null)
		{
			agreedValues.put(key, Integer.valueOf(proposal));
			return proposal;
		}
		return agreed.intValue();
	}

	// ------------------------------------------------------------ milestones

	/** Records a run milestone (run start, input complete, merge, end) once. */
	void milestone(String name)
	{
		recorder.milestone(name, now());
	}

	/** Marks the start of a node's pipeline stage. */
	void stageStart(int node, String stage)
	{
		recorder.stageStart(node, stage, now());
	}

	void stageEnd(int node, String stage)
	{
		recorder.stageEnd(node, stage, now());
	}

	// ------------------------------------------------------------ run end

	/**
	 * Marks the run finished with {@code status} (see {@link RunRecords#finish}).
	 * Returns false when it was already finished.
	 */
	boolean markFinished(String status)
	{
		synchronized (this)
		{
			if (finished)
			{
				return false;
			}
			finished = true;
			addEvent("finished", status == null ? "completed" : status);
		}
		recorder.status(status == null ? "completed" : status);
		return true;
	}

	synchronized boolean isFinished()
	{
		return finished;
	}

	/** Called once the run's record was written (metrics may be null on failure). */
	void recordWritten(RunMetrics metrics, java.io.File directory)
	{
		synchronized (this)
		{
			recordedMetrics = metrics;
			recordDirectory = directory;
		}
		recordLatch.countDown();
	}

	synchronized RunMetrics getRecordedMetrics()
	{
		return recordedMetrics;
	}

	synchronized java.io.File getRecordDirectory()
	{
		return recordDirectory;
	}

	/** Waits for the run's record; returns its metrics or null on timeout. */
	RunMetrics awaitRecord(long timeoutMs) throws InterruptedException
	{
		recordLatch.await(Math.max(0L, timeoutMs), java.util.concurrent.TimeUnit.MILLISECONDS);
		return getRecordedMetrics();
	}

	// ------------------------------------------------------------ placement

	synchronized void recordPlacement(String summary, List<String> warnings)
	{
		placementSummary = summary == null ? "" : summary;
		placementWarnings.clear();
		if (warnings != null)
		{
			placementWarnings.addAll(warnings);
		}
		addEvent("placement", placementSummary);
		for (String warning : placementWarnings)
		{
			addEvent("placement warning", warning);
			recorder.warning(warning);
		}
	}

	synchronized String getPlacementSummary()
	{
		return placementSummary;
	}

	// ------------------------------------------------------------ phases

	/**
	 * Opens (or joins) the work phase {@code key}. The first caller's supplier
	 * defines the units; later callers get the same phase, so every node works
	 * on one consistent set of units. In the unemulated mode units that have a
	 * home node may only run there, as in the original simulator.
	 */
	WorkPhase openPhase(String key, String label, UnitSupplier supplier)
	{
		PhaseEntry created;
		synchronized (this)
		{
			PhaseEntry existing = phases.get(key);
			if (existing != null)
			{
				return existing.phase;
			}
			List<WorkUnitSpec> units = supplier == null
					? Collections.<WorkUnitSpec>emptyList() : supplier.units();
			if (mode == Mode.BASELINE)
			{
				units = pinToHome(units);
			}
			final String phaseKey = key;
			double openedAt = now();
			WorkDispatcher dispatcher = new WorkDispatcher(
					runId + "/" + key,
					effectivePolicy(),
					specs,
					units,
					new WorkDispatcher.Options()
							.faultInjection(isFaultInjection(), faultSeed)
							.maxFailuresPerUnit(maxFailuresPerUnit)
							.msPerTimeUnit(1.0)
							.network(network)
							.openedAt(openedAt)
							.maxRetainedEvents(4000)
							.listener(new WorkDispatcher.EventListener()
							{
								public void onEvent(WorkDispatcher.Event event)
								{
									recordDispatcherEvent(phaseKey, event);
								}
							}));
			for (int node = 1; node <= nodeCount; node++)
			{
				WorkDispatcher.NodeState state = nodeStates[node - 1];
				if (state != WorkDispatcher.NodeState.ACTIVE)
				{
					dispatcher.setNodeState(node, state, openedAt);
				}
				if (down[node - 1])
				{
					dispatcher.crash(node, openedAt);
					if (crashDetected[node - 1])
					{
						dispatcher.detectCrash(node, openedAt);
					}
				}
			}
			WorkPhase phase = new WorkPhase(dispatcher, runId, stallTimeoutMs, this);
			created = new PhaseEntry(key, label == null ? key : label, phase, openedAt);
			phases.put(key, created);
			recorder.phaseOpened(new RunRecorder.Phase(key, created.label,
					dispatcher.getPolicy().getLabel(), dispatcher.getMode().name().toLowerCase(Locale.US),
					units.size(), openedAt, dispatcher.lowerBound(), dispatcher.getUnitSpecs(),
					new ArrayList<NodeSpec>(specs)));
			addEvent("phase", "Opened " + created.label + ": "
					+ units.size() + " unit(s), " + dispatcher.getPolicy().getLabel()
					+ " (" + dispatcher.getMode().name().toLowerCase(Locale.US) + ").");
		}
		timeline.wake();
		return created.phase;
	}

	/** Called by a phase when its last unit committed or it failed. */
	void phaseFinished(WorkPhase phase, boolean failed, String reason)
	{
		String key = null;
		synchronized (this)
		{
			for (PhaseEntry entry : phases.values())
			{
				if (entry.phase == phase)
				{
					key = entry.key;
				}
			}
		}
		if (key != null)
		{
			double at = failed ? now() : phase.getDispatcher().getLastCommitTime();
			recorder.phaseFinished(key, Double.isNaN(at) ? now() : at, failed ? "failed: " + reason : "completed");
		}
		timeline.wake();
	}

	/** Called after each commit so progress-triggered scenario steps fire at once. */
	void progressMade()
	{
		timeline.wake();
	}

	private void recordDispatcherEvent(String phaseKey, WorkDispatcher.Event event)
	{
		String type = event.getType();
		if ("assigned".equals(type) || "delivered".equals(type) || "completed".equals(type))
		{
			return;
		}
		recorder.event(event.getTime(), type, event.getNode(),
				phaseKey + (event.getUnitKey().length() > 0 ? " " + event.getUnitKey() : "")
						+ ": " + event.getDetail());
	}

	synchronized List<PhaseEntry> phases()
	{
		return new ArrayList<PhaseEntry>(phases.values());
	}

	synchronized PhaseEntry phase(String key)
	{
		return phases.get(key);
	}

	synchronized PhaseEntry latestPhase()
	{
		PhaseEntry latest = null;
		for (PhaseEntry entry : phases.values())
		{
			latest = entry;
		}
		return latest;
	}

	/** The most recently opened phase that is still running, or null. */
	synchronized PhaseEntry runningPhase()
	{
		PhaseEntry running = null;
		for (PhaseEntry entry : phases.values())
		{
			if (!entry.phase.getDispatcher().isComplete() && !entry.phase.isClosed())
			{
				running = entry;
			}
		}
		return running;
	}

	private static List<WorkUnitSpec> pinToHome(List<WorkUnitSpec> units)
	{
		ArrayList<WorkUnitSpec> pinned = new ArrayList<WorkUnitSpec>();
		for (WorkUnitSpec unit : units)
		{
			if (unit.getHomeNode() > 0 && !unit.isRestricted())
			{
				pinned.add(unit.withAllowedNodes(new int[] {unit.getHomeNode()}));
			}
			else
			{
				pinned.add(unit);
			}
		}
		return pinned;
	}

	// ------------------------------------------------------------ live controls

	synchronized WorkDispatcher.NodeState nodeState(int node)
	{
		return down[node - 1] ? WorkDispatcher.NodeState.DOWN : nodeStates[node - 1];
	}

	/** Operator state (ignores crashes). */
	synchronized WorkDispatcher.NodeState operatorState(int node)
	{
		return nodeStates[node - 1];
	}

	synchronized boolean isDraining(int node)
	{
		return draining[node - 1];
	}

	synchronized double capacityScale(int node)
	{
		return capacityScales[node - 1];
	}

	/** True when the node takes new work and input: active and up (or not yet known down). */
	synchronized boolean isEligible(int node)
	{
		return nodeStates[node - 1] == WorkDispatcher.NodeState.ACTIVE
				&& !(down[node - 1] && crashDetected[node - 1]);
	}

	synchronized boolean isDown(int node)
	{
		return node >= 1 && node <= nodeCount && down[node - 1];
	}

	synchronized int crashCount(int node)
	{
		return crashCount[node - 1];
	}

	/** Pausing holds a node at its next safe boundary and stops its claims. */
	void setPaused(int node, boolean paused)
	{
		WorkDispatcher.NodeState next = paused
				? WorkDispatcher.NodeState.PAUSED : WorkDispatcher.NodeState.ACTIVE;
		List<PhaseEntry> open;
		synchronized (this)
		{
			requireOperable(node);
			if (!paused && draining[node - 1])
			{
				draining[node - 1] = false;
			}
			nodeStates[node - 1] = next;
			recordControl(paused ? Scenario.ActionKind.PAUSE : Scenario.ActionKind.RESUME, node, 0, 0,
					Double.NaN, null);
			addEvent(paused ? "pause" : "resume",
					"Node " + node + (paused
							? " stops claiming work and holds at its next safe boundary."
							: " claims work again."));
			open = openPhases();
			notifyAll();
		}
		for (PhaseEntry entry : open)
		{
			entry.phase.setNodeState(node, next);
		}
	}

	/**
	 * An unavailable node takes no new queue work and no new input users; what
	 * it already holds is handed to the other nodes. Its own data phases keep
	 * running.
	 */
	void setUnavailable(int node, boolean unavailable)
	{
		WorkDispatcher.NodeState next = unavailable
				? WorkDispatcher.NodeState.UNAVAILABLE : WorkDispatcher.NodeState.ACTIVE;
		List<PhaseEntry> open;
		synchronized (this)
		{
			requireOperable(node);
			if (unavailable)
			{
				requireAnotherEligible(node);
			}
			else
			{
				draining[node - 1] = false;
			}
			nodeStates[node - 1] = next;
			recordControl(unavailable ? Scenario.ActionKind.UNAVAILABLE : Scenario.ActionKind.AVAILABLE,
					node, 0, 0, Double.NaN, null);
			if (redirectTarget == node && unavailable)
			{
				redirectTarget = 0;
				redirectRemainingUsers = 0;
			}
			addEvent(unavailable ? "unavailable" : "available",
					"Node " + node + (unavailable
							? " takes no new work; its queued units move to other nodes."
							: " is available again."));
			open = openPhases();
			notifyAll();
		}
		for (PhaseEntry entry : open)
		{
			entry.phase.setNodeState(node, next);
		}
	}

	/**
	 * Drains a node: it becomes unavailable, its running queue work is revoked
	 * (and stops at its next cancellation check) and restarts elsewhere, and
	 * its future input users go to other nodes (preferring
	 * {@code preferredTarget} when it is eligible).
	 *
	 * @return number of revoked running units
	 */
	int drain(int node, int preferredTarget)
	{
		List<PhaseEntry> open;
		synchronized (this)
		{
			requireOperable(node);
			requireAnotherEligible(node);
			nodeStates[node - 1] = WorkDispatcher.NodeState.UNAVAILABLE;
			draining[node - 1] = true;
			drainPreferredTarget = preferredTarget >= 1 && preferredTarget <= nodeCount
					&& preferredTarget != node ? preferredTarget : 0;
			recordControl(Scenario.ActionKind.DRAIN, node, drainPreferredTarget, 0, Double.NaN, null);
			if (redirectTarget == node)
			{
				redirectTarget = 0;
				redirectRemainingUsers = 0;
			}
			open = openPhases();
			notifyAll();
		}
		int revoked = 0;
		for (PhaseEntry entry : open)
		{
			revoked += entry.phase.drain(node).size();
		}
		synchronized (this)
		{
			addEvent("drain", "Node " + node + " drained: " + revoked
					+ " running unit(s) revoked and restarted elsewhere; queued units and future input rebalanced"
					+ (drainPreferredTarget > 0 ? " (input prefers node " + drainPreferredTarget + ")" : "")
					+ ".");
		}
		return revoked;
	}

	/**
	 * Moves up to {@code count} not-yet-started queue units to {@code node} and
	 * sends it the next {@code count} unassigned input users. Other nodes keep
	 * working on everything else.
	 */
	int redirect(int node, int count)
	{
		List<PhaseEntry> open;
		synchronized (this)
		{
			requireOperable(node);
			if (!isEligible(node))
			{
				throw new IllegalStateException(
						"Node " + node + " is " + nodeState(node).name().toLowerCase(Locale.US)
						+ "; resume it before redirecting work to it.");
			}
			redirectTarget = node;
			redirectRemainingUsers = Math.max(0, count);
			recordControl(Scenario.ActionKind.REDIRECT, node, 0, count, Double.NaN, null);
			open = openPhases();
		}
		int moved = 0;
		for (PhaseEntry entry : open)
		{
			moved += entry.phase.redirect(node, count);
		}
		synchronized (this)
		{
			addEvent("redirect", "Node " + node + " received " + moved
					+ " queued unit(s); the next " + count + " new input user(s) go to it.");
		}
		return moved;
	}

	/**
	 * Changes a node's live capacity (a factor of its profile speed); future
	 * work and dilation use it at once. Speeds are absolute, so no other node
	 * changes.
	 */
	void setCapacityScale(int node, double scale)
	{
		List<PhaseEntry> open;
		List<NodeSpec> updated;
		synchronized (this)
		{
			requireOperable(node);
			double safe = Double.isNaN(scale) ? 1.0 : Math.max(0.05, Math.min(16.0, scale));
			capacityScales[node - 1] = safe;
			recordControl(Scenario.ActionKind.CAPACITY, node, 0, 0, safe, null);
			specs = NodeSpec.fromProfiles(profiles, capacityScales, isEmulating());
			updated = specs;
			double speed = specs.get(node - 1).getSpeed();
			addEvent("capacity", String.format(Locale.US,
					"Node %d capacity set to %.0f%%; its speed is now %.3f.",
					node, safe * 100.0, speed));
			if (isEmulating() && speed > clock.getDilation() + 1e-9)
			{
				recorder.warning(String.format(Locale.US,
						"Node %d was boosted to speed %.2f, above the time dilation factor %.2f: it runs at host speed. Raise the boost headroom or the TDF.",
						node, speed, clock.getDilation()));
			}
			open = openPhases();
		}
		for (PhaseEntry entry : open)
		{
			entry.phase.updateSpecs(updated);
		}
	}

	/** Switches the policy for every unit that has not started. */
	void setPolicyOverride(SchedulerLabEngine.SchedulingPolicy policy)
	{
		List<PhaseEntry> open;
		SchedulerLabEngine.SchedulingPolicy effective;
		synchronized (this)
		{
			if (mode == Mode.BASELINE)
			{
				throw new IllegalStateException(
						"The unemulated hardware mode has no policy to override; choose an emulated mode.");
			}
			policyOverride = policy;
			effective = effectivePolicy();
			recordControl(Scenario.ActionKind.POLICY, 0, 0, 0, Double.NaN, effective);
			addEvent("policy", policy == null
					? "Remaining work follows the plan policy " + basePolicy.getLabel() + " again."
					: "Remaining work now follows " + policy.getLabel() + ".");
			open = openPhases();
		}
		for (PhaseEntry entry : open)
		{
			entry.phase.switchPolicy(effective);
		}
	}

	synchronized void setPacingMs(int milliseconds)
	{
		pacingMs = Math.max(0, Math.min(15000, milliseconds));
		addEvent(pacingMs > 0 ? "action window" : "action window off",
				pacingMs > 0 ? "Each claimed unit waits " + pacingMs
						+ " ms before running so live controls can be tried; this run's timings are marked interactive."
						: "Claims run immediately.");
		if (pacingMs > 0)
		{
			recorder.warning("An interactive action window was used; this run's timings are not comparable.");
		}
	}

	void resetControls()
	{
		for (int node = 1; node <= nodeCount; node++)
		{
			if (operatorState(node) != WorkDispatcher.NodeState.ACTIVE)
			{
				setPaused(node, false);
			}
			if (Math.abs(capacityScale(node) - 1.0) > 1e-9)
			{
				setCapacityScale(node, 1.0);
			}
		}
		if (mode != Mode.BASELINE && getPolicyOverride() != null)
		{
			setPolicyOverride(null);
		}
		synchronized (this)
		{
			redirectTarget = 0;
			redirectRemainingUsers = 0;
			drainPreferredTarget = 0;
			java.util.Arrays.fill(draining, false);
			pacingMs = 0;
			addEvent("reset", "Live controls cleared.");
		}
	}

	private void recordControl(Scenario.ActionKind kind, int node, int target, int count, double value,
			SchedulerLabEngine.SchedulingPolicy policy)
	{
		recorder.control(new RunRecorder.Control(now(), kind, node, target, count, value, policy));
	}

	// ------------------------------------------------------------ crashes

	/**
	 * The node crashes now: its running units are lost (revoked), its threads
	 * block until the repair, and the coordinator notices after the detection
	 * timeout. With a finite {@code outageMs} the node is repaired that much
	 * later; NaN leaves it down until {@link #recoverNode}.
	 *
	 * @return false when the node was already down
	 */
	boolean crashNode(int node, double outageMs)
	{
		final int epoch;
		final double at;
		List<PhaseEntry> open;
		synchronized (this)
		{
			requireOperable(node);
			if (down[node - 1])
			{
				return false;
			}
			at = now();
			down[node - 1] = true;
			crashDetected[node - 1] = false;
			downSince[node - 1] = at;
			crashCount[node - 1]++;
			epoch = ++crashEpoch[node - 1];
			recordControl(Scenario.ActionKind.CRASH, node, 0, 0, outageMs, null);
			addEvent("crash", String.format(Locale.US, "Node %d crashed%s; its work in progress is lost.",
					node, Double.isNaN(outageMs) ? "" : String.format(Locale.US,
							" (repair in %s)", Scenario.formatDuration(outageMs))));
			open = openPhases();
		}
		//A crash stops the node's emulated work now, not when a sleep ends.
		emulators[node - 1].wakeSleepers();
		for (PhaseEntry entry : open)
		{
			entry.phase.crash(node);
		}
		final int crashed = node;
		timeline.schedule(at + settings.getDetectionTimeoutMs(), "crash detection of node " + node,
				new Runnable()
		{
			public void run()
			{
				detectCrash(crashed, epoch);
			}
		});
		if (!Double.isNaN(outageMs))
		{
			timeline.schedule(at + Math.max(0.0, outageMs), "repair of node " + node, new Runnable()
			{
				public void run()
				{
					if (currentEpoch(crashed) == epoch)
					{
						recoverNode(crashed);
					}
				}
			});
		}
		return true;
	}

	private synchronized int currentEpoch(int node)
	{
		return crashEpoch[node - 1];
	}

	private void detectCrash(int node, int epoch)
	{
		List<PhaseEntry> open;
		synchronized (this)
		{
			if (!down[node - 1] || crashDetected[node - 1] || crashEpoch[node - 1] != epoch)
			{
				return;
			}
			crashDetected[node - 1] = true;
			addEvent("crash detected", String.format(Locale.US,
					"The coordinator detected node %d's crash after %d ms; its work is rescheduled.",
					node, settings.getDetectionTimeoutMs()));
			open = openPhases();
		}
		for (PhaseEntry entry : open)
		{
			entry.phase.detectCrash(node);
		}
	}

	/** Repairs a crashed node; it rejoins with its data but no work in progress. */
	void recoverNode(int node)
	{
		List<PhaseEntry> open;
		synchronized (this)
		{
			requireOperable(node);
			if (!down[node - 1])
			{
				return;
			}
			down[node - 1] = false;
			crashDetected[node - 1] = false;
			recordControl(Scenario.ActionKind.RECOVER, node, 0, 0, Double.NaN, null);
			addEvent("repair", String.format(Locale.US, "Node %d repaired after %s of downtime.",
					node, Scenario.formatDuration(now() - downSince[node - 1])));
			open = openPhases();
			notifyAll();
		}
		for (PhaseEntry entry : open)
		{
			entry.phase.recover(node);
		}
	}

	/** Blocks while the node is down (its threads stall during the outage). */
	void awaitNodeUp(int node)
	{
		synchronized (this)
		{
			waitingForRepair[node - 1]++;
			try
			{
				while (down[node - 1])
				{
					requireActive();
					try
					{
						wait(100L);
					}
					catch (InterruptedException interrupted)
					{
						Thread.currentThread().interrupt();
						throw new IllegalStateException(
								"Interrupted while node " + node + " was down.", interrupted);
					}
				}
			}
			finally
			{
				waitingForRepair[node - 1]--;
			}
		}
	}

	/** How many of the node's threads are stalled by its outage right now. */
	synchronized int threadsWaitingForRepair(int node)
	{
		return waitingForRepair[node - 1];
	}

	synchronized int getRedirectTarget()
	{
		return redirectTarget;
	}

	synchronized int getRedirectRemainingUsers()
	{
		return redirectRemainingUsers;
	}

	synchronized int getDrainPreferredTarget()
	{
		return drainPreferredTarget;
	}

	synchronized void clearRedirect()
	{
		redirectTarget = 0;
		redirectRemainingUsers = 0;
		addEvent("redirect", "Input redirect cleared.");
	}

	/** Records an operator action that was refused, with the reason. */
	synchronized void recordRejected(String action, String reason)
	{
		addEvent(action + " refused", reason);
	}

	/** Records that a scenario step fired. */
	synchronized void recordScenarioStep(Scenario.Step step)
	{
		addEvent("scenario", "Line " + step.getLine() + ": " + step.describe());
	}

	/** Blocks while the node is paused (or down), for node-local phase boundaries. */
	void awaitNotPaused(int node, String boundary)
	{
		boolean logged = false;
		synchronized (this)
		{
			while (nodeStates[node - 1] == WorkDispatcher.NodeState.PAUSED || down[node - 1])
			{
				requireActive();
				if (!logged)
				{
					logged = true;
					addEvent("hold", "Node " + node + " holds at " + boundary
							+ (down[node - 1] ? " while down." : " while paused."));
				}
				try
				{
					wait(250L);
				}
				catch (InterruptedException interrupted)
				{
					Thread.currentThread().interrupt();
					throw new IllegalStateException(
							"Interrupted while node " + node + " was paused.", interrupted);
				}
			}
			if (logged)
			{
				addEvent("release", "Node " + node + " continues past " + boundary + ".");
			}
		}
	}

	// ------------------------------------------------------------ input routing

	/**
	 * Receiver for a non-replicated input user, decided once per user so a
	 * user's rows never split across nodes. Returns 0 while the user must be
	 * held (its node is paused and nothing moves it elsewhere).
	 */
	synchronized int routeUser(String user, int originalNode, double rows)
	{
		return routeUsers(Collections.singletonList(user), originalNode, rows);
	}

	/**
	 * One receiver for a batch of whole, non-replicated input users, decided
	 * once: users already routed (a resent batch) keep their receiver, new
	 * users all go to the same node. An operator redirect takes the next
	 * batches until it has moved at least its count of users; a user whose
	 * node is unavailable (or known to be down) goes to the drain's preferred
	 * target, else to the eligible node that would finish its rerouted input
	 * first. Returns 0 while the batch must be held (its node is paused and
	 * nothing moves it).
	 */
	synchronized int routeUsers(List<String> users, int originalNode, double rows)
	{
		if (users == null || users.isEmpty())
		{
			return originalNode >= 1 && originalNode <= nodeCount && isEligible(originalNode)
					? originalNode : 0;
		}
		for (String user : users)
		{
			Integer decided = userReceiver.get(user);
			if (decided != null)
			{
				for (String other : users)
				{
					if (!userReceiver.containsKey(other))
					{
						userReceiver.put(other, decided);
					}
				}
				return decided.intValue();
			}
		}
		int receiver;
		if (redirectRemainingUsers > 0 && redirectTarget > 0 && isEligible(redirectTarget))
		{
			receiver = redirectTarget;
			redirectRemainingUsers = Math.max(0, redirectRemainingUsers - users.size());
			if (redirectRemainingUsers == 0)
			{
				addEvent("redirect", "Input redirect to node " + redirectTarget + " consumed.");
				redirectTarget = 0;
			}
		}
		else if (originalNode >= 1 && originalNode <= nodeCount && isEligible(originalNode))
		{
			receiver = originalNode;
		}
		else if (originalNode >= 1 && originalNode <= nodeCount
				&& nodeStates[originalNode - 1] == WorkDispatcher.NodeState.PAUSED)
		{
			return 0;
		}
		else
		{
			receiver = drainPreferredTarget > 0 && isEligible(drainPreferredTarget)
					? drainPreferredTarget : leastReroutedEligible(rows);
			if (receiver == 0)
			{
				return 0;
			}
			reroutedWork[receiver - 1] += Math.max(0.0, rows);
		}
		for (String user : users)
		{
			userReceiver.put(user, Integer.valueOf(receiver));
		}
		if (receiver != originalNode)
		{
			addEvent("input moved", users.size() + " user(s) starting with " + users.get(0)
					+ " go to node " + receiver + " instead of node " + originalNode + ".");
		}
		return receiver;
	}

	private final Map<String,Integer> plannedOwners = new java.util.HashMap<String,Integer>();

	/** The controller's placement: each user's planned primary node. */
	synchronized void recordPrimaryNodes(Map<String,Integer> primaryByUser)
	{
		if (primaryByUser != null)
		{
			plannedOwners.putAll(primaryByUser);
		}
	}

	/** A user's planned primary node, or 0 when the placement does not list it. */
	synchronized int plannedOwnerOf(String user)
	{
		Integer owner = plannedOwners.get(user);
		return owner == null ? 0 : owner.intValue();
	}

	/** True while a replicated input batch must wait for a paused node. */
	synchronized boolean holdReplicatedInput()
	{
		for (int node = 1; node <= nodeCount; node++)
		{
			if (nodeStates[node - 1] == WorkDispatcher.NodeState.PAUSED)
			{
				return true;
			}
		}
		return false;
	}

	synchronized Map<String,Integer> movedUsers()
	{
		return new LinkedHashMap<String,Integer>(userReceiver);
	}

	private int leastReroutedEligible(double rows)
	{
		int best = 0;
		double bestFinish = Double.POSITIVE_INFINITY;
		for (int node = 1; node <= nodeCount; node++)
		{
			if (!isEligible(node))
			{
				continue;
			}
			double finish = (reroutedWork[node - 1] + Math.max(0.0, rows))
					/ Math.max(1.0e-9, specs.get(node - 1).getSpeed());
			if (finish < bestFinish)
			{
				best = node;
				bestFinish = finish;
			}
		}
		return best;
	}

	// ------------------------------------------------------------ input progress

	private int inputUsersPlanned;
	private boolean streamedInput;
	private final LinkedHashMap<String,Integer> inputInFlight = new LinkedHashMap<String,Integer>();
	private final LinkedHashMap<String,Integer> inputDelivered = new LinkedHashMap<String,Integer>();

	/**
	 * The streamer announces how many non-replicated users it will deliver.
	 * From then on input is routed per user at run time, so a node cannot know
	 * in advance how many tweets it will receive: it finishes its input when
	 * the delivery-complete notice arrives, which is sent only after every
	 * batch was acknowledged.
	 */
	synchronized void inputPlanned(int users)
	{
		inputUsersPlanned = Math.max(0, users);
		streamedInput = true;
	}

	/** True when input is streamed with run-time routing (see {@link #inputPlanned}). */
	synchronized boolean isStreamedInput()
	{
		return streamedInput;
	}

	/** A user's first batch left the streamer for {@code node}. */
	synchronized void inputDispatched(String user, int node)
	{
		if (!inputDelivered.containsKey(user) && !inputInFlight.containsKey(user))
		{
			inputInFlight.put(user, Integer.valueOf(node));
		}
	}

	/** Every batch of a user was acknowledged by {@code node}. */
	void inputDelivered(String user, int node)
	{
		synchronized (this)
		{
			inputInFlight.remove(user);
			if (!inputDelivered.containsKey(user))
			{
				inputDelivered.put(user, Integer.valueOf(node));
			}
		}
		timeline.wake();
	}

	synchronized int getInputUsersPlanned()
	{
		return inputUsersPlanned;
	}

	/** Delivered fraction of the streamed input users (0 before streaming starts). */
	synchronized double inputProgress()
	{
		return inputUsersPlanned <= 0 ? 0.0 : inputDelivered.size() / (double)inputUsersPlanned;
	}

	synchronized Map<String,Integer> inputInFlightByUser()
	{
		return new LinkedHashMap<String,Integer>(inputInFlight);
	}

	synchronized Map<String,Integer> inputDeliveredByUser()
	{
		return new LinkedHashMap<String,Integer>(inputDelivered);
	}

	// ------------------------------------------------------------ reporting

	synchronized List<ControlEvent> events()
	{
		ArrayList<ControlEvent> copy = new ArrayList<ControlEvent>(events);
		Collections.reverse(copy);
		return copy;
	}

	synchronized boolean hasActiveControls()
	{
		if (policyOverride != null || redirectRemainingUsers > 0 || pacingMs > 0)
		{
			return true;
		}
		for (int node = 0; node < nodeCount; node++)
		{
			if (nodeStates[node] != WorkDispatcher.NodeState.ACTIVE || draining[node] || down[node]
					|| Math.abs(capacityScales[node] - 1.0) > 1e-9)
			{
				return true;
			}
		}
		return false;
	}

	synchronized String speedSummary()
	{
		StringBuilder builder = new StringBuilder();
		for (NodeSpec spec : specs)
		{
			if (builder.length() > 0)
			{
				builder.append(", ");
			}
			builder.append(String.format(Locale.US, "n%d=%.2f", spec.getIndex(), spec.getSpeed()));
		}
		return builder.toString();
	}

	/** Average host CPU utilization since the run started (0..1 of all cores), or NaN. */
	double hostUtilization()
	{
		long cpu = processCpuNanos();
		if (cpu < 0L || hostCpuAtStartNanos < 0L)
		{
			return Double.NaN;
		}
		double wall = System.nanoTime() - hostWallAtStartNanos;
		int cores = Runtime.getRuntime().availableProcessors();
		return wall <= 0.0 ? Double.NaN : (cpu - hostCpuAtStartNanos) / (wall * cores);
	}

	private static long processCpuNanos()
	{
		try
		{
			java.lang.management.OperatingSystemMXBean bean = ManagementFactory.getOperatingSystemMXBean();
			if (bean instanceof com.sun.management.OperatingSystemMXBean)
			{
				return ((com.sun.management.OperatingSystemMXBean)bean).getProcessCpuTime();
			}
		}
		catch (Throwable unsupported)
		{
			//Not available on this JVM.
		}
		return -1L;
	}

	/** One line per node: where its wall time went, plus fidelity notes. */
	List<String> timingReport()
	{
		ArrayList<String> lines = new ArrayList<String>();
		lines.add(String.format(Locale.US, "Mode %s, TDF %.2f, model time %.1f ms",
				mode.getLabel(), clock.getDilation(), now()));
		for (int node = 1; node <= nodeCount; node++)
		{
			lines.add("Node " + node + ": " + emulator(node).getLedger().summary());
		}
		double utilization = hostUtilization();
		if (!Double.isNaN(utilization))
		{
			lines.add(String.format(Locale.US, "Host CPU utilization during the run: %.0f%% of %d cores%s",
					utilization * 100.0, Runtime.getRuntime().availableProcessors(),
					utilization > 0.85 ? " (saturated: timings are distorted; raise the TDF)" : ""));
		}
		for (String warning : recorder.warnings())
		{
			lines.add("Note: " + warning);
		}
		return lines;
	}

	synchronized String describe()
	{
		return mode.getLabel() + " | " + effectivePolicy().getLabel()
				+ " | " + nodeCount + " node" + (nodeCount == 1 ? "" : "s")
				+ " | TDF " + String.format(Locale.US, "%.2f", clock.getDilation())
				+ " | speeds " + speedSummary();
	}

	/** Ends the run's scheduling: open phases and collectives release waiters. */
	void close(String reason)
	{
		List<PhaseEntry> open;
		synchronized (this)
		{
			if (closed)
			{
				return;
			}
			closed = true;
			open = new ArrayList<PhaseEntry>(phases.values());
			addEvent("end", reason == null ? "Run ended." : reason);
			for (int node = 1; node <= nodeCount; node++)
			{
				if (down[node - 1])
				{
					down[node - 1] = false;
				}
			}
			notifyAll();
		}
		timeline.stop();
		for (PhaseEntry entry : open)
		{
			entry.phase.close(reason == null ? "run ended" : reason);
		}
		collectives.clear();
	}

	boolean isClosed()
	{
		return closed;
	}

	private List<PhaseEntry> openPhases()
	{
		ArrayList<PhaseEntry> open = new ArrayList<PhaseEntry>();
		for (PhaseEntry entry : phases.values())
		{
			if (!entry.phase.getDispatcher().isComplete() && !entry.phase.isClosed())
			{
				open.add(entry);
			}
		}
		return open;
	}

	private void requireOperable(int node)
	{
		if (node < 1 || node > nodeCount)
		{
			throw new IllegalArgumentException("Node " + node + " is outside 1.." + nodeCount + ".");
		}
		if (closed)
		{
			throw new IllegalStateException("Run " + runId + " has ended.");
		}
	}

	private void requireAnotherEligible(int node)
	{
		for (int other = 1; other <= nodeCount; other++)
		{
			if (other != node && nodeStates[other - 1] == WorkDispatcher.NodeState.ACTIVE)
			{
				return;
			}
		}
		throw new IllegalStateException(
				"At least one other node must stay available to receive node " + node + "'s work.");
	}

	private void addEvent(String action, String detail)
	{
		double at = clock == null ? 0.0 : clock.nowMs();
		events.add(new ControlEvent(System.currentTimeMillis(), at, action, detail));
		while (events.size() > MAX_EVENTS)
		{
			events.remove(0);
		}
		if (recorder != null)
		{
			recorder.event(at, action, 0, detail);
		}
	}
}
