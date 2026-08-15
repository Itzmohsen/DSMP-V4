package TwitterGatherDataFollowers.userRyersonU;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

final class RecommendationRunLifecycle
{
	enum Admission
	{
		NEW_RUN,
		CURRENT_RUN,
		RETIRED_RUN
	}

	private static final String LEGACY_RUN_ID = "StartSim-legacy";
	private static final int MAX_RETIRED_RUNS = 16;

	private final Set<String> retiredRunIds = new LinkedHashSet<String>();
	private String activeRunId;
	private boolean inputDeliveryComplete;
	private boolean textProcessingComplete;

	static String createRunId()
	{
		return "StartSim-" + UUID.randomUUID().toString();
	}

	Admission admit(String requestedRunId)
	{
		String runId = normalize(requestedRunId);
		if (activeRunId == null)
		{
			activeRunId = runId;
			inputDeliveryComplete = false;
			textProcessingComplete = false;
			return Admission.NEW_RUN;
		}
		if (activeRunId.equals(runId))
		{
			return Admission.CURRENT_RUN;
		}
		if (retiredRunIds.contains(runId))
		{
			return Admission.RETIRED_RUN;
		}

		retire(activeRunId);
		activeRunId = runId;
		inputDeliveryComplete = false;
		textProcessingComplete = false;
		return Admission.NEW_RUN;
	}

	boolean isActive(String requestedRunId)
	{
		return activeRunId != null && activeRunId.equals(normalize(requestedRunId));
	}

	String getActiveRunId()
	{
		return activeRunId;
	}

	boolean isTextProcessingComplete()
	{
		return textProcessingComplete;
	}

	boolean markInputDeliveryComplete(String requestedRunId)
	{
		if (!isActive(requestedRunId) || inputDeliveryComplete)
		{
			return false;
		}
		inputDeliveryComplete = true;
		return true;
	}

	boolean isInputDeliveryComplete()
	{
		return inputDeliveryComplete;
	}

	boolean markTextProcessingCompleteIfReady(int receivedTweets, int expectedTweets)
	{
		return markTextProcessingCompleteIfReady(
				receivedTweets,
				expectedTweets,
				false);
	}

	boolean markTextProcessingCompleteIfReady(
			int receivedTweets,
			int expectedTweets,
			boolean inputClosedByRuntime)
	{
		if (textProcessingComplete || !inputDeliveryComplete)
		{
			return false;
		}
		if (!inputClosedByRuntime && receivedTweets < expectedTweets)
		{
			return false;
		}
		textProcessingComplete = true;
		return true;
	}

	private void retire(String runId)
	{
		retiredRunIds.add(runId);
		while (retiredRunIds.size() > MAX_RETIRED_RUNS)
		{
			Iterator<String> iterator = retiredRunIds.iterator();
			if (!iterator.hasNext())
			{
				return;
			}
			iterator.next();
			iterator.remove();
		}
	}

	private static String normalize(String runId)
	{
		return runId == null || runId.trim().length() == 0
				? LEGACY_RUN_ID : runId;
	}
}
