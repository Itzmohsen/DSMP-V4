package TwitterGatherDataFollowers.userRyersonU;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

/**
 * Makes one node's capacity profile physically true in a live run.
 *
 * <b>Time model.</b> The run has a time dilation factor TDF (see
 * {@link ModelClock}): model time is wall time / TDF. Node work that uses W
 * nanoseconds of host CPU lasts W / s of model time on a node of speed s, so
 * it must last TDF x W / s of wall time; it already took W, so the emulator
 * sleeps W x (TDF / s - 1). Inside a work unit ({@link #beginUnit}), time V the
 * unit waits for something other than the CPU (a worker process starting, a
 * file) is part of the unit and lasts V of model time, so it is stretched by
 * TDF as well: V x (TDF - 1). Outside units, waiting (for other nodes, for a
 * message, for a lease) is not node work and is never stretched. Work reported
 * by a worker process (its CPU time C) counts as node CPU instead of waiting,
 * capped at the time the lane waited for it (a lane is one core).
 *
 * <b>Accuracy.</b> Sleeps are owed as a debt and paid when the debt reaches
 * 1.5 ms, at every synchronization point and at the end of every work unit;
 * each payment parks in shrinking steps and carries the residual (a few
 * hundred microseconds, positive or negative) into the next payment, so
 * sleep-timer granularity never accumulates: the total emulated time of a run
 * is exact to within one residual per thread.
 *
 * <b>Saturation.</b> A node cannot run faster than the host: a node whose
 * speed exceeds the TDF runs at speed TDF, and the run is flagged.
 *
 * <b>Crashes.</b> While the node is down its threads block at the next
 * emulator call. Node-local work in progress when the node crashed is redone
 * after the repair (the node's state is durable, its progress is not); a work
 * unit in progress is revoked and restarts wherever the scheduler sends it.
 *
 * Every emulated nanosecond is recorded in the node's {@link Ledger}. In an
 * unemulated run the emulator only measures.
 */
final class NodeEmulator
{
	private static final ThreadMXBean THREADS = ManagementFactory.getThreadMXBean();
	private static final boolean CPU_TIME_AVAILABLE = cpuTimeAvailable();
	/** Debt below this is carried instead of slept. */
	private static final long PAY_THRESHOLD_NANOS = 1500000L;
	/** A sleep stops parking when less than this remains; the rest is carried. */
	private static final long RESIDUAL_NANOS = 150000L;
	/** Longest single park, so aborts and revocations are noticed quickly. */
	private static final long MAX_PARK_NANOS = 50000000L;

	/** Per-node accounting of where wall time went. */
	static final class Ledger
	{
		private final AtomicLong computeNanos = new AtomicLong();
		private final AtomicLong waitNanos = new AtomicLong();
		private final AtomicLong externalNanos = new AtomicLong();
		private final AtomicLong dilationNanos = new AtomicLong();
		private final AtomicLong owedNanos = new AtomicLong();
		private final AtomicLong networkNanos = new AtomicLong();
		private final AtomicLong pacingNanos = new AtomicLong();
		private final AtomicLong downtimeNanos = new AtomicLong();
		private final AtomicLong redoNanos = new AtomicLong();
		private final AtomicLong sleepErrorNanos = new AtomicLong();
		private final AtomicLong sleeps = new AtomicLong();
		private final AtomicLong messages = new AtomicLong();
		private final AtomicLong saturatedNanos = new AtomicLong();

		/** Host CPU time of node work. */
		long getComputeNanos()
		{
			return computeNanos.get();
		}

		/** Host time node work spent waiting on something other than the emulator. */
		long getWaitNanos()
		{
			return waitNanos.get();
		}

		/** CPU time reported by worker processes for this node. */
		long getExternalNanos()
		{
			return externalNanos.get();
		}

		/** Wall time slept to emulate speed and time dilation. */
		long getDilationNanos()
		{
			return dilationNanos.get();
		}

		/**
		 * Wall time the model says the node owed for speed and time dilation;
		 * {@link #getDilationNanos()} differs from it only by the residual still
		 * carried and the sleep timer's error.
		 */
		long getOwedNanos()
		{
			return owedNanos.get();
		}

