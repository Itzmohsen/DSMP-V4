package TwitterGatherDataFollowers.userRyersonU;

import java.util.Locale;

/**
 * Plans exact-SVM retained-corpus execution for the shared JADE JVM.
 *
 * When all node maps fit together, the existing in-memory path remains active.
 * When only one node map fits, nodes use disk-backed execution waves. A run is
 * rejected only when its largest individual shard cannot fit safely.
 */
final class ExactSvmCorpusAdmission
{
	private static final long MEBIBYTE = RecommendationMemoryCoordinator.MEBIBYTE;
	private static final long BASE_RUNTIME_BYTES = 384L * MEBIBYTE;
	private static final long PER_NODE_RUNTIME_BYTES = 24L * MEBIBYTE;
	private static final long PER_USER_RUNTIME_BYTES = 8L * 1024L;
	private static final long PEAK_BYTES_PER_SPARSE_TERM = 240L;
	private static final long SAFE_HEAP_PERCENT = 78L;
	private static final long HEAP_RECOMMENDATION_STEP_MB = 512L;

	private ExactSvmCorpusAdmission()
	{
	}

	static Profile profile(
			long retainedSparseTermEntries,
			int retainedUsers,
			int requestedNodes)
	{
		return profile(
				retainedSparseTermEntries,
				retainedUsers,
				requestedNodes,
				Runtime.getRuntime().maxMemory());
	}

	static Profile profile(
			long retainedSparseTermEntries,
			int retainedUsers,
			int requestedNodes,
			long maximumHeapBytes)
	{
		long sparseEntries = Math.max(0L, retainedSparseTermEntries);
		int users = Math.max(0, retainedUsers);
		int nodes = Math.max(1, requestedNodes);
		long maxHeap = Math.max(MEBIBYTE, maximumHeapBytes);

		long estimatedPeak = BASE_RUNTIME_BYTES;
		estimatedPeak = saturatingAdd(
				estimatedPeak,
				saturatingMultiply(nodes, PER_NODE_RUNTIME_BYTES));
		estimatedPeak = saturatingAdd(
				estimatedPeak,
				saturatingMultiply(users, PER_USER_RUNTIME_BYTES));
		estimatedPeak = saturatingAdd(
				estimatedPeak,
				saturatingMultiply(sparseEntries, PEAK_BYTES_PER_SPARSE_TERM));

		long proportionalSafeBudget =
				saturatingMultiply(maxHeap, SAFE_HEAP_PERCENT) / 100L;
		long safeBudget = Math.min(
				maxHeap,
				Math.max(256L * MEBIBYTE, proportionalSafeBudget));
		long requiredHeapBytes = divideRoundUp(
				saturatingMultiply(estimatedPeak, 100L),
				SAFE_HEAP_PERCENT);
		long requiredHeapMb = divideRoundUp(requiredHeapBytes, MEBIBYTE);
		requiredHeapMb = alignUp(
				requiredHeapMb, HEAP_RECOMMENDATION_STEP_MB);

		return new Profile(
				sparseEntries,
				users,
				nodes,
				estimatedPeak,
				safeBudget,
				requiredHeapMb);
	}

	static void validate(
			long retainedSparseTermEntries,
			int retainedUsers,
			int requestedNodes)
	{
		profile(retainedSparseTermEntries, retainedUsers, requestedNodes)
				.validate();
	}

	static WavePlan wavePlan(
			long totalRetainedSparseTermEntries,
			long largestNodeSparseTermEntries,
			int retainedUsers,
			int requestedNodes)
	{
		long maximumHeapBytes = Runtime.getRuntime().maxMemory();
		return wavePlan(
				totalRetainedSparseTermEntries,
				largestNodeSparseTermEntries,
				retainedUsers,
				requestedNodes,
				maximumHeapBytes);
	}

	static WavePlan wavePlan(
			long totalRetainedSparseTermEntries,
			long largestNodeSparseTermEntries,
			int retainedUsers,
			int requestedNodes,
			long maximumHeapBytes)
	{
		Profile concurrentProfile = profile(
				totalRetainedSparseTermEntries,
				retainedUsers,
				requestedNodes,
				maximumHeapBytes);
		Profile largestNodeProfile = profile(
				largestNodeSparseTermEntries,
				retainedUsers,
				1,
				maximumHeapBytes);
		return new WavePlan(concurrentProfile, largestNodeProfile);
	}

	private static long saturatingAdd(long first, long second)
	{
		return first > Long.MAX_VALUE - second
				? Long.MAX_VALUE : first + second;
	}

	private static long saturatingMultiply(long first, long second)
	{
		if (first <= 0L || second <= 0L)
		{
			return 0L;
		}
		return first > Long.MAX_VALUE / second
				? Long.MAX_VALUE : first * second;
	}

	private static long divideRoundUp(long value, long divisor)
	{
		long quotient = value / divisor;
		return value % divisor == 0L
				? quotient
				: saturatingAdd(quotient, 1L);
	}

