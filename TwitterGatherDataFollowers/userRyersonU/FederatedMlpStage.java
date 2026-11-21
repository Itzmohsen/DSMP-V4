package TwitterGatherDataFollowers.userRyersonU;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Runs a {@link FederatedMlp} federation on the nodes of a run.
 *
 * Every node calls {@link #run} with the same inputs, and the nodes then move
 * in step:
 * <ol>
 * <li><b>Plan.</b> A collective in which every node submits the fingerprint of
 *     its inputs; the coordinator checks that they agree and plans the
 *     federation once, so all nodes hold the same clients.</li>
 * <li><b>Rounds.</b> Each round is a work phase with one unit per
 *     participating client, placed and moved by the run's scheduling policy
 *     like any other unit, followed by a collective: every node uploads the
 *     updates of the clients it trained, the coordinator averages them into
 *     the global models and validates those, and every node downloads the new
 *     models and learns which folds go on.</li>
 * <li><b>Scoring.</b> A phase with one unit that measures test accuracy and
 *     scores every target; the node that commits it holds the result, as the
 *     central MLP's single unit does.</li>
 * </ol>
 *
 * A client unit is a pure function of the round's state, so a unit that is
 * retried, drained, stolen or lost in a crash produces the same update
 * wherever it finally commits, and the dispatcher commits it exactly once. The
 * coordinator refuses a round with a missing or doubled update. The models and
 * scores are therefore those of {@link FederatedMlp#runSequential} for every
 * node count, policy, capacity profile and fault pattern; only time differs.
 *
 * On the emulated network a round costs what it would cost a real federation:
 * each update is charged at its wire size on its node's uplink and every new
 * global model on every node's downlink (see {@link RunCollectives}).
 */
final class FederatedMlpStage
{
	/** The collective that plans the federation. */
	static final String PLAN_STEP = RecommendationWorkQueueRegistry.MLP_FEDERATED + "/plan";

	private FederatedMlpStage()
	{
	}

	/** What a federation left on one node. */
	static final class NodeResult
	{
		/** The scores and the report; null on every node but the one that committed the scoring unit. */
		final FederatedMlp.Outcome outcome;
		final FederatedMlp.Plan plan;
		/** The federation's final state, identical on every node. */
		final FederatedMlp.State state;
		/** Host time this node spent in its client units and in the scoring unit. */
		final long trainNanos;
		final long testNanos;
		/** Client units this node committed over all rounds. */
		final int clientUnits;

		private NodeResult(FederatedMlp.Outcome outcome, FederatedMlp.Plan plan, FederatedMlp.State state,
				long trainNanos, long testNanos, int clientUnits)
		{
			this.outcome = outcome;
			this.plan = plan;
			this.state = state;
			this.trainNanos = trainNanos;
			this.testNanos = testNanos;
			this.clientUnits = clientUnits;
		}
	}

	/** One node's upload in a round: the updates of the clients it trained, by unit. */
	private static final class Updates implements PayloadSize.Sized
	{
		private final TreeMap<Integer,SparseFederatedMlpModelSupport.ModelUpdate> byUnit;

		private Updates(Map<Integer,SparseFederatedMlpModelSupport.ModelUpdate> byUnit)
		{
			this.byUnit = new TreeMap<Integer,SparseFederatedMlpModelSupport.ModelUpdate>(byUnit);
		}

		public long payloadBytes()
		{
			long total = 4L;
			for (SparseFederatedMlpModelSupport.ModelUpdate update : byUnit.values())
			{
				total += 4L + update.payloadBytes();
			}
			return total;
		}
	}

	/**
	 * This node's part of the federation; returns when the whole run's
	 * federation has finished.
	 *
	 * @param owner the node's name in logs and in the memory gate
	 */
	static NodeResult run(final SchedulingRunContext context, final int node, final String owner,
			final FederatedMlp.Inputs inputs)
	{
		final FederatedMlp.Config config = inputs.config;
		final RunCollectives collectives = context.collectives();
		int lanes = Math.max(1, context.lanesOf(node));
		//The nodes meet in a collective after every round, so they enter together.
		try (RecommendationMemoryCoordinator.Lease ignored = RecommendationMemoryCoordinator.acquireTogether(
				context.getRunId() + "/federated-mlp",
				owner,
				"Federated MLP training",
				RecommendationMemoryCoordinator.Workload.MLP_TRAIN,
				RecommendationMemoryCoordinator.estimateFederatedMlpNodeBytes(
						inputs.vocabulary.size(),
						config.hiddenNeurons,
						config.hiddenLayers,
						inputs.classNames.length,
						inputs.corpus.nonZeroCount(),
						inputs.corpus.size(),
						inputs.foldCount(),
						config.clients,
						lanes,
						context.getNodeCount())))
		{
			final String fingerprint = inputs.fingerprint();
			final FederatedMlp.Plan plan = collectives.allReduce(PLAN_STEP, node, fingerprint,
					new RunCollectives.Reducer<FederatedMlp.Plan>()
					{
						public FederatedMlp.Plan reduce(List<Object> contributionsByNode)
						{
							for (int index = 0; index < contributionsByNode.size(); index++)
							{
								if (!fingerprint.equals(contributionsByNode.get(index)))
								{
									throw new IllegalStateException("Node " + (index + 1)
											+ " would train another federation than node " + node
											+ ": their targets, splits, vocabulary or settings differ.");
								}
							}
							return FederatedMlp.Plan.build(inputs);
						}
					});
			recordProperties(context, plan);
			final AtomicLong trainNanos = new AtomicLong();
			int clientUnits = 0;
			FederatedMlp.State state = plan.initialState();
			while (plan.hasWork(state))
			{
				final FederatedMlp.State before = state;
				final int round = before.round + 1;
				final List<FederatedMlp.Task> tasks = plan.tasks(before, round);
				String key = RecommendationWorkQueueRegistry.mlpFederatedRound(round);
				WorkPhase phase = context.openPhase(
						key,
						"Federated MLP round " + round + " of " + config.rounds,
						new SchedulingRunContext.UnitSupplier()
						{
							public List<WorkUnitSpec> units()
							{
								ArrayList<WorkUnitSpec> units = new ArrayList<WorkUnitSpec>();
								for (FederatedMlp.Task task : tasks)
								{
									FederatedMlp.Client client = plan.client(task);
									units.add(new WorkUnitSpec(task.unit, client.key(), client.costMs));
								}
								return units;
							}
						});
				final TreeMap<Integer,SparseFederatedMlpModelSupport.ModelUpdate> trained =
						new TreeMap<Integer,SparseFederatedMlpModelSupport.ModelUpdate>();
				NodeWorkRunner.run(
						context,
						phase,
						node,
						new NodeWorkRunner.UnitExecutor()
						{
							public NodeWorkRunner.UnitOutput execute(
									WorkDispatcher.Assignment assignment, int lane)
							{
								UnitCancellation.check();
								long started = System.nanoTime();
								SparseFederatedMlpModelSupport.ModelUpdate update =
										plan.train(before, round, tasks.get(assignment.getUnitId() - 1));
								trainNanos.addAndGet(System.nanoTime() - started);
								return NodeWorkRunner.UnitOutput.measured(update);
							}
						},
						new NodeWorkRunner.CommitSink()
						{
							public void commit(WorkDispatcher.Assignment assignment, Object value)
							{
								trained.put(Integer.valueOf(assignment.getUnitId()),
										(SparseFederatedMlpModelSupport.ModelUpdate)value);
							}
						});
				clientUnits += trained.size();
				state = collectives.allReduce(key + "/aggregate", node, new Updates(trained),
						new RunCollectives.Reducer<FederatedMlp.State>()
						{
							public FederatedMlp.State reduce(List<Object> contributionsByNode)
							{
								TreeMap<Integer,SparseFederatedMlpModelSupport.ModelUpdate> all =
										new TreeMap<Integer,SparseFederatedMlpModelSupport.ModelUpdate>();
								for (Object contribution : contributionsByNode)
								{
									if (contribution == null)
									{
										continue;
									}
									for (Map.Entry<Integer,SparseFederatedMlpModelSupport.ModelUpdate> entry
											: ((Updates)contribution).byUnit.entrySet())
									{
										if (all.put(entry.getKey(), entry.getValue()) != null)
										{
											throw new IllegalStateException("Client unit " + entry.getKey()
													+ " of federated round " + round + " was committed twice.");
										}
									}
								}
								return plan.aggregate(before, round, tasks, all);
							}
						});
			}
			final FederatedMlp.State last = state;
			WorkPhase scoring = context.openPhase(
					RecommendationWorkQueueRegistry.MLP_FEDERATED_SCORING,
					"Federated MLP scoring",
					new SchedulingRunContext.UnitSupplier()
					{
						public List<WorkUnitSpec> units()
						{
							//As the central MLP's unit: on node 1 in the original DSMP
							//baseline, where the policy places it otherwise.
							return Collections.singletonList(new WorkUnitSpec(
									1, "score", scoringCostMs(plan), 0, 0, Double.POSITIVE_INFINITY, 1, null));
						}
					});
			final FederatedMlp.Outcome[] committed = new FederatedMlp.Outcome[1];
			final AtomicLong testNanos = new AtomicLong();
			NodeWorkRunner.run(
					context,
					scoring,
					node,
					new NodeWorkRunner.UnitExecutor()
					{
						public NodeWorkRunner.UnitOutput execute(WorkDispatcher.Assignment assignment, int lane)
						{
							UnitCancellation.check();
							long started = System.nanoTime();
							FederatedMlp.Outcome outcome = plan.finish(last);
							testNanos.set(System.nanoTime() - started);
							return NodeWorkRunner.UnitOutput.measured(outcome);
						}
					},
					new NodeWorkRunner.CommitSink()
					{
						public void commit(WorkDispatcher.Assignment assignment, Object value)
						{
							committed[0] = (FederatedMlp.Outcome)value;
						}
					});
			return new NodeResult(committed[0], plan, last, trainNanos.get(),
					committed[0] == null ? 0L : testNanos.get(), clientUnits);
		}
	}

	/** Estimated reference work of the scoring unit: one forward pass per test user and target. */
	private static double scoringCostMs(FederatedMlp.Plan plan)
	{
		double perRow = FederatedMlp.COST_ROW_MS
				+ FederatedMlp.COST_FORWARD_MS * averageRowSize(plan) * plan.config().hiddenNeurons;
		double cost = 1.0;
		for (int index = 0; index < plan.foldCount(); index++)
		{
			FederatedMlp.Fold fold = plan.fold(index);
			cost += (fold.testUsers() + fold.targetUsers()) * perRow;
		}
		return cost;
	}

	private static double averageRowSize(FederatedMlp.Plan plan)
	{
		long nonZeros = 0L;
		long rows = 0L;
		for (int index = 0; index < plan.foldCount(); index++)
		{
			FederatedMlp.Fold fold = plan.fold(index);
			for (int client = 0; client < fold.clientCount(); client++)
			{
				nonZeros += fold.client(client).nonZeros;
				rows += fold.client(client).samples();
			}
		}
		return rows == 0L ? 1.0 : (double)nonZeros / rows;
	}

	/** What the run's record says about the federation, so results can be told apart and reproduced. */
	private static void recordProperties(SchedulingRunContext context, FederatedMlp.Plan plan)
	{
		FederatedMlp.Config config = plan.config();
		RunRecorder recorder = context.recorder();
		recorder.property("mlpTraining", AlgorithmParameterSettings.MLP_TRAINING_FEDERATED);
		recorder.property("mlpFederatedClients", Integer.toString(config.clients));
		recorder.property("mlpFederatedRounds", Integer.toString(config.rounds));
		recorder.property("mlpFederatedLocalEpochs", Integer.toString(config.localEpochs));
		recorder.property("mlpFederatedClientFraction", Double.toString(config.clientFraction));
		recorder.property("mlpFederatedPartition", config.labelSkew
				? AlgorithmParameterSettings.MLP_FEDERATED_PARTITION_DIRICHLET + " alpha "
						+ Double.toString(config.dirichletAlpha)
				: AlgorithmParameterSettings.MLP_FEDERATED_PARTITION_IID);
		recorder.property("mlpFederatedProximalMu", Double.toString(config.proximalMu));
		recorder.property("mlpFederatedPatience", Integer.toString(config.patience));
		recorder.property("mlpFederatedSeed", Long.toString(config.seed));
	}
}
