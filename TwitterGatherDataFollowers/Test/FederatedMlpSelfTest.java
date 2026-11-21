package TwitterGatherDataFollowers.userRyersonU;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Federated averaging of the softmax MLP, without a cluster: the kernel
 * against the central engine bit for bit, the average against its definition,
 * the client partitions, the plan against the central split, and the whole
 * federation's determinism and learning.
 */
public final class FederatedMlpSelfTest
{
	private FederatedMlpSelfTest()
	{
	}

	public static void main(String[] args) throws Exception
	{
		kernelMatchesTheCentralEngineBitForBit();
		localTrainingLeavesTheGlobalModelAlone();
		averageMatchesItsDefinition();
		oneClientRoundIsItsOwnTraining();
		iidPartitionIsEvenAndStratified();
		dirichletPartitionIsSkewedAndComplete();
		apportionAddsUp();
		planMirrorsTheCentralSplit();
		federationIsDeterministicAndOrderFree();
		federationLearns();
		everyTargetIsScoredInFolds();
		earlyStoppingKeepsTheBestRound();
		partialParticipationSamplesClients();
		proximalTermHoldsClientsBack();
		inputsFingerprintSeesEveryInput();
		reportsAreKeptPerRunAndWritten();
		System.out.println("FederatedMlpSelfTest passed");
	}

	// ------------------------------------------------------------- kernel

	private static void kernelMatchesTheCentralEngineBitForBit()
	{
		for (int layers = 1; layers <= 3; layers++)
		{
			for (double[] regularisation : new double[][] {{0.0, 0.0}, {0.001, 0.0}, {0.0005, 0.07}})
			{
				Random random = new Random(41L + layers);
				int features = 60;
				int classes = 4;
				List<SparseFederatedMlpModelSupport.SparseRow> rows = randomRows(random, 37, features, classes);
				List<SparseFederatedMlpModelSupport.TrainingExample> examples = examplesOf(rows);
				SparseFederatedMlpModelSupport.SparseMlpModel central =
						SparseFederatedMlpModelSupport.SparseMlpModel.create(features, layers, 7, classes, 99L);
				SparseFederatedMlpModelSupport.SparseMlpModel global =
						SparseFederatedMlpModelSupport.SparseMlpModel.create(features, layers, 7, classes, 99L);
				require(central.fingerprint().equals(global.fingerprint()),
						"two models of one seed must start identical");
				for (int index = 0; index < rows.size(); index++)
				{
					require(sameBits(central.predict(examples.get(index).getFeatures()),
							global.predict(rows.get(index))),
							"row prediction differs from the central engine's (layers " + layers + ")");
				}
				require(Double.doubleToLongBits(central.meanCrossEntropy(examples))
						== Double.doubleToLongBits(global.meanCrossEntropyOfRows(rows)),
						"row cross-entropy differs from the central engine's");
				//The central engine trains in place with its creation seed; the
				//federated kernel trains a copy with the seed it is given.
				central.train(examples, 4, 0.1, regularisation[0], regularisation[1]);
				SparseFederatedMlpModelSupport.SparseMlpModel trained =
						global.locallyTrained(rows, 4, 0.1, regularisation[0], regularisation[1], 99L);
				require(central.fingerprint().equals(trained.fingerprint()),
						"local training differs from the central engine's (layers " + layers + ", l2 "
								+ regularisation[0] + ", mu " + regularisation[1] + ")");
			}
		}
	}

	private static void localTrainingLeavesTheGlobalModelAlone()
	{
		Random random = new Random(7L);
		int features = 80;
		List<SparseFederatedMlpModelSupport.SparseRow> rows = randomRows(random, 12, features, 3);
		SparseFederatedMlpModelSupport.SparseMlpModel global =
				SparseFederatedMlpModelSupport.SparseMlpModel.create(features, 2, 5, 3, 3L);
		String before = global.fingerprint();
		SparseFederatedMlpModelSupport.ModelUpdate update = global.localUpdate(rows, 3, 0.2, 0.001, 0.0, 11L);
		require(before.equals(global.fingerprint()), "local training changed the global model");
		TreeSet<Integer> seen = new TreeSet<Integer>();
		for (SparseFederatedMlpModelSupport.SparseRow row : rows)
		{
			for (int position = 0; position < row.size(); position++)
			{
				seen.add(Integer.valueOf(row.indexAt(position)));
			}
		}
		require(update.getTouchedRows() == seen.size(),
				"an update must carry exactly the features of its rows: " + update.getTouchedRows()
						+ " for " + seen.size());
		int position = 0;
		for (Integer feature : seen)
		{
			require(update.touchedRowAt(position++) == feature.intValue(), "touched rows must ascend");
		}
		require(update.getSamples() == rows.size(), "an update weighs as many rows as it trained on");
		//The same call again is the same update: local training has no hidden state.
		SparseFederatedMlpModelSupport.ModelUpdate again = global.localUpdate(rows, 3, 0.2, 0.001, 0.0, 11L);
		require(global.plusAverage(Collections.singletonList(update)).fingerprint().equals(
				global.plusAverage(Collections.singletonList(again)).fingerprint()),
				"the same local training gave two updates");
		SparseFederatedMlpModelSupport.ModelUpdate other = global.localUpdate(rows, 3, 0.2, 0.001, 0.0, 12L);
		require(!global.plusAverage(Collections.singletonList(update)).fingerprint().equals(
				global.plusAverage(Collections.singletonList(other)).fingerprint()),
				"another seed must give another order and another update");
		//Rows without a class train nothing and weigh nothing.
		ArrayList<SparseFederatedMlpModelSupport.SparseRow> mixed =
				new ArrayList<SparseFederatedMlpModelSupport.SparseRow>(rows);
		mixed.add(new SparseFederatedMlpModelSupport.SparseRow("unlabelled", new int[] {features - 1},
				new double[] {1.0}, -1));
		require(global.localUpdate(mixed, 1, 0.2, 0.0, 0.0, 5L).getSamples() == rows.size(),
				"a row without a class must not count as a sample");
		boolean refused = false;
		try
		{
			global.localUpdate(Collections.singletonList(mixed.get(mixed.size() - 1)), 1, 0.2, 0.0, 0.0, 5L);
		}
		catch (IllegalArgumentException expected)
		{
			refused = true;
		}
		require(refused, "a client without a labelled row must be refused");
	}

