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
			for (int node = 0; node < unitsByNode.size(); node++)
			{
				for (WorkUnitSpec unit : unitsByNode.get(node))
				{
					if (unit.getId() == unitId)
					{
						return node + 1;
					}
				}
			}
			return 0;
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
			laneAvailable[slot][lane] = start + unit.getCost() / spec.getSpeed();
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

		/** Latency, in cost units, of moving a unit's data away from its home. */
		double transferDelay(WorkUnitSpec unit, int node)
		{
			int home = unit.getHomeNode();
			if (msPerTimeUnit <= 0.0 || home <= 0 || home == node || home > nodes.size())
			{
				return 0.0;
			}
			return (spec(home).getDelayMs() + spec(node).getDelayMs()) / msPerTimeUnit;
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
				double duration = unit.getCost() / spec.getSpeed();
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
