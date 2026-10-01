package TwitterGatherDataFollowers.userRyersonU;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

/**
 * Which users a run recommends for (the evaluation's queries).
 *
 * <ul>
 *   <li>{@link Mode#SELECTED}: the users picked in the list, in list order;</li>
 *   <li>{@link Mode#SAMPLE}: a stratified random sample of n users: every
 *       followee community gets a share proportional to its size (largest
 *       remainders), members are drawn by a seeded shuffle, so the same seed
 *       and dataset always give the same targets;</li>
 *   <li>{@link Mode#ALL}: every user of the run.</li>
 * </ul>
 * The result is sorted by name (except for {@link Mode#SELECTED}), so it
 * depends only on the users, their labels, n and the seed.
 */
final class TargetSelection
{
	enum Mode
	{
		SELECTED("Selected users"),
		SAMPLE("Stratified sample"),
		ALL("All users");

		private final String label;

		Mode(String label)
		{
			this.label = label;
		}

		public String toString()
		{
			return label;
		}
	}

	static final long DEFAULT_SEED = 20261001L;

	private TargetSelection()
	{
	}

	static List<String> select(Mode mode, Collection<String> selected, Collection<String> users,
			Map<String,String> labels, int sampleSize, long seed)
	{
		if (mode == Mode.ALL)
		{
			return sortedDistinct(users);
		}
		if (mode == Mode.SAMPLE)
		{
			return sample(users, labels, sampleSize, seed);
		}
		ArrayList<String> chosen = new ArrayList<String>();
		if (selected != null)
		{
			for (String user : new LinkedHashSet<String>(selected))
			{
				if (user != null && (users == null || users.contains(user)))
				{
					chosen.add(user);
				}
			}
		}
		return chosen;
	}

	/** A stratified sample of {@code size} users (all of them when size reaches the population). */
	static List<String> sample(Collection<String> users, Map<String,String> labels, int size, long seed)
	{
		List<String> population = sortedDistinct(users);
		if (size <= 0 || population.isEmpty())
		{
			return new ArrayList<String>();
		}
		if (size >= population.size())
		{
			return population;
		}
		//Strata: followee communities in name order; unlabelled users form one more.
		TreeMap<String,List<String>> strata = new TreeMap<String,List<String>>();
		for (String user : population)
		{
			String label = labels == null ? null : labels.get(user);
			String key = label == null || label.length() == 0 ? "￿" : label;
			List<String> members = strata.get(key);
			if (members == null)
			{
				members = new ArrayList<String>();
				strata.put(key, members);
			}
			members.add(user);
		}
		//Proportional allocation with largest remainders (ties: larger stratum, then name).
		int total = population.size();
		ArrayList<String> keys = new ArrayList<String>(strata.keySet());
		final int[] quota = new int[keys.size()];
		final double[] remainder = new double[keys.size()];
		int allocated = 0;
		for (int index = 0; index < keys.size(); index++)
		{
			double exact = size * (strata.get(keys.get(index)).size() / (double)total);
			quota[index] = (int)Math.floor(exact);
			remainder[index] = exact - quota[index];
			allocated += quota[index];
		}
		ArrayList<Integer> order = new ArrayList<Integer>();
		for (int index = 0; index < keys.size(); index++)
		{
			order.add(Integer.valueOf(index));
		}
		final List<String> keyList = keys;
		final TreeMap<String,List<String>> strataView = strata;
		Collections.sort(order, new java.util.Comparator<Integer>()
		{
			public int compare(Integer first, Integer second)
			{
				int byRemainder = Double.compare(remainder[second.intValue()], remainder[first.intValue()]);
				if (byRemainder != 0)
				{
					return byRemainder;
				}
				int bySize = Integer.compare(strataView.get(keyList.get(second.intValue())).size(),
						strataView.get(keyList.get(first.intValue())).size());
				return bySize != 0 ? bySize : keyList.get(first.intValue()).compareTo(keyList.get(second.intValue()));
			}
		});
		for (int position = 0; allocated < size && position < order.size(); position++)
		{
			int index = order.get(position).intValue();
			if (quota[index] < strata.get(keys.get(index)).size())
			{
				quota[index]++;
				allocated++;
			}
		}
		ArrayList<String> chosen = new ArrayList<String>();
		for (int index = 0; index < keys.size(); index++)
		{
			List<String> members = new ArrayList<String>(strata.get(keys.get(index)));
			Collections.shuffle(members, new Random(seed ^ SimulationSeeds.forName(keys.get(index))));
			chosen.addAll(members.subList(0, Math.min(quota[index], members.size())));
		}
		Collections.sort(chosen);
		return chosen;
	}

	private static List<String> sortedDistinct(Collection<String> users)
	{
		ArrayList<String> sorted = new ArrayList<String>();
		if (users != null)
		{
			java.util.TreeSet<String> distinct = new java.util.TreeSet<String>();
			for (String user : users)
			{
				if (user != null)
				{
					distinct.add(user);
				}
			}
			sorted.addAll(distinct);
		}
		return sorted;
	}
}
