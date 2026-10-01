package TwitterGatherDataFollowers.userRyersonU;

import java.util.List;

/**
 * The star network of the cluster model: every node has one full-duplex link to
 * the coordinator (the hub), with a one-way latency and a bandwidth per
 * direction.
 *
 * A transfer of b bytes over a link that is asked to send at time t starts at
 * max(t, the link's free time), occupies the link for b / bandwidth (so
 * transfers on one link queue behind each other), and arrives one latency after
 * it finished sending; latency overlaps with other transfers, as on a real
 * pipelined link. A node-to-node transfer crosses the sender's up link and then
 * the receiver's down link (store and forward at the hub). This is the
 * alpha-beta (latency-bandwidth) model with per-link FIFO contention.
 *
 * All times are model milliseconds. The same instance serves the dispatcher
 * (unit descriptors and input data), messages and collectives of one run, so
 * every byte of a run competes for the same links. Thread-safe.
 */
final class NetworkModel
{
	/** Index of the coordinator. */
	static final int HUB = 0;
	/** Envelope bytes of one message (headers, identifiers). */
	static final long MESSAGE_HEADER_BYTES = 96L;
	/** Bytes of one work-unit descriptor sent by the coordinator. */
	static final long UNIT_DESCRIPTOR_BYTES = 256L;

	/** One direction of one node's link. */
	static final class Link
	{
		private double latencyMs;
		private double bytesPerMs;
		private double freeAtMs;
		private long bytes;
		private long messages;
		private double busyMs;

		private Link(double latencyMs, double bytesPerMs)
		{
			this.latencyMs = Math.max(0.0, latencyMs);
			this.bytesPerMs = Math.max(0.0, bytesPerMs);
		}

		/** Sends {@code size} bytes asked at {@code at}; returns the arrival time. */
		private double transmit(long size, double at)
		{
			double begin = Math.max(at, freeAtMs);
			double serialization = bytesPerMs <= 0.0 || size <= 0L ? 0.0 : size / bytesPerMs;
			freeAtMs = begin + serialization;
			busyMs += serialization;
			bytes += Math.max(0L, size);
			messages++;
			return freeAtMs + latencyMs;
		}

		long getBytes()
		{
			return bytes;
		}

		long getMessages()
		{
			return messages;
		}

		double getBusyMs()
		{
			return busyMs;
		}
	}

	private final Link[] up;
	private final Link[] down;
	private final boolean free;

	/**
	 * @param specs nodes 1..N in order
	 * @param free  true for the unemulated model: every transfer is instantaneous
	 */
	NetworkModel(List<NodeSpec> specs, boolean free)
	{
		int count = specs == null ? 0 : specs.size();
		up = new Link[count + 1];
		down = new Link[count + 1];
		this.free = free;
		for (int node = 1; node <= count; node++)
		{
			NodeSpec spec = specs.get(node - 1);
			up[node] = new Link(free ? 0.0 : spec.getDelayMs(),
					free ? 0.0 : spec.getBandwidthBytesPerMs());
			down[node] = new Link(free ? 0.0 : spec.getDelayMs(),
					free ? 0.0 : spec.getBandwidthBytesPerMs());
		}
	}

	/** A network in which every transfer is free (tests, the unemulated mode). */
	static NetworkModel free(int nodeCount)
	{
		return new NetworkModel(NodeSpec.uniform(nodeCount), true);
	}

	int nodeCount()
	{
		return up.length - 1;
	}

	boolean isFree()
	{
		return free;
	}

	/**
	 * Sends {@code bytes} from {@code from} to {@code to} (0 is the hub),
	 * asked at model time {@code nowMs}; returns the arrival time.
	 */
	synchronized double send(int from, int to, long bytes, double nowMs)
	{
		double now = Double.isNaN(nowMs) ? 0.0 : nowMs;
		if (from == to || free)
		{
			return now;
		}
		double at = now;
		if (from != HUB)
		{
			at = link(up, from).transmit(bytes, at);
		}
		if (to != HUB)
		{
			at = link(down, to).transmit(bytes, at);
		}
		return at;
	}

	/** Time a transfer would arrive if sent now, without occupying the links. */
	synchronized double peek(int from, int to, long bytes, double nowMs)
	{
		if (from == to || free)
		{
			return nowMs;
		}
		double at = nowMs;
		if (from != HUB)
		{
			Link link = link(up, from);
			double begin = Math.max(at, link.freeAtMs);
			at = begin + (link.bytesPerMs <= 0.0 ? 0.0 : bytes / link.bytesPerMs) + link.latencyMs;
		}
		if (to != HUB)
		{
			Link link = link(down, to);
			double begin = Math.max(at, link.freeAtMs);
			at = begin + (link.bytesPerMs <= 0.0 ? 0.0 : bytes / link.bytesPerMs) + link.latencyMs;
		}
		return at;
	}

	/** Applies new link parameters (live changes); traffic already sent keeps its times. */
	synchronized void updateSpecs(List<NodeSpec> specs)
	{
		if (specs == null || free)
		{
			return;
		}
		for (NodeSpec spec : specs)
		{
			int node = spec.getIndex();
			if (node >= 1 && node < up.length)
			{
				up[node].latencyMs = spec.getDelayMs();
				up[node].bytesPerMs = spec.getBandwidthBytesPerMs();
				down[node].latencyMs = spec.getDelayMs();
				down[node].bytesPerMs = spec.getBandwidthBytesPerMs();
			}
		}
	}

	synchronized long bytesUp(int node)
	{
		return link(up, node).getBytes();
	}

	synchronized long bytesDown(int node)
	{
		return link(down, node).getBytes();
	}

	synchronized long messagesUp(int node)
	{
		return link(up, node).getMessages();
	}

	synchronized long messagesDown(int node)
	{
		return link(down, node).getMessages();
	}

	synchronized double busyUpMs(int node)
	{
		return link(up, node).getBusyMs();
	}

	synchronized double busyDownMs(int node)
	{
		return link(down, node).getBusyMs();
	}

	synchronized long totalBytes()
	{
		long total = 0L;
		for (int node = 1; node < up.length; node++)
		{
			total += up[node].getBytes() + down[node].getBytes();
		}
		return total;
	}

	private static Link link(Link[] links, int node)
	{
		if (node < 1 || node >= links.length)
		{
			throw new IllegalArgumentException("Node " + node + " has no link in a network of "
					+ (links.length - 1) + " node(s).");
		}
		return links[node];
	}
}
