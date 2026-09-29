package TwitterGatherDataFollowers.userRyersonU;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.math3.stat.regression.SimpleRegression;
import org.apache.commons.math3.util.Precision;

public final class UserGenerationEngine
{
	private static final int MIN_WORDS = 3;
	private static final int CLUSTER_COUNT = 3;
	private static final int MAX_CLUSTER_ITERATIONS = 10;
	private static final long MINIMUM_FREE_SPACE_BYTES = 64L * 1024L * 1024L;
	private static final Set<String> STOP_WORDS = createStopWords();

	public interface ProgressListener
	{
		void onProgress(String stage, String detail, long completed, long total);
	}

	public static final class Options
	{
		private final File sourceFile;
		private final File outputDirectory;
		private final long requestedTweetCount;
		private final long randomSeed;

		public Options(
				File sourceFile,
				File outputDirectory,
				long requestedTweetCount,
				long randomSeed)
		{
			this.sourceFile = sourceFile;
			this.outputDirectory = outputDirectory;
			this.requestedTweetCount = requestedTweetCount;
			this.randomSeed = randomSeed;
		}
	}

	public static final class ClusterResult
	{
		private final int clusterId;
		private final List<String> userNames;

		private ClusterResult(int clusterId, List<String> userNames)
		{
			this.clusterId = clusterId;
			this.userNames = Collections.unmodifiableList(
					new ArrayList<String>(userNames));
		}

		public int getClusterId()
		{
			return clusterId;
		}

		public List<String> getUserNames()
		{
			return userNames;
		}
	}

	public static final class Result
	{
		private final File outputFile;
		private final long sourceRows;
		private final long processedRows;
		private final long generatedRows;
		private final int generatedUsers;
		private final int generatedVocabularySize;
		private final long elapsedMillis;
		private final long randomSeed;
		private final List<ClusterResult> clusters;
		private final List<String> warnings;

		private Result(
				File outputFile,
				long sourceRows,
				long processedRows,
				long generatedRows,
				int generatedUsers,
				int generatedVocabularySize,
				long elapsedMillis,
				long randomSeed,
				List<ClusterResult> clusters,
				List<String> warnings)
		{
			this.outputFile = outputFile;
			this.sourceRows = sourceRows;
			this.processedRows = processedRows;
			this.generatedRows = generatedRows;
			this.generatedUsers = generatedUsers;
			this.generatedVocabularySize = generatedVocabularySize;
			this.elapsedMillis = elapsedMillis;
			this.randomSeed = randomSeed;
			this.clusters = Collections.unmodifiableList(
					new ArrayList<ClusterResult>(clusters));
			this.warnings = Collections.unmodifiableList(
					new ArrayList<String>(warnings));
		}

		public File getOutputFile()
		{
			return outputFile;
		}

		public long getSourceRows()
		{
			return sourceRows;
		}

		public long getProcessedRows()
		{
			return processedRows;
		}

		public long getGeneratedRows()
		{
			return generatedRows;
		}

		public int getGeneratedUsers()
		{
			return generatedUsers;
		}

		public int getGeneratedVocabularySize()
		{
			return generatedVocabularySize;
		}

		public long getElapsedMillis()
		{
			return elapsedMillis;
		}

		public long getRandomSeed()
		{
			return randomSeed;
		}

		public List<ClusterResult> getClusters()
		{
			return clusters;
		}

		public List<String> getWarnings()
		{
			return warnings;
		}
	}

	private static final class ProcessedRecord
	{
		private final String referenceUser;
		private final long tweetId;
		private final String tweetDate;
		private final long userId;
		private final String userName;

		private ProcessedRecord(DatasetRow row)
		{
			referenceUser = row.referenceUser;
			tweetId = row.tweetId;
			tweetDate = row.tweetDate;
			userId = row.userId;
			userName = row.userName;
		}
	}

	private static final class UserModel
	{
		private final String userName;
		private final String referenceUser;
		private final long userId;
		private final List<ProcessedRecord> records =
				new ArrayList<ProcessedRecord>();
		private Map<String, Integer> wordCounts =
				new HashMap<String, Integer>();
		private SamplingBins samplingBins;

		private UserModel(DatasetRow row)
		{
			userName = row.userName;
			referenceUser = row.referenceUser;
			userId = row.userId;
		}
	}

	private static final class FolloweeModel
	{
		private final String name;
		private final LinkedHashSet<String> users =
				new LinkedHashSet<String>();
		private final TreeSet<String> uniqueTerms = new TreeSet<String>();
		private long tweetCount;
		private long totalWordCount;
		private int maximumGeneratedWords;
		private int[] globalTermIds;
		private double shape;
		private double scale;

		private FolloweeModel(String name)
		{
			this.name = name;
		}
	}

	private static final class SourceModel
	{
		private final LinkedHashMap<String, UserModel> users =
				new LinkedHashMap<String, UserModel>();
		private final LinkedHashMap<String, FolloweeModel> followees =
				new LinkedHashMap<String, FolloweeModel>();
		private final List<ProcessedRecord> processedRecords =
				new ArrayList<ProcessedRecord>();
		private final List<String> warnings = new ArrayList<String>();
		private long sourceRows;
		private String[] globalVocabulary;
	}

	private static final class SamplingBins
	{
		private final double[] values;
		private final int[][] termIds;
		private final int zeroBinIndex;
		private final int[] groupTermIds;
		private final BitSet nonZeroLocalTerms;
		private final int[] explicitZeroTermIds;

		private SamplingBins(
				double[] values,
				int[][] termIds,
				int zeroBinIndex,
				int[] groupTermIds,
				BitSet nonZeroLocalTerms,
				int[] explicitZeroTermIds)
		{
			this.values = values;
			this.termIds = termIds;
			this.zeroBinIndex = zeroBinIndex;
			this.groupTermIds = groupTermIds;
			this.nonZeroLocalTerms = nonZeroLocalTerms;
			this.explicitZeroTermIds = explicitZeroTermIds;
		}

