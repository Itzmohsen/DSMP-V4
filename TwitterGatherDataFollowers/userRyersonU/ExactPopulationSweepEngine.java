package TwitterGatherDataFollowers.userRyersonU;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import weka.classifiers.functions.SMO;
import weka.core.Instances;

/**
 * An isolated population benchmark. Complete target-user jobs are assigned to
 * workers, so node count changes scheduling without changing a target's model
 * input or score calculation.
 */
final class ExactPopulationSweepEngine
{
	enum Algorithm
	{
		SIMILARITY("Similarity", RecommendationCandidateType.USER),
		SVM("SVM", RecommendationCandidateType.FOLLOWEE),
		DOC2VEC("Doc2Vec", RecommendationCandidateType.USER);

		private final String displayName;
		private final RecommendationCandidateType candidateType;

		Algorithm(String displayName, RecommendationCandidateType candidateType)
		{
			this.displayName = displayName;
			this.candidateType = candidateType;
		}

		RecommendationCandidateType getCandidateType()
		{
			return candidateType;
		}

		public String toString()
		{
			return displayName;
		}
	}

	enum WorkerState
	{
		READY,
		RUNNING,
		COMPLETE,
		FAILED,
		CANCELLED
	}

	interface Listener
	{
		void onPhase(String phase, String detail);

		void onProgress(boolean baseline, int completed, int total);

		void onWorker(
				int workerIndex,
				WorkerState state,
				String target,
				int completed,
				int assigned,
				long elapsedMillis,
				String detail);

		void onTargetResult(String target, LinkedHashMap<String,Double> scores);
	}

	static final class CancellationToken
	{
		private final AtomicBoolean cancelled = new AtomicBoolean();

		void cancel()
		{
			cancelled.set(true);
		}

		boolean isCancelled()
		{
			return cancelled.get();
		}

		void throwIfCancelled() throws InterruptedException
		{
			if (cancelled.get() || Thread.currentThread().isInterrupted())
			{
				throw new InterruptedException("Population sweep cancelled.");
			}
		}
	}

	static final class Config
	{
		final File datasetFile;
		final Algorithm algorithm;
		final int nodes;
		final int topResults;
		final int targetLimit;
		final String beginDate;
		final String endDate;
		final boolean removeHashtags;
		final boolean removeRetweets;
		final boolean removeStopWords;
		final boolean verifyAgainstBaseline;
		final String pythonCommand;
		final AlgorithmParameterSettings algorithmSettings;

		Config(
				File datasetFile,
				Algorithm algorithm,
				int nodes,
				int topResults,
				int targetLimit,
				String beginDate,
				String endDate,
				boolean removeHashtags,
				boolean removeRetweets,
				boolean removeStopWords,
				boolean verifyAgainstBaseline,
				String pythonCommand)
		{
			this(
					datasetFile,
					algorithm,
					nodes,
					topResults,
					targetLimit,
					beginDate,
					endDate,
					removeHashtags,
					removeRetweets,
					removeStopWords,
					verifyAgainstBaseline,
					pythonCommand,
					AlgorithmParameterSettings.defaults());
		}

		Config(
				File datasetFile,
				Algorithm algorithm,
				int nodes,
				int topResults,
				int targetLimit,
				String beginDate,
				String endDate,
				boolean removeHashtags,
				boolean removeRetweets,
				boolean removeStopWords,
				boolean verifyAgainstBaseline,
				String pythonCommand,
				AlgorithmParameterSettings algorithmSettings)
		{
			this.datasetFile = datasetFile;
			this.algorithm = algorithm;
			this.nodes = Math.max(1, nodes);
			this.topResults = Math.max(1, topResults);
			this.targetLimit = Math.max(0, targetLimit);
			this.beginDate = cleanDate(beginDate, "2007-01-01");
			this.endDate = cleanDate(endDate, "2099-12-31");
			this.removeHashtags = removeHashtags;
			this.removeRetweets = removeRetweets;
			this.removeStopWords = removeStopWords;
			this.verifyAgainstBaseline = verifyAgainstBaseline;
			this.pythonCommand = pythonCommand == null
					|| pythonCommand.trim().length() == 0
					? "python3" : pythonCommand.trim();
			this.algorithmSettings = algorithmSettings == null
					? AlgorithmParameterSettings.defaults()
					: algorithmSettings.copy();
		}

		private static String cleanDate(String value, String fallback)
		{
			return value == null || value.trim().length() == 0
					? fallback : value.trim();
		}
	}

	static final class TargetResult
	{
		private final String target;
		private final LinkedHashMap<String,Double> topScores;
		private final String digest;

		private TargetResult(
				String target,
				LinkedHashMap<String,Double> topScores,
				String digest)
		{
			this.target = target;
			this.topScores = topScores;
			this.digest = digest;
		}

		String getTarget()
		{
			return target;
		}

		LinkedHashMap<String,Double> getTopScores()
		{
			return new LinkedHashMap<String,Double>(topScores);
		}

		String getDigest()
		{
			return digest;
		}
	}

	static final class WorkerMetrics
	{
		private final int workerIndex;
		private final int assignedTargets;
		private final int completedTargets;
		private final long elapsedMillis;

		private WorkerMetrics(
				int workerIndex,
				int assignedTargets,
				int completedTargets,
				long elapsedMillis)
		{
			this.workerIndex = workerIndex;
			this.assignedTargets = assignedTargets;
			this.completedTargets = completedTargets;
			this.elapsedMillis = elapsedMillis;
		}

		int getWorkerIndex()
		{
			return workerIndex;
		}

		int getAssignedTargets()
		{
			return assignedTargets;
		}

		int getCompletedTargets()
		{
			return completedTargets;
		}

		long getElapsedMillis()
		{
			return elapsedMillis;
		}
	}

	static final class Report
	{
		private final Algorithm algorithm;
		private final String datasetDigest;
		private final int totalTargets;
		private final long preparationMillis;
		private final long baselineMillis;
		private final long parallelMillis;
		private final boolean baselineExecuted;
		private final boolean verified;
		private final String resultDigest;
		private final LinkedHashMap<String,TargetResult> results;
		private final List<WorkerMetrics> workers;

