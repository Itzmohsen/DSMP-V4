package TwitterGatherDataFollowers.userRyersonU;
 

import java.awt.AWTException;
import java.awt.Robot;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Serializable;
import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Map.Entry;
import java.io.*;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.*;
import jade.domain.AMSService;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.AMSAgentDescription;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.SearchConstraints;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.UnreadableException;

import org.neuroph.core.NeuralNetwork;
import org.neuroph.nnet.MultiLayerPerceptron;
import org.neuroph.core.data.DataSet;
import org.neuroph.core.data.DataSetRow;
import org.neuroph.util.TransferFunctionType;
import org.neuroph.core.Layer;
import org.neuroph.core.Neuron;
import org.neuroph.core.Connection;
import org.neuroph.core.Weight;
import org.neuroph.core.transfer.TransferFunction;
import org.neuroph.contrib.learning.SoftMax;
import weka.classifiers.functions.MultilayerPerceptron;   //Sepide
import weka.core.Instances;  //Sepide
import weka.classifiers.Classifier;   //Sepide
import weka.classifiers.functions.neural.LinearUnit;   //Sepide
import weka.classifiers.functions.neural.NeuralConnection;   //Sepide
import weka.classifiers.functions.neural.NeuralNode;     //Sepide
import java.lang.reflect.*;   //Sepide 
//S import weka.classifiers.functions.neural;      //Sepide


public class OrganizingAgent extends Agent 
{
	private static final long serialVersionUID = 1L;

	private static final int COS_SIM = 0;
	private static final int K_MEANS = 1;
	private static final int SVM = 2;
	private static final int MLP = 3;
	public static final int Doc2Vec = 4;             // added by Sepide
    public static final int CommonNeighbors = 5;        // added by Sepide	
	public static final int K_MEANSEUCLIDEAN = 6;   // added by Sepide 
	public static final int TEMPORAL_GRAPH_FOUNDATION = 8;
	private static final long DEFAULT_INITIALIZATION_STALL_TIMEOUT_MILLIS =
			180000L;
	private static final long INITIALIZATION_STALL_TIMEOUT_MILLIS =
			Math.max(
					30000L,
					Long.getLong(
							"dsmp.initialization.stallTimeoutMillis",
							DEFAULT_INITIALIZATION_STALL_TIMEOUT_MILLIS)
							.longValue());
	
	private ArrayList<String> global_interestlistData = new ArrayList<String>();		

	private int numNodes = 0;
	private int count_contiuneTweeting_messages   = 0;
	private int count_contiuneTweeting_messages_2 = 0;

	private	int totalvectorstosend = 0;			
	private int numberofuserparticipated =0;

	private long a = 0;

	private AID[] allRecAgents;		
	private AID[] allUserAgents;
	private Agent thisAgent = this;

	private LinkedHashMap<String, AID> useragent_replyIDs = new LinkedHashMap<String, AID>(); 
	private long timestampstart =0;
	private AID   AID_agent_name;
	private String agent_name;
	private int numberofusers = 0;
	private int usercounter=0;

	private LinkedHashMap<String, String> user_is_connectedtothis_RecServer = new LinkedHashMap<String, String>();
	private LinkedHashMap<String, LinkedHashMap<String, Double>> ListofUsers_temp = new LinkedHashMap<String, LinkedHashMap<String, Double>>();
	private LinkedHashMap<String, Long> ListofUsers_for_time       = new LinkedHashMap<String, Long>();
	private LinkedHashMap<String, Long> ListofUsers_for_MaxServerProctime = new LinkedHashMap<String, Long>();

	private LinkedHashMap<String, Integer> user_respondedRecAgents = new LinkedHashMap<String, Integer>();
	private LinkedHashMap<String, Long> userRequestTimes = new LinkedHashMap<String, Long>();
	private LinkedHashMap<String, Long> userMsgPassTimes = new LinkedHashMap<String, Long>();

	//@Jason added algorithmRec 
	private int algorithmRec;

	private ArrayList<RecommendationScorePayload> recommendationScorePayloads =
			new ArrayList<RecommendationScorePayload>();
	private Map<String,RecommendationResultPayload> recommendationResults =
			new LinkedHashMap<String,RecommendationResultPayload>();
	private int recMergeCount = 0;
	private boolean recommendationRunFailed;
	private RecommendationExperimentConfig recommendationConfig;
	private final RecommendationRunLifecycle runLifecycle =
			new RecommendationRunLifecycle();

	private int usersRequestedCount = 0;
	private int userInterestListSent = 0;
	private boolean initializationCompleted;
	private boolean initializationFailed;
	private TickerBehaviour recommenderReadinessWaiter;
	private long lastInitializationProgressAt;

	transient protected ControllerAgentGui myGui;
	
