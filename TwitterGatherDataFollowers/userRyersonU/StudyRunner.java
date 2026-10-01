package TwitterGatherDataFollowers.userRyersonU;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Executes a {@link StudyDesign}: every run slot in the design's order, each a
 * complete live run of the real simulator, collected into a
 * {@link StudyResult} whose files are rewritten after every run (so an
 * interrupted study keeps what it measured).
 */
final class StudyRunner
{
	/** Executes one configured live run and returns its run id. */
	interface Driver
	{
		/**
		 * Loads the design's dataset once, before the first run.
		 */
		void prepare(StudyDesign design) throws Exception;

		/**
		 * Runs one slot: the driver sets the simulator's run options (algorithm,
		 * node count, targets), then calls {@code configure} on the thread it
		 * configures on (it sets the cluster, plan, scenario and run properties),
		 * initializes and starts the simulator and returns when the run ended.
		 * Returns the run's id.
		 */
		String execute(StudyDesign design, StudyDesign.RunSlot slot, Runnable configure) throws Exception;
	}

	/** Progress callbacks (called on the runner's thread). */
	interface Progress
	{
		void runStarting(int index, int total, StudyDesign.RunSlot slot);

		void runFinished(int index, int total, StudyResult.RunOutcome outcome);

		void message(String text);
	}

	private final StudyDesign design;
	private final Driver driver;
	private final Progress progress;
	private final AtomicBoolean cancelled = new AtomicBoolean();
	private final int heapMb;

	StudyRunner(StudyDesign design, Driver driver, Progress progress, int heapMb)
	{
		this.design = design;
		this.driver = driver;
		this.progress = progress;
		this.heapMb = heapMb;
	}

	void cancel()
	{
		cancelled.set(true);
	}

	boolean isCancelled()
	{
		return cancelled.get();
	}

	/** Runs the whole study; returns its result (also when cancelled part-way). */
	StudyResult run() throws Exception
	{
		StudyResult result = new StudyResult(design);
		List<StudyDesign.RunSlot> slots = design.schedule();
		progress.message("Study \"" + design.getName() + "\": " + design.cells().size() + " cells, "
				+ slots.size() + " runs.");
		driver.prepare(design);
		RecommendationSchedulerPlan savedPlan = RecommendationSchedulerRegistry.snapshot();
		ClusterConfig savedCluster = ClusterConfig.current("Before study");
		try
		{
			for (int index = 0; index < slots.size() && !cancelled.get(); index++)
			{
				StudyDesign.RunSlot slot = slots.get(index);
				progress.runStarting(index + 1, slots.size(), slot);
				StudyResult.RunOutcome outcome = runSlot(slot);
				result.add(outcome);
				progress.runFinished(index + 1, slots.size(), outcome);
				try
				{
					File directory = result.write();
					if (index + 1 == slots.size())
					{
						progress.message("Study written to " + directory.getPath());
					}
				}
				catch (java.io.IOException failure)
				{
					progress.message("Could not write the study files: " + failure.getMessage());
				}
			}
		}
		finally
		{
			RecommendationSchedulerRegistry.setRunProperties(null);
			savedCluster.apply(heapMb);
			RecommendationSchedulerRegistry.apply(savedPlan.getMode(), savedPlan.getPolicy(),
					savedPlan.getPlannedNodes(), savedPlan.getPlannedHeapMb(), savedPlan.getNote(),
					savedPlan.getScenario());
		}
		if (cancelled.get())
		{
			progress.message("Study cancelled after " + result.outcomes().size() + " run(s).");
		}
		return result;
	}

	private StudyResult.RunOutcome runSlot(StudyDesign.RunSlot slot)
	{
		long started = System.nanoTime();
		StudyDesign.Cell cell = slot.cell;
		LinkedHashMap<String,String> properties = new LinkedHashMap<String,String>();
		properties.put("study", design.getName());
		properties.put("cell", cell.label());
		properties.put("repetition", slot.warmup ? "warm-up" : Integer.toString(slot.repetition));
		properties.put("cluster", cell.cluster.getName());
		RecommendationSchedulerRegistry.setRunProperties(properties);
		String runId = null;
		try
		{
			final ClusterConfig cluster = cell.cluster.withSettings(
					cell.cluster.getSettings().withSeed(slot.seed));
			final StudyDesign.Cell configured = cell;
			Runnable configure = new Runnable()
			{
				public void run()
				{
					cluster.apply(heapMb);
					RecommendationSchedulerRegistry.apply(design.getMode(), configured.policy,
							cluster.getNodeCount(), heapMb, "study " + design.getName(), configured.scenario);
				}
			};
			runId = driver.execute(design, slot, configure);
			SchedulingRunContext context = SchedulingRuntime.get(runId);
			if (context == null)
			{
				throw new IllegalStateException("the run " + runId + " has no scheduling context");
			}
			RunMetrics metrics = context.awaitRecord(120000L);
			String status = context.recorder().getStatus();
			return new StudyResult.RunOutcome(slot, runId, status,
					metrics == null ? null : metrics.summary(), RunRecordWriter.resultsHash(runId),
					context.getRecordDirectory() == null ? "" : context.getRecordDirectory().getName(),
					metrics == null ? "no run record" : "", (System.nanoTime() - started) / 1.0e9);
		}
		catch (Exception failure)
		{
			return new StudyResult.RunOutcome(slot, runId, "failed", null, "", "",
					String.valueOf(failure.getMessage()), (System.nanoTime() - started) / 1.0e9);
		}
	}
}