		private int sampleTerm(double sampledValue, Random random)
		{
			int position = Arrays.binarySearch(values, sampledValue);
			if (position < 0)
			{
				int insertionPoint = -position - 1;
				if (insertionPoint == 0)
				{
					position = 0;
				}
				else if (insertionPoint == values.length)
				{
					position = values.length - 1;
				}
				else
				{
					double lowerDifference =
							Math.abs(sampledValue - values[insertionPoint - 1]);
					double upperDifference =
							Math.abs(sampledValue - values[insertionPoint]);
					position = lowerDifference <= upperDifference
							? insertionPoint - 1
							: insertionPoint;
				}
			}

			if (position == zeroBinIndex)
			{
				if (explicitZeroTermIds != null)
				{
					return explicitZeroTermIds[
							random.nextInt(explicitZeroTermIds.length)];
				}

				int localIndex;
				do
				{
					localIndex = random.nextInt(groupTermIds.length);
				}
				while (nonZeroLocalTerms.get(localIndex));
				return groupTermIds[localIndex];
			}

			int[] candidates = termIds[position];
			return candidates[random.nextInt(candidates.length)];
		}
	}

	private static final class IntArrayBuilder
	{
		private int[] values = new int[8];
		private int size;

		private void add(int value)
		{
			if (size == values.length)
			{
				values = Arrays.copyOf(values, values.length * 2);
			}
			values[size++] = value;
		}

		private int[] toArray()
		{
			return Arrays.copyOf(values, size);
		}
	}

	private static final class IntLongCounter
	{
		private int[] keys = new int[16];
		private long[] values = new long[16];
		private boolean[] occupied = new boolean[16];
		private int size;

		private void increment(int key)
		{
			if ((size + 1) * 10 >= keys.length * 7)
			{
				resize(keys.length * 2);
			}

			int slot = findSlot(key, keys, occupied);
			if (!occupied[slot])
			{
				occupied[slot] = true;
				keys[slot] = key;
				size++;
			}
			values[slot]++;
		}

		private SparseCounts compact()
		{
			Integer[] order = new Integer[size];
			int index = 0;
			for (int i = 0; i < occupied.length; i++)
			{
				if (occupied[i])
				{
					order[index++] = Integer.valueOf(i);
				}
			}
			Arrays.sort(order, new Comparator<Integer>() {
				public int compare(Integer left, Integer right)
				{
					return Integer.compare(keys[left.intValue()], keys[right.intValue()]);
				}
			});

			int[] compactKeys = new int[size];
			long[] compactValues = new long[size];
			for (int i = 0; i < order.length; i++)
			{
				int slot = order[i].intValue();
				compactKeys[i] = keys[slot];
				compactValues[i] = values[slot];
			}
			return new SparseCounts(compactKeys, compactValues);
		}

		private void resize(int newCapacity)
		{
			int[] oldKeys = keys;
			long[] oldValues = values;
			boolean[] oldOccupied = occupied;
			keys = new int[newCapacity];
			values = new long[newCapacity];
			occupied = new boolean[newCapacity];

			for (int i = 0; i < oldOccupied.length; i++)
			{
				if (oldOccupied[i])
				{
					int slot = findSlot(oldKeys[i], keys, occupied);
					occupied[slot] = true;
					keys[slot] = oldKeys[i];
					values[slot] = oldValues[i];
				}
			}
		}

		private static int findSlot(int key, int[] tableKeys, boolean[] tableOccupied)
		{
			int mask = tableKeys.length - 1;
			int slot = mix(key) & mask;
			while (tableOccupied[slot] && tableKeys[slot] != key)
			{
				slot = (slot + 1) & mask;
			}
			return slot;
		}

		private static int mix(int value)
		{
			value ^= value >>> 16;
			value *= 0x7feb352d;
			value ^= value >>> 15;
			value *= 0x846ca68b;
			return value ^ (value >>> 16);
		}
	}

	private static final class SparseCounts
	{
		private final int[] termIds;
		private final long[] counts;

		private SparseCounts(int[] termIds, long[] counts)
		{
			this.termIds = termIds;
			this.counts = counts;
		}
	}

	private static final class SparseVector
	{
		private final int[] termIds;
		private final double[] values;

		private SparseVector(int[] termIds, double[] values)
		{
			this.termIds = termIds;
			this.values = values;
		}

		private double dot(double[] denseVector)
		{
			double result = 0.0;
			for (int i = 0; i < termIds.length; i++)
			{
				result += values[i] * denseVector[termIds[i]];
			}
			return result;
		}

		private void addTo(double[] denseVector)
		{
			for (int i = 0; i < termIds.length; i++)
			{
				denseVector[termIds[i]] += values[i];
			}
		}

		private void copyTo(double[] denseVector)
		{
			Arrays.fill(denseVector, 0.0);
			addTo(denseVector);
		}
	}

	private final Options options;
	private final ProgressListener progressListener;
	private final Random random;
	private long lastProgressPercent = -1L;

	public UserGenerationEngine(Options options, ProgressListener progressListener)
	{
		if (options == null)
		{
			throw new IllegalArgumentException("Generation options are required.");
		}
		this.options = options;
		this.progressListener = progressListener;
		random = new Random(options.randomSeed);
	}

	public Result run() throws IOException
	{
		long startedAt = System.currentTimeMillis();
		validateOptions();
		SourceModel sourceModel = readAndPrepareSource();
		compileSamplingModels(sourceModel);

		long targetCount = options.requestedTweetCount == 0L
				? sourceModel.processedRecords.size()
				: options.requestedTweetCount;
		ensureOutputCapacity(sourceModel, targetCount);

		Path outputDirectory = options.outputDirectory.toPath();
		Files.createDirectories(outputDirectory);
		String sourceName = removeExtension(options.sourceFile.getName());
		String timestamp = new SimpleDateFormat(
				"yyyy-MM-dd HH-mm-ss-SSS", Locale.ROOT).format(new Date());
		String runToken = UUID.randomUUID().toString().substring(0, 8);
		String outputName = sourceName + "_" + timestamp + "-"
				+ runToken + "_GENERATED.txt";
		Path finalPath = outputDirectory.resolve(outputName);
		Path temporaryPath = outputDirectory.resolve(
				"." + outputName + "." + UUID.randomUUID().toString() + ".part");

		try
		{
			GenerationData generationData = generateFile(
					sourceModel, targetCount, temporaryPath);
			report("VALIDATING", "Building sparse TF-IDF validation vectors.", 0L, 0L);
			LinkedHashMap<String, SparseVector> vectors =
					createGeneratedTfidfVectors(
							generationData.userCounts,
							generationData.documentFrequency);
			List<ClusterResult> clusters = clusterUsers(
					vectors, sourceModel.globalVocabulary.length);

			report("FINALIZING", "Verifying the generated dataset.", 0L, targetCount);
			long actualLineCount = countLines(temporaryPath);
			if (actualLineCount != targetCount)
			{
				throw new IOException(
						"Generated row verification failed: expected "
						+ targetCount + " rows but found " + actualLineCount + ".");
			}
			moveAtomically(temporaryPath, finalPath);

			long elapsed = System.currentTimeMillis() - startedAt;
			report(
					"COMPLETE",
					"Generated and verified " + targetCount + " rows.",
					targetCount,
					targetCount);
			return new Result(
					finalPath.toFile(),
					sourceModel.sourceRows,
					sourceModel.processedRecords.size(),
					targetCount,
					generationData.userCounts.size(),
					generationData.generatedVocabularySize,
					elapsed,
					options.randomSeed,
					clusters,
					sourceModel.warnings);
		}
		catch (Throwable failure)
		{
			try
			{
				Files.deleteIfExists(temporaryPath);
			}
			catch (IOException cleanupFailure)
			{
				failure.addSuppressed(cleanupFailure);
			}

			if (failure instanceof IOException)
			{
				throw (IOException) failure;
			}
			if (failure instanceof RuntimeException)
			{
				throw (RuntimeException) failure;
			}
			throw new IOException("User generation failed.", failure);
		}
	}

