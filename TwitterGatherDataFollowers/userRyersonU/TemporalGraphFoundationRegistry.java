package TwitterGatherDataFollowers.userRyersonU;

final class TemporalGraphFoundationRegistry
{
	private static TemporalGraphFoundationModelSupport.Result latestResult;
	private static long revision;

	private TemporalGraphFoundationRegistry()
	{
	}

	static synchronized void record(
			TemporalGraphFoundationModelSupport.Result result)
	{
		latestResult = result;
		revision++;
	}

	static synchronized TemporalGraphFoundationModelSupport.Result latest()
	{
		return latestResult;
	}

	static synchronized long revision()
	{
		return revision;
	}

	static synchronized void clear()
	{
		latestResult = null;
		revision++;
	}
}
