package TwitterGatherDataFollowers.userRyersonU;

import java.util.List;

/**
 * Blocking, thread-safe facade over a {@link WorkDispatcher} for real threads
 * (live recommender nodes and the Dynamic Queue demo).
 *
 * Every dispatcher call and every wait happen while holding this phase's
 * monitor, and every state change ends with {@code notifyAll()}, so a lane can
 * never miss the assignment made for it. Waits wake up at least every 250 ms to
 * honour run aborts, and a phase that makes no progress for
 * {@code stallTimeoutMs} while no operator action or crash repair is holding it
 * fails instead of hanging.
 *
 * The dispatcher's clock is the run's model clock when the phase belongs to a
 * run context, else milliseconds since the phase opened.
 */
final class WorkPhase
{
	private final WorkDispatcher dispatcher;
	private final String runId;
	private final SchedulingRunContext context;
	private final long startNanos;
	private final long stallTimeoutMs;
	private long lastProgressNanos;
	private volatile boolean closed;
	private String closeReason = "";
	private boolean finishReported;

	WorkPhase(WorkDispatcher dispatcher, String runId, long stallTimeoutMs)
	{
		this(dispatcher, runId, stallTimeoutMs, null);
	}

	WorkPhase(WorkDispatcher dispatcher, String runId, long stallTimeoutMs,
			SchedulingRunContext context)
	{
		this.dispatcher = dispatcher;
		this.runId = runId;
		this.context = context;
		this.startNanos = System.nanoTime();
		this.lastProgressNanos = startNanos;
		this.stallTimeoutMs = stallTimeoutMs <= 0L ? Long.MAX_VALUE : stallTimeoutMs;
	}

	WorkDispatcher getDispatcher()
	{
		return dispatcher;
	}

	/** The dispatcher's clock: model milliseconds of the run, or since the phase opened. */
	double now()
	{
		if (context != null)
		{
			return context.now();
		}
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
				if (idleMs > stallTimeoutMs && !dispatcher.isBlockedByOperator()
						&& !dispatcher.isWaitingForRepair())
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
		WorkDispatcher.Outcome outcome;
		synchronized (this)
		{
			outcome = dispatcher.complete(assignment, now());
			noteProgress();
			notifyAll();
		}
		afterChange();
		return outcome;
	}

	/**
	 * Reports a failed execution. Unit work is deterministic, so only a failure
	 * of the environment (an I/O error such as a crashed worker process, a
	 * timeout, the JVM running out of memory) can succeed when retried; any
	 * other error is the work itself failing and ends the phase at once.
	 */
	WorkDispatcher.Outcome fail(WorkDispatcher.Assignment assignment, Throwable cause)
	{
		WorkDispatcher.Outcome outcome;
		synchronized (this)
		{
			String reason = cause == null ? "failure"
					: cause.getClass().getSimpleName()
							+ (cause.getMessage() == null ? "" : ": " + cause.getMessage());
			outcome = dispatcher.fail(assignment, now(), reason, isTransient(cause));
			noteProgress();
			notifyAll();
		}
		afterChange();
		return outcome;
	}

	static boolean isTransient(Throwable cause)
	{
		if (cause == null)
		{
			return true;
		}
		for (Throwable link = cause; link != null; link = link.getCause() == link ? null : link.getCause())
		{
			if (link instanceof java.io.IOException
					|| link instanceof java.io.UncheckedIOException
					|| link instanceof java.util.concurrent.TimeoutException
					|| link instanceof VirtualMachineError)
			{
				return true;
			}
		}
		return false;
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

	List<Integer> crash(int node)
	{
		synchronized (this)
		{
			List<Integer> lost = dispatcher.crash(node, now());
			noteProgress();
			notifyAll();
			return lost;
		}
	}

	void detectCrash(int node)
	{
		synchronized (this)
		{
			dispatcher.detectCrash(node, now());
			noteProgress();
			notifyAll();
		}
	}

	void recover(int node)
	{
		synchronized (this)
		{
			dispatcher.recover(node, now());
			noteProgress();
			notifyAll();
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

	private void afterChange()
	{
		if (context == null)
		{
			return;
		}
		boolean report = false;
		boolean failed = false;
		synchronized (this)
		{
			if (!finishReported && (dispatcher.isComplete() || dispatcher.hasFailed()))
			{
				finishReported = true;
				report = true;
				failed = dispatcher.hasFailed();
			}
		}
		if (report)
		{
			context.phaseFinished(this, failed, dispatcher.getFailureReason());
		}
		context.progressMade();
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
