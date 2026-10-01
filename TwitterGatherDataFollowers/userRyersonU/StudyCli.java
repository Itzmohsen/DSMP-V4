package TwitterGatherDataFollowers.userRyersonU;

import java.io.File;
import java.net.ServerSocket;
import java.util.Locale;

/**
 * Runs a node-lab study without the window: boots the JADE platform and the
 * simulator hidden, runs every slot of the study file, and writes the study
 * to {@code Results/Studies}.
 *
 * Usage: {@code StudyCli <study file>} (see {@link StudyDesign} for the
 * format). Exit status 0 when every run completed and every algorithm's runs
 * produced identical recommendations, 1 otherwise.
 */
public final class StudyCli
{
	private StudyCli()
	{
	}

	public static void main(String[] args) throws Exception
	{
		if (args.length < 1)
		{
			System.err.println("usage: StudyCli <study file>");
			System.exit(2);
		}
		System.setProperty("dsmp.gui.hidden", "true");
		int heapMb = RuntimeResourceSettings.currentMaxHeapMb();
		StudyDesign design = StudyDesign.load(new File(args[0]), heapMb);

		jade.core.Runtime runtime = jade.core.Runtime.instance();
		runtime.setCloseVM(false);
		jade.core.Profile profile = new jade.core.ProfileImpl();
		profile.setParameter(jade.core.Profile.LOCAL_HOST, "localhost");
		profile.setParameter(jade.core.Profile.LOCAL_PORT, Integer.toString(freePort()));
		profile.setParameter(jade.core.Profile.GUI, "false");
		profile.setParameter("nomtp", "true");
		long queueBytes = Math.max(32L, Math.min(256L, Runtime.getRuntime().maxMemory() / 4L / (1L << 20)))
				* (1L << 20);
		profile.setParameter("jade_domain_df_maxresult", "100000");
		profile.setParameter("jade_domain_df_autocleanup", "true");
		profile.setParameter("jade_core_messaging_MessageManager_poolsize", "10");
		profile.setParameter("jade_core_messaging_MessageManager_maxqueuesize", Long.toString(queueBytes));
		profile.setParameter("jade_core_messaging_MessageManager_deliverytimethreshold", "10000");
		jade.wrapper.ContainerController main = runtime.createMainContainer(profile);
		main.createNewAgent("controller", ControllerAgent.class.getName(), null).start();
		ControllerAgentGui gui = awaitGui();

		StudyRunner runner = new StudyRunner(design, new GuiStudyDriver(gui), new StudyRunner.Progress()
		{
			public void runStarting(int index, int total, StudyDesign.RunSlot slot)
			{
				System.out.println(String.format(Locale.US, "[study] run %d/%d: %s, %s", index, total,
						slot.cell.label(), slot.warmup ? "warm-up" : "repetition " + slot.repetition));
			}

			public void runFinished(int index, int total, StudyResult.RunOutcome outcome)
			{
				System.out.println(String.format(Locale.US, "[study]   %s in %.1f s: makespan %s ms%s",
						outcome.status, outcome.wallSeconds, StudyResult.format(outcome.metric("makespan_ms")),
						outcome.error.length() == 0 ? "" : " (" + outcome.error + ")"));
			}

			public void message(String text)
			{
				System.out.println("[study] " + text);
			}
		}, heapMb);
		StudyResult result = runner.run();
		// The runner rewrites the study after every run and reports where; this
		// last write only makes a failure to write fail the command.
		result.write();
		boolean ok = result.failedRuns() == 0 && result.isExact();
		System.out.println(ok ? "[study] passed: every run completed; results are exact."
				: "[study] finished with " + result.failedRuns() + " failed run(s)"
						+ (result.isExact() ? "" : " and DIFFERENT results for the same algorithm"));
		try
		{
			main.kill();
		}
		catch (Exception ignored)
		{
		}
		System.exit(ok ? 0 : 1);
	}

	private static ControllerAgentGui awaitGui() throws InterruptedException
	{
		long deadline = System.currentTimeMillis() + 120000L;
		while (ControllerAgentGui.latestInstance() == null)
		{
			if (System.currentTimeMillis() > deadline)
			{
				throw new IllegalStateException("the simulator window was not created");
			}
			Thread.sleep(100L);
		}
		return ControllerAgentGui.latestInstance();
	}

	private static int freePort() throws Exception
	{
		ServerSocket socket = new ServerSocket(0);
		try
		{
			return socket.getLocalPort();
		}
		finally
		{
			socket.close();
		}
	}
}
