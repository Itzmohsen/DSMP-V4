package TwitterGatherDataFollowers.userRyersonU;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * A run's canonical corpus indexed for explanations. The TF-IDF is the
 * recommender agents' own (raw term frequency times log2(N / df), N the users
 * with a document, L2-normalised per user, every term kept), so the cosine of
 * two rows is exactly the Similarity algorithm's score. Postings give every
 * term's users, so scores against all candidates and the effect of removing
 * words are computed exactly and quickly; each user's tweets are read back
 * from the file when quoted.
 */
final class EvidenceCorpus
{
	final File file;
	final String[] users;
	final HashMap<String,Integer> index = new HashMap<String,Integer>();
	/** Each user's followed account as the corpus records it ("" when unknown). */
	final String[] followee;
	final String[] terms;
	final HashMap<String,Integer> termIndex = new HashMap<String,Integer>();
	/** Users using each term. */
	final int[] documentFrequency;
	/** Per user (CSR, columns increasing): raw counts and normalised TF-IDF. */
	final int[] pointer;
	final int[] column;
	final int[] count;
	final double[] weight;
	/** Users with a positive TF-IDF weight for each term, and the weights (postings). */
	final int[] postingPointer;
	final int[] postingUser;
	final double[] postingWeight;
	/** Byte offset and line count of each user's tweets in the file (the corpus is grouped by user). */
	final long[] offset;
	final int[] lines;
	/** Tweets per UTC month (sparse: month index to count) for each user. */
	final List<TreeMap<Integer,Integer>> monthly;
	final int tweets;

	private static final LinkedHashMap<String,EvidenceCorpus> CACHE = new LinkedHashMap<String,EvidenceCorpus>()
	{
		private static final long serialVersionUID = 1L;

		protected boolean removeEldestEntry(Map.Entry<String,EvidenceCorpus> eldest)
		{
			return size() > 2;
		}
	};

	/** One tweet read back from the corpus. */
	static final class Tweet
	{
		final long time;
		final String text;

		Tweet(long time, String text)
		{
			this.time = time;
			this.text = text;
		}
	}

	private EvidenceCorpus(File file, String[] users, String[] followee, String[] terms, int[] documentFrequency,
			int[] pointer, int[] column, int[] count, double[] weight, long[] offset, int[] lines,
			List<TreeMap<Integer,Integer>> monthly, int tweets)
	{
		this.file = file;
		this.users = users;
		for (int u = 0; u < users.length; u++)
		{
			index.put(users[u], Integer.valueOf(u));
		}
		this.followee = followee;
		this.terms = terms;
		for (int t = 0; t < terms.length; t++)
		{
			termIndex.put(terms[t], Integer.valueOf(t));
		}
		this.documentFrequency = documentFrequency;
		this.pointer = pointer;
		this.column = column;
		this.count = count;
		this.weight = weight;
		this.offset = offset;
		this.lines = lines;
		this.monthly = monthly;
		this.tweets = tweets;
		int[] postings = new int[terms.length + 1];
		for (int p = 0; p < column.length; p++)
		{
			if (weight[p] > 0.0)
			{
				postings[column[p] + 1]++;
			}
		}
		for (int t = 0; t < terms.length; t++)
		{
			postings[t + 1] += postings[t];
		}
		postingPointer = postings;
		postingUser = new int[postings[terms.length]];
		postingWeight = new double[postings[terms.length]];
		int[] fill = Arrays.copyOf(postings, terms.length);
		for (int u = 0; u < users.length; u++)
		{
			for (int p = pointer[u]; p < pointer[u + 1]; p++)
			{
				if (weight[p] > 0.0)
				{
					int t = column[p];
					postingUser[fill[t]] = u;
					postingWeight[fill[t]] = weight[p];
					fill[t]++;
				}
			}
		}
	}

	int size()
	{
		return users.length;
	}

