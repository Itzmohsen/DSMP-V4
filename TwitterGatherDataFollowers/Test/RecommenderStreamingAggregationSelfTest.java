package TwitterGatherDataFollowers.userRyersonU;

import jade.core.Agent;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

public final class RecommenderStreamingAggregationSelfTest
{
	private static final Pattern RETWEET_PATTERN = Pattern.compile("RT @");
	private static final Pattern PUNCTUATION_PATTERN = Pattern.compile("[\\p{P}]");
	private static final Pattern LINK_PATTERN = Pattern.compile(
			"http[a-zA-Z0-9]*|bitly[a-zA-Z0-9]*|www[a-zA-Z0-9]*");
	private static final Pattern SPECIAL_CHARACTER_PATTERN =
			Pattern.compile("[^a-zA-Z\\p{Z}]");
	private static final Pattern NON_ALPHA_PATTERN = Pattern.compile("[^a-zA-Z ]");
	private static final Pattern MULTI_SPACE_PATTERN = Pattern.compile(" +");

	private RecommenderStreamingAggregationSelfTest()
	{
	}

	public static void main(String[] args) throws Exception
	{
		preservesLegacyPerUserDocuments();
		diskBackedExactSvmReplaysIdenticalDocuments();
		System.out.println("RecommenderStreamingAggregationSelfTest passed");
	}

	@SuppressWarnings("unchecked")
	private static void preservesLegacyPerUserDocuments() throws Exception
	{
		RecommenderAgent agent = new RecommenderAgent();
		setInt(agent, "algorithmRec", RecommenderAgent.K_MEANS);
		setInt(agent, "retweetedby_temp", 1);
		setInt(agent, "hashtags_temp", 1);
		setInt(agent, "stopWordFlag_temp", 1);

		Class<?> behaviorClass = Class.forName(
				"TwitterGatherDataFollowers.userRyersonU.RecommenderAgent"
				+ "$RecommenderServiceBehaviour");
		Constructor<?> constructor = behaviorClass.getDeclaredConstructor(
				RecommenderAgent.class, Agent.class);
		constructor.setAccessible(true);
		Object behavior = constructor.newInstance(agent, agent);
		Method aggregate = behaviorClass.getDeclaredMethod(
				"aggregateReceivedTweet", String.class, String.class);
		aggregate.setAccessible(true);

		LinkedHashMap<String,LinkedHashMap<String,Double>> expected =
				new LinkedHashMap<String,LinkedHashMap<String,Double>>();
		Set<String> expectedTerms = new TreeSet<String>();
		int expectedUsable = 0;
		for (Sample sample : Arrays.asList(
				new Sample("alice", "Alpha beta beta gamma"),
				new Sample("alice", "the delta echo foxtrot"),
				new Sample("bob", "RT @name hidden words here"),
				new Sample("bob", "Photo: hidden words here"),
				new Sample("bob", "Golf, hotel! india juliet")))
		{
			aggregate.invoke(behavior, sample.user, sample.text);
			if (legacyAdd(expected, expectedTerms, sample))
			{
				expectedUsable++;
			}
		}

		Field documentsField =
				RecommenderAgent.class.getDeclaredField("allUserDocuments");
		documentsField.setAccessible(true);
		Map<String,LinkedHashMap<String,Double>> actual =
				(Map<String,LinkedHashMap<String,Double>>)
						documentsField.get(agent);

		Field termsField =
				RecommenderAgent.class.getDeclaredField("allUniqueDocTerms");
		termsField.setAccessible(true);
		Set<String> actualTerms = (Set<String>) termsField.get(agent);

		Field countField =
				RecommenderAgent.class.getDeclaredField("usableTweetCount");
		countField.setAccessible(true);
		int actualUsable = countField.getInt(agent);

		require(expected.equals(actual),
				"streaming ingestion changed per-user term frequencies");
		require(expectedTerms.equals(actualTerms),
				"streaming ingestion changed the model vocabulary");
		require(expectedUsable == actualUsable,
				"streaming ingestion changed the usable-tweet count");
	}

