package TwitterGatherDataFollowers.userRyersonU;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class AdaptiveModelConcurrencySelfTest
{
	private AdaptiveModelConcurrencySelfTest()
	{
	}

	public static void main(String[] args) throws Exception
	{
		allowsSafeParallelModelStages();
		honorsRequestedNodeLimit();
		allowsRequestedMlpParallelismWhenMemoryFits();
		completesKMeansNodesInMemorySizedWaves();
		wideExactSvmPreparationReceivesARealReservation();
		wideLegacyNeurophMlpReceivesARealReservation();
		serializesOversizedReservations();
		System.out.println("AdaptiveModelConcurrencySelfTest passed");
	}

	private static void allowsSafeParallelModelStages() throws Exception
	{
		long reservation = 128L * RecommendationMemoryCoordinator.MEBIBYTE;
		int expectedParallelism =
				RecommendationMemoryCoordinator.recommendedParallelism(
						RecommendationMemoryCoordinator.Workload.SVM,
						reservation);
		if (expectedParallelism < 2)
		{
			System.out.println(
					"Adaptive scheduler has room for only one model under this test heap.");
			return;
		}

		int workerCount = Math.min(expectedParallelism, 4);
		AtomicInteger active = new AtomicInteger();
		AtomicInteger maxActive = new AtomicInteger();
		CountDownLatch entered = new CountDownLatch(workerCount);
		CountDownLatch release = new CountDownLatch(1);
		List<Thread> workers = new ArrayList<Thread>();
		for (int i = 0; i < workerCount; i++)
		{
			final int worker = i;
			Thread thread = new Thread(new Runnable()
			{
				public void run()
				{
					try (RecommendationMemoryCoordinator.Lease ignored =
							RecommendationMemoryCoordinator.acquire(
									"parallel-test-"+worker,
									"small SVM",
									RecommendationMemoryCoordinator.Workload.SVM,
									reservation))
					{
						int nowActive = active.incrementAndGet();
						updateMaximum(maxActive, nowActive);
						entered.countDown();
						await(release);
						active.decrementAndGet();
					}
				}
			});
			workers.add(thread);
			thread.start();
		}

		require(entered.await(10, TimeUnit.SECONDS),
				"safe model reservations did not enter concurrently");
		release.countDown();
		join(workers);
		require(maxActive.get() >= 2,
				"adaptive scheduler still serialized every model stage");
	}

	private static void honorsRequestedNodeLimit() throws Exception
	{
		RecommendationMemoryCoordinator.configureRequestedParallelism(2);
		long reservation = 96L * RecommendationMemoryCoordinator.MEBIBYTE;
		AtomicInteger active = new AtomicInteger();
		AtomicInteger maxActive = new AtomicInteger();
		CountDownLatch firstWaveEntered = new CountDownLatch(2);
		CountDownLatch release = new CountDownLatch(1);
		List<Thread> workers = new ArrayList<Thread>();
		for (int index = 0; index < 4; index++)
		{
			final int worker = index;
			Thread thread = new Thread(new Runnable()
			{
				public void run()
				{
					try (RecommendationMemoryCoordinator.Lease ignored =
							RecommendationMemoryCoordinator.acquire(
									"requested-limit-"+worker,
									"small SVM",
									RecommendationMemoryCoordinator.Workload.SVM,
									reservation))
					{
						int nowActive = active.incrementAndGet();
						updateMaximum(maxActive, nowActive);
						firstWaveEntered.countDown();
						await(release);
						active.decrementAndGet();
					}
				}
			});
			workers.add(thread);
			thread.start();
		}

		require(firstWaveEntered.await(10, TimeUnit.SECONDS),
				"requested node limit did not admit its first wave");
		Thread.sleep(150L);
		require(maxActive.get() == 2,
				"requested node limit did not cap simultaneous model stages");
		release.countDown();
		join(workers);
		RecommendationMemoryCoordinator.configureRequestedParallelism(
				Integer.MAX_VALUE);
	}

	private static void allowsRequestedMlpParallelismWhenMemoryFits()
	{
		RecommendationMemoryCoordinator.configureRequestedParallelism(8);
		int expected = RecommendationMemoryCoordinator.recommendedParallelism(
				RecommendationMemoryCoordinator.Workload.MLP_TRAIN,
				96L * RecommendationMemoryCoordinator.MEBIBYTE);
		int memoryLimit = (int)(
				RecommendationMemoryCoordinator.modelBudgetBytes()
				/ (96L * RecommendationMemoryCoordinator.MEBIBYTE));
		require(expected == Math.min(8, Math.max(1, memoryLimit)),
				"MLP training retained an arbitrary four-node ceiling");
		RecommendationMemoryCoordinator.configureRequestedParallelism(
				Integer.MAX_VALUE);
	}

	private static void completesKMeansNodesInMemorySizedWaves()
			throws Exception
	{
		RecommendationMemoryCoordinator.configureRequestedParallelism(8);
		long reservation = RecommendationMemoryCoordinator.estimateKMeansBytes(
				75000, 40, 150000L);
		int expectedParallelism =
				RecommendationMemoryCoordinator.recommendedParallelism(
						RecommendationMemoryCoordinator.Workload.KMEANS,
						reservation);
		require(expectedParallelism >= 1,
				"K-Means scheduler did not admit any node");

		AtomicInteger active = new AtomicInteger();
		AtomicInteger maxActive = new AtomicInteger();
		AtomicInteger completed = new AtomicInteger();
		List<Thread> workers = new ArrayList<Thread>();
		for (int index = 0; index < 8; index++)
		{
			final int worker = index;
			Thread thread = new Thread(new Runnable()
			{
				public void run()
				{
					try (RecommendationMemoryCoordinator.Lease ignored =
							RecommendationMemoryCoordinator.acquire(
									"kmeans-wave-"+worker,
									"K-Means features and clustering",
									RecommendationMemoryCoordinator.Workload.KMEANS,
									reservation))
					{
						int nowActive = active.incrementAndGet();
						updateMaximum(maxActive, nowActive);
						try
						{
							Thread.sleep(40L);
						}
						catch (InterruptedException e)
						{
							Thread.currentThread().interrupt();
						}
						active.decrementAndGet();
						completed.incrementAndGet();
					}
				}
			});
			workers.add(thread);
			thread.start();
		}

		join(workers);
		require(completed.get() == 8,
				"not every requested K-Means node completed");
		require(maxActive.get() <= expectedParallelism,
				"K-Means exceeded the heap-sized execution wave");
		RecommendationMemoryCoordinator.configureRequestedParallelism(
				Integer.MAX_VALUE);
	}

	private static void serializesOversizedReservations() throws Exception
	{
		long reservation =
				RecommendationMemoryCoordinator.modelBudgetBytes() * 3L / 4L;
		AtomicInteger active = new AtomicInteger();
		AtomicInteger maxActive = new AtomicInteger();
		CountDownLatch firstEntered = new CountDownLatch(1);
		CountDownLatch releaseFirst = new CountDownLatch(1);

		Thread first = oversizedWorker(
				"oversized-first",
				reservation,
				active,
				maxActive,
				firstEntered,
				releaseFirst);
		Thread second = oversizedWorker(
				"oversized-second",
				reservation,
				active,
				maxActive,
				new CountDownLatch(0),
				new CountDownLatch(0));
		first.start();
		require(firstEntered.await(10, TimeUnit.SECONDS),
				"first oversized reservation was not admitted");
		second.start();
		Thread.sleep(150L);
		require(maxActive.get() == 1,
				"oversized reservations exceeded the model memory budget");
		releaseFirst.countDown();
		first.join(10000L);
		second.join(10000L);
		require(!first.isAlive() && !second.isAlive(),
				"oversized reservation test did not complete");
	}

	private static void wideExactSvmPreparationReceivesARealReservation()
	{
		long reservation =
				RecommendationMemoryCoordinator.estimateSvmPreparationBytes(
						250000,
						400,
						12000000L,
						8,
						16,
						true);
		require(
				reservation > 1024L * RecommendationMemoryCoordinator.MEBIBYTE,
				"wide exact-SVM preparation retained the old small reservation cap");
		require(
				RecommendationMemoryCoordinator.recommendedParallelism(
						RecommendationMemoryCoordinator.Workload.SVM,
						reservation) >= 1,
				"wide exact-SVM preparation could not enter a safe execution wave");
	}

	private static void wideLegacyNeurophMlpReceivesARealReservation()
	{
		long reservation =
				RecommendationMemoryCoordinator.estimateMlpTrainingBytes(
						250000,
						10,
						5,
						20,
						1000);
		require(
				reservation > 512L * RecommendationMemoryCoordinator.MEBIBYTE,
				"wide legacy Neuroph MLP retained the old small reservation cap");
		require(
				RecommendationMemoryCoordinator.recommendedParallelism(
						RecommendationMemoryCoordinator.Workload.MLP_TRAIN,
						reservation) >= 1,
				"wide legacy Neuroph MLP could not enter a safe execution wave");
	}

	private static Thread oversizedWorker(
			final String owner,
			final long reservation,
			final AtomicInteger active,
			final AtomicInteger maxActive,
			final CountDownLatch entered,
			final CountDownLatch release)
	{
		return new Thread(new Runnable()
		{
			public void run()
			{
				try (RecommendationMemoryCoordinator.Lease ignored =
						RecommendationMemoryCoordinator.acquire(
								owner,
								"oversized SVM",
								RecommendationMemoryCoordinator.Workload.SVM,
								reservation))
				{
					int nowActive = active.incrementAndGet();
					updateMaximum(maxActive, nowActive);
					entered.countDown();
					await(release);
					active.decrementAndGet();
				}
			}
		});
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

	private static void join(List<Thread> workers) throws InterruptedException
	{
		for (Thread worker : workers)
		{
			worker.join(10000L);
			require(!worker.isAlive(), "parallel worker did not terminate");
		}
	}

	private static void require(boolean condition, String message)
	{
		if (!condition)
		{
			throw new AssertionError(message);
		}
	}
}
