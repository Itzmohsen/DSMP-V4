package TwitterGatherDataFollowers.userRyersonU;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.locks.LockSupport;

/**
 * The timed side of one live run: scenario steps, crash-recovery processes,
 * crash detection and repairs, each fired at its model time.
 *
 * A single daemon thread per run sleeps until the next due action (converting
 * model time to wall time with the run's clock) and is woken at once when a
 * phase opens or makes progress, so progress-triggered steps fire within
 * microseconds of the commit that reaches their threshold. Refused actions
 * (for example draining the last available node) are recorded with the reason.
 */
final class RunTimeline implements Runnable
{
	private static final long IDLE_POLL_NANOS = 20000000L;

	/** An action due at a model time. */
	private static final class Timed implements Comparable<Timed>
	{
		private final double atMs;
		private final long sequence;
		private final String label;
		private final Runnable action;

		private Timed(double atMs, long sequence, String label, Runnable action)
		{
			this.atMs = atMs;
			this.sequence = sequence;
			this.label = label;
			this.action = action;
		}

		public int compareTo(Timed other)
		{
			int byTime = Double.compare(atMs, other.atMs);
			return byTime != 0 ? byTime : Long.compare(sequence, other.sequence);
		}
	}

	private final SchedulingRunContext context;
	private final Scenario scenario;
	private final List<FailureSchedule> schedules;
	private final PriorityQueue<Timed> queue = new PriorityQueue<Timed>();
	private final ArrayList<Scenario.Step> progressSteps = new ArrayList<Scenario.Step>();
	private final ArrayList<Scenario.Step> phaseOpenSteps = new ArrayList<Scenario.Step>();
	private final Set<String> seenPhases = new HashSet<String>();
	private long sequence;
	private volatile Thread thread;
	private volatile boolean stopped;

	RunTimeline(SchedulingRunContext context, Scenario scenario, List<FailureSchedule> schedules)
	{
		this.context = context;
		this.scenario = scenario == null ? Scenario.NONE : scenario;
		this.schedules = schedules == null ? new ArrayList<FailureSchedule>() : schedules;
	}

	/** Starts the timeline thread; the scenario's time steps count from model time 0. */
	synchronized void start()
	{
		if (thread != null)
		{
			return;
		}
		for (final Scenario.Step step : scenario.getSteps())
		{
			switch (step.getTrigger().getKind())
			{
				case TIME:
					schedule(step.getTrigger().getTimeMs(), "scenario: " + step.describe(), new Runnable()
					{
						public void run()
						{
							fire(step);
						}
					});
					break;
				case PHASE_OPEN:
					phaseOpenSteps.add(step);
					break;
				default:
					progressSteps.add(step);
					break;
			}
		}
		for (FailureSchedule schedule : schedules)
		{
			scheduleNextOutage(schedule, 0.0);
		}
		if (queue.isEmpty() && progressSteps.isEmpty() && phaseOpenSteps.isEmpty())
		{
			return;
		}
		Thread worker = new Thread(this, "Run timeline " + context.getRunId());
		worker.setDaemon(true);
		thread = worker;
		worker.start();
	}

	/** Schedules {@code action} at model time {@code atMs}. */
	void schedule(double atMs, String label, Runnable action)
	{
		synchronized (this)
		{
			queue.add(new Timed(atMs, sequence++, label, action));
			if (thread == null && !stopped)
			{
				Thread worker = new Thread(this, "Run timeline " + context.getRunId());
				worker.setDaemon(true);
				thread = worker;
				worker.start();
				return;
			}
		}
		wake();
	}

	/** A phase opened or made progress, or input was delivered: re-check triggers now. */
	void wake()
	{
		Thread worker = thread;
		if (worker != null)
		{
			LockSupport.unpark(worker);
		}
	}

	void stop()
	{
		stopped = true;
		wake();
	}

	public void run()
	{
		while (!stopped && !context.isClosed())
		{
			try
			{
				checkPhaseOpenings();
				checkProgress();
				Timed due = null;
				long parkNanos = IDLE_POLL_NANOS;
				synchronized (this)
				{
					Timed next = queue.peek();
					if (next != null)
					{
						long remaining = context.clock().wallNanosAt(next.atMs) - System.nanoTime();
						if (remaining <= 0L)
						{
							due = queue.poll();
						}
						else
						{
							parkNanos = Math.min(IDLE_POLL_NANOS, remaining);
						}
					}
				}
				if (due != null)
				{
					try
					{
						due.action.run();
					}
					catch (RuntimeException refused)
					{
						context.recordRejected(due.label, refused.getMessage());
					}
					continue;
				}
				LockSupport.parkNanos(parkNanos);
			}
			catch (RuntimeException unexpected)
			{
				context.recordRejected("timeline", String.valueOf(unexpected.getMessage()));
				LockSupport.parkNanos(IDLE_POLL_NANOS);
			}
		}
	}

