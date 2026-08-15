package TwitterGatherDataFollowers.userRyersonU;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Stores non-target text-dataset traffic in a small set of route files so a
 * bounded number of JADE agents can replay the complete recommendation corpus.
 */
final class ReplayableCorpusStream implements Closeable
{
	private static final int BUFFER_BYTES = 128 * 1024;
	private static final int MAX_RECORD_BYTES = 16 * 1024 * 1024;

	private final List<RouteSource> routeSources;
	private final int rowCount;
	private boolean closed;

	private ReplayableCorpusStream(List<RouteSource> routeSources, int rowCount)
	{
		this.routeSources = Collections.unmodifiableList(
				new ArrayList<RouteSource>(routeSources));
		this.rowCount = rowCount;
	}

	static ReplayableCorpusStream create(
			List<Tweet> tweets,
			Map<String,CorpusStreamRoute> routesByUser,
			Map<String,String> followeesByUser,
			Set<String> excludedUsers) throws IOException
	{
		List<Tweet> sourceTweets = tweets == null
				? Collections.<Tweet>emptyList() : tweets;
		Map<String,CorpusStreamRoute> sourceRoutes = routesByUser == null
				? Collections.<String,CorpusStreamRoute>emptyMap() : routesByUser;
		Map<String,String> sourceFollowees = followeesByUser == null
				? Collections.<String,String>emptyMap() : followeesByUser;
		Set<String> excluded = excludedUsers == null
				? Collections.<String>emptySet()
				: new LinkedHashSet<String>(excludedUsers);

		LinkedHashMap<String,Integer> tweetCounts =
				new LinkedHashMap<String,Integer>();
		for (Tweet tweet : sourceTweets)
		{
			if (tweet == null || excluded.contains(tweet.getUser()))
			{
				continue;
			}
			Integer count = tweetCounts.get(tweet.getUser());
			tweetCounts.put(tweet.getUser(), count == null ? 1 : count + 1);
		}

		LinkedHashMap<String,ArrayList<String>> messagesByUser =
				new LinkedHashMap<String,ArrayList<String>>();
		for (int index = sourceTweets.size() - 1; index >= 0; index--)
		{
			Tweet tweet = sourceTweets.get(index);
			if (tweet == null || excluded.contains(tweet.getUser()))
			{
				continue;
			}
			String user = tweet.getUser();
			Integer userTweetCount = tweetCounts.get(user);
			String followee = sourceFollowees.get(user);
			String message = (userTweetCount == null ? 0 : userTweetCount)
					+ "\t" + user
					+ "\t" + tweet.getTweetId()
					+ "\t" + (followee == null ? "" : followee)
					+ "\t" + (tweet.getTweetText() == null
							? "" : tweet.getTweetText());
			ArrayList<String> userMessages = messagesByUser.get(user);
			if (userMessages == null)
			{
				userMessages = new ArrayList<String>();
				messagesByUser.put(user, userMessages);
			}
			userMessages.add(message);
		}

		LinkedHashMap<CorpusStreamRoute,RouteWriter> writers =
				new LinkedHashMap<CorpusStreamRoute,RouteWriter>();
		boolean success = false;
		int totalRows = 0;
		try
		{
			for (Map.Entry<String,ArrayList<String>> entry
					: messagesByUser.entrySet())
			{
				String user = entry.getKey();
				CorpusStreamRoute route = sourceRoutes.get(user);
				if (route == null)
				{
					throw new IOException(
							"No recommender route exists for dataset user '"
							+ user + "'.");
				}
				RouteWriter writer = writers.get(route);
				if (writer == null)
				{
					writer = new RouteWriter(route);
					writers.put(route, writer);
				}
				for (String message : entry.getValue())
				{
					writer.write(message);
					totalRows++;
				}
			}
			success = true;
		}
		finally
		{
			IOException closeFailure = null;
			for (RouteWriter writer : writers.values())
			{
				try
				{
					writer.close();
				}
				catch (IOException e)
				{
					closeFailure = e;
				}
			}
			if (!success)
			{
				for (RouteWriter writer : writers.values())
				{
					Files.deleteIfExists(writer.file.toPath());
				}
			}
			if (success && closeFailure != null)
			{
				throw closeFailure;
			}
		}

		ArrayList<RouteSource> routeSources = new ArrayList<RouteSource>();
		for (RouteWriter writer : writers.values())
		{
			writer.file.deleteOnExit();
			if (writer.rowCount > 0)
			{
				routeSources.add(new RouteSource(
						writer.route, writer.file, writer.rowCount));
			}
		}
		return new ReplayableCorpusStream(routeSources, totalRows);
	}

