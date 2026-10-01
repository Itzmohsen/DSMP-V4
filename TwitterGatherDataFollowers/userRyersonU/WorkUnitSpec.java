package TwitterGatherDataFollowers.userRyersonU;

import java.util.Arrays;
import java.util.Comparator;

/**
 * One schedulable unit of work: a candidate shard, a target job, or a model
 * training job.
 *
 * <ul>
 * <li>{@code cost}: estimated reference work, the milliseconds the unit takes
 *     on a speed-1 node; it runs for cost / speed on a node of that speed.</li>
 * <li>{@code fixedMs}: a part of the duration that does not scale with CPU
 *     speed (process start-up, disk), in the same time unit as the cost
 *     (milliseconds in live runs); 0 for pure computation.</li>
 * <li>{@code homeNode}: the node that holds the unit's input data (0 when the
 *     data is equally available everywhere). Running the unit elsewhere moves
 *     {@code inputBytes} from the home node over the network; while the home
 *     node is down its data cannot be read.</li>
 * <li>{@code outputBytes}: bytes the executing node sends to the coordinator
 *     when the unit completes.</li>
 * <li>{@code allowedNodes}: when non-null, the only nodes the unit may run on.</li>
 * </ul>
 */
final class WorkUnitSpec
{
	private final int id;
	private final String key;
	private final double cost;
	private final int memoryMb;
	private final int priority;
	private final double deadline;
	private final int homeNode;
	private final int[] allowedNodes;
	private final int maxFailures;
	private final long inputBytes;
	private final long outputBytes;
	private final double fixedMs;

	WorkUnitSpec(int id, String key, double cost)
	{
		this(id, key, cost, 0, 0, Double.POSITIVE_INFINITY, 0, null);
	}

	WorkUnitSpec(
			int id,
			String key,
			double cost,
			int memoryMb,
			int priority,
			double deadline,
			int homeNode,
			int[] allowedNodes)
	{
		this(id, key, cost, memoryMb, priority, deadline, homeNode, allowedNodes, 0);
	}

	/**
	 * @param maxFailures failures after which this unit fails permanently; 0 uses
	 *                    the dispatcher's default
	 */
	WorkUnitSpec(
			int id,
			String key,
			double cost,
			int memoryMb,
			int priority,
			double deadline,
			int homeNode,
			int[] allowedNodes,
			int maxFailures)
	{
		this(id, key, cost, memoryMb, priority, deadline, homeNode, allowedNodes,
				maxFailures, 0L, 0L, 0.0);
	}

	WorkUnitSpec(
			int id,
			String key,
			double cost,
			int memoryMb,
			int priority,
			double deadline,
			int homeNode,
			int[] allowedNodes,
			int maxFailures,
			long inputBytes,
			long outputBytes,
			double fixedMs)
	{
		if (id <= 0)
		{
			throw new IllegalArgumentException("Work unit ids start at 1.");
		}
		this.id = id;
		this.key = key == null ? String.format("unit-%08d", id) : key;
		this.cost = Double.isNaN(cost) || cost < 0.0 ? 0.0 : cost;
		this.memoryMb = Math.max(0, memoryMb);
		this.priority = priority;
		this.deadline = Double.isNaN(deadline) ? Double.POSITIVE_INFINITY : deadline;
		this.homeNode = Math.max(0, homeNode);
		if (allowedNodes == null || allowedNodes.length == 0)
		{
			this.allowedNodes = null;
		}
		else
		{
			int[] copy = Arrays.copyOf(allowedNodes, allowedNodes.length);
			Arrays.sort(copy);
			this.allowedNodes = copy;
		}
		this.maxFailures = Math.max(0, maxFailures);
		this.inputBytes = Math.max(0L, inputBytes);
		this.outputBytes = Math.max(0L, outputBytes);
		this.fixedMs = Double.isNaN(fixedMs) || fixedMs < 0.0 ? 0.0 : fixedMs;
	}

	/** The same unit with data volumes. */
	WorkUnitSpec withData(long input, long output)
	{
		return new WorkUnitSpec(id, key, cost, memoryMb, priority, deadline, homeNode,
				allowedNodes, maxFailures, input, output, fixedMs);
	}

