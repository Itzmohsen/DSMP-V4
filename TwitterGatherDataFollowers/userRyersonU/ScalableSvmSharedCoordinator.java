package TwitterGatherDataFollowers.userRyersonU;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Builds deterministic global scalable-SVM models from all recommender shards.
 *
 * Recommender nodes still perform their assigned text processing in parallel.
 * This coordinator merges their sparse user documents before TF-IDF and model
 * training so changing the node count cannot change the learned models.
 */
final class ScalableSvmSharedCoordinator
{
	private static final Object LOCK = new Object();
	private static final Map<String,RunState> RUNS =
			new LinkedHashMap<String,RunState>();

	private ScalableSvmSharedCoordinator()
	{
	}

	static Outcome score(
			String owner,
			String runId,
			int expectedNodes,
			int nodeNumber,
			Map<String,LinkedHashMap<String,Double>> localDocuments,
			Map<String,String> localFollowees,
			Map<String,String> datasetFollowees,
			List<String> targets,
			boolean buildSimilarUserEvidence) throws Exception
	{
		return score(
				owner,
				runId,
				expectedNodes,
				nodeNumber,
				localDocuments,
				localFollowees,
				datasetFollowees,
				targets,
				buildSimilarUserEvidence,
				AlgorithmParameterSettings.defaults());
	}

	static Outcome score(
			String owner,
			String runId,
			int expectedNodes,
			int nodeNumber,
			Map<String,LinkedHashMap<String,Double>> localDocuments,
			Map<String,String> localFollowees,
			Map<String,String> datasetFollowees,
			List<String> targets,
			boolean buildSimilarUserEvidence,
			AlgorithmParameterSettings settings) throws Exception
	{
		AlgorithmParameterSettings effectiveSettings =
				AlgorithmParameterSettings.effective(settings);
		String key = runId == null ? "__legacy_scalable_svm__" : runId;
		RunState state;
		boolean builder = false;
		synchronized (LOCK)
		{
			state = RUNS.get(key);
			if (state == null)
			{
					state = new RunState(
							Math.max(1, expectedNodes),
							canonicalTargets(targets),
							buildSimilarUserEvidence,
							effectiveSettings);
					RUNS.put(key, state);
				}
				state.validate(
						expectedNodes,
						targets,
						buildSimilarUserEvidence,
						effectiveSettings);
			if (!state.submissions.containsKey(nodeNumber))
			{
				state.submissions.put(
						nodeNumber,
						new Submission(localDocuments, localFollowees, datasetFollowees));
			}
			if (!state.building
					&& state.submissions.size() == state.expectedNodes)
			{
				state.building = true;
				builder = true;
			}
		}

		if (builder)
		{
			try
			{
				Outcome result = build(owner, runId, state);
				synchronized (LOCK)
				{
					state.outcome = result;
					LOCK.notifyAll();
				}
			}
			catch (Throwable failure)
			{
				synchronized (LOCK)
				{
					state.failure = failure;
					LOCK.notifyAll();
				}
			}
		}
		else
		{
			NodeExecutionMonitor.waitingForSharedModel(
					owner,
					"Waiting for all recommender shards before building the "
					+ "node-count-invariant scalable SVM.");
		}

		long deadline = System.currentTimeMillis()
				+ (ModelWorkerProcessSupport.configuredTimeoutSeconds() + 3600L)
						* 1000L;
		synchronized (LOCK)
		{
			while (state.outcome == null && state.failure == null)
			{
				if (RecommendationRunAbortRegistry.isAborted(runId))
				{
					removeRunIfCurrent(key, state);
					RecommendationRunAbortRegistry.requireActive(runId);
				}
				long remaining = deadline - System.currentTimeMillis();
				if (remaining <= 0L)
				{
					removeRunIfCurrent(key, state);
					throw new IllegalStateException(
							"Timed out waiting for the shared scalable-SVM model.");
				}
				LOCK.wait(Math.min(1000L, remaining));
			}
			if (state.failure != null)
			{
				state.consumers++;
				if (state.consumers >= state.expectedNodes)
				{
					removeRunIfCurrent(key, state);
				}
				if (state.failure instanceof Exception)
				{
					throw (Exception)state.failure;
				}
				throw new IllegalStateException(
						"Shared scalable-SVM model failed.", state.failure);
			}
			Outcome copy = state.outcome.copy();
			state.consumers++;
			if (state.consumers >= state.expectedNodes)
			{
				removeRunIfCurrent(key, state);
			}
			return copy;
		}
	}

