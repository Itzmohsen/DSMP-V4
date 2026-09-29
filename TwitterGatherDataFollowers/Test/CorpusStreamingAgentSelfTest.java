package TwitterGatherDataFollowers.userRyersonU;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

public final class CorpusStreamingAgentSelfTest
{
	private CorpusStreamingAgentSelfTest()
	{
	}

	public static void main(String[] args) throws Exception
	{
		parallelizesOnlyDisjointRoutes();
		preservesSingleInFlightBatchPerRoute();
		doesNotSplitOneUserAcrossInputBatches();
		inputDispatchHonorsLiveRedirectForSingleOwnerRoutes();
		System.out.println("CorpusStreamingAgentSelfTest passed");
	}

	private static void parallelizesOnlyDisjointRoutes() throws Exception
	{
		ReplayableCorpusStream stream = createFourRouteStream();
		ArrayList<CorpusStreamingAgent.RouteDeliveryState> states =
				CorpusStreamingAgent.createRouteDeliveryStates(
						stream.getRouteSources());
		try
		{
			require(states.size() == 4,
					"test stream did not create four route states");
			require("[1]".equals(routeServers(states.get(0))),
					"route 1 should feed only recommender 1");
			require("[2]".equals(routeServers(states.get(1))),
					"route 2 should feed only recommender 2");
			require("[1, 2]".equals(routeServers(states.get(2))),
					"route 3 should feed recommenders 1 and 2");
			require("[3]".equals(routeServers(states.get(3))),
					"route 4 should feed only recommender 3");

			require(!CorpusStreamingAgent.routeIsBlockedByEarlierIncompleteRoute(
							states, 0),
					"first route should be dispatchable");
			require(!CorpusStreamingAgent.routeIsBlockedByEarlierIncompleteRoute(
							states, 1),
					"disjoint second route should be dispatchable");
			require(CorpusStreamingAgent.routeIsBlockedByEarlierIncompleteRoute(
							states, 2),
					"overlapping replicated route must wait for routes 1 and 2");
			require(!CorpusStreamingAgent.routeIsBlockedByEarlierIncompleteRoute(
							states, 3),
					"disjoint route 4 should not wait for routes 1 or 2");

			prepare(states.get(0), 1);
			prepare(states.get(1), 2);
			prepare(states.get(3), 3);
			require(states.get(0).hasPendingBatch()
					&& states.get(1).hasPendingBatch()
					&& states.get(3).hasPendingBatch(),
					"disjoint routes did not allow concurrent pending batches");
			require(CorpusStreamingAgent.routeIsBlockedByEarlierIncompleteRoute(
							states, 2),
					"replicated route started while an earlier shared node route was active");

			ackAll(states.get(0));
			completeAfterFinalBatch(states.get(0), 4);
			require(CorpusStreamingAgent.routeIsBlockedByEarlierIncompleteRoute(
							states, 2),
					"replicated route ignored earlier route 2 ordering");
			ackAll(states.get(1));
			completeAfterFinalBatch(states.get(1), 5);
			require(!CorpusStreamingAgent.routeIsBlockedByEarlierIncompleteRoute(
							states, 2),
					"replicated route stayed blocked after shared-node routes completed");
		}
		finally
		{
			close(states);
			stream.close();
		}
	}

	private static void preservesSingleInFlightBatchPerRoute() throws Exception
	{
		ReplayableCorpusStream stream = createFourRouteStream();
		ArrayList<CorpusStreamingAgent.RouteDeliveryState> states =
				CorpusStreamingAgent.createRouteDeliveryStates(
						stream.getRouteSources());
		try
		{
			CorpusStreamingAgent.RouteDeliveryState replicated = states.get(2);
			prepare(replicated, 7);
			String firstBatchId = replicated.getPendingBatchId();
			require(replicated.hasPendingBatch(),
					"replicated route did not prepare a pending batch");
			require(replicated.getPendingRowCount() == 1,
					"replicated route changed the row payload");
			require(!replicated.prepareNextBatch(
							"run-1",
							"Corpus-Stream-UserAgent",
							8,
							CorpusStreamingAgent.maxBatchRows(),
							replicated.getRoute().getRecommenderServers(),
							replicated.getRoute().getPrimaryRecommenderServer()),
					"route accepted a second batch before acknowledgements");

			require(replicated.acknowledge(
							"Recommender-ServiceAgent1",
							firstBatchId),
					"first receiver acknowledgement was rejected");
			require(replicated.hasPendingBatch(),
					"route advanced before all receivers acknowledged");
			require(replicated.acknowledge(
							"Recommender-ServiceAgent2",
							firstBatchId),
					"second receiver acknowledgement was rejected");
			require(!replicated.hasPendingBatch(),
					"route kept a pending batch after all acknowledgements");
			completeAfterFinalBatch(replicated, 9);
			require(replicated.isCompleted(),
					"route did not complete after its final acknowledged batch");
		}
		finally
		{
			close(states);
			stream.close();
		}
	}

