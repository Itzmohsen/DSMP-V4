package TwitterGatherDataFollowers.userRyersonU;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeSet;

/**
 * The canonical text of one experiment.
 *
 * Every tweet the run actually uses (after the date range, the tweet limit and
 * retweet removal), normalised by {@link TweetTextNormalizer} exactly as the
 * recommender nodes normalise it, with tweets shorter than
 * {@link TweetTextNormalizer#MINIMUM_USABLE_TOKENS} dropped. Algorithms that
 * read text outside the agents (Doc2Vec's Python worker, the temporal graph
 * model's activity profiles) read this file, so every algorithm sees the same
 * users, the same tweets and the same tokens.
 *
 * Format: one tweet per line, tab-separated: user, followee, timestamp (epoch
 * milliseconds, UTC; empty when the date cannot be parsed), normalised text.
 * Lines are ordered by user name, then tweet id. The file is named by the
 * SHA-256 of its content, so an unchanged corpus is written once and shared.
 */
final class RunCorpus
{
	/** Date formats accepted in the dataset's date column, all read as UTC. */
	private static final String[] DATE_PATTERNS = {
			"yyyy-MM-dd HH:mm:ss",
			"yyyy-MM-dd HH:mm",
			"yyyy-MM-dd",
			"EEE MMM dd HH:mm:ss Z yyyy",
			"MM/dd/yyyy",
			"yyyy/MM/dd"};

	/** One tweet of the corpus. */
	static final class Row
	{
		final String user;
		final String followee;
		/** Epoch milliseconds (UTC), or {@link Long#MIN_VALUE} when undated. */
		final long time;
		final String text;

		Row(String user, String followee, long time, String text)
		{
			this.user = user;
			this.followee = followee;
			this.time = time;
			this.text = text;
		}
	}

	/** Receives the corpus rows in file order. */
	interface RowConsumer
	{
		void accept(Row row) throws IOException;
	}

	private RunCorpus()
	{
	}

	/** Where corpora are kept, relative to the working directory. */
	static File defaultDirectory()
	{
		return new File(System.getProperty("dsmp.runCorpus.dir", ".runtime" + File.separator + "run-corpus"));
	}

	/**
	 * Writes the corpus of {@code users} (or returns the existing file with the
	 * same content) and returns it.
	 *
	 * @param tweetsByUser each user's tweets as delivered to the run
	 */
	static File write(
			File directory,
			Collection<String> users,
			Map<String,? extends Collection<Tweet>> tweetsByUser,
			Map<String,String> followees,
			boolean dropRetweets,
			boolean stripNonAlpha,
			boolean removeStopWords,
			Set<String> stopWords) throws IOException
	{
		if (!directory.isDirectory() && !directory.mkdirs())
		{
			throw new IOException("Cannot create the run corpus folder " + directory.getAbsolutePath());
		}
		File temporary = File.createTempFile("corpus-", ".tmp", directory);
		MessageDigest digest = sha256();
		try
		{
			try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
					new FileOutputStream(temporary), StandardCharsets.UTF_8)))
			{
				for (String user : new TreeSet<String>(users))
				{
					Collection<Tweet> tweets = tweetsByUser.get(user);
					if (tweets == null)
					{
						continue;
					}
					List<Tweet> ordered = new ArrayList<Tweet>(tweets);
					Collections.sort(ordered, new Comparator<Tweet>()
					{
						public int compare(Tweet first, Tweet second)
						{
							return Long.compare(first.getTweetId(), second.getTweetId());
						}
					});
					String followee = followees == null ? null : followees.get(user);
					for (Tweet tweet : ordered)
					{
						String text = TweetTextNormalizer.normalize(tweet.getTweetText(),
								dropRetweets, stripNonAlpha, removeStopWords, stopWords);
						if (TweetTextNormalizer.countTokens(text) < TweetTextNormalizer.MINIMUM_USABLE_TOKENS)
						{
							continue;
						}
						long time = parseTimestamp(tweet.getDateString());
						String line = clean(user) + "\t" + clean(followee == null ? "" : followee) + "\t"
								+ (time == Long.MIN_VALUE ? "" : Long.toString(time)) + "\t" + text + "\n";
						writer.write(line);
						digest.update(line.getBytes(StandardCharsets.UTF_8));
					}
				}
			}
			File target = new File(directory, "corpus-" + hex(digest.digest()) + ".tsv");
			if (target.isFile())
			{
				return target;
			}
			Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
			return target;
		}
		finally
		{
			if (temporary.exists())
			{
				temporary.delete();
			}
		}
	}

	/** Streams the rows of a corpus file. */
	static void read(File file, RowConsumer consumer) throws IOException
	{
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(
				new FileInputStream(file), StandardCharsets.UTF_8), 128 * 1024))
		{
			String line;
			while ((line = reader.readLine()) != null)
			{
				String[] fields = line.split("\t", 4);
				if (fields.length < 4)
				{
					continue;
				}
				long time = Long.MIN_VALUE;
				if (fields[2].length() > 0)
				{
					try
					{
						time = Long.parseLong(fields[2]);
					}
					catch (NumberFormatException ignored)
					{
						//An unparsable time is an undated tweet.
					}
				}
				consumer.accept(new Row(fields[0], fields[1], time, fields[3]));
			}
		}
	}

	/**
	 * Epoch milliseconds of a dataset date, read in UTC so every machine gets
	 * the same instant; {@link Long#MIN_VALUE} when it matches no format.
	 */
	static long parseTimestamp(String value)
	{
		if (value == null || value.trim().length() == 0)
		{
			return Long.MIN_VALUE;
		}
		String trimmed = value.trim();
		for (String pattern : DATE_PATTERNS)
		{
			SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.US);
			format.setLenient(false);
			format.setTimeZone(TimeZone.getTimeZone("UTC"));
			ParsePosition position = new ParsePosition(0);
			Date parsed = format.parse(trimmed, position);
			if (parsed != null && position.getIndex() == trimmed.length())
			{
				return parsed.getTime();
			}
		}
		return Long.MIN_VALUE;
	}

	private static String clean(String value)
	{
		return value.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
	}

	private static MessageDigest sha256()
	{
		try
		{
			return MessageDigest.getInstance("SHA-256");
		}
		catch (java.security.NoSuchAlgorithmException impossible)
		{
			throw new IllegalStateException(impossible);
		}
	}

	private static String hex(byte[] bytes)
	{
		StringBuilder text = new StringBuilder();
		for (byte value : bytes)
		{
			text.append(String.format(Locale.US, "%02x", value & 0xff));
		}
		return text.toString();
	}
}
