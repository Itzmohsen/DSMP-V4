package TwitterGatherDataFollowers.userRyersonU;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import jade.core.Agent;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;

/**
 * JADE's Directory Facilitator can stall when hundreds of agents register at
 * the same instant. Keep registration serialized so large text experiments do
 * not deadlock before the recommendation algorithm starts.
 */
final class JadeDfRegistrationGate
{
	private static final int DEFAULT_PARALLEL_REGISTRATIONS = 1;
	private static final long DEFAULT_ACQUIRE_TIMEOUT_MILLIS = 60000L;
	private static final Semaphore REGISTRATION_PERMITS =
			new Semaphore(resolveParallelRegistrations(), true);

	private JadeDfRegistrationGate()
	{
	}

	static void register(Agent agent, DFAgentDescription description)
			throws FIPAException
	{
		boolean acquired = false;
		try
		{
			acquired = REGISTRATION_PERMITS.tryAcquire(
					resolveAcquireTimeoutMillis(),
					TimeUnit.MILLISECONDS);
			if (!acquired)
			{
				throw new FIPAException(
						"Timed out waiting for JADE DF registration; "
						+"another agent registration may be stuck.");
			}
			DFService.register(agent, description);
		}
		catch (InterruptedException e)
		{
			Thread.currentThread().interrupt();
			FIPAException failure = new FIPAException(
					"Interrupted while waiting for JADE DF registration.");
			failure.initCause(e);
			throw failure;
		}
		finally
		{
			if (acquired)
			{
				REGISTRATION_PERMITS.release();
			}
		}
	}

	private static int resolveParallelRegistrations()
	{
		return Math.max(
				1,
				Integer.getInteger(
						"dsmp.df.registration.parallelism",
						DEFAULT_PARALLEL_REGISTRATIONS).intValue());
	}

	private static long resolveAcquireTimeoutMillis()
	{
		return Math.max(
				1000L,
				Long.getLong(
						"dsmp.df.registration.acquireTimeoutMillis",
						DEFAULT_ACQUIRE_TIMEOUT_MILLIS).longValue());
	}
}
