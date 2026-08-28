package TwitterGatherDataFollowers.userRyersonU;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.*;
import java.lang.*;   //added by Sepide
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.math3.random.RandomDataGenerator;

import java.io.*;
import java.nio.charset.StandardCharsets;

import javax.swing.SwingUtilities;

import jade.lang.acl.*;
import jade.wrapper.AgentContainer;
import jade.wrapper.AgentController;
import jade.wrapper.StaleProxyException;
import jade.content.*;
import jade.content.onto.basic.*;
import jade.content.lang.*;
import jade.content.lang.sl.*;
import jade.core.*;
import jade.core.Runtime;
import jade.core.behaviours.*;
import jade.domain.*;
import jade.domain.mobility.*;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.JADEAgentManagement.*;
import jade.gui.*;
import jade.domain.FIPAAgentManagement.*;
import jade.util.leap.Properties;

import weka.classifiers.Classifier;
import weka.classifiers.Evaluation;
import weka.classifiers.evaluation.NominalPrediction;
import weka.classifiers.functions.SMO;
import weka.classifiers.meta.FilteredClassifier;
import weka.core.FastVector;
import weka.core.Instances;
import weka.filters.unsupervised.attribute.Remove;

public class ControllerAgent extends GuiAgent {
	//public class ControllerAgent extends Agent {

	private static final long serialVersionUID = 1L;
	private AgentContainer workContainer;
	private AgentContainer userSimContainer;

	private AID[] allRecAgents;
	private long tweetDelay;
	private String currentRecommendationRunId = RecommendationRunLifecycle.createRunId();

	String[] stopWordsArray = {"a","able","about","above","abst","accordance","according","accordingly","across","act",
			"actually","added","adj","affected","affecting","affects","after","afterwards","again","against",
			"ah","all","almost","alone","along","already","also","although","always","am",
			"among","amongst","an","and","announce","another","any","anybody","anyhow","anymore",
			"anyone","anything","anyway","anyways","anywhere","apparently","approximately","are","aren","arent",
			"arise","around","as","aside","ask","asking","at","auth","available","away",
			"awfully","b","back","be","became","because","become","becomes","becoming","been",
			"before","beforehand","begin","beginning","beginnings","begins","behind","being","believe","below",
			"beside","besides","between","beyond","biol","both","brief","briefly","but","by",
			"c","ca","came","can","cannot","cant","cause","causes","certain","certainly",
			"co","com","come","comes","contain","containing","contains","could","couldnt","d",
			"da","date","did","didnt","different","do","does","doesnt","doing","done",
			"dont","down","downwards","due","during","e","each","ed","edu","effect",
			"eg","eight","eighty","either","else","elsewhere","end","ending","enough","especially",
			"et","et-al","etc","even","ever","every","everybody","everyone","everything","everywhere",
			"ex","except","f","far","few","ff","fifth","first","five","fix",
			"followed","following","follows","for","former","formerly","forth","found","four","from",
			"further","furthermore","g","gave","get","gets","getting","give","given","gives",
			"giving","go","goes","gone","got","gotten","h","had","happens","hardly",
			"has","hasnt","have","havent","having","he","hed","hence","her","here",
			"hereafter","hereby","herein","heres","hereupon","hers","herself","hes","hi","hid",
			"him","himself","his","hither","home","how","howbeit","however","hundred","i",
			"id","idk","ie","if","ill","im","immediate","immediately","importance","important","in",
			"inc","indeed","index","information","instead","into","invention","inward","is","isnt",
			"it","itd","itll","its","itself","ive","j","just","k","keep",
			"keeps","kept","kg","km","know","known","knows","l","largely","last",
			"lately","later","latter","latterly","least","less","lest","let","lets","like",
			"liked","likely","line","little","ll","look","looking","looks","ltd","m",
			"made","mainly","make","makes","many","may","maybe","me","mean","means",
			"meantime","meanwhile","merely","mg","might","million","miss","ml","more","moreover",
			"most","mostly","mr","mrs","much","mug","must","my","myself","n",
			"na","name","namely","nay","nd","near","nearly","necessarily","necessary","need",
			"needs","neither","never","nevertheless","next","new","nine","ninety","no","nobody",
			"non","none","nonetheless","noone","nor","normally","nos","not","noted","nothing",
			"now","nowhere","o","obtain","obtained","obviously","of","off","often","oh",
			"ok","okay","old","omitted","on","once","one","ones","only","onto",
			"or","ord","other","others","otherwise","ought","our","ours","ourselves","out",
			"outside","over","overall","owing","own","p","page","pages","part","particular",
			"particularly","past","per","perhaps","placed","please","plus","poorly","possible","possibly",
			"potentially","pp","predominantly","present","previously","primarily","probably","promptly","proud","provides",
			"put","q","que","quickly","quite","qv","r","ran","rather","rd",
			"re","readily","really","recent","recently","ref","refs","regarding","regardless","regards",
			"related","relatively","research","respectively","resulted","resulting","results","right","rt","run","s",
			"said","same","saw","say","saying","says","sec","section","see","seeing",
			"seem","seemed","seeming","seems","seen","self","selves","sent","seven","several",
			"shall","she","shed","shell","shes","should","shouldnt","show","showed","shown",
			"showns","shows","significant","significantly","similar","similarly","since","six","slightly","so",
			"some","somebody","somehow","someone","somethan","something","sometime","sometimes","somewhat","somewhere",
			"soon","sorry","specifically","specified","specify","specifying","still","stop","strongly","sub",
			"substantially","successfully","such","sufficiently","suggest","sup","sure","t","take","taken",
			"taking","tell","tends","th","than","thank","thanks","thanx","that","thatll",
			"thats","thatve","the","their","theirs","them","themselves","then","thence","there",
			"thereafter","thereby","thered","therefore","therein","therell","thereof","therere","theres","thereto",
			"thereupon","thereve","these","they","theyd","theyll","theyre","theyve","think","this",
			"those","thou","though","thoughh","thousand","throug","through","throughout","thru","thus",
			"til","tip","to","together","too","took","toward","towards","tried","tries",
			"truly","try","trying","ts","twice","two","ty","u","ull","ull",
			"un","under","unfortunately","unless","unlike","unlikely","until","unto","up","upon",
			"ups","ur","us","use","used","useful","usefully","usefulness","uses","using",
			"usually","v","value","various","ve","very","via","viz","vol","vols",
			"vs","w","want","wants","was","wasnt","way","we","wed","welcome",
			"well","went","were","werent","weve","what","whatever","whatll","whats","when",
			"whence","whenever","where","whereafter","whereas","whereby","wherein","wheres","whereupon","wherever",
			"whether","which","while","whim","whither","who","whod","whoever","whole","wholl",
			"whom","whomever","whos","whose","why","widely","willing","wish","with","within",
			"without","wont","words","world","would","wouldnt","www","x","y","yes",
			"yet","yo","you","youd","youll","your","youre","yours","yourself","yourselves","youve",
			"z","zero"};
	
	
	private int totalUsers = 0;
	private ArrayList<String> usersRec = new ArrayList<String>(); //Users looking for recommendation
	private ArrayList<String> listOfUsers = new ArrayList<String>(); //List of users from dataset
	private ArrayList<String> listOfAgents = new ArrayList<String>(); //List of all agents created
	private ArrayList<AgentController> listOfAgentControllers = new ArrayList<AgentController>(); //List of all agents AID created
	private ArrayList<Date> allUniqueDateTimes = new ArrayList<Date>(); 
	
	private static String serverName = DatabaseSettings.host();
	private static String portNumber = DatabaseSettings.port();
	private static String sid = DatabaseSettings.database();

	private Connection con;
	private Statement stmt = null;
	private ResultSet resultSet = null;
	private static String user = DatabaseSettings.user();
	private static String pass = DatabaseSettings.password();

	public static final int QUIT = 0;
	public static final int START_SIM = 1;
	public static final int INITIALIZE = 2;
	public static final int GET_USERS = 3;
	public static final int COLLECT_DATA =  4;
	public static final int START_USER_GEN_SIM =  5;
	public static final int RESET_EXPERIMENT = 6;
	public static final int FROM_ARTIFICIAL = 3;
	public static final int FROM_GENERATION = 2;
	public static final int FROM_DB = 1;
	public static final int FROM_TEXT = 0;
	public static final int COS_SIM = 0;
	public static final int K_MEANS = 1;
	public static final int SVM = 2;
	public static final int MLP = 3;
	public static final int Doc2Vec = 4;
	public static final int CommonNeighbors = 5;        // added by Sepide
	public static final int K_MEANSEUCLIDEAN = 6;      // added by Sepide 
	public static final int SCALABLE_SVM = 7;
	public static final int TEMPORAL_GRAPH_FOUNDATION = 8;
	private static final int HASH_TAGS = 1;
	private static final int RE_TWEETS = 1;
	private static final int STOP_WORDS = 1;
	public static final int MAX_WORDS = 7; //Maximum number of words in tweet after processing
	public static final int MIN_WORDS = 3; //Minimum number of words in tweet after processing
	private static final int TOTAL_ORIGINAL_WORDS = 1148; //Total number of words from original corpus
	private static final int TOTAL_ORIGINAL_USERS = 10; //Total number of users from original corpus
	private static final long TWEET_DELAY = 10L; //default 10ms
	private static final String REFERENCE_USER = "RyersonU";
	private static final double DEFAULT_SHAPE = 0.435008; //Average of shape parameters from users for tweet counts from RyersonU
	private static final double DEFAULT_SCALE = 304.842738; //Average of scale parameters from users for tweet counts from RyersonU
	private static final String BEGIN_DATE = "2007-01-01";
	private static final String END_DATE = "2017-01-01";
	private static final double DEFAULT_SHAPE2 = 0.48694;
	private static final double DEFAULT_SCALE2 = 2.09345E-6;
	private static final int TOTAL_ORIGINAL_WORDS_DEMO = 30506;
	private static final double MIN_TFIDF = 0;
	private static final double TEST_SET_PERCENT = 0.30;
	private static final double TRAIN_SET_PERCENT = 0.70;
	private static final int MAX_DIRECT_TEXT_USER_AGENTS = Math.max(
			1,
			Integer.getInteger("dsmp.max.direct.text.user.agents", 480)
					.intValue());
	private static final Pattern RETWEET_PATTERN = Pattern.compile("RT @");
	private static final Pattern PUNCTUATION_PATTERN = Pattern.compile("[\\p{P}]");
	private static final Pattern LINK_PATTERN = Pattern.compile(
			"http[a-zA-Z0-9]*|bitly[a-zA-Z0-9]*|www[a-zA-Z0-9]*");
	private static final Pattern SPECIAL_CHARACTER_PATTERN =
			Pattern.compile("[^a-zA-Z\\p{Z}]");
	private static final Pattern NON_ALPHA_PATTERN = Pattern.compile("[^a-zA-Z ]");
	private static final Pattern MULTI_SPACE_PATTERN = Pattern.compile(" +");
	
	private AID agentNameAID;
	private String agentName;

	private int algorithmRec;
	private RecommendationExperimentConfig recommendationConfig =
			RecommendationExperimentConfig.legacyDefault(K_MEANS, FROM_TEXT);
	private int readFrom;
	private int totalTweetLimit;
	private int numFollowers;
	private int numFollowees;
	private int numTweetsGenerated;
	private int numRecAgents; //numRecAgents #RecommenderAgents numNodes

	private String referenceUser;
	private String beginDate;
	private String endDate;	

	private Runtime runTime;

	transient protected ControllerAgentGui myGui;

	AgentController agentController = null;

	private boolean initialized;
	private boolean firstRun;
	private boolean resetPendingDatasetSelection;

	private InMemoryDb localDb;
	private InMemoryDb availableDb;

	private LinkedHashMap<String,LinkedHashMap<String,Integer>> allUserFollowTweetCounts = new LinkedHashMap<String,LinkedHashMap<String,Integer>>(); //LHM of users and how many tweets before each follower follows
	
	public File textFile; //text file for performance measurement  // private access changed to public by Sepide
	private File corpusGenFile; //text file for user gen simulation
	
	private double[][] topicsParameters = new double[TOTAL_ORIGINAL_USERS][2];
	private double[][] topicsWordTfidf = new double[TOTAL_ORIGINAL_USERS][TOTAL_ORIGINAL_WORDS];
	private String[][] topicsWords = new String[TOTAL_ORIGINAL_USERS][TOTAL_ORIGINAL_WORDS];
	private double[][] topicsMinMax = new double[TOTAL_ORIGINAL_USERS][2];
	private	double[][] topicsWordsIndex = new double[TOTAL_ORIGINAL_USERS][TOTAL_ORIGINAL_WORDS];
	private ArrayList<String> currentBagWords = new ArrayList<String>(); //words already used in random generation
	private static RandomDataGenerator randomDataGenerator = new RandomDataGenerator(); //should not construct in method, make it static
	private static Random r = new Random(); //should not construct in method, make it static
	
	private double[] topicsWordsTfidfDemo = new double[TOTAL_ORIGINAL_WORDS_DEMO];
	private String[] topicsWordsDemo = new String[TOTAL_ORIGINAL_WORDS_DEMO];
	private double[] topicsWordsIndexDemo = new double[TOTAL_ORIGINAL_WORDS_DEMO];
	
	private String generatingParameterFolderName = "GeneratingParameterFiles/";
	
	private double averageUserShape;
	private double averageUserScale;
	private String pathToUserParameters = generatingParameterFolderName + "user_parameters_CombinedMatrix_avg_SMALL.txt"; //gen3
	private String[] pathsToTfidfMatrix = {"RyersonU_tfidf_matrix_SMALL.txt","TheCatTweeting_tfidf_matrix_SMALL.txt","TorontoStar_tfidf_matrix_SMALL.txt"};
	private String pathToTfidfMatrix = "RyersonU_tfidf_matrix_SMALL.txt"; //default, will change based on pathsToTfidfMatrix for loop in initialization
	
	String[] corpusfileNames = {"RyersonU_SMALL_cleaned.txt","TheCatTweeting_SMALL_cleaned.txt","TorontoStar_SMALL_cleaned.txt"};
	
	private LinkedHashMap<String,String> followerFollowee = new LinkedHashMap<String,String>();
	private LinkedHashMap<String,ArrayList<String>> allWordsInSet = new LinkedHashMap<String,ArrayList<String>>();
	
	private LinkedHashMap<String,Double> usersShape = new LinkedHashMap<String,Double>(); //shape parameter for user
	private LinkedHashMap<String,Double> usersScale = new LinkedHashMap<String,Double>(); //scale parameter for user
	private LinkedHashMap<String,Double> userTfidfVector; //each user tfidf vector
	private LinkedHashMap<String,LinkedHashMap<String,Double>> allUserTfidfVectors = new LinkedHashMap<String,LinkedHashMap<String,Double>>(); //all the users tfidf vectors
	private LinkedHashMap<String,Integer> userTweetCounts = new LinkedHashMap<String,Integer>(); //each user's tweet counts
	
	private LinkedHashMap<String,LinkedHashMap<Double,ArrayList<String>>> allUserTfidfWordsBins = new LinkedHashMap<String,LinkedHashMap<Double,ArrayList<String>>>(); //All bins for each user
	
	private HashMap<String,String> userFollowee;
	
	private int tweetsReadCount = 0;

	private int numArtificialTweets = 0;
	private Long userGenerationSeed;
	
	private Map<String,String> userFolloweeMap = new LinkedHashMap<String,String>(); //list of users and their followeeNames before processing (may have extra since processing can remove users) From reading text file 
	private final LinkedHashSet<String> datasetReferenceUsers = new LinkedHashSet<String>();
	private final LinkedHashSet<String> datasetGraphNodeNames = new LinkedHashSet<String>();
	private RecommendationDatasetContext recommendationDatasetContext =
			RecommendationDatasetContext.empty();
	private Map<String,Integer> followeeFollowerCounts = new LinkedHashMap<String,Integer>(); //number of followers for each followee/class
	private Map<String,List<String>> followeeFollowers = new LinkedHashMap<String,List<String>>(); //list of followees and their followers
	private List<String> testSetUsers; //list of users in test set
	private List<String> trainSetUsers; //list of users in training set
	
	private TreeSet<String> allUniqueDocTerms = new TreeSet<String>();
	private LinkedHashMap<Long,String> tweetIdUser = new LinkedHashMap<Long,String>();
	private LinkedHashMap<Long,String> tweetIdText = new LinkedHashMap<Long,String>();
	private LinkedHashMap<Long,LinkedHashMap<String,Double>> tweetIdDocumentVector = new LinkedHashMap<Long,LinkedHashMap<String,Double>>();
	private boolean controllerDocumentsPreparedDuringRead;
	
	private LinkedHashMap<String,Integer> allTermsDocumentFreq = new LinkedHashMap<String,Integer>();
	private LinkedHashMap<String,LinkedHashMap<String,Double>> allUserDocuments = new LinkedHashMap<String,LinkedHashMap<String,Double>>();
	private LinkedHashMap<String,Double> aggregatedUserTweets = new LinkedHashMap<String,Double>();
	private int totalDocuments = 0;
	private Map<String,LinkedHashMap<String,Double>> allUserDocumentsTFIDF;
	private List<String> datasetFollowees; //followees in the whole dataset for MLP distributed
	private final Map<String,TextUserLoadEstimate> textUserLoadEstimates =
			new HashMap<String,TextUserLoadEstimate>();
	
