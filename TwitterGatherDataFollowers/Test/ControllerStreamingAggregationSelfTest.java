package TwitterGatherDataFollowers.userRyersonU;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ControllerStreamingAggregationSelfTest
{
	private ControllerStreamingAggregationSelfTest()
	{
	}

	public static void main(String[] args) throws Exception
	{
		preservesLegacyControllerAggregation();
		System.out.println("ControllerStreamingAggregationSelfTest passed");
	}

	@SuppressWarnings("unchecked")
	private static void preservesLegacyControllerAggregation() throws Exception
	{
		ControllerAgent controller = new ControllerAgent();
		Method addTweet = ControllerAgent.class.getDeclaredMethod(
				"addControllerTweetToUserDocument",
				String.class, String.class, int.class, int.class, int.class);
		addTweet.setAccessible(true);

		Map<String,LinkedHashMap<String,Double>> expected =
				new LinkedHashMap<String,LinkedHashMap<String,Double>>();
		Set<String> expectedTerms = new TreeSet<String>();
		for (Sample sample : Arrays.asList(
				new Sample("alice", "Alpha beta beta gamma", 0, 0, 0),
				new Sample("alice", "the delta echo foxtrot", 0, 0, 1),
				new Sample("bob", "RT @name hidden words here", 1, 1, 1),
				new Sample("bob", "Photo: hidden words here", 0, 1, 1),
				new Sample("bob", "Golf, hotel! india juliet", 0, 1, 0)))
		{
			addTweet.invoke(
					controller,
					sample.user,
					sample.text,
					Integer.valueOf(sample.retweets),
					Integer.valueOf(sample.hashTags),
					Integer.valueOf(sample.stopWords));
			legacyAdd(expected, expectedTerms, sample, controller.stopWordsArray);
		}

		Field documentsField =
				ControllerAgent.class.getDeclaredField("allUserDocuments");
		documentsField.setAccessible(true);
		Map<String,LinkedHashMap<String,Double>> actual =
				(Map<String,LinkedHashMap<String,Double>>) documentsField.get(controller);

		Field termsField =
				ControllerAgent.class.getDeclaredField("allUniqueDocTerms");
		termsField.setAccessible(true);
		Set<String> actualTerms = (Set<String>) termsField.get(controller);

		require(expected.equals(actual),
				"streaming aggregation changed per-user term frequencies");
		require(expectedTerms.equals(actualTerms),
				"streaming aggregation changed the vocabulary");
	}

	private static void legacyAdd(
			Map<String,LinkedHashMap<String,Double>> documents,
			Set<String> terms,
			Sample sample,
			String[] stopWords)
	{
		String currentText = String.format(" %s ", sample.text);
		if (currentText.contains("Photo:"))
			currentText = currentText.substring(0,currentText.indexOf("Photo:"));
		if (currentText.contains("Photoset:"))
			currentText = currentText.substring(0,currentText.indexOf("Photoset:"));

		Matcher matcher;
		if (currentText.contains("RT @") && sample.retweets == 1)
			currentText = "";
		else
			currentText = Pattern.compile("RT @").matcher(currentText).replaceAll("RT ");
		currentText = Pattern.compile("[\\p{P}]").matcher(currentText).replaceAll("");
		currentText = Pattern.compile(
				"http[a-zA-Z0-9]*|bitly[a-zA-Z0-9]*|www[a-zA-Z0-9]*")
				.matcher(currentText).replaceAll(" ");
		if (sample.hashTags == 1)
			currentText = Pattern.compile("[^a-zA-Z\\p{Z}]")
					.matcher(currentText).replaceAll(" ");
		if (sample.stopWords == 1)
		{
			for (String stopWord : stopWords)
			{
				if (currentText.toLowerCase().contains(" "+stopWord+" "))
					currentText = currentText.toLowerCase()
							.replaceAll(" "+stopWord+" "," ");
			}
		}
		currentText = currentText.toLowerCase().trim();
		currentText = currentText.replaceAll("[^a-zA-Z ]","");
		currentText = currentText.replaceAll(" +", " ");
		if (currentText.split("\\s+").length < 3)
			return;

		LinkedHashMap<String,Double> userDocument = documents.get(sample.user);
		if (userDocument == null)
		{
			userDocument = new LinkedHashMap<String,Double>();
			documents.put(sample.user, userDocument);
		}
		Scanner scanner = new Scanner(currentText);
		try
		{
			while (scanner.hasNext())
			{
				String token = scanner.next();
				terms.add(token);
				Double frequency = userDocument.get(token);
				userDocument.put(token, frequency == null ? 1.0 : frequency + 1.0);
			}
		}
		finally
		{
			scanner.close();
		}
	}

	private static void require(boolean condition, String message)
	{
		if (!condition)
		{
			throw new AssertionError(message);
		}
	}

	private static final class Sample
	{
		private final String user;
		private final String text;
		private final int retweets;
		private final int hashTags;
		private final int stopWords;

		private Sample(
				String user,
				String text,
				int retweets,
				int hashTags,
				int stopWords)
		{
			this.user = user;
			this.text = text;
			this.retweets = retweets;
			this.hashTags = hashTags;
			this.stopWords = stopWords;
		}
	}
}
