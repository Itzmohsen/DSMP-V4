package TwitterGatherDataFollowers.userRyersonU;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Schedules isolated SVM JVMs in memory-sized waves.
 */
final class ExactSvmWorkerCoordinator
{
	private static final long MEBIBYTE = RecommendationMemoryCoordinator.MEBIBYTE;
	private static final ReentrantLock LOCK = new ReentrantLock(true);
	private static final Condition CAPACITY_CHANGED = LOCK.newCondition();
	private static final long TOTAL_BUDGET_MB = calculateTotalBudgetMb();

	private static long reservedMb;
	private static int activeWorkers;

	private ExactSvmWorkerCoordinator()
	{
	}

	static Lease acquire(String owner, String stage, long workerHeapMb)
	{
		long reservationMb = Math.max(512L, workerHeapMb);
		boolean interrupted = false;
		long waitStarted = System.nanoTime();
		LOCK.lock();
		try
		{
			while (activeWorkers > 0
					&& reservedMb + reservationMb > TOTAL_BUDGET_MB)
			{
				NodeExecutionMonitor.waitingForMemory(
						owner,
						stage + " (isolated model worker)",
						reservationMb);
				try
				{
					CAPACITY_CHANGED.await();
				}
				catch (InterruptedException exception)
				{
					interrupted = true;
				}
			}
			activeWorkers++;
			reservedMb += reservationMb;
			NodeExecutionMonitor.memoryStageActive(
					owner,
					stage + " in isolated model worker",
					reservationMb);
		}
		finally
		{
			LOCK.unlock();
		}
		if (interrupted)
		{
			Thread.currentThread().interrupt();
		}
		System.out.println(
				owner + " model worker admitted: heap=" + reservationMb
				+ " MB activeWorkers=" + activeWorkers
				+ " reserved=" + reservedMb + "/" + TOTAL_BUDGET_MB
				+ " MB waited="
				+ ((System.nanoTime() - waitStarted) / 1000000L) + " ms");
		return new Lease(owner, stage, reservationMb);
	}

	static long totalBudgetMb()
	{
		return TOTAL_BUDGET_MB;
	}

	private static long calculateTotalBudgetMb()
	{
		long physicalMb = RuntimeResourceSettings.totalPhysicalMemoryMb();
		if (physicalMb <= 0L)
		{
			return Math.max(
					1024L,
					RuntimeResourceSettings.currentMaxHeapMb() / 2L);
		}
		long operatingSystemReserve = Math.max(4096L, physicalMb / 5L);
		long mainJvmReserve = RuntimeResourceSettings.currentMaxHeapMb();
		return Math.max(
				1024L,
				physicalMb - operatingSystemReserve - mainJvmReserve);
	}

	static final class Lease implements AutoCloseable
	{
		private final String owner;
		private final String stage;
		private final long reservationMb;
		private boolean closed;

		private Lease(String owner, String stage, long reservationMb)
		{
			this.owner = owner;
			this.stage = stage;
			this.reservationMb = reservationMb;
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
				activeWorkers = Math.max(0, activeWorkers - 1);
				reservedMb = Math.max(0L, reservedMb - reservationMb);
				CAPACITY_CHANGED.signalAll();
			}
			finally
			{
				LOCK.unlock();
			}
			NodeExecutionMonitor.memoryStageReleased(owner, stage);
		}
	}
}
