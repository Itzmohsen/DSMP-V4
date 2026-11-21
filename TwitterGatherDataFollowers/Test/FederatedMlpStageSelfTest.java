package TwitterGatherDataFollowers.userRyersonU;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * The federated MLP on clusters: whatever the node count, the scheduling
 * policy, the nodes' speeds and lanes, injected faults or a scripted crash and
 * drain, every run must end in the state and the scores of the one-thread
 * reference, bit for bit, and must do each client's work exactly once.
 *
 * Nothing here asserts a time: only results and counts.
 */
public final class FederatedMlpStageSelfTest
{
	private static final long TIMEOUT_SECONDS = 240L;

	private FederatedMlpStageSelfTest()
	{
	}

	public static void main(String[] args) throws Exception
	{
		FederatedMlpSelfTest.Fixture fixture = new FederatedMlpSelfTest.Fixture(90, 3, 17L);
		//One held-out model (few targets) and five folds (every user a target).
		check("one model", fixture, fixture.users.subList(0, 9),
				FederatedMlpSelfTest.config(5, 4, 2, 1.0, false, 0.0, 0, 3L), true);
		check("five folds", fixture, fixture.users,
				FederatedMlpSelfTest.config(4, 3, 1, 1.0, false, 0.0, 0, 3L), false);
		//Sampled clients, skewed clients, a proximal term and early stopping together.
		check("sampled and skewed", fixture, fixture.users.subList(0, 9),
				FederatedMlpSelfTest.config(6, 8, 1, 0.5, true, 0.05, 2, 9L), false);
		nodesMustAgreeOnTheFederation(fixture);
		System.out.println("FederatedMlpStageSelfTest passed");
	}

	private static void check(String name, FederatedMlpSelfTest.Fixture fixture, List<String> targets,
			FederatedMlp.Config config, boolean everyPolicy) throws Exception
	{
		FederatedMlp.Inputs inputs = fixture.inputs(targets, config);
		FederatedMlp.Outcome reference = FederatedMlp.runSequential(FederatedMlp.Plan.build(inputs));
		int expectedUnits = 0;
		for (FederatedMlp.RoundRecord record : reference.state.history())
		{
			expectedUnits += record.participants;
		}
		require(expectedUnits > 0, name + ": the reference federation trained nobody");

		NodeCapacityProfile.Role normal = NodeCapacityProfile.Role.NORMAL;
		NodeCapacityProfile.Role strong = NodeCapacityProfile.Role.STRONG;
		NodeCapacityProfile.Role weak = NodeCapacityProfile.Role.WEAK;
		NodeCapacityProfile.Role unreliable = NodeCapacityProfile.Role.UNRELIABLE;

		verify(name + ", 1 node unemulated", reference, expectedUnits, run(inputs,
				SchedulingRunContext.Mode.BASELINE, SchedulerLabEngine.SchedulingPolicy.ORIGINAL_DSMP,
				profiles(normal), Scenario.NONE));
		verify(name + ", 3 nodes unemulated", reference, expectedUnits, run(inputs,
				SchedulingRunContext.Mode.BASELINE, SchedulerLabEngine.SchedulingPolicy.ORIGINAL_DSMP,
				profiles(normal, normal, normal), Scenario.NONE));
		verify(name + ", 2 equal nodes, equal split", reference, expectedUnits, run(inputs,
				SchedulingRunContext.Mode.EMULATED, SchedulerLabEngine.SchedulingPolicy.STATIC_EQUAL_SPLIT,
				profiles(normal, normal), Scenario.NONE));
		//A strong node has two lanes: two clients train at once on one node.
		verify(name + ", strong/normal/weak, capacity-weighted", reference, expectedUnits, run(inputs,
				SchedulingRunContext.Mode.EMULATED, SchedulerLabEngine.SchedulingPolicy.CAPACITY_WEIGHTED,
				profiles(strong, normal, weak), Scenario.NONE));
		verify(name + ", 4 nodes, work stealing", reference, expectedUnits, run(inputs,
				SchedulingRunContext.Mode.EMULATED, SchedulerLabEngine.SchedulingPolicy.WORK_STEALING,
				profiles(strong, normal, normal, strong), Scenario.NONE));
		//More nodes than clients in a round: some nodes train nothing and still take part.
		verify(name + ", 8 nodes, queue length", reference, expectedUnits, run(inputs,
				SchedulingRunContext.Mode.EMULATED, SchedulerLabEngine.SchedulingPolicy.QUEUE_LENGTH,
				profiles(normal, normal, normal, normal, normal, normal, normal, normal), Scenario.NONE));
		//Transient faults and crashes with repairs: units are lost and run again.
		verify(name + ", the unreliable preset", reference, expectedUnits, run(inputs,
				SchedulingRunContext.Mode.FAULT_INJECTION,
				SchedulerLabEngine.SchedulingPolicy.FAILURE_AWARE_RESCHEDULING,
				profiles(normal, unreliable, unreliable), Scenario.NONE));
		//A third of all executions on two nodes are lost, and both crash about once a second.
		ArrayList<NodeCapacityProfile> flaky = new ArrayList<NodeCapacityProfile>(profiles(normal, normal, normal));
		flaky.set(1, flaky(2, 3));
		flaky.set(2, flaky(3, 3));
		ClusterRun faulty = run(inputs, SchedulingRunContext.Mode.FAULT_INJECTION,
				SchedulerLabEngine.SchedulingPolicy.WORK_STEALING, flaky, Scenario.NONE);
		verify(name + ", heavy faults and crashes", reference, expectedUnits, faulty);
		require(faulty.revoked > 0, name + ": the heavy-fault run lost no execution, so it tested nothing");
		verify(name + ", scripted crash, drain and policy switch", reference, expectedUnits, run(inputs,
				SchedulingRunContext.Mode.EMULATED, SchedulerLabEngine.SchedulingPolicy.CAPACITY_WEIGHTED,
				profiles(normal, normal, normal), Scenario.parse(
						"name: federated crash and drain\n"
						+ "at 40% of " + RecommendationWorkQueueRegistry.mlpFederatedRound(1) + " crash node 2 for 150ms\n"
						+ "when " + RecommendationWorkQueueRegistry.mlpFederatedRound(2) + " opens drain node 3 to node 1\n"
						+ "when " + RecommendationWorkQueueRegistry.mlpFederatedRound(3) + " opens policy work stealing\n"
						+ "when " + RecommendationWorkQueueRegistry.MLP_FEDERATED_SCORING + " opens pause node 1\n"
						+ "when " + RecommendationWorkQueueRegistry.MLP_FEDERATED_SCORING + " opens + 50ms resume node 1\n")));
		if (everyPolicy)
		{
			for (SchedulerLabEngine.SchedulingPolicy policy : SchedulerLabEngine.SchedulingPolicy.values())
			{
				verify(name + ", " + policy.getLabel(), reference, expectedUnits, run(inputs,
						SchedulingRunContext.Mode.EMULATED, policy, profiles(strong, normal, normal),
						Scenario.NONE));
			}
		}
	}

