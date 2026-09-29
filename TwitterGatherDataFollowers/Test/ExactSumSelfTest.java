package TwitterGatherDataFollowers.userRyersonU;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Checks ExactSum against BigDecimal, which is exact, and whose doubleValue()
 * is correctly rounded. Also checks the property the distributed algorithms
 * rely on: the rounded result is identical for every order and every way of
 * splitting the terms into partial sums.
 */
public final class ExactSumSelfTest
{
	private ExactSumSelfTest()
	{
	}

	public static void main(String[] args)
	{
		Random random = new Random(20260928L);
		int checked = 0;
		for (int trial = 0; trial < 4000; trial++)
		{
			List<Double> values = randomValues(random, trial);
			checkAgainstOracle(values);
			checkPartitionIndependence(values, random);
			checked++;
		}
		checkSpecials();
		checkKnownCases();
		checkLongRuns(random);
		System.out.println("ExactSumSelfTest passed (" + checked + " randomized sums)");
	}

	private static List<Double> randomValues(Random random, int trial)
	{
		int count = 1 + random.nextInt(trial % 7 == 0 ? 400 : 40);
		ArrayList<Double> values = new ArrayList<Double>();
		int style = trial % 6;
		for (int index = 0; index < count; index++)
		{
			double value;
			if (style == 0)
			{
				//Unit-normalised TF-IDF style values.
				value = random.nextDouble();
			}
			else if (style == 1)
			{
				//Full exponent range, both signs.
				long bits = random.nextLong();
				value = Double.longBitsToDouble(bits);
				if (Double.isNaN(value) || Double.isInfinite(value))
				{
					value = random.nextGaussian();
				}
			}
			else if (style == 2)
			{
				//Subnormals and tiny normals.
				value = Double.longBitsToDouble(random.nextLong() & 0x001FFFFFFFFFFFFFL)
						* (random.nextBoolean() ? 1.0 : -1.0);
			}
			else if (style == 3)
			{
				//Heavy cancellation around a large magnitude.
				double base = Math.scalb(1.0 + random.nextDouble(), random.nextInt(200) - 100);
				value = random.nextBoolean() ? base : -base * (1.0 + Math.ulp(1.0) * random.nextInt(8));
			}
			else if (style == 4)
			{
				//Gradient-like mixed signs.
				value = random.nextGaussian() * Math.pow(10.0, random.nextInt(12) - 6);
			}
			else
			{
				//Near ties: halves of ulps.
				value = 1.0 + Math.ulp(1.0) * random.nextInt(4) / 2.0;
				if (random.nextBoolean())
				{
					value = Math.scalb(Math.ulp(1.0), -1) * (random.nextBoolean() ? 1 : -1);
				}
			}
			values.add(Double.valueOf(value));
		}
		return values;
	}

	private static void checkAgainstOracle(List<Double> values)
	{
		ExactSum sum = new ExactSum();
		BigDecimal exact = BigDecimal.ZERO;
		for (Double value : values)
		{
			sum.add(value.doubleValue());
			exact = exact.add(new BigDecimal(value.doubleValue()));
		}
		double expected = exact.doubleValue();
		double actual = sum.toDouble();
		if (Double.doubleToLongBits(expected) != Double.doubleToLongBits(actual)
				&& !(expected == 0.0 && actual == 0.0))
		{
			throw new AssertionError("ExactSum mismatch: expected " + expected
					+ " (" + Double.toHexString(expected) + ") but got " + actual
					+ " (" + Double.toHexString(actual) + ") for " + values);
		}
		int expectedSign = exact.signum();
		if (sum.signum() != expectedSign)
		{
			throw new AssertionError("ExactSum sign mismatch for " + values);
		}
	}

	private static void checkPartitionIndependence(List<Double> values, Random random)
	{
		ExactSum reference = new ExactSum();
		for (Double value : values)
		{
			reference.add(value.doubleValue());
		}
		double expected = reference.toDouble();
		for (int round = 0; round < 3; round++)
		{
			ArrayList<Double> shuffled = new ArrayList<Double>(values);
			Collections.shuffle(shuffled, random);
			int parts = 1 + random.nextInt(8);
			ExactSum[] partials = new ExactSum[parts];
			for (int part = 0; part < parts; part++)
			{
				partials[part] = new ExactSum();
			}
			for (Double value : shuffled)
			{
				partials[random.nextInt(parts)].add(value.doubleValue());
			}
			ExactSum combined = new ExactSum();
			for (int part = parts - 1; part >= 0; part--)
			{
				combined.add(partials[part]);
			}
			double actual = combined.toDouble();
			if (Double.doubleToLongBits(expected) != Double.doubleToLongBits(actual)
					&& !(expected == 0.0 && actual == 0.0))
			{
				throw new AssertionError("Partition changed the exact sum: "
						+ expected + " vs " + actual);
			}
		}
	}

	private static void checkSpecials()
	{
		ExactSum sum = new ExactSum();
		sum.add(Double.POSITIVE_INFINITY);
		require(sum.toDouble() == Double.POSITIVE_INFINITY, "+inf");
		sum.add(Double.NEGATIVE_INFINITY);
		require(Double.isNaN(sum.toDouble()), "inf-inf is NaN");
		ExactSum nan = new ExactSum();
		nan.add(1.0);
		nan.add(Double.NaN);
		require(Double.isNaN(nan.toDouble()), "NaN propagates");
		ExactSum zero = new ExactSum();
		zero.add(-0.0);
		zero.add(0.0);
		require(zero.isZero() && zero.toDouble() == 0.0, "zeros");
		ExactSum overflow = new ExactSum();
		overflow.add(Double.MAX_VALUE);
		overflow.add(Double.MAX_VALUE);
		require(overflow.toDouble() == Double.POSITIVE_INFINITY,
				"sum beyond MAX_VALUE rounds to infinity");
		overflow.add(-Double.MAX_VALUE);
		require(overflow.toDouble() == Double.MAX_VALUE,
				"exact intermediate beyond MAX_VALUE is recovered");
	}

	private static void checkKnownCases()
	{
		ExactSum classic = new ExactSum();
		classic.add(1e16);
		classic.add(1.0);
		classic.add(-1e16);
		require(classic.toDouble() == 1.0, "1e16 + 1 - 1e16 == 1 exactly");
		ExactSum tenth = new ExactSum();
		for (int index = 0; index < 10; index++)
		{
			tenth.add(0.1);
		}
		BigDecimal exact = new BigDecimal(0.1).multiply(BigDecimal.TEN);
		require(tenth.toDouble() == exact.doubleValue(), "ten tenths");
		ExactSum smallest = new ExactSum();
		smallest.add(Double.MIN_VALUE);
		smallest.add(Double.MIN_VALUE);
		require(smallest.toDouble() == 2 * Double.MIN_VALUE, "subnormal sum");
		ExactSum self = new ExactSum(3.25);
		self.add(self);
		require(self.toDouble() == 6.5, "adding itself doubles the value");
	}

	private static void checkLongRuns(Random random)
	{
		ExactSum sum = new ExactSum();
		BigDecimal exact = BigDecimal.ZERO;
		for (int index = 0; index < 200000; index++)
		{
			double value = random.nextDouble() - 0.5;
			sum.add(value);
			exact = exact.add(new BigDecimal(value));
		}
		require(Double.doubleToLongBits(sum.toDouble())
				== Double.doubleToLongBits(exact.doubleValue()),
				"long run matches the oracle");
	}

	private static void require(boolean condition, String message)
	{
		if (!condition)
		{
			throw new AssertionError(message);
		}
	}
}
