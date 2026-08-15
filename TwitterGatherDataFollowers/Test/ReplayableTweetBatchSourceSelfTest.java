package TwitterGatherDataFollowers.userRyersonU;

import java.util.ArrayList;
import java.util.Date;

public final class ReplayableTweetBatchSourceSelfTest
{
	private ReplayableTweetBatchSourceSelfTest()
	{
	}

	public static void main(String[] args) throws Exception
	{
		ArrayList<Tweet> tweets = new ArrayList<Tweet>();
		tweets.add(new Tweet("first\twith tab", 10L, new Date(1L), "alice"));
		tweets.add(new Tweet("second\nwith newline", 20L, new Date(2L), "alice"));
		tweets.add(new Tweet("third", 30L, new Date(3L), "alice"));

		ReplayableTweetBatchSource source =
				ReplayableTweetBatchSource.create(tweets, 3, "followee");
		try
		{
			assertReplay(source);
			assertReplay(source);
		}
		finally
		{
			source.close();
		}
		System.out.println("ReplayableTweetBatchSourceSelfTest passed");
	}

	private static void assertReplay(ReplayableTweetBatchSource source)
			throws Exception
	{
		try (ReplayableTweetBatchSource.Reader reader = source.openReader())
		{
			ArrayList<String> firstBatch = reader.readBatch(2);
			require(firstBatch.size() == 2, "first replay batch size changed");
			require(firstBatch.get(0).contains("\t30\tfollowee\tthird"),
					"legacy descending tweet order changed");
			require(firstBatch.get(1).contains("second\nwith newline"),
					"tweet text was not preserved byte-for-byte");
			require(reader.hasRemaining(), "reader ended one batch too early");

			ArrayList<String> secondBatch = reader.readBatch(2);
			require(secondBatch.size() == 1, "final replay batch size changed");
			require(secondBatch.get(0).contains("first\twith tab"),
					"tab-containing tweet text was not preserved");
			require(!reader.hasRemaining(), "reader retained phantom rows");
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
