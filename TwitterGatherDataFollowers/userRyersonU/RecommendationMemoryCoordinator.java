package TwitterGatherDataFollowers.userRyersonU;

import java.io.File;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Admits independent model stages in parallel while reserving shared JVM heap.
 */
final class RecommendationMemoryCoordinator
{
	static final long MEBIBYTE = 1024L * 1024L;

	enum Workload
	{
		KMEANS,
		SVM,
		MLP_TRAIN,
		MLP_AVERAGE,
		MLP_SCORE,
		GENERIC
	}

	private static final Runtime RUNTIME = Runtime.getRuntime();
	private static final long MAX_HEAP_BYTES = RUNTIME.maxMemory();
	private static final int CPU_COUNT =
			Math.max(1, Runtime.getRuntime().availableProcessors());
	private static final int CONFIGURED_PARALLEL_LIMIT =
			readPositiveIntProperty(
					"recommender.model.parallelism",
					CPU_COUNT);
	private static final long NON_MODEL_ALLOWANCE_BYTES =
			Math.max(256L * MEBIBYTE, MAX_HEAP_BYTES * 35L / 100L);
	private static final long MODEL_BUDGET_BYTES =
			Math.max(
					96L * MEBIBYTE,
					MAX_HEAP_BYTES - NON_MODEL_ALLOWANCE_BYTES);
	private static final long LIVE_HEAP_SAFETY_BYTES =
			Math.max(96L * MEBIBYTE, MAX_HEAP_BYTES / 12L);

	private static final ReentrantLock LOCK = new ReentrantLock(true);
	private static final Condition CAPACITY_CHANGED = LOCK.newCondition();
	private static final Map<Workload,Integer> ACTIVE_BY_WORKLOAD =
			new EnumMap<Workload,Integer>(Workload.class);

	private static long reservedBytes;
	private static int activeLeases;
	private static int waitingLeases;
	private static int requestedParallelism = CONFIGURED_PARALLEL_LIMIT;
	private static volatile StatusListener statusListener;

	static
	{
		for (Workload workload : Workload.values())
		{
			ACTIVE_BY_WORKLOAD.put(workload, Integer.valueOf(0));
		}
		System.out.println(
				"Adaptive model scheduler: maxHeap="
				+toMegabytes(MAX_HEAP_BYTES)+" MB modelBudget="
				+toMegabytes(MODEL_BUDGET_BYTES)+" MB parallelLimit="
				+CONFIGURED_PARALLEL_LIMIT);
	}

	private RecommendationMemoryCoordinator()
	{
	}

	static Lease acquire(String owner, String stage)
	{
		return acquire(owner, stage, Workload.GENERIC, 160L * MEBIBYTE);
	}

	static Lease acquire(
			String owner,
			String stage,
			Workload workload,
			long estimatedBytes)
	{
		long reservation = normalizeReservation(estimatedBytes);
		long waitStart = System.nanoTime();
		boolean interrupted = false;

		LOCK.lock();
		try
		{
			boolean waitingRegistered = false;
			while (!canAdmit(workload, reservation))
			{
				if (!waitingRegistered)
				{
					waitingLeases++;
					waitingRegistered = true;
					NodeExecutionMonitor.waitingForMemory(
							owner, stage, toMegabytes(reservation));
					publishStatusLocked();
				}
				try
				{
					CAPACITY_CHANGED.await();
				}
				catch (InterruptedException e)
				{
					interrupted = true;
				}
			}
			if (waitingRegistered)
			{
				waitingLeases--;
			}
			reservedBytes += reservation;
			activeLeases++;
			ACTIVE_BY_WORKLOAD.put(
					workload,
					Integer.valueOf(ACTIVE_BY_WORKLOAD.get(workload).intValue() + 1));
			NodeExecutionMonitor.memoryStageActive(
					owner, stage, toMegabytes(reservation));
			publishStatusLocked();
		}
		finally
		{
			LOCK.unlock();
		}

		if (interrupted)
		{
			Thread.currentThread().interrupt();
		}

		long waitedMs = (System.nanoTime() - waitStart) / 1000000L;
		System.out.println(
				owner+" entered memory-intensive stage: "+stage
				+" workload="+workload
				+" reservation="+toMegabytes(reservation)+" MB"
				+" active="+activeLeaseCount()
				+" reserved="+toMegabytes(reservedByteCount())+" MB"
				+" waited="+waitedMs+" ms");
		return new Lease(owner, stage, workload, reservation);
	}

