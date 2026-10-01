package TwitterGatherDataFollowers.userRyersonU;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Static placement of work units onto nodes, one precise rule per policy.
 *
 * Every rule is deterministic: units are taken in a fixed order and every tie
 * is broken by a total order (faster node, then lower node index), so the same
 * input always yields the same plan. With {@code useLanes} each node is
 * modelled as {@code lanes} parallel machines of its speed (queue phases);
 * without it each node is a single machine (the node's main thread, which
 * processes its own input data).
 *
 * <pre>
 * Policy                     order                 node choice
 * Original DSMP (V3)         canonical (key)       the unit's home node; else min raw assigned cost
 * Static equal split         canonical (key)       contiguous blocks of equal count
 * Round robin                canonical             unit k -> node k mod N
 * Weighted round robin       canonical             smooth weighted RR by throughput
 * Least loaded node          canonical             min raw assigned cost (Graham)
 * Fastest node first         canonical             earliest-available lane, ties to fastest
 * Capacity-weighted          largest cost first    earliest finish time (LPT-EFT)
 * Queue-length               canonical             fewest units per lane (JSQ)
 * Work stealing (initial)    largest cost first    earliest finish time
 * Deadline / priority        priority, deadline    earliest finish time
 * Memory-aware safe-wave     largest memory first  earliest finish among nodes that fit
 * Failure-aware              largest cost first    earliest expected finish (1/(1-p))
 * </pre>
 *
 * Earliest-finish rules include the data-transfer latency a unit pays when it
 * runs away from its home node, since that latency is part of its finish time.
 */
final class PlacementPlanner
{
	private PlacementPlanner()
	{
	}

	/** Result of a static placement. */
	static final class Plan
	{
		private final List<NodeSpec> nodes;
		private final List<List<WorkUnitSpec>> unitsByNode;
		private final double[] rawCost;
		private final double[] predictedFinish;
		private final int[] unitCount;
		private final List<String> warnings;
		private final java.util.HashMap<Integer,Integer> nodeByUnit =
				new java.util.HashMap<Integer,Integer>();

		private Plan(
				List<NodeSpec> nodes,
				List<List<WorkUnitSpec>> unitsByNode,
				double[] rawCost,
				double[] predictedFinish,
				int[] unitCount,
				List<String> warnings)
		{
			this.nodes = nodes;
			this.unitsByNode = unitsByNode;
			this.rawCost = rawCost;
			this.predictedFinish = predictedFinish;
			this.unitCount = unitCount;
			this.warnings = Collections.unmodifiableList(new ArrayList<String>(warnings));
			for (int node = 0; node < unitsByNode.size(); node++)
			{
				for (WorkUnitSpec unit : unitsByNode.get(node))
				{
					nodeByUnit.put(Integer.valueOf(unit.getId()), Integer.valueOf(node + 1));
				}
			}
		}

		int nodeCount()
		{
			return unitsByNode.size();
		}

		/** Units of node {@code node} (1-based) in local execution order. */
		List<WorkUnitSpec> unitsFor(int node)
		{
			return Collections.unmodifiableList(unitsByNode.get(node - 1));
		}

		/** 1-based node of every unit id, 0 for units not in the plan. */
		int nodeOfUnit(int unitId)
		{
			Integer node = nodeByUnit.get(Integer.valueOf(unitId));
			return node == null ? 0 : node.intValue();
		}

		double rawCost(int node)
		{
			return rawCost[node - 1];
		}

		double predictedFinish(int node)
		{
			return predictedFinish[node - 1];
		}

		double predictedMakespan()
		{
			double makespan = 0.0;
			for (double finish : predictedFinish)
			{
				makespan = Math.max(makespan, finish);
			}
			return makespan;
		}

		int unitCount(int node)
		{
			return unitCount[node - 1];
		}

		List<String> getWarnings()
		{
			return warnings;
		}

		List<NodeSpec> getNodes()
		{
			return nodes;
		}