	private static void averageMatchesItsDefinition()
	{
		Random random = new Random(19L);
		int features = 50;
		int classes = 3;
		for (int layers = 1; layers <= 2; layers++)
		{
			SparseFederatedMlpModelSupport.SparseMlpModel global =
					SparseFederatedMlpModelSupport.SparseMlpModel.create(features, layers, 6, classes, 5L);
			ArrayList<SparseFederatedMlpModelSupport.ModelUpdate> updates =
					new ArrayList<SparseFederatedMlpModelSupport.ModelUpdate>();
			long total = 0L;
			for (int client = 0; client < 5; client++)
			{
				//Few features per client, so most first-layer rows are touched by some clients only.
				List<SparseFederatedMlpModelSupport.SparseRow> rows =
						randomRows(random, 3 + 2 * client, features, classes, 4);
				SparseFederatedMlpModelSupport.ModelUpdate update =
						global.localUpdate(rows, 2, 0.15, 0.0005, 0.0, 100L + client);
				updates.add(update);
				total += update.getSamples();
			}
			SparseFederatedMlpModelSupport.SparseMlpModel averaged = global.plusAverage(updates);
			int untouched = 0;
			for (int layer = 0; layer < global.layerCount(); layer++)
			{
				for (int source = 0; source < global.layerInputs(layer); source++)
				{
					for (int target = 0; target < global.layerOutputs(layer); target++)
					{
						double sum = 0.0;
						boolean touched = layer > 0;
						for (SparseFederatedMlpModelSupport.ModelUpdate update : updates)
						{
							if (layer > 0)
							{
								sum += update.getSamples() * update.upperDelta(layer, source, target);
								continue;
							}
							for (int position = 0; position < update.getTouchedRows(); position++)
							{
								if (update.touchedRowAt(position) == source)
								{
									sum += update.getSamples() * update.firstLayerDelta(position, target);
									touched = true;
								}
							}
						}
						double expected = touched
								? global.weightAt(layer, source, target) + sum / total
								: global.weightAt(layer, source, target);
						untouched += touched ? 0 : 1;
						require(Double.doubleToLongBits(expected)
								== Double.doubleToLongBits(averaged.weightAt(layer, source, target)),
								"weight " + layer + "/" + source + "/" + target + " is not the weighted average");
					}
				}
				for (int index = 0; index < global.layerOutputs(layer); index++)
				{
					double sum = 0.0;
					for (SparseFederatedMlpModelSupport.ModelUpdate update : updates)
					{
						sum += update.getSamples() * update.biasDelta(layer, index);
					}
					require(Double.doubleToLongBits(global.biasAt(layer, index) + sum / total)
							== Double.doubleToLongBits(averaged.biasAt(layer, index)),
							"bias " + layer + "/" + index + " is not the weighted average");
				}
			}
			require(untouched > 0, "the test needs first-layer rows no client touched");
			require(averaged.getSampleCount() == total, "the average weighs the sum of its clients");
			//Averaging is defined in the order given; the federation fixes that order.
			ArrayList<SparseFederatedMlpModelSupport.ModelUpdate> reversed =
					new ArrayList<SparseFederatedMlpModelSupport.ModelUpdate>(updates);
			Collections.reverse(reversed);
			requireClose(averaged, global.plusAverage(reversed), 1e-12,
					"reversing the clients may change rounding only");
		}
		boolean refused = false;
		try
		{
			SparseFederatedMlpModelSupport.SparseMlpModel.create(features, 1, 6, classes, 5L).plusAverage(
					Collections.singletonList(SparseFederatedMlpModelSupport.SparseMlpModel
							.create(features, 1, 7, classes, 5L)
							.localUpdate(randomRows(random, 4, features, classes), 1, 0.1, 0.0, 0.0, 1L)));
		}
		catch (IllegalArgumentException expected)
		{
			refused = true;
		}
		require(refused, "an update of another shape must be refused");
	}

	private static void oneClientRoundIsItsOwnTraining()
	{
		Random random = new Random(23L);
		int features = 40;
		List<SparseFederatedMlpModelSupport.SparseRow> rows = randomRows(random, 25, features, 3);
		SparseFederatedMlpModelSupport.SparseMlpModel global =
				SparseFederatedMlpModelSupport.SparseMlpModel.create(features, 1, 8, 3, 17L);
		SparseFederatedMlpModelSupport.SparseMlpModel trained = global.locallyTrained(rows, 5, 0.1, 0.0001, 0.0, 9L);
		SparseFederatedMlpModelSupport.SparseMlpModel averaged = global.plusAverage(
				Collections.singletonList(global.localUpdate(rows, 5, 0.1, 0.0001, 0.0, 9L)));
		requireClose(trained, averaged, 1e-12, "one client's average must be its own trained model");
	}

	// ---------------------------------------------------------- partition

	private static void iidPartitionIsEvenAndStratified()
	{
		TreeMap<String,List<String>> byClass = classes(new int[] {23, 17, 9, 4});
		List<List<String>> clients = FederatedMlp.partitionIid(byClass, 6, 77L);
		require(clients.size() == 6, "six clients expected, got " + clients.size());
		requirePartition(byClass, clients);
		int smallest = Integer.MAX_VALUE;
		int largest = 0;
		for (List<String> client : clients)
		{
			smallest = Math.min(smallest, client.size());
			largest = Math.max(largest, client.size());
		}
		require(largest - smallest <= 1, "IID clients differ by at most one user: " + smallest + ".." + largest);
		for (Map.Entry<String,List<String>> entry : byClass.entrySet())
		{
			int low = Integer.MAX_VALUE;
			int high = 0;
			for (List<String> client : clients)
			{
				int count = 0;
				for (String user : client)
				{
					count += entry.getValue().contains(user) ? 1 : 0;
				}
				low = Math.min(low, count);
				high = Math.max(high, count);
			}
			require(high - low <= 1, "class " + entry.getKey() + " is not spread evenly: " + low + ".." + high);
		}
		require(clients.equals(FederatedMlp.partitionIid(byClass, 6, 77L)), "the same seed must deal the same clients");
		require(!clients.equals(FederatedMlp.partitionIid(byClass, 6, 78L)), "another seed must deal other clients");
		//The deal depends on the users, not on the order they were listed in.
		TreeMap<String,List<String>> reversed = new TreeMap<String,List<String>>();
		for (Map.Entry<String,List<String>> entry : byClass.entrySet())
		{
			ArrayList<String> members = new ArrayList<String>(entry.getValue());
			Collections.reverse(members);
			reversed.put(entry.getKey(), members);
		}
		require(clients.equals(FederatedMlp.partitionIid(reversed, 6, 77L)),
				"the deal must not depend on the order users are listed in");
		require(FederatedMlp.partitionIid(classes(new int[] {2, 1}), 8, 1L).size() == 3,
				"three users make at most three clients");
		require(FederatedMlp.partitionIid(new TreeMap<String,List<String>>(), 4, 1L).isEmpty(),
				"no users make no clients");
		require(FederatedMlp.partitionIid(byClass, 1, 1L).get(0).size() == 53, "one client holds everyone");
	}