	private static void removeRunIfCurrent(String key, RunState state)
	{
		if (RUNS.get(key) == state)
		{
			RUNS.remove(key);
		}
	}

	private static Outcome build(
			String owner,
			String runId,
			RunState state) throws Exception
	{
		NodeExecutionMonitor.modelRunning(
				owner,
				"Merging all sparse document shards for node-count-invariant "
				+ "Scalable Linear SVM training.");
		GlobalCorpus corpus = mergeSubmissions(state);
		if (corpus.documents.size() < 2)
		{
			throw new IllegalArgumentException(
					"Scalable SVM needs at least two usable global documents.");
		}
		for (String target : state.targets)
		{
			if (!corpus.documents.containsKey(target))
			{
				throw new IllegalArgumentException(
						"Scalable SVM target has no globally prepared document: "
						+ target);
			}
		}

		LinkedHashMap<String,LinkedHashMap<String,Double>> tfidf =
				calculateGlobalTfidf(corpus.documents, state.expectedNodes);
		TreeSet<String> terms = collectTerms(tfidf);
		TreeSet<String> classes = collectClasses(corpus.documents.keySet(), corpus.followees);

		File directory = Files.createTempDirectory(
				"shared-scalable-svm-").toFile();
		try
		{
			TreeMap<String,TreeMap<String,Double>> scores =
					new TreeMap<String,TreeMap<String,Double>>();
			double accuracyTotal = 0.0;
			int accuracyCount = 0;
			if (state.buildSimilarUserEvidence)
			{
				ExecutorService targetExecutor = Executors.newFixedThreadPool(
						effectiveParallelism(
								state.expectedNodes, state.targets.size()));
				List<Future<TargetModelResult>> targetFutures =
						new ArrayList<Future<TargetModelResult>>();
				for (int targetIndex = 0;
						targetIndex < state.targets.size(); targetIndex++)
				{
					final int modelIndex = targetIndex;
					targetFutures.add(targetExecutor.submit(
							new Callable<TargetModelResult>()
							{
								public TargetModelResult call() throws Exception
								{
									String target = state.targets.get(modelIndex);
									Map<String,List<String>> usersByClass =
											groupUsersByClass(
													corpus.documents.keySet(),
													corpus.followees);
									removeUser(usersByClass, target);
									SvmReproducibility.Split split =
											SvmReproducibility.stratifiedSplit(
													usersByClass, 0.30);
									ExactSvmWorkerResult worker = runWorker(
											owner,
											runId,
											directory,
											"target-" + modelIndex,
											Collections.singletonList(target),
											split,
											tfidf,
												terms,
												classes,
												corpus.followees,
												"shared global scalable SVM target "
														+ target,
												state.settings);
									return new TargetModelResult(target, worker);
								}
							}));
				}
				try
				{
					Exception firstFailure = null;
					for (Future<TargetModelResult> future : targetFutures)
					{
						try
						{
							TargetModelResult targetResult = get(future);
							scores.putAll(buildScores(
									Collections.singletonList(targetResult.target),
									targetResult.worker));
							if (Double.isFinite(
									targetResult.worker.getTestAccuracyPercent()))
							{
								accuracyTotal +=
										targetResult.worker.getTestAccuracyPercent();
								accuracyCount++;
							}
						}
						catch (Exception failure)
						{
							if (firstFailure == null)
							{
								firstFailure = failure;
							}
						}
					}
					if (firstFailure != null)
					{
						throw firstFailure;
					}
				}
				finally
				{
					targetExecutor.shutdown();
				}
			}
			else
			{
				//A target must never be in the training data of the model that
				//predicts it: its followee is exactly what is scored. The targets
				//are held out together, or fold by fold when they are many.
				int labelled = groupUsersByClass(corpus.documents.keySet(), corpus.followees)
						.values().stream().mapToInt(List::size).sum();
				List<List<String>> folds = TargetFolds.of(state.targets, corpus.followees, labelled);
				for (int foldIndex = 0; foldIndex < folds.size(); foldIndex++)
				{
					List<String> fold = folds.get(foldIndex);
					Map<String,List<String>> usersByClass =
							groupUsersByClass(corpus.documents.keySet(), corpus.followees);
					for (String target : fold)
					{
						removeUser(usersByClass, target);
					}
					SvmReproducibility.Split split =
							SvmReproducibility.stratifiedSplit(usersByClass, 0.30);
					ExactSvmWorkerResult worker = runWorker(
							owner,
							runId,
							directory,
							"fold-" + foldIndex,
							fold,
							split,
							tfidf,
							terms,
							classes,
							corpus.followees,
							"shared scalable SVM, held-out fold " + (foldIndex + 1) + " of " + folds.size(),
							state.settings);
					scores.putAll(buildScores(fold, worker));
					if (Double.isFinite(worker.getTestAccuracyPercent()))
					{
						accuracyTotal += worker.getTestAccuracyPercent();
						accuracyCount++;
					}
				}
			}
			Map<String,TreeMap<String,RecommendationCandidateEvidence>> evidence =
					state.buildSimilarUserEvidence
							? buildEvidence(
									state.targets,
									tfidf,
									corpus.followees,
									state.expectedNodes)
							: Collections.<String,TreeMap<String,RecommendationCandidateEvidence>>emptyMap();
			return new Outcome(
					scores,
					evidence,
					accuracyCount == 0 ? Double.NaN : accuracyTotal / accuracyCount,
					corpus.documents.size(),
					terms.size(),
					classes.size());
		}
		finally
		{
			delete(directory);
		}
	}

