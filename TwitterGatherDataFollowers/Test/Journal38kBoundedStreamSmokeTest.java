package TwitterGatherDataFollowers.userRyersonU;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;

public final class Journal38kBoundedStreamSmokeTest
{
	private Journal38kBoundedStreamSmokeTest()
	{
	}

	public static void main(String[] args) throws Exception
	{
		File dataset = new File(
				"Dataset/424k/Newfolder/New-Journal-Datasets2022/"
				+ "Dataset-Journals-Authors-Titles-38k.txt");
		ArrayList<Tweet> tweets = new ArrayList<Tweet>();
		Map<String,String> followees = new LinkedHashMap<String,String>();
		Map<String,CorpusStreamRoute> routes =
				new LinkedHashMap<String,CorpusStreamRoute>();
		String target = null;
		int targetRows = 0;

		try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(
						new FileInputStream(dataset),
						StandardCharsets.UTF_8),
				128 * 1024))
		{
			String line;
			long lineNumber = 0L;
			while ((line = reader.readLine()) != null)
			{
				lineNumber++;
				DatasetRow row = DatasetRow.parse(line, lineNumber);
				if (target == null)
				{
					target = row.userName;
				}
				if (target.equals(row.userName))
				{
					targetRows++;
				}
				tweets.add(new Tweet(
						row.text, row.tweetId, row.tweetDate, row.userName));
				if (!followees.containsKey(row.userName))
				{
					followees.put(row.userName, row.referenceUser);
				}
				routes.put(
						row.userName,
						new CorpusStreamRoute(Arrays.asList(1), 1));
			}
		}

		require(tweets.size() == 38875,
				"unexpected journal row count: " + tweets.size());
		require(routes.size() == 38546,
				"unexpected journal user count: " + routes.size());
		require(ControllerAgent.calculateTextParticipantCount(
				new ArrayList<String>(routes.keySet()),
				Arrays.asList(target),
				true) == 2,
				"38k journal initialization was not bounded to two participants");

		ReplayableCorpusStream stream = ReplayableCorpusStream.create(
				tweets,
				routes,
				followees,
				new LinkedHashSet<String>(Arrays.asList(target)));
		try
		{
			require(stream.size() == tweets.size() - targetRows,
					"bounded stream lost or duplicated journal rows");
			int replayedRows = 0;
			for (ReplayableCorpusStream.RouteSource source
					: stream.getRouteSources())
			{
				try (ReplayableCorpusStream.Reader routeReader =
						source.openReader())
				{
					while (routeReader.hasRemaining())
					{
						replayedRows += routeReader.readBatch(128).size();
					}
				}
			}
			require(replayedRows == stream.size(),
					"journal replay count changed: " + replayedRows);
		}
		finally
		{
			stream.close();
		}

		System.out.println(
				"Journal38kBoundedStreamSmokeTest passed: "
				+ tweets.size() + " rows, " + routes.size()
				+ " users, 2 live participants");
	}

	private static void require(boolean condition, String message)
	{
		if (!condition)
		{
			throw new AssertionError(message);
		}
	}
}
