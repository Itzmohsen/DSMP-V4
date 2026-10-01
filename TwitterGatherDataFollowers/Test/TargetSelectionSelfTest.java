package TwitterGatherDataFollowers.userRyersonU;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Target selection: the stratified sample keeps every community's share,
 * depends only on the users, labels, size and seed, and the other modes
 * return exactly what they promise.
 */
public final class TargetSelectionSelfTest
{
	private TargetSelectionSelfTest()
	{
	}

	public static void main(String[] args)
	{
		Map<String,String> labels = new LinkedHashMap<String,String>();
		List<String> users = new ArrayList<String>();
		//Communities of 50, 30 and 20 users, plus 10 without a followee.
		addCommunity(users, labels, "A", 50);
		addCommunity(users, labels, "B", 30);
		addCommunity(users, labels, "C", 20);
		for (int index = 0; index < 10; index++)
		{
			users.add("none-" + index);
		}

		List<String> sample = TargetSelection.sample(users, labels, 22, 7L);
		require(sample.size() == 22, "sample size " + sample.size());
		Map<String,Integer> counts = count(sample, labels);
		//Exact shares: 10, 6, 4 and 2 of 22 (50/110, 30/110, 20/110, 10/110).
		require(counts.get("A").intValue() == 10 && counts.get("B").intValue() == 6
				&& counts.get("C").intValue() == 4 && counts.get("").intValue() == 2,
				"proportional allocation: " + counts);
		require(sample.equals(TargetSelection.sample(shuffled(users), labels, 22, 7L)),
				"the sample depends on the input order");
		require(!sample.equals(TargetSelection.sample(users, labels, 22, 8L)), "the seed has no effect");
		List<String> sorted = new ArrayList<String>(sample);
		Collections.sort(sorted);
		require(sample.equals(sorted), "the sample is sorted by name");

		//Largest remainders: 3 of [A 50, B 30, C 20, none 10] -> 1.36, 0.82, 0.55, 0.27.
		Map<String,Integer> three = count(TargetSelection.sample(users, labels, 3, 1L), labels);
		require(three.get("A").intValue() == 1 && three.get("B").intValue() == 1
				&& three.get("C").intValue() == 1 && !three.containsKey(""), "largest remainders: " + three);

		require(TargetSelection.sample(users, labels, 500, 1L).size() == users.size(), "a sample is capped");
		require(TargetSelection.select(TargetSelection.Mode.ALL, null, users, labels, 0, 0L).size()
				== users.size(), "all users");
		List<String> chosen = TargetSelection.select(TargetSelection.Mode.SELECTED,
				Arrays.asList("B-3", "A-1", "B-3", "ghost"), users, labels, 0, 0L);
		require(chosen.equals(Arrays.asList("B-3", "A-1")),
				"selected users keep their order, without duplicates or unknown names: " + chosen);
		System.out.println("TargetSelectionSelfTest passed");
	}

	private static void addCommunity(List<String> users, Map<String,String> labels, String followee, int size)
	{
		for (int index = 0; index < size; index++)
		{
			String user = followee + "-" + index;
			users.add(user);
			labels.put(user, followee);
		}
	}

	private static Map<String,Integer> count(List<String> sample, Map<String,String> labels)
	{
		Map<String,Integer> counts = new HashMap<String,Integer>();
		for (String user : sample)
		{
			String label = labels.containsKey(user) ? labels.get(user) : "";
			Integer old = counts.get(label);
			counts.put(label, Integer.valueOf(old == null ? 1 : old.intValue() + 1));
		}
		return counts;
	}

	private static List<String> shuffled(List<String> users)
	{
		List<String> copy = new ArrayList<String>(users);
		Collections.shuffle(copy, new java.util.Random(99L));
		return copy;
	}

	private static void require(boolean condition, String message)
	{
		if (!condition)
		{
			throw new AssertionError(message);
		}
	}
}
