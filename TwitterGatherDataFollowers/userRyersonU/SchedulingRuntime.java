package TwitterGatherDataFollowers.userRyersonU;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registry of scheduling run contexts by run id.
 *
 * The controller registers a context when it initializes a run; every agent of
 * that run and every panel looks it up by the run's conversation id. The most
 * recent contexts are retained after their runs end so panels can still show
 * them.
 */
final class SchedulingRuntime
{
	private static final int RETAINED_RUNS = 8;
	private static final Object LOCK = new Object();
	private static final LinkedHashMap<String,SchedulingRunContext> RUNS =
			new LinkedHashMap<String,SchedulingRunContext>();
	private static String latestRunId = "";

	private SchedulingRuntime()
	{
	}

	/**
	 * Registers a new run. The simulator runs one experiment at a time, so any
	 * earlier run that is still open is closed: its phases and collectives
	 * release their waiting threads at once instead of at the stall timeout.
	 */
	static void register(SchedulingRunContext context)
	{
		if (context == null)
		{
			return;
		}
		synchronized (LOCK)
		{
			SchedulingRunContext previous = RUNS.remove(context.getRunId());
			if (previous != null && previous != context)
			{
				previous.close("replaced by a new initialization of the same run id");
			}
			for (SchedulingRunContext earlier : RUNS.values())
			{
				if (!earlier.isFinished())
				{
					//A run that never finished (aborted, stopped) still leaves a record.
					RunRecords.finish(earlier.getRunId(), "superseded by run " + context.getRunId());
				}
				if (!earlier.isClosed())
				{
					earlier.close("superseded by run " + context.getRunId());
				}
			}
			RUNS.put(context.getRunId(), context);
			latestRunId = context.getRunId();
			while (RUNS.size() > RETAINED_RUNS)
			{
				String eldest = RUNS.keySet().iterator().next();
				SchedulingRunContext removed = RUNS.remove(eldest);
				if (removed != null)
				{
					removed.close("retired");
				}
			}
		}
	}

	/** The context of {@code runId}, or null when none is registered. */
	static SchedulingRunContext get(String runId)
	{
		synchronized (LOCK)
		{
			return runId == null ? null : RUNS.get(runId);
		}
	}

	/**
	 * The context of {@code runId}; a run that was started without one (for
	 * example by a test harness) gets the original DSMP baseline, created once
	 * so that every node of the run shares it.
	 */
	static SchedulingRunContext getOrCreateBaseline(String runId, int nodeCount, int algorithm)
	{
		synchronized (LOCK)
		{
			SchedulingRunContext context = runId == null ? null : RUNS.get(runId);
			if (context != null)
			{
				return context;
			}
			context = new SchedulingRunContext(
					runId,
					SchedulingRunContext.Mode.BASELINE,
					SchedulerLabEngine.SchedulingPolicy.CAPACITY_WEIGHTED,
					null,
					nodeCount,
					algorithm,
					RecommendationSchedulerRegistry.FAULT_SEED,
					RecommendationSchedulerRegistry.MAX_FAILURES_PER_UNIT,
					RecommendationSchedulerRegistry.STALL_TIMEOUT_MS,
					0);
			register(context);
			return context;
		}
	}

	/** The most recently registered context, or null. */
	static SchedulingRunContext latest()
	{
		synchronized (LOCK)
		{
			return RUNS.get(latestRunId);
		}
	}

	/** The latest context whose run has not ended, or null. */
	static SchedulingRunContext active()
	{
		SchedulingRunContext latest = latest();
		return latest == null || latest.isClosed() ? null : latest;
	}

	static void close(String runId, String reason)
	{
		SchedulingRunContext context = get(runId);
		if (context != null)
		{
			context.close(reason);
		}
	}

	static List<SchedulingRunContext> retained()
	{
		synchronized (LOCK)
		{
			return new ArrayList<SchedulingRunContext>(RUNS.values());
		}
	}

	/** For tests: forget every context. */
	static void clear()
	{
		synchronized (LOCK)
		{
			for (Map.Entry<String,SchedulingRunContext> entry : RUNS.entrySet())
			{
				entry.getValue().close("cleared");
			}
			RUNS.clear();
			latestRunId = "";
		}
	}
}
