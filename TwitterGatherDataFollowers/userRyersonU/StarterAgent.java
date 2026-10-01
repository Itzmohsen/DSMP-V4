package TwitterGatherDataFollowers.userRyersonU;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Scanner;
import java.util.Set;
import java.util.SortedSet;
import java.util.StringTokenizer;
import java.util.TreeMap;
import java.util.Collections;
import java.util.TreeSet;
import java.util.Map.Entry;
import java.time.LocalDateTime;

import jade.content.lang.Codec;
import jade.content.lang.sl.SLCodec;
import jade.content.onto.Ontology;
import jade.content.onto.basic.Action;
import jade.core.AID;
import jade.core.Agent;
import jade.core.Location;
import jade.core.behaviours.*;
import jade.domain.DFService;
import jade.domain.FIPAException;

import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.JADEAgentManagement.JADEManagementOntology;
import jade.domain.JADEAgentManagement.KillAgent;
import jade.domain.JADEAgentManagement.ShutdownPlatform;
import jade.domain.mobility.MobilityOntology;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.UnreadableException;

import java.awt.Desktop;  // added by Sepide
import java.io.*;  // added by Sepide

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.gephi.statistics.plugin.Modularity;   // added by Sepide
import org.gephi.statistics.plugin.builder.ModularityBuilder;  // added by Sepide 
import org.gephi.io.importer.plugin.file.ImporterBuilderGML;   // added by Sepide
import org.gephi.io.importer.spi.*;  // added by Sepide 
//import org.gephi.io.*;   // added by Sepide
import org.gephi.io.importer.plugin.file.ImporterGML;  // added by Sepide 
import org.gephi.io.importer.api.ImportController;  // added by Sepide 
import org.gephi.io.importer.api.Container;  // added by Sepide 
import org.gephi.io.importer.api.EdgeDirectionDefault;  // added by Sepide 
import org.openide.util.Lookup;      // added by Sepide
import org.gephi.graph.api.GraphModel;  // added by Sepide 
import org.gephi.graph.api.GraphController;   // added by Sepide 
import org.gephi.io.processor.plugin.DefaultProcessor;    // added by Sepide
import org.gephi.graph.api.UndirectedGraph;    // added by Sepide 
import org.gephi.graph.api.DirectedGraph;    // added by Sepide 
import org.gephi.project.api.Workspace;    // added by Sepide
import org.gephi.project.api.ProjectController;   // added by Sepide 
import org.gephi.statistics.plugin.Modularity;  // added by Sepide 
import org.gephi.appearance.api.Partition;      // added by Sepide 
import org.gephi.appearance.api.PartitionFunction;   // added by Sepide 
import org.gephi.appearance.plugin.PartitionElementColorTransformer;   // added by Sepide 
import org.gephi.appearance.plugin.palette.Palette;   // added by Sepide 
import org.gephi.appearance.plugin.palette.PaletteManager;   // added by Sepide 
import org.gephi.graph.api.Column;   // added by Sepide 
import org.gephi.appearance.api.AppearanceController;    // added by Sepide 
import org.gephi.appearance.api.AppearanceModel;        // added by Sepide 
import org.gephi.appearance.api.Function;       // added by Sepide
import org.gephi.io.exporter.api.ExportController;   // added by Sepide
import org.gephi.layout.plugin.forceAtlas.ForceAtlasLayout;      // added by Sepide
import org.gephi.layout.plugin.forceAtlas.ForceAtlas;   // added by Sepide
import java.util.concurrent.TimeUnit;   // added by Sepide
import org.gephi.layout.plugin.AutoLayout;   // added by Sepide
import org.gephi.layout.plugin.force.StepDisplacement;   // added by Sepide 
import org.gephi.layout.plugin.force.yifanHu.YifanHuLayout;  // added by Sepide 
import org.gephi.layout.plugin.forceAtlas2.ForceAtlas2; // added by Sepide 
import org.ghost4j.*;   // added by Sepide 
import org.ghost4j.converter.PDFConverter;    // added by Sepide 
import org.ghost4j.renderer.SimpleRenderer;   // added by Sepide 
import java.awt.Image;       // added by Sepide 
import java.awt.image.RenderedImage;    // added by Sepide 
import javax.imageio.ImageIO;     // added by Sepide 
import org.ghost4j.document.PDFDocument;   // added by Sepide 
import org.ghost4j.analyzer.AnalysisItem;   // added by Sepide 
import org.ghost4j.analyzer.FontAnalyzer;     // added by Sepide   
import java.awt.image.BufferedImage;    // added by Sepide 
import com.spire.pdf.PdfDocument;   // added by Sepide 
import org.gephi.preview.api.*;       // added by Sepide 

public class StarterAgent extends Agent 
{
	private static final long serialVersionUID = 1L;

	private AID[] alltfidfserviceAgents;
	private AID controllerAID;
	private Location destination;
	private int numberofusers=0;
	private int numberofusers_counter=0;
	private int queryUserCounter=0;
	private int numberofusermessagesfromserver=0;
	private int userCounter=0;
	private int nextQueryUserIndex=0;


	private int tfidfservercount=0;



	private String conversationIDReceived;
	private String conversationIDInitial = RecommendationRunLifecycle.createRunId();
	private static final String INPUT_DELIVERY_COMPLETE_ONTOLOGY =
			"Input Delivery Complete";
	private static final long USER_AGENT_DISCOVERY_TIMEOUT_MS = Math.max(
			10000L,
			Long.getLong("dsmp.starter.user.discovery.timeout.ms", 120000L));
	private static final long USER_AGENT_DISCOVERY_POLL_MS = Math.max(
			25L,
			Long.getLong("dsmp.starter.user.discovery.poll.ms", 50L));
	private static final long USER_AGENT_DISCOVERY_LOG_INTERVAL_MS = 1000L;
	private boolean alltweetsflag = false;
	private boolean alltfidfflag = false;

	//@Jason added numOfRecAgentsCount recommender agents
	private int numOfRecAgentsCount=0;
	private int numNodes = 1;

	//@Jason changed datastructure of allUserAgents to a list
	private ArrayList<AID> allUserAgentsList = new ArrayList<AID>();
	private Set<String> expectedUserAgentNames = Collections.emptySet();

	private ArrayList<String> usersRec;
	private RecommendationExperimentConfig recommendationConfig;
	private boolean graphGenerationEnabled;

	transient protected ControllerAgentGui myGui;

	private boolean endSimulation = false; //To determine if user that is looking for recommendation is removed after processing
	private boolean simulationLifecycleFinished;
	private boolean simulationRunFailed;

	private long[] messagePassingTimes; //Message passing times for each node
	private long maxMessagePassingTime; //Maximum message passing of parallel nodes
	
	private int[] messagePassingCosts; //Message passing cost bytes for each node
	private int totalMessagePassingCost; //Total message passing cost of parallel nodes

	private long kmeansMessageTime; //Message passing time for k-means results
	private long beginKmeansMergeTime;
	private long endKmeansMergeTime;
	private long stageSimulationStartTime;
	private long stageStartSimBroadcastEndTime;
	private long stageAllTweetingCompletedTime;
	private long stageAllTextProcessingCompletedTime;
	private long stageAlgorithmStartTime;
	private long stageAllAlgorithmsReportedTime;
	private long stageMergeCompletedTime;
	private long stageQueryStartTime;
	private long stageQueryCompletedTime;
	
	private void logStageDuration(String label, long startTime, long endTime)
	{
		if (startTime <= 0 || endTime <= 0)
		{
			return;
		}
		String line = "[StageTimer] " + label + ": " + (endTime - startTime) + " ms";
		System.out.println(line);
		if (myGui != null)
		{
			myGui.appendResult(line);
		}
	}
	
	private void logStagePoint(String label)
	{
		String line = "[StageTimer] " + label + " at " + LocalDateTime.now();
		System.out.println(line);
		if (myGui != null)
		{
			myGui.appendResult(line);
		}
	}