		private Report(
				Algorithm algorithm,
				String datasetDigest,
				int totalTargets,
				long preparationMillis,
				long baselineMillis,
				long parallelMillis,
				boolean baselineExecuted,
				boolean verified,
				String resultDigest,
				Map<String,TargetResult> results,
				List<WorkerMetrics> workers)
		{
			this.algorithm = algorithm;
			this.datasetDigest = datasetDigest;
			this.totalTargets = totalTargets;
			this.preparationMillis = preparationMillis;
			this.baselineMillis = baselineMillis;
			this.parallelMillis = parallelMillis;
			this.baselineExecuted = baselineExecuted;
			this.verified = verified;
			this.resultDigest = resultDigest;
			this.results = new LinkedHashMap<String,TargetResult>(results);
			this.workers = Collections.unmodifiableList(
					new ArrayList<WorkerMetrics>(workers));
		}

		Algorithm getAlgorithm()
		{
			return algorithm;
		}

		String getDatasetDigest()
		{
			return datasetDigest;
		}

		int getTotalTargets()
		{
			return totalTargets;
		}

		long getPreparationMillis()
		{
			return preparationMillis;
		}

		long getBaselineMillis()
		{
			return baselineMillis;
		}

		long getParallelMillis()
		{
			return parallelMillis;
		}

		boolean isBaselineExecuted()
		{
			return baselineExecuted;
		}

		boolean isVerified()
		{
			return verified;
		}

		String getResultDigest()
		{
			return resultDigest;
		}

		double getSpeedup()
		{
			return baselineExecuted && baselineMillis > 0L && parallelMillis > 0L
					? baselineMillis / (double)parallelMillis : Double.NaN;
		}

		LinkedHashMap<String,TargetResult> getResults()
		{
			return new LinkedHashMap<String,TargetResult>(results);
		}

		List<WorkerMetrics> getWorkers()
		{
			return workers;
		}
	}

	private static final Pattern RETWEET_PATTERN = Pattern.compile("RT @");
	private static final Pattern PUNCTUATION_PATTERN = Pattern.compile("[\\p{P}]");
	private static final Pattern LINK_PATTERN = Pattern.compile(
			"http[a-zA-Z0-9]*|bitly[a-zA-Z0-9]*|www[a-zA-Z0-9]*");
	private static final Pattern SPECIAL_CHARACTER_PATTERN =
			Pattern.compile("[^a-zA-Z\\p{Z}]");
	private static final Pattern NON_ALPHA_PATTERN = Pattern.compile("[^a-zA-Z ]");
	private static final Pattern MULTI_SPACE_PATTERN = Pattern.compile(" +");
	private static final double TEST_SET_PERCENT = 0.30;

	private ExactPopulationSweepEngine()
	{
	}

	static Report run(
			Config config,
			Listener listener,
			CancellationToken cancellationToken)
			throws Exception
	{
		validateConfig(config);
		NodeCapacityRegistry.configureForNodeCount(
				config.nodes,
				RuntimeResourceSettings.currentMaxHeapMb());
		Listener safeListener = listener == null ? new NoOpListener() : listener;
		CancellationToken safeToken = cancellationToken == null
				? new CancellationToken() : cancellationToken;

		int previousSchedulerParallelism =
				RecommendationMemoryCoordinator.snapshot()
						.getRequestedParallelism();
		boolean schedulerChanged = false;
		if (config.algorithm == Algorithm.SVM)
		{
			RecommendationMemoryCoordinator.Snapshot scheduler =
					RecommendationMemoryCoordinator.snapshot();
			if (scheduler.getActiveLeases() > 0
					|| scheduler.getWaitingLeases() > 0)
			{
				throw new IllegalStateException(
						"Another memory-intensive recommendation run is active. "
						+ "Finish or reset it before starting the exact SVM lab.");
			}
			RecommendationMemoryCoordinator.configureRequestedParallelism(
					config.nodes);
			schedulerChanged = true;
		}

		PreparedSvm preparedSvm = null;
		try
		{
			long preparationStart = System.nanoTime();
			safeListener.onPhase(
					"PREPARING",
					"Reading one immutable dataset snapshot for every worker.");
			DatasetSnapshot snapshot = loadSnapshot(config, safeToken);
			List<String> targets =
					selectTargets(snapshot.users, config.targetLimit);
			if (targets.isEmpty())
			{
				throw new IllegalArgumentException(
						"No users retained at least one tweet with three processed words.");
			}

			if (config.algorithm == Algorithm.SVM)
			{
				preparedSvm = prepareSvm(snapshot);
			}
			else if (config.algorithm == Algorithm.DOC2VEC)
			{
				warmDoc2Vec(config, targets.get(0), safeListener, safeToken);
			}
			long preparationMillis = elapsedMillis(preparationStart);

			Map<String,TargetResult> baselineResults =
					Collections.emptyMap();
			long baselineMillis = 0L;
			if (config.verifyAgainstBaseline)
			{
				safeListener.onPhase(
						"BASELINE",
						"Running the same target jobs with one worker.");
				long baselineStart = System.nanoTime();
				ExecutionResult baseline = execute(
						config,
						snapshot,
						preparedSvm,
						targets,
						1,
						true,
						safeListener,
						safeToken);
				baselineMillis = elapsedMillis(baselineStart);
				baselineResults = baseline.results;
			}

			safeListener.onPhase(
					"PARALLEL",
					"Assigning complete target users across capacity-aware "
							+ "worker profiles ("
							+ NodeCapacityRegistry.summary() + "): "
							+ config.nodes + " worker"
							+ (config.nodes == 1 ? "." : "s."));
			long parallelStart = System.nanoTime();
			ExecutionResult parallel = execute(
					config,
					snapshot,
					preparedSvm,
					targets,
					config.nodes,
					false,
					safeListener,
					safeToken);
			long parallelMillis = elapsedMillis(parallelStart);

			String resultDigest = digestResultSet(parallel.results);
			boolean verified = false;
			if (config.verifyAgainstBaseline)
			{
				safeListener.onPhase(
						"VERIFYING",
						"Comparing every target fingerprint with the one-worker baseline.");
				verifyEquivalent(targets, baselineResults, parallel.results);
				verified = true;
			}
			safeListener.onPhase(
					"COMPLETE",
					verified
							? "Every target matched the one-worker baseline."
							: "Parallel scoring completed without baseline verification.");

			return new Report(
					config.algorithm,
					snapshot.datasetDigest,
					targets.size(),
					preparationMillis,
					baselineMillis,
					parallelMillis,
					config.verifyAgainstBaseline,
					verified,
					resultDigest,
					parallel.results,
					parallel.workers);
		}
		finally
		{
			if (preparedSvm != null)
			{
				preparedSvm.close();
			}
			if (schedulerChanged)
			{
				RecommendationMemoryCoordinator.configureRequestedParallelism(
						previousSchedulerParallelism);
			}
		}
	}

