package TwitterGatherDataFollowers.userRyersonU;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
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
		admitsACollectiveGroupPastTheParallelLimit();
		admitsACollectiveGroupPastTheMemoryBudget();
		keepsOtherStagesWaitingWhileAGroupRunsPastTheLimit();
		admitsAGroupsFirstNodeByTheUsualRules();
		System.out.println("AdaptiveModelConcurrencySelfTest passed");
	}

	/**
	 * Four nodes of one run take their leases and then meet in a collective
	 * step, as the exact SVM's corpus gather does. With room for two, nodes
	 * left waiting would stall the admitted ones forever (the four-node study
	 * that never opened its SVM phase).
	 */
	private static void admitsACollectiveGroupPastTheParallelLimit() throws Exception
	{
		RecommendationMemoryCoordinator.configureRequestedParallelism(2);
		require(runsCollectiveGroup("run-a/exact-svm", 4,
				96L * RecommendationMemoryCoordinator.MEBIBYTE),
				"a run's nodes deadlocked at the parallel limit");
		RecommendationMemoryCoordinator.configureRequestedParallelism(
				Integer.MAX_VALUE);
	}

	/** The same at the model budget: three reservations of three quarters of it. */
	private static void admitsACollectiveGroupPastTheMemoryBudget() throws Exception
	{
		long reservation =
				RecommendationMemoryCoordinator.modelBudgetBytes() * 3L / 4L;
		require(runsCollectiveGroup("run-b/kmeans", 3, reservation),
				"a run's nodes deadlocked at the model budget");
	}

	/** A group past the limit does not open the gate for unrelated stages. */
	private static void keepsOtherStagesWaitingWhileAGroupRunsPastTheLimit()
			throws Exception
	{
		RecommendationMemoryCoordinator.configureRequestedParallelism(2);
		long reservation = 96L * RecommendationMemoryCoordinator.MEBIBYTE;
		CountDownLatch groupEntered = new CountDownLatch(3);
		CountDownLatch releaseGroup = new CountDownLatch(1);
		List<Thread> workers = new ArrayList<Thread>();
		for (int index = 0; index < 3; index++)
		{
			workers.add(groupWorker("run-c/kmeans", "group-node-" + index,
					reservation, groupEntered, releaseGroup));
		}
		for (Thread worker : workers)
		{
			worker.start();
		}
		require(groupEntered.await(10, TimeUnit.SECONDS),
				"a run's nodes were not admitted together");
		CountDownLatch otherEntered = new CountDownLatch(1);
		Thread other = plainWorker("unrelated-stage", reservation,
				otherEntered, new CountDownLatch(0));
		other.start();
		require(!otherEntered.await(150L, TimeUnit.MILLISECONDS),
				"an unrelated stage was admitted past the parallel limit");
		releaseGroup.countDown();
		require(otherEntered.await(10, TimeUnit.SECONDS),
				"an unrelated stage was not admitted once the group finished");
		workers.add(other);
		join(workers);
		RecommendationMemoryCoordinator.configureRequestedParallelism(
				Integer.MAX_VALUE);
	}

	/**
	 * While no node of a group holds a lease, its first node waits like any
	 * stage; once it is admitted, the rest of the group follows at once.
	 */
	private static void admitsAGroupsFirstNodeByTheUsualRules() throws Exception
	{
		RecommendationMemoryCoordinator.configureRequestedParallelism(1);
		long reservation = 96L * RecommendationMemoryCoordinator.MEBIBYTE;
		CountDownLatch blockerEntered = new CountDownLatch(1);
		CountDownLatch releaseBlocker = new CountDownLatch(1);
		Thread blocker = plainWorker("earlier-stage", reservation,
				blockerEntered, releaseBlocker);
		blocker.start();
		require(blockerEntered.await(10, TimeUnit.SECONDS),
				"the earlier stage was not admitted");
		CountDownLatch groupEntered = new CountDownLatch(2);
		CountDownLatch releaseGroup = new CountDownLatch(1);
		List<Thread> workers = new ArrayList<Thread>();
		for (int index = 0; index < 2; index++)
		{
			workers.add(groupWorker("run-d/exact-svm", "waiting-node-" + index,
					reservation, groupEntered, releaseGroup));
		}
		for (Thread worker : workers)
		{
			worker.start();
		}
		require(!groupEntered.await(150L, TimeUnit.MILLISECONDS),
				"a group's first node skipped the parallel limit");
		releaseBlocker.countDown();
		require(groupEntered.await(10, TimeUnit.SECONDS),
				"the group was not admitted together once the gate opened");
		releaseGroup.countDown();
		workers.add(blocker);
		join(workers);
		RecommendationMemoryCoordinator.configureRequestedParallelism(
				Integer.MAX_VALUE);
	}

	/**
	 * Runs {@code nodes} threads that each take a lease of {@code group} and
	 * then meet at a barrier while holding it; true if every node got through.
	 */
	private static boolean runsCollectiveGroup(
			final String group,
			int nodes,
			final long reservation) throws Exception
	{
		final CyclicBarrier collective = new CyclicBarrier(nodes);
		final AtomicInteger met = new AtomicInteger();
		List<Thread> workers = new ArrayList<Thread>();
		for (int index = 0; index < nodes; index++)
		{
			final int node = index + 1;
			Thread thread = new Thread(new Runnable()
			{
				public void run()
				{
					try (RecommendationMemoryCoordinator.Lease ignored =
							RecommendationMemoryCoordinator.acquireTogether(
									group,
									"collective-node-" + node,
									"collective stage",
									RecommendationMemoryCoordinator.Workload.SVM,
									reservation))
					{
						collective.await(10L, TimeUnit.SECONDS);
						met.incrementAndGet();
					}
					catch (Exception brokenOrTimedOut)
					{
						//A node never reached the collective step.
					}
				}
			});
			thread.setDaemon(true);
			workers.add(thread);
			thread.start();
		}
		for (Thread worker : workers)
		{
			worker.join(30000L);
		}
		return met.get() == nodes;
	}

	private static Thread groupWorker(
			final String group,
			final String owner,
			final long reservation,
			final CountDownLatch entered,
			final CountDownLatch release)
	{
		Thread thread = new Thread(new Runnable()
		{
			public void run()
			{
				try (RecommendationMemoryCoordinator.Lease ignored =
						RecommendationMemoryCoordinator.acquireTogether(
								group,
								owner,
								"group stage",
								RecommendationMemoryCoordinator.Workload.KMEANS,
								reservation))
				{
					entered.countDown();
					await(release);
				}
			}
		});
		thread.setDaemon(true);
		return thread;
	}

	private static Thread plainWorker(
			final String owner,
			final long reservation,
			final CountDownLatch entered,
			final CountDownLatch release)
	{
		Thread thread = new Thread(new Runnable()
		{
			public void run()
			{
				try (RecommendationMemoryCoordinator.Lease ignored =
						RecommendationMemoryCoordinator.acquire(
								owner,
								"plain stage",
								RecommendationMemoryCoordinator.Workload.SVM,
								reservation))
				{
					entered.countDown();
					await(release);
				}
			}
		});
		thread.setDaemon(true);
		return thread;
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
		//The configured limit defaults to the machine's CPU count, so a 4-core
		//runner legitimately stops at 4; only a cap below both limits is wrong.
		int configuredLimit = RecommendationMemoryCoordinator.snapshot().getConfiguredLimit();
		require(expected == Math.min(Math.min(8, configuredLimit), Math.max(1, memoryLimit)),
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
