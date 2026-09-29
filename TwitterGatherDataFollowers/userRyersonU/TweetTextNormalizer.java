package TwitterGatherDataFollowers.userRyersonU;

import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The one definition of DSMP's tweet-text normalisation.
 *
 * The controller and every recommender node must produce byte-identical tokens
 * for the same input. They previously each carried their own copy of the
 * pipeline, so a change in one silently desynchronised the vocabularies: the
 * controller's {@code centralUniqueDocTerms} would no longer contain terms the
 * recommenders actually produced, which breaks distributed MLP feature vectors
 * and can leave document-frequency lookups without an entry.
 *
 * The pipeline, in order:
 * <ol>
 *   <li>truncate at a {@code Photo:} / {@code Photoset:} marker</li>
 *   <li>drop retweets entirely, or reduce {@code RT @} to {@code RT }</li>
 *   <li>strip whole URLs, before punctuation, so a link is still one run</li>
 *   <li>replace punctuation with a space, so {@code cat,dog} yields two tokens</li>
 *   <li>optionally replace any non-letter with a space (hash-tag mode)</li>
 *   <li>replace every remaining non-letter with a space, so the token boundaries
 *       the stop-word pass sees are the final ones</li>
 *   <li>lower-case in {@link Locale#ENGLISH}, so the result cannot depend on the
 *       machine's locale</li>
 *   <li>collapse runs of spaces</li>
 *   <li>optionally drop stop words, token by token</li>
 *   <li>collapse again and trim</li>
 * </ol>
 *
 * The pipeline is idempotent: normalising an already-normalised string returns it
 * unchanged. That is what guarantees a stop word next to a tab or newline is
 * removed on the first pass rather than surviving into the vocabulary.
 */
final class TweetTextNormalizer
{
	private static final Pattern RETWEET_PATTERN = Pattern.compile("RT @");
	private static final Pattern PUNCTUATION_PATTERN = Pattern.compile("[\\p{P}]");

	/**
	 * Whole-URL matcher.
	 *
	 * Runs before punctuation becomes whitespace, so it has to match the
	 * separators inside a link. The bare {@code http}/{@code www} alternatives at
	 * the end keep already-stripped corpora matching as they did before.
	 */
	private static final Pattern LINK_PATTERN = Pattern.compile(
			"(?:https?|ftp)://\\S+"
			+ "|www\\.\\S+"
			+ "|bit\\.ly/\\S+"
			+ "|\\S+\\.(?:com|org|net|edu|gov|io|co)(?:/\\S*)?"
			+ "|http[a-zA-Z0-9]*|bitly[a-zA-Z0-9]*|www[a-zA-Z0-9]*",
			Pattern.CASE_INSENSITIVE);

	private static final Pattern SPECIAL_CHARACTER_PATTERN =
			Pattern.compile("[^a-zA-Z\\p{Z}]");
	private static final Pattern NON_ALPHA_PATTERN = Pattern.compile("[^a-zA-Z ]");
	private static final Pattern MULTI_SPACE_PATTERN = Pattern.compile(" +");

	/** Minimum tokens a normalised tweet needs to enter a document vector. */
	static final int MINIMUM_USABLE_TOKENS = 3;

	private TweetTextNormalizer()
	{
	}

	/** Immutable lookup set built once from a stop-word array. */
	static Set<String> stopWordSet(String[] stopWords)
	{
		HashSet<String> set = new HashSet<String>();
		if (stopWords != null)
		{
			for (String word : stopWords)
			{
				if (word != null && word.length() > 0)
				{
					set.add(word);
				}
			}
		}
		return Collections.unmodifiableSet(set);
	}

	/**
	 * @param tweetText     raw tweet text
	 * @param dropRetweets  true to discard retweets outright
	 * @param stripNonAlpha true to apply hash-tag mode's non-letter pass
	 * @param removeStopWords true to drop stop words
	 * @param stopWords     lookup set; ignored when {@code removeStopWords} is false
	 * @return space-separated tokens, trimmed; possibly empty
	 */
	static String normalize(
			String tweetText,
			boolean dropRetweets,
			boolean stripNonAlpha,
			boolean removeStopWords,
			Set<String> stopWords)
	{
		String currentText = tweetText == null ? "" : tweetText;

		int photoIndex = currentText.indexOf("Photo:");
		if (photoIndex >= 0)
		{
			currentText = currentText.substring(0, photoIndex);
		}
		int photosetIndex = currentText.indexOf("Photoset:");
		if (photosetIndex >= 0)
		{
			currentText = currentText.substring(0, photosetIndex);
		}

		if (currentText.contains("RT @") && dropRetweets)
		{
			currentText = "";
		}
		else
		{
			currentText = RETWEET_PATTERN.matcher(currentText).replaceAll("RT ");
		}

		currentText = LINK_PATTERN.matcher(currentText).replaceAll(" ");
		currentText = PUNCTUATION_PATTERN.matcher(currentText).replaceAll(" ");

		if (stripNonAlpha)
		{
			currentText = SPECIAL_CHARACTER_PATTERN.matcher(currentText).replaceAll(" ");
		}

		//Every non-letter becomes a space before stop words are considered, so the
		//token boundaries the stop-word pass sees are the final ones. Filtering
		//first left a stop word attached to a tab or newline unremoved, and made
		//the pipeline non-idempotent: a second pass would remove what the first
		//could not see.
		currentText = NON_ALPHA_PATTERN.matcher(currentText).replaceAll(" ");
		currentText = currentText.toLowerCase(Locale.ENGLISH);
		currentText = MULTI_SPACE_PATTERN.matcher(currentText).replaceAll(" ");

		if (removeStopWords)
		{
			currentText = dropStopWords(currentText, stopWords);
			currentText = MULTI_SPACE_PATTERN.matcher(currentText).replaceAll(" ");
		}

		return currentText.trim();
	}

	/**
	 * Drops stop words token by token.
	 *
	 * Replacing the literal {@code " word "} for each stop word in turn was both
	 * quadratic and wrong: neighbouring matches share the space between them, so
	 * {@link String#replace} consumed the delimiter and skipped every second
	 * occurrence, leaving {@code " a a a "} as {@code " a "}.
	 */
	private static String dropStopWords(String currentText, Set<String> stopWords)
	{
		if (currentText.length() == 0 || stopWords == null || stopWords.isEmpty())
		{
			return currentText;
		}
		StringBuilder kept = new StringBuilder(currentText.length() + 2);
		kept.append(' ');
		int tokenStart = 0;
		int length = currentText.length();
		while (tokenStart < length)
		{
			while (tokenStart < length && currentText.charAt(tokenStart) == ' ')
			{
				tokenStart++;
			}
			if (tokenStart >= length)
			{
				break;
			}
			int tokenEnd = currentText.indexOf(' ', tokenStart);
			if (tokenEnd < 0)
			{
				tokenEnd = length;
			}
			String token = currentText.substring(tokenStart, tokenEnd);
			if (!stopWords.contains(token))
			{
				kept.append(token).append(' ');
			}
			tokenStart = tokenEnd + 1;
		}
		return kept.toString();
	}

	/** Number of space-separated tokens in an already-normalised string. */
	static int countTokens(String normalizedText)
	{
		if (normalizedText == null || normalizedText.length() == 0)
		{
			return 0;
		}
		int tokenCount = 1;
		for (int index = 0; index < normalizedText.length(); index++)
		{
			if (normalizedText.charAt(index) == ' ')
			{
				tokenCount++;
			}
		}
		return tokenCount;
	}
}
