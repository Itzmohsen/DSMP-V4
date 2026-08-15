package TwitterGatherDataFollowers.userRyersonU;

public final class TweetBatchDeliveryLedgerSelfTest
{
	private TweetBatchDeliveryLedgerSelfTest()
	{
	}

	public static void main(String[] args)
	{
		TweetBatchDeliveryLedger ledger = new TweetBatchDeliveryLedger();
		require(!ledger.wasProcessed("streamer", "run:batch:1"),
				"a new batch was treated as a retry");

		ledger.markProcessed("streamer", "run:batch:1");
		require(ledger.wasProcessed("streamer", "run:batch:1"),
				"a processed retryable batch was forgotten");
		require(!ledger.wasProcessed("target-user", "run:batch:1"),
				"batch correlation IDs collided across senders");

		ledger.markProcessed("streamer", null);
		require(!ledger.wasProcessed("streamer", null),
				"an uncorrelated legacy batch was incorrectly deduplicated");

		ledger.clear();
		require(!ledger.wasProcessed("streamer", "run:batch:1"),
				"per-run batch delivery state was not cleared");

		System.out.println("TweetBatchDeliveryLedgerSelfTest passed");
	}

	private static void require(boolean condition, String message)
	{
		if (!condition)
		{
			throw new AssertionError(message);
		}
	}
}