	/** What one run left on its nodes. */
	private static final class ClusterRun
	{
		private final List<FederatedMlpStage.NodeResult> nodes = new ArrayList<FederatedMlpStage.NodeResult>();
		private int phases;
		private int revoked;
	}

	private static int runCounter;

	private static ClusterRun run(final FederatedMlp.Inputs inputs, SchedulingRunContext.Mode mode,
			SchedulerLabEngine.SchedulingPolicy policy, List<NodeCapacityProfile> profiles, Scenario scenario)
			throws Exception
	{
		final int nodeCount = profiles.size();
		//A fixed name per run keeps the injected fault pattern the same on every execution of the test.
		final SchedulingRunContext context = new SchedulingRunContext(
				"federated-stage-self-test-" + (++runCounter),
				mode,
				policy,
				profiles,
				nodeCount,
				RecommendationModeSupport.MLP,
				ClusterSettings.defaults().withSeed(20261120L),
				scenario,
				RecommendationSchedulerRegistry.MAX_FAILURES_PER_UNIT,
				120000L,
				0);
		ExecutorService executor = Executors.newFixedThreadPool(nodeCount);
		ClusterRun result = new ClusterRun();
		try
		{
			context.start();
			ArrayList<Future<FederatedMlpStage.NodeResult>> futures =
					new ArrayList<Future<FederatedMlpStage.NodeResult>>();
			for (int node = 1; node <= nodeCount; node++)
			{
				final int index = node;
				futures.add(executor.submit(new Callable<FederatedMlpStage.NodeResult>()
				{
					public FederatedMlpStage.NodeResult call()
					{
						return FederatedMlpStage.run(context, index, "Node" + index, inputs);
					}
				}));
			}
			for (Future<FederatedMlpStage.NodeResult> future : futures)
			{
				result.nodes.add(future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
			}
			result.phases = context.phases().size();
			for (RunRecorder.UnitExecution execution : context.recorder().executions())
			{
				result.revoked += "committed".equals(execution.outcome) ? 0 : 1;
			}
		}
		finally
		{
			context.close("self-test finished");
			executor.shutdownNow();
		}
		return result;
	}

	private static void verify(String name, FederatedMlp.Outcome reference, int expectedUnits, ClusterRun run)
	{
		FederatedMlp.Outcome outcome = null;
		int units = 0;
		for (FederatedMlpStage.NodeResult node : run.nodes)
		{
			require(node.state.fingerprint().equals(reference.state.fingerprint()),
					name + ": a node ended in another state than the reference");
			units += node.clientUnits;
			if (node.outcome != null)
			{
				require(outcome == null, name + ": two nodes hold the scores");
				outcome = node.outcome;
			}
		}
		require(outcome != null, name + ": no node holds the scores");
		require(units == expectedUnits,
				name + ": " + units + " client units were committed, the federation has " + expectedUnits);
		require(outcome.scoresFingerprint().equals(reference.scoresFingerprint()),
				name + ": the scores differ from the reference");
		require(outcome.report.equals(reference.report), name + ": the report differs from the reference:\n"
				+ outcome.report + "\n" + reference.report);
		require(outcome.correct == reference.correct && outcome.tested == reference.tested,
				name + ": the test accuracy differs from the reference");
		require(run.phases == reference.state.round + 1,
				name + ": " + run.phases + " phases for " + reference.state.round + " round(s) and the scoring");
		System.out.println("ok  " + name + " (" + run.nodes.size() + " node(s), " + units + " client units"
				+ (run.revoked > 0 ? ", " + run.revoked + " executions lost and redone" : "") + ")");
	}

	/** Nodes whose inputs differ must stop at the plan, not train two federations. */
	private static void nodesMustAgreeOnTheFederation(FederatedMlpSelfTest.Fixture fixture) throws Exception
	{
		final FederatedMlp.Config config = FederatedMlpSelfTest.config(4, 2, 1, 1.0, false, 0.0, 0, 3L);
		final FederatedMlp.Inputs first = fixture.inputs(fixture.users.subList(0, 9), config);
		final FederatedMlp.Inputs second = fixture.inputs(fixture.users.subList(0, 8), config);
		final SchedulingRunContext context = new SchedulingRunContext(
				"federated-stage-self-test-disagreement",
				SchedulingRunContext.Mode.EMULATED,
				SchedulerLabEngine.SchedulingPolicy.CAPACITY_WEIGHTED,
				profiles(NodeCapacityProfile.Role.NORMAL, NodeCapacityProfile.Role.NORMAL),
				2,
				RecommendationModeSupport.MLP,
				ClusterSettings.defaults(),
				Scenario.NONE,
				3,
				60000L,
				0);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try
		{
			context.start();
			ArrayList<Future<FederatedMlpStage.NodeResult>> futures =
					new ArrayList<Future<FederatedMlpStage.NodeResult>>();
			for (int node = 1; node <= 2; node++)
			{
				final int index = node;
				futures.add(executor.submit(new Callable<FederatedMlpStage.NodeResult>()
				{
					public FederatedMlpStage.NodeResult call()
					{
						return FederatedMlpStage.run(context, index, "Node" + index, index == 1 ? first : second);
					}
				}));
			}
			for (Future<FederatedMlpStage.NodeResult> future : futures)
			{
				String message = "";
				try
				{
					future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
				}
				catch (ExecutionException failure)
				{
					message = String.valueOf(failure.getCause().getMessage());
				}
				require(message.contains("another federation"),
						"nodes with different targets must be stopped at the plan, got: " + message);
			}
			require(context.phases().isEmpty(), "no round may open when the nodes disagree");
		}
		finally
		{
			context.close("self-test finished");
			executor.shutdownNow();
		}
		System.out.println("ok  nodes with different inputs are stopped before any round");
	}

	/** A node that loses 35% of its executions and crashes about once a second for a tenth of one. */
	private static NodeCapacityProfile flaky(int index, int nodes)
	{
		NodeCapacityProfile normal = NodeCapacityProfile.rolePreset(index, NodeCapacityProfile.Role.NORMAL, nodes, 4096);
		return new NodeCapacityProfile(index, NodeCapacityProfile.Role.UNRELIABLE, 1.0,
				normal.getMemoryLimitMb(), 1, 0, 2, false, 1.0, true, 35, 1000.0, 1.0, 0.1);
	}

	private static List<NodeCapacityProfile> profiles(NodeCapacityProfile.Role... roles)
	{
		ArrayList<NodeCapacityProfile> profiles = new ArrayList<NodeCapacityProfile>();
		for (int index = 0; index < roles.length; index++)
		{
			profiles.add(NodeCapacityProfile.rolePreset(index + 1, roles[index], roles.length, 4096));
		}
		return profiles;
	}

	private static void require(boolean condition, String message)
	{
		if (!condition)
		{
			throw new IllegalStateException(message);
		}
	}
}