		String summary()
		{
			StringBuilder builder = new StringBuilder();
			for (int node = 1; node <= nodeCount(); node++)
			{
				if (builder.length() > 0)
				{
					builder.append("; ");
				}
				builder.append(String.format(
						Locale.US,
						"node %d: %d unit%s, cost %.1f, predicted finish %.1f",
						node,
						unitCount(node),
						unitCount(node) == 1 ? "" : "s",
						rawCost(node),
						predictedFinish(node)));
			}
			return builder.toString();
		}
	}

	/**
	 * Plans {@code units} onto {@code nodes} with {@code policy}.
	 *
	 * @param baseLoad      raw cost already committed to each node before these
	 *                      units (index node - 1), e.g. replicated input; may be null
	 * @param useLanes      model each node as {@code lanes} parallel machines
	 * @param cumulativeMemory true when unit memory stays allocated for the whole
	 *                      phase (input data), false when it is released as each
	 *                      unit finishes (queue work); only the memory-aware
	 *                      policy enforces it, the others report violations
	 * @param msPerTimeUnit milliseconds represented by one unit of cost, used to
	 *                      express network latency in the same unit; 0 when the
	 *                      cost unit is not a time (latency is then ignored)
	 */
	static Plan plan(
			SchedulerLabEngine.SchedulingPolicy policy,
			List<WorkUnitSpec> units,
			List<NodeSpec> nodes,
			double[] baseLoad,
			boolean useLanes,
			boolean cumulativeMemory,
			double msPerTimeUnit)
	{
		if (nodes == null || nodes.isEmpty())
		{
			throw new IllegalArgumentException("Placement needs at least one node.");
		}
		SchedulerLabEngine.SchedulingPolicy safePolicy = policy == null
				? SchedulerLabEngine.SchedulingPolicy.CAPACITY_WEIGHTED : policy;
		State state = new State(nodes, baseLoad, useLanes, msPerTimeUnit);
		ArrayList<WorkUnitSpec> ordered = new ArrayList<WorkUnitSpec>();
		if (units != null)
		{
			for (WorkUnitSpec unit : units)
			{
				if (unit != null)
				{
					ordered.add(unit);
				}
			}
		}
		Collections.sort(ordered, orderFor(safePolicy));

		if (safePolicy == SchedulerLabEngine.SchedulingPolicy.STATIC_EQUAL_SPLIT)
		{
			planBlocks(state, ordered);
		}
		else if (safePolicy == SchedulerLabEngine.SchedulingPolicy.WEIGHTED_ROUND_ROBIN)
		{
			planSmoothWeighted(state, ordered);
		}
		else
		{
			int sequence = 0;
			for (WorkUnitSpec unit : ordered)
			{
				int node = chooseNode(safePolicy, state, unit, sequence, cumulativeMemory);
				state.assign(node, unit, cumulativeMemory);
				sequence++;
			}
		}
		state.reportMemory(cumulativeMemory);
		return state.toPlan();
	}

	/**
	 * The original DSMP rule: units in the given order, each to the node with
	 * the smallest raw assigned cost, ties to the lowest node index. Capacity
	 * is ignored entirely.
	 */
	static Plan planOriginalListScheduling(
			List<WorkUnitSpec> unitsInListOrder,
			int nodeCount,
			double[] baseLoad)
	{
		List<NodeSpec> nodes = NodeSpec.uniform(nodeCount);
		State state = new State(nodes, baseLoad, false, 0.0);
		if (unitsInListOrder != null)
		{
			for (WorkUnitSpec unit : unitsInListOrder)
			{
				if (unit == null)
				{
					continue;
				}
				int best = 1;
				for (int node = 2; node <= nodes.size(); node++)
				{
					if (state.rawCost[node - 1] < state.rawCost[best - 1])
					{
						best = node;
					}
				}
				state.assign(best, unit, false);
			}
		}
		return state.toPlan();
	}

	/** A lower bound on a phase's makespan, and the constraint that sets it. */
	static final class Bound
	{
		/** Model milliseconds from the phase's opening. */
		final double value;
		/** work, lanes, unit, pinned (see {@link PlacementPlanner#bound}). */
		final String kind;