	/** The same unit with another reference cost and speed-independent part. */
	WorkUnitSpec withCost(double newCost, double newFixedMs)
	{
		return new WorkUnitSpec(id, key, newCost, memoryMb, priority, deadline, homeNode,
				allowedNodes, maxFailures, inputBytes, outputBytes, newFixedMs);
	}

	/** The same unit restricted to {@code nodes} (null lifts the restriction). */
	WorkUnitSpec withAllowedNodes(int[] nodes)
	{
		return new WorkUnitSpec(id, key, cost, memoryMb, priority, deadline, homeNode,
				nodes, maxFailures, inputBytes, outputBytes, fixedMs);
	}

	/** The same unit renumbered onto another node index space. */
	WorkUnitSpec withNodes(int newHome, int[] newAllowed)
	{
		return new WorkUnitSpec(id, key, cost, memoryMb, priority, deadline, newHome,
				newAllowed, maxFailures, inputBytes, outputBytes, fixedMs);
	}

	/** Failure budget for this unit; 0 means the dispatcher default. */
	int getMaxFailures()
	{
		return maxFailures;
	}

	int getId()
	{
		return id;
	}

	String getKey()
	{
		return key;
	}

	double getCost()
	{
		return cost;
	}

	int getMemoryMb()
	{
		return memoryMb;
	}

	int getPriority()
	{
		return priority;
	}

	double getDeadline()
	{
		return deadline;
	}

	int getHomeNode()
	{
		return homeNode;
	}

	long getInputBytes()
	{
		return inputBytes;
	}

	long getOutputBytes()
	{
		return outputBytes;
	}

	double getFixedMs()
	{
		return fixedMs;
	}

	/** Duration on a node of {@code speed}: cost / speed + the fixed part. */
	double durationOn(double speed)
	{
		return cost / Math.max(1.0e-9, speed) + fixedMs;
	}

	boolean isAllowedOn(int node)
	{
		return allowedNodes == null || Arrays.binarySearch(allowedNodes, node) >= 0;
	}

	boolean isRestricted()
	{
		return allowedNodes != null;
	}

	int[] allowedNodesCopy()
	{
		return allowedNodes == null ? null : Arrays.copyOf(allowedNodes, allowedNodes.length);
	}

	/** Canonical order: by key, then id. Independent of submission order. */
	static final Comparator<WorkUnitSpec> CANONICAL = new Comparator<WorkUnitSpec>()
	{
		public int compare(WorkUnitSpec first, WorkUnitSpec second)
		{
			int byKey = first.key.compareTo(second.key);
			return byKey != 0 ? byKey : Integer.compare(first.id, second.id);
		}
	};

	/** Longest processing time first; ties canonical. */
	static final Comparator<WorkUnitSpec> LARGEST_FIRST = new Comparator<WorkUnitSpec>()
	{
		public int compare(WorkUnitSpec first, WorkUnitSpec second)
		{
			int byCost = Double.compare(second.cost + second.fixedMs, first.cost + first.fixedMs);
			return byCost != 0 ? byCost : CANONICAL.compare(first, second);
		}
	};

	/** Highest priority first, then earliest deadline; ties canonical. */
	static final Comparator<WorkUnitSpec> EARLIEST_DEADLINE = new Comparator<WorkUnitSpec>()
	{
		public int compare(WorkUnitSpec first, WorkUnitSpec second)
		{
			int byPriority = Integer.compare(second.priority, first.priority);
			if (byPriority != 0)
			{
				return byPriority;
			}
			int byDeadline = Double.compare(first.deadline, second.deadline);
			return byDeadline != 0 ? byDeadline : CANONICAL.compare(first, second);
		}
	};

	/** Largest memory first (first-fit decreasing), then largest cost. */
	static final Comparator<WorkUnitSpec> LARGEST_MEMORY_FIRST = new Comparator<WorkUnitSpec>()
	{
		public int compare(WorkUnitSpec first, WorkUnitSpec second)
		{
			int byMemory = Integer.compare(second.memoryMb, first.memoryMb);
			return byMemory != 0 ? byMemory : LARGEST_FIRST.compare(first, second);
		}
	};

	public String toString()
	{
		return key + "#" + id;
	}
}
