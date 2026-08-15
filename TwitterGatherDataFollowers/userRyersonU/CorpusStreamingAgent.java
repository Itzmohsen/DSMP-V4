package TwitterGatherDataFollowers.userRyersonU;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Replays non-target text-dataset rows without creating one native JADE thread
 * per corpus user. Selected recommendation targets remain ordinary MobileAgents.
 */
public final class CorpusStreamingAgent extends Agent
{
	private static final long serialVersionUID = 1L;
	private static final String TWEET_BATCH_ONTOLOGY =
			"Tweet Batch From User Agent";
	private static final String TWEET_BATCH_ACK_ONTOLOGY =
			"Tweet Batch Received By Recommender";
	private static final String TWEET_BATCH_PRIMARY_REC_SERVER_PREFIX =
			"__PRIMARY_REC_SERVER__\t";
	private static final int MAX_BATCH_ROWS = Math.max(
			1, Integer.getInteger("dsmp.corpus.batch.rows", 128).intValue());
	private static final long ACK_TIMEOUT_MILLIS = Math.max(
			5000L, Long.getLong("dsmp.corpus.ack.timeout.ms", 60000L));
	private static final int MAX_DELIVERY_ATTEMPTS = Math.max(
			1, Integer.getInteger("dsmp.corpus.ack.max.attempts", 3));

	private final RecommendationRunLifecycle runLifecycle =
			new RecommendationRunLifecycle();
	private ReplayableCorpusStream corpusStream;
	private transient ControllerAgentGui myGui;
	private List<ReplayableCorpusStream.RouteSource> routeSources;
	private final ArrayList<RouteDeliveryState> routeDeliveryStates =
			new ArrayList<RouteDeliveryState>();
	private String activeConversationId;
	private int batchSequence;
	private int nodeCount = 1;
	private int totalInputBatchCount;
	private boolean completed;

	protected void setup()
	{
		Object[] args = getArguments();
		corpusStream = (ReplayableCorpusStream) args[0];
		myGui = args.length > 1 && args[1] instanceof ControllerAgentGui
				? (ControllerAgentGui) args[1] : null;
		routeSources = corpusStream.getRouteSources();
		nodeCount = inferNodeCount(routeSources);
		totalInputBatchCount = estimateInputBatchCount(routeSources);

		try
		{
			DFAgentDescription description = new DFAgentDescription();
			description.setName(getAID());
			ServiceDescription service = new ServiceDescription();
			service.setName("Distributed Recommender System");
			service.setType("User-Agent");
			service.setOwnership("corpus-stream");
			description.addServices(service);
			JadeDfRegistrationGate.register(this, description);
		}
		catch (FIPAException e)
		{
			failStream("Could not register the bounded corpus streamer.", e);
			doDelete();
			return;
		}

		ACLMessage ready = new ACLMessage(ACLMessage.INFORM);
		ready.addReceiver(new AID("Organizing Agent1", AID.ISLOCALNAME));
		ready.setContent(corpusStream.size() + " corpus-stream");
		ready.setOntology("Ready");
		send(ready);

		addBehaviour(new CyclicBehaviour(this)
		{
			private static final long serialVersionUID = 1L;

			public void action()
			{
				ACLMessage message = myAgent.receive();
				if (message == null)
				{
					block();
					return;
				}

				if ("Start SIM".equals(message.getOntology())
						&& message.getPerformative() == ACLMessage.REQUEST)
				{
					RecommendationRunLifecycle.Admission admission =
							runLifecycle.admit(message.getConversationId());
					if (admission == RecommendationRunLifecycle.Admission.NEW_RUN)
					{
						startReplay(message.getConversationId());
					}
					return;
				}

				if (TWEET_BATCH_ACK_ONTOLOGY.equals(message.getOntology())
						&& runLifecycle.isActive(message.getConversationId()))
				{
					acknowledgeBatch(message);
				}
			}
		});
		addBehaviour(new TickerBehaviour(
				this, Math.min(1000L, ACK_TIMEOUT_MILLIS))
		{
			private static final long serialVersionUID = 1L;

			protected void onTick()
			{
				checkPendingBatchTimeout();
				sendAvailableBatches();
			}
		});
	}

