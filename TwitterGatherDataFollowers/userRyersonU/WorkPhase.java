package TwitterGatherDataFollowers.userRyersonU;

import java.util.List;

/**
 * Blocking, thread-safe facade over a {@link WorkDispatcher} for real threads
 * (live recommender nodes and the Dynamic Queue Lab).
 *
 * Every dispatcher call and every wait happen while holding this phase's
 * monitor, and every state change ends with {@code notifyAll()}, so a lane can
 * never miss the assignment made for it. Waits wake up at least every 250 ms to
 * honour run aborts, and a phase that makes no progress for
 * {@code stallTimeoutMs} while no operator action is holding it fails instead
 * of hanging.
 */
final class WorkPhase
{
	private final WorkDispatcher dispatcher;
	private final String runId;
	private final long startNanos;
	private final long stallTimeoutMs;
	private long lastProgressNanos;
	private volatile boolean closed;
	private String closeReason = "";

	WorkPhase(WorkDispatcher dispatcher, String runId, long stallTimeoutMs)
	{
		this.dispatcher = dispatcher;
		this.runId = runId;
		this.startNanos = System.nanoTime();
		this.lastProgressNanos = startNanos;
		this.stallTimeoutMs = stallTimeoutMs <= 0L ? Long.MAX_VALUE : stallTimeoutMs;
	}

	WorkDispatcher getDispatcher()
	{
		return dispatcher;
	}

	/** Milliseconds since the phase opened: the dispatcher's clock. */
	double now()
	{
		return (System.nanoTime() - startNanos) / 1.0e6;
	}

	/**
	 * Blocks until this lane has work, the phase is complete (returns null), or
	 * the phase fails, is closed, or its run is aborted (throws).
	 */
	WorkDispatcher.Assignment claim(int node, int lane) throws InterruptedException
	{
		synchronized (this)
		{
			while (true)
			{
				requireOpen();
				WorkDispatcher.Assignment assignment = dispatcher.request(node, lane, now());
				if (assignment != null)
				{
					noteProgress();
					notifyAll();
					return assignment;
				}
				if (dispatcher.isComplete())
				{
					dispatcher.withdraw(node, lane);
					return null;
				}
				if (dispatcher.hasFailed())
				{
					dispatcher.withdraw(node, lane);
					throw new IllegalStateException(
							"Work phase " + dispatcher.getPhaseKey() + " failed: "
							+ dispatcher.getFailureReason());
				}
				long idleMs = (System.nanoTime() - lastProgressNanos) / 1000000L;
				if (idleMs > stallTimeoutMs && !dispatcher.isBlockedByOperator())
				{
					dispatcher.withdraw(node, lane);
					throw new IllegalStateException(
							"Work phase " + dispatcher.getPhaseKey()
							+ " made no progress for " + idleMs + " ms ("
							+ dispatcher.getCommittedCount() + " of "
							+ dispatcher.getUnitCount() + " units committed).");
				}
				wait(250L);
			}
		}
	}

	WorkDispatcher.Outcome complete(WorkDispatcher.Assignment assignment)
	{
		synchronized (this)
		{
			WorkDispatcher.Outcome outcome = dispatcher.complete(assignment, now());
			noteProgress();
			notifyAll();
			return outcome;
		}
	}

	WorkDispatcher.Outcome fail(WorkDispatcher.Assignment assignment, Throwable cause)
	{
		synchronized (this)
		{
			String reason = cause == null ? "failure"
					: cause.getClass().getSimpleName()
							+ (cause.getMessage() == null ? "" : ": " + cause.getMessage());
			WorkDispatcher.Outcome outcome = dispatcher.fail(assignment, now(), reason);
			noteProgress();
			notifyAll();
			return outcome;
		}
	}

	/** A lane that stops for good must withdraw so nothing waits for it. */
	void withdraw(int node, int lane)
	{
		synchronized (this)
		{
			dispatcher.withdraw(node, lane);
			notifyAll();
		}
	}

	boolean isLive(WorkDispatcher.Assignment assignment)
	{
		synchronized (this)
		{
			return dispatcher.isLive(assignment);
		}
	}

	void setNodeState(int node, WorkDispatcher.NodeState state)
	{
		synchronized (this)
		{
			dispatcher.setNodeState(node, state, now());
			noteProgress();
			notifyAll();
		}
	}

	List<Integer> drain(int node)
	{
		synchronized (this)
		{
			List<Integer> revoked = dispatcher.drain(node, now());
			noteProgress();
			notifyAll();
			return revoked;
		}
	}

	int redirect(int node, int count)
	{
		synchronized (this)
		{
			int moved = dispatcher.redirect(node, count, now());
			noteProgress();
			notifyAll();
			return moved;
		}
	}

	void updateSpecs(List<NodeSpec> specs)
	{
		synchronized (this)
		{
			dispatcher.updateSpecs(specs, now());
			notifyAll();
		}
	}

	void switchPolicy(SchedulerLabEngine.SchedulingPolicy policy)
	{
		synchronized (this)
		{
			dispatcher.switchPolicy(policy, now());
			noteProgress();
			notifyAll();
		}
	}

	/** Wakes every waiting lane and makes further claims throw. */
	void close(String reason)
	{
		synchronized (this)
		{
			closed = true;
			closeReason = reason == null ? "closed" : reason;
			notifyAll();
		}
	}

	boolean isClosed()
	{
		return closed;
	}

	private void requireOpen()
	{
		if (closed)
		{
			throw new IllegalStateException(
					"Work phase " + dispatcher.getPhaseKey() + " was closed: " + closeReason);
		}
		if (runId != null && RecommendationRunAbortRegistry.isAborted(runId))
		{
			RecommendationRunAbortRegistry.requireActive(runId);
			throw new IllegalStateException("Run " + runId + " was aborted.");
		}
		if (Thread.currentThread().isInterrupted())
		{
			throw new IllegalStateException(
					"Interrupted while waiting for work in " + dispatcher.getPhaseKey() + ".");
		}
	}

	private void noteProgress()
	{
		lastProgressNanos = System.nanoTime();
	}
}
