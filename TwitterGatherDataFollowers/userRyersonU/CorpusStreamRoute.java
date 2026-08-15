package TwitterGatherDataFollowers.userRyersonU;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class CorpusStreamRoute
{
	private final List<Integer> recommenderServers;
	private final int primaryRecommenderServer;

	CorpusStreamRoute(List<Integer> recommenderServers, int primaryRecommenderServer)
	{
		if (recommenderServers == null || recommenderServers.isEmpty())
		{
			throw new IllegalArgumentException(
					"A corpus stream route needs at least one recommender server.");
		}
		this.recommenderServers = Collections.unmodifiableList(
				new ArrayList<Integer>(recommenderServers));
		this.primaryRecommenderServer = primaryRecommenderServer;
	}

	List<Integer> getRecommenderServers()
	{
		return recommenderServers;
	}

	int getPrimaryRecommenderServer()
	{
		return primaryRecommenderServer;
	}

	public boolean equals(Object other)
	{
		if (this == other)
		{
			return true;
		}
		if (!(other instanceof CorpusStreamRoute))
		{
			return false;
		}
		CorpusStreamRoute route = (CorpusStreamRoute) other;
		return primaryRecommenderServer == route.primaryRecommenderServer
				&& recommenderServers.equals(route.recommenderServers);
	}

	public int hashCode()
	{
		return 31 * recommenderServers.hashCode() + primaryRecommenderServer;
	}
}
