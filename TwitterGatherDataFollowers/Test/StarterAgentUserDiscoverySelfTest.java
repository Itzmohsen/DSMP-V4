package TwitterGatherDataFollowers.userRyersonU;

import jade.core.AID;
import jade.domain.FIPAAgentManagement.DFAgentDescription;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class StarterAgentUserDiscoverySelfTest
{
	private StarterAgentUserDiscoverySelfTest()
	{
	}

	public static void main(String[] args)
	{
		Map<String,AID> discovered = new LinkedHashMap<String,AID>();

		DFAgentDescription first = description("Customer1-UserAgent");
		DFAgentDescription second = description("Customer2-UserAgent");
		DFAgentDescription duplicateFirst = description("Customer1-UserAgent");

		require(StarterAgent.rememberUserAgents(
				discovered,
				new DFAgentDescription[] { first, second }) == 2,
				"initial discovery did not count both user agents");
		require(discovered.size() == 2,
				"initial discovery retained the wrong number of agents");
		require(StarterAgent.hasExpectedUserAgentCount(discovered, 2),
				"expected population was not considered ready");

		require(StarterAgent.rememberUserAgents(
				discovered,
				new DFAgentDescription[] { duplicateFirst }) == 0,
				"duplicate user-agent registrations should not increase the count");
		require(discovered.size() == 2,
				"duplicate discovery changed the population size");
		require(!StarterAgent.hasExpectedUserAgentCount(discovered, 3),
			"partial population should not start the simulation");

			Map<String,AID> withStaleAgent = new LinkedHashMap<String,AID>(
					discovered);
			StarterAgent.rememberUserAgents(
					withStaleAgent,
					new DFAgentDescription[] {
							description("OldRun-UserAgent")
					});
			Set<String> expectedRoster = new LinkedHashSet<String>(
					Arrays.asList(
							"Customer1-UserAgent",
							"Customer2-UserAgent"));
			StarterAgent.retainExpectedUserAgents(
					withStaleAgent, expectedRoster);
			require(withStaleAgent.size() == 2,
					"stale user agents should not remain in the startup roster");
			require(!withStaleAgent.containsKey("OldRun-UserAgent"),
					"stale user agent was not filtered from startup discovery");

			require(StarterAgent.hasExpectedUserAgentCount(discovered, 0),
					"zero expected users should be immediately ready");

		System.out.println("StarterAgentUserDiscoverySelfTest passed");
	}

	private static DFAgentDescription description(String localName)
	{
		DFAgentDescription description = new DFAgentDescription();
		description.setName(new AID(localName + "@test-platform", AID.ISGUID));
		return description;
	}

	private static void require(boolean condition, String message)
	{
		if (!condition)
		{
			throw new AssertionError(message);
		}
	}
}
