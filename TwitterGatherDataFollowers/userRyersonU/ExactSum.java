package TwitterGatherDataFollowers.userRyersonU;

import java.io.Serializable;
import java.util.Arrays;

/**
 * Exact, order-independent sum of doubles.
 *
 * Floating-point addition is not associative, so a sum computed as partial
 * sums on several nodes and then combined differs, in the last bits, from the
 * same sum computed on one node, and differs again for every other way the
 * terms happen to be split. That makes any distributed aggregate depend on the
 * data partition. This accumulator removes the dependence: every finite double
 * is an integer multiple of 2^-1074, so the exact sum of any number of them is
 * an integer multiple of 2^-1074 as well. It is kept exactly, as a fixed-point
 * integer held in 32-bit limbs, and rounded to the nearest double (ties to
 * even) only when {@link #toDouble()} is called. The result is the correctly
 * rounded exact sum, so it is identical for every order and every grouping of
 * the terms.
 *
 * Only the limbs a value actually touches are stored, so a sum of values with
 * similar magnitudes costs a handful of longs. Carries are deferred and
 * resolved when the value is read.
 *
 * Representation invariant after {@link #normalize()}: every limb except the
 * last lies in [0, 2^32) and the last limb is signed, in [-2^31, 2^31). The
 * value is sum(limbs[k] * 2^(32 * (lowLimb + k))) * 2^-1074.
 */
final class ExactSum implements Serializable, PayloadSize.Sized
{
	private static final long serialVersionUID = 1L;

	private static final long MASK32 = 0xFFFFFFFFL;
	private static final long SIGNED_TOP_LIMIT = 1L << 31;
	/** Bit index 0 is worth 2^-1074, the smallest subnormal double. */
	private static final int BIT_OFFSET = 1074;
	/** Deferred carries stay far below the long range for this many adds. */
	private static final int NORMALIZE_INTERVAL = 1 << 28;

	/** limbs[k] is limb number (lowLimb + k); each limb carries 32 bits. */
	private long[] limbs;
	private int lowLimb;
	private int pendingAdds;
	private boolean positiveInfinity;
	private boolean negativeInfinity;
	private boolean notANumber;

	ExactSum()
	{
	}

	/** Wire size: the limb offset and flags, then each 32-bit limb. */
	public long payloadBytes()
	{
		return 8L + 4L * (limbs == null ? 0 : limbs.length);
	}

	ExactSum(double initial)
	{
		add(initial);
	}

	/** Adds one double exactly. */
	void add(double value)
	{
		if (value == 0.0)
		{
			return;
		}
		if (Double.isNaN(value))
		{
			notANumber = true;
			return;
		}
		if (Double.isInfinite(value))
		{
			if (value > 0.0)
			{
				positiveInfinity = true;
			}
			else
			{
				negativeInfinity = true;
			}
			return;
		}
		long bits = Double.doubleToRawLongBits(value);
		boolean negative = bits < 0L;
		int exponentField = (int)((bits >>> 52) & 0x7FFL);
		long mantissa = bits & 0x000FFFFFFFFFFFFFL;
		int bitIndex;
		if (exponentField == 0)
		{
			bitIndex = 0;
		}
		else
		{
			mantissa |= 1L << 52;
			bitIndex = exponentField - 1;
		}
		//value = mantissa * 2^(bitIndex - 1074) with mantissa < 2^53, so it
		//spans at most three 32-bit limbs starting at bitIndex / 32.
		int limb = bitIndex >>> 5;
		int shift = bitIndex & 31;
		long chunk0 = (mantissa & (MASK32 >>> shift)) << shift;
		long rest = mantissa >>> (32 - shift);
		long chunk1 = rest & MASK32;
		long chunk2 = rest >>> 32;
		ensureRange(limb, limb + 2);
		int index = limb - lowLimb;
		if (negative)
		{
			limbs[index] -= chunk0;
			limbs[index + 1] -= chunk1;
			limbs[index + 2] -= chunk2;
		}
		else
		{
			limbs[index] += chunk0;
			limbs[index + 1] += chunk1;
			limbs[index + 2] += chunk2;
		}
		notePendingAdd();
	}

