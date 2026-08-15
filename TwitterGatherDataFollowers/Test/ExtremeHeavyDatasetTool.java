package TwitterGatherDataFollowers.userRyersonU;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Generates and validates an extreme journal-style workload intended for
 * long-running parallel demonstrations rather than ordinary experiments.
 */
public final class ExtremeHeavyDatasetTool
{
	private static final int CLASS_COUNT = 16;
	private static final int USERS_PER_CLASS = 30;
	private static final int ROWS_PER_USER = 512;
	private static final int GROUP_SIZE = 4;
	private static final int COHORTS_PER_CLASS = 5;

	private static final int CLASS_POOL_SIZE = 4096;
	private static final int GROUP_POOL_SIZE = 4096;
	private static final int COHORT_POOL_SIZE = 1024;
	private static final int SHARED_LOAD_POOL_SIZE = 98304;

	private static final int CLASS_TERMS_PER_ROW = 32;
	private static final int GROUP_TERMS_PER_ROW = 16;
	private static final int COHORT_TERMS_PER_ROW = 16;
	private static final int SHARED_LOAD_TERMS_PER_ROW = 192;
	private static final int TOKENS_PER_ROW =
			CLASS_TERMS_PER_ROW + GROUP_TERMS_PER_ROW
			+ COHORT_TERMS_PER_ROW + SHARED_LOAD_TERMS_PER_ROW;

	private static final int EXPECTED_USER_COUNT =
			CLASS_COUNT * USERS_PER_CLASS;
	private static final long EXPECTED_ROW_COUNT =
			(long)EXPECTED_USER_COUNT * ROWS_PER_USER;
	private static final long EXPECTED_TOKEN_COUNT =
			EXPECTED_ROW_COUNT * TOKENS_PER_ROW;
	private static final int EXPECTED_VOCABULARY =
			CLASS_COUNT * CLASS_POOL_SIZE
			+ (CLASS_COUNT / GROUP_SIZE) * GROUP_POOL_SIZE
			+ CLASS_COUNT * COHORTS_PER_CLASS * COHORT_POOL_SIZE
			+ SHARED_LOAD_POOL_SIZE;
	private static final int EXPECTED_TERMS_PER_USER =
			CLASS_POOL_SIZE + GROUP_POOL_SIZE + COHORT_POOL_SIZE
			+ SHARED_LOAD_POOL_SIZE;
	private static final File DEFAULT_FILE = new File(
			"Dataset/Benchmarks/Extreme-15-Minute-Plus-Journal-245k.txt");

	private ExtremeHeavyDatasetTool()
	{
	}

	public static void main(String[] args) throws Exception
	{
		String command = args.length == 0 ? "generate" : args[0];
		File file = args.length > 1 ? new File(args[1]) : DEFAULT_FILE;
		if ("generate".equalsIgnoreCase(command))
		{
			generate(file);
			validate(file);
			return;
		}
		if ("validate".equalsIgnoreCase(command))
		{
			validate(file);
			return;
		}
		throw new IllegalArgumentException(
				"Usage: ExtremeHeavyDatasetTool [generate|validate] [dataset]");
	}

