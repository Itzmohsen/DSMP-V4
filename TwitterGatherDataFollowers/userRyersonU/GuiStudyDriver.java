package TwitterGatherDataFollowers.userRyersonU;

import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.swing.SwingUtilities;

/**
 * Runs study slots through the simulator window's automation hooks, exactly
 * as a person would: load the dataset, set the run options, initialize the
 * agents, start the simulation and wait for it to end. Works with the window
 * shown or hidden ({@code -Ddsmp.gui.hidden=true}).
 *
 * The run options come from the design, not from the Simulator's pages: the
 * algorithm, the node count, the first {@code targets} users of the user list
 * as targets, the list length k, and tweet delays and graph output off.
 */
final class GuiStudyDriver implements StudyRunner.Driver
{
	private static final long USER_LOAD_TIMEOUT_MS = 10L * 60L * 1000L;
	private static final long RUN_TIMEOUT_MS = 2L * 60L * 60L * 1000L;

	private final ControllerAgentGui gui;
	private int sequence;

	GuiStudyDriver(ControllerAgentGui gui)
	{
		this.gui = gui;
	}

	public void prepare(final StudyDesign design) throws Exception
	{
		final File dataset = design.getDataset();
		if (dataset == null)
		{
			if (!(gui.isReadyToInitialize() && gui.loadedUserCount() > 0))
			{
				throw new IllegalStateException(
						"The study names no dataset and the simulator has no users loaded.");
			}
			return;
		}
		if (!dataset.isFile())
		{
			throw new IllegalStateException("The study's dataset does not exist: " + dataset);
		}
		onEdt(new Runnable()
		{
			public void run()
			{
				gui.loadImportedDataset(dataset.getAbsoluteFile(), "study " + design.getName());
				gui.getUsers();
			}
		});
		long deadline = System.currentTimeMillis() + USER_LOAD_TIMEOUT_MS;
		while (!(gui.isReadyToInitialize() && gui.loadedUserCount() > 0))
		{
			if (System.currentTimeMillis() > deadline)
			{
				throw new IllegalStateException("The dataset's users were not loaded in time.");
			}
			Thread.sleep(200L);
		}
	}

	public String execute(StudyDesign design, final StudyDesign.RunSlot slot, final Runnable configure)
			throws Exception
	{
		final int heapMb = RuntimeResourceSettings.currentMaxHeapMb();
		final StudyDesign.Cell cell = slot.cell;
		final int index = ++sequence;
		final ExperimentObservatoryEngine.RunSpec spec = new ExperimentObservatoryEngine.RunSpec(
				index,
				cell.algorithm,
				cell.algorithmName(),
				cell.cluster.getNodeCount(),
				design.getTargets(),
				cell.policy.getLabel(),
				false,
				false,
				Math.max(1, slot.repetition),
				design.getK(),
				heapMb,
				"",
				"");
		onEdt(new Runnable()
		{
			public void run()
			{
				gui.applyObservatoryRunSpec(spec);
				configure.run();
			}
		});
		final String automationId = "study-" + index + "-" + System.nanoTime();
		final CountDownLatch initialized = new CountDownLatch(1);
		final CountDownLatch finished = new CountDownLatch(1);
		final String[] failure = new String[1];
		ControllerAgentGui.ObservatoryAutomationListener listener =
				new ControllerAgentGui.ObservatoryAutomationListener()
		{
			public void initializationFinished(String runId)
			{
				initialized.countDown();
			}

			public void simulationFinished(String runId, String message)
			{
				finished.countDown();
			}

			public void automationFailed(String runId, String phase, String message)
			{
				failure[0] = phase + ": " + message;
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
			await(initialized, failure, "initialization");
			//The run this driver asked for, never a superseded one still winding down.
			String runId = gui.getCurrentRunId();
			SchedulingRunContext context = SchedulingRuntime.get(runId);
			onEdt(new Runnable()
			{
				public void run()
				{
					gui.startSimulation();
				}
			});
			await(finished, failure, "simulation");
			if (runId == null)
			{
				throw new IllegalStateException("The run registered no scheduling context.");
			}
			return runId;
		}
		finally
		{
			gui.endObservatoryAutomation(automationId, listener);
		}
	}

	private static void await(CountDownLatch latch, String[] failure, String phase) throws InterruptedException
	{
		if (!latch.await(RUN_TIMEOUT_MS, TimeUnit.MILLISECONDS))
		{
			throw new IllegalStateException("Timed out waiting for " + phase + ".");
		}
		if (failure[0] != null)
		{
			throw new IllegalStateException(failure[0]);
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
