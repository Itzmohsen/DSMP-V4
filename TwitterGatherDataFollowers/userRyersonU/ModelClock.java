package TwitterGatherDataFollowers.userRyersonU;

/**
 * The model time of one live run.
 *
 * Model time starts at 0 when the run's scheduling context is created and
 * advances at 1 / TDF of wall-clock speed, where TDF is the run's time
 * dilation factor: every emulated duration is stretched by the TDF in wall
 * time, so model time = wall time / TDF. With TDF 1, model time is wall time.
 * All timings of a run (unit executions, phases, messages, collectives,
 * crashes, scenario triggers) are expressed in model milliseconds, the same
 * time scale as the discrete-event simulator.
 */
final class ModelClock
{
	private final long originNanos;
	private final double dilation;

	ModelClock(double dilation)
	{
		this(System.nanoTime(), dilation);
	}

	ModelClock(long originNanos, double dilation)
	{
		this.originNanos = originNanos;
		this.dilation = Double.isNaN(dilation) || dilation <= 0.0 ? 1.0 : dilation;
	}

	/** The time dilation factor (wall time per unit of model time). */
	double getDilation()
	{
		return dilation;
	}

	/** Model milliseconds since the origin. */
	double nowMs()
	{
		return (System.nanoTime() - originNanos) / 1.0e6 / dilation;
	}

	/** Wall-clock nanoTime at which model time reaches {@code modelMs}. */
	long wallNanosAt(double modelMs)
	{
		return originNanos + (long)Math.floor(modelMs * dilation * 1.0e6);
	}

	/** Model milliseconds of a wall-clock nanoTime. */
	double modelMsAt(long wallNanos)
	{
		return (wallNanos - originNanos) / 1.0e6 / dilation;
	}

	/** Model milliseconds of a wall-clock epoch time (System.currentTimeMillis). */
	double modelMsAtEpochMillis(long epochMillis)
	{
		long ageNanos = (System.currentTimeMillis() - epochMillis) * 1000000L;
		return modelMsAt(System.nanoTime() - Math.max(0L, ageNanos));
	}

	/** Wall-clock nanoseconds that emulate {@code modelMs} of model time. */
	long wallNanosFor(double modelMs)
	{
		return (long)Math.floor(Math.max(0.0, modelMs) * dilation * 1.0e6);
	}

	long getOriginNanos()
	{
		return originNanos;
	}
}
