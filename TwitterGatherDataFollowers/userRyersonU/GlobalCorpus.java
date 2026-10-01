package TwitterGatherDataFollowers.userRyersonU;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * The corpus of a whole run, for the models that must see every user: the
 * exact classifiers and the temporal graph model.
 *
 * Every node contributes the TF-IDF vectors (and followee labels) of the users
 * it owns; a collective combines them once, so each user appears exactly once,
 * and every node receives the same object. Users are kept in name order, which
 * fixes every order-dependent step downstream (ARFF rows, vocabulary, class
 * lists) independently of how users were spread over the nodes. The vectors
 * are the owners' own objects and must be treated as read-only.
 */
final class GlobalCorpus
{
	/** One node's share: the users it owns. */
	static final class Contribution implements PayloadSize.Sized
	{
		private final Map<String,? extends Map<String,Double>> vectors;
		private final Map<String,String> labels;

		Contribution(Map<String,? extends Map<String,Double>> vectors, Map<String,String> labels)
		{
			this.vectors = vectors == null
					? Collections.<String,Map<String,Double>>emptyMap() : vectors;
			this.labels = labels == null ? Collections.<String,String>emptyMap() : labels;
		}

		/** Wire size: every owned user's name, vector and followee label. */
		public long payloadBytes()
		{
			long total = 8L;
			for (Map.Entry<String,? extends Map<String,Double>> entry : vectors.entrySet())
			{
				total += PayloadSize.string(entry.getKey()) + PayloadSize.sparseVector(entry.getValue());
			}
			for (Map.Entry<String,String> entry : labels.entrySet())
			{
				total += PayloadSize.string(entry.getKey()) + PayloadSize.string(entry.getValue());
			}
			return total;
		}
	}

	private final List<String> users;
	private final LinkedHashMap<String,Map<String,Double>> vectors;
	private final Map<String,String> labels;
	private final Map<String,Integer> owners;
	private List<String> terms;
	private Map<String,Integer> termIndex;
	private List<String> classes;

	private GlobalCorpus(
			List<String> users,
			LinkedHashMap<String,Map<String,Double>> vectors,
			Map<String,String> labels,
			Map<String,Integer> owners)
	{
		this.users = Collections.unmodifiableList(users);
		this.vectors = vectors;
		this.labels = labels;
		this.owners = owners;
	}

	/**
	 * Exchanges contributions; every node gets the same combined corpus. On the
	 * wire each node uploads its share and downloads everyone else's.
	 */
	static GlobalCorpus gather(RunCollectives collectives, String step, int node, Contribution own)
	{
		return collectives.allReduce(step, node, own, -1L, new RunCollectives.Reducer<GlobalCorpus>()
		{
			public GlobalCorpus reduce(List<Object> contributionsByNode)
			{
				return combine(contributionsByNode);
			}
		}, RunCollectives.OTHERS_CONTRIBUTIONS);
	}

	/** Combines node contributions (index = node - 1); ownership must be exclusive. */
	static GlobalCorpus combine(List<?> contributionsByNode)
	{
		TreeMap<String,Map<String,Double>> sorted = new TreeMap<String,Map<String,Double>>();
		Map<String,String> labels = new LinkedHashMap<String,String>();
		Map<String,Integer> owners = new LinkedHashMap<String,Integer>();
		for (int index = 0; index < contributionsByNode.size(); index++)
		{
			Contribution contribution = (Contribution)contributionsByNode.get(index);
			if (contribution == null)
			{
				continue;
			}
			for (Map.Entry<String,? extends Map<String,Double>> entry : contribution.vectors.entrySet())
			{
				String user = entry.getKey();
				if (user == null || entry.getValue() == null)
				{
					continue;
				}
				Integer previous = owners.put(user, Integer.valueOf(index + 1));
				if (previous != null)
				{
					throw new IllegalStateException("User " + user + " is owned by nodes "
							+ previous + " and " + (index + 1) + "; ownership must be exclusive.");
				}
				sorted.put(user, entry.getValue());
				String label = contribution.labels.get(user);
				if (label != null && label.length() > 0)
				{
					labels.put(user, label);
				}
			}
		}
		LinkedHashMap<String,Map<String,Double>> vectors =
				new LinkedHashMap<String,Map<String,Double>>(sorted);
		return new GlobalCorpus(new ArrayList<String>(sorted.keySet()), vectors, labels, owners);
	}

	/** Every user, in name order. */
	List<String> users()
	{
		return users;
	}

	/** Users and vectors in name order (read-only). */
	Map<String,Map<String,Double>> vectors()
	{
		return Collections.unmodifiableMap(vectors);
	}

	int size()
	{
		return users.size();
	}

	boolean contains(String user)
	{
		return vectors.containsKey(user);
	}

	Map<String,Double> vectorOf(String user)
	{
		return vectors.get(user);
	}

	/** The user's followee, or null when it has none. */
	String labelOf(String user)
	{
		return labels.get(user);
	}

	Map<String,String> labels()
	{
		return Collections.unmodifiableMap(labels);
	}

	int ownerOf(String user)
	{
		Integer owner = owners.get(user);
		return owner == null ? 0 : owner.intValue();
	}

	/** Every term of every vector, sorted: the classifiers' attribute order. */
	synchronized List<String> terms()
	{
		if (terms == null)
		{
			TreeSet<String> all = new TreeSet<String>();
			for (Map<String,Double> vector : vectors.values())
			{
				all.addAll(vector.keySet());
			}
			terms = Collections.unmodifiableList(new ArrayList<String>(all));
		}
		return terms;
	}

	/** Attribute index of each term in {@link #terms()}. */
	synchronized Map<String,Integer> termIndex()
	{
		if (termIndex == null)
		{
			Map<String,Integer> index = new java.util.HashMap<String,Integer>();
			List<String> ordered = terms();
			for (int position = 0; position < ordered.size(); position++)
			{
				index.put(ordered.get(position), Integer.valueOf(position));
			}
			termIndex = Collections.unmodifiableMap(index);
		}
		return termIndex;
	}

	/** Distinct followee labels, sorted: the classifiers' class order. */
	synchronized List<String> classes()
	{
		if (classes == null)
		{
			classes = Collections.unmodifiableList(new ArrayList<String>(new TreeSet<String>(labels.values())));
		}
		return classes;
	}

	/** Non-zero feature entries over all users, for cost and memory estimates. */
	long nonZeroCount()
	{
		long count = 0L;
		for (Map<String,Double> vector : vectors.values())
		{
			count += vector.size();
		}
		return count;
	}

	/**
	 * Labelled users of each class, classes and users in name order, leaving
	 * out {@code excluded} (the users whose labels must not be learned from).
	 */
	LinkedHashMap<String,List<String>> usersByClass(Collection<String> excluded)
	{
		Set<String> skip = excluded == null ? Collections.<String>emptySet() : new HashSet<String>(excluded);
		TreeMap<String,List<String>> byClass = new TreeMap<String,List<String>>();
		for (String user : users)
		{
			String label = labels.get(user);
			if (label == null || skip.contains(user))
			{
				continue;
			}
			List<String> members = byClass.get(label);
			if (members == null)
			{
				members = new ArrayList<String>();
				byClass.put(label, members);
			}
			members.add(user);
		}
		return new LinkedHashMap<String,List<String>>(byClass);
	}
}
