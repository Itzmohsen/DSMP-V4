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
 * contributes its partial result to a named step, the coordinator combines all
 * contributions once, in node order, and every node receives the identical
 * combined object. Combined results built from exact arithmetic
 * ({@link ExactSum}) and integer counts are therefore identical to what one node
 * holding all the data would compute, whatever the partition.
 *
 * <b>Cost model.</b> Each node uploads its contribution over its link (its
 * wire size, {@link PayloadSize}), the coordinator starts combining when the
 * last upload has arrived and combines at the coordinator's speed, and each
 * node then downloads what it needs of the result over its link. Every node
 * waits for its download, so a slow node, a slow link or a large contribution
 * delays everyone, as in a real bulk-synchronous exchange. The reduction runs
 * on whichever thread arrives last but is charged to the coordinator, never to
 * that node. Waits honour the run's abort registry and fail after the run's
 * stall timeout instead of hanging. Contributions and results are shared
 * objects and must be treated as read-only once submitted.
 */
final class RunCollectives
{
	/** Combines the node-ordered contributions of one step. */
	interface Reducer<R>
	{
		R reduce(List<Object> contributionsByNode);
	}

	/** Bytes node {@code node} downloads of a step's result. */
	interface DownloadSizer
	{
		long bytesFor(int node, Object result, List<Long> uploadBytesByNode,
				List<Object> contributionsByNode);
	}

	/** Every node downloads the whole result. */
	static final DownloadSizer WHOLE_RESULT = new DownloadSizer()
	{
		public long bytesFor(int node, Object result, List<Long> uploadBytesByNode,
				List<Object> contributionsByNode)
		{
			return PayloadSize.of(result);
		}
	};

	/** Each node downloads everyone else's contributions (an all-gather). */
	static final DownloadSizer OTHERS_CONTRIBUTIONS = new DownloadSizer()
	{
		public long bytesFor(int node, Object result, List<Long> uploadBytesByNode,
				List<Object> contributionsByNode)
		{
			long total = 0L;
			for (int index = 0; index < uploadBytesByNode.size(); index++)
			{
				if (index != node - 1)
				{
					total += uploadBytesByNode.get(index).longValue();
				}
			}
			return total;
		}
	};

	/** Only a completion notice (barriers). */
	static final DownloadSizer NOTICE_ONLY = new DownloadSizer()
	{
		public long bytesFor(int node, Object result, List<Long> uploadBytesByNode,
				List<Object> contributionsByNode)
		{
			return 0L;
		}
	};

	private static final class Step
	{
		private final Object[] contributions;
		private final long[] uploadBytes;
		private final double[] hubArrival;
		private final long[] downloadBytes;
		private final double[] delivered;
		private int received;
		private Object result;
		private boolean done;
		private int consumers;
		private RuntimeException failure;
		private double reduceStart;
		private double reduceEnd;
		private long reduceCpuNanos;

		private Step(int nodes)
		{
			contributions = new Object[nodes];
			uploadBytes = new long[nodes];
			hubArrival = new double[nodes];
			downloadBytes = new long[nodes];
			delivered = new double[nodes];
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
		Object result = run(step, node, contribution, -1L, new Reducer<Object>()
		{
			public Object reduce(List<Object> contributionsByNode)
			{
				return Collections.unmodifiableList(
						new ArrayList<Object>(contributionsByNode));
			}
		}, OTHERS_CONTRIBUTIONS);
		return (List<T>)result;
	}

	/** Combines contributions once with {@code reducer}; all nodes get the whole result. */
	@SuppressWarnings("unchecked")
	<R> R allReduce(String step, int node, Object contribution, Reducer<R> reducer)
	{
		return (R)run(step, node, contribution, -1L, reducer, WHOLE_RESULT);
	}

	/**
	 * Combines contributions with explicit wire sizes: the node uploads
	 * {@code uploadBytes} and downloads what {@code sizer} says.
	 */
	@SuppressWarnings("unchecked")
	<R> R allReduce(String step, int node, Object contribution, long uploadBytes,
			Reducer<R> reducer, DownloadSizer sizer)
	{
		return (R)run(step, node, contribution, uploadBytes, reducer, sizer);
	}

	void barrier(String step, int node)
	{
		run(step, node, Boolean.TRUE, 0L, new Reducer<Object>()
		{
			public Object reduce(List<Object> contributionsByNode)
			{
				return Boolean.TRUE;
			}
		}, NOTICE_ONLY);
	}