	private static void dirichletPartitionIsSkewedAndComplete()
	{
		TreeMap<String,List<String>> byClass = classes(new int[] {60, 60, 60, 60});
		List<List<String>> skewed = FederatedMlp.partitionDirichlet(byClass, 8, 0.1, 5L);
		requirePartition(byClass, skewed);
		require(skewed.equals(FederatedMlp.partitionDirichlet(byClass, 8, 0.1, 5L)),
				"the same seed must deal the same skewed clients");
		List<List<String>> even = FederatedMlp.partitionDirichlet(byClass, 8, 1000.0, 5L);
		requirePartition(byClass, even);
		require(even.size() == 8, "a large alpha keeps every client");
		require(classShareSpread(byClass, skewed) > 3.0 * classShareSpread(byClass, even),
				"a small alpha must skew the classes far more than a large one: "
						+ classShareSpread(byClass, skewed) + " vs " + classShareSpread(byClass, even));
		for (List<String> client : even)
		{
			require(client.size() >= 20 && client.size() <= 40,
					"a large alpha keeps clients near 30 users, got " + client.size());
		}
		for (double alpha : new double[] {0.01, 0.05, 0.5, 5.0})
		{
			for (long seed = 1L; seed <= 20L; seed++)
			{
				List<List<String>> clients = FederatedMlp.partitionDirichlet(byClass, 12, alpha, seed);
				requirePartition(byClass, clients);
				require(!clients.isEmpty() && clients.size() <= 12, "between one and twelve clients");
			}
		}
		require(FederatedMlp.partitionDirichlet(classes(new int[] {1, 1}), 5, 0.5, 3L).size() <= 2,
				"two users make at most two clients");
	}

	private static void apportionAddsUp()
	{
		require(Arrays.equals(FederatedMlp.apportion(new double[] {1.0, 1.0, 1.0}, 10), new int[] {4, 3, 3}),
				"equal shares: the first takes the remainder");
		require(Arrays.equals(FederatedMlp.apportion(new double[] {0.5, 0.25, 0.25}, 8), new int[] {4, 2, 2}),
				"exact shares");
		require(Arrays.equals(FederatedMlp.apportion(new double[] {0.0, 0.0}, 5), new int[] {3, 2}),
				"no shares fall back to equal ones");
		require(Arrays.equals(FederatedMlp.apportion(new double[] {1.0, 0.0, 3.0}, 4), new int[] {1, 0, 3}),
				"a zero share gets nobody");
		Random random = new Random(3L);
		for (int trial = 0; trial < 2000; trial++)
		{
			double[] shares = new double[1 + random.nextInt(9)];
			for (int index = 0; index < shares.length; index++)
			{
				shares[index] = random.nextInt(4) == 0 ? 0.0 : random.nextDouble() * Math.pow(10.0, random.nextInt(6) - 3);
			}
			int total = random.nextInt(200);
			int sum = 0;
			for (int count : FederatedMlp.apportion(shares, total))
			{
				require(count >= 0, "counts are not negative");
				sum += count;
			}
			require(sum == total, "apportioned " + sum + " of " + total);
		}
	}

	// --------------------------------------------------------------- plan

	private static void planMirrorsTheCentralSplit()
	{
		Fixture fixture = new Fixture(140, 4, 31L);
		for (int targetCount : new int[] {10, 140})
		{
			List<String> targets = fixture.users.subList(0, targetCount);
			FederatedMlp.Plan plan = FederatedMlp.Plan.build(fixture.inputs(targets, config(6, 3, 2, 1.0, false, 0.0, 0, 1L)));
			LinkedHashMap<String,String> labels = new LinkedHashMap<String,String>(fixture.labels);
			List<List<String>> folds = TargetFolds.of(targets, labels, labels.size());
			require(plan.foldCount() == folds.size(), "the plan has the central folds");
			require(plan.foldCount() == (targetCount == 10 ? 1 : TargetFolds.FOLDS), "one fold or five");
			for (int foldIndex = 0; foldIndex < folds.size(); foldIndex++)
			{
				HashSet<String> heldOut = new HashSet<String>(folds.get(foldIndex));
				ArrayList<String> train = new ArrayList<String>();
				ArrayList<String> test = new ArrayList<String>();
				for (String user : fixture.corpus.users())
				{
					if (heldOut.contains(user))
					{
						continue;
					}
					if (fixture.train.contains(user))
					{
						train.add(user);
					}
					else if (fixture.test.contains(user))
					{
						test.add(user);
					}
				}
				TreeMap<String,List<String>> trainByClass = new TreeMap<String,List<String>>();
				for (String user : train)
				{
					List<String> members = trainByClass.get(labels.get(user));
					if (members == null)
					{
						members = new ArrayList<String>();
						trainByClass.put(labels.get(user), members);
					}
					members.add(user);
				}
				SvmReproducibility.Split inner = SvmReproducibility.stratifiedSplit(trainByClass, 0.15);
				FederatedMlp.Fold fold = plan.fold(foldIndex);
				TreeSet<String> fitting = new TreeSet<String>();
				for (int client = 0; client < fold.clientCount(); client++)
				{
					for (String user : fold.client(client).users)
					{
						require(fitting.add(user), user + " is in two clients");
					}
					require(fold.client(client).samples() > 0, "no client is empty");
				}
				require(fitting.equals(new TreeSet<String>(inner.getTrainUsers())),
						"the clients must hold exactly the central engine's fitting users");
				require(fold.validationUsers() == inner.getTestUsers().size(), "the central validation users");
				require(fold.testUsers() == test.size(), "the central test users");
				require(fold.targetUsers() == folds.get(foldIndex).size(), "the fold's targets");
				require(fold.clientCount() == 6, "six clients per fold");
				for (String user : fitting)
				{
					require(!heldOut.contains(user), "a held-out target is in a client");
				}
			}
			require(plan.initialState().fold(0).global.fingerprint().equals(
					SparseFederatedMlpModelSupport.SparseMlpModel.create(fixture.vocabulary.size(), 1, 10,
							fixture.classNames.length, 0x5A17EEDL).fingerprint()),
					"fold 0 must start from the central engine's first weights");
			if (plan.foldCount() > 1)
			{
				require(plan.initialState().fold(1).global.fingerprint().equals(
						SparseFederatedMlpModelSupport.SparseMlpModel.create(fixture.vocabulary.size(), 1, 10,
								fixture.classNames.length, 0x5A17EEDL + 1L).fingerprint()),
						"fold 1 must start from the central engine's first weights");
			}
		}
		//No targets: nothing to train, and an honest report.
		FederatedMlp.Outcome empty = FederatedMlp.runSequential(FederatedMlp.Plan.build(
				fixture.inputs(Collections.<String>emptyList(), config(4, 3, 1, 1.0, false, 0.0, 0, 1L))));
		require(empty.scores.isEmpty() && empty.tested == 0, "no targets, no scores");
		//Targets that leave a single class cannot be trained on.
		boolean refused = false;
		try
		{
			Fixture single = new Fixture(30, 1, 5L);
			FederatedMlp.Plan.build(single.inputs(single.users.subList(0, 3), config(4, 3, 1, 1.0, false, 0.0, 0, 1L)));
		}
		catch (IllegalStateException expected)
		{
			refused = expected.getMessage().contains("at least two followee classes");
		}
		require(refused, "one class must be refused as the central engine refuses it");
	}