	static List<String> supportedAlgorithmNames()
	{
		ArrayList<String> names = new ArrayList<String>();
		for (Algorithm algorithm : Algorithm.values())
		{
			names.add(algorithm.toString());
		}
		return names;
	}

	private static void validateConfig(Config config)
	{
		if (config == null)
		{
			throw new IllegalArgumentException("Population sweep configuration is required.");
		}
		if (config.datasetFile == null || !config.datasetFile.isFile())
		{
			throw new IllegalArgumentException("Choose a readable text dataset first.");
		}
		if (config.algorithm == null)
		{
			throw new IllegalArgumentException("Choose a supported algorithm.");
		}
		parseDate(config.beginDate);
		parseDate(config.endDate);
	}

	private static DatasetSnapshot loadSnapshot(
			Config config,
			CancellationToken token) throws Exception
	{
		MessageDigest datasetDigest = sha256();
		LinkedHashMap<String,LinkedHashMap<String,Double>> documents =
				new LinkedHashMap<String,LinkedHashMap<String,Double>>();
		LinkedHashMap<String,String> userFollowees =
				new LinkedHashMap<String,String>();
		LinkedHashSet<String> users = new LinkedHashSet<String>();
		long lineNumber = 0L;
		long acceptedRows = 0L;
		long malformedRows = 0L;

		try (DigestInputStream digestInput = new DigestInputStream(
					new FileInputStream(config.datasetFile), datasetDigest);
				BufferedReader reader = new BufferedReader(
					new InputStreamReader(digestInput, StandardCharsets.UTF_8),
					128 * 1024))
		{
			String line;
			while ((line = reader.readLine()) != null)
			{
				token.throwIfCancelled();
				lineNumber++;
				if (line.trim().length() == 0)
				{
					malformedRows++;
					continue;
				}
				DatasetRow row;
				try
				{
					row = DatasetRow.parse(line, lineNumber);
				}
				catch (IllegalArgumentException exception)
				{
					malformedRows++;
					continue;
				}
				if (config.algorithm == Algorithm.DOC2VEC)
				{
					if (!userFollowees.containsKey(row.userName))
					{
						userFollowees.put(row.userName, row.referenceUser);
					}
					users.add(row.userName);
					acceptedRows++;
					continue;
				}

				if (!withinExclusiveDateRange(
						row.tweetDate, config.beginDate, config.endDate))
				{
					continue;
				}
				if (!userFollowees.containsKey(row.userName))
				{
					userFollowees.put(row.userName, row.referenceUser);
				}

				List<String> tokens = tokenize(
						row.text,
						config.removeHashtags,
						config.removeRetweets,
						config.removeStopWords);
				if (tokens.size() < 3)
				{
					continue;
				}
				LinkedHashMap<String,Double> document = documents.get(row.userName);
				if (document == null)
				{
					document = new LinkedHashMap<String,Double>();
					documents.put(row.userName, document);
				}
				for (String word : tokens)
				{
					Double count = document.get(word);
					document.put(word, count == null ? 1.0 : count + 1.0);
				}
				users.add(row.userName);
				acceptedRows++;
			}
		}

		if (lineNumber == 0L)
		{
			throw new IllegalArgumentException("The selected dataset is empty.");
		}
		if (acceptedRows == 0L)
		{
			throw new IllegalArgumentException(
					"No dataset rows were available inside the selected date window.");
		}

		LinkedHashMap<String,LinkedHashMap<String,Double>> tfidf =
				config.algorithm == Algorithm.DOC2VEC
						? new LinkedHashMap<String,LinkedHashMap<String,Double>>()
						: calculateTfidf(documents);
		documents.clear();
		return new DatasetSnapshot(
				new ArrayList<String>(users),
				tfidf,
				userFollowees,
				toHex(datasetDigest.digest()),
				lineNumber,
				acceptedRows,
				malformedRows);
	}

	private static List<String> selectTargets(List<String> users, int limit)
	{
		if (limit <= 0 || limit >= users.size())
		{
			return new ArrayList<String>(users);
		}
		return new ArrayList<String>(users.subList(0, limit));
	}

	private static ExecutionResult execute(
			Config config,
			DatasetSnapshot snapshot,
			PreparedSvm preparedSvm,
			List<String> targets,
			int requestedWorkers,
			boolean baseline,
			Listener listener,
			CancellationToken token) throws Exception
	{
		int workerCount = Math.max(1, Math.min(requestedWorkers, targets.size()));
		if (config.algorithm == Algorithm.DOC2VEC)
		{
			return executeDoc2Vec(
					config, targets, workerCount, baseline, listener, token);
		}
		return executeJavaWorkers(
				config,
				snapshot,
				preparedSvm,
				targets,
				workerCount,
				baseline,
				listener,
				token);
	}