		/** Wall time waited for link transfers. */
		long getNetworkNanos()
		{
			return networkNanos.get();
		}

		long getPacingNanos()
		{
			return pacingNanos.get();
		}

		/** Wall time the node's threads were blocked because the node was down. */
		long getDowntimeNanos()
		{
			return downtimeNanos.get();
		}

		/** Wall time spent redoing work lost in crashes. */
		long getRedoNanos()
		{
			return redoNanos.get();
		}

		/** Sum of absolute sleep errors carried between payments. */
		long getSleepErrorNanos()
		{
			return sleepErrorNanos.get();
		}

		long getSleeps()
		{
			return sleeps.get();
		}

		long getMessages()
		{
			return messages.get();
		}

		/** Host CPU time the node ran at host speed because its speed exceeded the TDF. */
		long getSaturatedNanos()
		{
			return saturatedNanos.get();
		}

		String summary()
		{
			return String.format(Locale.US,
					"compute %.1f ms, other waits %.1f ms, worker CPU %.1f ms, speed emulation %.1f ms, network %.1f ms (%d msg), downtime %.1f ms, redo %.1f ms, pacing %.1f ms, mean sleep error %.3f ms",
					computeNanos.get() / 1.0e6,
					waitNanos.get() / 1.0e6,
					externalNanos.get() / 1.0e6,
					dilationNanos.get() / 1.0e6,
					networkNanos.get() / 1.0e6,
					messages.get(),
					downtimeNanos.get() / 1.0e6,
					redoNanos.get() / 1.0e6,
					pacingNanos.get() / 1.0e6,
					sleeps.get() == 0L ? 0.0 : sleepErrorNanos.get() / 1.0e6 / sleeps.get());
		}
	}

	/**
	 * A measured span of work on the current thread. Spans nest: opening a
	 * span settles the enclosing span's work so far, and closing it restarts
	 * the enclosing span's measurement, so every nanosecond of work is counted
	 * exactly once, by the innermost span that encloses it.
	 */
	static final class Span
	{
		private long cpuMark;
		private long wallMark;
		private long excludedMark;
		private final long cpuStart;
		private final long wallStart;
		private final long excludedStart;
		private final Span parent;
		private final boolean unit;

		private Span(long cpu, long wall, long excluded, Span parent, boolean unit)
		{
			this.unit = unit;
			this.cpuMark = cpu;
			this.wallMark = wall;
			this.excludedMark = excluded;
			this.cpuStart = cpu;
			this.wallStart = wall;
			this.excludedStart = excluded;
			this.parent = parent;
		}
	}

	/** Emulation state of one thread working for this node. */
	private static final class ThreadState
	{
		private Span top;
		/** Wall nanoseconds owed (negative: slept too long, credit). */
		private long debt;
		/** Wall nanoseconds this thread spent in emulator waits (never counted as work). */
		private long excluded;
	}

	private final SchedulingRunContext context;
	private final int node;
	private final Ledger ledger = new Ledger();
	private final ThreadLocal<ThreadState> threads = new ThreadLocal<ThreadState>()
	{
		protected ThreadState initialValue()
		{
			return new ThreadState();
		}
	};

	NodeEmulator(SchedulingRunContext context, int node)
	{
		this.context = context;
		this.node = node;
	}

	int getNode()
	{
		return node;
	}

	Ledger getLedger()
	{
		return ledger;
	}

	boolean isEmulating()
	{
		return context != null && context.isEmulating();
	}

	// ------------------------------------------------------------ spans

	/** Starts measuring node work (message handling, node-local stages) on this thread. */
	Span begin()
	{
		return open(false);
	}

	/**
	 * Starts measuring one work unit's execution: unlike other node work, the
	 * unit's non-CPU waits (worker start-up, files) are part of its duration.
	 */
	Span beginUnit()
	{
		return open(true);
	}

