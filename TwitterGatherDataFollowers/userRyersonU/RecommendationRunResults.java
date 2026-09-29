package TwitterGatherDataFollowers.userRyersonU;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The final merged recommendations of recent runs, as the organizer ranked
 * them: for each target, candidates in rank order with their exact scores.
 * Kept so a run's output can be compared with another run's (for example the
 * same experiment on a different number of nodes) without parsing reports.
 */
final class RecommendationRunResults
{
	/** One ranked entry. */
	static final class Entry
	{
		private final String candidate;
		private final double score;

		Entry(String candidate, double score)
		{
			this.candidate = candidate;
			this.score = score;
		}

		String getCandidate()
		{
			return candidate;
		}

		double getScore()
		{
			return score;
		}

		public String toString()
		{
			return candidate + "=" + score;
		}
	}

	private static final int RETAINED_RUNS = 16;
	private static final LinkedHashMap<String,Map<String,List<Entry>>> RUNS =
			new LinkedHashMap<String,Map<String,List<Entry>>>();

	private RecommendationRunResults()
	{
	}

	/** Records a run's ranked lists (target to candidates in rank order). */
	static synchronized void record(String runId, Map<String,? extends Iterable<? extends Map.Entry<String,Double>>> ranked)
	{
		LinkedHashMap<String,List<Entry>> copy = new LinkedHashMap<String,List<Entry>>();
		for (Map.Entry<String,? extends Iterable<? extends Map.Entry<String,Double>>> row : ranked.entrySet())
		{
			ArrayList<Entry> entries = new ArrayList<Entry>();
			if (row.getValue() != null)
			{
				for (Map.Entry<String,Double> entry : row.getValue())
				{
					entries.add(new Entry(entry.getKey(),
							entry.getValue() == null ? Double.NaN : entry.getValue().doubleValue()));
				}
			}
			copy.put(row.getKey(), Collections.unmodifiableList(entries));
		}
		RUNS.remove(runId);
		RUNS.put(runId == null ? "" : runId, Collections.unmodifiableMap(copy));
		while (RUNS.size() > RETAINED_RUNS)
		{
			RUNS.remove(RUNS.keySet().iterator().next());
		}
	}

	/** The run's ranked lists, or null when it produced none (yet). */
	static synchronized Map<String,List<Entry>> get(String runId)
	{
		return RUNS.get(runId);
	}
}