	private static ExactSvmWorkerResult runWorker(
			String owner,
			String runId,
			File directory,
			String fileSuffix,
			List<String> targets,
			SvmReproducibility.Split split,
			Map<String,LinkedHashMap<String,Double>> tfidf,
			Set<String> terms,
			Set<String> classes,
			Map<String,String> followees,
			String stage,
			AlgorithmParameterSettings settings) throws Exception
	{
		File training = new File(directory, "train-" + fileSuffix + ".arff");
		File test = new File(directory, "test-" + fileSuffix + ".arff");
		File recommend = new File(directory, "recommend-" + fileSuffix + ".arff");
		List<String> trainUsers = split.getTrainUsers();
		writeArff(training, "trainingSet", trainUsers,
				tfidf, terms, classes, followees);
		writeArff(test, "testSet", split.getTestUsers(),
				tfidf, terms, classes, followees);
		writeArff(recommend, "recommendSet", targets,
				tfidf, terms, classes, followees);
		return ScalableSvmWorkerRunner.run(
				owner,
				stage,
				runId,
				training,
				test,
				recommend,
					terms.size(),
					trainUsers.size(),
					classes.size(),
					settings);
	}

	private static void removeUser(
			Map<String,List<String>> usersByClass,
			String user)
	{
		for (List<String> users : usersByClass.values())
		{
			users.remove(user);
		}
	}

	private static GlobalCorpus mergeSubmissions(RunState state)
	{
		TreeMap<String,LinkedHashMap<String,Double>> documents =
				new TreeMap<String,LinkedHashMap<String,Double>>();
		TreeMap<String,String> followees = new TreeMap<String,String>();
		for (Submission submission : state.submissions.values())
		{
			mergeFollowees(followees, submission.datasetFollowees);
			mergeFollowees(followees, submission.localFollowees);
			for (Map.Entry<String,LinkedHashMap<String,Double>> entry
					: submission.documents.entrySet())
			{
				LinkedHashMap<String,Double> old = documents.get(entry.getKey());
				if (old == null)
				{
					documents.put(entry.getKey(), entry.getValue());
				}
				else if (!old.equals(entry.getValue()))
				{
					throw new IllegalStateException(
							"Conflicting replicated scalable-SVM document for "
							+ entry.getKey() + ".");
				}
			}
		}
		return new GlobalCorpus(documents, followees);
	}

