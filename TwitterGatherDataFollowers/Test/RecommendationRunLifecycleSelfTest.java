package TwitterGatherDataFollowers.userRyersonU;

public final class RecommendationRunLifecycleSelfTest
{
	private RecommendationRunLifecycleSelfTest()
	{
	}

	public static void main(String[] args)
	{
		String firstRunId = RecommendationRunLifecycle.createRunId();
		String secondRunId = RecommendationRunLifecycle.createRunId();
		require(!firstRunId.equals(secondRunId), "Run IDs must be unique");

		RecommendationRunLifecycle lifecycle = new RecommendationRunLifecycle();
		require(lifecycle.admit(firstRunId)
				== RecommendationRunLifecycle.Admission.NEW_RUN,
				"The first message must open a new run");
		require(lifecycle.admit(firstRunId)
				== RecommendationRunLifecycle.Admission.CURRENT_RUN,
				"Messages from the active run must remain current");

		require(!lifecycle.markTextProcessingCompleteIfReady(99, 100),
				"Text processing started before all expected records arrived");
		require(!lifecycle.markTextProcessingCompleteIfReady(100, 100),
				"Text processing started before every producer completed delivery");
		require(lifecycle.markInputDeliveryComplete(firstRunId),
				"The active run rejected its input-delivery barrier");
		require(lifecycle.isInputDeliveryComplete(),
				"The active run did not retain its input-delivery barrier");
		require(!lifecycle.markInputDeliveryComplete(firstRunId),
				"A duplicate input-delivery barrier was accepted twice");
		require(lifecycle.markTextProcessingCompleteIfReady(100, 100),
				"Text processing did not start at the expected threshold");
		require(!lifecycle.markTextProcessingCompleteIfReady(101, 100),
				"Text processing was allowed to run more than once");

		require(lifecycle.admit(secondRunId)
				== RecommendationRunLifecycle.Admission.NEW_RUN,
				"A different run ID must reset the run gate");
		require(!lifecycle.isTextProcessingComplete(),
				"A new run retained the previous completion flag");
		require(!lifecycle.isInputDeliveryComplete(),
				"A new run retained the previous input-delivery barrier");
		require(!lifecycle.markInputDeliveryComplete(firstRunId),
				"A retired run was allowed to complete input delivery");
		require(lifecycle.admit(firstRunId)
				== RecommendationRunLifecycle.Admission.RETIRED_RUN,
				"Late messages from a completed run were not rejected");
		require(lifecycle.isActive(secondRunId),
				"The current run changed after a stale message");

		System.out.println("Recommendation run lifecycle self-test passed");
	}

	private static void require(boolean condition, String message)
	{
		if (!condition)
		{
			throw new AssertionError(message);
		}
	}
}
