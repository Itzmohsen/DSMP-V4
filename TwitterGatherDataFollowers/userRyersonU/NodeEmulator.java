package TwitterGatherDataFollowers.userRyersonU;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Makes one node's capacity profile physically true in a live run.
 *
 * <b>Speed.</b> All recommender nodes are threads of one JVM on one machine,
 * so they naturally run at the same speed. After each piece of node work the
 * emulator measures the work W (CPU time of the calling thread, so time spent
 * waiting on barriers or messages is not counted) and sleeps W x (1/speed - 1).
 * A node of speed 0.5 therefore takes twice as long for the same work, and the
 * fastest node runs at hardware speed. The fidelity condition is that the
 * machine has a core for every busy lane; the run report says when it does not.
 *
 * <b>Links.</b> Each node's link has a one-way delay d; a message to or from
 * the node costs d, and the emulator sleeps it.
 *
 * Every emulated nanosecond is recorded in the node's {@link Ledger}, so timing
 * reports can separate real compute, speed emulation, network emulation and
 * interactive pacing. In a baseline run the emulator does nothing.
 */
final class NodeEmulator
{
	private static final ThreadMXBean THREADS = ManagementFactory.getThreadMXBean();
	private static final boolean CPU_TIME_AVAILABLE = cpuTimeAvailable();

	/** Per-node accounting of where wall time went. */
	static final class Ledger
	{
		private final AtomicLong computeNanos = new AtomicLong();
		private final AtomicLong dilationNanos = new AtomicLong();
		private final AtomicLong networkNanos = new AtomicLong();
		private final AtomicLong pacingNanos = new AtomicLong();
		private final AtomicLong messages = new AtomicLong();

		long getComputeNanos()
		{
			return computeNanos.get();
		}

		long getDilationNanos()
		{
			return dilationNanos.get();
		}

		long getNetworkNanos()
		{
			return networkNanos.get();
		}

		long getPacingNanos()
		{
			return pacingNanos.get();
		}

		long getMessages()
		{
			return messages.get();
		}

		String summary()
		{
			return String.format(Locale.US,
					"compute %.1f ms, speed emulation %.1f ms, network emulation %.1f ms (%d msg), interactive pacing %.1f ms",
					computeNanos.get() / 1.0e6,
					dilationNanos.get() / 1.0e6,
					networkNanos.get() / 1.0e6,
					messages.get(),
					pacingNanos.get() / 1.0e6);
		}
	}

	/**
	 * A measured span of work on the current thread. Spans nest: opening a span
	 * first settles the enclosing span's work so far, and closing it restarts
	 * the enclosing span's measurement, so every nanosecond of work is dilated
	 * exactly once, by the innermost span that encloses it.
	 */
	static final class Span
	{
		private long cpuMark;
		private long wallMark;
		private final Span parent;

		private Span(long cpuMark, long wallMark, Span parent)
		{
			this.cpuMark = cpuMark;
			this.wallMark = wallMark;
			this.parent = parent;
		}
	}

	private final SchedulingRunContext context;
	private final int node;
	private final Ledger ledger = new Ledger();
	private final ThreadLocal<Span> openSpan = new ThreadLocal<Span>();

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

	/** Starts measuring work done on the current thread. */
	Span begin()
	{
		Span parent = openSpan.get();
		if (parent != null)
		{
			settle(parent);
		}
		Span span = new Span(threadCpuNanos(), System.nanoTime(), parent);
		openSpan.set(span);
		return span;
	}

	/**
	 * Ends a span: records the work since the span began (or since its last
	 * settlement) and, when emulating, dilates it by the node's current speed.
	 * Returns the work in nanoseconds.
	 */
	long end(Span span)
	{
		if (span == null)
		{
			return 0L;
		}
		long work = CPU_TIME_AVAILABLE
				? Math.max(0L, threadCpuNanos() - span.cpuMark)
				: Math.max(0L, System.nanoTime() - span.wallMark);
		if (openSpan.get() == span)
		{
			openSpan.set(span.parent);
		}
		try
		{
			dilate(work);
		}
		finally
		{
			if (span.parent != null)
			{
				//The enclosing span resumes measuring now.
				span.parent.cpuMark = threadCpuNanos();
				span.parent.wallMark = System.nanoTime();
			}
		}
		return work;
	}

