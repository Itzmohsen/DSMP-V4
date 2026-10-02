package TwitterGatherDataFollowers.userRyersonU;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * The social network of one recommendation run: one node set, three layers.
 *
 * <pre>
 * FOLLOW     user to the account it follows        observed, the dataset's ground truth
 * RECOMMEND  target to each of its top-k candidates what the recommender proposed
 * ADOPT      target to the candidate it follows     the follow simulation's choice
 * </pre>
 *
 * Nodes are the run's users and the accounts they follow; a handle that is
 * both a user and a followed account is one node. Every user belongs to the
 * community of the account it follows, and an account to its own: these
 * ground-truth communities are what the recommender is judged against.
 * Recommendation edges carry their rank and score; the adoption is the one
 * {@link BatchRecommendationSelector#adoptedIndex} picks, the same choice the
 * user agents make. Nodes are ordered by name, so every analysis of the graph
 * depends on the run's content only, never on the order agents reported in.
 */
final class RunGraph
{
	enum Kind
	{
		USER("User"),
		ACCOUNT("Account"),
		BOTH("User and account");

		final String label;

		Kind(String label)
		{
			this.label = label;
		}
	}

	enum Layer
	{
		FOLLOW("Follows"),
		RECOMMEND("Recommendations"),
		ADOPT("Adoptions");

		final String label;

		Layer(String label)
		{
			this.label = label;
		}
	}

	/** A directed edge of one layer. */
	static final class Edge
	{
		final int source;
		final int target;
		final Layer layer;
		/** The candidate's 1-based rank in the target's list; 0 for a follow. */
		final int rank;
		/** The recommender's score; NaN for a follow. */
		final double score;

		Edge(int source, int target, Layer layer, int rank, double score)
		{
			this.source = source;
			this.target = target;
			this.layer = layer;
			this.rank = rank;
			this.score = score;
		}

		/** The edge's weight in rank-aware analyses: 1 for a follow or adoption, 1/log2(1 + rank) for a recommendation. */
		double weight()
		{
			return layer == Layer.RECOMMEND ? rankWeight(rank) : 1.0;
		}
	}

	/** The DCG discount of a rank: 1 for the first, 0.63 for the second, 0.5 for the third. */
	static double rankWeight(int rank)
	{
		return 1.0 / (Math.log(1.0 + Math.max(1, rank)) / Math.log(2.0));
	}

	final String runId;
	final String algorithm;
	final String dataset;
	final RecommendationCandidateType candidateType;
	final int cutoff;
	private final String[] names;
	private final Kind[] kinds;
	private final int[] community;
	private final String[] communities;
	private final boolean[] targets;
	private final boolean[] pool;
	private final List<Edge> follows;
	private final List<Edge> recommendations;
	private final List<Edge> adoptions;
	private final Map<String,Integer> index;

	private RunGraph(String runId, String algorithm, String dataset, RecommendationCandidateType candidateType,
			int cutoff, String[] names, Kind[] kinds, int[] community, String[] communities, boolean[] targets,
			boolean[] pool, List<Edge> follows, List<Edge> recommendations, List<Edge> adoptions)
	{
		this.runId = runId == null ? "" : runId;
		this.algorithm = algorithm == null ? "" : algorithm;
		this.dataset = dataset == null ? "" : dataset;
		this.candidateType = candidateType == null ? RecommendationCandidateType.USER : candidateType;
		this.cutoff = Math.max(1, cutoff);
		this.names = names;
		this.kinds = kinds;
		this.community = community;
		this.communities = communities;
		this.targets = targets;
		this.pool = pool;
		this.follows = Collections.unmodifiableList(follows);
		this.recommendations = Collections.unmodifiableList(recommendations);
		this.adoptions = Collections.unmodifiableList(adoptions);
		HashMap<String,Integer> byName = new HashMap<String,Integer>();
		for (int node = 0; node < names.length; node++)
		{
			byName.put(names[node], Integer.valueOf(node));
		}
		this.index = byName;
	}

	// ------------------------------------------------------------ building

	/**
	 * The graph of a run from what it was evaluated against and its merged
	 * lists, or null when the run was not prepared for evaluation.
	 */
	static RunGraph of(RecommendationQualityEvaluator.Setup setup,
			Map<String,? extends List<RecommendationRunResults.Entry>> results, String dataset)
	{
		if (setup == null || results == null)
		{
			return null;
		}
		Builder builder = new Builder(setup.runId, setup.algorithm, dataset, setup.candidateType, setup.cutoff);
		for (Map.Entry<String,String> label : setup.labels.entrySet())
		{
			builder.user(label.getKey(), label.getValue());
		}
		for (String user : setup.users)
		{
			builder.user(user, setup.labels.get(user));
			builder.candidate(user, setup.candidateType == RecommendationCandidateType.USER);
		}
		if (setup.candidateType == RecommendationCandidateType.FOLLOWEE)
		{
			for (String account : setup.followees())
			{
				builder.candidate(account, true);
			}
		}
		for (String target : setup.targets)
		{
			builder.target(target, setup.labels.get(target));
		}
		for (Map.Entry<String,? extends List<RecommendationRunResults.Entry>> row
				: new TreeMap<String,List<RecommendationRunResults.Entry>>(results).entrySet())
		{
			builder.target(row.getKey(), setup.labels.get(row.getKey()));
			List<String> candidates = new ArrayList<String>();
			List<Double> scores = new ArrayList<Double>();
			for (RecommendationRunResults.Entry entry : row.getValue())
			{
				candidates.add(entry.getCandidate());
				scores.add(Double.valueOf(entry.getScore()));
			}
			builder.list(row.getKey(), candidates, scores, setup.labels);
		}
		return builder.build();
	}

	/** Assembles a graph from users, accounts, targets and ranked lists. */
	static final class Builder
	{
		private final String runId;
		private final String algorithm;
		private final String dataset;
		private final RecommendationCandidateType candidateType;
		private final int cutoff;
		private final TreeMap<String,String> labels = new TreeMap<String,String>();
		private final TreeSet<String> users = new TreeSet<String>();
		private final TreeSet<String> accounts = new TreeSet<String>();
		private final TreeSet<String> targets = new TreeSet<String>();
		private final TreeSet<String> pool = new TreeSet<String>();
		private final TreeMap<String,List<String>> lists = new TreeMap<String,List<String>>();
		private final TreeMap<String,List<Double>> scores = new TreeMap<String,List<Double>>();

		Builder(String runId, String algorithm, String dataset, RecommendationCandidateType candidateType, int cutoff)
		{
			this.runId = runId;
			this.algorithm = algorithm;
			this.dataset = dataset;
			this.candidateType = candidateType == null ? RecommendationCandidateType.USER : candidateType;
			this.cutoff = Math.max(1, cutoff);
		}

		/** A user and the account it follows (null: unknown). */
		Builder user(String name, String followee)
		{
			if (name == null || name.length() == 0)
			{
				return this;
			}
			users.add(name);
			if (followee != null && followee.length() > 0)
			{
				labels.put(name, followee);
				accounts.add(followee);
			}
			return this;
		}

		/** An account (a node that others can follow). */
		Builder account(String name)
		{
			if (name != null && name.length() > 0)
			{
				accounts.add(name);
			}
			return this;
		}

		/** Marks a node as a candidate the recommender could have proposed. */
		Builder candidate(String name, boolean candidate)
		{
			if (candidate && name != null && name.length() > 0)
			{
				pool.add(name);
			}
			return this;
		}

		Builder target(String name, String followee)
		{
			if (name != null && name.length() > 0)
			{
				user(name, followee);
				targets.add(name);
			}
			return this;
		}

		/** A target's merged list: candidates in rank order with their scores. */
		Builder list(String target, List<String> candidates, List<Double> candidateScores, Map<String,String> known)
		{
			lists.put(target, new ArrayList<String>(candidates));
			scores.put(target, new ArrayList<Double>(candidateScores));
			int shown = Math.min(cutoff, candidates.size());
			for (int rank = 0; rank < shown; rank++)
			{
				String candidate = candidates.get(rank);
				if (candidateType == RecommendationCandidateType.FOLLOWEE)
				{
					account(candidate);
				}
				else
				{
					user(candidate, known == null ? null : known.get(candidate));
				}
			}
			return this;
		}

		RunGraph build()
		{
			TreeSet<String> all = new TreeSet<String>(users);
			all.addAll(accounts);
			String[] names = all.toArray(new String[all.size()]);
			String[] communityNames = accounts.toArray(new String[accounts.size()]);
			HashMap<String,Integer> communityIndex = new HashMap<String,Integer>();
			for (int index = 0; index < communityNames.length; index++)
			{
				communityIndex.put(communityNames[index], Integer.valueOf(index));
			}
			HashMap<String,Integer> nodeIndex = new HashMap<String,Integer>();
			Kind[] kinds = new Kind[names.length];
			int[] community = new int[names.length];
			boolean[] target = new boolean[names.length];
			boolean[] candidate = new boolean[names.length];
			for (int node = 0; node < names.length; node++)
			{
				String name = names[node];
				nodeIndex.put(name, Integer.valueOf(node));
				boolean user = users.contains(name);
				boolean account = accounts.contains(name);
				kinds[node] = user && account ? Kind.BOTH : account ? Kind.ACCOUNT : Kind.USER;
				String label = labels.get(name);
				Integer own = label != null ? communityIndex.get(label) : account ? communityIndex.get(name) : null;
				community[node] = own == null ? -1 : own.intValue();
				target[node] = targets.contains(name);
				candidate[node] = pool.contains(name);
			}
			List<Edge> follows = new ArrayList<Edge>();
			for (Map.Entry<String,String> label : labels.entrySet())
			{
				int source = nodeIndex.get(label.getKey()).intValue();
				int account = nodeIndex.get(label.getValue()).intValue();
				if (source != account)
				{
					follows.add(new Edge(source, account, Layer.FOLLOW, 0, Double.NaN));
				}
			}
			List<Edge> recommendations = new ArrayList<Edge>();
			List<Edge> adoptions = new ArrayList<Edge>();
			for (Map.Entry<String,List<String>> list : lists.entrySet())
			{
				int source = nodeIndex.get(list.getKey()).intValue();
				List<String> candidates = list.getValue();
				List<Double> values = scores.get(list.getKey());
				int shown = Math.min(cutoff, candidates.size());
				for (int rank = 0; rank < shown; rank++)
				{
					Integer node = nodeIndex.get(candidates.get(rank));
					if (node != null && node.intValue() != source)
					{
						recommendations.add(new Edge(source, node.intValue(), Layer.RECOMMEND, rank + 1,
								values.get(rank).doubleValue()));
					}
				}
				int adopted = BatchRecommendationSelector.adoptedIndex(list.getKey(), values, cutoff);
				if (adopted >= 0)
				{
					Integer node = nodeIndex.get(candidates.get(adopted));
					if (node != null && node.intValue() != source)
					{
						adoptions.add(new Edge(source, node.intValue(), Layer.ADOPT, adopted + 1,
								values.get(adopted).doubleValue()));
					}
				}
			}
			return new RunGraph(runId, algorithm, dataset, candidateType, cutoff, names, kinds, community,
					communityNames, target, candidate, follows, recommendations, adoptions);
		}
	}

	// ------------------------------------------------------------ nodes

	int size()
	{
		return names.length;
	}

	String name(int node)
	{
		return names[node];
	}

	Kind kind(int node)
	{
		return kinds[node];
	}

	boolean isUser(int node)
	{
		return kinds[node] != Kind.ACCOUNT;
	}

	boolean isAccount(int node)
	{
		return kinds[node] != Kind.USER;
	}

	/** The node's ground-truth community (an index into {@link #communityName}), or -1 when unknown. */
	int community(int node)
	{
		return community[node];
	}

	int communityCount()
	{
		return communities.length;
	}

	String communityName(int community)
	{
		return community < 0 || community >= communities.length ? "unknown" : communities[community];
	}

	/** The community an account anchors (the community of its followers), or -1 for a user. */
	int anchoredCommunity(int node)
	{
		if (!isAccount(node))
		{
			return -1;
		}
		int found = java.util.Arrays.binarySearch(communities, names[node]);
		return found < 0 ? -1 : found;
	}

	boolean isTarget(int node)
	{
		return targets[node];
	}

	/** Whether the recommender could have proposed this node (it is in the run's candidate set). */
	boolean isCandidate(int node)
	{
		return pool[node];
	}

	/** The node of a name, or -1. */
	int indexOf(String name)
	{
		Integer node = name == null ? null : index.get(name);
		return node == null ? -1 : node.intValue();
	}

	int targetCount()
	{
		int count = 0;
		for (boolean target : targets)
		{
			count += target ? 1 : 0;
		}
		return count;
	}

	int count(Kind kind)
	{
		int count = 0;
		for (Kind value : kinds)
		{
			count += value == kind ? 1 : 0;
		}
		return count;
	}

	int candidateCount()
	{
		int count = 0;
		for (boolean candidate : pool)
		{
			count += candidate ? 1 : 0;
		}
		return count;
	}

	/** Members of each ground-truth community among the candidates (the community sizes chance is drawn from). */
	int[] candidateCommunitySizes()
	{
		int[] sizes = new int[communities.length];
		for (int node = 0; node < names.length; node++)
		{
			if (pool[node] && community[node] >= 0)
			{
				sizes[community[node]]++;
			}
		}
		return sizes;
	}

	/** Followers of each account in the observed network. */
	int[] followerCounts()
	{
		int[] counts = new int[communities.length];
		for (Edge edge : follows)
		{
			int anchored = anchoredCommunity(edge.target);
			if (anchored >= 0)
			{
				counts[anchored]++;
			}
		}
		return counts;
	}

	// ------------------------------------------------------------ edges

	List<Edge> edges(Layer layer)
	{
		switch (layer)
		{
			case FOLLOW:
				return follows;
			case RECOMMEND:
				return recommendations;
			default:
				return adoptions;
		}
	}

	/** Every edge, layer by layer. */
	List<Edge> allEdges()
	{
		List<Edge> all = new ArrayList<Edge>(follows.size() + recommendations.size() + adoptions.size());
		all.addAll(follows);
		all.addAll(recommendations);
		all.addAll(adoptions);
		return all;
	}

	/**
	 * Whether a recommendation or adoption edge stays inside the target's
	 * ground-truth community: the candidate follows the target's account (a
	 * user candidate) or is that account (an account candidate). Null when
	 * either side's community is unknown.
	 */
	Boolean inside(Edge edge)
	{
		int own = community[edge.source];
		if (own < 0)
		{
			return null;
		}
		if (candidateType == RecommendationCandidateType.FOLLOWEE)
		{
			int anchored = anchoredCommunity(edge.target);
			return anchored < 0 ? null : Boolean.valueOf(anchored == own);
		}
		int other = community[edge.target];
		return other < 0 ? null : Boolean.valueOf(other == own);
	}

	/**
	 * The chance that a uniformly random candidate of {@code target} lies in
	 * its community: same-community candidates over all candidates other than
	 * the target. NaN when the target's community is unknown or it has no
	 * candidates.
	 */
	double insideChance(int target)
	{
		int own = community[target];
		if (own < 0)
		{
			return Double.NaN;
		}
		int[] counts = insideCounts(target);
		return counts[1] == 0 ? Double.NaN : counts[0] / (double)counts[1];
	}

	/** {same-community candidates, candidates} of a target (the target itself excluded). */
	int[] insideCounts(int target)
	{
		int[] perCommunity = poolCounts();
		int own = community[target];
		int total = poolSize - (pool[target] ? 1 : 0);
		if (own < 0)
		{
			return new int[] {0, total};
		}
		int inside = perCommunity[own] - (pool[target] && candidateCommunity(target) == own ? 1 : 0);
		return new int[] {inside, total};
	}

	/** The community a candidate counts for: an account's own, a user's followee's. */
	private int candidateCommunity(int node)
	{
		return candidateType == RecommendationCandidateType.FOLLOWEE ? anchoredCommunity(node) : community[node];
	}

	private int[] poolCounts;
	private int poolSize;

	private synchronized int[] poolCounts()
	{
		if (poolCounts == null)
		{
			int[] counts = new int[communities.length];
			int size = 0;
			for (int node = 0; node < names.length; node++)
			{
				if (pool[node])
				{
					size++;
					int other = candidateCommunity(node);
					if (other >= 0)
					{
						counts[other]++;
					}
				}
			}
			poolSize = size;
			poolCounts = counts;
		}
		return poolCounts;
	}

	/** The adoption edge of a target, or null. */
	Edge adoptionOf(int target)
	{
		for (Edge edge : adoptions)
		{
			if (edge.source == target)
			{
				return edge;
			}
		}
		return null;
	}

	/** A target's recommendation edges in rank order. */
	List<Edge> recommendationsOf(int target)
	{
		List<Edge> list = new ArrayList<Edge>();
		for (Edge edge : recommendations)
		{
			if (edge.source == target)
			{
				list.add(edge);
			}
		}
		return list;
	}

	/** The account a user follows, or -1. */
	int followeeOf(int user)
	{
		for (Edge edge : follows)
		{
			if (edge.source == user)
			{
				return edge.target;
			}
		}
		return -1;
	}

	String describe()
	{
		return names.length + " nodes (" + count(Kind.USER) + " users, " + count(Kind.ACCOUNT) + " accounts, "
				+ count(Kind.BOTH) + " both), " + follows.size() + " follows, " + recommendations.size()
				+ " recommendations, " + adoptions.size() + " adoptions";
	}
}
