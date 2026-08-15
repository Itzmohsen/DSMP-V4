package TwitterGatherDataFollowers.userRyersonU;

public final class ExactSvmCorpusAdmissionSelfTest
{
	private static final long MEBIBYTE = RecommendationMemoryCoordinator.MEBIBYTE;

	private ExactSvmCorpusAdmissionSelfTest()
	{
	}

	public static void main(String[] args)
	{
		acceptsOrdinarySparseCorpus();
		rejectsExtremeRetainedCorpusBeforeAgentCreation();
		admitsExtremeCorpusWhenEachNodeFitsInWaves();
		rejectsExtremeCorpusWhenOneNodeCannotFit();
		requiredHeapAcceptsTheSameCorpus();
		System.out.println("ExactSvmCorpusAdmissionSelfTest passed");
	}

	private static void acceptsOrdinarySparseCorpus()
	{
		ExactSvmCorpusAdmission.Profile profile =
				ExactSvmCorpusAdmission.profile(
						750000L, 620, 8, 4L * 1024L * MEBIBYTE);
		require(profile.fits(),
				"ordinary sparse corpus was rejected under a 4 GB heap");
		profile.validate();
	}

	private static void rejectsExtremeRetainedCorpusBeforeAgentCreation()
	{
		long extremeEntries = 480L * 107520L;
		ExactSvmCorpusAdmission.Profile profile =
				ExactSvmCorpusAdmission.profile(
						extremeEntries, 480, 8, 4L * 1024L * MEBIBYTE);
		require(!profile.fits(),
				"extreme retained corpus was incorrectly admitted under 4 GB");
		try
		{
			profile.validate();
			throw new AssertionError(
					"extreme retained corpus did not fail its admission check");
		}
		catch (IllegalArgumentException expected)
		{
			require(expected.getMessage().contains("Execution waves cannot reduce"),
					"rejection did not explain why scheduling cannot fix retained state");
			require(expected.getMessage().contains("No agents were started"),
					"rejection did not guarantee a clean pre-agent failure");
		}
	}

	private static void requiredHeapAcceptsTheSameCorpus()
	{
		long extremeEntries = 480L * 107520L;
		ExactSvmCorpusAdmission.Profile rejected =
				ExactSvmCorpusAdmission.profile(
						extremeEntries, 480, 8, 4L * 1024L * MEBIBYTE);
		ExactSvmCorpusAdmission.Profile admitted =
				ExactSvmCorpusAdmission.profile(
						extremeEntries,
						480,
						8,
						rejected.getRequiredHeapMb() * MEBIBYTE);
		require(admitted.fits(),
				"recommended heap did not admit the same exact-SVM corpus");
	}

	private static void admitsExtremeCorpusWhenEachNodeFitsInWaves()
	{
		long extremeEntries = (480L + 7L) * 107520L;
		long largestNodeEntries = 61L * 107520L;
		ExactSvmCorpusAdmission.WavePlan plan =
				ExactSvmCorpusAdmission.wavePlan(
						extremeEntries,
						largestNodeEntries,
						480,
						8,
						4L * 1024L * MEBIBYTE);
		plan.validate();
		require(plan.requiresDiskBackedWaves(),
				"extreme eight-node corpus was not assigned disk-backed waves");
	}

	private static void rejectsExtremeCorpusWhenOneNodeCannotFit()
	{
		long extremeEntries = 480L * 107520L;
		ExactSvmCorpusAdmission.WavePlan plan =
				ExactSvmCorpusAdmission.wavePlan(
						extremeEntries,
						extremeEntries,
						480,
						1,
						4L * 1024L * MEBIBYTE);
		try
		{
			plan.validate();
			throw new AssertionError(
					"one oversized Exact-SVM node was incorrectly admitted");
		}
		catch (IllegalArgumentException expected)
		{
			require(expected.getMessage().contains("largest recommender-node shard"),
					"single-node rejection did not identify the oversized shard");
		}
	}

	private static void require(boolean condition, String message)
	{
		if (!condition)
		{
			throw new AssertionError(message);
		}
	}
}
