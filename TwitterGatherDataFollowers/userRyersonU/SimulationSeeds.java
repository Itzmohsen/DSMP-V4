package TwitterGatherDataFollowers.userRyersonU;

import java.util.SplittableRandom;

/**
 * Seeds of the simulation's random draws.
 *
 * A draw seeded by the names it is about gives the same value on every run,
 * every machine and every node split, whatever order the agents happen to
 * run in. {@link String#hashCode()} is fixed by the language specification,
 * and the SplitMix64 finaliser spreads it over all 64 bits.
 */
final class SimulationSeeds
{
	/** Seed of the controller's generated-dataset mode. */
	static final long GENERATION = 0x6D5F1E2A4C3B7089L;

	private SimulationSeeds()
	{
	}

	static long forName(String name)
	{
		return mix(0x9E3779B97F4A7C15L ^ (name == null ? 0 : name.hashCode()));
	}

	static long forPair(String first, String second)
	{
		return mix(forName(first) * 31L + forName(second));
	}

	/**
	 * One Weibull draw with the given shape and scale, by inversion:
	 * {@code scale * (-ln(1 - u))^(1/shape)} for a uniform {@code u}.
	 */
	static double weibull(long seed, double shape, double scale)
	{
		double uniform = new SplittableRandom(seed).nextDouble();
		return scale * Math.pow(-Math.log1p(-uniform), 1.0 / shape);
	}

	private static long mix(long value)
	{
		long z = value + 0x9E3779B97F4A7C15L;
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		return z ^ (z >>> 31);
	}
}