		Bound(double value, String kind)
		{
			this.value = value;
			this.kind = kind;
		}

		/** The constraint in words, for reports. */
		String describe()
		{
			if ("work".equals(kind))
			{
				return "the total work on the nodes' combined speed";
			}
			if ("lanes".equals(kind))
			{
				return "the lane time every unit needs";
			}
			if ("unit".equals(kind))
			{
				return "one unit's earliest finish, its data and descriptor transfers included";
			}
			if ("pinned".equals(kind))
			{
				return "the work only one node may run";
			}
			return kind;
		}
	}

	/**
	 * A lower bound on the makespan of {@code units} on {@code nodes} without
	 * communication or availability: see {@link #bound}.
	 */
	static double lowerBound(List<WorkUnitSpec> units, List<NodeSpec> nodes)
	{
		return bound(units, nodes, null, false).value;
	}

	/**
	 * A lower bound on the makespan of {@code units} on {@code nodes}, from the
	 * phase's opening, that no policy can beat. Node n can start computing at
	 * a_n, the later of its availability (its join; null or NaN is 0, +inf
	 * means it took no part) and, with {@code network}, the arrival of a first
	 * unit descriptor over its link. The bound is the largest of:
	 * <ul>
	 * <li>work: the earliest T with sum_n s_n L_n (T - a_n)+ &gt;= total work
	 *     (no node computes faster than its speed on all its lanes);</li>
	 * <li>lanes: the earliest T with sum_n L_n (T - a_n)+ &gt;= total work /
	 *     fastest speed + total speed-independent time (each unit occupies a
	 *     lane at least that long);</li>
	 * <li>unit: the latest earliest finish of any unit on its best allowed
	 *     node: a_n, then (away from its home, with {@code network}) its input
	 *     travelling home -&gt; coordinator -&gt; node, then its duration;</li>
	 * <li>pinned: on each node, its start plus the lane time of the units only
	 *     it may run, spread over its lanes.</li>
	 * </ul>
	 * Contention, faults, crashes and controls that slow or remove nodes only
	 * lengthen a schedule, so they are left out; a caller whose nodes are sped
	 * up during the phase passes each node's fastest speed. The bound is
	 * therefore valid for every policy.
	 */
	static Bound bound(List<WorkUnitSpec> units, List<NodeSpec> nodes, double[] availableAt, boolean network)
	{
		if (units == null || units.isEmpty() || nodes == null || nodes.isEmpty())
		{
			return new Bound(0.0, "empty");
		}
		int count = nodes.size();
		double[] start = new double[count];
		double fastest = 0.0;
		boolean any = false;
		for (int index = 0; index < count; index++)
		{
			NodeSpec node = nodes.get(index);
			double available = availableAt == null || index >= availableAt.length || Double.isNaN(availableAt[index])
					? 0.0 : Math.max(0.0, availableAt[index]);
			start[index] = Math.max(available, network ? transfer(node, NetworkModel.UNIT_DESCRIPTOR_BYTES) : 0.0);
			if (!Double.isInfinite(start[index]))
			{
				any = true;
				fastest = Math.max(fastest, node.getSpeed());
			}
		}
		if (!any)
		{
			return new Bound(Double.NaN, "unavailable");
		}
		double work = 0.0;
		double fixed = 0.0;
		double unitBound = 0.0;
		double[] pinnedLaneTime = new double[count];
		for (WorkUnitSpec unit : units)
		{
			work += unit.getCost();
			fixed += unit.getFixedMs();
			double best = Double.POSITIVE_INFINITY;
			int allowed = 0;
			int only = -1;
			for (int index = 0; index < count; index++)
			{
				NodeSpec node = nodes.get(index);
				if (Double.isInfinite(start[index]) || !unit.isAllowedOn(node.getIndex()))
				{
					continue;
				}
				allowed++;
				only = index;
				double ready = start[index];
				int home = unit.getHomeNode();
				if (network && home > 0 && home != node.getIndex() && unit.getInputBytes() > 0L)
				{
					NodeSpec homeSpec = home <= count && nodes.get(home - 1).getIndex() == home
							? nodes.get(home - 1) : null;
					if (homeSpec != null)
					{
						ready = Math.max(ready, transfer(homeSpec, unit.getInputBytes())
								+ transfer(node, unit.getInputBytes()));
					}
				}
				best = Math.min(best, ready + unit.durationOn(node.getSpeed()));
			}
			if (allowed == 1)
			{
				pinnedLaneTime[only] += unit.durationOn(nodes.get(only).getSpeed());
			}
			if (best < Double.POSITIVE_INFINITY)
			{
				unitBound = Math.max(unitBound, best);
			}
		}
		double[] speedLanes = new double[count];
		double[] lanes = new double[count];
		double pinned = 0.0;
		for (int index = 0; index < count; index++)
		{
			speedLanes[index] = nodes.get(index).getSpeed() * nodes.get(index).getLanes();
			lanes[index] = nodes.get(index).getLanes();
			if (pinnedLaneTime[index] > 0.0)
			{
				pinned = Math.max(pinned, start[index] + pinnedLaneTime[index] / lanes[index]);
			}
		}
		double workBound = fluid(start, speedLanes, work);
		double laneBound = fluid(start, lanes, work / Math.max(1.0e-12, fastest) + fixed);
		Bound result = new Bound(workBound, "work");
		if (laneBound > result.value + 1e-12)
		{
			result = new Bound(laneBound, "lanes");
		}
		if (unitBound > result.value + 1e-12)
		{
			result = new Bound(unitBound, "unit");
		}
		if (pinned > result.value + 1e-12)
		{
			result = new Bound(pinned, "pinned");
		}
		return result;
	}