	private static void mergeFollowees(
			Map<String,String> destination,
			Map<String,String> source)
	{
		if (source == null)
		{
			return;
		}
		for (Map.Entry<String,String> entry : source.entrySet())
		{
			if (entry.getKey() == null || entry.getValue() == null)
			{
				continue;
			}
			String old = destination.get(entry.getKey());
			if (old != null && !old.equals(entry.getValue()))
			{
				throw new IllegalStateException(
						"Conflicting followee labels for " + entry.getKey() + ".");
			}
			destination.put(entry.getKey(), entry.getValue());
		}
	}

	private static LinkedHashMap<String,LinkedHashMap<String,Double>>
			calculateGlobalTfidf(
					Map<String,LinkedHashMap<String,Double>> documents,
					int requestedParallelism) throws Exception
	{
		List<Map.Entry<String,LinkedHashMap<String,Double>>> entries =
				new ArrayList<Map.Entry<String,LinkedHashMap<String,Double>>>(
						documents.entrySet());
		int parallelism = effectiveParallelism(
				requestedParallelism, entries.size());
		ExecutorService executor = Executors.newFixedThreadPool(parallelism);
		int chunkSize = Math.max(
				1,
				(entries.size() + parallelism - 1) / parallelism);
		try
		{
			List<Future<TreeMap<String,Integer>>> frequencyFutures =
					new ArrayList<Future<TreeMap<String,Integer>>>();
			for (int from = 0; from < entries.size(); from += chunkSize)
			{
				final int rangeStart = from;
				final int rangeEnd = Math.min(entries.size(), from + chunkSize);
				frequencyFutures.add(executor.submit(
						new Callable<TreeMap<String,Integer>>()
				{
					public TreeMap<String,Integer> call()
					{
						TreeMap<String,Integer> local =
								new TreeMap<String,Integer>();
						for (int index = rangeStart; index < rangeEnd; index++)
						{
							for (String term
									: entries.get(index).getValue().keySet())
							{
								Integer count = local.get(term);
								local.put(term, count == null ? 1 : count + 1);
							}
						}
						return local;
					}
				}));
			}
			TreeMap<String,Integer> documentFrequency =
					new TreeMap<String,Integer>();
			for (Future<TreeMap<String,Integer>> future : frequencyFutures)
			{
				for (Map.Entry<String,Integer> entry : get(future).entrySet())
				{
					Integer count = documentFrequency.get(entry.getKey());
					documentFrequency.put(
							entry.getKey(),
							(count == null ? 0 : count) + entry.getValue());
				}
			}

			@SuppressWarnings("unchecked")
			LinkedHashMap<String,Double>[] vectors =
					new LinkedHashMap[entries.size()];
			final double totalDocuments = documents.size();
			List<Future<Void>> vectorFutures = new ArrayList<Future<Void>>();
			for (int from = 0; from < entries.size(); from += chunkSize)
			{
				final int rangeStart = from;
				final int rangeEnd = Math.min(entries.size(), from + chunkSize);
				vectorFutures.add(executor.submit(new Callable<Void>()
				{
					public Void call()
					{
						for (int index = rangeStart; index < rangeEnd; index++)
						{
							vectors[index] = calculateDocumentTfidf(
									entries.get(index).getValue(),
									documentFrequency,
									totalDocuments);
						}
						return null;
					}
				}));
			}
			for (Future<Void> future : vectorFutures)
			{
				get(future);
			}
			LinkedHashMap<String,LinkedHashMap<String,Double>> tfidf =
					new LinkedHashMap<String,LinkedHashMap<String,Double>>();
			for (int index = 0; index < entries.size(); index++)
			{
				tfidf.put(entries.get(index).getKey(), vectors[index]);
			}
			return tfidf;
		}
		finally
		{
			executor.shutdownNow();
		}
	}