	private static void generate(File file) throws Exception
	{
		File parent = file.getParentFile();
		if (parent != null && !parent.exists() && !parent.mkdirs())
		{
			throw new IllegalStateException(
					"Could not create dataset directory: " + parent);
		}

		long rowIndex = 0L;
		try (BufferedWriter writer = new BufferedWriter(
				new OutputStreamWriter(
						new FileOutputStream(file),
						StandardCharsets.UTF_8),
				1024 * 1024))
		{
			for (int classIndex = 0; classIndex < CLASS_COUNT; classIndex++)
			{
				for (int memberIndex = 0;
						memberIndex < USERS_PER_CLASS; memberIndex++)
				{
					int userIndex = classIndex * USERS_PER_CLASS + memberIndex;
					int groupIndex = classIndex / GROUP_SIZE;
					int cohortIndex = memberIndex % COHORTS_PER_CLASS;
					String followee = String.format(
							"ExtremeJournal%02d", classIndex);
					String user = String.format(
							"ExtremeAuthorC%02dM%02d",
							classIndex, memberIndex);
					long userId = 3000000L + userIndex;

					for (int row = 0; row < ROWS_PER_USER; row++)
					{
						StringBuilder text = new StringBuilder(4800);
						appendTerms(text, "classword",
								classIndex * CLASS_POOL_SIZE,
								CLASS_POOL_SIZE,
								row * CLASS_TERMS_PER_ROW,
								CLASS_TERMS_PER_ROW);
						appendTerms(text, "groupword",
								groupIndex * GROUP_POOL_SIZE,
								GROUP_POOL_SIZE,
								row * GROUP_TERMS_PER_ROW,
								GROUP_TERMS_PER_ROW);
						appendTerms(text, "cohortword",
								(classIndex * COHORTS_PER_CLASS + cohortIndex)
										* COHORT_POOL_SIZE,
								COHORT_POOL_SIZE,
								row * COHORT_TERMS_PER_ROW,
								COHORT_TERMS_PER_ROW);
						appendTerms(text, "loadword",
								0,
								SHARED_LOAD_POOL_SIZE,
								row * SHARED_LOAD_TERMS_PER_ROW,
								SHARED_LOAD_TERMS_PER_ROW);

						long tweetId = 9100000000000000L + rowIndex;
						String date = String.format(
								"2024-%02d-%02d 12:%02d:00",
								1 + classIndex % 12,
								1 + (memberIndex + row) % 28,
								(userIndex * 7 + row) % 60);
						writer.write(followee);
						writer.write('\t');
						writer.write(Long.toString(tweetId));
						writer.write('\t');
						writer.write(date);
						writer.write('\t');
						writer.write(Long.toString(userId));
						writer.write('\t');
						writer.write(user);
						writer.write('\t');
						writer.write(text.toString());
						writer.newLine();
						rowIndex++;
					}
				}
			}
		}
		System.out.println(
				"Generated " + file.getPath() + " with " + rowIndex
				+ " rows and " + EXPECTED_TOKEN_COUNT + " text tokens.");
	}

	private static void validate(File file) throws Exception
	{
		LinkedHashMap<String,String> userClasses =
				new LinkedHashMap<String,String>();
		TreeMap<String,Integer> classUsers = new TreeMap<String,Integer>();
		TreeSet<String> vocabulary = new TreeSet<String>();
		TreeMap<String,LinkedHashSet<String>> sampleTerms =
				new TreeMap<String,LinkedHashSet<String>>();
		for (String sample : sampleUsers())
		{
			sampleTerms.put(sample, new LinkedHashSet<String>());
		}

		String currentUser = null;
		String currentClass = null;
		int currentRows = 0;
		LinkedHashSet<String> currentTerms = new LinkedHashSet<String>();
		long rows = 0L;
		long tokens = 0L;

		try (BufferedReader reader = reader(file))
		{
			String line;
			while ((line = reader.readLine()) != null)
			{
				DatasetRow row = DatasetRow.parse(line, ++rows);
				if (currentUser == null || !currentUser.equals(row.userName))
				{
					if (currentUser != null)
					{
						validateCompletedUser(
								currentUser, currentClass, currentRows, currentTerms);
					}
					currentUser = row.userName;
					currentClass = row.referenceUser;
					currentRows = 0;
					currentTerms.clear();
					String oldClass = userClasses.put(
							row.userName, row.referenceUser);
					require(oldClass == null,
							"non-contiguous or duplicate user block: " + row.userName);
					increment(classUsers, row.referenceUser);
				}
				require(currentClass.equals(row.referenceUser),
						"user changed journals inside its row block: " + row.userName);
				currentRows++;

				String[] rowTokens = row.text.split(" ");
				require(rowTokens.length == TOKENS_PER_ROW,
						"unexpected token count on row " + rows);
				for (String token : rowTokens)
				{
					require(token.matches("[a-z]+"),
							"token will not survive cleaning on row "
							+ rows + ": " + token);
					vocabulary.add(token);
					currentTerms.add(token);
					Set<String> sample = sampleTerms.get(row.userName);
					if (sample != null)
					{
						sample.add(token);
					}
					tokens++;
				}
			}
		}
		if (currentUser != null)
		{
			validateCompletedUser(
					currentUser, currentClass, currentRows, currentTerms);
		}

		require(rows == EXPECTED_ROW_COUNT,
				"unexpected row count: " + rows);
		require(tokens == EXPECTED_TOKEN_COUNT,
				"unexpected token count: " + tokens);
		require(userClasses.size() == EXPECTED_USER_COUNT,
				"unexpected user count: " + userClasses.size());
		require(classUsers.size() == CLASS_COUNT,
				"unexpected journal count: " + classUsers.size());
		require(vocabulary.size() == EXPECTED_VOCABULARY,
				"unexpected vocabulary size: " + vocabulary.size());
		for (Map.Entry<String,Integer> entry : classUsers.entrySet())
		{
			require(entry.getValue() == USERS_PER_CLASS,
					"unbalanced journal " + entry.getKey());
		}
		validateEightNodeClassCoverage(userClasses);
		validateSemanticStructure(sampleTerms);

		System.out.println(
				"Validated " + file.getPath() + ": "
				+ rows + " rows, " + tokens + " tokens, "
				+ userClasses.size() + " users, " + classUsers.size()
				+ " balanced journals, " + vocabulary.size()
				+ " cleaned vocabulary terms, " + EXPECTED_TERMS_PER_USER
				+ " unique terms per user.");
	}