	/** Adds another exact sum exactly; {@code other} is not changed in value. */
	void add(ExactSum other)
	{
		if (other == null)
		{
			return;
		}
		if (other == this)
		{
			add(copy());
			return;
		}
		positiveInfinity |= other.positiveInfinity;
		negativeInfinity |= other.negativeInfinity;
		notANumber |= other.notANumber;
		if (other.limbs == null)
		{
			return;
		}
		other.normalize();
		ensureRange(other.lowLimb, other.lowLimb + other.limbs.length - 1);
		int offset = other.lowLimb - lowLimb;
		for (int index = 0; index < other.limbs.length; index++)
		{
			limbs[offset + index] += other.limbs[index];
		}
		notePendingAdd();
	}

	ExactSum copy()
	{
		ExactSum copy = new ExactSum();
		copy.lowLimb = lowLimb;
		copy.limbs = limbs == null ? null : Arrays.copyOf(limbs, limbs.length);
		copy.pendingAdds = pendingAdds;
		copy.positiveInfinity = positiveInfinity;
		copy.negativeInfinity = negativeInfinity;
		copy.notANumber = notANumber;
		return copy;
	}

	/** True when the exact value is zero (and no infinity or NaN was added). */
	boolean isZero()
	{
		return signum() == 0
				&& !notANumber && !positiveInfinity && !negativeInfinity;
	}

	/** Sign of the exact value: -1, 0 or 1. NaN and inf-inf report 0. */
	int signum()
	{
		if (notANumber || (positiveInfinity && negativeInfinity))
		{
			return 0;
		}
		if (positiveInfinity)
		{
			return 1;
		}
		if (negativeInfinity)
		{
			return -1;
		}
		if (limbs == null)
		{
			return 0;
		}
		normalize();
		for (int index = limbs.length - 1; index >= 0; index--)
		{
			if (limbs[index] != 0L)
			{
				return limbs[index] < 0L ? -1 : 1;
			}
		}
		return 0;
	}

	/** The exact sum rounded once to the nearest double, ties to even. */
	double toDouble()
	{
		if (notANumber || (positiveInfinity && negativeInfinity))
		{
			return Double.NaN;
		}
		if (positiveInfinity)
		{
			return Double.POSITIVE_INFINITY;
		}
		if (negativeInfinity)
		{
			return Double.NEGATIVE_INFINITY;
		}
		int sign = signum();
		if (sign == 0)
		{
			return 0.0;
		}
		long[] magnitude = Arrays.copyOf(limbs, limbs.length);
		if (sign < 0)
		{
			for (int index = 0; index < magnitude.length; index++)
			{
				magnitude[index] = -magnitude[index];
			}
		}
		propagateMagnitude(magnitude);
		int highest = highestSetBit(magnitude);
		int localOfGlobalZero = -lowLimb * 32;
		int globalHighest = highest - localOfGlobalZero;
		double result;
		if (globalHighest < 53)
		{
			//Below 2^-1021 every multiple of 2^-1074 is exactly representable
			//(subnormals and the lowest normal binade), so nothing is rounded.
			long value = extractBits(magnitude, localOfGlobalZero, globalHighest + 1);
			result = Math.scalb((double)value, -BIT_OFFSET);
		}
		else
		{
			int lowestKept = globalHighest - 52;
			int localLowestKept = lowestKept + localOfGlobalZero;
			long mantissa = extractBits(magnitude, localLowestKept, 53);
			boolean roundBit = bitAt(magnitude, localLowestKept - 1);
			boolean sticky = anyBitBelow(magnitude, localLowestKept - 1);
			if (roundBit && (sticky || (mantissa & 1L) != 0L))
			{
				mantissa++;
				if (mantissa == (1L << 53))
				{
					mantissa >>>= 1;
					lowestKept++;
				}
			}
			result = Math.scalb((double)mantissa, lowestKept - BIT_OFFSET);
		}
		return sign < 0 ? -result : result;
	}