	static LinkedHashMap<String, Long> allocateExactly(
			LinkedHashMap<String, Integer> sourceCounts,
			long targetCount)
	{
		if (targetCount < 0L)
		{
			throw new IllegalArgumentException("Target count cannot be negative.");
		}
		if (sourceCounts == null || sourceCounts.isEmpty())
		{
			throw new IllegalArgumentException("At least one source user is required.");
		}

		long totalSourceCount = 0L;
		for (Integer count : sourceCounts.values())
		{
			if (count == null || count.intValue() <= 0)
			{
				throw new IllegalArgumentException(
						"Every source user must have at least one tweet.");
			}
			totalSourceCount += count.intValue();
		}

		final class Share
		{
			private final String user;
			private final int order;
			private final long remainder;

			private Share(String user, int order, long remainder)
			{
				this.user = user;
				this.order = order;
				this.remainder = remainder;
			}
		}

		LinkedHashMap<String, Long> allocation =
				new LinkedHashMap<String, Long>();
		List<Share> shares = new ArrayList<Share>();
		long assigned = 0L;
		int order = 0;
		for (Map.Entry<String, Integer> entry : sourceCounts.entrySet())
		{
			long product = targetCount * (long) entry.getValue().intValue();
			long base = product / totalSourceCount;
			long remainder = product % totalSourceCount;
			allocation.put(entry.getKey(), Long.valueOf(base));
			shares.add(new Share(entry.getKey(), order++, remainder));
			assigned += base;
		}

		Collections.sort(shares, new Comparator<Share>() {
			public int compare(Share left, Share right)
			{
				int remainderOrder = Long.compare(right.remainder, left.remainder);
				return remainderOrder != 0
						? remainderOrder
						: Integer.compare(left.order, right.order);
			}
		});

		long remaining = targetCount - assigned;
		for (int i = 0; i < remaining; i++)
		{
			Share share = shares.get(i);
			allocation.put(
					share.user,
					Long.valueOf(allocation.get(share.user).longValue() + 1L));
		}
		return allocation;
	}

	private void validateOptions() throws IOException
	{
		if (options.sourceFile == null)
		{
			throw new IllegalArgumentException("A source corpus is required.");
		}
		if (!options.sourceFile.isFile() || !options.sourceFile.canRead())
		{
			throw new IOException(
					"Source corpus is not a readable file: "
					+ options.sourceFile.getAbsolutePath());
		}
		if (options.outputDirectory == null)
		{
			throw new IllegalArgumentException("An output directory is required.");
		}
		if (options.requestedTweetCount < 0L)
		{
			throw new IllegalArgumentException(
					"The artificial tweet count cannot be negative.");
		}
		if (options.requestedTweetCount > Integer.MAX_VALUE)
		{
			throw new IllegalArgumentException(
					"The artificial tweet count cannot exceed "
					+ Integer.MAX_VALUE + " rows.");
		}
	}

	private SourceModel readAndPrepareSource() throws IOException
	{
		report("READING", "Reading and validating the source corpus.", 0L, 0L);
		SourceModel sourceModel = new SourceModel();
		Set<String> userIdWarnings = new HashSet<String>();

		try (BufferedReader reader = new BufferedReader(
				new java.io.InputStreamReader(
						new FileInputStream(options.sourceFile),
						StandardCharsets.UTF_8),
				128 * 1024))
		{
			String line;
			long lineNumber = 0L;
			while ((line = reader.readLine()) != null)
			{
				lineNumber++;
				if (line.trim().isEmpty())
				{
					throw new IllegalArgumentException(
							"Line " + lineNumber + " is blank.");
				}

				DatasetRow row = DatasetRow.parse(line, lineNumber);
				sourceModel.sourceRows++;
				List<String> words = preprocess(row.text);
				if (words.size() < MIN_WORDS)
				{
					continue;
				}

				UserModel user = sourceModel.users.get(row.userName);
				if (user == null)
				{
					user = new UserModel(row);
					sourceModel.users.put(row.userName, user);
				}
				else
				{
					if (!user.referenceUser.equals(row.referenceUser))
					{
						throw new IllegalArgumentException(
								"User " + row.userName
								+ " belongs to multiple reference users. "
								+ "The generation model requires one reference user per user.");
					}
					if (user.userId != row.userId
							&& userIdWarnings.add(row.userName))
					{
						sourceModel.warnings.add(
								"User " + row.userName
								+ " has multiple user IDs; the first ID was retained.");
					}
				}

				FolloweeModel followee = sourceModel.followees.get(row.referenceUser);
				if (followee == null)
				{
					followee = new FolloweeModel(row.referenceUser);
					sourceModel.followees.put(row.referenceUser, followee);
				}

				ProcessedRecord record = new ProcessedRecord(row);
				sourceModel.processedRecords.add(record);
				user.records.add(record);
				followee.users.add(row.userName);
				followee.tweetCount++;
				followee.totalWordCount += words.size();
				for (String word : words)
				{
					Integer count = user.wordCounts.get(word);
					user.wordCounts.put(
							word,
							Integer.valueOf(count == null ? 1 : count.intValue() + 1));
					followee.uniqueTerms.add(word);
				}

				if (lineNumber % 25000L == 0L)
				{
					report(
							"READING",
							"Validated " + lineNumber + " source rows.",
							lineNumber,
							0L);
				}
			}
		}

		if (sourceModel.sourceRows == 0L)
		{
			throw new IllegalArgumentException("The selected corpus is empty.");
		}
		if (sourceModel.processedRecords.isEmpty())
		{
			throw new IllegalArgumentException(
					"No source rows contain at least three usable words after preprocessing.");
		}

		for (FolloweeModel followee : sourceModel.followees.values())
		{
			if (followee.tweetCount == 0L || followee.uniqueTerms.isEmpty())
			{
				throw new IllegalArgumentException(
						"Reference user " + followee.name
						+ " has no usable source tweets.");
			}
			followee.maximumGeneratedWords = Math.max(
					MIN_WORDS,
					(int) Math.ceil(
							(double) followee.totalWordCount
							/ (double) followee.tweetCount));
		}

		report(
				"READING",
				"Prepared " + sourceModel.processedRecords.size()
				+ " usable rows across " + sourceModel.users.size() + " users.",
				sourceModel.sourceRows,
				sourceModel.sourceRows);
		return sourceModel;
	}