	private static ExecutionResult executeJavaWorkers(
			final Config config,
			final DatasetSnapshot snapshot,
			final PreparedSvm preparedSvm,
			List<String> targets,
			int workerCount,
			final boolean baseline,
			final Listener listener,
			final CancellationToken token) throws Exception
	{
		final List<List<String>> assignments = baseline
				? assignRoundRobin(targets, workerCount)
				: NodeCapacityScheduler.assignTargetJobs(targets, workerCount);
		final AtomicInteger completedTotal = new AtomicInteger();
		ExecutorService executor = Executors.newFixedThreadPool(workerCount);
		ArrayList<Future<WorkerResult>> futures =
				new ArrayList<Future<WorkerResult>>();
		try
		{
			for (int workerOffset = 0; workerOffset < workerCount; workerOffset++)
			{
				final int workerIndex = workerOffset + 1;
				final List<String> assignedTargets = assignments.get(workerOffset);
				final NodeCapacityProfile capacityProfile =
						NodeCapacityRegistry.profileForNode(workerIndex);
				futures.add(executor.submit(new Callable<WorkerResult>()
				{
					public WorkerResult call() throws Exception
					{
						long workerStart = System.nanoTime();
						int completed = 0;
						if (!baseline)
						{
							listener.onWorker(
									workerIndex,
									WorkerState.READY,
									null,
									0,
									assignedTargets.size(),
									0L,
									NodeCapacityScheduler.assignmentDetail(
											workerIndex,
											assignedTargets.size()));
						}
						LinkedHashMap<String,TargetResult> results =
								new LinkedHashMap<String,TargetResult>();
						try
						{
							for (String target : assignedTargets)
							{
								token.throwIfCancelled();
								if (!baseline)
								{
									NodeCapacityScheduler.applyPerJobSimulation(
											capacityProfile,
											target,
											workerIndex,
											new NodeCapacityScheduler.CancellationCheck()
											{
												public void throwIfCancelled()
														throws InterruptedException
												{
													token.throwIfCancelled();
												}
											});
								}
								if (!baseline)
								{
									listener.onWorker(
											workerIndex,
											WorkerState.RUNNING,
											target,
											completed,
											assignedTargets.size(),
											elapsedMillis(workerStart),
											capacityProfile.shortLabel()
													+ " | computing a complete "
													+ config.algorithm
													+ " target job.");
								}
								TargetResult result = config.algorithm == Algorithm.SIMILARITY
										? scoreSimilarity(
												target, snapshot.tfidf, config.topResults)
											: scoreSvm(
													target,
													preparedSvm,
													config.topResults,
													workerIndex,
													config.algorithmSettings);
								results.put(target, result);
								completed++;
								int totalComplete = completedTotal.incrementAndGet();
								listener.onProgress(
										baseline, totalComplete, countAssigned(assignments));
								if (!baseline)
								{
									listener.onTargetResult(
											target, result.getTopScores());
								}
							}
							if (!baseline)
							{
								listener.onWorker(
										workerIndex,
										WorkerState.COMPLETE,
										null,
										completed,
										assignedTargets.size(),
										elapsedMillis(workerStart),
										"All assigned target jobs completed.");
							}
							return new WorkerResult(
									results,
									new WorkerMetrics(
											workerIndex,
											assignedTargets.size(),
											completed,
											elapsedMillis(workerStart)));
						}
						catch (InterruptedException exception)
						{
							if (!baseline)
							{
								listener.onWorker(
										workerIndex,
										WorkerState.CANCELLED,
										null,
										completed,
										assignedTargets.size(),
										elapsedMillis(workerStart),
										"Cancelled.");
							}
							throw exception;
						}
						catch (Exception exception)
						{
							if (!baseline)
							{
								listener.onWorker(
										workerIndex,
										WorkerState.FAILED,
										null,
										completed,
										assignedTargets.size(),
										elapsedMillis(workerStart),
										exception.getMessage());
							}
							throw exception;
						}
					}
				}));
			}
			return collectWorkerResults(futures, targets);
		}
		finally
		{
			executor.shutdownNow();
		}
	}

	private static ExecutionResult executeDoc2Vec(
			final Config config,
			List<String> targets,
			int workerCount,
			final boolean baseline,
			final Listener listener,
			final CancellationToken token) throws Exception
	{
		final List<List<String>> assignments = baseline
				? assignRoundRobin(targets, workerCount)
				: NodeCapacityScheduler.assignTargetJobs(targets, workerCount);
		final AtomicInteger completedTotal = new AtomicInteger();
		ExecutorService executor = Executors.newFixedThreadPool(workerCount);
		ArrayList<Future<WorkerResult>> futures =
				new ArrayList<Future<WorkerResult>>();
		try
		{
			for (int workerOffset = 0; workerOffset < workerCount; workerOffset++)
			{
				final int workerIndex = workerOffset + 1;
				final List<String> assignedTargets = assignments.get(workerOffset);
				final NodeCapacityProfile capacityProfile =
						NodeCapacityRegistry.profileForNode(workerIndex);
				futures.add(executor.submit(new Callable<WorkerResult>()
				{
					public WorkerResult call() throws Exception
					{
						long workerStart = System.nanoTime();
						if (!baseline)
						{
							listener.onWorker(
									workerIndex,
									WorkerState.READY,
									null,
									0,
									assignedTargets.size(),
									0L,
									NodeCapacityScheduler.assignmentDetail(
											workerIndex,
											assignedTargets.size())
									+ " | loading shared Doc2Vec scoring index.");
						}
						token.throwIfCancelled();
						Doc2VecBatchRunner.Result batch = Doc2VecBatchRunner.run(
								config.pythonCommand,
								doc2VecScript(),
								config.topResults,
								config.datasetFile,
								1,
								1,
									doc2VecTrainingWorkers(),
									assignedTargets,
									config.algorithmSettings,
									new Doc2VecBatchRunner.ProgressListener()
								{
									public void onProgress(String event, String detail)
									{
										if (!baseline)
										{
											listener.onWorker(
													workerIndex,
													WorkerState.RUNNING,
													null,
													0,
													assignedTargets.size(),
													elapsedMillis(workerStart),
													detail);
										}
									}
								});
						if (batch.getExitCode() != 0)
						{
							throw new IOException(
									"Doc2Vec worker " + workerIndex
											+ " exited with code " + batch.getExitCode()
											+ "." + batch.getDiagnosticSummary());
						}
						if (batch.hasUnsafeNumericalDiagnostic())
						{
							throw new IOException(
									"Doc2Vec reported an unsafe numerical runtime diagnostic.");
						}

						LinkedHashMap<String,TargetResult> results =
								new LinkedHashMap<String,TargetResult>();
						int completed = 0;
						for (String target : assignedTargets)
						{
							token.throwIfCancelled();
							if (!baseline)
							{
								NodeCapacityScheduler.applyPerJobSimulation(
										capacityProfile,
										target,
										workerIndex,
										new NodeCapacityScheduler.CancellationCheck()
										{
											public void throwIfCancelled()
													throws InterruptedException
											{
												token.throwIfCancelled();
											}
										});
							}
							if (!batch.isCompleted(target) || batch.hasError(target))
							{
								throw new IOException(
										"Doc2Vec did not complete target " + target
												+ (batch.hasError(target)
														? ": " + batch.getErrors().get(target)
														: "."));
							}
							TreeMap<String,Double> scores = batch.getScores(target);
							TargetResult result = buildTargetResult(
									target,
									scores == null
											? Collections.<String,Double>emptyMap()
											: scores,
									config.topResults);
							results.put(target, result);
							completed++;
							int totalComplete = completedTotal.incrementAndGet();
							listener.onProgress(
									baseline, totalComplete, countAssigned(assignments));
							if (!baseline)
							{
								listener.onTargetResult(
										target, result.getTopScores());
								listener.onWorker(
										workerIndex,
										WorkerState.RUNNING,
										target,
										completed,
										assignedTargets.size(),
										elapsedMillis(workerStart),
										capacityProfile.shortLabel()
												+ " | scored " + completed + " of "
												+ assignedTargets.size() + " assigned targets.");
							}
						}
						if (!baseline)
						{
							listener.onWorker(
									workerIndex,
									WorkerState.COMPLETE,
									null,
									completed,
									assignedTargets.size(),
									elapsedMillis(workerStart),
									"All assigned target jobs completed.");
						}
						return new WorkerResult(
								results,
								new WorkerMetrics(
										workerIndex,
										assignedTargets.size(),
										completed,
										elapsedMillis(workerStart)));
					}
				}));
			}
			return collectWorkerResults(futures, targets);
		}
		finally
		{
			executor.shutdownNow();
		}
	}