	private List<MultiLayerPerceptron> listMLP;
	private List<NeuralNetwork> listNN;
	private List<String> mlpModelPaths;
	private List<String> sparseMlpModelPaths;
	private int averageWeightCount;
	private int sparseMlpAverageWeightCount;

	protected void setup() 
	{
		Object[] args = getArguments();


		numberofuserparticipated	 = (Integer) args[13];	  
		numNodes 		 = (Integer) args[15];
		lastInitializationProgressAt = System.currentTimeMillis();

		//@Jason added algorithmRec argument
		algorithmRec = (Integer) args[16];
		recommendationConfig = args.length > 17
				&& args[17] instanceof RecommendationExperimentConfig
				? (RecommendationExperimentConfig) args[17]
				: RecommendationExperimentConfig.legacyDefault(algorithmRec, ControllerAgent.FROM_TEXT);


		myGui = (ControllerAgentGui) args[18];

		if (algorithmRec == MLP && numNodes > 1)
		{
			listMLP = new ArrayList<MultiLayerPerceptron>();
			listNN = new ArrayList<NeuralNetwork>();
			mlpModelPaths = new ArrayList<String>();
			sparseMlpModelPaths = new ArrayList<String>();
			averageWeightCount = 0;
			sparseMlpAverageWeightCount = 0;
		}
			

		recMergeCount = 0;

		try {
			DFAgentDescription dfd = new DFAgentDescription();
			dfd.setName(getAID());
			ServiceDescription sd = new ServiceDescription();
			sd.setName("Distributed Recommender System");
			sd.setType("Organizing Agent");
			dfd.addServices(sd);
			DFService.register(this, dfd);
			System.out.println(getLocalName()+" REGISTERED WITH THE DF");
		} catch (FIPAException e) {
			e.printStackTrace();
		}		
		System.out.println("Hello! I am " + getAID().getName()+ " and is setup properly.");

		agent_name = getLocalName();
		AID_agent_name = getAID();

		setExpectedRecommenderAgents();


		DFAgentDescription template2 = new DFAgentDescription();
		ServiceDescription sd2 = new ServiceDescription();
		sd2.setType("User-Agent");
		template2.addServices(sd2);
		try {
			DFAgentDescription[] result = DFService.search(this, template2);
			allUserAgents = new AID[result.length];
			for (int i = 0; i < result.length; ++i) {
				allUserAgents[i] = result[i].getName();
			}
		}
		catch (FIPAException fe) {
			fe.printStackTrace();
		}

			InitializeBehaviour initialBehaviour = new InitializeBehaviour(this);
			addBehaviour(initialBehaviour);
			ensureInitializationReadinessWaiter();
			
			setQueueSize(0);
		}
		
			private void setExpectedRecommenderAgents()
			{
				allRecAgents = new AID[numNodes];
			for (int i = 0; i < numNodes; i++)
			{
					allRecAgents[i] = new AID("Recommender-ServiceAgent" + (i + 1), AID.ISLOCALNAME);
				}
			}

			private boolean allExpectedRecommenderAgentsAvailable()
			{
				DFAgentDescription template = new DFAgentDescription();
				ServiceDescription service = new ServiceDescription();
				service.setType("Recommender Agent");
				template.addServices(service);
				try
				{
					DFAgentDescription[] results = DFService.search(this, template);
					TreeSet<String> availableNames = new TreeSet<String>();
					for (DFAgentDescription result : results)
					{
						if (result != null && result.getName() != null)
						{
							availableNames.add(result.getName().getLocalName());
						}
					}
					for (AID recommender : allRecAgents)
					{
						if (!availableNames.contains(recommender.getLocalName()))
						{
							return false;
						}
					}
					return true;
				}
				catch (FIPAException e)
				{
					e.printStackTrace();
					return false;
				}
			}

			private void attemptInitializationCompletion()
			{
				if (initializationCompleted || initializationFailed)
				{
					return;
				}
				if (usercounter < numberofuserparticipated)
				{
					ensureInitializationReadinessWaiter();
					if (initializationHasStalled())
					{
						failInitialization(
								"Initialization stalled after "
								+usercounter+"/"+numberofuserparticipated
								+" user agents reported ready. JADE DF registration "
								+"or user-agent startup stopped making progress.");
					}
					return;
				}
				setExpectedRecommenderAgents();
				if (!allExpectedRecommenderAgentsAvailable())
				{
					ensureInitializationReadinessWaiter();
					if (initializationHasStalled())
					{
						failInitialization(
								"Initialization stalled while waiting for "
								+numNodes+" recommender node(s) to register.");
					}
					return;
				}

				initializationCompleted = true;
				if (recommenderReadinessWaiter != null)
				{
					removeBehaviour(recommenderReadinessWaiter);
					recommenderReadinessWaiter = null;
				}

				ACLMessage update = new ACLMessage(ACLMessage.REQUEST);
				for (AID recommender : allRecAgents)
				{
					update.addReceiver(recommender);
				}
				update.setContent("Update user list");
				update.setOntology(
						"Update Connected UserAgent List for this Rec Server");
				send(update);

				myGui.enableAllButtons();
				myGui.enableList();
				myGui.showRunMessage(runLifecycle.getActiveRunId(), "initialize");
				System.out.println(
						getLocalName()
						+ " all live participants and recommender nodes are ready.");
				myGui.enableStartButton();
				myGui.testPrint();
			}