	static long estimateSvmBytes(
			int featureCount,
			int instanceCount,
			File... arffFiles)
	{
		long arffBytes = 0L;
		if (arffFiles != null)
		{
			for (File arffFile : arffFiles)
			{
				if (arffFile != null && arffFile.isFile())
				{
					arffBytes = saturatingAdd(arffBytes, arffFile.length());
				}
			}
		}

		long attributeCopies = saturatingMultiply(
				Math.max(1, featureCount), 3L * 400L);
		long parsedSparseData = saturatingMultiply(arffBytes, 4L);
		long kernelWorkspace = saturatingMultiply(
				saturatingMultiply(Math.max(1, instanceCount),
						Math.max(1, instanceCount)),
				24L);
		long estimate = 64L * MEBIBYTE;
		estimate = saturatingAdd(estimate, attributeCopies);
		estimate = saturatingAdd(estimate, parsedSparseData);
		estimate = saturatingAdd(estimate, kernelWorkspace);
		return clampAndAlign(estimate, 96L * MEBIBYTE, 384L * MEBIBYTE);
	}

	static long estimateSvmPreparationBytes(
			int featureCount,
			int userCount,
			long nonZeroTermCount,
			int targetCount,
			int classCount,
			boolean targetSpecificPreparation)
	{
		/*
		 * Exact SVM preparation happens in the main JADE JVM before the
		 * isolated Weka worker is admitted. It retains feature indexes and
		 * document maps while constructing document-frequency/TF-IDF maps and
		 * sparse ARFF files. Batch and converted recommendation modes also
		 * construct one target-specific TF-IDF copy at a time.
		 */
		long featureState = saturatingMultiply(
				Math.max(1, featureCount), 1280L);
		long preparedTermState = saturatingMultiply(
				Math.max(1L, nonZeroTermCount),
				targetSpecificPreparation ? 112L : 80L);
		long userState = saturatingMultiply(
				Math.max(1, userCount), 4096L);
		long targetState = saturatingMultiply(
				saturatingMultiply(
						Math.max(1, targetCount), Math.max(1, classCount)),
				128L);
		targetState = saturatingAdd(
				targetState,
				saturatingMultiply(Math.max(1, targetCount), 64L * 1024L));

		long estimate = 256L * MEBIBYTE;
		estimate = saturatingAdd(estimate, featureState);
		estimate = saturatingAdd(estimate, preparedTermState);
		estimate = saturatingAdd(estimate, userState);
		estimate = saturatingAdd(estimate, targetState);
		return alignToMegabytes(estimate);
	}

	static long estimateKMeansBytes(
			int featureCount,
			int userCount,
			long nonZeroTermCount)
	{
		long centroidMaps = saturatingMultiply(
				Math.max(1, featureCount), 3L * 56L);
		long pointAndIterationState = saturatingMultiply(
				Math.max(1L, nonZeroTermCount), 24L);
		long scoreMaps = saturatingMultiply(
				saturatingMultiply(
						Math.max(1, userCount), Math.max(1, userCount)),
				40L);
		long estimate = 64L * MEBIBYTE;
		estimate = saturatingAdd(estimate, centroidMaps);
		estimate = saturatingAdd(estimate, pointAndIterationState);
		estimate = saturatingAdd(estimate, scoreMaps);
		return clampAndAlign(estimate, 96L * MEBIBYTE, 384L * MEBIBYTE);
	}

	static long estimateMlpTrainingBytes(
			int featureCount,
			int hiddenNeuronCount,
			int outputCount,
			int denseRowCount)
	{
		return estimateMlpTrainingBytes(
				featureCount, hiddenNeuronCount, 1, outputCount, denseRowCount);
	}

	static long estimateMlpTrainingBytes(
			int featureCount,
			int hiddenNeuronCount,
			int hiddenLayerCount,
			int outputCount,
			int denseRowCount)
	{
		int hiddenLayers = Math.max(1, hiddenLayerCount);
		long inputConnections = saturatingMultiply(
				Math.max(1, featureCount), Math.max(1, hiddenNeuronCount));
		long hiddenConnections = saturatingMultiply(
				Math.max(0, hiddenLayers - 1),
				saturatingMultiply(
						Math.max(1, hiddenNeuronCount),
						Math.max(1, hiddenNeuronCount)));
		long outputConnections = saturatingMultiply(
				Math.max(1, hiddenNeuronCount), Math.max(1, outputCount));
		long connectionObjects = saturatingMultiply(
				saturatingAdd(
						saturatingAdd(inputConnections, hiddenConnections),
						outputConnections),
				72L);
		long denseVectors = saturatingMultiply(
				saturatingMultiply(
						Math.max(1, denseRowCount), Math.max(1, featureCount)),
				8L);
		long estimate = 80L * MEBIBYTE;
		estimate = saturatingAdd(estimate, connectionObjects);
		estimate = saturatingAdd(estimate, denseVectors);
		return alignToMegabytes(Math.max(144L * MEBIBYTE, estimate));
	}