	private static ExecutionResult collectWorkerResults(
			List<Future<WorkerResult>> futures,
			List<String> targetOrder) throws Exception
	{
		Map<String,TargetResult> unordered =
				new LinkedHashMap<String,TargetResult>();
		ArrayList<WorkerMetrics> metrics = new ArrayList<WorkerMetrics>();
		for (Future<WorkerResult> future : futures)
		{
			try
			{
				WorkerResult workerResult = future.get();
				for (Map.Entry<String,TargetResult> entry
						: workerResult.results.entrySet())
				{
					if (unordered.put(entry.getKey(), entry.getValue()) != null)
					{
						throw new IllegalStateException(
								"Target was completed more than once: " + entry.getKey());
					}
				}
				metrics.add(workerResult.metrics);
			}
			catch (ExecutionException exception)
			{
				Throwable cause = exception.getCause();
				if (cause instanceof Exception)
				{
					throw (Exception)cause;
				}
				throw new RuntimeException(cause);
			}
		}
		LinkedHashMap<String,TargetResult> ordered =
				new LinkedHashMap<String,TargetResult>();
		for (String target : targetOrder)
		{
			TargetResult result = unordered.get(target);
			if (result == null)
			{
				throw new IllegalStateException(
						"Target did not produce a result: " + target);
			}
			ordered.put(target, result);
		}
		Collections.sort(metrics, new Comparator<WorkerMetrics>()
		{
			public int compare(WorkerMetrics first, WorkerMetrics second)
			{
				return Integer.compare(
						first.workerIndex, second.workerIndex);
			}
		});
		return new ExecutionResult(ordered, metrics);
	}

	private static TargetResult scoreSimilarity(
			String target,
			Map<String,? extends Map<String,Double>> documents,
			int topResults)
	{
		Map<String,Double> targetDocument = documents.get(target);
		if (targetDocument == null)
		{
			throw new IllegalArgumentException(
					"Target has no prepared TF-IDF document: " + target);
		}
		TreeMap<String,Double> scores = new TreeMap<String,Double>();
		for (Map.Entry<String,? extends Map<String,Double>> entry
				: documents.entrySet())
		{
			if (!target.equals(entry.getKey()))
			{
				scores.put(
						entry.getKey(),
						sparseCosine(targetDocument, entry.getValue()));
			}
		}
		return buildTargetResult(target, scores, topResults);
	}

	private static TargetResult scoreSvm(
			String target,
			PreparedSvm prepared,
			int topResults,
			int workerIndex,
			AlgorithmParameterSettings settings) throws Exception
	{
		if (prepared == null)
		{
			throw new IllegalStateException("SVM inputs were not prepared.");
		}
		Map<String,Double> targetDocument = prepared.tfidf.get(target);
		if (targetDocument == null)
		{
			throw new IllegalArgumentException(
					"Target has no prepared TF-IDF document: " + target);
		}

		File recommendFile = File.createTempFile(
				"exact-sweep-recommend-" + workerIndex + "-", ".arff",
				prepared.directory);
		try
		{
			try (BufferedWriter writer = new BufferedWriter(
					new java.io.OutputStreamWriter(
							new FileOutputStream(recommendFile),
							StandardCharsets.UTF_8)))
			{
				SparseArffWriter.writeHeader(
						writer,
						"recommendSet",
						prepared.terms,
						prepared.classes);
				SparseArffWriter.writeRow(
						writer,
						targetDocument,
						prepared.terms,
						prepared.userFollowees.get(target));
			}

			ExactSvmWorkerResult workerResult = ExactSvmWorkerRunner.run(
					"Exact-Sweep-Node-" + workerIndex,
					"SVM target " + target,
					null,
					prepared.trainFile,
					prepared.emptyTestFile,
						recommendFile,
						prepared.terms.size(),
						prepared.trainUsers.size(),
						prepared.classes.size(),
						settings);
				String predictedClass =
						workerResult.getPredictedClasses().get(0);
				double[] distribution =
						workerResult.getDistributions().get(0);
				TreeMap<String,Double> scores = new TreeMap<String,Double>();
				for (String className : prepared.classes)
				{
					scores.put(className, 0.0);
				}
				if (distribution != null)
				{
					for (int index = 0; index < distribution.length; index++)
					{
						double score = distribution[index];
						if (!Double.isFinite(score))
						{
							score = 0.0;
						}
						scores.put(
								workerResult.getClassNames().get(index),
								score);
					}
				}
				boolean positive = false;
				for (Double score : scores.values())
				{
					if (score != null && score > 0.0)
					{
						positive = true;
						break;
					}
				}
				if (!positive && scores.containsKey(predictedClass))
				{
					scores.put(predictedClass, 1.0);
				}
				return buildTargetResult(target, scores, topResults);
		}
		finally
		{
			Files.deleteIfExists(recommendFile.toPath());
		}
	}