	/**
	 * @param uploadBytes wire size of the contribution; negative to measure it
	 *                    (the measuring itself is never charged to the node)
	 */
	private Object run(String name, int node, final Object contribution, long uploadBytes,
			final Reducer<?> reducer, final DownloadSizer sizer)
	{
		if (node < 1 || node > nodes)
		{
			throw new IllegalArgumentException(
					"Node " + node + " is outside 1.." + nodes + " for step " + name + ".");
		}
		final NodeEmulator emulator = context == null ? null : context.emulator(node);
		if (uploadBytes < 0L)
		{
			final long[] measured = new long[1];
			Runnable sizing = new Runnable()
			{
				public void run()
				{
					measured[0] = PayloadSize.of(contribution);
				}
			};
			if (emulator != null)
			{
				emulator.runUncharged(sizing);
			}
			else
			{
				sizing.run();
			}
			uploadBytes = measured[0];
		}
		double arrival = 0.0;
		if (emulator != null)
		{
			//Work done since the last synchronization is slowed to the node's
			//speed before it can contribute; then the upload crosses its link.
			emulator.checkpoint();
			arrival = emulator.sendToHub(NetworkModel.MESSAGE_HEADER_BYTES + Math.max(0L, uploadBytes));
		}
		final Step step;
		synchronized (this)
		{
			Step existing = steps.get(name);
			if (existing == null)
			{
				existing = new Step(nodes);
				steps.put(name, existing);
			}
			step = existing;
			if (step.contributions[node - 1] != null)
			{
				throw new IllegalStateException(
						"Node " + node + " contributed twice to collective step " + name + ".");
			}
			step.contributions[node - 1] = contribution == null ? NULL : contribution;
			step.uploadBytes[node - 1] = Math.max(0L, uploadBytes);
			step.hubArrival[node - 1] = arrival;
			step.received++;
			if (step.received == nodes)
			{
				final ArrayList<Object> ordered = new ArrayList<Object>();
				for (Object value : step.contributions)
				{
					ordered.add(value == NULL ? null : value);
				}
				double start = 0.0;
				for (double value : step.hubArrival)
				{
					start = Math.max(start, value);
				}
				final long[] excluded = new long[1];
				Runnable reduction = new Runnable()
				{
					public void run()
					{
						PayloadSize.takeExcludedCpu();
						try
						{
							step.result = reducer.reduce(ordered);
						}
						catch (RuntimeException failure)
						{
							step.failure = failure;
						}
						finally
						{
							excluded[0] = PayloadSize.takeExcludedCpu();
						}
					}
				};
				long cpu = emulator == null ? measure(reduction) : emulator.runUncharged(reduction);
				//Wire-size bookkeeping inside the reduction is not coordinator work.
				cpu = Math.max(0L, cpu - excluded[0]);
				step.reduceStart = start;
				step.reduceCpuNanos = cpu;
				double speed = context == null ? 1.0 : context.getSettings().getCoordinatorSpeed();
				step.reduceEnd = start + (context != null && context.isEmulating()
						? cpu / 1.0e6 / Math.max(1.0e-6, speed) : 0.0);
				final ArrayList<Long> sizes = new ArrayList<Long>();
				for (long size : step.uploadBytes)
				{
					sizes.add(Long.valueOf(size));
				}
				Runnable sizing = new Runnable()
				{
					public void run()
					{
						DownloadSizer safe = sizer == null ? WHOLE_RESULT : sizer;
						//The whole result is sized once, not once per node.
						long whole = safe == WHOLE_RESULT && step.failure == null
								? PayloadSize.of(step.result) : -1L;
						for (int index = 0; index < nodes; index++)
						{
							step.downloadBytes[index] = step.failure != null ? 0L
									: whole >= 0L ? whole
									: Math.max(0L, safe.bytesFor(index + 1, step.result, sizes, ordered));
						}
					}
				};
				if (emulator != null)
				{
					emulator.runUncharged(sizing);
				}
				else
				{
					sizing.run();
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
			if (step.failure != null)
			{
				consume(name, step);
				throw new IllegalStateException(
						"Collective step " + name + " failed: " + step.failure.getMessage(),
						step.failure);
			}
		}
		if (emulator != null)
		{
			double delivered = context.isEmulating()
					? context.network().send(NetworkModel.HUB, node,
							NetworkModel.MESSAGE_HEADER_BYTES + step.downloadBytes[node - 1], step.reduceEnd)
					: context.now();
			step.delivered[node - 1] = delivered;
			emulator.awaitModelTime(delivered, true);
		}
		synchronized (this)
		{
			consume(name, step);
		}
		return step.result;
	}

	/** Counts a consumer; the last one records the step. Caller holds the lock. */
	private void consume(String name, Step step)
	{
		step.consumers++;
		if (step.consumers >= nodes)
		{
			steps.remove(name);
			if (context != null && step.failure == null)
			{
				long up = 0L;
				long down = 0L;
				double first = Double.POSITIVE_INFINITY;
				double last = 0.0;
				for (int index = 0; index < nodes; index++)
				{
					up += step.uploadBytes[index];
					down += step.downloadBytes[index];
					first = Math.min(first, step.hubArrival[index]);
					last = Math.max(last, step.delivered[index]);
				}
				context.recorder().collective(new RunRecorder.Collective(name, nodes, up, down,
						first, step.reduceStart, step.reduceEnd, step.reduceCpuNanos, last));
			}
		}
	}

	private static long measure(Runnable work)
	{
		long before = NodeEmulator.threadCpuNanos();
		work.run();
		return Math.max(0L, NodeEmulator.threadCpuNanos() - before);
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
