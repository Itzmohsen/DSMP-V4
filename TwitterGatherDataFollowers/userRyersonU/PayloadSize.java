package TwitterGatherDataFollowers.userRyersonU;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Map;

/**
 * Wire size of the data the nodes exchange, in a compact binary encoding.
 *
 * All nodes of a live run share one JVM, so collectives hand objects over by
 * reference; the network model still has to charge what a distributed system
 * would send. This class defines that size once:
 *
 * <pre>
 * String            2 + UTF-8 length (as DataOutput.writeUTF)
 * double, long      8        int, float  4        boolean, byte  1
 * double[]/long[]   4 + 8n   int[]       4 + 4n   byte[]         4 + n
 * Map               4 + sum of key and value sizes
 * Collection        4 + sum of element sizes
 * null              1
 * Sized             its own payloadBytes()
 * other             16 (an opaque reference; the object is not sent)
 * </pre>
 */
final class PayloadSize
{
	/** An object that knows its own wire size. */
	interface Sized
	{
		long payloadBytes();
	}

	/** CPU the current thread spent sizing payloads, to exclude from emulated work. */
	private static final ThreadLocal<long[]> EXCLUDED_CPU = new ThreadLocal<long[]>()
	{
		protected long[] initialValue()
		{
			return new long[1];
		}
	};

	private PayloadSize()
	{
	}

	/**
	 * Records CPU nanoseconds the current thread spent computing wire sizes
	 * inside node or coordinator work; that time is emulation bookkeeping, not
	 * work of the modeled system, and {@link #takeExcludedCpu()} removes it.
	 */
	static void excludeCpu(long nanos)
	{
		EXCLUDED_CPU.get()[0] += Math.max(0L, nanos);
	}

	/** Returns and clears the current thread's excluded sizing CPU. */
	static long takeExcludedCpu()
	{
		long[] slot = EXCLUDED_CPU.get();
		long value = slot[0];
		slot[0] = 0L;
		return value;
	}

	static long of(Object value)
	{
		if (value == null)
		{
			return 1L;
		}
		if (value instanceof Sized)
		{
			return Math.max(0L, ((Sized)value).payloadBytes());
		}
		if (value instanceof String)
		{
			return string((String)value);
		}
		if (value instanceof Double || value instanceof Long)
		{
			return 8L;
		}
		if (value instanceof Integer || value instanceof Float)
		{
			return 4L;
		}
		if (value instanceof Boolean || value instanceof Byte)
		{
			return 1L;
		}
		if (value instanceof Short || value instanceof Character)
		{
			return 2L;
		}
		if (value instanceof double[])
		{
			return 4L + 8L * ((double[])value).length;
		}
		if (value instanceof long[])
		{
			return 4L + 8L * ((long[])value).length;
		}
		if (value instanceof int[])
		{
			return 4L + 4L * ((int[])value).length;
		}
		if (value instanceof byte[])
		{
			return 4L + ((byte[])value).length;
		}
		if (value instanceof Object[])
		{
			long total = 4L;
			for (Object element : (Object[])value)
			{
				total += of(element);
			}
			return total;
		}
		if (value instanceof Map)
		{
			long total = 4L;
			for (Map.Entry<?,?> entry : ((Map<?,?>)value).entrySet())
			{
				total += of(entry.getKey()) + of(entry.getValue());
			}
			return total;
		}
		if (value instanceof Collection)
		{
			long total = 4L;
			for (Object element : (Collection<?>)value)
			{
				total += of(element);
			}
			return total;
		}
		return 16L;
	}

	/** Size of a sparse vector: its term strings and double weights. */
	static long sparseVector(Map<String,Double> vector)
	{
		if (vector == null)
		{
			return 1L;
		}
		long total = 4L;
		for (Map.Entry<String,Double> entry : vector.entrySet())
		{
			total += string(entry.getKey()) + 8L;
		}
		return total;
	}

	static long string(String value)
	{
		if (value == null)
		{
			return 1L;
		}
		int length = value.length();
		boolean ascii = true;
		for (int index = 0; index < length; index++)
		{
			if (value.charAt(index) >= 0x80)
			{
				ascii = false;
				break;
			}
		}
		return 2L + (ascii ? length : value.getBytes(StandardCharsets.UTF_8).length);
	}
}