	private static LinkedHashMap<String,Double> calculateDocumentTfidf(
			Map<String,Double> document,
			Map<String,Integer> documentFrequency,
			double totalDocuments)
	{
		LinkedHashMap<String,Double> vector = new LinkedHashMap<String,Double>();
		double magnitudeSquared = 0.0;
		for (Map.Entry<String,Double> term : document.entrySet())
		{
			Integer frequency = documentFrequency.get(term.getKey());
			double value = frequency == null || frequency == 0
					? 0.0
					: term.getValue()
							* (Math.log10(totalDocuments / frequency.doubleValue())
									/ Math.log10(2.0));
			if (!Double.isFinite(value))
			{
				value = 0.0;
			}
			vector.put(term.getKey(), value);
			magnitudeSquared += value * value;
		}
		double magnitude = Math.sqrt(magnitudeSquared);
		for (Map.Entry<String,Double> term : vector.entrySet())
		{
			double value = magnitude > 0.0
					? term.getValue() / magnitude : 0.0;
			term.setValue(Double.isFinite(value) ? value : 0.0);
		}
		return vector;
	}

	private static TreeSet<String> collectTerms(
			Map<String,LinkedHashMap<String,Double>> documents)
	{
		TreeSet<String> terms = new TreeSet<String>();
		for (Map<String,Double> document : documents.values())
		{
			terms.addAll(document.keySet());
		}
		return terms;
	}

	private static TreeSet<String> collectClasses(
			Collection<String> users,
			Map<String,String> followees)
	{
		TreeSet<String> classes = new TreeSet<String>();
		for (String user : users)
		{
			String followee = followees.get(user);
			if (followee != null)
			{
				classes.add(followee);
			}
		}
		return classes;
	}

	private static Map<String,List<String>> groupUsersByClass(
			Collection<String> users,
			Map<String,String> followees)
	{
		TreeMap<String,List<String>> grouped = new TreeMap<String,List<String>>();
		for (String user : users)
		{
			String followee = followees.get(user);
			if (followee == null)
			{
				continue;
			}
			List<String> followers = grouped.get(followee);
			if (followers == null)
			{
				followers = new ArrayList<String>();
				grouped.put(followee, followers);
			}
			followers.add(user);
		}
		return grouped;
	}

	private static void writeArff(
			File file,
			String relation,
			List<String> users,
			Map<String,LinkedHashMap<String,Double>> tfidf,
			Set<String> terms,
			Set<String> classes,
			Map<String,String> followees) throws Exception
	{
		try (BufferedWriter writer = new BufferedWriter(new FileWriter(file)))
		{
			SparseArffWriter.writeHeader(writer, relation, terms, classes);
			for (String user : users)
			{
				Map<String,Double> vector = tfidf.get(user);
				if (vector != null)
				{
					SparseArffWriter.writeRow(
							writer, vector, terms, followees.get(user));
				}
			}
		}
	}

	private static TreeMap<String,TreeMap<String,Double>> buildScores(
			List<String> targets,
			ExactSvmWorkerResult result)
	{
		TreeMap<String,TreeMap<String,Double>> scores =
				new TreeMap<String,TreeMap<String,Double>>();
		List<String> classes = result.getClassNames();
		List<String> predictions = result.getPredictedClasses();
		List<double[]> distributions = result.getDistributions();
		if (predictions.size() < targets.size()
				|| distributions.size() < targets.size())
		{
			throw new IllegalStateException(
					"Shared scalable-SVM worker returned incomplete target scores.");
		}
		for (int targetIndex = 0; targetIndex < targets.size(); targetIndex++)
		{
			TreeMap<String,Double> targetScores = new TreeMap<String,Double>();
			double[] distribution = distributions.get(targetIndex);
			boolean positive = false;
			for (int classIndex = 0; classIndex < classes.size(); classIndex++)
			{
				double value = distribution != null
						&& classIndex < distribution.length
						&& Double.isFinite(distribution[classIndex])
						? distribution[classIndex] : 0.0;
				targetScores.put(classes.get(classIndex), value);
				positive = positive || value > 0.0;
			}
			if (!positive && targetScores.containsKey(predictions.get(targetIndex)))
			{
				targetScores.put(predictions.get(targetIndex), 1.0);
			}
			scores.put(targets.get(targetIndex), targetScores);
		}
		return scores;
	}