	protected void setup() {

		//Get access to current jade runtime system
		runTime = jade.core.Runtime.instance();

		getContentManager().registerLanguage(new SLCodec(),FIPANames.ContentLanguage.FIPA_SL0);
		getContentManager().registerOntology(MobilityOntology.getInstance());

		setUpWorkContainer();
		setUpUserSimContainer();

		try {
			SwingUtilities.invokeAndWait(new Runnable() {
				public void run() {
					myGui = new ControllerAgentGui(ControllerAgent.this);
					myGui.revealAfterLaunch();
				}
			});
		} catch (Exception guiError) {
			System.err.println("ControllerAgent could not create the GUI: " + guiError.getMessage());
			guiError.printStackTrace();
			doDelete();
			return;
		}

		agentName = getLocalName();
		agentNameAID = getAID();

		initialized = false;
		firstRun = true;
		resetPendingDatasetSelection = false;
		readFrom = FROM_TEXT;
		recommendationConfig = RecommendationExperimentConfig.legacyDefault(K_MEANS, FROM_TEXT);
		textFile = null;
		corpusGenFile = null;
		//readFrom = FROM_DB;
		localDb = new InMemoryDb();
		availableDb = new InMemoryDb();
		referenceUser = REFERENCE_USER;
		numRecAgents = 1;
		
		try {
//			readInGeneratingParameters();
			readInGeneratingParameters2();
			readInGeneratingParameters4();
			for (int i = 0; i < pathsToTfidfMatrix.length; i++)
			{
				pathToTfidfMatrix = generatingParameterFolderName+pathsToTfidfMatrix[i];
				readInTfidfMatrix();
			}
			
			for (String follower: followerFollowee.keySet())
			{
				System.out.println("follower: "+follower+"\tfollowee: "+followerFollowee.get(follower));
			}
			
		} catch (FileNotFoundException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		
//		setGeneratedWordBins();
		setGeneratedWordBins2();
		
		System.out.println("ControllerAGENT MADE agentName:" + agentName + " agentNameAID: "+agentNameAID);


		try {
			DFAgentDescription dfd = new DFAgentDescription();
			dfd.setName(getAID());
			ServiceDescription sd = new ServiceDescription();
			sd.setName("Distributed Recommender System");
			sd.setType("Controller Agent");
			dfd.addServices(sd);
			DFService.register(this, dfd);
		} catch (FIPAException e) {
			e.printStackTrace();
		}

		setQueueSize(0);
		

		
		
	}


	protected void onGuiEvent(GuiEvent ev) {
		int actionToPerform;
		actionToPerform = ev.getType();
		System.out.println("actionToPerform: "+actionToPerform);
		if (actionToPerform == QUIT)
		{
			System.exit(0);
		}
		else if (actionToPerform == GET_USERS)
		{
			loadUsers(ev);
		}
		else if (actionToPerform == INITIALIZE)
		{
			if (initialized)
			{
				killContainer();
				setUpWorkContainer();
			}
			initializeSetup(ev);
		}
		else if (actionToPerform == START_SIM)
		{
			startSimulation(ev);
		}
		else if (actionToPerform == COLLECT_DATA)
		{
			collectData(ev);
		}
		else if (actionToPerform == START_USER_GEN_SIM)
		{
			startUserGenSimulation();
		}
		else if (actionToPerform == RESET_EXPERIMENT)
		{
			resetExperiment();
		}
	}

	private void setUpWorkContainer()
	{
		System.out.println("Set up work container");
		try {
			// Properties containerProperties = new Properties();
			// containerProperties.put("jade_core_messaging_MessageManager_poolsize", "20");
			// containerProperties.put("jade_core_messaging_MessageManager_maxqueuesize", "2000000000");
			// containerProperties.put("jade_core_messaging_MessageManager_deliverytimethreshold", "10000");
			// Profile mainProfile = new ProfileImpl(containerProperties);
		
			Profile mainProfile = new ProfileImpl();
			mainProfile.setParameter(Profile.CONTAINER_NAME, "workContainer");
			mainProfile.setParameter("jade_core_messaging_MessageManager_poolsize", "20");
			mainProfile.setParameter(
					"jade_core_messaging_MessageManager_maxqueuesize",
					Long.toString(adaptiveMessageQueueBytes()));
			mainProfile.setParameter("jade_core_messaging_MessageManager_deliverytimethreshold", "10000");
			workContainer = runTime.createAgentContainer(mainProfile);

		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}

	private long adaptiveMessageQueueBytes()
	{
		long maxHeap = java.lang.Runtime.getRuntime().maxMemory();
		long minimum = 32L * 1024L * 1024L;
		long maximum = 256L * 1024L * 1024L;
		return Math.max(minimum, Math.min(maximum, maxHeap / 4L));
	}

	private void setUpUserSimContainer()
	{
		System.out.println("Set up user sim container");
		try {
			Profile userSimProfile = new ProfileImpl();
			userSimProfile.setParameter(Profile.CONTAINER_NAME, "userSimContainer");
			userSimContainer = runTime.createAgentContainer(userSimProfile);

		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}

	public void collectData(GuiEvent ev)
	{
		String followeesToGet = (String) ev.getParameter(0);
		int followersToGet = (Integer) ev.getParameter(1);
		//S int followerNameToGet = (String) ev.getParameter(1);  // added by Sepide
		
		String[] followeesToGetArray = followeesToGet.split("\\s*,\\s*");
		HashMap<String,Integer> followeeNumFollowersToGet = new LinkedHashMap<String,Integer>();
		
		for (int i = 0; i < followeesToGetArray.length; i++)
		{
			followeeNumFollowersToGet.put(followeesToGetArray[i], followersToGet);
		}
		
		System.out.println("Collect Data Time");
		System.out.println("followeesToGet: "+followeesToGet);
		System.out.println("followersToGet: "+followersToGet);
		for (String followeeName : followeeNumFollowersToGet.keySet())
		{
				System.out.println("LinkedHashMap followeeName: "+ followeeName + " followerNums: "+ followeeNumFollowersToGet.get(followeeName));
		}
		
		Object[] dataCrawlerAgentArgs = new Object[2];
		dataCrawlerAgentArgs[0] = followeeNumFollowersToGet;
		dataCrawlerAgentArgs[1] = myGui;								

		String dataCrawlerAgentName = "Data Crawler Agent";
		
		try{
			agentController = userSimContainer.createNewAgent(dataCrawlerAgentName, DataCrawlerAgent.class.getName(), dataCrawlerAgentArgs);
			agentController.start();
		}
		catch (StaleProxyException e)
		{
			e.printStackTrace();
		}
		
		
		System.out.println("Made agent: "+dataCrawlerAgentName);
		
		myGui.disableUserGenSimButtons();
				
	}
	
	public void startUserGenSimulation()
	{
		if (corpusGenFile != null)
		{
			Object[] userGenSimAgentArgs = new Object[4];
			userGenSimAgentArgs[0] = numArtificialTweets;
			userGenSimAgentArgs[1] = corpusGenFile;
			userGenSimAgentArgs[2] = myGui;
			userGenSimAgentArgs[3] = Long.valueOf(
					userGenerationSeed == null
							? System.nanoTime()
							: userGenerationSeed.longValue());
			
			String userGenSimAgentName =
					"User Generation Simulation Agent-" + System.currentTimeMillis();

			try{
				agentController = userSimContainer.createNewAgent(userGenSimAgentName, UserGenSimAgent.class.getName(), userGenSimAgentArgs);
				agentController.start();
				myGui.disableUserGenSimButtons();
			}
			catch (StaleProxyException e)
			{
				e.printStackTrace();
				myGui.failUserGeneration(
						"Could not start the generation agent: " + e.getMessage());
				return;
			}


			System.out.println("Made agent: "+userGenSimAgentName);
		}
		else
		{
			myGui.noFileSelected();
		}
		
	}
	
	private void logStageDuration(String label, long startTime, long endTime)
	{
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
	
	public void loadUsers(GuiEvent ev)
	{
		boolean readyToInitialize = true;
		
		localDb.clearDb();
		availableDb.clearDb();

//		referenceUser = (String) ev.getParameter(0);
		totalTweetLimit = (Integer) ev.getParameter(1); //DO NOT SET < 300	
		beginDate = (String) ev.getParameter(2);
		endDate = (String) ev.getParameter(3);
		numFollowees = (Integer) ev.getParameter(4);
		numFollowers = (Integer) ev.getParameter(5);
		numTweetsGenerated = (Integer) ev.getParameter(6);

		myGui.disableList();

		long startTime = System.currentTimeMillis();
		logStagePoint("GET_USERS started");

		if (readFrom == FROM_ARTIFICIAL)
		{
			tweetsReadCount = 0;
			for (String corpusName : corpusfileNames)
			{
				textFile = new File(generatingParameterFolderName+corpusName);
				readFromTextFile();
			}
			ArrayList<Tweet> availableTweets;
			availableTweets = getAvailableTweets();
						
			availableDb.setTweets(availableTweets);
			
			// for (Tweet currTweet : availableDb.getTweetsFromUser("TetraRyerson"))
			// {
				// System.out.println("ARTIFICIAL TetraRyerson tweet: "+currTweet.getTweetText());
			// }
			// System.out.println("ARTIFICIAL TetraRyerson: "+availableDb.getTweetCountFromUser("TetraRyerson"));
			// System.out.println("ARTIFICIAL tweetsReadCount: "+tweetsReadCount);
			System.out.println("ARTIFICIAL TOTAL TWEETS READ: "+availableDb.getTotalTweets());
		}
		
		if (readFrom == FROM_GENERATION)
		{
			generateUserNames();
			
			int tweetCount = 0;
			int totalGeneratedUsers = numFollowees + numFollowers;
			int tweetsForEachUser = numTweetsGenerated / totalGeneratedUsers;
			int generatedUserIndex = 0;
			
			int[] tweetCountsForUsers = new int[totalGeneratedUsers]; //followees first then followers
			
			//Assign number of tweets to each generated user's bin
			while (tweetCount < numTweetsGenerated)
			{
				tweetCount+=tweetsForEachUser;
				
				if (generatedUserIndex == totalGeneratedUsers-1)
				{
					if (tweetCount > numTweetsGenerated)
					{
//						System.out.println("tweetsForEachUser: "+tweetsForEachUser);
						tweetsForEachUser-=(tweetCount-numTweetsGenerated); //Lower number of tweets to maintain the number of tweets that should be generated
//						System.out.println("tweetsForEachUser: "+tweetsForEachUser);
					}
					else if (tweetCount < numTweetsGenerated)
					{
//						System.out.println("tweetsForEachUser: "+tweetsForEachUser);
						tweetsForEachUser+=(numTweetsGenerated-tweetCount); //Increase number of tweets to maintain the number of tweets that should be generated
//						System.out.println("tweetsForEachUser: "+tweetsForEachUser);
					}
					
					tweetCount = numTweetsGenerated; //Update tweetCount to numTweetsGenerated after adjusting the tweet bin for the last user
				}
				
				tweetCountsForUsers[generatedUserIndex] = tweetsForEachUser;
				generatedUserIndex++;
			}
			
			for (int i = 0; i < tweetCountsForUsers.length; i++)
			{
				System.out.print(tweetCountsForUsers[i]+" ");
			}
			
			System.out.println("WILL GENERATE DATA");
			
			int generatedTweetId = 1;
			String generatedDate = BEGIN_DATE;
			String generatedTweetText = "";
			String generatedUserName = "";
			
			//Generate the tweets to be stored InMemoryDb for each user
			for (int i = 0; i < tweetCountsForUsers.length; i++)
			{
				generatedUserName = "User"+(i+1);
				if (i < numFollowees)
				{
					generatedUserName += "_Followee";
				}
				else
				{
					generatedUserName += "_Follower";
				}
				
				for (int j = 0; j < tweetCountsForUsers[i]; j++)
				{
					//Generate stochastic tweet
			
//					generatedTweetText = generateTweetText();
					generatedTweetText = generateTweetText2();

					Tweet currentGeneratedTweet = new Tweet(generatedTweetText,generatedTweetId,generatedDate,generatedUserName);
					
					localDb.addTweet(currentGeneratedTweet);
					
					generatedTweetId++;
					
					//Generate a fake date starting from BEGIN_DATE
					SimpleDateFormat dateFormat = new SimpleDateFormat( "yyyy-MM-dd" );   
					Calendar cal = Calendar.getInstance();    
					try {
						cal.setTime( dateFormat.parse(generatedDate));
					} catch (ParseException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}    
					cal.add( Calendar.DATE, 1 ); 
					
					generatedDate=dateFormat.format(cal.getTime());
					
					//Check if the fake date is still in range, if not set it back to the default BEGIN_DATE;
					boolean dateRangeValid = true;
					dateRangeValid = checkDateRange(generatedDate,beginDate,endDate);
					
					if (!dateRangeValid)
					{
						generatedDate = BEGIN_DATE;
					}
				}
			}
			
			referenceUser = listOfUsers.get(0); //Assumes first user is the followee/referenceUser
			
			System.out.println("Done Generating Tweets");
			System.out.println(getLocalName()+" total tweets in local db: "+localDb.getTotalTweets());
			
		}
		if (readFrom == FROM_TEXT)
		{

			if (textFile != null)
			{
				long readTextStartTime = System.currentTimeMillis();
				readFromTextFile();
				logStageDuration("GET_USERS read text dataset", readTextStartTime, System.currentTimeMillis());

				ArrayList<Tweet> availableTweets;
				long availableTweetsStartTime = System.currentTimeMillis();
				availableTweets = getAvailableTweets();
				logStageDuration("GET_USERS build available tweet/user list", availableTweetsStartTime, System.currentTimeMillis());
							
				long setAvailableStartTime = System.currentTimeMillis();
				availableDb.setTweets(availableTweets);
				logStageDuration("GET_USERS store available tweets in memory", setAvailableStartTime, System.currentTimeMillis());

				//			for (Tweet t : datasetAvailable)
				//			{
				//				try {
				//					FileWriter writer = new FileWriter("demoTweetsVerification1000.txt", true); //append	
				//					BufferedWriter bufferedWriter = new BufferedWriter(writer);
				//					bufferedWriter.write("RyersonU\t"+t.getTweetId()+"\t"+t.getDateString()+"\t123456\t"+t.getUser()+"\t"+t.getTweetText());					
				//					bufferedWriter.newLine();
				//					bufferedWriter.close();
				//				} catch (IOException e) {
				//					e.printStackTrace();
				//				}
				//			
				//			}
			}
			else
			{
				readyToInitialize = false;
			}
		}

		if (readFrom == FROM_DB)
		{				 
			listOfUsers.clear();

			try {

				String url = "jdbc:mysql://" + serverName + ":" + portNumber + "/" + sid + "?useSSL=false";
				con = DriverManager.getConnection(url, user, pass);

				String query;

				if (totalTweetLimit > 0)
					query = "select screen_name,referenceUser,count(*) from (select * from usertweet where referenceUser='"+referenceUser+"' AND CAST(created_at AS DATE) BETWEEN '" + beginDate + "' AND '" + endDate + "' order by tweetid DESC LIMIT "+ totalTweetLimit +") as T1 group by screen_name order by count(*) DESC";				  
				else
					query = "select screen_name,referenceUser,count(*) from (select * from usertweet where referenceUser='"+referenceUser+"' AND CAST(created_at AS DATE) BETWEEN '" + beginDate + "' AND '" + endDate + "') as T1 group by screen_name order by count(*) DESC";

				//String queryst = "select * from ryersontop41users";

				stmt = con.createStatement();
				resultSet = stmt.executeQuery(query);

				String currentTwitterUser;

				resultSet.beforeFirst();
				while(!resultSet.isLast())
				{
					resultSet.next();
					currentTwitterUser = resultSet.getString(1);
					listOfUsers.add(currentTwitterUser);
				}

				resultSet.close();
				stmt.close();
				con.close();

			} catch (SQLException e1) {
				e1.printStackTrace();
			}

			//myGui.updateList(listOfUsers);

		}

		if (readyToInitialize)
		{
			myGui.updateList(listOfUsers);
			myGui.enableAllButtons();
			myGui.enableList();
			myGui.changeUserListTitle(readFrom);
			long endTime = System.currentTimeMillis();
			System.out.println("Load time: "+(endTime-startTime)+"ms");
			logStageDuration("GET_USERS total", startTime, endTime);
			System.out.println("READY TO INITIALIZE MULTI-AGENT SYSTEM");
			resetPendingDatasetSelection = false;
			myGui.showMessageBox("get users");
		}
		else
		{
			myGui.noFileSelected();
			myGui.enableAllButtons();
		}

	} //loadUsers

	private void resetExperiment()
	{
		System.out.println("Resetting experiment");

		stopExperimentContainers();
		File selectedTextDataset = readFrom == FROM_TEXT ? textFile : null;
		reportFailedExperimentOutputDeletes(
				ExperimentResetFiles.resetExperimentOutputs(new File("."), selectedTextDataset));
		clearExperimentState();
		resetPendingDatasetSelection = true;
		setUpWorkContainer();
		setUpUserSimContainer();

		if (myGui != null)
		{
			SwingUtilities.invokeLater(new Runnable() {
				@Override
				public void run() {
					myGui.resetForNewExperiment();
				}
			});
		}

		System.out.println("Experiment reset complete");
	}

	private void stopExperimentContainers()
	{
		for (AgentController currentAgentController : new ArrayList<AgentController>(listOfAgentControllers))
		{
			try
			{
				currentAgentController.kill();
			}
			catch (StaleProxyException e)
			{
				System.err.println("Could not stop experiment agent during reset: " + e.getMessage());
			}
		}

		stopContainer(workContainer, "workContainer");
		stopContainer(userSimContainer, "userSimContainer");
		workContainer = null;
		userSimContainer = null;
		agentController = null;
	}

	private void stopContainer(AgentContainer container, String containerName)
	{
		if (container == null)
		{
			return;
		}

		try
		{
			container.kill();
		}
		catch (StaleProxyException e)
		{
			System.err.println("Could not stop " + containerName + ": " + e.getMessage());
		}
	}

	private void clearExperimentState()
	{
		initialized = false;
		firstRun = true;
		algorithmRec = K_MEANS;
		recommendationConfig = RecommendationExperimentConfig.legacyDefault(K_MEANS, FROM_TEXT);
		readFrom = FROM_TEXT;
		totalTweetLimit = 0;
		numFollowers = 0;
		numFollowees = 0;
		numTweetsGenerated = 0;
		numRecAgents = 1;
		numArtificialTweets = 0;
		userGenerationSeed = null;
		totalUsers = 0;
		totalDocuments = 0;
		tweetsReadCount = 0;
		tweetDelay = 0;
		referenceUser = REFERENCE_USER;
		beginDate = null;
		endDate = null;
		textFile = null;
		corpusGenFile = null;
		allRecAgents = null;

		localDb = new InMemoryDb();
		availableDb = new InMemoryDb();

		usersRec.clear();
		listOfUsers.clear();
		listOfAgents.clear();
		listOfAgentControllers.clear();
		allUniqueDateTimes.clear();
		allUserFollowTweetCounts.clear();
		currentBagWords.clear();
			userTweetCounts.clear();
			userFolloweeMap.clear();
			datasetReferenceUsers.clear();
			datasetGraphNodeNames.clear();
			recommendationDatasetContext = RecommendationDatasetContext.empty();
			followeeFollowerCounts.clear();
		followeeFollowers.clear();
		allUniqueDocTerms.clear();
		tweetIdUser.clear();
		tweetIdText.clear();
		tweetIdDocumentVector.clear();
		controllerDocumentsPreparedDuringRead = false;
		allTermsDocumentFreq.clear();
		allUserDocuments.clear();
		aggregatedUserTweets.clear();

		userFollowee = null;
		testSetUsers = null;
		trainSetUsers = null;
			allUserDocumentsTFIDF = null;
			datasetFollowees = null;
			userTfidfVector = null;
			textUserLoadEstimates.clear();
	}

	private void reportFailedExperimentOutputDeletes(List<File> failedDeletes)
	{
		for (File failedDelete : failedDeletes)
		{
			System.err.println("Could not delete old experiment file: " + failedDelete.getPath());
		}
	}

	private String validateRecommendationModeBeforeLoading()
	{
		if (recommendationConfig.getTargetMode() == RecommendationTargetMode.ALGORITHM_DEFAULT)
		{
			return null;
		}
		if (readFrom != FROM_TEXT)
		{
			return "Explicit recommendation targets currently require a text dataset. "
					+ "Choose Algorithm Default for database, generated, or artificial data.";
		}
		if (!RecommendationModeSupport.isWorkingAlgorithm(algorithmRec))
		{
			return RecommendationModeSupport.algorithmName(algorithmRec)
					+ " does not have an active recommendation implementation for explicit targets.";
		}
		if (usersRec == null || usersRec.isEmpty())
		{
			return "Select at least one recommendation target before initialization.";
		}
		return null;
	}

	private boolean requiresControllerTextVectors()
	{
		return algorithmRec == MLP
				|| (recommendationConfig != null
						&& recommendationConfig.usesConvertedPipeline()
						&& RecommendationModeSupport.isClassifierAlgorithm(algorithmRec)
						&& recommendationConfig.getCandidateType()
								== RecommendationCandidateType.USER);
	}

	private String validateRecommendationModeForTextDataset()
	{
		if (recommendationConfig.getTargetMode() == RecommendationTargetMode.ALGORITHM_DEFAULT)
		{
			return null;
		}

		for (String target : usersRec)
		{
			if (!listOfUsers.contains(target))
			{
				return "Recommendation target '" + target + "' is not present in the active dataset.";
			}
		}

		RecommendationCandidateType candidateType = recommendationConfig.getCandidateType();
		if (candidateType == RecommendationCandidateType.USER && listOfUsers.size() < 2)
		{
			return "Similar Users mode needs at least two active users.";
		}

		int activeFolloweeCount = countDistinctFollowees(listOfUsers);
		if (candidateType == RecommendationCandidateType.FOLLOWEE && activeFolloweeCount == 0)
		{
			return "Followee Accounts mode needs field-0 followee labels for active users.";
		}
		if (RecommendationModeSupport.isClassifierAlgorithm(algorithmRec)
				&& activeFolloweeCount < 2)
		{
			return RecommendationModeSupport.algorithmName(algorithmRec)
					+ " needs at least two followee classes in this dataset.";
		}
		if (recommendationConfig.usesConvertedPipeline()
				&& RecommendationModeSupport.isClassifierAlgorithm(algorithmRec))
		{
			ArrayList<String> trainingCandidates = new ArrayList<String>(listOfUsers);
			trainingCandidates.removeAll(usersRec);
			if (countDistinctFollowees(trainingCandidates) < 2)
			{
				return RecommendationModeSupport.algorithmName(algorithmRec)
						+ " Similar Users mode needs at least two followee classes after "
						+ "recommendation targets are held out.";
			}
		}
		return null;
	}

	private String filterSvmTargetsWithoutUsableDocuments(
			int retweetsFlag,
			int hashTagsFlag,
			int stopWordsFlag)
	{
		if (!RecommendationModeSupport.isSvmFamilyAlgorithm(algorithmRec)
				|| readFrom != FROM_TEXT
				|| usersRec == null
				|| usersRec.isEmpty())
		{
			return null;
		}

		ArrayList<String> skippedTargets = new ArrayList<String>();
		ArrayList<String> usableTargets = new ArrayList<String>();
		for (String target : usersRec)
		{
			if (hasSvmUsableTargetDocument(
					target,
					retweetsFlag,
					hashTagsFlag,
					stopWordsFlag))
			{
				usableTargets.add(target);
			}
			else
			{
				skippedTargets.add(target);
			}
		}

		if (skippedTargets.isEmpty())
		{
			return null;
		}
		if (usableTargets.isEmpty())
		{
			return "No selected SVM target has a usable document after text preprocessing. "
					+ "Every selected target became empty or shorter than three tokens. "
					+ "Skipped: " + compactUserList(skippedTargets, 8) + ".";
		}

		usersRec = usableTargets;
		String warning = "Skipped " + skippedTargets.size()
				+ " selected SVM target(s) because they have no usable document "
				+ "after text preprocessing: "
				+ compactUserList(skippedTargets, 8) + ".";
		System.out.println(warning);
		if (myGui != null)
		{
			myGui.appendResult(warning);
		}
		return null;
	}

	private boolean hasSvmUsableTargetDocument(
			String target,
			int retweetsFlag,
			int hashTagsFlag,
			int stopWordsFlag)
	{
		if (target == null || availableDb == null)
		{
			return false;
		}
		List<Tweet> tweets = availableDb.getTweetsViewFromUser(target);
		if (tweets == null || tweets.isEmpty())
		{
			return false;
		}
		for (Tweet tweet : tweets)
		{
			String normalizedText = normalizeSvmPreflightText(
					tweet == null ? null : tweet.getTweetText(),
					retweetsFlag,
					hashTagsFlag,
					stopWordsFlag);
			if (countSvmPreflightTokens(normalizedText) >= MIN_WORDS)
			{
				return true;
			}
		}
		return false;
	}

	private String normalizeSvmPreflightText(
			String tweetText,
			int retweetsFlag,
			int hashTagsFlag,
			int stopWordsFlag)
	{
		String currentText = tweetText == null ? "" : tweetText;

		if (currentText.contains("Photo:"))
		{
			currentText = currentText.substring(0, currentText.indexOf("Photo:"));
		}
		if (currentText.contains("Photoset:"))
		{
			currentText = currentText.substring(0, currentText.indexOf("Photoset:"));
		}
		if (currentText.contains("RT @") && retweetsFlag == RE_TWEETS)
		{
			currentText = "";
		}
		else
		{
			currentText = RETWEET_PATTERN.matcher(currentText).replaceAll("RT ");
		}

		currentText = PUNCTUATION_PATTERN.matcher(currentText).replaceAll("");
		currentText = LINK_PATTERN.matcher(currentText).replaceAll(" ");
		if (hashTagsFlag == HASH_TAGS)
		{
			currentText = SPECIAL_CHARACTER_PATTERN.matcher(currentText).replaceAll(" ");
		}
		currentText = currentText.toLowerCase(Locale.ENGLISH);
		if (stopWordsFlag == STOP_WORDS)
		{
			currentText = removeSvmPreflightStopWords(currentText);
		}
		currentText = currentText.trim();
		currentText = NON_ALPHA_PATTERN.matcher(currentText).replaceAll("");
		currentText = MULTI_SPACE_PATTERN.matcher(currentText).replaceAll(" ");
		return currentText.trim();
	}

	private String removeSvmPreflightStopWords(String currentText)
	{
		String paddedText = " " + currentText + " ";
		for (String stopWord : stopWordsArray)
		{
			String stopWordPattern = " " + stopWord + " ";
			if (paddedText.contains(stopWordPattern))
			{
				paddedText = paddedText.replace(stopWordPattern, " ");
			}
		}
		return paddedText;
	}

	private int countSvmPreflightTokens(String normalizedText)
	{
		if (normalizedText == null || normalizedText.length() == 0)
		{
			return 0;
		}
		int tokenCount = 1;
		for (int index = 0; index < normalizedText.length(); index++)
		{
			if (normalizedText.charAt(index) == ' ')
			{
				tokenCount++;
			}
		}
		return tokenCount;
	}

	private String compactUserList(List<String> users, int limit)
	{
		if (users == null || users.isEmpty())
		{
			return "";
		}
		StringBuilder builder = new StringBuilder();
		int shown = Math.min(Math.max(1, limit), users.size());
		for (int i = 0; i < shown; i++)
		{
			if (i > 0)
			{
				builder.append(", ");
			}
			builder.append(users.get(i));
		}
		if (users.size() > shown)
		{
			builder.append(", and ");
			builder.append(users.size() - shown);
			builder.append(" more");
		}
		return builder.toString();
	}

	private int countDistinctFollowees(List<String> users)
	{
		HashSet<String> followees = new HashSet<String>();
		if (users == null || userFollowee == null)
		{
			return 0;
		}
		for (String currentUser : users)
		{
			String followee = userFollowee.get(currentUser);
			if (followee != null && followee.length() > 0)
			{
				followees.add(followee);
			}
		}
		return followees.size();
	}

	private void failInitialization(String message)
	{
		initialized = false;
		System.err.println("Initialization rejected: " + message);
		if (myGui != null)
		{
			myGui.handleInitializationFailure(message);
		}
	}

	private Map<Integer,Map<String,TreeMap<String,RecommendationCandidateEvidence>>>
			buildClassifierEvidenceByNode(
					int nodeCount,
					Map<String,Integer> primaryRecServers)
	{
		Map<Integer,Map<String,TreeMap<String,RecommendationCandidateEvidence>>> byNode =
				new LinkedHashMap<Integer,Map<String,TreeMap<String,RecommendationCandidateEvidence>>>();
		for (int node = 1; node <= nodeCount; node++)
		{
			Map<String,TreeMap<String,RecommendationCandidateEvidence>> targetEvidence =
					new TreeMap<String,TreeMap<String,RecommendationCandidateEvidence>>();
			for (String target : usersRec)
			{
				targetEvidence.put(
						target, new TreeMap<String,RecommendationCandidateEvidence>());
			}
			byNode.put(node, targetEvidence);
		}
		if (allUserDocumentsTFIDF == null || userFollowee == null)
		{
			return byNode;
		}

		for (String target : usersRec)
		{
			LinkedHashMap<String,Double> targetDocument =
					allUserDocumentsTFIDF.get(target);
			if (targetDocument == null)
			{
				continue;
			}
			for (Map.Entry<String,LinkedHashMap<String,Double>> candidateEntry
					: allUserDocumentsTFIDF.entrySet())
			{
				String candidate = candidateEntry.getKey();
				if (target.equals(candidate))
				{
					continue;
				}
				String followee = userFollowee.get(candidate);
				Integer primaryNode = primaryRecServers.get(candidate);
				if (followee == null || followee.length() == 0 || primaryNode == null
						|| !byNode.containsKey(primaryNode))
				{
					continue;
				}
				double similarity = calculateSparseCosineScore(
						targetDocument, candidateEntry.getValue());
				if (Double.isFinite(similarity) && similarity > 0.0)
				{
					byNode.get(primaryNode).get(target).put(
							candidate,
							new RecommendationCandidateEvidence(followee, similarity));
				}
			}
		}
		return byNode;
	}

	private double calculateSparseCosineScore(
			Map<String,Double> first,
			Map<String,Double> second)
	{
		if (first == null || second == null || first.isEmpty() || second.isEmpty())
		{
			return 0.0;
		}
		Map<String,Double> smaller = first.size() <= second.size() ? first : second;
		Map<String,Double> larger = smaller == first ? second : first;
		double dotProduct = 0.0;
		for (Map.Entry<String,Double> entry : smaller.entrySet())
		{
			Double otherValue = larger.get(entry.getKey());
			if (otherValue != null)
			{
				dotProduct += entry.getValue() * otherValue;
			}
		}
		return dotProduct;
	}

	public void startSimulation(GuiEvent ev)
	{
		boolean graphGenerationEnabled = recommendationConfig == null
				|| recommendationConfig.isGraphGenerationEnabled();
		try
		{
			Object requestedGraphGeneration = ev.getParameter(10);
			if (requestedGraphGeneration instanceof Boolean)
			{
				graphGenerationEnabled = ((Boolean) requestedGraphGeneration).booleanValue();
			}
		}
		catch (RuntimeException ignored)
		{
			// Older callers retain the graph setting captured during initialization.
		}

		try{
				Object[] starterAgentArgs = new Object[16];
				starterAgentArgs[0] = getAID();										
			starterAgentArgs[1] = referenceUser;		//referenceUser			NOT USED IN STARTER AGENT ANYMORE			
			starterAgentArgs[2] = "1"; //1
			starterAgentArgs[5] = "0"; //0
			starterAgentArgs[6] = totalUsers; //1

			starterAgentArgs[8] = "1"; //1
			starterAgentArgs[9] = 1;	//1
			starterAgentArgs[10] = recommendationConfig;
				starterAgentArgs[11] = myGui;
				starterAgentArgs[12] = numRecAgents;
				starterAgentArgs[13] = Boolean.valueOf(graphGenerationEnabled);
				starterAgentArgs[14] = getCurrentUserAgentNames();
				starterAgentArgs[15] = currentRecommendationRunId;

			String starterAgentName = "Starter Agent";
			agentController = workContainer.createNewAgent(starterAgentName, StarterAgent.class.getName(), starterAgentArgs);
			listOfAgentControllers.add(agentController);
			agentController.start();
			listOfAgents.add(starterAgentName);
			System.out.println("Made agent: "+starterAgentName);
			System.out.println(getLocalName()+" getAID(): "+getAID());
			
			if (textFile != null)
				System.out.println("Reading from textFile: "+ textFile.getName());

			} catch (Exception e) {
				String message = "Simulation could not start: " + e.getMessage();
				System.err.println(message);
				myGui.handleSimulationStartFailure(message);
				return;
			}

			myGui.disableStartButton();
		myGui.changeRecommendUserTitle();
		
		firstRun = false;
		
		}

	private ArrayList<String> getCurrentUserAgentNames()
	{
		ArrayList<String> userAgentNames = new ArrayList<String>();
		for (String agentName : listOfAgents)
		{
			if (agentName != null && agentName.endsWith("-UserAgent"))
			{
				userAgentNames.add(agentName);
			}
		}
		return userAgentNames;
	}
	
	public void initializeSetup(GuiEvent ev)
	{
		String currentTwitterUser = "RyersonU";
		/*referenceUser = "Ryerson";
		String beginDate = "2007-04-04";
		String endDate = "2015-05-24";	
		int kRecommend = 3;
		int hashtags_temp = 1; //1 to remove hash tags	
		int retweetedby_temp = 1; //1 if it is retweeted	
		int stopWordFlag_temp = 0; //remove stop words	

		int readFrom = 1;	//1 to read FROM_DB	
		//Modify for servers @Jason	
		int numOrgAgents = 1;	//number of recommender servers (RecommenderServiceAgents) # Organizing Agents
		int numRecAgents = 4; //numRecAgentss OrganizingAgent, numRecAgentss RecommenderServiceAgent 	 #RecommenderAgents
		//@Jason change limit of overall tweets  
		//  int totalTweetLimit = 7446;
		int totalTweetLimit = 300; //DO NOT SET < 300
		//  int totalTweetLimit = 2000;


		//algorithmRec = COS_SIM;
		  algorithmRec=K_MEANS;
		 */

		/*
		ge.addParameter(numServers);
		ge.addParameter(tweetLimit);
		ge.addParameter(beginDate);
		ge.addParameter(endDate);
		ge.addParameter(kRecommend);
		ge.addParameter(referenceUser);
		ge.addParameter(hashTags);
		ge.addParameter(retweets);
		ge.addParameter(stopWords);
		ge.addParameter(algorithmRec);
		 */
		//readFrom = FROM_DB;	//1 to read FROM_DB	

//		referenceUser = (String) ev.getParameter(5);
		beginDate = (String) ev.getParameter(2);
		endDate = (String) ev.getParameter(3);	
		int kRecommend = (Integer) ev.getParameter(4);
		int hashtags_temp = (Integer) ev.getParameter(6); //1 to remove hash tags	
		int retweetedby_temp = (Integer) ev.getParameter(7); //1 if it is retweeted	
		int stopWordFlag_temp = (Integer) ev.getParameter(8); //remove stop words			
		//Modify for servers @Jason	
		int numOrgAgents = 1;	//number of recommender servers (RecommenderServiceAgents) # Organizing Agents
		numRecAgents = (Integer) ev.getParameter(0); //numRecAgentss OrganizingAgent, numRecAgentss RecommenderServiceAgent 	 #RecommenderAgents
		RecommendationMemoryCoordinator.configureRequestedParallelism(
				numRecAgents);
		NodeCapacityRegistry.configureForNodeCount(
				numRecAgents,
				RuntimeResourceSettings.currentMaxHeapMb());
		//@Jason change limit of overall tweets
		totalTweetLimit = (Integer) ev.getParameter(1); //DO NOT SET < 300
		System.out.println(getLocalName()+" totalTweetLimits initialize: "+totalTweetLimit);
		
		if (totalTweetLimit == 0)
			totalTweetLimit = localDb.getTotalTweets();

		algorithmRec=(Integer) ev.getParameter(9);
		NodeExecutionMonitor.beginExperiment(
				numRecAgents,
				RecommendationModeSupport.algorithmName(algorithmRec));
		numFollowees = (Integer) ev.getParameter(10);
		numFollowers = (Integer) ev.getParameter(11);
		numTweetsGenerated = (Integer) ev.getParameter(12);


		tweetDelay = TWEET_DELAY;

		usersRec = myGui.getUsersRec();
		currentRecommendationRunId = RecommendationRunLifecycle.createRunId();
		RecommendationSchedulerRegistry.beginRun(
				currentRecommendationRunId,
				numRecAgents,
				RuntimeResourceSettings.currentMaxHeapMb(),
				algorithmRec,
				usersRec == null ? 0 : usersRec.size());
		RecommendationTargetMode targetMode = RecommendationTargetMode.ALGORITHM_DEFAULT;
		try
		{
			Object requestedMode = ev.getParameter(13);
			if (requestedMode instanceof RecommendationTargetMode)
			{
				targetMode = (RecommendationTargetMode) requestedMode;
			}
		}
			catch (RuntimeException ignored)
			{
				// Older callers omit this parameter and retain the historical behavior.
			}
			boolean graphGenerationEnabled = true;
			try
			{
				Object requestedGraphGeneration = ev.getParameter(14);
				if (requestedGraphGeneration instanceof Boolean)
				{
					graphGenerationEnabled = ((Boolean) requestedGraphGeneration).booleanValue();
				}
			}
			catch (RuntimeException ignored)
			{
				// Older callers omit this parameter and retain graph generation.
			}
			recommendationConfig = new RecommendationExperimentConfig(
					targetMode,
					algorithmRec,
					readFrom,
					usersRec,
					graphGenerationEnabled,
					readFrom == FROM_TEXT ? textFile : null);

		String basicModeError = validateRecommendationModeBeforeLoading();
		if (basicModeError != null)
		{
			failInitialization(basicModeError);
			return;
		}
		initialized = true;

		myGui.disableList();
		
		String directoryNameTiming = "Results/Timing/" + referenceUser;
		String directoryNameRecommendation = "Results/Recommendations/" + referenceUser;
						
		createDir(directoryNameTiming);
		createDir(directoryNameRecommendation);
		
		long startTime = System.currentTimeMillis();
		logStagePoint("INITIALIZE started nodes="+numRecAgents+" algorithm="+algorithmRec+" users="+listOfUsers.size());
		//@Jason
		
		if (readFrom == FROM_ARTIFICIAL)
		{
			localDb.clearDb();
			availableDb.clearDb();
			allUserFollowTweetCounts.clear();
			
			for (String corpusName : corpusfileNames)
			{
				textFile = new File(generatingParameterFolderName+corpusName);
				readFromTextFile();
			}

			ArrayList<Tweet> availableTweets;
			availableTweets = getAvailableTweets();
						
			availableDb.setTweets(availableTweets);		

			setGeneratedWordBins4();
			
			System.out.println("controller availableDb.getTweetsSize(): "+ availableDb.getTotalTweets());
			
			
			//Making agents

			try{

				//@Jason added algorithmRec arg
				//Object[] orgAgentArgs = new Object[16];
				Object[] orgAgentArgs = new Object[19];
				orgAgentArgs[0] = getAID();										
				orgAgentArgs[1] = referenceUser;	//NOT USED IN ORGANIZING AGENT ANYMORE								

				orgAgentArgs[3] = beginDate;
				orgAgentArgs[4] = endDate;
				orgAgentArgs[5] = hashtags_temp;

				orgAgentArgs[7] = retweetedby_temp;
				orgAgentArgs[8] = stopWordFlag_temp;



				orgAgentArgs[11] = 1;
				orgAgentArgs[12] = 1;
				orgAgentArgs[13] = listOfUsers.size();
				orgAgentArgs[14] = 1;		
				orgAgentArgs[15] = numRecAgents;

				//@Jason added algorithmRec argument
				orgAgentArgs[16] = algorithmRec;
				orgAgentArgs[17] = recommendationConfig;

				orgAgentArgs[18] = myGui;

				for(int i=1; i<numOrgAgents+1; i++)
				{
					String orgAgentName = "Organizing Agent" + i;
					agentController = workContainer.createNewAgent(orgAgentName, OrganizingAgent.class.getName(), orgAgentArgs);
					agentController.start();
					listOfAgents.add(orgAgentName);
					listOfAgentControllers.add(agentController);
					System.out.println("Made agent: "+orgAgentName);

				}


				
				int[] bins = new int[numRecAgents]; //bins for recommender agents

				int currentTweetCount;
				int totalRecBin = 0; //Number of tweets in a recommender agent's bin
				int smallestBinIndex;

				totalUsers = 0;
				
				for (String user : listOfUsers)
				{
					totalUsers++;

					currentTweetCount = availableDb.getTweetCountFromUser(user);
				
					smallestBinIndex = selectCapacityAwareBin(bins);
//					totalRecBin = bins.get(smallestBinIndex);
					totalRecBin = bins[smallestBinIndex];
					totalRecBin += currentTweetCount;
//					bins.set(smallestBinIndex, totalRecBin);
					bins[smallestBinIndex] = totalRecBin;
					smallestBinIndex++; //add 1 to index to make index start from 1 instead of 0

					//Add list of recommender servers and increase bins accordingly if distributed
					//ie. add the tweet count to all recommenders if there are 2 for the user looking for recommendation
					ArrayList<Integer> recServers = new ArrayList<Integer>();
					if (usersRec.contains(user))
					{	
						for (int i = 0; i < numRecAgents; i++)
						{
							if (smallestBinIndex-1 != i)
							{
//								totalRecBin = bins.get(i);
								totalRecBin = bins[i];
								totalRecBin += currentTweetCount;
//								bins.set(i,totalRecBin);
								bins[i] = totalRecBin;
							}
							recServers.add(i+1);
						}
					}
					else
					{
						recServers.add(smallestBinIndex);
					}
					


					long userTweetDelay = getConfiguredTweetDelay(user);

					
//					userFollowTweetCounts = new LinkedHashMap<String,Integer>();
					LinkedHashMap<String,Integer> userFollowTweetCounts = new LinkedHashMap<String,Integer>();
					
					for (String otherUser : listOfUsers)
					{
						if (!otherUser.equals(user))
						{
							if (user.equals(referenceUser))
							{
								int tweetCountToFollow;
								tweetCountToFollow = calculateTweetsToFollow(availableDb.getTweetCountFromUser(otherUser));
								userFollowTweetCounts.put(otherUser, tweetCountToFollow);
							}
							
						}
					}

					allUserFollowTweetCounts.put(user, userFollowTweetCounts);
//					System.out.println("user: "+user+" userFollowTweetCounts: " + userFollowTweetCounts);
					
					//Object[] userAgentArgs = new Object[15];							
					Object[] userAgentArgs = new Object[31];
					userAgentArgs[0] = getAID();										
					userAgentArgs[1] = referenceUser;							

					userAgentArgs[3] = beginDate;
					userAgentArgs[4] = endDate;
					userAgentArgs[5] = hashtags_temp;

					userAgentArgs[7] = retweetedby_temp;
					userAgentArgs[8] = stopWordFlag_temp;
					userAgentArgs[9] = recommendationConfig;

					userAgentArgs[10] = recServers;

					userAgentArgs[11] = smallestBinIndex;				

					userAgentArgs[12] = 1;
					userAgentArgs[13] = 1;	
					//					userAgentArgs[14] = tweetDelay;
					userAgentArgs[14] = userTweetDelay;

					//@Jason added limit and referenceUser;
					userAgentArgs[15] = totalTweetLimit;
					userAgentArgs[16] = referenceUser;
					userAgentArgs[17] = kRecommend;
					userAgentArgs[18] = algorithmRec;
					userAgentArgs[19] = myGui;
					userAgentArgs[20] = readFrom;
					userAgentArgs[21] = availableDb.getTweetsFromUser(user);
					userAgentArgs[22] = true;
					userAgentArgs[23] = 1;
					userAgentArgs[24] = referenceUser;
					
					if (user.equals(referenceUser))
					{
						userAgentArgs[25] = true;
					}
					else
					{	
						userAgentArgs[25] = false;
					}
					
					userAgentArgs[26] = averageUserShape;
					userAgentArgs[27] = averageUserScale;
					userAgentArgs[28] = allUserTfidfWordsBins.get(user); //LinkedHashMap<Double,ArrayList<String>>
					userAgentArgs[29] = userFollowee.get(user); //followee of User
					userAgentArgs[30] = recommendationDatasetContext;

					String userAgentName = user + "-UserAgent";
					agentController = workContainer.createNewAgent(userAgentName, MobileAgent.class.getName(), userAgentArgs);   
					agentController.start();
					listOfAgents.add(userAgentName);
					listOfAgentControllers.add(agentController);
					//System.out.println("Made agent: "+userAgentName);
//					System.out.println("totalUsers: "+totalUsers);

				}

				int totalnumberoftweets =0;
//				for(int k=0; k<bins.size();k++)
				for(int k=0; k<bins.length;k++)
				{
					//totalnumberoftweets += bins.get(k);
//					System.out.println("bin "+k+": "+bins.get(k));
					System.out.println("bin "+k+": "+bins[k]);
				}

				myGui.updateList(listOfUsers); //update before creating recommender agents

				int j =0;
				for(int i=1; i<numRecAgents+1; i++)
				{

					Object[] recAgentArgs = new Object[19];
					recAgentArgs[0] = getAID();										
					recAgentArgs[1] = referenceUser;								

					recAgentArgs[3] = beginDate;
					recAgentArgs[4] = endDate;
					recAgentArgs[5] = hashtags_temp;

					recAgentArgs[7] = retweetedby_temp;
					recAgentArgs[8] = stopWordFlag_temp;



					recAgentArgs[11] = 1;
					recAgentArgs[12] = 1;
//					recAgentArgs[13] = bins.get(j);
					recAgentArgs[13] = bins[j];

					recAgentArgs[15] = numRecAgents;

					//@Jason added argument for algorithm to use
					recAgentArgs[16] = algorithmRec;
					recAgentArgs[17] = recommendationConfig;
					recAgentArgs[18] = myGui;

					String recAgentName = "Recommender-ServiceAgent" + i;
					agentController = workContainer.createNewAgent(recAgentName, RecommenderAgent.class.getName(), recAgentArgs);
					agentController.start();
					listOfAgents.add(recAgentName);
					listOfAgentControllers.add(agentController);
					System.out.println("Made agent: "+recAgentName);
					j++;
				}

			}catch (Exception e){
				System.err.println("ARTIFICIAL Text Read Initialize Error: " + e.getMessage());
			}  

			System.out.println("ARTIFICIAL TOTALUSERS: "+totalUsers);
			
		}
		
		if (readFrom == FROM_GENERATION)
		{
			availableDb.clearDb();
			allUserFollowTweetCounts.clear();
			
			System.out.println(getLocalName()+" initialize total tweets localDb: "+localDb.getTotalTweets());
			
			ArrayList<Tweet> availableTweets;
			availableTweets = getAvailableTweets();
						
			availableDb.setTweets(availableTweets);	
			
			Collections.reverse(listOfUsers); //Change order of users back to original with followee at the top
					
			//Making agents

			try{

				//@Jason added algorithmRec arg
				//Object[] orgAgentArgs = new Object[16];
				Object[] orgAgentArgs = new Object[19];
				orgAgentArgs[0] = getAID();										
				orgAgentArgs[1] = referenceUser;			//Not used	in organizing agent anymore			

				orgAgentArgs[3] = beginDate;
				orgAgentArgs[4] = endDate;
				orgAgentArgs[5] = hashtags_temp;

				orgAgentArgs[7] = retweetedby_temp;
				orgAgentArgs[8] = stopWordFlag_temp;



				orgAgentArgs[11] = 1;
				orgAgentArgs[12] = 1;
				orgAgentArgs[13] = listOfUsers.size();
				orgAgentArgs[14] = 1;		
				orgAgentArgs[15] = numRecAgents;

				//@Jason added algorithmRec argument
				orgAgentArgs[16] = algorithmRec;
				orgAgentArgs[17] = recommendationConfig;

				orgAgentArgs[18] = myGui;

				for(int i=1; i<numOrgAgents+1; i++)
				{
					String orgAgentName = "Organizing Agent" + i;
					agentController = workContainer.createNewAgent(orgAgentName, OrganizingAgent.class.getName(), orgAgentArgs);
					agentController.start();
					listOfAgents.add(orgAgentName);
					listOfAgentControllers.add(agentController);
					System.out.println("Made agent: "+orgAgentName);

				}


				//Create bins for recommender agents
//				ArrayList<Integer> bins = new ArrayList<Integer>();
//				for(int i=0; i<numRecAgents; i++)
//				{
//					bins.add(0);
//				}
				
				int[] bins = new int[numRecAgents]; //bins for recommender agents

				int currentTweetCount;
				int totalRecBin = 0; //Number of tweets in a recommender agent's bin
				int smallestBinIndex;

				totalUsers = 0;
				
				for (String user : listOfUsers)
				{
					totalUsers++;

					currentTweetCount = availableDb.getTweetCountFromUser(user);
					smallestBinIndex = selectCapacityAwareBin(bins);
//					totalRecBin = bins.get(smallestBinIndex);
					totalRecBin = bins[smallestBinIndex];
					totalRecBin += currentTweetCount;
//					bins.set(smallestBinIndex, totalRecBin);
					bins[smallestBinIndex] = totalRecBin;
					smallestBinIndex++; //add 1 to index to make index start from 1 instead of 0

					//Add list of recommender servers and increase bins accordingly if distributed
					//ie. add the tweet count to all recommenders if there are 2 for the user looking for recommendation
					ArrayList<Integer> recServers = new ArrayList<Integer>();
					if (usersRec.contains(user))
					{	
						for (int i = 0; i < numRecAgents; i++)
						{
							if (smallestBinIndex-1 != i)
							{
//								totalRecBin = bins.get(i);
								totalRecBin = bins[i];
								totalRecBin += currentTweetCount;
//								bins.set(i,totalRecBin);
								bins[i] = totalRecBin;
							}
							recServers.add(i+1);
						}
					}
					else
					{
						recServers.add(smallestBinIndex);
					}

					long userTweetDelay = getConfiguredTweetDelay(user);

					
//					userFollowTweetCounts = new LinkedHashMap<String,Integer>();
					LinkedHashMap<String,Integer> userFollowTweetCounts = new LinkedHashMap<String,Integer>();
					
					for (String otherUser : listOfUsers)
					{
						if (!otherUser.equals(user))
						{
							if (user.equals(referenceUser))
							{
								int tweetCountToFollow;
								tweetCountToFollow = calculateTweetsToFollow(availableDb.getTweetCountFromUser(otherUser));
								userFollowTweetCounts.put(otherUser, tweetCountToFollow);
							}
							
						}
					}

					allUserFollowTweetCounts.put(user, userFollowTweetCounts);
					System.out.println("user: "+user+" userFollowTweetCounts: " + userFollowTweetCounts);
					
					//Object[] userAgentArgs = new Object[15];							
					Object[] userAgentArgs = new Object[31];
					userAgentArgs[0] = getAID();										
					userAgentArgs[1] = referenceUser;							

					userAgentArgs[3] = beginDate;
					userAgentArgs[4] = endDate;
					userAgentArgs[5] = hashtags_temp;

					userAgentArgs[7] = retweetedby_temp;
					userAgentArgs[8] = stopWordFlag_temp;
					userAgentArgs[9] = recommendationConfig;

					userAgentArgs[10] = recServers;

					userAgentArgs[11] = smallestBinIndex;				

					userAgentArgs[12] = 1;
					userAgentArgs[13] = 1;	
					//					userAgentArgs[14] = tweetDelay;
					userAgentArgs[14] = userTweetDelay;

					//@Jason added limit and referenceUser;
					userAgentArgs[15] = totalTweetLimit;
					userAgentArgs[16] = referenceUser;
					userAgentArgs[17] = kRecommend;
					userAgentArgs[18] = algorithmRec;
					userAgentArgs[19] = myGui;
					userAgentArgs[20] = readFrom;
					userAgentArgs[21] = availableDb.getTweetsFromUser(user);
					userAgentArgs[22] = true;
					userAgentArgs[23] = 1;
					userAgentArgs[24] = referenceUser;
					
					if (user.equals(referenceUser))
					{
						userAgentArgs[25] = true;
					}
					else
					{	
						userAgentArgs[25] = false;
					}
					userAgentArgs[29] = userFollowee.get(user); //followee of User
					userAgentArgs[30] = recommendationDatasetContext;

					String userAgentName = user + "-UserAgent";
					agentController = workContainer.createNewAgent(userAgentName, MobileAgent.class.getName(), userAgentArgs);   
					agentController.start();
					listOfAgents.add(userAgentName);
					listOfAgentControllers.add(agentController);
					//System.out.println("Made agent: "+userAgentName);
//					System.out.println("totalUsers: "+totalUsers);

				}

				int totalnumberoftweets =0;
//				for(int k=0; k<bins.size();k++)
				for(int k=0; k<bins.length;k++)
				{
					//totalnumberoftweets += bins.get(k);
//					System.out.println("bin "+k+": "+bins.get(k));
					System.out.println("bin "+k+": "+bins[k]);
				}
				
				myGui.updateList(listOfUsers); //update before creating recommender agents

				int j =0;
				for(int i=1; i<numRecAgents+1; i++)
				{

					Object[] recAgentArgs = new Object[19];
					recAgentArgs[0] = getAID();										
					recAgentArgs[1] = referenceUser;								

					recAgentArgs[3] = beginDate;
					recAgentArgs[4] = endDate;
					recAgentArgs[5] = hashtags_temp;

					recAgentArgs[7] = retweetedby_temp;
					recAgentArgs[8] = stopWordFlag_temp;



					recAgentArgs[11] = 1;
					recAgentArgs[12] = 1;
					recAgentArgs[13] = bins[j];

					recAgentArgs[15] = numRecAgents;

					//@Jason added argument for algorithm to use
					recAgentArgs[16] = algorithmRec;
					recAgentArgs[17] = recommendationConfig;
					recAgentArgs[18] = myGui;

					String recAgentName = "Recommender-ServiceAgent" + i;
					agentController = workContainer.createNewAgent(recAgentName, RecommenderAgent.class.getName(), recAgentArgs);
					agentController.start();
					listOfAgents.add(recAgentName);
					listOfAgentControllers.add(agentController);
					System.out.println("Made agent: "+recAgentName);
					j++;
				}


			}catch (Exception e){
				System.err.println("Generate Initialize Error: " + e.getMessage());
			}  

			System.out.println("allUserFollowTweetCounts: "+allUserFollowTweetCounts);
			
			
			
			System.out.println("Generated Dataset");
		}
		
		///*IMPORTANT CODE FOR READING FROM TEXT FILE*/
			if (readFrom == FROM_TEXT)
			{
				long textInitializeStartTime = System.currentTimeMillis();
				localDb.clearDb();
				availableDb.clearDb();
				allUserFollowTweetCounts.clear();
				textUserLoadEstimates.clear();
			
			long readTextStartTime = System.currentTimeMillis();
			readFromTextFile(
					retweetedby_temp,
					hashtags_temp,
					stopWordFlag_temp);
			logStageDuration("INITIALIZE read text dataset", readTextStartTime, System.currentTimeMillis());

			ArrayList<Tweet> availableTweets;
			Classifier trainedSVM = null; //SVM to train
			
				long availableTweetsStartTime = System.currentTimeMillis();
				availableTweets = getAvailableTweets();
				myGui.updateList(listOfUsers);
				logStageDuration("INITIALIZE build available tweet/user list", availableTweetsStartTime, System.currentTimeMillis());

				long setAvailableStartTime = System.currentTimeMillis();
				availableDb.setTweets(availableTweets);
				logStageDuration("INITIALIZE store available tweets in memory", setAvailableStartTime, System.currentTimeMillis());
				
				long followeesStartTime = System.currentTimeMillis();
				datasetFollowees = findListFollowees();
				logStageDuration("INITIALIZE find dataset followees", followeesStartTime, System.currentTimeMillis());

				if (algorithmRec == SVM)
				{
					try
					{
						ExactSvmWorkerRunner.validateBestCaseStructure(
								listOfUsers.size(),
								datasetFollowees.size(),
								numRecAgents);
					}
					catch (IllegalArgumentException unsafeExactSvm)
					{
						failInitialization(unsafeExactSvm.getMessage());
						return;
					}
				}

				String datasetModeError = validateRecommendationModeForTextDataset();
				if (datasetModeError != null)
				{
					failInitialization(datasetModeError);
					return;
				}
				String svmTargetError = filterSvmTargetsWithoutUsableDocuments(
						retweetedby_temp,
						hashtags_temp,
						stopWordFlag_temp);
				if (svmTargetError != null)
				{
					failInitialization(svmTargetError);
					return;
				}
				recommendationConfig = new RecommendationExperimentConfig(
						targetMode,
						algorithmRec,
						readFrom,
						usersRec,
						graphGenerationEnabled,
						readFrom == FROM_TEXT ? textFile : null);

				boolean boundedTextAgentProvisioning =
						supportsBoundedCorpusStreaming(algorithmRec)
						&& (shouldUseBoundedTextAgentProvisioning(
								listOfUsers.size())
								|| RecommendationSchedulerRegistry
										.snapshot().isActive());
				int expectedTextParticipants = calculateTextParticipantCount(
						listOfUsers,
						usersRec,
						boundedTextAgentProvisioning);
				if (boundedTextAgentProvisioning)
				{
					String boundedMessage =
							"Large text dataset detected: retaining normal agents for "
							+ countActiveTargets(listOfUsers, usersRec)
							+ " selected target(s) and streaming the remaining "
							+ Math.max(0, listOfUsers.size()
									- countActiveTargets(listOfUsers, usersRec))
							+ " users through one bounded corpus agent.";
					System.out.println(boundedMessage);
					myGui.appendResult(boundedMessage);
				}
				
				//Distributed classifiers
			// if (algorithmRec == MLP && numRecAgents > 1)
			// {
				// findUniqueDocTerms(retweetedby_temp,hashtags_temp,stopWordFlag_temp);
			// }
			boolean needsGlobalClassifierEvidence =
					recommendationConfig.usesConvertedPipeline()
					&& RecommendationModeSupport.isClassifierAlgorithm(algorithmRec)
					&& recommendationConfig.getCandidateType() == RecommendationCandidateType.USER;
			if (algorithmRec == MLP || needsGlobalClassifierEvidence)
			{
				long mlpPrepStartTime = System.currentTimeMillis();
				processTweets(retweetedby_temp,hashtags_temp,stopWordFlag_temp);
				for (String target : usersRec)
				{
					if (!allUserDocumentsTFIDF.containsKey(target))
					{
						failInitialization(
								"Recommendation target '" + target
								+ "' has no usable document after text preprocessing.");
						return;
					}
				}
				if (algorithmRec == MLP)
				{
					determineTrainingTestSet();
					if (needsGlobalClassifierEvidence)
					{
						trainSetUsers.removeAll(usersRec);
						testSetUsers.removeAll(usersRec);
						if (countDistinctFollowees(trainSetUsers) < 2)
						{
							failInitialization(
									"MLP Similar Users mode needs at least two followee classes in the "
									+ "training set after recommendation targets are held out.");
							return;
						}
					}
				}
				logStageDuration("INITIALIZE MLP central text/training-test preparation", mlpPrepStartTime, System.currentTimeMillis());
			}
			
			// if (algorithmRec == SVM && numRecAgents > 1)
			// {
				// System.out.println("Controller Entered Distributed Classifiers");
				// processTweets(retweetedby_temp,hashtags_temp,stopWordFlag_temp);
				// System.out.println("Controller Finished Text Processing Tweets");
				// determineTrainingTestSet();
				// System.out.println("Controller Finished Determining TrainingTest Sets");
				// String arffDirName = "Dataset/Arff_files/";
				// createDir(arffDirName);			
				// String trainSetFilePath = arffDirName + "train_set_controller.txt";
				// createTrainingFile(trainSetFilePath);
				// trainedSVM = trainCentralSVM(trainSetFilePath);
				
				
				// /*To set availableDb based on test set Users*/
				// availableTweets = getDistributedTestSet();
			// }
			
					int[] bins = new int[numRecAgents];
				long[] balanceBins = new long[numRecAgents];
				Map<String,UserNodeAssignment> textNodeAssignments = null;
				boolean exactSvmDiskBackedWaves = false;
				long[] exactSvmNodeTermEntries = new long[numRecAgents];
				if (algorithmRec == SVM)
				{
					long admissionStartTime = System.currentTimeMillis();
					try
					{
						textNodeAssignments =
								buildBalancedTextNodeAssignments(bins, balanceBins);
						exactSvmNodeTermEntries =
								estimateExactSvmRetainedSparseTermEntriesByNode(
										textNodeAssignments);
						long totalRetainedSparseTermEntries = 0L;
						long largestNodeSparseTermEntries = 0L;
						for (long nodeTermEntries : exactSvmNodeTermEntries)
						{
							totalRetainedSparseTermEntries = saturatingAdd(
									totalRetainedSparseTermEntries,
									nodeTermEntries);
							largestNodeSparseTermEntries = Math.max(
									largestNodeSparseTermEntries,
									nodeTermEntries);
						}
						ExactSvmCorpusAdmission.WavePlan admission =
								ExactSvmCorpusAdmission.wavePlan(
										totalRetainedSparseTermEntries,
										largestNodeSparseTermEntries,
										listOfUsers.size(),
										numRecAgents);
						System.out.println("Exact SVM corpus preflight: " + admission);
						admission.validate();
						exactSvmDiskBackedWaves =
								admission.requiresDiskBackedWaves();
						if (exactSvmDiskBackedWaves && myGui != null)
						{
							myGui.appendResult(
									"Exact SVM will spool node corpora to disk and "
									+ "execute recommender nodes in memory-safe waves.");
						}
					}
					catch (IllegalArgumentException unsafeCorpus)
					{
						availableDb.clearDb();
						localDb.clearDb();
						availableTweets.clear();
						failInitialization(unsafeCorpus.getMessage());
						return;
					}
					logStageDuration(
							"INITIALIZE exact SVM retained-corpus preflight",
							admissionStartTime,
							System.currentTimeMillis());
				}

				System.out.println("controller availableDb.getTweetsSize(): "+ availableDb.getTotalTweets());
			//Making agents
			try{
				long textAgentCreationStartTime = System.currentTimeMillis();

				//@Jason added algorithmRec arg
				//Object[] orgAgentArgs = new Object[16];
				Object[] orgAgentArgs = new Object[19];
				orgAgentArgs[0] = getAID();										
				orgAgentArgs[1] = referenceUser;								

				orgAgentArgs[3] = beginDate;
				orgAgentArgs[4] = endDate;
				orgAgentArgs[5] = hashtags_temp;

				orgAgentArgs[7] = retweetedby_temp;
				orgAgentArgs[8] = stopWordFlag_temp;



				orgAgentArgs[11] = 1;
				orgAgentArgs[12] = 1;
					orgAgentArgs[13] = expectedTextParticipants;
				orgAgentArgs[14] = 1;		
				orgAgentArgs[15] = numRecAgents;

				//@Jason added algorithmRec argument
				orgAgentArgs[16] = algorithmRec;
				orgAgentArgs[17] = recommendationConfig;

				orgAgentArgs[18] = myGui;

				long organizingAgentStartTime = System.currentTimeMillis();
				for(int i=1; i<numOrgAgents+1; i++)
				{
					String orgAgentName = "Organizing Agent" + i;
					agentController = workContainer.createNewAgent(orgAgentName, OrganizingAgent.class.getName(), orgAgentArgs);
					agentController.start();
					listOfAgents.add(orgAgentName);
					listOfAgentControllers.add(agentController);
					System.out.println("Made agent: "+orgAgentName);

				}
				logStageDuration("INITIALIZE create organizing agents", organizingAgentStartTime, System.currentTimeMillis());


				//Create bins for recommender agents
//				ArrayList<Integer> bins = new ArrayList<Integer>();
//				for(int i=0; i<numRecAgents; i++)
				//				{
				//					bins.add(0);
				//				}

					if (textNodeAssignments == null)
					{
						textNodeAssignments =
								buildBalancedTextNodeAssignments(bins, balanceBins);
					}
					Map<String,Integer> textPrimaryRecServers = new LinkedHashMap<String,Integer>();
					for (Map.Entry<String,UserNodeAssignment> assignment : textNodeAssignments.entrySet())
					{
						textPrimaryRecServers.put(
								assignment.getKey(), assignment.getValue().primaryRecServer);
					}
						Map<Integer,Map<String,TreeMap<String,RecommendationCandidateEvidence>>>
								classifierEvidenceByNode = needsGlobalClassifierEvidence
							? buildClassifierEvidenceByNode(
									numRecAgents, textPrimaryRecServers)
							: Collections.<Integer,Map<String,TreeMap<String,RecommendationCandidateEvidence>>>emptyMap();
						allUserDocuments.clear();
						allUserDocumentsTFIDF = null;

						if (boundedTextAgentProvisioning)
						{
							Map<String,CorpusStreamRoute> corpusRoutes =
									new LinkedHashMap<String,CorpusStreamRoute>();
							for (Map.Entry<String,UserNodeAssignment> assignment
									: textNodeAssignments.entrySet())
							{
								UserNodeAssignment nodeAssignment = assignment.getValue();
								corpusRoutes.put(
										assignment.getKey(),
										new CorpusStreamRoute(
												nodeAssignment.recServers,
												nodeAssignment.primaryRecServer));
							}

							ReplayableCorpusStream corpusStream;
							try
							{
								corpusStream = ReplayableCorpusStream.create(
										availableDb.getTweets(),
										corpusRoutes,
										userFollowee,
										new LinkedHashSet<String>(usersRec));
							}
							catch (IOException e)
							{
								e.printStackTrace();
								failInitialization(
										"Could not prepare the bounded corpus stream: "
										+ e.getMessage());
								killContainer();
								return;
							}

							if (corpusStream.size() > 0)
							{
								Object[] corpusAgentArgs = new Object[] {
									corpusStream,
									myGui
								};
								String corpusAgentName = "Corpus-Stream-UserAgent";
								agentController = workContainer.createNewAgent(
										corpusAgentName,
										CorpusStreamingAgent.class.getName(),
										corpusAgentArgs);
								agentController.start();
								listOfAgents.add(corpusAgentName);
								listOfAgentControllers.add(agentController);
								totalUsers = 1;
								System.out.println(
										"Made bounded corpus agent for "
										+ corpusStream.size() + " non-target rows.");
							}
						}
		
						int smallestBinIndex = 0;
		
						if (!boundedTextAgentProvisioning)
						{
							totalUsers = 0;
						}
						ArrayList<Integer> recServers;
						
						long userAgentStartTime = System.currentTimeMillis();
						for (String user : listOfUsers)
						{
							if (boundedTextAgentProvisioning
									&& !usersRec.contains(user))
							{
								continue;
							}
							totalUsers++;
	
						UserNodeAssignment textNodeAssignment = textNodeAssignments.get(user);
						if (textNodeAssignment != null)
						{
							recServers = new ArrayList<Integer>(textNodeAssignment.recServers);
							smallestBinIndex = textNodeAssignment.primaryRecServer;
						}
						else
						{
							recServers = new ArrayList<Integer>();
							smallestBinIndex = 1;
							recServers.add(smallestBinIndex);
						}
						
						System.out.println("Controller recServers: "+user+" "+recServers+" smallestBinIndex: "+smallestBinIndex);
					
					long userTweetDelay = getConfiguredTweetDelay(user);

					
//					userFollowTweetCounts = new LinkedHashMap<String,Integer>();
					LinkedHashMap<String,Integer> userFollowTweetCounts = new LinkedHashMap<String,Integer>();
					
						if (user.equals(referenceUser))
						{
							for (String otherUser : listOfUsers)
							{
								if (!otherUser.equals(user))
								{
									int tweetCountToFollow;
									tweetCountToFollow = calculateTweetsToFollow(availableDb.getTweetCountFromUser(otherUser));
									userFollowTweetCounts.put(otherUser, tweetCountToFollow);
								}
							}
						}

					allUserFollowTweetCounts.put(user, userFollowTweetCounts);
//					System.out.println("user: "+user+" userFollowTweetCounts: " + userFollowTweetCounts);
					
					//Object[] userAgentArgs = new Object[15];							
					Object[] userAgentArgs = new Object[31];
					userAgentArgs[0] = getAID();										
					userAgentArgs[1] = referenceUser;							

					userAgentArgs[3] = beginDate;
					userAgentArgs[4] = endDate;
					userAgentArgs[5] = hashtags_temp;

					userAgentArgs[7] = retweetedby_temp;
					userAgentArgs[8] = stopWordFlag_temp;
					userAgentArgs[9] = recommendationConfig;

					userAgentArgs[10] = recServers;

					userAgentArgs[11] = smallestBinIndex;				

					userAgentArgs[12] = 1;
					userAgentArgs[13] = 1;	
					//					userAgentArgs[14] = tweetDelay;
					userAgentArgs[14] = userTweetDelay;

					//@Jason added limit and referenceUser;
					userAgentArgs[15] = totalTweetLimit;
					userAgentArgs[16] = referenceUser;
					userAgentArgs[17] = kRecommend;
					userAgentArgs[18] = algorithmRec;
					userAgentArgs[19] = myGui;
					userAgentArgs[20] = readFrom;
					userAgentArgs[21] = availableDb.getTweetsFromUser(user);
					userAgentArgs[22] = true;
					userAgentArgs[23] = 1;
					userAgentArgs[24] = referenceUser;
					
					if (user.equals(referenceUser))
					{
						userAgentArgs[25] = true;
					}
					else
					{	
						userAgentArgs[25] = false;
					}
					userAgentArgs[29] = userFollowee.get(user); //followee of User
					userAgentArgs[30] = recommendationDatasetContext;
					
					String userAgentName = user + "-UserAgent";
					agentController = workContainer.createNewAgent(userAgentName, MobileAgent.class.getName(), userAgentArgs);   
					agentController.start();
					listOfAgents.add(userAgentName);
					listOfAgentControllers.add(agentController);
					//System.out.println("Made agent: "+userAgentName);
//					System.out.println("totalUsers: "+totalUsers);

					}
					if (totalUsers != expectedTextParticipants)
					{
						failInitialization(
								"Internal large-dataset provisioning mismatch: expected "
								+ expectedTextParticipants + " live text participant(s) but created "
								+ totalUsers + ".");
						killContainer();
						return;
					}
					logStageDuration("INITIALIZE assign bins and create user agents", userAgentStartTime, System.currentTimeMillis());

				int totalnumberoftweets =0;
//				for(int k=0; k<bins.size();k++)
					for(int k=0; k<bins.length;k++)
					{
						//totalnumberoftweets += bins.get(k);
	//					System.out.println("bin "+k+": "+bins.get(k));
						System.out.println("bin "+k+": "+bins[k]);
						System.out.println("balance bin "+k+": "+balanceBins[k]);
					}

				//Only update if it is not distributed SVM
				if (!RecommendationModeSupport.isSvmFamilyAlgorithm(algorithmRec)
						|| numRecAgents == 1)
					myGui.updateList(listOfUsers); //update before creating recommender agents
				
				long recommenderAgentStartTime = System.currentTimeMillis();
				int j =0;
				for(int i=1; i<numRecAgents+1; i++)
				{

					Object[] recAgentArgs = new Object[28];
					recAgentArgs[0] = getAID();										
					recAgentArgs[1] = referenceUser;								

					recAgentArgs[3] = beginDate;
					recAgentArgs[4] = endDate;
					recAgentArgs[5] = hashtags_temp;

					recAgentArgs[7] = retweetedby_temp;
					recAgentArgs[8] = stopWordFlag_temp;



					recAgentArgs[11] = 1;
					recAgentArgs[12] = 1;
//					recAgentArgs[13] = bins.get(j);
					recAgentArgs[13] = bins[j];

					recAgentArgs[15] = numRecAgents;

					//@Jason added argument for algorithm to use
					recAgentArgs[16] = algorithmRec;
					recAgentArgs[17] = recommendationConfig;
					recAgentArgs[18] = myGui;
					
					if (RecommendationModeSupport.isSvmFamilyAlgorithm(algorithmRec)
							&& numRecAgents > 1)
					{
						recAgentArgs[19] = trainedSVM;
						
					}
					
					// if (algorithmRec == MLP && numRecAgents > 1)
					if (algorithmRec == MLP)
					{
						recAgentArgs[20] = allUniqueDocTerms;
						recAgentArgs[22] = trainSetUsers;
						recAgentArgs[23] = testSetUsers;
					}
					recAgentArgs[21] = datasetFollowees;
					recAgentArgs[24] = new LinkedHashMap<String,String>(userFollowee);
					if (needsGlobalClassifierEvidence)
					{
						recAgentArgs[25] = classifierEvidenceByNode.get(i);
					}
					recAgentArgs[26] = Boolean.valueOf(exactSvmDiskBackedWaves);
					recAgentArgs[27] = Long.valueOf(exactSvmNodeTermEntries[j]);
					
					String recAgentName = "Recommender-ServiceAgent" + i;
					agentController = workContainer.createNewAgent(recAgentName, RecommenderAgent.class.getName(), recAgentArgs);
					agentController.start();
					listOfAgents.add(recAgentName);
					listOfAgentControllers.add(agentController);
					System.out.println("Made agent: "+recAgentName);
					j++;
				}
				logStageDuration("INITIALIZE create recommender agents", recommenderAgentStartTime, System.currentTimeMillis());
				logStageDuration("INITIALIZE total text agent creation", textAgentCreationStartTime, System.currentTimeMillis());
				localDb.clearDb();
				availableDb.clearDb();
				availableTweets.clear();

				/*//@Jason added algorithmRec arg
				//Object[] orgAgentArgs = new Object[16];
				Object[] orgAgentArgs = new Object[19];
				orgAgentArgs[0] = getAID();										
				orgAgentArgs[1] = referenceUser;								

				orgAgentArgs[3] = beginDate;
				orgAgentArgs[4] = endDate;
				orgAgentArgs[5] = hashtags_temp;

				orgAgentArgs[7] = retweetedby_temp;
				orgAgentArgs[8] = stopWordFlag_temp;



				orgAgentArgs[11] = 1;
				orgAgentArgs[12] = 1;
				orgAgentArgs[13] = totalUsers;
				orgAgentArgs[14] = 1;		
				orgAgentArgs[15] = numRecAgents;

				//@Jason added algorithmRec argument
				orgAgentArgs[16] = algorithmRec;
				orgAgentArgs[17] = recommendationConfig;

				orgAgentArgs[18] = myGui;

				for(int i=1; i<numOrgAgents+1; i++)
				{
					String orgAgentName = "Organizing Agent" + i;
					agentController = workContainer.createNewAgent(orgAgentName, OrganizingAgent.class.getName(), orgAgentArgs);
					agentController.start();
					listOfAgents.add(orgAgentName);
					listOfAgentControllers.add(agentController);
					System.out.println("Made agent: "+orgAgentName);

				}*/
			}catch (Exception e){
				System.err.println("Text Read Initialize Error: " + e.getMessage());
			}  

			System.out.println("FROM TEXTFILE TotalUsers: "+totalUsers);
			System.out.println("allUserFollowTweetCounts: "+allUserFollowTweetCounts);
			logStageDuration("INITIALIZE text-file branch total", textInitializeStartTime, System.currentTimeMillis());

		}


		if (readFrom == FROM_DB)
		{	

			listOfUsers.clear();

			try{


				try {

					String url = "jdbc:mysql://" + serverName + ":" + portNumber + "/" + sid + "?useSSL=false";
					con = DriverManager.getConnection(url, user, pass);


					//@Jason Change the top* tables queryst

					String query;

					if (totalTweetLimit > 0)
						query = "select screen_name,referenceUser,count(*) from (select * from usertweet where referenceUser='"+referenceUser+"' AND CAST(created_at AS DATE) BETWEEN '" + beginDate + "' AND '" + endDate + "' order by tweetid DESC LIMIT "+ totalTweetLimit +") as T1 group by screen_name order by count(*) DESC";				  
					else
						query = "select screen_name,referenceUser,count(*) from (select * from usertweet where referenceUser='"+referenceUser+"' AND CAST(created_at AS DATE) BETWEEN '" + beginDate + "' AND '" + endDate + "') as T1 group by screen_name order by count(*) DESC";
					stmt = con.createStatement();
					resultSet = stmt.executeQuery(query);


				} catch (SQLException e1) {
					e1.printStackTrace();
				}

				resultSet.beforeFirst();
				while(!resultSet.isLast())
				{
					resultSet.next();
					currentTwitterUser = resultSet.getString(1);
					listOfUsers.add(currentTwitterUser);
				}

				int[] bins = new int[numRecAgents]; //bins for recommender agents

				//adds referenceUser tweets to first recommender agent
				//bins.set(0, referenceUserCount);    
				//totalUsers++;

				int currentTweetCount;
				int totalRecBin = 0;
				int smallestBinIndex;

				resultSet.beforeFirst();
				while(!resultSet.isLast())
				{

					resultSet.next();
					currentTwitterUser = resultSet.getString(1);
					//if(!currentTwitterUser.equalsIgnoreCase(referenceUser))			
					//{
					totalUsers++;		

					currentTweetCount  = resultSet.getInt(3);

					smallestBinIndex = selectCapacityAwareBin(bins);
					/*System.out.println("smallestBinIndex: "+smallestBinIndex);
					  		for (int i = 0; i < bins.length; i++){
					  			System.out.println("bin "+i+": "+bins[i]);
					  		}*/

					totalRecBin = bins[smallestBinIndex];
					totalRecBin += currentTweetCount;
					bins[smallestBinIndex] = totalRecBin;
					
					smallestBinIndex++;
					
					//Add list of recommender servers and increase bins accordingly if distributed
					//ie. add the tweet count to all recommenders if there are 2 for the user looking for recommendation
					ArrayList<Integer> recServers = new ArrayList<Integer>();
					if (usersRec.contains(user))
					{	
						for (int i = 0; i < numRecAgents; i++)
						{
							if (smallestBinIndex-1 != i)
							{
//								totalRecBin = bins.get(i);
								totalRecBin = bins[i];
								totalRecBin += currentTweetCount;
//								bins.set(i,totalRecBin);
								bins[i] = totalRecBin;
							}
							recServers.add(i+1);
						}
					}
					else
					{
						recServers.add(smallestBinIndex);
					}
					
					//Object[] userAgentArgs = new Object[15];							
					Object[] userAgentArgs = new Object[31];
					userAgentArgs[0] = getAID();										
					userAgentArgs[1] = referenceUser;							

					userAgentArgs[3] = beginDate;
					userAgentArgs[4] = endDate;
					userAgentArgs[5] = hashtags_temp;

					userAgentArgs[7] = retweetedby_temp;
					userAgentArgs[8] = stopWordFlag_temp;
					userAgentArgs[9] = recommendationConfig;

					userAgentArgs[10] = recServers;

					userAgentArgs[11] = smallestBinIndex;				

					userAgentArgs[12] = 1;
					userAgentArgs[13] = 1;	
					userAgentArgs[14] = getConfiguredTweetDelay(tweetDelay);

					//@Jason added limit and referenceUser;
					userAgentArgs[15]=totalTweetLimit;
					userAgentArgs[16]=referenceUser;
					userAgentArgs[17] = kRecommend;
					userAgentArgs[18] = algorithmRec;
					userAgentArgs[19] = myGui;
					userAgentArgs[20] = readFrom;
					userAgentArgs[29] = userFollowee.get(user); //followee of User
					userAgentArgs[30] = recommendationDatasetContext;


					String userAgentName = currentTwitterUser + "-UserAgent";
					agentController = workContainer.createNewAgent(userAgentName, MobileAgent.class.getName(), userAgentArgs);   
					agentController.start();
					listOfAgents.add(userAgentName);
					listOfAgentControllers.add(agentController);
					System.out.println("Made agent: "+userAgentName);
//					System.out.println("totalUsers: "+totalUsers);

					//++totalUsers;		
				}
				// }
				resultSet.close();
				stmt.close();
				con.close();


				int totalnumberoftweets =0;
//				for(int k=0; k<bins.size();k++)
				for(int k=0; k<bins.length;k++)
				{
					//totalnumberoftweets += bins.get(k);
//					System.out.println("bin "+k+": "+bins.get(k));
					System.out.println("bin "+k+": "+bins[k]);
				}

				myGui.updateList(listOfUsers); //update before creating recommender agents

				int j =0;
				for(int i=1; i<numRecAgents+1; i++)
				{

					Object[] recAgentArgs = new Object[19];
					recAgentArgs[0] = getAID();										
					recAgentArgs[1] = referenceUser;								

					recAgentArgs[3] = beginDate;
					recAgentArgs[4] = endDate;
					recAgentArgs[5] = hashtags_temp;

					recAgentArgs[7] = retweetedby_temp;
					recAgentArgs[8] = stopWordFlag_temp;



					recAgentArgs[11] = 1;
					recAgentArgs[12] = 1;
//					recAgentArgs[13] = bins.get(j);
					recAgentArgs[13] = bins[j];

					recAgentArgs[15] = numRecAgents;

					//@Jason added argument for algorithm to use
					recAgentArgs[16] = algorithmRec;
					recAgentArgs[17] = recommendationConfig;
					recAgentArgs[18] = myGui;

					String recAgentName = "Recommender-ServiceAgent" + i;
					agentController = workContainer.createNewAgent(recAgentName, RecommenderAgent.class.getName(), recAgentArgs);
					agentController.start();
					listOfAgents.add(recAgentName);
					listOfAgentControllers.add(agentController);
					System.out.println("Made agent: "+recAgentName);
					j++;
				}

				//@Jason added algorithmRec arg
				//Object[] orgAgentArgs = new Object[16];
				Object[] orgAgentArgs = new Object[19];
				orgAgentArgs[0] = getAID();										
				orgAgentArgs[1] = referenceUser;								

				orgAgentArgs[3] = beginDate;
				orgAgentArgs[4] = endDate;
				orgAgentArgs[5] = hashtags_temp;

				orgAgentArgs[7] = retweetedby_temp;
				orgAgentArgs[8] = stopWordFlag_temp;

 

				orgAgentArgs[11] = 1;
				orgAgentArgs[12] = 1;
				orgAgentArgs[13] = totalUsers;
				orgAgentArgs[14] = 1;		
				orgAgentArgs[15] = numRecAgents;

				//@Jason added algorithmRec argument
				orgAgentArgs[16] = algorithmRec;
				orgAgentArgs[17] = recommendationConfig;

				orgAgentArgs[18] = myGui;

				for(int i=1; i<numOrgAgents+1; i++)
				{
					String orgAgentName = "Organizing Agent" + i;
					agentController = workContainer.createNewAgent(orgAgentName, OrganizingAgent.class.getName(), orgAgentArgs);
					agentController.start();
					listOfAgents.add(orgAgentName);
					listOfAgentControllers.add(agentController);
					System.out.println("Made agent: "+orgAgentName);

				}
			}catch (Exception e){
				System.err.println("Error: " + e.getMessage());
			}


		}   //From_DB

		// myGui.updateList(listOfUsers); //update for db or text
		// myGui.enableAllButtons();
		// myGui.reselectRecommendee();
		// myGui.enableList();
		long endTime = System.currentTimeMillis();
		System.out.println("Initialize time: "+(endTime-startTime)+"ms");
		logStageDuration("INITIALIZE total", startTime, endTime);
		// myGui.showMessageBox("initialize");

	} //inititalize

	public void killContainer()
	{
		for (AgentController ac : new ArrayList<AgentController>(listOfAgentControllers))
		{
			try
			{
				ac.kill();
			}
			catch (StaleProxyException e)
			{
				System.err.println("Could not stop an old experiment agent during re-initialization: " + e.getMessage());
			}
		}
		stopContainer(workContainer, "workContainer");
		workContainer = null;
		agentController = null;

		listOfAgentControllers.clear();
		listOfAgents.clear();
		totalUsers=0;
		usersRec.clear();

		System.out.println("Killed container");

	}

	public void readInGeneratingParameters() throws FileNotFoundException
	{
		//Read in the parameters for each topic's weibull distribution averaged
		Scanner sc = new Scanner(new File("topic_parameters_tfidf_avg.txt")).useDelimiter("[,\n]");
		for (int i = 0; i < topicsParameters.length; i++) {
			for (int j = 0; j < topicsParameters[i].length; j++) {
				if (sc.hasNext()) {
					topicsParameters[i][j] = Double.parseDouble(sc.next());
				}
			}
		}
		sc.close();
		
		// Read in the tf-idf of each word for each topic
		Scanner sc2 = new Scanner(new File("topic_word_tfidf.txt")).useDelimiter("[,\n]");
		for (int i = 0; i < topicsWordTfidf.length; i++) {
			for (int j = 0; j < topicsWordTfidf[i].length; j++) {
				if (sc2.hasNext()) {
					String s = sc2.next();
					topicsWordTfidf[i][j] = Double.parseDouble(sc2.next());
				}
			}
		}
		sc2.close();
		
		// Read in the list of words sorted according to tfidf in
		// ascending order
		Scanner sc3 = new Scanner(new File("topic_words_tfidf.txt"));
		for (int i = 0; i < topicsWords.length; i++) {
			for (int j = 0; j < topicsWords[i].length; j++) {
				if (sc3.hasNext()) {
					topicsWords[i][j] = sc3.next();
//							System.out.println(topicsWords[i][j]);
				}
			}
		}
		sc3.close();
		
		// Read in which user belongs to which topic and how many tweets to
		// generate for each user and min, max values
		Scanner sc4 = new Scanner(new File("topic_generate_user_parameters_tfidf.txt"));
		for (int i = 0; i < TOTAL_ORIGINAL_USERS; i++) {
			sc4.next();
			sc4.nextInt();
			sc4.nextInt();
			topicsMinMax[i][0] = sc4.nextDouble();
			topicsMinMax[i][1] = sc4.nextDouble();
		}
		sc4.close();
	}
	
	public void readInGeneratingParameters2() throws FileNotFoundException
	{
		// Read in the tf-idf of each word for each topic
		Scanner sc = new Scanner(new File("words_100k.txt")).useDelimiter("[\t\n]");
		for (int i = 0; i < TOTAL_ORIGINAL_WORDS_DEMO; i++) {
			String s = sc.next();
			topicsWordsDemo[i] = s;
			Double d = Double.parseDouble(sc.next());
			topicsWordsTfidfDemo[i] = d;
//			System.out.println(s+" "+d);
		}
		sc.close();
	}
	
	// Sets the bins for generating random indices for words
	public void setGeneratedWordBins()
	{
		for (int i = 0; i < totalUsers; i++) {
			
			double dist = (topicsMinMax[i][1] - topicsMinMax[i][0]) / TOTAL_ORIGINAL_WORDS;
			double currentValue = topicsMinMax[i][0];

			for (int j = 0; j < TOTAL_ORIGINAL_WORDS; j++) {
				topicsWordsIndex[i][j] = currentValue;
				currentValue += dist;
//				System.out.println(currentValue);
			}
		}
	}

	// Sets the bins for generating random indices for words
	public void setGeneratedWordBins2()
	{
		for (int i = 0; i < totalUsers; i++) {
			
			double dist = (1.55907e-5 - 4.91893e-6) / TOTAL_ORIGINAL_WORDS_DEMO;
			double currentValue = 4.91893e-6;

			for (int j = 0; j < TOTAL_ORIGINAL_WORDS; j++) {
				topicsWordsIndexDemo[i] = currentValue;
				currentValue += dist;
//				System.out.println(currentValue);
			}
		}
	}
	
//	public double weibull2(double shape, double scale) {
////		RandomDataGenerator r = new RandomDataGenerator();
//		double i;
//		i = randomDataGenerator.nextWeibull(shape, scale);
//		return i;
//	}

	//Finds the smallest bin to fill in when using distributed system
//	public int findSmallestBin(ArrayList<Integer> numbers) { 
		public int findSmallestBin(int[] currentBins) {
			int smallestIndex = 0;
	//		int smallest = numbers.get(0);
			int smallest = currentBins[0];
//		for(int i = 0; i < numbers.size(); i++) { 
//			if(numbers.get(i) < smallest) { 
//				smallest = numbers.get(i); 
//				smallestIndex = i;
//			} 
//		}
		for(int i = 0; i < currentBins.length; i++) { 
			if(currentBins[i] < smallest) { 
				smallest = currentBins[i]; 
				smallestIndex = i;
			} 
			} 
			return smallestIndex;
		}

		private int selectCapacityAwareBin(int[] currentBins)
		{
			return NodeCapacityScheduler.chooseNodeByCapacity(
					currentBins,
					numRecAgents);
		}

			private int findSmallestBin(long[] currentBins) {
				int smallestIndex = 0;
				long smallest = currentBins[0];
			for(int i = 0; i < currentBins.length; i++) { 
				if(currentBins[i] < smallest) { 
					smallest = currentBins[i]; 
					smallestIndex = i;
				} 
			} 
				return smallestIndex;
			}

			private int selectCapacityAwareBin(long[] currentBins)
			{
				return NodeCapacityScheduler.chooseNodeByCapacity(
						currentBins,
						numRecAgents);
			}

			static boolean shouldUseBoundedTextAgentProvisioning(int activeUserCount)
			{
				return activeUserCount >= MAX_DIRECT_TEXT_USER_AGENTS;
			}

				static boolean supportsBoundedCorpusStreaming(int algorithm)
				{
					return RecommendationModeSupport.isSvmFamilyAlgorithm(algorithm)
							|| algorithm == COS_SIM
							|| algorithm == Doc2Vec
							|| algorithm == CommonNeighbors;
				}

			static int calculateTextParticipantCount(
					List<String> activeUsers,
					List<String> targetUsers,
					boolean boundedProvisioning)
			{
				int activeUserCount = activeUsers == null ? 0 : activeUsers.size();
				if (!boundedProvisioning)
				{
					return activeUserCount;
				}

				int activeTargetCount =
						countActiveTargets(activeUsers, targetUsers);
				boolean hasNonTargetUsers = activeUserCount > activeTargetCount;
				return activeTargetCount + (hasNonTargetUsers ? 1 : 0);
			}

			private static int countActiveTargets(
					List<String> activeUsers,
					List<String> targetUsers)
			{
				if (activeUsers == null || targetUsers == null)
				{
					return 0;
				}
				LinkedHashSet<String> activeUserSet =
						new LinkedHashSet<String>(activeUsers);
				LinkedHashSet<String> activeTargets =
						new LinkedHashSet<String>();
				for (String target : targetUsers)
				{
					if (activeUserSet.contains(target))
					{
						activeTargets.add(target);
					}
				}
				return activeTargets.size();
			}

			private Map<String,UserNodeAssignment> buildBalancedTextNodeAssignments(int[] tweetBins, long[] balanceBins)
		{
			LinkedHashMap<String,UserNodeAssignment> assignments = new LinkedHashMap<String,UserNodeAssignment>();
			if (numRecAgents < 1)
			{
				return assignments;
			}

			final LinkedHashMap<String,Integer> originalUserOrder = new LinkedHashMap<String,Integer>();
			for (int i = 0; i < listOfUsers.size(); i++)
			{
				originalUserOrder.put(listOfUsers.get(i), i);
			}
			
			final LinkedHashMap<String,Long> userAssignmentLoads = new LinkedHashMap<String,Long>();
			for (String user : listOfUsers)
			{
				userAssignmentLoads.put(user, estimateTextUserAssignmentLoad(user));
			}

			ArrayList<String> usersSortedByEstimatedLoad = new ArrayList<String>(listOfUsers);
			Collections.sort(usersSortedByEstimatedLoad, new Comparator<String>() {
				public int compare(String userA, String userB)
				{
					long userALoad = userAssignmentLoads.get(userA);
					long userBLoad = userAssignmentLoads.get(userB);
					if (userALoad < userBLoad)
						return 1;
					if (userALoad > userBLoad)
						return -1;

					Integer userAOrder = originalUserOrder.get(userA);
					Integer userBOrder = originalUserOrder.get(userB);
					if (userAOrder == null)
						userAOrder = 0;
					if (userBOrder == null)
						userBOrder = 0;
					return userAOrder.compareTo(userBOrder);
				}
			});

			for (String user : usersSortedByEstimatedLoad)
			{
				int currentTweetCount = availableDb.getTweetCountFromUser(user);
				long userAssignmentLoad = userAssignmentLoads.get(user);
				int selectedBinIndex = selectCapacityAwareBin(balanceBins);
				ArrayList<Integer> recServers;

				addUserLoadToTextBin(tweetBins, balanceBins, selectedBinIndex, currentTweetCount, userAssignmentLoad);
				if (shouldReplicateTextUserToAllRecommenders(user))
				{
					recServers = createAllRecommenderServerList();
					for (int i = 0; i < numRecAgents; i++)
					{
						if (i != selectedBinIndex)
						{
							addUserLoadToTextBin(tweetBins, balanceBins, i, currentTweetCount, userAssignmentLoad);
						}
					}
				}
				else
				{
					recServers = new ArrayList<Integer>();
					recServers.add(selectedBinIndex + 1);
				}

				assignments.put(user, new UserNodeAssignment(recServers, selectedBinIndex + 1));
			}

			System.out.println("Controller balanced text assignment loads: "+Arrays.toString(balanceBins));
			System.out.println("Controller balanced text assignment tweet bins: "+Arrays.toString(tweetBins));
			return assignments;
		}

		private boolean isSvmBatchMode()
		{
			return RecommendationModeSupport.isSvmFamilyAlgorithm(algorithmRec)
					&& myGui != null && myGui.getSvmBatchUserCount() > 1;
		}

		private void addUserLoadToTextBin(int[] tweetBins, long[] balanceBins, int binIndex, int tweetCount, long assignmentLoad)
		{
			tweetBins[binIndex] += tweetCount;
			balanceBins[binIndex] += assignmentLoad;
		}

		private boolean shouldReplicateTextUserToAllRecommenders(String user)
		{
			if (usersRec.contains(user))
			{
				return true;
			}

			if (numRecAgents <= 1)
			{
				return false;
			}

			return (algorithmRec == K_MEANS || algorithmRec == MLP
					|| algorithmRec == K_MEANSEUCLIDEAN
					|| algorithmRec == Doc2Vec
					|| RecommendationModeSupport.isSvmFamilyAlgorithm(algorithmRec))
				&& datasetFollowees != null
				&& datasetFollowees.contains(user);
		}

		private ArrayList<Integer> createAllRecommenderServerList()
		{
			ArrayList<Integer> recServers = new ArrayList<Integer>();
			for (int i = 0; i < numRecAgents; i++)
			{
				recServers.add(i + 1);
			}
			return recServers;
		}

			private long estimateTextUserAssignmentLoad(String user)
			{
				return estimateTextUserLoad(user).assignmentLoad;
			}

			private long[] estimateExactSvmRetainedSparseTermEntriesByNode(
					Map<String,UserNodeAssignment> assignments)
			{
				long[] nodeEntries = new long[Math.max(1, numRecAgents)];
				for (String user : listOfUsers)
				{
					long userTerms = estimateTextUserLoad(user).approximateTermCount;
					UserNodeAssignment assignment = assignments.get(user);
					if (assignment == null || assignment.recServers == null)
					{
						continue;
					}
					for (Integer server : assignment.recServers)
					{
						if (server == null
								|| server.intValue() < 1
								|| server.intValue() > nodeEntries.length)
						{
							continue;
						}
						int nodeIndex = server.intValue() - 1;
						nodeEntries[nodeIndex] = saturatingAdd(
								nodeEntries[nodeIndex], userTerms);
					}
				}
				return nodeEntries;
			}

			private TextUserLoadEstimate estimateTextUserLoad(String user)
			{
				TextUserLoadEstimate cached = textUserLoadEstimates.get(user);
				if (cached != null)
				{
					return cached;
				}

				int currentTweetCount = availableDb.getTweetCountFromUser(user);
				long fallbackLoad = Math.max(1, currentTweetCount);
				List<Tweet> userTweets = availableDb.getTweetsViewFromUser(user);
				if (userTweets == null || userTweets.size() == 0)
				{
					TextUserLoadEstimate empty =
							new TextUserLoadEstimate(fallbackLoad, 0L);
					textUserLoadEstimates.put(user, empty);
					return empty;
				}

				HashSet<String> approximateTerms = new HashSet<String>();
				long characterCount = 0L;
				for (Tweet tweet : userTweets)
				{
					String tweetText = tweet.getTweetText();
					if (tweetText == null)
					{
						continue;
					}
					characterCount = saturatingAdd(
							characterCount, tweetText.length());
					addApproximateTerms(approximateTerms, tweetText);
				}

				long termCount = Math.max(1, approximateTerms.size());
				long denseDocumentLoad = saturatingMultiply(termCount, termCount);
				long textLengthLoad = characterCount / 8L;
				long tweetLoad = saturatingMultiply(
						Math.max(1, userTweets.size()), 10L);
				long assignmentLoad = Math.max(
						fallbackLoad,
						saturatingAdd(
								denseDocumentLoad,
								saturatingAdd(textLengthLoad, tweetLoad)));
				TextUserLoadEstimate estimate =
						new TextUserLoadEstimate(assignmentLoad, termCount);
				textUserLoadEstimates.put(user, estimate);
				return estimate;
			}

			private static long saturatingAdd(long first, long second)
			{
				return first > Long.MAX_VALUE - second
						? Long.MAX_VALUE : first + second;
			}

			private static long saturatingMultiply(long first, long second)
			{
				if (first <= 0L || second <= 0L)
				{
					return 0L;
				}
				return first > Long.MAX_VALUE / second
						? Long.MAX_VALUE : first * second;
			}

		private void addApproximateTerms(Set<String> approximateTerms, String tweetText)
		{
			int tokenStart = -1;
			for (int index = 0; index <= tweetText.length(); index++)
			{
				boolean isAsciiLetter = false;
				if (index < tweetText.length())
				{
					char character = tweetText.charAt(index);
					isAsciiLetter = (character >= 'a' && character <= 'z')
							|| (character >= 'A' && character <= 'Z');
				}

				if (isAsciiLetter)
				{
					if (tokenStart < 0)
					{
						tokenStart = index;
					}
				}
				else if (tokenStart >= 0)
				{
					approximateTerms.add(
							tweetText.substring(tokenStart, index)
									.toLowerCase(Locale.ENGLISH));
					tokenStart = -1;
				}
			}
		}

			private static class UserNodeAssignment
			{
			private final ArrayList<Integer> recServers;
			private final int primaryRecServer;

				private UserNodeAssignment(ArrayList<Integer> recServers, int primaryRecServer)
				{
					this.recServers = recServers;
					this.primaryRecServer = primaryRecServer;
				}
			}

			private static class TextUserLoadEstimate
			{
				private final long assignmentLoad;
				private final long approximateTermCount;

				private TextUserLoadEstimate(
						long assignmentLoad,
						long approximateTermCount)
				{
					this.assignmentLoad = assignmentLoad;
					this.approximateTermCount = approximateTermCount;
				}
			}
			
			public void generateUserNames()
		{
		int totalUsers = numFollowees + numFollowers;
		listOfUsers.clear(); //Clear list of usernames if it already exists
		String username = "";
		for (int i = 0; i < totalUsers; i++)
		{
			username = "User"+(i+1);
			if (i < numFollowees)
			{
				username += "_Followee";
			}
			else
			{
				username += "_Follower";
			}
			
			listOfUsers.add(username);
		}
	}
	
	public String generateTweetText()
	{
	
		String generatedTweetText = "";
		double averageShape = 0.174651; //Average shape parameter from original corpus
		double averageScale = 5.50E-05; //Average scale parameter from original corpus
				

				
//		Random r = new Random();
		int wordsInTweet = r.nextInt((MAX_WORDS - MIN_WORDS) + 1) + MIN_WORDS;
		int userTopic = r.nextInt(TOTAL_ORIGINAL_USERS);
		
//		System.out.println(getLocalName()+" userTopic: "+userTopic);
		
		for (int j = 0; j < wordsInTweet; j++) {

//			double x = weibull2(averageShape, averageScale);
////			System.out.println(x);
//			while (x < topicsMinMax[userTopic][0] || x > topicsMinMax[userTopic][1])
//			{
//				x = weibull2(averageShape,averageScale);
////				System.out.println("new x: "+x);
//			}
////			System.out.println(x + " min: " + topicsMinMax[userTopic][0] + " max: " + topicsMinMax[userTopic][1]);
			String word = "";
//			int indexOfWord = 0;
//			
//			while (x > topicsWordsIndex[userTopic][indexOfWord] && indexOfWord < TOTAL_ORIGINAL_WORDS-1)
//			{
//				indexOfWord++;
//			}
//			word = topicsWords[userTopic][indexOfWord];
			
			word = generateWord(averageShape, averageScale, userTopic);
			System.out.println("word: "+word);
			//Checks if generated word is already used
			if (currentBagWords.size() < TOTAL_ORIGINAL_WORDS)
			{
				//Generate word until it is a word that has not been used
				while(currentBagWords.contains(word))
				{
					word = generateWord(averageShape, averageScale, userTopic);
					System.out.println("word: "+word);
				}
			}
			//Clear bag of words if all words are in the bag
			else
			{
				currentBagWords.clear();
			}
			
			//Add newly generated word to bag of words
			currentBagWords.add(word);
								
//			System.out.println("bin = "+indexOfWord);
				
			generatedTweetText += word + " ";
		}
		
//		System.out.println("generatedTweet: "+ generatedTweetText);
		
		return generatedTweetText;
	}

	//Testing for Demo
	public String generateTweetText2()
	{
	
		String generatedTweetText = "";
		double averageShape = DEFAULT_SHAPE2; //Average shape parameter from original corpus
		double averageScale = DEFAULT_SCALE2; //Average scale parameter from original corpus
				

				

		int wordsInTweet = r.nextInt((MAX_WORDS - MIN_WORDS) + 1) + MIN_WORDS;		
		
		for (int j = 0; j < wordsInTweet; j++) {

			String word = "";

			
			word = generateWord2(averageShape, averageScale);
			System.out.println("word: "+word);
			//Checks if generated word is already used
			if (currentBagWords.size() < TOTAL_ORIGINAL_WORDS_DEMO)
			{
				//Generate word until it is a word that has not been used
				//while(currentBagWords.contains(word))
				//{
					word = generateWord2(averageShape, averageScale);
					System.out.println("word: "+word);
				//}
			}
			//Clear bag of words if all words are in the bag
			else
			{
				currentBagWords.clear();
			}
			
			//Add newly generated word to bag of words
			currentBagWords.add(word);
								
//			System.out.println("bin = "+indexOfWord);
				
			generatedTweetText += word + " ";
		}
		
//		System.out.println("generatedTweet: "+ generatedTweetText);
		
		return generatedTweetText;
	}
	
	
	public String generateWord(double averageShape, double averageScale, int userTopic)
	{
		String generatedWord = "";
		
//		double x = weibull2(averageShape, averageScale);
		double x = randomDataGenerator.nextWeibull(averageShape, averageScale);
//		System.out.println(x);
		while (x < topicsMinMax[userTopic][0] || x > topicsMinMax[userTopic][1])
		{
//			x = weibull2(averageShape,averageScale);
			x = randomDataGenerator.nextWeibull(averageShape, averageScale);
			System.out.println("new x: "+x);
		}
//		System.out.println(x + " min: " + topicsMinMax[userTopic][0] + " max: " + topicsMinMax[userTopic][1]);
		
		int indexOfWord = 0;
		
		while (x > topicsWordsIndex[userTopic][indexOfWord] && indexOfWord < TOTAL_ORIGINAL_WORDS-1)
		{
			indexOfWord++;
		}
		
		generatedWord = topicsWords[userTopic][indexOfWord];
		
		
		return generatedWord;
	}
	
	public String generateWord2(double averageShape, double averageScale)
	{
		String generatedWord = "";
		ArrayList<String> bagOfWordsFound = new ArrayList<String>();

		double x = randomDataGenerator.nextWeibull(averageShape, averageScale);
//		System.out.println(x);
//		while (x < topicsMinMax[userTopic][0] || x > topicsMinMax[userTopic][1])
//		{
//			x = weibull2(averageShape,averageScale);
			x = randomDataGenerator.nextWeibull(averageShape, averageScale) + 4.91893e-6;
			System.out.println("new x: "+x);
//		}
//		System.out.println(x + " min: " + topicsMinMax[userTopic][0] + " max: " + topicsMinMax[userTopic][1]);
		
		int indexOfWord = 0;
		
		// for (int i = 0; i < topicsWordsDemo.length; i++)
		// {
			// System.out.print(topicsWordsDemo[i]+" ");
		// }
		// System.out.println();
		
		for (int i = 0; i < TOTAL_ORIGINAL_WORDS_DEMO; i++)
		{
			if (topicsWordsTfidfDemo[i] > x)
			{
				bagOfWordsFound.add(topicsWordsDemo[i]);
			}
		}
		
		indexOfWord = r.nextInt(bagOfWordsFound.size());
		
//		for (int i = 0; i < TOTAL_ORIGINAL_WORDS_DEMO; i++)
//		{
//			if (topicsWordsIndexDemo[i] >= x || x > topicsWordsIndexDemo[TOTAL_ORIGINAL_WORDS-1])
//			{
//				indexOfWord = i;
//				break;
//			}
//		}
		
//		while (x > topicsWordsIndex[userTopic][indexOfWord] && indexOfWord < TOTAL_ORIGINAL_WORDS-1)
//		{
//			indexOfWord++;
//		}
		
		generatedWord = topicsWordsDemo[indexOfWord];
		
		
		return generatedWord;
	}
	
	public long calculateTweetDelay(String username)
	{
		int numUserTweets = availableDb.getTweetCountFromUser(username);
		int totalTweets = availableDb.getTotalTweets();
		double ratioOfTweets = (double) numUserTweets/totalTweets;
		double tweetDelay = TWEET_DELAY - (ratioOfTweets)*TWEET_DELAY;
		long tweetDelayLong;
		//		System.out.println("Total Tweets: "+totalTweets+" "+username+" tweets: "+numUserTweets + " tweetDelay: "+tweetDelay);
		//		System.out.println("Math.ceil(tweetDelay): "+Math.ceil(tweetDelay));

		//return (long) Math.ceil(tweetDelay);
		tweetDelayLong = Math.round(tweetDelay);
		
		if (tweetDelayLong < 1)
			tweetDelayLong = 1;
		
		return tweetDelayLong;
	}

	public long getConfiguredTweetDelay(String username)
	{
		if (myGui != null && !myGui.isSimulateTweetDelayEnabled())
		{
			return 1L;
		}
		return calculateTweetDelay(username);
	}

	public long getConfiguredTweetDelay(long defaultDelay)
	{
		if (myGui != null && !myGui.isSimulateTweetDelayEnabled())
		{
			return 1L;
		}
		return defaultDelay;
	}

	//Returns the index of the followee
	public int calculateWhoToFollow(int numFollowees)
	{
//		Random r = new Random();
		return r.nextInt(numFollowees);
	}

	//Returns how many tweets before user follows a followee
	public int calculateTweetsToFollow(int tweetCount)
	{
		int tweetsToFollow = 0;
//		double value = weibull2(DEFAULT_SHAPE,DEFAULT_SCALE);
		double value = randomDataGenerator.nextWeibull(DEFAULT_SHAPE,DEFAULT_SCALE);
		tweetsToFollow = (int) Math.round(tweetCount * value);
		if (tweetsToFollow > tweetCount)
			tweetsToFollow = tweetCount;
		if (tweetsToFollow < 1)
			tweetsToFollow = 1;
		return tweetsToFollow;
	}

	public boolean checkDateRange(String tweetDate, String beginDate, String endDate)
	{
		if (tweetDate == null || beginDate == null || endDate == null)
		{
			return false;
		}

		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
		boolean dateRangeValid = true;
		Date tweetDateFormat = null;
		Date beginDateFormat = null;
		Date endDateFormat = null;
		try {
			tweetDateFormat = sdf.parse(tweetDate);
			beginDateFormat = sdf.parse(beginDate);
			endDateFormat = sdf.parse(endDate);

			if (beginDateFormat.before(tweetDateFormat) && endDateFormat.after(tweetDateFormat))
				dateRangeValid = true;
			else
				dateRangeValid = false;

		} catch (ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return dateRangeValid;
	}

	//Read text file of tweets and store into InMemoryDb
	public void readFromTextFile()
	{
		readFromTextFile(null, null, null);
	}

	private void readFromTextFile(
			Integer retweetsFlag,
			Integer hashTagsFlag,
			Integer stopWordsFlag)
	{
		try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(
						new FileInputStream(textFile),
						StandardCharsets.UTF_8),
				128 * 1024)) {
			int malformedRows = 0;
			int ambiguousMappings = 0;
			long lineNumber = 0L;
			userFollowee = new LinkedHashMap<String,String>();
				tweetIdText.clear();
				tweetIdUser.clear();
				userFolloweeMap.clear();
				datasetReferenceUsers.clear();
				datasetGraphNodeNames.clear();
				recommendationDatasetContext = RecommendationDatasetContext.empty();
				boolean aggregateControllerDocuments =
					retweetsFlag != null
					&& hashTagsFlag != null
					&& stopWordsFlag != null
					&& requiresControllerTextVectors();
			controllerDocumentsPreparedDuringRead = aggregateControllerDocuments;
			if (aggregateControllerDocuments)
			{
				allUniqueDocTerms.clear();
				allTermsDocumentFreq.clear();
				allUserDocuments.clear();
				followeeFollowerCounts.clear();
				followeeFollowers.clear();
			}

			String line;
			while ((line = reader.readLine()) != null)
			{
				lineNumber++;
				if (line.trim().isEmpty())
				{
					malformedRows++;
					continue;
				}

				String graphLine = lineNumber == 1L && line.startsWith("\uFEFF")
						? line.substring(1) : line;
				String[] graphFields = graphLine.split("\t", 6);
				if (graphFields.length >= 5)
				{
					datasetReferenceUsers.add(graphFields[0]);
					datasetGraphNodeNames.add(graphFields[0]);
					datasetGraphNodeNames.add(graphFields[4]);
				}

				DatasetRow row;
				try
				{
					row = DatasetRow.parse(line, lineNumber);
				}
				catch (IllegalArgumentException e)
				{
					malformedRows++;
					continue;
				}

				referenceUser = row.referenceUser;
				boolean dateRangeValid =
							checkDateRange(row.tweetDate, beginDate, endDate);
				Tweet currentTweet = new Tweet(
						row.text, row.tweetId, row.tweetDate, row.userName);
				tweetsReadCount++;

				if (dateRangeValid)
				{
					localDb.addTweet(currentTweet);
					if (aggregateControllerDocuments)
					{
						addControllerTweetToUserDocument(
								row.userName,
								row.text,
								retweetsFlag.intValue(),
								hashTagsFlag.intValue(),
								stopWordsFlag.intValue());
					}

					String existingFollowee = userFollowee.get(row.userName);
					if (existingFollowee == null)
						userFollowee.put(row.userName, row.referenceUser);
					else if (!existingFollowee.equals(row.referenceUser))
						ambiguousMappings++;

					if (!userFolloweeMap.containsKey(row.userName))
						userFolloweeMap.put(row.userName, row.referenceUser);
				}
			}

			recommendationDatasetContext = new RecommendationDatasetContext(
					userFolloweeMap,
					datasetReferenceUsers,
					datasetGraphNodeNames);

			if (malformedRows > 0)
			{
				String warning = "Skipped " + malformedRows
						+ " malformed dataset row(s); six tab-separated fields are required.";
				System.err.println(warning);
				myGui.appendResult(warning);
			}
			if (ambiguousMappings > 0)
			{
				String warning = "Found " + ambiguousMappings
						+ " conflicting user-to-followee row(s). The first mapping was preserved.";
				System.err.println(warning);
				myGui.appendResult(warning);
			}

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	private void addControllerTweetToUserDocument(
			String username,
			String tweetText,
			int retweetsFlag,
			int hashTagsFlag,
			int stopWordsFlag)
	{
		String currentText = String.format(" %s ", tweetText);
		if (currentText.contains("Photo:"))
			currentText = currentText.substring(0,currentText.indexOf("Photo:"));
		if (currentText.contains("Photoset:"))
			currentText = currentText.substring(0,currentText.indexOf("Photoset:"));

		if (currentText.contains("RT @") && retweetsFlag == RE_TWEETS)
			currentText = "";
		else
			currentText = RETWEET_PATTERN.matcher(currentText).replaceAll("RT ");

		currentText = PUNCTUATION_PATTERN.matcher(currentText).replaceAll("");
		currentText = LINK_PATTERN.matcher(currentText).replaceAll(" ");
		if (hashTagsFlag == HASH_TAGS)
		{
			currentText = SPECIAL_CHARACTER_PATTERN.matcher(currentText).replaceAll(" ");
		}
		if (stopWordsFlag == STOP_WORDS)
		{
			for (String stopWord : stopWordsArray)
			{
				if (currentText.toLowerCase().contains(" "+stopWord+" "))
				{
					currentText = currentText.toLowerCase()
							.replaceAll(" "+stopWord+" "," ");
				}
			}
		}
		currentText = currentText.toLowerCase();
		currentText = currentText.trim();
		currentText = NON_ALPHA_PATTERN.matcher(currentText).replaceAll("");
		currentText = MULTI_SPACE_PATTERN.matcher(currentText).replaceAll(" ");

		String[] tokens = currentText.split("\\s+");
		if (tokens.length < 3)
		{
			return;
		}

		LinkedHashMap<String,Double> userDocument = allUserDocuments.get(username);
		if (userDocument == null)
		{
			userDocument = new LinkedHashMap<String,Double>();
			allUserDocuments.put(username, userDocument);
		}
		for (String token : tokens)
		{
			allUniqueDocTerms.add(token);
			Double frequency = userDocument.get(token);
			userDocument.put(token, frequency == null ? 1.0 : frequency + 1.0);
		}
	}
	
	public ArrayList<Tweet> getAvailableTweets()
	{
		ArrayList<Tweet> tweetsFromDb = localDb.getTweets();
		ArrayList<Tweet> datasetAvailable = new ArrayList<Tweet>();

		Collections.sort(tweetsFromDb); //Sort by tweetId
		localDb.refreshUserIndex();

		int tweetToLimitCount = 0;
		// int currUserTweetCount = 0;
		String prevUserName = "";
		listOfUsers.clear(); //Clear list of users if there is already a list

		System.out.println(getLocalName()+" getAvailableTweets totalTweetLimit: "+totalTweetLimit);
		for (int i = tweetsFromDb.size()-1; i >= 0; i--)
		{
			tweetToLimitCount++;
			//System.out.println(getLocalName()+" tweetToLimitCount: "+tweetToLimitCount+" totalTweetLimit: "+totalTweetLimit);
			String currentUser;
			Tweet currentTweet = tweetsFromDb.get(i);
//			System.out.println(getLocalName()+" "+currentTweet.getTweetId()+" "+currentTweet.getUser()+" "+currentTweet.getTweetText());


			datasetAvailable.add(currentTweet);
			currentUser = currentTweet.getUser();

			// if (!userTweetCounts.containsKey(currentUser))
			// {
				// currUserTweetCount = 1;
				// userTweetCounts.put(currentUser,currUserTweetCount);
			// }
			// else
			// {
				// currUserTweetCount = userTweetCounts.get(currentUser);
				// currUserTweetCount++;
				// userTweetCounts.put(currentUser,currUserTweetCount);
			// }
			
			if (!currentUser.equals(prevUserName))
			{
				if (!listOfUsers.contains(currentUser))
					listOfUsers.add(currentUser);
				prevUserName = currentUser;
			}
			if (tweetToLimitCount == totalTweetLimit)
				break;
		}
		
		return datasetAvailable;
	}
	
	public void findUniqueDocTerms(int retweetsFlag, int hashTagsFlag, int stopWordsFlag)
	{
		
		System.out.println("Controller Finding Unique Doc Terms");
		int tweetIdCount = 0;
		
		for (Long currTweetId : tweetIdText.keySet())
		{
			tweetIdCount++;
			// System.out.println("controller text processing "+tweetIdCount+" tweetid: "+currTweetId);
			
			LinkedHashMap<String,Double> tweetDocumentVector = new LinkedHashMap<String,Double>();
			String currentText = tweetIdText.get(currTweetId);

			//pad out spaces before and after word for parsing 
			currentText = String.format(" %s ",currentText);

			//System.out.println("Original text: "+currentText);


			//Remove Photo: tweets
			if (currentText.contains("Photo:"))
				currentText = currentText.substring(0,currentText.indexOf("Photo:"));

			//Remove Photoset: tweets
			if (currentText.contains("Photoset:"))
				currentText = currentText.substring(0,currentText.indexOf("Photoset:"));

			//Remove all retweets if flagged
			Matcher matcher; //a matcher
			if (currentText.contains("RT @") && retweetsFlag == RE_TWEETS)
				currentText="";
			else
			{
				//Remove RT @, conserve the text from retweets
				Pattern retweet = Pattern.compile("RT @");

				matcher = retweet.matcher(currentText);
				currentText = matcher.replaceAll("RT ");
			}
			//System.out.println("After RT @: " + currentText);

			//Remove punctuations
			Pattern punctuations = Pattern.compile("[\\p{P}]");
			matcher = punctuations.matcher(currentText);
			currentText = matcher.replaceAll("");

			//System.out.println("After punctuations: "+ currentText);

			//Remove url links
			Pattern links = Pattern.compile("http[a-zA-Z0-9]*|bitly[a-zA-Z0-9]*|www[a-zA-Z0-9]*");
			matcher = links.matcher(currentText);
			currentText = matcher.replaceAll(" ");

			//System.out.println("After url links: "+ currentText);

			//Remove special characters including hash tags if flagged
			if (hashTagsFlag == HASH_TAGS)
			{
				Pattern specialCharacters = Pattern.compile("[^a-zA-Z\\p{Z}]");
				matcher = specialCharacters.matcher(currentText);
				currentText = matcher.replaceAll(" ");
			}
			//System.out.println("After special characters: " + currentText);

			//Remove stop words if flagged
			if (stopWordsFlag == STOP_WORDS)
			{
				for (String stopWord : stopWordsArray){	
					if (currentText.toLowerCase().contains(" "+stopWord+" "))
						//System.out.println("FOUND STOPWORD: "+stopWord);
						currentText = currentText.toLowerCase().replaceAll(" "+stopWord+" "," ");
				}
			}
			//Change all text to lowercase
			currentText=currentText.toLowerCase();

			//Trim leading and ending white space
			currentText=currentText.trim();
			//Remove any non-alphabetical characters
			currentText=currentText.replaceAll("[^a-zA-Z ]","");
			//Shorten any spaces to just 1 single space
			currentText=currentText.replaceAll(" +", " ");
			//currentText=currentText.trim().replaceAll(" +", " ");

			//System.out.println(getLocalName()+" Removed junk: "+currentText);

			//******@Begin making vectors*************************************************
			//Add processed texts to a list
			Scanner sc = new Scanner(currentText);
			//List<String> list = new ArrayList<String>();
			String stringToken;
			double wordFreq = 0.0;
			int wordCount = 0;
			wordCount = currentText.split("\\s+").length;

			//System.out.println("currentText: "+ currentText);
			//If processed text is a blank line with 1 single space or less than 3 words
			if (wordCount >= 3)
			{
				while (sc.hasNext()){
					stringToken = sc.next();

					//Add all unique terms to allUniqueDocTerms
					allUniqueDocTerms.add(stringToken);

				}
				/*for (String s : list){
						System.out.print(s+" ");
					}
					System.out.println();*/
				sc.close();
			}


		} //end for (Long currTweetId : tweetIdText.keySet())
	
	}
	
	public List<String> findListFollowees()
	{
		List<String> listFolloweeNames = new ArrayList<String>();
		for (String currUser : userFollowee.keySet())
		{
			if (!listFolloweeNames.contains(userFollowee.get(currUser)))
				listFolloweeNames.add(userFollowee.get(currUser));
		}
		return listFolloweeNames;


	}
	
	public void processTweets(int retweetsFlag, int hashTagsFlag, int stopWordsFlag)
	{
		System.out.println("Controller Processing Tweets");
		allTermsDocumentFreq.clear();
		followeeFollowerCounts.clear();
		followeeFollowers.clear();
		tweetIdDocumentVector.clear();
		if (!controllerDocumentsPreparedDuringRead)
		{
			allUniqueDocTerms.clear();
			allUserDocuments.clear();
			for (Map.Entry<Long,String> tweetEntry : tweetIdText.entrySet())
			{
				String username = tweetIdUser.get(tweetEntry.getKey());
				if (username != null)
				{
					addControllerTweetToUserDocument(
							username,
							tweetEntry.getValue(),
							retweetsFlag,
							hashTagsFlag,
							stopWordsFlag);
				}
			}
		} //end fallback aggregation from per-tweet maps

		// The per-tweet source maps are no longer needed after direct user aggregation.
		tweetIdText.clear();
		tweetIdUser.clear();
		tweetIdDocumentVector.clear();
		controllerDocumentsPreparedDuringRead = false;

		int followerCount = 0;
		String followeeName;
		List<String> followerNames;
		
		//adds only usable users to follower list					
		for (String currUser : allUserDocuments.keySet())
		{
			followeeName = userFollowee.get(currUser);
			// System.out.println("followeeName: "+followeeName);
			if (!followeeFollowers.containsKey(followeeName))
			{
				followerNames = new ArrayList<String>();
				// System.out.println("Entered first time: "+followeeName);
			}
			else
			{
				followerNames = followeeFollowers.get(followeeName);
				// System.out.println("Entered NOT first time: "+followeeName);
			}
			
			followerNames.add(currUser);
			followeeFollowers.put(followeeName,followerNames);
			
			if (followeeFollowerCounts.containsKey(followeeName))
				followerCount = followeeFollowerCounts.get(followeeName) + 1;
			else
				followerCount = 1;
			
			followeeFollowerCounts.put(followeeName,followerCount);
		}
		
		for (String f: followeeFollowers.keySet())
		{
			List<String> fNames = followeeFollowers.get(f);
			System.out.println(f+": "+followeeFollowerCounts.get(f));
			System.out.println(fNames);
		}
		
		int docFreq=0;
		//Get the document frequency of terms
		
		for (String term : allUniqueDocTerms)
		{
			//Initialize allTermsDocumentFreq to 0
			allTermsDocumentFreq.put(term, 0);
		}
		
		for (String curName : allUserDocuments.keySet())
		{
			LinkedHashMap<String,Double> curDoc = allUserDocuments.get(curName);
			for (String docTerm : curDoc.keySet())
			{
				docFreq = allTermsDocumentFreq.get(docTerm);
				docFreq++;
				allTermsDocumentFreq.put(docTerm,docFreq);
			}
		}
		
		//Calculate tfidf
		double tf,df,idf,tfidf;
		totalDocuments = allUserDocuments.size();
		System.out.println(getLocalName()+" Calculating TF-IDF");

		double vectorMagnitude=0.0;

		//Tf-idf for aggregated tweets
		for (Map.Entry<String,LinkedHashMap<String,Double>> userEntry
				: allUserDocuments.entrySet())
		{
			vectorMagnitude = 0.0;
			LinkedHashMap<String,Double> userDoc = userEntry.getValue();
			LinkedHashMap<String,Double> userDocumentTFIDF = new LinkedHashMap<String,Double>();

			for (String docTerm : userDoc.keySet())
			{
				tf=userDoc.get(docTerm); //tf raw frequency
				df=allTermsDocumentFreq.get(docTerm);
				idf=(double)Math.log10(totalDocuments/df) / Math.log10(2); //log base 2
				tfidf=tf*idf;
				userDocumentTFIDF.put(docTerm, tfidf);
//				System.out.println("docTerm: "+docTerm+"\ttf: "+tf+"\tdf: "+df+"\tidf: "+idf+"\ttfidf: "+tfidf);
				vectorMagnitude+=tfidf*tfidf;
			}

			//System.out.println("tweetId: "+currTweetId+","+userDocumentTFIDF);

			vectorMagnitude = Math.sqrt(vectorMagnitude);

			//precalculate the magnitude of vectors and element-wise division of documents to the magnitude x./|x| ie. normalize the document to unit vectors
			double docSumMag = 0.0;
			for (String docTerm : userDoc.keySet())
			{
				tfidf = userDocumentTFIDF.get(docTerm);
				tfidf = tfidf / vectorMagnitude;
				userDocumentTFIDF.put(docTerm,tfidf);
				docSumMag += tfidf*tfidf;
			}

			//System.out.println("Normalized_tweetId: "+currTweetId+","+userDocumentTFIDF);

			//System.out.println(Math.sqrt(docSumMag));

			userEntry.setValue(userDocumentTFIDF);
		}
		allUserDocumentsTFIDF = allUserDocuments;
		allTermsDocumentFreq.clear();


	}
	
	public void determineTrainingTestSet()
	{
		int numUsers = allUserDocuments.keySet().size();
		
		System.out.println("Entered determineTrainingTestSet");
		System.out.println("numUsers: "+numUsers);

		SvmReproducibility.Split split = SvmReproducibility.stratifiedSplit(
				followeeFollowers, TEST_SET_PERCENT);
		trainSetUsers = split.getTrainUsers();
		testSetUsers = split.getTestUsers();
		
		System.out.println("Determined Training Set");
		System.out.println("Controller SVM reproducibility split seed: "
				+ SvmReproducibility.SPLIT_SEED + " model seed: " + SvmReproducibility.MODEL_SEED);
		System.out.println("Controller SVM train class counts: "+countUsersByFollowee(trainSetUsers));
		System.out.println("Controller SVM test class counts: "+countUsersByFollowee(testSetUsers));
		
	}

	private Map<String,Integer> countUsersByFollowee(List<String> users)
	{
		Map<String,Integer> counts = new LinkedHashMap<String,Integer>();
		for (String user : users)
		{
			String followeeName = userFollowee.get(user);
			if (followeeName == null)
			{
				followeeName = "UNKNOWN";
			}
			Integer count = counts.get(followeeName);
			if (count == null)
			{
				count = 0;
			}
			counts.put(followeeName, count + 1);
		}
		return counts;
	}
	
	public void createTrainingFile(String pathTrainSet)
	{
		System.out.println(getLocalName()+" test set "+ testSetUsers.size() +": "+testSetUsers);
		System.out.println(getLocalName()+" train set "+ trainSetUsers.size() +": "+trainSetUsers);
		
		String algorithmString = "";
		if (RecommendationModeSupport.isSvmFamilyAlgorithm(algorithmRec))
			algorithmString = RecommendationModeSupport.algorithmName(algorithmRec) + " ";
		myGui.appendResult(algorithmString + " train set "+ trainSetUsers.size() +": "+trainSetUsers);
		myGui.appendResult(algorithmString + " test set "+ testSetUsers.size() +": "+testSetUsers);
		
		// String arffDirName = "Dataset/Arff_files/";
		// File arffDir = new File(arffDirName);
		// if (!arffDir.exists())
		// {
				// arffDir.mkdirs();
		// }
		
		//Create the arff training file attributes
		FileWriter trainWriter;
		// FileWriter testWriter;
		// FileWriter recommendWriter;
		BufferedWriter bufferedWriterTrain;
		// BufferedWriter bufferedWriterTest;
		// BufferedWriter bufferedWriterRecommend;
		int uniqueWordCount = 0;
		// String trainSetFilePath = arffDirName + "train_set_rec"+nodeNumber+".txt";
		// String testSetFilePath = arffDirName + "test_set_rec"+nodeNumber+".txt";
		// String recommendSetFilePath = arffDirName + "recommend_set_rec"+nodeNumber+".txt";
		
		try {
				trainWriter = new FileWriter(pathTrainSet, false);
				// testWriter = new FileWriter(testSetFilePath, false);
				// recommendWriter = new FileWriter(recommendSetFilePath, false);
				bufferedWriterTrain = new BufferedWriter(trainWriter);
				// bufferedWriterTest = new BufferedWriter(testWriter);
				// bufferedWriterRecommend = new BufferedWriter(recommendWriter);
				
				bufferedWriterTrain.write("@relation trainingSetCentral");
				bufferedWriterTrain.newLine();
				bufferedWriterTrain.newLine();
				// bufferedWriterTest.write("@relation testSet");
				// bufferedWriterTest.newLine();
				// bufferedWriterTest.newLine();
				// bufferedWriterRecommend.write("@relation recommendSet");
				// bufferedWriterRecommend.newLine();
				// bufferedWriterRecommend.newLine();
				
				for (String uniqueWord: allUniqueDocTerms)
				{
					bufferedWriterTrain.write("@attribute word"+ uniqueWordCount +" numeric");
					bufferedWriterTrain.newLine();
					// bufferedWriterTest.write("@attribute word"+ uniqueWordCount +" numeric");
					// bufferedWriterTest.newLine();
					// bufferedWriterRecommend.write("@attribute word"+ uniqueWordCount +" numeric");
					// bufferedWriterRecommend.newLine();
					
					uniqueWordCount++;
				}
				
				String attributeClass = buildArffClassAttribute(followeeFollowers.keySet());
				
				bufferedWriterTrain.write(attributeClass);
				bufferedWriterTrain.newLine();
				bufferedWriterTrain.newLine();
				bufferedWriterTrain.newLine();
				bufferedWriterTrain.write("@data");
				bufferedWriterTrain.newLine();
				
				// bufferedWriterTest.write(attributeClass);
				// bufferedWriterTest.newLine();
				// bufferedWriterTest.newLine();
				// bufferedWriterTest.newLine();
				// bufferedWriterTest.write("@data");
				// bufferedWriterTest.newLine();
				
				// bufferedWriterRecommend.write(attributeClass);
				// bufferedWriterRecommend.newLine();
				// bufferedWriterRecommend.newLine();
				// bufferedWriterRecommend.newLine();
				// bufferedWriterRecommend.write("@data");
				// bufferedWriterRecommend.newLine();
				
				bufferedWriterTrain.close();
				// bufferedWriterTest.close();
				// bufferedWriterRecommend.close();
				
		}
		catch (IOException e) {
			e.printStackTrace();
		}
		
		//Write the vector data of each user for training, test, recommend set to arff files
		try {
				trainWriter = new FileWriter(pathTrainSet, true);
				// testWriter = new FileWriter(testSetFilePath, true);
				// recommendWriter = new FileWriter(recommendSetFilePath, true);
				bufferedWriterTrain = new BufferedWriter(trainWriter);
				// bufferedWriterTest = new BufferedWriter(testWriter);
				// bufferedWriterRecommend = new BufferedWriter(recommendWriter);

				StringJoiner tfidfJoiner;
				double currTfidf;
				
				// for (String currUser : testSetUsers)
				// {
					// Map<String,Double> currDocTfidf = allUserDocumentsTFIDF.get(currUser);
					
					// tfidfJoiner = vectorArffFormat(currDocTfidf,allUniqueDocTerms);
						
					// bufferedWriterTest.write(tfidfJoiner.toString() + "," + userFollowee.get(currUser));
					// bufferedWriterTest.newLine();	
				// }
				// bufferedWriterTest.close();
				
				for (String currUser : trainSetUsers)
				{
					Map<String,Double> currDocTfidf = allUserDocumentsTFIDF.get(currUser);
					tfidfJoiner = vectorArffFormat(currDocTfidf,allUniqueDocTerms);
					
					bufferedWriterTrain.write(tfidfJoiner.toString() + "," + formatArffClassValue(userFollowee.get(currUser)));
					bufferedWriterTrain.newLine();
					
				}
				bufferedWriterTrain.close();	

				// for (String currUser : usersRec)
				// {
					
					// Map<String,Double> currDocTfidf = allUserDocumentsTFIDF.get(currUser);
					
					// tfidfJoiner = vectorArffFormat(currDocTfidf,allUniqueDocTerms);
					
					// bufferedWriterRecommend.write(tfidfJoiner.toString() + "," + userFollowee.get(currUser));
					// bufferedWriterRecommend.newLine();
				// }
				// bufferedWriterRecommend.close();	
		}
		catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public StringJoiner vectorArffFormat(Map<String,Double> currDocTfidf, Set<String> uniqueDocTerms)
	{
		StringJoiner tfidfJoinerTemp = new StringJoiner(",");
		double currTfidf;
		
		for (String uniqueWord: uniqueDocTerms)
		{
			if (currDocTfidf.keySet().contains(uniqueWord))
				currTfidf = currDocTfidf.get(uniqueWord);
			else
				currTfidf = 0.0;
			
			tfidfJoinerTemp.add(String.valueOf(currTfidf));
		}
		return tfidfJoinerTemp;
	}

	private String buildArffClassAttribute(Set<String> classNames)
	{
		StringJoiner classJoiner = new StringJoiner(",","{","}");
		Collection<String> orderedClassNames = classNames;
		if (RecommendationModeSupport.isSvmFamilyAlgorithm(algorithmRec))
		{
			orderedClassNames = SvmReproducibility.sortedNonNullValues(classNames);
		}
		for (String className: orderedClassNames)
		{
			classJoiner.add(formatArffClassValue(className));
		}
		return "@attribute result " + classJoiner.toString();
	}

	private String formatArffClassValue(String className)
	{
		if (className == null)
		{
			return "?";
		}
		return "'" + className
				.replace("\\", "\\\\")
				.replace("'", "\\'")
				.replace("\r", " ")
				.replace("\n", " ") + "'";
	}

	public ArrayList<Tweet> getDistributedTestSet()
	{
		ArrayList<Tweet> distributedTestSet = new ArrayList<Tweet>();
		listOfUsers.clear();
		
		//update new list of users to only include test set users
		for (String testUser : testSetUsers)
		{
			distributedTestSet.addAll(localDb.getTweetsFromUser(testUser));
			listOfUsers.add(testUser);
		}
		
		//add recommended user(s) to new list of users
		for (String recUser : usersRec)
		{
			if (!listOfUsers.contains(recUser))
			{
				listOfUsers.add(recUser);
				distributedTestSet.addAll(localDb.getTweetsFromUser(recUser));
			}
				
		}
		System.out.println("Controller Distributed SVM listOfUsers: "+listOfUsers);
		
		return distributedTestSet;
	}
	
	public Classifier trainCentralSVM(String pathTrainSet)
	{
		//Train the SVM
		BufferedReader datafile = readDataFile(pathTrainSet);
		Instances data = null;
		try{
			data = new Instances(datafile);
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}

		data.setClassIndex(data.numAttributes() - 1);
		SMO svmModel = new SMO();
		svmModel.setC(0.1);
		svmModel.setBuildCalibrationModels(true);
		SvmReproducibility.applyModelSeed(svmModel);
							
		try{
			svmModel.buildClassifier(data);
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
		
		return svmModel;
	}
	
	public BufferedReader readDataFile(String filename) {
		BufferedReader inputReader = null;

		try {
			inputReader = new BufferedReader(new FileReader(filename));
		} catch (FileNotFoundException ex) {
			System.err.println("File not found: " + filename);
		}

		return inputReader;
	}

	public Evaluation classify(Classifier model,
			Instances trainingSet, Instances testingSet) throws Exception {
		Evaluation evaluation = new Evaluation(trainingSet);

		model.buildClassifier(trainingSet);
		evaluation.evaluateModel(model, testingSet);

		return evaluation;
	}

	public double calculateAccuracy(FastVector predictions) {
		double correct = 0;

		for (int i = 0; i < predictions.size(); i++) {
			NominalPrediction np = (NominalPrediction) predictions.elementAt(i);
			if (np.predicted() == np.actual()) {
				correct++;
			}
		}

		return 100 * correct / predictions.size();
	}
	
	
	//Creates a directory if it does not exist
	public void createDir(String dirName) {
		File dir = new File(dirName);
		if (!dir.exists())
			dir.mkdirs();
	}

	public void setFile(File textFile)
	{
		this.textFile = textFile;
		if (resetPendingDatasetSelection)
		{
			reportFailedExperimentOutputDeletes(
					ExperimentResetFiles.resetDatasetOutputs(new File("."), textFile));
		}
		System.out.println("textFile.getName(): "+textFile.getName());
		// myGui.appendResult("Loaded File: "+textFile.getName());
	}
	
	public void setCorpusGenFile(File corpusGenFile)
	{
		this.corpusGenFile = corpusGenFile;
		System.out.println("corpusGenFile.getName(): "+corpusGenFile.getName());
		// myGui.appendUserGenTweetsResult("Loaded File: "+corpusGenFile.getName());
	}

	public void setReadFrom(int readFrom)
	{
		this.readFrom = readFrom;
	}
	
	public void setNumArtificialTweets(int numArtificialTweets)
	{
		this.numArtificialTweets = numArtificialTweets;
		System.out.println("numArtificialTweets: "+numArtificialTweets);
	}

	public void setUserGenerationSeed(Long userGenerationSeed)
	{
		this.userGenerationSeed = userGenerationSeed;
	}
	
	//Read in individual user's shape scale then get average
	public void readInGeneratingParameters4() throws FileNotFoundException
	{
		// Read in the scale and shape of each user
		Scanner sc = new Scanner(new File(pathToUserParameters)).useDelimiter("[\t\n]");
		
		int numUsers = 0;
		
		while (sc.hasNext())
		{	
			String userName = sc.next();
			Double scaleInput = Double.parseDouble(sc.next());
			Double shapeInput = Double.parseDouble(sc.next());
			usersScale.put(userName,scaleInput);
			usersShape.put(userName,shapeInput);
			
			numUsers++;
			averageUserScale += scaleInput;
			averageUserShape += shapeInput;
			//			System.out.println(userName+" "+scaleInput+" "+shapeInput);
		}
		
		averageUserScale /= numUsers;
		averageUserShape /= numUsers;
		
		sc.close();
	}
	
	//Modified to include finding out followee name from filename
	public void readInTfidfMatrix() throws FileNotFoundException
	{
		Scanner sc = new Scanner(new File(pathToTfidfMatrix)).useDelimiter("[\t\n]");
		ArrayList<String> allWordsInSet = new ArrayList<String>();
		ArrayList<String> userNamesIndex = new ArrayList<String>();
		// for (int i = 0; i < usersShape.keySet().size(); i++)
			
		String followeeName = pathToTfidfMatrix.split("_",3)[0].split("/",2)[1];
		
		int numInSet = 4; //for separate groups
		for (int i = 0; i < numInSet; i++)
		{
			String s = sc.next();
			userNamesIndex.add(s);
			followerFollowee.put(s,followeeName);
			//			System.out.println("s: "+s);
		}

		
		while (sc.hasNext())
		{
			String wordInMatrix;
			sc.next(); //blank tab
			if (sc.hasNext())
			{
				wordInMatrix = sc.next();
				//				System.out.println("wordInMatrix: "+wordInMatrix);

				allWordsInSet.add(wordInMatrix);
				
				for (String currUser : userNamesIndex)
				{
					Double tfidfValue = Double.parseDouble(sc.next());
					//					System.out.print(tfidfValue+"\t");

					if (allUserTfidfVectors.containsKey(currUser))
					{
						userTfidfVector = allUserTfidfVectors.get(currUser);
					}
					else
					{
						userTfidfVector = new LinkedHashMap<String,Double>(); 
					}

					userTfidfVector.put(wordInMatrix,tfidfValue);
					allUserTfidfVectors.put(currUser, userTfidfVector);
				}
				//				System.out.println();
			}
		}
		sc.close();

		// System.out.println("OK ALL USER TFIDF VECTORS: "+allUserTfidfVectors);
		//		System.out.println(userNamesIndex);
		//		
		//		for (String currWord : allUserTfidfVectors.get(userNamesIndex.get(0)).keySet())
		//		{
		//			for (int i = 0; i < userNamesIndex.size(); i++)
		//			{
		//				if (i == 0)
		//					System.out.print(currWord+"\t");
		//				
		//				System.out.print(allUserTfidfVectors.get(userNamesIndex.get(i)).get(currWord)+"\t");
		//			}
		//			System.out.println();
		//		}
	}
	
	public void setGeneratedWordBins4()
	{
		double currentValue = MIN_TFIDF;

		for (String currUser : allUserTfidfVectors.keySet())
		{
			ArrayList<String> wordsInBin = new ArrayList<String>();

			LinkedHashMap<String,Double> currUserTfidfVector = allUserTfidfVectors.get(currUser);
			LinkedHashMap<Double,ArrayList<String>> userTfidfWordsBins = new LinkedHashMap<Double,ArrayList<String>>();
			
			//System.out.println(currUser+"--------------");
			
			for (String currWord : currUserTfidfVector.keySet())
			{
				currentValue = currUserTfidfVector.get(currWord);
				
//				System.out.println(currWord);
				
				if (!userTfidfWordsBins.keySet().contains(currentValue))
				{
//					System.out.println("I ENTERED HERE "+currentValue+" "+currWord);
					wordsInBin = new ArrayList<String>();
					wordsInBin.add(currWord);
					userTfidfWordsBins.put(currentValue, wordsInBin);
				}
				else
				{
//					System.out.println("I OTHERED HERE "+currentValue+" "+currWord);
					wordsInBin = userTfidfWordsBins.get(currentValue);
					wordsInBin.add(currWord);
					userTfidfWordsBins.put(currentValue,wordsInBin);
				}

			}
			
			allUserTfidfWordsBins.put(currUser, userTfidfWordsBins);
			
//			private static LinkedHashMap<Double,ArrayList<String>> userTfidfWordsBins 
//			private static LinkedHashMap<String,LinkedHashMap<Double,ArrayList<String>>> allUserTfidfWordsBins
//			put in all userwordbin
		}
		
//		System.out.println("HELLO "+allUserTfidfWordsBins);
	}

}