	/**
	 * The corpus of a file, indexed once and kept for the next explanations.
	 * One corpus is read at a time: callers that arrive while it is being read
	 * wait for it instead of reading the same large file again in parallel.
	 */
	static EvidenceCorpus of(File file) throws IOException
	{
		String key = file.getAbsolutePath() + ":" + file.length() + ":" + file.lastModified();
		synchronized (LOADING)
		{
			synchronized (CACHE)
			{
				EvidenceCorpus cached = CACHE.get(key);
				if (cached != null)
				{
					return cached;
				}
			}
			EvidenceCorpus corpus = read(file);
			synchronized (CACHE)
			{
				CACHE.put(key, corpus);
			}
			return corpus;
		}
	}

	private static final Object LOADING = new Object();

	private static EvidenceCorpus read(File file) throws IOException
	{
		ArrayList<String> userList = new ArrayList<String>();
		ArrayList<String> followeeList = new ArrayList<String>();
		ArrayList<HashMap<String,int[]>> counts = new ArrayList<HashMap<String,int[]>>();
		ArrayList<long[]> spans = new ArrayList<long[]>();
		ArrayList<TreeMap<Integer,Integer>> months = new ArrayList<TreeMap<Integer,Integer>>();
		HashMap<String,Integer> seen = new HashMap<String,Integer>();
		int tweets = 0;
		try (InputStream input = new BufferedInputStream(new FileInputStream(file), 1 << 17))
		{
			ByteArrayOutputStream line = new ByteArrayOutputStream(256);
			long position = 0L;
			long start = 0L;
			int next;
			while (true)
			{
				next = input.read();
				if (next != '\n' && next != -1)
				{
					line.write(next);
					position++;
					continue;
				}
				if (next == '\n')
				{
					position++;
				}
				if (line.size() > 0)
				{
					String text = new String(line.toByteArray(), StandardCharsets.UTF_8);
					String[] fields = text.split("\t", 4);
					if (fields.length == 4)
					{
						Integer u = seen.get(fields[0]);
						if (u == null)
						{
							u = Integer.valueOf(userList.size());
							seen.put(fields[0], u);
							userList.add(fields[0]);
							followeeList.add(fields[1]);
							counts.add(new HashMap<String,int[]>());
							spans.add(new long[] {start, 0});
							months.add(new TreeMap<Integer,Integer>());
						}
						long[] span = spans.get(u.intValue());
						span[1]++;
						tweets++;
						for (String token : TgfFeatures.tokens(fields[3]))
						{
							int[] c = counts.get(u.intValue()).get(token);
							if (c == null)
							{
								counts.get(u.intValue()).put(token, new int[] {1});
							}
							else
							{
								c[0]++;
							}
						}
						if (fields[2].length() > 0)
						{
							try
							{
								Integer month = Integer.valueOf(TgfFeatures.monthOf(Long.parseLong(fields[2])));
								TreeMap<Integer,Integer> map = months.get(u.intValue());
								Integer old = map.get(month);
								map.put(month, Integer.valueOf(old == null ? 1 : old.intValue() + 1));
							}
							catch (NumberFormatException undated)
							{
								//An undated tweet has no month.
							}
						}
					}
				}
				line.reset();
				start = position;
				if (next == -1)
				{
					break;
				}
			}
		}
		int n = userList.size();
		TreeSet<String> vocabulary = new TreeSet<String>();
		for (HashMap<String,int[]> map : counts)
		{
			vocabulary.addAll(map.keySet());
		}
		String[] terms = vocabulary.toArray(new String[0]);
		HashMap<String,Integer> termIndex = new HashMap<String,Integer>();
		for (int t = 0; t < terms.length; t++)
		{
			termIndex.put(terms[t], Integer.valueOf(t));
		}
		int[] df = new int[terms.length];
		int nonZeros = 0;
		for (HashMap<String,int[]> map : counts)
		{
			for (String term : map.keySet())
			{
				df[termIndex.get(term).intValue()]++;
			}
			nonZeros += map.size();
		}
		int[] pointer = new int[n + 1];
		int[] column = new int[nonZeros];
		int[] count = new int[nonZeros];
		double[] weight = new double[nonZeros];
		int p = 0;
		for (int u = 0; u < n; u++)
		{
			pointer[u] = p;
			int[] ids = new int[counts.get(u).size()];
			int i = 0;
			for (String term : counts.get(u).keySet())
			{
				ids[i++] = termIndex.get(term).intValue();
			}
			Arrays.sort(ids);
			double norm = 0.0;
			int start = p;
			for (int t : ids)
			{
				int tf = counts.get(u).get(terms[t])[0];
				//The agents' formula: raw tf times log2(N / df).
				double w = tf * (Math.log10(n / (double)df[t]) / Math.log10(2));
				column[p] = t;
				count[p] = tf;
				weight[p] = Double.isNaN(w) ? 0.0 : w;
				norm += weight[p] * weight[p];
				p++;
			}
			norm = Math.sqrt(norm);
			for (int q = start; q < p; q++)
			{
				weight[q] = norm > 0.0 ? weight[q] / norm : 0.0;
			}
		}
		pointer[n] = p;
		long[] offset = new long[n];
		int[] lineCount = new int[n];
		for (int u = 0; u < n; u++)
		{
			offset[u] = spans.get(u)[0];
			lineCount[u] = (int)spans.get(u)[1];
		}
		return new EvidenceCorpus(file, userList.toArray(new String[0]), followeeList.toArray(new String[0]), terms,
				df, pointer, column, count, weight, offset, lineCount, months, tweets);
	}

