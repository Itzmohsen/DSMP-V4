package TwitterGatherDataFollowers.userRyersonU;

import java.util.Arrays;
import java.util.Comparator;

/**
 * One schedulable unit of work: an input user, a candidate shard, a target
 * job, or a model-training job.
 *
 * {@code cost} is estimated reference work (the time the unit would take on a
 * speed-1.0 node, in the caller's time unit). {@code homeNode} names the node
 * that already holds the unit's input data (0 when the data is equally
 * available everywhere); running it elsewhere costs a transfer. A non-null
 * {@code allowedNodes} restricts the unit to those nodes, for work that cannot
 * move at all.
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

	boolean isAllowedOn(int node)
	{
		return allowedNodes == null || Arrays.binarySearch(allowedNodes, node) >= 0;
	}

	boolean isRestricted()
	{
		return allowedNodes != null;
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
			int byCost = Double.compare(second.cost, first.cost);
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