	private Span open(boolean unit)
	{
		ThreadState state = threads.get();
		if (state.top != null)
		{
			accrue(state, state.top, 0L);
		}
		Span span = new Span(threadCpuNanos(), System.nanoTime(), state.excluded, state.top, unit);
		state.top = span;
		return span;
	}

	/**
	 * Ends a span: accrues the work since the span began (or was last settled)
	 * and pays the debt when it is due. Returns the host CPU nanoseconds.
	 */
	long end(Span span)
	{
		return end(span, 0L, false);
	}

	/**
	 * Ends a span that also waited for {@code externalCpuNanos} of worker
	 * process CPU. With {@code settle} the whole debt is paid now, so the
	 * work's model end time is exact (used at the end of every work unit).
	 */
	long end(Span span, long externalCpuNanos, boolean settle)
	{
		if (span == null)
		{
			return 0L;
		}
		ThreadState state = threads.get();
		long cpu = accrue(state, span, Math.max(0L, externalCpuNanos));
		if (state.top == span)
		{
			state.top = span.parent;
		}
		try
		{
			pay(state, settle);
		}
		finally
		{
			if (span.parent != null)
			{
				//The enclosing span resumes measuring now.
				span.parent.cpuMark = threadCpuNanos();
				span.parent.wallMark = System.nanoTime();
				span.parent.excludedMark = state.excluded;
			}
		}
		return cpu;
	}

	/**
	 * Settles the current thread's open span and pays its whole debt. Called
	 * before a node synchronizes with other nodes or sends a message, so a
	 * slow node reaches every synchronization point late.
	 */
	void checkpoint()
	{
		ThreadState state = threads.get();
		if (state.top != null)
		{
			accrue(state, state.top, 0L);
		}
		pay(state, true);
	}

	/**
	 * Runs {@code work} on this thread without charging it to this node (for
	 * example a collective reduction the coordinator performs). Returns the
	 * host CPU nanoseconds it used.
	 */
	long runUncharged(Runnable work)
	{
		ThreadState state = threads.get();
		if (state.top != null)
		{
			accrue(state, state.top, 0L);
		}
		long cpuBefore = threadCpuNanos();
		long wallBefore = System.nanoTime();
		try
		{
			work.run();
		}
		finally
		{
			long wall = System.nanoTime() - wallBefore;
			state.excluded += wall;
			if (state.top != null)
			{
				state.top.cpuMark = threadCpuNanos();
				state.top.wallMark = System.nanoTime();
				state.top.excludedMark = state.excluded;
			}
		}
		return Math.max(0L, threadCpuNanos() - cpuBefore);
	}

	/**
	 * Accrues the work of {@code span} since its last mark into the thread's
	 * debt and restarts the mark. Returns the host CPU nanoseconds accrued.
	 */
	private long accrue(ThreadState state, Span span, long externalCpuNanos)
	{
		long cpuNow = threadCpuNanos();
		long wallNow = System.nanoTime();
		long cpu = CPU_TIME_AVAILABLE ? Math.max(0L, cpuNow - span.cpuMark)
				: Math.max(0L, wallNow - span.wallMark - (state.excluded - span.excludedMark));
		long wall = Math.max(0L, wallNow - span.wallMark - (state.excluded - span.excludedMark));
		long wait = Math.max(0L, wall - cpu);
		long external = Math.min(Math.max(0L, externalCpuNanos), wait);
		wait -= external;
		span.cpuMark = cpuNow;
		span.wallMark = wallNow;
		span.excludedMark = state.excluded;
		ledger.computeNanos.addAndGet(cpu);
		ledger.waitNanos.addAndGet(wait);
		ledger.externalNanos.addAndGet(external);
		if (!isEmulating())
		{
			return cpu;
		}
		double dilation = context.clock().getDilation();
		double speed = context.speedOf(node);
		double effective = Math.min(speed, dilation);
		if (speed > dilation + 1e-12)
		{
			ledger.saturatedNanos.addAndGet(cpu + external);
		}
		double owed = (cpu + external) * (dilation / effective - 1.0)
				+ (span.unit ? wait * (dilation - 1.0) : 0.0);
		long rounded = (long)Math.round(owed);
		ledger.owedNanos.addAndGet(rounded);
		state.debt += rounded;
		return cpu;
	}

