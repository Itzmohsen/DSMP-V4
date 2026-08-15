package TwitterGatherDataFollowers.userRyersonU;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class InMemoryDbSelfTest
{
	private InMemoryDbSelfTest()
	{
	}

	public static void main(String[] args)
	{
		preservesLookupCountsOrderAndIsolation();
		rebuildsIndexAfterDatasetReplacement();
		System.out.println("InMemoryDbSelfTest passed");
	}

	private static void preservesLookupCountsOrderAndIsolation()
	{
		InMemoryDb database = new InMemoryDb();
		database.addTweet(tweet(3L, "alice"));
		database.addTweet(tweet(1L, "bob"));
		database.addTweet(tweet(2L, "alice"));

		require(database.getTweetCountFromUser("alice") == 2,
				"indexed count for Alice is incorrect");
		require(database.getTweetCountFromUser("missing") == 0,
				"missing users must have a zero count");

		ArrayList<Tweet> aliceCopy = database.getTweetsFromUser("alice");
		require(ids(aliceCopy).equals(java.util.Arrays.asList(3L, 2L)),
				"indexed lookup changed insertion order");
		aliceCopy.clear();
		require(database.getTweetCountFromUser("alice") == 2,
				"caller mutation leaked into the database index");

		Collections.sort(database.getTweets());
		database.refreshUserIndex();
		require(ids(database.getTweetsFromUser("alice"))
						.equals(java.util.Arrays.asList(2L, 3L)),
				"index refresh did not preserve sorted master order");

		database.clearDb();
		require(database.getTotalTweets() == 0
						&& database.getTweetCountFromUser("alice") == 0,
				"clearDb left indexed tweet state behind");
	}

	private static void rebuildsIndexAfterDatasetReplacement()
	{
		InMemoryDb database = new InMemoryDb();
		database.addTweet(tweet(1L, "old"));

		ArrayList<Tweet> replacement = new ArrayList<Tweet>();
		replacement.add(tweet(10L, "new"));
		replacement.add(tweet(11L, "new"));
		database.setTweets(replacement);

		require(database.getTweetCountFromUser("old") == 0,
				"setTweets retained the previous user index");
		require(database.getTweetCountFromUser("new") == 2,
				"setTweets did not index the replacement dataset");
	}

	private static Tweet tweet(long id, String user)
	{
		return new Tweet("alpha beta gamma", id, "2020-01-01", user);
	}

	private static List<Long> ids(List<Tweet> tweets)
	{
		ArrayList<Long> ids = new ArrayList<Long>();
		for (Tweet tweet : tweets)
		{
			ids.add(Long.valueOf(tweet.getTweetId()));
		}
		return ids;
	}

	private static void require(boolean condition, String message)
	{
		if (!condition)
		{
			throw new AssertionError(message);
		}
	}
}
