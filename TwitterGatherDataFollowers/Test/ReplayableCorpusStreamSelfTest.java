package TwitterGatherDataFollowers.userRyersonU;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

public final class ReplayableCorpusStreamSelfTest
{
	private ReplayableCorpusStreamSelfTest()
	{
	}

	public static void main(String[] args) throws Exception
	{
		preservesRowsRoutesAndReplay();
		System.out.println("ReplayableCorpusStreamSelfTest passed");
	}

	private static void preservesRowsRoutesAndReplay() throws Exception
	{
		ArrayList<Tweet> tweets = new ArrayList<Tweet>();
		tweets.add(new Tweet("alice target", 1L, new Date(1L), "alice"));
		tweets.add(new Tweet("bob first", 2L, new Date(2L), "bob"));
		tweets.add(new Tweet("bob second", 3L, new Date(3L), "bob"));
		tweets.add(new Tweet("carol replicated", 4L, new Date(4L), "carol"));

		Map<String,CorpusStreamRoute> routes =
				new LinkedHashMap<String,CorpusStreamRoute>();
		routes.put("alice", new CorpusStreamRoute(Arrays.asList(1, 2), 1));
		routes.put("bob", new CorpusStreamRoute(Arrays.asList(1), 1));
		routes.put("carol", new CorpusStreamRoute(Arrays.asList(1, 2), 2));

		Map<String,String> followees = new LinkedHashMap<String,String>();
		followees.put("alice", "journal-a");
		followees.put("bob", "journal-b");
		followees.put("carol", "journal-c");

		ReplayableCorpusStream stream = ReplayableCorpusStream.create(
				tweets,
				routes,
				followees,
				new LinkedHashSet<String>(Arrays.asList("alice")));
		try
		{
			require(stream.size() == 3, "target rows were not excluded");
			require(stream.getRouteSources().size() == 2,
					"route-compatible rows were not grouped");
			List<String> firstReplay = replay(stream);
			List<String> secondReplay = replay(stream);
			require(firstReplay.equals(secondReplay),
					"corpus replay changed between simulation runs");
			require(firstReplay.contains(
					"2\tbob\t2\tjournal-b\tbob first"),
					"bob's first row or per-user count changed");
			require(firstReplay.contains(
					"2\tbob\t3\tjournal-b\tbob second"),
					"bob's second row or per-user count changed");
			require(firstReplay.contains(
					"1\tcarol\t4\tjournal-c\tcarol replicated"),
					"replicated route row changed");
			for (String row : firstReplay)
			{
				require(!row.contains("\talice\t"),
						"selected target leaked into the virtual corpus");
			}
		}
		finally
		{
			stream.close();
		}
	}

	private static List<String> replay(ReplayableCorpusStream stream)
			throws Exception
	{
		ArrayList<String> rows = new ArrayList<String>();
		for (ReplayableCorpusStream.RouteSource source
				: stream.getRouteSources())
		{
			try (ReplayableCorpusStream.Reader reader = source.openReader())
			{
				while (reader.hasRemaining())
				{
					rows.addAll(reader.readBatch(2));
				}
			}
		}
		return rows;
	}

	private static void require(boolean condition, String message)
	{
		if (!condition)
		{
			throw new AssertionError(message);
		}
	}
}
