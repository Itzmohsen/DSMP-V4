package jade.core;

import java.util.Comparator;
import java.util.TreeSet;

import jade.util.Logger;

/**
 * Compatibility replacement for JADE 4.5's timer dispatcher (shadows the
 * class in jade.jar; compiled classes come first on the classpath): one
 * thread that fires the platform's timers (every behaviour blocked with a
 * timeout) in expiration order.
 *
 * The fix: the dispatcher keeps its timers in a set ordered by expiration
 * time alone (add moves a timer forward one millisecond at a time until its
 * millisecond is free), and JADE's remove deleted whichever timer held the
 * given timer's millisecond. A timer that has fired has already left the set,
 * yet its owner still removes it (Agent.doTimeOut, and a behaviour restarted
 * or blocked again before the expiry was handled). If another agent's new
 * timer had taken the freed millisecond in between, that timer was deleted
 * instead, and the behaviour waiting for it never woke. With hundreds of
 * agents ticking every millisecond (user agents when tweet delays are off)
 * this froze a random agent's TickerBehaviour mid-run, so the run never
 * finished. Here remove deletes a timer only if that very timer is in the
 * set. Everything else is as in JADE 4.5.
 */
public class TimerDispatcher implements Runnable
{
	private static TimerDispatcher theDispatcher;

	private Thread myThread = null;
	private final Timers timers = new Timers();
	private boolean active;
	protected Logger myLogger = Logger.getJADELogger(getClass().getName());

	void setThread(Thread t)
	{
		if (myThread == null)
		{
			myThread = t;
		}
	}

	/** Adds a timer, moving it to the next free millisecond; returns it. */
	public synchronized Timer add(Timer t)
	{
		if (myThread == null)
		{
			myThread = new Thread(this);
			start();
		}
		while (!timers.add(t))
		{
			t.setExpirationTime(t.expirationTime() + 1);
		}
		if (timers.first() == t)
		{
			wakeUp();
		}
		return t;
	}

	/** Removes this timer if it is still pending (never another one). */
	public synchronized void remove(Timer t)
	{
		timers.remove(t);
	}

	public void run()
	{
		try
		{
			while (active)
			{
				Timer t = null;
				synchronized (this)
				{
					while (active)
					{
						long timeToWait = 0;
						if (!timers.isEmpty())
						{
							t = timers.first();
							if (t.isExpired())
							{
								timers.remove(t);
								break;
							}
							timeToWait = t.expirationTime() - System.currentTimeMillis();
							if (timeToWait <= 0)
							{
								timeToWait = 1;
							}
						}
						sleep(timeToWait);
					}
				}
				if (active)
				{
					t.fire();
				}
			}
		}
		catch (InterruptedException interrupted)
		{
			// Stopped.
		}
		timers.clear();
	}

	protected void sleep(long sleepTime) throws InterruptedException
	{
		wait(sleepTime);
	}

	protected void wakeUp()
	{
		notifyAll();
	}

	void start()
	{
		synchronized (myThread)
		{
			active = true;
			myThread.start();
		}
	}

	void stop()
	{
		if (myThread != null)
		{
			synchronized (myThread)
			{
				if (Thread.currentThread().equals(myThread))
				{
					System.out.println("Deadlock avoidance: TimerDispatcher thread calling stop on itself!");
				}
				else
				{
					active = false;
					synchronized (this)
					{
						wakeUp();
					}
					try
					{
						myThread.join();
					}
					catch (InterruptedException interrupted)
					{
						// Stop anyway.
					}
				}
				myThread = null;
			}
		}
	}

	public static TimerDispatcher getTimerDispatcher()
	{
		if (theDispatcher == null)
		{
			theDispatcher = new TimerDispatcher();
		}
		return theDispatcher;
	}

	public static void setTimerDispatcher(TimerDispatcher td)
	{
		theDispatcher = td;
	}

	/** Pending timers, at most one per millisecond, in expiration order. */
	private static final class Timers
	{
		private final TreeSet<Timer> set = new TreeSet<Timer>(new Comparator<Timer>()
		{
			public int compare(Timer a, Timer b)
			{
				return Long.compare(a.expirationTime(), b.expirationTime());
			}
		});

		boolean add(Timer t)
		{
			return set.add(t);
		}

		Timer first()
		{
			return set.first();
		}

		boolean isEmpty()
		{
			return set.isEmpty();
		}

		/** The set finds timers by millisecond, so check it holds this one. */
		void remove(Timer t)
		{
			if (set.ceiling(t) == t)
			{
				set.remove(t);
			}
		}

		void clear()
		{
			set.clear();
		}
	}
}