	/** Time to send {@code bytes} over a node's link, uncontended. */
	private static double transfer(NodeSpec node, long bytes)
	{
		return node.getDelayMs() + node.serializationMs(bytes);
	}

	/**
	 * The earliest T with sum_n rate_n (T - start_n)+ &gt;= amount (rates of
	 * nodes that never start are ignored).
	 */
	private static double fluid(double[] start, double[] rate, double amount)
	{
		if (amount <= 0.0)
		{
			return 0.0;
		}
		Integer[] order = new Integer[start.length];
		for (int index = 0; index < order.length; index++)
		{
			order[index] = Integer.valueOf(index);
		}
		final double[] starts = start;
		java.util.Arrays.sort(order, new java.util.Comparator<Integer>()
		{
			public int compare(Integer first, Integer second)
			{
				return Double.compare(starts[first.intValue()], starts[second.intValue()]);
			}
		});
		double done = 0.0;
		double totalRate = 0.0;
		double time = 0.0;
		for (int position = 0; position < order.length; position++)
		{
			int index = order[position].intValue();
			if (Double.isInfinite(start[index]))
			{
				break;
			}
			if (totalRate > 0.0)
			{
				double reach = time + (amount - done) / totalRate;
				if (reach <= start[index])
				{
					return reach;
				}
				done += totalRate * (start[index] - time);
			}
			time = start[index];
			totalRate += rate[index];
		}
		return totalRate <= 0.0 ? Double.POSITIVE_INFINITY : time + (amount - done) / totalRate;
	}

	static java.util.Comparator<WorkUnitSpec> orderFor(
			SchedulerLabEngine.SchedulingPolicy policy)
	{
		switch (policy)
		{
			case CAPACITY_WEIGHTED:
			case WORK_STEALING:
			case FAILURE_AWARE_RESCHEDULING:
				return WorkUnitSpec.LARGEST_FIRST;
			case DEADLINE_PRIORITY:
				return WorkUnitSpec.EARLIEST_DEADLINE;
			case MEMORY_AWARE_SAFE_WAVE:
				return WorkUnitSpec.LARGEST_MEMORY_FIRST;
			default:
				return WorkUnitSpec.CANONICAL;
		}
	}

