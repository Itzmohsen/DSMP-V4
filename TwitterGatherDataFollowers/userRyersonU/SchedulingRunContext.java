package TwitterGatherDataFollowers.userRyersonU;

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
 * The controller creates it from the scheduler plan and the node capacity
 * profiles. From then on the plan and the profiles no longer change for this
 * run, so every node, the corpus streamer and the panels read one consistent
 * configuration; only the operator's live controls (pause, unavailable, drain,
 * redirect, capacity scale, policy override, action window) change it, and each
 * control applies at once to every open work phase and to input routing.
 */
final class SchedulingRunContext
{
	enum Mode
	{
		BASELINE("Original DSMP",
				"V3 list-scheduling placement, no capacity emulation; work stays on the node that holds its data."),
		EMULATED("Scheduled, capacity emulated",
				"Policy placement and dispatch; node speed, lanes, memory, queue and link delay are physically emulated."),
		FAULT_INJECTION("Scheduled, fault injection",
				"As emulated, plus injected unit failures at each node's failure probability, with exactly-once retries.");

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

		private PhaseEntry(String key, String label, WorkPhase phase)
		{
			this.key = key;
			this.label = label;
			this.phase = phase;
			this.openedAtMillis = System.currentTimeMillis();
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
	}

	static final class ControlEvent
	{
		private final long time;
		private final String action;
		private final String detail;

		private ControlEvent(long time, String action, String detail)
		{
			this.time = time;
			this.action = action;
			this.detail = detail;
		}