	// --------------------------------------------------------- federation

	private static void federationIsDeterministicAndOrderFree()
	{
		Fixture fixture = new Fixture(90, 3, 11L);
		List<String> targets = fixture.users.subList(0, 9);
		FederatedMlp.Config config = config(5, 6, 2, 1.0, false, 0.0, 0, 4L);
		FederatedMlp.Outcome first = FederatedMlp.runSequential(FederatedMlp.Plan.build(fixture.inputs(targets, config)));
		FederatedMlp.Outcome second = FederatedMlp.runSequential(FederatedMlp.Plan.build(fixture.inputs(targets, config)));
		require(first.state.fingerprint().equals(second.state.fingerprint()), "two runs must end in one state");
		require(first.scoresFingerprint().equals(second.scoresFingerprint()), "two runs must score identically");
		require(first.report.equals(second.report), "two runs must report identically");
		//The same federation from a corpus listed by other nodes in another order.
		Fixture shuffled = fixture.reownedBy(4);
		FederatedMlp.Outcome third = FederatedMlp.runSequential(FederatedMlp.Plan.build(shuffled.inputs(targets, config)));
		require(first.state.fingerprint().equals(third.state.fingerprint()),
				"who owns the users must not change the federation");
		//Updates in any arrival order and from clients trained in any order.
		FederatedMlp.Plan plan = FederatedMlp.Plan.build(fixture.inputs(targets, config));
		FederatedMlp.State state = plan.initialState();
		while (plan.hasWork(state))
		{
			int round = state.round + 1;
			List<FederatedMlp.Task> tasks = plan.tasks(state, round);
			ArrayList<FederatedMlp.Task> backwards = new ArrayList<FederatedMlp.Task>(tasks);
			Collections.reverse(backwards);
			HashMap<Integer,SparseFederatedMlpModelSupport.ModelUpdate> updates =
					new HashMap<Integer,SparseFederatedMlpModelSupport.ModelUpdate>();
			for (FederatedMlp.Task task : backwards)
			{
				updates.put(Integer.valueOf(task.unit), plan.train(state, round, task));
			}
			state = plan.aggregate(state, round, tasks, updates);
		}
		require(first.state.fingerprint().equals(state.fingerprint()),
				"the order clients train and report in must not change the federation");
		require(first.scoresFingerprint().equals(plan.finish(state).scoresFingerprint()),
				"the order clients train and report in must not change the scores");
		FederatedMlp.Outcome reseeded = FederatedMlp.runSequential(FederatedMlp.Plan.build(
				fixture.inputs(targets, config(5, 6, 2, 1.0, false, 0.0, 0, 5L))));
		require(!first.state.fingerprint().equals(reseeded.state.fingerprint()), "another seed is another federation");
		//A missing or surplus update is an error, never a silent average of the rest.
		FederatedMlp.State start = plan.initialState();
		List<FederatedMlp.Task> tasks = plan.tasks(start, 1);
		HashMap<Integer,SparseFederatedMlpModelSupport.ModelUpdate> partial =
				new HashMap<Integer,SparseFederatedMlpModelSupport.ModelUpdate>();
		for (FederatedMlp.Task task : tasks.subList(1, tasks.size()))
		{
			partial.put(Integer.valueOf(task.unit), plan.train(start, 1, task));
		}
		boolean refused = false;
		try
		{
			plan.aggregate(start, 1, tasks, partial);
		}
		catch (IllegalStateException expected)
		{
			refused = true;
		}
		require(refused, "a round with a missing update must be refused");
		refused = false;
		try
		{
			partial.put(Integer.valueOf(tasks.get(0).unit), plan.train(start, 1, tasks.get(1)));
			plan.aggregate(start, 1, tasks, partial);
		}
		catch (IllegalStateException expected)
		{
			refused = true;
		}
		require(refused || plan.client(tasks.get(0)).samples() == plan.client(tasks.get(1)).samples(),
				"an update of the wrong client must be refused when its size shows it");
	}