	private void compileSamplingModels(SourceModel sourceModel)
	{
		report("MODELING", "Building source TF-IDF generation models.", 0L, 0L);
		TreeSet<String> allTerms = new TreeSet<String>();
		for (FolloweeModel followee : sourceModel.followees.values())
		{
			allTerms.addAll(followee.uniqueTerms);
		}
		sourceModel.globalVocabulary = allTerms.toArray(new String[allTerms.size()]);
		Map<String, Integer> globalTermIds = new HashMap<String, Integer>(
				sourceModel.globalVocabulary.length * 2);
		for (int i = 0; i < sourceModel.globalVocabulary.length; i++)
		{
			globalTermIds.put(
					sourceModel.globalVocabulary[i],
					Integer.valueOf(i));
		}

		long modeledUsers = 0L;
		for (FolloweeModel followee : sourceModel.followees.values())
		{
			String[] groupVocabulary =
					followee.uniqueTerms.toArray(new String[followee.uniqueTerms.size()]);
			followee.globalTermIds = new int[groupVocabulary.length];
			for (int i = 0; i < groupVocabulary.length; i++)
			{
				followee.globalTermIds[i] =
						globalTermIds.get(groupVocabulary[i]).intValue();
			}

			Map<String, Integer> documentFrequency =
					new HashMap<String, Integer>();
			for (String userName : followee.users)
			{
				UserModel user = sourceModel.users.get(userName);
				for (String term : user.wordCounts.keySet())
				{
					Integer frequency = documentFrequency.get(term);
					documentFrequency.put(
							term,
							Integer.valueOf(
									frequency == null ? 1 : frequency.intValue() + 1));
				}
			}

			double shapeSum = 0.0;
			double scaleSum = 0.0;
			for (String userName : followee.users)
			{
				UserModel user = sourceModel.users.get(userName);
				double[] tfidfValues = new double[groupVocabulary.length];
				double magnitude = 0.0;
				for (int i = 0; i < groupVocabulary.length; i++)
				{
					String term = groupVocabulary[i];
					Integer termCount = user.wordCounts.get(term);
					int frequency = documentFrequency.get(term).intValue();
					double idf = log2(
							(double) followee.users.size() / (double) frequency);
					double value = termCount == null
							? 0.0
							: termCount.intValue() * idf;
					tfidfValues[i] = value;
					magnitude += value * value;
				}

				magnitude = Math.sqrt(magnitude);
				if (magnitude > 0.0 && Double.isFinite(magnitude))
				{
					for (int i = 0; i < tfidfValues.length; i++)
					{
						tfidfValues[i] /= magnitude;
					}
				}
				else
				{
					Arrays.fill(tfidfValues, 0.0);
				}

				user.samplingBins = buildSamplingBins(
						tfidfValues, followee.globalTermIds);
				double[] parameters = estimateWeibullParameters(tfidfValues);
				shapeSum += parameters[0];
				scaleSum += parameters[1];
				user.wordCounts = null;
				modeledUsers++;
				reportStageProgress(
						"MODELING",
						"Built generation models",
						modeledUsers,
						sourceModel.users.size());
			}

			followee.shape = shapeSum / followee.users.size();
			followee.scale = scaleSum / followee.users.size();
			if (!isPositiveFinite(followee.shape)
					|| !isPositiveFinite(followee.scale))
			{
				throw new IllegalStateException(
						"Could not calculate valid generation parameters for "
						+ followee.name + ".");
			}
		}
	}

	private GenerationData generateFile(
			SourceModel sourceModel,
			long targetCount,
			Path temporaryPath) throws IOException
	{
		report(
				"GENERATING",
				"Streaming " + targetCount + " artificial tweets to a verified temporary file.",
				0L,
				targetCount);

		LinkedHashMap<String, SparseCounts> generatedCounts =
				new LinkedHashMap<String, SparseCounts>();
		int[] documentFrequency =
				new int[sourceModel.globalVocabulary.length];
		BitSet generatedTerms = new BitSet(sourceModel.globalVocabulary.length);
		long generatedRows = 0L;

		try (FileOutputStream fileOutput = new FileOutputStream(
				temporaryPath.toFile(), false);
				BufferedWriter writer = new BufferedWriter(
						new OutputStreamWriter(fileOutput, StandardCharsets.UTF_8),
						128 * 1024))
		{
			if (options.requestedTweetCount == 0L)
			{
				LinkedHashMap<String, IntLongCounter> counters =
						new LinkedHashMap<String, IntLongCounter>();
				for (ProcessedRecord record : sourceModel.processedRecords)
				{
					UserModel user = sourceModel.users.get(record.userName);
					FolloweeModel followee =
							sourceModel.followees.get(record.referenceUser);
					IntLongCounter counter = counters.get(record.userName);
					if (counter == null)
					{
						counter = new IntLongCounter();
						counters.put(record.userName, counter);
					}

					String generatedText = generateTweet(
							user, followee, counter, sourceModel.globalVocabulary);
					writeRow(
							writer,
							record.referenceUser,
							record.tweetId,
							record.tweetDate,
							record.userId,
							record.userName,
							generatedText);
					generatedRows++;
					reportGenerationProgress(generatedRows, targetCount);
				}

				for (Map.Entry<String, IntLongCounter> entry : counters.entrySet())
				{
					storeCounts(
							entry.getKey(),
							entry.getValue().compact(),
							generatedCounts,
							documentFrequency,
							generatedTerms);
				}
			}
			else
			{
				LinkedHashMap<String, Integer> sourceCounts =
						new LinkedHashMap<String, Integer>();
				for (UserModel user : sourceModel.users.values())
				{
					sourceCounts.put(
							user.userName,
							Integer.valueOf(user.records.size()));
				}
				LinkedHashMap<String, Long> allocation =
						allocateExactly(sourceCounts, targetCount);
				long generatedTweetId = 1L;

				for (UserModel user : sourceModel.users.values())
				{
					long userTarget = allocation.get(user.userName).longValue();
					if (userTarget == 0L)
					{
						continue;
					}

					FolloweeModel followee =
							sourceModel.followees.get(user.referenceUser);
					IntLongCounter counter = new IntLongCounter();
					for (long i = 0L; i < userTarget; i++)
					{
						ProcessedRecord metadata = user.records.get(
								(int) (i % user.records.size()));
						String generatedText = generateTweet(
								user, followee, counter, sourceModel.globalVocabulary);
						writeRow(
								writer,
								user.referenceUser,
								generatedTweetId++,
								metadata.tweetDate,
								user.userId,
								user.userName,
								generatedText);
						generatedRows++;
						reportGenerationProgress(generatedRows, targetCount);
					}

					storeCounts(
							user.userName,
							counter.compact(),
							generatedCounts,
							documentFrequency,
							generatedTerms);
				}
			}

			if (generatedRows != targetCount)
			{
				throw new IOException(
						"Internal allocation error: expected " + targetCount
						+ " generated rows but wrote " + generatedRows + ".");
			}
			writer.flush();
			fileOutput.getFD().sync();
		}

		return new GenerationData(
				generatedCounts,
				documentFrequency,
				generatedTerms.cardinality());
	}