	private boolean sendNextQueryUserRequest()
	{
		if (usersRec == null || nextQueryUserIndex >= usersRec.size())
		{
			return false;
		}

		String queryUserName = usersRec.get(nextQueryUserIndex);
		String suffixAgentName = "-UserAgent";
		for (int i = 0; i < allUserAgentsList.size(); i++)
		{
			if (allUserAgentsList.get(i).getLocalName().equals(queryUserName+suffixAgentName))
			{
				ACLMessage msg2 = new ACLMessage( ACLMessage.REQUEST);
				msg2.setContent("requestedBy");
				msg2.setOntology("Start Querying");
				msg2.setConversationId(conversationIDInitial);
				msg2.addReceiver(allUserAgentsList.get(i));
				nextQueryUserIndex++;
				System.out.println(getLocalName()+" "+queryUserName+suffixAgentName+" queryMessageIndex: "+nextQueryUserIndex+" of "+usersRec.size());
				send(msg2);
				return true;
			}
		}

		System.out.println("ERROR: QUERY USER " + queryUserName + " NOT FOUND IN USER AGENT LIST");
		myGui.appendResult("ERROR: QUERY USER " + queryUserName + " NOT FOUND IN USER AGENT LIST");
		nextQueryUserIndex++;
		return sendNextQueryUserRequest();
	}

	private ArrayList<String> filterUsersRecToAvailableUserAgents(ArrayList<String> requestedUsers)
	{
		ArrayList<String> availableUsers = new ArrayList<String>();
		if (requestedUsers == null)
		{
			return availableUsers;
		}

		for (String requestedUser : requestedUsers)
		{
			if (isUserAgentAvailable(requestedUser))
			{
				availableUsers.add(requestedUser);
			}
			else
			{
				String line = "Skipping query for " + requestedUser + " because no active user agent exists.";
				System.out.println(line);
				if (myGui != null)
				{
					myGui.appendResult(line);
				}
			}
		}
		return availableUsers;
	}

	private ArrayList<String> getConfiguredRecommendationTargets()
	{
		if (recommendationConfig != null
				&& !recommendationConfig.getTargetUsers().isEmpty())
		{
			return new ArrayList<String>(recommendationConfig.getTargetUsers());
		}
		return myGui.getUsersRec();
	}

	private boolean isUserAgentAvailable(String userName)
	{
		if (userName == null)
		{
			return false;
		}
		String suffixAgentName = "-UserAgent";
		for (int i = 0; i < allUserAgentsList.size(); i++)
		{
			if (allUserAgentsList.get(i).getLocalName().equals(userName + suffixAgentName))
			{
				return true;
			}
		}
		return false;
	}

	private void prepareRecommendationRun()
	{
		NodeExecutionMonitor.setRunPhase(
				"Preparing recommender nodes for a new simulation run.");
		ACLMessage prepareMessage = new ACLMessage(ACLMessage.REQUEST);
		prepareMessage.addReceiver(new AID("Organizing Agent1", AID.ISLOCALNAME));
		for (AID recommenderAgent : alltfidfserviceAgents)
		{
			prepareMessage.addReceiver(recommenderAgent);
		}
		prepareMessage.setConversationId(conversationIDInitial);
		prepareMessage.setContent("Prepare recommendation run");
		prepareMessage.setOntology("Prepare Recommendation Run");
		send(prepareMessage);
	}

	private boolean isCurrentRunMessage(ACLMessage message)
	{
		return message != null
				&& conversationIDInitial.equals(message.getConversationId());
	}

	private ArrayList<AID> waitForExpectedUserAgents(int expectedUsers)
	{
		LinkedHashMap<String,AID> discovered =
				new LinkedHashMap<String,AID>();
		int expectedDiscoveryCount = getExpectedUserAgentDiscoveryCount(
				expectedUsers);
		long waitStartedAt = System.currentTimeMillis();
		long lastProgressLogAt = 0L;
		int lastLoggedCount = -1;

		while (true)
		{
			try
			{
				DFAgentDescription[] results =
						DFService.search(this, userAgentTemplate());
				rememberUserAgents(discovered, results);
				retainExpectedUserAgents(discovered);
			}
			catch (FIPAException e)
			{
				reportSimulationStartFailure(
						"Could not discover user agents before starting the simulation.",
						e);
				return null;
			}

			if (hasExpectedUserAgentCount(discovered, expectedDiscoveryCount))
			{
				if (expectedUserAgentNames.isEmpty()
						&& discovered.size() > expectedDiscoveryCount)
				{
					String line = "Starter Agent discovered "
							+ discovered.size() + " user agent(s) for "
							+ expectedDiscoveryCount + " expected participant(s); "
							+ "using the first expected participant count for legacy callers.";
					System.out.println(line);
					if (myGui != null)
					{
						myGui.appendResult(line);
					}
					return new ArrayList<AID>(
							new ArrayList<AID>(discovered.values()).subList(
									0, expectedDiscoveryCount));
				}
				return new ArrayList<AID>(discovered.values());
			}

			long now = System.currentTimeMillis();
			if (discovered.size() != lastLoggedCount
					|| now - lastProgressLogAt
							>= USER_AGENT_DISCOVERY_LOG_INTERVAL_MS)
			{
				lastLoggedCount = discovered.size();
				lastProgressLogAt = now;
				String line = "Starter Agent waiting for user-agent registration: "
						+ discovered.size() + " of " + expectedDiscoveryCount
						+ " discovered.";
				System.out.println(line);
				if (myGui != null)
				{
					myGui.appendResult(line);
				}
				NodeExecutionMonitor.setRunPhase(
						"Waiting for all user agents to register before starting.");
			}

			if (now - waitStartedAt >= USER_AGENT_DISCOVERY_TIMEOUT_MS)
			{
				reportSimulationStartFailure(
						"Simulation start timed out while waiting for user agents. "
						+ "Discovered " + discovered.size() + " of "
						+ expectedDiscoveryCount + " expected participant(s). "
						+ "Reset the experiment and initialize again.",
						null);
				return null;
			}

			doWait(USER_AGENT_DISCOVERY_POLL_MS);
		}
	}

	private ArrayList<AID> getControllerProvidedUserAgentAids(int expectedUsers)
	{
		if (expectedUserAgentNames == null || expectedUserAgentNames.isEmpty())
		{
			return null;
		}
		ArrayList<AID> userAgents = new ArrayList<AID>();
		for (String userAgentName : expectedUserAgentNames)
		{
			userAgents.add(new AID(userAgentName, AID.ISLOCALNAME));
		}
		String line = "Starter Agent using controller-provided user-agent roster: "
				+ userAgents.size() + " of " + expectedUsers + " participant(s).";
		System.out.println(line);
		if (myGui != null)
		{
			myGui.appendResult(line);
		}
		NodeExecutionMonitor.setRunPhase(
				"Starting all user agents from the controller-provided roster.");
		return userAgents;
	}

	private DFAgentDescription userAgentTemplate()
	{
		DFAgentDescription userAgentDFTemplate =
				new DFAgentDescription();
		ServiceDescription sd = new ServiceDescription();
		sd.setType("User-Agent");
		userAgentDFTemplate.addServices(sd);
		return userAgentDFTemplate;
	}

	static int rememberUserAgents(
			Map<String,AID> discovered,
			DFAgentDescription[] results)
	{
		if (discovered == null || results == null)
		{
			return 0;
		}
		int added = 0;
		for (DFAgentDescription result : results)
		{
			if (result == null || result.getName() == null)
			{
				continue;
			}
			AID aid = result.getName();
			String key = aid.getLocalName();
			if (key == null || key.length() == 0)
			{
				key = aid.getName();
			}
			if (key == null || key.length() == 0)
			{
				continue;
			}
			if (!discovered.containsKey(key))
			{
				added++;
			}
			discovered.put(key, aid);
		}
		return added;
	}

	static boolean hasExpectedUserAgentCount(
			Map<String,AID> discovered, int expectedUsers)
	{
		int expected = Math.max(0, expectedUsers);
		int actual = discovered == null ? 0 : discovered.size();
		return actual >= expected;
	}

	private int getExpectedUserAgentDiscoveryCount(int expectedUsers)
	{
		if (expectedUserAgentNames != null && !expectedUserAgentNames.isEmpty())
		{
			return expectedUserAgentNames.size();
		}
		return Math.max(0, expectedUsers);
	}

