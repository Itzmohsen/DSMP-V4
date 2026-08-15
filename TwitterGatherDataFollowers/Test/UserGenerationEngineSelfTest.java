package TwitterGatherDataFollowers.userRyersonU;

import java.io.BufferedReader;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;

public final class UserGenerationEngineSelfTest
{
	private UserGenerationEngineSelfTest()
	{
	}

	public static void main(String[] args) throws Exception
	{
		testExactAllocation();
		testRejectsUnsupportedExtremeCount();
		testMixedLineEndingsAndExactOutput();
		testZeroMeansUsableSourceCount();
		testRequestSmallerThanSource();
		testDeterministicSeed();
		testMalformedInputDoesNotPublishOutput();
		testGenerationFailureCleansTemporaryOutput();
		if (args.length > 0 && "--million".equals(args[0]))
		{
			testMillionRowGeneration();
		}
		else if (args.length == 3 && "--corpus".equals(args[0]))
		{
			testRealCorpus(new File(args[1]), Long.parseLong(args[2]));
		}
		System.out.println("UserGenerationEngineSelfTest passed");
	}

	private static void testExactAllocation()
	{
		LinkedHashMap<String, Integer> sourceCounts =
				new LinkedHashMap<String, Integer>();
		sourceCounts.put("alice", Integer.valueOf(3));
		sourceCounts.put("bob", Integer.valueOf(2));
		sourceCounts.put("carol", Integer.valueOf(1));

		LinkedHashMap<String, Long> allocation =
				UserGenerationEngine.allocateExactly(sourceCounts, 10L);
		require(allocation.get("alice").longValue() == 5L,
				"Alice did not receive her exact proportional allocation");
		require(allocation.get("bob").longValue() == 3L,
				"Bob did not receive his exact proportional allocation");
		require(allocation.get("carol").longValue() == 2L,
				"Carol did not receive the largest-remainder row");
		require(sum(allocation) == 10L,
				"Allocation did not equal the requested total");
	}

	private static void testRejectsUnsupportedExtremeCount() throws Exception
	{
		Path directory = Files.createTempDirectory("user-generation-extreme-");
		try
		{
			Path source = writeStandardSource(directory);
			boolean failed = false;
			try
			{
				run(
						source.toFile(),
						directory.resolve("out").toFile(),
						(long) Integer.MAX_VALUE + 1L,
						19L);
			}
			catch (IllegalArgumentException expected)
			{
				failed = true;
			}
			require(failed, "An unsupported extreme row count was accepted");
		}
		finally
		{
			deleteTree(directory);
		}
	}

	private static void testMixedLineEndingsAndExactOutput() throws Exception
	{
		Path directory = Files.createTempDirectory("user-generation-exact-");
		try
		{
			Path source = directory.resolve("source.txt");
			String content =
					"StoreA\t1\t2020-01-01 10:00:00\t101\talice\talpha bravo charlie delta\r\n"
					+ "StoreA\t2\t2020-01-02 10:00:00\t101\talice\talpha bravo echo foxtrot\n"
					+ "StoreA\t3\t2020-01-03 10:00:00\t202\tbob\tgolf hotel india juliet\r\n"
					+ "StoreA\t4\t2020-01-04 10:00:00\t202\tbob\tkilo lima mike november";
			Files.write(source, content.getBytes(StandardCharsets.UTF_8));

			UserGenerationEngine.Result result = run(
					source.toFile(), directory.resolve("out").toFile(), 11L, 17L);
			require(result.getSourceRows() == 4L,
					"Mixed line endings did not preserve every source row");
			require(result.getProcessedRows() == 4L,
					"Usable source rows were lost");
			require(result.getGeneratedRows() == 11L,
					"The generated result count was not exact");
			require(countAndValidateRows(result.getOutputFile().toPath()) == 11L,
					"The published file does not contain exactly eleven valid rows");
			require(noPartFiles(directory.resolve("out")),
					"A successful run left a temporary part file");
		}
		finally
		{
			deleteTree(directory);
		}
	}