	private static final class GenerationData
	{
		private final LinkedHashMap<String, SparseCounts> userCounts;
		private final int[] documentFrequency;
		private final int generatedVocabularySize;

		private GenerationData(
				LinkedHashMap<String, SparseCounts> userCounts,
				int[] documentFrequency,
				int generatedVocabularySize)
		{
			this.userCounts = userCounts;
			this.documentFrequency = documentFrequency;
			this.generatedVocabularySize = generatedVocabularySize;
		}
	}

	private String generateTweet(
			UserModel user,
			FolloweeModel followee,
			IntLongCounter counter,
			String[] globalVocabulary)
	{
		int range = followee.maximumGeneratedWords - MIN_WORDS + 1;
		int wordCount = MIN_WORDS + random.nextInt(range);
		StringBuilder text = new StringBuilder(wordCount * 9);
		for (int i = 0; i < wordCount; i++)
		{
			double weibullValue = sampleWeibull(
					followee.shape, followee.scale, random);
			int termId = user.samplingBins.sampleTerm(weibullValue, random);
			counter.increment(termId);
			text.append(globalVocabulary[termId]).append(' ');
		}
		return text.toString();
	}

	private static void writeRow(
			BufferedWriter writer,
			String referenceUser,
			long tweetId,
			String tweetDate,
			long userId,
			String userName,
			String generatedText) throws IOException
	{
		writer.write(referenceUser);
		writer.write('\t');
		writer.write(Long.toString(tweetId));
		writer.write('\t');
		writer.write(tweetDate);
		writer.write('\t');
		writer.write(Long.toString(userId));
		writer.write('\t');
		writer.write(userName);
		writer.write('\t');
		writer.write(generatedText);
		writer.write("\r\n");
	}

	private static void storeCounts(
			String userName,
			SparseCounts counts,
			LinkedHashMap<String, SparseCounts> generatedCounts,
			int[] documentFrequency,
			BitSet generatedTerms)
	{
		generatedCounts.put(userName, counts);
		for (int termId : counts.termIds)
		{
			documentFrequency[termId]++;
			generatedTerms.set(termId);
		}
	}

	private LinkedHashMap<String, SparseVector> createGeneratedTfidfVectors(
			LinkedHashMap<String, SparseCounts> generatedCounts,
			int[] documentFrequency)
	{
		LinkedHashMap<String, SparseVector> vectors =
				new LinkedHashMap<String, SparseVector>();
		int totalDocuments = generatedCounts.size();
		long completed = 0L;
		for (Map.Entry<String, SparseCounts> entry : generatedCounts.entrySet())
		{
			SparseCounts counts = entry.getValue();
			int nonZeroValues = 0;
			double magnitude = 0.0;
			double[] unnormalized = new double[counts.termIds.length];
			for (int i = 0; i < counts.termIds.length; i++)
			{
				int termId = counts.termIds[i];
				int frequency = documentFrequency[termId];
				double value = frequency == 0
						? 0.0
						: counts.counts[i]
						* log2((double) totalDocuments / (double) frequency);
				unnormalized[i] = value;
				if (value != 0.0 && Double.isFinite(value))
				{
					nonZeroValues++;
					magnitude += value * value;
				}
			}

			int[] termIds = new int[nonZeroValues];
			double[] values = new double[nonZeroValues];
			magnitude = Math.sqrt(magnitude);
			int outputIndex = 0;
			if (magnitude > 0.0 && Double.isFinite(magnitude))
			{
				for (int i = 0; i < unnormalized.length; i++)
				{
					if (unnormalized[i] != 0.0
							&& Double.isFinite(unnormalized[i]))
					{
						termIds[outputIndex] = counts.termIds[i];
						values[outputIndex] = unnormalized[i] / magnitude;
						outputIndex++;
					}
				}
			}
			else
			{
				termIds = new int[0];
				values = new double[0];
			}

			vectors.put(entry.getKey(), new SparseVector(termIds, values));
			completed++;
			reportStageProgress(
					"VALIDATING",
					"Built validation vectors",
					completed,
					totalDocuments);
		}
		return vectors;
	}

