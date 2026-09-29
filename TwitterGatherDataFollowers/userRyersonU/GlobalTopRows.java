package TwitterGatherDataFollowers.userRyersonU;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * The exact global top L of per-target candidate scores that are spread over
 * the nodes of a run.
 *
 * Every candidate is scored by exactly one node, and each node has cut its own
 * rows to its top L. The global top L of a target is contained in the union of
 * the nodes' top L, so after an all-gather every node computes the same global
 * top L, ranked by score descending and then name ascending (the organizer's
 * ranking order), and keeps the entries it contributed. The organizer's union
 * of the kept rows is then exactly the top L one node holding every candidate
 * would publish, whatever the partition and the schedule.
 */
final class GlobalTopRows
{
	/** Score descending, then candidate name ascending. */
	static final Comparator<Map.Entry<String,Double>> RANKING =
			new Comparator<Map.Entry<String,Double>>()
	{
		public int compare(Map.Entry<String,Double> first, Map.Entry<String,Double> second)
		{
			int byScore = Double.compare(second.getValue().doubleValue(), first.getValue().doubleValue());
			return byScore != 0 ? byScore : first.getKey().compareTo(second.getKey());
		}
	};

	private GlobalTopRows()
	{
	}

	/**
	 * Exchanges this node's rows and returns the part of the global top
	 * {@code limit} it contributed. Rows for targets this node holds are kept
	 * even when none of their entries survive. A non-positive limit keeps
	 * every entry (the exchange then only checks exclusivity).
	 */
	static TreeMap<String,TreeMap<String,Double>> keep(
			RunCollectives collectives,
			String step,
			int node,
			TreeMap<String,TreeMap<String,Double>> localRows,
			int limit)
	{
		List<TreeMap<String,TreeMap<String,Double>>> rowsByNode =
				collectives.allGather(step, node, localRows);
		return split(rowsByNode, limit).get(node - 1);
	}

	/** Pure form of {@link #keep}: each node's share of the global top rows. */
	static List<TreeMap<String,TreeMap<String,Double>>> split(
			List<? extends Map<String,? extends Map<String,Double>>> rowsByNode,
			int limit)
	{
		int nodes = rowsByNode.size();
		List<TreeMap<String,TreeMap<String,Double>>> kept =
				new ArrayList<TreeMap<String,TreeMap<String,Double>>>();
		TreeSet<String> targets = new TreeSet<String>();
		for (int index = 0; index < nodes; index++)
		{
			TreeMap<String,TreeMap<String,Double>> rows = new TreeMap<String,TreeMap<String,Double>>();
			Map<String,? extends Map<String,Double>> contributed = rowsByNode.get(index);
			if (contributed != null)
			{
				for (String target : contributed.keySet())
				{
					rows.put(target, new TreeMap<String,Double>());
					targets.add(target);
				}
			}
			kept.add(rows);
		}
		for (String target : targets)
		{
			ArrayList<Map.Entry<String,Double>> entries = new ArrayList<Map.Entry<String,Double>>();
			TreeMap<String,Integer> contributor = new TreeMap<String,Integer>();
			for (int index = 0; index < nodes; index++)
			{
				Map<String,? extends Map<String,Double>> contributed = rowsByNode.get(index);
				Map<String,Double> row = contributed == null ? null : contributed.get(target);
				if (row == null)
				{
					continue;
				}
				for (Map.Entry<String,Double> entry : row.entrySet())
				{
					Integer previous = contributor.put(entry.getKey(), Integer.valueOf(index));
					if (previous != null)
					{
						throw new IllegalStateException("Candidate " + entry.getKey()
								+ " of target " + target + " was scored by nodes "
								+ (previous.intValue() + 1) + " and " + (index + 1)
								+ "; each candidate must be scored exactly once.");
					}
					entries.add(new java.util.AbstractMap.SimpleImmutableEntry<String,Double>(
							entry.getKey(), entry.getValue()));
				}
			}
			Collections.sort(entries, RANKING);
			int count = limit <= 0 ? entries.size() : Math.min(limit, entries.size());
			for (int rank = 0; rank < count; rank++)
			{
				Map.Entry<String,Double> entry = entries.get(rank);
				int index = contributor.get(entry.getKey()).intValue();
				kept.get(index).get(target).put(entry.getKey(), entry.getValue());
			}
		}
		return kept;
	}

	/** This node's own top {@code limit} of one row, by {@link #RANKING}. */
	static TreeMap<String,Double> localTop(Map<String,Double> row, int limit)
	{
		TreeMap<String,Double> top = new TreeMap<String,Double>();
		if (row == null)
		{
			return top;
		}
		if (limit <= 0 || row.size() <= limit)
		{
			top.putAll(row);
			return top;
		}
		ArrayList<Map.Entry<String,Double>> entries = new ArrayList<Map.Entry<String,Double>>(row.entrySet());
		Collections.sort(entries, RANKING);
		for (int rank = 0; rank < limit; rank++)
		{
			top.put(entries.get(rank).getKey(), entries.get(rank).getValue());
		}
		return top;
	}
}
