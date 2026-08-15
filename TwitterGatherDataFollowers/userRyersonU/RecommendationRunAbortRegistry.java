package TwitterGatherDataFollowers.userRyersonU;

import java.util.HashMap;
import java.util.Map;

/**
 * In-process cancellation signal shared by recommender agents in the same JADE
 * container. It prevents queued model workers and late score publications after
 * one required node has failed.
 */
final class RecommendationRunAbortRegistry
{
	private static final Object LOCK = new Object();
	private static final Map<String,String> ABORTED_RUNS =
			new HashMap<String,String>();

	private RecommendationRunAbortRegistry()
	{
	}

	static void prepare(String runId)
	{
		if (runId == null)
		{
			return;
		}
		synchronized (LOCK)
		{
			ABORTED_RUNS.remove(runId);
		}
	}

	static void abort(String runId, String reason)
	{
		if (runId == null)
		{
			return;
		}
		synchronized (LOCK)
		{
			if (!ABORTED_RUNS.containsKey(runId))
			{
				ABORTED_RUNS.put(
						runId,
						reason == null ? "Recommendation run aborted." : reason);
			}
		}
	}

	static boolean isAborted(String runId)
	{
		synchronized (LOCK)
		{
			return runId != null && ABORTED_RUNS.containsKey(runId);
		}
	}

	static void requireActive(String runId)
	{
		synchronized (LOCK)
		{
			String reason = runId == null ? null : ABORTED_RUNS.get(runId);
			if (reason != null)
			{
				throw new IllegalStateException(
						"Recommendation run was already aborted: " + reason);
			}
		}
	}
}