	/** Pays the thread's debt when due (always with {@code settle}). */
	private void pay(ThreadState state, boolean settle)
	{
		if (!isEmulating())
		{
			state.debt = 0L;
			return;
		}
		if (state.debt <= 0L || (!settle && state.debt < PAY_THRESHOLD_NANOS))
		{
			return;
		}
		long target = state.debt;
		long slept = sleepPrecisely(state, target, "speed emulation");
		long residual = target - slept;
		if (settle && residual > 0L && residual <= RESIDUAL_NANOS)
		{
			//At a unit's end or a synchronization point the time must be right
			//now, not on average: spin away the sub-timer residual.
			long spinStart = System.nanoTime();
			long deadline = spinStart + residual;
			while (System.nanoTime() < deadline)
			{
				Thread.onSpinWait();
			}
			long spun = System.nanoTime() - spinStart;
			state.excluded += spun;
			slept += spun;
		}
		ledger.dilationNanos.addAndGet(slept);
		ledger.sleepErrorNanos.addAndGet(Math.abs(slept - target));
		ledger.sleeps.incrementAndGet();
		state.debt -= slept;
	}

	/** Records {@code workNanos} of worker-process CPU outside any span and dilates it. */
	void dilate(long workNanos)
	{
		long work = Math.max(0L, workNanos);
		ledger.externalNanos.addAndGet(work);
		if (!isEmulating() || work == 0L)
		{
			return;
		}
		ThreadState state = threads.get();
		double dilation = context.clock().getDilation();
		double effective = Math.min(context.speedOf(node), dilation);
		long owed = (long)Math.round(work * (dilation / effective));
		ledger.owedNanos.addAndGet(owed);
		state.debt += owed;
		pay(state, false);
	}

	// ------------------------------------------------------------ time

	/**
	 * Blocks until model time {@code modelMs} (a link arrival, a unit's ready
	 * time). Returns the model time reached.
	 */
	double awaitModelTime(double modelMs, boolean network)
	{
		if (!isEmulating() || Double.isNaN(modelMs))
		{
			return context == null ? modelMs : context.clock().nowMs();
		}
		ThreadState state = threads.get();
		if (state.top != null)
		{
			accrue(state, state.top, 0L);
		}
		pay(state, true);
		long deadline = context.clock().wallNanosAt(modelMs);
		long remaining = deadline - System.nanoTime();
		if (remaining > 0L)
		{
			long slept = sleepPrecisely(state, remaining, network ? "network emulation" : "waiting");
			if (network)
			{
				ledger.networkNanos.addAndGet(slept);
			}
			else
			{
				ledger.dilationNanos.addAndGet(slept);
			}
		}
		return context.clock().nowMs();
	}

	/** Model time now (0 without a context). */
	double now()
	{
		return context == null ? 0.0 : context.clock().nowMs();
	}

	/**
	 * Sends {@code bytes} from this node to the coordinator now and returns
	 * the arrival model time; the node does not wait (sending overlaps with
	 * its work).
	 */
	double sendToHub(long bytes)
	{
		ledger.messages.incrementAndGet();
		if (!isEmulating())
		{
			return now();
		}
		checkpoint();
		return context.network().send(node, NetworkModel.HUB, bytes, context.clock().nowMs());
	}

	/** Emulates {@code messageCount} header-only messages to the coordinator and waits for them. */
	void linkDelay(int messageCount)
	{
		for (int index = 0; index < messageCount; index++)
		{
			double arrival = sendToHub(NetworkModel.MESSAGE_HEADER_BYTES);
			awaitModelTime(arrival, true);
		}
	}

	/** One-way latency of this node's link in model milliseconds (0 unemulated). */
	long linkDelayMillis()
	{
		return isEmulating() ? Math.max(0, context.delayOf(node)) : 0L;
	}

	/** Records messages whose link time overlapped with other work. */
	void noteLatency(int messageCount)
	{
		if (messageCount > 0)
		{
			ledger.messages.addAndGet(messageCount);
		}
	}

