package TwitterGatherDataFollowers.userRyersonU;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Ends runs and publishes their records.
 *
 * When a run completes or fails, {@link #finish} records its end milestone,
 * stops its timeline (scenario and crash processes), computes its
 * {@link RunMetrics} and writes its record ({@link RunRecordWriter}) on a
 * background thread, then tells the listeners (panels, the study runner).
 * Each run is finished once.
 */
final class RunRecords
{
	/** Told when a run's record has been written. */
	interface Listener
	{
		void runRecorded(SchedulingRunContext context, RunMetrics metrics, File directory);
	}

	private static final CopyOnWriteArrayList<Listener> LISTENERS = new CopyOnWriteArrayList<Listener>();

	private RunRecords()
	{
	}

	static void addListener(Listener listener)
	{
		if (listener != null)
		{
			LISTENERS.addIfAbsent(listener);
		}
	}

	static void removeListener(Listener listener)
	{
		LISTENERS.remove(listener);
	}

	/** Records a run milestone (a no-op for runs without a context). */
	static void milestone(String runId, String name)
	{
		SchedulingRunContext context = SchedulingRuntime.get(runId);
		if (context != null)
		{
			context.milestone(name);
		}
	}

	/**
	 * Finishes the run with {@code status} ("completed", "failed: reason",
	 * "aborted", "superseded"): its record is computed and written once.
	 */
	static void finish(String runId, final String status)
	{
		final SchedulingRunContext context = SchedulingRuntime.get(runId);
		if (context == null || !context.markFinished(status))
		{
			return;
		}
		if ("completed".equals(status))
		{
			context.milestone("run-end");
		}
		context.timeline().stop();
		Thread writer = new Thread(new Runnable()
		{
			public void run()
			{
				RunMetrics metrics = null;
				File directory = null;
				try
				{
					metrics = RunMetrics.compute(context);
					List<PhaseReplay.Result> validation = new ArrayList<PhaseReplay.Result>();
					List<PhaseReplay.Result> counterfactuals = new ArrayList<PhaseReplay.Result>();
					try
					{
						validation = PhaseReplay.validateAll(context);
						RunMetrics.PhaseMetrics main = metrics.mainPhase();
						double mainError = Double.NaN;
						if (main != null && !Double.isNaN(main.completedAt))
						{
							counterfactuals = PhaseReplay.comparePolicies(context, main.key);
							for (PhaseReplay.Result result : validation)
							{
								if (result.getPhase().equals(main.key))
								{
									mainError = result.getRelativeError();
								}
							}
						}
						PhaseReplay.Result best = null;
						for (PhaseReplay.Result result : counterfactuals)
						{
							if (result.isComplete() && (best == null
									|| result.getPredictedMakespan() < best.getPredictedMakespan()))
							{
								best = result;
							}
						}
						metrics.setReplay(PhaseReplay.meanAbsoluteError(validation), mainError,
								best == null ? "" : best.getPolicy().getLabel(),
								best == null ? Double.NaN : best.getPredictedMakespan());
					}
					catch (RuntimeException replayFailure)
					{
						context.recorder().warning("The simulator replay of this run failed: "
								+ replayFailure.getMessage());
					}
					directory = RunRecordWriter.write(context, metrics);
					if (directory != null)
					{
						RunRecordWriter.writeReplay(directory, validation, counterfactuals);
					}
					System.out.println("[run record] " + metrics.describe());
					if (directory != null)
					{
						System.out.println("[run record] written to " + directory.getPath());
					}
				}
				catch (RuntimeException failure)
				{
					System.err.println("Could not record run " + context.getRunId() + ": " + failure);
				}
				finally
				{
					context.recordWritten(metrics, directory);
				}
				if (metrics != null)
				{
					for (Listener listener : LISTENERS)
					{
						try
						{
							listener.runRecorded(context, metrics, directory);
						}
						catch (RuntimeException ignored)
						{
							//A listener must not stop the others.
						}
					}
				}
			}
		}, "Run record " + runId);
		writer.start();
	}

	/** Contexts of the retained runs that have a written record, oldest first. */
	static List<SchedulingRunContext> recorded()
	{
		ArrayList<SchedulingRunContext> recorded = new ArrayList<SchedulingRunContext>();
		for (SchedulingRunContext context : SchedulingRuntime.retained())
		{
			if (context.getRecordedMetrics() != null)
			{
				recorded.add(context);
			}
		}
		return recorded;
	}
}
