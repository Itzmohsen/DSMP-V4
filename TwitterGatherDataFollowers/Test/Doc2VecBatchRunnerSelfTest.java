package TwitterGatherDataFollowers.userRyersonU;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.TreeMap;

public final class Doc2VecBatchRunnerSelfTest
{
	private Doc2VecBatchRunnerSelfTest()
	{
	}

	public static void main(String[] args) throws Exception
	{
		parsesStructuredProgress();
		parsesDistinctUsersAndCandidatesWithoutTruncation();
		recordsPerUserErrorsAndCompletion();
		ignoresScoresForUnrequestedUsers();
		runsOneProcessForMultipleUsers();
		capturesProcessFailureDiagnostics();
		detectsUnsafeGensimNumericalDiagnostics();
		System.out.println("Doc2VecBatchRunnerSelfTest passed");
	}

	private static void parsesStructuredProgress()
	{
		Doc2VecBatchRunner.ProgressUpdate update =
				Doc2VecBatchRunner.parseProgressLine(
						"DOC2VEC_PROGRESS\tWAITING\tWaiting for shared model");
		require(update != null, "structured progress was not parsed");
		require("WAITING".equals(update.getEvent()),
				"progress event was not preserved");
		require("Waiting for shared model".equals(update.getDetail()),
				"progress detail was not preserved");
		require(Doc2VecBatchRunner.parseProgressLine("ordinary output") == null,
				"ordinary output was parsed as progress");
	}

	private static void parsesDistinctUsersAndCandidatesWithoutTruncation()
	{
		Doc2VecBatchRunner.Result result = new Doc2VecBatchRunner.Result(
				Arrays.asList("First User", "Second User"));
		Doc2VecBatchRunner.parseOutputLine(scoreLine("First User", "T&T Supermarket", 0.75), result);
		Doc2VecBatchRunner.parseOutputLine(scoreLine("Second User", "Candidate, With Comma", 0.25), result);

		TreeMap<String,Double> firstScores = result.getScores("First User");
		TreeMap<String,Double> secondScores = result.getScores("Second User");
		require(firstScores.size() == 1 && firstScores.get("T&T Supermarket") == 0.75,
				"first user's score was not parsed exactly");
		require(secondScores.size() == 1 && secondScores.get("Candidate, With Comma") == 0.25,
				"second user's score was not parsed exactly");
	}

	private static void recordsPerUserErrorsAndCompletion()
	{
		Doc2VecBatchRunner.Result result = new Doc2VecBatchRunner.Result(Arrays.asList("First User"));
		Doc2VecBatchRunner.parseOutputLine(
				"DOC2VEC_ERROR\t" + encode("First User") + "\t" + encode("missing target"), result);
		Doc2VecBatchRunner.parseOutputLine("DOC2VEC_DONE\t" + encode("First User"), result);

		require("missing target".equals(result.getErrors().get("First User")), "per-user error was lost");
		require(result.hasError("First User"), "failed user was not marked as erroneous");
		require(result.isCompleted("First User"), "completed user was not recorded");
	}

	private static void ignoresScoresForUnrequestedUsers()
	{
		Doc2VecBatchRunner.Result result = new Doc2VecBatchRunner.Result(Arrays.asList("Requested User"));
		Doc2VecBatchRunner.parseOutputLine(scoreLine("Unexpected User", "Candidate", 1.0), result);
		require(result.getScores("Requested User").isEmpty(), "unexpected user's score leaked into results");
	}

	private static void runsOneProcessForMultipleUsers() throws Exception
	{
		File scriptFile = File.createTempFile("doc2vec-batch-runner-test", ".py");
		File datasetFile = File.createTempFile("doc2vec-batch-runner-dataset", ".txt");
		try
		{
			String script = "import base64, sys\n"
					+ "encode = lambda value: base64.b64encode(value.encode('utf-8')).decode('ascii')\n"
					+ "with open(sys.argv[6], 'r', encoding='utf-8') as users_file:\n"
					+ "    users = [line.rstrip('\\r\\n') for line in users_file]\n"
					+ "for user in users:\n"
					+ "    candidate = 'Candidate for ' + user\n"
					+ "    print('DOC2VEC_SCORE\\t%s\\t%s\\t0.5' % (encode(user), encode(candidate)))\n"
					+ "    print('DOC2VEC_DONE\\t%s' % encode(user))\n";
			java.nio.file.Files.write(scriptFile.toPath(), script.getBytes(StandardCharsets.UTF_8));

			Doc2VecBatchRunner.Result result = Doc2VecBatchRunner.run(
					"python", scriptFile, 3, datasetFile, 1, 1, 1,
					Arrays.asList("First User", "Second User"));

			require(result.getExitCode() == 0, "batch process did not exit successfully");
			require(result.isCompleted("First User") && result.isCompleted("Second User"),
					"batch process did not complete every requested user");
			require(result.getScores("First User").get("Candidate for First User") == 0.5,
					"first user's process score was not preserved");
			require(result.getScores("Second User").get("Candidate for Second User") == 0.5,
					"second user's process score was not preserved");
		}
		finally
		{
			scriptFile.delete();
			datasetFile.delete();
		}
	}

	private static void capturesProcessFailureDiagnostics() throws Exception
	{
		File scriptFile = File.createTempFile("doc2vec-batch-failure-test", ".py");
		File datasetFile = File.createTempFile("doc2vec-batch-failure-dataset", ".txt");
		try
		{
			String script = "import sys\n"
					+ "print('fatal model setup detail')\n"
					+ "sys.exit(7)\n";
			java.nio.file.Files.write(
					scriptFile.toPath(), script.getBytes(StandardCharsets.UTF_8));

			Doc2VecBatchRunner.Result result = Doc2VecBatchRunner.run(
					"python", scriptFile, 3, datasetFile, 1, 1, 1,
					Arrays.asList("First User"));

			require(result.getExitCode() == 7,
					"failed batch process exit code was lost");
			require(result.getDiagnosticSummary().contains(
							"fatal model setup detail"),
					"failed batch process diagnostic was lost");
		}
		finally
		{
			scriptFile.delete();
			datasetFile.delete();
		}
	}

	private static void detectsUnsafeGensimNumericalDiagnostics()
			throws Exception
	{
		File scriptFile = File.createTempFile(
				"doc2vec-batch-numerical-test", ".py");
		File datasetFile = File.createTempFile(
				"doc2vec-batch-numerical-dataset", ".txt");
		try
		{
			String script = "print(\"Exception ignored in: "
					+ "'gensim.models.word2vec_inner.our_dot_float'\")\n";
			java.nio.file.Files.write(
					scriptFile.toPath(), script.getBytes(StandardCharsets.UTF_8));

			Doc2VecBatchRunner.Result result = Doc2VecBatchRunner.run(
					"python", scriptFile, 3, datasetFile, 1, 1, 1,
					Arrays.asList("First User"));

			require(result.hasUnsafeNumericalDiagnostic(),
					"unsafe Gensim numerical diagnostic was ignored");
		}
		finally
		{
			scriptFile.delete();
			datasetFile.delete();
		}
	}

	private static String scoreLine(String user, String candidate, double score)
	{
		return "DOC2VEC_SCORE\t" + encode(user) + "\t" + encode(candidate) + "\t" + score;
	}

	private static String encode(String value)
	{
		return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
	}

	private static void require(boolean condition, String message)
	{
		if (!condition)
		{
			throw new AssertionError(message);
		}
	}
}
