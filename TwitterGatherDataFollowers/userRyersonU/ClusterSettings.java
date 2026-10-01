package TwitterGatherDataFollowers.userRyersonU;

import java.util.Locale;

/**
 * Cluster-wide settings of the node model (the per-node settings live in
 * {@link NodeCapacityProfile}).
 *
 * <ul>
 * <li>Coordinator speed: speed, in reference cores, of the coordinator (the hub
 *     of the star network) that runs collective reductions.</li>
 * <li>Time dilation factor (TDF): in a live run every emulated duration is
 *     stretched by the TDF, so a node of speed s runs host work W in
 *     TDF x W / s of wall time and model time is wall time / TDF. The TDF must
 *     be at least the fastest node's speed (a host core cannot run faster than
 *     itself); a larger TDF also lowers the load on the host's cores, which
 *     keeps the emulation faithful when many lanes run at once. 0 selects it
 *     automatically.</li>
 * <li>Boost headroom: when the TDF is automatic, live capacity boosts up to
 *     this factor stay faithful.</li>
 * <li>Detection timeout: how long the coordinator takes to notice a crashed
 *     node (a heartbeat timeout); only then is its work rescheduled.</li>
 * <li>Crash distribution: exponential up-times (memoryless) or Weibull with a
 *     shape parameter; repairs are either fixed (MTTR) or exponential.</li>
 * <li>Seed: base seed of transient faults and crash times; experiment
 *     repetitions derive their own seeds from it.</li>
 * </ul>
 */
final class ClusterSettings
{
	enum UptimeDistribution
	{
		EXPONENTIAL("Exponential (memoryless)"),
		WEIBULL("Weibull");

		private final String label;

		UptimeDistribution(String label)
		{
			this.label = label;
		}

		String getLabel()
		{
			return label;
		}

		public String toString()
		{
			return label;
		}
	}

	enum RepairDistribution
	{
		FIXED("Fixed (MTTR)"),
		EXPONENTIAL("Exponential");

		private final String label;

		RepairDistribution(String label)
		{
			this.label = label;
		}

		String getLabel()
		{
			return label;
		}

		public String toString()
		{
			return label;
		}
	}

	static final long DEFAULT_SEED = 0x44534D50L;

	private final double coordinatorSpeed;
	private final double timeDilation;
	private final double boostHeadroom;
	private final int detectionTimeoutMs;
	private final UptimeDistribution uptimeDistribution;
	private final double weibullShape;
	private final RepairDistribution repairDistribution;
	private final long seed;

	ClusterSettings(
			double coordinatorSpeed,
			double timeDilation,
			double boostHeadroom,
			int detectionTimeoutMs,
			UptimeDistribution uptimeDistribution,
			double weibullShape,
			RepairDistribution repairDistribution,
			long seed)
	{
		this.coordinatorSpeed = clamp(coordinatorSpeed, 0.05, 64.0, 1.0);
		this.timeDilation = Double.isNaN(timeDilation) || timeDilation <= 0.0
				? 0.0 : Math.min(1000.0, Math.max(0.05, timeDilation));
		this.boostHeadroom = clamp(boostHeadroom, 1.0, 16.0, 1.0);
		this.detectionTimeoutMs = Math.max(0, Math.min(600000, detectionTimeoutMs));
		this.uptimeDistribution = uptimeDistribution == null
				? UptimeDistribution.EXPONENTIAL : uptimeDistribution;
		this.weibullShape = clamp(weibullShape, 0.2, 10.0, 1.5);
		this.repairDistribution = repairDistribution == null
				? RepairDistribution.FIXED : repairDistribution;
		this.seed = seed;
	}

	static ClusterSettings defaults()
	{
		return new ClusterSettings(1.0, 0.0, 1.0, 500,
				UptimeDistribution.EXPONENTIAL, 1.5, RepairDistribution.FIXED, DEFAULT_SEED);
	}

	private static double clamp(double value, double minimum, double maximum, double fallback)
	{
		if (Double.isNaN(value) || Double.isInfinite(value))
		{
			return fallback;
		}
		return Math.max(minimum, Math.min(maximum, value));
	}

	double getCoordinatorSpeed()
	{
		return coordinatorSpeed;
	}

	/** The fixed time dilation factor, or 0 for automatic. */
	double getTimeDilation()
	{
		return timeDilation;
	}

	boolean isAutomaticTimeDilation()
	{
		return timeDilation <= 0.0;
	}

	double getBoostHeadroom()
	{
		return boostHeadroom;
	}

	int getDetectionTimeoutMs()
	{
		return detectionTimeoutMs;
	}

	UptimeDistribution getUptimeDistribution()
	{
		return uptimeDistribution;
	}

	double getWeibullShape()
	{
		return weibullShape;
	}

	RepairDistribution getRepairDistribution()
	{
		return repairDistribution;
	}

	long getSeed()
	{
		return seed;
	}

	ClusterSettings withSeed(long value)
	{
		return new ClusterSettings(coordinatorSpeed, timeDilation, boostHeadroom,
				detectionTimeoutMs, uptimeDistribution, weibullShape, repairDistribution, value);
	}

	ClusterSettings withTimeDilation(double value)
	{
		return new ClusterSettings(coordinatorSpeed, value, boostHeadroom,
				detectionTimeoutMs, uptimeDistribution, weibullShape, repairDistribution, seed);
	}

	ClusterSettings withCoordinatorSpeed(double value)
	{
		return new ClusterSettings(value, timeDilation, boostHeadroom,
				detectionTimeoutMs, uptimeDistribution, weibullShape, repairDistribution, seed);
	}

	ClusterSettings withBoostHeadroom(double value)
	{
		return new ClusterSettings(coordinatorSpeed, timeDilation, value,
				detectionTimeoutMs, uptimeDistribution, weibullShape, repairDistribution, seed);
	}

	ClusterSettings withDetectionTimeoutMs(int value)
	{
		return new ClusterSettings(coordinatorSpeed, timeDilation, boostHeadroom,
				value, uptimeDistribution, weibullShape, repairDistribution, seed);
	}

	ClusterSettings withFailureDistributions(
			UptimeDistribution uptime, double shape, RepairDistribution repair)
	{
		return new ClusterSettings(coordinatorSpeed, timeDilation, boostHeadroom,
				detectionTimeoutMs, uptime, shape, repair, seed);
	}

	String describe()
	{
		return String.format(Locale.US,
				"coordinator %.2fx, TDF %s (headroom %.2fx), detection %d ms, up-times %s%s, repairs %s, seed %d",
				coordinatorSpeed,
				isAutomaticTimeDilation() ? "auto" : String.format(Locale.US, "%.2f", timeDilation),
				boostHeadroom,
				detectionTimeoutMs,
				uptimeDistribution.getLabel(),
				uptimeDistribution == UptimeDistribution.WEIBULL
						? String.format(Locale.US, " (k=%.2f)", weibullShape) : "",
				repairDistribution.getLabel(),
				seed);
	}
}
