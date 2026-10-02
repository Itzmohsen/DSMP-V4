package TwitterGatherDataFollowers.userRyersonU;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * The representations the Temporal Graph Foundation model learns from, built
 * once per run from the run's canonical corpus (RunCorpus), so every node and
 * every node count sees the same features.
 *
 * <ul>
 * <li><b>Text</b>: sublinear TF-IDF, (1 + ln tf) ln(N / df), L2-normalised per
 *     user, over the terms used by at least two users (at most
 *     {@link #MAX_TERMS}, the most widely used first). A term only one user
 *     writes can neither link users nor generalise to a new one.</li>
 * <li><b>Activity</b>: each user's tweets per UTC calendar month over the
 *     corpus span, per hour of the day and per weekday.</li>
 * <li><b>Term at time</b>: the same TF-IDF over (term, month) pairs used by at
 *     least two users: two users share such a feature when they wrote the same
 *     word in the same month. These are the edges of the user-(term, month)
 *     bipartite graph whose projection is the temporal co-activity graph.</li>
 * </ul>
 *
 * Without a corpus (or for a user missing from it) the run's own TF-IDF
 * vector is used as the text representation and the temporal features are
 * empty.
 */
final class TgfFeatures
{
	static final int MAX_TERMS = 40000;
	static final int MAX_PAIRS = 200000;

	/** Users with a usable document, sorted by name. */
	final String[] users;
	final HashMap<String,Integer> index = new HashMap<String,Integer>();
	/** Kept vocabulary, sorted. */
	final String[] terms;
	/** Text, CSR over {@link #terms}, columns increasing. */
	final Sparse text;
	/** Raw term counts, parallel to {@link #text}. */
	final int[] termCounts;
	/** Users using each kept term. */
	final int[] termUsers;
	/** Term at month, CSR over pair features; pairTerm/pairMonth decode a feature. */
	final Sparse termTime;
	final int[] pairTerm;
	final int[] pairMonth;
	/** Tweets per month, hour and weekday, per user. */
	final double[][] monthly;
	final double[][] hourly;
	final double[][] weekly;
	/** Activity shares (month, hour, weekday), CSR, for the activity learner. */
	final Sparse activity;
	/** UTC year * 12 + month of the first month of the span; {@link #months} months. */
	final int firstMonth;
	final int months;
	final long minTime;
	final long maxTime;
	final long rowsScanned;
	final long datedRows;
	final int[] tweets;
	final int[] datedTweets;
	final long[] firstTime;
	final long[] lastTime;
	/** Whether the text came from the run corpus (false: the run's TF-IDF vectors). */
	final boolean fromCorpus;
	final String warning;

	/** Compressed sparse rows. */
	static final class Sparse
	{
		final int[] pointer;
		final int[] column;
		final double[] value;
		final int columns;

		Sparse(int[] pointer, int[] column, double[] value, int columns)
		{
			this.pointer = pointer;
			this.column = column;
			this.value = value;
			this.columns = columns;
		}

		int rows()
		{
			return pointer.length - 1;
		}

		long nonZeros()
		{
			return column.length;
		}

		double dot(int row, double[] dense)
		{
			double total = 0.0;
			for (int p = pointer[row]; p < pointer[row + 1]; p++)
			{
				total += value[p] * dense[column[p]];
			}
			return total;
		}

		double valueAt(int row, int col)
		{
			int position = Arrays.binarySearch(column, pointer[row], pointer[row + 1], col);
			return position >= 0 ? value[position] : 0.0;
		}
	}

	private TgfFeatures(String[] users, String[] terms, Sparse text, int[] termCounts, int[] termUsers,
			Sparse termTime, int[] pairTerm, int[] pairMonth, double[][] monthly, double[][] hourly,
			double[][] weekly, Sparse activity, int firstMonth, int months, long minTime, long maxTime,
			long rowsScanned, long datedRows, int[] tweets, int[] datedTweets, long[] firstTime, long[] lastTime,
			boolean fromCorpus, String warning)
	{
		this.users = users;
		for (int u = 0; u < users.length; u++)
		{
			index.put(users[u], Integer.valueOf(u));
		}
		this.terms = terms;
		this.text = text;
		this.termCounts = termCounts;
		this.termUsers = termUsers;
		this.termTime = termTime;
		this.pairTerm = pairTerm;
		this.pairMonth = pairMonth;
		this.monthly = monthly;
		this.hourly = hourly;
		this.weekly = weekly;
		this.activity = activity;
		this.firstMonth = firstMonth;
		this.months = months;
		this.minTime = minTime;
		this.maxTime = maxTime;
		this.rowsScanned = rowsScanned;
		this.datedRows = datedRows;
		this.tweets = tweets;
		this.datedTweets = datedTweets;
		this.firstTime = firstTime;
		this.lastTime = lastTime;
		this.fromCorpus = fromCorpus;
		this.warning = warning == null ? "" : warning;
	}

	int size()
	{
		return users.length;
	}

	boolean temporal()
	{
		return datedRows > 0 && maxTime > minTime;
	}

	/** The calendar month (UTC) of a month index, as yyyy-MM. */
	String monthLabel(int month)
	{
		int absolute = firstMonth + month;
		return String.format(java.util.Locale.US, "%04d-%02d", Integer.valueOf(absolute / 12),
				Integer.valueOf(absolute % 12 + 1));
	}

	static int monthOf(long time)
	{
		ZonedDateTime date = Instant.ofEpochMilli(time).atZone(ZoneOffset.UTC);
		return date.getYear() * 12 + date.getMonthValue() - 1;
	}

	// ================================================================ build

	/**
	 * Builds the features of {@code users} from the corpus file, falling back
	 * to {@code vectors} (the run's TF-IDF) where the corpus has no text.
	 */
	static TgfFeatures build(File corpus, Collection<String> users, Map<String,? extends Map<String,Double>> vectors)
	{
		final String[] names = new TreeSet<String>(users).toArray(new String[0]);
		final int n = names.length;
		final HashMap<String,Integer> position = new HashMap<String,Integer>();
		for (int u = 0; u < n; u++)
		{
			position.put(names[u], Integer.valueOf(u));
		}
		final ArrayList<HashMap<String,int[]>> counts = new ArrayList<HashMap<String,int[]>>();
		for (int u = 0; u < n; u++)
		{
			counts.add(new HashMap<String,int[]>());
		}
		final int[] tweets = new int[n];
		final int[] dated = new int[n];
		final long[] first = new long[n];
		final long[] last = new long[n];
		Arrays.fill(first, Long.MAX_VALUE);
		Arrays.fill(last, Long.MIN_VALUE);
		final long[] totals = new long[4];
		totals[2] = Long.MAX_VALUE;
		totals[3] = Long.MIN_VALUE;
		String warning = "";
		boolean readable = corpus != null && corpus.isFile();
		if (readable)
		{
			try
			{
				readRows(corpus, new RunCorpus.RowConsumer()
				{
					public void accept(RunCorpus.Row row)
					{
						totals[0]++;
						Integer u = position.get(row.user);
						if (u == null)
						{
							return;
						}
						int user = u.intValue();
						tweets[user]++;
						HashMap<String,int[]> map = counts.get(user);
						for (String token : tokens(row.text))
						{
							int[] count = map.get(token);
							if (count == null)
							{
								map.put(token, new int[] {1});
							}
							else
							{
								count[0]++;
							}
						}
						if (row.time != Long.MIN_VALUE)
						{
							totals[1]++;
							dated[user]++;
							totals[2] = Math.min(totals[2], row.time);
							totals[3] = Math.max(totals[3], row.time);
							first[user] = Math.min(first[user], row.time);
							last[user] = Math.max(last[user], row.time);
						}
					}
				});
			}
			catch (IOException failure)
			{
				readable = false;
				warning = "The run corpus could not be read (" + failure.getMessage()
						+ "); the run's TF-IDF vectors are used and the temporal signals are off.";
				for (HashMap<String,int[]> map : counts)
				{
					map.clear();
				}
				Arrays.fill(tweets, 0);
				Arrays.fill(dated, 0);
				totals[1] = 0;
			}
		}
		else
		{
			warning = "No run corpus was available: the run's TF-IDF vectors are used and the temporal signals are off.";
		}
		//Vocabulary: terms of at least two users, the most widely used first.
		HashMap<String,int[]> df = new HashMap<String,int[]>();
		boolean anyCorpusText = false;
		for (HashMap<String,int[]> map : counts)
		{
			anyCorpusText |= !map.isEmpty();
			for (String term : map.keySet())
			{
				int[] d = df.get(term);
				if (d == null)
				{
					df.put(term, new int[] {1});
				}
				else
				{
					d[0]++;
				}
			}
		}
		if (!anyCorpusText && vectors != null)
		{
			//Fallback text: the run's TF-IDF vectors (no raw counts).
			for (int u = 0; u < n; u++)
			{
				Map<String,Double> vector = vectors.get(names[u]);
				if (vector == null)
				{
					continue;
				}
				for (Map.Entry<String,Double> entry : vector.entrySet())
				{
					double w = entry.getValue() == null ? 0.0 : entry.getValue().doubleValue();
					if (w > 0.0 && Double.isFinite(w))
					{
						counts.get(u).put(entry.getKey(), new int[] {1});
						int[] d = df.get(entry.getKey());
						if (d == null)
						{
							df.put(entry.getKey(), new int[] {1});
						}
						else
						{
							d[0]++;
						}
					}
				}
			}
		}
		final HashMap<String,int[]> documentFrequency = df;
		ArrayList<String> candidates = new ArrayList<String>();
		for (Map.Entry<String,int[]> entry : df.entrySet())
		{
			if (entry.getValue()[0] >= 2 && entry.getValue()[0] < n)
			{
				candidates.add(entry.getKey());
			}
		}
		Collections.sort(candidates, new Comparator<String>()
		{
			public int compare(String a, String b)
			{
				int byUsers = Integer.compare(documentFrequency.get(b)[0], documentFrequency.get(a)[0]);
				return byUsers != 0 ? byUsers : a.compareTo(b);
			}
		});
		if (candidates.size() > MAX_TERMS)
		{
			candidates = new ArrayList<String>(candidates.subList(0, MAX_TERMS));
		}
		Collections.sort(candidates);
		String[] terms = candidates.toArray(new String[0]);
		HashMap<String,Integer> termIndex = new HashMap<String,Integer>();
		int[] termUsers = new int[terms.length];
		for (int t = 0; t < terms.length; t++)
		{
			termIndex.put(terms[t], Integer.valueOf(t));
			termUsers[t] = df.get(terms[t])[0];
		}
		//Text CSR.
		int[] pointer = new int[n + 1];
		ArrayList<int[]> rows = new ArrayList<int[]>();
		int nonZeros = 0;
		for (int u = 0; u < n; u++)
		{
			ArrayList<Integer> ids = new ArrayList<Integer>();
			for (String term : counts.get(u).keySet())
			{
				Integer id = termIndex.get(term);
				if (id != null)
				{
					ids.add(id);
				}
			}
			int[] sorted = new int[ids.size()];
			for (int i = 0; i < sorted.length; i++)
			{
				sorted[i] = ids.get(i).intValue();
			}
			Arrays.sort(sorted);
			rows.add(sorted);
			nonZeros += sorted.length;
		}
		int[] column = new int[nonZeros];
		double[] value = new double[nonZeros];
		int[] termCounts = new int[nonZeros];
		int p = 0;
		for (int u = 0; u < n; u++)
		{
			pointer[u] = p;
			int start = p;
			double norm = 0.0;
			for (int id : rows.get(u))
			{
				int tf = counts.get(u).get(terms[id])[0];
				double w = (1.0 + Math.log(tf)) * Math.log((double)n / termUsers[id]);
				column[p] = id;
				value[p] = w;
				termCounts[p] = tf;
				norm += w * w;
				p++;
			}
			norm = Math.sqrt(norm);
			for (int q = start; q < p; q++)
			{
				value[q] = norm > 0.0 ? value[q] / norm : 0.0;
			}
		}
		pointer[n] = p;
		Sparse text = new Sparse(pointer, column, value, terms.length);
		//Activity and term-at-month features need the dated rows: a second pass.
		boolean temporal = readable && totals[1] > 0 && totals[3] > totals[2];
		int firstMonth = temporal ? monthOf(totals[2]) : 0;
		int months = temporal ? monthOf(totals[3]) - firstMonth + 1 : 0;
		double[][] monthly = new double[n][months];
		double[][] hourly = new double[n][temporal ? 24 : 0];
		double[][] weekly = new double[n][temporal ? 7 : 0];
		Sparse termTime = new Sparse(new int[n + 1], new int[0], new double[0], 0);
		int[] pairTerm = new int[0];
		int[] pairMonth = new int[0];
		if (temporal)
		{
			final ArrayList<HashMap<Long,int[]>> pairs = new ArrayList<HashMap<Long,int[]>>();
			for (int u = 0; u < n; u++)
			{
				pairs.add(new HashMap<Long,int[]>());
			}
			final HashMap<String,Integer> kept = termIndex;
			final int offset = firstMonth;
			final double[][] month = monthly;
			final double[][] hour = hourly;
			final double[][] week = weekly;
			try
			{
				readRows(corpus, new RunCorpus.RowConsumer()
				{
					public void accept(RunCorpus.Row row)
					{
						Integer u = position.get(row.user);
						if (u == null || row.time == Long.MIN_VALUE)
						{
							return;
						}
						int user = u.intValue();
						ZonedDateTime date = Instant.ofEpochMilli(row.time).atZone(ZoneOffset.UTC);
						int m = date.getYear() * 12 + date.getMonthValue() - 1 - offset;
						month[user][m] += 1.0;
						hour[user][date.getHour()] += 1.0;
						week[user][date.getDayOfWeek().getValue() - 1] += 1.0;
						HashMap<Long,int[]> map = pairs.get(user);
						for (String token : tokens(row.text))
						{
							Integer id = kept.get(token);
							if (id == null)
							{
								continue;
							}
							Long key = Long.valueOf((long)id.intValue() * 4096L + m);
							int[] count = map.get(key);
							if (count == null)
							{
								map.put(key, new int[] {1});
							}
							else
							{
								count[0]++;
							}
						}
					}
				});
			}
			catch (IOException failure)
			{
				temporal = false;
				warning = "The run corpus could not be re-read for the temporal signals: " + failure.getMessage();
			}
			if (temporal)
			{
				Object[] built = pairFeatures(pairs, n);
				termTime = (Sparse)built[0];
				pairTerm = (int[])built[1];
				pairMonth = (int[])built[2];
			}
		}
		if (!temporal && warning.isEmpty() && readable)
		{
			warning = "The corpus has fewer than two distinct timestamps: the temporal signals are off.";
		}
		Sparse activity = activityShares(monthly, hourly, weekly, temporal);
		if (!temporal)
		{
			months = 0;
		}
		return new TgfFeatures(names, terms, text, termCounts, termUsers, termTime, pairTerm, pairMonth,
				temporal ? monthly : new double[n][0], temporal ? hourly : new double[n][0],
				temporal ? weekly : new double[n][0], activity, firstMonth, months,
				temporal ? totals[2] : 0L, temporal ? totals[3] : 0L, totals[0], totals[1], tweets, dated,
				first, last, anyCorpusText, warning);
	}

	/** (term, month) features used by at least two users, TF-IDF weighted and L2-normalised per user. */
	private static Object[] pairFeatures(List<HashMap<Long,int[]>> pairs, int n)
	{
		final HashMap<Long,int[]> df = new HashMap<Long,int[]>();
		for (HashMap<Long,int[]> map : pairs)
		{
			for (Long key : map.keySet())
			{
				int[] d = df.get(key);
				if (d == null)
				{
					df.put(key, new int[] {1});
				}
				else
				{
					d[0]++;
				}
			}
		}
		ArrayList<Long> keys = new ArrayList<Long>();
		for (Map.Entry<Long,int[]> entry : df.entrySet())
		{
			if (entry.getValue()[0] >= 2 && entry.getValue()[0] < n)
			{
				keys.add(entry.getKey());
			}
		}
		Collections.sort(keys, new Comparator<Long>()
		{
			public int compare(Long a, Long b)
			{
				int byUsers = Integer.compare(df.get(b)[0], df.get(a)[0]);
				return byUsers != 0 ? byUsers : a.compareTo(b);
			}
		});
		if (keys.size() > MAX_PAIRS)
		{
			keys = new ArrayList<Long>(keys.subList(0, MAX_PAIRS));
		}
		Collections.sort(keys);
		HashMap<Long,Integer> id = new HashMap<Long,Integer>();
		int[] pairTerm = new int[keys.size()];
		int[] pairMonth = new int[keys.size()];
		for (int i = 0; i < keys.size(); i++)
		{
			long key = keys.get(i).longValue();
			id.put(keys.get(i), Integer.valueOf(i));
			pairTerm[i] = (int)(key / 4096L);
			pairMonth[i] = (int)(key % 4096L);
		}
		int[] pointer = new int[n + 1];
		ArrayList<int[]> rows = new ArrayList<int[]>();
		int nonZeros = 0;
		for (int u = 0; u < n; u++)
		{
			ArrayList<Integer> own = new ArrayList<Integer>();
			for (Long key : pairs.get(u).keySet())
			{
				Integer feature = id.get(key);
				if (feature != null)
				{
					own.add(feature);
				}
			}
			int[] sorted = new int[own.size()];
			for (int i = 0; i < sorted.length; i++)
			{
				sorted[i] = own.get(i).intValue();
			}
			Arrays.sort(sorted);
			rows.add(sorted);
			nonZeros += sorted.length;
		}
		int[] column = new int[nonZeros];
		double[] value = new double[nonZeros];
		int p = 0;
		for (int u = 0; u < n; u++)
		{
			pointer[u] = p;
			int start = p;
			double norm = 0.0;
			for (int feature : rows.get(u))
			{
				Long key = keys.get(feature);
				int tf = pairs.get(u).get(key)[0];
				double w = (1.0 + Math.log(tf)) * Math.log((double)n / df.get(key)[0]);
				column[p] = feature;
				value[p] = w;
				norm += w * w;
				p++;
			}
			norm = Math.sqrt(norm);
			for (int q = start; q < p; q++)
			{
				value[q] = norm > 0.0 ? value[q] / norm : 0.0;
			}
		}
		pointer[n] = p;
		return new Object[] {new Sparse(pointer, column, value, keys.size()), pairTerm, pairMonth};
	}

	/** Shares of a user's dated tweets per month, per hour and per weekday (each block sums to one). */
	private static Sparse activityShares(double[][] monthly, double[][] hourly, double[][] weekly, boolean temporal)
	{
		int n = monthly.length;
		int months = temporal && n > 0 ? monthly[0].length : 0;
		int width = temporal ? months + 24 + 7 : 0;
		int[] pointer = new int[n + 1];
		ArrayList<Integer> columns = new ArrayList<Integer>();
		ArrayList<Double> values = new ArrayList<Double>();
		for (int u = 0; u < n; u++)
		{
			pointer[u] = columns.size();
			if (!temporal)
			{
				continue;
			}
			double[][] blocks = {monthly[u], hourly[u], weekly[u]};
			int offset = 0;
			for (double[] block : blocks)
			{
				double total = 0.0;
				for (double x : block)
				{
					total += x;
				}
				for (int d = 0; d < block.length; d++)
				{
					if (block[d] > 0.0 && total > 0.0)
					{
						columns.add(Integer.valueOf(offset + d));
						values.add(Double.valueOf(block[d] / total));
					}
				}
				offset += block.length;
			}
		}
		pointer[n] = columns.size();
		int[] column = new int[columns.size()];
		double[] value = new double[columns.size()];
		for (int i = 0; i < column.length; i++)
		{
			column[i] = columns.get(i).intValue();
			value[i] = values.get(i).doubleValue();
		}
		return new Sparse(pointer, column, value, width);
	}

	/**
	 * Streams a corpus: the run's RunCorpus file, or (when only the raw
	 * six-column dataset is at hand) its rows normalised with the application's
	 * default text options (retweets dropped, hashtags and stop words removed),
	 * keeping tweets of at least {@link TweetTextNormalizer#MINIMUM_USABLE_TOKENS}
	 * tokens as a run would.
	 */
	static void readRows(File file, RunCorpus.RowConsumer consumer) throws IOException
	{
		if (isRunCorpus(file))
		{
			RunCorpus.read(file, consumer);
			return;
		}
		try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(
				new java.io.FileInputStream(file), java.nio.charset.StandardCharsets.UTF_8), 128 * 1024))
		{
			String line;
			boolean first = true;
			while ((line = reader.readLine()) != null)
			{
				if (first && line.startsWith("\uFEFF"))
				{
					line = line.substring(1);
				}
				first = false;
				String[] fields = line.split("\t", 6);
				if (fields.length != 6)
				{
					continue;
				}
				String text = TweetTextNormalizer.normalize(fields[5], true, true, true, ControllerAgent.STOP_WORD_SET);
				if (TweetTextNormalizer.countTokens(text) < TweetTextNormalizer.MINIMUM_USABLE_TOKENS)
				{
					continue;
				}
				consumer.accept(new RunCorpus.Row(fields[4].trim(), fields[0].trim(),
						RunCorpus.parseTimestamp(fields[2]), text));
			}
		}
	}

	/** Whether a file is a run corpus (corpus-SHA256.tsv) rather than a raw dataset. */
	static boolean isRunCorpus(File file)
	{
		String name = file.getName();
		return name.startsWith("corpus-") && name.endsWith(".tsv");
	}

	/** The corpus text's tokens: normalised text is single-space separated. */
	static String[] tokens(String text)
	{
		if (text == null || text.isEmpty())
		{
			return new String[0];
		}
		String[] split = text.split(" ");
		int kept = 0;
		for (String token : split)
		{
			if (!token.isEmpty())
			{
				split[kept++] = token;
			}
		}
		return kept == split.length ? split : Arrays.copyOf(split, kept);
	}

	/** Approximate wire size of the features a node needs to score targets. */
	long payloadBytes()
	{
		long total = 64L;
		for (String user : users)
		{
			total += PayloadSize.string(user);
		}
		for (String term : terms)
		{
			total += PayloadSize.string(term);
		}
		total += 12L * text.nonZeros() + 12L * termTime.nonZeros() + 12L * activity.nonZeros();
		total += 8L * (long)users.length * (months + 31);
		return total;
	}
}
