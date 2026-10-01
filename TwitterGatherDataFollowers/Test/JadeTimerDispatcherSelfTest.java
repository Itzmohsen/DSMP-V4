package TwitterGatherDataFollowers.userRyersonU;

import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import jade.core.Timer;
import jade.core.TimerDispatcher;
import jade.core.TimerListener;

/**
 * Checks the compatibility TimerDispatcher (compat/jade/core): removing a
 * timer that has already fired must not delete another timer that took its
 * millisecond, which in JADE 4.5 froze a random agent's TickerBehaviour.
 */
public final class JadeTimerDispatcherSelfTest
{
	private static final long WAIT_MS = 5000L;

	private JadeTimerDispatcherSelfTest()
	{
	}

	public static void main(String[] args) throws Exception
	{
		String source = String.valueOf(
				TimerDispatcher.class.getProtectionDomain().getCodeSource().getLocation());
		require(!source.endsWith("jade.jar"),
				"the compat TimerDispatcher must come before jade.jar on the classpath, not " + source);
		lateRemovalKeepsTheTimerThatTookTheMillisecond();
		removeCancelsAPendingTimer();
		timersForOneMillisecondAllFire();
		System.out.println("JadeTimerDispatcherSelfTest passed");
	}

	/**
	 * The race, made deterministic: the dispatcher takes an expired timer out
	 * of its set and fires it; before the owner removes it (as
	 * Agent.doTimeOut does), another agent adds a timer for that same, now
	 * free, millisecond. The late removal must leave the new timer alone.
	 */
	private static void lateRemovalKeepsTheTimerThatTookTheMillisecond() throws Exception
	{
		final TimerDispatcher dispatcher = new TimerDispatcher();
		final long past = System.currentTimeMillis() - 1000L;
		final CountDownLatch secondFired = new CountDownLatch(1);
		final Timer[] second = new Timer[1];
		Timer first = new Timer(past, new TimerListener()
		{
			public void doTimeOut(Timer fired)
			{
				second[0] = dispatcher.add(new Timer(past, new TimerListener()
				{
					public void doTimeOut(Timer t)
					{
						secondFired.countDown();
					}
				}));
				dispatcher.remove(fired);
			}
		});
		try
		{
			dispatcher.add(first);
			boolean fired = secondFired.await(WAIT_MS, TimeUnit.MILLISECONDS);
			require(second[0] != null, "the first timer never fired");
			require(expiration(second[0]) == expiration(first),
					"the second timer should have taken the fired timer's millisecond");
			require(fired, "removing a fired timer deleted another agent's timer for the same millisecond");
		}
		finally
		{
			stop(dispatcher);
		}
	}

	private static void removeCancelsAPendingTimer() throws Exception
	{
		TimerDispatcher dispatcher = new TimerDispatcher();
		final AtomicBoolean cancelledFired = new AtomicBoolean();
		final CountDownLatch keptFired = new CountDownLatch(1);
		long soon = System.currentTimeMillis() + 1000L;
		try
		{
			Timer cancelled = dispatcher.add(new Timer(soon, new TimerListener()
			{
				public void doTimeOut(Timer t)
				{
					cancelledFired.set(true);
				}
			}));
			dispatcher.remove(cancelled);
			Timer kept = dispatcher.add(new Timer(soon, new TimerListener()
			{
				public void doTimeOut(Timer t)
				{
					keptFired.countDown();
				}
			}));
			require(expiration(kept) == soon, "a cancelled timer's millisecond should be free again");
			require(keptFired.await(WAIT_MS, TimeUnit.MILLISECONDS), "a pending timer did not fire");
			Thread.sleep(50L);
			require(!cancelledFired.get(), "a removed timer fired");
		}
		finally
		{
			stop(dispatcher);
		}
	}

	private static void timersForOneMillisecondAllFire() throws Exception
	{
		TimerDispatcher dispatcher = new TimerDispatcher();
		int count = 50;
		final CountDownLatch fired = new CountDownLatch(count);
		long soon = System.currentTimeMillis() + 500L;
		long[] expirations = new long[count];
		try
		{
			for (int i = 0; i < count; i++)
			{
				expirations[i] = expiration(dispatcher.add(new Timer(soon, new TimerListener()
				{
					public void doTimeOut(Timer t)
					{
						fired.countDown();
					}
				})));
			}
			for (int i = 0; i < count; i++)
			{
				require(expirations[i] == soon + i, "colliding timers should take the following milliseconds");
			}
			require(fired.await(WAIT_MS, TimeUnit.MILLISECONDS),
					"only " + (count - fired.getCount()) + " of " + count + " timers fired");
		}
		finally
		{
			stop(dispatcher);
		}
	}

	private static long expiration(Timer timer) throws Exception
	{
		Method method = Timer.class.getDeclaredMethod("expirationTime");
		method.setAccessible(true);
		return ((Long)method.invoke(timer)).longValue();
	}

	/** Ends the dispatcher's (non-daemon) thread. */
	private static void stop(TimerDispatcher dispatcher) throws Exception
	{
		Method method = TimerDispatcher.class.getDeclaredMethod("stop");
		method.setAccessible(true);
		method.invoke(dispatcher);
	}

	private static void require(boolean condition, String message)
	{
		if (!condition)
		{
			throw new IllegalStateException(message);
		}
	}
}
