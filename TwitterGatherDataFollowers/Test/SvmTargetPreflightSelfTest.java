package TwitterGatherDataFollowers.userRyersonU;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;

public final class SvmTargetPreflightSelfTest
{
	private SvmTargetPreflightSelfTest()
	{
	}

	public static void main(String[] args) throws Exception
	{
		skipsOnlyTargetsWithoutUsableDocuments();
		failsWhenEverySelectedTargetIsUnusable();
		dateRangeRejectsMissingValuesWithoutChangingValidWindowSemantics();
		journalTargetSurvivesAfterAvailableIndexIsPopulated();
		System.out.println("SvmTargetPreflightSelfTest passed");
	}

	private static void skipsOnlyTargetsWithoutUsableDocuments() throws Exception
	{
		ControllerAgent controller = controllerWithTargets(
				"ReadyUser",
				"ShortUser",
				"RetweetUser",
				"MissingUser");

		InMemoryDb db = new InMemoryDb();
		db.addTweet(new Tweet("alpha beta gamma", 1L, "2020-01-01", "ReadyUser"));
		db.addTweet(new Tweet("alpha beta", 2L, "2020-01-01", "ShortUser"));
		db.addTweet(new Tweet("RT @source alpha beta gamma", 3L, "2020-01-01", "RetweetUser"));
		setField(controller, "availableDb", db);

		String error = invokePreflight(controller);
		require(error == null, "one valid SVM target should let initialization continue");
		require(getUsersRec(controller).equals(Arrays.asList("ReadyUser")),
				"preflight did not keep exactly the usable SVM target");
	}

	private static void failsWhenEverySelectedTargetIsUnusable() throws Exception
	{
		ControllerAgent controller = controllerWithTargets("ShortUser", "RetweetUser");

		InMemoryDb db = new InMemoryDb();
		db.addTweet(new Tweet("alpha beta", 10L, "2020-01-01", "ShortUser"));
		db.addTweet(new Tweet("RT @source alpha beta gamma", 11L, "2020-01-01", "RetweetUser"));
		setField(controller, "availableDb", db);

		String error = invokePreflight(controller);
		require(error != null && error.contains("No selected SVM target has a usable document"),
				"all-invalid SVM targets should fail before recommender agents start");
	}

	/** True for a Git LFS pointer file ("version https://git-lfs..."), not real content. */
	private static boolean isGitLfsPointer(File file) throws IOException
	{
		if (file.length() > 1024L)
		{
			return false;
		}
		BufferedReader reader = new BufferedReader(new FileReader(file));
		try
		{
			String first = reader.readLine();
			return first != null && first.startsWith("version https://git-lfs");
		}
		finally
		{
			reader.close();
		}
	}

	private static void journalTargetSurvivesAfterAvailableIndexIsPopulated()
			throws Exception
	{
		File dataset = new File(
				"Dataset/Benchmarks/Extreme-15-Minute-Plus-Journal-245k.txt");
		if (!dataset.isFile() || isGitLfsPointer(dataset))
		{
			//A clone without Git LFS content has only a small pointer file here.
			System.out.println("Skipping journal target preflight regression: "
					+ dataset.getPath() + " is not present (run git lfs pull)");
			return;
		}

		ControllerAgent controller =
				controllerWithTargets("ExtremeAuthorC15M29");
		setField(controller, "beginDate", "2007-01-01");
		setField(controller, "endDate", "2026-12-31");
		setField(controller, "totalTweetLimit", Integer.valueOf(0));
		setField(controller, "textFile", dataset);
		setField(controller, "localDb", new InMemoryDb());
		setField(controller, "availableDb", new InMemoryDb());

		invokeReadFromTextFile(controller);
		@SuppressWarnings("unchecked")
		ArrayList<Tweet> availableTweets =
				(ArrayList<Tweet>) invokeGetAvailableTweets(controller);

		String prematureError = invokePreflight(controller);
		require(prematureError != null
				&& prematureError.contains("No selected SVM target"),
				"empty availableDb should reproduce the old false preflight failure");

		InMemoryDb availableDb = new InMemoryDb();
		availableDb.setTweets(availableTweets);
		setField(controller, "availableDb", availableDb);

		String error = invokePreflight(controller);
		require(error == null,
				"journal target with valid text should pass after availableDb is populated");
		require(getUsersRec(controller).equals(
				Arrays.asList("ExtremeAuthorC15M29")),
				"journal target was unexpectedly removed by SVM preflight");
	}