	// ============================================================ access

	double weightOf(int user, int term)
	{
		int position = Arrays.binarySearch(column, pointer[user], pointer[user + 1], term);
		return position >= 0 ? weight[position] : 0.0;
	}

	int countOf(int user, int term)
	{
		int position = Arrays.binarySearch(column, pointer[user], pointer[user + 1], term);
		return position >= 0 ? count[position] : 0;
	}

	/** Terms with a positive weight in a user's document. */
	int usableTerms(int user)
	{
		int usable = 0;
		for (int p = pointer[user]; p < pointer[user + 1]; p++)
		{
			usable += weight[p] > 0.0 ? 1 : 0;
		}
		return usable;
	}

	/** Exact cosine of a user with every user (the Similarity algorithm's score). */
	double[] cosines(int user)
	{
		double[] scores = new double[users.length];
		for (int p = pointer[user]; p < pointer[user + 1]; p++)
		{
			double a = weight[p];
			if (a <= 0.0)
			{
				continue;
			}
			int t = column[p];
			for (int q = postingPointer[t]; q < postingPointer[t + 1]; q++)
			{
				scores[postingUser[q]] += a * postingWeight[q];
			}
		}
		scores[user] = 0.0;
		return scores;
	}

	/** A user's tweets, read back from the file. */
	List<Tweet> tweetsOf(int user) throws IOException
	{
		ArrayList<Tweet> result = new ArrayList<Tweet>();
		try (java.nio.channels.FileChannel channel = java.nio.channels.FileChannel.open(file.toPath(),
				java.nio.file.StandardOpenOption.READ))
		{
			channel.position(offset[user]);
			InputStream input = new BufferedInputStream(java.nio.channels.Channels.newInputStream(channel), 1 << 15);
			ByteArrayOutputStream line = new ByteArrayOutputStream(256);
			int read = 0;
			while (read < lines[user])
			{
				int next = input.read();
				if (next != '\n' && next != -1)
				{
					line.write(next);
					continue;
				}
				String text = new String(line.toByteArray(), StandardCharsets.UTF_8);
				line.reset();
				String[] fields = text.split("\t", 4);
				if (fields.length == 4)
				{
					long time = Long.MIN_VALUE;
					if (fields[2].length() > 0)
					{
						try
						{
							time = Long.parseLong(fields[2]);
						}
						catch (NumberFormatException undated)
						{
							//Undated.
						}
					}
					result.add(new Tweet(time, fields[3]));
				}
				read++;
				if (next == -1)
				{
					break;
				}
			}
		}
		return result;
	}
}