	private static PreparedSvm prepareSvm(DatasetSnapshot snapshot)
			throws Exception
	{
		Map<String,List<String>> usersByClass =
				new LinkedHashMap<String,List<String>>();
		for (String user : snapshot.tfidf.keySet())
		{
			String className = snapshot.userFollowees.get(user);
			if (className == null)
			{
				continue;
			}
			List<String> classUsers = usersByClass.get(className);
			if (classUsers == null)
			{
				classUsers = new ArrayList<String>();
				usersByClass.put(className, classUsers);
			}
			classUsers.add(user);
		}
		if (usersByClass.size() < 2)
		{
			throw new IllegalArgumentException(
					"SVM needs at least two followee classes. This dataset has "
							+ usersByClass.size() + ".");
		}

		SvmReproducibility.Split split =
				SvmReproducibility.stratifiedSplit(
						usersByClass, TEST_SET_PERCENT);
		ArrayList<String> trainUsers = split.getTrainUsers();
		if (trainUsers.size() < 2)
		{
			throw new IllegalArgumentException(
					"SVM needs at least two training users after the deterministic split.");
		}
		TreeSet<String> terms = new TreeSet<String>();
		for (Map<String,Double> document : snapshot.tfidf.values())
		{
			terms.addAll(document.keySet());
		}
		ArrayList<String> classes =
				SvmReproducibility.sortedNonNullValues(usersByClass.keySet());
		// Every sweep worker trains the same full exact model; worker count
		// parallelizes targets, not the model's class space.
		ExactSvmWorkerRunner.validateBestCaseStructure(
				trainUsers.size(), classes.size(), 1);
		File directory = Files.createTempDirectory(
				"exact-population-svm-").toFile();
		File trainFile = new File(directory, "training.arff");
		File emptyTestFile = new File(directory, "empty-test.arff");
		try (BufferedWriter writer = new BufferedWriter(
				new java.io.OutputStreamWriter(
						new FileOutputStream(trainFile),
						StandardCharsets.UTF_8)))
		{
			SparseArffWriter.writeHeader(
					writer, "trainingSet", terms, classes);
			for (String user : trainUsers)
			{
				Map<String,Double> document = snapshot.tfidf.get(user);
				if (document != null)
				{
					SparseArffWriter.writeRow(
							writer,
							document,
							terms,
							snapshot.userFollowees.get(user));
				}
			}
		}
		try (BufferedWriter writer = new BufferedWriter(
				new java.io.OutputStreamWriter(
						new FileOutputStream(emptyTestFile),
						StandardCharsets.UTF_8)))
		{
			SparseArffWriter.writeHeader(
					writer, "emptyTestSet", terms, classes);
		}
		return new PreparedSvm(
				directory,
				trainFile,
				emptyTestFile,
				terms,
				classes,
				trainUsers,
				snapshot.tfidf,
				snapshot.userFollowees);
	}

	private static void warmDoc2Vec(
			Config config,
			String firstTarget,
			final Listener listener,
			CancellationToken token) throws Exception
	{
		token.throwIfCancelled();
		Doc2VecBatchRunner.Result warmup = Doc2VecBatchRunner.run(
				config.pythonCommand,
				doc2VecScript(),
				1,
				config.datasetFile,
				1,
				1,
					doc2VecTrainingWorkers(),
					Collections.singletonList(firstTarget),
					config.algorithmSettings,
					new Doc2VecBatchRunner.ProgressListener()
				{
					public void onProgress(String event, String detail)
					{
						listener.onPhase("PREPARING DOC2VEC", detail);
					}
				});
		if (warmup.getExitCode() != 0
				|| warmup.hasError(firstTarget)
				|| !warmup.isCompleted(firstTarget)
				|| warmup.hasUnsafeNumericalDiagnostic())
		{
			throw new IOException(
					"Doc2Vec shared-model preparation failed."
							+ warmup.getDiagnosticSummary());
		}
	}

	private static int doc2VecTrainingWorkers()
	{
		return Math.min(
				Math.max(Runtime.getRuntime().availableProcessors() - 1, 1),
				12);
	}

	private static File doc2VecScript()
	{
		File script = new File(
				"TwitterGatherDataFollowers/userRyersonU/doc2vec_batch.py");
		if (!script.isFile())
		{
			throw new IllegalStateException(
					"Doc2Vec batch script is unavailable: "
							+ script.getAbsolutePath());
		}
		return script;
	}