	private void checkPhaseOpenings()
	{
		if (phaseOpenSteps.isEmpty())
		{
			return;
		}
		for (SchedulingRunContext.PhaseEntry entry : context.phases())
		{
			if (!seenPhases.add(entry.getKey()))
			{
				continue;
			}
			for (final Scenario.Step step : phaseOpenSteps)
			{
				if (step.getTrigger().getPhase().equals(entry.getKey()))
				{
					schedule(entry.getOpenedAtModelMs() + step.getTrigger().getTimeMs(),
							"scenario: " + step.describe(), new Runnable()
					{
						public void run()
						{
							fire(step);
						}
					});
				}
			}
		}
	}

	private void checkProgress()
	{
		if (progressSteps.isEmpty())
		{
			return;
		}
		ArrayList<Scenario.Step> due = new ArrayList<Scenario.Step>();
		for (Scenario.Step step : progressSteps)
		{
			if (reached(step.getTrigger()))
			{
				due.add(step);
			}
		}
		progressSteps.removeAll(due);
		for (Scenario.Step step : due)
		{
			try
			{
				fire(step);
			}
			catch (RuntimeException refused)
			{
				context.recordRejected("scenario: " + step.describe(), refused.getMessage());
			}
		}
	}

	private boolean reached(Scenario.Trigger trigger)
	{
		if (trigger.getKind() == Scenario.TriggerKind.INPUT_PROGRESS)
		{
			return context.inputProgress() + 1e-12 >= trigger.getFraction();
		}
		String key = trigger.getPhase();
		SchedulingRunContext.PhaseEntry entry = key.length() == 0
				? context.runningPhase() : context.phase(key);
		if (entry == null)
		{
			return false;
		}
		return entry.getPhase().getDispatcher().progress() + 1e-12 >= trigger.getFraction();
	}

	private void fire(Scenario.Step step)
	{
		context.recordScenarioStep(step);
		Scenario.Action action = step.getAction();
		switch (action.getKind())
		{
			case PAUSE:
				context.setPaused(action.getNode(), true);
				break;
			case RESUME:
				context.setPaused(action.getNode(), false);
				break;
			case UNAVAILABLE:
				context.setUnavailable(action.getNode(), true);
				break;
			case AVAILABLE:
				context.setUnavailable(action.getNode(), false);
				break;
			case DRAIN:
				context.drain(action.getNode(), action.getTarget());
				break;
			case REDIRECT:
				context.redirect(action.getNode(), action.getCount());
				break;
			case CAPACITY:
				context.setCapacityScale(action.getNode(), action.getValue());
				break;
			case POLICY:
				context.setPolicyOverride(action.getPolicy());
				break;
			case CRASH:
				context.crashNode(action.getNode(), action.getValue());
				break;
			case RECOVER:
				context.recoverNode(action.getNode());
				break;
			case ACTION_WINDOW:
				context.setPacingMs((int)Math.round(action.getValue()));
				break;
			default:
				context.resetControls();
				break;
		}
	}

	/** Schedules the schedule's next crash after {@code afterMs} and, from it, its repair. */
	private void scheduleNextOutage(final FailureSchedule schedule, double afterMs)
	{
		final FailureSchedule.Outage outage = schedule.nextAfter(afterMs);
		if (outage == null)
		{
			return;
		}
		schedule(outage.getCrashAtMs(), "crash of node " + outage.getNode(), new Runnable()
		{
			public void run()
			{
				if (context.crashNode(outage.getNode(), outage.getDowntimeMs()))
				{
					schedule(outage.getRecoverAtMs(), "next outage of node " + outage.getNode(),
							new Runnable()
					{
						public void run()
						{
							scheduleNextOutage(schedule, outage.getRecoverAtMs());
						}
					});
				}
				else
				{
					scheduleNextOutage(schedule, outage.getRecoverAtMs());
				}
			}
		});
	}
}