	private void startReplay(String conversationId)
	{
		closeRouteReaders();
		activeConversationId = conversationId;
		batchSequence = 0;
		completed = false;
		routeDeliveryStates.clear();
		routeDeliveryStates.addAll(createRouteDeliveryStates(routeSources));
		RecommendationWorkQueueRegistry.beginInputBatchQueue(
				activeConversationId,
				nodeCount,
				totalInputBatchCount);
		sendAvailableBatches();
	}

	private void sendAvailableBatches()
	{
		if (completed)
		{
			return;
		}

		for (int routeIndex = 0;
				routeIndex < routeDeliveryStates.size();
				routeIndex++)
		{
			RouteDeliveryState state = routeDeliveryStates.get(routeIndex);
			if (state.isCompleted() || state.hasPendingBatch()
					|| routeIsBlockedByEarlierIncompleteRoute(
							routeDeliveryStates, routeIndex))
			{
				continue;
			}
			sendNextBatchForRoute(state);
			if (completed)
			{
				return;
			}
		}
		completeReplayIfAllRoutesDone();
	}

	private void sendNextBatchForRoute(RouteDeliveryState state)
	{
		if (completed || state == null || state.isCompleted()
				|| state.hasPendingBatch())
		{
			return;
		}

		try
		{
			DispatchPlan plan = dispatchPlanForRoute(
					state.getRoute(),
					activeConversationId);
			if (!plan.hasReceivers())
			{
				return;
			}
			int sequence = batchSequence + 1;
			boolean prepared = state.prepareNextBatch(
					activeConversationId,
					getLocalName(),
					sequence,
					MAX_BATCH_ROWS,
					plan.getReceivers(),
					plan.getPrimaryServer());
			if (prepared)
			{
				batchSequence = sequence;
				// Dispatch at send time so the last redirect token is not
				// consumed before the batch chooses its actual receiver.
				sendPendingBatch(state);
			}
		}
		catch (IOException e)
		{
			failStream("The bounded corpus streamer could not replay the dataset.", e);
			failReplay(e);
		}
	}

	private void acknowledgeBatch(ACLMessage acknowledgement)
	{
		RouteDeliveryState state =
				findPendingRoute(acknowledgement.getInReplyTo());
		if (state == null)
		{
			return;
		}
		String sender = acknowledgement.getSender() == null
				? null : acknowledgement.getSender().getLocalName();
		int pendingOwnerNode = state.getPendingOwnerNode();
		int pendingWorkItemNumber = state.getPendingWorkItemNumber();
		if (!state.acknowledge(sender, acknowledgement.getInReplyTo()))
		{
			return;
		}
		if (!state.hasPendingBatch())
		{
			RecommendationWorkQueueRegistry.completeInputBatch(
					activeConversationId,
					pendingOwnerNode,
					nodeCount,
					totalInputBatchCount,
					pendingWorkItemNumber);
		}
		sendAvailableBatches();
	}

	private RouteDeliveryState findPendingRoute(String batchId)
	{
		if (batchId == null)
		{
			return null;
		}
		for (RouteDeliveryState state : routeDeliveryStates)
		{
			if (batchId.equals(state.getPendingBatchId()))
			{
				return state;
			}
		}
		return null;
	}

	private void sendPendingBatch(RouteDeliveryState state)
	{
		if (completed || state == null || !state.hasPendingBatch())
		{
			return;
		}
		if (state.getPendingDeliveryAttempts() <= 0)
		{
			DispatchPlan plan = dispatchPlanForRoute(
					state.getRoute(),
					activeConversationId);
			if (!plan.hasReceivers())
			{
				return;
			}
			state.retargetPendingBatch(
					plan.getReceivers(),
					plan.getPrimaryServer());
			RecommendationWorkQueueRegistry.dispatchInputBatch(
					activeConversationId,
					state.getPendingOwnerNode(),
					nodeCount,
					totalInputBatchCount,
					state.getPendingWorkItemNumber());
		}

		ACLMessage batch = new ACLMessage(ACLMessage.INFORM);
		for (String receiverName : state.getPendingAcknowledgements())
		{
			batch.addReceiver(new AID(receiverName, AID.ISLOCALNAME));
		}
		batch.setConversationId(activeConversationId);
		batch.setOntology(TWEET_BATCH_ONTOLOGY);
		batch.setReplyWith(state.getPendingBatchId());
		try
		{
			batch.setContentObject(state.getPendingBatchPayload());
		}
		catch (IOException e)
		{
			failStream(
					"The bounded corpus streamer could not serialize a retryable batch.",
					e);
			failReplay(e);
			return;
		}

		state.markPendingBatchSent(System.currentTimeMillis());
		send(batch);
	}