	private List<ClusterResult> clusterUsers(
			LinkedHashMap<String, SparseVector> vectors,
			int vocabularySize)
	{
		if (vectors.isEmpty())
		{
			return Collections.emptyList();
		}

		List<String> users = new ArrayList<String>(vectors.keySet());
		int clusterCount = Math.min(CLUSTER_COUNT, users.size());
		List<String> seeds = chooseSeeds(users, clusterCount);
		double[][] centroids = new double[clusterCount][vocabularySize];
		for (int i = 0; i < clusterCount; i++)
		{
			vectors.get(seeds.get(i)).copyTo(centroids[i]);
		}

		int[] assignments = new int[users.size()];
		Arrays.fill(assignments, -1);
		for (int iteration = 0; iteration < MAX_CLUSTER_ITERATIONS; iteration++)
		{
			boolean changed = false;
			for (int userIndex = 0; userIndex < users.size(); userIndex++)
			{
				SparseVector vector = vectors.get(users.get(userIndex));
				int bestCluster = 0;
				double bestSimilarity = vector.dot(centroids[0]);
				for (int cluster = 1; cluster < clusterCount; cluster++)
				{
					double similarity = vector.dot(centroids[cluster]);
					if (similarity > bestSimilarity)
					{
						bestSimilarity = similarity;
						bestCluster = cluster;
					}
				}
				if (assignments[userIndex] != bestCluster)
				{
					assignments[userIndex] = bestCluster;
					changed = true;
				}
			}

			recalculateCentroids(
					users, vectors, assignments, centroids, clusterCount);
			if (!changed)
			{
				break;
			}
		}

		List<List<String>> clusterUsers = new ArrayList<List<String>>();
		for (int i = 0; i < clusterCount; i++)
		{
			clusterUsers.add(new ArrayList<String>());
		}
		for (int i = 0; i < users.size(); i++)
		{
			clusterUsers.get(assignments[i]).add(users.get(i));
		}

		List<ClusterResult> results = new ArrayList<ClusterResult>();
		for (int i = 0; i < clusterUsers.size(); i++)
		{
			results.add(new ClusterResult(i, clusterUsers.get(i)));
		}
		return results;
	}

	private List<String> chooseSeeds(List<String> users, int clusterCount)
	{
		List<String> remaining = new ArrayList<String>(users);
		List<String> seeds = new ArrayList<String>();
		String[] preferred = {"RyersonU", "TheCatTweeting", "weathernetwork"};
		for (String preferredUser : preferred)
		{
			if (seeds.size() == clusterCount)
			{
				break;
			}
			if (remaining.remove(preferredUser))
			{
				seeds.add(preferredUser);
			}
		}
		Collections.shuffle(remaining, random);
		while (seeds.size() < clusterCount)
		{
			seeds.add(remaining.remove(0));
		}
		return seeds;
	}

	private static void recalculateCentroids(
			List<String> users,
			LinkedHashMap<String, SparseVector> vectors,
			int[] assignments,
			double[][] centroids,
			int clusterCount)
	{
		double[][] sums = new double[clusterCount][centroids[0].length];
		int[] counts = new int[clusterCount];
		for (int i = 0; i < users.size(); i++)
		{
			int cluster = assignments[i];
			counts[cluster]++;
			vectors.get(users.get(i)).addTo(sums[cluster]);
		}

		for (int cluster = 0; cluster < clusterCount; cluster++)
		{
			if (counts[cluster] == 0)
			{
				continue;
			}
			double magnitude = 0.0;
			for (int term = 0; term < sums[cluster].length; term++)
			{
				sums[cluster][term] /= counts[cluster];
				magnitude += sums[cluster][term] * sums[cluster][term];
			}
			magnitude = Math.sqrt(magnitude);
			if (magnitude > 0.0 && Double.isFinite(magnitude))
			{
				for (int term = 0; term < sums[cluster].length; term++)
				{
					sums[cluster][term] /= magnitude;
				}
			}
			centroids[cluster] = sums[cluster];
		}
	}

	private static SamplingBins buildSamplingBins(
			double[] tfidfValues,
			int[] globalTermIds)
	{
		TreeMap<Double, IntArrayBuilder> positiveBins =
				new TreeMap<Double, IntArrayBuilder>();
		BitSet nonZeroLocalTerms = new BitSet(tfidfValues.length);
		int zeroCount = 0;
		for (int i = 0; i < tfidfValues.length; i++)
		{
			double value = tfidfValues[i];
			if (value > 0.0 && Double.isFinite(value))
			{
				nonZeroLocalTerms.set(i);
				Double key = Double.valueOf(value);
				IntArrayBuilder terms = positiveBins.get(key);
				if (terms == null)
				{
					terms = new IntArrayBuilder();
					positiveBins.put(key, terms);
				}
				terms.add(globalTermIds[i]);
			}
			else
			{
				zeroCount++;
			}
		}

		int binCount = positiveBins.size() + (zeroCount > 0 ? 1 : 0);
		double[] values = new double[binCount];
		int[][] termIds = new int[binCount][];
		int outputIndex = 0;
		int zeroBinIndex = -1;
		if (zeroCount > 0)
		{
			values[0] = 0.0;
			zeroBinIndex = 0;
			outputIndex = 1;
		}
		for (Map.Entry<Double, IntArrayBuilder> entry : positiveBins.entrySet())
		{
			values[outputIndex] = entry.getKey().doubleValue();
			termIds[outputIndex] = entry.getValue().toArray();
			outputIndex++;
		}

		int[] explicitZeroTermIds = null;
		if (zeroCount > 0 && zeroCount * 4 <= globalTermIds.length)
		{
			explicitZeroTermIds = new int[zeroCount];
			int zeroIndex = 0;
			for (int i = 0; i < globalTermIds.length; i++)
			{
				if (!nonZeroLocalTerms.get(i))
				{
					explicitZeroTermIds[zeroIndex++] = globalTermIds[i];
				}
			}
		}

		return new SamplingBins(
				values,
				termIds,
				zeroBinIndex,
				globalTermIds,
				nonZeroLocalTerms,
				explicitZeroTermIds);
	}

	private static double[] estimateWeibullParameters(double[] normalizedTfidf)
	{
		if (normalizedTfidf.length < 2)
		{
			return fallbackWeibullParameters(normalizedTfidf);
		}

		double[] observations = Arrays.copyOf(
				normalizedTfidf, normalizedTfidf.length);
		for (int i = 0; i < observations.length; i++)
		{
			if (!(observations[i] > 0.0) || !Double.isFinite(observations[i]))
			{
				observations[i] = 1E-8;
			}
		}

		double[] mle = calculateWeibullMle(observations);
		if (isPositiveFinite(mle[0]) && isPositiveFinite(mle[1]))
		{
			return mle;
		}

		double[] regression = calculateWeibullRegression(observations);
		if (isPositiveFinite(regression[0])
				&& isPositiveFinite(regression[1]))
		{
			return regression;
		}
		return fallbackWeibullParameters(observations);
	}

