package TwitterGatherDataFollowers.userRyersonU;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import weka.classifiers.functions.SMO;
import weka.core.Instances;

public final class MillionRowSparseSvmSelfTest
{
	private MillionRowSparseSvmSelfTest()
	{
	}

	public static void main(String[] args) throws Exception
	{
		if (args.length < 1 || args.length > 2)
		{
			throw new IllegalArgumentException(
					"Expected the generated dataset path and optional worker count");
		}
		int requestedWorkers = args.length == 2
				? Math.max(1, Integer.parseInt(args[1])) : 1;

		Map<String,LinkedHashMap<String,Double>> documents =
				new LinkedHashMap<String,LinkedHashMap<String,Double>>();
		Map<String,String> userClasses = new LinkedHashMap<String,String>();
		TreeSet<String> terms = new TreeSet<String>();
		TreeSet<String> classes = new TreeSet<String>();
		long rows = 0L;

		try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(
						new FileInputStream(args[0]), StandardCharsets.UTF_8),
				128 * 1024))
		{
			String line;
			while ((line = reader.readLine()) != null)
			{
				DatasetRow row = DatasetRow.parse(line, rows + 1L);
				LinkedHashMap<String,Double> document =
						documents.get(row.userName);
				if (document == null)
				{
					document = new LinkedHashMap<String,Double>();
					documents.put(row.userName, document);
					userClasses.put(row.userName, row.referenceUser);
				}
				classes.add(row.referenceUser);
				for (String token : row.text.toLowerCase().split("[^a-z]+"))
				{
					if (token.length() == 0)
						continue;
					terms.add(token);
					Double frequency = document.get(token);
					document.put(token, frequency == null ? 1.0 : frequency + 1.0);
				}
				rows++;
			}
		}

		require(rows == 1000000L, "stress corpus does not contain one million rows");
		require(terms.size() >= 70000,
				"stress corpus did not reproduce a 70k-plus feature space");
		require(classes.size() >= 2, "stress corpus needs multiple SVM classes");

		Map<String,Integer> documentFrequency =
				new LinkedHashMap<String,Integer>();
		for (Map<String,Double> document : documents.values())
		{
			for (String term : document.keySet())
			{
				Integer frequency = documentFrequency.get(term);
				documentFrequency.put(
						term,
						Integer.valueOf(frequency == null ? 1 : frequency.intValue() + 1));
			}
		}

		int documentCount = documents.size();
		for (Map.Entry<String,LinkedHashMap<String,Double>> entry
				: documents.entrySet())
		{
			LinkedHashMap<String,Double> raw = entry.getValue();
			LinkedHashMap<String,Double> normalized =
					new LinkedHashMap<String,Double>();
			double magnitude = 0.0;
			for (Map.Entry<String,Double> termEntry : raw.entrySet())
			{
				int df = documentFrequency.get(termEntry.getKey()).intValue();
				double idf = Math.log10(documentCount / df) / Math.log10(2);
				double tfidf = termEntry.getValue().doubleValue() * idf;
				normalized.put(termEntry.getKey(), Double.valueOf(tfidf));
				magnitude += tfidf * tfidf;
			}
			magnitude = Math.sqrt(magnitude);
			for (Map.Entry<String,Double> termEntry : normalized.entrySet())
			{
				termEntry.setValue(Double.valueOf(
						termEntry.getValue().doubleValue() / magnitude));
			}
			entry.setValue(normalized);
		}

		File arff = File.createTempFile("million-row-sparse-svm-", ".arff");
		try
		{
			ArrayList<String> orderedTerms = new ArrayList<String>(terms);
			try (BufferedWriter writer = new BufferedWriter(new FileWriter(arff)))
			{
				SparseArffWriter.writeHeader(
						writer,
						"millionRowSparseSvm",
						orderedTerms,
						new ArrayList<String>(classes));
				for (Map.Entry<String,LinkedHashMap<String,Double>> entry
						: documents.entrySet())
				{
					SparseArffWriter.writeRow(
							writer,
							entry.getValue(),
							orderedTerms,
							userClasses.get(entry.getKey()));
				}
			}

			long reservation =
					RecommendationMemoryCoordinator.estimateSvmBytes(
							terms.size(), documents.size(), arff);
			int workerCount = Math.min(
					requestedWorkers,
					RecommendationMemoryCoordinator.recommendedParallelism(
							RecommendationMemoryCoordinator.Workload.SVM,
							reservation));

			long sequentialStart = System.nanoTime();
			List<double[]> sequentialScores = new ArrayList<double[]>();
			for (int worker = 0; worker < workerCount; worker++)
			{
				sequentialScores.add(trainModel(arff));
			}
			long sequentialMs =
					(System.nanoTime() - sequentialStart) / 1000000L;

			System.gc();
			Thread.sleep(100L);

			AtomicInteger active = new AtomicInteger();
			AtomicInteger maxActive = new AtomicInteger();
			CountDownLatch start = new CountDownLatch(1);
			List<double[]> parallelScores =
					Collections.synchronizedList(new ArrayList<double[]>());
			List<Throwable> failures =
					Collections.synchronizedList(new ArrayList<Throwable>());
			List<Thread> workers = new ArrayList<Thread>();
			long parallelStart = System.nanoTime();
			for (int worker = 0; worker < workerCount; worker++)
			{
				final int workerNumber = worker;
				Thread thread = new Thread(new Runnable()
				{
					public void run()
					{
						await(start);
						try (RecommendationMemoryCoordinator.Lease ignored =
								RecommendationMemoryCoordinator.acquire(
										"wide-svm-worker-"+workerNumber,
										"million-row sparse SVM",
										RecommendationMemoryCoordinator.Workload.SVM,
										reservation))
						{
							int nowActive = active.incrementAndGet();
							updateMaximum(maxActive, nowActive);
							try
							{
								parallelScores.add(trainModel(arff));
							}
							finally
							{
								active.decrementAndGet();
							}
						}
						catch (Throwable failure)
						{
							failures.add(failure);
						}
					}
				});
				workers.add(thread);
				thread.start();
			}
			start.countDown();
			for (Thread worker : workers)
			{
				worker.join(120000L);
				require(!worker.isAlive(), "parallel wide SVM worker timed out");
			}
			long parallelMs = (System.nanoTime() - parallelStart) / 1000000L;

			require(failures.isEmpty(),
					"parallel wide SVM failed: "+failures);
			require(parallelScores.size() == workerCount,
					"parallel wide SVM lost model results");
			if (workerCount > 1)
			{
				require(maxActive.get() > 1,
						"wide SVM models were still serialized");
			}
			for (double[] score : sequentialScores)
			{
				require(Arrays.equals(sequentialScores.get(0), score),
						"sequential wide SVM models changed scores");
			}
			for (double[] score : parallelScores)
			{
				require(Arrays.equals(sequentialScores.get(0), score),
						"parallel scheduling changed SVM scores");
			}

			Runtime runtime = Runtime.getRuntime();
			long usedMb = (runtime.totalMemory() - runtime.freeMemory())
					/ (1024L * 1024L);
			System.out.println(
					"MillionRowSparseSvmSelfTest passed: rows=" + rows
					+ " users=" + documents.size()
					+ " terms=" + terms.size()
					+ " classes=" + classes.size()
					+ " workers=" + workerCount
					+ " maxConcurrent=" + maxActive.get()
					+ " sequentialMs=" + sequentialMs
					+ " parallelMs=" + parallelMs
					+ " speedup="
					+ String.format("%.2fx", sequentialMs / (double)Math.max(1L, parallelMs))
					+ " usedHeapMB=" + usedMb);
		}
		finally
		{
			arff.delete();
		}
	}

	private static double[] trainModel(File arff) throws Exception
	{
		Instances instances;
		try (FileReader reader = new FileReader(arff))
		{
			instances = new Instances(reader);
		}
		instances.setClassIndex(instances.numAttributes() - 1);
		SMO model = new SMO();
		model.setC(0.1);
		model.setBuildCalibrationModels(true);
		SvmReproducibility.applyModelSeed(model);
		model.buildClassifier(instances);
		return model.distributionForInstance(instances.instance(0));
	}

	private static void await(CountDownLatch latch)
	{
		boolean interrupted = false;
		while (true)
		{
			try
			{
				latch.await();
				break;
			}
			catch (InterruptedException e)
			{
				interrupted = true;
			}
		}
		if (interrupted)
		{
			Thread.currentThread().interrupt();
		}
	}

	private static void updateMaximum(AtomicInteger maximum, int value)
	{
		int current;
		do
		{
			current = maximum.get();
			if (value <= current)
				return;
		}
		while (!maximum.compareAndSet(current, value));
	}

	private static void require(boolean condition, String message)
	{
		if (!condition)
		{
			throw new AssertionError(message);
		}
	}
}