	private static Instances loadInstances(File file) throws IOException
	{
		try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(
						new FileInputStream(file),
						StandardCharsets.UTF_8)))
		{
			return new Instances(reader);
		}
	}

	private static LinkedHashMap<String,LinkedHashMap<String,Double>>
			calculateTfidf(
					LinkedHashMap<String,LinkedHashMap<String,Double>> documents)
	{
		LinkedHashMap<String,Integer> documentFrequency =
				new LinkedHashMap<String,Integer>();
		for (Map<String,Double> document : documents.values())
		{
			for (String term : document.keySet())
			{
				Integer frequency = documentFrequency.get(term);
				documentFrequency.put(
						term, frequency == null ? 1 : frequency + 1);
			}
		}

		int documentCount = documents.size();
		LinkedHashMap<String,LinkedHashMap<String,Double>> tfidf =
				new LinkedHashMap<String,LinkedHashMap<String,Double>>();
		for (Map.Entry<String,LinkedHashMap<String,Double>> entry
				: documents.entrySet())
		{
			LinkedHashMap<String,Double> weighted =
					new LinkedHashMap<String,Double>();
			double magnitude = 0.0;
			for (Map.Entry<String,Double> term : entry.getValue().entrySet())
			{
				double df = documentFrequency.get(term.getKey());
				double idf = Math.log(documentCount / df) / Math.log(2);
				double value = term.getValue() * idf;
				if (!Double.isFinite(value))
				{
					value = 0.0;
				}
				weighted.put(term.getKey(), value);
				magnitude += value * value;
			}
			magnitude = Math.sqrt(magnitude);
			for (Map.Entry<String,Double> term : weighted.entrySet())
			{
				double value = magnitude > 0.0
						? term.getValue() / magnitude : 0.0;
				term.setValue(Double.isFinite(value) ? value : 0.0);
			}
			tfidf.put(entry.getKey(), weighted);
		}
		return tfidf;
	}

	static List<String> tokenizeForTest(
			String text,
			boolean removeHashtags,
			boolean removeRetweets,
			boolean removeStopWords)
	{
		return tokenize(text, removeHashtags, removeRetweets, removeStopWords);
	}

	private static List<String> tokenize(
			String text,
			boolean removeHashtags,
			boolean removeRetweets,
			boolean removeStopWords)
	{
		String currentText = text == null ? "" : text;
		if (currentText.contains("Photo:"))
		{
			currentText = currentText.substring(0, currentText.indexOf("Photo:"));
		}
		if (currentText.contains("Photoset:"))
		{
			currentText = currentText.substring(
					0, currentText.indexOf("Photoset:"));
		}
		if (currentText.contains("RT @") && removeRetweets)
		{
			currentText = "";
		}
		else
		{
			currentText = RETWEET_PATTERN.matcher(currentText).replaceAll("RT ");
		}
		currentText = PUNCTUATION_PATTERN.matcher(currentText).replaceAll("");
		currentText = LINK_PATTERN.matcher(currentText).replaceAll(" ");
		if (removeHashtags)
		{
			currentText = SPECIAL_CHARACTER_PATTERN.matcher(
					currentText).replaceAll(" ");
		}
		currentText = currentText.toLowerCase(Locale.US);
		if (removeStopWords)
		{
			String padded = " " + currentText + " ";
			for (String stopWord : RecommenderAgent.stopWordsArray)
			{
				String pattern = " " + stopWord + " ";
				if (padded.contains(pattern))
				{
					padded = padded.replace(pattern, " ");
				}
			}
			currentText = padded;
		}
		currentText = currentText.trim();
		currentText = NON_ALPHA_PATTERN.matcher(currentText).replaceAll("");
		currentText = MULTI_SPACE_PATTERN.matcher(currentText).replaceAll(" ");
		currentText = currentText.trim();
		if (currentText.length() == 0)
		{
			return Collections.emptyList();
		}
		return Arrays.asList(currentText.split(" "));
	}

	private static boolean withinExclusiveDateRange(
			String tweetDate,
			String beginDate,
			String endDate)
	{
		SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
		try
		{
			Date tweet = format.parse(tweetDate);
			Date begin = format.parse(beginDate);
			Date end = format.parse(endDate);
			return begin.before(tweet) && end.after(tweet);
		}
		catch (ParseException exception)
		{
			return true;
		}
	}

	private static void parseDate(String value)
	{
		SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
		format.setLenient(false);
		try
		{
			format.parse(value);
		}
		catch (ParseException exception)
		{
			throw new IllegalArgumentException(
					"Dates must use YYYY-MM-DD: " + value);
		}
	}

	private static double sparseCosine(
			Map<String,Double> first,
			Map<String,Double> second)
	{
		if (first == null || second == null
				|| first.isEmpty() || second.isEmpty())
		{
			return 0.0;
		}
		Map<String,Double> smaller = first.size() <= second.size()
				? first : second;
		Map<String,Double> larger = smaller == first ? second : first;
		double score = 0.0;
		for (Map.Entry<String,Double> entry : smaller.entrySet())
		{
			Double other = larger.get(entry.getKey());
			if (other != null)
			{
				score += entry.getValue() * other;
			}
		}
		return score;
	}

	private static TargetResult buildTargetResult(
			String target,
			Map<String,Double> rawScores,
			int topResults)
	{
		ArrayList<Map.Entry<String,Double>> sorted =
				new ArrayList<Map.Entry<String,Double>>();
		TreeMap<String,Double> canonical = new TreeMap<String,Double>();
		for (Map.Entry<String,Double> entry : rawScores.entrySet())
		{
			double score = entry.getValue() == null
					? 0.0 : entry.getValue();
			if (!Double.isFinite(score))
			{
				score = 0.0;
			}
			canonical.put(entry.getKey(), score);
			if (score > 0.0)
			{
				sorted.add(new java.util.AbstractMap.SimpleImmutableEntry<String,Double>(
						entry.getKey(), score));
			}
		}
		Collections.sort(sorted, new Comparator<Map.Entry<String,Double>>()
		{
			public int compare(
					Map.Entry<String,Double> first,
					Map.Entry<String,Double> second)
			{
				int scoreComparison =
						Double.compare(second.getValue(), first.getValue());
				return scoreComparison != 0
						? scoreComparison
						: first.getKey().compareTo(second.getKey());
			}
		});
		LinkedHashMap<String,Double> top =
				new LinkedHashMap<String,Double>();
		for (int index = 0;
				index < sorted.size() && index < topResults;
				index++)
		{
			Map.Entry<String,Double> entry = sorted.get(index);
			top.put(entry.getKey(), entry.getValue());
		}
		return new TargetResult(target, top, digestScores(target, canonical));
	}

	private static String digestScores(
			String target,
			Map<String,Double> scores)
	{
		try
		{
			MessageDigest digest = sha256();
			updateDigest(digest, target);
			TreeMap<String,Double> ordered =
					new TreeMap<String,Double>(scores);
			for (Map.Entry<String,Double> entry : ordered.entrySet())
			{
				updateDigest(digest, entry.getKey());
				updateDigest(digest, Double.doubleToLongBits(entry.getValue()));
			}
			return toHex(digest.digest());
		}
		catch (RuntimeException exception)
		{
			throw exception;
		}
	}

	private static String digestResultSet(Map<String,TargetResult> results)
	{
		MessageDigest digest = sha256();
		TreeMap<String,TargetResult> ordered =
				new TreeMap<String,TargetResult>(results);
		for (Map.Entry<String,TargetResult> entry : ordered.entrySet())
		{
			updateDigest(digest, entry.getKey());
			updateDigest(digest, entry.getValue().digest);
		}
		return toHex(digest.digest());
	}

	private static void verifyEquivalent(
			List<String> targets,
			Map<String,TargetResult> baseline,
			Map<String,TargetResult> parallel)
	{
		if (baseline.size() != parallel.size()
				|| parallel.size() != targets.size())
		{
			throw new IllegalStateException(
					"Verification failed: result target counts differ.");
		}
		for (String target : targets)
		{
			TargetResult oneWorker = baseline.get(target);
			TargetResult manyWorkers = parallel.get(target);
			if (oneWorker == null || manyWorkers == null)
			{
				throw new IllegalStateException(
						"Verification failed: missing target " + target + ".");
			}
			if (!oneWorker.digest.equals(manyWorkers.digest))
			{
				throw new IllegalStateException(
						"Verification failed for target " + target
								+ ": raw result fingerprints differ.");
			}
		}
	}

	private static List<List<String>> assignRoundRobin(
			List<String> targets,
			int workerCount)
	{
		ArrayList<List<String>> assignments = new ArrayList<List<String>>();
		for (int worker = 0; worker < workerCount; worker++)
		{
			assignments.add(new ArrayList<String>());
		}
		for (int index = 0; index < targets.size(); index++)
		{
			assignments.get(index % workerCount).add(targets.get(index));
		}
		return assignments;
	}

	private static int countAssigned(List<List<String>> assignments)
	{
		int total = 0;
		for (List<String> assignment : assignments)
		{
			total += assignment.size();
		}
		return total;
	}

	private static long elapsedMillis(long startNanos)
	{
		return Math.max(0L, (System.nanoTime() - startNanos) / 1000000L);
	}

	private static MessageDigest sha256()
	{
		try
		{
			return MessageDigest.getInstance("SHA-256");
		}
		catch (NoSuchAlgorithmException exception)
		{
			throw new IllegalStateException("SHA-256 is unavailable.", exception);
		}
	}

	private static void updateDigest(MessageDigest digest, String value)
	{
		byte[] bytes = value == null
				? new byte[0] : value.getBytes(StandardCharsets.UTF_8);
		updateDigest(digest, bytes.length);
		digest.update(bytes);
	}

	private static void updateDigest(MessageDigest digest, long value)
	{
		digest.update(new byte[] {
				(byte)(value >>> 56),
				(byte)(value >>> 48),
				(byte)(value >>> 40),
				(byte)(value >>> 32),
				(byte)(value >>> 24),
				(byte)(value >>> 16),
				(byte)(value >>> 8),
				(byte)value
		});
	}

	private static String toHex(byte[] bytes)
	{
		StringBuilder output = new StringBuilder(bytes.length * 2);
		for (byte value : bytes)
		{
			output.append(String.format(Locale.US, "%02x", value & 0xff));
		}
		return output.toString();
	}

	private static void deleteRecursively(File file)
	{
		if (file == null || !file.exists())
		{
			return;
		}
		if (file.isDirectory())
		{
			File[] children = file.listFiles();
			if (children != null)
			{
				for (File child : children)
				{
					deleteRecursively(child);
				}
			}
		}
		if (!file.delete())
		{
			file.deleteOnExit();
		}
	}

	private static final class DatasetSnapshot
	{
		private final List<String> users;
		private final LinkedHashMap<String,LinkedHashMap<String,Double>> tfidf;
		private final LinkedHashMap<String,String> userFollowees;
		private final String datasetDigest;
		@SuppressWarnings("unused")
		private final long inputRows;
		@SuppressWarnings("unused")
		private final long acceptedRows;
		@SuppressWarnings("unused")
		private final long malformedRows;

		private DatasetSnapshot(
				List<String> users,
				LinkedHashMap<String,LinkedHashMap<String,Double>> tfidf,
				LinkedHashMap<String,String> userFollowees,
				String datasetDigest,
				long inputRows,
				long acceptedRows,
				long malformedRows)
		{
			this.users = users;
			this.tfidf = tfidf;
			this.userFollowees = userFollowees;
			this.datasetDigest = datasetDigest;
			this.inputRows = inputRows;
			this.acceptedRows = acceptedRows;
			this.malformedRows = malformedRows;
		}
	}

	private static final class PreparedSvm
	{
		private final File directory;
		private final File trainFile;
		private final File emptyTestFile;
		private final TreeSet<String> terms;
		private final ArrayList<String> classes;
		private final ArrayList<String> trainUsers;
		private final LinkedHashMap<String,LinkedHashMap<String,Double>> tfidf;
		private final LinkedHashMap<String,String> userFollowees;

		private PreparedSvm(
				File directory,
				File trainFile,
				File emptyTestFile,
				TreeSet<String> terms,
				ArrayList<String> classes,
				ArrayList<String> trainUsers,
				LinkedHashMap<String,LinkedHashMap<String,Double>> tfidf,
				LinkedHashMap<String,String> userFollowees)
		{
			this.directory = directory;
			this.trainFile = trainFile;
			this.emptyTestFile = emptyTestFile;
			this.terms = terms;
			this.classes = classes;
			this.trainUsers = trainUsers;
			this.tfidf = tfidf;
			this.userFollowees = userFollowees;
		}

		private void close()
		{
			deleteRecursively(directory);
		}
	}

	private static final class WorkerResult
	{
		private final LinkedHashMap<String,TargetResult> results;
		private final WorkerMetrics metrics;

		private WorkerResult(
				LinkedHashMap<String,TargetResult> results,
				WorkerMetrics metrics)
		{
			this.results = results;
			this.metrics = metrics;
		}
	}

	private static final class ExecutionResult
	{
		private final LinkedHashMap<String,TargetResult> results;
		private final List<WorkerMetrics> workers;

		private ExecutionResult(
				LinkedHashMap<String,TargetResult> results,
				List<WorkerMetrics> workers)
		{
			this.results = results;
			this.workers = workers;
		}
	}

	private static final class NoOpListener implements Listener
	{
		public void onPhase(String phase, String detail)
		{
		}

		public void onProgress(boolean baseline, int completed, int total)
		{
		}

		public void onWorker(
				int workerIndex,
				WorkerState state,
				String target,
				int completed,
				int assigned,
				long elapsedMillis,
				String detail)
		{
		}

		public void onTargetResult(
				String target,
				LinkedHashMap<String,Double> scores)
		{
		}
	}
}
