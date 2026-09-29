package TwitterGatherDataFollowers.userRyersonU;

import java.io.File;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.swing.SwingUtilities;

/**
 * Live end-to-end exactness check of the whole simulator.
 *
 * Boots the real JADE multi-agent system with the simulator window hidden,
 * loads a dataset, and runs each chosen algorithm under a matrix of scheduler
 * configurations: one node in the original DSMP mode (the reference), more
 * nodes in the original mode, capacity-emulated runs with unequal node
 * profiles under several policies, and a fault-injection run. Every run's
 * final merged recommendations (each target's ranked candidates with their
 * scores) must be identical to the reference, bit for bit.
 *
 * Usage: LiveExactnessHarness &lt;dataset&gt; [algorithms] [targets] [runs] [target mode]
 * where algorithms is a comma list of SIMILARITY, COMMON_NEIGHBORS, KMEANS,
 * KMEANS_EUCLIDEAN, SVM, MLP, DOC2VEC, TGF (default: all but DOC2VEC and
 * MLP), targets is the number of recommendation targets for SVM and Doc2Vec,
 * runs is a comma list of configuration numbers to keep besides the reference
 * (default: all), and target mode is "Similar Users" or "Followee Accounts"
 * (default: each algorithm's own).
 * Run it from a scratch working directory: the simulator writes its reports
 * into the working directory.
 */
public final class LiveExactnessHarness
{
	private static final long USER_LOAD_TIMEOUT_MS = 10L * 60L * 1000L;
	private static final long RUN_TIMEOUT_MS = 60L * 60L * 1000L;
	/** Target mode label ("Similar Users", "Followee Accounts"); empty keeps the default. */
	private static String targetMode = "";

	/** One scheduler configuration of the matrix. */
	private static final class Configuration
	{
		final String name;
		final int nodes;
		final RecommendationSchedulerPlan.Mode mode;
		final SchedulerLabEngine.SchedulingPolicy policy;
		final NodeCapacityProfile.Role[] roles;
		boolean liveControls;

		Configuration(String name, int nodes, RecommendationSchedulerPlan.Mode mode,
				SchedulerLabEngine.SchedulingPolicy policy, NodeCapacityProfile.Role... roles)
		{
			this.name = name;
			this.nodes = nodes;
			this.mode = mode;
			this.policy = policy;
			this.roles = roles;
		}

		Configuration withLiveControls()
		{
			liveControls = true;
			return this;
		}
	}

	/**
	 * Operator actions applied while a run is in flight: an action window
	 * that slows claims, a pause and resume, a redirect, a drain, a capacity
	 * change and a policy switch. Actions a phase cannot take at that moment
	 * are refused by the scheduler and logged.
	 */
	private static final class LiveOperator extends Thread
	{
		private final String runId;
		private volatile boolean stopped;
		private final ArrayList<String> log = new ArrayList<String>();

		LiveOperator(String runId)
		{
			super("live-exactness-operator");
			setDaemon(true);
			this.runId = runId;
		}

		public void run()
		{
			step(0L, "action window 40 ms", new Runnable()
			{
				public void run()
				{
					LiveWorkloadAdjustmentRegistry.setPacingDelayMs(runId, 40);
				}
			});
			step(0L, "pause node 2", new Runnable()
			{
				public void run()
				{
					LiveWorkloadAdjustmentRegistry.setPaused(runId, 2, true);
				}
			});
			step(900L, "resume node 2", new Runnable()
			{
				public void run()
				{
					LiveWorkloadAdjustmentRegistry.setPaused(runId, 2, false);
				}
			});
			step(0L, "redirect 3 units and users to node 1", new Runnable()
			{
				public void run()
				{
					LiveWorkloadAdjustmentRegistry.redirectPendingToNode(runId, 1, 3);
				}
			});
			step(900L, "drain node 3 to node 1", new Runnable()
			{
				public void run()
				{
					LiveWorkloadAdjustmentRegistry.drainNodeTo(runId, 3, 1, 0);
				}
			});
			step(900L, "halve node 1's capacity", new Runnable()
			{
				public void run()
				{
					LiveWorkloadAdjustmentRegistry.setCapacityScale(runId, 1, 0.5);
				}
			});
			step(0L, "switch to work stealing", new Runnable()
			{
				public void run()
				{
					LiveWorkloadAdjustmentRegistry.setPolicyOverride(
							runId, SchedulerLabEngine.SchedulingPolicy.WORK_STEALING);
				}
			});
			step(900L, "node 3 available again", new Runnable()
			{
				public void run()
				{
					LiveWorkloadAdjustmentRegistry.setUnavailable(runId, 3, false);
				}
			});
			step(600L, "close the action window", new Runnable()
			{
				public void run()
				{
					LiveWorkloadAdjustmentRegistry.setPacingDelayMs(runId, 0);
				}
			});
		}