	private static double[] calculateWeibullMle(double[] observations)
	{
		try
		{
			int count = observations.length;
			double[] logarithms = new double[count];
			for (int i = 0; i < count; i++)
			{
				logarithms[i] = Math.log(observations[i]);
			}

			double logarithmSquareSum = 0.0;
			double logarithmSum = 0.0;
			for (double logarithm : logarithms)
			{
				logarithmSquareSum += logarithm * logarithm;
				logarithmSum += logarithm;
			}
			double varianceComponent =
					(logarithmSquareSum - logarithmSum * logarithmSum / count)
					/ (count - 1);
			double shape = Math.pow(
					(6.0 / (Math.PI * Math.PI)) * varianceComponent,
					-0.5);
			if (!isPositiveFinite(shape))
			{
				return new double[] {Double.NaN, Double.NaN};
			}

			for (int iteration = 0; iteration < 20; iteration++)
			{
				double powerSum = 0.0;
				double powerLogSum = 0.0;
				double powerLogSquareSum = 0.0;
				for (int i = 0; i < count; i++)
				{
					double powered = Math.pow(observations[i], shape);
					powerSum += powered;
					powerLogSum += powered * logarithms[i];
					powerLogSquareSum +=
							powered * logarithms[i] * logarithms[i];
				}
				double function =
						logarithmSum / count + 1.0 / shape
						- powerLogSum / powerSum;
				double derivative =
						-1.0 / (shape * shape)
						- (powerSum * powerLogSquareSum
						- powerLogSum * powerLogSum)
						/ (powerSum * powerSum);
				double nextShape = shape - function / derivative;
				if (!isPositiveFinite(nextShape))
				{
					return new double[] {Double.NaN, Double.NaN};
				}
				if (Precision.equals(nextShape, shape, 1E-8))
				{
					shape = nextShape;
					break;
				}
				shape = nextShape;
			}

			double poweredSum = 0.0;
			for (double observation : observations)
			{
				poweredSum += Math.pow(observation, shape);
			}
			double scale = Math.pow(poweredSum / count, 1.0 / shape);
			return new double[] {shape, scale};
		}
		catch (RuntimeException e)
		{
			return new double[] {Double.NaN, Double.NaN};
		}
	}

	private static double[] calculateWeibullRegression(double[] observations)
	{
		double[] sorted = Arrays.copyOf(observations, observations.length);
		Arrays.sort(sorted);
		SimpleRegression regression = new SimpleRegression();
		for (int i = 0; i < sorted.length; i++)
		{
			double x = Math.log(Math.max(sorted[i], 1E-8));
			double probability = (i + 1.0 - 0.5) / sorted.length;
			double y = Math.log(-Math.log(1.0 - probability));
			regression.addData(x, y);
		}
		double shape = regression.getSlope();
		double scale = Math.exp(-regression.getIntercept() / shape);
		return new double[] {shape, scale};
	}

	private static double[] fallbackWeibullParameters(double[] observations)
	{
		double sum = 0.0;
		int count = 0;
		for (double observation : observations)
		{
			if (observation > 0.0 && Double.isFinite(observation))
			{
				sum += observation;
				count++;
			}
		}
		double scale = count == 0 ? 1E-8 : Math.max(sum / count, 1E-8);
		return new double[] {1.0, scale};
	}

	private static double sampleWeibull(double shape, double scale, Random random)
	{
		double uniform = random.nextDouble();
		if (uniform <= 0.0)
		{
			uniform = Double.MIN_VALUE;
		}
		return scale * Math.pow(-Math.log1p(-uniform), 1.0 / shape);
	}

	private void ensureOutputCapacity(SourceModel sourceModel, long targetCount)
			throws IOException
	{
		Files.createDirectories(options.outputDirectory.toPath());
		long averageWordLength = 8L;
		if (sourceModel.globalVocabulary.length > 0)
		{
			long totalLength = 0L;
			for (String term : sourceModel.globalVocabulary)
			{
				totalLength += term.length() + 1L;
			}
			averageWordLength = Math.max(
					2L, totalLength / sourceModel.globalVocabulary.length);
		}

		double weightedWords = 0.0;
		long weightedRows = 0L;
		LinkedHashMap<String, Integer> sourceCounts =
				new LinkedHashMap<String, Integer>();
		for (UserModel user : sourceModel.users.values())
		{
			sourceCounts.put(user.userName, Integer.valueOf(user.records.size()));
		}
		LinkedHashMap<String, Long> allocation =
				allocateExactly(sourceCounts, targetCount);
		for (UserModel user : sourceModel.users.values())
		{
			long rows = allocation.get(user.userName).longValue();
			FolloweeModel followee = sourceModel.followees.get(user.referenceUser);
			double averageWords =
					(MIN_WORDS + followee.maximumGeneratedWords) / 2.0;
			weightedWords += rows * averageWords;
			weightedRows += rows;
		}

		double estimatedBytesDouble =
				weightedRows * 72.0 + weightedWords * averageWordLength;
		long estimatedBytes = estimatedBytesDouble >= Long.MAX_VALUE
				? Long.MAX_VALUE
				: (long) Math.ceil(estimatedBytesDouble);
		long usableSpace = options.outputDirectory.getUsableSpace();
		if (usableSpace > 0L
				&& estimatedBytes > usableSpace - MINIMUM_FREE_SPACE_BYTES)
		{
			throw new IOException(
					"Insufficient free disk space. Estimated output size is "
					+ humanBytes(estimatedBytes) + " with "
					+ humanBytes(usableSpace) + " available.");
		}
	}

	private static List<String> preprocess(String originalText)
	{
		//Shared with the simulator so generated corpora are built from the same
		//tokens the simulator will later extract from them.
		String text = TweetTextNormalizer.normalize(
				originalText,
				true,
				true,
				false,
				null);
		if (text.isEmpty())
		{
			return Collections.emptyList();
		}

		String[] tokens = text.split(" ");
		List<String> words = new ArrayList<String>(tokens.length);
		for (String token : tokens)
		{
			if (!token.isEmpty() && !STOP_WORDS.contains(token))
			{
				words.add(token);
			}
		}
		return words;
	}

