package TwitterGatherDataFollowers.userRyersonU;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class LiveWorkloadAdjustmentRegistry
{
	static final class NodeControl
	{
		private final int nodeIndex;
		private final boolean paused;
		private final boolean unavailable;
		private final double capacityScale;
		private final int drainTargetNode;

		private NodeControl(
				int nodeIndex,
				boolean paused,
				boolean unavailable,
				double capacityScale,
				int drainTargetNode)
		{
			this.nodeIndex = Math.max(1, nodeIndex);
			this.paused = paused;
			this.unavailable = unavailable;
			this.capacityScale = clamp(capacityScale, 0.05, 4.0);
			this.drainTargetNode = Math.max(0, drainTargetNode);
		}

		static NodeControl defaults(int nodeIndex)
		{
			return new NodeControl(nodeIndex, false, false, 1.0, 0);
		}

		NodeControl withPaused(boolean value)
		{
			return new NodeControl(
					nodeIndex,
					value,
					unavailable,
					capacityScale,
					value ? drainTargetNode : 0);
		}

		NodeControl withUnavailable(boolean value)
		{
			return new NodeControl(
					nodeIndex,
					paused,
					value,
					capacityScale,
					value ? 0 : drainTargetNode);
		}

		NodeControl withCapacityScale(double value)
		{
			return new NodeControl(
					nodeIndex,
					paused,
					unavailable,
					value,
					drainTargetNode);
		}

		NodeControl withDrainTargetNode(int targetNode)
		{
			return new NodeControl(
					nodeIndex,
					true,
					unavailable,
					capacityScale,
					Math.max(0, targetNode));
		}

		int getNodeIndex()
		{
			return nodeIndex;
		}

		boolean isPaused()
		{
			return paused;
		}

		boolean isUnavailable()
		{
			return unavailable;
		}

		double getCapacityScale()
		{
			return capacityScale;
		}

		int getDrainTargetNode()
		{
			return drainTargetNode;
		}

		boolean isDraining()
		{
			return paused && drainTargetNode > 0;
		}

		boolean isDefault()
		{
			return !paused
					&& !unavailable
					&& drainTargetNode <= 0
					&& Math.abs(capacityScale - 1.0) < 0.0000001;
		}

		String statusLabel()
		{
			if (unavailable)
			{
				return "unavailable";
			}
			if (isDraining())
			{
				return "draining to Node " + drainTargetNode;
			}
			if (paused)
			{
				return "paused";
			}
			if (Math.abs(capacityScale - 1.0) > 0.0000001)
			{
				return Math.round(capacityScale * 100.0) + "% capacity";
			}
			return "normal";
		}
	}

	static final class ControlEvent
	{
		private final long time;
		private final String action;
		private final String detail;

		private ControlEvent(long time, String action, String detail)
		{
			this.time = Math.max(0L, time);
			this.action = action == null ? "" : action;
			this.detail = detail == null ? "" : detail;
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

	static final class Snapshot
	{
		private final String runId;
		private final int nodeCount;
		private final Map<Integer,NodeControl> controls;
		private final SchedulerLabEngine.SchedulingPolicy policyOverride;
		private final int redirectTargetNode;
		private final int redirectRemaining;
		private final int pacingDelayMs;
		private final List<ControlEvent> events;

		private Snapshot(
				String runId,
				int nodeCount,
				Map<Integer,NodeControl> controls,
				SchedulerLabEngine.SchedulingPolicy policyOverride,
				int redirectTargetNode,
				int redirectRemaining,
				int pacingDelayMs,
				List<ControlEvent> events)
		{
			this.runId = normalizeRunId(runId);
			this.nodeCount = Math.max(1, nodeCount);
			this.controls = Collections.unmodifiableMap(
					new LinkedHashMap<Integer,NodeControl>(controls));
			this.policyOverride = policyOverride;
			this.redirectTargetNode = Math.max(0, redirectTargetNode);
			this.redirectRemaining = Math.max(0, redirectRemaining);
			this.pacingDelayMs = clampDelay(pacingDelayMs);
			this.events = Collections.unmodifiableList(
					new ArrayList<ControlEvent>(events));
		}

		String getRunId()
		{
			return runId;
		}

		int getNodeCount()
		{
			return nodeCount;
		}

		SchedulerLabEngine.SchedulingPolicy getPolicyOverride()
		{
			return policyOverride;
		}

		int getRedirectTargetNode()
		{
			return redirectTargetNode;
		}

		int getRedirectRemaining()
		{
			return redirectRemaining;
		}

		int getPacingDelayMs()
		{
			return pacingDelayMs;
		}

		List<ControlEvent> getEvents()
		{
			return events;
		}

		NodeControl controlForNode(int nodeIndex)
		{
			NodeControl control = controls.get(Integer.valueOf(nodeIndex));
			return control == null
					? NodeControl.defaults(nodeIndex)
					: control;
		}

		boolean isPaused(int nodeIndex)
		{
			return controlForNode(nodeIndex).isPaused();
		}

		boolean isUnavailable(int nodeIndex)
		{
			return controlForNode(nodeIndex).isUnavailable();
		}

		boolean isEligible(int nodeIndex)
		{
			NodeControl control = controlForNode(nodeIndex);
			return !control.isPaused() && !control.isUnavailable();
		}

		double capacityScale(int nodeIndex)
		{
			return controlForNode(nodeIndex).getCapacityScale();
		}

		boolean hasActiveControls()
		{
			if (policyOverride != null || redirectRemaining > 0
					|| pacingDelayMs > 0)
			{
				return true;
			}
			for (NodeControl control : controls.values())
			{
				if (control != null && !control.isDefault())
				{
					return true;
				}
			}
			return false;
		}

		String compactSummary()
		{
			int paused = 0;
			int draining = 0;
			int unavailable = 0;
			int throttled = 0;
			for (NodeControl control : controls.values())
			{
				if (control.isUnavailable())
				{
					unavailable++;
				}
				else if (control.isDraining())
				{
					draining++;
				}
				else if (control.isPaused())
				{
					paused++;
				}
				if (Math.abs(control.getCapacityScale() - 1.0) > 0.0000001)
				{
					throttled++;
				}
			}
			StringBuilder builder = new StringBuilder();
			if (!hasActiveControls())
			{
				return "No live workload overrides.";
			}
			if (policyOverride != null)
			{
				builder.append("policy ")
						.append(policyOverride.getLabel());
			}
			if (redirectRemaining > 0)
			{
				appendSeparator(builder);
				builder.append("next ")
						.append(redirectRemaining)
						.append(" pending work item")
						.append(redirectRemaining == 1 ? "" : "s")
						.append(" to Node ")
						.append(redirectTargetNode);
			}
			if (paused > 0)
			{
				appendSeparator(builder);
				builder.append(paused).append(" paused");
			}
			if (draining > 0)
			{
				appendSeparator(builder);
				builder.append(draining).append(" draining");
			}
			if (unavailable > 0)
			{
				appendSeparator(builder);
				builder.append(unavailable).append(" unavailable");
			}
			if (throttled > 0)
			{
				appendSeparator(builder);
				builder.append(throttled).append(" capacity override")
						.append(throttled == 1 ? "" : "s");
			}
			if (pacingDelayMs > 0)
			{
				appendSeparator(builder);
				builder.append("action window ")
						.append(formatDelay(pacingDelayMs));
			}
			return builder.toString();
		}
	}

	private static final Object LOCK = new Object();
	private static final int MAX_EVENTS = 80;
	private static final int MAX_PACING_DELAY_MS = 15000;
	private static String activeRunId = "";
	private static int activeNodeCount = 1;
	private static int nextRunPacingDelayMs;
	private static int pacingDelayMs;
	private static final LinkedHashMap<Integer,NodeControl> CONTROLS =
			new LinkedHashMap<Integer,NodeControl>();
	private static SchedulerLabEngine.SchedulingPolicy policyOverride;
	private static int redirectTargetNode;
	private static int redirectRemaining;
	private static final ArrayList<ControlEvent> EVENTS =
			new ArrayList<ControlEvent>();

	private LiveWorkloadAdjustmentRegistry()
	{
	}

	static void beginRun(String runId, int nodeCount)
	{
		synchronized (LOCK)
		{
			activeRunId = normalizeRunId(runId);
			activeNodeCount = Math.max(1, nodeCount);
			CONTROLS.clear();
			policyOverride = null;
			redirectTargetNode = 0;
			redirectRemaining = 0;
			pacingDelayMs = nextRunPacingDelayMs;
			EVENTS.clear();
			addEventLocked(
					"run",
					"Live workload adjustment reset for run "
							+ activeRunId + ".");
			if (pacingDelayMs > 0)
			{
				addEventLocked(
						"action window",
						"Every newly claimed queue work item pauses for "
								+ formatDelay(pacingDelayMs)
								+ " before scoring so live controls can be applied.");
			}
		}
	}

	static Snapshot snapshot(String runId)
	{
		synchronized (LOCK)
		{
			return snapshotLocked(runId);
		}
	}

	static Snapshot snapshot()
	{
		synchronized (LOCK)
		{
			return snapshotLocked(activeRunId);
		}
	}

	static String activeRunId()
	{
		synchronized (LOCK)
		{
			return activeRunId;
		}
	}

	static int nextRunPacingDelayMs()
	{
		synchronized (LOCK)
		{
			return nextRunPacingDelayMs;
		}
	}

	static void setNextRunPacingDelayMs(int delayMs)
	{
		synchronized (LOCK)
		{
			nextRunPacingDelayMs = clampDelay(delayMs);
		}
	}

	static void setPacingDelayMs(String runId, int delayMs)
	{
		synchronized (LOCK)
		{
			if (!matchesActiveRunLocked(runId))
			{
				return;
			}
			pacingDelayMs = clampDelay(delayMs);
			addEventLocked(
					pacingDelayMs > 0 ? "action window" : "action window off",
					pacingDelayMs > 0
							? "Newly claimed queue work items pause for "
									+ formatDelay(pacingDelayMs)
									+ " before scoring."
							: "Queue claims continue immediately.");
		}
	}

	static void applyClaimPacingDelay(String runId)
			throws InterruptedException
	{
		int delay;
		synchronized (LOCK)
		{
			if (!matchesActiveRunLocked(runId))
			{
				return;
			}
			delay = pacingDelayMs;
		}
		if (delay <= 0)
		{
			return;
		}
		long deadline = System.currentTimeMillis() + delay;
		while (true)
		{
			long remaining = deadline - System.currentTimeMillis();
			if (remaining <= 0L)
			{
				return;
			}
			Thread.sleep(Math.min(100L, remaining));
		}
	}

	static void waitUntilNodeEligible(
			String runId,
			int nodeIndex,
			String phaseLabel) throws InterruptedException
	{
		boolean loggedWait = false;
		String safePhase = phaseLabel == null || phaseLabel.trim().length() == 0
				? "safe phase boundary" : phaseLabel.trim();
		while (true)
		{
			synchronized (LOCK)
			{
				if (!matchesActiveRunLocked(runId))
				{
					return;
				}
				int safeNode = safeNode(nodeIndex);
				NodeControl control = controlLocked(safeNode);
				if (!control.isPaused() && !control.isUnavailable())
				{
					if (loggedWait)
					{
						addEventLocked(
								"phase resumed",
								"Node " + safeNode
										+ " resumed from " + safePhase + ".");
					}
					return;
				}
				if (!loggedWait)
				{
					loggedWait = true;
					addEventLocked(
							"phase paused",
							"Node " + safeNode + " is waiting at "
									+ safePhase + " while "
									+ control.statusLabel() + ".");
				}
			}
			Thread.sleep(200L);
		}
	}

	static void setPaused(String runId, int nodeIndex, boolean paused)
	{
		synchronized (LOCK)
		{
			if (!matchesActiveRunLocked(runId))
			{
				return;
			}
			int safeNode = safeNode(nodeIndex);
			NodeControl control = controlLocked(safeNode).withPaused(paused);
			CONTROLS.put(Integer.valueOf(safeNode), control);
			addEventLocked(
					paused ? "pause" : "resume",
					"Node " + safeNode + (paused
							? " will stop claiming new pending work items."
							: " can claim pending work items again."));
		}
	}

	static void setUnavailable(String runId, int nodeIndex, boolean unavailable)
	{
		synchronized (LOCK)
		{
			if (!matchesActiveRunLocked(runId))
			{
				return;
			}
			int safeNode = safeNode(nodeIndex);
			NodeControl control = controlLocked(safeNode)
					.withUnavailable(unavailable);
			CONTROLS.put(Integer.valueOf(safeNode), control);
			if (redirectTargetNode == safeNode && unavailable)
			{
				redirectTargetNode = 0;
				redirectRemaining = 0;
			}
			addEventLocked(
					unavailable ? "unavailable" : "available",
					"Node " + safeNode + (unavailable
							? " is marked unavailable for future work claims."
							: " is available for pending work again."));
		}
	}

	static int drainNodeTo(
			String runId,
			int sourceNodeIndex,
			int targetNodeIndex,
			int pendingCount)
	{
		int sourceNode;
		int targetNode;
		int redirectCount;
		synchronized (LOCK)
		{
			if (!matchesActiveRunLocked(runId))
			{
				return 0;
			}
			sourceNode = safeNode(sourceNodeIndex);
			targetNode = safeNode(targetNodeIndex);
			if (sourceNode == targetNode)
			{
				addEventLocked(
						"drain blocked",
						"Node " + sourceNode
								+ " cannot drain work to itself.");
				return 0;
			}
			NodeControl targetControl = controlLocked(targetNode);
			if (targetControl.isUnavailable() || targetControl.isPaused())
			{
				addEventLocked(
						"drain blocked",
						"Node " + targetNode
								+ " cannot receive drained work while "
								+ targetControl.statusLabel() + ".");
				return 0;
			}
			CONTROLS.put(
					Integer.valueOf(sourceNode),
					controlLocked(sourceNode).withDrainTargetNode(targetNode));
			redirectTargetNode = targetNode;
			redirectCount = Math.max(1, Math.min(1000000, pendingCount));
			redirectRemaining = redirectCount;
			addEventLocked(
					"drain started",
					"Node " + sourceNode
							+ " is draining to Node " + targetNode
							+ "; current safe-boundary work will be requeued.");
		}

		int moved = RecommendationWorkQueueRegistry.requeueActiveWorkForNode(
				runId,
				sourceNode,
				targetNode);
		synchronized (LOCK)
		{
			if (matchesActiveRunLocked(runId))
			{
				addEventLocked(
						"drain",
						"Node " + sourceNode + " drained "
								+ moved + " active safe-boundary work item"
								+ (moved == 1 ? "" : "s")
								+ " to the waiting queue; next "
								+ redirectCount + " pending work item"
								+ (redirectCount == 1 ? "" : "s")
								+ " prefer Node " + targetNode + ".");
			}
		}
		return moved;
	}

	static void setCapacityScale(String runId, int nodeIndex, double capacityScale)
	{
		synchronized (LOCK)
		{
			if (!matchesActiveRunLocked(runId))
			{
				return;
			}
			int safeNode = safeNode(nodeIndex);
			double safeScale = clamp(capacityScale, 0.05, 4.0);
			CONTROLS.put(
					Integer.valueOf(safeNode),
					controlLocked(safeNode).withCapacityScale(safeScale));
			addEventLocked(
					"capacity",
					"Node " + safeNode + " future-claim capacity set to "
							+ Math.round(safeScale * 100.0) + "%.");
		}
	}

	static void setPolicyOverride(
			String runId,
			SchedulerLabEngine.SchedulingPolicy policy)
	{
		synchronized (LOCK)
		{
			if (!matchesActiveRunLocked(runId))
			{
				return;
			}
			policyOverride = policy;
			addEventLocked(
					"policy",
					policy == null
							? "Remaining pending work uses the scheduler plan policy again."
							: "Remaining pending work now uses "
									+ policy.getLabel() + ".");
		}
	}

	static void redirectPendingToNode(
			String runId,
			int nodeIndex,
			int shardCount)
	{
		synchronized (LOCK)
		{
			if (!matchesActiveRunLocked(runId))
			{
				return;
			}
			int safeNode = safeNode(nodeIndex);
			NodeControl control = controlLocked(safeNode);
			if (control.isUnavailable() || control.isPaused())
			{
				addEventLocked(
						"redirect blocked",
						"Node " + safeNode
								+ " cannot receive redirected pending work while "
								+ control.statusLabel() + ".");
				return;
			}
			redirectTargetNode = safeNode;
			redirectRemaining = Math.max(1, Math.min(1000000, shardCount));
			addEventLocked(
					"redirect",
					"Next " + redirectRemaining + " pending work item"
							+ (redirectRemaining == 1 ? "" : "s")
							+ " prefer Node " + redirectTargetNode
							+ " at claim time.");
		}
	}

	static void clearRedirect(String runId)
	{
		synchronized (LOCK)
		{
			if (!matchesActiveRunLocked(runId))
			{
				return;
			}
			redirectTargetNode = 0;
			redirectRemaining = 0;
			addEventLocked("redirect", "Pending-shard redirect cleared.");
		}
	}

	static void resetRunControls(String runId)
	{
		synchronized (LOCK)
		{
			if (!matchesActiveRunLocked(runId))
			{
				return;
			}
			CONTROLS.clear();
			policyOverride = null;
			redirectTargetNode = 0;
			redirectRemaining = 0;
			pacingDelayMs = 0;
			addEventLocked(
					"reset",
					"Live workload overrides cleared for the active run.");
		}
	}

	static boolean noteClaimedByNode(String runId, int nodeIndex)
	{
		synchronized (LOCK)
		{
			if (!matchesActiveRunLocked(runId))
			{
				return false;
			}
			int safeNode = safeNode(nodeIndex);
			if (redirectRemaining > 0 && redirectTargetNode == safeNode)
			{
				redirectRemaining--;
				if (redirectRemaining <= 0)
				{
					redirectRemaining = 0;
					redirectTargetNode = 0;
					addEventLocked(
							"redirect complete",
							"Requested pending-shard redirect has been consumed.");
				}
				return true;
			}
			return false;
		}
	}

	static double adjustedWeight(
			Snapshot snapshot,
			int nodeIndex,
			double baseWeight)
	{
		if (snapshot == null)
		{
			return Math.max(0.0, baseWeight);
		}
		NodeControl control = snapshot.controlForNode(nodeIndex);
		if (control.isUnavailable() || control.isPaused())
		{
			return 0.0;
		}
		return Math.max(0.0, baseWeight)
				* clamp(control.getCapacityScale(), 0.05, 4.0);
	}

	private static Snapshot snapshotLocked(String runId)
	{
		int safeNodes = Math.max(1, activeNodeCount);
		LinkedHashMap<Integer,NodeControl> copy =
				new LinkedHashMap<Integer,NodeControl>();
		for (int node = 1; node <= safeNodes; node++)
		{
			copy.put(Integer.valueOf(node), controlLocked(node));
		}
		ArrayList<ControlEvent> events = new ArrayList<ControlEvent>(EVENTS);
		Collections.reverse(events);
		return new Snapshot(
				matchesActiveRunLocked(runId) ? activeRunId : normalizeRunId(runId),
				safeNodes,
				copy,
				policyOverride,
				redirectTargetNode,
				redirectRemaining,
				pacingDelayMs,
				events);
	}

	private static NodeControl controlLocked(int nodeIndex)
	{
		int safeNode = safeNode(nodeIndex);
		NodeControl control = CONTROLS.get(Integer.valueOf(safeNode));
		return control == null ? NodeControl.defaults(safeNode) : control;
	}

	private static boolean matchesActiveRunLocked(String runId)
	{
		String safeRun = normalizeRunId(runId);
		return safeRun.length() == 0
				|| activeRunId.length() == 0
				|| activeRunId.equals(safeRun);
	}

	private static int safeNode(int nodeIndex)
	{
		return Math.max(1, Math.min(nodeIndex, Math.max(1, activeNodeCount)));
	}

	private static void addEventLocked(String action, String detail)
	{
		EVENTS.add(new ControlEvent(
				System.currentTimeMillis(),
				action,
				detail));
		while (EVENTS.size() > MAX_EVENTS)
		{
			EVENTS.remove(0);
		}
	}

	private static String normalizeRunId(String runId)
	{
		return runId == null ? "" : runId.trim();
	}

	private static void appendSeparator(StringBuilder builder)
	{
		if (builder.length() > 0)
		{
			builder.append(" | ");
		}
	}

	private static double clamp(
			double value,
			double minimum,
			double maximum)
	{
		if (Double.isNaN(value) || Double.isInfinite(value))
		{
			return minimum;
		}
		return Math.max(minimum, Math.min(maximum, value));
	}

	private static int clampDelay(int value)
	{
		return Math.max(0, Math.min(MAX_PACING_DELAY_MS, value));
	}

	private static String formatDelay(int delayMs)
	{
		int safeDelay = clampDelay(delayMs);
		if (safeDelay >= 1000 && safeDelay % 1000 == 0)
		{
			return (safeDelay / 1000) + " s";
		}
		if (safeDelay >= 1000)
		{
			return String.format(java.util.Locale.US, "%.1f s",
					safeDelay / 1000.0);
		}
		return safeDelay + " ms";
	}
}
