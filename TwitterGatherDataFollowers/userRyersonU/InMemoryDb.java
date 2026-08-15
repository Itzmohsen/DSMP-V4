package TwitterGatherDataFollowers.userRyersonU;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class InMemoryDb {

	private ArrayList<Tweet> tweets;
	private final Map<String,ArrayList<Tweet>> tweetsByUser;

	public InMemoryDb()
	{
		tweets = new ArrayList<Tweet>();
		tweetsByUser = new LinkedHashMap<String,ArrayList<Tweet>>();
	}

	public ArrayList<Tweet> getTweets()
	{
		return tweets;
	}
	
	public void setTweets(ArrayList<Tweet> tweets)
	{
		this.tweets = tweets == null ? new ArrayList<Tweet>() : tweets;
		rebuildUserIndex();
	}

	public void addTweet(Tweet t)
	{
		tweets.add(t);
		indexTweet(t);
	}

	public ArrayList<Tweet> getTweetsFromUser(String username)
	{
		List<Tweet> tweetsFromUser = getTweetsViewFromUser(username);
		return new ArrayList<Tweet>(tweetsFromUser);
	}

	List<Tweet> getTweetsViewFromUser(String username)
	{
		ArrayList<Tweet> tweetsFromUser = tweetsByUser.get(username);
		return tweetsFromUser == null
				? Collections.<Tweet>emptyList()
				: tweetsFromUser;
	}

	public int getTweetCountFromUser(String username)
	{
		return getTweetsViewFromUser(username).size();
	}
	
	public int getTotalTweets()
	{
		return tweets.size(); 
	}

	public void clearDb()
	{
		tweets.clear();
		tweetsByUser.clear();
	}

	void refreshUserIndex()
	{
		rebuildUserIndex();
	}

	private void rebuildUserIndex()
	{
		tweetsByUser.clear();
		for (Tweet tweet : tweets)
		{
			indexTweet(tweet);
		}
	}

	private void indexTweet(Tweet tweet)
	{
		if (tweet == null)
		{
			return;
		}
		String username = tweet.getUser();
		ArrayList<Tweet> userTweets = tweetsByUser.get(username);
		if (userTweets == null)
		{
			userTweets = new ArrayList<Tweet>();
			tweetsByUser.put(username, userTweets);
		}
		userTweets.add(tweet);
	}
}