		private void step(long delayMs, String action, Runnable body)
		{
			try
			{
				Thread.sleep(delayMs);
			}
			catch (InterruptedException interrupted)
			{
				stopped = true;
			}
			if (stopped)
			{
				return;
			}
			try
			{
				body.run();
				log.add(action);
			}
			catch (RuntimeException refused)
			{
				log.add(action + " (refused: " + refused.getMessage() + ")");
			}
		}

		void finish()
		{
			stopped = true;
			interrupt();
		}

		List<String> getLog()
		{
			return log;
		}
	}

	private LiveExactnessHarness()
	{
	}

	public static void main(String[] args) throws Exception
	{
		if (args.length < 1)
		{
			throw new IllegalArgumentException(
					"usage: LiveExactnessHarness <dataset> [algorithms] [targets] [runs]");
		}
		System.setProperty("dsmp.gui.hidden", "true");
		File dataset = new File(args[0]).getAbsoluteFile();
		List<Integer> algorithms = parseAlgorithms(args.length > 1 ? args[1]
				: "SIMILARITY,COMMON_NEIGHBORS,KMEANS,KMEANS_EUCLIDEAN,SVM,TGF");
		int targets = args.length > 2 ? Integer.parseInt(args[2]) : 4;
		String keep = args.length > 3 ? args[3] : "";
		targetMode = args.length > 4 ? args[4] : "";
		List<Configuration> matrix = matrix(keep);

		jade.core.Runtime runtime = jade.core.Runtime.instance();
		runtime.setCloseVM(false);
		jade.core.Profile profile = new jade.core.ProfileImpl();
		profile.setParameter(jade.core.Profile.LOCAL_HOST, "localhost");
		profile.setParameter(jade.core.Profile.LOCAL_PORT, Integer.toString(freePort()));
		profile.setParameter(jade.core.Profile.GUI, "false");
		profile.setParameter("nomtp", "true");
		//The launcher's platform options (build.py), so agent churn between
		//runs behaves as in the application.
		long queueBytes = Math.max(32L, Math.min(256L, Runtime.getRuntime().maxMemory() / 4L / (1L << 20)))
				* (1L << 20);
		profile.setParameter("jade_domain_df_maxresult", "100000");
		profile.setParameter("jade_domain_df_autocleanup", "true");
		profile.setParameter("jade_core_messaging_MessageManager_poolsize", "10");
		profile.setParameter("jade_core_messaging_MessageManager_maxqueuesize", Long.toString(queueBytes));
		profile.setParameter("jade_core_messaging_MessageManager_deliverytimethreshold", "10000");
		jade.wrapper.ContainerController main = runtime.createMainContainer(profile);
		main.createNewAgent("controller", ControllerAgent.class.getName(), null).start();

		final ControllerAgentGui gui = awaitGui();
		final File datasetFile = dataset;
		onEdt(new Runnable()
		{
			public void run()
			{
				gui.loadImportedDataset(datasetFile, "live-exactness");
				gui.getUsers();
			}
		});
		awaitUsers(gui);
		System.out.println("[harness] loaded " + gui.loadedUserCount() + " users from " + dataset);

		int failures = 0;
		ArrayList<String> summary = new ArrayList<String>();
		int runIndex = 0;
		for (int algorithm : algorithms)
		{
			Map<String,List<RecommendationRunResults.Entry>> reference = null;
			String referenceName = null;
			for (Configuration configuration : matrix)
			{
				runIndex++;
				long start = System.nanoTime();
				String label = RecommendationModeSupport.algorithmName(algorithm) + " / " + configuration.name;
				Map<String,List<RecommendationRunResults.Entry>> result;
				try
				{
					result = run(gui, runIndex, algorithm, configuration, targets);
				}
				catch (Exception failure)
				{
					failures++;
					summary.add(String.format(Locale.US, "FAIL %-58s run failed: %s", label, failure.getMessage()));
					System.out.println("[verdict] " + summary.get(summary.size() - 1));
					System.out.println("[harness] " + label + " failed: " + failure);
					continue;
				}
				double seconds = (System.nanoTime() - start) / 1.0e9;
				if (reference == null)
				{
					reference = result;
					referenceName = configuration.name;
					summary.add(String.format(Locale.US, "REF  %-58s %6.1fs  %s", label, seconds, describe(result)));
					System.out.println("[verdict] " + summary.get(summary.size() - 1));
					continue;
				}
				String difference = compare(reference, result);
				if (difference == null)
				{
					summary.add(String.format(Locale.US, "PASS %-58s %6.1fs  identical to %s",
							label, seconds, referenceName));
					System.out.println("[verdict] " + summary.get(summary.size() - 1));
				}
				else
				{
					failures++;
					summary.add(String.format(Locale.US, "FAIL %-58s %6.1fs  %s", label, seconds, difference));
					System.out.println("[verdict] " + summary.get(summary.size() - 1));
				}
			}
		}
		System.out.println();
		System.out.println("[harness] ---------------- live exactness summary ----------------");
		for (String line : summary)
		{
			System.out.println(line);
		}
		System.out.println(failures == 0
				? "LiveExactnessHarness passed"
				: "LiveExactnessHarness FAILED: " + failures + " run(s) differ or failed");
		try
		{
			main.kill();
		}
		catch (Exception ignored)
		{
		}
		System.exit(failures == 0 ? 0 : 1);
	}