	/** Operator action window: slows claims so live controls can be tried. */
	void pacing(int milliseconds)
	{
		if (milliseconds <= 0)
		{
			return;
		}
		ThreadState state = threads.get();
		long nanos = 1000000L * milliseconds;
		long slept = sleepPrecisely(state, nanos, "interactive action window");
		ledger.pacingNanos.addAndGet(slept);
	}

	// ------------------------------------------------------------ sleeping

	/**
	 * Sleeps about {@code nanos}: parks in shrinking steps until less than the
	 * residual remains. Honors aborts, revocation of the current unit and node
	 * crashes (a crashed node blocks until repaired; the time is downtime, not
	 * sleep). Returns the wall nanoseconds slept (excluding downtime).
	 */
	private long sleepPrecisely(ThreadState state, long nanos, String what)
	{
		long start = System.nanoTime();
		long deadline = start + Math.max(0L, nanos);
		long downtime = 0L;
		try
		{
			while (true)
			{
				long now = System.nanoTime();
				long remaining = deadline - now;
				if (remaining <= RESIDUAL_NANOS)
				{
					break;
				}
				if (context != null)
				{
					context.requireActive();
					if (context.isDown(node))
					{
						long blocked = awaitUp(state);
						downtime += blocked;
						deadline += blocked;
						continue;
					}
				}
				UnitCancellation.check();
				if (Thread.currentThread().isInterrupted())
				{
					throw new IllegalStateException(
							"Node " + node + " was interrupted during " + what + ".");
				}
				//The platform timer overshoots by up to about a fifth of the
				//requested time, so park three quarters of what remains: the
				//deadline is approached from below in a few steps.
				long park = Math.min((long)(remaining * 0.75), MAX_PARK_NANOS);
				LockSupport.parkNanos(Math.max(1000L, park));
			}
		}
		finally
		{
			state.excluded += System.nanoTime() - start;
		}
		return System.nanoTime() - start - downtime;
	}

	/**
	 * Blocks the current thread while the node is down. A node-local activity
	 * in progress (no work unit) is redone after the repair: its work so far is
	 * owed again. Returns the wall nanoseconds blocked.
	 */
	private long awaitUp(ThreadState state)
	{
		long start = System.nanoTime();
		context.awaitNodeUp(node);
		long blocked = System.nanoTime() - start;
		ledger.downtimeNanos.addAndGet(blocked);
		UnitCancellation.check();
		Span top = state.top;
		if (top != null && UnitCancellation.current() == null && isEmulating())
		{
			Span root = top;
			while (root.parent != null)
			{
				root = root.parent;
			}
			long cpu = Math.max(0L, root.cpuMark - root.cpuStart);
			double dilation = context.clock().getDilation();
			double effective = Math.min(context.speedOf(node), dilation);
			long redo = (long)Math.round(cpu * dilation / effective);
			ledger.redoNanos.addAndGet(redo);
			ledger.owedNanos.addAndGet(redo);
			state.debt += redo;
		}
		return blocked;
	}

	/** Current thread CPU time, or wall time when the JVM cannot measure CPU. */
	static long threadCpuNanos()
	{
		if (!CPU_TIME_AVAILABLE)
		{
			return System.nanoTime();
		}
		try
		{
			return THREADS.getCurrentThreadCpuTime();
		}
		catch (UnsupportedOperationException unsupported)
		{
			return System.nanoTime();
		}
	}

	static boolean isCpuTimeMeasured()
	{
		return CPU_TIME_AVAILABLE;
	}

	private static boolean cpuTimeAvailable()
	{
		try
		{
			if (!THREADS.isCurrentThreadCpuTimeSupported())
			{
				return false;
			}
			if (!THREADS.isThreadCpuTimeEnabled())
			{
				THREADS.setThreadCpuTimeEnabled(true);
			}
			return THREADS.isThreadCpuTimeEnabled();
		}
		catch (RuntimeException unsupported)
		{
			return false;
		}
	}
}