	int size()
	{
		return rowCount;
	}

	List<RouteSource> getRouteSources()
	{
		return routeSources;
	}

	public void close() throws IOException
	{
		if (closed)
		{
			return;
		}
		closed = true;
		IOException failure = null;
		for (RouteSource source : routeSources)
		{
			try
			{
				Files.deleteIfExists(source.file.toPath());
			}
			catch (IOException e)
			{
				failure = e;
			}
		}
		if (failure != null)
		{
			throw failure;
		}
	}

	static final class RouteSource
	{
		private final CorpusStreamRoute route;
		private final File file;
		private final int rowCount;

		private RouteSource(
				CorpusStreamRoute route,
				File file,
				int rowCount)
		{
			this.route = route;
			this.file = file;
			this.rowCount = rowCount;
		}

		CorpusStreamRoute getRoute()
		{
			return route;
		}

		int size()
		{
			return rowCount;
		}

		Reader openReader() throws IOException
		{
			return new Reader(file, rowCount);
		}
	}

	static final class Reader implements Closeable
	{
		private final DataInputStream input;
		private int remainingRows;
		private String lookaheadRow;
		private boolean closed;

		private Reader(File file, int rowCount) throws IOException
		{
			input = new DataInputStream(new BufferedInputStream(
					new FileInputStream(file), BUFFER_BYTES));
			remainingRows = rowCount;
		}

		ArrayList<String> readBatch(int maximumRows) throws IOException
		{
			int requested = Math.max(1, maximumRows);
			ArrayList<String> rows =
					new ArrayList<String>(Math.min(requested, remainingRows));
			String currentUser = null;
			while (lookaheadRow != null || remainingRows > 0)
			{
				String row = readNextRow();
				String rowUser = userFromRow(row);
				if (!rows.isEmpty()
						&& rows.size() >= requested
						&& !rowUser.equals(currentUser))
				{
					lookaheadRow = row;
					break;
				}
				rows.add(row);
				currentUser = rowUser;
			}
			return rows;
		}

		boolean hasRemaining()
		{
			return lookaheadRow != null || remainingRows > 0;
		}

		private String readNextRow() throws IOException
		{
			if (lookaheadRow != null)
			{
				String row = lookaheadRow;
				lookaheadRow = null;
				return row;
			}
			int length;
			try
			{
				length = input.readInt();
			}
			catch (EOFException e)
			{
				throw new IOException(
						"Corpus stream ended before every row was replayed.", e);
			}
			if (length < 0 || length > MAX_RECORD_BYTES)
			{
				throw new IOException(
						"Invalid corpus stream record length: " + length);
			}
			byte[] encoded = new byte[length];
			input.readFully(encoded);
			remainingRows--;
			return new String(encoded, StandardCharsets.UTF_8);
		}

		private static String userFromRow(String row)
		{
			if (row == null)
			{
				return "";
			}
			int firstTab = row.indexOf('\t');
			if (firstTab < 0)
			{
				return "";
			}
			int secondTab = row.indexOf('\t', firstTab + 1);
			if (secondTab < 0)
			{
				return "";
			}
			return row.substring(firstTab + 1, secondTab);
		}

		public void close() throws IOException
		{
			if (closed)
			{
				return;
			}
			closed = true;
			input.close();
		}
	}

	private static final class RouteWriter implements Closeable
	{
		private final CorpusStreamRoute route;
		private final File file;
		private final DataOutputStream output;
		private int rowCount;

		private RouteWriter(CorpusStreamRoute route) throws IOException
		{
			this.route = route;
			file = Files.createTempFile("dsmp-corpus-route-", ".bin").toFile();
			output = new DataOutputStream(new BufferedOutputStream(
					new FileOutputStream(file), BUFFER_BYTES));
		}

		private void write(String message) throws IOException
		{
			byte[] encoded = message.getBytes(StandardCharsets.UTF_8);
			if (encoded.length > MAX_RECORD_BYTES)
			{
				throw new IOException(
						"Corpus row exceeds the supported transport size: "
						+ encoded.length + " bytes.");
			}
			output.writeInt(encoded.length);
			output.write(encoded);
			rowCount++;
		}

		public void close() throws IOException
		{
			output.close();
		}
	}
}