	private static void dateRangeRejectsMissingValuesWithoutChangingValidWindowSemantics()
	{
		ControllerAgent controller = new ControllerAgent();

		require(controller.checkDateRange(
						"2010-01-01",
						"2007-01-01",
						"2017-01-01"),
				"valid date inside the configured window should still pass");
		require(!controller.checkDateRange(
						"2007-01-01",
						"2007-01-01",
						"2017-01-01"),
				"begin boundary should remain exclusive");
		require(!controller.checkDateRange(
						"2017-01-01",
						"2007-01-01",
						"2017-01-01"),
				"end boundary should remain exclusive");
		require(!controller.checkDateRange(
						"2010-01-01",
						null,
						"2017-01-01"),
				"missing begin date should be rejected instead of crashing");
		require(!controller.checkDateRange(
						"2010-01-01",
						"2007-01-01",
						null),
				"missing end date should be rejected instead of crashing");
		require(!controller.checkDateRange(
						null,
						"2007-01-01",
						"2017-01-01"),
				"missing tweet date should be rejected instead of crashing");
	}

	private static ControllerAgent controllerWithTargets(String... targets) throws Exception
	{
		ControllerAgent controller = new ControllerAgent();
		setField(controller, "algorithmRec", Integer.valueOf(ControllerAgent.SVM));
		setField(controller, "readFrom", Integer.valueOf(ControllerAgent.FROM_TEXT));
		setField(controller, "usersRec", new ArrayList<String>(Arrays.asList(targets)));
		return controller;
	}

	private static String invokePreflight(ControllerAgent controller) throws Exception
	{
		Method method = ControllerAgent.class.getDeclaredMethod(
				"filterSvmTargetsWithoutUsableDocuments",
				Integer.TYPE,
				Integer.TYPE,
				Integer.TYPE);
		method.setAccessible(true);
		return (String) method.invoke(controller, 1, 1, 1);
	}

	private static void invokeReadFromTextFile(ControllerAgent controller)
			throws Exception
	{
		Method method = ControllerAgent.class.getDeclaredMethod(
				"readFromTextFile",
				Integer.class,
				Integer.class,
				Integer.class);
		method.setAccessible(true);
		method.invoke(controller, Integer.valueOf(1), Integer.valueOf(1),
				Integer.valueOf(1));
	}

	private static Object invokeGetAvailableTweets(ControllerAgent controller)
			throws Exception
	{
		Method method = ControllerAgent.class.getDeclaredMethod(
				"getAvailableTweets");
		method.setAccessible(true);
		return method.invoke(controller);
	}

	@SuppressWarnings("unchecked")
	private static ArrayList<String> getUsersRec(ControllerAgent controller)
			throws Exception
	{
		return (ArrayList<String>) getField(controller, "usersRec");
	}

	private static Object getField(Object instance, String fieldName) throws Exception
	{
		Field field = ControllerAgent.class.getDeclaredField(fieldName);
		field.setAccessible(true);
		return field.get(instance);
	}

	private static void setField(Object instance, String fieldName, Object value)
			throws Exception
	{
		Field field = ControllerAgent.class.getDeclaredField(fieldName);
		field.setAccessible(true);
		field.set(instance, value);
	}

	private static void require(boolean condition, String message)
	{
		if (!condition)
		{
			throw new AssertionError(message);
		}
	}
}