	private static int chooseNode(
			SchedulerLabEngine.SchedulingPolicy policy,
			State state,
			WorkUnitSpec unit,
			int sequence,
			boolean cumulativeMemory)
	{
		switch (policy)
		{
			case ORIGINAL_DSMP:
				return state.homeOrLeastRawLoad(unit);
			case ROUND_ROBIN:
				return state.firstAllowedFrom(unit, (sequence % state.nodeCount()) + 1);
			case LEAST_LOADED_NODE:
				return state.leastRawLoad(unit);
			case FASTEST_NODE_FIRST:
				return state.earliestAvailable(unit);
			case QUEUE_LENGTH:
				return state.shortestQueue(unit);
			case MEMORY_AWARE_SAFE_WAVE:
				return state.earliestFinish(unit, false, true, cumulativeMemory);
			case FAILURE_AWARE_RESCHEDULING:
				return state.earliestFinish(unit, true, false, cumulativeMemory);
			case CAPACITY_WEIGHTED:
			case WORK_STEALING:
			case DEADLINE_PRIORITY:
			default:
				return state.earliestFinish(unit, false, false, cumulativeMemory);
		}
	}

	/** Contiguous blocks whose sizes differ by at most one. */
	private static void planBlocks(State state, List<WorkUnitSpec> ordered)
	{
		int nodes = state.nodeCount();
		int total = ordered.size();
		for (int position = 0; position < total; position++)
		{
			int node = (int)(((long)position * nodes) / Math.max(1, total)) + 1;
			WorkUnitSpec unit = ordered.get(position);
			state.assign(state.firstAllowedFrom(unit, node), unit, false);
		}
	}

	/**
	 * Smooth weighted round robin (Nginx): every step adds each node's weight
	 * to its credit, picks the highest credit (ties to the lower index) and
	 * charges it the total weight. Over any prefix the counts stay within one
	 * unit of the weight-proportional share.
	 */
	private static void planSmoothWeighted(State state, List<WorkUnitSpec> ordered)
	{
		int nodes = state.nodeCount();
		double[] credit = new double[nodes];
		double totalWeight = 0.0;
		for (int node = 1; node <= nodes; node++)
		{
			totalWeight += state.weight(node);
		}
		for (WorkUnitSpec unit : ordered)
		{
			int best = 0;
			for (int node = 1; node <= nodes; node++)
			{
				credit[node - 1] += state.weight(node);
				if (!unit.isAllowedOn(node))
				{
					continue;
				}
				if (best == 0 || credit[node - 1] > credit[best - 1])
				{
					best = node;
				}
			}
			if (best == 0)
			{
				best = state.firstAllowedFrom(unit, 1);
			}
			credit[best - 1] -= totalWeight;
			state.assign(best, unit, false);
		}
	}

	/** Mutable planning state: per-node lanes, costs and memory. */
	private static final class State
	{
		private final List<NodeSpec> nodes;
		private final boolean useLanes;
		private final double msPerTimeUnit;
		private final double[][] laneAvailable;
		private final double[] rawCost;
		private final int[] unitCount;
		private final long[] memoryUsed;
		private final List<List<WorkUnitSpec>> unitsByNode;
		private final ArrayList<String> warnings = new ArrayList<String>();

		State(List<NodeSpec> nodes, double[] baseLoad, boolean useLanes, double msPerTimeUnit)
		{
			this.nodes = nodes;
			this.useLanes = useLanes;
			this.msPerTimeUnit = Double.isNaN(msPerTimeUnit) ? 0.0 : Math.max(0.0, msPerTimeUnit);
			int count = nodes.size();
			laneAvailable = new double[count][];
			rawCost = new double[count];
			unitCount = new int[count];
			memoryUsed = new long[count];
			unitsByNode = new ArrayList<List<WorkUnitSpec>>();
			for (int node = 0; node < count; node++)
			{
				NodeSpec spec = nodes.get(node);
				int lanes = useLanes ? spec.getLanes() : 1;
				laneAvailable[node] = new double[lanes];
				double base = baseLoad == null || node >= baseLoad.length
						? 0.0 : Math.max(0.0, baseLoad[node]);
				rawCost[node] = base;
				//Base load occupies the node's lanes evenly before these units.
				double perLane = base / lanes / spec.getSpeed();
				Arrays.fill(laneAvailable[node], perLane);
				unitsByNode.add(new ArrayList<WorkUnitSpec>());
			}
		}