	private static void doesNotSplitOneUserAcrossInputBatches()
			throws Exception
	{
		ArrayList<Tweet> tweets = new ArrayList<Tweet>();
		tweets.add(new Tweet("u2 text", 4L, new Date(4L), "u2"));
		tweets.add(new Tweet("u1 third", 3L, new Date(3L), "u1"));
		tweets.add(new Tweet("u1 second", 2L, new Date(2L), "u1"));
		tweets.add(new Tweet("u1 first", 1L, new Date(1L), "u1"));

		Map<String,CorpusStreamRoute> routes =
				new LinkedHashMap<String,CorpusStreamRoute>();
		routes.put("u1", new CorpusStreamRoute(Arrays.asList(1), 1));
		routes.put("u2", new CorpusStreamRoute(Arrays.asList(1), 1));
		Map<String,String> followees = new LinkedHashMap<String,String>();
		followees.put("u1", "f1");
		followees.put("u2", "f2");
		ReplayableCorpusStream stream = ReplayableCorpusStream.create(
				tweets,
				routes,
				followees,
				Collections.<String>emptySet());
		ArrayList<CorpusStreamingAgent.RouteDeliveryState> states =
				CorpusStreamingAgent.createRouteDeliveryStates(
						stream.getRouteSources());
		try
		{
			CorpusStreamingAgent.RouteDeliveryState state = states.get(0);
			require(state.prepareNextBatch(
							"run-1",
							"Corpus-Stream-UserAgent",
							1,
							1,
							state.getRoute().getRecommenderServers(),
							state.getRoute().getPrimaryRecommenderServer()),
					"first user-safe batch was not prepared");
			require(state.getPendingRowCount() == 3,
					"one user's rows were split across input batches");
			ackAll(state);
			require(state.prepareNextBatch(
							"run-1",
							"Corpus-Stream-UserAgent",
							2,
							1,
							state.getRoute().getRecommenderServers(),
							state.getRoute().getPrimaryRecommenderServer()),
					"second user-safe batch was not prepared");
			require(state.getPendingRowCount() == 1,
					"second user's batch size changed unexpectedly");
		}
		finally
		{
			close(states);
			stream.close();
		}
	}

	/**
	 * Batches are routed at run time through the run's scheduler: a paused
	 * owner holds its users, a redirect moves the next users whole, a routed
	 * user keeps its receiver, and replicated users go to every node (held
	 * only while a node is paused, never redirected).
	 */
	private static void inputDispatchHonorsLiveRedirectForSingleOwnerRoutes()
			throws Exception
	{
		String runId = RecommendationRunLifecycle.createRunId();
		NodeCapacityRegistry.resetToDefaults(4, 4096);
		RecommendationSchedulerRegistry.apply(
				RecommendationSchedulerPlan.Mode.SCORING_SAFE,
				SchedulerLabEngine.SchedulingPolicy.CAPACITY_WEIGHTED,
				4,
				4096,
				"corpus streamer redirect self-test");
		RecommendationSchedulerRegistry.beginRun(runId, 4, 4096, ControllerAgent.COS_SIM, 1);

		ArrayList<Tweet> tweets = new ArrayList<Tweet>();
		tweets.add(new Tweet("u34 text", 3L, new Date(3L), "u34"));
		tweets.add(new Tweet("u3b text", 2L, new Date(2L), "u3b"));
		tweets.add(new Tweet("u3a text", 1L, new Date(1L), "u3a"));
		Map<String,CorpusStreamRoute> routes = new LinkedHashMap<String,CorpusStreamRoute>();
		routes.put("u3a", new CorpusStreamRoute(Arrays.asList(3), 3));
		routes.put("u3b", new CorpusStreamRoute(Arrays.asList(3), 3));
		routes.put("u34", new CorpusStreamRoute(Arrays.asList(3, 4), 3));
		ReplayableCorpusStream stream = ReplayableCorpusStream.create(
				tweets, routes, new LinkedHashMap<String,String>(), Collections.<String>emptySet());
		ArrayList<CorpusStreamingAgent.RouteDeliveryState> states =
				CorpusStreamingAgent.createRouteDeliveryStates(stream.getRouteSources());
		try
		{
			CorpusStreamingAgent.RouteDeliveryState single = states.get(0);
			CorpusStreamingAgent.RouteDeliveryState replicated = states.get(1);
			require(single.prepareNextBatch("run-1", "Corpus-Stream-UserAgent", 1, 1, null, 0),
					"single-owner batch was not prepared");
			require(!single.isAssigned(), "a batch must stay unrouted until it is sent");
			require(replicated.prepareNextBatch("run-1", "Corpus-Stream-UserAgent", 2, 1, null, 0),
					"replicated batch was not prepared");

			LiveWorkloadAdjustmentRegistry.setPaused(runId, 3, true);
			require(CorpusStreamingAgent.dispatchReceiversForTest(single, runId).isEmpty(),
					"a paused owner's users should be held, not moved");
			require(CorpusStreamingAgent.dispatchReceiversForTest(replicated, runId).isEmpty(),
					"replicated input should wait while a receiver is paused");
			LiveWorkloadAdjustmentRegistry.redirectPendingToNode(runId, 2, 10);
			require("[2]".equals(CorpusStreamingAgent.dispatchReceiversForTest(single, runId).toString()),
					"a live redirect did not take the held users");
			LiveWorkloadAdjustmentRegistry.setPaused(runId, 3, false);
			require("[2]".equals(CorpusStreamingAgent.dispatchReceiversForTest(single, runId).toString()),
					"a routed user changed receiver (its rows would split)");
			LiveWorkloadAdjustmentRegistry.drainNodeTo(runId, 3, 1, 0);
			require("[3, 4]".equals(CorpusStreamingAgent.dispatchReceiversForTest(replicated, runId).toString()),
					"replicated input must still reach a drained node, which keeps its data phases");
		}
		finally
		{
			close(states);
			stream.close();
			SchedulingRuntime.close(runId, "test finished");
			RecommendationSchedulerRegistry.restoreDefault(4, 4096);
		}
	}