	private static long alignUp(long value, long step)
	{
		if (value == Long.MAX_VALUE)
		{
			return Long.MAX_VALUE;
		}
		long remainder = value % step;
		if (remainder == 0L)
		{
			return value;
		}
		return value > Long.MAX_VALUE - (step - remainder)
				? Long.MAX_VALUE : value + step - remainder;
	}

	static final class Profile
	{
		private final long retainedSparseTermEntries;
		private final int retainedUsers;
		private final int requestedNodes;
		private final long estimatedPeakBytes;
		private final long safeBudgetBytes;
		private final long requiredHeapMb;

		private Profile(
				long retainedSparseTermEntries,
				int retainedUsers,
				int requestedNodes,
				long estimatedPeakBytes,
				long safeBudgetBytes,
				long requiredHeapMb)
		{
			this.retainedSparseTermEntries = retainedSparseTermEntries;
			this.retainedUsers = retainedUsers;
			this.requestedNodes = requestedNodes;
			this.estimatedPeakBytes = estimatedPeakBytes;
			this.safeBudgetBytes = safeBudgetBytes;
			this.requiredHeapMb = requiredHeapMb;
		}

		boolean fits()
		{
			return estimatedPeakBytes <= safeBudgetBytes;
		}

		long getEstimatedPeakMb()
		{
			return divideRoundUp(estimatedPeakBytes, MEBIBYTE);
		}

		long getEstimatedPeakBytes()
		{
			return estimatedPeakBytes;
		}

		long getSafeBudgetMb()
		{
			return divideRoundUp(safeBudgetBytes, MEBIBYTE);
		}

		long getRequiredHeapMb()
		{
			return requiredHeapMb;
		}

		long getRetainedSparseTermEntries()
		{
			return retainedSparseTermEntries;
		}

		void validate()
		{
			if (fits())
			{
				return;
			}
			throw new IllegalArgumentException(
					"Exact SVM was stopped before agent creation because this text "
					+ "corpus cannot be retained safely in the current shared Java heap. "
					+ "The preflight found about "
					+ String.format(Locale.US, "%,d", retainedSparseTermEntries)
					+ " per-user term entries across " + requestedNodes
					+ " recommender node(s) and estimates a peak of "
					+ RuntimeResourceSettings.formatMegabytes(getEstimatedPeakMb())
					+ ", while the safe budget is "
					+ RuntimeResourceSettings.formatMegabytes(getSafeBudgetMb())
					+ ". Execution waves cannot reduce a single retained group of this "
					+ "size because its documents must remain available together while "
					+ "Exact SVM builds TF-IDF and ARFF inputs. Set Maximum Java heap to at least "
					+ RuntimeResourceSettings.formatMegabytes(requiredHeapMb)
					+ " and restart, use a smaller corpus, or choose Scalable Linear SVM. "
					+ "No agents were started and no partial recommendations were accepted.");
		}

		public String toString()
		{
			return "ExactSvmCorpusAdmission.Profile{entries="
					+ retainedSparseTermEntries
					+ ", users=" + retainedUsers
					+ ", nodes=" + requestedNodes
					+ ", peakMb=" + getEstimatedPeakMb()
					+ ", safeBudgetMb=" + getSafeBudgetMb()
					+ ", requiredHeapMb=" + requiredHeapMb + "}";
		}
	}

	static final class WavePlan
	{
		private final Profile concurrentProfile;
		private final Profile largestNodeProfile;

		private WavePlan(Profile concurrentProfile, Profile largestNodeProfile)
		{
			this.concurrentProfile = concurrentProfile;
			this.largestNodeProfile = largestNodeProfile;
		}

		boolean requiresDiskBackedWaves()
		{
			return largestNodeProfile.fits() && !concurrentProfile.fits();
		}

		void validate()
		{
			if (largestNodeProfile.fits())
			{
				return;
			}
			throw new IllegalArgumentException(
					"Exact SVM was stopped before agent creation because its largest "
					+ "recommender-node shard cannot fit safely in the current Java heap, "
					+ "even when nodes execute one at a time. The largest node retains "
					+ String.format(
							Locale.US,
							"about %,d per-user term entries",
							largestNodeProfile.getRetainedSparseTermEntries())
					+ " and needs a Java heap of at least "
					+ RuntimeResourceSettings.formatMegabytes(
							largestNodeProfile.getRequiredHeapMb())
					+ ". Increase Maximum Java heap and restart, request more recommender "
					+ "nodes so each shard is smaller, use a smaller corpus, or choose "
					+ "Scalable Linear SVM. No agents were started and no partial "
					+ "recommendations were accepted.");
		}

		public String toString()
		{
			return "ExactSvmCorpusAdmission.WavePlan{concurrent="
					+ concurrentProfile
					+ ", largestNode=" + largestNodeProfile
					+ ", diskBackedWaves=" + requiresDiskBackedWaves() + "}";
		}
	}
}