	private static void federationLearns()
	{
		Fixture fixture = new Fixture(160, 4, 2L);
		List<String> targets = fixture.users.subList(0, 12);
		FederatedMlp.Plan plan = FederatedMlp.Plan.build(
				fixture.inputs(targets, config(4, 40, 3, 1.0, false, 0.0, 5, 1L)));
		FederatedMlp.Outcome outcome = FederatedMlp.runSequential(plan);
		require(outcome.tested > 20, "the fixture has test users");
		double accuracy = (double)outcome.correct / outcome.tested;
		require(accuracy >= 0.85, "the federation should learn the fixture: accuracy " + accuracy);
		List<FederatedMlp.RoundRecord> history = outcome.state.history();
		require(!history.isEmpty(), "rounds were recorded");
		double first = history.get(0).validationLoss;
		double best = outcome.state.fold(0).bestLoss;
		require(best < first, "validation loss must fall: " + first + " -> " + best);
		require(best < Math.log(fixture.classNames.length), "validation loss must beat a uniform guess");
		require(outcome.scores.size() == targets.size(), "every target is scored");
		for (Map.Entry<String,TreeMap<String,Double>> entry : outcome.scores.entrySet())
		{
			double sum = 0.0;
			for (Double score : entry.getValue().values())
			{
				require(score.doubleValue() >= 0.0, "scores are probabilities");
				sum += score.doubleValue();
			}
			require(Math.abs(sum - 1.0) < 1e-9, "a target's scores add up to one, not " + sum);
			require(entry.getValue().size() == fixture.classNames.length, "one score per class");
		}
		int hits = 0;
		for (String target : targets)
		{
			String top = null;
			for (Map.Entry<String,Double> score : outcome.scores.get(target).entrySet())
			{
				if (top == null || score.getValue().doubleValue() > outcome.scores.get(target).get(top).doubleValue())
				{
					top = score.getKey();
				}
			}
			hits += top.equals(fixture.labels.get(target)) ? 1 : 0;
		}
		require(hits >= 9, "held-out targets should mostly be placed in their class: " + hits + " of 12");
		//The central engine on the same fitting and validation users, for scale.
		FederatedMlp.Fold fold = plan.fold(0);
		require(fold.clientCount() == 4, "four clients");
		String last = outcome.report.get(outcome.report.size() - 1);
		require(last.startsWith("MLP held-out accuracy (test split, targets excluded): "),
				"the accuracy line keeps the central engine's wording: " + last);
		require(outcome.report.get(0).contains("federated averaging"), "the report names the training");
		Map<String,Object> json = outcome.toJson();
		require("FedAvg".equals(json.get("algorithm")) && ((List<?>)json.get("history")).size() == history.size(),
				"the report carries every round");
		require(JsonText.of(json).contains("\"validationLoss\""), "the report serialises");
	}

	private static void everyTargetIsScoredInFolds()
	{
		Fixture fixture = new Fixture(100, 4, 8L);
		FederatedMlp.Outcome outcome = FederatedMlp.runSequential(FederatedMlp.Plan.build(
				fixture.inputs(fixture.users, config(3, 12, 2, 1.0, false, 0.0, 3, 1L))));
		require(outcome.scores.size() == fixture.users.size(),
				"all " + fixture.users.size() + " users are targets and all must be scored, got "
						+ outcome.scores.size());
		int hits = 0;
		for (String user : fixture.users)
		{
			TreeMap<String,Double> scores = outcome.scores.get(user);
			String top = null;
			for (Map.Entry<String,Double> score : scores.entrySet())
			{
				if (top == null || score.getValue().doubleValue() > scores.get(top).doubleValue())
				{
					top = score.getKey();
				}
			}
			hits += top.equals(fixture.labels.get(user)) ? 1 : 0;
		}
		require(hits >= 75, "five folds should place most held-out users in their class: " + hits + " of 100");
		for (int fold = 0; fold < 5; fold++)
		{
			require(outcome.state.fold(fold).rounds >= 1, "every fold ran");
		}
	}

	private static void earlyStoppingKeepsTheBestRound()
	{
		//Classes that overlap: the validation loss falls for a few rounds and then rises.
		Fixture fixture = new Fixture(120, 3, 14L, 0.45, 0.12);
		List<String> targets = fixture.users.subList(0, 10);
		FederatedMlp.Outcome patient = FederatedMlp.runSequential(FederatedMlp.Plan.build(
				fixture.inputs(targets, config(4, 60, 2, 1.0, false, 0.0, 0, 1L))));
		require(patient.state.fold(0).rounds == 60 && patient.state.round == 60,
				"patience 0 runs every round, ran " + patient.state.fold(0).rounds);
		require("round limit reached".equals(patient.state.fold(0).stopReason), "the limit is named");
		FederatedMlp.Outcome stopped = FederatedMlp.runSequential(FederatedMlp.Plan.build(
				fixture.inputs(targets, config(4, 60, 2, 1.0, false, 0.0, 3, 1L))));
		FederatedMlp.FoldState fold = stopped.state.fold(0);
		require(fold.rounds < 60, "patience 3 should stop before round 60 on this fixture, ran " + fold.rounds);
		require(fold.bestRound > 1, "the fixture should improve for more than one round");
		require(patient.state.fold(0).bestRound == fold.bestRound
				&& patient.state.fold(0).model().fingerprint().equals(fold.model().fingerprint()),
				"with or without early stopping the fold is scored by the same best model");
		require(fold.rounds == fold.bestRound + 3, "it stops three rounds after its best: best "
				+ fold.bestRound + ", ran " + fold.rounds);
		require("stopped early".equals(fold.stopReason), "the early stop is named");
		require(fold.model() == fold.best && fold.best != fold.global,
				"the fold is scored by its best round's model, not its last");
		double best = Double.POSITIVE_INFINITY;
		int bestRound = 0;
		for (FederatedMlp.RoundRecord record : stopped.state.history())
		{
			if (record.validationLoss < best - 1e-12)
			{
				best = record.validationLoss;
				bestRound = record.round;
			}
		}
		require(bestRound == fold.bestRound && Double.doubleToLongBits(best) == Double.doubleToLongBits(fold.bestLoss),
				"the best round is the lowest recorded validation loss");
		//Both runs share every round up to the stop.
		for (int index = 0; index < stopped.state.history().size(); index++)
		{
			require(Double.doubleToLongBits(stopped.state.history().get(index).validationLoss)
					== Double.doubleToLongBits(patient.state.history().get(index).validationLoss),
					"stopping early must not change the rounds before it");
		}
	}