	private void checkPendingBatchTimeout()
	{
		if (completed)
		{
			return;
		}

		long now = System.currentTimeMillis();
		for (RouteDeliveryState state : routeDeliveryStates)
		{
			if (state.hasPendingBatch()
					&& state.getPendingDeliverySentAt() <= 0L)
			{
				sendPendingBatch(state);
				continue;
			}
			if (!state.isPendingBatchTimedOut(now, ACK_TIMEOUT_MILLIS))
			{
				continue;
			}

			if (state.getPendingDeliveryAttempts()
					>= MAX_DELIVERY_ATTEMPTS)
			{
				IOException timeout = new IOException(
						"Corpus batch " + state.getPendingBatchId()
						+ " for route " + (state.getRouteIndex() + 1)
						+ " was not acknowledged after "
						+ state.getPendingDeliveryAttempts()
						+ " attempt(s). Missing nodes: "
						+ state.describePendingAcknowledgements() + ".");
				failStream(
						"The bounded corpus streamer stopped after repeated acknowledgement timeouts.",
						timeout);
				failReplay(timeout);
				return;
			}

			System.err.println(
					"Retrying corpus batch " + state.getPendingBatchId()
					+ " for route " + (state.getRouteIndex() + 1)
					+ " nodes " + state.describePendingAcknowledgements()
					+ " after " + ACK_TIMEOUT_MILLIS
					+ " ms without acknowledgement.");
			sendPendingBatch(state);
		}
	}

	private void completeReplayIfAllRoutesDone()
	{
		if (!completed && allRouteDeliveryStatesCompleted())
		{
			completeReplay();
		}
	}

	private boolean allRouteDeliveryStatesCompleted()
	{
		for (RouteDeliveryState state : routeDeliveryStates)
		{
			if (!state.isCompleted())
			{
				return false;
			}
		}
		return true;
	}

	private void completeReplay()
	{
		if (completed)
		{
			return;
		}
		completed = true;
		closeRouteReaders();
		ACLMessage completedMessage = new ACLMessage(ACLMessage.INFORM);
		completedMessage.addReceiver(
				new AID("Starter Agent", AID.ISLOCALNAME));
		completedMessage.setConversationId(activeConversationId);
		completedMessage.setContent(
				"Bounded corpus stream completed " + corpusStream.size()
				+ " rows.");
		completedMessage.setOntology("Tweeting Completed");
		send(completedMessage);
	}

	private void failReplay(Exception exception)
	{
		if (completed)
		{
			return;
		}
		completed = true;
		closeRouteReaders();
		for (RouteDeliveryState state : routeDeliveryStates)
		{
			if (state.hasPendingBatch())
			{
				RecommendationWorkQueueRegistry.failInputBatch(
						activeConversationId,
						state.getPendingOwnerNode(),
						nodeCount,
						totalInputBatchCount,
						state.getPendingWorkItemNumber());
			}
			state.clearPendingBatch();
		}
		RecommendationRunAbortRegistry.abort(
				activeConversationId,
				"Bounded corpus delivery failed: " + exception.getMessage());
		ACLMessage failedMessage = new ACLMessage(ACLMessage.FAILURE);
		failedMessage.addReceiver(
				new AID("Starter Agent", AID.ISLOCALNAME));
		failedMessage.setConversationId(activeConversationId);
		failedMessage.setContent(
				"Corpus streaming failed before every dataset row reached "
				+ "its recommender node: " + exception.getMessage());
		failedMessage.setOntology("Corpus Stream Failed");
		send(failedMessage);
	}

	private void closeRouteReaders()
	{
		for (RouteDeliveryState state : routeDeliveryStates)
		{
			state.closeReader();
		}
	}

