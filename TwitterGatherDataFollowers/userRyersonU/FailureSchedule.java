package TwitterGatherDataFollowers.userRyersonU;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The crash-recovery process of one node: alternating up-times and repairs,
 * drawn from a seeded generator so a fault-injection run is reproducible.
 *
 * Up-times are exponential with mean MTBF (memoryless crashes) or Weibull with
 * shape k and mean MTBF (k &lt; 1: infant mortality, k &gt; 1: wear-out);
 * repairs last exactly MTTR or are exponential with mean MTTR. Times are model
 * milliseconds from the start of the run. The same (seed, node) always yields
 * the same outages, in the live emulator and in the simulator alike.
 */
final class FailureSchedule
{
	/** One crash and its repair. */
	static final class Outage
	{
		private final int node;
		private final double crashAtMs;
		private final double recoverAtMs;

		Outage(int node, double crashAtMs, double recoverAtMs)
		{
			this.node = node;
			this.crashAtMs = crashAtMs;
			this.recoverAtMs = Math.max(crashAtMs, recoverAtMs);
		}

		int getNode()
		{
			return node;
		}

		double getCrashAtMs()
		{
			return crashAtMs;
		}

		double getRecoverAtMs()
		{
			return recoverAtMs;
		}

		double getDowntimeMs()
		{
			return recoverAtMs - crashAtMs;
		}
	}

	private final int node;
	private final double mtbfMs;
	private final double mttrMs;
	private final ClusterSettings.UptimeDistribution uptime;
	private final double shape;
	private final ClusterSettings.RepairDistribution repair;
	private final SplitMix random;
	private final ArrayList<Outage> generated = new ArrayList<Outage>();
	private double cursorMs;

	FailureSchedule(int node, double mtbfMs, double mttrMs, ClusterSettings settings, long seed)
	{
		ClusterSettings safe = settings == null ? ClusterSettings.defaults() : settings;
		this.node = node;
		this.mtbfMs = Math.max(0.0, mtbfMs);
		this.mttrMs = Math.max(0.0, mttrMs);
		this.uptime = safe.getUptimeDistribution();
		this.shape = safe.getWeibullShape();
		this.repair = safe.getRepairDistribution();
		this.random = new SplitMix(WorkDispatcher.mix(seed ^ (0x9E3779B97F4A7C15L * (node + 1L))));
	}

	/** The schedules of every node that has a crash process. */
	static List<FailureSchedule> forNodes(List<NodeSpec> specs, ClusterSettings settings, long seed)
	{
		ArrayList<FailureSchedule> schedules = new ArrayList<FailureSchedule>();
		if (specs != null)
		{
			for (NodeSpec spec : specs)
			{
				if (spec.hasCrashProcess())
				{
					schedules.add(new FailureSchedule(spec.getIndex(), spec.getMtbfMs(),
							spec.getMttrMs(), settings, seed));
				}
			}
		}
		return schedules;
	}

	int getNode()
	{
		return node;
	}

	boolean isActive()
	{
		return mtbfMs > 0.0;
	}

	/** Every outage whose crash happens before {@code horizonMs}, in order. */
	synchronized List<Outage> outagesBefore(double horizonMs)
	{
		if (!isActive())
		{
			return Collections.emptyList();
		}
		while (cursorMs < horizonMs && generated.size() < 1000000)
		{
			double up = sampleUptime();
			double crash = cursorMs + up;
			double down = sampleRepair();
			generated.add(new Outage(node, crash, crash + down));
			cursorMs = crash + down;
		}
		ArrayList<Outage> result = new ArrayList<Outage>();
		for (Outage outage : generated)
		{
			if (outage.crashAtMs < horizonMs)
			{
				result.add(outage);
			}
		}
		return result;
	}

	/** The first outage whose crash is at or after {@code timeMs}, or null. */
	synchronized Outage nextAfter(double timeMs)
	{
		if (!isActive())
		{
			return null;
		}
		double horizon = Math.max(timeMs, cursorMs) + 10.0 * mtbfMs + 1.0;
		for (Outage outage : outagesBefore(horizon))
		{
			if (outage.crashAtMs >= timeMs)
			{
				return outage;
			}
		}
		return null;
	}

	private double sampleUptime()
	{
		double u = random.nextOpenUnit();
		double exponential = -Math.log(u);
		if (uptime == ClusterSettings.UptimeDistribution.WEIBULL)
		{
			double scale = mtbfMs / gamma(1.0 + 1.0 / shape);
			return scale * Math.pow(exponential, 1.0 / shape);
		}
		return mtbfMs * exponential;
	}

	private double sampleRepair()
	{
		if (repair == ClusterSettings.RepairDistribution.EXPONENTIAL)
		{
			return mttrMs * -Math.log(random.nextOpenUnit());
		}
		return mttrMs;
	}

	/** Lanczos approximation of the gamma function (relative error below 1e-13). */
	static double gamma(double x)
	{
		if (x < 0.5)
		{
			return Math.PI / (Math.sin(Math.PI * x) * gamma(1.0 - x));
		}
		double[] g = {
			0.99999999999980993, 676.5203681218851, -1259.1392167224028,
			771.32342877765313, -176.61502916214059, 12.507343278686905,
			-0.13857109526572012, 9.9843695780195716e-6, 1.5056327351493116e-7
		};
		double z = x - 1.0;
		double a = g[0];
		double t = z + 7.5;
		for (int index = 1; index < g.length; index++)
		{
			a += g[index] / (z + index);
		}
		return Math.sqrt(2.0 * Math.PI) * Math.pow(t, z + 0.5) * Math.exp(-t) * a;
	}

	/** SplitMix64: a small, fully specified generator, identical on every JVM. */
	static final class SplitMix
	{
		private long state;

		SplitMix(long seed)
		{
			this.state = seed;
		}

		long nextLong()
		{
			state += 0x9E3779B97F4A7C15L;
			long z = state;
			z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
			z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
			return z ^ (z >>> 31);
		}

		/** Uniform in [0, 1). */
		double nextUnit()
		{
			return (nextLong() >>> 11) * 0x1.0p-53;
		}

		/** Uniform in (0, 1), never 0 (safe for logarithms). */
		double nextOpenUnit()
		{
			double value;
			do
			{
				value = nextUnit();
			}
			while (value <= 0.0);
			return value;
		}

		/** Standard normal (Box-Muller). */
		double nextGaussian()
		{
			double u1 = nextOpenUnit();
			double u2 = nextUnit();
			return Math.sqrt(-2.0 * Math.log(u1)) * Math.cos(2.0 * Math.PI * u2);
		}
	}
}