	private void retainExpectedUserAgents(Map<String,AID> discovered)
	{
		retainExpectedUserAgents(discovered, expectedUserAgentNames);
	}

	static void retainExpectedUserAgents(
			Map<String,AID> discovered, Set<String> expectedNames)
	{
		if (discovered == null || expectedNames == null
				|| expectedNames.isEmpty())
		{
			return;
		}
		discovered.keySet().retainAll(expectedNames);
	}

	@SuppressWarnings("unchecked")
	private Set<String> resolveExpectedUserAgentNames(Object[] args)
	{
		LinkedHashSet<String> names = new LinkedHashSet<String>();
		if (args != null && args.length > 14
				&& args[14] instanceof Collection<?>)
		{
			for (Object value : (Collection<Object>) args[14])
			{
				if (value == null)
				{
					continue;
				}
				String name = value.toString().trim();
				if (name.length() > 0)
				{
					names.add(name);
				}
			}
		}
		return names.isEmpty() ? Collections.<String>emptySet() : names;
	}

	private boolean validateExpectedUserAgentRoster(int expectedUsers)
	{
		if (expectedUserAgentNames == null || expectedUserAgentNames.isEmpty()
				|| expectedUserAgentNames.size() == expectedUsers)
		{
			return true;
		}
		reportSimulationStartFailure(
				"Simulation start aborted because the controller roster has "
				+ expectedUserAgentNames.size() + " user agent(s), but the run "
				+ "expects " + expectedUsers + " participant(s). Reset and "
				+ "initialize again.",
				null);
		return false;
	}

	private void reportSimulationStartFailure(
			String message, Exception exception)
	{
		String safeMessage = message == null
				? "Simulation could not start." : message;
		System.err.println(safeMessage);
		if (exception != null)
		{
			exception.printStackTrace();
		}
		NodeExecutionMonitor.setRunPhase(safeMessage);
		if (myGui != null)
		{
			myGui.handleSimulationStartFailure(conversationIDInitial, safeMessage);
		}
	}
	