			private void ensureInitializationReadinessWaiter()
			{
				if (recommenderReadinessWaiter != null)
				{
					return;
				}
				recommenderReadinessWaiter =
						new TickerBehaviour(this, 1000L)
						{
							private static final long serialVersionUID = 1L;

							protected void onTick()
							{
								attemptInitializationCompletion();
							}
						};
				addBehaviour(recommenderReadinessWaiter);
			}

			private boolean initializationHasStalled()
			{
				return System.currentTimeMillis() - lastInitializationProgressAt
						>= INITIALIZATION_STALL_TIMEOUT_MILLIS;
			}

			private void failInitialization(String message)
			{
				if (initializationCompleted || initializationFailed)
				{
					return;
				}
				initializationFailed = true;
				if (recommenderReadinessWaiter != null)
				{
					removeBehaviour(recommenderReadinessWaiter);
					recommenderReadinessWaiter = null;
				}
				String detail = message == null
						? "Initialization stopped before all agents became ready."
						: message;
				System.err.println(getLocalName()+" initialization failed: "+detail);
				if (myGui != null)
				{
					myGui.handleInitializationFailure(runLifecycle.getActiveRunId(), detail);
				}
			}

	private void resetForRecommendationRun(String runId)
	{
		System.out.println(getLocalName()+" resetting per-run merge state for "+runId);
		recMergeCount = 0;
		recommendationRunFailed = false;
		recommendationScorePayloads.clear();
		recommendationResults.clear();
		usersRequestedCount = 0;
		userInterestListSent = 0;
		userRequestTimes.clear();
		userMsgPassTimes.clear();
		if (listMLP != null)
		{
			listMLP.clear();
		}
		if (listNN != null)
		{
			listNN.clear();
		}
		if (mlpModelPaths != null)
		{
			mlpModelPaths.clear();
		}
		if (sparseMlpModelPaths != null)
		{
			sparseMlpModelPaths.clear();
		}
		averageWeightCount = 0;
		sparseMlpAverageWeightCount = 0;
	}
	
		private class InitializeBehaviour extends CyclicBehaviour {
		private static final long serialVersionUID = 1L;

		public InitializeBehaviour(Agent a) {
			super(a);
		}

