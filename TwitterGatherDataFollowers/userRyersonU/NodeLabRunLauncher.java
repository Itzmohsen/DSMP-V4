package TwitterGatherDataFollowers.userRyersonU;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.swing.SwingUtilities;

/**
 * Starts a run from the Node Lab with one click: it initializes the agents
 * with the current cluster and plan, then starts the simulation, through the
 * same automation hooks the study runner uses (so the simulator's modal
 * "finished" dialogs stay quiet and failures come back here as text).
 */
final class NodeLabRunLauncher
{
	enum State
	{
		IDLE("Ready"),
		INITIALIZING("Initializing agents"),
		RUNNING("Running"),
		FINISHED("Finished"),
		FAILED("Failed");

		private final String label;

		State(String label)
		{
			this.label = label;
		}

		String getLabel()
		{
			return label;
		}
	}

	private static final long TIMEOUT_MS = 6L * 60L * 60L * 1000L;

	private final ControllerAgentGui gui;
	private volatile State state = State.IDLE;
	private volatile String message = "";
	private volatile long changedAt = System.currentTimeMillis();
	private volatile boolean studyActive;

	NodeLabRunLauncher(ControllerAgentGui gui)
	{
		this.gui = gui;
	}

	State getState()
	{
		return state;
	}

	String getMessage()
	{
		return message;
	}

	long getChangedAt()
	{
		return changedAt;
	}

	/** While a study runs, it alone starts runs. */
	void setStudyActive(boolean active)
	{
		studyActive = active;
	}

	boolean isBusy()
	{
		return state == State.INITIALIZING || state == State.RUNNING;
	}

	/** Why a run cannot start now, or null when it can. */
	String blocker()
	{
		if (gui == null)
		{
			return "The simulator window is not available.";
		}
		if (studyActive)
		{
			return "A study is running; it starts its own runs.";
		}
		if (isBusy() || NodeLabPanel.isRunning() || gui.isSimulatorInitializing())
		{
			return "A run is already in progress.";
		}
		if (gui.loadedUserCount() <= 0)
		{
			return "Load a dataset and get its users in the Simulator tab first.";
		}
		if (gui.selectedTargetCount() <= 0)
		{
			return "Choose the recommendation targets on the Simulator's Targets page first.";
		}
		if (!gui.isReadyToInitialize())
		{
			return "The simulator is busy; wait until its current step finishes.";
		}
		return null;
	}

	/** Initializes and starts a run in the background; {@code changed} runs on the EDT at each step. */
	void start(final Runnable changed)
	{
		if (blocker() != null)
		{
			return;
		}
		set(State.INITIALIZING, "Creating the agents with this cluster and plan.", changed);
		Thread thread = new Thread(new Runnable()
		{
			public void run()
			{
				launch(changed);
			}
		}, "node-lab-run-launcher");
		thread.setDaemon(true);
		thread.start();
	}

	private void launch(final Runnable changed)
	{
		final String automationId = "node-lab-" + System.nanoTime();
		final CountDownLatch initialized = new CountDownLatch(1);
		final CountDownLatch finished = new CountDownLatch(1);
		final String[] failure = new String[1];
		final String[] finishedMessage = new String[1];
		ControllerAgentGui.ObservatoryAutomationListener listener =
				new ControllerAgentGui.ObservatoryAutomationListener()
		{
			public void initializationFinished(String runId)
			{
				initialized.countDown();
			}

			public void simulationFinished(String runId, String text)
			{
				finishedMessage[0] = text;
				finished.countDown();
			}

			public void automationFailed(String runId, String phase, String text)
			{
				failure[0] = capitalize(phase) + " failed: " + text;
				initialized.countDown();
				finished.countDown();
			}
		};
		gui.beginObservatoryAutomation(automationId, listener);
		try
		{
			onEdt(new Runnable()
			{
				public void run()
				{
					gui.initializeAgents();
				}
			});
			if (!initialized.await(TIMEOUT_MS, TimeUnit.MILLISECONDS))
			{
				throw new IllegalStateException("Initialization did not finish in time.");
			}
			if (failure[0] != null)
			{
				throw new IllegalStateException(failure[0]);
			}
			set(State.RUNNING, "The recommender is running on the emulated cluster.", changed);
			onEdt(new Runnable()
			{
				public void run()
				{
					gui.startSimulation();
				}
			});
			if (!finished.await(TIMEOUT_MS, TimeUnit.MILLISECONDS))
			{
				throw new IllegalStateException("The run did not finish in time.");
			}
			if (failure[0] != null)
			{
				throw new IllegalStateException(failure[0]);
			}
			set(State.FINISHED, finishedMessage[0] == null || finishedMessage[0].length() == 0
					? "The run finished." : finishedMessage[0], changed);
		}
		catch (Exception problem)
		{
			set(State.FAILED, problem.getMessage() == null ? problem.toString() : problem.getMessage(), changed);
		}
		finally
		{
			gui.endObservatoryAutomation(automationId, listener);
		}
	}

	private static String capitalize(String text)
	{
		if (text == null || text.length() == 0)
		{
			return "The run";
		}
		return Character.toUpperCase(text.charAt(0)) + text.substring(1);
	}

	private void set(State next, String text, final Runnable changed)
	{
		state = next;
		message = text == null ? "" : text;
		changedAt = System.currentTimeMillis();
		if (changed != null)
		{
			SwingUtilities.invokeLater(changed);
		}
	}

	private static void onEdt(Runnable action) throws Exception
	{
		if (SwingUtilities.isEventDispatchThread())
		{
			action.run();
		}
		else
		{
			SwingUtilities.invokeAndWait(action);
		}
	}
}