	private static DispatchPlan dispatchPlanForRoute(
			CorpusStreamRoute route,
			String runId)
	{
		if (route == null)
		{
			return DispatchPlan.blocked();
		}
		LiveWorkloadAdjustmentRegistry.Snapshot controls =
				LiveWorkloadAdjustmentRegistry.snapshot(runId);
		List<Integer> originalReceivers = route.getRecommenderServers();
		boolean replicatedRoute = originalReceivers.size() > 1;
		if (!replicatedRoute
				&& controls.getRedirectRemaining() > 0
				&& controls.isEligible(controls.getRedirectTargetNode()))
		{
			int targetNode = controls.getRedirectTargetNode();
			return new DispatchPlan(
					Collections.singletonList(Integer.valueOf(targetNode)),
					targetNode);
		}
		LinkedHashSet<Integer> receivers = new LinkedHashSet<Integer>();
		for (Integer server : originalReceivers)
		{
			if (server == null)
			{
				continue;
			}
			int node = Math.max(1, server.intValue());
			LiveWorkloadAdjustmentRegistry.NodeControl control =
					controls.controlForNode(node);
			if (!control.isPaused() && !control.isUnavailable())
			{
				receivers.add(Integer.valueOf(node));
				continue;
			}
			if (replicatedRoute)
			{
				return DispatchPlan.blocked();
			}
			if (control.isDraining()
					&& controls.isEligible(control.getDrainTargetNode()))
			{
				receivers.add(Integer.valueOf(control.getDrainTargetNode()));
			}
		}
		if (receivers.isEmpty())
		{
			return DispatchPlan.blocked();
		}
		int primary = route.getPrimaryRecommenderServer();
		if (!receivers.contains(Integer.valueOf(primary)))
		{
			primary = receivers.iterator().next().intValue();
		}
		return new DispatchPlan(new ArrayList<Integer>(receivers), primary);
	}

	static ArrayList<Integer> dispatchReceiversForTest(
			CorpusStreamRoute route,
			String runId)
	{
		return dispatchPlanForRoute(route, runId).getReceivers();
	}

	private static int inferNodeCount(
			List<ReplayableCorpusStream.RouteSource> sources)
	{
		int maxNode = 1;
		if (sources == null)
		{
			return maxNode;
		}
		for (ReplayableCorpusStream.RouteSource source : sources)
		{
			if (source == null || source.getRoute() == null)
			{
				continue;
			}
			maxNode = Math.max(
					maxNode,
					source.getRoute().getPrimaryRecommenderServer());
			for (Integer server : source.getRoute().getRecommenderServers())
			{
				if (server != null)
				{
					maxNode = Math.max(maxNode, server.intValue());
				}
			}
		}
		return Math.max(1, maxNode);
	}

	private static int estimateInputBatchCount(
			List<ReplayableCorpusStream.RouteSource> sources)
	{
		int batches = 0;
		if (sources == null)
		{
			return 0;
		}
		for (ReplayableCorpusStream.RouteSource source : sources)
		{
			if (source == null || source.size() <= 0)
			{
				continue;
			}
			ReplayableCorpusStream.Reader reader = null;
			try
			{
				reader = source.openReader();
				while (reader.hasRemaining())
				{
					if (reader.readBatch(MAX_BATCH_ROWS).isEmpty())
					{
						break;
					}
					batches++;
				}
			}
			catch (IOException e)
			{
				batches += Math.max(
						1,
						(int)Math.ceil(source.size()
								/ (double)Math.max(1, MAX_BATCH_ROWS)));
			}
			finally
			{
				if (reader != null)
				{
					try
					{
						reader.close();
					}
					catch (IOException ignored)
					{
					}
				}
			}
		}
		return Math.max(0, batches);
	}

	static int maxBatchRows()
	{
		return MAX_BATCH_ROWS;
	}

	static ArrayList<RouteDeliveryState> createRouteDeliveryStates(
			List<ReplayableCorpusStream.RouteSource> sources)
	{
		ArrayList<RouteDeliveryState> states =
				new ArrayList<RouteDeliveryState>();
		if (sources == null)
		{
			return states;
		}
		for (int i = 0; i < sources.size(); i++)
		{
			states.add(new RouteDeliveryState(i, sources.get(i)));
		}
		return states;
	}