		long getTime()
		{
			return time;
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

	private static final int MAX_EVENTS = 200;

	private final String runId;
	private final Mode mode;
	private final SchedulerLabEngine.SchedulingPolicy basePolicy;
	private final int nodeCount;
	private final int algorithm;
	private final List<NodeCapacityProfile> profiles;
	private final long faultSeed;
	private final int maxFailuresPerUnit;
	private final long stallTimeoutMs;
	private final long createdAtMillis = System.currentTimeMillis();

	private final WorkDispatcher.NodeState[] nodeStates;
	private final boolean[] draining;
	private final double[] capacityScales;
	private SchedulerLabEngine.SchedulingPolicy policyOverride;
	private int pacingMs;
	private List<NodeSpec> specs;

	private final NodeEmulator[] emulators;
	private final RunCollectives collectives;
	private final LinkedHashMap<String,PhaseEntry> phases = new LinkedHashMap<String,PhaseEntry>();
	private final ArrayList<ControlEvent> events = new ArrayList<ControlEvent>();

	//Input routing under live controls: one receiver per user, decided once.
	private final LinkedHashMap<String,Integer> userReceiver = new LinkedHashMap<String,Integer>();
	private final double[] reroutedWork;
	private int redirectTarget;
	private int redirectRemainingUsers;
	private int drainPreferredTarget;
	private String placementSummary = "";
	private final List<String> placementWarnings = new ArrayList<String>();
	private volatile boolean closed;

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
		this.runId = runId == null ? "" : runId;
		this.mode = mode == null ? Mode.BASELINE : mode;
		this.basePolicy = policy == null
				? SchedulerLabEngine.SchedulingPolicy.CAPACITY_WEIGHTED : policy;
		this.nodeCount = Math.max(1, nodeCount);
		this.algorithm = algorithm;
		this.profiles = Collections.unmodifiableList(denseProfiles(profiles, this.nodeCount));
		this.faultSeed = faultSeed;
		this.maxFailuresPerUnit = Math.max(1, maxFailuresPerUnit);
		this.stallTimeoutMs = stallTimeoutMs <= 0L ? Long.MAX_VALUE / 4 : stallTimeoutMs;
		this.pacingMs = Math.max(0, pacingMs);
		nodeStates = new WorkDispatcher.NodeState[this.nodeCount];
		java.util.Arrays.fill(nodeStates, WorkDispatcher.NodeState.ACTIVE);
		draining = new boolean[this.nodeCount];
		capacityScales = new double[this.nodeCount];
		java.util.Arrays.fill(capacityScales, 1.0);
		reroutedWork = new double[this.nodeCount];
		emulators = new NodeEmulator[this.nodeCount];
		for (int node = 1; node <= this.nodeCount; node++)
		{
			emulators[node - 1] = new NodeEmulator(this, node);
		}
		collectives = new RunCollectives(this, this.nodeCount);
		specs = NodeSpec.fromProfiles(this.profiles, capacityScales, isEmulating());
		addEvent("run", "Run " + this.runId + ": " + this.mode.getLabel() + ", policy "
				+ this.basePolicy.getLabel() + ", " + this.nodeCount + " node(s).");
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

	synchronized SchedulerLabEngine.SchedulingPolicy getBasePolicy()
	{
		return basePolicy;
	}

	/** The policy work phases use now: the live override, else the plan's policy. */
	synchronized SchedulerLabEngine.SchedulingPolicy effectivePolicy()
	{
		if (mode == Mode.BASELINE)
		{
			return SchedulerLabEngine.SchedulingPolicy.ROUND_ROBIN;
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
	 * on one consistent set of units. In baseline mode units that have a home
	 * node may only run there, as in the original simulator.
	 */
	WorkPhase openPhase(String key, String label, UnitSupplier supplier)
	{
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
			WorkDispatcher dispatcher = new WorkDispatcher(
					runId + "/" + key,
					effectivePolicy(),
					specs,
					units,
					new WorkDispatcher.Options()
							.faultInjection(isFaultInjection(), faultSeed)
							.maxFailuresPerUnit(maxFailuresPerUnit)
							.msPerTimeUnit(1.0)
							.maxRetainedEvents(4000));
			for (int node = 1; node <= nodeCount; node++)
			{
				WorkDispatcher.NodeState state = nodeStates[node - 1];
				if (state != WorkDispatcher.NodeState.ACTIVE)
				{
					dispatcher.setNodeState(node, state, 0.0);
				}
			}
			WorkPhase phase = new WorkPhase(dispatcher, runId, stallTimeoutMs);
			phases.put(key, new PhaseEntry(key, label == null ? key : label, phase));
			addEvent("phase", "Opened " + (label == null ? key : label) + ": "
					+ units.size() + " unit(s), " + dispatcher.getPolicy().getLabel()
					+ " (" + dispatcher.getMode().name().toLowerCase(Locale.US) + ").");
			return phase;
		}
	}

	synchronized List<PhaseEntry> phases()
	{
		return new ArrayList<PhaseEntry>(phases.values());
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

	private static List<WorkUnitSpec> pinToHome(List<WorkUnitSpec> units)
	{
		ArrayList<WorkUnitSpec> pinned = new ArrayList<WorkUnitSpec>();
		for (WorkUnitSpec unit : units)
		{
			if (unit.getHomeNode() > 0 && !unit.isRestricted())
			{
				pinned.add(new WorkUnitSpec(unit.getId(), unit.getKey(), unit.getCost(),
						unit.getMemoryMb(), unit.getPriority(), unit.getDeadline(),
						unit.getHomeNode(), new int[] {unit.getHomeNode()},
						unit.getMaxFailures()));
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

	synchronized boolean isEligible(int node)
	{
		return nodeStates[node - 1] == WorkDispatcher.NodeState.ACTIVE;
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
	 * and restarted elsewhere, and its future input users go to other nodes
	 * (preferring {@code preferredTarget} when it is eligible). The other nodes
	 * share the drained work by the active policy; nobody is blocked.
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
					+ " running unit(s) restarted elsewhere; queued units and future input rebalanced"
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
			if (nodeStates[node - 1] != WorkDispatcher.NodeState.ACTIVE)
			{
				throw new IllegalStateException(
						"Node " + node + " is " + nodeStates[node - 1].name().toLowerCase(Locale.US)
						+ "; resume it before redirecting work to it.");
			}
			redirectTarget = node;
			redirectRemainingUsers = Math.max(0, count);
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

	/** Changes a node's live capacity; future work and dilation use it at once. */
	void setCapacityScale(int node, double scale)
	{
		List<PhaseEntry> open;
		List<NodeSpec> updated;
		synchronized (this)
		{
			requireOperable(node);
			double safe = Double.isNaN(scale) ? 1.0 : Math.max(0.05, Math.min(4.0, scale));
			capacityScales[node - 1] = safe;
			specs = NodeSpec.fromProfiles(profiles, capacityScales, isEmulating());
			updated = specs;
			addEvent("capacity", String.format(Locale.US,
					"Node %d capacity set to %.0f%%; speeds now %s.",
					node, safe * 100.0, speedSummary()));
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
						"The original DSMP mode has no policy to override; choose a scheduled mode.");
			}
			policyOverride = policy;
			effective = effectivePolicy();
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
	}

	void resetControls()
	{
		for (int node = 1; node <= nodeCount; node++)
		{
			if (nodeState(node) != WorkDispatcher.NodeState.ACTIVE)
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
			setPacingMs(0);
			addEvent("reset", "Live controls cleared.");
		}
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

	/** Blocks while the node is paused, for node-local phase boundaries. */
	void awaitNotPaused(int node, String boundary)
	{
		boolean logged = false;
		synchronized (this)
		{
			while (nodeStates[node - 1] == WorkDispatcher.NodeState.PAUSED)
			{
				requireActive();
				if (!logged)
				{
					logged = true;
					addEvent("hold", "Node " + node + " holds at " + boundary + " while paused.");
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
	 * node is unavailable goes to the drain's preferred target, else to the
	 * eligible node that would finish its rerouted input first. Returns 0
	 * while the batch must be held (its node is paused and nothing moves it).
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
	private final int[] inputDeliveredByNode = new int[64];
	private final ArrayList<ControlEvent> inputEvents = new ArrayList<ControlEvent>();

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
	synchronized void inputDelivered(String user, int node)
	{
		inputInFlight.remove(user);
		if (!inputDelivered.containsKey(user))
		{
			inputDelivered.put(user, Integer.valueOf(node));
			if (node >= 1 && node <= inputDeliveredByNode.length)
			{
				inputDeliveredByNode[node - 1]++;
			}
		}
	}

	synchronized int getInputUsersPlanned()
	{
		return inputUsersPlanned;
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
			if (nodeStates[node] != WorkDispatcher.NodeState.ACTIVE || draining[node]
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

	/** One line per node: where its wall time went. */
	List<String> timingReport()
	{
		ArrayList<String> lines = new ArrayList<String>();
		int lanes = 0;
		for (int node = 1; node <= nodeCount; node++)
		{
			lanes += lanesOf(node);
			lines.add("Node " + node + ": " + emulator(node).getLedger().summary());
		}
		int cores = Runtime.getRuntime().availableProcessors();
		if (isEmulating() && lanes > cores)
		{
			lines.add("Note: " + lanes + " emulated lanes share " + cores
					+ " host core(s); CPU contention adds time on top of the emulated speeds.");
		}
		if (getPacingMs() > 0)
		{
			lines.add("Note: an interactive action window was active; these timings are not comparable.");
		}
		return lines;
	}

	synchronized String describe()
	{
		return mode.getLabel() + " | " + effectivePolicy().getLabel()
				+ " | " + nodeCount + " node" + (nodeCount == 1 ? "" : "s")
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
			notifyAll();
		}
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
		events.add(new ControlEvent(System.currentTimeMillis(), action, detail));
		while (events.size() > MAX_EVENTS)
		{
			events.remove(0);
		}
	}
}