		int nodeCount()
		{
			return nodes.size();
		}

		NodeSpec spec(int node)
		{
			return nodes.get(node - 1);
		}

		double weight(int node)
		{
			NodeSpec spec = spec(node);
			return useLanes ? spec.getThroughput() : spec.getSpeed();
		}

		void assign(int node, WorkUnitSpec unit, boolean cumulativeMemory)
		{
			int slot = node - 1;
			NodeSpec spec = spec(node);
			int lane = earliestLane(slot);
			double start = laneAvailable[slot][lane] + transferDelay(unit, node);
			laneAvailable[slot][lane] = start + unit.durationOn(spec.getSpeed());
			rawCost[slot] += unit.getCost();
			unitCount[slot]++;
			if (cumulativeMemory)
			{
				memoryUsed[slot] += unit.getMemoryMb();
			}
			unitsByNode.get(slot).add(unit);
		}

		int earliestLane(int slot)
		{
			int best = 0;
			for (int lane = 1; lane < laneAvailable[slot].length; lane++)
			{
				if (laneAvailable[slot][lane] < laneAvailable[slot][best])
				{
					best = lane;
				}
			}
			return best;
		}

		/**
		 * Time, in cost units, to move a unit's input data from its home node to
		 * {@code node}: both links' latency and serialization (alpha-beta model).
		 */
		double transferDelay(WorkUnitSpec unit, int node)
		{
			int home = unit.getHomeNode();
			if (msPerTimeUnit <= 0.0 || home <= 0 || home == node || home > nodes.size())
			{
				return 0.0;
			}
			long bytes = unit.getInputBytes();
			double milliseconds = spec(home).getDelayMs() + spec(home).serializationMs(bytes)
					+ spec(node).getDelayMs() + spec(node).serializationMs(bytes);
			return milliseconds / msPerTimeUnit;
		}

		int firstAllowedFrom(WorkUnitSpec unit, int start)
		{
			int count = nodeCount();
			for (int offset = 0; offset < count; offset++)
			{
				int node = ((start - 1 + offset) % count) + 1;
				if (unit.isAllowedOn(node))
				{
					return node;
				}
			}
			throw new IllegalArgumentException(
					"Unit " + unit + " is not allowed on any node.");
		}

		int leastRawLoad(WorkUnitSpec unit)
		{
			int best = 0;
			for (int node = 1; node <= nodeCount(); node++)
			{
				if (!unit.isAllowedOn(node))
				{
					continue;
				}
				if (best == 0 || rawCost[node - 1] < rawCost[best - 1])
				{
					best = node;
				}
			}
			return best == 0 ? firstAllowedFrom(unit, 1) : best;
		}

		/** The unit's home node when it may run there, else the least raw load (V3). */
		int homeOrLeastRawLoad(WorkUnitSpec unit)
		{
			int home = unit.getHomeNode();
			if (home >= 1 && home <= nodeCount() && unit.isAllowedOn(home))
			{
				return home;
			}
			return leastRawLoad(unit);
		}

		/** Node whose earliest lane frees up first; ties to the faster node. */
		int earliestAvailable(WorkUnitSpec unit)
		{
			int best = 0;
			double bestTime = Double.POSITIVE_INFINITY;
			for (int node = 1; node <= nodeCount(); node++)
			{
				if (!unit.isAllowedOn(node))
				{
					continue;
				}
				double available = laneAvailable[node - 1][earliestLane(node - 1)];
				if (best == 0 || available < bestTime
						|| (available == bestTime
								&& spec(node).getSpeed() > spec(best).getSpeed()))
				{
					best = node;
					bestTime = available;
				}
			}
			return best == 0 ? firstAllowedFrom(unit, 1) : best;
		}