	protected void setup() 
	{
		long starterSetupStartTime = System.currentTimeMillis();


		getContentManager().registerLanguage(new SLCodec());
		getContentManager().registerOntology(JADEManagementOntology.getInstance());

		Object[] args 				= getArguments();
		controllerAID				= (AID) args[0];
		destination 				= here();

		String temptweetvector    	= (String) args[5];
		numberofusers 				= (Integer) args[6];

		recommendationConfig = args.length > 10
				&& args[10] instanceof RecommendationExperimentConfig
				? (RecommendationExperimentConfig) args[10]
				: RecommendationExperimentConfig.legacyDefault(
						RecommendationModeSupport.K_MEANS, ControllerAgent.FROM_TEXT);
		myGui = (ControllerAgentGui) args[11];
		numNodes = (Integer) args[12];
		graphGenerationEnabled = resolveGraphGenerationEnabled(args, recommendationConfig);
		expectedUserAgentNames = resolveExpectedUserAgentNames(args);
		if (args.length > 15 && args[15] instanceof String
				&& ((String)args[15]).trim().length() > 0)
		{
			conversationIDInitial = ((String)args[15]).trim();
		}


		final int numberofuserparticipated = numberofusers;
		if (!validateExpectedUserAgentRoster(numberofuserparticipated))
		{
			doDelete();
			return;
		}
		logStagePoint("STARTER setup started nodes="+numNodes+" expectedUsers="+numberofuserparticipated);
		
		//@Jason checking numberofuserparticipated
		System.out.println("numberofuserparticipated: "+numberofuserparticipated);

		try {
			DFAgentDescription dfd = new DFAgentDescription();
			dfd.setName(getAID());
			ServiceDescription sd = new ServiceDescription();
			sd.setName("Distributed Recommender System");
			sd.setType("Starter Agent");
			dfd.addServices(sd);
			DFService.register(this, dfd);
		} catch (FIPAException e) {
			e.printStackTrace();
		}		

		do 
		{
			DFAgentDescription recAgentDFTemplate = new DFAgentDescription();
			ServiceDescription sd2 = new ServiceDescription();
			sd2.setType("Recommender Agent");
			recAgentDFTemplate.addServices(sd2);
			try {
				DFAgentDescription[] result2 = DFService.search(this, recAgentDFTemplate);
				alltfidfserviceAgents = new AID[result2.length];
				for (int i = 0; i < result2.length; ++i) {
					alltfidfserviceAgents[i] = result2[i].getName();
				}
			}
			catch (FIPAException fe) {
				fe.printStackTrace();
			}
			System.out.println(getLocalName()+" ENTERED CHECKING NUM NODES");
		} while (alltfidfserviceAgents.length < numNodes);

		//Set controllerAgentDFTemplate to look in DF for user agents
		DFAgentDescription controllerAgentDFTemplate = new DFAgentDescription();
		ServiceDescription sd3 = new ServiceDescription();
		sd3.setType("Controller Agent");
		controllerAgentDFTemplate.addServices(sd3);

		//@Jason get controller AID
		try{
			DFAgentDescription[] result = DFService.search(this, controllerAgentDFTemplate);
			controllerAID = result[0].getName();
		}

		catch (FIPAException fe) {
			fe.printStackTrace();
		}
		logStageDuration("STARTER setup DF discovery and agent list collection", starterSetupStartTime, System.currentTimeMillis());



		//@Jason using allUserAgentsList
		ArrayList<AID> discoveredUserAgents =
				getControllerProvidedUserAgentAids(numberofuserparticipated);
		if (discoveredUserAgents == null)
		{
			discoveredUserAgents =
					waitForExpectedUserAgents(numberofuserparticipated);
		}
		if (discoveredUserAgents == null)
		{
			doDelete();
			return;
		}
		allUserAgentsList.clear();
		allUserAgentsList.addAll(discoveredUserAgents);
		logStageDuration(
				"STARTER resolve user-agent startup roster",
				starterSetupStartTime,
				System.currentTimeMillis());

		//@Jason print out all the initial user agents
		/*for (int i = 0; i < allUserAgentsList.size(); i++){
			System.out.println(this.getLocalName()+" allUserAgentsList.get("+i+"): "+allUserAgentsList.get(i));
		}*/

		ACLMessage msg2 = new ACLMessage( ACLMessage.REQUEST );
		//@Jason using allUserAgentsList
		for(int i=0; i<allUserAgentsList.size(); i++)
		{
			msg2.addReceiver(allUserAgentsList.get(i));  
		}	  	

		//@Jason added simulation start message and time
		System.out.println("Simulation has started...");
		final long startSimTime = System.currentTimeMillis();
		stageSimulationStartTime = startSimTime;
		long startSimBroadcastStartTime = System.currentTimeMillis();
		System.out.println("The start time: "+startSimTime);
		System.out.println("The readable start time: "+LocalDateTime.now());

		prepareRecommendationRun();
		msg2.setConversationId(conversationIDInitial);
		msg2.setOntology("Start SIM");
		send(msg2);
		stageStartSimBroadcastEndTime = System.currentTimeMillis();
		logStageDuration("START_SIM broadcast to MobileAgents", startSimBroadcastStartTime, stageStartSimBroadcastEndTime);
		RunRecords.milestone(conversationIDInitial, "run-start");
		if (isMobileAgentTweetDelayBypassEnabled())
		{
			String bypassLine = "[StageTimer] MobileAgent tweet-delay simulation bypass enabled; proceeding after recommender text-processing confirmation.";
			System.out.println(bypassLine);
			myGui.appendResult(bypassLine);
		}
		System.out.println(getLocalName()+": Tweets have started...");

		myGui.disableList();

		setQueueSize(0);

		messagePassingTimes = new long[alltfidfserviceAgents.length];
		messagePassingCosts = new int[alltfidfserviceAgents.length];

		addBehaviour(new TickerBehaviour(this, 10) 
		{
			private static final long serialVersionUID = 1L;
			private int messagePassingTimesReceived;
			private int messagePassingCostReceived;
			private boolean messagePassingCompleted = false;
			
			protected void onTick() 
			{
				ACLMessage msg= myAgent.receive();
				while (msg != null)
				{
				if (!isCurrentRunMessage(msg))
				{
					System.out.println(getLocalName()+" ignored stale run message "
							+msg.getOntology()+" conversationId="+msg.getConversationId());
					msg = myAgent.receive();
					continue;
				}

				if (messagePassingCostReceived == numNodes && messagePassingTimesReceived == numNodes && !messagePassingCompleted)
				{
					maxMessagePassingTime = 0;
					totalMessagePassingCost = 0;
					// for (int i = 0; i < messagePassingTimes.length; i++)
					for (int i = 0; i < messagePassingCosts.length; i++)
					{
						System.out.println("messagePassingTime["+i+"]: "+messagePassingTimes[i]);
						System.out.println("messagePassingCosts["+i+"]: "+messagePassingCosts[i]);
						if (messagePassingTimes[i] > maxMessagePassingTime)
						{
							maxMessagePassingTime = messagePassingTimes[i];
						}
						totalMessagePassingCost += messagePassingCosts[i];
					}

					System.out.println("maxMessagePassingTime: "+ maxMessagePassingTime + " ms");
					// System.out.println("totalMessagePassingTime: "+ maxMessagePassingTime + "bytes");
					System.out.println("totalMessagePassingCost: "+ totalMessagePassingCost + " bytes");
					
					myGui.addMessagePassingTime(messagePassingTimes);
					myGui.addMessagePassingTime(maxMessagePassingTime);
					
					myGui.addMessagePassingCost(messagePassingCosts);
					myGui.addMessagePassingCost(totalMessagePassingCost);

					BufferedWriter writer;
					try {
						writer = new BufferedWriter(new FileWriter("MessagePassingTimes.txt", true));
						writer.write("totalMessagePassingCost: "+ totalMessagePassingCost + " bytes Nodes: "+ messagePassingCosts.length);
						writer.newLine();
						writer.write("maxMessagePassingTime: "+ maxMessagePassingTime + " ms Nodes: "+ messagePassingTimes.length);
						writer.newLine();
						writer.flush();
						writer.close();
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					
					messagePassingCompleted = true;
				}
				
				//Get message passing cost from each node if there is more than 1 node
				if (msg!=null && "Message Passing Cost".equals(msg.getOntology()) && msg.getPerformative() == ACLMessage.INFORM)
				{
					
					messagePassingCostReceived++;
					System.out.println("StarterAgent Message Passing Cost Received: "+ Integer.parseInt(msg.getContent()));
					// if (alltfidfserviceAgents.length > 1)
					// {
						String recAgentName = msg.getSender().getLocalName();
						int recIndex = Integer.parseInt(recAgentName.split("(?=\\d*$)",2)[1])-1;
						// int recIndex = Integer.parseInt(recAgentName.substring(recAgentName.length()-1)) - 1;
						System.out.println(getLocalName()+" recIndex: "+recIndex);
						// messagePassingTimes[recIndex] = Long.valueOf(msg.getContent());
						messagePassingCosts[recIndex] = Integer.parseInt(msg.getContent());
					// }

				}
				
				if (msg!=null && "Message Passing Time".equals(msg.getOntology()) && msg.getPerformative() == ACLMessage.INFORM)
				{
					messagePassingTimesReceived++;
					System.out.println("StarterAgent Message Passing Time Received: "+ Long.valueOf(msg.getContent()));
					// if (alltfidfserviceAgents.length > 1)
					// {
						String recAgentName = msg.getSender().getLocalName();
						int recIndex = Integer.parseInt(recAgentName.split("(?=\\d*$)",2)[1])-1;
						// int recIndex = Integer.parseInt(recAgentName.substring(recAgentName.length()-1)) - 1;
						messagePassingTimes[recIndex] = Long.valueOf(msg.getContent());
						// messagePassingTimes[recIndex] = Integer.parseInt(msg.getContent());
					// }

				}

					if (msg!=null && "Tweeting Completed".equals(msg.getOntology()) && msg.getPerformative() == ACLMessage.INFORM)
					{
					//System.out.println(getLocalName()+" Tweeting Completed from: "+msg.getSender().getLocalName());
					ACLMessage reply = msg.createReply();
					reply.setPerformative( ACLMessage.REQUEST );
					reply.setContent("Stop Tweeting");
					reply.setOntology("Stop Tweeting");
					send(reply);

					numberofusers_counter++;

					//@Jason changed to numberofuserparticipated
					if(numberofusers_counter == numberofuserparticipated)
						//if(numberofusers_counter == numberofusers)
					{
						numberofusers_counter = 0;
						//@Jason changed to -1 to reuse counter in ontology: Querying Done from Organizing Agent below
						alltweetsflag = true;
						stageAllTweetingCompletedTime = System.currentTimeMillis();
						RunRecords.milestone(conversationIDInitial, "input-delivered");
							logStageDuration("WAIT all MobileAgents Tweeting Completed", stageStartSimBroadcastEndTime, stageAllTweetingCompletedTime);
							System.out.println(myAgent.getLocalName()+" TWEETING COMPLETED numberofusers: "+numberofusers);
							ACLMessage inputDeliveryComplete =
									new ACLMessage(ACLMessage.INFORM);
							for (AID recommenderAgent : alltfidfserviceAgents)
							{
								inputDeliveryComplete.addReceiver(recommenderAgent);
							}
							inputDeliveryComplete.setConversationId(
									conversationIDInitial);
							inputDeliveryComplete.setContent(
									"All input producers completed delivery.");
							inputDeliveryComplete.setOntology(
									INPUT_DELIVERY_COMPLETE_ONTOLOGY);
							send(inputDeliveryComplete);
							NodeExecutionMonitor.setRunPhase(
									"All input producers finished; recommender nodes are finalizing text preparation.");

							// System.out.println("STARTER AGENT BEFORE CALCULATE MESSAGE PASSING COST TIME");
						//Get the max message passing time from nodes if more than 1 node
						// if (alltfidfserviceAgents.length > 1)
						// {
							// maxMessagePassingTime = 0;
							// totalMessagePassingCost = 0;
							// for (int i = 0; i < messagePassingTimes.length; i++)
							// for (int i = 0; i < messagePassingCosts.length; i++)
							// {
								// System.out.println("messagePassingTime["+i+"]: "+messagePassingTimes[i]);
								// System.out.println("messagePassingCosts["+i+"]: "+messagePassingCosts[i]);
								// if (messagePassingTimes[i] > maxMessagePassingTime)
								// {
									// maxMessagePassingTime = messagePassingTimes[i];
								// }
								// totalMessagePassingCost += messagePassingCosts[i];
							// }

							// System.out.println("maxMessagePassingTime: "+ maxMessagePassingTime + "ms");
							// System.out.println("totalMessagePassingTime: "+ maxMessagePassingTime + "bytes");
							// System.out.println("totalMessagePassingCost: "+ totalMessagePassingCost + "bytes");
							
							// myGui.addMessagePassingTime(messagePassingTimes);
							// myGui.addMessagePassingTime(maxMessagePassingTime);
							
							// myGui.addMessagePassingCost(messagePassingCosts);
							// myGui.addMessagePassingCost(totalMessagePassingCost);

							// BufferedWriter writer;
							// try {
								// writer = new BufferedWriter(new FileWriter("MessagePassingTimes.txt", true));
								// writer.write("totalMessagePassingCost: "+ totalMessagePassingCost + "bytes Nodes: "+ messagePassingCosts.length);
								// writer.newLine();
								// writer.write("maxMessagePassingTime: "+ maxMessagePassingTime + "ms Nodes: "+ messagePassingTimes.length);
								// writer.newLine();
								// writer.flush();
								// writer.close();
							// } catch (IOException e) {
								// TODO Auto-generated catch block
								// e.printStackTrace();
							// }			


						// }

					}

					}

					if (msg!=null
							&& "Corpus Stream Failed".equals(msg.getOntology())
							&& isCurrentRunMessage(msg))
					{
						String failure = msg.getContent() == null
								? "The bounded corpus stream failed."
								: msg.getContent();
						System.err.println(failure);
						NodeExecutionMonitor.setRunPhase(failure);
						RunRecords.finish(conversationIDInitial, "failed: " + failure);
						myGui.handleSimulationStartFailure(conversationIDInitial, failure);
						finishSimulationLifecycle();
						return;
					}

					if (msg!=null
							&& "Recommendation Run Failed".equals(msg.getOntology())
							&& isCurrentRunMessage(msg))
					{
						if (!simulationRunFailed)
						{
							simulationRunFailed = true;
							String failure = msg.getContent() == null
									? "A recommender node failed before producing valid scores."
									: msg.getContent();
							System.err.println(failure);
							NodeExecutionMonitor.setRunPhase(
									"Recommendation run failed safely. No partial results were published.");
							RunRecords.finish(conversationIDInitial, "failed: " + failure);
							myGui.handleSimulationRunFailure(conversationIDInitial, failure);
							finishSimulationLifecycle();
						}
						return;
					}

					//@Jason added remove user from total number of users msg from recommender agent
				if (msg!=null && "Remove Users From Total".equals(msg.getOntology()) && msg.getPerformative() == ACLMessage.INFORM)
				{
					ArrayList<String> usersToRemove;
					try {
						usersToRemove = (ArrayList<String>)msg.getContentObject();
						numberofusers-=usersToRemove.size();

						for (int i = allUserAgentsList.size()-1; i >= 0; i--)
						{
							for (String nameToRemove : usersToRemove)
							{
								String suffixAgentName = "-UserAgent";
								if (allUserAgentsList.get(i).getLocalName().equals(nameToRemove+suffixAgentName))
								{
									allUserAgentsList.remove(i);
									break;
								}
							}
						}

						System.out.println(getLocalName()+" Received Remove Users From Total from: "+ msg.getSender().getLocalName());
						System.out.println(getLocalName()+" Removed User From Total Amount: "+usersToRemove.size());
						System.out.println(getLocalName()+" numberofusers: "+numberofusers);



						for (AID a : allUserAgentsList)
						{
							System.out.println(a.getLocalName());
						}


					} catch (UnreadableException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}

				}




				/*
				//@Jason added remove user from total number of users msg from recommenderagent
				if (msg!=null && msg.getOntology() == "Remove This User From List of Agents" && msg.getPerformative() == ACLMessage.INFORM)
				{
					int indexToRemove = 0;

					for (int i = 0; i < allUserAgentsList.size(); i++){
						//System.out.println("allUserAgentsList.get(i).getLocalName(): "+allUserAgentsList.get(i).getLocalName()+"\tmsg.getContent(): "+msg.getContent());
						if (allUserAgentsList.get(i).getLocalName().equals(msg.getContent())){
							indexToRemove = i;
							break;
						}
					}

					allUserAgentsList.remove(indexToRemove);




				}
				 */
				//@Jason added wait for all recommender agents to finish text processing before calculating recommendations
				if (msg!=null && "Text Processing Complete".equals(msg.getOntology()) && msg.getPerformative() == ACLMessage.INFORM)
				{
					numOfRecAgentsCount++;
					System.out.println(getLocalName()+" Text Processing Completed: "+numOfRecAgentsCount);

					//All recommender agents ready to cluster since text processing is completed
					if (numOfRecAgentsCount == alltfidfserviceAgents.length){
						stageAllTextProcessingCompletedTime = System.currentTimeMillis();
						RunRecords.milestone(conversationIDInitial, "text-processing-complete");
						long textProcessingWaitStartTime = stageAllTweetingCompletedTime > 0 ? stageAllTweetingCompletedTime : stageStartSimBroadcastEndTime;
						logStageDuration("WAIT all RecommenderAgents Text Processing Complete", textProcessingWaitStartTime, stageAllTextProcessingCompletedTime);
						NodeExecutionMonitor.setRunPhase(
								"All nodes prepared their input; model execution is starting.");

						//System.exit(0);
						
						//Send show followers message before clustering
						ACLMessage showFollowersMsg = new ACLMessage( ACLMessage.REQUEST );
						for(int i=0; i<allUserAgentsList.size(); i++)
						{
							showFollowersMsg.addReceiver(allUserAgentsList.get(i));  
						}	  	

						try {
							showFollowersMsg.setContentObject(allUserAgentsList);
						} catch (IOException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
						showFollowersMsg.setOntology("Show Followers");
						send(showFollowersMsg);

						int recUserInList = 0;
						usersRec = filterUsersRecToAvailableUserAgents(getConfiguredRecommendationTargets());

						for (String recUser : usersRec)
						{
							for (int i = 0; i < allUserAgentsList.size(); i++){

								String suffixAgentName = "-UserAgent";
								if (allUserAgentsList.get(i).getLocalName().equals(recUser+suffixAgentName))
								{
									recUserInList++;
									System.out.println(getLocalName()+" recUser: "+recUser+" recUserInList: "+recUserInList);
								}
							}

						}

						if (recUserInList == usersRec.size())
						{
							ACLMessage startRecMsg = new ACLMessage(ACLMessage.REQUEST);
							startRecMsg.setContent("Start Recommend Algorithms");
							startRecMsg.setOntology("Start Recommend Algorithms");
							startRecMsg.setConversationId(conversationIDInitial);

							stageAlgorithmStartTime = System.currentTimeMillis();
							RunRecords.milestone(conversationIDInitial, "model-start");
							for (int i=0; i < alltfidfserviceAgents.length; i++){
								String recAgentToSend = "Recommender-ServiceAgent"+(i+1);
								System.out.println(getLocalName()+ " Start Recommend Algorithm Msg Sent To: "+recAgentToSend);
								startRecMsg.addReceiver(new AID(recAgentToSend,AID.ISLOCALNAME));
								send(startRecMsg);
							}
							logStageDuration("START Recommend Algorithms broadcast", stageAlgorithmStartTime, System.currentTimeMillis());
						}
						else
						{
							for (String recUser : usersRec)
							{
								System.out.println("ERROR: RECOMMENDED USER " + recUser + " REMOVED FROM LIST OF USERS AFTER TEXT PROCESSING");
								myGui.appendResult("ERROR: RECOMMENDED USER " + recUser + " REMOVED FROM LIST OF USERS AFTER TEXT PROCESSING");
								}
								myGui.appendResult("Re-choose Recommendees");
								finishSimulationLifecycle();
							}
					}					
				}

				//Msg from rec agents
				if (msg!=null && "Tweets TFIDF Algorithm Calculation Done".equals(msg.getOntology()) && msg.getPerformative() == ACLMessage.INFORM)
				{
					tfidfservercount++;
					System.out.println(getLocalName()+" received: Tweets TFIDF Algorithm Calculation Done");
					if(tfidfservercount == 1 && alltfidfserviceAgents.length > 1)
					{

						beginKmeansMergeTime = System.nanoTime();
					}
					if(tfidfservercount == alltfidfserviceAgents.length)
					{
						stageAllAlgorithmsReportedTime = System.currentTimeMillis();
						RunRecords.milestone(conversationIDInitial, "all-reported");
						logStageDuration("WAIT all RecommenderAgents Algorithm/TFIDF Done", stageAlgorithmStartTime, stageAllAlgorithmsReportedTime);
						NodeExecutionMonitor.setRunPhase(
								"All recommender nodes reported; combining score maps.");
						tfidfservercount = 0;
					}
				}
				if (msg!=null && "Merge Lists Completed".equals(msg.getOntology()) && msg.getPerformative() == ACLMessage.INFORM)
				{
					if (simulationRunFailed)
					{
						return;
					}

					System.out.println(getLocalName()+" received Merge Lists Completed");

					endKmeansMergeTime = System.nanoTime();
					stageMergeCompletedTime = System.currentTimeMillis();
					RunRecords.milestone(conversationIDInitial, "merge-complete");
					logStageDuration("WAIT merge completed after algorithm start", stageAlgorithmStartTime, stageMergeCompletedTime);
					logStageDuration("WAIT merge completed after all algorithms reported", stageAllAlgorithmsReportedTime, stageMergeCompletedTime);
					NodeExecutionMonitor.setRunPhase(
							"Node score maps are merged; delivering recommendations to target users.");

					if (alltfidfserviceAgents.length > 1)
					{
						kmeansMessageTime = endKmeansMergeTime - beginKmeansMergeTime;
						myGui.addKmeansMergeTime(kmeansMessageTime);
						myGui.addKmeansMergeTimeNano(kmeansMessageTime);
					}

					myGui.addTiming();
						usersRec = filterUsersRecToAvailableUserAgents(getConfiguredRecommendationTargets());
							if (usersRec.size() == 0)
							{
								String line = "No active users are available for querying.";
								System.out.println(line);
								myGui.appendResult(line);
								finishSimulationLifecycle();
							}
						else
						{
							stageQueryStartTime = System.currentTimeMillis();
							queryUserCounter = 0;
							nextQueryUserIndex = 0;
							sendNextQueryUserRequest();
							logStageDuration("START Querying broadcast", stageQueryStartTime, System.currentTimeMillis());
						}
						}

				//When user got its recommendation list
				if (msg!=null && "Querying Done from Organizing Agent".equals(msg.getOntology()) && msg.getPerformative() == ACLMessage.INFORM)
				{
					queryUserCounter++;
					numberofusers_counter++;
					//@Jason checking numberofusers_counter
					//System.out.println(myAgent.getLocalName()+ " numberofusers_counter: "+numberofusers_counter + "\tnumberofusers: "+numberofusers);
                      
					// Code added by Sepide 
                     /* try {
							 //Runtime.getRuntime().exec("cmd /c start cmd.exe /K \"gcc -o C:\\Users\\s2baniha\\Desktop\\important-stuff\\TXT2GMLv1.0 conver C:\\Users\\s2baniha\\Desktop\\important-stuff\\TXT2GMLv1.0 conversion.c");
                             //Runtime.getRuntime().exec("cmd /c start cmd.exe /K \"C:\\Users\\s2baniha\\Desktop\\important-stuff\\TXT2GMLv1.0\\conver C:\\Users\\s2baniha\\Desktop\\important-stuff\\edges-numbers");
							 Process p1 = Runtime.getRuntime().exec( "gcc -o C:\\Users\\s2baniha\\Desktop\\important-stuff\\TXT2GMLv1.0 conver C:\\Users\\s2baniha\\Desktop\\important-stuff\\TXT2GMLv1.0 conversion.c" );
							 Process p = Runtime.getRuntime().exec( "C:\\Users\\s2baniha\\Desktop\\important-stuff\\TXT2GMLv1.0\\conver C:\\Users\\s2baniha\\Desktop\\important-stuff\\edges-numbers" );  
							 
						 }
						 catch (Exception ex) {
                               ex.printStackTrace();
							   return;
                      
                        }  */

                    // End of code added by Sepide 					
					  
					System.out.println("queryUserCounter: "+queryUserCounter+" usersRec.size(): "+ usersRec.size());
						if (queryUserCounter == usersRec.size()) 				
							//if(numberofusers_counter == numberofusers)
						{
							numberofusers_counter = 0;
						queryUserCounter = 0;

						//@Jason timing whole execution
						final long endSimTime = System.currentTimeMillis();
						RunRecords.finish(conversationIDInitial, "completed");
						stageQueryCompletedTime = endSimTime;
						logStageDuration("WAIT querying/recommendation completed", stageQueryStartTime, stageQueryCompletedTime);
						System.out.println("Simulation Completed");
							System.out.println("Final Total execution time: " + (endSimTime - startSimTime) + "ms" );
								logStageDuration("SIMULATION total before graph visualization", stageSimulationStartTime, endSimTime);
								System.out.println("Simulated ended at "+ LocalDateTime.now());

								if (!isGraphGenerationEnabled())
								{
									logStagePoint("GRAPH visualization/export skipped; accumulated node and edge data retained");
									showSimulationFinishedMessage();
									finishSimulationLifecycle();
									return;
								}

								//myGui.showMessageBox("finished simulation");    // commented out by Sepide
						
						// Code added by Sepide
                          /*try {
							 Runtime.getRuntime().exec("cmd /c start cmd.exe /K \"gcc -o C:\\Users\\s2baniha\\Desktop\\important-stuff\\TXT2GMLv1.0\\conver C:\\Users\\s2baniha\\Desktop\\important-stuff\\TXT2GMLv1.0\\conversion.c");
                             Runtime.getRuntime().exec("cmd /c start cmd.exe /K \"C:\\Users\\s2baniha\\Desktop\\important-stuff\\TXT2GMLv1.0\\conver C:\\Users\\s2baniha\\Desktop\\important-stuff\\edges-numbers");
							 //Process p1 = Runtime.getRuntime().exec( "gcc -o C:\\Users\\s2baniha\\Desktop\\important-stuff\\TXT2GMLv1.0 conver C:\\Users\\s2baniha\\Desktop\\important-stuff\\TXT2GMLv1.0 conversion.c" );
							 //Process p = Runtime.getRuntime().exec( "C:\\Users\\s2baniha\\Desktop\\important-stuff\\TXT2GMLv1.0\\conver C:\\Users\\s2baniha\\Desktop\\important-stuff\\edges-numbers" );  
							 
						 }
						 catch (Exception ex) {
                               ex.printStackTrace();
							   return;
                      
                         } */
						
							ProjectController graphProjectController = null;
							try
							{
							long graphVisualizationStartTime = System.currentTimeMillis();
						logStagePoint("GRAPH visualization started");
						String importantStuffDirName = "important-stuff/";
						File importantStuffDir = new File(importantStuffDirName);
						if (!importantStuffDir.exists())
						{
								importantStuffDir.mkdirs();
						}

						File graphFile;
						long graphBuildStartTime = System.currentTimeMillis();
						try {
							GraphGmlBuilder.Result graphBuild = buildCompletedGraphFile(importantStuffDir);
							graphFile = graphBuild.getOutputFile();
							System.out.println("Built graph file with " + graphBuild.getNodeCount() + " nodes and "
									+ graphBuild.getEdgeCount() + " edges: " + graphFile.getPath());
						}
							catch (Exception ex) {
								reportGraphVisualizationFailure("could not build the completed graph file", ex);
								finishSimulationLifecycle();
								return;
							}
						logStageDuration("GRAPH build validated GML file", graphBuildStartTime, System.currentTimeMillis());

						ProjectController pc;
						AppearanceController appearanceController;
						AppearanceModel appearanceModel;
						ImportController importController;
						GraphModel graphModel;
						Workspace workspace;
						Container container;
						long graphSetupStartTime = System.currentTimeMillis();
						try {
							pc = requireGraphComponent(
									Lookup.getDefault().lookup(ProjectController.class), "ProjectController");
							pc.newProject();
							graphProjectController = pc;
							appearanceController = requireGraphComponent(
									Lookup.getDefault().lookup(AppearanceController.class), "AppearanceController");
							appearanceModel = requireGraphComponent(appearanceController.getModel(), "AppearanceModel");
							importController = requireGraphComponent(
									Lookup.getDefault().lookup(ImportController.class), "ImportController");
							GraphController graphController = requireGraphComponent(
									Lookup.getDefault().lookup(GraphController.class), "GraphController");
							graphModel = requireGraphComponent(graphController.getGraphModel(), "GraphModel");
							workspace = requireGraphComponent(pc.getCurrentWorkspace(), "Workspace");
						}
							catch (Exception ex) {
								reportGraphVisualizationFailure("could not initialize Gephi", ex);
								finishSimulationLifecycle();
								return;
							}
						logStageDuration("GRAPH initialize Gephi controllers/workspace", graphSetupStartTime, System.currentTimeMillis());

						long graphImportStartTime = System.currentTimeMillis();
						try {
							System.out.println("Importing graph file: " + graphFile.getPath());
							container = importController.importFile(graphFile);
							if (container == null) {
								throw new IOException("Gephi returned no import container for " + graphFile.getPath());
							}
							if (container.getLoader() == null) {
								throw new IOException("Gephi returned an import container without a loader for "
										+ graphFile.getPath());
							}
							container.getLoader().setEdgeDefault(EdgeDirectionDefault.UNDIRECTED);
							container.getLoader().setAllowAutoNode(false);
						}
							catch (Exception ex) {
								reportGraphVisualizationFailure("Gephi could not import the completed graph file", ex);
								finishSimulationLifecycle();
								return;
							}
						logStageDuration("GRAPH import GML file", graphImportStartTime, System.currentTimeMillis());
						
						     //Append imported data to GraphAPI
                             long graphProcessStartTime = System.currentTimeMillis();
                             importController.process(container, new DefaultProcessor(), workspace);
							 logStageDuration("GRAPH process imported container", graphProcessStartTime, System.currentTimeMillis());
							 
					         //See if graph is well imported
                             //UndirectedGraph graph = graphModel.getUndirectedGraph();
							 DirectedGraph graph = graphModel.getDirectedGraph();
                             System.out.println("Nodes: " + graph.getNodeCount());
                             System.out.println("Edges: " + graph.getEdgeCount());
							 
							 //Run modularity algorithm - community detection
                             long graphModularityStartTime = System.currentTimeMillis();
                             Modularity modularity = new Modularity();
							 modularity.setResolution(1.0);
                             modularity.execute(graphModel);
							 logStageDuration("GRAPH modularity calculation", graphModularityStartTime, System.currentTimeMillis());
							 
							//Partition with 'modularity_class', just created by Modularity algorithm
                            long graphPartitionStartTime = System.currentTimeMillis();
                            Column modColumn = graphModel.getNodeTable().getColumn(Modularity.MODULARITY_CLASS);
                            Function func2 = appearanceModel.getNodeFunction(graph, modColumn, PartitionElementColorTransformer.class);
                            Partition partition2 = ((PartitionFunction) func2).getPartition();
                            System.out.println(partition2.size() + " partitions found");
                            Palette palette2 = PaletteManager.getInstance().randomPalette(partition2.size());
                            partition2.setColors(palette2.getColors());
                            appearanceController.transform(func2);
							logStageDuration("GRAPH partition/color transform", graphPartitionStartTime, System.currentTimeMillis());
							
							//Preview 
							long graphPreviewStartTime = System.currentTimeMillis();
							PreviewModel model = Lookup.getDefault().lookup(PreviewController.class).getModel();
                            model.getProperties().putValue(PreviewProperty.SHOW_NODE_LABELS, Boolean.TRUE);
							model.getProperties().putValue(PreviewProperty.NODE_LABEL_FONT, model.getProperties().getFontValue(PreviewProperty.NODE_LABEL_FONT).deriveFont(8));
							model.getProperties().putValue(PreviewProperty.NODE_LABEL_PROPORTIONAL_SIZE, Boolean.FALSE);
							model.getProperties().putValue(PreviewProperty.NODE_LABEL_OUTLINE_SIZE, 12);
							logStageDuration("GRAPH configure preview", graphPreviewStartTime, System.currentTimeMillis());
							
							//Export
                            long graphPartitionExportStartTime = System.currentTimeMillis();
                            ExportController ec = Lookup.getDefault().lookup(ExportController.class);
                              try {
	                                ec.exportFile(new File(importantStuffDirName+"partition.pdf"));
	                                } catch (IOException ex) {
	                                   ex.printStackTrace();
	                                   finishSimulationLifecycle();
	                                   return;
	                                }
							  logStageDuration("GRAPH export partition PDF", graphPartitionExportStartTime, System.currentTimeMillis());
								
								//Layout for 1 minute
								long graphLayoutStartTime = System.currentTimeMillis();
								AutoLayout autoLayout = new AutoLayout(1, TimeUnit.MINUTES);
                                autoLayout.setGraphModel(graphModel);
								//YifanHuLayout secondLayout = new YifanHuLayout(null, new StepDisplacement(1f));
								ForceAtlasLayout firstLayout = new ForceAtlasLayout(null);
								//firstLayout.setRepulsionStrength(20000.00);
								//ForceAtlas fa = new ForceAtlas(); 
                                //fa.buildLayout();
								//AutoLayout.DynamicProperty adjustBySizeProperty = AutoLayout.createDynamicProperty("forceAtlas.adjustSizes.name", Boolean.TRUE, 0f);//True after 10% of layout time
                                AutoLayout.DynamicProperty repulsionProperty = AutoLayout.createDynamicProperty("forceAtlas.repulsionStrength.name", 20000., 0f);//500 for the complete period
								//autoLayout.addLayout(firstLayout, 0.0f, new AutoLayout.DynamicProperty[]{adjustBySizeProperty, repulsionProperty});
                                autoLayout.addLayout(firstLayout, 0.0f, new AutoLayout.DynamicProperty[]{repulsionProperty});
								ForceAtlas2 secondLayout = new ForceAtlas2(null);
								secondLayout.setLinLogMode(true);
								secondLayout.setAdjustSizes(true);
								secondLayout.setGravity(10d);
								secondLayout.setScalingRatio(30d);
								secondLayout.setEdgeWeightInfluence(0.2);
								System.out.println("Scaling Ratio of ForceAtlas2: " + secondLayout.getScalingRatio());
								//AutoLayout.DynamicProperty preventOver = AutoLayout.createDynamicProperty("forceAtlas2.preventOverlap.namePreventOverlap", Boolean.FALSE, 0f);
								//autoLayout.addLayout(secondLayout, 1f,new AutoLayout.DynamicProperty[]{preventOver});
								AutoLayout.DynamicProperty adjustBySizeProperty = AutoLayout.createDynamicProperty("ForceAtlas2.adjustSizes.name",Boolean.TRUE,0f);//True for the complete period
								AutoLayout.DynamicProperty distrAttraction = AutoLayout.createDynamicProperty("ForceAtlas2.distributedAttraction.name",Boolean.TRUE,0f);//True for the complete period
                                autoLayout.addLayout(secondLayout, 1.0f, new AutoLayout.DynamicProperty[]{adjustBySizeProperty,distrAttraction});
								//autoLayout.addLayout(secondLayout, 1.0f);
								autoLayout.execute();
								logStageDuration("GRAPH AutoLayout execute", graphLayoutStartTime, System.currentTimeMillis());
								
								//Export full graph
								long graphGexfExportStartTime = System.currentTimeMillis();
								ExportController ec3 = Lookup.getDefault().lookup(ExportController.class);
                                 try {
	                                       ec3.exportFile(new File(importantStuffDirName+"graph.gexf"));
	                                   } catch (IOException ex) {
	                                       ex.printStackTrace();
	                                     finishSimulationLifecycle();
	                                     return;
									}
								logStageDuration("GRAPH export GEXF", graphGexfExportStartTime, System.currentTimeMillis());
								
								
								
								//Export
                                  long graphAutolayoutExportStartTime = System.currentTimeMillis();
                                  ExportController ec2 = Lookup.getDefault().lookup(ExportController.class);
                                  try {
                                   ec2.exportFile(new File(importantStuffDirName+"autolayout.pdf"));
                                    } catch (IOException ex) {
                                     ex.printStackTrace();
                                     }
								  logStageDuration("GRAPH export autolayout PDF", graphAutolayoutExportStartTime, System.currentTimeMillis());
									 
									 long graphRenderImageStartTime = System.currentTimeMillis();
									 try {
										 PDDocument document = PDDocument.load(new File(importantStuffDirName + "autolayout.pdf"));
										 PDFRenderer pdfRenderer = new PDFRenderer(document);

										 try {
											 for (int i = 0; i < document.getNumberOfPages(); i++) {
												 BufferedImage image = pdfRenderer.renderImageWithDPI(i, 300); // 300 DPI, you can adjust this
												 File file = new File(String.format(importantStuffDirName + "layout.jpg", i));
												 ImageIO.write(image, "png", file);
											 }
										 } finally {
											 document.close();
										 }

//										 PdfDocument pdf = new PdfDocument();
//									 pdf.loadFromFile(importantStuffDirName+"autolayout.pdf");
//
//									BufferedImage image;
//
//                                      for (int i = 0; i < pdf.getPages().getCount(); i++) {
//                                           image = pdf.saveAsImage(i);
//                                           File file = new File( String.format(importantStuffDirName+"layout.jpg", i));
//                                         ImageIO.write(image, "png", file);
//                                         }
//                                        pdf.close();
									 }
									 catch (Exception e) {
                                                   System.out.println("ERROR: " + e.getMessage());
                                            }
									 logStageDuration("GRAPH render layout image", graphRenderImageStartTime, System.currentTimeMillis());
																		
									 
									 /* try {
										 PDFDocument document = new PDFDocument();
										 document.load(new File("C:\\Users\\s2baniha\\Desktop\\important-stuff\\autolayout.pdf"));
									     SimpleRenderer renderer = new SimpleRenderer();
                                         
										 // set resolution (in DPI)
                                         renderer.setResolution(300);
										 List<Image> images = renderer.render(document);
										 
										 try {
											for (int i = 0; i < images.size(); i++) {
                                               ImageIO.write((RenderedImage) images.get(i), "PNJ", new File((i + 1) + ".png"));
                                           } 
										 }
										  catch (Exception e) {
                                                   System.out.println("ERROR: " + e.getMessage());
                                            }
								 
									}
									catch (Exception e) {
                                           System.out.println("ERROR: " + e.getMessage());
                                    }  */
									
									 try  
	                                 {  
	                                   long graphOpenImageStartTime = System.currentTimeMillis();
	                                   //constructor of file class having file as argument  
	                                   File file = new File(importantStuffDirName+"layout.jpg");   
	                                   if(!Desktop.isDesktopSupported())//check if Desktop is supported by Platform or not  
		                                     {
		                                             System.out.println("not supported");
		                                             finishSimulationLifecycle();
		                                             return;
		                                     }
	                                  Desktop desktop = Desktop.getDesktop();  
	                                  if(file.exists())         //checks file exists or not  
	                                  desktop.open(file);              //opens the specified file  
									  logStageDuration("GRAPH open layout image", graphOpenImageStartTime, System.currentTimeMillis());
	                                   }  
	                                catch(Exception e)  
	                                 {  
	                                       e.printStackTrace();  
	                                }
						logStageDuration("GRAPH visualization total", graphVisualizationStartTime, System.currentTimeMillis());
						
						// End of code added by Sepide 
						
						// Code added by Sepide for automation of Simulator 
						
								showSimulationFinishedMessage();
								finishSimulationLifecycle();
							}
							catch (Exception ex)
							{
								reportGraphVisualizationFailure("unexpected graph finalization failure", ex);
							}
							finally
							{
								closeGraphProject(graphProjectController);
								finishSimulationLifecycle();
							}
						//if (myGui.indexToRecommend < myGui.listSize - 1){  // commented out on Jan 30, 2021
							//myGui.simulationButton.doClick();
						//}
						//else {
							//myGui.showMessageBox("finished simulation");
							//if (myGui.simulationSelectionBox.getSelectedIndex() <= 5 ) {
								//myGui.showAgentsList.setSelectedIndex(0);
								//myGui.simulationSelectionBox.setSelectedIndex(myGui.simulationSelectionBox.getSelectedIndex()+1);
								//TwitterGatherDataFollowers.userRyersonU.myGui.simulationSelectionListener simList = new TwitterGatherDataFollowers.userRyersonU.myGui.simulationSelectionListener();
								//myGui.simulationSelectionBox.addActionListener(myGui.simulationSelectionListener simList = new myGui.simulationSelectionListener());
								
							//} 
							//else {
								 //myGui.showMessageBox("finished simulation");
							//}
						//}    // commendted out on Jan 30, 2021
						//End of code added by Sepide 
						
						//@Jason added exit to save CPU from running forever doing nothing and causing an early death for the CPU's lifespan
						//System.exit(0);
						}
						else
						{
							sendNextQueryUserRequest();
						}
					}
					
					if (!isMobileAgentTweetDelayBypassEnabled())
					{
						break;
					}
					msg = myAgent.receive();
				}
				
				block();
			}
		});


	}


	private GraphGmlBuilder.Result buildCompletedGraphFile(File importantStuffDir) throws IOException {
		File selectedDataset = getConfiguredDatasetFile();
		if (selectedDataset == null) {
			throw new IOException("No selected dataset is available for graph generation");
		}

		String graphEdgesBaseName = getDatasetGraphBaseName(selectedDataset.getName());
		File edgeListFile = new File(importantStuffDir, graphEdgesBaseName + ".txt");
		File nameNumberFile = new File(importantStuffDir, "name-number-" + selectedDataset.getName());
		File graphFile = new File(importantStuffDir, graphEdgesBaseName + ".gml");
		return GraphGmlBuilder.build(edgeListFile, nameNumberFile, graphFile);
	}

	private <T> T requireGraphComponent(T component, String componentName) {
		if (component == null) {
			throw new IllegalStateException("Gephi component is unavailable: " + componentName);
		}
		return component;
	}

	private void reportGraphVisualizationFailure(String reason, Exception exception) {
		String message = "GRAPH visualization skipped: " + reason + ". "
				+ (exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage());
		System.err.println(message);
		exception.printStackTrace();
		if (myGui != null) {
			javax.swing.SwingUtilities.invokeLater(new Runnable() {
				public void run()
				{
					myGui.appendResult(message);
					myGui.showRunMessage(conversationIDInitial, "finished simulation");
				}
			});
		}
	}


	private boolean isMobileAgentTweetDelayBypassEnabled() {
		return myGui != null && !myGui.isSimulateTweetDelayEnabled();
	}

	private boolean isGraphGenerationEnabled() {
		return graphGenerationEnabled;
	}

	static boolean resolveGraphGenerationEnabled(
			Object[] args,
			RecommendationExperimentConfig config) {
		if (args != null && args.length > 13 && args[13] instanceof Boolean) {
			return ((Boolean) args[13]).booleanValue();
		}
		return config == null || config.isGraphGenerationEnabled();
	}

	private String getConvertedModeFileSuffix() {
		return recommendationConfig == null
				? "" : recommendationConfig.getConvertedModeFileSuffix();
	}


	private String getGraphEdgesBaseName() {
		File selectedDataset = getConfiguredDatasetFile();
		if (selectedDataset != null) {
			return getDatasetGraphBaseName(selectedDataset.getName());
		}
		return "edges-numbers";
	}

	private File getConfiguredDatasetFile() {
		File configuredDataset = recommendationConfig == null
				? null : recommendationConfig.getDatasetFile();
		if (configuredDataset != null) {
			return configuredDataset;
		}
		return myGui == null || myGui.fileChooser == null
				? null : myGui.fileChooser.getSelectedFile();
	}

	private String getDatasetGraphBaseName(String selectedFileName) {
		String datasetName = selectedFileName;
		int extensionIndex = datasetName.lastIndexOf('.');
		if (extensionIndex > 0) {
			datasetName = datasetName.substring(0, extensionIndex);
		}
		datasetName = datasetName.replaceAll("[^A-Za-z0-9._-]", "_");
		if (datasetName.length() == 0) {
			datasetName = "uploaded-dataset";
		}
		return "edges-numbers-" + datasetName + getConvertedModeFileSuffix();
	}

	private void finishSimulationLifecycle()
	{
		if (simulationLifecycleFinished)
		{
			return;
		}
		simulationLifecycleFinished = true;
		NodeExecutionMonitor.setRunPhase("Recommendation run finished.");
		doDelete();
	}

	private void closeGraphProject(ProjectController projectController)
	{
		if (projectController == null || projectController.getCurrentProject() == null)
		{
			return;
		}

		try
		{
			projectController.closeCurrentProject();
			logStagePoint("GRAPH released Gephi project");
		}
		catch (RuntimeException ex)
		{
			System.err.println("Could not release the completed Gephi project: " + ex.getMessage());
		}
	}

	private void showSimulationFinishedMessage()
	{
		if (myGui == null)
		{
			return;
		}

		myGui.showRunMessage(conversationIDInitial, "finished simulation");
	}

	protected void takeDown() {
		try {
			DFService.deregister(this);
			System.out.println(getLocalName()+" DEREGISTERED WITH THE DF");
		} catch (FIPAException e) {
			e.printStackTrace();
		}
		if (simulationLifecycleFinished && myGui != null)
		{
			javax.swing.SwingUtilities.invokeLater(new Runnable() {
				public void run()
				{
					myGui.enableAllButtons();
					myGui.enableList();
				}
			});
		}
	}



}
