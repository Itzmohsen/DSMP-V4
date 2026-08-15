package TwitterGatherDataFollowers.userRyersonU;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class MillionRowIngestionSelfTest
{
	private MillionRowIngestionSelfTest()
	{
	}

	public static void main(String[] args) throws Exception
	{
		if (args.length != 1)
		{
			throw new IllegalArgumentException("Expected the generated dataset path");
		}
		File dataset = new File(args[0]);
		InMemoryDb local = new InMemoryDb();
		Map<String,Integer> expectedCounts = new LinkedHashMap<String,Integer>();
		long rows = 0L;

		try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(
						new FileInputStream(dataset), StandardCharsets.UTF_8),
				128 * 1024))
		{
			String line;
			while ((line = reader.readLine()) != null)
			{
				DatasetRow row = DatasetRow.parse(line, rows + 1L);
				local.addTweet(new Tweet(
						row.text, row.tweetId, row.tweetDate, row.userName));
				Integer count = expectedCounts.get(row.userName);
				expectedCounts.put(
						row.userName,
						Integer.valueOf(count == null ? 1 : count.intValue() + 1));
				rows++;
			}
		}

		require(rows == 1000000L, "stress corpus does not contain one million rows");
		require(local.getTotalTweets() == 1000000,
				"local database lost rows during ingestion");

		Collections.sort(local.getTweets());
		local.refreshUserIndex();
		ArrayList<Tweet> availableTweets =
				new ArrayList<Tweet>(local.getTweets());
		InMemoryDb available = new InMemoryDb();
		available.setTweets(availableTweets);

		ArrayList<ArrayList<Tweet>> mobileAgentSources =
				new ArrayList<ArrayList<Tweet>>(expectedCounts.size());
		long assignedRows = 0L;
		for (Map.Entry<String,Integer> entry : expectedCounts.entrySet())
		{
			require(available.getTweetCountFromUser(entry.getKey())
							== entry.getValue().intValue(),
					"indexed count mismatch for " + entry.getKey());
			ArrayList<Tweet> userTweets =
					available.getTweetsFromUser(entry.getKey());
			assignedRows += userTweets.size();
			mobileAgentSources.add(userTweets);
		}
		require(assignedRows == rows,
				"mobile-agent assignment did not preserve every row");

		local.clearDb();
		available.clearDb();
		availableTweets.clear();
		require(sumSizes(mobileAgentSources) == rows,
				"controller cleanup invalidated mobile-agent source lists");

		ArrayList<ReplayableTweetBatchSource> replaySources =
				new ArrayList<ReplayableTweetBatchSource>();
		long replayRows = 0L;
		try
		{
			for (ArrayList<Tweet> userTweets : mobileAgentSources)
			{
				ReplayableTweetBatchSource source =
						ReplayableTweetBatchSource.create(
								userTweets, userTweets.size(), "followee");
				replaySources.add(source);
				replayRows += source.size();
				userTweets.clear();
			}
			require(replayRows == rows,
					"disk-backed mobile sources did not preserve every row");

			System.gc();
			Thread.sleep(100L);
			Runtime runtime = Runtime.getRuntime();
			long usedMb = (runtime.totalMemory() - runtime.freeMemory())
					/ (1024L * 1024L);
			System.out.println(
					"MillionRowIngestionSelfTest passed: rows=" + rows
					+ " users=" + expectedCounts.size()
					+ " replayRows=" + replayRows
					+ " usedHeapAfterSpoolMB=" + usedMb);
		}
		finally
		{
			for (ReplayableTweetBatchSource source : replaySources)
			{
				source.close();
			}
		}
	}

	private static long sumSizes(ArrayList<ArrayList<Tweet>> lists)
	{
		long total = 0L;
		for (ArrayList<Tweet> list : lists)
		{
			total += list.size();
		}
		return total;
	}

	private static void require(boolean condition, String message)
	{
		if (!condition)
		{
			throw new AssertionError(message);
		}
	}
}