	private static Map<String,TreeMap<String,RecommendationCandidateEvidence>>
			buildEvidence(
					List<String> targets,
					Map<String,LinkedHashMap<String,Double>> tfidf,
					Map<String,String> followees,
					int requestedParallelism) throws Exception
	{
		ExecutorService executor = Executors.newFixedThreadPool(
				effectiveParallelism(requestedParallelism, targets.size()));
		List<Future<TreeMap<String,RecommendationCandidateEvidence>>> futures =
				new ArrayList<Future<TreeMap<String,RecommendationCandidateEvidence>>>();
		for (final String target : targets)
		{
			futures.add(executor.submit(
					new Callable<TreeMap<String,RecommendationCandidateEvidence>>()
			{
				public TreeMap<String,RecommendationCandidateEvidence> call()
				{
					TreeMap<String,RecommendationCandidateEvidence> targetEvidence =
							new TreeMap<String,RecommendationCandidateEvidence>();
					Map<String,Double> targetVector = tfidf.get(target);
					if (targetVector != null)
					{
						for (Map.Entry<String,LinkedHashMap<String,Double>> candidate
								: tfidf.entrySet())
						{
							if (target.equals(candidate.getKey()))
							{
								continue;
							}
							String followee = followees.get(candidate.getKey());
							double similarity =
									cosine(targetVector, candidate.getValue());
							if (followee != null && similarity > 0.0
									&& Double.isFinite(similarity))
							{
								targetEvidence.put(
										candidate.getKey(),
										new RecommendationCandidateEvidence(
												followee, similarity));
							}
						}
					}
					return targetEvidence;
				}
			}));
		}
		TreeMap<String,TreeMap<String,RecommendationCandidateEvidence>> evidence =
				new TreeMap<String,TreeMap<String,RecommendationCandidateEvidence>>();
		try
		{
			for (int index = 0; index < targets.size(); index++)
			{
				evidence.put(targets.get(index), get(futures.get(index)));
			}
		}
		finally
		{
			executor.shutdownNow();
		}
		return evidence;
	}

	private static int effectiveParallelism(int requested, int workUnits)
	{
		return Math.max(
				1,
				Math.min(
						Math.min(
								Math.max(1, requested),
								Runtime.getRuntime().availableProcessors()),
						Math.max(1, workUnits)));
	}

	private static <T> T get(Future<T> future) throws Exception
	{
		try
		{
			return future.get();
		}
		catch (InterruptedException interrupted)
		{
			Thread.currentThread().interrupt();
			throw interrupted;
		}
		catch (ExecutionException failed)
		{
			Throwable cause = failed.getCause();
			if (cause instanceof Exception)
			{
				throw (Exception)cause;
			}
			throw new IllegalStateException(
					"Parallel scalable-SVM stage failed.", cause);
		}
	}

	private static double cosine(
			Map<String,Double> first,
			Map<String,Double> second)
	{
		Map<String,Double> smaller = first.size() <= second.size() ? first : second;
		Map<String,Double> larger = smaller == first ? second : first;
		double score = 0.0;
		for (Map.Entry<String,Double> entry : smaller.entrySet())
		{
			Double other = larger.get(entry.getKey());
			if (other != null)
			{
				score += entry.getValue() * other.doubleValue();
			}
		}
		return score;
	}

	private static List<String> canonicalTargets(List<String> targets)
	{
		LinkedHashSet<String> unique = new LinkedHashSet<String>();
		if (targets != null)
		{
			for (String target : targets)
			{
				if (target != null)
				{
					unique.add(target);
				}
			}
		}
		return new ArrayList<String>(unique);
	}