	private void notePendingAdd()
	{
		pendingAdds++;
		if (pendingAdds >= NORMALIZE_INTERVAL)
		{
			normalize();
		}
	}

	/**
	 * Resolves deferred carries so every limb but the last is in [0, 2^32) and
	 * the last, signed limb is in [-2^31, 2^31). Growing the window by at most
	 * two limbs is always enough because no limb can exceed about 2^61 in
	 * magnitude between normalizations.
	 */
	private void normalize()
	{
		if (limbs == null)
		{
			pendingAdds = 0;
			return;
		}
		long carry = 0L;
		int index = 0;
		while (true)
		{
			long value = limbs[index] + carry;
			if (index == limbs.length - 1)
			{
				if (value >= -SIGNED_TOP_LIMIT && value < SIGNED_TOP_LIMIT)
				{
					limbs[index] = value;
					break;
				}
				carry = value >> 32;
				limbs[index] = value & MASK32;
				limbs = Arrays.copyOf(limbs, limbs.length + 1);
				index++;
				continue;
			}
			carry = value >> 32;
			limbs[index] = value & MASK32;
			index++;
		}
		pendingAdds = 0;
	}

	/** Carry propagation for a non-negative value; all limbs end in [0, 2^32). */
	private static void propagateMagnitude(long[] values)
	{
		long carry = 0L;
		for (int index = 0; index < values.length; index++)
		{
			long value = values[index] + carry;
			carry = value >> 32;
			values[index] = value & MASK32;
		}
		if (carry != 0L)
		{
			throw new IllegalStateException(
					"Exact sum magnitude did not fit its limb window.");
		}
	}

	private void ensureRange(int firstLimb, int lastLimb)
	{
		if (limbs == null)
		{
			lowLimb = firstLimb;
			limbs = new long[lastLimb - firstLimb + 2];
			return;
		}
		int currentHigh = lowLimb + limbs.length - 1;
		if (firstLimb >= lowLimb && lastLimb <= currentHigh)
		{
			return;
		}
		int newLow = Math.min(lowLimb, firstLimb);
		int newHigh = Math.max(currentHigh, lastLimb + 1);
		long[] grown = new long[newHigh - newLow + 1];
		System.arraycopy(limbs, 0, grown, lowLimb - newLow, limbs.length);
		limbs = grown;
		lowLimb = newLow;
	}

	private static int highestSetBit(long[] values)
	{
		for (int index = values.length - 1; index >= 0; index--)
		{
			if (values[index] != 0L)
			{
				return index * 32 + (63 - Long.numberOfLeadingZeros(values[index]));
			}
		}
		return -1;
	}

	private static boolean bitAt(long[] values, int bit)
	{
		if (bit < 0)
		{
			return false;
		}
		int index = bit >>> 5;
		if (index >= values.length)
		{
			return false;
		}
		return ((values[index] >>> (bit & 31)) & 1L) != 0L;
	}

	/** True when any bit strictly below local bit {@code bit} is set. */
	private static boolean anyBitBelow(long[] values, int bit)
	{
		if (bit <= 0)
		{
			return false;
		}
		int index = Math.min(bit >>> 5, values.length);
		for (int lower = 0; lower < index; lower++)
		{
			if (values[lower] != 0L)
			{
				return true;
			}
		}
		if (index < values.length)
		{
			long mask = (1L << (bit & 31)) - 1L;
			return (values[index] & mask) != 0L;
		}
		return false;
	}

	/** Reads {@code count} (<= 63) bits starting at local bit {@code start}. */
	private static long extractBits(long[] values, int start, int count)
	{
		long result = 0L;
		for (int offset = count - 1; offset >= 0; offset--)
		{
			result = (result << 1) | (bitAt(values, start + offset) ? 1L : 0L);
		}
		return result;
	}

	public String toString()
	{
		return Double.toString(toDouble());
	}
}
