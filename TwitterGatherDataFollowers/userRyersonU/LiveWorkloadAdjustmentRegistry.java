package TwitterGatherDataFollowers.userRyersonU;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Operator controls for a running simulation, as seen by the panels.
 *
 * Every control is applied through the run's {@link SchedulingRunContext}, so
 * it takes effect at once on every open work phase and on input routing:
 *
 * <ul>
 * <li>pause: the node stops claiming queue work and holds at its next safe
 *     boundary; its input is held;</li>
 * <li>unavailable: the node takes no new queue work or input users, its queued
 *     units move to other nodes, and its own data phases keep running;</li>
 * <li>drain: unavailable, plus its running queue units are revoked and
 *     restarted elsewhere (their late results are discarded), and its future
 *     input users go to the other nodes;</li>
 * <li>redirect: up to N not-started units move to one node and it receives the
 *     next N new input users; nobody else waits;</li>
 * <li>capacity: the node's speed changes for future work and dilation;</li>
 * <li>policy: every not-started unit follows another policy;</li>
 * <li>action window: each claimed unit waits before running so controls can be
 *     tried interactively; the run's timings are then marked interactive.</li>
 * </ul>
 *
 * Refused controls (for example disabling the last available node) are
 * recorded in the run's event log with the reason.
 */
final class LiveWorkloadAdjustmentRegistry
{
	static final class NodeControl
	{
		private final int nodeIndex;
		private final boolean paused;
		private final boolean unavailable;
		private final boolean draining;
		private final double capacityScale;
		private final int drainTargetNode;

		private NodeControl(int nodeIndex, boolean paused, boolean unavailable,
				boolean draining, double capacityScale, int drainTargetNode)
		{
			this.nodeIndex = Math.max(1, nodeIndex);
			this.paused = paused;
			this.unavailable = unavailable;
			this.draining = draining;
			this.capacityScale = capacityScale;
			this.drainTargetNode = Math.max(0, drainTargetNode);
		}

		static NodeControl defaults(int nodeIndex)
		{
			return new NodeControl(nodeIndex, false, false, false, 1.0, 0);
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
			return draining;
		}

		boolean isDefault()
		{
			return !paused && !unavailable && !draining
					&& Math.abs(capacityScale - 1.0) < 1e-9;
		}

