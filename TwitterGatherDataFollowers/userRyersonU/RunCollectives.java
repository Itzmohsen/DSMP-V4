package TwitterGatherDataFollowers.userRyersonU;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Collective operations among the N recommender nodes of one run.
 *
 * These are the building blocks of the exact distributed algorithms: every node
 * contributes its partial result to a named step, the last node to arrive
 * combines all contributions once, in node order, and every node receives the
 * identical combined object. Combined results built from exact arithmetic
 * ({@link ExactSum}) and integer counts are therefore identical to what one node
 * holding all the data would compute, whatever the partition.
 *
 * Communication is emulated on the star network of the capacity model: a node
 * pays its link delay once to upload its contribution and once to download the
 * result. Waits honour the run's abort registry and fail after the run's stall
 * timeout instead of hanging. Contributions and results are shared objects and
 * must be treated as read-only once submitted.
 */
final class RunCollectives
{
	/** Combines the node-ordered contributions of one step. */
	interface Reducer<R>
	{
		R reduce(List<Object> contributionsByNode);
	}

	private static final class Step
	{
		private final Object[] contributions;
		private int received;
		private Object result;
		private boolean done;
		private int consumers;
		private RuntimeException failure;

		private Step(int nodes)
		{
			contributions = new Object[nodes];
		}
	}

	private final SchedulingRunContext context;
	private final int nodes;
	private final Map<String,Step> steps = new LinkedHashMap<String,Step>();

	RunCollectives(SchedulingRunContext context, int nodes)
	{
		this.context = context;
		this.nodes = Math.max(1, nodes);
	}

	int getNodeCount()
	{
		return nodes;
	}

	/** Every node's contribution, indexed by node - 1. */
	@SuppressWarnings("unchecked")
	<T> List<T> allGather(String step, int node, T contribution)
	{
		Object result = run(step, node, contribution, new Reducer<Object>()
		{
			public Object reduce(List<Object> contributionsByNode)
			{
				return Collections.unmodifiableList(
						new ArrayList<Object>(contributionsByNode));
			}
		});
		return (List<T>)result;
	}

	/** Combines contributions once with {@code reducer}; all nodes get the result. */
	@SuppressWarnings("unchecked")
	<R> R allReduce(String step, int node, Object contribution, Reducer<R> reducer)
	{
		return (R)run(step, node, contribution, reducer);
	}

	void barrier(String step, int node)
	{
		run(step, node, Boolean.TRUE, new Reducer<Object>()
		{
			public Object reduce(List<Object> contributionsByNode)
			{
				return Boolean.TRUE;
			}
		});
	}

	private Object run(String name, int node, Object contribution, Reducer<?> reducer)
	{
		if (node < 1 || node > nodes)
		{
			throw new IllegalArgumentException(
					"Node " + node + " is outside 1.." + nodes + " for step " + name + ".");
		}
		NodeEmulator emulator = context == null ? null : context.emulator(node);
		if (emulator != null)
		{
			//Work done since the last synchronization is slowed to the node's
			//speed before it can contribute, then the upload crosses its link.
			emulator.checkpoint();
			emulator.linkDelay(1);
		}
		Step step;
		synchronized (this)
		{
			step = steps.get(name);
			if (step == null)
			{
				step = new Step(nodes);
				steps.put(name, step);
			}
			if (step.contributions[node - 1] != null)
			{
				throw new IllegalStateException(
						"Node " + node + " contributed twice to collective step " + name + ".");
			}
			step.contributions[node - 1] = contribution == null ? NULL : contribution;
			step.received++;
			if (step.received == nodes)
			{
				ArrayList<Object> ordered = new ArrayList<Object>();
				for (Object value : step.contributions)
				{
					ordered.add(value == NULL ? null : value);
				}
				try
				{
					step.result = reducer.reduce(ordered);
				}
				catch (RuntimeException failure)
				{
					step.failure = failure;
				}
				step.done = true;
				//Contributions are no longer needed once combined.
				java.util.Arrays.fill(step.contributions, null);
				notifyAll();
			}
			long deadline = context == null ? Long.MAX_VALUE
					: System.currentTimeMillis() + context.getStallTimeoutMs();
			while (!step.done)
			{
				if (context != null)
				{
					if (context.isAborted())
					{
						steps.remove(name);
						notifyAll();
						context.requireActive();
					}
				}
				long remaining = deadline - System.currentTimeMillis();
				if (remaining <= 0L)
				{
					steps.remove(name);
					notifyAll();
					throw new IllegalStateException(
							"Timed out in collective step " + name + ": " + step.received
							+ " of " + nodes + " node(s) arrived.");
				}
				try
				{
					wait(Math.min(250L, remaining));
				}
				catch (InterruptedException interrupted)
				{
					Thread.currentThread().interrupt();
					throw new IllegalStateException(
							"Interrupted in collective step " + name + ".", interrupted);
				}
			}
			step.consumers++;
			if (step.consumers >= nodes)
			{
				steps.remove(name);
			}
			if (step.failure != null)
			{
				throw new IllegalStateException(
						"Collective step " + name + " failed: " + step.failure.getMessage(),
						step.failure);
			}
		}
		if (emulator != null)
		{
			emulator.linkDelay(1);
		}
		return step.result;
	}

	/** Fails every pending step so its waiters stop (used when a run ends). */
	synchronized void clear()
	{
		for (Step step : steps.values())
		{
			if (!step.done)
			{
				step.failure = new IllegalStateException("the run ended");
				step.done = true;
			}
		}
		steps.clear();
		notifyAll();
	}

	private static final Object NULL = new Object();
}