	private static Set<String> createStopWords()
	{
		String words =
				"a able about above abst accordance according accordingly across act "
				+ "actually added adj affected affecting affects after afterwards again against "
				+ "ah all almost alone along already also although always am among amongst an "
				+ "and announce another any anybody anyhow anymore anyone anything anyway anyways "
				+ "anywhere apparently approximately are aren arent arise around as aside ask "
				+ "asking at auth available away awfully b back be became because become becomes "
				+ "becoming been before beforehand begin beginning beginnings begins behind being "
				+ "believe below beside besides between beyond biol both brief briefly but by c ca "
				+ "came can cannot cant cause causes certain certainly co com come comes contain "
				+ "containing contains could couldnt d da date did didnt different do does doesnt "
				+ "doing done dont down downwards due during e each ed edu effect eg eight eighty "
				+ "either else elsewhere end ending enough especially et etal etc even ever every "
				+ "everybody everyone everything everywhere ex except f far few ff fifth first five "
				+ "fix followed following follows for former formerly forth found four from further "
				+ "furthermore g gave get gets getting give given gives giving go goes gone got "
				+ "gotten h had happens hardly has hasnt have havent having he hed hence her here "
				+ "hereafter hereby herein heres hereupon hers herself hes hi hid him himself his "
				+ "hither home how howbeit however hundred i id idk ie if ill im immediate "
				+ "immediately importance important in inc indeed index information instead into "
				+ "invention inward is isnt it itd itll its itself ive j just k keep keeps kept kg "
				+ "km know known knows l largely last lately later latter latterly least less lest "
				+ "let lets like liked likely line little ll look looking looks ltd m made mainly "
				+ "make makes many may maybe me mean means meantime meanwhile merely mg might "
				+ "million miss ml more moreover most mostly mr mrs much mug must my myself n na "
				+ "name namely nay nd near nearly necessarily necessary need needs neither never "
				+ "nevertheless next new nine ninety no nobody non none nonetheless noone nor "
				+ "normally nos not noted nothing now nowhere o obtain obtained obviously of off "
				+ "often oh ok okay old omitted on once one ones only onto or ord other others "
				+ "otherwise ought our ours ourselves out outside over overall owing own p page "
				+ "pages part particular particularly past per perhaps placed please plus poorly "
				+ "possible possibly potential potentially pp predominantly present previous "
				+ "previously primarily probably promptly proud provides put q que quickly quite "
				+ "qv r ran rather rd re readily really recent recently ref refs regarding "
				+ "regardless regards related relatively research respectively resulted resulting "
				+ "results right rt run s said same saw say saying says sec section see seeing seem "
				+ "seemed seeming seems seen self selves sent seven several shall she shed shell "
				+ "shes should shouldnt show showed shown showns shows significant significantly "
				+ "similar similarly since six slightly so some somebody somehow someone somethan "
				+ "something sometime sometimes somewhat somewhere soon sorry specifically "
				+ "specified specify specifying still stop strongly sub substantially successfully "
				+ "such sufficient sufficiently suggest sup sure t take taken taking tell tends th "
				+ "than thank thanks thanx that thatll thats thatve the their theirs them themselves "
				+ "then thence there thereafter thereby thered therefore therein therell thereof "
				+ "therere theres thereto thereupon thereve these they theyd theyll theyre theyve "
				+ "think this those thou though thoughh thousand throug through throughout thru "
				+ "thus til tip to together too took toward towards tried tries truly try trying ts "
				+ "twice two ty u ull un under unfortunately unless unlike unlikely until unto up "
				+ "upon ups ur us use used useful usefully usefulness uses using usually v value "
				+ "various ve very via viz vol vols vs w want wants was wasnt way we wed welcome "
				+ "well went were werent weve what whatever whatll whats when whence whenever where "
				+ "whereafter whereas whereby wherein wheres whereupon wherever whether which while "
				+ "whim whither who whod whoever whole wholl whom whomever whos whose why widely "
				+ "willing wish with within without wont words world would wouldnt www x y yes yet "
				+ "yo you youd youll your youre yours yourself yourselves youve z zero";
		return Collections.unmodifiableSet(
				new HashSet<String>(Arrays.asList(words.split(" "))));
	}

	private static double log2(double value)
	{
		return Math.log(value) / Math.log(2.0);
	}

	private static boolean isPositiveFinite(double value)
	{
		return value > 0.0 && Double.isFinite(value);
	}

	private static String removeExtension(String fileName)
	{
		int extension = fileName.lastIndexOf('.');
		return extension <= 0 ? fileName : fileName.substring(0, extension);
	}

	private static long countLines(Path file) throws IOException
	{
		long lines = 0L;
		try (BufferedInputStream input = new BufferedInputStream(
				new FileInputStream(file.toFile()), 128 * 1024))
		{
			byte[] buffer = new byte[128 * 1024];
			int read;
			while ((read = input.read(buffer)) != -1)
			{
				for (int i = 0; i < read; i++)
				{
					if (buffer[i] == '\n')
					{
						lines++;
					}
				}
			}
		}
		return lines;
	}

	private static void moveAtomically(Path source, Path target) throws IOException
	{
		try
		{
			Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
		}
		catch (AtomicMoveNotSupportedException e)
		{
			Files.move(source, target);
		}
	}

	private void reportGenerationProgress(long completed, long total)
	{
		if (total <= 0L)
		{
			return;
		}
		long percent = completed * 100L / total;
		if (completed == total || percent > lastProgressPercent)
		{
			lastProgressPercent = percent;
			report(
					"GENERATING",
					"Generated " + completed + " of " + total
					+ " tweets (" + percent + "%).",
					completed,
					total);
		}
	}

	private void reportStageProgress(
			String stage,
			String description,
			long completed,
			long total)
	{
		if (total <= 0L)
		{
			return;
		}
		long percent = completed * 100L / total;
		long previousPercent = (completed - 1L) * 100L / total;
		if (completed == 1L || completed == total || percent > previousPercent)
		{
			report(
					stage,
					description + " for " + completed + " of " + total
					+ " users (" + percent + "%).",
					completed,
					total);
		}
	}

	private void report(
			String stage,
			String detail,
			long completed,
			long total)
	{
		if (progressListener != null)
		{
			progressListener.onProgress(stage, detail, completed, total);
		}
	}

	private static String humanBytes(long bytes)
	{
		double value = bytes;
		String[] units = {"B", "KB", "MB", "GB", "TB"};
		int unit = 0;
		while (value >= 1024.0 && unit < units.length - 1)
		{
			value /= 1024.0;
			unit++;
		}
		return String.format(Locale.ROOT, "%.1f %s", value, units[unit]);
	}
}