	private static List<Configuration> matrix(String keep)
	{
		NodeCapacityProfile.Role normal = NodeCapacityProfile.Role.NORMAL;
		NodeCapacityProfile.Role strong = NodeCapacityProfile.Role.STRONG;
		NodeCapacityProfile.Role weak = NodeCapacityProfile.Role.WEAK;
		NodeCapacityProfile.Role unreliable = NodeCapacityProfile.Role.UNRELIABLE;
		ArrayList<Configuration> all = new ArrayList<Configuration>();
		all.add(new Configuration("1 node, original DSMP", 1,
				RecommendationSchedulerPlan.Mode.DSMP_DEFAULT, null, normal));
		all.add(new Configuration("3 nodes, original DSMP", 3,
				RecommendationSchedulerPlan.Mode.DSMP_DEFAULT, null, normal, normal, normal));
		all.add(new Configuration("3 nodes, capacity-weighted, strong/normal/weak", 3,
				RecommendationSchedulerPlan.Mode.SCORING_SAFE,
				SchedulerLabEngine.SchedulingPolicy.CAPACITY_WEIGHTED, strong, normal, weak));
		all.add(new Configuration("4 nodes, work stealing, weak/strong/normal/weak", 4,
				RecommendationSchedulerPlan.Mode.SCORING_SAFE,
				SchedulerLabEngine.SchedulingPolicy.WORK_STEALING, weak, strong, normal, weak));
		all.add(new Configuration("2 nodes, queue length, fault injection", 2,
				RecommendationSchedulerPlan.Mode.SCHEDULING_EXPERIMENT,
				SchedulerLabEngine.SchedulingPolicy.QUEUE_LENGTH, unreliable, normal));
		all.add(new Configuration("3 nodes, queue length, live operator controls", 3,
				RecommendationSchedulerPlan.Mode.SCORING_SAFE,
				SchedulerLabEngine.SchedulingPolicy.QUEUE_LENGTH, normal, strong, weak).withLiveControls());
		if (keep == null || keep.trim().length() == 0)
		{
			return all;
		}
		ArrayList<Configuration> kept = new ArrayList<Configuration>();
		kept.add(all.get(0));
		for (String name : keep.split(","))
		{
			int index = Integer.parseInt(name.trim());
			if (index > 0 && index < all.size())
			{
				kept.add(all.get(index));
			}
		}
		return kept;
	}

