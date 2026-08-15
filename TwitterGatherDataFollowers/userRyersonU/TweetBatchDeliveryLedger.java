package TwitterGatherDataFollowers.userRyersonU;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Tracks correlated batch deliveries so transport retries remain idempotent.
 */
final class TweetBatchDeliveryLedger
{
	private final Set<String> processedBatchKeys =
			new LinkedHashSet<String>();

	boolean wasProcessed(String sender, String correlationId)
	{
		String key = key(sender, correlationId);
		return key != null && processedBatchKeys.contains(key);
	}

	void markProcessed(String sender, String correlationId)
	{
		String key = key(sender, correlationId);
		if (key != null)
		{
			processedBatchKeys.add(key);
		}
	}

	void clear()
	{
		processedBatchKeys.clear();
	}

	private static String key(String sender, String correlationId)
	{
		if (correlationId == null)
		{
			return null;
		}
		return (sender == null ? "" : sender) + "\t" + correlationId;
	}
}