	static boolean routeIsBlockedByEarlierIncompleteRoute(
			List<RouteDeliveryState> states,
			int routeIndex)
	{
		if (states == null || routeIndex < 0 || routeIndex >= states.size())
		{
			return false;
		}
		RouteDeliveryState current = states.get(routeIndex);
		if (current == null || current.isCompleted())
		{
			return false;
		}
		for (int i = 0; i < routeIndex; i++)
		{
			RouteDeliveryState earlier = states.get(i);
			if (earlier != null
					&& !earlier.isCompleted()
					&& current.sharesAnyReceiverWith(earlier))
			{
				return true;
			}
		}
		return false;
	}

	private static final class DispatchPlan
	{
		private final ArrayList<Integer> receivers;
		private final int primaryServer;

		private DispatchPlan(List<Integer> receivers, int primaryServer)
		{
			this.receivers = new ArrayList<Integer>();
			if (receivers != null)
			{
				for (Integer receiver : receivers)
				{
					if (receiver != null && receiver.intValue() > 0
							&& !this.receivers.contains(receiver))
					{
						this.receivers.add(receiver);
					}
				}
			}
			this.primaryServer = Math.max(1, primaryServer);
		}

		static DispatchPlan blocked()
		{
			return new DispatchPlan(null, 1);
		}

		boolean hasReceivers()
		{
			return !receivers.isEmpty();
		}

		ArrayList<Integer> getReceivers()
		{
			return new ArrayList<Integer>(receivers);
		}

		int getPrimaryServer()
		{
			return primaryServer;
		}
	}

	private void failStream(String message, Exception exception)
	{
		System.err.println(message);
		exception.printStackTrace();
		if (myGui != null)
		{
			myGui.appendResult(message + " " + exception.getMessage());
		}
	}

	protected void takeDown()
	{
		closeRouteReaders();
		if (corpusStream != null)
		{
			try
			{
				corpusStream.close();
			}
			catch (IOException e)
			{
				System.err.println(
						"Could not remove bounded corpus stream files: "
						+ e.getMessage());
			}
			corpusStream = null;
		}
		try
		{
			DFService.deregister(this);
		}
		catch (FIPAException e)
		{
			System.err.println(
					"Could not deregister bounded corpus streamer: "
					+ e.getMessage());
		}
	}

	static final class RouteDeliveryState
	{
		private final int routeIndex;
		private final ReplayableCorpusStream.RouteSource routeSource;
		private final CorpusStreamRoute route;
		private ReplayableCorpusStream.Reader routeReader;
		private String pendingBatchId;
		private ArrayList<String> pendingBatchPayload;
		private int pendingDeliveryAttempts;
		private long pendingDeliverySentAt;
		private int pendingOwnerNode;
		private int pendingWorkItemNumber;
		private final Set<String> pendingAcknowledgements =
				new LinkedHashSet<String>();
		private boolean completed;

		RouteDeliveryState(
				int routeIndex,
				ReplayableCorpusStream.RouteSource routeSource)
		{
			this.routeIndex = routeIndex;
			this.routeSource = routeSource;
			this.route = routeSource.getRoute();
		}

		int getRouteIndex()
		{
			return routeIndex;
		}

		CorpusStreamRoute getRoute()
		{
			return route;
		}

		boolean isCompleted()
		{
			return completed;
		}

		boolean hasPendingBatch()
		{
			return pendingBatchId != null
					&& pendingBatchPayload != null
					&& !pendingAcknowledgements.isEmpty();
		}

		String getPendingBatchId()
		{
			return pendingBatchId;
		}

		ArrayList<String> getPendingBatchPayload()
		{
			return pendingBatchPayload;
		}

		Set<String> getPendingAcknowledgements()
		{
			return pendingAcknowledgements;
		}

		int getPendingDeliveryAttempts()
		{
			return pendingDeliveryAttempts;
		}

		long getPendingDeliverySentAt()
		{
			return pendingDeliverySentAt;
		}

		int getPendingOwnerNode()
		{
			return Math.max(1, pendingOwnerNode);
		}

		int getPendingWorkItemNumber()
		{
			return Math.max(0, pendingWorkItemNumber);
		}