		String statusLabel()
		{
			if (draining)
			{
				return drainTargetNode > 0
						? "drained (input prefers node " + drainTargetNode + ")" : "drained";
			}
			if (unavailable)
			{
				return "unavailable";
			}
			if (paused)
			{
				return "paused";
			}
			if (Math.abs(capacityScale - 1.0) > 1e-9)
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
			this.time = time;
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
		private final boolean activeControls;
		private final String modeLabel;

		private Snapshot(String runId, int nodeCount, Map<Integer,NodeControl> controls,
				SchedulerLabEngine.SchedulingPolicy policyOverride, int redirectTargetNode,
				int redirectRemaining, int pacingDelayMs, List<ControlEvent> events,
				boolean activeControls, String modeLabel)
		{
			this.runId = runId == null ? "" : runId;
			this.nodeCount = Math.max(1, nodeCount);
			this.controls = Collections.unmodifiableMap(
					new LinkedHashMap<Integer,NodeControl>(controls));
			this.policyOverride = policyOverride;
			this.redirectTargetNode = redirectTargetNode;
			this.redirectRemaining = redirectRemaining;
			this.pacingDelayMs = pacingDelayMs;
			this.events = Collections.unmodifiableList(new ArrayList<ControlEvent>(events));
			this.activeControls = activeControls;
			this.modeLabel = modeLabel == null ? "" : modeLabel;
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

		String getModeLabel()
		{
			return modeLabel;
		}

		NodeControl controlForNode(int nodeIndex)
		{
			NodeControl control = controls.get(Integer.valueOf(nodeIndex));
			return control == null ? NodeControl.defaults(nodeIndex) : control;
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
			return activeControls;
		}

		String compactSummary()
		{
			if (!activeControls)
			{
				return "No live workload overrides.";
			}
			StringBuilder builder = new StringBuilder();
			if (policyOverride != null)
			{
				builder.append("policy ").append(policyOverride.getLabel());
			}
			int paused = 0;
			int unavailable = 0;
			int draining = 0;
			int throttled = 0;
			for (NodeControl control : controls.values())
			{
				if (control.isDraining())
				{
					draining++;
				}
				else if (control.isUnavailable())
				{
					unavailable++;
				}
				else if (control.isPaused())
				{
					paused++;
				}
				if (Math.abs(control.getCapacityScale() - 1.0) > 1e-9)
				{
					throttled++;
				}
			}
			append(builder, paused, "paused");
			append(builder, unavailable, "unavailable");
			append(builder, draining, "drained");
			append(builder, throttled, "capacity override");
			if (redirectRemaining > 0)
			{
				separate(builder);
				builder.append("next ").append(redirectRemaining)
						.append(" input user(s) to node ").append(redirectTargetNode);
			}
			if (pacingDelayMs > 0)
			{
				separate(builder);
				builder.append("action window ").append(pacingDelayMs).append(" ms");
			}
			return builder.toString();
		}

		private static void append(StringBuilder builder, int count, String label)
		{
			if (count > 0)
			{
				separate(builder);
				builder.append(count).append(' ').append(label);
			}
		}

		private static void separate(StringBuilder builder)
		{
			if (builder.length() > 0)
			{
				builder.append(" | ");
			}
		}
	}

	private static final Object LOCK = new Object();
	private static int nextRunPacingDelayMs;

	private LiveWorkloadAdjustmentRegistry()
	{
	}

	/** Snapshot of the run's controls; an empty id means the latest run. */
	static Snapshot snapshot(String runId)
	{
		SchedulingRunContext context = resolve(runId);
		if (context == null)
		{
			int nodes = NodeCapacityRegistry.configuredNodeCount();
			LinkedHashMap<Integer,NodeControl> controls = new LinkedHashMap<Integer,NodeControl>();
			for (int node = 1; node <= nodes; node++)
			{
				controls.put(Integer.valueOf(node), NodeControl.defaults(node));
			}
			return new Snapshot(runId, nodes, controls, null, 0, 0,
					nextRunPacingDelayMs(), Collections.<ControlEvent>emptyList(), false, "");
		}
		LinkedHashMap<Integer,NodeControl> controls = new LinkedHashMap<Integer,NodeControl>();
		for (int node = 1; node <= context.getNodeCount(); node++)
		{
			WorkDispatcher.NodeState state = context.nodeState(node);
			boolean draining = context.isDraining(node);
			controls.put(Integer.valueOf(node), new NodeControl(
					node,
					state == WorkDispatcher.NodeState.PAUSED,
					state == WorkDispatcher.NodeState.UNAVAILABLE,
					draining,
					context.capacityScale(node),
					draining ? context.getDrainPreferredTarget() : 0));
		}
		ArrayList<ControlEvent> events = new ArrayList<ControlEvent>();
		for (SchedulingRunContext.ControlEvent event : context.events())
		{
			events.add(new ControlEvent(event.getTime(), event.getAction(), event.getDetail()));
		}
		return new Snapshot(
				context.getRunId(),
				context.getNodeCount(),
				controls,
				context.getPolicyOverride(),
				context.getRedirectTarget(),
				context.getRedirectRemainingUsers(),
				context.getPacingMs(),
				events,
				context.hasActiveControls(),
				context.getMode().getLabel());
	}

	static Snapshot snapshot()
	{
		return snapshot("");
	}

	static String activeRunId()
	{
		SchedulingRunContext context = SchedulingRuntime.active();
		return context == null ? "" : context.getRunId();
	}

	static int nextRunPacingDelayMs()
	{
		synchronized (LOCK)
		{
			return nextRunPacingDelayMs;
		}
	}

	/** Arms an action window for the next initialized run only. */
	static void setNextRunPacingDelayMs(int delayMs)
	{
		synchronized (LOCK)
		{
			nextRunPacingDelayMs = Math.max(0, Math.min(15000, delayMs));
		}
	}

	/** Reads and disarms the next-run action window (called at run creation). */
	static int consumeNextRunPacingDelayMs()
	{
		synchronized (LOCK)
		{
			int value = nextRunPacingDelayMs;
			nextRunPacingDelayMs = 0;
			return value;
		}
	}

	static void setPacingDelayMs(String runId, int delayMs)
	{
		SchedulingRunContext context = resolve(runId);
		if (context != null)
		{
			context.setPacingMs(delayMs);
		}
	}

	/** Called by a lane after it claims a unit; sleeps the action window, if any. */
	static void applyClaimPacingDelay(String runId, int node)
	{
		SchedulingRunContext context = resolve(runId);
		if (context != null && node >= 1 && node <= context.getNodeCount())
		{
			context.emulator(node).pacing(context.getPacingMs());
		}
	}

	/** Holds a node at a safe phase boundary while it is paused. */
	static void waitUntilNodeEligible(String runId, int nodeIndex, String phaseLabel)
	{
		SchedulingRunContext context = resolve(runId);
		if (context != null && nodeIndex >= 1 && nodeIndex <= context.getNodeCount())
		{
			context.awaitNotPaused(nodeIndex, phaseLabel);
		}
	}

	static boolean setPaused(String runId, int nodeIndex, boolean paused)
	{
		SchedulingRunContext context = resolve(runId);
		if (context == null)
		{
			return false;
		}
		try
		{
			context.setPaused(nodeIndex, paused);
			return true;
		}
		catch (RuntimeException refused)
		{
			context.recordRejected(paused ? "pause" : "resume", refused.getMessage());
			return false;
		}
	}

	static boolean setUnavailable(String runId, int nodeIndex, boolean unavailable)
	{
		SchedulingRunContext context = resolve(runId);
		if (context == null)
		{
			return false;
		}
		try
		{
			context.setUnavailable(nodeIndex, unavailable);
			return true;
		}
		catch (RuntimeException refused)
		{
			context.recordRejected(unavailable ? "unavailable" : "available", refused.getMessage());
			return false;
		}
	}

	/**
	 * Drains {@code sourceNodeIndex}; its future input prefers
	 * {@code targetNodeIndex}. Returns the number of revoked running units, or
	 * -1 when the drain was refused.
	 */
	static int drainNodeTo(String runId, int sourceNodeIndex, int targetNodeIndex, int ignored)
	{
		SchedulingRunContext context = resolve(runId);
		if (context == null)
		{
			return -1;
		}
		try
		{
			return context.drain(sourceNodeIndex, targetNodeIndex);
		}
		catch (RuntimeException refused)
		{
			context.recordRejected("drain", refused.getMessage());
			return -1;
		}
	}

	static boolean setCapacityScale(String runId, int nodeIndex, double capacityScale)
	{
		SchedulingRunContext context = resolve(runId);
		if (context == null)
		{
			return false;
		}
		try
		{
			context.setCapacityScale(nodeIndex, capacityScale);
			return true;
		}
		catch (RuntimeException refused)
		{
			context.recordRejected("capacity", refused.getMessage());
			return false;
		}
	}

	static boolean setPolicyOverride(String runId, SchedulerLabEngine.SchedulingPolicy policy)
	{
		SchedulingRunContext context = resolve(runId);
		if (context == null)
		{
			return false;
		}
		try
		{
			context.setPolicyOverride(policy);
			return true;
		}
		catch (RuntimeException refused)
		{
			context.recordRejected("policy", refused.getMessage());
			return false;
		}
	}

	/** Returns the number of queued units moved, or -1 when refused. */
	static int redirectPendingToNode(String runId, int nodeIndex, int count)
	{
		SchedulingRunContext context = resolve(runId);
		if (context == null)
		{
			return -1;
		}
		try
		{
			return context.redirect(nodeIndex, count);
		}
		catch (RuntimeException refused)
		{
			context.recordRejected("redirect", refused.getMessage());
			return -1;
		}
	}

	static void clearRedirect(String runId)
	{
		SchedulingRunContext context = resolve(runId);
		if (context != null)
		{
			context.clearRedirect();
		}
	}

	static void resetRunControls(String runId)
	{
		SchedulingRunContext context = resolve(runId);
		if (context != null)
		{
			context.resetControls();
		}
	}

	private static SchedulingRunContext resolve(String runId)
	{
		if (runId == null || runId.trim().length() == 0)
		{
			return SchedulingRuntime.latest();
		}
		SchedulingRunContext context = SchedulingRuntime.get(runId.trim());
		return context != null ? context : null;
	}
}
