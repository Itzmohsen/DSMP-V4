package TwitterGatherDataFollowers.userRyersonU;

import twitter4j.conf.ConfigurationBuilder;

/**
 * Loads optional Twitter crawler credentials without storing secrets in Git.
 */
final class TwitterCredentials
{
	private TwitterCredentials()
	{
	}

	static ConfigurationBuilder configure(ConfigurationBuilder builder)
	{
		builder.setOAuthConsumerKey(required(
				"dsmp.twitter.consumerKey", "DSMP_TWITTER_CONSUMER_KEY"));
		builder.setOAuthConsumerSecret(required(
				"dsmp.twitter.consumerSecret", "DSMP_TWITTER_CONSUMER_SECRET"));
		builder.setOAuthAccessToken(required(
				"dsmp.twitter.accessToken", "DSMP_TWITTER_ACCESS_TOKEN"));
		builder.setOAuthAccessTokenSecret(required(
				"dsmp.twitter.accessTokenSecret", "DSMP_TWITTER_ACCESS_TOKEN_SECRET"));
		return builder;
	}

	private static String required(String propertyName, String environmentName)
	{
		String value = System.getProperty(propertyName);
		if (value == null || value.trim().length() == 0)
		{
			value = System.getenv(environmentName);
		}
		if (value == null || value.trim().length() == 0)
		{
			throw new IllegalStateException(
					"Twitter crawling is not configured. Set environment variable "
					+ environmentName + " (or Java property " + propertyName + ").");
		}
		return value.trim();
	}
}