		int getPendingRowCount()
		{
			return pendingBatchPayload == null
					? 0 : Math.max(0, pendingBatchPayload.size() - 1);
		}

		boolean prepareNextBatch(
				String conversationId,
				String senderName,
				int sequence,
				int maximumRows,
				List<Integer> receivers,
				int primaryServer) throws IOException
		{
			if (completed || hasPendingBatch())
			{
				return false;
			}
			if (routeReader == null)
			{
				routeReader = routeSource.openReader();
			}

			ArrayList<String> rows = routeReader.readBatch(maximumRows);
			if (rows.isEmpty())
			{
				completed = true;
				closeReader();
				return false;
			}

			ArrayList<String> payload =
					new ArrayList<String>(rows.size() + 1);
			payload.add(TWEET_BATCH_PRIMARY_REC_SERVER_PREFIX
					+ Math.max(1, primaryServer));
			payload.addAll(rows);

			if (!setPendingReceivers(receivers))
			{
				throw new IOException(
						"Corpus route " + (routeIndex + 1)
						+ " has no recommender receivers.");
			}

			pendingBatchId = conversationId + ":" + senderName + ":"
					+ sequence;
			pendingBatchPayload = payload;
			pendingDeliveryAttempts = 0;
			pendingDeliverySentAt = 0L;
			pendingOwnerNode = Math.max(1, primaryServer);
			pendingWorkItemNumber = Math.max(1, sequence);
			return true;
		}

		void retargetPendingBatch(
				List<Integer> receivers,
				int primaryServer)
		{
			if (!hasPendingBatch() || !setPendingReceivers(receivers))
			{
				return;
			}
			pendingOwnerNode = Math.max(1, primaryServer);
			if (pendingBatchPayload != null && !pendingBatchPayload.isEmpty())
			{
				pendingBatchPayload.set(
						0,
						TWEET_BATCH_PRIMARY_REC_SERVER_PREFIX
								+ pendingOwnerNode);
			}
		}

		private boolean setPendingReceivers(List<Integer> receivers)
		{
			pendingAcknowledgements.clear();
			if (receivers == null)
			{
				return false;
			}
			for (Integer server : receivers)
			{
				if (server != null && server.intValue() > 0)
				{
					pendingAcknowledgements.add(
							"Recommender-ServiceAgent" + server.intValue());
				}
			}
			return !pendingAcknowledgements.isEmpty();
		}

		boolean acknowledge(String sender, String inReplyTo)
		{
			if (sender == null
					|| pendingBatchId == null
					|| !pendingBatchId.equals(inReplyTo)
					|| !pendingAcknowledgements.remove(sender))
			{
				return false;
			}
			if (pendingAcknowledgements.isEmpty())
			{
				clearPendingBatch();
			}
			return true;
		}

		void markPendingBatchSent(long sentAt)
		{
			pendingDeliveryAttempts++;
			pendingDeliverySentAt = sentAt;
		}

		boolean isPendingBatchTimedOut(long now, long timeoutMillis)
		{
			return hasPendingBatch()
					&& pendingDeliverySentAt > 0L
					&& now - pendingDeliverySentAt >= timeoutMillis;
		}

		String describePendingAcknowledgements()
		{
			return pendingAcknowledgements.toString();
		}

		void clearPendingBatch()
		{
			pendingBatchId = null;
			pendingBatchPayload = null;
			pendingDeliveryAttempts = 0;
			pendingDeliverySentAt = 0L;
			pendingOwnerNode = 0;
			pendingWorkItemNumber = 0;
			pendingAcknowledgements.clear();
		}

		void closeReader()
		{
			if (routeReader == null)
			{
				return;
			}
			try
			{
				routeReader.close();
			}
			catch (IOException e)
			{
				System.err.println(
						"Could not close bounded corpus stream route "
						+ (routeIndex + 1) + ": " + e.getMessage());
			}
			finally
			{
				routeReader = null;
			}
		}

		boolean sharesAnyReceiverWith(RouteDeliveryState other)
		{
			if (other == null)
			{
				return false;
			}
			for (Integer server : route.getRecommenderServers())
			{
				if (other.route.getRecommenderServers().contains(server))
				{
					return true;
				}
			}
			return false;
		}
	}
}
