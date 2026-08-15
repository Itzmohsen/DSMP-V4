package TwitterGatherDataFollowers.userRyersonU;

public final class RecommendationRunAbortRegistrySelfTest
{
	private RecommendationRunAbortRegistrySelfTest()
	{
	}

	public static void main(String[] args)
	{
		String runId = "abort-registry-self-test";
		RecommendationRunAbortRegistry.prepare(runId);
		require(!RecommendationRunAbortRegistry.isAborted(runId),
				"fresh run was already aborted");
		RecommendationRunAbortRegistry.abort(runId, "required node failed");
		require(RecommendationRunAbortRegistry.isAborted(runId),
				"failure did not abort the run");
		try
		{
			RecommendationRunAbortRegistry.requireActive(runId);
			throw new AssertionError("aborted run was allowed to continue");
		}
		catch (IllegalStateException expected)
		{
			// Expected.
		}
		RecommendationRunAbortRegistry.prepare(runId);
		require(!RecommendationRunAbortRegistry.isAborted(runId),
				"new run preparation did not clear the old abort");
		System.out.println("RecommendationRunAbortRegistrySelfTest passed");
	}

	private static void require(boolean condition, String message)
	{
		if (!condition)
		{
			throw new AssertionError(message);
		}
	}
}