	private static boolean legacyAdd(
			Map<String,LinkedHashMap<String,Double>> documents,
			Set<String> terms,
			Sample sample)
	{
		String currentText = sample.text;
		if (currentText.contains("Photo:"))
			currentText = currentText.substring(0,currentText.indexOf("Photo:"));
		if (currentText.contains("Photoset:"))
			currentText = currentText.substring(0,currentText.indexOf("Photoset:"));
		if (currentText.contains("RT @"))
			currentText = "";
		else
			currentText = RETWEET_PATTERN.matcher(currentText).replaceAll("RT ");
		currentText = PUNCTUATION_PATTERN.matcher(currentText).replaceAll("");
		currentText = LINK_PATTERN.matcher(currentText).replaceAll(" ");
		currentText = SPECIAL_CHARACTER_PATTERN.matcher(currentText).replaceAll(" ");
		currentText = currentText.toLowerCase();
		currentText = removeStopWords(currentText);
		currentText = NON_ALPHA_PATTERN.matcher(currentText.trim()).replaceAll("");
		currentText = MULTI_SPACE_PATTERN.matcher(currentText).replaceAll(" ").trim();
		if (currentText.length() == 0)
		{
			return false;
		}
		String[] tokens = currentText.split(" ");
		if (tokens.length < 3)
		{
			return false;
		}

		LinkedHashMap<String,Double> document = documents.get(sample.user);
		if (document == null)
		{
			document = new LinkedHashMap<String,Double>();
			documents.put(sample.user, document);
		}
		for (String token : tokens)
		{
			terms.add(token);
			Double frequency = document.get(token);
			document.put(token, frequency == null ? 1.0 : frequency + 1.0);
		}
		return true;
	}

	@SuppressWarnings("unchecked")
	private static void diskBackedExactSvmReplaysIdenticalDocuments()
			throws Exception
	{
		RecommenderAgent agent = new RecommenderAgent();
		setInt(agent, "algorithmRec", RecommenderAgent.SVM);
		setInt(agent, "retweetedby_temp", 1);
		setInt(agent, "hashtags_temp", 1);
		setInt(agent, "stopWordFlag_temp", 1);
		setBoolean(agent, "exactSvmDiskBackedWaves", true);

		Class<?> behaviorClass = Class.forName(
				"TwitterGatherDataFollowers.userRyersonU.RecommenderAgent"
				+ "$RecommenderServiceBehaviour");
		Constructor<?> constructor = behaviorClass.getDeclaredConstructor(
				RecommenderAgent.class, Agent.class);
		constructor.setAccessible(true);
		Object behavior = constructor.newInstance(agent, agent);
		Method aggregate = behaviorClass.getDeclaredMethod(
				"aggregateReceivedTweet", String.class, String.class);
		aggregate.setAccessible(true);
		Method finish = behaviorClass.getDeclaredMethod(
				"finishExactSvmCorpusSpoolWriting");
		finish.setAccessible(true);
		Method materialize = behaviorClass.getDeclaredMethod(
				"materializeExactSvmCorpusSpool");
		materialize.setAccessible(true);

		LinkedHashMap<String,LinkedHashMap<String,Double>> expected =
				new LinkedHashMap<String,LinkedHashMap<String,Double>>();
		Set<String> expectedTerms = new TreeSet<String>();
		for (Sample sample : Arrays.asList(
				new Sample("alice", "Alpha beta beta gamma"),
				new Sample("alice", "Delta echo foxtrot"),
				new Sample("bob", "Golf hotel india juliet")))
		{
			aggregate.invoke(behavior, sample.user, sample.text);
			legacyAdd(expected, expectedTerms, sample);
		}

		Field documentsField =
				RecommenderAgent.class.getDeclaredField("allUserDocuments");
		documentsField.setAccessible(true);
		Map<String,LinkedHashMap<String,Double>> documentsBeforeReplay =
				(Map<String,LinkedHashMap<String,Double>>)
						documentsField.get(agent);
		require(documentsBeforeReplay.isEmpty(),
				"disk-backed Exact SVM retained documents before its wave");

		finish.invoke(behavior);
		materialize.invoke(behavior);

		Map<String,LinkedHashMap<String,Double>> actual =
				(Map<String,LinkedHashMap<String,Double>>)
						documentsField.get(agent);
		Field termsField =
				RecommenderAgent.class.getDeclaredField("allUniqueDocTerms");
		termsField.setAccessible(true);
		Set<String> actualTerms = (Set<String>) termsField.get(agent);
		require(expected.equals(actual),
				"disk-backed Exact SVM changed per-user term frequencies");
		require(expectedTerms.equals(actualTerms),
				"disk-backed Exact SVM changed the model vocabulary");
	}

	private static String removeStopWords(String currentText)
	{
		String padded = " " + currentText + " ";
		for (String stopWord : RecommenderAgent.stopWordsArray)
		{
			String pattern = " " + stopWord + " ";
			if (padded.contains(pattern))
			{
				padded = padded.replace(pattern, " ");
			}
		}
		return padded;
	}

	private static void setInt(Object target, String fieldName, int value)
			throws Exception
	{
		Field field = target.getClass().getDeclaredField(fieldName);
		field.setAccessible(true);
		field.setInt(target, value);
	}

	private static void setBoolean(
			Object target,
			String fieldName,
			boolean value) throws Exception
	{
		Field field = target.getClass().getDeclaredField(fieldName);
		field.setAccessible(true);
		field.setBoolean(target, value);
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

		private Sample(String user, String text)
		{
			this.user = user;
			this.text = text;
		}
	}
}
