package TwitterGatherDataFollowers.userRyersonU;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Runs one node's lanes over a live {@link WorkPhase}.
 *
 * Each lane repeatedly claims a unit, waits until the unit is ready on the
 * node (its descriptor and, away from home, its input data have crossed the
 * emulated network), executes it while its work is measured, pays the node's
 * speed emulation, and commits. A result is handed to the sink only after the
 * dispatcher accepted the commit, and the commit cannot be revoked once
 * accepted, so every unit's result is published by exactly one node, however
 * units are retried, drained, redirected or lost in crashes.
 *
 * While a lane executes a unit the unit is registered with
 * {@link UnitCancellation}; a revoked execution stops at its next check.
 * Every execution is recorded in the run's {@link RunRecorder} with its model
 * times and measured host CPU.
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
			return committed.get() + " committed, " + injected.get() + " lost to injected faults, "
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

	private static String phaseKeyOf(WorkPhase phase, SchedulingRunContext context)
	{
		String key = phase.getDispatcher().getPhaseKey();
		String prefix = context.getRunId() + "/";
		return key.startsWith(prefix) ? key.substring(prefix.length()) : key;
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
		String phaseKey = phaseKeyOf(phase, context);
		//The node joins the phase when its first lane asks for work.
		context.recorder().phaseJoined(phaseKey, node, context.now());
		try
		{
			while (fatal.get() == null)
			{
				WorkDispatcher.Assignment assignment = phase.claim(node, lane);
				if (assignment == null)
				{
					return;
				}
				double startAt;
				UnitCancellation.enter(assignment);
				try
				{
					//The unit's descriptor and data cross the emulated network.
					startAt = emulator.awaitModelTime(assignment.getReadyAt(), true);
					emulator.pacing(context.getPacingMs());
				}
				catch (UnitCancellation.Revoked revoked)
				{
					startAt = context.now();
				}
				finally
				{
					UnitCancellation.exit();
				}
				if (assignment.isRevoked() || !phase.isLive(assignment))
				{
					//Revoked while in transit: report it so its lane slot frees up.
					phase.complete(assignment);
					record(context, phaseKey, assignment, startAt, context.now(), 0L, 0L, 0L, "revoked");
					stats.stale.incrementAndGet();
					continue;
				}
				long wallStart = System.nanoTime();
				NodeEmulator.Span span = emulator.beginUnit();
				UnitOutput output = null;
				Throwable error = null;
				long external = 0L;
				long cpu = 0L;
				//The unit stays registered through its speed emulation, so a drain
				//or crash also cuts that sleep short.
				UnitCancellation.enter(assignment);
				try
				{
					try
					{
						output = executor.execute(assignment, lane);
					}
					catch (Throwable thrown)
					{
						error = thrown;
					}
					external = output == null ? 0L : Math.max(0L, output.externalWorkNanos);
					try
					{
						cpu = emulator.end(span, external, true);
					}
					catch (UnitCancellation.Revoked revoked)
					{
						error = error == null ? revoked : error;
					}
				}
				finally
				{
					UnitCancellation.exit();
				}
				long wall = System.nanoTime() - wallStart;
				double endAt = context.now();
				if (error instanceof UnitCancellation.Revoked || assignment.isRevoked())
				{
					phase.complete(assignment);
					record(context, phaseKey, assignment, startAt, endAt, cpu, external, wall, "revoked");
					stats.stale.incrementAndGet();
					continue;
				}
				if (error != null)
				{
					if (error instanceof InterruptedException)
					{
						Thread.currentThread().interrupt();
					}
					WorkDispatcher.Outcome outcome = phase.fail(assignment, error);
					record(context, phaseKey, assignment, startAt, endAt, cpu, external, wall, "error");
					if (outcome == WorkDispatcher.Outcome.FINAL_FAILURE)
					{
						fatal.compareAndSet(null, new IllegalStateException(
								phase.getDispatcher().getFailureReason(), error));
						return;
					}
					stats.retried.incrementAndGet();
					continue;
				}
				WorkDispatcher.Outcome outcome = phase.complete(assignment);
				switch (outcome)
				{
					case COMMITTED:
						synchronized (sinkLock)
						{
							sink.commit(assignment, output == null ? null : output.value);
						}
						stats.committed.incrementAndGet();
						record(context, phaseKey, assignment, startAt, endAt, cpu, external, wall, "committed");
						break;
					case INJECTED_FAILURE:
						stats.injected.incrementAndGet();
						record(context, phaseKey, assignment, startAt, endAt, cpu, external, wall, "fault");
						break;
					case STALE:
						stats.stale.incrementAndGet();
						record(context, phaseKey, assignment, startAt, endAt, cpu, external, wall, "revoked");
						break;
					case FINAL_FAILURE:
						record(context, phaseKey, assignment, startAt, endAt, cpu, external, wall, "fault");
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

	private static void record(SchedulingRunContext context, String phaseKey,
			WorkDispatcher.Assignment assignment, double startAt, double endAt,
			long cpu, long external, long wall, String outcome)
	{
		WorkUnitSpec unit = assignment.getUnit();
		context.recorder().execution(new RunRecorder.UnitExecution(
				phaseKey,
				unit.getId(),
				unit.getKey(),
				assignment.getExecution(),
				assignment.getNode(),
				assignment.getLane(),
				unit.getHomeNode(),
				assignment.getSource().name().toLowerCase(java.util.Locale.US),
				unit.getCost(),
				assignment.getAssignedAt(),
				assignment.getReadyAt(),
				startAt,
				endAt,
				cpu,
				external,
				wall,
				assignment.getTransferredBytes(),
				outcome));
	}
}