	static long estimateSparseMlpTrainingBytes(
			int featureCount,
			int hiddenNeuronCount,
			int hiddenLayerCount,
			int outputCount,
			long nonZeroFeatureCount,
			int sparseRowCount)
	{
		int hiddenLayers = Math.max(1, hiddenLayerCount);
		long inputWeights = saturatingMultiply(
				Math.max(1, featureCount), Math.max(1, hiddenNeuronCount));
		long hiddenWeights = saturatingMultiply(
				Math.max(0, hiddenLayers - 1),
				saturatingMultiply(
						Math.max(1, hiddenNeuronCount),
						Math.max(1, hiddenNeuronCount)));
		long outputWeights = saturatingMultiply(
				Math.max(1, hiddenNeuronCount), Math.max(1, outputCount));
		long weights = saturatingMultiply(
				saturatingAdd(saturatingAdd(inputWeights, hiddenWeights), outputWeights),
				8L);
		long sparseExamples = saturatingMultiply(
				Math.max(1L, nonZeroFeatureCount), 40L);
		sparseExamples = saturatingAdd(
				sparseExamples,
				saturatingMultiply(Math.max(1, sparseRowCount), 192L));
		long estimate = 64L * MEBIBYTE;
		estimate = saturatingAdd(estimate, weights);
		estimate = saturatingAdd(estimate, sparseExamples);
		return clampAndAlign(estimate, 96L * MEBIBYTE, 384L * MEBIBYTE);
	}

	static long estimateMlpModelBytes(File modelFile, int multiplier)
	{
		long fileBytes = modelFile != null && modelFile.isFile()
				? modelFile.length() : 0L;
		long estimate = saturatingAdd(
				64L * MEBIBYTE,
				saturatingMultiply(fileBytes, Math.max(2, multiplier)));
		return clampAndAlign(estimate, 96L * MEBIBYTE, 384L * MEBIBYTE);
	}

	static int recommendedParallelism(Workload workload, long estimatedBytes)
	{
		long reservation = normalizeReservation(estimatedBytes);
		int memoryParallelism = (int)Math.max(
				1L, MODEL_BUDGET_BYTES / reservation);
		return Math.max(
				1,
				Math.min(
						Math.min(effectiveParallelLimit(), workloadLimit(workload)),
						memoryParallelism));
	}

	static void configureRequestedParallelism(int requested)
	{
		LOCK.lock();
		try
		{
			requestedParallelism = Math.max(1, requested);
			CAPACITY_CHANGED.signalAll();
			publishStatusLocked();
		}
		finally
		{
			LOCK.unlock();
		}
	}

	static void setStatusListener(StatusListener listener)
	{
		statusListener = listener;
		publishStatus();
	}

	static Snapshot snapshot()
	{
		LOCK.lock();
		try
		{
			return new Snapshot(
					toMegabytes(MAX_HEAP_BYTES),
					toMegabytes(MODEL_BUDGET_BYTES),
					CONFIGURED_PARALLEL_LIMIT,
					requestedParallelism,
					effectiveParallelLimit(),
					activeLeases,
					waitingLeases,
					toMegabytes(reservedBytes));
		}
		finally
		{
			LOCK.unlock();
		}
	}

	static long modelBudgetBytes()
	{
		return MODEL_BUDGET_BYTES;
	}

	private static boolean canAdmit(Workload workload, long reservation)
	{
		if (activeLeases >= effectiveParallelLimit())
		{
			return false;
		}
		if (ACTIVE_BY_WORKLOAD.get(workload).intValue() >= workloadLimit(workload))
		{
			return false;
		}
		if (reservedBytes + reservation > MODEL_BUDGET_BYTES)
		{
			return false;
		}

		if (activeLeases == 0)
		{
			return true;
		}

		long usedHeap = usedHeapBytes();
		return usedHeap <= MAX_HEAP_BYTES - LIVE_HEAP_SAFETY_BYTES - reservation;
	}

	private static int workloadLimit(Workload workload)
	{
		switch (workload)
		{
			case KMEANS:
			case MLP_TRAIN:
				return effectiveParallelLimit();
			case MLP_AVERAGE:
				return 1;
			case SVM:
			case MLP_SCORE:
			case GENERIC:
			default:
				return effectiveParallelLimit();
		}
	}

	private static int effectiveParallelLimit()
	{
		return Math.max(
				1,
				Math.min(CONFIGURED_PARALLEL_LIMIT, requestedParallelism));
	}

	private static long normalizeReservation(long requestedBytes)
	{
		long positiveBytes = Math.max(32L * MEBIBYTE, requestedBytes);
		return Math.min(MODEL_BUDGET_BYTES, alignToMegabytes(positiveBytes));
	}

	private static long clampAndAlign(long value, long minimum, long maximum)
	{
		return alignToMegabytes(Math.max(minimum, Math.min(maximum, value)));
	}

	private static long alignToMegabytes(long bytes)
	{
		return ((bytes + MEBIBYTE - 1L) / MEBIBYTE) * MEBIBYTE;
	}