	private static void delete(File file) throws Exception
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
					delete(child);
				}
			}
		}
		Files.deleteIfExists(file.toPath());
	}

	static final class Outcome
	{
		private final TreeMap<String,TreeMap<String,Double>> scores;
		private final Map<String,TreeMap<String,RecommendationCandidateEvidence>> evidence;
		private final double accuracyPercent;
		private final int documentCount;
		private final int featureCount;
		private final int classCount;

		private Outcome(
				Map<String,TreeMap<String,Double>> scores,
				Map<String,TreeMap<String,RecommendationCandidateEvidence>> evidence,
				double accuracyPercent,
				int documentCount,
				int featureCount,
				int classCount)
		{
			this.scores = copyScores(scores);
			this.evidence = copyEvidence(evidence);
			this.accuracyPercent = accuracyPercent;
			this.documentCount = documentCount;
			this.featureCount = featureCount;
			this.classCount = classCount;
		}

		TreeMap<String,TreeMap<String,Double>> getScores()
		{
			return copyScores(scores);
		}

		Map<String,TreeMap<String,RecommendationCandidateEvidence>> getEvidence()
		{
			return copyEvidence(evidence);
		}

		double getAccuracyPercent()
		{
			return accuracyPercent;
		}

		String describe()
		{
			return documentCount + " global documents, "
					+ featureCount + " features, " + classCount + " classes";
		}

		private Outcome copy()
		{
			return new Outcome(
					scores, evidence, accuracyPercent,
					documentCount, featureCount, classCount);
		}
	}

	private static TreeMap<String,TreeMap<String,Double>> copyScores(
			Map<String,TreeMap<String,Double>> source)
	{
		TreeMap<String,TreeMap<String,Double>> copy =
				new TreeMap<String,TreeMap<String,Double>>();
		for (Map.Entry<String,TreeMap<String,Double>> entry : source.entrySet())
		{
			copy.put(entry.getKey(), new TreeMap<String,Double>(entry.getValue()));
		}
		return copy;
	}

	private static Map<String,TreeMap<String,RecommendationCandidateEvidence>>
			copyEvidence(
					Map<String,TreeMap<String,RecommendationCandidateEvidence>> source)
	{
		TreeMap<String,TreeMap<String,RecommendationCandidateEvidence>> copy =
				new TreeMap<String,TreeMap<String,RecommendationCandidateEvidence>>();
		for (Map.Entry<String,TreeMap<String,RecommendationCandidateEvidence>>
				entry : source.entrySet())
		{
			copy.put(
					entry.getKey(),
					new TreeMap<String,RecommendationCandidateEvidence>(
							entry.getValue()));
		}
		return copy;
	}

	private static final class RunState
	{
		private final int expectedNodes;
		private final List<String> targets;
		private final boolean buildSimilarUserEvidence;
		private final AlgorithmParameterSettings settings;
		private final TreeMap<Integer,Submission> submissions =
				new TreeMap<Integer,Submission>();
		private boolean building;
		private Outcome outcome;
		private Throwable failure;
		private int consumers;

		private RunState(
				int expectedNodes,
				List<String> targets,
				boolean buildSimilarUserEvidence,
				AlgorithmParameterSettings settings)
		{
			this.expectedNodes = expectedNodes;
			this.targets = targets;
			this.buildSimilarUserEvidence = buildSimilarUserEvidence;
			this.settings = settings == null
					? AlgorithmParameterSettings.defaults() : settings.copy();
		}

		private void validate(
				int requestedNodes,
				List<String> requestedTargets,
				boolean requestedEvidence,
				AlgorithmParameterSettings requestedSettings)
		{
			if (expectedNodes != Math.max(1, requestedNodes)
					|| !targets.equals(canonicalTargets(requestedTargets))
					|| buildSimilarUserEvidence != requestedEvidence
					|| !settings.equals(AlgorithmParameterSettings.effective(
							requestedSettings)))
			{
				throw new IllegalStateException(
						"Inconsistent shared scalable-SVM run configuration.");
			}
		}
	}

	private static final class Submission
	{
		private final Map<String,LinkedHashMap<String,Double>> documents;
		private final Map<String,String> localFollowees;
		private final Map<String,String> datasetFollowees;

		private Submission(
				Map<String,LinkedHashMap<String,Double>> documents,
				Map<String,String> localFollowees,
				Map<String,String> datasetFollowees)
		{
			this.documents = documents;
			this.localFollowees = localFollowees;
			this.datasetFollowees = datasetFollowees;
		}
	}

	private static final class GlobalCorpus
	{
		private final TreeMap<String,LinkedHashMap<String,Double>> documents;
		private final TreeMap<String,String> followees;

		private GlobalCorpus(
				TreeMap<String,LinkedHashMap<String,Double>> documents,
				TreeMap<String,String> followees)
		{
			this.documents = documents;
			this.followees = followees;
		}
	}

	private static final class TargetModelResult
	{
		private final String target;
		private final ExactSvmWorkerResult worker;

		private TargetModelResult(
				String target,
				ExactSvmWorkerResult worker)
		{
			this.target = target;
			this.worker = worker;
		}
	}
}