			public void action() {
				ACLMessage msg = this.myAgent.receive();
				if (msg == null)
				{
					block();
					return;
				}
				if ("Prepare Recommendation Run".equals(msg.getOntology()))
				{
					RecommendationRunLifecycle.Admission admission =
							runLifecycle.admit(msg.getConversationId());
					if (admission == RecommendationRunLifecycle.Admission.NEW_RUN)
					{
						resetForRecommendationRun(runLifecycle.getActiveRunId());
					}
					return;
				}
				if ("Initialization Failed".equals(msg.getOntology()))
				{
					failInitialization(msg.getContent());
					return;
				}
				if (!"Ready".equals(msg.getOntology())
						&& !runLifecycle.isActive(msg.getConversationId()))
				{
					System.out.println(getLocalName()+" ignored stale run message "
							+msg.getOntology()+" conversationId="+msg.getConversationId());
					return;
				}
				//Message from user agents
				if ("Ready".equals(msg.getOntology()))
				{
//				++numberofusers;

//				String user_name1[] = msg.getSender().getLocalName().split("-",2);
//				String requestedBy = user_name1[0];
//
//				String ConnectedtoServer1[] = msg.getContent().split("Tweets and connected to ",2);
//				String ConnectedtoServer = ConnectedtoServer1[1];
//
//				if(!user_is_connectedtothis_RecServer.containsKey(requestedBy))
//				{
//					user_is_connectedtothis_RecServer.put(requestedBy, ConnectedtoServer);
//				}
				usercounter++;
				lastInitializationProgressAt = System.currentTimeMillis();

				System.out.println(getLocalName()+" usercounter: "+usercounter+"\tnumberofuserparticipated: "+numberofuserparticipated);

					if(usercounter == numberofuserparticipated)
					{
						attemptInitializationCompletion();
					}


			}

			if ("Recommendation Run Failed".equals(msg.getOntology()))
			{
				recommendationRunFailed = true;
				String failure = msg.getContent() == null
						? "A recommender node failed before publishing scores."
						: msg.getContent();
				System.err.println(getLocalName() + " stopped score merging: " + failure);
				return;
			}

			if ("Merge Recommendation Scores V2".equals(msg.getOntology()))
			{
				if (recommendationRunFailed)
				{
					return;
				}
				recMergeCount++;
				try
				{
					Object content = msg.getContentObject();
					if (content instanceof RecommendationScorePayload)
					{
						recommendationScorePayloads.add((RecommendationScorePayload) content);
					}
				}
				catch (UnreadableException e)
				{
					System.err.println(getLocalName() + " could not read converted recommendation scores.");
					e.printStackTrace();
				}

				if (recMergeCount == allRecAgents.length)
				{
					recommendationResults =
							RecommendationScoreMerger.merge(recommendationScorePayloads);
					LinkedHashMap<String,Iterable<Map.Entry<String,Double>>> ranked =
							new LinkedHashMap<String,Iterable<Map.Entry<String,Double>>>();
					for (Map.Entry<String,RecommendationResultPayload> result
							: recommendationResults.entrySet())
					{
						ranked.put(result.getKey(), result.getValue().getScores().entrySet());
					}
					RecommendationRunResults.record(runLifecycle.getActiveRunId(), ranked);
					sendMergeCompleted();
				}
			}

			//Message from user agent to get scores
			if ("Get Score List".equals(msg.getOntology()))		
			{
				usersRequestedCount++;


				String requestedBy = msg.getSender().getLocalName().split("-",2)[0];
				

				System.out.println(getLocalName()+" Interest List requestedBy:" + requestedBy + " usersRequestedCount: "+usersRequestedCount);
                 
				 
				// This is the code causing the problem 
				 
				if(!userRequestTimes.containsKey(requestedBy))
				{
					userRequestTimes.put(requestedBy, (long) System.nanoTime());
				}

				ACLMessage msg2 = new ACLMessage( ACLMessage.REQUEST );
				msg2.addReceiver(msg.getSender());
				msg2.setConversationId(runLifecycle.getActiveRunId());
				//Every algorithm and mode delivers one structured result.
				msg2.setOntology("Recommendation Result V2");
				RecommendationResultPayload result = recommendationResults.get(requestedBy);
				if (result == null)
				{
					result = new RecommendationResultPayload(
							requestedBy,
							recommendationConfig.getTargetMode(),
							recommendationConfig.getCandidateType(),
							RecommendationResultStatus.FAILED,
							"No merged recommendation result was produced for this target.",
							Collections.<String,Double>emptyMap());
				}
				try
				{
					msg2.setContentObject(result);
					send(msg2);
				}
				catch (IOException e)
				{
					e.printStackTrace();
				}
			}

			//Message from user agent that user received score list
			if ("Scores Received".equals(msg.getOntology()))
			{
				long messagePassTime;
				String requestedBy = msg.getSender().getLocalName().split("-",2)[0];
				messagePassTime = System.nanoTime() - userRequestTimes.get(requestedBy);
				userMsgPassTimes.put(requestedBy, messagePassTime);

				ACLMessage msg2 = new ACLMessage( ACLMessage.REQUEST );
				msg2.addReceiver(msg.getSender());
				msg2.setConversationId(runLifecycle.getActiveRunId());
				msg2.setOntology("Stop Querying");
				msg2.setContent("Querying completed");
				send(msg2);

				ACLMessage queryDoneMsg = new ACLMessage( ACLMessage.INFORM );
				queryDoneMsg.addReceiver(new AID("Starter Agent",AID.ISLOCALNAME));
				queryDoneMsg.setConversationId(runLifecycle.getActiveRunId());
				queryDoneMsg.setContent("Query is done");
				queryDoneMsg.setOntology("Querying Done from Organizing Agent");
				send(queryDoneMsg);
			}
			
			//No MLP averaging round: the exact MLP trains one model on the whole run.

				


		}
	}

		private void sendMergeCompleted()
		{
			if (recommendationRunFailed)
		{
			return;
		}
		ACLMessage mergedMsg = new ACLMessage(ACLMessage.INFORM);
		mergedMsg.addReceiver(new AID("Starter Agent", AID.ISLOCALNAME));
		mergedMsg.setContent("Can query now");
		mergedMsg.setConversationId(runLifecycle.getActiveRunId());
		mergedMsg.setOntology("Merge Lists Completed");
		send(mergedMsg);
	}



	protected void takeDown() {
		try {
			DFService.deregister(this);
			System.out.println(getLocalName()+" DEREGISTERED WITH THE DF");
			//doDelete();
		} catch (FIPAException e) {
			e.printStackTrace();
		}

	}
	
	/* public NeuralConnection[] myNeurons(MultilayerPerceptron mlp) {
							
		NeuralConnection[]  node = mlp.m_neuralNodes;
		return node;
							
	} */


}
