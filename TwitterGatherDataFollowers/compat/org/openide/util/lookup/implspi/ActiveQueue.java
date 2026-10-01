package org.openide.util.lookup.implspi;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Compatibility replacement for the NetBeans lookup library's ActiveQueue
 * bundled in gephi-toolkit.jar (2016). It shadows the jar's class because the
 * compiled classes come first on the classpath.
 *
 * The original daemon calls {@code super.remove(0)} on a queue that overrides
 * {@code remove()} to throw. Since JDK 19, {@code ReferenceQueue.remove(0)}
 * delegates to the overridable {@code remove()}, so the daemon caught the
 * exception and retried forever, burning a whole core for the life of the JVM
 * and distorting every timing measurement. This version waits with a positive
 * timeout, which never dispatches to the overridden methods, on every JDK.
 * Behaviour is otherwise unchanged: references enqueued on
 * {@link #queue()} must implement {@link Runnable} and are run by one daemon
 * thread.
 */
public final class ActiveQueue
{
	private static final Logger LOGGER = Logger.getLogger(ActiveQueue.class.getName());
	private static Impl activeReferenceQueue;

	private ActiveQueue()
	{
	}

	/** The shared active reference queue; references on it are run when collected. */
	public static synchronized ReferenceQueue<Object> queue()
	{
		if (activeReferenceQueue == null)
		{
			activeReferenceQueue = new Impl();
			Daemon.ping();
		}
		return activeReferenceQueue;
	}

	static synchronized Impl current()
	{
		return activeReferenceQueue;
	}

	/** A queue only the daemon may drain. */
	static final class Impl extends ReferenceQueue<Object>
	{
		@Override
		public Reference<Object> poll()
		{
			throw new UnsupportedOperationException();
		}

		@Override
		public Reference<Object> remove(long timeout) throws IllegalArgumentException, InterruptedException
		{
			throw new InterruptedException();
		}

		@Override
		public Reference<Object> remove() throws InterruptedException
		{
			throw new InterruptedException();
		}

		/** Blocks until a reference is enqueued, without calling the overridden methods. */
		Reference<? extends Object> removeSuper() throws InterruptedException
		{
			while (true)
			{
				Reference<? extends Object> reference = super.remove(60000L);
				if (reference != null)
				{
					return reference;
				}
			}
		}
	}

	static final class Daemon extends Thread
	{
		private static Daemon running;

		Daemon()
		{
			super("Active Reference Queue Daemon");
		}

		static synchronized void ping()
		{
			if (running == null)
			{
				Daemon daemon = new Daemon();
				daemon.setPriority(Thread.MIN_PRIORITY);
				daemon.setDaemon(true);
				daemon.start();
				LOGGER.fine("starting thread");
				running = daemon;
			}
		}

		static synchronized boolean isActive()
		{
			return running != null;
		}

		@Override
		public void run()
		{
			while (true)
			{
				try
				{
					Impl queue = current();
					if (queue == null)
					{
						return;
					}
					Reference<? extends Object> reference = queue.removeSuper();
					LOGGER.log(Level.FINE, "Got dequeued reference {0}", reference);
					if (!(reference instanceof Runnable))
					{
						LOGGER.log(Level.WARNING,
								"A reference not implementing runnable has been added to the Utilities.activeReferenceQueue(): {0}",
								reference.getClass());
						continue;
					}
					try
					{
						((Runnable)reference).run();
					}
					catch (Throwable failure)
					{
						LOGGER.log(Level.WARNING, "Cannot process " + reference, failure);
					}
				}
				catch (InterruptedException interrupted)
				{
					//Keep serving the queue, as the original daemon did.
				}
			}
		}
	}
}