	private static void testRequestSmallerThanSource() throws Exception
	{
		Path directory = Files.createTempDirectory("user-generation-small-");
		try
		{
			Path source = writeStandardSource(directory);
			UserGenerationEngine.Result result = run(
					source.toFile(), directory.resolve("out").toFile(), 2L, 23L);
			require(result.getGeneratedRows() == 2L,
					"A request smaller than the source corpus was ignored");
			require(countAndValidateRows(result.getOutputFile().toPath()) == 2L,
					"The smaller generated file has the wrong row count");
		}
		finally
		{
			deleteTree(directory);
		}
	}

	private static void testZeroMeansUsableSourceCount() throws Exception
	{
		Path directory = Files.createTempDirectory("user-generation-zero-");
		try
		{
			Path source = writeStandardSource(directory);
			UserGenerationEngine.Result result = run(
					source.toFile(), directory.resolve("out").toFile(), 0L, 21L);
			require(result.getGeneratedRows() == 4L,
					"Zero did not preserve the usable source row count");
			require(countAndValidateRows(result.getOutputFile().toPath()) == 4L,
					"Zero-count mode published the wrong number of rows");
		}
		finally
		{
			deleteTree(directory);
		}
	}

	private static void testDeterministicSeed() throws Exception
	{
		Path directory = Files.createTempDirectory("user-generation-seed-");
		try
		{
			Path source = writeStandardSource(directory);
			UserGenerationEngine.Result first = run(
					source.toFile(), directory.resolve("first").toFile(), 50L, 29L);
			UserGenerationEngine.Result second = run(
					source.toFile(), directory.resolve("second").toFile(), 50L, 29L);
			require(first.getRandomSeed() == 29L && second.getRandomSeed() == 29L,
					"The selected random seed was not retained");
			require(
					java.util.Arrays.equals(
							Files.readAllBytes(first.getOutputFile().toPath()),
							Files.readAllBytes(second.getOutputFile().toPath())),
					"The same random seed did not reproduce the generated dataset");
		}
		finally
		{
			deleteTree(directory);
		}
	}

	private static void testMalformedInputDoesNotPublishOutput() throws Exception
	{
		Path directory = Files.createTempDirectory("user-generation-invalid-");
		try
		{
			Path source = directory.resolve("invalid.txt");
			Files.write(
					source,
					("StoreA\t1\t2020-01-01\t101\talice\talpha bravo charlie\r\n"
					+ "this row is malformed\r\n").getBytes(StandardCharsets.UTF_8));
			Path output = directory.resolve("out");

			boolean failed = false;
			try
			{
				run(source.toFile(), output.toFile(), 10L, 31L);
			}
			catch (IllegalArgumentException expected)
			{
				failed = true;
			}
			require(failed, "Malformed input was accepted");
			require(!Files.exists(output) || isDirectoryEmpty(output),
					"Malformed input published a final or temporary output file");
		}
		finally
		{
			deleteTree(directory);
		}
	}

	private static void testGenerationFailureCleansTemporaryOutput() throws Exception
	{
		Path directory = Files.createTempDirectory("user-generation-cleanup-");
		try
		{
			Path source = writeStandardSource(directory);
			Path output = directory.resolve("out");
			UserGenerationEngine.Options options =
					new UserGenerationEngine.Options(
							source.toFile(), output.toFile(), 100L, 37L);
			boolean failed = false;
			try
			{
				new UserGenerationEngine(
						options,
						new UserGenerationEngine.ProgressListener() {
							public void onProgress(
									String stage,
									String detail,
									long completed,
									long total)
							{
								if ("GENERATING".equals(stage) && completed > 0L)
								{
									throw new IllegalStateException(
											"Intentional test interruption");
								}
							}
						}).run();
			}
			catch (IllegalStateException expected)
			{
				failed = true;
			}
			require(failed, "The intentional generation interruption was ignored");
			require(!Files.exists(output) || isDirectoryEmpty(output),
					"An interrupted run left a final or temporary output file");
		}
		finally
		{
			deleteTree(directory);
		}
	}

