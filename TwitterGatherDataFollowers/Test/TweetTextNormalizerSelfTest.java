package TwitterGatherDataFollowers.userRyersonU;

import java.util.Set;

/**
 * Locks the tokenisation contract shared by the controller and every
 * recommender node.
 *
 * If these ever diverge again, {@code centralUniqueDocTerms} stops containing
 * the terms the recommenders actually produce, which corrupts distributed MLP
 * feature vectors and can leave document-frequency lookups without an entry.
 */
public final class TweetTextNormalizerSelfTest
{
	private static final Set<String> STOP_WORDS =
			TweetTextNormalizer.stopWordSet(new String[] {"a", "the", "is", "and", "of"});

	private TweetTextNormalizerSelfTest()
	{
	}

	public static void main(String[] args)
	{
		punctuationSeparatesWords();
		consecutiveStopWordsAreAllRemoved();
		urlsAreRemovedWhole();
		retweetsHonourTheFlag();
		photoMarkersTruncate();
		lowercasingIsLocaleIndependent();
		tokenCountMatchesTheSplit();
		outputShapeIsStableAndClean();
		System.out.println("TweetTextNormalizerSelfTest passed");
	}

	/** "cat,dog" used to weld into the single junk token "catdog". */
	private static void punctuationSeparatesWords()
	{
		requireEquals(normalize("cat,dog bird"), "cat dog bird", "comma");
		requireEquals(normalize("word--word end"), "word word end", "dash run");
		requireEquals(normalize("end.Start here"), "end start here", "missing space");
	}

	/** Shared delimiters used to leave every second occurrence behind. */
	private static void consecutiveStopWordsAreAllRemoved()
	{
		requireEquals(normalize("a a a house"), "house", "repeated stop word");
		requireEquals(normalize("the the the cat"), "cat", "repeated the");
		requireEquals(normalize("is a house of the cat"), "house cat", "interleaved");
	}

	/** Links must vanish entirely, not shed their punctuation and survive. */
	private static void urlsAreRemovedWhole()
	{
		requireEquals(normalize("check http://t.co/xyz now"), "check now", "http link");
		requireEquals(normalize("see www.example.com/page ok"), "see ok", "www link");
		requireEquals(normalize("visit bit.ly/abc123 today"), "visit today", "shortener");
	}

	private static void retweetsHonourTheFlag()
	{
		requireEquals(
				TweetTextNormalizer.normalize("RT @someone hello world",
						true, false, false, STOP_WORDS),
				"", "retweet should be dropped");
		requireEquals(
				TweetTextNormalizer.normalize("RT @someone hello world",
						false, false, false, STOP_WORDS),
				"rt someone hello world", "retweet should be kept");
	}

	private static void photoMarkersTruncate()
	{
		requireEquals(normalize("keep this Photo: drop that"), "keep this", "Photo:");
		requireEquals(normalize("keep this Photoset: drop that"), "keep this", "Photoset:");
	}

	/**
	 * Lower-casing must not depend on the machine's locale. In a Turkish locale
	 * a default toLowerCase turns "I" into a dotless i, which would silently
	 * produce a different vocabulary on a different machine.
	 */
	private static void lowercasingIsLocaleIndependent()
	{
		java.util.Locale original = java.util.Locale.getDefault();
		try
		{
			java.util.Locale.setDefault(java.util.Locale.forLanguageTag("tr-TR"));
			requireEquals(normalize("INSIGHT DIGITAL"), "insight digital",
					"Turkish locale changed the tokens");
		}
		finally
		{
			java.util.Locale.setDefault(original);
		}
	}

	private static void tokenCountMatchesTheSplit()
	{
		String text = normalize("cat,dog bird fish");
		require(TweetTextNormalizer.countTokens(text) == text.split(" ").length,
				"token count disagreed with the split");
		require(TweetTextNormalizer.countTokens("") == 0, "empty text counted a token");
	}

	/**
	 * Output invariants every caller depends on.
	 *
	 * The controller and the recommender both call this one method now, so what
	 * still needs guarding is the shape of its output: lowercase letters and
	 * single spaces only, trimmed, and stable when called repeatedly.
	 */
	private static void outputShapeIsStableAndClean()
	{
		String[] corpus = {
			"Great deals on shoes,bags and hats!",
			"RT @store Visit http://bit.ly/x for the sale",
			"Kids' toys -- games, puzzles. Fun!",
			"a a the the of of nothing left",
			"Photoset: hidden text",
			"plain tweet with no punctuation at all",
			"MiXeD CaSe 12345 numbers\tand\ttabs",
			"",
		};
		for (String tweet : corpus)
		{
			String first = normalize(tweet);
			requireEquals(normalize(tweet), first,
					"normalisation was not deterministic for: " + tweet);
			require(first.indexOf("  ") < 0,
					"double space survived: [" + first + "]");
			require(first.equals(first.trim()),
					"untrimmed result: [" + first + "]");
			for (int i = 0; i < first.length(); i++)
			{
				char c = first.charAt(i);
				require((c >= 'a' && c <= 'z') || c == ' ',
						"non-lowercase-letter survived: [" + first + "]");
			}
			//Re-normalising an already-normalised string must not change it.
			requireEquals(normalize(first), first,
					"normalisation was not idempotent for: " + tweet);
		}
	}

	private static String normalize(String text)
	{
		return TweetTextNormalizer.normalize(text, false, false, true, STOP_WORDS);
	}

	private static void requireEquals(String actual, String expected, String label)
	{
		require(expected.equals(actual),
				label + ": expected [" + expected + "] but was [" + actual + "]");
	}

	private static void require(boolean condition, String message)
	{
		if (!condition)
		{
			throw new AssertionError(message);
		}
	}
}