	private static void validateCompletedUser(
			String user,
			String journal,
			int rows,
			Set<String> terms)
	{
		require(journal != null && journal.length() > 0,
				"missing journal for " + user);
		require(rows == ROWS_PER_USER,
				"unexpected rows for " + user + ": " + rows);
		require(terms.size() == EXPECTED_TERMS_PER_USER,
				"unexpected document vocabulary for " + user
				+ ": " + terms.size());
	}

	private static List<String> sampleUsers()
	{
		List<String> users = new ArrayList<String>();
		users.add("ExtremeAuthorC00M00");
		users.add("ExtremeAuthorC00M05");
		users.add("ExtremeAuthorC01M00");
		users.add("ExtremeAuthorC08M00");
		return users;
	}

	private static void validateEightNodeClassCoverage(
			LinkedHashMap<String,String> userClasses)
	{
		List<Set<String>> nodeClasses = new ArrayList<Set<String>>();
		for (int node = 0; node < 8; node++)
		{
			nodeClasses.add(new TreeSet<String>());
		}
		int userIndex = 0;
		for (String journal : userClasses.values())
		{
			nodeClasses.get(userIndex % 8).add(journal);
			userIndex++;
		}
		for (int node = 0; node < nodeClasses.size(); node++)
		{
			require(nodeClasses.get(node).size() == CLASS_COUNT,
					"node " + (node + 1)
					+ " does not receive every journal class");
		}
	}

	private static void validateSemanticStructure(
			Map<String,LinkedHashSet<String>> sampleTerms)
	{
		Set<String> target = sampleTerms.get("ExtremeAuthorC00M00");
		Set<String> sameJournal = sampleTerms.get("ExtremeAuthorC00M05");
		Set<String> sameGroup = sampleTerms.get("ExtremeAuthorC01M00");
		Set<String> unrelated = sampleTerms.get("ExtremeAuthorC08M00");
		int sameJournalOverlap = overlap(target, sameJournal);
		int sameGroupOverlap = overlap(target, sameGroup);
		int unrelatedOverlap = overlap(target, unrelated);
		require(sameJournalOverlap > sameGroupOverlap,
				"same-journal documents are not more related than group peers");
		require(sameGroupOverlap > unrelatedOverlap,
				"group-related documents are not more related than unrelated peers");
		System.out.println(
				"Semantic overlap check: same journal=" + sameJournalOverlap
				+ ", same group=" + sameGroupOverlap
				+ ", unrelated=" + unrelatedOverlap + ".");
	}

	private static int overlap(Set<String> left, Set<String> right)
	{
		int overlap = 0;
		for (String value : left)
		{
			if (right.contains(value))
			{
				overlap++;
			}
		}
		return overlap;
	}

	private static void appendTerms(
			StringBuilder text,
			String prefix,
			int base,
			int range,
			int offset,
			int count)
	{
		for (int index = 0; index < count; index++)
		{
			appendToken(
					text,
					prefix + letters(base + Math.floorMod(offset + index, range)));
		}
	}

	private static void appendToken(StringBuilder text, String token)
	{
		if (text.length() > 0)
		{
			text.append(' ');
		}
		text.append(token);
	}

	private static String letters(int value)
	{
		StringBuilder result = new StringBuilder();
		int remaining = value;
		do
		{
			result.append((char)('a' + remaining % 26));
			remaining = remaining / 26;
		}
		while (remaining > 0);
		return result.reverse().toString();
	}

	private static BufferedReader reader(File file) throws Exception
	{
		return new BufferedReader(
				new InputStreamReader(
						new FileInputStream(file),
						StandardCharsets.UTF_8),
				1024 * 1024);
	}

	private static <K> void increment(Map<K,Integer> counts, K key)
	{
		Integer old = counts.get(key);
		counts.put(key, old == null ? 1 : old + 1);
	}

	private static void require(boolean condition, String message)
	{
		if (!condition)
		{
			throw new AssertionError(message);
		}
	}
}