	private static void testMillionRowGeneration() throws Exception
	{
		Path directory = Files.createTempDirectory("user-generation-million-");
		long started = System.currentTimeMillis();
		try
		{
			Path source = writeStandardSource(directory);
			UserGenerationEngine.Result result = run(
					source.toFile(),
					directory.resolve("out").toFile(),
					1_000_000L,
					47L);
			require(result.getGeneratedRows() == 1_000_000L,
					"The million-row run returned the wrong count");
			require(countAndValidateRows(result.getOutputFile().toPath())
					== 1_000_000L,
					"The million-row file failed row or schema verification");
			require(noPartFiles(directory.resolve("out")),
					"The million-row run left a temporary part file");
			System.out.println(
					"Million-row stress test completed in "
					+ (System.currentTimeMillis() - started) + " ms");
		}
		finally
		{
			deleteTree(directory);
		}
	}

	private static void testRealCorpus(File source, long rowCount) throws Exception
	{
		require(source.isFile(), "Real-corpus source file does not exist");
		Path directory = Files.createTempDirectory("user-generation-real-");
		long started = System.currentTimeMillis();
		try
		{
			UserGenerationEngine.Result result = run(
					source,
					directory.resolve("out").toFile(),
					rowCount,
					59L);
			require(result.getGeneratedRows() == rowCount,
					"The real-corpus run returned the wrong count");
			require(countAndValidateRows(result.getOutputFile().toPath()) == rowCount,
					"The real-corpus file failed row or schema verification");
			require(noPartFiles(directory.resolve("out")),
					"The real-corpus run left a temporary part file");
			System.out.println(
					"Real-corpus stress test completed: "
					+ result.getProcessedRows() + " usable source rows, "
					+ result.getGeneratedUsers() + " generated users, "
					+ rowCount + " output rows in "
					+ (System.currentTimeMillis() - started) + " ms");
		}
		finally
		{
			deleteTree(directory);
		}
	}

	private static UserGenerationEngine.Result run(
			File source,
			File outputDirectory,
			long count,
			long seed) throws Exception
	{
		UserGenerationEngine.Options options =
				new UserGenerationEngine.Options(
						source, outputDirectory, count, seed);
		return new UserGenerationEngine(options, null).run();
	}

	private static Path writeStandardSource(Path directory) throws Exception
	{
		Path source = directory.resolve("source.txt");
		String content =
				"StoreA\t1\t2020-01-01 10:00:00\t101\talice\talpha bravo charlie delta\r\n"
				+ "StoreA\t2\t2020-01-02 10:00:00\t101\talice\talpha bravo echo foxtrot\r\n"
				+ "StoreA\t3\t2020-01-03 10:00:00\t202\tbob\tgolf hotel india juliet\r\n"
				+ "StoreA\t4\t2020-01-04 10:00:00\t202\tbob\tkilo lima mike november\r\n";
		Files.write(source, content.getBytes(StandardCharsets.UTF_8));
		return source;
	}

	private static long countAndValidateRows(Path output) throws Exception
	{
		long rows = 0L;
		try (BufferedReader reader = Files.newBufferedReader(
				output, StandardCharsets.UTF_8))
		{
			String line;
			while ((line = reader.readLine()) != null)
			{
				rows++;
				DatasetRow.parse(line, rows);
			}
		}
		return rows;
	}

	private static boolean noPartFiles(Path directory) throws Exception
	{
		if (!Files.exists(directory))
		{
			return true;
		}
		try (java.util.stream.Stream<Path> paths = Files.list(directory))
		{
			return !paths.anyMatch(path -> path.getFileName().toString().endsWith(".part"));
		}
	}

	private static boolean isDirectoryEmpty(Path directory) throws Exception
	{
		try (java.util.stream.Stream<Path> paths = Files.list(directory))
		{
			return !paths.findAny().isPresent();
		}
	}

	private static long sum(LinkedHashMap<String, Long> values)
	{
		long total = 0L;
		for (Long value : values.values())
		{
			total += value.longValue();
		}
		return total;
	}

	private static void deleteTree(Path root) throws Exception
	{
		if (!Files.exists(root))
		{
			return;
		}
		try (java.util.stream.Stream<Path> paths = Files.walk(root))
		{
			paths.sorted(java.util.Comparator.reverseOrder())
					.forEach(path -> {
						try
						{
							Files.deleteIfExists(path);
						}
						catch (Exception e)
						{
							throw new RuntimeException(e);
						}
					});
		}
	}

	private static void require(boolean condition, String message)
	{
		if (!condition)
		{
			throw new AssertionError(message);
		}
	}
}