		/** Fewest assigned units per lane (join the shortest queue). */
		int shortestQueue(WorkUnitSpec unit)
		{
			int best = 0;
			double bestLength = Double.POSITIVE_INFINITY;
			for (int node = 1; node <= nodeCount(); node++)
			{
				if (!unit.isAllowedOn(node))
				{
					continue;
				}
				int lanes = useLanes ? spec(node).getLanes() : 1;
				double length = unitCount[node - 1] / (double)lanes;
				if (best == 0 || length < bestLength
						|| (length == bestLength
								&& spec(node).getSpeed() > spec(best).getSpeed()))
				{
					best = node;
					bestLength = length;
				}
			}
			return best == 0 ? firstAllowedFrom(unit, 1) : best;
		}

		/**
		 * Earliest finish time. With {@code reliability} the execution time is
		 * the expected time under retries, t / (1 - p). With {@code memoryFit}
		 * only nodes whose budget still fits the unit are considered.
		 */
		int earliestFinish(
				WorkUnitSpec unit,
				boolean reliability,
				boolean memoryFit,
				boolean cumulativeMemory)
		{
			int best = 0;
			double bestFinish = Double.POSITIVE_INFINITY;
			for (int node = 1; node <= nodeCount(); node++)
			{
				if (!unit.isAllowedOn(node))
				{
					continue;
				}
				NodeSpec spec = spec(node);
				if (memoryFit && !fits(node, unit, cumulativeMemory))
				{
					continue;
				}
				int lane = earliestLane(node - 1);
				double duration = unit.durationOn(spec.getSpeed());
				if (reliability)
				{
					duration = duration / Math.max(1.0e-6, 1.0 - spec.getFailureProbability());
				}
				double finish = laneAvailable[node - 1][lane]
						+ transferDelay(unit, node) + duration;
				if (best == 0 || finish < bestFinish
						|| (finish == bestFinish && spec.getSpeed() > spec(best).getSpeed()))
				{
					best = node;
					bestFinish = finish;
				}
			}
			if (best == 0)
			{
				if (memoryFit)
				{
					throw new IllegalArgumentException(
							"No node has room for " + unit + " (" + unit.getMemoryMb()
							+ " MB); add memory or nodes, or choose another policy.");
				}
				return firstAllowedFrom(unit, 1);
			}
			return best;
		}

		boolean fits(int node, WorkUnitSpec unit, boolean cumulativeMemory)
		{
			long used = cumulativeMemory ? memoryUsed[node - 1] : 0L;
			return used + unit.getMemoryMb() <= spec(node).getMemoryMb();
		}

		void reportMemory(boolean cumulativeMemory)
		{
			if (!cumulativeMemory)
			{
				return;
			}
			for (int node = 1; node <= nodeCount(); node++)
			{
				if (memoryUsed[node - 1] > spec(node).getMemoryMb())
				{
					warnings.add(String.format(
							Locale.US,
							"Node %d is planned %d MB of retained data but its budget is %d MB.",
							node,
							memoryUsed[node - 1],
							spec(node).getMemoryMb()));
				}
			}
		}

		Plan toPlan()
		{
			double[] finish = new double[nodeCount()];
			for (int node = 0; node < nodeCount(); node++)
			{
				double last = 0.0;
				for (double available : laneAvailable[node])
				{
					last = Math.max(last, available);
				}
				finish[node] = last;
			}
			ArrayList<List<WorkUnitSpec>> copy = new ArrayList<List<WorkUnitSpec>>();
			for (List<WorkUnitSpec> nodeUnits : unitsByNode)
			{
				copy.add(new ArrayList<WorkUnitSpec>(nodeUnits));
			}
			return new Plan(
					nodes,
					copy,
					Arrays.copyOf(rawCost, rawCost.length),
					finish,
					Arrays.copyOf(unitCount, unitCount.length),
					warnings);
		}
	}
}