	private static void partialParticipationSamplesClients()
	{
		Fixture fixture = new Fixture(120, 3, 21L);
		List<String> targets = fixture.users.subList(0, 10);
		FederatedMlp.Plan plan = FederatedMlp.Plan.build(
				fixture.inputs(targets, config(8, 20, 1, 0.5, false, 0.0, 0, 6L)));
		require(plan.fold(0).clientCount() == 8, "eight clients");
		HashSet<Integer> everSelected = new HashSet<Integer>();
		HashSet<String> selections = new HashSet<String>();
		for (int round = 1; round <= 20; round++)
		{
			int[] chosen = plan.participants(0, round);
			require(chosen.length == 4, "half of eight clients train per round, got " + chosen.length);
			for (int index = 0; index < chosen.length; index++)
			{
				require(index == 0 || chosen[index] > chosen[index - 1], "participants ascend and are distinct");
				everSelected.add(Integer.valueOf(chosen[index]));
			}
			selections.add(Arrays.toString(chosen));
			require(Arrays.equals(chosen, plan.participants(0, round)), "a round's participants are fixed");
		}
		require(everSelected.size() == 8, "over twenty rounds every client trains at least once");
		require(selections.size() > 5, "rounds draw different clients");
		FederatedMlp.Outcome outcome = FederatedMlp.runSequential(plan);
		for (FederatedMlp.RoundRecord record : outcome.state.history())
		{
			require(record.participants == 4, "four updates per round");
		}
		require(FederatedMlp.Plan.build(fixture.inputs(targets, config(8, 2, 1, 0.01, false, 0.0, 0, 6L)))
				.participants(0, 1).length == 1, "at least one client trains");
		require(plan.clientsPerRound() == 4, "four client units per round");
	}

	private static void proximalTermHoldsClientsBack()
	{
		Fixture fixture = new Fixture(120, 4, 33L);
		List<String> targets = fixture.users.subList(0, 10);
		FederatedMlp.Outcome free = FederatedMlp.runSequential(FederatedMlp.Plan.build(
				fixture.inputs(targets, config(6, 3, 6, 1.0, true, 0.0, 0, 2L))));
		FederatedMlp.Outcome held = FederatedMlp.runSequential(FederatedMlp.Plan.build(
				fixture.inputs(targets, config(6, 3, 6, 1.0, true, 2.0, 0, 2L))));
		require(held.state.history().get(0).largestUpdate < free.state.history().get(0).largestUpdate,
				"a proximal term must shrink the first round's updates: "
						+ held.state.history().get(0).largestUpdate + " vs "
						+ free.state.history().get(0).largestUpdate);
		require(!free.state.fingerprint().equals(held.state.fingerprint()), "mu changes the federation");
	}

	private static void inputsFingerprintSeesEveryInput()
	{
		Fixture fixture = new Fixture(60, 3, 9L);
		List<String> targets = fixture.users.subList(0, 6);
		FederatedMlp.Config config = config(4, 5, 2, 1.0, false, 0.0, 2, 1L);
		String base = fixture.inputs(targets, config).fingerprint();
		require(base.equals(fixture.inputs(new ArrayList<String>(targets), config).fingerprint()),
				"equal inputs have one fingerprint");
		ArrayList<String> reordered = new ArrayList<String>(targets);
		Collections.reverse(reordered);
		require(base.equals(fixture.inputs(reordered, config).fingerprint()),
				"the order targets are listed in is not an input");
		require(!base.equals(fixture.inputs(fixture.users.subList(0, 7), config).fingerprint()), "targets are an input");
		require(!base.equals(fixture.inputs(targets, config(4, 5, 2, 1.0, false, 0.0, 2, 2L)).fingerprint()),
				"the seed is an input");
		require(!base.equals(fixture.inputs(targets, config(5, 5, 2, 1.0, false, 0.0, 2, 1L)).fingerprint()),
				"the client count is an input");
		Fixture other = new Fixture(60, 3, 10L);
		require(!base.equals(other.inputs(targets, config).fingerprint()), "the corpus is an input");
		require(!base.equals(fixture.withDocuments(fixture.users.get(20), 1.5, false)
				.inputs(targets, config).fingerprint()), "every value of every document is an input");
		require(base.equals(fixture.withDocuments(null, 1.0, true).inputs(targets, config).fingerprint()),
				"the order a document lists its terms in is not an input");
		require(base.equals(fixture.reownedBy(3).inputs(targets, config).fingerprint()),
				"who owns the users is not an input");
	}

	private static void reportsAreKeptPerRunAndWritten() throws Exception
	{
		Fixture fixture = new Fixture(60, 3, 12L);
		List<String> targets = fixture.users.subList(0, 6);
		FederatedMlp.Outcome first = FederatedMlp.runSequential(
				FederatedMlp.Plan.build(fixture.inputs(targets, config(4, 3, 1, 1.0, false, 0.0, 0, 1L))));
		FederatedMlp.Outcome second = FederatedMlp.runSequential(
				FederatedMlp.Plan.build(fixture.inputs(targets, config(4, 2, 1, 1.0, false, 0.0, 0, 1L))));
		require(first.state.history().size() == 3 && second.state.history().size() == 2,
				"one fold records one line per round");
		FederatedMlpReport.clear();
		java.io.File directory = java.nio.file.Files.createTempDirectory("federated-mlp-report").toFile();
		try
		{
			require(FederatedMlpReport.of("run-a") == null, "a run that trained no federation has no report");
			FederatedMlpReport.remember("run-a", first);
			//Records are written in the background: a later run may finish before an
			//earlier run's record is written, and must not take its report away.
			FederatedMlpReport.remember("run-b", second);
			require(FederatedMlpReport.of("run-a").rounds().size() == 3
					&& FederatedMlpReport.of("run-b").rounds().size() == 2, "each run keeps its own report");
			FederatedMlpReport.of("run-a").write(directory);
			java.io.File folder = new java.io.File(directory, FederatedMlpReport.FOLDER);
			Object report = JsonText.parse(new String(java.nio.file.Files.readAllBytes(
					new java.io.File(folder, FederatedMlpReport.FILE).toPath()),
					java.nio.charset.StandardCharsets.UTF_8));
			require(report instanceof Map && "FedAvg".equals(((Map<?,?>)report).get("algorithm"))
					&& ((List<?>)((Map<?,?>)report).get("history")).size() == 3
					&& ((List<?>)((Map<?,?>)report).get("folds")).size() == 1,
					"report.json must hold the run's folds and rounds");
			List<List<String>> rounds = JsonText.parseCsv(new String(java.nio.file.Files.readAllBytes(
					new java.io.File(folder, FederatedMlpReport.ROUNDS_FILE).toPath()),
					java.nio.charset.StandardCharsets.UTF_8));
			require(rounds.size() == 4 && "round".equals(rounds.get(0).get(0)) && "3".equals(rounds.get(3).get(0)),
					"rounds.csv has a header and one line per fold and round");
			for (int run = 0; run < FederatedMlpReport.KEPT_RUNS; run++)
			{
				FederatedMlpReport.remember("later-" + run, second);
			}
			require(FederatedMlpReport.of("run-a") == null && FederatedMlpReport.of("run-b") == null
					&& FederatedMlpReport.of("later-0") != null, "only the latest reports are kept");
		}
		finally
		{
			FederatedMlpReport.clear();
			java.io.File folder = new java.io.File(directory, FederatedMlpReport.FOLDER);
			new java.io.File(folder, FederatedMlpReport.FILE).delete();
			new java.io.File(folder, FederatedMlpReport.ROUNDS_FILE).delete();
			folder.delete();
			directory.delete();
		}
	}