	/**
	 * Settles the current thread's open span: dilates the work done since the
	 * span began or was last settled. Called before a node synchronizes with
	 * other nodes, so a slow node reaches every synchronization point late.
	 */
	void checkpoint()
	{
		Span span = openSpan.get();
		if (span != null)
		{
			settle(span);
		}
	}

	private void settle(Span span)
	{
		long work;
		if (CPU_TIME_AVAILABLE)
		{
			long now = threadCpuNanos();
			work = Math.max(0L, now - span.cpuMark);
		}
		else
		{
			work = Math.max(0L, System.nanoTime() - span.wallMark);
		}
		dilate(work);
		span.cpuMark = threadCpuNanos();
		span.wallMark = System.nanoTime();
	}

	/** Records {@code workNanos} of work reported by a subprocess and dilates it. */
	void dilate(long workNanos)
	{
		long work = Math.max(0L, workNanos);
		ledger.computeNanos.addAndGet(work);
		if (!isEmulating() || work == 0L)
		{
			return;
		}
		double speed = context.speedOf(node);
		if (speed >= 1.0)
		{
			return;
		}
		long extra = (long)Math.round(work * (1.0 / speed - 1.0));
		ledger.dilationNanos.addAndGet(extra);
		sleepNanos(extra, "speed emulation");
	}

	/** Emulates {@code messageCount} one-way messages on this node's link. */
	void linkDelay(int messageCount)
	{
		if (messageCount <= 0)
		{
			return;
		}
		ledger.messages.addAndGet(messageCount);
		if (!isEmulating())
		{
			return;
		}
		long nanos = 1000000L * (long)context.delayOf(node) * messageCount;
		if (nanos <= 0L)
		{
			return;
		}
		ledger.networkNanos.addAndGet(nanos);
		sleepNanos(nanos, "network emulation");
	}

	/**
	 * One-way delay of this node's link in milliseconds, or 0 when the run is
	 * not emulated. Messages in flight overlap: callers delay a message's
	 * handling by this much without blocking the node (see {@link #noteLatency}).
	 */
	long linkDelayMillis()
	{
		return isEmulating() ? Math.max(0, context.delayOf(node)) : 0L;
	}

	/** Records messages whose link delay overlapped with other work. */
	void noteLatency(int messageCount)
	{
		if (messageCount > 0)
		{
			ledger.messages.addAndGet(messageCount);
		}
	}

	/** Emulates a transfer the dispatcher priced in milliseconds. */
	void transfer(double milliseconds)
	{
		if (!isEmulating() || milliseconds <= 0.0)
		{
			return;
		}
		long nanos = (long)Math.round(milliseconds * 1.0e6);
		ledger.networkNanos.addAndGet(nanos);
		sleepNanos(nanos, "network emulation");
	}

	/** Operator action window: slows claims so live controls can be tried. */
	void pacing(int milliseconds)
	{
		if (milliseconds <= 0)
		{
			return;
		}
		long nanos = 1000000L * milliseconds;
		ledger.pacingNanos.addAndGet(nanos);
		sleepNanos(nanos, "interactive action window");
	}

	private void sleepNanos(long nanos, String what)
	{
		long deadline = System.nanoTime() + nanos;
		while (true)
		{
			long remaining = deadline - System.nanoTime();
			if (remaining <= 0L)
			{
				return;
			}
			if (context != null)
			{
				context.requireActive();
			}
			try
			{
				long millis = Math.min(100L, Math.max(1L, remaining / 1000000L));
				if (remaining < 1000000L)
				{
					Thread.sleep(0L, (int)remaining);
					return;
				}
				Thread.sleep(millis);
			}
			catch (InterruptedException interrupted)
			{
				Thread.currentThread().interrupt();
				throw new IllegalStateException(
						"Node " + node + " was interrupted during " + what + ".", interrupted);
			}
		}
	}

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
