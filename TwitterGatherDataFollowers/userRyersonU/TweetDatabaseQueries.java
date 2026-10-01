package TwitterGatherDataFollowers.userRyersonU;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * The MySQL queries of the database dataset source.
 *
 * Every value the user typed (the followee, the date range, a screen name) is
 * a bound parameter, never part of the SQL text, so a name with a quote in it
 * cannot change the query. The tweet limit keeps the {@code limit} most recent
 * tweets by tweet id, and the date range includes both boundary days (SQL
 * {@code BETWEEN}), matching the text-file source. Result sets are scrollable
 * because the agents rewind them.
 */
final class TweetDatabaseQueries
{
	private static final String WINDOW =
			"select * from usertweet where referenceUser = ?"
			+ " AND CAST(created_at AS DATE) BETWEEN ? AND ?";

	private TweetDatabaseQueries()
	{
	}

	/** Each user of the followee's window with its tweet count, most active first. */
	static PreparedStatement usersByTweetCount(Connection connection, String referenceUser,
			String beginDate, String endDate, int tweetLimit) throws SQLException
	{
		return bind(connection,
				"select screen_name, referenceUser, count(*) from (" + window(tweetLimit)
				+ ") as T1 group by screen_name order by count(*) DESC",
				referenceUser, beginDate, endDate, tweetLimit, null);
	}

	/** The number of tweets one user has in the window, as column {@code rowcount}. */
	static PreparedStatement userTweetCount(Connection connection, String referenceUser,
			String beginDate, String endDate, int tweetLimit, String screenName) throws SQLException
	{
		return bind(connection,
				"select count(*) AS rowcount from (" + window(tweetLimit) + ") AS T1 where screen_name = ?",
				referenceUser, beginDate, endDate, tweetLimit, screenName);
	}

	/** One user's tweets in the window, all {@code usertweet} columns. */
	static PreparedStatement userTweets(Connection connection, String referenceUser,
			String beginDate, String endDate, int tweetLimit, String screenName) throws SQLException
	{
		return bind(connection,
				"select * from (" + window(tweetLimit) + ") AS T1 where screen_name = ?",
				referenceUser, beginDate, endDate, tweetLimit, screenName);
	}

	private static String window(int tweetLimit)
	{
		return WINDOW + (tweetLimit > 0 ? " ORDER BY tweetid DESC LIMIT ?" : " ORDER BY tweetid");
	}

	private static PreparedStatement bind(Connection connection, String sql, String referenceUser,
			String beginDate, String endDate, int tweetLimit, String screenName) throws SQLException
	{
		PreparedStatement statement = connection.prepareStatement(sql,
				ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
		try
		{
			int index = 1;
			statement.setString(index++, referenceUser);
			statement.setString(index++, beginDate);
			statement.setString(index++, endDate);
			if (tweetLimit > 0)
			{
				statement.setInt(index++, tweetLimit);
			}
			if (screenName != null)
			{
				statement.setString(index++, screenName);
			}
			return statement;
		}
		catch (SQLException | RuntimeException e)
		{
			statement.close();
			throw e;
		}
	}
}