	private static long usedHeapBytes()
	{
		return RUNTIME.totalMemory() - RUNTIME.freeMemory();
	}

	private static long saturatingAdd(long first, long second)
	{
		if (Long.MAX_VALUE - first < second)
		{
			return Long.MAX_VALUE;
		}
		return first + second;
	}

	private static long saturatingMultiply(long first, long second)
	{
		if (first == 0L || second == 0L)
		{
			return 0L;
		}
		if (first > Long.MAX_VALUE / second)
		{
			return Long.MAX_VALUE;
		}
		return first * second;
	}

	private static int readPositiveIntProperty(String name, int defaultValue)
	{
		String configured = System.getProperty(name);
		if (configured == null)
		{
			return defaultValue;
		}
		try
		{
			return Math.max(1, Integer.parseInt(configured));
		}
		catch (NumberFormatException ignored)
		{
			return defaultValue;
		}
	}

	private static long toMegabytes(long bytes)
	{
		return bytes / MEBIBYTE;
	}

	private static int activeLeaseCount()
	{
		LOCK.lock();
		try
		{
			return activeLeases;
		}
		finally
		{
			LOCK.unlock();
		}
	}

	private static long reservedByteCount()
	{
		LOCK.lock();
		try
		{
			return reservedBytes;
		}
		finally
		{
			LOCK.unlock();
		}
	}

	private static void publishStatus()
	{
		LOCK.lock();
		try
		{
			publishStatusLocked();
		}
		finally
		{
			LOCK.unlock();
		}
	}

	private static void publishStatusLocked()
	{
		StatusListener listener = statusListener;
		if (listener != null)
		{
			listener.onStatus(new Snapshot(
					toMegabytes(MAX_HEAP_BYTES),
					toMegabytes(MODEL_BUDGET_BYTES),
					CONFIGURED_PARALLEL_LIMIT,
					requestedParallelism,
					effectiveParallelLimit(),
					activeLeases,
					waitingLeases,
					toMegabytes(reservedBytes)));
		}
	}

	interface StatusListener
	{
		void onStatus(Snapshot snapshot);
	}

	static final class Snapshot
	{
		private final long maxHeapMb;
		private final long modelBudgetMb;
		private final int configuredLimit;
		private final int requestedParallelism;
		private final int effectiveLimit;
		private final int activeLeases;
		private final int waitingLeases;
		private final long reservedMb;

		private Snapshot(
				long maxHeapMb,
				long modelBudgetMb,
				int configuredLimit,
				int requestedParallelism,
				int effectiveLimit,
				int activeLeases,
				int waitingLeases,
				long reservedMb)
		{
			this.maxHeapMb = maxHeapMb;
			this.modelBudgetMb = modelBudgetMb;
			this.configuredLimit = configuredLimit;
			this.requestedParallelism = requestedParallelism;
			this.effectiveLimit = effectiveLimit;
			this.activeLeases = activeLeases;
			this.waitingLeases = waitingLeases;
			this.reservedMb = reservedMb;
		}

		long getMaxHeapMb()
		{
			return maxHeapMb;
		}

		long getModelBudgetMb()
		{
			return modelBudgetMb;
		}

		int getConfiguredLimit()
		{
			return configuredLimit;
		}

		int getRequestedParallelism()
		{
			return requestedParallelism;
		}

		int getEffectiveLimit()
		{
			return effectiveLimit;
		}

		int getActiveLeases()
		{
			return activeLeases;
		}

		int getWaitingLeases()
		{
			return waitingLeases;
		}

		long getReservedMb()
		{
			return reservedMb;
		}
	}

	static final class Lease implements AutoCloseable
	{
		private final String owner;
		private final String stage;
		private final Workload workload;
		private final long reservation;
		private boolean closed;

		private Lease(
				String owner,
				String stage,
				Workload workload,
				long reservation)
		{
			this.owner = owner;
			this.stage = stage;
			this.workload = workload;
			this.reservation = reservation;
		}

		public void close()
		{
			LOCK.lock();
			try
			{
				if (closed)
				{
					return;
				}
				closed = true;
				reservedBytes -= reservation;
				activeLeases--;
				ACTIVE_BY_WORKLOAD.put(
						workload,
						Integer.valueOf(
								ACTIVE_BY_WORKLOAD.get(workload).intValue() - 1));
				CAPACITY_CHANGED.signalAll();
				publishStatusLocked();
			}
			finally
			{
				LOCK.unlock();
			}
			System.out.println(
					owner+" left memory-intensive stage: "+stage
					+" workload="+workload
					+" active="+activeLeaseCount()
					+" reserved="+toMegabytes(reservedByteCount())+" MB");
			NodeExecutionMonitor.memoryStageReleased(owner, stage);
		}
	}
}
