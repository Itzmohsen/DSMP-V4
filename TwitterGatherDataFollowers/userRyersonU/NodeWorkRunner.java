package TwitterGatherDataFollowers.userRyersonU;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Runs one node's lanes over a live {@link WorkPhase}.
 *
 * Each lane repeatedly claims a unit, pays the emulated transfer latency (and
 * the interactive action window, if armed), executes the unit while its work is
 * measured, dilates that work by the node's speed, and commits. A result is
 * handed to the sink only after the dispatcher accepted the commit, and the
 * commit cannot be revoked once accepted, so every unit's result is published
 * by exactly one node, however units are retried, drained or redirected.
 *
 * The calling thread is lane 0; extra lanes are daemon threads. The executor
 * must be safe to call from several lanes at once, and the sink is called under
 * this runner's lock.
 */
final class NodeWorkRunner
{
	interface UnitExecutor
	{
		UnitOutput execute(WorkDispatcher.Assignment assignment, int lane) throws Exception;
	}

	interface CommitSink
	{
		void commit(WorkDispatcher.Assignment assignment, Object value);
	}

	/** A unit's result and, for work done in another process, its reported CPU time. */
	static final class UnitOutput
	{
		private final Object value;
		private final long externalWorkNanos;

		private UnitOutput(Object value, long externalWorkNanos)
		{
			this.value = value;
			this.externalWorkNanos = externalWorkNanos;
		}

		/** Work measured as this lane's thread CPU time. */
		static UnitOutput measured(Object value)
		{
			return new UnitOutput(value, -1L);
		}

		/**
		 * Work partly done in a worker process: {@code workNanos} is that
		 * process's reported CPU time, added to this lane's measured work.
		 */
		static UnitOutput external(Object value, long workNanos)
		{
			return new UnitOutput(value, Math.max(0L, workNanos));
		}
	}

	static final class Stats
	{
		private final AtomicInteger committed = new AtomicInteger();
		private final AtomicInteger injected = new AtomicInteger();
		private final AtomicInteger stale = new AtomicInteger();
		private final AtomicInteger retried = new AtomicInteger();

		int getCommitted()
		{
			return committed.get();
		}

		int getInjectedFailures()
		{
			return injected.get();
		}

		int getStale()
		{
			return stale.get();
		}

		int getRetried()
		{
			return retried.get();
		}

		public String toString()
		{
			return committed.get() + " committed, " + injected.get() + " lost to injected failures, "
					+ retried.get() + " retried after errors, " + stale.get() + " revoked";
		}
	}

	private NodeWorkRunner()
	{
	}

	static Stats run(
			final SchedulingRunContext context,
			final WorkPhase phase,
			final int node,
			final UnitExecutor executor,
			final CommitSink sink)
	{
		final Stats stats = new Stats();
		final AtomicReference<Throwable> fatal = new AtomicReference<Throwable>();
		final Object sinkLock = new Object();
		int lanes = Math.max(1, context.lanesOf(node));
		ArrayList<Thread> helpers = new ArrayList<Thread>();
		for (int lane = 1; lane < lanes; lane++)
		{
			final int laneIndex = lane;
			Thread thread = new Thread(new Runnable()
			{
				public void run()
				{
					runLane(context, phase, node, laneIndex, executor, sink, sinkLock, stats, fatal);
				}
			}, "Node" + node + "-lane" + (lane + 1));
			thread.setDaemon(true);
			helpers.add(thread);
			thread.start();
		}
		runLane(context, phase, node, 0, executor, sink, sinkLock, stats, fatal);
		for (Thread helper : helpers)
		{
			boolean joined = false;
			while (!joined)
			{
				try
				{
					helper.join();
					joined = true;
				}
				catch (InterruptedException interrupted)
				{
					phase.close("node " + node + " was interrupted");
					Thread.currentThread().interrupt();
				}
			}
		}
		Throwable failure = fatal.get();
		if (failure != null)
		{
			if (failure instanceof RuntimeException)
			{
				throw (RuntimeException)failure;
			}
			throw new IllegalStateException(failure.getMessage(), failure);
		}
		return stats;
	}

	private static void runLane(
			SchedulingRunContext context,
			WorkPhase phase,
			int node,
			int lane,
			UnitExecutor executor,
			CommitSink sink,
			Object sinkLock,
			Stats stats,
			AtomicReference<Throwable> fatal)
	{
		NodeEmulator emulator = context.emulator(node);
		try
		{
			while (fatal.get() == null)
			{
				WorkDispatcher.Assignment assignment = phase.claim(node, lane);
				if (assignment == null)
				{
					return;
				}
				emulator.transfer(assignment.getTransferDelayMs());
				emulator.pacing(context.getPacingMs());
				if (!phase.isLive(assignment))
				{
					//Revoked while in transit: report it so its lane slot frees up.
					phase.complete(assignment);
					stats.stale.incrementAndGet();
					continue;
				}
				NodeEmulator.Span span = emulator.begin();
				UnitOutput output;
				try
				{
					output = executor.execute(assignment, lane);
				}
				catch (Throwable error)
				{
					emulator.end(span);
					if (error instanceof InterruptedException)
					{
						Thread.currentThread().interrupt();
					}
					WorkDispatcher.Outcome outcome = phase.fail(assignment, error);
					if (outcome == WorkDispatcher.Outcome.FINAL_FAILURE)
					{
						fatal.compareAndSet(null, new IllegalStateException(
								phase.getDispatcher().getFailureReason(), error));
						return;
					}
					stats.retried.incrementAndGet();
					continue;
				}
				if (output == null)
				{
					output = UnitOutput.measured(null);
				}
				emulator.end(span);
				if (output.externalWorkNanos > 0L)
				{
					//Work a worker process did for this unit, on top of this lane's own.
					emulator.dilate(output.externalWorkNanos);
				}
				WorkDispatcher.Outcome outcome = phase.complete(assignment);
				switch (outcome)
				{
					case COMMITTED:
						synchronized (sinkLock)
						{
							sink.commit(assignment, output.value);
						}
						stats.committed.incrementAndGet();
						break;
					case INJECTED_FAILURE:
						stats.injected.incrementAndGet();
						break;
					case STALE:
						stats.stale.incrementAndGet();
						break;
					case FINAL_FAILURE:
						fatal.compareAndSet(null, new IllegalStateException(
								phase.getDispatcher().getFailureReason()));
						return;
					default:
						break;
				}
			}
		}
		catch (Throwable error)
		{
			if (error instanceof InterruptedException)
			{
				Thread.currentThread().interrupt();
			}
			fatal.compareAndSet(null, error);
			phase.withdraw(node, lane);
		}
	}
}