	// ------------------------------------------------------------ fixture

	static FederatedMlp.Config config(int clients, int rounds, int localEpochs, double fraction,
			boolean labelSkew, double mu, int patience, long seed)
	{
		return new FederatedMlp.Config(clients, rounds, localEpochs, fraction, labelSkew, 0.3, mu, patience,
				seed, 1, 10, 0.1, 0.0001);
	}

	/** A small corpus whose classes can be learned: signature terms per class and shared noise. */
	static final class Fixture
	{
		final GlobalCorpus corpus;
		final List<String> users;
		final LinkedHashMap<String,String> labels = new LinkedHashMap<String,String>();
		final LinkedHashMap<String,LinkedHashMap<String,Double>> vectors =
				new LinkedHashMap<String,LinkedHashMap<String,Double>>();
		final List<String> vocabulary;
		final String[] classNames;
		final HashSet<String> train = new HashSet<String>();
		final HashSet<String> test = new HashSet<String>();

		Fixture(int userCount, int classes, long seed)
		{
			this(userCount, classes, seed, 0.6, 0.06);
		}

		/**
		 * @param signature probability that a user has each term of its own class
		 * @param confusion probability that a user has each term of another class
		 */
		Fixture(int userCount, int classes, long seed, double signature, double confusion)
		{
			Random random = new Random(seed);
			classNames = new String[classes];
			for (int index = 0; index < classes; index++)
			{
				classNames[index] = "account-" + (char)('A' + index);
			}
			TreeSet<String> terms = new TreeSet<String>();
			for (int user = 0; user < userCount; user++)
			{
				int label = user % classes;
				ArrayList<String> present = new ArrayList<String>();
				for (int term = 0; term < 12; term++)
				{
					if (random.nextDouble() < signature)
					{
						present.add("sig" + label + "-" + term);
					}
					for (int other = 0; other < classes; other++)
					{
						if (other != label && random.nextDouble() < confusion)
						{
							present.add("sig" + other + "-" + term);
						}
					}
				}
				for (int term = 0; term < 30; term++)
				{
					if (random.nextDouble() < 0.25)
					{
						present.add("noise-" + term);
					}
				}
				if (present.isEmpty())
				{
					present.add("noise-0");
				}
				//Documents list their terms in no particular order, as a node's do.
				Collections.shuffle(present, random);
				LinkedHashMap<String,Double> vector = new LinkedHashMap<String,Double>();
				for (String term : present)
				{
					vector.put(term, Double.valueOf(0.2 + random.nextDouble()));
					terms.add(term);
				}
				String name = String.format("user-%04d", Integer.valueOf(user));
				vectors.put(name, vector);
				labels.put(name, classNames[label]);
			}
			vocabulary = new ArrayList<String>(terms);
			corpus = GlobalCorpus.combine(Collections.singletonList(new GlobalCorpus.Contribution(vectors, labels)));
			users = new ArrayList<String>(corpus.users());
			split();
		}

		private Fixture(Fixture source, int nodes)
		{
			classNames = source.classNames;
			vocabulary = source.vocabulary;
			labels.putAll(source.labels);
			vectors.putAll(source.vectors);
			ArrayList<GlobalCorpus.Contribution> contributions = new ArrayList<GlobalCorpus.Contribution>();
			ArrayList<String> names = new ArrayList<String>(vectors.keySet());
			Collections.reverse(names);
			for (int node = 0; node < nodes; node++)
			{
				LinkedHashMap<String,LinkedHashMap<String,Double>> ownedVectors =
						new LinkedHashMap<String,LinkedHashMap<String,Double>>();
				LinkedHashMap<String,String> ownedLabels = new LinkedHashMap<String,String>();
				for (int index = node; index < names.size(); index += nodes)
				{
					ownedVectors.put(names.get(index), vectors.get(names.get(index)));
					ownedLabels.put(names.get(index), labels.get(names.get(index)));
				}
				contributions.add(new GlobalCorpus.Contribution(ownedVectors, ownedLabels));
			}
			corpus = GlobalCorpus.combine(contributions);
			users = new ArrayList<String>(corpus.users());
			train.addAll(source.train);
			test.addAll(source.test);
		}

		/** The same users and documents, contributed by {@code nodes} owners in another order. */
		Fixture reownedBy(int nodes)
		{
			return new Fixture(this, nodes);
		}

		private Fixture(Fixture source, String scaledUser, double factor, boolean reverseTerms)
		{
			classNames = source.classNames;
			vocabulary = source.vocabulary;
			labels.putAll(source.labels);
			for (Map.Entry<String,LinkedHashMap<String,Double>> entry : source.vectors.entrySet())
			{
				ArrayList<String> terms = new ArrayList<String>(entry.getValue().keySet());
				if (reverseTerms)
				{
					Collections.reverse(terms);
				}
				LinkedHashMap<String,Double> vector = new LinkedHashMap<String,Double>();
				for (String term : terms)
				{
					vector.put(term, entry.getValue().get(term));
				}
				if (entry.getKey().equals(scaledUser))
				{
					String first = terms.get(0);
					vector.put(first, Double.valueOf(vector.get(first).doubleValue() * factor));
				}
				vectors.put(entry.getKey(), vector);
			}
			corpus = GlobalCorpus.combine(Collections.singletonList(new GlobalCorpus.Contribution(vectors, labels)));
			users = new ArrayList<String>(corpus.users());
			train.addAll(source.train);
			test.addAll(source.test);
		}