	private static ReplayableCorpusStream createFourRouteStream()
			throws Exception
	{
		ArrayList<Tweet> tweets = new ArrayList<Tweet>();
		tweets.add(new Tweet("u3 text", 4L, new Date(4L), "u3"));
		tweets.add(new Tweet("u12 text", 3L, new Date(3L), "u12"));
		tweets.add(new Tweet("u2 text", 2L, new Date(2L), "u2"));
		tweets.add(new Tweet("u1 text", 1L, new Date(1L), "u1"));

		Map<String,CorpusStreamRoute> routes =
				new LinkedHashMap<String,CorpusStreamRoute>();
		routes.put("u1", new CorpusStreamRoute(Arrays.asList(1), 1));
		routes.put("u2", new CorpusStreamRoute(Arrays.asList(2), 2));
		routes.put("u12", new CorpusStreamRoute(Arrays.asList(1, 2), 1));
		routes.put("u3", new CorpusStreamRoute(Arrays.asList(3), 3));

		Map<String,String> followees = new LinkedHashMap<String,String>();
		followees.put("u1", "f1");
		followees.put("u2", "f2");
		followees.put("u12", "f12");
		followees.put("u3", "f3");

		return ReplayableCorpusStream.create(
				tweets,
				routes,
				followees,
				Collections.<String>emptySet());
	}

	private static void prepare(
			CorpusStreamingAgent.RouteDeliveryState state,
			int sequence) throws Exception
	{
		require(state.prepareNextBatch(
						"run-1",
						"Corpus-Stream-UserAgent",
						sequence,
						CorpusStreamingAgent.maxBatchRows(),
						state.getRoute().getRecommenderServers(),
						state.getRoute().getPrimaryRecommenderServer()),
				"route did not prepare its expected next batch");
	}

	private static void ackAll(CorpusStreamingAgent.RouteDeliveryState state)
	{
		String batchId = state.getPendingBatchId();
		for (Integer server : state.getRoute().getRecommenderServers())
		{
			require(state.acknowledge(
							"Recommender-ServiceAgent" + server,
							batchId),
					"acknowledgement failed for recommender " + server);
		}
	}

	private static void completeAfterFinalBatch(
			CorpusStreamingAgent.RouteDeliveryState state,
			int sequence) throws Exception
	{
		require(!state.prepareNextBatch(
						"run-1",
						"Corpus-Stream-UserAgent",
						sequence,
						CorpusStreamingAgent.maxBatchRows(),
						state.getRoute().getRecommenderServers(),
						state.getRoute().getPrimaryRecommenderServer()),
				"route prepared an unexpected extra batch");
		require(state.isCompleted(),
				"route did not mark itself complete after final replay");
	}

	private static String routeServers(
			CorpusStreamingAgent.RouteDeliveryState state)
	{
		return state.getRoute().getRecommenderServers().toString();
	}

	private static void close(
			ArrayList<CorpusStreamingAgent.RouteDeliveryState> states)
	{
		for (CorpusStreamingAgent.RouteDeliveryState state : states)
		{
			state.closeReader();
		}
	}

	private static void require(boolean condition, String message)
	{
		if (!condition)
		{
			throw new AssertionError(message);
		}
	}
}
