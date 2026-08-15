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
import java.util.List;

/**
 * Stores a user's outbound tweet messages off-heap and replays them for every run.
 */
final class ReplayableTweetBatchSource implements Closeable
{
	private static final int BUFFER_BYTES = 64 * 1024;
	private static final int MAX_RECORD_BYTES = 16 * 1024 * 1024;

	private final File file;
	private final int rowCount;
	private boolean closed;

	private ReplayableTweetBatchSource(File file, int rowCount)
	{
		this.file = file;
		this.rowCount = rowCount;
	}

	static ReplayableTweetBatchSource create(
			List<Tweet> tweets,
			int totalTweetCount,
			String followeeName) throws IOException
	{
		File spoolFile = Files.createTempFile(
				"dsmp-mobile-tweets-", ".bin").toFile();
		boolean success = false;
		int written = 0;
		try (DataOutputStream output = new DataOutputStream(
				new BufferedOutputStream(
						new FileOutputStream(spoolFile), BUFFER_BYTES)))
		{
			for (int index = tweets.size() - 1; index >= 0; index--)
			{
				Tweet tweet = tweets.get(index);
				if (tweet == null)
				{
					continue;
				}
				String message = totalTweetCount
						+ "\t" + tweet.getUser()
						+ "\t" + tweet.getTweetId()
						+ "\t" + followeeName
						+ "\t" + tweet.getTweetText();
				byte[] encoded = message.getBytes(StandardCharsets.UTF_8);
				if (encoded.length > MAX_RECORD_BYTES)
				{
					throw new IOException(
							"Tweet record exceeds the supported transport size: "
							+ encoded.length + " bytes");
				}
				output.writeInt(encoded.length);
				output.write(encoded);
				written++;
			}
			success = true;
		}
		finally
		{
			if (!success)
			{
				Files.deleteIfExists(spoolFile.toPath());
			}
		}
		spoolFile.deleteOnExit();
		return new ReplayableTweetBatchSource(spoolFile, written);
	}

	int size()
	{
		return rowCount;
	}

	Reader openReader() throws IOException
	{
		if (closed)
		{
			throw new IOException("Tweet source is already closed");
		}
		return new Reader(file, rowCount);
	}

	public void close() throws IOException
	{
		if (closed)
		{
			return;
		}
		closed = true;
		Files.deleteIfExists(file.toPath());
	}

	static final class Reader implements Closeable
	{
		private final DataInputStream input;
		private int remainingRows;
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
			while (rows.size() < requested && remainingRows > 0)
			{
				int length;
				try
				{
					length = input.readInt();
				}
				catch (EOFException e)
				{
					throw new IOException(
							"Tweet spool ended before all rows were replayed", e);
				}
				if (length < 0 || length > MAX_RECORD_BYTES)
				{
					throw new IOException(
							"Invalid tweet spool record length: " + length);
				}
				byte[] encoded = new byte[length];
				input.readFully(encoded);
				rows.add(new String(encoded, StandardCharsets.UTF_8));
				remainingRows--;
			}
			return rows;
		}

		boolean hasRemaining()
		{
			return remainingRows > 0;
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
}