		/**
		 * The same users, labels and splits with other documents: one value of
		 * {@code scaledUser} multiplied by {@code factor} (null changes none),
		 * and every document's terms listed in reverse when asked.
		 */
		Fixture withDocuments(String scaledUser, double factor, boolean reverseTerms)
		{
			return new Fixture(this, scaledUser, factor, reverseTerms);
		}

		/** The controller's stratified 70/30 split. */
		private void split()
		{
			TreeMap<String,List<String>> byClass = new TreeMap<String,List<String>>();
			for (String user : users)
			{
				List<String> members = byClass.get(labels.get(user));
				if (members == null)
				{
					members = new ArrayList<String>();
					byClass.put(labels.get(user), members);
				}
				members.add(user);
			}
			SvmReproducibility.Split split = SvmReproducibility.stratifiedSplit(byClass, 0.30);
			train.addAll(split.getTrainUsers());
			test.addAll(split.getTestUsers());
		}

		FederatedMlp.Inputs inputs(List<String> targets, FederatedMlp.Config config)
		{
			return new FederatedMlp.Inputs(corpus, targets, train, test, classNames, vocabulary, config);
		}
	}

	private static TreeMap<String,List<String>> classes(int[] sizes)
	{
		TreeMap<String,List<String>> byClass = new TreeMap<String,List<String>>();
		for (int index = 0; index < sizes.length; index++)
		{
			ArrayList<String> members = new ArrayList<String>();
			for (int member = 0; member < sizes[index]; member++)
			{
				members.add(String.format("c%d-u%03d", Integer.valueOf(index), Integer.valueOf(member)));
			}
			byClass.put("class-" + index, members);
		}
		return byClass;
	}

	private static void requirePartition(Map<String,List<String>> byClass, List<List<String>> clients)
	{
		TreeSet<String> all = new TreeSet<String>();
		for (List<String> members : byClass.values())
		{
			all.addAll(members);
		}
		TreeSet<String> dealt = new TreeSet<String>();
		for (List<String> client : clients)
		{
			require(!client.isEmpty(), "no client is empty");
			ArrayList<String> sorted = new ArrayList<String>(client);
			Collections.sort(sorted);
			require(sorted.equals(client), "a client lists its users in name order");
			for (String user : client)
			{
				require(dealt.add(user), user + " was dealt twice");
			}
		}
		require(dealt.equals(all), "every user is dealt to exactly one client");
	}

	/** Mean over clients of the largest share one class has in the client: 1 is fully skewed. */
	private static double classShareSpread(Map<String,List<String>> byClass, List<List<String>> clients)
	{
		double spread = 0.0;
		for (List<String> client : clients)
		{
			int largest = 0;
			for (List<String> members : byClass.values())
			{
				int count = 0;
				for (String user : client)
				{
					count += members.contains(user) ? 1 : 0;
				}
				largest = Math.max(largest, count);
			}
			spread += (double)largest / client.size() - 1.0 / byClass.size();
		}
		return spread / clients.size();
	}

	private static List<SparseFederatedMlpModelSupport.SparseRow> randomRows(Random random, int count,
			int features, int classes)
	{
		return randomRows(random, count, features, classes, 1 + features / 4);
	}

	private static List<SparseFederatedMlpModelSupport.SparseRow> randomRows(Random random, int count,
			int features, int classes, int perRow)
	{
		ArrayList<SparseFederatedMlpModelSupport.SparseRow> rows =
				new ArrayList<SparseFederatedMlpModelSupport.SparseRow>();
		for (int index = 0; index < count; index++)
		{
			TreeMap<Integer,Double> values = new TreeMap<Integer,Double>();
			int wanted = 1 + random.nextInt(Math.max(1, perRow));
			while (values.size() < wanted)
			{
				values.put(Integer.valueOf(random.nextInt(features)), Double.valueOf(random.nextDouble() * 2.0 - 0.5));
			}
			rows.add(SparseFederatedMlpModelSupport.SparseRow.of("row-" + index, values, random.nextInt(classes)));
		}
		return rows;
	}

	/** The rows as the central engine's examples: the same features in the same (ascending) order. */
	private static List<SparseFederatedMlpModelSupport.TrainingExample> examplesOf(
			List<SparseFederatedMlpModelSupport.SparseRow> rows)
	{
		ArrayList<SparseFederatedMlpModelSupport.TrainingExample> examples =
				new ArrayList<SparseFederatedMlpModelSupport.TrainingExample>();
		for (SparseFederatedMlpModelSupport.SparseRow row : rows)
		{
			LinkedHashMap<Integer,Double> features = new LinkedHashMap<Integer,Double>();
			for (int position = 0; position < row.size(); position++)
			{
				features.put(Integer.valueOf(row.indexAt(position)), Double.valueOf(row.valueAt(position)));
			}
			examples.add(new SparseFederatedMlpModelSupport.TrainingExample(
					row.getUser(), features, row.getLabelIndex()));
		}
		return examples;
	}

	private static boolean sameBits(double[] left, double[] right)
	{
		if (left.length != right.length)
		{
			return false;
		}
		for (int index = 0; index < left.length; index++)
		{
			if (Double.doubleToLongBits(left[index]) != Double.doubleToLongBits(right[index]))
			{
				return false;
			}
		}
		return true;
	}

	private static void requireClose(SparseFederatedMlpModelSupport.SparseMlpModel left,
			SparseFederatedMlpModelSupport.SparseMlpModel right, double tolerance, String message)
	{
		for (int layer = 0; layer < left.layerCount(); layer++)
		{
			for (int source = 0; source < left.layerInputs(layer); source++)
			{
				for (int target = 0; target < left.layerOutputs(layer); target++)
				{
					double a = left.weightAt(layer, source, target);
					double b = right.weightAt(layer, source, target);
					require(Math.abs(a - b) <= tolerance * Math.max(1.0, Math.abs(a)),
							message + " (weight " + layer + "/" + source + "/" + target + ": " + a + " vs " + b + ")");
				}
			}
			for (int index = 0; index < left.layerOutputs(layer); index++)
			{
				double a = left.biasAt(layer, index);
				double b = right.biasAt(layer, index);
				require(Math.abs(a - b) <= tolerance * Math.max(1.0, Math.abs(a)),
						message + " (bias " + layer + "/" + index + ": " + a + " vs " + b + ")");
			}
		}
	}

	static void require(boolean condition, String message)
	{
		if (!condition)
		{
			throw new IllegalStateException(message);
		}
	}
}