	private static Map<String,List<RecommendationRunResults.Entry>> run(
			final ControllerAgentGui gui,
			int runIndex,
			int algorithm,
			final Configuration configuration,
			int targets) throws Exception
	{
		final int heapMb = RuntimeResourceSettings.currentMaxHeapMb();
		final ExperimentObservatoryEngine.RunSpec spec = new ExperimentObservatoryEngine.RunSpec(
				runIndex,
				algorithm,
				RecommendationModeSupport.algorithmName(algorithm),
				configuration.nodes,
				Math.max(1, targets),
				configuration.policy == null ? "DSMP default" : configuration.policy.getLabel(),
				false,
				false,
				1,
				10,
				heapMb,
				targetMode,
				"");
		onEdt(new Runnable()
		{
			public void run()
			{
				gui.applyObservatoryRunSpec(spec);
				ArrayList<NodeCapacityProfile> profiles = new ArrayList<NodeCapacityProfile>();
				for (int node = 1; node <= configuration.nodes; node++)
				{
					profiles.add(NodeCapacityProfile.rolePreset(
							node, configuration.roles[node - 1], configuration.nodes, heapMb));
				}
				NodeCapacityRegistry.setProfiles(profiles, heapMb);
				if (configuration.mode == RecommendationSchedulerPlan.Mode.DSMP_DEFAULT)
				{
					RecommendationSchedulerRegistry.restoreDefault(configuration.nodes, heapMb);
				}
				else
				{
					RecommendationSchedulerRegistry.apply(configuration.mode, configuration.policy,
							configuration.nodes, heapMb, "live exactness harness");
				}
			}
		});
		final String automationId = "live-exactness-" + runIndex;
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
			SchedulingRunContext context = SchedulingRuntime.latest();
			String runId = context == null ? null : context.getRunId();
			System.out.println("[harness] run " + runIndex + " (" + runId + "): "
					+ (context == null ? "no scheduling context" : context.describe()));
			LiveOperator operator = configuration.liveControls && runId != null
					? new LiveOperator(runId) : null;
			onEdt(new Runnable()
			{
				public void run()
				{
					gui.startSimulation();
				}
			});
			if (operator != null)
			{
				operator.start();
			}
			try
			{
				await(finished, failure, "simulation");
			}
			finally
			{
				if (operator != null)
				{
					operator.finish();
					System.out.println("[harness]   operator actions: " + operator.getLog());
					if (context != null)
					{
						for (SchedulingRunContext.ControlEvent event : context.events())
						{
							if (!"phase".equals(event.getAction()))
							{
								System.out.println("[harness]   control event: " + event.getAction()
										+ ": " + event.getDetail());
							}
						}
					}
				}
			}
			Map<String,List<RecommendationRunResults.Entry>> result = RecommendationRunResults.get(runId);
			if (result == null)
			{
				throw new IllegalStateException("the organizer recorded no results for run " + runId);
			}
			if (context != null)
			{
				for (String line : context.timingReport())
				{
					System.out.println("[harness]   " + line);
				}
			}
			return result;
		}
		finally
		{
			gui.endObservatoryAutomation(automationId, listener);
		}
	}

	/** Null when identical, else the first difference. */
	private static String compare(
			Map<String,List<RecommendationRunResults.Entry>> expected,
			Map<String,List<RecommendationRunResults.Entry>> actual)
	{
		if (!expected.keySet().equals(actual.keySet()))
		{
			return "targets differ: " + expected.keySet() + " vs " + actual.keySet();
		}
		for (Map.Entry<String,List<RecommendationRunResults.Entry>> row : expected.entrySet())
		{
			List<RecommendationRunResults.Entry> a = row.getValue();
			List<RecommendationRunResults.Entry> b = actual.get(row.getKey());
			if (a.size() != b.size())
			{
				return row.getKey() + ": " + a.size() + " vs " + b.size() + " ranked candidates";
			}
			for (int rank = 0; rank < a.size(); rank++)
			{
				RecommendationRunResults.Entry first = a.get(rank);
				RecommendationRunResults.Entry second = b.get(rank);
				if (!first.getCandidate().equals(second.getCandidate())
						|| Double.doubleToLongBits(first.getScore())
								!= Double.doubleToLongBits(second.getScore()))
				{
					return row.getKey() + " rank " + (rank + 1) + ": " + first + " vs " + second;
				}
			}
		}
		return null;
	}

	private static String describe(Map<String,List<RecommendationRunResults.Entry>> result)
	{
		int entries = 0;
		for (List<RecommendationRunResults.Entry> row : result.values())
		{
			entries += row.size();
		}
		StringBuilder builder = new StringBuilder();
		builder.append(result.size()).append(" target(s), ").append(entries).append(" ranked entries");
		for (Map.Entry<String,List<RecommendationRunResults.Entry>> row : result.entrySet())
		{
			builder.append("; ").append(row.getKey()).append(" -> ")
					.append(row.getValue().subList(0, Math.min(3, row.getValue().size())));
			break;
		}
		return builder.toString();
	}

	private static List<Integer> parseAlgorithms(String list)
	{
		LinkedHashMap<String,Integer> names = new LinkedHashMap<String,Integer>();
		names.put("SIMILARITY", Integer.valueOf(ControllerAgent.COS_SIM));
		names.put("COMMON_NEIGHBORS", Integer.valueOf(ControllerAgent.CommonNeighbors));
		names.put("KMEANS", Integer.valueOf(ControllerAgent.K_MEANS));
		names.put("KMEANS_EUCLIDEAN", Integer.valueOf(ControllerAgent.K_MEANSEUCLIDEAN));
		names.put("SVM", Integer.valueOf(ControllerAgent.SVM));
		names.put("SCALABLE_SVM", Integer.valueOf(ControllerAgent.SCALABLE_SVM));
		names.put("MLP", Integer.valueOf(ControllerAgent.MLP));
		names.put("DOC2VEC", Integer.valueOf(ControllerAgent.Doc2Vec));
		names.put("TGF", Integer.valueOf(ControllerAgent.TEMPORAL_GRAPH_FOUNDATION));
		ArrayList<Integer> algorithms = new ArrayList<Integer>();
		for (String name : list.split(","))
		{
			Integer algorithm = names.get(name.trim().toUpperCase(Locale.US));
			if (algorithm == null)
			{
				throw new IllegalArgumentException("unknown algorithm " + name + "; use " + names.keySet());
			}
			algorithms.add(algorithm);
		}
		return algorithms;
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

	private static void awaitUsers(ControllerAgentGui gui) throws InterruptedException
	{
		long deadline = System.currentTimeMillis() + USER_LOAD_TIMEOUT_MS;
		while (!(gui.isReadyToInitialize() && gui.loadedUserCount() > 0))
		{
			if (System.currentTimeMillis() > deadline)
			{
				throw new IllegalStateException("users were not loaded in time");
			}
			Thread.sleep(200L);
		}
	}

	private static void await(CountDownLatch latch, String[] failure, String phase)
			throws InterruptedException
	{
		if (!latch.await(RUN_TIMEOUT_MS, TimeUnit.MILLISECONDS))
		{
			throw new IllegalStateException("timed out waiting for " + phase);
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
