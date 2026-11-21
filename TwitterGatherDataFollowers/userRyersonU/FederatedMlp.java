package TwitterGatherDataFollowers.userRyersonU;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Federated averaging (FedAvg, McMahan et al. 2017) of the softmax MLP, as a
 * pure and deterministic algorithm that knows nothing about the cluster that
 * runs it.
 *
 * <b>The federation.</b> For every fold of held-out targets
 * ({@link TargetFolds}) the labelled users of the controller's training split
 * are divided exactly as the central softmax MLP divides them: 85% for
 * fitting and 15% for validation, stratified by class. The fitting users are
 * then dealt to K <i>clients</i>. A client is a fixed set of users, decided by
 * the data and the seed alone, so the federation is the same on one node and
 * on thirty-two. Which node trains a client in a round is the scheduler's
 * choice and cannot change any number computed here.
 *
 * <b>A round.</b> Every participating client trains a private copy of its
 * fold's global model for E epochs on its own users
 * ({@link SparseFederatedMlpModelSupport.SparseMlpModel#localUpdate}) and
 * returns the difference. The server adds the sample-weighted mean of the
 * differences to the global model
 * ({@link SparseFederatedMlpModelSupport.SparseMlpModel#plusAverage}),
 * measures the new model's cross-entropy on the fold's validation users and
 * keeps the best model so far. A fold ends after {@code patience} rounds
 * without improvement or after R rounds; its targets are scored by its best
 * model, as the central engine scores them by its best epoch.
 *
 * <b>Why the result is exact.</b> A client's update is a function of the
 * global model, the client's users and a seed derived from (fold, round,
 * client). The average adds the updates in client order. Nothing depends on
 * time, threads, node counts or the order in which updates arrive, so every
 * schedule of the same federation produces bit-identical models and scores;
 * {@link #runSequential} is the reference every distributed run must equal.
 *
 * With one client, full participation and no proximal term, a round is E
 * epochs of ordinary training (up to rounding in the last place), which is
 * what the central engine does between two validation checks.
 */
final class FederatedMlp
{
	/** Share of a fold's training users kept for validation, as in the central softmax MLP. */
	static final double VALIDATION_SHARE = 0.15;
	/** Seed of fold 0's initial weights, as in the central softmax MLP (fold f adds f). */
	static final long INITIAL_WEIGHT_SEED = 0x5A17EEDL;

	private static final int PURPOSE_PARTITION = 1;
	private static final int PURPOSE_DIRICHLET = 2;
	private static final int PURPOSE_SAMPLING = 3;
	private static final int PURPOSE_LOCAL = 4;

	/**
	 * Estimated reference work of local training, in milliseconds on a speed-1
	 * node: per first-layer weight visited in an epoch, per upper-layer weight
	 * per row and epoch, and per row and epoch. Fitted to the softmax kernel
	 * over clients of 5 to 400 users, 20 to 5,000 features per user and 10 to
	 * 100 hidden neurons (within about 30% on one 2024 laptop core). Planners
	 * need the proportions between clients; every measure of a run uses the
	 * work it measured, not these estimates.
	 */
	static final double COST_FIRST_LAYER_MS = 1.5e-6;
	static final double COST_UPPER_LAYER_MS = 1.2e-6;
	static final double COST_ROW_MS = 6.0e-4;
	/** Estimated reference work of one forward pass, per first-layer weight visited. */
	static final double COST_FORWARD_MS = 0.5e-6;

	private FederatedMlp()
	{
	}

	// ------------------------------------------------------------- settings

	/** The hyper-parameters of a federation, fixed for a run. */
	static final class Config implements Serializable
	{
		private static final long serialVersionUID = 1L;

		final int clients;
		final int rounds;
		final int localEpochs;
		final double clientFraction;
		final boolean labelSkew;
		final double dirichletAlpha;
		final double proximalMu;
		final int patience;
		final long seed;
		final int hiddenLayers;
		final int hiddenNeurons;
		final double learningRate;
		final double l2;

		/**
		 * @param clients         clients per fold (fewer when a fold has fewer fitting users)
		 * @param rounds          most rounds a fold runs
		 * @param localEpochs     passes over a client's users per round
		 * @param clientFraction  share of a fold's clients that train in a round, in (0, 1]
		 * @param labelSkew       deal each class to the clients in Dirichlet proportions
		 *                        (non-IID) instead of evenly
		 * @param dirichletAlpha  concentration of those proportions; small is skewed
		 * @param proximalMu      FedProx coefficient; 0 is plain FedAvg
		 * @param patience        rounds without a better validation loss before a fold
		 *                        stops; 0 runs every round
		 * @param seed            decides the clients, the sampling and the local orders
		 */
		Config(int clients, int rounds, int localEpochs, double clientFraction, boolean labelSkew,
				double dirichletAlpha, double proximalMu, int patience, long seed,
				int hiddenLayers, int hiddenNeurons, double learningRate, double l2)
		{
			this.clients = Math.max(1, clients);
			this.rounds = Math.max(1, rounds);
			this.localEpochs = Math.max(1, localEpochs);
			this.clientFraction = Double.isNaN(clientFraction) ? 1.0
					: Math.min(1.0, Math.max(0.0, clientFraction));
			this.labelSkew = labelSkew;
			this.dirichletAlpha = Double.isFinite(dirichletAlpha) && dirichletAlpha > 0.0
					? dirichletAlpha : AlgorithmParameterSettings.DEFAULT_MLP_FEDERATED_DIRICHLET_ALPHA;
			this.proximalMu = Double.isFinite(proximalMu) ? Math.max(0.0, proximalMu) : 0.0;
			this.patience = Math.max(0, patience);
			this.seed = seed;
			this.hiddenLayers = Math.max(1, hiddenLayers);
			this.hiddenNeurons = Math.max(1, hiddenNeurons);
			this.learningRate = learningRate;
			this.l2 = l2;
		}

		static Config from(AlgorithmParameterSettings settings)
		{
			AlgorithmParameterSettings effective = AlgorithmParameterSettings.effective(settings);
			return new Config(
					effective.getMlpFederatedClients(),
					effective.getMlpFederatedRounds(),
					effective.getMlpFederatedLocalEpochs(),
					effective.getMlpFederatedClientFraction(),
					AlgorithmParameterSettings.MLP_FEDERATED_PARTITION_DIRICHLET.equals(
							effective.getMlpFederatedPartition()),
					effective.getMlpFederatedDirichletAlpha(),
					effective.getMlpFedProxMu(),
					effective.getMlpFederatedPatience(),
					effective.getMlpFederatedSeed(),
					effective.getMlpHiddenLayers(),
					effective.getMlpHiddenNeurons(),
					effective.getMlpLearningRate(),
					effective.getMlpSparseL2());
		}

		/** How the clients of a fold are formed, in words. */
		String partitionLabel()
		{
			return labelSkew
					? "label-skewed, Dirichlet alpha " + AlgorithmParameterSettings.formatDouble(dirichletAlpha)
					: "IID";
		}

		/** One line for reports. */
		String describe()
		{
			return clients + " client" + (clients == 1 ? "" : "s") + " per fold (" + partitionLabel() + "), "
					+ (clientFraction >= 1.0 ? "all"
							: String.format(Locale.US, "%.0f%%", Double.valueOf(clientFraction * 100.0)))
					+ " training per round, " + localEpochs + " local epoch" + (localEpochs == 1 ? "" : "s")
					+ ", up to " + rounds + " round" + (rounds == 1 ? "" : "s")
					+ (patience > 0 ? ", patience " + patience : ", no early stopping")
					+ (proximalMu > 0.0 ? ", FedProx mu " + AlgorithmParameterSettings.formatDouble(proximalMu) : "")
					+ ", seed " + seed;
		}

		/** Every field, for checking that the nodes of a run agree. */
		String canonical()
		{
			return "clients=" + clients + ";rounds=" + rounds + ";localEpochs=" + localEpochs
					+ ";clientFraction=" + Double.toString(clientFraction) + ";labelSkew=" + labelSkew
					+ ";dirichletAlpha=" + Double.toString(dirichletAlpha)
					+ ";proximalMu=" + Double.toString(proximalMu) + ";patience=" + patience
					+ ";seed=" + seed + ";hiddenLayers=" + hiddenLayers + ";hiddenNeurons=" + hiddenNeurons
					+ ";learningRate=" + Double.toString(learningRate) + ";l2=" + Double.toString(l2);
		}

		Map<String,Object> toJson()
		{
			LinkedHashMap<String,Object> json = new LinkedHashMap<String,Object>();
			json.put("clients", Integer.valueOf(clients));
			json.put("rounds", Integer.valueOf(rounds));
			json.put("localEpochs", Integer.valueOf(localEpochs));
			json.put("clientFraction", Double.valueOf(clientFraction));
			json.put("partition", labelSkew ? "dirichlet" : "iid");
			json.put("dirichletAlpha", Double.valueOf(dirichletAlpha));
			json.put("proximalMu", Double.valueOf(proximalMu));
			json.put("patience", Integer.valueOf(patience));
			json.put("seed", Long.valueOf(seed));
			json.put("hiddenLayers", Integer.valueOf(hiddenLayers));
			json.put("hiddenNeurons", Integer.valueOf(hiddenNeurons));
			json.put("learningRate", Double.valueOf(learningRate));
			json.put("l2", Double.valueOf(l2));
			return json;
		}
	}

	/** Everything a federation is planned from; identical on every node of a run. */
	static final class Inputs
	{
		final GlobalCorpus corpus;
		final List<String> targets;
		final Set<String> centralTrain;
		final Set<String> centralTest;
		final String[] classNames;
		final List<String> vocabulary;
		final Config config;

		/**
		 * @param targets       the run's recommendation targets
		 * @param centralTrain  users of the controller's training split
		 * @param centralTest   users of the controller's test split
		 * @param classNames    the followee classes, in the model's output order
		 * @param vocabulary    the features, in the model's input order
		 */
		Inputs(GlobalCorpus corpus, Collection<String> targets, Collection<String> centralTrain,
				Collection<String> centralTest, String[] classNames, Collection<String> vocabulary,
				Config config)
		{
			if (corpus == null || config == null)
			{
				throw new IllegalArgumentException("A federation needs a corpus and a configuration.");
			}
			this.corpus = corpus;
			this.targets = Collections.unmodifiableList(new ArrayList<String>(
					targets == null ? Collections.<String>emptyList() : targets));
			this.centralTrain = Collections.unmodifiableSet(new HashSet<String>(
					centralTrain == null ? Collections.<String>emptyList() : centralTrain));
			this.centralTest = Collections.unmodifiableSet(new HashSet<String>(
					centralTest == null ? Collections.<String>emptyList() : centralTest));
			this.classNames = classNames == null ? new String[0] : classNames.clone();
			this.vocabulary = Collections.unmodifiableList(new ArrayList<String>(
					vocabulary == null ? Collections.<String>emptyList() : vocabulary));
			this.config = config;
		}

		/**
		 * SHA-256 over everything the plan is built from: the settings, the
		 * targets, the splits, the classes, the vocabulary and every user's
		 * label and vector. Every node of a run submits its own; the plan is
		 * built only when they are all equal, so a node with other targets,
		 * splits, data or settings stops the run instead of silently training
		 * another federation.
		 */
		String fingerprint()
		{
			MessageDigest digest = sha256();
			feed(digest, config.canonical());
			feed(digest, "targets");
			for (String target : new TreeSet<String>(nonNull(targets)))
			{
				feed(digest, target);
			}
			feed(digest, "train");
			for (String user : new TreeSet<String>(nonNull(centralTrain)))
			{
				feed(digest, user);
			}
			feed(digest, "test");
			for (String user : new TreeSet<String>(nonNull(centralTest)))
			{
				feed(digest, user);
			}
			feed(digest, "classes");
			for (String name : classNames)
			{
				feed(digest, String.valueOf(name));
			}
			feed(digest, "vocabulary");
			for (String term : vocabulary)
			{
				feed(digest, String.valueOf(term));
			}
			feed(digest, "corpus");
			for (String user : corpus.users())
			{
				feed(digest, user);
				feed(digest, String.valueOf(corpus.labelOf(user)));
				Map<String,Double> vector = corpus.vectorOf(user);
				feed(digest, Integer.toString(vector == null ? -1 : vector.size()));
				feed(digest, Long.toString(checksum(vector)));
			}
			return hex(digest.digest());
		}

		/** A checksum of a vector's terms and values that does not depend on their order. */
		private static long checksum(Map<String,Double> vector)
		{
			long sum = 0L;
			if (vector != null)
			{
				for (Map.Entry<String,Double> entry : vector.entrySet())
				{
					long term = String.valueOf(entry.getKey()).hashCode();
					long value = entry.getValue() == null
							? 0L : Double.doubleToLongBits(entry.getValue().doubleValue());
					sum += splitMix(splitMix(term) ^ value);
				}
			}
			return sum;
		}

		/** Every user whose followee is one of the model's classes, in the corpus's order. */
		LinkedHashMap<String,String> labels()
		{
			HashSet<String> classes = new HashSet<String>(Arrays.asList(classNames));
			LinkedHashMap<String,String> labels = new LinkedHashMap<String,String>();
			for (String user : corpus.users())
			{
				String label = corpus.labelOf(user);
				if (label != null && classes.contains(label))
				{
					labels.put(user, label);
				}
			}
			return labels;
		}

		/** The folds {@link Plan#build} will make of these inputs: one model, or five. */
		int foldCount()
		{
			LinkedHashMap<String,String> labels = labels();
			return TargetFolds.of(targets, labels, labels.size()).size();
		}

		private static List<String> nonNull(Collection<String> values)
		{
			ArrayList<String> kept = new ArrayList<String>();
			for (String value : values)
			{
				if (value != null)
				{
					kept.add(value);
				}
			}
			return kept;
		}
	}

	// ----------------------------------------------------------------- plan

	/** One client of one fold: a fixed set of fitting users. */
	static final class Client
	{
		final int fold;
		final int index;
		private final List<SparseFederatedMlpModelSupport.SparseRow> rows;
		final List<String> users;
		/** Feature values over all its users: the work of one pass. */
		final long nonZeros;
		/** Distinct features of its users: the first-layer rows its update carries. */
		final int features;
		/** Estimated reference work of one round, in milliseconds on a speed-1 node. */
		final double costMs;
		/** Wire size of its update. */
		final long updateBytes;

		private Client(int fold, int index, List<SparseFederatedMlpModelSupport.SparseRow> rows,
				long nonZeros, int features, double costMs, long updateBytes)
		{
			this.fold = fold;
			this.index = index;
			this.rows = Collections.unmodifiableList(rows);
			ArrayList<String> names = new ArrayList<String>();
			for (SparseFederatedMlpModelSupport.SparseRow row : rows)
			{
				names.add(row.getUser());
			}
			this.users = Collections.unmodifiableList(names);
			this.nonZeros = nonZeros;
			this.features = features;
			this.costMs = costMs;
			this.updateBytes = updateBytes;
		}

		/** Rows it trains on; its weight in the average. */
		int samples()
		{
			return rows.size();
		}

		String key()
		{
			return String.format(Locale.ROOT, "f%d-c%03d", Integer.valueOf(fold + 1), Integer.valueOf(index + 1));
		}
	}

	/** One fold: its clients, its validation, test and target users and its first model. */
	static final class Fold
	{
		final int index;
		private final Client[] clients;
		private final List<SparseFederatedMlpModelSupport.SparseRow> validation;
		private final List<SparseFederatedMlpModelSupport.SparseRow> test;
		private final List<SparseFederatedMlpModelSupport.SparseRow> targets;
		private final SparseFederatedMlpModelSupport.SparseMlpModel initial;
		final int fittingUsers;

		private Fold(int index, Client[] clients,
				List<SparseFederatedMlpModelSupport.SparseRow> validation,
				List<SparseFederatedMlpModelSupport.SparseRow> test,
				List<SparseFederatedMlpModelSupport.SparseRow> targets,
				SparseFederatedMlpModelSupport.SparseMlpModel initial)
		{
			this.index = index;
			this.clients = clients;
			this.validation = Collections.unmodifiableList(validation);
			this.test = Collections.unmodifiableList(test);
			this.targets = Collections.unmodifiableList(targets);
			this.initial = initial;
			int fitting = 0;
			for (Client client : clients)
			{
				fitting += client.samples();
			}
			this.fittingUsers = fitting;
		}

		int clientCount()
		{
			return clients.length;
		}

		Client client(int index)
		{
			return clients[index];
		}

		int validationUsers()
		{
			return validation.size();
		}

		int testUsers()
		{
			return test.size();
		}

		int targetUsers()
		{
			return targets.size();
		}
	}

	/** One client's training in one round: a unit of work. */
	static final class Task
	{
		/** 1-based position in the round, the unit's id. */
		final int unit;
		final int fold;
		final int client;

		private Task(int unit, int fold, int client)
		{
			this.unit = unit;
			this.fold = fold;
			this.client = client;
		}
	}

	/** The federation of a run: who the clients are and how each fold starts. */
	static final class Plan implements PayloadSize.Sized
	{
		private final Config config;
		private final String[] classNames;
		private final String[] scoreNames;
		private final int features;
		private final Fold[] folds;
		private final int labelledUsers;
		private final String protocol;
		private final long membershipBytes;
		private final long rowBytes;

		private Plan(Config config, String[] classNames, String[] scoreNames, int features, Fold[] folds,
				int labelledUsers, String protocol, long membershipBytes, long rowBytes)
		{
			this.config = config;
			this.classNames = classNames;
			this.scoreNames = scoreNames;
			this.features = features;
			this.folds = folds;
			this.labelledUsers = labelledUsers;
			this.protocol = protocol;
			this.membershipBytes = membershipBytes;
			this.rowBytes = rowBytes;
		}

		/**
		 * Plans the federation. Folds, splits and first weights are the central
		 * softmax MLP's; only the dealing of the fitting users to clients is new.
		 */
		static Plan build(Inputs inputs)
		{
			Config config = inputs.config;
			GlobalCorpus corpus = inputs.corpus;
			LinkedHashMap<String,Integer> classIndex = new LinkedHashMap<String,Integer>();
			for (int index = 0; index < inputs.classNames.length; index++)
			{
				classIndex.put(inputs.classNames[index], Integer.valueOf(index));
			}
			HashMap<String,Integer> termIndex = new HashMap<String,Integer>();
			for (String term : inputs.vocabulary)
			{
				if (!termIndex.containsKey(term))
				{
					termIndex.put(term, Integer.valueOf(termIndex.size()));
				}
			}
			int features = inputs.vocabulary.size();
			LinkedHashMap<String,String> labels = inputs.labels();
			List<List<String>> targetFolds = TargetFolds.of(inputs.targets, labels, labels.size());
			HashMap<String,SparseFederatedMlpModelSupport.SparseRow> rowCache =
					new HashMap<String,SparseFederatedMlpModelSupport.SparseRow>();
			long[] rowBytes = new long[1];
			long membershipBytes = 16L;
			Fold[] folds = new Fold[targetFolds.size()];
			for (int foldIndex = 0; foldIndex < folds.length; foldIndex++)
			{
				List<String> foldTargets = targetFolds.get(foldIndex);
				HashSet<String> heldOut = new HashSet<String>(foldTargets);
				ArrayList<String> train = new ArrayList<String>();
				ArrayList<String> test = new ArrayList<String>();
				for (String user : corpus.users())
				{
					if (heldOut.contains(user) || !labels.containsKey(user))
					{
						continue;
					}
					if (inputs.centralTrain.contains(user))
					{
						train.add(user);
					}
					else if (inputs.centralTest.contains(user))
					{
						test.add(user);
					}
				}
				TreeMap<String,List<String>> trainByClass = new TreeMap<String,List<String>>();
				for (String user : train)
				{
					String label = labels.get(user);
					List<String> members = trainByClass.get(label);
					if (members == null)
					{
						members = new ArrayList<String>();
						trainByClass.put(label, members);
					}
					members.add(user);
				}
				if (trainByClass.size() < 2)
				{
					throw new IllegalStateException("MLP needs at least two followee classes in the training "
							+ "set after the recommendation targets are held out; it has "
							+ trainByClass.size() + ".");
				}
				SvmReproducibility.Split inner = SvmReproducibility.stratifiedSplit(trainByClass, VALIDATION_SHARE);
				TreeMap<String,List<String>> fittingByClass = new TreeMap<String,List<String>>();
				for (String user : inner.getTrainUsers())
				{
					if (row(user, corpus, termIndex, classIndex, rowCache, rowBytes) == null)
					{
						continue;
					}
					String label = labels.get(user);
					List<String> members = fittingByClass.get(label);
					if (members == null)
					{
						members = new ArrayList<String>();
						fittingByClass.put(label, members);
					}
					members.add(user);
				}
				long partitionSeed = derive(config.seed, PURPOSE_PARTITION, foldIndex);
				List<List<String>> membership = config.labelSkew
						? partitionDirichlet(fittingByClass, config.clients, config.dirichletAlpha, partitionSeed)
						: partitionIid(fittingByClass, config.clients, partitionSeed);
				if (membership.isEmpty())
				{
					throw new IllegalStateException(
							"Federated MLP has no fitting users in fold " + (foldIndex + 1) + ".");
				}
				SparseFederatedMlpModelSupport.SparseMlpModel initial =
						SparseFederatedMlpModelSupport.SparseMlpModel.create(
								features,
								config.hiddenLayers,
								config.hiddenNeurons,
								inputs.classNames.length,
								INITIAL_WEIGHT_SEED + foldIndex);
				long upperValues = initial.parameterCount()
						- (long)initial.getInputSize() * config.hiddenNeurons;
				Client[] clients = new Client[membership.size()];
				for (int clientIndex = 0; clientIndex < clients.length; clientIndex++)
				{
					ArrayList<SparseFederatedMlpModelSupport.SparseRow> rows =
							new ArrayList<SparseFederatedMlpModelSupport.SparseRow>();
					BitSet touched = new BitSet(Math.max(1, features));
					long nonZeros = 0L;
					for (String user : membership.get(clientIndex))
					{
						SparseFederatedMlpModelSupport.SparseRow row = rowCache.get(user);
						rows.add(row);
						nonZeros += row.size();
						for (int position = 0; position < row.size(); position++)
						{
							if (row.indexAt(position) < features)
							{
								touched.set(row.indexAt(position));
							}
						}
						membershipBytes += PayloadSize.string(user) + 4L;
					}
					int touchedRows = touched.cardinality();
					double costMs = config.localEpochs
							* (COST_FIRST_LAYER_MS * nonZeros * config.hiddenNeurons
									+ COST_UPPER_LAYER_MS * rows.size() * (double)upperValues
									+ COST_ROW_MS * rows.size());
					long updateBytes = 8L + 4L * touchedRows
							+ 8L * ((long)touchedRows * config.hiddenNeurons + upperValues);
					clients[clientIndex] = new Client(foldIndex, clientIndex, rows, nonZeros, touchedRows,
							costMs, updateBytes);
				}
				folds[foldIndex] = new Fold(
						foldIndex,
						clients,
						rows(inner.getTestUsers(), true, corpus, termIndex, classIndex, rowCache, rowBytes),
						rows(test, true, corpus, termIndex, classIndex, rowCache, rowBytes),
						rows(foldTargets, false, corpus, termIndex, classIndex, rowCache, rowBytes),
						initial);
			}
			return new Plan(
					config,
					inputs.classNames.clone(),
					classIndex.keySet().toArray(new String[0]),
					features,
					folds,
					labels.size(),
					TargetFolds.describe(targetFolds, labels.size()),
					membershipBytes,
					rowBytes[0]);
		}

		Config config()
		{
			return config;
		}

		int foldCount()
		{
			return folds.length;
		}

		Fold fold(int index)
		{
			return folds[index];
		}

		int features()
		{
			return features;
		}

		int classCount()
		{
			return classNames.length;
		}

		/** Client units of one full round over every fold: the most a round schedules. */
		int clientsPerRound()
		{
			int count = 0;
			for (Fold fold : folds)
			{
				count += participantCount(fold);
			}
			return count;
		}

		/** Wire size of what every node learns from the plan: who belongs to which client. */
		public long payloadBytes()
		{
			return membershipBytes;
		}

		/** Heap the sparse rows of the plan hold. */
		long rowBytes()
		{
			return rowBytes;
		}

		/** Wire size of one fold's model. */
		long modelBytes()
		{
			return folds.length == 0 ? 0L : folds[0].initial.payloadBytes();
		}

		/** The state before the first round: every fold at its seeded weights. */
		State initialState()
		{
			FoldState[] states = new FoldState[folds.length];
			for (int index = 0; index < folds.length; index++)
			{
				states[index] = new FoldState(folds[index].initial, null, Double.POSITIVE_INFINITY, 0, 0, 0,
						true, "");
			}
			return new State(0, states, Collections.<RoundRecord>emptyList(), 0L);
		}

		/** Whether another round has work. */
		boolean hasWork(State state)
		{
			for (FoldState fold : state.folds)
			{
				if (fold.active)
				{
					return true;
				}
			}
			return false;
		}

		private int participantCount(Fold fold)
		{
			int count = fold.clients.length;
			if (config.clientFraction >= 1.0)
			{
				return count;
			}
			return Math.max(1, Math.min(count, (int)Math.round(config.clientFraction * count)));
		}

		/** The clients of {@code fold} that train in {@code round}, ascending. */
		int[] participants(int fold, int round)
		{
			Fold plan = folds[fold];
			int count = plan.clients.length;
			int chosen = participantCount(plan);
			int[] order = new int[count];
			for (int index = 0; index < count; index++)
			{
				order[index] = index;
			}
			if (chosen < count)
			{
				Random random = new Random(derive(config.seed, PURPOSE_SAMPLING, fold, round));
				for (int index = count - 1; index > 0; index--)
				{
					int other = random.nextInt(index + 1);
					int swap = order[index];
					order[index] = order[other];
					order[other] = swap;
				}
			}
			int[] selected = Arrays.copyOf(order, chosen);
			Arrays.sort(selected);
			return selected;
		}

		/** The work of round {@code round}: every participating client of every running fold. */
		List<Task> tasks(State state, int round)
		{
			ArrayList<Task> tasks = new ArrayList<Task>();
			for (Fold fold : folds)
			{
				if (!state.folds[fold.index].active)
				{
					continue;
				}
				for (int client : participants(fold.index, round))
				{
					tasks.add(new Task(tasks.size() + 1, fold.index, client));
				}
			}
			return Collections.unmodifiableList(tasks);
		}

		Client client(Task task)
		{
			return folds[task.fold].clients[task.client];
		}

		/** One client's local training in {@code round}, from the state the round started in. */
		SparseFederatedMlpModelSupport.ModelUpdate train(State state, int round, Task task)
		{
			Client client = client(task);
			return state.folds[task.fold].global.localUpdate(
					client.rows,
					config.localEpochs,
					config.learningRate,
					config.l2,
					config.proximalMu,
					derive(config.seed, PURPOSE_LOCAL, task.fold, round, task.client));
		}

		/**
		 * The server's step: averages the round's updates into every running
		 * fold's model, validates it, and decides which folds go on.
		 *
		 * @param updates the round's updates by unit id; exactly one per task
		 */
		State aggregate(State state, int round, List<Task> tasks,
				Map<Integer,SparseFederatedMlpModelSupport.ModelUpdate> updates)
		{
			if (round != state.round + 1)
			{
				throw new IllegalStateException("Federated round " + round + " cannot follow round "
						+ state.round + ".");
			}
			if (updates == null || updates.size() != tasks.size())
			{
				throw new IllegalStateException("Federated round " + round + " has "
						+ (updates == null ? 0 : updates.size()) + " update(s) for " + tasks.size()
						+ " client(s).");
			}
			FoldState[] next = state.folds.clone();
			ArrayList<RoundRecord> history = new ArrayList<RoundRecord>(state.history);
			long download = 0L;
			int position = 0;
			while (position < tasks.size())
			{
				int foldIndex = tasks.get(position).fold;
				ArrayList<SparseFederatedMlpModelSupport.ModelUpdate> foldUpdates =
						new ArrayList<SparseFederatedMlpModelSupport.ModelUpdate>();
				long samples = 0L;
				long upload = 0L;
				double largest = 0.0;
				int end = position;
				while (end < tasks.size() && tasks.get(end).fold == foldIndex)
				{
					Task task = tasks.get(end);
					SparseFederatedMlpModelSupport.ModelUpdate update = updates.get(Integer.valueOf(task.unit));
					if (update == null)
					{
						throw new IllegalStateException("Federated round " + round + " has no update of client "
								+ client(task).key() + ".");
					}
					if (update.getSamples() != client(task).samples())
					{
						throw new IllegalStateException("Federated round " + round + ": client "
								+ client(task).key() + " trained on " + update.getSamples() + " row(s), not its "
								+ client(task).samples() + ".");
					}
					foldUpdates.add(update);
					samples += update.getSamples();
					upload += update.payloadBytes();
					largest = Math.max(largest, update.maxAbsolute());
					end++;
				}
				Fold plan = folds[foldIndex];
				FoldState before = state.folds[foldIndex];
				SparseFederatedMlpModelSupport.SparseMlpModel global = before.global.plusAverage(foldUpdates);
				boolean validating = !plan.validation.isEmpty();
				double loss = validating ? global.meanCrossEntropyOfRows(plan.validation) : Double.NaN;
				double accuracy = validating ? accuracy(global, plan.validation) : Double.NaN;
				SparseFederatedMlpModelSupport.SparseMlpModel best = before.best;
				double bestLoss = before.bestLoss;
				int bestRound = before.bestRound;
				int sinceBest = before.sinceBest;
				boolean improved = false;
				if (validating)
				{
					//The central engine's rule, with rounds for epochs.
					if (loss < bestLoss - 1e-12)
					{
						best = global;
						bestLoss = loss;
						bestRound = round;
						sinceBest = 0;
						improved = true;
					}
					else
					{
						sinceBest++;
					}
				}
				boolean stoppedEarly = validating && config.patience > 0 && sinceBest >= config.patience;
				boolean active = !stoppedEarly && round < config.rounds;
				String reason = active ? "" : stoppedEarly ? "stopped early" : "round limit reached";
				next[foldIndex] = new FoldState(global, best, bestLoss, bestRound, sinceBest, round, active, reason);
				history.add(new RoundRecord(round, foldIndex, end - position, samples, upload,
						global.payloadBytes(), loss, accuracy, improved, largest));
				download += global.payloadBytes() + 64L;
				position = end;
			}
			return new State(round, next, history, download);
		}

		/** Test accuracy and target scores of every fold's final model. */
		Outcome finish(State state)
		{
			TreeMap<String,TreeMap<String,Double>> scores = new TreeMap<String,TreeMap<String,Double>>();
			ArrayList<String> report = new ArrayList<String>();
			ArrayList<Map<String,Object>> foldJson = new ArrayList<Map<String,Object>>();
			report.add("MLP (Softmax MLP, federated averaging) on the whole run: " + protocol + "; "
					+ features + " features, " + classNames.length + " classes.");
			report.add("Federated averaging: " + config.describe() + ".");
			int correct = 0;
			int tested = 0;
			long uploaded = 0L;
			long downloaded = 0L;
			int updates = 0;
			for (RoundRecord record : state.history)
			{
				uploaded += record.uploadBytes;
				downloaded += record.modelBytes;
				updates += record.participants;
			}
			for (Fold fold : folds)
			{
				FoldState result = state.folds[fold.index];
				SparseFederatedMlpModelSupport.SparseMlpModel model = result.model();
				int foldCorrect = 0;
				for (SparseFederatedMlpModelSupport.SparseRow row : fold.test)
				{
					if (indexOfMax(model.predict(row)) == row.getLabelIndex())
					{
						foldCorrect++;
					}
				}
				correct += foldCorrect;
				tested += fold.test.size();
				for (SparseFederatedMlpModelSupport.SparseRow row : fold.targets)
				{
					double[] output = model.predict(row);
					TreeMap<String,Double> scored = new TreeMap<String,Double>();
					for (int index = 0; index < output.length && index < scoreNames.length; index++)
					{
						scored.put(scoreNames[index], Double.valueOf(Math.max(0.0, output[index])));
					}
					scores.put(row.getUser(), scored);
				}
				String fingerprint = model.fingerprint();
				report.add(describe(fold, result) + "; model " + fingerprint.substring(0, 12) + ".");
				LinkedHashMap<String,Object> json = new LinkedHashMap<String,Object>();
				json.put("fold", Integer.valueOf(fold.index + 1));
				json.put("clients", Integer.valueOf(fold.clients.length));
				ArrayList<Object> sizes = new ArrayList<Object>();
				for (Client client : fold.clients)
				{
					sizes.add(Integer.valueOf(client.samples()));
				}
				json.put("clientUsers", sizes);
				json.put("fittingUsers", Integer.valueOf(fold.fittingUsers));
				json.put("validationUsers", Integer.valueOf(fold.validation.size()));
				json.put("testUsers", Integer.valueOf(fold.test.size()));
				json.put("targets", Integer.valueOf(fold.targets.size()));
				json.put("rounds", Integer.valueOf(result.rounds));
				json.put("bestRound", Integer.valueOf(result.bestRound));
				json.put("bestValidationLoss", result.bestRound > 0 ? Double.valueOf(result.bestLoss) : null);
				json.put("stop", result.stopReason);
				json.put("testCorrect", Integer.valueOf(foldCorrect));
				json.put("modelSha256", fingerprint);
				foldJson.add(json);
			}
			report.add(String.format(Locale.US,
					"Federated communication: %d client update(s), %s uploaded; %s of global models sent to every node.",
					Integer.valueOf(updates), megabytes(uploaded), megabytes(downloaded)));
			report.add(tested == 0
					? "MLP held-out accuracy skipped: no test users."
					: String.format(Locale.US,
							"MLP held-out accuracy (test split, targets excluded): %.2f%% (%d/%d)",
							Double.valueOf(100.0 * correct / tested), Integer.valueOf(correct),
							Integer.valueOf(tested)));
			LinkedHashMap<String,Object> json = new LinkedHashMap<String,Object>();
			json.put("algorithm", "FedAvg");
			json.put("settings", config.toJson());
			json.put("features", Integer.valueOf(features));
			json.put("classes", Arrays.<Object>asList((Object[])classNames));
			json.put("labelledUsers", Integer.valueOf(labelledUsers));
			json.put("protocol", protocol);
			json.put("rounds", Integer.valueOf(state.round));
			json.put("clientUpdates", Integer.valueOf(updates));
			json.put("uploadedBytes", Long.valueOf(uploaded));
			json.put("modelBytesPerNode", Long.valueOf(downloaded));
			json.put("testCorrect", Integer.valueOf(correct));
			json.put("testUsers", Integer.valueOf(tested));
			json.put("folds", foldJson);
			ArrayList<Object> rounds = new ArrayList<Object>();
			for (RoundRecord record : state.history)
			{
				rounds.add(record.toJson());
			}
			json.put("history", rounds);
			return new Outcome(scores, report, correct, tested, state, json);
		}

		private String describe(Fold fold, FoldState result)
		{
			int smallest = Integer.MAX_VALUE;
			int largest = 0;
			for (Client client : fold.clients)
			{
				smallest = Math.min(smallest, client.samples());
				largest = Math.max(largest, client.samples());
			}
			String label = folds.length == 1 ? "" : " (fold " + (fold.index + 1) + " of " + folds.length + ")";
			String training = result.bestRound <= 0
					? result.rounds + " round(s) without a validation split"
					: result.rounds + " round(s); best validation cross-entropy "
							+ String.format(Locale.US, "%.4f", Double.valueOf(result.bestLoss))
							+ " at round " + result.bestRound + " (" + result.stopReason + ")";
			return "Federated softmax MLP" + label + ": " + fold.fittingUsers + " fitting users in "
					+ fold.clients.length + " client" + (fold.clients.length == 1 ? "" : "s") + " ("
					+ (smallest == largest ? smallest + " each" : smallest + " to " + largest + " users")
					+ "), " + fold.validation.size() + " validation and " + fold.test.size()
					+ " test users; " + training;
		}
	}

	// ---------------------------------------------------------------- state

	/** One fold between rounds. */
	static final class FoldState
	{
		/** The model after the last aggregation: what the next round starts from. */
		final SparseFederatedMlpModelSupport.SparseMlpModel global;
		/** The model with the best validation loss so far; null before one was measured. */
		final SparseFederatedMlpModelSupport.SparseMlpModel best;
		final double bestLoss;
		final int bestRound;
		final int sinceBest;
		/** Rounds this fold has run. */
		final int rounds;
		final boolean active;
		final String stopReason;

		private FoldState(SparseFederatedMlpModelSupport.SparseMlpModel global,
				SparseFederatedMlpModelSupport.SparseMlpModel best, double bestLoss, int bestRound,
				int sinceBest, int rounds, boolean active, String stopReason)
		{
			this.global = global;
			this.best = best;
			this.bestLoss = bestLoss;
			this.bestRound = bestRound;
			this.sinceBest = sinceBest;
			this.rounds = rounds;
			this.active = active;
			this.stopReason = stopReason;
		}

		/** The model that scores the fold: the best validated one, else the last. */
		SparseFederatedMlpModelSupport.SparseMlpModel model()
		{
			return best != null ? best : global;
		}
	}

	/** What one fold's server step did in one round. */
	static final class RoundRecord
	{
		final int round;
		final int fold;
		final int participants;
		final long samples;
		final long uploadBytes;
		final long modelBytes;
		final double validationLoss;
		final double validationAccuracy;
		final boolean improved;
		final double largestUpdate;

		private RoundRecord(int round, int fold, int participants, long samples, long uploadBytes,
				long modelBytes, double validationLoss, double validationAccuracy, boolean improved,
				double largestUpdate)
		{
			this.round = round;
			this.fold = fold;
			this.participants = participants;
			this.samples = samples;
			this.uploadBytes = uploadBytes;
			this.modelBytes = modelBytes;
			this.validationLoss = validationLoss;
			this.validationAccuracy = validationAccuracy;
			this.improved = improved;
			this.largestUpdate = largestUpdate;
		}

		Map<String,Object> toJson()
		{
			LinkedHashMap<String,Object> json = new LinkedHashMap<String,Object>();
			json.put("round", Integer.valueOf(round));
			json.put("fold", Integer.valueOf(fold + 1));
			json.put("clients", Integer.valueOf(participants));
			json.put("users", Long.valueOf(samples));
			json.put("uploadBytes", Long.valueOf(uploadBytes));
			json.put("modelBytes", Long.valueOf(modelBytes));
			json.put("validationLoss", Double.isNaN(validationLoss) ? null : Double.valueOf(validationLoss));
			json.put("validationAccuracy",
					Double.isNaN(validationAccuracy) ? null : Double.valueOf(validationAccuracy));
			json.put("improved", Boolean.valueOf(improved));
			json.put("largestUpdate", Double.valueOf(largestUpdate));
			return json;
		}
	}

	/** Every fold after a number of rounds; what all nodes hold between rounds. */
	static final class State implements PayloadSize.Sized
	{
		/** Rounds aggregated so far. */
		final int round;
		private final FoldState[] folds;
		private final List<RoundRecord> history;
		private final long downloadBytes;

		private State(int round, FoldState[] folds, List<RoundRecord> history, long downloadBytes)
		{
			this.round = round;
			this.folds = folds;
			this.history = Collections.unmodifiableList(history);
			this.downloadBytes = downloadBytes;
		}

		FoldState fold(int index)
		{
			return folds[index];
		}

		List<RoundRecord> history()
		{
			return history;
		}

		/** Wire size of what a node receives after a round: the new model of every fold that ran. */
		public long payloadBytes()
		{
			return downloadBytes;
		}

		/** SHA-256 over every fold's models and counters: equal states are bit-identical. */
		String fingerprint()
		{
			MessageDigest digest = sha256();
			feed(digest, Integer.toString(round));
			for (FoldState fold : folds)
			{
				feed(digest, fold.global.fingerprint());
				feed(digest, fold.best == null ? "-" : fold.best.fingerprint());
				feed(digest, Long.toString(Double.doubleToLongBits(fold.bestLoss)));
				feed(digest, fold.bestRound + "/" + fold.sinceBest + "/" + fold.rounds + "/" + fold.active);
			}
			for (RoundRecord record : history)
			{
				feed(digest, record.round + "/" + record.fold + "/" + record.participants + "/" + record.samples
						+ "/" + Double.doubleToLongBits(record.validationLoss));
			}
			return hex(digest.digest());
		}
	}

	/** The result of a federation: every target's scores and the run's report. */
	static final class Outcome
	{
		final TreeMap<String,TreeMap<String,Double>> scores;
		final List<String> report;
		final int correct;
		final int tested;
		final State state;
		private final Map<String,Object> json;

		private Outcome(TreeMap<String,TreeMap<String,Double>> scores, List<String> report, int correct,
				int tested, State state, Map<String,Object> json)
		{
			this.scores = scores;
			this.report = Collections.unmodifiableList(report);
			this.correct = correct;
			this.tested = tested;
			this.state = state;
			this.json = json;
		}

		/** The whole run as nested maps and lists: settings, folds and every round. */
		Map<String,Object> toJson()
		{
			return json;
		}

		/** SHA-256 over every score's bits: equal outcomes scored every target identically. */
		String scoresFingerprint()
		{
			MessageDigest digest = sha256();
			for (Map.Entry<String,TreeMap<String,Double>> target : scores.entrySet())
			{
				feed(digest, target.getKey());
				for (Map.Entry<String,Double> score : target.getValue().entrySet())
				{
					feed(digest, score.getKey());
					feed(digest, Long.toString(Double.doubleToLongBits(score.getValue().doubleValue())));
				}
			}
			return hex(digest.digest());
		}
	}

	/**
	 * The whole federation in one thread, round after round and client after
	 * client: the definition every scheduled execution must reproduce bit for
	 * bit.
	 */
	static Outcome runSequential(Plan plan)
	{
		State state = plan.initialState();
		while (plan.hasWork(state))
		{
			int round = state.round + 1;
			List<Task> tasks = plan.tasks(state, round);
			TreeMap<Integer,SparseFederatedMlpModelSupport.ModelUpdate> updates =
					new TreeMap<Integer,SparseFederatedMlpModelSupport.ModelUpdate>();
			for (Task task : tasks)
			{
				updates.put(Integer.valueOf(task.unit), plan.train(state, round, task));
			}
			state = plan.aggregate(state, round, tasks, updates);
		}
		return plan.finish(state);
	}

	// ------------------------------------------------------------ partition

	/**
	 * Deals the users of every class to the clients in turn, after a seeded
	 * shuffle of each class and continuing the turn from one class to the
	 * next: clients differ by at most one user and share every class almost
	 * evenly (an IID federation).
	 *
	 * @return the clients' users, each list in name order; never an empty client
	 */
	static List<List<String>> partitionIid(Map<String,? extends List<String>> usersByClass, int clients,
			long seed)
	{
		TreeMap<String,List<String>> classes = shuffledClasses(usersByClass, seed);
		int total = 0;
		for (List<String> members : classes.values())
		{
			total += members.size();
		}
		ArrayList<List<String>> result = new ArrayList<List<String>>();
		if (total == 0)
		{
			return result;
		}
		int count = Math.max(1, Math.min(clients, total));
		for (int index = 0; index < count; index++)
		{
			result.add(new ArrayList<String>());
		}
		int next = 0;
		for (List<String> members : classes.values())
		{
			for (String member : members)
			{
				result.get(next % count).add(member);
				next++;
			}
		}
		for (List<String> members : result)
		{
			Collections.sort(members);
		}
		return result;
	}

	/**
	 * Deals every class to the clients in proportions drawn from a symmetric
	 * Dirichlet distribution with concentration {@code alpha} (Hsu et al.
	 * 2019): a small alpha gives every client a few classes and very different
	 * sizes, a large one approaches the even deal. Clients that receive nobody
	 * are dropped.
	 *
	 * @return the clients' users, each list in name order; never an empty client
	 */
	static List<List<String>> partitionDirichlet(Map<String,? extends List<String>> usersByClass,
			int clients, double alpha, long seed)
	{
		TreeMap<String,List<String>> classes = shuffledClasses(usersByClass, seed);
		int total = 0;
		for (List<String> members : classes.values())
		{
			total += members.size();
		}
		ArrayList<List<String>> result = new ArrayList<List<String>>();
		if (total == 0)
		{
			return result;
		}
		int count = Math.max(1, Math.min(clients, total));
		for (int index = 0; index < count; index++)
		{
			result.add(new ArrayList<String>());
		}
		double concentration = Double.isFinite(alpha) && alpha > 0.0 ? alpha : 1.0;
		for (Map.Entry<String,List<String>> entry : classes.entrySet())
		{
			Random random = new Random(derive(seed, PURPOSE_DIRICHLET, entry.getKey().hashCode()));
			//Gamma draws in logarithms, so a tiny alpha concentrates instead of underflowing.
			double[] logShares = new double[count];
			double largest = Double.NEGATIVE_INFINITY;
			for (int index = 0; index < count; index++)
			{
				logShares[index] = logGamma(concentration, random);
				largest = Math.max(largest, logShares[index]);
			}
			double[] shares = new double[count];
			for (int index = 0; index < count; index++)
			{
				shares[index] = StrictMath.exp(logShares[index] - largest);
			}
			int[] counts = apportion(shares, entry.getValue().size());
			int position = 0;
			for (int index = 0; index < count; index++)
			{
				for (int taken = 0; taken < counts[index]; taken++)
				{
					result.get(index).add(entry.getValue().get(position++));
				}
			}
		}
		ArrayList<List<String>> kept = new ArrayList<List<String>>();
		for (List<String> members : result)
		{
			if (!members.isEmpty())
			{
				Collections.sort(members);
				kept.add(members);
			}
		}
		return kept;
	}

	/** Every class's users in name order, then shuffled by a seed of the class. */
	private static TreeMap<String,List<String>> shuffledClasses(
			Map<String,? extends List<String>> usersByClass, long seed)
	{
		TreeMap<String,List<String>> classes = new TreeMap<String,List<String>>();
		if (usersByClass == null)
		{
			return classes;
		}
		for (Map.Entry<String,? extends List<String>> entry : usersByClass.entrySet())
		{
			if (entry.getKey() == null || entry.getValue() == null || entry.getValue().isEmpty())
			{
				continue;
			}
			ArrayList<String> members = new ArrayList<String>(new TreeSet<String>(entry.getValue()));
			Collections.shuffle(members, new Random(derive(seed, PURPOSE_PARTITION, entry.getKey().hashCode())));
			classes.put(entry.getKey(), members);
		}
		return classes;
	}

	/**
	 * Whole numbers proportional to {@code shares} that add up to
	 * {@code total}: every share's integer part, then the largest remainders
	 * (the lowest index wins a tie).
	 */
	static int[] apportion(double[] shares, int total)
	{
		int count = shares.length;
		int[] counts = new int[count];
		if (count == 0 || total <= 0)
		{
			return counts;
		}
		double sum = 0.0;
		for (double share : shares)
		{
			sum += share > 0.0 && Double.isFinite(share) ? share : 0.0;
		}
		double[] remainders = new double[count];
		int assigned = 0;
		for (int index = 0; index < count; index++)
		{
			double share = shares[index] > 0.0 && Double.isFinite(shares[index]) ? shares[index] : 0.0;
			double ideal = sum > 0.0 ? total * (share / sum) : (double)total / count;
			int whole = (int)Math.max(0.0, Math.min(total, Math.floor(ideal)));
			counts[index] = whole;
			remainders[index] = ideal - whole;
			assigned += whole;
		}
		while (assigned > total)
		{
			int largest = 0;
			for (int index = 1; index < count; index++)
			{
				if (counts[index] > counts[largest])
				{
					largest = index;
				}
			}
			counts[largest]--;
			assigned--;
		}
		while (assigned < total)
		{
			int largest = 0;
			for (int index = 1; index < count; index++)
			{
				if (remainders[index] > remainders[largest])
				{
					largest = index;
				}
			}
			counts[largest]++;
			remainders[largest] = Double.NEGATIVE_INFINITY;
			assigned++;
			boolean exhausted = true;
			for (double remainder : remainders)
			{
				exhausted &= remainder == Double.NEGATIVE_INFINITY;
			}
			if (exhausted)
			{
				Arrays.fill(remainders, 0.0);
			}
		}
		return counts;
	}

	/**
	 * The logarithm of a Gamma(shape, 1) draw (Marsaglia and Tsang 2000; below
	 * shape 1 through Gamma(shape + 1) U^(1/shape)).
	 */
	private static double logGamma(double shape, Random random)
	{
		if (shape < 1.0)
		{
			double uniform = random.nextDouble();
			return logGamma(shape + 1.0, random)
					+ StrictMath.log(uniform > 0.0 ? uniform : Double.MIN_VALUE) / shape;
		}
		double d = shape - 1.0 / 3.0;
		double c = 1.0 / StrictMath.sqrt(9.0 * d);
		while (true)
		{
			double normal = random.nextGaussian();
			double cube = 1.0 + c * normal;
			if (cube <= 0.0)
			{
				continue;
			}
			cube = cube * cube * cube;
			double uniform = random.nextDouble();
			double square = normal * normal;
			if (uniform < 1.0 - 0.0331 * square * square
					|| StrictMath.log(uniform > 0.0 ? uniform : Double.MIN_VALUE)
							< 0.5 * square + d * (1.0 - cube + StrictMath.log(cube)))
			{
				return StrictMath.log(d * cube);
			}
		}
	}

	// -------------------------------------------------------------- helpers

	/** A seed for one use, derived from the federation's seed and what it is for. */
	static long derive(long seed, int purpose, long... coordinates)
	{
		long state = splitMix(seed ^ (0x51ED270B0F1L * purpose));
		for (long coordinate : coordinates)
		{
			state = splitMix(state ^ coordinate);
		}
		return state;
	}

	private static long splitMix(long value)
	{
		long mixed = value + 0x9E3779B97F4A7C15L;
		mixed = (mixed ^ (mixed >>> 30)) * 0xBF58476D1CE4E5B9L;
		mixed = (mixed ^ (mixed >>> 27)) * 0x94D049BB133111EBL;
		return mixed ^ (mixed >>> 31);
	}

	/** Index of the largest value; the first wins a tie, as in the central engine. */
	static int indexOfMax(double[] values)
	{
		int best = 0;
		for (int index = 1; index < values.length; index++)
		{
			if (values[index] > values[best])
			{
				best = index;
			}
		}
		return best;
	}

	private static double accuracy(SparseFederatedMlpModelSupport.SparseMlpModel model,
			List<SparseFederatedMlpModelSupport.SparseRow> rows)
	{
		int correct = 0;
		for (SparseFederatedMlpModelSupport.SparseRow row : rows)
		{
			if (indexOfMax(model.predict(row)) == row.getLabelIndex())
			{
				correct++;
			}
		}
		return rows.isEmpty() ? Double.NaN : (double)correct / rows.size();
	}

	private static List<SparseFederatedMlpModelSupport.SparseRow> rows(
			List<String> users, boolean requireLabel, GlobalCorpus corpus, Map<String,Integer> termIndex,
			Map<String,Integer> classIndex, Map<String,SparseFederatedMlpModelSupport.SparseRow> cache,
			long[] bytes)
	{
		ArrayList<SparseFederatedMlpModelSupport.SparseRow> rows =
				new ArrayList<SparseFederatedMlpModelSupport.SparseRow>();
		for (String user : users)
		{
			SparseFederatedMlpModelSupport.SparseRow row = row(user, corpus, termIndex, classIndex, cache, bytes);
			if (row != null && (!requireLabel || row.getLabelIndex() >= 0))
			{
				rows.add(row);
			}
		}
		return rows;
	}

	/** A user's row (null without a document); a label outside the classes is -1. */
	private static SparseFederatedMlpModelSupport.SparseRow row(String user, GlobalCorpus corpus,
			Map<String,Integer> termIndex, Map<String,Integer> classIndex,
			Map<String,SparseFederatedMlpModelSupport.SparseRow> cache, long[] bytes)
	{
		SparseFederatedMlpModelSupport.SparseRow cached = cache.get(user);
		if (cached != null || cache.containsKey(user))
		{
			return cached;
		}
		Map<String,Double> document = corpus.vectorOf(user);
		SparseFederatedMlpModelSupport.SparseRow row = null;
		if (document != null)
		{
			long[] packed = new long[document.size()];
			double[] raw = new double[document.size()];
			int count = 0;
			for (Map.Entry<String,Double> entry : document.entrySet())
			{
				Integer feature = termIndex.get(entry.getKey());
				Double value = entry.getValue();
				if (feature != null && value != null && Double.isFinite(value.doubleValue())
						&& value.doubleValue() != 0.0)
				{
					packed[count] = ((long)feature.intValue() << 32) | count;
					raw[count] = value.doubleValue();
					count++;
				}
			}
			packed = Arrays.copyOf(packed, count);
			Arrays.sort(packed);
			int[] indexes = new int[count];
			double[] values = new double[count];
			for (int position = 0; position < count; position++)
			{
				indexes[position] = (int)(packed[position] >>> 32);
				values[position] = raw[(int)(packed[position] & 0xFFFFFFFFL)];
			}
			Integer label = classIndex.get(corpus.labelOf(user));
			row = new SparseFederatedMlpModelSupport.SparseRow(user, indexes, values,
					label == null ? -1 : label.intValue());
			bytes[0] += 64L + 12L * count;
		}
		cache.put(user, row);
		return row;
	}

	private static String megabytes(long bytes)
	{
		return String.format(Locale.US, "%.2f MB", Double.valueOf(bytes / (1024.0 * 1024.0)));
	}

	private static MessageDigest sha256()
	{
		try
		{
			return MessageDigest.getInstance("SHA-256");
		}
		catch (NoSuchAlgorithmException impossible)
		{
			throw new IllegalStateException(impossible);
		}
	}

	private static void feed(MessageDigest digest, String value)
	{
		byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
		digest.update((byte)(bytes.length >>> 24));
		digest.update((byte)(bytes.length >>> 16));
		digest.update((byte)(bytes.length >>> 8));
		digest.update((byte)bytes.length);
		digest.update(bytes);
	}

	private static String hex(byte[] bytes)
	{
		StringBuilder text = new StringBuilder();
		for (byte value : bytes)
		{
			text.append(String.format("%02x", Integer.valueOf(value & 0xff)));
		}
		return text.toString();
	}
}
