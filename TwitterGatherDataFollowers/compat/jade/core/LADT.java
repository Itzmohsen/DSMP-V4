package jade.core;

import jade.util.leap.HashMap;
import jade.util.leap.Map;

/**
 * Compatibility replacement for JADE 4.5's local agent descriptor table
 * (shadows the class in jade.jar; compiled classes come first on the
 * classpath): a map of agent rows, each a re-entrant lock owned by one
 * thread at a time.
 *
 * The fix: JADE's put and remove changed the (unsynchronized) hash map
 * outside the lock that acquire and release read it under. While a
 * container creates many agents and messages already flow, a resize could
 * make release miss a row; the row then stayed locked by an idle message
 * deliverer, every later delivery to that agent blocked, and killing the
 * agent hung. Here every access to the map holds the map's lock (rows are
 * still locked outside it, as before, so the lock order is unchanged).
 *
 * Diagnostics: with -Ddsmp.jade.ladt.diagnostics=true, a thread that waits
 * more than five seconds for a row prints the row's owner, the owner's
 * current stack and the stack with which it took the row.
 */
class LADT
{
	private static final boolean DIAGNOSTICS = Boolean.getBoolean("dsmp.jade.ladt.diagnostics");

	/** One agent's row: the agent and a re-entrant lock. */
	private static final class Row
	{
		private Agent value;
		private Thread owner;
		private long depth;
		private Throwable lockedAt;

		public Row(Agent value)
		{
			this.value = value;
			this.depth = 0L;
		}

		public synchronized Agent get()
		{
			return value;
		}

		public synchronized void clear()
		{
			value = null;
		}

		public synchronized void lock()
		{
			try
			{
				if (value == null)
				{
					return;
				}
				Thread current = Thread.currentThread();
				long started = System.currentTimeMillis();
				boolean reported = false;
				while (owner != null && owner != current)
				{
					if (DIAGNOSTICS)
					{
						wait(1000L);
						if (!reported && owner != null && owner != current
								&& System.currentTimeMillis() - started > 5000L)
						{
							reported = true;
							report(current);
						}
					}
					else
					{
						wait();
					}
					if (value == null)
					{
						return;
					}
				}
				owner = current;
				depth++;
				if (DIAGNOSTICS && depth == 1L)
				{
					lockedAt = new Throwable("row of " + value.getName() + " locked by " + current.getName());
				}
			}
			catch (InterruptedException interrupted)
			{
				return;
			}
		}

		public synchronized void unlock()
		{
			if (owner != Thread.currentThread())
			{
				return;
			}
			depth--;
			if (depth != 0L && value != null)
			{
				return;
			}
			owner = null;
			lockedAt = null;
			notifyAll();
		}

		private void report(Thread waiter)
		{
			Thread holder = owner;
			StringBuilder text = new StringBuilder();
			text.append("[ladt] ").append(waiter.getName()).append(" has waited over 5 s for the row of ")
					.append(value == null ? "?" : value.getName()).append(", owned by ")
					.append(holder == null ? "nobody" : holder.getName() + " (" + holder.getState() + ", alive "
							+ holder.isAlive() + ", depth " + depth + ")").append('\n');
			if (holder != null)
			{
				text.append("[ladt] owner's current stack:\n");
				for (StackTraceElement element : holder.getStackTrace())
				{
					text.append("[ladt]     at ").append(element).append('\n');
				}
			}
			if (lockedAt != null)
			{
				text.append("[ladt] the owner took the row at:\n");
				for (StackTraceElement element : lockedAt.getStackTrace())
				{
					text.append("[ladt]     at ").append(element).append('\n');
				}
			}
			System.err.print(text);
		}

		public String toString()
		{
			return value != null ? "(" + value.getName() + " :owner " + (owner != null ? owner.toString() : "null")
					+ ")" : "null";
		}
	}

	private final Map agents;

	public LADT(int size)
	{
		agents = new HashMap(size);
	}

	public Agent put(AID aid, Agent agent)
	{
		Row row;
		synchronized (agents)
		{
			row = (Row)agents.get(aid);
		}
		if (row == null)
		{
			synchronized (agents)
			{
				Row raced = (Row)agents.get(aid);
				if (raced == null)
				{
					agents.put(aid, new Row(agent));
					return null;
				}
				row = raced;
			}
		}
		row.lock();
		Agent previous = row.get();
		if (agent != previous)
		{
			synchronized (agents)
			{
				agents.put(aid, new Row(agent));
			}
		}
		row.unlock();
		return previous;
	}

	public Agent remove(AID aid)
	{
		Row row;
		synchronized (agents)
		{
			row = (Row)agents.get(aid);
		}
		if (row == null)
		{
			return null;
		}
		row.lock();
		synchronized (agents)
		{
			if (agents.get(aid) == row)
			{
				agents.remove(aid);
			}
		}
		Agent previous = row.get();
		row.clear();
		row.unlock();
		return previous;
	}

	public Agent acquire(AID aid)
	{
		Row row;
		synchronized (agents)
		{
			row = (Row)agents.get(aid);
		}
		if (row == null)
		{
			return null;
		}
		row.lock();
		return row.get();
	}

	public void release(AID aid)
	{
		Row row;
		synchronized (agents)
		{
			row = (Row)agents.get(aid);
		}
		if (row != null)
		{
			row.unlock();
		}
	}

	public AID[] keys()
	{
		synchronized (agents)
		{
			Object[] keys = agents.keySet().toArray();
			AID[] result = new AID[keys.length];
			for (int index = 0; index < result.length; index++)
			{
				result[index] = (AID)keys[index];
			}
			return result;
		}
	}

	public Agent[] values()
	{
		synchronized (agents)
		{
			Object[] rows = agents.values().toArray();
			Agent[] result = new Agent[rows.length];
			for (int index = 0; index < rows.length; index++)
			{
				result[index] = ((Row)rows[index]).get();
			}
			return result;
		}
	}

	boolean contains(AID aid)
	{
		synchronized (agents)
		{
			return agents.containsKey(aid);
		}
	}

	public String[] getStatus()
	{
		synchronized (agents)
		{
			Object[] rows = agents.values().toArray();
			String[] result = new String[rows.length];
			for (int index = 0; index < rows.length; index++)
			{
				result[index] = rows[index].toString();
			}
			return result;
		}
	}
}
